/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import uk.co.omgdrv.simplevgm.psg.nuked.NukedPsgProvider;


/**
 * YM7101 nuked version.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-01-28 nsano initial version <br>
 */
public class Ym7101Inst extends Instrument.BaseInstrument {

    private final NukedPsgProvider[] chips = {new NukedPsgProvider(), new NukedPsgProvider()};

    private final int[] mask = {0, 0};

    public Ym7101Inst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "YM7101";
    }

    @Override
    public String getShortName() {
        return "YM7101";
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
        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < chips.length;
        chips[chipId].writeData(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        assert chipId < chips.length;

        int[] buffer = new int[2];
        buffer[0] = 0;
        buffer[1] = 0;
        chips[chipId].endFrame(0);
        for (int i = 0; i < 1; i++) {
            outputs[0][i] = buffer[i * 2 + 0];
            outputs[1][i] = buffer[i * 2 + 1];
//logger.log(Level.TRACE, "[%8d] : [%8d] [%d]".formatted(outputs[0][i], outputs[1][i], i));
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId] = null;
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
