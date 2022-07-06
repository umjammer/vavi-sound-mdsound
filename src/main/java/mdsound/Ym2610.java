package mdsound;

import mdsound.fmgen.Opna.OPNB;


public class Ym2610 extends Instrument.BaseInstrument {
    private OPNB[] chip = new OPNB[2];
    private static final int DefaultYM2610ClockValue = 8000000;

    @Override
    public String getName() {
        return "YM2610";
    }

    @Override
    public String getShortName() {
        return "OPNB";
    }

    public Ym2610() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}}
                , new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}}
        };
        //0..Main 1..FM 2..SSG 3..PCMa 4..PCMb
    }

    @Override
    public void reset(byte chipId) {
        if (chip[chipId] == null) return;
        chip[chipId].reset();
    }

    @Override
    public int start(byte chipId, int clock) {
        chip[chipId] = new OPNB();
        chip[chipId].init(DefaultYM2610ClockValue, clock);

        return clock;
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        chip[chipId] = new OPNB();
        chip[chipId].init(clockValue, clock, false, new byte[0x20ffff], 0x20ffff, new byte[0x20ffff], 0x20ffff);

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
            //Debug.printf("[%8d] : [%8d] [%d]\r\n", outputs[0][i], outputs[1][i],i);
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
        visVolume[chipId][1][0] = chip[chipId].visVolume[0];
        visVolume[chipId][1][1] = chip[chipId].visVolume[1];
        visVolume[chipId][2][0] = chip[chipId].psg.visVolume;
        visVolume[chipId][2][1] = chip[chipId].psg.visVolume;
        visVolume[chipId][3][0] = chip[chipId].visRtmVolume[0];
        visVolume[chipId][3][1] = chip[chipId].visRtmVolume[1];
        visVolume[chipId][4][0] = chip[chipId].visAPCMVolume[0];
        visVolume[chipId][4][1] = chip[chipId].visAPCMVolume[1];
    }

    private int YM2610_Write(byte chipId, int adr, byte data) {
        if (chip[chipId] == null) return 0;
        chip[chipId].setReg(adr, data);
        return 0;
    }

    public void YM2610_setAdpcmA(byte chipId, byte[] _adpcma, int _adpcma_size) {
        if (chip[chipId] == null) return;
        chip[chipId].setAdpcmA(_adpcma, _adpcma_size);
    }

    public void YM2610_setAdpcmB(byte chipId, byte[] _adpcmb, int _adpcmb_size) {
        if (chip[chipId] == null) return;
        chip[chipId].setAdpcmB(_adpcmb, _adpcmb_size);
    }

    public void SetFMVolume(byte chipId, int db) {
        if (chip[chipId] == null) return;

        chip[chipId].setVolumeFM(db);
    }

    public void SetPSGVolume(byte chipId, int db) {
        if (chip[chipId] == null) return;

        chip[chipId].setVolumePSG(db);
    }

    public void SetAdpcmAVolume(byte chipId, int db) {
        if (chip[chipId] == null) return;

        chip[chipId].setVolumeADPCMATotal(db);
    }

    public void SetAdpcmBVolume(byte chipId, int db) {
        if (chip[chipId] == null) return;

        chip[chipId].setVolumeADPCMB(db);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        return YM2610_Write(chipId, adr, (byte) data);
    }
}

