package mdsound.instrument;

import mdsound.Instrument;
import mdsound.mame.Fm2612.Ym2612;


public class MameYm2612Inst extends Instrument.BaseInstrument {

    public static final int MAX_CHIPS = 2;
    public static final int DefaultClockValue = 7670454;

    private final Ym2612[] chips = {new Ym2612(), new Ym2612()};

    private final int[] mask = {0, 0};

    public MameYm2612Inst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "YM2612mame";
    }

    @Override
    public String getShortName() {
        return "OPN2mame";
    }

    @Override
    public void reset(int chipId) {
        assert chipId < MAX_CHIPS;
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        chips[chipId].init(clock, samplingRate, null, null);
        chips[chipId].updateRequest = () -> chips[chipId].updateOne(new int[2][], 0);

        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < MAX_CHIPS;
        chips[chipId].write(0 + (port & 1) * 2, adr);
        chips[chipId].write(1 + (port & 1) * 2, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        assert chipId < MAX_CHIPS;

        chips[chipId].updateOne(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId] = null;
    }

    // TODO 2612
    @Override
    public synchronized void setMask(int chipId, int ch) {
        assert chipId < MAX_CHIPS;
        mask[chipId] |= 1 << ch;
        chips[chipId].setMuteMask(mask[chipId]);
    }

    // TODO 2612
    @Override
    public synchronized void resetMask(int chipId, int ch) {
        assert chipId < MAX_CHIPS;
        mask[chipId] &= ~(1 << ch);
        chips[chipId].setMuteMask(mask[chipId]);
    }
}
