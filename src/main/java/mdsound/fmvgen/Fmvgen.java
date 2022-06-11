package mdsound.fmvgen;

import mdsound.fmgen.Fmgen;


public class Fmvgen extends Fmgen {

    public enum OpType {
        typeN,
        typeM
    }

    public static final int[][][] sinetable = new int[][][] {
            new int[][] {new int[1024], new int[1024], new int[1024], new int[1024]},
            new int[][] {new int[1024], new int[1024], new int[1024], new int[1024]},
            new int[][] {new int[1024], new int[1024], new int[1024], new int[1024]},
            new int[][] {new int[1024], new int[1024], new int[1024], new int[1024]},
            new int[][] {new int[1024], new int[1024], new int[1024], new int[1024]},
            new int[][] {new int[1024], new int[1024], new int[1024], new int[1024]},
            new int[][] {new int[1024], new int[1024], new int[1024], new int[1024]},
            new int[][] {new int[1024], new int[1024], new int[1024], new int[1024]},
            new int[][] {new int[1024], new int[1024], new int[1024], new int[1024]},
            new int[][] {new int[1024], new int[1024], new int[1024], new int[1024]},
            new int[][] {new int[1024], new int[1024], new int[1024], new int[1024]},
            new int[][] {new int[1024], new int[1024], new int[1024], new int[1024]}
    };

    // Operator
    public static class Operator {
        public static final byte[] notetable = new byte[] {
                0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 3, 3, 3, 3, 3, 3,
                4, 4, 4, 4, 4, 4, 4, 5, 6, 7, 7, 7, 7, 7, 7, 7,
                8, 8, 8, 8, 8, 8, 8, 9, 10, 11, 11, 11, 11, 11, 11, 11,
                12, 12, 12, 12, 12, 12, 12, 13, 14, 15, 15, 15, 15, 15, 15, 15,
                16, 16, 16, 16, 16, 16, 16, 17, 18, 19, 19, 19, 19, 19, 19, 19,
                20, 20, 20, 20, 20, 20, 20, 21, 22, 23, 23, 23, 23, 23, 23, 23,
                24, 24, 24, 24, 24, 24, 24, 25, 26, 27, 27, 27, 27, 27, 27, 27,
                28, 28, 28, 28, 28, 28, 28, 29, 30, 31, 31, 31, 31, 31, 31, 31,
        };

        public static final byte[] dttable = new byte[] {
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

        public static final byte[][] decaytable1 = new byte[][] {
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

        public static final int[] decaytable2 = new int[] {
                1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2047, 2047, 2047, 2047, 2047
        };

        public static final byte[][] attacktable = new byte[][] {
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

        public static final int[][][][] ssgenvtable = new int[][][][] {
                new int[][][] {
                        new int[][] {new int[] {1, 1}, new int[] {1, 1}, new int[] {1, 1}},      // 08
                        new int[][] {new int[] {0, 1}, new int[] {1, 1}, new int[] {1, 1}}      // 08 56~
                },
                new int[][][] {
                        new int[][] {new int[] {0, 1}, new int[] {2, 0}, new int[] {2, 0}},      // 09
                        new int[][] {new int[] {0, 1}, new int[] {2, 0}, new int[] {2, 0}}      // 09
                },
                new int[][][] {
                        new int[][] {new int[] {1, -1}, new int[] {0, 1}, new int[] {1, -1}},      // 10
                        new int[][] {new int[] {0, 1}, new int[] {1, -1}, new int[] {0, 1}}      // 10 60~
                },
                new int[][][] {
                        new int[][] {new int[] {1, -1}, new int[] {0, 0}, new int[] {0, 0}},      // 11
                        new int[][] {new int[] {0, 1}, new int[] {0, 0}, new int[] {0, 0}}      // 11 60~
                },
                new int[][][] {
                        new int[][] {new int[] {2, -1}, new int[] {2, -1}, new int[] {2, -1}},      // 12
                        new int[][] {new int[] {1, -1}, new int[] {2, -1}, new int[] {2, -1}}      // 12 56~
                },
                new int[][][] {
                        new int[][] {new int[] {1, -1}, new int[] {0, 0}, new int[] {0, 0}},      // 13
                        new int[][] {new int[] {1, -1}, new int[] {0, 0}, new int[] {0, 0}}      // 13
                },
                new int[][][] {
                        new int[][] {new int[] {0, 1}, new int[] {1, -1}, new int[] {0, 1}},      // 14
                        new int[][] {new int[] {1, -1}, new int[] {0, 1}, new int[] {1, -1}}      // 14 60~
                },
                new int[][][] {
                        new int[][] {new int[] {0, 1}, new int[] {2, 0}, new int[] {2, 0}},      // 15
                        new int[][] {new int[] {1, -1}, new int[] {2, 0}, new int[] {2, 0}}      // 15 60~
                }
        };

        //
        // Operator
        //
        boolean tableHasMade = false;
        //int[] sinetable = new int[1024];
        static int[] clTable = new int[FM_CLENTS];

        public OpType type;       // OP の種類 (MPcm, N...)
        private int bn;       // Block/Note
        private int egLevel;  // EG の出力値
        private int egLevelOnNextPhase;    // 次の eg_phase_ に移る値
        private int egCount;      // EG の次の変移までの時間
        private int egCountDiff; // eg_count_ の差分
        private int egOut;        // EG+TL を合わせた出力値
        private int tlOut;        // TL 分の出力値
        //private boolean tl_out_;        // TL 分の出力値
        //  int  pm_depth_;  // PM depth
        //  int  am_depth_;  // AM depth
        private int egRate;
        private int egCurveCount;
        private int ssgOffset;
        private int ssgVector;
        private int ssgPhase;


        private int keyScaleRate;       // key scale rate
        private EGPhase egPhase;
        private int[] ams;
        public int ms;

        private int tl;           // Total Level  (0-127)
        private int tlLatch;     // Total Level Latch (for CSM mode)
        private int ar;           // Attack Rate   (0-63)
        private int dr;           // Decay Rate    (0-63)
        private int sr;           // Sustain Rate  (0-63)
        private int sl;           // Sustain Level (0-127)
        private int rr;           // Release Rate  (0-63)
        private int ks;           // Keyscale      (0-3)
        private int ssgType; // SSG-Type Envelop Control
        private int phaseReset;   // phaseReset(0/1)

        public int fb;
        public byte algLink;
        public byte wt;

        private boolean keyOn;
        public boolean amOn;     // enable Amplitude Modulation
        public boolean paramChanged;    // パラメータが更新された
        private boolean mute;

        // １サンプル合成

        // ISample を envelop count (2π) に変換するシフト量
        public static final int IS2EC_SHIFT = ((20 + FM_PGBITS) - 13);

        //typedef (int)32 Counter;

        private Chip chip;
        public int out, out2;
        private int in2;

        // Phase Generator this.
        private int dp;       // ΔP
        private int detune;       // Detune
        private int detune2;  // DT2
        private int multiple; // Multiple
        private int pgCount;   // Phase 現在値
        private int pgDiff;    // Phase 差分値
        private int pgDiffLfo; // Phase 差分値 >> x

        // Envelop Generator this.
        public enum EGPhase {
            Next, Attack, Decay, Sustain, Release, Off;

            EGPhase next() {
                return values()[ordinal() + (ordinal() < Off.ordinal() ? 1 : 0)];
            }
        }

        // Tables this.
        private int[] rateTable = new int[16];
        private int[][] multable = new int[][] {new int[16], new int[16], new int[16], new int[16]};

        public int dbgOpOut;
        public int dbgPgOut;


        // 構築
        public Operator() {
            if (!tableHasMade)
                makeTable();

            // EG Part
            ar = dr = sr = rr = keyScaleRate = 0;
            ams = amTable[0][0];
            mute = false;
            keyOn = false;
            //tl_out_ = false;
            tlOut = 0;
            ssgType = 0;

            // PG Part
            multiple = 0;
            detune = 0;
            detune2 = 0;

            // LFO
            ms = 0;

            // reset();
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

        public void makeTable() {
            // 対数テーブルの作成
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
                //System.err.System.err.printf("{0}:", cltable[p]);
                p++;
            }

            // for (i=0; i<13*256; i++)
            //  System.err.printf("%4d, %d, %d\n", i, cltable[i*2], cltable[i*2+1]);

            // サインテーブルの作成
            //double log2 = Math.log(2.0);
            //for (i = 0; i < FM_OPSINENTS / 2; i++)
            //{
            //double r = (i * 2 + 1) * FM_PI / FM_OPSINENTS;
            //double q = -256 * Math.log(Math.sin(r)) / log2;
            //int s = (int)((int)(Math.floor(q + 0.5)) + 1);
            //System.err.System.err.printf("{0}, {1}", s, cltable[s * 2] / 8);
            //System.err.System.err.printf("{0:d6} , {1:d6} , {2:d6} , {3:X4} , {4:X4}"
            //    , s
            //    , cltable[s * 2]
            //    , ((s * 2) % 2 == 0 ? 1 : -1) * (4095 - Math.abs((s * 2) / 2))
            //    , ((s * 2) % 2 == 0 ? 1 : -1) * (4095 - Math.abs((s * 2) / 2))
            //    , ((s * 2 + 1 ) % 2 == 0 ? 1 : -1) * (4095 - Math.abs((s * 2+1) / 2))
            //    );
            for (int j = 0; j < 12; j++) {
                Fmvgen.waveReset(j, 0);
                Fmvgen.waveReset(j, 1);
                Fmvgen.waveReset(j, 2);
                Fmvgen.waveReset(j, 3);
            }
            //}

            Fmvgen.makeLFOTable();

            tableHasMade = true;
        }

        public void setDPBN(int dp, int bn) {
            this.dp = dp;
            this.bn = bn;
            paramChanged = true;
            //PARAMCHANGE(1);
        }

        // 準備

        public void prepare() {
            if (paramChanged) {
                paramChanged = false;
                // PG Part
                pgDiff = (dp + dttable[detune + bn]) * chip.getMulValue(detune2, multiple);
                pgDiffLfo = pgDiff >> 11;

                // EG Part
                keyScaleRate = bn >> (3 - ks);
                tlOut = mute ? 0x3ff : tl * 8;

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
                    int[] table = ssgenvtable[ssgType & 7][m][ssgPhase];

                    ssgOffset = table[0] * 0x200;
                    ssgVector = table[1];
                }
                // LFO
                ams = amTable[(int) type.ordinal()][amOn ? (ms >> 4) & 3 : 0];
                egUpdate();

                dbgOpOut = 0;
            }
        }

        // envelop の eg_phase_ 変更

        public void shiftPhase(EGPhase nextphase) {
            switch (nextphase) {
            case Attack:        // Attack Phase
                tl = tlLatch;
                if (ssgType != 0) {
                    ssgPhase = ssgPhase + 1;
                    if (ssgPhase > 2)
                        ssgPhase = 1;

                    int m = ar >= ((ssgType == 8 || ssgType == 12) ? 56 : 60) ? 1 : 0;

                    //assert(0 <= ssg_phase_ && ssg_phase_ <= 2);
                    int[] table = ssgenvtable[ssgType & 7][m][ssgPhase];

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
                    egLevelOnNextPhase = (int) (ssgType != 0 ? Math.min(sl * 8, 0x200) : sl * 8);

                    setEGRate(dr != 0 ? Math.min(63, dr + keyScaleRate) : 0);
                    egPhase = EGPhase.Decay;
                    break;
                }

                egLevel = sl * 8;
                egLevelOnNextPhase = ssgType != 0 ? 0x200 : 0x400;

                setEGRate(sr != 0 ? Math.min(63, sr + keyScaleRate) : 0);
                egPhase = EGPhase.Sustain;
                break;
            case Decay:         // Decay Phase
                if (sl != 0) {
                    egLevel = 0;
                    egLevelOnNextPhase = ssgType != 0 ? Math.min(sl * 8, 0x200) : sl * 8;

                    setEGRate(dr != 0 ? Math.min(63, dr + keyScaleRate) : 0);
                    egPhase = EGPhase.Decay;
                    break;
                }

                egLevel = (int) (sl * 8);
                egLevelOnNextPhase = ssgType != 0 ? 0x200 : 0x400;

                setEGRate(sr != 0 ? Math.min(63, sr + keyScaleRate) : 0);
                egPhase = EGPhase.Sustain;
                break;
            case Sustain:       // Sustain Phase
                egLevel = (int) (sl * 8);
                egLevelOnNextPhase = ssgType != 0 ? 0x200 : 0x400;

                setEGRate(sr != 0 ? Math.min(63, sr + keyScaleRate) : 0);
                egPhase = EGPhase.Sustain;
                break;

            case Release:       // Release Phase
                if (ssgType != 0) {
                    egLevel = egLevel * ssgVector + ssgOffset;
                    ssgVector = 1;
                    ssgOffset = 0;
                }
                if (egPhase == EGPhase.Attack || (egLevel < FM_EG_BOTTOM)) //0x400/* && eg_phase_ != off*/))
                {
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

            case Off:           // off
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
            dp = (f & 2047) << (int) ((f >> 11) & 7);
            bn = notetable[(f >> 7) & 127];
            paramChanged = true;
        }

        // 入力: s = 20+FM_PGBITS = 29

        public int sine(int c, int s) {
            return sinetable[c][wt][((s) >> (20 + FM_PGBITS - FM_OPSINBITS)) & (FM_OPSINENTS - 1)];
        }

        public int SINE(int c, int s) {
            return sinetable[c][wt][(s) & (FM_OPSINENTS - 1)];
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
            egCountDiff = decaytable2[rate / 4] * chip.getRatio();
        }

        // EG 計算
        public void egCalc() {
            egCount = (2047 * 3) << FM_RATIOBITS; // この手抜きは再現性を低下させる

            if (egPhase == EGPhase.Attack) {
                int c = attacktable[egRate][egCurveCount & 7];
                if (c >= 0) {
                    egLevel -= 1 + (egLevel >> c);
                    if (egLevel <= 0)
                        shiftPhase(EGPhase.Decay);
                }
                egUpdate();
            } else {
                if (ssgType == 0) {
                    egLevel += decaytable1[egRate][egCurveCount & 7];
                    if (egLevel >= egLevelOnNextPhase)
                        shiftPhase(egPhase.next());
                    egUpdate();
                } else {
                    egLevel += 4 * decaytable1[egRate][egCurveCount & 7];
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
            return ret /* + pmv * pg_diff_;*/;
        }

        // OP 計算
        // in: ISample (最大 8π)
        public int calc(int ch, int In) {
            egStep();
            int In2 = In + out + out2;
            out2 = out;

            int pgin = pgCalc() >> (20 + FM_PGBITS - FM_OPSINBITS);
            //pgin += In >> (20 + FM_PGBITS - FM_OPSINBITS - (2 + IS2EC_SHIFT));
            if (fb < 31) {
                pgin += ((In2 << (int) (1 + IS2EC_SHIFT)) >> (int) fb) >> (20 + FM_PGBITS - FM_OPSINBITS);
                //System.err.System.err.printf("Calc:{0}", pgin);
            } else {
                pgin += In >> (20 + FM_PGBITS - FM_OPSINBITS - (2 + IS2EC_SHIFT));
            }
            out = logToLin(egOut + SINE(ch, pgin));

            dbgOpOut = out2;
            return out2;
        }

        public int calcL(int ch, int In) {
            egStep();
            int In2 = In + out + out2;
            out2 = out;

            int pgin = pgCalcL() >> (20 + FM_PGBITS - FM_OPSINBITS);
            //pgin += In >> (20 + FM_PGBITS - FM_OPSINBITS - (2 + IS2EC_SHIFT));
            if (fb < 31) {
                //                        17                                       19
                pgin += ((In2 << (1 + IS2EC_SHIFT)) >> (int) fb) >> (20 + FM_PGBITS - FM_OPSINBITS);
            } else {
                //                                      1
                pgin += In >> (20 + FM_PGBITS - FM_OPSINBITS - (2 + IS2EC_SHIFT));
            }
            out = logToLin(egOut + SINE(ch, pgin) + ams[chip.getAmL()]);

            dbgOpOut = out2;
            return out2;
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
        public int calcFB(int ch, int fb) {
            egStep();

            int In = out + out2;
            out2 = out;

            int pgin = pgCalc() >> (20 + FM_PGBITS - FM_OPSINBITS);
            if (fb < 31) {
                pgin += ((In << (1 + IS2EC_SHIFT)) >> fb) >> (20 + FM_PGBITS - FM_OPSINBITS);
                //System.err.System.err.printf("CalcFB:{0}", pgin);
            }
            out = logToLin(egOut + SINE(ch, pgin));
            dbgOpOut = out2;

            return out2;
        }

        public int calcFBL(int ch, int fb) {
            egStep();

            int In = out + out2;
            out2 = out;

            int pgin = pgCalcL() >> (20 + FM_PGBITS - FM_OPSINBITS);
            if (fb < 31) {
                pgin += ((In << (int) (1 + IS2EC_SHIFT)) >> fb) >> (20 + FM_PGBITS - FM_OPSINBITS);
            }

            out = logToLin(egOut + SINE(ch, pgin) + ams[chip.getAmL()]);
            dbgOpOut = out;

            return out;
        }

        public void resetFB() {
            out = out2 = 0;
        }

        // キーオン
        public void keyOn() {
            if (keyOn) return;

            keyOn = true;

            if (phaseReset != 0) {
                //位相リセットスイッチ有効

                shiftPhase(EGPhase.Off);
                ssgPhase = -1;
                shiftPhase(EGPhase.Attack);
                egUpdate();
                in2 = out = out2 = 0;

                pgCount = 0;

                return;
            }

            //位相リセットスイッチ無効

            if (egPhase == EGPhase.Off || egPhase == EGPhase.Release) {
                ssgPhase = -1;
                shiftPhase(EGPhase.Attack);
                egUpdate();
                in2 = out = out2 = 0;

                pgCount = 0;
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
            detune = dt * 0x20;
            paramChanged = true;
        }

        // DT2 (0-3)
        public void setDT2(int dt2) {
            detune2 = dt2 & 3;
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

        public void setPhaseReset(int prst) {
            phaseReset = (int) (prst != 0 ? 1 : 0);
            paramChanged = true;
        }

        // SSG-type Envelop (0-15)
        public void setSSGEC(int ssgec) {
            if ((ssgec & 8) != 0)
                ssgType = ssgec;
            else
                ssgType = 0;
        }

        public void setAMON(boolean amon) {
            amOn = amon;
            paramChanged = true;
        }

        public void setFB(int fb) {
            this.fb = Channel4.fbtable[fb];
            paramChanged = true;
        }

        public void setALGLink(int AlgLink) {
            algLink = (byte) AlgLink;
            paramChanged = true;
        }

        public void setWaveTypeL(byte wt) {
            this.wt = (byte) ((this.wt & 2) | (wt & 1));
            paramChanged = true;
        }

        public void setWaveTypeH(byte wt) {
            this.wt = (byte) ((this.wt & 1) | ((wt & 1) << 1));
            paramChanged = true;
        }

        public void mute(boolean mute) {
            this.mute = mute;
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

        // friends this.
        //private class Channel4;

        private void fm_nextPhase(Operator op) {
        }

        public static int[] dbgGetClTable() {
            return clTable;
        }

        public int[] dbgGetSineTable(int c, int t) {
            return sinetable[c][t];
        }
    }

    public static void waveReset(int waveCh, int wavetype) {
        double log2 = Math.log(2.0);
        for (int i = 0; i < FM_OPSINENTS / 2; i++) {
            double r = (i * 2 + 1) * Math.PI / FM_OPSINENTS;
            double q = -256 * Math.log(Math.sin(r)) / log2;
            int s = (int) ((int) (Math.floor(q + 0.5)) + 1);
            sinetable[waveCh][wavetype][i] = s * 2;
            sinetable[waveCh][wavetype][FM_OPSINENTS / 2 + i] = s * 2 + 1;
        }
    }

    // this.
    // 4-Op Channel
    //
    public static class Channel4 {
        public static final byte[] fbtable = new byte[] {31, 7, 6, 5, 4, 3, 2, 1};

        private final boolean tableHasMade = false;
        private static final int[] kftable = new int[64];
        private int fb;
        private int[] buf = new int[4];
        private int[] in = new int[3];          // 各 OP の入力ポインタ
        private int[] out = new int[3];         // 各 OP の出力ポインタ
        private int[] pms;
        private int algo;
        private Chip chip;
        private boolean ac = false;
        private byte carrier;
        private int[] oAlg = new int[] {2, 1, 3, 0};//オペレータの計算順序
        private int ch = 0;

        public Operator[] op = new Operator[] {
                new Operator(), new Operator(), new Operator(), new Operator()
        };

        public Channel4(int ch) {
            if (!tableHasMade)
                makeTable();

            setAlgorithm(0);
            pms = pmTable[0][0];
            this.ch = ch;
        }

        public void makeTable() {
            // 100/64 cent =  2^(i*100/64*1200)
            for (int i = 0; i < 64; i++) {
                kftable[i] = (int) (0x10000 * Math.pow(2.0, i / 768.0));
            }
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
            int key = op[0].isOn() | op[1].isOn() | op[2].isOn() | op[3].isOn() ? 1 : 0;
            int lfo = (op[0].ms & ((op[0].amOn | op[1].amOn | op[2].amOn | op[3].amOn) ? 0x37 : 7)) != 0 ? 2 : 0;
            return key | lfo;
        }

        // F-Number/BLOCK を設定

        public void setFNum(int f) {
            for (int i = 0; i < 4; i++)
                op[i].setFNum(f);
        }

        static final int[] kcTable = new int[] {
                5197, 5506, 5833, 6180, 6180, 6547, 6937, 7349,
                7349, 7786, 8249, 8740, 8740, 9259, 9810, 10394,
        };

        // KC/KF を設定

        public void setKCKF(int kc, int kf) {
            int oct = 19 - ((kc >> 4) & 7);

            //System.err.printf("%p", this);
            int kcv = kcTable[kc & 0x0f];
            kcv = (kcv + 2) / 4 * 4;
            //System.err.printf(" %.4x", kcv);
            int dp = (int) (kcv * kftable[kf & 0x3f]);
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

        static final byte[][] table1 = new byte[][] {
                new byte[] {0, 1, 1, 2, 2, 3},
                new byte[] {1, 0, 0, 1, 1, 2},
                new byte[] {1, 1, 1, 0, 0, 2},
                new byte[] {0, 1, 2, 1, 1, 2},
                new byte[] {0, 1, 2, 2, 2, 1},
                new byte[] {0, 1, 0, 1, 0, 1},
                new byte[] {0, 1, 2, 1, 2, 1},
                new byte[] {1, 0, 1, 0, 1, 0}
        };

        // アルゴリズムを設定
        public void setAlgorithm(int algo) {
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

            if (!ac) {
                switch (algo) {
                case 0:
                    op[2].calc(ch, op[1].out());
                    op[1].calc(ch, op[0].out());
                    r = op[3].calc(ch, op[2].out());
                    op[0].calcFB(ch, fb);
                    break;
                case 1:
                    op[2].calc(ch, op[0].out() + op[1].out());
                    op[1].calc(ch, 0);
                    r = op[3].calc(ch, op[2].out());
                    op[0].calcFB(ch, fb);
                    break;
                case 2:
                    op[2].calc(ch, op[1].out());
                    op[1].calc(ch, 0);
                    r = op[3].calc(ch, op[0].out() + op[2].out());
                    op[0].calcFB(ch, fb);
                    break;
                case 3:
                    op[2].calc(ch, 0);
                    op[1].calc(ch, op[0].out());
                    r = op[3].calc(ch, op[1].out() + op[2].out());
                    op[0].calcFB(ch, fb);
                    break;
                case 4:
                    op[2].calc(ch, 0);
                    r = op[1].calc(ch, op[0].out());
                    r += op[3].calc(ch, op[2].out());
                    op[0].calcFB(ch, fb);
                    break;
                case 5:
                    r = op[2].calc(ch, op[0].out());
                    r += op[1].calc(ch, op[0].out());
                    r += op[3].calc(ch, op[0].out());
                    op[0].calcFB(ch, fb);
                    break;
                case 6:
                    r = op[2].calc(ch, 0);
                    r += op[1].calc(ch, op[0].out());
                    r += op[3].calc(ch, 0);
                    op[0].calcFB(ch, fb);
                    break;
                case 7:
                    r = op[2].calc(ch, 0);
                    r += op[1].calc(ch, 0);
                    r += op[3].calc(ch, 0);
                    r += op[0].calcFB(ch, fb);
                    break;
                }
            } else {
                for (int n : oAlg) {
                    int rn;
                    if (n == 0) {
                        rn = op[n].calcFB(ch, fb);
                        r += ((carrier & 0x1) != 0) ? rn : 0;
                        continue;
                    }
                    int v = 0;
                    v += ((op[n].algLink & 0x1) != 0) ? op[0].out() : 0;
                    v += ((op[n].algLink & 0x2) != 0) ? op[1].out() : 0;
                    v += ((op[n].algLink & 0x4) != 0) ? op[2].out() : 0;
                    v += ((op[n].algLink & 0x8) != 0) ? op[3].out() : 0;

                    rn = op[n].calc(ch, v);
                    r += ((carrier & (0x1 << n)) != 0) ? rn : 0;
                }
            }

            return r;
        }

        //  合成
        public int calcL() {
            chip.setPMV(pms[chip.getPmL()]);

            int r = 0;
            if (!ac) {
                switch (algo) {
                case 0:
                    op[2].calcL(ch, op[1].out());
                    op[1].calcL(ch, op[0].out());
                    r = op[3].calcL(ch, op[2].out());
                    op[0].calcFBL(ch, fb);
                    break;
                case 1:
                    op[2].calcL(ch, op[0].out() + op[1].out());
                    op[1].calcL(ch, 0);
                    r = op[3].calcL(ch, op[2].out());
                    op[0].calcFBL(ch, fb);
                    break;
                case 2:
                    op[2].calcL(ch, op[1].out());
                    op[1].calcL(ch, 0);
                    r = op[3].calcL(ch, op[0].out() + op[2].out());
                    op[0].calcFBL(ch, fb);
                    break;
                case 3:
                    op[2].calcL(ch, 0);
                    op[1].calcL(ch, op[0].out());
                    r = op[3].calcL(ch, op[1].out() + op[2].out());
                    op[0].calcFBL(ch, fb);
                    break;
                case 4:
                    op[2].calcL(ch, 0);
                    r = op[1].calcL(ch, op[0].out());
                    r += op[3].calcL(ch, op[2].out());
                    op[0].calcFBL(ch, fb);
                    break;
                case 5:
                    r = op[2].calcL(ch, op[0].out());
                    r += op[1].calcL(ch, op[0].out());
                    r += op[3].calcL(ch, op[0].out());
                    op[0].calcFBL(ch, fb);
                    break;
                case 6:
                    r = op[2].calcL(ch, 0);
                    r += op[1].calcL(ch, op[0].out());
                    r += op[3].calcL(ch, 0);
                    op[0].calcFBL(ch, fb);
                    break;
                case 7:
                    r = op[2].calcL(ch, 0);
                    r += op[1].calcL(ch, 0);
                    r += op[3].calcL(ch, 0);
                    r += op[0].calcFBL(ch, fb);
                    break;
                }
            } else {
                for (int n : oAlg) {
                    int rn;
                    if (n == 0) {
                        rn = op[n].calcFBL(ch, fb);
                        r += ((carrier & 0x1) != 0) ? rn : 0;
                        continue;
                    }
                    int v = 0;
                    v += ((op[n].algLink & 0x1) != 0) ? op[0].out() : 0;
                    v += ((op[n].algLink & 0x2) != 0) ? op[1].out() : 0;
                    v += ((op[n].algLink & 0x4) != 0) ? op[2].out() : 0;
                    v += ((op[n].algLink & 0x8) != 0) ? op[3].out() : 0;

                    rn = op[n].calcL(ch, v);
                    r += ((carrier & (0x1 << n)) != 0) ? rn : 0;
                }
            }

            return r;
        }

        //  合成
        public int calcN(int noise) {
            buf[1] = buf[2] = buf[3] = 0;

            buf[0] = op[0].out;
            op[0].calcFB(ch, fb);
            out[0] += op[1].calc(ch, buf[in[0]]);
            out[1] += op[2].calc(ch, buf[in[1]]);
            int o = op[3].out;
            op[3].calcN(noise);
            return buf[out[2]] + o;
        }

        //  合成
        public int calcLN(int noise) {
            chip.setPMV(pms[chip.getPmL()]);
            buf[1] = buf[2] = buf[3] = 0;

            buf[0] = op[0].out;
            op[0].calcFBL(ch, fb);
            out[0] += op[1].calcL(ch, buf[in[0]]);
            out[1] += op[2].calcL(ch, buf[in[1]]);
            int o = op[3].out;
            op[3].calcN(noise);
            return buf[out[2]] + o;
        }

        // オペレータの種類 (LFO) を設定
        public void setType(OpType type) {
            for (int i = 0; i < 4; i++)
                op[i].type = type;
        }

        // セルフ・フィードバックレートの設定 (0-7)
        public void setFB(int feedback) {
            fb = fbtable[feedback];
        }


        public void setAC(boolean sw) {
            ac = sw;
            buildAlg();
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

        public void buildAlg() {
            byte mask = 0xf;
            byte omask = 0;
            boolean[] use = new boolean[] {false, false, false, false};

            carrier = 0xf;

            for (int j = 0; j < 4; j++) {
                omask = (byte) (~mask);
                for (int i = 0; i < 4; i++) {
                    if (!use[i] && (((op[i].algLink & mask) == 0) || i == 0)) {
                        use[i] = true;
                        //oAlg.add(i);
                        omask |= (byte) (1 << i);
                        carrier &= (byte) (~op[i].algLink);
                    }
                }
                mask = (byte) (~omask);
            }

            if (carrier != 0xf) {
                for (int i = 0; i < 4; i++)
                    System.err.printf("algLink:%d:%d", i, op[i].algLink);
                System.err.printf("carrier:%d", carrier);
            }
        }
    }
}
