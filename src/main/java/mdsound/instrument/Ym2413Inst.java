package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Ym2413;


public class Ym2413Inst extends Instrument.BaseInstrument {

    private static final int DefaultYM2413ClockValue = 3579545;

    private Ym2413[] chips = new Ym2413[2];

    public Ym2413Inst() {
        // 0..Main
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public String getName() {
        return "YM2413";
    }

    @Override
    public String getShortName() {
        return "Ym2413";
    }

    @Override
    public void reset(byte chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(byte chipId, int samplingRate) {
        chips[chipId] = new Ym2413(DefaultYM2413ClockValue, samplingRate, null);
        chips[chipId].setQuality(0);
        return samplingRate;
    }

    @Override
    public int start(byte chipId, int samplingRate, int clockValue, Object... option) {
        if (option != null && option.length > 0 && option[0] instanceof byte[] ary) {
            chips[chipId] = new Ym2413(clockValue, samplingRate, ary);
        } else {
            chips[chipId] = new Ym2413(clockValue, samplingRate, null);
        }
        chips[chipId].setQuality(0);
        return samplingRate;
    }

    @Override
    public void stop(byte chipId) {
        chips[chipId] = null;
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        chips[chipId].calcStereo(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        if (chips[chipId] != null) {
            chips[chipId].writeReg((byte) adr, (byte) data);
        }
        return 0;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x200/*0x155*/, 0.5);
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        result.put("ym2413", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        return result;
    }
}

