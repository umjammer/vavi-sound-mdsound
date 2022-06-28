/*
 * FM Sound Generator
 * Copyright (C) cisc 1998, 2001.
 */

package mdsound.fmgen;

/**
 * FM Sound Generator - Core Unit
 * Copyright (C) cisc 1998, 2003.
 * <p>
 * $Id: Fmgen.cpp,v 1.49 2003/09/02 14:51:04 cisc Exp $
 * <p>
 * 参考:
 * FM Sound generator for MPcm.A.MPcm.E., written by Tatsuyuki Satoh.
 * <p>
 * 謎:
 * OPNB の CSM モード(仕様がよくわからない)
 * <p>
 * 制限:
 * ・AR!=31 で SSGEC を使うと波形が実際と異なる可能性あり
 * <p>
 * 謝辞:
 * Tatsuyuki Satoh さん(Fm.c)
 * Hiromitsu Shioya さん(ADPCM-A)
 * DMP-SOFT. さん(OPNB)
 * KAJA さん(test program)
 * ほか掲示板等で様々なご助言，ご支援をお寄せいただいた皆様に
 */
public class Fmgen {

    // Table/etc

    /**
     * 定数その１
     * 静的テーブルのサイズ
     */
    public static final int FM_EG_BOTTOM = 955;
    public static final int FM_LFOBITS = 8; // 変更不可
    public static final int FM_TLBITS = 7;
    /**
     *
     */
    public static final int FM_TLENTS = 1 << FM_TLBITS;
    public static final int FM_LFOENTS = 1 << FM_LFOBITS;
    public static final int FM_TLPOS = FM_TLENTS / 4;
    // サイン波の精度は 2^(1/256)
    public static final int FM_CLENTS = 0x1000 * 2; // sin + TL + LFO
    // EGとサイン波の精度の差  0(低)-2(高)
    public static final int FM_SINEPRESIS = 2;
    public static final int FM_OPSINBITS = 10;
    public static final int FM_OPSINENTS = 1 << FM_OPSINBITS;
    // eg の count のシフト値
    public static final int FM_EGCBITS = 18;
    public static final int FM_LFOCBITS = 14;
    public static final int FM_PGBITS = 9;
    public static final int FM_RATIOBITS = 7; // 8-12 くらいまで？
    public static final int FM_EGBITS = 16;

    // fixed equasion-based tables
    public static int[][][] pmTable = new int[][][] {
            new int[][] {new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS]},
            new int[][] {new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS]}
    };

    public static int[][][] amTable = new int[][][] {
            new int[][] {new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS]},
            new int[][] {new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS], new int[FM_LFOENTS]}
    };

    // Types

    // class Chip;

    public static int limit(int v, int max, int min) {
        return v > max ? max : Math.max(v, min);
    }

    /*
     * テーブル作成
     */
    static {
        final double[][] pms = new double[][] {
                new double[] {0, 1 / 360.0, 2 / 360.0, 3 / 360.0, 4 / 360.0, 6 / 360.0, 12 / 360.0, 24 / 360.0,}, // OPNA
                //  { 0, 1/240., 2/240., 4/240., 10/240., 20/240., 80/240., 140/240., }, // OPM
                new double[] {0, 1 / 480.0, 2 / 480.0, 4 / 480.0, 10 / 480.0, 20 / 480.0, 80 / 480.0, 140 / 480.0,}    // OPM
                //  { 0, 1/960., 2/960., 4/960., 10/960., 20/960., 80/960., 140/960., }, // OPM
        };
        //   3   6,      12      30       60       240      420  / 720
        // 1.000963
        // lfofref[level * max * wave];
        // pre = lfofref[level][pms * wave >> 8];
        final byte[][] amt = new byte[][] {
                new byte[] {31, 6, 4, 3}, // OPNA
                new byte[] {31, 2, 1, 0} // OPM
        };

        for (int type = 0; type < 2; type++) {
            for (int i = 0; i < 8; i++) {
                double pmb = pms[type][i];
                for (int j = 0; j < FM_LFOENTS; j++) {
                    double v = Math.pow(2.0, pmb * (2 * j - FM_LFOENTS + 1) / (FM_LFOENTS - 1));
                    double w = 0.6 * pmb * Math.sin(2 * j * 3.14159265358979323846 / FM_LFOENTS) + 1;
                    //pmtable[type][i][j] = int(0x10000 * (v - 1));
                    //if (type == 0)
                    pmTable[type][i][j] = (int) (0x10000 * (w - 1));
                    //else
                    //     pmtable[type][i][j] = int(0x10000 * (v - 1));

                    //System.err.printf("pmtable[%d][%d][%.2x] = %5d  %7.5f %7.5f\n", type, i, j, pmtable[type][i][j], v, w);
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
            public static final byte[] noteTable = new byte[] {
                    0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 3, 3, 3, 3, 3, 3,
                    4, 4, 4, 4, 4, 4, 4, 5, 6, 7, 7, 7, 7, 7, 7, 7,
                    8, 8, 8, 8, 8, 8, 8, 9, 10, 11, 11, 11, 11, 11, 11, 11,
                    12, 12, 12, 12, 12, 12, 12, 13, 14, 15, 15, 15, 15, 15, 15, 15,
                    16, 16, 16, 16, 16, 16, 16, 17, 18, 19, 19, 19, 19, 19, 19, 19,
                    20, 20, 20, 20, 20, 20, 20, 21, 22, 23, 23, 23, 23, 23, 23, 23,
                    24, 24, 24, 24, 24, 24, 24, 25, 26, 27, 27, 27, 27, 27, 27, 27,
                    28, 28, 28, 28, 28, 28, 28, 29, 30, 31, 31, 31, 31, 31, 31, 31,
            };

            public static final byte[] dtTable = new byte[] {
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

            public static final byte[][] decayTable1 = new byte[][] {
                    new byte[] {0, 0, 0, 0, 0, 0, 0, 0}, new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                    new byte[] {1, 1, 1, 1, 1, 1, 1, 1}, new byte[] {1, 1, 1, 1, 1, 1, 1, 1},
                    new byte[] {1, 1, 1, 1, 1, 1, 1, 1}, new byte[] {1, 1, 1, 1, 1, 1, 1, 1},
                    new byte[] {1, 1, 1, 0, 1, 1, 1, 0}, new byte[] {1, 1, 1, 0, 1, 1, 1, 0},
                    new byte[] {1, 0, 1, 0, 1, 0, 1, 0}, new byte[] {1, 1, 1, 0, 1, 0, 1, 0},
                    new byte[] {1, 1, 1, 0, 1, 1, 1, 0}, new byte[] {1, 1, 1, 1, 1, 1, 1, 0},
                    new byte[] {1, 0, 1, 0, 1, 0, 1, 0}, new byte[] {1, 1, 1, 0, 1, 0, 1, 0},
                    new byte[] {1, 1, 1, 0, 1, 1, 1, 0}, new byte[] {1, 1, 1, 1, 1, 1, 1, 0},
                    new byte[] {1, 0, 1, 0, 1, 0, 1, 0}, new byte[] {1, 1, 1, 0, 1, 0, 1, 0},
                    new byte[] {1, 1, 1, 0, 1, 1, 1, 0}, new byte[] {1, 1, 1, 1, 1, 1, 1, 0},
                    new byte[] {1, 0, 1, 0, 1, 0, 1, 0}, new byte[] {1, 1, 1, 0, 1, 0, 1, 0},
                    new byte[] {1, 1, 1, 0, 1, 1, 1, 0}, new byte[] {1, 1, 1, 1, 1, 1, 1, 0},
                    new byte[] {1, 0, 1, 0, 1, 0, 1, 0}, new byte[] {1, 1, 1, 0, 1, 0, 1, 0},
                    new byte[] {1, 1, 1, 0, 1, 1, 1, 0}, new byte[] {1, 1, 1, 1, 1, 1, 1, 0},
                    new byte[] {1, 0, 1, 0, 1, 0, 1, 0}, new byte[] {1, 1, 1, 0, 1, 0, 1, 0},
                    new byte[] {1, 1, 1, 0, 1, 1, 1, 0}, new byte[] {1, 1, 1, 1, 1, 1, 1, 0},
                    new byte[] {1, 0, 1, 0, 1, 0, 1, 0}, new byte[] {1, 1, 1, 0, 1, 0, 1, 0},
                    new byte[] {1, 1, 1, 0, 1, 1, 1, 0}, new byte[] {1, 1, 1, 1, 1, 1, 1, 0},
                    new byte[] {1, 0, 1, 0, 1, 0, 1, 0}, new byte[] {1, 1, 1, 0, 1, 0, 1, 0},
                    new byte[] {1, 1, 1, 0, 1, 1, 1, 0}, new byte[] {1, 1, 1, 1, 1, 1, 1, 0},
                    new byte[] {1, 0, 1, 0, 1, 0, 1, 0}, new byte[] {1, 1, 1, 0, 1, 0, 1, 0},
                    new byte[] {1, 1, 1, 0, 1, 1, 1, 0}, new byte[] {1, 1, 1, 1, 1, 1, 1, 0},
                    new byte[] {1, 0, 1, 0, 1, 0, 1, 0}, new byte[] {1, 1, 1, 0, 1, 0, 1, 0},
                    new byte[] {1, 1, 1, 0, 1, 1, 1, 0}, new byte[] {1, 1, 1, 1, 1, 1, 1, 0},
                    new byte[] {1, 1, 1, 1, 1, 1, 1, 1}, new byte[] {2, 1, 1, 1, 2, 1, 1, 1},
                    new byte[] {2, 1, 2, 1, 2, 1, 2, 1}, new byte[] {2, 2, 2, 1, 2, 2, 2, 1},
                    new byte[] {2, 2, 2, 2, 2, 2, 2, 2}, new byte[] {4, 2, 2, 2, 4, 2, 2, 2},
                    new byte[] {4, 2, 4, 2, 4, 2, 4, 2}, new byte[] {4, 4, 4, 2, 4, 4, 4, 2},
                    new byte[] {4, 4, 4, 4, 4, 4, 4, 4}, new byte[] {8, 4, 4, 4, 8, 4, 4, 4},
                    new byte[] {8, 4, 8, 4, 8, 4, 8, 4}, new byte[] {8, 8, 8, 4, 8, 8, 8, 4},
                    new byte[] {16, 16, 16, 16, 16, 16, 16, 16}, new byte[] {16, 16, 16, 16, 16, 16, 16, 16},
                    new byte[] {16, 16, 16, 16, 16, 16, 16, 16}, new byte[] {16, 16, 16, 16, 16, 16, 16, 16}
            };

            public static final int[] decayTable2 = new int[] {
                    1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2047, 2047, 2047, 2047, 2047
            };

            public static final byte[][] attackTable = new byte[][] {
                    new byte[] {-1, -1, -1, -1, -1, -1, -1, -1}, new byte[] {-1, -1, -1, -1, -1, -1, -1, -1},
                    new byte[] {4, 4, 4, 4, 4, 4, 4, 4}, new byte[] {4, 4, 4, 4, 4, 4, 4, 4},
                    new byte[] {4, 4, 4, 4, 4, 4, 4, 4}, new byte[] {4, 4, 4, 4, 4, 4, 4, 4},
                    new byte[] {4, 4, 4, -1, 4, 4, 4, -1}, new byte[] {4, 4, 4, -1, 4, 4, 4, -1},
                    new byte[] {4, -1, 4, -1, 4, -1, 4, -1}, new byte[] {4, 4, 4, -1, 4, -1, 4, -1},
                    new byte[] {4, 4, 4, -1, 4, 4, 4, -1}, new byte[] {4, 4, 4, 4, 4, 4, 4, -1},
                    new byte[] {4, -1, 4, -1, 4, -1, 4, -1}, new byte[] {4, 4, 4, -1, 4, -1, 4, -1},
                    new byte[] {4, 4, 4, -1, 4, 4, 4, -1}, new byte[] {4, 4, 4, 4, 4, 4, 4, -1},
                    new byte[] {4, -1, 4, -1, 4, -1, 4, -1}, new byte[] {4, 4, 4, -1, 4, -1, 4, -1},
                    new byte[] {4, 4, 4, -1, 4, 4, 4, -1}, new byte[] {4, 4, 4, 4, 4, 4, 4, -1},
                    new byte[] {4, -1, 4, -1, 4, -1, 4, -1}, new byte[] {4, 4, 4, -1, 4, -1, 4, -1},
                    new byte[] {4, 4, 4, -1, 4, 4, 4, -1}, new byte[] {4, 4, 4, 4, 4, 4, 4, -1},
                    new byte[] {4, -1, 4, -1, 4, -1, 4, -1}, new byte[] {4, 4, 4, -1, 4, -1, 4, -1},
                    new byte[] {4, 4, 4, -1, 4, 4, 4, -1}, new byte[] {4, 4, 4, 4, 4, 4, 4, -1},
                    new byte[] {4, -1, 4, -1, 4, -1, 4, -1}, new byte[] {4, 4, 4, -1, 4, -1, 4, -1},
                    new byte[] {4, 4, 4, -1, 4, 4, 4, -1}, new byte[] {4, 4, 4, 4, 4, 4, 4, -1},
                    new byte[] {4, -1, 4, -1, 4, -1, 4, -1}, new byte[] {4, 4, 4, -1, 4, -1, 4, -1},
                    new byte[] {4, 4, 4, -1, 4, 4, 4, -1}, new byte[] {4, 4, 4, 4, 4, 4, 4, -1},
                    new byte[] {4, -1, 4, -1, 4, -1, 4, -1}, new byte[] {4, 4, 4, -1, 4, -1, 4, -1},
                    new byte[] {4, 4, 4, -1, 4, 4, 4, -1}, new byte[] {4, 4, 4, 4, 4, 4, 4, -1},
                    new byte[] {4, -1, 4, -1, 4, -1, 4, -1}, new byte[] {4, 4, 4, -1, 4, -1, 4, -1},
                    new byte[] {4, 4, 4, -1, 4, 4, 4, -1}, new byte[] {4, 4, 4, 4, 4, 4, 4, -1},
                    new byte[] {4, -1, 4, -1, 4, -1, 4, -1}, new byte[] {4, 4, 4, -1, 4, -1, 4, -1},
                    new byte[] {4, 4, 4, -1, 4, 4, 4, -1}, new byte[] {4, 4, 4, 4, 4, 4, 4, -1},
                    new byte[] {4, 4, 4, 4, 4, 4, 4, 4}, new byte[] {3, 4, 4, 4, 3, 4, 4, 4},
                    new byte[] {3, 4, 3, 4, 3, 4, 3, 4}, new byte[] {3, 3, 3, 4, 3, 3, 3, 4},
                    new byte[] {3, 3, 3, 3, 3, 3, 3, 3}, new byte[] {2, 3, 3, 3, 2, 3, 3, 3},
                    new byte[] {2, 3, 2, 3, 2, 3, 2, 3}, new byte[] {2, 2, 2, 3, 2, 2, 2, 3},
                    new byte[] {2, 2, 2, 2, 2, 2, 2, 2}, new byte[] {1, 2, 2, 2, 1, 2, 2, 2},
                    new byte[] {1, 2, 1, 2, 1, 2, 1, 2}, new byte[] {1, 1, 1, 2, 1, 1, 1, 2},
                    new byte[] {0, 0, 0, 0, 0, 0, 0, 0}, new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                    new byte[] {0, 0, 0, 0, 0, 0, 0, 0}, new byte[] {0, 0, 0, 0, 0, 0, 0, 0}
            };

            public static final int[][][][] ssgEnvTable = new int[][][][] {
                    new int[][][] {
                            new int[][] {new int[] {1, 1}, new int[] {1, 1}, new int[] {1, 1}}, // 08
                            new int[][] {new int[] {0, 1}, new int[] {1, 1}, new int[] {1, 1}} // 08 56~
                    },
                    new int[][][] {
                            new int[][] {new int[] {0, 1}, new int[] {2, 0}, new int[] {2, 0}}, // 09
                            new int[][] {new int[] {0, 1}, new int[] {2, 0}, new int[] {2, 0}} // 09
                    },
                    new int[][][] {
                            new int[][] {new int[] {1, -1}, new int[] {0, 1}, new int[] {1, -1}}, // 10
                            new int[][] {new int[] {0, 1}, new int[] {1, -1}, new int[] {0, 1}} // 10 60~
                    },
                    new int[][][] {
                            new int[][] {new int[] {1, -1}, new int[] {0, 0}, new int[] {0, 0}}, // 11
                            new int[][] {new int[] {0, 1}, new int[] {0, 0}, new int[] {0, 0}}      // 11 60~
                    },
                    new int[][][] {
                            new int[][] {new int[] {2, -1}, new int[] {2, -1}, new int[] {2, -1}}, // 12
                            new int[][] {new int[] {1, -1}, new int[] {2, -1}, new int[] {2, -1}} // 12 56~
                    },
                    new int[][][] {
                            new int[][] {new int[] {1, -1}, new int[] {0, 0}, new int[] {0, 0}}, // 13
                            new int[][] {new int[] {1, -1}, new int[] {0, 0}, new int[] {0, 0}} // 13
                    },
                    new int[][][] {
                            new int[][] {new int[] {0, 1}, new int[] {1, -1}, new int[] {0, 1}}, // 14
                            new int[][] {new int[] {1, -1}, new int[] {0, 1}, new int[] {1, -1}} // 14 60~
                    },
                    new int[][][] {
                            new int[][] {new int[] {0, 1}, new int[] {2, 0}, new int[] {2, 0}}, // 15
                            new int[][] {new int[] {1, -1}, new int[] {2, 0}, new int[] {2, 0}} // 15 60~
                    }
            };

            // Operator

            static int[] sineTable = new int[1024];
            static int[] clTable = new int[FM_CLENTS];

            // OP の種類 (MPcm, N...)
            public Chip.OpType type;
            // Block/Note
            private int bn;
            // EG の出力値
            private int egLevel;
            // 次の eg_phase_ に移る値
            private int egLevelOnNextPhase;
            // EG の次の変移までの時間
            private int egCount;
            // eg_count_ の差分
            private int egCountDiff;
            // EG+TL を合わせた出力値
            private int egOut;
            // TL 分の出力値
            private int tlOut;
            //  int  pm_depth_; // PM depth
            //  int  am_depth_; // AM depth
            private int egRate;
            private int egCurveCount;
            private int ssgOffset;
            private int ssgVector;
            private int ssgPhase;

            // key scale rate
            private int keyScaleRate;
            private EGPhase egPhase;
            private int[] ams;
            public int ms;

            // Total Level (0-127)
            private int tl;
            // Total Level Latch (for CSM mode)
            private int tlLatch;
            // Attack Rate (0-63)
            private int ar;
            // Decay Rate (0-63)
            private int dr;
            // Sustain Rate (0-63)
            private int sr;
            // Sustain Level (0-127)
            private int sl;
            // Release Rate (0-63)
            private int rr;
            // Keyscale (0-3)
            private int ks;
            // SSG-Type Envelop Controller
            private int ssgType;

            private boolean keyOn;
            // enable Amplitude Modulation
            public boolean amOn;
            // パラメータが更新された
            public boolean paramChanged;
            private boolean mute_;

            // 1 サンプル合成

            // ISample を envelop count (2π) に変換するシフト量
            public static final int IS2EC_SHIFT = ((20 + FM_PGBITS) - 13);

            private Chip chip;
            public int out, out2;
            private int in2;

            // Phase Generator

            // ΔP
            private int dp;
            // Detune
            private int deTune;
            // DT2
            private int deTune2;
            // Multiple
            private int multiple;
            // Phase 現在値
            private int pgCount;
            // Phase 差分値
            private int pgDiff;
            // Phase 差分値 >> x
            private int pgDiffLfo;

            // Envelop Generator
            public enum EGPhase {
                Next, Attack, Decay, Sustain, Release, Off;

                EGPhase next() {
                    return values()[ordinal() + (ordinal() < Off.ordinal() ? 1 : 0)];
                }
            }

            // Tables

            private int[] rateTable = new int[16];
            private int[][] multable = new int[][] {new int[16], new int[16], new int[16], new int[16]};

            public int dbgOpOut;
            public int dbgPgOut;

            // 構築
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

            // 初期化
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

            // 対数テーブルの作成
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

                // for (i=0; i<13*256; i++)
                //  System.err.printf("%4d, %d, %d\n", i, cltable[i*2], cltable[i*2+1]);

                // サインテーブルの作成
                double log2 = Math.log(2.0);
                for (int i = 0; i < FM_OPSINENTS / 2; i++) {
                    double r = (i * 2 + 1) * Math.PI / FM_OPSINENTS;
                    double q = -256 * Math.log(Math.sin(r)) / log2;
                    int s = (int) (Math.floor(q + 0.5)) + 1;
                    //  System.err.printf("%d, %d\n", s, cltable[s * 2] / 8);
                    sineTable[i] = s * 2;
                    sineTable[FM_OPSINENTS / 2 + i] = s * 2 + 1;
                }
            }

            public void setDPBN(int dp, int bn) {
                this.dp = dp;
                this.bn = bn;
                paramChanged = true;
            }

            // 準備
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

            // envelop の egPhase 変更
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

            // Block/F-Num
            public void setFNum(int f) {
                dp = (f & 2047) << ((f >> 11) & 7);
                bn = noteTable[(f >> 7) & 127];
                paramChanged = true;
            }

            // 入力: s = 20+FM_PGBITS = 29
            public int sine_(int s) {
                return sineTable[((s) >> (20 + FM_PGBITS - FM_OPSINBITS)) & (FM_OPSINENTS - 1)];
            }

            public int sine(int s) {
                return sineTable[(s) & (FM_OPSINENTS - 1)];
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

            // EG 計算
            public void egCalc() {
                egCount = (2047 * 3) << FM_RATIOBITS; // ##この手抜きは再現性を低下させる

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

                // EG の変化は全スロットで同期しているという噂もある
                if (egCount <= 0)
                    egCalc();
            }

            // PG 計算
            // ret:2^(20+PGBITS) / cycle
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

            // OP 計算
            // in: ISample (最大 8π)
            public int calc(int In) {
                egStep();
                out2 = out;

                int pgin = pgCalc() >> (20 + FM_PGBITS - FM_OPSINBITS);
                pgin += In >> (20 + FM_PGBITS - FM_OPSINBITS - (2 + IS2EC_SHIFT));
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

                // noise & 1 ? lv : -lv と等価
                noise = (noise & 1) - 1;
                out = (lv + noise) ^ noise;

                dbgOpOut = out;
                return out;
            }

            // OP (FB) 計算
            // Self Feedback の変調最大 = 4π
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

            // キーオン
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

            // キーオフ
            public void keyOff() {
                if (keyOn) {
                    keyOn = false;
                    shiftPhase(EGPhase.Release);
                }
            }

            // オペレータは稼働中か？
            public boolean isOn() {
                return egPhase != EGPhase.Off;
            }

            // Detune (0-7)
            public void setDT(int dt) {
                deTune = dt * 0x20;
                paramChanged = true;
            }

            // DT2 (0-3)
            public void setDT2(int dt2) {
                deTune2 = dt2 & 3;
                paramChanged = true;
            }

            // Multiple (0-15)
            public void setMULTI(int mul) {
                multiple = mul;
                paramChanged = true;
            }

            // Total Level (0-127) (0.75dB step)
            public void setTL(int tl, boolean csm) {
                if (!csm) {
                    this.tl = tl;
                    paramChanged = true;
                }
                tlLatch = tl;
            }

            // Attack Rate (0-63)
            public void setAR(int ar) {
                this.ar = ar;
                paramChanged = true;
            }

            // Decay Rate (0-63)
            public void setDR(int dr) {
                this.dr = dr;
                paramChanged = true;
            }

            // Sustain Rate (0-63)
            public void setSR(int sr) {
                this.sr = sr;
                paramChanged = true;
            }

            // Sustain Level (0-127)
            public void setSL(int sl) {
                this.sl = sl;
                paramChanged = true;
            }

            // Release Rate (0-63)
            public void setRR(int rr) {
                this.rr = rr;
                paramChanged = true;
            }

            // Keyscale (0-3)
            public void setKS(int ks) {
                this.ks = ks;
                paramChanged = true;
            }

            // SSG-type Envelop (0-15)
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

            //  static void SetAML(int l);
            //  static void SetPML(int l);

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
            //private class Channel4;
            private void fmNextPhase(Operator op) {
            }

            public static int[] dbgGetClTable() {
                return clTable;
            }

            public static int[] dbgGetSineTable() {
                return sineTable;
            }
        }

        // Chip resource
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
            private int[][] mulTable = new int[][] {new int[16], new int[16], new int[16], new int[16]};

            /**
             * チップ内で共通な部分
             */
            public Chip() {
                ratio = 0;
                amL = 0;
                pmL = 0;
                pmV = 0;
                opType = OpType.typeN;
            }

            // クロック・サンプリングレート比に依存するテーブルを作成
            public void setRatio(int ratio) {
                if (this.ratio != ratio) {
                    this.ratio = ratio;
                    makeTable();
                }
            }

            /**
             * AM のレベルを設定
             */
            public void setAML(int l) {
                amL = l & (FM_LFOENTS - 1);
            }

            // PM のレベルを設定
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
                final float[] dt2lv = new float[] {1.0f, 1.414f, 1.581f, 1.732f};
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
        private static final byte[] fbTable = new byte[] {31, 7, 6, 5, 4, 3, 2, 1};

        private static int[] kfTable = new int[64];

        static {
            // 100/64 cent =  2^(i*100/64*1200)
            for (int i = 0; i < 64; i++) {
                kfTable[i] = (int) (0x10000 * Math.pow(2.0, i / 768.0));
            }
        }

        private int fb;
        private int[] buf = new int[4];
        // 各 OP の入力ポインタ
        private int[] in = new int[3];
        // 各 OP の出力ポインタ
        private int[] out = new int[3];
        private int[] pms;
        private int algo;
        private Chip chip;

        public Operator[] op = new Operator[] {
                new Operator(), new Operator(), new Operator(), new Operator()
        };

        public Channel4() {
            setAlgorithm(0);
            pms = pmTable[0][0];
        }

        // リセット
        public void reset() {
            op[0].reset();
            op[1].reset();
            op[2].reset();
            op[3].reset();
        }

        // Calc の用意
        public int prepare() {
            op[0].prepare();
            op[1].prepare();
            op[2].prepare();
            op[3].prepare();

            pms = pmTable[op[0].type.ordinal()][op[0].ms & 7];
            int key = (op[0].isOn() | op[1].isOn() | op[2].isOn() | op[3].isOn()) ? 1 : 0;
            int lfo = (op[0].ms & ((op[0].amOn | op[1].amOn | op[2].amOn | op[3].amOn) ? 0x37 : 7)) != 0 ? 2 : 0;
            return key | lfo;
        }

        // F-Number/BLOCK を設定
        public void setFNum(int f) {
            for (int i = 0; i < 4; i++)
                op[i].setFNum(f);
        }

        // KC/KF を設定
        public void setKCKF(int kc, int kf) {
            final int[] kcTable = new int[] {
                    5197, 5506, 5833, 6180, 6180, 6547, 6937, 7349,
                    7349, 7786, 8249, 8740, 8740, 9259, 9810, 10394,
            };

            int oct = 19 - ((kc >> 4) & 7);

            //System.err.printf("%p", this);
            int kcv = kcTable[kc & 0x0f];
            kcv = (kcv + 2) / 4 * 4;
            //System.err.printf(" %.4x", kcv);
            int dp = kcv * kfTable[kf & 0x3f];
            //System.err.printf(" %.4x %.4x %.8x", kcv, kftable[kf & 0x3f], dp >> oct);
            dp >>= 16 + 3;
            dp <<= 16 + 3;
            dp >>= oct;
            int bn = (kc >> 2) & 31;
            op[0].setDPBN(dp, bn);
            op[1].setDPBN(dp, bn);
            op[2].setDPBN(dp, bn);
            op[3].setDPBN(dp, bn);
            //System.err.printf(" %.8x\n", dp);
        }

        // キー制御
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

        // アルゴリズムを設定
        public void setAlgorithm(int algo) {
            final byte[][] table1 = new byte[][] {
                    new byte[] {0, 1, 1, 2, 2, 3},
                    new byte[] {1, 0, 0, 1, 1, 2},
                    new byte[] {1, 1, 1, 0, 0, 2},
                    new byte[] {0, 1, 2, 1, 1, 2},
                    new byte[] {0, 1, 2, 2, 2, 1},
                    new byte[] {0, 1, 0, 1, 0, 1},
                    new byte[] {0, 1, 2, 1, 2, 1},
                    new byte[] {1, 0, 1, 0, 1, 0}
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

        //  合成
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

        // 合成
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

        // 合成
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

        // 合成
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

        // オペレータの種類 (LFO) を設定
        public void setType(Chip.OpType type) {
            for (int i = 0; i < 4; i++)
                op[i].type = type;
        }

        // セルフ・フィードバックレートの設定 (0-7)
        public void setFB(int feedback) {
            fb = fbTable[feedback];
        }

        // OPNA 系 LFO の設定
        public void setMS(int ms) {
            op[0].setMS(ms);
            op[1].setMS(ms);
            op[2].setMS(ms);
            op[3].setMS(ms);
        }

        // チャンネル・マスク
        public void mute(boolean m) {
            for (int i = 0; i < 4; i++)
                op[i].mute(m);
        }

        // 内部パラメータを再計算
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
