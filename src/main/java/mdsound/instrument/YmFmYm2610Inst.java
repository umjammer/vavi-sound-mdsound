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
import mdsound.Instrument.AdpcmEnabledInstrument;
import vavi.sound.ymfm.Opn.Ym2610;
import vavi.sound.ymfm.YmFm.VgmChip;


/**
 * Ym2610 (OPNB) YmFm version.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-01-18 nsano initial version <br>
 */
public class YmFmYm2610Inst extends Instrument.BaseInstrument implements AdpcmEnabledInstrument {

    public static final int DefaultClockValue = 8000000;

    private final VgmChip[] chips = new VgmChip[2];

    // TODO similar variables in VgmChip class, those can be eliminated?
    long output_pos;
    long output_step;

    @Override
    public String getName() {
        return "YM2610ymfm";
    }

    @Override
    public String getShortName() {
        return "OPNB";
    }

    public YmFmYm2610Inst() {
        // 0..Main 1..FM 2..SSG 3..PCMa 4..PCMb
        visVolume = new int[][][] {
                {{0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}},
                {{0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}}
        };
    }

    @Override
    public void reset(int chipId) {
        assert chipId < chips.length;
        chips[chipId].reset();

        output_pos = 0;
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId] = new VgmChip(clock, Ym2610.class);

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
        chips[chipId].write(port * 0x100 + adr, data);
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
            //logger.log(Level.TRACE, "[%8d] : [%8d] [%d]\r".formatted(outputs[0][i], outputs[1][i],i));
        }

        output_pos += output_step;

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
//        visVolume[chipId][1][0] = chips[chipId].visVolume[0];
//        visVolume[chipId][1][1] = chips[chipId].visVolume[1];
//        visVolume[chipId][2][0] = chips[chipId].psg.visVolume;
//        visVolume[chipId][2][1] = chips[chipId].psg.visVolume;
//        visVolume[chipId][3][0] = chips[chipId].visRtmVolume[0];
//        visVolume[chipId][3][1] = chips[chipId].visRtmVolume[1];
//        visVolume[chipId][4][0] = chips[chipId].visAPCMVolume[0];
//        visVolume[chipId][4][1] = chips[chipId].visAPCMVolume[1];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId] = null;
    }

    @Override
    public void setMask(int chipId, int ch) {
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }

    @Override
    public void writeAdpcmA(int chipId, byte[] _adpcma) {
        assert chipId < chips.length;
//        chips[chipId].setAdpcmA(_adpcma, _adpcma_size);
    }

    @Override
    public void writeAdpcmB(int chipId, byte[] _adpcmb) {
        assert chipId < chips.length;
//        chips[chipId].setAdpcmB(_adpcmb, _adpcmb_size);
    }

    // ----

    // TODO automatic wired, use annotation?
    public void setFMVolume(int vol, double ignored) {
        if (chips[0] == null) return; // chips[0].setFMVolume(vol);
        if (chips[1] == null) return; // chips[1].setFMVolume(vol);
    }

    // TODO automatic wired, use annotation?
    public void setPSGVolume(int vol, double ignored) {
        if (chips[0] == null) return; // chips[0].setPSGVolume(vol);
        if (chips[1] == null) return; // chips[1].setPSGVolume(vol);
    }

    // TODO automatic wired, use annotation?
    public void setAdpcmAVolume(int vol, double ignored) {
        if (chips[0] == null) return; // chips[0].setAdpcmAVolume(vol);
        if (chips[1] == null) return; // chips[1].setAdpcmAVolume(vol);
    }

    // TODO automatic wired, use annotation?
    public void setAdpcmBVolume(int vol, double ignored) {
        if (chips[0] == null) return; // chips[0].setAdpcmBVolume(vol);
        if (chips[1] == null) return; // chips[1].setAdpcmBVolume(vol);
    }

    // ----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x80, 1d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        // TODO tag commonize
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" -> {
                result.put("ym2610", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
                result.put("ym2610FM", getMonoVolume(visVolume[0][1][0], visVolume[0][1][1], visVolume[1][1][0], visVolume[1][1][1]));
                result.put("ym2610SSG", getMonoVolume(visVolume[0][2][0], visVolume[0][2][1], visVolume[1][2][0], visVolume[1][2][1]));
                result.put("ym2610APCMA", getMonoVolume(visVolume[0][3][0], visVolume[0][3][1], visVolume[1][3][0], visVolume[1][3][1]));
                result.put("ym2610APCMB", getMonoVolume(visVolume[0][4][0], visVolume[0][4][1], visVolume[1][4][0], visVolume[1][4][1]));
            }
        }
        return result;
    }
}

