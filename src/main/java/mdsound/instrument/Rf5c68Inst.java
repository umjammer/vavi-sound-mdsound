package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Rf5c68;


public class Rf5c68Inst extends Instrument.BaseInstrument {

    @Override
    public String getName() {
        return "RF5C68";
    }

    @Override
    public String getShortName() {
        return "RF68";
    }

    @Override
    public void reset(byte chipId) {
        visVolume = new int[][][] {
                {new int[] {0, 0}},
                {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipId, int clock) {
        return start(chipId, clock, 0);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        return device_start_rf5c68(chipId, clockValue);
    }

    @Override
    public void stop(byte chipId) {
        device_stop_rf5c68(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        rf5c68_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        rf5c68_w(chipId, adr, (byte) data);
        return 0;
    }

    private static final int MAX_CHIPS = 0x02;
    public Rf5c68[] rf5C68Data = new Rf5c68[] {new Rf5c68(), new Rf5c68()};

    private void rf5c68_update(byte chipId, int[][] outputs, int samples) {
        Rf5c68 chip = rf5C68Data[chipId];
        chip.update(outputs, samples);
    }

    private int device_start_rf5c68(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        Rf5c68 chip = rf5C68Data[chipId];
        return chip.start(clock);
    }

    private void device_stop_rf5c68(byte chipId) {
        Rf5c68 chip = rf5C68Data[chipId];
        chip.stop();
    }

    private void device_reset_rf5c68(byte chipId) {
        Rf5c68 chip = rf5C68Data[chipId];
        chip.reset();
    }

    public void rf5c68_w(byte chipId, int offset, byte data) {
        Rf5c68 chip = rf5C68Data[chipId];
        chip.write(offset, data);
    }

    private byte rf5c68_mem_r(byte chipId, int offset) {
        Rf5c68 chip = rf5C68Data[chipId];
        return chip.readMemory(offset);
    }

    public void rf5c68_mem_w(byte chipId, int offset, byte data) {
        Rf5c68 chip = rf5C68Data[chipId];
        chip.writeMemory(offset, data);
    }

    private void rf5c68_write_ram(byte chipId, int dataStart, int dataLength, byte[] ramData) {
        Rf5c68 chip = rf5C68Data[chipId];
        chip.writeRam(dataStart, dataLength, ramData);
    }

    public void rf5c68_write_ram2(byte chipId, int dataStart, int dataLength, byte[] ramData, int srcStartAdr) {
        Rf5c68 chip = rf5C68Data[chipId];
        chip.writeRam2(dataStart, dataLength, ramData, srcStartAdr);
    }

    private void rf5c68_set_mute_mask(byte chipId, int muteMask) {
        Rf5c68 chip = rf5C68Data[chipId];
        chip.setMuteMask(muteMask);
    }

    /*
     * Generic get_info
     */
    /*DEVICE_GET_INFO( Rf5c68Inst ) {
            case DEVINFO_STR_NAME:       strcpy(info.s, "RF5C68");      break;
            case DEVINFO_STR_FAMILY:     strcpy(info.s, "Ricoh PCM");     break;
            case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
            case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
        }
    }*/


    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0xB0, 1d);
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        result.put("rf5c68", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        return result;
    }
}
