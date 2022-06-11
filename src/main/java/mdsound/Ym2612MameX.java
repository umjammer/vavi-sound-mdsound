
package mdsound;

public class Ym2612MameX extends Ym2612Mame {
    public XgmFunction XGMfunction = new XgmFunction();

    private int samplerate = 0;

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        samplerate = (int) super.start(chipID, clock, clockValue, option);
        return clock;
    }

    @Override
    public void reset(byte chipID) {
        XGMfunction.reset(chipID, samplerate);
        super.reset(chipID);
    }

    @Override
    public void stop(byte chipID) {
        XGMfunction.stop(chipID);
        super.stop(chipID);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        XGMfunction.write(chipID, port, adr, data);
        return super.write(chipID, port, (byte) adr, (byte) data);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        XGMfunction.update(chipID, samples, this::write);
        super.update(chipID, outputs, samples);
    }
}
