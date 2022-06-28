package mdsound;

import mdsound.mame.Fm;
import mdsound.mame.Fm2612;


public class Ym2612Mame extends Instrument.BaseInstrument {
    private static final int MAX_CHIPS = 2;
    private static final int DefaultFMClockValue = 7670454;
    public Fm2612[] chips = new Fm2612[] {null, null};
    private Fm2612.Ym2612[] ym2612 = new Fm2612.Ym2612[MAX_CHIPS];

    @Override
    public String getName() {
        return "YM2612mame";
    }

    @Override
    public String getShortName() {
        return "OPN2mame";
    }

    public Ym2612Mame() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
        //0..Main
    }

    @Override
    public void reset(byte chipId) {
        if (chips[chipId] == null) return;

        chips[chipId].ym2612_reset_chip(ym2612[chipId]);
    }

    @Override
    public int start(byte chipId, int clock) {
        chips[chipId] = new Fm2612();
        ym2612[chipId] = new Fm2612.Ym2612(DefaultFMClockValue, clock, null, null);

        return clock;
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        chips[chipId] = new Fm2612();
        ym2612[chipId] = new Fm2612.Ym2612(clockValue, clock, null, null);
        ym2612[chipId].updateRequest = () -> ym2612[chipId].updateOne(new int[2][], 0);

        return clock;
    }

    @Override
    public void stop(byte chipId) {
        chips[chipId] = null;
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        chips[chipId].ym2612_update_one(ym2612[chipId], outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        if (chips[chipId] == null) return 0;

        return chips[chipId].ym2612_write(chipId, ym2612[chipId], (byte) adr, (byte) data);
    }

    public void SetMute(byte chipId, int mask) {
        if (chips[chipId] == null) return;
        chips[chipId].ym2612_set_mutemask(chipId, ym2612[chipId], mask);
    }
}
