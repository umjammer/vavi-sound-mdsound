package mdsound.np.chip;

import mdsound.MDSound;
import mdsound.Common;
import mdsound.np.Device.SoundChip;
import mdsound.np.chip.DeviceInfo.BasicTrackInfo;


public class NesVrc7 implements SoundChip {
    protected int mask;
    protected int patchSet;
    protected int[][] sm = new int[][] {new int[6], new int[6]}; // stereo mix
    protected short[] buf = new short[2];
    protected Emu2413.Opll opll;
    protected Emu2413 emu2413 = new Emu2413();
    protected int divider; // clock divider
    protected double clock, rate;
    protected BasicTrackInfo[] trkInfo = new BasicTrackInfo[6];

    public NesVrc7() {
        patchSet = Emu2413.Opll.Tone.VRC7_RW.ordinal();

        opll = new Emu2413.Opll(3579545, Common.SampleRate);
        opll.resetPatch(patchSet);
        setClock(Common.NsfClock);// DEFAULT_CLOCK);

        for (int c = 0; c < 2; ++c)
            for (int t = 0; t < 6; ++t)
                sm[c][t] = 128;
    }

    protected void finalinze() {
        opll = null;
    }

    public void setPatchSet(int p) {
        patchSet = p;
    }

    @Override
    public void setClock(double c) {
        clock = c / 36;
    }

    @Override
    public void setRate(double r) {
        //rate = r ? r : DEFAULT_RATE;
        //(void)r; // rate is ignored
        rate = 49716;
        opll.set_quality(1); // quality always on (not really a CPU hog)
        opll.set_rate((int) rate);
    }

    @Override
    public void reset() {
        for (int i = 0; i < 0x40; ++i) {
            write(0x9010, i);
            write(0x9030, 0);
        }

        divider = 0;
        opll.resetPatch(patchSet);
        opll.reset();
    }

    @Override
    public void setStereoMix(int trk, short mixL, short mixR) {
        if (trk < 0) return;
        if (trk > 5) return;
        sm[0][trk] = mixL;
        sm[1][trk] = mixR;
    }

    public DeviceInfo.TrackInfo getTrackInfo(int trk) {
        if (opll != null && trk < 6) {
            trkInfo[trk].maxVolume = 15;
            trkInfo[trk].volume = 15 - ((opll.reg[0x30 + trk]) & 15);
            trkInfo[trk]._freq = opll.reg[0x10 + trk] + ((opll.reg[0x20 + trk] & 1) << 8);
            int blk = (opll.reg[0x20 + trk] >> 1) & 7;
            trkInfo[trk].freq = clock * trkInfo[trk]._freq / (double) (0x80000 >> blk);
            trkInfo[trk].tone = (opll.reg[0x30 + trk] >> 4) & 15;
            trkInfo[trk].key = (opll.reg[0x20 + trk] & 0x10) != 0;
            return trkInfo[trk];
        } else
            return null;
    }

    public byte[] getRegs() {
        return opll.reg;
    }

    public static class ChipKeyInfo {
        public boolean[] on;
        public boolean[] off;

        public ChipKeyInfo(int n) {
            on = new boolean[n];
            off = new boolean[n];
            //for (int i = 0; i < n; i++) Off[i] = true;
        }
    }

    private ChipKeyInfo ki = new ChipKeyInfo(6);
    private ChipKeyInfo kiRet = new ChipKeyInfo(6);

    public ChipKeyInfo getKeyInfo(int chipId) {
        for (int ch = 0; ch < 6; ch++) {
            kiRet.off[ch] = ki.off[ch];
            kiRet.on[ch] = ki.on[ch];
            ki.on[ch] = false;
        }
        return kiRet;
    }

    @Override
    public boolean write(int adr, int val, int id/* = 0*/) {
        if (adr == 0x9010) {
            opll.writeIO(0, val);
            return true;
        }
        if (adr == 0x9030) {
            opll.writeIO(1, val);
            if (opll.adr >= 0x20 && opll.adr <= 0x25) {
                int ch = opll.adr - 0x20;
                int k = val & 0x10;
                if (k == 0) {
                    ki.off[ch] = true;
                } else {
                    if (ki.off[ch]) ki.on[ch] = true;
                    ki.off[ch] = false;
                }
            }
            return true;
        } else
            return false;
    }

    @Override
    public boolean read(int adr, int[] val, int id/* = 0*/) {
        return false;
    }

    @Override
    public void tick(int clocks) {
        divider += clocks;
        while (divider >= 36) {
            divider -= 36;
            opll._calc();
        }
    }

    @Override
    public int render(int[] b) {
        b[0] = b[1] = 0;
        for (int i = 0; i < 6; ++i) {
            int val = (mask & (1 << i)) != 0 ? 0 : opll.slot[(i << 1) | 1].output[1];
            b[0] += val * sm[0][i];
            b[1] += val * sm[1][i];
        }
        b[0] >>= (7 - 4);
        b[1] >>= (7 - 4);

        // master volume adjustment
        final int MASTER = (int) (0.8 * 256.0);
        b[0] = (b[0] * MASTER) >> 8;
        b[1] = (b[1] * MASTER) >> 8;

        MDSound.np_nes_vrc7_volume = Math.abs(b[0]);

        return 2;
    }


    @Override
    public void setMask(int m) {
        mask = m;
        if (opll != null) opll.setMask(m);
    }

    @Override
    public void setOption(int id, int val) {
        throw new UnsupportedOperationException();
    }
}

