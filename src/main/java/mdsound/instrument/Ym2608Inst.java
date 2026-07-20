package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.fmgen.Opna;


public class Ym2608Inst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 8000000;

    private final Opna.OPNA[] chips = {new Opna.OPNA(), new Opna.OPNA()};

    private final int[][] keyOn = {new int[11], new int[11]};

    public Ym2608Inst() {
        // 0..Main 1..FM 2..SSG 3..Rhm 4..PCM
        visVolume = new int[][][] {
                {{0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}},
                {{0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}}
        };
    }

    @Override
    public String getName() {
        return "YM2608";
    }

    @Override
    public String getShortName() {
        return "OPNA";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    /**
     * @param option {@code String}: Path of rhythm sound dir or
     *               {@code Function<String, Stream>}: function returns full path with given filename
     * @see "mdplayer.Common#getOPNARyhthmStream"
     */
    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        if (option != null && option.length > 0) {
            if (option[0] instanceof Function function) // <String, Stream>
                chips[chipId].init(clock, samplingRate, false, chipId, function);
            else if (option[0] instanceof String string)
                chips[chipId].init(clock, samplingRate, false, chipId, string);
        } else {
            chips[chipId].init(clock, samplingRate, chipId);
        }

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

    private final int[] buffer = new int[2];

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
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

    //----

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
            case "RHYTHM" -> {
                chips[0].setVolumeRhythmTotal(vol);
                chips[1].setVolumeRhythmTotal(vol);
            }
            case "ADPCM" -> {
                chips[0].setVolumeADPCM(vol);
                chips[1].setVolumeADPCM(vol);
            }
        }
    }

    // ----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x80, 1d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Object... args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" -> {
                result.put("ym2608", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
                result.put("ym2608:FM", getMonoVolume(visVolume[0][1][0], visVolume[0][1][1], visVolume[1][1][0], visVolume[1][1][1]));
                result.put("ym2608:SSG", getMonoVolume(visVolume[0][2][0], visVolume[0][2][1], visVolume[1][2][0], visVolume[1][2][1]));
                result.put("ym2608:RHYTHM", getMonoVolume(visVolume[0][3][0], visVolume[0][3][1], visVolume[1][3][0], visVolume[1][3][1]));
                result.put("ym2608:ADPCM", getMonoVolume(visVolume[0][4][0], visVolume[0][4][1], visVolume[1][4][0], visVolume[1][4][1]));
            }
            case "keyOn" -> {
//                for (int i = 0; i < 11; i++) {
//                    keyOn[chipId][i] = chips[chipId].CHANNEL[i].KeyOn;
//                }
                result.put(getName(), keyOn[chipId]);
            }
            case "adpcm" -> result.put(getName(), chips[chipId].getADPCMBuffer());
            case "statusEx" -> result.put(getName(), chips[chipId].readStatusEx());
            case "info" -> {
                Opna.OPNA chip = chips[chipId];
                result.put("ch3ex", chip.isCh3Extended());
                result.put("ssg.register", chip.getSsgRegisters());
                result.put("timerB", chip.getTimerB());
                for (int ch = 0; ch < Opna.OPNABase.CHANNELS; ch++) {
                    result.put("channels." + ch + ".keyOn", chip.isKeyOn(ch));
                    result.put("channels." + ch + ".fnum", chip.getFnum(ch));
                    result.put("channels." + ch + ".block", chip.getBlock(ch));
                    result.put("channels." + ch + ".totalLevel", chip.getCarrierTotalLevel(ch));
                    result.put("channels." + ch + ".pan", chip.getPan(ch));
                }
                result.put("rhythm.key", chip.getRhythmKey());
                result.put("adpcm.control", chip.getAdpcmControl());
                result.put("adpcm.pan", chip.getAdpcmPan());
                result.put("adpcm.deltaN", chip.getAdpcmDeltaN());
                result.put("adpcm.level", chip.getAdpcmLevel());
                for (int slot = 0; slot < 4; slot++) {
                    result.put("channels.2.slots." + slot + ".keyOn", chip.isKeyOn(2, slot));
                    result.put("channels.2.slots." + slot + ".fnum", chip.getSlotFnum(2, slot));
                    result.put("channels.2.slots." + slot + ".block", chip.getSlotBlock(2, slot));
                }
            }
        }
        return result;
    }
}
