package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.X1_010;


public class X1_010Inst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x02;
    private final X1_010[] x1010Data = new X1_010[] {new X1_010(), new X1_010()};

    @Override
    public String getName() {
        return "X1-010";
    }

    @Override
    public String getShortName() {
        return "X1-010";
    }

    public X1_010Inst() {
    }

    @Override
    public void reset(int chipId) {
        X1_010 info = x1010Data[chipId];
        info.reset();
    }

    @Override
    public int start(int chipId, int samplingRate) {
        return startInternal(chipId, 16000000);
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        return startInternal(chipId, clock);
    }

    @Override
    public void stop(int chipId) {
        X1_010 info = x1010Data[chipId];
        info.stop();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        X1_010 info = x1010Data[chipId];
        info.update(outputs, samples);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        X1_010 info = x1010Data[chipId];
        info.write((port << 8) | adr, data);
        return 0;
    }

    private int startInternal(int chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        X1_010 info = x1010Data[chipId];

        int rate = clock / 512;
        if (((Instrument.BaseInstrument.CHIP_SAMPLING_MODE & 0x01) != 0 && rate < Instrument.BaseInstrument.CHIP_SAMPLE_RATE) ||
                Instrument.BaseInstrument.CHIP_SAMPLING_MODE == 0x02)
            rate = Instrument.BaseInstrument.CHIP_SAMPLE_RATE;
        info.start(clock, rate);
        return rate;
    }

    private int seta_sound_r(int chipId, int offset) {
        X1_010 info = x1010Data[chipId];
        return info.read(offset);
    }

    public void x1_010_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int romDataStartAddress/* = 0*/) {
        X1_010 info = x1010Data[chipId];
        info.writeRom(romSize, dataStart, dataLength, romData, romDataStartAddress);
    }

    public void x1_010_set_mute_mask(int chipId, int muteMask) {
        X1_010 info = x1010Data[chipId];
        info.setMuteMask(muteMask);
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
