package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.K054539;


public class K054539Inst extends Instrument.BaseInstrument {

    public static final int MAX_CHIPS = 0x02;

    private final K054539[] chips = {new K054539(), new K054539()};

    public K054539Inst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "K054539";
    }

    @Override
    public String getShortName() {
        return "K054";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    /**
     * @param Option 0: [Byte] ?
     */
    @Override
    public int start(int chipId, int samplingRate, int clock, Object... Option) {
        assert chipId < MAX_CHIPS;

        int rate = chips[chipId].start(clock);

        int flags = 1;
        if (Option != null && Option.length > 0) flags = (int) (byte) Option[0];
        chips[chipId].intFlags(flags);

        return rate;
    }

    @Override
    public int read(int chipId, int adr) {
        return chips[chipId].write(adr);
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

    public void setGain(int chipId, int channel, double gain) {
        if (gain >= 0) chips[chipId].setGain(channel, gain);
    }

    private void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        writePcm(chipId, romSize, dataStart, dataLength, romData, 0);
    }

    public void setMuteMask(int chipId, int muteMask) {
        chips[chipId].setMuteMask(muteMask);
    }

    //----

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
            case "NAME" -> result.put(getName(), "K054539");
            case "FAMILY" -> result.put(getName(), "Konami custom");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
