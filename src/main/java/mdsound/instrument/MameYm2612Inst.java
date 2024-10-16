package mdsound.instrument;

import mdsound.Instrument;
import mdsound.mame.Fm2612;


public class MameYm2612Inst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 2;
    private static final int DefaultFMClockValue = 7670454;
    public Fm2612[] chips = new Fm2612[MAX_CHIPS];
    private Fm2612.Ym2612[] ym2612 = new Fm2612.Ym2612[MAX_CHIPS];

    @Override
    public String getName() {
        return "YM2612mame";
    }

    @Override
    public String getShortName() {
        return "OPN2mame";
    }

    public MameYm2612Inst() {
        // 0..Main
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public void reset(int chipId) {
        if (chips[chipId] == null) return;

        chips[chipId].ym2612_reset_chip(ym2612[chipId]);
    }

    @Override
    public int start(int chipId, int clock) {
        chips[chipId] = new Fm2612();
        ym2612[chipId] = new Fm2612.Ym2612(DefaultFMClockValue, clock, null, null);

        return clock;
    }

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        chips[chipId] = new Fm2612();
        ym2612[chipId] = new Fm2612.Ym2612(clockValue, clock, null, null);
        ym2612[chipId].updateRequest = () -> ym2612[chipId].updateOne(new int[2][], 0);

        return clock;
    }

    @Override
    public void stop(int chipId) {
        chips[chipId] = null;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].ym2612_update_one(ym2612[chipId], outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        if (chips[chipId] == null) return 0;

        return chips[chipId].ym2612_write(chipId, ym2612[chipId], adr, data);
    }

    public void SetMute(int chipId, int mask) {
        if (chips[chipId] == null) return;
        chips[chipId].ym2612_set_mutemask(chipId, ym2612[chipId], mask);
    }
}
