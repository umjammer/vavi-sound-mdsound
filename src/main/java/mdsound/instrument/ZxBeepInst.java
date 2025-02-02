package mdsound.instrument;

import mdsound.Instrument.BaseInstrument;


public class ZxBeepInst extends BaseInstrument {

    private static class Zx {
        short val = 0;
        short vol = 0xfff;
    }

    private final Zx[] chips = {new Zx(), new Zx()};

    @Override
    public String getName() {
        return "ZXBeep";
    }

    @Override
    public String getShortName() {
        return "Beeper";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].val = 0;
        chips[chipId].vol = 0xfff;
    }

    @Override
    public int start(int chipId, int clock, int ClockValue, Object... option) {
        reset(chipId);
        return clock;
    }

    @Override
    public int read(int chipId, int adr) {
        return 0;
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].val = (short) (chips[chipId].val != 0 ? 0 : chips[chipId].vol);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        for (int i = 0; i < 1; i++) {
            outputs[0][i] = chips[chipId].val;
            outputs[1][i] = chips[chipId].val;
        }
    }

    @Override
    public void stop(int chipId) {
        reset(chipId);
    }

    @Override
    public void setMask(int chipId, int ch) {
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }
}
