
package mdsound;

public class ym2612mameX extends ym2612mame {
    public XGMFunction XGMfunction = new XGMFunction();

    private int samplerate = 0;

    @Override
    public int Start(byte ChipID, int clock, int clockValue, Object... option) {
        samplerate = (int) super.Start(ChipID, clock, clockValue, option);
        return clock;
    }

    @Override
    public void Reset(byte ChipID) {
        XGMfunction.Reset(ChipID, samplerate);
        super.Reset(ChipID);
    }

    @Override
    public void Stop(byte ChipID) {
        XGMfunction.Stop(ChipID);
        super.Stop(ChipID);
    }

    @Override
    public int Write(byte ChipID, int port, int adr, int data) {
        XGMfunction.Write(ChipID, port, adr, data);
        return super.Write(ChipID, port, (byte) adr, (byte) data);
    }

    @Override
    public void Update(byte ChipID, int[][] outputs, int samples) {
        XGMfunction.Update(ChipID, samples, this::Write);
        super.Update(ChipID, outputs, samples);
    }
}
