
package mdsound;

public interface Instrument {

    int[][][] getVisVolume();

    String getName();

    String getShortName();

    int start(byte chipID, int clock);

    int start(byte chipID, int clock, int clockValue, Object... option);

    void stop(byte chipID);

    void reset(byte chipID);

    void update(byte chipID, int[][] outputs, int samples);

    int write(byte chipID, int port, int adr, int data);

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
