package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.OkiM6295;


public class OkiM6295Inst extends Instrument.BaseInstrument implements PcmEnabledInstrument {

    public static final int MAX_CHIPS = 0x02;

    private final OkiM6295[] chips = {new OkiM6295(), new OkiM6295()};

    private final int[] mask = {0, 0};

    public OkiM6295Inst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "OKIM6295";
    }

    @Override
    public String getShortName() {
        return "OKI9";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    /** @param option 0: (BiConsumer<Integer, Integer>) fn, 1: (int) sampleRate */
    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        BiConsumer<Integer, Integer> callbackFunc = (BiConsumer<Integer, Integer>) option[0];
        int oldSampleRate = (int) option[1];
        chips[chipId].setCallback(newSamplingRate -> callbackFunc.accept(oldSampleRate, newSamplingRate));

        return chips[chipId].start(clock);
    }

    /**
     * read the status port of an OKIM6295-compatible chips
     */
    @Override
    public int read(int chipId, int adr) {
        return chips[chipId].read(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].stop();
    }

    @Override
    public synchronized void setMask(int chipId, int ch) {
        mask[chipId] |= ch;
        setMuteMask(chipId, mask[chipId]);
    }

    @Override
    public synchronized void resetMask(int chipId, int ch) {
        mask[chipId] &= ~(int) ch;
        setMuteMask(chipId, mask[chipId]);
    }

    private void setMuteMask(int chipId, int muteMask) {
        chips[chipId].setMuteMask(muteMask);
    }

    /** @param extras 0: srcOffset, 1: romSize */
    @Override
    public synchronized void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
        int srcOffset = (int) extras[0];
        int romSize = (int) extras[1];
        chips[chipId].writeRom(romSize, offset, length, buf, srcOffset);
    }

    //----

    private synchronized Map<String, Object> getInfo(int chipId) {
        OkiM6295 chip = chips[chipId];
        OkiM6295.ChannelInfo info = chip.readChInfo();

        Map<String, Object> newParam = new HashMap<>();
        for (int c = 0; c < 4; c++) {

            newParam.put("channels." + c + ".keyon", info.keyon[c]);
            newParam.put("channels." + c + ".sadr", info.chInfo[c].stAdr);
            newParam.put("channels." + c + ".eadr", info.chInfo[c].edAdr);
            // the key on above is an edge and reading it takes it; these are levels, and a second
            // view reading them takes nothing away from the first
            newParam.put("channels." + c + ".playing", chip.isPlaying(c));
            newParam.put("channels." + c + ".volume", chip.getVolume(c));
            newParam.put("channels." + c + ".mute", chip.isMuted(c));
        }

        newParam.put("sampleRate", chip.getSampleRate());
        newParam.put("masterClock", info.masterClock);
        newParam.put("pin7State", info.pin7State);
        newParam.put("nmkBank.0", info.nmkBank[0]);
        newParam.put("nmkBank.1", info.nmkBank[1]);
        newParam.put("nmkBank.2", info.nmkBank[2]);
        newParam.put("nmkBank.3", info.nmkBank[3]);

        return newParam;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100 /* 110 */, 2d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Object... args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "OKI6295");
            case "FAMILY" -> result.put(getName(), "OKI ADPCM");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
            case "info" -> result.putAll(getInfo(chipId));
        }
        return result;
    }
}
