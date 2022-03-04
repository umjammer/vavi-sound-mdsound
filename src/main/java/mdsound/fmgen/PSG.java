﻿package mdsound.fmgen;

// ---------------------------------------------------------------------------
    // class PSG
    // PSG に良く似た音を生成する音源ユニット
    // 
    // interface:
    // boolean SetClock(int clock, int rate)
    //  初期化．このクラスを使用する前にかならず呼んでおくこと．
    //  PSG のクロックや PCM レートを設定する
    //
    //  clock: PSG の動作クロック
    //  rate: 生成する PCM のレート
    //  retval 初期化に成功すれば true
    //
    // void Mix(Sample* dest, int nsamples)
    //  PCM を nsamples 分合成し， dest で始まる配列に加える(加算する)
    //  あくまで加算なので，最初に配列をゼロクリアする必要がある
    // 
    // void Reset()
    //  リセットする
    //
    // void SetReg(int reg, (int)8 data)
    //  レジスタ reg に data を書き込む
    // 
    // int GetReg(int reg)
    //  レジスタ reg の内容を読み出す
    // 
    // void SetVolume(int db)
    //  各音源の音量を調節する
    //  単位は約 1/2 dB
    //
    public class PSG
    {

        public final int noisetablesize = 1 << 11;   // ←メモリ使用量を減らしたいなら減らして
        public final int toneshift = 24;
        public final int envshift = 22;
        public final int noiseshift = 14;
        public final int oversampling = 2;       // ← 音質より速度が優先なら減らすといいかも

        public int visVolume = 0;

        public PSG()
        {
            SetVolume(0);
            MakeNoiseTable();
            Reset();
            mask = 0x3f;
        }

        protected void finalinze()
        {
        }

        // ---------------------------------------------------------------------------
        // PSG を初期化する(RESET)
        //
        public void Reset()
        {
            for (int i = 0; i < 14; i++)
                SetReg((int)i, (byte) 0);
            SetReg(7, (byte) 0xff);
            SetReg(14, (byte) 0xff);
            SetReg(15,(byte)  0xff);
        }

        // ---------------------------------------------------------------------------
        // クロック周波数の設定
        //
        public void SetClock(int clock, int rate)
        {
            tperiodbase = (int)((1 << toneshift) / 4.0 * clock / rate);
            eperiodbase = (int)((1 << envshift) / 4.0 * clock / rate);
            nperiodbase = (int)((1 << noiseshift) / 4.0 * clock / rate);

            // 各データの更新
            int tmp;
            tmp = ((reg[0] + reg[1] * 256) & 0xfff);
            speriod[0] = (int)(tmp != 0 ? tperiodbase / tmp : tperiodbase);
            tmp = ((reg[2] + reg[3] * 256) & 0xfff);
            speriod[1] = (int)(tmp != 0 ? tperiodbase / tmp : tperiodbase);
            tmp = ((reg[4] + reg[5] * 256) & 0xfff);
            speriod[2] = (int)(tmp != 0 ? tperiodbase / tmp : tperiodbase);
            tmp = reg[6] & 0x1f;
            nperiod = (int)(tmp != 0 ? nperiodbase / tmp / 2 : nperiodbase / 2);
            tmp = ((reg[11] + reg[12] * 256) & 0xffff);
            eperiod = (int)(tmp != 0 ? eperiodbase / tmp : eperiodbase * 2);
        }

        // ---------------------------------------------------------------------------
        // ノイズテーブルを作成する
        //
        public void MakeNoiseTable()
        {
            if (noisetable[0] == 0)
            {
                int noise = 14321;
                for (int i = 0; i < noisetablesize; i++)
                {
                    int n = 0;
                    for (int j = 0; j < 32; j++)
                    {
                        n = n * 2 + (noise & 1);
                        noise = (noise >> 1) | (((noise << 14) ^ (noise << 16)) & 0x10000);
                    }
                    noisetable[i] = (int)n;
                }
            }
        }

        // ---------------------------------------------------------------------------
        // 出力テーブルを作成
        // 素直にテーブルで持ったほうが省スペース。
        //
        public void SetVolume(int volume)
        {
            double Base = 0x4000 / 3.0 * Math.pow(10.0, volume / 40.0);
            for (int i = 31; i >= 2; i--)
            {
                EmitTable[i] = (int)(Base);
                Base /= 1.189207115;
            }
            EmitTable[1] = 0;
            EmitTable[0] = 0;
            MakeEnvelopTable();

            SetChannelMask(~mask);
        }

        public void SetChannelMask(int c)
        {
            mask = ~c;
            for (int i = 0; i < 3; i++)
                olevel[i] = (int)((mask & (1 << i)) != 0 ? EmitTable[(reg[8 + i] & 15) * 2 + 1] : 0);
        }

        // ---------------------------------------------------------------------------
        // エンベロープ波形テーブル
        //
        public void MakeEnvelopTable()
        {
            // 0 lo  1 up 2 down 3 hi
            byte[] table1 = new byte[]             {
                2,0, 2,0, 2,0, 2,0, 1,0, 1,0, 1,0, 1,0,
                2,2, 2,0, 2,1, 2,3, 1,1, 1,3, 1,2, 1,0
            };
            byte[] table2 = new byte[] { 0, 0, 31, 31 };
            byte[] table3 = new byte[] { 0, 1, (byte) 255, 0 };

            //(int)* ptr = enveloptable[0];
            int ptr = 0;

            for (int i = 0; i < 16 * 2; i++)
            {
                byte v = table2[table1[i]];

                for (int j = 0; j < 32; j++)
                {
                    enveloptable[ptr / 64][ptr % 64] = (int)EmitTable[v];
                    ptr++;
                    v += table3[table1[i]];
                }
            }
        }

        // ---------------------------------------------------------------------------
        // PSG のレジスタに値をセットする
        // regnum  レジスタの番号 (0 - 15)
        // data  セットする値
        //
        public void SetReg(int regnum, byte data)
        {
            if (regnum < 0x10)
            {
                reg[regnum] = data;
                int tmp;
                switch (regnum)
                {
                    case 0:     // ChA Fine Tune
                    case 1:     // ChA Coarse Tune
                        tmp = ((reg[0] + reg[1] * 256) & 0xfff);
                        speriod[0] = (int)(tmp != 0 ? tperiodbase / tmp : tperiodbase);
                        break;

                    case 2:     // ChB Fine Tune
                    case 3:     // ChB Coarse Tune
                        tmp = ((reg[2] + reg[3] * 256) & 0xfff);
                        speriod[1] = (int)(tmp != 0 ? tperiodbase / tmp : tperiodbase);
                        break;

                    case 4:     // ChC Fine Tune
                    case 5:     // ChC Coarse Tune
                        tmp = ((reg[4] + reg[5] * 256) & 0xfff);
                        speriod[2] = (int)(tmp != 0 ? tperiodbase / tmp : tperiodbase);
                        break;

                    case 6:     // Noise generator control
                        data &= 0x1f;
                        nperiod = data != 0 ? nperiodbase / data : nperiodbase;
                        break;

                    case 8:
                        olevel[0] = (int)((mask & 1) != 0 ? EmitTable[(data & 15) * 2 + 1] : 0);
                        break;

                    case 9:
                        olevel[1] = (int)((mask & 2) != 0 ? EmitTable[(data & 15) * 2 + 1] : 0);
                        break;

                    case 10:
                        olevel[2] = (int)((mask & 4) != 0 ? EmitTable[(data & 15) * 2 + 1] : 0);
                        break;

                    case 11:    // Envelop period
                    case 12:
                        tmp = ((reg[11] + reg[12] * 256) & 0xffff);
                        eperiod = (int)(tmp != 0 ? eperiodbase / tmp : eperiodbase * 2);
                        break;

                    case 13:    // Envelop shape
                        ecount = 0;
                        envelop = enveloptable[data & 15];
                        break;
                }
            }
        }

        // ---------------------------------------------------------------------------
        // PCM データを吐き出す(2ch)
        // dest  PCM データを展開するポインタ
        // nsamples 展開する PCM のサンプル数
        //
        public void Mix(int[] dest, int nsamples)
        {
            byte[] chenable = new byte[3];
            byte[] nenable = new byte[3];
            byte r7 = (byte)~reg[7];

            if (((r7 & 0x3f) | ((reg[8] | reg[9] | reg[10]) & 0x1f)) != 0)
            {
                chenable[0] = (byte)((((r7 & 0x01) != 0) && (speriod[0] <= (int)(1 << toneshift))) ? 1 : 0);
                chenable[1] = (byte)((((r7 & 0x02) != 0) && (speriod[1] <= (int)(1 << toneshift))) ? 1 : 0);
                chenable[2] = (byte)((((r7 & 0x04) != 0) && (speriod[2] <= (int)(1 << toneshift))) ? 1 : 0);
                nenable[0] = (byte)(((r7 >> 3) & 1) != 0 ? 1 : 0);
                nenable[1] = (byte)(((r7 >> 4) & 1) != 0 ? 1 : 0);
                nenable[2] = (byte)(((r7 >> 5) & 1) != 0 ? 1 : 0);

                int noise, sample;
                int env;
                //(int)* p1 = ((mask & 1) && (reg[8] & 0x10)) ? &env : &olevel[0];
                //(int)* p2 = ((mask & 2) && (reg[9] & 0x10)) ? &env : &olevel[1];
                //(int)* p3 = ((mask & 4) && (reg[10] & 0x10)) ? &env : &olevel[2];
                boolean p1 = ((mask & 1) != 0 && (reg[8] & 0x10) != 0);
                boolean p2 = ((mask & 2) != 0 && (reg[9] & 0x10) != 0);
                boolean p3 = ((mask & 4) != 0 && (reg[10] & 0x10) != 0);

                //#define SCOUNT(ch) (scount[ch] >> (toneshift+oversampling))

                //if (p1 != &env && p2 != &env && p3 != &env)
                if (!p1 && !p2 && !p3)
                {
                    // エンベロープ無し
                    if ((r7 & 0x38) == 0)
                    {
                        int ptrDest = 0;
                        // ノイズ無し
                        for (int i = 0; i < nsamples; i++)
                        {
                            sample = 0;
                            for (int j = 0; j < (1 << oversampling); j++)
                            {
                                int x, y, z;

                                x = ((int)(scount[0] >> (toneshift + oversampling)) & chenable[0]) - 1;
                                sample += (int)((olevel[0] + x) ^ x);
                                scount[0] += speriod[0];
                                y = ((int)(scount[1] >> (toneshift + oversampling)) & chenable[1]) - 1;
                                sample += (int)((olevel[1] + y) ^ y);
                                scount[1] += speriod[1];
                                z = ((int)(scount[2] >> (toneshift + oversampling)) & chenable[2]) - 1;
                                sample += (int)((olevel[2] + z) ^ z);
                                scount[2] += speriod[2];
                            }
                            sample /= (1 << oversampling);
                            StoreSample(dest[ptrDest + 0], sample);
                            StoreSample(dest[ptrDest + 1], sample);
                            ptrDest += 2;

                            visVolume = sample;

                        }
                    }
                    else
                    {
                        int ptrDest = 0;
                        // ノイズ有り
                        for (int i = 0; i < nsamples; i++)
                        {
                            sample = 0;
                            for (int j = 0; j < (1 << oversampling); j++)
                            {
                                //# ifdef _M_IX86
                                //noise = (int)(noisetable[(ncount >> (int)((noiseshift + oversampling + 6)) & (noisetablesize - 1))]
                                //    >> (int)(ncount >> (noiseshift + oversampling + 1)));
                                //noise = (int)(noisetable[(ncount >> (int)((noiseshift + oversampling + 6)) & (noisetablesize - 1))]
                                //    >> (int)(ncount >> (noiseshift + oversampling + 1)));
                                //#else
                                noise = (int)noisetable[(ncount >> (noiseshift + oversampling + 6)) & (noisetablesize - 1)]
                                     >> (int)(ncount >> (noiseshift + oversampling + 1) & 31);
                                //#endif
                                ncount += nperiod;

                                int x, y, z;

                                x = (((int)(scount[0] >> (toneshift + oversampling)) & chenable[0]) | (nenable[0] & noise)) - 1;     // 0 or -1
                                sample += (int)((olevel[0] + x) ^ x);
                                scount[0] += speriod[0];

                                y = (((int)(scount[1] >> (toneshift + oversampling)) & chenable[1]) | (nenable[1] & noise)) - 1;
                                sample += (int)((olevel[1] + y) ^ y);
                                scount[1] += speriod[1];

                                z = (((int)(scount[2] >> (toneshift + oversampling)) & chenable[2]) | (nenable[2] & noise)) - 1;
                                sample += (int)((olevel[2] + z) ^ z);
                                scount[2] += speriod[2];


                            }
                            sample /= (1 << oversampling);
                            StoreSample(dest[ptrDest + 0], sample);
                            StoreSample(dest[ptrDest + 1], sample);
                            ptrDest += 2;

                            visVolume = sample;

                        }
                    }

                    // エンベロープの計算をさぼった帳尻あわせ
                    ecount = (int)((ecount >> 8) + (eperiod >> (8 - oversampling)) * nsamples);
                    if (ecount >= (1 << (envshift + 6 + oversampling - 8)))
                    {
                        if ((reg[0x0d] & 0x0b) != 0x0a)
                            ecount |= (1 << (envshift + 5 + oversampling - 8));
                        ecount &= (1 << (envshift + 6 + oversampling - 8)) - 1;
                    }
                    ecount <<= 8;
                }
                else
                {
                    int ptrDest = 0;
                    // エンベロープあり
                    for (int i = 0; i < nsamples; i++)
                    {
                        sample = 0;
                        for (int j = 0; j < (1 << oversampling); j++)
                        {
                            env = envelop[ecount >> (envshift + oversampling)];
                            ecount += eperiod;
                            if (ecount >= (1 << (envshift + 6 + oversampling)))
                            {
                                if ((reg[0x0d] & 0x0b) != 0x0a)
                                    ecount |= (1 << (envshift + 5 + oversampling));
                                ecount &= (1 << (envshift + 6 + oversampling)) - 1;
                            }
                            //# ifdef _M_IX86
                            //noise = (int)(noisetable[(ncount >> (int)((noiseshift + oversampling + 6)) & (noisetablesize - 1))]
                            //    >> (int)(ncount >> (noiseshift + oversampling + 1)));
                            //#else
                            noise = (int)noisetable[(ncount >> (noiseshift + oversampling + 6)) & (noisetablesize - 1)]
                                >> (int)(ncount >> (noiseshift + oversampling + 1) & 31);
                            //#endif
                            ncount += nperiod;

                            int x, y, z;
                            //x = (((int)(scount[0] >> (toneshift + oversampling)) & chenable[0]) | (nenable[0] & noise)) - 1;
                            x = (((int)(scount[0] >> (toneshift + oversampling)) & chenable[0]) | (nenable[0] & noise)) - 1;
                            // 0 or -1
                            //sample += (int)((p1 + x) ^ x);
                            sample += (int)(((p1 ? env : olevel[0]) + x) ^ x);
                            scount[0] += speriod[0];
                            y = (((int)(scount[1] >> (toneshift + oversampling)) & chenable[1]) | (nenable[1] & noise)) - 1;
                            sample += (int)(((p2 ? env : olevel[1]) + y) ^ y);
                            scount[1] += speriod[1];
                            z = (((int)(scount[2] >> (toneshift + oversampling)) & chenable[2]) | (nenable[2] & noise)) - 1;
                            sample += (int)(((p3 ? env : olevel[2]) + z) ^ z);
                            scount[2] += speriod[2];

                        }
                        sample /= (1 << oversampling);
                        StoreSample(dest[ptrDest + 0], sample);
                        StoreSample(dest[ptrDest + 1], sample);
                        ptrDest += 2;

                        visVolume = sample;

                    }
                }
            }
        }

        public int GetReg(int regnum)
        {
            return reg[regnum & 0x0f];
        }

        protected static void StoreSample(int dest, int data)
        {
            //if (sizeof(int) == 2)
            //dest = (int)Limit(dest + data, 0x7fff, -0x8000);
            //else
            dest += data;
        }

        protected byte[] reg = new byte[16];

        protected int[] envelop;

        protected int[] olevel = new int[3];

        protected int[] scount = new int[3];
        protected int[] speriod = new int[3];
        protected int ecount, eperiod;
        protected int ncount, nperiod;
        protected int tperiodbase;
        protected int eperiodbase;
        protected int nperiodbase;
        protected int volume;
        protected int mask;

        protected int[][] enveloptable = new int[][] {
            new int[64], new int[64], new int[64], new int[64], new int[64], new int[64], new int[64], new int[64],
            new int[64], new int[64], new int[64], new int[64], new int[64], new int[64], new int[64], new int[64]
        };

        protected int[] noisetable = new int[noisetablesize];
        protected int[] EmitTable = new int[] { -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    }
