package mdsound.instrument;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.C140;

import static java.lang.System.getLogger;


public class C140Inst extends Instrument.BaseInstrument implements PcmEnabledInstrument {

    private static final Logger logger = getLogger(C140Inst.class.getName());

    public static final int MAX_CHIPS = 0x02;

    private final C140[] chips = {new C140(), new C140()};

    private final int[] mask = {0, 0};

    private C140.Type type;

    public C140Inst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "C140" + type.name().toLowerCase();
    }

    @Override
    public String getShortName() {
        return "C140";
    }

    @Override
    public void init() {
        mask[0] = 0;
        mask[1] = 0;
    }

    @Override
    public void reset(int chipId) {
    }

    /**
     * @param option 0: C140.Type
     */
    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        int sampleRate = clock;
        if ((CHIP_SAMPLING_MODE == 0x01 && sampleRate < CHIP_SAMPLE_RATE) || CHIP_SAMPLING_MODE == 0x02)
            sampleRate = CHIP_SAMPLE_RATE;
        if (sampleRate >= 0x100_0000) { // limit to 16 MHz sample rate (32 MB buffer)
logger.log(Level.WARNING, "sampleRate: " + sampleRate);
            return 0;
        }

        type = (C140.Type) option[0];
        chips[chipId].start(clock, sampleRate, type);

        return sampleRate;
    }

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
        chips[chipId].setMuteMask(mask[chipId]);
    }

    @Override
    public synchronized void resetMask(int chipId, int ch) {
        mask[chipId] &= ~(int) ch;
        chips[chipId].setMuteMask(mask[chipId]);
    }

    /** @param extras 0: srcOffset, 1: romSize */
    @Override
    public synchronized void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
        int srcOffset = (int) extras[0];
        int romSize = (int) extras[1];
        chips[chipId].writeRom(romSize, offset, length, buf, srcOffset);
    }

    //----

    public void setBase(int chipId, byte[] base) {
        chips[chipId].setBase(base);
    }

    public synchronized C140 getRegister(int cur) {
        return chips[cur];
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        }
        return result;
    }
}