package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Y8950;


public class Y8950Inst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 3579545;
    public static final int MAX_CHIPS = 0x02;

    private final Y8950[] chips = {new Y8950(), new Y8950()};

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
        Y8950 info = chips[chipId];
        info.reset();

        visVolume = new int[][][] {
                {{0, 0}},
                {{0, 0}}
        };
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        if (chipId >= MAX_CHIPS) return 0;

        Y8950 chip = chips[chipId];

        int rate = clock / 72;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < BaseInstrument.CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = BaseInstrument.CHIP_SAMPLE_RATE;
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
        chip.setUpdateHandler(this::updateStream);

        //this.timer[0] = timer_alloc(device.machine, timer_callback_0, info);
        //this.timer[1] = timer_alloc(device.machine, timer_callback_1, info);

        return rate;
    }

    @Override
    public int read(int chipId, int adr) {
        Y8950 chip = chips[chipId];
        return chip.read(adr & 1);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        Y8950 chip = chips[chipId];
        chip.write(0x00 & 1, adr);
        chip.write(0x01 & 1, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        Y8950 info = chips[chipId];
        info.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        Y8950 info = chips[chipId];
        info.stop();
    }

    public int readStatusPort(int chipId, int offset) {
        return read(chipId, 0);
    }

    public int readPort(int chipId, int offset) {
        return read(chipId, 1);
    }

    public void writeControlPort(int chipId, int offset, int data) {
        Y8950 info = chips[chipId];
        info.write(0 & 1, data);
    }

    public void writePort(int chipId, int offset, int data) {
        Y8950 info = chips[chipId];
        info.write(1 & 1, data);
    }

    public void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        writePcm(chipId, romSize, dataStart, dataLength, romData, 0);
    }

    public void setMuteMask(int chipId, int muteMask) {
        Y8950 info = chips[chipId];
        info.setMuteMask(muteMask);
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

    public synchronized void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        Y8950 chip = chips[chipId];
        chip.writePcmRom(romSize, dataStart, dataLength, romData, srcStartAdr);
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
