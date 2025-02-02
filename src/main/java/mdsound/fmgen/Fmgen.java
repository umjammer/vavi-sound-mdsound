/*
 * FM Sound Generator - Core Unit
 *
 * Copyright (C) cisc 1998, 2003.
 */

package mdsound.fmgen;


/**
 * FM Sound Generator - Core Unit.
 *
 * <h4>reference:</h4>
 * FM Sound generator for MPcm.A.MPcm.E., written by Tatsuyuki Satoh.
 *
 * <h4>Mystery:</h4>
 * OPNB CSM mode (I don't really understand the specifications)
 *
 * <h4>limit:</h4>
 * - When using SSGEC with AR!=31, the waveform may differ from the actual one.
 *
 * <h4>Acknowledgements:</h4>
 * <pre>
 * Tatsuyuki Satoh-san(Fm.c)
 * Hiromitsu Shioya-san(ADPCM-A)
 * DMP-SOFT.-san(OPNB)
 * KAJA-san(test program)
 * thank everyone who has provided us with various advice and support on message boards, etc.
 * </pre>
 *
 * @author cisc
 * @version $Id: Fmgen.cpp,v 1.49 2003/09/02 14:51:04 cisc Exp $
 */
public class Fmgen {

    // Table/etc

    /**
     * Constant #1
     * Static table size
     */
    public static final int FM_EG_BOTTOM = 955;
    public static final int FM_LFOBITS = 8; // Not changeable
    public static final int FM_TLBITS = 7;
    /**
     *
     */
    public static final int FM_TLENTS = 1 << FM_TLBITS;
    public static final int FM_LFOENTS = 1 << FM_LFOBITS;
    public static final int FM_TLPOS = FM_TLENTS / 4;
    // The precision of a sine wave is 2^(1/256)
    public static final int FM_CLENTS = 0x1000 * 2; // sin + TL + LFO
    // Difference in accuracy between EG and sine wave 0(low)-2(high)
    public static final int FM_SINEPRESIS = 2;
    public static final int FM_OPSINBITS = 10;
    public static final int FM_OPSINENTS = 1 << FM_OPSINBITS;
    // eg shift value of count
    public static final int FM_EGCBITS = 18;
    public static final int FM_LFOCBITS = 14;
    public static final int FM_PGBITS = 9;
    public static final int FM_RATIOBITS = 7; // Around 8-12?
    public static final int FM_EGBITS = 16;

    // fixed equation-based tables
    public static final int[][][] pmTable = {
            {new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS]},
            {new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS]}
    };

    public static final int[][][] amTable = {
            {new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS]},
            {new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS]}
    };

    // Types

    // class Chip;

    public static int storeSample(int dest, int data) {
        return limit(dest + data, 0x7fff, -0x8000);
    }

    public static int limit(int v, int max, int min) {
        return Math.min(max, Math.max(v, min));
    }

    /*
     * Creates tables
     */
    static {
        double[][] pms = {
                new double[] {0, 1 / 360.0, 2 / 360.0, 3 / 360.0, 4 / 360.0, 6 / 360.0, 12 / 360.0, 24 / 360.0,}, // OPNA
                //  { 0, 1/240., 2/240., 4/240., 10/240., 20/240., 80/240., 140/240., }, // OPM
                new double[] {0, 1 / 480.0, 2 / 480.0, 4 / 480.0, 10 / 480.0, 20 / 480.0, 80 / 480.0, 140 / 480.0,} // OPM
                //  { 0, 1/960., 2/960., 4/960., 10/960., 20/960., 80/960., 140/960., }, // OPM
        };
        //   3   6,      12      30       60       240      420  / 720
        // 1.000963
        //lfofref[level * max * wave];
        //pre = lfofref[level][pms * wave >> 8];
        int[][] amt = {
                new int[] {31, 6, 4, 3}, // OPNA
                new int[] {31, 2, 1, 0} // OPM
        };

        for (int type = 0; type < 2; type++) {
            for (int i = 0; i < 8; i++) {
                double pmb = pms[type][i];
                for (int j = 0; j < FM_LFOENTS; j++) {
                    double v = Math.pow(2.0, pmb * (2 * j - FM_LFOENTS + 1) / (FM_LFOENTS - 1));
                    double w = 0.6 * pmb * Math.sin(2 * j * 3.14159265358979323846 / FM_LFOENTS) + 1;
                    //pmTable[type][i][j] = int(0x10000 * (v - 1));
//                    if (type == 0)
                        pmTable[type][i][j] = (int) (0x10000 * (w - 1));
//                    else
//                        pmTable[type][i][j] = int(0x10000 * (v - 1));

//logger.log(Level.TRACE, "pmTable[%d][%d][%.2x] = %5d  %7.5f %7.5f".formatted(type, i, j, pmTable[type][i][j], v, w));
                }
            }
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < FM_LFOENTS; j++) {
                    amTable[type][i][j] = (((j * 4) >> amt[type][i]) * 2) << 2;
                }
            }
        }
    }

    // 4-Op Channel
    public static class Channel4 {

        // Operator
        static class Operator {
            public static final int[] noteTable = {
                    0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 3, 3, 3, 3, 3, 3,
                    4, 4, 4, 4, 4, 4, 4, 5, 6, 7, 7, 7, 7, 7, 7, 7,
                    8, 8, 8, 8, 8, 8, 8, 9, 10, 11, 11, 11, 11, 11, 11, 11,
                    12, 12, 12, 12, 12, 12, 12, 13, 14, 15, 15, 15, 15, 15, 15, 15,
                    16, 16, 16, 16, 16, 16, 16, 17, 18, 19, 19, 19, 19, 19, 19, 19,
                    20, 20, 20, 20, 20, 20, 20, 21, 22, 23, 23, 23, 23, 23, 23, 23,
                    24, 24, 24, 24, 24, 24, 24, 25, 26, 27, 27, 27, 27, 27, 27, 27,
                    28, 28, 28, 28, 28, 28, 28, 29, 30, 31, 31, 31, 31, 31, 31, 31,
            };

            public static final int[] dtTable = {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 2, 2, 4, 4, 4, 4,
                    4, 6, 6, 6, 8, 8, 8, 10, 10, 12, 12, 14, 16, 16, 16, 16,
                    2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 8, 8, 8, 10,
                    10, 12, 12, 14, 16, 16, 18, 20, 22, 24, 26, 28, 32, 32, 32, 32,
                    4, 4, 4, 4, 4, 6, 6, 6, 8, 8, 8, 10, 10, 12, 12, 14,
                    16, 16, 18, 20, 22, 24, 26, 28, 32, 34, 38, 40, 44, 44, 44, 44,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, -2, -2, -2, -2, -2, -2, -2, -2, -4, -4, -4, -4,
                    -4, -6, -6, -6, -8, -8, -8, -10, -10, -12, -12, -14, -16, -16, -16, -16,
                    -2, -2, -2, -2, -4, -4, -4, -4, -4, -6, -6, -6, -8, -8, -8, -10,
                    -10, -12, -12, -14, -16, -16, -18, -20, -22, -24, -26, -28, -32, -32, -32, -32,
                    -4, -4, -4, -4, -4, -6, -6, -6, -8, -8, -8, -10, -10, -12, -12, -14,
                    -16, -16, -18, -20, -22, -24, -26, -28, -32, -34, -38, -40, -44, -44, -44, -44,
            };

            public static final int[][] decayTable1 = {
                    {0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0},
                    {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1},
                    {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1},
                    {1, 1, 1, 0, 1, 1, 1, 0}, {1, 1, 1, 0, 1, 1, 1, 0},
                    {1, 0, 1, 0, 1, 0, 1, 0}, {1, 1, 1, 0, 1, 0, 1, 0},
                    {1, 1, 1, 0, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 0},
                    {1, 0, 1, 0, 1, 0, 1, 0}, {1, 1, 1, 0, 1, 0, 1, 0},
                    {1, 1, 1, 0, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 0},
                    {1, 0, 1, 0, 1, 0, 1, 0}, {1, 1, 1, 0, 1, 0, 1, 0},
                    {1, 1, 1, 0, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 0},
                    {1, 0, 1, 0, 1, 0, 1, 0}, {1, 1, 1, 0, 1, 0, 1, 0},
                    {1, 1, 1, 0, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 0},
                    {1, 0, 1, 0, 1, 0, 1, 0}, {1, 1, 1, 0, 1, 0, 1, 0},
                    {1, 1, 1, 0, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 0},
                    {1, 0, 1, 0, 1, 0, 1, 0}, {1, 1, 1, 0, 1, 0, 1, 0},
                    {1, 1, 1, 0, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 0},
                    {1, 0, 1, 0, 1, 0, 1, 0}, {1, 1, 1, 0, 1, 0, 1, 0},
                    {1, 1, 1, 0, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 0},
                    {1, 0, 1, 0, 1, 0, 1, 0}, {1, 1, 1, 0, 1, 0, 1, 0},
                    {1, 1, 1, 0, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 0},
                    {1, 0, 1, 0, 1, 0, 1, 0}, {1, 1, 1, 0, 1, 0, 1, 0},
                    {1, 1, 1, 0, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 0},
                    {1, 0, 1, 0, 1, 0, 1, 0}, {1, 1, 1, 0, 1, 0, 1, 0},
                    {1, 1, 1, 0, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 0},
                    {1, 1, 1, 1, 1, 1, 1, 1}, {2, 1, 1, 1, 2, 1, 1, 1},
                    {2, 1, 2, 1, 2, 1, 2, 1}, {2, 2, 2, 1, 2, 2, 2, 1},
                    {2, 2, 2, 2, 2, 2, 2, 2}, {4, 2, 2, 2, 4, 2, 2, 2},
                    {4, 2, 4, 2, 4, 2, 4, 2}, {4, 4, 4, 2, 4, 4, 4, 2},
                    {4, 4, 4, 4, 4, 4, 4, 4}, {8, 4, 4, 4, 8, 4, 4, 4},
                    {8, 4, 8, 4, 8, 4, 8, 4}, {8, 8, 8, 4, 8, 8, 8, 4},
                    {16, 16, 16, 16, 16, 16, 16, 16}, {16, 16, 16, 16, 16, 16, 16, 16},
                    {16, 16, 16, 16, 16, 16, 16, 16}, {16, 16, 16, 16, 16, 16, 16, 16}
            };

            public static final int[] decayTable2 = {
                    1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2047, 2047, 2047, 2047, 2047
            };

            public static final int[][] attackTable = {
                    {-1, -1, -1, -1, -1, -1, -1, -1}, {-1, -1, -1, -1, -1, -1, -1, -1},
                    {4, 4, 4, 4, 4, 4, 4, 4}, {4, 4, 4, 4, 4, 4, 4, 4},
                    {4, 4, 4, 4, 4, 4, 4, 4}, {4, 4, 4, 4, 4, 4, 4, 4},
                    {4, 4, 4, -1, 4, 4, 4, -1}, {4, 4, 4, -1, 4, 4, 4, -1},
                    {4, -1, 4, -1, 4, -1, 4, -1}, {4, 4, 4, -1, 4, -1, 4, -1},
                    {4, 4, 4, -1, 4, 4, 4, -1}, {4, 4, 4, 4, 4, 4, 4, -1},
                    {4, -1, 4, -1, 4, -1, 4, -1}, {4, 4, 4, -1, 4, -1, 4, -1},
                    {4, 4, 4, -1, 4, 4, 4, -1}, {4, 4, 4, 4, 4, 4, 4, -1},
                    {4, -1, 4, -1, 4, -1, 4, -1}, {4, 4, 4, -1, 4, -1, 4, -1},
                    {4, 4, 4, -1, 4, 4, 4, -1}, {4, 4, 4, 4, 4, 4, 4, -1},
                    {4, -1, 4, -1, 4, -1, 4, -1}, {4, 4, 4, -1, 4, -1, 4, -1},
                    {4, 4, 4, -1, 4, 4, 4, -1}, {4, 4, 4, 4, 4, 4, 4, -1},
                    {4, -1, 4, -1, 4, -1, 4, -1}, {4, 4, 4, -1, 4, -1, 4, -1},
                    {4, 4, 4, -1, 4, 4, 4, -1}, {4, 4, 4, 4, 4, 4, 4, -1},
                    {4, -1, 4, -1, 4, -1, 4, -1}, {4, 4, 4, -1, 4, -1, 4, -1},
                    {4, 4, 4, -1, 4, 4, 4, -1}, {4, 4, 4, 4, 4, 4, 4, -1},
                    {4, -1, 4, -1, 4, -1, 4, -1}, {4, 4, 4, -1, 4, -1, 4, -1},
                    {4, 4, 4, -1, 4, 4, 4, -1}, {4, 4, 4, 4, 4, 4, 4, -1},
                    {4, -1, 4, -1, 4, -1, 4, -1}, {4, 4, 4, -1, 4, -1, 4, -1},
                    {4, 4, 4, -1, 4, 4, 4, -1}, {4, 4, 4, 4, 4, 4, 4, -1},
                    {4, -1, 4, -1, 4, -1, 4, -1}, {4, 4, 4, -1, 4, -1, 4, -1},
                    {4, 4, 4, -1, 4, 4, 4, -1}, {4, 4, 4, 4, 4, 4, 4, -1},
                    {4, -1, 4, -1, 4, -1, 4, -1}, {4, 4, 4, -1, 4, -1, 4, -1},
                    {4, 4, 4, -1, 4, 4, 4, -1}, {4, 4, 4, 4, 4, 4, 4, -1},
                    {4, 4, 4, 4, 4, 4, 4, 4}, {3, 4, 4, 4, 3, 4, 4, 4},
                    {3, 4, 3, 4, 3, 4, 3, 4}, {3, 3, 3, 4, 3, 3, 3, 4},
                    {3, 3, 3, 3, 3, 3, 3, 3}, {2, 3, 3, 3, 2, 3, 3, 3},
                    {2, 3, 2, 3, 2, 3, 2, 3}, {2, 2, 2, 3, 2, 2, 2, 3},
                    {2, 2, 2, 2, 2, 2, 2, 2}, {1, 2, 2, 2, 1, 2, 2, 2},
                    {1, 2, 1, 2, 1, 2, 1, 2}, {1, 1, 1, 2, 1, 1, 1, 2},
                    {0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0}
            };

            public static final int[][][][] ssgEnvTable = {
                    {
                           {{1, 1}, {1, 1}, {1, 1}}, // 08
                           {{0, 1}, {1, 1}, {1, 1}}  // 08 56~
                    },
                    {
                           {{0, 1}, {2, 0}, {2, 0}}, // 09
                           {{0, 1}, {2, 0}, {2, 0}}  // 09
                    },
                    {
                           {{1, -1}, {0, 1}, {1, -1}}, // 10
                           {{0, 1}, {1, -1}, {0, 1}}   // 10 60~
                    },
                    {
                           {{1, -1}, {0, 0}, {0, 0}}, // 11
                           {{0, 1}, {0, 0}, {0, 0}}   // 11 60~
                    },
                    {
                           {{2, -1}, {2, -1}, {2, -1}}, // 12
                           {{1, -1}, {2, -1}, {2, -1}}  // 12 56~
                    },
                    {
                           {{1, -1}, {0, 0}, {0, 0}}, // 13
                           {{1, -1}, {0, 0}, {0, 0}}  // 13
                    },
                    {
                           {{0, 1}, {1, -1}, {0, 1}}, // 14
                           {{1, -1}, {0, 1}, {1, -1}} // 14 60~
                    },
                    {
                           {{0, 1}, {2, 0}, {2, 0}}, // 15
                           {{1, -1}, {2, 0}, {2, 0}} // 15 60~
                    }
            };

            // Operator

            static int[] sineTable = new int[1024];
            static int[] clTable = new int[FM_CLENTS];

            /** OP type (MPcm, N...) */
            public Chip.OpType type;
            /** Block/Note */
            private int bn;
            /** EG output value */
            private int egLevel;
            /** Value to move to next eg_phase_ */
            private int egLevelOnNextPhase;
            /** Time until next EG transition */
            private int egCount;
            /** eg_count_ Diff */
            private int egCountDiff;
            /** EG+TL combined output value */
            private int egOut;
            /** TL output value */
            private int tlOut;
            //  int  pm_depth_; // PM depth
            //  int  am_depth_; // AM depth
            private int egRate;
            private int egCurveCount;
            private int ssgOffset;
            private int ssgVector;
            private int ssgPhase;

            /** key scale rate */
            private int keyScaleRate;
            private EGPhase egPhase;
            private int[] ams;
            public int ms;

            /** Total Level (0-127) */
            private int tl;
            /** Total Level Latch (for CSM mode) */
            private int tlLatch;
            /** Attack Rate (0-63) */
            private int ar;
            /** Decay Rate (0-63) */
            private int dr;
            /** Sustain Rate (0-63) */
            private int sr;
            /** Sustain Level (0-127) */
            private int sl;
            /** Release Rate (0-63) */
            private int rr;
            /** Keyscale (0-3) */
            private int ks;
            /** SSG-Type Envelop Controller */
            private int ssgType;

            private boolean keyOn;
            /** enable Amplitude Modulation */
            public boolean amOn;
            // The parameters were updated
            public boolean paramChanged;
            private boolean mute_;

            // 1. Sample synthesis

            /** Shift amount to convert ISample to envelope count (2π) */
            public static final int IS2EC_SHIFT = ((20 + FM_PGBITS) - 13);

            private Chip chip;
            public int out, out2;
            private int in2;

            // Phase Generator

            /** ΔP */
            private int dp;
            /** Detune */
            private int deTune;
            /** DT2 */
            private int deTune2;
            /** Multiple */
            private int multiple;
            /** Phase Current value */
            private int pgCount;
            /** Phase difference value */
            private int pgDiff;
            /** Phase difference value >> x */
            private int pgDiffLfo;

            /** Envelop Generator */
            public enum EGPhase {
                Next, Attack, Decay, Sustain, Release, Off;

                EGPhase next() {
                    return values()[ordinal() + (ordinal() < Off.ordinal() ? 1 : 0)];
                }
            }

            // Tables

            private final int[] rateTable = new int[16];
            private final int[][] mulTable = {new int[16], new int[16], new int[16], new int[16]};

            public int dbgOpOut;
            public int dbgPgOut;

            /** Constructs */
            public Operator() {
                // EG Part
                ar = dr = sr = rr = keyScaleRate = 0;
                ams = amTable[0][0];
                mute_ = false;
                keyOn = false;
                //tl_out_ = false;
                tlOut = 0;
                ssgType = 0;

                // PG Part
                multiple = 0;
                deTune = 0;
                deTune2 = 0;

                // LFO
                ms = 0;
            }

            /** Initializes */
            public void reset() {
                // EG part
                tl = tlLatch = 127;
                shiftPhase(EGPhase.Off);
                egCount = 0;
                egCurveCount = 0;
                ssgPhase = 0;

                // PG part
                pgCount = 0;

                // OP part
                out = out2 = 0;

                paramChanged = true;
            }

            /* Creating a logarithm table */
            static {
                //assert(FM_CLENTS >= 256);

                int p = 0;
                for (int i = 0; i < 256; i++) {
                    int v = (int) (Math.floor(Math.pow(2.0, 13.0 - i / 256.0)));
                    v = (v + 2) & ~3;
                    clTable[p++] = v;
                    clTable[p++] = -v;
                }
                while (p < FM_CLENTS) {
                    clTable[p] = clTable[p - 512] / 2;
                    p++;
                }

//for (i=0; i<13*256; i++)
// logger.log(Level.TRACE, "%4d, %d, %d".formatted(i, cltable[i*2], cltable[i*2+1]));

                // Creating a Sign Table
                double log2 = Math.log(2.0);
                for (int i = 0; i < FM_OPSINENTS / 2; i++) {
                    double r = (i * 2 + 1) * Math.PI / FM_OPSINENTS;
                    double q = -256 * Math.log(Math.sin(r)) / log2;
                    int s = (int) (Math.floor(q + 0.5)) + 1;
//logger.log(Level.TRACE, "%d, %d".formatted(s, cltable[s * 2] / 8));
                    sineTable[i] = s * 2;
                    sineTable[FM_OPSINENTS / 2 + i] = s * 2 + 1;
                }
            }

            public void setDPBN(int dp, int bn) {
                this.dp = dp;
                this.bn = bn;
                paramChanged = true;
            }

            /** Prepares */
            public void prepare() {
                if (paramChanged) {
                    paramChanged = false;
                    // PG Part
                    pgDiff = (dp + dtTable[deTune + bn]) * chip.getMulValue(deTune2, multiple);
                    pgDiffLfo = pgDiff >> 11;

                    // EG Part
                    keyScaleRate = bn >> (3 - ks);
                    tlOut = mute_ ? 0x3ff : tl * 8;

                    switch (egPhase) {
                    case Attack:
                        setEGRate(ar != 0 ? Math.min(63, ar + keyScaleRate) : 0);
                        break;
                    case Decay:
                        setEGRate(dr != 0 ? Math.min(63, dr + keyScaleRate) : 0);
                        egLevelOnNextPhase = sl * 8;
                        break;
                    case Sustain:
                        setEGRate(sr != 0 ? Math.min(63, sr + keyScaleRate) : 0);
                        break;
                    case Release:
                        setEGRate(Math.min(63, rr + keyScaleRate));
                        break;
                    }

                    // SSG-EG
                    if (ssgType != 0 && (egPhase != EGPhase.Release)) {
                        int m = ar >= ((ssgType == 8 || ssgType == 12) ? 56 : 60) ? 1 : 0;

                        //assert(0 <= ssg_phase_ && ssg_phase_ <= 2);
                        int phase = (ssgPhase >= 0 && ssgPhase <= 2) ? ssgPhase : 0;
                        int[] table = ssgEnvTable[ssgType & 7][m][phase];

                        ssgOffset = table[0] * 0x200;
                        ssgVector = table[1];
                    }
                    // LFO
                    ams = amTable[type.ordinal()][amOn ? (ms >> 4) & 3 : 0];
                    egUpdate();

                    dbgOpOut = 0;
                }
            }

            /** Change egPhase of envelope */
            public void shiftPhase(EGPhase nextPhase) {
                switch (nextPhase) {
                case Attack:
                    tl = tlLatch;
                    if (ssgType != 0) {
                        ssgPhase = ssgPhase + 1;
                        if (ssgPhase > 2)
                            ssgPhase = 1;

                        int m = ar >= ((ssgType == 8 || ssgType == 12) ? 56 : 60) ? 1 : 0;

                        //assert(0 <= ssg_phase_ && ssg_phase_ <= 2);
                        int phase = (ssgPhase >= 0 && ssgPhase <= 2) ? ssgPhase : 0;
                        int[] table = ssgEnvTable[ssgType & 7][m][phase];

                        ssgOffset = table[0] * 0x200;
                        ssgVector = table[1];
                    }
                    if ((ar + keyScaleRate) < 62) {
                        setEGRate(ar != 0 ? Math.min(63, ar + keyScaleRate) : 0);
                        egPhase = EGPhase.Attack;
                        break;
                    }

                    if (sl != 0) {
                        egLevel = 0;
                        egLevelOnNextPhase = ssgType != 0 ? Math.min(sl * 8, 0x200) : sl * 8;

                        setEGRate(dr != 0 ? Math.min(63, dr + keyScaleRate) : 0);
                        egPhase = EGPhase.Decay;
                        break;
                    }

                    egLevel = sl * 8;
                    egLevelOnNextPhase = ssgType != 0 ? 0x200 : 0x400;

                    setEGRate(sr != 0 ? Math.min(63, sr + keyScaleRate) : 0);
                    egPhase = EGPhase.Sustain;
                    break;
                case Decay:
                    if (sl != 0) {
                        egLevel = 0;
                        egLevelOnNextPhase = ssgType != 0 ? Math.min(sl * 8, 0x200) : sl * 8;

                        setEGRate(dr != 0 ? Math.min(63, dr + keyScaleRate) : 0);
                        egPhase = EGPhase.Decay;
                        break;
                    }

                    egLevel = sl * 8;
                    egLevelOnNextPhase = ssgType != 0 ? 0x200 : 0x400;

                    setEGRate(sr != 0 ? Math.min(63, sr + keyScaleRate) : 0);
                    egPhase = EGPhase.Sustain;
                    break;
                case Sustain:
                    egLevel = sl * 8;
                    egLevelOnNextPhase = ssgType != 0 ? 0x200 : 0x400;

                    setEGRate(sr != 0 ? Math.min(63, sr + keyScaleRate) : 0);
                    egPhase = EGPhase.Sustain;
                    break;

                case Release:
                    if (ssgType != 0) {
                        egLevel = egLevel * ssgVector + ssgOffset;
                        ssgVector = 1;
                        ssgOffset = 0;
                    }
                    if (egPhase == EGPhase.Attack || (egLevel < FM_EG_BOTTOM)) {
                        egLevelOnNextPhase = 0x400;
                        setEGRate(Math.min(63, rr + keyScaleRate));
                        egPhase = EGPhase.Release;
                        break;
                    }

                    egLevel = FM_EG_BOTTOM;
                    egLevelOnNextPhase = FM_EG_BOTTOM;
                    egUpdate();
                    setEGRate(0);
                    egPhase = EGPhase.Off;
                    break;

                case Off:
                default:
                    egLevel = FM_EG_BOTTOM;
                    egLevelOnNextPhase = FM_EG_BOTTOM;
                    egUpdate();
                    setEGRate(0);
                    egPhase = EGPhase.Off;
                    break;
                }
            }

            /** Block/F-Num */
            public void setFNum(int f) {
                dp = (f & 2047) << ((f >> 11) & 7);
                bn = noteTable[(f >> 7) & 127];
                paramChanged = true;
            }

            /** @param s 20+FM_PGBITS = 29 */
            public int sine_(int s) {
                return sineTable[(s >>> (20 + FM_PGBITS - FM_OPSINBITS)) & (FM_OPSINENTS - 1)];
            }

            public int sine(int s) {
                return sineTable[s & (FM_OPSINENTS - 1)];
            }

            public int logToLin(int a) {
//#if 1 // FM_CLENTS < 0xc00  // 400 for TL, 400 for ENV, 400 for LFO.
                return (a < FM_CLENTS) ? clTable[a] : 0;
//#else
                //return cltable[a];
//#endif
            }

            public void egUpdate() {
                if (ssgType == 0) {
                    egOut = Math.min(tlOut + egLevel, 0x3ff) << (1 + 2);
                } else {
                    egOut = Math.min(tlOut + egLevel * ssgVector + ssgOffset, 0x3ff) << (1 + 2);
                }
            }

            public void setEGRate(int rate) {
                egRate = rate;
                egCountDiff = decayTable2[rate / 4] * chip.getRatio();
            }

            /** EG Calculation */
            public void egCalc() {
                egCount = (2047 * 3) << FM_RATIOBITS; // TODO This shortcut reduces reproducibility

                if (egPhase == EGPhase.Attack) {
                    int c = attackTable[egRate][egCurveCount & 7];
                    if (c >= 0) {
                        egLevel -= 1 + (egLevel >> c);
                        if (egLevel <= 0)
                            shiftPhase(EGPhase.Decay);
                    }
                    egUpdate();
                } else {
                    if (ssgType == 0) {
                        egLevel += decayTable1[egRate][egCurveCount & 7];
                        if (egLevel >= egLevelOnNextPhase)
                            shiftPhase(egPhase.next());
                        egUpdate();
                    } else {
                        egLevel += 4 * decayTable1[egRate][egCurveCount & 7];
                        if (egLevel >= egLevelOnNextPhase) {
                            egUpdate();
                            switch (egPhase) {
                            case Decay:
                                shiftPhase(EGPhase.Sustain);
                                break;
                            case Sustain:
                                shiftPhase(EGPhase.Attack);
                                break;
                            case Release:
                                shiftPhase(EGPhase.Off);
                                break;
                            }
                        }
                    }
                }
                egCurveCount++;
            }

            public void egStep() {
                egCount -= egCountDiff;

                // It is rumored that the EG changes are synchronized across all slots.
                if (egCount <= 0)
                    egCalc();
            }

            /**
             * PG calculation
             * ret:2^(20+PGBITS) / cycle
             */
            public int pgCalc() {
                int ret = pgCount;
                pgCount += pgDiff;
                dbgPgOut = ret;
                return ret;
            }

            public int pgCalcL() {
                int ret = pgCount;
                pgCount += pgDiff + ((pgDiffLfo * chip.getPmV()) >> 5);
                dbgPgOut = ret;
                return ret;
            }

            /**
             * OP calculation
             * @param in ISample (up to 8π)
             */
            public int calc(int in) {
                egStep();
                out2 = out;

                int pgin = pgCalc() >> (20 + FM_PGBITS - FM_OPSINBITS);
                pgin += in >> (20 + FM_PGBITS - FM_OPSINBITS - (2 + IS2EC_SHIFT));
                out = logToLin(egOut + sine(pgin));

                dbgOpOut = out;
                return out;
            }

            public int calcL(int In) {
                egStep();

                int pgin = pgCalcL() >> (20 + FM_PGBITS - FM_OPSINBITS);
                pgin += In >> (20 + FM_PGBITS - FM_OPSINBITS - (2 + IS2EC_SHIFT));
                out = logToLin(egOut + sine(pgin) + ams[chip.getAmL()]);

                dbgOpOut = out;
                return out;
            }

            public int calcN(int noise) {
                egStep();

                int lv = Math.max(0, 0x3ff - (tlOut + egLevel)) << 1;

                // noise & 1 ? lv : equivalent to -lv
                noise = (noise & 1) - 1;
                out = (lv + noise) ^ noise;

                dbgOpOut = out;
                return out;
            }

            /**
             * OP (FB) Calculation
             * Self Feedback Modulation Max = 4π
             */
            public int calcFB(int fb) {
                egStep();

                int In = out + out2;
                out2 = out;

                int pgin = pgCalc() >> (20 + FM_PGBITS - FM_OPSINBITS);
                if (fb < 31) {
                    pgin += ((In << (1 + IS2EC_SHIFT)) >> fb) >> (20 + FM_PGBITS - FM_OPSINBITS);
                }
                out = logToLin(egOut + sine(pgin));
                dbgOpOut = out2;

                return out2;
            }

            public int calcFBL(int fb) {
                egStep();

                int In = out + out2;
                out2 = out;

                int pgin = pgCalcL() >> (20 + FM_PGBITS - FM_OPSINBITS);
                if (fb < 31) {
                    pgin += ((In << (1 + IS2EC_SHIFT)) >> fb) >> (20 + FM_PGBITS - FM_OPSINBITS);
                }

                out = logToLin(egOut + sine(pgin) + ams[chip.getAmL()]);
                dbgOpOut = out;

                return out;
            }

            public void resetFB() {
                out = out2 = 0;
            }

            /** Key On */
            public void keyOn() {
                if (!keyOn) {
                    keyOn = true;
                    if (egPhase == EGPhase.Off || egPhase == EGPhase.Release) {
                        ssgPhase = -1;
                        shiftPhase(EGPhase.Attack);
                        egUpdate();
                        in2 = out = out2 = 0;
                        pgCount = 0;
                    }
                }
            }

            /** Key Off */
            public void keyOff() {
                if (keyOn) {
                    keyOn = false;
                    shiftPhase(EGPhase.Release);
                }
            }

            /** Is the operator up and running? */
            public boolean isOn() {
                return egPhase != EGPhase.Off;
            }

            /** Detune (0-7) */
            public void setDT(int dt) {
                deTune = dt * 0x20;
                paramChanged = true;
            }

            /** DT2 (0-3) */
            public void setDT2(int dt2) {
                deTune2 = dt2 & 3;
                paramChanged = true;
            }

            /** Multiple (0-15) */
            public void setMULTI(int mul) {
                multiple = mul;
                paramChanged = true;
            }

            /** Total Level (0-127) (0.75dB step) */
            public void setTL(int tl, boolean csm) {
                if (!csm) {
                    this.tl = tl;
                    paramChanged = true;
                }
                tlLatch = tl;
            }

            /** Attack Rate (0-63) */
            public void setAR(int ar) {
                this.ar = ar;
                paramChanged = true;
            }

            /** Decay Rate (0-63) */
            public void setDR(int dr) {
                this.dr = dr;
                paramChanged = true;
            }

            /** Sustain Rate (0-63) */
            public void setSR(int sr) {
                this.sr = sr;
                paramChanged = true;
            }

            /** Sustain Level (0-127) */
            public void setSL(int sl) {
                this.sl = sl;
                paramChanged = true;
            }

            /** Release Rate (0-63) */
            public void setRR(int rr) {
                this.rr = rr;
                paramChanged = true;
            }

            /** Keyscale (0-3) */
            public void setKS(int ks) {
                this.ks = ks;
                paramChanged = true;
            }

            /** SSG-type Envelop (0-15) */
            public void setSSGEC(int ssgec) {
                if ((ssgec & 8) != 0)
                    ssgType = ssgec;
                else
                    ssgType = 0;
            }

            public void setAmOn(boolean amon) {
                amOn = amon;
                paramChanged = true;
            }

            public void mute(boolean mute) {
                mute_ = mute;
                paramChanged = true;
            }

            public void setMS(int ms) {
                this.ms = ms;
                paramChanged = true;
            }

            public void setChip(Chip chip) {
                this.chip = chip;
            }

            public static void makeTimeTable(int ratio) {
            }

            public void setMode(boolean modulator) {
            }

//            static void SetAML(int l);
//            static void SetPML(int l);

            public int out() {
                return out;
            }

            public int dbgGetIn2() {
                return in2;
            }

            public void dbgStopPG() {
                pgDiff = 0;
                pgDiffLfo = 0;
            }

            private void ssgShiftPhase(int mode) {
            }

            private int fbCalc(int fb) {
                return -1;
            }

            // friends

//            private class Channel4;

            private void fmNextPhase(Operator op) {
            }

            public static int[] dbgGetClTable() {
                return clTable;
            }

            public static int[] dbgGetSineTable() {
                return sineTable;
            }
        }

        /** Chip resource */
        public static class Chip {

            public enum OpType {
                typeN,
                typeM
            }

            private int ratio;
            private int amL;
            private int pmL;
            private int pmV;
            public OpType opType;
            private final int[][] mulTable = {new int[16], new int[16], new int[16], new int[16]};

            /**
             * Common parts within the chip
             */
            public Chip() {
                ratio = 0;
                amL = 0;
                pmL = 0;
                pmV = 0;
                opType = OpType.typeN;
            }

            /** Create a table that depends on the clock/sampling rate ratio */
            public void setRatio(int ratio) {
                if (this.ratio != ratio) {
                    this.ratio = ratio;
                    makeTable();
                }
            }

            /**
             * Set the AM level
             */
            public void setAML(int l) {
                amL = l & (FM_LFOENTS - 1);
            }

            /** Set PM level */
            public void setPML(int l) {
                pmL = l & (FM_LFOENTS - 1);
            }

            public void setPMV(int pmv) {
                pmV = pmv;
            }

            public int getMulValue(int dt2, int mul) {
                return mulTable[dt2][mul];
            }

            public int getAmL() {
                return amL;
            }

            public int getPmL() {
                return pmL;
            }

            public int getPmV() {
                return pmV;
            }

            public int getRatio() {
                return ratio;
            }

            private void makeTable() {
                // PG Part
                float[] dt2lv = new float[] {1.0f, 1.414f, 1.581f, 1.732f};
                for (int h = 0; h < 4; h++) {
                    //assert(2 + FM_RATIOBITS - FM_PGBITS >= 0);
                    double rr = dt2lv[h] * (double) (ratio) / (1 << (2 + FM_RATIOBITS - FM_PGBITS));
                    for (int l = 0; l < 16; l++) {
                        int mul = l > 0 ? l * 2 : 1;
                        mulTable[h][l] = (int) (mul * rr);
                    }
                }
            }
        }

        /**
         * 4-Op Channel
         */
        private static final int[] fbTable = {31, 7, 6, 5, 4, 3, 2, 1};

        private static final int[] kfTable = new int[64];

        static {
            // 100/64 cent =  2^(i*100/64*1200)
            for (int i = 0; i < 64; i++) {
                kfTable[i] = (int) (0x10000 * Math.pow(2.0, i / 768.0));
            }
        }

        private int fb;
        private final int[] buf = new int[4];
        /** Input pointer for each OP */
        private final int[] in = new int[3];
        /** Output pointer for each OP */
        private final int[] out = new int[3];
        private int[] pms;
        private int algo;
        private Chip chip;

        Operator[] op = new Operator[] {
                new Operator(), new Operator(), new Operator(), new Operator()
        };

        public Channel4() {
            setAlgorithm(0);
            pms = pmTable[0][0];
        }

        /** Rests */
        public void reset() {
            op[0].reset();
            op[1].reset();
            op[2].reset();
            op[3].reset();
        }

        /** Preparing Calc */
        public int prepare() {
            op[0].prepare();
            op[1].prepare();
            op[2].prepare();
            op[3].prepare();

            pms = pmTable[op[0].type.ordinal()][op[0].ms & 7];
            int key = (op[0].isOn() || op[1].isOn() || op[2].isOn() || op[3].isOn()) ? 1 : 0;
            int lfo = (op[0].ms & ((op[0].amOn || op[1].amOn || op[2].amOn || op[3].amOn) ? 0x37 : 7)) != 0 ? 2 : 0;
            return key | lfo;
        }

        /** Set F-Number/BLOCK */
        public void setFNum(int f) {
            for (int i = 0; i < 4; i++)
                op[i].setFNum(f);
        }

        /** Set KC/KF */
        public void setKCKF(int kc, int kf) {
            int[] kcTable = {
                    5197, 5506, 5833, 6180, 6180, 6547, 6937, 7349,
                    7349, 7786, 8249, 8740, 8740, 9259, 9810, 10394,
            };

            int oct = 19 - ((kc >> 4) & 7);

            //logger.log(Level.TRACE, "%p".formatted(this));
            int kcv = kcTable[kc & 0x0f];
            kcv = (kcv + 2) / 4 * 4;
            //logger.log(Level.TRACE, " %.4x".formatted(kcv));
            int dp = kcv * kfTable[kf & 0x3f];
            //logger.log(Level.TRACE, " %.4x %.4x %.8x".formatted(kcv, kftable[kf & 0x3f], dp >> oct));
            dp >>>= 16 + 3;
            dp <<= 16 + 3;
            dp >>>= oct;
            int bn = (kc >> 2) & 31;
            op[0].setDPBN(dp, bn);
            op[1].setDPBN(dp, bn);
            op[2].setDPBN(dp, bn);
            op[3].setDPBN(dp, bn);
            //logger.log(Level.TRACE, " %.8x".formatted(dp));
        }

        /** Key Control */
        public void keyControl(int key) {
            if ((key & 0x1) != 0) op[0].keyOn();
            else op[0].keyOff();
            if ((key & 0x2) != 0) op[1].keyOn();
            else op[1].keyOff();
            if ((key & 0x4) != 0) op[2].keyOn();
            else op[2].keyOff();
            if ((key & 0x8) != 0) op[3].keyOn();
            else op[3].keyOff();
        }

        /** Set the algorithm */
        public void setAlgorithm(int algo) {
            int[][] table1 = {
                    {0, 1, 1, 2, 2, 3},
                    {1, 0, 0, 1, 1, 2},
                    {1, 1, 1, 0, 0, 2},
                    {0, 1, 2, 1, 1, 2},
                    {0, 1, 2, 2, 2, 1},
                    {0, 1, 0, 1, 0, 1},
                    {0, 1, 2, 1, 2, 1},
                    {1, 0, 1, 0, 1, 0}
            };

            in[0] = table1[algo][0];
            out[0] = table1[algo][1];
            in[1] = table1[algo][2];
            out[1] = table1[algo][3];
            in[2] = table1[algo][4];
            out[2] = table1[algo][5];

            op[0].resetFB();
            this.algo = algo;
        }

        /** Synthesis */
        public int calc() {
            int r = 0;
            switch (algo) {
            case 0:
                op[2].calc(op[1].out());
                op[1].calc(op[0].out());
                r = op[3].calc(op[2].out());
                op[0].calcFB(fb);
                break;
            case 1:
                op[2].calc(op[0].out() + op[1].out());
                op[1].calc(0);
                r = op[3].calc(op[2].out());
                op[0].calcFB(fb);
                break;
            case 2:
                op[2].calc(op[1].out());
                op[1].calc(0);
                r = op[3].calc(op[0].out() + op[2].out());
                op[0].calcFB(fb);
                break;
            case 3:
                op[2].calc(0);
                op[1].calc(op[0].out());
                r = op[3].calc(op[1].out() + op[2].out());
                op[0].calcFB(fb);
                break;
            case 4:
                op[2].calc(0);
                r = op[1].calc(op[0].out());
                r += op[3].calc(op[2].out());
                op[0].calcFB(fb);
                break;
            case 5:
                r = op[2].calc(op[0].out());
                r += op[1].calc(op[0].out());
                r += op[3].calc(op[0].out());
                op[0].calcFB(fb);
                break;
            case 6:
                r = op[2].calc(0);
                r += op[1].calc(op[0].out());
                r += op[3].calc(0);
                op[0].calcFB(fb);
                break;
            case 7:
                r = op[2].calc(0);
                r += op[1].calc(0);
                r += op[3].calc(0);
                r += op[0].calcFB(fb);
                break;
            }
            return r;
        }

        /** Synthesis */
        public int calcL() {
            chip.setPMV(pms[chip.getPmL()]);

            int r = 0;
            switch (algo) {
            case 0:
                op[2].calcL(op[1].out());
                op[1].calcL(op[0].out());
                r = op[3].calcL(op[2].out());
                op[0].calcFBL(fb);
                break;
            case 1:
                op[2].calcL(op[0].out() + op[1].out());
                op[1].calcL(0);
                r = op[3].calcL(op[2].out());
                op[0].calcFBL(fb);
                break;
            case 2:
                op[2].calcL(op[1].out());
                op[1].calcL(0);
                r = op[3].calcL(op[0].out() + op[2].out());
                op[0].calcFBL(fb);
                break;
            case 3:
                op[2].calcL(0);
                op[1].calcL(op[0].out());
                r = op[3].calcL(op[1].out() + op[2].out());
                op[0].calcFBL(fb);
                break;
            case 4:
                op[2].calcL(0);
                r = op[1].calcL(op[0].out());
                r += op[3].calcL(op[2].out());
                op[0].calcFBL(fb);
                break;
            case 5:
                r = op[2].calcL(op[0].out());
                r += op[1].calcL(op[0].out());
                r += op[3].calcL(op[0].out());
                op[0].calcFBL(fb);
                break;
            case 6:
                r = op[2].calcL(0);
                r += op[1].calcL(op[0].out());
                r += op[3].calcL(0);
                op[0].calcFBL(fb);
                break;
            case 7:
                r = op[2].calcL(0);
                r += op[1].calcL(0);
                r += op[3].calcL(0);
                r += op[0].calcFBL(fb);
                break;
            }
            return r;
        }

        /** Synthesis */
        public int calcN(int noise) {
            buf[1] = buf[2] = buf[3] = 0;

            buf[0] = op[0].out;
            op[0].calcFB(fb);
            out[0] += op[1].calc(buf[in[0]]);
            out[1] += op[2].calc(buf[in[1]]);
            int o = op[3].out;
            op[3].calcN(noise);
            return buf[out[2]] + o;
        }

        /** Synthesis */
        public int calcLN(int noise) {
            chip.setPMV(pms[chip.getPmL()]);
            buf[1] = buf[2] = buf[3] = 0;

            buf[0] = op[0].out;
            op[0].calcFBL(fb);
            out[0] += op[1].calcL(buf[in[0]]);
            out[1] += op[2].calcL(buf[in[1]]);
            int o = op[3].out;
            op[3].calcN(noise);
            return buf[out[2]] + o;
        }

        /** Sets the operator type (LFO). */
        public void setType(Chip.OpType type) {
            for (int i = 0; i < 4; i++)
                op[i].type = type;
        }

        /** Self-feedback rate setting (0-7) */
        public void setFB(int feedback) {
            fb = fbTable[feedback];
        }

        /** OPNA LFO settings */
        public void setMS(int ms) {
            op[0].setMS(ms);
            op[1].setMS(ms);
            op[2].setMS(ms);
            op[3].setMS(ms);
        }

        /** Channel Mask */
        public void mute(boolean m) {
            for (int i = 0; i < 4; i++)
                op[i].mute(m);
        }

        /** Recalculate internal parameters */
        public void refresh() {
            for (int i = 0; i < 4; i++)
                op[i].paramChanged = true;
            //PARAMCHANGE(3);
        }

        public void setChip(Chip chip) {
            this.chip = chip;
            for (int i = 0; i < 4; i++)
                op[i].setChip(chip);
        }

        public void dbgStopPG() {
            for (int i = 0; i < 4; i++) op[i].dbgStopPG();
        }

        public static void PARAMCHANGE(int i) {
        }
    }
}
