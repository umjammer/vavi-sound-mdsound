/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import mdsound.Instrument.BaseInstrument;
import mdsound.chips.Gigatron;


/**
 * GigatronInst.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-02 nsano initial version <br>
 */
public class GigatronInst extends BaseInstrument {

    public Gigatron[] gig = {new Gigatron(), new Gigatron()};

    @Override
    public String getName() {
        return "Gigatron";
    }

    @Override
    public String getShortName() {
        return "Gigatron";
   }

    @Override
    public void reset(int chipId) {
        gig[chipId].Reset();
    }

    @Override
    public int start(int chipId, int sampleRate, int clock, Object... option) {
        gig[chipId].Start(sampleRate, clock);
        return sampleRate;
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
        gig[chipId].Update(outputs, samples);
    }

    @Override
    public void stop(int chipId) {
        gig[chipId].Stop();
    }

    @Override
    public void setMask(int chipId, int ch) {
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }
}
