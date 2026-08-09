/*
 * https://github.com/kuma4649/MDSound
 */

package mdsound;


class ChipVolume {

    private static int getChipVolume(VGMX_CHP_EXTRA16 tempCX, int chipId, int chipNum, int chipCnt, int sn76496VGMHeaderClock, String strSystemNameE, boolean doubleSSGVol) {
        // chipId: ID of Chip
        //  Bit 7 - Is Paired Chip
        // chipNum: chips number (0 - first chips, 1 - second chips)
        // chipCnt: chips volume divider (number of used chips)
        int[] CHIP_VOLS = { // CHIP_COUNT
                0x80, 0x200 /* 0x155 */, 0x100, 0x100, 0x180, 0xB0, 0x100, 0x80, // 00-07
                0x80, 0x100, 0x100, 0x100, 0x100, 0x100, 0x100, 0x98,            // 08-0F
                0x80, 0xE0 /* 0xCD */, 0x100, 0xC0, 0x100, 0x40, 0x11E, 0x1C0,   // 10-17
                0x100 /* 110 */, 0xA0, 0x100, 0x100, 0x100, 0xB3, 0x100, 0x100,  // 18-1F
                0x20, 0x100, 0x100, 0x100, 0x40, 0x20, 0x100, 0x40,              // 20-27
                0x280
        };
        int volume;
        int curChp;
        //VGMX_CHP_EXTRA16 tempCX;
        VGMX_CHIP_DATA16 tempCD;

        volume = CHIP_VOLS[chipId & 0x7F];
        switch (chipId & 0xff) {
            case 0x00: // Sn76496
                // if T6W28, set volume Divider to 01
                if ((sn76496VGMHeaderClock & 0x8000_0000) != 0) {
                    // The T6W28 consists of 2 "half" chips.
                    chipNum = 0x01;
                    chipCnt = 0x01;
                }
                break;
            case 0x18: // OkiM6295
                // CP System 1 patch
                if ((strSystemNameE != null && !strSystemNameE.isEmpty()) && strSystemNameE.indexOf("CP") == 0)
                    volume = 110;
                break;
            case 0x86: // Ym2203's AY
                volume /= 2;
                break;
            case 0x87: // Ym2608's AY
                // The Ym2608 outputs twice as loud as the Ym2203 here.
                //volume *= 1;
                break;
            case 0x88: // Ym2610's AY
                //volume *= 1;
                break;
        }
        if (chipCnt > 1)
            volume /= chipCnt;

        for (curChp = 0x00; curChp < tempCX.chipCnt; curChp++) {
            tempCD = tempCX.ccData[curChp];
            if (tempCD.type == chipId && (tempCD.flags & 0x01) == chipNum) {
                // Bit 15 - absolute/relative volume
                // 0 - absolute
                // 1 - relative (0x0100 = 1.0, 0x80 = 0.5, etc.)
                if ((tempCD.data & 0x8000) != 0)
                    volume = (volume * (tempCD.data & 0x7fff) + 0x80) >> 8;
                else {
                    volume = tempCD.data;
                    if ((chipId & 0x80) != 0 && doubleSSGVol)
                        volume *= 2;
                }
                break;
            }
        }

        return volume;
    }

    static class VGMX_CHIP_DATA16 {
        int type;
        int flags;
        int data;
    }

    static class VGMX_CHP_EXTRA16 {
        int chipCnt;
        VGMX_CHIP_DATA16[] ccData;
    }
}
