package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Y8950;


public class Y8950Inst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x02;
    private Y8950[] chips = new Y8950[] {new Y8950(), new Y8950()};

    @Override
    public void reset(int chipId) {
        device_reset_y8950(chipId);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public String getName() {
        return "Y8950";
    }

    @Override
    public String getShortName() {
        return "Y895";
    }

    @Override
    public int start(int chipId, int clock) {
        return device_start_y8950(chipId, 3579545);
    }

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        return device_start_y8950(chipId, clockValue);
    }

    @Override
    public void stop(int chipId) {
        device_stop_y8950(chipId);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        y8950_stream_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        y8950_w(chipId, 0x00, adr);
        y8950_w(chipId, 0x01, data);
        return 0;
    }

    public void y8950_stream_update(int chipId, int[][] outputs, int samples) {
        Y8950 info = chips[chipId];
        info.update(outputs, samples);
    }

    //static DEVICE_START( Y8950Inst )
    public int device_start_y8950(int chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        Y8950 chip = chips[chipId];

        int rate = clock / 72;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < Instrument.BaseInstrument.CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = Instrument.BaseInstrument.CHIP_SAMPLE_RATE;
        //this.intf = device.static_config ? (final y8950_interface *)device.static_config : &dummy;
        //this.intf = &dummy;
        //this.device = device;

        // stream system initialize
        chip.start(clock, rate);
        //assert_always(this.chips != NULL, "Error creating Y8950 chips");

        //this.stream = stream_create(device,0,1,rate,info,y8950_stream_update);

        // port and keyboard handler
        chip.setPortHandler(this::writePort, this::readPort);
        chip.setKeyboardHandler(this::writeKeyboard, this::readKeyboard);

        // Y8950 setup
        chip.setTimerHandler(this::doTimer);
        chip.setIrqHandler(this::doIrq);
        chip.setUpdateHandler(this::_stream_update);

        //this.timer[0] = timer_alloc(device.machine, timer_callback_0, info);
        //this.timer[1] = timer_alloc(device.machine, timer_callback_1, info);

        return rate;
    }

    public void device_stop_y8950(int chipId) {
        Y8950 info = chips[chipId];
        info.stop();
    }

    public void device_reset_y8950(int chipId) {
        Y8950 info = chips[chipId];
        info.reset();
    }

    public int y8950_r(int chipId, int offset) {
        Y8950 info = chips[chipId];
        return info.read(offset & 1);
    }

    public void y8950_w(int chipId, int offset, int data) {
        Y8950 info = chips[chipId];
        info.write(offset & 1, data);
    }

    public int y8950_status_port_r(int chipId, int offset) {
        return y8950_r(chipId, 0);
    }

    public int y8950_read_port_r(int chipId, int offset) {
        return y8950_r(chipId, 1);
    }

    public void y8950_control_port_w(int chipId, int offset, int data) {
        y8950_w(chipId, 0, data);
    }

    public void y8950_write_port_w(int chipId, int offset, int data) {
        y8950_w(chipId, 1, data);
    }

    public void y8950_write_data_pcmrom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        Y8950 info = chips[chipId];
        info.writePcmRom(romSize, dataStart, dataLength, romData);
    }

    public void y8950_write_data_pcmrom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        Y8950 info = chips[chipId];
        info.writePcmRom(romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    public void y8950_set_mute_mask(int chipId, int muteMask) {
        Y8950 info = chips[chipId];
        info.setMuteMask(muteMask);
    }

    private void doIrq(int irq) {
        //if (info.intf.handler) (info.intf.handler)(info.device, irq ? ASSERT_LINE : CLEAR_LINE);
        //if (info.intf.handler) (info.intf.handler)(irq ? ASSERT_LINE : CLEAR_LINE);
    }

    private void doTimer(int c, int period) {
        //if( attotime_compare(period, attotime_zero) == 0 )
        if (period == 0) { // Reset FM Timer
            //timer_enable(info.timer[c], 0);
        } else { // Start FM Timer
            //timer_adjust_oneshot(info.timer[c], period, 0);
        }
    }

    private int readPort() {
//        if (info.intf.portread)
//            return info.intf.portread(0);
        return 0;
    }

    private void writePort(int data) {
//        if (info.intf.portwrite)
//            info.intf.portwrite(0,data);
    }

    private int readKeyboard() {
//        if (info.intf.keyboardread)
//            return info.intf.keyboardread(0);
        return 0;
    }

    private void writeKeyboard(int data) {
//        if (info.intf.keyboardwrite)
//            info.intf.keyboardwrite(0,data);
    }

    private int[][] dummyBuf = new int[][] {null, null};

    private void _stream_update(int interval) {
        //stream_update(info.stream);
        chips[0].update(dummyBuf, 0);
    }

//    /*
//     * Generic get_info
//     */
//    DEVICE_GET_INFO( Y8950Inst ) {
//            case DEVINFO_STR_NAME:       strcpy(info.s, "Y8950Inst");       break;
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
