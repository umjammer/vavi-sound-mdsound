package mdsound.np.chip;

import mdsound.Common;
import mdsound.Instrument;
import mdsound.MDSound;
import mdsound.np.Device.SoundChip;
import mdsound.np.NpNesApu;


public class NesApu implements SoundChip {

    public NpNesApu apu = new NpNesApu(Common.NsfClock, Instrument.BaseInstrument.CHIP_SAMPLE_RATE);

    @Override
    public boolean read(int adr, int[] val, int id /*= 0*/) {
        return apu.read(adr, val);
    }

    @Override
    public int render(int[] b) {
        int ret = apu.renderOrg(b);
        MDSound.np_nes_apu_volume = Math.abs(b[0]);
        return ret;
    }

    @Override
    public void reset() {
        apu.reset();
    }

    @Override
    public void setClock(double clock) {
        apu.setClock(clock);
    }

    @Override
    public void setMask(int mask) {
        apu.setMask(mask);
    }

    @Override
    public void setOption(int id, int val) {
        apu.setOption(id, val);
    }

    @Override
    public void setRate(double rate) {
        apu.setRate(rate);
    }

    @Override
    public void setStereoMix(int trk, short mixl, short mixr) {
        apu.setStereoMix(trk, mixl, mixr);
    }

    @Override
    public void tick(int clocks) {
        apu.tick(clocks);
    }

    @Override
    public boolean write(int adr, int val, int id/* = 0*/) {
        return apu.write(adr, val);
    }
}
