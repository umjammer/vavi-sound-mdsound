package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.SegaPcm;


public class SegaPcmInst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x02;
    public SegaPcm[] SPCMData = new SegaPcm[] {new SegaPcm(), new SegaPcm()};

    @Override
    public String getName() {
        return "SEGA PCM";
    }

    @Override
    public String getShortName() {
        return "SPCM";
    }

    public SegaPcmInst() {
        // 0..Main
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(int chipId, int samplingRate) {
        int intFBank = 0;
        if (chipId >= MAX_CHIPS)
            return 0;

        SegaPcm spcm = SPCMData[chipId];
        return spcm.start(samplingRate, intFBank);
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        if (chipId >= MAX_CHIPS)
            return 0;

        SegaPcm spcm = SPCMData[chipId];
        return spcm.start(clock, (int) option[0]);
    }

    @Override
    public void stop(int chipId) {
        SegaPcm spcm = SPCMData[chipId];
        spcm.stop();
    }

    @Override
    public void reset(int chipId) {
        SegaPcm spcm = SPCMData[chipId];
        spcm.reset();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        SegaPcm spcm = SPCMData[chipId];
        spcm.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    public int sega_pcm_r(int chipId, int offset) {
        SegaPcm spcm = SPCMData[chipId];
        return spcm.read(offset);
    }

    public void sega_pcm_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        SegaPcm spcm = SPCMData[chipId];
        spcm.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void sega_pcm_write_rom2(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        SegaPcm spcm = SPCMData[chipId];
        spcm.writeRom2(romSize, dataStart, dataLength, romData, srcStartAdr);
    }

    public void segapcm_set_mute_mask(int chipId, int muteMask) {
        SegaPcm spcm = SPCMData[chipId];
        spcm.setMuteMask(muteMask);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        SegaPcm spcm = SPCMData[chipId];
        spcm.write(adr, data);
        return 0;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x180, 1d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "Sega PCM");
            case "FAMILY" -> result.put(getName(), "Sega custom");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
