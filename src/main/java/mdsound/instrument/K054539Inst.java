package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.K054539;


public class K054539Inst extends Instrument.BaseInstrument {

    public K054539Inst() {
        //0..Main
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public void reset(int chipId) {
        device_reset_k054539(chipId);
    }

    @Override
    public int start(int chipId, int clock) {
        return device_start_k054539(chipId, clock);
    }

    @Override
    public void stop(int chipId) {
        device_stop_k054539(chipId);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        k054539_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int start(int chipId, int SamplingRate, int clockValue, Object... Option) {
        int sampRate = device_start_k054539(chipId, clockValue);
        int flags = 1;
        if (Option != null && Option.length > 0) flags = (int) (byte) Option[0];
        k054539_init_flags(chipId, flags);

        return sampRate;
    }

    private static final int MAX_CHIPS = 0x02;
    private static K054539[] chips = new K054539[] {new K054539(), new K054539()};

    @Override
    public String getName() {
        return "K054539";
    }

    @Override
    public String getShortName() {
        return "K054";
    }

    public void k054539_init_flags(int chipId, int flags) {
        K054539 info = chips[chipId];
        info.intFlags(flags);
    }

    public void k054539_set_gain(int chipId, int channel, double gain) {
        K054539 info = chips[chipId];
        if (gain >= 0) info.setGain(channel, gain);
    }

    private void k054539_update(int chipId, int[][] outputs, int samples) {
        K054539 info = chips[chipId];
        info.update(outputs, samples);
    }

    private void k054539_w(int chipId, int offset, int data) {
        K054539 info = chips[chipId];
        info.write(offset, data);
    }

    public byte k054539_r(int chipId, int offset) {
        K054539 info = chips[chipId];
        return info.write(offset);
    }

    private int device_start_k054539(int chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        K054539 info = chips[chipId];
        return info.start(clock);
    }

    private void device_stop_k054539(int chipId) {
        K054539 info = chips[chipId];
        info.stop();
    }

    private void device_reset_k054539(int chipId) {
        K054539 chip = chips[chipId];
        chip.reset();
    }

    private void k054539_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        K054539 chip = chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void k054539_write_rom2(int chipId, int romSize, int dataStart, int dataLength,
                                   byte[] romData, int startAdr) {
        K054539 chip = chips[chipId];
        chip.writeRom2(romSize, dataStart, dataLength, romData, startAdr);
    }

    public void k054539_set_mute_mask(int chipId, int muteMask) {
        K054539 chip = chips[chipId];
        chip.setMuteMask(muteMask);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        k054539_w(chipId, adr, data);
        return 0;
    }

//    /**
//     * Generic get_info
//     */
//            case DEVINFO_STR_NAME:       strcpy(info.s, "K054539Inst");      break;
//            case DEVINFO_STR_FAMILY:     strcpy(info.s, "Konami custom");    break;
//            case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
//            case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
//        }
//    }

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
