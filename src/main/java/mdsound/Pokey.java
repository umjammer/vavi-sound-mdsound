/*
 *  POKEY chip emulator 4.51
 *  Copyright Nicola Salmoria and the MAME Team
 *
 *  Based on original info found in Ron Fries' Pokey emulator,
 *  with additions by Brad Oliver, Eric Smith and Juergen Buchmueller.
 *  paddle (a/d conversion) details from the Atari 400/800 Hardware Manual.
 *  Polynome algorithms according to info supplied by Perry McFarlane.
 *
 *  This code is subject to the MAME license, which besides other
 *  things means it is distributed as is, no warranties whatsoever.
 *  For more details read mame.txt that comes with MAME.
 */

package mdsound;


import java.util.logging.Logger;


public class Pokey extends Instrument.BaseInstrument {
    @Override
    public String getName() {
        return "POKEY";
    }

    @Override
    public String getShortName() {
        return "POKEY";
    }

    @Override
    public void reset(byte chipId) {
        device_reset_pokey(chipId);
    }

    @Override
    public int start(byte chipId, int clock) {
        return device_start_pokey(chipId, 1789772);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        return device_start_pokey(chipId, clockValue);
    }

    @Override
    public void stop(byte chipId) {
        device_stop_pokey(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        pokey_update(chipId, outputs, samples);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        pokey_w(chipId, adr, (byte) data);
        return 0;
    }

    /**
     * POKEY chip emulator 4.51
     * Copyright Nicola Salmoria and the MAME Team
     * <p>
     * Based on original info found in Ron Fries' Pokey emulator,
     * with additions by Brad Oliver, Eric Smith and Juergen Buchmueller,
     * paddle (a/d conversion) details from the Atari 400/800 Hardware Manual.
     * Polynome algorithms according to info supplied by Perry McFarlane.
     * <p>
     * This code is subject to the MAME license, which besides other
     * things means it is distributed as is, no warranties whatsoever.
     * For more details read mame.txt that comes with MAME.
     * <p>
     * 4.51:
     * - changed to use the attotime datatype
     * 4.5:
     * - changed the 9/17 bit polynomial formulas such that the values
     * required for the Tempest Pokey protection will be found.
     * Tempest expects the upper 4 bits of the RNG to appear in the
     * lower 4 bits after four cycles, so there has to be a shift
     * of 1 per cycle (which was not the case before). Bits // #6-#13 of the
     * new RNG give this expected result now, bits // #0-7 of the 9 bit poly.
     * - reading the RNG returns the shift register contents ^ 0xff.
     * That way resetting the Pokey with SKCTL (which resets the
     * polynome shifters to 0) returns the expected 0xff value.
     * 4.4:
     * - reversed sample values to make OFF channels produce a zero signal.
     * actually de-reversed them; don't remember that I reversed them ;-/
     * 4.3:
     * - for POT inputs returning zero, immediately assert the ALLPOT
     * bit after POTGO is written, otherwise start trigger timer
     * depending on SK_PADDLE mode, either 1-228 scanlines or 1-2
     * scanlines, depending on the SK_PADDLE bit of SKCTL.
     * 4.2:
     * - half volume for channels which are inaudible (this should be
     * close to the real thing).
     * 4.1:
     * - default gain increased to closely match the old code.
     * - random numbers repeat rate depends on POLY9 flag too!
     * - verified Sound output with many, many Atari 800 games,
     * including the SUPPRESS_INAUDIBLE optimizations.
     * 4.0:
     * - rewritten from scratch.
     * - 16bit stream interface.
     * - serout ready/complete delayed interrupts.
     * - reworked pot anaSystem.err.printf/digital conversion timing.
     * - optional non-indexing Pokey update functions.
     */
    public static class PokeyState {

        // POKEY WRITE System.err.printfICALS
        private static final int AUDF1_C = 0x00;
        private static final int AUDC1_C = 0x01;
        private static final int AUDF2_C = 0x02;
        private static final int AUDC2_C = 0x03;
        private static final int AUDF3_C = 0x04;
        private static final int AUDC3_C = 0x05;
        private static final int AUDF4_C = 0x06;
        private static final int AUDC4_C = 0x07;
        private static final int AUDCTL_C = 0x08;
        private static final int STIMER_C = 0x09;
        private static final int SKREST_C = 0x0A;
        private static final int POTGO_C = 0x0B;
        private static final int SEROUT_C = 0x0D;
        private static final int IRQEN_C = 0x0E;
        private static final int SKCTL_C = 0x0F;

        // POKEY READ System.err.printfICALS
        private static final int POT0_C = 0x00;
        private static final int POT1_C = 0x01;
        private static final int POT2_C = 0x02;
        private static final int POT3_C = 0x03;
        private static final int POT4_C = 0x04;
        private static final int POT5_C = 0x05;
        private static final int POT6_C = 0x06;
        private static final int POT7_C = 0x07;
        private static final int ALLPOT_C = 0x08;
        private static final int KBCODE_C = 0x09;
        private static final int RANDOM_C = 0x0A;
        private static final int SERIN_C = 0x0D;
        private static final int IRQST_C = 0x0E;
        private static final int SKSTAT_C = 0x0F;

        /** exact 1.79 MHz clock freq (of the Atari 800 that is) */
        private static final int FREQ_17_EXACT = 1789790;

        /*
         * pot0_r to pot7_r:
         *  Handlers for reading the pot values. Some Atari games use
         *  ALLPOT to return dipswitch settings and other things.
         * serin_r, serout_w, interrupt_cb:
         *  New function pointers for serial input/output and a interrupt Callback.
         */

        /*
         * Defining this produces much more (about twice as much)
         * but also more efficient code. Ideally this should be set
         * for processors with big code cache and for healthy compilers :)
         */
        private static final int HEAVY_MACRO_USAGE = 1;

        private static final int SUPPRESS_INAUDIBLE = 1;

        /*
         * But we raise the gain and risk clipping, the old Pokey did
         * this too. It defined POKEY_DEFAULT_GAIN 6 and this was
         * 6 * 15 * 4 = 360, 360/256 = 1.40625
         * I use 15/11 = 1.3636, so this is a little lower.
         */
        private static final int POKEY_DEFAULT_GAIN = (32767 / 11 / 4);

        private Logger logger = Logger.getLogger(Pokey.class.getName());
        private Logger LOG_SOUND = Logger.getLogger(Pokey.class.getName() + ".SOUND");
        private Logger LOG_TIMER = Logger.getLogger(Pokey.class.getName() + ".TIMER");
        private Logger LOG_POLY = Logger.getLogger(Pokey.class.getName() + ".POLY");
        private Logger LOG_RAND = Logger.getLogger(Pokey.class.getName() + ".RAND");

        private static final int CHAN1 = 0;
        private static final int CHAN2 = 1;
        private static final int CHAN3 = 2;
        private static final int CHAN4 = 3;

        private static final int TIMER1 = 0;
        private static final int TIMER2 = 1;
        private static final int TIMER4 = 2;

        // values to add to the divisors for the different modes

        private static final int DIVADD_LOCLK = 1;
        private static final int DIVADD_HICLK = 4;
        private static final int DIVADD_HICLK_JOINED = 7;

        // AUDCx

        /** selects POLY5 or direct CLOCK */
        private static final int NOTPOLY5 = 0x80;
        /** selects POLY4 or POLY17  */
        private static final int POLY4 = 0x40;
        /** selects POLY4/17 or PURE tone  */
        private static final int PURE = 0x20;
        /** selects VOLUME OUTPUT ONLY  */
        private static final int VOLUME_ONLY = 0x10;
        /** volume mask  */
        private static final int VOLUME_MASK = 0x0f;

        // AUDCTL

        /** selects POLY9 or POLY17  */
        private static final int POLY9 = 0x80;
        /** selects 1.78979 MHz for Ch 1  */
        private static final int CH1_HICLK = 0x40;
        /** selects 1.78979 MHz for Ch 3  */
        private static final int CH3_HICLK = 0x20;
        /** clocks channel 1 w/channel 2  */
        private static final int CH12_JOINED = 0x10;
        /** clocks channel 3 w/channel 4  */
        private static final int CH34_JOINED = 0x08;
        /** selects channel 1 high pass filter  */
        private static final int CH1_FILTER = 0x04;
        /** selects channel 2 high pass filter  */
        private static final int CH2_FILTER = 0x02;
        /** selects 15.6999 kHz or 63.9211 kHz  */
        private static final int CLK_15KHZ = 0x01;

        // IRQEN (D20E)

        /** BREAK key pressed interrupt  */
        private static final int IRQ_BREAK = 0x80;
        /** keyboard data ready interrupt  */
        private static final int IRQ_KEYBD = 0x40;
        /** serial input data ready interrupt  */
        private static final int IRQ_SERIN = 0x20;
        /** serial output register ready interrupt  */
        private static final int IRQ_SEROR = 0x10;
        /** serial output complete interrupt  */
        private static final int IRQ_SEROC = 0x08;
        /** timer channel #4 interrupt  */
        private static final int IRQ_TIMR4 = 0x04;
        /** timer channel #2 interrupt  */
        private static final int IRQ_TIMR2 = 0x02;
        /** timer channel #1 interrupt  */
        private static final int IRQ_TIMR1 = 0x01;

        // SKSTAT (R/D20F)

        /** serial framing error  */
        private static final int SK_FRAME = 0x80;
        /** serial overrun error  */
        private static final int SK_OVERRUN = 0x40;
        /** keyboard overrun error  */
        private static final int SK_KBERR = 0x20;
        /** serial input high  */
        private static final int SK_SERIN = 0x10;
        /** shift key pressed  */
        private static final int SK_SHIFT = 0x08;
        /** keyboard key pressed  */
        private static final int SK_KEYBD = 0x04;
        /** serial output active  */
        private static final int SK_SEROUT = 0x02;

        // SKCTL (W/D20F)

        /** serial out break signal  */
        private static final int SK_BREAK = 0x80;
        /** bits per second  */
        private static final int SK_BPS = 0x70;
        /** FM mode  */
        private static final int SK_FM = 0x08;
        /** fast paddle a/d conversion  */
        private static final int SK_PADDLE = 0x04;
        /** reset serial/keyboard interface  */
        private static final int SK_RESET = 0x03;

        /** divisor for 1.78979 MHz clock to 63.9211 kHz  */
        private static final int DIV_64 = 28;
        /** divisor for 1.78979 MHz clock to 15.6999 kHz  */
        private static final int DIV_15 = 114;

        /** channel counter  */
        public int[] counter = new int[4];
        /** channel divisor (modulo value)  */
        public int[] divisor = new int[4];
        /** channel volume - derived  */
        public int[] volume = new int[4];
        /** channel output signal (1 active, 0 inactive)  */
        public byte[] output = new byte[4];
        /** channel plays an audible tone/effect  */
        public byte[] audible = new byte[4];
        public byte[] muted = new byte[4];
        /** sample rate in 24.8 format  */
        public int samplerate24_8;
        /** sample position fractional part  */
        public int samplePosFract;
        /** sample position whole part  */
        public int samplePosWhole;
        /** polynome adjustment  */
        public int polyAdjust;
        /** poly4 index  */
        public int p4;
        /** poly5 index  */
        public int p5;
        /** poly9 index  */
        public int p9;
        /** poly17 index  */
        public int p17;
        /** rand9 index  */
        public int r9;
        /** rand17 index  */
        public int r17;
        /** clock multiplier  */
        public int clockMult;

        /** AUDFx (D200, D202, D204, D206)  */
        public byte[] audioF = new byte[4];
        /** AUDCx (D201, D203, D205, D207)  */
        public byte[] audioC = new byte[4];
        /** POTx   (R/D200-D207)  */
        public byte[] POTx = new byte[8];
        /** AUDCTL (W/D208)  */
        public byte audioControl;
        /** ALLPOT (R/D208)  */
        public byte allPot;
        /** KBCODE (R/D209)  */
        public byte kbCode;
        /** RANDOM (R/D20A)  */
        public byte random;
        /** SERIN  (R/D20D)  */
        public byte serIn;
        /** SEROUT (W/D20D)  */
        public byte serOut;
        /** IRQST  (R/D20E)  */
        public byte irqSt;
        /** IRQEN  (W/D20E)  */
        public byte irqEn;
        /** SKSTAT (R/D20F)  */
        public byte skStat;
        /** SKCTL  (W/D20F)  */
        public byte skCtl;
        public double clockPeriod;

        public byte[] poly4 = new byte[0x0f];
        public byte[] poly5 = new byte[0x1f];
        public byte[] poly9 = new byte[0x1ff];
        public byte[] poly17 = new byte[0x1ffff];

        public byte[] rand9 = new byte[0x1ff];
        public byte[] rand17 = new byte[0x1ffff];

        private byte P4() {
            return this.poly4[this.p4];
        }

        private byte P5() {
            return this.poly5[this.p5];
        }

        private byte P9() {
            return this.poly9[this.p9];
        }

        private byte P17() {
            return this.poly17[this.p17];
        }

        private static final int SAMPLE = -1;

        private void adjustEvent(int event) {
            this.counter[CHAN1] -= event;
            this.counter[CHAN2] -= event;
            this.counter[CHAN3] -= event;
            this.counter[CHAN4] -= event;
            this.samplePosWhole -= event;
            this.polyAdjust += event;
        }

        private int processChannel(int ch, int event, int sum) {
            int toggle = 0;
            adjustEvent(event);
            // reset the channel counter
            if (this.audible[ch] != 0)
                this.counter[ch] = this.divisor[ch];
            else
                this.counter[ch] = 0x7fffffff;
            this.p4 = (this.p4 + this.polyAdjust) % 0x0000f;
            this.p5 = (this.p5 + this.polyAdjust) % 0x0001f;
            this.p9 = (this.p9 + this.polyAdjust) % 0x001ff;
            this.p17 = (this.p17 + this.polyAdjust) % 0x1ffff;
            this.polyAdjust = 0;
            if ((this.audioC[ch] & NOTPOLY5) != 0 || P5() != 0) {
                if ((this.audioC[ch] & PURE) != 0)
                    toggle = 1;
                else if ((this.audioC[ch] & POLY4) != 0)
                    toggle = ((this.output[ch] != 0 && P4() == 0) || (this.output[ch] == 0 && P4() != 0)) ? 1 : 0;
                else if ((this.audioControl & POLY9) != 0)
                    toggle = ((this.output[ch] != 0 && P9() == 0) || (this.output[ch] == 0 && P9() != 0)) ? 1 : 0;
                else
                    toggle = ((this.output[ch] != 0 && P17() == 0) || (this.output[ch] == 0 && P17() != 0)) ? 1 : 0;
            }
            if (toggle != 0) {
                if (this.audible[ch] != 0 && this.muted[ch] == 0) {
                    if (this.output[ch] != 0)
                        sum -= this.volume[ch];
                    else
                        sum += this.volume[ch];
                }
                this.output[ch] ^= 1;
            }
            // is this a filtering channel (3/4) and is the filter active?
            if ((this.audioControl & ((CH1_FILTER | CH2_FILTER) & (0x10 >> ch))) != 0) {
                if (this.output[ch - 2] != 0) {
                    this.output[ch - 2] = 0;
                    if (this.audible[ch] != 0 && this.muted[ch] == 0)
                        sum -= this.volume[ch - 2];
                }
            }
            return sum;
        }

        private void processSample(int[] bufL, int[] bufR, int bufPtr, int event, int sum) {
            adjustEvent(event);
            // adjust the sample position
            this.samplePosWhole++;
            // store sum of output signals into the buffer
            bufL[bufPtr] = bufR[bufPtr] = sum;
        }

        /**
         * This version of PROCESS_POKEY repeats the search for the minimum
         * event value without using an index to the channel. That way the
         * PROCESS_CHANNEL macros can be called with fixed values and expand
         * to much more efficient code
         */
        private void update(int[][] outputs, int samples) {
            int p = 0;
            int sum = 0;
            if (this.output[CHAN1] != 0 && this.muted[CHAN1] == 0)
                sum += this.volume[CHAN1];
            if (this.output[CHAN2] != 0 && this.muted[CHAN2] == 0)
                sum += this.volume[CHAN2];
            if (this.output[CHAN3] != 0 && this.muted[CHAN3] == 0)
                sum += this.volume[CHAN3];
            if (this.output[CHAN4] != 0 && this.muted[CHAN4] == 0)
                sum += this.volume[CHAN4];
            while (samples > 0) {
                if (this.counter[CHAN1] < this.samplePosWhole) {
                    if (this.counter[CHAN2] < this.counter[CHAN1]) {
                        if (this.counter[CHAN3] < this.counter[CHAN2]) {
                            if (this.counter[CHAN4] < this.counter[CHAN3]) {
                                int event = this.counter[CHAN4];
                                sum = processChannel(CHAN4, event, sum);
                            } else {
                                int event = this.counter[CHAN3];
                                sum = processChannel(CHAN3, event, sum);
                            }
                        } else if (this.counter[CHAN4] < this.counter[CHAN2]) {
                            int event = this.counter[CHAN4];
                            sum = processChannel(CHAN4, event, sum);
                        } else {
                            int event = this.counter[CHAN2];
                            sum = processChannel(CHAN2, event, sum);
                        }
                    } else if (this.counter[CHAN3] < this.counter[CHAN1]) {
                        if (this.counter[CHAN4] < this.counter[CHAN3]) {
                            int event = this.counter[CHAN4];
                            sum = processChannel(CHAN4, event, sum);
                        } else {
                            int event = this.counter[CHAN3];
                            sum = processChannel(CHAN3, event, sum);
                        }
                    } else if (this.counter[CHAN4] < this.counter[CHAN1]) {
                        int event = this.counter[CHAN4];
                        sum = processChannel(CHAN4, event, sum);
                    } else {
                        int event = this.counter[CHAN1];
                        sum = processChannel(CHAN1, event, sum);
                    }
                } else if (this.counter[CHAN2] < this.samplePosWhole) {
                    if (this.counter[CHAN3] < this.counter[CHAN2]) {
                        if (this.counter[CHAN4] < this.counter[CHAN3]) {
                            int event = this.counter[CHAN4];
                            sum = processChannel(CHAN4, event, sum);
                        } else {
                            int event = this.counter[CHAN3];
                            sum = processChannel(CHAN3, event, sum);
                        }
                    } else if (this.counter[CHAN4] < this.counter[CHAN2]) {
                        int event = this.counter[CHAN4];
                        sum = processChannel(CHAN4, event, sum);
                    } else {
                        int event = this.counter[CHAN2];
                        sum = processChannel(CHAN2, event, sum);
                    }
                } else if (this.counter[CHAN3] < this.samplePosWhole) {
                    if (this.counter[CHAN4] < this.counter[CHAN3]) {
                        int event = this.counter[CHAN4];
                        sum = processChannel(CHAN4, event, sum);
                    } else {
                        int event = this.counter[CHAN3];
                        sum = processChannel(CHAN3, event, sum);
                    }
                } else if (this.counter[CHAN4] < this.samplePosWhole) {
                    int event = this.counter[CHAN4];
                    sum = processChannel(CHAN4, event, sum);
                } else {
                    int event = this.samplePosWhole;
                    processSample(outputs[0], outputs[1], p, event, sum);
                    p++;
                    samples--;
                }
            }
//            this.rtimer.adjust(attotime::never);
        }

        private void init(byte[] poly, int size, int left, int right, int add) {
            int mask = (1 << size) - 1;
            int x = 0;

            LOG_POLY.fine(String.format("poly %d\n", size));
            for (int i = 0; i < mask; i++) {
                poly[i] = (byte) (x & 1);
                LOG_POLY.fine(String.format("%05x: %d\n", x, x & 1));
                /* calculate next bit */
                x = ((x << left) + (x >> right) + add) & mask;
            }
        }

        private void randRand(byte[] rng, int size, int left, int right, int add) {
            int mask = (1 << size) - 1;
            int x = 0;

            LOG_RAND.fine(String.format("rand %d\n", size));
            for (int i = 0; i < mask; i++) {
                if (size == 17)
                    rng[i] = (byte) (x >> 6); // use bits 6..13
                else
                    rng[i] = (byte) x; // use bits 0..7
                LOG_RAND.fine(String.format("%05x: %02x\n", x, rng[i]));
                // calculate next bit
                x = ((x << left) + (x >> right) + add) & mask;
            }
        }

        private int start(int clock) {
            int sampleRate = clock;

            this.clockPeriod = 1.0 / clock;

            // calculate the A/D times
            // In normal, slow mode (SKCTL bit SK_PADDLE is clear) the conversion
            // takes N scanlines, where N is the paddle value. A single scanline
            // takes approximately 64us to finish (1.78979MHz clock).
            // In quick mode (SK_PADDLE set) the conversion is done very fast
            // (takes two scanlines) but the result is not as accurate.

            // initialize the poly counters
            init(this.poly4, 4, 3, 1, 0x00004);
            init(this.poly5, 5, 3, 2, 0x00008);
            init(this.poly9, 9, 8, 1, 0x00180);
            init(this.poly17, 17, 16, 1, 0x1c000);

            // initialize the random arrays
            randRand(this.rand9, 9, 8, 1, 0x00180);
            randRand(this.rand17, 17, 16, 1, 0x1c000);

            this.samplerate24_8 = (clock << 8) / sampleRate;
            this.divisor[CHAN1] = 4;
            this.divisor[CHAN2] = 4;
            this.divisor[CHAN3] = 4;
            this.divisor[CHAN4] = 4;
            this.clockMult = DIV_64;
            this.kbCode = 0x09; // Atari 800 'no key'
            this.skCtl = SK_RESET; // let the RNG run after reset

            return sampleRate;
        }

        private void reset() {
            for (byte curChn = 0; curChn < 4; curChn++) {
                this.counter[curChn] = 0;
                this.divisor[curChn] = 4;
                this.volume[curChn] = 0;
                this.output[curChn] = 0;
                this.audible[curChn] = 0;
            }
            this.samplePosFract = 0;
            this.samplePosWhole = 0;
            this.polyAdjust = 0;
            this.p4 = 0;
            this.p5 = 0;
            this.p9 = 0;
            this.p17 = 0;
            this.r9 = 0;
            this.r17 = 0;
            this.clockMult = DIV_64;
        }

        private byte read(int offset) {
            int data = 0, pot;
            int adjust = 0;

            switch (offset & 15) {
            case POT0_C:
            case POT1_C:
            case POT2_C:
            case POT3_C:
            case POT4_C:
            case POT5_C:
            case POT6_C:
            case POT7_C:
                pot = offset & 7;
                    /*if( !this.pot_r[pot].isnull() ) {
                        // If the conversion is not yet finished (ptimer running),
                        // get the current value by the linear interpolation of
                        // the final value using the elapsed time.
                        if( this.ALLPOT & (1 << pot) ) {
                            //data = this.ptimer[pot].elapsed().attoseconds / AD_TIME.attoseconds;
                            data = this.POTx[pot];
                            Debug.printf("POKEY '%s' read POT%d (interpolated) $%02x\n", this.device.tag(), pot, data);
                        } else {
                            data = this.POTx[pot];
                            Debug.printf("POKEY '%s' read POT%d (final value)  $%02x\n", this.device.tag(), pot, data);
                        }
                    } else
                        Debug.printf("%s: warning - read '%s' POT%d\n", this.device.machine().describe_context(), this.device.tag(), pot);*/
                break;

            case ALLPOT_C:
                // If the 2 least significant bits of SKCTL are 0, the ALLPOTs
                // are disabled (SKRESET). Thanks to MikeJ for pointing this out.
                    /*if( (this.SKCTL & SK_RESET) == 0) {
                        data = 0;
                        Debug.printf("POKEY '%s' ALLPOT internal $%02x (reset)\n", this.device.tag(), data);
                    } else if( !this.allpot_r.isnull() ) {
                        data = this.allpot_r(offset);
                        Debug.printf("POKEY '%s' ALLPOT Callback $%02x\n", this.device.tag(), data);
                    } else {
                        data = this.ALLPOT;
                        Debug.printf("POKEY '%s' ALLPOT internal $%02x\n", this.device.tag(), data);
                    }*/
                break;

            case KBCODE_C:
                data = this.kbCode;
                break;

            case RANDOM_C:
                // If the 2 least significant bits of SKCTL are 0, the random
                // number generator is disabled (SKRESET). Thanks to Eric Smith
                // for pointing out this critical bit of info! If the random
                // number generator is enabled, get a new random number. Take
                // the time gone since the last read into account and read the
                // new value from an appropriate offset in the rand17 table.
                if ((this.skCtl & SK_RESET) != 0) {
                    adjust = 0;
                    this.r9 = (this.r9 + adjust) % 0x001ff;
                    this.r17 = (this.r17 + adjust) % 0x1ffff;
                } else {
                    adjust = 1;
                    this.r9 = 0;
                    this.r17 = 0;
                    //LOG_RAND(("POKEY '%s' rand17 frozen (SKCTL): $%02x\n", this.device.tag(), this.RANDOM));
                }
                if ((this.audioControl & POLY9) != 0) {
                    this.random = this.rand9[this.r9];
                    //LOG_RAND(("POKEY '%s' adjust %u rand9[$%05x]: $%02x\n", this.device.tag(), adjust, this.r9, this.RANDOM));
                } else {
                    this.random = this.rand17[this.r17];
                    //LOG_RAND(("POKEY '%s' adjust %u rand17[$%05x]: $%02x\n", this.device.tag(), adjust, this.r17, this.RANDOM));
                }
                //if (adjust > 0)
                // this.rtimer.adjust(attotime::never);
                data = this.random ^ 0xff;
                break;

            case SERIN_C:
                //if( !this.serin_r.isnull() )
                // this.SERIN = this.serin_r(offset);
                data = this.serIn;
                //Debug.printf(("POKEY '%s' SERIN  $%02x\n", this.device.tag(), data));
                break;

            case IRQST_C:
                // IRQST is an active low input port; we keep it active high
                // internally to ease the (un-)masking of bits
                data = this.irqSt ^ 0xff;
                //Debug.printf(("POKEY '%s' IRQST  $%02x\n", this.device.tag(), data));
                break;

            case SKSTAT_C:
                // SKSTAT is also an active low input port
                data = this.skStat ^ 0xff;
                //Debug.printf(("POKEY '%s' SKSTAT $%02x\n", this.device.tag(), data));
                break;

            default:
                //Debug.printf(("POKEY '%s' register $%02x\n", this.device.tag(), offset));
                break;
            }

            return (byte) data;
        }

        private void write(int offset, byte data) {
            int chMask = 0, newVal;

            //this.channel.update();

            // determine which address was changed
            switch (offset & 15) {
            case AUDF1_C:
                if (data == this.audioF[CHAN1])
                    return;
                //LOG_SOUND(("POKEY '%s' AUDF1  $%02x\n", this.device.tag(), data));
                this.audioF[CHAN1] = data;
                chMask = 1 << CHAN1;
                if ((this.audioControl & CH12_JOINED) != 0) // if ch 1&2 tied together
                    chMask |= 1 << CHAN2; // then also change on ch2
                break;

            case AUDC1_C:
                if (data == this.audioC[CHAN1])
                    return;
                //LOG_SOUND(("POKEY '%s' AUDC1  $%02x (%s)\n", this.device.tag(), data, audc2str(data)));
                this.audioC[CHAN1] = data;
                chMask = 1 << CHAN1;
                break;

            case AUDF2_C:
                if (data == this.audioF[CHAN2])
                    return;
                //LOG_SOUND(("POKEY '%s' AUDF2  $%02x\n", this.device.tag(), data));
                this.audioF[CHAN2] = data;
                chMask = 1 << CHAN2;
                break;

            case AUDC2_C:
                if (data == this.audioC[CHAN2])
                    return;
                //LOG_SOUND(("POKEY '%s' AUDC2  $%02x (%s)\n", this.device.tag(), data, audc2str(data)));
                this.audioC[CHAN2] = data;
                chMask = 1 << CHAN2;
                break;

            case AUDF3_C:
                if (data == this.audioF[CHAN3])
                    return;
                //LOG_SOUND(("POKEY '%s' AUDF3  $%02x\n", this.device.tag(), data));
                this.audioF[CHAN3] = data;
                chMask = 1 << CHAN3;

                if ((this.audioControl & CH34_JOINED) != 0) // if ch 3&4 tied together
                    chMask |= 1 << CHAN4; // then also change on ch4
                break;

            case AUDC3_C:
                if (data == this.audioC[CHAN3])
                    return;
                //LOG_SOUND(("POKEY '%s' AUDC3  $%02x (%s)\n", this.device.tag(), data, audc2str(data)));
                this.audioC[CHAN3] = data;
                chMask = 1 << CHAN3;
                break;

            case AUDF4_C:
                if (data == this.audioF[CHAN4])
                    return;
                //LOG_SOUND(("POKEY '%s' AUDF4  $%02x\n", this.device.tag(), data));
                this.audioF[CHAN4] = data;
                chMask = 1 << CHAN4;
                break;

            case AUDC4_C:
                if (data == this.audioC[CHAN4])
                    return;
                //LOG_SOUND(("POKEY '%s' AUDC4  $%02x (%s)\n", this.device.tag(), data, audc2str(data)));
                this.audioC[CHAN4] = data;
                chMask = 1 << CHAN4;
                break;

            case AUDCTL_C:
                if (data == this.audioControl)
                    return;
                //LOG_SOUND(("POKEY '%s' AUDCTL $%02x (%s)\n", this.device.tag(), data, audctl2str(data)));
                this.audioControl = data;
                chMask = 15; // all channels
                // determine the base multiplier for the 'div by n' calculations
                this.clockMult = (this.audioControl & CLK_15KHZ) != 0 ? DIV_15 : DIV_64;
                break;

            case STIMER_C:
                    /*// first remove any existing timers
                    LOG_TIMER(("POKEY '%s' STIMER $%02x\n", this.device.tag(), data));

                    this.timer[TIMER1].adjust(attotime::never, this.timer_param[TIMER1]);
                    this.timer[TIMER2].adjust(attotime::never, this.timer_param[TIMER2]);
                    this.timer[TIMER4].adjust(attotime::never, this.timer_param[TIMER4]);

                    // reset all counters to zero (side effect)
                    this.polyadjust = 0;
                    this.counter[CHAN1] = 0;
                    this.counter[CHAN2] = 0;
                    this.counter[CHAN3] = 0;
                    this.counter[CHAN4] = 0;

                    // joined chan#1 and chan#2 ?
                    if( this.AUDCTL & CH12_JOINED ) {
                        if( this.divisor[CHAN2] > 4 ) {
                            LOG_TIMER(("POKEY '%s' timer1+2 after %d clocks\n", this.device.tag(), this.divisor[CHAN2]));
                            // set timer #1 _and_ // #2 event after timer_div clocks of joined CHAN1+CHAN2
                            this.timer_period[TIMER2] = this.clock_period * this.divisor[CHAN2];
                            this.timer_param[TIMER2] = IRQ_TIMR2|IRQ_TIMR1;
                            this.timer[TIMER2].adjust(this.timer_period[TIMER2], this.timer_param[TIMER2], this.timer_period[TIMER2]);
                        }
                    } else {
                        if( this.divisor[CHAN1] > 4 ) {
                            LOG_TIMER(("POKEY '%s' timer1 after %d clocks\n", this.device.tag(), this.divisor[CHAN1]));
                            // set timer #1 event after timer_div clocks of CHAN1
                            this.timer_period[TIMER1] = this.clock_period * this.divisor[CHAN1];
                            this.timer_param[TIMER1] = IRQ_TIMR1;
                            this.timer[TIMER1].adjust(this.timer_period[TIMER1], this.timer_param[TIMER1], this.timer_period[TIMER1]);
                        }

                        if( this.divisor[CHAN2] > 4 ) {
                            LOG_TIMER(("POKEY '%s' timer2 after %d clocks\n", this.device.tag(), this.divisor[CHAN2]));
                            // set timer #2 event after timer_div clocks of CHAN2
                            this.timer_period[TIMER2] = this.clock_period * this.divisor[CHAN2];
                            this.timer_param[TIMER2] = IRQ_TIMR2;
                            this.timer[TIMER2].adjust(this.timer_period[TIMER2], this.timer_param[TIMER2], this.timer_period[TIMER2]);
                        }
                    }

                    // Note: p[chip] does not have a timer #3

                    if( this.AUDCTL & CH34_JOINED ) {
                        // not sure about this: if audc4 == 0000xxxx don't start timer 4 ?
                        if( this.AUDC[CHAN4] & 0xf0 ) {
                            if( this.divisor[CHAN4] > 4 ) {
                                LOG_TIMER(("POKEY '%s' timer4 after %d clocks\n", this.device.tag(), this.divisor[CHAN4]));
                                // set timer #4 event after timer_div clocks of CHAN4
                                this.timer_period[TIMER4] = this.clock_period * this.divisor[CHAN4];
                                this.timer_param[TIMER4] = IRQ_TIMR4;
                                this.timer[TIMER4].adjust(this.timer_period[TIMER4], this.timer_param[TIMER4], this.timer_period[TIMER4]);
                            }
                        }
                    } else {
                        if( this.divisor[CHAN4] > 4 ) {
                            LOG_TIMER(("POKEY '%s' timer4 after %d clocks\n", this.device.tag(), this.divisor[CHAN4]));
                            // set timer #4 event after timer_div clocks of CHAN4
                            this.timer_period[TIMER4] = this.clock_period * this.divisor[CHAN4];
                            this.timer_param[TIMER4] = IRQ_TIMR4;
                            this.timer[TIMER4].adjust(this.timer_period[TIMER4], this.timer_param[TIMER4], this.timer_period[TIMER4]);
                        }
                    }

                    this.timer[TIMER1].enable(this.IRQEN & IRQ_TIMR1);
                    this.timer[TIMER2].enable(this.IRQEN & IRQ_TIMR2);
                    this.timer[TIMER4].enable(this.IRQEN & IRQ_TIMR4);*/
                break;

            case SKREST_C:
                // reset SKSTAT
                //Debug.printf(("POKEY '%s' SKREST $%02x\n", this.device.tag(), data));
                this.skStat &= (byte) ~(SK_FRAME | SK_OVERRUN | SK_KBERR);
                break;

            case POTGO_C:
                //Debug.printf(("POKEY '%s' POTGO  $%02x\n", this.device.tag(), data));
                //pokey_potgo(p);
                break;

            case SEROUT_C:
                //Debug.printf(("POKEY '%s' SEROUT $%02x\n", this.device.tag(), data));
                //this.serout_w(offset, data);
                //this.SKSTAT |= SK_SEROUT;
                // These are arbitrary values, tested with some custom boot
                // loaders from Ballblazer and Escape from Fractalus
                // The real times are unknown
                //device.machine().scheduler().timer_set(attotime::from_usec(200), FUNC(pokey_serout_ready_cb), 0, p);
                // 10 bits (assumption 1 start, 8 data and 1 stop bit) take how long?
                //device.machine().scheduler().timer_set(attotime::from_usec(2000), FUNC(pokey_serout_complete), 0, p);
                break;

            case IRQEN_C:
                //Debug.printf(("POKEY '%s' IRQEN  $%02x\n", this.device.tag(), data));

                // acknowledge one or more IRQST bits ?
                if ((this.irqSt & ~data) != 0) {
                    // reset IRQST bits that are masked now
                    this.irqSt &= data;
//                } else {
                    // enable/disable timers now to avoid unneeded
                    // breaking of the CPU cores for masked timers
                    /*if( this.timer[TIMER1] && ((this.IRQEN^data) & IRQ_TIMR1) )
                        this.timer[TIMER1].enable(data & IRQ_TIMR1);
                    if( this.timer[TIMER2] && ((this.IRQEN^data) & IRQ_TIMR2) )
                        this.timer[TIMER2].enable(data & IRQ_TIMR2);
                    if( this.timer[TIMER4] && ((this.IRQEN^data) & IRQ_TIMR4) )
                        this.timer[TIMER4].enable(data & IRQ_TIMR4);*/
                }
                // store irq enable
                this.irqEn = data;
                break;

            case SKCTL_C:
                if (data == this.skCtl)
                    return;
                //Debug.printf(("POKEY '%s' SKCTL  $%02x\n", this.device.tag(), data));
                this.skCtl = data;
                if ((data & SK_RESET) == 0) {
                    write(IRQEN_C, (byte) 0);
                    write(SKREST_C, (byte) 0);
                }
                break;
            }

            // As defined in the manual, the exact counter values are
            // different depending on the frequency and resolution:
            //    64 kHz or 15 kHz - AUDF + 1
            //    1.79 MHz, 8-bit  - AUDF + 4
            //    1.79 MHz, 16-bit - AUDF[CHAN1]+256*AUDF[CHAN2] + 7

            // only reset the channels that have changed

            if ((chMask & (1 << CHAN1)) != 0) {
                // process channel 1 frequency
                if ((this.audioControl & CH1_HICLK) != 0)
                    newVal = this.audioF[CHAN1] + DIVADD_HICLK;
                else
                    newVal = (this.audioF[CHAN1] + DIVADD_LOCLK) * this.clockMult;

                //LOG_SOUND(("POKEY '%s' chan1 %d\n", this.device.tag(), newVal));

                this.volume[CHAN1] = (this.audioC[CHAN1] & VOLUME_MASK) * POKEY_DEFAULT_GAIN;
                this.divisor[CHAN1] = newVal;
                if (newVal < this.counter[CHAN1])
                    this.counter[CHAN1] = newVal;
                //if( this.interrupt_cb && this.timer[TIMER1] )
                // this.timer[TIMER1].adjust(this.clock_period * newVal, this.timer_param[TIMER1], this.timer_period[TIMER1]);
                this.audible[CHAN1] = (byte) (
                                (this.audioC[CHAN1] & VOLUME_ONLY) != 0 ||
                                        (this.audioC[CHAN1] & VOLUME_MASK) == 0 ||
                                        ((this.audioC[CHAN1] & PURE) != 0 && newVal < (this.samplerate24_8 >> 8))
                                ? 0 : 1
                );
                if (this.audible[CHAN1] == 0) {
                    this.output[CHAN1] = 1;
                    this.counter[CHAN1] = 0x7fffffff;
                    // 50% duty cycle should result in half volume
                    this.volume[CHAN1] >>= 1;
                }
            }

            if ((chMask & (1 << CHAN2)) != 0) {
                /* process channel 2 frequency */
                if ((this.audioControl & CH12_JOINED) != 0) {
                    if ((this.audioControl & CH1_HICLK) != 0)
                        newVal = this.audioF[CHAN2] * 256 + this.audioF[CHAN1] + DIVADD_HICLK_JOINED;
                    else
                        newVal = (this.audioF[CHAN2] * 256 + this.audioF[CHAN1] + DIVADD_LOCLK) * this.clockMult;
                    //LOG_SOUND(("POKEY '%s' chan1+2 %d\n", this.device.tag(), newVal));
                } else {
                    newVal = (this.audioF[CHAN2] + DIVADD_LOCLK) * this.clockMult;
                    //LOG_SOUND(("POKEY '%s' chan2 %d\n", this.device.tag(), newVal));
                }

                this.volume[CHAN2] = (this.audioC[CHAN2] & VOLUME_MASK) * POKEY_DEFAULT_GAIN;
                this.divisor[CHAN2] = newVal;
                if (newVal < this.counter[CHAN2])
                    this.counter[CHAN2] = newVal;
                //if( this.interrupt_cb && this.timer[TIMER2] )
                // this.timer[TIMER2].adjust(this.clock_period * newVal, this.timer_param[TIMER2], this.timer_period[TIMER2]);
                this.audible[CHAN2] = (byte) (
                                (this.audioC[CHAN2] & VOLUME_ONLY) != 0 ||
                                        (this.audioC[CHAN2] & VOLUME_MASK) == 0 ||
                                        ((this.audioC[CHAN2] & PURE) != 0 && newVal < (this.samplerate24_8 >> 8))
                                ? 0 : 1
                );
                if (this.audible[CHAN2] == 0) {
                    this.output[CHAN2] = 1;
                    this.counter[CHAN2] = 0x7fffffff;
                    /* 50% duty cycle should result in half volume */
                    this.volume[CHAN2] >>= 1;
                }
            }

            if ((chMask & (1 << CHAN3)) != 0) {
                /* process channel 3 frequency */
                if ((this.audioControl & CH3_HICLK) != 0)
                    newVal = this.audioF[CHAN3] + DIVADD_HICLK;
                else
                    newVal = (this.audioF[CHAN3] + DIVADD_LOCLK) * this.clockMult;

                //LOG_SOUND(("POKEY '%s' chan3 %d\n", this.device.tag(), newVal));

                this.volume[CHAN3] = (this.audioC[CHAN3] & VOLUME_MASK) * POKEY_DEFAULT_GAIN;
                this.divisor[CHAN3] = newVal;
                if (newVal < this.counter[CHAN3])
                    this.counter[CHAN3] = newVal;
                /* channel 3 does not have a timer associated */
                this.audible[CHAN3] = (byte) (
                                !((this.audioC[CHAN3] & VOLUME_ONLY) != 0 ||
                                  (this.audioC[CHAN3] & VOLUME_MASK) == 0 ||
                                  ((this.audioC[CHAN3] & PURE) != 0 && newVal < (this.samplerate24_8 >> 8))
                                ) ||
                                  (this.audioControl & CH1_FILTER) != 0
                            ? 1 : 0
                );
                if (this.audible[CHAN3] == 0) {
                    this.output[CHAN3] = 1;
                    this.counter[CHAN3] = 0x7fffffff;
                    /* 50% duty cycle should result in half volume */
                    this.volume[CHAN3] >>= 1;
                }
            }

            if ((chMask & (1 << CHAN4)) != 0) {
                /* process channel 4 frequency */
                if ((this.audioControl & CH34_JOINED) != 0) {
                    if ((this.audioControl & CH3_HICLK) != 0)
                        newVal = this.audioF[CHAN4] * 256 + this.audioF[CHAN3] + DIVADD_HICLK_JOINED;
                    else
                        newVal = (this.audioF[CHAN4] * 256 + this.audioF[CHAN3] + DIVADD_LOCLK) * this.clockMult;
                    //LOG_SOUND(("POKEY '%s' chan3+4 %d\n", this.device.tag(), newVal));
                } else {
                    newVal = (this.audioF[CHAN4] + DIVADD_LOCLK) * this.clockMult;
                    //LOG_SOUND(("POKEY '%s' chan4 %d\n", this.device.tag(), newVal));
                }

                this.volume[CHAN4] = (this.audioC[CHAN4] & VOLUME_MASK) * POKEY_DEFAULT_GAIN;
                this.divisor[CHAN4] = newVal;
                if (newVal < this.counter[CHAN4])
                    this.counter[CHAN4] = newVal;
                //if( this.interrupt_cb && this.timer[TIMER4] )
                // this.timer[TIMER4].adjust(this.clock_period * newVal, this.timer_param[TIMER4], this.timer_period[TIMER4]);
                this.audible[CHAN4] = (byte) (
                                !((this.audioC[CHAN4] & VOLUME_ONLY) != 0 ||
                                  (this.audioC[CHAN4] & VOLUME_MASK) == 0 ||
                                  (this.audioC[CHAN4] & PURE) != 0 && newVal < (this.samplerate24_8 >> 8)
                                ) ||
                                  (this.audioControl & CH2_FILTER) != 0
                        ? 1 : 0
                );

                if (this.audible[CHAN4] == 0) {
                    this.output[CHAN4] = 1;
                    this.counter[CHAN4] = 0x7fffffff;
                    // 50% duty cycle should result in half volume
                    this.volume[CHAN4] >>= 1;
                }
            }
        }

        private void set_mute_mask(int muteMask) {
            for (int c = 0; c < 4; c++)
                this.muted[c] = (byte) ((muteMask >> c) & 0x01);
        }
    }

    private static final int MAX_CHIPS = 0x02;
    private PokeyState[] chips = new PokeyState[MAX_CHIPS];

    private void pokey_update(byte chipId, int[][] outputs, int samples) {
        PokeyState chip = chips[chipId];
        chip.update(outputs, samples);
    }

    private int device_start_pokey(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        if (chips[chipId] == null) {
            chips[chipId] = new PokeyState();
        }

        PokeyState chip = chips[chipId];
        return chip.start(clock);
    }

    private void device_stop_pokey(byte chipId) {
        PokeyState chip = chips[chipId];
    }

    private void device_reset_pokey(byte chipId) {
        PokeyState chip = chips[chipId];
        chip.reset();
    }

    private byte pokey_r(byte chipId, int offset) {
        PokeyState chip = chips[chipId];
        return chip.read(offset);
    }

    private void pokey_w(byte chipId, int offset, byte data) {
        PokeyState chip = chips[chipId];
        chip.write(offset, data);
    }

    private void pokey_set_mute_mask(byte chipId, int muteMask) {
        PokeyState chip = chips[chipId];
        chip.set_mute_mask(muteMask);
    }

    /**
     * Generic get_info
     */
    /*DEVICE_GET_INFO( Pokey ) {
     switch (state) {
      // --- the following bits of info are returned as 64-bit signed integers --- //
      case DEVINFO_INT_TOKEN_BYTES:     info.i = sizeof(PokeyState);  break;

      // --- the following bits of info are returned as pointers to data or functions --- //
      case DEVINFO_FCT_START:       info.start = DEVICE_START_NAME( Pokey );   break;
      case DEVINFO_FCT_STOP: // Nothing //         break;
      case DEVINFO_FCT_RESET: // Nothing //         break;

      // --- the following bits of info are returned as NULL-terminated strings --- //
      case DEVINFO_STR_NAME:       strcpy(info.s, "POKEY");      break;
      case DEVINFO_STR_FAMILY:     strcpy(info.s, "Atari custom");    break;
      case DEVINFO_STR_VERSION:     strcpy(info.s, "4.51");      break;
      case DEVINFO_STR_SOURCE_FILE:      strcpy(info.s, __FILE__);      break;
      case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
     }
    }
    DEFINE_LEGACY_SOUND_DEVICE(POKEY, Pokey);*/
}
