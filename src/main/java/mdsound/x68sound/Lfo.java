package mdsound.x68sound;

public class Lfo {

    public Global global;

    private static final int SIZELFOTBL = 512;             // 2^9
    private static final int SIZELFOTBL_BITS = 9;
    private static final int LFOPRECISION = 4096; // 2^12
    //#define PMTBLMAXVAL (128)
    //#define PMTBLMAXVAL_BITS (7)
    //#define AMTBLMAXVAL (256)
    //#define AMTBLMAXVAL_BITS (8)
    //#define LFOTIMECYCLE  1073741824  // 2^30
    //#define LFOTIMECYCLE_BITS 30
    //#define LFORNDTIMECYCLE  (LFOTIMECYCLE>>8)  // 2^22
    //#define CYCLE2PMAM (30-8)    // log2(LFOTIMECYCLE/SIZEPMAMTBL)
    //#define LFOHZ  0.0009313900811
    //int  LFOSTEPTBL[256];
    //int  LFOSTEPTBL3[256];  // Wave form 3 用
    //short PMSTBL[8]={ 0,1,2,4,8,16,64,128 };
    private static final int[] PMSMUL = new int[] {0, 1, 2, 4, 8, 16, 32, 32};
    private static final int[] PMSSHL = new int[] {0, 0, 0, 0, 0, 0, 1, 2};

    private int[] pmsmul = new int[Global.N_CH];   // 0, 1, 2, 4, 8, 16, 32, 32
    private int[] pmsshl = new int[Global.N_CH];   // 0, 0, 0, 0, 0,  0,  1,  2
    private int[] ams = new int[Global.N_CH];  // 左シフト回数 31(0), 0(1), 1(2), 2(3)
    private int[] pmdPmsmul = new int[Global.N_CH];    // Pmd*Pmsmul[]
    private int pmd;
    private int amd;

    private int lfoStartingFlag;    // 0:LFO停止中  1:LFO動作中
    private int lfoOverFlow;    // LFO tのオーバーフロー値
    private int lfoTime;    // LFO専用 t
    private int lfoTimeAdd; // LFO専用Δt
    private int lfoIdx; // LFOテーブルへのインデックス値
    private int lfoSmallCounter;    // LFO周期微調整カウンタ (0～15の値をとる)
    private int lfoSmallCounterStep;    // LFO周期微調整カウンタ用ステップ値 (16～31)
    private int lfrq;       // LFO周波数設定値 LFRQ
    private int lfoWaveForm;    // LFO wave form

    private int pmTblValue, amTblValue;
    private int[] pmValue = new int[Global.N_CH], amValue = new int[Global.N_CH];

    private byte[] pmTbl0 = new byte[SIZELFOTBL], pmTbl2 = new byte[SIZELFOTBL];
    private byte[] amTbl0 = new byte[SIZELFOTBL], amTbl2 = new byte[SIZELFOTBL];

    public Lfo(Global global) {
        this.global = global;

        int i;

        for (i = 0; i < Global.N_CH; ++i) {
            pmsmul[i] = 0;
            pmsshl[i] = 0;
            ams[i] = 31;
            pmdPmsmul[i] = 0;

            pmValue[i] = 0;
            amValue[i] = 0;
        }
        pmd = 0;
        amd = 0;

        lfoStartingFlag = 0;
        lfoOverFlow = 0;
        lfoTime = 0;
        lfoTimeAdd = 0;
        lfoIdx = 0;
        lfoSmallCounter = 0;
        lfoSmallCounterStep = 0;
        lfrq = 0;
        lfoWaveForm = 0;

        pmTblValue = 0;
        amTblValue = 255;

        // PM Wave Form 0,3
        for (i = 0; i <= 127; ++i) {
            pmTbl0[i] = (byte) i;
            pmTbl0[i + 128] = (byte) (i - 127);
            pmTbl0[i + 256] = (byte) i;
            pmTbl0[i + 384] = (byte) (i - 127);
        }
        // AM Wave Form 0,3
        for (i = 0; i <= 255; ++i) {
            amTbl0[i] = (byte) (255 - i);
            amTbl0[i + 256] = (byte) (255 - i);

        }

        // PM Wave Form 2
        for (i = 0; i <= 127; ++i) {
            pmTbl2[i] = (byte) i;
            pmTbl2[i + 128] = (byte) (127 - i);
            pmTbl2[i + 256] = (byte) -i;
            pmTbl2[i + 384] = (byte) (i - 127);
        }
        // AM Wave Form 2
        for (i = 0; i <= 255; ++i) {
            amTbl2[i] = (byte) (255 - i);
            amTbl2[i + 256] = (byte) i;
        }
    }

    public void init() {
        lfoTimeAdd = LFOPRECISION * global.opmRate / Global.samprate;

        lfoSmallCounter = 0;

        setLFRQ(0);
        setPMDAMD(0);
        setPMDAMD(128 + 0);
        setWaveForm(0);
        {
            int ch;
            for (ch = 0; ch < Global.N_CH; ++ch) {
                setPMSAMS(ch, 0);
            }
        }
        lfoReset();
        lfoStart();
    }

    public void initSamprate() {
        lfoTimeAdd = LFOPRECISION * global.opmRate / Global.samprate;
    }

    public void lfoReset() {
        lfoStartingFlag = 0;

        // LfoTime はリセットされない！！
        lfoIdx = 0;

        culcTblValue();
        culcAllPmValue();
        culcAllAmValue();
    }

    public void lfoStart() {
        lfoStartingFlag = 1;
    }

    public void setLFRQ(int n) {
        lfrq = n & 255;

        lfoSmallCounterStep = 16 + (lfrq & 15);
        int shift = 15 - (lfrq >> 4);
        if (shift == 0) {
            shift = 1;
            lfoSmallCounterStep <<= 1;
        }
        lfoOverFlow = (8 << shift) * LFOPRECISION;

        // LfoTime はリセットされる
        lfoTime = 0;
    }

    public void setPMDAMD(int n) {
        if ((n & 0x80) != 0) {
            pmd = n & 0x7F;
            int ch;
            for (ch = 0; ch < Global.N_CH; ++ch) {
                pmdPmsmul[ch] = pmd * pmsmul[ch];
            }
            culcAllPmValue();
        } else {
            amd = n & 0x7F;
            culcAllAmValue();
        }
    }

    public void setWaveForm(int n) {
        lfoWaveForm = n & 3;

        culcTblValue();
        culcAllPmValue();
        culcAllAmValue();
    }

    public void setPMSAMS(int ch, int n) {
        int pms = (n >> 4) & 7;
        pmsmul[ch] = PMSMUL[pms];
        pmsshl[ch] = PMSSHL[pms];
        pmdPmsmul[ch] = pmd * pmsmul[ch];
        culcPmValue(ch);

        ams[ch] = ((n & 3) - 1) & 31;
        culcAmValue(ch);
    }

    public void update() {
        if (lfoStartingFlag == 0) {
            return;
        }

        lfoTime += lfoTimeAdd;
        //2008.4.19 sam 修正 LfoTimeの誤差を小さくするため,残余を保存する
        // if (LfoTime >= LfoOverFlow) {
        //  LfoTime = 0;
        while (lfoTime >= lfoOverFlow) {
            lfoTime -= lfoOverFlow;
            lfoSmallCounter += lfoSmallCounterStep;
            switch (lfoWaveForm) {
            case 0: {
                int idxadd = lfoSmallCounter >> 4;
                lfoIdx = (lfoIdx + idxadd) & (SIZELFOTBL - 1);
                pmTblValue = pmTbl0[lfoIdx];
                amTblValue = amTbl0[lfoIdx];
                break;
            }
            case 1: {
                int idxadd = lfoSmallCounter >> 4;
                lfoIdx = (lfoIdx + idxadd) & (SIZELFOTBL - 1);
                if ((lfoIdx & (SIZELFOTBL / 2 - 1)) < SIZELFOTBL / 4) {
                    pmTblValue = 128;
                    amTblValue = 256;
                } else {
                    pmTblValue = -128;
                    amTblValue = 0;
                }
            }
            break;
            case 2: {
                int idxadd = lfoSmallCounter >> 4;
                lfoIdx = (lfoIdx + idxadd + idxadd) & (SIZELFOTBL - 1);
                pmTblValue = pmTbl2[lfoIdx];
                amTblValue = amTbl2[lfoIdx];
                break;
            }
            case 3: {
                lfoIdx = (int) (global.irnd() >> (32 - SIZELFOTBL_BITS));
                pmTblValue = pmTbl0[lfoIdx];
                amTblValue = amTbl0[lfoIdx];
                break;
            }
            }
            lfoSmallCounter &= 15;

            culcAllPmValue();
            culcAllAmValue();
        }
    }

    public int getPmValue(int ch) {
        return pmValue[ch];
    }

    public int getAmValue(int ch) {
        return amValue[ch];
    }

    public void culcTblValue() {
        switch (lfoWaveForm) {
        case 0:
            pmTblValue = pmTbl0[lfoIdx];
            amTblValue = amTbl0[lfoIdx];
            break;
        case 1:
            if ((lfoIdx & (SIZELFOTBL / 2 - 1)) < SIZELFOTBL / 4) {
                pmTblValue = 128;
                amTblValue = 256;
            } else {
                pmTblValue = -128;
                amTblValue = 0;
            }
            break;
        case 2:
            pmTblValue = pmTbl2[lfoIdx];
            amTblValue = amTbl2[lfoIdx];
            break;
        case 3:
            pmTblValue = pmTbl0[lfoIdx];
            amTblValue = amTbl0[lfoIdx];
            break;
        }
    }

    public void culcPmValue(int ch) {
        if (pmTblValue >= 0) {
            pmValue[ch] = ((pmTblValue * pmdPmsmul[ch]) >> (7 + 5)) << pmsshl[ch];
        } else {
            pmValue[ch] = -((((-pmTblValue) * pmdPmsmul[ch]) >> (7 + 5)) << pmsshl[ch]);
        }
    }

    public void culcAmValue(int ch) {
        amValue[ch] = (((amTblValue * amd) >> 7) << ams[ch]) & (int) 0x7FFFFFFF;
    }

    public void culcAllPmValue() {
        for (int ch = 0; ch < Global.N_CH; ++ch) {
            culcPmValue(ch);
        }
    }

    public void culcAllAmValue() {
        for (int ch = 0; ch < Global.N_CH; ++ch) {
            culcAmValue(ch);
        }
    }
}
