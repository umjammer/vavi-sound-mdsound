
package mdsound;

public interface Instrument {

    int[][][] getVisVolume();

    String getName();

    String getShortName();

    int start(byte chipId, int clock);

    int start(byte chipId, int clock, int clockValue, Object... option);

    void stop(byte chipId);

    void reset(byte chipId);

    void update(byte chipId, int[][] outputs, int samples);

    int write(byte chipId, int port, int adr, int data);

    abstract class BaseInstrument implements Instrument {
        protected static byte CHIP_SAMPLING_MODE = 2;
        public static int CHIP_SAMPLE_RATE = 44100;

        // chipid , type , LR
        protected int[][][] visVolume;

        public int[][][] getVisVolume() {
            return visVolume;
        }
    }
}
