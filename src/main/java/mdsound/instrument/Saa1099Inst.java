package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Saa1099;


public class Saa1099Inst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x02;
    private final Saa1099[] saa1099Data = new Saa1099[] {new Saa1099(), new Saa1099()};

    @Override
    public String getName() {
        return "SAA1099";
    }

    @Override
    public String getShortName() {
        return "SAA";
    }

    public Saa1099Inst() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public void reset(int chipId) {
        Saa1099 saa = saa1099Data[chipId];
        saa.reset();
    }

    @Override
    public int start(int chipId, int samplingRate) {
        return startInternal(chipId, 8000000);
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        return startInternal(chipId, clock);
    }

    @Override
    public void stop(int chipId) {
        Saa1099 saa = saa1099Data[chipId];
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        Saa1099 saa = saa1099Data[chipId];

        saa.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        saa1099_control_w(chipId, 0, adr);
        saa1099_data_w(chipId, 0, data);
        return 0;
    }

    public void setMute(int chipId, int v) {
        saa1099_set_mute_mask(chipId, v);
    }

    private int startInternal(int chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        Saa1099 saa = saa1099Data[chipId];
        return saa.start(clock);
    }

    public void saa1099_control_w(int chipId, int offset, int data) {
        Saa1099 saa = saa1099Data[chipId];
        saa.writeControl(offset, data);
    }

    public void saa1099_data_w(int chipId, int offset, int data) {
        Saa1099 saa = saa1099Data[chipId];
        saa.write(offset, data);
    }

    public void saa1099_set_mute_mask(int chipId, int muteMask) {
        Saa1099 saa = saa1099Data[chipId];
        saa.setMuteMask(muteMask);
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
            case "NAME" -> result.put(getName(), "SAA1099");
            case "FAMILY" -> result.put(getName(), "Philips");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
