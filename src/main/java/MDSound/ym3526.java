﻿package MDSound;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import MDSound.common.TriConsumer;

    public class ym3526 extends Instrument
    {
        @Override public String getName()  { return "YM3526"; } 
        @Override public String getShortName () { return "OPL"; } 

        @Override public void Reset(byte ChipID)
        {
            visVolume = new int[][][] {
                new int[][] { new int[] { 0, 0 } }
                , new int[][] { new int[] { 0, 0 } }
            };

            device_reset_ym3526(ChipID);
        }

        private final int DefaultYM3526ClockValue = 3579545;

        @Override public int Start(byte ChipID, int clock)
        {
            return (int)device_start_ym3526(ChipID, (int)DefaultYM3526ClockValue);
        }

        @Override public int Start(byte ChipID, int clock, int ClockValue, Object... option)
        {
            return (int)device_start_ym3526(ChipID, (int)ClockValue);
        }

        @Override public void Stop(byte ChipID)
        {
            device_stop_ym3526(ChipID);
        }

        @Override public void Update(byte ChipID, int[][] outputs, int samples)
        {
            ym3526_stream_update(ChipID, outputs, samples);

            visVolume[ChipID][0][0] = outputs[0][0];
            visVolume[ChipID][0][1] = outputs[1][0];
        }

        @Override public int Write(byte ChipID, int port, int adr, int data)
        {
            ym3526_w(ChipID, 0x00, (byte)adr);
            ym3526_w(ChipID, 0x01, (byte)data);
            return 0;
        }



        // #region 3526intf.h

        //#pragma once

        //        /*typedef struct _ym3526_interface ym3526_interface;
        //        struct _ym3526_interface
        //        {
        //            //void (*handler)(final device_config *device, int linestate);
        //            void (*handler)(int linestate);
        //        };*/

        //        /*READ8_DEVICE_HANDLER( ym3526_r );
        //        WRITE8_DEVICE_HANDLER( ym3526_w );

        //        READ8_DEVICE_HANDLER( ym3526_status_port_r );
        //        READ8_DEVICE_HANDLER( ym3526_read_port_r );
        //        WRITE8_DEVICE_HANDLER( ym3526_control_port_w );
        //        WRITE8_DEVICE_HANDLER( ym3526_write_port_w );

        //        DEVICE_GET_INFO( ym3526 );
        //        // #define SOUND_YM3526 DEVICE_GET_INFO_NAME( ym3526 )*/
        //        void ym3526_stream_update(byte ChipID, stream_sample_t** outputs, int samples);
        //        int device_start_ym3526(byte ChipID, int clock);
        //        void device_stop_ym3526(byte ChipID);
        //        void device_reset_ym3526(byte ChipID);

        //        byte ym3526_r(byte ChipID, offs_t offset);
        //        void ym3526_w(byte ChipID, offs_t offset, byte data);

        //        byte ym3526_status_port_r(byte ChipID, offs_t offset);
        //        byte ym3526_read_port_r(byte ChipID, offs_t offset);
        //        void ym3526_control_port_w(byte ChipID, offs_t offset, byte data);
        //        void ym3526_write_port_w(byte ChipID, offs_t offset, byte data);

        //        void ym3526_set_mute_mask(byte ChipID, (int)32 MuteMask);

        // #endregion

        // #region 3526intf.c

        /******************************************************************************
        * FILE
        *   Yamaha 3812 emulator interface - MAME VERSION
        *
        * CREATED BY
        *   Ernesto Corvi
        *
        * UPDATE LOG
        *   JB  28-04-2002  Fixed simultaneous usage of all three different chip types.
        *                       Used real sample rate when resample filter is active.
        *       AAT 12-28-2001  Protected Y8950 from accessing unmapped port and keyboard handlers.
        *   CHS 1999-01-09  Fixes new ym3812 emulation interface.
        *   CHS 1998-10-23  Mame streaming sound chip update
        *   EC  1998        Created Interface
        *
        * NOTES
        *
        ******************************************************************************/
        //# include "mamedef.h"
        //#include "attotime.h"
        //#include "sndintrf.h"
        //#include "streams.h"
        //#include "cp(int)rf.h"
        //# include "3526intf.h"
        //# include "fmopl.h"

        static class ym3526_state
        {
            //sound_stream *	stream;
            //emu_timer *		timer[2];
            public FM_OPL chip;
            //final ym3526_interface *intf;
            //final device_config *device;
        };


        private static byte CHIP_SAMPLING_MODE = 0;
        //private int CHIP_SAMPLE_RATE;
        private final int MAX_CHIPS = 0x02;
        static ym3526_state[] YM3526Data = new ym3526_state[] { new ym3526_state(), new ym3526_state() };

        /*INLINE ym3526_state *get_safe_token(final device_config *device)
        {
            assert(device != NULL);
            assert(device.token != NULL);
            assert(device.type == SOUND);
            assert(sound_get_type(device) == SOUND_YM3526);
            return (ym3526_state *)device.token;
        }*/


        /* IRQ Handler */
        private void IRQHandler(ym3526_state param, int irq)
        {
            ym3526_state info = (ym3526_state)param;
            //if (info.intf.handler) (info.intf.handler)(info.device, irq ? ASSERT_LINE : CLEAR_LINE);
            //if (info.intf.handler) (info.intf.handler)(irq ? ASSERT_LINE : CLEAR_LINE);
        }
        /* Timer overflow callback from timer.c */
        /*static TIMER_CALLBACK( timer_callback_0 )
        {
            ym3526_state *info = (ym3526_state *)ptr;
            ym3526_timer_over(info.chip,0);
        }
        static TIMER_CALLBACK( timer_callback_1 )
        {
            ym3526_state *info = (ym3526_state *)ptr;
            ym3526_timer_over(info.chip,1);
        }*/
        /* TimerHandler from fm.c */
        //static void TimerHandler(void *param,int c,attotime period)
        private void TimerHandler(ym3526_state param, int c, int period)
        {
            ym3526_state info = (ym3526_state)param;
            //if( attotime_compare(period, attotime_zero) == 0 )
            if (period == 0)
            {   /* Reset FM Timer */
                //timer_enable(info.timer[c], 0);
            }
            else
            {   /* Start FM Timer */
                //timer_adjust_oneshot(info.timer[c], period, 0);
            }
        }


        //static STREAM_UPDATE( ym3526_stream_update )
        public void ym3526_stream_update(byte ChipID, int[][] outputs, int samples)
        {
            //ym3526_state *info = (ym3526_state *)param;
            ym3526_state info = YM3526Data[ChipID];
            ym3526_update_one(info.chip, outputs, samples);
        }

        private int[][] DUMMYBUF = new int[][] { null, null };
        private void _stream_update(ym3526_state param/*, int interval*/)
        {
            ym3526_state info = (ym3526_state)param;
            //stream_update(info.stream);

            ym3526_update_one(info.chip, DUMMYBUF, 0);
        }


        //static DEVICE_START( ym3526 )
        public int device_start_ym3526(byte ChipID, int clock)
        {
            //static final ym3526_interface dummy = { 0 };
            //ym3526_state *info = get_safe_token(device);
            ym3526_state info;
            int rate;

            if (ChipID >= MAX_CHIPS)
                return 0;

            info = YM3526Data[ChipID];
            rate = (clock & 0x7FFFFFFF) / 72;
            if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
                rate = CHIP_SAMPLE_RATE;
            //info.intf = device.static_config ? (final ym3526_interface *)device.static_config : &dummy;
            //info.intf = &dummy;
            //info.device = device;

            /* stream system initialize */
            info.chip = ym3526_init((int)clock, (int)rate);
            //assert_always(info.chip != NULL, "Error creating YM3526 chip");

            //info.stream = stream_create(device,0,1,rate,info,ym3526_stream_update);
            /* YM3526 setup */
            ym3526_set_timer_handler(info.chip, this::TimerHandler, info);
            ym3526_set_irq_handler(info.chip, this::IRQHandler, info);
            ym3526_set_update_handler(info.chip, this::_stream_update, info);

            //info.timer[0] = timer_alloc(device.machine, timer_callback_0, info);
            //info.timer[1] = timer_alloc(device.machine, timer_callback_1, info);

            return rate;
        }

        //static DEVICE_STOP( ym3526 )
        public void device_stop_ym3526(byte ChipID)
        {
            //ym3526_state *info = get_safe_token(device);
            ym3526_state info = YM3526Data[ChipID];
            ym3526_shutdown(info.chip);
        }

        //static DEVICE_RESET( ym3526 )
        public void device_reset_ym3526(byte ChipID)
        {
            //ym3526_state *info = get_safe_token(device);
            ym3526_state info = YM3526Data[ChipID];
            ym3526_reset_chip(info.chip);
        }


        //READ8_DEVICE_HANDLER( ym3526_r )
        public byte ym3526_r(byte ChipID, int offset)
        {
            //ym3526_state *info = get_safe_token(device);
            ym3526_state info = YM3526Data[ChipID];
            return ym3526_read(info.chip, offset & 1);
        }

        //WRITE8_DEVICE_HANDLER( ym3526_w )
        public void ym3526_w(byte ChipID, int offset, byte data)
        {
            //ym3526_state *info = get_safe_token(device);
            ym3526_state info = YM3526Data[ChipID];
            if (info==null || info.chip == null) return;

            ym3526_write(info.chip, offset & 1, data);
        }

        //READ8_DEVICE_HANDLER( ym3526_status_port_r )
        public byte ym3526_status_port_r(byte ChipID, int offset)
        {
            return ym3526_r(ChipID, 0);
        }
        //READ8_DEVICE_HANDLER( ym3526_read_port_r )
        public byte ym3526_read_port_r(byte ChipID, int offset)
        {
            return ym3526_r(ChipID, 1);
        }
        //WRITE8_DEVICE_HANDLER( ym3526_control_port_w )
        public void ym3526_control_port_w(byte ChipID, int offset, byte data)
        {
            ym3526_w(ChipID, 0, data);
        }
        //WRITE8_DEVICE_HANDLER( ym3526_write_port_w )
        public void ym3526_write_port_w(byte ChipID, int offset, byte data)
        {
            ym3526_w(ChipID, 1, data);
        }


        public void ym3526_set_mute_mask(byte ChipID, int MuteMask)
        {
            ym3526_state info = YM3526Data[ChipID];
            opl_set_mute_mask(info.chip, MuteMask);
        }


        /**************************************************************************
         * Generic get_info
         **************************************************************************/

        /*DEVICE_GET_INFO( ym3526 )
        {
            switch (state)
            {
                // --- the following bits of info are returned as 64-bit signed integers ---
                case DEVINFO_INT_TOKEN_BYTES:					info.i = sizeof(ym3526_state);				break;

                // --- the following bits of info are returned as pointers to data or functions ---
                case DEVINFO_FCT_START:							info.start = DEVICE_START_NAME( ym3526 );				break;
                case DEVINFO_FCT_STOP:							info.stop = DEVICE_STOP_NAME( ym3526 );				break;
                case DEVINFO_FCT_RESET:							info.reset = DEVICE_RESET_NAME( ym3526 );				break;

                // --- the following bits of info are returned as NULL-terminated strings ---
                case DEVINFO_STR_NAME:							strcpy(info.s, "YM3526");							break;
                case DEVINFO_STR_FAMILY:					strcpy(info.s, "Yamaha FM");						break;
                case DEVINFO_STR_VERSION:					strcpy(info.s, "1.0");								break;
                case DEVINFO_STR_SOURCE_FILE:						strcpy(info.s, __FILE__);							break;
                case DEVINFO_STR_CREDITS:					strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
            }
        }*/

        // #endregion

        // #region fmopl.h
        //#pragma once

        //#include "attotime.h"

        /* --- select emulation chips --- */
        //#define BUILD_YM3812 (HAS_YM3812)
        //#define BUILD_YM3526 (HAS_YM3526)
        //#define BUILD_Y8950  (HAS_Y8950)
        //#define BUILD_YM3812 1
        //#define BUILD_YM3526 1
        //# ifndef NO_Y8950
        //#define BUILD_Y8950  1
        //#else
        //#define BUILD_Y8950  0
        //#endif

        /* select output bits size of output : 8 or 16 */
        private final int OPL_SAMPLE_BITS = 16;

        /* compiler dependence */
        /*#ifndef __OSDCOMM_H__
        // #define __OSDCOMM_H__
        typedef unsigned char	(int)8;   // unsigned  8bit
        typedef unsigned short	int;  // unsigned 16bit
        typedef unsigned int	(int)32;  // unsigned 32bit
        typedef signed char		INT8;    // signed  8bit
        typedef signed short	INT16;   // signed 16bit
        typedef signed int		int;   // signed 32bit
        // #endif*/ /* __OSDCOMM_H__ */

        //typedef stream_sample_t OPLSAMPLE;
        /*
        // #if (OPL_SAMPLE_BITS==16)
        typedef INT16 OPLSAMPLE;
        // #endif
        // #if (OPL_SAMPLE_BITS==8)
        typedef INT8 OPLSAMPLE;
        // #endif
        */

        //typedef void (*OPL_TIMERHANDLER)(void *param,int timer,attotime period);
        //typedef void (* OPL_TIMERHANDLER) (void* param, int timer, int period);
        //typedef void (* OPL_IRQHANDLER) (void* param, int irq);
        //typedef void (* OPL_UPDATEHANDLER) (void* param/*,int min_interval_us*/);
        //typedef void (* OPL_PORTHANDLER_W) (void* param, unsigned char data);
        //typedef unsigned char (* OPL_PORTHANDLER_R) (void* param);
        public interface OPL_TIMERHANDLER extends TriConsumer<ym3526_state, Integer, Integer> {} // (ym3526_state param, int timer, int period);
        public interface OPL_IRQHANDLER extends BiConsumer<ym3526_state, Integer> {} // (ym3526_state param, int irq);
        public interface OPL_UPDATEHANDLER extends Consumer<ym3526_state> {} // (ym3526_state param/*,int min_interval_us*/);
        public interface OPL_PORTHANDLER_W extends BiConsumer<ym3526_state, Byte> {} // (ym3526_state param, byte data);
        public interface OPL_PORTHANDLER_R extends Function<ym3526_state, Byte>{} // (ym3526_state param);


        //#if BUILD_YM3812

        //void* ym3812_init((int)32 clock, (int)32 rate);
        //void ym3812_shutdown(void* chip);
        //void ym3812_reset_chip(void* chip);
        //int ym3812_write(void* chip, int a, int v);
        //unsigned char ym3812_read(void* chip, int a);
        //int ym3812_timer_over(void* chip, int c);
        //void ym3812_update_one(void* chip, OPLSAMPLE** buffer, int length);

        //void ym3812_set_timer_handler(void* chip, OPL_TIMERHANDLER TimerHandler, void* param);
        //void ym3812_set_irq_handler(void* chip, OPL_IRQHANDLER IRQHandler, void* param);
        //void ym3812_set_update_handler(void* chip, OPL_UPDATEHANDLER UpdateHandler, void* param);

        //#endif /* BUILD_YM3812 */


        //#if BUILD_YM3526

        /*
        ** Initialize YM3526 emulator(s).
        **
        ** 'num' is the number of virtual YM3526's to allocate
        ** 'clock' is the chip clock in Hz
        ** 'rate' is sampling rate
        */
        //void* ym3526_init(UINT32 clock, UINT32 rate);
        ///* shutdown the YM3526 emulators*/
        //void ym3526_shutdown(void* chip);
        //void ym3526_reset_chip(void* chip);
        //int ym3526_write(void* chip, int a, int v);
        //unsigned char ym3526_read(void* chip, int a);
        //int ym3526_timer_over(void* chip, int c);
        ///*
        //** Generate samples for one of the YM3526's
        //**
        //** 'which' is the virtual YM3526 number
        //** '*buffer' is the output buffer pointer
        //** 'length' is the number of samples that should be generated
        //*/
        //void ym3526_update_one(void* chip, OPLSAMPLE** buffer, int length);

        //void ym3526_set_timer_handler(void* chip, OPL_TIMERHANDLER TimerHandler, void* param);
        //void ym3526_set_irq_handler(void* chip, OPL_IRQHANDLER IRQHandler, void* param);
        //void ym3526_set_update_handler(void* chip, OPL_UPDATEHANDLER UpdateHandler, void* param);

        //#endif /* BUILD_YM3526 */


        //#if BUILD_Y8950

        //        /* Y8950 port handlers */
        //        void y8950_set_port_handler(void* chip, OPL_PORTHANDLER_W PortHandler_w, OPL_PORTHANDLER_R PortHandler_r, void* param);
        //        void y8950_set_keyboard_handler(void* chip, OPL_PORTHANDLER_W KeyboardHandler_w, OPL_PORTHANDLER_R KeyboardHandler_r, void* param);
        //        void y8950_set_delta_t_memory(void* chip, void* deltat_mem_ptr, int deltat_mem_size);
        //        void y8950_write_pcmrom(void* chip, offs_t ROMSize, offs_t DataStart,
        //                                 offs_t DataLength, const UINT8* ROMData);

        //        void* y8950_init(UINT32 clock, UINT32 rate);
        //        void y8950_shutdown(void* chip);
        //        void y8950_reset_chip(void* chip);
        //        int y8950_write(void* chip, int a, int v);
        //        unsigned char y8950_read(void* chip, int a);
        //        int y8950_timer_over(void* chip, int c);
        //        void y8950_update_one(void* chip, OPLSAMPLE** buffer, int length);

        //        void y8950_set_timer_handler(void* chip, OPL_TIMERHANDLER TimerHandler, void* param);
        //        void y8950_set_irq_handler(void* chip, OPL_IRQHANDLER IRQHandler, void* param);
        //        void y8950_set_update_handler(void* chip, OPL_UPDATEHANDLER UpdateHandler, void* param);

        //#endif /* BUILD_Y8950 */

        //void opl_set_mute_mask(void* chip, UINT32 MuteMask);

        // #endregion

        // #region fmopl.c
        /*
        **
        ** File: fmopl.c - software implementation of FM sound generator
        **                                            types OPL and OPL2
        **
        ** Copyright Jarek Burczynski (bujar at mame dot net)
        ** Copyright Tatsuyuki Satoh , MultiArcadeMachineEmulator development
        **
        ** Version 0.72
        **

        Revision History:

        04-08-2003 Jarek Burczynski:
         - removed BFRDY hack. BFRDY is busy flag, and it should be 0 only when the chip
           handles memory read/write or during the adpcm synthesis when the chip
           requests another byte of ADPCM data.

        24-07-2003 Jarek Burczynski:
         - added a small hack for Y8950 status BFRDY flag (bit 3 should be set after
           some (unknown) delay). Right now it's always set.

        14-06-2003 Jarek Burczynski:
         - implemented all of the status register flags in Y8950 emulation
         - renamed y8950_set_delta_t_memory() parameters from _rom_ to _mem_ since
           they can be either RAM or ROM

        08-10-2002 Jarek Burczynski (thanks to Dox for the YM3526 chip)
         - corrected ym3526_read() to always set bit 2 and bit 1
           to HIGH state - identical to ym3812_read (verified on real YM3526)

        04-28-2002 Jarek Burczynski:
         - binary exact Envelope Generator (verified on real YM3812);
           compared to YM2151: the EG clock is equal to internal_clock,
           rates are 2 times slower and volume resolution is one bit less
         - modified interface functions (they no longer return pointer -
           that's internal to the emulator now):
            - new wrapper functions for OPLCreate: ym3526_init(), ym3812_init() and y8950_init()
         - corrected 'off by one' error in feedback calculations (when feedback is off)
         - enabled waveform usage (credit goes to Vlad Romascanu and zazzal22)
         - speeded up noise generator calculations (Nicola Salmoria)

        03-24-2002 Jarek Burczynski (thanks to Dox for the YM3812 chip)
         Complete rewrite (all verified on real YM3812):
         - corrected sin_tab and tl_tab data
         - corrected operator output calculations
         - corrected waveform_select_enable register;
           simply: ignore all writes to waveform_select register when
           waveform_select_enable == 0 and do not change the waveform previously selected.
         - corrected KSR handling
         - corrected Envelope Generator: attack shape, Sustain mode and
           Percussive/Non-percussive modes handling
         - Envelope Generator rates are two times slower now
         - LFO amplitude (tremolo) and phase modulation (vibrato)
         - rhythm sounds phase generation
         - white noise generator (big thanks to Olivier Galibert for mentioning Berlekamp-Massey algorithm)
         - corrected key on/off handling (the 'key' signal is ORed from three sources: FM, rhythm and CSM)
         - funky details (like ignoring output of operator 1 in BD rhythm sound when connect == 1)

        12-28-2001 Acho A. Tang
         - reflected Delta-T EOS status on Y8950 status port.
         - fixed subscription range of attack/decay tables


            To do:
                add delay before key off in CSM mode (see CSMKeyControll)
                verify volume of the FM part on the Y8950
        */

        //# include <math.h>
        //# include "mamedef.h"
        //# ifdef _DEBUG
        //# include <stdio.h>
        //#endif
        //# include <stdlib.h>
        //# include <string.h>	// for memset
        //# include <stddef.h>	// for NULL
        //#include "sndintrf.h"
        //# include "fmopl.h"
        //#if BUILD_Y8950
        //# include "ymdeltat.h"
        //#endif


        /* output final shift */
        //#if (OPL_SAMPLE_BITS ==16)
        private final int FINAL_SH = (0);
        private final int MAXOUT = (+32767);
        private final int MINOUT = (-32768);
        //#else
        //#define FINAL_SH	(8)
        //#define MAXOUT		(+127)
        //#define MINOUT		(-128)
        //#endif


        private final int FREQ_SH = 16;  /* 16.16 fixed point (frequency calculations) */
        private final int EG_SH = 16;  /* 16.16 fixed point (EG timing)              */
        private final int LFO_SH = 24;  /*  8.24 fixed point (LFO calculations)       */
        private final int TIMER_SH = 16;  /* 16.16 fixed point (timers calculations)    */

        private final int FREQ_MASK = ((1 << FREQ_SH) - 1);

        /* envelope output entries */
        private final static int ENV_BITS = 10;
        private final static int ENV_LEN = (1 << ENV_BITS);
        private final static double ENV_STEP = (128.0 / ENV_LEN);

        private final int MAX_ATT_INDEX = ((1 << (ENV_BITS - 1)) - 1);/*511*/
        private final int MIN_ATT_INDEX = (0);

        /* sinwave entries */
        private final int SIN_BITS = 10;
        private final int SIN_LEN = (1 << SIN_BITS);
        private final int SIN_MASK = (SIN_LEN - 1);

        private final static int TL_RES_LEN = (256);/* 8 bits addressing (real chip) */



        /* register number to channel number , slot offset */
        private final int SLOT1 = 0;
        private final int SLOT2 = 1;

        /* Envelope Generator phases */

        private final int EG_ATT = 4;
        private final int EG_DEC = 3;
        private final int EG_SUS = 2;
        private final int EG_REL = 1;
        private final int EG_OFF = 0;


        /* save output as raw 16-bit sample */

        /*#define SAVE_SAMPLE*/

        //# ifdef SAVE_SAMPLE
        //        INLINE signed int acc_calc(signed int value)
        //        {
        //            if (value >= 0)
        //            {
        //                if (value < 0x0200)
        //                    return (value & ~0);
        //                if (value < 0x0400)
        //                    return (value & ~1);
        //                if (value < 0x0800)
        //                    return (value & ~3);
        //                if (value < 0x1000)
        //                    return (value & ~7);
        //                if (value < 0x2000)
        //                    return (value & ~15);
        //                if (value < 0x4000)
        //                    return (value & ~31);
        //                return (value & ~63);
        //            }
        //            /*else value < 0*/
        //            if (value > -0x0200)
        //                return (~abs(value) & ~0);
        //            if (value > -0x0400)
        //                return (~abs(value) & ~1);
        //            if (value > -0x0800)
        //                return (~abs(value) & ~3);
        //            if (value > -0x1000)
        //                return (~abs(value) & ~7);
        //            if (value > -0x2000)
        //                return (~abs(value) & ~15);
        //            if (value > -0x4000)
        //                return (~abs(value) & ~31);
        //            return (~abs(value) & ~63);
        //        }


        //        static FILE* sample[1];
        //	#if 1	/*save to MONO file */
        //		#define SAVE_ALL_CHANNELS \
        //		{	signed int pom = acc_calc(lt); \
        //			fputc((unsigned short)pom&0xff,sample[0]); \
        //			fputc(((unsigned short)pom>>8)&0xff,sample[0]); \
        //		}
        //	#else	/*save to STEREO file */
        //		#define SAVE_ALL_CHANNELS \
        //		{	signed int pom = lt; \
        //			fputc((unsigned short)pom&0xff,sample[0]); \
        //			fputc(((unsigned short)pom>>8)&0xff,sample[0]); \
        //			pom = rt; \
        //			fputc((unsigned short)pom&0xff,sample[0]); \
        //			fputc(((unsigned short)pom>>8)&0xff,sample[0]); \
        //		}
        //#endif
        //#endif

        //#define LOG_CYM_FILE 0
        //static FILE * cymfile = NULL;



        private final int OPL_TYPE_WAVESEL = 0x01; /* waveform select     */
        private final int OPL_TYPE_ADPCM = 0x02; /* DELTA-T ADPCM unit  */
        private final int OPL_TYPE_KEYBOARD = 0x04; /* keyboard interface  */
        private final int OPL_TYPE_IO = 0x08; /* I/O port            */

        /* ---------- Generic interface section ---------- */
        private final int OPL_TYPE_YM3526 = (0);
        private final int OPL_TYPE_YM3812 = (OPL_TYPE_WAVESEL);
        private final int OPL_TYPE_Y8950 = (OPL_TYPE_ADPCM | OPL_TYPE_KEYBOARD | OPL_TYPE_IO);



        public class OPL_SLOT
        {

            public int ar;          /* attack rate: AR<<2           */
            public int dr;          /* decay rate:  DR<<2           */
            public int rr;          /* release rate:RR<<2           */
            public byte KSR;      /* key scale rate               */
            public byte ksl;      /* keyscale level               */
            public byte ksr;      /* key scale rate: kcode>>KSR   */
            public byte mul;      /* multiple: mul_tab[ML]        */

            /* Phase Generator */
            public int Cnt;     /* frequency counter            */
            public int Incr;        /* frequency counter step       */
            public byte FB;           /* feedback shift value         */
            //public int connect1;    /* slot1 output pointer         */
            public int ptrConnect1;    /* slot1 output pointer         */
            public int[] op1_out = new int[2];   /* slot1 output for feedback    */
            public byte CON;      /* connection (algorithm) type  */

            /* Envelope Generator */
            public byte eg_type;  /* percussive/non-percussive mode */
            public byte state;        /* phase type                   */
            public int TL;          /* total level: TL << 2         */
            public int TLL;      /* adjusted now TL              */
            public int volume;       /* envelope counter             */
            public int sl;          /* sustain level: sl_tab[SL]    */
            public byte eg_sh_ar; /* (attack state)               */
            public byte eg_sel_ar;    /* (attack state)               */
            public byte eg_sh_dr; /* (decay state)                */
            public byte eg_sel_dr;    /* (decay state)                */
            public byte eg_sh_rr; /* (release state)              */
            public byte eg_sel_rr;    /* (release state)              */
            public int key;     /* 0 = KEY OFF, >0 = KEY ON     */

            /* LFO */
            public int AMmask;      /* LFO Amplitude Modulation enable mask */
            public byte vib;      /* LFO Phase Modulation enable flag (active high)*/

            /* waveform select */
            public int wavetable;
        }

        public class OPL_CH
        {

            public OPL_SLOT[] SLOT = new OPL_SLOT[] { new OPL_SLOT(), new OPL_SLOT() };
            /* phase generator state */
            public int block_fnum;  /* block+fnum                   */
            public int fc;          /* Freq. Increment base         */
            public int ksl_base;    /* KeyScaleLevel Base step      */
            public byte kcode;        /* key code (for key scaling)   */
            public byte Muted;
        }

        /* OPL state */
        //private FM_OPL fm_opl_f;
        public class FM_OPL
        {
            /* FM channel slots */
            public OPL_CH[] P_CH = new OPL_CH[]{
                new OPL_CH(),new OPL_CH(),new OPL_CH(),new OPL_CH(),new OPL_CH(),
                new OPL_CH(),new OPL_CH(),new OPL_CH(),new OPL_CH()
            };             /* OPL/OPL2 chips have 9 channels*/
            public byte[] MuteSpc = new byte[6];               /* Mute Special: 5 Rhythm + 1 DELTA-T Channel */

            public int eg_cnt;                  /* Global envelope generator counter    */
            public int eg_timer;                /* Global envelope generator counter works at frequency = chipclock/72 */
            public int eg_timer_add;            /* step of eg_timer                     */
            public int eg_timer_overflow;       /* envelope generator timer overlfows every 1 sample (on real chip) */

            public byte rhythm;                   /* Rhythm mode                  */

            public int[] fn_tab = new int[1024];            /* fnumber.increment counter   */

            /* LFO */
            public int LFO_AM;
            public int LFO_PM;

            public byte lfo_am_depth;
            public byte lfo_pm_depth_range;
            public int lfo_am_cnt;
            public int lfo_am_inc;
            public int lfo_pm_cnt;
            public int lfo_pm_inc;

            public int noise_rng;               /* 23 bit noise shift register  */
            public int noise_p;             /* current noise 'phase'        */
            public int noise_f;             /* current noise period         */

            public byte wavesel;              /* waveform select enable flag  */

            public int[] T = new int[2];                    /* timer counters               */
            public byte[] st = new byte[2];                    /* timer enable                 */

            //#if BUILD_Y8950
            //	/* Delta-T ADPCM unit (Y8950) */

            //	YM_DELTAT *deltat;

            //	/* Keyboard and I/O ports interface */
            //	(int)8	portDirection;
            //	(int)8	portLatch;
            //	OPL_PORTHANDLER_R porthandler_r;
            //	OPL_PORTHANDLER_W porthandler_w;
            //	void *	port_param;
            //	OPL_PORTHANDLER_R keyboardhandler_r;
            //	OPL_PORTHANDLER_W keyboardhandler_w;
            //	void *	keyboard_param;
            //#endif

            /* external event callback handlers */
            public OPL_TIMERHANDLER timer_handler; /* TIMER handler                */
            public Object TimerParam;                   /* TIMER parameter              */
            public OPL_IRQHANDLER IRQHandler;  /* IRQ handler                  */
            //public Object IRQParam;                 /* IRQ parameter                */
            public ym3526_state IRQParam;                 /* IRQ parameter                */
            public OPL_UPDATEHANDLER UpdateHandler;/* stream update handler        */
            //public Object UpdateParam;              /* stream update parameter      */
            public ym3526_state UpdateParam;              /* stream update parameter      */

            public byte type;                     /* chip type                    */
            public byte address;                  /* address register             */
            public byte status;                   /* status flag                  */
            public byte statusmask;               /* status mask                  */
            public byte mode;                     /* Reg.08 : CSM,notesel,etc.    */

            public int clock;                   /* master clock  (Hz)           */
            public int rate;                    /* sampling rate (Hz)           */
            public double freqbase;                /* frequency base               */
                                                   //attotime TimerBase;			/* Timer base time (==sampling time)*/

            public int phase_modulation;    /* phase modulation input (SLOT 2) */
            public int[] output = new int[1];
            //#if BUILD_Y8950
            //	int output_deltat[4];		/* for Y8950 DELTA-T, chip is mono, that 4 here is just for safety */
            //#endif
        }



        /* mapping of register number (offset) to slot number used by the emulator */
        private int[] slot_array = new int[]
        {
             0, 2, 4, 1, 3, 5,-1,-1,
             6, 8,10, 7, 9,11,-1,-1,
            12,14,16,13,15,17,-1,-1,
            -1,-1,-1,-1,-1,-1,-1,-1
        };

        /* key scale level */
        /* table is 3dB/octave , DV converts this into 6dB/octave */
        /* 0.1875 is bit 0 weight of the envelope counter (volume) expressed in the 'decibel' scale */
        private final double DV = (0.1875 / 2.0);
        private int[] ksl_tab = new int[]
        {
            /* OCT 0 */
            (int)(0.000/DV),(int)( 0.000/DV),(int)( 0.000/DV),(int)( 0.000/DV),
            (int)( 0.000/DV),(int)( 0.000/DV),(int)( 0.000/DV),(int)( 0.000/DV),
            (int)( 0.000/DV),(int)( 0.000/DV),(int)( 0.000/DV),(int)( 0.000/DV),
            (int)( 0.000/DV),(int)( 0.000/DV),(int)( 0.000/DV),(int)( 0.000/DV),
            /* OCT 1 */         
            (int)( 0.000/DV),(int)( 0.000/DV),(int)( 0.000/DV),(int)( 0.000/DV),
            (int)( 0.000/DV),(int)( 0.000/DV),(int)( 0.000/DV),(int)( 0.000/DV),
            (int)( 0.000/DV),(int)( 0.750/DV),(int)( 1.125/DV),(int)( 1.500/DV),
            (int)( 1.875/DV),(int)( 2.250/DV),(int)( 2.625/DV),(int)( 3.000/DV),
            /* OCT 2 */        
            (int)( 0.000/DV),(int)( 0.000/DV),(int)( 0.000/DV),(int)( 0.000/DV),
            (int)( 0.000/DV),(int)( 1.125/DV),(int)( 1.875/DV),(int)( 2.625/DV),
            (int)( 3.000/DV),(int)( 3.750/DV),(int)( 4.125/DV),(int)( 4.500/DV),
            (int)( 4.875/DV),(int)( 5.250/DV),(int)( 5.625/DV),(int)( 6.000/DV),
            /* OCT 3 */        
            (int)( 0.000/DV),(int)( 0.000/DV),(int)( 0.000/DV),(int)( 1.875/DV),
            (int)( 3.000/DV),(int)( 4.125/DV),(int)( 4.875/DV),(int)( 5.625/DV),
            (int)( 6.000/DV),(int)( 6.750/DV),(int)( 7.125/DV),(int)( 7.500/DV),
            (int)( 7.875/DV),(int)( 8.250/DV),(int)( 8.625/DV),(int)( 9.000/DV),
            /* OCT 4 */        
            (int)( 0.000/DV),(int)( 0.000/DV),(int)( 3.000/DV),(int)( 4.875/DV),
            (int)( 6.000/DV),(int)( 7.125/DV),(int)( 7.875/DV),(int)( 8.625/DV),
            (int)( 9.000/DV),(int)( 9.750/DV),(int)(10.125/DV),(int)(10.500/DV),
            (int)(10.875/DV),(int)(11.250/DV),(int)(11.625/DV),(int)(12.000/DV),
            /* OCT 5 */        
            (int)( 0.000/DV),(int)( 3.000/DV),(int)( 6.000/DV),(int)( 7.875/DV),
            (int)( 9.000/DV),(int)(10.125/DV),(int)(10.875/DV),(int)(11.625/DV),
            (int)(12.000/DV),(int)(12.750/DV),(int)(13.125/DV),(int)(13.500/DV),
            (int)(13.875/DV),(int)(14.250/DV),(int)(14.625/DV),(int)(15.000/DV),
            /* OCT 6 */        
            (int)( 0.000/DV),(int)( 6.000/DV),(int)( 9.000/DV),(int)(10.875/DV),
            (int)(12.000/DV),(int)(13.125/DV),(int)(13.875/DV),(int)(14.625/DV),
            (int)(15.000/DV),(int)(15.750/DV),(int)(16.125/DV),(int)(16.500/DV),
            (int)(16.875/DV),(int)(17.250/DV),(int)(17.625/DV),(int)(18.000/DV),
            /* OCT 7 */        
            (int)( 0.000/DV),(int)( 9.000/DV),(int)(12.000/DV),(int)(13.875/DV),
            (int)(15.000/DV),(int)(16.125/DV),(int)(16.875/DV),(int)(17.625/DV),
            (int)(18.000/DV),(int)(18.750/DV),(int)(19.125/DV),(int)(19.500/DV),
            (int)(19.875/DV),(int)(20.250/DV),(int)(20.625/DV),(int)(21.000/DV)
        };
        //#undef DV

        /* 0 / 3.0 / 1.5 / 6.0 dB/OCT */
        private int[] ksl_shift = new int[] { 31, 1, 2, 0 };


        /* sustain level table (3dB per step) */
        /* 0 - 15: 0, 3, 6, 9,12,15,18,21,24,27,30,33,36,39,42,93 (dB)*/
        private static int SC(double db) { return (int)(db * (2.0 / ENV_STEP)); }
        private int[] sl_tab = new int[]{
            SC( 0),SC( 1),SC( 2),SC(3 ),SC(4 ),SC(5 ),SC(6 ),SC( 7),
            SC( 8),SC( 9),SC(10),SC(11),SC(12),SC(13),SC(14),SC(31)
        };
        //#undef SC


        private final static int RATE_STEPS = 8;
        private byte[] eg_inc = new byte[]{
            /*cycle:0 1  2 3  4 5  6 7*/
            
            /* 0 */ 0,1, 0,1, 0,1, 0,1, /* rates 00..12 0 (increment by 0 or 1) */
            /* 1 */ 0,1, 0,1, 1,1, 0,1, /* rates 00..12 1 */
            /* 2 */ 0,1, 1,1, 0,1, 1,1, /* rates 00..12 2 */
            /* 3 */ 0,1, 1,1, 1,1, 1,1, /* rates 00..12 3 */
            
            /* 4 */ 1,1, 1,1, 1,1, 1,1, /* rate 13 0 (increment by 1) */
            /* 5 */ 1,1, 1,2, 1,1, 1,2, /* rate 13 1 */
            /* 6 */ 1,2, 1,2, 1,2, 1,2, /* rate 13 2 */
            /* 7 */ 1,2, 2,2, 1,2, 2,2, /* rate 13 3 */
            
            /* 8 */ 2,2, 2,2, 2,2, 2,2, /* rate 14 0 (increment by 2) */
            /* 9 */ 2,2, 2,4, 2,2, 2,4, /* rate 14 1 */
            /*10 */ 2,4, 2,4, 2,4, 2,4, /* rate 14 2 */
            /*11 */ 2,4, 4,4, 2,4, 4,4, /* rate 14 3 */
            
            /*12 */ 4,4, 4,4, 4,4, 4,4, /* rates 15 0, 15 1, 15 2, 15 3 (increment by 4) */
            /*13 */ 8,8, 8,8, 8,8, 8,8, /* rates 15 2, 15 3 for attack */
            /*14 */ 0,0, 0,0, 0,0, 0,0, /* infinity rates for attack and decay(s) */
        };


        private static byte O(int a) { return (byte)(a * RATE_STEPS); }

        /*note that there is no O(13) in this table - it's directly in the code */
        private byte[] eg_rate_select = new byte[] {	/* Envelope Generator rates (16 + 64 rates + 16 RKS) */
            /* 16 infinite time rates */
            O(14),O(14),O(14),O(14),O(14),O(14),O(14),O(14),
            O(14),O(14),O(14),O(14),O(14),O(14),O(14),O(14),
            
            /* rates 00-12 */
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
            
            /* rate 13 */
            O( 4),O( 5),O( 6),O( 7),
            
            /* rate 14 */
            O( 8),O( 9),O(10),O(11),
            
            /* rate 15 */
            O(12),O(12),O(12),O(12),
            
            /* 16 dummy rates (same as 15 3) */
            O(12),O(12),O(12),O(12),O(12),O(12),O(12),O(12),
            O(12),O(12),O(12),O(12),O(12),O(12),O(12),O(12),

        };
        //#undef O

        /*rate  0,    1,    2,    3,   4,   5,   6,  7,  8,  9,  10, 11, 12, 13, 14, 15 */
        /*shift 12,   11,   10,   9,   8,   7,   6,  5,  4,  3,  2,  1,  0,  0,  0,  0  */
        /*mask  4095, 2047, 1023, 511, 255, 127, 63, 31, 15, 7,  3,  1,  0,  0,  0,  0  */

        private static byte O2(int a) { return (byte)(a * 1); }
        private byte[] eg_rate_shift = new byte[]{	/* Envelope Generator counter shifts (16 + 64 rates + 16 RKS) */
            /* 16 infinite time rates */
            O2(0),O2(0),O2(0),O2(0),O2(0),O2(0),O2(0),O2(0),
            O2(0),O2(0),O2(0),O2(0),O2(0),O2(0),O2(0),O2(0),
            
            /* rates 00-12 */
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
            
            /* rate 13 */
            O2( 0),O2( 0),O2( 0),O2( 0),
            
            /* rate 14 */
            O2( 0),O2( 0),O2( 0),O2( 0),
            
            /* rate 15 */
            O2( 0),O2( 0),O2( 0),O2( 0),
            
            /* 16 dummy rates (same as 15 3) */
            O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),
            O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),

        };
        //#undef O


        /* multiple table */
        private static byte ML = 2;
        private byte[] mul_tab = new byte[] {
            /* 1/2, 1, 2, 3, 4, 5, 6, 7, 8, 9,10,10,12,12,15,15 */
            (byte)(0.50*ML),(byte)( 1.00*ML),(byte)( 2.00*ML),(byte)( 3.00*ML),(byte)( 4.00*ML),(byte)( 5.00*ML),(byte)( 6.00*ML),(byte)( 7.00*ML),
            (byte)(8.00*ML),(byte)( 9.00*ML),(byte)(10.00*ML),(byte)(10.00*ML),(byte)(12.00*ML),(byte)(12.00*ML),(byte)(15.00*ML),(byte)(15.00*ML)
        };
        //#undef ML

        /*  TL_TAB_LEN is calculated as:
        *   12 - sinus amplitude bits     (Y axis)
        *   2  - sinus sign bit           (Y axis)
        *   TL_RES_LEN - sinus resolution (X axis)
        */
        private static int TL_TAB_LEN = (12 * 2 * TL_RES_LEN);
        private int[] tl_tab = new int[TL_TAB_LEN];

        private static int ENV_QUIET = (TL_TAB_LEN >> 4);

        /* sin waveform table in 'decibel' scale */
        /* four waveforms on OPL2 type chips */
        private int[] sin_tab = new int[SIN_LEN * 4];


        /* LFO Amplitude Modulation table (verified on real YM3812)
           27 output levels (triangle waveform); 1 level takes one of: 192, 256 or 448 samples

           Length: 210 elements.

            Each of the elements has to be repeated
            exactly 64 times (on 64 consecutive samples).
            The whole table takes: 64 * 210 = 13440 samples.

            When AM = 1 data is used directly
            When AM = 0 data is divided by 4 before being used (losing precision is important)
        */

        private final int LFO_AM_TAB_ELEMENTS = 210;
        private byte[] lfo_am_table = new byte[] {
            0,0,0,0,0,0,0,
            1,1,1,1,
            2,2,2,2,
            3,3,3,3,
            4,4,4,4,
            5,5,5,5,
            6,6,6,6,
            7,7,7,7,
            8,8,8,8,
            9,9,9,9,
            10,10,10,10,
            11,11,11,11,
            12,12,12,12,
            13,13,13,13,
            14,14,14,14,
            15,15,15,15,
            16,16,16,16,
            17,17,17,17,
            18,18,18,18,
            19,19,19,19,
            20,20,20,20,
            21,21,21,21,
            22,22,22,22,
            23,23,23,23,
            24,24,24,24,
            25,25,25,25,
            26,26,26,
            25,25,25,25,
            24,24,24,24,
            23,23,23,23,
            22,22,22,22,
            21,21,21,21,
            20,20,20,20,
            19,19,19,19,
            18,18,18,18,
            17,17,17,17,
            16,16,16,16,
            15,15,15,15,
            14,14,14,14,
            13,13,13,13,
            12,12,12,12,
            11,11,11,11,
            10,10,10,10,
            9,9,9,9,
            8,8,8,8,
            7,7,7,7,
            6,6,6,6,
            5,5,5,5,
            4,4,4,4,
            3,3,3,3,
            2,2,2,2,
            1,1,1,1
        };

        /* LFO Phase Modulation table (verified on real YM3812) */
        private byte[] lfo_pm_table = new byte[]{
            /* FNUM2/FNUM = 00 0xxxxxxx (0x0000) */
            0, 0, 0, 0, 0, 0, 0, 0,	/*LFO PM depth = 0*/
            0, 0, 0, 0, 0, 0, 0, 0,	/*LFO PM depth = 1*/
            
            /* FNUM2/FNUM = 00 1xxxxxxx (0x0080) */
            0, 0, 0, 0, 0, 0, 0, 0,	/*LFO PM depth = 0*/
            1, 0, 0, 0,-1, 0, 0, 0,	/*LFO PM depth = 1*/
            
            /* FNUM2/FNUM = 01 0xxxxxxx (0x0100) */
            1, 0, 0, 0,-1, 0, 0, 0,	/*LFO PM depth = 0*/
            2, 1, 0,-1,-2,-1, 0, 1,	/*LFO PM depth = 1*/
            
            /* FNUM2/FNUM = 01 1xxxxxxx (0x0180) */
            1, 0, 0, 0,-1, 0, 0, 0,	/*LFO PM depth = 0*/
            3, 1, 0,-1,-3,-1, 0, 1,	/*LFO PM depth = 1*/
            
            /* FNUM2/FNUM = 10 0xxxxxxx (0x0200) */
            2, 1, 0,-1,-2,-1, 0, 1,	/*LFO PM depth = 0*/
            4, 2, 0,-2,-4,-2, 0, 2,	/*LFO PM depth = 1*/
            
            /* FNUM2/FNUM = 10 1xxxxxxx (0x0280) */
            2, 1, 0,-1,-2,-1, 0, 1,	/*LFO PM depth = 0*/
            5, 2, 0,-2,-5,-2, 0, 2,	/*LFO PM depth = 1*/
            
            /* FNUM2/FNUM = 11 0xxxxxxx (0x0300) */
            3, 1, 0,-1,-3,-1, 0, 1,	/*LFO PM depth = 0*/
            6, 3, 0,-3,-6,-3, 0, 3,	/*LFO PM depth = 1*/
            
            /* FNUM2/FNUM = 11 1xxxxxxx (0x0380) */
            3, 1, 0,-1,-3,-1, 0, 1,	/*LFO PM depth = 0*/
            7, 3, 0,-3,-7,-3, 0, 3	/*LFO PM depth = 1*/
        };


        /* synchronized level of common table */
        private int num_lock = 0;

        private OPL_SLOT SLOT7_1(FM_OPL OPL) { return (OPL.P_CH[7].SLOT[SLOT1]); }
        private OPL_SLOT SLOT7_2(FM_OPL OPL) { return (OPL.P_CH[7].SLOT[SLOT2]); }
        private OPL_SLOT SLOT8_1(FM_OPL OPL) { return (OPL.P_CH[8].SLOT[SLOT1]); }
        private OPL_SLOT SLOT8_2(FM_OPL OPL) { return (OPL.P_CH[8].SLOT[SLOT2]); }




        /*INLINE int limit( int val, int max, int min ) {
            if ( val > max )
                val = max;
            else if ( val < min )
                val = min;

            return val;
        }*/


        /* status set and IRQ handling */
        private void OPL_STATUS_SET(FM_OPL OPL, int flag)
        {
            /* set status flag */
            OPL.status |= (byte)flag;
            if ((OPL.status & 0x80) == 0)
            {
                if ((OPL.status & OPL.statusmask) != 0)
                {   /* IRQ on */
                    OPL.status |= 0x80;
                    /* callback user interrupt handler (IRQ is OFF to ON) */
                    if (OPL.IRQHandler != null) OPL.IRQHandler.accept(OPL.IRQParam, 1);
                }
            }
        }

        /* status reset and IRQ handling */
        private void OPL_STATUS_RESET(FM_OPL OPL, int flag)
        {
            /* reset status flag */
            OPL.status &= (byte)~flag;
            if ((OPL.status & 0x80) != 0)
            {
                if ((OPL.status & OPL.statusmask) == 0)
                {
                    OPL.status &= 0x7f;
                    /* callback user interrupt handler (IRQ is ON to OFF) */
                    if (OPL.IRQHandler != null) OPL.IRQHandler.accept(OPL.IRQParam, 0);
                }
            }
        }

        /* IRQ mask set */
        private void OPL_STATUSMASK_SET(FM_OPL OPL, int flag)
        {
            OPL.statusmask = (byte)flag;
            /* IRQ handling check */
            OPL_STATUS_SET(OPL, 0);
            OPL_STATUS_RESET(OPL, 0);
        }


        /* advance LFO to next sample */
        private void advance_lfo(FM_OPL OPL)
        {
            byte tmp;

            /* LFO */
            OPL.lfo_am_cnt += OPL.lfo_am_inc;

            if (OPL.lfo_am_cnt >= ((int)LFO_AM_TAB_ELEMENTS << LFO_SH)) /* lfo_am_table is 210 elements long */
                OPL.lfo_am_cnt -= ((int)LFO_AM_TAB_ELEMENTS << LFO_SH);

            tmp = lfo_am_table[OPL.lfo_am_cnt >> LFO_SH];
            //System.err.printf("tmp {0}\n", tmp);

            if (OPL.lfo_am_depth != 0)
                OPL.LFO_AM = tmp;
            else
                OPL.LFO_AM = (int)(tmp >> 2);

            OPL.lfo_pm_cnt += OPL.lfo_pm_inc;
            OPL.LFO_PM = (int)(((OPL.lfo_pm_cnt >> LFO_SH) & 7) | OPL.lfo_pm_depth_range);
        }


        private void refresh_eg(FM_OPL OPL)
        {
            OPL_CH CH;
            OPL_SLOT op;
            int i;
            int new_vol;

            for (i = 0; i < 9 * 2; i++)
            {
                CH = OPL.P_CH[i / 2];
                op = CH.SLOT[i & 1];

                // Envelope Generator
                switch (op.state)
                {
                    case EG_ATT:        // attack phase
                        if ((OPL.eg_cnt & ((1 << op.eg_sh_ar) - 1)) == 0)
                        {
                            new_vol = op.volume + ((~op.volume *
                                           (eg_inc[op.eg_sel_ar + ((OPL.eg_cnt >> op.eg_sh_ar) & 7)])
                                          ) >> 3);
                            if (new_vol <= MIN_ATT_INDEX)
                            {
                                op.volume = MIN_ATT_INDEX;
                                op.state = EG_DEC;
                            }
                        }
                        break;
                        /*case EG_DEC:	// decay phase
                            if ( !(OPL.eg_cnt & ((1<<op.eg_sh_dr)-1) ) )
                            {
                                new_vol = op.volume + eg_inc[op.eg_sel_dr + ((OPL.eg_cnt>>op.eg_sh_dr)&7)];

                                if ( new_vol >= op.sl )
                                    op.state = EG_SUS;
                            }
                            break;
                        case EG_SUS:	// sustain phase
                            if ( !op.eg_type)	percussive mode
                            {
                                new_vol = op.volume + eg_inc[op.eg_sel_rr + ((OPL.eg_cnt>>op.eg_sh_rr)&7)];

                                if ( !(OPL.eg_cnt & ((1<<op.eg_sh_rr)-1) ) )
                                {
                                    if ( new_vol >= MAX_ATT_INDEX )
                                        op.volume = MAX_ATT_INDEX;
                                }
                            }
                            break;
                        case EG_REL:	// release phase
                            if ( !(OPL.eg_cnt & ((1<<op.eg_sh_rr)-1) ) )
                            {
                                new_vol = op.volume + eg_inc[op.eg_sel_rr + ((OPL.eg_cnt>>op.eg_sh_rr)&7)];
                                if ( new_vol >= MAX_ATT_INDEX )
                                {
                                    op.volume = MAX_ATT_INDEX;
                                    op.state = EG_OFF;
                                }

                            }
                            break;
                        default:
                            break;*/
                }
            }

            return;
        }

        /* advance to next sample */
        private void advance(FM_OPL OPL)
        {
            OPL_CH CH;
            OPL_SLOT op;
            int i;

            OPL.eg_timer += OPL.eg_timer_add;

            while (OPL.eg_timer >= OPL.eg_timer_overflow)
            {
                OPL.eg_timer -= OPL.eg_timer_overflow;

                OPL.eg_cnt++;

                for (i = 0; i < 9 * 2; i++)
                {
                    CH = OPL.P_CH[i / 2];
                    op = CH.SLOT[i & 1];

                    /* Envelope Generator */
                    switch (op.state)
                    {
                        case EG_ATT:        /* attack phase */
                            if ((OPL.eg_cnt & ((1 << op.eg_sh_ar) - 1)) == 0)
                            {
                                //System.err.printf("eg_inc:{0} op.eg_sel_ar:{1} OPL.eg_cnt:{2} op.eg_sh_ar:{3}\n"
                                //    , eg_inc[op.eg_sel_ar + ((OPL.eg_cnt >> op.eg_sh_ar) & 7)]
                                //    , op.eg_sel_ar
                                //    , OPL.eg_cnt
                                //    , op.eg_sh_ar);
                                op.volume += ((~op.volume) *
                                           (eg_inc[op.eg_sel_ar + ((OPL.eg_cnt >> op.eg_sh_ar) & 7)])
                                          ) >> 3;


                                if (op.volume <= MIN_ATT_INDEX)
                                {
                                    op.volume = MIN_ATT_INDEX;
                                    op.state = EG_DEC;
                                }

                            }
                            break;

                        case EG_DEC:    /* decay phase */
                            if ((OPL.eg_cnt & ((1 << op.eg_sh_dr) - 1)) == 0)
                            {
                                op.volume += eg_inc[op.eg_sel_dr + ((OPL.eg_cnt >> op.eg_sh_dr) & 7)];

                                if (op.volume >= op.sl)
                                    op.state = EG_SUS;

                            }
                            break;

                        case EG_SUS:    /* sustain phase */

                            /* this is important behaviour:
                            one can change percusive/non-percussive modes on the fly and
                            the chip will remain in sustain phase - verified on real YM3812 */

                            if (op.eg_type != 0)        /* non-percussive mode */
                            {
                                /* do nothing */
                            }
                            else                /* percussive mode */
                            {
                                /* during sustain phase chip adds Release Rate (in percussive mode) */
                                if ((OPL.eg_cnt & ((1 << op.eg_sh_rr) - 1)) == 0)
                                {
                                    op.volume += eg_inc[op.eg_sel_rr + ((OPL.eg_cnt >> op.eg_sh_rr) & 7)];

                                    if (op.volume >= MAX_ATT_INDEX)
                                        op.volume = MAX_ATT_INDEX;
                                }
                                /* else do nothing in sustain phase */
                            }
                            break;

                        case EG_REL:    /* release phase */
                            if ((OPL.eg_cnt & ((1 << op.eg_sh_rr) - 1)) == 0)
                            {
                                op.volume += eg_inc[op.eg_sel_rr + ((OPL.eg_cnt >> op.eg_sh_rr) & 7)];

                                if (op.volume >= MAX_ATT_INDEX)
                                {
                                    op.volume = MAX_ATT_INDEX;
                                    op.state = EG_OFF;
                                }

                            }
                            break;

                        default:
                            break;
                    }
                }
            }

            for (i = 0; i < 9 * 2; i++)
            {
                CH = OPL.P_CH[i / 2];
                op = CH.SLOT[i & 1];

                /* Phase Generator */
                if (op.vib != 0)
                {
                    byte block;
                    int block_fnum = CH.block_fnum;

                    int fnum_lfo = (block_fnum & 0x0380) >> 7;

                    int lfo_fn_table_index_offset = lfo_pm_table[OPL.LFO_PM + 16 * fnum_lfo];

                    if (lfo_fn_table_index_offset != 0)  /* LFO phase modulation active */
                    {
                        block_fnum += (int)lfo_fn_table_index_offset;
                        block = (byte)((block_fnum & 0x1c00) >> 10);
                        op.Cnt += (OPL.fn_tab[block_fnum & 0x03ff] >> (7 - block)) * op.mul;
                    }
                    else    /* LFO phase modulation  = zero */
                    {
                        op.Cnt += op.Incr;
                    }
                }
                else    /* LFO phase modulation disabled for this operator */
                {
                    op.Cnt += op.Incr;
                }
            }

            /*  The Noise Generator of the YM3812 is 23-bit shift register.
            *   Period is equal to 2^23-2 samples.
            *   Register works at sampling frequency of the chip, so output
            *   can change on every sample.
            *
            *   Output of the register and input to the bit 22 is:
            *   bit0 XOR bit14 XOR bit15 XOR bit22
            *
            *   Simply use bit 22 as the noise output.
            */

            OPL.noise_p += OPL.noise_f;
            i = (int)(OPL.noise_p >> FREQ_SH);        /* number of events (shifts of the shift register) */
            OPL.noise_p &= FREQ_MASK;
            while (i != 0)
            {
                /*
                (int)32 j;
                j = ( (OPL.noise_rng) ^ (OPL.noise_rng>>14) ^ (OPL.noise_rng>>15) ^ (OPL.noise_rng>>22) ) & 1;
                OPL.noise_rng = (j<<22) | (OPL.noise_rng>>1);
                */

                /*
                    Instead of doing all the logic operations above, we
                    use a trick here (and use bit 0 as the noise output).
                    The difference is only that the noise bit changes one
                    step ahead. This doesn't matter since we don't know
                    what is real state of the noise_rng after the reset.
                */

                if ((OPL.noise_rng & 1) != 0) OPL.noise_rng ^= 0x800302;
                OPL.noise_rng >>= 1;

                i--;
            }
        }


        private int op_calc(int phase, int env, int pm, int wave_tab)
        {
            int p;

            p = (env << 4) + sin_tab[wave_tab + ((((int)((phase & ~FREQ_MASK) + (pm << 16))) >> FREQ_SH) & SIN_MASK)];
            //System.err.printf("op_calc:{0}",p);
            if (p >= TL_TAB_LEN)
                return 0;
            return tl_tab[p];
        }

        private int op_calc1(int phase, int env, int pm, int wave_tab)
        {
            int p;

            p = (env << 4) + sin_tab[wave_tab + ((((int)((phase & ~FREQ_MASK) + pm)) >> FREQ_SH) & SIN_MASK)];
            //System.err.printf("op_calc1:{0}", p);

            if (p >= TL_TAB_LEN)
                return 0;
            return tl_tab[p];
        }


        private int volume_calc(OPL_SLOT OP, FM_OPL OPL)
        {
            return (int)(OP.TLL + ((int)OP.volume) + (OPL.LFO_AM & OP.AMmask));
        }

        /* calculate output */
        private void OPL_CALC_CH(FM_OPL OPL, OPL_CH CH)
        {
            OPL_SLOT SLOT;
            int env;
            int _out;

            if (CH.Muted != 0)
                return;

            OPL.phase_modulation = 0;

            /* SLOT 1 */
            SLOT = CH.SLOT[SLOT1];
            env = volume_calc(SLOT, OPL);
            //System.err.printf("env1 {0} {1} {2} {3} {4}\n", env, SLOT.TLL, SLOT.volume, OPL.LFO_AM, SLOT.AMmask);
            _out = SLOT.op1_out[0] + SLOT.op1_out[1];
            SLOT.op1_out[0] = SLOT.op1_out[1];
            //SLOT.connect1 += SLOT.op1_out[0];
            if (SLOT.ptrConnect1 == 0) OPL.output[0] += SLOT.op1_out[0];
            else OPL.phase_modulation += SLOT.op1_out[0];
            SLOT.op1_out[1] = 0;
            if (env < ENV_QUIET)
            {
                if (SLOT.FB == 0)
                    _out = 0;
                SLOT.op1_out[1] = op_calc1(SLOT.Cnt, env, (_out << SLOT.FB), SLOT.wavetable);
            }

            /* SLOT 2 */
            //SLOT++;
            SLOT = CH.SLOT[SLOT2];
            env = volume_calc(SLOT, OPL);
            //System.err.printf("env2 {0}\n", env);
            if (env < ENV_QUIET)
                OPL.output[0] += op_calc(SLOT.Cnt, env, OPL.phase_modulation, SLOT.wavetable);

        }

        /*
            operators used in the rhythm sounds generation process:

            Envelope Generator:

        channel  operator  register number   Bass  High  Snare Tom  Top
        / slot   number    TL ARDR SLRR Wave Drum  Hat   Drum  Tom  Cymbal
         6 / 0   12        50  70   90   f0  +
         6 / 1   15        53  73   93   f3  +
         7 / 0   13        51  71   91   f1        +
         7 / 1   16        54  74   94   f4              +
         8 / 0   14        52  72   92   f2                    +
         8 / 1   17        55  75   95   f5                          +

            Phase Generator:

        channel  operator  register number   Bass  High  Snare Tom  Top
        / slot   number    MULTIPLE          Drum  Hat   Drum  Tom  Cymbal
         6 / 0   12        30                +
         6 / 1   15        33                +
         7 / 0   13        31                      +     +           +
         7 / 1   16        34                -----  n o t  u s e d -----
         8 / 0   14        32                                  +
         8 / 1   17        35                      +                 +

        channel  operator  register number   Bass  High  Snare Tom  Top
        number   number    BLK/FNUM2 FNUM    Drum  Hat   Drum  Tom  Cymbal
           6     12,15     B6        A6      +

           7     13,16     B7        A7            +     +           +

           8     14,17     B8        A8            +           +     +

        */

        /* calculate rhythm */

        private void OPL_CALC_RH(FM_OPL OPL, OPL_CH[] CH, int noise)
        {
            OPL_SLOT SLOT;
            int _out;
            int env;


            /* Bass Drum (verified on real YM3812):
              - depends on the channel 6 'connect' register:
                  when connect = 0 it works the same as in normal (non-rhythm) mode (op1.op2.out)
                  when connect = 1 _only_ operator 2 is present on output (op2.out), operator 1 is ignored
              - output sample always is multiplied by 2
            */

            OPL.phase_modulation = 0;
            /* SLOT 1 */
            SLOT = CH[6].SLOT[SLOT1];
            env = volume_calc(SLOT, OPL);

            _out = SLOT.op1_out[0] + SLOT.op1_out[1];
            SLOT.op1_out[0] = SLOT.op1_out[1];

            if (SLOT.CON == 0)
                OPL.phase_modulation = SLOT.op1_out[0];
            /* else ignore output of operator 1 */

            SLOT.op1_out[1] = 0;
            if (env < ENV_QUIET)
            {
                if (SLOT.FB == 0)
                    _out = 0;
                SLOT.op1_out[1] = op_calc1(SLOT.Cnt, env, (_out << SLOT.FB), SLOT.wavetable);
            }

            /* SLOT 2 */
            //SLOT++;
            SLOT = CH[6].SLOT[SLOT2];
            env = volume_calc(SLOT, OPL);
            if (env < ENV_QUIET && OPL.MuteSpc[0] == 0)
                OPL.output[0] += op_calc(SLOT.Cnt, env, OPL.phase_modulation, SLOT.wavetable) * 2;


            /* Phase generation is based on: */
            /* HH  (13) channel 7.slot 1 combined with channel 8.slot 2 (same combination as TOP CYMBAL but different output phases) */
            /* SD  (16) channel 7.slot 1 */
            /* TOM (14) channel 8.slot 1 */
            /* TOP (17) channel 7.slot 1 combined with channel 8.slot 2 (same combination as HIGH HAT but different output phases) */

            /* Envelope generation based on: */
            /* HH  channel 7.slot1 */
            /* SD  channel 7.slot2 */
            /* TOM channel 8.slot1 */
            /* TOP channel 8.slot2 */


            /* The following formulas can be well optimized.
               I leave them in direct form for now (in case I've missed something).
            */

            /* High Hat (verified on real YM3812) */
            env = volume_calc(SLOT7_1(OPL), OPL);
            if (env < ENV_QUIET && OPL.MuteSpc[4] == 0)
            {

                /* high hat phase generation:
                    phase = d0 or 234 (based on frequency only)
                    phase = 34 or 2d0 (based on noise)
                */

                /* base frequency derived from operator 1 in channel 7 */
                byte bit7 = (byte)(((SLOT7_1(OPL).Cnt >> FREQ_SH) >> 7) & 1);
                byte bit3 = (byte)(((SLOT7_1(OPL).Cnt >> FREQ_SH) >> 3) & 1);
                byte bit2 = (byte)(((SLOT7_1(OPL).Cnt >> FREQ_SH) >> 2) & 1);

                byte res1 = (byte)((bit2 ^ bit7) | bit3);

                /* when res1 = 0 phase = 0x000 | 0xd0; */
                /* when res1 = 1 phase = 0x200 | (0xd0>>2); */
                int phase = (int)(res1 != 0 ? (0x200 | (0xd0 >> 2)) : 0xd0);

                /* enable gate based on frequency of operator 2 in channel 8 */
                byte bit5e = (byte)(((SLOT8_2(OPL).Cnt >> FREQ_SH) >> 5) & 1);
                byte bit3e = (byte)(((SLOT8_2(OPL).Cnt >> FREQ_SH) >> 3) & 1);

                byte res2 = (byte)(bit3e ^ bit5e);

                /* when res2 = 0 pass the phase from calculation above (res1); */
                /* when res2 = 1 phase = 0x200 | (0xd0>>2); */
                if (res2 != 0)
                    phase = (0x200 | (0xd0 >> 2));


                /* when phase & 0x200 is set and noise=1 then phase = 0x200|0xd0 */
                /* when phase & 0x200 is set and noise=0 then phase = 0x200|(0xd0>>2), ie no change */
                if ((phase & 0x200) != 0)
                {
                    if (noise != 0)
                        phase = 0x200 | 0xd0;
                }
                else
                /* when phase & 0x200 is clear and noise=1 then phase = 0xd0>>2 */
                /* when phase & 0x200 is clear and noise=0 then phase = 0xd0, ie no change */
                {
                    if (noise != 0)
                        phase = 0xd0 >> 2;
                }

                OPL.output[0] += op_calc(phase << FREQ_SH, env, 0, SLOT7_1(OPL).wavetable) * 2;
            }

            /* Snare Drum (verified on real YM3812) */
            env = volume_calc(SLOT7_2(OPL), OPL);
            if (env < ENV_QUIET && OPL.MuteSpc[1] == 0)
            {
                /* base frequency derived from operator 1 in channel 7 */
                byte bit8 = (byte)(((SLOT7_1(OPL).Cnt >> FREQ_SH) >> 8) & 1);

                /* when bit8 = 0 phase = 0x100; */
                /* when bit8 = 1 phase = 0x200; */
                int phase = (int)(bit8 != 0 ? 0x200 : 0x100);

                /* Noise bit XOR'es phase by 0x100 */
                /* when noisebit = 0 pass the phase from calculation above */
                /* when noisebit = 1 phase ^= 0x100; */
                /* in other words: phase ^= (noisebit<<8); */
                if (noise != 0)
                    phase ^= 0x100;

                OPL.output[0] += op_calc(phase << FREQ_SH, env, 0, SLOT7_2(OPL).wavetable) * 2;
            }

            /* Tom Tom (verified on real YM3812) */
            env = volume_calc(SLOT8_1(OPL), OPL);
            if (env < ENV_QUIET && OPL.MuteSpc[2] == 0)
                OPL.output[0] += op_calc(SLOT8_1(OPL).Cnt, env, 0, SLOT8_1(OPL).wavetable) * 2;

            /* Top Cymbal (verified on real YM3812) */
            env = volume_calc(SLOT8_2(OPL), OPL);
            if (env < ENV_QUIET && OPL.MuteSpc[3] == 0)
            {
                /* base frequency derived from operator 1 in channel 7 */
                byte bit7 = (byte)(((SLOT7_1(OPL).Cnt >> FREQ_SH) >> 7) & 1);
                byte bit3 = (byte)(((SLOT7_1(OPL).Cnt >> FREQ_SH) >> 3) & 1);
                byte bit2 = (byte)(((SLOT7_1(OPL).Cnt >> FREQ_SH) >> 2) & 1);

                byte res1 = (byte)((bit2 ^ bit7) | bit3);

                /* when res1 = 0 phase = 0x000 | 0x100; */
                /* when res1 = 1 phase = 0x200 | 0x100; */
                int phase = (int)(res1 != 0 ? 0x300 : 0x100);

                /* enable gate based on frequency of operator 2 in channel 8 */
                byte bit5e = (byte)(((SLOT8_2(OPL).Cnt >> FREQ_SH) >> 5) & 1);
                byte bit3e = (byte)(((SLOT8_2(OPL).Cnt >> FREQ_SH) >> 3) & 1);

                byte res2 = (byte)(bit3e ^ bit5e);
                /* when res2 = 0 pass the phase from calculation above (res1); */
                /* when res2 = 1 phase = 0x200 | 0x100; */
                if (res2 != 0)
                    phase = 0x300;

                OPL.output[0] += op_calc(phase << FREQ_SH, env, 0, SLOT8_2(OPL).wavetable) * 2;
            }
        }


        /* generic table initialize */
        private int init_tables()
        {
            int i, x;
            int n;
            double o, m;


            for (x = 0; x < TL_RES_LEN; x++)
            {
                m = (1 << 16) / Math.pow(2, (x + 1) * (ENV_STEP / 4.0) / 8.0);
                m = Math.floor(m);

                /* we never reach (1<<16) here due to the (x+1) */
                /* result fits within 16 bits at maximum */

                n = (int)m;     /* 16 bits here */
                n >>= 4;        /* 12 bits here */
                if ((n & 1) != 0)      /* round to nearest */
                    n = (n >> 1) + 1;
                else
                    n = n >> 1;
                /* 11 bits here (rounded) */
                n <<= 1;        /* 12 bits here (as in real chip) */
                tl_tab[x * 2 + 0] = n;
                tl_tab[x * 2 + 1] = -tl_tab[x * 2 + 0];

                for (i = 1; i < 12; i++)
                {
                    tl_tab[x * 2 + 0 + i * 2 * TL_RES_LEN] = tl_tab[x * 2 + 0] >> i;
                    tl_tab[x * 2 + 1 + i * 2 * TL_RES_LEN] = -tl_tab[x * 2 + 0 + i * 2 * TL_RES_LEN];
                }
                //#if 0
                //			logerror("tl %04i", x*2);
                //			for (i=0; i<12; i++)
                //				logerror(", [%02i] %5i", i*2, tl_tab[ x*2 /*+1*/ + i*2*TL_RES_LEN ] );
                //			logerror("\n");
                //#endif
            }
            /*logerror("FMOPL.C: TL_TAB_LEN = %i elements (%i bytes)\n",TL_TAB_LEN, (int)sizeof(tl_tab));*/


            for (i = 0; i < SIN_LEN; i++)
            {
                /* non-standard sinus */
                m = Math.sin(((i * 2) + 1) * Math.PI / SIN_LEN); /* checked against the real chip */

                /* we never reach zero here due to ((i*2)+1) */

                if (m > 0.0)
                    o = 8 * Math.log(1.0 / m) / Math.log(2.0);    /* convert to 'decibels' */
                else
                    o = 8 * Math.log(-1.0 / m) / Math.log(2.0);   /* convert to 'decibels' */

                o = o / (ENV_STEP / 4);

                n = (int)(2.0 * o);
                if ((n & 1) != 0)                      /* round to nearest */
                    n = (n >> 1) + 1;
                else
                    n = n >> 1;

                sin_tab[i] = (int)(n * 2 + (m >= 0.0 ? 0 : 1));

                /*logerror("FMOPL.C: sin [%4i (hex=%03x)]= %4i (tl_tab value=%5i)\n", i, i, sin_tab[i], tl_tab[sin_tab[i]] );*/
            }

            for (i = 0; i < SIN_LEN; i++)
            {
                /* waveform 1:  __      __     */
                /*             /  \____/  \____*/
                /* output only first half of the sinus waveform (positive one) */

                if ((i & (1 << (SIN_BITS - 1))) != 0)
                    sin_tab[1 * SIN_LEN + i] = (int)TL_TAB_LEN;
                else
                    sin_tab[1 * SIN_LEN + i] = sin_tab[i];

                /* waveform 2:  __  __  __  __ */
                /*             /  \/  \/  \/  \*/
                /* abs(sin) */

                sin_tab[2 * SIN_LEN + i] = sin_tab[i & (SIN_MASK >> 1)];

                /* waveform 3:  _   _   _   _  */
                /*             / |_/ |_/ |_/ |_*/
                /* abs(output only first quarter of the sinus waveform) */

                if ((i & (1 << (SIN_BITS - 2))) != 0)
                    sin_tab[3 * SIN_LEN + i] = (int)TL_TAB_LEN;
                else
                    sin_tab[3 * SIN_LEN + i] = sin_tab[i & (SIN_MASK >> 2)];

                /*logerror("FMOPL.C: sin1[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[1*SIN_LEN+i], tl_tab[sin_tab[1*SIN_LEN+i]] );
                logerror("FMOPL.C: sin2[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[2*SIN_LEN+i], tl_tab[sin_tab[2*SIN_LEN+i]] );
                logerror("FMOPL.C: sin3[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[3*SIN_LEN+i], tl_tab[sin_tab[3*SIN_LEN+i]] );*/
            }
            /*logerror("FMOPL.C: ENV_QUIET= %08x (dec*8=%i)\n", ENV_QUIET, ENV_QUIET*8 );*/


            //# ifdef SAVE_SAMPLE
            //    sample[0] = fopen("sampsum.pcm", "wb");
            //#endif

            return 1;
        }

        private void OPLCloseTable()
        {
            //# ifdef SAVE_SAMPLE
            //    fclose(sample[0]);
            //#endif
        }



        private void OPL_initalize(FM_OPL OPL)
        {
            int i;

            /* frequency base */
            OPL.freqbase = (OPL.rate != 0) ? ((double)OPL.clock / 72.0) / OPL.rate : 0;
            //#if 0
            //	OPL.rate = (double)OPL.clock / 72.0;
            //	OPL.freqbase  = 1.0;
            //#endif

            /*logerror("freqbase=%f\n", OPL.freqbase);*/

            /* Timer base time */
            //OPL.TimerBase = attotime_mul(ATTOTIME_IN_HZ(OPL.clock), 72);

            /* make fnumber . increment counter table */
            for (i = 0; i < 1024; i++)
            {
                /* opn phase increment counter = 20bit */
                OPL.fn_tab[i] = (int)((double)i * 64 * OPL.freqbase * (1 << (FREQ_SH - 10))); /* -10 because chip works with 10.10 fixed point, while we use 16.16 */

                //System.err.printf("FMOPL.C: fn_tab[{0:d}] = {1:x} (dec={2})\n",
                //i, OPL.fn_tab[i] >> 6, OPL.fn_tab[i] >> 6);
            }

            //#if 0
            for (i = 0; i < 16; i++)
            {
                //System.err.printf("FMOPL.C: sl_tab[{0}] = {1:x}\n",
                    //i, sl_tab[i]);
            }
            for (i = 0; i < 8; i++)
            {
                int j;
                //System.err.printf("FMOPL.C: ksl_tab[oct={0}] =", i);
                for (j = 0; j < 16; j++)
                {
                    //System.err.printf("{0:x} ", ksl_tab[i * 16 + j]);
                }
                //System.err.printf("\n");
            }
            //#endif

            for (i = 0; i < 9; i++)
                OPL.P_CH[i].Muted = 0x00;
            for (i = 0; i < 6; i++)
                OPL.MuteSpc[i] = 0x00;


            /* Amplitude modulation: 27 output levels (triangle waveform); 1 level takes one of: 192, 256 or 448 samples */
            /* One entry from LFO_AM_TABLE lasts for 64 samples */
            OPL.lfo_am_inc = (int)((1.0 / 64.0) * (1 << LFO_SH) * OPL.freqbase);

            /* Vibrato: 8 output levels (triangle waveform); 1 level takes 1024 samples */
            OPL.lfo_pm_inc = (int)((1.0 / 1024.0) * (1 << LFO_SH) * OPL.freqbase);

            /*logerror ("OPL.lfo_am_inc = %8x ; OPL.lfo_pm_inc = %8x\n", OPL.lfo_am_inc, OPL.lfo_pm_inc);*/

            /* Noise generator: a step takes 1 sample */
            OPL.noise_f = (int)((1.0 / 1.0) * (1 << FREQ_SH) * OPL.freqbase);

            OPL.eg_timer_add = (int)((1 << EG_SH) * OPL.freqbase);
            OPL.eg_timer_overflow = (1) * (1 << EG_SH);
            /*logerror("OPLinit eg_timer_add=%8x eg_timer_overflow=%8x\n", OPL.eg_timer_add, OPL.eg_timer_overflow);*/

        }

        private void FM_KEYON(OPL_SLOT SLOT, int key_set)
        {
            if (SLOT.key == 0)
            {
                /* restart Phase Generator */
                SLOT.Cnt = 0;
                /* phase . Attack */
                SLOT.state = EG_ATT;
            }
            SLOT.key |= key_set;
        }

        private void FM_KEYOFF(OPL_SLOT SLOT, int key_clr)
        {
            if (SLOT.key != 0)
            {
                SLOT.key &= key_clr;

                if (SLOT.key == 0)
                {
                    /* phase . Release */
                    if (SLOT.state > EG_REL)
                        SLOT.state = EG_REL;
                }
            }
        }

        /* update phase increment counter of operator (also update the EG rates if necessary) */
        private void CALC_FCSLOT(OPL_CH CH, OPL_SLOT SLOT)
        {
            int ksr;

            /* (frequency) phase increment counter */
            SLOT.Incr = CH.fc * SLOT.mul;
            ksr = CH.kcode >> SLOT.KSR;

            if (SLOT.ksr != ksr)
            {
                SLOT.ksr = (byte)ksr;

                /* calculate envelope generator rates */
                if ((SLOT.ar + SLOT.ksr) < 16 + 62)
                {
                    SLOT.eg_sh_ar = eg_rate_shift[SLOT.ar + SLOT.ksr];
                    SLOT.eg_sel_ar = eg_rate_select[SLOT.ar + SLOT.ksr];
                }
                else
                {
                    SLOT.eg_sh_ar = 0;
                    SLOT.eg_sel_ar = 13 * RATE_STEPS;
                }
                //System.err.printf("CALC_FCSLOT SLOT.eg_sel_ar:{0} SLOT.ar:{1} SLOT.ksr:{2}\n", SLOT.eg_sel_ar, SLOT.ar, SLOT.ksr);
                SLOT.eg_sh_dr = eg_rate_shift[SLOT.dr + SLOT.ksr];
                SLOT.eg_sel_dr = eg_rate_select[SLOT.dr + SLOT.ksr];
                SLOT.eg_sh_rr = eg_rate_shift[SLOT.rr + SLOT.ksr];
                SLOT.eg_sel_rr = eg_rate_select[SLOT.rr + SLOT.ksr];
            }
        }

        /* set multi,am,vib,EG-TYP,KSR,mul */
        private void set_mul(FM_OPL OPL, int slot, int v)
        {
            OPL_CH CH = OPL.P_CH[slot / 2];
            OPL_SLOT SLOT = CH.SLOT[slot & 1];

            SLOT.mul = mul_tab[v & 0x0f];
            SLOT.KSR = (byte)((v & 0x10) != 0 ? 0 : 2);
            SLOT.eg_type = (byte)(v & 0x20);
            SLOT.vib = (byte)(v & 0x40);
            SLOT.AMmask = (int)((v & 0x80) != 0 ? ~0 : 0);
            CALC_FCSLOT(CH, SLOT);
        }

        /* set ksl & tl */
        private void set_ksl_tl(FM_OPL OPL, int slot, int v)
        {
            OPL_CH CH = OPL.P_CH[slot / 2];
            OPL_SLOT SLOT = CH.SLOT[slot & 1];

            SLOT.ksl = (byte)ksl_shift[v >> 6];
            SLOT.TL = (int)((v & 0x3f) << (ENV_BITS - 1 - 7)); /* 7 bits TL (bit 6 = always 0) */

            SLOT.TLL = (int)(SLOT.TL + (CH.ksl_base >> SLOT.ksl));
        }

        /* set attack rate & decay rate  */
        private void set_ar_dr(FM_OPL OPL, int slot, int v)
        {
            OPL_CH CH = OPL.P_CH[slot / 2];
            OPL_SLOT SLOT = CH.SLOT[slot & 1];

            SLOT.ar = (int)((v >> 4) != 0 ? (16 + ((v >> 4) << 2)) : 0);

            if ((SLOT.ar + SLOT.ksr) < 16 + 62)
            {
                SLOT.eg_sh_ar = eg_rate_shift[SLOT.ar + SLOT.ksr];
                SLOT.eg_sel_ar = eg_rate_select[SLOT.ar + SLOT.ksr];
            }
            else
            {
                SLOT.eg_sh_ar = 0;
                SLOT.eg_sel_ar = 13 * RATE_STEPS;
            }
            //System.err.printf("SLOT.eg_sel_ar:{0} SLOT.ar:{1} SLOT.ksr:{2}\n", SLOT.eg_sel_ar, SLOT.ar, SLOT.ksr);

            SLOT.dr = (int)((v & 0x0f) != 0 ? (16 + ((v & 0x0f) << 2)) : 0);
            SLOT.eg_sh_dr = eg_rate_shift[SLOT.dr + SLOT.ksr];
            SLOT.eg_sel_dr = eg_rate_select[SLOT.dr + SLOT.ksr];
        }

        /* set sustain level & release rate */
        private void set_sl_rr(FM_OPL OPL, int slot, int v)
        {
            OPL_CH CH = OPL.P_CH[slot / 2];
            OPL_SLOT SLOT = CH.SLOT[slot & 1];

            SLOT.sl = sl_tab[v >> 4];

            SLOT.rr = (int)((v & 0x0f) != 0 ? (16 + ((v & 0x0f) << 2)) : 0);
            SLOT.eg_sh_rr = eg_rate_shift[SLOT.rr + SLOT.ksr];
            SLOT.eg_sel_rr = eg_rate_select[SLOT.rr + SLOT.ksr];
        }


        /* write a value v to register r on OPL chip */
        private void OPLWriteReg(FM_OPL OPL, int r, int v)
        {
            OPL_CH CH;
            int slot;
            int block_fnum;

            //System.err.printf("writeReg:{0}:{1}\n", r, v);

            /* adjust bus to 8 bits */
            r &= 0xff;
            v &= 0xff;

            /*if (LOG_CYM_FILE && (cymfile) && (r!=0) )
            {
                fputc( (unsigned char)r, cymfile );
                fputc( (unsigned char)v, cymfile );
            }*/


            switch (r & 0xe0)
            {
                case 0x00:  /* 00-1f:control */
                    switch (r & 0x1f)
                    {
                        case 0x01:  /* waveform select enable */
                            if ((OPL.type & OPL_TYPE_WAVESEL) != 0)
                            {
                                OPL.wavesel = (byte)(v & 0x20);
                                /* do not change the waveform previously selected */
                            }
                            break;
                        case 0x02:  /* Timer 1 */
                            OPL.T[0] = (int)((256 - v) * 4);
                            break;
                        case 0x03:  /* Timer 2 */
                            OPL.T[1] = (int)((256 - v) * 16);
                            break;
                        case 0x04:  /* IRQ clear / mask and Timer enable */
                            if ((v & 0x80) != 0)
                            {   /* IRQ flag clear */
                                OPL_STATUS_RESET(OPL, 0x7f - 0x08); /* don't reset BFRDY flag or we will have to call deltat module to set the flag */
                            }
                            else
                            {   /* set IRQ mask ,timer enable*/
                                byte st1 = (byte)(v & 1);
                                byte st2 = (byte)((v >> 1) & 1);

                                /* IRQRST,T1MSK,t2MSK,EOSMSK,BRMSK,x,ST2,ST1 */
                                OPL_STATUS_RESET(OPL, v & (0x78 - 0x08));
                                OPL_STATUSMASK_SET(OPL, (~v) & 0x78);

                                /* timer 2 */
                                if (OPL.st[1] != st2)
                                {
                                    //attotime period = st2 ? attotime_mul(OPL.TimerBase, OPL.T[1]) : attotime_zero;
                                    OPL.st[1] = st2;
                                    //if (OPL.timer_handler) (OPL.timer_handler)(OPL.TimerParam,1,period);
                                }
                                /* timer 1 */
                                if (OPL.st[0] != st1)
                                {
                                    //attotime period = st1 ? attotime_mul(OPL.TimerBase, OPL.T[0]) : attotime_zero;
                                    OPL.st[0] = st1;
                                    //if (OPL.timer_handler) (OPL.timer_handler)(OPL.TimerParam,0,period);
                                }
                            }
                            break;
                        //#if BUILD_Y8950
                        //		case 0x06:		/* Key Board OUT */
                        //			if(OPL.type&OPL_TYPE_KEYBOARD)
                        //			{
                        //				if(OPL.keyboardhandler_w)
                        //					OPL.keyboardhandler_w(OPL.keyboard_param,v);
                        //# ifdef _DEBUG
                        //				else
                        //					logerror("Y8950: write unmapped KEYBOARD port\n");
                        //#endif
                        //                    }
                        //                    break;
                        //        case 0x07:  /* DELTA-T control 1 : START,REC,MEMDATA,REPT,SPOFF,x,x,RST */
                        //            if (OPL.type & OPL_TYPE_ADPCM)
                        //                YM_DELTAT_ADPCM_Write(OPL.deltat, r - 0x07, v);
                        //            break;
                        //#endif
                        case 0x08:  /* MODE,DELTA-T control 2 : CSM,NOTESEL,x,x,smpl,da/ad,64k,rom */
                            OPL.mode = (byte)v;
                            //#if BUILD_Y8950
                            //			if(OPL.type&OPL_TYPE_ADPCM)
                            //				YM_DELTAT_ADPCM_Write(OPL.deltat,r-0x07,v&0x0f); /* mask 4 LSBs in register 08 for DELTA-T unit */
                            //#endif
                            break;

                        //#if BUILD_Y8950
                        //		case 0x09:		/* START ADD */
                        //		case 0x0a:
                        //		case 0x0b:		/* STOP ADD  */
                        //		case 0x0c:
                        //		case 0x0d:		/* PRESCALE   */
                        //		case 0x0e:
                        //		case 0x0f:		/* ADPCM data write */
                        //		case 0x10:		/* DELTA-N    */
                        //		case 0x11:		/* DELTA-N    */
                        //		case 0x12:		/* ADPCM volume */
                        //			if(OPL.type&OPL_TYPE_ADPCM)
                        //				YM_DELTAT_ADPCM_Write(OPL.deltat,r-0x07,v);
                        //			break;

                        //		case 0x15:		/* DAC data high 8 bits (F7,F6...F2) */
                        //		case 0x16:		/* DAC data low 2 bits (F1, F0 in bits 7,6) */
                        //		case 0x17:		/* DAC data shift (S2,S1,S0 in bits 2,1,0) */
                        //# ifdef _DEBUG
                        //			logerror("FMOPL.C: DAC data register written, but not implemented reg=%02x val=%02x\n",r,v);
                        //#endif
                        //            break;

                        //        case 0x18:      /* I/O CTRL (Direction) */
                        //            if (OPL.type & OPL_TYPE_IO)
                        //                OPL.portDirection = v & 0x0f;
                        //            break;
                        //        case 0x19:      /* I/O DATA */
                        //            if (OPL.type & OPL_TYPE_IO)
                        //            {
                        //                OPL.portLatch = v;
                        //                if (OPL.porthandler_w)
                        //                    OPL.porthandler_w(OPL.port_param, v & OPL.portDirection);
                        //            }
                        //            break;
                        //#endif
                        default:
                            //# ifdef _DEBUG
                            //logerror("FMOPL.C: write to unknown register: %02x\n", r);
                            //#endif
                            break;
                    }
                    break;
                case 0x20:  /* am ON, vib ON, ksr, eg_type, mul */
                    slot = slot_array[r & 0x1f];
                    if (slot < 0) return;
                    set_mul(OPL, slot, v);
                    break;
                case 0x40:
                    slot = slot_array[r & 0x1f];
                    if (slot < 0) return;
                    set_ksl_tl(OPL, slot, v);
                    break;
                case 0x60:
                    slot = slot_array[r & 0x1f];
                    if (slot < 0) return;
                    set_ar_dr(OPL, slot, v);
                    break;
                case 0x80:
                    slot = slot_array[r & 0x1f];
                    if (slot < 0) return;
                    set_sl_rr(OPL, slot, v);
                    break;
                case 0xa0:
                    if (r == 0xbd)          /* am depth, vibrato depth, r,bd,sd,tom,tc,hh */
                    {
                        OPL.lfo_am_depth = (byte)(v & 0x80);
                        OPL.lfo_pm_depth_range = (byte)((v & 0x40) != 0 ? 8 : 0);

                        OPL.rhythm = (byte)(v & 0x3f);

                        if ((OPL.rhythm & 0x20) != 0)
                        {
                            /* BD key on/off */
                            if ((v & 0x10) != 0)
                            {
                                FM_KEYON(OPL.P_CH[6].SLOT[SLOT1], 2);
                                FM_KEYON(OPL.P_CH[6].SLOT[SLOT2], 2);
                            }
                            else
                            {
                                FM_KEYOFF(OPL.P_CH[6].SLOT[SLOT1], ~(int)2);
                                FM_KEYOFF(OPL.P_CH[6].SLOT[SLOT2], ~(int)2);
                            }
                            /* HH key on/off */
                            if ((v & 0x01) != 0) FM_KEYON(OPL.P_CH[7].SLOT[SLOT1], 2);
                            else FM_KEYOFF(OPL.P_CH[7].SLOT[SLOT1], ~(int)2);
                            /* SD key on/off */
                            if ((v & 0x08) != 0) FM_KEYON(OPL.P_CH[7].SLOT[SLOT2], 2);
                            else FM_KEYOFF(OPL.P_CH[7].SLOT[SLOT2], ~(int)2);
                            /* TOM key on/off */
                            if ((v & 0x04) != 0) FM_KEYON(OPL.P_CH[8].SLOT[SLOT1], 2);
                            else FM_KEYOFF(OPL.P_CH[8].SLOT[SLOT1], ~(int)2);
                            /* TOP-CY key on/off */
                            if ((v & 0x02) != 0) FM_KEYON(OPL.P_CH[8].SLOT[SLOT2], 2);
                            else FM_KEYOFF(OPL.P_CH[8].SLOT[SLOT2], ~(int)2);
                        }
                        else
                        {
                            /* BD key off */
                            FM_KEYOFF(OPL.P_CH[6].SLOT[SLOT1], ~(int)2);
                            FM_KEYOFF(OPL.P_CH[6].SLOT[SLOT2], ~(int)2);
                            /* HH key off */
                            FM_KEYOFF(OPL.P_CH[7].SLOT[SLOT1], ~(int)2);
                            /* SD key off */
                            FM_KEYOFF(OPL.P_CH[7].SLOT[SLOT2], ~(int)2);
                            /* TOM key off */
                            FM_KEYOFF(OPL.P_CH[8].SLOT[SLOT1], ~(int)2);
                            /* TOP-CY off */
                            FM_KEYOFF(OPL.P_CH[8].SLOT[SLOT2], ~(int)2);
                        }
                        return;
                    }
                    /* keyon,block,fnum */
                    if ((r & 0x0f) > 8) return;
                    CH = OPL.P_CH[r & 0x0f];
                    if ((r & 0x10) == 0)
                    {   /* a0-a8 */
                        block_fnum = (int)((CH.block_fnum & 0x1f00) | (int)v);
                    }
                    else
                    {   /* b0-b8 */
                        block_fnum = (int)((int)((v & 0x1f) << 8) | (CH.block_fnum & 0xff));

                        if ((v & 0x20) != 0)
                        {
                            FM_KEYON(CH.SLOT[SLOT1], 1);
                            FM_KEYON(CH.SLOT[SLOT2], 1);
                        }
                        else
                        {
                            FM_KEYOFF(CH.SLOT[SLOT1], ~(int)1);
                            FM_KEYOFF(CH.SLOT[SLOT2], ~(int)1);
                        }
                    }
                    /* update */
                    if (CH.block_fnum != block_fnum)
                    {
                        byte block = (byte)(block_fnum >> 10);

                        CH.block_fnum = (int)block_fnum;

                        CH.ksl_base = ksl_tab[block_fnum >> 6];
                        CH.fc = OPL.fn_tab[block_fnum & 0x03ff] >> (7 - block);

                        /* BLK 2,1,0 bits . bits 3,2,1 of kcode */
                        CH.kcode = (byte)((CH.block_fnum & 0x1c00) >> 9);

                        /* the info below is actually opposite to what is stated in the Manuals (verifed on real YM3812) */
                        /* if notesel == 0 . lsb of kcode is bit 10 (MSB) of fnum  */
                        /* if notesel == 1 . lsb of kcode is bit 9 (MSB-1) of fnum */
                        if ((OPL.mode & 0x40) != 0)
                            CH.kcode |= (byte)((CH.block_fnum & 0x100) >> 8); /* notesel == 1 */
                        else
                            CH.kcode |= (byte)((CH.block_fnum & 0x200) >> 9); /* notesel == 0 */

                        /* refresh Total Level in both SLOTs of this channel */
                        CH.SLOT[SLOT1].TLL = (int)(CH.SLOT[SLOT1].TL + (CH.ksl_base >> CH.SLOT[SLOT1].ksl));
                        CH.SLOT[SLOT2].TLL = (int)(CH.SLOT[SLOT2].TL + (CH.ksl_base >> CH.SLOT[SLOT2].ksl));

                        /* refresh frequency counter in both SLOTs of this channel */
                        CALC_FCSLOT(CH, CH.SLOT[SLOT1]);
                        CALC_FCSLOT(CH, CH.SLOT[SLOT2]);
                    }
                    break;
                case 0xc0:
                    /* FB,C */
                    if ((r & 0x0f) > 8) return;
                    CH = OPL.P_CH[r & 0x0f];
                    CH.SLOT[SLOT1].FB = (byte)(((v >> 1) & 7) != 0 ? ((v >> 1) & 7) + 7 : 0);
                    CH.SLOT[SLOT1].CON = (byte)(v & 1);
                    //CH.SLOT[SLOT1].connect1 = (int)(CH.SLOT[SLOT1].CON != 0 ? OPL.output[0] : OPL.phase_modulation);
                    CH.SLOT[SLOT1].ptrConnect1 = CH.SLOT[SLOT1].CON != 0 ? 0 : 1;
                    break;
                case 0xe0: /* waveform select */
                           /* simply ignore write to the waveform select register if selecting not enabled in test register */
                    if (OPL.wavesel != 0)
                    {
                        slot = slot_array[r & 0x1f];
                        if (slot < 0) return;
                        CH = OPL.P_CH[slot / 2];

                        CH.SLOT[slot & 1].wavetable = (int)((v & 0x03) * SIN_LEN);
                    }
                    break;
            }
        }

        /*static TIMER_CALLBACK( cymfile_callback )
        {
            if (cymfile)
            {
                fputc( (unsigned char)0, cymfile );
            }
        }*/

        /* lock/unsynchronized for common table */
        private int OPL_LockTable()
        {
            num_lock++;
            if (num_lock > 1) return 0;

            /* first time */

            /* allocate total level table (128kb space) */
            if (init_tables() == 0)
            {
                num_lock--;
                return -1;
            }

            /*if (LOG_CYM_FILE)
            {
                cymfile = fopen("3812_.cym","wb");
                if (cymfile)
                    timer_pulse ( device.machine, ATTOTIME_IN_HZ(110), NULL, 0, cymfile_callback); //110 Hz pulse timer
                else
                    logerror("Could not create file 3812_.cym\n");
            }*/

            return 0;
        }

        private void OPL_UnLockTable()
        {
            if (num_lock != 0) num_lock--;
            if (num_lock != 0) return;

            /* last time */

            OPLCloseTable();

            /*if (cymfile)
                fclose (cymfile);
            cymfile = NULL;*/
        }

        private void OPLResetChip(FM_OPL OPL)
        {
            int c, s;
            int i;

            OPL.eg_timer = 0;
            OPL.eg_cnt = 0;

            OPL.noise_rng = 1; /* noise shift register */
            OPL.mode = 0;  /* normal mode */
            OPL_STATUS_RESET(OPL, 0x7f);

            /* reset with register write */
            OPLWriteReg(OPL, 0x01, 0); /* wavesel disable */
            OPLWriteReg(OPL, 0x02, 0); /* Timer1 */
            OPLWriteReg(OPL, 0x03, 0); /* Timer2 */
            OPLWriteReg(OPL, 0x04, 0); /* IRQ mask clear */
            for (i = 0xff; i >= 0x20; i--) OPLWriteReg(OPL, i, 0);

            /* reset operator parameters */
            for (c = 0; c < 9; c++)
            {
                OPL_CH CH = OPL.P_CH[c];
                for (s = 0; s < 2; s++)
                {
                    /* wave table */
                    CH.SLOT[s].wavetable = 0;
                    CH.SLOT[s].state = EG_OFF;
                    CH.SLOT[s].volume = MAX_ATT_INDEX;
                }
            }
            //#if BUILD_Y8950
            //	if(OPL.type&OPL_TYPE_ADPCM)
            //	{
            //		YM_DELTAT *DELTAT = OPL.deltat;

            //		DELTAT.freqbase = OPL.freqbase;
            //		DELTAT.output_pointer = &OPL.output_deltat[0];
            //		DELTAT.portshift = 5;
            //		DELTAT.output_range = 1<<23;
            //		YM_DELTAT_ADPCM_Reset(DELTAT,0,YM_DELTAT_EMULATION_MODE_NORMAL);
            //	}
            //#endif
        }


        //#if 0
        ////static STATE_POSTLOAD( OPL_postload )
        //static void OPL_postload(void* param)
        //{
        //	FM_OPL *OPL = (FM_OPL *)param;
        //	int slot, ch;

        //	for( ch=0 ; ch < 9 ; ch++ )
        //	{
        //		OPL_CH *CH = &OPL.P_CH[ch];

        //		/* Look up key scale level */
        //		(int)32 block_fnum = CH.block_fnum;
        //		CH.ksl_base = ksl_tab[block_fnum >> 6];
        //		CH.fc       = OPL.fn_tab[block_fnum & 0x03ff] >> (7 - (block_fnum >> 10));

        //		for( slot=0 ; slot < 2 ; slot++ )
        //		{
        //			OPL_SLOT *SLOT = &CH.SLOT[slot];

        //			/* Calculate key scale rate */
        //			SLOT.ksr = CH.kcode >> SLOT.KSR;

        //			/* Calculate attack, decay and release rates */
        //			if ((SLOT.ar + SLOT.ksr) < 16+62)
        //			{
        //				SLOT.eg_sh_ar  = eg_rate_shift [SLOT.ar + SLOT.ksr ];
        //				SLOT.eg_sel_ar = eg_rate_select[SLOT.ar + SLOT.ksr ];
        //			}
        //			else
        //			{
        //				SLOT.eg_sh_ar  = 0;
        //				SLOT.eg_sel_ar = 13*RATE_STEPS;
        //			}
        //			SLOT.eg_sh_dr  = eg_rate_shift [SLOT.dr + SLOT.ksr ];
        //			SLOT.eg_sel_dr = eg_rate_select[SLOT.dr + SLOT.ksr ];
        //			SLOT.eg_sh_rr  = eg_rate_shift [SLOT.rr + SLOT.ksr ];
        //			SLOT.eg_sel_rr = eg_rate_select[SLOT.rr + SLOT.ksr ];

        //			/* Calculate phase increment */
        //			SLOT.Incr = CH.fc * SLOT.mul;

        //			/* Total level */
        //			SLOT.TLL = SLOT.TL + (CH.ksl_base >> SLOT.ksl);

        //			/* Connect output */
        //			SLOT.connect1 = SLOT.CON ? &OPL.output[0] : &OPL.phase_modulation;
        //		}
        //	}
        //#if BUILD_Y8950
        //	if ( (OPL.type & OPL_TYPE_ADPCM) && (OPL.deltat) )
        //	{
        //		// We really should call the postlod function for the YM_DELTAT, but it's hard without registers
        //		// (see the way the YM2610 does it)
        //		//YM_DELTAT_postload(OPL.deltat, REGS);
        //	}
        //#endif
        //}


        //static void OPLsave_state_channel(OPL_CH *CH)
        //{
        //	int slot, ch;

        //	for( ch=0 ; ch < 9 ; ch++, CH++ )
        //	{
        //		// channel 
        //		state_save_register_device_item(device, ch, CH.block_fnum);
        //		state_save_register_device_item(device, ch, CH.kcode);
        //		// slots 
        //		for( slot=0 ; slot < 2 ; slot++ )
        //		{
        //			OPL_SLOT *SLOT = &CH.SLOT[slot];

        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.ar);
        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.dr);
        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.rr);
        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.KSR);
        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.ksl);
        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.mul);

        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.Cnt);
        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.FB);
        //			state_save_register_device_item_array(device, ch * 2 + slot, SLOT.op1_out);
        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.CON);

        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.eg_type);
        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.state);
        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.TL);
        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.volume);
        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.sl);
        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.key);

        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.AMmask);
        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.vib);

        //			state_save_register_device_item(device, ch * 2 + slot, SLOT.wavetable);
        //		}
        //	}
        //}
        //#endif


        /* Register savestate for a virtual YM3812/YM3526/Y8950 */

        /*static void OPL_save_state(FM_OPL *OPL)
        {
            OPLsave_state_channel(device, OPL.P_CH);

            state_save_register_device_item(device, 0, OPL.eg_cnt);
            state_save_register_device_item(device, 0, OPL.eg_timer);

            state_save_register_device_item(device, 0, OPL.rhythm);

            state_save_register_device_item(device, 0, OPL.lfo_am_depth);
            state_save_register_device_item(device, 0, OPL.lfo_pm_depth_range);
            state_save_register_device_item(device, 0, OPL.lfo_am_cnt);
            state_save_register_device_item(device, 0, OPL.lfo_pm_cnt);

            state_save_register_device_item(device, 0, OPL.noise_rng);
            state_save_register_device_item(device, 0, OPL.noise_p);

            if( OPL.type & OPL_TYPE_WAVESEL )
            {
                state_save_register_device_item(device, 0, OPL.wavesel);
            }

            state_save_register_device_item_array(device, 0, OPL.T);
            state_save_register_device_item_array(device, 0, OPL.st);

        // #if BUILD_Y8950
            if ( (OPL.type & OPL_TYPE_ADPCM) && (OPL.deltat) )
            {
                YM_DELTAT_savestate(device, OPL.deltat);
            }

            if ( OPL.type & OPL_TYPE_IO )
            {
                state_save_register_device_item(device, 0, OPL.portDirection);
                state_save_register_device_item(device, 0, OPL.portLatch);
            }
        // #endif

            state_save_register_device_item(device, 0, OPL.address);
            state_save_register_device_item(device, 0, OPL.status);
            state_save_register_device_item(device, 0, OPL.statusmask);
            state_save_register_device_item(device, 0, OPL.mode);

            state_save_register_postload(device.machine, OPL_postload, OPL);
        }*/


        /* Create one of virtual YM3812/YM3526/Y8950 */
        /* 'clock' is chip clock in Hz  */
        /* 'rate'  is sampling rate  */
        private FM_OPL OPLCreate(int clock, int rate, int type)
        {
            //byte[] ptr;
            FM_OPL OPL;
            //int state_size;

            if (OPL_LockTable() == -1) return null;

            ///* calculate OPL state size */
            //state_size = sizeof(FM_OPL);

            ////#if BUILD_Y8950
            //if ((type & OPL_TYPE_ADPCM) != 0) state_size += sizeof(YM_DELTAT);
            ////#endif

            ///* allocate memory block */
            //ptr = new byte[state_size];// (char*)malloc(state_size);

            //if (ptr == null)
            //    return null;

            ///* clear */
            //for (int i = 0; i < state_size; i++) ptr[i] = 0;
            ////memset(ptr, 0, state_size);

            //OPL = (FM_OPL)ptr;

            //ptr += sizeof(FM_OPL);

            ////#if BUILD_Y8950
            //if ((type & OPL_TYPE_ADPCM) != 0)
            //{
            //    OPL.deltat = (YM_DELTAT)ptr;
            //}
            //ptr += sizeof(YM_DELTAT);
            ////#endif

            OPL = new FM_OPL();
            OPL.type = (byte)type;
            OPL.clock = clock;
            OPL.rate = rate;

            /* init Global tables */
            OPL_initalize(OPL);

            return OPL;
        }

        /* Destroy one of virtual YM3812 */
        private void OPLDestroy(FM_OPL OPL)
        {
            OPL_UnLockTable();
            //free(OPL);
        }

        /* Optional handlers */

        private void OPLSetTimerHandler(FM_OPL OPL, OPL_TIMERHANDLER timer_handler, Object param)
        {
            OPL.timer_handler = timer_handler;
            OPL.TimerParam = param;
        }

        private void OPLSetIRQHandler(FM_OPL OPL, OPL_IRQHANDLER IRQHandler, ym3526_state param)
        {
            OPL.IRQHandler = IRQHandler;
            OPL.IRQParam = param;
        }

        private void OPLSetUpdateHandler(FM_OPL OPL, OPL_UPDATEHANDLER UpdateHandler, ym3526_state param)
        {
            OPL.UpdateHandler = UpdateHandler;
            OPL.UpdateParam = param;
        }

        private int OPLWrite(FM_OPL OPL, int a, int v)
        {
            if ((a & 1) == 0)
            {   /* address port */
                OPL.address = (byte)(v & 0xff);
            }
            else
            {   /* data port */
                if (OPL.UpdateHandler != null) OPL.UpdateHandler.accept(OPL.UpdateParam/*,0*/);
                OPLWriteReg(OPL, OPL.address, v);
            }
            return OPL.status >> 7;
        }

        private byte OPLRead(FM_OPL OPL, int a)
        {
            if ((a & 1) == 0)
            {
                /* status port */

                //#if BUILD_Y8950

                //		if(OPL.type&OPL_TYPE_ADPCM)	/* Y8950 */
                //		{
                //			return (OPL.status & (OPL.statusmask|0x80)) | (OPL.deltat.PCM_BSY&1);
                //		}

                //#endif

                /* OPL and OPL2 */
                return (byte)(OPL.status & (OPL.statusmask | 0x80));
            }

            //#if BUILD_Y8950
            //	/* data port */
            //	switch(OPL.address)
            //	{
            //	case 0x05: /* KeyBoard IN */
            //		if(OPL.type&OPL_TYPE_KEYBOARD)
            //		{
            //			if(OPL.keyboardhandler_r)
            //				return OPL.keyboardhandler_r(OPL.keyboard_param);
            //# ifdef _DEBUG
            //			else
            //				logerror("Y8950: read unmapped KEYBOARD port\n");
            //#endif
            //        }
            //		return 0;

            //	case 0x0f: /* ADPCM-DATA  */
            //		if(OPL.type&OPL_TYPE_ADPCM)
            //		{
            //			(int)8 val;

            //val = YM_DELTAT_ADPCM_Read(OPL.deltat);
            //			/*logerror("Y8950: read ADPCM value read=%02x\n",val);*/
            //			return val;
            //		}
            //		return 0;

            //	case 0x19: /* I/O DATA    */
            //		if(OPL.type&OPL_TYPE_IO)
            //		{
            //			if(OPL.porthandler_r)
            //				return OPL.porthandler_r(OPL.port_param);
            //#ifdef _DEBUG
            //			else
            //				logerror("Y8950:read unmapped I/O port\n");
            //#endif
            //		}
            //		return 0;
            //	case 0x1a: /* PCM-DATA    */
            //		if(OPL.type&OPL_TYPE_ADPCM)
            //		{
            //#ifdef _DEBUG
            //			logerror("Y8950 A/D conversion is accessed but not implemented !\n");
            //#endif
            //			return 0x80; /* 2's complement PCM data - result from A/D conversion */
            //		}
            //		return 0;
            //	}
            //#endif

            return (byte)0xff;
        }

        /* CSM Key Controll */
        private void CSMKeyControll(OPL_CH CH)
        {
            FM_KEYON(CH.SLOT[SLOT1], 4);
            FM_KEYON(CH.SLOT[SLOT2], 4);

            /* The key off should happen exactly one sample later - not implemented correctly yet */

            FM_KEYOFF(CH.SLOT[SLOT1], ~(int)4);
            FM_KEYOFF(CH.SLOT[SLOT2], ~(int)4);
        }


        private int OPLTimerOver(FM_OPL OPL, int c)
        {
            if (c != 0)
            {   /* Timer B */
                OPL_STATUS_SET(OPL, 0x20);
            }
            else
            {   /* Timer A */
                OPL_STATUS_SET(OPL, 0x40);
                /* CSM mode key,TL controll */
                if ((OPL.mode & 0x80) != 0)
                {   /* CSM mode total level latch and auto key on */
                    int ch;
                    if (OPL.UpdateHandler != null) OPL.UpdateHandler.accept(OPL.UpdateParam/*,0*/);
                    for (ch = 0; ch < 9; ch++)
                        CSMKeyControll(OPL.P_CH[ch]);
                }
            }
            /* reload timer */
            //if (OPL.timer_handler) (OPL.timer_handler)(OPL.TimerParam,c,attotime_mul(OPL.TimerBase, OPL.T[c]));
            return OPL.status >> 7;
        }


        private final int MAX_OPL_CHIPS = 2;


        //#if (BUILD_YM3812)

        //void * ym3812_init((int)32 clock, (int)32 rate)
        //{
        //	/* emulator create */
        //	FM_OPL *YM3812 = OPLCreate(clock,rate,OPL_TYPE_YM3812);
        //	if (YM3812)
        //	{
        //		//OPL_save_state(YM3812);
        //		ym3812_reset_chip(YM3812);
        //	}
        //	return YM3812;
        //}

        //void ym3812_shutdown(void *chip)
        //{
        //	FM_OPL *YM3812 = (FM_OPL *)chip;
        //	/* emulator shutdown */
        //	OPLDestroy(YM3812);
        //}
        //void ym3812_reset_chip(void *chip)
        //{
        //	FM_OPL *YM3812 = (FM_OPL *)chip;
        //	OPLResetChip(YM3812);
        //}

        //int ym3812_write(void *chip, int a, int v)
        //{
        //	FM_OPL *YM3812 = (FM_OPL *)chip;
        //	return OPLWrite(YM3812, a, v);
        //}

        //unsigned char ym3812_read(void *chip, int a)
        //{
        //	FM_OPL *YM3812 = (FM_OPL *)chip;
        //	/* YM3812 always returns bit2 and bit1 in HIGH state */
        //	return OPLRead(YM3812, a) | 0x06 ;
        //}
        //int ym3812_timer_over(void *chip, int c)
        //{
        //	FM_OPL *YM3812 = (FM_OPL *)chip;
        //	return OPLTimerOver(YM3812, c);
        //}

        //void ym3812_set_timer_handler(void *chip, OPL_TIMERHANDLER timer_handler, void *param)
        //{
        //	FM_OPL *YM3812 = (FM_OPL *)chip;
        //	OPLSetTimerHandler(YM3812, timer_handler, param);
        //}
        //void ym3812_set_irq_handler(void *chip,OPL_IRQHANDLER IRQHandler,void *param)
        //{
        //	FM_OPL *YM3812 = (FM_OPL *)chip;
        //	OPLSetIRQHandler(YM3812, IRQHandler, param);
        //}
        //void ym3812_set_update_handler(void *chip,OPL_UPDATEHANDLER UpdateHandler,void *param)
        //{
        //	FM_OPL *YM3812 = (FM_OPL *)chip;
        //	OPLSetUpdateHandler(YM3812, UpdateHandler, param);
        //}


        ///*
        //** Generate samples for one of the YM3812's
        //**
        //** 'which' is the virtual YM3812 number
        //** '*buffer' is the output buffer pointer
        //** 'length' is the number of samples that should be generated
        //*/
        //void ym3812_update_one(void *chip, OPLSAMPLE **buffer, int length)
        //{
        //	FM_OPL		*OPL = (FM_OPL *)chip;
        //	(int)8		rhythm = OPL.rhythm&0x20;
        //	OPLSAMPLE	*bufL = buffer[0];
        //	OPLSAMPLE	*bufR = buffer[1];
        //	int i;

        //	if (! length)
        //	{
        //		refresh_eg(OPL);
        //		return;
        //	}

        //	for( i=0; i < length ; i++ )
        //	{
        //		int lt;

        //		OPL.output[0] = 0;

        //		advance_lfo(OPL);

        //		/* FM part */
        //		OPL_CALC_CH(OPL, &OPL.P_CH[0]);
        //		OPL_CALC_CH(OPL, &OPL.P_CH[1]);
        //		OPL_CALC_CH(OPL, &OPL.P_CH[2]);
        //		OPL_CALC_CH(OPL, &OPL.P_CH[3]);
        //		OPL_CALC_CH(OPL, &OPL.P_CH[4]);
        //		OPL_CALC_CH(OPL, &OPL.P_CH[5]);

        //		if(!rhythm)
        //		{
        //			OPL_CALC_CH(OPL, &OPL.P_CH[6]);
        //			OPL_CALC_CH(OPL, &OPL.P_CH[7]);
        //			OPL_CALC_CH(OPL, &OPL.P_CH[8]);
        //		}
        //		else		/* Rhythm part */
        //		{
        //			OPL_CALC_RH(OPL, &OPL.P_CH[0], (OPL.noise_rng>>0)&1 );
        //		}

        //		lt = OPL.output[0];

        //		lt >>= FINAL_SH;

        //		/* limit check */
        //		//lt = limit( lt , MAXOUT, MINOUT );

        //# ifdef SAVE_SAMPLE
        //		if (which==0)
        //		{
        //			SAVE_ALL_CHANNELS
        //		}
        //#endif

        //        /* store to sound buffer */
        //        bufL[i] = lt;
        //		bufR[i] = lt;

        //		advance(OPL);
        //	}

        //}
        //#endif /* BUILD_YM3812 */



        //#if (BUILD_YM3526)

        public FM_OPL ym3526_init(int clock, int rate)
        {
            /* emulator create */
            FM_OPL YM3526 = OPLCreate(clock, rate, OPL_TYPE_YM3526);
            if (YM3526 != null)
            {
                //OPL_save_state(YM3526);
                ym3526_reset_chip(YM3526);
            }
            return YM3526;
        }

        public void ym3526_shutdown(FM_OPL chip)
        {
            FM_OPL YM3526 = (FM_OPL)chip;
            /* emulator shutdown */
            OPLDestroy(YM3526);
        }
        public void ym3526_reset_chip(FM_OPL chip)
        {
            FM_OPL YM3526 = (FM_OPL)chip;
            OPLResetChip(YM3526);
        }

        public int ym3526_write(FM_OPL chip, int a, int v)
        {
            FM_OPL YM3526 = (FM_OPL)chip;
            return OPLWrite(YM3526, a, v);
        }

        public byte ym3526_read(FM_OPL chip, int a)
        {
            FM_OPL YM3526 = (FM_OPL)chip;
            /* YM3526 always returns bit2 and bit1 in HIGH state */
            return (byte)(OPLRead(YM3526, a) | 0x06);
        }
        public int ym3526_timer_over(FM_OPL chip, int c)
        {
            FM_OPL YM3526 = (FM_OPL)chip;
            return OPLTimerOver(YM3526, c);
        }

        public void ym3526_set_timer_handler(FM_OPL chip, OPL_TIMERHANDLER timer_handler, Object param)
        {
            FM_OPL YM3526 = (FM_OPL)chip;
            OPLSetTimerHandler(YM3526, timer_handler, param);
        }
        public void ym3526_set_irq_handler(FM_OPL chip, OPL_IRQHANDLER IRQHandler, ym3526_state param)
        {
            FM_OPL YM3526 = (FM_OPL)chip;
            OPLSetIRQHandler(YM3526, IRQHandler, param);
        }
        public void ym3526_set_update_handler(FM_OPL chip, OPL_UPDATEHANDLER UpdateHandler, ym3526_state param)
        {
            FM_OPL YM3526 = (FM_OPL)chip;
            OPLSetUpdateHandler(YM3526, UpdateHandler, param);
        }


        /*
        ** Generate samples for one of the YM3526's
        **
        ** 'which' is the virtual YM3526 number
        ** '*buffer' is the output buffer pointer
        ** 'length' is the number of samples that should be generated
        */
        private void ym3526_update_one(FM_OPL chip, int[][] buffer, int length)
        {
            FM_OPL OPL = (FM_OPL)chip;
            byte rhythm = (byte)(OPL.rhythm & 0x20);
            int[] bufL = buffer[0];
            int[] bufR = buffer[1];
            int i;

            for (i = 0; i < length; i++)
            {
                int lt;

                OPL.output[0] = 0;

                advance_lfo(OPL);

                /* FM part */
                OPL_CALC_CH(OPL, OPL.P_CH[0]);
                OPL_CALC_CH(OPL, OPL.P_CH[1]);
                OPL_CALC_CH(OPL, OPL.P_CH[2]);
                OPL_CALC_CH(OPL, OPL.P_CH[3]);
                OPL_CALC_CH(OPL, OPL.P_CH[4]);
                OPL_CALC_CH(OPL, OPL.P_CH[5]);

                if (rhythm == 0)
                {
                    OPL_CALC_CH(OPL, OPL.P_CH[6]);
                    OPL_CALC_CH(OPL, OPL.P_CH[7]);
                    OPL_CALC_CH(OPL, OPL.P_CH[8]);
                }
                else        /* Rhythm part */
                {
                    OPL_CALC_RH(OPL, OPL.P_CH, (OPL.noise_rng >> 0) & 1);
                }

                lt = OPL.output[0];

                lt >>= FINAL_SH;

                /* limit check */
                //lt = limit( lt , MAXOUT, MINOUT );

                //# ifdef SAVE_SAMPLE
                //                if (which == 0)
                //                {
                //                    SAVE_ALL_CHANNELS

                //        }
                //#endif

                /* store to sound buffer */
                bufL[i] = lt;
                bufR[i] = lt;

                advance(OPL);
            }

        }
        //#endif /* BUILD_YM3526 */




        //#if BUILD_Y8950

        //static void Y8950_deltat_status_set(void *chip, (int)8 changebits)
        //{
        //	FM_OPL *Y8950 = (FM_OPL *)chip;
        //	OPL_STATUS_SET(Y8950, changebits);
        //}
        //static void Y8950_deltat_status_reset(void *chip, (int)8 changebits)
        //{
        //	FM_OPL *Y8950 = (FM_OPL *)chip;
        //	OPL_STATUS_RESET(Y8950, changebits);
        //}

        //void *y8950_init((int)32 clock, (int)32 rate)
        //{
        //	/* emulator create */
        //	FM_OPL *Y8950 = OPLCreate(clock,rate,OPL_TYPE_Y8950);
        //	if (Y8950)
        //	{
        //		Y8950.deltat.memory = NULL;
        //		Y8950.deltat.memory_size = 0x00;
        //		Y8950.deltat.memory_mask = 0x00;

        //		Y8950.deltat.status_set_handler = Y8950_deltat_status_set;
        //		Y8950.deltat.status_reset_handler = Y8950_deltat_status_reset;
        //		Y8950.deltat.status_change_which_chip = Y8950;
        //		Y8950.deltat.status_change_EOS_bit = 0x10;		/* status flag: set bit4 on End Of Sample */
        //		Y8950.deltat.status_change_BRDY_bit = 0x08;	/* status flag: set bit3 on BRDY (End Of: ADPCM analysis/synthesis, memory reading/writing) */

        //		/*Y8950.deltat.write_time = 10.0 / clock;*/		/* a single byte write takes 10 cycles of main clock */
        //		/*Y8950.deltat.read_time  = 8.0 / clock;*/		/* a single byte read takes 8 cycles of main clock */
        //		/* reset */
        //		//OPL_save_state(Y8950);
        //		y8950_reset_chip(Y8950);
        //	}

        //	return Y8950;
        //}

        //void y8950_shutdown(void *chip)
        //{
        //	FM_OPL *Y8950 = (FM_OPL *)chip;

        //	free(Y8950.deltat.memory);	Y8950.deltat.memory = NULL;

        //	/* emulator shutdown */
        //	OPLDestroy(Y8950);
        //}
        //void y8950_reset_chip(void *chip)
        //{
        //	FM_OPL *Y8950 = (FM_OPL *)chip;
        //	OPLResetChip(Y8950);
        //}

        //int y8950_write(void *chip, int a, int v)
        //{
        //	FM_OPL *Y8950 = (FM_OPL *)chip;
        //	return OPLWrite(Y8950, a, v);
        //}

        //unsigned char y8950_read(void *chip, int a)
        //{
        //	FM_OPL *Y8950 = (FM_OPL *)chip;
        //	return OPLRead(Y8950, a);
        //}
        //int y8950_timer_over(void *chip, int c)
        //{
        //	FM_OPL *Y8950 = (FM_OPL *)chip;
        //	return OPLTimerOver(Y8950, c);
        //}

        //void y8950_set_timer_handler(void *chip, OPL_TIMERHANDLER timer_handler, void *param)
        //{
        //	FM_OPL *Y8950 = (FM_OPL *)chip;
        //	OPLSetTimerHandler(Y8950, timer_handler, param);
        //}
        //void y8950_set_irq_handler(void *chip,OPL_IRQHANDLER IRQHandler,void *param)
        //{
        //	FM_OPL *Y8950 = (FM_OPL *)chip;
        //	OPLSetIRQHandler(Y8950, IRQHandler, param);
        //}
        //void y8950_set_update_handler(void *chip,OPL_UPDATEHANDLER UpdateHandler,void *param)
        //{
        //	FM_OPL *Y8950 = (FM_OPL *)chip;
        //	OPLSetUpdateHandler(Y8950, UpdateHandler, param);
        //}

        //void y8950_set_delta_t_memory(void *chip, void * deltat_mem_ptr, int deltat_mem_size )
        //{
        //	FM_OPL		*OPL = (FM_OPL *)chip;
        //	OPL.deltat.memory = ((int)8 *)(deltat_mem_ptr);
        //	OPL.deltat.memory_size = deltat_mem_size;
        //}

        //void y8950_write_pcmrom(void *chip, offs_t ROMSize, offs_t DataStart,
        //						 offs_t DataLength, final (int)8* ROMData)
        //{
        //	FM_OPL *Y8950 = (FM_OPL *)chip;

        //	if (Y8950.deltat.memory_size != ROMSize)
        //	{
        //		Y8950.deltat.memory = ((int)8*)realloc(Y8950.deltat.memory, ROMSize);
        //		Y8950.deltat.memory_size = ROMSize;
        //		memset(Y8950.deltat.memory, 0xFF, ROMSize);
        //		YM_DELTAT_calc_mem_mask(Y8950.deltat);
        //	}
        //	if (DataStart > ROMSize)
        //		return;
        //	if (DataStart + DataLength > ROMSize)
        //		DataLength = ROMSize - DataStart;

        //	memcpy(Y8950.deltat.memory + DataStart, ROMData, DataLength);

        //	return;
        //}

        ///*
        //** Generate samples for one of the Y8950's
        //**
        //** 'which' is the virtual Y8950 number
        //** '*buffer' is the output buffer pointer
        //** 'length' is the number of samples that should be generated
        //*/
        //void y8950_update_one(void *chip, OPLSAMPLE **buffer, int length)
        //{
        //	int i;
        //	FM_OPL		*OPL = (FM_OPL *)chip;
        //	(int)8		rhythm  = OPL.rhythm&0x20;
        //	YM_DELTAT	*DELTAT = OPL.deltat;
        //	OPLSAMPLE	*bufL = buffer[0];
        //	OPLSAMPLE	*bufR = buffer[1];

        //	for( i=0; i < length ; i++ )
        //	{
        //		int lt;

        //		OPL.output[0] = 0;
        //		OPL.output_deltat[0] = 0;

        //		advance_lfo(OPL);

        //		/* deltaT ADPCM */
        //		if( DELTAT.portstate&0x80 && ! OPL.MuteSpc[5] )
        //			YM_DELTAT_ADPCM_CALC(DELTAT);

        //		/* FM part */
        //		OPL_CALC_CH(OPL, &OPL.P_CH[0]);
        //		OPL_CALC_CH(OPL, &OPL.P_CH[1]);
        //		OPL_CALC_CH(OPL, &OPL.P_CH[2]);
        //		OPL_CALC_CH(OPL, &OPL.P_CH[3]);
        //		OPL_CALC_CH(OPL, &OPL.P_CH[4]);
        //		OPL_CALC_CH(OPL, &OPL.P_CH[5]);

        //		if(!rhythm)
        //		{
        //			OPL_CALC_CH(OPL, &OPL.P_CH[6]);
        //			OPL_CALC_CH(OPL, &OPL.P_CH[7]);
        //			OPL_CALC_CH(OPL, &OPL.P_CH[8]);
        //		}
        //		else		/* Rhythm part */
        //		{
        //			OPL_CALC_RH(OPL, &OPL.P_CH[0], (OPL.noise_rng>>0)&1 );
        //		}

        //		lt = OPL.output[0] + (OPL.output_deltat[0]>>11);

        //		lt >>= FINAL_SH;

        //		/* limit check */
        //		//lt = limit( lt , MAXOUT, MINOUT );

        //		// #ifdef SAVE_SAMPLE
        //		if (which==0)
        //		{
        //			SAVE_ALL_CHANNELS
        //		}
        //		// #endif

        //		/* store to sound buffer */
        //		bufL[i] = lt;
        //		bufR[i] = lt;

        //		advance(OPL);
        //	}

        //}

        //void y8950_set_port_handler(void* chip, OPL_PORTHANDLER_W PortHandler_w, OPL_PORTHANDLER_R PortHandler_r, void* param)
        //{
        //    FM_OPL* OPL = (FM_OPL*)chip;
        //    OPL.porthandler_w = PortHandler_w;
        //    OPL.porthandler_r = PortHandler_r;
        //    OPL.port_param = param;
        //}

        //void y8950_set_keyboard_handler(void* chip, OPL_PORTHANDLER_W KeyboardHandler_w, OPL_PORTHANDLER_R KeyboardHandler_r, void* param)
        //{
        //    FM_OPL* OPL = (FM_OPL*)chip;
        //    OPL.keyboardhandler_w = KeyboardHandler_w;
        //    OPL.keyboardhandler_r = KeyboardHandler_r;
        //    OPL.keyboard_param = param;
        //}

        //#endif

        private void opl_set_mute_mask(FM_OPL chip, int MuteMask)
        {
            FM_OPL opl = (FM_OPL)chip;
            byte CurChn;

            for (CurChn = 0; CurChn < 9; CurChn++)
                opl.P_CH[CurChn].Muted = (byte)((MuteMask >> CurChn) & 0x01);
            for (CurChn = 0; CurChn < 6; CurChn++)
                opl.MuteSpc[CurChn] = (byte)((MuteMask >> (9 + CurChn)) & 0x01);

            return;
        }
        // #endregion

    }
