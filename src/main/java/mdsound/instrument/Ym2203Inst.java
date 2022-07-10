package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.fmgen.Opna;


public class Ym2203Inst extends Instrument.BaseInstrument {

    private Opna.OPN[] chips = new Opna.OPN[2];
    private static final int DefaultYM2203ClockValue = 3000000;

    @Override
    public String getName() {
        return "YM2203";
    }

    @Override
    public String getShortName() {
        return "OPN";
    }

    public Ym2203Inst() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}},
                new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}}
        };
        //0..Main 1..FM 2..SSG
    }

    @Override
    public void reset(byte chipId) {
        if (chips[chipId] == null) return;
        chips[chipId].reset();
    }

    @Override
    public int start(byte chipId, int clock) {
        chips[chipId] = new Opna.OPN();
        chips[chipId].init(DefaultYM2203ClockValue, clock);

        return clock;
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        chips[chipId] = new Opna.OPN();
        chips[chipId].init(clockValue, clock);

        return clock;
    }

    @Override
    public void stop(byte chipId) {
        chips[chipId] = null;
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        if (chips[chipId] == null) return;
        int[] buffer = new int[2];
        buffer[0] = 0;
        buffer[1] = 0;
        chips[chipId].mix(buffer, 1);
        for (int i = 0; i < 1; i++) {
            outputs[0][i] = buffer[i * 2 + 0];
            outputs[1][i] = buffer[i * 2 + 1];
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
        visVolume[chipId][1][0] = chips[chipId].visVolume[0];
        visVolume[chipId][1][1] = chips[chipId].visVolume[1];
        visVolume[chipId][2][0] = chips[chipId].psg.visVolume;
        visVolume[chipId][2][1] = chips[chipId].psg.visVolume;
    }

    private int YM2203_Write(byte chipId, byte adr, byte data) {
        if (chips[chipId] == null) return 0;
        chips[chipId].setReg(adr, data);
        return 0;
    }

    public void setMute(byte chipId, int val) {
        Opna.OPN chip = chips[chipId];
        if (chip == null) return;


        chip.setChannelMask(val);
    }

    public void SetFMVolume(byte chipId, int db) {
        if (chips[chipId] == null) return;

        chips[chipId].setVolumeFM(db);
    }

    public void SetPSGVolume(byte chipId, int db) {
        if (chips[chipId] == null) return;

        chips[chipId].setVolumePSG(db);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        return YM2203_Write(chipId, (byte) adr, (byte) data);
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        // mul=0.5 SSG
        return new Tuple<>(0x100, 1d);
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        result.put("ym2203", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        result.put("ym2203FM", getMonoVolume(visVolume[0][1][0], visVolume[0][1][1], visVolume[1][1][0], visVolume[1][1][1]));
        result.put("ym2203SSG", getMonoVolume(visVolume[0][2][0], visVolume[0][2][1], visVolume[1][2][0], visVolume[1][2][1]));
        return result;
    }
}
