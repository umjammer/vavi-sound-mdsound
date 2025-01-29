/*
 * - You are free to modify this source, make the modified versions public,
 *   incorporate it into self-made software, and distribute that software.
 *   There is no need to report this to m_puusan.
 * - m_puusan assumes no responsibility for any damages (including loss of profits
 *   or data, or other monetary losses) arising from the use or inability to use this software.
 *
 * http://mpuusan.web.fc2.com/x68sound/index.htm
 */

package mdsound.x68sound;


/**
 * Lfo.
 *
 * @author m_puusan
 */
public class Lfo {

    private static final Global global = Global.getInstance();

    private static final int SIZELFOTBL = 512; // 2^9
    private static final int SIZELFOTBL_BITS = 9;
    private static final int LFOPRECISION = 4096; // 2^12
    //#define PMTBLMAXVAL (128)
    //#define PMTBLMAXVAL_BITS (7)
    //#define AMTBLMAXVAL (256)
    //#define AMTBLMAXVAL_BITS (8)
    //#define LFOTIMECYCLE  1073741824  // 2^30
    //#define LFOTIMECYCLE_BITS 30
    //#define LFORNDTIMECYCLE  (LFOTIMECYCLE>>8) // 2^22
    //#define CYCLE2PMAM (30-8) // log2(LFOTIMECYCLE/SIZEPMAMTBL)
    //#define LFOHZ  0.0009313900811
    //int  LFOSTEPTBL[256];
    //int  LFOSTEPTBL3[256]; // Wave form 3 用
    //short PMSTBL[8]={ 0,1,2,4,8,16,64,128 };
    private static final int[] PMSMUL = new int[] {0, 1, 2, 4, 8, 16, 32, 32};
    private static final int[] PMSSHL = new int[] {0, 0, 0, 0, 0, 0, 1, 2};

    /** 0, 1, 2, 4, 8, 16, 32, 32 */
    private final int[] pmsMul = new int[Global.N_CH];
    /** 0, 0, 0, 0, 0,  0,  1,  2 */
    private final int[] pmsShl = new int[Global.N_CH];
    /** Left shift count 31(0), 0(1), 1(2), 2(3) */
    private final int[] ams = new int[Global.N_CH];
    /** Pmd*Pmsmul[] */
    private final int[] pmdPmsMul = new int[Global.N_CH];
    private int pmd;
    private int amd;

    /** 0: LFO stopped 1: LFO operating */
    private int lfoStartingFlag;
    /** LFO t overflow value */
    private int lfoOverFlow;
    /** LFO only t */
    private int lfoTime;
    /** LFO only Δt */
    private int lfoTimeAdd;
    /** Index value into the LFO table */
    private int lfoIdx;
    /** LFO cycle fine adjustment counter (values range from 0 to 15) */
    private int lfoSmallCounter;
    /** Step value for LFO cycle fine adjustment counter (16 to 31) */
    private int lfoSmallCounterStep;
    /** LFO frequency setting value LFRQ */
    private int lFrq;
    /** LFO wave form */
    private int lfoWaveForm;

    private int pmTblValue, amTblValue;
    private final int[] pmValue = new int[Global.N_CH];
    private final int[] amValue = new int[Global.N_CH];

    private final int[] pmTbl0 = new int[SIZELFOTBL]; // sbyte
    private final int[] pmTbl2 = new int[SIZELFOTBL]; // sbyte
    private final int[] amTbl0 = new int[SIZELFOTBL]; // byte
    private final int[] amTbl2 = new int[SIZELFOTBL]; // byte

    public Lfo() {
        for (int i = 0; i < Global.N_CH; ++i) {
            pmsMul[i] = 0;
            pmsShl[i] = 0;
            ams[i] = 31;
            pmdPmsMul[i] = 0;

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
        lFrq = 0;
        lfoWaveForm = 0;

        pmTblValue = 0;
        amTblValue = 255;

        // PM Wave Form 0,3
        for (int i = 0; i <= 127; ++i) {
            pmTbl0[i] = i & 0xff;
            pmTbl0[i + 128] = (i - 127) & 0xff;
            pmTbl0[i + 256] = i & 0xff;
            pmTbl0[i + 384] = (i - 127) & 0xff;
        }
        // AM Wave Form 0,3
        for (int i = 0; i <= 255; ++i) {
            amTbl0[i] = 255 - i;
            amTbl0[i + 256] = 255 - i;
        }

        // PM Wave Form 2
        for (int i = 0; i <= 127; ++i) {
            pmTbl2[i] = i & 0xff;
            pmTbl2[i + 128] = (127 - i) & 0xff;
            pmTbl2[i + 256] = (-i) & 0xff;
            pmTbl2[i + 384] = (i - 127) & 0xff;
        }
        // AM Wave Form 2
        for (int i = 0; i <= 255; ++i) {
            amTbl2[i] = 255 - i;
            amTbl2[i + 256] = i;
        }
    }

    public void init() {
        lfoTimeAdd = LFOPRECISION * global.opmRate / Global.sampleRate;

        lfoSmallCounter = 0;

        setLFRQ(0);
        setPMDAMD(0);
        setPMDAMD(128 + 0);
        setWaveForm(0);
        for (int ch = 0; ch < Global.N_CH; ++ch) {
            setPMSAMS(ch, 0);
        }
        lfoReset();
        lfoStart();
    }

    public void initSampleRate() {
        lfoTimeAdd = LFOPRECISION * global.opmRate / Global.sampleRate;
    }

    public void lfoReset() {
        lfoStartingFlag = 0;

        // LfoTime is not reset!!
        lfoIdx = 0;

        calcTblValue();
        calcAllPmValue();
        calcAllAmValue();
    }

    public void lfoStart() {
        lfoStartingFlag = 1;
    }

    public void setLFRQ(int n) {
        lFrq = n & 255;

        lfoSmallCounterStep = 16 + (lFrq & 15);
        int shift = 15 - (lFrq >> 4);
        if (shift == 0) {
            shift = 1;
            lfoSmallCounterStep <<= 1;
        }
        lfoOverFlow = (8 << shift) * LFOPRECISION;

        // LfoTime is reset
        lfoTime = 0;
    }

    public void setPMDAMD(int n) {
        if ((n & 0x80) != 0) {
            pmd = n & 0x7f;
            int ch;
            for (ch = 0; ch < Global.N_CH; ++ch) {
                pmdPmsMul[ch] = pmd * pmsMul[ch];
            }
            calcAllPmValue();
        } else {
            amd = n & 0x7f;
            calcAllAmValue();
        }
    }

    public void setWaveForm(int n) {
        lfoWaveForm = n & 3;

        calcTblValue();
        calcAllPmValue();
        calcAllAmValue();
    }

    public void setPMSAMS(int ch, int n) {
        int pms = (n >> 4) & 7;
        pmsMul[ch] = PMSMUL[pms];
        pmsShl[ch] = PMSSHL[pms];
        pmdPmsMul[ch] = pmd * pmsMul[ch];
        calcPmValue(ch);

        ams[ch] = ((n & 3) - 1) & 31;
        calcAmValue(ch);
    }

    public void update() {
        if (lfoStartingFlag == 0) {
            return;
        }

        lfoTime += lfoTimeAdd;
        // 2008.4.19 sam modified Save the residual to reduce the error of LfoTime
        //if (LfoTime >= LfoOverFlow) {
        // LfoTime = 0;
        while (lfoTime >= lfoOverFlow) {
            lfoTime -= lfoOverFlow;
            lfoSmallCounter += lfoSmallCounterStep;
            switch (lfoWaveForm) {
            case 0: {
                int idxAdd = lfoSmallCounter >> 4;
                lfoIdx = (lfoIdx + idxAdd) & (SIZELFOTBL - 1);
                pmTblValue = pmTbl0[lfoIdx];
                amTblValue = amTbl0[lfoIdx];
                break;
            }
            case 1: {
                int idxAdd = lfoSmallCounter >> 4;
                lfoIdx = (lfoIdx + idxAdd) & (SIZELFOTBL - 1);
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
                int idxAdd = lfoSmallCounter >> 4;
                lfoIdx = (lfoIdx + idxAdd + idxAdd) & (SIZELFOTBL - 1);
                pmTblValue = pmTbl2[lfoIdx];
                amTblValue = amTbl2[lfoIdx];
                break;
            }
            case 3: {
                lfoIdx = global.irnd() >> (32 - SIZELFOTBL_BITS);
                pmTblValue = pmTbl0[lfoIdx];
                amTblValue = amTbl0[lfoIdx];
                break;
            }
            }
            lfoSmallCounter &= 15;

            calcAllPmValue();
            calcAllAmValue();
        }
    }

    public int getPmValue(int ch) {
        return pmValue[ch];
    }

    public int getAmValue(int ch) {
        return amValue[ch];
    }

    public void calcTblValue() {
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

    public void calcPmValue(int ch) {
        if (pmTblValue >= 0) {
            pmValue[ch] = ((pmTblValue * pmdPmsMul[ch]) >> (7 + 5)) << pmsShl[ch];
        } else {
            pmValue[ch] = -((((-pmTblValue) * pmdPmsMul[ch]) >> (7 + 5)) << pmsShl[ch]);
        }
    }

    public void calcAmValue(int ch) {
        amValue[ch] = (((amTblValue * amd) >> 7) << ams[ch]) & 0x7fff_ffff;
    }

    public void calcAllPmValue() {
        for (int ch = 0; ch < Global.N_CH; ++ch) {
            calcPmValue(ch);
        }
    }

    public void calcAllAmValue() {
        for (int ch = 0; ch < Global.N_CH; ++ch) {
            calcAmValue(ch);
        }
    }
}
