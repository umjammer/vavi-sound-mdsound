package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Saa1099;


public class Saa1099Inst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x02;
    public static final int DefaultClockValue = 8000000;

    private final Saa1099[] chips = {new Saa1099(), new Saa1099()};

    private final int[] mask = {0, 0};

    public Saa1099Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "SAA1099";
    }

    @Override
    public String getShortName() {
        return "SAA";
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
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].writeControl(0, adr);
        chips[chipId].write(0, data);
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
    public synchronized void setMask(int chipId, int ch) {
        mask[chipId] |= ch;
        chips[chipId].setMuteMask(mask[chipId]);
    }

    @Override
    public synchronized void resetMask(int chipId, int ch) {
        mask[chipId] &= ~ch;
        chips[chipId].setMuteMask(mask[chipId]);
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Object... args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "SAA1099");
            case "FAMILY" -> result.put(getName(), "Philips");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
            case "channels" -> {
                Saa1099 chip = chips[chipId];
                result.put("enabled", chip.isEnabled());
                for (int ch = 0; ch < Saa1099.CHANNELS; ch++) {
                    result.put("channels." + ch + ".volumeL", chip.getAmplitude(ch, Saa1099.LEFT));
                    result.put("channels." + ch + ".volumeR", chip.getAmplitude(ch, Saa1099.RIGHT));
                    result.put("channels." + ch + ".frequency", chip.getFrequency(ch));
                    result.put("channels." + ch + ".octave", chip.getOctave(ch));
                    result.put("channels." + ch + ".tone", chip.isFrequencyEnabled(ch));
                    result.put("channels." + ch + ".noise", chip.isNoiseEnabled(ch));
                    result.put("channels." + ch + ".mute", chip.isMuted(ch));
                }
            }
        }
        return result;
    }
}
