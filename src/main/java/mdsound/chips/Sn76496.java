package mdsound.chips;


/**
 * sn76496
 * by Nicola Salmoria
 * with contributions by others
 *
 * Routines to emulate the:
 * Texas Instruments SN76489, SN76489A, SN76494/Sn76496
 * ( Also known as, or at least compatible with, the TMS9919 and SN94624.)
 * and the Sega 'Psg' used on the Master System, Game Gear, and Megadrive/Genesis
 * This chips is known as the Programmable Sound Generator, or Psg, and is a 4
 * channel Sound generator, with three squarewave channels and a noise/arbitrary
 * duty cycle channel.
 *
 * Noise emulation for all verified chips should be accurate:
 *
 * ** SN76489 uses a 15-bit shift register with taps on bits D and E, output on E,
 *    XOR function.
 *    It uses a 15-bit ring buffer for periodic noise/arbitrary duty cycle.
 *    Its output is inverted.
 * ** SN94624 is the same as SN76489 but lacks the /8 divider on its clock input.
 * ** SN76489A uses a 15-bit shift register with taps on bits D and E, output on F,
 *    XOR function.
 *    It uses a 15-bit ring buffer for periodic noise/arbitrary duty cycle.
 *    Its output is not inverted.
 * ** SN76494 is the same as SN76489A but lacks the /8 divider on its clock input.
 * ** Sn76496 is identical in operation to the SN76489A, but the audio input is
 *    documented.
 *    All the TI-made Psg chips have an audio input line which is mixed with the 4 channels
 *    of output. (It is undocumented and may not function properly on the Sn76489, 76489a
 *    and 76494; the sn76489a input is mentioned in datasheets for the tms5200)
 *    All the TI-made Psg chips act as if the frequency was set to 0x400 if 0 is
 *    written to the frequency register.
 * ** Sega Master System III/MD/Genesis Psg uses a 16-bit shift register with taps
 *    on bits C and F, output on F
 *    It uses a 16-bit ring buffer for periodic noise/arbitrary duty cycle.
 *    (whether it uses an XOR or XNOR needs to be verified, assumed XOR)
 *    (whether output is inverted or not needs to be verified, assumed to be inverted)
 * ** Sega Game Gear Psg is identical to the SMS3/MD/Genesis one except it has an
 *    extra register for mapping which channels go to which speaker.
 *    The register, connected to a z80 port, means:
 *    for bits 7  6  5  4  3  2  1  0
 *             L3 L2 L1 L0 R3 R2 R1 R0
 *    Noise is an XOR function, and audio output is negated before being output.
 *    All the Sega-made Psg chips act as if the frequency was set to 0 if 0 is written
 *    to the frequency register.
 * ** NCR7496 (as used on the Tandy 1000) is similar to the SN76489 but with a
 *    different noise LFSR patttern: taps on bits A and E, output on E
 *    It uses a 15-bit ring buffer for periodic noise/arbitrary duty cycle.
 *    (all this chips's info needs to be verified)
 *
 * 28/03/2005 : Sebastien Chevalier
 *  Update th SN76496Write func, according to SN76489 doc found on SMSPower.
 *  - On write with 0x80 set to 0, when LastRegister is other then TONE,
 *    the function is similar than update with 0x80 set to 1
 *
 * 23/04/2007 : Lord Nightmare
 *  Major update, implement all three different noise generation algorithms and a
 *  set_variant call to discern among them.
 *
 * 28/04/2009 : Lord Nightmare
 *  Add READY line readback; cleaned up struct a bit. Cleaned up comments.
 *  Add more TODOs. Fixed some unsaved savestate related stuff.
 *
 * 04/11/2009 : Lord Nightmare
 *  Changed the way that the invert works (it now selects between XOR and XNOR
 *  for the taps), and added R.OldNoise to simulate the extra 0 that is always
 *  output before the noise LFSR contents are after an LFSR reset.
 *  This fixes SN76489/A to match chips. Added SN94624.
 *
 * 14/11/2009 : Lord Nightmare
 *  Removed STEP mess, vastly simplifying the code. Made output bipolar rather
 *  than always above the 0 line, but disabled that code due to pending issues.
 *
 * 16/11/2009 : Lord Nightmare
 *  Fix screeching in regulus: When summing together four equal channels, the
 *  size of the max amplitude per channel should be 1/4 of the max range, not
 *  1/3. Added NCR7496.
 *
 * 18/11/2009 : Lord Nightmare
 *  Modify Init functions to support negating the audio output. The gamegear
 *  psg does this. Change gamegear and sega psgs to use XOR rather than XNOR
 *  based on testing. Got rid of R.OldNoise and fixed taps accordingly.
 *  Added stereo support for game gear.
 *
 * 15/01/2010 : Lord Nightmare
 *  Fix an issue with SN76489 and SN76489A having the wrong periodic noise periods.
 *  Note that properly emulating the noise cycle bit timing accurately may require
 *  extensive rewriting.
 *
 * 24/01/2010: Lord Nightmare
 *  Implement periodic noise as forcing one of the XNOR or XOR taps to 1 or 0 respectively.
 *  Thanks to PlgDavid for providing samples which helped immensely here.
 *  Added true clock divider emulation, so sn94624 and sn76494 run 8x faster than
 *  the others, as in real life.
 *
 * 15/02/2010: Lord Nightmare & Michael Zapf (additional testing by PlgDavid)
 *  Fix noise period when set to mirror channel 3 and channel 3 period is set to 0 (tested on hardware for noise, wave needs tests) - MZ
 *  Fix phase of noise on sn94624 and Sn76489; all chips use a standard XOR, the only inversion is the output itself - LN, Plgdavid
 *  Thanks to PlgDavid and Michael Zapf for providing samples which helped immensely here.
 *
 * 23/02/2011: Lord Nightmare & Enik
 *  Made it so the Sega Psg chips have a frequency of 0 if 0 is written to the
 *  frequency register, while the others have 0x400 as before. Should fix a bug
 *  or two on sega games, particularly Vigilante on Sega Master System. Verified
 *  on SMS hardware.
 *
 * TODO: * Implement the TMS9919 - any difference to sn94624?
 *       * Implement the T6W28; has registers in a weird order, needs writes
 *         to be 'sanitized' first. Also is stereo, similar to game gear.
 *       * Test the NCR7496; Smspower says the whitenoise taps are A and E,
 *         but this needs verification on real hardware.
 *       * Factor out common code so that the SAA1099 can share some code.
 *       * Convert to modern device
 *
 * Note: I patched the core to speed the emulation up (factor 8!!)
 *       My Pentium2 233MHz was too slow for two Sn76496 chips in release mode!
 *       Now a 2xSN76496 vgm takes about 45 % CPU.
 */
public class Sn76496 {

    private static final int MAX_OUTPUT = 0x8000;

    /** volume table (for 4-bit to db conversion) */
    private int[] volTable = new int[16];
    /* registers */
    private int[] register = new int[8];
    /* last register written */
    private int lastRegister;
    /* db volume of Voice 0-2 and noise */
    private int[] volume = new int[4];
    /* noise generator LFSR */
    private int rng;
    /* clock divider */
    private int clockDivider;
    private int currentClock;
    /* mask for feedback */
    private int feedbackMask;
    /* mask for white noise tap 1 (higher one, usually bit 14) */
    private int whiteNoiseTap1;
    /* mask for white noise tap 2 (lower one, usually bit 13) */
    private int whiteNoiseTap2;
    /* output negate flag */
    private int negate;
    /* whether we're dealing with stereo or not */
    private int stereo;
    /* the stereo output mask */
    private int stereoMask;
    /* Length of 1/2 of waveform */
    private int[] period = new int[4];
    /* Position within the waveform */
    private int[] count = new int[4];
    /* 1-bit output of each channel, pre-volume */
    private int[] output = new int[4];
    /* number of cycles until the READY line goes active */
    private int cyclestoReady;
    /* flag for if frequency zero acts as if it is one more than max (0x3ff+1) or if it acts like 0 */
    private int freq0IsMax;
    private int[] muteMsk = new int[4];
    /* bit 7 - NGP Mode on/off, bit 0 - is 2nd NGP chips */
    private byte ngpFlags;
    /* Pointer to other Chip */
    private Sn76496 ngpChip2;

    public static Sn76496 lastChipInit = null;
    public static int fNumLimit;

    private byte readyR(int offset) {
        return (byte) (this.cyclestoReady != 0 ? 0 : 1);
    }

    public void writeStereo(int offset, byte data) {
        if (this.stereo != 0) this.stereoMask = data;
        //else Debug.printf("Call to stereo write with mono chips!\n");
    }

    public void writeReg(int offset, byte data) {
        int n, r, c;

        // set number of cycles until READY is active; this is always one
        // 'sample', i.e. it equals the clock divider exactly; until the
        // clock divider is fully supported, we delay until one sample has
        // played. The fact that this below is '2' and not '1' is because
        // of a ?race condition? in the mess crvision driver, where after
        // any sample is played at all, no matter what, the cycles_to_ready
        // ends up never being not ready, unless this value is greater than
        // 1. Once the full clock divider stuff is written, this should no
        // longer be an issue.
        this.cyclestoReady = 2;

        if ((data & 0x80) != 0) {
            r = (data & 0x70) >> 4;
            this.lastRegister = r;
            this.register[r] = (this.register[r] & 0x3f0) | (data & 0x0f);
        } else {
            r = this.lastRegister;
        }
        c = r / 2;
        switch (r) {
        case 0: /* tone 0 : frequency */
        case 2: /* tone 1 : frequency */
        case 4: /* tone 2 : frequency */
            if ((data & 0x80) == 0) this.register[r] = (this.register[r] & 0x0f) | ((data & 0x3f) << 4);
            if ((this.register[r] != 0) || (this.freq0IsMax == 0)) this.period[c] = this.register[r];
            else this.period[c] = 0x400;
            if (r == 4) {
                /* update noise shift frequency */
                if ((this.register[6] & 0x03) == 0x03)
                    this.period[3] = 2 * this.period[2];
            }
            break;
        case 1: /* tone 0 : volume */
        case 3: /* tone 1 : volume */
        case 5: /* tone 2 : volume */
        case 7: /* noise  : volume */
            this.volume[c] = this.volTable[data & 0x0f];
            if ((data & 0x80) == 0) this.register[r] = (this.register[r] & 0x3f0) | (data & 0x0f);

            // // "Every volume write resets the waveform to High level.", TmEE, 2012-11-24 on SMSPower
            // this.Output[c] = 1;
            // this.Count[c] = this.Period[c];
            // disabled for now - sounds awful
            break;
        case 6: { // noise  : frequency, mode
// #if DEBUG
            //if ((data & 0x80) == 0) Debug.printf("Sn76489: write to reg 6 with bit 7 clear; data was %03x, new write is %02x! report this to LN!\n", this.Register[6], data);
// #endif
            if ((data & 0x80) == 0) this.register[r] = (this.register[r] & 0x3f0) | (data & 0x0f);
            n = this.register[6];
            /* N/512,N/1024,N/2048,Tone // #3 output */
            this.period[3] = ((n & 3) == 3) ? 2 * this.period[2] : (1 << (5 + (n & 3)));
            this.rng = this.feedbackMask;
        }
        break;
        }
    }

    int[] vol = new int[4];
    int[] ggst = new int[2];

    public void update(int[][] outputs, int samples) {
        int i;
        Sn76496 r2;
        int[] lBuffer = outputs[0];
        int[] rBuffer = outputs[1];
        int _out = 0;
        int _out2 = 0;
        byte ngpMode;

        ngpMode = (byte) ((this.ngpFlags >> 7) & 0x01);
        r2 = this.ngpChip2;

        if (ngpMode == 0) {
            // Speed Hack
            _out = 0;
            for (i = 0; i < 3; i++) {
                if (this.period[i] != 0 || this.volume[i] != 0) {
                    _out = 1;
                    break;
                }
            }
            if (this.volume[3] != 0)
                _out = 1;
            if (_out == 0) {
                for (int j = 0; j < samples; j++) {
                    lBuffer[j] = 0x00;
                    rBuffer[j] = 0x00;
                }
                return;
            }
        }

        ggst[0] = 0x01;
        ggst[1] = 0x01;
        int ptr = 0;
        while (samples > 0) {
            // Speed Patch
//            // clock chips once
//            if (this.CurrentClock > 0) { // not ready for new divided clock
//                this.CurrentClock--;
//            } else { // ready for new divided clock, make a new sample
//                this.CurrentClock = this.ClockDivider - 1;
            // decrement Cycles to READY by one
            if (this.cyclestoReady > 0) this.cyclestoReady--;

            // handle channels 0,1,2
            for (i = 0; i < 3; i++) {
                this.count[i]--;
                if (this.count[i] <= 0) {
                    this.output[i] ^= 1;
                    this.count[i] = this.period[i];
                }
            }

            // handle channel 3
            this.count[3]--;
            if (this.count[3] <= 0) {
                // if noisemode is 1, both taps are enabled
                // if noisemode is 0, the lower tap, whitenoisetap2, is held at 0
                if ((
                        ((this.rng & this.whiteNoiseTap1) != 0 ? 1 : 0)
                                ^
                                (((this.rng & this.whiteNoiseTap2) != 0 ? 1 : 0) * ((this.register[6] & 4) != 0 ? 1 : 0))
                ) != 0) {
                    this.rng >>= 1;
                    this.rng |= this.feedbackMask;
                } else {
                    this.rng >>= 1;
                }
                this.output[3] = this.rng & 1;

                this.count[3] = this.period[3];
            }
//            }

//            if (this.Stereo) {
//                out = (((this.StereoMask & 0x10) && this.Output[0]) ? this.Volume[0] : 0)
//                        + (((this.StereoMask & 0x20) && this.Output[1]) ? this.Volume[1] : 0)
//                        + (((this.StereoMask & 0x40) && this.Output[2]) ? this.Volume[2] : 0)
//                        + (((this.StereoMask & 0x80) && this.Output[3]) ? this.Volume[3] : 0);
//
//                out2 = (((this.StereoMask & 0x1) && this.Output[0]) ? this.Volume[0] : 0)
//                        + (((this.StereoMask & 0x2) && this.Output[1]) ? this.Volume[1] : 0)
//                        + (((this.StereoMask & 0x4) && this.Output[2]) ? this.Volume[2] : 0)
//                        + (((this.StereoMask & 0x8) && this.Output[3]) ? this.Volume[3] : 0);
//            } else {
//                out = (this.Output[0] ? this.Volume[0] : 0)
//                        + (this.Output[1] ? this.Volume[1] : 0)
//                        + (this.Output[2] ? this.Volume[2] : 0)
//                        + (this.Output[3] ? this.Volume[3] : 0);
//            }

            // CUSTOM CODE START
            _out = _out2 = 0;
            if (this.ngpFlags == 0) {
                for (i = 0; i < 4; i++) {
                    // Preparation Start
                    // Bipolar output
                    vol[i] = this.output[i] != 0 ? +1 : -1;

                    // Disable high frequencies (> SampleRate / 2) for tone channels
                    // Freq. 0/1 isn't disabled becaus it would also disable PCM
                    if (i != 3) {
                        if (this.period[i] <= fNumLimit && this.period[i] > 1)
                            vol[i] = 0;
                    }
                    vol[i] &= this.muteMsk[i];
                    // Preparation End

                    if (this.stereo != 0) {
                        ggst[0] = (this.stereoMask & (0x10 << i)) != 0 ? 0x01 : 0x00;
                        ggst[1] = (this.stereoMask & (0x01 << i)) != 0 ? 0x01 : 0x00;
                    }
                    if (this.period[i] > 1 || i == 3) {
                        _out += vol[i] * this.volume[i] * ggst[0];
                        _out2 += vol[i] * this.volume[i] * ggst[1];
                    } else if (this.muteMsk[i] != 0) {
                        // Make Bipolar Output with PCM possible
                        _out += this.volume[i] * ggst[0];
                        _out2 += this.volume[i] * ggst[1];
                    }
                }
            } else {
                if ((this.ngpFlags & 0x01) == 0) {
                    // Tone Channel 1-3
                    if (this.stereo != 0) {
                        ggst[0] = (this.stereoMask & (0x10 << i)) != 0 ? 0x01 : 0x00;
                        ggst[1] = (this.stereoMask & (0x01 << i)) != 0 ? 0x01 : 0x00;
                    }
                    for (i = 0; i < 3; i++) {
                        // Preparation Start
                        // Bipolar output
                        vol[i] = this.output[i] != 0 ? +1 : -1;

                        // Disable high frequencies (> SampleRate / 2) for tone channels
                        // Freq. 0 isn't disabled becaus it would also disable PCM
                        if (this.period[i] <= fNumLimit && this.period[i] != 0)
                            vol[i] = 0;
                        vol[i] &= this.muteMsk[i];
                        // Preparation End

                        //out += vol[i] * this.Volume[i];
                        //out2 += vol[i] * r2.Volume[i];
                        if (this.period[i] != 0) {
                            _out += vol[i] * this.volume[i] * ggst[0];
                            _out2 += vol[i] * r2.volume[i] * ggst[1];
                        } else if (this.muteMsk[i] != 0) {
                            // Make Bipolar Output with PCM possible
                            _out += this.volume[i] * ggst[0];
                            _out2 += r2.volume[i] * ggst[1];
                        }
                    }
                } else {
                    // Preparation Start
                    // Bipolar output
                    vol[i] = this.output[i] != 0 ? +1 : -1;

                    //vol[i] &= this.MuteMsk[i];
                    vol[i] &= r2.muteMsk[i]; // use muteMask from chips 0
                    // Preparation End

                    // Noise Channel
                    if (this.stereo != 0) {
                        ggst[0] = (this.stereoMask & 0x80) != 0 ? 0x01 : 0x00;
                        ggst[1] = (this.stereoMask & 0x08) != 0 ? 0x01 : 0x00;
                    } else {
                        ggst[0] = 0x01;
                        ggst[1] = 0x01;
                    }
                    //out += vol[3] * r2.Volume[3];
                    //out2 += vol[3] * this.Volume[3];
                    _out += vol[3] * r2.volume[3] * ggst[0];
                    _out2 += vol[3] * this.volume[3] * ggst[1];
                }
            }
            // CUSTOM CODE END

            if (this.negate != 0) {
                _out = -_out;
                _out2 = -_out2;
            }

            lBuffer[ptr] = _out >> 1; // Output is Bipolar
            rBuffer[ptr] = _out2 >> 1;
            ptr++;
            samples--;
        }
    }

    private void setGain(int gain) {
        int i;
        double _out;


        gain &= 0xff;

        // increase max output basing on gain (0.2 dB per step)
        _out = MAX_OUTPUT / 4; // four channels, each gets 1/4 of the total range
        while (gain-- > 0)
            _out *= 1.023292992; // = (10 ^ (0.2/20))

        /* build volume table (2dB per step) */
        for (i = 0; i < 15; i++) {
            /* limit volume to avoid clipping */
            if (_out > MAX_OUTPUT / 4) this.volTable[i] = MAX_OUTPUT / 4;
                //else this.VolTable[i] = out;
            else this.volTable[i] = (int) (_out + 0.5); // I like rounding

            _out /= 1.258925412; // = 10 ^ (2/20) = 2dB
        }
        this.volTable[15] = 0;
    }

    private int init(int clock, int stereo) {
        int sampleRate = clock / 2;

        for (int i = 0; i < 4; i++) this.volume[i] = 0;

        this.lastRegister = 0;
        for (int i = 0; i < 8; i += 2) {
            this.register[i] = 0;
            this.register[i + 1] = 0x0f; // volume = 0
        }

        for (int i = 0; i < 4; i++) {
            this.output[i] = this.period[i] = this.count[i] = 0;
            this.muteMsk[i] = ~0x00;
        }

        // Default is SN76489A
        this.clockDivider = 8;
        this.feedbackMask = 0x10000; // mask for feedback
        this.whiteNoiseTap1 = 0x04; // mask for white noise tap 1
        this.whiteNoiseTap2 = 0x08; // mask for white noise tap 2
        this.negate = 0; // channels are not negated
        this.stereo = stereo; // depends on init
        this.cyclestoReady = 1; // assume ready is not active immediately on init. is this correct?
        this.stereoMask = 0xFF; // all channels enabled
        this.freq0IsMax = 1; // frequency set to 0 results in freq = 0x400 rather than 0

        this.rng = this.feedbackMask;
        this.output[3] = this.rng & 1;

        this.ngpFlags = 0x00;
        this.ngpChip2 = null;

        return sampleRate;
    }

    private int startGeneric(int clock, int feedbackMask, int noiseTap1, int noiseTap2, int negate, int stereo, int clockDivider, int freq0) {
        int sampleRate = init(clock & 0x7FFFFFFF, stereo);
        if ((clock & 0x80000000) != 0 && lastChipInit != null) {
            // Activate special NeoGeoPocket Mode
            Sn76496 chip2 = lastChipInit;
            chip2.ngpFlags = (byte) 0x80 | 0x00;
            this.ngpFlags = (byte) 0x80 | 0x01;
            this.ngpChip2 = chip2;
            chip2.ngpChip2 = this;
            lastChipInit = null;
        } else {
            lastChipInit = this;
        }
        setGain(0);

        this.feedbackMask = feedbackMask;
        this.whiteNoiseTap1 = noiseTap1;
        this.whiteNoiseTap2 = noiseTap2;
        this.negate = negate;
        this.stereo = stereo;
        if (clockDivider != 0)
            this.clockDivider = clockDivider;
        this.currentClock = clockDivider - 1;
        this.freq0IsMax = freq0;

        // Speed Patch
        sampleRate /= this.clockDivider;

        return sampleRate;
    }

    public long start(int clock, int shiftRegWidth, int noiseTaps,
                      int negate, int stereo, int clockDivider, int freq0) {
        int[] ntap = new int[2];
        int curbit;

        // extract single noise tap bits
        int curtap = 0;
        for (curbit = 0; curbit < 16; curbit++) {
            if ((noiseTaps & (1 << curbit)) != 0) {
                ntap[curtap] = (1 << curbit);
                curtap++;
                if (curtap >= 2)
                    break;
            }
        }
        while (curtap < 2) {
            ntap[curtap] = ntap[0];
            curtap++;
        }

        return startGeneric(clock, 1 << (shiftRegWidth - 1), ntap[0], ntap[1],
                negate, stereo == 0 ? 1 : 0, clockDivider != 0 ? 1 : 8, freq0);
    }

    public void stop() {
    }

    public void reset() {
        for (byte i = 0; i < 4; i++) this.volume[i] = 0;

        this.lastRegister = 0;
        for (byte i = 0; i < 8; i += 2) {
            this.register[i] = 0;
            this.register[i + 1] = 0x0f; // volume = 0
        }

        for (byte i = 0; i < 4; i++) {
            this.output[i] = this.period[i] = this.count[i] = 0;
        }

        this.cyclestoReady = 1;
        this.stereoMask = 0xFF; // all channels enabled

        this.rng = this.feedbackMask;
        this.output[3] = this.rng & 1;
    }

    public void limitFreq(int clock, int clockdiv, int sampleRate) {
        fNumLimit = (int) ((clock / (clockdiv != 0 ? 2.0 : 16.0)) / sampleRate);
    }

    private void setMuteMask(int muteMask) {
        for (byte curChn = 0; curChn < 4; curChn++)
            this.muteMsk[curChn] = (muteMask & (1 << curChn)) != 0 ? 0 : ~0;
    }
}
