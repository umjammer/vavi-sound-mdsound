package mdsound.instrument;

import mdsound.Instrument;
import mdsound.chips.Ym3438Const;
import mdsound.chips.Ym3438;


// nuked YM3438 OPN2 (cmos) (YM2612 variant)
public class Ym3438Inst extends Instrument.BaseInstrument {

    private final Ym3438[] chips = {new Ym3438(), new Ym3438()};

    private final int[] mask = {0, 0};

    private Ym3438Const.Type type;

    public void setChipType(Ym3438Const.Type type) {
        this.type = type;
    }

    public Ym3438Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "YM3438" + Ym3438Const.chip_type;
    }

    @Override
    public String getShortName() {
        return "OPN2cmos";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset(0, 0);
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
//        chips[chipId].setChipType(type);
        chips[chipId].reset(samplingRate, clock);
        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized int write(int chipId, int port, int adr, int data) {
        assert chipId < chips.length;
        chips[chipId].writeBuffered(0 + (port & 1) * 2, adr);
        chips[chipId].writeBuffered(1 + (port & 1) * 2, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        int[] buffer = new int[2];
        for (int i = 0; i < samples; i++) {
            chips[chipId].update(buffer);
            outputs[0][i] = buffer[0];
            outputs[1][i] = buffer[1];
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].reset(0, 0);
    }

    // TODO 2612
    @Override
    public synchronized void setMask(int chipId, int ch) {
//        mask[chipId] |= 1 << ch;
//        int mask = this.mask[chipId];
//        if ((mask & 0b0010_0000) == 0) mask &= 0b1011_1111;
//        else mask |= 0b0100_0000;
//        chips[chipId].setMuteMask(mask);
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }
}

