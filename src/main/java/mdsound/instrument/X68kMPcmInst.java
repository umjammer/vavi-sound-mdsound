package mdsound.instrument;

import mdsound.Instrument;
import mdsound.chips.MPcm;


/**
 * X68kMPcm MPCM
 * <p>
 * original mpcmX68k.cs
 */
public class X68kMPcmInst extends Instrument.BaseInstrument {

    public static final int MAX_CHIPS = 0x02;

    public final MPcm[] chips = {new MPcm(), new MPcm()};

    @Override
    public String getName() {
        return "X68kMPcm";
    }

    @Override
    public String getShortName() {
        return "mpx";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId].mount();
        chips[chipId].init(clock, (float) samplingRate);
        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        return 0;
    } // TODO

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].update(outputs, samples);
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].unmount();
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

    public boolean writePcm(int chipId, int ch, MPcm.PCM ptr) {
        return chips[chipId].setPcm(ch, ptr);
    }

    public void setPitch(int chipId, int ch, int note) {
        chips[chipId].setPitch(ch, note);
    }

    public void setVol(int chipId, int ch, int vol) {
        chips[chipId].setVol(ch, vol);
    }

    public void setPan(int chipId, int ch, int pan) {
        chips[chipId].setPan(ch, pan);
    }

    public void setVolTable(int chipId, int sel) {
        chips[chipId].setVolTable(sel);
    }

    private int decode(int chipId, int ch, byte[] buffer, int bufferP, int pos) {
        return chips[chipId].decode(ch, buffer, bufferP, pos);
    }

    public void setFreq(int chipId, int ch, int num) {
        if (ch == 0xff) {
            for (int i = 0; i < MPcm.VOICE_MAX; i++) setFreq(chipId, i, num);
        } else {
            if (num < 0 || num > 6) return;
            chips[chipId].channels[ch].base = (float) MPcm.baseClockTbl[num] / chips[chipId].rate;
            chips[chipId].setPitch(ch, chips[chipId].channels[ch].lastNote);
        }
    }

    public void setVolTable(int chipId, int sel, int[] vtbl) {
        if (sel == 1) {
            // 16
            chips[chipId].setVolTable(sel, vtbl);
        } else {
            // 128
            chips[chipId].setVolTable(sel, vtbl);
        }
    }
}
