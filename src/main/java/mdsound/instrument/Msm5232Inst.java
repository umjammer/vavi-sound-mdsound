/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import mdsound.Instrument.BaseInstrument;
import mdsound.chips.MPcmPP;
import mdsound.chips.Msm5232;


/**
 * Msm5232Inst.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-02 nsano initial version <br>
 */
public class Msm5232Inst extends BaseInstrument {

    private final Msm5232[] chips = {new Msm5232(), new Msm5232()};

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
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId].init(clock, samplingRate);
        chips[chipId].device_start();
        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        return 0;
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        //TBD
    }

    @Override
    public void stop(int chipId) {
    }

    @Override
    public void setMask(int chipId, int ch) {
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }
}
