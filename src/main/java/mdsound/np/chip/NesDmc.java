package mdsound.np.chip;

import mdsound.Common;
import mdsound.Instrument;
import mdsound.MDSound;
import mdsound.np.Device;
import mdsound.np.Device.SoundChip;
import mdsound.np.NpNesDmc;


public class NesDmc implements SoundChip {

    public NpNesDmc dmc = new NpNesDmc(Common.NsfClock, Instrument.BaseInstrument.CHIP_SAMPLE_RATE);

    @Override
    public boolean read(int adr, int[] val, int id/* = 0*/) {
        return dmc.read(adr, val);
    }

    @Override
    public int render(int[] b) {
        int ret = dmc.renderOrg(b);
        MDSound.np_nes_dmc_volume = Math.abs(b[0]);
        return ret;
    }

    @Override
    public void reset() {
        dmc.reset();
    }

    @Override
    public void setClock(double clock) {
        dmc.setClock(clock);
    }

    @Override
    public void setMask(int mask) {
        dmc.setMask(mask);
    }

    @Override
    public void setOption(int id, int val) {
        dmc.setOption(id, val);
    }

    @Override
    public void setRate(double rate) {
        dmc.setRate(rate);
    }

    @Override
    public void setStereoMix(int trk, short mixl, short mixr) {
        dmc.setStereoMix(trk, mixl, mixr);
    }

    @Override
    public void tick(int clocks) {
        dmc.orgTick(clocks);
    }

    @Override
    public boolean write(int adr, int val, int id/* = 0*/) {
        return dmc.write(adr, val);
    }

    public void setMemory(Device r) {
        dmc.setMemoryOrg(r);
    }
}
