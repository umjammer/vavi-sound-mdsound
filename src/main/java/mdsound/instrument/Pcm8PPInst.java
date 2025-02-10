/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import mdsound.Instrument.BaseInstrument;
import mdsound.chips.Pcm8PP;


/**
 * Pcm8PPInst.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-02 nsano initial version <br>
 */
public class Pcm8PPInst extends BaseInstrument {

    private final Pcm8PP[] chips = {new Pcm8PP(), new Pcm8PP()};

    public Pcm8PPInst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "PCM8PP";
    }

    @Override
    public String getShortName() {
        return "PCM8PP";
    }

    @Override
    public void reset(int chipId) {
        assert chipId < chips.length;
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplerate, int clock, Object... option) {
        assert chipId < chips.length;
        chips[chipId].start(samplerate, clock);

        return samplerate;
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
    }

    @Override
    public void setMask(int chipId, int ch) {
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }

    public void keyOn(int chipId, int c, int adrsPtr, int mode, int len, int d3Freq) {
        chips[chipId].keyOn(c, adrsPtr, mode, len, d3Freq);
    }

    public void keyOff(int chipId, int c) {
        chips[chipId].keyOff(c);
    }

    public void mountMemory(int chipId, byte[] mem) {
        chips[chipId].mountMemory(mem);
    }
}
