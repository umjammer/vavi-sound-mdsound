package mdsound;

import java.util.function.BiConsumer;
import java.util.function.Function;

import mdsound.Common.TriConsumer;


public class Y8950 extends Instrument.BaseInstrument {
    @Override
    public String getName() {
        return "Y8950";
    }

    @Override
    public String getShortName() {
        return "Y895";
    }

    @Override
    public void reset(byte chipID) {
        device_reset_y8950(chipID);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipID, int clock) {
        return device_start_y8950(chipID, 3579545);
    }

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        return (int) device_start_y8950(chipID, (int) clockValue);
    }

    @Override
    public void stop(byte chipID) {
        device_stop_y8950(chipID);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        y8950_stream_update(chipID, outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        y8950_w(chipID, 0x00, (byte) adr);
        y8950_w(chipID, 0x01, (byte) data);
        return 0;
    }


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
     *   CHS 1999-01-09  Fixes new Ym3812 emulation interface.
     *   CHS 1998-10-23  Mame streaming Sound chip update
     *   EC  1998        Created Interface
     *
     * NOTES
     *
     ******************************************************************************/


    public class y8950_state {
        //sound_stream * stream;
        //emu_timer *  timer[2];
        public FM_OPL chip;
        //final y8950_interface *intf;
        //final DeviceConfig *device;
    }

    private final byte CHIP_SAMPLING_MODE = 0;
    //private int CHIP_SAMPLE_RATE;
    private static final int MAX_CHIPS = 0x02;
    private y8950_state[] y8950Data = new y8950_state[] {new y8950_state(), new y8950_state()};//[MAX_CHIPS];

        /*INLINE y8950_state *get_safe_token(final DeviceConfig *device)
        {
            assert(device != NULL);
            assert(device.token != NULL);
            assert(device.type == Sound);
            assert(sound_get_type(device) == SOUND_Y8950);
            return (y8950_state *)device.token;
        }*/


    private void IRQHandler(y8950_state param, int irq) {
        y8950_state info = param;
        //if (info.intf.handler) (info.intf.handler)(info.device, irq ? ASSERT_LINE : CLEAR_LINE);
        //if (info.intf.handler) (info.intf.handler)(irq ? ASSERT_LINE : CLEAR_LINE);
    }

    /*static TIMER_CALLBACK( timer_callback_0 )
        {
            y8950_state *info = (y8950_state *)ptr;
            y8950_timer_over(info.chip,0);
        }
        static TIMER_CALLBACK( timer_callback_1 )
        {
            y8950_state *info = (y8950_state *)ptr;
            y8950_timer_over(info.chip,1);
        }*/
    //static void TimerHandler(void *param,int c,attotime period)
    private void TimerHandler(y8950_state param, int c, int period) {
        y8950_state info = param;
        //if( attotime_compare(period, attotime_zero) == 0 )
        if (period == 0) {   /* Reset FM Timer */
            //timer_enable(info.timer[c], 0);
        } else {   /* Start FM Timer */
            //timer_adjust_oneshot(info.timer[c], period, 0);
        }
    }


    private byte Y8950PortHandler_r(y8950_state param) {
        y8950_state info = param;
            /*if (info.intf.portread)
                return info.intf.portread(0);*/
        return 0;
    }

    private void Y8950PortHandler_w(y8950_state param, byte data) {
        y8950_state info = param;
            /*if (info.intf.portwrite)
                info.intf.portwrite(0,data);*/
    }

    private byte Y8950KeyboardHandler_r(y8950_state param) {
        y8950_state info = param;
            /*if (info.intf.keyboardread)
                return info.intf.keyboardread(0);*/
        return 0;
    }

    private void Y8950KeyboardHandler_w(y8950_state param, byte data) {
        y8950_state info = param;
            /*if (info.intf.keyboardwrite)
                info.intf.keyboardwrite(0,data);*/
    }

    //static STREAM_UPDATE( y8950_stream_update )
    public void y8950_stream_update(byte chipID, int[][] outputs, int samples) {
        //y8950_state *info = (y8950_state *)param;
        y8950_state info = y8950Data[chipID];
        y8950_update_one(info.chip, outputs, samples);
    }

    private int[][] dummyBuf = new int[][] {null, null};

    private void _stream_update(y8950_state param, int interval) {
        y8950_state info = param;
        //stream_update(info.stream);
        y8950_update_one(info.chip, dummyBuf, 0);
    }


    //static DEVICE_START( Y8950 )
    public int device_start_y8950(byte chipID, int clock) {
        //static final y8950_interface dummy = { 0 };
        //y8950_state *info = get_safe_token(device);
        y8950_state info;
        int rate;

        if (chipID >= MAX_CHIPS)
            return 0;

        info = y8950Data[chipID];
        rate = clock / 72;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;
        //info.intf = device.static_config ? (final y8950_interface *)device.static_config : &dummy;
        //info.intf = &dummy;
        //info.device = device;

        /* stream system initialize */
        info.chip = y8950_init(clock, rate);
        //assert_always(info.chip != NULL, "Error creating Y8950 chip");

        /* ADPCM ROM data */
        //y8950_set_delta_t_memory(info.chip, device.region, device.regionbytes);
        y8950_set_delta_t_memory(info.chip, null, 0x00);

        //info.stream = stream_create(device,0,1,rate,info,y8950_stream_update);

        /* port and keyboard handler */
        y8950_set_port_handler(info.chip, this::Y8950PortHandler_w, this::Y8950PortHandler_r, info);
        y8950_set_keyboard_handler(info.chip, this::Y8950KeyboardHandler_w, this::Y8950KeyboardHandler_r, info);

        /* Y8950 setup */
        y8950_set_timer_handler(info.chip, this::TimerHandler, info);
        y8950_set_irq_handler(info.chip, this::IRQHandler, info);
        y8950_set_update_handler(info.chip, this::_stream_update, info);

        //info.timer[0] = timer_alloc(device.machine, timer_callback_0, info);
        //info.timer[1] = timer_alloc(device.machine, timer_callback_1, info);

        return rate;
    }

    //static DEVICE_STOP( Y8950 )
    public void device_stop_y8950(byte chipID) {
        //y8950_state *info = get_safe_token(device);
        y8950_state info = y8950Data[chipID];
        y8950_shutdown(info.chip);
    }

    //static DEVICE_RESET( Y8950 )
    public void device_reset_y8950(byte chipID) {
        //y8950_state *info = get_safe_token(device);
        y8950_state info = y8950Data[chipID];
        y8950_reset_chip(info.chip);
    }


    //READ8_DEVICE_HANDLER( y8950_r )
    public byte y8950_r(byte chipID, int offset) {
        //y8950_state *info = get_safe_token(device);
        y8950_state info = y8950Data[chipID];
        return y8950_read(info.chip, offset & 1);
    }

    //WRITE8_DEVICE_HANDLER( y8950_w )
    public void y8950_w(byte chipID, int offset, byte data) {
        //y8950_state *info = get_safe_token(device);
        y8950_state info = y8950Data[chipID];
        y8950_write(info.chip, offset & 1, data);
    }

    //READ8_DEVICE_HANDLER( y8950_status_port_r )
    public byte y8950_status_port_r(byte chipID, int offset) {
        return y8950_r(chipID, 0);
    }

    //READ8_DEVICE_HANDLER( y8950_read_port_r )
    public byte y8950_read_port_r(byte chipID, int offset) {
        return y8950_r(chipID, 1);
    }

    //WRITE8_DEVICE_HANDLER( y8950_control_port_w )
    public void y8950_control_port_w(byte chipID, int offset, byte data) {
        y8950_w(chipID, 0, data);
    }

    //WRITE8_DEVICE_HANDLER( y8950_write_port_w )
    public void y8950_write_port_w(byte chipID, int offset, byte data) {
        y8950_w(chipID, 1, data);
    }


    public void y8950_write_data_pcmrom(byte chipID, int romSize, int dataStart, int dataLength, byte[] romData) {
        y8950_state info = y8950Data[chipID];
        y8950_write_pcmrom(info.chip, romSize, dataStart, dataLength, romData);
    }

    public void y8950_write_data_pcmrom(byte chipID, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        y8950_state info = y8950Data[chipID];
        y8950_write_pcmrom(info.chip, romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    public void y8950_set_mute_mask(byte chipID, int muteMask) {
        y8950_state info = y8950Data[chipID];
        opl_set_mute_mask(info.chip, muteMask);
    }


    /**************************************************************************
     * Generic get_info
     **************************************************************************/

        /*DEVICE_GET_INFO( Y8950 )
        {
            switch (state)
            {
                // --- the following bits of info are returned as 64-bit signed integers ---
                case DEVINFO_INT_TOKEN_BYTES:     info.i = sizeof(y8950_state);    break;

                // --- the following bits of info are returned as pointers to data or functions ---
                case DEVINFO_FCT_START:       info.start = DEVICE_START_NAME( Y8950 );    break;
                case DEVINFO_FCT_STOP:       info.stop = DEVICE_STOP_NAME( Y8950 );    break;
                case DEVINFO_FCT_RESET:       info.reset = DEVICE_RESET_NAME( Y8950 );    break;

                // --- the following bits of info are returned as NULL-terminated strings ---
                case DEVINFO_STR_NAME:       strcpy(info.s, "Y8950");       break;
                case DEVINFO_STR_FAMILY:     strcpy(info.s, "Yamaha FM");      break;
                case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");        break;
                case DEVINFO_STR_SOURCE_FILE:      strcpy(info.s, __FILE__);       break;
                case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
            }
        }*/


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
    private static final int OPL_SAMPLE_BITS = 16;

    /* compiler dependence */
        /*#ifndef __OSDCOMM_H__
        // #define __OSDCOMM_H__
        typedef unsigned char byte;   // unsigned  8bit
        typedef unsigned short int;  // unsigned 16bit
        typedef unsigned int (int)32;  // unsigned 32bit
        typedef signed char  INT8;    // signed  8bit
        typedef signed short INT16;   // signed 16bit
        typedef signed int  int;   // signed 32bit
        // #endif*/ /* __OSDCOMM_H__ */

    //private stream_sample_t OPLSAMPLE;
        /*
        // #if (OPL_SAMPLE_BITS==16)
        typedef INT16 OPLSAMPLE;
        // #endif
        // #if (OPL_SAMPLE_BITS==8)
        typedef INT8 OPLSAMPLE;
        // #endif
        */

    //typedef void (*OPL_TIMERHANDLER)(void *param,int timer,attotime period);
    //public delegate void OPL_TIMERHANDLER(Object param, int timer, int period);
    //public delegate void OPL_IRQHANDLER(Object param, int irq);
    //public delegate void OPL_UPDATEHANDLER(Object param/*,int min_interval_us*/);
    //public delegate void OPL_PORTHANDLER_W(Object param, byte data);
    //public delegate byte OPL_PORTHANDLER_R (Object param);
    public interface OPL_TIMERHANDLER extends TriConsumer<y8950_state, Integer, Integer> {
    }

    public interface OPL_IRQHANDLER extends BiConsumer<y8950_state, Integer> {
    }

    public interface OPL_UPDATEHANDLER extends BiConsumer<y8950_state, Integer> {
    }

    public interface OPL_PORTHANDLER_W extends BiConsumer<y8950_state, Byte> {
    }

    public interface OPL_PORTHANDLER_R extends Function<y8950_state, Byte> {
    }

    //#if BUILD_YM3812

    //void* ym3812_init(int clock, int rate);
    //    void ym3812_shutdown(void* chip);
    //    void ym3812_reset_chip(void* chip);
    //    int ym3812_write(void* chip, int a, int v);
    //    unsigned char ym3812_read(void* chip, int a);
    //    int ym3812_timer_over(void* chip, int c);
    //    void ym3812_update_one(void* chip, OPLSAMPLE** buffer, int length);

    //    void ym3812_set_timer_handler(void* chip, OPL_TIMERHANDLER TimerHandler, void* param);
    //    void ym3812_set_irq_handler(void* chip, OPL_IRQHANDLER IRQHandler, void* param);
    //    void ym3812_set_update_handler(void* chip, OPL_UPDATEHANDLER UpdateHandler, void* param);

    //#endif /* BUILD_YM3812 */


    //#if BUILD_YM3526

    /*
     ** Initialize YM3526 emulator(s).
     **
     ** 'num' is the number of virtual YM3526's to allocate
     ** 'clock' is the chip clock in Hz
     ** 'rate' is sampling rate
     */
    //void* ym3526_init(int clock, int rate);
    ///*
    //** Generate samples for one of the YM3526's
    //**
    //** 'which' is the virtual YM3526 number
    //** '*buffer' is the output buffer pointer
    //** 'length' is the number of samples that should be generated
    //*/

    private static final int YM_DELTAT_SHIFT = (16);

    private static final int YM_DELTAT_EMULATION_MODE_NORMAL = 0;
    private static final int YM_DELTAT_EMULATION_MODE_YM2610 = 1;

    public interface STATUS_CHANGE_HANDLER extends BiConsumer<FM_OPL, Byte> {
    }
    //typedef void (* STATUS_CHANGE_HANDLER) (void* chip, (int)8 status_bits);


    /*
     **
     ** File: ymdeltat.c
     **
     ** YAMAHA DELTA-T adpcm Sound emulation subroutine
     ** used by fmopl.c (Y8950) and Fm.c (YM2608 and YM2610/B)
     **
     ** Base program is YM2610 emulator by Hiromitsu Shioya.
     ** Written by Tatsuyuki Satoh
     ** Improvements by Jarek Burczynski (bujar at mame dot net)
     **
     **
     ** History:
     **
     ** 03-08-2003 Jarek Burczynski:
     **  - fixed BRDY flag implementation.
     **
     ** 24-07-2003 Jarek Burczynski, Frits Hilderink:
     **  - fixed delault value for control2 in YM_DELTAT_ADPCM_Reset
     **
     ** 22-07-2003 Jarek Burczynski, Frits Hilderink:
     **  - fixed external memory support
     **
     ** 15-06-2003 Jarek Burczynski:
     **  - implemented CPU . AUDIO ADPCM synthesis (via writes to the ADPCM data reg $08)
     **  - implemented support for the Limit address register
     **  - supported two bits from the control register 2 ($01): RAM TYPE (x1 bit/x8 bit), ROM/RAM
     **  - implemented external memory access (read/write) via the ADPCM data reg reads/writes
     **    Thanks go to Frits Hilderink for the example code.
     **
     ** 14-06-2003 Jarek Burczynski:
     **  - various fixes to enable proper support for status register flags: BSRDY, PCM BSY, ZERO
     **  - modified EOS handling
     **
     ** 05-04-2003 Jarek Burczynski:
     **  - implemented partial support for external/processor memory on sample replay
     **
     ** 01-12-2002 Jarek Burczynski:
     **  - fixed first missing Sound in gigandes thanks to previous fix (interpolator) by ElSemi
     **  - renamed/removed some YM_DELTAT struct fields
     **
     ** 28-12-2001 Acho A. Tang
     **  - added EOS status report on ADPCM playback.
     **
     ** 05-08-2001 Jarek Burczynski:
     **  - now_step is initialized with 0 at the start of play.
     **
     ** 12-06-2001 Jarek Burczynski:
     **  - corrected end of sample bug in YM_DELTAT_ADPCM_CALC.
     **    Checked on real YM2610 chip - address register is 24 bits wide.
     **    Thanks go to Stefan Jokisch (stefan.jokisch@gmx.de) for tracking down the problem.
     **
     ** TO DO:
     **      Check size of the address register on the other chips....
     **
     ** Version 0.72
     **
     ** Sound chips that have this unit:
     ** YM2608   OPNA
     ** YM2610/B OPNB
     ** Y8950    MSX AUDIO
     **
     */
    /* DELTA-T (adpcm type B) struct */
    public class YM_DELTAT        //deltat_adpcm_state
    {     /* AT: rearranged and tigntened structure */
        public byte[] memory;
        //public int[] output_pointer;/* pointer of output pointers   */
        //public int[] pan;         /* pan : &output_pointer[pan]   */
        public int[] output_pointer;/* pointer of output pointers   */
        public int ptrOutput_pointer;/* pointer of output pointers   */
        public int ptrPan;         /* pan : &output_pointer[pan]   */
        public double freqbase;
        //#if 0
        //double write_time;  /* Y8950: 10 cycles of main clock; YM2608: 20 cycles of main clock */
        //double read_time;  /* Y8950: 8 cycles of main clock;  YM2608: 18 cycles of main clock */
        //#endif
        public int memory_size;
        public int memory_mask;
        public int output_range;
        public int now_addr;        /* current address      */
        public int now_step;        /* currect step         */
        public int step;            /* step                 */
        public int start;           /* start address        */
        public int limit;           /* limit address        */
        public int end;         /* end address          */
        public int delta;           /* delta scale          */
        public int volume;           /* current volume       */
        public int acc;          /* shift Measurement value*/
        public int adpcmd;           /* next Forecast        */
        public int adpcml;           /* current value        */
        public int prev_acc;     /* leveling value       */
        public byte now_data;     /* current rom data     */
        public byte CPU_data;     /* current data from reg 08 */
        public byte portstate;        /* port status          */
        public byte control2;     /* control reg: SAMPLE, DA/AD, RAM TYPE (x8bit / x1bit), ROM/RAM */
        public byte portshift;        /* address bits shift-left:
         ** 8 for YM2610,
         ** 5 for Y8950 and YM2608 */

        public byte dramPortShift;    /* address bits shift-right:
         ** 0 for ROM and x8bit DRAMs,
         ** 3 for x1 DRAMs */

        public byte memread;      /* needed for reading/writing external memory */

        /* handlers and parameters for the status flags support */
        public STATUS_CHANGE_HANDLER status_set_handler;
        public STATUS_CHANGE_HANDLER status_reset_handler;

        /* note that different chips have these flags on different
         ** bits of the status register
         */
        public FM_OPL status_change_which_chip; /* this chip id */
        public byte status_change_EOS_bit;        /* 1 on End Of Sample (record/playback/cycle time of AD/DA converting has passed)*/
        public byte status_change_BRDY_bit;       /* 1 after recording 2 datas (2x4bits) or after reading/writing 1 data */
        public byte status_change_ZERO_bit;       /* 1 if silence lasts for more than 290 miliseconds on ADPCM recording */

        /* neither Y8950 nor YM2608 can generate IRQ when PCMBSY bit changes, so instead of above,
         ** the statusflag gets ORed with PCM_BSY (below) (on each read of statusflag of Y8950 and YM2608)
         */
        public byte PCM_BSY;      /* 1 when ADPCM is playing; Y8950/YM2608 only */

        public byte[] reg = new byte[16];      /* adpcm registers      */
        public byte emulation_mode;   /* which chip we're emulating */
    }

    /*void YM_DELTAT_BRDY_callback(YM_DELTAT *DELTAT);*/

    //public byte YM_DELTAT_ADPCM_Read(YM_DELTAT DELTAT) { return 0; }
    //public void YM_DELTAT_ADPCM_Write(YM_DELTAT DELTAT, int r, int v) { }
    //public void YM_DELTAT_ADPCM_Reset(YM_DELTAT DELTAT, int pan, int emulation_mode) { }
    //public void YM_DELTAT_ADPCM_CALC(YM_DELTAT DELTAT) { }

        /*void YM_DELTAT_postload(YM_DELTAT *DELTAT,(int)8 *regs);
        //void YM_DELTAT_savestate(final DeviceConfig *device,YM_DELTAT *DELTAT);
        void YM_DELTAT_savestate(YM_DELTAT *DELTAT);*/

    //public void YM_DELTAT_calc_mem_mask(YM_DELTAT DELTAT) { }







    private static final int YM_DELTAT_DELTA_MAX = 24576;
    private static final int YM_DELTAT_DELTA_MIN = 127;
    private static final int YM_DELTAT_DELTA_DEF = 127;
    private static final int YM_DELTAT_DECODE_RANGE = 32768;
    private static final int YM_DELTAT_DECODE_MIN = -YM_DELTAT_DECODE_RANGE;
    private static final int YM_DELTAT_DECODE_MAX = YM_DELTAT_DECODE_RANGE - 1;


    /* Forecast to next Forecast (rate = *8) */
    /* 1/8 , 3/8 , 5/8 , 7/8 , 9/8 , 11/8 , 13/8 , 15/8 */
    private static final int[] ym_deltat_decode_tableB1 = new int[] {
            1, 3, 5, 7, 9, 11, 13, 15,
            -1, -3, -5, -7, -9, -11, -13, -15,
    };
    /* delta to next delta (rate= *64) */
    /* 0.9 , 0.9 , 0.9 , 0.9 , 1.2 , 1.6 , 2.0 , 2.4 */
    private static final int[] ym_deltat_decode_tableB2 = new int[] {
            57, 57, 57, 57, 77, 102, 128, 153,
            57, 57, 57, 57, 77, 102, 128, 153
    };

    //#if 0
    //void YM_DELTAT_BRDY_callback(YM_DELTAT *DELTAT)
    //{
    // logerror("BRDY_callback reached (flag set) !\n");

    // /* set BRDY bit in status register */
    // if(DELTAT.status_set_handler)
    //  if(DELTAT.status_change_BRDY_bit)
    //   (DELTAT.status_set_handler)(DELTAT.status_change_which_chip, DELTAT.status_change_BRDY_bit);
    //}
    //#endif

    public byte YM_DELTAT_ADPCM_Read(YM_DELTAT DELTAT) {
        byte v = 0;

        /* external memory read */
        if ((DELTAT.portstate & 0xe0) == 0x20) {
            /* two dummy reads */
            if (DELTAT.memread != 0) {
                DELTAT.now_addr = DELTAT.start << 1;
                DELTAT.memread--;
                return 0;
            }


            if (DELTAT.now_addr != (DELTAT.end << 1)) {
                v = DELTAT.memory[DELTAT.now_addr >> 1];

                /*logerror("YM Delta-T memory read  $%08x, v=$%02x\n", DELTAT.now_addr >> 1, v);*/

                DELTAT.now_addr += 2; /* two nibbles at a time */

                /* reset BRDY bit in status register, which means we are reading the memory now */
                if (DELTAT.status_reset_handler != null)
                    if (DELTAT.status_change_BRDY_bit != 0)
                        DELTAT.status_reset_handler.accept(DELTAT.status_change_which_chip, DELTAT.status_change_BRDY_bit);

                /* setup a timer that will callback us in 10 master clock cycles for Y8950
                 * in the callback set the BRDY flag to 1 , which means we have another data ready.
                 * For now, we don't really do this; we simply reset and set the flag in zero time, so that the IRQ will work.
                 */
                /* set BRDY bit in status register */
                if (DELTAT.status_set_handler != null)
                    if (DELTAT.status_change_BRDY_bit != 0)
                        DELTAT.status_set_handler.accept(DELTAT.status_change_which_chip, DELTAT.status_change_BRDY_bit);
            } else {
                /* set EOS bit in status register */
                if (DELTAT.status_set_handler != null)
                    if (DELTAT.status_change_EOS_bit != 0)
                        DELTAT.status_set_handler.accept(DELTAT.status_change_which_chip, DELTAT.status_change_EOS_bit);
            }
        }

        return v;
    }


    /* 0-DRAM x1, 1-ROM, 2-DRAM x8, 3-ROM (3 is bad setting - not allowed by the manual) */
    private static final byte[] dram_rightshift = new byte[] {3, 0, 0, 0};

    /* DELTA-T ADPCM write register */
    public void YM_DELTAT_ADPCM_Write(YM_DELTAT deltaT, int r, int v) {
        if (r >= 0x10) return;
        deltaT.reg[r] = (byte) v; /* stock data */

        switch (r) {
        case 0x00:
                    /*
                    START:
                        Accessing *external* memory is started when START bit (D7) is set to "1", so
                        you must set all conditions needed for recording/playback before starting.
                        If you access *CPU-managed* memory, recording/playback starts after
                        read/write of ADPCM data register $08.

                    REC:
                        0 = ADPCM synthesis (playback)
                        1 = ADPCM analysis (record)

                    MEMDATA:
                        0 = processor (*CPU-managed*) memory (means: using register $08)
                        1 = external memory (using start/end/limit registers to access memory: RAM or ROM)


                    SPOFF:
                        controls output pin that should disable the speaker while ADPCM analysis

                    RESET and REPEAT only work with external memory.


                    some examples:
                    value:   START, REC, MEMDAT, REPEAT, SPOFF, x,x,RESET   meaning:
                      C8     1      1    0       0       1      0 0 0       Analysis (recording) from AUDIO to CPU (to reg $08), sample rate in PRESCALER register
                      E8     1      1    1       0       1      0 0 0       Analysis (recording) from AUDIO to EXT.MEMORY,       sample rate in PRESCALER register
                      80     1      0    0       0       0      0 0 0       Synthesis (playing) from CPU (from reg $08) to AUDIO,sample rate in DELTA-N register
                      a0     1      0    1       0       0      0 0 0       Synthesis (playing) from EXT.MEMORY to AUDIO,        sample rate in DELTA-N register

                      60     0      1    1       0       0      0 0 0       External memory write via ADPCM data register $08
                      20     0      0    1       0       0      0 0 0       External memory read via ADPCM data register $08

                    */
            /* handle emulation mode */
            if (deltaT.emulation_mode == YM_DELTAT_EMULATION_MODE_YM2610) {
                v |= 0x20;      /*  YM2610 always uses external memory and doesn't even have memory flag bit. */
            }

            deltaT.portstate = (byte) (v & (0x80 | 0x40 | 0x20 | 0x10 | 0x01)); /* start, rec, memory mode, repeat flag copy, reset(bit0) */

            if ((deltaT.portstate & 0x80) != 0)/* START,REC,MEMDATA,REPEAT,SPOFF,--,--,RESET */ {
                /* set PCM BUSY bit */
                deltaT.PCM_BSY = 1;

                /* start ADPCM */
                deltaT.now_step = 0;
                deltaT.acc = 0;
                deltaT.prev_acc = 0;
                deltaT.adpcml = 0;
                deltaT.adpcmd = YM_DELTAT_DELTA_DEF;
                deltaT.now_data = 0;
                //if (deltaT.start > deltaT.end)
                //logerror("DeltaT-Warning: Start: %06X, End: %06X\n", deltaT.start, deltaT.end);
            }

            if ((deltaT.portstate & 0x20) != 0) /* do we access external memory? */ {
                deltaT.now_addr = deltaT.start << 1;
                deltaT.memread = 2;    /* two dummy reads needed before accesing external memory via register $08*/

                /* if yes, then let's check if ADPCM memory is mapped and big enough */
                if (deltaT.memory == null) {
                    //# ifdef _DEBUG
                    //logerror("YM Delta-T ADPCM rom not mapped\n");
                    //#endif
                    deltaT.portstate = 0x00;
                    deltaT.PCM_BSY = 0;
                } else {
                    if (deltaT.end >= deltaT.memory_size) /* Check End in Range */ {
                        //# ifdef _DEBUG
                        //logerror("YM Delta-T ADPCM end out of range: $%08x\n", deltaT.end);
                        //#endif
                        deltaT.end = deltaT.memory_size - 1;
                    }
                    if (deltaT.start >= deltaT.memory_size)   /* Check Start in Range */ {
                        //# ifdef _DEBUG
                        //logerror("YM Delta-T ADPCM start out of range: $%08x\n", deltaT.start);
                        //#endif
                        deltaT.portstate = 0x00;
                        deltaT.PCM_BSY = 0;
                    }
                }
            } else    /* we access CPU memory (ADPCM data register $08) so we only reset now_addr here */ {
                deltaT.now_addr = 0;
            }

            if ((deltaT.portstate & 0x01) != 0) {
                deltaT.portstate = 0x00;

                /* clear PCM BUSY bit (in status register) */
                deltaT.PCM_BSY = 0;

                /* set BRDY flag */
                if (deltaT.status_set_handler != null)
                    if (deltaT.status_change_BRDY_bit != 0)
                        deltaT.status_set_handler.accept(deltaT.status_change_which_chip, deltaT.status_change_BRDY_bit);
            }
            break;
        case 0x01:  /* L,R,-,-,SAMPLE,DA/AD,RAMTYPE,ROM */
            /* handle emulation mode */
            if (deltaT.emulation_mode == YM_DELTAT_EMULATION_MODE_YM2610) {
                v |= 0x01;      /*  YM2610 always uses ROM as an external memory and doesn't have ROM/RAM memory flag bit. */
            }

            //deltaT.pan = deltaT.output_pointer[(v >> 6) & 0x03];
            deltaT.ptrPan = ((v >> 6) & 0x03);
            if ((deltaT.control2 & 3) != (v & 3)) {
                /*0-DRAM x1, 1-ROM, 2-DRAM x8, 3-ROM (3 is bad setting - not allowed by the manual) */
                if (deltaT.dramPortShift != dram_rightshift[v & 3]) {
                    deltaT.dramPortShift = dram_rightshift[v & 3];

                            /* final shift value depends on chip type and memory type selected:
                                    8 for YM2610 (ROM only),
                                    5 for ROM for Y8950 and YM2608,
                                    5 for x8bit DRAMs for Y8950 and YM2608,
                                    2 for x1bit DRAMs for Y8950 and YM2608.
                            */

                    /* refresh addresses */
                    deltaT.start = (int) ((deltaT.reg[0x3] * 0x0100 | deltaT.reg[0x2]) << (deltaT.portshift - deltaT.dramPortShift));
                    deltaT.end = (int) ((deltaT.reg[0x5] * 0x0100 | deltaT.reg[0x4]) << (deltaT.portshift - deltaT.dramPortShift));
                    deltaT.end += (int) ((1 << (deltaT.portshift - deltaT.dramPortShift)) - 1);
                    deltaT.limit = (int) ((deltaT.reg[0xd] * 0x0100 | deltaT.reg[0xc]) << (deltaT.portshift - deltaT.dramPortShift));
                }
            }
            deltaT.control2 = (byte) v;
            break;
        case 0x02:  /* Start Address L */
        case 0x03:  /* Start Address H */
            deltaT.start = (int) ((deltaT.reg[0x3] * 0x0100 | deltaT.reg[0x2]) << (deltaT.portshift - deltaT.dramPortShift));
            /*logerror("deltaT start: 02=%2x 03=%2x addr=%8x\n",deltaT.reg[0x2], deltaT.reg[0x3],deltaT.start );*/
            break;
        case 0x04:  /* Stop Address L */
        case 0x05:  /* Stop Address H */
            deltaT.end = (int) ((deltaT.reg[0x5] * 0x0100 | deltaT.reg[0x4]) << (deltaT.portshift - deltaT.dramPortShift));
            deltaT.end += (int) ((1 << (deltaT.portshift - deltaT.dramPortShift)) - 1);
            /*logerror("deltaT end  : 04=%2x 05=%2x addr=%8x\n",deltaT.reg[0x4], deltaT.reg[0x5],deltaT.end   );*/
            break;
        case 0x06:  /* Prescale L (ADPCM and Record frq) */
        case 0x07:  /* Prescale H */
            break;
        case 0x08:  /* ADPCM data */

                    /*
                    some examples:
                    value:   START, REC, MEMDAT, REPEAT, SPOFF, x,x,RESET   meaning:
                      C8     1      1    0       0       1      0 0 0       Analysis (recording) from AUDIO to CPU (to reg $08), sample rate in PRESCALER register
                      E8     1      1    1       0       1      0 0 0       Analysis (recording) from AUDIO to EXT.MEMORY,       sample rate in PRESCALER register
                      80     1      0    0       0       0      0 0 0       Synthesis (playing) from CPU (from reg $08) to AUDIO,sample rate in DELTA-N register
                      a0     1      0    1       0       0      0 0 0       Synthesis (playing) from EXT.MEMORY to AUDIO,        sample rate in DELTA-N register

                      60     0      1    1       0       0      0 0 0       External memory write via ADPCM data register $08
                      20     0      0    1       0       0      0 0 0       External memory read via ADPCM data register $08

                    */

            /* external memory write */
            if ((deltaT.portstate & 0xe0) == 0x60) {
                if (deltaT.memread != 0) {
                    deltaT.now_addr = deltaT.start << 1;
                    deltaT.memread = 0;
                }

                /*logerror("YM Delta-T memory write $%08x, v=$%02x\n", deltaT.now_addr >> 1, v);*/

                if (deltaT.now_addr != (deltaT.end << 1)) {
                    deltaT.memory[deltaT.now_addr >> 1] = (byte) v;
                    deltaT.now_addr += 2; /* two nibbles at a time */

                    /* reset BRDY bit in status register, which means we are processing the write */
                    if (deltaT.status_reset_handler != null)
                        if (deltaT.status_change_BRDY_bit != 0)
                            deltaT.status_reset_handler.accept(deltaT.status_change_which_chip, deltaT.status_change_BRDY_bit);

                    /* setup a timer that will callback us in 10 master clock cycles for Y8950
                     * in the callback set the BRDY flag to 1 , which means we have written the data.
                     * For now, we don't really do this; we simply reset and set the flag in zero time, so that the IRQ will work.
                     */
                    /* set BRDY bit in status register */
                    if (deltaT.status_set_handler != null)
                        if (deltaT.status_change_BRDY_bit != 0)
                            deltaT.status_set_handler.accept(deltaT.status_change_which_chip, deltaT.status_change_BRDY_bit);

                } else {
                    /* set EOS bit in status register */
                    if (deltaT.status_set_handler != null)
                        if (deltaT.status_change_EOS_bit != 0)
                            deltaT.status_set_handler.accept(deltaT.status_change_which_chip, deltaT.status_change_EOS_bit);
                }

                return;
            }

            /* ADPCM synthesis from CPU */
            if ((deltaT.portstate & 0xe0) == 0x80) {
                deltaT.CPU_data = (byte) v;

                /* Reset BRDY bit in status register, which means we are full of data */
                if (deltaT.status_reset_handler != null)
                    if (deltaT.status_change_BRDY_bit != 0)
                        deltaT.status_reset_handler.accept(deltaT.status_change_which_chip, deltaT.status_change_BRDY_bit);
                return;
            }

            break;
        case 0x09:  /* DELTA-N L (ADPCM Playback Prescaler) */
        case 0x0a:  /* DELTA-N H */
            deltaT.delta = (int) (deltaT.reg[0xa] * 0x0100 | deltaT.reg[0x9]);
            deltaT.step = (int) ((double) (deltaT.delta /* *(1<<(YM_DELTAT_SHIFT-16)) */) * (deltaT.freqbase));
            /*logerror("deltaT deltan:09=%2x 0a=%2x\n",deltaT.reg[0x9], deltaT.reg[0xa]);*/
            break;
        case 0x0b:  /* Output level control (volume, linear) */ {
            int oldvol = deltaT.volume;
            deltaT.volume = (v & 0xff) * (deltaT.output_range / 256) / YM_DELTAT_DECODE_RANGE;
            /*                              v     *     ((1<<16)>>8)        >>  15;
             *                       thus:   v     *     (1<<8)              >>  15;
             *                       thus: output_range must be (1 << (15+8)) at least
             *                               v     *     ((1<<23)>>8)        >>  15;
             *                               v     *     (1<<15)             >>  15;
             */
            /*logerror("deltaT vol = %2x\n",v&0xff);*/
            if (oldvol != 0) {
                deltaT.adpcml = (int) ((double) deltaT.adpcml / (double) oldvol * (double) deltaT.volume);
            }
        }
        break;
        case 0x0c:  /* Limit Address L */
        case 0x0d:  /* Limit Address H */
            deltaT.limit = (int) ((deltaT.reg[0xd] * 0x0100 | deltaT.reg[0xc]) << (deltaT.portshift - deltaT.dramPortShift));
            /*logerror("deltaT limit: 0c=%2x 0d=%2x addr=%8x\n",deltaT.reg[0xc], deltaT.reg[0xd],deltaT.limit );*/
            break;
        }
    }

    public void YM_DELTAT_ADPCM_Reset(YM_DELTAT DELTAT, int pan, int emulation_mode) {
        DELTAT.now_addr = 0;
        DELTAT.now_step = 0;
        DELTAT.step = 0;
        DELTAT.start = 0;
        DELTAT.end = 0;
        DELTAT.limit = 0xffffffff;// ~0; /* this way YM2610 and Y8950 (both of which don't have limit address reg) will still work */
        DELTAT.volume = 0;
        //DELTAT.pan = &DELTAT.output_pointer[pan];
        DELTAT.ptrPan = pan;
        DELTAT.acc = 0;
        DELTAT.prev_acc = 0;
        DELTAT.adpcmd = 127;
        DELTAT.adpcml = 0;
        DELTAT.emulation_mode = (byte) emulation_mode;
        DELTAT.portstate = (byte) ((emulation_mode == YM_DELTAT_EMULATION_MODE_YM2610) ? 0x20 : 0);
        DELTAT.control2 = (byte) ((emulation_mode == YM_DELTAT_EMULATION_MODE_YM2610) ? 0x01 : 0);  /* default setting depends on the emulation mode. MSX demo called "facdemo_4" doesn't setup control2 register at all and still works */
        DELTAT.dramPortShift = dram_rightshift[DELTAT.control2 & 3];

        /* The flag mask register disables the BRDY after the reset, however
         ** as soon as the mask is enabled the flag needs to be set. */

        /* set BRDY bit in status register */
        if (DELTAT.status_set_handler != null)
            if (DELTAT.status_change_BRDY_bit != 0)
                DELTAT.status_set_handler.accept(DELTAT.status_change_which_chip, DELTAT.status_change_BRDY_bit);
    }

        /*void YM_DELTAT_postload(YM_DELTAT *DELTAT,(int)8 *regs)
        {
            int r;

            // to keep adpcml
            DELTAT.volume = 0;
            // update
            for(r=1;r<16;r++)
                YM_DELTAT_ADPCM_Write(DELTAT,r,regs[r]);
            DELTAT.reg[0] = regs[0];

            // current rom data
            if (DELTAT.memory)
                DELTAT.now_data = *(DELTAT.memory + (DELTAT.now_addr>>1) );

        }
        //void YM_DELTAT_savestate(final DeviceConfig *device,YM_DELTAT *DELTAT)
        void YM_DELTAT_savestate(YM_DELTAT *DELTAT)
        {
        // #ifdef __STATE_H__
            state_save_register_device_item(device, 0, DELTAT.portstate);
            state_save_register_device_item(device, 0, DELTAT.now_addr);
            state_save_register_device_item(device, 0, DELTAT.now_step);
            state_save_register_device_item(device, 0, DELTAT.acc);
            state_save_register_device_item(device, 0, DELTAT.prev_acc);
            state_save_register_device_item(device, 0, DELTAT.adpcmd);
            state_save_register_device_item(device, 0, DELTAT.adpcml);
        // #endif
        }*/


    private int YM_DELTAT_Limit(int val, int max, int min) {
        if (val > max) val = max;
        else if (val < min) val = min;

        return val;
    }

    private void YM_DELTAT_synthesis_from_external_memory(YM_DELTAT DELTAT) {
        int step;
        int data;

        DELTAT.now_step += DELTAT.step;
        if (DELTAT.now_step >= (1 << YM_DELTAT_SHIFT)) {
            step = DELTAT.now_step >> YM_DELTAT_SHIFT;
            DELTAT.now_step &= (1 << YM_DELTAT_SHIFT) - 1;
            do {

                if (DELTAT.now_addr == (DELTAT.limit << 1))
                    DELTAT.now_addr = 0;

                if (DELTAT.now_addr == (DELTAT.end << 1)) {   /* 12-06-2001 JB: corrected comparison. Was > instead of == */
                    if ((DELTAT.portstate & 0x10) != 0) {
                        /* repeat start */
                        DELTAT.now_addr = DELTAT.start << 1;
                        DELTAT.acc = 0;
                        DELTAT.adpcmd = YM_DELTAT_DELTA_DEF;
                        DELTAT.prev_acc = 0;
                    } else {
                        /* set EOS bit in status register */
                        if (DELTAT.status_set_handler != null)
                            if (DELTAT.status_change_EOS_bit != 0)
                                DELTAT.status_set_handler.accept(DELTAT.status_change_which_chip, DELTAT.status_change_EOS_bit);

                        /* clear PCM BUSY bit (reflected in status register) */
                        DELTAT.PCM_BSY = 0;

                        DELTAT.portstate = 0;
                        DELTAT.adpcml = 0;
                        DELTAT.prev_acc = 0;
                        return;
                    }
                }

                if ((DELTAT.now_addr & 1) != 0) data = DELTAT.now_data & 0x0f;
                else {
                    DELTAT.now_data = DELTAT.memory[DELTAT.now_addr >> 1];// *(DELTAT.memory + (DELTAT.now_addr >> 1));
                    data = DELTAT.now_data >> 4;
                }

                DELTAT.now_addr++;
                /* 12-06-2001 JB: */
                /* YM2610 address register is 24 bits wide.*/
                /* The "+1" is there because we use 1 bit more for nibble calculations.*/
                /* WARNING: */
                /* Side effect: we should take the size of the mapped ROM into account */
                //DELTAT.now_addr &= ( (1<<(24+1))-1);
                DELTAT.now_addr &= DELTAT.memory_mask;


                /* store accumulator value */
                DELTAT.prev_acc = DELTAT.acc;

                /* Forecast to next Forecast */
                DELTAT.acc += (ym_deltat_decode_tableB1[data] * DELTAT.adpcmd / 8);
                YM_DELTAT_Limit(DELTAT.acc, YM_DELTAT_DECODE_MAX, YM_DELTAT_DECODE_MIN);

                /* delta to next delta */
                DELTAT.adpcmd = (DELTAT.adpcmd * ym_deltat_decode_tableB2[data]) / 64;
                YM_DELTAT_Limit(DELTAT.adpcmd, YM_DELTAT_DELTA_MAX, YM_DELTAT_DELTA_MIN);

                /* ElSemi: Fix interpolator. */
                /*DELTAT.prev_acc = prev_acc + ((DELTAT.acc - prev_acc) / 2 );*/

            } while ((--step) != 0);

        }

        /* ElSemi: Fix interpolator. */
        DELTAT.adpcml = DELTAT.prev_acc * (int) ((1 << YM_DELTAT_SHIFT) - DELTAT.now_step);
        DELTAT.adpcml += (DELTAT.acc * (int) DELTAT.now_step);
        DELTAT.adpcml = (DELTAT.adpcml >> YM_DELTAT_SHIFT) * (int) DELTAT.volume;

        /* output for work of output channels (outd[OPNxxxx])*/
        DELTAT.output_pointer[DELTAT.ptrPan] += DELTAT.adpcml;
        //*(DELTAT.pan) += DELTAT.adpcml;
    }


    private void YM_DELTAT_synthesis_from_CPU_memory(YM_DELTAT DELTAT) {
        int step;
        int data;

        DELTAT.now_step += DELTAT.step;
        if (DELTAT.now_step >= (1 << YM_DELTAT_SHIFT)) {
            step = DELTAT.now_step >> YM_DELTAT_SHIFT;
            DELTAT.now_step &= (1 << YM_DELTAT_SHIFT) - 1;
            do {

                if ((DELTAT.now_addr & 1) != 0) {
                    data = DELTAT.now_data & 0x0f;

                    DELTAT.now_data = DELTAT.CPU_data;

                    /* after we used CPU_data, we set BRDY bit in status register,
                     * which means we are ready to accept another byte of data */
                    if (DELTAT.status_set_handler != null)
                        if (DELTAT.status_change_BRDY_bit != 0)
                            DELTAT.status_set_handler.accept(DELTAT.status_change_which_chip, DELTAT.status_change_BRDY_bit);
                } else {
                    data = DELTAT.now_data >> 4;
                }

                DELTAT.now_addr++;

                /* store accumulator value */
                DELTAT.prev_acc = DELTAT.acc;

                /* Forecast to next Forecast */
                DELTAT.acc += (ym_deltat_decode_tableB1[data] * DELTAT.adpcmd / 8);
                YM_DELTAT_Limit(DELTAT.acc, YM_DELTAT_DECODE_MAX, YM_DELTAT_DECODE_MIN);

                /* delta to next delta */
                DELTAT.adpcmd = (DELTAT.adpcmd * ym_deltat_decode_tableB2[data]) / 64;
                YM_DELTAT_Limit(DELTAT.adpcmd, YM_DELTAT_DELTA_MAX, YM_DELTAT_DELTA_MIN);


            } while ((--step) != 0);

        }

        /* ElSemi: Fix interpolator. */
        DELTAT.adpcml = DELTAT.prev_acc * (int) ((1 << YM_DELTAT_SHIFT) - DELTAT.now_step);
        DELTAT.adpcml += (DELTAT.acc * (int) DELTAT.now_step);
        DELTAT.adpcml = (DELTAT.adpcml >> YM_DELTAT_SHIFT) * (int) DELTAT.volume;

        /* output for work of output channels (outd[OPNxxxx])*/
        DELTAT.output_pointer[DELTAT.ptrPan] += DELTAT.adpcml;
        //*(DELTAT.pan) += DELTAT.adpcml;
    }


    /* ADPCM B (Delta-T control type) */
    public void YM_DELTAT_ADPCM_CALC(YM_DELTAT deltaT) {

            /*
            some examples:
            value:   START, REC, MEMDAT, REPEAT, SPOFF, x,x,RESET   meaning:
              80     1      0    0       0       0      0 0 0       Synthesis (playing) from CPU (from reg $08) to AUDIO,sample rate in DELTA-N register
              a0     1      0    1       0       0      0 0 0       Synthesis (playing) from EXT.MEMORY to AUDIO,        sample rate in DELTA-N register
              C8     1      1    0       0       1      0 0 0       Analysis (recording) from AUDIO to CPU (to reg $08), sample rate in PRESCALER register
              E8     1      1    1       0       1      0 0 0       Analysis (recording) from AUDIO to EXT.MEMORY,       sample rate in PRESCALER register

              60     0      1    1       0       0      0 0 0       External memory write via ADPCM data register $08
              20     0      0    1       0       0      0 0 0       External memory read via ADPCM data register $08

            */

        if ((deltaT.portstate & 0xe0) == 0xa0) {
            YM_DELTAT_synthesis_from_external_memory(deltaT);
            return;
        }

        if ((deltaT.portstate & 0xe0) == 0x80) {
            /* ADPCM synthesis from CPU-managed memory (from reg $08) */
            YM_DELTAT_synthesis_from_CPU_memory(deltaT);    /* change output based on data in ADPCM data reg ($08) */
            return;
        }

        //todo: ADPCM analysis
        //  if ( (deltaT.portstate & 0xe0)==0xc0 )
        //  if ( (deltaT.portstate & 0xe0)==0xe0 )
    }

    public void YM_DELTAT_calc_mem_mask(YM_DELTAT deltaT) {
        int maskSize;

        maskSize = 0x01;
        while (maskSize < deltaT.memory_size)
            maskSize <<= 1;

        deltaT.memory_mask = (maskSize << 1) - 1;  // it's Mask<<1 because of the nibbles
    }


    /* output final shift */
    private static final int FINAL_SH = 0;
    private static final int MAXOUT = 32767;
    private static final int MINOUT = -32768;

    private static final int FREQ_SH = 16;  /* 16.16 fixed point (frequency calculations) */
    private static final int EG_SH = 16;  /* 16.16 fixed point (EG timing)              */
    private static final int LFO_SH = 24;  /*  8.24 fixed point (LFO calculations)       */
    private static final int TIMER_SH = 16;  /* 16.16 fixed point (timers calculations)    */

    private static final int FREQ_MASK = (1 << FREQ_SH) - 1;

    /* envelope output entries */
    private static final int ENV_BITS = 10;
    private static final int ENV_LEN = (1 << ENV_BITS);
    private static final double ENV_STEP = 128.0 / ENV_LEN;

    private static final int MAX_ATT_INDEX = (1 << (ENV_BITS - 1)) - 1;/*511*/
    private static final int MIN_ATT_INDEX = (0);

    /* sinwave entries */
    private static final int SIN_BITS = 10;
    private static final int SIN_LEN = 1 << SIN_BITS;
    private static final int SIN_MASK = SIN_LEN - 1;

    private static final int TL_RES_LEN = 256;/* 8 bits addressing (real chip) */


    /* register number to channel number , slot offset */
    private static final int SLOT1 = 0;
    private static final int SLOT2 = 1;

    /* Envelope Generator phases */

    private static final int EG_ATT = 4;
    private static final int EG_DEC = 3;
    private static final int EG_SUS = 2;
    private static final int EG_REL = 1;
    private static final int EG_OFF = 0;

    private static final int OPL_TYPE_WAVESEL = 0x01; /* waveform select     */
    private static final int OPL_TYPE_ADPCM = 0x02; /* DELTA-T ADPCM unit  */
    private static final int OPL_TYPE_KEYBOARD = 0x04; /* keyboard interface  */
    private static final int OPL_TYPE_IO = 0x08; /* I/O port            */

    /* ---------- Generic interface section ---------- */
    private static final int OPL_TYPE_YM3526 = 0;
    private static final int OPL_TYPE_YM3812 = OPL_TYPE_WAVESEL;
    private static final int OPL_TYPE_Y8950 = OPL_TYPE_ADPCM | OPL_TYPE_KEYBOARD | OPL_TYPE_IO;


    /*
**
** File: fmopl.c - software implementation of FM Sound generator
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
 - corrected Operator output calculations
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
 - funky details (like ignoring output of Operator 1 in BD rhythm Sound when connect == 1)

12-28-2001 Acho A. Tang
 - reflected Delta-T EOS status on Y8950 status port.
 - fixed subscription range of attack/decay tables


    To do:
        add delay before key off in CSM mode (see CSMKeyControll)
        verify volume of the FM part on the Y8950
*/
    public static class OPL_SLOT {

        public int ar;          /* attack rate: AR<<2           */
        public int dr;          /* decay rate:  DR<<2           */
        public int rr;          /* release rate:RR<<2           */
        public byte KSR;      /* key scale rate               */
        public byte ksl;      /* keyscale level               */
        public byte ksr;      /* key scale rate: kcode>>KSR   */
        public byte mul;      /* multiple: mul_tab[ML]        */

        /* Phase Generator */
        public int cnt;     /* frequency counter            */
        public int Incr;        /* frequency counter step       */
        public byte fb;           /* feedback shift value         */
        //public int connect1;    /* slot1 output pointer         */
        public int ptrConnect1;    /* slot1 output pointer         */
        public int[] op1_out = new int[2];   /* slot1 output for feedback    */
        public byte con;      /* connection (algorithm) type  */

        /* Envelope Generator */
        public byte eg_type;  /* percussive/non-percussive mode */
        public byte state;        /* phase type                   */
        public int TL;          /* total level: TL << 2         */
        public int tll;      /* adjusted now TL              */
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

    public static class OPL_CH {

        public OPL_SLOT[] SLOT = new OPL_SLOT[] {new OPL_SLOT(), new OPL_SLOT()};
        /* phase generator state */
        public int block_fnum;  /* block+fnum                   */
        public int fc;          /* Freq. Increment base         */
        public int ksl_base;    /* KeyScaleLevel Base step      */
        public byte kcode;        /* key code (for key scaling)   */
        public byte Muted;
    }

    /* OPL state */
    //private FM_OPL fm_opl_f;
    public class FM_OPL {
        /* FM channel slots */
        public OPL_CH[] P_CH = new OPL_CH[] {
                new OPL_CH(), new OPL_CH(), new OPL_CH(), new OPL_CH(), new OPL_CH(),
                new OPL_CH(), new OPL_CH(), new OPL_CH(), new OPL_CH()
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
        /* Delta-T ADPCM unit (Y8950) */

        public YM_DELTAT deltaT = new YM_DELTAT();

        /* Keyboard and I/O ports interface */
        public byte portDirection;
        public byte portLatch;
        public OPL_PORTHANDLER_R porthandler_r;
        public OPL_PORTHANDLER_W porthandler_w;
        //public Object port_param;
        public y8950_state port_param;
        public OPL_PORTHANDLER_R keyboardhandler_r;
        public OPL_PORTHANDLER_W keyboardhandler_w;
        //public Object keyboard_param;
        public y8950_state keyboard_param;
        //#endif

        /* external event callback handlers */
        public OPL_TIMERHANDLER timer_handler; /* TIMER handler                */
        public Object TimerParam;                   /* TIMER parameter              */
        public OPL_IRQHANDLER irqHandler;  /* IRQ handler                  */
        //public Object IRQParam;                 /* IRQ parameter                */
        public y8950_state irqParam;                 /* IRQ parameter                */
        public OPL_UPDATEHANDLER updateHandler;/* stream update handler        */
        //public Object UpdateParam;              /* stream update parameter      */
        public y8950_state updateParam;              /* stream update parameter      */

        public byte type;                     /* chip type                    */
        public byte address;                  /* address register             */
        public byte status;                   /* status flag                  */
        public byte statusmask;               /* status mask                  */
        public byte mode;                     /* Reg.08 : CSM,notesel,etc.    */

        public int clock;                   /* master clock  (Hz)           */
        public int rate;                    /* sampling rate (Hz)           */
        public double freqbase;                /* frequency base               */
        //attotime TimerBase;   /* Timer base time (==sampling time)*/

        public int phase_modulation;    /* phase modulation input (SLOT 2) */
        public int[] output = new int[1];
        //#if BUILD_Y8950
        public int[] output_deltat = new int[4];     /* for Y8950 DELTA-T, chip is mono, that 4 here is just for safety */
        //#endif
    }


    /* mapping of register number (offset) to slot number used by the emulator */
    private static final int[] slot_array = new int[] {
                    0, 2, 4, 1, 3, 5, -1, -1,
                    6, 8, 10, 7, 9, 11, -1, -1,
                    12, 14, 16, 13, 15, 17, -1, -1,
                    -1, -1, -1, -1, -1, -1, -1, -1
            };

    /* key scale level */
    /* table is 3dB/octave , DV converts this into 6dB/octave */
    /* 0.1875 is bit 0 weight of the envelope counter (volume) expressed in the 'decibel' scale */
    private static final double DV = (0.1875 / 2.0);
    private static final int[] ksl_tab = new int[]            {
                    /* OCT 0 */
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                    /* OCT 1 */
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                    (int) (0.000 / DV), (int) (0.750 / DV), (int) (1.125 / DV), (int) (1.500 / DV),
                    (int) (1.875 / DV), (int) (2.250 / DV), (int) (2.625 / DV), (int) (3.000 / DV),
                    /* OCT 2 */
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                    (int) (0.000 / DV), (int) (1.125 / DV), (int) (1.875 / DV), (int) (2.625 / DV),
                    (int) (3.000 / DV), (int) (3.750 / DV), (int) (4.125 / DV), (int) (4.500 / DV),
                    (int) (4.875 / DV), (int) (5.250 / DV), (int) (5.625 / DV), (int) (6.000 / DV),
                    /* OCT 3 */
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (1.875 / DV),
                    (int) (3.000 / DV), (int) (4.125 / DV), (int) (4.875 / DV), (int) (5.625 / DV),
                    (int) (6.000 / DV), (int) (6.750 / DV), (int) (7.125 / DV), (int) (7.500 / DV),
                    (int) (7.875 / DV), (int) (8.250 / DV), (int) (8.625 / DV), (int) (9.000 / DV),
                    /* OCT 4 */
                    (int) (0.000 / DV), (int) (0.000 / DV), (int) (3.000 / DV), (int) (4.875 / DV),
                    (int) (6.000 / DV), (int) (7.125 / DV), (int) (7.875 / DV), (int) (8.625 / DV),
                    (int) (9.000 / DV), (int) (9.750 / DV), (int) (10.125 / DV), (int) (10.500 / DV),
                    (int) (10.875 / DV), (int) (11.250 / DV), (int) (11.625 / DV), (int) (12.000 / DV),
                    /* OCT 5 */
                    (int) (0.000 / DV), (int) (3.000 / DV), (int) (6.000 / DV), (int) (7.875 / DV),
                    (int) (9.000 / DV), (int) (10.125 / DV), (int) (10.875 / DV), (int) (11.625 / DV),
                    (int) (12.000 / DV), (int) (12.750 / DV), (int) (13.125 / DV), (int) (13.500 / DV),
                    (int) (13.875 / DV), (int) (14.250 / DV), (int) (14.625 / DV), (int) (15.000 / DV),
                    /* OCT 6 */
                    (int) (0.000 / DV), (int) (6.000 / DV), (int) (9.000 / DV), (int) (10.875 / DV),
                    (int) (12.000 / DV), (int) (13.125 / DV), (int) (13.875 / DV), (int) (14.625 / DV),
                    (int) (15.000 / DV), (int) (15.750 / DV), (int) (16.125 / DV), (int) (16.500 / DV),
                    (int) (16.875 / DV), (int) (17.250 / DV), (int) (17.625 / DV), (int) (18.000 / DV),
                    /* OCT 7 */
                    (int) (0.000 / DV), (int) (9.000 / DV), (int) (12.000 / DV), (int) (13.875 / DV),
                    (int) (15.000 / DV), (int) (16.125 / DV), (int) (16.875 / DV), (int) (17.625 / DV),
                    (int) (18.000 / DV), (int) (18.750 / DV), (int) (19.125 / DV), (int) (19.500 / DV),
                    (int) (19.875 / DV), (int) (20.250 / DV), (int) (20.625 / DV), (int) (21.000 / DV)
            };
    //#undef DV

    /* 0 / 3.0 / 1.5 / 6.0 dB/OCT */
    private static final int[] ksl_shift = new int[] {31, 1, 2, 0};


    /* sustain level table (3dB per step) */
    /* 0 - 15: 0, 3, 6, 9,12,15,18,21,24,27,30,33,36,39,42,93 (dB)*/
    private static int sc(double db) {
        return (int) (db * (2.0 / ENV_STEP));
    }

    private static final int[] sl_tab = new int[] {
            sc(0), sc(1), sc(2), sc(3), sc(4), sc(5), sc(6), sc(7),
            sc(8), sc(9), sc(10), sc(11), sc(12), sc(13), sc(14), sc(31)
    };
    //#undef SC


    private static final int RATE_STEPS = 8;
    private static final byte[] eg_inc = new byte[] {
            /*cycle:0 1  2 3  4 5  6 7*/

            /* 0 */ 0, 1, 0, 1, 0, 1, 0, 1, /* rates 00..12 0 (increment by 0 or 1) */
            /* 1 */ 0, 1, 0, 1, 1, 1, 0, 1, /* rates 00..12 1 */
            /* 2 */ 0, 1, 1, 1, 0, 1, 1, 1, /* rates 00..12 2 */
            /* 3 */ 0, 1, 1, 1, 1, 1, 1, 1, /* rates 00..12 3 */

            /* 4 */ 1, 1, 1, 1, 1, 1, 1, 1, /* rate 13 0 (increment by 1) */
            /* 5 */ 1, 1, 1, 2, 1, 1, 1, 2, /* rate 13 1 */
            /* 6 */ 1, 2, 1, 2, 1, 2, 1, 2, /* rate 13 2 */
            /* 7 */ 1, 2, 2, 2, 1, 2, 2, 2, /* rate 13 3 */

            /* 8 */ 2, 2, 2, 2, 2, 2, 2, 2, /* rate 14 0 (increment by 2) */
            /* 9 */ 2, 2, 2, 4, 2, 2, 2, 4, /* rate 14 1 */
            /*10 */ 2, 4, 2, 4, 2, 4, 2, 4, /* rate 14 2 */
            /*11 */ 2, 4, 4, 4, 2, 4, 4, 4, /* rate 14 3 */

            /*12 */ 4, 4, 4, 4, 4, 4, 4, 4, /* rates 15 0, 15 1, 15 2, 15 3 (increment by 4) */
            /*13 */ 8, 8, 8, 8, 8, 8, 8, 8, /* rates 15 2, 15 3 for attack */
            /*14 */ 0, 0, 0, 0, 0, 0, 0, 0, /* infinity rates for attack and decay(s) */
    };

    private static byte o(int a) {
        return (byte) (a * RATE_STEPS);
    }

    /*note that there is no O(13) in this table - it's directly in the code */
    private static final byte[] eg_rate_select = new byte[] { /* Envelope Generator rates (16 + 64 rates + 16 RKS) */
            /* 16 infinite time rates */
            o(14), o(14), o(14), o(14), o(14), o(14), o(14), o(14),
            o(14), o(14), o(14), o(14), o(14), o(14), o(14), o(14),

            /* rates 00-12 */
            o(0), o(1), o(2), o(3),
            o(0), o(1), o(2), o(3),
            o(0), o(1), o(2), o(3),
            o(0), o(1), o(2), o(3),
            o(0), o(1), o(2), o(3),
            o(0), o(1), o(2), o(3),
            o(0), o(1), o(2), o(3),
            o(0), o(1), o(2), o(3),
            o(0), o(1), o(2), o(3),
            o(0), o(1), o(2), o(3),
            o(0), o(1), o(2), o(3),
            o(0), o(1), o(2), o(3),
            o(0), o(1), o(2), o(3),

            /* rate 13 */
            o(4), o(5), o(6), o(7),

            /* rate 14 */
            o(8), o(9), o(10), o(11),

            /* rate 15 */
            o(12), o(12), o(12), o(12),

            /* 16 dummy rates (same as 15 3) */
            o(12), o(12), o(12), o(12), o(12), o(12), o(12), o(12),
            o(12), o(12), o(12), o(12), o(12), o(12), o(12), o(12),

    };
    //#undef O

    /*rate  0,    1,    2,    3,   4,   5,   6,  7,  8,  9,  10, 11, 12, 13, 14, 15 */
    /*shift 12,   11,   10,   9,   8,   7,   6,  5,  4,  3,  2,  1,  0,  0,  0,  0  */
    /*mask  4095, 2047, 1023, 511, 255, 127, 63, 31, 15, 7,  3,  1,  0,  0,  0,  0  */

    private static byte o2(int a) {
        return (byte) (a * 1);
    }

    private static final byte[] eg_rate_shift = new byte[] { /* Envelope Generator counter shifts (16 + 64 rates + 16 RKS) */
            /* 16 infinite time rates */
            o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),
            o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),

            /* rates 00-12 */
            o2(12), o2(12), o2(12), o2(12),
            o2(11), o2(11), o2(11), o2(11),
            o2(10), o2(10), o2(10), o2(10),
            o2(9), o2(9), o2(9), o2(9),
            o2(8), o2(8), o2(8), o2(8),
            o2(7), o2(7), o2(7), o2(7),
            o2(6), o2(6), o2(6), o2(6),
            o2(5), o2(5), o2(5), o2(5),
            o2(4), o2(4), o2(4), o2(4),
            o2(3), o2(3), o2(3), o2(3),
            o2(2), o2(2), o2(2), o2(2),
            o2(1), o2(1), o2(1), o2(1),
            o2(0), o2(0), o2(0), o2(0),

            /* rate 13 */
            o2(0), o2(0), o2(0), o2(0),

            /* rate 14 */
            o2(0), o2(0), o2(0), o2(0),

            /* rate 15 */
            o2(0), o2(0), o2(0), o2(0),

            /* 16 dummy rates (same as 15 3) */
            o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),
            o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),
    };
    //#undef O


    /* multiple table */
    private static final byte ML = 2;
    private static final byte[] mul_tab = new byte[] {
            /* 1/2, 1, 2, 3, 4, 5, 6, 7, 8, 9,10,10,12,12,15,15 */
            (byte) (0.50 * ML), (byte) (1.00 * ML), (byte) (2.00 * ML), (byte) (3.00 * ML), (byte) (4.00 * ML), (byte) (5.00 * ML), (byte) (6.00 * ML), (byte) (7.00 * ML),
            (byte) (8.00 * ML), (byte) (9.00 * ML), (byte) (10.00 * ML), (byte) (10.00 * ML), (byte) (12.00 * ML), (byte) (12.00 * ML), (byte) (15.00 * ML), (byte) (15.00 * ML)
    };
    //#undef ML

    /*  TL_TAB_LEN is calculated as:
     *   12 - sinus amplitude bits     (Y axis)
     *   2  - sinus sign bit           (Y axis)
     *   TL_RES_LEN - sinus resolution (X axis)
     */
    private static final int TL_TAB_LEN = 12 * 2 * TL_RES_LEN;
    private int[] tl_tab = new int[TL_TAB_LEN];

    private static final int ENV_QUIET = TL_TAB_LEN >> 4;

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

    private static final int LFO_AM_TAB_ELEMENTS = 210;
    private static final byte[] lfo_am_table = new byte[] {
            0, 0, 0, 0, 0, 0, 0,
            1, 1, 1, 1,
            2, 2, 2, 2,
            3, 3, 3, 3,
            4, 4, 4, 4,
            5, 5, 5, 5,
            6, 6, 6, 6,
            7, 7, 7, 7,
            8, 8, 8, 8,
            9, 9, 9, 9,
            10, 10, 10, 10,
            11, 11, 11, 11,
            12, 12, 12, 12,
            13, 13, 13, 13,
            14, 14, 14, 14,
            15, 15, 15, 15,
            16, 16, 16, 16,
            17, 17, 17, 17,
            18, 18, 18, 18,
            19, 19, 19, 19,
            20, 20, 20, 20,
            21, 21, 21, 21,
            22, 22, 22, 22,
            23, 23, 23, 23,
            24, 24, 24, 24,
            25, 25, 25, 25,
            26, 26, 26,
            25, 25, 25, 25,
            24, 24, 24, 24,
            23, 23, 23, 23,
            22, 22, 22, 22,
            21, 21, 21, 21,
            20, 20, 20, 20,
            19, 19, 19, 19,
            18, 18, 18, 18,
            17, 17, 17, 17,
            16, 16, 16, 16,
            15, 15, 15, 15,
            14, 14, 14, 14,
            13, 13, 13, 13,
            12, 12, 12, 12,
            11, 11, 11, 11,
            10, 10, 10, 10,
            9, 9, 9, 9,
            8, 8, 8, 8,
            7, 7, 7, 7,
            6, 6, 6, 6,
            5, 5, 5, 5,
            4, 4, 4, 4,
            3, 3, 3, 3,
            2, 2, 2, 2,
            1, 1, 1, 1
    };

    /* LFO Phase Modulation table (verified on real YM3812) */
    private static final byte[] lfo_pm_table = new byte[] {
            /* FNUM2/FNUM = 00 0xxxxxxx (0x0000) */
            0, 0, 0, 0, 0, 0, 0, 0, /*LFO PM depth = 0*/
            0, 0, 0, 0, 0, 0, 0, 0, /*LFO PM depth = 1*/

            /* FNUM2/FNUM = 00 1xxxxxxx (0x0080) */
            0, 0, 0, 0, 0, 0, 0, 0, /*LFO PM depth = 0*/
            1, 0, 0, 0, -1, 0, 0, 0, /*LFO PM depth = 1*/

            /* FNUM2/FNUM = 01 0xxxxxxx (0x0100) */
            1, 0, 0, 0, -1, 0, 0, 0, /*LFO PM depth = 0*/
            2, 1, 0, -1, -2, -1, 0, 1, /*LFO PM depth = 1*/

            /* FNUM2/FNUM = 01 1xxxxxxx (0x0180) */
            1, 0, 0, 0, -1, 0, 0, 0, /*LFO PM depth = 0*/
            3, 1, 0, -1, -3, -1, 0, 1, /*LFO PM depth = 1*/

            /* FNUM2/FNUM = 10 0xxxxxxx (0x0200) */
            2, 1, 0, -1, -2, -1, 0, 1, /*LFO PM depth = 0*/
            4, 2, 0, -2, -4, -2, 0, 2, /*LFO PM depth = 1*/

            /* FNUM2/FNUM = 10 1xxxxxxx (0x0280) */
            2, 1, 0, -1, -2, -1, 0, 1, /*LFO PM depth = 0*/
            5, 2, 0, -2, -5, -2, 0, 2, /*LFO PM depth = 1*/

            /* FNUM2/FNUM = 11 0xxxxxxx (0x0300) */
            3, 1, 0, -1, -3, -1, 0, 1, /*LFO PM depth = 0*/
            6, 3, 0, -3, -6, -3, 0, 3, /*LFO PM depth = 1*/

            /* FNUM2/FNUM = 11 1xxxxxxx (0x0380) */
            3, 1, 0, -1, -3, -1, 0, 1, /*LFO PM depth = 0*/
            7, 3, 0, -3, -7, -3, 0, 3 /*LFO PM depth = 1*/
    };


    /* synchronized level of common table */
    private int num_lock = 0;

    private OPL_SLOT SLOT7_1(FM_OPL opl) {
        return (opl.P_CH[7].SLOT[SLOT1]);
    }

    private OPL_SLOT SLOT7_2(FM_OPL opl) {
        return (opl.P_CH[7].SLOT[SLOT2]);
    }

    private OPL_SLOT SLOT8_1(FM_OPL opl) {
        return (opl.P_CH[8].SLOT[SLOT1]);
    }

    private OPL_SLOT SLOT8_2(FM_OPL opl) {
        return (opl.P_CH[8].SLOT[SLOT2]);
    }

        /*INLINE int limit( int val, int max, int min ) {
            if ( val > max )
                val = max;
            else if ( val < min )
                val = min;

            return val;
        }*/


    /* status set and IRQ handling */
    private void OPL_STATUS_SET(FM_OPL opl, int flag) {
        /* set status flag */
        opl.status |= (byte) flag;
        if ((opl.status & 0x80) == 0) {
            if ((opl.status & opl.statusmask) != 0) {   /* IRQ on */
                opl.status |= 0x80;
                /* callback user interrupt handler (IRQ is OFF to ON) */
                if (opl.irqHandler != null) opl.irqHandler.accept(opl.irqParam, 1);
            }
        }
    }

    /* status reset and IRQ handling */
    private void OPL_STATUS_RESET(FM_OPL opl, int flag) {
        /* reset status flag */
        opl.status &= (byte) ~(byte) flag;
        if ((opl.status & 0x80) != 0) {
            if ((opl.status & opl.statusmask) == 0) {
                opl.status &= 0x7f;
                /* callback user interrupt handler (IRQ is ON to OFF) */
                if (opl.irqHandler != null) opl.irqHandler.accept(opl.irqParam, 0);
            }
        }
    }

    /* IRQ mask set */
    private void OPL_STATUSMASK_SET(FM_OPL opl, int flag) {
        opl.statusmask = (byte) flag;
        /* IRQ handling check */
        OPL_STATUS_SET(opl, 0);
        OPL_STATUS_RESET(opl, 0);
    }

    /* advance LFO to next sample */
    private void advance_lfo(FM_OPL opl) {
        byte tmp;

        /* LFO */
        opl.lfo_am_cnt += opl.lfo_am_inc;
        if (opl.lfo_am_cnt >= ((int) LFO_AM_TAB_ELEMENTS << LFO_SH)) /* lfo_am_table is 210 elements long */
            opl.lfo_am_cnt -= ((int) LFO_AM_TAB_ELEMENTS << LFO_SH);

        tmp = lfo_am_table[opl.lfo_am_cnt >> LFO_SH];

        if (opl.lfo_am_depth != 0)
            opl.LFO_AM = tmp;
        else
            opl.LFO_AM = (byte) (tmp >> 2);

        opl.lfo_pm_cnt += opl.lfo_pm_inc;
        opl.LFO_PM = (int) (((opl.lfo_pm_cnt >> LFO_SH) & 7) | opl.lfo_pm_depth_range);
    }

    private void refresh_eg(FM_OPL opl) {
        OPL_CH ch;
        OPL_SLOT op;
        int i;
        int new_vol;

        for (i = 0; i < 9 * 2; i++) {
            ch = opl.P_CH[i / 2];
            op = ch.SLOT[i & 1];

            // Envelope Generator
            switch (op.state) {
            case EG_ATT:        // attack phase
                if ((opl.eg_cnt & ((1 << op.eg_sh_ar) - 1)) == 0) {
                    new_vol = op.volume + ((~op.volume *
                            (eg_inc[op.eg_sel_ar + ((opl.eg_cnt >> op.eg_sh_ar) & 7)])
                    ) >> 3);
                    if (new_vol <= MIN_ATT_INDEX) {
                        op.volume = MIN_ATT_INDEX;
                        op.state = EG_DEC;
                    }
                }
                break;
                        /*case EG_DEC: // decay phase
                            if ( !(opl.eg_cnt & ((1<<Op.eg_sh_dr)-1) ) )
                            {
                                new_vol = Op.volume + eg_inc[Op.eg_sel_dr + ((opl.eg_cnt>>Op.eg_sh_dr)&7)];

                                if ( new_vol >= Op.sl )
                                    Op.state = EG_SUS;
                            }
                            break;
                        case EG_SUS: // sustain phase
                            if ( !Op.eg_type) percussive mode
                            {
                                new_vol = Op.volume + eg_inc[Op.eg_sel_rr + ((opl.eg_cnt>>Op.eg_sh_rr)&7)];

                                if ( !(opl.eg_cnt & ((1<<Op.eg_sh_rr)-1) ) )
                                {
                                    if ( new_vol >= MAX_ATT_INDEX )
                                        Op.volume = MAX_ATT_INDEX;
                                }
                            }
                            break;
                        case EG_REL: // release phase
                            if ( !(opl.eg_cnt & ((1<<Op.eg_sh_rr)-1) ) )
                            {
                                new_vol = Op.volume + eg_inc[Op.eg_sel_rr + ((opl.eg_cnt>>Op.eg_sh_rr)&7)];
                                if ( new_vol >= MAX_ATT_INDEX )
                                {
                                    Op.volume = MAX_ATT_INDEX;
                                    Op.state = EG_OFF;
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
    private void advance(FM_OPL opl) {
        OPL_CH ch;
        OPL_SLOT op;
        int i;

        opl.eg_timer += opl.eg_timer_add;

        while (opl.eg_timer >= opl.eg_timer_overflow) {
            opl.eg_timer -= opl.eg_timer_overflow;

            opl.eg_cnt++;

            for (i = 0; i < 9 * 2; i++) {
                ch = opl.P_CH[i / 2];
                op = ch.SLOT[i & 1];

                /* Envelope Generator */
                switch (op.state) {
                case EG_ATT:        /* attack phase */
                    if ((opl.eg_cnt & ((1 << op.eg_sh_ar) - 1)) == 0) {
                        op.volume += (~op.volume *
                                (eg_inc[op.eg_sel_ar + ((opl.eg_cnt >> op.eg_sh_ar) & 7)])
                        ) >> 3;

                        if (op.volume <= MIN_ATT_INDEX) {
                            op.volume = MIN_ATT_INDEX;
                            op.state = EG_DEC;
                        }

                    }
                    break;

                case EG_DEC:    /* decay phase */
                    if ((opl.eg_cnt & ((1 << op.eg_sh_dr) - 1)) == 0) {
                        op.volume += eg_inc[op.eg_sel_dr + ((opl.eg_cnt >> op.eg_sh_dr) & 7)];

                        if (op.volume >= op.sl)
                            op.state = EG_SUS;

                    }
                    break;

                case EG_SUS:    /* sustain phase */

                            /* this is important behaviour:
                            one can change percusive/non-percussive modes on the fly and
                            the chip will remain in sustain phase - verified on real YM3812 */

                    if (op.eg_type != 0)        /* non-percussive mode */ {
                        /* do nothing */
                    } else                /* percussive mode */ {
                        /* during sustain phase chip adds Release Rate (in percussive mode) */
                        if ((opl.eg_cnt & ((1 << op.eg_sh_rr) - 1)) == 0) {
                            op.volume += eg_inc[op.eg_sel_rr + ((opl.eg_cnt >> op.eg_sh_rr) & 7)];

                            if (op.volume >= MAX_ATT_INDEX)
                                op.volume = MAX_ATT_INDEX;
                        }
                        /* else do nothing in sustain phase */
                    }
                    break;

                case EG_REL:    /* release phase */
                    if ((opl.eg_cnt & ((1 << op.eg_sh_rr) - 1)) == 0) {
                        op.volume += eg_inc[op.eg_sel_rr + ((opl.eg_cnt >> op.eg_sh_rr) & 7)];

                        if (op.volume >= MAX_ATT_INDEX) {
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

        for (i = 0; i < 9 * 2; i++) {
            ch = opl.P_CH[i / 2];
            op = ch.SLOT[i & 1];

            /* Phase Generator */
            if (op.vib != 0) {
                byte block;
                int block_fnum = ch.block_fnum;

                int fnum_lfo = (block_fnum & 0x0380) >> 7;

                int lfo_fn_table_index_offset = lfo_pm_table[opl.LFO_PM + 16 * fnum_lfo];

                if (lfo_fn_table_index_offset != 0)  /* LFO phase modulation active */ {
                    block_fnum += (int) lfo_fn_table_index_offset;
                    block = (byte) ((block_fnum & 0x1c00) >> 10);
                    op.cnt += (opl.fn_tab[block_fnum & 0x03ff] >> (7 - block)) * op.mul;
                } else    /* LFO phase modulation  = zero */ {
                    op.cnt += op.Incr;
                }
            } else    /* LFO phase modulation disabled for this Operator */ {
                op.cnt += op.Incr;
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

        opl.noise_p += opl.noise_f;
        i = (int) (opl.noise_p >> FREQ_SH);        /* number of events (shifts of the shift register) */
        opl.noise_p &= FREQ_MASK;
        while (i != 0) {
                /*
                (int)32 j;
                j = ( (opl.noise_rng) ^ (opl.noise_rng>>14) ^ (opl.noise_rng>>15) ^ (opl.noise_rng>>22) ) & 1;
                opl.noise_rng = (j<<22) | (opl.noise_rng>>1);
                */

                /*
                    Instead of doing all the logic operations above, we
                    use a trick here (and use bit 0 as the noise output).
                    The difference is only that the noise bit changes one
                    step ahead. This doesn't matter since we don't know
                    what is real state of the noise_rng after the reset.
                */

            if ((opl.noise_rng & 1) != 0) opl.noise_rng ^= 0x800302;
            opl.noise_rng >>= 1;

            i--;
        }
    }


    private int op_calc(int phase, int env, int pm, int wave_tab) {
        int p;

        p = (env << 4) + sin_tab[wave_tab + ((((int) ((phase & ~FREQ_MASK) + (pm << 16))) >> FREQ_SH) & SIN_MASK)];

        if (p >= TL_TAB_LEN)
            return 0;
        return tl_tab[p];
    }

    private int op_calc1(int phase, int env, int pm, int wave_tab) {
        int p;

        p = (env << 4) + sin_tab[wave_tab + ((((int) ((phase & ~FREQ_MASK) + pm)) >> FREQ_SH) & SIN_MASK)];

        if (p >= TL_TAB_LEN)
            return 0;
        return tl_tab[p];
    }


    private int volume_calc(OPL_SLOT op, FM_OPL opl) {
        return (int) (op.tll + ((int) op.volume) + (opl.LFO_AM & op.AMmask));
    }

    /* calculate output */
    private void OPL_CALC_CH(FM_OPL opl, OPL_CH ch) {
        OPL_SLOT SLOT;
        int env;
        int _out;

        if (ch.Muted != 0)
            return;

        opl.phase_modulation = 0;

        /* SLOT 1 */
        SLOT = ch.SLOT[SLOT1];
        env = volume_calc(SLOT, opl);
        _out = SLOT.op1_out[0] + SLOT.op1_out[1];
        SLOT.op1_out[0] = SLOT.op1_out[1];
        //SLOT.connect1 += SLOT.op1_out[0];
        if (SLOT.ptrConnect1 == 0) opl.output[0] += SLOT.op1_out[0];
        else opl.phase_modulation += SLOT.op1_out[0];
        SLOT.op1_out[1] = 0;
        if (env < ENV_QUIET) {
            if (SLOT.fb == 0)
                _out = 0;
            SLOT.op1_out[1] = op_calc1(SLOT.cnt, env, (_out << SLOT.fb), SLOT.wavetable);
        }

        /* SLOT 2 */
        //SLOT++;
        SLOT = ch.SLOT[SLOT2];
        env = volume_calc(SLOT, opl);
        if (env < ENV_QUIET)
            opl.output[0] += op_calc(SLOT.cnt, env, opl.phase_modulation, SLOT.wavetable);
    }

        /*
            operators used in the rhythm sounds generation process:

            Envelope Generator:

        channel  Operator  register number   Bass  High  Snare Tom  Top
        / slot   number    TL ARDR SLRR Wave Drum  Hat   Drum  Tom  Cymbal
         6 / 0   12        50  70   90   f0  +
         6 / 1   15        53  73   93   f3  +
         7 / 0   13        51  71   91   f1        +
         7 / 1   16        54  74   94   f4              +
         8 / 0   14        52  72   92   f2                    +
         8 / 1   17        55  75   95   f5                          +

            Phase Generator:

        channel  Operator  register number   Bass  High  Snare Tom  Top
        / slot   number    MULTIPLE          Drum  Hat   Drum  Tom  Cymbal
         6 / 0   12        30                +
         6 / 1   15        33                +
         7 / 0   13        31                      +     +           +
         7 / 1   16        34                -----  n o t  u s e d -----
         8 / 0   14        32                                  +
         8 / 1   17        35                      +                 +

        channel  Operator  register number   Bass  High  Snare Tom  Top
        number   number    BLK/FNUM2 FNUM    Drum  Hat   Drum  Tom  Cymbal
           6     12,15     B6        A6      +

           7     13,16     B7        A7            +     +           +

           8     14,17     B8        A8            +           +     +

        */

    /* calculate rhythm */

    private void OPL_CALC_RH(FM_OPL opl, OPL_CH[] ch, int noise) {
        OPL_SLOT slot;
        int _out;
        int env;


            /* Bass Drum (verified on real YM3812):
              - depends on the channel 6 'connect' register:
                  when connect = 0 it works the same as in normal (non-rhythm) mode (op1.op2.out)
                  when connect = 1 _only_ Operator 2 is present on output (op2.out), Operator 1 is ignored
              - output sample always is multiplied by 2
            */

        opl.phase_modulation = 0;
        /* slot 1 */
        slot = ch[6].SLOT[SLOT1];
        env = volume_calc(slot, opl);

        _out = slot.op1_out[0] + slot.op1_out[1];
        slot.op1_out[0] = slot.op1_out[1];

        if (slot.con == 0)
            opl.phase_modulation = slot.op1_out[0];
        /* else ignore output of Operator 1 */

        slot.op1_out[1] = 0;
        if (env < ENV_QUIET) {
            if (slot.fb == 0)
                _out = 0;
            slot.op1_out[1] = op_calc1(slot.cnt, env, (_out << slot.fb), slot.wavetable);
        }

        /* slot 2 */
        //slot++;
        slot = ch[6].SLOT[SLOT2];
        env = volume_calc(slot, opl);
        if (env < ENV_QUIET && opl.MuteSpc[0] == 0)
            opl.output[0] += op_calc(slot.cnt, env, opl.phase_modulation, slot.wavetable) * 2;


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
        env = volume_calc(SLOT7_1(opl), opl);
        if (env < ENV_QUIET && opl.MuteSpc[4] == 0) {

                /* high hat phase generation:
                    phase = d0 or 234 (based on frequency only)
                    phase = 34 or 2d0 (based on noise)
                */

            /* base frequency derived from Operator 1 in channel 7 */
            byte bit7 = (byte) (((SLOT7_1(opl).cnt >> FREQ_SH) >> 7) & 1);
            byte bit3 = (byte) (((SLOT7_1(opl).cnt >> FREQ_SH) >> 3) & 1);
            byte bit2 = (byte) (((SLOT7_1(opl).cnt >> FREQ_SH) >> 2) & 1);

            byte res1 = (byte) ((bit2 ^ bit7) | bit3);

            /* when res1 = 0 phase = 0x000 | 0xd0; */
            /* when res1 = 1 phase = 0x200 | (0xd0>>2); */
            int phase = (int) (res1 != 0 ? (0x200 | (0xd0 >> 2)) : 0xd0);

            /* enable gate based on frequency of Operator 2 in channel 8 */
            byte bit5e = (byte) (((SLOT8_2(opl).cnt >> FREQ_SH) >> 5) & 1);
            byte bit3e = (byte) (((SLOT8_2(opl).cnt >> FREQ_SH) >> 3) & 1);

            byte res2 = (byte) (bit3e ^ bit5e);

            /* when res2 = 0 pass the phase from calculation above (res1); */
            /* when res2 = 1 phase = 0x200 | (0xd0>>2); */
            if (res2 != 0)
                phase = (0x200 | (0xd0 >> 2));


            /* when phase & 0x200 is set and noise=1 then phase = 0x200|0xd0 */
            /* when phase & 0x200 is set and noise=0 then phase = 0x200|(0xd0>>2), ie no change */
            if ((phase & 0x200) != 0) {
                if (noise != 0)
                    phase = 0x200 | 0xd0;
            } else
                /* when phase & 0x200 is clear and noise=1 then phase = 0xd0>>2 */
                /* when phase & 0x200 is clear and noise=0 then phase = 0xd0, ie no change */ {
                if (noise != 0)
                    phase = 0xd0 >> 2;
            }

            opl.output[0] += op_calc(phase << FREQ_SH, env, 0, SLOT7_1(opl).wavetable) * 2;
        }

        /* Snare Drum (verified on real YM3812) */
        env = volume_calc(SLOT7_2(opl), opl);
        if (env < ENV_QUIET && opl.MuteSpc[1] == 0) {
            /* base frequency derived from Operator 1 in channel 7 */
            byte bit8 = (byte) (((SLOT7_1(opl).cnt >> FREQ_SH) >> 8) & 1);

            /* when bit8 = 0 phase = 0x100; */
            /* when bit8 = 1 phase = 0x200; */
            int phase = (int) (bit8 != 0 ? 0x200 : 0x100);

            /* Noise bit XOR'es phase by 0x100 */
            /* when noisebit = 0 pass the phase from calculation above */
            /* when noisebit = 1 phase ^= 0x100; */
            /* in other words: phase ^= (noisebit<<8); */
            if (noise != 0)
                phase ^= 0x100;

            opl.output[0] += op_calc(phase << FREQ_SH, env, 0, SLOT7_2(opl).wavetable) * 2;
        }

        /* Tom Tom (verified on real YM3812) */
        env = volume_calc(SLOT8_1(opl), opl);
        if (env < ENV_QUIET && opl.MuteSpc[2] == 0)
            opl.output[0] += op_calc(SLOT8_1(opl).cnt, env, 0, SLOT8_1(opl).wavetable) * 2;

        /* Top Cymbal (verified on real YM3812) */
        env = volume_calc(SLOT8_2(opl), opl);
        if (env < ENV_QUIET && opl.MuteSpc[3] == 0) {
            /* base frequency derived from Operator 1 in channel 7 */
            byte bit7 = (byte) (((SLOT7_1(opl).cnt >> FREQ_SH) >> 7) & 1);
            byte bit3 = (byte) (((SLOT7_1(opl).cnt >> FREQ_SH) >> 3) & 1);
            byte bit2 = (byte) (((SLOT7_1(opl).cnt >> FREQ_SH) >> 2) & 1);

            byte res1 = (byte) ((bit2 ^ bit7) | bit3);

            /* when res1 = 0 phase = 0x000 | 0x100; */
            /* when res1 = 1 phase = 0x200 | 0x100; */
            int phase = res1 != 0 ? 0x300 : 0x100;

            /* enable gate based on frequency of Operator 2 in channel 8 */
            byte bit5e = (byte) (((SLOT8_2(opl).cnt >> FREQ_SH) >> 5) & 1);
            byte bit3e = (byte) (((SLOT8_2(opl).cnt >> FREQ_SH) >> 3) & 1);

            byte res2 = (byte) (bit3e ^ bit5e);
            /* when res2 = 0 pass the phase from calculation above (res1); */
            /* when res2 = 1 phase = 0x200 | 0x100; */
            if (res2 != 0)
                phase = 0x300;

            opl.output[0] += op_calc(phase << FREQ_SH, env, 0, SLOT8_2(opl).wavetable) * 2;
        }
    }


    /* generic table initialize */
    private int init_tables() {
        int i, x;
        int n;
        double o, m;


        for (x = 0; x < TL_RES_LEN; x++) {
            m = (1 << 16) / Math.pow(2, (x + 1) * (ENV_STEP / 4.0) / 8.0);
            m = Math.floor(m);

            /* we never reach (1<<16) here due to the (x+1) */
            /* result fits within 16 bits at maximum */

            n = (int) m;     /* 16 bits here */
            n >>= 4;        /* 12 bits here */
            if ((n & 1) != 0)      /* round to nearest */
                n = (n >> 1) + 1;
            else
                n = n >> 1;
            /* 11 bits here (rounded) */
            n <<= 1;        /* 12 bits here (as in real chip) */
            tl_tab[x * 2 + 0] = n;
            tl_tab[x * 2 + 1] = -tl_tab[x * 2 + 0];

            for (i = 1; i < 12; i++) {
                tl_tab[x * 2 + 0 + i * 2 * TL_RES_LEN] = tl_tab[x * 2 + 0] >> i;
                tl_tab[x * 2 + 1 + i * 2 * TL_RES_LEN] = -tl_tab[x * 2 + 0 + i * 2 * TL_RES_LEN];
            }
            //#if 0
            //   logerror("tl %04i", x*2);
            //   for (i=0; i<12; i++)
            //    logerror(", [%02i] %5i", i*2, tl_tab[ x*2 /*+1*/ + i*2*TL_RES_LEN ] );
            //   logerror("\n");
            //#endif
        }
        /*logerror("FMOPL.C: TL_TAB_LEN = %i elements (%i bytes)\n",TL_TAB_LEN, (int)sizeof(tl_tab));*/


        for (i = 0; i < SIN_LEN; i++) {
            /* non-standard sinus */
            m = Math.sin(((i * 2) + 1) * Math.PI / SIN_LEN); /* checked against the real chip */

            /* we never reach zero here due to ((i*2)+1) */

            if (m > 0.0)
                o = 8 * Math.log(1.0 / m) / Math.log(2.0);    /* convert to 'decibels' */
            else
                o = 8 * Math.log(-1.0 / m) / Math.log(2.0);   /* convert to 'decibels' */

            o = o / (ENV_STEP / 4);

            n = (int) (2.0 * o);
            if ((n & 1) != 0)                      /* round to nearest */
                n = (n >> 1) + 1;
            else
                n = n >> 1;

            sin_tab[i] = (int) (n * 2 + (m >= 0.0 ? 0 : 1));

            /*logerror("FMOPL.C: sin [%4i (hex=%03x)]= %4i (tl_tab value=%5i)\n", i, i, sin_tab[i], tl_tab[sin_tab[i]] );*/
        }

        for (i = 0; i < SIN_LEN; i++) {
            /* waveform 1:  __      __     */
            /*             /  \____/  \____*/
            /* output only first half of the sinus waveform (positive one) */

            if ((i & (1 << (SIN_BITS - 1))) != 0)
                sin_tab[1 * SIN_LEN + i] = (int) TL_TAB_LEN;
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
                sin_tab[3 * SIN_LEN + i] = (int) TL_TAB_LEN;
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

    private void OPLCloseTable() {
        //# ifdef SAVE_SAMPLE
        //    fclose(sample[0]);
        //#endif
    }


    private void OPL_initalize(FM_OPL opl) {
        int i;

        /* frequency base */
        opl.freqbase = (opl.rate != 0) ? ((double) opl.clock / 72.0) / opl.rate : 0;
        //#if 0
        // opl.rate = (double)opl.clock / 72.0;
        // opl.freqbase  = 1.0;
        //#endif

        /*logerror("freqbase=%f\n", opl.freqbase);*/

        /* Timer base time */
        //opl.TimerBase = attotime_mul(ATTOTIME_IN_HZ(opl.clock), 72);

        /* make fnumber . increment counter table */
        for (i = 0; i < 1024; i++) {
            /* opn phase increment counter = 20bit */
            opl.fn_tab[i] = (int) ((double) i * 64 * opl.freqbase * (1 << (FREQ_SH - 10))); /* -10 because chip works with 10.10 fixed point, while we use 16.16 */
            //#if 0
            //  logerror("FMOPL.C: fn_tab[%4i] = %08x (dec=%8i)\n",
            //     i, opl.fn_tab[i]>>6, opl.fn_tab[i]>>6 );
            //#endif
        }

        //#if 0
        // for( i=0 ; i < 16 ; i++ )
        // {
        //  logerror("FMOPL.C: sl_tab[%i] = %08x\n",
        //   i, sl_tab[i] );
        // }
        // for( i=0 ; i < 8 ; i++ )
        // {
        //  int j;
        //  logerror("FMOPL.C: ksl_tab[oct=%2i] =",i);
        //  for (j=0; j<16; j++)
        //  {
        //   logerror("%08x ", ksl_tab[i*16+j] );
        //  }
        //  logerror("\n");
        // }
        //#endif

        for (i = 0; i < 9; i++)
            opl.P_CH[i].Muted = 0x00;
        for (i = 0; i < 6; i++)
            opl.MuteSpc[i] = 0x00;


        /* Amplitude modulation: 27 output levels (triangle waveform); 1 level takes one of: 192, 256 or 448 samples */
        /* One entry from LFO_AM_TABLE lasts for 64 samples */
        opl.lfo_am_inc = (int) ((1.0 / 64.0) * (1 << LFO_SH) * opl.freqbase);

        /* Vibrato: 8 output levels (triangle waveform); 1 level takes 1024 samples */
        opl.lfo_pm_inc = (int) ((1.0 / 1024.0) * (1 << LFO_SH) * opl.freqbase);

        /*logerror ("opl.lfo_am_inc = %8x ; opl.lfo_pm_inc = %8x\n", opl.lfo_am_inc, opl.lfo_pm_inc);*/

        /* Noise generator: a step takes 1 sample */
        opl.noise_f = (int) ((1.0 / 1.0) * (1 << FREQ_SH) * opl.freqbase);

        opl.eg_timer_add = (int) ((1 << EG_SH) * opl.freqbase);
        opl.eg_timer_overflow = (1) * (1 << EG_SH);
        /*logerror("OPLinit eg_timer_add=%8x eg_timer_overflow=%8x\n", opl.eg_timer_add, opl.eg_timer_overflow);*/

    }

    private void FM_KEYON(OPL_SLOT slot, int key_set) {
        if (slot.key == 0) {
            /* restart Phase Generator */
            slot.cnt = 0;
            /* phase . Attack */
            slot.state = EG_ATT;
        }
        slot.key |= key_set;
    }

    private void FM_KEYOFF(OPL_SLOT slot, int key_clr) {
        if (slot.key != 0) {
            slot.key &= key_clr;

            if (slot.key == 0) {
                /* phase . Release */
                if (slot.state > EG_REL)
                    slot.state = EG_REL;
            }
        }
    }

    /* update phase increment counter of Operator (also update the EG rates if necessary) */
    private void CALC_FCSLOT(OPL_CH ch, OPL_SLOT slot) {
        int ksr;

        /* (frequency) phase increment counter */
        slot.Incr = ch.fc * slot.mul;
        ksr = ch.kcode >> slot.KSR;

        if (slot.ksr != ksr) {
            slot.ksr = (byte) ksr;

            /* calculate envelope generator rates */
            if ((slot.ar + slot.ksr) < 16 + 62) {
                slot.eg_sh_ar = eg_rate_shift[slot.ar + slot.ksr];
                slot.eg_sel_ar = eg_rate_select[slot.ar + slot.ksr];
            } else {
                slot.eg_sh_ar = 0;
                slot.eg_sel_ar = 13 * RATE_STEPS;
            }
            slot.eg_sh_dr = eg_rate_shift[slot.dr + slot.ksr];
            slot.eg_sel_dr = eg_rate_select[slot.dr + slot.ksr];
            slot.eg_sh_rr = eg_rate_shift[slot.rr + slot.ksr];
            slot.eg_sel_rr = eg_rate_select[slot.rr + slot.ksr];
        }
    }

    /* set multi,am,vib,EG-TYP,KSR,mul */
    private void set_mul(FM_OPL opl, int slot, int v) {
        OPL_CH CH = opl.P_CH[slot / 2];
        OPL_SLOT SLOT = CH.SLOT[slot & 1];

        SLOT.mul = mul_tab[v & 0x0f];
        SLOT.KSR = (byte) ((v & 0x10) != 0 ? 0 : 2);
        SLOT.eg_type = (byte) (v & 0x20);
        SLOT.vib = (byte) (v & 0x40);
        SLOT.AMmask = (int) ((v & 0x80) != 0 ? ~0 : 0);
        CALC_FCSLOT(CH, SLOT);
    }

    /* set ksl & tl */
    private void set_ksl_tl(FM_OPL opl, int slot, int v) {
        OPL_CH CH = opl.P_CH[slot / 2];
        OPL_SLOT SLOT = CH.SLOT[slot & 1];

        SLOT.ksl = (byte) ksl_shift[v >> 6];
        SLOT.TL = (int) ((v & 0x3f) << (ENV_BITS - 1 - 7)); /* 7 bits TL (bit 6 = always 0) */

        SLOT.tll = (int) (SLOT.TL + (CH.ksl_base >> SLOT.ksl));
    }

    /* set attack rate & decay rate  */
    private void set_ar_dr(FM_OPL opl, int slot, int v) {
        OPL_CH CH = opl.P_CH[slot / 2];
        OPL_SLOT SLOT = CH.SLOT[slot & 1];

        SLOT.ar = (int) ((v >> 4) != 0 ? 16 + ((v >> 4) << 2) : 0);

        if ((SLOT.ar + SLOT.ksr) < 16 + 62) {
            SLOT.eg_sh_ar = eg_rate_shift[SLOT.ar + SLOT.ksr];
            SLOT.eg_sel_ar = eg_rate_select[SLOT.ar + SLOT.ksr];
        } else {
            SLOT.eg_sh_ar = 0;
            SLOT.eg_sel_ar = 13 * RATE_STEPS;
        }

        SLOT.dr = (int) ((v & 0x0f) != 0 ? 16 + ((v & 0x0f) << 2) : 0);
        SLOT.eg_sh_dr = eg_rate_shift[SLOT.dr + SLOT.ksr];
        SLOT.eg_sel_dr = eg_rate_select[SLOT.dr + SLOT.ksr];
    }

    /* set sustain level & release rate */
    private void set_sl_rr(FM_OPL opl, int slot, int v) {
        OPL_CH CH = opl.P_CH[slot / 2];
        OPL_SLOT SLOT = CH.SLOT[slot & 1];

        SLOT.sl = sl_tab[v >> 4];

        SLOT.rr = (v & 0x0f) != 0 ? 16 + ((v & 0x0f) << 2) : 0;
        SLOT.eg_sh_rr = eg_rate_shift[SLOT.rr + SLOT.ksr];
        SLOT.eg_sel_rr = eg_rate_select[SLOT.rr + SLOT.ksr];
    }


    /* write a value v to register r on opl chip */
    private void OPLWriteReg(FM_OPL opl, int r, int v) {
        OPL_CH ch;
        int slot;
        int block_fnum;


        /* adjust bus to 8 bits */
        r &= 0xff;
        v &= 0xff;

            /*if (LOG_CYM_FILE && (cymfile) && (r!=0) )
            {
                fputc( (unsigned char)r, cymfile );
                fputc( (unsigned char)v, cymfile );
            }*/


        switch (r & 0xe0) {
        case 0x00:  /* 00-1f:control */
            switch (r & 0x1f) {
            case 0x01:  /* waveform select enable */
                if ((opl.type & OPL_TYPE_WAVESEL) != 0) {
                    opl.wavesel = (byte) (v & 0x20);
                    /* do not change the waveform previously selected */
                }
                break;
            case 0x02:  /* Timer 1 */
                opl.T[0] = (int) ((256 - v) * 4);
                break;
            case 0x03:  /* Timer 2 */
                opl.T[1] = (int) ((256 - v) * 16);
                break;
            case 0x04:  /* IRQ clear / mask and Timer enable */
                if ((v & 0x80) != 0) {   /* IRQ flag clear */
                    OPL_STATUS_RESET(opl, 0x7f - 0x08); /* don't reset BFRDY flag or we will have to call deltat module to set the flag */
                } else {   /* set IRQ mask ,timer enable*/
                    byte st1 = (byte) (v & 1);
                    byte st2 = (byte) ((v >> 1) & 1);

                    /* IRQRST,T1MSK,t2MSK,EOSMSK,BRMSK,x,ST2,ST1 */
                    OPL_STATUS_RESET(opl, v & (0x78 - 0x08));
                    OPL_STATUSMASK_SET(opl, (~v) & 0x78);

                    /* timer 2 */
                    if (opl.st[1] != st2) {
                        //attotime period = st2 ? attotime_mul(opl.TimerBase, opl.T[1]) : attotime_zero;
                        opl.st[1] = st2;
                        //if (opl.timer_handler) (opl.timer_handler)(opl.TimerParam,1,period);
                    }
                    /* timer 1 */
                    if (opl.st[0] != st1) {
                        //attotime period = st1 ? attotime_mul(opl.TimerBase, opl.T[0]) : attotime_zero;
                        opl.st[0] = st1;
                        //if (opl.timer_handler) (opl.timer_handler)(opl.TimerParam,0,period);
                    }
                }
                break;
            //#if BUILD_Y8950
            case 0x06:      /* Key Board OUT */
                if ((opl.type & OPL_TYPE_KEYBOARD) != 0) {
                    if (opl.keyboardhandler_w != null)
                        opl.keyboardhandler_w.accept(opl.keyboard_param, (byte) v);
                    //# ifdef _DEBUG
                    //else
                    //logerror("Y8950: write unmapped KEYBOARD port\n");
                    //#endif
                }
                break;
            case 0x07:  /* DELTA-T control 1 : START,REC,MEMDATA,REPT,SPOFF,x,x,RST */
                if ((opl.type & OPL_TYPE_ADPCM) != 0)
                    YM_DELTAT_ADPCM_Write(opl.deltaT, r - 0x07, v);
                break;
            //#endif
            case 0x08:  /* MODE,DELTA-T control 2 : CSM,NOTESEL,x,x,smpl,da/ad,64k,rom */
                opl.mode = (byte) v;
                //#if BUILD_Y8950
                if ((opl.type & OPL_TYPE_ADPCM) != 0)
                    YM_DELTAT_ADPCM_Write(opl.deltaT, r - 0x07, v & 0x0f); /* mask 4 LSBs in register 08 for DELTA-T unit */
                //#endif
                break;

            //#if BUILD_Y8950
            case 0x09:      /* START ADD */
            case 0x0a:
            case 0x0b:      /* STOP ADD  */
            case 0x0c:
            case 0x0d:      /* PRESCALE   */
            case 0x0e:
            case 0x0f:      /* ADPCM data write */
            case 0x10:      /* DELTA-N    */
            case 0x11:      /* DELTA-N    */
            case 0x12:      /* ADPCM volume */
                if ((opl.type & OPL_TYPE_ADPCM) != 0)
                    YM_DELTAT_ADPCM_Write(opl.deltaT, r - 0x07, v);
                break;

            case 0x15:      /* DAC data high 8 bits (F7,F6...F2) */
            case 0x16:      /* DAC data low 2 bits (F1, F0 in bits 7,6) */
            case 0x17:      /* DAC data shift (S2,S1,S0 in bits 2,1,0) */
                //# ifdef _DEBUG
                //logerror("FMOPL.C: DAC data register written, but not implemented reg=%02x val=%02x\n", r, v);
                //#endif
                break;

            case 0x18:      /* I/O CTRL (Direction) */
                if ((opl.type & OPL_TYPE_IO) != 0)
                    opl.portDirection = (byte) (v & 0x0f);
                break;
            case 0x19:      /* I/O DATA */
                if ((opl.type & OPL_TYPE_IO) != 0) {
                    opl.portLatch = (byte) v;
                    if (opl.porthandler_w != null)
                        opl.porthandler_w.accept(opl.port_param, (byte) (v & opl.portDirection));
                }
                break;
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
            set_mul(opl, slot, v);
            break;
        case 0x40:
            slot = slot_array[r & 0x1f];
            if (slot < 0) return;
            set_ksl_tl(opl, slot, v);
            break;
        case 0x60:
            slot = slot_array[r & 0x1f];
            if (slot < 0) return;
            set_ar_dr(opl, slot, v);
            break;
        case 0x80:
            slot = slot_array[r & 0x1f];
            if (slot < 0) return;
            set_sl_rr(opl, slot, v);
            break;
        case 0xa0:
            if (r == 0xbd)          /* am depth, vibrato depth, r,bd,sd,tom,tc,hh */ {
                opl.lfo_am_depth = (byte) (v & 0x80);
                opl.lfo_pm_depth_range = (byte) ((v & 0x40) != 0 ? 8 : 0);

                opl.rhythm = (byte) (v & 0x3f);

                if ((opl.rhythm & 0x20) != 0) {
                    /* BD key on/off */
                    if ((v & 0x10) != 0) {
                        FM_KEYON(opl.P_CH[6].SLOT[SLOT1], 2);
                        FM_KEYON(opl.P_CH[6].SLOT[SLOT2], 2);
                    } else {
                        FM_KEYOFF(opl.P_CH[6].SLOT[SLOT1], ~(int) 2);
                        FM_KEYOFF(opl.P_CH[6].SLOT[SLOT2], ~(int) 2);
                    }
                    /* HH key on/off */
                    if ((v & 0x01) != 0) FM_KEYON(opl.P_CH[7].SLOT[SLOT1], 2);
                    else FM_KEYOFF(opl.P_CH[7].SLOT[SLOT1], ~(int) 2);
                    /* SD key on/off */
                    if ((v & 0x08) != 0) FM_KEYON(opl.P_CH[7].SLOT[SLOT2], 2);
                    else FM_KEYOFF(opl.P_CH[7].SLOT[SLOT2], ~(int) 2);
                    /* TOM key on/off */
                    if ((v & 0x04) != 0) FM_KEYON(opl.P_CH[8].SLOT[SLOT1], 2);
                    else FM_KEYOFF(opl.P_CH[8].SLOT[SLOT1], ~(int) 2);
                    /* TOP-CY key on/off */
                    if ((v & 0x02) != 0) FM_KEYON(opl.P_CH[8].SLOT[SLOT2], 2);
                    else FM_KEYOFF(opl.P_CH[8].SLOT[SLOT2], ~(int) 2);
                } else {
                    /* BD key off */
                    FM_KEYOFF(opl.P_CH[6].SLOT[SLOT1], ~(int) 2);
                    FM_KEYOFF(opl.P_CH[6].SLOT[SLOT2], ~(int) 2);
                    /* HH key off */
                    FM_KEYOFF(opl.P_CH[7].SLOT[SLOT1], ~(int) 2);
                    /* SD key off */
                    FM_KEYOFF(opl.P_CH[7].SLOT[SLOT2], ~(int) 2);
                    /* TOM key off */
                    FM_KEYOFF(opl.P_CH[8].SLOT[SLOT1], ~(int) 2);
                    /* TOP-CY off */
                    FM_KEYOFF(opl.P_CH[8].SLOT[SLOT2], ~(int) 2);
                }
                return;
            }
            /* keyon,block,fnum */
            if ((r & 0x0f) > 8) return;
            ch = opl.P_CH[r & 0x0f];
            if ((r & 0x10) == 0) {   /* a0-a8 */
                block_fnum = (ch.block_fnum & 0x1f00) | (int) v;
            } else {   /* b0-b8 */
                block_fnum = ((v & 0x1f) << 8) | (ch.block_fnum & 0xff);

                if ((v & 0x20) != 0) {
                    FM_KEYON(ch.SLOT[SLOT1], 1);
                    FM_KEYON(ch.SLOT[SLOT2], 1);
                } else {
                    FM_KEYOFF(ch.SLOT[SLOT1], ~(int) 1);
                    FM_KEYOFF(ch.SLOT[SLOT2], ~(int) 1);
                }
            }
            /* update */
            if (ch.block_fnum != block_fnum) {
                byte block = (byte) (block_fnum >> 10);

                ch.block_fnum = block_fnum;

                ch.ksl_base = ksl_tab[block_fnum >> 6];
                ch.fc = opl.fn_tab[block_fnum & 0x03ff] >> (7 - block);

                /* BLK 2,1,0 bits . bits 3,2,1 of kcode */
                ch.kcode = (byte) ((ch.block_fnum & 0x1c00) >> 9);

                /* the info below is actually opposite to what is stated in the Manuals (verifed on real YM3812) */
                /* if notesel == 0 . lsb of kcode is bit 10 (MSB) of fnum  */
                /* if notesel == 1 . lsb of kcode is bit 9 (MSB-1) of fnum */
                if ((opl.mode & 0x40) != 0)
                    ch.kcode |= (byte) ((ch.block_fnum & 0x100) >> 8); /* notesel == 1 */
                else
                    ch.kcode |= (byte) ((ch.block_fnum & 0x200) >> 9); /* notesel == 0 */

                /* refresh Total Level in both SLOTs of this channel */
                ch.SLOT[SLOT1].tll = ch.SLOT[SLOT1].TL + (ch.ksl_base >> ch.SLOT[SLOT1].ksl);
                ch.SLOT[SLOT2].tll = ch.SLOT[SLOT2].TL + (ch.ksl_base >> ch.SLOT[SLOT2].ksl);

                /* refresh frequency counter in both SLOTs of this channel */
                CALC_FCSLOT(ch, ch.SLOT[SLOT1]);
                CALC_FCSLOT(ch, ch.SLOT[SLOT2]);
            }
            break;
        case 0xc0:
            /* FB,C */
            if ((r & 0x0f) > 8) return;
            ch = opl.P_CH[r & 0x0f];
            ch.SLOT[SLOT1].fb = (byte) (((v >> 1) & 7) != 0 ? ((v >> 1) & 7) + 7 : 0);
            ch.SLOT[SLOT1].con = (byte) (v & 1);
            //ch.SLOT[SLOT1].connect1 = (int)(ch.SLOT[SLOT1].CON != 0 ? opl.output[0] : opl.phase_modulation);
            ch.SLOT[SLOT1].ptrConnect1 = ch.SLOT[SLOT1].con != 0 ? 0 : 1;
            break;
        case 0xe0: /* waveform select */
            /* simply ignore write to the waveform select register if selecting not enabled in test register */
            if (opl.wavesel != 0) {
                slot = slot_array[r & 0x1f];
                if (slot < 0) return;
                ch = opl.P_CH[slot / 2];

                ch.SLOT[slot & 1].wavetable = (int) ((v & 0x03) * SIN_LEN);
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
    private int OPL_LockTable() {
        num_lock++;
        if (num_lock > 1) return 0;

        /* first time */

        /* allocate total level table (128kb space) */
        if (init_tables() == 0) {
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

    private void OPL_UnLockTable() {
        if (num_lock != 0) num_lock--;
        if (num_lock != 0) return;

        /* last time */

        OPLCloseTable();

            /*if (cymfile)
                fclose (cymfile);
            cymfile = NULL;*/
    }

    private void OPLResetChip(FM_OPL opl) {
        int c, s;
        int i;

        opl.eg_timer = 0;
        opl.eg_cnt = 0;

        opl.noise_rng = 1; /* noise shift register */
        opl.mode = 0;  /* normal mode */
        OPL_STATUS_RESET(opl, 0x7f);

        /* reset with register write */
        OPLWriteReg(opl, 0x01, 0); /* wavesel disable */
        OPLWriteReg(opl, 0x02, 0); /* Timer1 */
        OPLWriteReg(opl, 0x03, 0); /* Timer2 */
        OPLWriteReg(opl, 0x04, 0); /* IRQ mask clear */
        for (i = 0xff; i >= 0x20; i--) OPLWriteReg(opl, i, 0);

        /* reset Operator parameters */
        for (c = 0; c < 9; c++) {
            OPL_CH CH = opl.P_CH[c];
            for (s = 0; s < 2; s++) {
                /* wave table */
                CH.SLOT[s].wavetable = 0;
                CH.SLOT[s].state = EG_OFF;
                CH.SLOT[s].volume = MAX_ATT_INDEX;
            }
        }
        //#if BUILD_Y8950
        if ((opl.type & OPL_TYPE_ADPCM) != 0) {
            YM_DELTAT DELTAT = opl.deltaT;

            DELTAT.freqbase = opl.freqbase;
            DELTAT.output_pointer = opl.output_deltat;
            DELTAT.ptrOutput_pointer = 0;
            DELTAT.portshift = 5;
            DELTAT.output_range = 1 << 23;
            YM_DELTAT_ADPCM_Reset(DELTAT, 0, YM_DELTAT_EMULATION_MODE_NORMAL);
        }
        //#endif
    }


    //#if 0
    ////static STATE_POSTLOAD( OPL_postload )
    //static void OPL_postload(void* param)
    //{
    // FM_OPL *OPL = (FM_OPL *)param;
    // int slot, ch;

    // for( ch=0 ; ch < 9 ; ch++ )
    // {
    //  OPL_CH *CH = OPL.P_CH[ch];

    //  /* Look up key scale level */
    //  (int)32 block_fnum = CH.block_fnum;
    //  CH.ksl_base = ksl_tab[block_fnum >> 6];
    //  CH.fc       = OPL.fn_tab[block_fnum & 0x03ff] >> (7 - (block_fnum >> 10));

    //  for( slot=0 ; slot < 2 ; slot++ )
    //  {
    //   OPL_SLOT *SLOT = CH.SLOT[slot];

    //   /* Calculate key scale rate */
    //   SLOT.ksr = CH.kcode >> SLOT.KSR;

    //   /* Calculate attack, decay and release rates */
    //   if ((SLOT.ar + SLOT.ksr) < 16+62)
    //   {
    //    SLOT.eg_sh_ar  = eg_rate_shift [SLOT.ar + SLOT.ksr ];
    //    SLOT.eg_sel_ar = eg_rate_select[SLOT.ar + SLOT.ksr ];
    //   }
    //   else
    //   {
    //    SLOT.eg_sh_ar  = 0;
    //    SLOT.eg_sel_ar = 13*RATE_STEPS;
    //   }
    //   SLOT.eg_sh_dr  = eg_rate_shift [SLOT.dr + SLOT.ksr ];
    //   SLOT.eg_sel_dr = eg_rate_select[SLOT.dr + SLOT.ksr ];
    //   SLOT.eg_sh_rr  = eg_rate_shift [SLOT.rr + SLOT.ksr ];
    //   SLOT.eg_sel_rr = eg_rate_select[SLOT.rr + SLOT.ksr ];

    //   /* Calculate phase increment */
    //   SLOT.Incr = CH.fc * SLOT.mul;

    //   /* Total level */
    //   SLOT.TLL = SLOT.TL + (CH.ksl_base >> SLOT.ksl);

    //   /* Connect output */
    //   SLOT.connect1 = SLOT.CON ? OPL.output[0] : OPL.phase_modulation;
    //  }
    // }
    //#if BUILD_Y8950
    // if ( (OPL.type & OPL_TYPE_ADPCM) && (OPL.deltat) )
    // {
    //  // We really should call the postlod function for the YM_DELTAT, but it's hard without registers
    //  // (see the way the YM2610 does it)
    //  //YM_DELTAT_postload(OPL.deltat, REGS);
    // }
    //#endif
    //}


    //static void OPLsave_state_channel(OPL_CH *CH)
    //{
    // int slot, ch;

    // for( ch=0 ; ch < 9 ; ch++, CH++ )
    // {
    //  // channel
    //  state_save_register_device_item(device, ch, CH.block_fnum);
    //  state_save_register_device_item(device, ch, CH.kcode);
    //  // slots
    //  for( slot=0 ; slot < 2 ; slot++ )
    //  {
    //   OPL_SLOT *SLOT = CH.SLOT[slot];

    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.ar);
    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.dr);
    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.rr);
    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.KSR);
    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.ksl);
    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.mul);

    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.Cnt);
    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.FB);
    //   state_save_register_device_item_array(device, ch * 2 + slot, SLOT.op1_out);
    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.CON);

    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.eg_type);
    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.state);
    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.TL);
    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.volume);
    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.sl);
    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.key);

    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.AMmask);
    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.vib);

    //   state_save_register_device_item(device, ch * 2 + slot, SLOT.wavetable);
    //  }
    // }
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


    /**
     * Create one of virtual YM3812/YM3526/Y8950
     * @param clock is chip clock in Hz
     * @param rate  is sampling rate
     */
    private FM_OPL OPLCreate(int clock, int rate, int type) {
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
        OPL.type = (byte) type;
        OPL.clock = clock;
        OPL.rate = rate;

        /* init Global tables */
        OPL_initalize(OPL);

        return OPL;
    }

    /* Destroy one of virtual YM3812 */
    private void OPLDestroy(FM_OPL opl) {
        OPL_UnLockTable();
        //free(opl);
    }

    /* Optional handlers */

    private void OPLSetTimerHandler(FM_OPL opl, OPL_TIMERHANDLER timer_handler, Object param) {
        opl.timer_handler = timer_handler;
        opl.TimerParam = param;
    }

    private void OPLSetIRQHandler(FM_OPL opl, OPL_IRQHANDLER IRQHandler, y8950_state param) {
        opl.irqHandler = IRQHandler;
        opl.irqParam = param;
    }

    private void OPLSetUpdateHandler(FM_OPL opl, OPL_UPDATEHANDLER UpdateHandler, y8950_state param) {
        opl.updateHandler = UpdateHandler;
        opl.updateParam = param;
    }

    private int OPLWrite(FM_OPL opl, int a, int v) {
        if ((a & 1) == 0) {   /* address port */
            opl.address = (byte) (v & 0xff);
        } else {   /* data port */
            if (opl.updateHandler != null) opl.updateHandler.accept(opl.updateParam, 0);
            OPLWriteReg(opl, opl.address, v);
        }
        return opl.status >> 7;
    }

    private byte OPLRead(FM_OPL opl, int a) {
        if ((a & 1) == 0) {
            /* status port */

            //#if BUILD_Y8950

            if ((opl.type & OPL_TYPE_ADPCM) != 0) /* Y8950 */ {
                return (byte) ((opl.status & (opl.statusmask | 0x80)) | (opl.deltaT.PCM_BSY & 1));
            }

            //#endif

            /* opl and OPL2 */
            return (byte) (opl.status & (opl.statusmask | 0x80));
        }

        //#if BUILD_Y8950
        /* data port */
        switch (opl.address) {
        case 0x05: /* KeyBoard IN */
            if ((opl.type & OPL_TYPE_KEYBOARD) != 0) {
                if (opl.keyboardhandler_r != null)
                    return opl.keyboardhandler_r.apply(opl.keyboard_param);
                //# ifdef _DEBUG
                //else
                //logerror("Y8950: read unmapped KEYBOARD port\n");
                //#endif
            }
            return 0;

        case 0x0f: /* ADPCM-DATA  */
            if ((opl.type & OPL_TYPE_ADPCM) != 0) {
                byte val;

                val = YM_DELTAT_ADPCM_Read(opl.deltaT);
                /*logerror("Y8950: read ADPCM value read=%02x\n",val);*/
                return val;
            }
            return 0;

        case 0x19: /* I/O DATA    */
            if ((opl.type & OPL_TYPE_IO) != 0) {
                if (opl.porthandler_r != null)
                    return opl.porthandler_r.apply(opl.port_param);
                //# ifdef _DEBUG
                //else
                //logerror("Y8950:read unmapped I/O port\n");
                //#endif
            }
            return 0;
        case 0x1a: /* PCM-DATA    */
            if ((opl.type & OPL_TYPE_ADPCM) != 0) {
                //# ifdef _DEBUG
                //logerror("Y8950 A/D conversion is accessed but not implemented !\n");
                //#endif
                return (byte) 0x80; /* 2's complement PCM data - result from A/D conversion */
            }
            return 0;
        }
        //#endif

        return (byte) 0xff;
    }

    /* CSM Key Controll */
    private void csmKeyControll(OPL_CH ch) {
        FM_KEYON(ch.SLOT[SLOT1], 4);
        FM_KEYON(ch.SLOT[SLOT2], 4);

        /* The key off should happen exactly one sample later - not implemented correctly yet */

        FM_KEYOFF(ch.SLOT[SLOT1], ~(int) 4);
        FM_KEYOFF(ch.SLOT[SLOT2], ~(int) 4);
    }


    private int oplTimerOver(FM_OPL opl, int c) {
        if (c != 0) {   /* Timer B */
            OPL_STATUS_SET(opl, 0x20);
        } else {   /* Timer A */
            OPL_STATUS_SET(opl, 0x40);
            /* CSM mode key,TL controll */
            if ((opl.mode & 0x80) != 0) {   /* CSM mode total level latch and auto key on */
                int ch;
                if (opl.updateHandler != null) opl.updateHandler.accept(opl.updateParam, 0);
                for (ch = 0; ch < 9; ch++)
                    csmKeyControll(opl.P_CH[ch]);
            }
        }
        /* reload timer */
        //if (opl.timer_handler) (opl.timer_handler)(opl.TimerParam,c,attotime_mul(opl.TimerBase, opl.T[c]));
        return opl.status >> 7;
    }


    private static final int MAX_OPL_CHIPS = 2;


    //#if (BUILD_YM3812)

    private FM_OPL ym3812_init(int clock, int rate) {
        /* emulator create */
        FM_OPL ym3812 = OPLCreate(clock, rate, OPL_TYPE_YM3812);
        if (ym3812 != null) {
            //OPL_save_state(ym3812);
            ym3812_reset_chip(ym3812);
        }
        return ym3812;
    }

    private void ym3812_shutdown(FM_OPL chip) {
        FM_OPL ym3812 = chip;
        /* emulator shutdown */
        OPLDestroy(ym3812);
    }

    private void ym3812_reset_chip(FM_OPL chip) {
        FM_OPL ym3812 = chip;
        OPLResetChip(ym3812);
    }

    private int ym3812_write(FM_OPL chip, int a, int v) {
        FM_OPL ym3812 = chip;
        return OPLWrite(ym3812, a, v);
    }

    private byte ym3812_read(FM_OPL chip, int a) {
        FM_OPL ym3812 = chip;
        /* ym3812 always returns bit2 and bit1 in HIGH state */
        return (byte) (OPLRead(ym3812, a) | 0x06);
    }

    private int ym3812_timer_over(FM_OPL chip, int c) {
        FM_OPL ym3812 = chip;
        return oplTimerOver(ym3812, c);
    }

    private void ym3812_set_timer_handler(FM_OPL chip, OPL_TIMERHANDLER timer_handler, Object param) {
        FM_OPL ym3812 = chip;
        OPLSetTimerHandler(ym3812, timer_handler, param);
    }

    private void ym3812_set_irq_handler(FM_OPL chip, OPL_IRQHANDLER irqHandler, y8950_state param) {
        FM_OPL ym3812 = chip;
        OPLSetIRQHandler(ym3812, irqHandler, param);
    }

    private void ym3812_set_update_handler(FM_OPL chip, OPL_UPDATEHANDLER updateHandler, y8950_state param) {
        FM_OPL ym3812 = chip;
        OPLSetUpdateHandler(ym3812, updateHandler, param);
    }


    /**
     * Generate samples for one of the YM3812's
     *
     * @param chip is the virtual YM3812 number
     * @param buffer is the output buffer pointer
     * @param length is the number of samples that should be generated
     */
    private void ym3812_update_one(FM_OPL chip, int[][] buffer, int length) {
        FM_OPL OPL = chip;
        byte rhythm = (byte) (OPL.rhythm & 0x20);
        int[] bufL = buffer[0];
        int[] bufR = buffer[1];
        int i;

        if (length == 0) {
            refresh_eg(OPL);
            return;
        }

        for (i = 0; i < length; i++) {
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

            if (rhythm == 0) {
                OPL_CALC_CH(OPL, OPL.P_CH[6]);
                OPL_CALC_CH(OPL, OPL.P_CH[7]);
                OPL_CALC_CH(OPL, OPL.P_CH[8]);
            } else        /* Rhythm part */ {
                OPL_CALC_RH(OPL, OPL.P_CH, (OPL.noise_rng >> 0) & 1);
            }

            lt = OPL.output[0];

            lt >>= FINAL_SH;

            /* limit check */
            //lt = limit( lt , MAXOUT, MINOUT );

            //# ifdef SAVE_SAMPLE
            //if (which == 0)
            //{
            //SAVE_ALL_CHANNELS

            //}
            //#endif

            /* store to Sound buffer */
            bufL[i] = lt;
            bufR[i] = lt;

            advance(OPL);
        }

    }
    //#endif /* BUILD_YM3812 */


    //#if (BUILD_YM3526)

    //void* ym3526_init(int clock, int rate)
    //{
    //    /* emulator create */
    //    FM_OPL* YM3526 = OPLCreate(clock, rate, OPL_TYPE_YM3526);
    //    if (YM3526)
    //    {
    //        //OPL_save_state(YM3526);
    //        ym3526_reset_chip(YM3526);
    //    }
    //    return YM3526;
    //}

    //void ym3526_shutdown(void* chip)
    //{
    //    FM_OPL* YM3526 = (FM_OPL*)chip;
    //    /* emulator shutdown */
    //    OPLDestroy(YM3526);
    //}
    //void ym3526_reset_chip(void* chip)
    //{
    //    FM_OPL* YM3526 = (FM_OPL*)chip;
    //    OPLResetChip(YM3526);
    //}

    //int ym3526_write(void* chip, int a, int v)
    //{
    //    FM_OPL* YM3526 = (FM_OPL*)chip;
    //    return OPLWrite(YM3526, a, v);
    //}

    //unsigned char ym3526_read(void* chip, int a)
    //{
    //    FM_OPL* YM3526 = (FM_OPL*)chip;
    //    /* YM3526 always returns bit2 and bit1 in HIGH state */
    //    return OPLRead(YM3526, a) | 0x06;
    //}
    //int ym3526_timer_over(void* chip, int c)
    //{
    //    FM_OPL* YM3526 = (FM_OPL*)chip;
    //    return OPLTimerOver(YM3526, c);
    //}

    //void ym3526_set_timer_handler(void* chip, OPL_TIMERHANDLER timer_handler, void* param)
    //{
    //    FM_OPL* YM3526 = (FM_OPL*)chip;
    //    OPLSetTimerHandler(YM3526, timer_handler, param);
    //}
    //void ym3526_set_irq_handler(void* chip, OPL_IRQHANDLER IRQHandler, void* param)
    //{
    //    FM_OPL* YM3526 = (FM_OPL*)chip;
    //    OPLSetIRQHandler(YM3526, IRQHandler, param);
    //}
    //void ym3526_set_update_handler(void* chip, OPL_UPDATEHANDLER UpdateHandler, void* param)
    //{
    //    FM_OPL* YM3526 = (FM_OPL*)chip;
    //    OPLSetUpdateHandler(YM3526, UpdateHandler, param);
    //}


    ///*
    //** Generate samples for one of the YM3526's
    //**
    //** 'which' is the virtual YM3526 number
    //** '*buffer' is the output buffer pointer
    //** 'length' is the number of samples that should be generated
    //*/
    //void ym3526_update_one(void* chip, OPLSAMPLE** buffer, int length)
    //{
    //    FM_OPL* OPL = (FM_OPL*)chip;
    //    byte rhythm = OPL.rhythm & 0x20;
    //    OPLSAMPLE* bufL = buffer[0];
    //    OPLSAMPLE* bufR = buffer[1];
    //    int i;

    //    for (i = 0; i < length; i++)
    //    {
    //        int lt;

    //        OPL.output[0] = 0;

    //        advance_lfo(OPL);

    //        /* FM part */
    //        OPL_CALC_CH(OPL, OPL.P_CH[0]);
    //        OPL_CALC_CH(OPL, OPL.P_CH[1]);
    //        OPL_CALC_CH(OPL, OPL.P_CH[2]);
    //        OPL_CALC_CH(OPL, OPL.P_CH[3]);
    //        OPL_CALC_CH(OPL, OPL.P_CH[4]);
    //        OPL_CALC_CH(OPL, OPL.P_CH[5]);

    //        if (!rhythm)
    //        {
    //            OPL_CALC_CH(OPL, OPL.P_CH[6]);
    //            OPL_CALC_CH(OPL, OPL.P_CH[7]);
    //            OPL_CALC_CH(OPL, OPL.P_CH[8]);
    //        }
    //        else        /* Rhythm part */
    //        {
    //            OPL_CALC_RH(OPL, OPL.P_CH[0], (OPL.noise_rng >> 0) & 1);
    //        }

    //        lt = OPL.output[0];

    //        lt >>= FINAL_SH;

    //        /* limit check */
    //        //lt = limit( lt , MAXOUT, MINOUT );

    //# ifdef SAVE_SAMPLE
    //        if (which == 0)
    //        {
    //            SAVE_ALL_CHANNELS

    //        }
    //#endif

    //        /* store to Sound buffer */
    //        bufL[i] = lt;
    //        bufR[i] = lt;

    //        advance(OPL);
    //    }

    //}
    //#endif /* BUILD_YM3526 */


    //#if BUILD_Y8950

    private void Y8950_deltat_status_set(FM_OPL chip, byte changebits) {
        FM_OPL Y8950 = chip;
        OPL_STATUS_SET(Y8950, changebits);
    }

    private void Y8950_deltat_status_reset(FM_OPL chip, byte changebits) {
        FM_OPL Y8950 = chip;
        OPL_STATUS_RESET(Y8950, changebits);
    }

    public FM_OPL y8950_init(int clock, int rate) {
        /* emulator create */
        FM_OPL Y8950 = OPLCreate(clock, rate, OPL_TYPE_Y8950);
        if (Y8950 != null) {
            Y8950.deltaT.memory = null;
            Y8950.deltaT.memory_size = 0x00;
            Y8950.deltaT.memory_mask = 0x00;

            Y8950.deltaT.status_set_handler = this::Y8950_deltat_status_set;
            Y8950.deltaT.status_reset_handler = this::Y8950_deltat_status_reset;
            Y8950.deltaT.status_change_which_chip = Y8950;
            Y8950.deltaT.status_change_EOS_bit = 0x10;        /* status flag: set bit4 on End Of Sample */
            Y8950.deltaT.status_change_BRDY_bit = 0x08;   /* status flag: set bit3 on BRDY (End Of: ADPCM analysis/synthesis, memory reading/writing) */

            /*Y8950.deltat.write_time = 10.0 / clock;*/        /* a single byte write takes 10 cycles of main clock */
            /*Y8950.deltat.read_time  = 8.0 / clock;*/       /* a single byte read takes 8 cycles of main clock */
            /* reset */
            //OPL_save_state(Y8950);
            y8950_reset_chip(Y8950);
        }

        return Y8950;
    }

    private void y8950_shutdown(FM_OPL chip) {
        FM_OPL Y8950 = chip;

        //free(Y8950.deltat.memory);
        Y8950.deltaT.memory = null;

        /* emulator shutdown */
        OPLDestroy(Y8950);
    }

    private void y8950_reset_chip(FM_OPL chip) {
        FM_OPL y8950 = chip;
        OPLResetChip(y8950);
    }

    public int y8950_write(FM_OPL chip, int a, int v) {
        FM_OPL y8950 = chip;
        return OPLWrite(y8950, a, v);
    }

    private byte y8950_read(FM_OPL chip, int a) {
        FM_OPL Y8950 = chip;
        return OPLRead(Y8950, a);
    }

    private int y8950_timer_over(FM_OPL chip, int c) {
        FM_OPL Y8950 = (FM_OPL) chip;
        return oplTimerOver(Y8950, c);
    }

    public void y8950_set_timer_handler(FM_OPL chip, OPL_TIMERHANDLER timer_handler, Object param) {
        FM_OPL Y8950 = chip;
        OPLSetTimerHandler(Y8950, timer_handler, param);
    }

    private void y8950_set_irq_handler(FM_OPL chip, OPL_IRQHANDLER irqHandler, y8950_state param) {
        FM_OPL Y8950 = chip;
        OPLSetIRQHandler(Y8950, irqHandler, param);
    }

    private void y8950_set_update_handler(FM_OPL chip, OPL_UPDATEHANDLER updatehandler, y8950_state param) {
        FM_OPL Y8950 = chip;
        OPLSetUpdateHandler(Y8950, updatehandler, param);
    }

    private void y8950_set_delta_t_memory(FM_OPL chip, byte[] deltat_mem_ptr, int deltat_mem_size) {
        FM_OPL opl = chip;
        opl.deltaT.memory = deltat_mem_ptr;
        opl.deltaT.memory_size = (int) deltat_mem_size;
    }

    private void y8950_write_pcmrom(FM_OPL chip, int romSize, int dataStart, int dataLength, byte[] romData) {
        FM_OPL y8950 = chip;

        if (y8950.deltaT.memory_size != romSize) {
            y8950.deltaT.memory = new byte[romSize];// (byte*)realloc(y8950.deltat.memory, romSize);
            y8950.deltaT.memory_size = (int) romSize;
            for (int i = 0; i < romSize; i++) y8950.deltaT.memory[i] = (byte) 0xff;
            //memset(y8950.deltat.memory, 0xFF, romSize);
            YM_DELTAT_calc_mem_mask(y8950.deltaT);
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        for (int i = 0; i < dataLength; i++) y8950.deltaT.memory[i + dataStart] = romData[i];
        //memcpy(y8950.deltat.memory + dataStart, romData, dataLength);
    }

    private void y8950_write_pcmrom(FM_OPL chip, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        FM_OPL Y8950 = chip;

        if (Y8950.deltaT.memory_size != romSize) {
            Y8950.deltaT.memory = new byte[romSize];// (byte*)realloc(Y8950.deltat.memory, romSize);
            Y8950.deltaT.memory_size = (int) romSize;
            for (int i = 0; i < romSize; i++) Y8950.deltaT.memory[i] = (byte) 0xff;
            //memset(Y8950.deltat.memory, 0xFF, romSize);
            YM_DELTAT_calc_mem_mask(Y8950.deltaT);
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        if (dataLength >= 0)
            System.arraycopy(romData, 0 + srcStartAddress, Y8950.deltaT.memory, 0 + dataStart, dataLength);
        //memcpy(Y8950.deltat.memory + dataStart, romData, dataLength);
    }

    //long cnt = 0;

    /**
     * Generate samples for one of the Y8950's
     *
     * 'which' is the virtual Y8950 number
     * '*buffer' is the output buffer pointer
     * 'length' is the number of samples that should be generated
     */
    private void y8950_update_one(FM_OPL chip, int[][] buffer, int length) {
        int i;
        FM_OPL opl = chip;
        byte rhythm = (byte) (opl.rhythm & 0x20);
        YM_DELTAT deltaT = opl.deltaT;
        int[] bufL = buffer[0];
        int[] bufR = buffer[1];

        for (i = 0; i < length; i++) {
            //System.System.err.printf("clock={0}:rate={1}:freqbase={2}:cnt={3}", chip.clock, chip.rate, chip.freqbase, cnt++);
            int lt;

            opl.output[0] = 0;
            opl.output_deltat[0] = 0;

            advance_lfo(opl);

            /* deltaT ADPCM */
            if ((deltaT.portstate & 0x80) != 0 && opl.MuteSpc[5] == 0)
                YM_DELTAT_ADPCM_CALC(deltaT);

            /* FM part */
            OPL_CALC_CH(opl, opl.P_CH[0]);
            //System.System.err.printf("P_CH[0] opl.output[0]={0}", opl.output[0]);
            OPL_CALC_CH(opl, opl.P_CH[1]);
            //System.System.err.printf("P_CH[1] opl.output[0]={0}", opl.output[0]);
            OPL_CALC_CH(opl, opl.P_CH[2]);
            //System.System.err.printf("P_CH[2] opl.output[0]={0}", opl.output[0]);
            OPL_CALC_CH(opl, opl.P_CH[3]);
            //System.System.err.printf("P_CH[3] opl.output[0]={0}", opl.output[0]);
            OPL_CALC_CH(opl, opl.P_CH[4]);
            //System.System.err.printf("P_CH[4] opl.output[0]={0} {1} {2}", opl.output[0], opl.P_CH[4].SLOT[SLOT1].op1_out[0], opl.P_CH[4].SLOT[SLOT1].op1_out[1]);
            OPL_CALC_CH(opl, opl.P_CH[5]);
            //System.System.err.printf("P_CH[5] opl.output[0]={0}", opl.output[0]);

            if (rhythm == 0) {
                OPL_CALC_CH(opl, opl.P_CH[6]);
                //System.System.err.printf("P_CH[6] opl.output[0]={0}", opl.output[0]);
                OPL_CALC_CH(opl, opl.P_CH[7]);
                //System.System.err.printf("P_CH[7] opl.output[0]={0}", opl.output[0]);
                OPL_CALC_CH(opl, opl.P_CH[8]);
                //System.System.err.printf("P_CH[8] opl.output[0]={0}", opl.output[0]);
            } else        /* Rhythm part */ {
                OPL_CALC_RH(opl, opl.P_CH, (opl.noise_rng >> 0) & 1);
                //System.System.err.printf("P_CH[0R] opl.output[0]={0}", opl.output[0]);
            }

            lt = opl.output[0] + (opl.output_deltat[0] >> 11);
                /*System.System.err.printf("opl.output_deltat[0]={0} acc={1} now_step={2} adpcmd={3}"
                    , opl.output_deltat[0]
                    ,deltaT.acc
                    ,deltaT.now_step
                    , deltaT.adpcmd
                    );
                    */
            lt >>= FINAL_SH;

            /* limit check */
            //lt = limit( lt , MAXOUT, MINOUT );

            //# ifdef SAVE_SAMPLE
            //if (which == 0)
            //{
            //SAVE_ALL_CHANNELS

            //}
            //#endif

            /* store to Sound buffer */
            bufL[i] = lt;
            bufR[i] = lt;

            advance(opl);
        }
    }

    private void y8950_set_port_handler(FM_OPL chip, OPL_PORTHANDLER_W portHandlerW, OPL_PORTHANDLER_R portHandlerR, y8950_state param) {
        FM_OPL opl = chip;
        opl.porthandler_w = portHandlerW;
        opl.porthandler_r = portHandlerR;
        opl.port_param = param;
    }

    private void y8950_set_keyboard_handler(FM_OPL chip, OPL_PORTHANDLER_W KeyboardHandler_w, OPL_PORTHANDLER_R KeyboardHandler_r, y8950_state param) {
        FM_OPL opl = chip;
        opl.keyboardhandler_w = KeyboardHandler_w;
        opl.keyboardhandler_r = KeyboardHandler_r;
        opl.keyboard_param = param;
    }

    //#endif

    private void opl_set_mute_mask(FM_OPL chip, int muteMask) {
        FM_OPL opl = chip;
        byte curChn;

        for (curChn = 0; curChn < 9; curChn++)
            opl.P_CH[curChn].Muted = (byte) ((muteMask >> curChn) & 0x01);
        for (curChn = 0; curChn < 6; curChn++)
            opl.MuteSpc[curChn] = (byte) ((muteMask >> (9 + curChn)) & 0x01);
    }
}
