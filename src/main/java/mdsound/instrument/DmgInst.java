
package mdsound.instrument;


import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.GbSound;


// DMG
public class DmgInst extends Instrument.BaseInstrument {

    @Override
    public void reset(byte chipId) {
        resetDevice(chipId);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipId, int clock) {
        return startDevice(chipId, 4194304);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        return startDevice(chipId, clockValue);
    }

    @Override
    public void stop(byte chipId) {
        stopDevice(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        updateDevice(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private static final int MAX_CHIPS = 0x02;
    private GbSound[] gbSoundData = new GbSound[] {new GbSound(), new GbSound()};

    public byte readWave(byte chipId, int offset) {
        GbSound gb = gbSoundData[chipId];
        return gb.readWave(offset);
    }

    public void writeWave(byte chipId, int offset, byte data) {
        GbSound gb = gbSoundData[chipId];
        gb.writeWave(offset, data);
    }

    public byte readSound(byte chipId, int offset) {
        GbSound gb = gbSoundData[chipId];
        return gb.readSound(offset);
    }

    private void writeSound(byte chipId, int offset, byte data) {
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

    public void updateDevice(byte chipId, int[][] outputs, int samples) {
        GbSound gb = gbSoundData[chipId];
        gb.update(outputs, samples);
    }

    public int startDevice(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        GbSound gb = gbSoundData[chipId];

        int rate = (clock & 0x7FFFFFFF) / 64;
        if (((Instrument.BaseInstrument.CHIP_SAMPLING_MODE & 0x01) != 0 && rate < Instrument.BaseInstrument.CHIP_SAMPLE_RATE) ||
                Instrument.BaseInstrument.CHIP_SAMPLING_MODE == 0x02)
            rate = Instrument.BaseInstrument.CHIP_SAMPLE_RATE;
        gb.start(clock, rate);
        return rate;
    }

    public void stopDevice(byte chipId) {
    }

    public void resetDevice(byte chipId) {
        GbSound gb = gbSoundData[chipId];
        gb.reset();
    }

    public void setMuteMask(byte chipId, int muteMask) {
        GbSound gb = gbSoundData[chipId];
        gb.setMuteMask(muteMask);
    }

    public int getMuteMask(byte chipId) {
        GbSound gb = gbSoundData[chipId];
        return gb.getMuteMask();
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        writeSound(chipId, adr, (byte) data);
        return 0;
    }

    public GbSound getSoundData(byte chipId) {
        return gbSoundData[chipId];
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0xC0, 2d);
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        result.put("DMG", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        return result;
    }
}
