/*
 * Yamaha YM2151 driver (version 2.150 final beta)
 */

package mdsound.chips;


/**
 * File: Ym2151.h - header file for software implementation of YM2151
 *                                            FM Operator Type-MPcm(OPM)
 *
 * (c) 1997-2002 Jarek Burczynski (s0246@poczta.onet.pl, bujar@mame.net)
 * Some of the optimizing ideas by Tatsuyuki Satoh
 *
 * Version 2.150 final beta May, 11th 2002
 *
 *
 * I would like to thank following people for making this project possible:
 *
 * Beauty Planets - for making a lot of real YM2151 samples and providing
 * additional informations about the this. Also for the time spent making
 * the samples and the speed of replying to my endless requests.
 *
 * Shigeharu Isoda - for general help, for taking time to scan his YM2151
 * Japanese Manual first of all, and answering MANY of my questions.
 *
 * Nao - for giving me some info about YM2151 and pointing me to Shigeharu.
 * Also for creating fmemu (which I still use to test the emulator).
 *
 * Aaron Giles and Chris Hardy - they made some samples of one of my favourite
 * arcade games so I could compare it to my emulator.
 *
 * Bryan McPhail and Tim (powerjaw) - for making some samples.
 *
 * Ishmair - for the datasheet and motivation.
 */
public class Ym2151 {

    /** struct describing a single Operator */
    private static class Operator {

        /** accumulated Operator phase */
        private int phase;
        /** Operator frequency count */
        private int freq;
        /** current DT1 (detune 1 phase inc/decrement) value */
        private int dt1;
        /** frequency count multiply */
        private int mul;
        /** DT1 index * 32 */
        private int dt1_i;
        /** current DT2 (detune 2) value */
        private int dt2;

        /** Operator output 'direction' */
        private RInt connect;

        // only M1 (Operator 0) is filled with this data

        /** where to put the delayed sample (MEM) */
        private RInt mem_connect;
        /** delayed sample (MEM) value */
        private int mem_value;

        // channel specific data; note: each Operator number 0 contains channel specific data

        /** feedback shift value for operators 0 in each channel */
        private int fb_shift;
        /** Operator feedback value (used only by operators 0) */
        private int fbOutCurr;
        /** previous feedback value (used only by operators 0) */
        private int fbOutPrev;
        /** channel KC (copied to all operators) */
        private int kc;
        /** just for speedup */
        private int kc_i;
        /** channel PMS */
        private int pms;
        /** channel AMS */
        private int ams;

        // end of channel specific data

        /** LFO Amplitude Modulation enable mask */
        private int aMmask;
        /** Envelope state: 4-attack(AR) 3-decay(D1R) 2-sustain(D2R) 1-release(RR) 0-off */
        private int state;
        /** (attack state) */
        private byte eg_sh_ar;
        /** (attack state) */
        private byte eg_sel_ar;
        private int tl;
        /** Total attenuation Level */
        private int volume;
        /** current envelope attenuation level */
        private byte eg_sh_d1r;
        /** (decay state) */
        private byte eg_sel_d1r;
        /** (decay state) */
        private int d1l;
        /** envelope switches to sustain state after reaching this level */
        private byte eg_sh_d2r;
        /** (sustain state) */
        private byte eg_sel_d2r;
        /** (sustain state) */
        private byte eg_sh_rr;
        /** (release state) */
        private byte eg_sel_rr;
        /** (release state) */

        private int key;
        /** 0=last key was KEY OFF, 1=last key was KEY ON */

        private int ks;
        /** key scale */
        private int ar;
        /** attack rate */
        private int d1r;
        /** decay rate */
        private int d2r;
        /** sustain rate */
        private int rr;
        /** release rate */

        private int reserved0; //*/
        private int reserved1; //*/

        private void KEY_ON(int key_set, int eg_cnt) {
            if (this.key == 0) {
                this.phase = 0; // clear phase */
                this.state = EG_ATT; // KEY ON = attack */
                this.volume += (~this.volume *
                        (eg_inc[this.eg_sel_ar + ((eg_cnt >> this.eg_sh_ar) & 7)])
                ) >> 4;
                if (this.volume <= MIN_ATT_INDEX) {
                    this.volume = MIN_ATT_INDEX;
                    this.state = EG_DEC;
                }
            }
            this.key |= key_set;
        }

        private void KEY_OFF(int key_clr) {
            if (this.key != 0) {
                this.key &= key_clr;
                if (this.key == 0) {
                    if (this.state > EG_REL)
                        this.state = EG_REL; /* KEY OFF = release */
                }
            }
        }

        private int opCalc(int env, int pm) {
            int p = (env << 3) + sin_tab[(((this.phase & ~FREQ_MASK) + (pm << 15)) >> FREQ_SH) & SIN_MASK];

            if (p >= TL_TAB_LEN)
                return 0;

            return tl_tab[p];
        }

        private int op_calc1(int env, int pm) {
            int i = (this.phase & ~FREQ_MASK) + pm;

            /*Debug.printf("i=%08x (i>>16)&511=%8i phase=%i [pm=%08x] ",i, (i>>16)&511, this.phase>>FREQ_SH, pm);*/

            int p = (env << 3) + sin_tab[(i >> FREQ_SH) & SIN_MASK];

            /*Debug.printf("(p&255=%i p>>8=%i) out= %i\n", p&255,p>>8, tl_tab[p&255]>>(p>>8) );*/

            if (p >= TL_TAB_LEN)
                return 0;

            //Debug.printf("p:%d tl_tab[p]:%d", p, tl_tab[p]);
            return tl_tab[p];
        }


        private int volumeCalc(int am) {
            return this.tl + this.volume + (am & this.aMmask);
        }
    }

    /** the 32 operators */
    private Operator[] oper = new Operator[32];

    /** channels output masks (0xffffffff = enable) */
    private int[] pan = new int[16];
    /** used for muting */
    private byte[] muted = new byte[8];

    /** Global envelope generator counter */
    private int eg_cnt;
    /** Global envelope generator counter works at frequency = chipclock/64/3 */
    private int eg_timer;
    /** step of eg_timer */
    private int eg_timer_add;
    /** envelope generator timer overlfows every 3 samples (on real chips) */
    private int eg_timer_overflow;

    /** accumulated LFO phase (0 to 255) */
    private int lfo_phase;
    /** LFO timer */
    private int lfo_timer;
    /** step of lfo_timer */
    private int lfo_timer_add;
    /** LFO generates new output when lfo_timer reaches this value */
    private int lfo_overflow;
    /** LFO phase increment counter */
    private int lfo_counter;
    /** step of lfo_counter */
    private int lfo_counter_add;
    /** LFO waveform (0-saw, 1-square, 2-triangle, 3-random noise) */
    private byte lfo_wsel;
    /** LFO Amplitude Modulation Depth */
    private byte amd;
    /** LFO Phase Modulation Depth */
    private byte pmd;
    /** LFO current AM output */
    private int lfa;
    /** LFO current PM output */
    private int lfp;

    /** TEST register */
    private byte test;
    /** output control pins (bit1-CT2, bit0-CT1) */
    private byte ct;

    /** noise enable/period register (bit 7 - noise enable, bits 4-0 - noise period */
    private int noise;
    /** 17 bit noise shift register */
    private int noise_rng;
    /** current noise 'phase' */
    private int noise_p;
    /** current noise period */
    private int noise_f;

    /** CSM  KEY ON / KEY OFF sequence request */
    private int csm_req;

    /**
     * IRQ enable for timer B (bit 3) and timer A (bit 2); bit 7 -
     * CSM mode (keyon to all slots, everytime timer A overflows)
     */
    private int irq_enable;
    /** chips status (BUSY, IRQ Flags) */
    private int status;
    /** channels connections */
    private byte[] connect = new byte[8];

    /** timer A enable (0-disabled) */
    private byte tim_A;
    /** timer B enable (0-disabled) */
    private byte tim_B;
    /** current value of timer A */
    private int tim_A_val;
    /** current value of timer B */
    private int tim_B_val;
    private int[] tim_A_tab = new int[1024];
    /** timer A deltas */
    private int[] tim_B_tab = new int[256];
    /** timer B deltas */
    private int timer_A_index;
    /** timer A index */
    private int timer_B_index;
    /** timer B index */
    private int timer_A_index_old;
    /** timer A previous index */
    private int timer_B_index_old; /** timer B previous index */

    // Frequency-deltas to get the closest frequency possible.
    //   There are 11 octaves because of DT2 (max 950 cents over base frequency)
    //   and LFO phase modulation (max 800 cents below AND over base frequency)
    //   Summary:   octave  explanation
    //              0       note code - LFO PM
    //              1       note code
    //              2       note code
    //              3       note code
    //              4       note code
    //              5       note code
    //              6       note code
    //              7       note code
    //              8       note code
    //              9       note code + DT2 + LFO PM
    //              10      note code + DT2 + LFO PM

    /** 11 octaves, 768 'cents' per octave */
    private int[] freq = new int[11 * 768];

    // Frequency deltas for DT1. These deltas alter Operator frequency
    // after it has been taken from frequency-deltas table.

    /** 8 DT1 levels, 32 KC values */
    private int[] dt1_freq = new int[8 * 32];

    /** 17bit Noise Generator periods */
    private int[] noise_tab = new int[32];

    /** chips clock in Hz (passed from 2151intf.c) */
    private int clock;
    /** sampling frequency in Hz (passed from 2151intf.c) */
    private int sampfreq;

    /** 16.16 fixed point (frequency calculations) */
    private static final int FREQ_SH = 16;
    /** 16.16 fixed point (envelope generator timing) */
    private static final int EG_SH = 16;
    /** 22.10 fixed point (LFO calculations) */
    private static final int LFO_SH = 10;
    /** 16.16 fixed point (timers calculations) */
    private static final int TIMER_SH = 16;

    private static final int FREQ_MASK = ((1 << FREQ_SH) - 1);

    private static final int ENV_BITS = 10;
    private static final int ENV_LEN = 1 << ENV_BITS;
    private static final double ENV_STEP = 128.0 / ENV_LEN;

    private static final int MAX_ATT_INDEX = ENV_LEN - 1; /* 1023 */
    private static final int MIN_ATT_INDEX = 0;

    private static final int EG_ATT = 4;
    private static final int EG_DEC = 3;
    private static final int EG_SUS = 2;
    private static final int EG_REL = 1;
    private static final int EG_OFF = 0;

    private static final int SIN_BITS = 10;
    private static final int SIN_LEN = 1 << SIN_BITS;
    private static final int SIN_MASK = SIN_LEN - 1;

    /** 8 bits addressing (real chips) */
    private static final int TL_RES_LEN = 256;

    private static final int FINAL_SH = 0;
    private static final int MAXOUT = 32767;
    private static final int MINOUT = -32768;

    // TL_TAB_LEN is calculated as:
    // 13 - sinus amplitude bits     (Y axis)
    // 2  - sinus sign bit           (Y axis)
    // TL_RES_LEN - sinus resolution (X axis)
    private static final int TL_TAB_LEN = (13 * 2 * TL_RES_LEN);
    private static int[] tl_tab = new int[TL_TAB_LEN];

    private static final int ENV_QUIET = (TL_TAB_LEN >> 3);

    /* sin waveform table in 'decibel' scale */
    private static int[] sin_tab = new int[SIN_LEN];

    /* translate from D1L to volume index (16 D1L levels) */
    private static int[] d1l_tab = new int[16];

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
            /*17 */ 16, 16, 16, 16, 16, 16, 16, 16, /* rates 15 2, 15 3 for attack
            /*18 */ 0, 0, 0, 0, 0, 0, 0, 0, // infinity rates for attack and decay(s)
    };

    // note that there is no O(17) in this table - it's directly in the code

    /** Envelope Generator rates (32 + 64 rates + 32 RKS) */
    private static final byte[] eg_rate_select = new byte[] {
            // 32 dummy (infinite time) rates
            (byte) ((byte) (18 * RATE_STEPS)), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS),
            (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS),
            (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS),
            (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS), (byte) (18 * RATE_STEPS),

            // rates 00-11
            (byte) (0 * RATE_STEPS), (byte) (1 * RATE_STEPS), (byte) (2 * RATE_STEPS), (byte) (3 * RATE_STEPS),
            (byte) (0 * RATE_STEPS), (byte) (1 * RATE_STEPS), (byte) (2 * RATE_STEPS), (byte) (3 * RATE_STEPS),
            (byte) (0 * RATE_STEPS), (byte) (1 * RATE_STEPS), (byte) (2 * RATE_STEPS), (byte) (3 * RATE_STEPS),
            (byte) (0 * RATE_STEPS), (byte) (1 * RATE_STEPS), (byte) (2 * RATE_STEPS), (byte) (3 * RATE_STEPS),
            (byte) (0 * RATE_STEPS), (byte) (1 * RATE_STEPS), (byte) (2 * RATE_STEPS), (byte) (3 * RATE_STEPS),
            (byte) (0 * RATE_STEPS), (byte) (1 * RATE_STEPS), (byte) (2 * RATE_STEPS), (byte) (3 * RATE_STEPS),
            (byte) (0 * RATE_STEPS), (byte) (1 * RATE_STEPS), (byte) (2 * RATE_STEPS), (byte) (3 * RATE_STEPS),
            (byte) (0 * RATE_STEPS), (byte) (1 * RATE_STEPS), (byte) (2 * RATE_STEPS), (byte) (3 * RATE_STEPS),
            (byte) (0 * RATE_STEPS), (byte) (1 * RATE_STEPS), (byte) (2 * RATE_STEPS), (byte) (3 * RATE_STEPS),
            (byte) (0 * RATE_STEPS), (byte) (1 * RATE_STEPS), (byte) (2 * RATE_STEPS), (byte) (3 * RATE_STEPS),
            (byte) (0 * RATE_STEPS), (byte) (1 * RATE_STEPS), (byte) (2 * RATE_STEPS), (byte) (3 * RATE_STEPS),
            (byte) (0 * RATE_STEPS), (byte) (1 * RATE_STEPS), (byte) (2 * RATE_STEPS), (byte) (3 * RATE_STEPS),

            // rate 12
            (byte) (4 * RATE_STEPS), (byte) (5 * RATE_STEPS), (byte) (6 * RATE_STEPS), (byte) (7 * RATE_STEPS),

            // rate 13
            (byte) (8 * RATE_STEPS), (byte) (9 * RATE_STEPS), (byte) (10 * RATE_STEPS), (byte) (11 * RATE_STEPS),

            // rate 14
            (byte) (12 * RATE_STEPS), (byte) (13 * RATE_STEPS), (byte) (14 * RATE_STEPS), (byte) (15 * RATE_STEPS),

            // rate 15
            (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS),

            // 32 dummy rates (same as 15 3)
            (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS),
            (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS),
            (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS),
            (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS),
            (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS),
            (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS),
            (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS),
            (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS), (byte) (16 * RATE_STEPS)
    };

    //rate  0,    1,    2,   3,   4,   5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15
    //shift 11,   10,   9,   8,   7,   6,  5,  4,  3,  2, 1,  0,  0,  0,  0,  0
    //mask  2047, 1023, 511, 255, 127, 63, 31, 15, 7,  3, 1,  0,  0,  0,  0,  0

    /** Envelope Generator counter shifts (32 + 64 rates + 32 RKS) */
    private static final byte[] eg_rate_shift = new byte[] {
            // 32 infinite time rates
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,


            // rates 00-11
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
            1, 1, 1, 1,
            0, 0, 0, 0,

            // rate 12
            0, 0, 0, 0,

            // rate 13
            0, 0, 0, 0,

            // rate 14
            0, 0, 0, 0,

            // rate 15
            0, 0, 0, 0,

            // 32 dummy rates (same as 15 3)
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0
    };

    /**
     * DT2 defines offset in cents from base note
     * <p>
     * This table defines offset in frequency-deltas table.
     * User's Manual page 22
     * <p>
     * Values below were calculated using formula: value =  orig.val / 1.5625
     * <p>
     * DT2=0 DT2=1 DT2=2 DT2=3
     * 0     600   781   950
     */
    private static final int[] dt2_tab = new int[] {0, 384, 500, 608};

    /**
     * DT1 defines offset in Hertz from base note
     * This table is converted while initialization...
     * Detune table shown in YM2151 User's Manual is wrong (verified on the real chips)
     * <p>
     * 4*32 DT1 values
     */
    private static final byte[] dt1_tab = new byte[] {
            /* DT1=0 */
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            /* DT1=1 */
            0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2,
            2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6, 7, 8, 8, 8, 8,

            /* DT1=2 */
            1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5,
            5, 6, 6, 7, 8, 8, 9, 10, 11, 12, 13, 14, 16, 16, 16, 16,

            /* DT1=3 */
            2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6, 7,
            8, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 20, 22, 22, 22, 22
    };

    private static final int[] phaseinc_rom = new int[] {
            1299, 1300, 1301, 1302, 1303, 1304, 1305, 1306, 1308, 1309, 1310, 1311, 1313, 1314, 1315, 1316,
            1318, 1319, 1320, 1321, 1322, 1323, 1324, 1325, 1327, 1328, 1329, 1330, 1332, 1333, 1334, 1335,
            1337, 1338, 1339, 1340, 1341, 1342, 1343, 1344, 1346, 1347, 1348, 1349, 1351, 1352, 1353, 1354,
            1356, 1357, 1358, 1359, 1361, 1362, 1363, 1364, 1366, 1367, 1368, 1369, 1371, 1372, 1373, 1374,
            1376, 1377, 1378, 1379, 1381, 1382, 1383, 1384, 1386, 1387, 1388, 1389, 1391, 1392, 1393, 1394,
            1396, 1397, 1398, 1399, 1401, 1402, 1403, 1404, 1406, 1407, 1408, 1409, 1411, 1412, 1413, 1414,
            1416, 1417, 1418, 1419, 1421, 1422, 1423, 1424, 1426, 1427, 1429, 1430, 1431, 1432, 1434, 1435,
            1437, 1438, 1439, 1440, 1442, 1443, 1444, 1445, 1447, 1448, 1449, 1450, 1452, 1453, 1454, 1455,
            1458, 1459, 1460, 1461, 1463, 1464, 1465, 1466, 1468, 1469, 1471, 1472, 1473, 1474, 1476, 1477,
            1479, 1480, 1481, 1482, 1484, 1485, 1486, 1487, 1489, 1490, 1492, 1493, 1494, 1495, 1497, 1498,
            1501, 1502, 1503, 1504, 1506, 1507, 1509, 1510, 1512, 1513, 1514, 1515, 1517, 1518, 1520, 1521,
            1523, 1524, 1525, 1526, 1528, 1529, 1531, 1532, 1534, 1535, 1536, 1537, 1539, 1540, 1542, 1543,
            1545, 1546, 1547, 1548, 1550, 1551, 1553, 1554, 1556, 1557, 1558, 1559, 1561, 1562, 1564, 1565,
            1567, 1568, 1569, 1570, 1572, 1573, 1575, 1576, 1578, 1579, 1580, 1581, 1583, 1584, 1586, 1587,
            1590, 1591, 1592, 1593, 1595, 1596, 1598, 1599, 1601, 1602, 1604, 1605, 1607, 1608, 1609, 1610,
            1613, 1614, 1615, 1616, 1618, 1619, 1621, 1622, 1624, 1625, 1627, 1628, 1630, 1631, 1632, 1633,
            1637, 1638, 1639, 1640, 1642, 1643, 1645, 1646, 1648, 1649, 1651, 1652, 1654, 1655, 1656, 1657,
            1660, 1661, 1663, 1664, 1666, 1667, 1669, 1670, 1672, 1673, 1675, 1676, 1678, 1679, 1681, 1682,
            1685, 1686, 1688, 1689, 1691, 1692, 1694, 1695, 1697, 1698, 1700, 1701, 1703, 1704, 1706, 1707,
            1709, 1710, 1712, 1713, 1715, 1716, 1718, 1719, 1721, 1722, 1724, 1725, 1727, 1728, 1730, 1731,
            1734, 1735, 1737, 1738, 1740, 1741, 1743, 1744, 1746, 1748, 1749, 1751, 1752, 1754, 1755, 1757,
            1759, 1760, 1762, 1763, 1765, 1766, 1768, 1769, 1771, 1773, 1774, 1776, 1777, 1779, 1780, 1782,
            1785, 1786, 1788, 1789, 1791, 1793, 1794, 1796, 1798, 1799, 1801, 1802, 1804, 1806, 1807, 1809,
            1811, 1812, 1814, 1815, 1817, 1819, 1820, 1822, 1824, 1825, 1827, 1828, 1830, 1832, 1833, 1835,
            1837, 1838, 1840, 1841, 1843, 1845, 1846, 1848, 1850, 1851, 1853, 1854, 1856, 1858, 1859, 1861,
            1864, 1865, 1867, 1868, 1870, 1872, 1873, 1875, 1877, 1879, 1880, 1882, 1884, 1885, 1887, 1888,
            1891, 1892, 1894, 1895, 1897, 1899, 1900, 1902, 1904, 1906, 1907, 1909, 1911, 1912, 1914, 1915,
            1918, 1919, 1921, 1923, 1925, 1926, 1928, 1930, 1932, 1933, 1935, 1937, 1939, 1940, 1942, 1944,
            1946, 1947, 1949, 1951, 1953, 1954, 1956, 1958, 1960, 1961, 1963, 1965, 1967, 1968, 1970, 1972,
            1975, 1976, 1978, 1980, 1982, 1983, 1985, 1987, 1989, 1990, 1992, 1994, 1996, 1997, 1999, 2001,
            2003, 2004, 2006, 2008, 2010, 2011, 2013, 2015, 2017, 2019, 2021, 2022, 2024, 2026, 2028, 2029,
            2032, 2033, 2035, 2037, 2039, 2041, 2043, 2044, 2047, 2048, 2050, 2052, 2054, 2056, 2058, 2059,
            2062, 2063, 2065, 2067, 2069, 2071, 2073, 2074, 2077, 2078, 2080, 2082, 2084, 2086, 2088, 2089,
            2092, 2093, 2095, 2097, 2099, 2101, 2103, 2104, 2107, 2108, 2110, 2112, 2114, 2116, 2118, 2119,
            2122, 2123, 2125, 2127, 2129, 2131, 2133, 2134, 2137, 2139, 2141, 2142, 2145, 2146, 2148, 2150,
            2153, 2154, 2156, 2158, 2160, 2162, 2164, 2165, 2168, 2170, 2172, 2173, 2176, 2177, 2179, 2181,
            2185, 2186, 2188, 2190, 2192, 2194, 2196, 2197, 2200, 2202, 2204, 2205, 2208, 2209, 2211, 2213,
            2216, 2218, 2220, 2222, 2223, 2226, 2227, 2230, 2232, 2234, 2236, 2238, 2239, 2242, 2243, 2246,
            2249, 2251, 2253, 2255, 2256, 2259, 2260, 2263, 2265, 2267, 2269, 2271, 2272, 2275, 2276, 2279,
            2281, 2283, 2285, 2287, 2288, 2291, 2292, 2295, 2297, 2299, 2301, 2303, 2304, 2307, 2308, 2311,
            2315, 2317, 2319, 2321, 2322, 2325, 2326, 2329, 2331, 2333, 2335, 2337, 2338, 2341, 2342, 2345,
            2348, 2350, 2352, 2354, 2355, 2358, 2359, 2362, 2364, 2366, 2368, 2370, 2371, 2374, 2375, 2378,
            2382, 2384, 2386, 2388, 2389, 2392, 2393, 2396, 2398, 2400, 2402, 2404, 2407, 2410, 2411, 2414,
            2417, 2419, 2421, 2423, 2424, 2427, 2428, 2431, 2433, 2435, 2437, 2439, 2442, 2445, 2446, 2449,
            2452, 2454, 2456, 2458, 2459, 2462, 2463, 2466, 2468, 2470, 2472, 2474, 2477, 2480, 2481, 2484,
            2488, 2490, 2492, 2494, 2495, 2498, 2499, 2502, 2504, 2506, 2508, 2510, 2513, 2516, 2517, 2520,
            2524, 2526, 2528, 2530, 2531, 2534, 2535, 2538, 2540, 2542, 2544, 2546, 2549, 2552, 2553, 2556,
            2561, 2563, 2565, 2567, 2568, 2571, 2572, 2575, 2577, 2579, 2581, 2583, 2586, 2589, 2590, 2593
    };

    /*
        Noise LFO waveform.

        Here are just 256 samples out of much longer data.

        It does NOT repeat every 256 samples on real chips and I wasnt able to find
        the point where it repeats (even in strings as long as 131072 samples).

        I only put it here because its better than nothing and perhaps
        someone might be able to figure out the real algorithm.


        Note that (due to the way the LFO output is calculated) it is quite
        possible that two values: 0x80 and 0x00 might be wrong in this table.
        To be exact:
            some 0x80 could be 0x81 as well as some 0x00 could be 0x01.
    */
    private static final byte[] lfo_noise_waveform = new byte[] {
            (byte) 0xFF, (byte) 0xEE, (byte) 0xD3, (byte) 0x80, 0x58, (byte) 0xDA, 0x7F, (byte) 0x94, (byte) 0x9E, (byte) 0xE3, (byte) 0xFA, 0x00, 0x4D, (byte) 0xFA, (byte) 0xFF, 0x6A,
            0x7A, (byte) 0xDE, 0x49, (byte) 0xF6, 0x00, 0x33, (byte) 0xBB, 0x63, (byte) 0x91, 0x60, 0x51, (byte) 0xFF, 0x00, (byte) 0xD8, 0x7F, (byte) 0xDE,
            (byte) 0xDC, 0x73, 0x21, (byte) 0x85, (byte) 0xB2, (byte) 0x9C, 0x5D, 0x24, (byte) 0xCD, (byte) 0x91, (byte) 0x9E, 0x76, 0x7F, 0x20, (byte) 0xFB, (byte) 0xF3,
            0x00, (byte) 0xA6, 0x3E, 0x42, 0x27, 0x69, (byte) 0xAE, 0x33, 0x45, 0x44, 0x11, 0x41, 0x72, 0x73, (byte) 0xDF, (byte) 0xA2,

            0x32, (byte) 0xBD, 0x7E, (byte) 0xA8, 0x13, (byte) 0xEB, (byte) 0xD3, 0x15, (byte) 0xDD, (byte) 0xFB, (byte) 0xC9, (byte) 0x9D, 0x61, 0x2F, (byte) 0xBE, (byte) 0x9D,
            0x23, 0x65, 0x51, 0x6A, (byte) 0x84, (byte) 0xF9, (byte) 0xC9, (byte) 0xD7, 0x23, (byte) 0xBF, 0x65, 0x19, (byte) 0xDC, 0x03, (byte) 0xF3, 0x24,
            0x33, (byte) 0xB6, 0x1E, 0x57, 0x5C, (byte) 0xAC, 0x25, (byte) 0x89, 0x4D, (byte) 0xC5, (byte) 0x9C, (byte) 0x99, 0x15, 0x07, (byte) 0xCF, (byte) 0xBA,
            (byte) 0xC5, (byte) 0x9B, 0x15, 0x4D, (byte) 0x8D, 0x2A, 0x1E, 0x1F, (byte) 0xEA, 0x2B, 0x2F, 0x64, (byte) 0xA9, 0x50, 0x3D, (byte) 0xAB,

            0x50, 0x77, (byte) 0xE9, (byte) 0xC0, (byte) 0xAC, 0x6D, 0x3F, (byte) 0xCA, (byte) 0xCF, 0x71, 0x7D, (byte) 0x80, (byte) 0xA6, (byte) 0xFD, (byte) 0xFF, (byte) 0xB5,
            (byte) 0xBD, 0x6F, 0x24, 0x7B, 0x00, (byte) 0x99, 0x5D, (byte) 0xB1, 0x48, (byte) 0xB0, 0x28, 0x7F, (byte) 0x80, (byte) 0xEC, (byte) 0xBF, 0x6F,
            0x6E, 0x39, (byte) 0x90, 0x42, (byte) 0xD9, 0x4E, 0x2E, 0x12, 0x66, (byte) 0xC8, (byte) 0xCF, 0x3B, 0x3F, 0x10, 0x7D, 0x79,
            0x00, (byte) 0xD3, 0x1F, 0x21, (byte) 0x93, 0x34, (byte) 0xD7, 0x19, 0x22, (byte) 0xA2, 0x08, 0x20, (byte) 0xB9, (byte) 0xB9, (byte) 0xEF, 0x51,

            (byte) 0x99, (byte) 0xDE, (byte) 0xBF, (byte) 0xD4, 0x09, 0x75, (byte) 0xE9, (byte) 0x8A, (byte) 0xEE, (byte) 0xFD, (byte) 0xE4, 0x4E, 0x30, 0x17, (byte) 0xDF, (byte) 0xCE,
            0x11, (byte) 0xB2, 0x28, 0x35, (byte) 0xC2, 0x7C, 0x64, (byte) 0xEB, (byte) 0x91, 0x5F, 0x32, 0x0C, 0x6E, 0x00, (byte) 0xF9, (byte) 0x92,
            0x19, (byte) 0xDB, (byte) 0x8F, (byte) 0xAB, (byte) 0xAE, (byte) 0xD6, 0x12, (byte) 0xC4, 0x26, 0x62, (byte) 0xCE, (byte) 0xCC, 0x0A, 0x03, (byte) 0xE7, (byte) 0xDD,
            (byte) 0xE2, 0x4D, (byte) 0x8A, (byte) 0xA6, 0x46, (byte) 0x95, 0x0F, (byte) 0x8F, (byte) 0xF5, 0x15, (byte) 0x97, 0x32, (byte) 0xD4, 0x28, 0x1E, 0x55
    };

    // these variables stay here for speedup purposes only
    private RInt[] chanout = new RInt[] {new RInt(), new RInt(), new RInt(), new RInt(), new RInt(), new RInt(), new RInt(), new RInt()};
    // Phase Modulation input for operators 2,3,4
    private RInt m2 = new RInt(), c1 = new RInt(), c2 = new RInt();
    /** one sample delay memory */
    private RInt mem = new RInt();

    /** for tru state? */
    private static class RInt {
        private int v = 0;
    }

    static {
        int i, x, n;
        double o, m;

        for (x = 0; x < TL_RES_LEN; x++) {
            m = (1 << 16) / Math.pow(2, (x + 1) * (ENV_STEP / 4.0) / 8.0);
            m = Math.floor(m);

            // we never reach (1<<16) here due to the (x+1)
            // result fits within 16 bits at maximum

            n = (int) m; // 16 bits here
            n >>= 4; // 12 bits here
            if ((n & 1) != 0) // round to closest
                n = (n >> 1) + 1;
            else
                n = n >> 1;
            // 11 bits here (rounded)
            n <<= 2; // 13 bits here (as in real chips)
            tl_tab[x * 2 + 0] = n;
            tl_tab[x * 2 + 1] = -tl_tab[x * 2 + 0];

            for (i = 1; i < 13; i++) {
                tl_tab[x * 2 + 0 + i * 2 * TL_RES_LEN] = tl_tab[x * 2 + 0] >> i;
                tl_tab[x * 2 + 1 + i * 2 * TL_RES_LEN] = -tl_tab[x * 2 + 0 + i * 2 * TL_RES_LEN];
            }
//Debug.printf("tl %04i", x*2);
//for (i=0; i<13; i++)
// Debug.printf(", [%02i] %4i", i*2, tl_tab[ x*2 /*+1*/ + i*2*TL_RES_LEN ]);
//Debug.printf("\n");
        }
        //Debug.printf("TL_TAB_LEN = %i (%i bytes)\n",TL_TAB_LEN, (int)sizeof(tl_tab));
        //Debug.printf("ENV_QUIET= %i\n",ENV_QUIET );

        for (i = 0; i < SIN_LEN; i++) {
            // non-standard sinus
            m = Math.sin(((i * 2) + 1) * Math.PI / SIN_LEN); // verified on the real chips

            // we never reach zero here due to ((i*2)+1)

            if (m > 0.0)
                o = 8 * Math.log(1.0 / m) / Math.log(2.0); // convert to 'decibels'
            else
                o = 8 * Math.log(-1.0 / m) / Math.log(2.0); // convert to 'decibels'

            o = o / (ENV_STEP / 4);

            n = (int) (2.0 * o);
            if ((n & 1) != 0) // round to closest
                n = (n >> 1) + 1;
            else
                n = n >> 1;

            sin_tab[i] = n * 2 + (m >= 0.0 ? 0 : 1);
            //Debug.printf("sin [0x%4x]= %4i (tl_tab value=%8x)\n", i, sin_tab[i],tl_tab[sin_tab[i]]);
        }

        // calculate d1l_tab table
        for (i = 0; i < 16; i++) {
            m = (i != 15 ? i : i + 16) * (4.0 / ENV_STEP); // every 3 'dB' except for all bits = 1 = 45+48 'dB'
            d1l_tab[i] = (int) m;
            //Debug.printf("d1l_tab[%02x]=%08x\n",i,d1l_tab[i] );
        }
    }

    private void init_chip_tables() {
        int i, j;
        double mult, phaseinc, Hz;
        double scaler;
        double pom;

        scaler = ((double) this.clock / 64.0) / ((double) this.sampfreq);
        //Debug.printf("scaler    = %20.15f\n", scaler);

        // this loop calculates Hertz values for notes from c-0 to b-7
        // including 64 'cents' (100/64 that is 1.5625 of real cent) per note
        // i*100/64/1200 is equal to i/768

        // real chips works with 10 bits fixed point values (10.10)
        mult = (1 << (FREQ_SH - 10)); // -10 because phaseinc_rom table values are already in 10.10 format

        for (i = 0; i < 768; i++) {
            // 3.4375 Hz is note A; C# is 4 semitones higher
            Hz = 1000;

            phaseinc = phaseinc_rom[i]; // real chips phase increment
            phaseinc *= scaler; // adjust

            // octave 2 - reference octave
            this.freq[768 + 2 * 768 + i] = ((int) (phaseinc * mult)) & 0xffffffc0; // adjust to X.10 fixed point
            // octave 0 and octave 1
            for (j = 0; j < 2; j++) {
                this.freq[768 + j * 768 + i] = (this.freq[768 + 2 * 768 + i] >> (2 - j)) & 0xffffffc0; /* adjust to X.10 fixed point */
            }
            // octave 3 to 7
            for (j = 3; j < 8; j++) {
                this.freq[768 + j * 768 + i] = this.freq[768 + 2 * 768 + i] << (j - 2);
            }
        }

        // octave -1 (all equal to: oct 0, _KC_00_, _KF_00_)
        for (i = 0; i < 768; i++) {
            this.freq[0 * 768 + i] = this.freq[1 * 768 + 0];
        }

        // octave 8 and 9 (all equal to: oct 7, _KC_14_, _KF_63_)
        for (j = 8; j < 10; j++) {
            for (i = 0; i < 768; i++) {
                this.freq[768 + j * 768 + i] = this.freq[768 + 8 * 768 - 1];
            }
        }

        mult = (1 << FREQ_SH);
        for (j = 0; j < 4; j++) {
            for (i = 0; i < 32; i++) {
                Hz = ((double) dt1_tab[j * 32 + i] * ((double) this.clock / 64.0)) / (double) (1 << 20);

                // calculate phase increment
                phaseinc = (Hz * SIN_LEN) / (double) this.sampfreq;

                // positive and negative values
                this.dt1_freq[(j + 0) * 32 + i] = (int) (phaseinc * mult);
                this.dt1_freq[(j + 4) * 32 + i] = -this.dt1_freq[(j + 0) * 32 + i];

//{
// int x = j*32 + i;
// pom = (double)this.dt1_freq[x] / mult;
// pom = pom * (double)this.sampfreq / (double)SIN_LEN;
// Debug.printf("DT1(%03i)[%02i %02i][%08x]= real %19.15f Hz  emul %19.15f Hz\n",
//  x, j, i, this.dt1_freq[x], Hz, pom);
//}
            }
        }

        // calculate timers' deltas
        // User's Manual pages 15,16
        mult = (1 << TIMER_SH);
        for (i = 0; i < 1024; i++) {
            // ASG 980324: changed to compute both tim_A_tab and timer_A_time
            pom = ((double) 64 * (1024 - i) / this.clock);
            this.tim_A_tab[i] = (int) (pom * (double) this.sampfreq * mult); // number of samples that timer period takes (fixed point) */
        }
        for (i = 0; i < 256; i++) {
            /* ASG 980324: changed to compute both tim_B_tab and timer_B_time */
            pom = ((double) 1024 * (256 - i) / this.clock);
            this.tim_B_tab[i] = (int) (pom * (double) this.sampfreq * mult); // number of samples that timer period takes (fixed point) */
        }

        // calculate noise periods table
        scaler = ((double) this.clock / 64.0) / ((double) this.sampfreq);
        for (i = 0; i < 32; i++) {
            j = (i != 31 ? i : 30); // rate 30 and 31 are the same
            j = 32 - j;
            j = (int) (65536.0 / (j * 32.0)); // number of samples per one shift of the shift register
            this.noise_tab[i] = (int) (j * 64 * scaler);
            //Debug.printf("noise_tab[%02x]=%08x\n", i, this.noise_tab[i]);
        }
    }

    private void set_connect(Operator[] om1Buf, int om1Ptr, int cha, int v) {
        Operator om1 = om1Buf[om1Ptr];
        Operator om2 = om1Buf[om1Ptr + 1];
        Operator oc1 = om1Buf[om1Ptr + 2];

        // set connect algorithm

        // MEM is simply one sample delay

        //Debug.printf("v:%d c1:%d mem:%d c2:%d m2:%d chanout[cha]:%d", v, c1.v, mem.v, c2.v, m2.v, chanout[cha]);

        switch (v & 7) {
        case 0:
            // M1---C1---MEM---M2---C2---OUT
            om1.connect = c1;
            oc1.connect = mem;
            om2.connect = c2;
            om1.mem_connect = m2;
            break;

        case 1:
            // M1------+-MEM---M2---C2---OUT
            //      C1-+
            om1.connect = mem;
            oc1.connect = mem;
            om2.connect = c2;
            om1.mem_connect = m2;
            break;

        case 2:
            // M1-----------------+-C2---OUT
            //      C1---MEM---M2-+
            om1.connect = c2;
            oc1.connect = mem;
            om2.connect = c2;
            om1.mem_connect = m2;
            break;

        case 3:
            // M1---C1---MEM------+-C2---OUT
            //                 M2-+
            om1.connect = c1;
            oc1.connect = mem;
            om2.connect = c2;
            om1.mem_connect = c2;
            break;

        case 4:
            // M1---C1-+-OUT
            // M2---C2-+
            // MEM: not used
            om1.connect = c1;
            oc1.connect = chanout[cha];
            om2.connect = c2;
            om1.mem_connect = mem; // store it anywhere where it will not be used
            break;

        case 5:
            //    +----C1----+
            // M1-+-MEM---M2-+-OUT
            //    +----C2----+
            om1.connect = null; // special mark
            oc1.connect = chanout[cha];
            om2.connect = chanout[cha];
            om1.mem_connect = m2;
            break;

        case 6:
            // M1---C1-+
            //      M2-+-OUT
            //      C2-+
            // MEM: not used
            om1.connect = c1;
            oc1.connect = chanout[cha];
            om2.connect = chanout[cha];
            om1.mem_connect = mem; // store it anywhere where it will not be used
            break;

        case 7:
            // M1-+
            // C1-+-OUT
            // M2-+
            // C2-+
            // MEM: not used*/
            om1.connect = chanout[cha];
            oc1.connect = chanout[cha];
            om2.connect = chanout[cha];
            om1.mem_connect = mem; // store it anywhere where it will not be used
            break;
        }
    }

    private void envelope_KONKOFF(Operator[] op, int opPtr, int v) {
        if ((v & 0x08) != 0) // M1
            op[opPtr + 0].KEY_ON(1, this.eg_cnt);
        else
            op[opPtr + 0].KEY_OFF(~1);

        if ((v & 0x20) != 0) // M2
            op[opPtr + 1].KEY_ON(1, this.eg_cnt);
        else
            op[opPtr + 1].KEY_OFF(~1);

        if ((v & 0x10) != 0) // C1
            op[opPtr + 2].KEY_ON(1, this.eg_cnt);
        else
            op[opPtr + 2].KEY_OFF(~1);

        if ((v & 0x40) != 0) // C2
            op[opPtr + 3].KEY_ON(1, this.eg_cnt);
        else
            op[opPtr + 3].KEY_OFF(~1);
    }

    private void refresh_EG(Operator[] opBuf, int opPtr) {
        int kc;
        int v;

        Operator op = opBuf[opPtr];

        kc = op.kc;

        // = 32 + 2*RATE + RKS = max 126

        v = kc >> op.ks;
        if ((op.ar + v) < 32 + 62) {
            op.eg_sh_ar = eg_rate_shift[op.ar + v];
            op.eg_sel_ar = eg_rate_select[op.ar + v];
        } else {
            op.eg_sh_ar = 0;
            op.eg_sel_ar = (byte) (17 * RATE_STEPS);
        }
        op.eg_sh_d1r = eg_rate_shift[op.d1r + v];
        op.eg_sel_d1r = eg_rate_select[op.d1r + v];
        op.eg_sh_d2r = eg_rate_shift[op.d2r + v];
        op.eg_sel_d2r = eg_rate_select[op.d2r + v];
        op.eg_sh_rr = eg_rate_shift[op.rr + v];
        op.eg_sel_rr = eg_rate_select[op.rr + v];


        opPtr++;
        op = opBuf[opPtr];

        v = kc >> op.ks;
        if ((op.ar + v) < 32 + 62) {
            op.eg_sh_ar = eg_rate_shift[op.ar + v];
            op.eg_sel_ar = eg_rate_select[op.ar + v];
        } else {
            op.eg_sh_ar = 0;
            op.eg_sel_ar = (byte) (17 * RATE_STEPS);
        }
        op.eg_sh_d1r = eg_rate_shift[op.d1r + v];
        op.eg_sel_d1r = eg_rate_select[op.d1r + v];
        op.eg_sh_d2r = eg_rate_shift[op.d2r + v];
        op.eg_sel_d2r = eg_rate_select[op.d2r + v];
        op.eg_sh_rr = eg_rate_shift[op.rr + v];
        op.eg_sel_rr = eg_rate_select[op.rr + v];

        opPtr++;
        op = opBuf[opPtr];

        v = kc >> op.ks;
        if ((op.ar + v) < 32 + 62) {
            op.eg_sh_ar = eg_rate_shift[op.ar + v];
            op.eg_sel_ar = eg_rate_select[op.ar + v];
        } else {
            op.eg_sh_ar = 0;
            op.eg_sel_ar = (byte) (17 * RATE_STEPS);
        }
        op.eg_sh_d1r = eg_rate_shift[op.d1r + v];
        op.eg_sel_d1r = eg_rate_select[op.d1r + v];
        op.eg_sh_d2r = eg_rate_shift[op.d2r + v];
        op.eg_sel_d2r = eg_rate_select[op.d2r + v];
        op.eg_sh_rr = eg_rate_shift[op.rr + v];
        op.eg_sel_rr = eg_rate_select[op.rr + v];

        opPtr++;
        op = opBuf[opPtr];

        v = kc >> op.ks;
        if ((op.ar + v) < 32 + 62) {
            op.eg_sh_ar = eg_rate_shift[op.ar + v];
            op.eg_sel_ar = eg_rate_select[op.ar + v];
        } else {
            op.eg_sh_ar = 0;
            op.eg_sel_ar = (byte) (17 * RATE_STEPS);
        }
        op.eg_sh_d1r = eg_rate_shift[op.d1r + v];
        op.eg_sel_d1r = eg_rate_select[op.d1r + v];
        op.eg_sh_d2r = eg_rate_shift[op.d2r + v];
        op.eg_sel_d2r = eg_rate_select[op.d2r + v];
        op.eg_sh_rr = eg_rate_shift[op.rr + v];
        op.eg_sel_rr = eg_rate_select[op.rr + v];
    }

    /** write a register on YM2151 chips number 'n' */
    public void ym2151_write_reg(int r, int v) {
        int i = (r & 0x07) * 4 + ((r & 0x18) >> 3);
        Operator op = this.oper[i];
        Operator[] opBuf = this.oper;
        int opPtr = i;

        // adjust bus to 8 bits
        r &= 0xff;
        v &= 0xff;

        switch (r & 0xe0) {
        case 0x00:
            switch (r) {
            case 0x01: // LFO reset(bit 1), Test Register (other bits)
                this.test = (byte) v;
                if ((v & 2) != 0) this.lfo_phase = 0;
                break;

            case 0x08:
                // Psg is used in KEY_ON macro
                envelope_KONKOFF(this.oper, (v & 7) * 4, v);
                break;

            case 0x0f: // noise mode enable, noise period
                this.noise = v;
                this.noise_f = this.noise_tab[v & 0x1f];
                break;

            case 0x10: // timer A hi
                this.timer_A_index = (this.timer_A_index & 0x003) | (v << 2);
                break;

            case 0x11: // timer A low
                this.timer_A_index = (this.timer_A_index & 0x3fc) | (v & 3);
                break;

            case 0x12: // timer B
                this.timer_B_index = v;
                break;

            case 0x14: // CSM, irq flag reset, irq enable, timer start/stop

                this.irq_enable = v; // bit 3-timer B, bit 2-timer A, bit 7 - CSM

                if ((v & 0x10) != 0) { // reset timer A irq flag
                    int oldstate = this.status & 3;
                    this.status &= 0xfffffffe;
                }

                if ((v & 0x20) != 0) { // reset timer B irq flag
                    int oldstate = this.status & 3;
                    this.status &= 0xfffffffd;
                }

                if ((v & 0x02) != 0) { // load and start timer B
                    if (this.tim_B == 0) {
                        this.tim_B = 1;
                        this.tim_B_val = this.tim_B_tab[this.timer_B_index];
                    }
                } else { // stop timer B
                    this.tim_B = 0;
                }

                if ((v & 0x01) != 0) { // load and start timer A
                    if (this.tim_A == 0) {
                        this.tim_A = 1;
                        this.tim_A_val = this.tim_A_tab[this.timer_A_index];
                    }
                } else { // stop timer A
                    this.tim_A = 0;
                }
                break;

            case 0x18: { // LFO frequency
                this.lfo_overflow = (1 << ((15 - (v >> 4)) + 3)) * (1 << LFO_SH);
                this.lfo_counter_add = 0x10 + (v & 0x0f);
            }
            break;

            case 0x19: // PMD (bit 7==1) or AMD (bit 7==0)
                if ((v & 0x80) != 0)
                    this.pmd = (byte) (v & 0x7f);
                else
                    this.amd = (byte) (v & 0x7f);
                break;

            case 0x1b: // CT2, CT1, LFO waveform
                this.ct = (byte) (v >> 6);
                this.lfo_wsel = (byte) (v & 3);
                break;

            default:
                //Debug.printf("YM2151 Write %02x to undocumented register // #%02x\n",v,r);
                break;
            }
            break;

        case 0x20:
            op = this.oper[(r & 7) * 4];
            opBuf = this.oper;
            opPtr = (r & 7) * 4;
            switch (r & 0x18) {
            case 0x00: // RL enable, Feedback, Connection
                op.fb_shift = ((v >> 3) & 7) != 0 ? ((v >> 3) & 7) + 6 : 0;
                this.pan[(r & 7) * 2] = (v & 0x40) != 0 ? ~0 : 0;
                this.pan[(r & 7) * 2 + 1] = (v & 0x80) != 0 ? ~0 : 0;
                this.connect[r & 7] = (byte) (v & 7);
                set_connect(opBuf, opPtr, r & 7, v & 7);
                break;

            case 0x08: // Key Code
                v &= 0x7f;
                if (v != op.kc) {
                    int kc, kc_channel;

                    kc_channel = (v - (v >> 2)) * 64;
                    kc_channel += 768;
                    kc_channel |= (op.kc_i & 63);

                    opBuf[opPtr + 0].kc = v;
                    opBuf[opPtr + 0].kc_i = kc_channel;
                    opBuf[opPtr + 1].kc = v;
                    opBuf[opPtr + 1].kc_i = kc_channel;
                    opBuf[opPtr + 2].kc = v;
                    opBuf[opPtr + 2].kc_i = kc_channel;
                    opBuf[opPtr + 3].kc = v;
                    opBuf[opPtr + 3].kc_i = kc_channel;

                    kc = v >> 2;

                    opBuf[opPtr + 0].dt1 = this.dt1_freq[opBuf[opPtr + 0].dt1_i + kc];
                    opBuf[opPtr + 0].freq = ((this.freq[kc_channel + opBuf[opPtr + 0].dt2] + opBuf[opPtr + 0].dt1) * opBuf[opPtr + 0].mul) >> 1;

                    opBuf[opPtr + 1].dt1 = this.dt1_freq[opBuf[opPtr + 1].dt1_i + kc];
                    opBuf[opPtr + 1].freq = ((this.freq[kc_channel + opBuf[opPtr + 1].dt2] + opBuf[opPtr + 1].dt1) * opBuf[opPtr + 1].mul) >> 1;

                    opBuf[opPtr + 2].dt1 = this.dt1_freq[opBuf[opPtr + 2].dt1_i + kc];
                    opBuf[opPtr + 2].freq = ((this.freq[kc_channel + opBuf[opPtr + 2].dt2] + opBuf[opPtr + 2].dt1) * opBuf[opPtr + 2].mul) >> 1;

                    opBuf[opPtr + 3].dt1 = this.dt1_freq[opBuf[opPtr + 3].dt1_i + kc];
                    opBuf[opPtr + 3].freq = ((this.freq[kc_channel + opBuf[opPtr + 3].dt2] + opBuf[opPtr + 3].dt1) * opBuf[opPtr + 3].mul) >> 1;

                    refresh_EG(opBuf, opPtr);
                }
                break;

            case 0x10: // Key Fraction
                v >>= 2;
                if (v != (op.kc_i & 63)) {
                    int kc_channel;

                    kc_channel = v;
                    kc_channel |= op.kc_i & ~63;

                    opBuf[opPtr + 0].kc_i = kc_channel;
                    opBuf[opPtr + 1].kc_i = kc_channel;
                    opBuf[opPtr + 2].kc_i = kc_channel;
                    opBuf[opPtr + 3].kc_i = kc_channel;

                    opBuf[opPtr + 0].freq = ((this.freq[kc_channel + opBuf[opPtr + 0].dt2] + opBuf[opPtr + 0].dt1) * opBuf[opPtr + 0].mul) >> 1;
                    opBuf[opPtr + 1].freq = ((this.freq[kc_channel + opBuf[opPtr + 1].dt2] + opBuf[opPtr + 1].dt1) * opBuf[opPtr + 1].mul) >> 1;
                    opBuf[opPtr + 2].freq = ((this.freq[kc_channel + opBuf[opPtr + 2].dt2] + opBuf[opPtr + 2].dt1) * opBuf[opPtr + 2].mul) >> 1;
                    opBuf[opPtr + 3].freq = ((this.freq[kc_channel + opBuf[opPtr + 3].dt2] + opBuf[opPtr + 3].dt1) * opBuf[opPtr + 3].mul) >> 1;
                }
                break;

            case 0x18: // PMS, AMS
                op.pms = (v >> 4) & 7;
                op.ams = v & 3;
                break;
            }
            break;

        case 0x40: { // DT1, MUL
            int olddt1_i = op.dt1_i;
            int oldmul = op.mul;

            op.dt1_i = (v & 0x70) << 1;
            op.mul = (v & 0x0f) != 0 ? (v & 0x0f) << 1 : 1;

            if (olddt1_i != op.dt1_i)
                op.dt1 = this.dt1_freq[op.dt1_i + (op.kc >> 2)];

            if ((olddt1_i != op.dt1_i) || (oldmul != op.mul))
                op.freq = ((this.freq[op.kc_i + op.dt2] + op.dt1) * op.mul) >> 1;
        }
        break;

        case 0x60: // TL
            op.tl = (v & 0x7f) << (ENV_BITS - 7); // 7bit TL
            break;

        case 0x80: { // KS, AR
            int oldks = op.ks;
            int oldar = op.ar;

            op.ks = 5 - (v >> 6);
            op.ar = (v & 0x1f) != 0 ? 32 + ((v & 0x1f) << 1) : 0;

            if ((op.ar != oldar) || (op.ks != oldks)) {
                if ((op.ar + (op.kc >> op.ks)) < 32 + 62) {
                    op.eg_sh_ar = eg_rate_shift[op.ar + (op.kc >> op.ks)];
                    op.eg_sel_ar = eg_rate_select[op.ar + (op.kc >> op.ks)];
                } else {
                    op.eg_sh_ar = 0;
                    op.eg_sel_ar = (byte) (17 * RATE_STEPS);
                }
            }

            if (op.ks != oldks) {
                op.eg_sh_d1r = eg_rate_shift[op.d1r + (op.kc >> op.ks)];
                op.eg_sel_d1r = eg_rate_select[op.d1r + (op.kc >> op.ks)];
                op.eg_sh_d2r = eg_rate_shift[op.d2r + (op.kc >> op.ks)];
                op.eg_sel_d2r = eg_rate_select[op.d2r + (op.kc >> op.ks)];
                op.eg_sh_rr = eg_rate_shift[op.rr + (op.kc >> op.ks)];
                op.eg_sel_rr = eg_rate_select[op.rr + (op.kc >> op.ks)];
            }
        }
        break;

        case 0xa0: // LFO AM enable, D1R
            op.aMmask = (v & 0x80) != 0 ? ~0 : 0;
            op.d1r = (v & 0x1f) != 0 ? 32 + ((v & 0x1f) << 1) : 0;
            op.eg_sh_d1r = eg_rate_shift[op.d1r + (op.kc >> op.ks)];
            op.eg_sel_d1r = eg_rate_select[op.d1r + (op.kc >> op.ks)];
            break;

        case 0xc0: { // DT2, D2R
            int olddt2 = op.dt2;
            op.dt2 = dt2_tab[v >> 6];
            if (op.dt2 != olddt2)
                op.freq = ((this.freq[op.kc_i + op.dt2] + op.dt1) * op.mul) >> 1;
        }
        op.d2r = (v & 0x1f) != 0 ? 32 + ((v & 0x1f) << 1) : 0;
        op.eg_sh_d2r = eg_rate_shift[op.d2r + (op.kc >> op.ks)];
        op.eg_sel_d2r = eg_rate_select[op.d2r + (op.kc >> op.ks)];
        break;

        case 0xe0: // D1L, RR
            op.d1l = d1l_tab[v >> 4];
            op.rr = 34 + ((v & 0x0f) << 2);
            op.eg_sh_rr = eg_rate_shift[op.rr + (op.kc >> op.ks)];
            op.eg_sel_rr = eg_rate_select[op.rr + (op.kc >> op.ks)];
            break;
        }
    }

    private int ym2151_read_status() {
        return this.status;
    }

    /**
     * Initialize YM2151 emulator(s).
     *
     * @param clock is the chips clock in Hz
     * @param rate  is sampling rate
     */
    public Ym2151(int clock, int rate) {
        int chn;

        this.clock = clock;
        this.sampfreq = rate != 0 ? rate : 44100; // avoid division by 0 in init_chip_tables()
        init_chip_tables();

        this.lfo_timer_add = (int) ((1 << LFO_SH) * (clock / 64.0) / this.sampfreq);

        this.eg_timer_add = (int) ((1 << EG_SH) * (clock / 64.0) / this.sampfreq);
        this.eg_timer_overflow = (3) * (1 << EG_SH);
        //Debug.printf("YM2151[init] eg_timer_add=%8x eg_timer_overflow=%8x\n", this.eg_timer_add, this.eg_timer_overflow);

        this.tim_A = 0;
        this.tim_B = 0;
        for (chn = 0; chn < 8; chn++)
            this.muted[chn] = 0x00;
        //Debug.printf("YM2151[init] clock=%i sampfreq=%i\n", this.clock, this.sampfreq);
    }

    public void stop() {
    }

    /*
     * Reset chips number 'n'.
     */
    public void reset() {
        int i;

        // initialize hardware registers
        for (i = 0; i < 32; i++) {
            //memset(&this.oper[i], '\0', sizeof(Operator));
            if (this.oper[i] == null) this.oper[i] = new Operator();
            this.oper[i].volume = MAX_ATT_INDEX;
            this.oper[i].kc_i = 768; /* min kc_i value */
        }

        this.eg_timer = 0;
        this.eg_cnt = 0;

        this.lfo_timer = 0;
        this.lfo_counter = 0;
        this.lfo_phase = 0;
        this.lfo_wsel = 0;
        this.pmd = 0;
        this.amd = 0;
        this.lfa = 0;
        this.lfp = 0;

        this.test = 0;

        this.irq_enable = 0;
        this.tim_A = 0;
        this.tim_B = 0;
        this.tim_A_val = 0;
        this.tim_B_val = 0;
        this.timer_A_index = 0;
        this.timer_B_index = 0;
        this.timer_A_index_old = 0;
        this.timer_B_index_old = 0;

        this.noise = 0;
        this.noise_rng = 0;
        this.noise_p = 0;
        this.noise_f = this.noise_tab[0];

        this.csm_req = 0;
        this.status = 0;

        ym2151_write_reg(0x1b, 0); // only because of CT1, CT2 output pins */
        ym2151_write_reg(0x18, 0); // set LFO frequency */
        for (i = 0x20; i < 0x100; i++) { // set the operators */
            ym2151_write_reg(i, 0);
        }
    }

    private void calcChannel(int chan) {
        Operator op;
        int env;
        int am = 0;

        if (this.muted[chan] != 0)
            return;

        m2.v = c1.v = c2.v = mem.v = 0;
        op = this.oper[chan * 4]; /* M1 */
        Operator[] opBuf = this.oper;
        int opPtr = chan * 4;

        op.mem_connect.v = op.mem_value; // restore delayed sample (MEM) value to m2 or c2

        if (op.ams != 0)
            am = this.lfa << (op.ams - 1);
//if (chan == 0) {
//Debug.printf("Ch:%d ENV_QUIET:%d Op.tl:%d Op.volume:%d Op.state:%d Psg.eg_cnt:%d Psg.eg_timer_add:%d Psg.eg_timer_overflow:%d \n"
//, chan, ENV_QUIET, Op.tl, Op.volume, Op.state, Psg.eg_cnt, Psg.eg_timer_add, Psg.eg_timer_overflow);
//}
        env = op.volumeCalc(am);

        int out_ = op.fbOutPrev + op.fbOutCurr;
        op.fbOutPrev = op.fbOutCurr;

        if (op.connect == null) {
            // algorithm 5
            mem.v = c1.v = c2.v = op.fbOutPrev;
        } else {
            // other algorithms
            op.connect.v = op.fbOutPrev;
        }

        op.fbOutCurr = 0;
        if (env < ENV_QUIET) {
            if (op.fb_shift == 0)
                out_ = 0;
            op.fbOutCurr = op.op_calc1(env, (out_ << op.fb_shift));
        }

        env = opBuf[opPtr + 1].volumeCalc(am); // M2
        if (env < ENV_QUIET)
            opBuf[opPtr + 1].connect.v += opBuf[opPtr + 1].opCalc(env, m2.v);

        env = opBuf[opPtr + 2].volumeCalc(am); // C1
        if (env < ENV_QUIET)
            opBuf[opPtr + 2].connect.v += opBuf[opPtr + 2].opCalc(env, c1.v);

        env = opBuf[opPtr + 3].volumeCalc(am); // C2
        if (env < ENV_QUIET) {
            chanout[chan].v += opBuf[opPtr + 3].opCalc(env, c2.v);
            //Debug.printf("chanout[chan]:%d env:%d c2:%d", chanout[chan].v, env, c2.v);
        }
        if (chanout[chan].v > 16384) chanout[chan].v = 16384;
        else if (chanout[chan].v < -16384) chanout[chan].v = -16384;

        /* M1 */
        op.mem_value = mem.v;
    }

    private void calcChannel7() {
        Operator op;
        int env;
        int am = 0;

        if (this.muted[7] != 0)
            return;

        m2.v = c1.v = c2.v = mem.v = 0;
        op = this.oper[7 * 4]; // M1 */
        Operator[] opBuf = this.oper;
        int opPtr = 7 * 4;

        op.mem_connect.v = op.mem_value; // restore delayed sample (MEM) value to m2 or c2 */

        if (op.ams != 0)
            am = this.lfa << (op.ams - 1);
        env = op.volumeCalc(am);
//Debug.printf("1:env:%d ENV_QUIET:%d Op.tl:%d Op.volume:%d Op.state:%d Psg.eg_cnt:%d Psg.eg_timer_add:%d Psg.eg_timer_overflow:%d \n"
//, env, ENV_QUIET, Op.tl, Op.volume, Op.state, Psg.eg_cnt, Psg.eg_timer_add, Psg.eg_timer_overflow);
        int out = op.fbOutPrev + op.fbOutCurr;
        op.fbOutPrev = op.fbOutCurr;

        if (op.connect == null)
            // algorithm 5
            mem.v = c1.v = c2.v = op.fbOutPrev;
        else
            // other algorithms
            op.connect.v = op.fbOutPrev;

        op.fbOutCurr = 0;
        if (env < ENV_QUIET) {
            if (op.fb_shift == 0)
                out = 0;
            op.fbOutCurr = op.op_calc1(env, (out << op.fb_shift));
//Debug.printf("2:env:%d ENV_QUIET:%d \n", env, ENV_QUIET);
        }

        env = opBuf[opPtr + 1].volumeCalc(am); // M2 */
//Debug.printf("3:env:%d ENV_QUIET:%d \n", env, ENV_QUIET);
        if (env < ENV_QUIET)
            opBuf[opPtr + 1].connect.v += opBuf[opPtr + 1].opCalc(env, m2.v);

        env = opBuf[opPtr + 2].volumeCalc(am); // C1 */
//Debug.printf("4:env:%d ENV_QUIET:%d \n", env, ENV_QUIET);
        if (env < ENV_QUIET)
            opBuf[opPtr + 2].connect.v += opBuf[opPtr + 2].opCalc(env, c1.v);

        env = opBuf[opPtr + 3].volumeCalc(am); // C2 */
        //Debug.printf("5:env:%d ENV_QUIET:%d \n", env, ENV_QUIET);
        if ((this.noise & 0x80) != 0) {
            int noiseout;

            noiseout = 0;
            if (env < 0x3ff)
                noiseout = (env ^ 0x3ff) * 2; // range of the YM2151 noise output is -2044 to 2040 */
            chanout[7].v += ((this.noise_rng & 0x10000) != 0 ? noiseout : -noiseout); // bit 16 . output
        } else {
            if (env < ENV_QUIET)
                chanout[7].v += opBuf[opPtr + 3].opCalc(env, c2.v);
        }
        if (chanout[7].v > 16384) chanout[7].v = 16384;
        else if (chanout[7].v < -16384) chanout[7].v = -16384;
        // M1
        op.mem_value = mem.v;
    }

        /*
        The 'rate' is calculated from following formula (example on decay rate):
          rks = notecode after key scaling (a value from 0 to 31)
          DR = value written to the chips register
          rate = 2*DR + rks; (max rate = 2*31+31 = 93)
        Four MSBs of the 'rate' above are the 'main' rate (from 00 to 15)
        Two LSBs of the 'rate' above are the value 'x' (the shape type).
        (eg. '11 2' means that 'rate' is 11*4+2=46)

        NOTE: A 'sample' in the description below is actually 3 output samples,
        thats because the Envelope Generator clock is equal to internal_clock/3.

        Single '-' (minus) character in the diagrams below represents one sample
        on the output; this is for rates 11 x (11 0, 11 1, 11 2 and 11 3)

        these 'main' rates:
        00 x: single '-' = 2048 samples; (ie. level can change every 2048 samples)
        01 x: single '-' = 1024 samples;
        02 x: single '-' = 512 samples;
        03 x: single '-' = 256 samples;
        04 x: single '-' = 128 samples;
        05 x: single '-' = 64 samples;
        06 x: single '-' = 32 samples;
        07 x: single '-' = 16 samples;
        08 x: single '-' = 8 samples;
        09 x: single '-' = 4 samples;
        10 x: single '-' = 2 samples;
        11 x: single '-' = 1 sample; (ie. level can change every 1 sample)

        Shapes for rates 11 x look like this:
        rate:       step:
        11 0        01234567

        level:
        0           --
        1             --
        2               --
        3                 --

        rate:       step:
        11 1        01234567

        level:
        0           --
        1             --
        2               -
        3                -
        4                 --

        rate:       step:
        11 2        01234567

        level:
        0           --
        1             -
        2              -
        3               --
        4                 -
        5                  -

        rate:       step:
        11 3        01234567

        level:
        0           --
        1             -
        2              -
        3               -
        4                -
        5                 -
        6                  -


        For rates 12 x, 13 x, 14 x and 15 x output level changes on every
        sample - this means that the waveform looks like this: (but the level
        changes by different values on different steps)
        12 3        01234567

        0           -
        2            -
        4             -
        8              -
        10              -
        12               -
        14                -
        18                 -
        20                  -

        Notes about the timing:
        ----------------------

        1. Synchronism

        Output level of each two (or more) voices running at the same 'main' rate
        (eg 11 0 and 11 1 in the diagram below) will always be changing in sync,
        even if there're started with some delay.

        Note that, in the diagram below, the decay phase in channel 0 starts at
        sample // #2, while in channel 1 it starts at sample // #6. Anyway, both channels
        will always change their levels at exactly the same (following) samples.

        (S - start point of this channel, A-attack phase, D-decay phase):

        step:
        01234567012345670123456

        channel 0:
          --
         |  --
         |    -
         |     -
         |      --
         |        --
        |           --
        |             -
        |              -
        |               --
        AADDDDDDDDDDDDDDDD
        S

        01234567012345670123456
        channel 1:
              -
             | -
             |  --
             |    --
             |      --
             |        -
            |          -
            |           --
            |             --
            |               --
            AADDDDDDDDDDDDDDDD
            S
        01234567012345670123456


        2. Shifted (delayed) synchronism

        Output of each two (or more) voices running at different 'main' rate
        (9 1, 10 1 and 11 1 in the diagrams below) will always be changing
        in 'delayed-sync' (even if there're started with some delay as in "1.")

        Note that the shapes are delayed by exactly one sample per one 'main' rate
        increment. (Normally one would expect them to start at the same samples.)

        See diagram below (* - start point of the shape).

        cycle:
        0123456701234567012345670123456701234567012345670123456701234567

        rate 09 1
        *-------
                --------
                        ----
                            ----
                                --------
                                        *-------
                                        |       --------
                                        |               ----
                                        |                   ----
                                        |                       --------
        rate 10 1                       |
        --                              |
          *---                          |
              ----                      |
                  --                    |
                    --                  |
                      ----              |
                          *---          |
                          |   ----      |
                          |       --    | | <- one step (two samples) delay between 9 1 and 10 1
                          |         --  | |
                          |           ----|
                          |               *---
                          |                   ----
                          |                       --
                          |                         --
                          |                           ----
        rate 11 1         |
        -                 |
         --               |
           *-             |
             --           |
               -          |
                -         |
                 --       |
                   *-     |
                     --   |
                       -  || <- one step (one sample) delay between 10 1 and 11 1
                        - ||
                         --|
                           *-
                             --
                               -
                                -
                                 --
                                   *-
                                     --
                                       -
                                        -
                                         --
        */

    private void advance_eg() {
        Operator op;
        int i;

        this.eg_timer += this.eg_timer_add;

        while (this.eg_timer >= this.eg_timer_overflow) {
            this.eg_timer -= this.eg_timer_overflow;

            this.eg_cnt++;

            /* envelope generator */
            op = this.oper[0]; /* CH 0 M1 */
            Operator[] opBuf = this.oper;
            int opPtr = 0;
            i = 32;
            do {
                switch (op.state) {
                case EG_ATT: // attack phase */
                    if ((this.eg_cnt & ((1 << op.eg_sh_ar) - 1)) == 0) {
                        op.volume += (~op.volume *
                                (eg_inc[op.eg_sel_ar + ((this.eg_cnt >> op.eg_sh_ar) & 7)])
                        ) >> 4;

                        if (op.volume <= MIN_ATT_INDEX) {
                            op.volume = MIN_ATT_INDEX;
                            op.state = EG_DEC;
                        }

                    }
                    break;

                case EG_DEC: // decay phase */
                    if ((this.eg_cnt & ((1 << op.eg_sh_d1r) - 1)) == 0) {
                        op.volume += eg_inc[op.eg_sel_d1r + ((this.eg_cnt >> op.eg_sh_d1r) & 7)];

                        if (op.volume >= op.d1l)
                            op.state = EG_SUS;

                    }
                    break;

                case EG_SUS: // sustain phase */
                    if ((this.eg_cnt & ((1 << op.eg_sh_d2r) - 1)) == 0) {
                        op.volume += eg_inc[op.eg_sel_d2r + ((this.eg_cnt >> op.eg_sh_d2r) & 7)];

                        if (op.volume >= MAX_ATT_INDEX) {
                            op.volume = MAX_ATT_INDEX;
                            op.state = EG_OFF;
                        }

                    }
                    break;

                case EG_REL: // release phase */
                    if ((this.eg_cnt & ((1 << op.eg_sh_rr) - 1)) == 0) {
                        op.volume += eg_inc[op.eg_sel_rr + ((this.eg_cnt >> op.eg_sh_rr) & 7)];

                        if (op.volume >= MAX_ATT_INDEX) {
                            op.volume = MAX_ATT_INDEX;
                            op.state = EG_OFF;
                        }

                    }
                    break;
                }
                opPtr++;
                op = opBuf[opPtr < 32 ? opPtr : 0];
                i--;
            } while (i != 0);
        }
    }

    private void advance() {
        Operator op;
        Operator[] opBuf;
        int opPtr;
        int i;
        int a, p;

        /* LFO */
        if ((this.test & 2) != 0)
            this.lfo_phase = 0;
        else {
            this.lfo_timer += this.lfo_timer_add;
            if (this.lfo_timer >= this.lfo_overflow) {
                this.lfo_timer -= this.lfo_overflow;
                this.lfo_counter += this.lfo_counter_add;
                this.lfo_phase += (this.lfo_counter >> 4);
                this.lfo_phase &= 255;
                this.lfo_counter &= 15;
            }
        }

        i = this.lfo_phase;
        // calculate LFO AM and PM waveform value (all verified on real chips, except for noise algorithm which is impossible to analyse)
        switch (this.lfo_wsel) {
        case 0:
            // saw */
            // AM: 255 down to 0 */
            // PM: 0 to 127, -127 to 0 (at PMD=127: LFP = 0 to 126, -126 to 0) */
            a = 255 - i;
            if (i < 128)
                p = i;
            else
                p = i - 255;
            break;
        case 1:
            // square */
            // AM: 255, 0 */
            // PM: 128,-128 (LFP = exactly +PMD, -PMD) */
            if (i < 128) {
                a = 255;
                p = 128;
            } else {
                a = 0;
                p = -128;
            }
            break;
        case 2:
            /* triangle */
            /* AM: 255 down to 1 step -2; 0 up to 254 step +2 */
            /* PM: 0 to 126 step +2, 127 to 1 step -2, 0 to -126 step -2, -127 to -1 step +2*/
            if (i < 128)
                a = 255 - (i * 2);
            else
                a = (i * 2) - 256;

            if (i < 64) // i = 0..63
                p = i * 2; // 0 to 126 step +2
            else if (i < 128) // i = 64..127
                p = 255 - i * 2; // 127 to 1 step -2
            else if (i < 192) // i = 128..191
                p = 256 - i * 2; // 0 to -126 step -2
            else // i = 192..255
                p = i * 2 - 511; //-127 to -1 step +2
            break;
        case 3:
        default: // keep the compiler happy
            // random
            // the real algorithm is unknown !!!
            // We just use a snapshot of data from real chips

            /* AM: range 0 to 255    */
            /* PM: range -128 to 127 */

            a = lfo_noise_waveform[i];
            p = a - 128;
            break;
        }
        this.lfa = a * this.amd / 128;
        this.lfp = p * this.pmd / 128;


        // The Noise Generator of the YM2151 is 17-bit shift register.
        // Input to the bit16 is negated (bit0 XOR bit3) (EXNOR).
        // Output of the register is negated (bit0 XOR bit3).
        // Simply use bit16 as the noise output.
        this.noise_p += this.noise_f;
        i = (this.noise_p >> 16); // number of events (shifts of the shift register)
        this.noise_p &= 0xffff;
        while (i != 0) {
            int j;
            j = ((this.noise_rng ^ (this.noise_rng >> 3)) & 1) ^ 1;
            this.noise_rng = (j << 16) | (this.noise_rng >> 1);
            i--;
        }

        // phase generator
        op = this.oper[0]; // CH 0 M1
        opBuf = this.oper;
        opPtr = 0;
        i = 8;
        do {
            if (op.pms != 0) { // only when phase modulation from LFO is enabled for this channel
                int mod_ind = this.lfp; // -128..+127 (8bits signed)
                if (op.pms < 6)
                    mod_ind >>= 6 - op.pms;
                else
                    mod_ind <<= op.pms - 5;

                if (mod_ind != 0) {
                    int kc_channel = op.kc_i + mod_ind;
                    opBuf[opPtr + 0].phase += ((this.freq[kc_channel + opBuf[opPtr + 0].dt2] + opBuf[opPtr + 0].dt1) * opBuf[opPtr + 0].mul) >> 1;
                    opBuf[opPtr + 1].phase += ((this.freq[kc_channel + opBuf[opPtr + 1].dt2] + opBuf[opPtr + 1].dt1) * opBuf[opPtr + 1].mul) >> 1;
                    opBuf[opPtr + 2].phase += ((this.freq[kc_channel + opBuf[opPtr + 2].dt2] + opBuf[opPtr + 2].dt1) * opBuf[opPtr + 2].mul) >> 1;
                    opBuf[opPtr + 3].phase += ((this.freq[kc_channel + opBuf[opPtr + 3].dt2] + opBuf[opPtr + 3].dt1) * opBuf[opPtr + 3].mul) >> 1;
                } else { // phase modulation from LFO is equal to zero
                    opBuf[opPtr + 0].phase += opBuf[opPtr + 0].freq;
                    opBuf[opPtr + 1].phase += opBuf[opPtr + 1].freq;
                    opBuf[opPtr + 2].phase += opBuf[opPtr + 2].freq;
                    opBuf[opPtr + 3].phase += opBuf[opPtr + 3].freq;
                }
            } else { // phase modulation from LFO is disabled
                opBuf[opPtr + 0].phase += opBuf[opPtr + 0].freq;
                opBuf[opPtr + 1].phase += opBuf[opPtr + 1].freq;
                opBuf[opPtr + 2].phase += opBuf[opPtr + 2].freq;
                opBuf[opPtr + 3].phase += opBuf[opPtr + 3].freq;
            }

            opPtr += 4;
            op = opBuf[opPtr < 32 ? opPtr : 0];
            i--;
        } while (i != 0);

        // CSM is calculated *after* the phase generator calculations (verified on real chips)
        // CSM keyon line seems to be ORed with the KO line inside of the this.
        // The result is that it only works when KO (register 0x08) is off, ie. 0
        //
        // Interesting effect is that when timer A is set to 1023, the KEY ON happens
        // on every sample, so there is no KEY OFF at all - the result is that
        // the Sound played is the same as after normal KEY ON.

        if (this.csm_req != 0) { // CSM KEYON/KEYOFF seqeunce request
            if (this.csm_req == 2) { // KEY ON
                op = this.oper[0]; // CH 0 M1
                opBuf = this.oper;
                opPtr = 0;
                i = 32;
                do {
                    op.KEY_ON(2, this.eg_cnt);
                    opPtr++;
                    op = opBuf[opPtr];
                    i--;
                } while (i != 0);
                this.csm_req = 1;
            } else { // KEY OFF
                op = this.oper[0]; /* CH 0 M1 */
                opBuf = this.oper;
                opPtr = 0;
                i = 32;
                do {
                    op.KEY_OFF(~2);
                    opPtr++;
                    op = opBuf[opPtr];
                    i--;
                } while (i != 0);
                this.csm_req = 0;
            }
        }
    }

    /**
     * Generate samples for one of the YM2151's
     *
     * @param buffers is table of pointers to the buffers: left and right
     * @param length  is the number of samples that should be generated
     */
    public void update(int[][] buffers, int length) {
        int[] bufL = buffers[0];
        int[] bufR = buffers[1];

        if (this.tim_B != 0) {
            this.tim_B_val -= (length << TIMER_SH);
            if (this.tim_B_val <= 0) {
                this.tim_B_val += this.tim_B_tab[this.timer_B_index];
                if ((this.irq_enable & 0x08) != 0) {
                    int oldstate = this.status & 3;
                    this.status |= 2;
                }
            }
        }

        for (int i = 0; i < length; i++) {
            advance_eg();

            chanout[0].v = 0;
            chanout[1].v = 0;
            chanout[2].v = 0;
            chanout[3].v = 0;
            chanout[4].v = 0;
            chanout[5].v = 0;
            chanout[6].v = 0;
            chanout[7].v = 0;

            calcChannel(0);
            calcChannel(1);
            calcChannel(2);
            calcChannel(3);
            calcChannel(4);
            calcChannel(5);
            calcChannel(6);
            calcChannel7();

            int outl = chanout[0].v & this.pan[0];
            int outr = chanout[0].v & this.pan[1];
            outl += chanout[1].v & this.pan[2];
            outr += chanout[1].v & this.pan[3];
            outl += chanout[2].v & this.pan[4];
            outr += chanout[2].v & this.pan[5];
            outl += chanout[3].v & this.pan[6];
            outr += chanout[3].v & this.pan[7];
            outl += chanout[4].v & this.pan[8];
            outr += chanout[4].v & this.pan[9];
            outl += chanout[5].v & this.pan[10];
            outr += chanout[5].v & this.pan[11];
            outl += chanout[6].v & this.pan[12];
            outr += chanout[6].v & this.pan[13];
            outl += chanout[7].v & this.pan[14];
            outr += chanout[7].v & this.pan[15];

            outl >>= FINAL_SH;
            outr >>= FINAL_SH;
            //Debug.printf("%d %d", outl, outr);
            bufL[i] = (short) outl;
            bufR[i] = (short) outr;

            // SAVE_ALL_CHANNELS

            // calculate timer A
            if (this.tim_A != 0) {
                this.tim_A_val -= (1 << TIMER_SH);
                if (this.tim_A_val <= 0) {
                    this.tim_A_val += this.tim_A_tab[this.timer_A_index];
                    if ((this.irq_enable & 0x04) != 0) {
                        int oldstate = this.status & 3;
                        this.status |= 1;
                        //if ((!oldstate) && (Psg.IrqHandler)) (*Psg.IrqHandler)(this.device, 1);
                    }
                    if ((this.irq_enable & 0x80) != 0)
                        this.csm_req = 2; // request KEY ON / KEY OFF sequence */
                }
            }
            advance();
        }
    }

    private void setMuteMask(int muteMask) {
        for (int c = 0; c < 8; c++)
            muted[c] = (byte) ((muteMask >> c) & 0x01);
    }
}
