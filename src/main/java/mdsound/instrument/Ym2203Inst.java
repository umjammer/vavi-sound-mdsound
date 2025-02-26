package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.fmgen.Opna;


public class Ym2203Inst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 3000000;

    private final Opna.OPN[] chips = {new Opna.OPN(), new Opna.OPN()};

    private final int[] mask = {0, 0};
    private final int[][] keyOn = {new int[6], new int[6]};

    public Ym2203Inst() {
        // 0..Main 1..FM 2..SSG
        visVolume = new int[][][] {{{0, 0}, {0, 0}, {0, 0}}, {{0, 0}, {0, 0}, {0, 0}}};
    }

    @Override
    public void init() {
        mask[0] = 0;
        mask[1] = 0;
    }

    @Override
    public String getName() {
        return "YM2203";
    }

    @Override
    public String getShortName() {
        return "OPN";
    }

    @Override
    public void reset(int chipId) {
        assert chipId < chips.length;
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId].init(clock, samplingRate);

        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < chips.length;
        chips[chipId].setReg(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        assert chipId < chips.length;

        int[] buffer = new int[2];
        buffer[0] = 0;
        buffer[1] = 0;
        chips[chipId].mix(buffer, 1);
        for (int i = 0; i < 1; i++) {
            outputs[0][i] = buffer[i * 2 + 0];
            outputs[1][i] = buffer[i * 2 + 1];
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
        visVolume[chipId][1][0] = chips[chipId].visVolume[0];
        visVolume[chipId][1][1] = chips[chipId].visVolume[1];
        visVolume[chipId][2][0] = chips[chipId].psg.visVolume;
        visVolume[chipId][2][1] = chips[chipId].psg.visVolume;
    }

    @Override
    public void stop(int chipId) {
    }

    @Override
    public synchronized void setMask(int chipId, int ch) {
        mask[chipId] |= ch;
        assert chipId < chips.length;
        chips[chipId].setChannelMask(mask[chipId]);
    }

    @Override
    public synchronized void resetMask(int chipId, int ch) {
        mask[chipId] &= ~ch;
        assert chipId < chips.length;
        chips[chipId].setChannelMask(mask[chipId]);
    }

    // ----

    public synchronized int[] readKeyOn(int chipId) {
//        for (int i = 0; i < 6; i++) {
//            keyOn[chipId][i] = chips[chipId].CHANNEL[i].KeyOn;
//        }
        return keyOn[chipId];
    }

    public void setVolume(String tag, int vol, double ignored) {
        switch (tag) {
            case "FM" -> {
                chips[0].setVolumeFM(vol);
                chips[1].setVolumeFM(vol);
            }
            case "SSG" -> {
                chips[0].setVolumePSG(vol);
                chips[1].setVolumePSG(vol);
            }
        }
    }

    // ----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        // mul=0.5 SSG
        return new Tuple<>(0x100, 1d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" -> {
                result.put("ym2203", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
                result.put("ym2203:FM", getMonoVolume(visVolume[0][1][0], visVolume[0][1][1], visVolume[1][1][0], visVolume[1][1][1]));
                result.put("ym2203:SSG", getMonoVolume(visVolume[0][2][0], visVolume[0][2][1], visVolume[1][2][0], visVolume[1][2][1]));
            }
        }
        return result;
    }
}
