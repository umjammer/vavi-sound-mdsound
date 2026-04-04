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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;


/**
 * Opm.
 *
 * @author m_puusan
 */
public class Opm {

    private static final Global global = Global.getInstance();
//    private WinAPI.WAVEFORMATEX wfx;

    private static final int CMNDBUFSIZE = 65535;

    //#define RES (20)
    //#define NDATA (44100/5)
    private static final int PCMBUFSIZE = 65536;
    //#define DELAY (1000/5)

//#if C86CTL
//    // Definitions for c86ctl
//    typedef HRESULT(WINAPI C86CtlCreateInstance)(REFIID, LPVOID);
//#endif

    public final String author;

    /** Operator 0-31 */
    private final Op[][] op = {
            new Op[4], new Op[4], new Op[4], new Op[4],
            new Op[4], new Op[4], new Op[4], new Op[4]
    };
    /** Envelope Counter 1 (0,1,2,3,4,5,6,...) */
    private int envCounter1;
    /** Envelope Counter 2 (3,2,1,3,2,1,3,2,...) */
    private int envCounter2;
//    /** algorithm 0～7 */
//    int con[N_CH];
    /** 0: Silence -1: Output */
    private final int[][] pan = {new int[Global.N_CH], new int[Global.N_CH]};
//    /** 0, 1, 2, 4, 10, 20, 80, 140 */
//    int pms[N_CH];
//    /** Right shift count 31(0), 2(1), 1(2), 0(3) */
//    int ams[N_CH];
//    int pmd;
//    int amd;
//    int pmspmd[N_CH]; //pms[]* pmd
    private final Lfo lfo;
    private final int[] SLOTTBL = new int[8 * 4];

    /** Initialize in the constructor */
    private final int[][] cmndBuf; //[CMNDBUFSIZE + 1][2];
    private final Object numCmndLockObj = new Object();
    private long numCmnd;
    private int cmndReadIdx, cmndWriteIdx;
    private int cmndRate;

    //short PcmBuf[PCMBUFSIZE][2];
    private short[] pcmBuf; // ?
    public int pcmBufSize;
    private int _pcmBufPtr;

    public synchronized int getPcmBufPtr() {
        return _pcmBufPtr;
    }

    public synchronized void setPcmBufPtr(int value) {
        _pcmBufPtr = value;
    }

    public int TimerID = 0;

//    /** LFO t overflow value */
//    int LfoOverTime;
//    /** LFO only t */
//    int LfoTime;
//    /** LFO Random Wave Only t */
//    int LfoRndTime;
//    /** LFO frequency setting value LFRQ */
//    int Lfrq;
//    /** LFO wave form */
//    int LfoWaveForm;
//    void CalcLfoStep();
//    void SetConnection(int ch, int alg);
    private final int[][] opOut = new int[][] {new int[1], new int[1], new int[1], new int[1], new int[1], new int[1], new int[1], new int[1]};
    private final int[] opOutDummy = new int[1];

    /** value of OPMreg$10 */
    private int timerAReg10;
    /** value of OPMreg$11 */
    private int timerAReg11;
    /** Timer A overflow setting */
    private int timerA;
    /** Timer A counter value */
    private int timerACounter;
    /** Timer B overflow setting value */
    private int timerB;
    /** Timer B counter value */
    private int timerBCounter;
    /** Timer control register (lower 4 bits of OPMreg$14 + 7 bits) */
    private int timerReg;
    /** OPM status register (lowest 2 bits of $E90003) */
    private int statReg;
    /** OPM interrupt callback function */
    private Runnable opmIntProc;

//    public double inpopmbuf_dummy;
    private final short[] inpOpmBuf0 = new short[Global.OPMLPF_COL * 2];
    private final short[] InpOpmBuf1 = new short[Global.OPMLPF_COL * 2];
    private int inpOpmIdx;
    private int opmLpfIdx;
    private short[][] opmLPFpBuf;
    private int opmLPFpPtr;
//    public double inpadpcmbuf_dummy;
//    short InpAdpcmBuf0[ADPCMLPF_COL*2],InpAdpcmBuf1[ADPCMLPF_COL*2];
//    int InpAdpcm_idx;
//    int AdpcmLPFidx; short *AdpcmLPFp;
    private final int[] outOpm = new int[2];
    private final int[] inpInpOpm = new int[2];
    private final int[] inpOpm = new int[2];
    private final int[] inpInpOpmPrev = new int[2];
    private final int[] inpOpmPrev = new int[2];
    private final int[] inpInpOpmPrev2 = new int[2];
    private final int[] inpOpmPrev2 = new int[2];
    private final int[] opmHpfInp = new int[2];
    private final int[] opmHpfInpPrev = new int[2];
    private final int[] opmHpfOut = new int[2];
    private final int[] outInpAdpcm = new int[2];
    private final int[] outInpAdpcmPrev = new int[2];
    private final int[] outInpAdpcmPrev2 = new int[2];
    private final int[] outOutAdpcm = new int[2];
    private final int[] outOutAdpcmPrev = new int[2];
    /** Treble filter 2 buffer */
    private final int[] outOutAdpcmPrev2 = new int[2];
    private final int[] outInpOutAdpcm = new int[2];
    private final int[] outInpOutAdpcmPrev = new int[2];
    private final int[] outInpOutAdpcmPrev2 = new int[2];
    private final int[] outOutInpAdpcm = new int[2];
    /** Treble filter 3 buffer */
    private final int[] outOutInpAdpcmPrev = new int[2];

    private int ppiReg;
    /** ADPCM clock switching (0:8MHz 1:4Mhz) */
    private int adpcmBaseClock;
//    inline void setAdpcmRate();

    /** Currently specified OPM register number */
    private int opmRegNo;
    /** Backup OPM register number */
    private int opmRegNoBackup;
    /** Multimedia Timer Interrupt */
    private Runnable betwIntProc;
    /** WaveFunc */
    private Supplier<Integer> waveFunc;

    /** Flag to use OPM */
    private int useOpmFlag;
    /** Flag to use ADPCM */
    private int useAdpcmFlag;
    private int _betw;
    private int _pcmbuf;
    private int _late;
    private int _rev;

    /** 0: Not working 1: During X68Sound_Start 2: During X68Sound_PcmStart */
    private int dousaMode;

    /** Channel Mask */
    private int opmChMask;

    //public:
    private final Adpcm adpcm;
    //private:
    private final Pcm8[] pcm8 = new Pcm8[Global.PCM8_NCH];

//    /** volume x/256 */
//    int TotalVolume;

    public void setAdpcmRate() {
        adpcm.setAdpcmRate(Global.ADPCMRATETBL[adpcmBaseClock][(ppiReg >> 2) & 3]);
    }

    public void setConnection(int ch, int alg) {
        switch (alg) {
        case 0:
            op[ch][0].out1 = op[ch][1].inp;
            op[ch][0].out2 = opOutDummy;
            op[ch][0].out3 = opOutDummy;
            op[ch][1].out1 = op[ch][2].inp;
            op[ch][2].out1 = op[ch][3].inp;
            op[ch][3].out1 = opOut[ch];
            break;
        case 1:
            op[ch][0].out1 = op[ch][2].inp;
            op[ch][0].out2 = opOutDummy;
            op[ch][0].out3 = opOutDummy;
            op[ch][1].out1 = op[ch][2].inp;
            op[ch][2].out1 = op[ch][3].inp;
            op[ch][3].out1 = opOut[ch];
            break;
        case 2:
            op[ch][0].out1 = op[ch][3].inp;
            op[ch][0].out2 = opOutDummy;
            op[ch][0].out3 = opOutDummy;
            op[ch][1].out1 = op[ch][2].inp;
            op[ch][2].out1 = op[ch][3].inp;
            op[ch][3].out1 = opOut[ch];
            break;
        case 3:
            op[ch][0].out1 = op[ch][1].inp;
            op[ch][0].out2 = opOutDummy;
            op[ch][0].out3 = opOutDummy;
            op[ch][1].out1 = op[ch][3].inp;
            op[ch][2].out1 = op[ch][3].inp;
            op[ch][3].out1 = opOut[ch];
            break;
        case 4:
            op[ch][0].out1 = op[ch][1].inp;
            op[ch][0].out2 = opOutDummy;
            op[ch][0].out3 = opOutDummy;
            op[ch][1].out1 = opOut[ch];
            op[ch][2].out1 = op[ch][3].inp;
            op[ch][3].out1 = opOut[ch];
            break;
        case 5:
            op[ch][0].out1 = op[ch][1].inp;
            op[ch][0].out2 = op[ch][2].inp;
            op[ch][0].out3 = op[ch][3].inp;
            op[ch][1].out1 = opOut[ch];
            op[ch][2].out1 = opOut[ch];
            op[ch][3].out1 = opOut[ch];
            break;
        case 6:
            op[ch][0].out1 = op[ch][1].inp;
            op[ch][0].out2 = opOutDummy;
            op[ch][0].out3 = opOutDummy;
            op[ch][1].out1 = opOut[ch];
            op[ch][2].out1 = opOut[ch];
            op[ch][3].out1 = opOut[ch];
            break;
        case 7:
            op[ch][0].out1 = opOut[ch];
            op[ch][0].out2 = opOutDummy;
            op[ch][0].out3 = opOutDummy;
            op[ch][1].out1 = opOut[ch];
            op[ch][2].out1 = opOut[ch];
            op[ch][3].out1 = opOut[ch];
            break;
        }
    }

    public int setOpmWait(int wait) {
        if (wait != -1) {
            Global.OpmWait = wait;
            calcCmndRate();
        }
        return Global.OpmWait;
    }

    private void calcCmndRate() {
        if (Global.OpmWait != 0) {
            cmndRate = (4096 * 160 / Global.OpmWait);
            if (cmndRate == 0) {
                cmndRate = 1;
            }
        } else {
            cmndRate = 4096 * CMNDBUFSIZE;
        }
    }

    public void reset() {
        // Initialize the OPM command buffer
        synchronized (numCmndLockObj) {
            numCmnd = 0;
            cmndReadIdx = cmndWriteIdx = 0;
        }

        calcCmndRate();

        // Clear the treble filter buffer
        inpInpOpm[0] = inpInpOpm[1] =
                inpInpOpmPrev[0] = inpInpOpmPrev[1] = 0;
        inpInpOpmPrev2[0] = inpInpOpmPrev2[1] = 0;
        inpOpm[0] = inpOpm[1] =
                inpOpmPrev[0] = inpOpmPrev[1] =
                        inpOpmPrev2[0] = inpOpmPrev2[1] =
                                outOpm[0] = outOpm[1] = 0;

        for (int i = 0; i < Global.OPMLPF_COL * 2; ++i) {
            inpOpmBuf0[i] = InpOpmBuf1[i] = 0;
        }
        inpOpmIdx = 0;
        opmLpfIdx = 0;
        opmLPFpBuf = Global.OPMLOWPASS;
        opmLPFpPtr = 0;

        opmHpfInp[0] = opmHpfInp[1] =
                opmHpfInpPrev[0] = opmHpfInpPrev[1] =
                        opmHpfOut[0] = opmHpfOut[1] = 0;
//        {
//            int i, j;
//            for (i = 0; i < ADPCMLPF_COL * 2; ++i) {
//                InpAdpcmBuf0[i] = InpAdpcmBuf1[i] = 0;
//            }
//            InpAdpcm_idx = 0;
//            AdpcmLPFidx = 0;
//            AdpcmLPFp = ADPCMLOWPASS[0];
//        }

        outInpAdpcm[0] = outInpAdpcm[1] =
                outInpAdpcmPrev[0] = outInpAdpcmPrev[1] =
                        outInpAdpcmPrev2[0] = outInpAdpcmPrev2[1] =
                                outOutAdpcm[0] = outOutAdpcm[1] =
                                        outOutAdpcmPrev[0] = outOutAdpcmPrev[1] =
                                                outOutAdpcmPrev2[0] = outOutAdpcmPrev2[1] =
                                                        0;
        outInpOutAdpcm[0] = outInpOutAdpcm[1] =
                outInpOutAdpcmPrev[0] = outInpOutAdpcmPrev[1] =
                        outInpOutAdpcmPrev2[0] = outInpOutAdpcmPrev2[1] =
                                outOutInpAdpcm[0] = outOutInpAdpcm[1] =
                                        outOutInpAdpcmPrev[0] = outOutInpAdpcmPrev[1] =
                                                0;

        // Initialize all operators
        for (int ch = 0; ch < Global.N_CH; ++ch) {
            op[ch][0] = new Op();
            op[ch][1] = new Op();
            op[ch][2] = new Op();
            op[ch][3] = new Op();
            op[ch][0].init();
            op[ch][1].init();
            op[ch][2].init();
            op[ch][3].init();
            //con[ch] = 0;
            setConnection(ch, 0);
            pan[0][ch] = pan[1][ch] = 0;
        }

        // Initialize envelope counter
        envCounter1 = 0;
        envCounter2 = 3;

        // LFO Initialization
        lfo.init();

        // Reset the PcmBuf pointer
        _pcmBufPtr = 0;
        //PcmBufSize = PCMBUFSIZE;

        // Timer related initialization
        timerAReg10 = 0;
        timerAReg11 = 0;
        timerA = 1024 - 0;
        timerACounter = 0;
        timerB = (256 - 0) << (10 - 6);
        timerBCounter = 0;
        timerReg = 0;
        statReg = 0;
        opmIntProc = null;

        ppiReg = 0x0B;
        adpcmBaseClock = 0;

        adpcm.init();

        for (int i = 0; i < Global.PCM8_NCH; ++i) {
            if (pcm8[i] == null) {
                pcm8[i] = new Pcm8();
            }
            pcm8[i].init();
        }

        global.totalVolume = 256;
        //TotalVolume = 192;

        opmRegNo = 0;
        betwIntProc = null;
        waveFunc = null;

        global.memRead = global::memReadDefault;

//#if C86CTL
//        if (pChipOPM)
//            pChipOPM.reset();
//#endif

//#if ROMEO
//        if (UseOpmFlag == 2) {
//            juliet_YM2151Reset();
//            juliet_YM2151Mute(0);
//        }
//#endif

        //UseOpmFlag = 0;
        //UseAdpcmFlag = 0;
    }

    private void resetSampleRate() {
        calcCmndRate();

        // Clear the treble filter buffer
        inpInpOpm[0] = inpInpOpm[1] =
        inpInpOpmPrev[0] = inpInpOpmPrev[1] = 0;
        inpInpOpmPrev2[0] = inpInpOpmPrev2[1] = 0;
        inpOpm[0] = inpOpm[1] =
        inpOpmPrev[0] = inpOpmPrev[1] =
        inpOpmPrev2[0] = inpOpmPrev2[1] =
        outOpm[0] = outOpm[1] = 0;
        {
            for (int i = 0; i < Global.OPMLPF_COL * 2; ++i) {
                inpOpmBuf0[i] = InpOpmBuf1[i] = 0;
            }
            inpOpmIdx = 0;
            opmLpfIdx = 0;
            opmLPFpBuf = Global.OPMLOWPASS;
            opmLPFpPtr = 0;
        }
        opmHpfInp[0] = opmHpfInp[1] =
                opmHpfInpPrev[0] = opmHpfInpPrev[1] =
                        opmHpfOut[0] = opmHpfOut[1] = 0;
//        {
//            int i, j;
//            for (i = 0; i < ADPCMLPF_COL * 2; ++i) {
//                InpAdpcmBuf0[i] = InpAdpcmBuf1[i] = 0;
//            }
//            InpAdpcm_idx = 0;
//            AdpcmLPFidx = 0;
//            AdpcmLPFp = ADPCMLOWPASS[0];
//        }

        outInpAdpcm[0] = outInpAdpcm[1] =
        outInpAdpcmPrev[0] = outInpAdpcmPrev[1] =
        outInpAdpcmPrev2[0] = outInpAdpcmPrev2[1] =
        outOutAdpcm[0] = outOutAdpcm[1] =
        outOutAdpcmPrev[0] = outOutAdpcmPrev[1] =
        outOutAdpcmPrev2[0] = outOutAdpcmPrev2[1] =
        0;
        outInpOutAdpcm[0] = outInpOutAdpcm[1] =
        outInpOutAdpcmPrev[0] = outInpOutAdpcmPrev[1] =
        outInpOutAdpcmPrev2[0] = outInpOutAdpcmPrev2[1] =
        outOutInpAdpcm[0] = outOutInpAdpcm[1] =
        outOutInpAdpcmPrev[0] = outOutInpAdpcmPrev[1] =
        0;

        // Initialize all operators
        for (int ch = 0; ch < Global.N_CH; ++ch) {
            op[ch][0].initSampleRate();
            op[ch][1].initSampleRate();
            op[ch][2].initSampleRate();
            op[ch][3].initSampleRate();
        }

        // Initialize LFO
        lfo.initSampleRate();

        // Reset the PcmBuf pointer
        _pcmBufPtr = 0;
        //PcmBufSize = PCMBUFSIZE;

        //PpiReg = 0x0B;
        // AdpcmBaseClock = 0;

        adpcm.initSampleRate();
    }

    public Opm() {
        lfo = new Lfo();
        adpcm = new Adpcm();

        cmndBuf = new int[CMNDBUFSIZE + 1][];
        for (int i = 0; i < CMNDBUFSIZE + 1; i++) {
            cmndBuf[i] = new int[2];
        }

        author = "m_puusan";

        //hwo = null;
        pcmBuf = null;
        TimerID = 0; // null;

        dousaMode = 0;
        opmChMask = 0;

        List<BiConsumer<Integer, Integer>> a = Arrays.<BiConsumer<Integer, Integer>>asList(
                this::dmy, this::ExeCmd_LfoReset, this::dmy, this::dmy,                                      // 00-03
                this::dmy, this::dmy, this::dmy, this::dmy,                                                  // 04-07
                this::ExeCmd_KON, this::dmy, this::dmy, this::dmy,                                           // 08-0B
                this::dmy, this::dmy, this::dmy, this::ExeCmd_NeNfrq,                                        // 0C-0F
                this::dmy, this::dmy, this::dmy, this::dmy,                                                  // 10-13
                this::dmy, this::dmy, this::dmy, this::dmy,                                                  // 14-17
                this::ExeCmd_Lfrq, this::ExeCmd_PmdAmd, this::dmy, this::ExeCmd_WaveForm,                    // 18-1B
                this::dmy, this::dmy, this::dmy, this::dmy,                                                  // 1C-1F
                this::ExeCmd_PanFlCon, this::ExeCmd_PanFlCon, this::ExeCmd_PanFlCon, this::ExeCmd_PanFlCon,  // 20-23
                this::ExeCmd_PanFlCon, this::ExeCmd_PanFlCon, this::ExeCmd_PanFlCon, this::ExeCmd_PanFlCon,  // 24-27
                this::ExeCmd_Kc, this::ExeCmd_Kc, this::ExeCmd_Kc, this::ExeCmd_Kc,                          // 28-2B
                this::ExeCmd_Kc, this::ExeCmd_Kc, this::ExeCmd_Kc, this::ExeCmd_Kc,                          // 2C-2F
                this::ExeCmd_Kf, this::ExeCmd_Kf, this::ExeCmd_Kf, this::ExeCmd_Kf,                          // 30-33
                this::ExeCmd_Kf, this::ExeCmd_Kf, this::ExeCmd_Kf, this::ExeCmd_Kf,                          // 34-37
                this::ExeCmd_PmsAms, this::ExeCmd_PmsAms, this::ExeCmd_PmsAms, this::ExeCmd_PmsAms,          // 38-3B
                this::ExeCmd_PmsAms, this::ExeCmd_PmsAms, this::ExeCmd_PmsAms, this::ExeCmd_PmsAms,          // 3C-3F
                this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul,          // 40-43
                this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul,          // 44-47
                this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul,          // 48-4B
                this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul,          // 4C-4F
                this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul,          // 50-53
                this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul,          // 54-57
                this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul,          // 58-5B
                this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul,          // 5C-5F
                this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl,                          // 60-63
                this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl,                          // 64-67
                this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl,                          // 68-6B
                this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl,                          // 6C-6F
                this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl,                          // 70-73
                this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl,                          // 74-77
                this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl,                          // 78-7B
                this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl,                          // 7C-7F
                this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr,                  // 80-83
                this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr,                  // 84-87
                this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr,                  // 88-8B
                this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr,                  // 8C-8F
                this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr,                  // 90-93
                this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr,                  // 94-97
                this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr,                  // 98-9B
                this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr,                  // 9C-9F
                this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r,          // A0-A3
                this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r,          // A4-A7
                this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r,          // A8-AB
                this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r,          // AC-AF
                this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r,          // B0-B3
                this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r,          // B4-B7
                this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r,          // B8-BB
                this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r,          // BC-BF
                this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r,          // C0-C3
                this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r,          // C4-C7
                this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r,          // C8-CB
                this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r,          // CC-CF
                this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r,          // D0-D3
                this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r,          // D4-D7
                this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r,          // D8-DB
                this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r,          // DC-DF
                this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr,              // E0-E3
                this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr,              // E4-E7
                this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr,              // E8-EB
                this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr,              // EC-EF
                this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr,              // F0-F3
                this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr,              // F4-F7
                this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr,              // F8-FB
                this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr               // FC-FF
        );
        cmdTbl = a.toArray(BiConsumer[]::new);
        //new BiConsumer<Byte, Byte>[]

//#if C86CTL
//        // load C86CTL
//        pChipBase = null;
//        pChipOPM = null;
//
//        hC86DLL = .LoadLibrary("c86ctl.dll");
//        if (hC86DLL) {
//            C86CtlCreateInstance pCI = (C86CtlCreateInstance).GetProcAddress(hC86DLL, "CreateInstance");
//            if (pCI) pCI(c86ctl.IID_IRealChipBase, (byte[][]) pChipBase);
//        }
//        // Initializing C86CTL & Searching for OPM Modules
//        if (pChipBase) {
//            pChipBase.initialize();
//            int num = pChipBase.getNumberOfChip();
//            for (int i = 0; i < num; i++) {
//                c86ctl.IGimic2 pGimic = 0;
//                pChipBase.getChipInterface(i, c86ctl.IID_IGimic2, (byte[][]) pGimic);
//                if (pGimic) {
//                    c86ctl.ChipType chipType;
//                    pGimic.getModuleType(chipType);
//                    if (chipType == c86ctl.CHIP_OPM) {
//                        pGimic.QueryInterface(c86ctl.IID_IRealChip2, (byte[][]) pChipOPM);
//                        pGimic.Release();
//                        break;
//                    }
//                    pGimic.Release();
//                }
//            }
//        }
//#endif
    }

    private void makeTable() {

        // Create a sin table
        for (int i = 0; i < Global.SIZESINTBL; ++i) {
            global.SINTBL[i] = (short) (Math.sin(2.0 * Math.PI * (i + 0.0) / Global.SIZESINTBL) * (Global.MAXSINVAL) + 0.5);
        }

        // Create an envelope value → alpha conversion table
        for (int i = 0; i <= Global.ALPHAZERO + Global.SIZEALPHATBL; ++i) {
            global.ALPHATBL[i] = 0;
        }
        for (int i = 17; i <= Global.SIZEALPHATBL; ++i) {
            global.ALPHATBL[Global.ALPHAZERO + i] = (int) (Math.floor(
                    Math.pow(2.0, -((Global.SIZEALPHATBL) - i) * (128.0 / 8.0) / (Global.SIZEALPHATBL)) *
                            1.0 * 1.0 * Global.PRECISION + 0.0));
        }

        // Create an envelope value → Noiseα conversion table
        for (int i = 0; i <= Global.ALPHAZERO + Global.SIZEALPHATBL; ++i) {
            global.NOISEALPHATBL[i] = 0;
        }
        for (int i = 17; i <= Global.SIZEALPHATBL; ++i) {
            global.NOISEALPHATBL[Global.ALPHAZERO + i] = (int) Math.floor(i * 1.0 / (Global.SIZEALPHATBL) *
                            1.0 * 0.25 * Global.PRECISION + 0.0); // Noise volume is 1/4 of Op.
        }

        // Create a D1L → D1l conversion table
        for (int i = 0; i < 15; ++i) {
            global.D1LTBL[i] = i * 2;
        }
        global.D1LTBL[15] = (15 + 16) * 2;

        // Create a C1 <. M2 transposition table
        for (int slot = 0; slot < 8; ++slot) {
            SLOTTBL[slot] = slot * 4;
            SLOTTBL[slot + 8] = slot * 4 + 2;
            SLOTTBL[slot + 16] = slot * 4 + 1;
            SLOTTBL[slot + 24] = slot * 4 + 3;
        }

        // Create a Pitch → Δt conversion table
        for (int oct = 0; oct <= 10; ++oct) {
            for (int notekf = 0; notekf < 12 * 64; ++notekf) {
                int step;
                if (oct >= 3) {
                    step = Global.STEPTBL_O2[notekf] << (oct - 3);
                } else {
                    step = Global.STEPTBL_O2[notekf] >> (3 - oct);
                }
                global.STEPTBL[oct * 12 * 64 + notekf] = (int) (step * 64 * (long) (global.opmRate) / Global.sampleRate);
            }
        }

        for (int i = 0; i <= 128 + 4 - 1; ++i) {
            global.DT1TBL[i] = (int) (Global.DT1TBL_org[i] * 64 * (long) (global.opmRate) / Global.sampleRate);
        }
    }

    public int peekOpm() {
        return statReg;
    }

    public void setRegNo(int no) {
        opmRegNo = no;
    }

    public void pokeOpm(int data) {
        if (useOpmFlag < 2) {

//#if C86CTL
//        if (pChipOPM)
//            pChipOPM.out(OpmRegNo, data);
//        else
//#endif
            synchronized (numCmndLockObj) {
                if (numCmnd < CMNDBUFSIZE) {
                    cmndBuf[cmndWriteIdx][0] = opmRegNo;
                    cmndBuf[cmndWriteIdx][1] = data;
                    ++cmndWriteIdx;
                    cmndWriteIdx &= CMNDBUFSIZE;
                    ++numCmnd;
                    //_InterlockedIncrement(&NumCmnd);
                }
            }

        } else {
            synchronized (numCmndLockObj) {
                if (numCmnd < ((CMNDBUFSIZE + 1) / 4) - 1) {
                    //DWORD time = timeGetTime();
                    cmndBuf[cmndWriteIdx][0] = 0; //(byte)(time >> 24);
                    cmndBuf[cmndWriteIdx][1] = 0; //(byte)(time >> 16);
                    cmndBuf[cmndWriteIdx][2] = 0; //(byte)(time >> 8);
                    cmndBuf[cmndWriteIdx][3] = 0; //(byte)(time >> 0);
                    cmndBuf[cmndWriteIdx][4] = opmRegNo;
                    cmndBuf[cmndWriteIdx][5] = data;
                    cmndWriteIdx += 4;
                    cmndWriteIdx &= CMNDBUFSIZE;
                    ++numCmnd;
                    //_InterlockedIncrement(&NumCmnd);
                }
            }
        }

        switch (opmRegNo) {
        case 0x10:
            // TimerA

            timerAReg10 = data;
            timerA = 1024 - ((timerAReg10 << 2) + timerAReg11);

            break;
        case 0x11:
            // TimerA

//            if (OpmRegNo == 0x10) {
//                TimerAreg10 = data;
//            } else {
                timerAReg11 = data & 3;
//            }
            timerA = 1024 - ((timerAReg10 << 2) + timerAReg11);

            break;

        case 0x12:
            // TimerB

            timerB = (256 - data) << (10 - 6);

            break;

        case 0x14:
            // Timer Control Register

            //while (_InterlockedCompareExchange(&TimerSemapho, 1, 0) == 1) ;

            timerReg = data & 0x8F;
            statReg &= 0xff - ((data >> 4) & 3);

            global.timerSemaphore = 0;

            break;

        case 0x1B:
            // WaveForm

            adpcmBaseClock = data >> 7;
            setAdpcmRate();

            break;
        }
    }

    private void executeCommand() {

        if (useOpmFlag < 2) {
            int rate = 0;
            rate -= cmndRate;
            while (rate < 0) {
                rate += 4096;
                synchronized (numCmndLockObj) {
                    if (numCmnd != 0) {
                        int regNo, data;
                        regNo = cmndBuf[cmndReadIdx][0];
                        data = cmndBuf[cmndReadIdx][1];
                        ++cmndReadIdx;
                        cmndReadIdx &= CMNDBUFSIZE;
                        --numCmnd;
                        //_InterlockedDecrement(&NumCmnd);
                        executeCoreCommand(regNo, data);
                    }
                }
            }
        } else {
            synchronized (numCmndLockObj) {
                while (numCmnd != 0) {
                    int t1, t2;
                    int regNo, data;
                    t1 = 0;// timeGetTime();
                    t2 = (cmndBuf[cmndReadIdx][0] * 0x100_0000) +
                            (cmndBuf[cmndReadIdx][1] * 0x1_0000) +
                            (cmndBuf[cmndReadIdx][2] * 0x100) +
                            (cmndBuf[cmndReadIdx][3] * 0x1);
                    t1 -= t2;
                    if (t1 < _late) break;
                    regNo = cmndBuf[cmndReadIdx][4];
                    data = cmndBuf[cmndReadIdx][5];
                    cmndReadIdx += 4;
                    cmndReadIdx &= CMNDBUFSIZE;
                    --numCmnd;
                    //_InterlockedDecrement(&NumCmnd);
                    executeCoreCommand(regNo, data);
                }
            }
        }
    }

    private final BiConsumer<Integer, Integer>[] cmdTbl;

    private void executeCoreCommand(int regNo, int data) {

        cmdTbl[regNo].accept(regNo, data);

//        switch (regNo) {
//            case 0x01:
//                // LFO RESET
//                ExeCmd_LfoReset(data);
//                break;
//
//            case 0x08:
//                // KON
//                ExeCmd_KON(data);
//                break;
//
//            case 0x0F:
//                // NE,NFRQ
//                ExeCmd_NeNfrq(data);
//                break;
//
//            case 0x18:
//                // LFRQ
//                ExeCmd_Lfrq(data);
//                break;
//            case 0x19:
//                // PMD/AMD
//                ExeCmd_PmdAmd(data);
//                break;
//            case 0x1B:
//                // WaveForm
//                ExeCmd_WaveForm(data);
//                break;
//
//            case 0x20:
//            case 0x21:
//            case 0x22:
//            case 0x23:
//            case 0x24:
//            case 0x25:
//            case 0x26:
//            case 0x27:
//                // PAN/FL/CON
//                ExeCmd_PanFlCon(regNo, data);
//                break;
//
//            case 0x28:
//            case 0x29:
//            case 0x2A:
//            case 0x2B:
//            case 0x2C:
//            case 0x2D:
//            case 0x2E:
//            case 0x2F:
//                // KC
//                ExeCmd_Kc(regNo, data);
//                break;
//
//            case 0x30:
//            case 0x31:
//            case 0x32:
//            case 0x33:
//            case 0x34:
//            case 0x35:
//            case 0x36:
//            case 0x37:
//                // KF
//                ExeCmd_Kf(regNo, data);
//                break;
//
//            case 0x38:
//            case 0x39:
//            case 0x3A:
//            case 0x3B:
//            case 0x3C:
//            case 0x3D:
//            case 0x3E:
//            case 0x3F:
//                // PMS/AMS
//                ExeCmd_PmsAms(regNo, data);
//                break;
//
//            case 0x40:
//            case 0x41:
//            case 0x42:
//            case 0x43:
//            case 0x44:
//            case 0x45:
//            case 0x46:
//            case 0x47:
//            case 0x48:
//            case 0x49:
//            case 0x4A:
//            case 0x4B:
//            case 0x4C:
//            case 0x4D:
//            case 0x4E:
//            case 0x4F:
//            case 0x50:
//            case 0x51:
//            case 0x52:
//            case 0x53:
//            case 0x54:
//            case 0x55:
//            case 0x56:
//            case 0x57:
//            case 0x58:
//            case 0x59:
//            case 0x5A:
//            case 0x5B:
//            case 0x5C:
//            case 0x5D:
//            case 0x5E:
//            case 0x5F:
//                // DT1/MUL
//                ExeCmd_Dt1Mul(regNo, data);
//                break;
//
//            case 0x60:
//            case 0x61:
//            case 0x62:
//            case 0x63:
//            case 0x64:
//            case 0x65:
//            case 0x66:
//            case 0x67:
//            case 0x68:
//            case 0x69:
//            case 0x6A:
//            case 0x6B:
//            case 0x6C:
//            case 0x6D:
//            case 0x6E:
//            case 0x6F:
//            case 0x70:
//            case 0x71:
//            case 0x72:
//            case 0x73:
//            case 0x74:
//            case 0x75:
//            case 0x76:
//            case 0x77:
//            case 0x78:
//            case 0x79:
//            case 0x7A:
//            case 0x7B:
//            case 0x7C:
//            case 0x7D:
//            case 0x7E:
//            case 0x7F:
//                // TL
//                ExeCmd_Tl(regNo, data);
//                break;
//
//            case 0x80:
//            case 0x81:
//            case 0x82:
//            case 0x83:
//            case 0x84:
//            case 0x85:
//            case 0x86:
//            case 0x87:
//            case 0x88:
//            case 0x89:
//            case 0x8A:
//            case 0x8B:
//            case 0x8C:
//            case 0x8D:
//            case 0x8E:
//            case 0x8F:
//            case 0x90:
//            case 0x91:
//            case 0x92:
//            case 0x93:
//            case 0x94:
//            case 0x95:
//            case 0x96:
//            case 0x97:
//            case 0x98:
//            case 0x99:
//            case 0x9A:
//            case 0x9B:
//            case 0x9C:
//            case 0x9D:
//            case 0x9E:
//            case 0x9F:
//                // KS/AR
//                ExeCmd_KsAr(regNo, data);
//                break;
//
//            case 0xA0:
//            case 0xA1:
//            case 0xA2:
//            case 0xA3:
//            case 0xA4:
//            case 0xA5:
//            case 0xA6:
//            case 0xA7:
//            case 0xA8:
//            case 0xA9:
//            case 0xAA:
//            case 0xAB:
//            case 0xAC:
//            case 0xAD:
//            case 0xAE:
//            case 0xAF:
//            case 0xB0:
//            case 0xB1:
//            case 0xB2:
//            case 0xB3:
//            case 0xB4:
//            case 0xB5:
//            case 0xB6:
//            case 0xB7:
//            case 0xB8:
//            case 0xB9:
//            case 0xBA:
//            case 0xBB:
//            case 0xBC:
//            case 0xBD:
//            case 0xBE:
//            case 0xBF:
//                // AME/D1R
//                ExeCmd_AmeD1r(regNo, data);
//                break;
//
//            case 0xC0:
//            case 0xC1:
//            case 0xC2:
//            case 0xC3:
//            case 0xC4:
//            case 0xC5:
//            case 0xC6:
//            case 0xC7:
//            case 0xC8:
//            case 0xC9:
//            case 0xCA:
//            case 0xCB:
//            case 0xCC:
//            case 0xCD:
//            case 0xCE:
//            case 0xCF:
//            case 0xD0:
//            case 0xD1:
//            case 0xD2:
//            case 0xD3:
//            case 0xD4:
//            case 0xD5:
//            case 0xD6:
//            case 0xD7:
//            case 0xD8:
//            case 0xD9:
//            case 0xDA:
//            case 0xDB:
//            case 0xDC:
//            case 0xDD:
//            case 0xDE:
//            case 0xDF:
//                // DT2/D2R
//                ExeCmd_Dt2D2r(regNo, data);
//                break;
//
//            case 0xE0:
//            case 0xE1:
//            case 0xE2:
//            case 0xE3:
//            case 0xE4:
//            case 0xE5:
//            case 0xE6:
//            case 0xE7:
//            case 0xE8:
//            case 0xE9:
//            case 0xEA:
//            case 0xEB:
//            case 0xEC:
//            case 0xED:
//            case 0xEE:
//            case 0xEF:
//            case 0xF0:
//            case 0xF1:
//            case 0xF2:
//            case 0xF3:
//            case 0xF4:
//            case 0xF5:
//            case 0xF6:
//            case 0xF7:
//            case 0xF8:
//            case 0xF9:
//            case 0xFA:
//            case 0xFB:
//            case 0xFC:
//            case 0xFD:
//            case 0xFE:
//            case 0xff:
//                // D1L/RR
//                CmdExe_D1lRr(regNo, data);
//                break;
//
//        }

//#if ROMEO
//    if (UseOpmFlag == 2)
//    {
//        juliet_YM2151W((Byte)regNo, (Byte)data);
//    }
//#endif
    }

    private void dmy(int regNo, int data) {
    }

    private void CmdExe_D1lRr(int regNo, int data) {
        int slot = regNo - 0xE0;
        op[SLOTTBL[slot] >> 2][SLOTTBL[slot] & 3].setD1LRR(data);
    }

    private void ExeCmd_Dt2D2r(int regNo, int data) {
        int slot = regNo - 0xC0;
        op[SLOTTBL[slot] >> 2][SLOTTBL[slot] & 3].setDT2D2R(data);
    }

    private void ExeCmd_AmeD1r(int regNo, int data) {
        int slot = regNo - 0xA0;
        op[SLOTTBL[slot] >> 2][SLOTTBL[slot] & 3].setAMED1R(data);
    }

    private void ExeCmd_KsAr(int regNo, int data) {
        int slot = regNo - 0x80;
        op[SLOTTBL[slot] >> 2][SLOTTBL[slot] & 3].setKSAR(data);
    }

    private void ExeCmd_Tl(int regNo, int data) {
        int slot = regNo - 0x60;
        op[SLOTTBL[slot] >> 2][SLOTTBL[slot] & 3].setTL(data);
    }

    private void ExeCmd_Dt1Mul(int regNo, int data) {
        int slot = regNo - 0x40;
        op[SLOTTBL[slot] >> 2][SLOTTBL[slot] & 3].setDT1MUL(data);
    }

    private void ExeCmd_PmsAms(int regNo, int data) {
        int ch = regNo - 0x38;
        lfo.setPMSAMS(ch, data & 0xff);
    }

    private void ExeCmd_Kf(int regNo, int data) {
        int ch = regNo - 0x30;
        op[ch][0].setKF(data);
        op[ch][1].setKF(data);
        op[ch][2].setKF(data);
        op[ch][3].setKF(data);
    }

    private void ExeCmd_Kc(int regNo, int data) {
        int ch = regNo - 0x28;
        op[ch][0].setKC(data);
        op[ch][1].setKC(data);
        op[ch][2].setKC(data);
        op[ch][3].setKC(data);
    }

    private void ExeCmd_PanFlCon(int regNo, int data) {
        int ch = regNo - 0x20;
        //con[ch] = data & 7;
        setConnection(ch, data & 7);
        //pan[ch] = data >> 6;
        pan[0][ch] = ((data & 0x40) != 0 ? -1 : 0);
        pan[1][ch] = ((data & 0x80) != 0 ? -1 : 0);
        op[ch][0].setFL(data);
    }

    private void ExeCmd_WaveForm(int regNo, int data) {
        lfo.setWaveForm(data); // & 0xff);
    }

    private void ExeCmd_PmdAmd(int regNo, int data) {
        lfo.setPMDAMD(data); // & 0xff);
    }

    private void ExeCmd_Lfrq(int regNo, int data) {
        lfo.setLFRQ(data); // & 0xff);
    }

    private void ExeCmd_NeNfrq(int regNo, int data) {
        op[7][3].setNFRQ(data); // & 0xff);
    }

    private void ExeCmd_KON(int regNo, int data) {
        int ch, s, bit;
        ch = data & 7;
        for (s = 0, bit = 8; s < 4; ++s, bit += bit) {
            if ((data & bit) != 0) {
                op[ch][s].keyON(0);
            } else {
                op[ch][s].keyOFF(0);
            }
        }
    }

    private void ExeCmd_LfoReset(int regNo, int data) {
        if ((data & 0x02) != 0) {
            lfo.lfoReset();
        } else {
            lfo.lfoStart();
        }
    }

    private int rate = 0;

    public void setPcm62(short[] buffer, int offset, int ndata, BiConsumer<Runnable, Boolean> oneFrameProc /* = null */) {

        //DetectMMX();

        _pcmBufPtr = 0;
        for (int i = 0; i < ndata / 2; ++i) {
            //int[] Out = new int[];
            out[0] = out[1] = 0;
            boolean firstFlg = true;

            opmLpfIdx += Global.sampleRate;
            while (opmLpfIdx >= Global.waveOutSamp) {
                opmLpfIdx -= Global.waveOutSamp;

                outInpOpm[0] = outInpOpm[1] = 0;
                if (useOpmFlag != 0) {
                    rate -= global.opmRate;
                    while (rate < 0) {
                        rate += global.opmRate;

                        if (oneFrameProc != null) {
                            oneFrameProc.accept(this::timer, firstFlg);
                            firstFlg = false;
                        } else {
                            timer();
                        }
                        executeCommand();
                        if ((--envCounter2) == 0) {
                            envCounter2 = 3;
                            ++envCounter1;
                            int slot;
                            for (slot = 0; slot < 32; ++slot) {
                                op[slot / 4][slot % 4].envelope(envCounter1);
                            }
                        }
                    }

                    if (useOpmFlag == 1) {
                        for (int ch = 0; ch < 8; ++ch) {
                            op[ch][1].inp[0] = op[ch][2].inp[0] = op[ch][3].inp[0] = opOut[ch][0] = 0;

                            lfoPitch[ch] = lfo.getPmValue(ch);
                            lfoLevel[ch] = lfo.getAmValue(ch);
                        }
                        for (int ch = 0; ch < 8; ++ch) {
                            op[ch][0].output0(lfoPitch[ch], lfoLevel[ch]);
                        }
                        for (int ch = 0; ch < 8; ++ch) {
                            op[ch][1].output(lfoPitch[ch], lfoLevel[ch]);
                        }
                        for (int ch = 0; ch < 8; ++ch) {
                            op[ch][2].output(lfoPitch[ch], lfoLevel[ch]);
                        }
                        for (int ch = 0; ch < 7; ++ch) {
                            op[ch][3].output(lfoPitch[ch], lfoLevel[ch]);
                        }
                        op[7][3].output32(lfoPitch[7], lfoLevel[7]);

                        // Stereo sum of OPM output PCM to OpmHpfInp[]
                        if ((opmChMask & 0xff) != 0) {
                            opmHpfInp[0] = ((opmChMask & 0x01) != 0 ? 0 : (opOut[0][0] & pan[0][0])) +
                                    ((opmChMask & 0x02) != 0 ? 0 : (opOut[1][0] & pan[0][1])) +
                                    ((opmChMask & 0x04) != 0 ? 0 : (opOut[2][0] & pan[0][2])) +
                                    ((opmChMask & 0x08) != 0 ? 0 : (opOut[3][0] & pan[0][3])) +
                                    ((opmChMask & 0x10) != 0 ? 0 : (opOut[4][0] & pan[0][4])) +
                                    ((opmChMask & 0x20) != 0 ? 0 : (opOut[5][0] & pan[0][5])) +
                                    ((opmChMask & 0x40) != 0 ? 0 : (opOut[6][0] & pan[0][6])) +
                                    ((opmChMask & 0x80) != 0 ? 0 : (opOut[7][0] & pan[0][7]));
                            opmHpfInp[1] = ((opmChMask & 0x01) != 0 ? 0 : (opOut[0][0] & pan[1][0])) +
                                    ((opmChMask & 0x02) != 0 ? 0 : (opOut[1][0] & pan[1][1])) +
                                    ((opmChMask & 0x04) != 0 ? 0 : (opOut[2][0] & pan[1][2])) +
                                    ((opmChMask & 0x08) != 0 ? 0 : (opOut[3][0] & pan[1][3])) +
                                    ((opmChMask & 0x10) != 0 ? 0 : (opOut[4][0] & pan[1][4])) +
                                    ((opmChMask & 0x20) != 0 ? 0 : (opOut[5][0] & pan[1][5])) +
                                    ((opmChMask & 0x40) != 0 ? 0 : (opOut[6][0] & pan[1][6])) +
                                    ((opmChMask & 0x80) != 0 ? 0 : (opOut[7][0] & pan[1][7]));
                        } else {
                            opmHpfInp[0] = (opOut[0][0] & pan[0][0]) +
                                    (opOut[1][0] & pan[0][1]) +
                                    (opOut[2][0] & pan[0][2]) +
                                    (opOut[3][0] & pan[0][3]) +
                                    (opOut[4][0] & pan[0][4]) +
                                    (opOut[5][0] & pan[0][5]) +
                                    (opOut[6][0] & pan[0][6]) +
                                    (opOut[7][0] & pan[0][7]);
                            opmHpfInp[1] = (opOut[0][0] & pan[1][0]) +
                                    (opOut[1][0] & pan[1][1]) +
                                    (opOut[2][0] & pan[1][2]) +
                                    (opOut[3][0] & pan[1][3]) +
                                    (opOut[4][0] & pan[1][4]) +
                                    (opOut[5][0] & pan[1][5]) +
                                    (opOut[6][0] & pan[1][6]) +
                                    (opOut[7][0] & pan[1][7]);
                        }
                        opmHpfInp[0] = (opmHpfInp[0] & -1024) << 4;
                        opmHpfInp[1] = (opmHpfInp[1] & -1024) << 4;

                        opmHpfOut[0] = opmHpfInp[0] - opmHpfInpPrev[0] +
                                opmHpfOut[0] - (opmHpfOut[0] >> 10) - (opmHpfOut[0] >> 12);
                        opmHpfOut[1] = opmHpfInp[1] - opmHpfInpPrev[1] +
                                opmHpfOut[1] - (opmHpfOut[1] >> 10) - (opmHpfOut[1] >> 12);
                        opmHpfInpPrev[0] = opmHpfInp[0];
                        opmHpfInpPrev[1] = opmHpfInp[1];

                        inpInpOpm[0] = opmHpfOut[0] >> (4 + 5);
                        inpInpOpm[1] = opmHpfOut[1] >> (4 + 5);

                        inpInpOpm[0] = inpInpOpm[0] * 29;
                        inpInpOpm[1] = inpInpOpm[1] * 29;
                        inpOpm[0] = (inpInpOpm[0] + inpInpOpmPrev[0] + inpOpm[0] * 70) >> 7;
                        inpOpm[1] = (inpInpOpm[1] + inpInpOpmPrev[1] + inpOpm[1] * 70) >> 7;
                        inpInpOpmPrev[0] = inpInpOpm[0];
                        inpInpOpmPrev[1] = inpInpOpm[1];

                        // OPM and ADPCM volume balance adjustment
                        outInpOpm[0] = inpOpm[0] >> 5; // 8*-2^12 ~ 8*+2^12
                        outInpOpm[1] = inpOpm[1] >> 5; // 8*-2^12 ~ 8*+2^12
                    }
                }

                if (useAdpcmFlag != 0) {
                    outInpAdpcm[0] = outInpAdpcm[1] = 0;
                    // Add the output PCM of Adpcm to OutInpAdpcm[]
                    int o = adpcm.getPcm62();
                    if ((opmChMask & 0x100) == 0)
                        if (o != 0x8000_0000) {
                            outInpAdpcm[0] += (((ppiReg >> 1) & 1) - 1) & o;
                            outInpAdpcm[1] += ((ppiReg & 1) - 1) & o;
                        }

                    // Add the output PCM of Pcm8 to OutInpAdpcm[]
                    for ( int ch = 0; ch < Global.PCM8_NCH; ++ch) {
                        int pan = pcm8[ch].getMode();
                        int o2 = pcm8[ch].getPcm62();
                        if ((opmChMask & (0x100 << ch)) == 0)
                            if (o2 != 0x8000_0000) {
                                outInpAdpcm[0] += (-(pan & 1)) & o;
                                outInpAdpcm[1] += (-((pan >> 1) & 1)) & o;
                            }
                    }

                    // Prevents distortion
                    int LIMITS = ((1 << (15 + 4)) - 1);
                    if (((outInpAdpcm[0] + LIMITS) & 0xffff_ffffL) > ((LIMITS * 2) & 0xffff_ffffL)) {
                        if ((outInpAdpcm[0] + LIMITS) >= (LIMITS * 2)) {
                            outInpAdpcm[0] = LIMITS;
                        } else {
                            outInpAdpcm[0] = -LIMITS;
                        }
                    }
                    if (((outInpAdpcm[1] + LIMITS) & 0xffff_ffffL) > ((LIMITS * 2) & 0xffff_ffffL)) {
                        if ((outInpAdpcm[1] + LIMITS) >= (LIMITS * 2)) {
                            outInpAdpcm[1] = LIMITS;
                        } else {
                            outInpAdpcm[1] = -LIMITS;
                        }
                    }

                    outInpAdpcm[0] *= 26;
                    outInpAdpcm[1] *= 26;
                    outInpOutAdpcm[0] = (outInpAdpcm[0] + outInpAdpcmPrev[0] + outInpAdpcmPrev[0] + outInpAdpcmPrev2[0] -
                            outInpOutAdpcmPrev[0] * (-1537) - outInpOutAdpcmPrev2[0] * 617) >> 10;
                    outInpOutAdpcm[1] = (outInpAdpcm[1] + outInpAdpcmPrev[1] + outInpAdpcmPrev[1] + outInpAdpcmPrev2[1] -
                            outInpOutAdpcmPrev[1] * (-1537) - outInpOutAdpcmPrev2[1] * 617) >> 10;

                    outInpAdpcmPrev2[0] = outInpAdpcmPrev[0];
                    outInpAdpcmPrev2[1] = outInpAdpcmPrev[1];
                    outInpAdpcmPrev[0] = outInpAdpcm[0];
                    outInpAdpcmPrev[1] = outInpAdpcm[1];
                    outInpOutAdpcmPrev2[0] = outInpOutAdpcmPrev[0];
                    outInpOutAdpcmPrev2[1] = outInpOutAdpcmPrev[1];
                    outInpOutAdpcmPrev[0] = outInpOutAdpcm[0];
                    outInpOutAdpcmPrev[1] = outInpOutAdpcm[1];

                    outOutInpAdpcm[0] = outInpOutAdpcm[0] * (356);
                    outOutInpAdpcm[1] = outInpOutAdpcm[1] * (356);
                    outOutAdpcm[0] = (outOutInpAdpcm[0] + outOutInpAdpcmPrev[0] -
                            outOutAdpcmPrev[0] * (-312)) >> 10;
                    outOutAdpcm[1] = (outOutInpAdpcm[1] + outOutInpAdpcmPrev[1] -
                            outOutAdpcmPrev[1] * (-312)) >> 10;

                    outOutInpAdpcmPrev[0] = outOutInpAdpcm[0];
                    outOutInpAdpcmPrev[1] = outOutInpAdpcm[1];
                    outOutAdpcmPrev[0] = outOutAdpcm[0];
                    outOutAdpcmPrev[1] = outOutAdpcm[1];

                    // OPM and ADPCM volume balance adjustment
                    // -2048 * 16 ~ +2048 * 16
                    outInpOpm[0] += (outOutAdpcm[0] * 506) >> (4 + 9);
                    outInpOpm[1] += (outOutAdpcm[1] * 506) >> (4 + 9);
                }

                // Prevents distortion
                final int PCM_LIMITS = ((1 << 15) - 1);
                if (((outInpOpm[0] + PCM_LIMITS) & 0xffff_ffffL) > ((PCM_LIMITS * 2) & 0xffff_ffffL)) {
                    if ((outInpOpm[0] + PCM_LIMITS) >= (PCM_LIMITS * 2)) {
                        outInpOpm[0] = PCM_LIMITS;
                    } else {
                        outInpOpm[0] = -PCM_LIMITS;
                    }
                }
                if (((outInpOpm[1] + PCM_LIMITS) & 0xffff_ffffL) > ((PCM_LIMITS * 2) & 0xffff_ffffL)) {
                    if ((outInpOpm[1] + PCM_LIMITS) >= (PCM_LIMITS * 2)) {
                        outInpOpm[1] = PCM_LIMITS;
                    } else {
                        outInpOpm[1] = -PCM_LIMITS;
                    }
                }

                --inpOpmIdx;
                if (inpOpmIdx < 0) inpOpmIdx = Global.OPMLPF_COL - 1;
                inpOpmBuf0[inpOpmIdx] =
                        inpOpmBuf0[inpOpmIdx + Global.OPMLPF_COL] = (short) outInpOpm[0];
                InpOpmBuf1[inpOpmIdx] =
                        InpOpmBuf1[inpOpmIdx + Global.OPMLPF_COL] = (short) outInpOpm[1];
            }

            Global.firOpm(opmLPFpBuf[opmLPFpPtr], inpOpmBuf0, inpOpmIdx, InpOpmBuf1, inpOpmIdx, outOpm);

            opmLPFpPtr += 1;
            if (opmLPFpPtr >= Global.OPMLPF_ROW) {
                opmLPFpBuf = Global.OPMLOWPASS;
                opmLPFpPtr = 0;
            }

            // Adjust the overall volume
            outOpm[0] = (outOpm[0] * global.totalVolume) >> 8;
            outOpm[1] = (outOpm[1] * global.totalVolume) >> 8;

            out[0] -= outOpm[0]; // -4096 ～ +4096
            out[1] -= outOpm[1];

            // Add the output value of WaveFunc()
            if (waveFunc != null) {
                int ret = waveFunc.get();
                out[0] += (short) (ret & 0xffff);
                out[1] += (short) ((ret >> 16) & 0xffff);
            }

            // Prevents distortion
            if (((out[0] + 32767) & 0xffff_ffffL) > ((32767 * 2) & 0xffff_ffffL)) {
                if ((out[0] + 32767) >= (32767 * 2)) {
                    out[0] = 32767;
                } else {
                    out[0] = -32767;
                }
            }
            if (((out[1] + 32767) & 0xffff_ffffL) > ((32767 * 2) & 0xffff_ffffL)) {
                if ((out[1] + 32767) >= (32767 * 2)) {
                    out[1] = 32767;
                } else {
                    out[1] = -32767;
                }
            }

            buffer[offset + _pcmBufPtr * 2 + 0] = (short) out[0];
            buffer[offset + _pcmBufPtr * 2 + 1] = (short) out[1];

            ++_pcmBufPtr;
            if (_pcmBufPtr >= pcmBufSize) {
                _pcmBufPtr = 0;
            }
        }
    }

    private final int[] out = new int[2];
    private final int[] outInpOpm = new int[2];
    final int[] lfoPitch = new int[8];
    final int[] lfoLevel = new int[8];
    int rate_b = 0;
    int rate2 = 0;

    public void setPcm22(short[] buffer, int offset, int ndata) {
        _pcmBufPtr = 0;

        for (int i = 0; i < ndata / 2; ++i) {
            out[0] = out[1] = 0;

            if (useOpmFlag != 0) {
                //int rate = 0;

                rate_b -= global.opmRate;
                while (rate_b < 0) {
                    rate_b += Global.waveOutSamp;

                    timer();
                    executeCommand();
                    if ((--envCounter2) == 0) {
                        envCounter2 = 3;
                        ++envCounter1;
                        int slot;
                        for (slot = 0; slot < 32; ++slot) {
                            op[slot / 4][slot % 4].envelope(envCounter1);
                        }
                    }
                }

                if (useOpmFlag == 1) {
                    lfo.update();

                    for (int ch = 0; ch < 8; ++ch) {
                        op[ch][1].inp[0] = op[ch][2].inp[0] = op[ch][3].inp[0] = opOut[ch][0] = 0;

                        lfoPitch[ch] = lfo.getPmValue(ch);
                        lfoLevel[ch] = lfo.getAmValue(ch);
                    }
                    for (int ch = 0; ch < 8; ++ch) {
                        op[ch][0].output0(lfoPitch[ch], lfoLevel[ch]);
                    }
                    for (int ch = 0; ch < 8; ++ch) {
                        op[ch][1].output(lfoPitch[ch], lfoLevel[ch]);
                    }
                    for (int ch = 0; ch < 8; ++ch) {
                        op[ch][2].output(lfoPitch[ch], lfoLevel[ch]);
                    }
                    for (int ch = 0; ch < 7; ++ch) {
                        op[ch][3].output(lfoPitch[ch], lfoLevel[ch]);
                    }
                    op[7][3].output32(lfoPitch[7], lfoLevel[7]);

                    // Add the OPM output PCM to InpInpOpm[] in stereo
                    if ((opmChMask & 0xff) != 0) {
                        inpInpOpm[0] = ((opmChMask & 0x01) != 0 ? 0 : (opOut[0][0] & pan[0][0])) +
                                ((opmChMask & 0x02) != 0 ? 0 : (opOut[1][0] & pan[0][1])) +
                                ((opmChMask & 0x04) != 0 ? 0 : (opOut[2][0] & pan[0][2])) +
                                ((opmChMask & 0x08) != 0 ? 0 : (opOut[3][0] & pan[0][3])) +
                                ((opmChMask & 0x10) != 0 ? 0 : (opOut[4][0] & pan[0][4])) +
                                ((opmChMask & 0x20) != 0 ? 0 : (opOut[5][0] & pan[0][5])) +
                                ((opmChMask & 0x40) != 0 ? 0 : (opOut[6][0] & pan[0][6])) +
                                ((opmChMask & 0x80) != 0 ? 0 : (opOut[7][0] & pan[0][7]));
                        inpInpOpm[1] = ((opmChMask & 0x01) != 0 ? 0 : (opOut[0][0] & pan[1][0])) +
                                ((opmChMask & 0x02) != 0 ? 0 : (opOut[1][0] & pan[1][1])) +
                                ((opmChMask & 0x04) != 0 ? 0 : (opOut[2][0] & pan[1][2])) +
                                ((opmChMask & 0x08) != 0 ? 0 : (opOut[3][0] & pan[1][3])) +
                                ((opmChMask & 0x10) != 0 ? 0 : (opOut[4][0] & pan[1][4])) +
                                ((opmChMask & 0x20) != 0 ? 0 : (opOut[5][0] & pan[1][5])) +
                                ((opmChMask & 0x40) != 0 ? 0 : (opOut[6][0] & pan[1][6])) +
                                ((opmChMask & 0x80) != 0 ? 0 : (opOut[7][0] & pan[1][7]));
                    } else {
                        inpInpOpm[0] = (opOut[0][0] & pan[0][0]) +
                                (opOut[1][0] & pan[0][1]) +
                                (opOut[2][0] & pan[0][2]) +
                                (opOut[3][0] & pan[0][3]) +
                                (opOut[4][0] & pan[0][4]) +
                                (opOut[5][0] & pan[0][5]) +
                                (opOut[6][0] & pan[0][6]) +
                                (opOut[7][0] & pan[0][7]);
                        inpInpOpm[1] = (opOut[0][0] & pan[1][0]) +
                                (opOut[1][0] & pan[1][1]) +
                                (opOut[2][0] & pan[1][2]) +
                                (opOut[3][0] & pan[1][3]) +
                                (opOut[4][0] & pan[1][4]) +
                                (opOut[5][0] & pan[1][5]) +
                                (opOut[6][0] & pan[1][6]) +
                                (opOut[7][0] & pan[1][7]);
                    }

                    inpInpOpm[0] = (inpInpOpm[0] & -1024) >>
                            ((Global.SIZESINTBL_BITS + Global.PRECISION_BITS) - 10 - 5); // 8*-2^17 ～ 8*+2^17
                    inpInpOpm[1] = (inpInpOpm[1] & -1024) >>
                            ((Global.SIZESINTBL_BITS + Global.PRECISION_BITS) - 10 - 5); // 8*-2^17 ～ 8*+2^17

                    inpOpm[0] = inpInpOpm[0];
                    inpOpm[1] = inpInpOpm[1];

                    // Adjust the overall volume
                    outOpm[0] = (inpOpm[0] * global.totalVolume) >> 8;
                    outOpm[1] = (inpOpm[1] * global.totalVolume) >> 8;

                    out[0] -= outOpm[0] >> 5; // -4096 ～ +4096
                    out[1] -= outOpm[1] >> 5;

                    //logger.log(Level.TRACE, "outOpm0:%d outOpm1:%d".formatted(OutOpm[0], OutOpm[1]));
                }
            }

            if (useAdpcmFlag != 0) {
                //static int rate2 = 0;
                rate2 -= 15625;
                if (rate2 < 0) {
                    rate2 += Global.waveOutSamp;

                    outInpAdpcm[0] = outInpAdpcm[1] = 0;
                    // Add the output PCM of Adpcm to OutInpAdpcm[]
                    int o = adpcm.getPcm();
                    if ((opmChMask & 0x100) == 0)
                        if (o != 0x8000_0000) {
                            outInpAdpcm[0] += (((ppiReg >> 1) & 1) - 1) & o;
                            outInpAdpcm[1] += ((ppiReg & 1) - 1) & o;
                        }

                    // Add the output PCM of Pcm8 to OutInpAdpcm[]
                    for (int ch = 0; ch < Global.PCM8_NCH; ++ch) {
                        int pan = pcm8[ch].getMode();
                        int o2 = pcm8[ch].getPcm();
                        if ((opmChMask & (0x100 << ch)) == 0)
                            if (o2 != 0x8000_0000) { //0x8000_0000)
                                outInpAdpcm[0] += (-(pan & 1)) & o;
                                outInpAdpcm[1] += (-((pan >> 1) & 1)) & o;
                            }
                    }

                    // Adjust the overall volume
                    //outInpAdpcm[0] = (outInpAdpcm[0] * global.totalVolume) >> 8;
                    //outInpAdpcm[1] = (outInpAdpcm[1] * global.totalVolume) >> 8;

                    // Prevents distortion
                    final int PCM_LIMITS = ((1 << 19) - 1);
                    if (((outInpAdpcm[0] + PCM_LIMITS) & 0xffff_ffffL) > ((PCM_LIMITS * 2) & 0xffff_ffffL)) {
                        if ((outInpAdpcm[0] + PCM_LIMITS) >= (PCM_LIMITS * 2)) {
                            outInpAdpcm[0] = PCM_LIMITS;
                        } else {
                            outInpAdpcm[0] = -PCM_LIMITS;
                        }
                    }
                    if (((outInpAdpcm[1] + PCM_LIMITS) & 0xffff_ffffL) > ((PCM_LIMITS * 2) & 0xffff_ffffL)) {
                        if ((outInpAdpcm[1] + PCM_LIMITS) >= (PCM_LIMITS * 2)) {
                            outInpAdpcm[1] = PCM_LIMITS;
                        } else {
                            outInpAdpcm[1] = -PCM_LIMITS;
                        }
                    }

                    outInpAdpcm[0] *= 40;
                    outInpAdpcm[1] *= 40;
                }

                outOutAdpcm[0] = (outInpAdpcm[0] + outInpAdpcmPrev[0] + outInpAdpcmPrev[0] + outInpAdpcmPrev2[0] -
                        outOutAdpcmPrev[0] * (-157) - outOutAdpcmPrev2[0] * 61) >> 8;
                outOutAdpcm[1] = (outInpAdpcm[1] + outInpAdpcmPrev[1] + outInpAdpcmPrev[1] + outInpAdpcmPrev2[1] -
                        outOutAdpcmPrev[1] * (-157) - outOutAdpcmPrev2[1] * 61) >> 8;

                outInpAdpcmPrev2[0] = outInpAdpcmPrev[0];
                outInpAdpcmPrev2[1] = outInpAdpcmPrev[1];
                outInpAdpcmPrev[0] = outInpAdpcm[0];
                outInpAdpcmPrev[1] = outInpAdpcm[1];
                outOutAdpcmPrev2[0] = outOutAdpcmPrev[0];
                outOutAdpcmPrev2[1] = outOutAdpcmPrev[1];
                outOutAdpcmPrev[0] = outOutAdpcm[0];
                outOutAdpcmPrev[1] = outOutAdpcm[1];

                out[0] -= outOutAdpcm[0] >> 4; // -2048*16～+2048*16
                out[1] -= outOutAdpcm[1] >> 4;
            }

//            // Adjust the overall volume
//            out[0] = (out[0] * global.totalVolume) >> 8;
//            out[1] = (out[1] * global.totalVolume) >> 8;

            // Add the output value of WaveFunc()
            if (waveFunc != null) {
                int ret;
                ret = waveFunc.get();
                out[0] += (short) (ret & 0xffff);
                out[1] += (short) ((ret >> 16) & 0xffff);
            }

            // Prevents distortion
            if (((out[0] + 32767) & 0xffff_ffffL) > ((32767 * 2) & 0xffff_ffffL)) {
                if ((out[0] + 32767) >= (32767 * 2)) {
                    out[0] = 32767;
                } else {
                    out[0] = -32767;
                }
            }
            if (((out[1] + 32767) & 0xffff_ffffL) > ((32767 * 2) & 0xffff_ffffL)) {
                if ((out[1] + 32767) >= (32767 * 2)) {
                    out[1] = 32767;
                } else {
                    out[1] = -32767;
                }
            }

            buffer[offset + _pcmBufPtr * 2 + 0] = (short) out[0];
            buffer[offset + _pcmBufPtr * 2 + 1] = (short) out[1];
//logger.log(Level.TRACE, "PcmBufPtr:%d out0:%d out1:%d".formatted(PcmBufPtr, PcmBuf[PcmBufPtr * 2 + 0], PcmBuf[PcmBufPtr * 2 + 1]));
            ++_pcmBufPtr;
            if (_pcmBufPtr >= pcmBufSize) {
                _pcmBufPtr = 0;
            }
        }
    }

    public int getPcm(short[] buf, int offset, int ndata, BiConsumer<Runnable, Boolean> oneFrameProc /* = null */) {
        if (dousaMode != 2) {
            return X68Sound.SNDERR_NOTACTIVE;
        }
        pcmBuf = buf;
        _pcmBufPtr = 0;
        if (Global.waveOutSamp == 44100 || Global.waveOutSamp == 48000) {
            setPcm62(pcmBuf, offset, ndata, oneFrameProc);
        } else {
            setPcm22(pcmBuf, 0, ndata);
        }
        pcmBuf = null;
        return 0;
    }

    private void timer() {
//        if (_InterlockedCompareExchange(&TimerSemapho, 1, 0) == 1) {
//            return;
//        }

        int prevStat = statReg;
        int flagSet = 0;
        if ((timerReg & 0x01) != 0) { // TimerA is running
            ++timerACounter;
            if (timerACounter >= timerA) {
                flagSet |= ((timerReg >> 2) & 0x01);
                timerACounter = 0;
                if ((timerReg & 0x80) != 0) csmKeyOn();
            }
        }
        if ((timerReg & 0x02) != 0) { // TimerB is running
            ++timerBCounter;
            if (timerBCounter >= timerB) {
                flagSet |= ((timerReg >> 2) & 0x02);
                timerBCounter = 0;
            }
        }

        //int next_stat = StatReg;

        statReg |= flagSet;

        global.timerSemaphore = 0;

        if (flagSet != 0) {
            if (prevStat == 0) {
                if (opmIntProc != null) {
                    opmIntProc.run();
                }
            }
        }
    }

    public int start(int sampleRate, int opmFlag, int adpcmFlag, int betw, int pcmBuf, int late, double rev) {
        if (dousaMode != 0) {
            return X68Sound.SNDERR_ALREADYACTIVE;
        }
        dousaMode = 1;

        if (rev < 0.1) rev = 0.1;

        useOpmFlag = opmFlag;
        useAdpcmFlag = adpcmFlag;
        _betw = betw;
        _pcmbuf = pcmBuf;
        _late = late;
        _rev = (int) rev;

        if (sampleRate == 44100) {
            Global.sampleRate = global.opmRate;
            Global.OPMLPF_ROW = Global.OPMLPF_ROW_44;
            Global.OPMLOWPASS = Global.OPMLOWPASS_44;
        } else if (sampleRate == 48000) {
            Global.sampleRate = global.opmRate;
            Global.OPMLPF_ROW = Global.OPMLPF_ROW_48;
            Global.OPMLOWPASS = Global.OPMLOWPASS_48;
        } else {
            Global.sampleRate = sampleRate;
        }
        Global.waveOutSamp = sampleRate;

//#if ROMEO
//    if (UseOpmFlag == 2) {
//        juliet_load();
//        juliet_prepare();
//        juliet_YM2151Mute(0);
//    }
//#endif

        makeTable();
        reset();

        return waveAndTimerStart();
    }

    public int startPcm(int sampleRate, int opmFlag, int adpcmFlag, int pcmBuf) {
        if (dousaMode != 0) {
            return X68Sound.SNDERR_ALREADYACTIVE;
        }
        dousaMode = 2;

        useOpmFlag = opmFlag;
        useAdpcmFlag = adpcmFlag;
        _betw = 5;
        _pcmbuf = pcmBuf;
        _late = 200;
        _rev = (int) 1.0;

        if (sampleRate == 44100) {
            Global.sampleRate = global.opmRate;
            Global.OPMLPF_ROW = Global.OPMLPF_ROW_44;
            Global.OPMLOWPASS = Global.OPMLOWPASS_44;
        } else if (sampleRate == 48000) {
            Global.sampleRate = global.opmRate;
            Global.OPMLPF_ROW = Global.OPMLPF_ROW_48;
            Global.OPMLOWPASS = Global.OPMLOWPASS_48;
        } else {
            Global.sampleRate = sampleRate;
        }
        Global.waveOutSamp = sampleRate;

        makeTable();
        reset();

        pcmBufSize = 0xffff_ffff;

        return waveAndTimerStart();
    }

    public int setSampleRate(int sampleRate) {
        if (dousaMode == 0) {
            return X68Sound.SNDERR_NOTACTIVE;
        }
        int dousa_mode_bak = dousaMode;

        free();

        if (sampleRate == 44100) {
            Global.sampleRate = global.opmRate;
            Global.OPMLPF_ROW = Global.OPMLPF_ROW_44;
            Global.OPMLOWPASS = Global.OPMLOWPASS_44;
        } else if (sampleRate == 48000) {
            Global.sampleRate = global.opmRate;
            Global.OPMLPF_ROW = Global.OPMLPF_ROW_48;
            Global.OPMLOWPASS = Global.OPMLOWPASS_48;
        } else {
            Global.sampleRate = sampleRate;
        }
        Global.waveOutSamp = sampleRate;

        makeTable();
        resetSampleRate();

        dousaMode = dousa_mode_bak;
        return waveAndTimerStart();
    }

    public int setOpmClock(int clock) {
        int rate = clock >> 6;
        if (rate <= 0) {
            return X68Sound.SNDERR_BADARG;
        }
        if (dousaMode == 0) {
            global.opmRate = rate;
            return 0;
        }
        int dousa_mode_bak = dousaMode;

        free();

        global.opmRate = rate;

        makeTable();
        resetSampleRate();

        dousaMode = dousa_mode_bak;
        return waveAndTimerStart();
    }

    private int waveAndTimerStart() {

        global.betwTime = _betw;
        global.timerResolution = _betw;
        global.lateTime = _late + _betw;
        global.betwSamplesSlower = (int) Math.floor((double) (Global.waveOutSamp) * _betw / 1000.0 - _rev);
        global.betwSamplesFaster = (int) Math.ceil((double) (Global.waveOutSamp) * _betw / 1000.0 + _rev);
        global.betwSamplesVerySlower = (int) (Math.floor((double) (Global.waveOutSamp) * _betw / 1000.0 - _rev) / 8.0);
        global.lateSamples = Global.waveOutSamp * global.lateTime / 1000;

        global.blkSamples = global.lateSamples;

        if (global.lateSamples >= Global.waveOutSamp * 175 / 1000) {
            global.fasterLimit = global.lateSamples - Global.waveOutSamp * 125 / 1000;
        } else {
            global.fasterLimit = Global.waveOutSamp * 50 / 1000;
        }
        if (global.fasterLimit > global.lateSamples) global.fasterLimit = global.lateSamples;
        global.slowerLimit = global.fasterLimit;
        if (global.slowerLimit > global.lateSamples) global.slowerLimit = global.lateSamples;

        if (dousaMode != 1) {
            return 0;
        }

        pcmBufSize = global.blkSamples * Global.N_WaveBlk;
        global.nSamples = global.betwSamplesFaster;

//        if (naudio != null) naudio.Stop();
//        naudio = new NAudioWrap(Global.WaveOutSamp, Global.OpmTimeProc);
        //naudio.Start();

//        try {
//            Global.thread_handle = WinAPI.CreateThread(IntPtr.Zero, 0, Global.keepWaveOutThread, IntPtr.Zero, 0, out Global.threadId);
//            WinAPI.SetThreadPriority(Global.thread_handle, 1);// THREAD_PRIORITY_ABOVE_NORMAL);
//            WinAPI.SetThreadPriority(Global.thread_handle, -1);// THREAD_PRIORITY_BELOW_NORMAL);
//            WinAPI.SetThreadPriority(Global.thread_handle, 2);// THREAD_PRIORITY_HIGHEST);
//        } catch {
//            Free();
//            Global.ErrorCode = 5;
//            return X68Sound.X68SNDERR_TIMER;
//        }
//        while (Global.threadFlag == 0) System.Threading.Thread.Sleep(100);
//
//        WinAPI.MMRESULT ret;
//
//        Global.hwo = IntPtr.Zero;
//        wfx.wFormatTag = 0x0001;// WAVE_FORMAT_PCM;
//        wfx.nChannels = 2;
//        wfx.nSamplesPerSec = (int)Global.WaveOutSamp;
//        wfx.wBitsPerSample = 16;
//        wfx.nBlockAlign = (int)(wfx.nChannels * (wfx.wBitsPerSample / 8));
//        wfx.nAvgBytesPerSec = wfx.nSamplesPerSec * wfx.nBlockAlign;
//        wfx.cbSize = 0;
//
//        Global.timerStartFlag = 0;
//        if ((ret = WinAPI.waveOutOpen(Global.hwo, Global.WAVE_MAPPER, wfx, Global.keepWaveOutProc, 0, Global.CALLBACK_FUNCTION))
//        != WinAPI.MMRESULT.MMSYSERR_NOERROR) {
//            Global.hwo = IntPtr.Zero;
//            Free();
//            Global.ErrorCode = 0x10000000 + (int)ret;
//            return X68Sound.X68SNDERR_PCMOUT;
//        }
//        if (waveOutReset(hwo) != MMRESULT.MMSYSERR_NOERROR) {
//            waveOutClose(hwo);
//            hwo = IntPtr.Zero;
//            return X68Sound.X68SNDERR_PCMOUT;
//        }

        pcmBuf = new short[pcmBufSize * 2];

        // pcmset(Late_Samples);
        for (int i = 0; i < pcmBufSize * 2; ++i) {
            pcmBuf[i] = 0;
        }

        _pcmBufPtr = global.blkSamples + global.lateSamples + global.betwSamplesFaster;
        while (_pcmBufPtr >= pcmBufSize) _pcmBufPtr -= pcmBufSize;
        global.waveblk = 0;
        global.playingBlk = 0;
//        playingBlkNext = playingBlk + 1;
//        for (int i = 0; i < global.N_WaveBlk; ++i) {
//            WinAPI.PostThreadMessage(Global.threadId, Global.THREADMES_WAVEOUTDONE, (int)Ptr.Zero, IntPtr.Zero);
//        }

//        WinAPI.timeBeginPeriod(Global.timerResolution);
//        int usrctx = 0;
//        TimerID = WinAPI.timeSetEvent((int)Global.betwTime, Global.timerResolution, Global.keepOpmTimeProc, usrctx , Global.TIME_PERIODIC);
//        if (TimerID == 0) {
//            Free();
//            Global.ErrorCode = 4;
//            return X68Sound.X68SNDERR_TIMER;
//        }

        //while (Global.timerStartFlag == 0) System.Threading.Thread.Sleep(200); // Wait until the multimedia timer starts

        return 0;
    }

    public void free() {
        global.timerStartFlag = 0; // Stop processing multimedia timers

        dousaMode = 0;
    }

    public void opmInt(Runnable proc) {
        opmIntProc = proc;
    }

    public int adpcmPeek() {
        return adpcm.adpcmReg;
    }

    public void adpcmPoke(int data) {
        // original
        if ((data & 0x02) != 0) { // ADPCM playback begins
            adpcm.adpcmReg &= 0x7f;
        } else if ((data & 0x01) != 0) { // Playback stops
            adpcm.adpcmReg |= 0x80;
            adpcm.reset();
        }
    }

    public int peekPpi() {
        return ppiReg;
    }

    public void pokePpi(int data) {
        ppiReg = data;
        setAdpcmRate();
    }

    public void controlPpi(int data) {
        if ((data & 0x80) == 0) {
            if ((data & 0x01) != 0) {
                ppiReg |= 1 << ((data >> 1) & 7);
            } else {
                ppiReg &= 0xff ^ (1 << ((data >> 1) & 7));
            }
            setAdpcmRate();
        }
    }

    public int dmaPeek(int adrs) {
        if (adrs >= 0x40) return 0;
        if (adrs == 0x00) {
            if ((adpcm.adpcmReg & 0x80) == 0) { // ADPCM Playing
                adpcm.dmaReg[0x00] |= 0x02;
                return adpcm.dmaReg[0x00] | 0x01;
            }
        }
        return adpcm.dmaReg[adrs];
    }

    public void dmaPoke(int adrs, int data) {
        if (adrs >= 0x40) return;
        switch (adrs) {
        case 0x00: // CSR
            data &= 0xf6; // ACT and PCS not cleared
            adpcm.dmaReg[adrs] &= ~data;
            if ((data & 0x10) != 0) {
                adpcm.dmaReg[0x01] = 0;
            }
            return;
        case 0x01: // CER
            return;
        case 0x04: // DCR
        case 0x05: // OCR
        case 0x06: // SCR
        case 0x0A: // MTC
        case 0x0B: // MTC
        case 0x0C: // MAR
        case 0x0D: // MAR
        case 0x0E: // MAR
        case 0x0F: // MAR
        case 0x14: // DAR
        case 0x15: // DAR
        case 0x16: // DAR
        case 0x17: // DAR
        case 0x29: // MFC
        case 0x31: // DFC
            if ((adpcm.dmaReg[0x00] & 0x08) != 0) { // ACT==1 ?
                adpcm.dmaError(0x02); // Operation timing error
                break;
            }
            adpcm.dmaReg[adrs] = data;
            break;
        case 0x1A: // BTC
        case 0x1B: // BTC
        case 0x1C: // BAR
        case 0x1D: // BAR
        case 0x1E: // BAR
        case 0x1F: // BAR
        case 0x25: // NIV
        case 0x27: // EIV
        case 0x2D: // CPR
        case 0x39: // BFC
        case 0x3F: // GCR
            adpcm.dmaReg[adrs] = data;
            break;

        case 0x07:
            adpcm.dmaReg[0x07] = data & 0x78;
            if ((data & 0x80) != 0) { // STR == 1 ?

                if ((adpcm.dmaReg[0x00] & 0xf8) != 0) { // COC|BTC|NDT|ERR|ACT == 1 ?
                    adpcm.dmaError(0x02); // Operation timing error
                    adpcm.dmaReg[0x07] = data & 0x28;
                    break;
                }
                adpcm.dmaReg[0x00] |= 0x08; // ACT=1
                //adpcm.FinishFlag=0;
                if ((adpcm.dmaReg[0x04] & 0x08) != 0       // DPS != 0 ?
                        || (adpcm.dmaReg[0x06] & 0x03) != 0        // DAC != 00 ?
                        //|| Global.bswapl(*(byte**)&adpcm.DmaReg[0x14]) != (byte*)0x00E92003) {
                        || (
                        adpcm.dmaReg[0x14] * 0x100_0000 +
                                adpcm.dmaReg[0x15] * 0x1_0000 +
                                adpcm.dmaReg[0x16] * 0x100 +
                                adpcm.dmaReg[0x17]
                ) != 0x00e9_2003) {
                    adpcm.dmaError(0x0a); // Bus Error (Device Address)
                    adpcm.dmaReg[0x07] = data & 0x28;
                    break;
                }
                int ocr;
                ocr = adpcm.dmaReg[0x05] & 0xB0;
                if (ocr != 0x00 && ocr != 0x30) { // DIR==1 || SIZE!=00&&SIZE!=11 ?
                    adpcm.dmaError(0x01); // Configuration Error
                    adpcm.dmaReg[0x07] = data & 0x28;
                    break;
                }

            }
            if ((data & 0x40) != 0) { // CNT == 1 ?
                if ((adpcm.dmaReg[0x00] & 0x48) != 0x08) { // !(BTC==0&&ACT==1) ?
                    adpcm.dmaError(0x02); // Operation timing error
                    adpcm.dmaReg[0x07] = data & 0x28;
                    break;
                }

                if ((adpcm.dmaReg[0x05] & 0x08) != 0) { // CHAIN == 10 or 11 ?
                    adpcm.dmaError(0x01); // Configuration Error
                    adpcm.dmaReg[0x07] = data & 0x28;
                    break;
                }

            }
            if ((data & 0x10) != 0) { // SAB == 1 ?
                if ((adpcm.dmaReg[0x00] & 0x08) != 0) { // ACT == 1 ?
                    adpcm.dmaError(0x11); // Software forced stop
                    adpcm.dmaReg[0x07] = data & 0x28;
                    break;
                }
            }
            if ((data & 0x80) != 0) { // STR == 1 ?
                data &= 0x7f;

                if ((adpcm.dmaReg[0x05] & 0x08) != 0) { // Chaining Operation
                    if ((adpcm.dmaReg[0x05] & 0x04) == 0) { // Array Chain
                        if (adpcm.dmaArrayChainSetNextMtcMar() != 0) {
                            adpcm.dmaReg[0x07] = data & 0x28;
                            break;
                        }
                    } else { // Link Array Chain
                        if (adpcm.dmaLinkArrayChainSetNextMtcMar() != 0) {
                            adpcm.dmaReg[0x07] = data & 0x28;
                            break;
                        }
                    }
                }

                //if ((*(int*)&adpcm.DmaReg[0x0A]) == 0) { // MTC == 0 ?
                if ((adpcm.dmaReg[0x0a] | adpcm.dmaReg[0x0b]) == 0) { // MTC == 0 ?
                    adpcm.dmaError(0x0d); // Count error (memory address/memory counter)
                    data &= 0x28;
                    break;
                }

            }
            break;
        }
    }

    public void dmaInt(Runnable proc) {
        adpcm.intProc = proc;
    }

    public void dmaErrInt(Runnable proc) {
        adpcm.errIntProc = proc;
    }

    public int pcm8Out(int ch, byte[] adrsBuf, int adrsPtr, int mode, int len) {
        return pcm8[ch & (Global.PCM8_NCH - 1)].out(adrsBuf, adrsPtr, mode, len);
    }

    public int pcm8Aot(int ch, byte[] tblBuf, int tblPtr, int mode, int cnt) {
        return pcm8[ch & (Global.PCM8_NCH - 1)].aot(tblBuf, tblPtr, mode, cnt);
    }

    public int pcm8Lot(int ch, byte[] tblBuf, int tblPtr, int mode) {
        return pcm8[ch & (Global.PCM8_NCH - 1)].lot(tblBuf, tblPtr, mode);
    }

    public int pcm8SetMode(int ch, int mode) {
        return pcm8[ch & (Global.PCM8_NCH - 1)].setMode(mode);
    }

    public int pcm8GetRest(int ch) {
        return pcm8[ch & (Global.PCM8_NCH - 1)].getRest();
    }

    public int pcm8GetMode(int ch) {
        return pcm8[ch & (Global.PCM8_NCH - 1)].getMode();
    }

    public int pcm8Abort() {
        for (int ch = 0; ch < Global.PCM8_NCH; ++ch) {
            pcm8[ch].init();
        }
        return 0;
    }

    public int setTotalVolume(int v) {
        if (v <= 65535) {
            global.totalVolume = v;
        }
        return global.totalVolume;
    }

    public void betwInt(Runnable proc) {
        betwIntProc = proc;
    }

    public void betwint() {
        if (betwIntProc != null) {
            betwIntProc.run();
        }
    }

    public void setWaveFunc(Supplier<Integer> func) {
        waveFunc = func;
    }

    public void pushRegs() {
        opmRegNoBackup = opmRegNo;
    }

    public void popRegs() {
        opmRegNo = opmRegNoBackup;
    }

    public void memReadFunc(Function<Integer, Integer> func) {
        global.memRead = Objects.requireNonNullElseGet(func, () -> global::memReadDefault);
    }

    public void setMask(int v) {
        opmChMask = v;
    }

    public void csmKeyOn() {
        for (int ch = 0; ch < 8; ch++) {
            op[ch][0].keyON(1);
        }
    }
}
