package mdsound.instrument;


import mdsound.Instrument;
import mdsound.chips.Ym2151;


public class MameYm2151Inst extends Instrument.BaseInstrument {

    @Override
    public String getName() {
        return "YM2151mame";
    }

    @Override
    public String getShortName() {
        return "OPMm";
    }

    @Override
    public int start(int chipId, int clock) {
        return start(chipId, clock, Ym2151Inst.DefaultYM2151ClockValue);
    }

    @Override
    public int start(int chipId, int sampleRate, int clockValue, Object... option) {
        if (chipId >= 2)
            return 0;

        chips[chipId] = new Ym2151(clockValue, sampleRate);

        return sampleRate;
    }

    @Override
    public void stop(int chipId) {
        if (chips[chipId] == null) return;
        Ym2151 chip = chips[chipId];
        chip.stop();
        chips[chipId] = null;
    }

    @Override
    public void reset(int chipId) {
        if (chips[chipId] == null) return;
        Ym2151 info = chips[chipId];
        info.reset();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        if (chips[chipId] == null) return;
        Ym2151 info = chips[chipId];
        info.update(outputs, samples);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        if (chips[chipId] == null) return 0;
        Ym2151 token = chips[chipId];
        token.ym2151_write_reg(adr, data);

        return 0;
    }

    private final Ym2151[] chips = new Ym2151[2];
}
