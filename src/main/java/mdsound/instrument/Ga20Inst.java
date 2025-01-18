package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.IremGa20;


// GA20
public class Ga20Inst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x02;
    private final IremGa20[] ga20Data = new IremGa20[] {new IremGa20(), new IremGa20()};

    @Override
    public String getName() {
        return "Irem GA20";
    }

    @Override
    public String getShortName() {
        return "GA20";
    }

    @Override
    public void reset(int chipId) {
        IremGa20 chip = ga20Data[chipId];
        chip.reset();

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(int chipId, int samplingRate) {
        if (chipId >= MAX_CHIPS)
            return 0;

        IremGa20 chip = ga20Data[chipId];
        return chip.start(3579545);
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        if (chipId >= MAX_CHIPS)
            return 0;

        IremGa20 chip = ga20Data[chipId];
        return chip.start(clock);
    }

    @Override
    public void stop(int chipId) {
        IremGa20 chip = ga20Data[chipId];
        chip.stop();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        IremGa20 chip = ga20Data[chipId];
        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    public int irem_ga20_r(int chipId, int offset) {
        IremGa20 chip = ga20Data[chipId];
        return chip.read(offset);
    }

    public void iremga20_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        IremGa20 chip = ga20Data[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void iremga20_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        IremGa20 chip = ga20Data[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    private void iremga20_set_mute_mask(int chipId, int muteMask) {
        IremGa20 chip = ga20Data[chipId];
        chip.setMuteMask(muteMask);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        IremGa20 chip = ga20Data[chipId];
        chip.write(adr, data);
        return 0;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x280, 1d);
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
