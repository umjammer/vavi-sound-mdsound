package mdsound.x68sound;


import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import static java.lang.System.getLogger;


public class Pcm8 {

    private static final Logger logger = getLogger(Pcm8.class.getName());

    private static final Global global = Global.getInstance();

    private int scale; //
    /** 16bit PCM data */
    private int pcm;
    /** Previous data of 16bit, 8bit PCM */
    private int pcm16Prev;
    /** For HPF 16bit PCM data */
    private int inpPcm, inpPcmPrev, outPcm;
    /** For HPF */
    private int outInpPcm, outInpPcmPrev;
    /** 187500(15625*12), 125000(10416.66*12), 93750(7812.5*12), 62500(5208.33*12), 46875(3906.25*12), ... */
    private int adpcmRate;
    private int rateCounter;
    /** ADPCM 1 sample data storage */
    private int n1Data;
    /** 0 or 1 */
    private int n1DataFlag;

    private int mode;
    /** x/16 */
    private int volume;
    /** 0～4:ADPCM  5:16bitPCM  6:8bitPCM  7:謎 */
    private int pcmKind;

    public int dmaLastValue;
    public int adpcmReg;

    public byte[] dmaMarBuf;
    public int dmaMarPtr;

    public int dmaMtc;
    public byte[] dmaBarBuf;
    public int dmaBarPtr;
    public int dmaBtc;
    /** 0: No chain operation 0x08: Array chain 0x0C: Link array chain */
    public int dmaOcr;

    public Pcm8() {
        mode = 0x0008_0403;
        setMode(mode);
    }

    public void init() {
        adpcmReg = 0xc7; // ADPCM operation stopped

        scale = 0;
        pcm = 0;
        pcm16Prev = 0;
        inpPcm = inpPcmPrev = outPcm = 0;
        outInpPcm = outInpPcmPrev = 0;
        adpcmRate = 15625 * 12;
        rateCounter = 0;
        n1Data = 0;
        n1DataFlag = 0;
        dmaLastValue = 0;

        dmaMarBuf = null;
        dmaMarPtr = 0;
        dmaMtc = 0;
        dmaBarBuf = null;
        dmaBarPtr = 0;
        dmaBtc = 0;
        dmaOcr = 0;
    }

    private void initSampleRate() {
        rateCounter = 0;
    }

    public void reset() { // ADPCM Key-on processing
        scale = 0;
        pcm = 0;
        pcm16Prev = 0;
        inpPcm = inpPcmPrev = outPcm = 0;
        outInpPcm = outInpPcmPrev = 0;

        n1Data = 0;
        n1DataFlag = 0;
    }

    public int dmaArrayChainSetNextMtcMar() {
        if (dmaBtc == 0) {
            return 1;
        }
        --dmaBtc;

        int mem0, mem1, mem2, mem3, mem4, mem5;
        mem0 = global.memRead.apply(dmaBarPtr++);
        mem1 = global.memRead.apply(dmaBarPtr++);
        mem2 = global.memRead.apply(dmaBarPtr++);
        mem3 = global.memRead.apply(dmaBarPtr++);
        mem4 = global.memRead.apply(dmaBarPtr++);
        mem5 = global.memRead.apply(dmaBarPtr++);
        if ((mem0 | mem1 | mem2 | mem3 | mem4 | mem5) == -1) {
            // Bus error (base address/base counter)
            return 1;
        }
        //DmaMarBuf = DmaBarBuf;
        dmaMarPtr = (mem0 << 24) | (mem1 << 16) | (mem2 << 8) | (mem3); // MAR
        dmaMtc = (mem4 << 8) | (mem5); // MTC

        if (dmaMtc == 0) { // MTC == 0 ?
            // Count error (memory address/memory counter)
            return 1;
        }
        return 0;
    }

    public int dmaLinkArrayChainSetNextMtcMar() {
        if (dmaBarPtr == 0) {
            return 1;
        }

        int mem0, mem1, mem2, mem3, mem4, mem5;
        int mem6, mem7, mem8, mem9;
        mem0 = global.memRead.apply(dmaBarPtr++);
        mem1 = global.memRead.apply(dmaBarPtr++);
        mem2 = global.memRead.apply(dmaBarPtr++);
        mem3 = global.memRead.apply(dmaBarPtr++);
        mem4 = global.memRead.apply(dmaBarPtr++);
        mem5 = global.memRead.apply(dmaBarPtr++);
        mem6 = global.memRead.apply(dmaBarPtr++);
        mem7 = global.memRead.apply(dmaBarPtr++);
        mem8 = global.memRead.apply(dmaBarPtr++);
        mem9 = global.memRead.apply(dmaBarPtr++);
        if ((mem0 | mem1 | mem2 | mem3 | mem4 | mem5 | mem6 | mem7 | mem8 | mem9) == -1) {
            // Bus error (base address/base counter)
            return 1;
        }
        dmaMarPtr = (mem0 << 24) | (mem1 << 16) | (mem2 << 8) | (mem3); // MAR
        dmaMtc = (mem4 << 8) | (mem5); // MTC
        dmaBarPtr = (mem6 << 24) | (mem7 << 16) | (mem8 << 8) | (mem9); // BAR

        if (dmaMtc == 0) { // MTC == 0 ?
            // Count error (memory address/memory counter)
            return 1;
        }
        return 0;
    }

    public int dmaGetByte() {
        if (dmaMtc == 0) {
            return 0x8000_0000;
        }
        int mem = global.memRead.apply(dmaMarPtr);
        if (mem == -1) {
            // Bus error (memory address/memory counter)
            return 0x8000_0000;
        }
        dmaLastValue = mem;
        dmaMarPtr++;

        --dmaMtc;

        try {
            if (dmaMtc == 0) {
                if ((dmaOcr & 0x08) != 0) { // Chaining Operation
                    if ((dmaOcr & 0x04) == 0) { // Array Chain
                        if (dmaArrayChainSetNextMtcMar() != 0) {
                            throw new IllegalStateException("Array Chain Failed");
                        }
                    } else { // Link Array Chain
                        if (dmaLinkArrayChainSetNextMtcMar() != 0) {
                            throw new IllegalStateException("Link Array Chain Filed");
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.TRACE, e.getMessage(), e);
        }

        return dmaLastValue;
    }


    private static final int MAXPCMVAL = 2047;
    private static final int[] HPF_shift_tbl = new int[] {0, 0, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 4,};

    /**
     * Enter adpcm to change the value of InpPcm
     * <pre>
     * -2047<<(4+4) <= InpPcm <= +2047<<(4+4)
     * </pre>
     */
    public void adpcm2pcm(byte adpcm) {

        int dltL = Global.dltLTBL[scale];
        dltL = (dltL & ((adpcm & 4) != 0 ? -1 : 0))
                + ((dltL >> 1) & ((adpcm & 2) != 0 ? -1 : 0))
                + ((dltL >> 2) & ((adpcm & 1) != 0 ? -1 : 0)) + (dltL >> 3);
        int sign = (adpcm & 8) != 0 ? -1 : 0;
        dltL = (dltL ^ sign) + (sign & 1);
        pcm += dltL;

        if (((pcm + MAXPCMVAL) & 0xffff_ffffL) > ((MAXPCMVAL * 2) & 0xffff_ffffL)) {
            if ((pcm + MAXPCMVAL) >= (MAXPCMVAL * 2)) {
                pcm = MAXPCMVAL;
            } else {
                pcm = -MAXPCMVAL;
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

    /**
     * Input pcm16 to change the value of InpPcm
     * <pre>
     * -2047<<(4+4) <= InpPcm <= +2047<<(4+4)
     * </pre>
     */
    private void pcm16_2pcm(int pcm16) {
        pcm += pcm16 - pcm16Prev;
        pcm16Prev = pcm16;

        if (((pcm + MAXPCMVAL) & 0xffff_ffffL) > ((MAXPCMVAL * 2) & 0xffff_ffffL)) {
            if ((pcm + MAXPCMVAL) >= (MAXPCMVAL * 2)) {
                pcm = MAXPCMVAL;
            } else {
                pcm = -MAXPCMVAL;
            }
        }

        inpPcm = (pcm & -4) << (4 + 4);
    }

    // -32768<<4 <= retval <= +32768<<4
    public int getPcm() {
        if ((adpcmReg & 0x80) != 0) { // ADPCM stopped
            return 0x8000_0000;
        }
        rateCounter -= adpcmRate;
        while (rateCounter < 0) {
            if (pcmKind == 5) { // 16bitPCM
                int dataH, dataL;
                dataH = dmaGetByte();
                if (dataH == 0x8000_0000) {
                    rateCounter = 0;
                    adpcmReg = 0xc7; // ADPCM stop
                    return 0x8000_0000;
                }
                dataL = dmaGetByte();
                if (dataL == 0x8000_0000) {
                    rateCounter = 0;
                    adpcmReg = 0xc7; // ADPCM stop
                    return 0x8000_0000;
                }
                pcm16_2pcm((short) ((dataH << 8) | dataL)); // A value is entered in OutPcm
            } else if (pcmKind == 6) { // 8bitPCM
                int data;
                data = dmaGetByte();
                if (data == 0x8000_0000) {
                    rateCounter = 0;
                    adpcmReg = 0xc7; // ADPCM stop
                    return 0x8000_0000;
                }
                pcm16_2pcm((byte) data); // A value is entered in InpPcm
            } else {
                if (n1DataFlag == 0) { // If the next ADPCM data is not available
                    int N10Data; // (N1Data << 4) | N0Data
                    N10Data = dmaGetByte(); // DMA transfer (1 byte)
                    if (N10Data == 0x8000_0000) {
                        rateCounter = 0;
                        adpcmReg = 0xc7; // ADPCM stop
                        return 0x8000_0000;
                    }
                    adpcm2pcm((byte) (N10Data & 0x0f)); // A value is entered in InpPcm
                    n1Data = (N10Data >> 4) & 0x0f;
                    n1DataFlag = 1;
                } else {
                    adpcm2pcm((byte) n1Data); // A value is entered in InpPcm
                    n1DataFlag = 0;
                }
            }
            rateCounter += 15625 * 12;
        }
        outPcm = ((inpPcm << 9) - (inpPcmPrev << 9) + 459 * outPcm) >> 9;
        inpPcmPrev = inpPcm;

        return (((outPcm * volume) >> 4) * global.totalVolume) >> 8;
    }

    // -32768<<4 <= retval <= +32768<<4
    public int getPcm62() {
        if ((adpcmReg & 0x80) != 0) { // ADPCM stopped
            return 0x8000_0000;
        }
        rateCounter -= adpcmRate;
        while (rateCounter < 0) {
            if (pcmKind == 5) { // 16bitPCM
                int dataH, dataL;
                dataH = dmaGetByte();
                if (dataH == 0x8000_0000) {
                    rateCounter = 0;
                    adpcmReg = 0xc7; // ADPCM stopped
                    return 0x8000_0000;
                }
                dataL = dmaGetByte();
                if (dataL == 0x8000_0000) {
                    rateCounter = 0;
                    adpcmReg = 0xc7; // ADPCM stopped
                    return 0x8000_0000;
                }
                pcm16_2pcm((short) ((dataH << 8) | dataL)); // A value is entered in OutPcm
            } else if (pcmKind == 6) { // 8bitPCM
                int data;
                data = dmaGetByte();
                if (data == 0x8000_0000) {
                    rateCounter = 0;
                    adpcmReg = 0xc7; // ADPCM stopped
                    return 0x8000_0000;
                }
                pcm16_2pcm((byte) data); // A value is entered in InpPcm
            } else {
                if (n1DataFlag == 0) { // If the next ADPCM data is not available
                    int N10Data; // (N1Data << 4) | N0Data
                    N10Data = dmaGetByte(); // DMA transfer (1 byte)
                    if (N10Data == 0x8000_0000) {
                        rateCounter = 0;
                        adpcmReg = 0xc7; // ADPCM stopped
                        return 0x8000_0000;
                    }
                    adpcm2pcm((byte) (N10Data & 0x0f)); // A value is entered in InpPcm
                    n1Data = (N10Data >> 4) & 0x0f;
                    n1DataFlag = 1;
                } else {
                    adpcm2pcm((byte) n1Data); // A value is entered in InpPcm
                    n1DataFlag = 0;
                }
            }
            rateCounter += 15625 * 12 * 4;
        }
        outInpPcm = (inpPcm << 9) - (inpPcmPrev << 9) + outInpPcm - (outInpPcm >> 5) - (outInpPcm >> 10);
        inpPcmPrev = inpPcm;
        outPcm = outInpPcm - outInpPcmPrev + outPcm - (outPcm >> 8) - (outPcm >> 9) - (outPcm >> 12);
        outInpPcmPrev = outInpPcm;
        return ((outPcm >> 9) * volume) >> 4;
    }

    public int out(byte[] adrsBuf, int adrsPtr, int mode, int len) {
        if (len <= 0) {
            if (len < 0) {
                return getRest();
            } else {
                dmaMtc = 0;
                return 0;
            }
        }
        adpcmReg = 0xC7; // ADPCM stopped
        dmaMtc = 0;
        dmaMarBuf = adrsBuf; // Even if you assign it here, it will not be referenced anywhere
        dmaMarPtr = adrsPtr;
        setMode(mode);
        if ((mode & 3) != 0) {
            dmaMtc = len;
            reset();
            adpcmReg = 0x47; // ADPCM operation starts
            dmaOcr = 0; // No chaining
        }
        return 0;
    }

    public int aot(byte[] tblBuf, int tblPtr, int mode, int cnt) {
        if (cnt <= 0) {
            if (cnt < 0) {
                return getRest();
            } else {
                dmaMtc = 0;
                return 0;
            }
        }
        adpcmReg = 0xC7; // ADPCM stopped
        dmaMtc = 0;
        dmaBarBuf = tblBuf; // Even if you assign it here, it will not be referenced anywhere
        dmaBarPtr = tblPtr;
        dmaBtc = cnt;
        setMode(mode);
        if ((mode & 3) != 0) {
            dmaArrayChainSetNextMtcMar();
            reset();
            adpcmReg = 0x47; // ADPCM operation starts
            dmaOcr = 0x08; // Array Chain
        }
        return 0;
    }

    public int lot(byte[] tblBuf, int tblPtr, int mode) {
        adpcmReg = 0xC7; // ADPCM stopped
        dmaMtc = 0;
        dmaBarBuf = tblBuf; // Even if you assign it here, it will not be referenced anywhere
        dmaBarPtr = tblPtr;
        setMode(mode);
        if ((mode & 3) != 0) {
            dmaLinkArrayChainSetNextMtcMar();
            reset();
            adpcmReg = 0x47; // ADPCM operation starts
            dmaOcr = 0x0c; // Link Array Chain
        }
        return 0;
    }

    public int setMode(int mode) {
        int m;
        m = (mode >> 16) & 0xff;
        if (m != 0xff) {
            m &= 15;
            volume = Global.PCM8VOLTBL[m];
            this.mode = (this.mode & 0xff00_ffff) | (m << 16);
        }
        m = (mode >> 8) & 0xff;
        if (m != 0xff) {
            m &= 7;
            adpcmRate = Global.ADPCMRATEADDTBL[m];
            pcmKind = m;
            this.mode = (this.mode & 0xffff_00ff) | (m << 8);
        }
        m = mode & 0xff;
        if (m != 0xff) {
            m &= 3;
            if (m == 0) {
                adpcmReg = 0xc7; // ADPCM stopped
                dmaMtc = 0;
            } else {
                this.mode = (this.mode & 0xffff_ff00) | m;
            }
        }
        return 0;
    }

    public int getRest() {
        if (dmaMtc == 0) {
            return 0;
        }
        if ((dmaOcr & 0x08) != 0) { // Chaining Operation
            if ((dmaOcr & 0x04) == 0) { // Array Chain
                return -1;
            } else { // Link Array Chain
                return -2;
            }
        }
        return dmaMtc;
    }

    public int getMode() {
        return mode;
    }
}
