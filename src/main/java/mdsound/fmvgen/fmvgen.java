﻿package mdsound.fmvgen;

import mdsound.fmgen.fmgen;
import mdsound.fmgen.fmgen.Chip;
import mdsound.fmgen.fmgen.Operator.EGPhase;

    public class fmvgen extends fmgen
    {
        public enum OpType
        {
            typeN,
            typeM
        };

        public static int[][][] sinetable = new int[][][]{
            new int[][] { new int[1024], new int[1024], new int[1024], new int[1024] },
            new int[][] { new int[1024], new int[1024], new int[1024], new int[1024] },
            new int[][] { new int[1024], new int[1024], new int[1024], new int[1024] },
            new int[][] { new int[1024], new int[1024], new int[1024], new int[1024] },
            new int[][] { new int[1024], new int[1024], new int[1024], new int[1024] },
            new int[][] { new int[1024], new int[1024], new int[1024], new int[1024] },
            new int[][] { new int[1024], new int[1024], new int[1024], new int[1024] },
            new int[][] { new int[1024], new int[1024], new int[1024], new int[1024] },
            new int[][] { new int[1024], new int[1024], new int[1024], new int[1024] },
            new int[][] { new int[1024], new int[1024], new int[1024], new int[1024] },
            new int[][] { new int[1024], new int[1024], new int[1024], new int[1024] },
            new int[][] { new int[1024], new int[1024], new int[1024], new int[1024] }
        };

        // Operator -------------------------------------------------------------
        public static class Operator
        {
            public static byte[] notetable = new byte[] {
            0,  0,  0,  0,  0,  0,  0,  1,  2,  3,  3,  3,  3,  3,  3,  3,
            4,  4,  4,  4,  4,  4,  4,  5,  6,  7,  7,  7,  7,  7,  7,  7,
            8,  8,  8,  8,  8,  8,  8,  9, 10, 11, 11, 11, 11, 11, 11, 11,
            12, 12, 12, 12, 12, 12, 12, 13, 14, 15, 15, 15, 15, 15, 15, 15,
            16, 16, 16, 16, 16, 16, 16, 17, 18, 19, 19, 19, 19, 19, 19, 19,
            20, 20, 20, 20, 20, 20, 20, 21, 22, 23, 23, 23, 23, 23, 23, 23,
            24, 24, 24, 24, 24, 24, 24, 25, 26, 27, 27, 27, 27, 27, 27, 27,
            28, 28, 28, 28, 28, 28, 28, 29, 30, 31, 31, 31, 31, 31, 31, 31,
            };

            public static byte[] dttable = new byte[]
            {
            0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
            0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
            0,  0,  0,  0,  2,  2,  2,  2,  2,  2,  2,  2,  4,  4,  4,  4,
            4,  6,  6,  6,  8,  8,  8, 10, 10, 12, 12, 14, 16, 16, 16, 16,
            2,  2,  2,  2,  4,  4,  4,  4,  4,  6,  6,  6,  8,  8,  8, 10,
            10, 12, 12, 14, 16, 16, 18, 20, 22, 24, 26, 28, 32, 32, 32, 32,
            4,  4,  4,  4,  4,  6,  6,  6,  8,  8,  8, 10, 10, 12, 12, 14,
            16, 16, 18, 20, 22, 24, 26, 28, 32, 34, 38, 40, 44, 44, 44, 44,
            0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
            0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
            0,  0,  0,  0, -2, -2, -2, -2, -2, -2, -2, -2, -4, -4, -4, -4,
            -4, -6, -6, -6, -8, -8, -8,-10,-10,-12,-12,-14,-16,-16,-16,-16,
            -2, -2, -2, -2, -4, -4, -4, -4, -4, -6, -6, -6, -8, -8, -8,-10,
            -10,-12,-12,-14,-16,-16,-18,-20,-22,-24,-26,-28,-32,-32,-32,-32,
            -4, -4, -4, -4, -4, -6, -6, -6, -8, -8, -8,-10,-10,-12,-12,-14,
            -16,-16,-18,-20,-22,-24,-26,-28,-32,-34,-38,-40,-44,-44,-44,-44,
            };

            public static byte[][] decaytable1 = new byte[][]
            {
            new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 }, new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 },
            new byte[] {1, 1, 1, 1, 1, 1, 1, 1 },new byte[] {     1, 1, 1, 1, 1, 1, 1, 1},
            new byte[] {1, 1, 1, 1, 1, 1, 1, 1 },new byte[] {     1, 1, 1, 1, 1, 1, 1, 1},
            new byte[] {1, 1, 1, 0, 1, 1, 1, 0 },new byte[] {     1, 1, 1, 0, 1, 1, 1, 0},
            new byte[] {1, 0, 1, 0, 1, 0, 1, 0 },new byte[] {     1, 1, 1, 0, 1, 0, 1, 0},
            new byte[] {1, 1, 1, 0, 1, 1, 1, 0 },new byte[] {     1, 1, 1, 1, 1, 1, 1, 0},
            new byte[] {1, 0, 1, 0, 1, 0, 1, 0 },new byte[] {     1, 1, 1, 0, 1, 0, 1, 0},
            new byte[] {1, 1, 1, 0, 1, 1, 1, 0 },new byte[] {     1, 1, 1, 1, 1, 1, 1, 0},
            new byte[] {1, 0, 1, 0, 1, 0, 1, 0 },new byte[] {     1, 1, 1, 0, 1, 0, 1, 0},
            new byte[] {1, 1, 1, 0, 1, 1, 1, 0 },new byte[] {     1, 1, 1, 1, 1, 1, 1, 0},
            new byte[] {1, 0, 1, 0, 1, 0, 1, 0 },new byte[] {     1, 1, 1, 0, 1, 0, 1, 0},
            new byte[] {1, 1, 1, 0, 1, 1, 1, 0 },new byte[] {     1, 1, 1, 1, 1, 1, 1, 0},
            new byte[] {1, 0, 1, 0, 1, 0, 1, 0 },new byte[] {     1, 1, 1, 0, 1, 0, 1, 0},
            new byte[] {1, 1, 1, 0, 1, 1, 1, 0 },new byte[] {     1, 1, 1, 1, 1, 1, 1, 0},
            new byte[] {1, 0, 1, 0, 1, 0, 1, 0 },new byte[] {     1, 1, 1, 0, 1, 0, 1, 0},
            new byte[] {1, 1, 1, 0, 1, 1, 1, 0 },new byte[] {     1, 1, 1, 1, 1, 1, 1, 0},
            new byte[] {1, 0, 1, 0, 1, 0, 1, 0 },new byte[] {     1, 1, 1, 0, 1, 0, 1, 0},
            new byte[] {1, 1, 1, 0, 1, 1, 1, 0 },new byte[] {     1, 1, 1, 1, 1, 1, 1, 0},
            new byte[] {1, 0, 1, 0, 1, 0, 1, 0 },new byte[] {     1, 1, 1, 0, 1, 0, 1, 0},
            new byte[] {1, 1, 1, 0, 1, 1, 1, 0 },new byte[] {     1, 1, 1, 1, 1, 1, 1, 0},
            new byte[] {1, 0, 1, 0, 1, 0, 1, 0 },new byte[] {     1, 1, 1, 0, 1, 0, 1, 0},
            new byte[] {1, 1, 1, 0, 1, 1, 1, 0 },new byte[] {     1, 1, 1, 1, 1, 1, 1, 0},
            new byte[] {1, 0, 1, 0, 1, 0, 1, 0 },new byte[] {     1, 1, 1, 0, 1, 0, 1, 0},
            new byte[] {1, 1, 1, 0, 1, 1, 1, 0 },new byte[] {     1, 1, 1, 1, 1, 1, 1, 0},
            new byte[] {1, 1, 1, 1, 1, 1, 1, 1 },new byte[] {     2, 1, 1, 1, 2, 1, 1, 1},
            new byte[] {2, 1, 2, 1, 2, 1, 2, 1 },new byte[] {     2, 2, 2, 1, 2, 2, 2, 1},
            new byte[] {2, 2, 2, 2, 2, 2, 2, 2 },new byte[] {     4, 2, 2, 2, 4, 2, 2, 2},
            new byte[] {4, 2, 4, 2, 4, 2, 4, 2 },new byte[] {     4, 4, 4, 2, 4, 4, 4, 2},
            new byte[] {4, 4, 4, 4, 4, 4, 4, 4 },new byte[] {     8, 4, 4, 4, 8, 4, 4, 4},
            new byte[] {8, 4, 8, 4, 8, 4, 8, 4 },new byte[] {     8, 8, 8, 4, 8, 8, 8, 4},
            new byte[] {16,16,16,16,16,16,16,16 },new byte[] {    16,16,16,16,16,16,16,16},
            new byte[] {16,16,16,16,16,16,16,16 },new byte[] {    16,16,16,16,16,16,16,16}
            };

            public static int[] decaytable2 = new int[]
            {
                1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2047, 2047, 2047, 2047, 2047
            };

            public static byte[][] attacktable = new byte[][]
            {
            new byte[] {  -1,-1,-1,-1,-1,-1,-1,-1},new byte[] { -1,-1,-1,-1,-1,-1,-1,-1},
            new byte[] {   4, 4, 4, 4, 4, 4, 4, 4},new byte[] {  4, 4, 4, 4, 4, 4, 4, 4},
            new byte[] {   4, 4, 4, 4, 4, 4, 4, 4},new byte[] {  4, 4, 4, 4, 4, 4, 4, 4},
            new byte[] {   4, 4, 4,-1, 4, 4, 4,-1},new byte[] {  4, 4, 4,-1, 4, 4, 4,-1},
            new byte[] {   4,-1, 4,-1, 4,-1, 4,-1},new byte[] {  4, 4, 4,-1, 4,-1, 4,-1},
            new byte[] {   4, 4, 4,-1, 4, 4, 4,-1},new byte[] {  4, 4, 4, 4, 4, 4, 4,-1},
            new byte[] {   4,-1, 4,-1, 4,-1, 4,-1},new byte[] {  4, 4, 4,-1, 4,-1, 4,-1},
            new byte[] {   4, 4, 4,-1, 4, 4, 4,-1},new byte[] {  4, 4, 4, 4, 4, 4, 4,-1},
            new byte[] {   4,-1, 4,-1, 4,-1, 4,-1},new byte[] {  4, 4, 4,-1, 4,-1, 4,-1},
            new byte[] {   4, 4, 4,-1, 4, 4, 4,-1},new byte[] {  4, 4, 4, 4, 4, 4, 4,-1},
            new byte[] {   4,-1, 4,-1, 4,-1, 4,-1},new byte[] {  4, 4, 4,-1, 4,-1, 4,-1},
            new byte[] {   4, 4, 4,-1, 4, 4, 4,-1},new byte[] {  4, 4, 4, 4, 4, 4, 4,-1},
            new byte[] {   4,-1, 4,-1, 4,-1, 4,-1},new byte[] {  4, 4, 4,-1, 4,-1, 4,-1},
            new byte[] {   4, 4, 4,-1, 4, 4, 4,-1},new byte[] {  4, 4, 4, 4, 4, 4, 4,-1},
            new byte[] {   4,-1, 4,-1, 4,-1, 4,-1},new byte[] {  4, 4, 4,-1, 4,-1, 4,-1},
            new byte[] {   4, 4, 4,-1, 4, 4, 4,-1},new byte[] {  4, 4, 4, 4, 4, 4, 4,-1},
            new byte[] {   4,-1, 4,-1, 4,-1, 4,-1},new byte[] {  4, 4, 4,-1, 4,-1, 4,-1},
            new byte[] {   4, 4, 4,-1, 4, 4, 4,-1},new byte[] {  4, 4, 4, 4, 4, 4, 4,-1},
            new byte[] {   4,-1, 4,-1, 4,-1, 4,-1},new byte[] {  4, 4, 4,-1, 4,-1, 4,-1},
            new byte[] {   4, 4, 4,-1, 4, 4, 4,-1},new byte[] {  4, 4, 4, 4, 4, 4, 4,-1},
            new byte[] {   4,-1, 4,-1, 4,-1, 4,-1},new byte[] {  4, 4, 4,-1, 4,-1, 4,-1},
            new byte[] {   4, 4, 4,-1, 4, 4, 4,-1},new byte[] {  4, 4, 4, 4, 4, 4, 4,-1},
            new byte[] {   4,-1, 4,-1, 4,-1, 4,-1},new byte[] {  4, 4, 4,-1, 4,-1, 4,-1},
            new byte[] {   4, 4, 4,-1, 4, 4, 4,-1},new byte[] {  4, 4, 4, 4, 4, 4, 4,-1},
            new byte[] {   4, 4, 4, 4, 4, 4, 4, 4},new byte[] {  3, 4, 4, 4, 3, 4, 4, 4},
            new byte[] {   3, 4, 3, 4, 3, 4, 3, 4},new byte[] {  3, 3, 3, 4, 3, 3, 3, 4},
            new byte[] {   3, 3, 3, 3, 3, 3, 3, 3},new byte[] {  2, 3, 3, 3, 2, 3, 3, 3},
            new byte[] {   2, 3, 2, 3, 2, 3, 2, 3},new byte[] {  2, 2, 2, 3, 2, 2, 2, 3},
            new byte[] {   2, 2, 2, 2, 2, 2, 2, 2},new byte[] {  1, 2, 2, 2, 1, 2, 2, 2},
            new byte[] {   1, 2, 1, 2, 1, 2, 1, 2},new byte[] {  1, 1, 1, 2, 1, 1, 1, 2},
            new byte[] {   0, 0, 0, 0, 0, 0, 0, 0},new byte[] {  0, 0 ,0, 0, 0, 0, 0, 0},
            new byte[] {   0, 0, 0, 0, 0, 0, 0, 0},new byte[] {  0, 0 ,0, 0, 0, 0, 0, 0}
            };

            public static int[][][][] ssgenvtable = new int[][][][] {
                new int[][][] {
                    new int[][] { new int[] { 1,  1 },new int[] {  1,  1 },new int[] {  1,  1 } },      // 08
                    new int[][] { new int[] { 0,  1 },new int[] {  1,  1 },new int[] {  1,  1 } }      // 08 56~
                },
                new int[][][] {
                    new int[][] { new int[] { 0,  1 },new int[] {  2,  0 },new int[] {  2,  0 } },      // 09
                    new int[][] { new int[] { 0,  1 },new int[] {  2,  0 },new int[] {  2,  0 } }      // 09
                },
                new int[][][] {
                    new int[][] { new int[] { 1, -1 },new int[] {  0,  1 },new int[] {  1, -1 } },      // 10
                    new int[][] { new int[] { 0,  1 },new int[] {  1, -1 },new int[] {  0,  1 } }      // 10 60~
                },
                new int[][][] {
                    new int[][] { new int[] { 1, -1 },new int[] {  0,  0 },new int[] {  0,  0 } },      // 11
                    new int[][] { new int[] { 0,  1 },new int[] {  0,  0 },new int[] {  0,  0 } }      // 11 60~
                },
                new int[][][] {
                    new int[][] { new int[] { 2, -1 },new int[] {  2, -1 },new int[] {  2, -1 } },      // 12
                    new int[][] { new int[] { 1, -1 },new int[] {  2, -1 },new int[] {  2, -1 } }      // 12 56~
                },
                new int[][][] {
                    new int[][] { new int[] { 1, -1 },new int[] {  0,  0 },new int[] {  0,  0 } },      // 13
                    new int[][] { new int[] { 1, -1 },new int[] {  0,  0 },new int[] {  0,  0 } }      // 13
                },
                new int[][][] {
                    new int[][] { new int[] { 0,  1 },new int[] {  1, -1 },new int[] {  0,  1 } },      // 14
                    new int[][] { new int[] { 1, -1 },new int[] {  0,  1 },new int[] {  1, -1 } }      // 14 60~
                },
                new int[][][] {
                    new int[][] { new int[] { 0,  1 },new int[] {  2,  0 },new int[] {  2,  0 } },      // 15
                    new int[][] { new int[] { 1, -1 },new int[] {  2,  0 },new int[] {  2,  0 } }      // 15 60~
                }
            };

            // ---------------------------------------------------------------------------
            // Operator
            //
            boolean tablehasmade = false;
            //int[] sinetable = new int[1024];
            static int[] cltable = new int[FM_CLENTS];

            public OpType type_;       // OP の種類 (M, N...)
            private int bn_;       // Block/Note
            private int eg_level_;  // EG の出力値
            private int eg_level_on_next_phase_;    // 次の eg_phase_ に移る値
            private int eg_count_;      // EG の次の変移までの時間
            private int eg_count_diff_; // eg_count_ の差分
            private int eg_out_;        // EG+TL を合わせた出力値
            private int tl_out_;        // TL 分の出力値
                                        //private boolean tl_out_;        // TL 分の出力値
                                        //  int  pm_depth_;  // PM depth
                                        //  int  am_depth_;  // AM depth
            private int eg_rate_;
            private int eg_curve_count_;
            private int ssg_offset_;
            private int ssg_vector_;
            private int ssg_phase_;


            private int key_scale_rate_;       // key scale rate
            private EGPhase eg_phase_;
            //private (int)* ams_;
            private int[] ams_;
            public int ms_;

            private int tl_;           // Total Level  (0-127)
            private int tl_latch_;     // Total Level Latch (for CSM mode)
            private int ar_;           // Attack Rate   (0-63)
            private int dr_;           // Decay Rate    (0-63)
            private int sr_;           // Sustain Rate  (0-63)
            private int sl_;           // Sustain Level (0-127)
            private int rr_;           // Release Rate  (0-63)
            private int ks_;           // Keyscale      (0-3)
            private int ssg_type_; // SSG-Type Envelop Control
            private int phaseReset_;   // phaseReset(0/1)

            public int fb_;
            public byte algLink_;
            public byte wt_;

            private boolean keyon_;
            public boolean amon_;     // enable Amplitude Modulation
            public boolean param_changed_;    // パラメータが更新された
            private boolean mute_;

            // １サンプル合成

            // ISample を envelop count (2π) に変換するシフト量
            public final int IS2EC_SHIFT = ((20 + FM_PGBITS) - 13);

            //typedef (int)32 Counter;

            private Chip chip_;
            public int out_, out2_;
            private int in2_;

            // Phase Generator ------------------------------------------------------
            private int dp_;       // ΔP
            private int detune_;       // Detune
            private int detune2_;  // DT2
            private int multiple_; // Multiple
            private int pg_count_;   // Phase 現在値
            private int pg_diff_;    // Phase 差分値
            private int pg_diff_lfo_; // Phase 差分値 >> x

            // Envelop Generator ---------------------------------------------------
            public enum EGPhase { next, attack, decay, sustain, release, off;
                EGPhase next() {
                    return values()[ordinal() + (ordinal() < off.ordinal() ? 1 : 0)];
                }
            };

            // Tables ---------------------------------------------------------------
            private static int[] rate_table = new int[16];
            private static int[][] multable = new int[][] { new int[16], new int[16], new int[16], new int[16] };

            public int dbgopout_;
            public int dbgpgout_;


            // 構築
            //FM::Operator::Operator()
            //: chip_(0)
            public Operator()
            {
                if (!tablehasmade)
                    MakeTable();

                // EG Part
                ar_ = dr_ = sr_ = rr_ = key_scale_rate_ = 0;
                ams_ = amtable[0][0];
                mute_ = false;
                keyon_ = false;
                //tl_out_ = false;
                tl_out_ = 0;
                ssg_type_ = 0;

                // PG Part
                multiple_ = 0;
                detune_ = 0;
                detune2_ = 0;

                // LFO
                ms_ = 0;

                // Reset();
            }

            // 初期化
            public void Reset()
            {
                // EG part
                tl_ = tl_latch_ = 127;
                ShiftPhase(EGPhase.off);
                eg_count_ = 0;
                eg_curve_count_ = 0;
                ssg_phase_ = 0;

                // PG part
                pg_count_ = 0;

                // OP part
                out_ = out2_ = 0;

                param_changed_ = true;
                //PARAMCHANGE(0);
            }

            public void MakeTable()
            {
                // 対数テーブルの作成
                //assert(FM_CLENTS >= 256);

                int p = 0;
                int i;
                for (i = 0; i < 256; i++)
                {
                    int v = (int)(Math.floor(Math.pow(2.0, 13.0 - i / 256.0)));
                    v = (v + 2) & ~3;
                    cltable[p++] = v;
                    cltable[p++] = -v;
                }
                while (p < FM_CLENTS)
                {
                    cltable[p] = cltable[p - 512] / 2;
                    //System.err.printf("{0}:", cltable[p]);
                    p++;
                }

                // for (i=0; i<13*256; i++)
                //  printf("%4d, %d, %d\n", i, cltable[i*2], cltable[i*2+1]);

                // サインテーブルの作成
                //double log2 = Math.log(2.0);
                //for (i = 0; i < FM_OPSINENTS / 2; i++)
                //{
                //double r = (i * 2 + 1) * FM_PI / FM_OPSINENTS;
                //double q = -256 * Math.log(Math.sin(r)) / log2;
                //int s = (int)((int)(Math.floor(q + 0.5)) + 1);
                //System.err.printf("{0}, {1}", s, cltable[s * 2] / 8);
                //System.err.printf("{0:d6} , {1:d6} , {2:d6} , {3:X4} , {4:X4}"
                //    , s
                //    , cltable[s * 2]
                //    , ((s * 2) % 2 == 0 ? 1 : -1) * (4095 - Math.abs((s * 2) / 2))
                //    , ((s * 2) % 2 == 0 ? 1 : -1) * (4095 - Math.abs((s * 2) / 2))
                //    , ((s * 2 + 1 ) % 2 == 0 ? 1 : -1) * (4095 - Math.abs((s * 2+1) / 2))
                //    );
                for (int j = 0; j < 12; j++)
                {
                    fmvgen.waveReset(j, 0);
                    fmvgen.waveReset(j, 1);
                    fmvgen.waveReset(j, 2);
                    fmvgen.waveReset(j, 3);
                }
                //}

                fmvgen.MakeLFOTable();

                tablehasmade = true;
            }


            public void SetDPBN(int dp, int bn)
            {
                dp_ = dp;
                bn_ = bn;
                param_changed_ = true;
                //PARAMCHANGE(1);
            }

            // 準備

            public void Prepare()
            {
                if (param_changed_)
                {
                    param_changed_ = false;
                    // PG Part
                    pg_diff_ = (int)(dp_ + dttable[detune_ + bn_]) * chip_.GetMulValue(detune2_, multiple_);
                    pg_diff_lfo_ = (int)(pg_diff_ >> 11);

                    // EG Part
                    key_scale_rate_ = bn_ >> (int)(3 - ks_);
                    tl_out_ = (int)(mute_ ? 0x3ff : tl_ * 8);

                    switch (eg_phase_)
                    {
                        case attack:
                            SetEGRate(ar_ != 0 ? Math.min(63, ar_ + key_scale_rate_) : 0);
                            break;
                        case decay:
                            SetEGRate(dr_ != 0 ? Math.min(63, dr_ + key_scale_rate_) : 0);
                            eg_level_on_next_phase_ = (int)(sl_ * 8);
                            break;
                        case sustain:
                            SetEGRate(sr_ != 0 ? Math.min(63, sr_ + key_scale_rate_) : 0);
                            break;
                        case release:
                            SetEGRate(Math.min(63, rr_ + key_scale_rate_));
                            break;
                    }

                    // SSG-EG
                    if (ssg_type_ != 0 && (eg_phase_ != EGPhase.release))
                    {
                        int m = ar_ >= ((ssg_type_ == 8 || ssg_type_ == 12) ? 56 : 60) ? 1 : 0;

                        //assert(0 <= ssg_phase_ && ssg_phase_ <= 2);
                        int[] table = ssgenvtable[ssg_type_ & 7][m][ssg_phase_];

                        ssg_offset_ = table[0] * 0x200;
                        ssg_vector_ = table[1];
                    }
                    // LFO
                    ams_ = amtable[(int)type_.ordinal()][amon_ ? (ms_ >> 4) & 3 : 0];
                    EGUpdate();

                    dbgopout_ = 0;
                }
            }

            // envelop の eg_phase_ 変更

            public void ShiftPhase(EGPhase nextphase)
            {
                switch (nextphase)
                {
                    case attack:        // Attack Phase
                        tl_ = tl_latch_;
                        if (ssg_type_ != 0)
                        {
                            ssg_phase_ = ssg_phase_ + 1;
                            if (ssg_phase_ > 2)
                                ssg_phase_ = 1;

                            int m = ar_ >= ((ssg_type_ == 8 || ssg_type_ == 12) ? 56 : 60) ? 1 : 0;

                            //assert(0 <= ssg_phase_ && ssg_phase_ <= 2);
                            int[] table = ssgenvtable[ssg_type_ & 7][m][ssg_phase_];

                            ssg_offset_ = table[0] * 0x200;
                            ssg_vector_ = table[1];
                        }
                        if ((ar_ + key_scale_rate_) < 62)
                        {
                            SetEGRate(ar_ != 0 ? Math.min(63, ar_ + key_scale_rate_) : 0);
                            eg_phase_ = EGPhase.attack;
                            break;
                        }

                        if (sl_ != 0)
                        {
                            eg_level_ = 0;
                            eg_level_on_next_phase_ = (int)(ssg_type_ != 0 ? Math.min(sl_ * 8, 0x200) : sl_ * 8);

                            SetEGRate(dr_ != 0 ? Math.min(63, dr_ + key_scale_rate_) : 0);
                            eg_phase_ = EGPhase.decay;
                            break;
                        }

                        eg_level_ = (int)(sl_ * 8);
                        eg_level_on_next_phase_ = ssg_type_ != 0 ? 0x200 : 0x400;

                        SetEGRate(sr_ != 0 ? Math.min(63, sr_ + key_scale_rate_) : 0);
                        eg_phase_ = EGPhase.sustain;
                        break;
                    case decay:         // Decay Phase
                        if (sl_ != 0)
                        {
                            eg_level_ = 0;
                            eg_level_on_next_phase_ = (int)(ssg_type_ != 0 ? Math.min(sl_ * 8, 0x200) : sl_ * 8);

                            SetEGRate(dr_ != 0 ? Math.min(63, dr_ + key_scale_rate_) : 0);
                            eg_phase_ = EGPhase.decay;
                            break;
                        }

                        eg_level_ = (int)(sl_ * 8);
                        eg_level_on_next_phase_ = ssg_type_ != 0 ? 0x200 : 0x400;

                        SetEGRate(sr_ != 0 ? Math.min(63, sr_ + key_scale_rate_) : 0);
                        eg_phase_ = EGPhase.sustain;
                        break;
                    case sustain:       // Sustain Phase
                        eg_level_ = (int)(sl_ * 8);
                        eg_level_on_next_phase_ = ssg_type_ != 0 ? 0x200 : 0x400;

                        SetEGRate(sr_ != 0 ? Math.min(63, sr_ + key_scale_rate_) : 0);
                        eg_phase_ = EGPhase.sustain;
                        break;

                    case release:       // Release Phase
                        if (ssg_type_ != 0)
                        {
                            eg_level_ = eg_level_ * ssg_vector_ + ssg_offset_;
                            ssg_vector_ = 1;
                            ssg_offset_ = 0;
                        }
                        if (eg_phase_ == EGPhase.attack || (eg_level_ < FM_EG_BOTTOM)) //0x400/* && eg_phase_ != off*/))
                        {
                            eg_level_on_next_phase_ = 0x400;
                            SetEGRate(Math.min(63, rr_ + key_scale_rate_));
                            eg_phase_ = EGPhase.release;
                            break;
                        }

                        eg_level_ = FM_EG_BOTTOM;
                        eg_level_on_next_phase_ = FM_EG_BOTTOM;
                        EGUpdate();
                        SetEGRate(0);
                        eg_phase_ = EGPhase.off;
                        break;

                    case off:           // off
                    default:
                        eg_level_ = FM_EG_BOTTOM;
                        eg_level_on_next_phase_ = FM_EG_BOTTOM;
                        EGUpdate();
                        SetEGRate(0);
                        eg_phase_ = EGPhase.off;
                        break;
                }
            }

            // Block/F-Num

            public void SetFNum(int f)
            {
                dp_ = (f & 2047) << (int)((f >> 11) & 7);
                bn_ = notetable[(f >> 7) & 127];
                param_changed_ = true;
                //PARAMCHANGE(2);
            }

            // 入力: s = 20+FM_PGBITS = 29

            public int Sine(int c,int s)
            {
                return sinetable[c][wt_][((s) >> (20 + FM_PGBITS - FM_OPSINBITS)) & (FM_OPSINENTS - 1)];
            }


            public int SINE(int c,int s)
            {
                return (int)sinetable[c][wt_][(s) & (FM_OPSINENTS - 1)];
            }



            public int LogToLin(int a)
            {
                //#if 1 // FM_CLENTS < 0xc00  // 400 for TL, 400 for ENV, 400 for LFO.
                return (a < FM_CLENTS) ? cltable[a] : 0;
                //#else
                //return cltable[a];
                //#endif
            }


            public void EGUpdate()
            {
                if (ssg_type_ == 0)
                {
                    eg_out_ = Math.min(tl_out_ + eg_level_, 0x3ff) << (1 + 2);
                }
                else
                {
                    eg_out_ = Math.min(tl_out_ + eg_level_ * ssg_vector_ + ssg_offset_, 0x3ff) << (1 + 2);
                }
            }


            public void SetEGRate(int rate)
            {
                eg_rate_ = (int)rate;
                eg_count_diff_ = (int)(decaytable2[rate / 4] * chip_.GetRatio());
            }

            // EG 計算

            public void EGCalc()
            {
                eg_count_ = (2047 * 3) << FM_RATIOBITS;             // ##この手抜きは再現性を低下させる

                if (eg_phase_ == EGPhase.attack)
                {
                    int c = attacktable[eg_rate_][eg_curve_count_ & 7];
                    if (c >= 0)
                    {
                        eg_level_ -= 1 + (eg_level_ >> c);
                        if (eg_level_ <= 0)
                            ShiftPhase(EGPhase.decay);
                    }
                    EGUpdate();
                }
                else
                {
                    if (ssg_type_ == 0)
                    {
                        eg_level_ += decaytable1[eg_rate_][eg_curve_count_ & 7];
                        if (eg_level_ >= eg_level_on_next_phase_)
                            ShiftPhase(eg_phase_.next());
                        EGUpdate();
                    }
                    else
                    {
                        eg_level_ += 4 * decaytable1[eg_rate_][eg_curve_count_ & 7];
                        if (eg_level_ >= eg_level_on_next_phase_)
                        {
                            EGUpdate();
                            switch (eg_phase_)
                            {
                                case decay:
                                    ShiftPhase(EGPhase.sustain);
                                    break;
                                case sustain:
                                    ShiftPhase(EGPhase.attack);
                                    break;
                                case release:
                                    ShiftPhase(EGPhase.off);
                                    break;
                            }
                        }
                    }
                }
                eg_curve_count_++;
            }


            public void EGStep()
            {
                eg_count_ -= eg_count_diff_;

                // EG の変化は全スロットで同期しているという噂もある
                if (eg_count_ <= 0)
                    EGCalc();
            }

            // PG 計算
            // ret:2^(20+PGBITS) / cycle

            public int PGCalc()
            {
                int ret = pg_count_;
                pg_count_ += pg_diff_;
                dbgpgout_ = (int)ret;
                return ret;
            }


            public int PGCalcL()
            {
                int ret = pg_count_;
                pg_count_ += (int)(pg_diff_ + ((pg_diff_lfo_ * chip_.GetPMV()) >> 5));// & -(1 << (2+IS2EC_SHIFT)));
                dbgpgout_ = (int)ret;
                return ret /* + pmv * pg_diff_;*/;
            }

            // OP 計算
            // in: ISample (最大 8π)

            public int Calc(int ch,int In)
            {
                //EGStep();
                //out2_ = out_;

                //int pgin = (int)(PGCalc() >> (20 + FM_PGBITS - FM_OPSINBITS));
                ////pgin += In >> (20 + FM_PGBITS - FM_OPSINBITS - (2 + IS2EC_SHIFT));
                //if (fb_ < 31)
                //{
                //    pgin += ((In << (int)(1 + IS2EC_SHIFT)) >> (int)fb_) >> (20 + FM_PGBITS - FM_OPSINBITS);
                //}
                //else
                //{
                //    pgin += In >> (20 + FM_PGBITS - FM_OPSINBITS - (2 + IS2EC_SHIFT));
                //}
                //out_ = LogToLin((int)(eg_out_ + SINE(ch,pgin)));

                //dbgopout_ = out_;
                //return out_;

                EGStep();
                int In2 = In + out_ + out2_;
                out2_ = out_;

                int pgin = (int)(PGCalc() >> (20 + FM_PGBITS - FM_OPSINBITS));
                //pgin += In >> (20 + FM_PGBITS - FM_OPSINBITS - (2 + IS2EC_SHIFT));
                if (fb_ < 31)
                {
                    pgin += ((In2 << (int)(1 + IS2EC_SHIFT)) >> (int)fb_) >> (20 + FM_PGBITS - FM_OPSINBITS);
                    //System.err.printf("Calc:{0}", pgin);
                }
                else
                {
                    pgin += In >> (20 + FM_PGBITS - FM_OPSINBITS - (2 + IS2EC_SHIFT));
                }
                out_ = LogToLin((int)(eg_out_ + SINE(ch, pgin)));

                dbgopout_ = out2_;
                return out2_;
            }


            public int CalcL(int ch,int In)
            {
                //EGStep();

                //int pgin = (int)(PGCalcL() >> (20 + FM_PGBITS - FM_OPSINBITS));
                ////pgin += In >> (20 + FM_PGBITS - FM_OPSINBITS - (2 + IS2EC_SHIFT));
                //if (fb_ < 31)
                //{
                //    //                        17                                       19
                //    pgin += ((In << (int)(1 + IS2EC_SHIFT)) >> (int)fb_) >> (20 + FM_PGBITS - FM_OPSINBITS);
                //}
                //else
                //{
                //    //                                      1
                //    pgin += In >> (20 + FM_PGBITS - FM_OPSINBITS - (2 + IS2EC_SHIFT));
                //}
                //out_ = LogToLin((int)(eg_out_ + SINE(ch,pgin) + ams_[chip_.GetAML()]));

                //dbgopout_ = out_;
                //return out_;


                EGStep();
                int In2 = In + out_ + out2_;
                out2_ = out_;

                int pgin = (int)(PGCalcL() >> (20 + FM_PGBITS - FM_OPSINBITS));
                //pgin += In >> (20 + FM_PGBITS - FM_OPSINBITS - (2 + IS2EC_SHIFT));
                if (fb_ < 31)
                {
                    //                        17                                       19
                    pgin += ((In2 << (int)(1 + IS2EC_SHIFT)) >> (int)fb_) >> (20 + FM_PGBITS - FM_OPSINBITS);
                }
                else
                {
                    //                                      1
                    pgin += In >> (20 + FM_PGBITS - FM_OPSINBITS - (2 + IS2EC_SHIFT));
                }
                out_ = LogToLin((int)(eg_out_ + SINE(ch, pgin) + ams_[chip_.GetAML()]));

                dbgopout_ = out2_;
                return out2_;
            }


            public int CalcN(int noise)
            {
                EGStep();

                int lv = Math.max(0, 0x3ff - (tl_out_ + eg_level_)) << 1;

                // noise & 1 ? lv : -lv と等価
                noise = (noise & 1) - 1;
                out_ = (int)((lv + noise) ^ noise);

                dbgopout_ = out_;
                return out_;
            }

            // OP (FB) 計算
            // Self Feedback の変調最大 = 4π

            public int CalcFB(int ch,int fb)
            {
                EGStep();

                int In = out_ + out2_;
                out2_ = out_;

                int pgin = (int)(PGCalc() >> (20 + FM_PGBITS - FM_OPSINBITS));
                if (fb < 31)
                {
                    pgin += ((In << (int)(1 + IS2EC_SHIFT)) >> (int)fb) >> (20 + FM_PGBITS - FM_OPSINBITS);
                    //System.err.printf("CalcFB:{0}", pgin);
                }
                out_ = LogToLin((int)(eg_out_ + SINE(ch,pgin)));
                dbgopout_ = out2_;

                return out2_;
            }


            public int CalcFBL(int ch,int fb)
            {
                EGStep();

                int In = out_ + out2_;
                out2_ = out_;

                int pgin = (int)(PGCalcL() >> (20 + FM_PGBITS - FM_OPSINBITS));
                if (fb < 31)
                {
                    pgin += ((In << (int)(1 + IS2EC_SHIFT)) >> (int)fb) >> (20 + FM_PGBITS - FM_OPSINBITS);
                }

                out_ = LogToLin((int)(eg_out_ + SINE(ch,pgin) + ams_[chip_.GetAML()]));
                dbgopout_ = out_;

                return out_;
            }


            public void ResetFB()
            {
                out_ = out2_ = 0;
            }

            // キーオン

            public void KeyOn()
            {
                if (keyon_) return;

                keyon_ = true;

                if (phaseReset_ != 0)
                {
                    //位相リセットスイッチ有効

                    ShiftPhase(EGPhase.off);
                    ssg_phase_ = -1;
                    ShiftPhase(EGPhase.attack);
                    EGUpdate();
                    in2_ = out_ = out2_ = 0;

                    pg_count_ = 0;

                    return;
                }

                //位相リセットスイッチ無効

                if (eg_phase_ == EGPhase.off || eg_phase_ == EGPhase.release)
                {
                    ssg_phase_ = -1;
                    ShiftPhase(EGPhase.attack);
                    EGUpdate();
                    in2_ = out_ = out2_ = 0;

                    pg_count_ = 0;
                }

            }

            // キーオフ

            public void KeyOff()
            {
                if (keyon_)
                {
                    keyon_ = false;
                    ShiftPhase(EGPhase.release);
                }
            }

            // オペレータは稼働中か？

            public boolean IsOn()
            {
                return eg_phase_ != EGPhase.off;
            }

            // Detune (0-7)

            public void SetDT(int dt)
            {
                detune_ = dt * 0x20;
                param_changed_ = true;
                //PARAMCHANGE(4);
            }

            // DT2 (0-3)

            public void SetDT2(int dt2)
            {
                detune2_ = dt2 & 3;
                param_changed_ = true;
                //PARAMCHANGE(5);
            }

            // Multiple (0-15)

            public void SetMULTI(int mul)
            {
                multiple_ = mul;
                param_changed_ = true;
                //PARAMCHANGE(6);
            }

            // Total Level (0-127) (0.75dB step)

            public void SetTL(int tl, boolean csm)
            {
                if (!csm)
                {
                    tl_ = tl;
                    param_changed_ = true;
                    //PARAMCHANGE(7);
                }
                tl_latch_ = tl;
            }

            // Attack Rate (0-63)

            public void SetAR(int ar)
            {
                ar_ = ar;
                param_changed_ = true;
                //PARAMCHANGE(8);
            }

            // Decay Rate (0-63)

            public void SetDR(int dr)
            {
                dr_ = dr;
                param_changed_ = true;
                //PARAMCHANGE(9);
            }

            // Sustain Rate (0-63)

            public void SetSR(int sr)
            {
                sr_ = sr;
                param_changed_ = true;
                //PARAMCHANGE(10);
            }

            // Sustain Level (0-127)

            public void SetSL(int sl)
            {
                sl_ = sl;
                param_changed_ = true;
                //PARAMCHANGE(11);
            }

            // Release Rate (0-63)

            public void SetRR(int rr)
            {
                rr_ = rr;
                param_changed_ = true;
                //PARAMCHANGE(12);
            }

            // Keyscale (0-3)

            public void SetKS(int ks)
            {
                ks_ = ks;
                param_changed_ = true;
                //PARAMCHANGE(13);
            }


            public void SetPhaseReset(int prst)
            {
                phaseReset_ = (int)(prst != 0 ? 1 : 0);
                param_changed_ = true;
            }

            // SSG-type Envelop (0-15)

            public void SetSSGEC(int ssgec)
            {
                if ((ssgec & 8) != 0)
                    ssg_type_ = ssgec;
                else
                    ssg_type_ = 0;
            }


            public void SetAMON(boolean amon)
            {
                amon_ = amon;
                param_changed_ = true;
                //PARAMCHANGE(14);
            }


            public void SetFB(int fb)
            {
                fb_ = Channel4.fbtable[fb];
                param_changed_ = true;
                //PARAMCHANGE(15);
            }


            public void SetALGLink(int AlgLink)
            {
                algLink_ = (byte)AlgLink;
                param_changed_ = true;
                //PARAMCHANGE(16);
            }


            public void SetWaveTypeL(byte wt)
            {
                wt_ = (byte)((wt_ & 2) | (wt & 1));
                param_changed_ = true;
                //PARAMCHANGE(17);
            }


            public void SetWaveTypeH(byte wt)
            {
                wt_ = (byte)((wt_ & 1) | ((wt & 1) << 1));
                param_changed_ = true;
                //PARAMCHANGE(17);
            }


            public void Mute(boolean mute)
            {
                mute_ = mute;
                param_changed_ = true;
                //PARAMCHANGE(15);
            }


            public void SetMS(int ms)
            {
                ms_ = ms;
                param_changed_ = true;
                //PARAMCHANGE(16);
            }




            public void SetChip(Chip chip)
            {
                chip_ = chip;
            }

            public static void MakeTimeTable(int ratio)
            {
            }


            public void SetMode(boolean modulator)
            {
            }

            //  static void SetAML(int l);
            //  static void SetPML(int l);


            public int Out()
            {
                return out_;
            }


            public int dbgGetIn2()
            {
                return in2_;
            }


            public void dbgStopPG()
            {
                pg_diff_ = 0; pg_diff_lfo_ = 0;
            }


            private void SSGShiftPhase(int mode)
            {
            }


            private int FBCalc(int fb)
            {
                return -1;
            }

            // friends --------------------------------------------------------------
            //private class Channel4;

            private void FM_NextPhase(Operator op)
            {
            }


            public static int[] dbgGetClTable()
            {
                return cltable;
            }


            public int[] dbgGetSineTable(int c,int t)
            {
                return sinetable[c][t];
            }
        };

        public static void waveReset(int waveCh, int wavetype)
        {
            double log2 = Math.log(2.0);
            for (int i = 0; i < FM_OPSINENTS / 2; i++)
            {
                double r = (i * 2 + 1) * FM_PI / FM_OPSINENTS;
                double q = -256 * Math.log(Math.sin(r)) / log2;
                int s = (int)((int)(Math.floor(q + 0.5)) + 1);
                sinetable[waveCh][wavetype][i] = s * 2;
                sinetable[waveCh][wavetype][FM_OPSINENTS / 2 + i] = s * 2 + 1;
            }
        }

        public static class Channel4
        {
            // ---------------------------------------------------------------------------
            // 4-op Channel
            //
            public final static byte[] fbtable = new byte[] { 31, 7, 6, 5, 4, 3, 2, 1 };

            private final boolean tablehasmade = false;
            private static final int[] kftable = new int[64];
            private int fb;
            private int[] buf = new int[4];
            private int[] In = new int[3];          // 各 OP の入力ポインタ
            private int[] Out = new int[3];         // 各 OP の出力ポインタ
            private int[] pms;
            private int algo_;
            private Chip chip_;
            private boolean ac = false;
            private byte carrier;
            private int[] oAlg = new int[] { 2, 1, 3, 0 };//オペレータの計算順序
            private int ch = 0;

            public Operator[] op = new Operator[] {
                new Operator(),new Operator(),new Operator(),new Operator()
            };


            public Channel4(int ch)
            {
                if (!tablehasmade)
                    MakeTable();

                SetAlgorithm(0);
                pms = pmtable[0][0];
                this.ch = ch;
            }


            public void MakeTable()
            {
                // 100/64 cent =  2^(i*100/64*1200)
                for (int i = 0; i < 64; i++)
                {
                    kftable[i] = (int)(0x10000 * Math.pow(2.0, i / 768.0));
                }
            }

            // リセット

            public void Reset()
            {
                op[0].Reset();
                op[1].Reset();
                op[2].Reset();
                op[3].Reset();
            }

            // Calc の用意

            public int Prepare()
            {
                op[0].Prepare();
                op[1].Prepare();
                op[2].Prepare();
                op[3].Prepare();

                pms = pmtable[(int)op[0].type_.ordinal()][op[0].ms_ & 7];
                int key = op[0].IsOn() | op[1].IsOn() | op[2].IsOn() | op[3].IsOn() ? 1 : 0;
                int lfo = (op[0].ms_ & ((op[0].amon_ | op[1].amon_ | op[2].amon_ | op[3].amon_) ? 0x37 : 7)) != 0 ? 2 : 0;
                return key | lfo;
            }

            // F-Number/BLOCK を設定

            public void SetFNum(int f)
            {
                for (int i = 0; i < 4; i++)
                    op[i].SetFNum(f);
            }

            int[] kctable = new int[]
            {
                    5197, 5506, 5833, 6180, 6180, 6547, 6937, 7349,
                    7349, 7786, 8249, 8740, 8740, 9259, 9810, 10394,
            };

            // KC/KF を設定

            public void SetKCKF(int kc, int kf)
            {
                int oct = (int)(19 - ((kc >> 4) & 7));

                //printf("%p", this);
                int kcv = kctable[kc & 0x0f];
                kcv = (kcv + 2) / 4 * 4;
                //printf(" %.4x", kcv);
                int dp = (int)(kcv * kftable[kf & 0x3f]);
                //printf(" %.4x %.4x %.8x", kcv, kftable[kf & 0x3f], dp >> oct);
                dp >>= 16 + 3;
                dp <<= 16 + 3;
                dp >>= oct;
                int bn = (kc >> 2) & 31;
                op[0].SetDPBN(dp, bn);
                op[1].SetDPBN(dp, bn);
                op[2].SetDPBN(dp, bn);
                op[3].SetDPBN(dp, bn);
                //printf(" %.8x\n", dp);
            }

            // キー制御

            public void KeyControl(int key)
            {
                if ((key & 0x1) != 0) op[0].KeyOn();
                else op[0].KeyOff();
                if ((key & 0x2) != 0) op[1].KeyOn();
                else op[1].KeyOff();
                if ((key & 0x4) != 0) op[2].KeyOn();
                else op[2].KeyOff();
                if ((key & 0x8) != 0) op[3].KeyOn();
                else op[3].KeyOff();
            }

            byte[][] table1 = new byte[][] {
                    new byte[] { 0, 1, 1, 2, 2, 3 },
                    new byte[] { 1, 0, 0, 1, 1, 2 },
                    new byte[] { 1, 1, 1, 0, 0, 2 },
                    new byte[] { 0, 1, 2, 1, 1, 2 },
                    new byte[] { 0, 1, 2, 2, 2, 1 },
                    new byte[] { 0, 1, 0, 1, 0, 1 },
                    new byte[] { 0, 1, 2, 1, 2, 1 },
                    new byte[] { 1, 0, 1, 0, 1, 0 }
                };

            // アルゴリズムを設定

            public void SetAlgorithm(int algo)
            {
                //In[0] = buf[table1[algo][0]];
                //Out[0] = buf[table1[algo][1]];
                //In[1] = buf[table1[algo][2]];
                //Out[1] = buf[table1[algo][3]];
                //In[2] = buf[table1[algo][4]];
                //Out[2] = buf[table1[algo][5]];

                In[0] = table1[algo][0];
                Out[0] = table1[algo][1];
                In[1] = table1[algo][2];
                Out[1] = table1[algo][3];
                In[2] = table1[algo][4];
                Out[2] = table1[algo][5];

                op[0].ResetFB();
                algo_ = (int)algo;
            }

            //  合成

            public int Calc()
            {
                int r = 0;

                if (!ac)
                {
                    switch (algo_)
                    {
                        case 0:
                            op[2].Calc(ch,op[1].Out());
                            op[1].Calc(ch, op[0].Out());
                            r = op[3].Calc(ch, op[2].Out());
                            op[0].CalcFB(ch, fb);
                            break;
                        case 1:
                            op[2].Calc(ch, op[0].Out() + op[1].Out());
                            op[1].Calc(ch, 0);
                            r = op[3].Calc(ch, op[2].Out());
                            op[0].CalcFB(ch, fb);
                            break;
                        case 2:
                            op[2].Calc(ch, op[1].Out());
                            op[1].Calc(ch, 0);
                            r = op[3].Calc(ch, op[0].Out() + op[2].Out());
                            op[0].CalcFB(ch, fb);
                            break;
                        case 3:
                            op[2].Calc(ch, 0);
                            op[1].Calc(ch, op[0].Out());
                            r = op[3].Calc(ch, op[1].Out() + op[2].Out());
                            op[0].CalcFB(ch, fb);
                            break;
                        case 4:
                            op[2].Calc(ch, 0);
                            r = op[1].Calc(ch, op[0].Out());
                            r += op[3].Calc(ch, op[2].Out());
                            op[0].CalcFB(ch, fb);
                            break;
                        case 5:
                            r = op[2].Calc(ch, op[0].Out());
                            r += op[1].Calc(ch, op[0].Out());
                            r += op[3].Calc(ch, op[0].Out());
                            op[0].CalcFB(ch, fb);
                            break;
                        case 6:
                            r = op[2].Calc(ch, 0);
                            r += op[1].Calc(ch, op[0].Out());
                            r += op[3].Calc(ch, 0);
                            op[0].CalcFB(ch, fb);
                            break;
                        case 7:
                            r = op[2].Calc(ch, 0);
                            r += op[1].Calc(ch, 0);
                            r += op[3].Calc(ch, 0);
                            r += op[0].CalcFB(ch, fb);
                            break;
                    }
                }
                else
                {
                    for (int n : oAlg)
                    {
                        int rn;
                        if (n == 0)
                        {
                            rn = op[n].CalcFB(ch, fb);
                            r += ((carrier & 0x1) != 0) ? rn : 0;
                            continue;
                        }
                        int v = 0;
                        v += ((op[n].algLink_ & 0x1) != 0) ? op[0].Out() : 0;
                        v += ((op[n].algLink_ & 0x2) != 0) ? op[1].Out() : 0;
                        v += ((op[n].algLink_ & 0x4) != 0) ? op[2].Out() : 0;
                        v += ((op[n].algLink_ & 0x8) != 0) ? op[3].Out() : 0;

                        rn = op[n].Calc(ch, v);
                        r += ((carrier & (0x1 << n)) != 0) ? rn : 0;
                    }
                    //r += ((carrier & 0x1) != 0) ? op[0].Out() : 0;
                    //r += ((carrier & 0x2) != 0) ? op[1].Out() : 0;
                    //r += ((carrier & 0x4) != 0) ? op[2].Out() : 0;
                    //r += ((carrier & 0x8) != 0) ? op[3].Out() : 0;
                }

                return r;
            }

            //  合成

            public int CalcL()
            {
                chip_.SetPMV(pms[chip_.GetPML()]);

                int r = 0;
                if (!ac)
                {
                    switch (algo_)
                    {
                        case 0:
                            op[2].CalcL(ch, op[1].Out());
                            op[1].CalcL(ch, op[0].Out());
                            r = op[3].CalcL(ch, op[2].Out());
                            op[0].CalcFBL(ch, fb);
                            break;
                        case 1:
                            op[2].CalcL(ch, op[0].Out() + op[1].Out());
                            op[1].CalcL(ch, 0);
                            r = op[3].CalcL(ch, op[2].Out());
                            op[0].CalcFBL(ch, fb);
                            break;
                        case 2:
                            op[2].CalcL(ch, op[1].Out());
                            op[1].CalcL(ch, 0);
                            r = op[3].CalcL(ch, op[0].Out() + op[2].Out());
                            op[0].CalcFBL(ch, fb);
                            break;
                        case 3:
                            op[2].CalcL(ch, 0);
                            op[1].CalcL(ch, op[0].Out());
                            r = op[3].CalcL(ch, op[1].Out() + op[2].Out());
                            op[0].CalcFBL(ch, fb);
                            break;
                        case 4:
                            op[2].CalcL(ch, 0);
                            r = op[1].CalcL(ch, op[0].Out());
                            r += op[3].CalcL(ch, op[2].Out());
                            op[0].CalcFBL(ch, fb);
                            break;
                        case 5:
                            r = op[2].CalcL(ch, op[0].Out());
                            r += op[1].CalcL(ch, op[0].Out());
                            r += op[3].CalcL(ch, op[0].Out());
                            op[0].CalcFBL(ch, fb);
                            break;
                        case 6:
                            r = op[2].CalcL(ch, 0);
                            r += op[1].CalcL(ch, op[0].Out());
                            r += op[3].CalcL(ch, 0);
                            op[0].CalcFBL(ch, fb);
                            break;
                        case 7:
                            r = op[2].CalcL(ch, 0);
                            r += op[1].CalcL(ch, 0);
                            r += op[3].CalcL(ch, 0);
                            r += op[0].CalcFBL(ch, fb);
                            break;
                    }
                }
                else
                {
                    for (int n : oAlg)
                    {
                        int rn;
                        if (n == 0)
                        {
                            rn=op[n].CalcFBL(ch, fb);
                            r += ((carrier & 0x1) != 0) ? rn : 0;
                            continue;
                        }
                        int v = 0;
                        v += ((op[n].algLink_ & 0x1) != 0) ? op[0].Out() : 0;
                        v += ((op[n].algLink_ & 0x2) != 0) ? op[1].Out() : 0;
                        v += ((op[n].algLink_ & 0x4) != 0) ? op[2].Out() : 0;
                        v += ((op[n].algLink_ & 0x8) != 0) ? op[3].Out() : 0;

                        rn = op[n].CalcL(ch, v);
                        r += ((carrier & (0x1 << n)) != 0) ? rn : 0;
                    }
                    //r += ((carrier & 0x1) != 0) ? op[0].Out() : 0;
                    //r += ((carrier & 0x2) != 0) ? op[1].Out() : 0;
                    //r += ((carrier & 0x4) != 0) ? op[2].Out() : 0;
                    //r += ((carrier & 0x8) != 0) ? op[3].Out() : 0;
                }

                return r;
            }

            //  合成

            public int CalcN(int noise)
            {
                buf[1] = buf[2] = buf[3] = 0;

                buf[0] = op[0].out_; op[0].CalcFB(ch, fb);
                Out[0] += op[1].Calc(ch, buf[In[0]]);
                Out[1] += op[2].Calc(ch, buf[In[1]]);
                int o = op[3].out_;
                op[3].CalcN(noise);
                return buf[Out[2]] + o;
            }

            //  合成

            public int CalcLN(int noise)
            {
                chip_.SetPMV(pms[chip_.GetPML()]);
                buf[1] = buf[2] = buf[3] = 0;

                buf[0] = op[0].out_; op[0].CalcFBL(ch, fb);
                Out[0] += op[1].CalcL(ch, buf[In[0]]);
                Out[1] += op[2].CalcL(ch, buf[In[1]]);
                int o = op[3].out_;
                op[3].CalcN(noise);
                return buf[Out[2]] + o;
            }

            // オペレータの種類 (LFO) を設定

            public void SetType(OpType type)
            {
                for (int i = 0; i < 4; i++)
                    op[i].type_ = type;
            }

            // セルフ・フィードバックレートの設定 (0-7)

            public void SetFB(int feedback)
            {
                fb = fbtable[feedback];
            }


            public void SetAC(boolean sw)
            {
                ac = sw;
                buildAlg();
            }

            // OPNA 系 LFO の設定

            public void SetMS(int ms)
            {
                op[0].SetMS(ms);
                op[1].SetMS(ms);
                op[2].SetMS(ms);
                op[3].SetMS(ms);
            }

            // チャンネル・マスク

            public void Mute(boolean m)
            {
                for (int i = 0; i < 4; i++)
                    op[i].Mute(m);
            }

            // 内部パラメータを再計算

            public void Refresh()
            {
                for (int i = 0; i < 4; i++)
                    op[i].param_changed_ = true;
                //PARAMCHANGE(3);
            }


            public void SetChip(Chip chip)
            {
                chip_ = chip;
                for (int i = 0; i < 4; i++)
                    op[i].SetChip(chip);
            }


            public void dbgStopPG()
            {
                for (int i = 0; i < 4; i++) op[i].dbgStopPG();
            }


            public void buildAlg()
            {
                byte mask = 0xf;
                byte omask = 0;
                boolean[] use = new boolean[] { false, false, false, false };

                carrier = 0xf;
                //oAlg.Clear();
                //oAlg.add(2);
                //oAlg.add(1);
                //oAlg.add(3);
                //oAlg.add(0);

                for (int j = 0; j < 4; j++)
                {
                    omask = (byte)(~mask);
                    for (int i = 0; i < 4; i++)
                    {
                        if (!use[i] && ( ((op[i].algLink_ & mask) == 0) || i == 0 ))
                        {
                            use[i] = true;
                            //oAlg.add(i);
                            omask |= (byte)(1 << i);
                            carrier &= (byte)(~op[i].algLink_);
                        }
                    }
                    mask = (byte)(~omask);
                }

                if (carrier != 0xf)
                {
                    for (int i = 0; i < 4; i++)
                        System.err.printf("algLink:{0}:{1}", i, op[i].algLink_);
                    System.err.printf("carrier:{0}", carrier);
                }
            }

        };

    }
