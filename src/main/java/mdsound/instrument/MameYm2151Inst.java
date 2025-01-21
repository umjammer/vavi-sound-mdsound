package mdsound.instrument;


import mdsound.Instrument;
import mdsound.chips.Ym2151;


public class MameYm2151Inst extends Instrument.BaseInstrument {

    private final Ym2151[] chips = {new Ym2151(), new Ym2151()};

    @Override
    public String getName() {
        return "YM2151mame";
    }

    @Override
    public String getShortName() {
        return "OPMm";
    }

    @Override
    public void reset(int chipId) {
        assert chipId < chips.length;
        Ym2151 chip = chips[chipId];
        chip.reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < chips.length;
        Ym2151 chip = chips[chipId];
        chip.init(clock, samplingRate);

        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < chips.length;
        Ym2151 chip = chips[chipId];
        chip.ym2151_write_reg(adr, data);

        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        assert chipId < chips.length;
        Ym2151 chip = chips[chipId];
        chip.update(outputs, samples);
    }

    @Override
    public void stop(int chipId) {
        assert chipId < chips.length;
        Ym2151 chip = chips[chipId];
        chip.stop();
    }
}
