package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.OotakeHuC6280;


// OotakeHuC6280
public class HuC6280Inst extends Instrument.BaseInstrument {

    private OotakeHuC6280[] chips = new OotakeHuC6280[2];
    private static final int DefaultHuC6280ClockValue = 3579545;

    @Override
    public String getName() {
        return "OotakeHuC6280";
    }

    @Override
    public String getShortName() {
        return "HuC8";
    }

    public HuC6280Inst() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
        //0..Main
    }

    @Override
    public int start(int chipId, int clock) {
        start(chipId, clock, DefaultHuC6280ClockValue);

        return clock;
    }

    @Override
    public int start(int chipId, int samplingRate, int clockValue, Object... option) {
        chips[chipId] = new OotakeHuC6280(clockValue, samplingRate);

        return samplingRate;
    }

    @Override
    public void stop(int chipId) {
        if (chips[chipId] == null) return;
        chips[chipId] = null;
    }

    @Override
    public void reset(int chipId) {
        if (chips[chipId] == null) return;
        chips[chipId].reset();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        if (chips[chipId] == null) return;
        chips[chipId].mix(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private int HuC6280_Write(int chipId, int adr, int data) {
        if (chips[chipId] == null) return 0;
        chips[chipId].writeReg(adr, data);
        return 0;
    }

    public int read(int chipId, int adr) {
        if (chips[chipId] == null) return 0;
        return chips[chipId].read(adr);
    }

    public void setMute(int chipId, int val) {
        if (chips[chipId] == null) return;

        chips[chipId].setMuteMask(val);
    }

    public void SetVolume(int chipId, int db) {
        if (chips[chipId] == null) {
        }
    }

    public OotakeHuC6280 GetState(int chipId) {
        return chips[chipId];
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        return HuC6280_Write(chipId, adr, data);
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
