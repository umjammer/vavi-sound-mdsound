package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import dotnet4j.io.Stream;
import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.YmF278b;


public class YmF278bInst extends Instrument.BaseInstrument {

    @Override
    public void reset(int chipId) {
        device_reset_ymf278b(chipId);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(int chipId, int clock) {
        return device_start_ymf278b(chipId, YmF278b.YMF278B_STD_CLOCK, null, null);
    }

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        String romPath = null;
        Function<String, Stream> romStream = null;

        if (option != null && option.length > 0) {
            if (option[0] instanceof String) {
                romPath = (String) option[0];
                romStream = null;
            }
            if (option[0] instanceof Function /*<String, Stream>*/) {
                romPath = null;
                romStream = (Function<String, Stream>) option[0];
            }
        }
        return device_start_ymf278b(chipId, clockValue, romPath, romStream);
    }

    @Override
    public void stop(int chipId) {
        device_stop_ymf278b(chipId);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        ymf278b_pcm_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private static final int MAX_CHIPS = 0x10;
    private final YmF278b[] ymF278BData = new YmF278b[] {new YmF278b(), new YmF278b()};

    @Override
    public String getName() {
        return "YMF278B";
    }

    @Override
    public String getShortName() {
        return "OPL4";
    }

    public void ymf278b_pcm_update(int chipId, int[][] outputs, int samples) {
        YmF278b chip = ymF278BData[chipId];
        chip.updatePcm(outputs, samples);
    }

    public void ymf278b_w(int chipId, int offset, int data) {
        YmF278b chip = ymF278BData[chipId];
        chip.write(offset, data);
    }

    public int device_start_ymf278b(int chipId, int clock, String romPath, Function<String, Stream> romStream) {
        if (chipId >= MAX_CHIPS)
            return 0;

        YmF278b chip = ymF278BData[chipId];
        return chip.start(clock, romPath, romStream);
    }

    public void device_stop_ymf278b(int chipId) {
        YmF278b chip = ymF278BData[chipId];
        chip.stop();
    }

    public void device_reset_ymf278b(int chipId) {
        YmF278b chip = ymF278BData[chipId];
        chip.reset();
    }

    public void ymf278b_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        YmF278b chip = ymF278BData[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void ymf278b_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        YmF278b chip = ymF278BData[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    public void ymf278b_write_ram(int chipId, int dataStart, int dataLength, byte[] ramData, int srcStartAddress) {
        YmF278b chip = ymF278BData[chipId];
        chip.writeRam(dataStart, dataLength, ramData, srcStartAddress);
    }

    public void ymf278b_set_mute_mask(int chipId, int muteMaskFM, int muteMaskWT) {
        YmF278b chip = ymF278BData[chipId];
        chip.setMuteMask(muteMaskFM, muteMaskWT);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        ymf278b_w(chipId, (port << 1) | 0x00, adr);
        ymf278b_w(chipId, (port << 1) | 0x01, data);
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
        }
        return result;
    }
}
