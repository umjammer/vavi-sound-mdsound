package mdsound.x68sound;


import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import static java.lang.System.getLogger;


public class Adpcm {

    private static final Logger logger = getLogger(Adpcm.class.getName());

    private static final Global global = Global.getInstance();

    //
    private int scale;
    /** 16bit PCM data */
    private int pcm;
    /** 16bit PCM data for HPF */
    private int inpPcm, inpPcmPrev, outPcm;
    /** for HPF */
    private int outInpPcm, outInpPcmPrev;
    /** 187500(15625*12), 125000(10416.66*12), 93750(7812.5*12), 62500(5208.33*12), 46875(3906.25*12), ... */
    private int adpcmRate;
    private int rateCounter;
    /** ADPCM 1 sample data storage */
    private int n1Data;
    /** 0 or 1 */
    private int n1DataFlag;

    /** Interrupt Address */
    public Runnable intProc;
    /** Interrupt Address */
    public Runnable errIntProc;
//    /** 0: Not working 1: Playing */
//    int adpcmFlag;
//    /** PPI Register Contents */
//    int ppiReg;
//    /** DMA CSR Register Contents */
//    int dmaCsr;
//    /** DMA CCR register contents */
//    int dmaCcr;
//    /** 0: DMA not operating 1: DMA operating */
//    int dmaFlag;
//    inline int dmaGetByte();
    public int dmaLastValue;
    public int adpcmReg;
    public int[] dmaReg = new int[0x40];
    public int finishCounter;

    public void setAdpcmRate(int rate) {
        adpcmRate = Global.ADPCMRATEADDTBL[rate & 7];
    }

    private static final int[] DmaRegInit = {
        /* +00 */ 0x00, 0x00, // CSR/CER
        /* +02 */ 0xff, 0xff,
        /* +04 */ 0x80, 0x32, // DCR/OCR
        /* +06 */ 0x04, 0x08, // SCR/CCR
        /* +08 */ 0xff, 0xff,
        /* +0A */ 0x00, 0x00, // MTC
        /* +0C */ 0x00, 0x00, // MAR
        /* +0E */ 0x00, 0x00, // MAR
        /* +10 */ 0xff, 0xff,
        /* +12 */ 0xff, 0xff,
        /* +14 */ 0x00, 0xE9, // DAR
        /* +16 */ 0x20, 0x03, // DAR
        /* +18 */ 0xff, 0xff,
        /* +1A */ 0x00, 0x00, // BTC
        /* +1C */ 0x00, 0x00, // BAR
        /* +1E */ 0x00, 0x00, // BAR
        /* +20 */ 0xff, 0xff,
        /* +22 */ 0xff, 0xff,
        /* +24 */ 0xff, 0x6A, // NIV
        /* +26 */ 0xff, 0x6B, // EIV
        /* +28 */ 0xff, 0x05, // MFC
        /* +2A */ 0xff, 0xff,
        /* +2C */ 0xff, 0x01, // CPR
        /* +2E */ 0xff, 0xff,
        /* +30 */ 0xff, 0x05, // DFC
        /* +32 */ 0xff, 0xff,
        /* +34 */ 0xff, 0xff,
        /* +36 */ 0xff, 0xff,
        /* +38 */ 0xff, 0x05, // BFC
        /* +3A */ 0xff, 0xff,
        /* +3C */ 0xff, 0xff,
        /* +3E */ 0xff, 0x00, // GCR
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
        adpcmReg = 0xc7;
        System.arraycopy(DmaRegInit, 0, dmaReg, 0, 0x40);
        finishCounter = 3;
    }

    public void initSampleRate() {
        rateCounter = 0;
    }

    /** ADPCM Key-on processing */
    public void reset() {
        scale = 0;

        pcm = 0;
        inpPcm = inpPcmPrev = outPcm = 0;
        outInpPcm = outInpPcmPrev = 0;

        n1Data = 0;
        n1DataFlag = 0;
    }

    public void dmaError(int errorCode) {
        dmaReg[0x00] &= 0xf7; // ACT=0
        dmaReg[0x00] |= 0x90; // COC=ERR=1
        dmaReg[0x01] = errorCode; // CER=error-code
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

        dmaReg[0x0a] = dmaReg[0x1a]; // BTC -> MTC
        dmaReg[0x0b] = dmaReg[0x1b];
        dmaReg[0x0c] = dmaReg[0x1c]; // BAR -> MAR
        dmaReg[0x0d] = dmaReg[0x1d];
        dmaReg[0x0e] = dmaReg[0x1e];
        dmaReg[0x0f] = dmaReg[0x1f];

        dmaReg[0x29] = dmaReg[0x39]; // BFC -> MFC

        if ((dmaReg[0x0a] | dmaReg[0x0b]) == 0) { // MTC == 0 ?
            dmaError(0x0d); // Count error (memory address/memory counter)
            return 1;
        }

        dmaReg[0x00] |= 0x40; // BTC=1

        if ((dmaReg[0x07] & 0x08) != 0) { // INT==1?
            intProc.run();
        }
        return 0;
    }

    public int dmaArrayChainSetNextMtcMar() {
        int btc = dmaReg[0x1a] * 0x100 + dmaReg[0x1b];
        if (btc == 0) {
            dmaFinish();
            finishCounter = 0;
            return 1;
        }
        --btc;
        dmaReg[0x1a] = (btc >> 8) & 0xff;
        dmaReg[0x1b] = btc & 0xff;

        int bar = dmaReg[0x1c] * 0x100_0000
                + dmaReg[0x1d] * 0x1_0000
                + dmaReg[0x1e] * 0x100
                + dmaReg[0x1f];
        int mem0 = global.memRead.apply(bar++);
        int mem1 = global.memRead.apply(bar++);
        int mem2 = global.memRead.apply(bar++);
        int mem3 = global.memRead.apply(bar++);
        int mem4 = global.memRead.apply(bar++);
        int mem5 = global.memRead.apply(bar++);
        if ((mem0 | mem1 | mem2 | mem3 | mem4 | mem5) == -1) {
            dmaError(0x0B); // Bus error (base address/base counter)
            return 1;
        }
//        dmaReg[0x1c] = Global.bswapl(bar);
        dmaReg[0x1c] = (bar >> 24) & 0xff;
        dmaReg[0x1d] = (bar >> 16) & 0xff;
        dmaReg[0x1e] = (bar >> 8) & 0xff;
        dmaReg[0x1f] = bar & 0xff;

        dmaReg[0x0c] = mem0; // MAR
        dmaReg[0x0d] = mem1;
        dmaReg[0x0e] = mem2;
        dmaReg[0x0f] = mem3;
        dmaReg[0x0a] = mem4; // MTC
        dmaReg[0x0b] = mem5;

        if ((dmaReg[0x0a] | dmaReg[0x0b]) == 0) { // MTC == 0 ?
            dmaError(0x0d); // Count error (memory address/memory counter)
            return 1;
        }
        return 0;
    }

    public int dmaLinkArrayChainSetNextMtcMar() {
        int bar = dmaReg[0x1c] * 0x10_00000
                + dmaReg[0x1d] * 0x1_0000
                + dmaReg[0x1e] * 0x100
                + dmaReg[0x1f];
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
            dmaError(0x0b); // Bus error (base address/base counter)
            return 1;
        }
        //dDmaReg[0x1C] = Global.bswapl(bar);
        dmaReg[0x1c] = (bar >> 24) & 0xff;
        dmaReg[0x1d] = (bar >> 16) & 0xff;
        dmaReg[0x1e] = (bar >> 8) & 0xff;
        dmaReg[0x1f] = bar & 0xff;

        dmaReg[0x0c] = mem0; // MAR
        dmaReg[0x0d] = mem1;
        dmaReg[0x0e] = mem2;
        dmaReg[0x0f] = mem3;
        dmaReg[0x0a] = mem4; // MTC
        dmaReg[0x0b] = mem5;
        dmaReg[0x1c] = mem6; // BAR
        dmaReg[0x1d] = mem7;
        dmaReg[0x1e] = mem8;
        dmaReg[0x1f] = mem9;

        if ((dmaReg[0x0a] | dmaReg[0x0b]) == 0) { // MTC == 0 ?
            dmaError(0x0d); // Count error (memory address/memory counter)
            return 1;
        }
        return 0;
    }

    private static final int[] MACTBL = new int[] {0, 1, -1, 1};

    public int dmaGetByte() {
        if (((dmaReg[0x00] & 0x08) == 0) || ((dmaReg[0x07] & 0x20) != 0)) { // ACT==0 || HLT==1 ?
            return 0x8000_0000;
        }
        int mtc;
        mtc = dmaReg[0x0a] * 0x100 + dmaReg[0x0b];
        if (mtc == 0) {
//            if (dmaReg[0x07] & 0x40) { // Continue動作
//                if (dmaContinueSetNextMtcMar()) {
//                    return 0x80000000;
//                }
//                mtc = bswapw((short) dmaReg[0x0a]);
//            } else {
                return 0x8000_0000;
//            }
        }

        int mar = dmaReg[0x0c] * 0x100_0000
                + dmaReg[0x0d] * 0x1_0000
                + dmaReg[0x0e] * 0x100
                + dmaReg[0x0f];
        int mem = global.memRead.apply(mar);
        if (mem == -1) {
            dmaError(0x09); // Bus error (memory address/memory counter)
            return -2147483648; // 0x8000_0000;
        }
        dmaLastValue = mem;
        mar += MACTBL[(dmaReg[0x06] >> 2) & 3];
        dmaReg[0x0c] = (mar >> 24) & 0xff;
        dmaReg[0x0d] = (mar >> 16) & 0xff;
        dmaReg[0x0e] = (mar >> 8) & 0xff;
        dmaReg[0x0f] = mar & 0xff;

        --mtc;
        dmaReg[0x0a] = (mtc >> 8) & 0xff;
        dmaReg[0x0b] = mtc & 0xff;

        try {
            if (mtc == 0) {
                if ((dmaReg[0x07] & 0x40) != 0) { // Continue action
                    if (dmaContinueSetNextMtcMar() != 0) {
                        throw new IllegalStateException("dmaContinueSetNextMtcMar");
                    }
                } else if ((dmaReg[0x05] & 0x08) != 0) { // Chaining Operation
                    if ((dmaReg[0x05] & 0x04) == 0) { // Array Chain
                        if (dmaArrayChainSetNextMtcMar() != 0) {
                            throw new IllegalStateException("dmaArrayChainSetNextMtcMar");
                        }
                    } else { // Link Array Chain
                        if (dmaLinkArrayChainSetNextMtcMar() != 0) {
                            throw new IllegalStateException("dmaLinkArrayChainSetNextMtcMar");
                        }
                    }
                } else { // Normal transfer completed
//                    if (!(dmaReg[0x00] & 0x40)) { // BTC=1 ?
//                        if (dmaContinueSetNextMtcMar()) {
//                            throw new IllegalStateException("");
//                        }
//                    } else {
                        dmaFinish();
                        finishCounter = 0;
//                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.TRACE, e.getMessage(), e);
        }

        return dmaLastValue;
    }

    private static final int MAX_PCM_VAL = 2047;

    /**
     * Enter adpcm to change the value of InpPcm
     * -2047<<(4+4) <= InpPcm <= +2047<<(4+4)
     */
    public void adpcm2pcm(int adpcm) {

        int dltL = Global.dltLTBL[scale];
        dltL = (dltL & ((adpcm & 4) != 0 ? -1 : 0))
                + ((dltL >> 1) & ((adpcm & 2) != 0 ? -1 : 0))
                + ((dltL >> 2) & ((adpcm & 1) != 0 ? -1 : 0)) + (dltL >> 3);
        int sign = (adpcm & 8) != 0 ? -1 : 0;
        dltL = (dltL ^ sign) + (sign & 1);
        pcm += dltL;

        if (((pcm + MAX_PCM_VAL) & 0xffff_ffffL) > ((MAX_PCM_VAL * 2) & 0xffff_ffffL)) {
            if ((pcm + MAX_PCM_VAL) >= (MAX_PCM_VAL * 2)) {
                pcm = MAX_PCM_VAL;
            } else {
                pcm = -MAX_PCM_VAL;
            }
        }

        inpPcm = (pcm & -4) << (4 + 4);

        scale += Global.DCT[adpcm];
        if ((scale & 0xffff_ffffL) > 48L) {
            if (scale >= 48) {
                scale = 48;
            } else {
                scale = 0;
            }
        }
    }

    // -32768<<4 <= retval <= +32768<<4
    public int getPcm() {
        if ((adpcmReg & 0x80) != 0) { // ADPCM stopped
            return 0x8000_0000;
        }
        rateCounter -= adpcmRate;
        while (rateCounter < 0) {
            if (n1DataFlag == 0) { // If the next ADPCM data is not available
                int n10Data; // (N1Data << 4) | N0Data
                n10Data = dmaGetByte(); // DMA transfer (1 byte)
                if (n10Data == 0x8000_0000) {
                    rateCounter = 0;
                    return 0x8000_0000;
                }
                adpcm2pcm(n10Data & 0x0F); // A value is entered in InpPcm
                n1Data = (n10Data >> 4) & 0x0F;
                n1DataFlag = 1;
            } else {
                adpcm2pcm(n1Data); // A value is entered in InpPcm
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
        if ((adpcmReg & 0x80) != 0) { // ADPCM stopped
            return 0x8000_0000;
        }
        rateCounter -= adpcmRate;
        while (rateCounter < 0) {
            if (n1DataFlag == 0) { // If the next ADPCM data is not available
                int n10Data; // (N1Data << 4) | N0Data
                n10Data = dmaGetByte(); // DMA transfer (1 byte)
                if (n10Data == 0x8000_0000) {
                    rateCounter = 0;
                    return 0x8000_0000;
                }
                adpcm2pcm(n10Data & 0x0f); // A value is entered in InpPcm
                n1Data = (n10Data >> 4) & 0x0f;
                n1DataFlag = 1;
            } else {
                adpcm2pcm(n1Data); // A value is entered in InpPcm
                n1DataFlag = 0;
            }
            rateCounter += 15625 * 12 * 4;

        }
        outInpPcm = (inpPcm << 9) - (inpPcmPrev << 9) + outInpPcm - (outInpPcm >> 5) - (outInpPcm >> 10);
        inpPcmPrev = inpPcm;
        outPcm = outInpPcm - outInpPcmPrev + outPcm - (outPcm >> 8) - (outPcm >> 9) - (outPcm >> 12);
        outInpPcmPrev = outInpPcm;
        return outPcm >> 9;
    }
}


