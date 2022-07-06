package mdsound;

import java.util.function.Function;

import dotnet4j.io.Stream;
import mdsound.fmgen.Opna;


public class Ym2608 extends Instrument.BaseInstrument {
    private Opna.OPNA[] chip = new Opna.OPNA[2];
    //private Fmgen.OPNA2[] chip = new Fmgen.OPNA2[2];
    private static final int DefaultYM2608ClockValue = 8000000;

    @Override
    public String getName() {
        return "YM2608";
    }

    @Override
    public String getShortName() {
        return "OPNA";
    }

    public Ym2608() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}},
                new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}}
        };
        //0..Main 1..FM 2..SSG 3..Rhm 4..PCM
    }

    @Override
    public void reset(byte chipId) {
        chip[chipId].reset();
    }

    @Override
    public int start(byte chipId, int clock) {
        chip[chipId] = new Opna.OPNA(chipId);
        //chip[chipId] = new Fmgen.OPNA2();
        chip[chipId].init(DefaultYM2608ClockValue, clock);

        return clock;
    }

    /**
     * @param chipId
     * @param clock
     * @param clockValue
     * @param option リズム音ファイルのパス(終端に\をつけること)
     * @return
     */
    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        chip[chipId] = new Opna.OPNA(chipId);
        //chip[chipId] = new Fmgen.OPNA2();
        if (option != null && option.length > 0 && option[0] instanceof Function) { // <string, Stream>
            if (option[0] instanceof Function) // <String, Stream>
                chip[chipId].init(clockValue, clock, false, (Function<String, Stream>) option[0]);
            else if (option[0] instanceof String)
                chip[chipId].init(clockValue, clock, false, (String) option[0]);
        } else {
            chip[chipId].init(clockValue, clock);
        }

        return clock;
    }

    @Override
    public void stop(byte chipId) {
        chip[chipId] = null;
    }

    int[] buffer = new int[2];

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
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

    private int YM2608_Write(byte chipId, int adr, byte data) {
        if (chip[chipId] == null) return 0;

        chip[chipId].setReg(adr, data);
        return 0;
    }

    public void SetFMVolume(byte chipId, int db) {
        if (chip[chipId] == null) return;

        chip[chipId].setVolumeFM(db);
    }

    public void SetPSGVolume(byte chipId, int db) {
        if (chip[chipId] == null) return;

        chip[chipId].setVolumePSG(db);
    }

    public void SetRhythmVolume(byte chipId, int db) {
        if (chip[chipId] == null) return;

        chip[chipId].setVolumeRhythmTotal(db);
    }

    public void SetAdpcmVolume(byte chipId, int db) {
        if (chip[chipId] == null) return;

        chip[chipId].setVolumeADPCM(db);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        return YM2608_Write(chipId, adr, (byte) data);
    }

    public byte[] GetADPCMBuffer(byte chipId) {
        return chip[chipId].getADPCMBuffer();
    }

    public int ReadStatusEx(byte chipId) {
        return chip[chipId].readStatusEx();
    }
}
