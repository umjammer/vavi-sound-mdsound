package mdsound.instrument;

import mdsound.Instrument;
import mdsound.chips.Ym3438Const;
import mdsound.chips.Ym3438;


public class Ym3438Inst extends Instrument.BaseInstrument {

    private final Ym3438[] chips = {new Ym3438(), new Ym3438()};

    private final int[] mask = {0, 0};

    private Ym3438Const.Type type;

    public void setChipType(Ym3438Const.Type type) {
        this.type = type;
    }

    public Ym3438Inst() {
        visVolume = new int[][][] {
                {{0, 0}},
                {{0, 0}}
        };
    }

    @Override
    public String getName() {
        return "YM3438";
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
        chips[chipId].setChipType(type);
        chips[chipId].reset(samplingRate, clock);
        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized int write(int chipId, int port, int adr, int data) {
        writeInternal(chipId, 0 + (port & 1) * 2, adr);
        writeInternal(chipId, 1 + (port & 1) * 2, data);
        return 0;
    }

    private final int[] gsBuffer = new int[2];

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        for (int i = 0; i < samples; i++) {
            Ym3438 chip = chips[chipId];
            chip.update(gsBuffer);
            //smpl[i] = gsBuffer[0];
            //smpr[i] = gsBuffer[1];
            outputs[0][i] = gsBuffer[0];
            outputs[1][i] = gsBuffer[1];
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].reset(0, 0);
    }

    private void writeInternal(int chipId, int adr, int data) {
        assert chipId < chips.length;

        Ym3438 chip = chips[chipId];
        chip.write(adr, data);
    }

    public void setMute(int chipId, int mute) {
        chips[chipId].setMuteMask(mute);
    }

    public void setMuteCh(int chipId, int ch, boolean mute) {
        chips[chipId].setMute(ch, mute);
    }

    // ----

    // TODO 2612
    public synchronized void setYm2612Mask(int chipId, int ch) {
        mask[chipId] |= 1 << ch;
        int mask = this.mask[chipId];
        if ((mask & 0b0010_0000) == 0) mask &= 0b1011_1111;
        else mask |= 0b0100_0000;
        setMute(chipId, mask);
    }
}

