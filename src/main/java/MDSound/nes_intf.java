﻿package MDSound;
import MDSound.np.np_nes_apu;
import MDSound.np.np_nes_dmc;
import MDSound.np.np_nes_fds;
import MDSound.np.np_nes_fds.NES_FDS;

    public class nes_intf extends Instrument
    {
        @Override public void Reset(byte ChipID)
        {
            device_reset_nes(ChipID);
        }

        @Override public int Start(byte ChipID, int clock)
        {
            return (int)device_start_nes(ChipID, (int)clock);
        }

        @Override public int Start(byte ChipID, int SamplingRate, int clock, Object... Option)
        {
            return (int)device_start_nes(ChipID, (int)clock);
        }

        @Override public void Stop(byte ChipID)
        {
            device_stop_nes(ChipID);
        }

        @Override public void Update(byte ChipID, int[][] outputs, int samples)
        {
            nes_stream_update(ChipID, outputs, samples);

            visVolume[ChipID][0][0] = outputs[0][0];
            visVolume[ChipID][0][1] = outputs[1][0];
        }

        //	音量設定
        public void SetVolumeAPU(int db)
        {
            db = Math.min(db, 20);
            if (db > -192)
                apuVolume = (int)(16384.0 * Math.pow(10.0, db / 40.0));
            else
                apuVolume = 0;
        }

        public void SetVolumeDMC(int db)
        {
            db = Math.min(db, 20);
            if (db > -192)
                dmcVolume = (int)(16384.0 * Math.pow(10.0, db / 40.0));
            else
                dmcVolume = 0;
        }

        public void SetVolumeFDS(int db)
        {
            db = Math.min(db, 20);
            if (db > -192)
                fdsVolume = (int)(16384.0 * Math.pow(10.0, db / 40.0));
            else
                fdsVolume = 0;
        }

        private int apuVolume = 0;
        private int dmcVolume = 0;
        private int fdsVolume = 0;
        private np_nes_apu nes_apu;
        private np_nes_dmc nes_dmc;
        private np_nes_fds nes_fds;

        public nes_intf()
        {
            nes_apu = new np_nes_apu();
            nes_dmc = new np_nes_dmc();
            nes_fds = new np_nes_fds();
            nes_dmc.nes_apu = nes_apu;

            visVolume = new int[][][] {
                new int[][] { new int[] { 0, 0 } }
                , new int[][] { new int[] { 0, 0 } }
            };

            SetVolumeAPU(0);
            SetVolumeDMC(0);
            SetVolumeFDS(0);
        }

        /****************************************************************

            MAME / MESS functions

        ****************************************************************/

        //# include "mamedef.h"
        //# include <memory.h>	// for memset
        //# include <malloc.h>	// for free
        //# include <stddef.h>	// for NULL
        //# include "../stdbool.h"
        //        //#include "sndintrf.h"
        //        //#include "streams.h"
        //# include "nes_apu.h"
        //# include "np_nes_apu.h"
        //# include "np_nes_dmc.h"
        //# include "np_nes_fds.h"
        //# include "nes_intf.h"


        //# ifdef ENABLE_ALL_CORES
        //#define EC_MAME		0x01	// NES core from MAME
        //#endif
        //#define EC_NSFPLAY	0x00	// NES core from NSFPlay
        // Note: FDS core from NSFPlay is always used

        /* for stream system */
        public static class nes_state
        {
            public np_nes_apu.NES_APU chip_apu;
            public np_nes_dmc.NES_DMC chip_dmc;
            public np_nes_fds.NES_FDS chip_fds;
            public byte[] Memory;
        };

        //extern
        //private byte CHIP_SAMPLING_MODE;
        //extern
        //private int CHIP_SAMPLE_RATE;
        //static
        //byte EMU_CORE = 0x00;

        //extern
        //private int SampleRate;
        private final byte MAX_CHIPS = 0x02;
        private static nes_state[] NESAPUData = new nes_state[] { new nes_state(), new nes_state() };// MAX_CHIPS];
        private static int NesOptions = 0x8000;

        @Override public String getName ()  { return "NES"; } 
        @Override public String getShortName() {   return "NES"; } 

        //static void nes_set_chip_option((int)8 ChipID);

        public void nes_stream_update(byte ChipID, int[][] outputs, int samples)
        {
            nes_state info = NESAPUData[ChipID];
            int CurSmpl;
            int[] BufferA = new int[2];
            int[] BufferD = new int[2];
            int[] BufferF = new int[2];

            //            switch (EMU_CORE)
            //            {
            //# ifdef ENABLE_ALL_CORES
            //                case EC_MAME:
            //                    nes_psg_update_sound(info.chip_apu, outputs, samples);
            //                   break;
            //#endif
            //                case EC_NSFPLAY:
            for (CurSmpl = 0x00; CurSmpl < samples; CurSmpl++)
            {
                nes_apu.NES_APU_np_Render(info.chip_apu, BufferA);
                nes_dmc.NES_DMC_np_Render(info.chip_dmc, BufferD);
                outputs[0][CurSmpl] = (short)((Limit(BufferA[0], 0x7fff, -0x8000) * apuVolume) >> 14);
                outputs[1][CurSmpl] = (short)((Limit(BufferA[1], 0x7fff, -0x8000) * apuVolume) >> 14);
                outputs[0][CurSmpl] += (short)((Limit(BufferD[0], 0x7fff, -0x8000) * dmcVolume) >> 14);
                outputs[1][CurSmpl] += (short)((Limit(BufferD[1], 0x7fff, -0x8000) * dmcVolume) >> 14);
                MDSound.np_nes_apu_volume = Math.abs(BufferA[0]);
                MDSound.np_nes_dmc_volume = Math.abs(BufferD[0]);
            }
            //                    break;
            //            }

            if (info.chip_fds != null)
            {
                for (CurSmpl = 0x00; CurSmpl < samples; CurSmpl++)
                {
                    nes_fds.NES_FDS_Render(info.chip_fds, BufferF);
                    outputs[0][CurSmpl] += (short)((Limit(BufferF[0], 0x7fff, -0x8000) * fdsVolume) >> 14);
                    outputs[1][CurSmpl] += (short)((Limit(BufferF[1], 0x7fff, -0x8000) * fdsVolume) >> 14);
                    MDSound.np_nes_fds_volume = Math.abs(BufferF[0]);
                }
            }

            return;
        }

        public static int Limit(int v, int max, int min)
        {
            return v > max ? max : (v < min ? min : v);
        }

        private int device_start_nes(byte ChipID, int clock)
        {
            nes_state info;
            int rate;
            boolean EnableFDS;

            if (ChipID >= MAX_CHIPS)
                return 0;

            EnableFDS = ((clock >> 31) & 0x01) != 0;
            clock &= 0x7FFFFFFF;

            info = NESAPUData[ChipID];
            rate = clock / 4;
            if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
                rate = CHIP_SAMPLE_RATE;

            //            switch (EMU_CORE)
            //            {
            //# ifdef ENABLE_ALL_CORES
            //                case EC_MAME:
            //                    info.chip_apu = device_start_nesapu(clock, rate);
            //                    if (info.chip_apu == NULL)
            //                        return 0;

            //                    info.chip_dmc = NULL;
            //                    info.chip_fds = NULL;

            //                    info.Memory = ((int)8*)malloc(0x8000);
            //                    memset(info.Memory, 0x00, 0x8000);
            //                    nesapu_set_rom(info.chip_apu, info.Memory - 0x8000);
            //                    break;
            //#endif
            //                case EC_NSFPLAY:
            info.chip_apu = nes_apu.NES_APU_np_Create(clock, rate);
            if (info.chip_apu == null)
                return 0;

            info.chip_dmc = nes_dmc.NES_DMC_np_Create(clock, rate);
            if (info.chip_dmc == null)
            {
                nes_apu.NES_APU_np_Destroy(info.chip_apu);
                info.chip_apu = null;
                return 0;
            }

            nes_dmc.NES_DMC_np_SetAPU(info.chip_dmc, info.chip_apu);

            info.Memory = new byte[0x8000];// ((int)8*)malloc(0x8000);
            for (int i = 0; i < info.Memory.length; i++) info.Memory[i] = 0;// memset(info.Memory, 0x00, 0x8000);
            nes_dmc.NES_DMC_np_SetMemory(info.chip_dmc, info.Memory, -0x8000);
            //        break;
            //}

            if (EnableFDS)
            {
                info.chip_fds = nes_fds.NES_FDS_Create(clock, rate);
                // If it returns NULL, that's okay.
            }
            else
            {
                info.chip_fds = null;
            }
            nes_set_chip_option(ChipID);

            return rate;
        }

        private void device_stop_nes(byte ChipID)
        {
            nes_state info = NESAPUData[ChipID];
            //            switch (EMU_CORE)
            //            {
            //# ifdef ENABLE_ALL_CORES
            //                case EC_MAME:
            //                    device_stop_nesapu(info.chip_apu);
            //                    break;
            //#endif
            //                case EC_NSFPLAY:
            nes_apu.NES_APU_np_Destroy(info.chip_apu);
            nes_dmc.NES_DMC_np_Destroy(info.chip_dmc);
            //                    break;
            //            }
            if (info.chip_fds != null)
                nes_fds.NES_FDS_Destroy(info.chip_fds);

            if (info.Memory != null)
            {
                //free(info.Memory);
                info.Memory = null;
            }
            info.chip_apu = null;
            info.chip_dmc = null;
            info.chip_fds = null;

            return;
        }

        private void device_reset_nes(byte ChipID)
        {
            nes_state info = NESAPUData[ChipID];
            //            switch (EMU_CORE)
            //            {
            //# ifdef ENABLE_ALL_CORES
            //                case EC_MAME:
            //                    device_reset_nesapu(info.chip_apu);
            //                    break;
            //#endif
            //                case EC_NSFPLAY:
            nes_apu.NES_APU_np_Reset(info.chip_apu);
            nes_dmc.NES_DMC_np_Reset(info.chip_dmc);
            //                    break;
            //            }
            if (info.chip_fds != null)
                nes_fds.NES_FDS_Reset(info.chip_fds);
        }


        private void nes_w(byte ChipID, int offset, byte data)
        {
            nes_state info = NESAPUData[ChipID];

            switch (offset & 0xE0)
            {
                case 0x00:  // NES APU
                            //                    switch (EMU_CORE)
                            //                    {
                            //# ifdef ENABLE_ALL_CORES
                            //                        case EC_MAME:
                            //                            nes_psg_w(info.chip_apu, offset, data);
                            //                            break;
                            //#endif
                            //                        case EC_NSFPLAY:
                            // NES_APU handles the sqaure waves, NES_DMC the rest
                    nes_apu.NES_APU_np_Write(info.chip_apu, (int)(0x4000 | offset), data);
                    nes_dmc.NES_DMC_np_Write(info.chip_dmc, (int)(0x4000 | offset), data);
                    //                            break;
                    //                    }
                    break;
                case 0x20:  // FDS register
                    if (info.chip_fds == null)
                        break;
                    if (offset == 0x3F)
                        nes_fds.NES_FDS_Write(info.chip_fds, 0x4023, data);
                    else
                        nes_fds.NES_FDS_Write(info.chip_fds, (int)(0x4080 | (offset & 0x1F)), data);
                    break;
                case 0x40:  // FDS wave RAM
                case 0x60:
                    if (info.chip_fds == null)
                        break;
                    nes_fds.NES_FDS_Write(info.chip_fds, (int)(0x4000 | offset), data);
                    break;
            }
        }

        public void nes_write_ram(byte ChipID, int DataStart, int DataLength, byte[] RAMData)
        {
            nes_write_ram(ChipID, DataStart, DataLength, RAMData, 0);
        }

        public void nes_write_ram(byte ChipID, int DataStart, int DataLength, byte[] RAMData, int RAMDataStartAdr)
        {
            nes_state info = NESAPUData[ChipID];
            int RemainBytes;
            int ptrRAMData = 0;

            if (DataStart >= 0x10000)
                return;

            if (DataStart < 0x8000)
            {
                if (DataStart + DataLength <= 0x8000)
                    return;

                RemainBytes = (int)(0x8000 - DataStart);
                DataStart = 0x8000;
                //RAMData += RemainBytes;
                ptrRAMData = RemainBytes;
                DataLength -= (int)RemainBytes;
            }

            RemainBytes = 0x00;
            if (DataStart + DataLength > 0x10000)
            {
                RemainBytes = (int)DataLength;
                DataLength = 0x10000 - DataStart;
                RemainBytes -= (int)DataLength;
            }

            //memcpy(info.Memory + (DataStart - 0x8000), RAMData, DataLength);
            for (int i = 0; i < DataLength; i++)
            {
                info.Memory[(DataStart - 0x8000) + i] = RAMData[ptrRAMData + i + RAMDataStartAdr];
            }

            if (RemainBytes != 0)
            {
                if (RemainBytes > 0x8000)
                    RemainBytes = 0x8000;
                //memcpy(info.Memory, RAMData + DataLength, RemainBytes);
                for (int i = 0; i < RemainBytes; i++)
                {
                    info.Memory[ptrRAMData + DataLength + i] = RAMData[ptrRAMData + DataLength + i + RAMDataStartAdr];
                }
            }

            return;
        }

        public byte[] nes_r_apu(byte ChipID)
        {
            if (NESAPUData[ChipID] == null) return null;
            if (NESAPUData[ChipID].chip_apu == null) return null;
            return NESAPUData[ChipID].chip_apu.reg;
        }

        public byte[] nes_r_dmc(byte ChipID)
        {
            if (NESAPUData[ChipID] == null) return null;
            if (NESAPUData[ChipID].chip_dmc == null) return null;

            return NESAPUData[ChipID].chip_dmc.reg;
        }

        public NES_FDS nes_r_fds(byte ChipID)
        {
            if (NESAPUData[ChipID] == null) return null;
            if (NESAPUData[ChipID].chip_fds == null) return null;

            return NESAPUData[ChipID].chip_fds;
        }

        private void nes_set_emu_core(byte Emulator)
        {
            //# ifdef ENABLE_ALL_CORES
            //    EMU_CORE = (Emulator < 0x02) ? Emulator : 0x00;
            //#else
            //EMU_CORE = EC_NSFPLAY;
            //#endif

            return;
        }

        private void nes_set_option(int Options)
        {
            NesOptions = Options;

            return;
        }

        private void nes_set_chip_option(byte ChipID)
        {
            nes_state info = NESAPUData[ChipID];
            byte CurOpt;

            if ((NesOptions & 0x8000) != 0)
                return;

            //    switch (EMU_CORE)
            //    {
            //# ifdef ENABLE_ALL_CORES
            //        case EC_MAME:
            //            // no options for MAME's NES core
            //            break;
            //#endif
            //        case EC_NSFPLAY:
            // shared APU/DMC options
            for (CurOpt = 0; CurOpt < 2; CurOpt++)
            {
                nes_apu.NES_APU_np_SetOption(info.chip_apu, CurOpt, (NesOptions >> CurOpt) & 0x01);
                nes_dmc.NES_DMC_np_SetOption(info.chip_dmc, CurOpt, (NesOptions >> CurOpt) & 0x01);
            }
            // APU-only options
            for (; CurOpt < 4; CurOpt++)
                nes_apu.NES_APU_np_SetOption(info.chip_apu, CurOpt - 2 + 2, (NesOptions >> CurOpt) & 0x01);
            // DMC-only options
            for (; CurOpt < 10; CurOpt++)
                nes_dmc.NES_DMC_np_SetOption(info.chip_dmc, CurOpt - 4 + 2, (NesOptions >> CurOpt) & 0x01);
            //            break;
            //    }
            if (info.chip_fds != null)
            {
                // FDS options
                // I skip the Cutoff frequency here, since it's not a boolean value.
                for (CurOpt = 12; CurOpt < 14; CurOpt++)
                    nes_fds.NES_FDS_SetOption(info.chip_fds, CurOpt - 12 + 1, (NesOptions >> CurOpt) & 0x01);
            }

            return;
        }

        public void nes_set_mute_mask(byte ChipID, int MuteMask)
        {
            if (NESAPUData[ChipID] == null) return;

            nes_state info = NESAPUData[ChipID];
            //    switch (EMU_CORE)
            //    {
            //# ifdef ENABLE_ALL_CORES
            //        case EC_MAME:
            //            nesapu_set_mute_mask(info.chip_apu, MuteMask);
            //            break;
            //#endif
            //        case EC_NSFPLAY:
            if (nes_apu != null && info.chip_apu != null) nes_apu.NES_APU_np_SetMask(info.chip_apu, (int)((MuteMask & 0x03) >> 0));
            if (nes_dmc != null && info.chip_dmc != null) nes_dmc.NES_DMC_np_SetMask(info.chip_dmc, (int)((MuteMask & 0x1C) >> 2));
            //        break;
            //}
            if (nes_fds != null && info.chip_fds != null) nes_fds.NES_FDS_SetMask(info.chip_fds, (int)((MuteMask & 0x20) >> 5));

            return;
        }

        @Override public int Write(byte ChipID, int port, int adr, int data)
        {
            nes_w(ChipID, adr, (byte)data);
            return 0;
        }
    }
