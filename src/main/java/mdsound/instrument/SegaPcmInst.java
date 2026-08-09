package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.SegaPcm;


public class SegaPcmInst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x02;

    private final SegaPcm[] chips = {new SegaPcm(), new SegaPcm()};

    private final int[] mask = {0, 0};

    public SegaPcmInst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public void init() {
        mask[0] = 0;
        mask[1] = 0;
    }

    @Override
    public String getName() {
        return "SEGA PCM";
    }

    @Override
    public String getShortName() {
        return "SPCM";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

//    public int start(int chipId, int samplingRate) {
//        assert chipId < MAX_CHIPS;
//        int intFBank = 0;
//        return chips[chipId].start(samplingRate, intFBank);
//    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;
        return chips[chipId].start(clock, (int) option[0]);
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
        mask[chipId] &= ~ch;
        chips[chipId].setMuteMask(mask[chipId]);
    }

    public synchronized void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        chips[chipId].writeRom2(romSize, dataStart, dataLength, romData, srcStartAdr);
    }

    // ----

    private synchronized Map<String, Object> getInfo(int chipId) {
        SegaPcm chip = chips[chipId];
        Map<String, Object> info = new HashMap<>();
        byte[] register = new byte[0x200];
        for (int i = 0; i < 0x200; i++) {
            register[i] = (byte) chip.read(i);
        }
        info.put("register", register);
        return info;
    }

    // ----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x180, 1d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Object... args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "Sega PCM");
            case "FAMILY" -> result.put(getName(), "Sega custom");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
            case "info" -> result.putAll(getInfo(chipId));
        }
        return result;
    }
}
