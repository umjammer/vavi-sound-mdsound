package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.C140;


public class C140Inst extends Instrument.BaseInstrument {

    public static final int MAX_CHIPS = 0x02;

    private final C140[] chips = {new C140(), new C140()};

    private final int[] mask = {0, 0};

    public C140Inst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "C140";
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
        if (sampleRate >= 0x100_0000) // limit to 16 MHz sample rate (32 MB buffer)
            return 0;

        chips[chipId].start(clock, sampleRate, (C140.Type) option[0]);

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

    //----

    public void setBase(int chipId, byte[] base) {
        chips[chipId].setBase(base);
    }

    public void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        writePcm(chipId, romSize, dataStart, dataLength, romData, 0);
    }

    private void setMuteMask(int chipId, int muteMask) {
        chips[chipId].setMuteMask(muteMask);
    }

    public synchronized C140 getRegister(int cur) {
        return chips[cur];
    }

    public synchronized void setMask(int chipId, int ch) {
        mask[chipId] |= ch;
        setMuteMask(chipId, mask[chipId]);
    }

    public synchronized void resetMask(int chipId, int ch) {
        mask[chipId] &= ~(int) ch;
        setMuteMask(chipId, mask[chipId]);
    }

    public synchronized void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        chips[chipId].writeRom(romSize, dataStart, dataLength, romData, srcStartAdr);
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