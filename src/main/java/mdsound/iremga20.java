﻿package mdsound;

    public class iremga20 extends Instrument
    {
        @Override public void Reset(byte ChipID)
        {
            device_reset_iremga20(ChipID);

            visVolume = new int[][][] {
                new int[][] { new int[] { 0, 0 } }
                , new int[][] { new int[] { 0, 0 } }
            };
        }

        @Override public int Start(byte ChipID, int clock)
        {
            return (int)device_start_iremga20(ChipID, (int)3579545);
        }

        @Override public int Start(byte ChipID, int clock, int ClockValue, Object... option)
        {
            return (int)device_start_iremga20(ChipID, (int)ClockValue);
        }

        @Override public void Stop(byte ChipID)
        {
            device_stop_iremga20(ChipID);
        }

        @Override public void Update(byte ChipID, int[][] outputs, int samples)
        {
            IremGA20_update(ChipID, outputs, samples);

            visVolume[ChipID][0][0] = outputs[0][0];
            visVolume[ChipID][0][1] = outputs[1][0];
        }




        /*********************************************************

    Irem GA20 PCM Sound Chip

*********************************************************/
        //#pragma once

        //# ifndef __IREMGA20_H__
        //#define __IREMGA20_H__

        //#include "devlegcy.h"

        //private void IremGA20_update(byte ChipID, int[][] outputs, int samples) { }
        //private int device_start_iremga20(byte ChipID, int clock) { return 0; }
        //private void device_stop_iremga20(byte ChipID) { }
        //private void device_reset_iremga20(byte ChipID) { }

        //WRITE8_DEVICE_HANDLER( irem_ga20_w );
        //READ8_DEVICE_HANDLER( irem_ga20_r );
        //private byte irem_ga20_r(byte ChipID, int offset) { return 0; }
        //private void irem_ga20_w(byte ChipID, int offset, byte data) { }

        //private void iremga20_write_rom(byte ChipID, int ROMSize, int DataStart, int DataLength,byte[] ROMData) { }

        //private void iremga20_set_mute_mask(byte ChipID, int MuteMask) { }

        //DECLARE_LEGACY_SOUND_DEVICE(IREMGA20, iremga20);

        //#endif /* __IREMGA20_H__ */





        /*********************************************************

Irem GA20 PCM Sound Chip

It's not currently known whether this chip is stereo.


Revisions:

04-15-2002 Acho A. Tang
- rewrote channel mixing
- added prelimenary volume and sample rate emulation

05-30-2002 Acho A. Tang
- applied hyperbolic gain control to volume and used
  a musical-note style progression in sample rate
  calculation(still very inaccurate)

02-18-2004 R. Belmont
- sample rate calculation reverse-engineered.
  Thanks to Fujix, Yasuhiro Ogawa, the Guru, and Tormod
  for real PCB samples that made this possible.

02-03-2007 R. Belmont
- Cleaned up faux x86 assembly.

*********************************************************/

        //#include "emu.h"
        //# include <stdlib.h>
        //# include <string.h> // for memset
        //# include <stddef.h> // for NULL
        //# include "mamedef.h"
        //# include "iremga20.h"

        private static final int MAX_VOL = 256;

        public class IremGA20_channel_def
        {
            public int rate;
            //int size;
            public int start;
            public int pos;
            public int frac;
            public int end;
            public int volume;
            public int pan;
            //int effect;
            public byte play;
            public byte Muted;
        };

        public class ga20_state
        {
            public byte[] rom;
            public int rom_size;
            //sound_stream * stream;
            public int[] regs = new int[0x40];
            public IremGA20_channel_def[] channel = new IremGA20_channel_def[]{
                new IremGA20_channel_def(),
                new IremGA20_channel_def(),
                new IremGA20_channel_def(),
                new IremGA20_channel_def()
            };
        };


        private static final int MAX_CHIPS = 0x02;
        private ga20_state[] GA20Data = new ga20_state[] { new ga20_state(), new ga20_state() };

        /*INLINE ga20_state *get_safe_token(device_t *device)
        {
            assert(device != NULL);
            assert(device.type() == IREMGA20);
            return (ga20_state *)downcast<legacy_device_base *>(device).token();
        }*/


        int[] rate = new int[4]
            , pos = new int[4]
            , frac = new int[4]
            , end = new int[4]
            , vol = new int[4]
            , play = new int[4];

        @Override public String getName() { return "Irem GA20"; }
        @Override public String getShortName() { return "GA20"; }

        //static STREAM_UPDATE( IremGA20_update )
        public void IremGA20_update(byte ChipID, int[][] outputs, int samples)
        {
            //ga20_state *chip = (ga20_state *)param;
            ga20_state chip = GA20Data[ChipID];
            byte[] pSamples;
            int[] outL, outR;
            int i, sampleout;

            /* precache some values */
            for (i = 0; i < 4; i++)
            {
                rate[i] = chip.channel[i].rate;
                pos[i] = chip.channel[i].pos;
                frac[i] = chip.channel[i].frac;
                end[i] = chip.channel[i].end - 0x20;
                vol[i] = chip.channel[i].volume;
                play[i] = (int)((chip.channel[i].Muted == 0) ? chip.channel[i].play : 0);
            }

            i = samples;
            pSamples = chip.rom;
            outL = outputs[0];
            outR = outputs[1];

            for (i = 0; i < samples; i++)
            {
                sampleout = 0;

                // update the 4 channels inline
                if (play[0] != 0)
                {
                    sampleout += (int)((pSamples[pos[0]] - 0x80) * vol[0]);
                    frac[0] += rate[0];
                    pos[0] += frac[0] >> 24;
                    frac[0] &= 0xffffff;
                    play[0] = (int)((pos[0] < end[0]) ? 1 : 0);
                }
                if (play[1] != 0)
                {
                    sampleout += (int)((pSamples[pos[1]] - 0x80) * vol[1]);
                    frac[1] += rate[1];
                    pos[1] += frac[1] >> 24;
                    frac[1] &= 0xffffff;
                    play[1] = (int)((pos[1] < end[1]) ? 1 : 0);
                }
                if (play[2] != 0)
                {
                    sampleout += (int)((pSamples[pos[2]] - 0x80) * vol[2]);
                    frac[2] += rate[2];
                    pos[2] += frac[2] >> 24;
                    frac[2] &= 0xffffff;
                    play[2] = (int)((pos[2] < end[2]) ? 1 : 0);
                }
                if (play[3] != 0)
                {
                    sampleout += (int)((pSamples[pos[3]] - 0x80) * vol[3]);
                    frac[3] += rate[3];
                    pos[3] += frac[3] >> 24;
                    frac[3] &= 0xffffff;
                    play[3] = (int)((pos[3] < end[3]) ? 1 : 0);
                }

                sampleout >>= 2;
                outL[i] = sampleout;
                outR[i] = sampleout;
            }

            /* update the regs now */
            for (i = 0; i < 4; i++)
            {
                chip.channel[i].pos = pos[i];
                chip.channel[i].frac = frac[i];
                if (chip.channel[i].Muted == 0)
                    chip.channel[i].play = (byte)play[i];
            }
        }

        //WRITE8_DEVICE_HANDLER( irem_ga20_w )
        private void irem_ga20_w(byte ChipID, int offset, byte data)
        {
            //ga20_state *chip = get_safe_token(device);
            ga20_state chip = GA20Data[ChipID];
            int channel;

            //logerror("GA20:  Offset %02x, data %04x\n",offset,data);

            //chip.stream.update();

            channel = offset >> 3;

            chip.regs[offset] = data;

            switch (offset & 0x7)
            {
                case 0: /* start address low */
                    chip.channel[channel].start = (int)(((chip.channel[channel].start) & 0xff000) | (int)(data << 4));
                    break;

                case 1: /* start address high */
                    chip.channel[channel].start = (int)(((chip.channel[channel].start) & 0x00ff0) | (int)(data << 12));
                    break;

                case 2: /* end address low */
                    chip.channel[channel].end = (int)(((chip.channel[channel].end) & 0xff000) | (int)(data << 4));
                    break;

                case 3: /* end address high */
                    chip.channel[channel].end = (int)(((chip.channel[channel].end) & 0x00ff0) | (int)(data << 12));
                    break;

                case 4:
                    chip.channel[channel].rate = (int)(0x1000000 / (256 - data));
                    break;

                case 5: //AT: gain control
                    chip.channel[channel].volume = (int)((data * MAX_VOL) / (data + 10));
                    break;

                case 6: //AT: this is always written 2(enabling both channels?)
                    chip.channel[channel].play = data;
                    chip.channel[channel].pos = chip.channel[channel].start;
                    chip.channel[channel].frac = 0;
                    break;
            }
        }

        //READ8_DEVICE_HANDLER( irem_ga20_r )
        public byte irem_ga20_r(byte ChipID, int offset)
        {
            //ga20_state *chip = get_safe_token(device);
            ga20_state chip = GA20Data[ChipID];
            int channel;

            //chip.stream.update();

            channel = offset >> 3;

            switch (offset & 0x7)
            {
                case 7: // voice status.  bit 0 is 1 if active. (routine around 0xccc in rtypeleo)
                    return (byte)(chip.channel[channel].play != 0 ? 1 : 0);

                default:
                    //logerror("GA20: read unk. register %d, channel %d\n", offset & 0xf, channel);
                    break;
            }

            return 0;
        }

        private void iremga20_reset(ga20_state chip)
        {
            int i;

            for (i = 0; i < 4; i++)
            {
                chip.channel[i].rate = 0;
                //chip.channel[i].size = 0;
                chip.channel[i].start = 0;
                chip.channel[i].pos = 0;
                chip.channel[i].frac = 0;
                chip.channel[i].end = 0;
                chip.channel[i].volume = 0;
                chip.channel[i].pan = 0;
                //chip.channel[i].effect = 0;
                chip.channel[i].play = 0;
            }
        }


        //static DEVICE_RESET( iremga20 )
        public void device_reset_iremga20(byte ChipID)
        {
            //iremga20_reset(get_safe_token(device));
            ga20_state chip = GA20Data[ChipID];

            iremga20_reset(chip);
            for (int i = 0; i < 0x40; i++) chip.regs[i] = 0x00;
            //memset(chip.regs, 0x00, 0x40 * sizeof(int));
        }

        //static DEVICE_START( iremga20 )
        public int device_start_iremga20(byte ChipID, int clock)
        {
            //ga20_state *chip = get_safe_token(device);
            ga20_state chip;
            int i;

            if (ChipID >= MAX_CHIPS)
                return 0;

            chip = GA20Data[ChipID];

            /* Initialize our chip structure */
            //chip.rom = *device.region();
            //chip.rom_size = device.region().bytes();
            chip.rom = null;
            chip.rom_size = 0x00;

            iremga20_reset(chip);

            for (i = 0; i < 0x40; i++)
                chip.regs[i] = 0;

            //chip.stream = device.machine().sound().stream_alloc( *device, 0, 2, device.clock()/4, chip, IremGA20_update );

            /*device.save_item(NAME(chip.regs));
            for (i = 0; i < 4; i++)
            {
                device.save_item(NAME(chip.channel[i].rate), i);
                device.save_item(NAME(chip.channel[i].size), i);
                device.save_item(NAME(chip.channel[i].start), i);
                device.save_item(NAME(chip.channel[i].pos), i);
                device.save_item(NAME(chip.channel[i].end), i);
                device.save_item(NAME(chip.channel[i].volume), i);
                device.save_item(NAME(chip.channel[i].pan), i);
                device.save_item(NAME(chip.channel[i].effect), i);
                device.save_item(NAME(chip.channel[i].play), i);
            }*/
            for (i = 0; i < 4; i++)
                chip.channel[i].Muted = 0x00;

            return clock / 4;
        }

        public void device_stop_iremga20(byte ChipID)
        {
            ga20_state chip = GA20Data[ChipID];

            //free(chip.rom);
            chip.rom = null;

            return;
        }

        public void iremga20_write_rom(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData)
        {

            ga20_state chip = GA20Data[ChipID];

            if (chip.rom_size != ROMSize)
            {
                chip.rom = new byte[ROMSize];// (byte*) realloc(chip.rom, ROMSize);
                chip.rom_size = (int)ROMSize;

                for (int i = 0; i < ROMSize; i++) chip.rom[i] = (byte) 0xff;
                //memset(chip.rom, 0xFF, ROMSize);
            }
            if (DataStart > ROMSize)
                return;
            if (DataStart + DataLength > ROMSize)
                DataLength = ROMSize - DataStart;


            for (int i = 0; i < DataLength; i++) chip.rom[i + DataStart] = ROMData[i];
            //memcpy(chip.rom + DataStart, ROMData, DataLength);

            return;
        }

        public void iremga20_write_rom(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int srcStartAddress)
        {

            ga20_state chip = GA20Data[ChipID];

            if (chip.rom_size != ROMSize)
            {
                chip.rom = new byte[ROMSize];// (byte*) realloc(chip.rom, ROMSize);
                chip.rom_size = (int)ROMSize;

                for (int i = 0; i < ROMSize; i++) chip.rom[i] = (byte) 0xff;
                //memset(chip.rom, 0xFF, ROMSize);
            }
            if (DataStart > ROMSize)
                return;
            if (DataStart + DataLength > ROMSize)
                DataLength = ROMSize - DataStart;


            for (int i = 0; i < DataLength; i++) chip.rom[i + DataStart] = ROMData[i+ srcStartAddress];
            //memcpy(chip.rom + DataStart, ROMData, DataLength);

            return;
        }


        public void iremga20_set_mute_mask(byte ChipID, int MuteMask)
        {
            ga20_state chip = GA20Data[ChipID];
            byte CurChn;

            for (CurChn = 0; CurChn < 4; CurChn++)
                chip.channel[CurChn].Muted = (byte)((MuteMask >> CurChn) & 0x01);

            return;
        }

        @Override public int Write(byte ChipID, int port, int adr, int data)
        {
            irem_ga20_w(ChipID, adr, (byte)data);
            return 0;
        }




        /**************************************************************************
         * Generic get_info
         **************************************************************************/

        /*DEVICE_GET_INFO( iremga20 )
        {
            switch (state)
            {
                // --- the following bits of info are returned as 64-bit signed integers ---
                case DEVINFO_INT_TOKEN_BYTES:     info.i = sizeof(ga20_state);     break;

                // --- the following bits of info are returned as pointers to data or functions ---
                case DEVINFO_FCT_START:       info.start = DEVICE_START_NAME( iremga20 ); break;
                case DEVINFO_FCT_STOP:       // nothing //         break;
                case DEVINFO_FCT_RESET:       info.reset = DEVICE_RESET_NAME( iremga20 ); break;

                // --- the following bits of info are returned as NULL-terminated strings ---
                case DEVINFO_STR_NAME:       strcpy(info.s, "Irem GA20");     break;
                case DEVINFO_STR_FAMILY:     strcpy(info.s, "Irem custom");     break;
                case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
                case DEVINFO_STR_SOURCE_FILE:      strcpy(info.s, __FILE__);      break;
                case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
            }
        }


        DEFINE_LEGACY_SOUND_DEVICE(IREMGA20, iremga20);*/

    }
