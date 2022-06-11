package mdsound;

import mdsound.fmgen.PSG;


public class Ay8910 extends Instrument.BaseInstrument {
    private PSG[] chip = new PSG[2];
    private static final int DefaultClockValue = 1789750;

    @Override
    public String getName() {
        return "AY8910";
    }

    @Override
    public String getShortName() {
        return "AY10";
    }

    public Ay8910() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
        //0..Main
    }

    @Override
    public void reset(byte chipID) {
        if (chip[chipID] == null) return;
        chip[chipID].reset();
    }

    @Override
    public int start(byte chipID, int clock) {
        chip[chipID] = new PSG();
        chip[chipID].setClock(DefaultClockValue, clock);

        return clock;
    }

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        chip[chipID] = new PSG();
        chip[chipID].setClock(clockValue, clock);

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
            //System.err.printf("[%8d] : [%8d] [%d]\n", outputs[0][i], outputs[1][i], i);
        }

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    private int write(byte chipID, byte adr, byte data) {
        if (chip[chipID] == null) return 0;
        chip[chipID].setReg(adr, data);
        return 0;
    }

    public void setMute(byte chipID, int val) {
        PSG psg = chip[chipID];
        if (psg == null) return;


        psg.setChannelMask(val);
    }

    public void setVolume(byte chipID, int db) {
        if (chip[chipID] == null) return;

        chip[chipID].setVolume(db);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        if (chip[chipID] == null) return 0;
        chip[chipID].setReg(adr, (byte) data);
        return 0;
    }
}

