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
import vavi.sound.ymfm.Opn.Ym2203;
import vavi.sound.ymfm.YmFm.VgmChip;


/**
 * Ym2203 (OPN) YmFm version.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-01-18 nsano initial version <br>
 */
public class YmFmYm2203Inst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 3000000;

    private final VgmChip[] chips = new VgmChip[2];

    // TODO similar variables in VgmChip class, those can be eliminated?
    long output_pos;
    long output_step;

    public YmFmYm2203Inst() {
        // 0..Main 1..FM 2..SSG
        visVolume = new int[][][] {{{0, 0}, {0, 0}, {0, 0}}, {{0, 0}, {0, 0}, {0, 0}}};
    }

    @Override
    public String getName() {
        return "YM2203ymfm";
    }

    @Override
    public String getShortName() {
        return "OPN";
    }

    @Override
    public void reset(int chipId) {
        assert chipId < chips.length;
        chips[chipId].reset();

        output_pos = 0;
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId] = new VgmChip(clock, Ym2203.class);

        output_step = 0x1_0000_0000L / samplingRate;

        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < chips.length;
        chips[chipId].write(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        assert chipId < chips.length;

        int[] buffer = new int[2];
        buffer[0] = 0;
        buffer[1] = 0;
        chips[chipId].generate(output_pos, output_step, buffer);
        for (int i = 0; i < 1; i++) {
            outputs[0][i] = buffer[i * 2 + 0];
            outputs[1][i] = buffer[i * 2 + 1];
        }

        output_pos += output_step;

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
//        visVolume[chipId][1][0] = chips[chipId].visVolume[0];
//        visVolume[chipId][1][1] = chips[chipId].visVolume[1];
//        visVolume[chipId][2][0] = chips[chipId].psg.visVolume;
//        visVolume[chipId][2][1] = chips[chipId].psg.visVolume;
    }

    @Override
    public void stop(int chipId) {
        chips[chipId] = null;
    }

    @Override
    public void setMask(int chipId, int ch) {
        assert chipId < chips.length;
//        chips[chipId].setChannelMask(val);
    }

    @Override
    public void resetMask(int chipId, int ch) {
        assert chipId < chips.length;
//        chips[chipId].setChannelMask(val);
    }

    // ----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        // mul=0.5 SSG
        return new Tuple<>(0x100, 1d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Object... args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" -> {
                result.put("ym2203", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
                result.put("ym2203FM", getMonoVolume(visVolume[0][1][0], visVolume[0][1][1], visVolume[1][1][0], visVolume[1][1][1]));
                result.put("ym2203SSG", getMonoVolume(visVolume[0][2][0], visVolume[0][2][1], visVolume[1][2][0], visVolume[1][2][1]));
            }
        }
        return result;
    }
}
