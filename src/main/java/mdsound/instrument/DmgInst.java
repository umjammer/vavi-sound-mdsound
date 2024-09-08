
package mdsound.instrument;


import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.GbSound;


// DMG
public class DmgInst extends Instrument.BaseInstrument {

    @Override
    public void reset(int chipId) {
        resetDevice(chipId);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(int chipId, int clock) {
        return startDevice(chipId, 4194304);
    }

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        return startDevice(chipId, clockValue);
    }

    @Override
    public void stop(int chipId) {
        stopDevice(chipId);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        updateDevice(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private static final int MAX_CHIPS = 0x02;
    private GbSound[] gbSoundData = new GbSound[] {new GbSound(), new GbSound()};

    public int readWave(int chipId, int offset) {
        GbSound gb = gbSoundData[chipId];
        return gb.readWave(offset);
    }

    public void writeWave(int chipId, int offset, byte data) {
        GbSound gb = gbSoundData[chipId];
        gb.writeWave(offset, data);
    }

    public int readSound(int chipId, int offset) {
        GbSound gb = gbSoundData[chipId];
        return gb.readSound(offset);
    }

    private void writeSound(int chipId, int offset, int data) {
        GbSound gb = gbSoundData[chipId];
        gb.writeSound(offset, data);
    }

    @Override
    public String getName() {
        return "Gameboy DMG";
    }

    @Override
    public String getShortName() {
        return "DMG";
    }

    public void updateDevice(int chipId, int[][] outputs, int samples) {
        GbSound gb = gbSoundData[chipId];
        gb.update(outputs, samples);
    }

    public int startDevice(int chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        GbSound gb = gbSoundData[chipId];

        int rate = (clock & 0x7fff_ffff) / 64;
        if (((Instrument.BaseInstrument.CHIP_SAMPLING_MODE & 0x01) != 0 && rate < Instrument.BaseInstrument.CHIP_SAMPLE_RATE) ||
                Instrument.BaseInstrument.CHIP_SAMPLING_MODE == 0x02)
            rate = Instrument.BaseInstrument.CHIP_SAMPLE_RATE;
        gb.start(clock, rate);
        return rate;
    }

    public void stopDevice(int chipId) {
    }

    public void resetDevice(int chipId) {
        GbSound gb = gbSoundData[chipId];
        gb.reset();
    }

    public void setMuteMask(int chipId, int muteMask) {
        GbSound gb = gbSoundData[chipId];
        gb.setMuteMask(muteMask);
    }

    public int getMuteMask(int chipId) {
        GbSound gb = gbSoundData[chipId];
        return gb.getMuteMask();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        writeSound(chipId, adr, data);
        return 0;
    }

    public GbSound getSoundData(int chipId) {
        return gbSoundData[chipId];
    }

    //----

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
