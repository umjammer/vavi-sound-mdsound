/*
 * File: Fm.c -- software implementation of Yamaha FM Sound generator
 *
 * Copyright Jarek Burczynski (bujar at mame dot net)
 * Copyright Tatsuyuki Satoh , MultiArcadeMachineEmulator development
 *
 * Version 1.4.2 (final beta)
 */

package mdsound.mame;

import java.util.function.BiConsumer;
import java.util.function.Function;

import dotnet4j.util.compat.QuadConsumer;
import dotnet4j.util.compat.TriConsumer;


/*
 * History:
 *
 * 2006-2008 Eke-Eke (Genesis Plus GX), MAME backport by R. Belmont.
 *  - implemented PG overflow, aka "detune bug" (Ariel, Comix Zone, Shaq Fu, Spiderman,...), credits to Nemesis
 *  - fixed SSG-EG support, credits to Nemesis and additional fixes from Alone Coder
 *  - modified EG rates and frequency, tested by Nemesis on real hardware
 *  - implemented LFO phase update for CH3 special mode (Warsynchronized birds, Alladin bug Sound)
 *  - fixed Attack Rate update (Batman & Robin intro)
 *  - fixed attenuation level at the start of Substain (Gynoug explosions)
 *  - fixed EG decay.substain transition to handle special cases, like SL=0 and Decay rate is very slow (Mega Turrican tracks 03,09...)
 *
 * 06-23-2007 Zsolt Vasvari:
 *  - changed the timing not to require the use of floating point calculations
 *
 * 03-08-2003 Jarek Burczynski:
 *  - fixed YM2608 initial values (after the reset)
 *  - fixed flag and irqmask handling (YM2608)
 *  - fixed BUFRDY flag handling (YM2608)
 *
 * 14-06-2003 Jarek Burczynski:
 *  - implemented all the YM2608 status register flags
 *  - implemented support for external memory read/write via YM2608
 *  - implemented support for DeltaT memory limit register in YM2608 emulation
 *
 * 22-05-2003 Jarek Burczynski:
 *  - fixed LFO PM calculations (copy&paste bugfix)
 *
 * 08-05-2003 Jarek Burczynski:
 *  - fixed SSG support
 *
 * 22-04-2003 Jarek Burczynski:
 *  - implemented 100% correct LFO generator (verified on real YM2610 and YM2608)
 *
 * 15-04-2003 Jarek Burczynski:
 *  - added support for YM2608's register 0x110 - status mask
 *
 * 01-12-2002 Jarek Burczynski:
 *  - fixed register addressing in YM2608, YM2610, YM2610B chips. (verified on real YM2608)
 *    The addressing patch used for early Neo-Geo games can be removed now.
 *
 * 26-11-2002 Jarek Burczynski, Nicola Salmoria:
 *  - recreated YM2608 ADPCM ROM using data from real YM2608's output which leads to:
 *  - added emulation of YM2608 drums.
 *  - output of YM2608 is two times lower now - same as YM2610 (verified on real YM2608)
 *
 * 16-08-2002 Jarek Burczynski:
 *  - binary exact Envelope Generator (verified on real YM2203);
 *    identical to YM2151
 *  - corrected 'off by one' error in feedback calculations (when feedback is off)
 *  - corrected connection (algorithm) calculation (verified on real YM2203 and YM2610)
 *
 * 18-12-2001 Jarek Burczynski:
 *  - added SSG-EG support (verified on real YM2203)
 *
 * 12-08-2001 Jarek Burczynski:
 *  - corrected sin_tab and tl_tab data (verified on real chip)
 *  - corrected feedback calculations (verified on real chip)
 *  - corrected phase generator calculations (verified on real chip)
 *  - corrected envelope generator calculations (verified on real chip)
 *  - corrected FM volume level (YM2610 and YM2610B).
 *  - changed YMxxxUpdateOne() functions (YM2203, YM2608, YM2610, YM2610B, Ym2612) :
 *    this was needed to calculate YM2610 FM channels output correctly.
 *    (Each FM channel is calculated as in other chips, but the output of the channel
 *    gets shifted right by one *before* sending to accumulator. That was impossible to do
 *    with previous implementation).
 *
 * 23-07-2001 Jarek Burczynski, Nicola Salmoria:
 *  - corrected YM2610 ADPCM type A algorithm and tables (verified on real chip)
 *
 * 11-06-2001 Jarek Burczynski:
 *  - corrected end of sample bug in ADPCMA_calc_cha().
 *    Real YM2610 checks for equality between current and end addresses (only 20 LSB bits).
 *
 * 08-12-98 hiro-shi:
 * rename AdpcmA . AdpcmB, AdpcmB . AdpcmA
 * move ROM limit check.(CALC_CH? . 2610Write1/2)
 * test program (ADPCMB_TEST)
 * move ADPCM A/B end check.
 * AdpcmB repeat flag(no check)
 * change ADPCM volume rate (8.16) (32.48).
 *
 * 09-12-98 hiro-shi:
 * change ADPCM volume. (8.16, 48.64)
 * replace Ym2610 ch0/3 (YM-2610B)
 * change ADPCM_SHIFT (10.8) missing bank change 0x4000-0xffff.
 * add ADPCM_SHIFT_MASK
 * change ADPCMA_DECODE_MIN/MAX.
 */
public class Fm {

    // comment of hiro-shi(Hiromitsu Shioya)
    // YM2610(B) = OPN-B
    // YM2610  : Psg:3ch FM:4ch ADPCM(18.5KHz):6ch DeltaT ADPCM:1ch
    // YM2610B : Psg:3ch FM:6ch ADPCM(18.5KHz):6ch DeltaT ADPCM:1ch

    // select emulation chips

    public static final int BUILD_YM2203 = 1;
    public static final int BUILD_YM2608 = 1;
    public static final int BUILD_YM2610 = 1;
    public static final int BUILD_YM2610B = 1;
    public static final int BUILD_YM2612 = 1;
    public static final int BUILD_YM3438 = 1;

    /** select bit size of output : 8 or 16 */
    private static final int FM_SAMPLE_BITS = 16;

    /** select timer system internal or external */
    private static final int FM_INTERNAL_TIMER = 1;

    // speedup optimize
    // busy flag enulation , The definition of FM_GET_TIME_NOW() is necessary.
    //#define FM_BUSY_FLAG_SUPPORT 1

    // external SSG(YM2149/AY-3-8910)emulator interface port

    /** used by YM2203,YM2608,and YM2610 */
    private Callbacks ssgCallbacks;

    public static class Callbacks {
        public interface SetClock extends BiConsumer<BaseChip, Integer> {
        }

        public SetClock set_clock;

        public interface Write extends TriConsumer<BaseChip, Integer, Integer> {
        }

        public Write write;

        public interface Read extends Function<BaseChip, Short> {
        }

        public Read read;

        public interface Reset extends Function<BaseChip, Short> {
        }

        public Reset reset;
    }

    // external Callback funstions for realtime update

    // in 2203intf.c

    public interface UpdateRequestCallback extends Runnable {
    }

    // in 2608intf.c

    public UpdateRequestCallback ym2608_update_request;

    private void ym2608_update_req(byte chipId, YM2608 chip) {
        ym2608_update_request.run();
    }

    // in 2610intf.c

    public UpdateRequestCallback ym2610_update_request;

    private void ym2610_update_req(byte chipId, YM2610 chip) {
        ym2610_update_request.run();
    }

    // in 2612intf.c

    public UpdateRequestCallback ym2612_update_request;

    private void ym2612_update_req(byte chipId, Fm2612.Ym2612 chip) {
        ym2612_update_request.run();
    }

    // FM_TIMERHANDLER : Stop or Start timer
    // int n          = chip number
    // int c          = Channel 0=TimerA,1=TimerB
    // int count      = timer count (0=stop)
    // doube stepTime = step time of one count (sec.)

    // FM_IRQHHANDLER : IRQ level changing sense 
    // int n       = chip number 
    // int irq     = IRQ level 0=OFF,1=ON 

    // include external DELTA-T unit (when needed)

    // shared function building option 
    private static final int BUILD_OPN = 1;
    private static final int BUILD_OPN_PRESCALER = 1;

    // globals

    /** SSG support */
    private static final int TYPE_SSG = 0x01;
    /** OPN type LFO and PAN */
    private static final int TYPE_LFOPAN = 0x02;
    /** FM 6CH / 3CH */
    private static final int TYPE_6CH = 0x04;
    /** Ym2612's DAC device */
    private static final int TYPE_DAC = 0x08;
    /** two ADPCM units */
    private static final int TYPE_ADPCM = 0x10;
    /** bogus flag to differentiate 2608 from 2610 */
    private static final int TYPE_2610 = 0x20;

    private static final int TYPE_YM2203 = TYPE_SSG;
    private static final int TYPE_YM2608 = TYPE_SSG | TYPE_LFOPAN | TYPE_6CH | TYPE_ADPCM;
    private static final int TYPE_YM2610 = TYPE_SSG | TYPE_LFOPAN | TYPE_6CH | TYPE_ADPCM | TYPE_2610;

    /** 16.16 fixed point (frequency calculations) */
    private static final int FREQ_SH = 16;
    /** 16.16 fixed point (envelope generator timing) */
    private static final int EG_SH = 16;
    /**  8.24 fixed point (LFO calculations) */
    private static final int LFO_SH = 24;
    /** 16.16 fixed point (timers calculations) */
    private static final int TIMER_SH = 16;

    private static final int FREQ_MASK = (1 << FREQ_SH) - 1;

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

    private static final int SIN_BITS = 10;
    private static final int SIN_LEN = 1 << SIN_BITS;
    private static final int SIN_MASK = SIN_LEN - 1;

    /** 8 bits addressing (real chip) */
    private static final int TL_RES_LEN = 256;

    private static final int FINAL_SH = 0;
    private static final int MAXOUT = 32767;
    private static final int MINOUT = -32768;

    private static final int RATE_STEPS = 8;
    private static final byte[] eg_inc = new byte[] {
                    //cycle:0  1  2  3  4  5  6  7
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


    /** this is YM2151 and Ym2612 phase increment data (in 10.10 fixed point format) */
    private static final byte[] dt_tab = new byte[] {
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
    private static final byte[] opn_fktable = new byte[] {0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 3, 3, 3, 3, 3, 3};


    /**
     * 8 LFO speed parameters
     * each value represents number of samples that one LFO level will last for
     */
    private static final int[] lfo_samples_per_step = new int[] {108, 77, 71, 67, 62, 44, 8, 5};

    /*
     * There are 4 different LFO AM depths available, they are:
     *  0 dB, 1.4 dB, 5.9 dB, 11.8 dB
     *  Here is how it is generated (in EG steps):
     * <pre>
     *  11.8 dB = 0, 2, 4, 6, 8, 10,12,14,16...126,126,124,122,120,118,....4,2,0
     *   5.9 dB = 0, 1, 2, 3, 4, 5, 6, 7, 8....63, 63, 62, 61, 60, 59,.....2,1,0
     *   1.4 dB = 0, 0, 0, 0, 1, 1, 1, 1, 2,...15, 15, 15, 15, 14, 14,.....0,0,0
     * </pre>
     *  (1.4 dB is losing precision as you can see)
     *
     *  It's implemented as generator from 0..126 with step 2 then a shift
     *  right N times, where N is:
     *    8 for 0 dB
     *    3 for 1.4 dB
     *    1 for 5.9 dB
     *    0 for 11.8 dB
     */
    private static final byte[] lfo_ams_depth_shift = new byte[] {8, 3, 1, 0};


    /*
     * There are 8 different LFO PM depths available, they are:
     * 0, 3.4, 6.7, 10, 14, 20, 40, 80 (cents)
     *
     * Modulation level at each depth depends on F-NUMBER bits: 4,5,6,7,8,9,10
     * (bits 8,9,10 = FNUM MSB from OCT/FNUM register)
     *
     * Here we store only first quarter (positive one) of full waveform.
     * Full table (lfo_pm_table) containing all 128 waveforms is build
     * at run (init) time.
     *
     * One value in table below represents 4 (four) basic LFO steps
     * (1 PM step = 4 AM steps).
     *
     *  For example:
     *   at LFO SPEED=0 (which is 108 samples per basic LFO step)
     *   one value from "lfo_pm_output" table lasts for 432 consecutive
     *   samples (4*108=432) and one full LFO waveform cycle lasts for 13824
     *   samples (32*432=13824; 32 because we store only a quarter of whole
     *   waveform in the table below)
     */
    private static final byte[][] lfo_pm_output = new byte[][] { // 7 bits meaningful (of F-NUMBER), 8 LFO output levels per one depth (of 32), 8 LFO depths 
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

    /** all 128 LFO PM waveforms */
    private static int[] lfo_pm_table = new int[128 * 8 * 32]; // 128 combinations of 7 bits meaningful (of F-NUMBER), 8 LFO depths, 32 LFO output levels per one depth 

    // slot number 
    private static final int SLOT1 = 0;
    private static final int SLOT2 = 2;
    private static final int SLOT3 = 1;
    private static final int SLOT4 = 3;

    // bit0 = Right enable , bit1 = Left enable 
    private static final int OUTD_RIGHT = 1;
    private static final int OUTD_LEFT = 2;
    static final int OUTD_CENTER = 3;

    //
    // OPN unit
    //

    /** OPN/A/B common state */
    private static class FM_OPN {

        protected static void resetChannels(Channel[] ch, int num) {
            for (int c = 0; c < num; c++) {
                ch[c].reset();
            }
        }

        public void reset2203(Channel[] ch) {
            // Reset Prescaler
            this.setPreScaler(0, 1);
            // reset SSG section
            this.st.ssg.reset.apply(this.st.param);
            // status clear
            this.st.setIrqMask(0x03);
            this.st.clearBusy();
            this.writeMode(0x27, 0x30); // mode 0 , timer reset

            this.egTimer = 0;
            this.egCnt = 0;

            this.st.resetStatus(0xff);

            this.st.reset();
            resetChannels(ch, 3);
            // reset Operator parameter
            for (int i = 0xb2; i >= 0x30; i--) this.writeReg(i, 0);
            for (int i = 0x26; i >= 0x20; i--) this.writeReg(i, 0);
        }

        public void reset2610(Channel[] ch) {
            // Reset Prescaler 
            this.setPreS((short) (6 * 24), (short) (6 * 24), (short) (4 * 2)); // opn 1/6 , SSG 1/4 
            // reset SSG section 
            this.st.ssg.reset.apply(this.st.param);
            // status clear 
            this.st.setIrqMask(0x03);
            this.st.clearBusy();
            this.writeMode(0x27, 0x30); // mode 0 , timer reset 

            this.egTimer = 0;
            this.egCnt = 0;

            this.st.resetStatus(0xff);

            this.st.reset();
            resetChannels(ch, 6);
            // reset OPerator paramater 
            for (int i = 0xb6; i >= 0xb4; i--) {
                this.writeReg(i, 0xc0);
                this.writeReg(i | 0x100, 0xc0);
            }
            for (int i = 0xb2; i >= 0x30; i--) {
                this.writeReg(i, 0);
                this.writeReg(i | 0x100, 0);
            }
            for (int i = 0x26; i >= 0x20; i--) this.writeReg(i, 0);
        }

        public void reset2608(YM2610 ym2610) {
            // Reset Prescaler 
            this.setPreScaler(0, 2);
            // reset SSG section 
            this.st.ssg.reset.apply(this.st.param);

            // status clear 
            this.st.clearBusy();

            // register 0x29 - default value after reset is:
            // enable only 3 FM channels and enable all the status flags
            this.writeIRQMask(ym2610, 0x1f); // default value for D4-D0 is 1

            // register 0x10, A1=1 - default value is 1 for D4, D3, D2, 0 for the rest 
            this.writeIRQFlag(ym2610, 0x1c); // default: enable timer A and B, disable EOS, BRDY and ZERO

            this.writeMode(0x27, 0x30); // mode 0 , timer reset

            this.egTimer = 0;
            this.egCnt = 0;

            this.st.resetStatus(0xff);

            this.st.reset();
            resetChannels(ym2610.ch, 6);
            // reset OPerator paramater 
            for (int i = 0xb6; i >= 0xb4; i--) {
                this.writeReg(i, 0xc0);
                this.writeReg(i | 0x100, 0xc0);
            }
            for (int i = 0xb2; i >= 0x30; i--) {
                this.writeReg(i, 0);
                this.writeReg(i | 0x100, 0);
            }
            for (int i = 0x26; i >= 0x20; i--) this.writeReg(i, 0);
        }

        /** OPN 3slot struct */
        private static class _3Slot {
            /** fnum3,blk3: calculated */
            public int[] fc = new int[3];
            /** freq3 latch */
            public byte fnH;
            /** key code */
            public byte[] kCode = new byte[3];
            /** current fnum value for this slot (can be different betweeen slots of one channel in 3slot mode) */
            public int[] blockFNum = new int[3];
        }

        private static class Channel {

            /** struct describing a single Operator (SLOT) */
            private static class Slot {

                /*rate  0,    1,    2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15*/
                /*shift 11,  10,  9,  8,  7,  6,  5,  4,  3,  2, 1,  0,  0,  0,  0,  0 */
                /*mask  2047, 1023, 511, 255, 127, 63, 31, 15, 7,  3, 1,  0,  0,  0,  0,  0 */

                private static byte o2(int a) {
                    return (byte) (a * 1);
                }

                /** Envelope Generator counter shifts (32 + 64 rates + 32 RKS) */
                private static final byte[] egRrateShift = new byte[] {
                            // 32 infinite time rates 
                            o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),
                            o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),
                            o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),
                            o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),

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

                /*note that there is no O(17) in this table - it's directly in the code */
                /** Envelope Generator rates (32 + 64 rates + 32 RKS) */
                private static final byte[] egRateSelect = new byte[] {
                            // 32 infinite time rates 
                            o(18), o(18), o(18), o(18), o(18), o(18), o(18), o(18),
                            o(18), o(18), o(18), o(18), o(18), o(18), o(18), o(18),
                            o(18), o(18), o(18), o(18), o(18), o(18), o(18), o(18),
                            o(18), o(18), o(18), o(18), o(18), o(18), o(18), o(18),
                            // rates 00-11 
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
                 * sustain level table (3dB per step)
                 * bit0, bit1, bit2, bit3, bit4, bit5, bit6
                 * 1,    2,    4,    8,    16,   32,   64   (value)
                 * 0.75, 1.5,  3,    6,    12,   24,   48   (dB)
                 */
                private static int[] slTable = new int[16];

                /** 0 - 15: 0, 3, 6, 9,12,15,18,21,24,27,30,33,36,39,42,93 (dB)*/
                private static int sc(int db) {
                    return (int) (db * (4.0 / ENV_STEP));
                }

                static  {
                    for (int i = 0; i < 16; i++) slTable[i] = sc(i == 15 ? 31 : i);
                }

                /** detune: dt_tab[DT] */
                public int[] dt;
                /** key scale rate: 3-KSR */
                public byte KSR;
                /** attack rate */
                public int ar;
                /** decay rate */
                public int d1r;
                /** sustain rate */
                public int d2r;
                /** release rate */
                public int rr;
                /** key scale rate: kcode>>(3-KSR) */
                public byte ksr;
                /** multiple: ML_TABLE[ML] */
                public int mul;

                // Phase Generator

                /** phase counter */
                public int phase;
                /** phase step */
                public int incr;

                // Envelope Generator

                /** phase type */
                public byte state;
                /** total level: TL << 3 */
                public int tl;
                /** envelope counter */
                public int volume;
                /** sustain level:sl_table[SL] */
                public int sl;
                /** current output from EG circuit (without AM from LFO) */
                public int volOut;

                /**  (attack state) */
                public byte egShAr;
                /**  (attack state) */
                public byte egSelAr;
                /**  (decay state) */
                public byte egShD1R;
                /**  (decay state) */
                public byte egSelD1R;
                /**  (sustain state) */
                public byte egShD2R;
                /**  (sustain state) */
                public byte egSelD2R;
                /**  (release state) */
                public byte egShRr;
                /**  (release state) */
                public byte egSelRr;

                /** SSG-EG waveform */
                public byte ssg;
                /** SSG-EG negated output */
                public byte ssgn;

                /** 0=last key was KEY OFF, 1=KEY ON */
                public int key;

                // LFO

                /** AM enable flag */
                public int amMask;

                /** set total level */
                private void set_tl(Channel ch, int v) {
                    this.tl = (v & 0x7f) << (ENV_BITS - 7); // 7bit TL
                }

                /** set detune & multiple */
                private void set_det_mul(State st, Channel ch, int v) {
                    this.mul = (v & 0x0f) != 0 ? ((v & 0x0f) * 2) : 1;
                    this.dt = st.dt_tab[(v >> 4) & 7];
                    ch.slots[SLOT1].incr = -1;
                }

                /** set attack rate & key scale */
                private void set_ar_ksr(byte type, Channel ch, int v) {
                    byte old_KSR = this.KSR;

                    this.ar = (v & 0x1f) != 0 ? (32 + ((v & 0x1f) << 1)) : 0;

                    this.KSR = (byte) (3 - (v >> 6));
                    if (this.KSR != old_KSR) {
                        ch.slots[SLOT1].incr = -1;
                    }

                    // refresh Attack rate 
                    if ((this.ar + this.ksr) < 32 + 62) {
                        this.egShAr = egRrateShift[this.ar + this.ksr];
                        this.egSelAr = egRateSelect[this.ar + this.ksr];
                    } else {
                        this.egShAr = 0;
                        this.egSelAr = (byte) (17 * RATE_STEPS);
                    }
                }

                /** set decay rate */
                private void set_dr(byte type, int v) {
                    this.d1r = (v & 0x1f) != 0 ? (32 + ((v & 0x1f) << 1)) : 0;

                    this.egShD1R = egRrateShift[this.d1r + this.ksr];
                    this.egSelD1R = egRateSelect[this.d1r + this.ksr];
                }

                /** set sustain rate */
                private void set_sr(byte type, int v) {
                    this.d2r = (v & 0x1f) != 0 ? (32 + ((v & 0x1f) << 1)) : 0;

                    this.egShD2R = egRrateShift[this.d2r + this.ksr];
                    this.egSelD2R = egRateSelect[this.d2r + this.ksr];
                }

                /** set release rate */
                private void set_sl_rr(byte type, int v) {
                    this.sl = slTable[v >> 4];

                    this.rr = 34 + ((v & 0x0f) << 2);

                    this.egShRr = egRrateShift[this.rr + this.ksr];
                    this.egSelRr = egRateSelect[this.rr + this.ksr];
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

                /** update phase increment and envelope generator */
                private void refresh_fc_eg(int fc, int kc, int fnMax) {
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
                            this.egShAr = egRrateShift[this.ar + this.ksr];
                            this.egSelAr = egRateSelect[this.ar + this.ksr];
                        } else {
                            this.egShAr = 0;
                            this.egSelAr = (byte) (17 * RATE_STEPS);
                        }

                        this.egShD1R = egRrateShift[this.d1r + this.ksr];
                        this.egShD2R = egRrateShift[this.d2r + this.ksr];
                        this.egShRr = egRrateShift[this.rr + this.ksr];

                        this.egSelD1R = egRateSelect[this.d1r + this.ksr];
                        this.egSelD2R = egRateSelect[this.d2r + this.ksr];
                        this.egSelRr = egRateSelect[this.rr + this.ksr];
                    }
                }

                public void advance_eg(int eg_cnt) {
                    int _out;
                    // reset SSG-EG swap flag 
                    int swapFlag = 0;

                    switch (this.state) {
                    case EG_ATT: // attack phase 
                        if ((eg_cnt & ((1 << this.egShAr) - 1)) == 0) {
                            this.volume += (~this.volume *
                                    eg_inc[this.egSelAr + ((eg_cnt >> this.egShAr) & 7)]
                            ) >> 4;

                            if (this.volume <= MIN_ATT_INDEX) {
                                this.volume = MIN_ATT_INDEX;
                                this.state = EG_DEC;
                            }
                        }
                        break;

                    case EG_DEC: { // decay phase
                        if ((this.ssg & 0x08) != 0) { // SSG EG type envelope selected
                            if ((eg_cnt & ((1 << this.egShD1R) - 1)) == 0) {
                                this.volume += 4 * eg_inc[this.egSelD1R + ((eg_cnt >> this.egShD1R) & 7)];

                                if (this.volume >= this.sl)
                                    this.state = EG_SUS;
                            }
                        } else {
                            if ((eg_cnt & ((1 << this.egShD1R) - 1)) == 0) {
                                this.volume += eg_inc[this.egSelD1R + ((eg_cnt >> this.egShD1R) & 7)];

                                if (this.volume >= this.sl)
                                    this.state = EG_SUS;
                            }
                        }
                    }
                    break;

                    case EG_SUS: // sustain phase
                        if ((this.ssg & 0x08) != 0) { // SSG EG type envelope selected
                            if ((eg_cnt & ((1 << this.egShD2R) - 1)) == 0) {

                                this.volume += 4 * eg_inc[this.egSelD2R + ((eg_cnt >> this.egShD2R) & 7)];

                                if (this.volume >= ENV_QUIET) {
                                    this.volume = MAX_ATT_INDEX;

                                    if ((this.ssg & 0x01) != 0) { // bit 0 = hold
                                        if ((this.ssgn & 1) != 0) { // have we swapped once ???
                                            // yes, so do nothing, just hold current level
                                        } else
                                            swapFlag = (this.ssg & 0x02) | 1; // bit 1 = alternate

                                    } else {
                                        // same as KEY-ON operation

                                        // restart of the Phase Generator should be here
                                        this.phase = 0;

                                        // phase . Attack
                                        this.volume = 511;
                                        this.state = EG_ATT;

                                        swapFlag = this.ssg & 0x02; // bit 1 = alternate 
                                    }
                                }
                            }
                        } else {
                            if ((eg_cnt & ((1 << this.egShD2R) - 1)) == 0) {
                                this.volume += eg_inc[this.egSelD2R + ((eg_cnt >> this.egShD2R) & 7)];

                                if (this.volume >= MAX_ATT_INDEX) {
                                    this.volume = MAX_ATT_INDEX;
                                    // do not change this.state (verified on real chip)
                                }
                            }
                        }
                        break;

                    case EG_REL: // release phase
                        if ((eg_cnt & ((1 << this.egShRr) - 1)) == 0) {
                            // SSG-EG affects Release phase also (Nemesis)
                            this.volume += eg_inc[this.egSelRr + ((eg_cnt >> this.egShRr) & 7)];

                            if (this.volume >= MAX_ATT_INDEX) {
                                this.volume = MAX_ATT_INDEX;
                                this.state = EG_OFF;
                            }
                        }
                        break;
                    }

                    _out = this.volume;

                    // negate output (changes come from alternate bit, init comes from attack bit)
                    if ((this.ssg & 0x08) != 0 && (this.ssgn & 2) != 0 && (this.state > EG_REL))
                        _out ^= MAX_ATT_INDEX;

                    // we need to store the result here because we are going to change ssgn
                    // in next instruction
                    this.volOut = _out + this.tl;

                    // reverse slot inversion flag
                    this.ssgn ^= (byte) swapFlag;
                }

                private int calcVolume(int am) {
                    return this.volOut + (am & this.amMask);
                }

                public void keyOn() {
                    if (this.key == 0) {
                        this.key = 1;
                        this.phase = 0; // restart Phase Generator
                        this.ssgn = (byte) ((this.ssg & 0x04) >> 1);
                        this.state = EG_ATT;
                    }
                }

                public void keyOff() {
                    if (this.key != 0) {
                        this.key = 0;
                        if (this.state > EG_REL)
                            this.state = EG_REL; // phase . Release
                    }
                }
            }

            /** four SLOTs (operators) */
            public Slot[] slots = new Slot[4];

            /** algorithm */
            public byte ALGO;
            /** feedback shift */
            public byte FB;
            /** op1 output for feedback */
            public int[] op1Out = new int[2];

            /** SLOT1 output pointer */
            public int[] connect1;
            /** SLOT3 output pointer */
            public int[] connect3;
            /** SLOT2 output pointer */
            public int[] connect2;
            /** SLOT4 output pointer */
            public int[] connect4;
            /** SLOT1 output pointer */
            public int connect1Ptr;
            /** SLOT3 output pointer */
            public int connect3Ptr;
            /** SLOT2 output pointer */
            public int connect2Ptr;
            /** SLOT4 output pointer */
            public int connect4Ptr;

            /** where to put the delayed sample (MEM) */
            public int[] memConnect;
            /** where to put the delayed sample (MEM) */
            public int memConnectPtr;
            /** delayed sample (MEM) value */
            public int memValue;

            /** channel PMS */
            public int pms;
            /** channel AMS */
            public byte ams;

            /** fnum,blk:adjusted to sample rate */
            public int fc;
            /** key code: */
            public byte kCode;
            /** current blk/fnum value for this slot (can be different betweeen slots of one channel in 3slot mode) */
            public int blockFNum;
            public byte muted;

            private void keyOn(byte _type, int s) {
                this.slots[s].keyOn();
            }

            private void keyOff(int s) {
                this.slots[s].keyOff();
            }

            /** CSM Key Controll */
            private void CSMKeyControll(byte type) {
                // all key on then off (only for operators which were OFF!) 
                if (this.slots[SLOT1].key == 0) {
                    keyOn(type, SLOT1);
                    keyOff(SLOT1);
                }
                if (this.slots[SLOT2].key == 0) {
                    keyOn(type, SLOT2);
                    keyOff(SLOT2);
                }
                if (this.slots[SLOT3].key == 0) {
                    keyOn(type, SLOT3);
                    keyOff(SLOT3);
                }
                if (this.slots[SLOT4].key == 0) {
                    keyOn(type, SLOT4);
                    keyOff(SLOT4);
                }
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

            /** update phase increment counters */
            private void refresh_fc_eg_chan(int fnMax) {
                if (this.slots[SLOT1].incr == -1) {
                    int fc = this.fc;
                    int kc = this.kCode;
                    this.slots[SLOT1].refresh_fc_eg(fc, kc, fnMax);
                    this.slots[SLOT2].refresh_fc_eg(fc, kc, fnMax);
                    this.slots[SLOT3].refresh_fc_eg(fc, kc, fnMax);
                    this.slots[SLOT4].refresh_fc_eg(fc, kc, fnMax);
                }
            }
        }

        public static class State {
            /** this chip parameter */
            public BaseChip param;
            /** master clock  (Hz) */
            public int clock;
            /** sampling rate (Hz) */
            public int rate;
            /** frequency base */
            public double freqBase;
            /** timer prescaler */
            public int timerPrescaler;
            /** address register */
            public byte address;
            /** interrupt level */
            public byte irq;
            /** irq mask */
            public byte irqmask;
            /** status flag */
            public byte status;
            /** mode  CSM / 3SLOT */
            public int mode;
            /** prescaler selector */
            public byte prescaler_sel;
            /** freq latch */
            public byte fn_h;
            /** timer a */
            public int ta;
            /** timer a counter */
            public int tac;
            /** timer b */
            public byte tb;
            /** timer b counter */
            public int tbc;

            // local timetables

            /** DeTune table */
            public int[][] dt_tab = new int[][] {
                    new int[32], new int[32], new int[32], new int[32],
                    new int[32], new int[32], new int[32], new int[32]};

            /** Extention Timer and IRQ handler */
            public interface TimerHandler extends QuadConsumer<Object, Integer, Integer, Integer> {
            }

            public TimerHandler timerHandler;

            public interface IrqHandler extends BiConsumer<BaseChip, Integer> {
            }

            public IrqHandler irqHandler;
            public Callbacks ssg;

            /** initialize time tables */
            private void initTimeTables(byte[] dtTable) {
                //Debug.printf("FM.C: samplerate=%8i chip clock=%8i  freqbase=%f  \n",
                // this.rate, this.clock, this.freqbase );

                // DeTune table 
                for (int d = 0; d <= 3; d++) {
                    for (int i = 0; i <= 31; i++) {
                        double rate = ((double) dtTable[d * 32 + i]) * SIN_LEN * this.freqBase * (1 << FREQ_SH) / ((double) (1 << 20));
                        this.dt_tab[d][i] = (int) rate;
                        this.dt_tab[d + 4][i] = -this.dt_tab[d][i];
                        //Debug.printf("FM.C: DT [%2i %2i] = %8x  \n", d, i, this.dt_tab[d][i] );
                    }
                }
            }

            private void reset() {
                this.mode = 0; // normal mode
                this.ta = 0;
                this.tac = 0;
                this.tb = 0;
                this.tbc = 0;
            }

            /** status set and IRQ handling */
            private void setStatus(int flag) {
                // set status flag 
                this.status |= (byte) flag;
                if (this.irq == 0 && ((this.status & this.irqmask) != 0)) {
                    this.irq = 1;
                    // Callback user interrupt handler (IRQ is OFF to ON) 
                    if (this.irqHandler != null) this.irqHandler.accept(this.param, 1);
                }
            }

            /** status reset and IRQ handling */
            private void resetStatus(int flag) {
                // reset status flag 
                this.status &= (byte) ~flag;
                if ((this.irq != 0) && ((this.status & this.irqmask) == 0)) {
                    this.irq = 0;
                    // Callback user interrupt handler (IRQ is ON to OFF) 
                    if (this.irqHandler != null) this.irqHandler.accept(this.param, 0);
                }
            }

            /** IRQ mask set */
            private void setIrqMask(int flag) {
                this.irqmask = (byte) flag;
                // IRQ handling check 
                setStatus(0);
                resetStatus(0);
            }

            /** OPN Mode Register Write */
            private void setTimers(BaseChip n, int v) {
                // b7 = CSM MODE 
                // b6 = 3 slot mode 
                // b5 = reset b 
                // b4 = reset a 
                // b3 = timer enable b 
                // b2 = timer enable a 
                // b1 = load b 
                // b0 = load a 
                this.mode = v;

                // reset Timer b flag 
                if ((v & 0x20) != 0)
                    resetStatus(0x02);
                // reset Timer a flag 
                if ((v & 0x10) != 0)
                    resetStatus(0x01);
                // load b 
                if ((v & 0x02) != 0) {
                    if (this.tbc == 0) {
                        this.tbc = (256 - this.tb) << 4;
                        // External timer handler 
                        if (this.timerHandler != null) this.timerHandler.accept(n, 1, this.tbc * this.timerPrescaler, this.clock);
                    }
                } else { // stop timer b 
                    if (this.tbc != 0) {
                        this.tbc = 0;
                        if (this.timerHandler != null) this.timerHandler.accept(n, 1, 0, this.clock);
                    }
                }
                // load a 
                if ((v & 0x01) != 0) {
                    if (this.tac == 0) {
                        this.tac = (1024 - this.ta);
                        // External timer handler 
                        if (this.timerHandler != null) this.timerHandler.accept(n, 0, this.tac * this.timerPrescaler, this.clock);
                    }
                } else { // stop timer a 
                    if (this.tac != 0) {
                        this.tac = 0;
                        if (this.timerHandler != null) this.timerHandler.accept(n, 0, 0, this.clock);
                    }
                }
            }

            /** Timer A Overflow */
            private void timerAOver() {
                // set status (if enabled) 
                if ((this.mode & 0x04) != 0) setStatus(0x01);
                // clear or reload the counter 
                this.tac = (1024 - this.ta);
                if (this.timerHandler != null) this.timerHandler.accept(this.param, 0, this.tac * this.timerPrescaler, this.clock);
            }

            /** Timer B Overflow */
            private void timerBOver() {
                // set status (if enabled) 
                if ((this.mode & 0x08) != 0) setStatus(0x02);
                // clear or reload the counter 
                this.tbc = (256 - this.tb) << 4;
                if (this.timerHandler != null) this.timerHandler.accept(this.param, 1, this.tbc * this.timerPrescaler, this.clock);
            }

            /** calculate timer A */
            private void internalTimerA(Channel csmCh, byte type) {
                if (this.tac != 0 && (this.timerHandler == null))
                    if ((this.tac -= (int) (this.freqBase * 4096)) <= 0) {
                        this.timerAOver();
                        // CSM mode total level latch and auto key on 
                        if ((this.mode & 0x80) != 0)
                            csmCh.CSMKeyControll(type);
                    }
            }

            /** calculate timer B */
            private void internalTimerB(int step) {
                if (this.tbc != 0 && this.timerHandler == null)
                    if ((this.tbc -= (int) (this.freqBase * 4096 * step)) <= 0)
                        timerBOver();
            }

            private byte getStatus() {
                return this.status;
            }

            private void setBusy(int bClock) {
            }

            private void clearBusy() {
            }
        }

        /** chip type */
        public byte type;
        /** general state */
        public State st;
        /** 3 slot mode state */
        public _3Slot sl3;
        /** pointer of CH */
        public Channel[] pCh;
        /** Fm channels output masks (0xffffffff = enable) */
        public int[] pan = new int[6 * 2];

        /** Global envelope generator counter */
        public int egCnt;
        /** Global envelope generator counter works at frequency = chipclock/64/3 */
        public int egTimer;
        /** step of eg_timer */
        public int egTimerAdd;
        /** envelope generator timer overlfows every 3 samples (on real chip) */
        public int egTimerOverflow;

        // there are 2048 FNUMs that can be generated using FNUM/BLK registers
        // but LFO works with one more bit of a precision so we really need 4096 elements 

        /** fnumber.increment counter */
        public int[] fnTable = new int[4096];
        /** maximal phase increment (used for phase overflow) */
        public int fnMax;

        // LFO

        /** runtime LFO calculations helper */
        public int lfoAm;
        /** runtime LFO calculations helper */
        public int lfoPm;

        public int lfoCnt;
        public int lfoInc;

        /** LFO FREQ table */
        public int[] lfo_freq = new int[8];

        /** Phase Modulation input for operators 2,3,4 */
        public int m2, c1, c2;
        /** one sample delay memory */
        public int mem;

        /** outputs of working channels */
        public int[] outFm = new int[8];

        /** channel output NONE,LEFT,RIGHT or CENTER for YM2608/YM2610 ADPCM */
        public int[] outAdpcm = new int[4];
        /** channel output NONE,LEFT,RIGHT or CENTER for YM2608/YM2610 DeltaT*/
        public int[] outDelta = new int[4];

        /** register number to channel number , slot offset */
        private byte channel(int N) {
            return (byte) (N & 3);
        }

        private int slot(int N) {
            return ((N >> 2) & 3);
        }

        /** prescaler set (and make time tables) */
        private void setPreS(short pres, short timerPrescaler, short ssgPreS) {
            // frequency base 
            this.st.freqBase = (this.st.rate != 0) ? ((double) this.st.clock / this.st.rate) / pres : 0;

            this.egTimerAdd = (int) ((1 << EG_SH) * this.st.freqBase);
            this.egTimerOverflow = (3) * (1 << EG_SH);


            // Timer base time 
            this.st.timerPrescaler = timerPrescaler;

            // SSG part  prescaler set 
            if (ssgPreS != 0) this.st.ssg.set_clock.accept(this.st.param, this.st.clock * 2 / ssgPreS);

            // make time tables 
            this.st.initTimeTables(dt_tab);

            // there are 2048 FNUMs that can be generated using FNUM/BLK registers
            // but LFO works with one more bit of a precision, so we really need 4096 elements
            // calculate fnumber . increment counter table 
            for (int i = 0; i < 4096; i++) {
                // freq table for octave 7 
                // OPN phase increment counter = 20bit 
                this.fnTable[i] = (int) ((double) i * 32 * this.st.freqBase * (1 << (FREQ_SH - 10))); // -10 because chip works with 10.10 fixed point, while we use 16.16 
//Debug.printf("FM.C: fn_table[%4i] = %08x (dec=%8i)\n",
// i, this.fn_table[i]>>6,this.fn_table[i]>>6 );
            }

            // maximal frequency is required for Phase overflow calculation, register size is 17 bits (Nemesis) 
            this.fnMax = (int) ((double) 0x20000 * this.st.freqBase * (1 << (FREQ_SH - 10)));

            // LFO freq. table 
            for (int i = 0; i < 8; i++) {
                // Amplitude modulation: 64 output levels (triangle waveform); 1 level lasts for one of "lfo_samples_per_step" samples 
                // Phase modulation: one entry from lfo_pm_output lasts for one of 4 * "lfo_samples_per_step" samples 
                this.lfo_freq[i] = (int) ((1.0 / lfo_samples_per_step[i]) * (1 << LFO_SH) * this.st.freqBase);
//Debug.printf("FM.C: lfo_freq[%i] = %08x (dec=%8i)\n",
// i, this.lfo_freq[i],this.lfo_freq[i] );
            }
        }

        /** write a OPN mode register 0x20-0x2f */
        private void writeMode(int r, int v) {
            switch (r) {
            case 0x21: // Test
                break;
            case 0x22: // LFO FREQ (YM2608/YM2610/YM2610B/Ym2612)
                if ((this.type & TYPE_LFOPAN) != 0) {
                    if ((v & 0x08) != 0) { // LFO enabled ?
                        this.lfoInc = this.lfo_freq[v & 7];
                    } else {
                        this.lfoInc = 0;
                    }
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
                this.st.setTimers(this.st.param, v);
                break;
            case 0x28: // key on / off
                byte c = (byte) (v & 0x03);
                if (c == 3) break;
                if ((v & 0x04) != 0 && (this.type & TYPE_6CH) != 0) c += 3;
                Channel ch = this.pCh[c];
                if ((v & 0x10) != 0) ch.keyOn(this.type, SLOT1);
                else ch.keyOff(SLOT1);
                if ((v & 0x20) != 0) ch.keyOn(this.type, SLOT2);
                else ch.keyOff(SLOT2);
                if ((v & 0x40) != 0) ch.keyOn(this.type, SLOT3);
                else ch.keyOff(SLOT3);
                if ((v & 0x80) != 0) ch.keyOn(this.type, SLOT4);
                else ch.keyOff(SLOT4);
                break;
            }
        }

        /** write a OPN register (0x30-0xff) */
        private void writeReg(int r, int v) {
            byte c = channel(r);

            if (c == 3) return; // 0xX3,0xX7,0xXB,0xXF 

            if (r >= 0x100) c += 3;

            Channel ch = this.pCh[c];

            Channel.Slot slot = ch.slots[slot(r)];

            switch (r & 0xf0) {
            case 0x30: // DET , MUL 
                slot.set_det_mul(this.st, ch, v);
                break;

            case 0x40: // TL 
                slot.set_tl(ch, v);
                break;

            case 0x50: // KS, AR 
                slot.set_ar_ksr(this.type, ch, v);
                break;

            case 0x60: // bit7 = AM ENABLE, DR 
                slot.set_dr(this.type, v);

                if ((this.type & TYPE_LFOPAN) != 0)  { // YM2608/2610/2610B/2612
                    slot.amMask = (v & 0x80) != 0 ? ~(int) 0 : 0;
                }
                break;

            case 0x70: // SR 
                slot.set_sr(this.type, v);
                break;

            case 0x80: // SL, RR 
                slot.set_sl_rr(this.type, v);
                break;

            case 0x90: // SSG-EG 
                slot.ssg = (byte) (v & 0x0f);
                slot.ssgn = (byte) ((v & 0x04) >> 1); // bit 1 in ssgn = attack 

            /*
             * SSG-EG envelope shapes :
             * <pre>
             *  E AtAlH
             *  1 0 0 0  \\\\
             *
             *  1 0 0 1  \___
             *
             *  1 0 1 0  \/\/
             *      ___
             *  1 0 1 1  \
             *
             *  1 1 0 0  ////
             *      ___
             *  1 1 0 1  /
             *
             *  1 1 1 0  /\/\
             *
             *  1 1 1 1  /___
             *
             *
             *  E = SSG-EG enable
             </pre>

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
                case 0: { // 0xa0-0xa2 : FNUM1
                    int fn = (((this.st.fn_h) & 7) << 8) + v;
                    byte blk = (byte) (this.st.fn_h >> 3);
                    // keyscale code 
                    ch.kCode = (byte) ((blk << 2) | opn_fktable[fn >> 7]);
                    // phase increment counter 
                    ch.fc = this.fnTable[fn * 2] >> (7 - blk);

                    // store fnum in clear form for LFO PM calculations 
                    ch.blockFNum = (int) (((long) blk << 11) | fn);

                    ch.slots[SLOT1].incr = -1;
                }
                break;
                case 1: // 0xa4-0xa6 : FNUM2,BLK 
                    this.st.fn_h = (byte) (v & 0x3f);
                    break;
                case 2: // 0xa8-0xaa : 3CH FNUM1 
                    if (r < 0x100) {
                        int fn = ((this.sl3.fnH & 7) << 8) + v;
                        byte blk = (byte) (this.sl3.fnH >> 3);
                        // keyscale code 
                        this.sl3.kCode[c] = (byte) ((blk << 2) | opn_fktable[fn >> 7]);
                        // phase increment counter 
                        this.sl3.fc[c] = this.fnTable[fn * 2] >> (7 - blk);
                        this.sl3.blockFNum[c] = (blk << 11) | fn;
                        (this.pCh)[2].slots[SLOT1].incr = -1;
                    }
                    break;
                case 3: // 0xac-0xae : 3CH FNUM2,BLK 
                    if (r < 0x100)
                        this.sl3.fnH = (byte) (v & 0x3f);
                    break;
                }
                break;

            case 0xb0:
                switch (slot(r)) {
                case 0: { // 0xb0-0xb2 : FB,ALGO
                    int feedback = (v >> 3) & 7;
                    ch.ALGO = (byte) (v & 7);
                    ch.FB = (byte) (feedback != 0 ? (feedback + 6) : 0);
                    setup_connection(ch, c);
                }
                break;
                case 1: // 0xb4-0xb6 : L , R , AMS , PMS (Ym2612/YM2610B/YM2610/YM2608)
                    if ((this.type & TYPE_LFOPAN) != 0) {
                        // b0-2 PMS 
                        ch.pms = (v & 7) * 32; // ch.pms = PM depth * 32 (index in lfo_pm_table) 

                        // b4-5 AMS 
                        ch.ams = lfo_ams_depth_shift[(v >> 4) & 0x03];

                        // PAN :  b7 = L, b6 = R 
                        this.pan[c * 2] = (v & 0x80) != 0 ? ~(int) 0 : 0;
                        this.pan[c * 2 + 1] = (v & 0x40) != 0 ? ~(int) 0 : 0;

                    }
                    break;
                }
                break;
            }
        }

        /**
         * prescaler circuit (best guess to verified chip behaviour)
         * <pre>
         *                      +--------------+  +-sel2-+
         *                      |              +--|in20  |
         *                +---+ |  +-sel1-+       |      |
         *    MPcm-CLK -+-|1/2|-+--|in10  | +---+ |   out|--INT_CLOCK
         *              | +---+    |   out|-|1/3|-|in21  |
         *              +----------|in11  | +---+ +------+
         *                         +------+
         *
         *       reg.2d : sel2 = in21 (select sel2)
         *       reg.2e : sel1 = in11 (select sel1)
         *       reg.2f : sel1 = in10 , sel2 = in20 (clear selector)
         *       reset  : sel1 = in11 , sel2 = in21 (clear both)
         * </pre>
         */
        private void setPreScaler(int addr, int preDivider) {
            final int[] opnPreS = new int[] {2 * 12, 2 * 12, 6 * 12, 3 * 12};
            final int[] ssgPreS = new int[] {1, 1, 4, 2};
            int sel;

            switch (addr) {
            case 0: // when reset 
                this.st.prescaler_sel = 2;
                break;
            case 1: // when postload 
                break;
            case 0x2d: // divider sel : select 1/1 for 1/3line 
                this.st.prescaler_sel |= 0x02;
                break;
            case 0x2e: // divider sel , select 1/3line for output 
                this.st.prescaler_sel |= 0x01;
                break;
            case 0x2f: // divider sel , clear both selector to 1/2,1/2 
                this.st.prescaler_sel = 0;
                break;
            }
            sel = this.st.prescaler_sel & 3;
            // update prescaler 
            setPreS((short) (opnPreS[sel] * preDivider),
                    (short) (opnPreS[sel] * preDivider),
                    (short) (ssgPreS[sel] * preDivider));
        }

        /** set algorithm connection */
        private void setup_connection(Channel CH, int ch) {
            int carrier = this.outFm[ch];

            int[] om1 = CH.connect1;
            int om1Ptr = CH.connect1Ptr;
            int[] om2 = CH.connect3;
            int om2Ptr = CH.connect3Ptr;
            int[] oc1 = CH.connect2;
            int oc1Ptr = CH.connect2Ptr;

            int[] memc = CH.memConnect;
            int memcPtr = CH.memConnectPtr;

            switch (CH.ALGO) {
            case 0:
                // M1---C1---MEM---M2---C2---OUT 
                om1[om1Ptr] = this.c1;
                oc1[oc1Ptr] = this.mem;
                om2[om2Ptr] = this.c2;
                memc[memcPtr] = this.m2;
                break;
            case 1:
                // M1------+-MEM---M2---C2---OUT 
                //      C1-+ 
                om1[om1Ptr] = this.mem;
                oc1[oc1Ptr] = this.mem;
                om2[om2Ptr] = this.c2;
                memc[memcPtr] = this.m2;
                break;
            case 2:
                // M1-----------------+-C2---OUT 
                //      C1---MEM---M2-+ 
                om1[om1Ptr] = this.c2;
                oc1[oc1Ptr] = this.mem;
                om2[om2Ptr] = this.c2;
                memc[memcPtr] = this.m2;
                break;
            case 3:
                // M1---C1---MEM------+-C2---OUT 
                //                 M2-+ 
                om1[om1Ptr] = this.c1;
                oc1[oc1Ptr] = this.mem;
                om2[om2Ptr] = this.c2;
                memc[memcPtr] = this.c2;
                break;
            case 4:
                // M1---C1-+-OUT 
                // M2---C2-+ 
                // MEM: not used 
                om1[om1Ptr] = this.c1;
                oc1[oc1Ptr] = carrier;
                om2[om2Ptr] = this.c2;
                memc[memcPtr] = this.mem; // store it anywhere where it will not be used
                break;
            case 5:
                //    +----C1----+ 
                // M1-+-MEM---M2-+-OUT 
                //    +----C2----+ 
                om1[om1Ptr] = 0; // special mark
                oc1[oc1Ptr] = carrier;
                om2[om2Ptr] = carrier;
                memc[memcPtr] = this.m2;
                break;
            case 6:
                // M1---C1-+ 
                //      M2-+-OUT 
                //      C2-+ 
                // MEM: not used 
                om1[om1Ptr] = this.c1;
                oc1[oc1Ptr] = carrier;
                om2[om2Ptr] = carrier;
                memc[memcPtr] = this.mem; // store it anywhere where it will not be used
                break;
            case 7:
                // M1-+ 
                // C1-+-OUT 
                // M2-+ 
                // C2-+ 
                // MEM: not used*/
                om1[om1Ptr] = carrier;
                oc1[oc1Ptr] = carrier;
                om2[om2Ptr] = carrier;
                memc[memcPtr] = this.mem; // store it anywhere where it will not be used
                break;
            }

            CH.connect4[CH.connect4Ptr] = carrier;
        }

        /** advance LFO to next sample */
        private void advance_lfo() {
            if (this.lfoInc != 0) { // LFO enabled ?
                this.lfoCnt += this.lfoInc;

                byte pos = (byte) ((this.lfoCnt >> LFO_SH) & 127);

                // update AM when LFO output changes 

                // actually I can't optimize is this way without rewriting chan_calc()
                // to use chip.lfo_am instead of Global lfo_am
                {
                    // triangle 
                    // AM: 0 to 126 step +2, 126 to 0 step -2 
                    if (pos < 64)
                        this.lfoAm = (pos & 63) * 2;
                    else
                        this.lfoAm = 126 - ((pos & 63) * 2);
                }

                // PM works with 4 times slower clock 
                pos >>= 2;
                // update PM when LFO output changes 
                /*if (prev_pos != pos)*/ // can't use Global lfo_pm for this optimization, must be chip.lfo_pm instead*/
                {
                    this.lfoPm = pos;
                }

            } else {
                this.lfoAm = 0;
                this.lfoPm = 0;
            }
        }

        /** */
        private void advance_eg_channel(Channel.Slot[] slots) {
            int slotPtr = 0;
            int i = 4; // four operators per channel
            do {
                Channel.Slot slot = slots[slotPtr];
                slot.advance_eg(this.egCnt);
                slotPtr++;
                i--;
            } while (i != 0);
        }

        private void update_phase_lfo_slot(Channel.Slot slot, int pms, int blockFnum) {
            int fnum_lfo = ((blockFnum & 0x7f0) >> 4) * 32 * 8;
            int lfo_fn_table_index_offset = lfo_pm_table[fnum_lfo + pms + this.lfoPm];

            if (lfo_fn_table_index_offset != 0) { // LFO phase modulation active 
                byte blk;
                int fn;
                int kc, fc;

                blockFnum = blockFnum * 2 + lfo_fn_table_index_offset;

                blk = (byte) ((blockFnum & 0x7000) >> 12);
                fn = blockFnum & 0xfff;

                // keyscale code 
                kc = (blk << 2) | opn_fktable[fn >> 8];

                // phase increment counter 
                fc = (this.fnTable[fn] >> (7 - blk)) + slot.dt[kc];

                // detects frequency overflow (credits to Nemesis) 
                if (fc < 0) fc += this.fnMax;

                // update phase 
                slot.phase += (fc * slot.mul) >> 1;
            } else { // LFO phase modulation  = zero 
                slot.phase += slot.incr;
            }
        }

        private void update_phase_lfo_channel(Channel ch) {
            int block_fnum = ch.blockFNum;

            int fnum_lfo = ((block_fnum & 0x7f0) >> 4) * 32 * 8;
            int lfo_fn_table_index_offset = lfo_pm_table[fnum_lfo + ch.pms + this.lfoPm];

            if (lfo_fn_table_index_offset != 0) { // LFO phase modulation active 
                byte blk;
                int fn;
                int kc, fc, finc;

                block_fnum = block_fnum * 2 + lfo_fn_table_index_offset;

                blk = (byte) ((block_fnum & 0x7000) >> 12);
                fn = block_fnum & 0xfff;

                // keyscale code 
                kc = (blk << 2) | opn_fktable[fn >> 8];

                // phase increment counter 
                fc = this.fnTable[fn] >> (7 - blk);

                // detects frequency overflow (credits to Nemesis) 
                finc = fc + ch.slots[SLOT1].dt[kc];

                if (finc < 0) finc += this.fnMax;
                ch.slots[SLOT1].phase += (finc * ch.slots[SLOT1].mul) >> 1;

                finc = fc + ch.slots[SLOT2].dt[kc];
                if (finc < 0) finc += this.fnMax;
                ch.slots[SLOT2].phase += (finc * ch.slots[SLOT2].mul) >> 1;

                finc = fc + ch.slots[SLOT3].dt[kc];
                if (finc < 0) finc += this.fnMax;
                ch.slots[SLOT3].phase += (finc * ch.slots[SLOT3].mul) >> 1;

                finc = fc + ch.slots[SLOT4].dt[kc];
                if (finc < 0) finc += this.fnMax;
                ch.slots[SLOT4].phase += (finc * ch.slots[SLOT4].mul) >> 1;
            } else  { // LFO phase modulation  = zero
                ch.slots[SLOT1].phase += ch.slots[SLOT1].incr;
                ch.slots[SLOT2].phase += ch.slots[SLOT2].incr;
                ch.slots[SLOT3].phase += ch.slots[SLOT3].incr;
                ch.slots[SLOT4].phase += ch.slots[SLOT4].incr;
            }
        }

        private void chan_calc(Channel ch, int chNum) {
            int egOut;

            int am = this.lfoAm >> ch.ams;

            if (ch.muted != 0)
                return;

            this.m2 = this.c1 = this.c2 = this.mem = 0;

            ch.memConnect[ch.memConnectPtr] = ch.memValue; // restore delayed sample (MEM) value to m2 or c2

            egOut = ch.slots[SLOT1].calcVolume(am);

            int _out = ch.op1Out[0] + ch.op1Out[1];
            ch.op1Out[0] = ch.op1Out[1];

            if (ch.connect1[ch.connect1Ptr] == 0) {
                // algorithm 5
                this.mem = this.c1 = this.c2 = ch.op1Out[0];
            } else {
                // other algorithms
                ch.connect1[ch.connect1Ptr] += ch.op1Out[0];
            }

            ch.op1Out[1] = 0;
            if (egOut < ENV_QUIET) { // SLOT 1
                if (ch.FB == 0)
                    _out = 0;

                ch.op1Out[1] = op_calc1(ch.slots[SLOT1].phase, egOut, (short) (_out << ch.FB));
            }

            egOut = ch.slots[SLOT3].calcVolume(am);
            if (egOut < ENV_QUIET) // SLOT 3
                ch.connect3[ch.connect3Ptr] += op_calc(ch.slots[SLOT3].phase, egOut, (short) this.m2);

            egOut = ch.slots[SLOT2].calcVolume(am);
            if (egOut < ENV_QUIET) // SLOT 2
                ch.connect2[ch.connect2Ptr] += op_calc(ch.slots[SLOT2].phase, egOut, (short) this.c1);

            egOut = ch.slots[SLOT4].calcVolume(am);
            if (egOut < ENV_QUIET) // SLOT 4
                ch.connect4[ch.connect4Ptr] += op_calc(ch.slots[SLOT4].phase, egOut, (short) this.c2);

            // store current MEM 
            ch.memValue = this.mem;

            // update phase counters AFTER output calculations 
            if (ch.pms != 0) {
                // add support for 3 slot mode 
                if ((this.st.mode & 0xC0) != 0 && (chNum == 2)) {
                    update_phase_lfo_slot(ch.slots[SLOT1], ch.pms, this.sl3.blockFNum[1]);
                    update_phase_lfo_slot(ch.slots[SLOT2], ch.pms, this.sl3.blockFNum[2]);
                    update_phase_lfo_slot(ch.slots[SLOT3], ch.pms, this.sl3.blockFNum[0]);
                    update_phase_lfo_slot(ch.slots[SLOT4], ch.pms, ch.blockFNum);
                } else update_phase_lfo_channel(ch);
            } else { // no LFO phase modulation
                ch.slots[SLOT1].phase += ch.slots[SLOT1].incr;
                ch.slots[SLOT2].phase += ch.slots[SLOT2].incr;
                ch.slots[SLOT3].phase += ch.slots[SLOT3].incr;
                ch.slots[SLOT4].phase += ch.slots[SLOT4].incr;
            }
        }

        /** sin waveform table in 'decibel' scale */
        private static int[] sin_tab = new int[SIN_LEN];
        /**
         *
         *  TL_TAB_LEN is calculated as:
         *   13 - sinus amplitude bits     (Y axis)
         *   2  - sinus sign bit           (Y axis)
         *   TL_RES_LEN - sinus resolution (X axis)
         */
        private static final int TL_TAB_LEN = 13 * 2 * TL_RES_LEN;

        private static short[] tl_tab = new short[TL_TAB_LEN];

        protected static final int ENV_QUIET = TL_TAB_LEN >> 3;

        /** initialize generic tables */
        static {
            for (short x = 0; x < TL_RES_LEN; x++) {
                double m = (1 << 16) / Math.pow(2, (x + 1) * (ENV_STEP / 4.0) / 8.0);
                m = Math.floor(m);

                // we never reach (1<<16) here due to the (x+1) 
                // result fits within 16 bits at maximum 

                short n = (short) m; // 16 bits here 
                n >>= 4; // 12 bits here 
                if ((n & 1) != 0) // round to nearest 
                    n = (short) ((n >> 1) + 1);
                else
                    n = (short) (n >> 1);
                // 11 bits here (rounded) 
                n <<= 2; // 13 bits here (as in real chip) 
                tl_tab[x * 2 + 0] = n;
                tl_tab[x * 2 + 1] = (short) -tl_tab[x * 2 + 0];

                for (short i = 1; i < 13; i++) {
                    tl_tab[x * 2 + 0 + i * 2 * TL_RES_LEN] = (short) (tl_tab[x * 2 + 0] >> i);
                    tl_tab[x * 2 + 1 + i * 2 * TL_RES_LEN] = (short) (-tl_tab[x * 2 + 0 + i * 2 * TL_RES_LEN]);
                }
                //#if 0
                //           Debug.printf("tl %04i", x);
                //           for (i=0; i<13; i++)
                //            Debug.printf(", [%02i] %4x", i*2, tl_tab[ x*2 /*+1*/ + i*2*TL_RES_LEN ]);
                //           Debug.printf("\n");
                //#endif
            }
            /*Debug.printf("FM.C: TL_TAB_LEN = %i elements (%i bytes)\n",TL_TAB_LEN, (int)sizeof(tl_tab));*/

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

                sin_tab[i] = n * 2 + (m >= 0.0 ? 0 : 1);
//Debug.printf("FM.C: sin [%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[i],tl_tab[sin_tab[i]]);
            }

//Debug.printf("FM.C: ENV_QUIET= %08x\n",ENV_QUIET );

            // build LFO PM modulation table 
            for (short i = 0; i < 8; i++) { // 8 PM depths
                for (byte fnum = 0; fnum < 128; fnum++) { // 7 bits meaningful of F-NUMBER
                    int offsetDepth = i;

                    for (byte step = 0; step < 8; step++) {
                        byte value = 0;
                        for (int bitTmp = 0; bitTmp < 7; bitTmp++) { // 7 bits
                            if ((fnum & (1 << bitTmp)) != 0) { // only if bit "bitTmp" is set
                                int offsetFnumBit = bitTmp * 8;
                                value += lfo_pm_output[offsetFnumBit + offsetDepth][step];
                            }
                        }
                        lfo_pm_table[(fnum * 32 * 8) + (i * 32) + step + 0] = value;
                        lfo_pm_table[(fnum * 32 * 8) + (i * 32) + (step ^ 7) + 8] = value;
                        lfo_pm_table[(fnum * 32 * 8) + (i * 32) + step + 16] = -value;
                        lfo_pm_table[(fnum * 32 * 8) + (i * 32) + (step ^ 7) + 24] = -value;
                    }
//Debug.printf("LFO depth=%1x FNUM=%04x (<<4=%4x): ", i, fnum, fnum<<4);
//for (step=0; step<16; step++) // dump only positive part of waveforms 
// Debug.printf("%02x ", lfo_pm_table[(fnum*32*8) + (i*32) + step] );
//Debug.printf("\n");
                }
            }
        }

        static int op_calc(int phase, int env, short pm) {
            int p = (env << 3) + sin_tab[(((short) ((phase & ~FREQ_MASK) + (pm << 15))) >> FREQ_SH) & SIN_MASK];

            if (p >= TL_TAB_LEN)
                return 0;
            return tl_tab[p];
        }

        static short op_calc1(int phase, int env, short pm) {
            int p = (env << 3) + sin_tab[(((short) ((phase & ~FREQ_MASK) + pm)) >> FREQ_SH) & SIN_MASK];

            if (p >= TL_TAB_LEN)
                return 0;
            return tl_tab[p];
        }

        /** flag enable control 0x110 */
        private void writeIRQFlag(YM2610 ym2610, int v) {
            if ((v & 0x80) != 0) { // Reset IRQ flag 
                this.st.resetStatus(0xf7); // don't touch BUFRDY flag otherwise we'd have to call ymdeltat module to set the flag back 
            } else { // Set status flag mask 
                ym2610.flagmask = (byte) (~(v & 0x1f));
                this.st.setIrqMask(ym2610.irqmask & ym2610.flagmask);
            }
        }

        /** compatible mode & IRQ enable control 0x29 */
        private void writeIRQMask(YM2610 ym2610, int v) {
            // SCH,xx,xxx,EN_ZERO,EN_BRDY,EN_EOS,EN_TB,EN_TA 

            // extend 3ch. enable/disable 
            if ((v & 0x80) != 0)
                this.type |= TYPE_6CH; // OPNA mode - 6 FM channels 
            else
                this.type &= (byte) ~TYPE_6CH; // OPN mode - 3 FM channels 

            // IRQ MASK store and set 
            ym2610.irqmask = (byte) (v & 0x1f);
            this.st.setIrqMask((ym2610.irqmask & ym2610.flagmask));
        }
    }

    // internal timer mode , update timer

    public static class DeviceConfig {

        /** FM channel save , internal state only */
        private void saveAdpcmaState(YM2610.AdpcmA[] adpcm) {

            for (int ch = 0; ch < 6; ch++) {
                state_save_register_device_item(ch, adpcm[ch].flag);
                state_save_register_device_item(ch, adpcm[ch].nowData);
                state_save_register_device_item(ch, adpcm[ch].nowAddr);
                state_save_register_device_item(ch, adpcm[ch].nowStep);
                state_save_register_device_item(ch, adpcm[ch].adpcmAcc);
                state_save_register_device_item(ch, adpcm[ch].adpcmStep);
                state_save_register_device_item(ch, adpcm[ch].adpcmOut);
            }
        }

        /** FM channel save , internal state only */
        private void saveChannelState(FM_OPN.Channel[] CH, int num_ch) {

            int slot;
            int chPtr = 0;

            for (int ch = 0; ch < num_ch; ch++, chPtr++) {
                // channel 
                state_save_register_device_item_array(ch, CH[chPtr].op1Out);
                state_save_register_device_item(ch, CH[chPtr].fc);
                // slots 
                for (slot = 0; slot < 4; slot++) {
                    FM_OPN.Channel.Slot SLOT = CH[chPtr].slots[slot];
                    state_save_register_device_item(ch * 4 + slot, SLOT.phase);
                    state_save_register_device_item(ch * 4 + slot, SLOT.state);
                    state_save_register_device_item(ch * 4 + slot, SLOT.volume);
                }
            }
        }

        private void saveStState(FM_OPN.State ST) {
// #if FM_BUSY_FLAG_SUPPORT
//         state_save_register_device_item(0, ST.busy_expiry_time.seconds );
//         state_save_register_device_item(0, ST.busy_expiry_time.attoseconds );
// #endif
            state_save_register_device_item(0, ST.address);
            state_save_register_device_item(0, ST.irq);
            state_save_register_device_item(0, ST.irqmask);
            state_save_register_device_item(0, ST.status);
            state_save_register_device_item(0, ST.mode);
            state_save_register_device_item(0, ST.prescaler_sel);
            state_save_register_device_item(0, ST.fn_h);
            state_save_register_device_item(0, ST.ta);
            state_save_register_device_item(0, ST.tac);
            state_save_register_device_item(0, ST.tb);
            state_save_register_device_item(0, ST.tbc);
        }

        private void state_save_register_device_item(int ch, int fc) {
            throw new UnsupportedOperationException();
        }

        private void state_save_register_device_item_array(int ch, int[] op1_out) {
            throw new UnsupportedOperationException();
        }

        private void state_save_register_device_item_array(int ch, byte[] op1_out) {
            throw new UnsupportedOperationException();
        }
    }

    public static class BaseChip {
    }

    /*
     * YM2203 local section
     */

    /** here's the virtual YM2203(OPN) */
    private static class YM2203 extends BaseChip {

        /** registers */
        public byte[] regs = new byte[256];
        /** OPN state */
        public FM_OPN opn;
        /** channel state */
        public FM_OPN.Channel[] ch = new FM_OPN.Channel[3];

        /** Generate samples for one of the YM2203s */
        void updateOne(int[][] buffer, int length) {
            FM_OPN opn = this.opn;
            int[] bufL = buffer[0];
            int[] bufR = buffer[1];
            FM_OPN.Channel[] cch = new FM_OPN.Channel[3];

            cch[0] = this.ch[0];
            cch[1] = this.ch[1];
            cch[2] = this.ch[2];

            // refresh PG and EG 
            cch[0].refresh_fc_eg_chan(opn.fnMax);
            cch[1].refresh_fc_eg_chan(opn.fnMax);
            if ((this.opn.st.mode & 0xc0) != 0) {
                // 3SLOT MODE 
                if (cch[2].slots[SLOT1].incr == -1) {
                    cch[2].slots[SLOT1].refresh_fc_eg(opn.sl3.fc[1], opn.sl3.kCode[1], opn.fnMax);
                    cch[2].slots[SLOT2].refresh_fc_eg(opn.sl3.fc[2], opn.sl3.kCode[2], opn.fnMax);
                    cch[2].slots[SLOT3].refresh_fc_eg(opn.sl3.fc[0], opn.sl3.kCode[0], opn.fnMax);
                    cch[2].slots[SLOT4].refresh_fc_eg(cch[2].fc, cch[2].kCode, opn.fnMax);
                }
            } else
                cch[2].refresh_fc_eg_chan(opn.fnMax);


            // YM2203 doesn't have LFO so we must keep these globals at 0 level 
            opn.lfoAm = 0;
            opn.lfoPm = 0;

            // buffering 
            for (int i = 0; i < length; i++) {
                // clear outputs 
                opn.outFm[0] = 0;
                opn.outFm[1] = 0;
                opn.outFm[2] = 0;

                // advance envelope generator 
                opn.egTimer += opn.egTimerAdd;
                while (opn.egTimer >= opn.egTimerOverflow) {
                    opn.egTimer -= opn.egTimerOverflow;
                    opn.egCnt++;

                    opn.advance_eg_channel(cch[0].slots);
                    opn.advance_eg_channel(cch[1].slots);
                    opn.advance_eg_channel(cch[2].slots);
                }

                // calculate FM 
                opn.chan_calc(cch[0], 0);
                opn.chan_calc(cch[1], 1);
                opn.chan_calc(cch[2], 2);

                // buffering 
                int lt = opn.outFm[0] + opn.outFm[1] + opn.outFm[2];

                lt >>= FINAL_SH;

                //limit( lt , MAXOUT, MINOUT );

                // buffering
                bufL[i] = lt;
                bufR[i] = lt;

                // timer A control 
                this.opn.st.internalTimerA(cch[2], this.opn.type);

            }
            this.opn.st.internalTimerB(length);
        }

        /** reset one of chip */
        void reset() {
            this.opn.reset2203(this.ch);
        }

        void postload() {
            // prescaler 
            this.opn.setPreScaler(1, 1);

            // SSG registers 
            for (int r = 0; r < 16; r++) {
                this.opn.st.ssg.write.accept(this.opn.st.param, 0, r);
                this.opn.st.ssg.write.accept(this.opn.st.param, 1, this.regs[r] & 0xff);
            }

            // OPN registers 
            // DT / MULTI , TL , KS / AR , AMON / DR , SR , SL / RR , SSG-EG 
            for (int r = 0x30; r < 0x9e; r++)
                if ((r & 3) != 3)
                    this.opn.writeReg(r, this.regs[r]);
            // FB / CONNECT , L / R / AMS / PMS 
            for (int r = 0xb0; r < 0xb6; r++)
                if ((r & 3) != 3)
                    this.opn.writeReg(r, this.regs[r]);

            // channels
            //postload(this.CH, 3);
        }

        void saveState(DeviceConfig device) {

            device.state_save_register_device_item_array(0, this.regs);
            device.saveStState(this.opn.st);
            device.saveChannelState(this.ch, 3);
            // 3slots 
            device.state_save_register_device_item_array(0, this.opn.sl3.fc);
            device.state_save_register_device_item(0, this.opn.sl3.fnH);
            device.state_save_register_device_item_array(0, this.opn.sl3.kCode);
        }

        /**
         * Initialize YM2203 emulator(s).
         *
         * @param clock is the chip clock in Hz
         * @param rate is sampling rate
         * @param timer_handler timer Callback handler when timer start and clear
         * @param IRQHandler IRQ Callback handler when changed IRQ level
         * @return 0 = success
         */
        private YM2203(int clock, int rate,
                       FM_OPN.State.TimerHandler timer_handler,
                       FM_OPN.State.IrqHandler IRQHandler, Callbacks ssg) {

            this.opn.type = TYPE_YM2203;
            this.opn.pCh = this.ch;
            //this.OPN.ST.device = device;
            this.opn.st.clock = clock;
            this.opn.st.rate = rate;

            this.opn.st.timerHandler = timer_handler;
            this.opn.st.irqHandler = IRQHandler;
            this.opn.st.ssg = ssg;
        }

        /**
         * shutdown the YM2203 emulators
         */
        void shutdown() {
        }

        byte read(int a) {
            int addr = this.opn.st.address;
            byte ret = 0;

            if ((a & 1) == 0) { // status port
                ret = this.opn.st.getStatus();
            } else { // data port (only SSG)
                if (addr < 16) ret = (byte) (short) this.opn.st.ssg.read.apply(this.opn.st.param);
            }
            return ret;
        }

        void setMmuteMask(int muteMask) {
            for (byte curChn = 0; curChn < 3; curChn++)
                this.ch[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
        }

        /** YM2203 I/O interface */
        int write(int a, byte v) {
            FM_OPN opn = this.opn;

            if ((a & 1) == 0) { // address port
                opn.st.address = (v &= 0xff);

                // Write register to SSG emulator 
                if (v < 16) opn.st.ssg.write.accept(opn.st.param, 0, v & 0xff);

                // prescaler select : 2d,2e,2f 
                if (v >= 0x2d && v <= 0x2f)
                    opn.setPreScaler(v, 1);
            } else { // data port
                int addr = opn.st.address;
                this.regs[addr] = v;
                switch (addr & 0xf0) {
                case 0x00: // 0x00-0x0f : SSG section
                    // Write data to SSG emulator 
                    opn.st.ssg.write.accept(opn.st.param, a, v & 0xff);
                    break;
                case 0x20: // 0x20-0x2f : Mode section
                    updateRequest.run();
                    // write register
                    opn.writeMode(addr, v);
                    break;
                default: // 0x30-0xff : opn section
                    updateRequest.run();
                    // write register
                    opn.writeReg(addr, v);
                    break;
                }
                opn.st.setBusy(1);
            }
            return opn.st.irq;
        }

        private int timerOver(int c) {
            if (c != 0) { // Timer B
                this.opn.st.timerBOver();
            } else { // Timer A
                updateRequest.run();
                // timer update
                this.opn.st.timerAOver();
                // CSM mode key,TL control 
                if ((this.opn.st.mode & 0x80) != 0) { // CSM mode auto key on
                    this.ch[2].CSMKeyControll(this.opn.type);
                }
            }
            return this.opn.st.irq;
        }

        public UpdateRequestCallback updateRequest;
    }

    /** here's the virtual YM2610 */
    private static class YM2610 extends BaseChip {

        /** ADPCM type A channel struct */
        public static class AdpcmA {

            // YM2610 ADPCM defines

            private static final int[] ADPCM_ROM_addr = new int[] {
                    0x0000, 0x01bf, // bass drum 
                    0x01c0, 0x043f, // snare drum 
                    0x0440, 0x1b7f, // top cymbal 
                    0x1b80, 0x1cff, // high hat 
                    0x1d00, 0x1f7f, // tom tom 
                    0x1f80, 0x1fff  // rim shot 
            };

            /**
             This data is derived from the chip's output - internal ROM can't be read.
             It was verified, using real YM2608, that this ADPCM stream produces 100% correct output signal.
             */
            private static final byte[] ADPCM_ROM = new byte[] {

                    // Source: 01BD.ROM 
                    // Length: 448 / 0x000001C0 

                    (byte) (byte) 0x88, 0x08, 0x08, 0x08, 0x00, (byte) (byte) 0x88, 0x16, 0x76, (byte) (byte) 0x99, (byte) (byte) 0xB8, 0x22, 0x3A, (byte) 0x84, 0x3C, (byte) 0xB1, 0x54,
                    0x10, (byte) 0xA9, (byte) 0x98, 0x32, (byte) 0x80, 0x33, (byte) 0x9A, (byte) 0xA7, 0x4A, (byte) 0xB4, 0x58, (byte) 0xBC, 0x15, 0x29, (byte) 0x8A, (byte) 0x97,
                    (byte) 0x9B, 0x44, (byte) 0xAC, (byte) 0x80, 0x12, (byte) 0xDE, 0x13, 0x1B, (byte) 0xC0, 0x58, (byte) 0xC8, 0x11, 0x0A, (byte) 0xA2, 0x1A, (byte) 0xA0,
                    0x00, (byte) 0x98, 0x0B, (byte) 0x93, (byte) 0x9E, (byte) 0x92, 0x0A, (byte) 0x88, (byte) 0xBE, 0x14, 0x1B, (byte) 0x98, 0x08, (byte) 0xA1, 0x4A, (byte) 0xC1,
                    0x30, (byte) 0xD9, 0x33, (byte) 0x98, 0x10, (byte) 0x89, 0x17, 0x1A, (byte) 0x82, 0x29, 0x37, 0x0C, (byte) 0x83, 0x50, (byte) 0x9A, 0x24,
                    0x1A, (byte) 0x83, 0x10, 0x23, 0x19, (byte) 0xB3, 0x72, (byte) 0x8A, 0x16, 0x10, 0x0A, (byte) 0x93, 0x70, (byte) 0x99, 0x23, (byte) 0x99,
                    0x02, 0x20, (byte) 0x91, 0x18, 0x02, 0x41, (byte) 0xAB, 0x24, 0x18, (byte) 0x81, (byte) 0x99, 0x4A, (byte) 0xE8, 0x28, (byte) 0x9A, (byte) 0x99,
                    (byte) 0xA1, 0x2F, (byte) 0xA8, (byte) 0x9D, (byte) 0x90, 0x08, (byte) 0xCC, (byte) 0xA3, 0x1D, (byte) 0xCA, (byte) 0x82, 0x0B, (byte) 0xD8, 0x08, (byte) 0xB9, 0x09,
                    (byte) 0xBC, (byte) 0xB8, 0x00, (byte) 0xBE, (byte) 0x90, 0x1B, (byte) 0xCA, 0x00, (byte) 0x9B, (byte) 0x8A, (byte) 0xA8, (byte) 0x91, 0x0F, (byte) 0xB3, 0x3D, (byte) 0xB8,
                    0x31, 0x0B, (byte) 0xA5, 0x0A, 0x11, (byte) 0xA1, 0x48, (byte) 0x92, 0x10, 0x50, (byte) 0x91, 0x30, 0x23, 0x09, 0x37, 0x39,
                    (byte) 0xA2, 0x72, (byte) 0x89, (byte) 0x92, 0x30, (byte) 0x83, 0x1C, (byte) 0x96, 0x28, (byte) 0xB9, 0x24, (byte) 0x8C, (byte) 0xA1, 0x31, (byte) 0xAD, (byte) 0xA9,
                    0x13, (byte) 0x9C, (byte) 0xBA, (byte) 0xA8, 0x0B, (byte) 0xBF, (byte) 0xB8, (byte) 0x9B, (byte) 0xCA, (byte) 0x88, (byte) 0xDB, (byte) 0xB8, 0x19, (byte) 0xFC, (byte) 0x92, 0x0A,
                    (byte) 0xBA, (byte) 0x89, (byte) 0xAB, (byte) 0xB8, (byte) 0xAB, (byte) 0xD8, 0x08, (byte) 0xAD, (byte) 0xBA, 0x33, (byte) 0x9D, (byte) 0xAA, (byte) 0x83, 0x3A, (byte) 0xC0, 0x40,
                    (byte) 0xB9, 0x15, 0x39, (byte) 0xA2, 0x52, (byte) 0x89, 0x02, 0x63, (byte) 0x88, 0x13, 0x23, 0x03, 0x52, 0x02, 0x54, 0x00,
                    0x11, 0x23, 0x23, 0x35, 0x20, 0x01, 0x44, 0x41, (byte) 0x80, 0x24, 0x40, (byte) 0xA9, 0x45, 0x19, (byte) 0x81, 0x12,
                    (byte) 0x81, 0x02, 0x11, 0x21, 0x19, 0x02, 0x61, (byte) 0x8A, 0x13, 0x3A, 0x10, 0x12, 0x23, (byte) 0x8B, 0x37, 0x18,
                    (byte) 0x91, 0x24, 0x10, (byte) 0x81, 0x34, 0x20, 0x05, 0x32, (byte) 0x82, 0x53, 0x20, 0x14, 0x33, 0x31, 0x34, 0x52,
                    0x00, 0x43, 0x32, 0x13, 0x52, 0x22, 0x13, 0x52, 0x11, 0x43, 0x11, 0x32, 0x32, 0x32, 0x22, 0x02,
                    0x13, 0x12, (byte) 0x89, 0x22, 0x19, (byte) 0x81, (byte) 0x81, 0x08, (byte) 0xA8, 0x08, (byte) 0x8B, (byte) 0x90, 0x1B, (byte) 0xBA, (byte) 0x8A, (byte) 0x9B,
                    (byte) 0xB9, (byte) 0x89, (byte) 0xCA, (byte) 0xB9, (byte) 0xAB, (byte) 0xCA, (byte) 0x9B, (byte) 0xCA, (byte) 0xB9, (byte) 0xAB, (byte) 0xDA, (byte) 0x99, (byte) 0xAC, (byte) 0xBB, (byte) 0x9B, (byte) 0xAC,
                    (byte) 0xAA, (byte) 0xBA, (byte) 0xAC, (byte) 0xAB, (byte) 0x9A, (byte) 0xAA, (byte) 0xAA, (byte) 0xBA, (byte) 0xB8, (byte) 0xA9, (byte) 0xBA, (byte) 0x99, (byte) 0xA9, (byte) 0x9A, (byte) 0xA0, (byte) 0x8A,
                    (byte) 0xA9, 0x08, (byte) 0x8A, (byte) 0xA9, 0x00, (byte) 0x99, (byte) 0x89, (byte) 0x88, (byte) 0x98, 0x08, (byte) 0x99, 0x00, (byte) 0x89, (byte) 0x80, 0x08, (byte) 0x98,
                    0x00, (byte) 0x88, (byte) 0x88, (byte) 0x80, (byte) 0x90, (byte) 0x80, (byte) 0x90, (byte) 0x80, (byte) 0x81, (byte) 0x99, 0x08, (byte) 0x88, (byte) 0x99, 0x09, 0x00, 0x1A,
                    (byte) 0xA8, 0x10, (byte) 0x9A, (byte) 0x88, 0x08, 0x0A, (byte) 0x8A, (byte) 0x89, (byte) 0x99, (byte) 0xA8, (byte) 0x98, (byte) 0xA9, (byte) 0x99, (byte) 0x99, (byte) 0xA9, (byte) 0x99,
                    (byte) 0xAA, (byte) 0x8A, (byte) 0xAA, (byte) 0x9B, (byte) 0x8A, (byte) 0x9A, (byte) 0xA9, (byte) 0x9A, (byte) 0xBA, (byte) 0x99, (byte) 0x9A, (byte) 0xAA, (byte) 0x99, (byte) 0x89, (byte) 0xA9, (byte) 0x99,
                    (byte) 0x98, (byte) 0x9A, (byte) 0x98, (byte) 0x88, 0x09, (byte) 0x89, 0x09, 0x08, 0x08, 0x09, 0x18, 0x18, 0x00, 0x12, 0x00, 0x11,
                    0x11, 0x11, 0x12, 0x12, 0x21, 0x21, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x32, 0x31, 0x32, 0x31,
                    0x32, 0x32, 0x21, 0x31, 0x21, 0x32, 0x21, 0x12, 0x00, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,

                    // Source: 02SD.ROM 
                    // Length: 640 / 0x00000280 

                    0x0A, (byte) 0xDC, 0x14, 0x0B, (byte) 0xBA, (byte) 0xBC, 0x01, 0x0F, (byte) 0xF5, 0x2F, (byte) 0x87, 0x19, (byte) 0xC9, 0x24, 0x1B, (byte) 0xA1,
                    0x31, (byte) 0x99, (byte) 0x90, 0x32, 0x32, (byte) 0xFE, (byte) 0x83, 0x48, (byte) 0xA8, (byte) 0xA9, 0x23, 0x19, (byte) 0xBC, (byte) 0x91, 0x02, 0x41,
                    (byte) 0xDE, (byte) 0x81, 0x28, (byte) 0xA8, 0x0A, (byte) 0xB1, 0x72, (byte) 0xDA, 0x23, (byte) 0xBC, 0x04, 0x19, (byte) 0xB8, 0x21, (byte) 0x8A, 0x03,
                    0x29, (byte) 0xBA, 0x14, 0x21, 0x0B, (byte) 0xC0, 0x43, 0x08, (byte) 0x91, 0x50, (byte) 0x93, 0x0F, (byte) 0x86, 0x1A, (byte) 0x91, 0x18,
                    0x21, (byte) 0xCB, 0x27, 0x0A, (byte) 0xA1, 0x42, (byte) 0x8C, (byte) 0xA9, 0x21, 0x10, 0x08, (byte) 0xAB, (byte) 0x94, 0x2A, (byte) 0xDA, 0x02,
                    (byte) 0x8B, (byte) 0x91, 0x09, (byte) 0x98, (byte) 0xAE, (byte) 0x80, (byte) 0xA9, 0x02, 0x0A, (byte) 0xE9, 0x21, (byte) 0xBB, 0x15, 0x20, (byte) 0xBE, (byte) 0x92,
                    0x42, 0x09, (byte) 0xA9, 0x11, 0x34, 0x08, 0x12, 0x0A, 0x27, 0x29, (byte) 0xA1, 0x52, 0x12, (byte) 0x8E, (byte) 0x92, 0x28,
                    (byte) 0x92, 0x2B, (byte) 0xD1, 0x23, (byte) 0xBF, (byte) 0x81, 0x10, (byte) 0x99, (byte) 0xA8, 0x0A, (byte) 0xC4, 0x3B, (byte) 0xB9, (byte) 0xB0, 0x00, 0x62,
                    (byte) 0xCF, (byte) 0x92, 0x29, (byte) 0x92, 0x2B, (byte) 0xB1, 0x1C, (byte) 0xB2, 0x72, (byte) 0xAA, (byte) 0x88, 0x11, 0x18, (byte) 0x80, 0x13, (byte) 0x9E,
                    0x03, 0x18, (byte) 0xB0, 0x60, (byte) 0xA1, 0x28, (byte) 0x88, 0x08, 0x04, 0x10, (byte) 0x8F, (byte) 0x96, 0x19, (byte) 0x90, 0x01, 0x09,
                    (byte) 0xC8, 0x50, (byte) 0x91, (byte) 0x8A, 0x01, (byte) 0xAB, 0x03, 0x50, (byte) 0xBA, (byte) 0x9D, (byte) 0x93, 0x68, (byte) 0xBA, (byte) 0x80, 0x22, (byte) 0xCB,
                    0x41, (byte) 0xBC, (byte) 0x92, 0x60, (byte) 0xB9, 0x1A, (byte) 0x95, 0x4A, (byte) 0xC8, 0x20, (byte) 0x88, 0x33, (byte) 0xAC, (byte) 0x92, 0x38, (byte) 0x83,
                    0x09, (byte) 0x80, 0x16, 0x09, 0x29, (byte) 0xD0, 0x54, (byte) 0x8C, (byte) 0xA2, 0x28, (byte) 0x91, (byte) 0x89, (byte) 0x93, 0x60, (byte) 0xCD, (byte) 0x85,
                    0x1B, (byte) 0xA1, 0x49, (byte) 0x90, (byte) 0x8A, (byte) 0x80, 0x34, 0x0C, (byte) 0xC9, 0x14, 0x19, (byte) 0x98, (byte) 0xA0, 0x40, (byte) 0xA9, 0x21,
                    (byte) 0xD9, 0x34, 0x0A, (byte) 0xA9, 0x10, 0x23, (byte) 0xCB, 0x25, (byte) 0xAA, 0x25, (byte) 0x9B, 0x13, (byte) 0xCD, 0x16, 0x09, (byte) 0xA0,
                    (byte) 0x80, 0x01, 0x19, (byte) 0x90, (byte) 0x88, 0x21, (byte) 0xAC, 0x33, (byte) 0x8B, (byte) 0xD8, 0x27, 0x3B, (byte) 0xB8, (byte) 0x81, 0x31, (byte) 0x80,
                    (byte) 0xAF, (byte) 0x97, 0x0A, (byte) 0x82, 0x0A, (byte) 0xA0, 0x21, (byte) 0x89, (byte) 0x8A, (byte) 0xA2, 0x32, (byte) 0x8D, (byte) 0xBB, (byte) 0x87, 0x19, 0x21,
                    (byte) 0xC9, (byte) 0xBC, 0x45, 0x09, (byte) 0x90, 0x09, (byte) 0xA1, 0x24, 0x1A, (byte) 0xD0, 0x10, 0x08, 0x11, (byte) 0xA9, 0x21, (byte) 0xE8,
                    0x60, (byte) 0xA9, 0x14, 0x0C, (byte) 0xD1, 0x32, (byte) 0xAB, 0x04, 0x0C, (byte) 0x81, (byte) 0x90, 0x29, (byte) 0x83, (byte) 0x9B, 0x01, (byte) 0x8F,
                    (byte) 0x97, 0x0B, (byte) 0x82, 0x18, (byte) 0x88, (byte) 0xBA, 0x06, 0x39, (byte) 0xC8, 0x23, (byte) 0xBC, 0x04, 0x09, (byte) 0x92, 0x08, 0x1A,
                    (byte) 0xBB, 0x74, (byte) 0x8C, (byte) 0x81, 0x18, (byte) 0x81, (byte) 0x9D, (byte) 0x83, 0x41, (byte) 0xCD, (byte) 0x81, 0x40, (byte) 0x9A, (byte) 0x90, 0x10, 0x12,
                    (byte) 0x9C, (byte) 0xA1, 0x68, (byte) 0xD8, 0x33, (byte) 0x9C, (byte) 0x91, 0x01, 0x12, (byte) 0xBE, 0x02, 0x09, 0x12, (byte) 0x99, (byte) 0x9A, 0x36,
                    0x0A, (byte) 0xB0, 0x30, (byte) 0x88, (byte) 0xA3, 0x2D, 0x12, (byte) 0xBC, 0x03, 0x3A, 0x11, (byte) 0xBD, 0x08, (byte) 0xC8, 0x62, (byte) 0x80,
                    (byte) 0x8B, (byte) 0xD8, 0x23, 0x38, (byte) 0xF9, 0x12, 0x08, (byte) 0x99, (byte) 0x91, 0x21, (byte) 0x99, (byte) 0x85, 0x2F, (byte) 0xB2, 0x30, (byte) 0x90,
                    (byte) 0x88, (byte) 0xD9, 0x53, (byte) 0xAC, (byte) 0x82, 0x19, (byte) 0x91, 0x20, (byte) 0xCC, (byte) 0x96, 0x29, (byte) 0xC9, 0x24, (byte) 0x89, (byte) 0x80, (byte) 0x99,
                    0x12, 0x08, 0x18, (byte) 0x88, (byte) 0x99, 0x23, (byte) 0xAB, 0x73, (byte) 0xCB, 0x33, (byte) 0x9F, 0x04, 0x2B, (byte) 0xB1, 0x08, 0x03,
                    0x1B, (byte) 0xC9, 0x21, 0x32, (byte) 0xFA, 0x33, (byte) 0xDB, 0x02, 0x33, (byte) 0xAE, (byte) 0xB9, 0x54, (byte) 0x8B, (byte) 0xA1, 0x20, (byte) 0x89,
                    (byte) 0x90, 0x11, (byte) 0x88, 0x09, (byte) 0x98, 0x23, (byte) 0xBE, 0x37, (byte) 0x8D, (byte) 0x81, 0x20, (byte) 0xAA, 0x34, (byte) 0xBB, 0x13, 0x18,
                    (byte) 0xB9, 0x40, (byte) 0xB1, 0x18, (byte) 0x83, (byte) 0x8E, (byte) 0xB2, 0x72, (byte) 0xBC, (byte) 0x82, 0x30, (byte) 0xA9, (byte) 0x9A, 0x24, (byte) 0x8B, 0x27,
                    0x0E, (byte) 0x91, 0x20, (byte) 0x90, 0x08, (byte) 0xB0, 0x32, (byte) 0xB9, 0x21, (byte) 0xB0, (byte) 0xAC, 0x45, (byte) 0x9A, (byte) 0xA1, 0x50, (byte) 0xA9,
                    (byte) 0x80, 0x0A, 0x26, (byte) 0x9B, 0x11, (byte) 0xBB, 0x23, 0x71, (byte) 0xCB, 0x12, 0x10, (byte) 0xB8, 0x40, (byte) 0xA9, (byte) 0xA5, 0x39,
                    (byte) 0xC0, 0x30, (byte) 0xB2, 0x20, (byte) 0xAA, (byte) 0xBA, 0x76, 0x1C, (byte) 0xC1, 0x48, (byte) 0x98, (byte) 0x80, 0x18, (byte) 0x81, (byte) 0xAA, 0x23,
                    (byte) 0x9C, (byte) 0xA2, 0x32, (byte) 0xAC, (byte) 0x9A, 0x43, (byte) 0x9C, 0x12, (byte) 0xAD, (byte) 0x82, 0x72, (byte) 0xBC, 0x00, (byte) 0x82, 0x39, (byte) 0xD1,
                    0x3A, (byte) 0xB8, 0x35, (byte) 0x9B, 0x10, 0x40, (byte) 0xF9, 0x22, 0x0A, (byte) 0xC0, 0x51, (byte) 0xB9, (byte) 0x82, 0x18, (byte) 0x98, (byte) 0xA3,
                    0x79, (byte) 0xD0, 0x20, (byte) 0x88, 0x09, 0x01, (byte) 0x99, (byte) 0x82, 0x11, 0x38, (byte) 0xFC, 0x33, 0x09, (byte) 0xC8, 0x40, (byte) 0xA9,
                    0x11, 0x29, (byte) 0xAA, (byte) 0x94, 0x3A, (byte) 0xC2, 0x4A, (byte) 0xC0, (byte) 0x89, 0x52, (byte) 0xBC, 0x11, 0x08, 0x09, (byte) 0xB8, 0x71,
                    (byte) 0xA9, 0x08, (byte) 0xA8, 0x62, (byte) 0x8D, (byte) 0x92, 0x10, 0x00, (byte) 0x9E, (byte) 0x94, 0x38, (byte) 0xBA, 0x13, (byte) 0x88, (byte) 0x90, 0x4A,
                    (byte) 0xE2, 0x30, (byte) 0xBA, 0x02, 0x00, 0x19, (byte) 0xD9, 0x62, (byte) 0xBB, 0x04, 0x0B, (byte) 0xA3, 0x68, (byte) 0xB9, 0x21, (byte) 0x88,
                    (byte) 0x9D, 0x04, 0x10, (byte) 0x8C, (byte) 0xC8, 0x62, (byte) 0x99, (byte) 0xAA, 0x24, 0x1A, (byte) 0x80, (byte) 0x9A, 0x14, (byte) 0x9B, 0x26, (byte) 0x8C,
                    (byte) 0x92, 0x30, (byte) 0xB9, 0x09, (byte) 0xA3, 0x71, (byte) 0xBB, 0x10, 0x19, (byte) 0x82, 0x39, (byte) 0xDB, 0x02, 0x44, (byte) 0x9F, 0x10,

                    // Source: 04TOP.ROM 
                    // Length: 5952 / 0x00001740 

                    0x07, (byte) 0xFF, 0x7C, 0x3C, 0x31, (byte) 0xC6, (byte) 0xC4, (byte) 0xBB, 0x7F, 0x7F, 0x7B, (byte) 0x82, (byte) 0x8A, 0x4D, 0x5F, 0x7C,
                    0x3E, 0x44, (byte) 0xD2, (byte) 0xB3, (byte) 0xA0, 0x19, 0x1B, 0x6C, (byte) 0x81, 0x28, (byte) 0xC4, (byte) 0xA1, 0x1C, 0x4B, 0x18, 0x00,
                    0x2A, (byte) 0xA2, 0x0A, 0x7C, 0x2A, 0x00, 0x01, (byte) 0x89, (byte) 0x98, 0x48, (byte) 0x8A, 0x3C, 0x28, 0x2A, 0x5B, 0x3E,
                    0x3A, 0x1A, 0x3B, 0x3D, 0x4B, 0x3B, 0x4A, 0x08, 0x2A, 0x1A, 0x2C, 0x4A, 0x3B, (byte) 0x82, (byte) 0x99, 0x3C,
                    0x5D, 0x29, 0x2B, 0x39, 0x0B, 0x23, (byte) 0xAB, 0x1A, 0x4C, 0x79, (byte) 0xA3, 0x01, (byte) 0xC1, 0x2A, 0x0A, 0x38,
                    (byte) 0xA7, (byte) 0xB9, 0x12, 0x1F, 0x29, 0x08, (byte) 0x82, (byte) 0xA1, 0x08, (byte) 0xA9, 0x42, (byte) 0xAA, (byte) 0x95, (byte) 0xB3, (byte) 0x90, (byte) 0x81,
                    0x09, (byte) 0xD4, 0x1A, (byte) 0x80, 0x1B, 0x07, (byte) 0xB8, 0x12, (byte) 0x8E, 0x49, (byte) 0x81, (byte) 0x92, (byte) 0xD3, (byte) 0x90, (byte) 0xA1, 0x2A,
                    0x02, (byte) 0xE1, (byte) 0xA3, (byte) 0x99, 0x02, (byte) 0xB3, (byte) 0x94, (byte) 0xB3, (byte) 0xB0, (byte) 0xF4, (byte) 0x98, (byte) 0x93, (byte) 0x90, 0x13, (byte) 0xE1, (byte) 0x81,
                    (byte) 0x99, 0x38, (byte) 0x91, (byte) 0xA6, (byte) 0xD3, (byte) 0x99, (byte) 0x94, (byte) 0xC1, (byte) 0x83, (byte) 0xB1, (byte) 0x92, (byte) 0x98, 0x49, (byte) 0xC4, (byte) 0xB2, (byte) 0xA4,
                    (byte) 0xA3, (byte) 0xD0, 0x1A, 0x30, (byte) 0xBA, 0x59, 0x02, (byte) 0xD4, (byte) 0xA0, (byte) 0xA4, (byte) 0xA2, (byte) 0x8A, 0x01, 0x00, (byte) 0xB7, (byte) 0xA8,
                    0x18, 0x2A, 0x2B, 0x1E, 0x23, (byte) 0xC8, 0x1A, 0x00, 0x39, (byte) 0xA0, 0x18, (byte) 0x92, 0x4F, 0x2D, 0x5A, 0x10,
                    (byte) 0x89, (byte) 0x81, 0x2A, (byte) 0x8B, 0x6A, 0x02, 0x09, (byte) 0xB3, (byte) 0x8D, 0x48, 0x1B, (byte) 0x80, 0x19, 0x34, (byte) 0xF8, 0x29,
                    0x0A, 0x7B, 0x2A, 0x28, (byte) 0x81, 0x0C, 0x02, 0x1E, 0x29, 0x09, 0x12, (byte) 0xC2, (byte) 0x94, (byte) 0xE1, 0x18, (byte) 0x98,
                    0x02, (byte) 0xC4, (byte) 0x89, (byte) 0x91, 0x1A, 0x20, (byte) 0xA9, 0x02, 0x1B, 0x48, (byte) 0x8E, 0x20, (byte) 0x88, 0x2D, 0x08, 0x59,
                    0x1B, 0x02, (byte) 0xA3, (byte) 0xB1, (byte) 0x8A, 0x1E, 0x58, (byte) 0x80, (byte) 0xC2, (byte) 0xB6, (byte) 0x88, (byte) 0x91, (byte) 0x88, 0x11, (byte) 0xA1, (byte) 0xA3,
                    (byte) 0xE2, 0x01, (byte) 0xB0, 0x19, 0x11, 0x09, (byte) 0xF4, (byte) 0x88, 0x09, (byte) 0x88, 0x19, (byte) 0x89, 0x12, (byte) 0xF1, 0x2A, 0x28,
                    (byte) 0x8C, 0x25, (byte) 0x99, (byte) 0xA4, (byte) 0x98, 0x39, (byte) 0xA1, 0x00, (byte) 0xD0, 0x58, (byte) 0xAA, 0x59, 0x01, 0x0C, 0x00, 0x2B,
                    0x00, 0x08, (byte) 0x89, 0x6B, 0x69, (byte) 0x90, 0x01, (byte) 0x90, (byte) 0x98, 0x12, (byte) 0xB3, (byte) 0xF3, (byte) 0xA0, (byte) 0x89, 0x02, 0x3B,
                    0x0C, 0x50, (byte) 0xA9, 0x4E, 0x6B, 0x19, 0x28, 0x09, (byte) 0xA2, 0x08, 0x2F, 0x20, (byte) 0x88, (byte) 0x92, (byte) 0x8A, 0x11,
                    (byte) 0xC4, (byte) 0x93, (byte) 0xF1, 0x18, (byte) 0x88, 0x11, (byte) 0xF2, (byte) 0x80, (byte) 0x92, (byte) 0xA8, 0x02, (byte) 0xA8, (byte) 0xB7, (byte) 0xB3, (byte) 0xA3, (byte) 0xA0,
                    (byte) 0x88, 0x1A, 0x40, (byte) 0xE2, (byte) 0x91, 0x19, (byte) 0x88, 0x18, (byte) 0x91, (byte) 0x83, (byte) 0xC1, (byte) 0xB5, (byte) 0x92, (byte) 0xA9, (byte) 0xC6, (byte) 0x90,
                    0x01, (byte) 0xC2, (byte) 0x81, (byte) 0x98, 0x03, (byte) 0xF0, 0x00, 0x2C, 0x2A, (byte) 0x92, 0x2C, (byte) 0x83, 0x1F, 0x3A, 0x29, 0x00,
                    (byte) 0xB8, 0x70, (byte) 0xAB, 0x69, 0x18, (byte) 0x89, 0x10, 0x0D, 0x12, 0x0B, (byte) 0x88, 0x4A, 0x3A, (byte) 0x9B, 0x70, (byte) 0xA8,
                    0x28, 0x2F, 0x2A, 0x3A, 0x1B, (byte) 0x85, (byte) 0x88, (byte) 0x8B, 0x6A, 0x29, 0x00, (byte) 0x91, (byte) 0x91, 0x1B, 0x7C, 0x29,
                    0x01, (byte) 0x88, (byte) 0x90, 0x19, 0x2B, 0x2B, 0x00, 0x39, (byte) 0xA8, 0x5E, 0x21, (byte) 0x89, (byte) 0x91, 0x09, 0x3A, 0x6F,
                    0x2A, 0x18, 0x18, (byte) 0x8B, 0x50, (byte) 0x89, 0x2B, 0x19, 0x49, (byte) 0x88, 0x29, (byte) 0xF5, (byte) 0x89, 0x08, 0x09, 0x12,
                    (byte) 0xAA, 0x15, (byte) 0xB0, (byte) 0x82, (byte) 0xAC, 0x38, 0x00, 0x3F, (byte) 0x81, 0x10, (byte) 0xB0, 0x49, (byte) 0xA2, (byte) 0x81, 0x3A, (byte) 0xC8,
                    (byte) 0x87, (byte) 0x90, (byte) 0xC4, (byte) 0xA3, (byte) 0x99, 0x19, (byte) 0x83, (byte) 0xE1, (byte) 0x84, (byte) 0xE2, (byte) 0xA2, (byte) 0x90, (byte) 0x80, (byte) 0x93, (byte) 0xB5, (byte) 0xC4,
                    (byte) 0xB3, (byte) 0xA1, 0x0A, 0x18, (byte) 0x92, (byte) 0xC4, (byte) 0xA0, (byte) 0x93, 0x0C, 0x3A, 0x18, 0x01, 0x1E, 0x20, (byte) 0xB1, (byte) 0x82,
                    (byte) 0x8C, 0x03, (byte) 0xB5, 0x2E, (byte) 0x82, 0x19, (byte) 0xB2, 0x1B, 0x1B, 0x6B, 0x4C, 0x19, 0x12, (byte) 0x8B, 0x5A, 0x11,
                    0x0C, 0x3A, 0x2C, 0x18, 0x3D, 0x08, 0x2A, 0x5C, 0x18, 0x00, (byte) 0x88, 0x3D, 0x29, (byte) 0x80, 0x2A, 0x09,
                    0x00, 0x7A, 0x0A, 0x10, 0x0B, 0x69, (byte) 0x98, 0x10, (byte) 0x81, 0x3F, 0x00, 0x18, 0x19, (byte) 0x91, (byte) 0xB7, (byte) 0x9A,
                    0x28, (byte) 0x8A, 0x48, (byte) 0x92, (byte) 0xF3, (byte) 0xA2, (byte) 0x88, (byte) 0x98, (byte) 0x87, (byte) 0xA1, (byte) 0x88, (byte) 0x80, (byte) 0x81, (byte) 0x95, (byte) 0xD1, (byte) 0xA3,
                    0x1B, 0x1C, 0x39, 0x10, (byte) 0xA1, 0x2A, 0x0B, 0x7A, 0x4B, (byte) 0x80, 0x13, (byte) 0xC1, (byte) 0xD1, 0x2B, 0x2A, (byte) 0x85,
                    (byte) 0xB2, (byte) 0xA2, (byte) 0x93, (byte) 0xB2, (byte) 0xD3, (byte) 0x80, (byte) 0xD1, 0x18, 0x08, 0x08, (byte) 0xB7, (byte) 0x98, (byte) 0x81, 0x3F, 0x01, (byte) 0x88,
                    0x01, (byte) 0xE2, 0x00, (byte) 0x9A, 0x59, 0x08, 0x10, (byte) 0xC3, (byte) 0x99, (byte) 0x84, (byte) 0xA9, (byte) 0xA5, (byte) 0x91, (byte) 0x91, (byte) 0x91, (byte) 0x80,
                    (byte) 0xB5, (byte) 0x94, (byte) 0xC0, 0x01, (byte) 0x98, 0x09, (byte) 0x84, (byte) 0xB0, (byte) 0x80, 0x7A, 0x08, 0x18, (byte) 0x90, (byte) 0xA8, 0x6A, 0x1C,
                    0x39, 0x2A, (byte) 0xB7, (byte) 0x98, 0x19, 0x10, 0x2A, (byte) 0xA1, 0x10, (byte) 0xBD, 0x39, 0x18, 0x2D, 0x39, 0x3F, 0x10,
                    0x3F, 0x01, 0x09, 0x19, 0x0A, 0x38, (byte) 0x8C, 0x40, (byte) 0xB3, (byte) 0xB4, (byte) 0x93, (byte) 0xAD, 0x20, 0x2B, (byte) 0xD4, (byte) 0x81,
                    (byte) 0xC3, (byte) 0xB0, 0x39, (byte) 0xA0, 0x23, (byte) 0xD8, 0x04, (byte) 0xB1, (byte) 0x9B, (byte) 0xA7, 0x1A, (byte) 0x92, 0x08, (byte) 0xA5, (byte) 0x88, (byte) 0x81,
                    (byte) 0xE2, 0x01, (byte) 0xB8, 0x01, (byte) 0x81, (byte) 0xC1, (byte) 0xC7, (byte) 0x90, (byte) 0x92, (byte) 0x80, (byte) 0xA1, (byte) 0x97, (byte) 0xA0, (byte) 0xA2, (byte) 0x82, (byte) 0xB8,
                    0x18, 0x00, (byte) 0x9C, 0x78, (byte) 0x98, (byte) 0x83, 0x0B, 0x0B, 0x32, 0x7D, 0x19, 0x10, (byte) 0xA1, 0x19, 0x09, 0x0A,
                    0x78, (byte) 0xA8, 0x10, 0x1B, 0x29, 0x29, 0x1A, 0x14, 0x2F, (byte) 0x88, 0x4A, 0x1B, 0x10, 0x10, (byte) 0xAB, 0x79,
                    0x0D, 0x49, 0x18, (byte) 0xA0, 0x02, 0x1F, 0x19, 0x3A, 0x2B, 0x11, (byte) 0x8A, (byte) 0x88, 0x79, (byte) 0x8A, 0x20, 0x49,
                    (byte) 0x9B, 0x58, 0x0B, 0x28, 0x18, (byte) 0xA9, 0x3A, 0x7D, 0x00, 0x29, (byte) 0x88, (byte) 0x82, 0x3D, 0x1A, 0x38, (byte) 0xBA,
                    0x15, 0x09, (byte) 0xAA, 0x51, (byte) 0x8B, (byte) 0x83, 0x3C, (byte) 0x8A, 0x58, 0x1B, (byte) 0xB5, 0x01, (byte) 0xBB, 0x50, 0x19, (byte) 0x99,
                    0x24, (byte) 0xCA, 0x21, 0x1B, (byte) 0xA2, (byte) 0x87, (byte) 0xA8, (byte) 0xB1, 0x68, (byte) 0xA1, (byte) 0xA6, (byte) 0xA2, (byte) 0xA8, 0x29, (byte) 0x8B, 0x24,
                    (byte) 0xB4, (byte) 0xE2, (byte) 0x92, (byte) 0x8A, 0x00, 0x19, (byte) 0x93, (byte) 0xB5, (byte) 0xB4, (byte) 0xB1, (byte) 0x81, (byte) 0xB1, 0x03, (byte) 0x9A, (byte) 0x82, (byte) 0xA7,
                    (byte) 0x90, (byte) 0xD6, (byte) 0xA0, (byte) 0x80, 0x1B, 0x29, 0x01, (byte) 0xA4, (byte) 0xE1, 0x18, 0x0A, 0x2A, 0x29, (byte) 0x92, (byte) 0xC7, (byte) 0xA8,
                    (byte) 0x81, 0x19, (byte) 0x89, 0x30, 0x10, (byte) 0xE0, 0x30, (byte) 0xB8, 0x10, 0x0C, 0x1A, 0x79, 0x1B, (byte) 0xA7, (byte) 0x80, (byte) 0xA0,
                    0x00, 0x0B, 0x28, 0x18, (byte) 0xB1, (byte) 0x85, 0x1E, 0x00, 0x20, (byte) 0xA9, 0x18, 0x18, 0x1C, 0x13, (byte) 0xBC, 0x15,
                    (byte) 0x99, 0x2E, 0x12, 0x00, (byte) 0xE1, 0x00, 0x0B, 0x3B, 0x21, (byte) 0x90, 0x06, (byte) 0xC9, 0x2A, 0x49, 0x0A, 0x18,
                    0x20, (byte) 0xD1, 0x3C, 0x08, 0x00, (byte) 0x83, (byte) 0xC9, 0x41, (byte) 0x8E, 0x18, 0x08, 0x02, (byte) 0xA0, 0x09, (byte) 0xA4, 0x7B,
                    (byte) 0x90, 0x19, 0x2A, 0x10, 0x2A, (byte) 0xA8, 0x71, (byte) 0xBA, 0x10, 0x4A, 0x0E, 0x22, (byte) 0xB2, (byte) 0xB2, 0x1B, (byte) 0x8C,
                    0x78, 0x1A, (byte) 0xB5, (byte) 0x93, (byte) 0xA9, 0x1B, 0x49, 0x19, 0x29, (byte) 0xA3, (byte) 0xC6, (byte) 0x88, (byte) 0xAA, 0x32, 0x0D, 0x1B,
                    0x22, 0x08, (byte) 0xC2, 0x18, (byte) 0xB9, 0x79, 0x3F, 0x01, 0x10, (byte) 0xA9, (byte) 0x84, 0x1C, 0x09, 0x21, (byte) 0xB0, (byte) 0xA7,
                    0x0A, (byte) 0x99, 0x50, 0x0C, (byte) 0x81, 0x28, (byte) 0x8B, 0x48, 0x2E, 0x00, 0x08, (byte) 0x99, 0x38, 0x5B, (byte) 0x88, 0x14,
                    (byte) 0xA9, 0x08, 0x11, (byte) 0xAA, 0x72, (byte) 0xC1, (byte) 0xB3, 0x09, (byte) 0x8A, 0x05, (byte) 0x91, (byte) 0xF2, (byte) 0x81, (byte) 0xA1, 0x09, 0x02,
                    (byte) 0xF2, (byte) 0x92, (byte) 0x99, 0x1A, 0x49, (byte) 0x80, (byte) 0xC5, (byte) 0x90, (byte) 0x90, 0x18, 0x09, 0x12, (byte) 0xA1, (byte) 0xF2, (byte) 0x81, (byte) 0x98,
                    (byte) 0xC6, (byte) 0x91, (byte) 0xA0, 0x11, (byte) 0xA0, (byte) 0x94, (byte) 0xB4, (byte) 0xF2, (byte) 0x81, (byte) 0x8B, 0x03, (byte) 0x80, (byte) 0xD2, (byte) 0x93, (byte) 0xA8, (byte) 0x88,
                    0x69, (byte) 0xA0, 0x03, (byte) 0xB8, (byte) 0x88, 0x32, (byte) 0xBC, (byte) 0x97, (byte) 0x80, (byte) 0xB1, 0x3B, 0x1A, (byte) 0xA6, 0x00, (byte) 0xD1, 0x01,
                    0x0B, 0x3B, 0x30, (byte) 0x9B, 0x31, 0x3E, (byte) 0x92, 0x19, (byte) 0x8A, (byte) 0xD3, 0x5C, 0x1B, 0x41, (byte) 0xA0, (byte) 0x93, (byte) 0xA2,
                    (byte) 0xAF, 0x39, 0x4C, 0x01, (byte) 0x92, (byte) 0xA8, (byte) 0x81, 0x3C, 0x0D, 0x78, (byte) 0x98, 0x00, 0x19, 0x0A, 0x20, 0x2D,
                    0x29, 0x3C, 0x1B, 0x48, (byte) 0x88, (byte) 0x99, 0x7A, 0x2D, 0x29, 0x2A, (byte) 0x82, (byte) 0x80, (byte) 0xA8, 0x49, 0x3E, 0x19,
                    0x11, (byte) 0x98, (byte) 0x82, (byte) 0x9A, 0x3B, 0x28, 0x2F, 0x20, 0x4C, (byte) 0x90, 0x29, 0x19, (byte) 0x9A, 0x7A, 0x29, 0x28,
                    (byte) 0x98, (byte) 0x88, 0x33, (byte) 0xCD, 0x11, 0x3A, (byte) 0xC1, (byte) 0xA4, (byte) 0xA0, (byte) 0xC4, (byte) 0x82, (byte) 0xC8, 0x50, (byte) 0x98, (byte) 0xB2, 0x21,
                    (byte) 0xC0, (byte) 0xB6, (byte) 0x98, (byte) 0x82, (byte) 0x80, (byte) 0x9C, 0x23, 0x00, (byte) 0xF8, 0x30, (byte) 0xA8, 0x1A, 0x68, (byte) 0xA8, (byte) 0x86, (byte) 0x9A,
                    0x01, 0x2A, 0x0A, (byte) 0x97, (byte) 0x91, (byte) 0xC1, 0x18, (byte) 0x89, 0x02, (byte) 0x83, (byte) 0xE0, 0x01, (byte) 0x8B, 0x29, 0x30, (byte) 0xE2,
                    (byte) 0x91, 0x0B, 0x18, 0x3B, 0x1C, 0x11, 0x28, (byte) 0xAC, 0x78, (byte) 0x80, (byte) 0x93, (byte) 0x91, (byte) 0xA9, 0x49, (byte) 0x8B, (byte) 0x87,
                    (byte) 0x90, (byte) 0x99, 0x3D, 0x5A, (byte) 0x81, 0x08, (byte) 0xA1, 0x11, 0x2F, 0x1A, 0x21, (byte) 0x9B, 0x15, (byte) 0xA2, (byte) 0xB0, 0x11,
                    (byte) 0xC0, (byte) 0x91, 0x5B, (byte) 0x98, 0x24, (byte) 0xA2, (byte) 0xF2, (byte) 0x92, (byte) 0x8B, 0x6A, 0x18, (byte) 0x81, (byte) 0xB5, (byte) 0xB1, (byte) 0x88, 0x4C,
                    0x00, 0x00, (byte) 0xA4, (byte) 0xC1, 0x2B, 0x1A, 0x59, 0x0A, 0x02, (byte) 0x80, 0x1E, 0x02, 0x08, (byte) 0xB3, (byte) 0x80, (byte) 0x9A,
                    0x23, (byte) 0xB8, (byte) 0xF2, (byte) 0x84, (byte) 0xAB, 0x01, 0x48, (byte) 0x90, (byte) 0xA7, (byte) 0x90, 0x0A, 0x29, 0x09, (byte) 0x95, (byte) 0x99, (byte) 0xA0,
                    0x59, 0x2B, 0x00, (byte) 0x97, (byte) 0xB0, 0x29, (byte) 0x89, 0x2A, 0x03, (byte) 0xD0, (byte) 0xB7, 0x1B, (byte) 0x81, 0x00, (byte) 0xA6, (byte) 0xB1,
                    (byte) 0x90, 0x09, 0x48, (byte) 0xC0, 0x11, 0x00, (byte) 0x8A, 0x00, 0x5B, (byte) 0x83, (byte) 0x9A, 0x18, 0x2F, 0x3C, 0x18, 0x11,
                    (byte) 0xA9, 0x04, 0x1A, 0x4F, 0x01, (byte) 0x98, (byte) 0x81, 0x09, 0x09, 0x4A, 0x18, (byte) 0xB4, (byte) 0xA2, 0x0B, 0x59, (byte) 0x90,
                    0x3B, 0x49, (byte) 0xBC, 0x40, 0x6A, (byte) 0x88, 0x3A, 0x08, 0x3E, 0x3A, (byte) 0x80, (byte) 0x93, (byte) 0xB0, (byte) 0xE1, 0x5A, 0x00,
                    (byte) 0xA4, (byte) 0xB3, (byte) 0xE3, (byte) 0x90, 0x0D, 0x38, 0x09, (byte) 0x82, (byte) 0xC4, (byte) 0xA1, (byte) 0xB1, 0x4C, 0x18, 0x10, (byte) 0x91, (byte) 0xB2,
                    0x13, (byte) 0xEA, 0x34, (byte) 0x99, (byte) 0x88, (byte) 0xA6, (byte) 0x89, (byte) 0x92, (byte) 0x91, (byte) 0xC1, 0x20, (byte) 0xB2, (byte) 0xC2, (byte) 0x86, (byte) 0xD2, (byte) 0xB3,
                    (byte) 0x80, (byte) 0xB2, 0x08, 0x09, (byte) 0x87, (byte) 0x91, (byte) 0xC0, 0x11, (byte) 0x89, (byte) 0x90, 0x28, (byte) 0xB9, 0x79, 0x19, (byte) 0xA4, (byte) 0x82,
                    (byte) 0xD0, 0x03, 0x0C, (byte) 0xA3, (byte) 0xA5, (byte) 0xB2, (byte) 0xB2, 0x1B, 0x29, 0x13, (byte) 0xF1, (byte) 0xB4, (byte) 0x81, (byte) 0x9D, 0x38, 0x00,
                    (byte) 0xC4, (byte) 0xA1, (byte) 0x89, 0x59, 0x1A, (byte) 0x81, (byte) 0xA4, (byte) 0xA9, 0x1C, 0x6A, 0x19, 0x02, (byte) 0xB1, 0x1A, 0x4A, 0x0B,
                    0x78, (byte) 0x89, (byte) 0x81, 0x1C, 0x2A, 0x29, 0x4A, (byte) 0xA3, 0x3E, 0x1C, 0x49, 0x1A, 0x08, 0x21, (byte) 0xAE, 0x28,
                    0x4B, 0x19, 0x20, (byte) 0x8C, 0x10, 0x3A, (byte) 0xAB, 0x26, (byte) 0x8B, 0x18, 0x59, (byte) 0x99, 0x13, (byte) 0xA2, (byte) 0xAB, 0x79,
                    0x2F, 0x18, 0x10, (byte) 0xB2, (byte) 0x80, 0x1B, 0x4D, 0x5A, (byte) 0x80, (byte) 0x82, (byte) 0x98, (byte) 0x81, (byte) 0x80, 0x09, (byte) 0xA5, (byte) 0x90,
                    (byte) 0x91, 0x03, (byte) 0xC2, (byte) 0xE2, (byte) 0x81, (byte) 0xA8, (byte) 0x82, 0x09, (byte) 0xC6, (byte) 0xA3, (byte) 0xB1, 0x08, 0x5B, 0x08, 0x05, (byte) 0xD1,
                    (byte) 0xA2, (byte) 0x89, 0x2A, 0x28, (byte) 0x91, (byte) 0xA6, (byte) 0x88, (byte) 0xB0, 0x49, (byte) 0x80, 0x09, 0x08, (byte) 0x88, 0x07, (byte) 0xB8, 0x05,
                    (byte) 0x99, (byte) 0x81, (byte) 0x88, 0x18, (byte) 0xE2, 0x00, (byte) 0xC3, 0x18, 0x0D, 0x10, 0x30, (byte) 0xD0, (byte) 0x93, (byte) 0x8A, 0x09, 0x10,
                    0x2F, 0x11, (byte) 0x90, (byte) 0xA1, 0x20, (byte) 0x9B, (byte) 0xB1, 0x73, (byte) 0xC8, (byte) 0x94, (byte) 0x98, 0x3B, 0x01, 0x0C, 0x30, 0x19,
                    (byte) 0xF8, 0x12, (byte) 0x90, (byte) 0xBA, 0x78, 0x0A, 0x11, (byte) 0x98, (byte) 0xA0, 0x79, (byte) 0x8A, 0x30, 0x2B, (byte) 0xC2, 0x11, 0x0D,
                    0x09, 0x7A, 0x00, (byte) 0x82, (byte) 0xB9, 0x01, 0x7A, (byte) 0x89, 0x21, 0x09, (byte) 0xA1, 0x0A, 0x7C, 0x10, (byte) 0x88, (byte) 0xB5,
                    (byte) 0x88, 0x0A, 0x2B, 0x69, 0x1A, 0x10, (byte) 0xA0, 0x5B, 0x19, 0x1A, 0x10, 0x19, 0x1A, 0x6C, 0x20, (byte) 0x90,
                    (byte) 0xA5, (byte) 0x98, 0x1B, 0x0A, 0x69, (byte) 0x82, (byte) 0xD1, 0x18, 0x09, 0x19, 0x2A, (byte) 0x93, (byte) 0xD4, (byte) 0x9A, 0x01, 0x49,
                    (byte) 0xA2, (byte) 0xA2, (byte) 0x82, (byte) 0xD8, 0x22, (byte) 0xAA, (byte) 0x97, (byte) 0xA9, 0x2D, 0x38, 0x2A, (byte) 0xB6, (byte) 0x80, (byte) 0x90, 0x0A, 0x3C,
                    (byte) 0x82, (byte) 0x94, (byte) 0xB8, 0x21, 0x0E, 0x2A, 0x22, (byte) 0xB8, 0x00, 0x4F, 0x2B, 0x3A, (byte) 0x81, (byte) 0xA1, 0x29, 0x2C,
                    0x6A, 0x13, (byte) 0xD1, (byte) 0xA2, (byte) 0x98, 0x28, 0x0C, 0x01, (byte) 0xD5, 0x08, (byte) 0xA9, 0x31, (byte) 0xB3, (byte) 0xB0, (byte) 0xA7, (byte) 0xB0,
                    0x29, 0x1B, (byte) 0x87, (byte) 0xA2, (byte) 0xA1, (byte) 0xB2, 0x4A, (byte) 0x89, 0x11, (byte) 0xC3, (byte) 0xF3, (byte) 0x98, 0x08, 0x03, (byte) 0xA0, (byte) 0xA3,
                    (byte) 0xC5, (byte) 0x90, (byte) 0xB3, (byte) 0xB5, (byte) 0xB4, (byte) 0xB8, 0x02, (byte) 0x91, (byte) 0x91, (byte) 0xD3, (byte) 0xA4, (byte) 0xC1, 0x1B, (byte) 0x82, 0x28, (byte) 0xA4,
                    (byte) 0xD1, (byte) 0x94, (byte) 0x8A, 0x28, 0x08, 0x03, (byte) 0xE0, (byte) 0x80, (byte) 0xD4, (byte) 0x90, (byte) 0x91, (byte) 0xA1, 0x3B, 0x3D, 0x02, (byte) 0xE4,
                    (byte) 0xA1, (byte) 0x92, (byte) 0x89, 0x1A, 0x4B, (byte) 0x95, (byte) 0xB3, (byte) 0x90, (byte) 0x99, 0x6A, 0x0A, 0x30, (byte) 0xA1, (byte) 0x93, (byte) 0xA6, (byte) 0xA9,
                    (byte) 0x85, (byte) 0x8B, (byte) 0x82, 0x10, (byte) 0xB1, (byte) 0xA3, (byte) 0x94, (byte) 0xF8, 0x38, (byte) 0x9A, 0x30, 0x1A, (byte) 0x8B, (byte) 0xA7, (byte) 0x89, 0x01,
                    0x5B, 0x19, 0x18, 0x11, (byte) 0xF0, 0x18, 0x1C, 0x39, 0x19, 0x0C, 0x12, 0x1C, 0x2A, 0x7B, 0x3A, (byte) 0x88,
                    0x2B, 0x18, 0x2B, 0x5C, 0x20, (byte) 0x92, (byte) 0x8D, 0x38, (byte) 0x8A, 0x3A, 0x5B, 0x2E, 0x3A, 0x2B, 0x10, 0x12,
                    (byte) 0xBB, 0x6A, 0x4D, 0x18, 0x10, (byte) 0xB1, (byte) 0x81, 0x2A, (byte) 0x8B, 0x79, (byte) 0x80, 0x01, 0x0A, 0x09, 0x5B, 0x2D,
                    (byte) 0x84, (byte) 0x8A, 0x08, 0x02, (byte) 0xA2, (byte) 0x91, (byte) 0x82, (byte) 0xE8, 0x50, (byte) 0x9B, (byte) 0x85, (byte) 0xA3, (byte) 0xB0, (byte) 0xA3, 0x1B, 0x02,
                    0x18, (byte) 0xF3, (byte) 0xA2, (byte) 0x88, (byte) 0xAB, 0x53, (byte) 0xD1, (byte) 0xB4, (byte) 0xA3, 0x09, 0x09, 0x18, (byte) 0xD4, 0x08, (byte) 0xB0, 0x09,
                    0x58, (byte) 0xD1, (byte) 0x82, (byte) 0x89, (byte) 0x81, 0x1A, 0x18, 0x05, (byte) 0xB9, (byte) 0xC3, 0x30, (byte) 0xC0, (byte) 0x95, (byte) 0x80, (byte) 0xC3, (byte) 0x89,
                    (byte) 0x89, 0x13, (byte) 0x88, (byte) 0xF2, (byte) 0x93, 0x0E, 0x18, 0x01, (byte) 0x92, (byte) 0xA5, (byte) 0xB8, 0x2A, 0x39, (byte) 0xAA, 0x33, (byte) 0x9A,
                    (byte) 0xB1, 0x11, (byte) 0xF5, (byte) 0xA1, (byte) 0xA1, 0x0A, 0x50, (byte) 0xB8, 0x03, (byte) 0xC4, (byte) 0xA0, 0x4E, 0x29, 0x10, (byte) 0x88, (byte) 0xC2,
                    0x1A, 0x39, 0x1D, 0x28, (byte) 0x98, (byte) 0x94, 0x0E, 0x10, 0x2A, 0x3C, 0x02, 0x2D, 0x1B, 0x4B, 0x3B, 0x49,
                    0x19, (byte) 0xA9, 0x48, 0x2F, 0x29, 0x10, (byte) 0x89, 0x02, 0x0C, 0x10, 0x09, (byte) 0xB9, 0x70, 0x1B, (byte) 0x8A, 0x50,
                    (byte) 0xA8, 0x2B, 0x49, (byte) 0x89, 0x69, (byte) 0x88, (byte) 0x95, (byte) 0x89, (byte) 0x90, (byte) 0x92, 0x4C, 0x19, (byte) 0x82, (byte) 0xC1, 0x01, (byte) 0x80,
                    (byte) 0xA0, 0x2B, 0x7A, (byte) 0x81, 0x10, (byte) 0xC2, (byte) 0xB7, (byte) 0x98, (byte) 0x88, 0x19, 0x2C, 0x03, (byte) 0xB1, (byte) 0xA4, (byte) 0xA1, 0x0C,
                    0x3B, 0x78, (byte) 0x88, (byte) 0x85, (byte) 0xB1, (byte) 0xA0, 0x1B, 0x3A, 0x4A, 0x08, (byte) 0x94, (byte) 0x81, (byte) 0xF1, (byte) 0x80, 0x00, 0x0C,
                    0x59, 0x09, 0x18, (byte) 0x90, (byte) 0xA6, (byte) 0x92, (byte) 0x8C, 0x1A, 0x79, (byte) 0x92, (byte) 0xA8, 0x00, (byte) 0x81, 0x2E, 0x2A, 0x13,
                    (byte) 0xA2, (byte) 0xB0, (byte) 0xA5, (byte) 0x88, (byte) 0x88, (byte) 0x89, 0x11, 0x19, (byte) 0xA0, (byte) 0xF3, (byte) 0x82, (byte) 0xB0, (byte) 0x83, 0x5F, 0x2A, 0x01,
                    (byte) 0xA1, (byte) 0x94, (byte) 0xB0, 0x09, 0x78, (byte) 0x98, (byte) 0xA3, (byte) 0xA6, (byte) 0xA0, (byte) 0x91, (byte) 0x80, (byte) 0x93, (byte) 0x98, (byte) 0xC1, 0x12, 0x18,
                    (byte) 0xC9, 0x17, (byte) 0xA0, (byte) 0xA0, 0x1A, 0x21, (byte) 0x80, (byte) 0x99, (byte) 0xD4, 0x30, (byte) 0x9D, 0x00, 0x10, 0x2F, 0x08, 0x1C,
                    0x21, 0x08, (byte) 0xB4, (byte) 0xC3, 0x2B, (byte) 0xA9, 0x52, (byte) 0xD2, (byte) 0xA3, (byte) 0xD1, 0x09, 0x10, (byte) 0x8B, 0x24, (byte) 0x92, (byte) 0xD1,
                    (byte) 0x80, 0x19, (byte) 0xA0, 0x2C, 0x12, 0x49, (byte) 0xAA, (byte) 0xB6, (byte) 0x95, (byte) 0xB8, 0x08, 0x3A, 0x2B, 0x01, (byte) 0xF3, (byte) 0xB3,
                    0x0B, 0x09, 0x79, 0x18, (byte) 0xA2, (byte) 0xA4, (byte) 0xA0, 0x18, 0x0C, 0x20, 0x08, (byte) 0xA9, 0x16, 0x0C, 0x00, 0x1B,
                    0x08, 0x2B, 0x7B, 0x01, 0x01, (byte) 0xB9, 0x59, 0x19, (byte) 0x8B, 0x45, (byte) 0xA8, (byte) 0x80, 0x0C, 0x1A, 0x41, 0x1E,
                    0x00, 0x28, (byte) 0xA8, 0x5A, 0x00, (byte) 0xC1, 0x49, (byte) 0x99, 0x21, 0x1D, 0x08, (byte) 0x85, (byte) 0x99, (byte) 0x95, (byte) 0x89, (byte) 0x90,
                    0x11, (byte) 0x90, (byte) 0xD1, 0x28, (byte) 0xB2, (byte) 0xA7, (byte) 0x99, (byte) 0x81, 0x02, (byte) 0xAC, 0x13, (byte) 0x81, (byte) 0xB2, (byte) 0xA6, (byte) 0xA9, 0x28,
                    0x1C, (byte) 0xB1, 0x33, (byte) 0xD1, (byte) 0xC1, 0x58, (byte) 0xA8, 0x14, (byte) 0xB0, (byte) 0xB7, (byte) 0x91, (byte) 0xA0, (byte) 0x82, (byte) 0x89, (byte) 0xC2, 0x28,
                    (byte) 0xA1, (byte) 0xB2, 0x49, (byte) 0xD2, (byte) 0x94, (byte) 0xC8, 0x12, (byte) 0x80, (byte) 0x99, (byte) 0x85, 0x08, (byte) 0xD3, 0x09, (byte) 0xA2, (byte) 0xB3, 0x1E,
                    0x08, 0x21, (byte) 0xB9, 0x23, (byte) 0xB4, (byte) 0xAB, 0x41, (byte) 0xAC, (byte) 0x87, 0x09, (byte) 0xA2, (byte) 0xC5, 0x0B, 0x2A, 0x5A, (byte) 0x91,
                    0x20, (byte) 0x9A, (byte) 0x89, 0x78, (byte) 0x9B, 0x31, (byte) 0x89, (byte) 0x80, 0x29, 0x0A, (byte) 0xB7, 0x3C, (byte) 0x98, 0x48, 0x1D, 0x00,
                    0x01, (byte) 0xB0, 0x20, 0x2F, 0x29, 0x4A, (byte) 0x89, (byte) 0x94, 0x1C, (byte) 0x88, 0x28, 0x2B, 0x10, (byte) 0x88, (byte) 0x9A, 0x71,
                    (byte) 0x9A, 0x08, 0x4A, 0x2F, 0x18, 0x2B, 0x18, 0x02, (byte) 0xA8, 0x4B, 0x7A, (byte) 0x99, 0x48, (byte) 0x80, (byte) 0xA8, 0x20,
                    0x1D, 0x40, (byte) 0xA8, 0x10, 0x08, (byte) 0xA8, (byte) 0xC5, (byte) 0x88, (byte) 0xC2, 0x18, (byte) 0x88, 0x2A, 0x12, (byte) 0xF3, (byte) 0x82, (byte) 0xD8,
                    0x20, 0x0A, 0x09, (byte) 0xA6, (byte) 0x98, 0x04, (byte) 0xB9, 0x11, 0x18, (byte) 0xC3, (byte) 0xE1, 0x29, (byte) 0xA1, 0x11, (byte) 0xC1, 0x03,
                    (byte) 0xE2, (byte) 0x9A, 0x33, (byte) 0xA9, (byte) 0xB5, (byte) 0x98, (byte) 0x92, (byte) 0xA1, 0x02, (byte) 0xF8, 0x21, (byte) 0xA8, 0x10, 0x02, (byte) 0xC1, (byte) 0xB7,
                    0x1B, (byte) 0x90, 0x5B, 0x3C, (byte) 0x83, (byte) 0x93, (byte) 0xE0, 0x19, 0x1A, 0x11, 0x11, (byte) 0xF1, (byte) 0x92, (byte) 0x89, 0x19, 0x2C,
                    0x2C, 0x41, (byte) 0x99, (byte) 0x92, (byte) 0x90, 0x3F, 0x18, 0x4B, 0x00, 0x08, (byte) 0xD2, 0x01, (byte) 0xB2, (byte) 0xAA, 0x78, 0x09,
                    0x01, (byte) 0x91, (byte) 0xA2, (byte) 0x98, 0x2F, 0x3A, 0x2C, 0x01, 0x00, (byte) 0x93, (byte) 0xE0, 0x28, 0x2C, 0x2B, 0x01, 0x12,
                    (byte) 0xE1, (byte) 0x80, (byte) 0xB3, 0x3D, 0x3A, 0x0A, 0x50, (byte) 0x98, (byte) 0xC2, (byte) 0xA0, 0x11, (byte) 0xAA, 0x30, (byte) 0x87, (byte) 0x90, (byte) 0xC2,
                    0x29, (byte) 0x88, 0x38, (byte) 0xC8, (byte) 0xB5, (byte) 0x90, (byte) 0xBA, 0x70, 0x1A, 0x02, (byte) 0x94, (byte) 0xD0, (byte) 0x80, 0x1A, (byte) 0x82, (byte) 0xA6,
                    (byte) 0xB0, (byte) 0x91, 0x18, (byte) 0xB3, 0x00, 0x13, (byte) 0xF1, (byte) 0xA2, (byte) 0xC1, (byte) 0x82, (byte) 0xB0, 0x00, 0x15, 0x0B, (byte) 0xD3, 0x02,
                    (byte) 0xA8, (byte) 0x91, 0x2B, 0x1F, 0x49, (byte) 0x88, (byte) 0xA6, (byte) 0x80, (byte) 0x88, 0x08, 0x1B, (byte) 0xA5, (byte) 0x80, (byte) 0xB9, 0x06, 0x0B,
                    (byte) 0x90, 0x21, (byte) 0x9D, 0x48, 0x18, (byte) 0xA0, 0x15, (byte) 0xC9, (byte) 0x82, 0x2B, 0x1A, 0x42, (byte) 0x9A, (byte) 0xC4, 0x39, (byte) 0xBC,
                    0x69, 0x00, (byte) 0xA0, 0x29, (byte) 0x8C, 0x39, 0x59, 0x08, 0x09, 0x49, (byte) 0xA9, 0x6B, (byte) 0x81, 0x00, (byte) 0x98, (byte) 0xB0,
                    0x68, 0x3D, (byte) 0x81, (byte) 0x88, 0x18, 0x19, 0x1D, 0x12, (byte) 0x80, (byte) 0xB2, 0x3A, 0x3F, (byte) 0x85, (byte) 0x92, (byte) 0xD0, 0x00,
                    0x0A, 0x19, 0x12, (byte) 0xF1, 0x02, (byte) 0x9B, 0x19, 0x40, (byte) 0xB9, 0x11, 0x02, (byte) 0xF2, 0x1A, 0x08, (byte) 0x94, 0x0A,
                    (byte) 0xC2, (byte) 0x83, 0x0B, (byte) 0xB4, (byte) 0xA4, (byte) 0xC0, 0x32, (byte) 0xD8, (byte) 0x86, (byte) 0x98, (byte) 0x90, (byte) 0x95, (byte) 0x89, (byte) 0xA3, (byte) 0x83, (byte) 0xC2,
                    (byte) 0x92, (byte) 0xE1, (byte) 0x92, (byte) 0x82, (byte) 0xD9, 0x03, 0x08, (byte) 0xA9, (byte) 0x85, (byte) 0x92, (byte) 0xA2, (byte) 0x80, (byte) 0xE0, 0x30, (byte) 0x8B, (byte) 0xB3,
                    (byte) 0x87, (byte) 0x89, (byte) 0x90, (byte) 0x83, (byte) 0xA0, 0x08, (byte) 0x92, (byte) 0x93, 0x3E, (byte) 0xAB, 0x43, (byte) 0x89, (byte) 0xE3, (byte) 0x80, (byte) 0x83, 0x2F,
                    0x00, (byte) 0xA3, (byte) 0x80, (byte) 0xC9, 0x22, 0x3F, 0x08, (byte) 0x81, 0x0B, 0x33, (byte) 0x9A, (byte) 0xA3, 0x7B, 0x0C, 0x29, 0x4A,
                    0x1B, 0x21, (byte) 0xAA, 0x70, 0x1B, 0x0D, 0x48, 0x1A, (byte) 0x81, (byte) 0x88, (byte) 0xB1, 0x39, 0x3F, 0x08, 0x58, (byte) 0xA0,
                    (byte) 0x81, 0x1A, 0x1A, 0x2B, 0x6D, 0x11, 0x0A, (byte) 0x91, 0x01, 0x1A, (byte) 0x98, 0x5A, 0x0C, 0x03, (byte) 0xB1, (byte) 0x84,
                    (byte) 0xA3, (byte) 0xAD, 0x58, 0x2A, (byte) 0xA1, (byte) 0x84, (byte) 0xB1, (byte) 0xA0, 0x5C, 0x2B, 0x13, (byte) 0xA8, (byte) 0x95, (byte) 0x83, (byte) 0xE8, 0x10,
                    (byte) 0x81, (byte) 0xB0, 0x00, (byte) 0xC2, (byte) 0x96, (byte) 0xA0, (byte) 0x91, 0x00, 0x2C, (byte) 0x90, 0x30, (byte) 0xF2, (byte) 0x80, (byte) 0xA8, 0x39, 0x21,
                    (byte) 0xC1, 0x03, (byte) 0xAC, 0x39, 0x7C, 0x29, (byte) 0x91, 0x1A, 0x00, 0x19, 0x2C, 0x3A, (byte) 0x93, (byte) 0xB0, 0x29, (byte) 0x8F,
                    0x28, 0x02, (byte) 0x93, (byte) 0xF3, (byte) 0xA9, 0x01, 0x03, (byte) 0xE0, 0x08, 0x09, 0x1D, 0x58, (byte) 0xA1, (byte) 0x83, (byte) 0xA9, 0x6B,
                    0x2A, 0x3C, 0x21, (byte) 0x89, (byte) 0xC2, 0x2C, 0x4B, (byte) 0x8A, 0x50, (byte) 0x81, (byte) 0x98, (byte) 0xA8, 0x32, 0x0C, (byte) 0x8E, 0x24,
                    0x0B, 0x1A, (byte) 0x81, (byte) 0x92, (byte) 0xA1, 0x4F, 0x18, 0x3A, 0x0A, (byte) 0xB4, 0x18, 0x2E, 0x39, (byte) 0x82, 0x19, (byte) 0xD3,
                    (byte) 0xD0, 0x28, 0x1B, 0x11, (byte) 0x98, 0x07, (byte) 0xAA, 0x28, 0x00, (byte) 0x88, (byte) 0xB4, (byte) 0x89, 0x1B, 0x1F, 0x22, 0x00,
                    (byte) 0xB3, (byte) 0xC9, 0x33, (byte) 0xAB, 0x2B, (byte) 0xB5, 0x48, (byte) 0x98, (byte) 0x98, (byte) 0xA7, 0x10, (byte) 0xD2, (byte) 0xC1, 0x23, (byte) 0xCA, (byte) 0x93,
                    (byte) 0xC6, (byte) 0x80, (byte) 0xA1, (byte) 0x88, 0x02, (byte) 0x89, (byte) 0xE2, 0x09, 0x38, (byte) 0xBA, 0x40, (byte) 0x89, 0x21, (byte) 0xD8, 0x49, 0x10,
                    (byte) 0x8D, 0x02, (byte) 0x90, (byte) 0xC3, (byte) 0x9A, 0x24, (byte) 0x89, 0x08, (byte) 0x84, (byte) 0xA5, (byte) 0x9C, 0x10, 0x11, (byte) 0x9C, (byte) 0x88, 0x30,
                    0x3C, (byte) 0xA1, (byte) 0x94, 0x58, (byte) 0x8C, 0x0B, 0x69, 0x29, (byte) 0x9A, (byte) 0x81, 0x12, 0x2B, (byte) 0x8B, 0x79, (byte) 0x94, (byte) 0xB0,
                    (byte) 0xC1, (byte) 0x84, (byte) 0xC2, (byte) 0x99, 0x25, (byte) 0x99, 0x11, (byte) 0xA2, (byte) 0x93, (byte) 0xE4, (byte) 0x99, (byte) 0x80, 0x0A, 0x00, 0x10, (byte) 0xB7,
                    (byte) 0xB0, 0x31, (byte) 0xBA, 0x3C, 0x21, (byte) 0xB3, (byte) 0xF1, 0x18, (byte) 0xA0, 0x2A, 0x20, (byte) 0xA3, 0x06, (byte) 0xE8, 0x28, (byte) 0xA1,
                    (byte) 0xB4, 0x08, 0x0B, 0x11, 0x4B, (byte) 0xB7, (byte) 0x90, (byte) 0xA5, (byte) 0x98, 0x3D, 0x19, 0x02, (byte) 0xA1, (byte) 0xC4, (byte) 0xB2, 0x19,
                    0x28, (byte) 0xC0, (byte) 0xA5, (byte) 0x92, (byte) 0xB1, (byte) 0xA3, 0x0A, 0x0A, 0x08, 0x2B, 0x70, (byte) 0xC4, (byte) 0xB3, 0x00, (byte) 0xBC, 0x4B,
                    0x39, 0x12, (byte) 0xE3, (byte) 0xA0, 0x00, 0x3F, 0x18, 0x29, (byte) 0x94, (byte) 0xD1, 0x19, 0x09, 0x00, (byte) 0xA1, (byte) 0x83, (byte) 0x99,
                    (byte) 0x9B, 0x35, (byte) 0x80, (byte) 0xC4, (byte) 0xB1, 0x6A, 0x1A, 0x1C, 0x29, 0x38, 0x0E, 0x19, 0x5A, 0x1A, (byte) 0x82, (byte) 0x8A,
                    0x59, 0x2A, 0x2E, 0x20, (byte) 0x88, (byte) 0xA8, 0x3A, 0x38, 0x3D, 0x00, (byte) 0xB3, 0x29, (byte) 0xAD, 0x49, 0x10, 0x0C,
                    0x01, 0x01, (byte) 0xA3, (byte) 0x8F, (byte) 0x85, 0x09, 0x1B, (byte) 0x88, 0x10, (byte) 0xA3, (byte) 0xD2, (byte) 0x90, 0x3C, 0x5C, 0x39, 0x03,
                    (byte) 0xD1, (byte) 0xA0, 0x00, 0x2A, 0x0B, 0x04, (byte) 0xA7, (byte) 0x90, (byte) 0xA0, 0x11, (byte) 0x90, (byte) 0x99, (byte) 0x83, (byte) 0xB4, (byte) 0xB1, (byte) 0xF1,
                    (byte) 0x84, (byte) 0x88, (byte) 0x90, 0x18, 0x18, (byte) 0xD3, (byte) 0xD2, (byte) 0xB3, (byte) 0xA0, 0x1A, 0x21, (byte) 0xA7, (byte) 0xB2, (byte) 0xB3, (byte) 0x92, (byte) 0x9A,
                    0x22, (byte) 0xB9, 0x28, 0x38, (byte) 0xBD, (byte) 0x87, 0x2A, (byte) 0xB1, 0x13, 0x0D, 0x0A, 0x38, (byte) 0xC9, 0x24, (byte) 0xC0, 0x19,
                    0x23, 0x0F, 0x01, (byte) 0x88, (byte) 0xC0, 0x2A, (byte) 0x82, 0x18, 0x28, (byte) 0xF0, 0x18, 0x2A, 0x29, 0x4B, 0x35, (byte) 0xB8,
                    (byte) 0xA3, (byte) 0x9D, 0x18, 0x1B, 0x40, 0x00, (byte) 0x9A, 0x5C, 0x3A, 0x09, 0x2F, 0x38, (byte) 0x8A, 0x3B, 0x3B, 0x11,
                    0x5C, 0x19, 0x2B, 0x4A, 0x08, 0x0A, 0x3D, 0x20, 0x4F, 0x3A, 0x19, 0x2A, 0x18, 0x4D, 0x1B, 0x3A,
                    0x11, 0x0D, 0x3A, 0x3C, 0x4B, (byte) 0x93, (byte) 0x81, (byte) 0xAA, 0x6B, 0x4A, 0x18, 0x00, (byte) 0xC3, (byte) 0xC3, (byte) 0x9A, 0x59,
                    0x2A, 0x1B, (byte) 0xA7, (byte) 0xA1, (byte) 0x81, (byte) 0x88, (byte) 0x88, 0x58, (byte) 0xB2, (byte) 0xB1, 0x2B, (byte) 0x83, (byte) 0xD4, (byte) 0x81, 0x08, 0x0F,
                    0x00, 0x20, (byte) 0xC2, (byte) 0xE2, (byte) 0x80, 0x08, 0x1C, 0x29, 0x04, (byte) 0xB1, (byte) 0xA2, 0x01, 0x1C, (byte) 0x91, 0x00, 0x0C,
                    0x49, (byte) 0xB0, 0x43, (byte) 0xF2, (byte) 0x99, 0x39, 0x3F, 0x00, (byte) 0x81, (byte) 0x94, (byte) 0xC1, 0x09, 0x1A, 0x69, (byte) 0x90, (byte) 0x80,
                    (byte) 0x94, (byte) 0xAA, 0x20, 0x2A, (byte) 0x91, (byte) 0xB1, 0x39, 0x7A, 0x38, (byte) 0xD1, 0x10, (byte) 0x8A, (byte) 0x8C, 0x5A, 0x01, (byte) 0xB5,
                    (byte) 0x98, (byte) 0x80, 0x2A, 0x0B, 0x32, (byte) 0x92, (byte) 0xF1, (byte) 0x81, (byte) 0x9A, 0x23, (byte) 0x8A, (byte) 0xA3, (byte) 0xB7, 0x09, 0x03, 0x08,
                    (byte) 0xD0, (byte) 0x94, (byte) 0x9A, 0x09, 0x01, (byte) 0x93, (byte) 0xB7, (byte) 0xC2, (byte) 0x8C, 0x3A, (byte) 0x83, (byte) 0x99, 0x05, (byte) 0xA0, 0x0B, 0x29,
                    (byte) 0x93, (byte) 0xE5, (byte) 0x80, (byte) 0x89, 0x38, (byte) 0x90, (byte) 0x8A, (byte) 0xD7, (byte) 0xA1, 0x19, 0x1B, 0x48, (byte) 0x98, (byte) 0x92, (byte) 0xC3, (byte) 0xA1,
                    0x09, 0x3F, 0x02, 0x0C, 0x22, (byte) 0xC3, (byte) 0xB2, (byte) 0xA1, 0x01, (byte) 0x9F, 0x4A, 0x01, (byte) 0xA3, (byte) 0xD3, (byte) 0xB0, 0x28,
                    0x3F, 0x29, 0x20, (byte) 0xA2, (byte) 0xC2, (byte) 0xB1, 0x08, 0x5A, (byte) 0x98, 0x13, (byte) 0xD2, (byte) 0xC1, 0x01, (byte) 0xB2, (byte) 0x80, 0x3D,
                    0x03, (byte) 0xC1, (byte) 0x89, (byte) 0x96, (byte) 0x90, (byte) 0x90, 0x3A, 0x1A, (byte) 0x9A, 0x32, (byte) 0xB6, (byte) 0xA2, (byte) 0x8E, 0x4A, 0x28, (byte) 0x8A,
                    (byte) 0x84, (byte) 0xA2, (byte) 0x8A, 0x2D, 0x49, 0x09, (byte) 0x88, 0x18, 0x30, (byte) 0x9D, 0x2C, 0x23, (byte) 0xB1, 0x0C, (byte) 0x92, 0x2D,
                    0x39, (byte) 0x82, (byte) 0xC4, 0x2E, 0x10, 0x1A, 0x10, (byte) 0xB9, 0x48, 0x19, 0x39, (byte) 0xBA, 0x34, (byte) 0xDA, 0x2D, 0x48,
                    0x1A, (byte) 0xA6, (byte) 0x98, (byte) 0x83, (byte) 0x9A, 0x1D, 0x38, 0x04, (byte) 0xD0, 0x18, (byte) 0x90, 0x2C, 0x11, (byte) 0x93, (byte) 0xD3, (byte) 0x9A,
                    0x11, 0x08, (byte) 0x82, (byte) 0xF1, 0x01, (byte) 0xA0, 0x2A, (byte) 0x93, (byte) 0xD3, (byte) 0xB4, (byte) 0xB8, (byte) 0x82, 0x2F, 0x11, (byte) 0xA3, (byte) 0xB3,
                    (byte) 0xA8, 0x3B, 0x09, 0x23, (byte) 0x96, (byte) 0xC8, 0x3B, 0x3F, (byte) 0x93, (byte) 0x82, (byte) 0xA1, (byte) 0x90, 0x3F, 0x28, (byte) 0x81, (byte) 0xD1,
                    (byte) 0x93, 0x08, 0x2D, 0x18, (byte) 0x91, (byte) 0xB3, (byte) 0xB5, (byte) 0x98, 0x2A, 0x2B, (byte) 0x84, (byte) 0xB1, 0x5B, (byte) 0x8A, 0x31, 0x18,
                    (byte) 0x80, (byte) 0x8B, 0x7E, 0x39, 0x2B, 0x02, (byte) 0xC1, (byte) 0x8B, 0x6C, 0x49, 0x09, 0x10, (byte) 0xA1, 0x08, 0x01, 0x0C,
                    0x20, (byte) 0xA1, 0x09, 0x4F, 0x18, 0x00, 0x01, (byte) 0xA0, 0x5C, 0x1B, 0x5B, 0x10, (byte) 0x92, (byte) 0x90, 0x2B, 0x5A,
                    0x3D, 0x18, (byte) 0x91, 0x19, (byte) 0x98, 0x2D, 0x39, (byte) 0x89, 0x2D, 0x3A, 0x48, 0x2C, 0x11, (byte) 0xB5, (byte) 0x9A, 0x19,
                    0x5B, 0x28, (byte) 0x90, (byte) 0x95, (byte) 0x98, (byte) 0x89, 0x2B, 0x40, 0x08, (byte) 0x90, (byte) 0xF3, 0x0A, 0x08, (byte) 0xA6, (byte) 0x80, (byte) 0x91,
                    (byte) 0xB2, (byte) 0xA0, 0x02, (byte) 0xF2, (byte) 0xA1, (byte) 0xB7, (byte) 0x89, (byte) 0x81, (byte) 0x82, (byte) 0x91, (byte) 0xB1, 0x21, (byte) 0xAB, 0x32, (byte) 0xE9, 0x04,
                    (byte) 0xA2, (byte) 0x8D, 0x12, (byte) 0x91, (byte) 0xA3, (byte) 0xA3, (byte) 0xD2, (byte) 0x8B, 0x39, (byte) 0xD1, (byte) 0x84, (byte) 0xE2, (byte) 0x90, 0x00, 0x2B, 0x29,
                    (byte) 0xA3, (byte) 0xD4, (byte) 0xA1, (byte) 0x91, 0x1D, 0x5A, 0x08, 0x19, 0x11, (byte) 0x99, 0x08, 0x18, 0x49, 0x0F, 0x18, 0x10,
                    (byte) 0x82, (byte) 0xF1, 0x00, (byte) 0x89, 0x2F, 0x3A, 0x01, (byte) 0xB3, (byte) 0xC2, (byte) 0x81, 0x3F, 0x29, 0x08, 0x10, (byte) 0xA1, (byte) 0xA1,
                    0x3B, 0x5D, 0x19, 0x28, 0x0B, 0x38, (byte) 0x82, (byte) 0x91, 0x19, (byte) 0xBD, 0x3B, 0x7A, (byte) 0x80, 0x12, (byte) 0xB3, (byte) 0xE0,
                    0x0B, 0x6A, 0x01, (byte) 0x88, (byte) 0xA4, 0x08, 0x0B, 0x08, 0x59, (byte) 0x80, (byte) 0x80, 0x1D, 0x49, (byte) 0x89, 0x00, (byte) 0x84,
                    (byte) 0x99, 0x1A, 0x2B, 0x32, (byte) 0xE3, (byte) 0xB4, (byte) 0xA9, 0x3A, (byte) 0x99, 0x31, (byte) 0xE3, (byte) 0xAA, 0x58, 0x3B, (byte) 0x88, (byte) 0x95,
                    (byte) 0xC0, 0x18, 0x4A, 0x09, 0x30, (byte) 0xF2, (byte) 0xA3, 0x1C, 0x1B, 0x49, 0x00, (byte) 0xD3, (byte) 0xB2, (byte) 0xA0, 0x18, 0x11,
                    (byte) 0x92, (byte) 0xD3, (byte) 0xB2, (byte) 0x91, (byte) 0x80, (byte) 0xE7, (byte) 0xA1, (byte) 0x91, (byte) 0x98, 0x19, 0x22, (byte) 0xC2, (byte) 0xD2, 0x18, (byte) 0x8D, 0x3B,
                    0x10, (byte) 0xA5, (byte) 0x91, (byte) 0x98, 0x02, 0x3E, (byte) 0x80, 0x01, (byte) 0x90, (byte) 0xAA, 0x13, (byte) 0xF1, 0x02, (byte) 0xD1, 0x08, 0x19,
                    0x49, (byte) 0xB4, (byte) 0x91, (byte) 0xB4, (byte) 0x99, 0x2A, 0x0C, 0x32, (byte) 0xC0, 0x05, (byte) 0x88, 0x0B, (byte) 0x80, 0x2C, (byte) 0x81, 0x10,
                    0x0B, 0x51, (byte) 0xA9, 0x19, 0x05, (byte) 0xBF, 0x28, 0x20, (byte) 0xE1, (byte) 0x90, (byte) 0x80, 0x28, 0x19, 0x08, 0x26, (byte) 0xB1,
                    (byte) 0xA1, 0x18, (byte) 0x88, 0x2A, (byte) 0xF0, 0x12, (byte) 0x8A, (byte) 0xB3, 0x14, 0x1B, (byte) 0xD4, (byte) 0xD8, 0x10, 0x08, (byte) 0x8A, 0x17,
                    (byte) 0xA0, (byte) 0x98, 0x2B, 0x3A, 0x29, 0x48, (byte) 0xA4, (byte) 0x99, 0x0E, 0x4A, 0x12, (byte) 0x8B, 0x31, (byte) 0x8B, 0x4E, 0x1A,
                    0x11, (byte) 0xB5, (byte) 0x89, (byte) 0x91, 0x29, (byte) 0x89, (byte) 0xC2, (byte) 0x97, (byte) 0x90, 0x0A, 0x19, 0x11, (byte) 0x91, (byte) 0xC1, (byte) 0xD5, 0x08,
                    (byte) 0x89, 0x20, (byte) 0x91, (byte) 0xB1, 0x1A, 0x2D, 0x18, 0x29, (byte) 0xD2, 0x3B, 0x3E, 0x3A, 0x2A, (byte) 0x90, (byte) 0x82, 0x1C,
                    0x49, 0x3B, (byte) 0x93, (byte) 0xB6, (byte) 0xC8, 0x4C, 0x02, (byte) 0x91, (byte) 0x93, (byte) 0xF2, (byte) 0x88, 0x2D, 0x28, (byte) 0x81, (byte) 0x82, (byte) 0xC1,
                    (byte) 0x89, 0x2D, 0x6B, 0x19, (byte) 0x82, (byte) 0x80, 0x18, (byte) 0x8B, 0x39, 0x39, (byte) 0xC8, 0x3A, 0x6A, 0x0A, 0x22, (byte) 0xD2,
                    0x09, 0x2C, 0x1A, 0x68, (byte) 0x92, (byte) 0xE2, (byte) 0x89, 0x2A, 0x2A, 0x30, (byte) 0xC2, (byte) 0xA3, (byte) 0xB4, 0x1D, 0x2A, 0x09,
                    (byte) 0x93, 0x18, (byte) 0xF2, (byte) 0x89, 0x28, (byte) 0xB3, 0x01, (byte) 0x8F, 0x18, 0x11, (byte) 0xA1, (byte) 0x93, (byte) 0x90, (byte) 0xD1, 0x7A, 0x20,
                    (byte) 0xC3, (byte) 0xA2, (byte) 0xA8, (byte) 0x88, 0x1D, 0x28, (byte) 0xA5, (byte) 0xA2, (byte) 0xA2, 0x0B, 0x29, 0x2B, (byte) 0x87, (byte) 0xC1, (byte) 0x80, 0x0A,
                    0x19, 0x01, 0x12, (byte) 0xF1, 0x10, (byte) 0x80, 0x0A, 0x18, 0x08, 0x2F, 0x4A, 0x02, (byte) 0x89, 0x1B, 0x29, 0x5D,
                    0x4C, 0x08, (byte) 0x82, (byte) 0xA1, 0x0A, 0x3A, 0x4B, 0x29, (byte) 0xC6, (byte) 0xC3, 0x09, 0x09, (byte) 0x88, 0x39, (byte) 0x98, (byte) 0x82,
                    (byte) 0xA5, 0x1A, 0x30, 0x11, (byte) 0xBD, 0x3F, 0x12, (byte) 0x8B, 0x28, (byte) 0xC3, (byte) 0x88, 0x3F, 0x2B, 0x3B, 0x48, (byte) 0xA1,
                    (byte) 0x80, (byte) 0x8A, 0x4D, 0x39, 0x01, (byte) 0x93, (byte) 0xA2, (byte) 0xF1, 0x19, 0x19, 0x0A, 0x02, (byte) 0xB2, (byte) 0x8B, 0x24, (byte) 0xD2,
                    0x4B, 0x12, (byte) 0xC8, 0x2E, 0x10, (byte) 0xB5, (byte) 0x89, 0x01, 0x09, 0x1C, 0x2A, 0x03, (byte) 0xD4, (byte) 0x91, (byte) 0x98, (byte) 0x99,
                    0x11, 0x2B, (byte) 0xE4, 0x00, 0x00, 0x01, (byte) 0xE0, (byte) 0xA5, (byte) 0x89, (byte) 0x99, 0x31, 0x18, (byte) 0xD0, (byte) 0xB7, (byte) 0x98, 0x18,
                    0x0A, 0x10, (byte) 0x94, (byte) 0xC2, (byte) 0x90, 0x18, 0x00, (byte) 0x99, (byte) 0x87, (byte) 0xA0, (byte) 0x90, 0x2A, 0x3C, 0x02, (byte) 0xB8, (byte) 0xC1,
                    0x79, 0x1A, 0x20, 0x08, (byte) 0xA1, (byte) 0xD2, 0x1C, 0x29, 0x03, (byte) 0xD1, 0x29, (byte) 0x99, 0x2C, 0x50, (byte) 0xB3, (byte) 0xD1,
                    0x08, 0x09, 0x3C, 0x10, 0x04, (byte) 0xB2, 0x0D, 0x2B, 0x59, (byte) 0x80, (byte) 0x90, 0x01, 0x0F, 0x3A, 0x18, 0x01,
                    (byte) 0xA2, (byte) 0x9B, 0x5B, 0x3D, (byte) 0x81, 0x03, (byte) 0xD2, (byte) 0x98, 0x59, (byte) 0x90, (byte) 0x81, (byte) 0x92, (byte) 0xB4, (byte) 0x8B, 0x1B, 0x40,
                    (byte) 0xB2, (byte) 0xB5, 0x08, 0x4B, 0x01, 0x09, (byte) 0xD1, (byte) 0x91, (byte) 0x8B, 0x7A, 0x10, (byte) 0xB3, (byte) 0xC3, (byte) 0x99, 0x49, 0x1A,
                    0x29, (byte) 0xB5, (byte) 0xA2, (byte) 0xAB, 0x40, (byte) 0x81, 0x19, (byte) 0xB7, (byte) 0xB0, 0x20, 0x2B, (byte) 0xD4, (byte) 0x88, (byte) 0xA1, (byte) 0x91, 0x3C,
                    (byte) 0x82, 0x37, (byte) 0xD3, (byte) 0xB1, (byte) 0x8A, 0x1B, 0x30, (byte) 0xB3, (byte) 0xF4, (byte) 0xA1, (byte) 0x91, 0x09, 0x10, 0x03, (byte) 0xD0, (byte) 0x83,
                    (byte) 0xA9, (byte) 0x8F, 0x10, 0x01, (byte) 0x90, 0x18, (byte) 0x80, 0x20, 0x2B, (byte) 0xF1, 0x28, (byte) 0x99, 0x2A, 0x41, (byte) 0xF0, 0x12,
                    (byte) 0xAA, (byte) 0x83, (byte) 0x82, (byte) 0xD1, (byte) 0xC1, 0x08, (byte) 0x89, 0x59, 0x09, (byte) 0x83, (byte) 0x87, (byte) 0xB0, 0x2A, 0x4D, 0x18, 0x09,
                    0x19, (byte) 0xB3, 0x4B, 0x3F, 0x39, 0x19, 0x09, 0x01, (byte) 0x89, 0x03, 0x1F, 0x00, 0x1A, 0x0B, 0x10, 0x68,
                    (byte) 0xA0, 0x18, (byte) 0x8C, 0x6A, 0x09, 0x08, (byte) 0x97, (byte) 0xA1, (byte) 0x81, 0x1B, 0x2B, 0x4C, 0x03, (byte) 0xB4, (byte) 0xA8, (byte) 0x92,
                    0x4B, 0x3C, (byte) 0xA1, (byte) 0x81, (byte) 0x95, (byte) 0xA8, (byte) 0x81, 0x12, (byte) 0xBB, (byte) 0x92, 0x45, (byte) 0xB9, (byte) 0x93, (byte) 0xF4, (byte) 0x88, 0x0A,
                    0x2D, 0x28, 0x00, (byte) 0xA3, (byte) 0xA3, (byte) 0x8A, 0x3F, 0x48, (byte) 0xB1, (byte) 0x92, (byte) 0xB4, (byte) 0xA8, 0x30, (byte) 0x80, (byte) 0xD3, (byte) 0x80,
                    (byte) 0xD1, 0x19, 0x3B, (byte) 0xC4, (byte) 0x81, (byte) 0xC1, 0x29, 0x0D, 0x20, 0x13, (byte) 0xC8, (byte) 0xB4, 0x4C, 0x09, 0x00, (byte) 0x82,
                    (byte) 0xC2, 0x3B, 0x0D, 0x30, 0x0B, 0x12, (byte) 0xF0, 0x1B, 0x20, 0x0A, (byte) 0xA6, (byte) 0x80, 0x0A, 0x4A, 0x4A, (byte) 0x80,
                    (byte) 0x94, (byte) 0xB1, 0x2E, 0x3B, 0x1A, 0x10, (byte) 0x93, 0x10, 0x4C, 0x3D, 0x08, (byte) 0x82, (byte) 0xC9, 0x19, 0x6A, 0x2B,
                    0x38, (byte) 0xD1, 0x08, 0x19, 0x2A, 0x5A, (byte) 0x82, (byte) 0xB1, (byte) 0x8D, 0x29, 0x78, 0x09, (byte) 0x82, 0x0A, 0x2C, 0x1B,
                    0x19, 0x41, (byte) 0xB8, (byte) 0x8C, 0x79, 0x2B, 0x11, (byte) 0x88, (byte) 0x82, (byte) 0x91, (byte) 0xDC, 0x28, 0x11, (byte) 0xB0, 0x11, 0x18,
                    (byte) 0xC9, 0x62, (byte) 0xA1, (byte) 0x91, (byte) 0x98, 0x3B, 0x3A, (byte) 0xB0, (byte) 0xF4, 0x01, (byte) 0xC0, 0x29, 0x39, (byte) 0xF8, (byte) 0x95, (byte) 0x91,
                    (byte) 0x88, (byte) 0x88, (byte) 0x91, 0x03, (byte) 0xA1, (byte) 0xE2, 0x18, (byte) 0x82, (byte) 0xD1, (byte) 0xA2, (byte) 0xD1, (byte) 0x80, 0x19, 0x20, (byte) 0x83, (byte) 0xB1,
                    (byte) 0xE3, (byte) 0x80, (byte) 0x91, 0x4D, 0x1A, 0x03, (byte) 0xB2, 0x09, 0x18, (byte) 0xD1, 0x19, 0x09, (byte) 0x92, (byte) 0xA6, (byte) 0xA0, (byte) 0xB6,
                    (byte) 0xB2, (byte) 0x8B, 0x38, 0x10, 0x42, (byte) 0xD3, (byte) 0xD0, (byte) 0xA8, 0x20, 0x2C, 0x10, 0x01, (byte) 0xB1, (byte) 0xB4, (byte) 0xAB, 0x5B,
                    0x79, (byte) 0x80, 0x10, 0x1A, (byte) 0xA8, 0x3D, 0x18, 0x20, (byte) 0xB3, (byte) 0x8F, 0x18, 0x01, 0x00, 0x09, (byte) 0xF3, (byte) 0x89,
                    0x69, (byte) 0x88, (byte) 0x81, (byte) 0x91, 0x08, (byte) 0xE1, 0x1A, 0x08, 0x11, (byte) 0x81, 0x1E, 0x29, (byte) 0xA0, 0x01, 0x00, (byte) 0x90,
                    0x3E, 0x7B, 0x18, (byte) 0x82, (byte) 0xC3, (byte) 0xA1, 0x2A, 0x2C, 0x5B, (byte) 0x81, (byte) 0xA5, (byte) 0x90, (byte) 0x81, 0x00, 0x0B, 0x1A,
                    0x1C, 0x2C, 0x32, (byte) 0xC0, (byte) 0xF3, (byte) 0x80, 0x2D, 0x2A, 0x10, 0x02, (byte) 0xE4, (byte) 0xC1, (byte) 0x89, 0x4A, 0x09, 0x01,
                    0x03, (byte) 0xD2, (byte) 0x98, 0x2A, 0x39, (byte) 0x8A, (byte) 0x89, 0x26, (byte) 0xB1, (byte) 0xB2, 0x12, (byte) 0xC0, 0x0A, 0x5A, 0x18, (byte) 0x98,
                    (byte) 0xF3, (byte) 0x92, (byte) 0x99, (byte) 0x99, 0x79, 0x01, (byte) 0xB5, (byte) 0xA1, (byte) 0x80, (byte) 0x80, (byte) 0x90, (byte) 0x83, (byte) 0xA0, (byte) 0xE2, (byte) 0x81, 0x29,
                    (byte) 0x93, (byte) 0x8A, 0x0A, 0x6A, 0x1F, 0x18, 0x02, (byte) 0xC8, 0x01, 0x19, 0x3B, 0x4A, (byte) 0x98, 0x17, (byte) 0xA8, 0x0D,
                    0x38, (byte) 0xA1, (byte) 0x91, 0x10, (byte) 0xA2, 0x2B, 0x4C, (byte) 0xA6, (byte) 0x81, (byte) 0xBA, 0x21, 0x4C, (byte) 0x80, 0x21, (byte) 0xD1, (byte) 0x92,
                    0x2C, 0x08, 0x30, (byte) 0x9F, (byte) 0x93, 0x2A, (byte) 0x89, 0x03, (byte) 0x8B, (byte) 0x87, 0x0A, 0x0D, 0x12, (byte) 0x98, (byte) 0xA4, (byte) 0x93,
                    (byte) 0xBB, 0x59, 0x18, (byte) 0xA1, 0x32, (byte) 0xE9, (byte) 0x84, 0x08, (byte) 0x8A, 0x02, (byte) 0xA1, (byte) 0x91, 0x4B, (byte) 0xB4, 0x20, (byte) 0x88,
                    (byte) 0xF0, 0x3A, 0x1A, (byte) 0x88, (byte) 0x87, (byte) 0xB1, (byte) 0x92, 0x0A, 0x08, 0x6B, (byte) 0x83, (byte) 0xC3, (byte) 0x91, (byte) 0xC0, 0x2B, 0x79,
                    0x08, (byte) 0x8A, (byte) 0x84, (byte) 0xA0, (byte) 0x89, 0x40, 0x1B, (byte) 0xA1, 0x39, (byte) 0x98, 0x17, (byte) 0xC2, (byte) 0xA2, 0x12, (byte) 0xCD, 0x20,
                    (byte) 0x89, (byte) 0x92, 0x25, (byte) 0xB0, 0x2D, 0x3A, (byte) 0x8B, 0x58, 0x2A, (byte) 0xA0, 0x4C, 0x08, 0x30, (byte) 0xAE, (byte) 0x82, 0x59,
                    (byte) 0x89, 0x1A, 0x10, (byte) 0xC2, 0x18, 0x2C, 0x40, 0x1E, 0x01, (byte) 0xA3, (byte) 0x8A, (byte) 0x81, 0x2C, 0x29, 0x29, (byte) 0xA9,
                    0x13, 0x51, (byte) 0xAD, 0x12, (byte) 0x89, (byte) 0x8F, 0x18, 0x2C, 0x39, 0x00, (byte) 0xC1, 0x10, 0x3C, 0x2A, 0x41, (byte) 0xC8,
                    (byte) 0xA2, (byte) 0x91, 0x0A, 0x6C, 0x10, 0x12, (byte) 0x88, (byte) 0xE8, 0x30, (byte) 0x91, (byte) 0x81, (byte) 0xD8, 0x01, 0x1B, 0x0D, 0x07,
                    0x00, (byte) 0xA8, (byte) 0x92, 0x0A, 0x28, (byte) 0xD2, (byte) 0xC3, 0x02, (byte) 0xAA, (byte) 0x94, (byte) 0x81, (byte) 0xB4, (byte) 0xB3, 0x1A, 0x0B, 0x13,
                    (byte) 0xF9, 0x16, (byte) 0xA1, (byte) 0x8A, 0x59, 0x19, 0x02, (byte) 0xC1, (byte) 0x91, (byte) 0x8B, 0x3D, 0x18, 0x3B, (byte) 0xA4, (byte) 0x94, (byte) 0x80,
                    (byte) 0x99, (byte) 0x88, 0x1C, 0x79, 0x0A, 0x02, 0x03, (byte) 0xF8, (byte) 0x90, 0x39, 0x5B, 0x19, 0x02, (byte) 0xC3, (byte) 0x90, (byte) 0xBB,
                    0x58, 0x6A, 0x09, 0x02, (byte) 0x89, (byte) 0x91, (byte) 0x88, 0x1A, 0x69, (byte) 0x8A, 0x19, 0x15, (byte) 0xA0, (byte) 0xA2, 0x00, (byte) 0x9A,
                    0x6B, 0x49, (byte) 0x88, (byte) 0xA3, (byte) 0x92, (byte) 0xBB, 0x6B, 0x3D, 0x38, 0x01, (byte) 0x98, (byte) 0x91, 0x3F, 0x09, 0x18, 0x20,
                    (byte) 0x90, (byte) 0x80, (byte) 0xAC, 0x70, (byte) 0x91, (byte) 0x9B, 0x51, 0x09, (byte) 0x88, (byte) 0x99, 0x14, (byte) 0x8B, (byte) 0x98, (byte) 0x83, 0x79, (byte) 0xA0,
                    (byte) 0x99, 0x13, 0x01, 0x19, (byte) 0xE0, (byte) 0x83, 0x0B, (byte) 0xB0, 0x0C, 0x31, (byte) 0x95, (byte) 0xB5, (byte) 0xC2, (byte) 0x8A, 0x39, 0x20,
                    (byte) 0x80, 0x39, (byte) 0xF3, (byte) 0xB1, 0x10, (byte) 0x88, 0x5E, 0x18, (byte) 0x94, (byte) 0xA1, (byte) 0x88, (byte) 0xA1, (byte) 0x98, 0x15, (byte) 0xAA, 0x39,
                    (byte) 0xD4, (byte) 0x84, (byte) 0xC0, (byte) 0xA2, (byte) 0xA2, 0x0C, (byte) 0x81, (byte) 0x86, (byte) 0xB5, (byte) 0xA1, (byte) 0xB1, 0x14, 0x1B, (byte) 0xB1, 0x02, (byte) 0x92,
                    (byte) 0xC3, (byte) 0xE0, (byte) 0x88, 0x11, (byte) 0xAA, 0x69, 0x18, (byte) 0x81, (byte) 0xA3, (byte) 0xB0, 0x01, (byte) 0xBF, 0x2A, 0x31, (byte) 0x93, (byte) 0xF1,
                    0x00, (byte) 0x89, 0x18, 0x19, 0x11, (byte) 0xD3, (byte) 0xE0, 0x10, 0x18, (byte) 0xB1, 0x18, 0x24, (byte) 0x9A, 0x2B, (byte) 0xA4, (byte) 0xC0,
                    (byte) 0xB0, 0x31, 0x6C, 0x19, (byte) 0xB4, 0x12, (byte) 0xA8, (byte) 0xEA, 0x58, 0x10, (byte) 0x8B, (byte) 0x93, (byte) 0x82, (byte) 0x88, (byte) 0x9A, 0x41,
                    0x10, (byte) 0xC3, (byte) 0xEA, 0x41, (byte) 0xA9, (byte) 0x9C, 0x34, (byte) 0xA1, 0x2A, 0x79, (byte) 0xA2, 0x01, (byte) 0xA8, (byte) 0xB3, 0x28, (byte) 0xCC,
                    0x41, (byte) 0x9A, (byte) 0xB3, 0x4B, (byte) 0xB3, 0x27, (byte) 0x8B, (byte) 0x83, 0x2B, 0x2F, 0x08, 0x28, (byte) 0xB2, (byte) 0x80, 0x2C, 0x30,
                    0x5E, 0x09, 0x12, (byte) 0x9B, 0x09, 0x22, 0x5B, 0x19, (byte) 0x8A, 0x11, 0x59, (byte) 0x99, (byte) 0xA4, 0x32, (byte) 0xCD, 0x18,
                    0x08, 0x10, (byte) 0x85, (byte) 0xB3, (byte) 0xB4, 0x1E, (byte) 0x88, 0x28, (byte) 0x8A, 0x11, 0x09, (byte) 0xC0, 0x79, (byte) 0x80, (byte) 0x91, 0x3B,
                    (byte) 0x80, 0x10, 0x0F, 0x01, (byte) 0x80, (byte) 0x91, 0x19, 0x3D, (byte) 0x92, 0x28, (byte) 0xA8, 0x37, (byte) 0x9A, 0x0A, 0x3A, (byte) 0x8A,
                    0x45, (byte) 0xA9, (byte) 0xA4, 0x00, (byte) 0xAA, 0x09, 0x3D, 0x59, 0x20, (byte) 0xE1, 0x08, (byte) 0x98, (byte) 0x90, 0x59, 0x10, 0x09,
                    (byte) 0xA3, (byte) 0xC3, (byte) 0x93, (byte) 0x99, 0x2B, 0x69, 0x11, (byte) 0xD1, (byte) 0xB1, (byte) 0xA4, (byte) 0x91, 0x3C, (byte) 0x89, (byte) 0x83, (byte) 0xF0, 0x10,
                    (byte) 0x91, (byte) 0xA1, (byte) 0x89, 0x59, 0x05, (byte) 0x99, (byte) 0x93, (byte) 0x94, (byte) 0xC8, 0x08, 0x0A, 0x09, 0x17, (byte) 0xB1, (byte) 0x83, (byte) 0xC1,
                    (byte) 0x91, 0x40, (byte) 0xA2, (byte) 0xC2, (byte) 0x98, (byte) 0xC3, (byte) 0xBA, 0x28, 0x23, 0x0F, (byte) 0x80, 0x50, (byte) 0xB8, 0x19, 0x10, (byte) 0x96,
                    (byte) 0x98, (byte) 0x8C, 0x05, (byte) 0x98, 0x19, 0x29, 0x2B, 0x3B, 0x0A, (byte) 0xE2, 0x01, 0x0F, 0x3C, 0x38, 0x08, 0x09,
                    (byte) 0x81, 0x4A, 0x6C, 0x08, 0x00, (byte) 0x88, (byte) 0x98, 0x38, 0x2C, 0x5A, 0x1B, 0x20, 0x1A, 0x39, (byte) 0xB0, 0x09,
                    (byte) 0xCB, 0x5B, 0x49, 0x09, 0x71, 0x00, (byte) 0xC1, 0x0E, 0x08, 0x38, 0x0C, 0x02, 0x10, 0x0E, 0x10, (byte) 0x8A,
                    0x48, 0x19, (byte) 0x90, (byte) 0x92, 0x0D, (byte) 0xA3, (byte) 0x98, 0x3B, 0x79, 0x19, 0x01, 0x10, (byte) 0xE1, (byte) 0x80, 0x19, 0x2B,
                    0x10, (byte) 0xF2, 0x02, (byte) 0xAB, (byte) 0x84, (byte) 0x9A, 0x29, (byte) 0xB4, (byte) 0x80, (byte) 0x92, 0x03, (byte) 0x88, (byte) 0x95, (byte) 0xD0, 0x03, (byte) 0x90,
                    (byte) 0xA0, (byte) 0xC7, (byte) 0xA1, (byte) 0xB0, (byte) 0xA2, 0x02, 0x18, (byte) 0xB5, (byte) 0xD4, 0x01, (byte) 0xC0, 0x08, (byte) 0xA2, (byte) 0x93, (byte) 0xA8, (byte) 0xA0,
                    (byte) 0xC3, 0x20, (byte) 0xF3, (byte) 0x90, 0x00, (byte) 0xD5, 0x08, (byte) 0x89, (byte) 0xA5, (byte) 0x80, (byte) 0xA0, (byte) 0x81, (byte) 0x82, (byte) 0xC2, 0x09, (byte) 0xD1,
                    0x13, (byte) 0xCB, 0x03, (byte) 0x84, (byte) 0x91, (byte) 0xE1, 0x1B, 0x12, 0x08, (byte) 0xAB, (byte) 0x87, 0x18, (byte) 0xAB, 0x58, (byte) 0x89, 0x28,
                    (byte) 0x81, (byte) 0xC9, 0x33, (byte) 0xA9, (byte) 0x80, 0x2E, 0x20, (byte) 0x83, (byte) 0xB9, 0x20, 0x3B, (byte) 0x9E, 0x7A, 0x08, (byte) 0x81, 0x18,
                    0x0B, (byte) 0x88, 0x79, (byte) 0x80, (byte) 0x8B, 0x00, 0x12, 0x0E, (byte) 0x89, 0x51, 0x1B, (byte) 0x81, (byte) 0xA0, 0x3A, 0x01, (byte) 0xAF,
                    0x11, 0x28, (byte) 0xBA, 0x35, (byte) 0x98, (byte) 0x88, 0x52, (byte) 0xC0, (byte) 0x83, 0x2F, (byte) 0xA9, 0x11, 0x0A, 0x19, 0x25, (byte) 0xD0,
                    0x30, (byte) 0x9C, 0x08, 0x21, (byte) 0x98, (byte) 0x81, 0x2A, (byte) 0xF3, 0x2A, (byte) 0x80, (byte) 0xB6, 0x2B, 0x08, (byte) 0x93, (byte) 0xE9, 0x02,
                    (byte) 0x81, (byte) 0x8C, 0x21, 0x00, (byte) 0xA6, (byte) 0xA9, (byte) 0x94, 0x01, (byte) 0x8F, (byte) 0x80, (byte) 0x94, (byte) 0x98, (byte) 0x93, (byte) 0xB4, 0x00, 0x08,
                    (byte) 0xC0, 0x14, (byte) 0x98, (byte) 0xB3, (byte) 0xB4, (byte) 0xC1, 0x09, 0x18, (byte) 0xA7, 0x00, (byte) 0xA3, (byte) 0xC8, 0x0A, 0x3C, 0x19, (byte) 0x96,
                    (byte) 0x83, (byte) 0xC1, (byte) 0x99, 0x19, 0x4A, (byte) 0x85, (byte) 0x80, (byte) 0xC1, (byte) 0x91, (byte) 0x99, (byte) 0x90, 0x2A, 0x17, (byte) 0x95, (byte) 0x99, (byte) 0x88,
                    0x12, (byte) 0xAE, 0x39, 0x08, (byte) 0x92, (byte) 0x84, (byte) 0xB0, (byte) 0xA8, 0x79, 0x09, 0x19, 0x01, (byte) 0xB2, (byte) 0xA3, (byte) 0x8F, 0x28,
                    0x2B, (byte) 0xA2, 0x40, (byte) 0x82, (byte) 0xA0, 0x4C, (byte) 0xA9, 0x39, (byte) 0x8D, (byte) 0x81, 0x70, (byte) 0x88, (byte) 0xA0, 0x1A, 0x49, 0x2D,
                    0x1A, 0x26, (byte) 0xA8, (byte) 0x98, 0x08, 0x29, 0x0B, 0x12, (byte) 0x96, (byte) 0xB1, (byte) 0xB2, 0x3A, 0x13, (byte) 0x9B, 0x60, (byte) 0xA0,
                    (byte) 0x88, (byte) 0xB2, 0x34, (byte) 0xEA, 0x1A, 0x2A, 0x79, (byte) 0x98, 0x10, 0x04, (byte) 0x8C, 0x1C, (byte) 0x81, 0x04, (byte) 0x8C, (byte) 0x83,
                    0x19, 0x2F, (byte) 0x81, (byte) 0x93, (byte) 0x98, 0x10, 0x08, 0x30, 0x2A, (byte) 0xFA, 0x05, 0x08, 0x2A, (byte) 0x89, (byte) 0x91, (byte) 0xA3,
                    (byte) 0xFA, 0x11, 0x11, 0x00, (byte) 0x8C, 0x04, (byte) 0x8A, 0x2A, (byte) 0xB5, 0x10, (byte) 0xA9, (byte) 0xC2, 0x3D, 0x1B, 0x32, 0x04,
                    0x0A, 0x1A, 0x09, 0x40, 0x1F, (byte) 0x92, 0x1D, 0x2A, (byte) 0x91, 0x10, 0x30, 0x2F, 0x0B, 0x68, (byte) 0x99, (byte) 0xA2,
                    (byte) 0x92, (byte) 0x88, 0x78, (byte) 0xA9, 0x20, 0x28, (byte) 0xE2, (byte) 0x92, 0x1A, (byte) 0x99, 0x4B, 0x19, 0x22, (byte) 0xA1, (byte) 0xE2, 0x21,
                    0x2F, (byte) 0x98, 0x29, 0x18, (byte) 0x91, 0x08, (byte) 0xB0, 0x79, 0x1A, (byte) 0x82, 0x3B, (byte) 0xB1, (byte) 0xA7, (byte) 0x8A, (byte) 0xB3, (byte) 0x98,
                    0x5B, 0x23, (byte) 0xCA, 0x42, (byte) 0x83, (byte) 0xF0, (byte) 0x90, 0x18, (byte) 0x98, 0x08, (byte) 0xB4, 0x20, (byte) 0xA3, (byte) 0xC0, 0x43, (byte) 0xD8,
                    (byte) 0x80, (byte) 0x81, (byte) 0xA3, (byte) 0x99, (byte) 0xD9, (byte) 0xA7, 0x19, (byte) 0x90, 0x10, 0x05, (byte) 0xB1, (byte) 0x8B, 0x02, (byte) 0xA4, (byte) 0xBD, 0x23,
                    (byte) 0x93, (byte) 0x8A, (byte) 0x99, 0x4B, 0x03, (byte) 0xC1, (byte) 0xF8, 0x38, 0x09, 0x2B, 0x14, (byte) 0xD0, 0x03, (byte) 0x8A, 0x2A, 0x39,
                    (byte) 0xB9, (byte) 0x97, (byte) 0x90, (byte) 0xAA, 0x50, 0x01, (byte) 0x99, 0x51, (byte) 0xD1, 0x09, 0x1A, (byte) 0xB5, 0x00, (byte) 0x8B, (byte) 0x93, 0x08,
                    (byte) 0x98, 0x11, (byte) 0xF9, (byte) 0x85, 0x2B, 0x08, (byte) 0x96, (byte) 0x89, (byte) 0x90, 0x2A, 0x12, 0x4A, (byte) 0xD8, (byte) 0x85, 0x2B, 0x0E,
                    0x10, 0x00, 0x01, (byte) 0xB1, (byte) 0x9B, 0x69, 0x1A, (byte) 0x90, 0x40, (byte) 0xB8, 0x01, 0x08, 0x0A, 0x2C, 0x09, 0x14,
                    0x4B, (byte) 0xE2, (byte) 0x82, (byte) 0x88, (byte) 0xB1, 0x78, 0x0A, 0x01, (byte) 0xC2, (byte) 0x93, 0x19, (byte) 0xCE, 0x20, 0x3C, (byte) 0x82, (byte) 0xB4,
                    0x1B, 0x20, (byte) 0x8C, 0x3B, 0x29, (byte) 0xAB, (byte) 0x86, 0x23, (byte) 0xD8, (byte) 0x81, (byte) 0x9A, 0x5A, 0x49, (byte) 0xB0, 0x16, (byte) 0xA0,
                    (byte) 0xB0, 0x28, 0x1B, 0x13, (byte) 0x93, (byte) 0xE4, (byte) 0xA2, (byte) 0xA9, 0x08, 0x5A, (byte) 0xB3, 0x12, (byte) 0xC1, (byte) 0xE1, 0x10, (byte) 0x88,
                    0x01, 0x0C, (byte) 0x92, 0x08, (byte) 0x89, (byte) 0xB7, (byte) 0x88, (byte) 0x81, 0x10, (byte) 0x9A, 0x17, (byte) 0xA0, (byte) 0xB0, 0x13, (byte) 0x99, (byte) 0xE0,
                    0x39, 0x31, (byte) 0xD2, (byte) 0xB2, (byte) 0x80, 0x0B, 0x2D, 0x49, (byte) 0x80, 0x01, (byte) 0xB0, 0x06, 0x09, 0x0C, 0x3A, 0x69,
                    (byte) 0xA0, 0x08, (byte) 0xB2, (byte) 0xA1, 0x69, 0x2B, 0x5A, (byte) 0x81, (byte) 0x92, (byte) 0xBA, 0x21, (byte) 0xB1, 0x7D, 0x10, (byte) 0x80, 0x08,
                    (byte) 0x88, (byte) 0x82, 0x32, 0x0D, (byte) 0xB0, 0x1A, 0x1C, 0x21, (byte) 0x94, (byte) 0xA9, 0x58, (byte) 0xB9, 0x5A, 0x4A, (byte) 0xA0, 0x13,
                    (byte) 0xA9, (byte) 0x80, 0x7C, 0x00, 0x20, (byte) 0x8A, 0x04, 0x0C, 0x00, (byte) 0x82, 0x2A, (byte) 0xB2, (byte) 0xAC, 0x4B, 0x69, (byte) 0xA0,
                    (byte) 0xA6, (byte) 0x81, (byte) 0x9B, 0x19, 0x38, (byte) 0x8B, 0x17, (byte) 0xB2, (byte) 0x81, 0x2A, (byte) 0xBB, (byte) 0x94, 0x29, (byte) 0xA2, 0x15, (byte) 0xBA,
                    (byte) 0x97, (byte) 0xA3, (byte) 0xB9, 0x79, 0x01, (byte) 0xB2, 0x02, (byte) 0xF1, (byte) 0x90, 0x0A, 0x29, 0x11, (byte) 0x88, (byte) 0xE5, (byte) 0xA0, (byte) 0x81,
                    0x19, (byte) 0x91, (byte) 0x90, 0x28, (byte) 0xB3, 0x14, (byte) 0xD0, (byte) 0xB5, (byte) 0x91, (byte) 0x9A, 0x29, 0x0B, 0x07, (byte) 0xA2, (byte) 0xB3, 0x01,
                    (byte) 0x9D, 0x28, 0x41, (byte) 0xD0, (byte) 0x91, (byte) 0x90, (byte) 0x82, 0x1A, (byte) 0xA8, 0x44, (byte) 0x9A, (byte) 0xA9, 0x21, (byte) 0xE3, (byte) 0xA9, 0x4B,
                    0x19, 0x78, (byte) 0x89, (byte) 0x83, (byte) 0xA3, (byte) 0xB9, 0x5A, 0x3D, (byte) 0x80, (byte) 0x82, (byte) 0xA2, (byte) 0xA0, 0x6C, 0x10, 0x20, (byte) 0x8B,
                    (byte) 0x93, (byte) 0x8B, 0x0E, 0x33, (byte) 0xA9, (byte) 0xB1, 0x68, (byte) 0x8A, 0x31, (byte) 0xAC, (byte) 0x94, (byte) 0xB4, (byte) 0x8B, 0x32, 0x0B, (byte) 0xB4,
                    (byte) 0x81, (byte) 0x91, 0x1D, 0x33, (byte) 0xD9, 0x31, (byte) 0xE1, (byte) 0x8B, 0x3B, 0x30, 0x12, 0x49, (byte) 0xD2, (byte) 0x8E, 0x29, 0x18,
                    (byte) 0x8A, (byte) 0x92, 0x02, (byte) 0xAA, 0x59, 0x1C, 0x32, (byte) 0x88, 0x01, 0x23, (byte) 0xFB, (byte) 0x83, 0x29, (byte) 0xDA, 0x59, 0x01,
                    (byte) 0x81, (byte) 0x92, (byte) 0xE1, 0x18, (byte) 0x8A, 0x1D, 0x30, (byte) 0x93, (byte) 0xF1, 0x00, 0x01, 0x0B, 0x39, (byte) 0x92, (byte) 0x89, (byte) 0xA0,
                    0x11, 0x5B, (byte) 0xE0, (byte) 0x82, 0x09, 0x13, (byte) 0xAA, (byte) 0xB4, 0x16, (byte) 0xD8, (byte) 0x91, 0x2A, 0x29, (byte) 0x84, 0x1B, (byte) 0xC5,
                    (byte) 0x98, (byte) 0x98, 0x31, (byte) 0x98, (byte) 0x99, 0x17, (byte) 0xA9, 0x20, (byte) 0x92, (byte) 0xC3, 0x18, (byte) 0x9D, 0x20, 0x3D, (byte) 0x89, (byte) 0x94,
                    (byte) 0xA2, 0x1C, 0x5C, 0x29, 0x39, (byte) 0xA0, (byte) 0xB3, 0x00, 0x0C, 0x4C, 0x48, (byte) 0x92, 0x0A, (byte) 0x91, (byte) 0x85, (byte) 0x9A,
                    0x01, (byte) 0x82, 0x1F, 0x10, (byte) 0x99, 0x15, (byte) 0xC1, (byte) 0xA0, 0x39, 0x1A, 0x1D, (byte) 0x85, (byte) 0xB4, (byte) 0x90, 0x1A, 0x2A,
                    0x4B, 0x01, (byte) 0xB2, (byte) 0x93, (byte) 0xBE, 0x12, (byte) 0x83, (byte) 0xC9, 0x18, 0x09, 0x20, 0x78, (byte) 0xF1, 0x08, 0x19, (byte) 0x88,
                    0x3A, (byte) 0x83, (byte) 0xB3, (byte) 0xA9, (byte) 0x93, 0x7A, 0x0A, (byte) 0x96, (byte) 0x98, 0x00, (byte) 0xA8, 0x3A, 0x30, (byte) 0x92, (byte) 0xF2, (byte) 0x9B,
                    0x3D, 0x38, (byte) 0x92, (byte) 0x92, (byte) 0xC3, (byte) 0xB8, 0x6B, 0x29, 0x01, 0x01, (byte) 0xB2, 0x2F, 0x09, 0x19, 0x18, 0x01,
                    0x3B, 0x7B, 0x10, (byte) 0xA1, (byte) 0x90, 0x39, 0x0F, 0x38, 0x0A, (byte) 0xB5, (byte) 0xA4, (byte) 0x89, (byte) 0x8B, 0x6A, 0x2B, 0x12,
                    (byte) 0xC8, (byte) 0x90, 0x40, 0x2A, (byte) 0x9E, 0x22, (byte) 0x88, 0x18, 0x09, 0x3A, (byte) 0xC3, (byte) 0xE8, 0x09, 0x59, 0x08, 0x12,
                    (byte) 0x94, (byte) 0xD0, 0x1A, 0x2C, 0x38, 0x00, (byte) 0xA1, (byte) 0x83, (byte) 0xE8, 0x08, 0x3A, 0x08, 0x10, (byte) 0x9E, (byte) 0x83, 0x1D,
                    (byte) 0x92, 0x19, 0x2C, 0x39, 0x3B, 0x59, 0x04, (byte) 0xE1, (byte) 0x80, 0x08, (byte) 0x8D, 0x21, (byte) 0x81, (byte) 0xB2, (byte) 0xB2, 0x02,
                    (byte) 0x99, (byte) 0x91, (byte) 0xA4, (byte) 0xD6, (byte) 0x98, (byte) 0x99, 0x03, (byte) 0x80, (byte) 0x98, (byte) 0xA7, (byte) 0x91, 0x09, (byte) 0xA1, (byte) 0xB2, (byte) 0xB3, (byte) 0xE1,
                    0x12, (byte) 0x92, (byte) 0xB1, (byte) 0x81, 0x06, (byte) 0x99, 0x0A, 0x23, (byte) 0xC4, (byte) 0xB1, (byte) 0xF2, (byte) 0x89, 0x19, 0x3A, (byte) 0x94, (byte) 0x82,
                    (byte) 0xE0, (byte) 0x89, 0x38, 0x0B, (byte) 0xA4, (byte) 0xA5, (byte) 0x80, (byte) 0x80, (byte) 0x8C, 0x34, (byte) 0xB9, (byte) 0xA9, 0x23, 0x13, (byte) 0xB9, (byte) 0xC1,
                    (byte) 0xC7, 0x1B, (byte) 0x89, 0x10, 0x20, 0x11, (byte) 0xE3, (byte) 0xA8, 0x4B, 0x0B, 0x40, (byte) 0x91, (byte) 0x90, 0x1B, 0x5F, 0x2A,
                    0x18, (byte) 0x82, (byte) 0x91, 0x0B, 0x4A, 0x28, (byte) 0xCA, 0x40, (byte) 0x80, 0x5B, 0x2C, 0x13, (byte) 0xB0, (byte) 0x8A, (byte) 0xA9, 0x5A,
                    0x58, (byte) 0x89, (byte) 0x82, (byte) 0x88, 0x2E, 0x3B, 0x31, (byte) 0xA1, (byte) 0x9B, 0x01, 0x7A, 0x2C, 0x01, (byte) 0x91, (byte) 0x93, 0x3F,
                    (byte) 0x88, 0x39, 0x10, (byte) 0xF1, (byte) 0x91, (byte) 0x8B, 0x48, 0x0A, 0x12, (byte) 0xE3, (byte) 0xA8, 0x18, 0x28, (byte) 0x92, (byte) 0x97, (byte) 0x98,
                    (byte) 0x99, 0x19, (byte) 0xA1, 0x11, (byte) 0xB6, (byte) 0x88, 0x3B, 0x10, (byte) 0xD3, (byte) 0xC3, (byte) 0xA1, 0x2A, (byte) 0x8A, 0x49, 0x04, (byte) 0xF1,
                    (byte) 0x91, 0x02, (byte) 0x8A, (byte) 0x89, 0x04, (byte) 0xF1, (byte) 0x98, (byte) 0x80, 0x18, 0x12, (byte) 0xE3, (byte) 0x81, (byte) 0x98, (byte) 0x80, 0x01, (byte) 0xB3,
                    (byte) 0xF2, (byte) 0x99, 0x12, 0x2A, (byte) 0xB5, (byte) 0xB3, (byte) 0x92, (byte) 0xAA, 0x19, 0x50, (byte) 0xB2, (byte) 0xC3, (byte) 0x92, (byte) 0xD0, 0x2B, 0x68,
                    (byte) 0x93, (byte) 0x99, (byte) 0xC0, 0x2C, 0x3E, (byte) 0x80, 0x20, 0x08, (byte) 0x93, 0x0D, 0x2A, 0x31, (byte) 0x8D, 0x02, 0x2B, (byte) 0x91,
                    0x08, 0x0A, 0x03, 0x2C, 0x3C, 0x52, (byte) 0xB9, (byte) 0xA0, 0x12, (byte) 0xBF, 0x3A, 0x29, 0x01, (byte) 0x88, (byte) 0xC0, 0x6A,
                    0x3C, 0x0A, 0x49, 0x18, 0x0B, 0x39, 0x2B, 0x69, 0x0A, (byte) 0x84, 0x2A, 0x2A, 0x1C, 0x2A, (byte) 0xC3, (byte) 0x8C,
                    0x19, 0x50, 0x09, (byte) 0x91, (byte) 0xA7, (byte) 0x8D, 0x18, 0x1A, 0x28, 0x00, (byte) 0xA0, (byte) 0x94, 0x10, 0x1F, 0x20, (byte) 0x90,
                    (byte) 0x8A, 0x12, (byte) 0xD0, 0x1A, 0x5A, (byte) 0x81, 0x04, (byte) 0xBC, 0x23, 0x10, (byte) 0xE0, (byte) 0x90, (byte) 0x90, 0x18, 0x1A, (byte) 0xA6,
                    0x12, (byte) 0xB1, (byte) 0xD0, 0x4A, 0x08, (byte) 0x82, (byte) 0x92, (byte) 0xB6, (byte) 0x9A, 0x0A, 0x12, (byte) 0x88, (byte) 0xC3, (byte) 0xC5, (byte) 0x8A, (byte) 0x89,
                    0x20, (byte) 0xB5, (byte) 0x93, 0x0B, 0x18, 0x00, 0x09, (byte) 0xF2, (byte) 0x88, 0x2A, 0x4A, 0x08, 0x05, (byte) 0xB2, (byte) 0xA9, 0x3B,
                    0x5D, 0x28, (byte) 0xA4, (byte) 0xB1, 0x00, 0x19, 0x19, 0x7A, (byte) 0xA3, (byte) 0xB3, 0x0A, (byte) 0x90, (byte) 0xA1, (byte) 0xC4, (byte) 0x80, (byte) 0xBA,
                    0x50, 0x13, (byte) 0xC1, (byte) 0xC2, (byte) 0x9A, 0x2A, 0x7B, 0x28, (byte) 0x84, (byte) 0xC1, 0x09, 0x3B, 0x4E, 0x20, (byte) 0x91, (byte) 0xA1,
                    0x18, (byte) 0xAB, 0x79, 0x10, (byte) 0xB4, 0x08, (byte) 0x9A, 0x11, 0x2B, (byte) 0xF0, (byte) 0x93, (byte) 0xAA, 0x01, 0x6A, 0x01, (byte) 0x93,
                    (byte) 0x80, (byte) 0xB8, 0x2A, 0x5B, 0x10, (byte) 0x80, (byte) 0x89, 0x4A, 0x5B, (byte) 0x92, 0x15, (byte) 0xB2, (byte) 0xA0, 0x2F, 0x19, (byte) 0x93,
                    (byte) 0xB8, (byte) 0x95, (byte) 0x80, 0x1C, 0x21, (byte) 0xA9, 0x02, 0x0B, (byte) 0xA0, 0x5A, 0x18, (byte) 0x98, 0x39, 0x1B, 0x68, 0x00,
                    (byte) 0x91, (byte) 0x91, (byte) 0x9C, 0x39, 0x3E, 0x18, (byte) 0x84, (byte) 0xB3, (byte) 0x9B, 0x7A, 0x08, 0x18, 0x0A, (byte) 0xB5, (byte) 0x91, 0x0B,
                    0x28, 0x39, 0x19, (byte) 0x90, 0x0A, 0x50, (byte) 0xAC, 0x11, 0x01, (byte) 0xAB, (byte) 0x88, 0x52, 0x1B, (byte) 0x83, (byte) 0xC4, (byte) 0xA2,
                    (byte) 0x9A, (byte) 0xAB, 0x03, (byte) 0x90, 0x19, (byte) 0x93, (byte) 0x81, 0x08, (byte) 0x92, (byte) 0x9A, 0x68, (byte) 0x98, 0x19, 0x39, (byte) 0xC1, (byte) 0x92,
                    (byte) 0x8A, 0x38, 0x4E, 0x02, (byte) 0xB1, (byte) 0x90, (byte) 0xC3, 0x18, 0x2B, 0x04, (byte) 0xC3, (byte) 0xD2, (byte) 0x91, (byte) 0x90, (byte) 0x81, (byte) 0x89,
                    0x13, (byte) 0xF1, (byte) 0x88, (byte) 0x93, (byte) 0xA2, 0x00, (byte) 0x91, (byte) 0xC0, 0x5B, 0x21, (byte) 0x99, (byte) 0x93, 0x06, (byte) 0x9A, 0x1B, 0x48,
                    (byte) 0x99, (byte) 0xB7, (byte) 0x90, (byte) 0x89, 0x18, 0x1B, 0x11, (byte) 0xA4, (byte) 0xB2, (byte) 0x81, (byte) 0x9A, 0x08, (byte) 0x97, (byte) 0x98, (byte) 0x91, 0x10,
                    (byte) 0xB8, 0x06, (byte) 0xA2, (byte) 0xA0, 0x29, 0x2B, 0x21, (byte) 0xC2, (byte) 0xD1, 0x10, 0x1A, 0x4A, 0x29, (byte) 0xF1, (byte) 0x98, 0x29,
                    0x1B, 0x31, 0x10, (byte) 0xA0, (byte) 0xA1, 0x1D, 0x5A, 0x29, (byte) 0xB2, (byte) 0x82, (byte) 0xA8, 0x0F, 0x28, 0x21, 0x09, (byte) 0x91,
                    (byte) 0x82, 0x4D, 0x10, (byte) 0xA3, (byte) 0xB0, (byte) 0x89, 0x4C, 0x39, (byte) 0xA0, (byte) 0xA4, (byte) 0xA1, (byte) 0x89, 0x1E, 0x28, 0x29, (byte) 0xA3,
                    (byte) 0xC3, 0x2D, 0x19, 0x01, 0x49, 0x01, (byte) 0x9B, 0x0C, 0x21, (byte) 0xC2, (byte) 0xA2, (byte) 0x93, 0x7C, 0x2A, 0x10, (byte) 0x90,

                    // Source: 08HH.ROM 
                    // Length: 384 / 0x00000180 

                    0x75, (byte) 0xF2, (byte) 0xAB, 0x7D, 0x7E, 0x5C, 0x3B, 0x4B, 0x3C, 0x4D, 0x4A, 0x02, (byte) 0xB3, (byte) 0xC5, (byte) 0xE7, (byte) 0xE3,
                    (byte) 0x92, (byte) 0xB3, (byte) 0xC4, (byte) 0xB3, (byte) 0xC3, (byte) 0x8A, 0x3B, 0x5D, 0x5C, 0x3A, (byte) 0x84, (byte) 0xC2, (byte) 0x91, (byte) 0xA4, (byte) 0xE7, (byte) 0xF7,
                    (byte) 0xF7, (byte) 0xF4, (byte) 0xA1, 0x1B, 0x49, (byte) 0xA5, (byte) 0xB1, 0x1E, 0x7F, 0x5A, 0x00, (byte) 0x89, 0x39, (byte) 0xB7, (byte) 0xA8, 0x3D,
                    0x4A, (byte) 0x84, (byte) 0xE7, (byte) 0xF7, (byte) 0xE2, 0x2D, 0x4C, 0x3A, 0x4E, 0x7D, 0x04, (byte) 0xB0, 0x2D, 0x4B, 0x10, (byte) 0x80,
                    (byte) 0xA3, (byte) 0x99, 0x10, 0x0E, 0x59, (byte) 0x93, (byte) 0xC4, (byte) 0xB1, (byte) 0x81, (byte) 0xC4, (byte) 0xA2, (byte) 0xB2, (byte) 0x88, 0x08, 0x3F, 0x3B,
                    0x28, (byte) 0xA6, (byte) 0xC3, (byte) 0xA2, (byte) 0xA2, (byte) 0xC5, (byte) 0xC1, 0x3F, 0x7E, 0x39, (byte) 0x81, (byte) 0x93, (byte) 0xC2, (byte) 0xA3, (byte) 0xE5, (byte) 0xD2,
                    (byte) 0x80, (byte) 0x93, (byte) 0xB8, 0x6D, 0x49, (byte) 0x82, (byte) 0xD4, (byte) 0xA1, (byte) 0x90, 0x01, (byte) 0xA0, 0x09, 0x04, (byte) 0xE3, (byte) 0xB2, (byte) 0x91,
                    (byte) 0xB7, (byte) 0xB3, (byte) 0xA8, 0x2A, 0x03, (byte) 0xF3, (byte) 0xA1, (byte) 0x92, (byte) 0xC5, (byte) 0xC3, (byte) 0xB2, 0x0B, 0x30, (byte) 0xB3, (byte) 0x8E, 0x6D,
                    0x4A, 0x01, (byte) 0xB4, (byte) 0xB4, (byte) 0xC4, (byte) 0xC3, (byte) 0x99, 0x3B, 0x12, (byte) 0xE3, (byte) 0xA1, (byte) 0x88, (byte) 0x82, (byte) 0xB4, (byte) 0x9A, 0x5C,
                    0x3A, 0x18, (byte) 0x93, (byte) 0xC3, (byte) 0xB3, (byte) 0xB4, (byte) 0xA8, 0x19, 0x04, (byte) 0xF3, (byte) 0xA8, 0x3B, 0x10, (byte) 0xA2, (byte) 0x88, (byte) 0xA5,
                    (byte) 0xB2, 0x0B, 0x6D, 0x4B, 0x10, (byte) 0x91, (byte) 0x89, 0x3C, 0x18, 0x18, (byte) 0xA6, (byte) 0xC4, (byte) 0xC3, (byte) 0x98, 0x19, 0x2B,
                    0x20, (byte) 0x91, (byte) 0xA0, 0x4E, 0x28, (byte) 0x93, (byte) 0xB3, (byte) 0xC2, (byte) 0x92, (byte) 0xA9, 0x5A, (byte) 0x96, (byte) 0xC4, (byte) 0xC2, 0x09, 0x01,
                    (byte) 0xC4, (byte) 0xA1, (byte) 0x92, (byte) 0xC4, (byte) 0xA1, (byte) 0x89, 0x10, (byte) 0xA3, (byte) 0xA1, (byte) 0x90, 0x1C, 0x5A, 0x01, (byte) 0xC5, (byte) 0xA1, (byte) 0x92,
                    (byte) 0xD4, (byte) 0xB3, (byte) 0xC4, (byte) 0xC4, (byte) 0xC3, (byte) 0xA1, (byte) 0x88, 0x1A, 0x28, (byte) 0x89, 0x3C, 0x3A, 0x3D, 0x29, 0x00, (byte) 0x93,
                    (byte) 0xB0, 0x3D, 0x28, (byte) 0x80, (byte) 0x91, (byte) 0x82, (byte) 0xE3, (byte) 0x99, 0x2A, 0x11, (byte) 0xD6, (byte) 0xC3, (byte) 0x99, 0x29, (byte) 0x82, (byte) 0xC4,
                    (byte) 0xC3, (byte) 0xA1, 0x0A, 0x3B, 0x3D, 0x3A, 0x02, (byte) 0xC3, (byte) 0xA2, (byte) 0x99, 0x3B, 0x2C, 0x7C, 0x28, (byte) 0x81, (byte) 0xA3,
                    (byte) 0xB2, (byte) 0xA3, (byte) 0xB1, 0x08, 0x1A, 0x3C, 0x18, 0x2E, 0x4C, 0x39, (byte) 0xA5, (byte) 0xB3, (byte) 0xB4, (byte) 0xC2, (byte) 0x88, 0x08,
                    0x19, 0x0A, 0x49, (byte) 0xB7, (byte) 0xB3, (byte) 0xA2, (byte) 0xA1, (byte) 0x92, (byte) 0xA1, (byte) 0x93, (byte) 0xB1, 0x0C, 0x7D, 0x39, (byte) 0x93, (byte) 0xB3,
                    (byte) 0xB1, 0x1A, 0x19, 0x5D, 0x28, (byte) 0xA6, (byte) 0xC4, (byte) 0xB2, (byte) 0x90, 0x09, 0x2A, 0x18, 0x1B, 0x5B, 0x28, (byte) 0x88,
                    0x2C, 0x29, (byte) 0x82, (byte) 0xA0, 0x18, (byte) 0x91, 0x2D, 0x29, 0x2B, 0x5C, 0x4C, 0x3B, 0x4C, 0x28, (byte) 0x80, (byte) 0x92,
                    (byte) 0x90, 0x09, 0x2B, 0x28, 0x1D, 0x6B, 0x11, (byte) 0xC5, (byte) 0xB2, 0x0B, 0x39, 0x09, 0x4D, 0x28, (byte) 0x88, 0x00,
                    0x1B, 0x28, (byte) 0x94, (byte) 0xE3, (byte) 0xA0, 0x1A, 0x28, (byte) 0xB5, (byte) 0xB4, (byte) 0xB3, (byte) 0xB2, (byte) 0x93, (byte) 0xE2, (byte) 0x91, (byte) 0x92, (byte) 0xD4,
                    (byte) 0xA0, 0x1B, 0x4A, 0x01, (byte) 0xA1, (byte) 0x88, 0x2D, 0x5C, 0x3B, 0x28, 0x08, (byte) 0x93, (byte) 0xD4, (byte) 0xB2, (byte) 0x91, (byte) 0xB4,
                    (byte) 0xA0, 0x3E, 0x3B, 0x4B, 0x3B, 0x29, 0x08, (byte) 0x93, (byte) 0x9B, 0x7B, 0x3A, 0x19, 0x00, (byte) 0x80, (byte) 0x80, (byte) 0xA0,

                    // Source: 10TOM.ROM 
                    // Length: 640 / 0x00000280 

                    0x77, 0x27, (byte) 0x87, 0x01, 0x2D, 0x4F, (byte) 0xC3, (byte) 0xC1, (byte) 0x92, (byte) 0x91, (byte) 0x89, 0x59, (byte) 0x83, 0x1A, 0x32, (byte) 0xC2,
                    (byte) 0x95, (byte) 0xB1, (byte) 0x81, (byte) 0x88, (byte) 0x81, 0x4A, 0x3D, 0x11, (byte) 0x9E, 0x0B, (byte) 0x88, 0x0C, 0x18, 0x3B, 0x11, 0x11,
                    (byte) 0x91, 0x00, (byte) 0xA0, (byte) 0xE2, 0x0A, 0x48, 0x13, 0x24, (byte) 0x81, 0x48, 0x1B, 0x39, 0x1C, (byte) 0x83, (byte) 0x84, (byte) 0xA1,
                    (byte) 0xD1, (byte) 0x8E, (byte) 0x8A, 0x0B, (byte) 0xC0, (byte) 0x98, (byte) 0x92, (byte) 0xB8, 0x39, (byte) 0x90, 0x10, (byte) 0x92, (byte) 0xF0, (byte) 0xB5, (byte) 0x88, 0x32,
                    0x49, 0x51, 0x21, 0x03, (byte) 0x82, 0x10, (byte) 0x8A, 0x7A, 0x09, 0x00, (byte) 0xA2, (byte) 0xCA, 0x1B, (byte) 0xCC, 0x1C, (byte) 0xB9,
                    (byte) 0x8E, (byte) 0x89, (byte) 0x89, (byte) 0xA1, (byte) 0x89, (byte) 0x92, 0x29, 0x11, 0x60, 0x40, 0x14, 0x22, 0x32, 0x78, 0x40, 0x01,
                    0x02, (byte) 0x90, (byte) 0x81, (byte) 0xAB, 0x0B, 0x00, (byte) 0xAF, (byte) 0x99, (byte) 0xCC, (byte) 0xAB, (byte) 0xDA, (byte) 0xA9, (byte) 0x99, 0x1B, 0x30, 0x14,
                    (byte) 0x92, 0x22, 0x19, 0x68, 0x32, 0x14, 0x26, 0x13, 0x23, 0x23, 0x20, 0x12, (byte) 0x9A, (byte) 0xA8, (byte) 0xB9, (byte) 0xFA,
                    (byte) 0xAA, (byte) 0xCA, (byte) 0xCC, 0x0C, (byte) 0xA8, (byte) 0xAE, (byte) 0x88, (byte) 0xB9, (byte) 0x88, (byte) 0xA0, 0x02, 0x21, 0x50, 0x43, 0x03, (byte) 0x81,
                    0x2A, 0x11, 0x34, 0x63, 0x24, 0x33, 0x22, 0x38, (byte) 0x8B, (byte) 0xEA, (byte) 0xAE, (byte) 0x99, (byte) 0xA0, (byte) 0x90, (byte) 0x82, 0x00,
                    (byte) 0x89, (byte) 0xBF, (byte) 0x8A, (byte) 0xE8, (byte) 0xA9, (byte) 0x90, 0x01, 0x12, 0x13, 0x12, 0x08, (byte) 0xA9, (byte) 0xAA, (byte) 0xC9, 0x22, 0x63,
                    0x63, 0x12, 0x44, 0x00, 0x10, (byte) 0x88, (byte) 0x9C, (byte) 0x98, (byte) 0xA1, (byte) 0x85, 0x03, 0x32, 0x36, (byte) 0x80, (byte) 0x89, (byte) 0xDB,
                    (byte) 0xDB, (byte) 0xBB, (byte) 0xB9, (byte) 0xBA, 0x01, (byte) 0x81, 0x28, 0x19, (byte) 0xCB, (byte) 0xFA, (byte) 0xBC, 0x09, 0x13, 0x37, 0x34, 0x34,
                    0x23, 0x31, 0x20, 0x10, 0x00, 0x00, 0x28, 0x38, 0x10, (byte) 0x88, (byte) 0xEC, (byte) 0x8D, (byte) 0xCB, (byte) 0xBC, (byte) 0xCC, (byte) 0xBB,
                    (byte) 0xBB, (byte) 0xC9, (byte) 0x99, 0x00, 0x00, 0x33, 0x11, 0x22, (byte) 0x81, 0x07, 0x41, 0x54, 0x34, 0x34, 0x22, 0x31,
                    0x00, (byte) 0x88, (byte) 0x9A, (byte) 0x9B, (byte) 0x98, (byte) 0xAB, (byte) 0x8E, (byte) 0x9B, (byte) 0xBD, (byte) 0x9C, (byte) 0xBC, (byte) 0xBB, (byte) 0xDA, (byte) 0xAA, (byte) 0xA9, (byte) 0x99,
                    0x18, 0x38, 0x60, 0x20, 0x31, 0x13, 0x13, 0x51, 0x14, 0x31, 0x53, 0x33, 0x35, 0x22, 0x01, (byte) 0x8A,
                    (byte) 0x9C, (byte) 0xA9, (byte) 0xCA, (byte) 0xC9, (byte) 0xA8, 0x00, 0x10, (byte) 0x81, (byte) 0x9C, (byte) 0x9E, (byte) 0xAB, (byte) 0xCC, (byte) 0xAB, (byte) 0xBA, (byte) 0x98, 0x30,
                    0x52, 0x03, (byte) 0x81, 0x08, (byte) 0x9C, (byte) 0xAC, (byte) 0xAC, 0x18, 0x11, 0x03, 0x51, 0x61, 0x41, 0x31, 0x31, 0x02,
                    0x01, 0x20, 0x24, 0x43, 0x44, 0x40, 0x30, 0x10, (byte) 0xBC, (byte) 0xBE, (byte) 0xCB, (byte) 0xDB, (byte) 0xAB, (byte) 0xBA, (byte) 0x99, (byte) 0x98,
                    (byte) 0x99, (byte) 0xAA, (byte) 0xBD, (byte) 0xAA, (byte) 0xC8, (byte) 0x90, 0x11, 0x53, 0x37, 0x23, 0x43, 0x34, 0x33, 0x33, 0x33, 0x11,
                    0x28, 0x00, 0x19, (byte) 0xA9, (byte) 0x9A, (byte) 0xCB, (byte) 0xCE, (byte) 0xBB, (byte) 0xEB, (byte) 0xBC, (byte) 0xBB, (byte) 0xCA, (byte) 0xBA, (byte) 0xA8, (byte) 0x88, 0x11,
                    0x12, 0x21, 0x20, 0x22, 0x26, 0x26, 0x23, 0x23, 0x43, 0x24, 0x22, 0x32, 0x20, 0x31, (byte) 0x81, (byte) 0x9A,
                    (byte) 0xBC, (byte) 0xBC, (byte) 0xCB, (byte) 0xBD, (byte) 0x9A, (byte) 0xA9, (byte) 0x90, (byte) 0x98, (byte) 0xBA, (byte) 0xCC, (byte) 0xCB, (byte) 0xBC, (byte) 0x8B, (byte) 0x88, 0x22, 0x35,
                    0x23, 0x12, (byte) 0x99, (byte) 0x8B, (byte) 0xAA, (byte) 0xAA, (byte) 0x89, (byte) 0x82, (byte) 0x93, 0x31, 0x42, 0x23, 0x23, 0x21, 0x32, 0x11,
                    0x20, 0x13, 0x13, 0x24, 0x24, 0x24, 0x22, 0x11, (byte) 0x8A, (byte) 0x9E, (byte) 0xAC, (byte) 0xAC, (byte) 0xAA, (byte) 0xBA, (byte) 0xAA, (byte) 0xAB,
                    (byte) 0xBD, (byte) 0xBC, (byte) 0xCB, (byte) 0xCB, (byte) 0xA9, (byte) 0xA8, (byte) 0x91, 0x12, 0x44, 0x43, 0x44, 0x34, 0x34, 0x42, 0x33, 0x42,
                    0x21, 0x11, 0x11, (byte) 0x88, (byte) 0x80, (byte) 0xAA, 0x0B, (byte) 0xAC, (byte) 0xCB, (byte) 0xEC, (byte) 0xAC, (byte) 0xBA, (byte) 0xCA, (byte) 0xAB, (byte) 0x9A, (byte) 0x99,
                    (byte) 0x80, (byte) 0x91, 0x09, 0x08, 0x10, 0x22, 0x44, 0x43, 0x44, 0x33, 0x43, 0x22, 0x13, 0x21, 0x22, 0x20,
                    0x09, (byte) 0x88, (byte) 0xB9, (byte) 0xC8, (byte) 0xBB, (byte) 0xAB, (byte) 0xAB, (byte) 0xA9, (byte) 0xA9, (byte) 0x9B, (byte) 0x9B, (byte) 0x99, (byte) 0x90, (byte) 0x90, 0x00, (byte) 0x81,
                    0x00, 0x08, 0x09, (byte) 0x8A, (byte) 0x9A, (byte) 0xAA, (byte) 0xA9, (byte) 0xA9, (byte) 0x99, (byte) 0x90, (byte) 0x80, 0x01, (byte) 0x80, 0x00, 0x09, 0x31,
                    0x32, 0x44, 0x33, 0x43, 0x34, 0x33, 0x24, 0x22, 0x23, 0x12, 0x10, 0x09, (byte) 0x9B, (byte) 0xAB, (byte) 0xCA, (byte) 0xCC,
                    (byte) 0xBB, (byte) 0xCB, (byte) 0xDA, (byte) 0xCA, (byte) 0xAB, (byte) 0xCA, (byte) 0xAB, (byte) 0xA9, (byte) 0xA8, (byte) 0x92, 0x12, 0x43, 0x53, 0x35, 0x23, 0x33,
                    0x43, 0x43, 0x52, 0x22, 0x22, 0x21, 0x01, 0x09, (byte) 0x89, (byte) 0xA9, (byte) 0xBB, (byte) 0xBD, (byte) 0xBC, (byte) 0xCB, (byte) 0xDA, (byte) 0xAB,
                    (byte) 0xAB, (byte) 0xAB, (byte) 0xAA, (byte) 0xA9, (byte) 0x99, (byte) 0xA8, 0x09, 0x01, 0x11, 0x34, 0x25, 0x23, 0x33, 0x51, 0x22, 0x31,
                    0x12, 0x20, 0x21, 0x12, 0x10, (byte) 0x80, (byte) 0x99, (byte) 0x9A, (byte) 0x99, (byte) 0x99, (byte) 0x88, 0x08, 0x00, (byte) 0x88, (byte) 0xA9, (byte) 0x99,
                    (byte) 0x99, (byte) 0x80, (byte) 0x80, 0x10, 0x01, 0x00, (byte) 0x9A, (byte) 0xAA, (byte) 0xBB, (byte) 0xBA, (byte) 0xBA, (byte) 0xA9, (byte) 0x99, (byte) 0x99, (byte) 0x89, (byte) 0x99,
                    (byte) 0x99, 0x00, 0x01, 0x33, 0x35, 0x24, 0x23, 0x34, 0x23, 0x33, 0x34, 0x33, 0x43, 0x32, 0x21, (byte) 0x88,
                    (byte) 0xAB, (byte) 0xBD, (byte) 0xBB, (byte) 0xDB, (byte) 0xAB, (byte) 0xBA, (byte) 0xBB, (byte) 0xDA, (byte) 0xBB, (byte) 0xCB, (byte) 0xBB, (byte) 0xBC, (byte) 0xA8, (byte) 0x90, 0x01, 0x12,
                    0x23, 0x43, 0x53, 0x34, 0x34, 0x39, (byte) 0x80, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x00,

                    // Source: 20RIM.ROM 
                    // Length: 128 / 0x00000080 

                    0x0F, (byte) 0xFF, 0x73, (byte) 0x8E, 0x71, (byte) 0xCD, 0x00, 0x49, 0x10, (byte) 0x90, 0x21, 0x49, (byte) 0xA0, (byte) 0xDB, 0x02, 0x3A,
                    (byte) 0xE3, 0x0A, 0x50, (byte) 0x98, (byte) 0xC0, 0x59, (byte) 0xA2, (byte) 0x99, 0x09, 0x22, (byte) 0xA2, (byte) 0x80, 0x10, (byte) 0xA8, 0x5B, (byte) 0xD2,
                    (byte) 0x88, 0x21, 0x09, (byte) 0x96, (byte) 0xA8, 0x10, 0x0A, (byte) 0xE0, 0x08, 0x48, 0x19, (byte) 0xAB, 0x52, (byte) 0xA8, (byte) 0x92, 0x0C,
                    0x03, 0x19, (byte) 0xE2, 0x0A, 0x12, (byte) 0xC2, (byte) 0x81, 0x1E, 0x01, (byte) 0xD0, 0x48, (byte) 0x88, (byte) 0x98, 0x01, 0x49, (byte) 0x91,
                    (byte) 0xAA, 0x2C, 0x25, (byte) 0x89, (byte) 0x88, (byte) 0xB5, (byte) 0x81, (byte) 0xA2, (byte) 0x9A, 0x12, (byte) 0x9E, 0x38, 0x3B, (byte) 0x81, (byte) 0x9B, 0x59,
                    0x01, (byte) 0x93, (byte) 0xCA, 0x4A, 0x21, (byte) 0xA0, 0x3D, 0x0A, 0x39, 0x3D, 0x12, (byte) 0xA8, 0x3F, 0x18, 0x01, (byte) 0x92,
                    0x1C, 0x00, (byte) 0xB2, 0x48, (byte) 0xB9, (byte) 0x94, (byte) 0xA3, 0x19, 0x4F, 0x19, (byte) 0xB2, 0x32, (byte) 0x90, (byte) 0xBA, 0x01, (byte) 0xE6,
                    (byte) 0x91, (byte) 0x80, (byte) 0xC1, (byte) 0xA4, 0x2A, 0x08, (byte) 0xA1, (byte) 0xB1, 0x25, (byte) 0xD2, (byte) 0x88, (byte) 0x99, 0x21, (byte) 0x80, (byte) 0x88, (byte) 0x80,
            };

            /** limitter */
            private static void limit(int val, int max, int min) {
                if (val > max) val = max;
                else if (val < min) val = min;
            }

            /** frequency step rate */
            private static final int SHIFT = 16;
            /** adpcm A address shift */
            private static final int ADDRESS_SHIFT = 8;

            // Algorithm and tables verified on real YM2608 and YM2610

            /** usual ADPCM table (16 * 1.1^N) */
            private static final int[] steps = new int[] {
                    16, 17, 19, 21, 23, 25, 28,
                    31, 34, 37, 41, 45, 50, 55,
                    60, 66, 73, 80, 88, 97, 107,
                    118, 130, 143, 157, 173, 190, 209,
                    230, 253, 279, 307, 337, 371, 408,
                    449, 494, 544, 598, 658, 724, 796,
                    876, 963, 1060, 1166, 1282, 1411, 1552
            };

            /** speedup purposes only */
            static int[] jedi_table = new int[49 * 16];

            static {
                for (int step = 0; step < 49; step++) {
                    // loop over all nibbles and compute the difference 
                    for (int nib = 0; nib < 16; nib++) {
                        int value = (2 * (nib & 0x07) + 1) * steps[step] / 8;
                        jedi_table[step * 16 + nib] = (nib & 0x08) != 0 ? -value : value;
                    }
                }
            }

            /** different from the usual ADPCM table */
            private static final int[] step_inc = new int[] {
                    -1 * 16, -1 * 16, -1 * 16, -1 * 16, 2 * 16, 5 * 16, 7 * 16, 9 * 16
            };

            /** port state */
            public byte flag;
            /** arrived flag mask */
            public byte flagMask;
            /** current ROM data */
            public byte nowData;
            /** current ROM address */
            public int nowAddr;
            public int nowStep;
            public int step;
            /** sample data start address */
            public int start;
            /** sample data end address */
            public int end;
            /** Instrument Level */
            public byte IL;
            /** accumulator */
            public int adpcmAcc;
            /** step */
            public int adpcmStep;
            /** (speedup) hiro-shi!! */
            public int adpcmOut;
            /** volume in "0.75dB" steps */
            public byte volMul;
            /** volume in "-6dB" steps */
            public byte volShift;
            /** &out_adpcm[OPN_xxxx] */
            public int[] pan;
            public int panPtr;
            public byte muted;

            /** ADPCM A (Non control type) : calculate one channel output */
            byte calcChan(byte a, byte b) {
                if (this.muted != 0)
                    return a;

                this.nowStep += this.step;
                if (this.nowStep >= (1 << SHIFT)) {
                    int step = this.nowStep >> SHIFT;
                    this.nowStep &= (1 << SHIFT) - 1;
                    do {
                        // end check
                        // 11-06-2001 JB: corrected comparison. Was > instead of == 
                        // YM2610 checks lower 20 bits only, the 4 MSB bits are sample bank 
                        // Here we use 1<<21 to compensate for nibble calculations 

                        if ((this.nowAddr & ((1 << 21) - 1)) == ((this.end << 1) & ((1 << 21) - 1))) {
                            this.flag = 0;
                            a |= this.flagMask;
                            return a;
                        }
                        byte data;
                        if ((this.nowAddr & 1) != 0)
                            data = (byte) (this.nowData & 0x0f);
                        else {
                            this.nowData = (byte) (b + (this.nowAddr >> 1));
                            data = (byte) ((this.nowData >> 4) & 0x0f);
                        }

                        this.nowAddr++;

                        this.adpcmAcc += jedi_table[this.adpcmStep + data];

                        // extend 12-bit signed int 
                        if ((this.adpcmAcc & ~0x7ff) != 0)
                            this.adpcmAcc |= ~0xfff;
                        else
                            this.adpcmAcc &= 0xfff;

                        this.adpcmStep += step_inc[data & 7];
                        limit(this.adpcmStep, 48 * 16, 0 * 16);

                    } while ((--step) != 0);

                    // calc pcm * volume data
                    this.adpcmOut = ((this.adpcmAcc * this.volMul) >> this.volShift) & ~3; // multiply, shift and mask out 2 LSB bits 
                }

                // output for work of output channels (out_adpcm[OPNxxxx])
                this.pan[this.panPtr] += this.adpcmOut;

                return a;
            }

            public void reset(int ch, double freqBase, int[] out_adpcm) {
                this.step = (int) ((float) (1 << AdpcmA.SHIFT) * ((float) freqBase) / 3.0);
                this.nowAddr = 0;
                this.nowStep = 0;
                this.start = 0;
                this.end = 0;
                // this.delta = 21866;
                this.volMul = 0;
                this.pan = out_adpcm;
                this.panPtr = OUTD_CENTER; // default center
                this.flagMask = (byte) (1 << ch);
                this.flag = 0;
                this.adpcmAcc = 0;
                this.adpcmStep = 0;
                this.adpcmOut = 0;
            }

            public void reset2608(int ch, double freqBase, int[] outAdpcm) {
                if (ch <= 3) // channels 0,1,2,3
                    this.step = (int) ((float) (1 << AdpcmA.SHIFT) * ((float) freqBase) / 3.0);
                else // channels 4 and 5 work with slower clock
                    this.step = (int) ((float) (1 << AdpcmA.SHIFT) * ((float) freqBase) / 6.0);

                this.start = ADPCM_ROM_addr[ch * 2];
                this.end = ADPCM_ROM_addr[ch * 2 + 1];

                this.nowAddr = 0;
                this.nowStep = 0;
                // this.delta = 21866;
                this.volMul = 0;
                this.pan = outAdpcm;
                this.panPtr = OUTD_CENTER; // default center
                this.flagMask = 0;
                this.flag = 0;
                this.adpcmAcc = 0;
                this.adpcmStep = 0;
                this.adpcmOut = 0;
            }
        }

        /** registers */
        public byte[] regs = new byte[512];
        /** OPN state */
        public FM_OPN opn;
        /** channel state */
        public FM_OPN.Channel[] ch = new FM_OPN.Channel[6];
        /** address line A1 */
        public byte addrA1;

        // ADPCM-A unit

        /** pcm rom buffer */
        public byte[] pcmBuf;
        /** pcm rom buffer */
        public int pcmbufPtr;
        /** size of pcm rom */
        public int pcmSize;
        /** adpcmA total level */
        public byte adpcmTL;
        /** adpcm channels */
        public AdpcmA[] adpcm = new AdpcmA[6];
        /** registers */
        public int[] adpcmreg = new int[0x30];
        public byte adpcmArrivedEndAddress;
        /** Delta-T ADPCM unit */
        public YmDeltaT deltaT;
        public byte muteDeltaT;

        /** YM2608 only */
        public byte flagmask;
        /** YM2608 only */
        public byte irqmask;

        // for subclasses
        protected YM2610() {}

        public YM2610(int clock, int rate,
                      FM_OPN.State.TimerHandler timer_handler, FM_OPN.State.IrqHandler IRQHandler, Callbacks ssg) {

            // FM 
            this.opn.st.param = this;
            this.opn.type = TYPE_YM2610;
            this.opn.pCh = this.ch;
            //this.OPN.ST.device = device;
            this.opn.st.clock = clock;
            this.opn.st.rate = rate;
            // Extend handler 
            this.opn.st.timerHandler = timer_handler;
            this.opn.st.irqHandler = IRQHandler;
            this.opn.st.ssg = ssg;
            // ADPCM 
            this.pcmBuf = null;
            this.pcmSize = 0x00;
            // DELTA-T 
            this.deltaT.memory = null;
            this.deltaT.memorySize = 0x00;
            this.deltaT.memoryMask = 0x00;

            this.deltaT.statusSetHandler = this::setDeltaTStatus;
            this.deltaT.statusResetHandler = this::resetDelTatStatus;
            this.deltaT.statusChangeWhichChip = this;
            this.deltaT.statusChangeEOSBit = (byte) 0x80; // status flag: set bit7 on End Of Sample
        }

        /** ADPCM type A Write */
        void writeAdpcmA(int r, int v) {
            byte c;

            this.adpcmreg[r] = v & 0xff; // stock data 
            switch (r) {
            case 0x00: // DM,--,C5,C4,C3,C2,C1,C0 
                if ((v & 0x80) == 0) {
                    // KEY ON 
                    for (c = 0; c < 6; c++) {
                        if (((v >> c) & 1) != 0) {
                            // start adpcm
                            // The .step variable is already set and for the YM2608 it is different on channels 4 and 5.
                            //adpcm[c].step      = (int)((float)(1<<ADPCM_SHIFT)*((float)this.OPN.ST.freqbase)/3.0);
                            adpcm[c].nowAddr = adpcm[c].start << 1;
                            adpcm[c].nowStep = 0;
                            adpcm[c].adpcmAcc = 0;
                            adpcm[c].adpcmStep = 0;
                            adpcm[c].adpcmOut = 0;
                            adpcm[c].flag = 1;

                            if (this.pcmBuf == null) { // Check ROM Mapped
//Debug.printf("YM2608-YM2610: ADPCM-A rom not mapped\n");
                                adpcm[c].flag = 0;
                            } else {
                                if (adpcm[c].end >= this.pcmSize) { // Check End in Range
//Debug.printf("YM2610: ADPCM-A end out of range: $%08x\n", adpcm[c].end);
                                    /*adpcm[c].end = this.pcm_size-1;*/ // JB: DO NOT uncomment this, otherwise you will break the comparison in the ADPCM_CALC_CHA() 
                                }
                                if (adpcm[c].start >= this.pcmSize) { // Check Start in Range
//Debug.printf("YM2608-YM2610: ADPCM-A start out of range: $%08x\n", adpcm[c].start);
                                    adpcm[c].flag = 0;
                                }
                            }
                        }
                    }
                } else {
                    // KEY OFF 
                    for (c = 0; c < 6; c++)
                        if (((v >> c) & 1) != 0)
                            adpcm[c].flag = 0;
                }
                break;
            case 0x01: // B0-5 = TL
                this.adpcmTL = (byte) ((v & 0x3f) ^ 0x3f);
                for (c = 0; c < 6; c++) {
                    int volume = this.adpcmTL + adpcm[c].IL;

                    if (volume >= 63) { // This is correct, 63 = quiet
                        adpcm[c].volMul = 0;
                        adpcm[c].volShift = 0;
                    } else {
                        adpcm[c].volMul = (byte) (15 - (volume & 7)); // so called 0.75 dB
                        adpcm[c].volShift = (byte) (1 + (volume >> 3)); // Yamaha engineers used the approximation: each -6 dB is close to divide by two (shift right)
                    }

                    // calc pcm * volume data
                    adpcm[c].adpcmOut = ((adpcm[c].adpcmAcc * adpcm[c].volMul) >> adpcm[c].volShift) & ~3; // multiply, shift and mask out low 2 bits
                }
                break;
            default:
                c = (byte) (r & 0x07);
                if (c >= 0x06) return;
                switch (r & 0x38) {
                case 0x08: { // B7=L,B6=R, B4-0=IL
                    int volume;

                    adpcm[c].IL = (byte) ((v & 0x1f) ^ 0x1f);

                    volume = this.adpcmTL + adpcm[c].IL;

                    if (volume >= 63) { // This is correct, 63 = quiet 
                        adpcm[c].volMul = 0;
                        adpcm[c].volShift = 0;
                    } else {
                        adpcm[c].volMul = (byte) (15 - (volume & 7)); // so called 0.75 dB 
                        adpcm[c].volShift = (byte) (1 + (volume >> 3)); // Yamaha engineers used the approximation: each -6 dB is close to divide by two (shift right) 
                    }

                    adpcm[c].pan[adpcm[c].panPtr] = this.opn.outAdpcm[(v >> 6) & 0x03];

                    // calc pcm * volume data
                    adpcm[c].adpcmOut = ((adpcm[c].adpcmAcc * adpcm[c].volMul) >> adpcm[c].volShift) & ~3; // multiply, shift and mask out low 2 bits
                }
                break;
                case 0x10:
                case 0x18:
                    adpcm[c].start = ((this.adpcmreg[0x18 + c] * 0x0100 | this.adpcmreg[0x10 + c]) << AdpcmA.ADDRESS_SHIFT);
                    break;
                case 0x20:
                case 0x28:
                    adpcm[c].end = ((this.adpcmreg[0x28 + c] * 0x0100 | this.adpcmreg[0x20 + c]) << AdpcmA.ADDRESS_SHIFT);
                    adpcm[c].end += (1 << AdpcmA.ADDRESS_SHIFT) - 1;
                    break;
                }
                break;
            }
        }

        byte read(int a) {
            int addr = this.opn.st.address;
            byte ret = 0;

            switch (a & 3) {
            case 0: // status 0 : YM2203 compatible 
                ret = (byte) (this.opn.st.getStatus() & 0x83);
                break;
            case 1: // data 0 
                if (addr < 16) ret = (byte) (short) this.opn.st.ssg.read.apply(this.opn.st.param);
                if (addr == 0xff) ret = 0x01;
                break;
            case 2: // status 1 : ADPCM status 
                // ADPCM STATUS (arrived End Address) 
                // B,--,A5,A4,A3,A2,A1,A0 
                // B     = ADPCM-B(DELTA-T) arrived end address 
                // A0-A5 = ADPCM-A          arrived end address 
                ret = this.adpcmArrivedEndAddress;
                break;
            case 3:
                ret = 0;
                break;
            }
            return ret;
        }

        /** Generate samples for one of the YM2610s */
        void updateOne(int[][] buffer, int length) {
            int i, j;
            int[] bufL, bufR;
            FM_OPN.Channel[] cch = new FM_OPN.Channel[4];
            int[] out_fm = opn.outFm;

            // buffer setup 
            bufL = buffer[0];
            bufR = buffer[1];

            cch[0] = this.ch[1];
            cch[1] = this.ch[2];
            cch[2] = this.ch[4];
            cch[3] = this.ch[5];

            // Check YM2610B warning message 
            if (this.ch[0].slots[3].key != 0) {
                //Debug.printf(FM_MSG_YM2610B, this.opn.ST.param, 0));
                this.ch[0].slots[3].key = 0;
            }
            if (this.ch[3].slots[3].key != 0) {
                //Debug.printf(FM_MSG_YM2610B, this.opn.ST.param, 3));
                this.ch[3].slots[3].key = 0;
            }

            // refresh PG and EG 
            cch[0].refresh_fc_eg_chan(opn.fnMax);
            if ((opn.st.mode & 0xc0) != 0) {
                // 3SLOT MODE 
                if (cch[1].slots[SLOT1].incr == -1) {
                    cch[1].slots[SLOT1].refresh_fc_eg(opn.sl3.fc[1], opn.sl3.kCode[1], opn.fnMax);
                    cch[1].slots[SLOT2].refresh_fc_eg(opn.sl3.fc[2], opn.sl3.kCode[2], opn.fnMax);
                    cch[1].slots[SLOT3].refresh_fc_eg(opn.sl3.fc[0], opn.sl3.kCode[0], opn.fnMax);
                    cch[1].slots[SLOT4].refresh_fc_eg(cch[1].fc, cch[1].kCode, opn.fnMax);
                }
            } else
                cch[1].refresh_fc_eg_chan(opn.fnMax);
            cch[2].refresh_fc_eg_chan(opn.fnMax);
            cch[3].refresh_fc_eg_chan(opn.fnMax);

            // buffering 
            for (i = 0; i < length; i++) {

                opn.advance_lfo();

                // clear output acc. 
                opn.outAdpcm[OUTD_LEFT] = opn.outAdpcm[OUTD_RIGHT] = opn.outAdpcm[OUTD_CENTER] = 0;
                opn.outDelta[OUTD_LEFT] = opn.outDelta[OUTD_RIGHT] = opn.outDelta[OUTD_CENTER] = 0;
                // clear outputs 
                out_fm[1] = 0;
                out_fm[2] = 0;
                out_fm[4] = 0;
                out_fm[5] = 0;

                // advance envelope generator 
                opn.egTimer += opn.egTimerAdd;
                while (opn.egTimer >= opn.egTimerOverflow) {
                    opn.egTimer -= opn.egTimerOverflow;
                    opn.egCnt++;

                    opn.advance_eg_channel(cch[0].slots);
                    opn.advance_eg_channel(cch[1].slots);
                    opn.advance_eg_channel(cch[2].slots);
                    opn.advance_eg_channel(cch[3].slots);
                }

                // calculate FM 
                opn.chan_calc(cch[0], 1); // remapped to 1
                opn.chan_calc(cch[1], 2); // remapped to 2
                opn.chan_calc(cch[2], 4); // remapped to 4
                opn.chan_calc(cch[3], 5); // remapped to 5

                // deltaT ADPCM 
                if ((deltaT.portState & 0x80) != 0 && this.muteDeltaT == 0)
                    deltaT.calcAdpcm();

                // AdpcmA 
                for (j = 0; j < 6; j++) {
                    if (this.adpcm[j].flag != 0)
                        this.adpcmArrivedEndAddress = this.adpcm[j].calcChan(this.adpcmArrivedEndAddress, this.pcmBuf[this.pcmbufPtr]);
                }

                // buffering 
                {
                    int lt, rt;

                    lt = (opn.outAdpcm[OUTD_LEFT] + opn.outAdpcm[OUTD_CENTER]) << 1;
                    rt = (opn.outAdpcm[OUTD_RIGHT] + opn.outAdpcm[OUTD_CENTER]) << 1;
                    lt += (opn.outDelta[OUTD_LEFT] + opn.outDelta[OUTD_CENTER]) >> 8;
                    rt += (opn.outDelta[OUTD_RIGHT] + opn.outDelta[OUTD_CENTER]) >> 8;


                    lt += out_fm[1] & opn.pan[2];
                    rt += out_fm[1] & opn.pan[3];
                    lt += out_fm[2] & opn.pan[4];
                    rt += out_fm[2] & opn.pan[5];

                    lt += out_fm[4] & opn.pan[8];
                    rt += out_fm[4] & opn.pan[9];
                    lt += out_fm[5] & opn.pan[10];
                    rt += out_fm[5] & opn.pan[11];


                    lt >>= FINAL_SH;
                    rt >>= FINAL_SH;

                    // buffering 
                    bufL[i] = lt;
                    bufR[i] = rt;
                }

                // timer A control 
                opn.st.internalTimerA(cch[1], opn.type);

            }
            opn.st.internalTimerB(length);
        }

        /** Generate samples for one of the YM2610Bs */
        void updateOneB(int[][] buffer, int length) {
            int[] bufL, bufR;
            FM_OPN.Channel[] cch = new FM_OPN.Channel[6];
            int[] out_fm = opn.outFm;

            // buffer setup 
            bufL = buffer[0];
            bufR = buffer[1];

            cch[0] = this.ch[0];
            cch[1] = this.ch[1];
            cch[2] = this.ch[2];
            cch[3] = this.ch[3];
            cch[4] = this.ch[4];
            cch[5] = this.ch[5];

            // refresh PG and EG 
            cch[0].refresh_fc_eg_chan(opn.fnMax);
            cch[1].refresh_fc_eg_chan(opn.fnMax);
            if ((opn.st.mode & 0xc0) != 0) {
                // 3SLOT MODE 
                if (cch[2].slots[SLOT1].incr == -1) {
                    cch[2].slots[SLOT1].refresh_fc_eg(opn.sl3.fc[1], opn.sl3.kCode[1], opn.fnMax);
                    cch[2].slots[SLOT2].refresh_fc_eg(opn.sl3.fc[2], opn.sl3.kCode[2], opn.fnMax);
                    cch[2].slots[SLOT3].refresh_fc_eg(opn.sl3.fc[0], opn.sl3.kCode[0], opn.fnMax);
                    cch[2].slots[SLOT4].refresh_fc_eg(cch[2].fc, cch[2].kCode, opn.fnMax);
                }
            } else
                cch[2].refresh_fc_eg_chan(opn.fnMax);
            cch[3].refresh_fc_eg_chan(opn.fnMax);
            cch[4].refresh_fc_eg_chan(opn.fnMax);
            cch[5].refresh_fc_eg_chan(opn.fnMax);

            // buffering 
            for (int i = 0; i < length; i++) {

                opn.advance_lfo();

                // clear output acc. 
                opn.outAdpcm[OUTD_LEFT] = opn.outAdpcm[OUTD_RIGHT] = opn.outAdpcm[OUTD_CENTER] = 0;
                opn.outDelta[OUTD_LEFT] = opn.outDelta[OUTD_RIGHT] = opn.outDelta[OUTD_CENTER] = 0;
                // clear outputs 
                out_fm[0] = 0;
                out_fm[1] = 0;
                out_fm[2] = 0;
                out_fm[3] = 0;
                out_fm[4] = 0;
                out_fm[5] = 0;

                // advance envelope generator 
                opn.egTimer += opn.egTimerAdd;
                while (opn.egTimer >= opn.egTimerOverflow) {
                    opn.egTimer -= opn.egTimerOverflow;
                    opn.egCnt++;

                    opn.advance_eg_channel(cch[0].slots);
                    opn.advance_eg_channel(cch[1].slots);
                    opn.advance_eg_channel(cch[2].slots);
                    opn.advance_eg_channel(cch[3].slots);
                    opn.advance_eg_channel(cch[4].slots);
                    opn.advance_eg_channel(cch[5].slots);
                }

                // calculate FM 
                opn.chan_calc(cch[0], 0);
                opn.chan_calc(cch[1], 1);
                opn.chan_calc(cch[2], 2);
                opn.chan_calc(cch[3], 3);
                opn.chan_calc(cch[4], 4);
                opn.chan_calc(cch[5], 5);

                // deltaT ADPCM 
                if ((deltaT.portState & 0x80) != 0 && this.muteDeltaT == 0)
                    deltaT.calcAdpcm();

                // AdpcmA 
                for (int j = 0; j < 6; j++) {
                    if (this.adpcm[j].flag != 0)
                        this.adpcmArrivedEndAddress = this.adpcm[j].calcChan(this.adpcmArrivedEndAddress, this.pcmBuf[this.pcmbufPtr]);
                }

                // buffering 
                int lt = (opn.outAdpcm[OUTD_LEFT] + opn.outAdpcm[OUTD_CENTER]) << 1;
                int rt = (opn.outAdpcm[OUTD_RIGHT] + opn.outAdpcm[OUTD_CENTER]) << 1;
                lt += (opn.outDelta[OUTD_LEFT] + opn.outDelta[OUTD_CENTER]) >> 8;
                rt += (opn.outDelta[OUTD_RIGHT] + opn.outDelta[OUTD_CENTER]) >> 8;

                lt += out_fm[0] & opn.pan[0];
                rt += out_fm[0] & opn.pan[1];
                lt += out_fm[1] & opn.pan[2];
                rt += out_fm[1] & opn.pan[3];
                lt += out_fm[2] & opn.pan[4];
                rt += out_fm[2] & opn.pan[5];
                lt += out_fm[3] & opn.pan[6];
                rt += out_fm[3] & opn.pan[7];
                lt += out_fm[4] & opn.pan[8];
                rt += out_fm[4] & opn.pan[9];
                lt += out_fm[5] & opn.pan[10];
                rt += out_fm[5] & opn.pan[11];

                lt >>= FINAL_SH;
                rt >>= FINAL_SH;

                // buffering
                bufL[i] = lt;
                bufR[i] = rt;

                // timer A control 
                opn.st.internalTimerA(cch[2], this.opn.type);

            }
            opn.st.internalTimerB(length);
        }

        void postLoad() {
            // SSG registers 
            for (int r = 0; r < 16; r++) {
                this.opn.st.ssg.write.accept(this.opn.st.param, 0, r & 0xff);
                this.opn.st.ssg.write.accept(this.opn.st.param, 1, this.regs[r] & 0xff);
            }

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
            // FM channels 
            /*channel_postload(this.CH,6);*/

            // rhythm(AdpcmA) 
            this.writeAdpcmA(1, this.regs[0x101]);
            for (int r = 0; r < 6; r++) {
                this.writeAdpcmA(r + 0x08, this.regs[r + 0x108]);
                this.writeAdpcmA(r + 0x10, this.regs[r + 0x110]);
                this.writeAdpcmA(r + 0x18, this.regs[r + 0x118]);
                this.writeAdpcmA(r + 0x20, this.regs[r + 0x120]);
                this.writeAdpcmA(r + 0x28, this.regs[r + 0x128]);
            }
            // Delta-T ADPCM unit 
            this.deltaT.postLoad(this.regs, 0x010);
        }

        void saveState(DeviceConfig device) {
            device.state_save_register_device_item_array(0, this.regs);
            device.saveStState(this.opn.st);
            device.saveChannelState(this.ch, 6);
            // 3slots 
            device.state_save_register_device_item_array(0, this.opn.sl3.fc);
            device.state_save_register_device_item(0, this.opn.sl3.fnH);
            device.state_save_register_device_item_array(0, this.opn.sl3.kCode);
            // address register1 
            device.state_save_register_device_item(0, this.addrA1);

            device.state_save_register_device_item(0, this.adpcmArrivedEndAddress);
            // rythm(AdpcmA) 
            device.saveAdpcmaState(this.adpcm);
            // Delta-T ADPCM unit 
            this.deltaT.saveState(device);
        }

        /** shut down emulator */
        void shutdown() {
            this.pcmBuf = null;
            this.deltaT.memory = null;
        }

        /** reset one of chip */
        void reset() {
            opn.reset2610(this.ch);

            // ADPCM work initial
            for (int i = 0; i < 6; i++) {
                this.adpcm[i].reset(i, opn.st.freqBase, opn.outAdpcm);
            }
            this.adpcmTL = 0x3f;

            this.adpcmArrivedEndAddress = 0;

            // DELTA-T unit 
            deltaT.reset2610(opn.st.freqBase, opn.outDelta);
        }

        void writePcmRom(byte rom_id, int romSize, int dataStart,
                                 int dataLength, byte[] romData) {
            switch (rom_id) {
            case 0x01: // ADPCM
                if (this.pcmSize != romSize) {
                    this.pcmBuf = new byte[romSize];
                    this.pcmSize = romSize;
                    for (int i = 0; i < romSize; i++) this.pcmBuf[i] = (byte) 0xff;
                }
                if (dataStart > romSize)
                    return;
                if (dataStart + dataLength > romSize)
                    dataLength = romSize - dataStart;

                if (dataLength >= 0) System.arraycopy(romData, 0, this.pcmBuf, dataStart, dataLength);
                break;
            case 0x02: // DELTA-T
                if (this.deltaT.memorySize != romSize) {
                    this.deltaT.memory = new byte[romSize];
                    this.deltaT.memorySize = romSize;
                    for (int i = 0; i < romSize; i++) this.deltaT.memory[i] = (byte) 0xff;
                    this.deltaT.calcMemMask();
                }
                if (dataStart > romSize)
                    return;
                if (dataStart + dataLength > romSize)
                    dataLength = romSize - dataStart;

                if (dataLength >= 0) System.arraycopy(romData, 0, this.deltaT.memory, dataStart, dataLength);
                break;
            }
        }

        void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < 6; curChn++)
                this.ch[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
            for (byte curChn = 0; curChn < 6; curChn++)
                this.adpcm[curChn].muted = (byte) ((muteMask >> (curChn + 6)) & 0x01);
            this.muteDeltaT = (byte) ((muteMask >> 12) & 0x01);
        }

        void setDeltaTStatus(byte changeBits) {
            adpcmArrivedEndAddress |= changeBits;
        }

        void resetDelTatStatus(byte changeBits) {
            adpcmArrivedEndAddress &= (byte) ~changeBits;
        }
    }

    /** here is the virtual YM2608 */
    private static class YM2608 extends YM2610 {

        /*
         * YM2608 local section
         */

        // Generate samples for one of the YM2608s 
        void updateOne(int[][] buffer, int length) {
            FM_OPN opn = this.opn;
            YmDeltaT deltaT = this.deltaT;
            int i, j;
            int[] bufL, bufR;
            FM_OPN.Channel[] cch = new FM_OPN.Channel[6];
            int[] out_fm = opn.outFm;

            // set bufer 
            bufL = buffer[0];
            bufR = buffer[1];

            cch[0] = this.ch[0];
            cch[1] = this.ch[1];
            cch[2] = this.ch[2];
            cch[3] = this.ch[3];
            cch[4] = this.ch[4];
            cch[5] = this.ch[5];

            // refresh PG and EG 
            cch[0].refresh_fc_eg_chan(opn.fnMax);
            cch[1].refresh_fc_eg_chan(opn.fnMax);
            if ((opn.st.mode & 0xc0) != 0) {
                // 3SLOT MODE 
                if (cch[2].slots[SLOT1].incr == -1) {
                    cch[2].slots[SLOT1].refresh_fc_eg(opn.sl3.fc[1], opn.sl3.kCode[1], opn.fnMax);
                    cch[2].slots[SLOT2].refresh_fc_eg(opn.sl3.fc[2], opn.sl3.kCode[2], opn.fnMax);
                    cch[2].slots[SLOT3].refresh_fc_eg(opn.sl3.fc[0], opn.sl3.kCode[0], opn.fnMax);
                    cch[2].slots[SLOT4].refresh_fc_eg(cch[2].fc, cch[2].kCode, opn.fnMax);
                }
            } else
                cch[2].refresh_fc_eg_chan(opn.fnMax);
            cch[3].refresh_fc_eg_chan(opn.fnMax);
            cch[4].refresh_fc_eg_chan(opn.fnMax);
            cch[5].refresh_fc_eg_chan(opn.fnMax);


            // buffering 
            for (i = 0; i < length; i++) {

                opn.advance_lfo();

                // clear output acc. 
                opn.outAdpcm[OUTD_LEFT] = opn.outAdpcm[OUTD_RIGHT] = opn.outAdpcm[OUTD_CENTER] = 0;
                opn.outDelta[OUTD_LEFT] = opn.outDelta[OUTD_RIGHT] = opn.outDelta[OUTD_CENTER] = 0;
                // clear outputs 
                out_fm[0] = 0;
                out_fm[1] = 0;
                out_fm[2] = 0;
                out_fm[3] = 0;
                out_fm[4] = 0;
                out_fm[5] = 0;

                // calculate FM 
                opn.chan_calc(cch[0], 0);
                opn.chan_calc(cch[1], 1);
                opn.chan_calc(cch[2], 2);
                opn.chan_calc(cch[3], 3);
                opn.chan_calc(cch[4], 4);
                opn.chan_calc(cch[5], 5);

                // deltaT ADPCM 
                if ((deltaT.portState & 0x80) != 0 && this.muteDeltaT == 0)
                    deltaT.calcAdpcm();

                // AdpcmA 
                for (j = 0; j < 6; j++) {
                    if (this.adpcm[j].flag != 0)
                        this.adpcmArrivedEndAddress = this.adpcm[j].calcChan(this.adpcmArrivedEndAddress, this.pcmBuf[this.pcmbufPtr]);
                }

                // advance envelope generator 
                opn.egTimer += opn.egTimerAdd;
                while (opn.egTimer >= opn.egTimerOverflow) {
                    opn.egTimer -= opn.egTimerOverflow;
                    opn.egCnt++;

                    opn.advance_eg_channel(cch[0].slots);
                    opn.advance_eg_channel(cch[1].slots);
                    opn.advance_eg_channel(cch[2].slots);
                    opn.advance_eg_channel(cch[3].slots);
                    opn.advance_eg_channel(cch[4].slots);
                    opn.advance_eg_channel(cch[5].slots);
                }

                // buffering 
                {
                    int lt, rt;

                    // this way it's louder (and more accurate)
                    lt = (opn.outAdpcm[OUTD_LEFT] + opn.outAdpcm[OUTD_CENTER]) << 1;
                    rt = (opn.outAdpcm[OUTD_RIGHT] + opn.outAdpcm[OUTD_CENTER]) << 1;
                    lt += (opn.outDelta[OUTD_LEFT] + opn.outDelta[OUTD_CENTER]) >> 8;
                    rt += (opn.outDelta[OUTD_RIGHT] + opn.outDelta[OUTD_CENTER]) >> 8;
                    lt += out_fm[0] & opn.pan[0];
                    rt += out_fm[0] & opn.pan[1];
                    lt += out_fm[1] & opn.pan[2];
                    rt += out_fm[1] & opn.pan[3];
                    lt += out_fm[2] & opn.pan[4];
                    rt += out_fm[2] & opn.pan[5];
                    lt += out_fm[3] & opn.pan[6];
                    rt += out_fm[3] & opn.pan[7];
                    lt += out_fm[4] & opn.pan[8];
                    rt += out_fm[4] & opn.pan[9];
                    lt += out_fm[5] & opn.pan[10];
                    rt += out_fm[5] & opn.pan[11];

                    lt >>= FINAL_SH;
                    rt >>= FINAL_SH;

                    // buffering 
                    bufL[i] = lt;
                    bufR[i] = rt;
                }

                // timer A control 
                opn.st.internalTimerA(cch[2], this.opn.type);

            }
            opn.st.internalTimerB(length);

            // check IRQ for DELTA-T EOS 
            opn.st.setStatus(0);
        }

        void postLoad() {
            // prescaler 
            this.opn.setPreScaler(1, 2);
            this.deltaT.freqBase = this.opn.st.freqBase;
            // IRQ mask / mode 
            this.opn.writeIRQMask(this, this.regs[0x29]);
            // SSG registers 
            for (int r = 0; r < 16; r++) {
                this.opn.st.ssg.write.accept(this.opn.st.param, 0, r);
                this.opn.st.ssg.write.accept(this.opn.st.param, 1, this.regs[r] & 0xff);
            }

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
            // FM channels 
            /*FM_channel_postload(this.CH,6);*/
            // rhythm(AdpcmA) 
            this.writeAdpcmA( 1, this.regs[0x111]);
            for (int r = 0x08; r < 0x0c; r++)
                this.writeAdpcmA( r, this.regs[r + 0x110]);
            // Delta-T ADPCM unit 
            this.deltaT.postLoad(this.regs, 0x100);
        }

        void saveState(DeviceConfig device) {

            device.state_save_register_device_item_array(0, this.regs);
            device.saveStState(this.opn.st);
            device.saveChannelState(this.ch, 6);
            // 3slots 
            device.state_save_register_device_item_array(0, this.opn.sl3.fc);
            device.state_save_register_device_item(0, this.opn.sl3.fnH);
            device.state_save_register_device_item_array(0, this.opn.sl3.kCode);
            // address register1 
            device.state_save_register_device_item(0, this.addrA1);
            // rythm(AdpcmA) 
            device.saveAdpcmaState(this.adpcm);
            // Delta-T ADPCM unit 
            this.deltaT.saveState(device);
        }

        /** YM2608(OPNA) */
        public YM2608(YM2608 param, int clock, int rate,
                      FM_OPN.State.TimerHandler timer_handler, FM_OPN.State.IrqHandler IRQHandler, Callbacks ssg) {

            this.opn.st.param = param;
            this.opn.type = TYPE_YM2608;
            this.opn.pCh = this.ch;
            //this.OPN.ST.device = device;
            this.opn.st.clock = clock;
            this.opn.st.rate = rate;

            // External handlers 
            this.opn.st.timerHandler = timer_handler;
            this.opn.st.irqHandler = IRQHandler;
            this.opn.st.ssg = ssg;

            // DELTA-T 
            this.deltaT.memory = null;
            this.deltaT.memorySize = 0x00;
            this.deltaT.memoryMask = 0x00;

            /*this.deltaT.write_time = 20.0 / clock;*/    // a single byte write takes 20 cycles of main clock 
            /*this.deltaT.read_time  = 18.0 / clock;*/    // a single byte read takes 18 cycles of main clock 

            this.deltaT.statusSetHandler = this::setDeltaTStatus;
            this.deltaT.statusResetHandler = this::resetDelTatStatus;
            this.deltaT.statusChangeWhichChip = this;
            this.deltaT.statusChangeEOSBit = 0x04; // status flag: set bit2 on End Of Sample
            this.deltaT.statusChangeBRDYBit = 0x08; // status flag: set bit3 on BRDY
            this.deltaT.statusChangeZEROBit = 0x10; // status flag: set bit4 if silence continues for more than 290 miliseconds while recording the ADPCM

            // ADPCM Rhythm 
            this.pcmBuf = AdpcmA.ADPCM_ROM;
            this.pcmSize = 0x2000;
        }

        /** shut down emulator */
        void shutdown() {
            this.deltaT.memory = null;
        }

        /** reset one of chips */
        void reset() {

            this.opn.reset2608(this);

            // ADPCM - percussion sounds 
            for (int i = 0; i < 6; i++) {
                this.adpcm[i].reset2608(i, this.opn.st.freqBase, this.opn.outAdpcm);
            }
            this.adpcmTL = 0x3f;

            this.adpcmArrivedEndAddress = 0; // not used

            // DELTA-T unit 
            this.deltaT.reset2608(this.opn.st.freqBase, this.opn.outDelta);
        }

        byte read(int a) {
            int addr = this.opn.st.address;
            byte ret = 0;

            switch (a & 3) {
            case 0: // status 0 : YM2203 compatible
                // BUSY:x:x:x:x:x:FLAGB:FLAGA
                ret = (byte) (this.opn.st.getStatus() & 0x83);
                break;

            case 1: // status 0, ID 
                if (addr < 16) ret = (byte) (short) this.opn.st.ssg.read.apply(this.opn.st.param);
                else if ((addr & 0xff) == 0xff) ret = 0x01; // ID code 
                break;

            case 2: // status 1 : status 0 + ADPCM status 
                // BUSY : x : PCMBUSY : ZERO : BRDY : EOS : FLAGB : FLAGA 
                ret = (byte) ((this.opn.st.getStatus() & (byte) (this.flagmask | 0x80)) | (byte) ((this.deltaT.pcmBsy & 1) << 5));
                break;

            case 3:
                if (addr == 0x08) {
                    ret = this.deltaT.readAdpcm();
                } else {
                    if (addr == 0x0f) {
                        //# ifdef _DEBUG
                        //System.err.pritnln("YM2608 A/D conversion is accessed but not implemented !\n");
                        //#endif
                        ret = (byte) 0x80; // 2's complement PCM data - result from A/D conversion 
                    }
                }
                break;
            }
            return ret;
        }

        void writePcmRom(byte rom_id, int romSize, int dataStart,
                                 int dataLength, byte[] romData) {
            switch (rom_id) {
            case 0x01: // ADPCM
                // unused, it's constant
                break;
            case 0x02: // DELTA-T
                if (this.deltaT.memorySize != romSize) {
                    this.deltaT.memory = new byte[romSize];
                    this.deltaT.memorySize = romSize;
                    for (int i = 0; i < romSize; i++) this.deltaT.memory[i] = (byte) 0xff;
                    this.deltaT.calcMemMask();
                }
                if (dataStart > romSize)
                    return;
                if (dataStart + dataLength > romSize)
                    dataLength = romSize - dataStart;

                if (dataLength >= 0)
                    System.arraycopy(romData, 0, this.deltaT.memory, dataStart + 0, dataLength);
                break;
            }
        }

        void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < 6; curChn++)
                this.ch[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
            for (byte curChn = 0; curChn < 6; curChn++)
                this.adpcm[curChn].muted = (byte) ((muteMask >> (curChn + 6)) & 0x01);
            this.muteDeltaT = (byte) ((muteMask >> 12) & 0x01);
        }

        void setDeltaTStatus(byte changeBits) {
            opn.st.setStatus(changeBits);
        }

        void resetDelTatStatus(byte changeBits) {
            opn.st.resetStatus(changeBits);
        }
    }

    /** YM2608 write
    * @param a = address
    * @param v = value   */
    private int ym2608_write(byte chipId, YM2608 chip, int a, byte v) {
        YM2608 f2608 = chip;
        FM_OPN opn = f2608.opn;
        int addr;

        v &= 0xff; //adjust to 8 bit bus */

        switch (a & 3) {
        case 0: // address port 0 
            opn.st.address = v;
            f2608.addrA1 = 0;

            // Write register to SSG emulator 
            if (v < 16) opn.st.ssg.write.accept(opn.st.param, 0, v & 0xff);
            // prescaler selecter : 2d,2e,2f 
            if (v >= 0x2d && v <= 0x2f) {
                f2608.opn.setPreScaler(v, 2);
                //TODO: set ADPCM[c].step
                f2608.deltaT.freqBase = opn.st.freqBase;
            }
            break;

        case 1: // data port 0 
            if (f2608.addrA1 != 0)
                break; // verified on real YM2608

            addr = opn.st.address;
            f2608.regs[addr] = v;
            switch (addr & 0xf0) {
            case 0x00: // SSG section
                // Write data to SSG emulator 
                opn.st.ssg.write.accept(opn.st.param, a, v & 0xff);
                break;
            case 0x10: // 0x10-0x1f : Rhythm section
                ym2608_update_req(chipId, (YM2608) opn.st.param);
                f2608.writeAdpcmA(addr - 0x10, v);
                break;
            case 0x20: // Mode Register
                switch (addr) {
                case 0x29: // SCH,xx,xxx,EN_ZERO,EN_BRDY,EN_EOS,EN_TB,EN_TA
                    opn.writeIRQMask(f2608, v);
                    break;
                default:
                    ym2608_update_req(chipId, (YM2608) opn.st.param);
                    f2608.opn.writeMode(addr, v);
                    break;
                }
                break;
            default: // opn section
                ym2608_update_req(chipId, (YM2608) opn.st.param);
                f2608.opn.writeReg(addr, v);
                break;
            }
            break;

        case 2: // address port 1 
            opn.st.address = v;
            f2608.addrA1 = 1;
            break;

        case 3: // data port 1 
            if (f2608.addrA1 != 1)
                break; // verified on real YM2608

            addr = opn.st.address;
            f2608.regs[addr | 0x100] = v;
            ym2608_update_req(chipId, (YM2608) opn.st.param);
            switch (addr & 0xf0) {
            case 0x00: // DeltaT PORT
                switch (addr) {
                case 0x0e: // DAC data
//Debug.printf("YM2608: write to DAC data (unimplemented) value=%02x\n", v);
                    break;
                default:
                    // 0x00-0x0d 
                    f2608.deltaT.writeAdpcm(addr, v);
                    break;
                }
                break;
            case 0x10: // IRQ Flag control 
                if (addr == 0x10) {
                    opn.writeIRQFlag(f2608, v);
                }
                break;
            default:
                f2608.opn.writeReg(addr | 0x100, v);
                break;
            }
            break;
        }
        return opn.st.irq;
    }

    private int ym2608_timer_over(byte chipId, YM2608 chip, int c) {
        YM2608 f2608 = chip;

        switch (c) {
        case 1: { // Timer B
            f2608.opn.st.timerBOver();
        }
        break;
        case 0: { // Timer A
            ym2608_update_req(chipId, (YM2608) f2608.opn.st.param);
            // timer update
            f2608.opn.st.timerAOver();
            // CSM mode key,TL control
            if ((f2608.opn.st.mode & 0x80) != 0) { // CSM mode total level latch and auto key on
                f2608.ch[2].CSMKeyControll(f2608.opn.type);
            }
        }
        break;
        default:
            break;
        }

        return f2608.opn.st.irq;
    }

    /**
     * write
     * @param chipId number
     * @param a address
     * @param v value
     */
    private int ym2610_write(byte chipId, YM2610 chip, int a, byte v) {
        YM2610 f2610 = chip;
        FM_OPN opn = f2610.opn;
        int addr;
        int ch;

        v &= 0xff; // adjust to 8 bit bus

        switch (a & 3) {
        case 0: // address port 0 
            opn.st.address = v;
            f2610.addrA1 = 0;

            // Write register to SSG emulator 
            if (v < 16) opn.st.ssg.write.accept(opn.st.param, 0, v & 0xff);
            break;

        case 1: // data port 0 
            if (f2610.addrA1 != 0)
                break; // verified on real YM2608

            addr = opn.st.address;
            f2610.regs[addr] = v;
            switch (addr & 0xf0) {
            case 0x00: // SSG section
                // Write data to SSG emulator 
                opn.st.ssg.write.accept(opn.st.param, a, v & 0xff);
                break;
            case 0x10: // DeltaT ADPCM 
                ym2610_update_req(chipId, (YM2610) opn.st.param);

                switch (addr) {
                case 0x10: // control 1
                case 0x11: // control 2
                case 0x12: // start address L
                case 0x13: // start address H
                case 0x14: // stop address L
                case 0x15: // stop address H

                case 0x19: // delta-n L
                case 0x1a: // delta-n H
                case 0x1b: { // volume
                    f2610.deltaT.writeAdpcm(addr - 0x10, v);
                }
                break;

                case 0x1c: { // FLAG CONTROL : Extend Status Clear/Mask
                    byte statusmask = (byte) ~v;
                    // set arrived flag mask 
                    for (ch = 0; ch < 6; ch++)
                        f2610.adpcm[ch].flagMask = (byte) (statusmask & (1 << ch));

                    f2610.deltaT.statusChangeEOSBit = (byte) (statusmask & 0x80); // status flag: set bit7 on End Of Sample

                    // clear arrived flag 
                    f2610.adpcmArrivedEndAddress &= statusmask;
                }
                break;

                default:
//Debug.printf("YM2610: write to unknown DeltaT register %02x val=%02x\n", addr, v);
                    break;
                }

                break;
            case 0x20: // Mode Register
                ym2610_update_req(chipId, (YM2610) opn.st.param);
                f2610.opn.writeMode(addr, v);
                break;
            default: // opn section
                ym2610_update_req(chipId, (YM2610) opn.st.param);
                // write register 
                f2610.opn.writeReg(addr, v);
                break;
            }
            break;

        case 2: // address port 1
            opn.st.address = v;
            f2610.addrA1 = 1;
            break;

        case 3: // data port 1
            if (f2610.addrA1 != 1)
                break; // verified on real YM2608

            ym2610_update_req(chipId, (YM2610) opn.st.param);
            addr = opn.st.address;
            f2610.regs[addr | 0x100] = v;
            if (addr < 0x30)
                // 100-12f : ADPCM A section 
                f2610.writeAdpcmA(addr, v);
            else
                f2610.opn.writeReg(addr | 0x100, v);
            break;
        }
        return opn.st.irq;
    }

    private int ym2610_timer_over(byte chipId, YM2610 chip, int c) {
        YM2610 F2610 = chip;

        if (c != 0) { // Timer B
            F2610.opn.st.timerBOver();
        } else { // Timer A
            ym2610_update_req(chipId, (YM2610) F2610.opn.st.param);
            // timer update
            F2610.opn.st.timerAOver();
            // CSM mode key,TL controll
            if ((F2610.opn.st.mode & 0x80) != 0) { // CSM mode total level latch and auto key on
                F2610.ch[2].CSMKeyControll(F2610.opn.type);
            }
        }
        return F2610.opn.st.irq;
    }

//#endif (BUILD_YM2610||BUILD_YM2610B)

    private YmDeltaT ymDeltat = new YmDeltaT();
}

