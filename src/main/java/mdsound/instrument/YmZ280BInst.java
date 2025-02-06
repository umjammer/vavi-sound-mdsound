package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.YmZ280B;


public class YmZ280BInst extends Instrument.BaseInstrument implements PcmEnabledInstrument {

    public static final int DefaultClockValue = 16934400;
    public static final int MAX_CHIPS = 0x10;

    private final YmZ280B[] chips = {new YmZ280B(), new YmZ280B()};

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
        chips[chipId].writeRom(romSize, offset, length, buf, srcOffset);
    }

    // handle external accesses

    private void updateIrqStateTimerCommon(Object param, int voiceNum) {
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
