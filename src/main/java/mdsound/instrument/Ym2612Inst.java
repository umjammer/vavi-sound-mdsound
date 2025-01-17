package mdsound.instrument;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Ym2612;

import static java.lang.System.getLogger;


public class Ym2612Inst extends Instrument.BaseInstrument {

    private static final Logger logger = getLogger(Ym2612Inst.class.getName());

    private static final int DefaultFMClockValue = 7670454;

    private static final int MAX_CHIPS = 2;
    public Ym2612[] chips = new Ym2612[MAX_CHIPS];

    public Ym2612Inst() {
        //0..Main
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public String getName() {
        return "Ym2612";
    }

    @Override
    public String getShortName() {
        return "OPN2";
    }

    @Override
    public int start(int chipId, int samplingRate) {
        if (samplingRate == 0) return 0;
        chips[chipId] = new Ym2612(DefaultFMClockValue, samplingRate, 0);
        chips[chipId].reset();

        return samplingRate;
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        if (samplingRate == 0) return 0;
        if (clock == 0) {
            clock = DefaultFMClockValue;
        }

        chips[chipId] = new Ym2612(clock, samplingRate, clock);
        chips[chipId].reset();

        // 動作オプション設定
        if (option != null && option.length > 0 && option[0] instanceof Integer optFlags) {
logger.log(Level.DEBUG, "option: " + optFlags);
            chips[chipId].setOptions(optFlags & 0x3);
        }

        return samplingRate;
    }

    @Override
    public void stop(int chipId) {
        chips[chipId] = null;
    }

    @Override
    public void reset(int chipId) {
        Ym2612 chip = chips[chipId];
        if (chip == null) return;

        chip.reset();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        Ym2612 chip = chips[chipId];
        if (chip == null) return;

        chip.update(outputs, samples);
        chip.updateDacAndTimers(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public synchronized int write(int chipId, int port, int adr, int data) {
        int r = 0;
        r += writeInternal(chipId, 0, 0 + (port & 1) * 2, adr);
        r += writeInternal(chipId, 0, 1 + (port & 1) * 2, data);
        return r;
    }

    private int writeInternal(int chipId, int port, int adr, int data) {
        Ym2612 chip = chips[chipId];
        if (chip == null) return 0;

        return chip.write(adr, data);
    }

    //----

    public void setMute(int chipId, int v) {
        Ym2612 chip = chips[chipId];
        if (chip == null) return;

        chip.setMute(v);
    }

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
            case "registers" ->
                    result.put(getName(), new int[][][] {chips[0].getRegisters(), chips[0].getRegisters()});
        }
        return result;
    }
}
