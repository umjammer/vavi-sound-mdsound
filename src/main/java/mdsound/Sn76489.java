package mdsound;

import java.util.Arrays;
import java.util.List;


public class Sn76489 extends Instrument.BaseInstrument {

    public static class SN76489Context {
        public int mute; // per-channel muting
        public int boostNoise; // double noise volume when non-zero

        /* Variables */
        public float clock;
        public float dClock;
        public int psgStereo;
        public int numClocksForSample;
        public FeedbackPatterns whiteNoiseFeedback;
        public SRWidths srWidth;

        /* PSG registers: */
        public int[] registers = new int[8];        /* Tone, vol x4 */
        public int latchedRegister;
        public int noiseShiftRegister;
        public int noiseFreq;            /* Noise channel signal generator frequency */

        /* Output calculation variables */
        public int[] toneFreqVals = new int[4];      /* Frequency register values (counters) */
        public int[] toneFreqPos = new int[4];        /* Frequency channel flip-flops */
        public int[] channels = new int[4];          /* Value of each channel, before stereo is applied */
        public float[] intermediatePos = new float[4];   /* intermediate values used at boundaries between + and - (does not need double accuracy)*/

        public float[][] panning = new float[][] {new float[2], new float[2], new float[2], new float[2]};            /* fake stereo */
        public int[][] volume = new int[][] {new int[2], new int[2], new int[2], new int[2]};

        public int ngpFlags;       /* bit 7 - NGP Mode on/off, bit 0 - is 2nd NGP chip */

        public SN76489Context ngpChip2;

        public enum FeedbackPatterns {
            FB_BBCMICRO(0x8005), /* Texas Instruments TMS SN76489N (original) from BBC Micro computer */
            FB_SC3000(0x0006), /* Texas Instruments TMS SN76489AN (rev. A) from SC-3000H computer */
            FB_SEGAVDP(0x0009); /* SN76489 clone in Sega's VDP chips (315-5124, 315-5246, 315-5313, Game Gear) */
            int v;

            FeedbackPatterns(int v) {
                this.v = v;
            }
        }

        public enum SRWidths {
            SRW_SC3000BBCMICRO(15),
            SRW_SEGAVDP(16);
            int v;

            SRWidths(int v) {
                this.v = v;
            }
        }

        public enum VolumeModes {
            VOL_TRUNC,      /* Volume levels 13-15 are identical */
            VOL_FULL,      /* Volume levels 13-15 are unique */
        }

        public enum MuteValues {
            MUTE_ALLOFF(0),      /* All channels muted */
            MUTE_TONE1(1),      /* Tone 1 mute control */
            MUTE_TONE2(2),      /* Tone 2 mute control */
            MUTE_TONE3(4),      /* Tone 3 mute control */
            MUTE_NOISE(8),      /* Noise mute control */
            MUTE_ALLON(15);     /* All channels enabled */
            int v;

            MuteValues(int v) {
                this.v = v;
            }
        }

        private static SN76489Context lastChipInit = null;

        private static final double SQRT2 = 1.414213562;
        private static final double RANGE = 512;

        private static final int NoiseInitialState = 0x8000;  /* Initial state of shift register */
        private static final int PSG_CUTOFF = 0x6;     /* Value below which PSG does not output */

        private static final List<Integer> PSGVolumeValues = Arrays.asList(
                /* These values are taken from a real SMS2's output */
                /* {892,892,892,760,623,497,404,323,257,198,159,123,96,75,60,0}, /* I can't remember why 892... :P some scaling I did at some point */
                /* these values are true volumes for 2dB drops at each step (multiply previous by 10^-0.1) */
                /*1516,1205,957,760,603,479,381,303,240,191,152,120,96,76,60,0*/
                // The MAME core uses 0x2000 as maximum volume (0x1000 for bipolar output)
                4096, 3254, 2584, 2053, 1631, 1295, 1029, 817, 649, 516, 410, 325, 258, 205, 163, 0
        );

        //static unsigned short int FNumLimit;

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

        //
        // Reset the panning values to the centre position
        //
        private void centrePanning(float[] channels) {
            channels[0] = channels[1] = 1.0f;
        }

        private void calcPanning(float[] channels, int position) {
            if (position > RANGE / 2)
                position = (int) (RANGE / 2);
            else if (position < -RANGE / 2)
                position = -(int) (RANGE / 2);
            position += (int) (RANGE / 2);  // make -256..0..256 . 0..256..512

            // Equal power law: equation is
            // right = sin( position / range * pi / 2) * sqrt( 2 )
            // left is equivalent to right with position = range - position
            // position is in the range 0 .. RANGE
            // RANGE / 2 = centre, result = 1.0f
            channels[1] = (float) (Math.sin((double) position / RANGE * Math.PI / 2) * SQRT2);
            position = (int) RANGE - position;
            channels[0] = (float) (Math.sin((double) position / RANGE * Math.PI / 2) * SQRT2);
        }

        public void GGStereoWrite(int data) {
            this.psgStereo = data;
            //System.err.printf("WrPSGStereo:0:%d", SN76489Chip[0].psgStereo);
            //System.err.printf("WrPSGStereo:1:%d", SN76489Chip[1].psgStereo);
        }

        public int start(int samplingRate, int clockValue) {
            this.dClock = (float) (clockValue & 0x7FFFFFF) / 16 / samplingRate;

            setMute(15);
            config(/*MUTE_ALLON,*/ FeedbackPatterns.FB_SEGAVDP, SRWidths.SRW_SEGAVDP, 1);

            for (int i = 0; i <= 3; i++)
                centrePanning(this.panning[i]);
            //SN76489_Reset(chip);

            if ((clockValue & 0x80000000) != 0 && lastChipInit != null) {
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
            this.psgStereo = 0xFF;

            for (int i = 0; i <= 3; i++) {
                /* Initialise PSG state */
                this.registers[2 * i] = 1;      /* tone freq=1 */
                this.registers[2 * i + 1] = 0xf;    /* vol=off */
                this.noiseFreq = 0x10;

                /* Set counters to 0 */
                this.toneFreqVals[i] = 0;

                /* Set flip-flops to 1 */
                this.toneFreqPos[i] = 1;

                /* Set intermediate positions to do-not-use value */
                this.intermediatePos[i] = Float.MIN_VALUE;

                /* Set panning to centre */
                //centre_panning( this.panning[i] );
            }

            this.latchedRegister = 0;

            /* Initialise noise generator */
            this.noiseShiftRegister = NoiseInitialState;

            /* Zero clock */
            this.clock = 0;
        }

        public int[][] update(int[][] buffer, int length) {
            //System.err.printf("PSGStereo:1:{0}", SN76489_Chip[1].PSGStereo);

            SN76489Context chip2;
            SN76489Context chipT;
            SN76489Context chipN;

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
                /* Tone channels */
                int i;
                for (i = 0; i <= 2; ++i)
                    if ((chipT.mute >> i & 1) != 0) {
                        if (chipT.intermediatePos[i] != Float.MIN_VALUE)
                            /* Intermediate position (antialiasing) */
                            this.channels[i] = (short) (PSGVolumeValues.get(this.registers[2 * i + 1]) * chipT.intermediatePos[i]);
                        else
                            /* Flat (no antialiasing needed) */
                            this.channels[i] = PSGVolumeValues.get(this.registers[2 * i + 1]) * chipT.toneFreqPos[i];
                    } else
                        /* Muted channel */
                        this.channels[i] = 0;

                /* Noise channel */
                if ((chipN.mute >> 3 & 1) != 0) {
                    //this.Channels[3] = PSGVolumeValues[this.Registers[7]] * ( chipN.NoiseShiftRegister & 0x1 ) * 2; /* double noise volume */
                    // Now the noise is bipolar, too. -Valley Bell
                    this.channels[3] = PSGVolumeValues.get(this.registers[7]) * ((chipN.noiseShiftRegister & 0x1) * 2 - 1);
                    // due to the way the white noise works here, it seems twice as loud as it should be
                    if ((this.registers[6] & 0x4) != 0)
                        this.channels[3] >>= 1;
                } else
                    this.channels[i] = 0;

                // Build stereo result into buffer
                buffer[0][j] = 0;
                buffer[1][j] = 0;
                int bl = 0;
                int br = 0;
                if (this.ngpFlags == 0) {
                    // For all 4 channels
                    for (i = 0; i <= 3; ++i) {
                        if (((this.psgStereo >> i) & 0x11) == 0x11) {
                            //System.err.printf("ggpan1");
                            // no GG stereo for this channel
                            if (this.panning[i][0] == 1.0f) {
                                bl = this.channels[i]; // left
                                br = this.channels[i]; // right

                            } else {
                                bl = (int) (this.panning[i][0] * this.channels[i]); // left
                                br = (int) (this.panning[i][1] * this.channels[i]); // right

                            }
                        } else {
                            //System.err.printf("ggpan2");
                            // GG stereo overrides panning
                            bl = ((this.psgStereo >> (i + 4)) & 0x1) * this.channels[i]; // left
                            br = ((this.psgStereo >> i) & 0x1) * this.channels[i]; // right
                            //System.err.printf("Ch:bl:br:{0}:{1}:{2}:{3}",i,bl,br, this.Channels[i]);
                        }

                        buffer[0][j] += bl;
                        buffer[1][j] += br;
                        this.volume[i][0] = Math.abs(bl);// Math.max(bl, this.volume[i][0]);
                        this.volume[i][1] = Math.abs(br);// Math.max(br, this.volume[i][1]);
                    }
                    //Log.WriteLine(LogLevel.TRACE,String.format("{0}", this.Channels[3]));
                } else {
                    if ((this.ngpFlags & 0x01) == 0) {
                        // For all 3 tone channels
                        for (i = 0; i < 3; i++) {
                            bl = (this.psgStereo >> (i + 4) & 0x1) * this.channels[i]; // left
                            br = (this.psgStereo >> i & 0x1) * chip2.channels[i]; // right
                            buffer[0][j] += bl;
                            buffer[1][j] += br;
                            this.volume[i][0] = Math.abs(bl);// Math.max(bl, this.volume[i][0]);
                            this.volume[i][1] = Math.abs(br);// Math.max(br, this.volume[i][1]);
                        }
                    } else {
                        // noise channel
                        i = 3;
                        bl = (this.psgStereo >> (i + 4) & 0x1) * chip2.channels[i]; // left
                        br = (this.psgStereo >> i & 0x1) * this.channels[i]; // right
                        buffer[0][j] += bl;
                        buffer[1][j] += br;
                        this.volume[i][0] = Math.abs(bl);// Math.max(bl, this.volume[i][0]);
                        this.volume[i][1] = Math.abs(br);// Math.max(br, this.volume[i][1]);
                    }
                }


                /* Increment clock by 1 sample length */
                this.clock += this.dClock;
                this.numClocksForSample = (int) this.clock;  /* truncate */
                this.clock -= this.numClocksForSample;      /* remove integer part */

                /* Decrement tone channel counters */
                for (i = 0; i <= 2; ++i)
                    this.toneFreqVals[i] -= this.numClocksForSample;

                /* Noise channel: match to tone2 or decrement its counter */
                if (this.noiseFreq == 0x80)
                    this.toneFreqVals[3] = this.toneFreqVals[2];
                else
                    this.toneFreqVals[3] -= this.numClocksForSample;

                /* Tone channels: */
                for (i = 0; i <= 2; ++i) {
                    if (this.toneFreqVals[i] <= 0) {   /* If the counter gets below 0... */
                        if (this.registers[i * 2] >= PSG_CUTOFF) {
                            /* For tone-generating values, calculate how much of the sample is + and how much is - */
                            /* This is optimised into an even more confusing state than it was in the first place... */
                            this.intermediatePos[i] = (this.numClocksForSample - this.clock + 2 * this.toneFreqVals[i]) * this.toneFreqPos[i] / (this.numClocksForSample + this.clock);
                            /* Flip the flip-flop */
                            this.toneFreqPos[i] = -this.toneFreqPos[i];
                        } else {
                            /* stuck value */
                            this.toneFreqPos[i] = 1;
                            this.intermediatePos[i] = Float.MIN_VALUE;
                        }
                        this.toneFreqVals[i] += this.registers[i * 2] * (this.numClocksForSample / this.registers[i * 2] + 1);
                    } else
                        /* signal no antialiasing needed */
                        this.intermediatePos[i] = Float.MIN_VALUE;
                }

                /* Noise channel */
                if (this.toneFreqVals[3] <= 0) {
                    /* If the counter gets below 0... */
                    /* Flip the flip-flop */
                    this.toneFreqPos[3] = -this.toneFreqPos[3];
                    if (this.noiseFreq != 0x80)
                        /* If not matching tone2, decrement counter */
                        this.toneFreqVals[3] += this.noiseFreq * (this.numClocksForSample / this.noiseFreq + 1);
                    if (this.toneFreqPos[3] == 1) {
                        /* On the positive edge of the square wave (only once per cycle) */
                        int feedback;
                        if ((this.registers[6] & 0x4) != 0) {
                            /* White noise */
                            /* Calculate parity of fed-back bits for feedback */
                            switch (this.whiteNoiseFeedback) {
                            /* Do some optimised calculations for common (known) feedback values */
                            //case 0x0003: /* SC-3000, BBC %00000011 */
                            case FB_SEGAVDP: /* SMS, GG, MD  %00001001 */
                                /* If two bits fed back, I can do feedback=(nsr & fb) && (nsr & fb ^ fb) */
                                /* since that's (one or more bits set) && (not all bits set) */
                                feedback = this.noiseShiftRegister & this.whiteNoiseFeedback.v;
                                feedback = (feedback > 0) && (((this.noiseShiftRegister & this.whiteNoiseFeedback.v) ^ this.whiteNoiseFeedback.v) > 0) ? 1 : 0;
                                break;
                            default:
                                /* Default handler for all other feedback values */
                                /* XOR fold bits into the final bit */
                                feedback = this.noiseShiftRegister & this.whiteNoiseFeedback.v;
                                feedback ^= feedback >> 8;
                                feedback ^= feedback >> 4;
                                feedback ^= feedback >> 2;
                                feedback ^= feedback >> 1;
                                feedback &= 1;
                                break;
                            }
                        } else      /* Periodic noise */
                            feedback = this.noiseShiftRegister & 1;

                        this.noiseShiftRegister = (this.noiseShiftRegister >> 1) | (feedback << (this.srWidth.v - 1));
                    }
                }
            }

            return this.volume;
        }

        private void write(int data) {
            if ((data & 0x80) != 0) {
                /* Latch/data byte  %1 cc t dddd */
                this.latchedRegister = (data >> 4) & 0x07;
                this.registers[this.latchedRegister] =
                        (this.registers[this.latchedRegister] & 0x3f0) /* zero low 4 bits */
                                | (data & 0xf);                            /* and replace with data */
            } else {
                /* Data byte        %0 - dddddd */
                if ((this.latchedRegister % 2) == 0 && (this.latchedRegister < 5))
                    /* Tone register */
                    this.registers[this.latchedRegister] =
                            (this.registers[this.latchedRegister] & 0x00f) /* zero high 6 bits */
                                    | ((data & 0x3f) << 4);                 /* and replace with data */
                else
                    /* Other register */
                    this.registers[this.latchedRegister] = data & 0x0f; /* Replace with data */
            }
            switch (this.latchedRegister) {
            case 0:
            case 2:
            case 4: /* Tone channels */
                if (this.registers[this.latchedRegister] == 0)
                    this.registers[this.latchedRegister] = 1; /* Zero frequency changed to 1 to avoid div/0 */
                break;
            case 6: /* Noise */
                this.noiseShiftRegister = NoiseInitialState;        /* reset shift register */
                this.noiseFreq = 0x10 << (this.registers[6] & 0x3); /* set noise signal generator frequency */
                break;
            }
        }

        public void setMute(int val) {
            this.mute = val;
        }
    }

    private static final int DefaultPSGClockValue = 3579545;

    private static final int MAX_CHIPS = 2;

    public SN76489Context[] chips = new SN76489Context[] {new SN76489Context(), new SN76489Context()};

    public void SN76489_GGStereoWrite(byte chipID, int data) {
        SN76489Context chip = chips[chipID];
        chip.GGStereoWrite(data);
    }

    @Override
    public String getName() {
        return "SN76489";
    }

    @Override
    public String getShortName() {
        return "DCSG";
    }

    public Sn76489() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
        //0..Main
    }

    @Override
    public int start(byte chipID, int clock) {
        return start(chipID, DefaultPSGClockValue, clock);
    }

    @Override
    public int start(byte chipID, int samplingRate, int clockValue, Object... option) {
        chips[chipID] = new SN76489Context();
        SN76489Context chip = chips[chipID];
        return chip.start(samplingRate, clockValue);
    }

    @Override
    public void reset(byte chipID) {
        SN76489Context chip = chips[chipID];
        chip.reset();
    }

    @Override
    public void stop(byte chipID) {
        chips[chipID] = null;
    }

    @Override
    public void update(byte chipID, int[][] buffer, int length) {
        SN76489Context chip = chips[chipID];
        int[][] volumes = chip.update(buffer, length);

        visVolume[chipID][0][0] = volumes[0][0];
        visVolume[chipID][0][1] = volumes[0][1];
    }

    private void SN76489_Write(byte chipID, int data) {
        SN76489Context chip = chips[chipID];
        chip.write(data);
    }

    public void SN76489_SetMute(byte chipID, int val) {
        SN76489Context chip = chips[chipID];
        chip.setMute(val);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        SN76489_Write(chipID, data);
        return 0;
    }
}

