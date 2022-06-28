/*
 * OPN/A/B interface with ADPCM support
 * Copyright (C) cisc 1998, 2003.
 *
 * $Id: Opna.h,v 1.33 2003/06/12 13:14:37 cisc Exp $
 */

package mdsound.fmgen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import dotnet4j.io.FileAccess;
import dotnet4j.io.FileMode;
import dotnet4j.io.FileStream;
import dotnet4j.io.Stream;
import mdsound.Common;

import static mdsound.fmgen.Fmgen.limit;


/**
 * class OPN/OPNA
 * OPN/OPNA に良く似た音を生成する音源ユニット
 * <p>
 * interface:
 * boolean Init(int clock, int rate, bool, final char* path);
 * 初期化．このクラスを使用する前にかならず呼んでおくこと．
 * OPNA の場合はこの関数でリズムサンプルを読み込む
 * <p>
 * clock: OPN/OPNA/OPNB のクロック周波数(Hz)
 * <p>
 * rate: 生成する PCM の標本周波数(Hz)
 * <p>
 * path: リズムサンプルのパス(OPNA のみ有効)
 * 省略時はカレントディレクトリから読み込む
 * 文字列の末尾には '\' や '/' などをつけること
 * <p>
 * 返り値 初期化に成功すれば true
 * <p>
 * boolean LoadRhythmSample(final char* path)
 * (OPNA ONLY)
 * Rhythm サンプルを読み直す．
 * path は Init の path と同じ．
 * <p>
 * boolean SetRate(int clock, int rate, bool)
 * クロックや PCM レートを変更する
 * 引数等は Init を参照のこと．
 * <p>
 * void Mix(FM_SAMPLETYPE* dest, int nsamples)
 * Stereo PCM データを nsamples 分合成し， dest で始まる配列に
 * 加える(加算する)
 * ・dest には sample*2 個分の領域が必要
 * ・格納形式は L, R, L, R... となる．
 * ・あくまで加算なので，あらかじめ配列をゼロクリアする必要がある
 * ・FM_SAMPLETYPE が short 型の場合クリッピングが行われる.
 * ・この関数は音源内部のタイマーとは独立している．
 * Timer は Count と GetNextEvent で操作する必要がある．
 * <p>
 * void Reset()
 * 音源をリセット(初期化)する
 * <p>
 * void SetReg(int reg, int data)
 * 音源のレジスタ reg に data を書き込む
 * <p>
 * int GetReg(int reg)
 * 音源のレジスタ reg の内容を読み出す
 * 読み込むことが出来るレジスタは Psg, ADPCM の一部，ID(0xff) とか
 * <p>
 * int ReadStatus()/ReadStatusEx()
 * 音源のステータスレジスタを読み出す
 * ReadStatusEx は拡張ステータスレジスタの読み出し(OPNA)
 * busy フラグは常に 0
 * <p>
 * boolean Count((int)32 t)
 * 音源のタイマーを t [μ秒] 進める．
 * 音源の内部状態に変化があった時(timer オーバーフロー)
 * true を返す
 * <p>
 * (int)32 GetNextEvent()
 * 音源のタイマーのどちらかがオーバーフローするまでに必要な
 * 時間[μ秒]を返す
 * タイマーが停止している場合は ULONG_MAX を返す… と思う
 * <p>
 * void SetVolumeFM(int db)/SetVolumePSG(int db) ...
 * 各音源の音量を＋－方向に調節する．標準値は 0.
 * 単位は約 1/2 dB，有効範囲の上限は 20 (10dB)
 */
public class Opna {
    // OPN Base
    static class OPNBase extends Timer {
        public OPNBase() {
            preScale = 0;
            psg = new PSG();
            chip = new Fmgen.Channel4.Chip();
        }

        // 初期化
        public boolean init(int c, int r) {
            clock = c;
            psgRate = r;

            return true;
        }

        public void reset() {
            status = 0;
            setPreScaler(0);
            super.reset();
            psg.reset();
        }

        // 音量設定
        public void setVolumeFM(int db) {
            db = Math.min(db, 20);
            if (db > -192)
                fmVolume = (int) (16384.0 * Math.pow(10.0, db / 40.0));
            else
                fmVolume = 0;
        }

        public void setVolumePSG(int db) {
            psg.setVolume(db);
        }

        public void setLPFCutoff(int freq) {
        }    // obsolete

        protected void setParameter(Fmgen.Channel4 ch, int addr, int data) {
            final int[] slotTable = new int[] {0, 2, 1, 3};
            final byte[] slTable = new byte[] {
                    0, 4, 8, 12, 16, 20, 24, 28,
                    32, 36, 40, 44, 48, 52, 56, 124
            };

            if ((addr & 3) < 3) {
                int slot = slotTable[(addr >> 2) & 3];
                Fmgen.Channel4.Operator op = ch.op[slot];

                switch ((addr >> 4) & 15) {
                case 3: // 30-3E DT/MULTI
                    op.setDT((data >> 4) & 0x07);
                    op.setMULTI(data & 0x0f);
                    break;

                case 4: // 40-4E TL
                    op.setTL(data & 0x7f, ((regTc & 0x80) != 0) && (csmCh == ch));
                    break;

                case 5: // 50-5E KS/AR
                    op.setKS((data >> 6) & 3);
                    op.setAR((data & 0x1f) * 2);
                    break;

                case 6: // 60-6E DR/AMON
                    op.setDR((data & 0x1f) * 2);
                    op.setAmOn((data & 0x80) != 0);
                    break;

                case 7: // 70-7E SR
                    op.setSR((data & 0x1f) * 2);
                    break;

                case 8: // 80-8E SL/RR
                    op.setSL(slTable[(data >> 4) & 15]);
                    op.setRR((data & 0x0f) * 4 + 2);
                    break;

                case 9: // 90-9E SSG-EC
                    op.setSSGEC(data & 0x0f);
                    break;
                }
            }
        }

        protected void setPreScaler(int p) {
            final byte[][] table = new byte[][] {new byte[] {6, 4}, new byte[] {3, 2}, new byte[] {2, 1}};
            final byte[] table2 = new byte[] {108, 77, 71, 67, 62, 44, 8, 5};
            // 512
            if (preScale != p) {
                preScale = (byte) p;
                //assert(0 <= prescale && prescale< 3);

                int fmClock = clock / table[p][0] / 12;

                rate = psgRate;

                // 合成周波数と出力周波数の比
                //assert(fmClock< (0x80000000 >> FM_RATIOBITS));
                int ratio = ((fmClock << Fmgen.FM_RATIOBITS) + rate / 2) / rate;

                setTimerBase(fmClock);
                //  MakeTimeTable(ratio);
                chip.setRatio(ratio);
                psg.setClock(clock / table[p][1], psgRate);

                for (int i = 0; i < 8; i++) {
                    lfoTable[i] = (ratio << (2 + Fmgen.FM_LFOCBITS - Fmgen.FM_RATIOBITS)) / table2[i];
                }
            }
        }

        protected void rebuildTimeTable() {
            int p = preScale;
            preScale = (byte) 0xff;
            setPreScaler(p);
        }

        protected int fmVolume;

        // OPN クロック
        protected int clock;
        // FM 音源合成レート
        protected int rate;
        // FMGen  出力レート
        protected int psgRate;
        protected int status;
        protected Fmgen.Channel4 csmCh;

        public int[] visVolume = new int[] {0, 0};

        protected int[] lfoTable = new int[8];

        // タイマー時間処理
        private void timerA() {
            if ((regTc & 0x80) != 0) {
                csmCh.keyControl(0x00);
                csmCh.keyControl(0x0f);
            }
        }

        protected byte preScale;

        protected Fmgen.Channel4.Chip chip;
        public PSG psg;
    }

    // OPN2 Base
    public static class OPNABase extends OPNBase {
        public int[] visRtmVolume = new int[] {0, 0};
        public int[] visAPCMVolume = new int[] {0, 0};

        public OPNABase() {
            amTable[0] = -1;
            tableHasMade = false;

            adpcmBuf = null;
            memAddr = 0;
            startAddr = 0;
            deltaN = 256;

            adpcmVol = 0;
            control2 = 0;

            makeTable2();
            buildLFOTable();
            for (int i = 0; i < 6; i++) {
                ch[i] = new Fmgen.Channel4();
                ch[i].setChip(chip);
                ch[i].setType(Fmgen.Channel4.Chip.OpType.typeN);
            }
        }

        public int readStatus() {
            return status & 0x03;
        }

        /**
         * 拡張ステータスを読みこむ
         */
        public int readStatusEx() {
            int a = status | 8;
            int b = a & stMask;
            int c = adpcmPlay ? 0x20 : 0;

            int r = b | c;
            status |= statusNext;
            statusNext = 0;
            return r;
        }

        /**
         * チャンネルマスクの設定
         */
        public void setChannelMask(int mask) {
            for (int i = 0; i < 6; i++)
                ch[i].mute(!((mask & (1 << i)) == 0));
            psg.setChannelMask(mask >> 6);
            adpcmMask_ = (mask & (1 << 9)) != 0;
            rhythmMask_ = (mask >> 10) & ((1 << 6) - 1);
        }

        private void intr(boolean f) {
        }

        /**
         * テーブル作成
         */
        private void makeTable2() {
            if (!tableHasMade) {
                for (int i = -Fmgen.FM_TLPOS; i < Fmgen.FM_TLENTS; i++) {
                    tlTable[i + Fmgen.FM_TLPOS] = (int) (65536.0 * Math.pow(2.0, i * -16.0 / Fmgen.FM_TLENTS)) - 1;
                }

                tableHasMade = true;
            }
        }

        /**
         * 初期化
         */
        protected boolean init(int c, int r, boolean f) {
            rebuildTimeTable();

            reset();

            setVolumeFM(0);
            setVolumePSG(0);
            setChannelMask(0);
            return true;
        }

        /**
         * サンプリングレート変更
         */
        protected boolean setRate(int c, int r, boolean f) {
            c /= 2; // 従来版との互換性を重視したけりゃコメントアウトしよう

            super.init(c, r);

            adplBase = (int) ((int) (8192.0 * (clock / 72.0) / r));
            adplD = deltaN * adplBase >> 16;

            rebuildTimeTable();

            lfoDCount = (reg22 & 0x08) != 0 ? lfoTable[reg22 & 7] : 0;
            return true;
        }

        /**
         * リセット
         */
        public void reset() {
            int i;

            super.reset();
            for (i = 0x20; i < 0x28; i++) setReg(i, 0);
            for (i = 0x30; i < 0xc0; i++) setReg(i, 0);
            for (i = 0x130; i < 0x1c0; i++) setReg(i, 0);
            for (i = 0x100; i < 0x110; i++) setReg(i, 0);
            for (i = 0x10; i < 0x20; i++) setReg(i, 0);
            for (i = 0; i < 6; i++) {
                pan[i] = 3;
                ch[i].reset();
            }

            stMask = 0x73;
            statusNext = 0;
            memAddr = 0;
            adpcmD = 127;
            adpcmX = 0;
            lfoCount = 0;
            adpcmPlay = false;
            adplC = 0;
            adplD = 0x100;
            status = 0;
            updateStatus();
        }

        /**
         * レジスタアレイにデータを設定
         */
        protected void setReg(int addr, int data) {
            int c = addr & 3;
            int modified;

            switch (addr) {

            // Timer
            case 0x24:
            case 0x25:
                setTimerA(addr, data);
                break;

            case 0x26:
                setTimerB(data);
                break;

            case 0x27:
                setTimerControl(data);
                break;

            // Misc-
            case 0x28: // Key On/Off
                if ((data & 3) < 3) {
                    c = (data & 3) + ((data & 4) != 0 ? 3 : 0);
                    ch[c].keyControl(data >> 4);
                }
                break;

            // Status Mask
            case 0x29:
                reg29 = data;
                // updateStatus(); // ?
                break;

            // Prescaler--
            case 0x2d:
            case 0x2e:
            case 0x2f:
                setPreScaler(addr - 0x2d);
                break;

            // F-Number---
            case 0x1a0:
            case 0x1a1:
            case 0x1a2:
                c += 3;
                fnum[c] = data + fnum2[c] * 0x100;
                ch[c].setFNum(fnum[c]);
                break;
            case 0xa0:
            case 0xa1:
            case 0xa2:
                fnum[c] = data + fnum2[c] * 0x100;
                ch[c].setFNum(fnum[c]);
                break;

            case 0x1a4:
            case 0x1a5:
            case 0x1a6:
                c += 3;
                fnum2[c] = (byte) (data);
                break;
            case 0xa4:
            case 0xa5:
            case 0xa6:
                fnum2[c] = (byte) (data);
                break;

            case 0xa8:
            case 0xa9:
            case 0xaa:
                fnum3[c] = data + fnum2[c + 6] * 0x100;
                break;

            case 0xac:
            case 0xad:
            case 0xae:
                fnum2[c + 6] = (byte) (data);
                break;

            // Algorithm--

            case 0x1b0:
            case 0x1b1:
            case 0x1b2:
                c += 3;
                ch[c].setFB((data >> 3) & 7);
                ch[c].setAlgorithm(data & 7);
                break;
            case 0xb0:
            case 0xb1:
            case 0xb2:
                ch[c].setFB((data >> 3) & 7);
                ch[c].setAlgorithm(data & 7);
                break;

            case 0x1b4:
            case 0x1b5:
            case 0x1b6:
                c += 3;
                pan[c] = (byte) ((data >> 6) & 3);
                ch[c].setMS(data);
                break;
            case 0xb4:
            case 0xb5:
            case 0xb6:
                pan[c] = (byte) ((data >> 6) & 3);
                ch[c].setMS(data);
                break;

            // LFO--
            case 0x22:
                modified = reg22 ^ data;
                reg22 = (byte) data;
                if ((modified & 0x8) != 0)
                    lfoCount = 0;
                lfoDCount = (reg22 & 8) != 0 ? lfoTable[reg22 & 7] : 0;
                break;

            // Psg--
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
                psg.setReg(addr, (byte) data);
                break;

            // 音色-
            default:
                if (c < 3) {
                    if ((addr & 0x100) != 0)
                        c += 3;
                    super.setParameter(ch[c], addr, data);
                }
                break;
            }
        }

        /**
         * ADPCM B
         */
        protected void setADPCMBReg(int addr, int data) {
            switch (addr) {
            case 0x00: // Controller Register 1
                if (((data & 0x80) != 0) && !adpcmPlay) {
                    adpcmPlay = true;
                    memAddr = startAddr;
                    adpcmX = 0;
                    adpcmD = 127;
                    adplC = 0;
                }
                if ((data & 1) != 0) {
                    adpcmPlay = false;
                }
                control1 = (byte) data;
                break;

            case 0x01: // Controller Register 2
                control2 = (byte) data;
                granuality = (byte) ((control2 & 2) != 0 ? 1 : 4);
                break;

            case 0x02: // Start Address L
            case 0x03: // Start Address H
                adpcmReg[addr - 0x02 + 0] = (byte) data;
                startAddr = (adpcmReg[1] * 256 + adpcmReg[0]) << 6;
                if ((control1 & 0x40) != 0) {
                    memAddr = startAddr;
                }
                //System.err.printf("  startaddr %.6x", startaddr);
                break;

            case 0x04: // Stop Address L
            case 0x05: // Stop Address H
                adpcmReg[addr - 0x04 + 2] = (byte) data;
                stopAddr = (adpcmReg[3] * 256 + adpcmReg[2] + 1) << 6;
                //System.err.printf("  stopaddr %.6x", stopaddr);
                break;

            case 0x08: // ADPCM data
                if ((control1 & 0x60) == 0x60) {
                    //System.err.printf("  Wr [0x%.5x] = %.2x", memaddr, data);
                    writeRAM(data);
                }
                break;

            case 0x09: // delta-N L
            case 0x0a: // delta-N H
                adpcmReg[addr - 0x09 + 4] = (byte) data;
                deltaN = adpcmReg[5] * 256 + adpcmReg[4];
                deltaN = Math.max(256, deltaN);
                adplD = deltaN * adplBase >> 16;
                break;

            case 0x0b: // Level Controller
                adpcmLevel = (byte) data;
                adpcmVolume = (adpcmVol * adpcmLevel) >> 12;
                break;

            case 0x0c: // Limit Address L
            case 0x0d: // Limit Address H
                adpcmReg[addr - 0x0c + 6] = (byte) data;
                limitAddr = (adpcmReg[7] * 256 + adpcmReg[6] + 1) << 6;
                //System.err.printf("  limitaddr %.6x", limitaddr);
                break;

            case 0x10: // Flag Controller
                if ((data & 0x80) != 0) {
                    status = 0;
                    updateStatus();
                } else {
                    stMask = ~(data & 0x1f);
                    // updateStatus(); // ???
                }
                break;
            }
        }

        /**
         * レジスタ取得
         */
        protected int getReg(int addr) {
            if (addr < 0x10)
                return psg.getReg(addr);

            if (addr == 0x108) {
                //System.err.printf("%d:reg[108] .   ", Diag::GetCPUTick());

                int data = adpcmReadBuf & 0xff;
                adpcmReadBuf >>= 8;
                if ((control1 & 0x60) == 0x20) {
                    adpcmReadBuf |= readRAM() << 8;
                    //System.err.printf("Rd [0x%.6x:%.2x] ", memaddr, adpcmreadbuf >> 8);
                }
                //System.err.printf("%.2x\n");
                return data;
            }

            if (addr == 0xff)
                return 1;

            return 0;
        }

        /**
         * 合成
         * @param buffer  合成先
         * @param nsamples 合成サンプル数
         */
        protected void fmMix(int[] buffer, int nsamples) {
            if (fmVolume > 0) {
                // 準備
                // Set F-Number
                if ((regTc & 0xc0) == 0)
                    csmCh.setFNum(fnum[2]);// csmch - ch]);
                else {
                    // 効果音モード
                    csmCh.op[0].setFNum(fnum3[1]);
                    csmCh.op[1].setFNum(fnum3[2]);
                    csmCh.op[2].setFNum(fnum3[0]);
                    csmCh.op[3].setFNum(fnum[2]);
                }

                int act = (((ch[2].prepare() << 2) | ch[1].prepare()) << 2) | ch[0].prepare();
                if ((reg29 & 0x80) != 0)
                    act |= (ch[3].prepare() | ((ch[4].prepare() | (ch[5].prepare() << 2)) << 2)) << 6;
                if ((reg22 & 0x08) == 0)
                    act &= 0x555;

                if ((act & 0x555) != 0) {
                    mix6(buffer, nsamples, act);
                }
            }
        }

        protected void mix6(int[] buffer, int nsamples, int activech) {
            // Mix
            int[] ibuf = new int[6];
            int[] idest = new int[6];
            idest[0] = pan[0];
            idest[1] = pan[1];
            idest[2] = pan[2];
            idest[3] = pan[3];
            idest[4] = pan[4];
            idest[5] = pan[5];

            int limit = nsamples * 2;
            for (int dest = 0; dest < limit; dest += 2) {
                ibuf[1] = ibuf[2] = ibuf[3] = 0;
                if ((activech & 0xaaa) != 0) {
                    lfo();
                    mixSubSL(activech, idest, ibuf);
                } else {
                    mixSubS(activech, idest, ibuf);
                }

                int v = ((limit(ibuf[2] + ibuf[3], 0x7fff, -0x8000) * fmVolume) >> 14);
                buffer[dest + 0] += v;
                visVolume[0] = v;

                v = ((limit(ibuf[1] + ibuf[3], 0x7fff, -0x8000) * fmVolume) >> 14);
                buffer[dest + 1]  += v;
                visVolume[1] = v;
            }
        }

        protected void mixSubS(int activech, int[] dest, int[] buf) {
            if ((activech & 0x001) != 0) buf[dest[0]] = ch[0].calc();
            if ((activech & 0x004) != 0) buf[dest[1]] += ch[1].calc();
            if ((activech & 0x010) != 0) buf[dest[2]] += ch[2].calc();
            if ((activech & 0x040) != 0) buf[dest[3]] += ch[3].calc();
            if ((activech & 0x100) != 0) buf[dest[4]] += ch[4].calc();
            if ((activech & 0x400) != 0) buf[dest[5]] += ch[5].calc();
        }

        protected void mixSubSL(int activech, int[] dest, int[] buf) {
            if ((activech & 0x001) != 0) buf[dest[0]] = ch[0].calcL();
            if ((activech & 0x004) != 0) buf[dest[1]] += ch[1].calcL();
            if ((activech & 0x010) != 0) buf[dest[2]] += ch[2].calcL();
            if ((activech & 0x040) != 0) buf[dest[3]] += ch[3].calcL();
            if ((activech & 0x100) != 0) buf[dest[4]] += ch[4].calcL();
            if ((activech & 0x400) != 0) buf[dest[5]] += ch[5].calcL();
        }

        /**
         * ステータスフラグ設定
         */
        protected void setStatus(int bits) {
            if ((status & bits) == 0) {
                //  System.err.printf("SetStatus(%.2x %.2x)\n", bits, stmask);
                status |= bits & stMask;
                updateStatus();
            }
            // else
            //System.err.printf("SetStatus(%.2x) - ignored\n", bits);
        }

        protected void resetStatus(int bits) {
            status &= ~bits;
            // System.err.printf("ResetStatus(%.2x)\n", bits);
            updateStatus();
        }

        protected void updateStatus() {
            // System.err.printf("%d:INT = %d\n", Diag::GetCPUTick(), (status & stmask & reg29) != 0);
            intr((status & stMask & reg29) != 0);
        }

        protected void lfo() {
            // System.err.printf("%4d - %8d, %8d\n", c, lfocount, lfodcount);

            chip.setPML(pmTable[(lfoCount >> (Fmgen.FM_LFOCBITS + 1)) & 0xff]);
            chip.setAML(amTable[(lfoCount >> (Fmgen.FM_LFOCBITS + 1)) & 0xff]);
            lfoCount += lfoDCount;
        }

        protected static void buildLFOTable() {
            if (amTable[0] == -1) {
                for (int c = 0; c < 256; c++) {
                    int v;
                    if (c < 0x40) v = c * 2 + 0x80;
                    else if (c < 0xc0) v = 0x7f - (c - 0x40) * 2 + 0x80;
                    else v = (c - 0xc0) * 2;
                    pmTable[c] = c;

                    if (c < 0x80) v = 0xff - c * 2;
                    else v = (c - 0x80) * 2;
                    amTable[c] = v & ~3;
                }
            }
        }

        /**
         * ADPCM 展開
         */
        protected void decodeADPCMB() {
            apOut0 = apOut1;
            int n = (readRAMN() * adpcmVolume) >> 13;
            apOut1 = adpcmOut + n;
            adpcmOut = n;
        }

        /**
         * ADPCM 合成
         */
        protected void adpcmBMix(int[] dest, int count) {
            int maskL = (control2 & 0x80) != 0 ? -1 : 0;
            int maskR = (control2 & 0x40) != 0 ? -1 : 0;
            if (adpcmMask_) {
                maskL = maskR = 0;
            }

            if (adpcmPlay) {
                int ptrDest = 0;
                //  System.err.printf("ADPCM Play: %d   DeltaN: %d\n", adpld, deltan);
                if (adplD <= 8192) { // fplay < fsamp
                    for (; count > 0; count--) {
                        if (adplC < 0) {
                            adplC += 8192;
                            decodeADPCMB();
                            if (!adpcmPlay)
                                break;
                        }
                        int s = (adplC * apOut0 + (8192 - adplC) * apOut1) >> 13;
                        dest[ptrDest + 0] += s & maskL;
                        dest[ptrDest + 1] += s & maskR;
                        visAPCMVolume[0] = s & maskL;
                        visAPCMVolume[1] = s & maskR;
                        ptrDest += 2;
                        adplC -= adplD;
                    }
                    for (; count > 0 && apOut0 != 0; count--) {
                        if (adplC < 0) {
                            apOut0 = apOut1;
                            apOut1 = 0;
                            adplC += 8192;
                        }
                        int s = (adplC * apOut1) >> 13;
                        dest[ptrDest + 0] += s & maskL;
                        dest[ptrDest + 1] += s & maskR;
                        visAPCMVolume[0] = s & maskL;
                        visAPCMVolume[1] = s & maskR;
                        ptrDest += 2;
                        adplC -= adplD;
                    }
                } else { // fplay > fsamp (adpld = fplay/famp*8192)
                    int t = (-8192 * 8192) / adplD;
stop:
                    for (; count > 0; count--) {
                        int s = apOut0 * (8192 + adplC);
                        while (adplC < 0) {
                            decodeADPCMB();
                            if (!adpcmPlay)
                                break stop;
                            s -= apOut0 * Math.max(adplC, t);
                            adplC -= t;
                        }
                        adplC -= 8192;
                        s >>= 13;
                        dest[ptrDest + 0] += s & maskL;
                        dest[ptrDest + 1] += s & maskR;
                        visAPCMVolume[0] = s & maskL;
                        visAPCMVolume[1] = s & maskR;
                        ptrDest += 2;
                    }
                }
            }
            if (!adpcmPlay) {
                apOut0 = apOut1 = adpcmOut = 0;
                adplC = 0;
            }
        }

        /**
         * ADPCM RAM への書込み操作
         */
        protected void writeRAM(int data) {
            if (NO_BITTYPE_EMULATION) {
                if ((control2 & 2) == 0) {
                    // 1 bit mode
                    adpcmBuf[(memAddr >> 4) & 0x3ffff] = (byte) data;
                    memAddr += 16;
                } else {
                    // 8 bit mode
                    //(int)8* p = &adpcmbuf[(memaddr >> 4) & 0x7fff];
                    int p = (memAddr >> 4) & 0x7fff;
                    int bank = (memAddr >> 1) & 7;
                    byte mask = (byte) (1 << bank);
                    data <<= bank;

                    adpcmBuf[p + 0x00000] = (byte) ((adpcmBuf[p + 0x00000] & ~mask) | ((byte) (data) & mask));
                    data >>= 1;
                    adpcmBuf[p + 0x08000] = (byte) ((adpcmBuf[p + 0x08000] & ~mask) | ((byte) (data) & mask));
                    data >>= 1;
                    adpcmBuf[p + 0x10000] = (byte) ((adpcmBuf[p + 0x10000] & ~mask) | ((byte) (data) & mask));
                    data >>= 1;
                    adpcmBuf[p + 0x18000] = (byte) ((adpcmBuf[p + 0x18000] & ~mask) | ((byte) (data) & mask));
                    data >>= 1;
                    adpcmBuf[p + 0x20000] = (byte) ((adpcmBuf[p + 0x20000] & ~mask) | ((byte) (data) & mask));
                    data >>= 1;
                    adpcmBuf[p + 0x28000] = (byte) ((adpcmBuf[p + 0x28000] & ~mask) | ((byte) (data) & mask));
                    data >>= 1;
                    adpcmBuf[p + 0x30000] = (byte) ((adpcmBuf[p + 0x30000] & ~mask) | ((byte) (data) & mask));
                    data >>= 1;
                    adpcmBuf[p + 0x38000] = (byte) ((adpcmBuf[p + 0x38000] & ~mask) | ((byte) (data) & mask));
                    memAddr += 2;
                }
            } else {
                adpcmBuf[(memAddr >> granuality) & 0x3ffff] = (byte) data;
                memAddr += 1 << granuality;
            }

            if (memAddr == stopAddr) {
                setStatus(4);
                statusNext = 0x04;// EOS
                memAddr &= 0x3fffff;
            }
            if (memAddr == limitAddr) {
                //System.err.printf("Limit ! (%.8x)\n", limitaddr);
                memAddr = 0;
            }
            setStatus(8);
        }

        /**
         * ADPCM RAM からの読み込み操作
         */
        protected int readRAM() {
            int data;
            if (NO_BITTYPE_EMULATION) {
                if ((control2 & 2) == 0) {
                    // 1 bit mode
                    data = adpcmBuf[(memAddr >> 4) & 0x3ffff];
                    memAddr += 16;
                } else {
                    // 8 bit mode
                    //(int)8* p = &adpcmbuf[(memaddr >> 4) & 0x7fff];
                    int p = (memAddr >> 4) & 0x7fff;
                    int bank = (memAddr >> 1) & 7;
                    byte mask = (byte) (1 << bank);

                    data = adpcmBuf[p + 0x38000] & mask;
                    data = data * 2 + (adpcmBuf[p + 0x30000] & mask);
                    data = data * 2 + (adpcmBuf[p + 0x28000] & mask);
                    data = data * 2 + (adpcmBuf[p + 0x20000] & mask);
                    data = data * 2 + (adpcmBuf[p + 0x18000] & mask);
                    data = data * 2 + (adpcmBuf[p + 0x10000] & mask);
                    data = data * 2 + (adpcmBuf[p + 0x08000] & mask);
                    data = data * 2 + (adpcmBuf[p + 0x00000] & mask);
                    data >>= bank;
                    memAddr += 2;
                }
            } else {
                data = adpcmBuf[(memAddr >> granuality) & 0x3ffff];
                memAddr += 1 << granuality;
            }

            if (memAddr == stopAddr) {
                setStatus(4);
                statusNext = 0x04; // EOS
                memAddr &= 0x3fffff;
            }
            if (memAddr == limitAddr) {
                //System.err.printf("Limit ! (%.8x)\n", limitaddr);
                memAddr = 0;
            }
            if (memAddr < stopAddr)
                setStatus(8);
            return data;
        }

        /**
         * ADPCM RAM からの nibble 読み込み及び ADPCM 展開
         */
        protected int readRAMN() {
            int data;
            if (granuality > 0) {
                if (NO_BITTYPE_EMULATION) {
                    if ((control2 & 2) == 0) {
                        data = adpcmBuf[(memAddr >> 4) & 0x3ffff];
                        memAddr += 8;
                        if ((memAddr & 8) != 0)
                            return decodeADPCMBSample(data >> 4);
                        data &= 0x0f;
                    } else {
                        //(int)8* p = &adpcmbuf[(memaddr >> 4) & 0x7fff] + ((~memaddr & 1) << 17);
                        int p = ((memAddr >> 4) & 0x7fff) + ((~memAddr & 1) << 17);
                        int bank = (memAddr >> 1) & 7;
                        byte mask = (byte) (1 << bank);

                        data = adpcmBuf[p + 0x18000] & mask;
                        data = data * 2 + (adpcmBuf[p + 0x10000] & mask);
                        data = data * 2 + (adpcmBuf[p + 0x08000] & mask);
                        data = data * 2 + (adpcmBuf[p + 0x00000] & mask);
                        data >>= bank;
                        memAddr++;
                        if ((memAddr & 1) != 0)
                            return decodeADPCMBSample(data);
                    }
                } else {
                    data = adpcmBuf[(memAddr >> granuality) & adpcmMask];
                    memAddr += 1 << (granuality - 1);
                    if ((memAddr & (1 << (granuality - 1))) != 0)
                        return decodeADPCMBSample(data >> 4);
                    data &= 0x0f;
                }
            } else {
                data = adpcmBuf[(memAddr >> 1) & adpcmMask];
                ++memAddr;
                if ((memAddr & 1) != 0)
                    return decodeADPCMBSample(data >> 4);
                data &= 0x0f;
            }

            decodeADPCMBSample(data);

            // check
            if (memAddr == stopAddr) {
                if ((control1 & 0x10) != 0) {
                    memAddr = startAddr;
                    data = adpcmX;
                    adpcmX = 0;
                    adpcmD = 127;
                    return data;
                } else {
                    memAddr &= adpcmMask; // 0x3fffff;
                    setStatus(adpcmNotice);
                    adpcmPlay = false;
                }
            }

            if (memAddr == limitAddr)
                memAddr = 0;

            return adpcmX;
        }

        protected int decodeADPCMBSample(int data) {
            final int[] table1 = new int[] {
                    1, 3, 5, 7, 9, 11, 13, 15,
                    -1, -3, -5, -7, -9, -11, -13, -15,
            };

            final int[] table2 = new int[] {
                    57, 57, 57, 57, 77, 102, 128, 153,
                    57, 57, 57, 57, 77, 102, 128, 153,
            };

            adpcmX = limit(adpcmX + table1[data] * adpcmD / 8, 32767, -32768);
            adpcmD = limit(adpcmD * table2[data] / 64, 24576, 127);
            return adpcmX;
        }

        public static boolean NO_BITTYPE_EMULATION = false;

        // FM 音源関係

        protected byte[] pan = new byte[6];
        protected byte[] fnum2 = new byte[9];

        protected byte reg22;
        protected int reg29; // OPNA only?

        protected int stMask;
        protected int statusNext;

        protected int lfoCount;
        protected int lfoDCount;

        protected int[] fnum = new int[6];
        protected int[] fnum3 = new int[3];

        // ADPCM 関係

        // ADPCM RAM
        protected byte[] adpcmBuf;
        // メモリアドレスに対するビットマスク
        protected int adpcmMask;
        // ADPCM 再生終了時にたつビット
        protected int adpcmNotice;
        // Start address
        protected int startAddr;
        // Stop address
        protected int stopAddr;
        // 再生中アドレス
        protected int memAddr;
        // Limit address/mask
        protected int limitAddr;
        // ADPCM 音量
        protected int adpcmLevel;
        protected int adpcmVolume;
        protected int adpcmVol;
        // ⊿ N
        protected int deltaN;
        // 周波数変換用変数
        protected int adplC;
        // 周波数変換用変数差分値
        protected int adplD;
        // adpld の元
        protected int adplBase;
        // ADPCM 合成用 x
        protected int adpcmX;
        // ADPCM 合成用 ⊿
        protected int adpcmD;
        // ADPCM 合成後の出力
        protected int adpcmOut;
        // out(t - 2) + out(t - 1)
        protected int apOut0;
        // out(t - 1) + out(t)
        protected int apOut1;

        // ADPCM リード用バッファ
        protected int adpcmReadBuf;
        // ADPCM 再生中
        protected boolean adpcmPlay;
        protected byte granuality;
        protected boolean adpcmMask_;

        // ADPCM コントロールレジスタ１
        protected byte control1;
        // ADPCM コントロールレジスタ２
        protected byte control2;
        // ADPCM レジスタの一部分
        protected byte[] adpcmReg = new byte[8];

        protected int rhythmMask_;

        protected Fmgen.Channel4[] ch = new Fmgen.Channel4[6];

        public static int[] amTable = new int[Fmgen.FM_LFOENTS];
        public static int[] pmTable = new int[Fmgen.FM_LFOENTS];
        public static int[] tlTable = new int[Fmgen.FM_TLENTS + Fmgen.FM_TLPOS];
        protected static boolean tableHasMade;
    }

    /** YM2203(OPN) */
    public static class OPN extends OPNBase {

        public OPN() {
            setVolumeFM(0);
            setVolumePSG(0);

            csmCh = ch[2];

            for (int i = 0; i < 3; i++) {
                ch[i].setChip(chip);
                ch[i].setType(Fmgen.Channel4.Chip.OpType.typeN);
            }
        }

        // 初期化
        public boolean init(int c, int r, boolean ip /*= false*/, String s/* = ""*/) {
            if (!setRate(c, r, ip))
                return false;

            reset();

            setVolumeFM(0);
            setVolumePSG(0);
            setChannelMask(0);
            return true;
        }

        // サンプリングレート変更
        public boolean setRate(int c, int r, boolean f/* = false*/) {
            super.init(c, r);
            rebuildTimeTable();
            return true;
        }

        // リセット
        public void reset() {
            for (int i = 0x20; i < 0x28; i++) setReg(i, 0);
            for (int i = 0x30; i < 0xc0; i++) setReg(i, 0);
            super.reset();
            ch[0].reset();
            ch[1].reset();
            ch[2].reset();
        }

        // 合成(2ch)
        public void mix(int[] buffer, int nsamples) {

            psg.mix(buffer, nsamples);

            // Set F-Number
            ch[0].setFNum(fnum[0]);
            ch[1].setFNum(fnum[1]);
            if ((regTc & 0xc0) == 0)
                ch[2].setFNum(fnum[2]);
            else { // 効果音
                ch[2].op[0].setFNum(fnum3[1]);
                ch[2].op[1].setFNum(fnum3[2]);
                ch[2].op[2].setFNum(fnum3[0]);
                ch[2].op[3].setFNum(fnum[2]);
            }

            int actch = (((ch[2].prepare() << 2) | ch[1].prepare()) << 2) | ch[0].prepare();
            if ((actch & 0x15) != 0) {
                int limit = nsamples * 2;
                for (int dest = 0; dest < limit; dest += 2) {
                    int s = 0;
                    if ((actch & 0x01) != 0) s = ch[0].calc();
                    if ((actch & 0x04) != 0) s += ch[1].calc();
                    if ((actch & 0x10) != 0) s += ch[2].calc();
                    s = ((limit(s, 0x7fff, -0x8000) * fmVolume) >> 14);
                    buffer[dest + 0] += s;
                    buffer[dest + 1] += s;

                    visVolume[0] = s;
                    visVolume[1] = s;
                }
            }
        }

        // レジスタアレイにデータを設定
        public void setReg(int addr, int data) {
            // System.err.printf("reg[%.2x] <- %.2x\n", addr, data);
            if (addr >= 0x100)
                return;

            int c = addr & 3;
            switch (addr) {
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
                psg.setReg(addr, (byte) data);
                break;

            case 0x24:
            case 0x25:
                setTimerA(addr, data);
                break;

            case 0x26:
                setTimerB(data);
                break;

            case 0x27:
                setTimerControl(data);
                break;

            case 0x28: // Key On/Off
                if ((data & 3) < 3)
                    ch[data & 3].keyControl(data >> 4);
                break;

            case 0x2d:
            case 0x2e:
            case 0x2f:
                setPreScaler(addr - 0x2d);
                break;

            // F-Number
            case 0xa0:
            case 0xa1:
            case 0xa2:
                fnum[c] = data + fnum2[c] * 0x100;
                break;

            case 0xa4:
            case 0xa5:
            case 0xa6:
                fnum2[c] = (byte) (data);
                break;

            case 0xa8:
            case 0xa9:
            case 0xaa:
                fnum3[c] = data + fnum2[c + 3] * 0x100;
                break;

            case 0xac:
            case 0xad:
            case 0xae:
                fnum2[c + 3] = (byte) (data);
                break;

            case 0xb0:
            case 0xb1:
            case 0xb2:
                ch[c].setFB((data >> 3) & 7);
                ch[c].setAlgorithm(data & 7);
                break;

            default:
                if (c < 3) {
                    if ((addr & 0xf0) == 0x60)
                        data &= 0x1f;
                    super.setParameter(ch[c], addr, data);
                }
                break;
            }
        }

        // レジスタ読み込み
        public int getReg(int addr) {
            if (addr < 0x10)
                return psg.getReg(addr);
            else
                return 0;
        }

        public int readStatus() {
            return status & 0x03;
        }

        public int readStatusEx() {
            return 0xff;
        }

        // マスク設定
        public void setChannelMask(int mask) {
            for (int i = 0; i < 3; i++)
                ch[i].mute(!!((mask & (1 << i)) != 0));
            psg.setChannelMask(mask >> 6);
        }

        public int dbgGetOpOut(int c, int s) {
            return ch[c].op[s].dbgOpOut;
        }

        public int dbgGetPGOut(int c, int s) {
            return ch[c].op[s].dbgPgOut;
        }

        public Fmgen.Channel4 dbgGetCh(int c) {
            return ch[c];
        }

        private void intr(boolean f) {
        }

        // ステータスフラグ設定
        protected void setStatus(int bits) {
            if ((status & bits) == 0) {
                status |= bits;
                intr(true);
            }
        }

        protected void resetStatus(int bit) {
            status &= ~bit;
            if (status == 0)
                intr(false);
        }

        private int[] fnum = new int[3];
        private int[] fnum3 = new int[3];
        private byte[] fnum2 = new byte[6];

        private Fmgen.Channel4[] ch = new Fmgen.Channel4[] {
                new Fmgen.Channel4(), new Fmgen.Channel4(), new Fmgen.Channel4()
        };
    }

    /** YM2608(OPNA) */
    public static class OPNA extends OPNABase {
        /**
         * 構築
         */
        public OPNA(byte chipId) {
            for (int i = 0; i < 6; i++) {
                rhythm[i].sample = null;
                rhythm[i].pos = 0;
                rhythm[i].size = 0;
                rhythm[i].volume = 0;
            }
            rhythmTVol = 0;
            adpcmMask = 0x3ffff;
            adpcmNotice = 4;
            csmCh = ch[2];
            this.chipId = chipId;
        }

        protected void finalize() {
            adpcmBuf = null;
            for (int i = 0; i < 6; i++) {
                rhythm[i].sample = null;
            }
        }

        public boolean init(int c, int r) {
            return init(c, r, false, "");
        }

        public boolean init(int c, int r, boolean ipFlag, String path) {
            return init(c, r, ipFlag, fname -> createRhythmFileStream(path, fname));
        }

        public boolean init(int c, int r, boolean ipflag, Function<String, Stream> appendFileReaderCallback/* = null*/) {
            rate = 8000;
            loadRhythmSample(appendFileReaderCallback);

            if (adpcmBuf == null)
                adpcmBuf = new byte[0x40000];

            if (!setRate(c, r, ipflag))
                return false;
            if (!super.init(c, r, ipflag))
                return false;

            this.reset();

            setVolumeADPCM(0);
            setVolumeRhythmTotal(0);
            for (int i = 0; i < 6; i++)
                setVolumeRhythm(0, 0);
            return true;
        }

        public static class Whdr {
            public int chunkSize;
            public int tag;
            public int nch;
            public int rate;
            public int avgBytes;
            public int align;
            public int bps;
            public int size;
        }

        private FileStream createRhythmFileStream(String dir, String fname) {
            Path path = dir == null || dir.isEmpty() ? Paths.get(fname) : Paths.get(dir, fname);
            return Files.exists(path) ? new FileStream(path.toString(), FileMode.Open, FileAccess.Read) : null;
        }

        public boolean loadRhythmSample(String path) {
            return loadRhythmSample(fname -> createRhythmFileStream(path, fname));
        }

        /**
         * リズム音を読みこむ
         */
        public boolean loadRhythmSample(Function<String, Stream> appendFileReaderCallback) {
            final String[] rhythmNames = new String[] {
                    "bd", "sd", "top", "hh", "tom", "rim",
            };

            int i;
            for (i = 0; i < 6; i++)
                rhythm[i].pos = ~(int) 0;

            for (i = 0; i < 6; i++) {
                try {
                    int fsize;
                    boolean f = true;
                    String buf1 = String.format("2608_%s_%d.wav", rhythmNames[i], chipId);
                    String buf2 = String.format("2608_%s.wav", rhythmNames[i]);
                    String rymBuf1 = String.format("2608_rym_%d.wav", chipId);
                    String rymBuf2 = "2608_rym.wav";
                    byte[] file;

                    try (Stream st = appendFileReaderCallback.apply(buf1)) {
                        file = Common.readAllBytes(st);
                    }
                    if (file == null) {
                        try (Stream st = appendFileReaderCallback.apply(buf2)) {
                            file = Common.readAllBytes(st);
                        }
                    }

                    if (file == null) {
                        f = false;
                        if (i == 5) {
                            try (Stream st = appendFileReaderCallback.apply(rymBuf1)) {
                                file = Common.readAllBytes(st);
                            }
                            if (file == null) {
                                try (Stream st = appendFileReaderCallback.apply(rymBuf2)) {
                                    file = Common.readAllBytes(st);
                                }
                            }
                            if (file != null) {
                                f = true;
                            }
                        }
                    }

                    if (!f) {
                        continue;
                    }

                    Whdr whdr = new Whdr();

                    int fInd = 0x10;
                    byte[] bufWhdr = new byte[4 + 2 + 2 + 4 + 4 + 2 + 2 + 2];
                    for (int j = 0; j < 4 + 2 + 2 + 4 + 4 + 2 + 2 + 2; j++) bufWhdr[j] = file[fInd++];

                    int chunkSize = bufWhdr[0] + bufWhdr[1] * 0x100 + bufWhdr[2] * 0x10000 + bufWhdr[3] * 0x10000;
                    whdr.chunkSize = chunkSize;
                    whdr.tag = bufWhdr[4] + bufWhdr[5] * 0x100;
                    whdr.nch = bufWhdr[6] + bufWhdr[7] * 0x100;
                    whdr.rate = bufWhdr[8] + bufWhdr[9] * 0x100 + bufWhdr[10] * 0x10000 + bufWhdr[11] * 0x10000;
                    whdr.avgBytes = bufWhdr[12] + bufWhdr[13] * 0x100 + bufWhdr[14] * 0x10000 + bufWhdr[15] * 0x10000;
                    whdr.align = bufWhdr[16] + bufWhdr[17] * 0x100;
                    whdr.bps = bufWhdr[18] + bufWhdr[19] * 0x100;
                    whdr.size = bufWhdr[20] + bufWhdr[21] * 0x100;

                    byte[] subchunkname = new byte[4];
                    fsize = 4 + whdr.chunkSize - (4 + 2 + 2 + 4 + 4 + 2 + 2 + 2);
                    do {
                        fInd += fsize;
                        for (int j = 0; j < 4; j++) subchunkname[j] = file[fInd++];
                        for (int j = 0; j < 4; j++) bufWhdr[j] = file[fInd++];

                        fsize = chunkSize;
                    } while ('d' != subchunkname[0] || 'a' != subchunkname[1] || 't' != subchunkname[2] || 'a' != subchunkname[3]);

                    fsize /= 2;
                    if (fsize >= 0x100000 || whdr.tag != 1 || whdr.nch != 1)
                        break;
                    fsize = Math.max(fsize, (1 << 31) / 1024);

                    rhythm[i].sample = null;
                    rhythm[i].sample = new int[fsize];
                    if (rhythm[i].sample == null)
                        break;
                    byte[] bufSample = new byte[fsize * 2];
                    for (int j = 0; j < fsize * 2; j++) bufSample[j] = file[fInd++];
                    for (int si = 0; si < fsize; si++) {
                        rhythm[i].sample[si] = (short) (bufSample[si * 2] + bufSample[si * 2 + 1] * 0x100);
                    }

                    rhythm[i].rate = whdr.rate;
                    rhythm[i].step = rhythm[i].rate * 1024 / rate;
                    rhythm[i].pos = rhythm[i].size = fsize * 1024;
                } catch (Exception e) {
                    // 無視
                }
            }
            if (i != 6) {
                for (i = 0; i < 6; i++) {
                    rhythm[i].sample = null;
                }
                return false;
            }
            return true;
        }

        /**
         * サンプリングレート変更
         */
        public boolean setRate(int c, int r, boolean ipFlag/* = false*/) {
            if (!super.setRate(c, r, ipFlag))
                return false;

            for (int i = 0; i < 6; i++) {
                rhythm[i].step = rhythm[i].rate * 1024 / r;
            }
            return true;
        }

        /**
         * 合成
         * @param buffer  合成先
         * @param nsamples 合成サンプル数
         */
        public void mix(int[] buffer, int nsamples) {
            fmMix(buffer, nsamples);
            psg.mix(buffer, nsamples);
            adpcmBMix(buffer, nsamples);
            rhythmMix(buffer, nsamples);
        }

        /**
         * リセット
         */
        public void reset() {
            reg29 = 0x1f;
            rhythmKey = 0;
            limitAddr = 0x3ffff;
            super.reset();
        }

        /**
         * レジスタアレイにデータを設定
         */
        public void setReg(int addr, int data) {
            addr &= 0x1ff;

            switch (addr) {
            case 0x29:
                reg29 = data;
                // updateStatus(); // ?
                break;

            // Rhythm
            case 0x10: // DM/KEYON
                if ((data & 0x80) == 0) { // KEY ON
                    rhythmKey |= (byte) (data & 0x3f);
                    if ((data & 0x01) != 0) rhythm[0].pos = 0;
                    if ((data & 0x02) != 0) rhythm[1].pos = 0;
                    if ((data & 0x04) != 0) rhythm[2].pos = 0;
                    if ((data & 0x08) != 0) rhythm[3].pos = 0;
                    if ((data & 0x10) != 0) rhythm[4].pos = 0;
                    if ((data & 0x20) != 0) rhythm[5].pos = 0;
                } else { // DUMP
                    rhythmKey &= (byte) (~(byte) data);
                }
                break;

            case 0x11:
                rhythmTl = (byte) (~data & 63);
                break;

            case 0x18: // Bass Drum
            case 0x19: // Snare Drum
            case 0x1a: // Top Cymbal
            case 0x1b: // Hihat
            case 0x1c: // Tom-tom
            case 0x1d: // Rim shot
                rhythm[addr & 7].pan = (byte) ((data >> 6) & 3);
                rhythm[addr & 7].level = (byte) (~data & 31);
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
                super.setADPCMBReg(addr - 0x100, data);
                break;

            default:
                super.setReg(addr, data);
                break;
            }
        }

        public int getReg(int addr) {
            return 0;
        }

        public void setVolumeADPCM(int db) {
            db = Math.min(db, 20);
            if (db > -192)
                adpcmVol = (int) (65536.0 * Math.pow(10.0, db / 40.0));
            else
                adpcmVol = 0;

            adpcmVolume = (adpcmVol * adpcmLevel) >> 12;
        }

        /**
         * 音量設定
         */
        public void setVolumeRhythmTotal(int db) {
            db = Math.min(db, 20);
            rhythmTVol = -(db * 2 / 3);
        }

        public void setVolumeRhythm(int index, int db) {
            db = Math.min(db, 20);
            rhythm[index].volume = -(db * 2 / 3);
        }


        public byte[] getADPCMBuffer() {
            return adpcmBuf;
        }

        public int dbgGetOpOut(int c, int s) {
            return ch[c].op[s].dbgOpOut;
        }

        public int dbgGetPGOut(int c, int s) {
            return ch[c].op[s].dbgPgOut;
        }

        public Fmgen.Channel4 dbgGetCh(int c) {
            return ch[c];
        }

        public static class Rhythm {
            // ぱん
            public byte pan;
            // おんりょう
            public byte level;
            // おんりょうせってい
            public int volume;
            // さんぷる
            public int[] sample;
            // さいず
            public int size;
            // いち
            public int pos;
            // すてっぷち
            public int step;
            // さんぷるのれーと
            public int rate;
        }

        /**
         * リズム合成
         */
        private void rhythmMix(int[] buffer, int count) {
            if (rhythmTVol < 128 && rhythm[0].sample != null && ((rhythmKey & 0x3f) != 0)) {
                int limit = count * 2;
                visRtmVolume[0] = 0;
                visRtmVolume[1] = 0;
                for (int i = 0; i < 6; i++) {
                    Rhythm r = rhythm[i];
                    if ((rhythmKey & (1 << i)) != 0 && r.level < 128) {
                        int db = limit(rhythmTl + rhythmTVol + r.level + r.volume, 127, -31);
                        int vol = tlTable[Fmgen.FM_TLPOS + (db << (Fmgen.FM_TLBITS - 7))] >> 4;
                        int maskL = -((r.pan >> 1) & 1);
                        int maskR = -(r.pan & 1);

                        if ((rhythmMask_ & (1 << i)) != 0) {
                            maskL = maskR = 0;
                        }

                        for (int dest = 0; dest < limit && r.pos < r.size; dest += 2) {
                            int sample = (r.sample[r.pos / 1024] * vol) >> 12;
                            r.pos += r.step;
                            buffer[dest + 0] += sample & maskL;
                            buffer[dest + 1] += sample & maskR;
                            visRtmVolume[0] += sample & maskL;
                            visRtmVolume[1] += sample & maskR;
                        }
                    }
                }
            }
        }

        // リズム音源関係
        private Rhythm[] rhythm = new Rhythm[] {new Rhythm(), new Rhythm(), new Rhythm(), new Rhythm(), new Rhythm(), new Rhythm()};

        // リズム全体の音量
        private byte rhythmTl;
        private int rhythmTVol;
        // リズムのキー
        private byte rhythmKey;
        private byte chipId;
    }

    /** YM2610/B(OPNB) */
    public static class OPNB extends OPNABase {
        /**
         * 構築
         */
        public OPNB() {
            adpcmaBuf = null;
            adpcmASize = 0;
            for (int i = 0; i < 6; i++) {
                adpcmA[i].reset();
            }
            adpcmATl = 0;
            adpcmAKey = 0;
            adpcmATVol = 0;
            adpcmMask = 0;
            adpcmNotice = 0x8000;
            granuality = -1;
            csmCh = ch[2];

            initADPCMATable();
        }

        /**
         * 初期化
         */
        public boolean init(int c, int r, boolean ipFlag/* = false*/,
                            byte[] _adpcmA/* = null*/, int _adpcmASize/* = 0*/,
                            byte[] _adpcmB/* = null*/, int _adpcmBSize/* = 0*/) {
            int i;
            if (!setRate(c, r, ipFlag))
                return false;
            if (!super.init(c, r, ipFlag))
                return false;

            setAdpcmA(_adpcmA, _adpcmASize);
            setAdpcmB(_adpcmB, _adpcmBSize);

            this.reset();

            setVolumeFM(0);
            setVolumePSG(0);
            setVolumeADPCMB(0);
            setVolumeADPCMATotal(0);
            for (i = 0; i < 6; i++)
                setVolumeADPCMA(0, 0);
            setChannelMask(0);
            return true;
        }

        public void setAdpcmA(byte[] _adpcmA, int _adpcmASize) {
            adpcmaBuf = _adpcmA;
            adpcmASize = _adpcmASize;
        }

        public void setAdpcmB(byte[] _adpcmB, int _adpcmBSize) {
            adpcmBuf = _adpcmB;

            for (int i = 0; i <= 24; i++) { // max 16M bytes
                if (_adpcmBSize <= (1 << i)) {
                    adpcmMask = (1 << i) - 1;
                    break;
                }
            }

            // adpcmmask = _adpcmBSize - 1;
            limitAddr = adpcmMask;
        }

        /**
         * サンプリングレート変更
         */
        public boolean setRate(int c, int r, boolean ipflag/* = false*/) {
            if (!super.setRate(c, r, ipflag))
                return false;

            adpcmAStep = (int) ((double) (c) / 54 * 8192 / r);
            return true;
        }

        /**
         * 合成
         * @param buffer  合成先
         * @param nsamples 合成サンプル数
         */
        public void mix(int[] buffer, int nsamples) {
            fmMix(buffer, nsamples);
            psg.mix(buffer, nsamples);
            adpcmBMix(buffer, nsamples);
            adpcmAMix(buffer, nsamples);
        }

        /**
         * リセット
         */
        public void reset() {
            super.reset();

            stMask = ~(int) 0;
            adpcmAKey = 0;
            reg29 = ~(int) 0;

            for (int i = 0; i < 6; i++) {
                adpcmA[i].reset();
            }
        }

        /**
         * レジスタアレイにデータを設定
         */
        public void setReg(int addr, int data) {
            addr &= 0x1ff;

            switch (addr) {
            // omitted registers
            case 0x29:
            case 0x2d:
            case 0x2e:
            case 0x2f:
                break;

            // ADPCM A----
            case 0x100: // DM/KEYON
                if ((data & 0x80) == 0) { // KEY ON
                    adpcmAKey |= (byte) (data & 0x3f);
                    for (int c = 0; c < 6; c++) {
                        if ((data & (1 << c)) != 0) {
                            resetStatus(0x100 << c);
                            adpcmA[c].pos = adpcmA[c].start;
                            //     adpcma[c].step = 0x10000 - adpcma[c].step;
                            adpcmA[c].step = 0;
                            adpcmA[c].adpcmX = 0;
                            adpcmA[c].adpcmD = 0;
                            adpcmA[c].nibble = 0;
                        }
                    }
                } else { // DUMP
                    adpcmAKey &= (byte) ~data;
                }
                break;

            case 0x101:
                adpcmATl = (byte) (~data & 63);
                break;

            case 0x108:
            case 0x109:
            case 0x10a:
            case 0x10b:
            case 0x10c:
            case 0x10d:
                adpcmA[addr & 7].pan = (byte) ((data >> 6) & 3);
                adpcmA[addr & 7].level = (byte) (~data & 31);
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
                adpcmAReg[addr - 0x110] = (byte) data;
                adpcmA[addr & 7].pos = adpcmA[addr & 7].start =
                        (adpcmAReg[(addr & 7) + 8] * 256 + adpcmAReg[addr & 7]) << 9;
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
                adpcmAReg[addr - 0x110] = (byte) data;
                adpcmA[addr & 7].stop =
                        (adpcmAReg[(addr & 7) + 24] * 256 + adpcmAReg[(addr & 7) + 16] + 1) << 9;
                break;

            // AdpcmB
            case 0x10:
                if ((data & 0x80) != 0 && !adpcmPlay) {
                    adpcmPlay = true;
                    memAddr = startAddr;
                    adpcmX = 0;
                    adpcmD = 127;
                    adplC = 0;
                }
                if ((data & 1) != 0)
                    adpcmPlay = false;
                control1 = (byte) (data & 0x91);
                break;


            case 0x11: // Controller Register 2
                control2 = (byte) (data & 0xc0);
                break;

            case 0x12: // Start Address L
            case 0x13: // Start Address H
                adpcmReg[addr - 0x12 + 0] = (byte) data;
                startAddr = (adpcmReg[1] * 256 + adpcmReg[0]) << 9;
                memAddr = startAddr;
                break;

            case 0x14: // Stop Address L
            case 0x15: // Stop Address H
                adpcmReg[addr - 0x14 + 2] = (byte) data;
                stopAddr = (adpcmReg[3] * 256 + adpcmReg[2] + 1) << 9;
                //System.err.printf("  stopaddr %.6x", stopaddr);
                break;

            case 0x19: // delta-N L
            case 0x1a: // delta-N H
                adpcmReg[addr - 0x19 + 4] = (byte) data;
                deltaN = adpcmReg[5] * 256 + adpcmReg[4];
                deltaN = Math.max(256, deltaN);
                adplD = deltaN * adplBase >> 16;
                break;

            case 0x1b: // Level Controller
                adpcmLevel = (byte) data;
                adpcmVolume = (adpcmVol * adpcmLevel) >> 12;
                break;

            case 0x1c: // Flag Controller
                stMask = ~((data & 0xbf) << 8);
                status &= stMask;
                updateStatus();
                break;

            default:
                super.setReg(addr, data);
                break;
            }
            // System.err.printf("\n");
        }

        /**
         * レジスタ取得
         */
        public int getReg(int addr) {
            if (addr < 0x10)
                return psg.getReg(addr);

            return 0;
        }

        /**
         * 拡張ステータスを読みこむ
         */
        public int readStatusEx() {
            return (status & stMask) >> 8;
        }

        public void setVolumeADPCMATotal(int db) {
            db = Math.min(db, 20);
            adpcmATVol = -(db * 2 / 3);
        }

        public void setVolumeADPCMA(int index, int db) {
            db = Math.min(db, 20);
            adpcmA[index].volume = -(db * 2 / 3);
        }

        public void setVolumeADPCMB(int db) {
            db = Math.min(db, 20);
            if (db > -192)
                adpcmVol = (int) (65536.0 * Math.pow(10, db / 40.0));
            else
                adpcmVol = 0;
        }

        //  void SetChannelMask(int mask);

        public static class ADPCMA {
            // ぱん
            public byte pan;
            // おんりょう
            public byte level;
            // おんりょうせってい
            public int volume;
            // いち
            public int pos;
            // すてっぷち
            public int step;

            // 開始
            public int start;
            // 終了
            public int stop;
            // 次の 4 bit
            public int nibble;
            // 変換用
            public short adpcmX;
            // 変換用
            public short adpcmD;

            public void reset() {
                this.pan = 0;
                this.level = 0;
                this.volume = 0;
                this.pos = 0;
                this.step = 0;
                this.volume = 0;
                this.start = 0;
                this.stop = 0;
                this.adpcmX = 0;
                this.adpcmD = 0;
            }
        }

        private int decodeADPCMASample(int a) {
            return -1;
        }

        static final int[] decodeTableA1 = new int[] {
                -1 * 16, -1 * 16, -1 * 16, -1 * 16, 2 * 16, 5 * 16, 7 * 16, 9 * 16,
                -1 * 16, -1 * 16, -1 * 16, -1 * 16, 2 * 16, 5 * 16, 7 * 16, 9 * 16
        };

        /**
         * AdpcmA 合成
         */
        public void adpcmAMix(int[] buffer, int count) {

            if (adpcmATVol < 128 && (adpcmAKey & 0x3f) != 0) {
                //Sample* limit = buffer + count * 2;
                int limit = count * 2;
                for (int i = 0; i < 6; i++) {
                    ADPCMA r = adpcmA[i];
                    if ((adpcmAKey & (1 << i)) != 0 && r.level < 128) {
                        int maskl = (r.pan & 2) != 0 ? -1 : 0;
                        int maskr = (r.pan & 1) != 0 ? -1 : 0;
                        if ((rhythmMask_ & (1 << i)) != 0) {
                            maskl = maskr = 0;
                        }

                        int db = limit(adpcmATl + adpcmATVol + r.level + r.volume, 127, -31);
                        int vol = tlTable[Fmgen.FM_TLPOS + (db << (Fmgen.FM_TLBITS - 7))] >> 4;

                        //Sample* dest = buffer;
                        int dest = 0;
                        for (; dest < limit; dest += 2) {
                            r.step += adpcmAStep;
                            if (r.pos >= r.stop) {
                                setStatus(0x100 << i);
                                adpcmAKey &= (byte) ~(1 << i);
                                break;
                            }

                            for (; r.step > 0x10000; r.step -= 0x10000) {
                                int data;
                                if ((r.pos & 1) == 0) {
                                    r.nibble = adpcmaBuf[r.pos >> 1];
                                    data = r.nibble >> 4;
                                } else {
                                    data = r.nibble & 0x0f;
                                }
                                r.pos++;

                                r.adpcmX += jedi_table[r.adpcmD + data];
                                r.adpcmX = (short) limit(r.adpcmX, 2048 * 3 - 1, -2048 * 3);
                                r.adpcmD += (short) decodeTableA1[data];
                                r.adpcmD = (short) limit(r.adpcmD, 48 * 16, 0);
                            }
                            int sample = (r.adpcmX * vol) >> 10;
                            buffer[dest + 0] += sample & maskl;
                            buffer[dest + 1] += sample & maskr;
                            visRtmVolume[0] = sample & maskl;
                            visRtmVolume[1] = sample & maskr;
                        }
                    }
                }
            }
        }

        static final byte[] table2 = new byte[] {
                1, 3, 5, 7, 9, 11, 13, 15,
                -1, -3, -5, -7, -9, -11, -13, -15,
        };


        public static void initADPCMATable() {
            for (int i = 0; i <= 48; i++) {
                int s = (int) (16.0 * Math.pow(1.1, i) * 3);
                for (int j = 0; j < 16; j++) {
                    jedi_table[i * 16 + j] = (short) (s * table2[j] / 8);
                }
            }
        }

        // AdpcmA 関係

        // AdpcmA ROM
        public byte[] adpcmaBuf;
        public int adpcmASize;
        public ADPCMA[] adpcmA = new ADPCMA[] {
                new ADPCMA(), new ADPCMA(), new ADPCMA(), new ADPCMA(), new ADPCMA(), new ADPCMA()
        };
        // AdpcmA 全体の音量
        public byte adpcmATl;
        public int adpcmATVol;
        // AdpcmA のキー
        public byte adpcmAKey;
        public int adpcmAStep;
        public byte[] adpcmAReg = new byte[32];

        public static short[] jedi_table = new short[(48 + 1) * 16];

        //public new Fmgen.Channel4[] ch = new Fmgen.Channel4[6];
    }

    /** Ym2612/3438(OPN2) */
    static class OPN2 extends OPNBase {

        public boolean init(int c, int r, boolean f /*= false*/, String s/* = null*/) {
            return false;
        }

        public boolean setRate(int c, int r, boolean f) {
            return false;
        }

        public void reset() {
        }

        public void mix(int[] buffer, int nsamples) {
        }

        public void setReg(int addr, int data) {
        }

        public int getReg(int addr) {
            return 0;
        }

        public int readStatus() {
            return status & 0x03;
        }

        public int readStatusEx() {
            return 0xff;
        }

        public void setChannelMask(int mask) {
        }

        private void intr(boolean f) {
        }

        public void setStatus(int bit) {
        }

        public void resetStatus(int bit) {
        }

        private int[] fnum = new int[3];
        private int[] fnum3 = new int[3];
        private byte[] fnum2 = new byte[6];

        // 線形補間用ワーク
        //private int mixc, mixc1;

        private Fmgen.Channel4[] ch = new Fmgen.Channel4[3];
    }
}
