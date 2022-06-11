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
    public void reset(byte chipID) {
        if (chip[chipID] == null) return;
        chip[chipID].reset();
    }

    @Override
    public int start(byte chipID, int clock) {
        chip[chipID] = new OPNB();
        chip[chipID].init(DefaultYM2610ClockValue, clock);

        return clock;
    }

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        chip[chipID] = new OPNB();
        chip[chipID].init(clockValue, clock, false, new byte[0x20ffff], 0x20ffff, new byte[0x20ffff], 0x20ffff);

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
            //System.err.printf("[{0:d8}] : [{1:d8}] [{2}]\r\n", outputs[0][i], outputs[1][i],i);
        }

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
        visVolume[chipID][1][0] = chip[chipID].visVolume[0];
        visVolume[chipID][1][1] = chip[chipID].visVolume[1];
        visVolume[chipID][2][0] = chip[chipID].psg.visVolume;
        visVolume[chipID][2][1] = chip[chipID].psg.visVolume;
        visVolume[chipID][3][0] = chip[chipID].visRtmVolume[0];
        visVolume[chipID][3][1] = chip[chipID].visRtmVolume[1];
        visVolume[chipID][4][0] = chip[chipID].visAPCMVolume[0];
        visVolume[chipID][4][1] = chip[chipID].visAPCMVolume[1];
    }

    private int YM2610_Write(byte chipID, int adr, byte data) {
        if (chip[chipID] == null) return 0;
        chip[chipID].setReg(adr, data);
        return 0;
    }

    public void YM2610_setAdpcmA(byte chipID, byte[] _adpcma, int _adpcma_size) {
        if (chip[chipID] == null) return;
        chip[chipID].setAdpcmA(_adpcma, _adpcma_size);
    }

    public void YM2610_setAdpcmB(byte chipID, byte[] _adpcmb, int _adpcmb_size) {
        if (chip[chipID] == null) return;
        chip[chipID].setAdpcmB(_adpcmb, _adpcmb_size);
    }

    public void SetFMVolume(byte chipID, int db) {
        if (chip[chipID] == null) return;

        chip[chipID].setVolumeFM(db);
    }

    public void SetPSGVolume(byte chipID, int db) {
        if (chip[chipID] == null) return;

        chip[chipID].setVolumePSG(db);
    }

    public void SetAdpcmAVolume(byte chipID, int db) {
        if (chip[chipID] == null) return;

        chip[chipID].setVolumeADPCMATotal(db);
    }

    public void SetAdpcmBVolume(byte chipID, int db) {
        if (chip[chipID] == null) return;

        chip[chipID].setVolumeADPCMB(db);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        return YM2610_Write(chipID, (int) adr, (byte) data);
    }
}

