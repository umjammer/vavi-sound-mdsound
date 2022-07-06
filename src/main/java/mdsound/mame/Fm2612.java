/*
 *
 * software implementation of Yamaha Ym2612 FM Sound generator
 * Split from Fm.c to keep 2612 fixes from infecting other OPN chips
 *
 * Copyright Jarek Burczynski (bujar at mame dot net)
 * Copyright Tatsuyuki Satoh , MultiArcadeMachineEmulator development
 *
 * Version 1.5.1 (Genesis Plus GX Ym2612.c rev. 368)
 */

package mdsound.mame;

import java.util.Arrays;
import java.util.function.BiConsumer;

import dotnet4j.util.compat.QuadConsumer;
import mdsound.mame.Fm.BaseChip;


/**
 * History:
 * <p>
 * 2006~2012  Eke-Eke (Genesis Plus GX):
 * Huge thanks to Nemesis, lot of those fixes came from his tests on Sega Genesis hardware
 * More informations at http://gendev.spritesmind.net/forum/viewtopic.php?t=386
 * <p>
 *  TODO:
 * <p>
 * - core documentation
 * - BUSY flag support
 * <p>
 * CHANGELOG:
 * <p>
 * - fixed LFO implementation:
 * .added support for CH3 special mode: fixes various Sound effects (birds in Warlock, bug Sound in Aladdin...)
 * .inverted LFO AM waveform: fixes Spider-Man & Venom : Separation Anxiety (intro), California Games (surfing event)
 * .improved LFO timing accuracy: now updated AFTER sample output, like EG/PG updates, and without any precision loss anymore.
 * - improved internal timers emulation
 * - adjusted lowest EG rates increment values
 * - fixed Attack Rate not being updated in some specific cases (Batman & Robin intro)
 * - fixed EG behavior when Attack Rate is maximal
 * - fixed EG behavior when SL=0 (Mega Turrican tracks 03,09...) or/and Key ON occurs at minimal attenuation
 * - implemented EG output immediate changes on register writes
 * - fixed Ym2612 initial values (after the reset): fixes missing intro in B.O.B
 * - implemented Detune overflow (Ariel, Comix Zone, Shaq Fu, Spiderman & many other games using GEMS Sound engine)
 * - implemented accurate CSM mode emulation
 * - implemented accurate SSG-EG emulation (Asterix, Beavis&Butthead, Bubba'n Stix & many other games)
 * - implemented accurate address/data ports behavior
 * <p>
 * 06-23-2007 Zsolt Vasvari:
 * - changed the timing not to require the use of floating point calculations
 * <p>
 * 03-08-2003 Jarek Burczynski:
 * - fixed YM2608 initial values (after the reset)
 * - fixed flag and irqmask handling (YM2608)
 * - fixed BUFRDY flag handling (YM2608)
 * <p>
 * 14-06-2003 Jarek Burczynski:
 * - implemented all of the YM2608 status register flags
 * - implemented support for external memory read/write via YM2608
 * - implemented support for DeltaT memory limit register in YM2608 emulation
 * <p>
 * 22-05-2003 Jarek Burczynski:
 * - fixed LFO PM calculations (copy&paste bugfix)
 * <p>
 * 08-05-2003 Jarek Burczynski:
 * - fixed SSG support
 * <p>
 * 22-04-2003 Jarek Burczynski:
 * - implemented 100% correct LFO generator (verified on real YM2610 and YM2608)
 * <p>
 * 15-04-2003 Jarek Burczynski:
 * - added support for YM2608's register 0x110 - status mask
 * <p>
 * 01-12-2002 Jarek Burczynski:
 * - fixed register addressing in YM2608, YM2610, YM2610B chips. (verified on real YM2608)
 * The addressing patch used for early Neo-Geo games can be removed now.
 * <p>
 * 26-11-2002 Jarek Burczynski, Nicola Salmoria:
 * - recreated YM2608 ADPCM ROM using data from real YM2608's output which leads to:
 * - added emulation of YM2608 drums.
 * - output of YM2608 is two times lower now - same as YM2610 (verified on real YM2608)
 * <p>
 * 16-08-2002 Jarek Burczynski:
 * - binary exact Envelope Generator (verified on real YM2203);
 * identical to YM2151
 * - corrected 'off by one' error in feedback calculations (when feedback is off)
 * - corrected connection (algorithm) calculation (verified on real YM2203 and YM2610)
 * <p>
 * 18-12-2001 Jarek Burczynski:
 * - added SSG-EG support (verified on real YM2203)
 * <p>
 * 12-08-2001 Jarek Burczynski:
 * - corrected sin_tab and tl_tab data (verified on real chip)
 * - corrected feedback calculations (verified on real chip)
 * - corrected phase generator calculations (verified on real chip)
 * - corrected envelope generator calculations (verified on real chip)
 * - corrected FM volume level (YM2610 and YM2610B).
 * - changed YMxxxUpdateOne() functions (YM2203, YM2608, YM2610, YM2610B, Ym2612) :
 * this was needed to calculate YM2610 FM channels output correctly.
 * (Each FM channel is calculated as in other chips, but the output of the channel
 * gets shifted right by one *before* sending to accumulator. That was impossible to do
 * with previous implementation).
 * <p>
 * 23-07-2001 Jarek Burczynski, Nicola Salmoria:
 * - corrected YM2610 ADPCM type A algorithm and tables (verified on real chip)
 * <p>
 * 11-06-2001 Jarek Burczynski:
 * - corrected end of sample bug in ADPCMA_calc_cha().
 * Real YM2610 checks for equality between current and end addresses (only 20 LSB bits).
 * <p>
 * 08-12-98 hiro-shi:
 * rename AdpcmA . AdpcmB, AdpcmB . AdpcmA
 * move ROM limit check.(CALC_CH? . 2610Write1/2)
 * test program (ADPCMB_TEST)
 * move ADPCM A/B end check.
 * AdpcmB repeat flag(no check)
 * change ADPCM volume rate (8.16) (32.48).
 * <p>
 * 09-12-98 hiro-shi:
 * change ADPCM volume. (8.16, 48.64)
 * replace Ym2610 ch0/3 (YM-2610B)
 * change ADPCM_SHIFT (10.8) missing bank change 0x4000-0xffff.
 * add ADPCM_SHIFT_MASK
 * change ADPCMA_DECODE_MIN/MAX.
 * <p>
 * comment of hiro-shi(Hiromitsu Shioya)
 *
 *  YM2610(B) = OPN-B
 *  YM2610  : Psg:3ch FM:4ch ADPCM(18.5KHz):6ch DeltaT ADPCM:1ch
 *  YM2610B : Psg:3ch FM:6ch ADPCM(18.5KHz):6ch DeltaT ADPCM:1ch
 */
public class Fm2612 {

    /**
     * here's the virtual Ym2612
     */
    public static class Ym2612 extends BaseChip {

        // shared function building option

        private static final int BUILD_OPN = Fm.BUILD_YM2203 | Fm.BUILD_YM2608 | Fm.BUILD_YM2610 | Fm.BUILD_YM2610B | Fm.BUILD_YM2612 | Fm.BUILD_YM3438;
        private static final int BUILD_OPN_PRESCALER = Fm.BUILD_YM2203 | Fm.BUILD_YM2608;

        // globals

        /**
         * SSG support
         */
        private static final int TYPE_SSG = 0x01;
        /**
         * OPN type LFO and PAN
         */
        private static final int TYPE_LFOPAN = 0x02;
        /**
         * FM 6CH / 3CH
         */
        private static final int TYPE_6CH = 0x04;
        /**
         * Ym2612's DAC device
         */
        private static final int TYPE_DAC = 0x08;
        /**
         * two ADPCM units
         */
        private static final int TYPE_ADPCM = 0x10;
        /**
         * bogus flag to differentiate 2608 from 2610
         */
        private static final int TYPE_2610 = 0x20;

        private static final int TYPE_YM2203 = TYPE_SSG;
        private static final int TYPE_YM2608 = TYPE_SSG | TYPE_LFOPAN | TYPE_6CH | TYPE_ADPCM;
        private static final int TYPE_YM2610 = TYPE_SSG | TYPE_LFOPAN | TYPE_6CH | TYPE_ADPCM | TYPE_2610;
        private static final int TYPE_YM2612 = TYPE_DAC | TYPE_LFOPAN | TYPE_6CH;

        // globals

        /** 16.16 fixed point (frequency calculations) */
        private static final int FREQ_SH = 16;
        /** 16.16 fixed point (envelope generator timing) */
        private static final int EG_SH = 16;
        /**  8.24 fixed point (LFO calculations) */
        private static final int LFO_SH = 24;
        /** 16.16 fixed point (timers calculations) */
        private static final int TIMER_SH = 16;

        private static final int FREQ_MASK = (1 << FREQ_SH) - 1;

        private static final int MAXOUT = 32767;
        private static final int MINOUT = -32768;

        // envelope generator

        private static final int ENV_BITS = 10;
        private static final int ENV_LEN = 1 << ENV_BITS;
        private static final double ENV_STEP = 128.0 / ENV_LEN;

        private static final int MAX_ATT_INDEX = ENV_LEN - 1;
        private static final int MIN_ATT_INDEX = 0;

        private static final int EG_ATT = 4;
        private static final int EG_DEC = 3;
        private static final int EG_SUS = 2;
        private static final int EG_REL = 1;
        private static final int EG_OFF = 0;

        // Operator unit

        private static final int SIN_BITS = 10;
        private static final int SIN_LEN = 1 << SIN_BITS;
        private static final int SIN_MASK = SIN_LEN - 1;

        /** 8 bits addressing (real chip) */
        private static final int TL_RES_LEN = 256;

        /**
         * TL_TAB_LEN is calculated as:
         * 13 - sinus amplitude bits     (Y axis)
         * 2  - sinus sign bit           (Y axis)
         * TL_RES_LEN - sinus resolution (X axis)
         */
        private static final int TL_TAB_LEN = 13 * 2 * TL_RES_LEN;
        private static int[] tlTab = new int[TL_TAB_LEN];

        private static final int ENV_QUIET = TL_TAB_LEN >> 3;

        /**
         * sin waveform table in 'decibel' scale
         */
        private static int[] sinTab = new int[SIN_LEN];

        private static final int RATE_STEPS = 8;
        private static final byte[] egInc = new byte[] {
                /*cycle:0 1  2 3  4 5  6 7*/

                /* 0 */ 0, 1, 0, 1, 0, 1, 0, 1, // rates 00..11 0 (increment by 0 or 1)
                /* 1 */ 0, 1, 0, 1, 1, 1, 0, 1, // rates 00..11 1
                /* 2 */ 0, 1, 1, 1, 0, 1, 1, 1, // rates 00..11 2
                /* 3 */ 0, 1, 1, 1, 1, 1, 1, 1, // rates 00..11 3

                /* 4 */ 1, 1, 1, 1, 1, 1, 1, 1, // rate 12 0 (increment by 1)
                /* 5 */ 1, 1, 1, 2, 1, 1, 1, 2, // rate 12 1
                /* 6 */ 1, 2, 1, 2, 1, 2, 1, 2, // rate 12 2
                /* 7 */ 1, 2, 2, 2, 1, 2, 2, 2, // rate 12 3

                /* 8 */ 2, 2, 2, 2, 2, 2, 2, 2, // rate 13 0 (increment by 2)
                /* 9 */ 2, 2, 2, 4, 2, 2, 2, 4, // rate 13 1
                /*10 */ 2, 4, 2, 4, 2, 4, 2, 4, // rate 13 2
                /*11 */ 2, 4, 4, 4, 2, 4, 4, 4, // rate 13 3

                /*12 */ 4, 4, 4, 4, 4, 4, 4, 4, // rate 14 0 (increment by 4)
                /*13 */ 4, 4, 4, 8, 4, 4, 4, 8, // rate 14 1
                /*14 */ 4, 8, 4, 8, 4, 8, 4, 8, // rate 14 2
                /*15 */ 4, 8, 8, 8, 4, 8, 8, 8, // rate 14 3

                /*16 */ 8, 8, 8, 8, 8, 8, 8, 8, // rates 15 0, 15 1, 15 2, 15 3 (increment by 8)
                /*17 */ 16, 16, 16, 16, 16, 16, 16, 16, // rates 15 2, 15 3 for attack
                /*18 */ 0, 0, 0, 0, 0, 0, 0, 0, // infinity rates for attack and decay(s)
        };

        /**
         * this is YM2151 and Ym2612 phase increment data (in 10.10 fixed point format)
         */
        private static final byte[] dtTab = new byte[] {
                // FD=0
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                // FD=1
                0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2,
                2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6, 7, 8, 8, 8, 8,
                // FD=2
                1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5,
                5, 6, 6, 7, 8, 8, 9, 10, 11, 12, 13, 14, 16, 16, 16, 16,
                // FD=3
                2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6, 7,
                8, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 20, 22, 22, 22, 22
        };

        /**
         * OPN key frequency number . key code follow table
         * fnum higher 4bit . keycode lower 2bit
         */
        private static final byte[] opnFkTable = new byte[] {0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 3, 3, 3, 3, 3, 3};

        /**
         * 8 LFO speed parameters
         * each value represents number of samples that one LFO level will last for
         */
        private static final int[] lfoSamplesPerStep = new int[] {108, 77, 71, 67, 62, 44, 8, 5};

        /**
         * There are 4 different LFO AM depths available, they are:
         * 0 dB, 1.4 dB, 5.9 dB, 11.8 dB
         * Here is how it is generated (in EG steps):
         * <p>
         * 11.8 dB = 0, 2, 4, 6, 8, 10,12,14,16...126,126,124,122,120,118,....4,2,0
         *  5.9 dB = 0, 1, 2, 3, 4, 5, 6, 7, 8....63, 63, 62, 61, 60, 59,.....2,1,0
         *  1.4 dB = 0, 0, 0, 0, 1, 1, 1, 1, 2,...15, 15, 15, 15, 14, 14,.....0,0,0
         * <p>
         * (1.4 dB is losing precision as you can see)
         * <p>
         * It's implemented as generator from 0..126 with step 2 then a shift
         * right N times, where N is:
         *  8 for 0 dB
         *  3 for 1.4 dB
         *  1 for 5.9 dB
         *  0 for 11.8 dB
         */
        private static final byte[] lfoAmsDepthShift = new byte[] {8, 3, 1, 0};

        /**
         * There are 8 different LFO PM depths available, they are:
         * 0, 3.4, 6.7, 10, 14, 20, 40, 80 (cents)
         * <p>
         * Modulation level at each depth depends on F-NUMBER bits: 4,5,6,7,8,9,10
         * (bits 8,9,10 = FNUM MSB from OCT/FNUM register)
         * <p>
         * Here we store only first quarter (positive one) of full waveform.
         * Full table (lfo_pm_table) containing all 128 waveforms is build
         * at run (init) time.
         * <p>
         * One value in table below represents 4 (four) basic LFO steps
         * (1 PM step = 4 AM steps).
         * <p>
         * For example:
         * at LFO SPEED=0 (which is 108 samples per basic LFO step)
         * one value from "lfo_pm_output" table lasts for 432 consecutive
         * samples (4*108=432) and one full LFO waveform cycle lasts for 13824
         * samples (32*432=13824; 32 because we store only a quarter of whole
         * waveform in the table below)
         *
         * 7 bits meaningful (of F-NUMBER), 8 LFO output levels per one depth (of 32), 8 LFO depths
         */
        private static final byte[][] lfo_pm_output = new byte[][] {
                // FNUM BIT 4: 000 0001xxxx
                /* DEPTH 0 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 1 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 2 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 3 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 4 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 5 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 6 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 7 */ new byte[] {0, 0, 0, 0, 1, 1, 1, 1},

                // FNUM BIT 5: 000 0010xxxx
                /* DEPTH 0 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 1 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 2 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 3 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 4 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 5 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 6 */ new byte[] {0, 0, 0, 0, 1, 1, 1, 1},
                /* DEPTH 7 */ new byte[] {0, 0, 1, 1, 2, 2, 2, 3},

                // FNUM BIT 6: 000 0100xxxx
                /* DEPTH 0 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 1 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 2 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 3 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 4 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 1},
                /* DEPTH 5 */ new byte[] {0, 0, 0, 0, 1, 1, 1, 1},
                /* DEPTH 6 */ new byte[] {0, 0, 1, 1, 2, 2, 2, 3},
                /* DEPTH 7 */ new byte[] {0, 0, 2, 3, 4, 4, 5, 6},

                // FNUM BIT 7: 000 1000xxxx
                /* DEPTH 0 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 1 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 2 */ new byte[] {0, 0, 0, 0, 0, 0, 1, 1},
                /* DEPTH 3 */ new byte[] {0, 0, 0, 0, 1, 1, 1, 1},
                /* DEPTH 4 */ new byte[] {0, 0, 0, 1, 1, 1, 1, 2},
                /* DEPTH 5 */ new byte[] {0, 0, 1, 1, 2, 2, 2, 3},
                /* DEPTH 6 */ new byte[] {0, 0, 2, 3, 4, 4, 5, 6},
                /* DEPTH 7 */ new byte[] {0, 0, 4, 6, 8, 8, 0xa, 0xc},

                // FNUM BIT 8: 001 0000xxxx
                /* DEPTH 0 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 1 */ new byte[] {0, 0, 0, 0, 1, 1, 1, 1},
                /* DEPTH 2 */ new byte[] {0, 0, 0, 1, 1, 1, 2, 2},
                /* DEPTH 3 */ new byte[] {0, 0, 1, 1, 2, 2, 3, 3},
                /* DEPTH 4 */ new byte[] {0, 0, 1, 2, 2, 2, 3, 4},
                /* DEPTH 5 */ new byte[] {0, 0, 2, 3, 4, 4, 5, 6},
                /* DEPTH 6 */ new byte[] {0, 0, 4, 6, 8, 8, 0xa, 0xc},
                /* DEPTH 7 */ new byte[] {0, 0, 8, 0xc, 0x10, 0x10, 0x14, 0x18},

                // FNUM BIT 9: 010 0000xxxx
                /* DEPTH 0 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 1 */ new byte[] {0, 0, 0, 0, 2, 2, 2, 2},
                /* DEPTH 2 */ new byte[] {0, 0, 0, 2, 2, 2, 4, 4},
                /* DEPTH 3 */ new byte[] {0, 0, 2, 2, 4, 4, 6, 6},
                /* DEPTH 4 */ new byte[] {0, 0, 2, 4, 4, 4, 6, 8},
                /* DEPTH 5 */ new byte[] {0, 0, 4, 6, 8, 8, 0xa, 0xc},
                /* DEPTH 6 */ new byte[] {0, 0, 8, 0xc, 0x10, 0x10, 0x14, 0x18},
                /* DEPTH 7 */ new byte[] {0, 0, 0x10, 0x18, 0x20, 0x20, 0x28, 0x30},

                // FNUM BIT10: 100 0000xxxx
                /* DEPTH 0 */ new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                /* DEPTH 1 */ new byte[] {0, 0, 0, 0, 4, 4, 4, 4},
                /* DEPTH 2 */ new byte[] {0, 0, 0, 4, 4, 4, 8, 8},
                /* DEPTH 3 */ new byte[] {0, 0, 4, 4, 8, 8, 0xc, 0xc},
                /* DEPTH 4 */ new byte[] {0, 0, 4, 8, 8, 8, 0xc, 0x10},
                /* DEPTH 5 */ new byte[] {0, 0, 8, 0xc, 0x10, 0x10, 0x14, 0x18},
                /* DEPTH 6 */ new byte[] {0, 0, 0x10, 0x18, 0x20, 0x20, 0x28, 0x30},
                /* DEPTH 7 */ new byte[] {0, 0, 0x20, 0x30, 0x40, 0x40, 0x50, 0x60},
        };

        /**
         * all 128 LFO PM waveforms
         *
         * 128 combinations of 7 bits meaningful (of F-NUMBER), 8 LFO depths, 32 LFO output levels per one depth
         */
        private static int[] lfoPmTable = new int[128 * 8 * 32];

        // slot number

        private static final int SLOT1 = 0;
        private static final int SLOT2 = 2;
        private static final int SLOT3 = 1;
        private static final int SLOT4 = 3;

        // bit0 = Right enable , bit1 = Left enable

        private static final int OUTD_RIGHT = 1;
        private static final int OUTD_LEFT = 2;
        private static final int OUTD_CENTER = 3;

        public Fm.UpdateRequestCallback updateRequest;

        public int timerOver(int c) {

            if (c != 0) { // Timer B
                this.opn.st.timerBOver();
            } else { // Timer A
                updateReq();
                // timer update
                this.opn.st.timerAOver();
                // CSM mode key,TL controll
                if ((this.opn.st.mode & 0xc0) == 0x80) { // CSM mode total level latch and auto key on
                    this.ch[2].controllKeyCsm(this.opn.sl3.keyCsm == 0);
                    this.opn.sl3.keyCsm = 1;
                }
            }
            return this.opn.st.irq;
        }

        public void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < 6; curChn++)
                this.ch[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
            this.muteDAC = (byte) ((muteMask >> 6) & 0x01);
        }

        public void updateReq() {
            updateRequest.run();
        }

        /**
         * OPN/A/B common state
         */
        static class Opn {

            public void reset() {
                this.setPreS(6 * 24, 6 * 24, 0);
                // status clear
                this.st.setIrqMask(0x03);
                this.st.clearBusy();
                //OPNWriteMode(opn,0x27,0x30); // mode 0 , timer reset

                this.egTimer = 0;
                this.egCnt = 0;

                this.lfoTimer = 0;
                this.lfoCnt = 0;
                this.lfoAm = 126;
                this.lfoPm = 0;

                this.st.tac = 0;
                this.st.tbc = 0;

                this.sl3.keyCsm = 0;

                this.st.status = 0;
                this.st.mode = 0;

                this.writeMode(0x22, 0x00);

                this.writeMode(0x27, 0x30);
                this.writeMode(0x26, 0x00);
                this.writeMode(0x25, 0x00);
                this.writeMode(0x24, 0x00);

                for (int i = 0xb6; i >= 0xb4; i--) {
                    this.writeReg(i, 0xc0);
                    this.writeReg(i | 0x100, 0xc0);
                }
                for (int i = 0xb2; i >= 0x30; i--) {
                    this.writeReg(i, 0);
                    this.writeReg(i | 0x100, 0);
                }
            }

            public static class Channel {

                static class Op {
                    public int val = 0;

                    private int calc(int phase, int env) {
                        int p = (env << 3) + sinTab[(((phase & ~FREQ_MASK) + (this.val << 15)) >> FREQ_SH) & SIN_MASK];

                        if (p >= TL_TAB_LEN)
                            return 0;
                        return tlTab[p];
                    }

                    private static int calc1(int phase, int env, int pm) {
                        int p = (env << 3) + sinTab[(((phase & ~FREQ_MASK) + pm) >> FREQ_SH) & SIN_MASK];

                        if (p >= TL_TAB_LEN)
                            return 0;
                        return tlTab[p];
                    }
                }

                /**
                 * struct describing a single Operator (SLOT)
                 */
                public static class Slot {

                    private byte isVGMInit;

                    /**
                     * detune: dt_tab[DT]
                     */
                    public int[] dt;
                    /**
                     * key scale rate: 3-KSR
                     */
                    public byte KSR;
                    /**
                     * attack rate
                     */
                    public int ar;
                    /**
                     * decay rate
                     */
                    public int d1r;
                    /**
                     * sustain rate
                     */
                    public int d2r;
                    /**
                     * release rate
                     */
                    public int rr;
                    /**
                     * key scale rate: kcode>>(3-KSR)
                     */
                    public byte ksr;
                    /**
                     * multiple: ML_TABLE[ML]
                     */
                    public int mul;

                    // Phase Generator

                    /**
                     * phase counter
                     */
                    public int phase;
                    /**
                     * phase step
                     */
                    public int incr;

                    // Envelope Generator

                    /**
                     * phase type
                     */
                    public byte state;
                    /**
                     * total level: TL << 3
                     */
                    public int tl;
                    /**
                     * envelope counter
                     */
                    public int volume;
                    /**
                     * sustain level:sl_table[SL]
                     */
                    public int sl;
                    /**
                     * current output from EG circuit (without AM from LFO)
                     */
                    public int volOut;

                    /**
                     * attack state
                     */
                    public byte egShAr;
                    /**
                     * attack state
                     */
                    public byte egSelAr;
                    /**
                     * decay state
                     */
                    public byte egShD1R;
                    /**
                     * decay state
                     */
                    public byte egSelD1R;
                    /**
                     * sustain state
                     */
                    public byte egShD2R;
                    /**
                     * sustain state
                     */
                    public byte egSelD2R;
                    /**
                     * release state
                     */
                    public byte egShRr;
                    /**
                     * release state
                     */
                    public byte egSelRr;

                    /**
                     * SSG-EG waveform
                     */
                    public byte ssg;
                    /**
                     * SSG-EG negated output
                     */
                    public byte ssgn;

                    /**
                     * 0=last key was KEY OFF, 1=KEY ON
                     */
                    public byte key;

                    // LFO

                    /**
                     * AM enable flag
                     */
                    public int aMmask;

                    private int calcVolume(int am) {
                        return (this.volOut + (am & this.aMmask));
                    }

                    /**
                     * set detune & multiple
                     */
                    private void setDetMul(int[] dt, int v) {
                        this.mul = (v & 0x0f) != 0 ? ((v & 0x0f) * 2) : 1;
                        this.dt = dt;
                    }

                    /**
                     * set total level
                     */
                    private void setTl(int v) {
                        this.tl = (v & 0x7f) << (ENV_BITS - 7); // 7bit TL

                        // recalculate EG output
                        if ((this.ssg & 0x08) != 0 && (this.ssgn ^ (this.ssg & 0x04)) != 0 && (this.state > EG_REL))
                            this.volOut = ((0x200 - this.volume) & MAX_ATT_INDEX) + this.tl;
                        else
                            this.volOut = this.volume + this.tl;
                    }

                    /**
                     * set attack rate & key scale
                     */
                    private void setArKsr(byte type, int v) {
                        this.ar = (v & 0x1f) != 0 ? (32 + ((v & 0x1f) << 1)) : 0;

                        this.KSR = (byte) (3 - (v >> 6));

                        // Even if it seems unnecessary, in some odd case, KSR and KC are both modified
                        // and could result in this.kc remaining unchanged.
                        // In such case, AR values would not be recalculated despite this.ar has changed
                        // This fixes the introduction music of Batman & Robin    (Eke-Eke)
                        if (this.ar + this.ksr < 32 + 62) {
                            this.egShAr = Opn.egRateShift[this.ar + this.ksr];
                            this.egSelAr = Opn.egRateSelect2612[this.ar + this.ksr];
                        } else {
                            this.egShAr = 0;
                            this.egSelAr = (byte) (18 * RATE_STEPS); // verified by Nemesis on real hardware
                        }
                    }

                    /**
                     * set decay rate
                     */
                    private void setDr(byte type, int v) {
                        this.d1r = (v & 0x1f) != 0 ? (32 + ((v & 0x1f) << 1)) : 0;

                        this.egShD1R = Opn.egRateShift[this.d1r + this.ksr];
                        this.egSelD1R = Opn.egRateSelect2612[this.d1r + this.ksr];
                    }

                    /**
                     * set sustain rate
                     */
                    private void setSr(byte type, int v) {
                        this.d2r = (v & 0x1f) != 0 ? (32 + ((v & 0x1f) << 1)) : 0;

                        this.egShD2R = Opn.egRateShift[this.d2r + this.ksr];
                        this.egSelD2R = Opn.egRateSelect2612[this.d2r + this.ksr];
                    }

                    /**
                     * set release rate
                     */
                    private void setSlRr(byte type, int v) {
                        this.sl = slTable[v >> 4];

                        // check EG state changes
                        if ((this.state == EG_DEC) && (this.volume >= this.sl))
                            this.state = EG_SUS;

                        this.rr = 34 + ((v & 0x0f) << 2);

                        this.egShRr = Opn.egRateShift[this.rr + this.ksr];
                        this.egSelRr = Opn.egRateSelect2612[this.rr + this.ksr];
                    }

                    // sustain level table (3dB per step)
                    // bit0, bit1, bit2, bit3, bit4, bit5, bit6
                    // 1,    2,    4,    8,    16,   32,   64   (value)
                    // 0.75, 1.5,  3,    6,    12,   24,   48   (dB)

                    // 0 - 15: 0, 3, 6, 9,12,15,18,21,24,27,30,33,36,39,42,93 (dB)
                    // attenuation value (10 bits) = (SL << 2) << 3
                    private static int sc(int db) {
                        return (int) (db * (4.0 / ENV_STEP));
                    }

                    private static final int[] slTable = new int[16];

                    static {
                        for (int i = 0; i < 16; i++) slTable[i] = sc(i == 15 ? 31 : i);
                    }

                    public void keyOffCsm() {
                        if (this.key == 0) {
                            if (isVGMInit != 0) {
                                this.state = EG_OFF;
                                this.volume = MAX_ATT_INDEX;
                                this.volOut = MAX_ATT_INDEX;
                            } else if (this.state > EG_REL) {
                                this.state = EG_REL; // phase . Release

                                // SSG-EG specific update
                                if ((this.ssg & 0x08) != 0) {
                                    // convert EG attenuation level
                                    if ((this.ssgn ^ (this.ssg & 0x04)) != 0)
                                        this.volume = (0x200 - this.volume);

                                    // force EG attenuation level
                                    if (this.volume >= 0x200) {
                                        this.volume = MAX_ATT_INDEX;
                                        this.state = EG_OFF;
                                    }

                                    // recalculate EG output
                                    this.volOut = this.volume + this.tl;
                                }
                            }
                        }
                    }

                    public void keyOn(boolean enabled) {
                        // Note by Valley Bell:
                        //  I assume that the CSM mode shouldn't affect channels
                        //  other than FM3, so I added a check for it here.
                        if (this.key == 0 && enabled) {
                            // restart Phase Generator
                            this.phase = 0;

                            // reset SSG-EG inversion flag
                            this.ssgn = 0;

                            if ((this.ar + this.ksr) < 32 + 62) {
                                this.state = (byte) ((this.volume <= MIN_ATT_INDEX) ? ((this.sl == MIN_ATT_INDEX) ? EG_SUS : EG_DEC) : EG_ATT);
                            } else {
                                // force attenuation level to 0
                                this.volume = MIN_ATT_INDEX;

                                // directly switch to Decay (or Sustain)
                                this.state = (this.sl == MIN_ATT_INDEX) ? (byte) EG_SUS : (byte) EG_DEC;
                            }

                            // recalculate EG output
                            if ((this.ssg & 0x08) != 0 && (this.ssgn ^ (this.ssg & 0x04)) != 0)
                                this.volOut = ((0x200 - this.volume) & MAX_ATT_INDEX) + this.tl;
                            else
                                this.volOut = this.volume + this.tl;
                        }

                        this.key = 1;
                    }

                    public void keyOnScm(boolean enabled) {
                        if (this.key == 0 && enabled) {
                            // restart Phase Generator
                            this.phase = 0;

                            // reset SSG-EG inversion flag
                            this.ssgn = 0;

                            if ((this.ar + this.ksr) < 32 + 62) {
                                this.state = (byte) ((this.volume <= MIN_ATT_INDEX) ? ((this.sl == MIN_ATT_INDEX) ? EG_SUS : EG_DEC) : EG_ATT);
                            } else {
                                // force attenuation level to 0
                                this.volume = MIN_ATT_INDEX;

                                // directly switch to Decay (or Sustain)
                                this.state = (byte) ((this.sl == MIN_ATT_INDEX) ? EG_SUS : EG_DEC);
                            }

                            // recalculate EG output
                            if ((this.ssg & 0x08) != 0 && (this.ssgn ^ (this.ssg & 0x04)) != 0)
                                this.volOut = ((0x200 - this.volume) & MAX_ATT_INDEX) + this.tl;
                            else
                                this.volOut = this.volume + this.tl;
                        }
                    }

                    public void keyOff(boolean enabled) {
                        if (this.key != 0 && enabled) {
                            if (isVGMInit != 0) { // workaround for VGMs trimmed with VGMTool
                                this.state = EG_OFF;
                                this.volume = MAX_ATT_INDEX;
                                this.volOut = MAX_ATT_INDEX;
                            } else if (this.state > EG_REL) {
                                this.state = EG_REL; // phase . Release

                                // SSG-EG specific update
                                if ((this.ssg & 0x08) != 0) {
                                    // convert EG attenuation level
                                    if ((this.ssgn ^ (this.ssg & 0x04)) != 0)
                                        this.volume = (0x200 - this.volume);

                                    // force EG attenuation level
                                    if (this.volume >= 0x200) {
                                        this.volume = MAX_ATT_INDEX;
                                        this.state = EG_OFF;
                                    }

                                    // recalculate EG output
                                    this.volOut = this.volume + this.tl;
                                }
                            }
                        }

                        this.key = 0;
                    }

                    public void reset() {
                        this.incr = -1;
                        this.key = 0;
                        this.phase = 0;
                        this.ssg = 0;
                        this.ssgn = 0;
                        this.state = EG_OFF;
                        this.volume = MAX_ATT_INDEX;
                        this.volOut = MAX_ATT_INDEX;
                    }

                    public void attack(int egCnt) {
                        if ((egCnt & ((1 << this.egShAr) - 1)) == 0) {
                            // update attenuation level
                            this.volume += (~this.volume * (egInc[this.egSelAr + ((egCnt >> this.egShAr) & 7)])) >> 4;

                            // check phase transition
                            if (this.volume <= MIN_ATT_INDEX) {
                                this.volume = MIN_ATT_INDEX;
                                this.state = (byte) ((this.sl == MIN_ATT_INDEX) ? EG_SUS : EG_DEC); // special case where SL=0
                            }

                            // recalculate EG output
                            if ((this.ssg & 0x08) != 0 && (this.ssgn ^ (this.ssg & 0x04)) != 0) // SSG-EG Output Inversion
                                this.volOut = ((0x200 - this.volume) & MAX_ATT_INDEX) + this.tl;
                            else
                                this.volOut = this.volume + this.tl;
                        }
//Debug.printf(Level.FINE, "this.state:%d this.volOut:%d", this.state, this.vol_out));
                    }

                    public void decay(int egCnt) {
                        if ((egCnt & ((1 << this.egShD1R) - 1)) == 0) {
                            // SSG EG type
                            if ((this.ssg & 0x08) != 0) {
                                // update attenuation level
                                if (this.volume < 0x200) {
                                    this.volume += 4 * egInc[this.egSelD1R + ((egCnt >> this.egShD1R) & 7)];

                                    // recalculate EG output
                                    if ((this.ssgn ^ (byte) (this.ssg & 0x04)) != 0) // SSG-EG Output Inversion
                                        this.volOut = ((0x200 - this.volume) & MAX_ATT_INDEX) + this.tl;
                                    else
                                        this.volOut = this.volume + this.tl;
                                }

                            } else {
                                // update attenuation level
                                this.volume += egInc[this.egSelD1R + ((egCnt >> this.egShD1R) & 7)];

                                // recalculate EG output
                                this.volOut = this.volume + this.tl;
                            }

                            // check phase transition
                            if (this.volume >= this.sl)
                                this.state = EG_SUS;
                        }
//Debug.printf(Level.FINE, "this.state:%d this.volOut:%d", this.state, this.volOut));
                    }

                    public void sustain(int egCnt) {
                        if ((egCnt & ((1 << this.egShD2R) - 1)) == 0) {
                            // SSG EG type
                            if ((this.ssg & 0x08) != 0) {
                                // update attenuation level
                                if (this.volume < 0x200) {
                                    this.volume += 4 * egInc[this.egSelD2R + ((egCnt >> this.egShD2R) & 7)];

                                    // recalculate EG output
                                    if ((this.ssgn ^ (byte) (this.ssg & 0x04)) != 0) // SSG-EG Output Inversion
                                        this.volOut = ((0x200 - this.volume) & MAX_ATT_INDEX) + this.tl;
                                    else
                                        this.volOut = this.volume + this.tl;
                                }
                            } else {
                                // update attenuation level
                                this.volume += egInc[this.egSelD2R + ((egCnt >> this.egShD2R) & 7)];

                                // check phase transition*/
                                if (this.volume >= MAX_ATT_INDEX)
                                    this.volume = MAX_ATT_INDEX;
                                // do not change this.state (verified on real chip)

                                // recalculate EG output
                                this.volOut = this.volume + this.tl;
                            }
                        }
//Debug.printf(Level.FINE, "this.state:%d this.volOut:%d", this.state, this.volOut));
                    }

                    public void release(int egCnt) {
                        if ((egCnt & ((1 << this.egShRr) - 1)) == 0) {
                            // SSG EG type
                            if ((this.ssg & 0x08) != 0) {
                                // update attenuation level
                                if (this.volume < 0x200)
                                    this.volume += 4 * egInc[this.egSelRr + ((egCnt >> this.egShRr) & 7)];
                                // check phase transition
                                if (this.volume >= 0x200) {
                                    this.volume = MAX_ATT_INDEX;
                                    this.state = EG_OFF;
                                }
                            } else {
                                // update attenuation level
                                this.volume += egInc[this.egSelRr + ((egCnt >> this.egShRr) & 7)];

                                // check phase transition*/
                                if (this.volume >= MAX_ATT_INDEX) {
                                    this.volume = MAX_ATT_INDEX;
                                    this.state = EG_OFF;
                                }
                            }

                            // recalculate EG output
                            this.volOut = this.volume + this.tl;
                        }
//Debug.printf(Level.FINE, "this.state:%d this.volOut:%d", this.state, this.volOut));
                    }

                    /**
                     * update phase increment and envelope generator
                     */
                    private void refreshFcEg(int fc, int kc, int fnMax) {
                        int ksr = kc >> this.KSR;

                        fc += this.dt[kc];

                        // detects frequency overflow (credits to Nemesis)
                        if (fc < 0) fc += fnMax;

                        // (frequency) phase increment counter
                        this.incr = (fc * this.mul) >> 1;

                        if (this.ksr != ksr) {
                            this.ksr = (byte) ksr;

                            // calculate envelope generator rates
                            if ((this.ar + this.ksr) < 32 + 62) {
                                this.egShAr = egRateShift[this.ar + this.ksr];
                                this.egSelAr = egRateSelect2612[this.ar + this.ksr];
                            } else {
                                this.egShAr = 0;
                                this.egSelAr = (byte) (18 * RATE_STEPS); // verified by Nemesis on real hardware (Attack phase is blocked)
                            }

                            this.egShD1R = egRateShift[this.d1r + this.ksr];
                            this.egShD2R = egRateShift[this.d2r + this.ksr];
                            this.egShRr = egRateShift[this.rr + this.ksr];

                            this.egSelD1R = egRateSelect2612[this.d1r + this.ksr];
                            this.egSelD2R = egRateSelect2612[this.d2r + this.ksr];
                            this.egSelRr = egRateSelect2612[this.rr + this.ksr];
                        }
                    }

                    private void updatePhaseLfo(int pms, int blockFnum, int lfoPm, int[] fnTable, int fnMax) {
                        int fnum_lfo = ((blockFnum & 0x7f0) >> 4) * 32 * 8;
                        int lfo_fn_table_index_offset = lfoPmTable[fnum_lfo + pms + lfoPm];

                        if (lfo_fn_table_index_offset != 0) { // LFO phase modulation active
                            byte blk = (byte) ((blockFnum & 0x7000) >> 12);
                            int fn = blockFnum & 0xfff;

                            // recalculate keyscale code
                            // This really stupid bug caused a read outside the
                            // array [size 0x10] and returned invalid values.
                            // This caused an annoying vibrato for some notes.
                            // (Note: seems to be a copy-and-paste from OPNWriteReg . case 0xA0)
                            // Why are MAME cores always SOO buggy ?!
                            // Oh, and before I forget: it's correct in Fm.c
                            int kc = (blk << 2) | opnFkTable[fn >> 8];
                            // Thanks to Blargg - his patch that helped me find this bug

                            // recalculate (frequency) phase increment counter
                            int fc = (fnTable[fn] >> (7 - blk)) + this.dt[kc];

                            // (frequency) phase overflow (credits to Nemesis)
                            if (fc < 0) fc += fnMax;

                            // update phase
                            this.phase += (fc * this.mul) >> 1;
                        } else { // LFO phase modulation = zero
                            this.phase += this.incr;
                        }
                    }

                    public void advanceEg(int egCnt) {
                        switch (this.state) {
                        case EG_ATT: // attack phase
                            this.attack(egCnt);
                            break;

                        case EG_DEC: // decay phase
                            this.decay(egCnt);
                            break;

                        case EG_SUS: // sustain phase
                            this.sustain(egCnt);
                            break;

                        case EG_REL: // release phase
                            this.release(egCnt);
                            break;
                        }

                        // Valley Bell: These few lines are missing in Genesis Plus GX' Ym2612 core file.
                        //              Disabling them fixes the SSG-EG.
                        // Additional Note: Asterix and the Great Rescue: Level 1 sounds "better" with these lines,
                        //                  but less accurate.
//                        out = (int) this.volume;
//
//                        // negate output (changes come from alternate bit, init comes from attack bit)
//                        if ((this.ssg & 0x08) && (this.ssgn & 2) && (this.state > EG_REL))
//                            out ^= MAX_ATT_INDEX;
//
//                        // we need to store the result here because we are going to change ssgn
//                        //  in next instruction
//                        this.vol_out = out + this.tl;
                    }

                    public void updateSsgEg() {
                        // detect SSG-EG transition
                        // this is not required during release phase as the attenuation has been forced to MAX and output invert flag is not used
                        // if an Attack Phase is programmed, inversion can occur on each sample
                        if ((this.ssg & 0x08) != 0 && (this.volume >= 0x200) && (this.state > EG_REL)) {
                            if ((this.ssg & 0x01) != 0) { // bit 0 = hold SSG-EG
                                // set inversion flag
                                if ((this.ssg & 0x02) != 0)
                                    this.ssgn = 4;

                                // force attenuation level during decay phases
                                if ((this.state != EG_ATT) && (this.ssgn ^ (this.ssg & 0x04)) == 0)
                                    this.volume = MAX_ATT_INDEX;
                            } else { // loop SSG-EG
                                // toggle output inversion flag or reset Phase Generator
                                if ((this.ssg & 0x02) != 0)
                                    this.ssgn ^= 4;
                                else
                                    this.phase = 0;

                                // same as Key ON
                                if (this.state != EG_ATT) {
                                    if ((this.ar + this.ksr) < 32 + 62) {
                                        this.state = (byte) ((this.volume <= MIN_ATT_INDEX) ? ((this.sl == MIN_ATT_INDEX) ? EG_SUS : EG_DEC) : EG_ATT);
                                    } else {
                                        // Attack Rate is maximal: directly switch to Decay or Substain
                                        this.volume = MIN_ATT_INDEX;
                                        this.state = (byte) ((this.sl == MIN_ATT_INDEX) ? EG_SUS : EG_DEC);
                                    }
                                }
                            }

                            // recalculate EG output
                            if ((this.ssgn ^ (this.ssg & 0x04)) != 0)
                                this.volOut = ((0x200 - this.volume) & MAX_ATT_INDEX) + this.tl;
                            else
                                this.volOut = this.volume + this.tl;
                        }
                    }
                }

                /**
                 * four SLOTs (operators)
                 */
                public Slot[] slots = new Slot[] {
                        new Slot(), new Slot(), new Slot(), new Slot()
                };

                /**
                 * algorithm
                 */
                public byte algo;
                /**
                 * feedback shift
                 */
                public byte fb;
                /**
                 * op1 output for feedback
                 */
                public int[] op1Out = new int[2];

                /**
                 * SLOT1 output pointer
                 */
                public Op connect1;
                /**
                 * SLOT3 output pointer
                 */
                public Op connect3;
                /**
                 * SLOT2 output pointer
                 */
                public Op connect2;
                /**
                 * SLOT4 output pointer
                 */
                public Op connect4;
                /**
                 * where to put the delayed sample (MEM)
                 */
                public Op memConnect;

                /**
                 * delayed sample (MEM) value
                 */
                public int memValue;

                /**
                 * channel PMS
                 */
                public int pms;
                /**
                 * channel AMS
                 */
                public byte ams;

                /**
                 * fnum,blk:adjusted to sample rate
                 */
                public int fc;
                /**
                 * key code:
                 */
                public byte kCode;
                /**
                 * current blk/fnum value for this slot (can be different betweeen slots of one channel in 3slot mode)
                 */
                public int blockFnum;
                public byte muted;

                private void keyOn(int s, boolean b) {
                    Channel.Slot slot = this.slots[s];
                    slot.keyOn(b);

                    //Debug.printf(Level.FINE, "volOut:%d", this.volOut);
                }

                private void keyOff(int s, boolean b) {
                    Channel.Slot slot = this.slots[s];
                    slot.keyOff(b);
                }

                private void keyOnCsm(int s, boolean b) {
                    Channel.Slot slot = this.slots[s];
                    slot.keyOnScm(b);
                }

                private void keyOffScm(int s) {
                    this.slots[s].keyOffCsm();
                }

                public void reset() {
                    this.memValue = 0;
                    this.op1Out[0] = 0;
                    this.op1Out[1] = 0;
                    this.fc = 0;
                    for (int s = 0; s < 4; s++) {
                        this.slots[s].reset();
                    }
                }

                /**
                 * update phase increment counters
                 */
                private void refreshFcEg(int fmax) {
                    if (this.slots[SLOT1].incr == -1) {
                        int fc = this.fc;
                        int kc = this.kCode;
                        this.slots[SLOT1].refreshFcEg(fc, kc, fmax);
                        this.slots[SLOT2].refreshFcEg(fc, kc, fmax);
                        this.slots[SLOT3].refreshFcEg(fc, kc, fmax);
                        this.slots[SLOT4].refreshFcEg(fc, kc, fmax);
                    }
                }

                void setOps(Op[] ops) {
                    this.connect1 = ops[0];
                    this.connect3 = ops[1];
                    this.connect2 = ops[2];
                    this.memConnect = ops[3];
                    this.connect4 = ops[4];
                }

                private void updatePhaseLfo(int lfoPm, int[] fnTable, int fnMax) {
                    int blockFnum = this.blockFnum;

                    int fNumLfo = ((blockFnum & 0x7f0) >> 4) * 32 * 8;
                    int lfoFnTableIndexOffset = lfoPmTable[fNumLfo + this.pms + lfoPm];

                    blockFnum = blockFnum * 2 + lfoFnTableIndexOffset;

                    if (lfoFnTableIndexOffset != 0) { // LFO phase modulation active
                        byte blk = (byte) ((blockFnum & 0x7000) >> 12);
                        int fn = blockFnum & 0xfff;

                        // recalculate keyscale code
                        //int kc = (blk<<2) | opn_fktable[fn >> 7];
                        // the same stupid bug as above
                        int kc = (blk << 2) | opnFkTable[fn >> 8];

                        // recalculate (frequency) phase increment counter
                        int fc = fnTable[fn] >> (7 - blk);

                        // (frequency) phase overflow (credits to Nemesis)
                        int finc = fc + this.slots[SLOT1].dt[kc];
                        if (finc < 0) finc += fnMax;
                        this.slots[SLOT1].phase += (finc * this.slots[SLOT1].mul) >> 1;

                        finc = fc + this.slots[SLOT2].dt[kc];
                        if (finc < 0) finc += fnMax;
                        this.slots[SLOT2].phase += (finc * this.slots[SLOT2].mul) >> 1;

                        finc = fc + this.slots[SLOT3].dt[kc];
                        if (finc < 0) finc += fnMax;
                        this.slots[SLOT3].phase += (finc * this.slots[SLOT3].mul) >> 1;

                        finc = fc + this.slots[SLOT4].dt[kc];
                        if (finc < 0) finc += fnMax;
                        this.slots[SLOT4].phase += (finc * this.slots[SLOT4].mul) >> 1;
                    } else { // LFO phase modulation = zero
                        this.slots[SLOT1].phase += this.slots[SLOT1].incr;
                        this.slots[SLOT2].phase += this.slots[SLOT2].incr;
                        this.slots[SLOT3].phase += this.slots[SLOT3].incr;
                        this.slots[SLOT4].phase += this.slots[SLOT4].incr;
                    }
                }

                /**
                 * CSM Key Controll
                 */
                private void controllKeyCsm(boolean b) {
                    // all key ON (verified by Nemesis on real hardware)
                    this.keyOnCsm(SLOT1, b);
                    this.keyOnCsm(SLOT2, b);
                    this.keyOnCsm(SLOT3, b);
                    this.keyOnCsm(SLOT4, b);
                }

                private void advanceEg(int egCnt) {
                    //unsigned int out;
                    int i = 4; // four operators per channel
                    int slotPtr = 0;

                    do {
                        Channel.Slot slot = this.slots[slotPtr];
                        slot.advanceEg(egCnt);

                        slotPtr++;
                        i--;
                    } while (i != 0);
                }

                /**
                 * SSG-EG update process
                 * The behavior is based upon Nemesis tests on real hardware
                 * This is actually executed before each samples
                 */
                private void updateSsgEg() {
                    int i = 4; // four operators per channel
                    int slotPtr = 0;
                    do {
                        Channel.Slot slot = this.slots[slotPtr];
                        slot.updateSsgEg();

                        // next slots
                        slotPtr++;
                        i--;
                    } while (i != 0);
                }

                private void calc(Opn opn, boolean b) {
                    int am = opn.lfoAm >> this.ams;
                    int egOut;

                    if (this.muted != 0)
                        return;

                    opn.m2.val = opn.c1.val = opn.c2.val = opn.mem.val = 0;

                    this.memConnect.val = this.memValue; // restore delayed sample (MEM) value to m2 or c2

                    egOut = this.slots[SLOT1].calcVolume(am);

                    int _out = this.op1Out[0] + this.op1Out[1];
                    this.op1Out[0] = this.op1Out[1];

                    if (this.connect1 == null) {
                        // algorithm 5
                        opn.mem.val = opn.c1.val = opn.c2.val = this.op1Out[0];
                    } else {
                        // other algorithms
                        this.connect1.val += this.op1Out[0];
                    }

                    this.op1Out[1] = 0;
                    if (egOut < ENV_QUIET) { // SLOT 1
                        if (this.fb == 0)
                            _out = 0;

                        this.op1Out[1] = Op.calc1(this.slots[SLOT1].phase, egOut, (_out << this.fb));
                        //Debug.printf(Level.FINE, "SLOT1:egOut:%d", egOut);
                        //Debug.printf(Level.FINE, "op1_out[1]:%d", this.op1_out[1]);
                        //Debug.printf(Level.FINE, "this.SLOT[SLOT4].phase:%d", this.SLOT[SLOT1].phase);
                        //Debug.printf(Level.FINE, "out:%d", _out);
                        //Debug.printf(Level.FINE, "this.FB:%d", this.FB);
                        //Debug.printf(Level.FINE, "o<c:%d", (_out << this.FB));
                    }

                    egOut = this.slots[SLOT3].calcVolume(am);
                    if (egOut < ENV_QUIET) { // SLOT 3
                        this.connect3.val += opn.m2.calc(this.slots[SLOT3].phase, egOut);
                        //Debug.printf(Level.FINE, "SLOT3:egOut:%d", egOut);
                        //Debug.printf(Level.FINE, "this.connect3.val:%d", this.connect3.val);
                    }

                    egOut = this.slots[SLOT2].calcVolume(am);
                    if (egOut < ENV_QUIET) { // SLOT 2
                        this.connect2.val += opn.c1.calc(this.slots[SLOT2].phase, egOut);
                        //Debug.printf(Level.FINE, "SLOT2:egOut:%d", egOut);
                        //Debug.printf(Level.FINE, "this.connect2.val:%d", this.connect2.val);
                    }

                    egOut = this.slots[SLOT4].calcVolume(am);
                    if (egOut < ENV_QUIET) { // SLOT 4
                        this.connect4.val += opn.c2.calc(this.slots[SLOT4].phase, egOut);
                        //Debug.printf(Level.FINE, "SLOT4:egOut:%d", egOut);
                        //Debug.printf(Level.FINE, "this.connect4.val:%d", this.connect4.val);
                        //Debug.printf(Level.FINE, "this.SLOT[SLOT4].phase:%d", this.SLOT[SLOT4].phase);
                        //Debug.printf(Level.FINE, "opn.c2:%d", opn.c2.val);
                    }

                    // store current MEM
                    this.memValue = opn.mem.val;

                    // update phase counters AFTER output calculations
                    if (this.pms != 0) {
                        // add support for 3 slot mode
                        if ((opn.st.mode & 0xC0) != 0 && b) {
                            this.slots[SLOT1].updatePhaseLfo(this.pms, opn.sl3.blockFnum[1], opn.lfoPm, opn.fnTable, opn.fnMax);
                            this.slots[SLOT2].updatePhaseLfo(this.pms, opn.sl3.blockFnum[2], opn.lfoPm, opn.fnTable, opn.fnMax);
                            this.slots[SLOT3].updatePhaseLfo(this.pms, opn.sl3.blockFnum[0], opn.lfoPm, opn.fnTable, opn.fnMax);
                            this.slots[SLOT4].updatePhaseLfo(this.pms, this.blockFnum, opn.lfoPm, opn.fnTable, opn.fnMax);
                        } else this.updatePhaseLfo(opn.lfoPm, opn.fnTable, opn.fnMax);
                    } else { // no LFO phase modulation
                        this.slots[SLOT1].phase += this.slots[SLOT1].incr;
                        this.slots[SLOT2].phase += this.slots[SLOT2].incr;
                        this.slots[SLOT3].phase += this.slots[SLOT3].incr;
                        this.slots[SLOT4].phase += this.slots[SLOT4].incr;
                    }
                }
            }

            static class State {
                /**
                 * this chip parameter
                 */
                public Ym2612 param;
                /**
                 * frequency base
                 */
                public double freqbase;
                /**
                 * timer prescaler
                 */
                public int timer_prescaler;
                /**
                 * interrupt level
                 */
                public byte irq;
                /**
                 * irq mask
                 */
                public byte irqmask;
//#if FM_BUSY_FLAG_SUPPORT
                // TIME_TYPE busy_expiry_time; // expiry time of the busy status
//#endif
                /**
                 * master clock  (Hz)
                 */
                public int clock;
                /**
                 * sampling rate (Hz)
                 */
                public int rate;
                /**
                 * address register
                 */
                public byte address;
                /**
                 * status flag
                 */
                public byte status;
                /**
                 * mode  CSM / 3SLOT
                 */
                public int mode;
                /**
                 * freq latch
                 */
                public byte fn_h;
                /**
                 * prescaler selector
                 */
                public byte prescalerSel;
                /**
                 * timer a
                 */
                public int ta;
                /**
                 * timer a counter
                 */
                public int tac;
                /**
                 * timer b
                 */
                public byte tb;
                /**
                 * timer b counter
                 */
                public int tbc;

                // local timetables

                /**
                 * DeTune table
                 */
                public int[][] dt_tab = new int[][] {
                        new int[32], new int[32], new int[32], new int[32],
                        new int[32], new int[32], new int[32], new int[32]
                };

                /**
                 * Extention Timer and IRQ handler
                 */
                public interface TimerHandler extends QuadConsumer<Object, Integer, Integer, Integer> {
                }

                public TimerHandler timerHandler;

                public interface IrqHandler extends BiConsumer<BaseChip, Integer> {
                }

                public IrqHandler irqHandler;
                public Fm.Callbacks ssg;

                /**
                 * status set and IRQ handling
                 */
                private void setStatus(int flag) {
                    // set status flag
                    this.status |= (byte) flag;
                    if (this.irq == 0 && ((this.status & this.irqmask) != 0)) {
                        this.irq = 1;
                        // Callback user interrupt handler (IRQ is OFF to ON)
                        if (this.irqHandler != null) this.irqHandler.accept(this.param, 1);
                    }
                }

                /**
                 * status reset and IRQ handling
                 */
                private void resetStatus(int flag) {
                    // reset status flag
                    this.status &= (byte) ~flag;
                    if ((this.irq != 0) && ((this.status & this.irqmask) == 0)) {
                        this.irq = 0;
                        // Callback user interrupt handler (IRQ is ON to OFF)
                        if (this.irqHandler != null) this.irqHandler.accept(this.param, 0);
                    }
                }

                /**
                 * IRQ mask set
                 */
                private void setIrqMask(int flag) {
                    this.irqmask = (byte) flag;
                    // IRQ handling check
                    setStatus(0);
                    resetStatus(0);
                }

                /**
                 * calculate timer A
                 */
                private void internalTimerA(Opn opn, Channel csmCh) {
                    if (this.tac != 0 && (this.timerHandler == null))
                        if ((this.tac -= (int) (this.freqbase * 4096)) <= 0) {
                            this.timerAOver();
                            // CSM mode total level latch and auto key on
                            if ((this.mode & 0x80) != 0) {
                                csmCh.controllKeyCsm(opn.sl3.keyCsm == 0);
                                opn.sl3.keyCsm = 1;
                            }
                        }
                }

                /**
                 * calculate timer B
                 */
                private void internalTimerB(int step) {
                    if (this.tbc != 0 && this.timerHandler == null)
                        if ((this.tbc -= (int) (this.freqbase * 4096 * step)) <= 0)
                            timerBOver();
                }

                private byte setStatus() {
                    return this.status;
                }

                private void setBusy(int bClock) {
                }

                private void clearBusy() {
                }

                /**
                 * Timer A Overflow
                 */
                private void timerAOver() {
                    // set status (if enabled)
                    if ((this.mode & 0x04) != 0) setStatus(0x01);
                    // clear or reload the counter
                    this.tac = (1024 - this.ta);
                    if (this.timerHandler != null)
                        this.timerHandler.accept(this.param, 0, this.tac * this.timer_prescaler, this.clock);
                    this.tac *= 4096;
                }

                /**
                 * Timer B Overflow
                 */
                private void timerBOver() {
                    // set status (if enabled)
                    if ((this.mode & 0x08) != 0) setStatus(0x02);
                    // clear or reload the counter
                    this.tbc = (256 - this.tb) << 4;
                    if (this.timerHandler != null)
                        this.timerHandler.accept(this.param, 1, this.tbc * this.timer_prescaler, this.clock);
                }

                public void setTimers(BaseChip n, int v) {
                    // reset Timer b flag
                    if ((v & 0x20) != 0)
                        this.resetStatus(0x02);
                    // reset Timer a flag
                    if ((v & 0x10) != 0)
                        this.resetStatus(0x01);
                    // load b
                    if ((v & 2) != 0 && (this.mode & 2) == 0) {
                        this.tbc = (256 - this.tb) << 4;
                        // External timer handler
                        if (this.timerHandler != null)
                            this.timerHandler.accept(n, 1, this.tbc * this.timer_prescaler, this.clock);
                    }
                    // load a
                    if ((v & 1) != 0 && (this.mode & 1) == 0) {
                        this.tac = (1024 - this.ta);
                        // External timer handler
                        if (this.timerHandler != null)
                            this.timerHandler.accept(n, 0, this.tac * this.timer_prescaler, this.clock);
                        this.tac *= 4096;
                    }

                    this.mode = v;
                }
            }

            /**
             * OPN 3slot struct
             */
            static class _3SLOT {
                /**
                 * fnum3,blk3: calculated
                 */
                public int[] fc = new int[3];
                /**
                 * freq3 latch
                 */
                public byte fnH;
                /**
                 * key code
                 */
                public byte[] kCode = new byte[3];
                /**
                 * current fnum value for this slot (can be different betweeen slots of one channel in 3slot mode)
                 */
                public int[] blockFnum = new int[3];
                /**
                 * CSM mode Key-ON flag
                 */
                public byte keyCsm;
            }

            private byte isVGMInit;

            // register number to channel number , slot offset

            private byte chan(int n) {
                return (byte) (n & 3);
            }

            private int slot(int s) {
                return ((s >> 2) & 3);
            }

            /**
             * chip type
             */
            public byte type;
            /**
             * general state
             */
            public State st = new State();
            /**
             * 3 slot mode state
             */
            public _3SLOT sl3 = new _3SLOT();
            /**
             * pointer of CH
             */
            public Channel[] channels;
            /**
             * Fm channels output masks (0xffffffff = enable)
             */
            public int[] pan = new int[6 * 2];

            /**
             * Global envelope generator counter
             */
            public int egCnt;
            /**
             * Global envelope generator counter works at frequency = chipclock/64/3
             */
            public int egTimer;
            /**
             * step of eg_timer
             */
            public int egTimerAdd;
            /**
             * envelope generator timer overlfows every 3 samples (on real chip)
             */
            public int egTimerOverflow;

            // there are 2048 FNUMs that can be generated using FNUM/BLK registers
            // but LFO works with one more bit of a precision so we really need 4096 elements

            /**
             * F-number increment counter
             */
            public int[] fnTable = new int[4096];
            /**
             * maximal phase increment (used for phase overflow)
             */
            public int fnMax;

            // LFO

            /**
             * current LFO phase (of 128)
             */
            public byte lfoCnt;
            /**
             * current LFO phase runs at LFO frequency
             */
            public int lfoTimer;
            /**
             * step of lfo_timer
             */
            public int lfoTimerAdd;
            /**
             * LFO timer overflows every N samples (depends on LFO frequency)
             */
            public int lfoTimerOverflow;
            /**
             * current LFO AM step
             */
            public int lfoAm;
            /**
             * current LFO PM step
             */
            public int lfoPm;

            /**
             * Phase Modulation input for operators 2,3,4
             */
            public Channel.Op m2 = new Channel.Op(),
                    c1 = new Channel.Op(),
                    c2 = new Channel.Op();
            /**
             * one sample delay memory
             */
            public Channel.Op mem = new Channel.Op();
            /**
             * outputs of working channels
             */
            public Channel.Op[] outFm = new Channel.Op[] {
                    new Channel.Op(), new Channel.Op(), new Channel.Op(), new Channel.Op(),
                    new Channel.Op(), new Channel.Op()
            };

            /**
             * OPN Mode Register Write
             */
            private void setTimers(int v) {
                // b7 = CSM MODE
                // b6 = 3 slot mode
                // b5 = reset b
                // b4 = reset a
                // b3 = timer enable b
                // b2 = timer enable a
                // b1 = load b
                // b0 = load a

                if (((this.st.mode ^ v) & 0xC0) != 0) {
                    // phase increment need to be recalculated
                    this.channels[2].slots[SLOT1].incr = -1;

                    // CSM mode disabled and CSM key ON active
                    if (((v & 0xC0) != 0x80) && this.sl3.keyCsm != 0) {
                        // CSM Mode Key OFF (verified by Nemesis on real hardware)
                        this.channels[2].keyOffScm(SLOT1);
                        this.channels[2].keyOffScm(SLOT2);
                        this.channels[2].keyOffScm(SLOT3);
                        this.channels[2].keyOffScm(SLOT4);
                        this.sl3.keyCsm = 0;
                    }
                }
            }

            // internal timer mode , update timer
            // Valley Bell: defines fixed

            /**
             * set algorithm connection
             */
            private Channel.Op[] setupConnection(int algo, int ch) {
                Channel.Op carrier = this.outFm[ch];
                Channel.Op om1 = null, om2 = null, oc1 = null, memc = null;

                switch (algo) {
                case 0:
                    // M1---C1---MEM---M2---C2---OUT
                    om1 = this.c1;
                    oc1 = this.mem;
                    om2 = this.c2;
                    memc = this.m2;
                    break;
                case 1:
                    // M1------+-MEM---M2---C2---OUT
                    //      C1-+
                    om1 = this.mem;
                    oc1 = this.mem;
                    om2 = this.c2;
                    memc = this.m2;
                    break;
                case 2:
                    // M1-----------------+-C2---OUT
                    //      C1---MEM---M2-+
                    om1 = this.c2;
                    oc1 = this.mem;
                    om2 = this.c2;
                    memc = this.m2;
                    break;
                case 3:
                    // M1---C1---MEM------+-C2---OUT
                    //                 M2-+
                    om1 = this.c1;
                    oc1 = this.mem;
                    om2 = this.c2;
                    memc = this.c2;
                    break;
                case 4:
                    // M1---C1-+-OUT
                    // M2---C2-+
                    // MEM: not used
                    om1 = this.c1;
                    oc1 = carrier;
                    om2 = this.c2;
                    memc = this.mem; // store it anywhere where it will not be used
                    break;
                case 5:
                    //    +----C1----+
                    // M1-+-MEM---M2-+-OUT
                    //    +----C2----+
                    om1 = null; // special mark
                    oc1 = carrier;
                    om2 = carrier;
                    memc = this.m2;
                    break;
                case 6:
                    // M1---C1-+
                    //      M2-+-OUT
                    //      C2-+
                    // MEM: not used
                    om1 = this.c1;
                    oc1 = carrier;
                    om2 = carrier;
                    memc = this.mem; // store it anywhere where it will not be used
                    break;
                case 7:
                    // M1-+
                    // C1-+-OUT
                    // M2-+
                    // C2-+
                    // MEM: not used*/
                    om1 = carrier;
                    oc1 = carrier;
                    om2 = carrier;
                    memc = this.mem; // store it anywhere where it will not be used
                    break;
                }

                return new Channel.Op[] {om1, om2, oc1, memc, carrier};
            }

            /**
             * advance LFO to next sample
             */
            private void advanceLfo() {
                if (this.lfoTimerOverflow != 0) { // LFO enabled ?
                    // increment LFO timer
                    this.lfoTimer += this.lfoTimerAdd;

                    // when LFO is enabled, one level will last for 108, 77, 71, 67, 62, 44, 8 or 5 samples
                    while (this.lfoTimer >= this.lfoTimerOverflow) {
                        this.lfoTimer -= this.lfoTimerOverflow;

                        // There are 128 LFO steps
                        this.lfoCnt = (byte) ((this.lfoCnt + 1) & 127);

                        // Valley Bell: Replaced old code (non-inverted triangle) with
                        // the one from Genesis Plus GX 1.71.
                        // triangle (inverted)
                        // AM: from 126 to 0 step -2, 0 to 126 step +2
                        if (this.lfoCnt < 64)
                            this.lfoAm = (this.lfoCnt ^ 63) << 1;
                        else
                            this.lfoAm = (this.lfoCnt & 63) << 1;

                        // PM works with 4 times slower clock
                        this.lfoPm = this.lfoCnt >> 2;
                    }
                }
            }

            private static byte o2(int a) {
                return (byte) (a * 1);
            }

            /**
             * Envelope Generator counter shifts (32 + 64 rates + 32 RKS)
             *
             * rate  0,    1,    2,   3,   4,   5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15
             * shift 11,   10,   9,   8,   7,   6,  5,  4,  3,  2, 1,  0,  0,  0,  0,  0
             * mask  2047, 1023, 511, 255, 127, 63, 31, 15, 7,  3, 1,  0,  0,  0,  0,  0
             */
            private static final byte[] egRateShift = new byte[] {
                    // 32 infinite time rates
                    // O(0),O(0),O(0),O(0),O(0),O(0),O(0),O(0),
                    // O(0),O(0),O(0),O(0),O(0),O(0),O(0),O(0),
                    // O(0),O(0),O(0),O(0),O(0),O(0),O(0),O(0),
                    // O(0),O(0),O(0),O(0),O(0),O(0),O(0),O(0),

                    // fixed (should be the same as rate 0, even if it makes no difference since increment value is 0 for these rates)
                    o2(11), o2(11), o2(11), o2(11), o2(11), o2(11), o2(11), o2(11),
                    o2(11), o2(11), o2(11), o2(11), o2(11), o2(11), o2(11), o2(11),
                    o2(11), o2(11), o2(11), o2(11), o2(11), o2(11), o2(11), o2(11),
                    o2(11), o2(11), o2(11), o2(11), o2(11), o2(11), o2(11), o2(11),

                    // rates 00-11
                    o2(11), o2(11), o2(11), o2(11),
                    o2(10), o2(10), o2(10), o2(10),
                    o2(9), o2(9), o2(9), o2(9),
                    o2(8), o2(8), o2(8), o2(8),
                    o2(7), o2(7), o2(7), o2(7),
                    o2(6), o2(6), o2(6), o2(6),
                    o2(5), o2(5), o2(5), o2(5),
                    o2(4), o2(4), o2(4), o2(4),
                    o2(3), o2(3), o2(3), o2(3),
                    o2(2), o2(2), o2(2), o2(2),
                    o2(1), o2(1), o2(1), o2(1),
                    o2(0), o2(0), o2(0), o2(0),

                    // rate 12
                    o2(0), o2(0), o2(0), o2(0),

                    // rate 13
                    o2(0), o2(0), o2(0), o2(0),

                    // rate 14
                    o2(0), o2(0), o2(0), o2(0),

                    // rate 15
                    o2(0), o2(0), o2(0), o2(0),

                    // 32 dummy rates (same as 15 3)
                    o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),
                    o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),
                    o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),
                    o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0)
            };

            private static byte o(int a) {
                return (byte) (a * RATE_STEPS);
            }

            /**
             * Envelope Generator rates (32 + 64 rates + 32 RKS)
             * note that there is no O(17) in this table - it's directly in the code
             */
            private static final byte[] egRateSelect2612 = new byte[] {
                    // 32 infinite time rates (same as Rate 0)
                    o(18), o(18), o(18), o(18), o(18), o(18), o(18), o(18),
                    o(18), o(18), o(18), o(18), o(18), o(18), o(18), o(18),
                    o(18), o(18), o(18), o(18), o(18), o(18), o(18), o(18),
                    o(18), o(18), o(18), o(18), o(18), o(18), o(18), o(18),

                    // rates 00-11
                    // O( 0),O( 1),O( 2),O( 3),
                    // O( 0),O( 1),O( 2),O( 3),

                    o(18), o(18), o(0), o(0),
                    o(0), o(0), o(2), o(2), // Nemesis's tests

                    o(0), o(1), o(2), o(3),
                    o(0), o(1), o(2), o(3),
                    o(0), o(1), o(2), o(3),
                    o(0), o(1), o(2), o(3),
                    o(0), o(1), o(2), o(3),
                    o(0), o(1), o(2), o(3),
                    o(0), o(1), o(2), o(3),
                    o(0), o(1), o(2), o(3),
                    o(0), o(1), o(2), o(3),
                    o(0), o(1), o(2), o(3),

                    // rate 12
                    o(4), o(5), o(6), o(7),

                    // rate 13
                    o(8), o(9), o(10), o(11),

                    // rate 14
                    o(12), o(13), o(14), o(15),

                    // rate 15
                    o(16), o(16), o(16), o(16),

                    // 32 dummy rates (same as 15 3)
                    o(16), o(16), o(16), o(16), o(16), o(16), o(16), o(16),
                    o(16), o(16), o(16), o(16), o(16), o(16), o(16), o(16),
                    o(16), o(16), o(16), o(16), o(16), o(16), o(16), o(16),
                    o(16), o(16), o(16), o(16), o(16), o(16), o(16), o(16)
            };

            /**
             * write a opn mode register 0x20-0x2f
             */
            private void writeMode(int r, int v) {
                byte c;
                Channel ch;

                switch (r) {
                case 0x21: // Test
                    break;
                case 0x22: // LFO FREQ (YM2608/YM2610/YM2610B/Ym2612)
                    if ((v & 8) != 0) { // LFO enabled ?
//                    if (!this.lfo_timer_overflow) {
//                        // restart LFO
//                        this.lfo_cnt = 0;
//                        this.lfo_timer = 0;
//                        this.LFO_AM = 0;
//                        this.LFO_PM = 0;
//                    }

                        this.lfoTimerOverflow = lfoSamplesPerStep[v & 7] << LFO_SH;
                    } else {
                        // Valley Bell: Ported from Genesis Plus GX 1.71
                        // hold LFO waveform in reset state
                        this.lfoTimerOverflow = 0;
                        this.lfoTimer = 0;
                        this.lfoCnt = 0;


                        this.lfoPm = 0;
                        this.lfoAm = 126;
                        //this.lfo_timer_overflow = 0;
                    }
                    break;
                case 0x24: // timer A High 8*/
                    this.st.ta = (this.st.ta & 0x03) | (v << 2);
                    break;
                case 0x25: // timer A Low 2*/
                    this.st.ta = (this.st.ta & 0x3fc) | (v & 3);
                    break;
                case 0x26: // timer B
                    this.st.tb = (byte) v;
                    break;
                case 0x27: // mode, timer control
                    this.setTimers(v);
                    this.st.setTimers(this.st.param, v);
                    break;
                case 0x28: // key on / off
                    c = (byte) (v & 0x03);
                    if (c == 3) break;
                    if ((v & 0x04) != 0 && (this.type & TYPE_6CH) != 0) c += 3;
                    ch = this.channels[c];
                    boolean b = this.sl3.keyCsm == 0 || ch == this.channels[3];
                    if ((v & 0x10) != 0) ch.keyOn(SLOT1, b);
                    else ch.keyOff(SLOT1, b);
                    if ((v & 0x20) != 0) ch.keyOn(SLOT2, b);
                    else ch.keyOff(SLOT2, b);
                    if ((v & 0x40) != 0) ch.keyOn(SLOT3, b);
                    else ch.keyOff(SLOT3, b);
                    if ((v & 0x80) != 0) ch.keyOn(SLOT4, b);
                    else ch.keyOff(SLOT4, b);
                    break;
                }
            }

            /**
             * write an opn register (0x30-0xff)
             */
            private void writeReg(int r, int v) {
                Channel ch;
                Channel.Slot slot;

                byte c = chan(r);

                if (c == 3) return; // 0xX3,0xX7,0xXB,0xXF

                if (r >= 0x100) c += 3;

                ch = this.channels[c];

                slot = ch.slots[slot(r)];

                switch (r & 0xf0) {
                case 0x30: // DET , MUL
                    slot.setDetMul(this.st.dt_tab[(v >> 4) & 7], v);
                    ch.slots[SLOT1].incr = -1;
                    break;

                case 0x40: // TL
                    slot.setTl(v);
                    break;

                case 0x50: // KS, AR
                    byte old_KSR = slot.KSR;
                    slot.setArKsr(this.type, v);
                    if (slot.KSR != old_KSR) {
                        ch.slots[SLOT1].incr = -1;
                    }
                    break;

                case 0x60: // bit7 = AM ENABLE, DR
                    slot.setDr(this.type, v);

                    if ((this.type & TYPE_LFOPAN) != 0) { // YM2608/2610/2610B/2612
                        slot.aMmask = (v & 0x80) != 0 ? 0xffff_ffff : 0;
                    }
                    break;

                case 0x70: // SR
                    slot.setSr(this.type, v);
                    break;

                case 0x80: // SL, RR
                    slot.setSlRr(this.type, v);
                    break;

                case 0x90: // SSG-EG
                    slot.ssg = (byte) (v & 0x0f);

                    // recalculate EG output
                    if (slot.state > EG_REL) {
                        if ((slot.ssg & 0x08) != 0 && (slot.ssgn ^ (slot.ssg & 0x04)) != 0)
                            slot.volOut = ((0x200 - slot.volume) & MAX_ATT_INDEX) + slot.tl;
                        else
                            slot.volOut = slot.volume + slot.tl;
                    }

                 /* SSG-EG envelope shapes :

                 E AtAlH
                 1 0 0 0  \\\\

                 1 0 0 1  \___

                 1 0 1 0  \/\/
                     ___
                 1 0 1 1  \

                 1 1 0 0  ////
                     ___
                 1 1 0 1  /

                 1 1 1 0  /\/\

                 1 1 1 1  /___


                 E = SSG-EG enable


                 The shapes are generated using Attack, Decay and Sustain phases.

                 Each single character in the diagrams above represents this whole
                 sequence:

                 - when KEY-ON = 1, normal Attack phase is generated (*without* any
                   difference when compared to normal mode),

                 - later, when envelope level reaches minimum level (max volume),
                   the EG switches to Decay phase (which works with bigger steps
                   when compared to normal mode - see below),

                 - later when envelope level passes the SL level,
                   the EG swithes to Sustain phase (which works with bigger steps
                   when compared to normal mode - see below),

                 - finally when envelope level reaches maximum level (min volume),
                   the EG switches to Attack phase again (depends on actual waveform).

                 Important is that when switch to Attack phase occurs, the phase counter
                 of that Operator will be zeroed-out (as in normal KEY-ON) but not always.
                 (I haven't found the rule for that - perhaps only when the output level is low)

                 The difference (when compared to normal Envelope Generator mode) is
                 that the resolution in Decay and Sustain phases is 4 times lower;
                 this results in only 256 steps instead of normal 1024.
                 In other words:
                 when SSG-EG is disabled, the step inside the EG is one,
                 when SSG-EG is enabled, the step is four (in Decay and Sustain phases).

                 Times between the level changes are the same in both modes.


                 Important:
                 Decay 1 Level (so-called SL) is compared to actual SSG-EG output, so
                 it is the same in both SSG and no-SSG modes, with this exception:

                 when the SSG-EG is enabled and is generating raising levels
                 (when the EG output is inverted) the SL will be found at wrong level !!!
                 For example, when SL=02:
                  0 -6 = -6dB in non-inverted EG output
                  96-6 = -90dB in inverted EG output
                 Which means that EG compares its level to SL as usual, and that the
                 output is simply inverted after all.


                 The Yamaha's manuals say that AR should be set to 0x1f (max speed).
                 That is not necessary, but then EG will be generating Attack phase.

                 */
                    break;

                case 0xa0:
                    switch (slot(r)) {
                    case 0: // 0xa0-0xa2 : FNUM1
                        if (isVGMInit != 0)
                            this.st.fn_h = (byte) (ch.blockFnum >> 8);
                    {
                        int fn = (((this.st.fn_h) & 7) << 8) + v;
                        byte blk = (byte) (this.st.fn_h >> 3);
                        // keyscale code
                        ch.kCode = (byte) ((blk << 2) | opnFkTable[fn >> 7]);
                        // phase increment counter
                        ch.fc = this.fnTable[fn * 2] >> (7 - blk);

                        // store fnum in clear form for LFO PM calculations
                        ch.blockFnum = (blk << 11) | fn;

                        ch.slots[SLOT1].incr = -1;
                    }
                    break;
                    case 1: // 0xa4-0xa6 : FNUM2,BLK
                        this.st.fn_h = (byte) (v & 0x3f);
                        if (isVGMInit != 0) // workaround for stupid Kega Fusion init block
                            ch.blockFnum = (this.st.fn_h << 8) | (ch.blockFnum & 0xFF);
                        break;
                    case 2: // 0xa8-0xaa : 3CH FNUM1
                        if (isVGMInit != 0)
                            this.sl3.fnH = (byte) (this.sl3.blockFnum[c] >> 8);
                        if (r < 0x100) {
                            int fn = ((this.sl3.fnH & 7) << 8) + v;
                            byte blk = (byte) (this.sl3.fnH >> 3);
                            // keyscale code
                            this.sl3.kCode[c] = (byte) ((blk << 2) | opnFkTable[fn >> 7]);
                            // phase increment counter
                            this.sl3.fc[c] = this.fnTable[fn * 2] >> (7 - blk);
                            this.sl3.blockFnum[c] = (blk << 11) | fn;
                            (this.channels)[2].slots[SLOT1].incr = -1;
                        }
                        break;
                    case 3: // 0xac-0xae : 3CH FNUM2,BLK
                        if (r < 0x100) {
                            this.sl3.fnH = (byte) (v & 0x3f);
                            if (isVGMInit != 0)
                                this.sl3.blockFnum[c] = (this.sl3.fnH << 8) | (this.sl3.blockFnum[c] & 0xFF);
                        }
                        break;
                    }
                    break;

                case 0xb0:
                    switch (slot(r)) {
                    case 0: { // 0xb0-0xb2 : FB,ALGO
                        int feedback = (v >> 3) & 7;
                        ch.algo = (byte) (v & 7);
                        ch.fb = (byte) (feedback != 0 ? (byte) (feedback + 6) : 0);
                        ch.setOps(this.setupConnection(ch.algo, c));
                    }
                    break;
                    case 1: // 0xb4-0xb6 : L , R , AMS , PMS (Ym2612/YM2610B/YM2610/YM2608)
                        if ((this.type & TYPE_LFOPAN) != 0) {
                            // b0-2 PMS
                            ch.pms = (v & 7) * 32; // ch.pms = PM depth * 32 (index in lfo_pm_table)

                            // b4-5 AMS
                            ch.ams = lfoAmsDepthShift[(v >> 4) & 0x03];

                            // PAN :  b7 = L, b6 = R
                            this.pan[c * 2] = (v & 0x80) != 0 ? 0xffff_ffff : 0;
                            this.pan[c * 2 + 1] = (v & 0x40) != 0 ? 0xffff_ffff : 0;
                        }
                        break;
                    }
                    break;
                }
            }

            /**
             * initialize time tables
             */
            private void init_timetables(double freqBase) {
                int i, d;
                double rate;

                // DeTune table
                for (d = 0; d <= 3; d++) {
                    for (i = 0; i <= 31; i++) {
                        rate = ((double) dtTab[d * 32 + i]) * freqBase * (1 << (FREQ_SH - 10)); // -10 because chip works with 10.10 fixed point, while we use 16.16
                        this.st.dt_tab[d][i] = (int) rate;
                        this.st.dt_tab[d + 4][i] = -this.st.dt_tab[d][i];
                    }
                }

                // there are 2048 FNUMs that can be generated using FNUM/BLK registers
                // but LFO works with one more bit of a precision so we really need 4096 elements
                // calculate fnumber . increment counter table
                for (i = 0; i < 4096; i++) {
                    // freq table for octave 7
                    // opn phase increment counter = 20bit
                    // the correct formula is : F-Number = (144 * fnote * 2^20 / MPcm) / 2^(B-1)
                    // where sample clock is  MPcm/144
                    // this means the increment value for one clock sample is FNUM * 2^(B-1) = FNUM * 64 for octave 7
                    // we also need to handle the ratio between the chip frequency and the emulated frequency (can be 1.0)
                    this.fnTable[i] = (int) ((double) i * 32 * freqBase * (1 << (FREQ_SH - 10))); // -10 because chip works with 10.10 fixed point, while we use 16.16
                }

                // maximal frequency is required for Phase overflow calculation, register size is 17 bits (Nemesis)
                this.fnMax = (int) ((double) 0x20000 * freqBase * (1 << (FREQ_SH - 10)));
            }

            /**
             * prescaler set (and make time tables)
             */
            private void setPreS(int pres, int timerPreScaler, int ssgPreS) {
                // frequency base
                this.st.freqbase = this.st.rate != 0 ? ((double) this.st.clock / this.st.rate) / pres : 0;

                // EG is updated every 3 samples
                this.egTimerAdd = (int) ((1 << EG_SH) * this.st.freqbase);
                this.egTimerOverflow = (3) * (1 << EG_SH);

                // LFO timer increment (every samples)
                this.lfoTimerAdd = (int) ((1 << LFO_SH) * this.st.freqbase);

                // Timer base time
                this.st.timer_prescaler = timerPreScaler;

                // SSG part  prescaler set
                if (ssgPreS != 0) this.st.ssg.set_clock.accept(this.st.param, this.st.clock * 2 / ssgPreS);

                // make time tables
                init_timetables(this.st.freqbase);
            }
        }

        /*
         * initialize generic tables
         * allocate total level table (128kb space)
         */
        static {

            // build Linear Power Table
            for (short x = 0; x < TL_RES_LEN; x++) {
                double m = (1 << 16) / Math.pow(2, (x + 1) * (ENV_STEP / 4.0) / 8.0);
                m = Math.floor(m);

                // we never reach (1<<16) here due to the (x+1)
                // result fits within 16 bits at maximum

                int ni = (int) m & 0xffff;
                ni >>= 4;
                if ((ni & 1) != 0) // round to nearest
                    ni = ((ni >> 1) + 1);
                else
                    ni = (ni >> 1);
                ni <<= 2;

                // 14 bits (with sign bit)
                tlTab[x * 2 + 0] = ni;
                tlTab[x * 2 + 1] = -ni;

                // one entry in the 'Power' table use the following format, xxxxxyyyyyyyys with:
                //        s = sign bit
                // yyyyyyyy = 8-bits decimal part (0-TL_RES_LEN)
                // xxxxx    = 5-bits integer 'shift' value (0-31) but, since Power table output is 13 bits,
                //            any value above 13 (included) would be discarded.
                for (short i = 1; i < 13; i++) {
                    tlTab[x * 2 + 0 + i * 2 * TL_RES_LEN] = (tlTab[x * 2 + 0] >> i);
                    tlTab[x * 2 + 1 + i * 2 * TL_RES_LEN] = -tlTab[x * 2 + 0 + i * 2 * TL_RES_LEN];
                }
            }

//for (x = 0; x < TL_TAB_LEN; x++) {
// Debug.printf(Level.FINE, "%d,", tl_tab[x]));
//}

            // build Logarithmic Sinus table
            for (short i = 0; i < SIN_LEN; i++) {
                // non-standard sinus
                double m = Math.sin(((i * 2) + 1) * Math.PI / SIN_LEN); // checked against the real chip
                // we never reach zero here due to ((i*2)+1)

                double o;
                if (m > 0.0)
                    o = 8 * Math.log(1.0 / m) / Math.log(2.0); // convert to 'decibels'
                else
                    o = 8 * Math.log(-1.0 / m) / Math.log(2.0); // convert to 'decibels'

                o = o / (ENV_STEP / 4);

                short n = (short) (2.0 * o);
                if ((n & 1) != 0) // round to nearest
                    n = (short) ((n >> 1) + 1);
                else
                    n = (short) (n >> 1);

                // 13-bits (8.5) value is formatted for above 'Power' table
                sinTab[i] = n * 2 + (m >= 0.0 ? 0 : 1);
            }

//for (x = 0; x < SIN_LEN; x++) {
// Debug.printf(Level.FINE, "%d,", sin_tab[x]);
//}

            // build LFO PM modulation table
            for (short i = 0; i < 8; i++) { // 8 PM depths
                byte fnum;
                for (fnum = 0; fnum < 128; fnum++) { // 7 bits meaningful of F-NUMBER
                    byte value;
                    byte step;
                    int offset_depth = i;
                    int offset_fnum_bit;
                    int bit_tmp;

                    for (step = 0; step < 8; step++) {
                        value = 0;
                        for (bit_tmp = 0; bit_tmp < 7; bit_tmp++) { // 7 bits
                            if ((fnum & (1 << bit_tmp)) != 0) { // only if bit "bit_tmp" is set
                                offset_fnum_bit = bit_tmp * 8;
                                value += lfo_pm_output[offset_fnum_bit + offset_depth][step];
                            }
                        }
                        // 32 steps for LFO PM (sinus)
                        lfoPmTable[(fnum * 32 * 8) + (i * 32) + step + 0] = value;
                        lfoPmTable[(fnum * 32 * 8) + (i * 32) + (step ^ 7) + 8] = value;
                        lfoPmTable[(fnum * 32 * 8) + (i * 32) + step + 16] = -value;
                        lfoPmTable[(fnum * 32 * 8) + (i * 32) + (step ^ 7) + 24] = -value;
                    }
                }
            }

//for (int j = 0; j < 128*8*32; j++) {
// Debug.printf(Level.FINE, "%d,", lfo_pm_table[j]);
//}
        }

        // Ym2612 local section

        Opn.Channel[] cch = new Opn.Channel[6];

        private byte pseudoSt = 0x00;

        private void ym2612_setoptions(byte flags) {
            pseudoSt = (byte) ((flags >> 2) & 0x01);
        }

        // registers
        public byte[] regs = new byte[512];
        /**
         * OPN state
         */
        public Opn opn;
        /**
         * channel state
         */
        public Opn.Channel[] ch = new Opn.Channel[] {
                new Opn.Channel(), new Opn.Channel(), new Opn.Channel(),
                new Opn.Channel(), new Opn.Channel(), new Opn.Channel()
        };

        /**
         * address line A1
         */
        public byte addr_A1;

        // dac output (Ym2612)
        //int   dacen;
        public byte dacen;
        public byte dac_test;
        public int dacOut;
        public byte muteDAC;

        public byte waveOutMode;
        public int waveL;
        public int waveR;

        /**
         * initialize Ym2612 emulator(s)
         */
        public Ym2612(int clock, int rate,
                      Opn.State.TimerHandler timer_handler, Opn.State.IrqHandler irqHandler) {

            this.opn = new Opn();
            this.opn.st.param = this;
            this.opn.type = TYPE_YM2612;
            this.opn.channels = this.ch;
            //this.OPN.ST.device = device;
            this.opn.st.clock = clock;
            this.opn.st.rate = rate;
            // this.OPN.ST.irq = 0;
            // this.OPN.ST.status = 0;
            // Extend handler
            this.opn.st.timerHandler = timer_handler;
            this.opn.st.irqHandler = irqHandler;

            if (pseudoSt != 0)
                this.waveOutMode = 0x01;
            else
                this.waveOutMode = 0x03;
//Debug.printf(hFile, "Clock: %d, Sample Rate: %d\n", clock, rate);
//Debug.printf(hFile, "Sample\tCh 0\tCh 1\tCh 2\tCh 3\tCh 4\tCh 5\n");
        }

        private void resetChannels(int num) {
            for (int c = 0; c < num; c++) {
                ch[c].reset();
            }
        }

        public byte read(int a) {
            switch (a & 3) {
            case 0: // status 0
                return this.opn.st.setStatus();
            case 1:
            case 2:
            case 3:
                //Debug.printf("Ym2612 #%p:A=%d read unmapped area\n",this.OPN.ST.param,a);
                return this.opn.st.setStatus();
            }
            return 0;
        }

        public int write(int a, byte v) {
            int addr;

            //this.OPN.eg_timer = 0;

            v &= 0xff; // adjust to 8 bit bus

            switch (a & 3) {
            case 0: // address port 0
                this.opn.st.address = v;
                this.addr_A1 = 0;
                break;

            case 1: // data port 0
                if (this.addr_A1 != 0)
                    break; // verified on real YM2608

                addr = this.opn.st.address;
                this.regs[addr] = v;
                switch (addr & 0xf0) {
                case 0x20: // 0x20-0x2f Mode
                    switch (addr) {
                    case 0x2a: // DAC data (Ym2612)
                        updateReq();
                        this.dacOut = ((int) v - 0x80) << 6; // level unknown
                        break;
                    case 0x2b: // DAC Sel  (Ym2612)
                        // b7 = dac enable
                        this.dacen = (byte) (v & 0x80);
                        break;
                    case 0x2C: // undocumented: DAC Test Reg
                        // b5 = volume enable
                        this.dac_test = (byte) (v & 0x20);
                        break;
                    default: // OPN section
                        updateReq();
                        // write register
                        this.opn.writeMode(addr, v);
                        break;
                    }
                    break;
                default: // 0x30-0xff OPN section
                    //ym2612_update_req(this.OPN.ST.param);
                    // write register
                    this.opn.writeReg(addr, v);
                    break;
                }
                break;

            case 2: // address port 1
                this.opn.st.address = v;
                this.addr_A1 = 1;
                break;

            case 3: // data port 1
                if (this.addr_A1 != 1)
                    break; // verified on real YM2608

                addr = this.opn.st.address;
                this.regs[addr | 0x100] = v;
                updateReq();
                this.opn.writeReg(addr | 0x100, v);
                break;
            }
            return this.opn.st.irq;
        }

        public void updateOne(int[][] buffer, int length) {
            Opn opn = this.opn;
            Opn.Channel.Op[] outFm = opn.outFm;

            // set bufer
            int[] bufL = buffer[0];
            int[] bufR = buffer[1];

            cch[0] = this.ch[0];
            cch[1] = this.ch[1];
            cch[2] = this.ch[2];
            cch[3] = this.ch[3];
            cch[4] = this.ch[4];
            cch[5] = this.ch[5];

            int dacOut;
            if (this.muteDAC == 0)
                dacOut = this.dacOut;
            else
                dacOut = 0;

            // refresh PG and EG
            cch[0].refreshFcEg(opn.fnMax);
            cch[1].refreshFcEg(opn.fnMax);
            if ((opn.st.mode & 0xc0) != 0) {
                // 3SLOT MODE
                if (cch[2].slots[SLOT1].incr == -1) {
                    cch[2].slots[SLOT1].refreshFcEg(opn.sl3.fc[1], opn.sl3.kCode[1], opn.fnMax);
                    cch[2].slots[SLOT2].refreshFcEg(opn.sl3.fc[2], opn.sl3.kCode[2], opn.fnMax);
                    cch[2].slots[SLOT3].refreshFcEg(opn.sl3.fc[0], opn.sl3.kCode[0], opn.fnMax);
                    cch[2].slots[SLOT4].refreshFcEg(cch[2].fc, cch[2].kCode, opn.fnMax);
                }
            } else cch[2].refreshFcEg(opn.fnMax);
            cch[3].refreshFcEg(opn.fnMax);
            cch[4].refreshFcEg(opn.fnMax);
            cch[5].refreshFcEg(opn.fnMax);
            if (length == 0) {
                cch[0].updateSsgEg();
                cch[1].updateSsgEg();
                cch[2].updateSsgEg();
                cch[3].updateSsgEg();
                cch[4].updateSsgEg();
                cch[5].updateSsgEg();
            }

            // buffering
            for (int i = 0; i < length; i++) {
                // clear outputs
                outFm[0].val = 0;
                outFm[1].val = 0;
                outFm[2].val = 0;
                outFm[3].val = 0;
                outFm[4].val = 0;
                outFm[5].val = 0;

                // update SSG-EG output
                cch[0].updateSsgEg();
                cch[1].updateSsgEg();
                cch[2].updateSsgEg();
                cch[3].updateSsgEg();
                cch[4].updateSsgEg();
                cch[5].updateSsgEg();

                // calculate FM
                if (this.dac_test == 0) {
                    cch[0].calc(opn, cch[0] == this.ch[2]);
                    cch[1].calc(opn, cch[1] == this.ch[2]);
                    cch[2].calc(opn, cch[2] == this.ch[2]);
                    cch[3].calc(opn, cch[3] == this.ch[2]);
                    cch[4].calc(opn, cch[4] == this.ch[2]);
                    if (this.dacen != 0)
                        cch[5].connect4.val += dacOut;
                    else
                        cch[5].calc(opn, cch[5] == this.ch[2]);
                } else {
                    outFm[0].val = outFm[1].val = dacOut;
                    outFm[2].val = outFm[3].val = dacOut;
                    outFm[5].val = dacOut;
                }

                // advance LFO
                opn.advanceLfo();

//Debug.printf(Level.FINE, "opn.eg_timer_add:%d opn.eg_timer:%d opn.eg_timer_overflow:%d", opn.eg_timer_add,opn.eg_timer,opn.eg_timer_overflow));

                // advance envelope generator
                opn.egTimer += opn.egTimerAdd;
                while (opn.egTimer >= opn.egTimerOverflow) {
                    opn.egTimer -= opn.egTimerOverflow;
                    opn.egCnt++;

                    cch[0].advanceEg(opn.egCnt);
                    cch[1].advanceEg(opn.egCnt);
                    cch[2].advanceEg(opn.egCnt);
                    cch[3].advanceEg(opn.egCnt);
                    cch[4].advanceEg(opn.egCnt);
                    cch[5].advanceEg(opn.egCnt);
                }

//Debug.printf(hFile, "%u", FileSample, outFm[0]);
//for (lt = 0; lt < 6; lt ++)
// Debug.printf(hFile, "\t%d", outFm[lt]);
//Debug.printf(hFile, "\n");

                if (outFm[0].val > 8192) outFm[0].val = 8192;
                else if (outFm[0].val < -8192) outFm[0].val = -8192;
                if (outFm[1].val > 8192) outFm[1].val = 8192;
                else if (outFm[1].val < -8192) outFm[1].val = -8192;
                if (outFm[2].val > 8192) outFm[2].val = 8192;
                else if (outFm[2].val < -8192) outFm[2].val = -8192;
                if (outFm[3].val > 8192) outFm[3].val = 8192;
                else if (outFm[3].val < -8192) outFm[3].val = -8192;
                if (outFm[4].val > 8192) outFm[4].val = 8192;
                else if (outFm[4].val < -8192) outFm[4].val = -8192;
                if (outFm[5].val > 8192) outFm[5].val = 8192;
                else if (outFm[5].val < -8192) outFm[5].val = -8192;

                int bitShift = 0;
                // 6-channels mixing
                int lt = (outFm[0].val >> bitShift) & opn.pan[0];
                int rt = (outFm[0].val >> bitShift) & opn.pan[1];
                lt += (outFm[1].val >> bitShift) & opn.pan[2];
                rt += (outFm[1].val >> bitShift) & opn.pan[3];
                lt += (outFm[2].val >> bitShift) & opn.pan[4];
                rt += (outFm[2].val >> bitShift) & opn.pan[5];
                lt += (outFm[3].val >> bitShift) & opn.pan[6];
                rt += (outFm[3].val >> bitShift) & opn.pan[7];
                if (this.dac_test == 0) {
                    lt += (outFm[4].val >> bitShift) & opn.pan[8];
                    rt += (outFm[4].val >> bitShift) & opn.pan[9];
                } else {
                    lt += dacOut;
                    lt += dacOut;
                }
                lt += (outFm[5].val >> bitShift) & opn.pan[10];
                rt += (outFm[5].val >> bitShift) & opn.pan[11];

                //Limit( lt, MAXOUT, MINOUT );
                //Limit( rt, MAXOUT, MINOUT );

                // buffering
                if ((this.waveOutMode & 0x01) != 0)
                    this.waveL = lt;
                if ((this.waveOutMode & 0x02) != 0)
                    this.waveR = rt;
                if ((this.waveOutMode ^ 0x03) != 0)
                    this.waveOutMode ^= 0x03;
                bufL[i] = this.waveL;
                bufR[i] = this.waveR;

//if (this.WaveL != 0)
// Debug.printf(Level.FINE, "L:%d R:%d", this.WaveL, this.WaveR));

                // CSM mode: if CSM Key ON has occured, CSM Key OFF need to be sent
                // only if Timer A does not overflow again (i.e CSM Key ON not set again)
                opn.sl3.keyCsm <<= 1;

                // timer A control
                if (opn.st.tac != 0 && (opn.st.timerHandler == null))
                    if ((opn.st.tac -= (int) (opn.st.freqbase * 4096)) <= 0) {
                        opn.st.timerAOver();
                        // CSM mode total level latch and auto key on
                        if ((opn.st.mode & 0x80) != 0) {
                            cch[2].controllKeyCsm(opn.sl3.keyCsm == 0);
                            opn.sl3.keyCsm = 1;
                        }
                    }

                // CSM Mode Key ON still disabled
                if ((opn.sl3.keyCsm & 2) != 0) {
                    // CSM Mode Key OFF (verified by Nemesis on real hardware)
                    cch[2].keyOffScm(SLOT1);
                    cch[2].keyOffScm(SLOT2);
                    cch[2].keyOffScm(SLOT3);
                    cch[2].keyOffScm(SLOT4);
                    opn.sl3.keyCsm = 0;
                }
            }

            // timer B control
            //INTERNAL_TIMER_B(&opn.ST,length)
        }

        public void postLoad() {
            // DAC data & port
            this.dacOut = ((int) this.regs[0x2a] - 0x80) << 6; // level unknown
            this.dacen = (byte) (this.regs[0x2d] & 0x80);
            // OPN registers
            // DT / MULTI , TL , KS / AR , AMON / DR , SR , SL / RR , SSG-EG
            for (int r = 0x30; r < 0x9e; r++)
                if ((r & 3) != 3) {
                    this.opn.writeReg(r, this.regs[r]);
                    this.opn.writeReg(r | 0x100, this.regs[r | 0x100]);
                }
            // FB / CONNECT , L / R / AMS / PMS
            for (int r = 0xb0; r < 0xb6; r++)
                if ((r & 3) != 3) {
                    this.opn.writeReg(r, this.regs[r]);
                    this.opn.writeReg(r | 0x100, this.regs[r | 0x100]);
                }
            // channels
            //FM_channel_postload(this.CH,6);
        }

        public void reset() {
            Opn opn = this.opn;
            opn.reset();

            Arrays.fill(this.regs, 0, 512, (byte) 0x00);

            this.resetChannels(6);

            // DAC mode clear
            this.dacen = 0;
            this.dac_test = 0;
            this.dacOut = 0;

            if (this.waveOutMode == 0x02)
                this.waveOutMode >>= 1;
        }

        /**
         * limiter
         */
        private static void limit(int val, int max, int min) {
            if (val > max) val = max;
            else if (val < min) val = min;
        }
    }

    /**
     * Generate samples for one of the YM2612s
     */
    public void ym2612_update_one(BaseChip chip, int[][] buffer, int length) {
        Ym2612 f2612 = (Ym2612) chip;
        f2612.updateOne(buffer, length);
    }

    private void postLoad(BaseChip chip) {
        if (chip != null) {
            Ym2612 f2612 = (Ym2612) chip;
            f2612.postLoad();
        }
    }

    /**
     * shut down emulator
     */
    private void ym2612_shutdown(BaseChip chip) {
        Ym2612 f2612 = (Ym2612) chip;
    }

    /**
     * reset one of chip
     */
    public void ym2612_reset_chip(BaseChip chip) {
        Ym2612 f2612 = (Ym2612) chip;
        f2612.reset();
    }

    /**
     * Ym2612 write
     *
     * @param chip number
     * @param a    address
     * @param v    value
     */
    public int ym2612_write(byte chipId, BaseChip chip, int a, byte v) {
        //Debug.printf(Level.FINE, "a:%x v:%x",a,v));

        Ym2612 f2612 = (Ym2612) chip;
        return f2612.write(a, v);
    }

    private byte ym2612_read(BaseChip chip, int a) {
        Ym2612 f2612 = (Ym2612) chip;
        return f2612.read(a);
    }

    private int ym2612_timer_over(byte chipId, BaseChip chip, int c) {
        Ym2612 f2612 = (Ym2612) chip;
        return f2612.timerOver(c);
    }

    public void ym2612_set_mutemask(byte chipId, BaseChip chip, int muteMask) {
        Ym2612 f2612 = (Ym2612) chip;
        f2612.setMuteMask(muteMask);
    }

    private void ym2612_update_req(byte chipId, Ym2612 chip) {
        Ym2612 f2612 = (Ym2612) chip;
        f2612.updateReq();
    }

    static class RunningDevice {
        private void saveState(Ym2612 f2612) {
            this.state_save_register_device_item_array(0, f2612.regs);
            this.saveState(f2612.opn.st);
            this.saveChannel(f2612.ch, 6);
            // 3slots
            this.state_save_register_device_item_array(0, f2612.opn.sl3.fc);
            this.state_save_register_device_item(0, f2612.opn.sl3.fnH);
            this.state_save_register_device_item_array(0, f2612.opn.sl3.kCode);
            // address register1
            this.state_save_register_device_item(0, f2612.addr_A1);
        }

        private void state_save_register_device_item(int v, byte fn_h) {
            throw new UnsupportedOperationException();
        }

        private void state_save_register_device_item_array(int v, byte[] rEGS) {
            throw new UnsupportedOperationException();
        }

        private void state_save_register_device_item(int v, int volume) {
            throw new UnsupportedOperationException();
        }

//        private void state_save_register_device_item(int ch, int fc) {
//            throw new UnsupportedOperationException();
//        }

//        private void state_save_register_device_item_array(int ch, int[] op1_out) {
//            throw new UnsupportedOperationException();
//        }

        private void state_save_register_device_item_array(int v, int[] fc) {
            throw new UnsupportedOperationException();
        }

        /**
         * FM channel save , internal state only
         */
        private void saveChannel(Ym2612.Opn.Channel[] ch_, int numCh) {
            int chPtr = 0;

            for (int ch = 0; ch < numCh; ch++, chPtr++) {
                // channel
                state_save_register_device_item_array(ch, ch_[chPtr].op1Out);
                this.state_save_register_device_item(ch, ch_[chPtr].fc);
                // slots
                for (int slot = 0; slot < 4; slot++) {
                    Ym2612.Opn.Channel.Slot SLOT = ch_[chPtr].slots[slot];
                    this.state_save_register_device_item(ch * 4 + slot, SLOT.phase);
                    this.state_save_register_device_item(ch * 4 + slot, SLOT.state);
                    this.state_save_register_device_item(ch * 4 + slot, SLOT.volume);
                }
            }
        }

        private void saveState(Ym2612.Opn.State st) {
// #if FM_BUSY_FLAG_SUPPORT
// state_save_register_device_item(device, 0, st.busy_expiry_time.seconds );
// state_save_register_device_item(device, 0, st.busy_expiry_time.attoseconds );
// #endif
            this.state_save_register_device_item(0, st.address);
            this.state_save_register_device_item(0, st.irq);
            this.state_save_register_device_item(0, st.irqmask);
            this.state_save_register_device_item(0, st.status);
            this.state_save_register_device_item(0, st.mode);
            this.state_save_register_device_item(0, st.prescalerSel);
            this.state_save_register_device_item(0, st.fn_h);
            this.state_save_register_device_item(0, st.ta);
            this.state_save_register_device_item(0, st.tac);
            this.state_save_register_device_item(0, st.tb);
            this.state_save_register_device_item(0, st.tbc);
        }
        //#endif /* _STATE_H
    }
}
