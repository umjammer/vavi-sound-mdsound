package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.YmZ280b;


public class YmZ280bInst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x10;
    private final YmZ280b[] chips = new YmZ280b[] {new YmZ280b(), new YmZ280b()};

    @Override
    public String getName() {
        return "YMZ280B";
    }

    @Override
    public String getShortName() {
        return "YMZ";
    }

    @Override
    public void reset(int chipId) {
        YmZ280b chip = chips[chipId];
        chip.reset();
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(int chipId, int samplingRate) {
        if (chipId >= MAX_CHIPS)
            return 0;

        YmZ280b chip = chips[chipId];
        return chip.start(16934400);
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        if (chipId >= MAX_CHIPS)
            return 0;

        YmZ280b chip = chips[chipId];
        return chip.start(clock);
    }

    @Override
    public void stop(int chipId) {
        YmZ280b chip = chips[chipId];
        chip.stop();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        YmZ280b chip = chips[chipId];
        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    /**
     * handle external accesses
     */
    public int ymz280b_r(int chipId, int offset) {
        YmZ280b chip = chips[chipId];
        return chip.read(offset);
    }

    public void ymz280b_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        YmZ280b chip = chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void ymz280b_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        YmZ280b chip = chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAddress);
    }


    public void ymz280b_set_mute_mask(int chipId, int muteMask) {
        YmZ280b chip = chips[chipId];
        chip.setMuteMask(muteMask);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        YmZ280b chip = chips[chipId];
        chip.write(0x00, adr);
        chip.write(0x01, data);
        return 0;
    }

    private void updateIrqStateTimerCommon(Object param, int voiceNum) {
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x98, 0x20 / 19.0);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "YMZ280B");
            case "FAMILY" -> result.put(getName(), "Yamaha Wavetable");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
