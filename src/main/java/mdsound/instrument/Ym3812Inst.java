package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.DosboxYm3812;


public class Ym3812Inst extends Instrument.BaseInstrument {

    public static final int MAX_CHIPS = 0x02;
    public static final int DefaultClockValue = 3579545;

    private final DosboxYm3812[] chips = new DosboxYm3812[] {new DosboxYm3812(), new DosboxYm3812()};

    private byte emuCore;

    @Override
    public void reset(int chipId) {
        DosboxYm3812 info = chips[chipId];
        info.reset();
        visVolume = new int[][][] {
                {{0, 0}},
                {{0, 0}}
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
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        return startInternal(chipId, clock);
    }

    @Override
    public int read(int chipId, int adr) {
        DosboxYm3812 chip = chips[chipId];
        return chip.read(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        DosboxYm3812 chip = chips[chipId];
        if (chip == null) return 0;

        chip.write(0, adr);
        chip.write(1, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        DosboxYm3812 chip = chips[chipId];
        chip.updateStream(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        DosboxYm3812 chip = chips[chipId];
        chip.stop();
    }

    private int startInternal(int chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        DosboxYm3812 info = chips[chipId];
        int rate = (clock & 0x7fff_ffff) / 72;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;
        //info.intf = device.static_config ? (ym3812_interface) device.static_config : dummy;
        //info.intf = dummy;
        //info.device = device;

        info.start(emuCore, clock, rate, this::updateStream);

        return rate;
    }

    private final int[][] dummyBuf = {null, null};

    private void updateStream(/*, int interval*/) {
        chips[0].updateStream(dummyBuf, 0); // TODO
    }

    public int readStatusPort(int chipId, int offset) {
        return read(chipId, 0);
    }

    public int readPort(int chipId, int offset) {
        return read(chipId, 1);
    }

    public void setEmuCore(byte Emulator) {
        emuCore = (byte) ((Emulator < 0x02) ? Emulator : 0x00);
    }

    public void setMuteMask(int chipId, int muteMask) {
        DosboxYm3812 info = chips[chipId];
        info.setMuteMask(muteMask);
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
            case "NAME" -> result.put(getName(), "YM3812");
            case "FAMILY" -> result.put(getName(), "Yamaha FM");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
