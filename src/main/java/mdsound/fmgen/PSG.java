package mdsound.fmgen;

/**
 * Psg に良く似た音を生成する音源ユニット
 */
public class PSG {

    /** メモリ使用量を減らしたいなら減らして */
    public static final int noiseTableSize = 1 << 11;
    public static final int toneShift = 24;
    public static final int envShift = 22;
    public static final int noiseShift = 14;
    /** 音質より速度が優先なら減らすといいかも */
    public static final int overSampling = 2;

    protected byte[] reg = new byte[16];

    protected int[] envelop;

    protected int[] olevel = new int[3];

    protected int[] sCount = new int[3];
    protected int[] speriod = new int[3];
    protected int ecount, eperiod;
    protected int ncount, nPeriod;
    protected int tPeriodBase;
    protected int eperiodbase;
    protected int nPeriodBase;
    protected int volume;
    protected int mask;

    protected static int[][] envelopTable = new int[][] {
            new int[64], new int[64], new int[64], new int[64], new int[64], new int[64], new int[64], new int[64],
            new int[64], new int[64], new int[64], new int[64], new int[64], new int[64], new int[64], new int[64]
    };

    protected static int[] noiseTable = new int[noiseTableSize];
    protected static final int[] emitTable = new int[] {-1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    public int visVolume = 0;

    /*
     * ノイズテーブルを作成する
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
     * エンベロープ波形テーブル
     */
    static {
        // 0 lo  1 up 2 down 3 hi
        int[] table1 = new int[] {
                2, 0, 2, 0, 2, 0, 2, 0, 1, 0, 1, 0, 1, 0, 1, 0,
                2, 2, 2, 0, 2, 1, 2, 3, 1, 1, 1, 3, 1, 2, 1, 0
        };
        int[] table2 = new int[] {0, 0, 31, 31};
        int[] table3 = new int[] {0, 1, 255, 0};

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
     * Psg を初期化する(RESET)
     */
    public void reset() {
        for (int i = 0; i < 14; i++)
            setReg(i, (byte) 0);
        setReg(7, 0xff);
        setReg(14, 0xff);
        setReg(15, 0xff);
    }

    /**
     * 初期化．このクラスを使用する前にかならず呼んでおくこと．
     * Psg のクロックや PCM レートを設定する
     *
     * @param clock Psg の動作クロック
     * @param rate  生成する PCM のレート
     */
    public void setClock(int clock, int rate) {
        tPeriodBase = (int) ((1 << toneShift) / 4.0 * clock / rate);
        eperiodbase = (int) ((1 << envShift) / 4.0 * clock / rate);
        nPeriodBase = (int) ((1 << noiseShift) / 4.0 * clock / rate);

        // 各データの更新
        int tmp;
        tmp = ((reg[0] & 0xff) + (reg[1] & 0xff) * 256) & 0xfff;
        speriod[0] = tmp != 0 ? tPeriodBase / tmp : tPeriodBase;
        tmp = ((reg[2] & 0xff) + (reg[3] & 0xff) * 256) & 0xfff;
        speriod[1] = tmp != 0 ? tPeriodBase / tmp : tPeriodBase;
        tmp = ((reg[4] & 0xff) + (reg[5] & 0xff) * 256) & 0xfff;
        speriod[2] = tmp != 0 ? tPeriodBase / tmp : tPeriodBase;
        tmp = reg[6] & 0x1f;
        nPeriod = tmp != 0 ? nPeriodBase / tmp / 2 : nPeriodBase / 2;
        tmp = ((reg[11] & 0xff) + (reg[12] & 0xff) * 256) & 0xffff;
        eperiod = tmp != 0 ? eperiodbase / tmp : eperiodbase * 2;
    }

    /**
     * 各音源の音量を調節する
     * 単位は約 1/2 dB
     * 出力テーブルを作成
     * 素直にテーブルで持ったほうが省スペース。
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
            olevel[i] = (mask & (1 << i)) != 0 ? emitTable[(reg[8 + i] & 15) * 2 + 1] : 0;
    }

    /**
     * Psg のレジスタに値をセットする
     *
     * @param regnum レジスタの番号 (0 - 15)
     * @param data   セットする値
     */
    public void setReg(int regnum, int data) {
        if (regnum < 0x10) {
            reg[regnum] = (byte) data;
            int tmp;
            switch (regnum) {
            case 0: // ChA Fine Tune
            case 1: // ChA Coarse Tune
                tmp = ((reg[0] & 0xff) + (reg[1] & 0xff) * 256) & 0xfff;
                speriod[0] = tmp != 0 ? tPeriodBase / tmp : tPeriodBase;
                break;

            case 2: // ChB Fine Tune
            case 3: // ChB Coarse Tune
                tmp = ((reg[2] & 0xff) + (reg[3] & 0xff) * 256) & 0xfff;
                speriod[1] = tmp != 0 ? tPeriodBase / tmp : tPeriodBase;
                break;

            case 4: // ChC Fine Tune
            case 5: // ChC Coarse Tune
                tmp = ((reg[4] & 0xff) + (reg[5] & 0xff) * 256) & 0xfff;
                speriod[2] = tmp != 0 ? tPeriodBase / tmp : tPeriodBase;
                break;

            case 6: // Noise generator control
                data &= 0x1f;
                nPeriod = data != 0 ? nPeriodBase / data : nPeriodBase;
                break;

            case 8:
                olevel[0] = (mask & 1) != 0 ? emitTable[(data & 15) * 2 + 1] : 0;
                break;

            case 9:
                olevel[1] = (mask & 2) != 0 ? emitTable[(data & 15) * 2 + 1] : 0;
                break;

            case 10:
                olevel[2] = (mask & 4) != 0 ? emitTable[(data & 15) * 2 + 1] : 0;
                break;

            case 11: // Envelop period
            case 12:
                tmp = ((reg[11] & 0xff) + (reg[12] & 0xff) * 256) & 0xffff;
                eperiod = tmp != 0 ? eperiodbase / tmp : eperiodbase * 2;
                break;

            case 13: // Envelop shape
                ecount = 0;
                envelop = envelopTable[data & 15];
                break;
            }
        }
    }

    /**
     * PCM を nsamples 分合成し， dest で始まる配列に加える(加算する)
     * あくまで加算なので，最初に配列をゼロクリアする必要がある
     *
     * @param dest     PCM データを展開するポインタ
     * @param nsamples 展開する PCM のサンプル数
     */
    public void mix(int[] dest, int nsamples) {
        int[] chEnable = new int[3];
        int[] nEnable = new int[3];
        int r7 = ~reg[7];

        if (((r7 & 0x3f) | ((reg[8] | reg[9] | reg[10]) & 0x1f)) != 0) {
            chEnable[0] = (((r7 & 0x01) != 0) && (speriod[0] <= (1 << toneShift))) ? 1 : 0;
            chEnable[1] = (((r7 & 0x02) != 0) && (speriod[1] <= (1 << toneShift))) ? 1 : 0;
            chEnable[2] = (((r7 & 0x04) != 0) && (speriod[2] <= (1 << toneShift))) ? 1 : 0;
            nEnable[0] = ((r7 >> 3) & 1) != 0 ? 1 : 0;
            nEnable[1] = ((r7 >> 4) & 1) != 0 ? 1 : 0;
            nEnable[2] = ((r7 >> 5) & 1) != 0 ? 1 : 0;

            boolean p1 = ((mask & 1) != 0 && (reg[8] & 0x10) != 0);
            boolean p2 = ((mask & 2) != 0 && (reg[9] & 0x10) != 0);
            boolean p3 = ((mask & 4) != 0 && (reg[10] & 0x10) != 0);

            if (!p1 && !p2 && !p3) {
                // エンベロープ無し
                if ((r7 & 0x38) == 0) {
                    int ptrDest = 0;
                    // ノイズ無し
                    for (int i = 0; i < nsamples; i++) {
                        int sample = 0;
                        for (int j = 0; j < (1 << overSampling); j++) {
                            int x, y, z;

                            x = ((sCount[0] >> (toneShift + overSampling)) & chEnable[0]) - 1;
                            sample += (olevel[0] + x) ^ x;
                            sCount[0] += speriod[0];
                            y = ((sCount[1] >> (toneShift + overSampling)) & chEnable[1]) - 1;
                            sample += (olevel[1] + y) ^ y;
                            sCount[1] += speriod[1];
                            z = ((sCount[2] >> (toneShift + overSampling)) & chEnable[2]) - 1;
                            sample += (olevel[2] + z) ^ z;
                            sCount[2] += speriod[2];
                        }
                        sample /= (1 << overSampling);
                        dest[ptrDest + 0] += sample;
                        dest[ptrDest + 1] += sample;
                        ptrDest += 2;

                        visVolume = sample;

                    }
                } else {
                    int ptrDest = 0;
                    // ノイズ有り
                    for (int i = 0; i < nsamples; i++) {
                        int sample = 0;
                        for (int j = 0; j < (1 << overSampling); j++) {
                            int noise = noiseTable[(ncount >> (noiseShift + overSampling + 6)) & (noiseTableSize - 1)]
                                    >> (ncount >> (noiseShift + overSampling + 1) & 31);
                            ncount += nPeriod;

                            int x = (((sCount[0] >> (toneShift + overSampling)) & chEnable[0]) | (nEnable[0] & noise)) - 1; // 0 or -1
                            sample += (olevel[0] + x) ^ x;
                            sCount[0] += speriod[0];

                            int y = (((sCount[1] >> (toneShift + overSampling)) & chEnable[1]) | (nEnable[1] & noise)) - 1;
                            sample += (olevel[1] + y) ^ y;
                            sCount[1] += speriod[1];

                            int z = (((sCount[2] >> (toneShift + overSampling)) & chEnable[2]) | (nEnable[2] & noise)) - 1;
                            sample += (olevel[2] + z) ^ z;
                            sCount[2] += speriod[2];
                        }
                        sample /= (1 << overSampling);
                        dest[ptrDest + 0] += sample;
                        dest[ptrDest + 1] += sample;
                        ptrDest += 2;

                        visVolume = sample;
                    }
                }

                // エンベロープの計算をさぼった帳尻あわせ
                ecount = (ecount >> 8) + (eperiod >> (8 - overSampling)) * nsamples;
                if (ecount >= (1 << (envShift + 6 + overSampling - 8))) {
                    if ((reg[0x0d] & 0x0b) != 0x0a)
                        ecount |= (1 << (envShift + 5 + overSampling - 8));
                    ecount &= (1 << (envShift + 6 + overSampling - 8)) - 1;
                }
                ecount <<= 8;
            } else {
                int ptrDest = 0;
                // エンベロープあり
                for (int i = 0; i < nsamples; i++) {
                    int sample = 0;
                    for (int j = 0; j < (1 << overSampling); j++) {
                        int env = envelop[ecount >> (envShift + overSampling)];
                        ecount += eperiod;
                        if (ecount >= (1 << (envShift + 6 + overSampling))) {
                            if ((reg[0x0d] & 0x0b) != 0x0a)
                                ecount |= (1 << (envShift + 5 + overSampling));
                            ecount &= (1 << (envShift + 6 + overSampling)) - 1;
                        }
                        int noise = noiseTable[(ncount >> (noiseShift + overSampling + 6)) & (noiseTableSize - 1)]
                                >> (ncount >> (noiseShift + overSampling + 1) & 31);
                        ncount += nPeriod;

                        int x = (((sCount[0] >> (toneShift + overSampling)) & chEnable[0]) | (nEnable[0] & noise)) - 1;
                        // 0 or -1
                        sample += ((p1 ? env : olevel[0]) + x) ^ x;
                        sCount[0] += speriod[0];
                        int y = (((sCount[1] >> (toneShift + overSampling)) & chEnable[1]) | (nEnable[1] & noise)) - 1;
                        sample += ((p2 ? env : olevel[1]) + y) ^ y;
                        sCount[1] += speriod[1];
                        int z = (((sCount[2] >> (toneShift + overSampling)) & chEnable[2]) | (nEnable[2] & noise)) - 1;
                        sample += ((p3 ? env : olevel[2]) + z) ^ z;
                        sCount[2] += speriod[2];

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
     * レジスタ reg の内容を読み出す
     */
    public int getReg(int regnum) {
        return reg[regnum & 0x0f] & 0xff;
    }
}
