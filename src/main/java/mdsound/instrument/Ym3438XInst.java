package mdsound.instrument;

import mdsound.chips.Xgm;


public class Ym3438XInst extends Ym3438Inst {

    private final Xgm chip = new Xgm();

    private int sampleRate = 0;

    @Override
    public void reset(int chipId) {
        chip.reset(chipId, sampleRate);
        super.reset(chipId);
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        sampleRate = super.start(chipId, samplingRate, clock, option);
        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chip.write(chipId, port, adr, data);
        return super.write(chipId, port, adr, data);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chip.update(chipId, samples, this::write);
        super.update(chipId, outputs, samples);
    }

    @Override
    public void stop(int chipId) {
        chip.stop(chipId);
        super.stop(chipId);
    }

    // ----

    public synchronized void playPcm(int chipId, int port, int adr, int data) {
        chip.playPCM(chipId, adr, data);
    }
}
