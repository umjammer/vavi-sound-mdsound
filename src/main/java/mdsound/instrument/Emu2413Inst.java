/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument.BaseInstrument;
import mdsound.chips.Emu2413;


/**
 * Emu2413Inst.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-02 nsano initial version <br>
 */
public class Emu2413Inst extends BaseInstrument {

    public static final int DefaultClockValue = 3579545;

    private final Emu2413[] chips = {new Emu2413(), new Emu2413()};

    private final int[][] buffers = new int[2][2];

    public Emu2413Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "YM2413emu";
    }

    @Override
    public String getShortName() {
        return "OPLLe";
    }

    @Override
    public int start(int chipId, int SamplingRate, int FMClockValue, Object... option) {
        chips[chipId].init(FMClockValue, SamplingRate);
        return SamplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        return 0;
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < chips.length;
        chips[chipId].writeReg(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        for (int i = 0; i < samples; i++) {
            chips[chipId].calcStereo(buffers[chipId]);
            outputs[0][i] = buffers[chipId][0] << 1;
            outputs[1][i] = buffers[chipId][1] << 1;
        }

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

    @Override
    public void reset(int chipId) {
    }

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x200 /* 0x155 */, 0.5);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Object... args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "register" -> result.put("register", chips[chipId].getRegisters());
        }
        return result;
    }
}
