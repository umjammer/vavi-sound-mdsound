/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument.BaseInstrument;
import mdsound.chips.K005289;


/**
 * Konami 005289 2 channel wavetable sound.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-09-24 nsano initial version <br>
 */
public class K005289Inst extends BaseInstrument {

    private static final int MAX_CHIPS = 0x02;

    private final K005289[] chips = {new K005289(), new K005289()};

    private final int[] muteMask = new int[MAX_CHIPS];

    public K005289Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "K005289";
    }

    @Override
    public String getShortName() {
        return "K005";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        muteMask[chipId] = 0;
        return chips[chipId].start(clock);
    }

    @Override
    public int read(int chipId, int adr) {
        return 0;
    }

    /** @param adr 0/1 control A/B, 2/3 LD1/LD2, 4/5 TG1/TG2 */
    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write(adr, data);
        return 0;
    }

    /** loads the waveform prom, 0x100 bytes per channel */
    public void writeProm(int chipId, int offset, byte[] data, int dataOffset, int length) {
        chips[chipId].writeProm(offset, data, dataOffset, length);
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

    /** @param ch a channel, 0..1 */
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
        K005289 chip = chips[chipId];

        Map<String, Object> info = new HashMap<>();
        info.put("clock", chip.getClock());
        List<Map<String, Object>> voices = new ArrayList<>();
        for (int ch = 0; ch < K005289.CHANNELS; ch++) {
            K005289.Voice v = chip.getVoice(ch);
            Map<String, Object> voice = new HashMap<>();
            voice.put("freq", v.getFreq());
            voice.put("volume", v.getVolume());
            voice.put("waveform", v.getWaveform());
            voice.put("wave", chip.getWave(ch));
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
            case "NAME" -> result.put(getName(), "K005289");
            case "FAMILY" -> result.put(getName(), "Konami wavetable");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Bryan McPhail, Mao, cam900");
            case "info" -> result.putAll(getInfo(chipId));
        }
        return result;
    }
}
