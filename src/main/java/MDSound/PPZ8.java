package MDSound;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

    public class PPZ8 extends Instrument
    {
        @Override public String getName() { return "PPZ8"; } 
        @Override public String getShortName() { return "PPZ8"; }

        public PPZ8()
        {
            visVolume = new int[][][] {
                new int[][] { new int[] { 0, 0 } }
                , new int[][] { new int[] { 0, 0 } }
            };

        }

        @Override public void Reset(byte ChipID)
        {
            pcmData = new byte[][][] { new byte[2][], new byte[2][] };
            isPVI = new boolean[][] { new boolean[2], new boolean[2] };
            chWk = new PPZChannelWork[][]
            {
                new PPZChannelWork[]
                {
                    new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),
                    new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork()
                },
                new PPZChannelWork[]
                {
                    new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),
                    new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork()
                }
            };

            bank = new int[] { 0, 0 };
            ptr = new int[] { 0, 0 };
            interrupt = new boolean[] { false, false };
            adpcmEmu = new byte[2];
            VolumeTable = new short[][][]{
                new short[][]{
                    new short[256], new short[256], new short[256], new short[256],
                    new short[256], new short[256], new short[256], new short[256],
                    new short[256], new short[256], new short[256], new short[256],
                    new short[256], new short[256], new short[256], new short[256]
                },
                new short[][]{
                    new short[256], new short[256], new short[256], new short[256],
                    new short[256], new short[256], new short[256], new short[256],
                    new short[256], new short[256], new short[256], new short[256],
                    new short[256], new short[256], new short[256], new short[256]
                }
            };

            PCM_VOLUME = new int[2];
            volume = new int[2];
        }

        @Override public int Start(byte ChipID, int clock)
        {
            return Start(ChipID, clock, 0);
        }

        @Override public int Start(byte ChipID, int clock, int ClockValue, Object... option)
        {
            this.SamplingRate = (double)clock;
            Reset(ChipID);
            return clock;
        }

        @Override public void Stop(byte ChipID)
        {
            ;//none
        }

        @Override public int Write(byte ChipID, int port, int adr, int data)
        {
            switch ((byte)port)
            {
                case 0x00:
                    Initialize(ChipID);
                    break;
                case 0x01:
                    PlayPCM(ChipID, (byte)adr, (int)data);
                    break;
                case 0x02:
                    StopPCM(ChipID, (byte)adr);
                    break;
                case 0x03://LoadPCM
                    ;
                    break;
                case 0x04://ReadStatus
                    ReadStatus(ChipID, (byte)adr);
                    break;
                case 0x07:
                    SetVolume(ChipID, (byte)adr, (int)data);
                    break;
                case 0x0b:
                    SetFrequency(ChipID, (byte)adr, (int)(data >> 16), (int)data);
                    break;
                case 0x0e:
                    SetLoopPoint(ChipID
                        , (byte)(port >> 8)
                        , (int)(adr >> 16), (int)adr
                        , (int)(data >> 16), (int)data);
                    break;
                case 0x12:
                    StopInterrupt(ChipID);
                    break;
                case 0x13:
                    SetPan(ChipID, (byte)adr, (int)data);
                    break;
                case 0x15:
                    SetSrcFrequency(ChipID, (byte)adr, (int)data);
                    break;
                case 0x16:
                    SetAllVolume(ChipID, data);
                    break;
                case 0x18:
                    SetAdpcmEmu(ChipID, (byte)adr);
                    break;
                case 0x19:
                    SetReleaseFlag(ChipID, data);
                    break;
            }
            return 0;
        }



        public byte[][][] pcmData = new byte[][][] { new byte[2][], new byte[2][] };
        private boolean[][] isPVI = new boolean[][] { new boolean[2], new boolean[2] };
        private PPZChannelWork[][] chWk = new PPZChannelWork[][]
        {
            new PPZChannelWork[]
            {
                new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),
                new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork()
            },
            new PPZChannelWork[]
            {
                new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),
                new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork()
            }
        };
        private PPZChannelWork[][] chWkBk = new PPZChannelWork[][]
        {
            new PPZChannelWork[]
            {
                new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),
                new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork()
            },
            new PPZChannelWork[]
            {
                new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),
                new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork(),new PPZChannelWork()
            }
        };
        public int[] bank = new int[] { 0, 0 };
        public int[] ptr = new int[] { 0, 0 };
        private boolean[] interrupt = new boolean[] { false, false };
        private byte[] adpcmEmu = new byte[2];
        private short[][][] VolumeTable = new short[][][]{
            new short[][]{
                new short[256], new short[256], new short[256], new short[256],
                new short[256], new short[256], new short[256], new short[256],
                new short[256], new short[256], new short[256], new short[256],
                new short[256], new short[256], new short[256], new short[256]
            },
            new short[][]{
                new short[256], new short[256], new short[256], new short[256],
                new short[256], new short[256], new short[256], new short[256],
                new short[256], new short[256], new short[256], new short[256],
                new short[256], new short[256], new short[256], new short[256]
            }
        };
        private double SamplingRate = 44100.0;
        private int[] PCM_VOLUME = new int[2];
        private int[] volume = new int[2];

        public class PPZChannelWork
        {
            public int loopStartOffset;
            public int loopEndOffset;
            public boolean playing;
            public int pan;
            public double panL;
            public double panR;
            public int srcFrequency;
            public int volume;
            public int frequency;
            public boolean KeyOn;

            public int _loopStartOffset;
            public int _loopEndOffset;
            //public int _frequency;
            public int _srcFrequency;

            public int bank;
            public int ptr;
            public int end;
            public double delta;
            public int num;
        }

        /// <summary>
        /// 0x00 初期化
        /// </summary>
        private void Initialize(int chipID)
        {
            bank[chipID] = 0;
            ptr[chipID] = 0;
            interrupt[chipID] = false;
            for (int i = 0; i < 8; i++)
            {
                chWk[chipID][i].srcFrequency = 16000;
                chWk[chipID][i].pan = 5;
                chWk[chipID][i].panL = 1.0;
                chWk[chipID][i].panR = 1.0;
                chWk[chipID][i].volume = 8;
                //chWk[i]._frequency = 0;
                chWk[chipID][i]._loopStartOffset = -1;
                chWk[chipID][i]._loopEndOffset = -1;
            }
            PCM_VOLUME[chipID] = 0;
            volume[chipID] = 0;
            SetAllVolume(chipID, 12);
        }

        private void MakeVolumeTable(int chipID, int vol)
        {
            int i, j;
            double temp;

            volume[chipID] = vol;
            int AVolume = (int)(0x1000 * Math.pow(10.0, vol / 40.0));

            for (i = 0; i < 16; i++)
            {
                temp = Math.pow(2.0, (i + PCM_VOLUME[chipID]) / 2.0) * AVolume / 0x18000;
                for (j = 0; j < 256; j++)
                {
                    VolumeTable[chipID][i][j] = (short)(Math.max(Math.min((j - 128) * temp, Short.MAX_VALUE), Short.MIN_VALUE));
                }
            }
        }

        /// <summary>
        /// 0x01 PCM発音
        /// </summary>
        /// <param name="al">PCMチャンネル(0-7)</param>
        /// <param name="dx">PCMの音色番号</param>
        private void PlayPCM(int chipID, byte al, int dx)
        {
// #if DEBUG
            Log.WriteLine(LogLevel.TRACE, String.format("ppz8em: PlayPCM: ch:{0} @:{1}", al, dx));
// #endif

            int bank = (dx & 0x8000) != 0 ? 1 : 0;
            int num = dx & 0x7fff;
            chWk[chipID][al].bank = bank;
            chWk[chipID][al].num = num;

            if (pcmData[chipID][bank] != null)
            {
                chWk[chipID][al].ptr = pcmData[chipID][bank][num * 0x12 + 32]
                    + pcmData[chipID][bank][num * 0x12 + 1 + 32] * 0x100
                    + pcmData[chipID][bank][num * 0x12 + 2 + 32] * 0x10000
                    + pcmData[chipID][bank][num * 0x12 + 3 + 32] * 0x1000000
                    + 0x20 + 0x12 * 128;
                if (chWk[chipID][al].ptr >= pcmData[chipID][bank].length)
                {
                    chWk[chipID][al].ptr = pcmData[chipID][bank].length - 1;
                }
                chWk[chipID][al].end = chWk[chipID][al].ptr
                    + pcmData[chipID][bank][num * 0x12 + 4 + 32]
                    + pcmData[chipID][bank][num * 0x12 + 5 + 32] * 0x100
                    + pcmData[chipID][bank][num * 0x12 + 6 + 32] * 0x10000
                    + pcmData[chipID][bank][num * 0x12 + 7 + 32] * 0x1000000
                    ;
                if (chWk[chipID][al].end >= pcmData[chipID][bank].length)
                {
                    chWk[chipID][al].end = pcmData[chipID][bank].length - 1;
                }


                chWk[chipID][al].loopStartOffset = chWk[chipID][al]._loopStartOffset;
                if (chWk[chipID][al]._loopStartOffset == -1)
                {
                    chWk[chipID][al].loopStartOffset = 0
                        + pcmData[chipID][bank][num * 0x12 + 8 + 32]
                        + pcmData[chipID][bank][num * 0x12 + 9 + 32] * 0x100
                        + pcmData[chipID][bank][num * 0x12 + 10 + 32] * 0x10000
                        + pcmData[chipID][bank][num * 0x12 + 11 + 32] * 0x1000000
                        ;
                }
                chWk[chipID][al].loopEndOffset = chWk[chipID][al]._loopEndOffset;
                if (chWk[chipID][al]._loopEndOffset == -1)
                {
                    chWk[chipID][al].loopEndOffset = 0
                    + pcmData[chipID][bank][num * 0x12 + 12 + 32]
                    + pcmData[chipID][bank][num * 0x12 + 13 + 32] * 0x100
                    + pcmData[chipID][bank][num * 0x12 + 14 + 32] * 0x10000
                    + pcmData[chipID][bank][num * 0x12 + 15 + 32] * 0x1000000
                    ;
                }
                if (chWk[chipID][al].loopStartOffset == 0xffff && chWk[chipID][al].loopEndOffset == 0xffff)
                {
                    chWk[chipID][al].loopStartOffset = -1;
                    chWk[chipID][al].loopEndOffset = -1;
                }

                //不要っぽい?
                //chWk[chipID][al].srcFrequency = (int)(chWk[chipID][al].ptr
                //    + (pcmData[chipID][bank][num * 0x12 + 16 + 32] | (pcmData[chipID][bank][num * 0x12 + 17 + 32] << 8))
                //    );
                //chWk[chipID][al].frequency = chWk[chipID][al]._frequency;

                chWk[chipID][al].srcFrequency = chWk[chipID][al]._srcFrequency;
            }

            interrupt[chipID] = false;
            chWk[chipID][al].playing = true;
            chWk[chipID][al].KeyOn = true;
        }


        /// <summary>
        /// 0x02 PCM停止
        /// </summary>
        /// <param name="al">PCMチャンネル(0-7)</param>
        private void StopPCM(int chipID, byte al)
        {
// #if DEBUG
            Log.WriteLine(LogLevel.TRACE, String.format("ppz8em: StopPCM: ch:{0}", al));
// #endif

            chWk[chipID][al].playing = false;
        }

        /// <summary>
        /// 0x03 PVIファイルの読み込み＆PCMへの変換
        /// </summary>
        /// <param name="bank">0:PCMバッファ0  1:PCMバッファ1</param>
        /// <param name="mode">0:.PVI (ADPCM)  1:.PZI(PCM)</param>
        /// <param name="pcmData">ファイル内容</param>
        /// <returns></returns>
        public int LoadPcm(int chipID, byte bank, byte mode, byte[][] pcmData)
        {
// #if DEBUG
            Log.WriteLine(LogLevel.TRACE, String.format("ppz8em: LoadPCM: bank:{0} mode:{1}", bank,mode));
// #endif

            bank &= 1;
            mode &= 1;
            int ret;
            this.pcmData[chipID] = pcmData;

            if (mode == 0) //PVI形式
                ret = CheckPVI(pcmData[bank]);
            else //PZI形式
                ret = CheckPZI(pcmData[bank]);

            if (ret == 0)
            {
                //this.pcmData[chipID][bank] = new byte[pcmData[bank].length];
                //Arrays.fill(pcmData, this.pcmData[chipID][bank], pcmData[bank].length);
                isPVI[chipID][bank] = mode == 0;
                if (isPVI[chipID][bank])
                {
                    ret = ConvertPviAdpcmToPziPcm(chipID, bank);
                }
            }

            return ret;
        }

        /// <summary>
        /// 0x04 ステータスの読み込み
        /// </summary>
        /// <param name="al"></param>
        private void ReadStatus(int chipID, byte al)
        {
            switch (al)
            {
                case 0xd:
// #if DEBUG
                    Log.WriteLine(LogLevel.TRACE, "ppz8em: ReadStatus: PCM0のテーブルアドレス");
// #endif
                    bank[chipID] = 0;
                    ptr[chipID] = 0;
                    break;
                case 0xe:
// #if DEBUG
                    Log.WriteLine(LogLevel.TRACE, "ppz8em: ReadStatus: PCM1のテーブルアドレス");
// #endif
                    bank[chipID] = 1;
                    ptr[chipID] = 0;
                    break;
            }
        }

        /// <summary>
        /// 0x07 ボリュームの変更
        /// </summary>
        /// <param name="al">PCMチャネル(0~7)</param>
        /// <param name="dx">ボリューム(0-15 / 0-255)</param>
        private void SetVolume(int chipID, byte al, int dx)
        {
// #if DEBUG
            Log.WriteLine(LogLevel.TRACE, String.format("ppz8em: SetVolume: Ch:{0} vol:{1}", al, dx));
// #endif
            chWk[chipID][al].volume = dx;
        }

        /// <summary>
        /// 0x0B PCMの音程周波数の指定
        /// </summary>
        /// <param name="al">PCMチャネル(0~7)</param>
        /// <param name="dx">PCMの音程周波数DX</param>
        /// <param name="cx">PCMの音程周波数CX</param>
        private void SetFrequency(int chipID, byte al, int dx, int cx)
        {
// #if DEBUG
            Log.WriteLine(LogLevel.TRACE, String.format("ppz8em: SetFrequency: 0x{0:x8}", dx * 0x10000 + cx));
// #endif
            chWk[chipID][al].frequency = (int)(dx * 0x10000 + cx);
        }

        /// <summary>
        /// 0x0e ループポインタの設定
        /// </summary>
        /// <param name="al">PCMチャネル(0~7)</param>
        /// <param name="lpStOfsDX">ループ開始オフセットDX</param>
        /// <param name="lpStOfsCX">ループ開始オフセットCX</param>
        /// <param name="lpEdOfsDI">ループ終了オフセットDI</param>
        /// <param name="lpEdOfsSI">ループ終了オフセットSI</param>
        private void SetLoopPoint(int chipID, byte al, int lpStOfsDX, int lpStOfsCX, int lpEdOfsDI, int lpEdOfsSI)
        {
// #if DEBUG
            Log.WriteLine(LogLevel.TRACE, String.format("ppz8em: SetLoopPoint: St:0x{0:x8} Ed:0x{1:x8}"
                , lpStOfsDX * 0x10000 + lpStOfsCX, lpEdOfsDI * 0x10000 + lpEdOfsSI));
// #endif
            al &= 7;
            chWk[chipID][al]._loopStartOffset = lpStOfsDX * 0x10000 + lpStOfsCX;
            chWk[chipID][al]._loopEndOffset = lpEdOfsDI * 0x10000 + lpEdOfsSI;

            if (chWk[chipID][al]._loopStartOffset == 0xffff || chWk[chipID][al]._loopStartOffset >= chWk[chipID][al]._loopEndOffset)
            {
                chWk[chipID][al]._loopStartOffset = -1;
                chWk[chipID][al]._loopEndOffset = -1;
            }
        }

        /// <summary>
        /// 0x12 PCMの割り込みを停止
        /// </summary>
        private void StopInterrupt(int chipID)
        {
// #if DEBUG
            Log.WriteLine(LogLevel.TRACE, "ppz8em: StopInterrupt");
// #endif
            interrupt[chipID] = true;
        }

        /// <summary>
        /// 0x13 PAN指定
        /// </summary>
        /// <param name="al">PCMチャネル(0~7)</param>
        /// <param name="dx">PAN(0~9)</param>
        private void SetPan(int chipID, byte al, int dx)
        {
// #if DEBUG
            Log.WriteLine(LogLevel.TRACE, String.format("ppz8em: SetPan: {0}", dx));
// #endif
            chWk[chipID][al].pan = dx;
            chWk[chipID][al].panL = (chWk[chipID][al].pan < 6 ? 1.0 : (0.25 * (9 - chWk[chipID][al].pan)));
            chWk[chipID][al].panR = (chWk[chipID][al].pan > 4 ? 1.0 : (0.25 * chWk[chipID][al].pan));
        }

        /// <summary>
        /// 0x15 元データ周波数設定
        /// </summary>
        /// <param name="al">PCMチャネル(0~7)</param>
        /// <param name="dx">元周波数</param>
        private void SetSrcFrequency(int chipID, byte al, int dx)
        {
// #if DEBUG
            Log.WriteLine(LogLevel.TRACE, String.format("ppz8em: SetSrcFrequency: {0}", dx));
// #endif
            chWk[chipID][al]._srcFrequency = dx;
        }

        /// <summary>
        /// 0x16 全体ボリューム
        /// </summary>
        private void SetAllVolume(int chipID, int vol)
        {
// #if DEBUG
            Log.WriteLine(LogLevel.TRACE, String.format("ppz8em: SetAllVolume: {0}", vol));
// #endif
            if (vol < 16 && vol != PCM_VOLUME[chipID])
            {
                PCM_VOLUME[chipID] = vol;
                MakeVolumeTable(chipID, volume[chipID]);
            }
        }

        //-----------------------------------------------------------------------------
        //	音量調整用
        //-----------------------------------------------------------------------------
        private void SetVolume(int chipID, int vol)
        {
            if (vol != volume[chipID])
            {
                MakeVolumeTable(chipID, vol);
            }
        }

        /// <summary>
        /// 0x18  ﾁｬﾈﾙ7のADPCMのエミュレート設定
        /// </summary>
        /// <param name="al">0:ﾁｬﾈﾙ7でADPCMのエミュレートしない  1:する</param>
        private void SetAdpcmEmu(int chipID, byte al)
        {
// #if DEBUG
            Log.WriteLine(LogLevel.TRACE, String.format("ppz8em: SetAdpcmEmu: {0}", al));
// #endif
            adpcmEmu[chipID] = al;
        }

        /// <summary>
        /// 0x19 常駐解除許可、禁止設定
        /// </summary>
        /// <param name="v">0:常駐解除許可 1:常駐解除禁止</param>
        private void SetReleaseFlag(int chipID, int v)
        {
            ;//なにもしない
        }

        private int CheckPZI(byte[] pcmData)
        {
            if (pcmData == null)
                return 5;
            if (!(pcmData[0] == 'P' && pcmData[1] == 'Z' && pcmData[2] == 'I'))
                return 2;

            return 0;
        }

        private int CheckPVI(byte[] pcmData)
        {
            if (pcmData == null)
                return 5;
            if (!(pcmData[0] == 'P' && pcmData[1] == 'V' && pcmData[2] == 'I'))
                return 2;

            return 0;
        }


        @Override public void Update(byte chipID, int[][] outputs, int samples)
        {
            if (interrupt[chipID]) return;

            for (int j = 0; j < samples; j++)
            {
                int l = 0, r = 0;
                for (int i = 0; i < 8; i++)
                {
                    if (pcmData[chipID][chWk[chipID][i].bank] == null) continue;
                    if (!chWk[chipID][i].playing) continue;
                    if (chWk[chipID][i].pan == 0) continue;

                    if (i == 6)
                    {
                        //System.err.printf(VolumeTable[chWk[i].volume][pcmData[chWk[i].bank][chWk[i].ptr]]
                        //* chWk[i].panL);
                    }

                    int n = (int)chWk[chipID][i].ptr >= pcmData[chipID][chWk[chipID][i].bank].length ? 0x80 : pcmData[chipID][chWk[chipID][i].bank][chWk[chipID][i].ptr];
                    l += (int)(VolumeTable[chipID][chWk[chipID][i].volume][n] * chWk[chipID][i].panL);
                    r += (int)(VolumeTable[chipID][chWk[chipID][i].volume][n] * chWk[chipID][i].panR);
                    chWk[chipID][i].delta += ((long)chWk[chipID][i].srcFrequency * (long)chWk[chipID][i].frequency / (long)0x8000) / SamplingRate;
                    chWk[chipID][i].ptr += (int)chWk[chipID][i].delta;
                    chWk[chipID][i].delta -= (int)chWk[chipID][i].delta;

                    if (chWk[chipID][i].ptr >= chWk[chipID][i].end)
                    {
                        if (chWk[chipID][i].loopStartOffset != -1)
                        {
                            chWk[chipID][i].ptr -= chWk[chipID][i].loopEndOffset - chWk[chipID][i].loopStartOffset;
                        }
                        else
                        {
                            chWk[chipID][i].playing = false;
                        }
                    }
                }

                l = (short)Math.max(Math.min(l, Short.MAX_VALUE), Short.MIN_VALUE);
                r = (short)Math.max(Math.min(r, Short.MAX_VALUE), Short.MIN_VALUE);
                outputs[0][j] += l;
                outputs[1][j] += r;
            }

            visVolume[chipID][0][0] = outputs[0][0];
            visVolume[chipID][0][1] = outputs[1][0];
        }



        private int ConvertPviAdpcmToPziPcm(int chipID, byte bank)
        {
            int[] table1 = new int[] {
                1,   3,   5,   7,   9,  11,  13,  15,
                -1,  -3,  -5,  -7,  -9, -11, -13, -15,
            };
            int[] table2 = new int[] {
                57,  57,  57,  57,  77, 102, 128, 153,
                57,  57,  57,  57,  77, 102, 128, 153,
            };

            List<Byte> o = new ArrayList<Byte>();

            //ヘッダの生成
            o.add((byte)'P'); o.add((byte)'Z'); o.add((byte)'I'); o.add((byte)'1');
            for (int i = 4; i < 0x0b; i++) o.add((byte) 0);
            byte instCount = pcmData[chipID][bank][0xb];
            o.add(instCount);
            for (int i = 0xc; i < 0x20; i++) o.add((byte) 0);

            //音色テーブルのコンバート
            long size2 = 0;
            for (int i = 0; i < instCount; i++)
            {
                int startaddress = (int)(pcmData[chipID][bank][i * 4 + 0x10] + pcmData[chipID][bank][i * 4 + 0x11] * 0x100) << (5 + 1);
                int size = ((int)(pcmData[chipID][bank][i * 4 + 0x12] + pcmData[chipID][bank][i * 4 + 0x13] * 0x100)
                    - (int)(pcmData[chipID][bank][i * 4 + 0x10] + pcmData[chipID][bank][i * 4 + 0x11] * 0x100) + 1)
                    << (5 + 1);// endAdr - startAdr
                size2 += size;
                short rate = 16000;   // 16kHz

                o.add((byte)startaddress); o.add((byte)(startaddress >> 8)); o.add((byte)(startaddress >> 16)); o.add((byte)(startaddress >> 24));
                o.add((byte)size); o.add((byte)(size >> 8)); o.add((byte)(size >> 16)); o.add((byte)(size >> 24));
                o.add((byte)0xff); o.add((byte)0xff); o.add((byte)0); o.add((byte)0);//loop_start
                o.add((byte)0xff); o.add((byte)0xff); o.add((byte)0); o.add((byte)0);//loop_end
                o.add((byte)rate); o.add((byte)(rate >> 8));//rate
            }

            for (int i = instCount; i < 128; i++)
            {
                o.add((byte)0); o.add((byte)0); o.add((byte)0); o.add((byte)0);
                o.add((byte)0); o.add((byte)0); o.add((byte)0); o.add((byte)0);
                o.add((byte)0xff); o.add((byte)0xff); o.add((byte)0); o.add((byte)0);//loop_start
                o.add((byte)0xff); o.add((byte)0xff); o.add((byte)0); o.add((byte)0);//loop_end
                short rate = 16000;   // 16kHz
                o.add((byte)rate); o.add((byte)(rate >> 8));//rate
            }

            //ADPCM > PCM に変換
            int psrcPtr = 0x10 + 4 * 128;
            for (int i = 0; i < instCount; i++)
            {
                int X_N = 0x80; // Xn     (ADPCM>PCM 変換用)
                int DELTA_N = 127; // DELTA_N(ADPCM>PCM 変換用)

                int size = ((int)(pcmData[chipID][bank][i * 4 + 0x12] + pcmData[chipID][bank][i * 4 + 0x13] * 0x100)
                    - (int)(pcmData[chipID][bank][i * 4 + 0x10] + pcmData[chipID][bank][i * 4 + 0x11] * 0x100) + 1)
                    << (5 + 1);// endAdr - startAdr

                for (int j = 0; j < size / 2; j++)
                {
                    byte psrc = pcmData[chipID][bank][psrcPtr++];

                    int n = X_N + table1[(psrc >> 4) & 0x0f] * DELTA_N / 8;
                    //System.err.printf(n);
                    X_N = Math.max(Math.min(n, 32767), -32768);

                    n = DELTA_N * table2[(psrc >> 4) & 0x0f] / 64;
                    //System.err.printf(n);
                    DELTA_N = Math.max(Math.min(n, 24576), 127);

                    o.add((byte)(X_N / (32768 / 128) + 128));


                    n = X_N + table1[psrc & 0x0f] * DELTA_N / 8;
                    //System.err.printf(n);
                    X_N = Math.max(Math.min(n, 32767), -32768);

                    n = DELTA_N * table2[psrc & 0x0f] / 64;
                    //System.err.printf(n);
                    DELTA_N = Math.max(Math.min(n, 24576), 127);

                    o.add((byte)(X_N / (32768 / 128) + 128));


                }
            }

            byte[] a = new byte[o.size()];
            IntStream.range(0, o.size()).forEach(i -> a[i] = o.get(i).byteValue());
            pcmData[chipID][bank] = a;
            //System.IO.File.WriteAllBytes("a.raw", pcmData[bank]);
            return 0;
        }

        public PPZChannelWork[] GetPPZ8_State(byte chipID)
        {
            for(int ch = 0; ch < 8; ch++)
            {
                chWkBk[chipID][ch].bank = chWk[chipID][ch].bank;
                chWkBk[chipID][ch].delta = chWk[chipID][ch].delta;
                chWkBk[chipID][ch].end = chWk[chipID][ch].end;
                chWkBk[chipID][ch].frequency = chWk[chipID][ch].frequency;
                chWkBk[chipID][ch].loopEndOffset = chWk[chipID][ch].loopEndOffset;
                chWkBk[chipID][ch].loopStartOffset = chWk[chipID][ch].loopStartOffset;
                chWkBk[chipID][ch].num = chWk[chipID][ch].num;
                chWkBk[chipID][ch].pan = chWk[chipID][ch].pan;
                chWkBk[chipID][ch].panL = chWk[chipID][ch].panL;
                chWkBk[chipID][ch].panR = chWk[chipID][ch].panR;
                chWkBk[chipID][ch].playing = chWk[chipID][ch].playing;
                chWkBk[chipID][ch].ptr = chWk[chipID][ch].ptr;
                chWkBk[chipID][ch].srcFrequency = chWk[chipID][ch].srcFrequency;
                chWkBk[chipID][ch].volume = chWk[chipID][ch].volume;
                chWkBk[chipID][ch].KeyOn = chWk[chipID][ch].KeyOn;

                chWk[chipID][ch].KeyOn = false;
            }
            return chWkBk[chipID];
        }
    }
