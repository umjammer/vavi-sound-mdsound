package mdsound.instrument;


import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.SegaPcm;


public class SegaPcmInst extends Instrument.BaseInstrument {

    public SegaPcmInst() {
        // 0..Main
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(int chipId, int clock) {
        int intFBank = 0;
        return device_start_segapcm(chipId, clock, intFBank);
    }

    @Override
    public int start(int chipId, int samplingRate, int clockValue, Object... option) {
        return device_start_segapcm(chipId, clockValue, (int) option[0]);
    }

    @Override
    public void stop(int chipId) {
        device_stop_segapcm(chipId);
    }

    @Override
    public void reset(int chipId) {
        device_reset_segapcm(chipId);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        SEGAPCM_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private static final int MAX_CHIPS = 0x02;
    public SegaPcm[] SPCMData = new SegaPcm[] {new SegaPcm(), new SegaPcm()};

    @Override
    public String getName() {
        return "SEGA PCM";
    }

    @Override
    public String getShortName() {
        return "SPCM";
    }

    public void SEGAPCM_update(int chipId, int[][] outputs, int samples) {
        SegaPcm spcm = SPCMData[chipId];
        spcm.update(outputs, samples);
    }

    public int device_start_segapcm(int chipId, int clock, int intf_bank) {
        if (chipId >= MAX_CHIPS)
            return 0;

        SegaPcm spcm = SPCMData[chipId];
        return spcm.start(clock, intf_bank);
    }

    public void device_stop_segapcm(int chipId) {
        SegaPcm spcm = SPCMData[chipId];
        spcm.stop();
    }

    public void device_reset_segapcm(int chipId) {
        SegaPcm spcm = SPCMData[chipId];
        spcm.reset();
    }

    private void sega_pcm_w(int chipId, int offset, int data) {
        SegaPcm spcm = SPCMData[chipId];
        spcm.write(offset, data);
    }

    public int sega_pcm_r(int chipId, int offset) {
        SegaPcm spcm = SPCMData[chipId];
        return spcm.read(offset);
    }

    public void sega_pcm_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        SegaPcm spcm = SPCMData[chipId];
        spcm.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void sega_pcm_write_rom2(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        SegaPcm spcm = SPCMData[chipId];
        spcm.writeRom2(romSize, dataStart, dataLength, romData, srcStartAdr);
    }

    public void segapcm_set_mute_mask(int chipId, int muteMask) {
        SegaPcm spcm = SPCMData[chipId];
        spcm.setMuteMask(muteMask);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        sega_pcm_w(chipId, adr, data);
        return 0;
    }

//    /*
//     * Generic get_info
//     */
//    DEVICE_GET_INFO( SegaPcmInst ) {
//            case DEVINFO_STR_NAME:       strcpy(info.s, "Sega PCM");     break;
//            case DEVINFO_STR_FAMILY:     strcpy(info.s, "Sega custom");     break;
//            case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
//            case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
//    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x180, 1d);
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
