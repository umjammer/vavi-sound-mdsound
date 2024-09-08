package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.fmgen.PSG;


public class Ay8910Inst extends Instrument.BaseInstrument {

    private PSG[] chip = new PSG[2];
    private static final int DefaultClockValue = 1789750;

    @Override
    public String getName() {
        return "AY8910";
    }

    @Override
    public String getShortName() {
        return "AY10";
    }

    public Ay8910Inst() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
        //0..Main
    }

    @Override
    public void reset(int chipId) {
        if (chip[chipId] == null) return;
        chip[chipId].reset();
    }

    @Override
    public int start(int chipId, int clock) {
        chip[chipId] = new PSG();
        chip[chipId].setClock(DefaultClockValue, clock);

        return clock;
    }

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        chip[chipId] = new PSG();
        chip[chipId].setClock(clockValue, clock);

        return clock;
    }

    @Override
    public void stop(int chipId) {
        chip[chipId] = null;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        if (chip[chipId] == null) return;
        int[] buffer = new int[2];
        buffer[0] = 0;
        buffer[1] = 0;
        chip[chipId].mix(buffer, 1);
        for (int i = 0; i < 1; i++) {
            outputs[0][i] = buffer[i * 2 + 0];
            outputs[1][i] = buffer[i * 2 + 1];
            //Debug.printf("[%8d] : [%8d] [%d]\n", outputs[0][i], outputs[1][i], i);
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private int write(int chipId, byte adr, byte data) {
        if (chip[chipId] == null) return 0;
        chip[chipId].setReg(adr, data);
        return 0;
    }

    public void setMute(int chipId, int val) {
        PSG psg = chip[chipId];
        if (psg == null) return;


        psg.setChannelMask(val);
    }

    public void setVolume(int chipId, int db) {
        if (chip[chipId] == null) return;

        chip[chipId].setVolume(db);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        if (chip[chipId] == null) return 0;
        chip[chipId].setReg(adr, data);
        return 0;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 2d);
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
