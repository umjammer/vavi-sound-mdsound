
package mdsound;

import java.util.Collections;
import java.util.Map;
import java.util.ServiceLoader;

import dotnet4j.util.compat.Tuple;


public interface Instrument {

    Map<String, Object> getView(String key, Map<String, Object> args);

    String getName();

    String getShortName();

    int start(int chipId, int clock);

    int start(int chipId, int clock, int clockValue, Object... option);

    void stop(int chipId);

    void reset(int chipId);

    void update(int chipId, int[][] outputs, int samples);

    int write(int chipId, int port, int adr, int data);

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

        protected int getMonoVolume(int pl, int pr, int sl, int sr) {
            int v = pl + pr + sl + sr;
            v >>= 1;
            if (sl + sr != 0) v >>= 1;

            return v;
        }
    }

    static Instrument getInstrument(Class<? extends Instrument> c) {
        ServiceLoader<Instrument> loader = ServiceLoader.load(Instrument.class);
        for (Instrument i : loader) {
            if (i.getClass() == c) {
                return i;
            }
        }
        return null;
    }
}
