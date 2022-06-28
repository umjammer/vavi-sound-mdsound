
package mdsound;

public class Ym2612X extends Ym2612 {
    public XgmFunction XGMfunction = new XgmFunction();

    private int samplerate = 0;

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        samplerate = super.start(chipId, clock, clockValue, option);
        return clock;
    }

    @Override
    public void reset(byte chipId) {
        XGMfunction.reset(chipId, samplerate);
        super.reset(chipId);
    }

    @Override
    public void stop(byte chipId) {
        XGMfunction.stop(chipId);
        super.stop(chipId);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        XGMfunction.write(chipId, port, adr, data);
        return super.write(chipId, port, (byte) adr, (byte) data);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        XGMfunction.update(chipId, samples, this::write);
        super.update(chipId, outputs, samples);
    }
}
