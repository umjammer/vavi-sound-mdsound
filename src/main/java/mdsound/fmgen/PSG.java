package mdsound.fmgen;

import vavi.util.Debug;
import vavi.util.StringUtil;


/**
 * A sound source unit that produces sounds similar to Psg.
 */
public class PSG {

    /** If you want to reduce memory usage, reduce it. */
    public static final int noiseTableSize = 1 << 11;
    public static final int toneShift = 24;
    public static final int envShift = 22;
    public static final int noiseShift = 14;
    /** If speed is more important than sound quality, you may want to reduce it. */
    public static final int overSampling = 2;

    protected byte[] reg = new byte[16];

    protected int[] envelop;

    protected int[] oLevel = new int[3];

    protected int[] sCount = new int[3];
    protected int[] sPeriod = new int[3];
    protected int eCount, ePeriod;
    protected int nCount, nPeriod;
    protected int tPeriodBase;
    protected int ePeriodBase;
    protected int nPeriodBase;
    protected int volume;
    protected int mask;

    protected static final int[][] envelopTable = {
            new int[64], new int[64], new int[64], new int[64], new int[64], new int[64], new int[64], new int[64],
            new int[64], new int[64], new int[64], new int[64], new int[64], new int[64], new int[64], new int[64]
    };

    protected static int[] noiseTable = new int[noiseTableSize];
    protected static final int[] emitTable = {-1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    public int visVolume = 0;

    /*
     * Creating a Noise Table
     */
    static {
        if (noiseTable[0] == 0) {
            int noise = 14321;
            for (int i = 0; i < noiseTableSize; i++) {
                int n = 0;
                for (int j = 0; j < 32; j++) {
                    n = n * 2 + (noise & 1);
                    noise = (noise >> 1) | (((noise << 14) ^ (noise << 16)) & 0x10000);
                }
                noiseTable[i] = n;
            }
        }
    }

    /*
     * Envelope Wavetable
     */
    static {
        // 0 lo  1 up 2 down 3 hi
        int[] table1 = {
                2, 0, 2, 0, 2, 0, 2, 0, 1, 0, 1, 0, 1, 0, 1, 0,
                2, 2, 2, 0, 2, 1, 2, 3, 1, 1, 1, 3, 1, 2, 1, 0
        };
        int[] table2 = {0, 0, 31, 31};
        int[] table3 = {0, 1, 255, 0};

        //(int)* ptr = enveloptable[0];
        int ptr = 0;

        for (int i = 0; i < 16 * 2; i++) {
            int v = table2[table1[i]];

            for (int j = 0; j < 32; j++) {
                envelopTable[ptr / 64][ptr % 64] = emitTable[v];
                ptr++;
                v += table3[table1[i]];
                v &= 0xff;
            }
        }
    }

    public PSG() {
        setVolume(0);
        reset();
        mask = 0x3f;
    }

    /**
     * Initialize Psg. (RESET)
     */
    public void reset() {
        for (int i = 0; i < 14; i++)
            setReg(i, 0);
        setReg(7, 0xff);
        setReg(14, 0xff);
        setReg(15, 0xff);
    }

    /**
     * Initialization. Must call before using this class.
     * Set the Psg clock and PCM rate
     *
     * @param clock Psg operating clock
     * @param rate  Generate PCM rate
     */
    public void setClock(int clock, int rate) {
        tPeriodBase = (int) ((1 << toneShift) / 4.0 * clock / rate);
        ePeriodBase = (int) ((1 << envShift) / 4.0 * clock / rate);
        nPeriodBase = (int) ((1 << noiseShift) / 4.0 * clock / rate);

        // 各データの更新
        int tmp;
        tmp = (((reg[0] & 0xff) + (reg[1] & 0xff) * 256)) & 0xfff;
        sPeriod[0] = tmp != 0 ? tPeriodBase / tmp : tPeriodBase;
        tmp = (((reg[2] & 0xff) + (reg[3] & 0xff) * 256)) & 0xfff;
        sPeriod[1] = tmp != 0 ? tPeriodBase / tmp : tPeriodBase;
        tmp = (((reg[4] & 0xff) + (reg[5] & 0xff) * 256)) & 0xfff;
        sPeriod[2] = tmp != 0 ? tPeriodBase / tmp : tPeriodBase;
        tmp = reg[6] & 0x1f;
        nPeriod = tmp != 0 ? nPeriodBase / tmp / 2 : nPeriodBase / 2;
        tmp = (((reg[11] & 0xff) + (reg[12] & 0xff) * 256)) & 0xffff;
        ePeriod = tmp != 0 ? ePeriodBase / tmp : ePeriodBase * 2;
    }

    /**
     * Adjusts the volume of each sound source.
     * - The unit is approximately 1/2 dB.
     * - Creates the output table.
     * - It takes up less space if you just hold it on the table.
     */
    public void setVolume(int volume) {
        double base = 0x4000 / 3.0 * Math.pow(10.0, volume / 40.0);
        for (int i = 31; i >= 2; i--) {
            emitTable[i] = (int) base;
            base /= 1.189207115;
        }
        emitTable[1] = 0;
        emitTable[0] = 0;

        setChannelMask(~mask);
    }

    public void setChannelMask(int c) {
        mask = ~c;
        for (int i = 0; i < 3; i++)
            oLevel[i] = (mask & (1 << i)) != 0 ? emitTable[(reg[8 + i] & 15) * 2 + 1] : 0;
    }

    /**
     * Sets the value in the Psg register.
     *
     * @param regNum The register number (0 - 15)
     * @param data   Value to set
     */
    public void setReg(int regNum, int data) {
        if (regNum < 0x10) {
            reg[regNum] = (byte) (data & 0xff);
            int tmp;
            switch (regNum) {
            case 0: // ChA Fine Tune
            case 1: // ChA Coarse Tune
                tmp = (((reg[0] & 0xff) + (reg[1] & 0xff) * 256)) & 0xfff;
                sPeriod[0] = tmp != 0 ? tPeriodBase / tmp : tPeriodBase;
                break;

            case 2: // ChB Fine Tune
            case 3: // ChB Coarse Tune
                tmp = (((reg[2] & 0xff) + (reg[3] & 0xff) * 256)) & 0xfff;
                sPeriod[1] = tmp != 0 ? tPeriodBase / tmp : tPeriodBase;
                break;

            case 4: // ChC Fine Tune
            case 5: // ChC Coarse Tune
                tmp = (((reg[4] & 0xff) + (reg[5] & 0xff) * 256)) & 0xfff;
                sPeriod[2] = tmp != 0 ? tPeriodBase / tmp : tPeriodBase;
                break;

            case 6: // Noise generator control
                data &= 0x1f;
                nPeriod = data != 0 ? nPeriodBase / data : nPeriodBase;
                break;

            case 8:
                oLevel[0] = (mask & 1) != 0 ? emitTable[(data & 15) * 2 + 1] : 0;
                break;

            case 9:
                oLevel[1] = (mask & 2) != 0 ? emitTable[(data & 15) * 2 + 1] : 0;
                break;

            case 10:
                oLevel[2] = (mask & 4) != 0 ? emitTable[(data & 15) * 2 + 1] : 0;
                break;

            case 11: // Envelop period
            case 12:
                tmp = (((reg[11] & 0xff) + (reg[12] & 0xff) * 256)) & 0xffff;
                ePeriod = tmp != 0 ? ePeriodBase / tmp : ePeriodBase * 2;
                break;

            case 13: // Envelop shape
                eCount = 0;
                envelop = envelopTable[data & 15];
                break;
            }
        }
    }

    /**
     * Synthesize {@code nSamples} of PCM and add it to the array starting at {@code dest}.
     * Since this is just an addition, you need to clear the array to zero first.
     *
     * @param dest     Pointer to expand PCM data
     * @param nSamples Number of PCM samples to expand
     */
    public void mix(int[] dest, int nSamples) {
        int[] chEnable = new int[3];
        int[] nEnable = new int[3];
        int r7 = ~(reg[7] & 0xff);

        if (((r7 & 0x3f) | (((reg[8] & 0xff) | (reg[9] & 0xff) | (reg[10] & 0xff)) & 0x1f)) != 0) {
            chEnable[0] = (((r7 & 0x01) != 0) && (sPeriod[0] <= (1 << toneShift))) ? 1 : 0;
            chEnable[1] = (((r7 & 0x02) != 0) && (sPeriod[1] <= (1 << toneShift))) ? 1 : 0;
            chEnable[2] = (((r7 & 0x04) != 0) && (sPeriod[2] <= (1 << toneShift))) ? 1 : 0;
            nEnable[0] = ((r7 >> 3) & 1) != 0 ? 1 : 0;
            nEnable[1] = ((r7 >> 4) & 1) != 0 ? 1 : 0;
            nEnable[2] = ((r7 >> 5) & 1) != 0 ? 1 : 0;

            boolean p1 = ((mask & 1) != 0 && (reg[8] & 0x10) != 0);
            boolean p2 = ((mask & 2) != 0 && (reg[9] & 0x10) != 0);
            boolean p3 = ((mask & 4) != 0 && (reg[10] & 0x10) != 0);

            if (!p1 && !p2 && !p3) {
                // No Envelope
                if ((r7 & 0x38) == 0) {
                    int ptrDest = 0;
                    // Noiseless
                    for (int i = 0; i < nSamples; i++) {
                        int sample = 0;
                        for (int j = 0; j < (1 << overSampling); j++) {
                            int x, y, z;

                            x = ((sCount[0] >> (toneShift + overSampling)) & chEnable[0]) - 1;
                            sample += (oLevel[0] + x) ^ x;
                            sCount[0] += sPeriod[0];
                            y = ((sCount[1] >> (toneShift + overSampling)) & chEnable[1]) - 1;
                            sample += (oLevel[1] + y) ^ y;
                            sCount[1] += sPeriod[1];
                            z = ((sCount[2] >> (toneShift + overSampling)) & chEnable[2]) - 1;
                            sample += (oLevel[2] + z) ^ z;
                            sCount[2] += sPeriod[2];
                        }
                        sample /= (1 << overSampling);
                        dest[ptrDest + 0] += sample;
                        dest[ptrDest + 1] += sample;
                        ptrDest += 2;

                        visVolume = sample;

                    }
                } else {
                    int ptrDest = 0;
                    // Noise
                    for (int i = 0; i < nSamples; i++) {
                        int sample = 0;
                        for (int j = 0; j < (1 << overSampling); j++) {
                            int noise = noiseTable[(nCount >>> (noiseShift + overSampling + 6)) & (noiseTableSize - 1)]
                                    >> (nCount >> (noiseShift + overSampling + 1) & 31);
                            nCount += nPeriod;

                            int x = (((sCount[0] >> (toneShift + overSampling)) & chEnable[0]) | (nEnable[0] & noise)) - 1; // 0 or -1
                            sample += (oLevel[0] + x) ^ x;
                            sCount[0] += sPeriod[0];

                            int y = (((sCount[1] >> (toneShift + overSampling)) & chEnable[1]) | (nEnable[1] & noise)) - 1;
                            sample += (oLevel[1] + y) ^ y;
                            sCount[1] += sPeriod[1];

                            int z = (((sCount[2] >> (toneShift + overSampling)) & chEnable[2]) | (nEnable[2] & noise)) - 1;
                            sample += (oLevel[2] + z) ^ z;
                            sCount[2] += sPeriod[2];
                        }
                        sample /= (1 << overSampling);
                        dest[ptrDest + 0] += sample;
                        dest[ptrDest + 1] += sample;
                        ptrDest += 2;

                        visVolume = sample;
                    }
                }

                // Balancing the accounts by skipping the envelope calculations
                eCount = (eCount >> 8) + (ePeriod >> (8 - overSampling)) * nSamples;
                if (eCount >= (1 << (envShift + 6 + overSampling - 8))) {
                    if ((reg[0x0d] & 0x0b) != 0x0a)
                        eCount |= (1 << (envShift + 5 + overSampling - 8));
                    eCount &= (1 << (envShift + 6 + overSampling - 8)) - 1;
                }
                eCount <<= 8;
            } else {
                int ptrDest = 0;
                // With envelope
                for (int i = 0; i < nSamples; i++) {
                    int sample = 0;
                    for (int j = 0; j < (1 << overSampling); j++) {
                        int env = envelop[eCount >>> (envShift + overSampling)];
                        eCount += ePeriod;
                        if (eCount >= (1 << (envShift + 6 + overSampling))) {
                            if ((reg[0x0d] & 0x0b) != 0x0a)
                                eCount |= (1 << (envShift + 5 + overSampling));
                            eCount &= (1 << (envShift + 6 + overSampling)) - 1;
                        }
                        int noise = noiseTable[(nCount >> (noiseShift + overSampling + 6)) & (noiseTableSize - 1)]
                                >> (nCount >> (noiseShift + overSampling + 1) & 31);
                        nCount += nPeriod;

                        int x = (((sCount[0] >> (toneShift + overSampling)) & chEnable[0]) | (nEnable[0] & noise)) - 1;
                        // 0 or -1
                        sample += ((p1 ? env : oLevel[0]) + x) ^ x;
                        sCount[0] += sPeriod[0];
                        int y = (((sCount[1] >> (toneShift + overSampling)) & chEnable[1]) | (nEnable[1] & noise)) - 1;
                        sample += ((p2 ? env : oLevel[1]) + y) ^ y;
                        sCount[1] += sPeriod[1];
                        int z = (((sCount[2] >> (toneShift + overSampling)) & chEnable[2]) | (nEnable[2] & noise)) - 1;
                        sample += ((p3 ? env : oLevel[2]) + z) ^ z;
                        sCount[2] += sPeriod[2];

                    }
                    sample /= (1 << overSampling);
                    dest[ptrDest + 0] += sample;
                    dest[ptrDest + 1] += sample;
                    ptrDest += 2;

                    visVolume = sample;
                }
            }
        }
    }

    /**
     * Reads the contents of the register {@link #reg}.
     */
    public int getReg(int regNum) {
        return reg[regNum & 0x0f] & 0xff;
    }
}
