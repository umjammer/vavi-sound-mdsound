package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Pokey;


public class PokeyInst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x02;
    private final Pokey[] chips = new Pokey[MAX_CHIPS];

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
        Pokey chip = chips[chipId];
        chip.reset();
    }

    @Override
    public int start(int chipId, int samplingRate) {
        return startInternal(chipId, 1789772);
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        return startInternal(chipId, clock);
    }

    @Override
    public void stop(int chipId) {
        Pokey chip = chips[chipId];
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        Pokey chip = chips[chipId];
        chip.update(outputs, samples);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        Pokey chip = chips[chipId];
        chip.write(adr, data);
        return 0;
    }

    private int startInternal(int chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        if (chips[chipId] == null) {
            chips[chipId] = new Pokey();
        }

        Pokey chip = chips[chipId];
        return chip.start(clock);
    }

    private int pokey_r(int chipId, int offset) {
        Pokey chip = chips[chipId];
        return chip.read(offset);
    }

    private void pokey_set_mute_mask(int chipId, int muteMask) {
        Pokey chip = chips[chipId];
        chip.setMuteMask(muteMask);
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
            case "NAME" -> result.put(getName(), "POKEY");
            case "FAMILY" -> result.put(getName(), "Atari custom");
            case "VERSION" -> result.put(getName(), "4.51");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
