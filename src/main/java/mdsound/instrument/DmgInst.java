package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.GbSound;


// DMG
public class DmgInst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 4194304;
    public static final int MAX_CHIPS = 0x02;

    private final GbSound[] chips = {new GbSound(), new GbSound()};

    public DmgInst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "Gameboy DMG";
    }

    @Override
    public String getShortName() {
        return "DMG";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert  chipId < MAX_CHIPS;

        int rate = (clock & 0x7fff_ffff) / 64;
        if (((CHIP_SAMPLING_MODE & 0x01) != 0 && rate < CHIP_SAMPLE_RATE) || CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;

        chips[chipId].start(clock, rate);
        return rate;
    }

    @Override
    public int read(int chipId, int adr) {
        return chips[chipId].readSound(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].writeSound(adr, data);
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
    }

    public int readPcm(int chipId, int offset) {
        return chips[chipId].readWave(offset);
    }

    public void writePcm(int chipId, int offset, byte data) {
        chips[chipId].writeWave(offset, data);
    }

    private void setMuteMask(int chipId, int muteMask) {
        chips[chipId].setMuteMask(muteMask);
    }

    public int getMuteMask(int chipId) {
        return chips[chipId].getMuteMask();
    }

    //----

    public synchronized GbSound getChip(int chipId) {
        return chips[chipId];
    }

    public synchronized void setMask(int chipId, int ch) {
        int maskStatus = getMuteMask(chipId);
        maskStatus |= 1 << ch;//ch:0 - 3
        setMuteMask(chipId, maskStatus);
    }

    public synchronized void resetMask(int chipId, int ch) {
        int maskStatus = getMuteMask(chipId);
        maskStatus &= ~(1 << ch);//ch:0 - 3
        setMuteMask(chipId, maskStatus);
    }

    // ----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0xC0, 2d);
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
