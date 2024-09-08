package mdsound.instrument;

import java.util.function.Function;

import dotnet4j.io.Stream;
import mdsound.Instrument;
import mdsound.fmvgen.OPNA2;


public class Ym2609Inst extends Instrument.BaseInstrument {

    private static final int DefaultYM2609ClockValue = 8000000;
    private OPNA2[] chip = new OPNA2[2];

    @Override
    public String getName() {
        return "YM2609";
    }

    @Override
    public String getShortName() {
        return "OPNA2";
    }

    // TODO
    boolean visVol;

    public Ym2609Inst() {
        visVolume = new int[][][] {
                // 0..Main 1..FM 2..SSG 3..Rhm 4..PCM
                new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}},
                new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}}
        };
    }

    @Override
    public void reset(int chipId) {
        chip[chipId].reset();
    }

    @Override
    public int start(int chipId, int clock) {
        chip[chipId] = new OPNA2(clock);
        chip[chipId].init(DefaultYM2609ClockValue, clock);

        return clock;
    }

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
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
    public void stop(int chipId) {
        chip[chipId] = null;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
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

    @Override
    public int write(int chipId, int port, int adr, int data) {
        if (chip[chipId] == null) return 0;

        chip[chipId].setReg(adr, data);
        return 0;
    }

    private void setFMVolume(int chipId, int db) {
        if (chip[chipId] == null) return;
        chip[chipId].setVolumeFM(db);
    }

    private void setPSGVolume(int chipId, int db) {
        if (chip[chipId] == null) return;
        chip[chipId].setVolumePSG(db);
    }

    private void setRhythmVolume(int chipId, int db) {
        if (chip[chipId] == null) return;
        chip[chipId].setVolumeRhythmTotal(db);
    }

    private void setAdpcmVolume(int chipId, int db) {
        if (chip[chipId] == null) return;
        chip[chipId].setVolumeADPCM(db);
    }

    public void setAdpcmA(int chipId, byte[] _adpcma, int _adpcma_size) {
        if (chip[chipId] == null) return;
        chip[chipId].setAdpcmA(_adpcma, _adpcma_size);
    }

    //----

    // TODO automatic wired, use annotation?
    public void setFMVolume(int vol, double ignored) {
        setFMVolume((byte) 0, vol);
        setFMVolume((byte) 1, vol);
    }

    // TODO automatic wired, use annotation?
    public void setPSGVolume(int vol, double ignored) {
        setPSGVolume((byte) 0, vol);
        setPSGVolume((byte) 1, vol);
    }

    // TODO automatic wired, use annotation?
    public void setRhythmVolume(int vol, double ignored) {
        setRhythmVolume((byte) 0, vol);
        setRhythmVolume((byte) 1, vol);
    }

    // TODO automatic wired, use annotation?
    public void setAdpcmVolume(int vol, double ignored) {
        setAdpcmVolume((byte) 0, vol);
        setAdpcmVolume((byte) 1, vol);
    }
}
