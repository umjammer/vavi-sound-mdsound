package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.YmF262;


public class YmF262Inst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x02;
    private final YmF262[] chips = new YmF262[] {new YmF262(), new YmF262()};

    private int emuCore = 0x00;

    @Override
    public void reset(int chipId) {
        device_reset_ymf262(chipId);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public String getName() {
        return "YMF262";
    }

    @Override
    public String getShortName() {
        return "Opl3";
    }

    @Override
    public int start(int chipId, int clock) {
        return device_start_ymf262(chipId, 14318180);
    }

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        return device_start_ymf262(chipId, clockValue);
    }

    @Override
    public void stop(int chipId) {
        device_stop_ymf262(chipId);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        ymf262_stream_update(chipId, outputs, samples);

        //common.write("output %d %d", outputs[0][0], outputs[1][0]);
        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private int YMF262_Write(int chipId, int adr, int data) {
        ymf262_w(chipId, (adr & 0x100) != 0 ? 0x02 : 0x00, adr & 0xff);
        ymf262_w(chipId, (adr & 0x100) != 0 ? 0x03 : 0x01, data);
        return 0;
    }

    public void ymf262_stream_update(int chipId, int[][] outputs, int samples) {
        YmF262 info = chips[chipId];
        info.update(outputs, samples);
    }

    public void setEmuCore(byte emulator) {
        emuCore = (byte) ((emulator < 0x02) ? emulator : 0x00);
    }

    private int[][] dummyBuf = new int[][] {null, null};

    private void updateHandler() {
        chips[0].update(dummyBuf, 0);
    }

    public int device_start_ymf262(int chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        YmF262 info = chips[chipId];
        int rate = clock / 288;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;

        //info.intf = device.static_config ? (final ymf262_interface *)device.static_config : &dummy;
        //info.intf = &dummy;
        //info.device = device;

        // stream system initialize
        info.start(emuCore, clock, rate, this::updateHandler);

        if (emuCore == YmF262.EC_MAME) {
            //assert_always(info.chips != NULL, "Error creating YMF262 chips");

            //info.stream = stream_create(device,0,4,rate,info,ymf262_stream_update);

            // YMF262 setup
            //ymf262_set_timer_handler(info.chips, timer_handler_262, info);
            //ymf262_set_irq_handler(info.chips, IRQHandler_262, info);
            //ymf262_set_update_handler(info.chips, _stream_update, info);

            //info.timer[0] = timer_alloc(device.machine, timer_callback_262_0, info);
            //info.timer[1] = timer_alloc(device.machine, timer_callback_262_1, info);
        }
        return rate;
    }

    public void device_stop_ymf262(int chipId) {
        YmF262 info = chips[chipId];
        info.stop();
    }

    /** reset */
    public void device_reset_ymf262(int chipId) {
        YmF262 info = chips[chipId];
        info.reset();
    }

    public byte ymf262_r(int chipId, int offset) {
        YmF262 info = chips[chipId];
        return info.read(offset);
    }

    public void ymf262_w(int chipId, int offset, int data) {
        YmF262 info = chips[chipId];

        info.write(offset, data);
    }

    public byte ymf262_status_r(int chipId, int offset) {
        return ymf262_r(chipId, 0);
    }

    public void ymf262_register_a_w(int chipId, int offset, int data) {
        ymf262_w(chipId, 0, data);
    }

    public void ymf262_register_b_w(int chipId, int offset, int data) {
        ymf262_w(chipId, 2, data);
    }

    public void ymf262_data_a_w(int chipId, int offset, int data) {
        ymf262_w(chipId, 1, data);
    }

    public void ymf262_data_b_w(int chipId, int offset, int data) {
        ymf262_w(chipId, 3, data);
    }

    public void ymf262_set_emu_core(int emulator) {
        this.emuCore = emulator;
    }

    public void ymf262_set_mute_mask(int chipId, int muteMask) {
        YmF262 info = chips[chipId];
        info.setMuteMask(muteMask);
    }

    private void irqHandler(int irq) {
    }

    private void timerHandler(int timer, int period) {
        if (period == 0) { // Reset FM Timer
            //timer_enable(info.timer[timer], 0);
        } else { // Start FM Timer
            //timer_adjust_oneshot(info.timer[timer], period, 0);
        }
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        return YMF262_Write(chipId, adr, data);
    }

//    /**
//     * Generic get_info
//     */
//    DEVICE_GET_INFO( YmF262Inst ) {
//            case DEVINFO_STR_NAME:       strcpy(info.s, "YMF262");       break;
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
