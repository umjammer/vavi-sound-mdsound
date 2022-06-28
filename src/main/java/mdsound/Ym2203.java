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
    public void reset(byte chipId) {
        if (chip[chipId] == null) return;
        chip[chipId].reset();
    }

    @Override
    public int start(byte chipId, int clock) {
        chip[chipId] = new Opna.OPN();
        chip[chipId].init(DefaultYM2203ClockValue, clock);

        return clock;
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        chip[chipId] = new Opna.OPN();
        chip[chipId].init(clockValue, clock);

        return clock;
    }

    @Override
    public void stop(byte chipId) {
        chip[chipId] = null;
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        if (chip[chipId] == null) return;
        int[] buffer = new int[2];
        buffer[0] = 0;
        buffer[1] = 0;
        chip[chipId].mix(buffer, 1);
        for (int i = 0; i < 1; i++) {
            outputs[0][i] = buffer[i * 2 + 0];
            outputs[1][i] = buffer[i * 2 + 1];
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
        visVolume[chipId][1][0] = chip[chipId].visVolume[0];
        visVolume[chipId][1][1] = chip[chipId].visVolume[1];
        visVolume[chipId][2][0] = chip[chipId].psg.visVolume;
        visVolume[chipId][2][1] = chip[chipId].psg.visVolume;
    }

    private int YM2203_Write(byte chipId, byte adr, byte data) {
        if (chip[chipId] == null) return 0;
        chip[chipId].setReg(adr, data);
        return 0;
    }

    public void YM2203_SetMute(byte chipId, int val) {
        Opna.OPN YM2203 = chip[chipId];
        if (YM2203 == null) return;


        YM2203.setChannelMask(val);

    }

    public void SetFMVolume(byte chipId, int db) {
        if (chip[chipId] == null) return;

        chip[chipId].setVolumeFM(db);
    }

    public void SetPSGVolume(byte chipId, int db) {
        if (chip[chipId] == null) return;

        chip[chipId].setVolumePSG(db);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        return YM2203_Write(chipId, (byte) adr, (byte) data);
    }
}
