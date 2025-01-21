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
        MPcm chip = chips[chipId];
        chip.reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        MPcm chip = chips[chipId];
        chip.mount();
        MPcm chip1 = chips[chipId];
        chip1.init(clock, (float) samplingRate);
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
        MPcm chip = chips[chipId];
        chip.update(outputs, samples);
    }

    @Override
    public void stop(int chipId) {
        MPcm chip = chips[chipId];
        chip.unmount();
    }

    public void keyOn(int chipId, int ch) {
        MPcm chip = chips[chipId];
        chip.keyOn(ch);
    }

    public void keyOff(int chipId, int ch) {
        MPcm chip = chips[chipId];
        chip.keyOff(ch);
    }

    public boolean writePcm(int chipId, int ch, MPcm.PCM ptr) {
        MPcm chip = chips[chipId];
        return chip.setPcm(ch, ptr);
    }

    public void setPitch(int chipId, int ch, int note) {
        MPcm chip = chips[chipId];
        chip.setPitch(ch, note);
    }

    public void setVol(int chipId, int ch, int vol) {
        MPcm chip = chips[chipId];
        chip.setVol(ch, vol);
    }

    public void setPan(int chipId, int ch, int pan) {
        MPcm chip = chips[chipId];
        chip.setPan(ch, pan);
    }

    public void setVolTable(int chipId, int sel, ByteBuffer tbl) {
        MPcm chip = chips[chipId];
        chip.setVolTable(sel, tbl);
    }

    private int decode(int chipId, int ch, byte[] buffer, int bufferP, int pos) {
        MPcm chip = chips[chipId];
        return chip.decode(ch, buffer, bufferP, pos);
    }
}
