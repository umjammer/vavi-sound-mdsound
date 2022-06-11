package mdsound;

import mdsound.mame.Fm;
import mdsound.mame.Fm2612;


public class Ym2612Mame extends Instrument.BaseInstrument {
    private static final int MAX_CHIPS = 2;
    private static final int DefaultFMClockValue = 7670454;
    public Fm2612[] YM2612_Chip = new Fm2612[] {null, null};
    private Fm2612.YM2612[] ym2612 = new Fm2612.YM2612[MAX_CHIPS];

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
    public void reset(byte chipID) {
        if (YM2612_Chip[chipID] == null) return;

        YM2612_Chip[chipID].ym2612_reset_chip(ym2612[chipID]);
    }

    @Override
    public int start(byte chipID, int clock) {
        YM2612_Chip[chipID] = new Fm2612();
        ym2612[chipID] = new Fm2612.YM2612(DefaultFMClockValue, clock, null, null);

        return clock;
    }

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        YM2612_Chip[chipID] = new Fm2612();
        ym2612[chipID] = new Fm2612.YM2612(clockValue, clock, null, null);
        YM2612_Chip[chipID].updateRequest = this::ym2612_update_request;

        return clock;
    }

    void ym2612_update_request(byte chipID, Fm.BaseChip param) {
        YM2612_Chip[chipID].ym2612_update_one(ym2612[chipID], new int[2][], 0);
    }

    @Override
    public void stop(byte chipID) {
        YM2612_Chip[chipID] = null;
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        YM2612_Chip[chipID].ym2612_update_one(ym2612[chipID], outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        if (YM2612_Chip[chipID] == null) return 0;

        return YM2612_Chip[chipID].ym2612_write(chipID, ym2612[chipID], (byte) adr, (byte) data);
    }

    public void SetMute(byte chipID, int mask) {
        if (YM2612_Chip[chipID] == null) return;
        YM2612_Chip[chipID].ym2612_set_mutemask(chipID, ym2612[chipID], mask);
    }
}
