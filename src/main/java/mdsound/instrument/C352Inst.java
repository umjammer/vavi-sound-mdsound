
package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.C352;


public class C352Inst extends Instrument.BaseInstrument {

    public C352Inst() {
        visVolume = new int[][][] {
                // 0..Main
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public void reset(byte chipId) {
        device_reset_c352(chipId);
    }

    @Override
    public int start(byte chipId, int clock) {
        return start(chipId, 44100, clock);
    }

    @Override
    public void stop(byte chipId) {
        device_stop_c352(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        c352_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int start(byte chipId, int SamplingRate, int clockValue, Object... Option) {
        byte bytC352ClkDiv = 0;
        if (Option == null || Option.length < 1) bytC352ClkDiv = 0;
        else bytC352ClkDiv = (byte) Option[0];

        return device_start_c352(chipId, clockValue, bytC352ClkDiv * 4);
    }

    private static final int MAX_CHIPS = 0x02;
    private static C352[] c352Data = new C352[] {new C352(), new C352()};

    @Override
    public String getName() {
        return "C352Inst";
    }

    @Override
    public String getShortName() {
        return "C352Inst";
    }

    private void c352_update(byte chipId, int[][] outputs, int samples) {
        C352 c = c352Data[chipId];
        c.update(outputs, samples);
    }

    private int device_start_c352(byte chipId, int clock, int clkdiv) {
        if (chipId >= MAX_CHIPS)
            return 0;

        C352 c = c352Data[chipId];
        return c.start(clock, clkdiv);
    }

    private void device_stop_c352(byte chipId) {
        C352 c = c352Data[chipId];
        c.stop();
    }

    private void device_reset_c352(byte chipId) {
        C352 c = c352Data[chipId];
        c.reset();
    }

    private int c352_r(byte chipId, int address) {
        C352 c = c352Data[chipId];
        return c.read(address);
    }

    private void c352_w(byte chipId, int address, int val) {
        C352 c = c352Data[chipId];
        c.write(address, val);
    }

    public void c352_write_rom(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        C352 c = c352Data[chipId];
        c.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void c352_write_rom2(byte chipId, int romSize, int dataStart, int dataLength,
                                byte[] romData, int srcStartAdr) {
        C352 c = c352Data[chipId];
        c.writeRom2(romSize, dataStart, dataLength, romData, srcStartAdr);
    }

    private void c352_set_mute_mask(byte chipId, int muteMask) {
        C352 c = c352Data[chipId];
        c.setMuteMask(muteMask);
    }

    private int c352_get_mute_mask(byte chipId) {
        C352 c = c352Data[chipId];
        return c.getMuteMask();
    }

    public void c352_set_options(byte flags) {
        C352.setOptions(flags);
    }

    private int get_mute_mask(byte chipId) {
        C352 c = c352Data[chipId];
        return c.getMuteMask();
    }

    public int[] getFlags(byte chipId) {
        C352 c = c352Data[chipId];
        return c.getFlags();
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        c352_w(chipId, adr, data);
        return 0;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x40, 8d);
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        result.put("c352", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        return result;
    }
}
