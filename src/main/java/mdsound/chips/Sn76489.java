package mdsound.chips;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import static java.lang.System.getLogger;


public class Sn76489 {

    private static final Logger logger = getLogger(Sn76489.class.getName());

    /** per-channel muting */
    private int mute;
    /** double noise volume when non-zero */
    private int boostNoise;

    // Variables
    private float clock;
    private float dClock;
    private int psgStereo;
    private int numClocksForSample;
    private FeedbackPatterns whiteNoiseFeedback;
    private SRWidths srWidth;

    // Psg registers

    /** Tone, vol x4 */
    public int[] registers = new int[8];
    private int latchedRegister;
    private int noiseShiftRegister;
    /** Noise channel signal generator frequency */
    private int noiseFreq;

    // Output calculation variables

    /** Frequency register values (counters) */
    private final int[] toneFreqVals = new int[4];
    /** Frequency channel flip-flops */
    private final int[] toneFreqPos = new int[4];
    /** Value of each channel, before stereo is applied */
    private final int[] channels = new int[4];
    /** intermediate values used at boundaries between + and - (does not need double accuracy) */
    private final float[] intermediatePos = new float[4];

    /** fake stereo */
    private final float[][] panning = new float[][] {new float[2], new float[2], new float[2], new float[2]};
    private final int[][] volume = new int[][] {new int[2], new int[2], new int[2], new int[2]};

    /** bit 7 - NGP Mode on/off, bit 0 - is 2nd NGP chips */
    private int ngpFlags;

    private Sn76489 ngpChip2;

    private enum FeedbackPatterns {
        /** Texas Instruments TMS SN76489N (original) from BBC Micro computer */
        FB_BBCMICRO(0x8005),
        /** Texas Instruments TMS SN76489AN (rev. A) from SC-3000H computer */
        FB_SC3000(0x0006),
        /** SN76489 clone in Sega's VDP chips (315-5124, 315-5246, 315-5313, Game Gear) */
        FB_SEGAVDP(0x0009);
        final int v;

        FeedbackPatterns(int v) {
            this.v = v;
        }
    }

    private enum SRWidths {
        SRW_SC3000BBCMICRO(15),
        SRW_SEGAVDP(16);
        final int v;

        SRWidths(int v) {
            this.v = v;
        }
    }

    private enum VolumeModes {
        /** Volume levels 13-15 are identical */
        VOL_TRUNC,
        /** Volume levels 13-15 are unique */
        VOL_FULL,
    }

    private enum MuteValues {
        /** All channels muted */
        MUTE_ALLOFF(0),
        /** Tone 1 mute control */
        MUTE_TONE1(1),
        /** Tone 2 mute control */
        MUTE_TONE2(2),
        /** Tone 3 mute control */
        MUTE_TONE3(4),
        /** Noise mute control **/
        MUTE_NOISE(8),
        /** All channels enabled */
        MUTE_ALLON(15);
        final int v;

        MuteValues(int v) {
            this.v = v;
        }
    }

    private static Sn76489 lastChipInit = null;

    private static final double SQRT2 = 1.414213562;
    private static final double RANGE = 512;

    /** Initial state of shift register */
    private static final int NoiseInitialState = 0x8000;
    /** Value below which Psg does not output */
    private static final int PSG_CUTOFF = 0x6;

    private static final int[] PSGVolumeValues = {
            // These values are taken from a real SMS2's output
            // {892,892,892,760,623,497,404,323,257,198,159,123,96,75,60,0},
            // I can't remember why 892... :P some scaling I did at some point
            // these values are true volumes for 2dB drops at each step (multiply previous by 10^-0.1)
            // 1516,1205,957,760,603,479,381,303,240,191,152,120,96,76,60,0
            // The MAME core uses 0x2000 as maximum volume (0x1000 for bipolar output)
            4096, 3254, 2584, 2053, 1631, 1295, 1029, 817, 649, 516, 410, 325, 258, 205, 163, 0
    };

    private void config(FeedbackPatterns feedback, SRWidths srWidth, int boostNoise) {
        this.whiteNoiseFeedback = feedback;
        this.srWidth = srWidth;
    }

    private void setPanning(int ch0, int ch1, int ch2, int ch3) {
        calcPanning(this.panning[0], ch0);
        calcPanning(this.panning[1], ch1);
        calcPanning(this.panning[2], ch2);
        calcPanning(this.panning[3], ch3);
    }

    /**
     * Reset the panning values to the centre position
     */
    private static void centerPanning(float[] channels) {
        channels[0] = channels[1] = 1.0f;
    }

    private static void calcPanning(float[] channels, int position) {
        if (position > RANGE / 2)
            position = (int) (RANGE / 2);
        else if (position < -RANGE / 2)
            position = -(int) (RANGE / 2);
        position += (int) (RANGE / 2); // make -256..0..256 . 0..256..512

        // Equal power law: equation is
        // right = sin( position / range * pi / 2) * sqrt( 2 )
        // left is equivalent to right with position = range - position
        // position is in the range 0 .. RANGE
        // RANGE / 2 = centre, result = 1.0f
        channels[1] = (float) (Math.sin(position / RANGE * Math.PI / 2d) * SQRT2);
        position = (int) RANGE - position;
        channels[0] = (float) (Math.sin(position / RANGE * Math.PI / 2d) * SQRT2);
    }

    public void writeGGStereo(int data) {
        this.psgStereo = data;
        //logger.log(Level.DEBUG, String.format("WrPSGStereo:0:%d", SN76489Chip[0].psgStereo));
        //logger.log(Level.DEBUG, String.format("WrPSGStereo:1:%d", SN76489Chip[1].psgStereo));
    }

    public int start(int samplingRate, int clockValue) {
        this.dClock = (float) (clockValue & 0x7ff_ffff) / 16 / samplingRate;

        setMute(15);
        config(/* MUTE_ALLON, */ FeedbackPatterns.FB_SEGAVDP, SRWidths.SRW_SEGAVDP, 1);

        for (int i = 0; i <= 3; i++)
            centerPanning(this.panning[i]);
        //reset(chips);

        if ((clockValue & 0x8000_0000) != 0 && lastChipInit != null) {
            // Activate special NeoGeoPocket Mode
            lastChipInit.ngpFlags = 0x80 | 0x00;
            this.ngpFlags = 0x80 | 0x01;
            this.ngpChip2 = lastChipInit;
            lastChipInit.ngpChip2 = this;
            lastChipInit = null;
        } else {
            this.ngpFlags = 0x00;
            this.ngpChip2 = null;
            lastChipInit = this;
        }

        return samplingRate;
    }

    public void reset() {
        this.psgStereo = 0xff;

        for (int i = 0; i <= 3; i++) {
            // Initialise Psg state
            this.registers[2 * i] = 1; // tone freq=1
            this.registers[2 * i + 1] = 0xf; // vol=off
            this.noiseFreq = 0x10;

            // Set counters to 0
            this.toneFreqVals[i] = 0;

            // Set flip-flops to 1
            this.toneFreqPos[i] = 1;

            // Set intermediate positions to do-not-use value
            this.intermediatePos[i] = Float.MIN_VALUE;

            // Set panning to centre
            //centerPanning( this.panning[i] );
        }

        this.latchedRegister = 0;

        // Initialise noise generator
        this.noiseShiftRegister = NoiseInitialState;

        // Zero clock
        this.clock = 0;
    }

    public int[][] update(int[][] buffer, int length) {
//logger.log(Level.DEBUG, String.format("PSGStereo:1: %d", this.psgStereo));

        Sn76489 chip2;
        Sn76489 chipT;
        Sn76489 chipN;

        int ngpMode = (this.ngpFlags >> 7) & 0x01;
        if (ngpMode == 0) {
            chip2 = null;
            chipT = chipN = this;
        } else {
            chip2 = this.ngpChip2;
            if ((this.ngpFlags & 0x01) == 0) {
                chipT = this;
                chipN = chip2;
            } else {
                chipT = chip2;
                chipN = this;
            }
        }

        for (int j = 0; j < length; j++) {
            // Tone channels
            int i;
            for (i = 0; i <= 2; ++i)
                if ((chipT.mute >> i & 1) != 0) {
                    if (Float.floatToIntBits(chipT.intermediatePos[i]) != Float.floatToIntBits(Float.MIN_VALUE))
                        // Intermediate position (antialiasing)
                        this.channels[i] = (int) (PSGVolumeValues[this.registers[2 * i + 1]] * chipT.intermediatePos[i]);
                    else
                        // Flat (no antialiasing needed)
                        this.channels[i] = PSGVolumeValues[this.registers[2 * i + 1]] * chipT.toneFreqPos[i];
                } else {
                    // Muted channel
logger.log(Level.DEBUG, String.format("T:ch[%d]: muted", i));
                    this.channels[i] = 0;
                }

            // Noise channel
            if ((chipN.mute >> 3 & 1) != 0) {
                //this.Channels[3] = PSGVolumeValues[this.Registers[7]] * ( chipN.NoiseShiftRegister & 0x1 ) * 2; // double noise volume
                // Now the noise is bipolar, too. -Valley Bell
                this.channels[3] = PSGVolumeValues[this.registers[7]] * ((chipN.noiseShiftRegister & 0x1) * 2 - 1);
                // due to the way the white noise works here, it seems twice as loud as it should be
                if ((this.registers[6] & 0x4) != 0)
                    this.channels[3] >>= 1;
            } else {
logger.log(Level.DEBUG, String.format("N:ch[%d]: muted", i));
                this.channels[i] = 0;
            }

            // Build stereo result into buffer
            buffer[0][j] = 0;
            buffer[1][j] = 0;
            int bl;
            int br;
            if (this.ngpFlags == 0) {
                // For all 4 channels
                for (i = 0; i <= 3; ++i) {
                    if (((this.psgStereo >> i) & 0x11) == 0x11) {
                        //logger.log(Level.DEBUG, String.format("ggpan1");
                        // no GG stereo for this channel
                        if (this.panning[i][0] == 1.0f) {
                            bl = this.channels[i]; // left
                            br = this.channels[i]; // right

                        } else {
                            bl = (int) (this.panning[i][0] * this.channels[i]); // left
                            br = (int) (this.panning[i][1] * this.channels[i]); // right

                        }
                    } else {
                        //logger.log(Level.DEBUG, String.format("ggpan2");
                        // GG stereo overrides panning
                        bl = ((this.psgStereo >> (i + 4)) & 0x1) * this.channels[i]; // left
                        br = ((this.psgStereo >> i) & 0x1) * this.channels[i]; // right
                        //logger.log(Level.DEBUG, String.format("Ch:bl:br:%d:%d:%d:%d",i,bl,br, this.Channels[i]);
                    }

                    buffer[0][j] += bl;
                    buffer[1][j] += br;
                    this.volume[i][0] = Math.abs(bl);
                    this.volume[i][1] = Math.abs(br);
                }
//logger.log(Level.TRACE, String.format("%d", this.channels[3]));
            } else {
                if ((this.ngpFlags & 0x01) == 0) {
                    // For all 3 tone channels
                    for (i = 0; i < 3; i++) {
                        bl = (this.psgStereo >> (i + 4) & 0x1) * this.channels[i]; // left
                        br = (this.psgStereo >> i & 0x1) * chip2.channels[i]; // right
                        buffer[0][j] += bl;
                        buffer[1][j] += br;
                        this.volume[i][0] = Math.abs(bl);
                        this.volume[i][1] = Math.abs(br);
                    }
                } else {
                    // noise channel
                    i = 3;
                    bl = (this.psgStereo >> (i + 4) & 0x1) * chip2.channels[i]; // left
                    br = (this.psgStereo >> i & 0x1) * this.channels[i]; // right
                    buffer[0][j] += bl;
                    buffer[1][j] += br;
                    this.volume[i][0] = Math.abs(bl);
                    this.volume[i][1] = Math.abs(br);
                }
            }

            // Increment clock by 1 sample length
            this.clock += this.dClock;
            this.numClocksForSample = (int) this.clock; // truncate
            this.clock -= this.numClocksForSample; // remove integer part

            // Decrement tone channel counters
            for (i = 0; i <= 2; ++i)
                this.toneFreqVals[i] -= this.numClocksForSample;

            // Noise channel: match to tone2 or decrement its counter
            if (this.noiseFreq == 0x80)
                this.toneFreqVals[3] = this.toneFreqVals[2];
            else
                this.toneFreqVals[3] -= this.numClocksForSample;

            // Tone channels:
            for (i = 0; i <= 2; ++i) {
                if (this.toneFreqVals[i] <= 0) { // If the counter gets below 0...
                    if (this.registers[i * 2] >= PSG_CUTOFF) {
                        // For tone-generating values, calculate how much of the sample is + and how much is -
                        // This is optimised into an even more confusing state than it was in the first place...
                        this.intermediatePos[i] = (this.numClocksForSample - this.clock + 2 * this.toneFreqVals[i]) * this.toneFreqPos[i] / (this.numClocksForSample + this.clock);
                        // Flip the flip-flop
                        this.toneFreqPos[i] = -this.toneFreqPos[i];
                    } else {
                        // stuck value
                        this.toneFreqPos[i] = 1;
                        this.intermediatePos[i] = Float.MIN_VALUE;
                    }
                    this.toneFreqVals[i] += this.registers[i * 2] * (this.numClocksForSample / this.registers[i * 2] + 1);
                } else
                    // signal no antialiasing needed
                    this.intermediatePos[i] = Float.MIN_VALUE;
            }

            // Noise channel
            if (this.toneFreqVals[3] <= 0) {
                // If the counter gets below 0...
                // Flip the flip-flop
                this.toneFreqPos[3] = -this.toneFreqPos[3];
                if (this.noiseFreq != 0x80)
                    // If not matching tone2, decrement counter
                    this.toneFreqVals[3] += this.noiseFreq * (this.numClocksForSample / this.noiseFreq + 1);
                if (this.toneFreqPos[3] == 1) {
                    // On the positive edge of the square wave (only once per cycle)
                    int feedback;
                    if ((this.registers[6] & 0x4) != 0) {
                        // White noise
                        // Calculate parity of fed-back bits for feedback
                        switch (this.whiteNoiseFeedback) {
                        // Do some optimised calculations for common (known) feedback values
                        //case 0x0003: // SC-3000, BBC %00000011
                        case FB_SEGAVDP: // SMS, GG, MD  %00001001
                            // If two bits fed back, I can do feedback=(nsr & fb) && (nsr & fb ^ fb)
                            // since that's (one or more bits set) && (not all bits set)
                            feedback = this.noiseShiftRegister & this.whiteNoiseFeedback.v;
                            feedback = (feedback > 0) && (((this.noiseShiftRegister & this.whiteNoiseFeedback.v) ^ this.whiteNoiseFeedback.v) > 0) ? 1 : 0;
                            break;
                        default:
                            // Default handler for all other feedback values
                            // XOR fold bits into the final bit
                            feedback = this.noiseShiftRegister & this.whiteNoiseFeedback.v;
                            feedback ^= feedback >> 8;
                            feedback ^= feedback >> 4;
                            feedback ^= feedback >> 2;
                            feedback ^= feedback >> 1;
                            feedback &= 1;
                            break;
                        }
                    } else // Periodic noise
                        feedback = this.noiseShiftRegister & 1;

                    this.noiseShiftRegister = (this.noiseShiftRegister >> 1) | (feedback << (this.srWidth.v - 1));
                }
            }
        }

//logger.log(Level.DEBUG, "psg: " + Arrays.toString(buffer[0]) + ", " + Arrays.toString(buffer[1]) + ", " + chipT.mute + ", " + chipN.mute); // mute ok, TODO data always 0
        return this.volume;
    }

    public void write(int data) {
logger.log(Level.TRACE, String.format("psg: %02x, mute: %02x", data & 0xff, mute)); // it seems ok
        if ((data & 0x80) != 0) {
            // Latch/data byte  %1 cc t dddd
            this.latchedRegister = (data >> 4) & 0x07;
            this.registers[this.latchedRegister] =
                    (this.registers[this.latchedRegister] & 0x3f0) // zero low 4 bits
                            | (data & 0xf); // and replace with data
        } else {
            // data byte %0 - dddddd
            if ((this.latchedRegister % 2) == 0 && (this.latchedRegister < 5))
                // Tone register
                this.registers[this.latchedRegister] =
                        (this.registers[this.latchedRegister] & 0x00f) // zero high 6 bits
                                | ((data & 0x3f) << 4); // and replace with data
            else
                // Other register
                this.registers[this.latchedRegister] = data & 0x0f; // Replace with data
        }
        switch (this.latchedRegister) {
        case 0:
        case 2:
        case 4: // Tone channels
            if (this.registers[this.latchedRegister] == 0)
                this.registers[this.latchedRegister] = 1; // Zero frequency changed to 1 to avoid div/0
            break;
        case 6: // Noise
            this.noiseShiftRegister = NoiseInitialState; // reset shift register
            this.noiseFreq = 0x10 << (this.registers[6] & 0x3); // set noise signal generator frequency
            break;
        }
    }

    /** @param val mask */
    public void setMute(int val) {
        this.mute = val;
    }
}
