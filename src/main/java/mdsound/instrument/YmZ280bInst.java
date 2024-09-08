package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.YmZ280b;


public class YmZ280bInst extends Instrument.BaseInstrument {

    @Override
    public void reset(int chipId) {
        device_reset_ymz280b(chipId);
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(int chipId, int clock) {
        return device_start_ymz280b(chipId, 16934400);
    }

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        return device_start_ymz280b(chipId, clockValue);
    }

    @Override
    public void stop(int chipId) {
        device_stop_ymz280b(chipId);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        ymz280b_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private int write(int chipId, int offset, int data) {
        ymz280b_w(chipId, 0x00, offset);
        ymz280b_w(chipId, 0x01, data);
        return 0;
    }

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

    /**
     * update the Sound chips so that it is in sync with CPU execution
     */
    public void ymz280b_update(int chipId, int[][] outputs, int samples) {
        YmZ280b chip = chips[chipId];
        chip.update(outputs, samples);
    }

    /**
     * start emulation of the YMZ280B
     */
    public int device_start_ymz280b(int chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        YmZ280b chip = chips[chipId];
        return chip.start(clock);
    }

    public void device_stop_ymz280b(int chipId) {
        YmZ280b chip = chips[chipId];
        chip.stop();
    }

    public void device_reset_ymz280b(int chipId) {
        YmZ280b chip = chips[chipId];
        chip.reset();    }

    /**
     * handle external accesses
     */
    public int ymz280b_r(int chipId, int offset) {
        YmZ280b chip = chips[chipId];
        return chip.read(offset);
    }

    public void ymz280b_w(int chipId, int offset, int data) {
        YmZ280b chip = chips[chipId];
        chip.write(offset, data);
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
        return write(chipId, adr, data);
    }

    private void updateIrqStateTimerCommon(Object param, int voiceNum) {
    }

//    /**
//     * Generic get_info
//     */
//    DEVICE_GET_INFO( YmZ280bInst ) {
//            case DEVINFO_STR_NAME:       strcpy(info.s, "YMZ280B");      break;
//            case DEVINFO_STR_FAMILY:     strcpy(info.s, "Yamaha Wavetable");   break;
//            case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
//            case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
//    }

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
        }
        return result;
    }
}
