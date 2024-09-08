
package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.C352;


public class C352Inst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x02;
    private static C352[] chips = new C352[MAX_CHIPS];

    @Override
    public String getName() {
        return "C352";
    }

    @Override
    public String getShortName() {
        return "C352";
    }

    public C352Inst() {
        visVolume = new int[][][] {
                // 0..Main
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public void reset(int chipId) {
        C352 chip = chips[chipId];
        chip.reset();
    }

    @Override
    public int start(int chipId, int clock) {
        return start(chipId, 44100, clock);
    }

    @Override
    public void stop(int chipId) {
        C352 chip = chips[chipId];
        chip.stop();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        C352 chip = chips[chipId];
        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int start(int chipId, int SamplingRate, int clockValue, Object... Option) {
        byte clockDivider;
        if (Option == null || Option.length < 1) clockDivider = 0;
        else clockDivider = (byte) Option[0];

        if (chipId >= MAX_CHIPS)
            return 0;

        C352 chip = chips[chipId];
        return chip.start(clockValue, clockDivider * 4);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        C352 chip = chips[chipId];
        chip.write(adr, data);
        return 0;
    }

    private int read(int chipId, int address) {
        C352 chip = chips[chipId];
        return chip.read(address);
    }

    public void c352_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        C352 chip = chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void c352_write_rom2(int chipId, int romSize, int dataStart, int dataLength,
                                byte[] romData, int srcStartAdr) {
        C352 c = chips[chipId];
        c.writeRom2(romSize, dataStart, dataLength, romData, srcStartAdr);
    }

    private void c352_set_mute_mask(int chipId, int muteMask) {
        C352 c = chips[chipId];
        c.setMuteMask(muteMask);
    }

    private int c352_get_mute_mask(int chipId) {
        C352 c = chips[chipId];
        return c.getMuteMask();
    }

    public void c352_set_options(byte flags) {
        C352.setOptions(flags);
    }

    private int get_mute_mask(int chipId) {
        C352 c = chips[chipId];
        return c.getMuteMask();
    }

    public int[] getFlags(int chipId) {
        C352 c = chips[chipId];
        return c.getFlags();
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x40, 8d);
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

    // TODO
    public void setRearMute(int vol, double ignored) {
        c352_set_options((byte) (vol & 0xff));
    }
}
