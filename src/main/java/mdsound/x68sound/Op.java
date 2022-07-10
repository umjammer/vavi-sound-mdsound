package mdsound.x68sound;

public class Op {
    public Global global;

    public static final int KEYON = -1;
    public static final int ATACK = 0;
    public static final int DECAY = 1;
    public static final int SUSTAIN = 2;
    public static final int SUSTAIN_MAX = 3;
    public static final int RELEASE = 4;
    public static final int RELEASE_MAX = 5;

    public static final int CULC_DELTA_T = 0x7FFFFFFF;
    public static final int CULC_ALPHA = 0x7FFFFFFF;

    public static int IS_ZERO_CLOSS(int a, int b) {
        return ((a < 0 && b >= 0) || (a > 0 && b <= 0)) ? 1 : 0;
    }

    public static final int[] NEXTSTAT = new int[] {
            DECAY, SUSTAIN, SUSTAIN_MAX, SUSTAIN_MAX, RELEASE_MAX, RELEASE_MAX,
    };
    public static final int[] MAXSTAT = new int[] {
            ATACK, SUSTAIN_MAX, SUSTAIN_MAX, SUSTAIN_MAX, RELEASE_MAX, RELEASE_MAX,
    };

    public int[] inp = new int[1]; // FM変調の入力
    private int lfoPitch; // 前回のlfopitch値, CULC_DELTA_T値の時はDeltaTを再計算する。
    private int t; // 現在時間 (0 <= T < SIZESINTBL*PRECISION)
    private int deltaT; // Δt
    private int ame; // 0(トレモロをかけない), -1(トレモロをかける)
    private int lfoLevel; // 前回のlfopitch&Ame値, CULC_ALPHA値の時はAlphaを再計算する。
    private int alpha; // 最終的なエンベロープ出力値
    //追加 2006.03.26 sam Lfoの更新をSinテーブルの0クロス時に修正するため
    private boolean lfoLevelReCalc;
    private short sinBf;

    public int[] out1 = new int[1]; // オペレータの出力先
    public int[] out2 = new int[1]; // オペレータの出力先(alg=5時のM1用)
    public int[] out3 = new int[1]; // オペレータの出力先(alg=5時のM1用)

    private int pitch; // 0<=pitch<10*12*64
    private int dt1Pitch; // Step に対する補正量
    private int mul; // 0.5*2 1*2 2*2 3*2 ... 15*2
    private int tl; // (128-TL)*8

    private int out2Fb; // フィードバックへの出力値
    private int inpLast; // 最後の入力値
    private int fl; // フィードバックレベルのシフト値(31,7,6,5,4,3,2,1)
    private int flMask; // フィードバックのマスク(0,-1)
    public int arTime = 0; // AR専用 t

    private int noiseCounter; // Noise用カウンタ
    private int noiseStep; // Noise用カウントダウン値
    private int noiseCycle; // Noise周期 32*2^25(0) ～ 1*2^25(31) NoiseCycle==0の時はノイズオフ
    private int noiseValue; // ノイズ値  1 or -1

    // エンベロープ関係
    private int xrStat;
    private int xrEl;
    private int xrStep;
    private int xrAnd;
    private int xrCmp;
    private int xrAdd;
    private int xrLimit;

    private int note; // 音階 (0 <= Note < 10*12)
    private int kc; // 音階 (1 <= Kc <= 128)
    private int kf; // 微調整 (0 <= Kf < 64)
    private int ar; // 0 <= Ar < 31
    private int d1R; // 0 <= D1r < 31
    private int d2R; // 0 <= D2r < 31
    private int rr; // 0 <= Rr < 15
    private int ks; // 0 <= Ks <= 3
    private int dt2; // Pitch に対する補正量(0, 384, 500, 608)
    private int dt1; // DT1の値(0～7)
    private int nfrq; // Noiseflag,NFRQの値

    public static class _StatTbl {
        public int and, cmp, add, limit;
    }

    private _StatTbl[] statTbl = new _StatTbl[RELEASE_MAX + 1]; // 状態推移テーブル
    //           ATACK     DECAY   SUSTAIN     SUSTAIN_MAX RELEASE     RELEASE_MAX
    // and     :                               4097                    4097
    // cmp     :                               2048                    2048
    // add     :                               0                       0
    // limit   : 0         D1l     63          63          63          63
    // nextstat: DECAY     SUSTAIN SUSTAIN_MAX SUSTAIN_MAX RELEASE_MAX RELEASE_MAX

    private int keyon;
    private int csmkeyon;

    public Op(Global global) {
        this.global = global;
    }

    public void init() {
        note = 5 * 12 + 8;
        kc = 5 * 16 + 8 + 1;
        kf = 5;
        ar = 10;
        d1R = 10;
        d2R = 5;
        rr = 12;
        ks = 1;
        dt2 = 0;
        dt1 = 0;

        arTime = 0;
        fl = 31;
        flMask = 0;
        out2Fb = 0;
        inp[0] = 0;
        inpLast = 0;
        deltaT = 0;
        lfoPitch = CULC_DELTA_T;
        t = 0;
        lfoLevel = CULC_ALPHA;
        alpha = 0;
        tl = (128 - 127) << 3;
        xrEl = 1024;
        xrStep = 0;
        mul = 2;
        ame = 0;

        noiseStep = (int) ((long) (1 << 26) * (long) global.opmRate / Global.sampleRate);
        setNFRQ(0);
        noiseValue = 1;

        // 状態推移テーブルを作成
        // StatTbl[ATACK].nextstat = DECAY;
        // StatTbl[DECAY].nextstat = SUSTAIN;
        // StatTbl[SUSTAIN].nextstat = SUSTAIN_MAX;
        // StatTbl[SUSTAIN_MAX].nextstat = SUSTAIN_MAX;
        // StatTbl[RELEASE].nextstat = RELEASE_MAX;
        // StatTbl[RELEASE_MAX].nextstat = RELEASE_MAX;

        statTbl[0] = new _StatTbl();
        statTbl[1] = new _StatTbl();
        statTbl[2] = new _StatTbl();
        statTbl[3] = new _StatTbl();
        statTbl[4] = new _StatTbl();
        statTbl[5] = new _StatTbl();
        statTbl[ATACK].limit = 0;
        statTbl[DECAY].limit = global.D1LTBL[0];
        statTbl[SUSTAIN].limit = 63;
        statTbl[SUSTAIN_MAX].limit = 63;
        statTbl[RELEASE].limit = 63;
        statTbl[RELEASE_MAX].limit = 63;

        statTbl[SUSTAIN_MAX].and = 4097;
        statTbl[SUSTAIN_MAX].cmp = 2048;
        statTbl[SUSTAIN_MAX].add = 0;
        statTbl[RELEASE_MAX].and = 4097;
        statTbl[RELEASE_MAX].cmp = 2048;
        statTbl[RELEASE_MAX].add = 0;

        xrStat = RELEASE_MAX;
        xrAnd = statTbl[xrStat].and;
        xrCmp = statTbl[xrStat].cmp;
        xrAdd = statTbl[xrStat].add;
        xrLimit = statTbl[xrStat].limit;

        keyon = 0;
        csmkeyon = 0;

        culcArStep();
        culcD1RStep();
        culcD2RStep();
        culcRrStep();
        culcPitch();
        culcDt1Pitch();

        //2006.03.26 追加 sam lfo更新タイミング修正のため
        sinBf = 0;
        lfoLevelReCalc = true;
    }

    public void initSamprate() {
        lfoPitch = CULC_DELTA_T;

        noiseStep = (int) ((long) (1 << 26) * (long) global.opmRate / Global.sampleRate);
        culcNoiseCycle();

        culcArStep();
        culcD1RStep();
        culcD2RStep();
        culcRrStep();
        culcPitch();
        culcDt1Pitch();
    }

    private void culcArStep() {
        if (ar != 0) {
            int ks = (ar << 1) + (kc >> (5 - this.ks));
            statTbl[ATACK].and = Global.XRTBL[ks].and;
            statTbl[ATACK].cmp = Global.XRTBL[ks].and >> 1;
            if (ks < 62) {
                statTbl[ATACK].add = Global.XRTBL[ks].add;
            } else {
                statTbl[ATACK].add = 128;
            }
        } else {
            statTbl[ATACK].and = 4097;
            statTbl[ATACK].cmp = 2048;
            statTbl[ATACK].add = 0;
        }
        if (xrStat == ATACK) {
            xrAnd = statTbl[xrStat].and;
            xrCmp = statTbl[xrStat].cmp;
            xrAdd = statTbl[xrStat].add;
        }
    }

    private void culcD1RStep() {
        if (d1R != 0) {
            int ks = (d1R << 1) + (kc >> (5 - this.ks));
            statTbl[DECAY].and = Global.XRTBL[ks].and;
            statTbl[DECAY].cmp = Global.XRTBL[ks].and >> 1;
            statTbl[DECAY].add = Global.XRTBL[ks].add;
        } else {
            statTbl[DECAY].and = 4097;
            statTbl[DECAY].cmp = 2048;
            statTbl[DECAY].add = 0;
        }
        if (xrStat == DECAY) {
            xrAnd = statTbl[xrStat].and;
            xrCmp = statTbl[xrStat].cmp;
            xrAdd = statTbl[xrStat].add;
        }
    }

    private void culcD2RStep() {
        if (d2R != 0) {
            int ks = (d2R << 1) + (kc >> (5 - this.ks));
            statTbl[SUSTAIN].and = Global.XRTBL[ks].and;
            statTbl[SUSTAIN].cmp = Global.XRTBL[ks].and >> 1;
            statTbl[SUSTAIN].add = Global.XRTBL[ks].add;
        } else {
            statTbl[SUSTAIN].and = 4097;
            statTbl[SUSTAIN].cmp = 2048;
            statTbl[SUSTAIN].add = 0;
        }
        if (xrStat == SUSTAIN) {
            xrAnd = statTbl[xrStat].and;
            xrCmp = statTbl[xrStat].cmp;
            xrAdd = statTbl[xrStat].add;
        }
    }

    private void culcRrStep() {
        int ks = (rr << 2) + 2 + (kc >> (5 - this.ks));
        statTbl[RELEASE].and = Global.XRTBL[ks].and;
        statTbl[RELEASE].cmp = Global.XRTBL[ks].and >> 1;
        statTbl[RELEASE].add = Global.XRTBL[ks].add;
        if (xrStat == RELEASE) {
            xrAnd = statTbl[xrStat].and;
            xrCmp = statTbl[xrStat].cmp;
            xrAdd = statTbl[xrStat].add;
        }
    }

    private void culcPitch() {
        pitch = (note << 6) + kf + dt2;
    }

    private void culcDt1Pitch() {
        dt1Pitch = global.DT1TBL[(kc & 0xFC) + (dt1 & 3)];
        if ((dt1 & 0x04) != 0) {
            dt1Pitch = -dt1Pitch;
        }
    }

    public void setFL(int n) {
        n = (n >> 3) & 7;
        if (n == 0) {
            fl = 31;
            flMask = 0;
        } else {
            fl = (7 - n + 1 + 1);
            flMask = -1;
        }
    }

    public void setKC(int n) {
        kc = n & 127;
        int note = kc & 15;
        this.note = ((kc >> 4) + 1) * 12 + note - (note >> 2);
        ++kc;
        culcPitch();
        culcDt1Pitch();
        lfoPitch = CULC_DELTA_T;
        culcArStep();
        culcD1RStep();
        culcD2RStep();
        culcRrStep();
    }

    public void setKF(int n) {
        kf = (n & 255) >> 2;
        culcPitch();
        lfoPitch = CULC_DELTA_T;
    }

    public void setDT1MUL(int n) {
        dt1 = (n >> 4) & 7;
        culcDt1Pitch();
        mul = (n & 15) << 1;
        if (mul == 0) {
            mul = 1;
        }
        lfoPitch = CULC_DELTA_T;
    }

    public void setTL(int n) {
        tl = (128 - (n & 127)) << 3;
        // LfoLevel = CULC_ALPHA;
        lfoLevelReCalc = true;
    }

    public void setKSAR(int n) {
        ks = (n & 255) >> 6;
        ar = n & 31;
        culcArStep();
        culcD1RStep();
        culcD2RStep();
        culcRrStep();
    }

    public void setAMED1R(int n) {
        d1R = n & 31;
        culcD1RStep();
        ame = 0;
        if ((n & 0x80) != 0) {
            ame = -1;
        }
    }

    public void setDT2D2R(int n) {
        dt2 = Global.DT2TBL[(n & 255) >> 6];
        culcPitch();
        lfoPitch = CULC_DELTA_T;
        d2R = n & 31;
        culcD2RStep();
    }

    public void setD1LRR(int n) {
        statTbl[DECAY].limit = global.D1LTBL[(n & 255) >> 4];
        if (xrStat == DECAY) {
            xrLimit = statTbl[DECAY].limit;
        }

        rr = n & 15;
        culcRrStep();
    }

    public void keyON(int csm) {
        if (keyon == 0) {
            if (xrStat >= RELEASE) {
                // KEYON
                t = 0;

                if (xrEl == 0) {
                    xrStat = DECAY;
                    xrAnd = statTbl[xrStat].and;
                    xrCmp = statTbl[xrStat].cmp;
                    xrAdd = statTbl[xrStat].add;
                    xrLimit = statTbl[xrStat].limit;
                    if ((xrEl >> 4) == xrLimit) {
                        xrStat = NEXTSTAT[xrStat];
                        xrAnd = statTbl[xrStat].and;
                        xrCmp = statTbl[xrStat].cmp;
                        xrAdd = statTbl[xrStat].add;
                        xrLimit = statTbl[xrStat].limit;
                    }
                } else {
                    xrStat = ATACK;
                    xrAnd = statTbl[xrStat].and;
                    xrCmp = statTbl[xrStat].cmp;
                    xrAdd = statTbl[xrStat].add;
                    xrLimit = statTbl[xrStat].limit;
                }
            }

            if (csm == 0) {
                keyon = 1;
                csmkeyon = 0;
            } else {
                csmkeyon = 1;
            }
        }
    }

    public void keyOFF(int csm) {
        if (keyon > 0 || csmkeyon > 0) {

            if (csm == 0) {
                keyon = 0;
            } else {
                csmkeyon = 0;
            }

            if (keyon == 0 && csmkeyon == 0) {
                xrStat = RELEASE;
                xrAnd = statTbl[xrStat].and;
                xrCmp = statTbl[xrStat].cmp;
                xrAdd = statTbl[xrStat].add;
                xrLimit = statTbl[xrStat].limit;
                if ((xrEl >> 4) >= 63 || csm > 0) {
                    xrEl = 1024;
                    xrStat = MAXSTAT[xrStat];
                    xrAnd = statTbl[xrStat].and;
                    xrCmp = statTbl[xrStat].cmp;
                    xrAdd = statTbl[xrStat].add;
                    xrLimit = statTbl[xrStat].limit;
                }
            }
        }
    }

    public void envelope(int env_counter) {
        if ((env_counter & xrAnd) == xrCmp) {

            if (xrStat == ATACK) {
                // ATACK
                xrStep += xrAdd;
                xrEl += ((~xrEl) * (xrStep >> 3)) >> 4;
                //   LfoLevel = CULC_ALPHA;
                lfoLevelReCalc = true;
                xrStep &= 7;

                if (xrEl <= 0) {
                    xrEl = 0;
                    xrStat = DECAY;
                    xrAnd = statTbl[xrStat].and;
                    xrCmp = statTbl[xrStat].cmp;
                    xrAdd = statTbl[xrStat].add;
                    xrLimit = statTbl[xrStat].limit;
                    if ((xrEl >> 4) == xrLimit) {
                        xrStat = NEXTSTAT[xrStat];
                        xrAnd = statTbl[xrStat].and;
                        xrCmp = statTbl[xrStat].cmp;
                        xrAdd = statTbl[xrStat].add;
                        xrLimit = statTbl[xrStat].limit;
                    }
                }
            } else {
                // DECAY, SUSTAIN, RELEASE
                xrStep += xrAdd;
                xrEl += xrStep >> 3;
                //   LfoLevel = CULC_ALPHA;
                lfoLevelReCalc = true;
                xrStep &= 7;

                int e = xrEl >> 4;
                if (e == 63) {
                    xrEl = 1024;
                    xrStat = MAXSTAT[xrStat];
                    xrAnd = statTbl[xrStat].and;
                    xrCmp = statTbl[xrStat].cmp;
                    xrAdd = statTbl[xrStat].add;
                    xrLimit = statTbl[xrStat].limit;
                } else if (e == xrLimit) {
                    xrStat = NEXTSTAT[xrStat];
                    xrAnd = statTbl[xrStat].and;
                    xrCmp = statTbl[xrStat].cmp;
                    xrAdd = statTbl[xrStat].add;
                    xrLimit = statTbl[xrStat].limit;
                }
            }
        }
    }

    public void setNFRQ(int nfrq) {
        if (((this.nfrq ^ nfrq) & 0x80) != 0) {
            //  LfoLevel = CULC_ALPHA;
            lfoLevelReCalc = true;
        }
        this.nfrq = nfrq;
        culcNoiseCycle();
    }

    private void culcNoiseCycle() {
        if ((nfrq & 0x80) != 0) {
            noiseCycle = (32 - (nfrq & 31)) << 25;
            if (noiseCycle < noiseStep) {
                noiseCycle = noiseStep;
            }
            noiseCounter = noiseCycle;
        } else {
            noiseCycle = 0;
        }
    }

    public void output0(int lfoPitch, int lfoLevel) {
        if (this.lfoPitch != lfoPitch) {
            //  DeltaT = ((STEPTBL[Pitch+lfoPitch]+Dt1Pitch)*Mul)>>1;
            deltaT = ((global.STEPTBL[pitch + lfoPitch] + dt1Pitch) * mul) >> (6 + 1);
            this.lfoPitch = lfoPitch;
        }
        t += deltaT;
        short Sin = (global.SINTBL[(((t + out2Fb) >> Global.PRECISION_BITS)) & (Global.SIZESINTBL - 1)]);

        int lfolevelame = lfoLevel & ame;
        if ((this.lfoLevel != lfolevelame || lfoLevelReCalc) && IS_ZERO_CLOSS(sinBf, Sin) != 0) {
            alpha = global.ALPHATBL[global.ALPHAZERO + tl - xrEl - lfolevelame];
            this.lfoLevel = lfolevelame;
            lfoLevelReCalc = false;
        }
        int o = (alpha)
                * (int) Sin;
        sinBf = Sin;

        // int o2 = (o+Inp_last) >> 1;
        // Out2Fb = (o+o) >> Fl;
        out2Fb = ((o + inpLast) & flMask) >> fl;
        inpLast = o;

        out1[0] = o;
        out2[0] = o; // alg=5用
        out3[0] = o; // alg=5用
        // *out = o2;
        // *out2 = o2; // alg=5用
        // *out3 = o2; // alg=5用
    }

    public void output(int lfoPitch, int lfoLevel) {
        if (this.lfoPitch != lfoPitch) {
            //  DeltaT = ((STEPTBL[Pitch+lfoPitch]+Dt1Pitch)*Mul)>>1;
            deltaT = ((global.STEPTBL[pitch + lfoPitch] + dt1Pitch) * mul) >> (6 + 1);
            this.lfoPitch = lfoPitch;
        }
        t += deltaT;
        short sin = (global.SINTBL[(((t + inp[0]) >> Global.PRECISION_BITS)) & (Global.SIZESINTBL - 1)]);

        int lfolevelame = lfoLevel & ame;
        if ((this.lfoLevel != lfolevelame || lfoLevelReCalc) && IS_ZERO_CLOSS(sinBf, sin) != 0) {
            alpha = global.ALPHATBL[global.ALPHAZERO + tl - xrEl - lfolevelame];
            this.lfoLevel = lfolevelame;
            lfoLevelReCalc = false;
        }
        int o = (alpha) * (int) sin;
        sinBf = sin;

        out1[0] += o;
    }

    public void output32(int lfoPitch, int lfoLevel) {
        if (this.lfoPitch != lfoPitch) {
            //  DeltaT = ((STEPTBL[Pitch+lfoPitch]+Dt1Pitch)*Mul)>>1;
            deltaT = ((global.STEPTBL[pitch + lfoPitch] + dt1Pitch) * mul) >> (6 + 1);
            this.lfoPitch = lfoPitch;
        }
        t += deltaT;

        int o;
        short sin = global.SINTBL[(((t + inp[0]) >> Global.PRECISION_BITS)) & (Global.SIZESINTBL - 1)];
        if (noiseCycle == 0) {
            int lfoLevelAme = lfoLevel & ame;
            if ((this.lfoLevel != lfoLevelAme || lfoLevelReCalc) && IS_ZERO_CLOSS(sinBf, sin) != 0) {
                alpha = global.ALPHATBL[global.ALPHAZERO + tl - xrEl - lfoLevelAme];
                this.lfoLevel = lfoLevelAme;
                lfoLevelReCalc = false;
            }
            o = (alpha) * (int) sin;
            sinBf = sin;
        } else {
            noiseCounter -= noiseStep;
            if (noiseCounter <= 0) {
                noiseValue = ((global.irnd() >> 30) & 2) - 1;
                noiseCounter += noiseCycle;
            }

            int lfoLevelAme = lfoLevel & ame;
            if (this.lfoLevel != lfoLevelAme || lfoLevelReCalc) {
                alpha = global.NOISEALPHATBL[global.ALPHAZERO + tl - xrEl - lfoLevelAme];
                this.lfoLevel = lfoLevelAme;
                lfoLevelReCalc = false;
            }
            o = (alpha) * noiseValue * Global.MAXSINVAL;
        }

        out1[0] += o;
    }
}
