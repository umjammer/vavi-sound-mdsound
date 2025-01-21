package mdsound.instrument;

import mdsound.Instrument;
import mdsound.chips.SinWaveGen;


public class SinWaveInst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 0;

    private final SinWaveGen[] chips = new SinWaveGen[2];

    public SinWaveInst() {
        // 0..Main
        visVolume = new int[][][] {
                {{0, 0}},
                {{0, 0}}
        };
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
        if (chips[chipId] == null) {
            chips[chipId] = new SinWaveGen();
        }
//        chips[chipId].render = false;
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        reset(chipId);
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
        if (chips[chipId] == null) return 0;
        return chips[chipId].write(data);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        if (chips[chipId] == null) return;
        chips[chipId].update(outputs, samples);
    }

    @Override
    public void stop(int chipId) {
        if (chips[chipId] == null) return;
        chips[chipId].render = false;
    }
}
