package mdsound.np.memory;

import mdsound.np.IDevice;

    public class nes_bank implements IDevice
    {
        //# include <stdio.h>
        //# include <stdlib.h>
        //# include "nes_bank.h"
        /**
         * 4KB*16バンクのバンク空間
         **/
        //      class NES_BANK extends public IDevice
        //{
        protected byte[] null_bank = new byte[0x1000];
        protected int[] bank = new int[256]; //ptr
        protected byte[] image;//ptr
        protected int[] bankswitch = new int[16];
        protected int[] bankdefault = new int[16];
        protected boolean fds_enable;
        protected int bankmax;

        //  public:
        //NES_BANK();
        //  ~NES_BANK();
        //  void Reset();
        //  boolean Read((int)32 adr, (int)32 & val, (int)32 id = 0);
        //  boolean Write((int)32 adr, (int)32 val, (int)32 id = 0);
        //  void SetBankDefault((int)8 bank, int value);
        //  boolean SetImage((int)8* data, (int)32 offset, (int)32 size);
        //  void SetFDSMode(bool);
        //};

        // this workaround solves a problem with mirrored FDS RAM writes
        // when the same bank is used twice; some NSF rips reuse bank 00
        // in "unused" banks that actually get written to.
        // it is preferred to fix the NSFs and leave this disabled.
        //#define FDS_MEMCPY 0

        // for detecting mirrored writes in FDS NSFs
        //#define DETECT_FDS_MIRROR 0

        //#if FDS_MEMCPY
        //    static (int)8* fds_image = NULL;
        //#endif

        //namespace xgm
        //{

        public nes_bank()
        {
            image = null;
            fds_enable = false;
        }

        protected void finalize()
        {
            //if (image!=0) delete[] image;

            //#if FDS_MEMCPY
            //        if (fds_image)
            //            delete[] fds_image;
            //#endif
        }

        public void SetBankDefault(byte bank, int value)
        {
            bankdefault[bank] = value;
        }

        public boolean SetImage(byte[] data, int offset, int size)
        {
            int i;

            // バンクスイッチの初期値は全て「バンク無効」
            for (i = 0; i < 16; i++)
                bankdefault[i] = -1; // -1 is special empty bank

            bankmax = (int)((((offset & 0xfff) + size) / 0x1000) + 1);
            if (bankmax > 256)
                return false;

            //if (image!=0)            delete[] image;
            image = new byte[0x1000 * bankmax];
            for (i = 0; i < image.length; i++) image[i] = 0;
            //memset(image, 0, 0x1000 * bankmax);
            for (i = 0; i < size; i++) image[(offset & 0xfff) + i] = data[i];
            //memcpy(image + (offset & 0xfff), data, size);

            //#if FDS_MEMCPY
            //        if (fds_image)
            //            delete[] fds_image;
            //        fds_image = new (int)8[0x10000];
            //        memset(fds_image, 0, 0x10000);
            //        for (i = 0; i < 16; i++)
            //            bank[i] = fds_image + 0x1000 * i;
            //#else
            for (i = 0; i < bankmax; i++)
                bank[i] = 0x1000 * i;
            for (i = bankmax; i < 256; i++)
                bank[i] = -1;// null_bank;
            //#endif

            return true;
        }

        @Override public void Reset()
        {
            for (int i = 0; i < 0x1000; i++) null_bank[i] = 0;
            //memset(null_bank, 0, 0x1000);
            for (int i = 0; i < 16; i++)
            {
                bankswitch[i] = bankdefault[i];

                //#if FDS_MEMCPY
                //            bankswitch[i] = i;
                //            if (bankdefault[i] == -1 || bankdefault[i] >= bankmax)
                //                memset(bank[i], 0, 0x1000);
                //            else
                //                memcpy(bank[i], image + (bankdefault[i] * 0x1000), 0x1000);
                //#endif
            }
        }

        @Override public boolean Write(int adr, int val, int id)
        {
            //#if FDS_MEMCPY
            //        if (!fds_enable)
            //#endif
            if (0x5ff8 <= adr && adr < 0x6000)
            {
                bankswitch[(adr & 7) + 8] = (int)(val & 0xff);
                return true;
            }

            if (fds_enable)
            {
                //#if FDS_MEMCPY
                //            if (0x5ff6 <= adr && adr < 0x6000)
                //            {
                //                int b = adr - 0x5ff0;
                //                if (int(val) >= bankmax)
                //                    memset(bank[b], 0, 0x1000);
                //                else
                //                    memcpy(bank[b], image + (val * 0x1000), 0x1000);
                //                return true;
                //            }
                //#else
                if (0x5ff6 <= adr && adr < 0x5ff8)
                {
                    bankswitch[adr & 7] = (int)(val & 0xff);
                    return true;
                }
                //#endif

                if (0 <= bankswitch[adr >> 12] && 0x6000 <= adr && adr < 0xe000)
                {
                    // for detecting FDS ROMs with improper mirrored writes
                    //#if DETECT_FDS_MIRROR
                    //                for (int i = 0; i < 14; ++i)
                    //                {
                    //                    int b = adr >> 12;
                    //                    if (i != b && bankswitch[i] == bankswitch[b])
                    //                    {
                    //                        DEBUG_OUT("[%04X] write mirrored to [%04X] = %02X\n",
                    //                          adr, (i * 0x1000) | (adr & 0x0fff), val);
                    //                    }
                    //                }
                    //#endif

                    image[bank[bankswitch[adr >> 12]] + (adr & 0x0fff)] = (byte)val;
                    return true;
                }
            }

            return false;
        }

        @Override public boolean Read(int adr, int val, int id)
        {
            if (0x5ff8 <= adr && adr < 0x5fff)
            {
                val = (int)bankswitch[(adr & 7) + 8];
                return true;
            }

            if (0 <= bankswitch[adr >> 12] && 0x8000 <= adr && adr < 0x10000)
            {
                val = image[bank[bankswitch[adr >> 12]] + (adr & 0xfff)];
                return true;
            }

            if (fds_enable)
            {
                if (0x5ff6 <= adr && adr < 0x5ff8)
                {
                    val = (int)bankswitch[adr & 7];
                    return true;
                }

                if (0 <= bankswitch[adr >> 12] && 0x6000 <= adr && adr < 0x8000)
                {
                    val = image[bank[bankswitch[adr >> 12]] + (adr & 0xfff)];
                    return true;
                }
            }

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

