package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.DosboxYm3812;


public class Ym3812Inst extends Instrument.BaseInstrument {

    private static final int DefaultYM3812ClockValue = 3579545;

    private static final int MAX_CHIPS = 0x02;
    private final DosboxYm3812[] chips = new DosboxYm3812[] {new DosboxYm3812(), new DosboxYm3812()};

    private byte emuCore;

    @Override
    public void reset(int chipId) {
        device_reset_ym3812(chipId);
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public String getName() {
        return "YM3812";
    }

    @Override
    public String getShortName() {
        return "OPL2";
    }

    @Override
    public int start(int chipId, int clock) {
        return start(chipId, DefaultYM3812ClockValue, 44100);
    }

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        return device_start_ym3812(chipId, clockValue);
    }

    @Override
    public void stop(int chipId) {
        device_stop_ym3812(chipId);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        ym3812_stream_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        ym3812_control_port_w(chipId, 0, adr);
        ym3812_write_port_w(chipId, 0, data);
        return 0;
    }

    private void ym3812_stream_update(int chipId, int[][] outputs, int samples) {
        DosboxYm3812 info = chips[chipId];
        info.updateStream(outputs, samples);
    }

    private int[][] dummyBuf = new int[][] {null, null};

    private void _stream_update(/*, int interval*/) {
        chips[0].updateStream(dummyBuf, 0); // TODO
    }

    private int device_start_ym3812(int chipId, int clock) {
        DosboxYm3812 info;
        int rate;

        if (chipId >= MAX_CHIPS)
            return 0;

        info = chips[chipId];
        rate = (clock & 0x7FFFFFFF) / 72;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;
        //info.intf = device.static_config ? (final ym3812_interface *)device.static_config : &dummy;
        //info.intf = &dummy;
        //info.device = device;

        info.start(emuCore, clock, rate, this::_stream_update);

        return rate;
    }

    private void device_stop_ym3812(int chipId) {
        DosboxYm3812 info = chips[chipId];
        info.stop();
    }

    private void device_reset_ym3812(int chipId) {
        DosboxYm3812 info = chips[chipId];
        info.reset();
    }

    private byte ym3812_r(int chipId, int offset) {
        DosboxYm3812 info = chips[chipId];
        return info.read(offset);
    }

    private void ym3812_w(int chipId, int offset, int data) {
        DosboxYm3812 info = chips[chipId];
        if (info == null) return;
        info.write(offset, data);
    }

    private byte ym3812_status_port_r(int chipId, int offset) {
        return ym3812_r(chipId, 0);
    }

    private byte ym3812_read_port_r(int chipId, int offset) {
        return ym3812_r(chipId, 1);
    }

    private void ym3812_control_port_w(int chipId, int offset, int data) {
        ym3812_w(chipId, 0, data);
    }

    private void ym3812_write_port_w(int chipId, int offset, int data) {
        ym3812_w(chipId, 1, data);
    }

    public void ym3812_set_emu_core(byte Emulator) {
        emuCore = (byte) ((Emulator < 0x02) ? Emulator : 0x00);
    }

    private void ym3812_set_mute_mask(int chipId, int muteMask) {
        DosboxYm3812 info = chips[chipId];
        info.setMuteMask(muteMask);
    }

//    /**
//     * Generic get_info
//     */
//    DEVICE_GET_INFO( Ym3812Inst ) {
//        switch (state) {
//            case DEVINFO_STR_NAME:       strcpy(info.s, "YM3812");       break;
//            case DEVINFO_STR_FAMILY:     strcpy(info.s, "Yamaha FM");      break;
//            case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");        break;
//            case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
//        }
//    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 2d);
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
