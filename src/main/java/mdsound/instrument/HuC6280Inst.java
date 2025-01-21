package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.OotakeHuC6280;


// OotakeHuC6280
public class HuC6280Inst extends Instrument.BaseInstrument {

    public static final int DefaultHuC6280ClockValue = 3579545;

    private final OotakeHuC6280[] chips = {new OotakeHuC6280(), new OotakeHuC6280()};

    private final int[] mask = {0, 0};

    public HuC6280Inst() {
        // 0..Main
        visVolume = new int[][][] {
                {{0, 0}},
                {{0, 0}}
        };
    }

    @Override
    public String getName() {
        return "HuC6280ootake";
    }

    @Override
    public String getShortName() {
        return "HuC8";
    }

    @Override
    public void reset(int chipId) {
        assert chipId < chips.length;
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < chips.length;
        chips[chipId].init(clock, samplingRate);

        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        assert chipId < chips.length;
        return chips[chipId].read(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < chips.length;
        chips[chipId].writeReg(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        assert chipId < chips.length;
        chips[chipId].mix(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        assert chipId < chips.length;
        chips[chipId] = null;
    }

    private void setMute(int chipId, int val) {
        assert chipId < chips.length;
        chips[chipId].setMuteMask(val);
    }

    public void setVolume(int chipId, int db) {
        assert chipId < chips.length;
    }

    //----

    public synchronized void setMask(int chipId, int ch) {
        mask[chipId] |= ch;
        setMute(chipId, mask[chipId]);
    }

    public synchronized void resetMask(int chipId, int ch) {
        mask[chipId] &= ~ch;
        setMute(chipId, mask[chipId]);
    }

    public synchronized OotakeHuC6280 getChip(int chipId) {
        return chips[chipId];
    }

    //----

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
