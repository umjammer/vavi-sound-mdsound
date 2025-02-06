/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import mdsound.Instrument.BaseInstrument;
import mdsound.chips.MPcmPP;


/**
 * MPcmPPInst.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-02 nsano initial version <br>
 */
public class MPcmPPInst extends BaseInstrument {

    private final int MAX_CHIPS = 0x02;

    private final MPcmPP[] chips = {new MPcmPP(), new MPcmPP()};

    public MPcmPPInst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "mpcmpp";
    }

    @Override
    public String getShortName() {
        return "mppp";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId].mountMPcmX68K();
        chips[chipId].init(clock, samplingRate);
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
        chips[chipId].update(outputs, samples);
        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].unmountMPcmX68K();
    }

    @Override
    public void setMask(int chipId, int ch) {
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }
}
