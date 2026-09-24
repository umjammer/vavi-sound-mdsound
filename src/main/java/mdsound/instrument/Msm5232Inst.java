/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import vavi.util.compat.Tuple;
import mdsound.Instrument.BaseInstrument;
import mdsound.chips.Msm5232;


/**
 * OKI MSM5232 8 channel tone generator.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-02 nsano initial version <br>
 */
public class Msm5232Inst extends BaseInstrument {

    private static final int MAX_CHIPS = 0x02;

    private final Msm5232[] chips = {new Msm5232(), new Msm5232()};

    private final int[] muteMask = new int[MAX_CHIPS];

    public Msm5232Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "MSM5232";
    }

    @Override
    public String getShortName() {
        return "MSM5232";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    /**
     * @param option 0: (double[]) the eight external capacitors in Farads, null for 1 µF each,
     *               1: (BiConsumer&lt;Integer, Integer&gt;) the sampling rate change callback, may be absent,
     *               2: (int) the sampling rate the callback reports as the old one
     */
    @Override
    @SuppressWarnings("unchecked")
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        double[] capacitors = option != null && option.length > 0 ? (double[]) option[0] : null;
        Msm5232 chip = chips[chipId];
        if (option != null && option.length > 2) {
            BiConsumer<Integer, Integer> callback = (BiConsumer<Integer, Integer>) option[1];
            int oldSampleRate = (int) option[2];
            chip.setSampleRateChanged(() -> callback.accept(oldSampleRate, chip.getRate()));
        }
        muteMask[chipId] = 0;
        return chip.start(clock, capacitors);
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
    public void stop(int chipId) {
    }

    /** @param ch a voice, 0..7 */
    @Override
    public void setMask(int chipId, int ch) {
        muteMask[chipId] |= 1 << ch;
        chips[chipId].setMuteMask(muteMask[chipId]);
    }

    @Override
    public void resetMask(int chipId, int ch) {
        muteMask[chipId] &= ~(1 << ch);
        chips[chipId].setMuteMask(muteMask[chipId]);
    }

    //----

    private Map<String, Object> getInfo(int chipId) {
        Msm5232 chip = chips[chipId];

        Map<String, Object> info = new HashMap<>();
        info.put("clock", chip.getClock());
        info.put("control1", chip.getControl(0));
        info.put("control2", chip.getControl(1));
        info.put("extVol1", chip.getExtVol(0));
        info.put("extVol2", chip.getExtVol(1));
        List<Map<String, Object>> voices = new ArrayList<>();
        for (int ch = 0; ch < Msm5232.CHANNELS; ch++) {
            Msm5232.Voice v = chip.getVoice(ch);
            Map<String, Object> voice = new HashMap<>();
            voice.put("pitch", v.getPitch());
            voice.put("keyOn", v.isKeyOn());
            voice.put("noise", v.isNoise());
            voice.put("egSection", v.getEgSection());
            voice.put("egVolume", v.getEgVolume());
            voice.put("mute", (muteMask[chipId] & (1 << ch)) != 0);
            voices.add(voice);
        }
        info.put("voices", voices);
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
            case "NAME" -> result.put(getName(), "MSM5232");
            case "FAMILY" -> result.put(getName(), "OKI tone generator");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Jarek Burczynski, Hiromitsu Shioya, Angelo Salese, Mao, cam900");
            case "info" -> result.putAll(getInfo(chipId));
        }
        return result;
    }
}
