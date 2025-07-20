package mdsound.instrument;

import mdsound.chips.Xgm;


public class Ym3438XInst extends Ym3438Inst {

    private final Xgm[] chips = {new Xgm()};

    private int sampleRate = 0;

    @Override
    public void reset(int chipId) {
        chips[chipId].reset(sampleRate);
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
        chips[chipId].write(port, adr, data);
        return super.write(chipId, port, adr, data);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].update(samples, this::write);
        super.update(chipId, outputs, samples);
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].stop();
        super.stop(chipId);
    }

    // ----

    public synchronized void playPcm(int chipId, int port, int adr, int data) {
        chips[chipId].playPCM(adr, data);
    }
}
