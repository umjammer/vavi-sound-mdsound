/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import mdsound.Instrument.BaseInstrument;
import mdsound.chips.Zsg2;


/**
 * Zsg2Inst.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-02 nsano initial version <br>
 */
public class Zsg2Inst extends BaseInstrument {

    private final Zsg2[] chips = {new Zsg2(), new Zsg2()};

    @Override
    public String getName() {
        return "ZSG2";
    }

    @Override
    public String getShortName() {
        return "ZSG2";
    }


    @Override
    public void reset(int chipId) {
        chips[chipId].device_reset();
    }

    @Override
    public int start(int chipId, int clock, int ClockValue, Object... option) {
        //TBD
        // Clock processing not implemented
        //sampleRate[chipId] = clock / 768;
        //baseClock[chipId] = ClockValue;
        chips[chipId].start();

        return clock;
    }

    @Override
    public int read(int chipId, int adr) {
        return 0;
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write(adr, data, 0xffff);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        //TBD
        //  更新タイミングの計算
        //  ZSG2は出力が4chである

        chips[chipId].update(outputs, samples);
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
