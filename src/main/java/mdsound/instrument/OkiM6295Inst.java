package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.MDSound;
import mdsound.chips.OkiM6295;


public class OkiM6295Inst extends Instrument.BaseInstrument {

    public OkiM6295Inst() {
        // 0..Main
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(int chipId, int clock) {
        return device_start_okim6295(chipId, clock);
    }

    @Override
    public int start(int chipId, int samplingrate, int clockValue, Object... option) {
        return device_start_okim6295(chipId, clockValue);
    }

    @Override
    public void stop(int chipId) {
        device_stop_okim6295(chipId);
    }

    @Override
    public void reset(int chipId) {
        device_reset_okim6295(chipId);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        okim6295_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private static final int MAX_CHIPS = 0x02;
    public OkiM6295[] chips = new OkiM6295[] {new OkiM6295(), new OkiM6295()};

    @Override
    public String getName() {
        return "OKIM6295";
    }

    @Override
    public String getShortName() {
        return "OKI9";
    }

    /**
     * update the Sound chips so that it is in sync with CPU execution
     */
    private void okim6295_update(int chipId, int[][] outputs, int samples) {
        OkiM6295 chip = chips[chipId];
        chip.update(outputs, samples);
    }

    /**
     * start emulation of an OKIM6295-compatible chips
     */
    private int device_start_okim6295(int chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        OkiM6295 info = chips[chipId];
        return info.start(clock);
    }

    private void device_stop_okim6295(int chipId) {
        OkiM6295 chip = chips[chipId];
        chip.stop();
    }

    /**
     * stop emulation of an OKIM6295-compatible chips
     */
    private void device_reset_okim6295(int chipId) {
        OkiM6295 info = chips[chipId];
        info.reset();
    }

    /**
     * read the status port of an OKIM6295-compatible chips
     */
    private int okim6295_r(int chipId, int offset) {
        OkiM6295 info = chips[chipId];
        return info.read(offset);
    }

    private void okim6295_w(int chipId, int offset, int data) {
        OkiM6295 chip = chips[chipId];
        chip.write(offset, data);
    }

    public void okim6295_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        OkiM6295 chip = chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void okim6295_write_rom2(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddr) {
        OkiM6295 chip = chips[chipId];
        chip.writeRom2(romSize, dataStart, dataLength, romData, srcStartAddr);
    }

    public void okim6295_set_mute_mask(int chipId, int muteMask) {
        OkiM6295 chip = chips[chipId];
        chip.setMuteMask(muteMask);
    }

    public void okim6295_set_srchg_cb(int chipId, OkiM6295.SamplingRateCallback CallbackFunc, MDSound.Chip dataPtr) {
        OkiM6295 info = chips[chipId];
        info.setCallback(CallbackFunc, dataPtr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        okim6295_w(chipId, adr, data);
        return 0;
    }

    public OkiM6295.ChannelInfo readChInfo(int chipId) {
        OkiM6295 info = chips[chipId];
        return info.readChInfo();
    }

//    /**
//     * Generic get_info
//     */
//    DEVICE_GET_INFO( OkiM6295Inst ) {
//       switch (state) {
//        case DEVINFO_STR_NAME:      strcpy(info.s, "OKI6295");      break;
//        case DEVINFO_STR_FAMILY:     strcpy(info.s, "OKI ADPCM");     break;
//        case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
//        case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
//       }
//    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100 /* 110 */, 2d);
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
