package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.YmF271;


public class YmF271Inst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x10;
    public YmF271[] ymf271Chips = new YmF271[] {new YmF271(), new YmF271(),};

    @Override
    public String getName() {
        return "YMF271";
    }

    @Override
    public String getShortName() {
        return "OPX";
    }

    @Override
    public void reset(int chipId) {
        YmF271 chip = ymf271Chips[chipId];
        chip.reset();

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(int chipId, int samplingRate) {
        return startInternal(chipId, 16934400);
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        return startInternal(chipId, clock);
    }

    @Override
    public void stop(int chipId) {
        YmF271 chip = ymf271Chips[chipId];
        chip.stop();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        YmF271 chip = ymf271Chips[chipId];
        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    public int ymf271_r(int chipId, int offset) {
        YmF271 chip = ymf271Chips[chipId];
        return chip.read(offset);
    }

    private int startInternal(int chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        YmF271 chip = ymf271Chips[chipId];
        return chip.start(clock);
    }

    public void ymf271_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        YmF271 chip = ymf271Chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void ymf271_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        YmF271 chip = ymf271Chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    public void ymf271_set_mute_mask(int chipId, int muteMask) {
        YmF271 chip = ymf271Chips[chipId];
        chip.setMuteMask(muteMask);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        YmF271 chip = ymf271Chips[chipId];
        chip.write((port << 1) | 0x00, adr & 0xff);
        chip.write((port << 1) | 0x01, data & 0xff);
        return 0;
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
            case "NAME" -> result.put(getName(), "YMF271");
            case "FAMILY" -> result.put(getName(), "Yamaha FM");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
