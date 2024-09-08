package mdsound.instrument;

import java.nio.ByteBuffer;

import mdsound.Instrument;
import mdsound.chips.MPcm;


public class X68kMPcmInst extends Instrument.BaseInstrument {

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
        reset_(chipId);
    }

    @Override
    public int start(int chipId, int clock) {
        return start(chipId, 44100, clock);
    }

    @Override
    public int start(int chipId, int samplingRate, int clockValue, Object... option) {
        mountMpcmX68K(chipId);
        initialize(chipId, clockValue, samplingRate);
        return samplingRate;
    }

    @Override
    public void stop(int chipId) {
        unmountMpcmX68K(chipId);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        update_(chipId, outputs, samples);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        return 0;
    }

    private static final int MAX_CHIPS = 0x02;
    public MPcm[] chips = new MPcm[] {new MPcm(), new MPcm()};

    public void mountMpcmX68K(int chipId) {
        MPcm chip = chips[chipId];
        chip.mount();
    }

    public void unmountMpcmX68K(int chipId) {
        MPcm chip = chips[chipId];
        chip.unmount();
    }

    public boolean initialize(int chipId, int base, float samplingRate) {
        MPcm chip = chips[chipId];
        return chip.init(base, samplingRate);
    }

    public void reset_(int chipId) {
        MPcm chip = chips[chipId];
        chip.reset();
    }

    public void keyOn(int chipId, int ch) {
        MPcm chip = chips[chipId];
        chip.keyOn(ch);
    }

    public void keyOff(int chipId, int ch) {
        MPcm chip = chips[chipId];
        chip.keyOff(ch);
    }

    public boolean setPcm(int chipId, int ch, MPcm.PCM ptr) {
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

    private int decode(int chipId, int ch, byte[] buffer, int bufferP, long pos) {
        MPcm chip = chips[chipId];
        return chip.decode(ch, buffer, bufferP, pos);
    }

    public void update_(int chipId, int[][] buffer, int count) {
        MPcm chip = chips[chipId];
        chip.update(buffer, count);
    }
}
