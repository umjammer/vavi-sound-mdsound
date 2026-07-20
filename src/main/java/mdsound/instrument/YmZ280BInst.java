package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.YmZ280B;


public class YmZ280BInst extends Instrument.BaseInstrument implements PcmEnabledInstrument {

    public static final int DefaultClockValue = 16934400;
    public static final int MAX_CHIPS = 0x10;

    private final YmZ280B[] chips = {new YmZ280B(), new YmZ280B()};

    @Override
    public String getName() {
        return "YMZ280B";
    }

    @Override
    public String getShortName() {
        return "YMZ";
    }

    public YmZ280BInst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;
        return chips[chipId].start(clock);
    }

    @Override
    public int read(int chipId, int adr) {
        return chips[chipId].read(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write(0x00, adr);
        chips[chipId].write(0x01, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].update(outputs, samples);

        // the loudest sample of the block, not the first one: a single sample lands wherever the
        // waveform happens to be, so a meter fed from it reads far under what is being heard
        int left = 0, right = 0;
        for (int i = 0; i < samples; i++) {
            left = Math.max(left, Math.abs(outputs[0][i]));
            right = Math.max(right, Math.abs(outputs[1][i]));
        }
        visVolume[chipId][0][0] = left;
        visVolume[chipId][0][1] = right;
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].stop();
    }

    @Override
    public void setMask(int chipId, int ch) {
//        chips[chipId].setMuteMask(ch); // TODO
    }

    @Override
    public void resetMask(int chipId, int ch) {
//        chips[chipId].setMuteMask(~ch); // TODO
    }

    /** @param extras 0: srcOffset, 1: romSize */
    @Override
    public synchronized void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
        int srcOffset = (int) extras[0];
        int romSize = (int) extras[1];
        chips[chipId].writeRom(romSize, offset, length, buf, srcOffset);
    }

    // handle external accesses

    private void updateIrqStateTimerCommon(Object param, int voiceNum) {
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x98, 0x20 / 19.0);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "YMZ280B");
            case "FAMILY" -> result.put(getName(), "Yamaha Wavetable");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
            case "info" -> {
                YmZ280B chip = chips[chipId];
                for (int ch = 0; ch < YmZ280B.VOICES; ch++) {
                    result.put("channels." + ch + ".playing", chip.isPlaying(ch));
                    result.put("channels." + ch + ".frequency", chip.getFrequency(ch));
                    result.put("channels." + ch + ".level", chip.getLevel(ch));
                    result.put("channels." + ch + ".pan", chip.getPan(ch));
                    result.put("channels." + ch + ".mute", chip.isMuted(ch));
                }
            }
        }
        return result;
    }
}
