package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.fmgen.OPM;


public class Ym2151Inst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 3579545;

    private final OPM[] chips = {new OPM(), new OPM()};

    private final int[][] keyOn = {new int[8], new int[8]};

    public Ym2151Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "YM2151";
    }

    @Override
    public String getShortName() {
        return "OPM";
    }

    @Override
    public void reset(int chipId) {
        assert chipId < chips.length;
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId].init(clock, samplingRate, false);
        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < chips.length;
        chips[chipId].setReg(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        assert chipId < chips.length;

        int[] buffer = new int[2];
        buffer[0] = 0;
        buffer[1] = 0;
        chips[chipId].mix(buffer, 1);
        for (int i = 0; i < 1; i++) {
            outputs[0][i] = buffer[i * 2 + 0];
            outputs[1][i] = buffer[i * 2 + 1];
//logger.log(Level.TRACE, "[%8d] : [%8d] [%d]".formatted(outputs[0][i], outputs[1][i],i));
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
    }

    @Override
    public void setMask(int chipId, int ch) {
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }

    // ----

    public synchronized int[] readKeyOn(int chipId) {
        for (int i = 0; i < 8; i++) {
//            keyOn[chipId][i] = chips[chipId].CHANNEL[i].KeyOn;
        }
        return keyOn[chipId];
    }

    // ----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        }
        return result;
    }
}
