package mdsound;

import mdsound.fmgen.Opna;


public class Ym2203 extends Instrument.BaseInstrument {
    private Opna.OPN[] chip = new Opna.OPN[2];
    private static final int DefaultYM2203ClockValue = 3000000;

    @Override
    public String getName() {
        return "YM2203";
    }

    @Override
    public String getShortName() {
        return "OPN";
    }

    public Ym2203() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}},
                new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}}
        };
        //0..Main 1..FM 2..SSG
    }

    @Override
    public void reset(byte chipID) {
        if (chip[chipID] == null) return;
        chip[chipID].reset();
    }

    @Override
    public int start(byte chipID, int clock) {
        chip[chipID] = new Opna.OPN();
        chip[chipID].init(DefaultYM2203ClockValue, clock);

        return clock;
    }

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        chip[chipID] = new Opna.OPN();
        chip[chipID].init(clockValue, clock);

        return clock;
    }

    @Override
    public void stop(byte chipID) {
        chip[chipID] = null;
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        if (chip[chipID] == null) return;
        int[] buffer = new int[2];
        buffer[0] = 0;
        buffer[1] = 0;
        chip[chipID].mix(buffer, 1);
        for (int i = 0; i < 1; i++) {
            outputs[0][i] = buffer[i * 2 + 0];
            outputs[1][i] = buffer[i * 2 + 1];
        }

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
        visVolume[chipID][1][0] = chip[chipID].visVolume[0];
        visVolume[chipID][1][1] = chip[chipID].visVolume[1];
        visVolume[chipID][2][0] = chip[chipID].psg.visVolume;
        visVolume[chipID][2][1] = chip[chipID].psg.visVolume;
    }

    private int YM2203_Write(byte chipID, byte adr, byte data) {
        if (chip[chipID] == null) return 0;
        chip[chipID].setReg(adr, data);
        return 0;
    }

    public void YM2203_SetMute(byte chipID, int val) {
        Opna.OPN YM2203 = chip[chipID];
        if (YM2203 == null) return;


        YM2203.setChannelMask((int) val);

    }

    public void SetFMVolume(byte chipID, int db) {
        if (chip[chipID] == null) return;

        chip[chipID].setVolumeFM(db);
    }

    public void SetPSGVolume(byte chipID, int db) {
        if (chip[chipID] == null) return;

        chip[chipID].setVolumePSG(db);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        return YM2203_Write(chipID, (byte) adr, (byte) data);
    }
}
