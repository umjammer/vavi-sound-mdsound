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
import vavi.sound.ymfm.Opl.Ymf262;
import vavi.sound.ymfm.YmFm.VgmChip;


/**
 * YmFmYmF262Inst.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-06 nsano initial version <br>
 */
public class YmFmYmF262Inst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 14318180;
    public static final int MAX_CHIPS = 0x02;

    private final VgmChip[] chips = new VgmChip[2];

    // TODO similar variables in VgmChip class, those can be eliminated?
    long output_pos;
    long output_step;

    public YmFmYmF262Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "YMF262ymfm";
    }

    @Override
    public String getShortName() {
        return "Opl3";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();

        output_pos = 0;
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        chips[chipId] = new VgmChip(clock, Ymf262.class);

        output_step = 0x1_0000_0000L / samplingRate;

        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write(adr | (port * 0x100), data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
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

//logger.log(Level.TRACE, "output %d %d".formatted(outputs[0][0], outputs[1][0]));
        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
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

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 2d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Object... args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        }
        return result;
    }
}
