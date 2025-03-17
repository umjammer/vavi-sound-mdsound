/*
 * NSFPlay/NFSPlug project by Brezza.
 *
 * https://web.archive.org/web/20160301201825/http://www.pokipoki.org/dsa/
 */

package mdsound.np.chip;

import java.util.function.Consumer;

import mdsound.Common;
import mdsound.Instrument;
import mdsound.chips.Nes;
import mdsound.np.Device;
import mdsound.np.Device.SoundChip;
import mdsound.np.NpNesDmc;


public class NesDmc implements SoundChip {

    public NpNesDmc dmc;

    public NesDmc() {
        dmc = new NpNesDmc();
        dmc.init(Common.NsfClock, Instrument.BaseInstrument.CHIP_SAMPLE_RATE);
    }

    @Override
    public boolean read(int adr, /* ref */ int[] val, int id /* = 0 */) {
        return dmc.read(adr, val);
    }

    @Override
    public int render(int[] b) {
        int ret = dmc.renderOrg(b);
        if (listener != null) listener.accept(new int[] {-1, Math.abs(b[0]), -1, -1, -1, -1, -1, -1});
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
    public void setStereoMix(int trk, int mixL, int mixR) {
        dmc.setStereoMix(trk, mixL, mixR);
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

    private Consumer<int[]> listener;

    public void setListener(Consumer<int[]> listener) {
        this.listener = listener;
    }
}
