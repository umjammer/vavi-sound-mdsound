package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.Y8950;


public class Y8950Inst extends Instrument.BaseInstrument implements PcmEnabledInstrument {

    protected static final int CHIP_SAMPLING_MODE = 0;

    public static final int DefaultClockValue = 3579545;
    public static final int MAX_CHIPS = 0x02;

    private final Y8950[] chips = {new Y8950(), new Y8950()};

    public Y8950Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
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
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        int rate = clock / 72;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) || CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;

        // stream system initialize
        chips[chipId].start(clock, rate);

        // port and keyboard handler
        chips[chipId].setPortHandler(this::writePort, this::readPort);
        chips[chipId].setKeyboardHandler(this::writeKeyboard, this::readKeyboard);

        // Y8950 setup
        chips[chipId].setTimerHandler(this::doTimer);
        chips[chipId].setIrqHandler(this::doIrq);
        chips[chipId].setUpdateHandler(this::updateStream);

        //this.timer[0] = timer_alloc(device.machine, timer_callback_0, info);
        //this.timer[1] = timer_alloc(device.machine, timer_callback_1, info);

        return rate;
    }

    @Override
    public int read(int chipId, int adr) {
        return chips[chipId].read(adr & 1);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write(0x00 & 1, adr);
        chips[chipId].write(0x01 & 1, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].update(outputs, samples);

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

    /** @param extras 0: srcOffset, 1: romSize */
    @Override
    public synchronized void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
        int srcOffset = (int) extras[0];
        int romSize = (int) extras[1];
        chips[chipId].writePcmRom(romSize, offset, length, buf, srcOffset);
    }

    public int readStatusPort(int chipId, int offset) {
        return read(chipId, 0);
    }

    public int readPort(int chipId, int offset) {
        return read(chipId, 1);
    }

    public void writeControlPort(int chipId, int offset, int data) {
        chips[chipId].write(0 & 1, data);
    }

    public void writePort(int chipId, int offset, int data) {
        chips[chipId].write(1 & 1, data);
    }

    private void doIrq(int irq) {
//        if (info.intf.handler) (info.intf.handler) (info.device, irq ? ASSERT_LINE : CLEAR_LINE);
//        if (info.intf.handler) (info.intf.handler) (irq ? ASSERT_LINE : CLEAR_LINE);
    }

    private void doTimer(int c, int period) {
//        if( attotime_compare(period, attotime_zero) == 0 )
        if (period == 0) { // Reset FM Timer
//            timer_enable(info.timer[c], 0);
        } else { // Start FM Timer
//            timer_adjust_oneshot(info.timer[c], period, 0);
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

    private final int[][] dummyBuf = {null, null};

    private void updateStream(int interval) {
        //stream_update(info.stream);
        chips[0].update(dummyBuf, 0);
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
            case "NAME" -> result.put(getName(), "Y8950");
            case "FAMILY" -> result.put(getName(), "Yamaha FM");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
