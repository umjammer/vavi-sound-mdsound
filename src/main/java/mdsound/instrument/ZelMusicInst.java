/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import java.util.Map;

import mdsound.Instrument;
import mdsound.zm1.ZelMusic;
import vavi.util.compat.Tuple;


/**
 * ZelMusicInst.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-08 nsano initial version <br>
 */
public class ZelMusicInst implements Instrument {

    private final ZelMusic[] chips = {new ZelMusic(), new ZelMusic()};

    @Override
    public String getName() {
        return "ZelMusic";
    }

    @Override
    public String getShortName() {
        return "ZM-1";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId].start(samplingRate, clock);
        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        return 0;
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].stop();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].update(outputs, samples);
    }

    @Override
    public int write(int chipId, int bank, int adr, int data) {
        chips[chipId].write(bank, adr, data);
        return 0;
    }

    @Override
    public void setMask(int chipId, int ch) {

    }

    @Override
    public void resetMask(int chipId, int ch) {

    }

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return null;
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        return Map.of();
    }
}
