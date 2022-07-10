package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.MultiPCM;


// TODO check SHIFT in all classes
public class MultiPcmInst extends Instrument.BaseInstrument {

    @Override
    public void reset(byte chipId) {
        device_reset_multipcm(chipId);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipId, int clock) {
        return device_start_multipcm(chipId, clock);
    }

    @Override
    public int start(byte chipId, int samplingrate, int clockValue, Object... option) {
        return device_start_multipcm(chipId, clockValue);
    }

    @Override
    public void stop(byte chipId) {
        device_stop_multipcm(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        MultiPCM_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private static final int MAX_CHIPS = 0x02;
    private MultiPCM[] chips = new MultiPCM[] {new MultiPCM(), new MultiPCM()};

    @Override
    public String getName() {
        return "Multi PCM";
    }

    @Override
    public String getShortName() {
        return "mPCM";
    }

    public void MultiPCM_update(byte chipId, int[][] outputs, int samples) {
        MultiPCM chip = chips[chipId];
        chip.update(outputs, samples);
    }

    public MultiPCM multipcm_r(int chipId) {
        return chips[chipId];
    }

    public int device_start_multipcm(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        MultiPCM chip = chips[chipId];
        return chip.start(clock);
    }

    public void device_stop_multipcm(byte chipId) {
        MultiPCM chip = chips[chipId];
        chip.stop();
    }

    public void device_reset_multipcm(byte chipId) {
        MultiPCM chip = chips[chipId];
        chip.reset();
    }

    private void multipcm_w(byte chipId, int offset, byte data) {
        MultiPCM chip = chips[chipId];
        chip.write(offset, data);
    }

    /* MAME/M1 access functions */
    public void multipcm_set_bank(byte chipId, int leftoffs, int rightoffs) {
        MultiPCM chip = chips[chipId];
        chip.setBank(leftoffs, rightoffs);
    }

    public void multipcm_bank_write(byte chipId, byte offset, int data) {
        MultiPCM chip = chips[chipId];
        chip.writeBank(offset, data);
    }

    public void multipcm_write_rom(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        MultiPCM chip = chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void multipcm_write_rom2(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        MultiPCM chip = chips[chipId];
        chip.writeRom2(romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    public void multipcm_set_mute_mask(byte chipId, int muteMask) {
        MultiPCM chip = chips[chipId];
        chip.setMuteMask(muteMask);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        multipcm_w(chipId, adr, (byte) data);
        return 0;
    }

    /**
     * Generic get_info
     */
    /*DEVICE_GET_INFO( MultiPcmInst ) {
            case DEVINFO_STR_NAME:       strcpy(info.s, "Sega/Yamaha 315-5560");  break;
            case DEVINFO_STR_FAMILY:     strcpy(info.s, "Sega custom");     break;
            case DEVINFO_STR_VERSION:     strcpy(info.s, "2.0");       break;
            case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
        }
    }*/

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x40, 4d);
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        result.put("multiPCM", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        return result;
    }
}
