package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.YmF262;


public class YmF262Inst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x02;
    private final YmF262[] chips = new YmF262[] {new YmF262(), new YmF262()};

    private int emuCore = YmF262.EC_DBOPL;

    @Override
    public void reset(int chipId) {
        YmF262 chip = chips[chipId];
        chip.reset();

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
    public int start(int chipId, int samplingRate) {
        return startInternal(chipId, 14318180);
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        return startInternal(chipId, clock);
    }

    private int startInternal(int chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        YmF262 chip = chips[chipId];
        int rate = clock / 288;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;

//        chip.intf = device.static_config ? (final ymf262_interface) device.static_config : dummy;
//        chip.intf = dummy;
//        chip.device = device;

        // stream system initialize
        chip.start(emuCore, clock, rate, this::updateHandler);

        if (emuCore == YmF262.EC_MAME) {
//            assert_always(chip.chips != NULL, "Error creating YMF262 chips");

//            chip.stream = stream_create(device,0,4,rate,chip,ymf262_stream_update);

            // YMF262 setup
//            ymf262_set_timer_handler(chip.chips, timer_handler_262, chip);
//            ymf262_set_irq_handler(chip.chips, IRQHandler_262, chip);
//            ymf262_set_update_handler(chip.chips, _stream_update, chip);
//
//            chip.timer[0] = timer_alloc(device.machine, timer_callback_262_0, chip);
//            chip.timer[1] = timer_alloc(device.machine, timer_callback_262_1, chip);
        }
        return rate;
    }

    @Override
    public void stop(int chipId) {
        YmF262 info = chips[chipId];
        info.stop();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        YmF262 info = chips[chipId];
        info.update(outputs, samples);

//logger.log(Level.TRACE, "output %d %d".formatted(outputs[0][0], outputs[1][0]));
        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    public void setEmuCore(int emulator) {
        emuCore = (emulator < 0x02) ? emulator : 0x00;
    }

    private final int[][] dummyBuf = new int[][] {null, null};

    private void updateHandler() {
        chips[0].update(dummyBuf, 0);
    }

    public int ymf262_r(int chipId, int offset) {
        YmF262 chip = chips[chipId];
        return chip.read(offset);
    }

    public int ymf262_status_r(int chipId, int offset) {
        return ymf262_r(chipId, 0);
    }

    public void ymf262_register_a_w(int chipId, int offset, int data) {
        YmF262 chip = chips[chipId];

        chip.write(0, data);
    }

    public void ymf262_register_b_w(int chipId, int offset, int data) {
        YmF262 chip = chips[chipId];

        chip.write(2, data);
    }

    public void ymf262_data_a_w(int chipId, int offset, int data) {
        YmF262 chip = chips[chipId];

        chip.write(1, data);
    }

    public void ymf262_data_b_w(int chipId, int offset, int data) {
        YmF262 chip = chips[chipId];

        chip.write(3, data);
    }

    public void ymf262_set_emu_core(int emulator) {
        this.emuCore = emulator;
    }

    public void ymf262_set_mute_mask(int chipId, int muteMask) {
        YmF262 chip = chips[chipId];
        chip.setMuteMask(muteMask);
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
        return writeInternal(chipId, (port * 0x100 + adr), data);
    }

    private int writeInternal(int chipId, int adr, int data) {
        YmF262 chip = chips[chipId];
        chip.write((adr & 0x100) != 0 ? 0x02 : 0x00, adr);
        chip.write((adr & 0x100) != 0 ? 0x03 : 0x01, data);
        return 0;
    }

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
            case "NAME" -> result.put(getName(), "YMF262");
            case "FAMILY" -> result.put(getName(), "Yamaha FM");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
