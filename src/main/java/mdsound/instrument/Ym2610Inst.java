package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.Instrument.AdpcmEnabledInstrument;
import mdsound.fmgen.Opna.OPNB;


public class Ym2610Inst extends Instrument.BaseInstrument implements AdpcmEnabledInstrument {

    public static final int DefaultClockValue = 8000000;

    private final OPNB[] chips = {new OPNB(), new OPNB()};

    private final int[][] keyOn = {new int[11], new int[11]};

    @Override
    public String getName() {
        return "YM2610";
    }

    @Override
    public String getShortName() {
        return "OPNB";
    }

    public Ym2610Inst() {
        // 0..Main 1..FM 2..SSG 3..PCMa 4..PCMb
        visVolume = new int[][][] {
                {{0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}},
                {{0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}}
        };
    }

    @Override
    public void reset(int chipId) {
        assert chipId < chips.length;
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId].init(clock, samplingRate, false, new byte[0x20_ffff], 0x20_ffff, new byte[0x20_ffff], 0x20_ffff);
        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < chips.length;
        chips[chipId].setReg(port * 0x100 + adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        assert chipId < chips.length;

        int[] buffer = new int[2];
        buffer[0] = 0;
        buffer[1] = 0;
        chips[chipId].mix(buffer, 1);
        for (int i = 0; i < 1; i++) {
            outputs[0][i] = buffer[i * 2 + 0];
            outputs[1][i] = buffer[i * 2 + 1];
//logger.log(Level.TRACE, "[%8d] : [%8d] [%d]\r".formatted(outputs[0][i], outputs[1][i],i));
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
        visVolume[chipId][1][0] = chips[chipId].visVolume[0];
        visVolume[chipId][1][1] = chips[chipId].visVolume[1];
        visVolume[chipId][2][0] = chips[chipId].psg.visVolume;
        visVolume[chipId][2][1] = chips[chipId].psg.visVolume;
        visVolume[chipId][3][0] = chips[chipId].visRtmVolume[0];
        visVolume[chipId][3][1] = chips[chipId].visRtmVolume[1];
        visVolume[chipId][4][0] = chips[chipId].visAPCMVolume[0];
        visVolume[chipId][4][1] = chips[chipId].visAPCMVolume[1];
    }

    @Override
    public void stop(int chipId) {
    }

    @Override
    public void setMask(int chipId, int ch) {
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }

    public void setVolume(String tag, int vol, double ignored) {
        switch (tag) {
            case "FM" -> {
                chips[0].setVolumeFM(vol);
                chips[1].setVolumeFM(vol);
            }
            case "SSG" -> {
                chips[0].setVolumePSG(vol);
                chips[1].setVolumePSG(vol);
            }
            case "ADPCMA" -> {
                chips[0].setVolumeADPCMATotal(vol);
                chips[1].setVolumeADPCMATotal(vol);
            }
            case "ADPCMB" -> {
                chips[0].setVolumeADPCMB(vol);
                chips[1].setVolumeADPCMB(vol);
            }
        }
    }

    // ----

    @Override
    public synchronized void writeAdpcmA(int chipId, byte[] Buf) {
        assert chipId < chips.length;
        chips[chipId].setAdpcmA(Buf, Buf.length);
    }

    @Override
    public synchronized void writeAdpcmB(int chipId, byte[] Buf) {
        assert chipId < chips.length;
        chips[chipId].setAdpcmB(Buf, Buf.length);
    }

    // ----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x80, 1d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Map<String, Object> args) {
        // TODO tag commonize
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" -> {
                result.put("ym2610", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
                result.put("ym2610:FM", getMonoVolume(visVolume[0][1][0], visVolume[0][1][1], visVolume[1][1][0], visVolume[1][1][1]));
                result.put("ym2610:SSG", getMonoVolume(visVolume[0][2][0], visVolume[0][2][1], visVolume[1][2][0], visVolume[1][2][1]));
                result.put("ym2610:ADPCMA", getMonoVolume(visVolume[0][3][0], visVolume[0][3][1], visVolume[1][3][0], visVolume[1][3][1]));
                result.put("ym2610:ADPCMB", getMonoVolume(visVolume[0][4][0], visVolume[0][4][1], visVolume[1][4][0], visVolume[1][4][1]));
            }
            case "keyOn" -> {
                for (int i = 0; i < 11; i++) {
                    //keyOn[chipId][i] = chips[chipId].CHANNEL[i].KeyOn;
                }
                result.put(getName(), keyOn[chipId]);
            }
        }
        return result;
    }
}

