package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.OotakeHuC6280;


// OotakeHuC6280
public class HuC6280Inst extends Instrument.BaseInstrument {

    public static final int DefaultHuC6280ClockValue = 3579545;

    private final OotakeHuC6280[] chips = {new OotakeHuC6280(), new OotakeHuC6280()};

    private final int[] mask = {0, 0};

    public HuC6280Inst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "HuC6280ootake";
    }

    @Override
    public String getShortName() {
        return "HuC8";
    }

    @Override
    public void reset(int chipId) {
        assert chipId < chips.length;
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < chips.length;
        chips[chipId].init(clock, samplingRate);
        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        assert chipId < chips.length;
        return chips[chipId].read(adr);
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
        chips[chipId].mix(outputs, samples);

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

    private void setMute(int chipId, int val) {
        assert chipId < chips.length;
        chips[chipId].setMuteMask(val);
    }

    public void setVolume(int chipId, int db) {
        assert chipId < chips.length;
    }

    //----

    private synchronized Map<String, Object> getInfo(int chipId) {
        OotakeHuC6280 chip = chips[chipId];

        Map<String, Object> newParam = new HashMap<>();
        for (int ch = 0; ch < 6; ch++) {
            OotakeHuC6280.Psg psg = chip.getPsg(ch);
            if (psg == null) continue;
            newParam.put("channels." + ch + ".volumeL", psg.outVolumeL >> 10);
            newParam.put("channels." + ch + ".volumeR", psg.outVolumeR >> 10);

            newParam.put("channels." + ch + ".pan",  (psg.volumeL & 0xf) | ((psg.volumeR & 0xf) << 4));

            newParam.put("channels." + ch + ".inst",  psg.wave);

            newParam.put("channels." + ch + ".dda",  psg.dda);

            int tp = psg.frq;
            if (tp == 0) tp = 1;

            float ftone = 3579545.0f / 32.0f / (float) tp;
            newParam.put("channels." + ch + ".ftone", ftone);

            if (ch < 4) continue;

            newParam.put("channels." + ch + ".noise",  psg.bNoiseOn);
            newParam.put("channels." + ch + ".nfrq",  psg.noiseFrq);
        }

        newParam.put("mvolL", chip.mainVolumeL);
        newParam.put("mvolR", chip.mainVolumeR);
        newParam.put("LfoCtrl", chip.lfoControl);
        newParam.put("LfoFrq", chip.lfoFreq);

        return newParam;
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
            case "info" -> result.putAll(getInfo(chipId));
        }
        return result;
    }
}
