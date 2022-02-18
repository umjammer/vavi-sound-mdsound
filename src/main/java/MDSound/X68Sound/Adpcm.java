package MDSound.X68Sound;

public class Adpcm
    {
        private Global global = null;

        private int Scale;      // 
        private int Pcm;        // 16bit PCM Data
        private int InpPcm, InpPcm_prev, OutPcm;        // HPF用 16bit PCM Data
        private int OutInpPcm, OutInpPcm_prev;      // HPF用
        private int AdpcmRate; // 187500(15625*12), 125000(10416.66*12), 93750(7812.5*12), 62500(5208.33*12), 46875(3906.25*12), ...
        private int RateCounter;
        private int N1Data; // ADPCM 1サンプルのデータの保存
        private int N1DataFlag; // 0 or 1

        //inline void adpcm2pcm(unsigned char adpcm);

        //public:
        public Runnable IntProc;// void (CALLBACK* IntProc) ();	// 割り込みアドレス
        public Runnable ErrIntProc;//void (CALLBACK* ErrIntProc) ();	// 割り込みアドレス
        ////	int	AdpcmFlag;	// 0:非動作  1:再生中
        ////	int PpiReg;		// PPI レジスタの内容
        ////	int	DmaCsr;		// DMA CSR レジスタの内容
        ////	int	DmaCcr;		// DMA CCR レジスタの内容
        ////	int	DmaFlag;	// 0:DMA非動作  1:DMA動作中
        //inline int DmaGetByte();
        public byte DmaLastValue;
        public byte AdpcmReg;
        public byte[] DmaReg = new byte[0x40];
        public int FinishCounter;
        //inline void DmaError(unsigned char errcode);
        //inline void DmaFinish();
        //inline int DmaContinueSetNextMtcMar();
        //inline int DmaArrayChainSetNextMtcMar();
        //inline int DmaLinkArrayChainSetNextMtcMar();

        //Adpcm(void);
        //~Adpcm() { };
        //inline void Init();
        //inline void InitSamprate();
        //inline void Reset();
        //inline int GetPcm();
        //inline int GetPcm62();

        //inline void SetAdpcmRate(int rate);




        public Adpcm(Global global)
        {
            this.global = global;
        }

        public void SetAdpcmRate(int rate)
        {
            AdpcmRate = global.ADPCMRATEADDTBL[rate & 7];
        }

        private byte[] DmaRegInit = new byte[] {
/*+00*/	0x00,0x00,	// CSR/CER
/*+02*/	(byte)0xFF,(byte)0xFF,
/*+04*/	(byte)0x80,0x32,	// DCR/OCR
/*+06*/	0x04,0x08,	// SCR/CCR
/*+08*/	(byte)0xFF,(byte)0xFF,
/*+0A*/	0x00,0x00,	// MTC
/*+0C*/	0x00,0x00,	// MAR
/*+0E*/	0x00,0x00,	// MAR
/*+10*/	(byte)0xFF,(byte)0xFF,
/*+12*/	(byte)0xFF,(byte)0xFF,
/*+14*/	0x00,(byte)0xE9,	// DAR
/*+16*/	0x20,0x03,	// DAR
/*+18*/	(byte)0xFF,(byte)0xFF,
/*+1A*/	0x00,0x00,	// BTC
/*+1C*/	0x00,0x00,	// BAR
/*+1E*/	0x00,0x00,	// BAR
/*+20*/	(byte)0xFF,(byte)0xFF,
/*+22*/	(byte)0xFF,(byte)0xFF,
/*+24*/	(byte)0xFF,0x6A,	// NIV
/*+26*/	(byte)0xFF,0x6B,	// EIV
/*+28*/	(byte)0xFF,0x05,	// MFC
/*+2A*/	(byte)0xFF,(byte)0xFF,
/*+2C*/	(byte)0xFF,0x01,	// CPR
/*+2E*/	(byte)0xFF,(byte)0xFF,
/*+30*/	(byte)0xFF,0x05,	// DFC
/*+32*/	(byte)0xFF,(byte)0xFF,
/*+34*/	(byte)0xFF,(byte)0xFF,
/*+36*/	(byte)0xFF,(byte)0xFF,
/*+38*/	(byte)0xFF,0x05,	// BFC
/*+3A*/	(byte)0xFF,(byte)0xFF,
/*+3C*/	(byte)0xFF,(byte)0xFF,
/*+3E*/	(byte)0xFF,0x00,	// GCR
};

        public void Init()
        {
            Scale = 0;
            Pcm = 0;
            InpPcm = InpPcm_prev = OutPcm = 0;
            OutInpPcm = OutInpPcm_prev = 0;
            AdpcmRate = 15625 * 12;
            RateCounter = 0;
            N1Data = 0;
            N1DataFlag = 0;
            IntProc = null;
            ErrIntProc = null;
            DmaLastValue = 0;
            AdpcmReg = (byte) 0xC7;
            {
                int i;
                for (i = 0; i < 0x40; ++i)
                {
                    DmaReg[i] = DmaRegInit[i];
                }
            }
            FinishCounter = 3;
        }

        public void InitSamprate()
        {
            RateCounter = 0;
        }

        public void Reset()
        {   // ADPCM キーオン時の処理

            Scale = 0;

            Pcm = 0;
            InpPcm = InpPcm_prev = OutPcm = 0;
            OutInpPcm = OutInpPcm_prev = 0;

            N1Data = 0;
            N1DataFlag = 0;


        }

        public void DmaError(byte errcode)
        {
            DmaReg[0x00] &= 0xF7;       // ACT=0
            DmaReg[0x00] |= 0x90;       // COC=ERR=1
            DmaReg[0x01] = errcode;     // CER=errorcode
            if ((DmaReg[0x07] & 0x08) != 0)
            {   // INT==1?
                ErrIntProc.run();
            }
        }

        public void DmaFinish()
        {
            DmaReg[0x00] &= 0xF7;       // ACT=0
            DmaReg[0x00] |= 0x80;       // COC=1
            if ((DmaReg[0x07] & 0x08) != 0)
            {   // INT==1?
                IntProc.run();
            }
        }

        public int DmaContinueSetNextMtcMar()
        {
            DmaReg[0x07] &= (0xFF - 0x40);  // CNT=0

            //*(int*)&DmaReg[0x0A] = *(int*)&DmaReg[0x1A];  // BTC . MTC
            DmaReg[0x0A] = DmaReg[0x1A];
            DmaReg[0x0B] = DmaReg[0x1B];
            //*((int)*)&DmaReg[0x0C] = *((int)*)&DmaReg[0x1C];  // BAR . MAR
            DmaReg[0x0C] = DmaReg[0x1C];
            DmaReg[0x0D] = DmaReg[0x1D];
            DmaReg[0x0E] = DmaReg[0x1E];
            DmaReg[0x0F] = DmaReg[0x1F];

            DmaReg[0x29] = DmaReg[0x39];    // BFC . MFC

            if ((DmaReg[0x0A] | DmaReg[0x0B]) == 0)
            {  // MTC == 0 ?
                DmaError((byte)0x0D); // カウントエラー(メモリアドレス/メモリカウンタ)
                return 1;
            }

            DmaReg[0x00] |= 0x40;       // BTC=1

            if ((DmaReg[0x07] & 0x08) != 0)
            {   // INT==1?
                IntProc.run();
            }
            return 0;
        }

        public int DmaArrayChainSetNextMtcMar()
        {
            int Btc;
            //Btc = Global.bswapw((int)(DmaReg[0x1A] * 0x100 + DmaReg[0x1B]));
            Btc = (int)(DmaReg[0x1A] * 0x100 + DmaReg[0x1B]);
            if (Btc == 0)
            {
                DmaFinish();
                FinishCounter = 0;
                return 1;
            }
            --Btc;
            //*(int*)&DmaReg[0x1A] = Global.bswapw(Btc);
            DmaReg[0x1A] = (byte)(Btc >> 8);
            DmaReg[0x1B] = (byte)Btc;

            //byte* Bar;
            int Bar;
            //Bar = Global.bswapl(*(byte**)&DmaReg[0x1C]);
            Bar = (int)(
                DmaReg[0x1C] * 0x1000000
                + DmaReg[0x1D] * 0x10000
                + DmaReg[0x1E] * 0x100
                + DmaReg[0x1F]
                );
            int mem0, mem1, mem2, mem3, mem4, mem5;
            mem0 = global.MemRead.apply(Bar++);
            mem1 = global.MemRead.apply(Bar++);
            mem2 = global.MemRead.apply(Bar++);
            mem3 = global.MemRead.apply(Bar++);
            mem4 = global.MemRead.apply(Bar++);
            mem5 = global.MemRead.apply(Bar++);
            if ((mem0 | mem1 | mem2 | mem3 | mem4 | mem5) == -1)
            {
                DmaError((byte)0x0B);     // バスエラー(ベースアドレス/ベースカウンタ)
                return 1;
            }
            //*(byte**)&DmaReg[0x1C] = Global.bswapl(Bar);
            DmaReg[0x1C] = (byte)(Bar >> 24);
            DmaReg[0x1D] = (byte)(Bar >> 16);
            DmaReg[0x1E] = (byte)(Bar >> 8);
            DmaReg[0x1F] = (byte)(Bar);

            DmaReg[0x0C] = (byte)mem0;    // MAR
            DmaReg[0x0D] = (byte)mem1;
            DmaReg[0x0E] = (byte)mem2;
            DmaReg[0x0F] = (byte)mem3;
            DmaReg[0x0A] = (byte)mem4;    // MTC
            DmaReg[0x0B] = (byte)mem5;

            if ((DmaReg[0x0A] | DmaReg[0x0B]) == 0)
            {  // MTC == 0 ?
                DmaError((byte)0x0D);     // カウントエラー(メモリアドレス/メモリカウンタ)
                return 1;
            }
            return 0;
        }

        public int DmaLinkArrayChainSetNextMtcMar()
        {
            //byte* Bar;
            int Bar;
            //Bar = Global.bswapl(*(byte**)&DmaReg[0x1C]);
            Bar = (int)(
                DmaReg[0x1C] * 0x1000000
                + DmaReg[0x1D] * 0x10000
                + DmaReg[0x1E] * 0x100
                + DmaReg[0x1F]
                );
            if (Bar == 0)
            {
                DmaFinish();
                FinishCounter = 0;
                return 1;
            }

            int mem0, mem1, mem2, mem3, mem4, mem5;
            int mem6, mem7, mem8, mem9;
            mem0 = global.MemRead.apply(Bar++);
            mem1 = global.MemRead.apply(Bar++);
            mem2 = global.MemRead.apply(Bar++);
            mem3 = global.MemRead.apply(Bar++);
            mem4 = global.MemRead.apply(Bar++);
            mem5 = global.MemRead.apply(Bar++);
            mem6 = global.MemRead.apply(Bar++);
            mem7 = global.MemRead.apply(Bar++);
            mem8 = global.MemRead.apply(Bar++);
            mem9 = global.MemRead.apply(Bar++);
            if ((mem0 | mem1 | mem2 | mem3 | mem4 | mem5 | mem6 | mem7 | mem8 | mem9) == -1)
            {
                DmaError((byte)0x0B);     // バスエラー(ベースアドレス/ベースカウンタ)
                return 1;
            }
            //*(byte**)&DmaReg[0x1C] = Global.bswapl(Bar);
            DmaReg[0x1C] = (byte)(Bar >> 24);
            DmaReg[0x1D] = (byte)(Bar >> 16);
            DmaReg[0x1E] = (byte)(Bar >> 8);
            DmaReg[0x1F] = (byte)(Bar);

            DmaReg[0x0C] = (byte)mem0;    // MAR
            DmaReg[0x0D] = (byte)mem1;
            DmaReg[0x0E] = (byte)mem2;
            DmaReg[0x0F] = (byte)mem3;
            DmaReg[0x0A] = (byte)mem4;    // MTC
            DmaReg[0x0B] = (byte)mem5;
            DmaReg[0x1C] = (byte)mem6;    // BAR
            DmaReg[0x1D] = (byte)mem7;
            DmaReg[0x1E] = (byte)mem8;
            DmaReg[0x1F] = (byte)mem9;

            if ((DmaReg[0x0A] | DmaReg[0x0B]) == 0)
            {  // MTC == 0 ?
                DmaError((byte)0x0D);     // カウントエラー(メモリアドレス/メモリカウンタ)
                return 1;
            }
            return 0;
        }

        private int[] MACTBL = new int[] { 0, 1, -1, 1 };

        public int DmaGetByte()
        {
            if (((DmaReg[0x00] & 0x08) == 0) || ((DmaReg[0x07] & 0x20) != 0))
            {   // ACT==0 || HLT==1 ?
                return -2147483648;// 0x80000000;
            }
            int Mtc;
            //Mtc = Global.bswapw(*(int*)&DmaReg[0x0A]);
            Mtc = (int)(DmaReg[0x0A] * 0x100 + DmaReg[0x0B]);
            if (Mtc == 0)
            {
                //		if (DmaReg[0x07] & 0x40) {	// Continue動作
                //			if (DmaContinueSetNextMtcMar()) {
                //				return 0x80000000;
                //			}
                //			Mtc = bswapw(*(unsigned short *)&DmaReg[0x0A]);
                //		} else {
                return -2147483648;// 0x80000000;
                //		}
            }


            {
                //byte* Mar;
                int Mar;
                //Mar = Global.bswapl(*(byte**)&DmaReg[0x0C]);
                Mar = (int)(
                    DmaReg[0x0C] * 0x1000000
                    + DmaReg[0x0D] * 0x10000
                    + DmaReg[0x0E] * 0x100
                    + DmaReg[0x0F]
                    );
                int mem;
                mem = global.MemRead.apply(Mar);
                if (mem == -1)
                {
                    DmaError((byte)0x09); // バスエラー(メモリアドレス/メモリカウンタ)
                    return -2147483648;// 0x80000000;
                }
                DmaLastValue = (byte)mem;
                Mar += (int)MACTBL[(DmaReg[0x06] >> 2) & 3];
                //*(byte**)&DmaReg[0x0C] = Global.bswapl(Mar);
                DmaReg[0x0C] = (byte)(Mar >> 24);
                DmaReg[0x0D] = (byte)(Mar >> 16);
                DmaReg[0x0E] = (byte)(Mar >> 8);
                DmaReg[0x0F] = (byte)(Mar);
            }

            --Mtc;
            //*(int*)&DmaReg[0x0A] = Global.bswapw(Mtc);
            DmaReg[0x0A] = (byte)(Mtc >> 8);
            DmaReg[0x0B] = (byte)Mtc;

            try
            {
                if (Mtc == 0)
                {
                    if ((DmaReg[0x07] & 0x40) != 0)
                    {   // Continue動作
                        if (DmaContinueSetNextMtcMar() != 0)
                        {
                            throw new Exception("");
                        }
                    }
                    else if ((DmaReg[0x05] & 0x08) != 0)
                    {   // チェイニング動作
                        if ((DmaReg[0x05] & 0x04) == 0)
                        {   // アレイチェイン
                            if (DmaArrayChainSetNextMtcMar() != 0)
                            {
                                throw new Exception("");
                            }
                        }
                        else
                        {                       // リンクアレイチェイン
                            if (DmaLinkArrayChainSetNextMtcMar() != 0)
                            {
                                throw new Exception("");
                            }
                        }
                    }
                    else
                    {   // ノーマル転送終了
                        //			if (!(DmaReg[0x00] & 0x40)) {		// BTC=1 ?
                        //				if (DmaContinueSetNextMtcMar()) {
                        //					throw "";
                        //				}
                        //			} else {
                        DmaFinish();
                        FinishCounter = 0;
                        //			}
                    }
                }
            }
            catch (Exception e)
            {
            }

            return DmaLastValue;
        }




        private final int MAXPCMVAL = (2047);

        // adpcmを入力して InpPcm の値を変化させる
        // -2047<<(4+4) <= InpPcm <= +2047<<(4+4)
        public void adpcm2pcm(byte adpcm)
        {


            int dltL;
            dltL = global.dltLTBL[Scale];
            dltL = (dltL & ((adpcm & 4) != 0 ? -1 : 0))
                        + ((dltL >> 1) & ((adpcm & 2) != 0 ? -1 : 0))
                        + ((dltL >> 2) & ((adpcm & 1) != 0 ? -1 : 0)) + (dltL >> 3);
            int sign = (adpcm & 8) != 0 ? -1 : 0;
            dltL = (dltL ^ sign) + (sign & 1);
            Pcm += dltL;


            if ((int)(Pcm + MAXPCMVAL) > (int)(MAXPCMVAL * 2))
            {
                if ((int)(Pcm + MAXPCMVAL) >= (int)(MAXPCMVAL * 2))
                {
                    Pcm = MAXPCMVAL;
                }
                else
                {
                    Pcm = -MAXPCMVAL;
                }
            }

            InpPcm = (Pcm & -4)//(int)0xFFFFFFFC) 
                << (4 + 4);

            Scale += global.DCT[adpcm];
            if ((int)Scale > (int)48)
            {
                if ((int)Scale >= (int)48)
                {
                    Scale = 48;
                }
                else
                {
                    Scale = 0;
                }
            }
        }

        // -32768<<4 <= retval <= +32768<<4
        public int GetPcm()
        {
            if ((AdpcmReg & 0x80) != 0)
            {       // ADPCM 停止中
                return -2147483648;// 0x80000000;
            }
            RateCounter -= AdpcmRate;
            while (RateCounter < 0)
            {
                if (N1DataFlag == 0)
                {       // 次のADPCMデータが内部にない場合
                    int N10Data;    // (N1Data << 4) | N0Data
                    N10Data = DmaGetByte(); // DMA転送(1バイト)
                    if (N10Data == -2147483648)//0x80000000)
                    {
                        RateCounter = 0;
                        return -2147483648;// 0x80000000;
                    }
                    adpcm2pcm((byte)(N10Data & 0x0F));  // InpPcm に値が入る
                    N1Data = (N10Data >> 4) & 0x0F;
                    N1DataFlag = 1;
                }
                else
                {
                    adpcm2pcm((byte)N1Data);          // InpPcm に値が入る
                    N1DataFlag = 0;
                }
                RateCounter += 15625 * 12;
            }
            OutPcm = ((InpPcm << 9) - (InpPcm_prev << 9) + 459 * OutPcm) >> 9;
            InpPcm_prev = InpPcm;

            return (OutPcm * global.TotalVolume) >> 8;
        }

        // -32768<<4 <= retval <= +32768<<4
        public int GetPcm62()
        {
            if ((AdpcmReg & 0x80) != 0)
            {       // ADPCM 停止中
                return -2147483648;// 0x80000000;
            }
            RateCounter -= AdpcmRate;
            while (RateCounter < 0)
            {
                if (N1DataFlag == 0)
                {       // 次のADPCMデータが内部にない場合
                    int N10Data;    // (N1Data << 4) | N0Data
                    N10Data = DmaGetByte(); // DMA転送(1バイト)
                    if (N10Data == -2147483648)//0x80000000)
                    {
                        RateCounter = 0;
                        return -2147483648;// 0x80000000;
                    }
                    adpcm2pcm((byte)(N10Data & 0x0F));  // InpPcm に値が入る
                    N1Data = (N10Data >> 4) & 0x0F;
                    N1DataFlag = 1;
                }
                else
                {
                    adpcm2pcm((byte)N1Data);          // InpPcm に値が入る
                    N1DataFlag = 0;
                }
                RateCounter += 15625 * 12 * 4;

            }
            OutInpPcm = (InpPcm << 9) - (InpPcm_prev << 9) + OutInpPcm - (OutInpPcm >> 5) - (OutInpPcm >> 10);
            InpPcm_prev = InpPcm;
            OutPcm = OutInpPcm - OutInpPcm_prev + OutPcm - (OutPcm >> 8) - (OutPcm >> 9) - (OutPcm >> 12);
            OutInpPcm_prev = OutInpPcm;
            return (OutPcm >> 9);
        }

    }


