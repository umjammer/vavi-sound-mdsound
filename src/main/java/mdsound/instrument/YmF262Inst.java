package mdsound.instrument;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.YmF262;

import static java.lang.System.getLogger;


public class YmF262Inst extends Instrument.BaseInstrument {

    private static final Logger logger = getLogger(YmF262Inst.class.getName());

    public static final int DefaultClockValue = 14318180;
    public static final int MAX_CHIPS = 0x02;

    private final YmF262[] chips = {new YmF262(), new YmF262()};

    private int emuCore = YmF262.EC_DBOPL;

    public YmF262Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "YMF262" + (emuCore == YmF262.EC_MAME ? "mame" : "db");
    }

    @Override
    public String getShortName() {
        return "Opl3";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    /**
     * @param option [0] <Integer> ... 0: dosbox, 1: mame
     */
    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        int rate = clock / 288;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) || CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;

logger.log(Level.TRACE, "option: " + Arrays.toString(option));
        if (option.length > 0 && option[0] instanceof Integer) emuCore = (int) option[0];
        chips[chipId].start(emuCore, clock, rate, this::updateHandler);

        return rate;
    }

    @Override
    public int read(int chipId, int adr) {
        return chips[chipId].read(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write((port << 1) | 0x00, adr);
        chips[chipId].write((port << 1) | 0x01, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].update(outputs, samples);

//logger.log(Level.TRACE, "output %d %d".formatted(outputs[0][0], outputs[1][0]));
        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].stop();
    }

    @Override
    public void setMask(int chipId, int ch) {
//        chips[chipId].setMuteMask(ch); // TODO
    }

    @Override
    public void resetMask(int chipId, int ch) {
//        chips[chipId].setMuteMask(~ch); // TODO
    }

    public void setEmuCore(int emulator) {
        emuCore = (emulator < 0x02) ? emulator : 0x00;
    }

    private final int[][] dummyBuf = {null, null};

    private void updateHandler() {
        chips[0].update(dummyBuf, 0);
    }

    public int readStatus(int chipId, int offset) {
        return read(chipId, 0);
    }

    public void writeRegisterA(int chipId, int offset, int data) {
        chips[chipId].write(0, data);
    }

    public void writeRegisterB(int chipId, int offset, int data) {
        chips[chipId].write(2, data);
    }

    public void writeDataA(int chipId, int offset, int data) {
        chips[chipId].write(1, data);
    }

    public void writeDataB(int chipId, int offset, int data) {
        chips[chipId].write(3, data);
    }

    private void irqHandler(int irq) {
    }

    private void timerHandler(int timer, int period) {
        if (period == 0) { // Reset FM Timer
//            timer_enable(info.timer[timer], 0);
        } else { // Start FM Timer
//            timer_adjust_oneshot(info.timer[timer], period, 0);
        }
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
