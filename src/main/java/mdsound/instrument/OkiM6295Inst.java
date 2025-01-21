package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.MDSound;
import mdsound.MDSound.Chip;
import mdsound.chips.OkiM6295;


public class OkiM6295Inst extends Instrument.BaseInstrument {

    public static final int MAX_CHIPS = 0x02;

    private final OkiM6295[] chips = {new OkiM6295(), new OkiM6295()};

    private final int[] mask = {0, 0};

    public OkiM6295Inst() {
        // 0..Main
        visVolume = new int[][][] {
                {{0, 0}},
                {{0, 0}}
        };
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
        OkiM6295 chip = chips[chipId];
        chip.reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        if (chipId >= MAX_CHIPS)
            return 0;

        OkiM6295 chip = chips[chipId];
        return chip.start(clock);
    }

    /**
     * read the status port of an OKIM6295-compatible chips
     */
    @Override
    public int read(int chipId, int adr) {
        OkiM6295 chip = chips[chipId];
        return chip.read(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        OkiM6295 chip = chips[chipId];
        chip.write(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        OkiM6295 chip = chips[chipId];
        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        OkiM6295 chip = chips[chipId];
        chip.stop();
    }

    public void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        writePcm(chipId, romSize, dataStart, dataLength, romData, 0);
    }

    private void setMuteMask(int chipId, int muteMask) {
        OkiM6295 chip = chips[chipId];
        chip.setMuteMask(muteMask);
    }

    public void setCallback(int chipId, BiConsumer<Chip, Integer> callbackFunc, MDSound.Chip dataPtr) {
        OkiM6295 chip = chips[chipId];
        chip.setCallback(samplingRate -> callbackFunc.accept(dataPtr, samplingRate));
    }

    //----

    public synchronized void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        OkiM6295 chip = chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAdr);
    }

    public synchronized OkiM6295 getChip(int chipId) {
        return chips[chipId];
    }

    public synchronized void setMask(int chipId, int ch) {
        mask[chipId] |= ch;
        setMuteMask(chipId, mask[chipId]);
    }

    public synchronized OkiM6295.ChannelInfo getChInfo(int chipId) {
        OkiM6295 chip = chips[chipId];
        return chip.readChInfo();
    }

    public synchronized void resetMask(int chipId, int ch) {
        mask[chipId] &= ~(int) ch;
        setMuteMask(chipId, mask[chipId]);
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100 /* 110 */, 2d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "OKI6295");
            case "FAMILY" -> result.put(getName(), "OKI ADPCM");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
