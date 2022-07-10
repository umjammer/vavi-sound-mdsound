package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.IremGa20;


// GA20
public class Ga20Inst extends Instrument.BaseInstrument {

    @Override
    public void reset(byte chipId) {
        device_reset_iremga20(chipId);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipId, int clock) {
        return device_start_iremga20(chipId, 3579545);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        return device_start_iremga20(chipId, clockValue);
    }

    @Override
    public void stop(byte chipId) {
        device_stop_iremga20(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        IremGA20_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private static final int MAX_CHIPS = 0x02;
    private IremGa20[] ga20Data = new IremGa20[] {new IremGa20(), new IremGa20()};

    @Override
    public String getName() {
        return "Irem GA20";
    }

    @Override
    public String getShortName() {
        return "GA20";
    }

    private void IremGA20_update(byte chipId, int[][] outputs, int samples) {
        IremGa20 chip = ga20Data[chipId];
        chip.update(outputs, samples);
    }

    private void irem_ga20_w(byte chipId, int offset, byte data) {
        IremGa20 chip = ga20Data[chipId];
        chip.write(offset, data);
    }

    public byte irem_ga20_r(byte chipId, int offset) {
        IremGa20 chip = ga20Data[chipId];
        return chip.read(offset);
    }

    private void device_reset_iremga20(byte chipId) {
        IremGa20 chip = ga20Data[chipId];
        chip.reset();
    }

    private int device_start_iremga20(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        IremGa20 chip = ga20Data[chipId];
        return chip.start(clock);
    }

    public void device_stop_iremga20(byte chipId) {
        IremGa20 chip = ga20Data[chipId];
        chip.stop();
    }

    public void iremga20_write_rom(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        IremGa20 chip = ga20Data[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void iremga20_write_rom(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        IremGa20 chip = ga20Data[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    private void iremga20_set_mute_mask(byte chipId, int muteMask) {
        IremGa20 chip = ga20Data[chipId];
        chip.setMuteMask(muteMask);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        irem_ga20_w(chipId, adr, (byte) data);
        return 0;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x280, 1d);
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        result.put("ga20", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        return result;
    }
}
