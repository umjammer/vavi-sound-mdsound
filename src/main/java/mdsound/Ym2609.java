package mdsound;

import java.util.function.Function;

import dotnet4j.io.Stream;
import mdsound.fmvgen.OPNA2;


public class Ym2609 extends Instrument.BaseInstrument {
    private OPNA2[] chip = new OPNA2[2];
    private static final int DefaultYM2609ClockValue = 8000000;

    @Override
    public String getName() {
        return "YM2609";
    }

    @Override
    public String getShortName() {
        return "OPNA2";
    }

    boolean visVol;

    public boolean getVisVol() {
        return visVol;
    }

    public Ym2609() {
        visVolume = new int[][][] {
                // 0..Main 1..FM 2..SSG 3..Rhm 4..PCM
                new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}},
                new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}}
        };
    }

    @Override
    public void reset(byte chipId) {
        chip[chipId].reset();
    }

    @Override
    public int start(byte chipId, int clock) {
        chip[chipId] = new OPNA2(clock);
        chip[chipId].init(DefaultYM2609ClockValue, clock);

        return clock;
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        chip[chipId] = new OPNA2(clock);

        if (option != null && option.length > 0 && option[0] instanceof Function) { //<String, Stream>
            if (option[0] instanceof Function) // <String, Stream>
                chip[chipId].init(clockValue, clock, false, (Function<String, Stream>) option[0], null, 0);
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

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        int[] updateBuffer = chip[chipId].update();
        //for (int i = 0; i < 1; i++) {
        //    outputs[0][i] = updateBuffer[i * 2 + 0];
        //    outputs[1][i] = updateBuffer[i * 2 + 1];

        //    //rev[chipId].StoreData(0, (outputs[0][i] + outputs[1][i]) / 2);
        //}
        outputs[0][0] = updateBuffer[0];
        outputs[1][0] = updateBuffer[1];

        if (!visVol) return;

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

    public int YM2609_Write(byte chipId, int adr, byte data) {
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

    public void SetAdpcmA(byte chipId, byte[] _adpcma, int _adpcma_size) {
        if (chip[chipId] == null) return;
        chip[chipId].setAdpcmA(_adpcma, _adpcma_size);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        return YM2609_Write(chipId, adr, (byte) data);
    }
}
