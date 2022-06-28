package mdsound.fmgen;

import java.util.Random;

import static mdsound.fmgen.Fmgen.limit;


/**
 * OPM に良く似た(?)音を生成する音源ユニット
 *
 * interface:
 * bool Init(uint clock, uint rate, bool);
 *     初期化．このクラスを使用する前にかならず呼んでおくこと．
 *     注意: 線形補完モードは廃止されました
 *
 *     clock:    OPM のクロック周波数(Hz)
 *
 *     rate:    生成する PCM の標本周波数(Hz)
 *
 *
 *     返値    初期化に成功すれば true
 *
 * bool SetRate(uint clock, uint rate, bool)
 *     クロックや PCM レートを変更する
 *     引数等は Init と同様．
 *
 * void Mix(Sample* dest, int nsamples)
 *     Stereo PCM データを nsamples 分合成し， dest で始まる配列に
 *     加える(加算する)
 *     ・dest には sample*2 個分の領域が必要
 *     ・格納形式は L, R, L, R... となる．
 *     ・あくまで加算なので，あらかじめ配列をゼロクリアする必要がある
 *     ・FM_SAMPLETYPE が short 型の場合クリッピングが行われる.
 *     ・この関数は音源内部のタイマーとは独立している．
 *       Timer は Count と GetNextEvent で操作する必要がある．
 *
 * void Reset()
 *     音源をリセット(初期化)する
 *
 * void SetReg(uint reg, uint data)
 *     音源のレジスタ reg に data を書き込む
 *
 * uint ReadStatus()
 *     音源のステータスレジスタを読み出す
 *     busy フラグは常に 0
 *
 * bool Count(uint32 t)
 *     音源のタイマーを t [10^(-6) 秒] 進める．
 *     音源の内部状態に変化があった時(timer オーバーフロー)
 *     true を返す
 *
 * uint32 GetNextEvent()
 *     音源のタイマーのどちらかがオーバーフローするまでに必要な
 *     時間[μ秒]を返す
 *     タイマーが停止している場合は 0 を返す．
 *
 * void SetVolume(int db)
 *     各音源の音量を＋－方向に調節する．標準値は 0.
 *     単位は約 1/2 dB，有効範囲の上限は 20 (10dB)
 *
 * 仮想関数:
 * virtual void Intr(bool irq)
 *     IRQ 出力に変化があった場合呼ばれる．
 *     irq = true:  IRQ 要求が発生
 *     irq = false: IRQ 要求が消える
 */
public class OPM extends Timer {
    private static final int OPM_LFOENTS = 512;
    private int fmVolume;

    private int clock;
    private int rate;
    private int pcmRate;

    private int pmd;
    private int amd;

    private int lfoCount;
    private int lfoCountDiff;
    private int lfoStep;
    private int lfoCountPrev;

    private int lfoWaveForm;
    private int rateRatio;
    private int noise;
    private int noiseCount;
    private int noiseDelta;

    //private boolean interpolation;
    private byte lfoFreq;
    private byte status;
    private byte reg01;

    private byte[] kc = new byte[8];
    private byte[] kf = new byte[8];
    private byte[] pan = new byte[8];

    private Fmgen.Channel4[] ch = new Fmgen.Channel4[8];
    private Fmgen.Channel4.Chip chip = new Fmgen.Channel4.Chip();

    private static int[][] amTable = new int[][] {new int[OPM_LFOENTS], new int[OPM_LFOENTS], new int[OPM_LFOENTS], new int[OPM_LFOENTS]};
    private static int[][] pmTable = new int[][] {new int[OPM_LFOENTS], new int[OPM_LFOENTS], new int[OPM_LFOENTS], new int[OPM_LFOENTS]};

    private static Random rand = new Random();

    public OPM() {
        lfoCount = 0;
        lfoCountPrev = ~(int) (0);
        for (int i = 0; i < 8; i++) {
            ch[i] = new Fmgen.Channel4();
            ch[i].setChip(chip);
            ch[i].setType(Fmgen.Channel4.Chip.OpType.typeM);
        }
    }

    public boolean init(int c, int rf, boolean ip/* = false*/) {
        if (!setRate(c, rf, ip))
            return false;

        reset();

        setVolume(0);
        setChannelMask(0);
        return true;
    }

    public boolean setRate(int c, int r, boolean ip) {
        clock = c;
        pcmRate = r;
        rate = r;

        rebuildTimeTable();

        return true;
    }

    public void setChannelMask(int mask) {
        for (int i = 0; i < 8; i++)
            ch[i].mute((mask & (1 << i)) != 0);
    }

    public void reset() {
        for (int i = 0x0; i < 0x100; i++) setReg(i, 0);
        setReg(0x19, 0x80);
        super.reset();

        status = 0;
        noise = 12345;
        noiseCount = 0;

        for (int i = 0; i < 8; i++)
            ch[i].reset();
    }

    private void rebuildTimeTable() {
        int fmClock = clock / 64;

        //assert(fmClock < (0x80000000 >> FM_RATIOBITS));
        rateRatio = ((fmClock << Fmgen.FM_RATIOBITS) + rate / 2) / rate;
        setTimerBase(fmClock);

        chip.setRatio(rateRatio);

        //lfo_diff_ =

        //lfoDCount = (16 + (lfoFreq & 15)) << (lfoFreq >> 4);
        //lfoDCount = lfoDCount * rateRatio >> FM_RATIOBITS;
    }

    private void timerA() {
        if ((regTc & 0x80) != 0) {
            for (int i = 0; i < 8; i++) {
                ch[i].keyControl(0);
                ch[i].keyControl(0xf);
            }
        }
    }

    public void setVolume(int db) {
        db = Math.min(db, 20);
        if (db > -192)
            fmVolume = (int) (16384.0 * Math.pow(10, db / 40.0));
        else
            fmVolume = 0;
    }

    public void setStatus(int bits) {
        if ((status & (byte) bits) == 0) {
            status |= (byte) bits;
            intr(true);
        }
    }

    public void resetStatus(int bits) {
        if ((status & (byte) bits) != 0) {
            status &= (byte) ~bits;
            if (status == 0)
                intr(false);
        }
    }

    public void setLPFCutoff(int freq) {
    }

    public void setReg(int addr, int data) {
        if (addr >= 0x100)
            return;

        int c = addr & 7;
        switch (addr & 0xff) {
        case 0x01: // TEST (Lfo restart)
            if ((data & 2) != 0) {
                lfoCount = 0;
                lfoCountPrev = ~(int) 0;
            }
            reg01 = (byte) data;
            break;

        case 0x08: // KEYON
            if ((regTc & 0x80) == 0)
                ch[data & 7].keyControl(data >> 3);
            else {
                c = data & 7;
                if ((data & 0x08) == 0) ch[c].op[0].keyOff();
                if ((data & 0x10) == 0) ch[c].op[1].keyOff();
                if ((data & 0x20) == 0) ch[c].op[2].keyOff();
                if ((data & 0x40) == 0) ch[c].op[3].keyOff();
            }
            break;

        case 0x10:
        case 0x11: // CLKA1, CLKA2
            setTimerA(addr, data);
            break;

        case 0x12: // CLKB
            setTimerB(data);
            break;

        case 0x14: // CSM, TIMER
            setTimerControl(data);
            break;

        case 0x18: // LFRQ(Lfo freq)
            lfoFreq = (byte) data;

            //assert(16 - 4 - FM_RATIOBITS >= 0);
            lfoCountDiff = rateRatio
                    * ((16 + (lfoFreq & 15)) << (16 - 4 - Fmgen.FM_RATIOBITS))
                    / (1 << (15 - (lfoFreq >> 4)));

            break;

        case 0x19: // PMD/AMD
            if ((data & 0x80) != 0) {
                pmd = data & 0x7f;
            } else {
                amd = data & 0x7f;
            }
            break;

        case 0x1b: // CT, W(Lfo waveform)
            lfoWaveForm = data & 3;
            break;

        // RL, FB, Connect
        case 0x20:
        case 0x21:
        case 0x22:
        case 0x23:
        case 0x24:
        case 0x25:
        case 0x26:
        case 0x27:
            ch[c].setFB((data >> 3) & 7);
            ch[c].setAlgorithm(data & 7);
            pan[c] = (byte) ((data >> 6) & 3);
            break;

        // KC
        case 0x28:
        case 0x29:
        case 0x2a:
        case 0x2b:
        case 0x2c:
        case 0x2d:
        case 0x2e:
        case 0x2f:
            kc[c] = (byte) data;
            ch[c].setKCKF(kc[c], kf[c]);
            break;

        // KF
        case 0x30:
        case 0x31:
        case 0x32:
        case 0x33:
        case 0x34:
        case 0x35:
        case 0x36:
        case 0x37:
            kf[c] = (byte) (data >> 2);
            ch[c].setKCKF(kc[c], kf[c]);
            break;

        // PMS, AMS
        case 0x38:
        case 0x39:
        case 0x3a:
        case 0x3b:
        case 0x3c:
        case 0x3d:
        case 0x3e:
        case 0x3f:
            ch[c].setMS((data << 4) | (data >> 4));
            break;

        case 0x0f: // NE/NFRQ (noise)
            noiseDelta = data;
            noiseCount = 0;
            break;

        default:
            if (addr >= 0x40)
                setParameter(addr, data);
            break;
        }
    }

    private void setParameter(int addr, int data) {
        final byte[] slTable = new byte[] {
                0, 4, 8, 12, 16, 20, 24, 28,
                32, 36, 40, 44, 48, 52, 56, 124
        };
        final byte[] slotTable = new byte[] {0, 2, 1, 3};

        int slot = slotTable[(addr >> 3) & 3];
        Fmgen.Channel4.Operator op = ch[addr & 7].op[slot];

        switch ((addr >> 5) & 7) {
        case 2: // 40-5F DT1/MULTI
            op.setDT((data >> 4) & 0x07);
            op.setMULTI(data & 0x0f);
            break;

        case 3: // 60-7F TL
            op.setTL(data & 0x7f, (regTc & 0x80) != 0);
            break;

        case 4: // 80-9F KS/AR
            op.setKS((data >> 6) & 3);
            op.setAR((data & 0x1f) * 2);
            break;

        case 5: // A0-BF DR/AMON(D1R/AMS-EN)
            op.setDR((data & 0x1f) * 2);
            op.setAmOn((data & 0x80) != 0);
            break;

        case 6: // C0-DF SR(D2R), DT2
            op.setSR((data & 0x1f) * 2);
            op.setDT2((data >> 6) & 3);
            break;

        case 7: // E0-FF SL(D1L)/RR
            op.setSL(slTable[(data >> 4) & 15]);
            op.setRR((data & 0x0f) * 4 + 2);
            break;
        }
    }

    static {
        for (int type = 0; type < 4; type++) {
            int r = 0;
            for (int c = 0; c < OPM_LFOENTS; c++) {
                int a = 0, p = 0;

                switch (type) {
                case 0:
                    p = (((c + 0x100) & 0x1ff) / 2) - 0x80;
                    a = 0xff - c / 2;
                    break;

                case 1:
                    a = c < 0x100 ? 0xff : 0;
                    p = c < 0x100 ? 0x7f : -0x80;
                    break;

                case 2:
                    p = (c + 0x80) & 0x1ff;
                    p = p < 0x100 ? (p - 0x80) : (0x17f - p);
                    a = c < 0x100 ? (0xff - c) : (c - 0x100);
                    break;

                case 3:
                    if ((c & 3) == 0)
                        r = (rand.nextInt() / 17) & 0xff;
                    a = r;
                    p = r - 0x80;
                    break;
                }

                amTable[type][c] = a;
                pmTable[type][c] = -p - 1;
                // System.err.printf("%d ", p);
            }
        }
    }

    private void lfo() {
        if (lfoWaveForm != 3) {
            // if ((lfo_count_ ^ lfo_count_prev_) & ~((1 << 15) - 1))
            {
                int c = (lfoCount >> 15) & 0x1fe;
                // System.err.printf("%.8x %.2x\n", lfo_count_, c);
                chip.setPML(pmTable[lfoWaveForm][c] * pmd / 128 + 0x80);
                chip.setAML(amTable[lfoWaveForm][c] * amd / 128);
            }
        } else {
            if (((lfoCount ^ lfoCountPrev) & ~((1 << 17) - 1)) != 0) {
                int c = (rand.nextInt() / 17) & 0xff;
                chip.setPML((c - 0x80) * pmd / 128 + 0x80);
                chip.setAML(c * amd / 128);
            }
        }
        lfoCountPrev = lfoCount;
        lfoStep++;
        if ((lfoStep & 7) == 0) {
            lfoCount += lfoCountDiff;
        }
    }

    private int noise() {
        noiseCount += 2 * rateRatio;
        if (noiseCount >= (32 << Fmgen.FM_RATIOBITS)) {
            int n = 32 - (noiseDelta & 0x1f);
            if (n == 1)
                n = 2;

            noiseCount = noiseCount - (n << Fmgen.FM_RATIOBITS);
            if ((noiseDelta & 0x1f) == 0x1f)
                noiseCount -= Fmgen.FM_RATIOBITS;
            noise = (noise >> 1) ^ ((noise & 1) != 0 ? 0x8408 : 0);
        }
        return noise;
    }

    private void mixSub(int activeCh, int[] iDest, int[] ibuf) {
        if ((activeCh & 0x4000) != 0) ibuf[iDest[0]] = ch[0].calc();
        if ((activeCh & 0x1000) != 0) ibuf[iDest[1]] += ch[1].calc();
        if ((activeCh & 0x0400) != 0) ibuf[iDest[2]] += ch[2].calc();
        if ((activeCh & 0x0100) != 0) ibuf[iDest[3]] += ch[3].calc();
        if ((activeCh & 0x0040) != 0) ibuf[iDest[4]] += ch[4].calc();
        if ((activeCh & 0x0010) != 0) ibuf[iDest[5]] += ch[5].calc();
        if ((activeCh & 0x0004) != 0) ibuf[iDest[6]] += ch[6].calc();
        if ((activeCh & 0x0001) != 0) {
            if ((noiseDelta & 0x80) != 0)
                ibuf[iDest[7]] += ch[7].calcN(noise());
            else
                ibuf[iDest[7]] += ch[7].calc();
        }
    }

    private void mixSubL(int activeCh, int[] iDest, int[] iBuf) {
        if ((activeCh & 0x4000) != 0) iBuf[iDest[0]] = ch[0].calcL();
        if ((activeCh & 0x1000) != 0) iBuf[iDest[1]] += ch[1].calcL();
        if ((activeCh & 0x0400) != 0) iBuf[iDest[2]] += ch[2].calcL();
        if ((activeCh & 0x0100) != 0) iBuf[iDest[3]] += ch[3].calcL();
        if ((activeCh & 0x0040) != 0) iBuf[iDest[4]] += ch[4].calcL();
        if ((activeCh & 0x0010) != 0) iBuf[iDest[5]] += ch[5].calcL();
        if ((activeCh & 0x0004) != 0) iBuf[iDest[6]] += ch[6].calcL();
        if ((activeCh & 0x0001) != 0) {
            if ((noiseDelta & 0x80) != 0)
                iBuf[iDest[7]] += ch[7].calcLN(noise());
            else
                iBuf[iDest[7]] += ch[7].calcL();
        }
    }

    public void mix(int[] buffer, int nsamples) {
        // odd bits - active, even bits - Lfo
        int activeCh = 0;
        for (int i = 0; i < 8; i++) {
            activeCh = activeCh << 2;
            int pre = ch[i].prepare();
            activeCh = (activeCh | pre);
        }

        if ((activeCh & 0x5555) != 0) {
            // LFO 波形初期化ビット = 1 ならば LFO はかからない?
            if ((reg01 & 0x02) != 0)
                activeCh &= 0x5555;

            // Mix
            int[] iBuf = new int[8];
            int[] iDest = new int[8];
            iDest[0] = pan[0];
            iDest[1] = pan[1];
            iDest[2] = pan[2];
            iDest[3] = pan[3];
            iDest[4] = pan[4];
            iDest[5] = pan[5];
            iDest[6] = pan[6];
            iDest[7] = pan[7];

            int limit = nsamples * 2;
            for (int dest = 0; dest < limit; dest += 2) {
                iBuf[1] = iBuf[2] = iBuf[3] = 0;
                if ((activeCh & 0xaaaa) != 0) {
                    lfo();
                    mixSubL(activeCh, iDest, iBuf);
                } else {
                    lfo();
                    mixSub(activeCh, iDest, iBuf);
                }

                buffer[dest + 0] += ((limit((iBuf[1] + iBuf[3]), 0xffff, -0x10000) * fmVolume) >> 14);
                buffer[dest + 1] += ((limit((iBuf[2] + iBuf[3]), 0xffff, -0x10000) * fmVolume) >> 14);
            }
        }
    }

    public int getReg(int addr) {
        return 0;
    }

    public int readStatus() {
        return status & 0x03;
    }

    private void intr(boolean f) {
    }

    public int dbgGetOpOut(int c, int s) {
        return ch[c].op[s].dbgOpOut;
    }

    public Fmgen.Channel4 dbgGetCh(int c) {
        return ch[c];
    }
}
