package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.K051649;


public class K051649Inst extends Instrument.BaseInstrument {

    public static final int MAX_CHIPS = 0x02;

    private final K051649[] chips = {new K051649(), new K051649()};

    public K051649Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "K051649";
    }

    @Override
    public String getShortName() {
        return "K051";  // TODO SCC1
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... Option) {
        assert chipId < MAX_CHIPS;
        int rate = chips[chipId].start(clock);

//        int flags = 1;
//        if (Option != null && Option.length > 0) flags = (int)(byte)Option[0];
//        k054539_init_flags(chipId, flags);

        return rate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write(adr, data);
        return 0;
    }

    /** generate Sound to the mix buffer */
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
//        chips[chipId].setMuteMask(ch); // TODO
    }

    @Override
    public void resetMask(int chipId, int ch) {
//        chips[chipId].setMuteMask(~ch); // TODO
    }

    //
    public void writeWaveform(int chipId, int offset, int data) {
        chips[chipId].writeWaveForm(offset, data);
    }

    public int readWaveform(int chipId, int offset) {
        return chips[chipId].readWaveForm(offset);
    }

    /* SY 20001114: Channel 5 doesn't share the waveform with channel 4 on this chips */
    public void writeK052539Waveform(int chipId, int offset, byte data) {
        chips[chipId].writeWaveFormK05239(offset, data);
    }

    public int readK052539Waveform(int chipId, int offset) {
        return chips[chipId].readWaveFormK05239(offset);
    }

    public void setVolume(int chipId, int offset, byte data) {
        chips[chipId].writeVolume(offset, data);
    }

    public void setFrequency(int chipId, int offset, byte data) {
        chips[chipId].writeFrequency(offset, data);
    }

    public void setKeyOnOff(int chipId, int offset, byte data) {
        chips[chipId].writeKeyOnOff(offset, data);
    }

    public void setTest(int chipId, int offset, int data) {
        chips[chipId].writeTest(offset, data);
    }

    public int readTest(int chipId, int offset) {
        // reading the test register sets it to $ff!
        setTest(chipId, offset, 0xff);
        return 0xff;
    }

    //----

    public synchronized K051649 getChip(int chipId) {
        return chips[chipId];
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0xA0, 1d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "K051649");
            case "FAMILY" -> result.put(getName(), "Konami custom");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
