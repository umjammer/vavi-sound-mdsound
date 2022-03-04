package mdsound;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.function.Function;

import dotnet4j.Tuple;
import dotnet4j.io.Stream;

    public class ymf278b extends Instrument
    {
        private String romPath = null;
        private Function<String, Stream> romStream = null;

        @Override public void Reset(byte ChipID)
        {
            device_reset_ymf278b(ChipID);

            visVolume = new int[][][] {
                new int[][] { new int[] { 0, 0 } }
                , new int[][] { new int[] { 0, 0 } }
            };
        }

        @Override public int Start(byte ChipID, int clock)
        {
            return (int)device_start_ymf278b(ChipID, 33868800);
        }

        @Override public int Start(byte ChipID, int clock, int FMClockValue, Object... option)
        {
            if(option!=null && option.length>0)
            {
                if (option[0] instanceof String)
                {
                    romPath = (String)option[0];
                    romStream = null;
                }
                if (option[0] instanceof Function /*<String, Stream>*/)
                {
                    romPath = null;
                    romStream = (Function<String, Stream>)option[0];
                }
            }
            return (int)device_start_ymf278b(ChipID, (int)FMClockValue);
        }

        @Override public void Stop(byte ChipID)
        {
            device_stop_ymf278b(ChipID);
        }

        @Override public void Update(byte ChipID, int[][] outputs, int samples)
        {
            ymf278b_pcm_update(ChipID, outputs, samples);

            visVolume[ChipID][0][0] = outputs[0][0];
            visVolume[ChipID][0][1] = outputs[1][0];
        }

        private int YMF278B_Write(byte ChipID,int Port, byte Offset, byte Data)
        {
            ymf278b_w(ChipID, (Port << 1) | 0x00, Offset);
            ymf278b_w(ChipID, (Port << 1) | 0x01, Data);
            return 0;
        }



        /*

   YMF278B  FM + Wave table Synthesizer (OPL4)

   Timer and PCM YMF278B.  The FM is shared with the ymf262.

   This chip roughly splits the difference between the Sega 315-5560 MultiPCM
   (Multi32, Model 1/2) and YMF 292-F SCSP (later Model 2, STV, Saturn, Model 3).

   Features as listed in LSI-4MF2782 data sheet:
    FM Synthesis (same as YMF262)
     1. Sound generation mode
         Two-operater mode
          Generates eighteen voices or fifteen voices plus five rhythm sounds simultaneously
         Four-operator mode
          Generates six voices in four-operator mode plus six voices in two-operator mode simultaneously,
          or generates six voices in four-operator mode plus three voices in two-operator mode plus five
          rhythm sounds simultaneously
     2. Eight selectable waveforms
     3. Stereo output
    Wave Table Synthesis
     1. Generates twenty-four voices simultaneously
     2. 44.1kHz sampling rate for output sound data
     3. Selectable from 8-bit, 12-bit and 16-bit word lengths for wave data
     4. Stereo output (16-stage panpot for each voice)
    Wave Data
     1. Accepts 32M bit external memory at maximum
     2. Up to 512 wave tables
     3. External ROM or SRAM can be connected. With SRAM connected, the CPU can download wave data
     4. Outputs chip select signals for 1Mbit, 4Mbit, 8Mbit or 16Mbit memory
     5. Can be directly connected to the Yamaha YRW801 (Wave data ROM)
        Features of YRW801 as listed in LSI 4RW801A2
          Built-in wave data of tones which comply with GM system Level 1
           Melody tone ....... 128 tones
           Percussion tone ...  47 tones
          16Mbit capacity (2,097,152word x 8)

   By R. Belmont and O. Galibert.

   Copyright R. Belmont and O. Galibert.

   This software is dual-licensed: it may be used in MAME and properly licensed
   MAME derivatives under the terms of the MAME license.  For use outside of
   MAME and properly licensed derivatives, it is available under the
   terms of the GNU Lesser General Public License (LGPL), version 2.1.
   You may read the LGPL at http://www.gnu.org/licenses/lgpl.html

   Changelog:
   Sep. 8, 2002 - fixed ymf278b_compute_rate when OCT is negative (RB)
   Dec. 11, 2002 - added ability to set non-standard clock rates (RB)
                   fixed envelope target for release (fixes missing
           instruments in hotdebut).
                   Thanks to Team Japump! for MP3s from a real PCB.
           fixed crash if MAME is run with no sound.
   June 4, 2003 -  Changed to dual-license with LGPL for use in OpenMSX.
                   OpenMSX contributed a bugfix where looped samples were
            not being addressed properly, causing pitch fluctuation.
   August 15, 2010 - Backport to MAME-style C from OpenMSX
*/

        //# include <math.h>
        //# include "mamedef.h"
        //#include "sndintrf.h"
        //#include "streams.h"
        //#include "cp(int)rf.h"
        //# include <stdlib.h>
        //# include <string.h> // for memset
        //# include <stddef.h> // for NULL
        //# include <stdio.h>
        //# include <string.h>
        //# include "ymf262.h"
        //# include "ymf278b.h"
        //#pragma once

        private static final int YMF278B_STD_CLOCK = (33868800);   /* standard clock for OPL4 */


        //typedef struct _ymf278b_interface ymf278b_interface;
        public class ymf278b_interface
        {
            //void (*irq_callback)(final device_config *device, int state); /* irq callback */
            public callback irq_callback;// (int state); /* irq callback */
        };

        /*READ8_DEVICE_HANDLER( ymf278b_r );
        WRITE8_DEVICE_HANDLER( ymf278b_w );

        DEVICE_GET_INFO( ymf278b );
        // #define SOUND_YMF278B DEVICE_GET_INFO_NAME( ymf278b )*/

        //private void ymf278b_pcm_update(byte ChipID, int[][] outputs, int samples) { }
        //private int device_start_ymf278b(byte ChipID, int clock) { return 0; }
        //private void device_stop_ymf278b(byte ChipID) { }
        //private void device_reset_ymf278b(byte ChipID) { }

        //private byte ymf278b_r(byte ChipID, int offset) { return 0; }
        //private void ymf278b_w(byte ChipID, int offset, byte data) { }
        //private void ymf278b_write_rom(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData) { }
        //private void ymf278b_set_mute_mask(byte ChipID, int MuteMaskFM, int MuteMaskWT) { }


        public class YMF278BSlot
        {

            public int startaddr;
            public int loopaddr;
            public int endaddr;
            public int step;    /* fixed-point frequency step */
            public int stepptr; /* fixed-point pointer into the sample */
            public int pos;
            public short sample1, sample2;

            public int env_vol;

            public int lfo_cnt;
            public int lfo_step;
            public int lfo_max;
            public short wave;     /* wavetable number */
            public short FN;       /* f-number */
            public byte OCT;       /* octave */
            public byte PRVB;      /* pseudo-reverb */
            public byte LD;        /* level direct */
            public byte TL;        /* total level */
            public byte pan;       /* panpot */
            public byte lfo;       /* LFO */
            public byte vib;       /* vibrato */
            public byte AM;        /* AM level */

            public byte AR;
            public byte D1R;
            public int DL;
            public byte D2R;
            public byte RC;        /* rate correction */
            public byte RR;

            public byte bits;      /* width of the samples */
            public byte active;        /* slot keyed on */

            public byte state;
            public byte lfo_active;

            public byte Muted;
        }

        public class YMF278BChip
        {

            public YMF278BSlot[] slots = new YMF278BSlot[]{
                new YMF278BSlot(),new YMF278BSlot(),new YMF278BSlot(),new YMF278BSlot(),
                new YMF278BSlot(),new YMF278BSlot(),new YMF278BSlot(),new YMF278BSlot(),
                new YMF278BSlot(),new YMF278BSlot(),new YMF278BSlot(),new YMF278BSlot(),
                new YMF278BSlot(),new YMF278BSlot(),new YMF278BSlot(),new YMF278BSlot(),
                new YMF278BSlot(),new YMF278BSlot(),new YMF278BSlot(),new YMF278BSlot(),
                new YMF278BSlot(),new YMF278BSlot(),new YMF278BSlot(),new YMF278BSlot()
            };

            public int eg_cnt;  /* Global envelope generator counter. */

            public byte wavetblhdr;
            public byte memmode;
            public int memadr;

            public byte exp;

            public int fm_l, fm_r;
            public int pcm_l, pcm_r;

            //byte timer_a_count, timer_b_count, enable, current_irq;
            //emu_timer *timer_a, *timer_b;
            //int irq_line;

            public byte port_A, port_B, port_C;
            //void (*irq_callback)(final device_config *, int);
            public callback irq_callback;
            //final device_config *device;

            public int ROMSize;
            public byte[] rom;
            public int RAMSize;
            public byte[] ram;
            public int clock;

            public int[] volume = new int[256 * 4];          // precalculated attenuation values with some marging for enveloppe and pan levels

            public byte[] regs = new byte[256];

            public ymf262.OPL3 fmchip=new ymf262.OPL3();
            public byte FMEnabled;    // that saves a whole lot of CPU
                                      //sound_stream * stream;
            public ymf262 ymf262 = new ymf262();
        }

        //private byte CHIP_SAMPLING_MODE;
        //private int CHIP_SAMPLE_RATE;
        private int MAX_CHIPS = 0x10;
        private YMF278BChip[] YMF278BData = new YMF278BChip[] { new YMF278BChip(),new YMF278BChip()};// MAX_CHIPS 0x10 ?
        private int ROMFileSize = 0x00;
        private byte[] ROMFile = null;

        //char* FindFile(final char* FileName);   // from VGMPlay_Intf.h/VGMPlay.c


        private final static int EG_SH = 16; // 16.16 fixed point (EG timing)
        private final static int EG_TIMER_OVERFLOW = (1 << EG_SH);

        // envelope output entries
        private final static int ENV_BITS = 10;
        private final static int ENV_LEN = (1 << ENV_BITS);
        private final static double ENV_STEP = (128.0 / ENV_LEN);
        private static final int MAX_ATT_INDEX = ((1 << (ENV_BITS - 1)) - 1); // 511
        private static final int MIN_ATT_INDEX = 0;

        // Envelope Generator phases
        private static final int EG_ATT = 4;
        private static final int EG_DEC = 3;
        private static final int EG_SUS = 2;
        private static final int EG_REL = 1;
        private static final int EG_OFF = 0;

        private static final int EG_REV = 5; // pseudo reverb
        private static final int EG_DMP = 6; // damp

        // Pan values, units are -3dB, i.e. 8.
        private int[] pan_left = new int[]{
            0, 8, 16, 24, 32, 40, 48, 256, 256,   0,  0,  0,  0,  0,  0, 0
        };
        private int[] pan_right = new int[]{
            0, 0,  0,  0,  0,  0,  0,   0, 256, 256, 48, 40, 32, 24, 16, 8
        };

        // Mixing levels, units are -3dB, and add some marging to avoid clipping
        private int[] mix_level = new int[]{
            8, 16, 24, 32, 40, 48, 56, 256+8
        };

        // decay level table (3dB per step)
        // 0 - 15: 0, 3, 6, 9,12,15,18,21,24,27,30,33,36,39,42,93 (dB)
        // private static int SC(int db) { return (int)(db * (2.0 / ENV_STEP)); }
        private static int SC(int db) { return (int)(db / 3 * 0x20); }
        private int[] dl_tab = new int[]{
             SC( 0), SC( 3), SC( 6), SC( 9), SC(12), SC(15), SC(18), SC(21),
             SC(24), SC(27), SC(30), SC(33), SC(36), SC(39), SC(42), SC(93)
        };
        //#undef SC

        private final static int RATE_STEPS = 8;
        private byte[] eg_inc = new byte[] {
            //cycle:0  1   2  3   4  5   6  7
         0, 1,  0, 1,  0, 1,  0, 1, //  0  rates 00..12 0 (increment by 0 or 1)
         0, 1,  0, 1,  1, 1,  0, 1, //  1  rates 00..12 1
         0, 1,  1, 1,  0, 1,  1, 1, //  2  rates 00..12 2
         0, 1,  1, 1,  1, 1,  1, 1, //  3  rates 00..12 3

         1, 1,  1, 1,  1, 1,  1, 1, //  4  rate 13 0 (increment by 1)
         1, 1,  1, 2,  1, 1,  1, 2, //  5  rate 13 1
         1, 2,  1, 2,  1, 2,  1, 2, //  6  rate 13 2
         1, 2,  2, 2,  1, 2,  2, 2, //  7  rate 13 3

         2, 2,  2, 2,  2, 2,  2, 2, //  8  rate 14 0 (increment by 2)
         2, 2,  2, 4,  2, 2,  2, 4, //  9  rate 14 1
         2, 4,  2, 4,  2, 4,  2, 4, // 10  rate 14 2
         2, 4,  4, 4,  2, 4,  4, 4, // 11  rate 14 3

         4, 4,  4, 4,  4, 4,  4, 4, // 12  rates 15 0, 15 1, 15 2, 15 3 for decay
         8, 8,  8, 8,  8, 8,  8, 8, // 13  rates 15 0, 15 1, 15 2, 15 3 for attack (zero time)
         0, 0,  0, 0,  0, 0,  0, 0, // 14  infinity rates for attack and decay(s)
        };

        private static byte O(int a) { return (byte)(a * RATE_STEPS); }
        private byte[] eg_rate_select = new byte[] {
            O( 0),O( 1),O( 2),O( 3),
            O( 0),O( 1),O( 2),O( 3),
            O( 0),O( 1),O( 2),O( 3),
            O( 0),O( 1),O( 2),O( 3),
            O( 0),O( 1),O( 2),O( 3),
            O( 0),O( 1),O( 2),O( 3),
            O( 0),O( 1),O( 2),O( 3),
            O( 0),O( 1),O( 2),O( 3),
            O( 0),O( 1),O( 2),O( 3),
            O( 0),O( 1),O( 2),O( 3),
            O( 0),O( 1),O( 2),O( 3),
            O( 0),O( 1),O( 2),O( 3),
            O( 0),O( 1),O( 2),O( 3),
            O( 4),O( 5),O( 6),O( 7),
            O( 8),O( 9),O(10),O(11),
            O(12),O(12),O(12),O(12),
        };
        //#undef O

        // rate  0,    1,    2,    3,   4,   5,   6,  7,  8,  9,  10, 11, 12, 13, 14, 15
        // shift 12,   11,   10,   9,   8,   7,   6,  5,  4,  3,  2,  1,  0,  0,  0,  0
        // mask  4095, 2047, 1023, 511, 255, 127, 63, 31, 15, 7,  3,  1,  0,  0,  0,  0
        private static byte O2(int a) { return (byte)(a); }
        private byte[] eg_rate_shift = new byte[] {
            O2(12),O2(12),O2(12),O2(12),
            O2(11),O2(11),O2(11),O2(11),
            O2(10),O2(10),O2(10),O2(10),
            O2( 9),O2( 9),O2( 9),O2( 9),
            O2( 8),O2( 8),O2( 8),O2( 8),
            O2( 7),O2( 7),O2( 7),O2( 7),
            O2( 6),O2( 6),O2( 6),O2( 6),
            O2( 5),O2( 5),O2( 5),O2( 5),
            O2( 4),O2( 4),O2( 4),O2( 4),
            O2( 3),O2( 3),O2( 3),O2( 3),
            O2( 2),O2( 2),O2( 2),O2( 2),
            O2( 1),O2( 1),O2( 1),O2( 1),
            O2( 0),O2( 0),O2( 0),O2( 0),
            O2( 0),O2( 0),O2( 0),O2( 0),
            O2( 0),O2( 0),O2( 0),O2( 0),
            O2( 0),O2( 0),O2( 0),O2( 0),
        };
        //#undef O

        // number of steps to take in quarter of lfo frequency
        // TODO check if frequency matches real chip
        private static int O3(double a) { return (int)((EG_TIMER_OVERFLOW / a) / 6); }
        private int[] lfo_period = new int[]{
            O3(0.168), O3(2.019), O3(3.196), O3(4.206),
            O3(5.215), O3(5.888), O3(6.224), O3(7.066)
        };
        //#undef O

        private static int O4(double a) { return (int)(a * 65536); }
        private int[] vib_depth = new int[]{
            O4(0),      O4(3.378),  O4(5.065),  O4(6.750),
            O4(10.114), O4(20.170), O4(40.106), O4(79.307)
        };
        //#undef O


        private static int SC2(double db) { return (int)(db * (2.0 / ENV_STEP)); }
        private int[] am_depth = new int[]{
            SC2(0),     SC2(1.781), SC2(2.906), SC2(3.656),
            SC2(4.406), SC2(5.906), SC2(7.406), SC2(11.91)
        };

        @Override public String getName ()  { return "YMF278B"; }
        @Override public String getShortName() { return "OPL4"; }

        //#undef SC

        private void ymf278b_slot_reset(YMF278BSlot slot)
        {
            slot.wave = slot.FN = slot.OCT = slot.PRVB = slot.LD = slot.TL = slot.pan =
                slot.lfo = slot.vib = slot.AM = 0;
            slot.AR = slot.D1R = slot.D2R = slot.RC = slot.RR = 0; slot.DL = 0;
            slot.step = slot.stepptr = 0;
            slot.bits = 0; slot.startaddr = slot.loopaddr = slot.endaddr = 0;
            slot.env_vol = MAX_ATT_INDEX;

            slot.lfo_active = 0;
            slot.lfo_cnt = slot.lfo_step = 0;
            slot.lfo_max = lfo_period[0];

            slot.state = EG_OFF;
            slot.active = 0;

            // not strictly needed, but avoid UMR on savestate
            slot.pos = 0; slot.sample1 = slot.sample2 = 0;
        }

        private int ymf278b_slot_compute_rate(YMF278BSlot slot, int val)
        {
            int res;
            int oct;

            if (val == 0)
                return 0;
            else if (val == 15)
                return 63;

            if (slot.RC != 15)
            {
                oct = slot.OCT;

                if ((oct & 8) != 0)
                {
                    oct |= -8;
                }
                res = (oct + slot.RC) * 2 + ((slot.FN & 0x200) != 0 ? 1 : 0) + val * 4;
            }
            else
            {
                res = val * 4;
            }

            if (res < 0)
                res = 0;
            else if (res > 63)
                res = 63;

            return res;
        }

        private int ymf278b_slot_compute_vib(YMF278BSlot slot)
        {
            return (((slot.lfo_step << 8) / slot.lfo_max) * vib_depth[slot.vib]) >> 24;
        }


        private int ymf278b_slot_compute_am(YMF278BSlot slot)
        {
            if (slot.lfo_active != 0 && slot.AM != 0)
                return (((slot.lfo_step << 8) / slot.lfo_max) * am_depth[slot.AM]) >> 12;
            else
                return 0;
        }

        private void ymf278b_slot_set_lfo(YMF278BSlot slot, int newlfo)
        {
            slot.lfo_step = (((slot.lfo_step << 8) / slot.lfo_max) * newlfo) >> 8;
            slot.lfo_cnt = (((slot.lfo_cnt << 8) / slot.lfo_max) * newlfo) >> 8;

            slot.lfo = (byte)newlfo;
            slot.lfo_max = lfo_period[slot.lfo];
        }

        private void ymf278b_advance(YMF278BChip chip)
        {
            YMF278BSlot op;
            int i;
            byte rate;
            byte shift;
            byte select;

            chip.eg_cnt++;
            for (i = 0; i < 24; i++)
            {
                op = chip.slots[i];

                if (op.lfo_active != 0)
                {
                    op.lfo_cnt++;
                    if (op.lfo_cnt < op.lfo_max)
                    {
                        op.lfo_step++;
                    }
                    else if (op.lfo_cnt < (op.lfo_max * 3))
                    {
                        op.lfo_step--;
                    }
                    else
                    {
                        op.lfo_step++;
                        if (op.lfo_cnt == (op.lfo_max * 4))
                            op.lfo_cnt = 0;
                    }
                }

                // Envelope Generator
                switch (op.state)
                {
                    case EG_ATT:    // attack phase
                        rate = (byte)ymf278b_slot_compute_rate(op, op.AR);
                        if (rate < 4)
                            break;

                        shift = eg_rate_shift[rate];
                        if ((chip.eg_cnt & ((1 << shift) - 1)) == 0)
                        {
                            select = eg_rate_select[rate];
                            op.env_vol += (~op.env_vol * eg_inc[select + ((chip.eg_cnt >> shift) & 7)]) >> 3;
                            if (op.env_vol <= MIN_ATT_INDEX)
                            {
                                op.env_vol = MIN_ATT_INDEX;
                                if (op.DL != 0)
                                    op.state = EG_DEC;
                                else
                                    op.state = EG_SUS;
                            }
                        }
                        break;
                    case EG_DEC:    // decay phase
                        rate = (byte)ymf278b_slot_compute_rate(op, op.D1R);
                        if (rate < 4)
                            break;

                        shift = eg_rate_shift[rate];
                        if ((chip.eg_cnt & ((1 << shift) - 1)) == 0)
                        {
                            select = eg_rate_select[rate];
                            op.env_vol += eg_inc[select + ((chip.eg_cnt >> shift) & 7)];

                            if ((op.env_vol > dl_tab[6]) && op.PRVB != 0)
                                op.state = EG_REV;
                            else
                            {
                                if (op.env_vol >= op.DL)
                                    op.state = EG_SUS;
                            }
                        }
                        break;
                    case EG_SUS:    // sustain phase
                        rate = (byte)ymf278b_slot_compute_rate(op, op.D2R);
                        if (rate < 4)
                            break;

                        shift = eg_rate_shift[rate];
                        if ((chip.eg_cnt & ((1 << shift) - 1)) == 0)
                        {
                            select = eg_rate_select[rate];
                            op.env_vol += eg_inc[select + ((chip.eg_cnt >> shift) & 7)];

                            if ((op.env_vol > dl_tab[6]) && op.PRVB != 0)
                                op.state = EG_REV;
                            else
                            {
                                if (op.env_vol >= MAX_ATT_INDEX)
                                {
                                    op.env_vol = MAX_ATT_INDEX;
                                    op.active = 0;
                                }
                            }
                        }
                        break;
                    case EG_REL:    // release phase
                        rate = (byte)ymf278b_slot_compute_rate(op, op.RR);
                        if (rate < 4)
                            break;

                        shift = eg_rate_shift[rate];
                        if ((chip.eg_cnt & ((1 << shift) - 1)) == 0)
                        {
                            select = eg_rate_select[rate];
                            op.env_vol += eg_inc[select + ((chip.eg_cnt >> shift) & 7)];

                            if ((op.env_vol > dl_tab[6]) && op.PRVB != 0)
                                op.state = EG_REV;
                            else
                            {
                                if (op.env_vol >= MAX_ATT_INDEX)
                                {
                                    op.env_vol = MAX_ATT_INDEX;
                                    op.active = 0;
                                }
                            }
                        }
                        break;
                    case EG_REV:    // pseudo reverb
                                    // TODO improve env_vol update
                        rate = (byte)ymf278b_slot_compute_rate(op, 5);
                        //if (rate < 4)
                        // break;

                        shift = eg_rate_shift[rate];
                        if ((chip.eg_cnt & ((1 << shift) - 1)) == 0)
                        {
                            select = eg_rate_select[rate];
                            op.env_vol += eg_inc[select + ((chip.eg_cnt >> shift) & 7)];

                            if (op.env_vol >= MAX_ATT_INDEX)
                            {
                                op.env_vol = MAX_ATT_INDEX;
                                op.active = 0;
                            }
                        }
                        break;
                    case EG_DMP:    // damping
                                    // TODO improve env_vol update, damp is just fastest decay now
                        rate = 56;
                        shift = eg_rate_shift[rate];
                        if ((chip.eg_cnt & ((1 << shift) - 1)) == 0)
                        {
                            select = eg_rate_select[rate];
                            op.env_vol += eg_inc[select + ((chip.eg_cnt >> shift) & 7)];

                            if (op.env_vol >= MAX_ATT_INDEX)
                            {
                                op.env_vol = MAX_ATT_INDEX;
                                op.active = 0;
                            }
                        }
                        break;
                    case EG_OFF:
                        // nothing
                        break;

                    default:
                        //# ifdef _DEBUG
                        //logerror(...);
                        //#endif
                        break;
                }
            }
        }

        private byte ymf278b_readMem(YMF278BChip chip, int address)
        {
            if (address < chip.ROMSize)
                return chip.rom[address & 0x3fffff];
            else if (address < chip.ROMSize + chip.RAMSize)
                return chip.ram[address - (chip.ROMSize & 0x3fffff)];
            else
                return (byte) 255; // TODO check
        }

        private Tuple<byte[],Integer> ymf278b_readMemAddr(YMF278BChip chip, int address)
        {
            if (address < chip.ROMSize)
            {
                return new Tuple<byte[], Integer>(chip.rom, address & 0x3fffff);
            }
            else if (address < chip.ROMSize + chip.RAMSize)
            {
                return new Tuple<byte[], Integer>(chip.ram,(int)(address - (chip.ROMSize & 0x3fffff)));
            }
            else
                return null; // TODO check
        }

        private void ymf278b_writeMem(YMF278BChip chip, int address, byte value)
        {
            if (address < chip.ROMSize)
                return; // can't write to ROM
            else if (address < chip.ROMSize + chip.RAMSize)
            {
                //System.err.printf("adr:{0:x06} dat:{1:x02}", address, value);
                chip.ram[address - chip.ROMSize] = value;
            }
            else
                return; // can't write to unmapped memory

            return;
        }

        private short ymf278b_getSample(YMF278BChip chip, YMF278BSlot op)
        {
            short sample;
            int addr;
            Tuple<byte[], Integer> addrp;

            switch (op.bits)
            {
                case 0:
                    // 8 bit
                    sample = (short)(ymf278b_readMem(chip, (int)(op.startaddr + op.pos)) << 8);
                    break;
                case 1:
                    // 12 bit
                    addr = (int)(op.startaddr + ((op.pos / 2) * 3));
                    addrp = ymf278b_readMemAddr(chip, (int)addr);
                    if ((op.pos & 1) != 0)
                        sample = (short)((addrp.Item1[addrp.Item2 + 2] << 8) | ((addrp.Item1[addrp.Item2 + 1] << 4) & 0xF0));
                    else
                        sample = (short)((addrp.Item1[addrp.Item2 + 0] << 8) | (addrp.Item1[addrp.Item2 + 1] & 0xF0));
                    break;
                case 2:
                    // 16 bit
                    addr = (int)(op.startaddr + (op.pos * 2));
                    addrp = ymf278b_readMemAddr(chip, (int)addr);
                    sample = (short)((addrp.Item1[addrp.Item2 + 0] << 8) | addrp.Item1[addrp.Item2 + 1]);
                    break;
                default:
                    // TODO unspecified
                    sample = 0;
                    break;
            }
            return sample;
        }

        private int ymf278b_anyActive(YMF278BChip chip)
        {
            int i;

            for (i = 0; i < 24; i++)
            {
                if (chip.slots[i].active != 0)
                    return 1;
            }
            return 0;
        }

        public void ymf278b_pcm_update(byte ChipID, int[][] outputs, int samples)
        {
            YMF278BChip chip = YMF278BData[ChipID];
            int i;
            int j;
            int vl;
            int vr;

            if (chip.FMEnabled != 0)
            {
                /* memset is done by ymf262_update */
                chip.ymf262.ymf262_update_one(chip.fmchip, outputs, samples);
                // apply FM mixing level
                vl = mix_level[chip.fm_l] - 8; vl = chip.volume[vl];
                vr = mix_level[chip.fm_r] - 8; vr = chip.volume[vr];
                // make FM softer by 3 db
                //vl = (vl * 0xB5) >> 8; vr = (vr * 0xB5) >> 8;
                vl = (vl * 0xB5) >> 7; vr = (vr * 0xB5) >> 7;
                for (j = 0; j < samples; j++)
                {
                    outputs[0][j] = (outputs[0][j] * vl) >> 15;
                    outputs[1][j] = (outputs[1][j] * vr) >> 15;
                }
            }
            else
            {
                for (i = 0; i < samples; i++)
                {
                    outputs[0][i] = 0x00;
                    outputs[1][i] = 0x00;
                }
                //memset(outputs[0], 0x00, samples * sizeof(stream_sample_t));
                //memset(outputs[1], 0x00, samples * sizeof(stream_sample_t));
            }

            if (ymf278b_anyActive(chip) == 0)
            {
                // TODO update internal state, even if muted
                // TODO also mute individual channels
                return;
            }

            vl = mix_level[chip.pcm_l];
            vr = mix_level[chip.pcm_r];
            for (j = 0; j < samples; j++)
            {
                for (i = 0; i < 24; i++)
                {
                    YMF278BSlot sl;
                    short sample;
                    int vol;
                    int volLeft;
                    int volRight;

                    sl = chip.slots[i];
                    if (sl.active == 0 || sl.Muted != 0)
                    {
                        //outputs[0][j] += 0;
                        //outputs[1][j] += 0;
                        continue;
                    }

                    sample = (short)((sl.sample1 * (0x10000 - sl.stepptr) + sl.sample2 * sl.stepptr) >> 16);
                    vol = sl.TL + (sl.env_vol >> 2) + ymf278b_slot_compute_am(sl);

                    volLeft = vol + pan_left[sl.pan] + vl;
                    volRight = vol + pan_right[sl.pan] + vr;
                    // TODO prob doesn't happen in real chip
                    //volLeft  = std::max(0, volLeft);
                    //volRight = std::max(0, volRight);
                    volLeft &= 0x3FF;   // catch negative Volume values in a hardware-like way
                    volRight &= 0x3FF;  // (anything beyond 0x100 results in *0)

                    outputs[0][j] += (sample * chip.volume[volLeft]) >> 17;
                    outputs[1][j] += (sample * chip.volume[volRight]) >> 17;

                    if (sl.lfo_active != 0 && sl.vib != 0)
                    {
                        int oct;
                        int step;

                        oct = sl.OCT;
                        if ((oct & 8) != 0)
                            oct |= -8;
                        oct += 5;
                        step = (int)((sl.FN | 1024) + ymf278b_slot_compute_vib(sl));
                        if (oct >= 0)
                            step <<= oct;
                        else
                            step >>= -oct;
                        sl.stepptr += step;
                    }
                    else
                        sl.stepptr += sl.step;

                    while (sl.stepptr >= 0x10000)
                    {
                        sl.stepptr -= 0x10000;
                        sl.sample1 = sl.sample2;

                        sl.sample2 = ymf278b_getSample(chip, sl);
                        if (sl.pos >= sl.endaddr)
                            sl.pos = (int)(sl.pos - sl.endaddr + sl.loopaddr);
                        else
                            sl.pos++;
                    }
                }
                ymf278b_advance(chip);
            }
        }

        private void ymf278b_keyOnHelper(YMF278BChip chip, YMF278BSlot slot)
        {
            int oct;
            int step;

            slot.active = 1;

            oct = slot.OCT;
            if ((oct & 8) != 0)
                oct |= -8;
            oct += 5;
            step = (int)(slot.FN | 1024);
            if (oct >= 0)
                step <<= oct;
            else
                step >>= -oct;
            slot.step = step;
            slot.state = EG_ATT;
            slot.stepptr = 0;
            slot.pos = 0;
            slot.sample1 = ymf278b_getSample(chip, slot);
            slot.pos = 1;
            slot.sample2 = ymf278b_getSample(chip, slot);
        }

        private void ymf278b_A_w(YMF278BChip chip, byte reg, byte data)
        {
            switch (reg)
            {
                case 0x02:
                    //chip.timer_a_count = data;
                    //ymf278b_timer_a_reset(chip);
                    break;
                case 0x03:
                    //chip.timer_b_count = data;
                    //ymf278b_timer_b_reset(chip);
                    break;
                case 0x04:
                    /*if(data & 0x80)
                        chip.current_irq = 0;
                    else
                    {
                        byte old_enable = chip.enable;
                        chip.enable = data;
                        chip.current_irq &= ~data;
                        if((old_enable ^ data) & 1)
                            ymf278b_timer_a_reset(chip);
                        if((old_enable ^ data) & 2)
                            ymf278b_timer_b_reset(chip);
                    }
                    ymf278b_irq_check(chip);*/
                    break;
                default:
                    //#ifdef _DEBUG
                    //   logerror("YMF278B:  Port A write %02x, %02x\n", reg, data);
                    //#endif
                    chip.ymf262.ymf262_write(chip.fmchip, 1, data);
                    //chip.ymf262.Write(0, 0, reg, data);
                    if ((reg & 0xF0) == 0xB0 && (data & 0x20) != 0)  // Key On set
                        chip.FMEnabled = 0x01;
                    else if (reg == 0xBD && (data & 0x1F) != 0)  // one of the Rhythm bits set
                        chip.FMEnabled = 0x01;
                    break;
            }
        }

        private void ymf278b_B_w(YMF278BChip chip, byte reg, byte data)
        {
            switch (reg)
            {
                case 0x05:  // OPL3/OPL4 Enable
                            // Bit 1 enables OPL4 WaveTable Synth
                    chip.exp = data;
                    chip.ymf262.ymf262_write(chip.fmchip, 3, data & ~0x02);
                    break;
                default:
                    chip.ymf262.ymf262_write(chip.fmchip, 3, data);
                    if ((reg & 0xF0) == 0xB0 && (data & 0x20) != 0)
                        chip.FMEnabled = 0x01;
                    break;
            }
            //#ifdef _DEBUG
            // logerror("YMF278B:  Port B write %02x, %02x\n", reg, data);
            //#endif
        }

        private void ymf278b_C_w(YMF278BChip chip, byte reg, byte data)
        {
            //System.err.printf("ymf278b_C_w reg:{0:x02} dat:{1:x02}", reg, data);

            // Handle slot registers specifically
            if (reg >= 0x08 && reg <= 0xF7)
            {
                int snum = (reg - 8) % 24;
                YMF278BSlot slot = chip.slots[snum];
                int _base;
                Tuple< byte[],Integer> buf;
                int oct;
                int step;

                switch ((reg - 8) / 24)
                {
                    case 0:
                        //loadTime = time + LOAD_DELAY;

                        slot.wave = (short)((slot.wave & 0x100) | data);
                        _base = (slot.wave < 384 || chip.wavetblhdr == 0) ?
                                (slot.wave * 12) :
                                (chip.wavetblhdr * 0x80000 + ((slot.wave - 384) * 12));
                        buf = ymf278b_readMemAddr(chip, _base);

                        slot.bits = (byte)((buf.Item1[buf.Item2 + 0] & 0xC0) >> 6);
                        ymf278b_slot_set_lfo(slot, (buf.Item1[buf.Item2 + 7] >> 3) & 7);
                        slot.vib = (byte)(buf.Item1[buf.Item2 + 7] & 7);
                        slot.AR = (byte)(buf.Item1[buf.Item2 + 8] >> 4);
                        slot.D1R = (byte)(buf.Item1[buf.Item2 + 8] & 0xF);
                        slot.DL = (int)(dl_tab[buf.Item1[buf.Item2 + 9] >> 4]);
                        slot.D2R = (byte)(buf.Item1[buf.Item2 + 9] & 0xF);
                        slot.RC = (byte)(buf.Item1[buf.Item2 + 10] >> 4);
                        slot.RR = (byte)(buf.Item1[buf.Item2 + 10] & 0xF);
                        slot.AM = (byte)(buf.Item1[buf.Item2 + 11] & 7);
                        slot.startaddr = (int)(buf.Item1[buf.Item2 + 2] | (buf.Item1[buf.Item2 + 1] << 8) | ((buf.Item1[buf.Item2 + 0] & 0x3F) << 16));
                        slot.loopaddr = (int)(buf.Item1[buf.Item2 + 4] + (buf.Item1[buf.Item2 + 3] << 8));
                        slot.endaddr = (int)(((buf.Item1[buf.Item2 + 6] + (buf.Item1[buf.Item2 + 5] << 8)) ^ 0xFFFF));

                        if ((chip.regs[reg + 4] & 0x080) != 0)
                            ymf278b_keyOnHelper(chip, slot);
                        break;
                    case 1:
                        slot.wave = (short)((slot.wave & 0xFF) | ((data & 0x1) << 8));
                        slot.FN = (short)((slot.FN & 0x380) | (data >> 1));

                        oct = slot.OCT;
                        if ((oct & 8) != 0)
                            oct |= -8;
                        oct += 5;
                        step = (int)(slot.FN | 1024);
                        if (oct >= 0)
                            step <<= oct;
                        else
                            step >>= -oct;
                        slot.step = step;
                        break;
                    case 2:
                        slot.FN = (short)((slot.FN & 0x07F) | ((data & 0x07) << 7));
                        slot.PRVB = (byte)((data & 0x08) >> 3);
                        slot.OCT = (byte)((data & 0xF0) >> 4);

                        oct = slot.OCT;
                        if ((oct & 8) != 0)
                            oct |= -8;
                        oct += 5;
                        step = (int)(slot.FN | 1024);
                        if (oct >= 0)
                            step <<= oct;
                        else
                            step >>= -oct;
                        slot.step = step;
                        break;
                    case 3:
                        slot.TL = (byte)(data >> 1);
                        slot.LD = (byte)(data & 0x1);

                        // TODO
                        if (slot.LD != 0)
                        {
                            // directly change volume
                        }
                        else
                        {
                            // interpolate volume
                        }
                        break;
                    case 4:
                        if ((data & 0x10) != 0)
                        {
                            // output to DO1 pin:
                            // this pin is not used in moonsound
                            // we emulate this by muting the sound
                            slot.pan = 8; // both left/right -inf dB
                        }
                        else
                            slot.pan = (byte)(data & 0x0F);

                        if ((data & 0x020) != 0)
                        {
                            // LFO reset
                            slot.lfo_active = 0;
                            slot.lfo_cnt = 0;
                            slot.lfo_max = lfo_period[slot.vib];
                            slot.lfo_step = 0;
                        }
                        else
                        {
                            // LFO activate
                            slot.lfo_active = 1;
                        }

                        switch (data >> 6)
                        {
                            case 0: // tone off, no damp
                                if (slot.active != 0 && (slot.state != EG_REV))
                                    slot.state = EG_REL;
                                break;
                            case 2: // tone on, no damp
                                if ((chip.regs[reg] & 0x080) == 0)
                                    ymf278b_keyOnHelper(chip, slot);
                                break;
                            case 1: // tone off, damp
                            case 3: // tone on,  damp
                                slot.state = EG_DMP;
                                break;
                        }
                        break;
                    case 5:
                        slot.vib = (byte)(data & 0x7);
                        ymf278b_slot_set_lfo(slot, (data >> 3) & 0x7);
                        break;
                    case 6:
                        slot.AR = (byte)(data >> 4);
                        slot.D1R = (byte)(data & 0xF);
                        break;
                    case 7:
                        slot.DL = (int)(dl_tab[data >> 4]);
                        slot.D2R = (byte)(data & 0xF);
                        break;
                    case 8:
                        slot.RC = (byte)(data >> 4);
                        slot.RR = (byte)(data & 0xF);
                        break;
                    case 9:
                        slot.AM = (byte)(data & 0x7);
                        break;
                }
            }
            else
            {
                // All non-slot registers
                switch (reg & 0xff)
                {
                    case 0x00: // TEST
                    case 0x01:
                        break;

                    case 0x02:
                        chip.wavetblhdr = (byte)((data >> 2) & 0x7);
                        chip.memmode = (byte)(data & 1);
                        break;

                    case 0x03:
                        chip.memadr = (chip.memadr & 0x00FFFF) | (data << 16);
                        break;

                    case 0x04:
                        chip.memadr = (chip.memadr & 0xFF00FF) | (data << 8);
                        break;

                    case 0x05:
                        chip.memadr = (chip.memadr & 0xFFFF00) | data;
                        break;

                    case 0x06:  // memory data
                                //busyTime = time + MEM_WRITE_DELAY;
                        ymf278b_writeMem(chip, chip.memadr, data);
                        chip.memadr = (chip.memadr + 1) & 0xFFFFFF;
                        break;

                    case 0xF8:
                        // TODO use these
                        chip.fm_l = data & 0x7;
                        chip.fm_r = (data >> 3) & 0x7;
                        break;

                    case 0xF9:
                        chip.pcm_l = data & 0x7;
                        chip.pcm_r = (data >> 3) & 0x7;
                        break;
                }
            }

            chip.regs[reg] = data;
        }

        private byte ymf278b_readReg(YMF278BChip chip, byte reg)
        {
            // no need to call updateStream(time)
            byte result;
            switch (reg)
            {
                case 2: // 3 upper bits are device ID
                    result = (byte)((chip.regs[2] & 0x1F) | 0x20);
                    break;

                case 6: // Memory Data Register
                        //busyTime = time + MEM_READ_DELAY;
                    result = ymf278b_readMem(chip, chip.memadr);
                    chip.memadr = (chip.memadr + 1) & 0xFFFFFF;
                    break;

                default:
                    result = chip.regs[reg];
                    break;
            }

            return result;
        }

        private byte ymf278b_peekReg(YMF278BChip chip, byte reg)
        {
            byte result;

            switch (reg)
            {
                case 2: // 3 upper bits are device ID
                    result = (byte)((chip.regs[2] & 0x1F) | 0x20);
                    break;

                case 6: // Memory Data Register
                    result = ymf278b_readMem(chip, chip.memadr);
                    break;

                default:
                    result = chip.regs[reg];
                    break;
            }
            return result;
        }

        private byte ymf278b_readStatus(YMF278BChip chip)
        {
            byte result = 0;
            //if (time < busyTime)
            // result |= 0x01;
            //if (time < loadTime)
            // result |= 0x02;
            return result;
        }

        //WRITE8_DEVICE_HANDLER( ymf278b_w )
        public void ymf278b_w(byte ChipID, int offset, byte data)
        {
            //YMF278BChip *chip = get_safe_token(device);
            YMF278BChip chip = YMF278BData[ChipID];

            switch (offset)
            {
                case 0:
                    chip.port_A = data;
                    chip.ymf262.ymf262_write(chip.fmchip, offset, data);
                    break;

                case 1:
                    ymf278b_A_w(chip, chip.port_A, data);
                    break;

                case 2:
                    chip.port_B = data;
                    chip.ymf262.ymf262_write(chip.fmchip, offset, data);
                    break;

                case 3:
                    ymf278b_B_w(chip, chip.port_B, data);
                    break;

                case 4:
                    chip.port_C = data;
                    break;

                case 5:
                    // PCM regs are only accessible if NEW2 is set
                    if ((~chip.exp & 2) != 0)
                        break;

                    ymf278b_C_w(chip, chip.port_C, data);
                    break;

                default:
                    //# ifdef _DEBUG
                    //logerror("YMF278B: unexpected write at offset %X to ymf278b = %02X\n", offset, data);
                    //#endif
                    break;
            }
        }

        private void ymf278b_clearRam(YMF278BChip chip)
        {
            for (int i = 0; i < chip.RAMSize; i++) chip.ram[i] = 0;
            //memset(chip.ram, 0, chip.RAMSize);
        }

        private void ymf278b_load_rom(YMF278BChip chip) throws IOException
        {
            Path ROM_FILENAME = Paths.get("yrw801.rom");
            //char[] FileName;
            //Object hFile;
            //size_t RetVal;
            if (romPath != null || !romPath.isEmpty())
            {
                ROM_FILENAME = Paths.get(romPath, ROM_FILENAME.toString());
            }

            if (ROMFileSize == 0)
            {
                ROMFileSize = 0x00200000;
                //ROMFile = new byte[ROMFileSize];// (byte*)malloc(ROMFileSize);
                //for (int i = 0; i < ROMFileSize; i++) ROMFile[i] = 0xff;
                //memset(ROMFile, 0xFF, ROMFileSize);

                //FileName = FindFile(ROM_FILENAME);

                ROMFile = null;
                if (romStream == null)
                {
                    if (Files.exists(ROM_FILENAME))
                    {
                        try
                        {
                            ROMFile = Files.readAllBytes(ROM_FILENAME);
                            ROMFileSize = (int)ROMFile.length;
                        }
                        catch (Exception e)
                        {
                        }
                    }
                }
                else
                {
                    try (Stream st = romStream.apply(ROM_FILENAME.toString())) {
                        ROMFile = Common.ReadAllBytes(st);
                    }
                    ROMFileSize = (int)ROMFile.length;
                }

                //if (ROMFile != null)
                //{
                //    RetVal = fread(ROMFile, 0x01, ROMFileSize, hFile);
                //    fclose(hFile);
                //    if (ROMFile.length != ROMFileSize)
                //        System.err.printf("Error while reading OPL4 Sample ROM ({0})!", ROM_FILENAME);
                //}
                //else
                //{
                //    System.err.printf("Warning! OPL4 Sample ROM ({0}) not found!", ROM_FILENAME);
                //}
            }



            chip.ROMSize = ROMFileSize;
            if (ROMFile != null && ROMFile.length > 0)
            {
                chip.rom = new byte[chip.ROMSize];// (byte*)malloc(chip.ROMSize);
                for (int i = 0; i < chip.ROMSize; i++)
                {
                    if (i < ROMFile.length) chip.rom[i] = ROMFile[i];
                    else chip.rom[i] = 0;
                }
            }
            else
            {
                chip.rom = new byte[chip.ROMSize];
                for (int i = 0; i < chip.ROMSize; i++) chip.rom[i] = 0;
            }
            //memcpy(chip.rom, ROMFile, chip.ROMSize);

            return;
        }

        public interface callback extends Consumer<Integer>{};
        private int ymf278b_init(YMF278BChip chip, int clock, callback cb)
        {
            int rate;

            rate = clock / 768;
            //if (((CHIP_SAMPLING_MODE & 0x01) && rate < CHIP_SAMPLE_RATE) ||
            // CHIP_SAMPLING_MODE == 0x02)
            // rate = CHIP_SAMPLE_RATE;
            chip.fmchip = chip.ymf262.ymf262_init(clock * 8 / 19, rate);
            chip.FMEnabled = 0x00;

            chip.rom = null;
            chip.irq_callback = cb;
            //chip.timer_a = timer_alloc(device.machine, ymf278b_timer_a_tick, chip);
            //chip.timer_b = timer_alloc(device.machine, ymf278b_timer_b_tick, chip);
            chip.clock = clock;

            try {
                ymf278b_load_rom(chip);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            chip.RAMSize = 0x00080000;
            chip.ram = new byte[chip.RAMSize];// (byte*)malloc(chip.RAMSize);
            ymf278b_clearRam(chip);

            return rate;
        }

        //static DEVICE_START( ymf278b )
        public int device_start_ymf278b(byte ChipID, int clock)
        {
            //ymf278b_interface defintrf = null;// { 0 };
            ymf278b_interface intf=new ymf278b_interface();
            int i;
            YMF278BChip chip;
            int rate;

            if (ChipID >= MAX_CHIPS)
                return 0;

            chip = YMF278BData[ChipID];

            //chip.device = device;
            //intf = (device.static_config != NULL) ? (final ymf278b_interface *)device.static_config : &defintrf;
            //intf = defintrf;

            rate = ymf278b_init(chip, clock, intf.irq_callback);
            //chip.stream = stream_create(device, 0, 2, device.clock/768, chip, ymf278b_pcm_update);

            chip.memadr = 0; // avoid UMR

            // Volume table, 1 = -0.375dB, 8 = -3dB, 256 = -96dB
            for (i = 0; i < 256; i++)
            {
                int vol_mul = 0x20 - (i & 0x0F);    // 0x10 values per 6 db
                int vol_shift = 5 + (i >> 4);       // approximation: -6 dB == divide by two (shift right)
                chip.volume[i] = (0x8000 * vol_mul) >> vol_shift;
            }
            //chip.volume[i] = (int)(32768 * Math.pow(2.0, (-0.375 / 6) * i));
            for (i = 256; i < 256 * 4; i++)
                chip.volume[i] = 0;
            for (i = 0; i < 24; i++)
                chip.slots[i].Muted = 0x00; ;

            return rate;
        }

        //static DEVICE_STOP( ymf278 )
        public void device_stop_ymf278b(byte ChipID)
        {
            YMF278BChip chip = YMF278BData[ChipID];

            chip.ymf262.ymf262_shutdown(chip.fmchip);
            //free(chip.rom);
            chip.rom = null;

            return;
        }

        public void device_reset_ymf278b(byte ChipID)
        {
            YMF278BChip chip = YMF278BData[ChipID];
            int i;

            chip.ymf262.ymf262_reset_chip(chip.fmchip);
            chip.FMEnabled = 0x00;

            chip.eg_cnt = 0;

            for (i = 0; i < 24; i++)
                ymf278b_slot_reset(chip.slots[i]);
            for (i = 255; i >= 0; i--)  // reverse order to avoid UMR
                ymf278b_C_w(chip, (byte)i, (byte)0);

            chip.wavetblhdr = chip.memmode = 0; chip.memadr = 0;
            chip.fm_l = chip.fm_r = 3;
            chip.pcm_l = chip.pcm_r = 0;
            //busyTime = time;
            //loadTime = time;
        }

        public void ymf278b_write_rom(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData)
        {
            YMF278BChip chip = YMF278BData[ChipID];

            if (chip.ROMSize != ROMSize)
            {
                chip.rom = new byte[ROMSize];// (byte*)realloc(chip.rom, ROMSize);
                chip.ROMSize = (int)ROMSize;
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

        public void ymf278b_write_rom(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData,int srcStartAddress)
        {
            YMF278BChip chip = YMF278BData[ChipID];

            if (chip.ROMSize != ROMSize)
            {
                chip.rom = new byte[ROMSize];// (byte*)realloc(chip.rom, ROMSize);
                chip.ROMSize = (int)ROMSize;
                for (int i = 0; i < ROMSize; i++) chip.rom[i] = (byte) 0xff;
                //memset(chip.rom, 0xFF, ROMSize);
            }
            if (DataStart > ROMSize)
                return;
            if (DataStart + DataLength > ROMSize)
                DataLength = ROMSize - DataStart;

            for (int i = 0; i < DataLength; i++) chip.rom[i + DataStart] = ROMData[i+srcStartAddress];
            //memcpy(chip.rom + DataStart, ROMData, DataLength);

            return;
        }

        public void ymf278b_write_ram(byte ChipID, int DataStart, int DataLength, byte[] RAMData, int srcStartAddress)
        {
            YMF278BChip chip = YMF278BData[ChipID];

            if (DataStart > chip.RAMSize)
                return;
            if (DataStart + DataLength >chip.RAMSize)
                DataLength = (int)chip.RAMSize - DataStart;

            for (int i = 0; i < DataLength; i++) chip.ram[i + DataStart] = RAMData[i + srcStartAddress];
            //memcpy(chip.rom + DataStart, ROMData, DataLength);

            return;
        }

        public void ymf278b_set_mute_mask(byte ChipID, int MuteMaskFM, int MuteMaskWT)
        {
            YMF278BChip chip = YMF278BData[ChipID];
            byte CurChn;

            chip.ymf262.ymf262_set_mutemask(chip.fmchip, MuteMaskFM);
            for (CurChn = 0; CurChn < 24; CurChn++)
                chip.slots[CurChn].Muted = (byte)((MuteMaskWT >> CurChn) & 0x01);

            return;
        }

        @Override public int Write(byte ChipID, int port, int adr, int data)
        {
            return YMF278B_Write(ChipID, port, (byte)adr, (byte)data);
        }
    }

