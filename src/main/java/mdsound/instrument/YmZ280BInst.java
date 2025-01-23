package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.YmZ280b;


public class YmZ280BInst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 16934400;
    public static final int MAX_CHIPS = 0x10;

    private final YmZ280b[] chips = {new YmZ280b(), new YmZ280b()};

    @Override
    public String getName() {
        return "YMZ280B";
    }

    @Override
    public String getShortName() {
        return "YMZ";
    }

    public YmZ280BInst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;
        return chips[chipId].start(clock);
    }

    @Override
    public int read(int chipId, int adr) {
        return chips[chipId].read(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write(0x00, adr);
        chips[chipId].write(0x01, data);
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

    // handle external accesses

    public void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        writePcm(chipId, romSize, dataStart, dataLength, romData, 0);
    }

    public void setMuteMask(int chipId, int muteMask) {
        chips[chipId].setMuteMask(muteMask);
    }

    private void updateIrqStateTimerCommon(Object param, int voiceNum) {
    }

    //----

    public synchronized void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        chips[chipId].writeRom(romSize, dataStart, dataLength, romData, srcStartAdr);
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x98, 0x20 / 19.0);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "YMZ280B");
            case "FAMILY" -> result.put(getName(), "Yamaha Wavetable");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
