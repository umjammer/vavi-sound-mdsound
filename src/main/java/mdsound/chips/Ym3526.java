package mdsound.chips;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;


/**
 * Yamaha 3812 emulator interface - MAME VERSION
 * <p>
 * CREATED BY
 * Ernesto Corvi
 * <p>
 * UPDATE LOG
 * JB  28-04-2002  Fixed simultaneous usage of all three different chips types.
 * Used real sample rate when resample filter is active.
 * AAT 12-28-2001  Protected Y8950 from accessing unmapped port and keyboard handlers.
 * CHS 1999-01-09  Fixes new Ym3812 emulation interface.
 * CHS 1998-10-23  Mame streaming Sound chips update
 * EC  1998        Created Interface
 * <p>
 * NOTES
 */
public class Ym3526 {

    private static final byte CHIP_SAMPLING_MODE = 0;

    public Opl chip;

    /*
     * File: fmopl.c - software implementation of FM Sound generator
     *                                            types OPL and OPL2
     *
     * Copyright Jarek Burczynski (bujar at mame dot net)
     * Copyright Tatsuyuki Satoh , MultiArcadeMachineEmulator development
     *
     * Version 0.72
     *

    Revision History:

    04-08-2003 Jarek Burczynski:
     - removed BFRDY hack. BFRDY is busy flag, and it should be 0 only when the chips
       handles memory read/write or during the adpcm synthesis when the chips
       requests another byte of ADPCM data.

    24-07-2003 Jarek Burczynski:
     - added a small hack for Y8950 status BFRDY flag (bit 3 should be set after
       some (unknown) delay). Right now it's always set.

    14-06-2003 Jarek Burczynski:
     - implemented all of the status register flags in Y8950 emulation
     - renamed y8950_set_delta_t_memory() parameters from _rom_ to _mem_ since
       they can be either RAM or ROM

    08-10-2002 Jarek Burczynski (thanks to Dox for the YM3526 chips)
     - corrected ym3526_read() to always set bit 2 and bit 1
       to HIGH state - identical to ym3812_read (verified on real YM3526)

    04-28-2002 Jarek Burczynski:
     - binary exact Envelope Generator (verified on real YM3812);
       compared to YM2151: the EG clock is equal to internal_clock,
       rates are 2 times slower and volume resolution is one bit less
     - modified interface functions (they no longer return pointer -
       that's internal to the emulator now):
        - new wrapper functions for OPLCreate: ym3526_init(), ym3812_init() and y8950_init()
     - corrected 'off by one' error in feedback calculations (when feedback is off)
     - enabled waveform usage (credit goes to Vlad Romascanu and zazzal22)
     - speeded up noise generator calculations (Nicola Salmoria)

    03-24-2002 Jarek Burczynski (thanks to Dox for the YM3812 chips)
     Complete rewrite (all verified on real YM3812):
     - corrected sin_tab and tl_tab data
     - corrected Operator output calculations
     - corrected waveform_select_enable register;
       simply: ignore all writes to waveform_select register when
       waveform_select_enable == 0 and do not change the waveform previously selected.
     - corrected KSR handling
     - corrected Envelope Generator: attack shape, Sustain mode and
       Percussive/Non-percussive modes handling
     - Envelope Generator rates are two times slower now
     - LFO amplitude (tremolo) and phase modulation (vibrato)
     - rhythm sounds phase generation
     - white noise generator (big thanks to Olivier Galibert for mentioning Berlekamp-Massey algorithm)
     - corrected key on/off handling (the 'key' signal is ORed from three sources: FM, rhythm and CSM)
     - funky details (like ignoring output of Operator 1 in BD rhythm Sound when connect == 1)

    12-28-2001 Acho A. Tang
     - reflected Delta-T EOS status on Y8950 status port.
     - fixed subscription range of attack/decay tables


        To do:
            add delay before key off in CSM mode (see CSMKeyControll)
            verify volume of the FM part on the Y8950
    */
    private static class Opl {

        /** select output bits size of output : 8 or 16 */
        private static final int SAMPLE_BITS = 16;


        public interface TimerHandler extends BiConsumer<Integer, Integer> {
        }

        public interface IRQHandler extends Consumer<Integer> {
        }

        public interface UpdateHandler extends Runnable {
        }

        public interface OPL_PORTHANDLER_W extends Consumer<Byte> {
        }

        public interface OPL_PORTHANDLER_R extends Supplier<Byte> {
        }

        // output final shift
        private static final int FINAL_SH = 0;

        private static final int MAXOUT = 32767;
        private static final int MINOUT = -32768;

        /** 16.16 fixed point (frequency calculations) */
        private static final int FREQ_SH = 16;
        /** 16.16 fixed point (EG timing) */
        private static final int EG_SH = 16;
        /** 8.24 fixed point (LFO calculations) */
        private static final int LFO_SH = 24;
        /** 16.16 fixed point (timers calculations) */
        private static final int TIMER_SH = 16;

        private static final int FREQ_MASK = ((1 << FREQ_SH) - 1);

        /* envelope output entries */
        private static final int ENV_BITS = 10;
        private static final int ENV_LEN = (1 << ENV_BITS);
        private static final double ENV_STEP = (128.0 / ENV_LEN);

        private static final int MAX_ATT_INDEX = ((1 << (ENV_BITS - 1)) - 1); // 511
        private static final int MIN_ATT_INDEX = (0);

        // sinwave entries
        private static final int SIN_BITS = 10;
        private static final int SIN_LEN = 1 << SIN_BITS;
        private static final int SIN_MASK = SIN_LEN - 1;

        /* 8 bits addressing (real chips) */
        private static final int TL_RES_LEN = 256;

        /* register number to channel number , slot offset */
        private static final int SLOT1 = 0;
        private static final int SLOT2 = 1;

        // Envelope Generator phases

        private static final int EG_ATT = 4;
        private static final int EG_DEC = 3;
        private static final int EG_SUS = 2;
        private static final int EG_REL = 1;
        private static final int EG_OFF = 0;

        // save output as raw 16-bit sample

        /** waveform select */
        private static final int OPL_TYPE_WAVESEL = 0x01;
        // DELTA-T ADPCM unit
        private static final int OPL_TYPE_ADPCM = 0x02;
        private static final int OPL_TYPE_KEYBOARD = 0x04;
        /** keyboard interface */
        private static final int OPL_TYPE_IO = 0x08;
        /** I/O port */

        // Generic interface section

        private static final int OPL_TYPE_YM3526 = 0;
        private static final int OPL_TYPE_YM3812 = OPL_TYPE_WAVESEL;
        private static final int OPL_TYPE_Y8950 = OPL_TYPE_ADPCM | OPL_TYPE_KEYBOARD | OPL_TYPE_IO;

        private static class Slot {

            /** mapping of register number (offset) to slot number used by the emulator */
            private static final int[] slotArray = new int[] {
                    0, 2, 4, 1, 3, 5, -1, -1,
                    6, 8, 10, 7, 9, 11, -1, -1,
                    12, 14, 16, 13, 15, 17, -1, -1,
                    -1, -1, -1, -1, -1, -1, -1, -1
            };

            /**
             * key scale level
             * table is 3dB/octave , DV converts this into 6dB/octave
             * 0.1875 is bit 0 weight of the envelope counter (volume) expressed in the 'decibel' scale
             */
            private static final double DV = 0.1875 / 2.0;
            private static final int[] kslTab = new int[] {
                    // OCT 0
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                    // OCT 1
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                    (int) (0.000 / DV), (int) (0.750 / DV), (int) (1.125 / DV), (int) (1.500 / DV),
                    (int) (1.875 / DV), (int) (2.250 / DV), (int) (2.625 / DV), (int) (3.000 / DV),
                    // OCT 2
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                    (int) (0.000 / DV), (int) (1.125 / DV), (int) (1.875 / DV), (int) (2.625 / DV),
                    (int) (3.000 / DV), (int) (3.750 / DV), (int) (4.125 / DV), (int) (4.500 / DV),
                    (int) (4.875 / DV), (int) (5.250 / DV), (int) (5.625 / DV), (int) (6.000 / DV),
                    // OCT 3
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (1.875 / DV),
                    (int) (3.000 / DV), (int) (4.125 / DV), (int) (4.875 / DV), (int) (5.625 / DV),
                    (int) (6.000 / DV), (int) (6.750 / DV), (int) (7.125 / DV), (int) (7.500 / DV),
                    (int) (7.875 / DV), (int) (8.250 / DV), (int) (8.625 / DV), (int) (9.000 / DV),
                    // OCT 4
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (3.000 / DV), (int) (4.875 / DV),
                    (int) (6.000 / DV), (int) (7.125 / DV), (int) (7.875 / DV), (int) (8.625 / DV),
                    (int) (9.000 / DV), (int) (9.750 / DV), (int) (10.125 / DV), (int) (10.500 / DV),
                    (int) (10.875 / DV), (int) (11.250 / DV), (int) (11.625 / DV), (int) (12.000 / DV),
                    // OCT 5
                    (int) (0.000 / DV), (int) (3.000 / DV), (int) (6.000 / DV), (int) (7.875 / DV),
                    (int) (9.000 / DV), (int) (10.125 / DV), (int) (10.875 / DV), (int) (11.625 / DV),
                    (int) (12.000 / DV), (int) (12.750 / DV), (int) (13.125 / DV), (int) (13.500 / DV),
                    (int) (13.875 / DV), (int) (14.250 / DV), (int) (14.625 / DV), (int) (15.000 / DV),
                    // OCT 6
                    (int) (0.000 / DV), (int) (6.000 / DV), (int) (9.000 / DV), (int) (10.875 / DV),
                    (int) (12.000 / DV), (int) (13.125 / DV), (int) (13.875 / DV), (int) (14.625 / DV),
                    (int) (15.000 / DV), (int) (15.750 / DV), (int) (16.125 / DV), (int) (16.500 / DV),
                    (int) (16.875 / DV), (int) (17.250 / DV), (int) (17.625 / DV), (int) (18.000 / DV),
                    // OCT 7
                    (int) (0.000 / DV), (int) (9.000 / DV), (int) (12.000 / DV), (int) (13.875 / DV),
                    (int) (15.000 / DV), (int) (16.125 / DV), (int) (16.875 / DV), (int) (17.625 / DV),
                    (int) (18.000 / DV), (int) (18.750 / DV), (int) (19.125 / DV), (int) (19.500 / DV),
                    (int) (19.875 / DV), (int) (20.250 / DV), (int) (20.625 / DV), (int) (21.000 / DV)
            };

            /** 0 / 3.0 / 1.5 / 6.0 dB/OCT */
            private static final int[] kslShift = new int[] {31, 1, 2, 0};

            /**
             * sustain level table (3dB per step)
             * 0 - 15: 0, 3, 6, 9,12,15,18,21,24,27,30,33,36,39,42,93 (dB)
             */
            private static int sc(double db) {
                return (int) (db * (2.0 / ENV_STEP));
            }

            private static final int[] slTab = new int[] {
                    sc(0), sc(1), sc(2), sc(3), sc(4), sc(5), sc(6), sc(7),
                    sc(8), sc(9), sc(10), sc(11), sc(12), sc(13), sc(14), sc(31)
            };

            private static final int RATE_STEPS = 8;
            private static final byte[] eg_inc = new byte[] {
                    //cycle:0  1  2  3  4  5  6  7

                    /* 0 */ 0, 1, 0, 1, 0, 1, 0, 1, // rates 00..12 0 (increment by 0 or 1)
                    /* 1 */ 0, 1, 0, 1, 1, 1, 0, 1, // rates 00..12 1
                    /* 2 */ 0, 1, 1, 1, 0, 1, 1, 1, // rates 00..12 2
                    /* 3 */ 0, 1, 1, 1, 1, 1, 1, 1, // rates 00..12 3

                    /* 4 */ 1, 1, 1, 1, 1, 1, 1, 1, // rate 13 0 (increment by 1)
                    /* 5 */ 1, 1, 1, 2, 1, 1, 1, 2, // rate 13 1
                    /* 6 */ 1, 2, 1, 2, 1, 2, 1, 2, // rate 13 2
                    /* 7 */ 1, 2, 2, 2, 1, 2, 2, 2, // rate 13 3

                    /* 8 */ 2, 2, 2, 2, 2, 2, 2, 2, // rate 14 0 (increment by 2)
                    /* 9 */ 2, 2, 2, 4, 2, 2, 2, 4, // rate 14 1
                    /*10 */ 2, 4, 2, 4, 2, 4, 2, 4, // rate 14 2
                    /*11 */ 2, 4, 4, 4, 2, 4, 4, 4, // rate 14 3

                    /*12 */ 4, 4, 4, 4, 4, 4, 4, 4, // rates 15 0, 15 1, 15 2, 15 3 (increment by 4)
                    /*13 */ 8, 8, 8, 8, 8, 8, 8, 8, // rates 15 2, 15 3 for attack
                    /*14 */ 0, 0, 0, 0, 0, 0, 0, 0, // infinity rates for attack and decay(s)
            };


            private static byte o(int a) {
                return (byte) (a * RATE_STEPS);
            }

            // note that there is no O(13) in this table - it's directly in the code

            /** Envelope Generator rates (16 + 64 rates + 16 RKS) */
            private static final byte[] egRateSelect = new byte[] {
                    // 16 infinite time rates
                    o(14), o(14), o(14), o(14), o(14), o(14), o(14), o(14),
                    o(14), o(14), o(14), o(14), o(14), o(14), o(14), o(14),

                    // rates 00-12
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
                    o(0), o(1), o(2), o(3),

                    // rate 13
                    o(4), o(5), o(6), o(7),

                    // rate 14
                    o(8), o(9), o(10), o(11),

                    // rate 15
                    o(12), o(12), o(12), o(12),

                    // 16 dummy rates (same as 15 3)
                    o(12), o(12), o(12), o(12), o(12), o(12), o(12), o(12),
                    o(12), o(12), o(12), o(12), o(12), o(12), o(12), o(12),

            };

            //rate  0,    1,    2,    3,   4,   5,   6,  7,  8,  9,  10, 11, 12, 13, 14, 15
            //shift 12,   11,   10,   9,   8,   7,   6,  5,  4,  3,  2,  1,  0,  0,  0,  0
            //mask  4095, 2047, 1023, 511, 255, 127, 63, 31, 15, 7,  3,  1,  0,  0,  0,  0

            private static byte o2(int a) {
                return (byte) (a * 1);
            }

            /** Envelope Generator counter shifts (16 + 64 rates + 16 RKS) */
            private static final byte[] egRateShift = new byte[] {
                    // 16 infinite time rates
                    o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),
                    o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),

                    // rates 00-12
                    o2(12), o2(12), o2(12), o2(12),
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

                    // rate 13
                    o2(0), o2(0), o2(0), o2(0),

                    // rate 14
                    o2(0), o2(0), o2(0), o2(0),

                    // rate 15
                    o2(0), o2(0), o2(0), o2(0),

                    // 16 dummy rates (same as 15 3)
                    o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),
                    o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),

            };

            /* multiple table */
            private static final byte ML = 2;
            private static final byte[] mulTab = new byte[] {
                    // 1/2, 1, 2, 3, 4, 5, 6, 7, 8, 9,10,10,12,12,15,15
                    (byte) (0.50 * ML), (byte) (1.00 * ML), (byte) (2.00 * ML), (byte) (3.00 * ML), (byte) (4.00 * ML), (byte) (5.00 * ML), (byte) (6.00 * ML), (byte) (7.00 * ML),
                    (byte) (8.00 * ML), (byte) (9.00 * ML), (byte) (10.00 * ML), (byte) (10.00 * ML), (byte) (12.00 * ML), (byte) (12.00 * ML), (byte) (15.00 * ML), (byte) (15.00 * ML)
            };

            private static final int LFO_AM_TAB_ELEMENTS = 210;

            /* LFO Amplitude Modulation table (verified on real YM3812)
               27 output levels (triangle waveform); 1 level takes one of: 192, 256 or 448 samples

               Length: 210 elements.

                Each of the elements has to be repeated
                exactly 64 times (on 64 consecutive samples).
                The whole table takes: 64 * 210 = 13440 samples.

                When AM = 1 data is used directly
                When AM = 0 data is divided by 4 before being used (losing precision is important)
            */
            private static final byte[] lfoAmTable = new byte[] {
                    0, 0, 0, 0, 0, 0, 0,
                    1, 1, 1, 1,
                    2, 2, 2, 2,
                    3, 3, 3, 3,
                    4, 4, 4, 4,
                    5, 5, 5, 5,
                    6, 6, 6, 6,
                    7, 7, 7, 7,
                    8, 8, 8, 8,
                    9, 9, 9, 9,
                    10, 10, 10, 10,
                    11, 11, 11, 11,
                    12, 12, 12, 12,
                    13, 13, 13, 13,
                    14, 14, 14, 14,
                    15, 15, 15, 15,
                    16, 16, 16, 16,
                    17, 17, 17, 17,
                    18, 18, 18, 18,
                    19, 19, 19, 19,
                    20, 20, 20, 20,
                    21, 21, 21, 21,
                    22, 22, 22, 22,
                    23, 23, 23, 23,
                    24, 24, 24, 24,
                    25, 25, 25, 25,
                    26, 26, 26,
                    25, 25, 25, 25,
                    24, 24, 24, 24,
                    23, 23, 23, 23,
                    22, 22, 22, 22,
                    21, 21, 21, 21,
                    20, 20, 20, 20,
                    19, 19, 19, 19,
                    18, 18, 18, 18,
                    17, 17, 17, 17,
                    16, 16, 16, 16,
                    15, 15, 15, 15,
                    14, 14, 14, 14,
                    13, 13, 13, 13,
                    12, 12, 12, 12,
                    11, 11, 11, 11,
                    10, 10, 10, 10,
                    9, 9, 9, 9,
                    8, 8, 8, 8,
                    7, 7, 7, 7,
                    6, 6, 6, 6,
                    5, 5, 5, 5,
                    4, 4, 4, 4,
                    3, 3, 3, 3,
                    2, 2, 2, 2,
                    1, 1, 1, 1
            };

            /* LFO Phase Modulation table (verified on real YM3812) */
            private static final byte[] lfoPmTable = new byte[] {
                    // FNUM2/FNUM = 00 0xxxxxxx (0x0000)
                    0, 0, 0, 0, 0, 0, 0, 0, // LFO PM depth = 0
                    0, 0, 0, 0, 0, 0, 0, 0, // LFO PM depth = 1

                    // FNUM2/FNUM = 00 1xxxxxxx (0x0080)
                    0, 0, 0, 0, 0, 0, 0, 0, // LFO PM depth = 0
                    1, 0, 0, 0, -1, 0, 0, 0, // LFO PM depth = 1

                    // FNUM2/FNUM = 01 0xxxxxxx (0x0100)
                    1, 0, 0, 0, -1, 0, 0, 0, // LFO PM depth = 0
                    2, 1, 0, -1, -2, -1, 0, 1, // LFO PM depth = 1

                    // FNUM2/FNUM = 01 1xxxxxxx (0x0180)
                    1, 0, 0, 0, -1, 0, 0, 0, // LFO PM depth = 0
                    3, 1, 0, -1, -3, -1, 0, 1, // LFO PM depth = 1

                    // FNUM2/FNUM = 10 0xxxxxxx (0x0200)
                    2, 1, 0, -1, -2, -1, 0, 1, // LFO PM depth = 0
                    4, 2, 0, -2, -4, -2, 0, 2, // LFO PM depth = 1

                    // FNUM2/FNUM = 10 1xxxxxxx (0x0280)
                    2, 1, 0, -1, -2, -1, 0, 1, // LFO PM depth = 0
                    5, 2, 0, -2, -5, -2, 0, 2, // LFO PM depth = 1

                    // FNUM2/FNUM = 11 0xxxxxxx (0x0300)
                    3, 1, 0, -1, -3, -1, 0, 1, //  LFO PM depth = 0
                    6, 3, 0, -3, -6, -3, 0, 3, //  LFO PM depth = 1

                    // FNUM2/FNUM = 11 1xxxxxxx (0x0380)
                    3, 1, 0, -1, -3, -1, 0, 1, //  LFO PM depth = 0
                    7, 3, 0, -3, -7, -3, 0, 3 //  LFO PM depth = 1
            };

            /* synchronized level of common table */
            private int numLock = 0;

            /* attack rate: AR<<2 */
            private int ar;
            /* decay rate:  DR<<2 */
            private int dr;
            /* release rate:RR<<2 */
            private int rr;
            /* key scale rate */
            private byte KSR;
            /* keyscale level */
            private byte ksl;
            /* key scale rate: kcode>>KSR */
            private byte ksr;
            /* multiple: mul_tab[ML] */
            private byte mul;

            // Phase Generator

            /* frequency counter */
            private int cnt;
            /* frequency counter step */
            private int incr;
            /* feedback shift value */
            private byte fb;
            /* slot1 output pointer */
            //private int connect1;
            /* slot1 output pointer */
            private int ptrConnect1;
            /* slot1 output for feedback */
            private int[] op1Out = new int[2];
            /* connection (algorithm) type  */
            private byte CON;

            // Envelope Generator

            /* percussive/non-percussive mode */
            private byte aByte;
            /* phase type */
            private byte state;
            /* total level: TL << 2 */
            private int tl;
            /* adjusted now TL */
            private int tll;
            /* envelope counter */
            private int volume;
            /* sustain level: sl_tab[SL] */
            private int sl;
            /* (attack state) */
            private byte egShAr;
            /* (attack state) */
            private byte egSelAr;
            /* (decay state) */
            private byte egShDr;
            /* (decay state) */
            private byte egSelDr;
            /* (release state) */
            private byte egShRr;
            /* (release state) */
            private byte egSelRr;
            /* 0 = KEY OFF, >0 = KEY ON */
            private int key;

            // LFO

            /* LFO Amplitude Modulation enable mask */
            private int aMmask;
            /* LFO Phase Modulation enable flag (active high) */
            private byte vib;

            /* waveform select */
            private int waveTable;

            private int calcVolume(int lfoAm) {
                return tll + volume + (lfoAm & aMmask);
            }

            /** update phase increment counter of Operator (also update the EG rates if necessary) */
            private void caclFcSlot(int fc, int kCode) {

                // (frequency) phase increment counter
                incr = fc * mul;
                int ksr = kCode >> KSR;

                if (this.ksr != ksr) {
                    this.ksr = (byte) ksr;

                    // calculate envelope generator rates
                    if ((ar + this.ksr) < 16 + 62) {
                        egShAr = egRateShift[ar + this.ksr];
                        egSelAr = egRateSelect[ar + this.ksr];
                    } else {
                        egShAr = 0;
                        egSelAr = 13 * RATE_STEPS;
                    }
                    //Debug.printf("CALC_FCSLOT slot.eg_sel_ar:%d slot.ar:%d slot.ksr:%d\n", slot.eg_sel_ar, slot.ar, slot.ksr);
                    egShDr = egRateShift[dr + this.ksr];
                    egSelDr = egRateSelect[dr + this.ksr];
                    egShRr = egRateShift[rr + this.ksr];
                    egSelRr = egRateSelect[rr + this.ksr];
                }
            }

            private void setMul(int v, int fc, byte kCode) {
                this.mul = mulTab[v & 0x0f];
                this.KSR = (byte) ((v & 0x10) != 0 ? 0 : 2);
                this.aByte = (byte) (v & 0x20);
                this.vib = (byte) (v & 0x40);
                this.aMmask = (v & 0x80) != 0 ? ~0 : 0;
                this.caclFcSlot(fc, kCode);
            }

            private void setKslTl(int v, int kslBase) {
                this.ksl = (byte) Opl.Slot.kslShift[v >> 6];
                this.tl = (v & 0x3f) << (ENV_BITS - 1 - 7); // 7 bits TL (bit 6 = always 0)

                this.tll = this.tl + (kslBase >> this.ksl);
            }

            private void setArDr(int v) {
                this.ar = (v >> 4) != 0 ? (16 + ((v >> 4) << 2)) : 0;

                if ((this.ar + this.ksr) < 16 + 62) {
                    this.egShAr = Opl.Slot.egRateShift[this.ar + this.ksr];
                    this.egSelAr = Opl.Slot.egRateSelect[this.ar + this.ksr];
                } else {
                    this.egShAr = 0;
                    this.egSelAr = 13 * Opl.Slot.RATE_STEPS;
                }
                //Debug.printf("this.eg_sel_ar:%d this.ar:%d this.ksr:%d\n", this.eg_sel_ar, this.ar, this.ksr);

                this.dr = (v & 0x0f) != 0 ? (16 + ((v & 0x0f) << 2)) : 0;
                this.egShDr = Opl.Slot.egRateShift[this.dr + this.ksr];
                this.egSelDr = Opl.Slot.egRateSelect[this.dr + this.ksr];
            }

            private void setSlRr(int v) {
                this.sl = Opl.Slot.slTab[v >> 4];

                this.rr = (v & 0x0f) != 0 ? (16 + ((v & 0x0f) << 2)) : 0;
                this.egShRr = Opl.Slot.egRateShift[this.rr + this.ksr];
                this.egSelRr = Opl.Slot.egRateSelect[this.rr + this.ksr];
            }
        }

        private static class Channel {

            private Opl.Slot[] slots = new Opl.Slot[] {new Opl.Slot(), new Opl.Slot()};
            // phase generator state
            /** block+fnum */
            private int blockFNum;
            /** Freq. Increment base */
            private int fc;
            /** KeyScaleLevel Base step */
            private int kslBase;
            /** key code (for key scaling) */
            private byte kCode;
            private byte muted;
        }

        // TL_TAB_LEN is calculated as:
        //  12 - sinus amplitude bits     (Y axis)
        //  2  - sinus sign bit           (Y axis)
        //  TL_RES_LEN - sinus resolution (X axis)
        private static final int TL_TAB_LEN = 12 * 2 * TL_RES_LEN;
        private static int[] tl_tab = new int[TL_TAB_LEN];

        private static final int ENV_QUIET = TL_TAB_LEN >> 4;

        /** sin waveform table in 'decibel' scale */
        /** four waveforms on OPL2 type chips */
        private static int[] sinTab = new int[SIN_LEN * 4];

        // FM channel slots

        /** OPL/OPL2 chips have 9 channels */
        private Opl.Channel[] chs = new Opl.Channel[] {
                new Opl.Channel(), new Opl.Channel(), new Opl.Channel(), new Opl.Channel(), new Opl.Channel(),
                new Opl.Channel(), new Opl.Channel(), new Opl.Channel(), new Opl.Channel()
        };
        /** Mute Special: 5 Rhythm + 1 DELTA-T Channel */
        private byte[] muteSpc = new byte[6];

        /** Global envelope generator counter */
        private int egCnt;
        /** Global envelope generator counter works at frequency = chipclock/72 */
        private int egTimer;
        /** step of eg_timer */
        private int egTimerAdd;
        /** envelope generator timer overlfows every 1 sample (on real chips) */
        private int egTimerOverflow;

        /** Rhythm mode */
        private byte rhythm;

        /** fnumber.increment counter */
        private int[] fnTab = new int[1024];

        /** LFO */
        private int lfoAm;
        private int lfoPm;

        private byte lfoAmDepth;
        private byte lfoPmDepthRange;
        private int lfoAmCnt;
        private int lfoAmInc;
        private int lfoPmCnt;
        private int lfoPmInc;

        /** 23 bit noise shift register */
        private int noiseRng;
        /** current noise 'phase' */
        private int noiseP;
        /** current noise period */
        private int noiseF;

        /** waveform select enable flag */
        private byte waveSel;

        /** timer counters */
        private int[] t = new int[2];
        /** timer enable */
        private byte[] st = new byte[2];

        // external event Callback handlers

        /** TIMER handler */
        private Opl.TimerHandler timer_handler;
        /** IRQ handler */
        private Opl.IRQHandler IRQHandler;
        /** stream update handler */
        private Opl.UpdateHandler updateHandler;
        /** stream update parameter */
        private Ym3526 UpdateParam;

        /** chips type */
        private byte type;
        /** address register */
        private byte address;
        /** status flag */
        private byte status;
        /** status mask */
        private byte statusMask;
        /** Reg.08 : CSM,notesel,etc. */
        private byte mode;

        /** master clock  (Hz) */
        private int clock;
        /** sampling rate (Hz) */
        private int rate;
        /** frequency base */
        private double freqBase;

        /** phase modulation input (SLOT 2) */
        private int phaseModulation;
        private int[] output = new int[1];

        private Opl.Slot slot7_1() {
            return this.chs[7].slots[SLOT1];
        }

        private Opl.Slot slot7_2() {
            return this.chs[7].slots[SLOT2];
        }

        private Opl.Slot slot8_1() {
            return this.chs[8].slots[SLOT1];
        }

        private Opl.Slot slot8_2() {
            return this.chs[8].slots[SLOT2];
        }

        /** status set and IRQ handling */
        private void setStatus(int flag) {
            // set status flag
            this.status |= (byte) flag;
            if ((this.status & 0x80) == 0) {
                if ((this.status & this.statusMask) != 0) { // IRQ on
                    this.status |= 0x80;
                    // Callback user interrupt handler (IRQ is OFF to ON)
                    if (this.IRQHandler != null) this.IRQHandler.accept(1);
                }
            }
        }

        /** status reset and IRQ handling */
        private void resetStatus(int flag) {
            // reset status flag
            this.status &= (byte) ~flag;
            if ((this.status & 0x80) != 0) {
                if ((this.status & this.statusMask) == 0) {
                    this.status &= 0x7f;
                    // Callback user interrupt handler (IRQ is ON to OFF)
                    if (this.IRQHandler != null) this.IRQHandler.accept(0);
                }
            }
        }

        /** IRQ mask set */
        private void setStatusMask(int flag) {
            this.statusMask = (byte) flag;
            // IRQ handling check
            setStatus(0);
            resetStatus(0);
        }

        /** advance LFO to next sample */
        private void advanceLfo() {
            // LFO
            this.lfoAmCnt += this.lfoAmInc;

            if (this.lfoAmCnt >= (Opl.Slot.LFO_AM_TAB_ELEMENTS << LFO_SH)) // lfo_am_table is 210 elements long
                this.lfoAmCnt -= (Opl.Slot.LFO_AM_TAB_ELEMENTS << LFO_SH);

            byte tmp = Opl.Slot.lfoAmTable[this.lfoAmCnt >> LFO_SH];
            //Debug.printf("tmp %d\n", tmp);

            if (this.lfoAmDepth != 0)
                this.lfoAm = tmp;
            else
                this.lfoAm = tmp >> 2;

            this.lfoPmCnt += this.lfoPmInc;
            this.lfoPm = ((this.lfoPmCnt >> LFO_SH) & 7) | this.lfoPmDepthRange;
        }

        private void refreshEg() {
            for (int i = 0; i < 9 * 2; i++) {
                Opl.Channel ch = this.chs[i / 2];
                Opl.Slot op = ch.slots[i & 1];

                // Envelope Generator
                switch (op.state) {
                case EG_ATT: // attack phase
                    if ((this.egCnt & ((1 << op.egShAr) - 1)) == 0) {
                        int newVol = op.volume + ((~op.volume *
                                (Opl.Slot.eg_inc[op.egSelAr + ((this.egCnt >> op.egShAr) & 7)])
                        ) >> 3);
                        if (newVol <= MIN_ATT_INDEX) {
                            op.volume = MIN_ATT_INDEX;
                            op.state = EG_DEC;
                        }
                    }
                    break;
                /*case EG_DEC: // decay phase
                    if ( !(this.eg_cnt & ((1<<Op.eg_sh_dr)-1) ) ) {
                        newVol = Op.volume + eg_inc[Op.eg_sel_dr + ((this.eg_cnt>>Op.eg_sh_dr)&7)];
                        if ( newVol >= Op.sl )
                            Op.state = EG_SUS;
                    }
                    break;
                case EG_SUS: // sustain phase
                    if ( !Op.eg_type) { // percussive mode
                        newVol = Op.volume + eg_inc[Op.eg_sel_rr + ((this.eg_cnt>>Op.eg_sh_rr)&7)];
                        if ( !(this.eg_cnt & ((1<<Op.eg_sh_rr)-1) ) ) {
                            if ( newVol >= MAX_ATT_INDEX )
                                Op.volume = MAX_ATT_INDEX;
                        }
                    }
                    break;
                case EG_REL: // release phase
                    if ( !(this.eg_cnt & ((1<<Op.eg_sh_rr)-1) ) ) {
                        newVol = Op.volume + eg_inc[Op.eg_sel_rr + ((this.eg_cnt>>Op.eg_sh_rr)&7)];
                        if ( newVol >= MAX_ATT_INDEX ) {
                            Op.volume = MAX_ATT_INDEX;
                            Op.state = EG_OFF;
                        }
                    }
                    break;
                default:
                    break;*/
                }
            }
        }

        /** advance to next sample */
        private void advance() {
            this.egTimer += this.egTimerAdd;

            while (this.egTimer >= this.egTimerOverflow) {
                this.egTimer -= this.egTimerOverflow;

                this.egCnt++;

                for (int i = 0; i < 9 * 2; i++) {
                    Opl.Channel ch = this.chs[i / 2];
                    Opl.Slot op = ch.slots[i & 1];

                    // Envelope Generator
                    switch (op.state) {
                    case EG_ATT: // attack phase
                        if ((this.egCnt & ((1 << op.egShAr) - 1)) == 0) {
//Debug.printf("eg_inc:%d Op.eg_sel_ar:%d this.eg_cnt:%d Op.eg_sh_ar:%d\n"
//    ,eg_inc[Op.eg_sel_ar + ((this.eg_cnt >> Op.eg_sh_ar) & 7)]
//    ,Op.eg_sel_ar, this.eg_cnt, Op.eg_sh_ar);
                            op.volume += ((~op.volume) *
                                    (Opl.Slot.eg_inc[op.egSelAr + ((this.egCnt >> op.egShAr) & 7)])
                            ) >> 3;

                            if (op.volume <= MIN_ATT_INDEX) {
                                op.volume = MIN_ATT_INDEX;
                                op.state = EG_DEC;
                            }
                        }
                        break;

                    case EG_DEC: // decay phase
                        if ((this.egCnt & ((1 << op.egShDr) - 1)) == 0) {
                            op.volume += Opl.Slot.eg_inc[op.egSelDr + ((this.egCnt >> op.egShDr) & 7)];

                            if (op.volume >= op.sl)
                                op.state = EG_SUS;

                        }
                        break;

                    case EG_SUS: // sustain phase

                        // this is important behaviour:
                        // one can change percusive/non-percussive modes on the fly and
                        // the chips will remain in sustain phase - verified on real YM3812

                        if (op.aByte != 0) { // non-percussive mode
                            // do nothing
                        } else { // percussive mode
                            // during sustain phase chips adds Release Rate (in percussive mode)
                            if ((this.egCnt & ((1 << op.egShRr) - 1)) == 0) {
                                op.volume += Opl.Slot.eg_inc[op.egSelRr + ((this.egCnt >> op.egShRr) & 7)];

                                if (op.volume >= MAX_ATT_INDEX)
                                    op.volume = MAX_ATT_INDEX;
                            }
                            // else do nothing in sustain phase
                        }
                        break;

                    case EG_REL: // release phase
                        if ((this.egCnt & ((1 << op.egShRr) - 1)) == 0) {
                            op.volume += Opl.Slot.eg_inc[op.egSelRr + ((this.egCnt >> op.egShRr) & 7)];

                            if (op.volume >= MAX_ATT_INDEX) {
                                op.volume = MAX_ATT_INDEX;
                                op.state = EG_OFF;
                            }

                        }
                        break;

                    default:
                        break;
                    }
                }
            }

            for (int i = 0; i < 9 * 2; i++) {
                Opl.Channel ch = this.chs[i / 2];
                Opl.Slot op = ch.slots[i & 1];

                // Phase Generator
                if (op.vib != 0) {
                    byte block;
                    int blockFNum = ch.blockFNum;

                    int fNumLfo = (blockFNum & 0x0380) >> 7;

                    int lfoFnTableIndexOffset = Opl.Slot.lfoPmTable[this.lfoPm + 16 * fNumLfo];

                    if (lfoFnTableIndexOffset != 0) { // LFO phase modulation active
                        blockFNum += lfoFnTableIndexOffset;
                        block = (byte) ((blockFNum & 0x1c00) >> 10);
                        op.cnt += (this.fnTab[blockFNum & 0x03ff] >> (7 - block)) * op.mul;
                    } else { // LFO phase modulation = zero
                        op.cnt += op.incr;
                    }
                } else { // LFO phase modulation disabled for this Operator
                    op.cnt += op.incr;
                }
            }

            // The Noise Generator of the YM3812 is 23-bit shift register.
            // Period is equal to 2^23-2 samples.
            // Register works at sampling frequency of the chips, so output
            // can change on every sample.
            //
            // Output of the register and input to the bit 22 is:
            // bit0 XOR bit14 XOR bit15 XOR bit22
            //
            // Simply use bit 22 as the noise output.

            this.noiseP += this.noiseF;
            int i = this.noiseP >> FREQ_SH; // number of events (shifts of the shift register)
            this.noiseP &= FREQ_MASK;
            while (i != 0) {
//                int j = ( (this.noise_rng) ^ (this.noise_rng>>14) ^ (this.noise_rng>>15) ^ (this.noise_rng>>22) ) & 1;
//                this.noise_rng = (j<<22) | (this.noise_rng>>1);

                // Instead of doing all the logic operations above, we
                // use a trick here (and use bit 0 as the noise output).
                // The difference is only that the noise bit changes one
                // step ahead. This doesn't matter since we don't know
                // what is real state of the noise_rng after the reset.

                if ((this.noiseRng & 1) != 0) this.noiseRng ^= 0x800302;
                this.noiseRng >>= 1;

                i--;
            }
        }

        private int opCalc(int phase, int env, int pm, int wave_tab) {
            int p;

            p = (env << 4) + sinTab[wave_tab + ((((phase & ~FREQ_MASK) + (pm << 16)) >> FREQ_SH) & SIN_MASK)];
            //Debug.printf("op_calc:%d",p);
            if (p >= TL_TAB_LEN)
                return 0;
            return tl_tab[p];
        }

        private int opCalc1(int phase, int env, int pm, int wave_tab) {
            int p;

            p = (env << 4) + sinTab[wave_tab + ((((phase & ~FREQ_MASK) + pm) >> FREQ_SH) & SIN_MASK)];
            //Debug.printf("op_calc1:%d", p);

            if (p >= TL_TAB_LEN)
                return 0;
            return tl_tab[p];
        }

        /** calculate output */
        private void calcCh(Opl.Channel ch) {
            if (ch.muted != 0)
                return;

            this.phaseModulation = 0;

            // slot 1
            Opl.Slot slot = ch.slots[SLOT1];
            int env = slot.calcVolume(this.lfoAm);
            //Debug.printf("env1 %d %d %d %d %d\n", env, slot.TLL, slot.volume, this.LFO_AM, slot.aMmask);
            int out = slot.op1Out[0] + slot.op1Out[1];
            slot.op1Out[0] = slot.op1Out[1];
            if (slot.ptrConnect1 == 0) this.output[0] += slot.op1Out[0];
            else this.phaseModulation += slot.op1Out[0];
            slot.op1Out[1] = 0;
            if (env < ENV_QUIET) {
                if (slot.fb == 0)
                    out = 0;
                slot.op1Out[1] = opCalc1(slot.cnt, env, (out << slot.fb), slot.waveTable);
            }

            // slot 2
            slot = ch.slots[SLOT2];
            env = slot.calcVolume(this.lfoAm);
            //Debug.printf("env2 %d\n", env);
            if (env < ENV_QUIET)
                this.output[0] += opCalc(slot.cnt, env, this.phaseModulation, slot.waveTable);
        }

        /*
            operators used in the rhythm sounds generation process:

            Envelope Generator:

        channel  Operator  register number   Bass  High  Snare Tom  Top
        / slot   number    TL ARDR SLRR Wave Drum  Hat   Drum  Tom  Cymbal
         6 / 0   12        50  70   90   f0  +
         6 / 1   15        53  73   93   f3  +
         7 / 0   13        51  71   91   f1        +
         7 / 1   16        54  74   94   f4              +
         8 / 0   14        52  72   92   f2                    +
         8 / 1   17        55  75   95   f5                          +

            Phase Generator:

        channel  Operator  register number   Bass  High  Snare Tom  Top
        / slot   number    MULTIPLE          Drum  Hat   Drum  Tom  Cymbal
         6 / 0   12        30                +
         6 / 1   15        33                +
         7 / 0   13        31                      +     +           +
         7 / 1   16        34                -----  n o t  u s e d -----
         8 / 0   14        32                                  +
         8 / 1   17        35                      +                 +

        channel  Operator  register number   Bass  High  Snare Tom  Top
        number   number    BLK/FNUM2 FNUM    Drum  Hat   Drum  Tom  Cymbal
           6     12,15     B6        A6      +

           7     13,16     B7        A7            +     +           +

           8     14,17     B8        A8            +           +     +

        */

        /** calculate rhythm */
        private void calcRh(Opl.Channel[] ch, int noise) {

            // Bass Drum (verified on real YM3812):
            // - depends on the channel 6 'connect' register:
            //     when connect = 0 it works the same as in normal (non-rhythm) mode (op1.op2.out)
            //     when connect = 1 _only_ Operator 2 is present on output (op2.out), Operator 1 is ignored
            // - output sample always is multiplied by 2

            this.phaseModulation = 0;
            // slot 1
            Opl.Slot slot = ch[6].slots[SLOT1];
            int env = slot.calcVolume(this.lfoAm);

            int out = slot.op1Out[0] + slot.op1Out[1];
            slot.op1Out[0] = slot.op1Out[1];

            if (slot.CON == 0)
                this.phaseModulation = slot.op1Out[0];
            // else ignore output of Operator 1

            slot.op1Out[1] = 0;
            if (env < ENV_QUIET) {
                if (slot.fb == 0)
                    out = 0;
                slot.op1Out[1] = opCalc1(slot.cnt, env, (out << slot.fb), slot.waveTable);
            }

            // slot 2
            slot = ch[6].slots[SLOT2];
            env = slot.calcVolume(this.lfoAm);
            if (env < ENV_QUIET && this.muteSpc[0] == 0)
                this.output[0] += opCalc(slot.cnt, env, this.phaseModulation, slot.waveTable) * 2;

            // Phase generation is based on:
            // HH  (13) channel 7.slot 1 combined with channel 8.slot 2 (same combination as TOP CYMBAL but different output phases)
            // SD  (16) channel 7.slot 1
            // TOM (14) channel 8.slot 1
            // TOP (17) channel 7.slot 1 combined with channel 8.slot 2 (same combination as HIGH HAT but different output phases)

            // Envelope generation based on:
            // HH  channel 7.slot1
            // SD  channel 7.slot2
            // TOM channel 8.slot1
            // TOP channel 8.slot2

            // The following formulas can be well optimized.
            // I leave them in direct form for now (in case I've missed something).

            /* High Hat (verified on real YM3812) */
            env = slot7_1().calcVolume(this.lfoAm);
            if (env < ENV_QUIET && this.muteSpc[4] == 0) {

                // high hat phase generation:
                //  phase = d0 or 234 (based on frequency only)
                //  phase = 34 or 2d0 (based on noise)

                // base frequency derived from Operator 1 in channel 7
                byte bit7 = (byte) (((slot7_1().cnt >> FREQ_SH) >> 7) & 1);
                byte bit3 = (byte) (((slot7_1().cnt >> FREQ_SH) >> 3) & 1);
                byte bit2 = (byte) (((slot7_1().cnt >> FREQ_SH) >> 2) & 1);

                byte res1 = (byte) ((bit2 ^ bit7) | bit3);

                // when res1 = 0 phase = 0x000 | 0xd0;
                // when res1 = 1 phase = 0x200 | (0xd0>>2);
                int phase = res1 != 0 ? (0x200 | (0xd0 >> 2)) : 0xd0;

                // enable gate based on frequency of Operator 2 in channel 8
                byte bit5e = (byte) (((slot8_2().cnt >> FREQ_SH) >> 5) & 1);
                byte bit3e = (byte) (((slot8_2().cnt >> FREQ_SH) >> 3) & 1);

                byte res2 = (byte) (bit3e ^ bit5e);

                // when res2 = 0 pass the phase from calculation above (res1);
                // when res2 = 1 phase = 0x200 | (0xd0>>2);
                if (res2 != 0)
                    phase = (0x200 | (0xd0 >> 2));


                // when phase & 0x200 is set and noise=1 then phase = 0x200|0xd0
                // when phase & 0x200 is set and noise=0 then phase = 0x200|(0xd0>>2), ie no change
                if ((phase & 0x200) != 0) {
                    if (noise != 0)
                        phase = 0x200 | 0xd0;
                } else {
                    // when phase & 0x200 is clear and noise=1 then phase = 0xd0>>2
                    // when phase & 0x200 is clear and noise=0 then phase = 0xd0, ie no change
                    if (noise != 0)
                        phase = 0xd0 >> 2;
                }

                this.output[0] += opCalc(phase << FREQ_SH, env, 0, slot7_1().waveTable) * 2;
            }

            // Snare Drum (verified on real YM3812)
            env = slot7_2().calcVolume(this.lfoAm);
            if (env < ENV_QUIET && this.muteSpc[1] == 0) {
                // base frequency derived from Operator 1 in channel 7
                byte bit8 = (byte) (((slot7_1().cnt >> FREQ_SH) >> 8) & 1);

                // when bit8 = 0 phase = 0x100;
                // when bit8 = 1 phase = 0x200;
                int phase = bit8 != 0 ? 0x200 : 0x100;

                // Noise bit XOR'es phase by 0x100
                // when noisebit = 0 pass the phase from calculation above
                // when noisebit = 1 phase ^= 0x100;
                // in other words: phase ^= (noisebit<<8);
                if (noise != 0)
                    phase ^= 0x100;

                this.output[0] += opCalc(phase << FREQ_SH, env, 0, slot7_2().waveTable) * 2;
            }

            // Tom Tom (verified on real YM3812)
            env = slot8_1().calcVolume(this.lfoAm);
            if (env < ENV_QUIET && this.muteSpc[2] == 0)
                this.output[0] += opCalc(slot8_1().cnt, env, 0, slot8_1().waveTable) * 2;

            // Top Cymbal (verified on real YM3812)
            env = slot8_2().calcVolume(this.lfoAm);
            if (env < ENV_QUIET && this.muteSpc[3] == 0) {
                // base frequency derived from Operator 1 in channel 7
                byte bit7 = (byte) (((slot7_1().cnt >> FREQ_SH) >> 7) & 1);
                byte bit3 = (byte) (((slot7_1().cnt >> FREQ_SH) >> 3) & 1);
                byte bit2 = (byte) (((slot7_1().cnt >> FREQ_SH) >> 2) & 1);

                byte res1 = (byte) ((bit2 ^ bit7) | bit3);

                // when res1 = 0 phase = 0x000 | 0x100;
                // when res1 = 1 phase = 0x200 | 0x100;
                int phase = res1 != 0 ? 0x300 : 0x100;

                // enable gate based on frequency of Operator 2 in channel 8
                byte bit5e = (byte) (((slot8_2().cnt >> FREQ_SH) >> 5) & 1);
                byte bit3e = (byte) (((slot8_2().cnt >> FREQ_SH) >> 3) & 1);

                byte res2 = (byte) (bit3e ^ bit5e);
                // when res2 = 0 pass the phase from calculation above (res1);
                // when res2 = 1 phase = 0x200 | 0x100;
                if (res2 != 0)
                    phase = 0x300;

                this.output[0] += opCalc(phase << FREQ_SH, env, 0, slot8_2().waveTable) * 2;
            }
        }

        // generic table initialize
        static {
            for (int x = 0; x < TL_RES_LEN; x++) {
                double m = (1 << 16) / Math.pow(2, (x + 1) * (ENV_STEP / 4.0) / 8.0);
                m = Math.floor(m);

                // we never reach (1<<16) here due to the (x+1)
                // result fits within 16 bits at maximum

                int n = (int) m; // 16 bits here
                n >>= 4; // 12 bits here
                if ((n & 1) != 0) // round to nearest
                    n = (n >> 1) + 1;
                else
                    n = n >> 1;
                // 11 bits here (rounded)
                n <<= 1; // 12 bits here (as in real chips)
                tl_tab[x * 2 + 0] = n;
                tl_tab[x * 2 + 1] = -tl_tab[x * 2 + 0];

                for (int i = 1; i < 12; i++) {
                    tl_tab[x * 2 + 0 + i * 2 * TL_RES_LEN] = tl_tab[x * 2 + 0] >> i;
                    tl_tab[x * 2 + 1 + i * 2 * TL_RES_LEN] = -tl_tab[x * 2 + 0 + i * 2 * TL_RES_LEN];
                }
            }
            // Debug.printf("FMthis.C: TL_TAB_LEN = %i elements (%i bytes)\n",TL_TAB_LEN, (int)sizeof(tl_tab));

            for (int i = 0; i < SIN_LEN; i++) {
                // non-standard sinus
                double m = Math.sin(((i * 2) + 1) * Math.PI / SIN_LEN); // checked against the real chips

                // we never reach zero here due to ((i*2)+1)

                double o;
                if (m > 0.0)
                    o = 8 * Math.log(1.0 / m) / Math.log(2.0); // convert to 'decibels'
                else
                    o = 8 * Math.log(-1.0 / m) / Math.log(2.0); // convert to 'decibels'

                o = o / (ENV_STEP / 4);

                int n = (int) (2.0 * o);
                if ((n & 1) != 0) // round to nearest
                    n = (n >> 1) + 1;
                else
                    n = n >> 1;

                sinTab[i] = n * 2 + (m >= 0.0 ? 0 : 1);

                //Debug.printf("FMthis.C: sin [%4i (hex=%03x)]= %4i (tl_tab value=%5i)\n", i, i, sin_tab[i], tl_tab[sin_tab[i]] );
            }

            for (int i = 0; i < SIN_LEN; i++) {
                // waveform 1:  __      __
                //             /  \____/  \____
                // output only first half of the sinus waveform (positive one)

                if ((i & (1 << (SIN_BITS - 1))) != 0)
                    sinTab[1 * SIN_LEN + i] = TL_TAB_LEN;
                else
                    sinTab[1 * SIN_LEN + i] = sinTab[i];

                // waveform 2:  __  __  __  __
                //             /  \/  \/  \/  \
                // abs(sin)

                sinTab[2 * SIN_LEN + i] = sinTab[i & (SIN_MASK >> 1)];

                // waveform 3:  _   _   _   _
                //             / |_/ |_/ |_/ |_
                // abs(output only first quarter of the sinus waveform)

                if ((i & (1 << (SIN_BITS - 2))) != 0)
                    sinTab[3 * SIN_LEN + i] = TL_TAB_LEN;
                else
                    sinTab[3 * SIN_LEN + i] = sinTab[i & (SIN_MASK >> 2)];

//Debug.printf("FMthis.C: sin1[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[1*SIN_LEN+i], tl_tab[sin_tab[1*SIN_LEN+i]] );
//Debug.printf("FMthis.C: sin2[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[2*SIN_LEN+i], tl_tab[sin_tab[2*SIN_LEN+i]] );
//Debug.printf("FMthis.C: sin3[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[3*SIN_LEN+i], tl_tab[sin_tab[3*SIN_LEN+i]] );
            }
            //Debug.printf("FMthis.C: ENV_QUIET= %08x (dec*8=%i)\n", ENV_QUIET, ENV_QUIET*8 );
        }

        private void initialize() {
            // frequency base
            this.freqBase = (this.rate != 0) ? ((double) this.clock / 72.0) / this.rate : 0;

            //Debug.printf("freqbase=%f\n", this.freqbase);

            // make fnumber . increment counter table
            for (int i = 0; i < 1024; i++) {
                // opn phase increment counter = 20bit
                this.fnTab[i] = (int) ((double) i * 64 * this.freqBase * (1 << (FREQ_SH - 10))); // -10 because chips works with 10.10 fixed point, while we use 16.16

                //Debug.printf("FMthis.C: fn_tab[%d] = %x (dec=%d)\n", i, this.fn_tab[i] >> 6, this.fn_tab[i] >> 6);
            }

            for (int i = 0; i < 9; i++)
                this.chs[i].muted = 0x00;
            for (int i = 0; i < 6; i++)
                this.muteSpc[i] = 0x00;

            // Amplitude modulation: 27 output levels (triangle waveform); 1 level takes one of: 192, 256 or 448 samples
            // One entry from LFO_AM_TABLE lasts for 64 samples
            this.lfoAmInc = (int) ((1.0 / 64.0) * (1 << LFO_SH) * this.freqBase);

            // Vibrato: 8 output levels (triangle waveform); 1 level takes 1024 samples
            this.lfoPmInc = (int) ((1.0 / 1024.0) * (1 << LFO_SH) * this.freqBase);

            //Debug.printf("this.lfo_am_inc = %8x ; this.lfo_pm_inc = %8x\n", this.lfo_am_inc, this.lfo_pm_inc);

            // Noise generator: a step takes 1 sample
            this.noiseF = (int) ((1.0 / 1.0) * (1 << FREQ_SH) * this.freqBase);

            this.egTimerAdd = (int) ((1 << EG_SH) * this.freqBase);
            this.egTimerOverflow = 1 * (1 << EG_SH);
            //Debug.printf("OPLinit eg_timer_add=%8x eg_timer_overflow=%8x\n", this.eg_timer_add, this.eg_timer_overflow);
        }

        private void keyOn(Opl.Slot slot, int keySet) {
            if (slot.key == 0) {
                // restart Phase Generator
                slot.cnt = 0;
                // phase . Attack
                slot.state = EG_ATT;
            }
            slot.key |= keySet;
        }

        private void keyOff(Opl.Slot slot, int keyClr) {
            if (slot.key != 0) {
                slot.key &= keyClr;

                if (slot.key == 0) {
                    // phase . Release
                    if (slot.state > EG_REL)
                        slot.state = EG_REL;
                }
            }
        }

        /* set multi,am,vib,EG-TYP,KSR,mul */
        private void setMul(int slotNo, int v) {
            Opl.Channel ch = this.chs[slotNo / 2];
            Opl.Slot slot = ch.slots[slotNo & 1];
            slot.setMul(v, ch.fc, ch.kCode);
        }

        /** set ksl & tl */
        private void setKslTl(int slotNo, int v) {
            Opl.Channel ch = this.chs[slotNo / 2];
            Opl.Slot slot = ch.slots[slotNo & 1];
            slot.setKslTl(v, ch.kslBase);
        }

        /** set attack rate & decay rate */
        private void setArDr(int slotNo, int v) {
            Opl.Channel ch = this.chs[slotNo / 2];
            Opl.Slot slot = ch.slots[slotNo & 1];
            slot.setArDr(v);
        }

        /** set sustain level & release rate */
        private void setSlRr(int slotNo, int v) {
            Opl.Channel ch = this.chs[slotNo / 2];
            Opl.Slot slot = ch.slots[slotNo & 1];
            slot.setSlRr(v);
        }

        /** write a value v to register r on opl chips */
        private void writeReg(int r, int v) {
            int slotNo;

            //Debug.printf("writeReg:%d:%d\n", r, v);

            // adjust bus to 8 bits
            r &= 0xff;
            v &= 0xff;

            switch (r & 0xe0) {
            case 0x00: // 00-1f:control
                switch (r & 0x1f) {
                case 0x01: // waveform select enable
                    if ((this.type & OPL_TYPE_WAVESEL) != 0) {
                        this.waveSel = (byte) (v & 0x20);
                        // do not change the waveform previously selected
                    }
                    break;
                case 0x02: // Timer 1
                    this.t[0] = (256 - v) * 4;
                    break;
                case 0x03: // Timer 2
                    this.t[1] = (256 - v) * 16;
                    break;
                case 0x04: // IRQ clear / mask and Timer enable
                    if ((v & 0x80) != 0) { // IRQ flag clear
                        resetStatus(0x7f - 0x08); // don't reset BFRDY flag or we will have to call DeltaT module to set the flag
                    } else { //  set IRQ mask ,timer enable
                        byte st1 = (byte) (v & 1);
                        byte st2 = (byte) ((v >> 1) & 1);

                        // IRQRST,T1MSK,t2MSK,EOSMSK,BRMSK,x,ST2,ST1
                        resetStatus(v & (0x78 - 0x08));
                        setStatusMask((~v) & 0x78);

                        // timer 2
                        if (this.st[1] != st2) {
                            //attotime period = st2 ? attotime_mul(this.TimerBase, this.T[1]) : attotime_zero;
                            this.st[1] = st2;
                            //if (this.timer_handler) (this.timer_handler)(this.TimerParam,1,period);
                        }
                        // timer 1
                        if (this.st[0] != st1) {
                            //attotime period = st1 ? attotime_mul(this.TimerBase, this.T[0]) : attotime_zero;
                            this.st[0] = st1;
                            //if (this.timer_handler) (this.timer_handler)(this.TimerParam,0,period);
                        }
                    }
                    break;
                case 0x08: // MODE,DELTA-T control 2 : CSM,NOTESEL,x,x,smpl,da/ad,64k,rom
                    this.mode = (byte) v;
//#if BUILD_Y8950
                    //   if(this.type&OPL_TYPE_ADPCM)
                    //    YM_DELTAT_ADPCM_Write(this.DeltaT,r-0x07,v&0x0f); // mask 4 LSBs in register 08 for DELTA-T unit
//#endif
                    break;

                default:
                    //Debug.printf("FMthis.C: write to unknown register: %02x\n", r);
                    break;
                }
                break;
            case 0x20: // am ON, vib ON, ksr, eg_type, mul
                slotNo = Opl.Slot.slotArray[r & 0x1f];
                if (slotNo < 0) return;
                setMul(slotNo, v);
                break;
            case 0x40:
                slotNo = Opl.Slot.slotArray[r & 0x1f];
                if (slotNo < 0) return;
                setKslTl(slotNo, v);
                break;
            case 0x60:
                slotNo = Opl.Slot.slotArray[r & 0x1f];
                if (slotNo < 0) return;
                setArDr(slotNo, v);
                break;
            case 0x80:
                slotNo = Opl.Slot.slotArray[r & 0x1f];
                if (slotNo < 0) return;
                setSlRr(slotNo, v);
                break;
            case 0xa0:
                if (r == 0xbd) { // am depth, vibrato depth, r,bd,sd,tom,tc,hh
                    this.lfoAmDepth = (byte) (v & 0x80);
                    this.lfoPmDepthRange = (byte) ((v & 0x40) != 0 ? 8 : 0);

                    this.rhythm = (byte) (v & 0x3f);

                    if ((this.rhythm & 0x20) != 0) {
                        // BD key on/off
                        if ((v & 0x10) != 0) {
                            keyOn(this.chs[6].slots[SLOT1], 2);
                            keyOn(this.chs[6].slots[SLOT2], 2);
                        } else {
                            keyOff(this.chs[6].slots[SLOT1], ~(int) 2);
                            keyOff(this.chs[6].slots[SLOT2], ~(int) 2);
                        }
                        // HH key on/off
                        if ((v & 0x01) != 0) keyOn(this.chs[7].slots[SLOT1], 2);
                        else keyOff(this.chs[7].slots[SLOT1], ~(int) 2);
                        // SD key on/off
                        if ((v & 0x08) != 0) keyOn(this.chs[7].slots[SLOT2], 2);
                        else keyOff(this.chs[7].slots[SLOT2], ~(int) 2);
                        // TOM key on/off
                        if ((v & 0x04) != 0) keyOn(this.chs[8].slots[SLOT1], 2);
                        else keyOff(this.chs[8].slots[SLOT1], ~(int) 2);
                        // TOP-CY key on/off
                        if ((v & 0x02) != 0) keyOn(this.chs[8].slots[SLOT2], 2);
                        else keyOff(this.chs[8].slots[SLOT2], ~(int) 2);
                    } else {
                        // BD key off
                        keyOff(this.chs[6].slots[SLOT1], ~(int) 2);
                        keyOff(this.chs[6].slots[SLOT2], ~(int) 2);
                        // HH key off
                        keyOff(this.chs[7].slots[SLOT1], ~(int) 2);
                        // SD key off
                        keyOff(this.chs[7].slots[SLOT2], ~(int) 2);
                        // TOM key off
                        keyOff(this.chs[8].slots[SLOT1], ~(int) 2);
                        // TOP-CY off
                        keyOff(this.chs[8].slots[SLOT2], ~(int) 2);
                    }
                    return;
                }
                // keyon,block,fnum
                if ((r & 0x0f) > 8) return;
                Opl.Channel ch = this.chs[r & 0x0f];
                int blockFNum;
                if ((r & 0x10) == 0) { // a0-a8
                    blockFNum = (ch.blockFNum & 0x1f00) | v;
                } else { // b0-b8
                    blockFNum = ((v & 0x1f) << 8) | (ch.blockFNum & 0xff);

                    if ((v & 0x20) != 0) {
                        keyOn(ch.slots[SLOT1], 1);
                        keyOn(ch.slots[SLOT2], 1);
                    } else {
                        keyOff(ch.slots[SLOT1], ~(int) 1);
                        keyOff(ch.slots[SLOT2], ~(int) 1);
                    }
                }
                // update
                if (ch.blockFNum != blockFNum) {
                    byte block = (byte) (blockFNum >> 10);

                    ch.blockFNum = blockFNum;

                    ch.kslBase = Opl.Slot.kslTab[blockFNum >> 6];
                    ch.fc = this.fnTab[blockFNum & 0x03ff] >> (7 - block);

                    // BLK 2,1,0 bits . bits 3,2,1 of kcode
                    ch.kCode = (byte) ((ch.blockFNum & 0x1c00) >> 9);

                    // the info below is actually opposite to what is stated in the Manuals (verifed on real YM3812)
                    // if notesel == 0 . lsb of kcode is bit 10 (MSB) of fnum
                    // if notesel == 1 . lsb of kcode is bit 9 (MSB-1) of fnum
                    if ((this.mode & 0x40) != 0)
                        ch.kCode |= (byte) ((ch.blockFNum & 0x100) >> 8); // notesel == 1
                    else
                        ch.kCode |= (byte) ((ch.blockFNum & 0x200) >> 9); // notesel == 0

                    // refresh Total Level in both SLOTs of this channel
                    ch.slots[SLOT1].tll = ch.slots[SLOT1].tl + (ch.kslBase >> ch.slots[SLOT1].ksl);
                    ch.slots[SLOT2].tll = ch.slots[SLOT2].tl + (ch.kslBase >> ch.slots[SLOT2].ksl);

                    // refresh frequency counter in both SLOTs of this channel
                    ch.slots[SLOT1].caclFcSlot(ch.fc, ch.kCode);
                    ch.slots[SLOT2].caclFcSlot(ch.fc, ch.kCode);
                }
                break;
            case 0xc0:
                // FB,C
                if ((r & 0x0f) > 8) return;
                ch = this.chs[r & 0x0f];
                ch.slots[SLOT1].fb = (byte) (((v >> 1) & 7) != 0 ? ((v >> 1) & 7) + 7 : 0);
                ch.slots[SLOT1].CON = (byte) (v & 1);
                //ch.SLOT[SLOT1].connect1 = (int)(ch.SLOT[SLOT1].CON != 0 ? this.output[0] : this.phase_modulation);
                ch.slots[SLOT1].ptrConnect1 = ch.slots[SLOT1].CON != 0 ? 0 : 1;
                break;
            case 0xe0: // waveform select
                // simply ignore write to the waveform select register if selecting not enabled in test register
                if (this.waveSel != 0) {
                    slotNo = Opl.Slot.slotArray[r & 0x1f];
                    if (slotNo < 0) return;
                    ch = this.chs[slotNo / 2];

                    ch.slots[slotNo & 1].waveTable = (v & 0x03) * SIN_LEN;
                }
                break;
            }
        }

        private void reset() {

            this.egTimer = 0;
            this.egCnt = 0;

            this.noiseRng = 1; // noise shift register
            this.mode = 0; // normal mode
            resetStatus(0x7f);

            // reset with register write
            writeReg(0x01, 0); // wavesel disable
            writeReg(0x02, 0); // Timer1
            writeReg(0x03, 0); // Timer2
            writeReg(0x04, 0); // IRQ mask clear
            for (int i = 0xff; i >= 0x20; i--) writeReg(i, 0);

            // reset Operator parameters
            for (int c = 0; c < 9; c++) {
                Opl.Channel CH = this.chs[c];
                for (int s = 0; s < 2; s++) {
                    // wave table
                    CH.slots[s].waveTable = 0;
                    CH.slots[s].state = EG_OFF;
                    CH.slots[s].volume = MAX_ATT_INDEX;
                }
            }
        }

        /**
         * Create one of virtual YM3812/YM3526/Y8950
         *
         * @param clock is chips clock in Hz
         * @param rate  is sampling rate
         */
        private void create(int clock, int rate, int type) {

            this.type = (byte) type;
            this.clock = clock;
            this.rate = rate;

            // init Global tables
            initialize();
        }

        // Optional handlers

        private void setTimerHandler(Opl.TimerHandler timerHandler) {
            this.timer_handler = timerHandler;
        }

        private void setIRQHandler(Opl.IRQHandler irqHandler) {
            this.IRQHandler = irqHandler;
        }

        private void setUpdateHandler(Opl.UpdateHandler updateHandler) {
            this.updateHandler = updateHandler;
        }

        private int write(int a, int v) {
            if ((a & 1) == 0) { // address port
                this.address = (byte) (v & 0xff);
            } else { // data port
                if (this.updateHandler != null) this.updateHandler.run(/*,0*/);
                writeReg(this.address, v);
            }
            return this.status >> 7;
        }

        private byte read(int a) {
            if ((a & 1) == 0) {
                // status port

                // opl and OPL2
                return (byte) (this.status & (this.statusMask | 0x80));
            }

            return (byte) 0xff;
        }

        /** CSM Key Controller */
        private void controlCSMKey(Opl.Channel ch) {
            keyOn(ch.slots[SLOT1], 4);
            keyOn(ch.slots[SLOT2], 4);

            // The key off should happen exactly one sample later - not implemented correctly yet

            keyOff(ch.slots[SLOT1], ~(int) 4);
            keyOff(ch.slots[SLOT2], ~(int) 4);
        }


        private int timerOver(int c) {
            if (c != 0) { // Timer B
                setStatus(0x20);
            } else { // Timer A
                setStatus(0x40);
                // CSM mode key,TL controll
                if ((this.mode & 0x80) != 0) { // CSM mode total level latch and auto key on
                    if (this.updateHandler != null) this.updateHandler.run(/*,0*/);
                    for (int ch = 0; ch < 9; ch++)
                        controlCSMKey(this.chs[ch]);
                }
            }
            // reload timer
            //if (this.timer_handler) (this.timer_handler)(this.TimerParam,c,attotime_mul(this.TimerBase, this.T[c]));
            return this.status >> 7;
        }
    }

    private static final int MAX_OPL_CHIPS = 2;

    /**
     * Initialize YM3526 emulator(s).
     *
     * @param clock is the chips clock in Hz
     * @param rate  is sampling rate
     */
    public Ym3526(int clock, int rate) {
        // emulator create
        chip.create(clock, rate, Opl.OPL_TYPE_YM3526);
        reset();
    }

    /* emulator shutdown */
    public void shutdown() {
    }

    public void reset() {
        chip.reset();
    }

    public int write(int a, int v) {
        return chip.write(a, v);
    }

    public byte read(int a) {
        // ym3526 always returns bit2 and bit1 in HIGH state
        return (byte) (chip.read(a) | 0x06);
    }

    private int timerOver(int c) {
        return chip.timerOver(c);
    }

    public void setTimerHandler(Opl.TimerHandler timerHandler) {
        chip.setTimerHandler(timerHandler);
    }

    public void setIrqHandler(Opl.IRQHandler irqHandler) {
        chip.setIRQHandler(irqHandler);
    }

    public void setUpdateHandler(Opl.UpdateHandler updateHandler) {
        chip.setUpdateHandler(updateHandler);
    }

    /**
     * Generate samples for one of the YM3526's
     *
     * @param buffer is the output buffer pointer
     * @param length is the number of samples that should be generated
     */
    public void updateOne(int[][] buffer, int length) {
        byte rhythm = (byte) (chip.rhythm & 0x20);
        int[] bufL = buffer[0];
        int[] bufR = buffer[1];

        for (int i = 0; i < length; i++) {

            chip.output[0] = 0;

            chip.advanceLfo();

            // FM part
            chip.calcCh(chip.chs[0]);
            chip.calcCh(chip.chs[1]);
            chip.calcCh(chip.chs[2]);
            chip.calcCh(chip.chs[3]);
            chip.calcCh(chip.chs[4]);
            chip.calcCh(chip.chs[5]);

            if (rhythm == 0) {
                chip.calcCh(chip.chs[6]);
                chip.calcCh(chip.chs[7]);
                chip.calcCh(chip.chs[8]);
            } else { // Rhythm part
                chip.calcRh(chip.chs, (chip.noiseRng >> 0) & 1);
            }

            int lt = chip.output[0];

            lt >>= Opl.FINAL_SH;

            // limit check
            //lt = limit(lt , MAXOUT, MINOUT);

            // store to Sound buffer
            bufL[i] = lt;
            bufR[i] = lt;

            chip.advance();
        }
    }

    public void setMuteMask(int muteMask) {
        for (int c = 0; c < 9; c++)
            chip.chs[c].muted = (byte) ((muteMask >> c) & 0x01);
        for (int c = 0; c < 6; c++)
            chip.muteSpc[c] = (byte) ((muteMask >> (9 + c)) & 0x01);
    }
}
