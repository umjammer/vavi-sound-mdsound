package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.ScdPcm;


// RF5C164
public class ScdPcmInst extends Instrument.BaseInstrument {

    public static final int MAX_CHIPS = 0x02;

    private final ScdPcm[] chips = {new ScdPcm(), new ScdPcm()};

    private final int[][][] volumes = {
            {new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2]},
            {new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2]}
    };

    public ScdPcmInst() {
        // 0..Main
        visVolume = new int[][][] {
                {{0, 0}},
                {{0, 0}}
        };
    }

    @Override
    public String getName() {
        return "RF5C164";
    }

    @Override
    public String getShortName() {
        return "RF5C";
    }

    @Override
    public void reset(int chipId) {
        ScdPcm chip = chips[chipId];
        chip.reset();
    }

    // samplingRate unused
    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        if (chipId >= 0x02) return 0;

        int rate = (clock & 0x7fff_ffff) / 384;
        if (((CHIP_SAMPLING_MODE & 0x01) != 0 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;

        ScdPcm chip = chips[chipId];
        chip.init(rate);
        chip.start(clock);

        return rate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        ScdPcm chip = chips[chipId];
        chip.writeReg(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        ScdPcm chip = chips[chipId];

        ScdPcm chip1 = chips[chipId];
        chip1.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        ScdPcm chip = chips[chipId];
    }

    public void setRate(int chipId, int rate) {
        ScdPcm chip = chips[chipId];
        chip.setRate(rate);
    }

    public void writePcm(int chipId, int dataStart, int dataLength, byte[] ramData) {
        writePcm(chipId, dataStart, dataLength, ramData, 0);
    }

    private void setMuteMask(int chipId, int muteMask) {
        ScdPcm chip = chips[chipId];
        chip.setMuteMask(muteMask);
    }

    public void setMuteCh(int chipId, int ch, int mute) {
        ScdPcm chip = chips[chipId];
        chip.setMuteCh(ch, mute);
    }

    // ----

    public synchronized void writePcm(int chipId, int ramStartAdr, int ramDataLength, byte[] srcData, int srcStartAdr) {
        ScdPcm chip = chips[chipId];
        chip.writeRam2(ramStartAdr, ramDataLength, srcData, srcStartAdr);
    }

    public synchronized void writeMemory(int chipId, int adr, int data) {
        ScdPcm chip = chips[chipId];
        chip.writeMem(adr, data);
    }

    public synchronized int[][] readVolumes(int chipId) {
        return volumes[chipId];
    }

    public synchronized void setMask(int chipId, int ch) {
        setMuteMask(chipId, 1);
    }

    public synchronized void resetMask(int chipId, int ch) {
        setMuteMask(chipId, 0);
    }

    public synchronized ScdPcm getChip(int chipId) {
        return chips[chipId];
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x80, 2d);
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
