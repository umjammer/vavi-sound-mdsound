package mdsound;

public class Ym3438X extends Ym3438 {
    public XgmFunction xgmFunction = new XgmFunction();
    private int sampleRate = 0;

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        sampleRate = super.start(chipID, clock, clockValue, option);
        return clock;
    }

    @Override
    public void reset(byte chipID) {
        xgmFunction.reset(chipID, sampleRate);
        super.reset(chipID);
    }

    @Override
    public void stop(byte chipID) {
        xgmFunction.stop(chipID);
        super.stop(chipID);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        xgmFunction.write(chipID, port, adr, data);
        return super.write(chipID, port, (byte) adr, (byte) data);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        xgmFunction.update(chipID, samples, this::write);
        super.update(chipID, outputs, samples);
    }
}
