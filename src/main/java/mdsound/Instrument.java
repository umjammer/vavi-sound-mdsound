
package mdsound;

public abstract class Instrument {

    public static final byte CHIP_SAMPLING_MODE = 2;
    public static final int CHIP_SAMPLE_RATE = 44100;

    public int[][][] visVolume = null;// chipid , type , LR

    public abstract String getName();

    public abstract String getShortName();

    public abstract int Start(byte ChipID, int clock);

    public abstract int Start(byte ChipID, int clock, int ClockValue, Object... option);

    public abstract void Stop(byte ChipID);

    public abstract void Reset(byte ChipID);

    public abstract void Update(byte ChipID, int[][] outputs, int samples);

    public abstract int Write(byte ChipID, int port, int adr, int data);
}
