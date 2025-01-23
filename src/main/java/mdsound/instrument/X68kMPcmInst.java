package mdsound.instrument;

import java.nio.ByteBuffer;

import mdsound.Instrument;
import mdsound.chips.MPcm;


public class X68kMPcmInst extends Instrument.BaseInstrument {

    public static final int MAX_CHIPS = 0x02;

    private final MPcm[] chips = {new MPcm(), new MPcm()};

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
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].update(outputs, samples);
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].unmount();
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

    public void setVolTable(int chipId, int sel, ByteBuffer tbl) {
        chips[chipId].setVolTable(sel, tbl);
    }

    private int decode(int chipId, int ch, byte[] buffer, int bufferP, int pos) {
        return chips[chipId].decode(ch, buffer, bufferP, pos);
    }
}
