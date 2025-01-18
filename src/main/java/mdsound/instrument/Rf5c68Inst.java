package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Rf5c68;


public class Rf5c68Inst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x02;
    public Rf5c68[] rf5C68Data = new Rf5c68[] {new Rf5c68(), new Rf5c68()};

    @Override
    public String getName() {
        return "RF5C68";
    }

    @Override
    public String getShortName() {
        return "RF68";
    }

    @Override
    public void reset(int chipId) {
        visVolume = new int[][][] {
                {new int[] {0, 0}},
                {new int[] {0, 0}}
        };
    }

    @Override
    public int start(int chipId, int samplingRate) {
        return start(chipId, samplingRate, 0);
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        if (chipId >= MAX_CHIPS)
            return 0;

        Rf5c68 chip = rf5C68Data[chipId];
        return chip.start(clock);
    }

    @Override
    public void stop(int chipId) {
        Rf5c68 chip = rf5C68Data[chipId];
        chip.stop();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        Rf5c68 chip = rf5C68Data[chipId];
        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        Rf5c68 chip = rf5C68Data[chipId];
        chip.write(adr, data);
        return 0;
    }

    private void device_reset_rf5c68(int chipId) {
        Rf5c68 chip = rf5C68Data[chipId];
        chip.reset();
    }

    private int rf5c68_mem_r(int chipId, int offset) {
        Rf5c68 chip = rf5C68Data[chipId];
        return chip.readMemory(offset);
    }

    public void rf5c68_mem_w(int chipId, int offset, int data) {
        Rf5c68 chip = rf5C68Data[chipId];
        chip.writeMemory(offset, data);
    }

    private void rf5c68_write_ram(int chipId, int dataStart, int dataLength, byte[] ramData) {
        Rf5c68 chip = rf5C68Data[chipId];
        chip.writeRam(dataStart, dataLength, ramData);
    }

    public void rf5c68_write_ram2(int chipId, int dataStart, int dataLength, byte[] ramData, int srcStartAdr) {
        Rf5c68 chip = rf5C68Data[chipId];
        chip.writeRam2(dataStart, dataLength, ramData, srcStartAdr);
    }

    private void rf5c68_set_mute_mask(int chipId, int muteMask) {
        Rf5c68 chip = rf5C68Data[chipId];
        chip.setMuteMask(muteMask);
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0xB0, 1d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "RF5C68");
            case "FAMILY" -> result.put(getName(), "Ricoh PCM");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
