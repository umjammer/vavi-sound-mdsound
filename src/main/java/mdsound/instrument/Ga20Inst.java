package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.IremGa20;


// GA20
public class Ga20Inst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 3579545;
    public static final int MAX_CHIPS = 0x02;

    private final IremGa20[] chips = {new IremGa20(), new IremGa20()};

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
        IremGa20 chip = chips[chipId];
        chip.reset();

        visVolume = new int[][][] {
                {{0, 0}},
                {{0, 0}}
        };
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        if (chipId >= MAX_CHIPS)
            return 0;

        IremGa20 chip = chips[chipId];
        return chip.start(clock);
    }

    @Override
    public int read(int chipId, int adr) {
        IremGa20 chip = chips[chipId];
        return chip.read(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        IremGa20 chip = chips[chipId];
        chip.write(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        IremGa20 chip = chips[chipId];
        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        IremGa20 chip = chips[chipId];
        chip.stop();
    }

    public void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        writePcm(chipId, romSize, dataStart, dataLength, romData, 0);
    }

    public void setMuteMask(int chipId, int muteMask) {
        IremGa20 chip = chips[chipId];
        chip.setMuteMask(muteMask);
    }

    //----

    public synchronized void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        IremGa20 chip = chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAdr);
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
