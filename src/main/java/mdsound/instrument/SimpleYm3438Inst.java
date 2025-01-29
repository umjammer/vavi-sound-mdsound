/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import mdsound.Instrument;
import mdsound.chips.Ym3438Const;
import uk.co.omgdrv.simplevgm.fm.nukeykt.Ym3438Provider;


/**
 * Ym3438 (OPN2 (cmos)) NukeYKT (simplevgm) version.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-01-24 nsano initial version <br>
 */
public class SimpleYm3438Inst extends Instrument.BaseInstrument {

    private final Ym3438Provider[] chips = {new Ym3438Provider(), new Ym3438Provider()};

    private final int[] mask = {0, 0};

    public SimpleYm3438Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "YM3438simple";
    }

    @Override
    public String getShortName() {
        return "OPN2cmos";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId].reset();
        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized int write(int chipId, int port, int adr, int data) {
        assert chipId < chips.length;
        chips[chipId].writePort(0 + (port & 1) * 2, adr);
        chips[chipId].writePort(1 + (port & 1) * 2, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        int[] buffer = new int[4]; // 4 is needed for update() internal
        for (int i = 0; i < samples; i++) {
            chips[chipId].update(buffer, 0, 1);
            outputs[0][i] = buffer[0];
            outputs[1][i] = buffer[1];
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].reset();
    }

    // TODO 2612
    @Override
    public synchronized void setMask(int chipId, int ch) {
        mask[chipId] |= 1 << ch;
        int mask = this.mask[chipId];
        if ((mask & 0b0010_0000) == 0) mask &= 0b1011_1111;
        else mask |= 0b0100_0000;
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }
}

