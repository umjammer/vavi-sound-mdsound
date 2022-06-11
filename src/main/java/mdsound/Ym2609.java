package mdsound;

import java.util.function.Function;

import mdsound.fmvgen.OPNA2;
import mdsound.fmvgen.effect.Compressor;
import mdsound.fmvgen.effect.HPFLPF;
import mdsound.fmvgen.effect.Reverb;
import mdsound.fmvgen.effect.ReversePhase;
import mdsound.fmvgen.effect.Chorus;
import mdsound.fmvgen.effect.Distortion;
import mdsound.fmvgen.effect.Eq3band;
import dotnet4j.io.Stream;


public class Ym2609 extends Instrument.BaseInstrument {
    private OPNA2[] chip = new OPNA2[2];
    private static final int DefaultYM2609ClockValue = 8000000;
    private Reverb[] reverb = new Reverb[2];
    private Distortion[] distortion = new Distortion[2];
    private Chorus[] chorus = new Chorus[2];
    private Eq3band[] ep3band = new Eq3band[2];
    private HPFLPF[] hpflpf = new HPFLPF[2];
    private ReversePhase[] reversePhase = new ReversePhase[2];
    private Compressor[] compressor = new Compressor[2];
    private static final int MaxCh = 39;
    private int[] updateBuffer = new int[2];

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
                new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}}
                , new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}}
        };
        //0..Main 1..FM 2..SSG 3..Rhm 4..PCM
    }

    @Override
    public void reset(byte chipID) {
        chip[chipID].reset();
    }

    @Override
    public int start(byte chipID, int clock) {
        reverb[chipID] = new Reverb((int) clock, MaxCh);
        distortion[chipID] = new Distortion((int) clock, MaxCh);
        chorus[chipID] = new Chorus((int) clock, MaxCh);
        ep3band[chipID] = new Eq3band((int) clock);
        hpflpf[chipID] = new HPFLPF((int) clock, MaxCh);
        reversePhase[chipID] = new ReversePhase();
        compressor[chipID] = new Compressor((int) clock, MaxCh);
        chip[chipID] = new OPNA2(reverb[chipID], distortion[chipID], chorus[chipID], ep3band[chipID], hpflpf[chipID], reversePhase[chipID], compressor[chipID]);
        chip[chipID].init(DefaultYM2609ClockValue, clock);

        return clock;
    }

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        reverb[chipID] = new Reverb((int) clock, MaxCh);
        distortion[chipID] = new Distortion((int) clock, MaxCh);
        chorus[chipID] = new Chorus((int) clock, MaxCh);
        ep3band[chipID] = new Eq3band((int) clock);
        hpflpf[chipID] = new HPFLPF((int) clock, MaxCh);
        reversePhase[chipID] = new ReversePhase();
        compressor[chipID] = new Compressor((int) clock, MaxCh);
        chip[chipID] = new OPNA2(reverb[chipID], distortion[chipID], chorus[chipID], ep3band[chipID], hpflpf[chipID], reversePhase[chipID], compressor[chipID]);

        if (option != null && option.length > 0 && option[0] instanceof Function) //<String, Stream>
        {
            if (option[0] instanceof Function) // <String, Stream>
                chip[chipID].init(clockValue, clock, false, (Function<String, Stream>) option[0], null, 0);
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

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        updateBuffer[0] = reverb[chipID].getDataFromPosL() >> 1;
        updateBuffer[1] = reverb[chipID].getDataFromPosR() >> 1;

        reverb[chipID].storeDataC(reverb[chipID].getDataFromPosL() >> 1, reverb[chipID].getDataFromPosR() >> 1);
        reverb[chipID].clearDataAtPos();

        chip[chipID].mix(updateBuffer, 1);
        //for (int i = 0; i < 1; i++)
        //{
        //    outputs[0][i] = updateBuffer[i * 2 + 0];
        //    outputs[1][i] = updateBuffer[i * 2 + 1];

        //    //rev[chipID].StoreData(0, (outputs[0][i] + outputs[1][i]) / 2);
        //}
        outputs[0][0] = updateBuffer[0];
        outputs[1][0] = updateBuffer[1];

        reverb[chipID].updatePos();

        if (!visVol) return;

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

    public int YM2609_Write(byte chipID, int adr, byte data) {
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

    public void SetAdpcmA(byte chipID, byte[] _adpcma, int _adpcma_size) {
        if (chip[chipID] == null) return;
        chip[chipID].setAdpcmA(_adpcma, _adpcma_size);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        return YM2609_Write(chipID, (int) adr, (byte) data);
    }
}
