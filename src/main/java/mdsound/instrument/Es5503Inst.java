/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import java.util.function.Consumer;

import mdsound.Instrument.BaseInstrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.Es5503;


/**
 * Es5503Inst.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-02 nsano initial version <br>
 */
public class Es5503Inst extends BaseInstrument implements PcmEnabledInstrument {

    private final Es5503[] chips = {new Es5503(), new Es5503()};

    @Override
    public String getName() {
        return "ES5503";
    }

    @Override
    public String getShortName() {
        return "ES5503";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int sampleRate, int clock, Object... option) {
        int ret = chips[chipId].start(clock, (int) option[0]);
        chips[chipId].setCallback((Consumer<Integer>) option[1]);
        if (ret == 0) return clock;
        return 0;
    }

    @Override
    public int read(int chipId, int adr) {
        return 0;
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].update(samples, outputs);
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].stop();
    }

    @Override
    public void setMask(int chipId, int ch) {
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }

    public void setMute(int chipId, int v) {
        chips[chipId].setMuteMask(v);
    }

    @Override
    public void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
        chips[chipId].writeRam(buf, offset, length);
    }
}
