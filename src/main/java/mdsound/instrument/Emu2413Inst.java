/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import dotnet4j.util.compat.Tuple;
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

    private final Emu2413[] opll_ = {new Emu2413(), new Emu2413()};

    private final int[] buffers = new int[2];

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
        opll_[chipId].OPLL_init(FMClockValue, SamplingRate);
        return SamplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        return 0;
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < opll_.length;
        opll_[chipId].OPLL_writeReg(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        for (int i = 0; i < samples; i++) {
            opll_[chipId].OPLL_calcStereo(buffers);
            outputs[0][i] = buffers[0] << 1;
            outputs[1][i] = buffers[1] << 1;
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        opll_[chipId] = null;
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
}
