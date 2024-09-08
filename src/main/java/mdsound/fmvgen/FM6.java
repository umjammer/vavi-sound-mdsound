package mdsound.fmvgen;

import mdsound.fmgen.Fmgen;
import mdsound.fmvgen.Fmvgen.Channel4;
import mdsound.fmvgen.Fmvgen.Effects;
import mdsound.fmvgen.effect.ReversePhase;


public class FM6 {
    public OPNA2 parent = null;

    public int fmvolume;
    protected Channel4 csmch;
    protected int[] fnum = new int[6];
    protected int[] fnum3 = new int[3];
    public Channel4[] ch = new Channel4[6];

    protected byte[] fnum2 = new byte[9];

    protected byte reg22;
    protected int reg29; // OPNA only?
    protected byte[] pan = new byte[6];
    //protected float[] panTable = new float[4] { 1.0f, 0.5012f, 0.2512f, 0.1000f };
    protected float[] panL = new float[6];
    protected float[] panR = new float[6];
    //protected boolean[] ac = new boolean[6];
    protected int lfoCount;
    protected int lfodCount;
    public int[] visVolume = new int[] {0, 0};
    protected byte regtc;
    public Fmgen.Channel4.Chip chip;
    public int waveType = 0;
    public int waveCh = 0;
    public int waveCounter = 0;

    protected int[] lfoTable = new int[8];
    private Effects effects;
    private int efcStartCh;
    private int num;

    public FM6(int n, Fmvgen.Effects effects, int efcStartCh) {
        this.num = n;
        this.effects = effects;
        this.efcStartCh = efcStartCh;

        chip = new Fmgen.Channel4.Chip();

        for (int i = 0; i < 6; i++) {
            ch[i] = new Channel4(i + n * 6);
            ch[i].setChip(chip);
            ch[i].setType(Fmvgen.OpType.typeN);
        }

        csmch = ch[2];
    }

    /**
     * レジスタアレイにデータを設定
     */
    public void setReg(int addr, int data) {
        if (addr < 0x20) return;
        if (addr >= 0x100 && addr < 0x120) return;

        int c = addr & 3;
        int modified;

        switch (addr) {

        // Timer
        case 0x24:
        case 0x25:
            parent.setTimerA(addr, data);
            break;

        case 0x26:
            parent.setTimerB(data);
            break;

        case 0x27:
            parent.setTimerControl(data);
            break;

        // Misc
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

        case 0x2a:
            break;

        // WaveType
        case 0x2b:
            waveType = data & 0x3;
            waveCh = (data >> 4) & 0xf;
            waveCh = Math.max(Math.min(waveCh, 11), 0);
            waveCounter = 0;
            if ((data & 0x4) != 0) Fmvgen.waveReset(waveCh, waveType);
            break;

        // Write WaveData
        case 0x2c:

            int cnt = waveCounter / 2;
            int d = waveCounter % 2;

            int s;
            if (d == 0) {
                s = (byte) data;
            } else {
                s = ((Fmvgen.sineTable[waveCh][waveType][cnt] & 0xff) | ((data & 0x1f) << 8));
            }

            Fmvgen.sineTable[waveCh][waveType][cnt] = s;
            waveCounter++;

            if (Fmvgen.FM_OPSINENTS * 2 <= waveCounter) waveCounter = 0;
            break;

        // Prescaler
        case 0x2d:
        case 0x2e:
        case 0x2f:
            parent.setPreScaler(addr - 0x2d);
            break;

        // F-Number
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
            panL[c] = OPNA2.panTable[(data >> 6) & 3];
            break;
        case 0xa4:
        case 0xa5:
        case 0xa6:
            fnum2[c] = (byte) (data);
            panL[c] = OPNA2.panTable[(data >> 6) & 3];
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

        case 0x1ac:
        case 0x1ad:
        case 0x1ae:
            c += 3;
            break;

        // Algorithm

        case 0x1b0:
        case 0x1b1:
        case 0x1b2:
            c += 3;
            ch[c].setFB((data >> 3) & 7);
            ch[c].setAlgorithm(data & 7);
            panR[c] = OPNA2.panTable[(data >> 6) & 3];
            break;
        case 0xb0:
        case 0xb1:
        case 0xb2:
            ch[c].setFB((data >> 3) & 7);
            ch[c].setAlgorithm(data & 7);
            panR[c] = OPNA2.panTable[(data >> 6) & 3];
            break;

        case 0x1b4:
        case 0x1b5:
        case 0x1b6:
            c += 3;
            pan[c] = (byte) ((data >> 6) & 3);
            ch[c].setMS(data);
            ch[c].setAC((data & 0x08) != 0);
            break;
        case 0xb4:
        case 0xb5:
        case 0xb6:
            pan[c] = (byte) ((data >> 6) & 3);
            ch[c].setMS(data);
            ch[c].setAC((data & 0x08) != 0);
            break;

        // LFO
        case 0x22:
            modified = reg22 ^ data;
            reg22 = (byte) data;
            if ((modified & 0x8) != 0)
                lfoCount = 0;
            lfodCount = (reg22 & 8) != 0 ? lfoTable[reg22 & 7] : 0;
            break;

        // 音色
        default:
            if (c < 3) {
                if ((addr & 0x100) != 0)
                    c += 3;
                setParameter(ch[c], addr, data);
            }
            break;
        }
    }

    protected void setParameter(Fmvgen.Channel4 ch, int addr, int data) {
        int[] slotTable = new int[] {0, 2, 1, 3};
        byte[] slTable = new byte[] {
                0, 4, 8, 12, 16, 20, 24, 28,
                32, 36, 40, 44, 48, 52, 56, 124
        };

        if ((addr & 3) < 3) {
            int slot = slotTable[(addr >> 2) & 3];
            Fmvgen.Operator op = ch.op[slot];

            switch ((addr >> 4) & 15) {
            case 3: // 30-3E DT/MULTI
                op.setDT((data >> 4) & 0x07);
                op.setMULTI(data & 0x0f);
                op.setWaveTypeL((byte) (data >> 7));
                break;

            case 4: // 40-4E TL
                op.setTL(data & 0x7f, ((regtc & 0x80) != 0) && (csmch == ch));
                op.setWaveTypeH((byte) (data >> 7));
                break;

            case 5: // 50-5E KS/AR
                op.setKS((data >> 6) & 3);
                op.setPhaseReset(data & 0x20);
                op.setAR((data & 0x1f) * 2);
                break;

            case 6: // 60-6E DR/AMON
                op.setDR((data & 0x1f) * 2);
                op.setAMON((data & 0x80) != 0);
                op.setDT2((data & 0x60) >> 5);
                break;

            case 7: // 70-7E SR
                op.setSR((data & 0x1f) * 2);
                op.setFB((data >> 5) & 7);
                break;

            case 8: // 80-8E SL/RR
                op.setSL(slTable[(data >> 4) & 15]);
                op.setRR((data & 0x0f) * 4 + 2);
                break;

            case 9: // 90-9E SSG-EC
                op.setSSGEC(data & 0x0f);
                op.setALGLink(data >> 4);
                ch.buildAlg();
                break;
            }
        }
    }

    public void mix(int[] buffer, int nsamples, byte regtc) {
        if (fmvolume <= 0) return;

        this.regtc = regtc;
        // 準備
        // Set F-Number
        if ((regtc & 0xc0) == 0)
            csmch.setFNum(fnum[2]);// csmch - ch]);
        else {
            // 効果音モード
            csmch.op[0].setFNum(fnum3[1]);
            csmch.op[1].setFNum(fnum3[2]);
            csmch.op[2].setFNum(fnum3[0]);
            csmch.op[3].setFNum(fnum[2]);
        }

        int act = (((ch[2].prepare() << 2) | ch[1].prepare()) << 2) | ch[0].prepare();
        if ((reg29 & 0x80) != 0)
            act |= (ch[3].prepare() | ((ch[4].prepare() | (ch[5].prepare() << 2)) << 2)) << 6;
        if ((reg22 & 0x08) == 0)
            act &= 0x555;

        if ((act & 0x555) == 0) return;

        mix6(buffer, nsamples, act);

    }

    private int[] iBuf = new int[4];
    private int[] iDest = new int[6];

    protected void mix6(int[] buffer, int nsamples, int activech) {

        // Mix
        iDest[0] = pan[0];
        iDest[1] = pan[1];
        iDest[2] = pan[2];
        iDest[3] = pan[3];
        iDest[4] = pan[4];
        iDest[5] = pan[5];

        int limit = nsamples << 1;
        int v;
        for (int dest = 0; dest < limit; dest += 2) {
            // 0,1 素
            // 2,3 rev
            iBuf[0] = iBuf[1] = iBuf[2] = iBuf[3] = 0;
            if ((activech & 0xaaa) != 0) {
                lfo();
                mixSubSL(activech, iDest, iBuf);
            } else {
                mixSubS(activech, iDest, iBuf);
            }

            v = ((Fmvgen.limit(iBuf[0], 0x7fff, -0x8000) * fmvolume) >> 14);
            buffer[dest + 0] += v;
            visVolume[0] = v;

            v = ((Fmvgen.limit(iBuf[1], 0x7fff, -0x8000) * fmvolume) >> 14);
            buffer[dest + 1] += v;
            visVolume[1] = v;

            int rvL = ((Fmvgen.limit(iBuf[2], 0x7fff, -0x8000) * fmvolume) >> 14);
            int rvR = ((Fmvgen.limit(iBuf[3], 0x7fff, -0x8000) * fmvolume) >> 14);

            effects.reverb.storeDataC(rvL, rvR);
        }
    }

    protected void mixSubS(int activeCh, int[] dest, int[] buf) {
        int v;
        int[] l, r;
        if ((activeCh & 0x001) != 0) {
            v = ch[0].calc();
            l = new int[] { v };
            r = new int[] { v };
            effects.distortion.mix(efcStartCh + 0, l, r);
            effects.chorus.mix(efcStartCh + 0, l, r);
            effects.hpflpf.mix(efcStartCh + 0, l, r);
            effects.compressor.mix(efcStartCh + 0, l, r);
            buf[0] = (int) ((dest[0] >> 1) * l[0] * panL[0]) * ReversePhase.fm[num][0][0];
            buf[1] = (int) ((dest[0] & 0x1) * r[0] * panR[0]) * ReversePhase.fm[num][0][1];
            buf[2] = (int) (buf[0] * effects.reverb.sendLevel[efcStartCh + 0]);
            buf[3] = (int) (buf[1] * effects.reverb.sendLevel[efcStartCh + 0]);
        }

        if ((activeCh & 0x004) != 0) {
            v = ch[1].calc();
            l = new int[] { v };
            r = new int[] { v };
            effects.distortion.mix(efcStartCh + 1, l, r);
            effects.chorus.mix(efcStartCh + 1, l, r);
            effects.hpflpf.mix(efcStartCh + 1, l, r);
            effects.compressor.mix(efcStartCh + 1, l, r);
            l[0] = (int) ((dest[1] >> 1) * l[0] * panL[1]) * ReversePhase.fm[num][1][0];
            r[0] = (int) ((dest[1] & 0x1) * r[0] * panR[1]) * ReversePhase.fm[num][1][1];
            buf[0] += l[0];
            buf[1] += r[0];
            buf[2] += (int) (l[0] * effects.reverb.sendLevel[efcStartCh + 1]);
            buf[3] += (int) (r[0] * effects.reverb.sendLevel[efcStartCh + 1]);
        }

        if ((activeCh & 0x010) != 0) {
            v = ch[2].calc();
            l = new int[] { v };
            r = new int[] { v };
            effects.distortion.mix(efcStartCh + 2, l, r);
            effects.chorus.mix(efcStartCh + 2, l, r);
            effects.hpflpf.mix(efcStartCh + 2, l, r);
            effects.compressor.mix(efcStartCh + 2, l, r);
            l[0] = (int) ((dest[2] >> 1) * l[0] * panL[2]) * ReversePhase.fm[num][2][0];
            r[0] = (int) ((dest[2] & 0x1) * r[0] * panR[2]) * ReversePhase.fm[num][2][1];
            buf[0] += l[0];
            buf[1] += r[0];
            buf[2] += (int) (l[0] * effects.reverb.sendLevel[efcStartCh + 2]);
            buf[3] += (int) (r[0] * effects.reverb.sendLevel[efcStartCh + 2]);
        }

        if ((activeCh & 0x040) != 0) {
            v = ch[3].calc();
            l = new int[] { v };
            r = new int[] { v };
            effects.distortion.mix(efcStartCh + 3, l, r);
            effects.chorus.mix(efcStartCh + 3, l, r);
            effects.hpflpf.mix(efcStartCh + 3, l, r);
            effects.compressor.mix(efcStartCh + 3, l, r);
            l[0] = (int) ((dest[3] >> 1) * l[0] * panL[3]) * ReversePhase.fm[num][3][0];
            r[0] = (int) ((dest[3] & 0x1) * r[0] * panR[3]) * ReversePhase.fm[num][3][1];
            buf[0] += l[0];
            buf[1] += r[0];
            buf[2] += (int) (l[0] * effects.reverb.sendLevel[efcStartCh + 3]);
            buf[3] += (int) (r[0] * effects.reverb.sendLevel[efcStartCh + 3]);
        }

        if ((activeCh & 0x100) != 0) {
            v = ch[4].calc();
            l = new int[] { v };
            r = new int[] { v };
            effects.distortion.mix(efcStartCh + 4, l, r);
            effects.chorus.mix(efcStartCh + 4, l, r);
            effects.hpflpf.mix(efcStartCh + 4, l, r);
            effects.compressor.mix(efcStartCh + 4, l, r);
            l[0] = (int) ((dest[4] >> 1) * l[0] * panL[4]) * ReversePhase.fm[num][4][0];
            r[0] = (int) ((dest[4] & 0x1) * r[0] * panR[4]) * ReversePhase.fm[num][4][1];
            buf[0] += l[0];
            buf[1] += r[0];
            buf[2] += (int) (l[0] * effects.reverb.sendLevel[efcStartCh + 4]);
            buf[3] += (int) (r[0] * effects.reverb.sendLevel[efcStartCh + 4]);
        }

        if ((activeCh & 0x400) != 0) {
            v = ch[5].calc();
            l = new int[] { v };
            r = new int[] { v };
            effects.distortion.mix(efcStartCh + 5, l, r);
            effects.chorus.mix(efcStartCh + 5, l, r);
            effects.hpflpf.mix(efcStartCh + 5, l, r);
            effects.compressor.mix(efcStartCh + 5, l, r);
            l[0] = (int) ((dest[5] >> 1) * l[0] * panL[5]) * ReversePhase.fm[num][5][0];
            r[0] = (int) ((dest[5] & 0x1) * r[0] * panR[5]) * ReversePhase.fm[num][5][1];
            buf[0] += l[0];
            buf[1] += r[0];
            buf[2] += (int) (l[0] * effects.reverb.sendLevel[efcStartCh + 5]);
            buf[3] += (int) (r[0] * effects.reverb.sendLevel[efcStartCh + 5]);
        }
    }

    protected void mixSubSL(int activeCh, int[] dest, int[] buf) {
        int v;
        int[] l, r;
        if ((activeCh & 0x001) != 0) {
            v = ch[0].calcL();
            l = new int[] { v };
            r = new int[] { v };
            effects.distortion.mix(efcStartCh + 0, l, r);
            effects.chorus.mix(efcStartCh + 0, l, r);
            effects.hpflpf.mix(efcStartCh + 0, l, r);
            buf[0] = (int) ((dest[0] >> 1) * l[0] * panL[0]) * ReversePhase.fm[num][0][0];
            buf[1] = (int) ((dest[0] & 0x1) * r[0] * panR[0]) * ReversePhase.fm[num][0][1];
            buf[2] = (int) (buf[0] * effects.reverb.sendLevel[efcStartCh + 0]);
            buf[3] = (int) (buf[1] * effects.reverb.sendLevel[efcStartCh + 0]);
        }
        if ((activeCh & 0x004) != 0) {
            v = ch[1].calcL();
            l = new int[] { v };
            r = new int[] { v };
            effects.distortion.mix(efcStartCh + 1, l, r);
            effects.chorus.mix(efcStartCh + 1, l, r);
            effects.hpflpf.mix(efcStartCh + 1, l, r);
            l[0] = (int) ((dest[1] >> 1) * l[0] * panL[1]) * ReversePhase.fm[num][1][0];
            r[0] = (int) ((dest[1] & 0x1) * r[0] * panR[1]) * ReversePhase.fm[num][1][1];
            buf[0] += l[0];
            buf[1] += r[0];
            buf[2] += (int) (l[0] * effects.reverb.sendLevel[efcStartCh + 1]);
            buf[3] += (int) (r[0] * effects.reverb.sendLevel[efcStartCh + 1]);
        }
        if ((activeCh & 0x010) != 0) {
            v = ch[2].calcL();
            l = new int[] { v };
            r = new int[] { v };
            effects.distortion.mix(efcStartCh + 2, l, r);
            effects.chorus.mix(efcStartCh + 2, l, r);
            effects.hpflpf.mix(efcStartCh + 2, l, r);
            l[0] = (int) ((dest[2] >> 1) * l[0] * panL[2]) * ReversePhase.fm[num][2][0];
            r[0] = (int) ((dest[2] & 0x1) * r[0] * panR[2]) * ReversePhase.fm[num][2][1];
            buf[0] += l[0];
            buf[1] += r[0];
            buf[2] += (int) (l[0] * effects.reverb.sendLevel[efcStartCh + 2]);
            buf[3] += (int) (r[0] * effects.reverb.sendLevel[efcStartCh + 2]);
        }
        if ((activeCh & 0x040) != 0) {
            v = ch[3].calcL();
            l = new int[] { v };
            r = new int[] { v };
            effects.distortion.mix(efcStartCh + 3, l, r);
            effects.chorus.mix(efcStartCh + 3, l, r);
            effects.hpflpf.mix(efcStartCh + 3, l, r);
            l[0] = (int) ((dest[3] >> 1) * l[0] * panL[3]) * ReversePhase.fm[num][3][0];
            r[0] = (int) ((dest[3] & 0x1) * r[0] * panR[3]) * ReversePhase.fm[num][3][1];
            buf[0] += l[0];
            buf[1] += r[0];
            buf[2] += (int) (l[0] * effects.reverb.sendLevel[efcStartCh + 3]);
            buf[3] += (int) (r[0] * effects.reverb.sendLevel[efcStartCh + 3]);
        }
        if ((activeCh & 0x100) != 0) {
            v = ch[4].calcL();
            l = new int[] { v };
            r = new int[] { v };
            effects.distortion.mix(efcStartCh + 4, l, r);
            effects.chorus.mix(efcStartCh + 4, l, r);
            effects.hpflpf.mix(efcStartCh + 4, l, r);
            l[0] = (int) ((dest[4] >> 1) * l[0] * panL[4]) * ReversePhase.fm[num][4][0];
            r[0] = (int) ((dest[4] & 0x1) * r[0] * panR[4]) * ReversePhase.fm[num][4][1];
            buf[0] += l[0];
            buf[1] += r[0];
            buf[2] += (int) (l[0] * effects.reverb.sendLevel[efcStartCh + 4]);
            buf[3] += (int) (r[0] * effects.reverb.sendLevel[efcStartCh + 4]);
        }
        if ((activeCh & 0x400) != 0) {
            v = ch[5].calcL();
            l = new int[] { v };
            r = new int[] { v };
            effects.distortion.mix(efcStartCh + 5, l, r);
            effects.chorus.mix(efcStartCh + 5, l, r);
            effects.hpflpf.mix(efcStartCh + 5, l, r);
            l[0] = (int) ((dest[5] >> 1) * l[0] * panL[5]) * ReversePhase.fm[num][5][0];
            r[0] = (int) ((dest[5] & 0x1) * r[0] * panR[5]) * ReversePhase.fm[num][5][1];
            buf[0] += l[0];
            buf[1] += r[0];
            buf[2] += (int) (l[0] * effects.reverb.sendLevel[efcStartCh + 5]);
            buf[3] += (int) (r[0] * effects.reverb.sendLevel[efcStartCh + 5]);
        }
    }

    protected void lfo() {
        // Debug.printf("%4d - %8d, %8d\n", c, lfocount, lfodcount);

        chip.setPML(OPNA2.pmTable[(lfoCount >> (Fmvgen.FM_LFOCBITS + 1)) & 0xff]);
        chip.setAML(OPNA2.amTable[(lfoCount >> (Fmvgen.FM_LFOCBITS + 1)) & 0xff]);
        lfoCount += lfodCount;
    }

    public void reset() {
        for (int i = 0x20; i < 0x28; i++) setReg(i, 0);
        for (int i = 0x30; i < 0xc0; i++) setReg(i, 0);
        for (int i = 0x130; i < 0x1c0; i++) setReg(i, 0);
        for (int i = 0; i < 6; i++) {
            pan[i] = 3;
            panL[i] = OPNA2.panTable[0];
            panR[i] = OPNA2.panTable[0];
            ch[i].reset();
        }
    }
}
