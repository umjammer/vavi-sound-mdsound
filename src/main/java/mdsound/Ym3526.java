package mdsound;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import mdsound.Common.TriConsumer;


public class Ym3526 extends Instrument.BaseInstrument {

    @Override
    public String getName() {
        return "YM3526";
    }

    @Override
    public String getShortName() {
        return "OPL";
    }

    @Override
    public void reset(byte chipID) {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };

        device_reset_ym3526(chipID);
    }

    private static final int DefaultYM3526ClockValue = 3579545;

    @Override
    public int start(byte chipID, int clock) {
        return device_start_ym3526(chipID, DefaultYM3526ClockValue);
    }

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        return device_start_ym3526(chipID, clockValue);
    }

    @Override
    public void stop(byte chipID) {
        device_stop_ym3526(chipID);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        ym3526_stream_update(chipID, outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        ym3526_w(chipID, 0x00, (byte) adr);
        ym3526_w(chipID, 0x01, (byte) data);
        return 0;
    }

    /**
     * Yamaha 3812 emulator interface - MAME VERSION
     *
     * CREATED BY
     *   Ernesto Corvi
     *
     * UPDATE LOG
     *   JB  28-04-2002  Fixed simultaneous usage of all three different chip types.
     *                       Used real sample rate when resample filter is active.
     *   AAT 12-28-2001  Protected Y8950 from accessing unmapped port and keyboard handlers.
     *   CHS 1999-01-09  Fixes new Ym3812 emulation interface.
     *   CHS 1998-10-23  Mame streaming Sound chip update
     *   EC  1998        Created Interface
     *
     * NOTES
     */
    static class ym3526_state {
        //sound_stream * stream;
        //emu_timer *  timer[2];
        public FM_OPL chip;
        //final ym3526_interface *intf;
        //final DeviceConfig *device;
    }

    private static byte CHIP_SAMPLING_MODE = 0;
    //private int CHIP_SAMPLE_RATE;
    private static final int MAX_CHIPS = 0x02;
    static ym3526_state[] ym3526Data = new ym3526_state[] {new ym3526_state(), new ym3526_state()};

        /*INLINE ym3526_state *get_safe_token(final DeviceConfig *device)
        {
            assert(device != NULL);
            assert(device.token != NULL);
            assert(device.type == Sound);
            assert(sound_get_type(device) == SOUND_YM3526);
            return (ym3526_state *)device.token;
        }*/


    /* IRQ Handler */
    private void IRQHandler(ym3526_state param, int irq) {
        ym3526_state info = param;
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

    /* TimerHandler from Fm.c */
    private void TimerHandler(ym3526_state param, int c, int period) {
        ym3526_state info = param;
        //if( attotime_compare(period, attotime_zero) == 0 )
        if (period == 0) {   /* Reset FM Timer */
            //timer_enable(info.timer[c], 0);
        } else {   /* Start FM Timer */
            //timer_adjust_oneshot(info.timer[c], period, 0);
        }
    }

    public void ym3526_stream_update(byte chipID, int[][] outputs, int samples) {
        ym3526_state info = ym3526Data[chipID];
        info.chip.ym3526_update_one(outputs, samples);
    }

    private int[][] DUMMYBUF = new int[][] {null, null};

    private void _stream_update(ym3526_state param/*, int interval*/) {
        ym3526_state info = param;
        //stream_update(info.stream);

        info.chip.ym3526_update_one(DUMMYBUF, 0);
    }

    //static DEVICE_START( Ym3526 )
    public int device_start_ym3526(byte chipID, int clock) {
        //static final ym3526_interface dummy = { 0 };
        //ym3526_state *info = get_safe_token(device);
        ym3526_state info;
        int rate;

        if (chipID >= MAX_CHIPS)
            return 0;

        info = ym3526Data[chipID];
        rate = (clock & 0x7FFFFFFF) / 72;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;
        //info.intf = device.static_config ? (final ym3526_interface *)device.static_config : &dummy;
        //info.intf = &dummy;
        //info.device = device;

        /* stream system initialize */
        info.chip = new FM_OPL(clock, rate);
        //assert_always(info.chip != NULL, "Error creating YM3526 chip");

        //info.stream = stream_create(device,0,1,rate,info,ym3526_stream_update);
        /* YM3526 setup */
        info.chip.ym3526_set_timer_handler(this::TimerHandler, info);
        info.chip.ym3526_set_irq_handler(this::IRQHandler, info);
        info.chip.ym3526_set_update_handler(this::_stream_update, info);

        //info.timer[0] = timer_alloc(device.machine, timer_callback_0, info);
        //info.timer[1] = timer_alloc(device.machine, timer_callback_1, info);

        return rate;
    }

    public void device_stop_ym3526(byte chipID) {
        ym3526_state info = ym3526Data[chipID];
        info.chip.ym3526_shutdown();
    }

    public void device_reset_ym3526(byte chipID) {
        ym3526_state info = ym3526Data[chipID];
        info.chip.ym3526_reset_chip();
    }

    public byte ym3526_r(byte chipID, int offset) {
        ym3526_state info = ym3526Data[chipID];
        return info.chip.ym3526_read(offset & 1);
    }

    public void ym3526_w(byte chipID, int offset, byte data) {
        ym3526_state info = ym3526Data[chipID];
        if (info == null || info.chip == null) return;

        info.chip.ym3526_write(offset & 1, data);
    }

    public byte ym3526_status_port_r(byte chipID, int offset) {
        return ym3526_r(chipID, 0);
    }

    public byte ym3526_read_port_r(byte chipID, int offset) {
        return ym3526_r(chipID, 1);
    }

    public void ym3526_control_port_w(byte chipID, int offset, byte data) {
        ym3526_w(chipID, 0, data);
    }

    public void ym3526_write_port_w(byte chipID, int offset, byte data) {
        ym3526_w(chipID, 1, data);
    }

    public void ym3526_set_mute_mask(byte chipID, int muteMask) {
        ym3526_state info = ym3526Data[chipID];
        info.chip.opl_set_mute_mask(muteMask);
    }

    /**
     * Generic get_info
     */
        /*DEVICE_GET_INFO( Ym3526 )
        {
            switch (state)
            {
                // --- the following bits of info are returned as 64-bit signed integers ---
                case DEVINFO_INT_TOKEN_BYTES:     info.i = sizeof(ym3526_state);    break;

                // --- the following bits of info are returned as pointers to data or functions ---
                case DEVINFO_FCT_START:       info.start = DEVICE_START_NAME( Ym3526 );    break;
                case DEVINFO_FCT_STOP:       info.stop = DEVICE_STOP_NAME( Ym3526 );    break;
                case DEVINFO_FCT_RESET:       info.reset = DEVICE_RESET_NAME( Ym3526 );    break;

                // --- the following bits of info are returned as NULL-terminated strings ---
                case DEVINFO_STR_NAME:       strcpy(info.s, "YM3526");       break;
                case DEVINFO_STR_FAMILY:     strcpy(info.s, "Yamaha FM");      break;
                case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");        break;
                case DEVINFO_STR_SOURCE_FILE:      strcpy(info.s, __FILE__);       break;
                case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
            }
        }*/

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
        typedef unsigned char (int)8;   // unsigned  8bit
        typedef unsigned short int;  // unsigned 16bit
        typedef unsigned int (int)32;  // unsigned 32bit
        typedef signed char  INT8;    // signed  8bit
        typedef signed short INT16;   // signed 16bit
        typedef signed int  int;   // signed 32bit
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

    public interface OPL_TIMERHANDLER extends TriConsumer<ym3526_state, Integer, Integer> {
    } // (ym3526_state param, int timer, int period);

    public interface OPL_IRQHANDLER extends BiConsumer<ym3526_state, Integer> {
    } // (ym3526_state param, int irq);

    public interface OPL_UPDATEHANDLER extends Consumer<ym3526_state> {
    } // (ym3526_state param/*,int min_interval_us*/);

    public interface OPL_PORTHANDLER_W extends BiConsumer<ym3526_state, Byte> {
    } // (ym3526_state param, byte data);

    public interface OPL_PORTHANDLER_R extends Function<ym3526_state, Byte> {
    } // (ym3526_state param);

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
    //        void y8950_write_pcmrom(void* chip, offs_t romSize, offs_t dataStart,
    //                                 offs_t dataLength, final UINT8* romData);

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

    //void opl_set_mute_mask(void* chip, UINT32 muteMask);

    // #endregion

    // #region fmopl.c
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

    /* output final shift */
    private static final int FINAL_SH = (0);
    private static final int MAXOUT = 32767;
    private static final int MINOUT = -32768;

    private static final int FREQ_SH = 16;  /* 16.16 fixed point (frequency calculations) */
    private static final int EG_SH = 16;  /* 16.16 fixed point (EG timing)              */
    private static final int LFO_SH = 24;  /*  8.24 fixed point (LFO calculations)       */
    private static final int TIMER_SH = 16;  /* 16.16 fixed point (timers calculations)    */

    private static final int FREQ_MASK = ((1 << FREQ_SH) - 1);

    /* envelope output entries */
    private static final int ENV_BITS = 10;
    private static final int ENV_LEN = (1 << ENV_BITS);
    private static final double ENV_STEP = (128.0 / ENV_LEN);

    private static final int MAX_ATT_INDEX = ((1 << (ENV_BITS - 1)) - 1);/*511*/
    private static final int MIN_ATT_INDEX = (0);

    /* sinwave entries */
    private static final int SIN_BITS = 10;
    private static final int SIN_LEN = (1 << SIN_BITS);
    private static final int SIN_MASK = (SIN_LEN - 1);

    private static final int TL_RES_LEN = (256);/* 8 bits addressing (real chip) */


    /* register number to channel number , slot offset */
    private static final int SLOT1 = 0;
    private static final int SLOT2 = 1;

    /* Envelope Generator phases */

    private static final int EG_ATT = 4;
    private static final int EG_DEC = 3;
    private static final int EG_SUS = 2;
    private static final int EG_REL = 1;
    private static final int EG_OFF = 0;


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
    // #if 1 /*save to MONO file */
    //  #define SAVE_ALL_CHANNELS \
    //  { signed int pom = acc_calc(lt); \
    //   fputc((unsigned short)pom&0xff,sample[0]); \
    //   fputc(((unsigned short)pom>>8)&0xff,sample[0]); \
    //  }
    // #else /*save to STEREO file */
    //  #define SAVE_ALL_CHANNELS \
    //  { signed int pom = lt; \
    //   fputc((unsigned short)pom&0xff,sample[0]); \
    //   fputc(((unsigned short)pom>>8)&0xff,sample[0]); \
    //   pom = rt; \
    //   fputc((unsigned short)pom&0xff,sample[0]); \
    //   fputc(((unsigned short)pom>>8)&0xff,sample[0]); \
    //  }
    //#endif
    //#endif

    //#define LOG_CYM_FILE 0
    //static FILE * cymfile = NULL;


    private static final int OPL_TYPE_WAVESEL = 0x01; /* waveform select     */
    private static final int OPL_TYPE_ADPCM = 0x02; /* DELTA-T ADPCM unit  */
    private static final int OPL_TYPE_KEYBOARD = 0x04; /* keyboard interface  */
    private static final int OPL_TYPE_IO = 0x08; /* I/O port            */

    /* ---------- Generic interface section ---------- */
    private static final int OPL_TYPE_YM3526 = 0;
    private static final int OPL_TYPE_YM3812 = OPL_TYPE_WAVESEL;
    private static final int OPL_TYPE_Y8950 = OPL_TYPE_ADPCM | OPL_TYPE_KEYBOARD | OPL_TYPE_IO;


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
        public int waveTable;
    }

    public static class OPL_CH {

        public OPL_SLOT[] SLOT = new OPL_SLOT[] {new OPL_SLOT(), new OPL_SLOT()};
        /* phase generator state */
        public int block_fnum;  /* block+fnum                   */
        public int fc;          /* Freq. Increment base         */
        public int ksl_base;    /* KeyScaleLevel Base step      */
        public byte kcode;        /* key code (for key scaling)   */
        public byte muted;
    }

    /* OPL state */
    //private FM_OPL fm_opl_f;
    public class FM_OPL {
        /* FM channel slots */
        public OPL_CH[] pCh = new OPL_CH[] {
                new OPL_CH(), new OPL_CH(), new OPL_CH(), new OPL_CH(), new OPL_CH(),
                new OPL_CH(), new OPL_CH(), new OPL_CH(), new OPL_CH()
        };             /* OPL/OPL2 chips have 9 channels*/
        public byte[] muteSpc = new byte[6];               /* Mute Special: 5 Rhythm + 1 DELTA-T Channel */

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
        // /* Delta-T ADPCM unit (Y8950) */

        // YM_DELTAT *deltat;

        // /* Keyboard and I/O ports interface */
        // (int)8 portDirection;
        // (int)8 portLatch;
        // OPL_PORTHANDLER_R porthandler_r;
        // OPL_PORTHANDLER_W porthandler_w;
        // void * port_param;
        // OPL_PORTHANDLER_R keyboardhandler_r;
        // OPL_PORTHANDLER_W keyboardhandler_w;
        // void * keyboard_param;
        //#endif

        /* external event callback handlers */
        public OPL_TIMERHANDLER timer_handler; /* TIMER handler                */
        public Object TimerParam;                   /* TIMER parameter              */
        public OPL_IRQHANDLER IRQHandler;  /* IRQ handler                  */
        //public Object IRQParam;                 /* IRQ parameter                */
        public ym3526_state IRQParam;                 /* IRQ parameter                */
        public OPL_UPDATEHANDLER updateHandler;/* stream update handler        */
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
        //attotime TimerBase;   /* Timer base time (==sampling time)*/

        public int phase_modulation;    /* phase modulation input (SLOT 2) */
        public int[] output = new int[1];
        //#if BUILD_Y8950
        // int output_deltat[4];  /* for Y8950 DELTA-T, chip is mono, that 4 here is just for safety */
        //#endif

        private OPL_SLOT SLOT7_1() {
            return this.pCh[7].SLOT[SLOT1];
        }
        private OPL_SLOT SLOT7_2() {
            return this.pCh[7].SLOT[SLOT2];
        }
        private OPL_SLOT SLOT8_1() {
            return this.pCh[8].SLOT[SLOT1];
        }
        private OPL_SLOT SLOT8_2() {
            return this.pCh[8].SLOT[SLOT2];
        }

        /*INLINE int limit( int val, int max, int min ) {
            if ( val > max )
                val = max;
            else if ( val < min )
                val = min;

            return val;
        }*/

        /* status set and IRQ handling */
        private void OPL_STATUS_SET(int flag) {
            /* set status flag */
            this.status |= (byte) flag;
            if ((this.status & 0x80) == 0) {
                if ((this.status & this.statusmask) != 0) {   /* IRQ on */
                    this.status |= 0x80;
                    /* callback user interrupt handler (IRQ is OFF to ON) */
                    if (this.IRQHandler != null) this.IRQHandler.accept(this.IRQParam, 1);
                }
            }
        }

        /* status reset and IRQ handling */
        private void OPL_STATUS_RESET(int flag) {
            /* reset status flag */
            this.status &= (byte) ~flag;
            if ((this.status & 0x80) != 0) {
                if ((this.status & this.statusmask) == 0) {
                    this.status &= 0x7f;
                    /* callback user interrupt handler (IRQ is ON to OFF) */
                    if (this.IRQHandler != null) this.IRQHandler.accept(this.IRQParam, 0);
                }
            }
        }

        /* IRQ mask set */
        private void OPL_STATUSMASK_SET(int flag) {
            this.statusmask = (byte) flag;
            /* IRQ handling check */
            OPL_STATUS_SET(0);
            OPL_STATUS_RESET(0);
        }


        /* advance LFO to next sample */
        private void advance_lfo() {
            byte tmp;

            /* LFO */
            this.lfo_am_cnt += this.lfo_am_inc;

            if (this.lfo_am_cnt >= (LFO_AM_TAB_ELEMENTS << LFO_SH)) /* lfo_am_table is 210 elements long */
                this.lfo_am_cnt -= (LFO_AM_TAB_ELEMENTS << LFO_SH);

            tmp = lfo_am_table[this.lfo_am_cnt >> LFO_SH];
            //System.err.printf("tmp {0}\n", tmp);

            if (this.lfo_am_depth != 0)
                this.LFO_AM = tmp;
            else
                this.LFO_AM = (int) (tmp >> 2);

            this.lfo_pm_cnt += this.lfo_pm_inc;
            this.LFO_PM = ((this.lfo_pm_cnt >> LFO_SH) & 7) | this.lfo_pm_depth_range;
        }


        private void refresh_eg() {
            OPL_CH CH;
            OPL_SLOT op;
            int i;
            int new_vol;

            for (i = 0; i < 9 * 2; i++) {
                CH = this.pCh[i / 2];
                op = CH.SLOT[i & 1];

                // Envelope Generator
                switch (op.state) {
                case EG_ATT:        // attack phase
                    if ((this.eg_cnt & ((1 << op.eg_sh_ar) - 1)) == 0) {
                        new_vol = op.volume + ((~op.volume *
                                (eg_inc[op.eg_sel_ar + ((this.eg_cnt >> op.eg_sh_ar) & 7)])
                        ) >> 3);
                        if (new_vol <= MIN_ATT_INDEX) {
                            op.volume = MIN_ATT_INDEX;
                            op.state = EG_DEC;
                        }
                    }
                    break;
                        /*case EG_DEC: // decay phase
                            if ( !(this.eg_cnt & ((1<<Op.eg_sh_dr)-1) ) )
                            {
                                new_vol = Op.volume + eg_inc[Op.eg_sel_dr + ((this.eg_cnt>>Op.eg_sh_dr)&7)];

                                if ( new_vol >= Op.sl )
                                    Op.state = EG_SUS;
                            }
                            break;
                        case EG_SUS: // sustain phase
                            if ( !Op.eg_type) percussive mode
                            {
                                new_vol = Op.volume + eg_inc[Op.eg_sel_rr + ((this.eg_cnt>>Op.eg_sh_rr)&7)];

                                if ( !(this.eg_cnt & ((1<<Op.eg_sh_rr)-1) ) )
                                {
                                    if ( new_vol >= MAX_ATT_INDEX )
                                        Op.volume = MAX_ATT_INDEX;
                                }
                            }
                            break;
                        case EG_REL: // release phase
                            if ( !(this.eg_cnt & ((1<<Op.eg_sh_rr)-1) ) )
                            {
                                new_vol = Op.volume + eg_inc[Op.eg_sel_rr + ((this.eg_cnt>>Op.eg_sh_rr)&7)];
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
        }

        /* advance to next sample */
        private void advance() {
            OPL_CH CH;
            OPL_SLOT op;
            int i;

            this.eg_timer += this.eg_timer_add;

            while (this.eg_timer >= this.eg_timer_overflow) {
                this.eg_timer -= this.eg_timer_overflow;

                this.eg_cnt++;

                for (i = 0; i < 9 * 2; i++) {
                    CH = this.pCh[i / 2];
                    op = CH.SLOT[i & 1];

                    /* Envelope Generator */
                    switch (op.state) {
                    case EG_ATT:        /* attack phase */
                        if ((this.eg_cnt & ((1 << op.eg_sh_ar) - 1)) == 0) {
                            //System.err.printf("eg_inc:{0} Op.eg_sel_ar:{1} this.eg_cnt:{2} Op.eg_sh_ar:{3}\n"
                            //    , eg_inc[Op.eg_sel_ar + ((this.eg_cnt >> Op.eg_sh_ar) & 7)]
                            //    , Op.eg_sel_ar
                            //    , this.eg_cnt
                            //    , Op.eg_sh_ar);
                            op.volume += ((~op.volume) *
                                    (eg_inc[op.eg_sel_ar + ((this.eg_cnt >> op.eg_sh_ar) & 7)])
                            ) >> 3;


                            if (op.volume <= MIN_ATT_INDEX) {
                                op.volume = MIN_ATT_INDEX;
                                op.state = EG_DEC;
                            }

                        }
                        break;

                    case EG_DEC:    /* decay phase */
                        if ((this.eg_cnt & ((1 << op.eg_sh_dr) - 1)) == 0) {
                            op.volume += eg_inc[op.eg_sel_dr + ((this.eg_cnt >> op.eg_sh_dr) & 7)];

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
                            if ((this.eg_cnt & ((1 << op.eg_sh_rr) - 1)) == 0) {
                                op.volume += eg_inc[op.eg_sel_rr + ((this.eg_cnt >> op.eg_sh_rr) & 7)];

                                if (op.volume >= MAX_ATT_INDEX)
                                    op.volume = MAX_ATT_INDEX;
                            }
                            /* else do nothing in sustain phase */
                        }
                        break;

                    case EG_REL:    /* release phase */
                        if ((this.eg_cnt & ((1 << op.eg_sh_rr) - 1)) == 0) {
                            op.volume += eg_inc[op.eg_sel_rr + ((this.eg_cnt >> op.eg_sh_rr) & 7)];

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
                CH = this.pCh[i / 2];
                op = CH.SLOT[i & 1];

                /* Phase Generator */
                if (op.vib != 0) {
                    byte block;
                    int block_fnum = CH.block_fnum;

                    int fnum_lfo = (block_fnum & 0x0380) >> 7;

                    int lfo_fn_table_index_offset = lfo_pm_table[this.LFO_PM + 16 * fnum_lfo];

                    if (lfo_fn_table_index_offset != 0)  /* LFO phase modulation active */ {
                        block_fnum += (int) lfo_fn_table_index_offset;
                        block = (byte) ((block_fnum & 0x1c00) >> 10);
                        op.cnt += (this.fn_tab[block_fnum & 0x03ff] >> (7 - block)) * op.mul;
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

            this.noise_p += this.noise_f;
            i = (int) (this.noise_p >> FREQ_SH);        /* number of events (shifts of the shift register) */
            this.noise_p &= FREQ_MASK;
            while (i != 0) {
                /*
                (int)32 j;
                j = ( (this.noise_rng) ^ (this.noise_rng>>14) ^ (this.noise_rng>>15) ^ (this.noise_rng>>22) ) & 1;
                this.noise_rng = (j<<22) | (this.noise_rng>>1);
                */

                /*
                    Instead of doing all the logic operations above, we
                    use a trick here (and use bit 0 as the noise output).
                    The difference is only that the noise bit changes one
                    step ahead. This doesn't matter since we don't know
                    what is real state of the noise_rng after the reset.
                */

                if ((this.noise_rng & 1) != 0) this.noise_rng ^= 0x800302;
                this.noise_rng >>= 1;

                i--;
            }
        }


        private int op_calc(int phase, int env, int pm, int wave_tab) {
            int p;

            p = (env << 4) + sin_tab[wave_tab + ((((int) ((phase & ~FREQ_MASK) + (pm << 16))) >> FREQ_SH) & SIN_MASK)];
            //System.err.printf("op_calc:{0}",p);
            if (p >= TL_TAB_LEN)
                return 0;
            return tl_tab[p];
        }

        private int op_calc1(int phase, int env, int pm, int wave_tab) {
            int p;

            p = (env << 4) + sin_tab[wave_tab + ((((int) ((phase & ~FREQ_MASK) + pm)) >> FREQ_SH) & SIN_MASK)];
            //System.err.printf("op_calc1:{0}", p);

            if (p >= TL_TAB_LEN)
                return 0;
            return tl_tab[p];
        }


        private int volume_calc(OPL_SLOT op) {
            return op.TLL + op.volume + (this.LFO_AM & op.AMmask);
        }

        /* calculate output */
        private void OPL_CALC_CH(OPL_CH ch) {
            OPL_SLOT slot;
            int env;
            int _out;

            if (ch.muted != 0)
                return;

            this.phase_modulation = 0;

            /* slot 1 */
            slot = ch.SLOT[SLOT1];
            env = volume_calc(slot);
            //System.err.printf("env1 {0} {1} {2} {3} {4}\n", env, slot.TLL, slot.volume, this.LFO_AM, slot.AMmask);
            _out = slot.op1_out[0] + slot.op1_out[1];
            slot.op1_out[0] = slot.op1_out[1];
            //slot.connect1 += slot.op1_out[0];
            if (slot.ptrConnect1 == 0) this.output[0] += slot.op1_out[0];
            else this.phase_modulation += slot.op1_out[0];
            slot.op1_out[1] = 0;
            if (env < ENV_QUIET) {
                if (slot.FB == 0)
                    _out = 0;
                slot.op1_out[1] = op_calc1(slot.cnt, env, (_out << slot.FB), slot.waveTable);
            }

            /* slot 2 */
            //slot++;
            slot = ch.SLOT[SLOT2];
            env = volume_calc(slot);
            //System.err.printf("env2 {0}\n", env);
            if (env < ENV_QUIET)
                this.output[0] += op_calc(slot.cnt, env, this.phase_modulation, slot.waveTable);

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

        private void OPL_CALC_RH(OPL_CH[] ch, int noise) {
            OPL_SLOT slot;
            int _out;
            int env;


            /* Bass Drum (verified on real YM3812):
              - depends on the channel 6 'connect' register:
                  when connect = 0 it works the same as in normal (non-rhythm) mode (op1.op2.out)
                  when connect = 1 _only_ Operator 2 is present on output (op2.out), Operator 1 is ignored
              - output sample always is multiplied by 2
            */

            this.phase_modulation = 0;
            /* slot 1 */
            slot = ch[6].SLOT[SLOT1];
            env = volume_calc(slot);

            _out = slot.op1_out[0] + slot.op1_out[1];
            slot.op1_out[0] = slot.op1_out[1];

            if (slot.CON == 0)
                this.phase_modulation = slot.op1_out[0];
            /* else ignore output of Operator 1 */

            slot.op1_out[1] = 0;
            if (env < ENV_QUIET) {
                if (slot.FB == 0)
                    _out = 0;
                slot.op1_out[1] = op_calc1(slot.cnt, env, (_out << slot.FB), slot.waveTable);
            }

            /* slot 2 */
            //slot++;
            slot = ch[6].SLOT[SLOT2];
            env = volume_calc(slot);
            if (env < ENV_QUIET && this.muteSpc[0] == 0)
                this.output[0] += op_calc(slot.cnt, env, this.phase_modulation, slot.waveTable) * 2;


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
            env = volume_calc(SLOT7_1());
            if (env < ENV_QUIET && this.muteSpc[4] == 0) {

                /* high hat phase generation:
                    phase = d0 or 234 (based on frequency only)
                    phase = 34 or 2d0 (based on noise)
                */

                /* base frequency derived from Operator 1 in channel 7 */
                byte bit7 = (byte) (((SLOT7_1().cnt >> FREQ_SH) >> 7) & 1);
                byte bit3 = (byte) (((SLOT7_1().cnt >> FREQ_SH) >> 3) & 1);
                byte bit2 = (byte) (((SLOT7_1().cnt >> FREQ_SH) >> 2) & 1);

                byte res1 = (byte) ((bit2 ^ bit7) | bit3);

                /* when res1 = 0 phase = 0x000 | 0xd0; */
                /* when res1 = 1 phase = 0x200 | (0xd0>>2); */
                int phase = res1 != 0 ? (0x200 | (0xd0 >> 2)) : 0xd0;

                /* enable gate based on frequency of Operator 2 in channel 8 */
                byte bit5e = (byte) (((SLOT8_2().cnt >> FREQ_SH) >> 5) & 1);
                byte bit3e = (byte) (((SLOT8_2().cnt >> FREQ_SH) >> 3) & 1);

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

                this.output[0] += op_calc(phase << FREQ_SH, env, 0, SLOT7_1().waveTable) * 2;
            }

            /* Snare Drum (verified on real YM3812) */
            env = volume_calc(SLOT7_2());
            if (env < ENV_QUIET && this.muteSpc[1] == 0) {
                /* base frequency derived from Operator 1 in channel 7 */
                byte bit8 = (byte) (((SLOT7_1().cnt >> FREQ_SH) >> 8) & 1);

                /* when bit8 = 0 phase = 0x100; */
                /* when bit8 = 1 phase = 0x200; */
                int phase = bit8 != 0 ? 0x200 : 0x100;

                /* Noise bit XOR'es phase by 0x100 */
                /* when noisebit = 0 pass the phase from calculation above */
                /* when noisebit = 1 phase ^= 0x100; */
                /* in other words: phase ^= (noisebit<<8); */
                if (noise != 0)
                    phase ^= 0x100;

                this.output[0] += op_calc(phase << FREQ_SH, env, 0, SLOT7_2().waveTable) * 2;
            }

            /* Tom Tom (verified on real YM3812) */
            env = volume_calc(SLOT8_1());
            if (env < ENV_QUIET && this.muteSpc[2] == 0)
                this.output[0] += op_calc(SLOT8_1().cnt, env, 0, SLOT8_1().waveTable) * 2;

            /* Top Cymbal (verified on real YM3812) */
            env = volume_calc(SLOT8_2());
            if (env < ENV_QUIET && this.muteSpc[3] == 0) {
                /* base frequency derived from Operator 1 in channel 7 */
                byte bit7 = (byte) (((SLOT7_1().cnt >> FREQ_SH) >> 7) & 1);
                byte bit3 = (byte) (((SLOT7_1().cnt >> FREQ_SH) >> 3) & 1);
                byte bit2 = (byte) (((SLOT7_1().cnt >> FREQ_SH) >> 2) & 1);

                byte res1 = (byte) ((bit2 ^ bit7) | bit3);

                /* when res1 = 0 phase = 0x000 | 0x100; */
                /* when res1 = 1 phase = 0x200 | 0x100; */
                int phase = res1 != 0 ? 0x300 : 0x100;

                /* enable gate based on frequency of Operator 2 in channel 8 */
                byte bit5e = (byte) (((SLOT8_2().cnt >> FREQ_SH) >> 5) & 1);
                byte bit3e = (byte) (((SLOT8_2().cnt >> FREQ_SH) >> 3) & 1);

                byte res2 = (byte) (bit3e ^ bit5e);
                /* when res2 = 0 pass the phase from calculation above (res1); */
                /* when res2 = 1 phase = 0x200 | 0x100; */
                if (res2 != 0)
                    phase = 0x300;

                this.output[0] += op_calc(phase << FREQ_SH, env, 0, SLOT8_2().waveTable) * 2;
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
            }
            /*logerror("FMthis.C: TL_TAB_LEN = %i elements (%i bytes)\n",TL_TAB_LEN, (int)sizeof(tl_tab));*/


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

                sin_tab[i] = n * 2 + (m >= 0.0 ? 0 : 1);

                /*logerror("FMthis.C: sin [%4i (hex=%03x)]= %4i (tl_tab value=%5i)\n", i, i, sin_tab[i], tl_tab[sin_tab[i]] );*/
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
                    sin_tab[3 * SIN_LEN + i] = TL_TAB_LEN;
                else
                    sin_tab[3 * SIN_LEN + i] = sin_tab[i & (SIN_MASK >> 2)];

                /*logerror("FMthis.C: sin1[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[1*SIN_LEN+i], tl_tab[sin_tab[1*SIN_LEN+i]] );
                logerror("FMthis.C: sin2[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[2*SIN_LEN+i], tl_tab[sin_tab[2*SIN_LEN+i]] );
                logerror("FMthis.C: sin3[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[3*SIN_LEN+i], tl_tab[sin_tab[3*SIN_LEN+i]] );*/
            }
            /*logerror("FMthis.C: ENV_QUIET= %08x (dec*8=%i)\n", ENV_QUIET, ENV_QUIET*8 );*/


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


        private void OPL_initalize() {
            int i;

            /* frequency base */
            this.freqbase = (this.rate != 0) ? ((double) this.clock / 72.0) / this.rate : 0;

            /*logerror("freqbase=%f\n", this.freqbase);*/

            /* Timer base time */
            //this.TimerBase = attotime_mul(ATTOTIME_IN_HZ(this.clock), 72);

            /* make fnumber . increment counter table */
            for (i = 0; i < 1024; i++) {
                /* opn phase increment counter = 20bit */
                this.fn_tab[i] = (int) ((double) i * 64 * this.freqbase * (1 << (FREQ_SH - 10))); /* -10 because chip works with 10.10 fixed point, while we use 16.16 */

                //System.err.printf("FMthis.C: fn_tab[{0:d}] = {1:x} (dec={2})\n",
                //i, this.fn_tab[i] >> 6, this.fn_tab[i] >> 6);
            }

            //#if 0
            for (i = 0; i < 16; i++) {
                //System.err.printf("FMthis.C: sl_tab[{0}] = {1:x}\n",
                //i, sl_tab[i]);
            }
            for (i = 0; i < 8; i++) {
                int j;
                //System.err.printf("FMthis.C: ksl_tab[oct={0}] =", i);
                for (j = 0; j < 16; j++) {
                    //System.err.printf("{0:x} ", ksl_tab[i * 16 + j]);
                }
                //System.err.printf("\n");
            }
            //#endif

            for (i = 0; i < 9; i++)
                this.pCh[i].muted = 0x00;
            for (i = 0; i < 6; i++)
                this.muteSpc[i] = 0x00;

            /* Amplitude modulation: 27 output levels (triangle waveform); 1 level takes one of: 192, 256 or 448 samples */
            /* One entry from LFO_AM_TABLE lasts for 64 samples */
            this.lfo_am_inc = (int) ((1.0 / 64.0) * (1 << LFO_SH) * this.freqbase);

            /* Vibrato: 8 output levels (triangle waveform); 1 level takes 1024 samples */
            this.lfo_pm_inc = (int) ((1.0 / 1024.0) * (1 << LFO_SH) * this.freqbase);

            /*logerror ("this.lfo_am_inc = %8x ; this.lfo_pm_inc = %8x\n", this.lfo_am_inc, this.lfo_pm_inc);*/

            /* Noise generator: a step takes 1 sample */
            this.noise_f = (int) ((1.0 / 1.0) * (1 << FREQ_SH) * this.freqbase);

            this.eg_timer_add = (int) ((1 << EG_SH) * this.freqbase);
            this.eg_timer_overflow = (1) * (1 << EG_SH);
            /*logerror("OPLinit eg_timer_add=%8x eg_timer_overflow=%8x\n", this.eg_timer_add, this.eg_timer_overflow);*/

        }

        private void FM_KEYON(OPL_SLOT slot, int keySet) {
            if (slot.key == 0) {
                /* restart Phase Generator */
                slot.cnt = 0;
                /* phase . Attack */
                slot.state = EG_ATT;
            }
            slot.key |= keySet;
        }

        private void FM_KEYOFF(OPL_SLOT slot, int keyClr) {
            if (slot.key != 0) {
                slot.key &= keyClr;

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
                //System.err.printf("CALC_FCSLOT slot.eg_sel_ar:{0} slot.ar:{1} slot.ksr:{2}\n", slot.eg_sel_ar, slot.ar, slot.ksr);
                slot.eg_sh_dr = eg_rate_shift[slot.dr + slot.ksr];
                slot.eg_sel_dr = eg_rate_select[slot.dr + slot.ksr];
                slot.eg_sh_rr = eg_rate_shift[slot.rr + slot.ksr];
                slot.eg_sel_rr = eg_rate_select[slot.rr + slot.ksr];
            }
        }

        /* set multi,am,vib,EG-TYP,KSR,mul */
        private void set_mul(int slot_, int v) {
            OPL_CH ch = this.pCh[slot_ / 2];
            OPL_SLOT slot = ch.SLOT[slot_ & 1];

            slot.mul = mul_tab[v & 0x0f];
            slot.KSR = (byte) ((v & 0x10) != 0 ? 0 : 2);
            slot.eg_type = (byte) (v & 0x20);
            slot.vib = (byte) (v & 0x40);
            slot.AMmask = (int) ((v & 0x80) != 0 ? ~0 : 0);
            CALC_FCSLOT(ch, slot);
        }

        /* set ksl & tl */
        private void set_ksl_tl(int slot_, int v) {
            OPL_CH ch = this.pCh[slot_ / 2];
            OPL_SLOT slot = ch.SLOT[slot_ & 1];

            slot.ksl = (byte) ksl_shift[v >> 6];
            slot.TL = (v & 0x3f) << (ENV_BITS - 1 - 7); /* 7 bits TL (bit 6 = always 0) */

            slot.TLL = slot.TL + (ch.ksl_base >> slot.ksl);
        }

        /* set attack rate & decay rate  */
        private void set_ar_dr(int slot_, int v) {
            OPL_CH ch = this.pCh[slot_ / 2];
            OPL_SLOT slot = ch.SLOT[slot_ & 1];

            slot.ar = (int) ((v >> 4) != 0 ? (16 + ((v >> 4) << 2)) : 0);

            if ((slot.ar + slot.ksr) < 16 + 62) {
                slot.eg_sh_ar = eg_rate_shift[slot.ar + slot.ksr];
                slot.eg_sel_ar = eg_rate_select[slot.ar + slot.ksr];
            } else {
                slot.eg_sh_ar = 0;
                slot.eg_sel_ar = 13 * RATE_STEPS;
            }
            //System.err.printf("slot.eg_sel_ar:{0} slot.ar:{1} slot.ksr:{2}\n", slot.eg_sel_ar, slot.ar, slot.ksr);

            slot.dr = (int) ((v & 0x0f) != 0 ? (16 + ((v & 0x0f) << 2)) : 0);
            slot.eg_sh_dr = eg_rate_shift[slot.dr + slot.ksr];
            slot.eg_sel_dr = eg_rate_select[slot.dr + slot.ksr];
        }

        /* set sustain level & release rate */
        private void set_sl_rr(int slot_, int v) {
            OPL_CH ch = this.pCh[slot_ / 2];
            OPL_SLOT slot = ch.SLOT[slot_ & 1];

            slot.sl = sl_tab[v >> 4];

            slot.rr = (int) ((v & 0x0f) != 0 ? (16 + ((v & 0x0f) << 2)) : 0);
            slot.eg_sh_rr = eg_rate_shift[slot.rr + slot.ksr];
            slot.eg_sel_rr = eg_rate_select[slot.rr + slot.ksr];
        }


        /* write a value v to register r on opl chip */
        private void OPLWriteReg(int r, int v) {
            OPL_CH ch;
            int slot;
            int blockFnum;

            //System.err.printf("writeReg:{0}:{1}\n", r, v);

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
                    if ((this.type & OPL_TYPE_WAVESEL) != 0) {
                        this.wavesel = (byte) (v & 0x20);
                        /* do not change the waveform previously selected */
                    }
                    break;
                case 0x02:  /* Timer 1 */
                    this.T[0] = (int) ((256 - v) * 4);
                    break;
                case 0x03:  /* Timer 2 */
                    this.T[1] = (int) ((256 - v) * 16);
                    break;
                case 0x04:  /* IRQ clear / mask and Timer enable */
                    if ((v & 0x80) != 0) {   /* IRQ flag clear */
                        OPL_STATUS_RESET(0x7f - 0x08); /* don't reset BFRDY flag or we will have to call deltat module to set the flag */
                    } else {   /* set IRQ mask ,timer enable*/
                        byte st1 = (byte) (v & 1);
                        byte st2 = (byte) ((v >> 1) & 1);

                        /* IRQRST,T1MSK,t2MSK,EOSMSK,BRMSK,x,ST2,ST1 */
                        OPL_STATUS_RESET(v & (0x78 - 0x08));
                        OPL_STATUSMASK_SET((~v) & 0x78);

                        /* timer 2 */
                        if (this.st[1] != st2) {
                            //attotime period = st2 ? attotime_mul(this.TimerBase, this.T[1]) : attotime_zero;
                            this.st[1] = st2;
                            //if (this.timer_handler) (this.timer_handler)(this.TimerParam,1,period);
                        }
                        /* timer 1 */
                        if (this.st[0] != st1) {
                            //attotime period = st1 ? attotime_mul(this.TimerBase, this.T[0]) : attotime_zero;
                            this.st[0] = st1;
                            //if (this.timer_handler) (this.timer_handler)(this.TimerParam,0,period);
                        }
                    }
                    break;
                case 0x08:  /* MODE,DELTA-T control 2 : CSM,NOTESEL,x,x,smpl,da/ad,64k,rom */
                    this.mode = (byte) v;
                    //#if BUILD_Y8950
                    //   if(this.type&OPL_TYPE_ADPCM)
                    //    YM_DELTAT_ADPCM_Write(this.deltat,r-0x07,v&0x0f); /* mask 4 LSBs in register 08 for DELTA-T unit */
                    //#endif
                    break;

                default:
                    //# ifdef _DEBUG
                    //logerror("FMthis.C: write to unknown register: %02x\n", r);
                    //#endif
                    break;
                }
                break;
            case 0x20:  /* am ON, vib ON, ksr, eg_type, mul */
                slot = slot_array[r & 0x1f];
                if (slot < 0) return;
                set_mul(slot, v);
                break;
            case 0x40:
                slot = slot_array[r & 0x1f];
                if (slot < 0) return;
                set_ksl_tl(slot, v);
                break;
            case 0x60:
                slot = slot_array[r & 0x1f];
                if (slot < 0) return;
                set_ar_dr(slot, v);
                break;
            case 0x80:
                slot = slot_array[r & 0x1f];
                if (slot < 0) return;
                set_sl_rr(slot, v);
                break;
            case 0xa0:
                if (r == 0xbd)          /* am depth, vibrato depth, r,bd,sd,tom,tc,hh */ {
                    this.lfo_am_depth = (byte) (v & 0x80);
                    this.lfo_pm_depth_range = (byte) ((v & 0x40) != 0 ? 8 : 0);

                    this.rhythm = (byte) (v & 0x3f);

                    if ((this.rhythm & 0x20) != 0) {
                        /* BD key on/off */
                        if ((v & 0x10) != 0) {
                            FM_KEYON(this.pCh[6].SLOT[SLOT1], 2);
                            FM_KEYON(this.pCh[6].SLOT[SLOT2], 2);
                        } else {
                            FM_KEYOFF(this.pCh[6].SLOT[SLOT1], ~(int) 2);
                            FM_KEYOFF(this.pCh[6].SLOT[SLOT2], ~(int) 2);
                        }
                        /* HH key on/off */
                        if ((v & 0x01) != 0) FM_KEYON(this.pCh[7].SLOT[SLOT1], 2);
                        else FM_KEYOFF(this.pCh[7].SLOT[SLOT1], ~(int) 2);
                        /* SD key on/off */
                        if ((v & 0x08) != 0) FM_KEYON(this.pCh[7].SLOT[SLOT2], 2);
                        else FM_KEYOFF(this.pCh[7].SLOT[SLOT2], ~(int) 2);
                        /* TOM key on/off */
                        if ((v & 0x04) != 0) FM_KEYON(this.pCh[8].SLOT[SLOT1], 2);
                        else FM_KEYOFF(this.pCh[8].SLOT[SLOT1], ~(int) 2);
                        /* TOP-CY key on/off */
                        if ((v & 0x02) != 0) FM_KEYON(this.pCh[8].SLOT[SLOT2], 2);
                        else FM_KEYOFF(this.pCh[8].SLOT[SLOT2], ~(int) 2);
                    } else {
                        /* BD key off */
                        FM_KEYOFF(this.pCh[6].SLOT[SLOT1], ~(int) 2);
                        FM_KEYOFF(this.pCh[6].SLOT[SLOT2], ~(int) 2);
                        /* HH key off */
                        FM_KEYOFF(this.pCh[7].SLOT[SLOT1], ~(int) 2);
                        /* SD key off */
                        FM_KEYOFF(this.pCh[7].SLOT[SLOT2], ~(int) 2);
                        /* TOM key off */
                        FM_KEYOFF(this.pCh[8].SLOT[SLOT1], ~(int) 2);
                        /* TOP-CY off */
                        FM_KEYOFF(this.pCh[8].SLOT[SLOT2], ~(int) 2);
                    }
                    return;
                }
                /* keyon,block,fnum */
                if ((r & 0x0f) > 8) return;
                ch = this.pCh[r & 0x0f];
                if ((r & 0x10) == 0) {   /* a0-a8 */
                    blockFnum = (ch.block_fnum & 0x1f00) | v;
                } else {   /* b0-b8 */
                    blockFnum = ((v & 0x1f) << 8) | (ch.block_fnum & 0xff);

                    if ((v & 0x20) != 0) {
                        FM_KEYON(ch.SLOT[SLOT1], 1);
                        FM_KEYON(ch.SLOT[SLOT2], 1);
                    } else {
                        FM_KEYOFF(ch.SLOT[SLOT1], ~(int) 1);
                        FM_KEYOFF(ch.SLOT[SLOT2], ~(int) 1);
                    }
                }
                /* update */
                if (ch.block_fnum != blockFnum) {
                    byte block = (byte) (blockFnum >> 10);

                    ch.block_fnum = (int) blockFnum;

                    ch.ksl_base = ksl_tab[blockFnum >> 6];
                    ch.fc = this.fn_tab[blockFnum & 0x03ff] >> (7 - block);

                    /* BLK 2,1,0 bits . bits 3,2,1 of kcode */
                    ch.kcode = (byte) ((ch.block_fnum & 0x1c00) >> 9);

                    /* the info below is actually opposite to what is stated in the Manuals (verifed on real YM3812) */
                    /* if notesel == 0 . lsb of kcode is bit 10 (MSB) of fnum  */
                    /* if notesel == 1 . lsb of kcode is bit 9 (MSB-1) of fnum */
                    if ((this.mode & 0x40) != 0)
                        ch.kcode |= (byte) ((ch.block_fnum & 0x100) >> 8); /* notesel == 1 */
                    else
                        ch.kcode |= (byte) ((ch.block_fnum & 0x200) >> 9); /* notesel == 0 */

                    /* refresh Total Level in both SLOTs of this channel */
                    ch.SLOT[SLOT1].TLL = (int) (ch.SLOT[SLOT1].TL + (ch.ksl_base >> ch.SLOT[SLOT1].ksl));
                    ch.SLOT[SLOT2].TLL = (int) (ch.SLOT[SLOT2].TL + (ch.ksl_base >> ch.SLOT[SLOT2].ksl));

                    /* refresh frequency counter in both SLOTs of this channel */
                    CALC_FCSLOT(ch, ch.SLOT[SLOT1]);
                    CALC_FCSLOT(ch, ch.SLOT[SLOT2]);
                }
                break;
            case 0xc0:
                /* FB,C */
                if ((r & 0x0f) > 8) return;
                ch = this.pCh[r & 0x0f];
                ch.SLOT[SLOT1].FB = (byte) (((v >> 1) & 7) != 0 ? ((v >> 1) & 7) + 7 : 0);
                ch.SLOT[SLOT1].CON = (byte) (v & 1);
                //ch.SLOT[SLOT1].connect1 = (int)(ch.SLOT[SLOT1].CON != 0 ? this.output[0] : this.phase_modulation);
                ch.SLOT[SLOT1].ptrConnect1 = ch.SLOT[SLOT1].CON != 0 ? 0 : 1;
                break;
            case 0xe0: /* waveform select */
                /* simply ignore write to the waveform select register if selecting not enabled in test register */
                if (this.wavesel != 0) {
                    slot = slot_array[r & 0x1f];
                    if (slot < 0) return;
                    ch = this.pCh[slot / 2];

                    ch.SLOT[slot & 1].waveTable = (int) ((v & 0x03) * SIN_LEN);
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

        private void OPLResetChip() {
            int c, s;
            int i;

            this.eg_timer = 0;
            this.eg_cnt = 0;

            this.noise_rng = 1; /* noise shift register */
            this.mode = 0;  /* normal mode */
            OPL_STATUS_RESET(0x7f);

            /* reset with register write */
            OPLWriteReg(0x01, 0); /* wavesel disable */
            OPLWriteReg(0x02, 0); /* Timer1 */
            OPLWriteReg(0x03, 0); /* Timer2 */
            OPLWriteReg(0x04, 0); /* IRQ mask clear */
            for (i = 0xff; i >= 0x20; i--) OPLWriteReg(i, 0);

            /* reset Operator parameters */
            for (c = 0; c < 9; c++) {
                OPL_CH CH = this.pCh[c];
                for (s = 0; s < 2; s++) {
                    /* wave table */
                    CH.SLOT[s].waveTable = 0;
                    CH.SLOT[s].state = EG_OFF;
                    CH.SLOT[s].volume = MAX_ATT_INDEX;
                }
            }
        }


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
        //   OPL_SLOT *SLOT = &CH.SLOT[slot];

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
            OPLsave_state_channel(device, this.P_CH);

            state_save_register_device_item(device, 0, this.eg_cnt);
            state_save_register_device_item(device, 0, this.eg_timer);

            state_save_register_device_item(device, 0, this.rhythm);

            state_save_register_device_item(device, 0, this.lfo_am_depth);
            state_save_register_device_item(device, 0, this.lfo_pm_depth_range);
            state_save_register_device_item(device, 0, this.lfo_am_cnt);
            state_save_register_device_item(device, 0, this.lfo_pm_cnt);

            state_save_register_device_item(device, 0, this.noise_rng);
            state_save_register_device_item(device, 0, this.noise_p);

            if( this.type & OPL_TYPE_WAVESEL )
            {
                state_save_register_device_item(device, 0, this.wavesel);
            }

            state_save_register_device_item_array(device, 0, this.T);
            state_save_register_device_item_array(device, 0, this.st);

        // #if BUILD_Y8950
            if ( (this.type & OPL_TYPE_ADPCM) && (this.deltat) )
            {
                YM_DELTAT_savestate(device, this.deltat);
            }

            if ( this.type & OPL_TYPE_IO )
            {
                state_save_register_device_item(device, 0, this.portDirection);
                state_save_register_device_item(device, 0, this.portLatch);
            }
        // #endif

            state_save_register_device_item(device, 0, this.address);
            state_save_register_device_item(device, 0, this.status);
            state_save_register_device_item(device, 0, this.statusmask);
            state_save_register_device_item(device, 0, this.mode);

            state_save_register_postload(device.machine, OPL_postload, OPL);
        }*/


        /* Create one of virtual YM3812/YM3526/Y8950 */
        /* 'clock' is chip clock in Hz  */
        /* 'rate'  is sampling rate  */
        private void OPLCreate(int clock, int rate, int type) {
            //byte[] ptr;
            //int state_size;

            if (OPL_LockTable() == -1) throw new IllegalStateException();

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
            //    this.deltat = (YM_DELTAT)ptr;
            //}
            //ptr += sizeof(YM_DELTAT);
            ////#endif

            this.type = (byte) type;
            this.clock = clock;
            this.rate = rate;

            /* init Global tables */
            OPL_initalize();
        }

        /* Destroy one of virtual YM3812 */
        private void OPLDestroy() {
            OPL_UnLockTable();
            //free(opl);
        }

        /* Optional handlers */

        private void OPLSetTimerHandler(OPL_TIMERHANDLER timerHandler, Object param) {
            this.timer_handler = timerHandler;
            this.TimerParam = param;
        }

        private void OPLSetIRQHandler(OPL_IRQHANDLER irqHandler, ym3526_state param) {
            this.IRQHandler = irqHandler;
            this.IRQParam = param;
        }

        private void OPLSetUpdateHandler(OPL_UPDATEHANDLER updateHandler, ym3526_state param) {
            this.updateHandler = updateHandler;
            this.UpdateParam = param;
        }

        private int OPLWrite(int a, int v) {
            if ((a & 1) == 0) {   /* address port */
                this.address = (byte) (v & 0xff);
            } else {   /* data port */
                if (this.updateHandler != null) this.updateHandler.accept(this.UpdateParam/*,0*/);
                OPLWriteReg(this.address, v);
            }
            return this.status >> 7;
        }

        private byte OPLRead(int a) {
            if ((a & 1) == 0) {
                /* status port */

                //#if BUILD_Y8950

                //  if(this.type&OPL_TYPE_ADPCM) /* Y8950 */
                //  {
                //   return (this.status & (this.statusmask|0x80)) | (this.deltat.PCM_BSY&1);
                //  }

                //#endif

                /* opl and OPL2 */
                return (byte) (this.status & (this.statusmask | 0x80));
            }

            //#if BUILD_Y8950
            // /* data port */
            // switch(this.address)
            // {
            // case 0x05: /* KeyBoard IN */
            //  if(this.type&OPL_TYPE_KEYBOARD)
            //  {
            //   if(this.keyboardhandler_r)
            //    return this.keyboardhandler_r(this.keyboard_param);
            //# ifdef _DEBUG
            //   else
            //    logerror("Y8950: read unmapped KEYBOARD port\n");
            //#endif
            //        }
            //  return 0;

            // case 0x0f: /* ADPCM-DATA  */
            //  if(this.type&OPL_TYPE_ADPCM)
            //  {
            //   (int)8 val;

            //val = YM_DELTAT_ADPCM_Read(this.deltat);
            //   /*logerror("Y8950: read ADPCM value read=%02x\n",val);*/
            //   return val;
            //  }
            //  return 0;

            // case 0x19: /* I/O DATA    */
            //  if(this.type&OPL_TYPE_IO)
            //  {
            //   if(this.porthandler_r)
            //    return this.porthandler_r(this.port_param);
            //#ifdef _DEBUG
            //   else
            //    logerror("Y8950:read unmapped I/O port\n");
            //#endif
            //  }
            //  return 0;
            // case 0x1a: /* PCM-DATA    */
            //  if(this.type&OPL_TYPE_ADPCM)
            //  {
            //#ifdef _DEBUG
            //   logerror("Y8950 A/D conversion is accessed but not implemented !\n");
            //#endif
            //   return 0x80; /* 2's complement PCM data - result from A/D conversion */
            //  }
            //  return 0;
            // }
            //#endif

            return (byte) 0xff;
        }

        /* CSM Key Controll */
        private void CSMKeyControll(OPL_CH ch) {
            FM_KEYON(ch.SLOT[SLOT1], 4);
            FM_KEYON(ch.SLOT[SLOT2], 4);

            /* The key off should happen exactly one sample later - not implemented correctly yet */

            FM_KEYOFF(ch.SLOT[SLOT1], ~(int) 4);
            FM_KEYOFF(ch.SLOT[SLOT2], ~(int) 4);
        }


        private int OPLTimerOver(int c) {
            if (c != 0) {   /* Timer B */
                OPL_STATUS_SET(0x20);
            } else {   /* Timer A */
                OPL_STATUS_SET(0x40);
                /* CSM mode key,TL controll */
                if ((this.mode & 0x80) != 0) {   /* CSM mode total level latch and auto key on */
                    int ch;
                    if (this.updateHandler != null) this.updateHandler.accept(this.UpdateParam/*,0*/);
                    for (ch = 0; ch < 9; ch++)
                        CSMKeyControll(this.pCh[ch]);
                }
            }
            /* reload timer */
            //if (this.timer_handler) (this.timer_handler)(this.TimerParam,c,attotime_mul(this.TimerBase, this.T[c]));
            return this.status >> 7;
        }


        private static final int MAX_OPL_CHIPS = 2;

        public FM_OPL(int clock, int rate) {
            /* emulator create */
            OPLCreate(clock, rate, OPL_TYPE_YM3526);
            ym3526_reset_chip();
        }

        public void ym3526_shutdown() {
            /* emulator shutdown */
            OPLDestroy();
        }

        public void ym3526_reset_chip() {
            OPLResetChip();
        }

        public int ym3526_write(int a, int v) {
            return OPLWrite(a, v);
        }

        public byte ym3526_read(int a) {
            /* ym3526 always returns bit2 and bit1 in HIGH state */
            return (byte) (OPLRead(a) | 0x06);
        }

        public int ym3526_timer_over(int c) {
            return OPLTimerOver(c);
        }

        public void ym3526_set_timer_handler(OPL_TIMERHANDLER timerHandler, Object param) {
            OPLSetTimerHandler(timerHandler, param);
        }

        public void ym3526_set_irq_handler(OPL_IRQHANDLER irqHandler, ym3526_state param) {
            OPLSetIRQHandler(irqHandler, param);
        }

        public void ym3526_set_update_handler(OPL_UPDATEHANDLER updateHandler, ym3526_state param) {
            OPLSetUpdateHandler(updateHandler, param);
        }

        /**
         * Generate samples for one of the YM3526's
         *
         * @param buffer is the output buffer pointer
         * @param length is the number of samples that should be generated
         */
        private void ym3526_update_one(int[][] buffer, int length) {
            byte rhythm = (byte) (this.rhythm & 0x20);
            int[] bufL = buffer[0];
            int[] bufR = buffer[1];
            int i;

            for (i = 0; i < length; i++) {
                int lt;

                this.output[0] = 0;

                advance_lfo();

                /* FM part */
                OPL_CALC_CH(this.pCh[0]);
                OPL_CALC_CH(this.pCh[1]);
                OPL_CALC_CH(this.pCh[2]);
                OPL_CALC_CH(this.pCh[3]);
                OPL_CALC_CH(this.pCh[4]);
                OPL_CALC_CH(this.pCh[5]);

                if (rhythm == 0) {
                    OPL_CALC_CH(this.pCh[6]);
                    OPL_CALC_CH(this.pCh[7]);
                    OPL_CALC_CH(this.pCh[8]);
                } else        /* Rhythm part */ {
                    OPL_CALC_RH(this.pCh, (this.noise_rng >> 0) & 1);
                }

                lt = this.output[0];

                lt >>= FINAL_SH;

                /* limit check */
                //lt = limit( lt , MAXOUT, MINOUT );

                //# ifdef SAVE_SAMPLE
                //                if (which == 0)
                //                {
                //                    SAVE_ALL_CHANNELS

                //        }
                //#endif

                /* store to Sound buffer */
                bufL[i] = lt;
                bufR[i] = lt;

                advance();
            }
        }

        private void opl_set_mute_mask(int muteMask) {
            for (byte curChn = 0; curChn < 9; curChn++)
                this.pCh[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
            for (byte curChn = 0; curChn < 6; curChn++)
                this.muteSpc[curChn] = (byte) ((muteMask >> (9 + curChn)) & 0x01);
        }
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
    private static final double DV = 0.1875 / 2.0;
    private static final int[] ksl_tab = new int[] {
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
}
