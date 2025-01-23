package mdsound.instrument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mdsound.Instrument;
import mdsound.chips.Sn76496;


public class Sn76496Inst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 3579545;

    private final List<Sn76496> chips = new ArrayList<>();

    @Override
    public String getName() {
        return "Sn76496";
    }

    @Override
    public String getShortName() {
        return "DCSGmame";
    }

    @Override
    public void reset(int chipId) {
        assert chipId < chips.size();
        chips.get(chipId).reset();
    }

    /**
     * @param samplingRate sampleRate
     * @param clock masterClock
     * @param option int[4]
     */
    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        int stereo = 0;
        int negate = 0;
        int freq0 = 0;
        int divider = 0;
        int noisetaps = 9;
        int shiftreg = 16;

        if (option != null && option.length == 4) {
            noisetaps = (int) option[0] + (int) option[1] * 0x100;
            shiftreg = (int) option[2];

            freq0 = ((int) option[3] & 0x1) != 0 ? 1 : 0;
            negate = ((int) option[3] & 0x2) != 0 ? 1 : 0;
            stereo = ((int) option[3] & 0x4) != 0 ? 1 : 0;
            divider = ((int) option[3] & 0x8) != 0 ? 1 : 0;

        }

        if (chipId == 0) {
            Sn76496.lastChipInit = null;
        }

        Sn76496 chip = new Sn76496();
        int rate = chip.start(clock, shiftreg, noisetaps, negate, stereo, divider, freq0);
        chip.limitFreq(clock & 0x3fff_ffff, 0, samplingRate);

        while (chipId >= chips.size()) chips.add(null);
        chips.set(chipId, chip);

        return rate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param port unused
     */
    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < chips.size();
        chips.get(chipId).writeReg(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        assert chipId < chips.size();
        chips.get(chipId).update(outputs, samples);
    }

    @Override
    public void stop(int chipId) {
        assert chipId < chips.size();
        chips.get(chipId).stop();
    }

    // ----

    public synchronized void setPan(int chipId, int data) {
        assert chipId < chips.size();
        chips.get(chipId).writeStereo(0, data);
    }

    // ----

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "NAME" -> result.put(getName(), "Sn76496");
            case "FAMILY" -> result.put(getName(), "TI Psg");
            case "VERSION" -> result.put(getName(), "1.1");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
