package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.MultiPCM;


// TODO check SHIFT in all classes
public class MultiPcmInst extends Instrument.BaseInstrument {

    public static final int MAX_CHIPS = 0x02;

    private final MultiPCM[] chips = new MultiPCM[MAX_CHIPS];

    @Override
    public String getName() {
        return "Multi PCM";
    }

    @Override
    public String getShortName() {
        return "mPCM";
    }

    @Override
    public void reset(int chipId) {
        MultiPCM chip = chips[chipId];
        chip.reset();

        visVolume = new int[][][] {
                {{0, 0}},
                {{0, 0}}
        };
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        if (chipId >= MAX_CHIPS) return 0;

        MultiPCM chip = chips[chipId];
        return chip.start(clock);
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        MultiPCM chip = chips[chipId];
        chip.write(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        MultiPCM chip = chips[chipId];
        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        MultiPCM chip = chips[chipId];
        chip.stop();
    }

    /* MAME/M1 access functions */
    public void setBank(int chipId, int leftOffs, int rightOffs) {
        MultiPCM chip = chips[chipId];
        chip.setBank(leftOffs, rightOffs);
    }

    public void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        writePcm(chipId, romSize, dataStart, dataLength, romData, 0);
    }

    public void setMuteMask(int chipId, int muteMask) {
        MultiPCM chip = chips[chipId];
        chip.setMuteMask(muteMask);
    }

    // ----

    public synchronized void writeBank(int chipId, int Ch, int adr) {
        MultiPCM chip = chips[chipId];
        chip.writeBank(Ch, adr);
    }

    public synchronized void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        MultiPCM chip = chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAdr);
    }

    public synchronized MultiPCM getChip(int chipId) {
        return chips[chipId];
    }

    // ----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x40, 4d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "Sega/Yamaha 315-5560");
            case "FAMILY" -> result.put(getName(), "Sega custom");
            case "VERSION" -> result.put(getName(), "2.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
