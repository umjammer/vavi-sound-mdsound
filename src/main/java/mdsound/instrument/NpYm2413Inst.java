package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.np.chip.Emu2413.Opll;


/** OPLL np emu */
public class NpYm2413Inst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 3579545;

    private final Opll[] chips = {new Opll(), new Opll()};

    public NpYm2413Inst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "YM2413np";
    }

    @Override
    public String getShortName() {
        return "OPLLnp";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId].init(clock, samplingRate);
        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < chips.length;
        chips[chipId].writeReg(adr, data);
        return 0;
    }

    private int[][] b = new int[2][2];

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].calcStereo(b[chipId]);

        outputs[0][0] = b[chipId][0] << 1;
        outputs[1][0] = b[chipId][1] << 1;

        visVolume[chipId][0][0] = b[0][0];
        visVolume[chipId][0][1] = b[1][0];
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

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x200 /* 0x155 */, 0.5);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "register" -> result.put("register", chips[chipId].getRegisters());
        }
        return result;
    }
}

