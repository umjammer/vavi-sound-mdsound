package mdsound.instrument;

import mdsound.Instrument;
import mdsound.chips.SinWaveGen;


public class SinWaveInst extends Instrument.BaseInstrument {

    @Override
    public String getName() {
        return "SinWave";
    }

    @Override
    public String getShortName() {
        return "SIN";
    }

    public SinWaveInst() {
        // 0..Main
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public void reset(int chipId) {
        if (chip[chipId] == null) {
            chip[chipId] = new SinWaveGen();
        }
        // chips[chipId].render = false;
    }

    @Override
    public int start(int chipId, int clock) {
        return start(chipId, clock, DefaultClockValue);
    }

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        reset(chipId);
        chip[chipId].clock = clock;
        chip[chipId].render = true;

        return clock; // samplingRate
    }

    @Override
    public void stop(int chipId) {
        if (chip[chipId] == null) return;
        chip[chipId].render = false;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        if (chip[chipId] == null) return;
        chip[chipId].update(outputs, samples);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        if (chip[chipId] == null) return 0;
        return chip[chipId].write(data);
    }

    private static final int DefaultClockValue = 0;
    private SinWaveGen[] chip = new SinWaveGen[2];
}
