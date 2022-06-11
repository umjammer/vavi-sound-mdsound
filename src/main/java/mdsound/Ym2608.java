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
    public void reset(byte chipID) {
        chip[chipID].reset();
    }

    @Override
    public int start(byte chipID, int clock) {
        chip[chipID] = new Opna.OPNA(chipID);
        //chip[chipID] = new Fmgen.OPNA2();
        chip[chipID].init(DefaultYM2608ClockValue, clock);

        return clock;
    }

    /**
     * @param chipID
     * @param clock
     * @param clockValue
     * @param option リズム音ファイルのパス(終端に\をつけること)
     * @return
     */
    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        chip[chipID] = new Opna.OPNA(chipID);
        //chip[chipID] = new Fmgen.OPNA2();
        if (option != null && option.length > 0 && option[0] instanceof Function) // <string, Stream>
        {
            if (option[0] instanceof Function) // <String, Stream>
                chip[chipID].init(clockValue, clock, false, (Function<String, Stream>) option[0]);
            else if (option[0] instanceof String)
                chip[chipID].init(clockValue, clock, false, (String) option[0]);
        } else {
            chip[chipID].init(clockValue, clock);
        }

        return clock;
    }

    @Override
    public void stop(byte chipID) {
        chip[chipID] = null;
    }

    int[] buffer = new int[2];

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
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

    private int YM2608_Write(byte chipID, int adr, byte data) {
        if (chip[chipID] == null) return 0;

        chip[chipID].setReg(adr, data);
        return 0;
    }

    public void SetFMVolume(byte chipID, int db) {
        if (chip[chipID] == null) return;

        chip[chipID].setVolumeFM(db);
    }

    public void SetPSGVolume(byte chipID, int db) {
        if (chip[chipID] == null) return;

        chip[chipID].setVolumePSG(db);
    }

    public void SetRhythmVolume(byte chipID, int db) {
        if (chip[chipID] == null) return;

        chip[chipID].setVolumeRhythmTotal(db);
    }

    public void SetAdpcmVolume(byte chipID, int db) {
        if (chip[chipID] == null) return;

        chip[chipID].setVolumeADPCM(db);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        return YM2608_Write(chipID, (int) adr, (byte) data);
    }

    public byte[] GetADPCMBuffer(byte chipID) {
        return chip[chipID].getADPCMBuffer();
    }

    public int ReadStatusEx(byte chipID) {
        return chip[chipID].readStatusEx();
    }
}
