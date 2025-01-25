package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import dotnet4j.io.Stream;
import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.YmF278B;


public class YmF278BInst extends Instrument.BaseInstrument implements PcmEnabledInstrument {

    public static final int MAX_CHIPS = 0x10;

    private final YmF278B[] chips = {new YmF278B(), new YmF278B()};

    @Override
    public String getName() {
        return "YMF278B";
    }

    @Override
    public String getShortName() {
        return "OPL4";
    }

    public YmF278BInst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    /**
     * @param option String:, Function<String, Stream>:. TODO
     */
    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        String romPath = null;
        Function<String, Stream> romStream = null;
        if (option != null && option.length > 0) {
            if (option[0] instanceof String) {
                romPath = (String) option[0];
                romStream = null;
            }
            if (option[0] instanceof Function /*<String, Stream>*/) {
                romPath = null;
                romStream = (Function<String, Stream>) option[0];
            }
        }

        return chips[chipId].start(clock, romPath, romStream);
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write((port << 1) | 0x00, adr);
        chips[chipId].write((port << 1) | 0x01, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].updatePcm(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].stop();
    }

    @Override
    public void setMask(int chipId, int ch) {
//        chips[chipId].setMuteMask(fm, wf); // TODO
    }

    @Override
    public void resetMask(int chipId, int ch) {
//        chips[chipId].setMuteMask(fm, wf); // TODO
    }

    /** @param extras 0: srcStartAddress, 1: romSize */
    @Override
    public synchronized void writePcm(int chipId, byte[] romData, int dataStart, int dataLength, Object... extras) {
        int srcStartAdr = (int) extras[0];
        int romSize = (int) extras[1];
        chips[chipId].writeRom(romSize, dataStart, dataLength, romData, srcStartAdr);
    }

    //----

    public synchronized void writeRam(int chipId, int RAMSize, int dataStart, int dataLength, byte[] ramData, int srcStartAdr) {
        chips[chipId].writeRam(dataStart, dataLength, ramData, srcStartAdr);
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
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        }
        return result;
    }
}
