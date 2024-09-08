package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.K053260;


public class K053260Inst extends Instrument.BaseInstrument {

    @Override
    public String getName() {
        return "K053260";
    }

    @Override
    public String getShortName() {
        return "K053";
    }

    @Override
    public void reset(int chipId) {
        device_reset_k053260(chipId);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(int chipId, int clock) {
        return device_start_k053260(chipId, 3579545);
    }

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        return device_start_k053260(chipId, clockValue);
    }

    @Override
    public void stop(int chipId) {
        device_stop_k053260(chipId);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        k053260_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        k053260_w(chipId, adr, (byte) data);
        return 0;
    }

    private static final int MAX_CHIPS = 0x02;
    private K053260[] chips = new K053260[] {new K053260(), new K053260()};

    public void device_reset_k053260(int chipId) {
        K053260 chip = chips[chipId];
        chip.reset();
    }

    public void k053260_update(int chipId, int[][] outputs, int samples) {
        K053260 chip = chips[chipId];
        chip.update(outputs, samples);
    }

    public int device_start_k053260(int chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        K053260 chip = chips[chipId];
        return chip.start(clock);
    }

    public void device_stop_k053260(int chipId) {
        K053260 chip = chips[chipId];
        chip.stop();
    }

    public void k053260_w(int chipId, int offset, byte data) {
        K053260 chip = chips[chipId];
        chip.write(offset, data);
    }

    public byte k053260_r(int chipId, int offset) {
        K053260 chip = chips[chipId];
        return chip.read(offset);
    }

    public void k053260_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        K053260 chip = chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void k053260_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        K053260 chip = chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAdr);
    }

    public void k053260_set_mute_mask(int chipId, int muteMask) {
        K053260 chip = chips[chipId];
        chip.setMuteMask(muteMask);
    }

    /**
     * Generic get_info
     */
    /*DEVICE_GET_INFO( k053260 ) {
            case DEVINFO_STR_NAME:       strcpy(info.s, "K053260Inst");      break;
            case DEVINFO_STR_FAMILY:      strcpy(info.s, "Konami custom");    break;
            case DEVINFO_STR_VERSION:      strcpy(info.s, "1.0");       break;
            case DEVINFO_STR_CREDITS:      strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
        }
    }*/

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0xB3, 1d);
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
