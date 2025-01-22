package mdsound.instrument;

import mdsound.Instrument;
import mdsound.chips.SinWaveGen;


public class SinWaveInst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 0;

    private final SinWaveGen[] chips = {new SinWaveGen(), new SinWaveGen()};

    public SinWaveInst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "SinWave";
    }

    @Override
    public String getShortName() {
        return "SIN";
    }

    @Override
    public void reset(int chipId) {
        assert chipId < chips.length;
//        chips[chipId].render = false;
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < chips.length;
        chips[chipId].clock = samplingRate;
        chips[chipId].render = true;
        return samplingRate; // samplingRate
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < chips.length;
        return chips[chipId].write(data);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        assert chipId < chips.length;
        chips[chipId].update(outputs, samples);
    }

    @Override
    public void stop(int chipId) {
        assert chipId < chips.length;
        chips[chipId].render = false;
    }
}
