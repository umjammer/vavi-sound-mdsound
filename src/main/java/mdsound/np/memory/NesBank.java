package mdsound.np.memory;

import java.util.Arrays;

import mdsound.np.Device;


/**
 * 4KB * 16 バンクのバンク空間
 */
public class NesBank implements Device {

    protected byte[] nullBank = new byte[0x1000];
    protected int[] bank = new int[256];
    protected byte[] image;
    protected int[] bankSwitch = new int[16];
    protected int[] bankDefault = new int[16];
    protected boolean fdsEnable;
    protected int bankMax;

    public NesBank() {
        image = null;
        fdsEnable = false;
    }

    public void setBankDefault(byte bank, int value) {
        bankDefault[bank] = value;
    }

    public boolean setImage(byte[] data, int offset, int size) {
        // バンクスイッチの初期値は全て「バンク無効」
        for (int i = 0; i < 16; i++)
            bankDefault[i] = -1; // -1 is special empty bank

        bankMax = (((offset & 0xfff) + size) / 0x1000) + 1;
        if (bankMax > 256)
            return false;

        image = new byte[0x1000 * bankMax];
        Arrays.fill(image, (byte) 0);
        if (size >= 0) System.arraycopy(data, 0, image, (offset & 0xfff) + 0, size);

        for (int i = 0; i < bankMax; i++)
            bank[i] = 0x1000 * i;
        for (int i = bankMax; i < 256; i++)
            bank[i] = -1; // null_bank;

        return true;
    }

    @Override
    public void reset() {
        for (int i = 0; i < 0x1000; i++) nullBank[i] = 0;
        System.arraycopy(bankDefault, 0, bankSwitch, 0, 16);
    }

    @Override
    public boolean write(int adr, int val, int id) {
        if (0x5ff8 <= adr && adr < 0x6000) {
            bankSwitch[(adr & 7) + 8] = val & 0xff;
            return true;
        }

        if (fdsEnable) {
            if (0x5ff6 <= adr && adr < 0x5ff8) {
                bankSwitch[adr & 7] = val & 0xff;
                return true;
            }

            if (0 <= bankSwitch[adr >> 12] && 0x6000 <= adr && adr < 0xe000) {
                // for detecting FDS ROMs with improper mirrored writes

                image[bank[bankSwitch[adr >> 12]] + (adr & 0x0fff)] = (byte) val;
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean read(int adr, int[] val, int id) {
        if (0x5ff8 <= adr && adr < 0x5fff) {
            val[0] = bankSwitch[(adr & 7) + 8];
            return true;
        }

        if (0 <= bankSwitch[adr >> 12] && 0x8000 <= adr && adr < 0x10000) {
            val[0] = image[bank[bankSwitch[adr >> 12]] + (adr & 0xfff)];
            return true;
        }

        if (fdsEnable) {
            if (0x5ff6 <= adr && adr < 0x5ff8) {
                val[0] = bankSwitch[adr & 7];
                return true;
            }

            if (0 <= bankSwitch[adr >> 12] && 0x6000 <= adr && adr < 0x8000) {
                val[0] = image[bank[bankSwitch[adr >> 12]] + (adr & 0xfff)];
                return true;
            }
        }

        return false;
    }

    public void setFDSMode(boolean t) {
        fdsEnable = t;
    }

    @Override
    public void setOption(int id, int val) {
        throw new UnsupportedOperationException();
    }
}

