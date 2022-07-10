package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.fmgen.OPM;


public class Ym2151Inst extends Instrument.BaseInstrument {

    public static final int DefaultYM2151ClockValue = 3579545;

    private OPM[] chip = new OPM[2];

    public Ym2151Inst() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
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
    public void reset(byte chipId) {
        if (chip[chipId] == null) return;
        chip[chipId].reset();
    }

    @Override
    public int start(byte chipId, int clock) {
        chip[chipId] = new OPM();
        chip[chipId].init(DefaultYM2151ClockValue, clock, false);

        return clock;
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        chip[chipId] = new OPM();
        chip[chipId].init(clockValue, clock, false);

        return clock;
    }

    @Override
    public void stop(byte chipId) {
        chip[chipId] = null;
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        if (chip[chipId] == null) return;

        int[] buffer = new int[2];
        buffer[0] = 0;
        buffer[1] = 0;
        chip[chipId].mix(buffer, 1);
        for (int i = 0; i < 1; i++) {
            outputs[0][i] = buffer[i * 2 + 0];
            outputs[1][i] = buffer[i * 2 + 1];
            //Debug.printf("[%8d] : [%8d] [%d]\n", outputs[0][i], outputs[1][i],i);
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        if (chip[chipId] == null) return 0;

        chip[chipId].setReg((byte) adr, (byte) data);
        return 0;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        result.put("ym2151", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        return result;
    }
}
