package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.np.chip.Emu2149.Psg;


/** PSG np emu */
public class NpYm2149Inst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 1789750;

    private final Psg[] chips = {new Psg(), new Psg()};

    private final int[] mask = {0, 0};

    public NpYm2149Inst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "Ym2149np";
    }

    @Override
    public String getShortName() {
        return "PSGnp";
    }

    @Override
    public void init() {
        mask[0] = 0;
        mask[1] = 0;
    }

    @Override
    public void reset(int chipId) {
        assert chipId < chips.length;
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId].init(DefaultClockValue * 2, samplingRate); // TODO clock is 4 times than others
        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < chips.length;
        chips[chipId].writeReg(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        assert chipId < chips.length;

        for (int i = 0; i < 1; i++) {
            int v = chips[chipId].calcPsg(); // TODO is this correct? do this twice and half clock seems to work.
            outputs[0][i] = v;
            outputs[1][i] = v;
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
    }

    @Override
    public synchronized void setMask(int chipId, int ch) {
        mask[chipId] |= ch;
        setMute(chipId, mask[chipId]);
    }

    @Override
    public synchronized void resetMask(int chipId, int ch) {
        mask[chipId] &= ~ch;
        setMute(chipId, mask[chipId]);
    }

    public void setVolume(int chipId, int db) {
        assert chipId < chips.length;
    }

    private void setMute(int chipId, int val) {
        assert chipId < chips.length;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 2d);
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
