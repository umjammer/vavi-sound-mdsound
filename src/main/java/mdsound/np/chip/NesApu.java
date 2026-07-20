/*
 * NSFPlay/NFSPlug project by Brezza.
 *
 * https://web.archive.org/web/20160301201825/http://www.pokipoki.org/dsa/
 */

package mdsound.np.chip;

import java.util.function.Consumer;

import mdsound.np.Device.SoundChip;
import mdsound.np.NpNesApu;


public class NesApu implements SoundChip {

    public final NpNesApu apu;

    public NesApu() {
        apu = new NpNesApu();
        apu.init(NsfClock, SampleRate);
    }

    @Override
    public boolean read(int adr, /* ref */ int[] val, int id /* = 0 */) {
        return apu.read(adr, val);
    }

    @Override
    public int render(int[] b) {
        int ret = apu.renderOrg(b);
        if (listener != null) listener.accept(new int[] {Math.abs(b[0]), -1, -1, -1, -1, -1, -1, -1});
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
    public void setStereoMix(int trk, int mixL, int mixR) {
        apu.setStereoMix(trk, mixL, mixR);
    }

    @Override
    public void tick(int clocks) {
        apu.tick(clocks);
    }

    @Override
    public boolean write(int adr, int val, int id /* = 0 */) {
        return apu.write(adr, val);
    }

    private Consumer<int[]> listener;

    @Override
    public void setListener(Consumer<int[]> listener) {
        this.listener = listener;
    }
}
