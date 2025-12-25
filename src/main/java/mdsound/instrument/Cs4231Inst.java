package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import mdsound.Instrument;
import mdsound.chips.Cs4231;


public class Cs4231Inst extends Instrument.BaseInstrument {

    private final Cs4231[] chips = {new Cs4231(), new Cs4231()};

    public Cs4231Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}}; // 0..Main
    }

    @Override
    public String getName() {
        return "CS4231";
    }

    @Override
    public String getShortName() {
        return "CS4231";
    }

    @Override
    public void reset(int chipId) {

    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId] = new Cs4231();
        chips[chipId].renderingFreq = samplingRate;
        if (option != null && option.length > 1) {
            chips[chipId].dma.fifoBuf = (byte[]) option[0];
            chips[chipId].dma.int0bEnt = (Runnable) option[1];
        }

        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        return chips[chipId].readReg(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        Cs4231 chip = chips[chipId & 1];
        chip.write(port, adr, data);

        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        Cs4231 chip = chips[chipId & 1];

        chip.update(outputs, samples);

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
