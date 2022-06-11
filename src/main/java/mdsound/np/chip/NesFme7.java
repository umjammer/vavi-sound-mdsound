package mdsound.np.chip;

import mdsound.MDSound;
import mdsound.np.Device.SoundChip;
import mdsound.np.chip.DeviceInfo.BasicTrackInfo;


public class NesFme7 implements SoundChip {
    protected int[][] sm = new int[][] {new int[3], new int[3]}; // stereo mix
    protected short[] buf = new short[2];
    protected Emu2149 emu2149;
    protected int divider; // clock divider
    protected double clock, rate;
    protected BasicTrackInfo[] trkinfo = new BasicTrackInfo[5];
    protected final int DIVIDER = 8 * 2;
    public final double DEFAULT_CLOCK = 1789772.0;
    public final int DEFAULT_RATE = 44100;

    public NesFme7() {
        emu2149 = new Emu2149((int) DEFAULT_CLOCK, DEFAULT_RATE);

        for (int c = 0; c < 2; ++c)
            for (int t = 0; t < 3; ++t)
                sm[c][t] = 128;
    }

    protected void finalinze() {
        emu2149.finalize();
    }

    @Override
    public void setClock(double c) {
        this.clock = c * 2.0;
    }

    @Override
    public void setRate(double r) {
        //rate = r ? r : DEFAULT_RATE;
        rate = DEFAULT_CLOCK / (double) DIVIDER; // TODO rewrite PSG to integrate with clock
        emu2149.psg.setRate((int) rate);
    }

    @Override
    public void reset() {
        for (int i = 0; i < 16; ++i) { // blank all registers
            write(0xC000, (int) i);
            write(0xE000, 0);
        }
        write(0xC000, 0x07); // disable all tones
        write(0xE000, 0x3F);

        divider = 0;
        emu2149.psg.reset();
    }

    @Override
    public boolean write(int adr, int val, int id/*=0*/) {
        if (adr == 0xC000) {
            emu2149.psg.writeIO(0, val);
            return true;
        }
        if (adr == 0xE000) {
            emu2149.psg.writeIO(1, val);
            return true;
        } else
            return false;
    }

    @Override
    public boolean read(int adr, int[] val, int id/* = 0*/) {
        val[0] = emu2149.psg.readReg(adr);
        return false;
    }

    @Override
    public void tick(int clocks) {
        divider += clocks;
        while (divider >= DIVIDER) {
            divider -= DIVIDER;
            emu2149.psg.calcPSG();
        }
    }

    @Override
    public int render(int[] b) {
        Emu2149.PSG psg = emu2149.psg;
        b[0] = b[1] = 0;

        for (int i = 0; i < 3; ++i) {
            // note negative polarity
            b[0] -= psg.cout[i] * sm[0][i];
            b[1] -= psg.cout[i] * sm[1][i];
        }
        b[0] >>= (7 - 4);
        b[1] >>= (7 - 4);

        // master volume adjustment
        final int master = (int) (0.64 * 256.0);
        b[0] = (b[0] * master) >> 8;
        b[1] = (b[1] * master) >> 8;

        MDSound.np_nes_fme7_volume = Math.abs(b[0]);

        return 2;
    }

    @Override
    public void setStereoMix(int trk, short mixl, short mixr) {
        if (trk < 0) return;
        if (trk > 2) return;
        sm[0][trk] = mixl;
        sm[1][trk] = mixr;
    }

    public DeviceInfo.TrackInfo getTrackInfo(int trk) {
        //assert(trk < 5);

        Emu2149.PSG psg = emu2149.psg;
        if (trk < 3) {
            trkinfo[trk]._freq = psg.freq[trk];
            if (psg.freq[trk] != 0)
                trkinfo[trk].freq = psg.clk / 32.0 / psg.freq[trk];
            else
                trkinfo[trk].freq = 0;

            trkinfo[trk].output = psg.cout[trk];
            trkinfo[trk].maxVolume = 15;
            trkinfo[trk].volume = (int) (psg.volume[trk] >> 1);
            //trkinfo[trk].key = (psg.cout[trk]>0)?true:false;
            trkinfo[trk].key = ((~(psg.tmask[trk])) & 1) != 0;
            trkinfo[trk].tone = (psg.tmask[trk] != 0 ? 2 : 0) + (psg.nmask[trk] != 0 ? 1 : 0);
        } else if (trk == 3) // envelope
        {
            trkinfo[trk]._freq = psg.envFreq;
            if (psg.envFreq != 0)
                trkinfo[trk].freq = psg.clk / 512.0 / psg.envFreq;
            else
                trkinfo[trk].freq = 0;

            if (psg.envContinue != 0 && psg.envAlternate != 0 && psg.envHold == 0) // triangle wave
            {
                trkinfo[trk].freq *= 0.5f; // sounds an octave down
            }

            trkinfo[trk].output = (int) psg.volTbl[psg.envPtr];
            trkinfo[trk].maxVolume = 0;
            trkinfo[trk].volume = 0;
            trkinfo[trk].key = (((psg.volume[0] | psg.volume[1] | psg.volume[2]) & 32) != 0);
            trkinfo[trk].tone =
                    (psg.envContinue != 0 ? 8 : 0) |
                            (psg.envAttack != 0 ? 4 : 0) |
                            (psg.envAlternate != 0 ? 2 : 0) |
                            (psg.envHold != 0 ? 1 : 0);
        } else if (trk == 4) // noise
        {
            trkinfo[trk]._freq = psg.noiseFreq >> 1;
            if (trkinfo[trk]._freq > 0)
                trkinfo[trk].freq = psg.clk / 16.0 / psg.noiseFreq;
            else
                trkinfo[trk].freq = 0;

            trkinfo[trk].output = psg.noiseSeed & 1;
            trkinfo[trk].maxVolume = 0;
            trkinfo[trk].volume = 0;
            //trkinfo[trk].key = ((psg.nmask[0]&psg.nmask[1]&psg.nmask[2]) == 0);
            trkinfo[trk].key = false;
            trkinfo[trk].tone = 0;
        }
        return trkinfo[trk];
    }

    @Override
    public void setMask(int mask) {
        emu2149.psg.setMask(mask);
    }

    @Override
    public void setOption(int id, int val) {
        throw new UnsupportedOperationException();
    }
}

