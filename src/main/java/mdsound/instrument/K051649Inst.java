package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.K051649;


public class K051649Inst extends Instrument.BaseInstrument {

    public static final int MAX_CHIPS = 0x02;

    private final K051649[] chips = {new K051649(), new K051649()};

    @Override
    public String getName() {
        return "K051649";
    }

    @Override
    public String getShortName() {
        return "K051";
    }

    @Override
    public void reset(int chipId) {
        K051649 chip = chips[chipId];
        chip.reset();
        visVolume = new int[][][] {
                {{0, 0}},
                {{0, 0}}
        };
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... Option) {
        assert chipId < MAX_CHIPS;
        K051649 chip = chips[chipId];
        int rate = chip.start(clock);

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
        K051649 chip = chips[chipId];
        chip.write(adr, data);
        return 0;
    }

    /** generate Sound to the mix buffer */
    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        K051649 chip = chips[chipId];
        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
    }

    //
    public void writeWaveform(int chipId, int offset, int data) {
        K051649 chip = chips[chipId];
        chip.writeWaveForm(offset, data);
    }

    public int readWaveform(int chipId, int offset) {
        K051649 chip = chips[chipId];
        return chip.readWaveForm(offset);
    }

    /* SY 20001114: Channel 5 doesn't share the waveform with channel 4 on this chips */
    public void writeK052539Waveform(int chipId, int offset, byte data) {
        K051649 chip = chips[chipId];
        chip.writeWaveFormK05239(offset, data);
    }

    public int readK052539Waveform(int chipId, int offset) {
        K051649 chip = chips[chipId];
        return chip.readWaveFormK05239(offset);
    }

    public void setVolume(int chipId, int offset, byte data) {
        K051649 chip = chips[chipId];
        chip.writeVolume(offset, data);
    }

    public void setFrequency(int chipId, int offset, byte data) {
        K051649 chip = chips[chipId];
        chip.writeFrequency(offset, data);
    }

    public void setKeyOnOff(int chipId, int offset, byte data) {
        K051649 chip = chips[chipId];
        chip.writeKeyOnOff(offset, data);
    }

    public void setTest(int chipId, int offset, int data) {
        K051649 chip = chips[chipId];
        chip.writeTest(offset, data);
    }

    public int readTest(int chipId, int offset) {
        // reading the test register sets it to $ff!
        setTest(chipId, offset, 0xff);
        return 0xff;
    }

    public void setMuteMask(int chipId, int muteMask) {
        K051649 info = chips[chipId];
        info.setMuteMask(muteMask);
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
