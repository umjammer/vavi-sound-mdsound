package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Pokey;


/** Atari Pokey */
public class PokeyInst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 1789772;
    public static final int MAX_CHIPS = 0x02;

    private final Pokey[] chips = {new Pokey(), new Pokey()};

    public PokeyInst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "POKEY";
    }

    @Override
    public String getShortName() {
        return "POKEY";
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
        chips[chipId].write(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].update(outputs, samples);
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

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "POKEY");
            case "info" -> {
                Pokey chip = chips[chipId];
                for (int ch = 0; ch < Pokey.CHANNELS; ch++) {
                    result.put("channels." + ch + ".audible", chip.isAudible(ch));
                    result.put("channels." + ch + ".volume", chip.getVolume(ch));
                    result.put("channels." + ch + ".divisor", chip.getDivisor(ch));
                    result.put("channels." + ch + ".mute", chip.isMuted(ch));
                }
            }
            case "FAMILY" -> result.put(getName(), "Atari custom");
            case "VERSION" -> result.put(getName(), "4.51");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
