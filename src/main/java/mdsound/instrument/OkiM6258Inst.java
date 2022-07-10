package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.MDSound;
import mdsound.chips.OkiM6258;


public class OkiM6258Inst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x02;
    public OkiM6258[] okiM6258Data = new OkiM6258[] {new OkiM6258(), new OkiM6258()};

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        OkiM6258 chip = okiM6258Data[chipId];
        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private int device_start_okim6258(byte chipId, int clock, int divider, int adpcm_type, int output_12bits) {
        if (chipId >= MAX_CHIPS)
            return 0;

        OkiM6258 info = okiM6258Data[chipId];
        return info.start(clock, divider, adpcm_type, output_12bits);
    }

    /**
     * stop emulation of an OKIM6258-compatible chips
     */
    private void device_stop_okim6258(byte chipId) {
        okiM6258Data[chipId] = null;
    }

    private void device_reset_okim6258(byte chipId) {
        OkiM6258 info = okiM6258Data[chipId];
        info.reset();
    }

    private void okim6258_set_divider(byte chipId, int val) {
        OkiM6258 info = okiM6258Data[chipId];
        info.setDivider(val);
    }

    private void okim6258_set_clock(byte chipId, int val) {
        OkiM6258 info = okiM6258Data[chipId];
        info.setClock(val);
    }

    private int okim6258_get_vclk(byte chipId) {
        OkiM6258 info = okiM6258Data[chipId];
        return info.getVclk();
    }

    /**
     * read the status port of an OKIM6258-compatible chips
     */
//    byte okim6258_status_r(byte chipId, offs_t offset) {
//        OkiM6258 info = OKIM6258Data[chipId];
//
//        return (info.status & STATUS_PLAYING) ? 0x00 : 0x80;
//    }

    private void okim6258_data_w(byte chipId, /*offs_t offset, */byte data) {
        OkiM6258 info = okiM6258Data[chipId];
        info.data_write(data);
    }

    private void okim6258_ctrl_w(byte chipId, /*offs_t offset, */byte data) {
        OkiM6258 info = okiM6258Data[chipId];
        info.writeControl(data);
    }

    private void okim6258_set_clock_byte(byte chipId, byte Byte, byte val) {
        OkiM6258 info = okiM6258Data[chipId];
        info.setClock(val);
    }

    private void okim6258_pan_w(byte chipId, byte data) {
        OkiM6258 info = okiM6258Data[chipId];
        info.writePan(data);
    }

    /**
     * Generic get_info
     */
//        DEVICE_GET_INFO( OkiM6258Inst ) {
//            switch (state) {
//                case DEVINFO_STR_NAME:       strcpy(info.s, "OKI6258");     break;
//                case DEVINFO_STR_FAMILY:     strcpy(info.s, "OKI ADPCM");    break;
//                case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");      break;
//                case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
//            }
//        }

    public OkiM6258Inst() {
        // 0..Main
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipId, int clock) {
        return start(chipId, 44100, clock, 0);
    }

    @Override
    public int start(byte chipId, int samplingRate, int clockValue, Object... option) {
        int divider = ((int) option[0] & 0x03) >> 0;
        int adpcmType = ((int) option[0] & 0x04) >> 2;
        int output12Bits = ((int) option[0] & 0x08) >> 3;
        return device_start_okim6258(chipId, clockValue, divider, adpcmType, output12Bits);
    }

    @Override
    public String getName() {
        return "OKIM6258";
    }

    @Override
    public String getShortName() {
        return "OKI5";
    }

    @Override
    public void stop(byte chipId) {
        device_stop_okim6258(chipId);
    }

    @Override
    public void reset(byte chipId) {
        device_reset_okim6258(chipId);
    }

    private void okim6258_write(byte chipId, byte port, byte data) {
        OkiM6258 info = okiM6258Data[chipId];
        info.write(port, data);
    }

    public void okim6258_set_options(int options) {
        OkiM6258.setOptions(options);
    }

    public void okim6258_set_srchg_cb(byte chipId, OkiM6258.SampleRateCallback callbackFunc, MDSound.Chip chip) {
        OkiM6258 info = okiM6258Data[chipId];
        info.setCallback(callbackFunc, chip);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        okim6258_write(chipId, (byte) adr, (byte) data);
        return 0;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x1C0, 2d);
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        result.put("okim6258", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        return result;
    }
}
