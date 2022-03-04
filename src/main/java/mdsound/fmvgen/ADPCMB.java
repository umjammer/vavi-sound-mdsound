package mdsound.fmvgen;

import mdsound.fmvgen.effect.Compressor;
import mdsound.fmvgen.effect.HPFLPF;
import mdsound.fmvgen.effect.ReversePhase;
import mdsound.fmvgen.effect.chorus;
import mdsound.fmvgen.effect.distortion;
import mdsound.fmvgen.effect.reverb;

    public class ADPCMB
    {
        public OPNA2 parent = null;

        public boolean NO_BITTYPE_EMULATION = false;

        public int stmask;
        public int statusnext;

        public byte[] adpcmbuf;        // ADPCM RAM
        public int adpcmmask;     // メモリアドレスに対するビットマスク
        public int adpcmnotice;   // ADPCM 再生終了時にたつビット
        protected int startaddr;     // Start address
        protected int stopaddr;      // Stop address
        public int memaddr;       // 再生中アドレス
        protected int limitaddr;     // Limit address/mask
        public int adpcmlevel;     // ADPCM 音量
        public int adpcmvolume;
        public int adpcmvol;
        public int deltan;            // ⊿N
        public int adplc;          // 周波数変換用変数
        public int adpld;          // 周波数変換用変数差分値
        public int adplbase;      // adpld の元
        public int adpcmx;         // ADPCM 合成用 x
        public int adpcmd;         // ADPCM 合成用 ⊿
        protected int adpcmout;       // ADPCM 合成後の出力
        protected int apout0;         // out(t-2)+out(t-1)
        protected int apout1;         // out(t-1)+out(t)

        public int shiftBit = 6;    //メモリ

        protected int status;

        protected int adpcmreadbuf;  // ADPCM リード用バッファ
        public boolean adpcmplay;     // ADPCM 再生中
        protected byte granuality;
        public boolean adpcmmask_;

        protected byte control1;     // ADPCM コントロールレジスタ１
        public byte control2;     // ADPCM コントロールレジスタ２
        protected byte[] adpcmreg = new byte[8];  // ADPCM レジスタの一部分
        //protected float[] panTable = new float[4] { 1.0f, 0.5012f, 0.2512f, 0.1000f };
        protected float panL = 1.0f;
        protected float panR = 1.0f;
        private reverb reverb;
        private distortion distortion;
        private chorus chorus;
        private HPFLPF hpflpf;
        private int efcCh;
        private int num;
        private ReversePhase reversePhase;
        private Compressor compressor;

        public ADPCMB(int num,reverb reverb, distortion distortion, chorus chorus,HPFLPF hpflpf, ReversePhase reversePhase, Compressor compressor, int efcCh)
        {
            this.num = num;
            this.reverb = reverb;
            this.distortion = distortion;
            this.chorus = chorus;
            this.hpflpf = hpflpf;
            this.reversePhase = reversePhase;
            this.compressor = compressor;
            this.efcCh = efcCh;
        }

        public void Mix(int[] dest, int count)
        {
            int maskl = (int)((control2 & 0x80) != 0 ? -1 : 0);
            int maskr = (int)((control2 & 0x40) != 0 ? -1 : 0);
            if (adpcmmask_)
            {
                maskl = maskr = 0;
            }

            if (adpcmplay)
            {
                int ptrDest = 0;
                //  LOG2("ADPCM Play: %d   DeltaN: %d\n", adpld, deltan);
                if (adpld <= 8192)      // fplay < fsamp
                {
                    for (; count > 0; count--)
                    {
                        if (adplc < 0)
                        {
                            adplc += 8192;
                            DecodeADPCMB();
                            if (!adpcmplay)
                                break;
                        }

                        int s = (adplc * apout0 + (8192 - adplc) * apout1) >> 13;
                        int sL = (int)(s & maskl);
                        int sR = (int)(s & maskr);
                        distortion.Mix(efcCh, sL, sR);
                        chorus.Mix(efcCh, sL, sR);
                        hpflpf.Mix(efcCh, sL, sR);
                        compressor.Mix(efcCh, sL, sR);

                        sL = (int)(sL * panL) * reversePhase.Adpcm[num][0];
                        sR = (int)(sR * panR) * reversePhase.Adpcm[num][1];
                        int revSampleL = (int)(sL * reverb.SendLevel[efcCh]);
                        int revSampleR = (int)(sR * reverb.SendLevel[efcCh]);
                        fmvgen.StoreSample(dest[ptrDest + 0], sL);
                        fmvgen.StoreSample(dest[ptrDest + 1], sR);
                        reverb.StoreDataC(revSampleL, revSampleR);
                        //visAPCMVolume[0] = (int)(s & maskl);
                        //visAPCMVolume[1] = (int)(s & maskr);
                        ptrDest += 2;
                        adplc -= adpld;
                    }
                    for (; count > 0 && apout0 != 0; count--)
                    {
                        if (adplc < 0)
                        {
                            apout0 = apout1;
                            apout1 = 0;
                            adplc += 8192;
                        }
                        int s = (adplc * apout1) >> 13;
                        int sL = (int)(s & maskl);
                        int sR = (int)(s & maskr);
                        distortion.Mix(efcCh, sL, sR);
                        chorus.Mix(efcCh, sL, sR);
                        hpflpf.Mix(efcCh, sL, sR);
                        compressor.Mix(efcCh, sL, sR);

                        sL = (int)(sL * panL) * reversePhase.Adpcm[num][0];
                        sR = (int)(sR * panR) * reversePhase.Adpcm[num][1];
                        int revSampleL = (int)(sL * reverb.SendLevel[efcCh]);
                        int revSampleR = (int)(sR * reverb.SendLevel[efcCh]);
                        fmvgen.StoreSample(dest[ptrDest + 0], sL);
                        fmvgen.StoreSample(dest[ptrDest + 1], sR);
                        reverb.StoreDataC(revSampleL, revSampleR);
                        //visAPCMVolume[0] = (int)(s & maskl);
                        //visAPCMVolume[1] = (int)(s & maskr);
                        ptrDest += 2;
                        adplc -= adpld;
                    }
                }
                else    // fplay > fsamp (adpld = fplay/famp*8192)
                {
                    int t = (-8192 * 8192) / adpld;
stop:
                    for (; count > 0; count--)
                    {
                        int s = apout0 * (8192 + adplc);
                        while (adplc < 0)
                        {
                            DecodeADPCMB();
                            if (!adpcmplay)
                                break stop;
                            s -= apout0 * Math.max(adplc, t);
                            adplc -= t;
                        }
                        adplc -= 8192;
                        s >>= 13;
                        int sL = (int)(s & maskl);
                        int sR = (int)(s & maskr);
                        distortion.Mix(efcCh, sL, sR);
                        chorus.Mix(efcCh, sL, sR);
                        hpflpf.Mix(efcCh, sL, sR);
                        compressor.Mix(efcCh, sL, sR);

                        sL = (int)(sL * panL) * reversePhase.Adpcm[num][0];
                        sR = (int)(sR * panR) * reversePhase.Adpcm[num][1];
                        int revSampleL = (int)(sL * reverb.SendLevel[efcCh]);
                        int revSampleR = (int)(sR * reverb.SendLevel[efcCh]);
                        fmvgen.StoreSample(dest[ptrDest + 0], sL);
                        fmvgen.StoreSample(dest[ptrDest + 1], sR);
                        reverb.StoreDataC(revSampleL, revSampleR);
                        //visAPCMVolume[0] = (int)(s & maskl);
                        //visAPCMVolume[1] = (int)(s & maskr);
                        ptrDest += 2;
                    }
                }
            }
            if (!adpcmplay)
            {
                apout0 = apout1 = adpcmout = 0;
                adplc = 0;
            }
        }

        public void SetADPCMBReg(int addr, int data)
        {
            switch (addr)
            {
                case 0x00:      // Control Register 1
                    if (((data & 0x80) != 0) && !adpcmplay)
                    {
                        adpcmplay = true;
                        memaddr = startaddr;
                        adpcmx = 0;
                        adpcmd = 127;
                        adplc = 0;
                    }
                    if ((data & 1) != 0)
                    {
                        adpcmplay = false;
                    }
                    control1 = (byte)data;
                    break;

                case 0x01:      // Control Register 2
                    control2 = (byte)data;
                    granuality = (byte)((control2 & 2) != 0 ? 1 : 4);
                    break;

                case 0x02:      // Start Address L
                case 0x03:      // Start Address H
                    adpcmreg[addr - 0x02 + 0] = (byte)data;
                    startaddr = (int)((adpcmreg[1] * 256 + adpcmreg[0]) << shiftBit);
                    memaddr = startaddr;
                    //  LOG1("  startaddr %.6x", startaddr);
                    break;

                case 0x04:      // Stop Address L
                case 0x05:      // Stop Address H
                    adpcmreg[addr - 0x04 + 2] = (byte)data;
                    stopaddr = (int)((adpcmreg[3] * 256 + adpcmreg[2] + 1) << shiftBit);
                    //  LOG1("  stopaddr %.6x", stopaddr);
                    break;

                case 0x07:
                    panL = OPNA2.panTable[(data >> 6) & 0x3];
                    panR = OPNA2.panTable[(data >> 4) & 0x3];
                    break;

                case 0x08:      // ADPCM data
                    if ((control1 & 0x60) == 0x60)
                    {
                        //   LOG2("  Wr [0x%.5x] = %.2x", memaddr, data);
                        WriteRAM(data);
                    }
                    break;

                case 0x09:      // delta-N L
                case 0x0a:      // delta-N H
                    adpcmreg[addr - 0x09 + 4] = (byte)data;
                    deltan = (int)(adpcmreg[5] * 256 + adpcmreg[4]);
                    deltan = Math.max(256, deltan);
                    adpld = (int)(deltan * adplbase >> 16);
                    break;

                case 0x0b:      // Level Control
                    adpcmlevel = (byte)data;
                    adpcmvolume = (adpcmvol * adpcmlevel) >> 12;
                    break;

                case 0x0c:      // Limit Address L
                case 0x0d:      // Limit Address H
                    adpcmreg[addr - 0x0c + 6] = (byte)data;
                    limitaddr = (int)((adpcmreg[7] * 256 + adpcmreg[6] + 1) << shiftBit);
                    //  LOG1("  limitaddr %.6x", limitaddr);
                    break;

                case 0x10:      // Flag Control
                    if ((data & 0x80) != 0)
                    {
                        status = 0;
                        UpdateStatus();
                    }
                    else
                    {
                        stmask = ~(data & 0x1f);
                        //   UpdateStatus();     //???
                    }
                    break;
            }
        }

        // ---------------------------------------------------------------------------
        // ADPCM RAM への書込み操作
        //

        protected void WriteRAM(int data)
        {
            if (NO_BITTYPE_EMULATION)
            {
                if ((control2 & 2) == 0)
                {
                    // 1 bit mode
                    //adpcmbuf[(memaddr >> 4) & 0x3ffff] = (byte)data;
                    adpcmbuf[(memaddr >> 4) & adpcmmask] = (byte)data;
                    memaddr += 16;
                }
                else
                {
                    // 8 bit mode
                    //(int)8* p = &adpcmbuf[(memaddr >> 4) & 0x7fff];
                    int p = (int)((memaddr >> 4) & 0x7fff);
                    int bank = (memaddr >> 1) & 7;
                    byte mask = (byte)(1 << (int)bank);
                    data <<= (int)bank;

                    adpcmbuf[p + 0x00000] = (byte)((adpcmbuf[p + 0x00000] & ~mask) | ((byte)(data) & mask));
                    data >>= 1;
                    adpcmbuf[p + 0x08000] = (byte)((adpcmbuf[p + 0x08000] & ~mask) | ((byte)(data) & mask));
                    data >>= 1;
                    adpcmbuf[p + 0x10000] = (byte)((adpcmbuf[p + 0x10000] & ~mask) | ((byte)(data) & mask));
                    data >>= 1;
                    adpcmbuf[p + 0x18000] = (byte)((adpcmbuf[p + 0x18000] & ~mask) | ((byte)(data) & mask));
                    data >>= 1;
                    adpcmbuf[p + 0x20000] = (byte)((adpcmbuf[p + 0x20000] & ~mask) | ((byte)(data) & mask));
                    data >>= 1;
                    adpcmbuf[p + 0x28000] = (byte)((adpcmbuf[p + 0x28000] & ~mask) | ((byte)(data) & mask));
                    data >>= 1;
                    adpcmbuf[p + 0x30000] = (byte)((adpcmbuf[p + 0x30000] & ~mask) | ((byte)(data) & mask));
                    data >>= 1;
                    adpcmbuf[p + 0x38000] = (byte)((adpcmbuf[p + 0x38000] & ~mask) | ((byte)(data) & mask));
                    memaddr += 2;
                }
            }
            else
            {
                //adpcmbuf[(memaddr >> granuality) & 0x3ffff] = (byte)data;
                adpcmbuf[(memaddr >> granuality) & adpcmmask] = (byte)data;
                memaddr += (int)(1 << granuality);
            }

            if (memaddr == stopaddr)
            {
                SetStatus(4);
                statusnext = 0x04;  // EOS
                memaddr &= (int)((shiftBit == 6) ? 0x3fffff : 0x1ffffff);
            }
            if (memaddr == limitaddr)
            {
                //  LOG1("Limit ! (%.8x)\n", limitaddr);
                memaddr = 0;
            }
            SetStatus(8);
        }

        // ---------------------------------------------------------------------------
        // ADPCM 展開
        //

        protected void DecodeADPCMB()
        {
            apout0 = apout1;
            int n = (ReadRAMN() * adpcmvolume) >> 13;
            apout1 = adpcmout + n;
            adpcmout = n;
        }

        // ---------------------------------------------------------------------------
        // ADPCM RAM からの nibble 読み込み及び ADPCM 展開
        //

        protected int ReadRAMN()
        {
            int data;
            if (granuality > 0)
            {
                if (NO_BITTYPE_EMULATION)
                {
                    if ((control2 & 2) == 0)
                    {
                        //data = adpcmbuf[(memaddr >> 4) & 0x3ffff];
                        data = adpcmbuf[(memaddr >> 4) & adpcmmask];
                        memaddr += 8;
                        if ((memaddr & 8) != 0)
                            return DecodeADPCMBSample(data >> 4);
                        data &= 0x0f;
                    }
                    else
                    {
                        //(int)8* p = &adpcmbuf[(memaddr >> 4) & 0x7fff] + ((~memaddr & 1) << 17);
                        int p = (int)(((memaddr >> 4) & 0x7fff) + ((~memaddr & 1) << 17));
                        int bank = (memaddr >> 1) & 7;
                        byte mask = (byte)(1 << (int)bank);

                        data = (int)(adpcmbuf[p + 0x18000] & mask);
                        data = (int)(data * 2 + (adpcmbuf[p + 0x10000] & mask));
                        data = (int)(data * 2 + (adpcmbuf[p + 0x08000] & mask));
                        data = (int)(data * 2 + (adpcmbuf[p + 0x00000] & mask));
                        data >>= (int)bank;
                        memaddr++;
                        if ((memaddr & 1) != 0)
                            return DecodeADPCMBSample(data);
                    }
                }
                else
                {
                    data = adpcmbuf[(memaddr >> granuality) & adpcmmask];
                    memaddr += (int)(1 << (granuality - 1));
                    if ((memaddr & (1 << (granuality - 1))) != 0)
                        return DecodeADPCMBSample(data >> 4);
                    data &= 0x0f;
                }
            }
            else
            {
                data = adpcmbuf[(memaddr >> 1) & adpcmmask];
                ++memaddr;
                if ((memaddr & 1) != 0)
                    return DecodeADPCMBSample(data >> 4);
                data &= 0x0f;
            }

            DecodeADPCMBSample(data);

            // check
            if (memaddr == stopaddr)
            {
                if ((control1 & 0x10) != 0)
                {
                    memaddr = startaddr;
                    data = (int)adpcmx;
                    adpcmx = 0;
                    adpcmd = 127;
                    return (int)data;
                }
                else
                {
                    memaddr &= adpcmmask;   //0x3fffff;
                    SetStatus(adpcmnotice);
                    adpcmplay = false;
                }
            }

            if (memaddr == limitaddr)
                memaddr = 0;

            return adpcmx;
        }

        int[] table1 = new int[]
        {
          1,   3,   5,   7,   9,  11,  13,  15,
         -1,  -3,  -5,  -7,  -9, -11, -13, -15,
        };

        int[] table2 = new int[]
        {
         57,  57,  57,  57,  77, 102, 128, 153,
         57,  57,  57,  57,  77, 102, 128, 153,
        };


        protected int DecodeADPCMBSample(int data)
        {
            adpcmx = fmvgen.Limit(adpcmx + table1[data] * adpcmd / 8, 32767, -32768);
            adpcmd = fmvgen.Limit(adpcmd * table2[data] / 64, 24576, 127);
            return adpcmx;
        }

        // ---------------------------------------------------------------------------
        // ステータスフラグ設定
        //
        protected void SetStatus(int bits)
        {
            if ((status & bits) == 0)
            {
                //  LOG2("SetStatus(%.2x %.2x)\n", bits, stmask);
                status |= bits & stmask;
                UpdateStatus();
            }
            // else
            //  LOG1("SetStatus(%.2x) - ignored\n", bits);
        }

        protected void ResetStatus(int bits)
        {
            status &= ~bits;
            // LOG1("ResetStatus(%.2x)\n", bits);
            UpdateStatus();
        }

        protected void UpdateStatus()
        {
            // LOG2("%d:INT = %d\n", Diag::GetCPUTick(), (status & stmask & reg29) != 0);
            //Intr((status & stmask & reg29) != 0);
        }

        private void Intr(boolean f)
        {
        }

    }
