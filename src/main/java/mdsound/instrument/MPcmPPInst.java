/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import mdsound.Instrument.BaseInstrument;
import mdsound.chips.MPcmPP;
import mdsound.chips.MPcmPP.SETPCM;


/**
 * MPcmPPInst.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-02 nsano initial version <br>
 */
public class MPcmPPInst extends BaseInstrument {

    private final int MAX_CHIPS = 0x02;

    public final MPcmPP[] chips = {new MPcmPP(), new MPcmPP()};

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
    } // TODO

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

    public void keyOn(int chipId, int ch) {
        chips[chipId].keyOn(ch);
    }

    public void keyOff(int chipId, int ch) {
        chips[chipId].keyOff(ch);
    }

    public void setPcm(int chipId, int ch, SETPCM ptr) {
        chips[chipId].setPcm(ch, ptr);
    }

    public void setFreq(int chipId, int ch, int freq) {
        chips[chipId].setFreq(ch, freq, 0);
    }

    public void setPitch(int chipId, int ch, int pitch) {
        chips[chipId].setPitch(ch, pitch);
    }

    public void setVol(int chipId, int ch, int volume) {
        chips[chipId].setVol(ch, volume);
    }

    public void setPan(int chipId, int ch, int pan) {
        chips[chipId].setPan(ch, pan);
    }

    public void setVolTableZms(int chipId, int sel, int[] vtbl) {
        chips[chipId].setVolTableZms(sel, vtbl);
    }
}
