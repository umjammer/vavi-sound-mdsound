package mdsound.np.chip;

import java.util.function.Consumer;

import mdsound.Common;
import mdsound.Instrument;
import mdsound.np.Device.SoundChip;
import mdsound.np.NpNesFds;


public class NesFds implements SoundChip {

    public NpNesFds fds = new NpNesFds(Common.NsfClock, Instrument.BaseInstrument.CHIP_SAMPLE_RATE);

    @Override
    public boolean read(int adr, int[] val, int id/* = 0*/) {
        return fds.read(adr, val);
    }

    @Override
    public int render(int[] b) {
        int ret = fds.renderOrg(b);
        if (listener != null) listener.accept(new int[] {-1, -1, Math.abs(b[0]), -1, -1, -1, -1, -1});
        return ret;
    }

    @Override
    public void reset() {
        fds.reset();
    }

    @Override
    public void setClock(double clock) {
        fds.setClock(clock);
    }

    @Override
    public void setMask(int mask) {
        fds.setMask(mask);
    }

    @Override
    public void setOption(int id, int val) {
        fds.setOption(id, val);
    }

    @Override
    public void setRate(double rate) {
        fds.setRate(rate);
    }

    @Override
    public void setStereoMix(int trk, short mixl, short mixr) {
        fds.setStereoMix(trk, mixl, mixr);
    }

    @Override
    public void tick(int clocks) {
        fds.tick(clocks);
    }

    @Override
    public boolean write(int adr, int val, int id/* = 0*/) {
        return fds.write(adr, val);
    }

    private Consumer<int[]> listener;

    public void setListener(Consumer<int[]> listener) {
        this.listener = listener;
    }
}
