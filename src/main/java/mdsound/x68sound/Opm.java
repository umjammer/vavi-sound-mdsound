package mdsound.x68sound;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;


public class Opm {
    public Global global = null;
    //private WinAPI.WAVEFORMATEX wfx;

    private static final int CMNDBUFSIZE = 65535;

    //#define RES (20)
    //#define NDATA (44100/5)
    private static final int PCMBUFSIZE = 65536;
    //#define DELAY (1000/5)

// #if C86CTL
    // c86ctl 用定義
//        typedef HRESULT(WINAPI C86CtlCreateInstance)(REFIID, LPVOID);
// #endif

    public String author = null;

    private Op[][] op = new Op[][] {
            new Op[4], new Op[4], new Op[4], new Op[4],
            new Op[4], new Op[4], new Op[4], new Op[4]
    };  // オペレータ0～31
    private int envCounter1;    // エンベロープ用カウンタ1 (0,1,2,3,4,5,6,...)
    private int envCounter2;    // エンベロープ用カウンタ2 (3,2,1,3,2,1,3,2,...)
    // int con[N_CH]; // アルゴリズム 0～7
    private int[][] pan = new int[][] {new int[Global.N_CH], new int[Global.N_CH]};  // 0:無音 -1:出力
    // int pms[N_CH]; // 0, 1, 2, 4, 10, 20, 80, 140
    // int ams[N_CH]; // 右シフト回数 31(0), 2(1), 1(2), 0(3)
    // int pmd;
    // int amd;
    // int pmspmd[N_CH]; // pms[]*pmd
    private Lfo lfo = null;
    private int[] SLOTTBL = new int[8 * 4];

    private byte[][] cmndBuf = null;//[CMNDBUFSIZE + 1][2];//コンストラクタで初期化
    private final Object numCmndLockObj = new Object();
    private long numCmnd;
    private int cmndReadIdx, cmndWriteIdx;
    private int cmndRate;


    // short PcmBuf[PCMBUFSIZE][2];
    private short[] pcmBuf;//?
    public int pcmBufSize;
    private int _pcmBufPtr;
    private final Object _pcmBufptrLockObj = new Object();
    int pcmBufPtr;

    public int getPcmBufPtr() {
        synchronized (_pcmBufptrLockObj) {
            return _pcmBufPtr;
        }
    }

    public void setPcmBufPtr(int value) {
        synchronized (_pcmBufptrLockObj) {
            _pcmBufPtr = value;
        }
    }


    public int TimerID = 0;

    // int LfoOverTime; // LFO tのオーバーフロー値
    // int LfoTime; // LFO専用 t
    // int LfoRndTime; // LFOランダム波専用t
    // int
    // int Lfrq;  // LFO周波数設定値 LFRQ
    // int LfoWaveForm; // LFO wave form
    // inline void CalcLfoStep();
    //inline void SetConnection(int ch, int alg);
    private int[][] opOut = new int[][] {new int[1], new int[1], new int[1], new int[1], new int[1], new int[1], new int[1], new int[1]};
    private int[] opOutDummy = new int[1];

    private int timerAreg10;    // OPMreg$10の値
    private int timerAreg11;    // OPMreg$11の値
    private int timerA;         // タイマーAのオーバーフロー設定値
    private int timerAcounter;  // タイマーAのカウンター値
    private int timerB;         // タイマーBのオーバーフロー設定値
    private int timerBcounter;  // タイマーBのカウンター値
    private int timerReg;      // タイマー制御レジスタ (OPMreg$14の下位4ビット+7ビット)
    private int statReg;       // OPMステータスレジスタ ($E90003の下位2ビット)
    private Runnable opmIntProc;  // OPM割り込みコールバック関数

    public double inpopmbuf_dummy;
    private short[] inpOpmBuf0 = new short[global.OPMLPF_COL * 2], InpOpmBuf1 = new short[global.OPMLPF_COL * 2];
    private int inpOpmIdx;
    private int opmLPFidx;
    private short[][] opmLPFpBuf;
    private int opmLPFpPtr;
    public double inpadpcmbuf_dummy;
    // short InpAdpcmBuf0[ADPCMLPF_COL*2],InpAdpcmBuf1[ADPCMLPF_COL*2];
    // int InpAdpcm_idx;
    // int AdpcmLPFidx; short *AdpcmLPFp;
    private int[] outOpm = new int[2];
    private int[] inpInpOpm = new int[2], inpOpm = new int[2];
    private int[] inpInpOpmPrev = new int[2], inpOpmPrev = new int[2];
    private int[] inpInpOpmPrev2 = new int[2], inpOpmPrev2 = new int[2];
    private int[] opmHpfInp = new int[2], opmHpfInpPrev = new int[2], opmHpfOut = new int[2];
    private int[] outInpAdpcm = new int[2], outInpAdpcmPrev = new int[2], outInpAdpcmPrev2 = new int[2],
            outOutAdpcm = new int[2], outOutAdpcmPrev = new int[2], outOutAdpcmPrev2 = new int[2];  // 高音フィルター２用バッファ
    private int[] outInpOutAdpcm = new int[2], outInpOutAdpcmPrev = new int[2], outInpOutAdpcmPrev2 = new int[2],
            outOutInpAdpcm = new int[2], outOutInpAdpcmPrev = new int[2];          // 高音フィルター３用バッファ

    private byte ppiReg;
    private byte adpcmBaseClock;   // ADPCMクロック切り替え(0:8MHz 1:4Mhz)
    //inline void SetAdpcmRate();

    private byte opmRegNo;     // 現在指定されているOPMレジスタ番号
    private byte opmRegNoBackup;      // バックアップ用OPMレジスタ番号
    private Runnable betwIntProc; // マルチメディアタイマー割り込み
    private Supplier<Integer> waveFunc;     // WaveFunc

    private int useOpmFlag;     // OPMを利用するかどうかのフラグ
    private int useAdpcmFlag;   // ADPCMを利用するかどうかのフラグ
    private int _betw;
    private int _pcmbuf;
    private int _late;
    private int _rev;

    private int dousaMode;     // 0:非動作 1:X68Sound_Start中  2:X68Sound_PcmStart中

    private int opmChMask;      // Channel Mask

    //public:
    private Adpcm adpcm = null;
    //private:
    private Pcm8[] pcm8 = new Pcm8[global.PCM8_NCH];

    // int TotalVolume; // 音量 x/256

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
        // OPMコマンドバッファを初期化
        synchronized (numCmndLockObj) {
            numCmnd = 0;
            cmndReadIdx = cmndWriteIdx = 0;
        }

        calcCmndRate();

        // 高音フィルター用バッファをクリア
        inpInpOpm[0] = inpInpOpm[1] =
                inpInpOpmPrev[0] = inpInpOpmPrev[1] = 0;
        inpInpOpmPrev2[0] = inpInpOpmPrev2[1] = 0;
        inpOpm[0] = inpOpm[1] =
                inpOpmPrev[0] = inpOpmPrev[1] =
                        inpOpmPrev2[0] = inpOpmPrev2[1] =
                                outOpm[0] = outOpm[1] = 0;
        {
            int i;
            for (i = 0; i < global.OPMLPF_COL * 2; ++i) {
                inpOpmBuf0[i] = InpOpmBuf1[i] = 0;
            }
            inpOpmIdx = 0;
            opmLPFidx = 0;
            opmLPFpBuf = global.OPMLOWPASS;
            opmLPFpPtr = 0;
        }
        opmHpfInp[0] = opmHpfInp[1] =
                opmHpfInpPrev[0] = opmHpfInpPrev[1] =
                        opmHpfOut[0] = opmHpfOut[1] = 0;
            /* {
                    int i,j;
                    for (i=0; i<ADPCMLPF_COL*2; ++i) {
                        InpAdpcmBuf0[i]=InpAdpcmBuf1[i]=0;
                    }
                    InpAdpcm_idx = 0;
                    AdpcmLPFidx = 0;
                    AdpcmLPFp = ADPCMLOWPASS[0];
                }
            */
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

        // 全オペレータを初期化
        {
            int ch;
            for (ch = 0; ch < Global.N_CH; ++ch) {
                op[ch][0] = new Op(global);
                op[ch][1] = new Op(global);
                op[ch][2] = new Op(global);
                op[ch][3] = new Op(global);
                op[ch][0].init();
                op[ch][1].init();
                op[ch][2].init();
                op[ch][3].init();
                //   con[ch] = 0;
                setConnection(ch, 0);
                pan[0][ch] = pan[1][ch] = 0;
            }
        }

        // エンベロープ用カウンタを初期化
        {
            envCounter1 = 0;
            envCounter2 = 3;
        }


        // LFO初期化
        lfo.init();

        // PcmBufポインターをリセット
        pcmBufPtr = 0;
        // PcmBufSize = PCMBUFSIZE;


        // タイマー関係の初期化
        timerAreg10 = 0;
        timerAreg11 = 0;
        timerA = 1024 - 0;
        timerAcounter = 0;
        timerB = (256 - 0) << (10 - 6);
        timerBcounter = 0;
        timerReg = 0;
        statReg = 0;
        opmIntProc = null;

        ppiReg = 0x0B;
        adpcmBaseClock = 0;


        adpcm.init();

        {
            int i;
            for (i = 0; i < global.PCM8_NCH; ++i) {
                if (pcm8[i] == null) {
                    pcm8[i] = new Pcm8(global);
                }
                pcm8[i].init();
            }
        }

        global.totalVolume = 256;
        // TotalVolume = 192;


        opmRegNo = 0;
        betwIntProc = null;
        waveFunc = null;

        global.memRead = Global::memReadDefault;

// #if C86CTL
//        if (pChipOPM)
//            pChipOPM.reset();
// #endif

// #if ROMEO
//        if (UseOpmFlag == 2)
//        {
//            juliet_YM2151Reset();
//            juliet_YM2151Mute(0);
//        }
// #endif

        // UseOpmFlag = 0;
        // UseAdpcmFlag = 0;
    }

    private void resetSamprate() {
        calcCmndRate();

        // 高音フィルター用バッファをクリア
        inpInpOpm[0] = inpInpOpm[1] =
                inpInpOpmPrev[0] = inpInpOpmPrev[1] = 0;
        inpInpOpmPrev2[0] = inpInpOpmPrev2[1] = 0;
        inpOpm[0] = inpOpm[1] =
                inpOpmPrev[0] = inpOpmPrev[1] =
                        inpOpmPrev2[0] = inpOpmPrev2[1] =
                                outOpm[0] = outOpm[1] = 0;
        {
            int i;
            for (i = 0; i < global.OPMLPF_COL * 2; ++i) {
                inpOpmBuf0[i] = InpOpmBuf1[i] = 0;
            }
            inpOpmIdx = 0;
            opmLPFidx = 0;
            opmLPFpBuf = global.OPMLOWPASS;
            opmLPFpPtr = 0;
        }
        opmHpfInp[0] = opmHpfInp[1] =
                opmHpfInpPrev[0] = opmHpfInpPrev[1] =
                        opmHpfOut[0] = opmHpfOut[1] = 0;
            /* {
                    int i,j;
                    for (i=0; i<ADPCMLPF_COL*2; ++i) {
                        InpAdpcmBuf0[i]=InpAdpcmBuf1[i]=0;
                    }
                    InpAdpcm_idx = 0;
                    AdpcmLPFidx = 0;
                    AdpcmLPFp = ADPCMLOWPASS[0];
                }
            */
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

        // 全オペレータを初期化
        {
            for (int ch = 0; ch < Global.N_CH; ++ch) {
                op[ch][0].initSamprate();
                op[ch][1].initSamprate();
                op[ch][2].initSamprate();
                op[ch][3].initSamprate();
            }
        }

        // LFOを初期化
        lfo.initSamprate();

        // PcmBufポインターをリセット
        pcmBufPtr = 0;
        // PcmBufSize = PCMBUFSIZE;

        // PpiReg = 0x0B;
        // AdpcmBaseClock = 0;

        adpcm.initSamprate();
    }

    public Opm(Global global) {
        this.global = global;
        lfo = new Lfo(global);
        adpcm = new Adpcm(global);

        cmndBuf = new byte[CMNDBUFSIZE + 1][];
        for (int i = 0; i < CMNDBUFSIZE + 1; i++) {
            cmndBuf[i] = new byte[2];
        }

        author = "m_puusan";

        //hwo = null;
        pcmBuf = null;
        TimerID = 0;// null;

        dousaMode = 0;
        opmChMask = 0;

        final List<BiConsumer<Byte, Byte>> a = Arrays.asList(
                this::dmy, this::ExeCmd_LfoReset, this::dmy, this::dmy              // 00-03
                , this::dmy, this::dmy, this::dmy, this::dmy              // 04-07
                , this::ExeCmd_KON, this::dmy, this::dmy, this::dmy              // 08-0B
                , this::dmy, this::dmy, this::dmy, this::ExeCmd_NeNfrq    // 0C-0F
                , this::dmy, this::dmy, this::dmy, this::dmy              // 10-13
                , this::dmy, this::dmy, this::dmy, this::dmy              // 14-17
                , this::ExeCmd_Lfrq, this::ExeCmd_PmdAmd, this::dmy, this::ExeCmd_WaveForm  // 18-1B
                , this::dmy, this::dmy, this::dmy, this::dmy              // 1C-1F
                , this::ExeCmd_PanFlCon, this::ExeCmd_PanFlCon, this::ExeCmd_PanFlCon, this::ExeCmd_PanFlCon  // 20-23
                , this::ExeCmd_PanFlCon, this::ExeCmd_PanFlCon, this::ExeCmd_PanFlCon, this::ExeCmd_PanFlCon  // 24-27
                , this::ExeCmd_Kc, this::ExeCmd_Kc, this::ExeCmd_Kc, this::ExeCmd_Kc        // 28-2B
                , this::ExeCmd_Kc, this::ExeCmd_Kc, this::ExeCmd_Kc, this::ExeCmd_Kc        // 2C-2F
                , this::ExeCmd_Kf, this::ExeCmd_Kf, this::ExeCmd_Kf, this::ExeCmd_Kf        // 30-33
                , this::ExeCmd_Kf, this::ExeCmd_Kf, this::ExeCmd_Kf, this::ExeCmd_Kf        // 34-37
                , this::ExeCmd_PmsAms, this::ExeCmd_PmsAms, this::ExeCmd_PmsAms, this::ExeCmd_PmsAms    // 38-3B
                , this::ExeCmd_PmsAms, this::ExeCmd_PmsAms, this::ExeCmd_PmsAms, this::ExeCmd_PmsAms    // 3C-3F
                , this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul    // 40-43
                , this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul    // 44-47
                , this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul    // 48-4B
                , this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul    // 4C-4F
                , this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul    // 50-53
                , this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul    // 54-57
                , this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul    // 58-5B
                , this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul, this::ExeCmd_Dt1Mul    // 5C-5F
                , this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl        // 60-63
                , this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl        // 64-67
                , this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl        // 68-6B
                , this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl        // 6C-6F
                , this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl        // 70-73
                , this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl        // 74-77
                , this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl        // 78-7B
                , this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl, this::ExeCmd_Tl        // 7C-7F
                , this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr      // 80-83
                , this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr      // 84-87
                , this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr      // 88-8B
                , this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr      // 8C-8F
                , this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr      // 90-93
                , this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr      // 94-97
                , this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr      // 98-9B
                , this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr, this::ExeCmd_KsAr      // 9C-9F
                , this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r    // A0-A3
                , this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r    // A4-A7
                , this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r    // A8-AB
                , this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r    // AC-AF
                , this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r    // B0-B3
                , this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r    // B4-B7
                , this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r    // B8-BB
                , this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r, this::ExeCmd_AmeD1r    // BC-BF
                , this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r    // C0-C3
                , this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r    // C4-C7
                , this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r    // C8-CB
                , this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r    // CC-CF
                , this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r    // D0-D3
                , this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r    // D4-D7
                , this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r    // D8-DB
                , this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r, this::ExeCmd_Dt2D2r    // DC-DF
                , this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr     // E0-E3
                , this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr     // E4-E7
                , this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr     // E8-EB
                , this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr     // EC-EF
                , this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr     // F0-F3
                , this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr     // F4-F7
                , this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr     // F8-FB
                , this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr, this::CmdExe_D1lRr     // FC-FF
        );
        cmdTbl = a.toArray(new BiConsumer[a.size()]);
        //new BiConsumer<Byte, Byte>[]

// #if C86CTL
        // C86CTL のロード
// pChipBase = null;
// pChipOPM = null;
// 
// hC86DLL = .LoadLibrary( "c86ctl.dll" );
// if(hC86DLL ){
//  C86CtlCreateInstance pCI = (C86CtlCreateInstance).GetProcAddress(hC86DLL, "CreateInstance");
//  if(pCI ) pCI (c86ctl.IID_IRealChipBase, (byte[][])pChipBase );
// }
// // C86CTLの初期化 & OPMモジュールの探索
// if(pChipBase ){
//  pChipBase.initialize();
//int num = pChipBase.getNumberOfChip();
//  for(int i=0; i<num; i++ ){
//   c86ctl.IGimic2 pGimic = 0;
//pChipBase.getChipInterface(i, c86ctl.IID_IGimic2, (byte[][])pGimic );
//   if(pGimic ){
//    c86ctl.ChipType chipType;
//pGimic.getModuleType( chipType );
//    if(chipType == c86ctl.CHIP_OPM ){
//     pGimic.QueryInterface(c86ctl.IID_IRealChip2, (byte[][])pChipOPM );
//pGimic.Release();
//     break;
//    }
//    pGimic.Release();
//   }
//  }
// }
// #endif
    }

    private void makeTable() {

        // sinテーブルを作成
        {
            int i;
            for (i = 0; i < Global.SIZESINTBL; ++i) {
                global.SINTBL[i] = (short) (Math.sin(2.0 * Math.PI * (i + 0.0) / Global.SIZESINTBL) * (Global.MAXSINVAL) + 0.5);
            }
            //  for (i=0; i<SIZESINTBL/4; ++i) {
            //   short s = sin(2.0*PI*i/(SIZESINTBL-0))*(MAXSINVAL+0.0) +0.9;
            //   SINTBL[i] = s;
            //   SINTBL[SIZESINTBL/2-1-i] = s;
            //   SINTBL[i+SIZESINTBL/2] = -s;
            //   SINTBL[SIZESINTBL-1-i] = -s;
            //  }

        }


        // エンベロープ値 → α 変換テーブルを作成
        {
            int i;
            for (i = 0; i <= global.ALPHAZERO + Global.SIZEALPHATBL; ++i) {
                global.ALPHATBL[i] = 0;
            }
            for (i = 17; i <= Global.SIZEALPHATBL; ++i) {
                global.ALPHATBL[global.ALPHAZERO + i] = (int) (Math.floor(
                        Math.pow(2.0, -((Global.SIZEALPHATBL) - i) * (128.0 / 8.0) / (Global.SIZEALPHATBL))
                                * 1.0 * 1.0 * Global.PRECISION + 0.0));
            }
        }
        // エンベロープ値 → Noiseα 変換テーブルを作成
        {
            int i;
            for (i = 0; i <= global.ALPHAZERO + Global.SIZEALPHATBL; ++i) {
                global.NOISEALPHATBL[i] = 0;
            }
            for (i = 17; i <= Global.SIZEALPHATBL; ++i) {
                global.NOISEALPHATBL[global.ALPHAZERO + i] = (int) Math.floor(
                        i * 1.0 / (Global.SIZEALPHATBL)
                                * 1.0 * 0.25 * Global.PRECISION + 0.0); // Noise音量はOpの1/4
            }
        }

        // D1L → D1l 変換テーブルを作成
        {
            int i;
            for (i = 0; i < 15; ++i) {
                global.D1LTBL[i] = i * 2;
            }
            global.D1LTBL[15] = (15 + 16) * 2;
        }


        // C1 <. M2 入れ替えテーブルを作成
        {
            int slot;
            for (slot = 0; slot < 8; ++slot) {
                SLOTTBL[slot] = slot * 4;
                SLOTTBL[slot + 8] = slot * 4 + 2;
                SLOTTBL[slot + 16] = slot * 4 + 1;
                SLOTTBL[slot + 24] = slot * 4 + 3;
            }
        }

        // Pitch→Δt変換テーブルを作成
        {
            int oct, notekf, step;

            for (oct = 0; oct <= 10; ++oct) {
                for (notekf = 0; notekf < 12 * 64; ++notekf) {
                    if (oct >= 3) {
                        step = Global.STEPTBL_O2[notekf] << (oct - 3);
                    } else {
                        step = Global.STEPTBL_O2[notekf] >> (3 - oct);
                    }
                    global.STEPTBL[oct * 12 * 64 + notekf] = (int) (step * 64 * (long) (global.opmRate) / Global.samprate);
                }
            }
            //  for (notekf=0; notekf<11*12*64; ++notekf) {
            //   STEPTBL3[notekf] = STEPTBL[notekf]/3.0+0.5;
            //  }
        }

        {
            int i;
            for (i = 0; i <= 128 + 4 - 1; ++i) {
                global.DT1TBL[i] = (int) (Global.DT1TBL_org[i] * 64 * (long) (global.opmRate) / Global.samprate);
            }
        }
    }

    public byte opmPeek() {
        return (byte) statReg;
    }

    public void opmReg(byte no) {
        opmRegNo = no;
    }

    public void opmPoke(byte data) {
        if (useOpmFlag < 2) {

// #if C86CTL
//        if (pChipOPM)
//            pChipOPM.out(OpmRegNo, data);
// else
// #endif
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
                    cmndBuf[cmndWriteIdx][0] = 0;//(byte)(time >> 24);
                    cmndBuf[cmndWriteIdx][1] = 0;//(byte)(time >> 16);
                    cmndBuf[cmndWriteIdx][2] = 0;//(byte)(time >> 8);
                    cmndBuf[cmndWriteIdx][3] = 0;//(byte)(time >> 0);
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

            timerAreg10 = data;
            timerA = 1024 - ((timerAreg10 << 2) + timerAreg11);

            break;
        case 0x11:
            // TimerA

            //if (OpmRegNo == 0x10)
            //{
            //    TimerAreg10 = data;
            //}
            //else
            //{
            timerAreg11 = data & 3;
            //}
            timerA = 1024 - ((timerAreg10 << 2) + timerAreg11);

            break;

        case 0x12:
            // TimerB

            timerB = (256 - (int) data) << (10 - 6);

            break;

        case 0x14:
            // タイマー制御レジスタ

            //while (_InterlockedCompareExchange(&TimerSemapho, 1, 0) == 1) ;

            timerReg = data & 0x8F;
            statReg &= 0xFF - ((data >> 4) & 3);

            global.timerSemapho = 0;

            break;

        case 0x1B:
            // WaveForm

            adpcmBaseClock = (byte) (data >> 7);
            setAdpcmRate();

            break;
        }
    }

    private void executeCmnd() {

        if (useOpmFlag < 2) {
            int rate = 0;
            rate -= cmndRate;
            while (rate < 0) {
                rate += 4096;
                synchronized (numCmndLockObj) {
                    if (numCmnd != 0) {
                        byte regno, data;
                        regno = cmndBuf[cmndReadIdx][0];
                        data = cmndBuf[cmndReadIdx][1];
                        ++cmndReadIdx;
                        cmndReadIdx &= CMNDBUFSIZE;
                        --numCmnd;
                        //_InterlockedDecrement(&NumCmnd);
                        executeCmndCore(regno, data);
                    }
                }
            }
        } else {
            synchronized (numCmndLockObj) {
                while (numCmnd != 0) {
                    int t1, t2;
                    byte regno, data;
                    t1 = 0;// timeGetTime();
                    t2 = (int) ((cmndBuf[cmndReadIdx][0] * 0x1000000) +
                            (cmndBuf[cmndReadIdx][1] * 0x10000) +
                            (cmndBuf[cmndReadIdx][2] * 0x100) +
                            (cmndBuf[cmndReadIdx][3] * 0x1));
                    t1 -= t2;
                    if (t1 < _late) break;
                    regno = cmndBuf[cmndReadIdx][4];
                    data = cmndBuf[cmndReadIdx][5];
                    cmndReadIdx += 4;
                    cmndReadIdx &= CMNDBUFSIZE;
                    --numCmnd;
                    //_InterlockedDecrement(&NumCmnd);
                    executeCmndCore(regno, data);
                }
            }
        }
    }

    private BiConsumer<Byte, Byte>[] cmdTbl;

    private void executeCmndCore(byte regno, byte data) {

        cmdTbl[regno].accept(regno, data);

        //switch (regno)
        //{
        //    case 0x01:
        //        // LFO RESET
        //        ExeCmd_LfoReset(data);
        //        break;

        //    case 0x08:
        //        // KON
        //        ExeCmd_KON(data);
        //        break;

        //    case 0x0F:
        //        // NE,NFRQ
        //        ExeCmd_NeNfrq(data);
        //        break;


        //    case 0x18:
        //        // LFRQ
        //        ExeCmd_Lfrq(data);
        //        break;
        //    case 0x19:
        //        // PMD/AMD
        //        ExeCmd_PmdAmd(data);
        //        break;
        //    case 0x1B:
        //        // WaveForm
        //        ExeCmd_WaveForm(data);
        //        break;

        //    case 0x20:
        //    case 0x21:
        //    case 0x22:
        //    case 0x23:
        //    case 0x24:
        //    case 0x25:
        //    case 0x26:
        //    case 0x27:
        //        // PAN/FL/CON
        //        ExeCmd_PanFlCon(regno, data);
        //        break;

        //    case 0x28:
        //    case 0x29:
        //    case 0x2A:
        //    case 0x2B:
        //    case 0x2C:
        //    case 0x2D:
        //    case 0x2E:
        //    case 0x2F:
        //        // KC
        //        ExeCmd_Kc(regno, data);
        //        break;

        //    case 0x30:
        //    case 0x31:
        //    case 0x32:
        //    case 0x33:
        //    case 0x34:
        //    case 0x35:
        //    case 0x36:
        //    case 0x37:
        //        // KF
        //        ExeCmd_Kf(regno, data);
        //        break;

        //    case 0x38:
        //    case 0x39:
        //    case 0x3A:
        //    case 0x3B:
        //    case 0x3C:
        //    case 0x3D:
        //    case 0x3E:
        //    case 0x3F:
        //        // PMS/AMS
        //        ExeCmd_PmsAms(regno, data);
        //        break;

        //    case 0x40:
        //    case 0x41:
        //    case 0x42:
        //    case 0x43:
        //    case 0x44:
        //    case 0x45:
        //    case 0x46:
        //    case 0x47:
        //    case 0x48:
        //    case 0x49:
        //    case 0x4A:
        //    case 0x4B:
        //    case 0x4C:
        //    case 0x4D:
        //    case 0x4E:
        //    case 0x4F:
        //    case 0x50:
        //    case 0x51:
        //    case 0x52:
        //    case 0x53:
        //    case 0x54:
        //    case 0x55:
        //    case 0x56:
        //    case 0x57:
        //    case 0x58:
        //    case 0x59:
        //    case 0x5A:
        //    case 0x5B:
        //    case 0x5C:
        //    case 0x5D:
        //    case 0x5E:
        //    case 0x5F:
        //        // DT1/MUL
        //        ExeCmd_Dt1Mul(regno, data);
        //        break;

        //    case 0x60:
        //    case 0x61:
        //    case 0x62:
        //    case 0x63:
        //    case 0x64:
        //    case 0x65:
        //    case 0x66:
        //    case 0x67:
        //    case 0x68:
        //    case 0x69:
        //    case 0x6A:
        //    case 0x6B:
        //    case 0x6C:
        //    case 0x6D:
        //    case 0x6E:
        //    case 0x6F:
        //    case 0x70:
        //    case 0x71:
        //    case 0x72:
        //    case 0x73:
        //    case 0x74:
        //    case 0x75:
        //    case 0x76:
        //    case 0x77:
        //    case 0x78:
        //    case 0x79:
        //    case 0x7A:
        //    case 0x7B:
        //    case 0x7C:
        //    case 0x7D:
        //    case 0x7E:
        //    case 0x7F:
        //        // TL
        //        ExeCmd_Tl(regno, data);
        //        break;

        //    case 0x80:
        //    case 0x81:
        //    case 0x82:
        //    case 0x83:
        //    case 0x84:
        //    case 0x85:
        //    case 0x86:
        //    case 0x87:
        //    case 0x88:
        //    case 0x89:
        //    case 0x8A:
        //    case 0x8B:
        //    case 0x8C:
        //    case 0x8D:
        //    case 0x8E:
        //    case 0x8F:
        //    case 0x90:
        //    case 0x91:
        //    case 0x92:
        //    case 0x93:
        //    case 0x94:
        //    case 0x95:
        //    case 0x96:
        //    case 0x97:
        //    case 0x98:
        //    case 0x99:
        //    case 0x9A:
        //    case 0x9B:
        //    case 0x9C:
        //    case 0x9D:
        //    case 0x9E:
        //    case 0x9F:
        //        // KS/AR
        //        ExeCmd_KsAr(regno, data);
        //        break;

        //    case 0xA0:
        //    case 0xA1:
        //    case 0xA2:
        //    case 0xA3:
        //    case 0xA4:
        //    case 0xA5:
        //    case 0xA6:
        //    case 0xA7:
        //    case 0xA8:
        //    case 0xA9:
        //    case 0xAA:
        //    case 0xAB:
        //    case 0xAC:
        //    case 0xAD:
        //    case 0xAE:
        //    case 0xAF:
        //    case 0xB0:
        //    case 0xB1:
        //    case 0xB2:
        //    case 0xB3:
        //    case 0xB4:
        //    case 0xB5:
        //    case 0xB6:
        //    case 0xB7:
        //    case 0xB8:
        //    case 0xB9:
        //    case 0xBA:
        //    case 0xBB:
        //    case 0xBC:
        //    case 0xBD:
        //    case 0xBE:
        //    case 0xBF:
        //        // AME/D1R
        //        ExeCmd_AmeD1r(regno, data);
        //        break;

        //    case 0xC0:
        //    case 0xC1:
        //    case 0xC2:
        //    case 0xC3:
        //    case 0xC4:
        //    case 0xC5:
        //    case 0xC6:
        //    case 0xC7:
        //    case 0xC8:
        //    case 0xC9:
        //    case 0xCA:
        //    case 0xCB:
        //    case 0xCC:
        //    case 0xCD:
        //    case 0xCE:
        //    case 0xCF:
        //    case 0xD0:
        //    case 0xD1:
        //    case 0xD2:
        //    case 0xD3:
        //    case 0xD4:
        //    case 0xD5:
        //    case 0xD6:
        //    case 0xD7:
        //    case 0xD8:
        //    case 0xD9:
        //    case 0xDA:
        //    case 0xDB:
        //    case 0xDC:
        //    case 0xDD:
        //    case 0xDE:
        //    case 0xDF:
        //        // DT2/D2R
        //        ExeCmd_Dt2D2r(regno, data);
        //        break;

        //    case 0xE0:
        //    case 0xE1:
        //    case 0xE2:
        //    case 0xE3:
        //    case 0xE4:
        //    case 0xE5:
        //    case 0xE6:
        //    case 0xE7:
        //    case 0xE8:
        //    case 0xE9:
        //    case 0xEA:
        //    case 0xEB:
        //    case 0xEC:
        //    case 0xED:
        //    case 0xEE:
        //    case 0xEF:
        //    case 0xF0:
        //    case 0xF1:
        //    case 0xF2:
        //    case 0xF3:
        //    case 0xF4:
        //    case 0xF5:
        //    case 0xF6:
        //    case 0xF7:
        //    case 0xF8:
        //    case 0xF9:
        //    case 0xFA:
        //    case 0xFB:
        //    case 0xFC:
        //    case 0xFD:
        //    case 0xFE:
        //    case 0xFF:
        //        // D1L/RR
        //        CmdExe_D1lRr(regno, data);
        //        break;

        //}

// #if ROMEO
//    if (UseOpmFlag == 2)
//    {
//        juliet_YM2151W((Byte)regno, (Byte)data);
//    }
// #endif

    }

    private void dmy(byte regno, byte data) {
    }

    private void CmdExe_D1lRr(byte regno, byte data) {
        int slot = regno - 0xE0;
        op[SLOTTBL[slot] >> 2][SLOTTBL[slot] & 3].setD1LRR(data);
    }

    private void ExeCmd_Dt2D2r(byte regno, byte data) {
        int slot = regno - 0xC0;
        op[SLOTTBL[slot] >> 2][SLOTTBL[slot] & 3].setDT2D2R(data);
    }

    private void ExeCmd_AmeD1r(byte regno, byte data) {
        int slot = regno - 0xA0;
        op[SLOTTBL[slot] >> 2][SLOTTBL[slot] & 3].setAMED1R(data);
    }

    private void ExeCmd_KsAr(byte regno, byte data) {
        int slot = regno - 0x80;
        op[SLOTTBL[slot] >> 2][SLOTTBL[slot] & 3].setKSAR(data);
    }

    private void ExeCmd_Tl(byte regno, byte data) {
        int slot = regno - 0x60;
        op[SLOTTBL[slot] >> 2][SLOTTBL[slot] & 3].setTL(data);
    }

    private void ExeCmd_Dt1Mul(byte regno, byte data) {
        int slot = regno - 0x40;
        op[SLOTTBL[slot] >> 2][SLOTTBL[slot] & 3].setDT1MUL(data);
    }

    private void ExeCmd_PmsAms(byte regno, byte data) {
        int ch = regno - 0x38;
        lfo.setPMSAMS(ch, data & 0xFF);
    }

    private void ExeCmd_Kf(byte regno, byte data) {
        int ch = regno - 0x30;
        op[ch][0].setKF(data);
        op[ch][1].setKF(data);
        op[ch][2].setKF(data);
        op[ch][3].setKF(data);
    }

    private void ExeCmd_Kc(byte regno, byte data) {
        int ch = regno - 0x28;
        op[ch][0].setKC(data);
        op[ch][1].setKC(data);
        op[ch][2].setKC(data);
        op[ch][3].setKC(data);
    }

    private void ExeCmd_PanFlCon(byte regno, byte data) {
        int ch = regno - 0x20;
        //   con[ch] = data & 7;
        setConnection(ch, data & 7);
        //   pan[ch] = data>>6;
        pan[0][ch] = ((data & 0x40) != 0 ? -1 : 0);
        pan[1][ch] = ((data & 0x80) != 0 ? -1 : 0);
        op[ch][0].setFL(data);
    }

    private void ExeCmd_WaveForm(byte regno, byte data) {
        lfo.setWaveForm(data);// & 0xFF);
    }

    private void ExeCmd_PmdAmd(byte regno, byte data) {
        lfo.setPMDAMD(data);// & 0xFF);
    }

    private void ExeCmd_Lfrq(byte regno, byte data) {
        lfo.setLFRQ(data);// & 0xFF);
    }

    private void ExeCmd_NeNfrq(byte regno, byte data) {
        op[7][3].setNFRQ(data);// & 0xFF);
    }

    private void ExeCmd_KON(byte regno, byte data) {
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

    private void ExeCmd_LfoReset(byte regno, byte data) {
        if ((data & 0x02) != 0) {
            lfo.lfoReset();
        } else {
            lfo.lfoStart();
        }
    }

    static int rate = 0;

    public void pcmset62(short[] buffer, int offset, int ndata, BiConsumer<Runnable, Boolean> oneFrameProc/* = null*/)
    //public void pcmset62(int ndata)
    {

        //DetectMMX();

        int i;
        pcmBufPtr = 0;
        for (i = 0; i < ndata / 2; ++i) {
            //int[] Out = new int[];
            out[0] = out[1] = 0;
            boolean firstFlg = true;

            opmLPFidx += Global.samprate;
            while (opmLPFidx >= Global.waveOutSamp) {
                opmLPFidx -= Global.waveOutSamp;

                //int[] OutInpOpm = new int[];
                outInpOpm[0] = outInpOpm[1] = 0;
                if (useOpmFlag != 0) {
                    //static int rate = 0;
                    rate -= global.opmRate;
                    while (rate < 0) {
                        rate += global.opmRate;

                        if (oneFrameProc != null) {
                            oneFrameProc.accept(this::timer, firstFlg);
                            firstFlg = false;
                        } else {
                            timer();
                        }
                        executeCmnd();
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
                        {
                            lfo.update();

                            //int[] lfopitch = new int[];
                            //int[] lfolevel = new int[];
                            int ch;
                            for (ch = 0; ch < 8; ++ch) {
                                op[ch][1].inp[0] = op[ch][2].inp[0] = op[ch][3].inp[0] = opOut[ch][0] = 0;

                                lfoPitch[ch] = lfo.getPmValue(ch);
                                lfoLevel[ch] = lfo.getAmValue(ch);
                            }
                            for (ch = 0; ch < 8; ++ch) {
                                op[ch][0].output0(lfoPitch[ch], lfoLevel[ch]);
                            }
                            for (ch = 0; ch < 8; ++ch) {
                                op[ch][1].output(lfoPitch[ch], lfoLevel[ch]);
                            }
                            for (ch = 0; ch < 8; ++ch) {
                                op[ch][2].output(lfoPitch[ch], lfoLevel[ch]);
                            }
                            for (ch = 0; ch < 7; ++ch) {
                                op[ch][3].output(lfoPitch[ch], lfoLevel[ch]);
                            }
                            op[7][3].output32(lfoPitch[7], lfoLevel[7]);
                        }

                        // OpmHpfInp[] に OPM の出力PCMをステレオ加算
                        if ((opmChMask & 0xff) != 0) {
                            opmHpfInp[0] = ((opmChMask & 0x01) != 0 ? 0 : (opOut[0][0] & pan[0][0]))
                                    + ((opmChMask & 0x02) != 0 ? 0 : (opOut[1][0] & pan[0][1]))
                                    + ((opmChMask & 0x04) != 0 ? 0 : (opOut[2][0] & pan[0][2]))
                                    + ((opmChMask & 0x08) != 0 ? 0 : (opOut[3][0] & pan[0][3]))
                                    + ((opmChMask & 0x10) != 0 ? 0 : (opOut[4][0] & pan[0][4]))
                                    + ((opmChMask & 0x20) != 0 ? 0 : (opOut[5][0] & pan[0][5]))
                                    + ((opmChMask & 0x40) != 0 ? 0 : (opOut[6][0] & pan[0][6]))
                                    + ((opmChMask & 0x80) != 0 ? 0 : (opOut[7][0] & pan[0][7]));
                            opmHpfInp[1] = ((opmChMask & 0x01) != 0 ? 0 : (opOut[0][0] & pan[1][0]))
                                    + ((opmChMask & 0x02) != 0 ? 0 : (opOut[1][0] & pan[1][1]))
                                    + ((opmChMask & 0x04) != 0 ? 0 : (opOut[2][0] & pan[1][2]))
                                    + ((opmChMask & 0x08) != 0 ? 0 : (opOut[3][0] & pan[1][3]))
                                    + ((opmChMask & 0x10) != 0 ? 0 : (opOut[4][0] & pan[1][4]))
                                    + ((opmChMask & 0x20) != 0 ? 0 : (opOut[5][0] & pan[1][5]))
                                    + ((opmChMask & 0x40) != 0 ? 0 : (opOut[6][0] & pan[1][6]))
                                    + ((opmChMask & 0x80) != 0 ? 0 : (opOut[7][0] & pan[1][7]));
                        } else {
                            opmHpfInp[0] = (opOut[0][0] & pan[0][0])
                                    + (opOut[1][0] & pan[0][1])
                                    + (opOut[2][0] & pan[0][2])
                                    + (opOut[3][0] & pan[0][3])
                                    + (opOut[4][0] & pan[0][4])
                                    + (opOut[5][0] & pan[0][5])
                                    + (opOut[6][0] & pan[0][6])
                                    + (opOut[7][0] & pan[0][7]);
                            opmHpfInp[1] = (opOut[0][0] & pan[1][0])
                                    + (opOut[1][0] & pan[1][1])
                                    + (opOut[2][0] & pan[1][2])
                                    + (opOut[3][0] & pan[1][3])
                                    + (opOut[4][0] & pan[1][4])
                                    + (opOut[5][0] & pan[1][5])
                                    + (opOut[6][0] & pan[1][6])
                                    + (opOut[7][0] & pan[1][7]);
                        }
                        opmHpfInp[0] = (opmHpfInp[0] & -1024) << 4;// (int)0xFFFFFC00) << 4;
                        opmHpfInp[1] = (opmHpfInp[1] & -1024) << 4;// (int)0xFFFFFC00) << 4;

                        opmHpfOut[0] = opmHpfInp[0] - opmHpfInpPrev[0]
                                + opmHpfOut[0] - (opmHpfOut[0] >> 10) - (opmHpfOut[0] >> 12);
                        opmHpfOut[1] = opmHpfInp[1] - opmHpfInpPrev[1]
                                + opmHpfOut[1] - (opmHpfOut[1] >> 10) - (opmHpfOut[1] >> 12);
                        opmHpfInpPrev[0] = opmHpfInp[0];
                        opmHpfInpPrev[1] = opmHpfInp[1];

                        inpInpOpm[0] = opmHpfOut[0] >> (4 + 5);
                        inpInpOpm[1] = opmHpfOut[1] >> (4 + 5);

                        //     InpInpOpm[0] = (InpInpOpm[0]&(int)0xFFFFFC00)
                        //         >> ((SIZESINTBL_BITS+PRECISION_BITS)-10-5); // 8*-2^17 ～ 8*+2^17
                        //     InpInpOpm[1] = (InpInpOpm[1]&(int)0xFFFFFC00)
                        //         >> ((SIZESINTBL_BITS+PRECISION_BITS)-10-5); // 8*-2^17 ～ 8*+2^17

                        inpInpOpm[0] = inpInpOpm[0] * 29;
                        inpInpOpm[1] = inpInpOpm[1] * 29;
                        inpOpm[0] = (inpInpOpm[0] + inpInpOpmPrev[0]
                                + inpOpm[0] * 70) >> 7;
                        inpOpm[1] = (inpInpOpm[1] + inpInpOpmPrev[1]
                                + inpOpm[1] * 70) >> 7;
                        inpInpOpmPrev[0] = inpInpOpm[0];
                        inpInpOpmPrev[1] = inpInpOpm[1];

                        outInpOpm[0] = inpOpm[0] >> 5; // 8*-2^12 ～ 8*+2^12
                        outInpOpm[1] = inpOpm[1] >> 5; // 8*-2^12 ～ 8*+2^12
                        //     OutInpOpm[0] = (InpOpm[0]*521) >> (5+9); // 8*-2^12 ～ 8*+2^12
                        //     OutInpOpm[1] = (InpOpm[1]*521) >> (5+9); // OPMとADPCMの音量バランス調整
                    }  // UseOpmFlags == 1
                }   // UseOpmFlag

                if (useAdpcmFlag != 0) {
                    outInpAdpcm[0] = outInpAdpcm[1] = 0;
                    // OutInpAdpcm[] に Adpcm の出力PCMを加算
                    {
                        int o;
                        o = adpcm.getPcm62();
                        if ((opmChMask & 0x100) == 0)
                            if (o != -2147483648)//0x80000000)
                            {
                                outInpAdpcm[0] += ((((int) (ppiReg) >> 1) & 1) - 1) & o;
                                outInpAdpcm[1] += (((int) (ppiReg) & 1) - 1) & o;
                            }
                    }

                    // OutInpAdpcm[] に Pcm8 の出力PCMを加算
                    {
                        int ch;
                        for (ch = 0; ch < Global.PCM8_NCH; ++ch) {
                            int pan;
                            pan = pcm8[ch].getMode();
                            int o;
                            o = pcm8[ch].getPcm62();
                            if ((opmChMask & (0x100 << ch)) == 0)
                                if (o != -2147483648)//0x80000000)
                                {
                                    outInpAdpcm[0] += (-(pan & 1)) & o;
                                    outInpAdpcm[1] += (-((pan >> 1) & 1)) & o;
                                }
                        }
                    }


                    //    OutInpAdpcm[0] >>= 4;
                    //    OutInpAdpcm[1] >>= 4;

                    // 音割れ防止
                    int LIMITS = ((1 << (15 + 4)) - 1);
                    if ((outInpAdpcm[0] + LIMITS) > (LIMITS * 2)) {
                        if ((outInpAdpcm[0] + LIMITS) >= (LIMITS * 2)) {
                            outInpAdpcm[0] = LIMITS;
                        } else {
                            outInpAdpcm[0] = -LIMITS;
                        }
                    }
                    if ((outInpAdpcm[1] + LIMITS) > (LIMITS * 2)) {
                        if ((outInpAdpcm[1] + LIMITS) >= (LIMITS * 2)) {
                            outInpAdpcm[1] = LIMITS;
                        } else {
                            outInpAdpcm[1] = -LIMITS;
                        }
                    }

                    outInpAdpcm[0] *= 26;
                    outInpAdpcm[1] *= 26;
                    outInpOutAdpcm[0] = (outInpAdpcm[0] + outInpAdpcmPrev[0] + outInpAdpcmPrev[0] + outInpAdpcmPrev2[0]
                            - outInpOutAdpcmPrev[0] * (-1537) - outInpOutAdpcmPrev2[0] * 617) >> 10;
                    outInpOutAdpcm[1] = (outInpAdpcm[1] + outInpAdpcmPrev[1] + outInpAdpcmPrev[1] + outInpAdpcmPrev2[1]
                            - outInpOutAdpcmPrev[1] * (-1537) - outInpOutAdpcmPrev2[1] * 617) >> 10;

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
                    outOutAdpcm[0] = (outOutInpAdpcm[0] + outOutInpAdpcmPrev[0]
                            - outOutAdpcmPrev[0] * (-312)) >> 10;
                    outOutAdpcm[1] = (outOutInpAdpcm[1] + outOutInpAdpcmPrev[1]
                            - outOutAdpcmPrev[1] * (-312)) >> 10;

                    outOutInpAdpcmPrev[0] = outOutInpAdpcm[0];
                    outOutInpAdpcmPrev[1] = outOutInpAdpcm[1];
                    outOutAdpcmPrev[0] = outOutAdpcm[0];
                    outOutAdpcmPrev[1] = outOutAdpcm[1];

                    //    OutInpOpm[0] += OutOutAdpcm[0] >> 4; // -2048*16～+2048*16
                    //    OutInpOpm[1] += OutOutAdpcm[1] >> 4; // -2048*16～+2048*16
                    outInpOpm[0] += (outOutAdpcm[0] * 506) >> (4 + 9);  // -2048*16～+2048*16
                    outInpOpm[1] += (outOutAdpcm[1] * 506) >> (4 + 9);  // OPMとADPCMの音量バランス調整
                }   // UseAdpcmFlag


                // 音割れ防止
                int PCM_LIMITS = ((1 << 15) - 1);
                if ((int) (outInpOpm[0] + PCM_LIMITS) > (int) (PCM_LIMITS * 2)) {
                    if ((int) (outInpOpm[0] + PCM_LIMITS) >= (int) (PCM_LIMITS * 2)) {
                        outInpOpm[0] = PCM_LIMITS;
                    } else {
                        outInpOpm[0] = -PCM_LIMITS;
                    }
                }
                if ((int) (outInpOpm[1] + PCM_LIMITS) > (int) (PCM_LIMITS * 2)) {
                    if ((int) (outInpOpm[1] + PCM_LIMITS) >= (int) (PCM_LIMITS * 2)) {
                        outInpOpm[1] = PCM_LIMITS;
                    } else {
                        outInpOpm[1] = -PCM_LIMITS;
                    }
                }
                //#undef PCM_LIMITS

                --inpOpmIdx;
                if (inpOpmIdx < 0) inpOpmIdx = global.OPMLPF_COL - 1;
                inpOpmBuf0[inpOpmIdx] =
                        inpOpmBuf0[inpOpmIdx + global.OPMLPF_COL] = (short) outInpOpm[0];
                InpOpmBuf1[inpOpmIdx] =
                        InpOpmBuf1[inpOpmIdx + global.OPMLPF_COL] = (short) outInpOpm[1];
            }

            global.OpmFir(opmLPFpBuf[opmLPFpPtr], inpOpmBuf0, (int) inpOpmIdx, InpOpmBuf1, (int) inpOpmIdx, outOpm);

            opmLPFpPtr += 1;// Global.OPMLPF_COL;
            if (opmLPFpPtr >= global.OPMLPF_ROW) {// Global.OPMLOWPASS[Global.OPMLPF_ROW]) {
                opmLPFpBuf = global.OPMLOWPASS;
                opmLPFpPtr = 0;
            }

            // 全体の音量を調整
            outOpm[0] = (outOpm[0] * global.totalVolume) >> 8;
            outOpm[1] = (outOpm[1] * global.totalVolume) >> 8;

            out[0] -= outOpm[0];    // -4096 ～ +4096
            out[1] -= outOpm[1];


            // WaveFunc()の出力値を加算
            if (waveFunc != null) {
                int ret;
                ret = waveFunc.get();
                out[0] += (short) ret;
                out[1] += (ret >> 16);
            }


            // 音割れ防止
            if ((out[0] + 32767) > (32767 * 2)) {
                if ((out[0] + 32767) >= (32767 * 2)) {
                    out[0] = 32767;
                } else {
                    out[0] = -32767;
                }
            }
            if ((out[1] + 32767) > (32767 * 2)) {
                if ((out[1] + 32767) >= (32767 * 2)) {
                    out[1] = 32767;
                } else {
                    out[1] = -32767;
                }
            }

            //PcmBuf[PcmBufPtr * 2 + 0] = (short)Out[0];
            //PcmBuf[PcmBufPtr * 2 + 1] = (short)Out[1];
            buffer[offset + pcmBufPtr * 2 + 0] = (short) out[0];
            buffer[offset + pcmBufPtr * 2 + 1] = (short) out[1];

            ++pcmBufPtr;
            if (pcmBufPtr >= pcmBufSize) {
                pcmBufPtr = 0;
            }
        }

    }

    private int[] out = new int[2];
    private int[] outInpOpm = new int[2];
    int[] lfoPitch = new int[8];
    int[] lfoLevel = new int[8];
    int rate_b = 0;
    int rate2 = 0;

    public void pcmset22(short[] buffer, int offset, int ndata)
    //public void pcmset22(int ndata)
    {
        pcmBufPtr = 0;

        int i;
        for (i = 0; i < ndata / 2; ++i) {
            out[0] = out[1] = 0;

            if (useOpmFlag != 0) {
                //int rate = 0;

                rate_b -= global.opmRate;
                while (rate_b < 0) {
                    rate_b += Global.waveOutSamp;

                    timer();
                    executeCmnd();
                    if ((--envCounter2) == 0) {
                        envCounter2 = 3;
                        ++envCounter1;
                        int slot;
                        for (slot = 0; slot < 32; ++slot) {
                            //Op[0][slot].Envelope(EnvCounter1);
                            op[slot / 4][slot % 4].envelope(envCounter1);
                        }
                    }
                }

                if (useOpmFlag == 1) {
                    {
                        lfo.update();

                        //int[] lfopitch = new int[];
                        //int[] lfolevel = new int[];
                        int ch;
                        for (ch = 0; ch < 8; ++ch) {
                            op[ch][1].inp[0] = op[ch][2].inp[0] = op[ch][3].inp[0] = opOut[ch][0] = 0;

                            lfoPitch[ch] = lfo.getPmValue(ch);
                            lfoLevel[ch] = lfo.getAmValue(ch);
                        }
                        for (ch = 0; ch < 8; ++ch) {
                            op[ch][0].output0(lfoPitch[ch], lfoLevel[ch]);
                        }
                        for (ch = 0; ch < 8; ++ch) {
                            op[ch][1].output(lfoPitch[ch], lfoLevel[ch]);
                        }
                        for (ch = 0; ch < 8; ++ch) {
                            op[ch][2].output(lfoPitch[ch], lfoLevel[ch]);
                        }
                        for (ch = 0; ch < 7; ++ch) {
                            op[ch][3].output(lfoPitch[ch], lfoLevel[ch]);
                        }
                        op[7][3].output32(lfoPitch[7], lfoLevel[7]);
                    }


                    // InpInpOpm[] に OPM の出力PCMをステレオ加算
                    if ((opmChMask & 0xff) != 0) {
                        inpInpOpm[0] = ((opmChMask & 0x01) != 0 ? 0 : (opOut[0][0] & pan[0][0]))
                                + ((opmChMask & 0x02) != 0 ? 0 : (opOut[1][0] & pan[0][1]))
                                + ((opmChMask & 0x04) != 0 ? 0 : (opOut[2][0] & pan[0][2]))
                                + ((opmChMask & 0x08) != 0 ? 0 : (opOut[3][0] & pan[0][3]))
                                + ((opmChMask & 0x10) != 0 ? 0 : (opOut[4][0] & pan[0][4]))
                                + ((opmChMask & 0x20) != 0 ? 0 : (opOut[5][0] & pan[0][5]))
                                + ((opmChMask & 0x40) != 0 ? 0 : (opOut[6][0] & pan[0][6]))
                                + ((opmChMask & 0x80) != 0 ? 0 : (opOut[7][0] & pan[0][7]));
                        inpInpOpm[1] = ((opmChMask & 0x01) != 0 ? 0 : (opOut[0][0] & pan[1][0]))
                                + ((opmChMask & 0x02) != 0 ? 0 : (opOut[1][0] & pan[1][1]))
                                + ((opmChMask & 0x04) != 0 ? 0 : (opOut[2][0] & pan[1][2]))
                                + ((opmChMask & 0x08) != 0 ? 0 : (opOut[3][0] & pan[1][3]))
                                + ((opmChMask & 0x10) != 0 ? 0 : (opOut[4][0] & pan[1][4]))
                                + ((opmChMask & 0x20) != 0 ? 0 : (opOut[5][0] & pan[1][5]))
                                + ((opmChMask & 0x40) != 0 ? 0 : (opOut[6][0] & pan[1][6]))
                                + ((opmChMask & 0x80) != 0 ? 0 : (opOut[7][0] & pan[1][7]));
                    } else {
                        inpInpOpm[0] = (opOut[0][0] & pan[0][0])
                                + (opOut[1][0] & pan[0][1])
                                + (opOut[2][0] & pan[0][2])
                                + (opOut[3][0] & pan[0][3])
                                + (opOut[4][0] & pan[0][4])
                                + (opOut[5][0] & pan[0][5])
                                + (opOut[6][0] & pan[0][6])
                                + (opOut[7][0] & pan[0][7]);
                        inpInpOpm[1] = (opOut[0][0] & pan[1][0])
                                + (opOut[1][0] & pan[1][1])
                                + (opOut[2][0] & pan[1][2])
                                + (opOut[3][0] & pan[1][3])
                                + (opOut[4][0] & pan[1][4])
                                + (opOut[5][0] & pan[1][5])
                                + (opOut[6][0] & pan[1][6])
                                + (opOut[7][0] & pan[1][7]);
                    }

                    {

                        inpInpOpm[0] = (inpInpOpm[0] & -1024)//(int)0xFFFFFC00)
                                >> ((Global.SIZESINTBL_BITS + Global.PRECISION_BITS) - 10 - 5); // 8*-2^17 ～ 8*+2^17
                        inpInpOpm[1] = (inpInpOpm[1] & -1024)//(int)0xFFFFFC00)
                                >> ((Global.SIZESINTBL_BITS + Global.PRECISION_BITS) - 10 - 5); // 8*-2^17 ～ 8*+2^17
                    }
                    inpOpm[0] = inpInpOpm[0];
                    inpOpm[1] = inpInpOpm[1];

                    // 全体の音量を調整
                    outOpm[0] = (inpOpm[0] * global.totalVolume) >> 8;
                    outOpm[1] = (inpOpm[1] * global.totalVolume) >> 8;

                    out[0] -= outOpm[0] >> (5); // -4096 ～ +4096
                    out[1] -= outOpm[1] >> (5);

                    //System.err.printf("outOpm0:{0} outOpm1:{1}", OutOpm[0], OutOpm[1]);

                }  // UseOpmFlags == 1
            }  // UseOpmFlags

            if (useAdpcmFlag != 0) {
                //static int rate2 = 0;
                rate2 -= 15625;
                if (rate2 < 0) {
                    rate2 += Global.waveOutSamp;

                    outInpAdpcm[0] = outInpAdpcm[1] = 0;
                    // OutInpAdpcm[] に Adpcm の出力PCMを加算
                    {
                        int o;
                        o = adpcm.getPcm();
                        if ((opmChMask & 0x100) == 0)
                            if (o != -2147483648)//0x80000000)
                            {
                                outInpAdpcm[0] += ((((int) (ppiReg) >> 1) & 1) - 1) & o;
                                outInpAdpcm[1] += (((int) (ppiReg) & 1) - 1) & o;
                            }
                    }

                    // OutInpAdpcm[] に Pcm8 の出力PCMを加算
                    {
                        int ch;
                        for (ch = 0; ch < global.PCM8_NCH; ++ch) {
                            int pan;
                            pan = pcm8[ch].getMode();
                            int o;
                            o = pcm8[ch].getPcm();
                            if ((opmChMask & (0x100 << ch)) == 0)
                                if (o != -2147483648)//0x80000000)
                                {
                                    outInpAdpcm[0] += (-(pan & 1)) & o;
                                    outInpAdpcm[1] += (-((pan >> 1) & 1)) & o;
                                }
                        }
                    }

                    // 全体の音量を調整
                    //     OutInpAdpcm[0] = (OutInpAdpcm[0]*TotalVolume) >> 8;
                    //     OutInpAdpcm[1] = (OutInpAdpcm[1]*TotalVolume) >> 8;

                    // 音割れ防止
                    int PCM_LIMITS = ((1 << 19) - 1);
                    if ((outInpAdpcm[0] + PCM_LIMITS) > (PCM_LIMITS * 2)) {
                        if ((outInpAdpcm[0] + PCM_LIMITS) >= (int) (PCM_LIMITS * 2)) {
                            outInpAdpcm[0] = PCM_LIMITS;
                        } else {
                            outInpAdpcm[0] = -PCM_LIMITS;
                        }
                    }
                    if ((outInpAdpcm[1] + PCM_LIMITS) > (PCM_LIMITS * 2)) {
                        if ((outInpAdpcm[1] + PCM_LIMITS) >= (PCM_LIMITS * 2)) {
                            outInpAdpcm[1] = PCM_LIMITS;
                        } else {
                            outInpAdpcm[1] = -PCM_LIMITS;
                        }
                    }
                    //#undef PCM_LIMITS

                    outInpAdpcm[0] *= 40;
                    outInpAdpcm[1] *= 40;
                }

                outOutAdpcm[0] = (outInpAdpcm[0] + outInpAdpcmPrev[0] + outInpAdpcmPrev[0] + outInpAdpcmPrev2[0]
                        - outOutAdpcmPrev[0] * (-157) - outOutAdpcmPrev2[0] * (61)) >> 8;
                outOutAdpcm[1] = (outInpAdpcm[1] + outInpAdpcmPrev[1] + outInpAdpcmPrev[1] + outInpAdpcmPrev2[1]
                        - outOutAdpcmPrev[1] * (-157) - outOutAdpcmPrev2[1] * (61)) >> 8;

                outInpAdpcmPrev2[0] = outInpAdpcmPrev[0];
                outInpAdpcmPrev2[1] = outInpAdpcmPrev[1];
                outInpAdpcmPrev[0] = outInpAdpcm[0];
                outInpAdpcmPrev[1] = outInpAdpcm[1];
                outOutAdpcmPrev2[0] = outOutAdpcmPrev[0];
                outOutAdpcmPrev2[1] = outOutAdpcmPrev[1];
                outOutAdpcmPrev[0] = outOutAdpcm[0];
                outOutAdpcmPrev[1] = outOutAdpcm[1];


                //   Out[0] += OutAdpcm[0] >> 4; // -2048*16～+2048*16
                //   Out[1] += OutAdpcm[1] >> 4;
                out[0] -= outOutAdpcm[0] >> 4;  // -2048*16～+2048*16
                out[1] -= outOutAdpcm[1] >> 4;
            }

            //  // 全体の音量を調整
            //  Out[0] = (Out[0]*TotalVolume) >> 8;
            //  Out[1] = (Out[1]*TotalVolume) >> 8;


            // WaveFunc()の出力値を加算
            if (waveFunc != null) {
                int ret;
                ret = waveFunc.get();
                out[0] += (int) (short) ret;
                out[1] += (ret >> 16);
            }


            // 音割れ防止
            if ((out[0] + 32767) > (32767 * 2)) {
                if ((out[0] + 32767) >= (32767 * 2)) {
                    out[0] = 32767;
                } else {
                    out[0] = -32767;
                }
            }
            if ((out[1] + 32767) > (32767 * 2)) {
                if ((int) (out[1] + 32767) >= (32767 * 2)) {
                    out[1] = 32767;
                } else {
                    out[1] = -32767;
                }
            }

            //PcmBuf[PcmBufPtr * 2 + 0] = (short)Out[0];
            //PcmBuf[PcmBufPtr * 2 + 1] = (short)Out[1];
            buffer[offset + pcmBufPtr * 2 + 0] = (short) out[0];
            buffer[offset + pcmBufPtr * 2 + 1] = (short) out[1];
            //System.err.printf("PcmBufPtr:{0} out0:{1} out1:{2}",PcmBufPtr, PcmBuf[PcmBufPtr*2+0], PcmBuf[PcmBufPtr*2+1]);
            ++pcmBufPtr;
            if (pcmBufPtr >= pcmBufSize) {
                pcmBufPtr = 0;
            }
        }
    }

    public int getPcm(short[] buf, int offset, int ndata, BiConsumer<Runnable, Boolean> oneFrameProc/* = null*/) {
        if (dousaMode != 2) {
            return X68Sound.SNDERR_NOTACTIVE;
        }
        //PcmBuf = (short(*)[2])buf;
        pcmBuf = buf;
        pcmBufPtr = 0;
        if (Global.waveOutSamp == 44100 || Global.waveOutSamp == 48000) {
            pcmset62(pcmBuf, offset, ndata, oneFrameProc);
        } else {
            pcmset22(pcmBuf, 0, ndata);
        }
        pcmBuf = null;
        return 0;
    }

    private void timer() {
        //if (_InterlockedCompareExchange(&TimerSemapho, 1, 0) == 1) {
        //return;
        //}

        int prev_stat = statReg;
        int flag_set = 0;
        if ((timerReg & 0x01) != 0) {   // TimerA 動作中
            ++timerAcounter;
            if (timerAcounter >= timerA) {
                flag_set |= ((timerReg >> 2) & 0x01);
                timerAcounter = 0;
                if ((timerReg & 0x80) != 0) csmKeyOn();
            }
        }
        if ((timerReg & 0x02) != 0) {   // TimerB 動作中
            ++timerBcounter;
            if (timerBcounter >= timerB) {
                flag_set |= ((timerReg >> 2) & 0x02);
                timerBcounter = 0;
            }
        }

        // int next_stat = StatReg;

        statReg |= flag_set;

        global.timerSemapho = 0;

        if (flag_set != 0) {
            if (
                    prev_stat == 0) {
                if (opmIntProc != null) {
                    opmIntProc.run();
                }
            }
        }
    }

    public int start(int samprate, int opmflag, int adpcmflag, int betw, int pcmbuf, int late, double rev) {
        if (dousaMode != 0) {
            return X68Sound.SNDERR_ALREADYACTIVE;
        }
        dousaMode = 1;

        if (rev < 0.1) rev = 0.1;

        useOpmFlag = opmflag;
        useAdpcmFlag = adpcmflag;
        _betw = betw;
        _pcmbuf = pcmbuf;
        _late = late;
        _rev = (int) rev;

        if (samprate == 44100) {
            Global.samprate = global.opmRate;
            global.OPMLPF_ROW = global.OPMLPF_ROW_44;
            global.OPMLOWPASS = Global.OPMLOWPASS_44;
        } else if (samprate == 48000) {
            Global.samprate = global.opmRate;
            global.OPMLPF_ROW = global.OPMLPF_ROW_48;
            global.OPMLOWPASS = Global.OPMLOWPASS_48;
        } else {
            Global.samprate = samprate;
        }
        Global.waveOutSamp = samprate;

// #if ROMEO
//    if (UseOpmFlag == 2)
//    {
//        juliet_load();
//        juliet_prepare();
//        juliet_YM2151Mute(0);
//    }
// #endif

        makeTable();
        reset();

        return waveAndTimerStart();
    }

    public int startPcm(int samprate, int opmflag, int adpcmflag, int pcmbuf) {
        if (dousaMode != 0) {
            return X68Sound.SNDERR_ALREADYACTIVE;
        }
        dousaMode = 2;

        useOpmFlag = opmflag;
        useAdpcmFlag = adpcmflag;
        _betw = 5;
        _pcmbuf = pcmbuf;
        _late = 200;
        _rev = (int) 1.0;

        if (samprate == 44100) {
            global.samprate = global.opmRate;
            global.OPMLPF_ROW = global.OPMLPF_ROW_44;
            global.OPMLOWPASS = Global.OPMLOWPASS_44;
        } else if (samprate == 48000) {
            global.samprate = global.opmRate;
            global.OPMLPF_ROW = global.OPMLPF_ROW_48;
            global.OPMLOWPASS = Global.OPMLOWPASS_48;
        } else {
            global.samprate = samprate;
        }
        global.waveOutSamp = samprate;

        makeTable();
        reset();

        pcmBufSize = 0xFFFFFFFF;

        return waveAndTimerStart();
    }

    public int setSamprate(int samprate) {
        if (dousaMode == 0) {
            return X68Sound.SNDERR_NOTACTIVE;
        }
        int dousa_mode_bak = dousaMode;

        free();

        if (samprate == 44100) {
            global.samprate = global.opmRate;
            global.OPMLPF_ROW = global.OPMLPF_ROW_44;
            global.OPMLOWPASS = Global.OPMLOWPASS_44;
        } else if (samprate == 48000) {
            global.samprate = global.opmRate;
            global.OPMLPF_ROW = global.OPMLPF_ROW_48;
            global.OPMLOWPASS = Global.OPMLOWPASS_48;
        } else {
            global.samprate = samprate;
        }
        global.waveOutSamp = samprate;

        makeTable();
        resetSamprate();

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
        resetSamprate();

        dousaMode = dousa_mode_bak;
        return waveAndTimerStart();
    }

    //public NAudioWrap naudio = null;

    private int waveAndTimerStart() {

        global.Betw_Time = _betw;
        global.TimerResolution = (int) _betw;
        global.Late_Time = _late + _betw;
        global.betwSamplesSlower = (int) Math.floor((double) (global.waveOutSamp) * _betw / 1000.0 - _rev);
        global.betwSamplesFaster = (int) Math.ceil((double) (global.waveOutSamp) * _betw / 1000.0 + _rev);
        global.betwSamplesVerySlower = (int) (Math.floor((double) (global.waveOutSamp) * _betw / 1000.0 - _rev) / 8.0);
        global.lateSamples = global.waveOutSamp * global.Late_Time / 1000;

        // Blk_Samples = WaveOutSamp/N_waveblk;
        global.blkSamples = global.lateSamples;

        // Faster_Limit = Late_Samples;
        // Faster_Limit = WaveOutSamp*50/1000;
        if (global.lateSamples >= global.waveOutSamp * 175 / 1000) {
            global.fasterLimit = global.lateSamples - global.waveOutSamp * 125 / 1000;
        } else {
            global.fasterLimit = global.waveOutSamp * 50 / 1000;
        }
        if (global.fasterLimit > global.lateSamples) global.fasterLimit = global.lateSamples;
        global.Slower_Limit = global.fasterLimit;
        // Slower_Limit = WaveOutSamp*100/1000;
        // Slower_Limit = Late_Samples;
        if (global.Slower_Limit > global.lateSamples) global.Slower_Limit = global.lateSamples;

        if (dousaMode != 1) {
            return 0;
        }


        pcmBufSize = (int) (global.blkSamples * global.N_waveblk);
        global.nSamples = (int) (global.betwSamplesFaster);

        //if (naudio != null) naudio.Stop();
        //naudio = new NAudioWrap(Global.WaveOutSamp, Global.OpmTimeProc);
        // naudio.Start();

        //try
        //{
        //    Global.thread_handle = WinAPI.CreateThread(IntPtr.Zero, 0, Global.keepWaveOutThread, IntPtr.Zero, 0, out Global.thread_id);
        //    WinAPI.SetThreadPriority(Global.thread_handle, 1);// THREAD_PRIORITY_ABOVE_NORMAL);
        //    WinAPI.SetThreadPriority(Global.thread_handle, -1);// THREAD_PRIORITY_BELOW_NORMAL);
        //    WinAPI.SetThreadPriority(Global.thread_handle, 2);// THREAD_PRIORITY_HIGHEST);
        //}
        //catch
        //{
        //    Free();
        //    Global.ErrorCode = 5;
        //    return X68Sound.X68SNDERR_TIMER;
        //}
        //while (Global.thread_flag == 0) System.Threading.Thread.Sleep(100);


        //WinAPI.MMRESULT ret;

        //Global.hwo = IntPtr.Zero;
        //wfx.wFormatTag = 0x0001;// WAVE_FORMAT_PCM;
        //wfx.nChannels = 2;
        //wfx.nSamplesPerSec = (int)Global.WaveOutSamp;
        //wfx.wBitsPerSample = 16;
        //wfx.nBlockAlign = (int)(wfx.nChannels * (wfx.wBitsPerSample / 8));
        //wfx.nAvgBytesPerSec = wfx.nSamplesPerSec * wfx.nBlockAlign;
        //wfx.cbSize = 0;

        //Global.timer_start_flag = 0;
        //if ((ret = WinAPI.waveOutOpen(Global.hwo, Global.WAVE_MAPPER, wfx, Global.keepWaveOutProc, 0, Global.CALLBACK_FUNCTION))
        //!= WinAPI.MMRESULT.MMSYSERR_NOERROR)
        //{
        //    Global.hwo = IntPtr.Zero;
        //    Free();
        //    Global.ErrorCode = 0x10000000 + (int)ret;
        //    return X68Sound.X68SNDERR_PCMOUT;
        //}
        ////if (waveOutReset(hwo) != MMRESULT.MMSYSERR_NOERROR)
        ////{
        ////    waveOutClose(hwo);
        ////    hwo = IntPtr.Zero;
        ////    return X68Sound.X68SNDERR_PCMOUT;
        ////}


        //PcmBuf = (short(*)[2])GlobalAllocPtr(GMEM_MOVEABLE | GMEM_SHARE, PcmBufSize * 2 * 2);
        pcmBuf = new short[pcmBufSize * 2];
        //for (int i = 0; i < PcmBufSize; i++) PcmBuf[i] = new short[2];
        if (pcmBuf == null) {
            free();
            global.ErrorCode = 2;
            return X68Sound.SNDERR_MEMORY;
        }
        //lpwh = (LPWAVEHDR)GlobalAllocPtr(GMEM_MOVEABLE | GMEM_SHARE,
        //(DWORD)sizeof(WAVEHDR) * N_waveblk);
        //if (!lpwh)
        //{
        //Free();
        //ErrorCode = 3;
        //return X68SNDERR_MEMORY;
        //}

        // pcmset(Late_Samples);
        {
            int i;
            for (i = 0; i < pcmBufSize * 2; ++i) {
                //PcmBuf[i][0] = PcmBuf[i][1] = 0;
                pcmBuf[i] = 0;
            }
        }

        //{
        //    int i;
        //    Global.N_wavehdr = 0;
        //    GCHandle gch = GCHandle.Alloc(PcmBuf, GCHandleType.Pinned);
        //    for (i = 0; i < Global.N_waveblk; ++i)
        //    {
        //        WinAPI.WaveHdr waveHdr = new WinAPI.WaveHdr();
        //        Global.WaveHdrList.add(waveHdr);

        //        waveHdr.lpData = gch.AddrOfPinnedObject() + Global.Blk_Samples * i * sizeof(short) * 2;
        //        waveHdr.dwBufferLength = (int)(Global.Blk_Samples * sizeof(short) * 2);
        //        waveHdr.dwUser = i == 0 ? GCHandle.ToIntPtr(gch) : IntPtr.Zero;
        //        waveHdr.dwFlags = 0;
        //        waveHdr.dwLoops = 0;

        //        if ((ret = WinAPI.waveOutPrepareHeader(Global.hwo, waveHdr, Marshal.SizeOf(typeof(WinAPI.WaveHdr)))) != WinAPI.MMRESULT.MMSYSERR_NOERROR)
        //        {
        //            Free();
        //            Global.ErrorCode = 0x20000000 + (int)ret;
        //            return X68Sound.X68SNDERR_PCMOUT;
        //        }
        //        ++Global.N_wavehdr;
        //    }
        //}

        pcmBufPtr = (int) (global.blkSamples + global.lateSamples + global.betwSamplesFaster);
        while (pcmBufPtr >= pcmBufSize) pcmBufPtr -= pcmBufSize;
        global.waveblk = 0;
        global.playingblk = 0;
        // playingblk_next = playingblk+1;
        {
            int i;
            for (i = 0; i < global.N_waveblk; ++i) {
                //WinAPI.PostThreadMessage(Global.thread_id, Global.THREADMES_WAVEOUTDONE, (int)Ptr.Zero, IntPtr.Zero);
            }
        }

        //WinAPI.timeBeginPeriod(Global.TimerResolution);
        //int usrctx = 0;
        //TimerID = WinAPI.timeSetEvent((int)Global.Betw_Time, Global.TimerResolution, Global.keepOpmTimeProc, usrctx , Global.TIME_PERIODIC);
        //if (TimerID == 0)
        //{
        //    Free();
        //    Global.ErrorCode = 4;
        //    return X68Sound.X68SNDERR_TIMER;
        //}

        //while (Global.timer_start_flag == 0) System.Threading.Thread.Sleep(200);   // マルチメディアタイマーの処理が開始されるまで待つ

        return 0;
    }


    public void free() {
// #if C86CTL
//    if (pChipBase)
//    {
//        if (pChipOPM)
//        {
//            pChipOPM.reset();
//            pChipOPM = 0;
//        }
//        pChipBase.deinitialize();
//        pChipBase = 0;
//    }
// #endif

// #if ROMEO
//    if (UseOpmFlag == 2)
//    {
//        juliet_YM2151Reset();
//        juliet_unload();
//    }
// #endif
        //if (naudio != null) naudio.Stop();

        //if (TimerID != 0)
        //{   // マルチメディアタイマー停止
        //    WinAPI.timeKillEvent(TimerID);
        //    TimerID = 0;
        //    WinAPI.timeEndPeriod(Global.TimerResolution);
        //}
        //if (Global.thread_handle != IntPtr.Zero)
        //{   // スレッド停止
        //    WinAPI.PostThreadMessage(Global.thread_id,Global.THREADMES_KILL, (int)Ptr.Zero, IntPtr.Zero);
        //    WinAPI.WaitForSingleObject(Global.thread_handle, 0xFFFFFFFF);// INFINITE);
        //    WinAPI.CloseHandle(Global.thread_handle);
        //    Global.thread_handle = IntPtr.Zero;
        //}
        global.timer_start_flag = 0;   // マルチメディアタイマーの処理を停止
        //if (Global.hwo != IntPtr.Zero)
        //{       // ウェーブ再生停止
        //        //  waveOutReset(hwo);
        //    if (Global.WaveHdrList.Count>0)
        //    {
        //        int i;
        //        for (i = 0; i <Global.N_wavehdr; ++i)
        //        {
        //            WinAPI.waveOutUnprepareHeader(Global.hwo, Global.WaveHdrList[i], Marshal.SizeOf(typeof(WinAPI.WaveHdr)));
        //            if (Global.WaveHdrList[i].dwUser != IntPtr.Zero)
        //            {
        //                GCHandle gch = GCHandle.FromIntPtr(Global.WaveHdrList[i].dwUser);
        //                gch.Free();
        //            }
        //        }
        //        Global.N_wavehdr = 0;
        //        //GlobalFreePtr(lpwh);
        //        Global.WaveHdrList.Clear();
        //    }
        //    WinAPI.waveOutClose(Global.hwo);
        //    //if (PcmBuf) { GlobalFreePtr(PcmBuf); PcmBuf = NULL; }
        //    Global.hwo = IntPtr.Zero;
        //}


        dousaMode = 0;
    }

    protected void finalinze() {
        free();
    }


    public void opmInt(Runnable proc) {
        opmIntProc = proc;
    }

    public byte adpcmPeek() {
        return adpcm.adpcmReg;
    }

    public void adpcmPoke(byte data) {
        //#ifdef ADPCM_POLY
// #if false
        // PCM8テスト
// if (data & 0x02) { // ADPCM再生開始
//  static int pcm8_pantbl[]={3,1,2,0};
//  int minch=0,ch;
//   int minlen=0xFFFFFFFF;
//  for (ch=0; ch<PCM8_NCH; ++ch) {
//   if (( int)pcm8[ch].GetRest() < minlen) {
//    minlen = pcm8[ch].GetRest();
//    minch = ch;
//   }
//  }
//  if (adpcm.DmaReg[0x05] & 0x08) { // チェイニング動作
//   if (!(adpcm.DmaReg[0x05] & 0x04)) { // アレイチェイン
//    pcm8[minch].Aot(bswapl((byte[][])adpcm.DmaReg[0x1C])),
//     (8<<16)+(ADPCMRATETBL[AdpcmBaseClock][(PpiReg>>2)&3]<<8)+pcm8_pantbl[PpiReg&3],
//     bswapw(( short[])(adpcm.DmaReg[0x1A])));
//   } else {      // リンクアレイチェイン
//    pcm8[minch].Lot(bswapl((byte[][])(adpcm.DmaReg[0x1C])),
//     (8<<16)+(ADPCMRATETBL[AdpcmBaseClock][(PpiReg>>2)&3]<<8)+pcm8_pantbl[PpiReg&3]);
//   }
//  } else { // ノーマル転送
//   pcm8[minch].Out(bswapl((byte[][])(adpcm.DmaReg[0x0C])),
//    (8<<16)+(ADPCMRATETBL[AdpcmBaseClock][(PpiReg>>2)&3]<<8)+pcm8_pantbl[PpiReg&3],
//    bswapw((short [])(adpcm.DmaReg[0x0A])));
//  }
//  if (adpcm.IntProc != null) {
//   adpcm.IntProc();
//  }
// } else if (data & 0x01) { // 再生動作停止
// }
// return;
// #endif

        // オリジナル
        if ((data & 0x02) != 0) {   // ADPCM再生開始
            adpcm.adpcmReg &= 0x7F;
        } else if ((data & 0x01) != 0) {   // 再生動作停止
            adpcm.adpcmReg |= 0x80;
            adpcm.reset();
        }
    }

    public byte ppiPeek() {
        return ppiReg;
    }

    public void ppiPoke(byte data) {
        ppiReg = data;
        setAdpcmRate();
    }

    public void ppiCtrl(byte data) {
        if ((data & 0x80) == 0) {
            if ((data & 0x01) != 0) {
                ppiReg |= (byte) (1 << ((data >> 1) & 7));
            } else {
                ppiReg &= (byte) (0xFF ^ (1 << ((data >> 1) & 7)));
            }
            setAdpcmRate();
        }
    }

    public byte dmaPeek(byte adrs) {
        if (adrs >= 0x40) return 0;
        if (adrs == 0x00) {
            if ((adpcm.adpcmReg & 0x80) == 0) {   // ADPCM 再生中
                adpcm.dmaReg[0x00] |= 0x02;
                return (byte) (adpcm.dmaReg[0x00] | 0x01);
            }
        }
        return adpcm.dmaReg[adrs];
    }

    public void dmaPoke(byte adrs, byte data) {
        if (adrs >= 0x40) return;
        switch (adrs) {
        case 0x00:  // CSR
            data &= 0xF6;                   // ACTとPCSはクリアしない
            adpcm.dmaReg[adrs] &= (byte) ~data;
            if ((data & 0x10) != 0) {
                adpcm.dmaReg[0x01] = 0;
            }
            return;
        case 0x01:  // CER
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
            if ((adpcm.dmaReg[0x00] & 0x08) != 0) {   // ACT==1 ?
                adpcm.dmaError((byte) 0x02);   // 動作タイミングエラー
                break;
            }
            adpcm.dmaReg[adrs] = data;
            break;
        case 0x1A:  // BTC
        case 0x1B:  // BTC
        case 0x1C:  // BAR
        case 0x1D:  // BAR
        case 0x1E:  // BAR
        case 0x1F:  // BAR
        case 0x25:  // NIV
        case 0x27:  // EIV
        case 0x2D:  // CPR
        case 0x39:  // BFC
        case 0x3F:  // GCR
            adpcm.dmaReg[adrs] = data;
            break;

        case 0x07:
            adpcm.dmaReg[0x07] = (byte) (data & 0x78);
            if ((data & 0x80) != 0) {       // STR == 1 ?

                if ((adpcm.dmaReg[0x00] & 0xF8) != 0) {   // COC|BTC|NDT|ERR|ACT == 1 ?
                    adpcm.dmaError((byte) 0x02);   // 動作タイミングエラー
                    adpcm.dmaReg[0x07] = (byte) (data & 0x28);
                    break;
                }
                adpcm.dmaReg[0x00] |= 0x08; // ACT=1
                //adpcm.FinishFlag=0;
                if ((adpcm.dmaReg[0x04] & 0x08) != 0       // DPS != 0 ?
                        || (adpcm.dmaReg[0x06] & 0x03) != 0        // DAC != 00 ?
                        //|| Global.bswapl(*(byte**)&adpcm.DmaReg[0x14]) != (byte*)0x00E92003) {
                        || (
                        adpcm.dmaReg[0x14] * 0x1000000
                                + adpcm.dmaReg[0x15] * 0x10000
                                + adpcm.dmaReg[0x16] * 0x100
                                + adpcm.dmaReg[0x17]
                ) != 0x00E92003) {
                    adpcm.dmaError((byte) 0x0A);   // バスエラー(デバイスアドレス)
                    adpcm.dmaReg[0x07] = (byte) (data & 0x28);
                    break;
                }
                byte ocr;
                ocr = (byte) (adpcm.dmaReg[0x05] & 0xB0);
                if (ocr != 0x00 && ocr != 0x30) {   // DIR==1 || SIZE!=00&&SIZE!=11 ?
                    adpcm.dmaError((byte) 0x01);   // コンフィグレーションエラー
                    adpcm.dmaReg[0x07] = (byte) (data & 0x28);
                    break;
                }

            }
            if ((data & 0x40) != 0) {   // CNT == 1 ?
                if ((adpcm.dmaReg[0x00] & 0x48) != 0x08) {   // !(BTC==0&&ACT==1) ?
                    adpcm.dmaError((byte) 0x02);   // 動作タイミングエラー
                    adpcm.dmaReg[0x07] = (byte) (data & 0x28);
                    break;
                }

                if ((adpcm.dmaReg[0x05] & 0x08) != 0) {   // CHAIN == 10 or 11 ?
                    adpcm.dmaError((byte) 0x01);   // コンフィグレーションエラー
                    adpcm.dmaReg[0x07] = (byte) (data & 0x28);
                    break;
                }

            }
            if ((data & 0x10) != 0) {   // SAB == 1 ?
                if ((adpcm.dmaReg[0x00] & 0x08) != 0) {   // ACT == 1 ?
                    adpcm.dmaError((byte) 0x11);   // ソフトウェア強制停止
                    adpcm.dmaReg[0x07] = (byte) (data & 0x28);
                    break;
                }
            }
            if ((data & 0x80) != 0) {   // STR == 1 ?
                data &= 0x7F;

                if ((adpcm.dmaReg[0x05] & 0x08) != 0) {   // チェイニング動作
                    if ((adpcm.dmaReg[0x05] & 0x04) == 0) {   // アレイチェイン
                        if (adpcm.dmaArrayChainSetNextMtcMar() != 0) {
                            adpcm.dmaReg[0x07] = (byte) (data & 0x28);
                            break;
                        }
                    } else {                       // リンクアレイチェイン
                        if (adpcm.dmaLinkArrayChainSetNextMtcMar() != 0) {
                            adpcm.dmaReg[0x07] = (byte) (data & 0x28);
                            break;
                        }
                    }
                }

                //if ((*(int*)&adpcm.DmaReg[0x0A]) == 0) {    // MTC == 0 ?
                if ((adpcm.dmaReg[0x0A] | adpcm.dmaReg[0x0B]) == 0) {    // MTC == 0 ?
                    adpcm.dmaError((byte) 0x0D);   // カウントエラー(メモリアドレス/メモリカウンタ)
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
        int ch;
        for (ch = 0; ch < Global.PCM8_NCH; ++ch) {
            pcm8[ch].init();
        }
        return 0;
    }

    public int setTotalVolume(int v) {
        if ((int) v <= 65535) {
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


    public void PushRegs() {
        opmRegNoBackup = opmRegNo;
    }

    public void PopRegs() {
        opmRegNo = opmRegNoBackup;
    }


    public void memReadFunc(Function<Integer, Integer> func) {
        if (func == null) {
            global.memRead = Global::memReadDefault;
        } else {
            global.memRead = func;
        }
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
