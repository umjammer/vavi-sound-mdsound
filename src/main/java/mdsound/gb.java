﻿package mdsound;

    public class gb extends Instrument
    {
        @Override public void Reset(byte ChipID)
        {
            device_reset_gameboy_sound(ChipID);

            visVolume = new int[][][] {
                new int[][] { new int[] { 0, 0 } }
                , new int[][] { new int[] { 0, 0 } }
            };
        }

        @Override public int Start(byte ChipID, int clock)
        {
            return (int)device_start_gameboy_sound(ChipID, 4194304);
        }

        @Override public int Start(byte ChipID, int clock, int ClockValue, Object... option)
        {
            return (int)device_start_gameboy_sound(ChipID, (int)ClockValue);
        }

        @Override public void Stop(byte ChipID)
        {
            device_stop_gameboy_sound(ChipID);
        }

        @Override public void Update(byte ChipID, int[][] outputs, int samples)
        {
            gameboy_update(ChipID, outputs, samples);

            visVolume[ChipID][0][0] = outputs[0][0];
            visVolume[ChipID][0][1] = outputs[1][0];
        }






        /* Custom Sound Interface */
        //private byte gb_wave_r(byte ChipID, int offset) { return 0; }
        //private void gb_wave_w(byte ChipID, int offset, byte data) { }
        //private byte gb_sound_r(byte ChipID, int offset) { return 0; }
        //private void gb_sound_w(byte ChipID, int offset, byte data) { }

        //private void gameboy_update(byte ChipID, int[][] outputs, int samples) { }
        //private int device_start_gameboy_sound(byte ChipID, int clock) { return 0; }
        //private void device_stop_gameboy_sound(byte ChipID) { }
        //private void device_reset_gameboy_sound(byte ChipID) { }

        //private void gameboy_sound_set_mute_mask(byte ChipID, int MuteMask) { }
        //private int gameboy_sound_get_mute_mask(byte ChipID) { return 0; }
        //private void gameboy_sound_set_options(byte Flags) { }





        // license:BSD-3-Clause
        // copyright-holders:Wilbert Pol, Anthony Kruize
        // thanks-to:Shay Green
        /**************************************************************************************
        * Game Boy sound emulation (c) Anthony Kruize (trandor@labyrinth.net.au)
        *
        * Anyways, sound on the Game Boy consists of 4 separate 'channels'
        *   Sound1 = Quadrangular waves with SWEEP and ENVELOPE functions  (NR10,11,12,13,14)
        *   Sound2 = Quadrangular waves with ENVELOPE functions (NR21,22,23,24)
        *   Sound3 = Wave patterns from WaveRAM (NR30,31,32,33,34)
        *   Sound4 = White noise with an envelope (NR41,42,43,44)
        *
        * Each sound channel has 2 modes, namely ON and OFF...  whoa
        *
        * These tend to be the two most important equations in
        * converting between Hertz and GB frequency registers:
        * (Sounds will have a 2.4% higher frequency on Super GB.)
        *       gb = 2048 - (131072 / Hz)
        *       Hz = 131072 / (2048 - gb)
        *
        * Changes:
        *
        *   10/2/2002       AK - Preliminary sound code.
        *   13/2/2002       AK - Added a hack for mode 4, other fixes.
        *   23/2/2002       AK - Use lookup tables, added sweep to mode 1. Re-wrote the square
        *                        wave generation.
        *   13/3/2002       AK - Added mode 3, better lookup tables, other adjustments.
        *   15/3/2002       AK - Mode 4 can now change frequencies.
        *   31/3/2002       AK - Accidently forgot to handle counter/consecutive for mode 1.
        *    3/4/2002       AK - Mode 1 sweep can still occur if shift is 0.  Don't let frequency
        *                        go past the maximum allowed value. Fixed Mode 3 length table.
        *                        Slight adjustment to Mode 4's period table generation.
        *    5/4/2002       AK - Mode 4 is done correctly, using a polynomial counter instead
        *                        of being a total hack.
        *    6/4/2002       AK - Slight tweak to mode 3's frequency calculation.
        *   13/4/2002       AK - Reset envelope value when sound is initialized.
        *   21/4/2002       AK - Backed out the mode 3 frequency calculation change.
        *                        Merged init functions into gameboy_sound_w().
        *   14/5/2002       AK - Removed magic numbers in the fixed point math.
        *   12/6/2002       AK - Merged SOUNDx structs into one SOUND struct.
        *  26/10/2002       AK - Finally fixed channel 3!
        * xx/4-5/2016       WP - Rewrote sound core. Most of the code is not optimized yet.

        TODO:
        - Implement different behavior of CGB-02.
        - Implement different behavior of CGB-05.
        - Perform more tests on real hardware to figure out when the frequency counters are
          reloaded.
        - Perform more tests on real hardware to understand when changes to the noise divisor
          and shift kick in.
        - Optimize the channel update methods.

        ***************************************************************************************/

        //# include "mamedef.h"
        //# include <stdlib.h> // for rand
        //# include <string.h> // for memset
        //#include "emu.h"
        //# include "gb.h"
        //#include "streams.h"


        //typedef byte   bool;
        //#define false 0x00
        //#define true 0x01


        private static final int RC_SHIFT = 16;

        public class RATIO_CNTR
        {

            public int inc; // counter increment
            public int val; // current value
        }

        private void RC_SET_RATIO(RATIO_CNTR rc, int mul, int div)
        {
            rc.inc = (int)((((long)mul << RC_SHIFT) + div / 2) / div);
        }


        /***************************************************************************
            CONSTANTS
        ***************************************************************************/

        private static final int NR10 = 0x00;
        private static final int NR11 = 0x01;
        private static final int NR12 = 0x02;
        private static final int NR13 = 0x03;
        private static final int NR14 = 0x04;
        // 0x05
        private static final int NR21 = 0x06;
        private static final int NR22 = 0x07;
        private static final int NR23 = 0x08;
        private static final int NR24 = 0x09;
        private static final int NR30 = 0x0A;
        private static final int NR31 = 0x0B;
        private static final int NR32 = 0x0C;
        private static final int NR33 = 0x0D;
        private static final int NR34 = 0x0E;
        // 0x0F
        private static final int NR41 = 0x10;
        private static final int NR42 = 0x11;
        private static final int NR43 = 0x12;
        private static final int NR44 = 0x13;
        private static final int NR50 = 0x14;
        private static final int NR51 = 0x15;
        private static final int NR52 = 0x16;
        // 0x17 - 0x1F
        private static final int AUD3W0 = 0x20;
        private static final int AUD3W1 = 0x21;
        private static final int AUD3W2 = 0x22;
        private static final int AUD3W3 = 0x23;
        private static final int AUD3W4 = 0x24;
        private static final int AUD3W5 = 0x25;
        private static final int AUD3W6 = 0x26;
        private static final int AUD3W7 = 0x27;
        private static final int AUD3W8 = 0x28;
        private static final int AUD3W9 = 0x29;
        private static final int AUD3WA = 0x2A;
        private static final int AUD3WB = 0x2B;
        private static final int AUD3WC = 0x2C;
        private static final int AUD3WD = 0x2D;
        private static final int AUD3WE = 0x2E;
        private static final int AUD3WF = 0x2F;

        private static final int FRAME_CYCLES = 8192;

        /* Represents wave duties of 12.5%, 25%, 50% and 75% */
        private int[][] wave_duty_table = new int[][]{
            new int[]{ -1, -1, -1, -1, -1, -1, -1,  1},
            new int[]{  1, -1, -1, -1, -1, -1, -1,  1},
            new int[]{  1, -1, -1, -1, -1,  1,  1,  1},
            new int[]{ -1,  1,  1,  1,  1,  1,  1, -1}
        };


        /***************************************************************************
            TYPE DEFINITIONS
        ***************************************************************************/

        public class SOUND
        {
            /* Common */
            public byte[] reg = new byte[5];
            public boolean on;
            public byte channel;
            public byte length;
            public byte length_mask;
            public boolean length_counting;
            public boolean length_enabled;
            /* Mode 1, 2, 3 */
            public int cycles_left;
            public byte duty;
            /* Mode 1, 2, 4 */
            public boolean envelope_enabled;
            public byte envelope_value;
            public byte envelope_direction;
            public byte envelope_time;
            public byte envelope_count;
            public byte signal;
            /* Mode 1 */
            public int frequency;
            public int frequency_counter;
            public boolean sweep_enabled;
            public boolean sweep_neg_mode_used;
            public byte sweep_shift;
            public int sweep_direction;
            public byte sweep_time;
            public byte sweep_count;
            /* Mode 3 */
            public byte level;
            public byte offset;
            public int duty_count;
            public byte current_sample;
            public boolean sample_reading;
            /* Mode 4 */
            public boolean noise_short;
            public int noise_lfsr;
            public byte Muted;
        };

        public class SOUNDC
        {
            public byte on;
            public byte vol_left;
            public byte vol_right;
            public byte mode1_left;
            public byte mode1_right;
            public byte mode2_left;
            public byte mode2_right;
            public byte mode3_left;
            public byte mode3_right;
            public byte mode4_left;
            public byte mode4_right;
            public int cycles;
            public boolean wave_ram_locked;
        };


        private static final int GBMODE_DMG = 0x00;
        private static final int GBMODE_CGB04 = 0x01;

        public class gb_sound_t
        {
            public int rate;

            public SOUND snd_1;
            public SOUND snd_2;
            public SOUND snd_3;
            public SOUND snd_4;
            public SOUNDC snd_control;

            public byte[] snd_regs = new byte[0x30];

            public RATIO_CNTR cycleCntr;

            public byte gbMode;
            //byte BoostWaveChn;
        };


        //private byte CHIP_SAMPLING_MODE;
        //private int CHIP_SAMPLE_RATE;
        //private int SampleRate;
        private static final int MAX_CHIPS = 0x02;
        private gb_sound_t[] GBSoundData = new gb_sound_t[] { new gb_sound_t(), new gb_sound_t() };

        private byte BoostWaveChn = 0x00;


        //private void gb_corrupt_wave_ram(gb_sound_t gb) { }
        //private void gb_apu_power_off(gb_sound_t gb) { }
        //private void gb_tick_length(SOUND  snd){ }
        //private int gb_calculate_next_sweep(SOUND  snd){ return 0; }
        //private boolean gb_dac_enabled(SOUND  snd){ return false; }
        //private int gb_noise_period_cycles(gb_sound_t gb) { return 0; }

        /***************************************************************************
            IMPLEMENTATION
        ***************************************************************************/

        public byte gb_wave_r(byte ChipID, int offset)
        {
            gb_sound_t gb = GBSoundData[ChipID];

            //gb_update_state(gb, 0);

            if (gb.snd_3.on)
            {
                if (gb.gbMode == GBMODE_DMG)
                    return (byte)(gb.snd_3.sample_reading ? gb.snd_regs[AUD3W0 + (gb.snd_3.offset / 2)] : 0xFF);
                else if (gb.gbMode == GBMODE_CGB04)
                    return gb.snd_regs[AUD3W0 + (gb.snd_3.offset / 2)];
            }

            return gb.snd_regs[AUD3W0 + offset];
        }

        public void gb_wave_w(byte ChipID, int offset, byte data)
        {
            gb_sound_t gb = GBSoundData[ChipID];

            //gb_update_state(gb, 0);

            if (gb.snd_3.on)
            {
                if (gb.gbMode == GBMODE_DMG)
                {
                    if (gb.snd_3.sample_reading)
                    {
                        gb.snd_regs[AUD3W0 + (gb.snd_3.offset / 2)] = data;
                    }
                }
                else if (gb.gbMode == GBMODE_CGB04)
                {
                    gb.snd_regs[AUD3W0 + (gb.snd_3.offset / 2)] = data;
                }
            }
            else
            {
                gb.snd_regs[AUD3W0 + offset] = data;
            }
        }

        private byte[] read_mask = new byte[] {
                (byte)0x80,0x3F,0x00,(byte)0xFF,(byte)0xBF,(byte)0xFF,0x3F,0x00,(byte)0xFF,(byte)0xBF,0x7F,(byte)0xFF,(byte)0x9F,(byte)0xFF,(byte)0xBF,(byte)0xFF,
                (byte)0xFF,0x00,0x00,(byte)0xBF,0x00,0x00,0x70,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
                0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
                0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
        };
        public byte gb_sound_r(byte ChipID, int offset)
        {
            gb_sound_t gb = GBSoundData[ChipID];

            //gb_update_state(gb, 0);

            if (offset < AUD3W0)
            {
                if (gb.snd_control.on != 0)
                {
                    if (offset == NR52)
                    {
                        return (byte)(
                            (gb.snd_regs[NR52] & 0xf0)
                            | (gb.snd_1.on ? 1 : 0)
                            | (gb.snd_2.on ? 2 : 0)
                            | (gb.snd_3.on ? 4 : 0)
                            | (gb.snd_4.on ? 8 : 0)
                            | 0x70);
                    }
                    return (byte)(gb.snd_regs[offset] | read_mask[offset & 0x3F]);
                }
                else
                {
                    return read_mask[offset & 0x3F];
                }
            }
            else if (offset <= AUD3WF)
            {
                return gb_wave_r(ChipID, offset - AUD3W0);
            }
            return (byte) 0xFF;
        }

        private void gb_sound_w_internal(gb_sound_t gb, byte offset, byte data)
        {
            /* Store the value */
            byte old_data = gb.snd_regs[offset];

            if (gb.snd_control.on != 0)
            {
                gb.snd_regs[offset] = data;
            }

            switch (offset)
            {
                /*MODE 1 */
                case NR10: /* Sweep (R/W) */
                    gb.snd_1.reg[0] = data;
                    gb.snd_1.sweep_shift = (byte)(data & 0x7);
                    gb.snd_1.sweep_direction = (data & 0x8) != 0 ? -1 : 1;
                    gb.snd_1.sweep_time = (byte)((data & 0x70) >> 4);
                    if ((old_data & 0x08) != 0 && (data & 0x08) == 0 && gb.snd_1.sweep_neg_mode_used)
                    {
                        gb.snd_1.on = false;
                    }
                    break;
                case NR11: /* Sound length/Wave pattern duty (R/W) */
                    gb.snd_1.reg[1] = data;
                    if (gb.snd_control.on != 0)
                    {
                        gb.snd_1.duty = (byte)((data & 0xc0) >> 6);
                    }
                    gb.snd_1.length = (byte)(data & 0x3f);
                    gb.snd_1.length_counting = true;
                    break;
                case NR12: /* Envelope (R/W) */
                    gb.snd_1.reg[2] = data;
                    gb.snd_1.envelope_value = (byte)(data >> 4);
                    gb.snd_1.envelope_direction = (byte)((data & 0x8) != 0 ? 1 : -1);
                    gb.snd_1.envelope_time = (byte)(data & 0x07);
                    if (!gb_dac_enabled(gb.snd_1))
                    {
                        gb.snd_1.on = false;
                    }
                    break;
                case NR13: /* Frequency lo (R/W) */
                    gb.snd_1.reg[3] = data;
                    // Only enabling the frequency line breaks blarggs's sound test // #5
                    // This condition may not be correct
                    if (!gb.snd_1.sweep_enabled)
                    {
                        gb.snd_1.frequency = (int)(((gb.snd_1.reg[4] & 0x7) << 8) | gb.snd_1.reg[3]);
                    }
                    break;
                case NR14: /* Frequency hi / Initialize (R/W) */
                    gb.snd_1.reg[4] = data;
                    {
                        boolean length_was_enabled = gb.snd_1.length_enabled;

                        gb.snd_1.length_enabled = (data & 0x40) != 0 ? true : false;
                        gb.snd_1.frequency = (int)(((gb.snd_regs[NR14] & 0x7) << 8) | gb.snd_1.reg[3]);

                        if (!length_was_enabled && (gb.snd_control.cycles & FRAME_CYCLES) == 0 && gb.snd_1.length_counting)
                        {
                            if (gb.snd_1.length_enabled)
                            {
                                gb_tick_length(gb.snd_1);
                            }
                        }

                        if ((data & 0x80) != 0)
                        {
                            gb.snd_1.on = true;
                            gb.snd_1.envelope_enabled = true;
                            gb.snd_1.envelope_value = (byte)(gb.snd_1.reg[2] >> 4);
                            gb.snd_1.envelope_count = gb.snd_1.envelope_time;
                            gb.snd_1.sweep_count = gb.snd_1.sweep_time;
                            gb.snd_1.sweep_neg_mode_used = false;
                            gb.snd_1.signal = 0;
                            gb.snd_1.length = (byte)(gb.snd_1.reg[1] & 0x3f); // VGM log fix -Valley Bell
                            gb.snd_1.length_counting = true;
                            gb.snd_1.frequency = (int)(((gb.snd_1.reg[4] & 0x7) << 8) | gb.snd_1.reg[3]);
                            gb.snd_1.frequency_counter = gb.snd_1.frequency;
                            gb.snd_1.cycles_left = 0;
                            gb.snd_1.duty_count = 0;
                            gb.snd_1.sweep_enabled = (gb.snd_1.sweep_shift != 0) || (gb.snd_1.sweep_time != 0);
                            if (!gb_dac_enabled(gb.snd_1))
                            {
                                gb.snd_1.on = false;
                            }
                            if (gb.snd_1.sweep_shift > 0)
                            {
                                gb_calculate_next_sweep(gb.snd_1);
                            }

                            if (gb.snd_1.length == 0 && gb.snd_1.length_enabled && (gb.snd_control.cycles & FRAME_CYCLES) == 0)
                            {
                                gb_tick_length(gb.snd_1);
                            }
                        }
                        else
                        {
                            // This condition may not be correct
                            if (!gb.snd_1.sweep_enabled)
                            {
                                gb.snd_1.frequency = (int)(((gb.snd_1.reg[4] & 0x7) << 8) | gb.snd_1.reg[3]);
                            }
                        }
                    }
                    break;

                /*MODE 2 */
                case NR21: /* Sound length/Wave pattern duty (R/W) */
                    gb.snd_2.reg[1] = data;
                    if (gb.snd_control.on != 0)
                    {
                        gb.snd_2.duty = (byte)((data & 0xc0) >> 6);
                    }
                    gb.snd_2.length = (byte)(data & 0x3f);
                    gb.snd_2.length_counting = true;
                    break;
                case NR22: /* Envelope (R/W) */
                    gb.snd_2.reg[2] = data;
                    gb.snd_2.envelope_value = (byte)(data >> 4);
                    gb.snd_2.envelope_direction = (byte)((data & 0x8) != 0 ? 1 : -1);
                    gb.snd_2.envelope_time = (byte)(data & 0x07);
                    if (!gb_dac_enabled(gb.snd_2))
                    {
                        gb.snd_2.on = false;
                    }
                    break;
                case NR23: /* Frequency lo (R/W) */
                    gb.snd_2.reg[3] = data;
                    gb.snd_2.frequency = (int)(((gb.snd_2.reg[4] & 0x7) << 8) | gb.snd_2.reg[3]);
                    break;
                case NR24: /* Frequency hi / Initialize (R/W) */
                    gb.snd_2.reg[4] = data;
                    {
                        boolean length_was_enabled = gb.snd_2.length_enabled;

                        gb.snd_2.length_enabled = ((data & 0x40) != 0) ? true : false;

                        if (!length_was_enabled && (gb.snd_control.cycles & FRAME_CYCLES) == 0 && gb.snd_2.length_counting)
                        {
                            if (gb.snd_2.length_enabled)
                            {
                                gb_tick_length(gb.snd_2);
                            }
                        }

                        if ((data & 0x80) != 0)
                        {
                            gb.snd_2.on = true;
                            gb.snd_2.envelope_enabled = true;
                            gb.snd_2.envelope_value = (byte)(gb.snd_2.reg[2] >> 4);
                            gb.snd_2.envelope_count = gb.snd_2.envelope_time;
                            gb.snd_2.frequency = (int)(((gb.snd_2.reg[4] & 0x7) << 8) | gb.snd_2.reg[3]);
                            gb.snd_2.frequency_counter = gb.snd_2.frequency;
                            gb.snd_2.cycles_left = 0;
                            gb.snd_2.duty_count = 0;
                            gb.snd_2.signal = 0;
                            gb.snd_2.length = (byte)(gb.snd_2.reg[1] & 0x3f); // VGM log fix -Valley Bell
                            gb.snd_2.length_counting = true;

                            if (!gb_dac_enabled(gb.snd_2))
                            {
                                gb.snd_2.on = false;
                            }

                            if (gb.snd_2.length == 0 && gb.snd_2.length_enabled && (gb.snd_control.cycles & FRAME_CYCLES) == 0)
                            {
                                gb_tick_length(gb.snd_2);
                            }
                        }
                        else
                        {
                            gb.snd_2.frequency = (int)(((gb.snd_2.reg[4] & 0x7) << 8) | gb.snd_2.reg[3]);
                        }
                    }
                    break;

                /*MODE 3 */
                case NR30: /* Sound On/Off (R/W) */
                    gb.snd_3.reg[0] = data;
                    if (!gb_dac_enabled(gb.snd_3))
                    {
                        gb.snd_3.on = false;
                    }
                    break;
                case NR31: /* Sound Length (R/W) */
                    gb.snd_3.reg[1] = data;
                    gb.snd_3.length = data;
                    gb.snd_3.length_counting = true;
                    break;
                case NR32: /* Select Output Level */
                    gb.snd_3.reg[2] = data;
                    gb.snd_3.level = (byte)((data & 0x60) >> 5);
                    break;
                case NR33: /* Frequency lo (W) */
                    gb.snd_3.reg[3] = data;
                    gb.snd_3.frequency = (int)(((gb.snd_3.reg[4] & 0x7) << 8) | gb.snd_3.reg[3]);
                    break;
                case NR34: /* Frequency hi / Initialize (W) */
                    gb.snd_3.reg[4] = data;
                    {
                        boolean length_was_enabled = gb.snd_3.length_enabled;

                        gb.snd_3.length_enabled = (data & 0x40) != 0 ? true : false;

                        if (!length_was_enabled && (gb.snd_control.cycles & FRAME_CYCLES) == 0 && gb.snd_3.length_counting)
                        {
                            if (gb.snd_3.length_enabled)
                            {
                                gb_tick_length(gb.snd_3);
                            }
                        }

                        if ((data & 0x80) != 0)
                        {
                            if (gb.snd_3.on && gb.snd_3.frequency_counter == 0x7ff)
                            {
                                gb_corrupt_wave_ram(gb);
                            }
                            gb.snd_3.on = true;
                            gb.snd_3.offset = 0;
                            gb.snd_3.duty = 1;
                            gb.snd_3.duty_count = 0;
                            gb.snd_3.length = gb.snd_3.reg[1];    // VGM log fix -Valley Bell
                            gb.snd_3.length_counting = true;
                            gb.snd_3.frequency = (int)(((gb.snd_3.reg[4] & 0x7) << 8) | gb.snd_3.reg[3]);
                            gb.snd_3.frequency_counter = gb.snd_3.frequency;
                            // There is a tiny bit of delay in starting up the wave channel(?)
                            //
                            // Results from older code where corruption of wave ram was triggered when sample_reading == true:
                            // 4 breaks test 09 (read wram), fixes test 10 (write trigger), breaks test 12 (write wram)
                            // 6 fixes test 09 (read wram), breaks test 10 (write trigger), fixes test 12 (write wram)
                            gb.snd_3.cycles_left = 0 + 6;
                            gb.snd_3.sample_reading = false;

                            if (!gb_dac_enabled(gb.snd_3))
                            {
                                gb.snd_3.on = false;
                            }

                            if (gb.snd_3.length == 0 && gb.snd_3.length_enabled && (gb.snd_control.cycles & FRAME_CYCLES) == 0)
                            {
                                gb_tick_length(gb.snd_3);
                            }
                        }
                        else
                        {
                            gb.snd_3.frequency = (int)(((gb.snd_3.reg[4] & 0x7) << 8) | gb.snd_3.reg[3]);
                        }
                    }
                    break;

                /*MODE 4 */
                case NR41: /* Sound Length (R/W) */
                    gb.snd_4.reg[1] = data;
                    gb.snd_4.length = (byte)(data & 0x3f);
                    gb.snd_4.length_counting = true;
                    break;
                case NR42: /* Envelope (R/W) */
                    gb.snd_4.reg[2] = data;
                    gb.snd_4.envelope_value = (byte)(data >> 4);
                    gb.snd_4.envelope_direction = (byte)((data & 0x8) != 0 ? 1 : -1);
                    gb.snd_4.envelope_time = (byte)(data & 0x07);
                    if (!gb_dac_enabled(gb.snd_4))
                    {
                        gb.snd_4.on = false;
                    }
                    break;
                case NR43: /* Polynomial Counter/Frequency */
                    gb.snd_4.reg[3] = data;
                    gb.snd_4.noise_short = (data & 0x8) != 0;
                    break;
                case NR44: /* Counter/Consecutive / Initialize (R/W)  */
                    gb.snd_4.reg[4] = data;
                    {
                        boolean length_was_enabled = gb.snd_4.length_enabled;

                        gb.snd_4.length_enabled = (data & 0x40) != 0 ? true : false;

                        if (!length_was_enabled && (gb.snd_control.cycles & FRAME_CYCLES) == 0 && gb.snd_4.length_counting)
                        {
                            if (gb.snd_4.length_enabled)
                            {
                                gb_tick_length(gb.snd_4);
                            }
                        }

                        if ((data & 0x80) != 0)
                        {
                            gb.snd_4.on = true;
                            gb.snd_4.envelope_enabled = true;
                            gb.snd_4.envelope_value = (byte)(gb.snd_4.reg[2] >> 4);
                            gb.snd_4.envelope_count = gb.snd_4.envelope_time;
                            gb.snd_4.frequency_counter = 0;
                            gb.snd_4.cycles_left = gb_noise_period_cycles(gb);
                            gb.snd_4.signal = -1;
                            gb.snd_4.noise_lfsr = 0x7fff;
                            gb.snd_4.length = (byte)(gb.snd_4.reg[1] & 0x3f); // VGM log fix -Valley Bell
                            gb.snd_4.length_counting = true;

                            if (!gb_dac_enabled(gb.snd_4))
                            {
                                gb.snd_4.on = false;
                            }

                            if (gb.snd_4.length == 0 && gb.snd_4.length_enabled && (gb.snd_control.cycles & FRAME_CYCLES) == 0)
                            {
                                gb_tick_length(gb.snd_4);
                            }
                        }
                    }
                    break;

                /* CONTROL */
                case NR50: /* Channel Control / On/Off / Volume (R/W)  */
                    gb.snd_control.vol_left = (byte)(data & 0x7);
                    gb.snd_control.vol_right = (byte)((data & 0x70) >> 4);
                    break;
                case NR51: /* Selection of Sound Output Terminal */
                    gb.snd_control.mode1_right = (byte)(data & 0x1);
                    gb.snd_control.mode1_left = (byte)((data & 0x10) >> 4);
                    gb.snd_control.mode2_right = (byte)((data & 0x2) >> 1);
                    gb.snd_control.mode2_left = (byte)((data & 0x20) >> 5);
                    gb.snd_control.mode3_right = (byte)((data & 0x4) >> 2);
                    gb.snd_control.mode3_left = (byte)((data & 0x40) >> 6);
                    gb.snd_control.mode4_right = (byte)((data & 0x8) >> 3);
                    gb.snd_control.mode4_left = (byte)((data & 0x80) >> 7);
                    break;
                case NR52: // Sound On/Off (R/W)
                           // Only bit 7 is writable, writing to bits 0-3 does NOT enable or disable sound. They are read-only.
                    if ((data & 0x80) == 0)
                    {
                        // On DMG the length counters are not affected and not clocked
                        // powering off should actually clear all registers
                        gb_apu_power_off(gb);
                    }
                    else
                    {
                        if (gb.snd_control.on == 0)
                        {
                            // When switching on, the next step should be 0.
                            gb.snd_control.cycles |= 7 * FRAME_CYCLES;
                        }
                    }
                    gb.snd_control.on = (byte)((data & 0x80) != 0 ? 1 : 0);// true : false;
                    gb.snd_regs[NR52] = (byte)(data & 0x80);
                    break;
            }
        }

        private void gb_sound_w(byte ChipID, int offset, byte data)
        {
            gb_sound_t gb = GBSoundData[ChipID];

            //gb_update_state(gb, 0);

            if (offset < AUD3W0)
            {
                if (gb.gbMode == GBMODE_DMG)
                {
                    /* Only register NR52 is accessible if the sound controller is disabled */
                    if (gb.snd_control.on == 0 && offset != NR52 && offset != NR11 && offset != NR21 && offset != NR31 && offset != NR41)
                        return;
                }
                else if (gb.gbMode == GBMODE_CGB04)
                {
                    /* Only register NR52 is accessible if the sound controller is disabled */
                    if (gb.snd_control.on == 0 && offset != NR52)
                        return;
                }

                gb_sound_w_internal(gb, (byte)offset, data);
            }
            else if (offset <= AUD3WF)
            {
                gb_wave_w(ChipID, offset - AUD3W0, data);
            }
        }

        public void gb_corrupt_wave_ram(gb_sound_t gb)
        {
            if (gb.gbMode != GBMODE_DMG)
                return;

            if (gb.snd_3.offset < 8)
            {
                gb.snd_regs[AUD3W0] = gb.snd_regs[AUD3W0 + (gb.snd_3.offset / 2)];
            }
            else
            {
                int i;
                for (i = 0; i < 4; i++)
                {
                    gb.snd_regs[AUD3W0 + i] = gb.snd_regs[AUD3W0 + ((gb.snd_3.offset / 2) & ~0x03) + i];
                }
            }
        }


        public void gb_apu_power_off(gb_sound_t gb)
        {
            int i;

            switch (gb.gbMode)
            {
                case GBMODE_DMG:
                    gb_sound_w_internal(gb, (byte)NR10, (byte)0x00);
                    gb.snd_1.duty = 0;
                    gb.snd_regs[NR11] = 0;
                    gb_sound_w_internal(gb, (byte)NR12, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR13, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR14, (byte)0x00);
                    gb.snd_1.length_counting = false;
                    gb.snd_1.sweep_neg_mode_used = false;

                    gb.snd_regs[NR21] = 0;
                    gb_sound_w_internal(gb, (byte)NR22, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR23, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR24, (byte)0x00);
                    gb.snd_2.length_counting = false;

                    gb_sound_w_internal(gb, (byte)NR30, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR32, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR33, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR34, (byte)0x00);
                    gb.snd_3.length_counting = false;
                    gb.snd_3.current_sample = 0;

                    gb.snd_regs[NR41] = 0;
                    gb_sound_w_internal(gb, (byte)NR42, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR43, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR44, (byte)0x00);
                    gb.snd_4.length_counting = false;
                    gb.snd_4.cycles_left = gb_noise_period_cycles(gb);
                    break;
                case GBMODE_CGB04:
                    gb_sound_w_internal(gb, (byte)NR10, (byte)0x00);
                    gb.snd_1.duty = 0;
                    gb_sound_w_internal(gb, (byte)NR11, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR12, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR13, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR14, (byte)0x00);
                    gb.snd_1.length_counting = false;
                    gb.snd_1.sweep_neg_mode_used = false;

                    gb_sound_w_internal(gb, (byte)NR21, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR22, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR23, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR24, (byte)0x00);
                    gb.snd_2.length_counting = false;

                    gb_sound_w_internal(gb, (byte)NR30, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR31, (byte)0x00);
                    gb_sound_w_internal(gb,(byte) NR32, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR33, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR34, (byte)0x00);
                    gb.snd_3.length_counting = false;
                    gb.snd_3.current_sample = 0;

                    gb_sound_w_internal(gb, (byte)NR41, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR42, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR43, (byte)0x00);
                    gb_sound_w_internal(gb, (byte)NR44, (byte)0x00);
                    gb.snd_4.length_counting = false;
                    gb.snd_4.cycles_left = gb_noise_period_cycles(gb);
                    break;
            }

            gb.snd_1.on = false;
            gb.snd_2.on = false;
            gb.snd_3.on = false;
            gb.snd_4.on = false;

            gb.snd_control.wave_ram_locked = false;

            for (i = NR44 + 1; i < NR52; i++)
            {
                gb_sound_w_internal(gb, (byte)i, (byte) 0x00);
            }

            return;
        }


        public void gb_tick_length(SOUND snd)
        {
            if (snd.length_enabled)
            {
                snd.length = (byte)((snd.length + 1) & snd.length_mask);
                if (snd.length == 0)
                {
                    snd.on = false;
                    snd.length_counting = false;
                }
            }
        }


        public int gb_calculate_next_sweep(SOUND snd)
        {
            int new_frequency;
            snd.sweep_neg_mode_used = (snd.sweep_direction < 0);
            new_frequency = snd.frequency + snd.sweep_direction * (snd.frequency >> snd.sweep_shift);

            if (new_frequency > 0x7FF)
            {
                snd.on = false;
            }

            return new_frequency;
        }


        private void gb_apply_next_sweep(SOUND snd)
        {
            int new_frequency = gb_calculate_next_sweep(snd);

            if (snd.on && snd.sweep_shift > 0)
            {
                snd.frequency = (int)new_frequency;
                snd.reg[3] = (byte)(snd.frequency & 0xFF);
            }
        }


        private void gb_tick_sweep(SOUND snd)
        {
            snd.sweep_count = (byte)((snd.sweep_count - 1) & 0x07);
            if (snd.sweep_count == 0)
            {
                snd.sweep_count = snd.sweep_time;

                if (snd.sweep_enabled && snd.sweep_time > 0)
                {

                    gb_apply_next_sweep(snd);

                    gb_calculate_next_sweep(snd);
                }
            }
        }


        private void gb_tick_envelope(SOUND snd)
        {
            if (snd.envelope_enabled)
            {
                snd.envelope_count = (byte)((snd.envelope_count - 1) & 0x07);

                if (snd.envelope_count == 0)
                {
                    snd.envelope_count = snd.envelope_time;

                    if (snd.envelope_count != 0)
                    {
                        byte new_envelope_value = (byte)(snd.envelope_value + snd.envelope_direction);

                        if (new_envelope_value >= 0 && new_envelope_value <= 15)
                        {
                            snd.envelope_value = new_envelope_value;
                        }
                        else
                        {
                            snd.envelope_enabled = false;
                        }
                    }
                }
            }
        }


        public boolean gb_dac_enabled(SOUND snd)
        {
            return ((snd.channel != 3) ? (snd.reg[2] & 0xF8) : (snd.reg[0] & 0x80)) != 0;
        }


        private void gb_update_square_channel(SOUND snd, int cycles)
        {
            if (snd.on)
            {
                // compensate for leftover cycles
                if (snd.cycles_left > 0)
                {
                    cycles += snd.cycles_left;
                    snd.cycles_left = 0;
                }

                while (cycles > 0)
                {
                    // Emit sample(s)
                    if (cycles < 4)
                    {
                        snd.cycles_left = cycles;
                        cycles = 0;
                    }
                    else
                    {
                        cycles -= 4;
                        snd.frequency_counter = (int)((snd.frequency_counter + 1) & 0x7FF);
                        if (snd.frequency_counter == 0)
                        {
                            snd.duty_count = (snd.duty_count + 1) & 0x07;
                            snd.signal = (byte)(wave_duty_table[snd.duty][snd.duty_count]);

                            // Reload frequency counter
                            snd.frequency_counter = snd.frequency;
                        }
                    }
                }
            }
        }


        private void gb_update_wave_channel(gb_sound_t gb, SOUND snd, int cycles)
        {
            if (snd.on)
            {
                // compensate for leftover cycles
                if (snd.cycles_left > 0)
                {
                    cycles += snd.cycles_left;
                    snd.cycles_left = 0;
                }

                while (cycles > 0)
                {
                    // Emit current sample

                    // cycles -= 2
                    if (cycles < 2)
                    {
                        snd.cycles_left = cycles;
                        cycles = 0;
                    }
                    else
                    {
                        cycles -= 2;

                        // Calculate next state
                        snd.frequency_counter = (int)((snd.frequency_counter + 1) & 0x7FF);
                        snd.sample_reading = false;
                        if (gb.gbMode == GBMODE_DMG && snd.frequency_counter == 0x7ff)
                            snd.offset = (byte)((snd.offset + 1) & 0x1F);
                        if (snd.frequency_counter == 0)
                        {
                            // Read next sample
                            snd.sample_reading = true;
                            if (gb.gbMode == GBMODE_CGB04)
                                snd.offset = (byte)((snd.offset + 1) & 0x1F);
                            snd.current_sample = (byte)(gb.snd_regs[AUD3W0 + (snd.offset / 2)]);
                            if ((snd.offset & 0x01) == 0)
                            {
                                snd.current_sample >>= 4;
                            }
                            snd.current_sample = (byte)((snd.current_sample & 0x0F) - 8);
                            if (BoostWaveChn != 0)

                                snd.current_sample <<= 1;

                            snd.signal = (byte)(snd.level != 0 ? snd.current_sample / (1 << (snd.level - 1)) : 0);

                            // Reload frequency counter
                            snd.frequency_counter = snd.frequency;
                        }
                    }
                }
            }
        }


        private void gb_update_noise_channel(gb_sound_t gb, SOUND snd, int cycles)
        {
            while (cycles > 0)
            {
                if (cycles < snd.cycles_left)
                {
                    if (snd.on)
                    {
                        // generate samples
                    }

                    snd.cycles_left -= cycles;
                    cycles = 0;
                }
                else
                {
                    int feedback;

                    if (snd.on)
                    {
                        // generate samples
                    }

                    cycles -= snd.cycles_left;
                    snd.cycles_left = gb_noise_period_cycles(gb);

                    /* Using a Polynomial Counter (aka Linear Feedback Shift Register)
                     Mode 4 has a 15 bit counter so we need to shift the
                     bits around accordingly */
                    feedback = (int)(((snd.noise_lfsr >> 1) ^ snd.noise_lfsr) & 1);
                    snd.noise_lfsr = (int)((snd.noise_lfsr >> 1) | (feedback << 14));
                    if (snd.noise_short)
                    {
                        snd.noise_lfsr = (int)((snd.noise_lfsr & ~(1 << 6)) | (feedback << 6));
                    }
                    snd.signal = (byte)((snd.noise_lfsr & 1) != 0 ? -1 : 1);
                }
            }
        }


        private void gb_update_state(gb_sound_t gb, int cycles)
        {
            int old_cycles;

            if (gb.snd_control.on == 0)
                return;

            old_cycles = gb.snd_control.cycles;
            gb.snd_control.cycles += cycles;

            if ((old_cycles / FRAME_CYCLES) != (gb.snd_control.cycles / FRAME_CYCLES))
            {
                // Left over cycles in current frame
                int cycles_current_frame = FRAME_CYCLES - (old_cycles & (FRAME_CYCLES - 1));

                gb_update_square_channel(gb.snd_1, cycles_current_frame);
                gb_update_square_channel(gb.snd_2, cycles_current_frame);
                gb_update_wave_channel(gb, gb.snd_3, cycles_current_frame);
                gb_update_noise_channel(gb, gb.snd_4, cycles_current_frame);

                cycles -= cycles_current_frame;

                // Switch to next frame
                switch ((gb.snd_control.cycles / FRAME_CYCLES) & 0x07)
                {
                    case 0:
                        // length
                        gb_tick_length(gb.snd_1);
                        gb_tick_length(gb.snd_2);
                        gb_tick_length(gb.snd_3);
                        gb_tick_length(gb.snd_4);
                        break;
                    case 2:
                        // sweep
                        gb_tick_sweep(gb.snd_1);
                        // length
                        gb_tick_length(gb.snd_1);
                        gb_tick_length(gb.snd_2);
                        gb_tick_length(gb.snd_3);
                        gb_tick_length(gb.snd_4);
                        break;
                    case 4:
                        // length
                        gb_tick_length(gb.snd_1);
                        gb_tick_length(gb.snd_2);
                        gb_tick_length(gb.snd_3);
                        gb_tick_length(gb.snd_4);
                        break;
                    case 6:
                        // sweep
                        gb_tick_sweep(gb.snd_1);
                        // length
                        gb_tick_length(gb.snd_1);
                        gb_tick_length(gb.snd_2);
                        gb_tick_length(gb.snd_3);
                        gb_tick_length(gb.snd_4);
                        break;
                    case 7:
                        // update envelope
                        gb_tick_envelope(gb.snd_1);
                        gb_tick_envelope(gb.snd_2);
                        gb_tick_envelope(gb.snd_4);
                        break;
                }
            }

            gb_update_square_channel(gb.snd_1, cycles);
            gb_update_square_channel(gb.snd_2, cycles);
            gb_update_wave_channel(gb, gb.snd_3, cycles);
            gb_update_noise_channel(gb, gb.snd_4, cycles);
        }


        private int[] divisor = new int[] { 8, 16, 32, 48, 64, 80, 96, 112 };

        @Override public String getName() { return "Gameboy DMG"; }
        @Override public String getShortName() { return "DMG"; }

        public int gb_noise_period_cycles(gb_sound_t gb)
        {
            return (int)(divisor[gb.snd_4.reg[3] & 7] << (gb.snd_4.reg[3] >> 4));
        }


        public void gameboy_update(byte ChipID, int[][] outputs, int samples)
        {
            gb_sound_t gb = GBSoundData[ChipID];
            int sample, left, right;
            int i;
            int cycles;

            for (i = 0; i < samples; i++)
            {
                left = right = 0;

                gb.cycleCntr.val += gb.cycleCntr.inc;
                cycles = (int)(gb.cycleCntr.val >> RC_SHIFT);
                gb.cycleCntr.val &= ((1 << RC_SHIFT) - 1);
                gb_update_state(gb, cycles);

                /* Mode 1 - Wave with Envelope and Sweep */
                if (gb.snd_1.on && gb.snd_1.Muted == 0)
                {
                    sample = gb.snd_1.signal * gb.snd_1.envelope_value;

                    if (gb.snd_control.mode1_left != 0)
                        left += sample;
                    if (gb.snd_control.mode1_right != 0)
                        right += sample;
                }

                /* Mode 2 - Wave with Envelope */
                if (gb.snd_2.on && gb.snd_2.Muted == 0)
                {
                    sample = gb.snd_2.signal * gb.snd_2.envelope_value;
                    if (gb.snd_control.mode2_left != 0)
                        left += sample;
                    if (gb.snd_control.mode2_right != 0)
                        right += sample;
                }

                /* Mode 3 - Wave patterns from WaveRAM */
                if (gb.snd_3.on && gb.snd_3.Muted == 0)
                {
                    sample = gb.snd_3.signal;
                    if (gb.snd_control.mode3_left != 0)
                        left += sample;
                    if (gb.snd_control.mode3_right != 0)
                        right += sample;
                }

                /* Mode 4 - Noise with Envelope */
                if (gb.snd_4.on && gb.snd_4.Muted == 0)
                {
                    sample = gb.snd_4.signal * gb.snd_4.envelope_value;
                    if (gb.snd_control.mode4_left != 0)
                        left += sample;
                    if (gb.snd_control.mode4_right != 0)
                        right += sample;
                }

                /* Adjust for master volume */
                left *= gb.snd_control.vol_left;
                right *= gb.snd_control.vol_right;

                /* pump up the volume */
                left <<= 6;
                right <<= 6;

                /* Update the buffers */
                outputs[0][i] = left;
                outputs[1][i] = right;
            }

            gb.snd_regs[NR52] = (byte)((gb.snd_regs[NR52] & 0xf0)
                | (gb.snd_1.on ? 1 : 0)
                | ((gb.snd_2.on ? 1 : 0) << 1)
                | ((gb.snd_3.on ? 1 : 0) << 2)
                | ((gb.snd_4.on ? 1 : 0) << 3));
        }


        public int device_start_gameboy_sound(byte ChipID, int clock)
        {
            gb_sound_t gb;

            if (ChipID >= MAX_CHIPS)
                return 0;

            gb = GBSoundData[ChipID];
            gb.cycleCntr = new RATIO_CNTR();
            gb.snd_1 = new SOUND();
            gb.snd_2 = new SOUND();
            gb.snd_3 = new SOUND();
            gb.snd_4 = new SOUND();
            gb.snd_control = new SOUNDC();
            //memset(gb, 0x00, sizeof(gb_sound_t));

            gb.rate = (int)((clock & 0x7FFFFFFF) / 64);
            if (((CHIP_SAMPLING_MODE & 0x01) != 0 && gb.rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
                gb.rate = (int)CHIP_SAMPLE_RATE;

            gb.gbMode = (byte)((clock & 0x80000000) != 0 ? GBMODE_CGB04 : GBMODE_DMG);
            RC_SET_RATIO(gb.cycleCntr, (int)(clock & 0x7FFFFFFF), gb.rate);

            gameboy_sound_set_mute_mask(ChipID, 0x00);
            //gb.BoostWaveChn = 0x00;

            return (int)gb.rate;
        }

        public void device_stop_gameboy_sound(byte ChipID)
        {
            return;
        }

        public void device_reset_gameboy_sound(byte ChipID)
        {
            gb_sound_t gb = GBSoundData[ChipID];
            int muteMask;

            muteMask = gameboy_sound_get_mute_mask(ChipID);

            gb.cycleCntr.val = 0;

            gb.snd_1 = new SOUND();
            gb.snd_2 = new SOUND();
            gb.snd_3 = new SOUND();
            gb.snd_4 = new SOUND();
            //memset(&gb.snd_1, 0, sizeof(gb.snd_1));
            //memset(&gb.snd_2, 0, sizeof(gb.snd_2));
            //memset(&gb.snd_3, 0, sizeof(gb.snd_3));
            //memset(&gb.snd_4, 0, sizeof(gb.snd_4));

            gameboy_sound_set_mute_mask(ChipID, muteMask);

            gb.snd_1.channel = 1;
            gb.snd_1.length_mask = 0x3F;
            gb.snd_2.channel = 2;
            gb.snd_2.length_mask = 0x3F;
            gb.snd_3.channel = 3;
            gb.snd_3.length_mask = (byte) 0xFF;
            gb.snd_4.channel = 4;
            gb.snd_4.length_mask = 0x3F;

            gb_sound_w_internal(gb, (byte) NR52, (byte) 0x00);
            switch (gb.gbMode)
            {
                case GBMODE_DMG:
                    gb.snd_regs[AUD3W0] = (byte) 0xac;
                    gb.snd_regs[AUD3W1] = (byte) 0xdd;
                    gb.snd_regs[AUD3W2] = (byte) 0xda;
                    gb.snd_regs[AUD3W3] = 0x48;
                    gb.snd_regs[AUD3W4] = 0x36;
                    gb.snd_regs[AUD3W5] = 0x02;
                    gb.snd_regs[AUD3W6] = (byte) 0xcf;
                    gb.snd_regs[AUD3W7] = 0x16;
                    gb.snd_regs[AUD3W8] = 0x2c;
                    gb.snd_regs[AUD3W9] = 0x04;
                    gb.snd_regs[AUD3WA] = (byte)0xe5;
                    gb.snd_regs[AUD3WB] = 0x2c;
                    gb.snd_regs[AUD3WC] = (byte)0xac;
                    gb.snd_regs[AUD3WD] = (byte)0xdd;
                    gb.snd_regs[AUD3WE] = (byte)0xda;
                    gb.snd_regs[AUD3WF] = 0x48;
                    break;
                case GBMODE_CGB04:
                    gb.snd_regs[AUD3W0] = 0x00;
                    gb.snd_regs[AUD3W1] = (byte)0xFF;
                    gb.snd_regs[AUD3W2] = 0x00;
                    gb.snd_regs[AUD3W3] = (byte)0xFF;
                    gb.snd_regs[AUD3W4] = 0x00;
                    gb.snd_regs[AUD3W5] = (byte)0xFF;
                    gb.snd_regs[AUD3W6] = 0x00;
                    gb.snd_regs[AUD3W7] = (byte)0xFF;
                    gb.snd_regs[AUD3W8] = 0x00;
                    gb.snd_regs[AUD3W9] = (byte)0xFF;
                    gb.snd_regs[AUD3WA] = 0x00;
                    gb.snd_regs[AUD3WB] = (byte)0xFF;
                    gb.snd_regs[AUD3WC] = 0x00;
                    gb.snd_regs[AUD3WD] = (byte)0xFF;
                    gb.snd_regs[AUD3WE] = 0x00;
                    gb.snd_regs[AUD3WF] = (byte)0xFF;
                    break;
            }

            return;
        }

        public void gameboy_sound_set_mute_mask(byte ChipID, int MuteMask)
        {
            gb_sound_t gb = GBSoundData[ChipID];
            if (gb == null) return;

            if (gb.snd_1 != null) gb.snd_1.Muted = (byte)((MuteMask >> 0) & 0x01);
            if (gb.snd_2 != null) gb.snd_2.Muted = (byte)((MuteMask >> 1) & 0x01);
            if (gb.snd_3 != null) gb.snd_3.Muted = (byte)((MuteMask >> 2) & 0x01);
            if (gb.snd_4 != null) gb.snd_4.Muted = (byte)((MuteMask >> 3) & 0x01);

            return;
        }

        public int gameboy_sound_get_mute_mask(byte ChipID)
        {
            gb_sound_t gb = GBSoundData[ChipID];
            if (gb == null) return 0;
            if (gb.snd_1 == null) return 0;
            if (gb.snd_2 == null) return 0;
            if (gb.snd_3 == null) return 0;
            if (gb.snd_4 == null) return 0;

            int muteMask;

            muteMask = (int)((gb.snd_1.Muted << 0) |
                        (gb.snd_2.Muted << 1) |
                        (gb.snd_3.Muted << 2) |
                        (gb.snd_4.Muted << 3));

            return muteMask;
        }

        public void gameboy_sound_set_options(byte Flags)
        {
            BoostWaveChn = (byte)((Flags & 0x01) >> 0);

            return;
        }

        @Override public int Write(byte ChipID, int port, int adr, int data)
        {
            gb_sound_w(ChipID, (int)adr, (byte)data);
            return 0;
        }

        public gb_sound_t GetSoundData(byte chipId)
        {
            return GBSoundData[chipId];
        }
    }
