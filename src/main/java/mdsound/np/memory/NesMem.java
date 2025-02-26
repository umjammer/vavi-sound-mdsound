/*
 * NSFPlay/NFSPlug project by Brezza.
 *
 * https://web.archive.org/web/20160301201825/http://www.pokipoki.org/dsa/
 */

package mdsound.np.memory;

import mdsound.np.Device;


public class NesMem implements Device {

    protected byte[] image = new byte[0x1_0000];
    protected boolean fdsEnabled;

    public NesMem() {
        fdsEnabled = true;
    }

    @Override
    public void reset() {
        for (int i = 0; i < 0x800; i++) image[i] = 0;
        //memset (image + 0x6000, 0, 0x2000); // I know this but I don't initialize it.
    }

    public boolean setImage(byte[] data, int offset, int size) {
        for (int i = 0; i < 0x1_0000; i++) image[i] = 0;
        //memset(image, 0, 0x1_0000);
        if (offset + size < 0x1_0000) {
            if (size >= 0) System.arraycopy(data, 0, image, offset, size);
        } else {
            if (0x1_0000 - offset >= 0) System.arraycopy(data, 0, image, offset, 0x1_0000 - offset);
        }
        return true;
    }

    @Override
    public boolean write(int adr, int val, int id) {
        if (0x0000 <= adr && adr < 0x2000) {
            image[adr & 0x7ff] = (byte) val;
            return true;
        }
        if (0x6000 <= adr && adr < 0x8000) {
            image[adr] = (byte) val;
            return true;
        }
        if (0x4100 <= adr && adr < 0x4110) {
            image[adr] = (byte) val;
            return true;
        }
        if (fdsEnabled && 0x8000 <= adr && adr < 0xe000) {
            image[adr] = (byte) val;
        }
        return false;
    }

    @Override
    public boolean read(int adr, /* ref */ int[] val, int id) {
        if (0x0000 <= adr && adr < 0x2000) {
            val[0] = image[adr & 0x7ff] & 0xff;
            return true;
        }
        if (0x4100 <= adr && adr < 0x4110) {
            val[0] = image[adr] & 0xff;
            return true;
        }
        if (0x6000 <= adr && adr < 0x1_0000) {
            val[0] = image[adr] & 0xff;
            return true;
        }
        val[0] = 0;
        return false;
    }

    public void setFDSMode(boolean t) {
        fdsEnabled = t;
    }

    @Override
    public void setOption(int id, int val) {
        throw new UnsupportedOperationException();
    }
}