﻿package MDSound;

public class ymf271 extends Instrument
    {
        @Override public void Reset(byte ChipID)
        {
            device_reset_ymf271(ChipID);

            visVolume = new int[][][] {
                new int[][] { new int[] { 0, 0 } }
                , new int[][] { new int[] { 0, 0 } }
            };
        }

        @Override public int Start(byte ChipID, int clock)
        {
            return (int)device_start_ymf271(ChipID, 16934400);
        }

        @Override public int Start(byte ChipID, int clock, int FMClockValue, Object... option)
        {
            return (int)device_start_ymf271(ChipID, (int)FMClockValue);
        }

        @Override public void Stop(byte ChipID)
        {
            device_stop_ymf271(ChipID);
        }

        @Override public void Update(byte ChipID, int[][] outputs, int samples)
        {
            ymf271_update(ChipID, outputs, samples);

            visVolume[ChipID][0][0] = outputs[0][0];
            visVolume[ChipID][0][1] = outputs[1][0];
        }

        private int YMF271_Write(byte ChipID, int Port, byte Offset, byte Data)
        {
            ymf271_w(ChipID, (int)((Port << 1) | 0x00), Offset);
            ymf271_w(ChipID, (int)((Port << 1) | 0x01), Data);
            return 0;
        }



        //#pragma once

        //#include "devcb.h"

        //typedef struct _ymf271_interface ymf271_interface;
        //struct _ymf271_interface
        //{
        //	//devcb_read8 ext_read;		/* external memory read */
        //	//devcb_write8 ext_write;	/* external memory write */
        //	//void (*irq_callback)(final device_config *device, int state);	/* irq callback */
        //	void (*irq_callback)(int state);	/* irq callback */
        //};

        /*READ8_DEVICE_HANDLER( ymf271_r );
        WRITE8_DEVICE_HANDLER( ymf271_w );

        DEVICE_GET_INFO( ymf271 );
        // #define SOUND_YMF271 DEVICE_GET_INFO_NAME( ymf271 )*/

        //private void ymf271_update(byte ChipID, int[][] outputs, int samples) { }
        //private int device_start_ymf271(byte ChipID, int clock) { return 0; }
        //private void device_stop_ymf271(byte ChipID) { }
        //private void device_reset_ymf271(byte ChipID) { }

        //private byte ymf271_r(byte ChipID, int offset) { return 0; }
        //private void ymf271_w(byte ChipID, int offset, byte data) { }
        //private void ymf271_write_rom(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData) { }
        //private void ymf271_set_mute_mask(byte ChipID, int MuteMask) { }




        /*
    Yamaha YMF271-F "OPX" emulator v0.1
    By R. Belmont.
    Based in part on YMF278B emulator by R. Belmont and O. Galibert.
    12June04 update by Toshiaki Nijiura
    Copyright R. Belmont.

    This software is dual-licensed: it may be used in MAME and properly licensed
    MAME derivatives under the terms of the MAME license.  For use outside of
    MAME and properly licensed derivatives, it is available under the
    terms of the GNU Lesser General Public License (LGPL), version 2.1.
    You may read the LGPL at http://www.gnu.org/licenses/lgpl.html

    TODO:
    - A/L bit (alternate loop)
    - EN and EXT Out bits
    - Src B and Src NOTE bits
    - statusreg Busy and End bits
    - timer register 0x11
    - ch2/ch3 (4 speakers)
    - PFM (FM using external PCM waveform)
    - detune (should be same as on other Yamaha chips)
    - Acc On bit (some sound effects in viprp1?). The documentation says
      "determines if slot output is accumulated(1), or output directly(0)"
    - Is memory handling 100% correct? At the moment, seibuspi.c is the only
      hardware currently emulated that uses external handlers.
*/

        //# include <math.h>
        //# include "mamedef.h"
        //#include "sndintrf.h"
        //#include "streams.h"
        //# ifdef _DEBUG
        //# include <stdio.h>
        //#endif
        //# include <stdlib.h>
        //# include <string.h>	// for memset
        //# include <stddef.h>	// for NULL
        //# include "../stdbool.h"
        //# include "ymf271.h"

        //#define DEVCB_NULL							{ DEVCB_TYPE_NULL }
        //#define DEVCB_NULL							DEVCB_TYPE_NULL
        //#define DEVCB_TYPE_NULL				(0)

        private final int VERBOSE = (1);
        private final int STD_CLOCK = (16934400);

        private final int MAXOUT = (+32767);
        private final int MINOUT = (-32768);

        private final int SIN_BITS = 10;
        private final int SIN_LEN = (1 << SIN_BITS);
        private final int SIN_MASK = (SIN_LEN - 1);

        private final int LFO_LENGTH = 256;
        private final int LFO_SHIFT = 8;
        private final double PLFO_MAX = (+1.0);
        private final double PLFO_MIN = (-1.0);
        private final int ALFO_MAX = (+65536);
        private final int ALFO_MIN = (0);

        private final int ENV_ATTACK = 0;
        private final int ENV_DECAY1 = 1;
        private final int ENV_DECAY2 = 2;
        private final int ENV_RELEASE = 3;
        private final int OP_INPUT_FEEDBACK = -1;
        private final int OP_INPUT_NONE = -2;
        private final int ENV_VOLUME_SHIFT = 16;
        private final double INF = -1.0;

        private double[] ARTime = new double[]{
            INF,        INF,        INF,        INF,        6188.12,    4980.68,    4144.76,    3541.04,
            3094.06,    2490.34,    2072.38,    1770.52,    1547.03,    1245.17,    1036.19,    885.26,
            773.51,     622.59,     518.10,     441.63,     386.76,     311.29,     259.05,     221.32,
            193.38,     155.65,     129.52,     110.66,     96.69,      77.82,      64.76,      55.33,
            48.34,      38.91,      32.38,      27.66,      24.17,      19.46,      16.19,      13.83,
            12.09,      9.73,       8.10,       6.92,       6.04,       4.86,       4.05,       3.46,
            3.02,       2.47,       2.14,       1.88,       1.70,       1.38,       1.16,       1.02,
            0.88,       0.70,       0.57,       0.48,       0.43,       0.43,       0.43,       0.07
        };

        private double[] DCTime = new double[]{
            INF,        INF,        INF,        INF,        93599.64,   74837.91,   62392.02,   53475.56,
            46799.82,   37418.96,   31196.01,   26737.78,   23399.91,   18709.48,   15598.00,   13368.89,
            11699.95,   9354.74,    7799.00,    6684.44,    5849.98,    4677.37,    3899.50,    3342.22,
            2924.99,    2338.68,    1949.75,    1671.11,    1462.49,    1169.34,    974.88,     835.56,
            731.25,     584.67,     487.44,     417.78,     365.62,     292.34,     243.72,     208.89,
            182.81,     146.17,     121.86,     104.44,     91.41,      73.08,      60.93,      52.22,
            45.69,      36.55,      33.85,      26.09,      22.83,      18.28,      15.22,      13.03,
            11.41,      9.12,       7.60,       6.51,       5.69,       5.69,       5.69,       5.69
        };

        /* Notes about the LFO Frequency Table below:

            There are 2 known errors in the LFO table listed in the original manual.

            Both 201 & 202 are listed as 3.74490.  202 has been computed/corrected to 3.91513
            232 was listed as 13.35547 but has been replaced with the correct value of 14.35547.

          Corrections are computed values based on formulas by Olivier Galibert & Nicola Salmoria listed below:

        LFO period seems easy to compute:

        Olivier Galibert's version                       Nicola Salmoria's version

        int lfo_period(int entry)             or         int calc_lfo_period(int entry)
        {                                                {
          int ma, ex;                                      entry = 256 - entry;
          entry = 256-entry;
          ma = entry & 15;                                 if (entry < 16)
                                                           {
          ex = entry >> 4;                                    return (entry & 0x0f) << 7;
          if(ex)                                           }
            return (ma | 16) << (ex+6);                    else
          else                                             {
            return ma << 7;                                   int shift = 6 + (entry >> 4);
        }                                                     return (0x10 + (entry & 0x0f)) << shift;
                                                           }
        lfo_freq = 44100 / lfo_period                    }

        */

        private double[] LFO_frequency_table = new double[]{
            0.00066,    0.00068,    0.00070,    0.00073,    0.00075,    0.00078,    0.00081,    0.00084,
            0.00088,    0.00091,    0.00096,    0.00100,    0.00105,    0.00111,    0.00117,    0.00124,
            0.00131,    0.00136,    0.00140,    0.00145,    0.00150,    0.00156,    0.00162,    0.00168,
            0.00175,    0.00183,    0.00191,    0.00200,    0.00210,    0.00221,    0.00234,    0.00247,
            0.00263,    0.00271,    0.00280,    0.00290,    0.00300,    0.00312,    0.00324,    0.00336,
            0.00350,    0.00366,    0.00382,    0.00401,    0.00421,    0.00443,    0.00467,    0.00495,
            0.00526,    0.00543,    0.00561,    0.00580,    0.00601,    0.00623,    0.00647,    0.00673,
            0.00701,    0.00731,    0.00765,    0.00801,    0.00841,    0.00885,    0.00935,    0.00990,
            0.01051,    0.01085,    0.01122,    0.01160,    0.01202,    0.01246,    0.01294,    0.01346,
            0.01402,    0.01463,    0.01529,    0.01602,    0.01682,    0.01771,    0.01869,    0.01979,
            0.02103,    0.02171,    0.02243,    0.02320,    0.02403,    0.02492,    0.02588,    0.02692,
            0.02804,    0.02926,    0.03059,    0.03204,    0.03365,    0.03542,    0.03738,    0.03958,
            0.04206,    0.04341,    0.04486,    0.04641,    0.04807,    0.04985,    0.05176,    0.05383,
            0.05608,    0.05851,    0.06117,    0.06409,    0.06729,    0.07083,    0.07477,    0.07917,
            0.08411,    0.08683,    0.08972,    0.09282,    0.09613,    0.09969,    0.10353,    0.10767,
            0.11215,    0.11703,    0.12235,    0.12817,    0.13458,    0.14167,    0.14954,    0.15833,
            0.16823,    0.17365,    0.17944,    0.18563,    0.19226,    0.19938,    0.20705,    0.21533,
            0.22430,    0.23406,    0.24470,    0.25635,    0.26917,    0.28333,    0.29907,    0.31666,
            0.33646,    0.34731,    0.35889,    0.37126,    0.38452,    0.39876,    0.41410,    0.43066,
            0.44861,    0.46811,    0.48939,    0.51270,    0.53833,    0.56666,    0.59814,    0.63333,
            0.67291,    0.69462,    0.71777,    0.74252,    0.76904,    0.79753,    0.82820,    0.86133,
            0.89722,    0.93623,    0.97878,    1.02539,    1.07666,    1.13333,    1.19629,    1.26666,
            1.34583,    1.38924,    1.43555,    1.48505,    1.53809,    1.59509,    1.65640,    1.72266,
            1.79443,    1.87245,    1.95756,    2.05078,    2.15332,    2.26665,    2.39258,    2.53332,
            2.69165,    2.77848,    2.87109,    2.97010,    3.07617,    3.19010,    3.31280,    3.44531,
            3.58887,    3.74490,    3.91513,    4.10156,    4.30664,    4.53331,    4.78516,    5.06664,
            5.38330,    5.55696,    5.74219,    5.94019,    6.15234,    6.38021,    6.62560,    6.89062,
            7.17773,    7.48981,    7.83026,    8.20312,    8.61328,    9.06661,    9.57031,    10.13327,
            10.76660,   11.11391,   11.48438,   11.88039,   12.30469,   12.76042,   13.25120,   13.78125,
            14.35547,   14.97962,   15.66051,   16.40625,   17.22656,   18.13322,   19.14062,   20.26654,
            21.53320,   22.96875,   24.60938,   26.50240,   28.71094,   31.32102,   34.45312,   38.28125,
            43.06641,   49.21875,   57.42188,   68.90625,   86.13281,   114.84375,  172.26562,  344.53125
        };

        private int[][] RKS_Table = new int[][]{
            new int[]{  0,  0,  0,  0,  0,  2,  4,  8 },
            new int[]{  0,  0,  0,  0,  1,  3,  5,  9 },
            new int[]{  0,  0,  0,  1,  2,  4,  6, 10 },
            new int[]{  0,  0,  0,  1,  3,  5,  7, 11 },
            new int[]{  0,  0,  1,  2,  4,  6,  8, 12 },
            new int[]{  0,  0,  1,  2,  5,  7,  9, 13 },
            new int[]{  0,  0,  1,  3,  6,  8, 10, 14 },
            new int[]{  0,  0,  1,  3,  7,  9, 11, 15 },
            new int[]{  0,  1,  2,  4,  8, 10, 12, 16 },
            new int[]{  0,  1,  2,  4,  9, 11, 13, 17 },
            new int[]{  0,  1,  2,  5, 10, 12, 14, 18 },
            new int[]{  0,  1,  2,  5, 11, 13, 15, 19 },
            new int[]{  0,  1,  3,  6, 12, 14, 16, 20 },
            new int[]{  0,  1,  3,  6, 13, 15, 17, 21 },
            new int[]{  0,  1,  3,  7, 14, 16, 18, 22 },
            new int[]{  0,  1,  3,  7, 15, 17, 19, 23 },
            new int[]{  0,  2,  4,  8, 16, 18, 20, 24 },
            new int[]{  0,  2,  4,  8, 17, 19, 21, 25 },
            new int[]{  0,  2,  4,  9, 18, 20, 22, 26 },
            new int[]{  0,  2,  4,  9, 19, 21, 23, 27 },
            new int[]{  0,  2,  5, 10, 20, 22, 24, 28 },
            new int[]{  0,  2,  5, 10, 21, 23, 25, 29 },
            new int[]{  0,  2,  5, 11, 22, 24, 26, 30 },
            new int[]{  0,  2,  5, 11, 23, 25, 27, 31 },
            new int[]{  0,  3,  6, 12, 24, 26, 28, 31 },
            new int[]{  0,  3,  6, 12, 25, 27, 29, 31 },
            new int[]{  0,  3,  6, 13, 26, 28, 30, 31 },
            new int[]{  0,  3,  6, 13, 27, 29, 31, 31 },
            new int[]{  0,  3,  7, 14, 28, 30, 31, 31 },
            new int[]{  0,  3,  7, 14, 29, 31, 31, 31 },
            new int[]{  0,  3,  7, 15, 30, 31, 31, 31 },
            new int[]{  0,  3,  7, 15, 31, 31, 31, 31 }
        };

        private double[] multiple_table = new double[] { 0.5, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };
        private double[] pow_table = new double[] { 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 0.5, 1, 2, 4, 8, 16, 32, 64 };
        private double[] fs_frequency = new double[] { 1.0 / 1.0, 1.0 / 2.0, 1.0 / 4.0, 1.0 / 8.0 };
        private double[] channel_attenuation_table = new double[]{
            0.0, 2.5, 6.0, 8.5, 12.0, 14.5, 18.1, 20.6, 24.1, 26.6, 30.1, 32.6, 36.1, 96.1, 96.1, 96.1
        };

        private int[] modulation_level = new int[] { 16, 8, 4, 2, 1, 32, 64, 128 };

        // feedback_level * 16
        private int[] feedback_level = new int[] { 0, 1, 2, 4, 8, 16, 32, 64 };

        // slot mapping assists
        private int[] fm_tab = new int[] { 0, 1, 2, -1, 3, 4, 5, -1, 6, 7, 8, -1, 9, 10, 11, -1 };
        private int[] pcm_tab = new int[] { 0, 4, 8, -1, 12, 16, 20, -1, 24, 28, 32, -1, 36, 40, 44, -1 };


        public class YMF271Slot
        {

            public byte ext_en;
            public byte ext_out;
            public byte lfoFreq;
            public byte lfowave;
            public byte pms, ams;
            public byte detune;
            public byte multiple;
            public byte tl;
            public byte keyscale;
            public byte ar;
            public byte decay1rate, decay2rate;
            public byte decay1lvl;
            public byte relrate;
            public byte block;
            public byte fns_hi;
            public int fns;
            public byte feedback;
            public byte waveform;
            public byte accon;
            public byte algorithm;
            public byte ch0_level, ch1_level, ch2_level, ch3_level;

            public int startaddr;
            public int loopaddr;
            public int endaddr;
            public byte altloop;
            public byte fs;
            public byte srcnote, srcb;

            public int step;
            public long stepptr;

            public byte active;
            public byte bits;

            // envelope generator
            public int volume;
            public int env_state;
            public int env_attack_step;      // volume increase step in attack state
            public int env_decay1_step;
            public int env_decay2_step;
            public int env_release_step;

            public long feedback_modulation0;
            public long feedback_modulation1;

            public int lfo_phase, lfo_step;
            public int lfo_amplitude;
            public double lfo_phasemod;
        }

        public class YMF271Group
        {
            public byte sync, pfm;
            public byte Muted;
        }

        public class YMF271Chip
        {
            // lookup tables
            public short[][] lut_waves = new short[8][];
            public double[][][] lut_plfo = new double[][][] { new double[8][], new double[8][], new double[8][], new double[8][] };
            public int[][] lut_alfo = new int[4][];
            public double[] lut_ar = new double[64];
            public double[] lut_dc = new double[64];
            public double[] lut_lfo = new double[256];
            public int[] lut_attenuation = new int[16];
            public int[] lut_total_level = new int[128];
            public int[] lut_env_volume = new int[256];

            public YMF271Slot[] slots = new YMF271Slot[]
            {
                new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),
                new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),
                new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),
                new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),
                new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),
                new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot(),new YMF271Slot()
            };
            public YMF271Group[] groups = new YMF271Group[] {
                new YMF271Group(), new YMF271Group(), new YMF271Group(), new YMF271Group(),
                new YMF271Group(),new YMF271Group(),new YMF271Group(),new YMF271Group(),
                new YMF271Group(),new YMF271Group(),new YMF271Group(),new YMF271Group()
            };

            public byte[] regs_main = new byte[0x10];

            public int timerA, timerB;
            public int timerAVal = 0, timerBVal = 0;
            public int irqstate;
            public byte status;
            public byte enable;

            public int ext_address;
            public byte ext_rw;
            public byte ext_readlatch;

            public byte[] mem_base;
            public int mem_size;
            public int clock;

            //emu_timer *timA, *timB;
            //sound_stream * stream;
            public int[] mix_buffer;
            //final device_config *device;

            //devcb_resolved_read8 ext_mem_read;
            //devcb_resolved_write8 ext_mem_write;
            //void (*irq_callback)(final device_config *, int);
            //void (*irq_callback)(int);
        }


        private final int MAX_CHIPS = 0x10;
        public YMF271Chip[] YMF271Data = new YMF271Chip[] { new YMF271Chip(), new YMF271Chip(), };// MAX_CHIPS];

        @Override public String getName() {  return "YMF271"; }
        @Override public String getShortName() { return "OPX"; }

        /*INLINE YMF271Chip *get_safe_token(final device_config *device)
        {
            assert(device != NULL);
            assert(device.token != NULL);
            assert(device.type == SOUND);
            assert(sound_get_type(device) == SOUND_YMF271);
            return (YMF271Chip *)device.token;
        }*/

        //private byte ymf271_read_memory(YMF271Chip chip, int offset) { return 0; }


        private void calculate_step(YMF271Slot slot)
        {
            double st;

            if (slot.waveform == 7)
            {
                // external waveform (PCM)
                st = (double)(2 * (slot.fns | 2048)) * pow_table[slot.block] * fs_frequency[slot.fs];
                st = st * multiple_table[slot.multiple];

                // LFO phase modulation
                st *= slot.lfo_phasemod;

                st /= (double)(524288 / 65536);     // pre-multiply with 65536

                slot.step = (int)st;
            }
            else
            {
                // internal waveform (FM)
                st = (double)(2 * slot.fns) * pow_table[slot.block];
                st = st * multiple_table[slot.multiple] * (double)(SIN_LEN);

                // LFO phase modulation
                st *= slot.lfo_phasemod;

                st /= (double)(536870912 / 65536);  // pre-multiply with 65536

                slot.step = (int)st;
            }
        }

        private boolean check_envelope_end(YMF271Slot slot)
        {
            if (slot.volume <= 0)
            {
                slot.active = 0;
                slot.volume = 0;
                return true;
            }
            return false;
        }

        private void update_envelope(YMF271Slot slot)
        {
            switch (slot.env_state)
            {
                case ENV_ATTACK:
                    {
                        slot.volume += slot.env_attack_step;

                        if (slot.volume >= (255 << ENV_VOLUME_SHIFT))
                        {
                            slot.volume = (255 << ENV_VOLUME_SHIFT);
                            slot.env_state = ENV_DECAY1;
                        }
                        break;
                    }

                case ENV_DECAY1:
                    {
                        int decay_level = 255 - (slot.decay1lvl << 4);
                        slot.volume -= slot.env_decay1_step;

                        if (!check_envelope_end(slot) && (slot.volume >> ENV_VOLUME_SHIFT) <= decay_level)
                        {
                            slot.env_state = ENV_DECAY2;
                        }
                        break;
                    }

                case ENV_DECAY2:
                    {
                        slot.volume -= slot.env_decay2_step;
                        check_envelope_end(slot);
                        break;
                    }

                case ENV_RELEASE:
                    {
                        slot.volume -= slot.env_release_step;
                        check_envelope_end(slot);
                        break;
                    }
            }
        }

        private int get_keyscaled_rate(int rate, int keycode, int keyscale)
        {
            int newrate = rate + RKS_Table[keycode][keyscale];

            if (newrate > 63)
            {
                newrate = 63;
            }
            if (newrate < 0)
            {
                newrate = 0;
            }
            return newrate;
        }

        private int get_internal_keycode(int block, int fns)
        {
            int n43;
            if (fns < 0x780)
            {
                n43 = 0;
            }
            else if (fns < 0x900)
            {
                n43 = 1;
            }
            else if (fns < 0xa80)
            {
                n43 = 2;
            }
            else
            {
                n43 = 3;
            }

            return ((block & 7) * 4) + n43;
        }

        private int get_external_keycode(int block, int fns)
        {
            int n43;
            if (fns < 0x100)
            {
                n43 = 0;
            }
            else if (fns < 0x300)
            {
                n43 = 1;
            }
            else if (fns < 0x500)
            {
                n43 = 2;
            }
            else
            {
                n43 = 3;
            }

            return ((block & 7) * 4) + n43;
        }

        private void init_envelope(YMF271Chip chip, YMF271Slot slot)
        {
            int keycode, rate;
            int decay_level = 255 - (slot.decay1lvl << 4);

            if (slot.waveform != 7)
            {
                keycode = get_internal_keycode(slot.block, (int)slot.fns);
            }
            else
            {
                keycode = get_external_keycode(slot.block, (int)(slot.fns & 0x7ff));
                /* keycode = (keycode + slot.srcb * 4 + slot.srcnote) / 2; */ // not sure
            }

            // init attack state
            rate = get_keyscaled_rate(slot.ar * 2, keycode, slot.keyscale);
            slot.env_attack_step = (rate < 4) ? 0 : (int)(((double)(255 - 0) / chip.lut_ar[rate]) * 65536.0);

            // init decay1 state
            rate = get_keyscaled_rate(slot.decay1rate * 2, keycode, slot.keyscale);
            slot.env_decay1_step = (rate < 4) ? 0 : (int)(((double)(255 - decay_level) / chip.lut_dc[rate]) * 65536.0);

            // init decay2 state
            rate = get_keyscaled_rate(slot.decay2rate * 2, keycode, slot.keyscale);
            slot.env_decay2_step = (rate < 4) ? 0 : (int)(((double)(255 - 0) / chip.lut_dc[rate]) * 65536.0);

            // init release state
            rate = get_keyscaled_rate(slot.relrate * 4, keycode, slot.keyscale);
            slot.env_release_step = (rate < 4) ? 0 : (int)(((double)(255 - 0) / chip.lut_ar[rate]) * 65536.0);

            slot.volume = (255 - 160) << ENV_VOLUME_SHIFT;     // -60db
            slot.env_state = ENV_ATTACK;
        }

        private void init_lfo(YMF271Chip chip, YMF271Slot slot)
        {
            slot.lfo_phase = 0;
            slot.lfo_amplitude = 0;
            slot.lfo_phasemod = 0;

            slot.lfo_step = (int)((((double)LFO_LENGTH * chip.lut_lfo[slot.lfoFreq]) / 44100.0) * 256.0);
        }

        private void update_lfo(YMF271Chip chip, YMF271Slot slot)
        {
            slot.lfo_phase += slot.lfo_step;

            slot.lfo_amplitude = chip.lut_alfo[slot.lfowave][(slot.lfo_phase >> LFO_SHIFT) & (LFO_LENGTH - 1)];
            slot.lfo_phasemod = chip.lut_plfo[slot.lfowave][slot.pms][(slot.lfo_phase >> LFO_SHIFT) & (LFO_LENGTH - 1)];

            calculate_step(slot);
        }

        private int calculate_slot_volume(YMF271Chip chip, YMF271Slot slot)
        {
            // Note: Actually everyone of these stores only int (16.16 fixed point),
            //       but the calculations need long.
            int volume;
            long env_volume;
            long lfo_volume = 65536;

            switch (slot.ams)
            {
                case 0: lfo_volume = 65536; break;  // 0dB
                case 1: lfo_volume = 65536 - ((slot.lfo_amplitude * 33124) >> 16); break;  // 5.90625dB
                case 2: lfo_volume = 65536 - ((slot.lfo_amplitude * 16742) >> 16); break;  // 11.8125dB
                case 3: lfo_volume = 65536 - ((slot.lfo_amplitude * 4277) >> 16); break;   // 23.625dB
            }

            env_volume = (chip.lut_env_volume[255 - (slot.volume >> ENV_VOLUME_SHIFT)] * lfo_volume) >> 16;

            volume = (int)((env_volume * chip.lut_total_level[slot.tl]) >> 16);

            return volume;
        }

        private void update_pcm(YMF271Chip chip, int slotnum, int[] mixp, int length, int ptrMixp)
        {
            int i;
            long final_volume;
            short sample;
            long ch0_vol, ch1_vol; //, ch2_vol, ch3_vol;

            YMF271Slot slot = chip.slots[slotnum];

            if (slot.active == 0)
            {
                return;
            }

            //# ifdef _DEBUG
            //if (slot.waveform != 7)
            //{
            //logerror("Waveform %d in update_pcm !!!\n", slot.waveform);
            //}
            //#endif

            for (i = 0; i < length; i++)
            {
                // loop
                if ((slot.stepptr >> 16) > slot.endaddr)
                {
                    slot.stepptr = slot.stepptr - (((long)slot.endaddr << 16) + (long)slot.loopaddr << 16);
                    if ((slot.stepptr >> 16) > slot.endaddr)
                    {
                        // overflow
                        slot.stepptr &= 0xffff;
                        slot.stepptr |= ((long)slot.loopaddr << 16);
                        if ((slot.stepptr >> 16) > slot.endaddr)
                        {
                            // still overflow? (triggers in rdft2, rarely)
                            slot.stepptr &= 0xffff;
                            slot.stepptr |= ((long)slot.endaddr << 16);
                        }
                    }
                }

                if (slot.bits == 8)
                {
                    // 8bit
                    sample = (short)(ymf271_read_memory(chip, (int)(slot.startaddr + (slot.stepptr >> 16))) << 8);
                }
                else
                {
                    // 12bit
                    if ((slot.stepptr & 0x10000) != 0)
                        sample = (short)(ymf271_read_memory(chip, (int)(slot.startaddr + (slot.stepptr >> 17) * 3 + 2)) << 8
                                    | ((ymf271_read_memory(chip, (int)(slot.startaddr + (slot.stepptr >> 17) * 3 + 1)) << 4) & 0xf0));
                    else
                        sample = (short)(ymf271_read_memory(chip, (int)(slot.startaddr + (slot.stepptr >> 17) * 3)) << 8
                                    | (ymf271_read_memory(chip, (int)(slot.startaddr + (slot.stepptr >> 17) * 3 + 1)) & 0xf0));
                }

                update_envelope(slot);
                update_lfo(chip, slot);

                final_volume = calculate_slot_volume(chip, slot);

                ch0_vol = (final_volume * chip.lut_attenuation[slot.ch0_level]) >> 16;
                ch1_vol = (final_volume * chip.lut_attenuation[slot.ch1_level]) >> 16;
                //		ch2_vol = (final_volume * chip.lut_attenuation[slot.ch2_level]) >> 16;
                //		ch3_vol = (final_volume * chip.lut_attenuation[slot.ch3_level]) >> 16;

                if (ch0_vol > 65536) ch0_vol = 65536;
                if (ch1_vol > 65536) ch1_vol = 65536;

                mixp[ptrMixp++] += (int)((sample * ch0_vol) >> 16);
                mixp[ptrMixp++] += (int)((sample * ch1_vol) >> 16);

                // go to next step
                slot.stepptr += slot.step;
            }
        }

        // calculates the output of one FM operator
        private long calculate_op(YMF271Chip chip, int slotnum, long inp)
        {
            YMF271Slot slot = chip.slots[slotnum];
            long env, slot_output, slot_input = 0;

            update_envelope(slot);
            update_lfo(chip, slot);
            env = calculate_slot_volume(chip, slot);

            if (inp == OP_INPUT_FEEDBACK)
            {
                // from own feedback
                slot_input = (slot.feedback_modulation0 + slot.feedback_modulation1) / 2;
                slot.feedback_modulation0 = slot.feedback_modulation1;
            }
            else if (inp != OP_INPUT_NONE)
            {
                // from previous slot output
                slot_input = ((inp << (SIN_BITS - 2)) * modulation_level[slot.feedback]);
            }

            slot_output = chip.lut_waves[slot.waveform][(int) ((slot.stepptr + (long)slot_input) >> 16) & SIN_MASK];
            slot_output = (slot_output * env) >> 16;
            slot.stepptr += slot.step;

            return slot_output;
        }

        private void set_feedback(YMF271Chip chip, int slotnum, long inp)
        {
            YMF271Slot slot = chip.slots[slotnum];
            slot.feedback_modulation1 = (((inp << (SIN_BITS - 2)) * feedback_level[slot.feedback]) / 16);
        }

        //static STREAM_UPDATE( ymf271_update )
        public void ymf271_update(byte ChipID, int[][] outputs, int samples)
        {
            int i, j;
            int op;
            int[] mixp;
            int ptrMixp;
            //YMF271Chip *chip = (YMF271Chip *)param;
            YMF271Chip chip = YMF271Data[ChipID];

            for (i = 0; i < samples * 2; i++) chip.mix_buffer[i] = 0;
            //memset(chip.mix_buffer, 0, sizeof(chip.mix_buffer[0]) * samples * 2);

            for (j = 0; j < 12; j++)
            {
                YMF271Group slot_group = chip.groups[j];
                mixp = chip.mix_buffer;//[0];
                ptrMixp = 0;

                if (slot_group.Muted != 0)
                    continue;

                //# ifdef _DEBUG
                //if (slot_group.pfm && slot_group.sync != 3)
                //{
                //logerror("ymf271 Group %d: PFM, Sync = %d, Waveform Slot1 = %d, Slot2 = %d, Slot3 = %d, Slot4 = %d\n",
                //j, slot_group.sync, chip.slots[j + 0].waveform, chip.slots[j + 12].waveform, chip.slots[j + 24].waveform, chip.slots[j + 36].waveform);
                //}
                //#endif

                switch (slot_group.sync)
                {
                    // 4 operator FM
                    case 0:
                        {
                            int slot1 = j + (0 * 12);
                            int slot2 = j + (1 * 12);
                            int slot3 = j + (2 * 12);
                            int slot4 = j + (3 * 12);
                            //mixp = chip.mix_buffer;

                            if (chip.slots[slot1].active != 0)
                            {
                                for (i = 0; i < samples; i++)
                                {
                                    long output1 = 0, output2 = 0, output3 = 0, output4 = 0;
                                    long phase_mod1 = 0, phase_mod2 = 0, phase_mod3 = 0;
                                    switch (chip.slots[slot1].algorithm)
                                    {
                                        // <--------|
                                        // +--[S1]--|--+--[S3]--+--[S2]--+--[S4]-.
                                        case 0:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            phase_mod3 = calculate_op(chip, slot3, phase_mod1);
                                            phase_mod2 = calculate_op(chip, slot2, phase_mod3);
                                            output4 = calculate_op(chip, slot4, phase_mod2);
                                            break;

                                        // <-----------------|
                                        // +--[S1]--+--[S3]--|--+--[S2]--+--[S4]-.
                                        case 1:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            phase_mod3 = calculate_op(chip, slot3, phase_mod1);
                                            set_feedback(chip, slot1, phase_mod3);
                                            phase_mod2 = calculate_op(chip, slot2, phase_mod3);
                                            output4 = calculate_op(chip, slot4, phase_mod2);
                                            break;

                                        // <--------|
                                        // +--[S1]--|
                                        //          |
                                        //  --[S3]--+--[S2]--+--[S4]-.
                                        case 2:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            phase_mod3 = calculate_op(chip, slot3, OP_INPUT_NONE);
                                            phase_mod2 = calculate_op(chip, slot2, (phase_mod1 + phase_mod3) / 1);
                                            output4 = calculate_op(chip, slot4, phase_mod2);
                                            break;

                                        //          <--------|
                                        //          +--[S1]--|
                                        //                   |
                                        //  --[S3]--+--[S2]--+--[S4]-.
                                        case 3:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            phase_mod3 = calculate_op(chip, slot3, OP_INPUT_NONE);
                                            phase_mod2 = calculate_op(chip, slot2, phase_mod3);
                                            output4 = calculate_op(chip, slot4, (phase_mod1 + phase_mod2) / 1);
                                            break;

                                        //              --[S2]--|
                                        // <--------|           |
                                        // +--[S1]--|--+--[S3]--+--[S4]-.
                                        case 4:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            phase_mod3 = calculate_op(chip, slot3, phase_mod1);
                                            phase_mod2 = calculate_op(chip, slot2, OP_INPUT_NONE);
                                            output4 = calculate_op(chip, slot4, (phase_mod3 + phase_mod2) / 1);
                                            break;

                                        //           --[S2]-----|
                                        // <-----------------|  |
                                        // +--[S1]--+--[S3]--|--+--[S4]-.
                                        case 5:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            phase_mod3 = calculate_op(chip, slot3, phase_mod1);
                                            set_feedback(chip, slot1, phase_mod3);
                                            phase_mod2 = calculate_op(chip, slot2, OP_INPUT_NONE);
                                            output4 = calculate_op(chip, slot4, (phase_mod3 + phase_mod2) / 1);
                                            break;

                                        //  --[S2]-----+--[S4]--|
                                        //                      |
                                        // <--------|           |
                                        // +--[S1]--|--+--[S3]--+-.
                                        case 6:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            output3 = calculate_op(chip, slot3, phase_mod1);
                                            phase_mod2 = calculate_op(chip, slot2, OP_INPUT_NONE);
                                            output4 = calculate_op(chip, slot4, phase_mod2);
                                            break;

                                        //  --[S2]--+--[S4]-----|
                                        //                      |
                                        // <-----------------|  |
                                        // +--[S1]--+--[S3]--|--+-.
                                        case 7:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            phase_mod3 = calculate_op(chip, slot3, phase_mod1);
                                            set_feedback(chip, slot1, phase_mod3);
                                            output3 = phase_mod3;
                                            phase_mod2 = calculate_op(chip, slot2, OP_INPUT_NONE);
                                            output4 = calculate_op(chip, slot4, phase_mod2);
                                            break;

                                        //  --[S3]--+--[S2]--+--[S4]--|
                                        //                            |
                                        // <--------|                 |
                                        // +--[S1]--|-----------------+-.
                                        case 8:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            output1 = phase_mod1;
                                            phase_mod3 = calculate_op(chip, slot3, OP_INPUT_NONE);
                                            phase_mod2 = calculate_op(chip, slot2, phase_mod3);
                                            output4 = calculate_op(chip, slot4, phase_mod2);
                                            break;

                                        //          <--------|
                                        //          +--[S1]--|
                                        //                   |
                                        //  --[S3]--|        |
                                        //  --[S2]--+--[S4]--+-.
                                        case 9:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            output1 = phase_mod1;
                                            phase_mod3 = calculate_op(chip, slot3, OP_INPUT_NONE);
                                            phase_mod2 = calculate_op(chip, slot2, OP_INPUT_NONE);
                                            output4 = calculate_op(chip, slot4, (phase_mod3 + phase_mod2) / 1);
                                            break;

                                        //              --[S4]--|
                                        //              --[S2]--|
                                        // <--------|           |
                                        // +--[S1]--|--+--[S3]--+-.
                                        case 10:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            output3 = calculate_op(chip, slot3, phase_mod1);
                                            output2 = calculate_op(chip, slot2, OP_INPUT_NONE);
                                            output4 = calculate_op(chip, slot4, OP_INPUT_NONE);
                                            break;

                                        //           --[S4]-----|
                                        //           --[S2]-----|
                                        // <-----------------|  |
                                        // +--[S1]--+--[S3]--|--+-.
                                        case 11:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            phase_mod3 = calculate_op(chip, slot3, phase_mod1);
                                            set_feedback(chip, slot1, phase_mod3);
                                            output3 = phase_mod3;
                                            output2 = calculate_op(chip, slot2, OP_INPUT_NONE);
                                            output4 = calculate_op(chip, slot4, OP_INPUT_NONE);
                                            break;

                                        //             |--+--[S4]--|
                                        // <--------|  |--+--[S3]--|
                                        // +--[S1]--|--|--+--[S2]--+-.
                                        case 12:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            output3 = calculate_op(chip, slot3, phase_mod1);
                                            output2 = calculate_op(chip, slot2, phase_mod1);
                                            output4 = calculate_op(chip, slot4, phase_mod1);
                                            break;

                                        //  --[S3]--+--[S2]--|
                                        //                   |
                                        //  --[S4]-----------|
                                        // <--------|        |
                                        // +--[S1]--|--------+-.
                                        case 13:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            output1 = phase_mod1;
                                            phase_mod3 = calculate_op(chip, slot3, OP_INPUT_NONE);
                                            output2 = calculate_op(chip, slot2, phase_mod3);
                                            output4 = calculate_op(chip, slot4, OP_INPUT_NONE);
                                            break;

                                        //  --[S2]-----+--[S4]--|
                                        //                      |
                                        // <--------|  +--[S3]--|
                                        // +--[S1]--|--|--------+-.
                                        case 14:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            output1 = phase_mod1;
                                            output3 = calculate_op(chip, slot3, phase_mod1);
                                            phase_mod2 = calculate_op(chip, slot2, OP_INPUT_NONE);
                                            output4 = calculate_op(chip, slot4, phase_mod2);
                                            break;

                                        //  --[S4]-----|
                                        //  --[S2]-----|
                                        //  --[S3]-----|
                                        // <--------|  |
                                        // +--[S1]--|--+-.
                                        case 15:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            output1 = phase_mod1;
                                            output3 = calculate_op(chip, slot3, OP_INPUT_NONE);
                                            output2 = calculate_op(chip, slot2, OP_INPUT_NONE);
                                            output4 = calculate_op(chip, slot4, OP_INPUT_NONE);
                                            break;
                                    }

                                    mixp[ptrMixp++] += (int)((output1 * chip.lut_attenuation[chip.slots[slot1].ch0_level]) +
                                                (output2 * chip.lut_attenuation[chip.slots[slot2].ch0_level]) +
                                                (output3 * chip.lut_attenuation[chip.slots[slot3].ch0_level]) +
                                                (output4 * chip.lut_attenuation[chip.slots[slot4].ch0_level])) >> 16;
                                    mixp[ptrMixp++] += (int)((output1 * chip.lut_attenuation[chip.slots[slot1].ch1_level]) +
                                                (output2 * chip.lut_attenuation[chip.slots[slot2].ch1_level]) +
                                                (output3 * chip.lut_attenuation[chip.slots[slot3].ch1_level]) +
                                                (output4 * chip.lut_attenuation[chip.slots[slot4].ch1_level])) >> 16;
                                }
                            }
                            break;
                        }

                    // 2x 2 operator FM
                    case 1:
                        {
                            for (op = 0; op < 2; op++)
                            {
                                int slot1 = j + ((op + 0) * 12);
                                int slot3 = j + ((op + 2) * 12);

                                mixp = chip.mix_buffer;
                                if (chip.slots[slot1].active != 0)
                                {
                                    for (i = 0; i < samples; i++)
                                    {
                                        long output1 = 0, output3 = 0;
                                        long phase_mod1, phase_mod3 = 0;
                                        switch (chip.slots[slot1].algorithm & 3)
                                        {
                                            // <--------|
                                            // +--[S1]--|--+--[S3]-.
                                            case 0:
                                                phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                                set_feedback(chip, slot1, phase_mod1);
                                                output3 = calculate_op(chip, slot3, phase_mod1);
                                                break;

                                            // <-----------------|
                                            // +--[S1]--+--[S3]--|-.
                                            case 1:
                                                phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                                phase_mod3 = calculate_op(chip, slot3, phase_mod1);
                                                set_feedback(chip, slot1, phase_mod3);
                                                output3 = phase_mod3;
                                                break;

                                            //  --[S3]-----|
                                            // <--------|  |
                                            // +--[S1]--|--+-.
                                            case 2:
                                                phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                                set_feedback(chip, slot1, phase_mod1);
                                                output1 = phase_mod1;
                                                output3 = calculate_op(chip, slot3, OP_INPUT_NONE);
                                                break;
                                            //
                                            // <--------|  +--[S3]--|
                                            // +--[S1]--|--|--------+-.
                                            case 3:
                                                phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                                set_feedback(chip, slot1, phase_mod1);
                                                output1 = phase_mod1;
                                                output3 = calculate_op(chip, slot3, phase_mod1);
                                                break;
                                        }

                                        mixp[ptrMixp++] += (int)((output1 * chip.lut_attenuation[chip.slots[slot1].ch0_level]) +
                                                    (output3 * chip.lut_attenuation[chip.slots[slot3].ch0_level])) >> 16;
                                        mixp[ptrMixp++] += (int)((output1 * chip.lut_attenuation[chip.slots[slot1].ch1_level]) +
                                                    (output3 * chip.lut_attenuation[chip.slots[slot3].ch1_level])) >> 16;
                                    }
                                }
                            }
                            break;
                        }

                    // 3 operator FM + PCM
                    case 2:
                        {
                            int slot1 = j + (0 * 12);
                            int slot2 = j + (1 * 12);
                            int slot3 = j + (2 * 12);
                            //mixp = chip.mix_buffer;

                            if (chip.slots[slot1].active != 0)
                            {
                                for (i = 0; i < samples; i++)
                                {
                                    long output1 = 0, output2 = 0, output3 = 0;
                                    long phase_mod1 = 0, phase_mod3 = 0;
                                    switch (chip.slots[slot1].algorithm & 7)
                                    {
                                        // <--------|
                                        // +--[S1]--|--+--[S3]--+--[S2]-.
                                        case 0:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            phase_mod3 = calculate_op(chip, slot3, phase_mod1);
                                            output2 = calculate_op(chip, slot2, phase_mod3);
                                            break;

                                        // <-----------------|
                                        // +--[S1]--+--[S3]--|--+--[S2]-.
                                        case 1:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            phase_mod3 = calculate_op(chip, slot3, phase_mod1);
                                            set_feedback(chip, slot1, phase_mod3);
                                            output2 = calculate_op(chip, slot2, phase_mod3);
                                            break;

                                        //  --[S3]-----|
                                        // <--------|  |
                                        // +--[S1]--|--+--[S2]-.
                                        case 2:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            phase_mod3 = calculate_op(chip, slot3, OP_INPUT_NONE);
                                            output2 = calculate_op(chip, slot2, (phase_mod1 + phase_mod3) / 1);
                                            break;

                                        //  --[S3]--+--[S2]--|
                                        // <--------|        |
                                        // +--[S1]--|--------+-.
                                        case 3:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            output1 = phase_mod1;
                                            phase_mod3 = calculate_op(chip, slot3, OP_INPUT_NONE);
                                            output2 = calculate_op(chip, slot2, phase_mod3);
                                            break;

                                        //              --[S2]--|
                                        // <--------|           |
                                        // +--[S1]--|--+--[S3]--+-.
                                        case 4:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            output3 = calculate_op(chip, slot3, phase_mod1);
                                            output2 = calculate_op(chip, slot2, OP_INPUT_NONE);
                                            break;

                                        //              --[S2]--|
                                        // <-----------------|  |
                                        // +--[S1]--+--[S3]--|--+-.
                                        case 5:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            phase_mod3 = calculate_op(chip, slot3, phase_mod1);
                                            set_feedback(chip, slot1, phase_mod3);
                                            output3 = phase_mod3;
                                            output2 = calculate_op(chip, slot2, OP_INPUT_NONE);
                                            break;

                                        //  --[S2]-----|
                                        //  --[S3]-----|
                                        // <--------|  |
                                        // +--[S1]--|--+-.
                                        case 6:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            output1 = phase_mod1;
                                            output3 = calculate_op(chip, slot3, OP_INPUT_NONE);
                                            output2 = calculate_op(chip, slot2, OP_INPUT_NONE);
                                            break;

                                        //              --[S2]--|
                                        // <--------|  +--[S3]--|
                                        // +--[S1]--|--|--------+-.
                                        case 7:
                                            phase_mod1 = calculate_op(chip, slot1, OP_INPUT_FEEDBACK);
                                            set_feedback(chip, slot1, phase_mod1);
                                            output1 = phase_mod1;
                                            output3 = calculate_op(chip, slot3, phase_mod1);
                                            output2 = calculate_op(chip, slot2, OP_INPUT_NONE);
                                            break;
                                    }

                                    mixp[ptrMixp++] += (int)((output1 * chip.lut_attenuation[chip.slots[slot1].ch0_level]) +
                                                (output2 * chip.lut_attenuation[chip.slots[slot2].ch0_level]) +
                                                (output3 * chip.lut_attenuation[chip.slots[slot3].ch0_level])) >> 16;
                                    mixp[ptrMixp++] += (int)((output1 * chip.lut_attenuation[chip.slots[slot1].ch1_level]) +
                                                (output2 * chip.lut_attenuation[chip.slots[slot2].ch1_level]) +
                                                (output3 * chip.lut_attenuation[chip.slots[slot3].ch1_level])) >> 16;
                                }
                            }

                            mixp = chip.mix_buffer;
                            update_pcm(chip, j + (3 * 12), mixp, samples, ptrMixp);
                            break;
                        }

                    // PCM
                    case 3:
                        {
                            update_pcm(chip, j + (0 * 12), mixp, samples, ptrMixp);
                            update_pcm(chip, j + (1 * 12), mixp, samples, ptrMixp);
                            update_pcm(chip, j + (2 * 12), mixp, samples, ptrMixp);
                            update_pcm(chip, j + (3 * 12), mixp, samples, ptrMixp);
                            break;
                        }
                }
            }

            mixp = chip.mix_buffer;
            ptrMixp = 0;
            for (i = 0; i < samples; i++)
            {
                outputs[0][i] = mixp[ptrMixp++] >> 2;
                outputs[1][i] = mixp[ptrMixp++] >> 2;
            }
        }

        private void write_register(YMF271Chip chip, int slotnum, int reg, byte data)
        {
            YMF271Slot slot = chip.slots[slotnum];

            switch (reg)
            {
                case 0x0:
                    slot.ext_en = (byte)((data & 0x80) != 0 ? 1 : 0);
                    slot.ext_out = (byte)((data >> 3) & 0xf);

                    if ((data & 1) != 0)
                    {
                        // key on
                        slot.step = 0;
                        slot.stepptr = 0;

                        slot.active = 1;

                        calculate_step(slot);
                        init_envelope(chip, slot);
                        init_lfo(chip, slot);
                        slot.feedback_modulation0 = 0;
                        slot.feedback_modulation1 = 0;
                    }
                    else
                    {
                        if (slot.active != 0)
                        {
                            slot.env_state = ENV_RELEASE;
                        }
                    }
                    break;

                case 0x1:
                    slot.lfoFreq = data;
                    break;

                case 0x2:
                    slot.lfowave = (byte)(data & 3);
                    slot.pms = (byte)((data >> 3) & 0x7);
                    slot.ams = (byte)((data >> 6) & 0x3);
                    break;

                case 0x3:
                    slot.multiple = (byte)(data & 0xf);
                    slot.detune = (byte)((data >> 4) & 0x7);
                    break;

                case 0x4:
                    slot.tl = (byte)(data & 0x7f);
                    break;

                case 0x5:
                    slot.ar = (byte)(data & 0x1f);
                    slot.keyscale = (byte)((data >> 5) & 0x7);
                    break;

                case 0x6:
                    slot.decay1rate = (byte)(data & 0x1f);
                    break;

                case 0x7:
                    slot.decay2rate = (byte)(data & 0x1f);
                    break;

                case 0x8:
                    slot.relrate = (byte)(data & 0xf);
                    slot.decay1lvl = (byte)((data >> 4) & 0xf);
                    break;

                case 0x9:
                    // write frequency and block here
                    slot.fns = (int)((slot.fns_hi << 8 & 0x0f00) | data);
                    slot.block = (byte)(slot.fns_hi >> 4 & 0xf);
                    break;

                case 0xa:
                    slot.fns_hi = data;
                    break;

                case 0xb:
                    slot.waveform = (byte)(data & 0x7);
                    slot.feedback = (byte)((data >> 4) & 0x7);
                    slot.accon = (byte)((data & 0x80) != 0 ? 1 : 0);
                    break;

                case 0xc:
                    slot.algorithm = (byte)(data & 0xf);
                    break;

                case 0xd:
                    slot.ch0_level = (byte)(data >> 4);
                    slot.ch1_level = (byte)(data & 0xf);
                    break;

                case 0xe:
                    slot.ch2_level = (byte)(data >> 4);
                    slot.ch3_level = (byte)(data & 0xf);
                    break;

                default:
                    break;
            }
        }

        private void ymf271_write_fm(YMF271Chip chip, int bank, byte address, byte data)
        {
            int groupnum = fm_tab[address & 0xf];
            int reg = (address >> 4) & 0xf;
            int sync_reg;
            int sync_mode;

            if (groupnum == -1)
            {
                //System.err.printf("ymf271_write_fm invalid group {0:X02} {1:X02}", address, data);
                return;
            }

            // check if the register is a synchronized register
            sync_reg = 0;
            switch (reg)
            {
                case 0:
                case 9:
                case 10:
                case 12:
                case 13:
                case 14:
                    sync_reg = 1;
                    break;

                default:
                    break;
            }

            // check if the slot is key on slot for synchronizing
            sync_mode = 0;
            switch (chip.groups[groupnum].sync)
            {
                // 4 slot mode
                case 0:
                    if (bank == 0)
                        sync_mode = 1;
                    break;

                // 2x 2 slot mode
                case 1:
                    if (bank == 0 || bank == 1)
                        sync_mode = 1;
                    break;

                // 3 slot + 1 slot mode
                case 2:
                    if (bank == 0)
                        sync_mode = 1;
                    break;

                default:
                    break;
            }

            // key-on slot & synced register
            if (sync_mode != 0 && sync_reg != 0)
            {
                switch (chip.groups[groupnum].sync)
                {
                    // 4 slot mode
                    case 0:
                        write_register(chip, (12 * 0) + groupnum, reg, data);
                        write_register(chip, (12 * 1) + groupnum, reg, data);
                        write_register(chip, (12 * 2) + groupnum, reg, data);
                        write_register(chip, (12 * 3) + groupnum, reg, data);
                        break;

                    // 2x 2 slot mode
                    case 1:
                        if (bank == 0)
                        {
                            // Slot 1 - Slot 3
                            write_register(chip, (12 * 0) + groupnum, reg, data);
                            write_register(chip, (12 * 2) + groupnum, reg, data);
                        }
                        else
                        {
                            // Slot 2 - Slot 4
                            write_register(chip, (12 * 1) + groupnum, reg, data);
                            write_register(chip, (12 * 3) + groupnum, reg, data);
                        }
                        break;

                    // 3 slot + 1 slot mode (1 slot is handled normally)
                    case 2:
                        write_register(chip, (12 * 0) + groupnum, reg, data);
                        write_register(chip, (12 * 1) + groupnum, reg, data);
                        write_register(chip, (12 * 2) + groupnum, reg, data);
                        break;
                }
            }
            else
            {
                // write register normally
                write_register(chip, (12 * bank) + groupnum, reg, data);
            }
        }

        private void ymf271_write_pcm(YMF271Chip chip, byte address, byte data)
        {
            int slotnum = pcm_tab[address & 0xf];
            YMF271Slot slot;
            if (slotnum == -1)
            {
                //System.err.printf("ymf271_write_pcm invalid slot {0:X02} {1:X02}", address, data);
                return;
            }
            slot = chip.slots[slotnum];

            switch ((address >> 4) & 0xf)
            {
                case 0x0:
                    slot.startaddr &= 0xffffff00;// ~0xff;
                    slot.startaddr |= data;
                    break;

                case 0x1:
                    slot.startaddr &= 0xffff00ff;// ~0xff00;
                    slot.startaddr |= (int)(data << 8);
                    break;

                case 0x2:
                    slot.startaddr &= 0xff00ffff;// ~0xff0000;
                    slot.startaddr |= (int)((data & 0x7f) << 16);
                    slot.altloop = (byte)((data & 0x80) != 0 ? 1 : 0);
                    //if (slot.altloop)
                    //	popmessage("ymf271 A/L, contact MAMEdev");
                    break;

                case 0x3:
                    slot.endaddr &= 0xffffff00;// ~0xff;
                    slot.endaddr |= data;
                    break;

                case 0x4:
                    slot.endaddr &= 0xffff00ff;// ~0xff00;
                    slot.endaddr |= (int)(data << 8);
                    break;

                case 0x5:
                    slot.endaddr &= 0xff00ffff;// ~0xff0000;
                    slot.endaddr |= (int)((data & 0x7f) << 16);
                    break;

                case 0x6:
                    slot.loopaddr &= 0xffffff00;// ~0xff;
                    slot.loopaddr |= data;
                    break;

                case 0x7:
                    slot.loopaddr &= 0xffff00ff;// ~0xff00;
                    slot.loopaddr |= (int)(data << 8);
                    break;

                case 0x8:
                    slot.loopaddr &= 0xff00ffff;// ~0xff0000;
                    slot.loopaddr |= (int)((data & 0x7f) << 16);
                    break;

                case 0x9:
                    slot.fs = (byte)(data & 0x3);
                    slot.bits = (byte)((data & 0x4) != 0 ? 12 : 8);
                    slot.srcnote = (byte)((data >> 3) & 0x3);
                    slot.srcb = (byte)((data >> 5) & 0x7);
                    break;

                default:
                    break;
            }
        }

        /*static TIMER_CALLBACK( ymf271_timer_a_tick )
        {
            YMF271Chip *chip = (YMF271Chip *)ptr;

            chip.status |= 1;

            if (chip.enable & 4)
            {
                chip.irqstate |= 1;
                if (chip.irq_callback) chip.irq_callback(chip.device, 1);
            }
        }

        static TIMER_CALLBACK( ymf271_timer_b_tick )
        {
            YMF271Chip *chip = (YMF271Chip *)ptr;

            chip.status |= 2;

            if (chip.enable & 8)
            {
                chip.irqstate |= 2;
                if (chip.irq_callback) chip.irq_callback(chip.device, 1);
            }
        }*/

        private byte ymf271_read_memory(YMF271Chip chip, int offset)
        {
            /*if (m_ext_read_handler.isnull())
            {
                if (offset < chip.mem_size)
                    return chip.mem_base[offset];

                // 8MB chip limit (shouldn't happen)
                else if (offset > 0x7fffff)
                    return chip.mem_base[offset & 0x7fffff];

                else
                    return 0;
            }
            else
                return m_ext_read_handler(offset);*/

            offset &= 0x7FFFFF;
            if (offset < chip.mem_size)
                return chip.mem_base[offset];
            else
                return 0;
        }

        private void ymf271_write_timer(YMF271Chip chip, byte address, byte data)
        {
            if ((address & 0xf0) == 0)
            {
                int groupnum = fm_tab[address & 0xf];
                YMF271Group group;
                if (groupnum == -1)
                {
                    //System.err.printf("ymf271_write_timer invalid group {0:X02} {1:X02}", address, data);
                    return;
                }
                group = chip.groups[groupnum];

                group.sync = (byte)(data & 0x3);
                group.pfm = (byte)(data >> 7);
            }
            else
            {
                switch (address)
                {
                    case 0x10:
                        chip.timerA = data;
                        break;

                    case 0x11:
                        // According to Yamaha's documentation, this sets timer A upper 2 bits
                        // (it says timer A is 10 bits). But, PCB audio recordings proves
                        // otherwise: it doesn't affect timer A frequency. (see ms32.c tetrisp)
                        // Does this register have another function regarding timer A/B?
                        break;

                    case 0x12:
                        chip.timerB = data;
                        break;

                    case 0x13:
                        // timer A load
                        //if (~chip.enable & data & 1)
                        //{
                        //attotime period = attotime::from_hz(chip.clock) * (384 * 4 * (256 - chip.timerA));
                        //chip.timA.adjust((data & 1) ? period : attotime::never, 0);
                        //}

                        // timer B load
                        //if (~chip.enable & data & 2)
                        //{
                        //attotime period = attotime::from_hz(chip.clock) * (384 * 16 * (256 - chip.timerB));
                        //chip.timB.adjust((data & 2) ? period : attotime::never, 0);
                        //}

                        // timer A reset
                        if ((data & 0x10) != 0)
                        {
                            chip.irqstate &= 0xfffffffe;// ~1;
                            chip.status &= 0xfe; //~1;

                            //if (!chip.irq_handler.isnull() && ~chip.irqstate & 2)
                            //	chip.irq_handler(0);
                        }

                        // timer B reset
                        if ((data & 0x20) != 0)
                        {
                            chip.irqstate &= 0xfffffffd;//~2;
                            chip.status &= 0xfd;// ~2;

                            //if (!chip.irq_handler.isnull() && ~chip.irqstate & 1)
                            //	chip.irq_handler(0);
                        }

                        chip.enable = data;
                        break;

                    case 0x14:
                        chip.ext_address &= 0xffffff00;// ~0xff;
                        chip.ext_address |= data;
                        break;
                    case 0x15:
                        chip.ext_address &= 0xffff00ff;// ~0xff00;
                        chip.ext_address |= (int)(data << 8);
                        break;
                    case 0x16:
                        chip.ext_address &= 0xff00ffff;// ~0xff0000;
                        chip.ext_address |= (int)((data & 0x7f) << 16);
                        chip.ext_rw = (byte)((data & 0x80) != 0 ? 1 : 0);
                        break;
                    case 0x17:
                        chip.ext_address = (chip.ext_address + 1) & 0x7fffff;
                        //if (!chip.ext_rw && !chip.ext_write_handler.isnull())
                        //	chip.ext_write_handler(chip.ext_address, data);
                        break;
                }
            }
        }

        //WRITE8_DEVICE_HANDLER( ymf271_w )
        public void ymf271_w(byte ChipID, int offset, byte data)
        {
            //YMF271Chip *chip = get_safe_token(device);
            YMF271Chip chip = YMF271Data[ChipID];

            chip.regs_main[offset & 0xf] = data;

            switch (offset & 0xf)
            {
                case 0x0:
                case 0x2:
                case 0x4:
                case 0x6:
                case 0x8:
                case 0xc:
                    // address regs
                    break;

                case 0x1:
                    ymf271_write_fm(chip, 0, chip.regs_main[0x0], data);
                    break;

                case 0x3:
                    ymf271_write_fm(chip, 1, chip.regs_main[0x2], data);
                    break;

                case 0x5:
                    ymf271_write_fm(chip, 2, chip.regs_main[0x4], data);
                    break;

                case 0x7:
                    ymf271_write_fm(chip, 3, chip.regs_main[0x6], data);
                    break;

                case 0x9:
                    ymf271_write_pcm(chip, chip.regs_main[0x8], data);
                    break;

                case 0xd:
                    ymf271_write_timer(chip, chip.regs_main[0xc], data);
                    break;

                default:
                    break;
            }
        }

        //READ8_DEVICE_HANDLER( ymf271_r )
        public byte ymf271_r(byte ChipID, int offset)
        {
            //YMF271Chip *chip = get_safe_token(device);
            YMF271Chip chip = YMF271Data[ChipID];

            switch (offset & 0xf)
            {
                case 0x0:
                    return chip.status;

                case 0x1:
                    // statusreg 2
                    return 0;

                case 0x2:
                    {
                        byte ret;
                        if (chip.ext_rw == 0)
                            return (byte) 0xff;

                        ret = chip.ext_readlatch;
                        chip.ext_address = (chip.ext_address + 1) & 0x7fffff;
                        chip.ext_readlatch = ymf271_read_memory(chip, chip.ext_address);
                        return ret;
                    }

                default:
                    break;
            }

            return (byte) 0xff;
        }

        private void init_tables(YMF271Chip chip)
        {
            int i, j;
            double clock_correction;

            for (i = 0; i < 8; i++)
                chip.lut_waves[i] = new short[SIN_LEN];// (short*)malloc(sizeof(short) * SIN_LEN);

            for (i = 0; i < 4 * 8; i++)
                chip.lut_plfo[i >> 3][i & 7] = new double[LFO_LENGTH];// (double*)malloc(sizeof(double) * LFO_LENGTH);

            for (i = 0; i < 4; i++)
                chip.lut_alfo[i] = new int[LFO_LENGTH];// (int*)malloc(sizeof(int) * LFO_LENGTH);

            for (i = 0; i < SIN_LEN; i++)
            {
                double m = Math.sin(((i * 2) + 1) * Math.PI / SIN_LEN);
                double m2 = Math.sin(((i * 4) + 1) * Math.PI / SIN_LEN);

                // Waveform 0: sin(wt)    (0 <= wt <= 2PI)
                chip.lut_waves[0][i] = (short)(m * MAXOUT);

                // Waveform 1: sin?(wt)   (0 <= wt <= PI)     -sin?(wt)  (PI <= wt <= 2PI)
                chip.lut_waves[1][i] = (i < (SIN_LEN / 2)) ? (short)((m * m) * MAXOUT) : (short)((m * m) * MINOUT);

                // Waveform 2: sin(wt)    (0 <= wt <= PI)     -sin(wt)   (PI <= wt <= 2PI)
                chip.lut_waves[2][i] = (i < (SIN_LEN / 2)) ? (short)(m * MAXOUT) : (short)(-m * MAXOUT);

                // Waveform 3: sin(wt)    (0 <= wt <= PI)     0
                chip.lut_waves[3][i] = (i < (SIN_LEN / 2)) ? (short)(m * MAXOUT) : (short)0;

                // Waveform 4: sin(2wt)   (0 <= wt <= PI)     0
                chip.lut_waves[4][i] = (i < (SIN_LEN / 2)) ? (short)(m2 * MAXOUT) : (short)0;

                // Waveform 5: |sin(2wt)| (0 <= wt <= PI)     0
                chip.lut_waves[5][i] = (i < (SIN_LEN / 2)) ? (short)(Math.abs(m2) * MAXOUT) : (short)0;

                // Waveform 6:     1      (0 <= wt <= 2PI)
                chip.lut_waves[6][i] = (short)(1 * MAXOUT);

                chip.lut_waves[7][i] = 0;
            }

            for (i = 0; i < LFO_LENGTH; i++)
            {
                int tri_wave;
                double ftri_wave, fsaw_wave;
                double[] plfo = new double[4];

                // LFO phase modulation
                plfo[0] = 0;

                fsaw_wave = ((i % (LFO_LENGTH / 2)) * PLFO_MAX) / (double)((LFO_LENGTH / 2) - 1);
                plfo[1] = (i < (LFO_LENGTH / 2)) ? fsaw_wave : fsaw_wave - PLFO_MAX;

                plfo[2] = (i < (LFO_LENGTH / 2)) ? PLFO_MAX : PLFO_MIN;

                ftri_wave = ((i % (LFO_LENGTH / 4)) * PLFO_MAX) / (double)(LFO_LENGTH / 4);
                switch (i / (LFO_LENGTH / 4))
                {
                    case 0: plfo[3] = ftri_wave; break;
                    case 1: plfo[3] = PLFO_MAX - ftri_wave; break;
                    case 2: plfo[3] = 0 - ftri_wave; break;
                    case 3: plfo[3] = 0 - (PLFO_MAX - ftri_wave); break;
                    default: plfo[3] = 0; /*assert(0);*/ break;
                }

                for (j = 0; j < 4; j++)
                {
                    chip.lut_plfo[j][0][i] = Math.pow(2.0, 0.0);
                    chip.lut_plfo[j][1][i] = Math.pow(2.0, (3.378 * plfo[j]) / 1200.0);
                    chip.lut_plfo[j][2][i] = Math.pow(2.0, (5.0646 * plfo[j]) / 1200.0);
                    chip.lut_plfo[j][3][i] = Math.pow(2.0, (6.7495 * plfo[j]) / 1200.0);
                    chip.lut_plfo[j][4][i] = Math.pow(2.0, (10.1143 * plfo[j]) / 1200.0);
                    chip.lut_plfo[j][5][i] = Math.pow(2.0, (20.1699 * plfo[j]) / 1200.0);
                    chip.lut_plfo[j][6][i] = Math.pow(2.0, (40.1076 * plfo[j]) / 1200.0);
                    chip.lut_plfo[j][7][i] = Math.pow(2.0, (79.307 * plfo[j]) / 1200.0);
                }

                // LFO amplitude modulation
                chip.lut_alfo[0][i] = 0;

                chip.lut_alfo[1][i] = ALFO_MAX - ((i * ALFO_MAX) / LFO_LENGTH);

                chip.lut_alfo[2][i] = (i < (LFO_LENGTH / 2)) ? ALFO_MAX : ALFO_MIN;

                tri_wave = ((i % (LFO_LENGTH / 2)) * ALFO_MAX) / (LFO_LENGTH / 2);
                chip.lut_alfo[3][i] = (i < (LFO_LENGTH / 2)) ? ALFO_MAX - tri_wave : tri_wave;
            }

            for (i = 0; i < 256; i++)
            {
                chip.lut_env_volume[i] = (int)(65536.0 / Math.pow(10.0, ((double)i / (256.0 / 96.0)) / 20.0));
            }

            for (i = 0; i < 16; i++)
            {
                chip.lut_attenuation[i] = (int)(65536.0 / Math.pow(10.0, channel_attenuation_table[i] / 20.0));
            }
            for (i = 0; i < 128; i++)
            {
                double db = 0.75 * (double)i;
                chip.lut_total_level[i] = (int)(65536.0 / Math.pow(10.0, db / 20.0));
            }

            // timing may use a non-standard XTAL
            clock_correction = (double)(STD_CLOCK) / (double)(chip.clock);
            for (i = 0; i < 256; i++)
            {
                chip.lut_lfo[i] = LFO_frequency_table[i] * clock_correction;
            }

            for (i = 0; i < 64; i++)
            {
                // attack/release rate in number of samples
                chip.lut_ar[i] = (ARTime[i] * clock_correction * 44100.0) / 1000.0;
            }
            for (i = 0; i < 64; i++)
            {
                // decay rate in number of samples
                chip.lut_dc[i] = (DCTime[i] * clock_correction * 44100.0) / 1000.0;
            }
        }

        /*static void init_state(YMF271Chip *chip, final device_config *device)
        {
            int i;

            for (i = 0; i < ARRAY_LENGTH(chip.slots); i++)
            {
                state_save_register_device_item(device, i, chip.slots[i].ext_out);
                state_save_register_device_item(device, i, chip.slots[i].lfoFreq);
                state_save_register_device_item(device, i, chip.slots[i].pms);
                state_save_register_device_item(device, i, chip.slots[i].ams);
                state_save_register_device_item(device, i, chip.slots[i].detune);
                state_save_register_device_item(device, i, chip.slots[i].multiple);
                state_save_register_device_item(device, i, chip.slots[i].tl);
                state_save_register_device_item(device, i, chip.slots[i].keyscale);
                state_save_register_device_item(device, i, chip.slots[i].ar);
                state_save_register_device_item(device, i, chip.slots[i].decay1rate);
                state_save_register_device_item(device, i, chip.slots[i].decay2rate);
                state_save_register_device_item(device, i, chip.slots[i].decay1lvl);
                state_save_register_device_item(device, i, chip.slots[i].relrate);
                state_save_register_device_item(device, i, chip.slots[i].fns);
                state_save_register_device_item(device, i, chip.slots[i].block);
                state_save_register_device_item(device, i, chip.slots[i].feedback);
                state_save_register_device_item(device, i, chip.slots[i].waveform);
                state_save_register_device_item(device, i, chip.slots[i].accon);
                state_save_register_device_item(device, i, chip.slots[i].algorithm);
                state_save_register_device_item(device, i, chip.slots[i].ch0_level);
                state_save_register_device_item(device, i, chip.slots[i].ch1_level);
                state_save_register_device_item(device, i, chip.slots[i].ch2_level);
                state_save_register_device_item(device, i, chip.slots[i].ch3_level);
                state_save_register_device_item(device, i, chip.slots[i].startaddr);
                state_save_register_device_item(device, i, chip.slots[i].loopaddr);
                state_save_register_device_item(device, i, chip.slots[i].endaddr);
                state_save_register_device_item(device, i, chip.slots[i].fs);
                state_save_register_device_item(device, i, chip.slots[i].srcnote);
                state_save_register_device_item(device, i, chip.slots[i].srcb);
                state_save_register_device_item(device, i, chip.slots[i].step);
                state_save_register_device_item(device, i, chip.slots[i].stepptr);
                state_save_register_device_item(device, i, chip.slots[i].active);
                state_save_register_device_item(device, i, chip.slots[i].bits);
                state_save_register_device_item(device, i, chip.slots[i].volume);
                state_save_register_device_item(device, i, chip.slots[i].env_state);
                state_save_register_device_item(device, i, chip.slots[i].env_attack_step);
                state_save_register_device_item(device, i, chip.slots[i].env_decay1_step);
                state_save_register_device_item(device, i, chip.slots[i].env_decay2_step);
                state_save_register_device_item(device, i, chip.slots[i].env_release_step);
                state_save_register_device_item(device, i, chip.slots[i].feedback_modulation0);
                state_save_register_device_item(device, i, chip.slots[i].feedback_modulation1);
                state_save_register_device_item(device, i, chip.slots[i].lfo_phase);
                state_save_register_device_item(device, i, chip.slots[i].lfo_step);
                state_save_register_device_item(device, i, chip.slots[i].lfo_amplitude);
            }

            for (i = 0; i < sizeof(chip.groups) / sizeof(chip.groups[0]); i++)
            {
                state_save_register_device_item(device, i, chip.groups[i].sync);
                state_save_register_device_item(device, i, chip.groups[i].pfm);
            }

            state_save_register_device_item(device, 0, chip.timerA);
            state_save_register_device_item(device, 0, chip.timerB);
            state_save_register_device_item(device, 0, chip.timerAVal);
            state_save_register_device_item(device, 0, chip.timerBVal);
            state_save_register_device_item(device, 0, chip.irqstate);
            state_save_register_device_item(device, 0, chip.status);
            state_save_register_device_item(device, 0, chip.enable);
            state_save_register_device_item(device, 0, chip.reg0);
            state_save_register_device_item(device, 0, chip.reg1);
            state_save_register_device_item(device, 0, chip.reg2);
            state_save_register_device_item(device, 0, chip.reg3);
            state_save_register_device_item(device, 0, chip.pcmreg);
            state_save_register_device_item(device, 0, chip.timerreg);
            state_save_register_device_item(device, 0, chip.ext_address);
            state_save_register_device_item(device, 0, chip.ext_read);
        }*/

        //static DEVICE_START( ymf271 )
        public int device_start_ymf271(byte ChipID, int clock)
        {
            //static final ymf271_interface defintrf = { DEVCB_NULL };
            //final ymf271_interface *intf;
            int i;
            //YMF271Chip *chip = get_safe_token(device);
            YMF271Chip chip;

            if (ChipID >= MAX_CHIPS)
                return 0;

            chip = YMF271Data[ChipID];
            //chip.device = device;
            chip.clock = (int)clock;

            //intf = (device.static_config != NULL) ? (final ymf271_interface *)device.static_config : &defintrf;

            chip.mem_size = 0x00;
            chip.mem_base = null;

            init_tables(chip);
            //init_state(chip);
            //chip.stream = stream_create(device, 0, 2, device.clock/384, chip, ymf271_update);

            //chip.mix_buffer = auto_alloc_array(machine, int, 44100*2);
            chip.mix_buffer = new int[44100 * 2];// (int*)malloc(44100 * 2 * sizeof(int));

            for (i = 0; i < 12; i++)
                chip.groups[i].Muted = 0x00;

            return clock / 384;
        }

        //static DEVICE_STOP( ymf271 )
        public void device_stop_ymf271(byte ChipID)
        {
            int i;
            YMF271Chip chip = YMF271Data[ChipID];

            //free(chip.mem_base); 
            chip.mem_base = null;

            for (i = 0; i < 8; i++)
            {
                //free(chip.lut_waves[i]);
                chip.lut_waves[i] = null;
            }
            for (i = 0; i < 4 * 8; i++)
            {
                //free(chip.lut_plfo[i >> 3][i & 7]);
                chip.lut_plfo[i >> 3][i & 7] = null;
            }

            for (i = 0; i < 4; i++)
            {
                //free(chip.lut_alfo[i]);
                chip.lut_alfo[i] = null;
            }

            //free(chip.mix_buffer);
            chip.mix_buffer = null;

            return;
        }

        //static DEVICE_RESET( ymf271 )
        public void device_reset_ymf271(byte ChipID)
        {
            int i;
            //YMF271Chip *chip = get_safe_token(device);
            YMF271Chip chip = YMF271Data[ChipID];

            for (i = 0; i < 48; i++)
            {
                chip.slots[i].active = 0;
                chip.slots[i].volume = 0;
            }

            // reset timers and IRQ
            //chip.timA.reset();
            //chip.timB.reset();

            chip.irqstate = 0;
            chip.status = 0;
            chip.enable = 0;

            //if (!chip.irq_handler.isnull())
            //	chip.irq_handler(0);
        }

        public void ymf271_write_rom(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData)
        {
            YMF271Chip chip = YMF271Data[ChipID];

            if (chip.mem_size != ROMSize)
            {
                chip.mem_base = new byte[ROMSize];// (byte*)realloc(chip.mem_base, ROMSize);
                chip.mem_size = ROMSize;
                for (int i = 0; i < ROMSize; i++) chip.mem_base[i] = (byte) 0xff;
                //memset(chip.mem_base, 0xFF, ROMSize);
            }
            if (DataStart > ROMSize)
                return;
            if (DataStart + DataLength > ROMSize)
                DataLength = ROMSize - DataStart;

            for (int i = 0; i < DataLength; i++) chip.mem_base[i + DataStart] = ROMData[i];
            //memcpy(chip.mem_base + DataStart, ROMData, DataLength);

            return;
        }

        public void ymf271_write_rom(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int srcStartAddress)
        {
            YMF271Chip chip = YMF271Data[ChipID];

            if (chip.mem_size != ROMSize)
            {
                chip.mem_base = new byte[ROMSize];// (byte*)realloc(chip.mem_base, ROMSize);
                chip.mem_size = ROMSize;
                for (int i = 0; i < ROMSize; i++) chip.mem_base[i] = (byte) 0xff;
                //memset(chip.mem_base, 0xFF, ROMSize);
            }
            if (DataStart > ROMSize)
                return;
            if (DataStart + DataLength > ROMSize)
                DataLength = ROMSize - DataStart;

            for (int i = 0; i < DataLength; i++) chip.mem_base[i + DataStart] = ROMData[i + srcStartAddress];
            //memcpy(chip.mem_base + DataStart, ROMData, DataLength);

            return;
        }

        public void ymf271_set_mute_mask(byte ChipID, int MuteMask)
        {
            YMF271Chip chip = YMF271Data[ChipID];
            byte CurChn;

            for (CurChn = 0; CurChn < 12; CurChn++)
                chip.groups[CurChn].Muted = (byte)((MuteMask >> CurChn) & 0x01);

            return;
        }

        @Override public int Write(byte ChipID, int port, int adr, int data)
        {
            YMF271_Write(ChipID, port, (byte)adr, (byte)data);
            return 0;
        }

        /**************************************************************************
         * Generic get_info
         **************************************************************************/

        /*DEVICE_GET_INFO( ymf271 )
        {
            switch (state)
            {
                // --- the following bits of info are returned as 64-bit signed integers ---
                case DEVINFO_INT_TOKEN_BYTES:					info.i = sizeof(YMF271Chip); 					break;

                // --- the following bits of info are returned as pointers to data or functions
                case DEVINFO_FCT_START:							info.start = DEVICE_START_NAME( ymf271 );			break;
                case DEVINFO_FCT_STOP:							// Nothing									break;
                case DEVINFO_FCT_RESET:							info.reset = DEVICE_RESET_NAME( ymf271 );			break;

                // --- the following bits of info are returned as NULL-terminated strings ---
                case DEVINFO_STR_NAME:							strcpy(info.s, "YMF271");						break;
                case DEVINFO_STR_FAMILY:					strcpy(info.s, "Yamaha FM");					break;
                case DEVINFO_STR_VERSION:					strcpy(info.s, "1.0");							break;
                case DEVINFO_STR_SOURCE_FILE:						strcpy(info.s, __FILE__);						break;
                case DEVINFO_STR_CREDITS:					strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
            }
        }*/

    }
