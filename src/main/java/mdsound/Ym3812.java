package mdsound;

import java.util.Random;
import java.util.function.Consumer;


public class Ym3812 extends Instrument.BaseInstrument {
    @Override
    public String getName() {
        return "YM3812";
    }

    @Override
    public String getShortName() {
        return "OPL2";
    }

    @Override
    public void reset(byte chipID) {
        device_reset_ym3812(chipID);
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    private static final int DefaultYM3812ClockValue = 3579545;

    @Override
    public int start(byte chipID, int clock) {
        return start(chipID, DefaultYM3812ClockValue, 44100, null);
    }

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        return (int) device_start_ym3812(chipID, clockValue);
    }

    @Override
    public void stop(byte chipID) {
        device_stop_ym3812(chipID);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        ym3812_stream_update(chipID, outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        ym3812_control_port_w(chipID, 0, (byte) adr);
        ym3812_write_port_w(chipID, 0, (byte) data);
        return 0;
    }

    private static final int EC_DBOPL = 0x00; // DosBox OPL (AdLibEmu)
    //# ifdef ENABLE_ALL_CORES
    private static final int EC_MAME = 0x01; // YM3826 core from MAME
    //#endif

    /**
     * Yamaha 3812 emulator interface - MAME VERSION
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
     */
    public class Ym3812State {
        //sound_stream * stream;
        //emu_timer *  timer[2];
        public OplData chip;
        //final ym3812_interface *intf;
        //final DeviceConfig *device;
    }

    //public byte CHIP_SAMPLING_MODE;
    //public int CHIP_SAMPLE_RATE;
    private byte EMU_CORE = 0x00;

    private static final int MAX_CHIPS = 0x02;
    private Ym3812State[] ym3812Data = new Ym3812State[] {new Ym3812State(), new Ym3812State()};

        /*INLINE Ym3812State *get_safe_token(final DeviceConfig *device)
        {
            assert(device != NULL);
            assert(device.token != NULL);
            assert(device.type == Sound);
            assert(sound_get_type(device) == SOUND_YM3812);
            return (Ym3812State *)device.token;
        }*/

    public interface AdlUpdatehandler extends Consumer<Ym3812State> {
    }

    private void irqHandler(Ym3812State param, int irq) {
        Ym3812State info = param;
        //if (info.intf.handler) (info.intf.handler)(info.device, irq ? ASSERT_LINE : CLEAR_LINE);
        //if (info.intf.handler) (info.intf.handler)(irq ? ASSERT_LINE : CLEAR_LINE);
    }

        /*static TIMER_CALLBACK( timer_callback_0 )
        {
            Ym3812State *info = (Ym3812State *)ptr;
            ym3812_timer_over(info.chip,0);
        }

        static TIMER_CALLBACK( timer_callback_1 )
        {
            Ym3812State *info = (Ym3812State *)ptr;
            ym3812_timer_over(info.chip,1);
        }*/

    private void timerHandler(Ym3812State param, int c, int period) {
        Ym3812State info = param;
        //if( attotime_compare(period, attotime_zero) == 0 )
        if (period == 0) {   /* Reset FM Timer */
            //timer_enable(info.timer[c], 0);
        } else {   /* Start FM Timer */
            //timer_adjust_oneshot(info.timer[c], period, 0);
        }
    }

    private void ym3812_stream_update(byte chipID, int[][] outputs, int samples) {
        //Ym3812State *info = (Ym3812State *)param;
        Ym3812State info = ym3812Data[chipID];
        switch (EMU_CORE) {
        //# ifdef ENABLE_ALL_CORES
        case EC_MAME:
            //ym3812_update_one(info.chip, outputs, samples);
            break;
        //#endif
        case EC_DBOPL:
            //adlib_OPL2_getsample(info.chip, outputs, samples);
            info.chip.getSample(outputs, samples);
            break;
        }
    }

    private int[][] DUMMYBUF = new int[][] {null, null};

    private void _stream_update(Ym3812State param/*, int interval*/) {
        Ym3812State info = param;
        //stream_update(info.stream);

        switch (EMU_CORE) {
        //# ifdef ENABLE_ALL_CORES
        case EC_MAME:
            //ym3812_update_one(info.chip, DUMMYBUF, 0);
            break;
        //#endif
        case EC_DBOPL:
            //adlib_OPL2_getsample(info.chip, DUMMYBUF, 0);
            info.chip.getSample(DUMMYBUF, 0);
            break;
        }
    }

    private int device_start_ym3812(byte chipID, int clock) {
        //static final ym3812_interface dummy = { 0 };
        //Ym3812State *info = get_safe_token(device);
        Ym3812State info;
        int rate;

        if (chipID >= MAX_CHIPS)
            return 0;

        info = ym3812Data[chipID];
        rate = (clock & 0x7FFFFFFF) / 72;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;
        //info.intf = device.static_config ? (final ym3812_interface *)device.static_config : &dummy;
        //info.intf = &dummy;
        //info.device = device;

        /* stream system initialize */
        switch (EMU_CORE) {
        //# ifdef ENABLE_ALL_CORES
        case EC_MAME:
            //info.chip = ym3812_init(clock & 0x7FFFFFFF, rate);
            ////assert_always(info.chip != NULL, "Error creating YM3812 chip");

            ////info.stream = stream_create(device,0,1,rate,info,ym3812_stream_update);

            ///* YM3812 setup */
            //ym3812_set_timer_handler(info.chip, TimerHandler, info);
            //ym3812_set_irq_handler(info.chip, IRQHandler, info);
            //ym3812_set_update_handler(info.chip, _stream_update, info);

            ////info.timer[0] = timer_alloc(device.machine, timer_callback_0, info);
            ////info.timer[1] = timer_alloc(device.machine, timer_callback_1, info);
            break;
        //#endif
        case EC_DBOPL:
            //info.chip = adlib_OPL2_init(clock & 0x7FFFFFFF, rate, _stream_update, info);
            info.chip = new OplData((int) (clock & 0x7FFFFFFF), (int) rate, this::_stream_update, info);
            break;
        }

        return rate;
    }

    //static DEVICE_STOP( Ym3812 )
    private void device_stop_ym3812(byte chipID) {
        //Ym3812State *info = get_safe_token(device);
        Ym3812State info = ym3812Data[chipID];
        switch (EMU_CORE) {
        //# ifdef ENABLE_ALL_CORES
        case EC_MAME:
            //ym3812_shutdown(info.chip);
            break;
        //#endif
        case EC_DBOPL:
            //adlib_OPL2_stop(info.chip);
            info.chip.stop();
            break;
        }
    }

    //static DEVICE_RESET( Ym3812 )
    private void device_reset_ym3812(byte chipID) {
        //Ym3812State *info = get_safe_token(device);
        Ym3812State info = ym3812Data[chipID];
        switch (EMU_CORE) {
        //# ifdef ENABLE_ALL_CORES
        case EC_MAME:
            //ym3812_reset_chip(info.chip);
            break;
        //#endif
        case EC_DBOPL:
            //adlib_OPL2_reset(info.chip);
            info.chip.reset();
            break;
        }
    }


    //READ8_DEVICE_HANDLER( ym3812_r )
    private byte ym3812_r(byte chipID, int offset) {
        //Ym3812State *info = get_safe_token(device);
        Ym3812State info = ym3812Data[chipID];
        switch (EMU_CORE) {
        //# ifdef ENABLE_ALL_CORES
        case EC_MAME:
            //return ym3812_read(info.chip, offset & 1);
            //#endif
        case EC_DBOPL:
            //return adlib_OPL2_reg_read(info.chip, offset & 0x01);
            return (byte) info.chip.readReg(offset & 0x01);
        default:
            return 0x00;
        }
    }

    //WRITE8_DEVICE_HANDLER( ym3812_w )
    private void ym3812_w(byte chipID, int offset, byte data) {
        //Ym3812State *info = get_safe_token(device);
        Ym3812State info = ym3812Data[chipID];
        if (info == null || info.chip == null) return;

        switch (EMU_CORE) {
        //# ifdef ENABLE_ALL_CORES
        case EC_MAME:
            //ym3812_write(info.chip, offset & 1, data);
            break;
        //#endif
        case EC_DBOPL:
            //adlib_OPL2_writeIO(info.chip, offset & 1, data);
            info.chip.writeIO(offset & 1, data);
            break;
        }
    }

    //READ8_DEVICE_HANDLER( ym3812_status_port_r )
    private byte ym3812_status_port_r(byte chipID, int offset) {
        return ym3812_r(chipID, 0);
    }

    //READ8_DEVICE_HANDLER( ym3812_read_port_r )
    private byte ym3812_read_port_r(byte chipID, int offset) {
        return ym3812_r(chipID, 1);
    }

    //WRITE8_DEVICE_HANDLER( ym3812_control_port_w )
    private void ym3812_control_port_w(byte chipID, int offset, byte data) {
        ym3812_w(chipID, 0, data);
    }

    //WRITE8_DEVICE_HANDLER( ym3812_write_port_w )
    private void ym3812_write_port_w(byte chipID, int offset, byte data) {
        ym3812_w(chipID, 1, data);
    }


    public void ym3812_set_emu_core(byte Emulator) {
        //# ifdef ENABLE_ALL_CORES
        EMU_CORE = (byte) ((Emulator < 0x02) ? Emulator : 0x00);
        //#else
        //EMU_CORE = EC_DBOPL;
        //#endif

    }

    private void ym3812_set_mute_mask(byte chipID, int muteMask) {
        Ym3812State info = ym3812Data[chipID];
        switch (EMU_CORE) {
        //# ifdef ENABLE_ALL_CORES
        case EC_MAME:
            //opl_set_mute_mask(info.chip, muteMask);
            break;
        //#endif
        case EC_DBOPL:
            //adlib_OPL2_set_mute_mask(info.chip, muteMask);
            info.chip.setMuteMask(muteMask);
            break;
        }
    }


    /**
     * Generic get_info
     */
        /*DEVICE_GET_INFO( Ym3812 )
        {
            switch (state)
            {
                // --- the following bits of info are returned as 64-bit signed integers ---
                case DEVINFO_INT_TOKEN_BYTES:     info.i = sizeof(Ym3812State);    break;
                // --- the following bits of info are returned as pointers to data or functions ---
                case DEVINFO_FCT_START:       info.start = DEVICE_START_NAME( Ym3812 );    break;
                case DEVINFO_FCT_STOP:       info.stop = DEVICE_STOP_NAME( Ym3812 );    break;
                case DEVINFO_FCT_RESET:       info.reset = DEVICE_RESET_NAME( Ym3812 );    break;
                // --- the following bits of info are returned as NULL-terminated strings ---
                case DEVINFO_STR_NAME:       strcpy(info.s, "YM3812");       break;
                case DEVINFO_STR_FAMILY:     strcpy(info.s, "Yamaha FM");      break;
                case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");        break;
                case DEVINFO_STR_SOURCE_FILE:      strcpy(info.s, __FILE__);       break;
                case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
            }
        }*/


    //opl.h
    /*
     *  Copyright (C) 2002-2010  The DOSBox Team
     *  OPL2/OPL3 emulation library
     *
     *  This library is free software; you can redistribute it and/or
     *  modify it under the terms of the GNU Lesser General Public
     *  License as published by the Free Software Foundation; either
     *  version 2.1 of the License, or (at your option) any later version.
     *
     *  This library is distributed in the hope that it will be useful,
     *  but WITHOUT ANY WARRANTY; without even the implied warranty of
     *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
     *  Lesser General Public License for more details.
     *
     *  You should have received a copy of the GNU Lesser General Public
     *  License along with this library; if not, write to the Free Software
     *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
     */


    /*
     * Originally based on ADLIBEMU.C, an AdLib/OPL2 emulation library by Ken Silverman
     * Copyright (C) 1998-2001 Ken Silverman
     * Ken Silverman's official web site: "http://www.advsys.net/ken"
     */


        /*
            define int, int, int, int, short, short, Bit8s, byte here
        */

        /*
            define attribution that inlines/forces inlining of a function (optional)
        */

    private static final int NUM_CHANNELS = 9;

    private static final int MAXOPERATORS = (NUM_CHANNELS * 2);


    private static final double FL05 = 0.5;
    private static final double FL2 = 2.0;

    private static final int FIXEDPT = 0x10000;        // fixed-point calculations using 16+16
    private static final int FIXEDPT_LFO = 0x1000000;  // fixed-point calculations using 8+24

    private static final int WAVEPREC = 1024;      // waveform precision (10 bits)

    // clocking of the chip
    private double INTFREQU(double n) {
        return n / 72.0;
    }


    private static final int OF_TYPE_ATT = 0;
    private static final int OF_TYPE_DEC = 1;
    private static final int OF_TYPE_REL = 2;
    private static final int OF_TYPE_SUS = 3;
    private static final int OF_TYPE_SUS_NOKEEP = 4;
    private static final int OF_TYPE_OFF = 5;

    private static final int ARC_CONTROL = 0x00;
    private static final int ARC_TVS_KSR_MUL = 0x20;
    private static final int ARC_KSL_OUTLEV = 0x40;
    private static final int ARC_ATTR_DECR = 0x60;
    private static final int ARC_SUSL_RELR = 0x80;
    private static final int ARC_FREQ_NUM = 0xa0;
    private static final int ARC_KON_BNUM = 0xb0;
    private static final int ARC_PERC_MODE = 0xbd;
    private static final int ARC_FEEDBACK = 0xc0;
    private static final int ARC_WAVE_SEL = 0xe0;

    private static final int ARC_SECONDSET = 0x100;    // second Operator set for OPL3


    private static final int OP_ACT_OFF = 0x00;
    private static final int OP_ACT_NORMAL = 0x01; // regular channel activated (bitmasked)
    private static final int OP_ACT_PERC = 0x02;   // percussion channel activated (bitmasked)

    private static final int BLOCKBUF_SIZE = 512;


    // vibrato constants
    private static final int VIBTAB_SIZE = 8;
    private static final double VIBFAC = 70 / 50000d;       // no braces, integer mul/div

    // tremolo constants and table
    private static final int TREMTAB_SIZE = 53;
    private static final double TREM_FREQ = 3.7;           // tremolo at 3.7hz


    /* Operator struct definition
         For OPL2 all 9 channels consist of two operators each, carrier and modulator.
         Channel x has operators x as modulator and operators (9+x) as carrier.
         For OPL3 all 18 channels consist either of two operators (2op mode) or four
         operators (4op mode) which is determined through register4 of the second
         adlib register set.
         Only the channels 0,1,2 (first set) and 9,10,11 (second set) can act as
         4op channels. The two additional operators for a channel y come from the
         2op channel y+3 so the operatorss y, (9+y), y+3, (9+y)+3 make up a 4op
         channel.
    */
    public static class OpType {
        public int cval, lastcval;           // current output/last output (used for feedback)
        public int tcount, wfpos, tinc;      // time (position in waveform) and time increment
        public double amp, step_amp;            // and amplification (envelope)
        public double vol;                      // volume
        public double sustain_level;            // sustain level
        public int mfbi;                 // feedback amount
        public double a0, a1, a2, a3;           // attack rate function coefficients
        public double decaymul, releasemul; // decay/release rate functions
        public int op_state;             // current state of Operator (attack/decay/sustain/release/off)
        public int toff;
        public int freq_high;                // highest three bits of the frequency, used for vibrato calculations
        public short[] cur_wform;               // start of selected waveform
        public int cur_wform_ptr;               // start of selected waveform
        public int cur_wmask;                // mask for selected waveform
        public int act_state;                // activity state (regular, percussion)
        public boolean sus_keep;                   // keep sustain level when decay finished
        public boolean vibrato, tremolo;            // vibrato/tremolo enable bits

        // variables used to provide non-continuous envelopes
        public int generator_pos;            // for non-standard sample rates we need to determine how many samples have passed
        public int cur_env_step;               // current (standardized) sample position
        public int env_step_a, env_step_d, env_step_r;   // number of std samples of one step (for attack/decay/release mode)
        public byte step_skip_pos_a;           // position of 8-cyclic step skipping (always 2^x to check against mask)
        public int env_step_skip_a;            // bitmask that determines if a step is skipped (respective bit is zero then)

        // output level is sustained, mode changes only when Operator is turned off (.release)
        // or when the keep-sustained bit is turned off (.sustain_nokeep)
        private void operator_output(int modulator, int trem) {
            if (this.op_state != OF_TYPE_OFF) {
                int i;
                this.lastcval = this.cval;
                i = (this.wfpos + modulator) / FIXEDPT;

                // wform: -16384 to 16383 (0x4000)
                // trem :  32768 to 65535 (0x10000)
                // step_amp: 0.0 to 1.0
                // vol  : 1/2^14 to 1/2^29 (/0x4000; /1../0x8000)

                this.cval = (int) (this.step_amp * this.vol * this.cur_wform[this.cur_wform_ptr + (i & this.cur_wmask)] * trem / 16.0);
            }
        }


        // no action, Operator is off
        private void operator_off() {
        }

        // output level is sustained, mode changes only when Operator is turned off (.release)
        // or when the keep-sustained bit is turned off (.sustain_nokeep)
        private void operator_sustain() {
            int num_steps_add = this.generator_pos / FIXEDPT;  // number of (standardized) samples
            int ct;
            for (ct = 0; ct < num_steps_add; ct++) {
                this.cur_env_step++;
            }
            this.generator_pos -= num_steps_add * FIXEDPT;
        }

        // Operator in release mode, if output level reaches zero the Operator is turned off
        private void operator_release() {
            int num_steps_add;
            int ct;

            // ??? boundary?
            if (this.amp > 0.00000001) {
                // release phase
                this.amp *= this.releasemul;
            }

            num_steps_add = this.generator_pos / FIXEDPT; // number of (standardized) samples
            for (ct = 0; ct < num_steps_add; ct++) {
                this.cur_env_step++;                      // sample counter
                if ((this.cur_env_step & this.env_step_r) == 0) {
                    if (this.amp <= 0.00000001) {
                        // release phase finished, turn off this Operator
                        this.amp = 0.0;
                        if (this.op_state == OF_TYPE_REL) {
                            this.op_state = OF_TYPE_OFF;
                        }
                    }
                    this.step_amp = this.amp;
                }
            }
            this.generator_pos -= num_steps_add * FIXEDPT;
        }

        // Operator in decay mode, if sustain level is reached the output level is either
        // kept (sustain level keep enabled) or the Operator is switched into release mode
        private void operator_decay() {
            int num_steps_add;
            int ct;

            if (this.amp > this.sustain_level) {
                // decay phase
                this.amp *= this.decaymul;
            }

            num_steps_add = this.generator_pos / FIXEDPT; // number of (standardized) samples
            for (ct = 0; ct < num_steps_add; ct++) {
                this.cur_env_step++;
                if ((this.cur_env_step & this.env_step_d) == 0) {
                    if (this.amp <= this.sustain_level) {
                        // decay phase finished, sustain level reached
                        if (this.sus_keep) {
                            // keep sustain level (until turned off)
                            this.op_state = OF_TYPE_SUS;
                            this.amp = this.sustain_level;
                        } else {
                            // next: release phase
                            this.op_state = OF_TYPE_SUS_NOKEEP;
                        }
                    }
                    this.step_amp = this.amp;
                }
            }
            this.generator_pos -= num_steps_add * FIXEDPT;
        }

        // Operator in attack mode, if full output level is reached,
        // the Operator is switched into decay mode
        private void operator_attack() {
            int num_steps_add;
            int ct;

            this.amp = ((this.a3 * this.amp + this.a2) * this.amp + this.a1) * this.amp + this.a0;

            num_steps_add = this.generator_pos / FIXEDPT;     // number of (standardized) samples
            for (ct = 0; ct < num_steps_add; ct++) {
                this.cur_env_step++;  // next sample
                if ((this.cur_env_step & this.env_step_a) == 0) {       // check if next step already reached
                    if (this.amp > 1.0) {
                        // attack phase finished, next: decay
                        this.op_state = OF_TYPE_DEC;
                        this.amp = 1.0;
                        this.step_amp = 1.0;
                    }
                    this.step_skip_pos_a <<= 1;
                    if (this.step_skip_pos_a == 0) this.step_skip_pos_a = 1;
                    if ((this.step_skip_pos_a & this.env_step_skip_a) != 0) {   // check if required to skip next step
                        this.step_amp = this.amp;
                    }
                }
            }
            this.generator_pos -= num_steps_add * FIXEDPT;
        }

        private void operator_eg_attack_check() {
            if (((this.cur_env_step + 1) & this.env_step_a) == 0) {
                // check if next step already reached
                if (this.a0 >= 1.0) {
                    // attack phase finished, next: decay
                    this.op_state = OF_TYPE_DEC;
                    this.amp = 1.0;
                    this.step_amp = 1.0;
                }
            }
        }
    }

    public class OplData {            //opl_chip
        // per-chip variables
        //int chip_num;
        public OpType[] op = new OpType[MAXOPERATORS];
        public byte[] muteChn = new byte[NUM_CHANNELS + 5];
        public int chipClock;

        public int intSampleRate;

        public byte status;
        public int opl_index;
        public int opl_addr;
        public byte[] adlibreg = new byte[256]; // adlib register set
        public byte[] wave_sel = new byte[22];      // waveform selection

        // vibrato/tremolo increment/counter
        public int vibtab_pos;
        public int vibtab_add;
        public int tremtab_pos;
        public int tremtab_add;

        public int generator_add;    // should be a chip parameter

        public double recipsamp;    // inverse of sampling rate
        public double[] frqmul = new double[16];

        public AdlUpdatehandler UpdateHandler; // stream update handler
        public Ym3812State UpdateParam; //void*                  // stream update parameter

        private void changeAttackRate(int regbase, OpType op_pt) {
            int attackrate = this.adlibreg[ARC_ATTR_DECR + regbase] >> 4;
            if (attackrate != 0) {
                //byte[] step_skip_mask = new byte[] { 0xff, 0xfe, 0xee, 0xba, 0xaa };
                int step_skip;
                int steps;
                int step_num;

                double f = Math.pow(FL2, (double) attackrate + (op_pt.toff >> 2) - 1) * attackconst[op_pt.toff & 3] * this.recipsamp;
                // attack rate coefficients
                op_pt.a0 = 0.0377 * f;
                op_pt.a1 = 10.73 * f + 1;
                op_pt.a2 = -17.57 * f;
                op_pt.a3 = 7.42 * f;

                step_skip = (int) (attackrate * 4 + op_pt.toff);
                steps = step_skip >> 2;
                op_pt.env_step_a = (1 << (steps <= 12 ? 12 - steps : 0)) - 1;

                step_num = (step_skip <= 48) ? (4 - (step_skip & 3)) : 0;
                op_pt.env_step_skip_a = step_skip_mask[step_num];

                if (step_skip >= 62) {
                    op_pt.a0 = 2.0;  // something that triggers an immediate transition to amp:=1.0
                    op_pt.a1 = 0.0;
                    op_pt.a2 = 0.0;
                    op_pt.a3 = 0.0;
                }
            } else {
                // attack disabled
                op_pt.a0 = 0.0;
                op_pt.a1 = 1.0;
                op_pt.a2 = 0.0;
                op_pt.a3 = 0.0;
                op_pt.env_step_a = 0;
                op_pt.env_step_skip_a = 0;
            }
        }

        private void changeDecayRate(int regbase, OpType op_pt) {
            int decayrate = this.adlibreg[ARC_ATTR_DECR + regbase] & 15;
            // decaymul should be 1.0 when decayrate==0
            if (decayrate != 0) {
                int steps;

                double f = -7.4493 * decrelconst[op_pt.toff & 3] * this.recipsamp;
                op_pt.decaymul = Math.pow(FL2, f * Math.pow(FL2, decayrate + (op_pt.toff >> 2)));
                steps = (decayrate * 4 + op_pt.toff) >> 2;
                op_pt.env_step_d = (1 << (steps <= 12 ? 12 - steps : 0)) - 1;
            } else {
                op_pt.decaymul = 1.0;
                op_pt.env_step_d = 0;
            }
        }

        private void changeReleaseRate(int regbase, OpType op_pt) {
            int releaserate = this.adlibreg[ARC_SUSL_RELR + regbase] & 15;
            // releasemul should be 1.0 when releaserate==0
            if (releaserate != 0) {
                int steps;

                double f = -7.4493 * decrelconst[op_pt.toff & 3] * this.recipsamp;
                op_pt.releasemul = Math.pow(FL2, f * Math.pow(FL2, releaserate + (op_pt.toff >> 2)));
                steps = (releaserate * 4 + op_pt.toff) >> 2;
                op_pt.env_step_r = (1 << (steps <= 12 ? 12 - steps : 0)) - 1;
            } else {
                op_pt.releasemul = 1.0;
                op_pt.env_step_r = 0;
            }
        }

        private void changeSustainLevel(int regbase, OpType op_pt) {
            int sustainlevel = this.adlibreg[ARC_SUSL_RELR + regbase] >> 4;
            // sustainlevel should be 0.0 when sustainlevel==15 (max)
            if (sustainlevel < 15) {
                op_pt.sustain_level = Math.pow(FL2, (double) sustainlevel * (-FL05));
            } else {
                op_pt.sustain_level = 0.0;
            }
        }

        private void changeWaveForm(int regbase, OpType op_pt) {
            // waveform selection
            op_pt.cur_wmask = wavemask[this.wave_sel[regbase]];
            op_pt.cur_wform = wavtable;
            op_pt.cur_wform_ptr = waveform[this.wave_sel[regbase]];
            // (might need to be adapted to waveform type here...)
        }

        private void changeKeepSustain(int regbase, OpType op_pt) {
            op_pt.sus_keep = (this.adlibreg[ARC_TVS_KSR_MUL + regbase] & 0x20) > 0;
            if (op_pt.op_state == OF_TYPE_SUS) {
                if (!op_pt.sus_keep)
                    op_pt.op_state = OF_TYPE_SUS_NOKEEP;
            } else if (op_pt.op_state == OF_TYPE_SUS_NOKEEP) {
                if (op_pt.sus_keep)
                    op_pt.op_state = OF_TYPE_SUS;
            }
        }

        // enable/disable vibrato/tremolo LFO effects
        private void changeVibrato(int regbase, OpType op_pt) {
            op_pt.vibrato = (this.adlibreg[ARC_TVS_KSR_MUL + regbase] & 0x40) != 0;
            op_pt.tremolo = (this.adlibreg[ARC_TVS_KSR_MUL + regbase] & 0x80) != 0;
        }

        // change amount of self-feedback
        private void changeFeedback(int chanbase, OpType op_pt) {
            int feedback = this.adlibreg[ARC_FEEDBACK + chanbase] & 14;
            if (feedback != 0)
                op_pt.mfbi = (int) (Math.pow(FL2, (feedback >> 1) + 8));
            else
                op_pt.mfbi = 0;
        }

        private void changeFrequency(int chanbase, int regbase, OpType op_pt) {
            int frn;
            int oct;
            int note_sel;
            double vol_in;

            // frequency
            frn = ((((int) this.adlibreg[ARC_KON_BNUM + chanbase]) & 3) << 8) + (int) this.adlibreg[ARC_FREQ_NUM + chanbase];
            // block number/octave
            oct = ((((int) this.adlibreg[ARC_KON_BNUM + chanbase]) >> 2) & 7);
            op_pt.freq_high = (frn >> 7) & 7;

            // keysplit
            note_sel = (this.adlibreg[8] >> 6) & 1;
            op_pt.toff = ((frn >> 9) & (note_sel ^ 1)) | ((frn >> 8) & note_sel);
            op_pt.toff += (oct << 1);

            // envelope scaling (KSR)
            if ((this.adlibreg[ARC_TVS_KSR_MUL + regbase] & 0x10) == 0) op_pt.toff >>= 2;

            // 20+a0+b0:
            op_pt.tinc = (int) ((((double) (frn << oct)) * this.frqmul[this.adlibreg[ARC_TVS_KSR_MUL + regbase] & 15]));
            // 40+a0+b0:
            vol_in = (double) (this.adlibreg[ARC_KSL_OUTLEV + regbase] & 63) +
                    kslmul[this.adlibreg[ARC_KSL_OUTLEV + regbase] >> 6] * kslev[oct][frn >> 6];
            op_pt.vol = Math.pow(FL2, vol_in * -0.125 - 14);

            // Operator frequency changed, care about features that depend on it
            changeAttackRate(regbase, op_pt);
            changeDecayRate(regbase, op_pt);
            changeReleaseRate(regbase, op_pt);
        }

        private void enableOperator(int regbase, OpType op_pt, int act_type) {
            // check if this is really an off-on transition
            if (op_pt.act_state == OP_ACT_OFF) {
                int wselbase = (int) regbase;
                if (wselbase >= ARC_SECONDSET)
                    wselbase -= (ARC_SECONDSET - 22);   // second set starts at 22

                op_pt.tcount = wavestart[this.wave_sel[wselbase]] * FIXEDPT;

                // start with attack mode
                op_pt.op_state = OF_TYPE_ATT;
                op_pt.act_state |= act_type;
            }
        }

        private void disableOperator(OpType op_pt, int act_type) {
            // check if this is really an on-off transition
            if (op_pt.act_state != OP_ACT_OFF) {
                op_pt.act_state &= (~act_type);
                if (op_pt.act_state == OP_ACT_OFF) {
                    if (op_pt.op_state != OF_TYPE_OFF)
                        op_pt.op_state = OF_TYPE_REL;
                }
            }
        }

        private int initFirstime = 0;

        OplData(int clock, int sampleRate, AdlUpdatehandler updateHandler, Ym3812State param) {

            int i, j, oct;
            //int trem_table_int[TREMTAB_SIZE];

            this.chipClock = clock;
            this.intSampleRate = (int) sampleRate;
            this.UpdateHandler = updateHandler;
            this.UpdateParam = param;

            this.generator_add = (int) (INTFREQU(this.chipClock) * FIXEDPT / this.intSampleRate);

            this.recipsamp = 1.0 / (double) this.intSampleRate;
            for (i = 15; i >= 0; i--) {
                this.frqmul[i] = frqmul_tab[i] * INTFREQU(this.chipClock) / (double) WAVEPREC * (double) FIXEDPT * this.recipsamp;
            }

            //this.status = 0;
            //this.opl_index = 0;

            if (initFirstime == 0) {
                // create vibrato table
                vib_table[0] = 8;
                vib_table[1] = 4;
                vib_table[2] = 0;
                vib_table[3] = -4;
                for (i = 4; i < VIBTAB_SIZE; i++) vib_table[i] = vib_table[i - 4] * -1;
            }

            // vibrato at ~6.1 ?? (opl3 docs say 6.1, opl4 docs say 6.0, Y8950 docs say 6.4)
            this.vibtab_add = (int) (VIBTAB_SIZE * FIXEDPT_LFO / 8192 * INTFREQU(this.chipClock) / this.intSampleRate);
            this.vibtab_pos = 0;

            if (initFirstime == 0) {
                int[] trem_table_int = new int[TREMTAB_SIZE];

                for (i = 0; i < BLOCKBUF_SIZE; i++) vibval_const[i] = 0;


                // create tremolo table
                for (i = 0; i < 14; i++) trem_table_int[i] = i - 13;        // upwards (13 to 26 . -0.5/6 to 0)
                for (i = 14; i < 41; i++) trem_table_int[i] = -i + 14;      // downwards (26 to 0 . 0 to -1/6)
                for (i = 41; i < 53; i++) trem_table_int[i] = i - 40 - 26;  // upwards (1 to 12 . -1/6 to -0.5/6)

                for (i = 0; i < TREMTAB_SIZE; i++) {
                    // 0.0 .. -26/26*4.8/6 == [0.0 .. -0.8], 4/53 steps == [1 .. 0.57]
                    double trem_val1 = ((double) trem_table_int[i]) * 4.8 / 26.0 / 6.0;                // 4.8db
                    double trem_val2 = (double) (trem_table_int[i] / 4) * 1.2 / 6.0 / 6.0;       // 1.2db (larger stepping)

                    trem_table[i] = (int) (Math.pow(FL2, trem_val1) * FIXEDPT);
                    trem_table[TREMTAB_SIZE + i] = (int) (Math.pow(FL2, trem_val2) * FIXEDPT);
                }
            }

            // tremolo at 3.7hz
            this.tremtab_add = (int) ((double) TREMTAB_SIZE * TREM_FREQ * FIXEDPT_LFO / (double) this.intSampleRate);
            this.tremtab_pos = 0;

            if (initFirstime == 0) {
                initFirstime = 1;

                for (i = 0; i < BLOCKBUF_SIZE; i++) tremval_const[i] = FIXEDPT;


                // create waveform tables
                for (i = 0; i < (WAVEPREC >> 1); i++) {
                    wavtable[(i << 1) + WAVEPREC] = (short) (16384 * Math.sin((double) ((i << 1)) * Math.PI * 2 / WAVEPREC));
                    wavtable[(i << 1) + 1 + WAVEPREC] = (short) (16384 * Math.sin((double) ((i << 1) + 1) * Math.PI * 2 / WAVEPREC));
                    wavtable[i] = wavtable[(i << 1) + WAVEPREC];
                    // alternative: (zero-less)
                    /*   wavtable[(i<<1)  +WAVEPREC] = (short)(16384*sin((double)((i<<2)+1)*PI/WAVEPREC));
                                wavtable[(i<<1)+1+WAVEPREC] = (short)(16384*sin((double)((i<<2)+3)*PI/WAVEPREC));
                                wavtable[i]     = wavtable[(i<<1)-1+WAVEPREC]; */
                }
                for (i = 0; i < (WAVEPREC >> 3); i++) {
                    wavtable[i + (WAVEPREC << 1)] = (short) (wavtable[i + (WAVEPREC >> 3)] - 16384);
                    wavtable[i + ((WAVEPREC * 17) >> 3)] = (short) (wavtable[i + (WAVEPREC >> 2)] + 16384);
                }

                // key scale level table verified ([table in book]*8/3)
                kslev[7][0] = 0;
                kslev[7][1] = 24;
                kslev[7][2] = 32;
                kslev[7][3] = 37;
                kslev[7][4] = 40;
                kslev[7][5] = 43;
                kslev[7][6] = 45;
                kslev[7][7] = 47;
                kslev[7][8] = 48;
                for (i = 9; i < 16; i++) kslev[7][i] = (byte) (i + 41);
                for (j = 6; j >= 0; j--) {
                    for (i = 0; i < 16; i++) {
                        oct = (int) kslev[j + 1][i] - 8;
                        if (oct < 0) oct = 0;
                        kslev[j][i] = (byte) oct;
                    }
                }
            }
        }

        private void stop() {
        }

        private void reset() {
            int i;
            OpType op;

            //memset(this.adlibreg, 0x00, sizeof(this.adlibreg));
            //memset(this.Op, 0x00, sizeof(OpType) * MAXOPERATORS);
            //memset(this.wave_sel, 0x00, sizeof(this.wave_sel));
            for (int ind = 0; ind < this.adlibreg.length; ind++) this.adlibreg[ind] = 0;
            for (int ind = 0; ind < this.op.length; ind++) this.op[ind] = new OpType();
            for (int ind = 0; ind < this.wave_sel.length; ind++) this.wave_sel[ind] = 0;

            for (i = 0; i < MAXOPERATORS; i++) {
                op = this.op[i];

                op.op_state = OF_TYPE_OFF;
                op.act_state = OP_ACT_OFF;
                op.amp = 0.0;
                op.step_amp = 0.0;
                op.vol = 0.0;
                op.tcount = 0;
                op.tinc = 0;
                op.toff = 0;
                op.cur_wmask = wavemask[0];
                op.cur_wform = wavtable;
                op.cur_wform_ptr = waveform[0];
                op.freq_high = 0;

                op.generator_pos = 0;
                op.cur_env_step = 0;
                op.env_step_a = 0;
                op.env_step_d = 0;
                op.env_step_r = 0;
                op.step_skip_pos_a = 0;
                op.env_step_skip_a = 0;
            }

            this.status = 0;
            this.opl_index = 0;
            this.opl_addr = 0;
        }

        private void writeIO(int addr, byte val) {
            if ((addr & 1) != 0)
                write((int) this.opl_addr, val);
            else
                this.opl_addr = val;
        }

        private void write(int idx, byte val) {
            int second_set = idx & 0x100;
            this.adlibreg[idx] = val;

            switch (idx & 0xf0) {
            case ARC_CONTROL:
                // here we check for the second set registers, too:
                switch (idx) {
                case 0x02:  // timer1 counter
                case 0x03:  // timer2 counter
                    break;
                case 0x04:
                    // IRQ reset, timer mask/start
                    if ((val & 0x80) != 0) {
                        // clear IRQ bits in status register
                        this.status &= 0x9f;// ~0x60;
                    } else {
                        this.status = 0;
                    }
                    break;
                case 0x08:
                    // CSW, note select
                    break;
                default:
                    break;
                }
                break;
            case ARC_TVS_KSR_MUL:
            case ARC_TVS_KSR_MUL + 0x10: {
                // tremolo/vibrato/sustain keeping enabled; key scale rate; frequency multiplication
                int num = (int) (idx & 7);
                int base_ = (idx - ARC_TVS_KSR_MUL) & 0xff;
                if ((num < 6) && (base_ < 22)) {
                    int modop = regbase2modop[second_set != 0 ? (base_ + 22) : base_];
                    int regbase = base_ + second_set;
                    int chanbase = second_set != 0 ? (modop - 18 + ARC_SECONDSET) : modop;

                    // change tremolo/vibrato and sustain keeping of this Operator
                    OpType op_ptr = this.op[modop + ((num < 3) ? 0 : 9)];
                    changeKeepSustain(regbase, op_ptr);
                    changeVibrato(regbase, op_ptr);

                    // change frequency calculations of this Operator as
                    // key scale rate and frequency multiplicator can be changed
                    changeFrequency(chanbase, base_, op_ptr);
                }
            }
            break;
            case ARC_KSL_OUTLEV:
            case ARC_KSL_OUTLEV + 0x10: {
                // key scale level; output rate
                int num = (int) (idx & 7);
                int base_ = (idx - ARC_KSL_OUTLEV) & 0xff;
                if ((num < 6) && (base_ < 22)) {
                    int modop = regbase2modop[second_set != 0 ? (base_ + 22) : base_];
                    int chanbase = second_set != 0 ? (modop - 18 + ARC_SECONDSET) : modop;

                    // change frequency calculations of this Operator as
                    // key scale level and output rate can be changed
                    OpType op_ptr = this.op[modop + ((num < 3) ? 0 : 9)];
                    changeFrequency(chanbase, base_, op_ptr);
                }
            }
            break;
            case ARC_ATTR_DECR:
            case ARC_ATTR_DECR + 0x10: {
                // attack/decay rates
                int num = (int) (idx & 7);
                int base_ = (idx - ARC_ATTR_DECR) & 0xff;
                if ((num < 6) && (base_ < 22)) {
                    int regbase = base_ + second_set;

                    // change attack rate and decay rate of this Operator
                    OpType op_ptr = this.op[regbase2op[second_set != 0 ? (base_ + 22) : base_]];
                    changeAttackRate(regbase, op_ptr);
                    changeDecayRate(regbase, op_ptr);
                }
            }
            break;
            case ARC_SUSL_RELR:
            case ARC_SUSL_RELR + 0x10: {
                // sustain level; release rate
                int num = (int) (idx & 7);
                int base_ = (idx - ARC_SUSL_RELR) & 0xff;
                if ((num < 6) && (base_ < 22)) {
                    int regbase = base_ + second_set;

                    // change sustain level and release rate of this Operator
                    OpType op_ptr = this.op[regbase2op[second_set != 0 ? (base_ + 22) : base_]];
                    changeReleaseRate(regbase, op_ptr);
                    changeSustainLevel(regbase, op_ptr);
                }
            }
            break;
            case ARC_FREQ_NUM: {
                // 0xa0-0xa8 low8 frequency
                int base_ = (idx - ARC_FREQ_NUM) & 0xff;
                if (base_ < 9) {
                    int opbase = (int) (second_set != 0 ? (base_ + 18) : base_);
                    int modbase;
                    int chanbase;
                    // regbase of modulator:
                    modbase = (int) (modulatorbase[base_] + second_set);

                    chanbase = base_ + second_set;

                    changeFrequency(chanbase, (int) modbase, this.op[opbase]);
                    changeFrequency(chanbase, (int) (modbase + 3), this.op[opbase + 9]);
                }
            }
            break;
            case ARC_KON_BNUM: {
                int base_;
                if (this.UpdateHandler != null) // hack for DOSBox logs
                    this.UpdateHandler.accept(this.UpdateParam);
                if (idx == ARC_PERC_MODE) {

                    if ((val & 0x30) == 0x30) {       // BassDrum active
                        enableOperator(16, this.op[6], OP_ACT_PERC);
                        changeFrequency(6, 16, this.op[6]);
                        enableOperator(16 + 3, this.op[6 + 9], OP_ACT_PERC);
                        changeFrequency(6, 16 + 3, this.op[6 + 9]);
                    } else {
                        disableOperator(this.op[6], OP_ACT_PERC);
                        disableOperator(this.op[6 + 9], OP_ACT_PERC);
                    }
                    if ((val & 0x28) == 0x28) {       // Snare active
                        enableOperator(17 + 3, this.op[16], OP_ACT_PERC);
                        changeFrequency(7, 17 + 3, this.op[16]);
                    } else {
                        disableOperator(this.op[16], OP_ACT_PERC);
                    }
                    if ((val & 0x24) == 0x24) {       // TomTom active
                        enableOperator(18, this.op[8], OP_ACT_PERC);
                        changeFrequency(8, 18, this.op[8]);
                    } else {
                        disableOperator(this.op[8], OP_ACT_PERC);
                    }
                    if ((val & 0x22) == 0x22) {       // Cymbal active
                        enableOperator(18 + 3, this.op[8 + 9], OP_ACT_PERC);
                        changeFrequency(8, 18 + 3, this.op[8 + 9]);
                    } else {
                        disableOperator(this.op[8 + 9], OP_ACT_PERC);
                    }
                    if ((val & 0x21) == 0x21) {       // Hihat active
                        enableOperator(17, this.op[7], OP_ACT_PERC);
                        changeFrequency(7, 17, this.op[7]);
                    } else {
                        disableOperator(this.op[7], OP_ACT_PERC);
                    }

                    break;
                }
                // regular 0xb0-0xb8
                base_ = (idx - ARC_KON_BNUM) & 0xff;
                if (base_ < 9) {
                    int opbase = (int) (second_set != 0 ? (base_ + 18) : base_);
                    // regbase of modulator:
                    int modbase = (int) (modulatorbase[base_] + second_set);
                    int chanbase;

                    if ((val & 32) != 0) {
                        // Operator switched on
                        enableOperator((int) modbase, this.op[opbase], OP_ACT_NORMAL);        // modulator (if 2op)
                        enableOperator((int) (modbase + 3), this.op[opbase + 9], OP_ACT_NORMAL);    // carrier (if 2op)
                    } else {
                        // Operator switched off
                        disableOperator(this.op[opbase], OP_ACT_NORMAL);
                        disableOperator(this.op[opbase + 9], OP_ACT_NORMAL);
                    }

                    chanbase = base_ + second_set;

                    // change frequency calculations of modulator and carrier (2op) as
                    // the frequency of the channel has changed
                    changeFrequency(chanbase, (int) modbase, this.op[opbase]);
                    changeFrequency(chanbase, (int) (modbase + 3), this.op[opbase + 9]);
                }
            }
            break;
            case ARC_FEEDBACK: {
                // 0xc0-0xc8 feedback/modulation type (AM/FM)
                int base_ = (idx - ARC_FEEDBACK) & 0xff;
                if (base_ < 9) {
                    int opbase = (int) (second_set != 0 ? (base_ + 18) : base_);
                    int chanbase = base_ + second_set;
                    changeFeedback(chanbase, this.op[opbase]);
                }
            }
            break;
            case ARC_WAVE_SEL:
            case ARC_WAVE_SEL + 0x10: {
                int num = (int) (idx & 7);
                int base_ = (idx - ARC_WAVE_SEL) & 0xff;
                if ((num < 6) && (base_ < 22)) {
                    if ((this.adlibreg[0x01] & 0x20) != 0) {
                        OpType op_ptr;

                        // wave selection enabled, change waveform
                        this.wave_sel[base_] = (byte) (val & 3);
                        op_ptr = this.op[regbase2modop[base_] + ((num < 3) ? 0 : 9)];
                        changeWaveForm(base_, op_ptr);
                    }
                }
            }
            break;
            default:
                break;
            }
        }

        private int readReg(int port) {
            if ((port & 1) == 0) {
                return status | 6;
            }
            return 0xff;
            //#endif
        }

        private void writeIndex(int port, byte val) {
            opl_index = val;
        }

        /*static void OPL_INLINE clipit16(int ival, short* outval)
        {
            if (ival<32768)
            {
                if (ival>-32769)
                {
                    *outval=(short)ival;
                }
                else
                {
                    *outval = -32768;
                }
            }
            else
            {
                *outval = 32767;
            }
        }*/


        // be careful with this
        // uses cptr and chanval, outputs into outbufl(/outbufr)
        // for opl3 check if opl3-mode is enabled (which uses stereo panning)
        //
        // Changes by Valley Bell:
        // - Changed to always output to both channels
        // - added parameter "chn" to fix panning for 4-Op channels and the Rhythm Cymbal
        //#undef CHANVAL_OUT
        private void CHANVAL_OUT(int chn, int[] outbufl, int[] outbufr, int i, int chanval) {
            outbufl[i] += chanval;
            outbufr[i] += chanval;
        }

        private int[] vib_lut = new int[BLOCKBUF_SIZE];
        private int[] trem_lut = new int[BLOCKBUF_SIZE];

        private void getSample(int[][] sndptr, int numsamples) {

            int i, endsamples;
            OpType[] cptr;
            int cptr_ptr;

            //int outbufl[BLOCKBUF_SIZE];
            int[] outbufl = sndptr[0];
            int[] outbufr = sndptr[1];

            // vibrato/tremolo lookup tables (Global, to possibly be used by all operators)
            //int[] vib_lut = new int[BLOCKBUF_SIZE];
            //int[] trem_lut = new int[BLOCKBUF_SIZE];

            int samples_to_process = numsamples;

            int cursmp;
            int vib_tshift;
            int max_channel = NUM_CHANNELS;
            int cur_ch;

            int[] vibval1, vibval2, vibval3, vibval4;
            int[] tremval1, tremval2, tremval3, tremval4;

            if (samples_to_process == 0) {
                for (cur_ch = 0; cur_ch < max_channel; cur_ch++) {
                    if ((this.adlibreg[ARC_PERC_MODE] & 0x20) != 0 && (cur_ch >= 6 && cur_ch < 9))
                        continue;

                    cptr = this.op;
                    cptr_ptr = cur_ch;

                    if (cptr[cptr_ptr + 0].op_state == OF_TYPE_ATT)
                        cptr[cptr_ptr + 0].operator_eg_attack_check();
                    if (cptr[cptr_ptr + 9].op_state == OF_TYPE_ATT)
                        cptr[cptr_ptr + 9].operator_eg_attack_check();
                }

                return;
            }

            for (cursmp = 0; cursmp < samples_to_process; cursmp += endsamples) {
                endsamples = samples_to_process - cursmp;
                //if (endsamples>BLOCKBUF_SIZE) endsamples = BLOCKBUF_SIZE;

                //memset(outbufl, 0, endsamples * sizeof(int));
                for (int ind = 0; ind < endsamples; ind++) {
                    outbufl[ind] = 0;
                    outbufr[ind] = 0;
                }

                // calculate vibrato/tremolo lookup tables
                vib_tshift = ((this.adlibreg[ARC_PERC_MODE] & 0x40) == 0) ? 1 : 0;   // 14cents/7cents switching
                for (i = 0; i < endsamples; i++) {
                    // cycle through vibrato table
                    this.vibtab_pos += this.vibtab_add;
                    if (this.vibtab_pos / FIXEDPT_LFO >= VIBTAB_SIZE)
                        this.vibtab_pos -= VIBTAB_SIZE * FIXEDPT_LFO;
                    vib_lut[i] = vib_table[this.vibtab_pos / FIXEDPT_LFO] >> vib_tshift;     // 14cents (14/100 of a semitone) or 7cents

                    // cycle through tremolo table
                    this.tremtab_pos += this.tremtab_add;
                    if (this.tremtab_pos / FIXEDPT_LFO >= TREMTAB_SIZE)
                        this.tremtab_pos -= TREMTAB_SIZE * FIXEDPT_LFO;
                    if ((this.adlibreg[ARC_PERC_MODE] & 0x80) != 0)
                        trem_lut[i] = trem_table[this.tremtab_pos / FIXEDPT_LFO];
                    else
                        trem_lut[i] = trem_table[TREMTAB_SIZE + this.tremtab_pos / FIXEDPT_LFO];
                }

                if ((this.adlibreg[ARC_PERC_MODE] & 0x20) != 0) {
                    if ((this.muteChn[NUM_CHANNELS + 0]) == 0) {
                        //BassDrum
                        cptr = this.op;
                        cptr_ptr = 6;
                        if ((this.adlibreg[ARC_FEEDBACK + 6] & 1) != 0) {
                            // additive synthesis
                            if (cptr[cptr_ptr + 9].op_state != OF_TYPE_OFF) {
                                if (cptr[cptr_ptr + 9].vibrato) {
                                    vibval1 = vibval_var1;
                                    for (i = 0; i < endsamples; i++)
                                        vibval1[i] = (int) ((vib_lut[i] * cptr[cptr_ptr + 9].freq_high / 8) * FIXEDPT * VIBFAC);
                                } else
                                    vibval1 = vibval_const;
                                if (cptr[cptr_ptr + 9].tremolo)
                                    tremval1 = trem_lut;    // tremolo enabled, use table
                                else
                                    tremval1 = tremval_const;

                                // calculate channel output
                                for (i = 0; i < endsamples; i++) {
                                    int chanval;

                                    operatorAdvance(cptr[cptr_ptr + 9], vibval1[i]);
                                    opfuncs[cptr[cptr_ptr + 9].op_state].accept(cptr[cptr_ptr + 9]);
                                    cptr[cptr_ptr + 9].operator_output(0, tremval1[i]);

                                    chanval = cptr[cptr_ptr + 9].cval * 2;
                                    CHANVAL_OUT(0, outbufl, outbufr, i, chanval);
                                }
                            }
                        } else {
                            // frequency modulation
                            if ((cptr[cptr_ptr + 9].op_state != OF_TYPE_OFF) || (cptr[cptr_ptr + 0].op_state != OF_TYPE_OFF)) {
                                if ((cptr[cptr_ptr + 0].vibrato) && (cptr[cptr_ptr + 0].op_state != OF_TYPE_OFF)) {
                                    vibval1 = vibval_var1;
                                    for (i = 0; i < endsamples; i++)
                                        vibval1[i] = (int) ((vib_lut[i] * cptr[cptr_ptr + 0].freq_high / 8) * FIXEDPT * VIBFAC);
                                } else
                                    vibval1 = vibval_const;
                                if ((cptr[cptr_ptr + 9].vibrato) && (cptr[cptr_ptr + 9].op_state != OF_TYPE_OFF)) {
                                    vibval2 = vibval_var2;
                                    for (i = 0; i < endsamples; i++)
                                        vibval2[i] = (int) ((vib_lut[i] * cptr[cptr_ptr + 9].freq_high / 8) * FIXEDPT * VIBFAC);
                                } else
                                    vibval2 = vibval_const;
                                if (cptr[cptr_ptr + 0].tremolo)
                                    tremval1 = trem_lut;    // tremolo enabled, use table
                                else
                                    tremval1 = tremval_const;
                                if (cptr[cptr_ptr + 9].tremolo)
                                    tremval2 = trem_lut;    // tremolo enabled, use table
                                else
                                    tremval2 = tremval_const;

                                // calculate channel output
                                for (i = 0; i < endsamples; i++) {
                                    int chanval;

                                    operatorAdvance(cptr[cptr_ptr + 0], vibval1[i]);
                                    opfuncs[cptr[cptr_ptr + 0].op_state].accept(cptr[cptr_ptr + 0]);
                                    cptr[cptr_ptr + 0].operator_output((cptr[cptr_ptr + 0].lastcval + cptr[cptr_ptr + 0].cval) * cptr[cptr_ptr + 0].mfbi / 2, tremval1[i]);

                                    operatorAdvance(cptr[cptr_ptr + 9], vibval2[i]);
                                    opfuncs[cptr[cptr_ptr + 9].op_state].accept(cptr[cptr_ptr + 9]);
                                    cptr[cptr_ptr + 9].operator_output(cptr[cptr_ptr + 0].cval * FIXEDPT, tremval2[i]);

                                    chanval = cptr[cptr_ptr + 9].cval * 2;
                                    CHANVAL_OUT(0, outbufl, outbufr, i, chanval);
                                }
                            }
                        }
                    }   // end if (! Muted)

                    //TomTom (j=8)
                    if ((this.muteChn[NUM_CHANNELS + 2]) == 0 && this.op[8].op_state != OF_TYPE_OFF) {
                        cptr = this.op;
                        cptr_ptr = 8;
                        if (cptr[cptr_ptr + 0].vibrato) {
                            vibval3 = vibval_var1;
                            for (i = 0; i < endsamples; i++)
                                vibval3[i] = (int) ((vib_lut[i] * cptr[cptr_ptr + 0].freq_high / 8) * FIXEDPT * VIBFAC);
                        } else
                            vibval3 = vibval_const;

                        if (cptr[cptr_ptr + 0].tremolo)
                            tremval3 = trem_lut;    // tremolo enabled, use table
                        else
                            tremval3 = tremval_const;

                        // calculate channel output
                        for (i = 0; i < endsamples; i++) {
                            int chanval;

                            operatorAdvance(cptr[cptr_ptr + 0], vibval3[i]);
                            opfuncs[cptr[cptr_ptr + 0].op_state].accept(cptr[cptr_ptr + 0]);     //TomTom
                            cptr[cptr_ptr + 0].operator_output(0, tremval3[i]);
                            chanval = cptr[cptr_ptr + 0].cval * 2;
                            CHANVAL_OUT(0, outbufl, outbufr, i, chanval);
                        }
                    }

                    //Snare/Hihat (j=7), Cymbal (j=8)
                    if ((this.op[7].op_state != OF_TYPE_OFF) || (this.op[16].op_state != OF_TYPE_OFF) ||
                            (this.op[17].op_state != OF_TYPE_OFF)) {
                        cptr = this.op;
                        cptr_ptr = 7;
                        if ((cptr[cptr_ptr + 0].vibrato) && (cptr[cptr_ptr + 0].op_state != OF_TYPE_OFF)) {
                            vibval1 = vibval_var1;
                            for (i = 0; i < endsamples; i++)
                                vibval1[i] = (int) ((vib_lut[i] * cptr[cptr_ptr + 0].freq_high / 8) * FIXEDPT * VIBFAC);
                        } else
                            vibval1 = vibval_const;
                        if ((cptr[cptr_ptr + 9].vibrato) && (cptr[cptr_ptr + 9].op_state == OF_TYPE_OFF)) {
                            vibval2 = vibval_var2;
                            for (i = 0; i < endsamples; i++)
                                vibval2[i] = (int) ((vib_lut[i] * cptr[cptr_ptr + 9].freq_high / 8) * FIXEDPT * VIBFAC);
                        } else
                            vibval2 = vibval_const;

                        if (cptr[cptr_ptr + 0].tremolo)
                            tremval1 = trem_lut;    // tremolo enabled, use table
                        else
                            tremval1 = tremval_const;
                        if (cptr[cptr_ptr + 9].tremolo)
                            tremval2 = trem_lut;    // tremolo enabled, use table
                        else
                            tremval2 = tremval_const;

                        cptr = this.op;
                        cptr_ptr = 8;
                        if ((cptr[cptr_ptr + 9].vibrato) && (cptr[cptr_ptr + 9].op_state == OF_TYPE_OFF)) {
                            vibval4 = vibval_var2;
                            for (i = 0; i < endsamples; i++)
                                vibval4[i] = (int) ((vib_lut[i] * cptr[cptr_ptr + 9].freq_high / 8) * FIXEDPT * VIBFAC);
                        } else
                            vibval4 = vibval_const;

                        if (cptr[cptr_ptr + 9].tremolo) tremval4 = trem_lut;   // tremolo enabled, use table
                        else tremval4 = tremval_const;

                        // calculate channel output
                        cptr = this.op;   // set cptr to something useful (else it stays at Op[8])
                        cptr_ptr = 0;
                        for (i = 0; i < endsamples; i++) {
                            int chanval;

                            operatorAdvanceDrums(this.op[7], vibval1[i], this.op[7 + 9], vibval2[i], this.op[8 + 9], vibval4[i]);

                            if ((this.muteChn[NUM_CHANNELS + 4]) == 0) {
                                opfuncs[this.op[7].op_state].accept(this.op[7]);         //Hihat
                                this.op[7].operator_output(0, tremval1[i]);
                            } else
                                this.op[7].cval = 0;

                            if ((this.muteChn[NUM_CHANNELS + 1]) == 0) {
                                opfuncs[this.op[7 + 9].op_state].accept(this.op[7 + 9]);     //Snare
                                this.op[7 + 9].operator_output(0, tremval2[i]);
                            } else
                                this.op[7 + 9].cval = 0;

                            if ((this.muteChn[NUM_CHANNELS + 3]) == 0) {
                                opfuncs[this.op[8 + 9].op_state].accept(this.op[8 + 9]);     //Cymbal
                                this.op[8 + 9].operator_output(0, tremval4[i]);
                            } else
                                this.op[8 + 9].cval = 0;

                            //chanval = (this.Op[7].cval + this.Op[7+9].cval + this.Op[8+9].cval)*2;
                            //CHANVAL_OUT(0)
                            // fix panning of the snare -Valley Bell
                            chanval = (this.op[7].cval + this.op[7 + 9].cval) * 2;
                            CHANVAL_OUT(7, outbufl, outbufr, i, chanval);

                            chanval = this.op[8 + 9].cval * 2;
                            CHANVAL_OUT(8, outbufl, outbufr, i, chanval);

                        }
                    }
                }

                for (cur_ch = (int) (max_channel - 1); cur_ch >= 0; cur_ch--) {
                    int k;

                    if (this.muteChn[cur_ch] != 0)
                        continue;

                    // skip drum/percussion operators
                    if ((this.adlibreg[ARC_PERC_MODE] & 0x20) != 0 && (cur_ch >= 6) && (cur_ch < 9)) continue;

                    k = (int) cur_ch;
                    cptr = this.op;
                    cptr_ptr = cur_ch;

                    // check for FM/AM
                    if ((this.adlibreg[ARC_FEEDBACK + k] & 1) != 0) {
                        // 2op additive synthesis
                        if ((cptr[cptr_ptr + 9].op_state == OF_TYPE_OFF) && (cptr[cptr_ptr + 0].op_state == OF_TYPE_OFF))
                            continue;
                        if ((cptr[cptr_ptr + 0].vibrato) && (cptr[cptr_ptr + 0].op_state != OF_TYPE_OFF)) {
                            vibval1 = vibval_var1;
                            for (i = 0; i < endsamples; i++)
                                vibval1[i] = (int) ((vib_lut[i] * cptr[cptr_ptr + 0].freq_high / 8) * FIXEDPT * VIBFAC);
                        } else
                            vibval1 = vibval_const;
                        if ((cptr[cptr_ptr + 9].vibrato) && (cptr[cptr_ptr + 9].op_state != OF_TYPE_OFF)) {
                            vibval2 = vibval_var2;
                            for (i = 0; i < endsamples; i++)
                                vibval2[i] = (int) ((vib_lut[i] * cptr[cptr_ptr + 9].freq_high / 8) * FIXEDPT * VIBFAC);
                        } else
                            vibval2 = vibval_const;
                        if (cptr[cptr_ptr + 0].tremolo)
                            tremval1 = trem_lut;    // tremolo enabled, use table
                        else
                            tremval1 = tremval_const;
                        if (cptr[cptr_ptr + 9].tremolo)
                            tremval2 = trem_lut;    // tremolo enabled, use table
                        else
                            tremval2 = tremval_const;

                        // calculate channel output
                        for (i = 0; i < endsamples; i++) {
                            int chanval;

                            // carrier1
                            operatorAdvance(cptr[cptr_ptr + 0], vibval1[i]);
                            opfuncs[cptr[cptr_ptr + 0].op_state].accept(cptr[cptr_ptr + 0]);
                            cptr[cptr_ptr + 0].operator_output((cptr[cptr_ptr + 0].lastcval + cptr[cptr_ptr + 0].cval) * cptr[cptr_ptr + 0].mfbi / 2, tremval1[i]);

                            // carrier2
                            operatorAdvance(cptr[cptr_ptr + 9], vibval2[i]);
                            opfuncs[cptr[cptr_ptr + 9].op_state].accept(cptr[cptr_ptr + 9]);
                            cptr[cptr_ptr + 9].operator_output(0, tremval2[i]);

                            chanval = cptr[cptr_ptr + 9].cval + cptr[cptr_ptr + 0].cval;
                            CHANVAL_OUT(0, outbufl, outbufr, i, chanval);
                        }
                    } else {
                        // 2op frequency modulation
                        if ((cptr[cptr_ptr + 9].op_state == OF_TYPE_OFF) && (cptr[cptr_ptr + 0].op_state == OF_TYPE_OFF))
                            continue;
                        if ((cptr[cptr_ptr + 0].vibrato) && (cptr[cptr_ptr + 0].op_state != OF_TYPE_OFF)) {
                            vibval1 = vibval_var1;
                            for (i = 0; i < endsamples; i++)
                                vibval1[i] = (int) ((vib_lut[i] * cptr[cptr_ptr + 0].freq_high / 8) * FIXEDPT * VIBFAC);
                        } else
                            vibval1 = vibval_const;
                        if ((cptr[cptr_ptr + 9].vibrato) && (cptr[cptr_ptr + 9].op_state != OF_TYPE_OFF)) {
                            vibval2 = vibval_var2;
                            for (i = 0; i < endsamples; i++)
                                vibval2[i] = (int) ((vib_lut[i] * cptr[cptr_ptr + 9].freq_high / 8) * FIXEDPT * VIBFAC);
                        } else
                            vibval2 = vibval_const;
                        if (cptr[cptr_ptr + 0].tremolo)
                            tremval1 = trem_lut;    // tremolo enabled, use table
                        else
                            tremval1 = tremval_const;
                        if (cptr[cptr_ptr + 9].tremolo)
                            tremval2 = trem_lut;    // tremolo enabled, use table
                        else
                            tremval2 = tremval_const;

                        // calculate channel output
                        for (i = 0; i < endsamples; i++) {
                            int chanval;

                            // modulator
                            operatorAdvance(cptr[cptr_ptr + 0], vibval1[i]);
                            opfuncs[cptr[cptr_ptr + 0].op_state].accept(cptr[cptr_ptr + 0]);
                            cptr[cptr_ptr + 0].operator_output((cptr[cptr_ptr + 0].lastcval + cptr[cptr_ptr + 0].cval) * cptr[cptr_ptr + 0].mfbi / 2, tremval1[i]);

                            // carrier
                            operatorAdvance(cptr[cptr_ptr + 9], vibval2[i]);
                            opfuncs[cptr[cptr_ptr + 9].op_state].accept(cptr[cptr_ptr + 9]);
                            cptr[cptr_ptr + 9].operator_output(cptr[cptr_ptr + 0].cval * FIXEDPT, tremval2[i]);

                            chanval = cptr[cptr_ptr + 9].cval;
                            CHANVAL_OUT(0, outbufl, outbufr, i, chanval);
                        }
                    }
                }

                // convert to 16bit samples
//                for (i=0;i<endsamples;i++)
//                    clipit16(outbufl[i],sndptr++);
            }
        }

        private void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < NUM_CHANNELS + 5; curChn++)
                this.muteChn[curChn] = (byte) ((muteMask >> curChn) & 0x01);
        }

        private void operatorAdvance(OpType op_pt, int vib) {
            op_pt.wfpos = op_pt.tcount;                       // waveform position

            // advance waveform time
            op_pt.tcount += op_pt.tinc;
            op_pt.tcount += op_pt.tinc * vib / FIXEDPT;

            op_pt.generator_pos += generator_add;
        }

        private void operatorAdvanceDrums(OpType op_pt1, int vib1, OpType op_pt2, int vib2, OpType op_pt3, int vib3) {
            int c1 = op_pt1.tcount / FIXEDPT;
            int c3 = op_pt3.tcount / FIXEDPT;
            int phasebit = (((c1 & 0x88) ^ ((c1 << 5) & 0x80)) | ((c3 ^ (c3 << 2)) & 0x20)) != 0 ? 0x02 : 0x00;

            int noisebit = rand.nextInt() & 1;

            int snare_phase_bit = (((op_pt1.tcount / FIXEDPT) / 0x100) & 1);

            //Hihat
            int inttm = (phasebit << 8) | (0x34 << (phasebit ^ (noisebit << 1)));
            op_pt1.wfpos = inttm * FIXEDPT;                // waveform position
            // advance waveform time
            op_pt1.tcount += op_pt1.tinc;
            op_pt1.tcount += op_pt1.tinc * vib1 / FIXEDPT;
            op_pt1.generator_pos += generator_add;

            //Snare
            inttm = ((1 + snare_phase_bit) ^ noisebit) << 8;
            op_pt2.wfpos = inttm * FIXEDPT;                // waveform position
            // advance waveform time
            op_pt2.tcount += op_pt2.tinc;
            op_pt2.tcount += op_pt2.tinc * vib2 / FIXEDPT;
            op_pt2.generator_pos += generator_add;

            //Cymbal
            inttm = (1 + phasebit) << 8;
            op_pt3.wfpos = inttm * FIXEDPT;                // waveform position
            // advance waveform time
            op_pt3.tcount += op_pt3.tinc;
            op_pt3.tcount += op_pt3.tinc * vib3 / FIXEDPT;
            op_pt3.generator_pos += generator_add;
        }
    }


    // IMPORTANT: This file is not meant to be compiled. It's included in adlibemu_opl.c.

    /*
     *  Copyright (C) 2002-2010  The DOSBox Team
     *  OPL2/OPL3 emulation library
     *
     *  This library is free software; you can redistribute it and/or
     *  modify it under the terms of the GNU Lesser General Public
     *  License as published by the Free Software Foundation; either
     *  version 2.1 of the License, or (at your option) any later version.
     *
     *  This library is distributed in the hope that it will be useful,
     *  but WITHOUT ANY WARRANTY; without even the implied warranty of
     *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
     *  Lesser General Public License for more details.
     *
     *  You should have received a copy of the GNU Lesser General Public
     *  License along with this library; if not, write to the Free Software
     *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
     */


    /*
     * Originally based on ADLIBEMU.C, an AdLib/OPL2 emulation library by Ken Silverman
     * Copyright (C) 1998-2001 Ken Silverman
     * Ken Silverman's official web site: "http://www.advsys.net/ken"
     */


    //static double recipsamp; // inverse of sampling rate  // moved to OplData
    private short[] wavtable = new short[WAVEPREC * 3];   // wave form table

    // vibrato/tremolo tables
    private int[] vib_table = new int[VIBTAB_SIZE];
    private int[] trem_table = new int[TREMTAB_SIZE * 2];

    private int[] vibval_const = new int[BLOCKBUF_SIZE];
    private int[] tremval_const = new int[BLOCKBUF_SIZE];

    // vibrato value tables (used per-Operator)
    private int[] vibval_var1 = new int[BLOCKBUF_SIZE];
    private int[] vibval_var2 = new int[BLOCKBUF_SIZE];

    // vibrato/trmolo value table pointers
    // moved to adlib_getsample


    // key scale level lookup table
    private static final double[] kslmul = new double[] {
            0.0, 0.5, 0.25, 1.0     // . 0, 3, 1.5, 6 dB/oct
    };

    // frequency multiplicator lookup table
    private static final double[] frqmul_tab = new double[] {
            0.5, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 12, 12, 15, 15
    };

    // calculated frequency multiplication values (depend on sampling rate)
    //static double frqmul[16]; // moved to OplData

    // key scale levels
    private byte[][] kslev = new byte[][] {
            new byte[16], new byte[16], new byte[16], new byte[16],
            new byte[16], new byte[16], new byte[16], new byte[16]
    };

    // map a channel number to the register offset of the modulator (=register base)
    private static final byte[] modulatorbase = new byte[] {
            0, 1, 2,
            8, 9, 10,
            16, 17, 18
    };

    // map a register base to a modulator Operator number or Operator number
    private static final byte[] regbase2modop = new byte[] {
            0, 1, 2, 0, 1, 2, 0, 0, 3, 4, 5, 3, 4, 5, 0, 0, 6, 7, 8, 6, 7, 8
    };

    private static final byte[] regbase2op = new byte[] {
            0, 1, 2, 9, 10, 11, 0, 0, 3, 4, 5, 12, 13, 14, 0, 0, 6, 7, 8, 15, 16, 17
    };


    // start of the waveform
    private static final int[] waveform = new int[] {
            WAVEPREC,
            WAVEPREC >> 1,
            WAVEPREC,
            (WAVEPREC * 3) >> 2,
            0,
            0,
            (WAVEPREC * 5) >> 2,
            WAVEPREC << 1
    };

    // length of the waveform as mask
    private static final int[] wavemask = new int[] {
            WAVEPREC - 1,
            WAVEPREC - 1,
            (WAVEPREC >> 1) - 1,
            (WAVEPREC >> 1) - 1,
            WAVEPREC - 1,
            ((WAVEPREC * 3) >> 2) - 1,
            WAVEPREC >> 1,
            WAVEPREC - 1
    };

    // where the first entry resides
    private static final int[] wavestart = new int[] {
            0,
            WAVEPREC >> 1,
            0,
            WAVEPREC >> 2,
            0,
            0,
            0,
            WAVEPREC >> 3
    };

    // envelope generator function constants
    private static final double[] attackconst = new double[] {
            1 / 2.82624,
            1 / 2.25280,
            1 / 1.88416,
            1 / 1.59744
    };

    private static final double[] decrelconst = new double[] {
            1 / 39.28064,
            1 / 31.41608,
            1 / 26.17344,
            1 / 22.44608
    };


    private static Random rand = new Random();


    private interface optype_fptr extends Consumer<OpType> {
    }

    private optype_fptr[] opfuncs = new optype_fptr[] {
            OpType::operator_attack,
            OpType::operator_decay,
            OpType::operator_release,
            OpType::operator_sustain, // sustain phase (keeping level)
            OpType::operator_release, // sustain_nokeep phase (release-style)
            OpType::operator_off
    };

    private static final byte[] step_skip_mask = new byte[] {(byte) 0xff, (byte) 0xfe, (byte) 0xee, (byte) 0xba, (byte) 0xaa};
}

