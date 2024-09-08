
package mdsound.instrument;

import mdsound.chips.Xgm;


public class Ym2612XInst extends Ym2612Inst {

    public Xgm xgmFunction = new Xgm();

    private int sampleRate = 0;

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        sampleRate = super.start(chipId, clock, clockValue, option);
        return clock;
    }

    @Override
    public void reset(int chipId) {
        xgmFunction.reset(chipId, sampleRate);
        super.reset(chipId);
    }

    @Override
    public void stop(int chipId) {
        xgmFunction.stop(chipId);
        super.stop(chipId);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        xgmFunction.write(chipId, port, adr, data);
        return super.write(chipId, port, adr, data);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        xgmFunction.update(chipId, samples, this::write);
        super.update(chipId, outputs, samples);
    }
}
