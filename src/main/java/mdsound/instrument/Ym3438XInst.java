package mdsound.instrument;

import mdsound.chips.Xgm;


public class Ym3438XInst extends Ym3438Inst {
    public Xgm xgmFunction = new Xgm();
    private int sampleRate = 0;

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        sampleRate = super.start(chipId, clock, clockValue, option);
        return clock;
    }

    @Override
    public void reset(byte chipId) {
        xgmFunction.reset(chipId, sampleRate);
        super.reset(chipId);
    }

    @Override
    public void stop(byte chipId) {
        xgmFunction.stop(chipId);
        super.stop(chipId);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        xgmFunction.write(chipId, port, adr, data);
        return super.write(chipId, port, (byte) adr, (byte) data);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        xgmFunction.update(chipId, samples, this::write);
        super.update(chipId, outputs, samples);
    }
}
