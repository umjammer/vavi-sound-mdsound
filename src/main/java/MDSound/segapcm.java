﻿package MDSound;

    public class segapcm extends Instrument
    {

        public segapcm()
        {
            visVolume = new int[][][] {
                new int[][] { new int[] { 0, 0 } }
                , new int[][] { new int[] { 0, 0 } }
            };
            //0..Main
        }

        @Override public int Start(byte ChipID, int clock)
        {
            int intf_bank = 0;
            return (int)device_start_segapcm(ChipID, (int)clock, intf_bank);
        }

        @Override public int Start(byte ChipID, int samplingrate, int clock, Object... option)
        {
            return (int)device_start_segapcm(ChipID, (int)clock, (int)option[0]);
        }

        @Override public void Stop(byte ChipID)
        {
            device_stop_segapcm(ChipID);
        }

        @Override public void Reset(byte ChipID)
        {
            device_reset_segapcm(ChipID);
        }

        @Override public void Update(byte ChipID, int[][] outputs, int samples)
        {
            SEGAPCM_update(ChipID, outputs, samples);

            visVolume[ChipID][0][0] = outputs[0][0];
            visVolume[ChipID][0][1] = outputs[1][0];
        }

        public final int BANK_256 = (11);
        public final int BANK_512 = (12);
        public final int BANK_12M = (13);
        public final int BANK_MASK7 = (0x70 << 16);
        public final int BANK_MASKF = (0xf0 << 16);
        public final int BANK_MASKF8 = (0xf8 << 16);

        public class sega_pcm_interface
        {
            public int bank;
        }

        /*WRITE8_DEVICE_HANDLER( sega_pcm_w );
        READ8_DEVICE_HANDLER( sega_pcm_r );

        DEVICE_GET_INFO( segapcm );
        // #define SOUND_SEGAPCM DEVICE_GET_INFO_NAME( segapcm )*/

        /*********************************************************/
        /*    SEGA 16ch 8bit PCM                                 */
        /*********************************************************/

        //# include "mamedef.h"
        //# include <stdlib.h>
        //# include <string.h>	// for memset
        //# include <stdio.h>
        //#include "sndintrf.h"
        //#include "streams.h"
        //# include "segapcm.h"


        public class segapcm_state
        {
            public byte[] ram;
            public int ptrRam=0;
            public byte[] low = new byte[16];
            public int ROMSize;
            public byte[] rom;
            public int ptrRom=0;
            //# ifdef _DEBUG
            //          (int)8* romusage;
            //#endif
            public int bankshift;
            public int bankmask;
            public int rgnmask;
            public sega_pcm_interface intf=new sega_pcm_interface();
            public byte[] Muted = new byte[16];
            //sound_stream * stream;
        };

        //#define MAX_CHIPS	0x02
        public segapcm_state[] SPCMData = new segapcm_state[] { new segapcm_state(), new segapcm_state() };// [MAX_CHIPS];

        @Override public String getName() { return "SEGA PCM"; } 
        @Override public String getShortName() { return "SPCM"; } 

        //# ifndef _DEBUG
        //byte SegaPCM_NewCore = 0x00;
        //#else
        ////(int)8 SegaPCM_NewCore = 0x01;
        //static void sega_pcm_fwrite_romusage((int)8 ChipID);
        //#endif

        /*INLINE segapcm_state *get_safe_token(final device_config *device)
        {
            assert(device != NULL);
            assert(device.token != NULL);
            assert(device.type == SOUND);
            assert(sound_get_type(device) == SOUND_SEGAPCM);
            return (segapcm_state *)device.token;
        }*/

        //static STREAM_UPDATE( SEGAPCM_update )
        public void SEGAPCM_update(byte ChipID, int[][] outputs, int samples)
        {
            //segapcm_state *spcm = (segapcm_state *)param;
            segapcm_state spcm = SPCMData[ChipID];
            int rgnmask = spcm.rgnmask;
            int ch;

            /* clear the buffers */
            //memset(outputs[0], 0, samples * sizeof(stream_sample_t));
            //memset(outputs[1], 0, samples * sizeof(stream_sample_t));
            //for (int i = 0; i < outputs[0].length; i++)
            for (int i = 0; i < samples; i++)
            {
                outputs[0][i] = 0;
                outputs[1][i] = 0;
            }

            // reg      function
            // ------------------------------------------------
            // 0x00     ?
            // 0x01     ?
            // 0x02     volume left
            // 0x03     volume right
            // 0x04     loop address (08-15)
            // 0x05     loop address (16-23)
            // 0x06     end address
            // 0x07     address delta
            // 0x80     ?
            // 0x81     ?
            // 0x82     ?
            // 0x83     ?
            // 0x84     current address (08-15), 00-07 is internal?
            // 0x85     current address (16-23)
            // 0x86     bit 0: channel disable?
            //          bit 1: loop disable
            //          other bits: bank
            // 0x87     ?

            /* loop over channels */
            for (ch = 0; ch < 16; ch++)
            {
                ////#if 0
                ////if (! SegaPCM_NewCore)
                ////{
                //		/* only process active channels */
                //		if (!(spcm.ram[0x86+8*ch] & 1) && ! spcm.Muted[ch])
                //		{
                //			(int)8 *base = spcm.ram+8*ch;
                //			(int)8 flags = base[0x86];
                //			final (int)8 *rom = spcm.rom + ((flags & spcm.bankmask) << spcm.bankshift);
                //# ifdef _DEBUG
                //			(int)8 *romusage = spcm.romusage + ((flags & spcm.bankmask) << spcm.bankshift);
                //#endif
                //                (int)32 addr = (base[5] << 16) | (base[4] << 8) | spcm.low[ch];
                //                int loop = (base[0x85] << 8) | base[0x84];
                //                (int)8 end = base[6] + 1;
                //                (int)8 delta = base[7];
                //                (int)8 voll = base[2] & 0x7F;
                //                (int)8 volr = base[3] & 0x7F;
                //                int i;

                //                /* loop over samples on this channel */
                //                for (i = 0; i < samples; i++)
                //                {
                //                    INT8 v = 0;

                //                    /* handle looping if we've hit the end */
                //                    if ((addr >> 16) == end)
                //                    {
                //                        if (!(flags & 2))
                //                            addr = loop << 8;
                //                        else
                //                        {
                //                            flags |= 1;
                //                            break;
                //                        }
                //                    }

                //                    /* fetch the sample */
                //                    v = rom[(addr >> 8) & rgnmask] - 0x80;
                //# ifdef _DEBUG
                //                    if ((romusage[(addr >> 8) & rgnmask] & 0x03) == 0x02 && (voll || volr))
                //                        printf("Access to empty ROM section! (0x%06lX)\n",
                //                                ((flags & spcm.bankmask) << spcm.bankshift) + (addr >> 8) & rgnmask);
                //                    romusage[(addr >> 8) & rgnmask] |= 0x01;
                //#endif

                //                    /* apply panning and advance */
                //                    outputs[0][i] += v * voll;
                //                    outputs[1][i] += v * volr;
                //                    addr += delta;
                //                }

                //                /* store back the updated address and info */
                //                base[0x86] = flags;
                //                base[4] = addr >> 8;
                //                base[5] = addr >> 16;
                //                spcm.low[ch] = flags & 1 ? 0 : addr;
                //            }
                //            //}
                //            //else
                //            //{
                //#else

                //byte* regs = spcm.ram + 8 * ch;
                int ptrRegs = spcm.ptrRam + 8 * ch;

                /* only process active channels */
                //if ((regs[0x86] & 1) == 0 && spcm.Muted[ch] == 0)
                if ((spcm.ram[ptrRegs + 0x86] & 1) == 0 && spcm.Muted[ch] == 0)
                {
                    //final byte* rom = spcm.rom + ((regs[0x86] & spcm.bankmask) << spcm.bankshift);
                    int ptrRom = spcm.ptrRom + ((spcm.ram[ptrRegs + 0x86] & spcm.bankmask) << spcm.bankshift);
                    //System.err.printf("spcm.ram[ptrRegs + 0x86]:{0:x}", spcm.ram[ptrRegs + 0x86]);
                    //System.err.printf("spcm.bankmask:{0:x}", spcm.bankmask);
                    //System.err.printf("spcm.bankshift:{0:x}", spcm.bankshift);
                    //# ifdef _DEBUG
                    //                (int)8* romusage = spcm.romusage + ((regs[0x86] & spcm.bankmask) << spcm.bankshift);
                    //#endif
                    int addr = (int)((spcm.ram[ptrRegs + 0x85] << 16) | (spcm.ram[ptrRegs + 0x84] << 8) | spcm.low[ch]);
                    int loop = (int)((spcm.ram[ptrRegs + 0x05] << 16) | (spcm.ram[ptrRegs + 0x04] << 8));
                    byte end = (byte)(spcm.ram[ptrRegs + 6] + 1);
                    int i;

                    /* loop over samples on this channel */
                    for (i = 0; i < samples; i++)
                    {
                        byte v = 0;

                        /* handle looping if we've hit the end */
                        if ((addr >> 16) == end)
                        {
                            if ((spcm.ram[ptrRegs + 0x86] & 2) != 0)
                            {
                                spcm.ram[ptrRegs + 0x86] |= 1;
                                break;
                            }
                            else addr = loop;
                        }

                        /* fetch the sample */
                        //v = (byte)(rom[(addr >> 8) & rgnmask] - 0x80);
                        if (ptrRom + ((addr >> 8) & rgnmask) < spcm.rom.length)
                        {
                            v = (byte)(spcm.rom[ptrRom + ((addr >> 8) & rgnmask)] - 0x80);
                        }
                        //# ifdef _DEBUG
                        //                    if ((romusage[(addr >> 8) & rgnmask] & 0x03) == 0x02 && (regs[2] || regs[3]))
                        //                        printf("Access to empty ROM section! (0x%06lX)\n",
                        //                               ((regs[0x86] & spcm.bankmask) << spcm.bankshift) + (addr >> 8) & rgnmask);
                        //                    romusage[(addr >> 8) & rgnmask] |= 0x01;
                        //#endif

                        /* apply panning and advance */
                        // fixed Bitmask for volume multiplication, thanks to ctr -Valley Bell
                        outputs[0][i] += v * (spcm.ram[ptrRegs + 2] & 0x7F);
                        outputs[1][i] += v * (spcm.ram[ptrRegs + 3] & 0x7F);
                        addr = (addr + spcm.ram[ptrRegs + 7]) & 0xffffff;

                    }

                    /* store back the updated address */
                    spcm.ram[ptrRegs + 0x84] = (byte)(addr >> 8);
                    spcm.ram[ptrRegs + 0x85] = (byte)(addr >> 16);
                    spcm.low[ch] = (byte)(((spcm.ram[ptrRegs + 0x86] & 1) != 0) ? 0 : addr);
                }
                //}
                //#endif
            }
        }

        //static DEVICE_START( segapcm )
        public int device_start_segapcm(byte ChipID, int clock, int intf_bank)
        {
            int STD_ROM_SIZE = 0x80000;
            //final sega_pcm_interface *intf = (final sega_pcm_interface *)device.static_config;
            sega_pcm_interface intf;
            int mask, rom_mask, len;
            //segapcm_state *spcm = get_safe_token(device);
            segapcm_state spcm;

            if (ChipID >= 2)//MAX_CHIPS)
                return 0;

            spcm = SPCMData[ChipID];
            intf = spcm.intf;
            intf.bank = intf_bank;

            //spcm.rom = (final (int)8 *)device.region;
            //spcm.ram = auto_alloc_array(device.machine, (int)8, 0x800);
            spcm.ROMSize = STD_ROM_SIZE;
            spcm.rom = new byte[STD_ROM_SIZE];// malloc(STD_ROM_SIZE);
            spcm.ptrRom = 0;
            //# ifdef _DEBUG
            //        spcm.romusage = malloc(STD_ROM_SIZE);
            //#endif
            spcm.ram = new byte[0x800];// ((int)8*)malloc(0x800);

            //# ifndef _DEBUG
            //memset(spcm.rom, 0xFF, STD_ROM_SIZE);
            // filling 0xFF would actually be more true to the hardware,
            // (unused ROMs have all FFs)
            // but 0x80 is the effective 'zero' byte
            //memset(spcm.rom, 0x80, STD_ROM_SIZE);
            for (int i = 0; i < STD_ROM_SIZE; i++)
            {
                spcm.rom[i] = (byte) 0x80;
            }
            //#else
            // filling with FF makes it easier to find bugs in a .wav-log
            //        memset(spcm.rom, 0xFF, STD_ROM_SIZE);
            //        memset(spcm.romusage, 0x02, STD_ROM_SIZE);
            //#endif
            //memset(spcm.ram, 0xff, 0x800);	// RAM Clear is done at device_reset

            spcm.bankshift = (byte)(intf.bank);
            mask = intf.bank >> 16;
            if (mask == 0)
                mask = BANK_MASK7 >> 16;

            len = (int)STD_ROM_SIZE;
            spcm.rgnmask = len - 1;
            for (rom_mask = 1; rom_mask < len; rom_mask *= 2) ;
            rom_mask--;

            spcm.bankmask = mask & (rom_mask >> spcm.bankshift);

            //spcm.stream = stream_create(device, 0, 2, device.clock / 128, spcm, SEGAPCM_update);

            //state_save_register_device_item_array(device, 0, spcm.low);
            //state_save_register_device_item_pointer(device, 0, spcm.ram, 0x800);

            for (mask = 0; mask < 16; mask++)
                spcm.Muted[mask] = 0x00;

            return clock / 128;
        }

        //static DEVICE_STOP( segapcm )
        public void device_stop_segapcm(byte ChipID)
        {
            //segapcm_state *spcm = get_safe_token(device);
            segapcm_state spcm = SPCMData[ChipID];
            //free(spcm.rom); spcm.rom = NULL;
            spcm.rom = null;
            //# ifdef _DEBUG
            //sega_pcm_fwrite_romusage(ChipID);
            //        free(spcm.romusage);
            //#endif
            //free(spcm.ram);
            spcm.ram = null;
            return;
        }

        //static DEVICE_RESET( segapcm )
        public void device_reset_segapcm(byte ChipID)
        {
            //segapcm_state *spcm = get_safe_token(device);
            segapcm_state spcm = SPCMData[ChipID];

            //memset(spcm.ram, 0xFF, 0x800);
            for (int i = 0; i < 0x800; i++)
            {
                spcm.ram[i] = (byte) 0xff;
            }

            return;
        }


        //WRITE8_DEVICE_HANDLER( sega_pcm_w )
        private void sega_pcm_w(byte ChipID, int offset, byte data)
        {
            //segapcm_state *spcm = get_safe_token(device);
            segapcm_state spcm = SPCMData[ChipID];
            //stream_update(spcm.stream);

            spcm.ram[offset & 0x07ff] = data;
        }

        //READ8_DEVICE_HANDLER( sega_pcm_r )
        public byte sega_pcm_r(byte ChipID, int offset)
        {
            //segapcm_state *spcm = get_safe_token(device);
            segapcm_state spcm = SPCMData[ChipID];
            //stream_update(spcm.stream);
            return spcm.ram[offset & 0x07ff];
        }

        public void sega_pcm_write_rom(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData)
        {
            segapcm_state spcm = SPCMData[ChipID];

            if (spcm.ROMSize != ROMSize)
            {
                long mask, rom_mask;

                //spcm.rom = (byte)realloc(spcm.rom, ROMSize);
                //byte[] tmp = new byte[ROMSize];
                //for (int i = 0; i < spcm.ROMSize; i++)
                //{
                //    tmp[i] = spcm.rom[i];
                //}
                //spcm.rom = tmp;
                spcm.rom = new byte[ROMSize];
                //# ifdef _DEBUG
                //            spcm.romusage = ((int)8*)realloc(spcm.romusage, ROMSize);
                //#endif
                spcm.ROMSize = (int)ROMSize;
                //memset(spcm.rom, 0x80, ROMSize);
                for (int i = 0; i < ROMSize; i++)
                {
                    spcm.rom[i] = (byte) 0x80;
                }
                //# ifdef _DEBUG
                //            memset(spcm.romusage, 0x02, ROMSize);
                //#endif

                // recalculate bankmask
                mask = (long)(spcm.intf.bank >> 16);
                if (mask == 0)
                    mask = BANK_MASK7 >> 16;

                //spcm.rgnmask = ROMSize - 1;
                for (rom_mask = 1; rom_mask < (long)ROMSize; rom_mask *= 2) ;
                rom_mask--;
                spcm.rgnmask = (int)rom_mask;   // fix for ROMs with e.g 0x60000 bytes (stupid M1)

                spcm.bankmask = (int)(mask & (rom_mask >> spcm.bankshift));
            }
            if (DataStart > ROMSize)
                return;
            if (DataStart + DataLength > ROMSize)
                DataLength = ROMSize - DataStart;

            //memcpy(spcm.rom + DataStart, ROMData, DataLength);
            for (int i = 0; i < DataLength; i++)
            {
                spcm.rom[i + DataStart] = ROMData[i];
            }
            //# ifdef _DEBUG
            //        memset(spcm.romusage + DataStart, 0x00, DataLength);
            //#endif

            return;
        }

        public void sega_pcm_write_rom2(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData,int SrcStartAdr)
        {
            segapcm_state spcm = SPCMData[ChipID];

            if (spcm.ROMSize != ROMSize)
            {
                long mask, rom_mask;

                //spcm.rom = (byte)realloc(spcm.rom, ROMSize);
                //byte[] tmp = new byte[ROMSize];
                //for (int i = 0; i < spcm.ROMSize; i++)
                //{
                //    tmp[i] = spcm.rom[i];
                //}
                //spcm.rom = tmp;
                spcm.rom = new byte[ROMSize];
                //# ifdef _DEBUG
                //            spcm.romusage = ((int)8*)realloc(spcm.romusage, ROMSize);
                //#endif
                spcm.ROMSize = (int)ROMSize;
                //memset(spcm.rom, 0x80, ROMSize);
                for (int i = 0; i < ROMSize; i++)
                {
                    spcm.rom[i] = (byte) 0x80;
                }
                //# ifdef _DEBUG
                //            memset(spcm.romusage, 0x02, ROMSize);
                //#endif

                // recalculate bankmask
                mask = (long)(spcm.intf.bank >> 16);
                if (mask == 0)
                    mask = BANK_MASK7 >> 16;

                //spcm.rgnmask = ROMSize - 1;
                for (rom_mask = 1; rom_mask < (long)ROMSize; rom_mask *= 2) ;
                rom_mask--;
                spcm.rgnmask = (int)rom_mask;   // fix for ROMs with e.g 0x60000 bytes (stupid M1)

                spcm.bankmask = (int)(mask & (rom_mask >> spcm.bankshift));
            }
            if (DataStart > ROMSize)
                return;
            if (DataStart + DataLength > ROMSize)
                DataLength = ROMSize - DataStart;

            //memcpy(spcm.rom + DataStart, ROMData, DataLength);
            for (int i = 0; i < DataLength; i++)
            {
                spcm.rom[i + DataStart] = ROMData[i+SrcStartAdr];
            }
            //# ifdef _DEBUG
            //        memset(spcm.romusage + DataStart, 0x00, DataLength);
            //#endif

            return;
        }

        //# ifdef _DEBUG
        //    static void sega_pcm_fwrite_romusage((int)8 ChipID)
        //    {
        //        segapcm_state* spcm = &SPCMData[ChipID];
        //
        //        FILE* hFile;
        //
        //        hFile = fopen("SPCM_ROMUsage.bin", "wb");
        //        if (hFile == NULL)
        //            return;
        //
        //        fwrite(spcm.romusage, 0x01, spcm.ROMSize, hFile);
        //
        //        fclose(hFile);
        //        return;
        //    }
        //#endif

        public void segapcm_set_mute_mask(byte ChipID, int MuteMask)
        {
            segapcm_state spcm = SPCMData[ChipID];
            byte CurChn;

            for (CurChn = 0; CurChn < 16; CurChn++)
                spcm.Muted[CurChn] = (byte)((MuteMask >> CurChn) & 0x01);

            return;
        }

        @Override public int Write(byte ChipID, int port, int adr, int data)
        {
            sega_pcm_w(ChipID, adr, (byte)data);
            return 0;
        }


        /**************************************************************************
         * Generic get_info
         **************************************************************************/

        /*DEVICE_GET_INFO( segapcm )
        {
            switch (state)
            {
                // --- the following bits of info are returned as 64-bit signed integers ---
                case DEVINFO_INT_TOKEN_BYTES:					info.i = sizeof(segapcm_state);				break;

                // --- the following bits of info are returned as pointers to data or functions ---
                case DEVINFO_FCT_START:							info.start = DEVICE_START_NAME( segapcm );		break;
                case DEVINFO_FCT_STOP:							// Nothing									break;
                case DEVINFO_FCT_RESET:							// Nothing									break;

                // --- the following bits of info are returned as NULL-terminated strings ---
                case DEVINFO_STR_NAME:							strcpy(info.s, "Sega PCM");					break;
                case DEVINFO_STR_FAMILY:					strcpy(info.s, "Sega custom");					break;
                case DEVINFO_STR_VERSION:					strcpy(info.s, "1.0");							break;
                case DEVINFO_STR_SOURCE_FILE:						strcpy(info.s, __FILE__);						break;
                case DEVINFO_STR_CREDITS:					strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
            }
        }*/
    }
