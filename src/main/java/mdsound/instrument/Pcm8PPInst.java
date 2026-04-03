/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import mdsound.Instrument.BaseInstrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.Pcm8PP;


/**
 * PCM8++ PCM8 (Mercury Unit).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-02 nsano initial version <br>
 */
public class Pcm8PPInst extends BaseInstrument implements PcmEnabledInstrument {

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

    /**
     * @param option [0] <Integer> ?
     */
    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < chips.length;

        int sOption;
        if (option == null || option.length < 1) sOption = -1;
        else sOption = (int) option[0];

        chips[chipId].start(samplingRate, clock, sOption);

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
    }

    @Override
    public void setMask(int chipId, int ch) {
        assert chipId < chips.length;
        chips[chipId].setMute(ch, true);
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }

    @Override
    public void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
        chips[chipId].mountMemory(buf);
    }

    public void keyOn(int chipId, int c, int adrsPtr, int mode, int len) {
        chips[chipId].keyOn(c, adrsPtr, mode, len, 0);
    }

    public void keyOff(int chipId, int c) {
        chips[chipId].keyOff(c);
    }
}
