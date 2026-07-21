package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.OkiM6258;


public class OkiM6258Inst extends Instrument.BaseInstrument {

    public static final int MAX_CHIPS = 0x02;

    private final OkiM6258[] chips = {new OkiM6258(), new OkiM6258()};

    public OkiM6258Inst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "OKIM6258";
    }

    @Override
    public String getShortName() {
        return "OKI5";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    /** @param option 0: (int) type, 1: (BiConsumer<Integer, Integer>) fn, 2: (int) sampleRate */
    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        int type = (int) option[0];
        int divider = (type & 0x03) >> 0;
        int adpcmType = (type & 0x04) >> 2;
        int output12Bits = (type & 0x08) >> 3;
        BiConsumer<Integer, Integer> callbackFunc = (BiConsumer<Integer, Integer>) option[1];
        int oldSampleRate = (int) option[2];
        chips[chipId].setCallback(newSamplingRate -> callbackFunc.accept(oldSampleRate, newSamplingRate));

        return chips[chipId].start(clock, divider, adpcmType, output12Bits);
    }

    @Override
    public int read(int chipId, int adr) {
//        // read the status port of an OKIM6258-compatible chips
//        OkiM6258 chip = chips[chipId];
//
//        return (chip.status & STATUS_PLAYING) ? 0x00 : 0x80;

        throw new UnsupportedOperationException();
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
    }

    @Override
    public void setMask(int chipId, int ch) {
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }

    public void setDivider(int chipId, int val) {
        chips[chipId].setDivider(val);
    }

    public void setClock(int chipId, int val) {
        chips[chipId].setClock(val);
    }

    public int getVclk(int chipId) {
        return chips[chipId].getVclk();
    }

    public void writeData(int chipId, /* int offset, */ int data) {
        chips[chipId].data_write(data);
    }

    public void writeCtrl(int chipId, /* int offset, */ int data) {
        chips[chipId].writeControl(data);
    }

    public void setClockByte(int chipId, int byte_, int val) {
        chips[chipId].setClock(val);
    }

    public void writePan(int chipId, int data) {
        chips[chipId].writePan(data);
    }

    public static void setOptions(int options) {
        OkiM6258.setOptions(options);
    }

    //----

    private synchronized Map<String, Object> getInfo(int chipId) {
        OkiM6258 chip = chips[chipId];

        Map<String, Object> info = new HashMap<>();
        info.put("pan", chip.getPan());
        info.put("masterFreq", chip.getMasterClock() / 1000);
        info.put("divider", chip.getDivider());
        if (chip.getDivider() == 0) info.put("pbFreq", 0);
        else info.put("pbFreq", chip.getMasterClock() / chip.getDivider() / 1000);

        info.put("dataIn", chip.getDataIn());
        info.put("status", chip.getStatus());

        info.put("keyOn", chip.getKeyOn());

        return info;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x1C0, 2d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Object... args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "OKI6258");
            case "FAMILY" -> result.put(getName(), "OKI ADPCM");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
            case "info" -> result.putAll(getInfo(chipId));
        }
        return result;
    }
}
