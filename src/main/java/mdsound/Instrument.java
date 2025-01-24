
package mdsound;

import java.util.Collections;
import java.util.Map;
import java.util.ServiceLoader;

import dotnet4j.util.compat.Tuple;


// TODO read
public interface Instrument {

    /** for view */
    Map<String, Object> getView(String key, Map<String, Object> args);

    /**
     * Returns type name.
     */
    String getName();

    /**
     * Returns common name.
     */
    String getShortName();

    /** */
    default void init() {}

    /** */
    void reset(int chipId);

    /**
     * @return sampling rate
     */
    int start(int chipId, int samplingRate, int clock, Object... option);

    /** */
    int read(int chipId, int adr);

    /** */
    int write(int chipId, int port, int adr, int data);

    /** */
    void update(int chipId, int[][] outputs, int samples);

    /** */
    void stop(int chipId);

    /** */
    void setMask(int chipId, int ch);

    /** */
    void resetMask(int chipId, int ch);

    //

    Tuple<Integer, Double> getRegulationVolume();

    abstract class BaseInstrument implements Instrument {

        protected static int CHIP_SAMPLING_MODE = 2;

        public static int CHIP_SAMPLE_RATE = 44100;

        // chipId , type , LR
        protected int[][][] visVolume;

        @Override
        public Map<String, Object> getView(String key, Map<String, Object> args) {
            return Collections.emptyMap();
        }

        @Override
        public Tuple<Integer, Double> getRegulationVolume() {
            return new Tuple<>(0x100, 1d);
        }

        protected static int getMonoVolume(int pl, int pr, int sl, int sr) {
            int v = pl + pr + sl + sr;
            v >>= 1;
            if (sl + sr != 0) v >>= 1;

            return v;
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends Instrument> T getInstrument(Class<T> c) {
        ServiceLoader<Instrument> loader = ServiceLoader.load(Instrument.class);
        for (Instrument i : loader) {
            if (i.getClass() == c) {
                return (T) i;
            }
        }
        return null;
    }
}
