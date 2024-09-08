package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.C140;


public class C140Inst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x02;

    public C140[] c140Data = new C140[MAX_CHIPS];

    public C140Inst() {
        // 0..Main
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    public byte c140_r(int chipId, int offset) {
        C140 info = c140Data[chipId];
        return info.read(offset);
    }

    private void c140_w(int chipId, int offset, int data) {
        C140 info = c140Data[chipId];
        info.write(offset, data);
    }

    public void c140_set_base(int chipId, byte[] base) {
        C140 info = c140Data[chipId];
        info.setBase(base);
    }

    public void c140_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        C140 info = c140Data[chipId];
        info.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void c140_write_rom2(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        C140 info = c140Data[chipId];
        info.writeRom2(romSize, dataStart, dataLength, romData, srcStartAdr);
    }

    public void c140_set_mute_mask(int chipId, int muteMask) {
        C140 info = c140Data[chipId];
        info.setMuteMask(muteMask);
    }

    @Override
    public String getName() {
        return "C140";
    }

    @Override
    public String getShortName() {
        return "C140";
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        C140 info = c140Data[chipId];
        info.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int start(int chipId, int clock) {
        return start(chipId, 44100, clock, C140.Type.SYSTEM2);
    }

    @Override
    public int start(int chipId, int samplingrate, int clockValue, Object... option) {
        if (chipId >= MAX_CHIPS)
            return 0;

        C140 info = c140Data[chipId];
        int sampleRate = clockValue;
        if ((Instrument.BaseInstrument.CHIP_SAMPLING_MODE == 0x01 && sampleRate < Instrument.BaseInstrument.CHIP_SAMPLE_RATE) ||
                Instrument.BaseInstrument.CHIP_SAMPLING_MODE == 0x02)
            sampleRate = Instrument.BaseInstrument.CHIP_SAMPLE_RATE;
        if (sampleRate >= 0x100_0000) // limit to 16 MHz sample rate (32 MB buffer)
            return 0;
        info.start(clockValue, sampleRate, (C140.Type) option[0]);
        return sampleRate;
    }

    @Override
    public void stop(int chipId) {
        C140 info = c140Data[chipId];
        info.stop();
    }

    @Override
    public void reset(int chipId) {
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        c140_w(chipId, adr, data);
        return 0;
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
        }
        return result;
    }
}