package MDSound.fmgen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import MDSound.common;
import dotnet4j.io.FileAccess;
import dotnet4j.io.FileMode;
import dotnet4j.io.FileStream;
import dotnet4j.io.Stream;

// ---------------------------------------------------------------------------
//	OPN/A/B interface with ADPCM support
//	Copyright (C) cisc 1998, 2003.
// ---------------------------------------------------------------------------
//	$Id: opna.h,v 1.33 2003/06/12 13:14:37 cisc Exp $

// ---------------------------------------------------------------------------
//	class OPN/OPNA
//	OPN/OPNA に良く似た音を生成する音源ユニット
//	
//	interface:
//	boolean Init(int clock, int rate, bool, final char* path);
//		初期化．このクラスを使用する前にかならず呼んでおくこと．
//		OPNA の場合はこの関数でリズムサンプルを読み込む
//
//		clock:	OPN/OPNA/OPNB のクロック周波数(Hz)
//
//		rate:	生成する PCM の標本周波数(Hz)
//
//		path:	リズムサンプルのパス(OPNA のみ有効)
//				省略時はカレントディレクトリから読み込む
//				文字列の末尾には '\' や '/' などをつけること
//
//		返り値	初期化に成功すれば true
//
//	boolean LoadRhythmSample(final char* path)
//		(OPNA ONLY)
//		Rhythm サンプルを読み直す．
//		path は Init の path と同じ．
//		
//	boolean SetRate(int clock, int rate, bool)
//		クロックや PCM レートを変更する
//		引数等は Init を参照のこと．
//	
//	void Mix(FM_SAMPLETYPE* dest, int nsamples)
//		Stereo PCM データを nsamples 分合成し， dest で始まる配列に
//		加える(加算する)
//		・dest には sample*2 個分の領域が必要
//		・格納形式は L, R, L, R... となる．
//		・あくまで加算なので，あらかじめ配列をゼロクリアする必要がある
//		・FM_SAMPLETYPE が short 型の場合クリッピングが行われる.
//		・この関数は音源内部のタイマーとは独立している．
//		  Timer は Count と GetNextEvent で操作する必要がある．
//	
//	void Reset()
//		音源をリセット(初期化)する
//
//	void SetReg(int reg, int data)
//		音源のレジスタ reg に data を書き込む
//	
//	int GetReg(int reg)
//		音源のレジスタ reg の内容を読み出す
//		読み込むことが出来るレジスタは PSG, ADPCM の一部，ID(0xff) とか
//	
//	int ReadStatus()/ReadStatusEx()
//		音源のステータスレジスタを読み出す
//		ReadStatusEx は拡張ステータスレジスタの読み出し(OPNA)
//		busy フラグは常に 0
//	
//	boolean Count((int)32 t)
//		音源のタイマーを t [μ秒] 進める．
//		音源の内部状態に変化があった時(timer オーバーフロー)
//		true を返す
//
//	(int)32 GetNextEvent()
//		音源のタイマーのどちらかがオーバーフローするまでに必要な
//		時間[μ秒]を返す
//		タイマーが停止している場合は ULONG_MAX を返す… と思う
//	
//	void SetVolumeFM(int db)/SetVolumePSG(int db) ...
//		各音源の音量を＋－方向に調節する．標準値は 0.
//		単位は約 1/2 dB，有効範囲の上限は 20 (10dB)
//

public class opna {
    //	OPN Base -------------------------------------------------------
    static class OPNBase extends Timer
    {
        public OPNBase()
        {
            prescale = 0;
            psg = new PSG();
            chip = new fmgen.Chip();
        }

        //	初期化
        public boolean Init(int c, int r)
        {
            clock = c;
            psgrate = r;

            return true;
        }

        public  void Reset()
        {
            status = 0;
            SetPrescaler(0);
            super.Reset();
            psg.Reset();
        }

        //	音量設定
        public void SetVolumeFM(int db)
        {
            db = Math.min(db, 20);
            if (db > -192)
                fmvolume = (int)(16384.0 * Math.pow(10.0, db / 40.0));
            else
                fmvolume = 0;
        }

        public void SetVolumePSG(int db)
        {
            psg.SetVolume(db);
        }

        public void SetLPFCutoff(int freq)
        {
        }    // obsolete

        protected void SetParameter(fmgen.Channel4 ch, int addr, int data)
        {
            int[] slottable=new int[]{ 0, 2, 1, 3 };
            byte[] sltable = new byte[] {
              0,   4,   8,  12,  16,  20,  24,  28,
             32,  36,  40,  44,  48,  52,  56, 124
            };

            if ((addr & 3) < 3)
            {
                int slot = slottable[(addr >> 2) & 3];
                fmgen.Operator op = ch.op[slot];

                switch ((addr >> 4) & 15)
                {
                    case 3: // 30-3E DT/MULTI
                        op.SetDT((data >> 4) & 0x07);
                        op.SetMULTI(data & 0x0f);
                        break;

                    case 4: // 40-4E TL
                        op.SetTL(data & 0x7f, ((regtc & 0x80)!=0) && (csmch == ch));
                        break;

                    case 5: // 50-5E KS/AR
                        op.SetKS((data >> 6) & 3);
                        op.SetAR((data & 0x1f) * 2);
                        break;

                    case 6: // 60-6E DR/AMON
                        op.SetDR((data & 0x1f) * 2);
                        op.SetAMON((data & 0x80) != 0);
                        break;

                    case 7: // 70-7E SR
                        op.SetSR((data & 0x1f) * 2);
                        break;

                    case 8: // 80-8E SL/RR
                        op.SetSL(sltable[(data >> 4) & 15]);
                        op.SetRR((data & 0x0f) * 4 + 2);
                        break;

                    case 9: // 90-9E SSG-EC
                        op.SetSSGEC(data & 0x0f);
                        break;
                }
            }
        }

        protected void SetPrescaler(int p)
        {
            byte[][] table = new byte[][] { new byte[] { 6, 4 }, new byte[] { 3, 2 }, new byte[] { 2, 1 } };
            byte[] table2 = new byte[] { 108, 77, 71, 67, 62, 44, 8, 5 };
            // 512
            if (prescale != p)
            {
                prescale = (byte)p;
                //assert(0 <= prescale && prescale< 3);

                int fmclock = (int)(clock / table[p][0] / 12);

                rate = psgrate;

                // 合成周波数と出力周波数の比
                //assert(fmclock< (0x80000000 >> FM_RATIOBITS));
                int ratio = ((fmclock << fmgen.FM_RATIOBITS) + rate / 2) / rate;

                SetTimerBase(fmclock);
                //		MakeTimeTable(ratio);
                chip.SetRatio(ratio);
                psg.SetClock((int)(clock / table[p][1]), (int)psgrate);

                for (int i = 0; i < 8; i++)
                {
                    lfotable[i] = (ratio << (2 + fmgen.FM_LFOCBITS - fmgen.FM_RATIOBITS)) / table2[i];
                }
            }
        }

        protected void RebuildTimeTable()
        {
            int p = prescale;
            prescale = (byte) 0xff;//-1;
            SetPrescaler((int)p);
        }



        protected int fmvolume;

        protected int clock;             // OPN クロック
        protected int rate;              // FM 音源合成レート
        protected int psgrate;           // FMGen  出力レート
        protected int status;
        protected fmgen.Channel4 csmch;

        public int[] visVolume = new int[] { 0, 0 };

        protected int[] lfotable = new int[8];

        //	タイマー時間処理
        private void TimerA()
        {
            if ((regtc & 0x80)!=0)
            {
                csmch.KeyControl(0x00);
                csmch.KeyControl(0x0f);
            }
        }

        protected byte prescale;

        protected fmgen.Chip chip;
        public PSG psg;

    }

    //	OPN2 Base ------------------------------------------------------
    public static class OPNABase extends OPNBase
    {
        public int[] visRtmVolume = new int[] { 0, 0 };
        public int[] visAPCMVolume = new int[] { 0, 0 };

        public OPNABase()
        {
            amtable[0] = -1;
            tablehasmade = false;

            adpcmbuf = null;
            memaddr = 0;
            startaddr = 0;
            deltan = 256;

            adpcmvol = 0;
            control2 = 0;

            MakeTable2();
            BuildLFOTable();
            for (int i = 0; i < 6; i++)
            {
                ch[i] = new fmgen.Channel4();
                ch[i].SetChip(chip);
                ch[i].SetType(fmgen.OpType.typeN);
            }
        }

        public int ReadStatus()
        {
            return status & 0x03;
        }

        // ---------------------------------------------------------------------------
        //	拡張ステータスを読みこむ
        //
        public int ReadStatusEx()
        {
            int a = status | 8;
            int b = a & stmask;
            int c = (int)(adpcmplay ? 0x20 : 0);

            int r = b | c;
            status |= statusnext;
            statusnext = 0;
            return r;
        }

        // ---------------------------------------------------------------------------
        //	チャンネルマスクの設定
        //
        public void SetChannelMask(int mask)
        {
            for (int i = 0; i < 6; i++)
                ch[i].Mute(!((mask & (1 << i)) == 0));
            psg.SetChannelMask((int)(mask >> 6));
            adpcmmask_ = (mask & (1 << 9)) != 0;
            rhythmmask_ = (int)((mask >> 10) & ((1 << 6) - 1));
        }

        private void Intr(boolean f)
        {
        }

        // ---------------------------------------------------------------------------
        //	テーブル作成
        //
        private void MakeTable2()
        {
            if (!tablehasmade)
            {
                for (int i = -fmgen.FM_TLPOS; i < fmgen.FM_TLENTS; i++)
                {
                    tltable[i + fmgen.FM_TLPOS] = (int)((int)(65536.0 * Math.pow(2.0, i * -16.0 / fmgen.FM_TLENTS))) - 1;
                }

                tablehasmade = true;
            }
        }

        // ---------------------------------------------------------------------------
        //	初期化
        //
        protected boolean Init(int c, int r, boolean f)
        {
            RebuildTimeTable();

            Reset();

            SetVolumeFM(0);
            SetVolumePSG(0);
            SetChannelMask(0);
            return true;
        }

        // ---------------------------------------------------------------------------
        //	サンプリングレート変更
        //
        protected boolean SetRate(int c, int r, boolean f)
        {
            c /= 2;     // 従来版との互換性を重視したけりゃコメントアウトしよう

            super.Init(c, r);

            adplbase = (int)((int)(8192.0 * (clock / 72.0) / r));
            adpld = (int)(deltan * adplbase >> 16);

            RebuildTimeTable();

            lfodcount = (reg22 & 0x08) != 0 ? lfotable[reg22 & 7] : 0;
            return true;
        }

        // ---------------------------------------------------------------------------
        //	リセット
        //
        public void Reset()
        {
            int i;

            super.Reset();
            for (i = 0x20; i < 0x28; i++) SetReg(i, 0);
            for (i = 0x30; i < 0xc0; i++) SetReg(i, 0);
            for (i = 0x130; i < 0x1c0; i++) SetReg(i, 0);
            for (i = 0x100; i < 0x110; i++) SetReg(i, 0);
            for (i = 0x10; i < 0x20; i++) SetReg(i, 0);
            for (i = 0; i < 6; i++)
            {
                pan[i] = 3;
                ch[i].Reset();
            }

            stmask = 0x73;// ~0x1c;
            statusnext = 0;
            memaddr = 0;
            adpcmd = 127;
            adpcmx = 0;
            lfocount = 0;
            adpcmplay = false;
            adplc = 0;
            adpld = 0x100;
            status = 0;
            UpdateStatus();
        }

        // ---------------------------------------------------------------------------
        //	レジスタアレイにデータを設定
        //
        protected void SetReg(int addr, int data)
        {
            int c = (int)(addr & 3);
            int modified;

            switch (addr)
            {

                // Timer -----------------------------------------------------------------
                case 0x24:
                case 0x25:
                    SetTimerA(addr, data);
                    break;

                case 0x26:
                    SetTimerB(data);
                    break;

                case 0x27:
                    SetTimerControl(data);
                    break;

                // Misc ------------------------------------------------------------------
                case 0x28:      // Key On/Off
                    if ((data & 3) < 3)
                    {
                        c = (int)((data & 3) + ((data & 4) != 0 ? 3 : 0));
                        ch[c].KeyControl(data >> 4);
                    }
                    break;

                // Status Mask -----------------------------------------------------------
                case 0x29:
                    reg29 = data;
                    //		UpdateStatus(); //?
                    break;

                // Prescaler -------------------------------------------------------------
                case 0x2d:
                case 0x2e:
                case 0x2f:
                    SetPrescaler(addr - 0x2d);
                    break;

                // F-Number --------------------------------------------------------------
                case 0x1a0:
                case 0x1a1:
                case 0x1a2:
                    c += 3;
                    fnum[c] = (int)(data + fnum2[c] * 0x100);
                    ch[c].SetFNum(fnum[c]);
                    break;
                case 0xa0:
                case 0xa1:
                case 0xa2:
                    fnum[c] = (int)(data + fnum2[c] * 0x100);
                    ch[c].SetFNum(fnum[c]);
                    break;

                case 0x1a4:
                case 0x1a5:
                case 0x1a6:
                    c += 3;
                    fnum2[c] = (byte)(data);
                    break;
                case 0xa4:
                case 0xa5:
                case 0xa6:
                    fnum2[c] = (byte)(data);
                    break;

                case 0xa8:
                case 0xa9:
                case 0xaa:
                    fnum3[c] = (int)(data + fnum2[c + 6] * 0x100);
                    break;

                case 0xac:
                case 0xad:
                case 0xae:
                    fnum2[c + 6] = (byte)(data);
                    break;

                // Algorithm -------------------------------------------------------------

                case 0x1b0:
                case 0x1b1:
                case 0x1b2:
                    c += 3;
                    ch[c].SetFB((data >> 3) & 7);
                    ch[c].SetAlgorithm(data & 7);
                    break;
                case 0xb0:
                case 0xb1:
                case 0xb2:
                    ch[c].SetFB((data >> 3) & 7);
                    ch[c].SetAlgorithm(data & 7);
                    break;

                case 0x1b4:
                case 0x1b5:
                case 0x1b6:
                    c += 3;
                    pan[c] = (byte)((data >> 6) & 3);
                    ch[c].SetMS(data);
                    break;
                case 0xb4:
                case 0xb5:
                case 0xb6:
                    pan[c] = (byte)((data >> 6) & 3);
                    ch[c].SetMS(data);
                    break;

                // LFO -------------------------------------------------------------------
                case 0x22:
                    modified = reg22 ^ data;
                    reg22 = (byte)data;
                    if ((modified & 0x8) != 0)
                        lfocount = 0;
                    lfodcount = (reg22 & 8) != 0 ? lfotable[reg22 & 7] : 0;
                    break;

                // PSG -------------------------------------------------------------------
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                case 10:
                case 11:
                case 12:
                case 13:
                case 14:
                case 15:
                    psg.SetReg(addr, (byte)data);
                    break;

                // 音色 ------------------------------------------------------------------
                default:
                    if (c < 3)
                    {
                        if ((addr & 0x100) != 0)
                            c += 3;
                        super.SetParameter(ch[c], addr, data);
                    }
                    break;
            }
        }

        // ---------------------------------------------------------------------------
        //	ADPCM B
        //
        protected void SetADPCMBReg(int addr, int data)
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
                    startaddr = (int)((adpcmreg[1] * 256 + adpcmreg[0]) << 6);
                    if ((control1 & 0x40) != 0)
                    {
                        memaddr = startaddr;
                    }
                    //		LOG1("  startaddr %.6x", startaddr);
                    break;

                case 0x04:      // Stop Address L
                case 0x05:      // Stop Address H
                    adpcmreg[addr - 0x04 + 2] = (byte)data;
                    stopaddr = (int)((adpcmreg[3] * 256 + adpcmreg[2] + 1) << 6);
                    //		LOG1("  stopaddr %.6x", stopaddr);
                    break;

                case 0x08:      // ADPCM data
                    if ((control1 & 0x60) == 0x60)
                    {
                        //			LOG2("  Wr [0x%.5x] = %.2x", memaddr, data);
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
                    limitaddr = (int)((adpcmreg[7] * 256 + adpcmreg[6] + 1) << 6);
                    //		LOG1("  limitaddr %.6x", limitaddr);
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
                        //			UpdateStatus();					//???
                    }
                    break;
            }
        }

        // ---------------------------------------------------------------------------
        //	レジスタ取得
        //
        protected int GetReg(int addr)
        {
            if (addr < 0x10)
                return psg.GetReg(addr);

            if (addr == 0x108)
            {
                //		LOG1("%d:reg[108] .   ", Diag::GetCPUTick());

                int data = adpcmreadbuf & 0xff;
                adpcmreadbuf >>= 8;
                if ((control1 & 0x60) == 0x20)
                {
                    adpcmreadbuf |= ReadRAM() << 8;
                    //			LOG2("Rd [0x%.6x:%.2x] ", memaddr, adpcmreadbuf >> 8);
                }
                //		LOG0("%.2x\n");
                return data;
            }

            if (addr == 0xff)
                return 1;

            return 0;
        }

        // ---------------------------------------------------------------------------
        //	合成
        //	in:		buffer		合成先
        //			nsamples	合成サンプル数
        //
        protected void FMMix(int[] buffer, int nsamples)
        {
            if (fmvolume > 0)
            {
                // 準備
                // Set F-Number
                if ((regtc & 0xc0) == 0)
                    csmch.SetFNum(fnum[2]);// csmch - ch]);
                else
                {
                    // 効果音モード
                    csmch.op[0].SetFNum(fnum3[1]);
                    csmch.op[1].SetFNum(fnum3[2]);
                    csmch.op[2].SetFNum(fnum3[0]);
                    csmch.op[3].SetFNum(fnum[2]);
                }

                int act = (((ch[2].Prepare() << 2) | ch[1].Prepare()) << 2) | ch[0].Prepare();
                if ((reg29 & 0x80)!=0)
                    act |= (ch[3].Prepare() | ((ch[4].Prepare() | (ch[5].Prepare() << 2)) << 2)) << 6;
                if ((reg22 & 0x08)==0)
                    act &= 0x555;

                if ((act & 0x555)!=0)
                {
                    Mix6(buffer, nsamples, act);
                }
            }
        }

        protected void Mix6(int[] buffer, int nsamples, int activech)
        {
            // Mix
            int[] ibuf=new int[6];
            int[] idest=new int[6];
            idest[0] = pan[0];
            idest[1] = pan[1];
            idest[2] = pan[2];
            idest[3] = pan[3];
            idest[4] = pan[4];
            idest[5] = pan[5];

            int limit = nsamples * 2;
            for (int dest = 0; dest < limit; dest += 2)
            {
                ibuf[1] = ibuf[2] = ibuf[3] = 0;
                if ((activech & 0xaaa) != 0)
                {
                    LFO();
                    MixSubSL(activech, idest, ibuf);
                }
                else
                {
                    MixSubS(activech, idest, ibuf);
                }

                int v = ((fmgen.Limit(ibuf[2] + ibuf[3], 0x7fff, -0x8000) * fmvolume) >> 14);
                fmgen.StoreSample(buffer[dest + 0], v);// ((fmgen.Limit(ibuf[2] + ibuf[3], 0x7fff, -0x8000) * fmvolume) >> 14));
                visVolume[0] = v;

                v = ((fmgen.Limit(ibuf[1] + ibuf[3], 0x7fff, -0x8000) * fmvolume) >> 14);
                fmgen.StoreSample(buffer[dest + 1], v);// ((fmgen.Limit(ibuf[1] + ibuf[3], 0x7fff, -0x8000) * fmvolume) >> 14));
                visVolume[1] = v;
            }
        }

        protected void MixSubS(int activech, int[] dest, int[] buf)
        {
            if ((activech & 0x001) != 0) buf[dest[0]] = ch[0].Calc();
            if ((activech & 0x004) != 0) buf[dest[1]] += ch[1].Calc();
            if ((activech & 0x010) != 0) buf[dest[2]] += ch[2].Calc();
            if ((activech & 0x040) != 0) buf[dest[3]] += ch[3].Calc();
            if ((activech & 0x100) != 0) buf[dest[4]] += ch[4].Calc();
            if ((activech & 0x400) != 0) buf[dest[5]] += ch[5].Calc();
        }

        protected void MixSubSL(int activech, int[] dest,int[] buf)
        {
            if ((activech & 0x001) != 0) buf[dest[0]] = ch[0].CalcL();
            if ((activech & 0x004) != 0) buf[dest[1]] += ch[1].CalcL();
            if ((activech & 0x010) != 0) buf[dest[2]] += ch[2].CalcL();
            if ((activech & 0x040) != 0) buf[dest[3]] += ch[3].CalcL();
            if ((activech & 0x100) != 0) buf[dest[4]] += ch[4].CalcL();
            if ((activech & 0x400) != 0) buf[dest[5]] += ch[5].CalcL();
        }

        // ---------------------------------------------------------------------------
        //	ステータスフラグ設定
        //
        protected void SetStatus(int bits)
        {
            if ((status & bits)==0)
            {
                //		LOG2("SetStatus(%.2x %.2x)\n", bits, stmask);
                status |= bits & stmask;
                UpdateStatus();
            }
            //	else
            //		LOG1("SetStatus(%.2x) - ignored\n", bits);
        }

        protected void ResetStatus(int bits)
        {
            status &= ~bits;
            //	LOG1("ResetStatus(%.2x)\n", bits);
            UpdateStatus();
        }

        protected void UpdateStatus()
        {
            //	LOG2("%d:INT = %d\n", Diag::GetCPUTick(), (status & stmask & reg29) != 0);
            Intr((status & stmask & reg29) != 0);
        }

        protected void LFO()
        {
            //	LOG3("%4d - %8d, %8d\n", c, lfocount, lfodcount);

            //	Operator::SetPML(pmtable[(lfocount >> (FM_LFOCBITS+1)) & 0xff]);
            //	Operator::SetAML(amtable[(lfocount >> (FM_LFOCBITS+1)) & 0xff]);
            chip.SetPML((int)(pmtable[(lfocount >> (fmgen.FM_LFOCBITS + 1)) & 0xff]));
            chip.SetAML((int)(amtable[(lfocount >> (fmgen.FM_LFOCBITS + 1)) & 0xff]));
            lfocount += lfodcount;
        }

        protected static void BuildLFOTable()
        {
            if (amtable[0] == -1)
            {
                for (int c = 0; c < 256; c++)
                {
                    int v;
                    if (c < 0x40) v = c * 2 + 0x80;
                    else if (c < 0xc0) v = 0x7f - (c - 0x40) * 2 + 0x80;
                    else v = (c - 0xc0) * 2;
                    pmtable[c] = c;

                    if (c < 0x80) v = 0xff - c * 2;
                    else v = (c - 0x80) * 2;
                    amtable[c] = v & ~3;
                }
            }
        }

        // ---------------------------------------------------------------------------
        //	ADPCM 展開
        //
        protected void DecodeADPCMB()
        {
            apout0 = apout1;
            int n = (ReadRAMN() * adpcmvolume) >> 13;
            apout1 = adpcmout + n;
            adpcmout = n;
        }

        // ---------------------------------------------------------------------------
        //	ADPCM 合成
        //	
        protected void ADPCMBMix(int[] dest, int count)
        {
            int maskl = (int)((control2 & 0x80)!=0 ? -1 : 0);
            int maskr = (int)((control2 & 0x40)!=0 ? -1 : 0);
            if (adpcmmask_)
            {
                maskl = maskr = 0;
            }

            if (adpcmplay)
            {
                int ptrDest = 0;
                //		LOG2("ADPCM Play: %d   DeltaN: %d\n", adpld, deltan);
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
                        fmgen.StoreSample(dest[ptrDest+0], (int)(s & maskl));
                        fmgen.StoreSample(dest[ptrDest + 1], (int)(s & maskr));
                        visAPCMVolume[0] = (int)(s & maskl);
                        visAPCMVolume[1] = (int)(s & maskr);
                        ptrDest += 2;
                        adplc -= adpld;
                    }
                    for (; count > 0 && apout0!=0; count--)
                    {
                        if (adplc < 0)
                        {
                            apout0 = apout1;
                            apout1 = 0;
                            adplc += 8192;
                        }
                        int s = (adplc * apout1) >> 13;
                        fmgen.StoreSample(dest[ptrDest + 0], (int)(s & maskl));
                        fmgen.StoreSample(dest[ptrDest + 1], (int)(s & maskr));
                        visAPCMVolume[0] = (int)(s & maskl);
                        visAPCMVolume[1] = (int)(s & maskr);
                        ptrDest += 2;
                        adplc -= adpld;
                    }
                }
                else    // fplay > fsamp	(adpld = fplay/famp*8192)
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
                        fmgen.StoreSample(dest[ptrDest + 0], (int)(s & maskl));
                        fmgen.StoreSample(dest[ptrDest + 1], (int)(s & maskr));
                        visAPCMVolume[0] = (int)(s & maskl);
                        visAPCMVolume[1] = (int)(s & maskr);
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

        // ---------------------------------------------------------------------------
        //	ADPCM RAM への書込み操作
        //
        protected void WriteRAM(int data)
        {
            if (NO_BITTYPE_EMULATION) {
                if ((control2 & 2)==0)
                {
                    // 1 bit mode
                    adpcmbuf[(memaddr >> 4) & 0x3ffff] = (byte)data;
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
                    adpcmbuf[p+0x08000] = (byte)((adpcmbuf[p+0x08000] & ~mask) | ((byte)(data) & mask));
                    data >>= 1;
                    adpcmbuf[p+0x10000] = (byte)((adpcmbuf[p+0x10000] & ~mask) | ((byte)(data) & mask));
                    data >>= 1;
                    adpcmbuf[p+0x18000] = (byte)((adpcmbuf[p+0x18000] & ~mask) | ((byte)(data) & mask));
                    data >>= 1;
                    adpcmbuf[p+0x20000] = (byte)((adpcmbuf[p+0x20000] & ~mask) | ((byte)(data) & mask));
                    data >>= 1;
                    adpcmbuf[p+0x28000] = (byte)((adpcmbuf[p+0x28000] & ~mask) | ((byte)(data) & mask));
                    data >>= 1;
                    adpcmbuf[p+0x30000] = (byte)((adpcmbuf[p+0x30000] & ~mask) | ((byte)(data) & mask));
                    data >>= 1;
                    adpcmbuf[p+0x38000] = (byte)((adpcmbuf[p+0x38000] & ~mask) | ((byte)(data) & mask));
                    memaddr += 2;
                }
            } else {
                adpcmbuf[(memaddr >> granuality) & 0x3ffff] = (byte)data;
                memaddr += (int)(1 << granuality);
            }

            if (memaddr == stopaddr)
            {
                SetStatus(4);
                statusnext = 0x04;  // EOS
                memaddr &= 0x3fffff;
            }
            if (memaddr == limitaddr)
            {
                //		LOG1("Limit ! (%.8x)\n", limitaddr);
                memaddr = 0;
            }
            SetStatus(8);
        }

        // ---------------------------------------------------------------------------
        //	ADPCM RAM からの読み込み操作
        //
        protected int ReadRAM()
        {
            int data;
            if (NO_BITTYPE_EMULATION) {
                if ((control2 & 2)==0)
                {
                    // 1 bit mode
                    data = adpcmbuf[(memaddr >> 4) & 0x3ffff];
                    memaddr += 16;
                }
                else
                {
                    // 8 bit mode
                    //(int)8* p = &adpcmbuf[(memaddr >> 4) & 0x7fff];
                    int p = (int)((memaddr >> 4) & 0x7fff);
                    int bank = (memaddr >> 1) & 7;
                    byte mask = (byte)(1 << (int)bank);

                    data = (int)(adpcmbuf[p+0x38000] & mask);
                    data = (int)(data * 2 + (adpcmbuf[p + 0x30000] & mask));
                    data = (int)(data * 2 + (adpcmbuf[p + 0x28000] & mask));
                    data = (int)(data * 2 + (adpcmbuf[p + 0x20000] & mask));
                    data = (int)(data * 2 + (adpcmbuf[p + 0x18000] & mask));
                    data = (int)(data * 2 + (adpcmbuf[p + 0x10000] & mask));
                    data = (int)(data * 2 + (adpcmbuf[p + 0x08000] & mask));
                    data = (int)(data * 2 + (adpcmbuf[p + 0x00000] & mask));
                    data >>= (int)bank;
                    memaddr += 2;
                }
            } else {
                data = adpcmbuf[(memaddr >> granuality) & 0x3ffff];
                memaddr += (int)(1 << granuality);
            }

            if (memaddr == stopaddr)
            {
                SetStatus(4);
                statusnext = 0x04;  // EOS
                memaddr &= 0x3fffff;
            }
            if (memaddr == limitaddr)
            {
                //		LOG1("Limit ! (%.8x)\n", limitaddr);
                memaddr = 0;
            }
            if (memaddr < stopaddr)
                SetStatus(8);
            return data;
        }

        // ---------------------------------------------------------------------------
        //	ADPCM RAM からの nibble 読み込み及び ADPCM 展開
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
                        data = adpcmbuf[(memaddr >> 4) & 0x3ffff];
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

        protected int DecodeADPCMBSample(int data)
        {
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

            adpcmx = fmgen.Limit(adpcmx + table1[data] * adpcmd / 8, 32767, -32768);
            adpcmd = fmgen.Limit(adpcmd * table2[data] / 64, 24576, 127);
            return adpcmx;
        }


        public boolean NO_BITTYPE_EMULATION = false;

        // FM 音源関係
        protected byte[] pan = new byte[6];
        protected byte[] fnum2 = new byte[9];

        protected byte reg22;
        protected int reg29;     // OPNA only?

        protected int stmask;
        protected int statusnext;

        protected int lfocount;
        protected int lfodcount;

        protected int[] fnum = new int[6];
        protected int[] fnum3 = new int[3];

        // ADPCM 関係
        protected byte[] adpcmbuf;        // ADPCM RAM
        protected int adpcmmask;     // メモリアドレスに対するビットマスク
        protected int adpcmnotice;   // ADPCM 再生終了時にたつビット
        protected int startaddr;     // Start address
        protected int stopaddr;      // Stop address
        protected int memaddr;       // 再生中アドレス
        protected int limitaddr;     // Limit address/mask
        protected int adpcmlevel;     // ADPCM 音量
        protected int adpcmvolume;
        protected int adpcmvol;
        protected int deltan;            // ⊿N
        protected int adplc;          // 周波数変換用変数
        protected int adpld;          // 周波数変換用変数差分値
        protected int adplbase;      // adpld の元
        protected int adpcmx;         // ADPCM 合成用 x
        protected int adpcmd;         // ADPCM 合成用 ⊿
        protected int adpcmout;       // ADPCM 合成後の出力
        protected int apout0;         // out(t-2)+out(t-1)
        protected int apout1;         // out(t-1)+out(t)

        protected int adpcmreadbuf;  // ADPCM リード用バッファ
        protected boolean adpcmplay;     // ADPCM 再生中
        protected byte granuality;
        protected boolean adpcmmask_;

        protected byte control1;     // ADPCM コントロールレジスタ１
        protected byte control2;     // ADPCM コントロールレジスタ２
        protected byte[] adpcmreg = new byte[8];  // ADPCM レジスタの一部分

        protected int rhythmmask_;

        protected fmgen.Channel4[] ch = new fmgen.Channel4[6];

        public static int[] amtable = new int[fmgen.FM_LFOENTS];
        public static int[] pmtable = new int[fmgen.FM_LFOENTS];
        public static int[] tltable = new int[fmgen.FM_TLENTS + fmgen.FM_TLPOS];
        protected static boolean tablehasmade;
    };

    //	YM2203(OPN) ----------------------------------------------------
    public static class OPN extends OPNBase
    {

        public OPN()
        {
            SetVolumeFM(0);
            SetVolumePSG(0);

            csmch = ch[2];

            for (int i = 0; i < 3; i++)
            {
                ch[i].SetChip(chip);
                ch[i].SetType(fmgen.OpType.typeN);
            }
        }

        //	初期化
        public boolean Init(int c, int r, boolean ip /*= false*/, String s/* = ""*/)
        {
            if (!SetRate(c, r, ip))
                return false;

            Reset();

            SetVolumeFM(0);
            SetVolumePSG(0);
            SetChannelMask(0);
            return true;
        }

        //	サンプリングレート変更
        public boolean SetRate(int c, int r, boolean f/* = false*/)
        {
            super.Init(c, r);
            RebuildTimeTable();
            return true;
        }

        //	リセット
        public void Reset()
        {
            int i;
            for (i = 0x20; i < 0x28; i++) SetReg(i, 0);
            for (i = 0x30; i < 0xc0; i++) SetReg(i, 0);
            super.Reset();
            ch[0].Reset();
            ch[1].Reset();
            ch[2].Reset();
        }

        //	合成(2ch)
        public void Mix(int[] buffer, int nsamples)
        {

            psg.Mix(buffer, nsamples);

            // Set F-Number
            ch[0].SetFNum(fnum[0]);
            ch[1].SetFNum(fnum[1]);
            if ((regtc & 0xc0)==0)
                ch[2].SetFNum(fnum[2]);
            else
            {   // 効果音
                ch[2].op[0].SetFNum(fnum3[1]);
                ch[2].op[1].SetFNum(fnum3[2]);
                ch[2].op[2].SetFNum(fnum3[0]);
                ch[2].op[3].SetFNum(fnum[2]);
            }

            int actch = (((ch[2].Prepare() << 2) | ch[1].Prepare()) << 2) | ch[0].Prepare();
            if ((actch & 0x15)!=0)
            {
                //Sample* limit = buffer + nsamples * 2;
                int limit = nsamples * 2;
                //for (Sample* dest = buffer; dest < limit; dest += 2)
                for (int dest = 0; dest < limit; dest += 2)
                {
                    int s = 0;
                    if ((actch & 0x01)!=0) s = ch[0].Calc();
                    if ((actch & 0x04)!=0) s += ch[1].Calc();
                    if ((actch & 0x10)!=0) s += ch[2].Calc();
                    s = ((fmgen.Limit(s, 0x7fff, -0x8000) * fmvolume) >> 14);
                    fmgen.StoreSample(buffer[dest + 0], s);
                    fmgen.StoreSample(buffer[dest + 1], s);

                    visVolume[0] = s;
                    visVolume[1] = s;

                }
            }
        }

        //	レジスタアレイにデータを設定
        public void SetReg(int addr, int data)
        {
            //	LOG2("reg[%.2x] <- %.2x\n", addr, data);
            if (addr >= 0x100)
                return;

            int c = (int)(addr & 3);
            switch (addr)
            {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                case 10:
                case 11:
                case 12:
                case 13:
                case 14:
                case 15:
                    psg.SetReg(addr, (byte)data);
                    break;

                case 0x24:
                case 0x25:
                    SetTimerA(addr, data);
                    break;

                case 0x26:
                    SetTimerB(data);
                    break;

                case 0x27:
                    SetTimerControl(data);
                    break;

                case 0x28:      // Key On/Off
                    if ((data & 3) < 3)
                        ch[data & 3].KeyControl(data >> 4);
                    break;

                case 0x2d:
                case 0x2e:
                case 0x2f:
                    SetPrescaler(addr - 0x2d);
                    break;

                // F-Number
                case 0xa0:
                case 0xa1:
                case 0xa2:
                    fnum[c] = (int)(data + fnum2[c] * 0x100);
                    break;

                case 0xa4:
                case 0xa5:
                case 0xa6:
                    fnum2[c] = (byte)(data);
                    break;

                case 0xa8:
                case 0xa9:
                case 0xaa:
                    fnum3[c] = (int)(data + fnum2[c + 3] * 0x100);
                    break;

                case 0xac:
                case 0xad:
                case 0xae:
                    fnum2[c + 3] = (byte)(data);
                    break;

                case 0xb0:
                case 0xb1:
                case 0xb2:
                    ch[c].SetFB((data >> 3) & 7);
                    ch[c].SetAlgorithm(data & 7);
                    break;

                default:
                    if (c < 3)
                    {
                        if ((addr & 0xf0) == 0x60)
                            data &= 0x1f;
                        super.SetParameter(ch[c], addr, data);
                    }
                    break;
            }
        }

        //	レジスタ読み込み
        public int GetReg(int addr)
        {
            if (addr < 0x10)
                return psg.GetReg(addr);
            else
                return 0;
        }

        public int ReadStatus()
        {
            return status & 0x03;
        }

        public int ReadStatusEx()
        {
            return 0xff;
        }

        //	マスク設定
        public void SetChannelMask(int mask)
        {
            for (int i = 0; i < 3; i++)
                ch[i].Mute(!!((mask & (1 << i)) != 0));
            psg.SetChannelMask((int)(mask >> 6));
        }

        public int dbgGetOpOut(int c, int s)
        {
            return ch[c].op[s].dbgopout_;
        }

        public int dbgGetPGOut(int c, int s)
        {
            return ch[c].op[s].dbgpgout_;
        }

        public fmgen.Channel4 dbgGetCh(int c)
        {
            return ch[c];
        }

        private void Intr(boolean f)
        {
        }

        //	ステータスフラグ設定
        protected void SetStatus(int bits)
        {
            if ((status & bits)==0)
            {
                status |= bits;
                Intr(true);
            }
        }

        protected void ResetStatus(int bit)
        {
            status &= ~bit;
            if (status==0)
                Intr(false);
        }

        private int[] fnum = new int[3];
        private int[] fnum3 = new int[3];
        private byte[] fnum2 = new byte[6];

        private fmgen.Channel4[] ch = new fmgen.Channel4[] { new fmgen.Channel4(), new fmgen.Channel4(), new fmgen.Channel4() };

    };

    //	YM2608(OPNA) ---------------------------------------------------
    public static class OPNA extends OPNABase
    {
        // ---------------------------------------------------------------------------
        //	構築
        //
        public OPNA(byte ChipID)
        {
            for (int i = 0; i < 6; i++)
            {
                rhythm[i].sample = null;
                rhythm[i].pos = 0;
                rhythm[i].size = 0;
                rhythm[i].volume = 0;
            }
            rhythmtvol = 0;
            adpcmmask = 0x3ffff;
            adpcmnotice = 4;
            csmch = ch[2];
            this.chipID = ChipID;
        }

        protected void finalize()
        {
            adpcmbuf = null;
            for (int i = 0; i < 6; i++) {
                rhythm[i].sample=null;
            }
        }

        public boolean Init(int c, int r)
        {
            return Init(c, r, false, "");
        }

        public boolean Init(int c, int r, boolean ipflag, String path)
        {
            return Init(c, r, ipflag, fname -> CreateRhythmFileStream(path, fname));
        }

        public boolean Init(int c, int r, boolean ipflag, Function<String, Stream> appendFileReaderCallback/* = null*/)
        {
            rate = 8000;
            LoadRhythmSample(appendFileReaderCallback);

            if (adpcmbuf == null)
                adpcmbuf = new byte[0x40000];
            if (adpcmbuf == null)
                return false;

            if (!SetRate(c, r, ipflag))
                return false;
            if (!super.Init(c, r, ipflag))
                return false;

            Reset();

            SetVolumeADPCM(0);
            SetVolumeRhythmTotal(0);
            for (int i = 0; i < 6; i++)
                SetVolumeRhythm(0, 0);
            return true;
        }

        public static class whdr
        {
            public int chunksize;
            public int tag;
            public int nch;
            public int rate;
            public int avgbytes;
            public int align;
            public int bps;
            public int size;
        }

        private FileStream CreateRhythmFileStream(String dir, String fname)
        {
            Path path = dir != null || !dir.isEmpty() ? Paths.get(fname) : Paths.get(dir, fname);
            return Files.exists(path) ? new FileStream(path.toString(), FileMode.Open, FileAccess.Read) : null;
        }

        public boolean LoadRhythmSample(String path)
        {
            return LoadRhythmSample(fname -> CreateRhythmFileStream(path, fname));
        }

        // ---------------------------------------------------------------------------
        //	リズム音を読みこむ
        //
        public boolean LoadRhythmSample(Function<String, Stream> appendFileReaderCallback)
        {
            String[] rhythmname = new String[]
            {
                "BD", "SD", "TOP", "HH", "TOM", "RIM",
            };

            int i;
            for (i = 0; i < 6; i++)
                rhythm[i].pos = ~(int)0;

            for (i = 0; i < 6; i++)
            {
                try
                {
                    int fsize;
                    boolean f = true;
                    String buf1 = String.format("2608_{0}_{1}.WAV", rhythmname[i], chipID);
                    String buf2 = String.format("2608_{0}.WAV", rhythmname[i]);
                    String rymBuf1 = String.format("2608_RYM_{0}.WAV", chipID);
                    String rymBuf2 = "2608_RYM.WAV";
                    byte[] file;

                    try (Stream st = appendFileReaderCallback.apply(buf1))
                    {
                        file = common.ReadAllBytes(st);
                    }
                    if (file == null)
                    {
                        try (Stream st = appendFileReaderCallback.apply(buf2))
                        {
                            file = common.ReadAllBytes(st);
                        }
                    }

                    if (file == null)
                    {
                        f = false;
                        if (i == 5)
                        {
                            try (Stream st = appendFileReaderCallback.apply(rymBuf1))
                            {
                                file = common.ReadAllBytes(st);
                            }
                            if (file == null)
                            {
                                try (Stream st = appendFileReaderCallback.apply(rymBuf2))
                                {
                                    file = common.ReadAllBytes(st);
                                }
                            }
                            if (file != null)
                            {
                                f = true;
                            }
                        }
                    }

                    if (!f)
                    {
                        continue;
                    }

                    whdr whdr = new whdr();

                    int fInd = 0x10;
                    byte[] bufWhdr = new byte[4 + 2 + 2 + 4 + 4 + 2 + 2 + 2];
                    for (int j = 0; j < 4 + 2 + 2 + 4 + 4 + 2 + 2 + 2; j++) bufWhdr[j] = file[fInd++];

                    whdr.chunksize = (int)(bufWhdr[0] + bufWhdr[1] * 0x100 + bufWhdr[2] * 0x10000 + bufWhdr[3] * 0x10000);
                    whdr.tag = (int)(bufWhdr[4] + bufWhdr[5] * 0x100);
                    whdr.nch = (int)(bufWhdr[6] + bufWhdr[7] * 0x100);
                    whdr.rate = (int)(bufWhdr[8] + bufWhdr[9] * 0x100 + bufWhdr[10] * 0x10000 + bufWhdr[11] * 0x10000);
                    whdr.avgbytes = (int)(bufWhdr[12] + bufWhdr[13] * 0x100 + bufWhdr[14] * 0x10000 + bufWhdr[15] * 0x10000);
                    whdr.align = (int)(bufWhdr[16] + bufWhdr[17] * 0x100);
                    whdr.bps = (int)(bufWhdr[18] + bufWhdr[19] * 0x100);
                    whdr.size = (int)(bufWhdr[20] + bufWhdr[21] * 0x100);

                    byte[] subchunkname = new byte[4];
                    fsize = 4 + whdr.chunksize - (4 + 2 + 2 + 4 + 4 + 2 + 2 + 2);
                    do
                    {
                        fInd += fsize;
                        for (int j = 0; j < 4; j++) subchunkname[j] = file[fInd++];
                        for (int j = 0; j < 4; j++) bufWhdr[j] = file[fInd++];

                        fsize = (int)(bufWhdr[0] + bufWhdr[1] * 0x100 + bufWhdr[2] * 0x10000 + bufWhdr[3] * 0x10000);
                    } while ('d' != subchunkname[0] || 'a' != subchunkname[1] || 't' != subchunkname[2] || 'a' != subchunkname[3]);

                    fsize /= 2;
                    if (fsize >= 0x100000 || whdr.tag != 1 || whdr.nch != 1)
                        break;
                    fsize = (int)Math.max(fsize, (1 << 31) / 1024);

                    rhythm[i].sample = null;
                    rhythm[i].sample = new int[fsize];
                    if (rhythm[i].sample == null)
                        break;
                    byte[] bufSample = new byte[fsize * 2];
                    for (int j = 0; j < fsize * 2; j++) bufSample[j] = file[fInd++];
                    for (int si = 0; si < fsize; si++)
                    {
                        rhythm[i].sample[si] = (short)(bufSample[si * 2] + bufSample[si * 2 + 1] * 0x100);
                    }

                    rhythm[i].rate = whdr.rate;
                    rhythm[i].step = rhythm[i].rate * 1024 / rate;
                    rhythm[i].pos = rhythm[i].size = fsize * 1024;
                }
                catch (Exception e)
                {
                    //無視
                }
            }
            if (i != 6)
            {
                for (i = 0; i < 6; i++)
                {
                    rhythm[i].sample = null;
                }
                return false;
            }
            return true;
        }

        // ---------------------------------------------------------------------------
        //	サンプリングレート変更
        //
        public boolean SetRate(int c, int r, boolean ipflag/* = false*/)
        {
            if (!super.SetRate(c, r, ipflag))
                return false;

            for (int i = 0; i < 6; i++)
            {
                rhythm[i].step = rhythm[i].rate * 1024 / r;
            }
            return true;
        }

        // ---------------------------------------------------------------------------
        //	合成
        //	in:		buffer		合成先
        //			nsamples	合成サンプル数
        //
        public void Mix(int[] buffer, int nsamples)
        {
            FMMix(buffer, nsamples);
            psg.Mix(buffer, nsamples);
            ADPCMBMix(buffer, (int)nsamples);
            RhythmMix(buffer, (int)nsamples);
        }

        // ---------------------------------------------------------------------------
        //	リセット
        //
        public void Reset()
        {
            reg29 = 0x1f;
            rhythmkey = 0;
            limitaddr = 0x3ffff;
            super.Reset();
        }

        // ---------------------------------------------------------------------------
        //	レジスタアレイにデータを設定
        //
        public void SetReg(int addr, int data)
        {
            addr &= 0x1ff;

            switch (addr)
            {
                case 0x29:
                    reg29 = data;
                    //		UpdateStatus(); //?
                    break;

                // Rhythm ----------------------------------------------------------------
                case 0x10:          // DM/KEYON
                    if ((data & 0x80) == 0)  // KEY ON
                    {
                        rhythmkey |= (byte)(data & 0x3f);
                        if ((data & 0x01) != 0) rhythm[0].pos = 0;
                        if ((data & 0x02) != 0) rhythm[1].pos = 0;
                        if ((data & 0x04) != 0) rhythm[2].pos = 0;
                        if ((data & 0x08) != 0) rhythm[3].pos = 0;
                        if ((data & 0x10) != 0) rhythm[4].pos = 0;
                        if ((data & 0x20) != 0) rhythm[5].pos = 0;
                    }
                    else
                    {                   // DUMP
                        rhythmkey &= (byte)(~(byte)data);
                    }
                    break;

                case 0x11:
                    rhythmtl = (byte)(~data & 63);
                    break;

                case 0x18:      // Bass Drum
                case 0x19:      // Snare Drum
                case 0x1a:      // Top Cymbal
                case 0x1b:      // Hihat
                case 0x1c:      // Tom-tom
                case 0x1d:      // Rim shot
                    rhythm[addr & 7].pan = (byte)((data >> 6) & 3);
                    rhythm[addr & 7].level = (byte)(~data & 31);
                    break;

                case 0x100:
                case 0x101:
                case 0x102:
                case 0x103:
                case 0x104:
                case 0x105:
                case 0x108:
                case 0x109:
                case 0x10a:
                case 0x10b:
                case 0x10c:
                case 0x10d:
                case 0x110:
                    super.SetADPCMBReg(addr - 0x100, data);
                    break;

                default:
                    super.SetReg(addr, data);
                    break;
            }
        }

        public int GetReg(int addr)
        {
            return 0;
        }

        public void SetVolumeADPCM(int db)
        {
            db = Math.min(db, 20);
            if (db > -192)
                adpcmvol = (int)(65536.0 * Math.pow(10.0, db / 40.0));
            else
                adpcmvol = 0;

            adpcmvolume = (adpcmvol * adpcmlevel) >> 12;
        }

        // ---------------------------------------------------------------------------
        //	音量設定
        //
        public void SetVolumeRhythmTotal(int db)
        {
            db = Math.min(db, 20);
            rhythmtvol = -(db * 2 / 3);
        }

        public void SetVolumeRhythm(int index, int db)
        {
            db = Math.min(db, 20);
            rhythm[index].volume = -(db * 2 / 3);
        }


        public byte[] GetADPCMBuffer()
        {
            return adpcmbuf;
        }

        public int dbgGetOpOut(int c, int s)
        {
            return ch[c].op[s].dbgopout_;
        }

        public int dbgGetPGOut(int c, int s)
        {
            return ch[c].op[s].dbgpgout_;
        }

        public fmgen.Channel4 dbgGetCh(int c)
        {
            return ch[c];
        }


        public class Rhythm
        {
            public byte pan;      // ぱん
            public byte level;     // おんりょう
            public int volume;     // おんりょうせってい
            public int[] sample;      // さんぷる
            public int size;      // さいず
            public int pos;       // いち
            public int step;      // すてっぷち
            public int rate;      // さんぷるのれーと
        };

        // ---------------------------------------------------------------------------
        //	リズム合成
        //
        private void RhythmMix(int[] buffer, int count)
        {
            if (rhythmtvol < 128 && rhythm[0].sample != null && ((rhythmkey & 0x3f) != 0))
            {
                int limit = (int)count * 2;
                visRtmVolume[0] = 0;
                visRtmVolume[1] = 0;
                for (int i = 0; i < 6; i++)
                {
                    Rhythm r = rhythm[i];
                    if ((rhythmkey & (1 << i)) != 0 && (byte)r.level < 128)
                    {
                        int db = fmgen.Limit(rhythmtl + rhythmtvol + r.level + r.volume, 127, -31);
                        int vol = tltable[fmgen.FM_TLPOS + (db << (fmgen.FM_TLBITS - 7))] >> 4;
                        int maskl = -((r.pan >> 1) & 1);
                        int maskr = -(r.pan & 1);

                        if ((rhythmmask_ & (1 << i)) != 0)
                        {
                            maskl = maskr = 0;
                        }

                        for (int dest = 0; dest < limit && r.pos < r.size; dest += 2)
                        {
                            int sample = (r.sample[r.pos / 1024] * vol) >> 12;
                            r.pos += r.step;
                            fmgen.StoreSample(buffer[dest + 0], sample & maskl);
                            fmgen.StoreSample(buffer[dest + 1], sample & maskr);
                            visRtmVolume[0] += sample & maskl;
                            visRtmVolume[1] += sample & maskr;
                        }
                    }
                }
            }
        }

        // リズム音源関係
        private Rhythm[] rhythm = new Rhythm[] { new Rhythm(), new Rhythm(), new Rhythm(), new Rhythm(), new Rhythm(), new Rhythm() };

        private byte rhythmtl;      // リズム全体の音量
        private int rhythmtvol;
        private byte rhythmkey;     // リズムのキー
        private byte chipID;
    }
    //	YM2610/B(OPNB) -------------------------------------------------
    public static class OPNB extends OPNABase
    {
        // ---------------------------------------------------------------------------
        //	構築
        //
        public OPNB()
        {
            adpcmabuf = null;
            adpcmasize = 0;
            for (int i = 0; i < 6; i++)
            {
                adpcma[i].pan = 0;
                adpcma[i].level = 0;
                adpcma[i].volume = 0;
                adpcma[i].pos = 0;
                adpcma[i].step = 0;
                adpcma[i].volume = 0;
                adpcma[i].start = 0;
                adpcma[i].stop = 0;
                adpcma[i].adpcmx = 0;
                adpcma[i].adpcmd = 0;
            }
            adpcmatl = 0;
            adpcmakey = 0;
            adpcmatvol = 0;
            adpcmmask = 0;
            adpcmnotice = 0x8000;
            granuality = -1;
            csmch = ch[2];

            InitADPCMATable();
        }

        // ---------------------------------------------------------------------------
        //	初期化
        //
        public boolean Init(int c, int r, boolean ipflag/* = false*/,
                     byte[] _adpcma/* = null*/, int _adpcma_size/* = 0*/,
                     byte[] _adpcmb/* = null*/, int _adpcmb_size/* = 0*/)
        {
            int i;
            if (!SetRate(c, r, ipflag))
                return false;
            if (!super.Init(c, r, ipflag))
                return false;

            setAdpcmA(_adpcma, _adpcma_size);
            setAdpcmB(_adpcmb, _adpcmb_size);

            Reset();

            SetVolumeFM(0);
            SetVolumePSG(0);
            SetVolumeADPCMB(0);
            SetVolumeADPCMATotal(0);
            for (i = 0; i < 6; i++)
                SetVolumeADPCMA(0, 0);
            SetChannelMask(0);
            return true;
        }

        public void setAdpcmA(byte[] _adpcma, int _adpcma_size)
        {
            adpcmabuf = _adpcma;
            adpcmasize = _adpcma_size;
        }

        public void setAdpcmB(byte[] _adpcmb, int _adpcmb_size)
        {
            adpcmbuf = _adpcmb;

            for (int i = 0; i <= 24; i++)       // max 16M bytes
            {
                if (_adpcmb_size <= (1 << i))
                {
                    adpcmmask = (int)((1 << i) - 1);
                    break;
                }
            }

            //	adpcmmask = _adpcmb_size - 1;
            limitaddr = adpcmmask;

        }

        // ---------------------------------------------------------------------------
        //	サンプリングレート変更
        //
        public boolean SetRate(int c, int r, boolean ipflag/* = false*/)
        {
            if (!super.SetRate(c, r, ipflag))
                return false;

            adpcmastep = (int)((double)(c) / 54 * 8192 / r);
            return true;
        }

        // ---------------------------------------------------------------------------
        //	合成
        //	in:		buffer		合成先
        //			nsamples	合成サンプル数
        //
        public void Mix(int[] buffer, int nsamples)
        {
            FMMix(buffer, nsamples);
            psg.Mix(buffer, nsamples);
            ADPCMBMix(buffer, (int)nsamples);
            ADPCMAMix(buffer, (int)nsamples);
        }

        // ---------------------------------------------------------------------------
        //	リセット
        //
        public void Reset()
        {
            super.Reset();

            stmask = ~(int)0;
            adpcmakey = 0;
            reg29 = ~(int)0;

            for (int i = 0; i < 6; i++)
            {
                adpcma[i].pan = 0;
                adpcma[i].level = 0;
                adpcma[i].volume = 0;
                adpcma[i].pos = 0;
                adpcma[i].step = 0;
                adpcma[i].volume = 0;
                adpcma[i].start = 0;
                adpcma[i].stop = 0;
                adpcma[i].adpcmx = 0;
                adpcma[i].adpcmd = 0;
            }
        }

        // ---------------------------------------------------------------------------
        //	レジスタアレイにデータを設定
        //
        public void SetReg(int addr, int data)
        {
            addr &= 0x1ff;

            switch (addr)
            {
                // omitted registers
                case 0x29:
                case 0x2d:
                case 0x2e:
                case 0x2f:
                    break;

                // ADPCM A ---------------------------------------------------------------
                case 0x100:         // DM/KEYON
                    if ((data & 0x80)==0)  // KEY ON
                    {
                        adpcmakey |= (byte)(data & 0x3f);
                        for (int c = 0; c < 6; c++)
                        {
                            if ((data & (1 << c))!=0)
                            {
                                ResetStatus((int)(0x100 << c));
                                adpcma[c].pos = adpcma[c].start;
                                //					adpcma[c].step = 0x10000 - adpcma[c].step;
                                adpcma[c].step = 0;
                                adpcma[c].adpcmx = 0;
                                adpcma[c].adpcmd = 0;
                                adpcma[c].nibble = 0;
                            }
                        }
                    }
                    else
                    {                   // DUMP
                        adpcmakey &= (byte)~data;
                    }
                    break;

                case 0x101:
                    adpcmatl = (byte)(~data & 63);
                    break;

                case 0x108:
                case 0x109:
                case 0x10a:
                case 0x10b:
                case 0x10c:
                case 0x10d:
                    adpcma[addr & 7].pan = (byte)((data >> 6) & 3);
                    adpcma[addr & 7].level = (byte)(~data & 31);
                    break;

                case 0x110:
                case 0x111:
                case 0x112: // START ADDRESS (L)
                case 0x113:
                case 0x114:
                case 0x115:
                case 0x118:
                case 0x119:
                case 0x11a: // START ADDRESS (H)
                case 0x11b:
                case 0x11c:
                case 0x11d:
                    adpcmareg[addr - 0x110] =(byte) data;
                    adpcma[addr & 7].pos = adpcma[addr & 7].start =
                        (int)((adpcmareg[(addr & 7) + 8] * 256 + adpcmareg[addr & 7]) << 9);
                    break;

                case 0x120:
                case 0x121:
                case 0x122: // END ADDRESS (L)
                case 0x123:
                case 0x124:
                case 0x125:
                case 0x128:
                case 0x129:
                case 0x12a: // END ADDRESS (H)
                case 0x12b:
                case 0x12c:
                case 0x12d:
                    adpcmareg[addr - 0x110] = (byte)data;
                    adpcma[addr & 7].stop =
                        (int)((adpcmareg[(addr & 7) + 24] * 256 + adpcmareg[(addr & 7) + 16] + 1) << 9);
                    break;

                // ADPCMB -----------------------------------------------------------------
                case 0x10:
                    if ((data & 0x80)!=0 && !adpcmplay)
                    {
                        adpcmplay = true;
                        memaddr = startaddr;
                        adpcmx = 0;
                        adpcmd = 127;
                        adplc = 0;
                    }
                    if ((data & 1)!=0)
                        adpcmplay = false;
                    control1 = (byte)(data & 0x91);
                    break;


                case 0x11:      // Control Register 2
                    control2 = (byte)(data & 0xc0);
                    break;

                case 0x12:      // Start Address L
                case 0x13:      // Start Address H
                    adpcmreg[addr - 0x12 + 0] = (byte)data;
                    startaddr = (int)((adpcmreg[1] * 256 + adpcmreg[0]) << 9);
                    memaddr = startaddr;
                    break;

                case 0x14:      // Stop Address L
                case 0x15:      // Stop Address H
                    adpcmreg[addr - 0x14 + 2] = (byte)data;
                    stopaddr = (int)((adpcmreg[3] * 256 + adpcmreg[2] + 1) << 9);
                    //		LOG1("  stopaddr %.6x", stopaddr);
                    break;

                case 0x19:      // delta-N L
                case 0x1a:      // delta-N H
                    adpcmreg[addr - 0x19 + 4] = (byte)data;
                    deltan = (int)(adpcmreg[5] * 256 + adpcmreg[4]);
                    deltan = Math.max(256, deltan);
                    adpld = (int)(deltan * adplbase >> 16);
                    break;

                case 0x1b:      // Level Control
                    adpcmlevel = (byte)data;
                    adpcmvolume = (adpcmvol * adpcmlevel) >> 12;
                    break;

                case 0x1c:      // Flag Control
                    stmask = ~((data & 0xbf) << 8);
                    status &= stmask;
                    UpdateStatus();
                    break;

                default:
                    super.SetReg(addr, data);
                    break;
            }
            //	LOG0("\n");
        }

        // ---------------------------------------------------------------------------
        //	レジスタ取得
        //
        public int GetReg(int addr)
        {
            if (addr < 0x10)
                return psg.GetReg(addr);

            return 0;
        }

        // ---------------------------------------------------------------------------
        //	拡張ステータスを読みこむ
        //
        public int ReadStatusEx()
        {
            return (status & stmask) >> 8;
        }

        public void SetVolumeADPCMATotal(int db)
        {
            db = Math.min(db, 20);
            adpcmatvol = -(db * 2 / 3);
        }

        public void SetVolumeADPCMA(int index, int db)
        {
            db = Math.min(db, 20);
            adpcma[index].volume = -(db * 2 / 3);
        }

        public void SetVolumeADPCMB(int db)
        {
            db = Math.min(db, 20);
            if (db > -192)
                adpcmvol = (int)(65536.0 * Math.pow(10, db / 40.0));
            else
                adpcmvol = 0;
        }

        //		void	SetChannelMask(int mask);

        public class ADPCMA
        {
            public byte pan;      // ぱん
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

        private int DecodeADPCMASample(int a)
        {
            return -1;
        }

        int[] decode_tableA1 = new int[]
        {
        -1*16, -1*16, -1*16, -1*16, 2*16, 5*16, 7*16, 9*16,
        -1*16, -1*16, -1*16, -1*16, 2*16, 5*16, 7*16, 9*16
        };

        // ---------------------------------------------------------------------------
        //	ADPCMA 合成
        //
        public void ADPCMAMix(int[] buffer, int count)
        {

            if (adpcmatvol < 128 && (adpcmakey & 0x3f)!=0)
            {
                //Sample* limit = buffer + count * 2;
                int limit = count * 2;
                for (int i = 0; i < 6; i++)
                {
                    ADPCMA r = adpcma[i];
                    if ((adpcmakey & (1 << i))!=0 && (byte)r.level < 128)
                    {
                        int maskl = (int)((r.pan & 2)!=0 ? -1 : 0);
                        int maskr = (int)((r.pan & 1)!=0 ? -1 : 0);
                        if ((rhythmmask_ & (1 << i))!=0)
                        {
                            maskl = maskr = 0;
                        }

                        int db = fmgen.Limit(adpcmatl + adpcmatvol + r.level + r.volume, 127, -31);
                        int vol = tltable[fmgen.FM_TLPOS + (db << (fmgen.FM_TLBITS - 7))] >> 4;

                        //Sample* dest = buffer;
                        int dest = 0;
                        for (; dest < limit; dest += 2)
                        {
                            r.step += (int)adpcmastep;
                            if (r.pos >= r.stop)
                            {
                                SetStatus((int)(0x100 << i));
                                adpcmakey &= (byte)~(1 << i);
                                break;
                            }

                            for (; r.step > 0x10000; r.step -= 0x10000)
                            {
                                int data;
                                if ((r.pos & 1)==0)
                                {
                                    r.nibble = adpcmabuf[r.pos >> 1];
                                    data = (int)(r.nibble >> 4);
                                }
                                else
                                {
                                    data = (int)(r.nibble & 0x0f);
                                }
                                r.pos++;

                                r.adpcmx += jedi_table[r.adpcmd + data];
                                r.adpcmx = (short)fmgen.Limit(r.adpcmx, 2048 * 3 - 1, -2048 * 3);
                                r.adpcmd += (short)decode_tableA1[data];
                                r.adpcmd = (short)fmgen.Limit(r.adpcmd, 48 * 16, 0);
                            }
                            int sample = (r.adpcmx * vol) >> 10;
                            fmgen.StoreSample(buffer[dest+0], (int)(sample & maskl));
                            fmgen.StoreSample(buffer[dest+1], (int)(sample & maskr));
                            visRtmVolume[0] = (int)(sample & maskl);
                            visRtmVolume[1] = (int)(sample & maskr);
                        }
                    }
                }
            }
        }

        static byte[] table2 = new byte[]
        {
         1,  3,  5,  7,  9, 11, 13, 15,
        -1, -3, -5, -7, -9,-11,-13,-15,
        };


        public static void InitADPCMATable()
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

        // ADPCMA 関係
        public byte[] adpcmabuf;       // ADPCMA ROM
        public int adpcmasize;
        public ADPCMA[] adpcma = new ADPCMA[] { new ADPCMA(), new ADPCMA(), new ADPCMA(), new ADPCMA(), new ADPCMA(), new ADPCMA()};
        public byte adpcmatl;      // ADPCMA 全体の音量
        public int adpcmatvol;
        public byte adpcmakey;        // ADPCMA のキー
        public int adpcmastep;
        public byte[] adpcmareg = new byte[32];

        public static short[] jedi_table = new short[(48 + 1) * 16];

        //public new fmgen.Channel4[] ch = new fmgen.Channel4[6];
    };

    //	YM2612/3438(OPN2) ----------------------------------------------
    class OPN2 extends OPNBase
    {
        public boolean Init(int c, int r, boolean f /*= false*/, String s/* = null*/)
        {
            return false;
        }

        public boolean SetRate(int c, int r, boolean f)
        {
            return false;
        }

        public void Reset()
        {
        }

        public void Mix(int[] buffer, int nsamples)
        {
        }

        public void SetReg(int addr, int data)
        {
        }

        public int GetReg(int addr)
        {
            return 0;
        }

        public int ReadStatus()
        {
            return status & 0x03;
        }

        public int ReadStatusEx()
        {
            return 0xff;
        }

        public void SetChannelMask(int mask)
        {
        }

        private void Intr(boolean f)
        {
        }

        public void SetStatus(int bit)
        {
        }

        public void ResetStatus(int bit)
        {
        }

        private int[] fnum = new int[3];
        private int[] fnum3 = new int[3];
        private byte[] fnum2 = new byte[6];

        //// 線形補間用ワーク
        //private int mixc, mixc1;

        private fmgen.Channel4[] ch = new fmgen.Channel4[3];
    }
}
