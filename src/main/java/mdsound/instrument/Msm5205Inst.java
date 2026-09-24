/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Msm5205;


/**
 * OKI MSM5205 / MSM6585 ADPCM.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-09-24 nsano initial version <br>
 */
public class Msm5205Inst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x02;

    private final Msm5205[] chips = {new Msm5205(), new Msm5205()};

    public Msm5205Inst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "MSM5205";
    }

    @Override
    public String getShortName() {
        return "MSM5";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    /**
     * @param option 0: (int) flags, bit 1-0: prescaler (S1, S2), bit 2: 4 bit (else 3 bit), bit 7: MSM6585,
     *               1: (BiConsumer&lt;Integer, Integer&gt;) the sampling rate change callback, may be absent,
     *               2: (int) the sampling rate the callback reports as the old one
     */
    @Override
    @SuppressWarnings("unchecked")
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        int flags = option != null && option.length > 0 ? (int) option[0] : 0x04;
        int prescaler = flags & 0x03;
        int bitWidth = (flags & 0x04) != 0 ? 4 : 3;
        boolean isMsm6585 = (flags & 0x80) != 0;
        if (option != null && option.length > 2) {
            BiConsumer<Integer, Integer> callback = (BiConsumer<Integer, Integer>) option[1];
            int oldSampleRate = (int) option[2];
            chips[chipId].setSampleRateChanged(newSampleRate -> callback.accept(oldSampleRate, newSampleRate));
        }

        return chips[chipId].start(clock, prescaler, bitWidth, isMsm6585);
    }

    @Override
    public void stop(int chipId) {
    }

    @Override
    public int read(int chipId, int adr) {
        return 0;
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
    public void setMask(int chipId, int ch) {
        chips[chipId].setMute(true);
    }

    @Override
    public void resetMask(int chipId, int ch) {
        chips[chipId].setMute(false);
    }

    //----

    private Map<String, Object> getInfo(int chipId) {
        Msm5205 chip = chips[chipId];

        Map<String, Object> info = new HashMap<>();
        info.put("masterClock", chip.getMasterClock());
        info.put("rate", chip.getRate());
        info.put("reset", chip.isReset());
        info.put("mute", chip.isMuted());
        info.put("signal", chip.getSignal());
        info.put("step", chip.getStep());
        info.put("data", chip.getLastData());
        info.put("bitWidth", chip.getBitWidth());
        info.put("idleSamples", chip.getIdleSamples());
        return info;
    }

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
            case "NAME" -> result.put(getName(), chips[chipId].isMsm6585() ? "MSM6585" : "MSM5205");
            case "FAMILY" -> result.put(getName(), "OKI ADPCM");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Aaron Giles, eito, cam900, Valley Bell, Mao");
            case "info" -> result.putAll(getInfo(chipId));
        }
        return result;
    }
}
