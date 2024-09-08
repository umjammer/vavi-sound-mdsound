package mdsound.x68sound;


public class Adpcm {
    private Global global = null;

    //
    private int scale;
    // 16bit PCM data
    private int pcm;
    // HPF用 16bit PCM data
    private int inpPcm, inpPcmPrev, outPcm;
    // HPF用
    private int outInpPcm, outInpPcmPrev;
    // 187500(15625*12), 125000(10416.66*12), 93750(7812.5*12), 62500(5208.33*12), 46875(3906.25*12), ...
    private int adpcmRate;
    private int rateCounter;
    // ADPCM 1サンプルのデータの保存
    private int n1Data;
    // 0 or 1
    private int n1DataFlag;

    // 割り込みアドレス
    public Runnable intProc;
    // 割り込みアドレス
    public Runnable errIntProc;
    //// int AdpcmFlag; // 0:非動作  1:再生中
    //// int PpiReg; // PPI レジスタの内容
    //// int DmaCsr; // DMA CSR レジスタの内容
    //// int DmaCcr; // DMA CCR レジスタの内容
    //// int DmaFlag; // 0:DMA非動作  1:DMA動作中
    //inline int DmaGetByte();
    public byte dmaLastValue;
    public byte adpcmReg;
    public byte[] dmaReg = new byte[0x40];
    public int finishCounter;

    public Adpcm(Global global) {
        this.global = global;
    }

    public void setAdpcmRate(int rate) {
        adpcmRate = Global.ADPCMRATEADDTBL[rate & 7];
    }

    private static final byte[] DmaRegInit = new byte[] {
        /*+00*/ 0x00, 0x00, // CSR/CER
        /*+02*/ (byte) 0xff, (byte) 0xff,
        /*+04*/ (byte) 0x80, 0x32, // DCR/OCR
        /*+06*/ 0x04, 0x08, // SCR/CCR
        /*+08*/ (byte) 0xff, (byte) 0xff,
        /*+0A*/ 0x00, 0x00, // MTC
        /*+0C*/ 0x00, 0x00, // MAR
        /*+0E*/ 0x00, 0x00, // MAR
        /*+10*/ (byte) 0xff, (byte) 0xff,
        /*+12*/ (byte) 0xff, (byte) 0xff,
        /*+14*/ 0x00, (byte) 0xE9, // DAR
        /*+16*/ 0x20, 0x03, // DAR
        /*+18*/ (byte) 0xff, (byte) 0xff,
        /*+1A*/ 0x00, 0x00, // BTC
        /*+1C*/ 0x00, 0x00, // BAR
        /*+1E*/ 0x00, 0x00, // BAR
        /*+20*/ (byte) 0xff, (byte) 0xff,
        /*+22*/ (byte) 0xff, (byte) 0xff,
        /*+24*/ (byte) 0xff, 0x6A, // NIV
        /*+26*/ (byte) 0xff, 0x6B, // EIV
        /*+28*/ (byte) 0xff, 0x05, // MFC
        /*+2A*/ (byte) 0xff, (byte) 0xff,
        /*+2C*/ (byte) 0xff, 0x01, // CPR
        /*+2E*/ (byte) 0xff, (byte) 0xff,
        /*+30*/ (byte) 0xff, 0x05, // DFC
        /*+32*/ (byte) 0xff, (byte) 0xff,
        /*+34*/ (byte) 0xff, (byte) 0xff,
        /*+36*/ (byte) 0xff, (byte) 0xff,
        /*+38*/ (byte) 0xff, 0x05, // BFC
        /*+3A*/ (byte) 0xff, (byte) 0xff,
        /*+3C*/ (byte) 0xff, (byte) 0xff,
        /*+3E*/ (byte) 0xff, 0x00, // GCR
    };

    public void init() {
        scale = 0;
        pcm = 0;
        inpPcm = inpPcmPrev = outPcm = 0;
        outInpPcm = outInpPcmPrev = 0;
        adpcmRate = 15625 * 12;
        rateCounter = 0;
        n1Data = 0;
        n1DataFlag = 0;
        intProc = null;
        errIntProc = null;
        dmaLastValue = 0;
        adpcmReg = (byte) 0xC7;
        System.arraycopy(DmaRegInit, 0, dmaReg, 0, 0x40);
        finishCounter = 3;
    }

    public void initSamprate() {
        rateCounter = 0;
    }

    // ADPCM キーオン時の処理
    public void reset() {
        scale = 0;

        pcm = 0;
        inpPcm = inpPcmPrev = outPcm = 0;
        outInpPcm = outInpPcmPrev = 0;

        n1Data = 0;
        n1DataFlag = 0;
    }

    public void dmaError(byte errorCode) {
        dmaReg[0x00] &= 0xF7; // ACT=0
        dmaReg[0x00] |= 0x90; // COC=ERR=1
        dmaReg[0x01] = errorCode; // CER=errorcode
        if ((dmaReg[0x07] & 0x08) != 0) { // INT==1?
            errIntProc.run();
        }
    }

    public void dmaFinish() {
        dmaReg[0x00] &= 0xF7; // ACT=0
        dmaReg[0x00] |= 0x80; // COC=1
        if ((dmaReg[0x07] & 0x08) != 0) { // INT==1?
            intProc.run();
        }
    }

    public int dmaContinueSetNextMtcMar() {
        dmaReg[0x07] &= (0xff - 0x40); // CNT=0

        dmaReg[0x0A] = dmaReg[0x1A]; // BTC . MTC
        dmaReg[0x0B] = dmaReg[0x1B];
        dmaReg[0x0C] = dmaReg[0x1C]; // BAR . MAR
        dmaReg[0x0D] = dmaReg[0x1D];
        dmaReg[0x0E] = dmaReg[0x1E];
        dmaReg[0x0F] = dmaReg[0x1F];

        dmaReg[0x29] = dmaReg[0x39]; // BFC . MFC

        if ((dmaReg[0x0A] | dmaReg[0x0B]) == 0) { // MTC == 0 ?
            dmaError((byte) 0x0D); // カウントエラー(メモリアドレス/メモリカウンタ)
            return 1;
        }

        dmaReg[0x00] |= 0x40; // BTC=1

        if ((dmaReg[0x07] & 0x08) != 0) { // INT==1?
            intProc.run();
        }
        return 0;
    }

    public int dmaArrayChainSetNextMtcMar() {
        int btc = dmaReg[0x1A] * 0x100 + dmaReg[0x1B];
        if (btc == 0) {
            dmaFinish();
            finishCounter = 0;
            return 1;
        }
        --btc;
        dmaReg[0x1A] = (byte) (btc >> 8);
        dmaReg[0x1B] = (byte) btc;

        int bar = dmaReg[0x1C] * 0x1000000
                + dmaReg[0x1D] * 0x10000
                + dmaReg[0x1E] * 0x100
                + dmaReg[0x1F];
        int mem0 = global.memRead.apply(bar++);
        int mem1 = global.memRead.apply(bar++);
        int mem2 = global.memRead.apply(bar++);
        int mem3 = global.memRead.apply(bar++);
        int mem4 = global.memRead.apply(bar++);
        int mem5 = global.memRead.apply(bar++);
        if ((mem0 | mem1 | mem2 | mem3 | mem4 | mem5) == -1) {
            dmaError((byte) 0x0B); // バスエラー(ベースアドレス/ベースカウンタ)
            return 1;
        }
        //*(byte**)&DmaReg[0x1C] = Global.bswapl(bar);
        dmaReg[0x1C] = (byte) (bar >> 24);
        dmaReg[0x1D] = (byte) (bar >> 16);
        dmaReg[0x1E] = (byte) (bar >> 8);
        dmaReg[0x1F] = (byte) (bar);

        dmaReg[0x0C] = (byte) mem0; // MAR
        dmaReg[0x0D] = (byte) mem1;
        dmaReg[0x0E] = (byte) mem2;
        dmaReg[0x0F] = (byte) mem3;
        dmaReg[0x0A] = (byte) mem4; // MTC
        dmaReg[0x0B] = (byte) mem5;

        if ((dmaReg[0x0A] | dmaReg[0x0B]) == 0) { // MTC == 0 ?
            dmaError((byte) 0x0D); // カウントエラー(メモリアドレス/メモリカウンタ)
            return 1;
        }
        return 0;
    }

    public int dmaLinkArrayChainSetNextMtcMar() {
        int bar = dmaReg[0x1C] * 0x1000000
                + dmaReg[0x1D] * 0x10000
                + dmaReg[0x1E] * 0x100
                + dmaReg[0x1F];
        if (bar == 0) {
            dmaFinish();
            finishCounter = 0;
            return 1;
        }

        int mem0 = global.memRead.apply(bar++);
        int mem1 = global.memRead.apply(bar++);
        int mem2 = global.memRead.apply(bar++);
        int mem3 = global.memRead.apply(bar++);
        int mem4 = global.memRead.apply(bar++);
        int mem5 = global.memRead.apply(bar++);
        int mem6 = global.memRead.apply(bar++);
        int mem7 = global.memRead.apply(bar++);
        int mem8 = global.memRead.apply(bar++);
        int mem9 = global.memRead.apply(bar++);
        if ((mem0 | mem1 | mem2 | mem3 | mem4 | mem5 | mem6 | mem7 | mem8 | mem9) == -1) {
            dmaError((byte) 0x0B); // バスエラー(ベースアドレス/ベースカウンタ)
            return 1;
        }
        //*(byte**)&DmaReg[0x1C] = Global.bswapl(bar);
        dmaReg[0x1C] = (byte) (bar >> 24);
        dmaReg[0x1D] = (byte) (bar >> 16);
        dmaReg[0x1E] = (byte) (bar >> 8);
        dmaReg[0x1F] = (byte) (bar);

        dmaReg[0x0C] = (byte) mem0; // MAR
        dmaReg[0x0D] = (byte) mem1;
        dmaReg[0x0E] = (byte) mem2;
        dmaReg[0x0F] = (byte) mem3;
        dmaReg[0x0A] = (byte) mem4; // MTC
        dmaReg[0x0B] = (byte) mem5;
        dmaReg[0x1C] = (byte) mem6; // BAR
        dmaReg[0x1D] = (byte) mem7;
        dmaReg[0x1E] = (byte) mem8;
        dmaReg[0x1F] = (byte) mem9;

        if ((dmaReg[0x0A] | dmaReg[0x0B]) == 0) { // MTC == 0 ?
            dmaError((byte) 0x0D); // カウントエラー(メモリアドレス/メモリカウンタ)
            return 1;
        }
        return 0;
    }

    private static final int[] MACTBL = new int[] {0, 1, -1, 1};

    public int dmaGetByte() {
        if (((dmaReg[0x00] & 0x08) == 0) || ((dmaReg[0x07] & 0x20) != 0)) { // ACT==0 || HLT==1 ?
            return 0x80000000;
        }
        int mtc;
        mtc = dmaReg[0x0A] * 0x100 + dmaReg[0x0B];
        if (mtc == 0) {
            //if (DmaReg[0x07] & 0x40) { // Continue動作
            // if (DmaContinueSetNextMtcMar()) {
            //return 0x80000000;
            // }
            // mtc = bswapw(*(unsigned short *)&DmaReg[0x0A]);
            //} else {
            return 0x80000000;
            //}
        }

        int mar = dmaReg[0x0C] * 0x1000000
                + dmaReg[0x0D] * 0x10000
                + dmaReg[0x0E] * 0x100
                + dmaReg[0x0F];
        int mem = global.memRead.apply(mar);
        if (mem == -1) {
            dmaError((byte) 0x09); // バスエラー(メモリアドレス/メモリカウンタ)
            return -2147483648;// 0x80000000;
        }
        dmaLastValue = (byte) mem;
        mar += MACTBL[(dmaReg[0x06] >> 2) & 3];
        dmaReg[0x0C] = (byte) (mar >> 24);
        dmaReg[0x0D] = (byte) (mar >> 16);
        dmaReg[0x0E] = (byte) (mar >> 8);
        dmaReg[0x0F] = (byte) (mar);

        --mtc;
        dmaReg[0x0A] = (byte) (mtc >> 8);
        dmaReg[0x0B] = (byte) mtc;

        try {
            if (mtc == 0) {
                if ((dmaReg[0x07] & 0x40) != 0) { // Continue動作
                    if (dmaContinueSetNextMtcMar() != 0) {
                        throw new IllegalStateException("dmaContinueSetNextMtcMar");
                    }
                } else if ((dmaReg[0x05] & 0x08) != 0) { // チェイニング動作
                    if ((dmaReg[0x05] & 0x04) == 0) { // アレイチェイン
                        if (dmaArrayChainSetNextMtcMar() != 0) {
                            throw new IllegalStateException("dmaArrayChainSetNextMtcMar");
                        }
                    } else { // リンクアレイチェイン
                        if (dmaLinkArrayChainSetNextMtcMar() != 0) {
                            throw new IllegalStateException("dmaLinkArrayChainSetNextMtcMar");
                        }
                    }
                } else { // ノーマル転送終了
                    //   if (!(DmaReg[0x00] & 0x40)) { // BTC=1 ?
                    //    if (DmaContinueSetNextMtcMar()) {
                    //     throw "";
                    //    }
                    //   } else {
                    dmaFinish();
                    finishCounter = 0;
                    //   }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return dmaLastValue;
    }

    private static final int MAXPCMVAL = (2047);

    // adpcmを入力して InpPcm の値を変化させる
    // -2047<<(4+4) <= InpPcm <= +2047<<(4+4)
    public void adpcm2pcm(byte adpcm) {

        int dltL = Global.dltLTBL[scale];
        dltL = (dltL & ((adpcm & 4) != 0 ? -1 : 0))
                + ((dltL >> 1) & ((adpcm & 2) != 0 ? -1 : 0))
                + ((dltL >> 2) & ((adpcm & 1) != 0 ? -1 : 0)) + (dltL >> 3);
        int sign = (adpcm & 8) != 0 ? -1 : 0;
        dltL = (dltL ^ sign) + (sign & 1);
        pcm += dltL;

        if ((pcm + MAXPCMVAL) > (MAXPCMVAL * 2)) {
            if ((pcm + MAXPCMVAL) >= (MAXPCMVAL * 2)) {
                pcm = MAXPCMVAL;
            } else {
                pcm = -MAXPCMVAL;
            }
        }

        inpPcm = (pcm & -4) << (4 + 4);

        scale += Global.DCT[adpcm];
        if (scale > 48) {
            if (scale >= 48) {
                scale = 48;
            } else {
                scale = 0;
            }
        }
    }

    // -32768<<4 <= retval <= +32768<<4
    public int getPcm() {
        if ((adpcmReg & 0x80) != 0) { // ADPCM 停止中
            return 0x80000000;
        }
        rateCounter -= adpcmRate;
        while (rateCounter < 0) {
            if (n1DataFlag == 0) { // 次のADPCMデータが内部にない場合
                int n10Data; // (N1Data << 4) | N0Data
                n10Data = dmaGetByte(); // DMA転送(1バイト)
                if (n10Data == 0x80000000) {
                    rateCounter = 0;
                    return 0x80000000;
                }
                adpcm2pcm((byte) (n10Data & 0x0F)); // InpPcm に値が入る
                n1Data = (n10Data >> 4) & 0x0F;
                n1DataFlag = 1;
            } else {
                adpcm2pcm((byte) n1Data); // InpPcm に値が入る
                n1DataFlag = 0;
            }
            rateCounter += 15625 * 12;
        }
        outPcm = ((inpPcm << 9) - (inpPcmPrev << 9) + 459 * outPcm) >> 9;
        inpPcmPrev = inpPcm;

        return (outPcm * global.totalVolume) >> 8;
    }

    // -32768<<4 <= retval <= +32768<<4
    public int getPcm62() {
        if ((adpcmReg & 0x80) != 0) { // ADPCM 停止中
            return 0x80000000;
        }
        rateCounter -= adpcmRate;
        while (rateCounter < 0) {
            if (n1DataFlag == 0) { // 次のADPCMデータが内部にない場合
                int n10Data; // (N1Data << 4) | N0Data
                n10Data = dmaGetByte(); // DMA転送(1バイト)
                if (n10Data == 0x80000000) {
                    rateCounter = 0;
                    return 0x80000000;
                }
                adpcm2pcm((byte) (n10Data & 0x0F)); // InpPcm に値が入る
                n1Data = (n10Data >> 4) & 0x0F;
                n1DataFlag = 1;
            } else {
                adpcm2pcm((byte) n1Data); // InpPcm に値が入る
                n1DataFlag = 0;
            }
            rateCounter += 15625 * 12 * 4;

        }
        outInpPcm = (inpPcm << 9) - (inpPcmPrev << 9) + outInpPcm - (outInpPcm >> 5) - (outInpPcm >> 10);
        inpPcmPrev = inpPcm;
        outPcm = outInpPcm - outInpPcmPrev + outPcm - (outPcm >> 8) - (outPcm >> 9) - (outPcm >> 12);
        outInpPcmPrev = outInpPcm;
        return (outPcm >> 9);
    }
}


