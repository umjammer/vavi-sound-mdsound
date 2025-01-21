package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.X1_010;


public class X1_010Inst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 16000000;

    public static final int MAX_CHIPS = 0x02;

    private final X1_010[] chips = {new X1_010(), new X1_010()};

    private final int[] mask = {0, 0};

    public X1_010Inst() {
    }

    @Override
    public String getName() {
        return "X1-010";
    }

    @Override
    public String getShortName() {
        return "X1-010";
    }

    @Override
    public void reset(int chipId) {
        X1_010 chip = chips[chipId];
        chip.reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        return startInternal(chipId, clock);
    }

    @Override
    public int read(int chipId, int adr) {
        X1_010 chip = chips[chipId];
        return chip.read(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        X1_010 chip = chips[chipId];
        chip.write((port << 8) | adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        X1_010 chip = chips[chipId];
        chip.update(outputs, samples);
    }

    @Override
    public void stop(int chipId) {
        X1_010 chip = chips[chipId];
        chip.stop();
    }

    private int startInternal(int chipId, int clock) {
        if (chipId >= MAX_CHIPS) return 0;

        X1_010 chip = chips[chipId];

        int rate = clock / 512;
        if (((Instrument.BaseInstrument.CHIP_SAMPLING_MODE & 0x01) != 0 && rate < Instrument.BaseInstrument.CHIP_SAMPLE_RATE) ||
                Instrument.BaseInstrument.CHIP_SAMPLING_MODE == 0x02)
            rate = Instrument.BaseInstrument.CHIP_SAMPLE_RATE;
        chip.start(clock, rate);
        return rate;
    }

    private void setMuteMask(int chipId, int muteMask) {
        X1_010 info = chips[chipId];
        info.setMuteMask(muteMask);
    }

    //----

    public synchronized void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        X1_010 chip = chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAdr);
    }

    public synchronized void setMask(int chipId, int ch) {
        mask[chipId] |= ch;
        setMuteMask(chipId, mask[chipId]);
    }

    public synchronized void resetMask(int chipId, int ch) {
        mask[chipId] &= ~ch;
        setMuteMask(chipId, mask[chipId]);
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "NAME" -> result.put(getName(), "X1-010");
            case "FAMILY" -> result.put(getName(), "Seta custom");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
