package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Ym2612;


public class Ym2612Inst extends Instrument.BaseInstrument {

    private static final int DefaultFMClockValue = 7670454;

    private static final int MAX_CHIPS = 2;
    public Ym2612[] chips = new Ym2612[] {null, null};

    public Ym2612Inst() {
        //0..Main
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public String getName() {
        return "Ym2612Inst";
    }

    @Override
    public String getShortName() {
        return "OPN2";
    }

    @Override
    public int start(byte chipId, int clock) {
        chips[chipId] = new Ym2612(DefaultFMClockValue, clock, 0);
        chips[chipId].reset();

        return clock;
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        if (clockValue == 0) return 0;
        if (clock == 0) {
            clock = DefaultFMClockValue;
        }

        chips[chipId] = new Ym2612(clockValue, clock, clockValue);
        chips[chipId].reset();

        // 動作オプション設定
        if (option != null && option.length > 0 && option[0] instanceof Integer optFlags) {
            chips[chipId].setOptions(optFlags & 0x3);
        }

        return clock;
    }

    @Override
    public void stop(byte chipId) {
        chips[chipId] = null;
    }

    @Override
    public void reset(byte chipId) {
        Ym2612 chip = chips[chipId];
        if (chip == null) return;

        chip.reset();
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        Ym2612 chip = chips[chipId];
        if (chip == null) return;

        chip.update(outputs, samples);
        chip.updateDacAndTimers(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        Ym2612 chip = chips[chipId];
        if (chip == null) return 0;

        return chip.write((byte) adr, (byte) data);
    }

    //----

    public void setMute(byte chipId, int v) {
        Ym2612 chip = chips[chipId];
        if (chip == null) return;

        chip.setMute(v);
    }

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        result.put("ym2612", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        return result;
    }
}
