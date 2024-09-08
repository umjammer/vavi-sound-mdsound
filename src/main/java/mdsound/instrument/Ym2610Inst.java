package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.fmgen.Opna.OPNB;


public class Ym2610Inst extends Instrument.BaseInstrument {

    private static final int DefaultYM2610ClockValue = 8000000;
    private OPNB[] chip = new OPNB[2];

    @Override
    public String getName() {
        return "YM2610";
    }

    @Override
    public String getShortName() {
        return "OPNB";
    }

    public Ym2610Inst() {
        //0..Main 1..FM 2..SSG 3..PCMa 4..PCMb
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}},
                new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}}
        };
    }

    @Override
    public void reset(int chipId) {
        if (chip[chipId] == null) return;
        chip[chipId].reset();
    }

    @Override
    public int start(int chipId, int clock) {
        chip[chipId] = new OPNB();
        chip[chipId].init(DefaultYM2610ClockValue, clock);

        return clock;
    }

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        chip[chipId] = new OPNB();
        chip[chipId].init(clockValue, clock, false, new byte[0x20_ffff], 0x20_ffff, new byte[0x20_ffff], 0x20_ffff);

        return clock;
    }

    @Override
    public void stop(int chipId) {
        chip[chipId] = null;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
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

    @Override
    public int write(int chipId, int port, int adr, int data) {
        if (chip[chipId] == null) return 0;
        chip[chipId].setReg(adr, data);
        return 0;
    }

    public void setAdpcmA(int chipId, byte[] _adpcma, int _adpcma_size) {
        if (chip[chipId] == null) return;
        chip[chipId].setAdpcmA(_adpcma, _adpcma_size);
    }

    public void setAdpcmB(int chipId, byte[] _adpcmb, int _adpcmb_size) {
        if (chip[chipId] == null) return;
        chip[chipId].setAdpcmB(_adpcmb, _adpcmb_size);
    }

    private void setFMVolume(int chipId, int db) {
        if (chip[chipId] == null) return;
        chip[chipId].setVolumeFM(db);
    }

    private void setPSGVolume(int chipId, int db) {
        if (chip[chipId] == null) return;
        chip[chipId].setVolumePSG(db);
    }

    private void setAdpcmAVolume(int chipId, int db) {
        if (chip[chipId] == null) return;
        chip[chipId].setVolumeADPCMATotal(db);
    }

    private void setAdpcmBVolume(int chipId, int db) {
        if (chip[chipId] == null) return;
        chip[chipId].setVolumeADPCMB(db);
    }

    // ----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x80, 1d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        // TODO tag commonize
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" -> {
                result.put("ym2610", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
                result.put("ym2610FM", getMonoVolume(visVolume[0][1][0], visVolume[0][1][1], visVolume[1][1][0], visVolume[1][1][1]));
                result.put("ym2610SSG", getMonoVolume(visVolume[0][2][0], visVolume[0][2][1], visVolume[1][2][0], visVolume[1][2][1]));
                result.put("ym2610APCMA", getMonoVolume(visVolume[0][3][0], visVolume[0][3][1], visVolume[1][3][0], visVolume[1][3][1]));
                result.put("ym2610APCMB", getMonoVolume(visVolume[0][4][0], visVolume[0][4][1], visVolume[1][4][0], visVolume[1][4][1]));
            }
        }
        return result;
    }

    // TODO automatic wired, use annotation?
    public void setFMVolume(int vol, double ignored) {
        setFMVolume(0, vol);
        setFMVolume(1, vol);
    }

    // TODO automatic wired, use annotation?
    public void setPSGVolume(int vol, double ignored) {
        setPSGVolume(0, vol);
        setPSGVolume(1, vol);
    }

    // TODO automatic wired, use annotation?
    public void setAdpcmAVolume(int vol, double ignored) {
        setAdpcmAVolume(0, vol);
        setAdpcmAVolume(1, vol);
    }

    // TODO automatic wired, use annotation?
    public void setAdpcmBVolume(int vol, double ignored) {
        setAdpcmBVolume(0, vol);
        setAdpcmBVolume(1, vol);
    }
}

