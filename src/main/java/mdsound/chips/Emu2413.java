/*
 * Copyright (C) 2020 Mitsutaka Okazaki
 */

package mdsound.chips;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import static java.lang.System.getLogger;


/**
 * emu2413
 * <p>
 * This source refers to the following documents. The author would like to thank all the authors who have
 * contributed to the writing of them.
 * - [YM2413 notes](http://www.smspower.org/Development/YM2413) by andete
 * - ymf262.c by Jarek Burczynski
 * - [VRC7 presets](https://siliconpr0n.org/archive/doku.php?id=vendor:yamaha:opl2#opll_vrc7_patch_format) by Nuke.YKT
 * - YMF281B presets by Chabin
 *
 * @author Mitsutaka Okazaki
 * @version  v1.5.9
 * @see "https://github.com/digital-sound-antiques/emu2413"
 */
public class Emu2413 {

    private static final Logger logger = getLogger(Emu2413.class.getName());

    private final int OPLL_DEBUG = 0;

    private enum OPLL_TONE_ENUM {
        OPLL_2413_TONE,
        OPLL_VRC7_TONE,
        OPLL_281B_TONE
    }

    /* voice data */
    private static class OPLL_PATCH {

        public int TL, FB, EG, ML, AR, DR, SL, RR, KR, KL, AM, PM, WS;
    }

    /** slot */
    private static class OPLL_SLOT {

        public int number;

        /* type flags:
         * 000000SM
         *       |+-- M: 0:modulator 1:carrier
         *       +--- S: 0:normal 1:single slot mode (sd, tom, hh or cym)
         */
        public int type;

        public OPLL_PATCH patch; /* voice parameter */

        /* slot output */
        public int[] output = new int[2]; /* output value, latest and previous. */

        /* phase generator (pg) */
        public int[] wave_table; /* wave table */
        public int pg_phase;    /* pg phase */
        public int pg_out;      /* pg output, as index of wave table */
        public int pg_keep;      /* if 1, pg_phase is preserved when key-on */
        public int blk_fnum;    /* (block << 9) | f-number */
        public int fnum;        /* f-number (9 bits) */
        public int blk;          /* block (3 bits) */

        /* envelope generator (eg) */
        public int eg_state;  /* current state */
        public int volume;    /* current volume */
        public int key_flag;  /* key-on flag 1:on 0:off */
        public int sus_flag;  /* key-sus option 1:on 0:off */
        public int tll;      /* total level + key scale level*/
        public int rks;       /* key scale offset (rks) for eg speed */
        public int eg_rate_h; /* eg speed rate high 4bits */
        public int eg_rate_l; /* eg speed rate low 2bits */
        public int eg_shift; /* shift for eg global counter, controls envelope speed */
        public int eg_out;   /* eg output */

        public int update_requests; /* flags to debounce update */

//#if OPLL_DEBUG
        public int last_eg_state;
//#endif
    }

    // mask
    private int OPLL_MASK_CH(int x) {
        return 1 << x;
    }

    private static final int OPLL_MASK_HH = (1 << (9));
    private static final int OPLL_MASK_CYM = (1 << (10));
    private static final int OPLL_MASK_TOM = (1 << (11));
    private static final int OPLL_MASK_SD = (1 << (12));
    private static final int OPLL_MASK_BD = (1 << (13));
    private static final int OPLL_MASK_RHYTHM = (OPLL_MASK_HH | OPLL_MASK_CYM | OPLL_MASK_TOM | OPLL_MASK_SD | OPLL_MASK_BD);

    /* rate converter */
    private static class OPLL_RateConv {

        private int ch;
        private double timer;
        private double f_ratio;
        private int[] sinc_table;
        private short[][] buf;
    }

    private int clk;
    private int rate;

    private int chip_type;

    private int adr;

    private double inp_step;
    private double out_step;
    private double out_time;

    private final int[] reg = new int[0x40];
    private int test_flag;
    private int slot_key_status;
    private int rhythm_mode;

    private int eg_counter;

    private int pm_phase;
    private int am_phase;

    private int lfo_am;

    private int noise;
    private int short_noise;

    private final int[] patch_number = new int[9];
    private final OPLL_SLOT[] slot = new OPLL_SLOT[18];
    private final OPLL_PATCH[][] patch = {
            new OPLL_PATCH[2], new OPLL_PATCH[2], new OPLL_PATCH[2], new OPLL_PATCH[2],
            new OPLL_PATCH[2], new OPLL_PATCH[2], new OPLL_PATCH[2], new OPLL_PATCH[2],
            new OPLL_PATCH[2], new OPLL_PATCH[2], new OPLL_PATCH[2], new OPLL_PATCH[2],
            new OPLL_PATCH[2], new OPLL_PATCH[2], new OPLL_PATCH[2], new OPLL_PATCH[2],
            new OPLL_PATCH[2], new OPLL_PATCH[2], new OPLL_PATCH[2]
    };

    private final int[] pan = new int[16];
    private final float[][] pan_fine = {
            new float[2], new float[2], new float[2], new float[2],
            new float[2], new float[2], new float[2], new float[2],
            new float[2], new float[2], new float[2], new float[2],
            new float[2], new float[2], new float[2], new float[2]};

    private int mask;

    /* channel output */
    /* 0..8:tone 9:bd 10:hh 11:sd 12:tom 13:cym */
    private final short[] ch_out = new short[14];

    private final short[] mix_out = new short[2];

    private OPLL_RateConv conv;

    private int panCh = 0;

    private static final double _PI_ = 3.14159265358979323846264338327950288;

    private static final int OPLL_TONE_NUM = 3;
    private static final short[][] default_inst = {
            {
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 0: User
                    0x71, 0x61, 0x1e, 0x17, 0xd0, 0x78, 0x00, 0x17, // 1: Violin
                    0x13, 0x41, 0x1a, 0x0d, 0xd8, 0xf7, 0x23, 0x13, // 2: Guitar
                    0x13, 0x01, 0x99, 0x00, 0xf2, 0xc4, 0x21, 0x23, // 3: Piano
                    0x11, 0x61, 0x0e, 0x07, 0x8d, 0x64, 0x70, 0x27, // 4: Flute
                    0x32, 0x21, 0x1e, 0x06, 0xe1, 0x76, 0x01, 0x28, // 5: Clarinet
                    0x31, 0x22, 0x16, 0x05, 0xe0, 0x71, 0x00, 0x18, // 6: Oboe
                    0x21, 0x61, 0x1d, 0x07, 0x82, 0x81, 0x11, 0x07, // 7: Trumpet
                    0x33, 0x21, 0x2d, 0x13, 0xb0, 0x70, 0x00, 0x07, // 8: Organ
                    0x61, 0x61, 0x1b, 0x06, 0x64, 0x65, 0x10, 0x17, // 9: Horn
                    0x41, 0x61, 0x0b, 0x18, 0x85, 0xf0, 0x81, 0x07, // A: Synthesizer
                    0x33, 0x01, 0x83, 0x11, 0xea, 0xef, 0x10, 0x04, // B: Harpsichord
                    0x17, 0xc1, 0x24, 0x07, 0xf8, 0xf8, 0x22, 0x12, // C: Vibraphone
                    0x61, 0x50, 0x0c, 0x05, 0xd2, 0xf5, 0x40, 0x42, // D: Synthsizer Bass
                    0x01, 0x01, 0x55, 0x03, 0xe9, 0x90, 0x03, 0x02, // E: Acoustic Bass
                    0x41, 0x41, 0x89, 0x03, 0xf1, 0xe4, 0xc0, 0x13, // F: Electric Guitar
                    0x01, 0x01, 0x18, 0x0f, 0xdf, 0xf8, 0x6a, 0x6d, // R: Bass Drum (from VRC7)
                    0x01, 0x01, 0x00, 0x00, 0xc8, 0xd8, 0xa7, 0x68, // R: High-Hat(M) / Snare Drum(C) (from VRC7)
                    0x05, 0x01, 0x00, 0x00, 0xf8, 0xaa, 0x59, 0x55, // R: Tom-tom(M) / Top Cymbal(C) (from VRC7)
            },
            {
                    /* VRC7 presets from Nuke.YKT */
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x03, 0x21, 0x05, 0x06, 0xe8, 0x81, 0x42, 0x27,
                    0x13, 0x41, 0x14, 0x0d, 0xd8, 0xf6, 0x23, 0x12,
                    0x11, 0x11, 0x08, 0x08, 0xfa, 0xb2, 0x20, 0x12,
                    0x31, 0x61, 0x0c, 0x07, 0xa8, 0x64, 0x61, 0x27,
                    0x32, 0x21, 0x1e, 0x06, 0xe1, 0x76, 0x01, 0x28,
                    0x02, 0x01, 0x06, 0x00, 0xa3, 0xe2, 0xf4, 0xf4,
                    0x21, 0x61, 0x1d, 0x07, 0x82, 0x81, 0x11, 0x07,
                    0x23, 0x21, 0x22, 0x17, 0xa2, 0x72, 0x01, 0x17,
                    0x35, 0x11, 0x25, 0x00, 0x40, 0x73, 0x72, 0x01,
                    0xb5, 0x01, 0x0f, 0x0F, 0xa8, 0xa5, 0x51, 0x02,
                    0x17, 0xc1, 0x24, 0x07, 0xf8, 0xf8, 0x22, 0x12,
                    0x71, 0x23, 0x11, 0x06, 0x65, 0x74, 0x18, 0x16,
                    0x01, 0x02, 0xd3, 0x05, 0xc9, 0x95, 0x03, 0x02,
                    0x61, 0x63, 0x0c, 0x00, 0x94, 0xC0, 0x33, 0xf6,
                    0x21, 0x72, 0x0d, 0x00, 0xc1, 0xd5, 0x56, 0x06,
                    0x01, 0x01, 0x18, 0x0f, 0xdf, 0xf8, 0x6a, 0x6d,
                    0x01, 0x01, 0x00, 0x00, 0xc8, 0xd8, 0xa7, 0x68,
                    0x05, 0x01, 0x00, 0x00, 0xf8, 0xaa, 0x59, 0x55,
            },
            {
                    /* YMF281B presets */
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 0: User
                    0x62, 0x21, 0x1a, 0x07, 0xf0, 0x6f, 0x00, 0x16, // 1: Electric Strings (form Chabin's patch)
                    0x40, 0x10, 0x45, 0x00, 0xf6, 0x83, 0x73, 0x63, // 2: Bow Wow (based on plgDavid's patch, KSL fixed)
                    0x13, 0x01, 0x99, 0x00, 0xf2, 0xc3, 0x21, 0x23, // 3: Electric Guitar (similar to YM2413 but different DR(C))
                    0x01, 0x61, 0x0b, 0x0f, 0xf9, 0x64, 0x70, 0x17, // 4: Organ (based on Chabin, TL/DR fixed)
                    0x32, 0x21, 0x1e, 0x06, 0xe1, 0x76, 0x01, 0x28, // 5: Clarinet (identical to YM2413)
                    0x60, 0x01, 0x82, 0x0e, 0xf9, 0x61, 0x20, 0x27, // 6: Saxophone (based on plgDavid, PM/EG fixed)
                    0x21, 0x61, 0x1c, 0x07, 0x84, 0x81, 0x11, 0x07, // 7: Trumpet (similar to YM2413 but different TL/DR(M))
                    0x37, 0x32, 0xc9, 0x01, 0x66, 0x64, 0x40, 0x28, // 8: Street Organ (from Chabin)
                    0x01, 0x21, 0x07, 0x03, 0xa5, 0x71, 0x51, 0x07, // 9: Synth Brass (based on Chabin, TL fixed)
                    0x06, 0x01, 0x5e, 0x07, 0xf3, 0xf3, 0xf6, 0x13, // A: Electric Piano (based on Chabin, DR/RR/KR fixed)
                    0x00, 0x00, 0x18, 0x06, 0xf5, 0xf3, 0x20, 0x23, // B: Bass (based on Chabin, EG fixed)
                    0x17, 0xc1, 0x24, 0x07, 0xf8, 0xf8, 0x22, 0x12, // C: Vibraphone (identical to YM2413)
                    0x35, 0x64, 0x00, 0x00, 0xff, 0xf3, 0x77, 0xf5, // D: Chimes (from plgDavid)
                    0x11, 0x31, 0x00, 0x07, 0xdd, 0xf3, 0xff, 0xfb, // E: Tom Tom II (from plgDavid)
                    0x3a, 0x21, 0x00, 0x07, 0x80, 0x84, 0x0f, 0xf5, // F: Noise (based on plgDavid, AR fixed)
                    0x01, 0x01, 0x18, 0x0f, 0xdf, 0xf8, 0x6a, 0x6d, // R: Bass Drum (identical to YM2413)
                    0x01, 0x01, 0x00, 0x00, 0xc8, 0xd8, 0xa7, 0x68, // R: High-Hat(M) / Snare Drum(C) (identical to YM2413)
                    0x05, 0x01, 0x00, 0x00, 0xf8, 0xaa, 0x59, 0x55, // R: Tom-tom(M) / Top Cymbal(C) (identical to YM2413)
            }
    };

    // sine table
    private static final int PG_BITS = 10; /* 2^10 = 1024 length sine table */
    private static final int PG_WIDTH = (1 << PG_BITS);

    // phase increment counter
    private static final int DP_BITS = 19;
    private static final int DP_WIDTH = (1 << DP_BITS);
    private static final int DP_BASE_BITS = (DP_BITS - PG_BITS);

    // dynamic range of envelope output
    private static final double EG_STEP = 0.375;
    private static final int EG_BITS = 7;
    private static final int EG_MUTE = ((1 << EG_BITS) - 1);
    private static final int EG_MAX = (EG_MUTE - 4);

    // dynamic range of total level
    private static final double TL_STEP = 0.75;
    private static final int TL_BITS = 6;

    // dynamic range of sustain level
    private static final double SL_STEP = 3.0;
    private static final int SL_BITS = 4;

    /** damper speed before key-on. key-scale affects. */
    private static final int DAMPER_RATE = 12;

    private static int TL2EG(int d) {
        return d << 1;
    }

    /* exp_table[x] = round((exp2((double)x / 256.0) - 1) * 1024) */
    private static final int[] exp_table = {
            0, 3, 6, 8, 11, 14, 17, 20, 22, 25, 28, 31, 34, 37, 40, 42,
            45, 48, 51, 54, 57, 60, 63, 66, 69, 72, 75, 78, 81, 84, 87, 90,
            93, 96, 99, 102, 105, 108, 111, 114, 117, 120, 123, 126, 130, 133, 136, 139,
            142, 145, 148, 152, 155, 158, 161, 164, 168, 171, 174, 177, 181, 184, 187, 190,
            194, 197, 200, 204, 207, 210, 214, 217, 220, 224, 227, 231, 234, 237, 241, 244,
            248, 251, 255, 258, 262, 265, 268, 272, 276, 279, 283, 286, 290, 293, 297, 300,
            304, 308, 311, 315, 318, 322, 326, 329, 333, 337, 340, 344, 348, 352, 355, 359,
            363, 367, 370, 374, 378, 382, 385, 389, 393, 397, 401, 405, 409, 412, 416, 420,
            424, 428, 432, 436, 440, 444, 448, 452, 456, 460, 464, 468, 472, 476, 480, 484,
            488, 492, 496, 501, 505, 509, 513, 517, 521, 526, 530, 534, 538, 542, 547, 551,
            555, 560, 564, 568, 572, 577, 581, 585, 590, 594, 599, 603, 607, 612, 616, 621,
            625, 630, 634, 639, 643, 648, 652, 657, 661, 666, 670, 675, 680, 684, 689, 693,
            698, 703, 708, 712, 717, 722, 726, 731, 736, 741, 745, 750, 755, 760, 765, 770,
            774, 779, 784, 789, 794, 799, 804, 809, 814, 819, 824, 829, 834, 839, 844, 849,
            854, 859, 864, 869, 874, 880, 885, 890, 895, 900, 906, 911, 916, 921, 927, 932,
            937, 942, 948, 953, 959, 964, 969, 975, 980, 986, 991, 996, 1002, 1007, 1013, 1018
    };
    /* fullSin_table[x] = round(-log2(sin((x + 0.5) * PI / (PG_WIDTH / 4) / 2)) * 256) */
    private static final int[] fullsin_table = {//PG_WIDTH
            2137, 1731, 1543, 1419, 1326, 1252, 1190, 1137, 1091, 1050, 1013, 979, 949, 920, 894, 869,
            846, 825, 804, 785, 767, 749, 732, 717, 701, 687, 672, 659, 646, 633, 621, 609,
            598, 587, 576, 566, 556, 546, 536, 527, 518, 509, 501, 492, 484, 476, 468, 461,
            453, 446, 439, 432, 425, 418, 411, 405, 399, 392, 386, 380, 375, 369, 363, 358,
            352, 347, 341, 336, 331, 326, 321, 316, 311, 307, 302, 297, 293, 289, 284, 280,
            276, 271, 267, 263, 259, 255, 251, 248, 244, 240, 236, 233, 229, 226, 222, 219,
            215, 212, 209, 205, 202, 199, 196, 193, 190, 187, 184, 181, 178, 175, 172, 169,
            167, 164, 161, 159, 156, 153, 151, 148, 146, 143, 141, 138, 136, 134, 131, 129,
            127, 125, 122, 120, 118, 116, 114, 112, 110, 108, 106, 104, 102, 100, 98, 96,
            94, 92, 91, 89, 87, 85, 83, 82, 80, 78, 77, 75, 74, 72, 70, 69,
            67, 66, 64, 63, 62, 60, 59, 57, 56, 55, 53, 52, 51, 49, 48, 47,
            46, 45, 43, 42, 41, 40, 39, 38, 37, 36, 35, 34, 33, 32, 31, 30,
            29, 28, 27, 26, 25, 24, 23, 23, 22, 21, 20, 20, 19, 18, 17, 17,
            16, 15, 15, 14, 13, 13, 12, 12, 11, 10, 10, 9, 9, 8, 8, 7,
            7, 7, 6, 6, 5, 5, 5, 4, 4, 4, 3, 3, 3, 2, 2, 2,
            2, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0,

            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

    };

    private static int[] halfSin_table = new int[PG_WIDTH];
    private static int[][] wave_table_map = {
            new int[PG_WIDTH], new int[PG_WIDTH]
    };// { fullSin_table, halfSin_table };

    /* pitch modulator */
    /* offset to fNum, rough approximation of 14 cents depth. */
    private static final int[][] pm_table = {
            {0, 0, 0, 0, 0, 0, 0, 0},    // fnum = 000xxxxxx
            {0, 0, 1, 0, 0, 0, -1, 0},   // fnum = 001xxxxxx
            {0, 1, 2, 1, 0, -1, -2, -1}, // fnum = 010xxxxxx
            {0, 1, 3, 1, 0, -1, -3, -1}, // fnum = 011xxxxxx
            {0, 2, 4, 2, 0, -2, -4, -2}, // fnum = 100xxxxxx
            {0, 2, 5, 2, 0, -2, -5, -2}, // fnum = 101xxxxxx
            {0, 3, 6, 3, 0, -3, -6, -3}, // fnum = 110xxxxxx
            {0, 3, 7, 3, 0, -3, -7, -3}, // fnum = 111xxxxxx
    };

    /* amplitude lfo table */
    /* The following envelop pattern is verified on real YM2413. */
    /* each element repeats 64 cycles */
    private static final int[] am_table = {
            0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1,  //
            2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3,  //
            4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5,  //
            6, 6, 6, 6, 6, 6, 6, 6, 7, 7, 7, 7, 7, 7, 7, 7,  //
            8, 8, 8, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9, 9, 9,  //
            10, 10, 10, 10, 10, 10, 10, 10, 11, 11, 11, 11, 11, 11, 11, 11, //
            12, 12, 12, 12, 12, 12, 12, 12,                                 //
            13, 13, 13,                                                     //
            12, 12, 12, 12, 12, 12, 12, 12,                                 //
            11, 11, 11, 11, 11, 11, 11, 11, 10, 10, 10, 10, 10, 10, 10, 10, //
            9, 9, 9, 9, 9, 9, 9, 9, 8, 8, 8, 8, 8, 8, 8, 8,  //
            7, 7, 7, 7, 7, 7, 7, 7, 6, 6, 6, 6, 6, 6, 6, 6,  //
            5, 5, 5, 5, 5, 5, 5, 5, 4, 4, 4, 4, 4, 4, 4, 4,  //
            3, 3, 3, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2,  //
            1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0};

    /* envelope decay increment step table */
    /* based on andete's research */
    private static final int[][] eg_step_tables = {
            {0, 1, 0, 1, 0, 1, 0, 1},
            {0, 1, 0, 1, 1, 1, 0, 1},
            {0, 1, 1, 1, 0, 1, 1, 1},
            {0, 1, 1, 1, 1, 1, 1, 1},
    };

    private enum __OPLL_EG_STATE {ATTACK, DECAY, SUSTAIN, RELEASE, DAMP, UNKNOWN}

    private static final int[] ml_table = {
            1, 1 * 2, 2 * 2, 3 * 2, 4 * 2, 5 * 2, 6 * 2, 7 * 2,
            8 * 2, 9 * 2, 10 * 2, 10 * 2, 12 * 2, 12 * 2, 15 * 2, 15 * 2
    };

    //#define dB2(x) ((x)*2)
    //        static double kl_table[16] = {dB2(0.000),  dB2(9.000),  dB2(12.000), dB2(13.875), dB2(15.000), dB2(16.125),
    //                              dB2(16.875), dB2(17.625), dB2(18.000), dB2(18.750), dB2(19.125), dB2(19.500),
    //                              dB2(19.875), dB2(20.250), dB2(20.625), dB2(21.000)};
    private static final double[] kl_table = {
            0.000 * 2, 9.000 * 2, 12.000 * 2, 13.875 * 2, 15.000 * 2, 16.125 * 2,
            16.875 * 2, 17.625 * 2, 18.000 * 2, 18.750 * 2, 19.125 * 2, 19.500 * 2,
            19.875 * 2, 20.250 * 2, 20.625 * 2, 21.000 * 2
    };

    private static int[][][] tll_table;// new int[8 * 16][1 << TL_BITS][4]

    private static int[][] rks_table = {
            new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2],
            new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2]
    };

    private final OPLL_PATCH null_patch = new OPLL_PATCH() {{
        AM = 0;
        AR = 0;
        DR = 0;
        EG = 0;
        FB = 0;
        KL = 0;
        KR = 0;
        ML = 0;
        PM = 0;
        RR = 0;
        SL = 0;
        TL = 0;
        WS = 0;
    }};

    private static final OPLL_PATCH[][][] default_patch = {
            {
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()}
            },
            {
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()}
            },
            {
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()},
                    {new OPLL_PATCH(), new OPLL_PATCH()}
            }
    };

    //
    // Internal Sample Rate Converter
    //
    /* Note: to disable internal rate converter, set clock/72 to output sampling rate. */

    /*
     * LW is truncate length of sinc(x) calculation.
     * Lower LW is faster, higher LW results better quality.
     * LW must be a non-zero positive even number, no upper limit.
     * LW=16 or greater is recommended when upsampling.
     * LW=8 is practically okay for downsampling.
     */
    private static final int LW = 16;

    /* resolution of sinc(x) table. sinc(x) where 0.0<=x<1.0 corresponds to sinc_table[0...SINC_RESO-1] */
    private static final int SINC_RESO = 256;
    private static final int SINC_AMP_BITS = 12;

    // double hamming(double x) { return 0.54 - 0.46 * cos(2 * PI * x); }
    private static double blackman(double x) {
        return 0.42 - 0.5 * Math.cos(2 * _PI_ * x) + 0.08 * Math.cos(4 * _PI_ * x);
    }

    private static double sinC(double x) {
        return (x == 0.0 ? 1.0 : Math.sin(_PI_ * x) / (_PI_ * x));
    }

    private static double windowed_sinC(double x) {
        return blackman(0.5 + 0.5 * x / (LW / 2)) * sinC(x);
    }

    /** f_inp: input frequency. f_out: output frequency, ch: number of channels */
    private OPLL_RateConv OPLL_RateConv_new(double f_inp, double f_out, int ch) {
        OPLL_RateConv conv = new OPLL_RateConv();// malloc(sizeof(OPLL_RateConv));

        conv.ch = ch;
        conv.f_ratio = f_inp / f_out;
        conv.buf = new short[ch][];// malloc(sizeof(void*) * ch);
        for (int i = 0; i < ch; i++) {
            conv.buf[i] = new short[LW];// malloc(sizeof(conv->buf[0][0]) * LW);
        }

        /* create sinc_table for positive 0 <= x < LW/2 */
        conv.sinc_table = new int[SINC_RESO * LW / 2];// malloc(sizeof(conv->sinc_table[0]) * SINC_RESO * LW / 2);
        for (int i = 0; i < SINC_RESO * LW / 2; i++) {
            double x = (double) i / SINC_RESO;
            if (f_out < f_inp) {
                /* for down-sampling */
                conv.sinc_table[i] = (int) ((1 << SINC_AMP_BITS) * windowed_sinC(x / conv.f_ratio) / conv.f_ratio);
            } else {
                /* for up-sampling */
                conv.sinc_table[i] = (int) ((1 << SINC_AMP_BITS) * windowed_sinC(x));
            }
        }

        return conv;
    }

    private static int lookup_sinc_table(int[] table, double x) {
        int index = (int) (x * SINC_RESO);
        if (index < 0)
            index = -index;
        return table[Math.min(SINC_RESO * LW / 2 - 1, index)];
    }

    private void OPLL_RateConv_reset(OPLL_RateConv conv) {
        conv.timer = 0;
        for (int i = 0; i < conv.ch; i++) {
            for (int j = 0; j < LW; j++) {
                conv.buf[i][j] = 0;
            }
            //memset(conv.buf[i], 0, sizeof(conv.buf[i][0]) * LW);
        }
    }

    /* put original data to this converter at f_inp. */
    private void OPLL_RateConv_putData(OPLL_RateConv conv, int ch, short data) {
        short[] buf = conv.buf[ch];
        for (int i = 0; i < LW - 1; i++) {
            buf[i] = buf[i + 1];
        }
        buf[LW - 1] = data;
    }

    /* get resampled data from this converter at f_out. */
    /* this function must be called f_out / f_inp times per one putData call. */
    private static short OPLL_RateConv_getData(OPLL_RateConv conv, int ch) {
        short[] buf = conv.buf[ch];
        int sum = 0;
        double dn;
        conv.timer += conv.f_ratio;
        dn = conv.timer - Math.floor(conv.timer);
        conv.timer = dn;

        for (int k = 0; k < LW; k++) {
            double x = ((double) k - (LW / 2 - 1)) - dn;
            sum += buf[k] * lookup_sinc_table(conv.sinc_table, x);
        }
        return (short) (sum >> SINC_AMP_BITS);
    }

    private void OPLL_RateConv_delete(OPLL_RateConv conv) {
    }

    /**
     * Create tables
     */
    private static void makeSinTable() {
        for (int x = 0; x < PG_WIDTH / 4; x++) {
            fullsin_table[PG_WIDTH / 4 + x] = fullsin_table[PG_WIDTH / 4 - x - 1];
        }

        for (int x = 0; x < PG_WIDTH / 2; x++) {
            fullsin_table[PG_WIDTH / 2 + x] = (int) (0x8000 | fullsin_table[x]);
        }

        for (int x = 0; x < PG_WIDTH / 2; x++)
            halfSin_table[x] = fullsin_table[x];

        for (int x = PG_WIDTH / 2; x < PG_WIDTH; x++)
            halfSin_table[x] = 0xfff;
        for (int j = 0; j < PG_WIDTH; j++) {
            wave_table_map[0][j] = fullsin_table[j];
            wave_table_map[1][j] = halfSin_table[j];
        }
    }

    private static void makeTllTable() {
        if (tll_table == null) {
            tll_table = new int[8 * 16][][];
            for (int i = 0; i < 8 * 16; i++) {
                tll_table[i] = new int[1 << TL_BITS][];
                for (int j = 0; j < 1 << TL_BITS; j++) {
                    tll_table[i][j] = new int[4];
                    for (int k = 0; k < 4; k++) {
                        tll_table[i][j][k] = 0;
                    }
                }
            }
        }

        for (int fnum = 0; fnum < 16; fnum++) {
            for (int block = 0; block < 8; block++) {
                for (int TL = 0; TL < 64; TL++) {
                    for (int KL = 0; KL < 4; KL++) {
                        if (KL == 0) {
                            tll_table[(block << 4) | fnum][TL][KL] = (int) TL2EG(TL);
                        } else {
                            int tmp = (int) (kl_table[fnum] - (3.000) * 2 * (7 - block));
                            if (tmp <= 0)
                                tll_table[(block << 4) | fnum][TL][KL] = TL2EG(TL);
                            else
                                tll_table[(block << 4) | fnum][TL][KL] = (int) ((tmp >> (3 - KL)) / EG_STEP) + (int) TL2EG(TL);
                        }
                    }
                }
            }
        }
    }

    private static void makeRksTable() {
        for (int fnum8 = 0; fnum8 < 2; fnum8++)
            for (int block = 0; block < 8; block++) {
                rks_table[(block << 1) | fnum8][1] = (block << 1) + fnum8;
                rks_table[(block << 1) | fnum8][0] = block >> 1;
            }
    }

    private void makeDefaultPatch() {
        for (int i = 0; i < OPLL_TONE_NUM; i++)
            for (int j = 0; j < 19; j++)
                OPLL_getDefaultPatch(i, j, default_patch);
    }

    private int table_initialized = 0;

    private void initializeTables() {
        makeTllTable();
        makeRksTable();
        makeSinTable();
        makeDefaultPatch();
        table_initialized = 1;
    }

    //
    // Synthesizing
    //
    private int SLOT_BD1 = 12;
    private int SLOT_BD2 = 13;
    private int SLOT_HH = 14;
    private int SLOT_SD = 15;
    private int SLOT_TOM = 16;
    private int SLOT_CYM = 17;

    /* utility macros */
    private OPLL_SLOT MOD(int x) {
        return this.slot[x << 1];
    }

    private OPLL_SLOT CAR(int x) {
        return this.slot[(x << 1) | 1];
    }

    private static int BIT(int s, int b) {
        return (s >> b) & 1;
    }

//#if OPLL_DEBUG
    private void _debug_print_patch(OPLL_SLOT slot) {
        OPLL_PATCH p = slot.patch;
logger.log(Level.TRACE, "[slot#{0} am:{1} pm:{2} eg:{3} kr:{4} ml:{5} kl:{6} tl:{7} ws:{8} fb:{9} A:{10} D:{11} S:{12} R:{13}]",
                slot.number, //
                p.AM, p.PM, p.EG, p.KR, p.ML,                                                                     //
                p.KL, p.TL, p.WS, p.FB,                                                                            //
                p.AR, p.DR, p.EG, p.SL, p.RR);
    }

    private String _debug_eg_state_name(OPLL_SLOT slot) {
        switch (__OPLL_EG_STATE.values()[slot.eg_state]) {
            case ATTACK:
                return "attack";
            case DECAY:
                return "decay";
            case SUSTAIN:
                return "sustain";
            case RELEASE:
                return "release";
            case DAMP:
                return "damp";
            default:
                return "unknown";
        }
    }

    private void _debug_print_slot_info(OPLL_SLOT slot) {
        String name = _debug_eg_state_name(slot);
logger.log(Level.TRACE, "[slot#{0} state:{1} fnum:{2:03x} rate:{3}-{4}]",
                slot.number, name, slot.blk_fnum, slot.eg_rate_h,
                slot.eg_rate_l);
        _debug_print_patch(slot);
        //fflush(stdout);
    }
//#endif

    private int get_parameter_rate(OPLL_SLOT slot) {

        if ((slot.type & 1) == 0 && slot.key_flag == 0) {
            return 0;
        }

        switch (__OPLL_EG_STATE.values()[slot.eg_state]) {
            case ATTACK:
                return slot.patch.AR;
            case DECAY:
                return slot.patch.DR;
            case SUSTAIN:
                return slot.patch.EG != 0 ? 0 : slot.patch.RR;
            case RELEASE:
                if (slot.sus_flag != 0) {
                    return 5;
                } else if (slot.patch.EG != 0) {
                    return slot.patch.RR;
                } else {
                    return 7;
                }
            case DAMP:
                return DAMPER_RATE;
            default:
                return 0;
        }
    }

    private enum SLOT_UPDATE_FLAG {
        UPDATE_WS(1),
        UPDATE_TLL(2),
        UPDATE_RKS(4),
        UPDATE_EG(8),
        UPDATE_ALL(255);
        final int v;

        SLOT_UPDATE_FLAG(int v) {
            this.v = v;
        }
    }

    private void request_update(OPLL_SLOT slot, int flag) {
        slot.update_requests |= flag;
    }

    private void commit_slot_update(OPLL_SLOT slot) {
//#if OPLL_DEBUG
        if (slot.last_eg_state != slot.eg_state) {
            _debug_print_slot_info(slot);
            slot.last_eg_state = slot.eg_state;
        }
//#endif

        if ((slot.update_requests & SLOT_UPDATE_FLAG.UPDATE_WS.v) != 0) {
            slot.wave_table = wave_table_map[slot.patch.WS];
        }

        if ((slot.update_requests & SLOT_UPDATE_FLAG.UPDATE_TLL.v) != 0) {
            if ((slot.type & 1) == 0) {
                slot.tll = tll_table[slot.blk_fnum >> 5][slot.patch.TL][slot.patch.KL];
            } else {
                slot.tll = tll_table[slot.blk_fnum >> 5][slot.volume][slot.patch.KL];
            }
        }

        if ((slot.update_requests & SLOT_UPDATE_FLAG.UPDATE_RKS.v) != 0) {
            slot.rks = rks_table[slot.blk_fnum >> 8][slot.patch.KR];
        }

        if ((slot.update_requests & ((int) SLOT_UPDATE_FLAG.UPDATE_RKS.v | (int) SLOT_UPDATE_FLAG.UPDATE_EG.v)) != 0) {
            int p_rate = get_parameter_rate(slot);

            if (p_rate == 0) {
                slot.eg_shift = 0;
                slot.eg_rate_h = 0;
                slot.eg_rate_l = 0;
                return;
            }

            slot.eg_rate_h = Math.min(15, p_rate + (slot.rks >> 2));
            slot.eg_rate_l = slot.rks & 3;
            if (slot.eg_state == __OPLL_EG_STATE.ATTACK.ordinal()) {
                slot.eg_shift = (0 < slot.eg_rate_h && slot.eg_rate_h < 12) ? (13 - slot.eg_rate_h) : 0;
            } else {
                slot.eg_shift = (slot.eg_rate_h < 13) ? (13 - slot.eg_rate_h) : 0;
            }
        }

        slot.update_requests = 0;
    }

    private void reset_slot(OPLL_SLOT slot, int number) {
        slot.number = number;
        slot.type = number % 2;
        slot.pg_keep = 0;
        slot.wave_table = wave_table_map[0];
        slot.pg_phase = 0;
        slot.output[0] = 0;
        slot.output[1] = 0;
        slot.eg_state = __OPLL_EG_STATE.RELEASE.ordinal();
        slot.eg_shift = 0;
        slot.rks = 0;
        slot.tll = 0;
        slot.key_flag = 0;
        slot.sus_flag = 0;
        slot.blk_fnum = 0;
        slot.blk = 0;
        slot.fnum = 0;
        slot.volume = 0;
        slot.pg_out = 0;
        slot.eg_out = EG_MUTE;
        slot.patch = null_patch;
    }

    private void slotOn(int i) {
        OPLL_SLOT slot = this.slot[i];
        slot.key_flag = 1;
        slot.eg_state = __OPLL_EG_STATE.DAMP.ordinal();
        request_update(slot, SLOT_UPDATE_FLAG.UPDATE_EG.v);
    }

    private void slotOff(int i) {
        OPLL_SLOT slot = this.slot[i];
        slot.key_flag = 0;
        if ((slot.type & 1) != 0) {
            slot.eg_state = __OPLL_EG_STATE.RELEASE.ordinal();
            request_update(slot, SLOT_UPDATE_FLAG.UPDATE_EG.v);
        }
    }

    private void update_key_status() {
        int r14 = this.reg[0x0e];
        int rhythm_mode = BIT(r14, 5);
        int new_slot_key_status = 0;
        int updated_status;

        for (int ch = 0; ch < 9; ch++)
            if ((this.reg[0x20 + ch] & 0x10) != 0)
                new_slot_key_status |= 3 << (ch * 2);

        if (rhythm_mode != 0) {
            if ((r14 & 0x10) != 0)
                new_slot_key_status |= 3 << SLOT_BD1;

            if ((r14 & 0x01) != 0)
                new_slot_key_status |= 1 << SLOT_HH;

            if ((r14 & 0x08) != 0)
                new_slot_key_status |= 1 << SLOT_SD;

            if ((r14 & 0x04) != 0)
                new_slot_key_status |= 1 << SLOT_TOM;

            if ((r14 & 0x02) != 0)
                new_slot_key_status |= 1 << SLOT_CYM;
        }

        updated_status = this.slot_key_status ^ new_slot_key_status;

        if (updated_status != 0) {
            for (int i = 0; i < 18; i++)
                if (BIT(updated_status, i) != 0) {
                    if (BIT(new_slot_key_status, i) != 0) {
                        slotOn(i);
                    } else {
                        slotOff(i);
                    }
                }
        }

        this.slot_key_status = new_slot_key_status;
    }

    private void set_patch(int ch, int num) {
        this.patch_number[ch] = num;
        MOD(ch).patch = this.patch[num][0];
        CAR(ch).patch = this.patch[num][1];
        request_update(MOD(ch), SLOT_UPDATE_FLAG.UPDATE_ALL.v);
        request_update(CAR(ch), SLOT_UPDATE_FLAG.UPDATE_ALL.v);
    }

    private void set_sus_flag(int ch, int flag) {
        CAR(ch).sus_flag = flag;
        request_update(CAR(ch), SLOT_UPDATE_FLAG.UPDATE_EG.v);
        if ((MOD(ch).type & 1) != 0) {
            MOD(ch).sus_flag = flag;
            request_update(MOD(ch), SLOT_UPDATE_FLAG.UPDATE_EG.v);
        }
    }

    /* set volume ( volume : 6bit, register value << 2 ) */
    private void set_volume(int ch, int volume) {
        CAR(ch).volume = volume;
        request_update(CAR(ch), SLOT_UPDATE_FLAG.UPDATE_TLL.v);
    }

    private void set_slot_volume(OPLL_SLOT slot, int volume) {
        slot.volume = volume;
        request_update(slot, SLOT_UPDATE_FLAG.UPDATE_TLL.v);
    }

    /* set f-Nnmber ( fNum : 9bit ) */
    private void set_fnumber(int ch, int fNum) {
        OPLL_SLOT car = CAR(ch);
        OPLL_SLOT mod = MOD(ch);
        car.fnum = fNum;
        car.blk_fnum = (int) ((car.blk_fnum & 0xe00) | (fNum & 0x1ff));
        mod.fnum = fNum;
        mod.blk_fnum = (mod.blk_fnum & 0xe00) | (fNum & 0x1ff);
        request_update(car, SLOT_UPDATE_FLAG.UPDATE_EG.v | SLOT_UPDATE_FLAG.UPDATE_RKS.v | SLOT_UPDATE_FLAG.UPDATE_TLL.v);
        request_update(mod, SLOT_UPDATE_FLAG.UPDATE_EG.v | SLOT_UPDATE_FLAG.UPDATE_RKS.v | SLOT_UPDATE_FLAG.UPDATE_TLL.v);
    }

    /* set block data (blk : 3bit ) */
    private void set_block(int ch, int blk) {
        OPLL_SLOT car = CAR(ch);
        OPLL_SLOT mod = MOD(ch);
        car.blk = blk;
        car.blk_fnum = ((blk & 7) << 9) | (car.blk_fnum & 0x1ff);
        mod.blk = blk;
        mod.blk_fnum = ((blk & 7) << 9) | (mod.blk_fnum & 0x1ff);
        request_update(car, SLOT_UPDATE_FLAG.UPDATE_EG.v | SLOT_UPDATE_FLAG.UPDATE_RKS.v | SLOT_UPDATE_FLAG.UPDATE_TLL.v);
        request_update(mod, SLOT_UPDATE_FLAG.UPDATE_EG.v | SLOT_UPDATE_FLAG.UPDATE_RKS.v | SLOT_UPDATE_FLAG.UPDATE_TLL.v);
    }

    private void update_rhythm_mode() {
        int new_rhythm_mode = (this.reg[0x0e] >> 5) & 1;

        if (this.rhythm_mode != new_rhythm_mode) {

            if (new_rhythm_mode != 0) {
                this.slot[SLOT_HH].type = 3;
                this.slot[SLOT_HH].pg_keep = 1;
                this.slot[SLOT_SD].type = 3;
                this.slot[SLOT_TOM].type = 3;
                this.slot[SLOT_CYM].type = 3;
                this.slot[SLOT_CYM].pg_keep = 1;
                set_patch(6, 16);
                set_patch(7, 17);
                set_patch(8, 18);
                set_slot_volume(this.slot[SLOT_HH], ((this.reg[0x37] >> 4) & 15) << 2);
                set_slot_volume(this.slot[SLOT_TOM], ((this.reg[0x38] >> 4) & 15) << 2);
            } else {
                this.slot[SLOT_HH].type = 0;
                this.slot[SLOT_HH].pg_keep = 0;
                this.slot[SLOT_SD].type = 1;
                this.slot[SLOT_TOM].type = 0;
                this.slot[SLOT_CYM].type = 1;
                this.slot[SLOT_CYM].pg_keep = 0;
                set_patch(6, this.reg[0x36] >> 4);
                set_patch(7, this.reg[0x37] >> 4);
                set_patch(8, this.reg[0x38] >> 4);
            }
        }

        this.rhythm_mode = new_rhythm_mode;
    }

    private void update_ampm() {
        if ((this.test_flag & 2) != 0) {
            this.pm_phase = 0;
            this.am_phase = 0;
        } else {
            this.pm_phase += (this.test_flag & 8) != 0 ? 1024 : 1;
            this.am_phase += (this.test_flag & 8) != 0 ? 64 : 1;
        }
        this.lfo_am = am_table[(this.am_phase >> 6) % am_table.length]; // sizeof(am_table)];
    }

    private void update_noise(int cycle) {
        for (int i = 0; i < cycle; i++) {
            if ((this.noise & 1) != 0) {
                this.noise ^= 0x800200;
            }
            this.noise >>= 1;
        }
    }

    private void update_short_noise() {
        int pg_hh = this.slot[SLOT_HH].pg_out;
        int pg_cym = this.slot[SLOT_CYM].pg_out;

        int h_bit2 = BIT(pg_hh, PG_BITS - 8);
        int h_bit7 = BIT(pg_hh, PG_BITS - 3);
        int h_bit3 = BIT(pg_hh, PG_BITS - 7);

        int c_bit3 = BIT(pg_cym, PG_BITS - 7);
        int c_bit5 = BIT(pg_cym, PG_BITS - 5);

        this.short_noise = (h_bit2 ^ h_bit7) | (h_bit3 ^ c_bit5) | (c_bit3 ^ c_bit5);
    }

    private static void calc_phase(OPLL_SLOT slot, int pm_phase, int reset) {
        int pm = slot.patch.PM != 0 ? pm_table[(slot.fnum >> 6) & 7][(pm_phase >> 10) & 7] : 0;
        if (reset != 0) {
            slot.pg_phase = 0;
        }
        slot.pg_phase += (int) ((((slot.fnum & 0x1ff) * 2 + pm) * ml_table[slot.patch.ML]) << slot.blk >> 2);
        slot.pg_phase &= (DP_WIDTH - 1);
        slot.pg_out = slot.pg_phase >> DP_BASE_BITS;
    }

    private static int lookup_attack_step(OPLL_SLOT slot, int counter) {
        int index;

        switch (slot.eg_rate_h) {
            case 12:
                index = (counter & 0xc) >> 1;
                return 4 - eg_step_tables[slot.eg_rate_l][index];
            case 13:
                index = (counter & 0xc) >> 1;
                return 3 - eg_step_tables[slot.eg_rate_l][index];
            case 14:
                index = (counter & 0xc) >> 1;
                return 2 - eg_step_tables[slot.eg_rate_l][index];
            case 0:
            case 15:
                return 0;
            default:
                index = counter >> slot.eg_shift;
                return eg_step_tables[slot.eg_rate_l][index & 7] != 0 ? 4 : 0;
        }
    }

    private static int lookup_decay_step(OPLL_SLOT slot, int counter) {
        int index;

        switch (slot.eg_rate_h) {
            case 0:
                return 0;
            case 13:
                index = ((counter & 0xc) >> 1) | (counter & 1);
                return eg_step_tables[slot.eg_rate_l][index];
            case 14:
                index = ((counter & 0xc) >> 1);
                return eg_step_tables[slot.eg_rate_l][index] + 1;
            case 15:
                return 2;
            default:
                index = counter >> slot.eg_shift;
                return eg_step_tables[slot.eg_rate_l][index & 7];
        }
    }

    private void start_envelope(OPLL_SLOT slot) {
        if (Math.min(15, slot.patch.AR + (slot.rks >> 2)) == 15) {
            slot.eg_state = __OPLL_EG_STATE.DECAY.ordinal();
            slot.eg_out = 0;
        } else {
            slot.eg_state = __OPLL_EG_STATE.ATTACK.ordinal();
        }
        request_update(slot, SLOT_UPDATE_FLAG.UPDATE_EG.v);
    }

    private void calc_envelope(OPLL_SLOT slot, OPLL_SLOT buddy, int eg_counter, int test) {

        int mask = (1 << slot.eg_shift) - 1;

        if (slot.eg_state == __OPLL_EG_STATE.ATTACK.ordinal()) {
            if (0 < slot.eg_out && 0 < slot.eg_rate_h && (eg_counter & mask & ~3) == 0) {
                int s = lookup_attack_step(slot, eg_counter);
                if (0 < s) {
                    slot.eg_out = (int) Math.max(0, (int) ((int) slot.eg_out - (slot.eg_out >> s) - 1));
                }
            }
        } else {
            if (slot.eg_rate_h > 0 && (eg_counter & mask) == 0) {
                slot.eg_out = Math.min(EG_MUTE, slot.eg_out + lookup_decay_step(slot, eg_counter));
            }
        }

        switch (__OPLL_EG_STATE.values()[slot.eg_state]) {
            case DAMP:
                // DAMP to ATTACK transition is occured when the envelope reaches EG_MAX (max attenuation but it's not mute).
                // Do not forget to check (eg_counter & mask) == 0 to synchronize it with the progress of the envelope.
                if (slot.eg_out >= EG_MAX && (eg_counter & mask) == 0) {
                    start_envelope(slot);
                    if ((slot.type & 1) != 0) {
                        if (slot.pg_keep == 0) {
                            slot.pg_phase = 0;
                        }
                        if (buddy != null && buddy.pg_keep == 0) {
                            buddy.pg_phase = 0;
                        }
                    }
                }
                break;

            case ATTACK:
                if (slot.eg_out == 0) {
                    slot.eg_state = __OPLL_EG_STATE.DECAY.ordinal();
                    request_update(slot, SLOT_UPDATE_FLAG.UPDATE_EG.v);
                }
                break;

            case DECAY:
                // DECAY to SUSTAIN transition must be checked at every cycle regardless of the conditions of the envelope rate and
                // counter. i.e. the transition is not synchronized with the progress of the envelope.
                if ((slot.eg_out >> 3) == slot.patch.SL) {
                    slot.eg_state = __OPLL_EG_STATE.SUSTAIN.ordinal();
                    request_update(slot, SLOT_UPDATE_FLAG.UPDATE_EG.v);
                }
                break;

            case SUSTAIN:
            case RELEASE:
            default:
                break;
        }

        if (test != 0) {
            slot.eg_out = 0;
        }
    }

    private void update_slots() {
        this.eg_counter++;

        for (int i = 0; i < 18; i++) {
            OPLL_SLOT slot = this.slot[i];
            OPLL_SLOT buddy = null;
            if (slot.type == 0) {
                buddy = this.slot[i + 1];
            }
            if (slot.type == 1) {
                buddy = this.slot[i - 1];
            }
            if (slot.update_requests != 0) {
                commit_slot_update(slot);
            }
            calc_envelope(slot, buddy, this.eg_counter, this.test_flag & 1);
            calc_phase(slot, this.pm_phase, this.test_flag & 4);
        }
    }

    /** output: -4095...4095 */
    private int lookup_exp_table(int i) {
        // from andete's expression
        int t = exp_table[(i & 0xff) ^ 0xff] + 1024;
        int res = t >> ((i & 0x7f00) >> 8);
        return ((i & 0x8000) != 0 ? ~res : res) << 1;
    }

    private int to_linear(int h, OPLL_SLOT slot, int am) {
        if (slot.eg_out > EG_MAX)
            return 0;

        int att = Math.min(EG_MUTE, (slot.eg_out + slot.tll + am)) << 4;
        return lookup_exp_table(h + att);
    }

    private int calc_slot_car(int ch, int fm) {
        OPLL_SLOT slot = CAR(ch);

        int am = slot.patch.AM != 0 ? this.lfo_am : 0;

        slot.output[1] = slot.output[0];
        slot.output[0] = to_linear(slot.wave_table[(slot.pg_out + 2 * (fm >> 1)) & (PG_WIDTH - 1)], slot, am);

        return slot.output[0];
    }

    private int calc_slot_mod(int ch) {
        OPLL_SLOT slot = MOD(ch);

        int fm = slot.patch.FB > 0 ? ((slot.output[1] + slot.output[0]) >> (int) (9 - slot.patch.FB)) : 0;
        int am = slot.patch.AM != 0 ? this.lfo_am : 0;

        slot.output[1] = slot.output[0];
        slot.output[0] = to_linear(slot.wave_table[(slot.pg_out + fm) & (PG_WIDTH - 1)], slot, am);

        return slot.output[0];
    }

    private int calc_slot_tom() {
        OPLL_SLOT slot = MOD(8);

        return to_linear(slot.wave_table[slot.pg_out], slot, 0);
    }

    /** Specify phase offset directly based on 10-bit (1024-length) sine table */
    private int _PD(int phase) {
        return ((PG_BITS < 10) ? (phase >> (10 - PG_BITS)) : (phase << (PG_BITS - 10)));
    }

    private int calc_slot_snare() {
        OPLL_SLOT slot = CAR(7);

        int phase;

        if (BIT(slot.pg_out, PG_BITS - 2) != 0)
            phase = (this.noise & 1) != 0 ? _PD(0x300) : _PD(0x200);
        else
            phase = (this.noise & 1) != 0 ? _PD(0x0) : _PD(0x100);

        return to_linear(slot.wave_table[phase], slot, 0);
    }

    private int calc_slot_cym() {
        OPLL_SLOT slot = CAR(8);

        int phase = this.short_noise != 0 ? _PD(0x300) : _PD(0x100);

        return to_linear(slot.wave_table[phase], slot, 0);
    }

    private int calc_slot_hat() {
        OPLL_SLOT slot = MOD(7);

        int phase;

        if (this.short_noise != 0)
            phase = (this.noise & 1) != 0 ? _PD(0x2d0) : _PD(0x234);
        else
            phase = (this.noise & 1) != 0 ? _PD(0x34) : _PD(0xd0);

        return to_linear(slot.wave_table[phase], slot, 0);
    }

    private static int _MO(int x) {
        return -x >> 1;
    }

    private static int _RO(int x) {
        return x;
    }

    private void update_output() {
        short[] _out;

        update_ampm();
        update_short_noise();
        update_slots();

        _out = this.ch_out;

        /* CH1-6 */
        for (int i = 0; i < 6; i++) {
            if ((this.mask & OPLL_MASK_CH(i)) == 0) {
                _out[i] = (short) _MO(calc_slot_car(i, calc_slot_mod(i)));
            }
        }

        /* CH7 */
        if (this.rhythm_mode == 0) {
            if ((this.mask & OPLL_MASK_CH(6)) == 0) {
                _out[6] = (short) _MO(calc_slot_car(6, calc_slot_mod(6)));
            }
        } else {
            if ((this.mask & OPLL_MASK_BD) == 0) {
                _out[9] = (short) _RO(calc_slot_car(6, calc_slot_mod(6)));
            }
        }
        update_noise(14);

        /* CH8 */
        if (this.rhythm_mode == 0) {
            if ((this.mask & OPLL_MASK_CH(7)) == 0) {
                _out[7] = (short) _MO(calc_slot_car(7, calc_slot_mod(7)));
            }
        } else {
            if ((this.mask & OPLL_MASK_HH) == 0) {
                _out[10] = (short) _RO(calc_slot_hat());
            }
            if ((this.mask & OPLL_MASK_SD) == 0) {
                _out[11] = (short) _RO(calc_slot_snare());
            }
        }
        update_noise(2);

        /* CH9 */
        if (this.rhythm_mode == 0) {
            if ((this.mask & OPLL_MASK_CH(8)) == 0) {
                _out[8] = (short) _MO(calc_slot_car(8, calc_slot_mod(8)));
            }
        } else {
            if ((this.mask & OPLL_MASK_TOM) == 0) {
                _out[12] = (short) _RO(calc_slot_tom());
            }
            if ((this.mask & OPLL_MASK_CYM) == 0) {
                _out[13] = (short) _RO(calc_slot_cym());
            }
        }
        update_noise(2);
    }

    private void mix_output() {
        short _out = 0;
        for (int i = 0; i < 14; i++) {
            _out += this.ch_out[i];
        }
        if (this.conv != null) {
            OPLL_RateConv_putData(this.conv, 0, _out);
        } else {
            this.mix_out[0] = _out;
        }
    }

    private void mix_output_stereo() {
        short[] _out = this.mix_out;
        _out[0] = _out[1] = 0;
        for (int i = 0; i < 14; i++) {
            if ((this.pan[i] & 2) != 0)
                _out[0] += (short) (this.ch_out[i] * this.pan_fine[i][0]);
            if ((this.pan[i] & 1) != 0)
                _out[1] += (short) (this.ch_out[i] * this.pan_fine[i][1]);
        }
        if (this.conv != null) {
            OPLL_RateConv_putData(this.conv, 0, _out[0]);
            OPLL_RateConv_putData(this.conv, 1, _out[1]);
        }
    }

    //
    // External Interfaces
    //

    public void OPLL_init(int clk, int rate) {
        if (table_initialized == 0) {
            initializeTables();
        }

        for (int i = 0; i < 19; i++) {
            this.patch[i] = new OPLL_PATCH[2];// null_patch;
            for (int j = 0; j < 2; j++) {
                this.patch[i][j] = new OPLL_PATCH();
            }
            //memcpy(&this.patch[i], &null_patch, sizeof(OPLL_PATCH));
        }

        this.clk = clk;
        this.rate = rate;
        this.mask = 0;
        this.conv = null;
        this.mix_out[0] = 0;
        this.mix_out[1] = 0;

        OPLL_reset();
        OPLL_setChipType(0);
        OPLL_resetPatch(0);
    }

    public void OPLL_delete() {
        if (this.conv != null) {
            OPLL_RateConv_delete(this.conv);
            this.conv = null;
        }
    }

    public void reset_rate_conversion_params() {
        double f_out = this.rate;
        double f_inp = this.clk / 72.0;

        this.out_time = 0;
        this.out_step = f_inp;
        this.inp_step = f_out;

        if (this.conv != null) {
            OPLL_RateConv_delete(this.conv);
            this.conv = null;
        }

        if (Math.floor(f_inp) != f_out && Math.floor(f_inp + 0.5) != f_out) {
            this.conv = OPLL_RateConv_new(f_inp, f_out, 2);
        }

        if (this.conv != null) {
            OPLL_RateConv_reset(this.conv);
        }
    }

    public void OPLL_reset() {
        this.adr = 0;

        this.pm_phase = 0;
        this.am_phase = 0;

        this.noise = 0x1;
        this.mask = 0;

        this.rhythm_mode = 0;
        this.slot_key_status = 0;
        this.eg_counter = 0;

        reset_rate_conversion_params();

        for (int i = 0; i < 18; i++) {
            this.slot[i] = new OPLL_SLOT();
            reset_slot(this.slot[i], i);
        }

        for (int i = 0; i < 9; i++) {
            set_patch(i, 0);
        }

        for (int i = 0; i < 0x40; i++)
            OPLL_writeReg(i, 0);

        for (int i = 0; i < 15; i++) {
            this.pan[i] = 3;
            this.pan_fine[i][1] = this.pan_fine[i][0] = 1.0f;
        }

        for (int i = 0; i < 14; i++) {
            this.ch_out[i] = 0;
        }
    }

    public void OPLL_forceRefresh() {
        for (int i = 0; i < 9; i++) {
            set_patch(i, this.patch_number[i]);
        }

        for (int i = 0; i < 18; i++) {
            request_update(this.slot[i], SLOT_UPDATE_FLAG.UPDATE_ALL.v);
        }
    }

    public void OPLL_setRate(int rate) {
        this.rate = rate;
        reset_rate_conversion_params();
    }

    public void OPLL_setQuality(int q) {
    }

    public void OPLL_setChipType(int type) {
        this.chip_type = type;
    }

    public void OPLL_writeReg(int reg, int data) {
        int ch;

        if (reg >= 0x40) {
            extendFunction(reg, data);
            return;
        }

        // mirror registers
        if ((0x19 <= reg && reg <= 0x1f) || (0x29 <= reg && reg <= 0x2f) || (0x39 <= reg && reg <= 0x3f)) {
            reg -= 9;
        }

        this.reg[reg] = data;

        switch (reg) {
            case 0x00:
                this.patch[0][0].AM = (data >> 7) & 1;
                this.patch[0][0].PM = (data >> 6) & 1;
                this.patch[0][0].EG = (data >> 5) & 1;
                this.patch[0][0].KR = (data >> 4) & 1;
                this.patch[0][0].ML = (data) & 15;
                for (int i = 0; i < 9; i++) {
                    if (this.patch_number[i] == 0) {
                        request_update(MOD(i), SLOT_UPDATE_FLAG.UPDATE_RKS.v | SLOT_UPDATE_FLAG.UPDATE_EG.v);
                    }
                }
                break;

            case 0x01:
                this.patch[0][1].AM = (data >> 7) & 1;
                this.patch[0][1].PM = (data >> 6) & 1;
                this.patch[0][1].EG = (data >> 5) & 1;
                this.patch[0][1].KR = (data >> 4) & 1;
                this.patch[0][1].ML = (data) & 15;
                for (int i = 0; i < 9; i++) {
                    if (this.patch_number[i] == 0) {
                        request_update(CAR(i), SLOT_UPDATE_FLAG.UPDATE_RKS.v | SLOT_UPDATE_FLAG.UPDATE_EG.v);
                    }
                }
                break;

            case 0x02:
                this.patch[0][0].KL = (data >> 6) & 3;
                this.patch[0][0].TL = (data) & 63;
                for (int i = 0; i < 9; i++) {
                    if (this.patch_number[i] == 0) {
                        request_update(MOD(i), SLOT_UPDATE_FLAG.UPDATE_TLL.v);
                    }
                }
                break;

            case 0x03:
                this.patch[0][1].KL = (data >> 6) & 3;
                this.patch[0][1].WS = (data >> 4) & 1;
                this.patch[0][0].WS = (data >> 3) & 1;
                this.patch[0][0].FB = (data) & 7;
                for (int i = 0; i < 9; i++) {
                    if (this.patch_number[i] == 0) {
                        request_update(MOD(i), SLOT_UPDATE_FLAG.UPDATE_WS.v);
                        request_update(CAR(i), SLOT_UPDATE_FLAG.UPDATE_WS.v | SLOT_UPDATE_FLAG.UPDATE_TLL.v);
                    }
                }
                break;

            case 0x04:
                this.patch[0][0].AR = (data >> 4) & 15;
                this.patch[0][0].DR = (data) & 15;
                for (int i = 0; i < 9; i++) {
                    if (this.patch_number[i] == 0) {
                        request_update(MOD(i), SLOT_UPDATE_FLAG.UPDATE_EG.v);
                    }
                }
                break;

            case 0x05:
                this.patch[0][1].AR = (data >> 4) & 15;
                this.patch[0][1].DR = (data) & 15;
                for (int i = 0; i < 9; i++) {
                    if (this.patch_number[i] == 0) {
                        request_update(CAR(i), SLOT_UPDATE_FLAG.UPDATE_EG.v);
                    }
                }
                break;

            case 0x06:
                this.patch[0][0].SL = (data >> 4) & 15;
                this.patch[0][0].RR = (data) & 15;
                for (int i = 0; i < 9; i++) {
                    if (this.patch_number[i] == 0) {
                        request_update(MOD(i), SLOT_UPDATE_FLAG.UPDATE_EG.v);
                    }
                }
                break;

            case 0x07:
                this.patch[0][1].SL = (data >> 4) & 15;
                this.patch[0][1].RR = (data) & 15;
                for (int i = 0; i < 9; i++) {
                    if (this.patch_number[i] == 0) {
                        request_update(CAR(i), SLOT_UPDATE_FLAG.UPDATE_EG.v);
                    }
                }
                break;

            case 0x0e:
                if (this.chip_type == 1)
                    break;
                update_rhythm_mode();
                update_key_status();
                break;

            case 0x0f:
                this.test_flag = data;
                break;

            case 0x10:
            case 0x11:
            case 0x12:
            case 0x13:
            case 0x14:
            case 0x15:
            case 0x16:
            case 0x17:
            case 0x18:
                ch = reg - 0x10;
                set_fnumber(ch, data + ((this.reg[0x20 + ch] & 1) << 8));
                break;

            case 0x20:
            case 0x21:
            case 0x22:
            case 0x23:
            case 0x24:
            case 0x25:
            case 0x26:
            case 0x27:
            case 0x28:
                ch = reg - 0x20;
                set_fnumber(ch, ((data & 1) << 8) + this.reg[0x10 + ch]);
                set_block(ch, (data >> 1) & 7);
                set_sus_flag(ch, (data >> 5) & 1);
                update_key_status();
                break;

            case 0x30:
            case 0x31:
            case 0x32:
            case 0x33:
            case 0x34:
            case 0x35:
            case 0x36:
            case 0x37:
            case 0x38:
                if ((this.reg[0x0e] & 32) != 0 && (reg >= 0x36)) {
                    switch (reg) {
                        case 0x37:
                            set_slot_volume(MOD(7), ((data >> 4) & 15) << 2);
                            break;
                        case 0x38:
                            set_slot_volume(MOD(8), ((data >> 4) & 15) << 2);
                            break;
                        default:
                            break;
                    }
                } else {
                    set_patch(reg - 0x30, (data >> 4) & 15);
                }
                set_volume(reg - 0x30, (data & 15) << 2);
                break;

            default:
                break;
        }
    }

    public void extendFunction(int reg, int data) {
        switch (reg) {
            case 0x40: // Pan Channel specification
                this.panCh = Math.min(Math.max(data, 0), 13);
                break;
            case 0x41: // Pan value specification
                this.pan[this.panCh] = ((data & 0xf0) != 0 ? 0x02 : 0x00)
                        | ((data & 0x0f) != 0 ? 0x01 : 0x00);
                this.pan[this.panCh] = (this.pan[this.panCh] == 0)
                        ? 3
                        : this.pan[this.panCh];

                this.pan_fine[this.panCh][0] = 1.0f * (data >> 4) / 15.0f;
                this.pan_fine[this.panCh][1] = 1.0f * (data & 0xf) / 15.0f;
                break;
        }
    }

    public void OPLL_writeIO(int adr, int val) {
        if ((adr & 1) != 0)
            OPLL_writeReg(this.adr, val);
        else
            this.adr = val;
    }

    public void OPLL_setPan(int ch, int pan) {
        this.pan[ch & 15] = pan;
    }

    public void OPLL_setPanFine(int ch, float[] pan) {
        this.pan_fine[ch & 15][0] = pan[0];
        this.pan_fine[ch & 15][1] = pan[1];
    }

    public void OPLL_dumpToPatch(short[] dump, int startAdr, OPLL_PATCH[][] patch) {
        if (patch[startAdr][0] == null) patch[startAdr][0] = new OPLL_PATCH();
        if (patch[startAdr][1] == null) patch[startAdr][1] = new OPLL_PATCH();

        patch[startAdr][0].AM = (dump[0 + startAdr * 8] >> 7) & 1;
        patch[startAdr][1].AM = (dump[1 + startAdr * 8] >> 7) & 1;
        patch[startAdr][0].PM = (dump[0 + startAdr * 8] >> 6) & 1;
        patch[startAdr][1].PM = (dump[1 + startAdr * 8] >> 6) & 1;
        patch[startAdr][0].EG = (dump[0 + startAdr * 8] >> 5) & 1;
        patch[startAdr][1].EG = (dump[1 + startAdr * 8] >> 5) & 1;
        patch[startAdr][0].KR = (dump[0 + startAdr * 8] >> 4) & 1;
        patch[startAdr][1].KR = (dump[1 + startAdr * 8] >> 4) & 1;
        patch[startAdr][0].ML = (dump[0 + startAdr * 8]) & 15;
        patch[startAdr][1].ML = (dump[1 + startAdr * 8]) & 15;
        patch[startAdr][0].KL = (dump[2 + startAdr * 8] >> 6) & 3;
        patch[startAdr][1].KL = (dump[3 + startAdr * 8] >> 6) & 3;
        patch[startAdr][0].TL = (dump[2 + startAdr * 8]) & 63;
        patch[startAdr][1].TL = 0;
        patch[startAdr][0].FB = (dump[3 + startAdr * 8]) & 7;
        patch[startAdr][1].FB = 0;
        patch[startAdr][0].WS = (dump[3 + startAdr * 8] >> 3) & 1;
        patch[startAdr][1].WS = (dump[3 + startAdr * 8] >> 4) & 1;
        patch[startAdr][0].AR = (dump[4 + startAdr * 8] >> 4) & 15;
        patch[startAdr][1].AR = (dump[5 + startAdr * 8] >> 4) & 15;
        patch[startAdr][0].DR = (dump[4 + startAdr * 8]) & 15;
        patch[startAdr][1].DR = (dump[5 + startAdr * 8]) & 15;
        patch[startAdr][0].SL = (dump[6 + startAdr * 8] >> 4) & 15;
        patch[startAdr][1].SL = (dump[7 + startAdr * 8] >> 4) & 15;
        patch[startAdr][0].RR = (dump[6 + startAdr * 8]) & 15;
        patch[startAdr][1].RR = (dump[7 + startAdr * 8]) & 15;
    }

    public void OPLL_getDefaultPatch(int type, int num, OPLL_PATCH[][][] patch) {
        OPLL_dumpToPatch(default_inst[type], num, patch[type]);
    }

    public void OPLL_setPatch(short[] dump) {
        OPLL_PATCH[][] patch = new OPLL_PATCH[2][];
        for (int i = 0; i < 19; i++) {
            OPLL_dumpToPatch(dump, i, patch);
            this.patch[i][0] = patch[i][0];
            this.patch[i][1] = patch[i][1];
        }
    }

    public void OPLL_patchToDump(OPLL_PATCH[] patch, int[] dump) {
        dump[0] = (patch[0].AM << 7) + (patch[0].PM << 6) + (patch[0].EG << 5) + (patch[0].KR << 4) + patch[0].ML;
        dump[1] = (patch[1].AM << 7) + (patch[1].PM << 6) + (patch[1].EG << 5) + (patch[1].KR << 4) + patch[1].ML;
        dump[2] = (patch[0].KL << 6) + patch[0].TL;
        dump[3] = (patch[1].KL << 6) + (patch[1].WS << 4) + (patch[0].WS << 3) + patch[0].FB;
        dump[4] = (patch[0].AR << 4) + patch[0].DR;
        dump[5] = (patch[1].AR << 4) + patch[1].DR;
        dump[6] = (patch[0].SL << 4) + patch[0].RR;
        dump[7] = (patch[1].SL << 4) + patch[1].RR;
    }

    public void OPLL_copyPatch(int num, OPLL_PATCH[] patch) {
        this.patch[num][0].AM = patch[0].AM;
        this.patch[num][0].AR = patch[0].AR;
        this.patch[num][0].DR = patch[0].DR;
        this.patch[num][0].EG = patch[0].EG;
        this.patch[num][0].FB = patch[0].FB;
        this.patch[num][0].KL = patch[0].KL;
        this.patch[num][0].KR = patch[0].KR;
        this.patch[num][0].ML = patch[0].ML;
        this.patch[num][0].PM = patch[0].PM;
        this.patch[num][0].RR = patch[0].RR;
        this.patch[num][0].SL = patch[0].SL;
        this.patch[num][0].TL = patch[0].TL;
        this.patch[num][0].WS = patch[0].WS;

        this.patch[num][1].AM = patch[1].AM;
        this.patch[num][1].AR = patch[1].AR;
        this.patch[num][1].DR = patch[1].DR;
        this.patch[num][1].EG = patch[1].EG;
        this.patch[num][1].FB = patch[1].FB;
        this.patch[num][1].KL = patch[1].KL;
        this.patch[num][1].KR = patch[1].KR;
        this.patch[num][1].ML = patch[1].ML;
        this.patch[num][1].PM = patch[1].PM;
        this.patch[num][1].RR = patch[1].RR;
        this.patch[num][1].SL = patch[1].SL;
        this.patch[num][1].TL = patch[1].TL;
        this.patch[num][1].WS = patch[1].WS;
    }

    public void OPLL_resetPatch(int type) {
        for (int i = 0; i < 19; i++)
            OPLL_copyPatch(i, default_patch[type][i]);
    }

    public short OPLL_calc() {
        while (this.out_step > this.out_time) {
            this.out_time += this.inp_step;
            update_output();
            mix_output();
        }
        this.out_time -= this.out_step;
        if (this.conv != null) {
            this.mix_out[0] = OPLL_RateConv_getData(this.conv, 0);
        }
        return this.mix_out[0];
    }

    public void OPLL_calcStereo(int[] _out) {
        while (this.out_step > this.out_time) {
            this.out_time += this.inp_step;
            update_output();
            mix_output_stereo();
        }
        this.out_time -= this.out_step;
        if (this.conv != null) {
            _out[0] = OPLL_RateConv_getData(this.conv, 0);
            _out[1] = OPLL_RateConv_getData(this.conv, 1);
        } else {
            _out[0] = this.mix_out[0];
            _out[1] = this.mix_out[1];
        }
    }

    public int OPLL_setMask(int mask) {
        int ret = this.mask;
        this.mask = mask;
        return ret;
    }

    public int OPLL_toggleMask(int mask) {
        int ret = this.mask;
        this.mask ^= mask;
        return ret;
    }
}
