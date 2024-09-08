package mdsound.instrument;

import java.util.ArrayList;
import java.util.List;

import mdsound.Instrument;
import mdsound.chips.Sn76496;


public class Sn76496Inst extends Instrument.BaseInstrument {

    @Override
    public String getName() {
        return "Sn76496";
    }

    @Override
    public String getShortName() {
        return "DCSGmame";
    }

    private final List<Sn76496> chips = new ArrayList<>();

    @Override
    public void reset(int chipId) {
        if (chips.get(chipId) == null) return;
        chips.get(chipId).reset();
    }

    @Override
    public int start(int chipId, int clock) {
        Sn76496 chip = new Sn76496();
        int i = (int) chip.start(3579545, 0, 0, 0, 0, 0, 0);
        chip.limitFreq(3579545 & 0x3fff_ffff, 0, clock);

        while (chipId >= chips.size()) chips.add(null);
        chips.set(chipId, chip);

        return i;
    }

    /**
     * @param clock sampleRate
     * @param clockValue masterClock
     * @param option int[4]
     */
    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        int stereo = 0;
        int negate = 0;
        int freq0 = 0;
        int divider = 0;
        int noisetaps = 9;
        int shiftreg = 16;

        if (option != null && option.length == 4) {
            noisetaps = (int) option[0] + (int) option[1] * 0x100;
            shiftreg = (byte) option[2];

            freq0 = ((int) option[3] & 0x1) != 0 ? 1 : 0;
            negate = ((int) option[3] & 0x2) != 0 ? 1 : 0;
            stereo = ((int) option[3] & 0x4) != 0 ? 1 : 0;
            divider = ((int) option[3] & 0x8) != 0 ? 1 : 0;

        }

        if (chipId == 0) {
            Sn76496.lastChipInit = null;
        }

        Sn76496 chip = new Sn76496();
        int i = chip.start(clockValue, shiftreg, noisetaps, negate, stereo, divider, freq0);
        chip.limitFreq(clockValue & 0x3fff_ffff, 0, clock);

        while (chipId >= chips.size()) chips.add(null);
        chips.set(chipId, chip);

        return i;
    }

    @Override
    public void stop(int chipId) {
        if (chips.get(chipId) == null) return;
        chips.get(chipId).stop();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        if (chips.get(chipId) == null) return;
        chips.get(chipId).update(outputs, samples);
    }

    /**
     * @param adr 未使用
     */
    @Override
    public int write(int chipId, int port, int adr, int data) {
        if (chips.get(chipId) == null) return 0;
        chips.get(chipId).writeReg(adr, data);
        return 0;
    }

    /**
     * @param adr 未使用
     */
    public int writeGGStereo(int chipId, int port, int adr, int data) {
        if (chips.get(chipId) == null) return 0;
        chips.get(chipId).writeStereo(adr, data);
        return 0;
    }

//    /*
//     * Generic get_info
//     */
//    DEVICE_GET_INFO( sn76496 ) {
//            case DEVINFO_STR_NAME: strcpy(info.s, "Sn76496Inst"); break;
//            case DEVINFO_STR_FAMILY: strcpy(info.s, "TI Psg"); break;
//            case DEVINFO_STR_VERSION: strcpy(info.s, "1.1"); break;
//            case DEVINFO_STR_CREDITS: strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
//        }
//    }
}
