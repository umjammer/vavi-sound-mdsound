package MDSound.fmvgen;
import MDSound.fmgen.opna.OPNABase;
import MDSound.fmvgen.effect.Compressor;
import MDSound.fmvgen.effect.HPFLPF;
import MDSound.fmvgen.effect.ReversePhase;
import MDSound.fmvgen.effect.chorus;
import MDSound.fmvgen.effect.distortion;
import MDSound.fmvgen.effect.reverb;

    public class ADPCMA
    {
        public OPNA2 parent = null;
        public class Channel
        {
            public float panL;      // ぱん
            public float panR;      // ぱん
            public byte level;     // おんりょう
            public int volume;     // おんりょうせってい
            public int pos;       // いち
            public int step;      // すてっぷち

            public int start;     // 開始
            public int stop;      // 終了
            public int nibble;        // 次の 4 bit
            public short adpcmx;     // 変換用
            public short adpcmd;     // 変換用
        };

        public Channel[] channel = new Channel[] { 
            new Channel(), new Channel(), new Channel(),
            new Channel(), new Channel(), new Channel() 
        };

        public byte[] buf;       // ADPCMA ROM
        public int size;
        public byte tl;      // ADPCMA 全体の音量
        public int tvol;
        public byte key;        // ADPCMA のキー
        public int step;
        public byte[] reg = new byte[32];
        public static short[] jedi_table = new short[(48 + 1) * 16];
        private reverb reverb = null;
        private distortion distortion = null;
        private chorus chorus = null;
        private HPFLPF hpflpf = null;
        private int revStartCh = 0;
        private int num = 0;
        private ReversePhase reversePhase;
        private Compressor compressor;

        private byte[] table2 = new byte[]
        {
             1,  3,  5,  7,  9, 11, 13, 15,
            -1, -3, -5, -7, -9,-11,-13,-15,
        };

        private int[] decode_tableA1 = new int[]
        {
            -1*16, -1*16, -1*16, -1*16, 2*16, 5*16, 7*16, 9*16,
            -1*16, -1*16, -1*16, -1*16, 2*16, 5*16, 7*16, 9*16
        };
        private int currentCh;
        private boolean currentIsLSB;
        //protected float[] panTable = new float[4] { 1.0f, 0.5012f, 0.2512f, 0.1000f };

        public ADPCMA(int num,reverb reverb, distortion distortion,chorus chorus,HPFLPF hpflpf, ReversePhase reversePhase, Compressor compressor, int revStartCh)
        {
            this.num = num;
            this.reversePhase = reversePhase;
            this.reverb = reverb;
            this.distortion = distortion;
            this.chorus = chorus;
            this.hpflpf = hpflpf;
            this.compressor = compressor;
            
            this.revStartCh = revStartCh;
            this.buf = null;
            this.size = 0;
            for (int i = 0; i < 6; i++)
            {
                channel[i].panL = 1.0f;
                channel[i].panR = 1.0f;
                channel[i].level = 0;
                channel[i].volume = 0;
                channel[i].pos = 0;
                channel[i].step = 0;
                channel[i].volume = 0;
                channel[i].start = 0;
                channel[i].stop = 0;
                channel[i].adpcmx = 0;
                channel[i].adpcmd = 0;
            }
            this.tl = 0;
            this.key = 0;
            this.tvol = 0;

            InitADPCMATable();

        }

        public void InitADPCMATable()
        {
            for (int i = 0; i <= 48; i++)
            {
                int s = (int)(16.0 * Math.pow(1.1, i) * 3);
                for (int j = 0; j < 16; j++)
                {
                    jedi_table[i * 16 + j] = (short)(s * table2[j] / 8);
                }
            }
        }


        // ---------------------------------------------------------------------------
        //	ADPCMA 合成
        //
        public void Mix(int[] buffer, int count)
        {

            if (tvol < 128 && (key & 0x3f) != 0)
            {
                //Sample* limit = buffer + count * 2;
                int limit = count * 2;
                int revSampleL = 0;
                int revSampleR = 0;
                for (int i = 0; i < 6; i++)
                {
                    Channel r = channel[i];
                    if ((key & (1 << i)) != 0 && (byte)r.level < 128)
                    {
                        //int maskl = (int)(r.panL == 0f ? -1 : 0);
                        //int maskr = (int)(r.panR == 0f ? -1 : 0);

                        int db = fmvgen.Limit(tl + tvol + r.level + r.volume, 127, -31);
                        int vol = OPNABase.tltable[fmvgen.FM_TLPOS + (db << (fmvgen.FM_TLBITS - 7))] >> 4;

                        //Sample* dest = buffer;
                        int dest = 0;
                        for (; dest < limit; dest += 2)
                        {
                            r.step += (int)step;
                            if (r.pos >= r.stop)
                            {
                                //SetStatus((int)(0x100 << i));
                                key &= (byte)~(1 << i);
                                break;
                            }

                            for (; r.step > 0x10000; r.step -= 0x10000)
                            {
                                int data;
                                if ((r.pos & 1) == 0)
                                {
                                    r.nibble = buf[r.pos >> 1];
                                    data = (int)(r.nibble >> 4);
                                }
                                else
                                {
                                    data = (int)(r.nibble & 0x0f);
                                }
                                r.pos++;

                                r.adpcmx += jedi_table[r.adpcmd + data];
                                r.adpcmx = (short)fmvgen.Limit(r.adpcmx, 2048 * 3 - 1, -2048 * 3);
                                r.adpcmd += (short)decode_tableA1[data];
                                r.adpcmd = (short)fmvgen.Limit(r.adpcmd, 48 * 16, 0);
                            }

                            int sampleL = (int)((r.adpcmx * vol) >> 10);
                            int sampleR = (int)((r.adpcmx * vol) >> 10);
                            distortion.Mix(revStartCh + i, sampleL, sampleR);
                            chorus.Mix(revStartCh + i, sampleL, sampleR);
                            hpflpf.Mix(revStartCh + i, sampleL, sampleR);
                            compressor.Mix(revStartCh + i, sampleL, sampleR);

                            sampleL = (int)(sampleL * r.panL) * reversePhase.AdpcmA[i][0];
                            sampleR = (int)(sampleR * r.panR) * reversePhase.AdpcmA[i][1];
                            fmvgen.StoreSample(buffer[dest + 0], sampleL);
                            fmvgen.StoreSample(buffer[dest + 1], sampleR);
                            revSampleL += (int)(sampleL * reverb.SendLevel[revStartCh + i] * 0.6);
                            revSampleR += (int)(sampleR * reverb.SendLevel[revStartCh + i] * 0.6);
                            //visRtmVolume[0] = (int)(sample & maskl);
                            //visRtmVolume[1] = (int)(sample & maskr);
                        }
                    }
                }
                reverb.StoreDataC(revSampleL, revSampleR);
            }
        }

        public void SetReg(int adr, byte data)
        {
            switch(adr)
            {
                case 0x00:         // DM/KEYON
                    if ((data & 0x80) == 0)  // KEY ON
                    {
                        key |= (byte)(data & 0x3f);
                        for (int c = 0; c < 6; c++)
                        {
                            if ((data & (1 << c)) != 0)
                            {
                                //ResetStatus((int)(0x100 << c));
                                channel[c].pos = channel[c].start;
                                channel[c].step = 0;
                                channel[c].adpcmx = 0;
                                channel[c].adpcmd = 0;
                                channel[c].nibble = 0;
                            }
                        }
                    }
                    else
                    {                   // DUMP
                        key &= (byte)~data;
                    }
                    break;

                case 0x01:
                    tl = (byte)(~data & 63);
                    break;

                case 0x02:
                    currentCh = data % 6;
                    currentIsLSB = true;
                    break;

                case 0x03:
                    channel[currentCh].level = (byte)(~data & 31);
                    break;

                case 0x04:
                    channel[currentCh].panL = OPNA2.panTable[((data >> 5) & 3) & 3] * ((data >> 7) & 1);
                    channel[currentCh].panR = OPNA2.panTable[((data >> 2) & 3) & 3] * ((data >> 4) & 1);
                    break;

                case 0x05:
                    if (currentIsLSB)
                    {
                        channel[currentCh].start = data;
                        currentIsLSB = false;
                    }
                    else
                    {
                        channel[currentCh].start |= (int)(data * 0x100);
                        channel[currentCh].start <<= 9;
                        currentIsLSB = true;
                    }
                    break;

                case 0x06:
                    if (currentIsLSB)
                    {
                        channel[currentCh].stop = data;
                        currentIsLSB = false;
                    }
                    else
                    {
                        channel[currentCh].stop |= (int)(data * 0x100);
                        channel[currentCh].stop <<= 9;
                        currentIsLSB = true;
                    }
                    break;

            }
        }
    }
