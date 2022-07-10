package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.YmF271;


public class YmF271Inst extends Instrument.BaseInstrument {
    @Override
    public void reset(byte chipId) {
        device_reset_ymf271(chipId);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipId, int clock) {
        return device_start_ymf271(chipId, 16934400);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        return device_start_ymf271(chipId, clockValue);
    }

    @Override
    public void stop(byte chipId) {
        device_stop_ymf271(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        ymf271_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private static final int MAX_CHIPS = 0x10;
    public YmF271[] ymf271Chips = new YmF271[] {new YmF271(), new YmF271(),};

    @Override
    public String getName() {
        return "YMF271";
    }

    @Override
    public String getShortName() {
        return "OPX";
    }

    public void ymf271_update(byte chipId, int[][] outputs, int samples) {
        YmF271 chip = ymf271Chips[chipId];
        chip.update(outputs, samples);
    }

    public void ymf271_w(byte chipId, int offset, byte data) {
        YmF271 chip = ymf271Chips[chipId];
        chip.write(offset, data);
    }

    public byte ymf271_r(byte chipId, int offset) {
        YmF271 chip = ymf271Chips[chipId];
        return chip.read(offset);
    }

    public int device_start_ymf271(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        YmF271 chip = ymf271Chips[chipId];
        return chip.start(clock);
    }

    public void device_stop_ymf271(byte chipId) {
        YmF271 chip = ymf271Chips[chipId];
        chip.stop();
    }

    public void device_reset_ymf271(byte chipId) {
        YmF271 chip = ymf271Chips[chipId];
        chip.reset();
    }

    public void ymf271_write_rom(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        YmF271 chip = ymf271Chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void ymf271_write_rom(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        YmF271 chip = ymf271Chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    public void ymf271_set_mute_mask(byte chipId, int muteMask) {
        YmF271 chip = ymf271Chips[chipId];
        chip.setMuteMask(muteMask);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        ymf271_w(chipId, (port << 1) | 0x00, (byte) adr);
        ymf271_w(chipId, (port << 1) | 0x01, (byte) data);
        return 0;
    }

    /**
     * Generic get_info
     */
    /*DEVICE_GET_INFO( YmF271Inst ) {
            case DEVINFO_STR_NAME:       strcpy(info.s, "YMF271");      break;
            case DEVINFO_STR_FAMILY:     strcpy(info.s, "Yamaha FM");     break;
            case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
            case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
        }
    }*/

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        result.put("ymf271", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        return result;
    }
}
