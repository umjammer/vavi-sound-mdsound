package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Rf5c68;


public class Rf5C68Inst extends Instrument.BaseInstrument {

    public static final int MAX_CHIPS = 0x02;

    private final Rf5c68[] chips = {new Rf5c68(), new Rf5c68()};

    @Override
    public String getName() {
        return "RF5C68";
    }

    @Override
    public String getShortName() {
        return "RF68";
    }

    public Rf5C68Inst() {
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
        return chips[chipId].readMemory(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write(adr, data);
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
    public synchronized void setMask(int chipId, int ch) {
        chips[chipId].setMuteMask(1);
    }

    @Override
    public synchronized void resetMask(int chipId, int ch) {
        chips[chipId].setMuteMask(0);
    }

    public void writeRam(int chipId, int dataStart, int dataLength, byte[] ramData) {
        chips[chipId].writeRam(dataStart, dataLength, ramData);
    }

    //----

    public synchronized void writePcm(int chipId, int ramStartAdr, int ramDataLength, byte[] srcData, int srcStartAdr) {
        chips[chipId].writeRam(ramStartAdr, ramDataLength, srcData, srcStartAdr);
    }

    public synchronized void writeMemory(int chipId, int adr, int data) {
        chips[chipId].writeMemory(adr, data);
    }

    public synchronized Rf5c68 getChip(int chipId) {
        return chips[chipId];
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0xB0, 1d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "RF5C68");
            case "FAMILY" -> result.put(getName(), "Ricoh PCM");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
