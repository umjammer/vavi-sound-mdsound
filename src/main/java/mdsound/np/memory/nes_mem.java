package mdsound.np.memory;
import mdsound.np.IDevice;

    public class nes_mem implements IDevice
    {
        //# include <assert.h>
        //# include "nes_mem.h"
        protected byte[] image = new byte[0x10000];
        protected boolean fds_enable;

        //  public:
        //NES_MEM();
        //  ~NES_MEM();
        //  void Reset();
        //  boolean Read((int)32 adr, (int)32 & val, (int)32 id = 0);
        //  boolean Write((int)32 adr, (int)32 val, (int)32 id = 0);
        //  boolean SetImage((int)8* data, (int)32 offset, (int)32 size);
        //  void SetFDSMode(bool);

        //namespace xgm
        //    {
        public nes_mem()
        {
            fds_enable = true;
        }

        @Override public void Reset()
        {
            for (int i = 0; i < 0x800; i++) image[i] = 0;
            //memset(image, 0, 0x800);
            //memset (image + 0x6000, 0, 0x2000); // 分かっててあえて初期化してません。
        }

        public boolean SetImage(byte[] data, int offset, int size)
        {
            for (int i = 0; i < 0x10000; i++) image[i] = 0;
            //memset(image, 0, 0x10000);
            if (offset + size < 0x10000)
            {
                for (int i = 0; i < size; i++) image[offset + i] = data[i];
                //memcpy(image + offset, data, size);
            }
            else
            {
                for (int i = 0; i < 0x10000 - offset; i++) image[offset + i] = data[i];
                //memcpy(image + offset, data, 0x10000 - offset);
            }
            return true;
        }

        @Override public boolean Write(int adr, int val, int id)
        {
            if (0x0000 <= adr && adr < 0x2000)
            {
                image[adr & 0x7ff] = (byte)val;
                return true;
            }
            if (0x6000 <= adr && adr < 0x8000)
            {
                image[adr] = (byte)val;
                return true;
            }
            if (0x4100 <= adr && adr < 0x4110)
            {
                image[adr] = (byte)val;
                return true;
            }
            if (fds_enable && 0x8000 <= adr && adr < 0xe000)
            {
                image[adr] = (byte)val;
            }
            return false;
        }

        @Override public boolean Read(int adr, int val, int id)
        {
            if (0x0000 <= adr && adr < 0x2000)
            {
                val = image[adr & 0x7ff];
                return true;
            }
            if (0x4100 <= adr && adr < 0x4110)
            {
                val = image[adr];
                return true;
            }
            if (0x6000 <= adr && adr < 0x10000)
            {
                val = image[adr];
                return true;
            }
            val = 0;
            return false;
        }

        public void SetFDSMode(boolean t)
        {
            fds_enable = t;
        }

        @Override public void SetOption(int id, int val)
        {
            throw new UnsupportedOperationException();
        }
    }