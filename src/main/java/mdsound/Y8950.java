package mdsound;

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;


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
    public void reset(byte chipId) {
        device_reset_y8950(chipId);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipId, int clock) {
        return device_start_y8950(chipId, 3579545);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        return device_start_y8950(chipId, clockValue);
    }

    @Override
    public void stop(byte chipId) {
        device_stop_y8950(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        y8950_stream_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        y8950_w(chipId, 0x00, (byte) adr);
        y8950_w(chipId, 0x01, (byte) data);
        return 0;
    }

    /**
     * FILE
     * Yamaha 3812 emulator interface - MAME VERSION
     * <p>
     * CREATED BY
     * Ernesto Corvi
     * <p>
     * UPDATE LOG
     * JB  28-04-2002  Fixed simultaneous usage of all three different chip types.
     * Used real sample rate when resample filter is active.
     * AAT 12-28-2001  Protected Y8950 from accessing unmapped port and keyboard handlers.
     * CHS 1999-01-09  Fixes new Ym3812 emulation interface.
     * CHS 1998-10-23  Mame streaming Sound chip update
     * EC  1998        Created Interface
     * <p>
     * NOTES
     */
    public static class Y8950State {
        private byte CHIP_SAMPLING_MODE = 0;

        public Opl opl;

        private void IRQHandler(int irq) {
            //if (info.intf.handler) (info.intf.handler)(info.device, irq ? ASSERT_LINE : CLEAR_LINE);
            //if (info.intf.handler) (info.intf.handler)(irq ? ASSERT_LINE : CLEAR_LINE);
        }

        private void TimerHandler(int c, int period) {
            //if( attotime_compare(period, attotime_zero) == 0 )
            if (period == 0) { // Reset FM Timer
                //timer_enable(info.timer[c], 0);
            } else { // Start FM Timer
                //timer_adjust_oneshot(info.timer[c], period, 0);
            }
        }

        private byte Y8950PortHandler_r() {
            /*if (info.intf.portread)
                return info.intf.portread(0);*/
            return 0;
        }

        private void Y8950PortHandler_w(byte data) {
            /*if (info.intf.portwrite)
                info.intf.portwrite(0,data);*/
        }

        private byte Y8950KeyboardHandler_r() {
            /*if (info.intf.keyboardread)
                return info.intf.keyboardread(0);*/
            return 0;
        }

        private void Y8950KeyboardHandler_w(byte data) {
            /*if (info.intf.keyboardwrite)
                info.intf.keyboardwrite(0,data);*/
        }

        private int[][] dummyBuf = new int[][] {null, null};

        private void _stream_update(int interval) {
            //stream_update(info.stream);
            this.y8950_update_one(dummyBuf, 0);
        }

        public int Y8950_start(int clock) {
            int rate = clock / 72;
            if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                    CHIP_SAMPLING_MODE == 0x02)
                rate = CHIP_SAMPLE_RATE;
            //this.intf = device.static_config ? (final y8950_interface *)device.static_config : &dummy;
            //this.intf = &dummy;
            //this.device = device;

            // stream system initialize
            this.opl = y8950_init(clock, rate);
            //assert_always(this.chip != NULL, "Error creating Y8950 chip");

            //this.stream = stream_create(device,0,1,rate,info,y8950_stream_update);

            // port and keyboard handler
            this.y8950_set_port_handler(this::Y8950PortHandler_w, this::Y8950PortHandler_r);
            this.y8950_set_keyboard_handler(this::Y8950KeyboardHandler_w, this::Y8950KeyboardHandler_r);

            // Y8950 setup
            this.y8950_set_timer_handler(this::TimerHandler);
            this.y8950_set_irq_handler(this::IRQHandler);
            this.y8950_set_update_handler(this::_stream_update);

            //this.timer[0] = timer_alloc(device.machine, timer_callback_0, info);
            //this.timer[1] = timer_alloc(device.machine, timer_callback_1, info);

            return rate;
        }

        private void Y8950_deltat_status_set(byte changebits) {
            opl.setStatus(changebits);
        }

        private void Y8950_deltat_status_reset(byte changebits) {
            opl.resetStatus(changebits);
        }

        public Opl y8950_init(int clock, int rate) {
            // emulator create
            this.opl = new Opl(clock, rate, Opl.TYPE_Y8950);
            opl.deltaT.statusSetHandler = this::Y8950_deltat_status_set;
            opl.deltaT.statusResetHandler = this::Y8950_deltat_status_reset;

            return opl;
        }

        private void y8950_shutdown() {
            opl.deltaT.memory = null;

            // emulator shutdown
            opl.destroy();
        }

        private void y8950_reset_chip() {
            opl.reset();
        }

        public int y8950_write(int a, int v) {
            return opl.write(a, v);
        }

        private byte y8950_read(int a) {
            return opl.read(a);
        }

        private int y8950_timer_over(int c) {
            return opl.oplTimerOver(c);
        }

        public void y8950_set_timer_handler(Opl.TimerHandler timer_handler) {
            opl.setTimerHandler(timer_handler);
        }

        private void y8950_set_irq_handler(Opl.IrqHandler irqHandler) {
            opl.setIRQHandler(irqHandler);
        }

        private void y8950_set_update_handler(Opl.UpdateHandler updatehandler) {
            opl.setUpdateHandler(updatehandler);
        }

        private void y8950_set_delta_t_memory(byte[] deltat_mem_ptr, int deltat_mem_size) {
            opl.deltaT.memory = deltat_mem_ptr;
            opl.deltaT.memorySize = deltat_mem_size;
        }

        private void y8950_write_pcmrom(int romSize, int dataStart, int dataLength, byte[] romData) {
            opl.writePcmRom(romSize, dataStart, dataLength, romData);
        }

        private void y8950_write_pcmrom(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
            opl.writePcmRom(romSize, dataStart, dataLength, romData, srcStartAddress);
        }

        /**
         * Generate samples for one of the Y8950's
         *
         * @param buffer is the output buffer pointer
         * @param length is the number of samples that should be generated
         */
        private void y8950_update_one(int[][] buffer, int length) {
            opl.updateOne(buffer, length);
        }

        private void y8950_set_port_handler(Opl.PortWriteHandler portHandlerW, Opl.PortReadHandler portHandlerR) {
            opl.portWriteHandler = portHandlerW;
            opl.portReadHandler = portHandlerR;
        }

        private void y8950_set_keyboard_handler(Opl.PortWriteHandler KeyboardHandler_w, Opl.PortReadHandler KeyboardHandler_r) {
            opl.keyboardWriteHandler = KeyboardHandler_w;
            opl.keyboardReadHandler = KeyboardHandler_r;
        }
    }

    static class Ym3812State {
        private static final int MAX_OPL_CHIPS = 2;

        public Opl opl;

        /**
         * Initialize YM3526 emulator(s).
         *
         * @param clock is the chip clock in Hz
         * @param rate is sampling rate
         */
        private Opl ym3812_init(int clock, int rate) {
            // emulator create
            opl = new Opl(clock, rate, Opl.TYPE_YM3812);
            ym3812_reset_chip();
            return opl;
        }

        private void ym3812_shutdown() {
            // emulator shutdown
            opl.destroy();
        }

        private void ym3812_reset_chip() {
            opl.reset();
        }

        private int ym3812_write(int a, int v) {
            return opl.write(a, v);
        }

        private byte ym3812_read(int a) {
            // ym3812 always returns bit2 and bit1 in HIGH state
            return (byte) (opl.read(a) | 0x06);
        }

        private int ym3812_timer_over(int c) {
            return opl.oplTimerOver(c);
        }

        private void ym3812_set_timer_handler(Opl.TimerHandler timer_handler) {
            opl.setTimerHandler(timer_handler);
        }

        private void ym3812_set_irq_handler(Opl.IrqHandler irqHandler) {
            opl.setIRQHandler(irqHandler);
        }

        private void ym3812_set_update_handler(Opl.UpdateHandler updateHandler) {
            opl.setUpdateHandler(updateHandler);
        }

        /**
         * Generate samples for one of the YM3812's
         *
         * @param buffer is the output buffer pointer
         * @param length is the number of samples that should be generated
         */
        private void ym3812_update_one(int[][] buffer, int length) {
            opl.updateOne(buffer, length);
        }
    }

    private static final int MAX_CHIPS = 0x02;
    private Y8950State[] y8950Data = new Y8950State[] {new Y8950State(), new Y8950State()};//[MAX_CHIPS];

    public void y8950_stream_update(byte chipId, int[][] outputs, int samples) {
        Y8950State info = y8950Data[chipId];
        info.y8950_update_one(outputs, samples);
    }

    //static DEVICE_START( Y8950 )
    public int device_start_y8950(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        Y8950State info = y8950Data[chipId];
        return info.Y8950_start(clock);
    }

    public void device_stop_y8950(byte chipId) {
        Y8950State info = y8950Data[chipId];
        info.y8950_shutdown();
    }

    public void device_reset_y8950(byte chipId) {
        Y8950State info = y8950Data[chipId];
        info.y8950_reset_chip();
    }

    public byte y8950_r(byte chipId, int offset) {
        Y8950State info = y8950Data[chipId];
        return info.y8950_read(offset & 1);
    }

    public void y8950_w(byte chipId, int offset, byte data) {
        Y8950State info = y8950Data[chipId];
        info.y8950_write(offset & 1, data);
    }

    public byte y8950_status_port_r(byte chipId, int offset) {
        return y8950_r(chipId, 0);
    }

    public byte y8950_read_port_r(byte chipId, int offset) {
        return y8950_r(chipId, 1);
    }

    public void y8950_control_port_w(byte chipId, int offset, byte data) {
        y8950_w(chipId, 0, data);
    }

    public void y8950_write_port_w(byte chipId, int offset, byte data) {
        y8950_w(chipId, 1, data);
    }

    public void y8950_write_data_pcmrom(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        Y8950State info = y8950Data[chipId];
        info.y8950_write_pcmrom(romSize, dataStart, dataLength, romData);
    }

    public void y8950_write_data_pcmrom(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        Y8950State info = y8950Data[chipId];
        info.y8950_write_pcmrom(romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    public void y8950_set_mute_mask(byte chipId, int muteMask) {
        Y8950State info = y8950Data[chipId];
        info.opl.setMuteMask(muteMask);
    }

    /*
     * Generic get_info
     */
    /*DEVICE_GET_INFO( Y8950 ) {
            case DEVINFO_STR_NAME:       strcpy(info.s, "Y8950");       break;
            case DEVINFO_STR_FAMILY:     strcpy(info.s, "Yamaha FM");      break;
            case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");        break;
            case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
        }
    }*/

    /*
     * software implementation of FM Sound generator
     *                                            types OPL and OPL2
     *
     * Copyright Jarek Burczynski (bujar at mame dot net)
     * Copyright Tatsuyuki Satoh , MultiArcadeMachineEmulator development
     *
     * Version 0.72
     *

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
    public static class Opl {

        /*
         * YAMAHA DELTA-T adpcm Sound emulation subroutine
         * used by fmopl.c (Y8950) and Fm.c (YM2608 and YM2610/B)
         *
         * Base program is YM2610 emulator by Hiromitsu Shioya.
         * Written by Tatsuyuki Satoh
         * Improvements by Jarek Burczynski (bujar at mame dot net)
         *
         *
         * History:
         *
         * 03-08-2003 Jarek Burczynski:
         *  - fixed BRDY flag implementation.
         *
         * 24-07-2003 Jarek Burczynski, Frits Hilderink:
         *  - fixed delault value for control2 in YM_DELTAT_ADPCM_Reset
         *
         * 22-07-2003 Jarek Burczynski, Frits Hilderink:
         *  - fixed external memory support
         *
         * 15-06-2003 Jarek Burczynski:
         *  - implemented CPU . AUDIO ADPCM synthesis (via writes to the ADPCM data reg $08)
         *  - implemented support for the Limit address register
         *  - supported two bits from the control register 2 ($01): RAM TYPE (x1 bit/x8 bit), ROM/RAM
         *  - implemented external memory access (read/write) via the ADPCM data reg reads/writes
         *    Thanks go to Frits Hilderink for the example code.
         *
         * 14-06-2003 Jarek Burczynski:
         *  - various fixes to enable proper support for status register flags: BSRDY, PCM BSY, ZERO
         *  - modified EOS handling
         *
         * 05-04-2003 Jarek Burczynski:
         *  - implemented partial support for external/processor memory on sample replay
         *
         * 01-12-2002 Jarek Burczynski:
         *  - fixed first missing Sound in gigandes thanks to previous fix (interpolator) by ElSemi
         *  - renamed/removed some DeltaT struct fields
         *
         * 28-12-2001 Acho A. Tang
         *  - added EOS status report on ADPCM playback.
         *
         * 05-08-2001 Jarek Burczynski:
         *  - now_step is initialized with 0 at the start of play.
         *
         * 12-06-2001 Jarek Burczynski:
         *  - corrected end of sample bug in YM_DELTAT_ADPCM_CALC.
         *    Checked on real YM2610 chip - address register is 24 bits wide.
         *    Thanks go to Stefan Jokisch (stefan.jokisch@gmx.de) for tracking down the problem.
         *
         * TO DO:
         *      Check size of the address register on the other chips....
         *
         * Version 0.72
         *
         * Sound chips that have this unit:
         * YM2608   OPNA
         * YM2610/B OPNB
         * Y8950    MSX AUDIO
         *
         * AT: rearranged and tigntened structure
         */
        public static class DeltaT {
            private static final int YM_DELTAT_SHIFT = 16;

            private static final int EMULATION_MODE_NORMAL = 0;
            private static final int EMULATION_MODE_YM2610 = 1;

            public void reset(double freqBase, int[] outputDeltaT) {
                this.freqVase = freqBase;
                this.outputPointer = outputDeltaT;
                this.outputPointerP = 0;
                this.portshift = 5;
                this.outputRange = 1 << 23;
                this.reset(0, EMULATION_MODE_NORMAL);
            }

            public void init() {
                this.memory = null;
                this.memorySize = 0x00;
                this.memoryMask = 0x00;

                this.statusChangeEOSBit = 0x10; // status flag: set bit4 on End Of Sample
                this.statusChangeBRDYBit = 0x08; // status flag: set bit3 on BRDY (End Of: ADPCM analysis/synthesis, memory reading/writing)

                //this.writeTime = 10.0 / clock; // a single byte write takes 10 cycles of main clock
                //this.readTime  = 8.0 / clock; // a single byte read takes 8 cycles of main clock
            }

            public interface StatusChangeHandler extends Consumer<Byte> {
            }

            public byte[] memory;
            /**
             * pointer of output pointers
             */
            public int[] outputPointer;
            /**
             * pointer of output pointers
             */
            public int outputPointerP;
            /**
             * pan : &output_pointer[pan]
             */
            public int panP;
            public double freqVase;
            public int memorySize;
            public int memoryMask;
            public int outputRange;
            /**
             * current address
             */
            public int now_addr;
            /**
             * currect step
             */
            public int now_step;
            /**
             * step
             */
            public int step;
            /**
             * start address
             */
            public int start;
            /**
             * limit address
             */
            public int limit;
            /**
             * end address
             */
            public int end;
            /**
             * delta scale
             */
            public int delta;
            /**
             * current volume
             */
            public int volume;
            /**
             * shift Measurement value
             */
            public int acc;
            /**
             * next Forecast
             */
            public int adpcmD;
            /**
             * current value
             */
            public int adpcmL;
            /**
             * leveling value
             */
            public int prevAcc;
            /**
             * current rom data
             */
            public byte nowData;
            /**
             * current data from reg 08
             */
            public byte cpuData;
            /**
             * port status
             */
            public byte portState;
            /**
             * control reg: SAMPLE, DA/AD, RAM TYPE (x8bit / x1bit), ROM/RAM
             */
            public byte control2;
            /**
             * address bits shift-left:
             * 8 for YM2610,
             * 5 for Y8950 and YM2608
             */
            public byte portshift;

            /**
             * address bits shift-right:
             * 0 for ROM and x8bit DRAMs,
             * 3 for x1 DRAMs
             */
            public byte dramPortShift;

            /**
             * needed for reading/writing external memory
             */
            public byte memRead;

            // handlers and parameters for the status flags support
            public StatusChangeHandler statusSetHandler;
            public StatusChangeHandler statusResetHandler;

            // note that different chips have these flags on different
            // bits of the status register

            /**
             * 1 on End Of Sample (record/playback/cycle time of AD/DA converting has passed)
             */
            public byte statusChangeEOSBit;
            /**
             * 1 after recording 2 datas (2x4bits) or after reading/writing 1 data
             */
            public byte statusChangeBRDYBit;
            /**
             * 1 if silence lasts for more than 290 miliseconds on ADPCM recording
             */
            public byte statusChangeZEROBit;

            // neither Y8950 nor YM2608 can generate IRQ when PCMBSY bit changes, so instead of above,
            // the statusflag gets ORed with PCM_BSY (below) (on each read of statusflag of Y8950 and YM2608)

            /**
             * 1 when ADPCM is playing; Y8950/YM2608 only
             */
            public byte pcmBsy;

            /**
             * adpcm registers
             */
            public byte[] reg = new byte[16];
            /**
             * which chip we're emulating
             */
            public byte emulationMode;

            private static final int DELTA_MAX = 24576;
            private static final int DELTA_MIN = 127;
            private static final int DELTA_DEF = 127;
            private static final int DECODE_RANGE = 32768;
            private static final int DECODE_MIN = -DECODE_RANGE;
            private static final int DECODE_MAX = DECODE_RANGE - 1;

            /**
             * Forecast to next Forecast (rate = *8)
             * 1/8 , 3/8 , 5/8 , 7/8 , 9/8 , 11/8 , 13/8 , 15/8
             */
            private static final int[] ymDeltatDecodeTableB1 = new int[] {
                    1, 3, 5, 7, 9, 11, 13, 15,
                    -1, -3, -5, -7, -9, -11, -13, -15,
            };

            /**
             * delta to next delta (rate= *64)
             * 0.9 , 0.9 , 0.9 , 0.9 , 1.2 , 1.6 , 2.0 , 2.4
             */
            private static final int[] ymDeltatDecodeTableB2 = new int[] {
                    57, 57, 57, 57, 77, 102, 128, 153,
                    57, 57, 57, 57, 77, 102, 128, 153
            };

            public byte read() {
                byte v = 0;

                // external memory read
                if ((this.portState & 0xe0) == 0x20) {
                    // two dummy reads
                    if (this.memRead != 0) {
                        this.now_addr = this.start << 1;
                        this.memRead--;
                        return 0;
                    }

                    if (this.now_addr != (this.end << 1)) {
                        v = this.memory[this.now_addr >> 1];

//Debug.printf("YM Delta-T memory read  $%08x, v=$%02x\n", this.now_addr >> 1, v);

                        this.now_addr += 2; // two nibbles at a time

                        // reset BRDY bit in status register, which means we are reading the memory now
                        if (this.statusResetHandler != null)
                            if (this.statusChangeBRDYBit != 0)
                                this.statusResetHandler.accept(this.statusChangeBRDYBit);

                        // setup a timer that will Callback us in 10 master clock cycles for Y8950
                        // in the Callback set the BRDY flag to 1 , which means we have another data ready.
                        // For now, we don't really do this; we simply reset and set the flag in zero time, so that the IRQ will work.
                        // set BRDY bit in status register
                        if (this.statusSetHandler != null)
                            if (this.statusChangeBRDYBit != 0)
                                this.statusSetHandler.accept(this.statusChangeBRDYBit);
                    } else {
                        // set EOS bit in status register
                        if (this.statusSetHandler != null)
                            if (this.statusChangeEOSBit != 0)
                                this.statusSetHandler.accept(this.statusChangeEOSBit);
                    }
                }

                return v;
            }

            /**
             * 0-DRAM x1, 1-ROM, 2-DRAM x8, 3-ROM (3 is bad setting - not allowed by the manual)
             */
            private static final byte[] dramRightShift = new byte[] {3, 0, 0, 0};

            /**
             * ADPCM write register
             */
            public void write(int r, int v) {
                if (r >= 0x10) return;
                this.reg[r] = (byte) v; // stock data

                switch (r) {
                case 0x00:
                    // START:
                    //   Accessing *external* memory is started when START bit (D7) is set to "1", so
                    //   you must set all conditions needed for recording/playback before starting.
                    //   If you access *CPU-managed* memory, recording/playback starts after
                    //   read/write of ADPCM data register $08.
                    //
                    // REC:
                    //   0 = ADPCM synthesis (playback)
                    //   1 = ADPCM analysis (record)
                    //
                    // MEMDATA:
                    //   0 = processor (*CPU-managed*) memory (means: using register $08)
                    //   1 = external memory (using start/end/limit registers to access memory: RAM or ROM)
                    //
                    // SPOFF:
                    //   controls output pin that should disable the speaker while ADPCM analysis
                    //
                    // RESET and REPEAT only work with external memory.
                    //
                    // some examples:
                    // value:   START, REC, MEMDAT, REPEAT, SPOFF, x,x,RESET   meaning:
                    //   C8     1      1    0       0       1      0 0 0       Analysis (recording) from AUDIO to CPU (to reg $08), sample rate in PRESCALER register
                    //   E8     1      1    1       0       1      0 0 0       Analysis (recording) from AUDIO to EXT.MEMORY,       sample rate in PRESCALER register
                    //   80     1      0    0       0       0      0 0 0       Synthesis (playing) from CPU (from reg $08) to AUDIO,sample rate in DELTA-N register
                    //   a0     1      0    1       0       0      0 0 0       Synthesis (playing) from EXT.MEMORY to AUDIO,        sample rate in DELTA-N register
                    //
                    //   60     0      1    1       0       0      0 0 0       External memory write via ADPCM data register $08
                    //   20     0      0    1       0       0      0 0 0       External memory read via ADPCM data register $08
                    //
                    // handle emulation mode
                    if (this.emulationMode == EMULATION_MODE_YM2610) {
                        v |= 0x20; // YM2610 always uses external memory and doesn't even have memory flag bit.
                    }

                    this.portState = (byte) (v & (0x80 | 0x40 | 0x20 | 0x10 | 0x01)); // start, rec, memory mode, repeat flag copy, reset(bit0)

                    if ((this.portState & 0x80) != 0) { // START,REC,MEMDATA,REPEAT,SPOFF,--,--,RESET
                        // set PCM BUSY bit
                        this.pcmBsy = 1;

                        // start ADPCM
                        this.now_step = 0;
                        this.acc = 0;
                        this.prevAcc = 0;
                        this.adpcmL = 0;
                        this.adpcmD = DELTA_DEF;
                        this.nowData = 0;
                        //if (this.start > this.end)
                        // Debug.printf("DeltaT-Warning: Start: %06X, End: %06X\n", this.start, this.end);
                    }

                    if ((this.portState & 0x20) != 0) { // do we access external memory?
                        this.now_addr = this.start << 1;
                        this.memRead = 2; // two dummy reads needed before accesing external memory via register $08

                        // if yes, then let's check if ADPCM memory is mapped and big enough
                        if (this.memory == null) {
                            //Debug.printf("YM Delta-T ADPCM rom not mapped\n");
                            this.portState = 0x00;
                            this.pcmBsy = 0;
                        } else {
                            if (this.end >= this.memorySize) { // Check End in Range
                                //Debug.printf("YM Delta-T ADPCM end out of range: $%08x\n", this.end);
                                this.end = this.memorySize - 1;
                            }
                            if (this.start >= this.memorySize) { // Check Start in Range
                                //Debug.printf("YM Delta-T ADPCM start out of range: $%08x\n", this.start);
                                this.portState = 0x00;
                                this.pcmBsy = 0;
                            }
                        }
                    } else { // we access CPU memory (ADPCM data register $08) so we only reset now_addr here
                        this.now_addr = 0;
                    }

                    if ((this.portState & 0x01) != 0) {
                        this.portState = 0x00;

                        // clear PCM BUSY bit (in status register)
                        this.pcmBsy = 0;

                        // set BRDY flag
                        if (this.statusSetHandler != null)
                            if (this.statusChangeBRDYBit != 0)
                                this.statusSetHandler.accept(this.statusChangeBRDYBit);
                    }
                    break;
                case 0x01: // L,R,-,-,SAMPLE,DA/AD,RAMTYPE,ROM
                    // handle emulation mode
                    if (this.emulationMode == EMULATION_MODE_YM2610) {
                        v |= 0x01; // YM2610 always uses ROM as an external memory and doesn't have ROM/RAM memory flag bit.
                    }

                    this.panP = ((v >> 6) & 0x03);
                    if ((this.control2 & 3) != (v & 3)) {
                        // 0-DRAM x1, 1-ROM, 2-DRAM x8, 3-ROM (3 is bad setting - not allowed by the manual)
                        if (this.dramPortShift != dramRightShift[v & 3]) {
                            this.dramPortShift = dramRightShift[v & 3];

                            // final shift value depends on chip type and memory type selected:
                            //    8 for YM2610 (ROM only),
                            //    5 for ROM for Y8950 and YM2608,
                            //    5 for x8bit DRAMs for Y8950 and YM2608,
                            //    2 for x1bit DRAMs for Y8950 and YM2608.

                            // refresh addresses
                            this.start = (this.reg[0x3] * 0x0100 | this.reg[0x2]) << (this.portshift - this.dramPortShift);
                            this.end = (this.reg[0x5] * 0x0100 | this.reg[0x4]) << (this.portshift - this.dramPortShift);
                            this.end += (1 << (this.portshift - this.dramPortShift)) - 1;
                            this.limit = (this.reg[0xd] * 0x0100 | this.reg[0xc]) << (this.portshift - this.dramPortShift);
                        }
                    }
                    this.control2 = (byte) v;
                    break;
                case 0x02: // Start Address L
                case 0x03: // Start Address H
                    this.start = (this.reg[0x3] * 0x0100 | this.reg[0x2]) << (this.portshift - this.dramPortShift);
                    //Debug.printf("deltaT start: 02=%2x 03=%2x addr=%8x\n",this.reg[0x2], this.reg[0x3],this.start );
                    break;
                case 0x04: // Stop Address L
                case 0x05: // Stop Address H
                    this.end = (this.reg[0x5] * 0x0100 | this.reg[0x4]) << (this.portshift - this.dramPortShift);
                    this.end += (1 << (this.portshift - this.dramPortShift)) - 1;
                    //Debug.printf("deltaT end  : 04=%2x 05=%2x addr=%8x\n",this.reg[0x4], this.reg[0x5],this.end   );
                    break;
                case 0x06: // Prescale L (ADPCM and Record frq)
                case 0x07: // Prescale H
                    break;
                case 0x08: // ADPCM data

                    // some examples:
                    // value:   START, REC, MEMDAT, REPEAT, SPOFF, x,x,RESET   meaning:
                    //   C8     1      1    0       0       1      0 0 0       Analysis (recording) from AUDIO to CPU (to reg $08), sample rate in PRESCALER register
                    //   E8     1      1    1       0       1      0 0 0       Analysis (recording) from AUDIO to EXT.MEMORY,       sample rate in PRESCALER register
                    //   80     1      0    0       0       0      0 0 0       Synthesis (playing) from CPU (from reg $08) to AUDIO,sample rate in DELTA-N register
                    //   a0     1      0    1       0       0      0 0 0       Synthesis (playing) from EXT.MEMORY to AUDIO,        sample rate in DELTA-N register
                    //
                    //   60     0      1    1       0       0      0 0 0       External memory write via ADPCM data register $08
                    //   20     0      0    1       0       0      0 0 0       External memory read via ADPCM data register $08

                    // external memory write
                    if ((this.portState & 0xe0) == 0x60) {
                        if (this.memRead != 0) {
                            this.now_addr = this.start << 1;
                            this.memRead = 0;
                        }

                        //Debug.printf("YM Delta-T memory write $%08x, v=$%02x\n", this.now_addr >> 1, v);

                        if (this.now_addr != (this.end << 1)) {
                            this.memory[this.now_addr >> 1] = (byte) v;
                            this.now_addr += 2; // two nibbles at a time

                            // reset BRDY bit in status register, which means we are processing the write
                            if (this.statusResetHandler != null)
                                if (this.statusChangeBRDYBit != 0)
                                    this.statusResetHandler.accept(this.statusChangeBRDYBit);

                            // setup a timer that will Callback us in 10 master clock cycles for Y8950
                            // in the Callback set the BRDY flag to 1 , which means we have written the data.
                            // For now, we don't really do this; we simply reset and set the flag in zero time, so that the IRQ will work.
                            // set BRDY bit in status register
                            if (this.statusSetHandler != null)
                                if (this.statusChangeBRDYBit != 0)
                                    this.statusSetHandler.accept(this.statusChangeBRDYBit);

                        } else {
                            // set EOS bit in status register
                            if (this.statusSetHandler != null)
                                if (this.statusChangeEOSBit != 0)
                                    this.statusSetHandler.accept(this.statusChangeEOSBit);
                        }

                        return;
                    }

                    // ADPCM synthesis from CPU
                    if ((this.portState & 0xe0) == 0x80) {
                        this.cpuData = (byte) v;

                        // Reset BRDY bit in status register, which means we are full of data
                        if (this.statusResetHandler != null)
                            if (this.statusChangeBRDYBit != 0)
                                this.statusResetHandler.accept(this.statusChangeBRDYBit);
                        return;
                    }

                    break;
                case 0x09: // DELTA-N L (ADPCM Playback Prescaler)
                case 0x0a: // DELTA-N H
                    this.delta = this.reg[0xa] * 0x0100 | this.reg[0x9];
                    this.step = (int) ((double) (this.delta /* *(1<<(YM_DELTAT_SHIFT-16)) */) * (this.freqVase));
                    //Debug.printf("deltaT deltan:09=%2x 0a=%2x\n",this.reg[0x9], this.reg[0xa]);
                    break;
                case 0x0b: { // Output level control (volume, linear)
                    int oldvol = this.volume;
                    this.volume = (v & 0xff) * (this.outputRange / 256) / DECODE_RANGE;
                    //          v     *     ((1<<16)>>8)        >>  15;
                    //  thus:   v     *     (1<<8)              >>  15;
                    //  thus: output_range must be (1 << (15+8)) at least
                    //          v     *     ((1<<23)>>8)        >>  15;
                    //          v     *     (1<<15)             >>  15;
                    //Debug.printf("deltaT vol = %2x\n",v&0xff);
                    if (oldvol != 0) {
                        this.adpcmL = (int) ((double) this.adpcmL / (double) oldvol * (double) this.volume);
                    }
                }
                break;
                case 0x0c: // Limit Address L
                case 0x0d: // Limit Address H
                    this.limit = (this.reg[0xd] * 0x0100 | this.reg[0xc]) << (this.portshift - this.dramPortShift);
                    //Debug.printf("deltaT limit: 0c=%2x 0d=%2x addr=%8x\n",this.reg[0xc], this.reg[0xd],this.limit );
                    break;
                }
            }

            public void reset(int pan, int emulation_mode) {
                this.now_addr = 0;
                this.now_step = 0;
                this.step = 0;
                this.start = 0;
                this.end = 0;
                this.limit = 0xffffffff; // this way YM2610 and Y8950 (both of which don't have limit address reg) will still work
                this.volume = 0;
                this.panP = pan;
                this.acc = 0;
                this.prevAcc = 0;
                this.adpcmD = 127;
                this.adpcmL = 0;
                this.emulationMode = (byte) emulation_mode;
                this.portState = (byte) ((emulation_mode == EMULATION_MODE_YM2610) ? 0x20 : 0);
                this.control2 = (byte) ((emulation_mode == EMULATION_MODE_YM2610) ? 0x01 : 0); // default setting depends on the emulation mode. MSX demo called "facdemo_4" doesn't setup control2 register at all and still works
                this.dramPortShift = dramRightShift[this.control2 & 3];

                // The flag mask register disables the BRDY after the reset, however
                // as soon as the mask is enabled the flag needs to be set.

                // set BRDY bit in status register
                if (this.statusSetHandler != null)
                    if (this.statusChangeBRDYBit != 0)
                        this.statusSetHandler.accept(this.statusChangeBRDYBit);
            }

//            void postLoad(,byte[] regs) {
//                // to keep adpcml
//                this.volume = 0;
//                // update
//                for (int r = 1; r < 16; r++)
//                    YM_DELTAT_ADPCM_Write(DeltaT, r, regs[r]);
//                this.reg[0] = regs[0];
//
//                // current rom data
//                if (this.memory)
//                    this.now_data = *(this.memory + (this.now_addr >> 1));
//            }
//
//            void saveState() {
//// #ifdef __STATE_H__
//                state_save_register_device_item(device, 0, this.portstate);
//                state_save_register_device_item(device, 0, this.now_addr);
//                state_save_register_device_item(device, 0, this.now_step);
//                state_save_register_device_item(device, 0, this.acc);
//                state_save_register_device_item(device, 0, this.prev_acc);
//                state_save_register_device_item(device, 0, this.adpcmd);
//                state_save_register_device_item(device, 0, this.adpcml);
//// #endif
//            }

            private static int limit(int val, int max, int min) {
                if (val > max) val = max;
                else if (val < min) val = min;

                return val;
            }

            private void synthesisFromExternalMemory() {
                int data;

                this.now_step += this.step;
                if (this.now_step >= (1 << YM_DELTAT_SHIFT)) {
                    int step = this.now_step >> YM_DELTAT_SHIFT;
                    this.now_step &= (1 << YM_DELTAT_SHIFT) - 1;
                    do {

                        if (this.now_addr == (this.limit << 1))
                            this.now_addr = 0;

                        if (this.now_addr == (this.end << 1)) { // 12-06-2001 JB: corrected comparison. Was > instead of ==
                            if ((this.portState & 0x10) != 0) {
                                // repeat start
                                this.now_addr = this.start << 1;
                                this.acc = 0;
                                this.adpcmD = DELTA_DEF;
                                this.prevAcc = 0;
                            } else {
                                // set EOS bit in status register
                                if (this.statusSetHandler != null)
                                    if (this.statusChangeEOSBit != 0)
                                        this.statusSetHandler.accept(this.statusChangeEOSBit);

                                // clear PCM BUSY bit (reflected in status register)
                                this.pcmBsy = 0;

                                this.portState = 0;
                                this.adpcmL = 0;
                                this.prevAcc = 0;
                                return;
                            }
                        }

                        if ((this.now_addr & 1) != 0) data = this.nowData & 0x0f;
                        else {
                            this.nowData = this.memory[this.now_addr >> 1];
                            data = this.nowData >> 4;
                        }

                        this.now_addr++;
                        // 12-06-2001 JB:
                        // YM2610 address register is 24 bits wide.
                        // The "+1" is there because we use 1 bit more for nibble calculations.
                        // WARNING:
                        // Side effect: we should take the size of the mapped ROM into account
                        this.now_addr &= this.memoryMask;

                        // store accumulator value
                        this.prevAcc = this.acc;

                        // Forecast to next Forecast
                        this.acc += (ymDeltatDecodeTableB1[data] * this.adpcmD / 8);
                        this.acc = limit(this.acc, DECODE_MAX, DECODE_MIN);

                        // delta to next delta
                        this.adpcmD = (this.adpcmD * ymDeltatDecodeTableB2[data]) / 64;
                        this.adpcmD = limit(this.adpcmD, DELTA_MAX, DELTA_MIN);

                        // ElSemi: Fix interpolator.
                        //this.prev_acc = prev_acc + ((this.acc - prev_acc) / 2 );

                    } while (--step != 0);
                }

                // ElSemi: Fix interpolator.
                this.adpcmL = this.prevAcc * ((1 << YM_DELTAT_SHIFT) - this.now_step);
                this.adpcmL += (this.acc * this.now_step);
                this.adpcmL = (this.adpcmL >> YM_DELTAT_SHIFT) * this.volume;

                // output for work of output channels (outd[OPNxxxx])
                this.outputPointer[this.panP] += this.adpcmL;
            }

            private void synthesisFromCPUMemory() {
                int data;

                this.now_step += this.step;
                if (this.now_step >= (1 << YM_DELTAT_SHIFT)) {
                    int step = this.now_step >> YM_DELTAT_SHIFT;
                    this.now_step &= (1 << YM_DELTAT_SHIFT) - 1;
                    do {

                        if ((this.now_addr & 1) != 0) {
                            data = this.nowData & 0x0f;

                            this.nowData = this.cpuData;

                            // after we used CPU_data, we set BRDY bit in status register,
                            // which means we are ready to accept another byte of data
                            if (this.statusSetHandler != null)
                                if (this.statusChangeBRDYBit != 0)
                                    this.statusSetHandler.accept(this.statusChangeBRDYBit);
                        } else {
                            data = this.nowData >> 4;
                        }

                        this.now_addr++;

                        // store accumulator value
                        this.prevAcc = this.acc;

                        // Forecast to next Forecast
                        this.acc += (ymDeltatDecodeTableB1[data] * this.adpcmD / 8);
                        this.acc = limit(this.acc, DECODE_MAX, DECODE_MIN);

                        // delta to next delta
                        this.adpcmD = (this.adpcmD * ymDeltatDecodeTableB2[data]) / 64;
                        this.adpcmD = limit(this.adpcmD, DELTA_MAX, DELTA_MIN);


                    } while ((--step) != 0);

                }

                // ElSemi: Fix interpolator.
                this.adpcmL = this.prevAcc * ((1 << YM_DELTAT_SHIFT) - this.now_step);
                this.adpcmL += (this.acc * this.now_step);
                this.adpcmL = (this.adpcmL >> YM_DELTAT_SHIFT) * this.volume;

                // output for work of output channels (outd[OPNxxxx])
                this.outputPointer[this.panP] += this.adpcmL;
            }

            /** ADPCM B (Delta-T control type) */
            public void calcAdpcm() {

                // some examples:
                // value:   START, REC, MEMDAT, REPEAT, SPOFF, x,x,RESET   meaning:
                //   80     1      0    0       0       0      0 0 0       Synthesis (playing) from CPU (from reg $08) to AUDIO,sample rate in DELTA-N register
                //   a0     1      0    1       0       0      0 0 0       Synthesis (playing) from EXT.MEMORY to AUDIO,        sample rate in DELTA-N register
                //   C8     1      1    0       0       1      0 0 0       Analysis (recording) from AUDIO to CPU (to reg $08), sample rate in PRESCALER register
                //   E8     1      1    1       0       1      0 0 0       Analysis (recording) from AUDIO to EXT.MEMORY,       sample rate in PRESCALER register
                //
                //   60     0      1    1       0       0      0 0 0       External memory write via ADPCM data register $08
                //   20     0      0    1       0       0      0 0 0       External memory read via ADPCM data register $08

                if ((this.portState & 0xe0) == 0xa0) {
                    synthesisFromExternalMemory();
                    return;
                }

                if ((this.portState & 0xe0) == 0x80) {
                    // ADPCM synthesis from CPU-managed memory (from reg $08)
                    synthesisFromCPUMemory(); // change output based on data in ADPCM data reg ($08)
                }

                // TODO: ADPCM analysis
                //if ( (this.portstate & 0xe0)==0xc0 )
                //if ( (this.portstate & 0xe0)==0xe0 )
            }

            public void calcMemMask() {
                int maskSize = 0x01;
                while (maskSize < this.memorySize)
                    maskSize <<= 1;
                this.memoryMask = (maskSize << 1) - 1; // it's Mask<<1 because of the nibbles
            }
        }

        /**
         * output final shift
         */
        private static final int FINAL_SH = 0;

        private static final int MAXOUT = 32767;
        private static final int MINOUT = -32768;

        /**
         * 16.16 fixed point (frequency calculations)
         */
        private static final int FREQ_SH = 16;
        /**
         * 16.16 fixed point (EG timing)
         */
        private static final int EG_SH = 16;
        /**
         * 8.24 fixed point (LFO calculations)
         */
        private static final int LFO_SH = 24;
        /**
         * 16.16 fixed point (timers calculations)
         */
        private static final int TIMER_SH = 16;

        private static final int FREQ_MASK = (1 << FREQ_SH) - 1;

        // envelope output entries

        private static final int ENV_BITS = 10;
        private static final int ENV_LEN = 1 << ENV_BITS;
        private static final double ENV_STEP = 128.0 / ENV_LEN;

        private static final int MAX_ATT_INDEX = (1 << (ENV_BITS - 1)) - 1;
        private static final int MIN_ATT_INDEX = 0;

        // sinwave entries

        private static final int SIN_BITS = 10;
        private static final int SIN_LEN = 1 << SIN_BITS;
        private static final int SIN_MASK = SIN_LEN - 1;

        /**
         * 8 bits addressing (real chip)
         */
        private static final int TL_RES_LEN = 256;


        // register number to channel number , slot offset

        private static final int SLOT1 = 0;
        private static final int SLOT2 = 1;

        // Envelope Generator phases

        private static final int EG_ATT = 4;
        private static final int EG_DEC = 3;
        private static final int EG_SUS = 2;
        private static final int EG_REL = 1;
        private static final int EG_OFF = 0;

        /**
         * waveform select
         */
        private static final int SUB_TYPE_WAVESEL = 0x01;
        /**
         * DELTA-T ADPCM unit
         */
        private static final int SUB_TYPE_ADPCM = 0x02;
        /**
         * keyboard interface
         */
        private static final int SUB_TYPE_KEYBOARD = 0x04;
        /**
         * I/O port
         */
        private static final int SUB_OPL_TYPE_IO = 0x08;

        // Generic interface section

        private static final int TYPE_YM3526 = 0;
        private static final int TYPE_YM3812 = SUB_TYPE_WAVESEL;
        private static final int TYPE_Y8950 = SUB_TYPE_ADPCM | SUB_TYPE_KEYBOARD | SUB_OPL_TYPE_IO;

        public static class Slot {

            /**
             * attack rate: AR<<2
             */
            public int ar;
            /**
             * decay rate:  DR<<2
             */
            public int dr;
            /**
             * release rate:RR<<2
             */
            public int rr;
            /**
             * key scale rate
             */
            public byte KSR;
            /**
             * keyscale level
             */
            public byte ksl;
            /**
             * key scale rate: kcode>>KSR
             */
            public byte ksr;
            /**
             * multiple: mul_tab[ML]
             */
            public byte mul;

            // Phase Generator

            /**
             * frequency counter
             */
            public int cnt;
            /**
             * frequency counter step
             */
            public int incr;
            /**
             * feedback shift value
             */
            public byte fb;
            /** slot1 output pointer */
            //public int connect1;
            /**
             * slot1 output pointer
             */
            public int ptrConnect1;
            /**
             * slot1 output for feedback
             */
            public int[] op1Out = new int[2];
            /**
             * connection (algorithm) type
             */
            public byte con;

            // Envelope Generator

            /**
             * percussive/non-percussive mode
             */
            public byte egType;
            /**
             * phase type
             */
            public byte state;
            /**
             * total level: TL << 2
             */
            public int tl;
            /**
             * adjusted now TL
             */
            public int tll;
            /**
             * envelope counter
             */
            public int volume;
            /**
             * sustain level: sl_tab[SL]
             */
            public int sl;
            /**
             * (attack state)
             */
            public byte egShAr;
            /**
             * (attack state)
             */
            public byte egSelAr;
            /**
             * (decay state)
             */
            public byte egShDr;
            /**
             * (decay state)
             */
            public byte egSelDr;
            /**
             * (release state)
             */
            public byte egShRr;
            /**
             * (release state)
             */
            public byte egSelRr;
            /**
             * 0 = KEY OFF, >0 = KEY ON
             */
            public int key;

            // LFO

            /**
             * LFO Amplitude Modulation enable mask
             */
            public int amMask;
            /**
             * LFO Phase Modulation enable flag (active high)
             */
            public byte vib;

            /**
             * waveform select
             */
            public int waveTable;

            private int volume_calc(int lfoAm) {
                return tll + volume + (lfoAm & amMask);
            }

            private void keyOn(int key_set) {
                if (key == 0) {
                    // restart Phase Generator
                    cnt = 0;
                    // phase . Attack
                    state = EG_ATT;
                }
                key |= key_set;
            }

            private void keyOff(int key_clr) {
                if (key != 0) {
                    key &= key_clr;

                    if (key == 0) {
                        // phase . Release
                        if (state > EG_REL)
                            state = EG_REL;
                    }
                }
            }

            public void setMul(int v) {
                this.mul = mulTab[v & 0x0f];
                this.KSR = (byte) ((v & 0x10) != 0 ? 0 : 2);
                this.egType = (byte) (v & 0x20);
                this.vib = (byte) (v & 0x40);
                this.amMask = (v & 0x80) != 0 ? ~0 : 0;
            }

            public void reset() {
                this.waveTable = 0;
                this.state = EG_OFF;
                this.volume = MAX_ATT_INDEX;
            }
        }

        public static class Channel {

            /** FM channel slots */
            public Slot[] slots = new Slot[] {new Slot(), new Slot()};

            // phase generator state

            /**
             * block+fnum
             */
            public int blockFNum;
            /**
             * Freq. Increment base
             */
            public int fc;
            /**
             * KeyScaleLevel Base step
             */
            public int kslBase;
            /**
             * key code (for key scaling)
             */
            public byte kCode;
            public byte muted;

            public void reset() {
                for (int s = 0; s < 2; s++) {
                    // wave table
                    this.slots[s].reset();
                }
            }
        }

        /**
         * select output bits size of output : 8 or 16
         */
        private static final int OPL_SAMPLE_BITS = 16;

        public interface TimerHandler extends BiConsumer<Integer, Integer> {
        }

        public interface IrqHandler extends Consumer<Integer> {
        }

        public interface UpdateHandler extends Consumer<Integer> {
        }

        public interface PortWriteHandler extends Consumer<Byte> {
        }

        public interface PortReadHandler extends Supplier<Byte> {
        }

        /**
         * OPL/OPL2 chips have 9 channels
         */
        public Channel[] channels = new Channel[] {
                new Channel(), new Channel(), new Channel(), new Channel(), new Channel(),
                new Channel(), new Channel(), new Channel(), new Channel()
        };

        /**
         * Mute Special: 5 Rhythm + 1 DELTA-T Channel
         */
        public byte[] muteSpc = new byte[6];

        /**
         * Global envelope generator counter
         */
        public int egCnt;
        /**
         * Global envelope generator counter works at frequency = chipclock/72
         */
        public int egTimer;
        /**
         * step of eg_timer
         */
        public int egTimerAdd;
        /**
         * envelope generator timer overlfows every 1 sample (on real chip)
         */
        public int egTimerOverflow;

        /**
         * Rhythm mode
         */
        public byte rhythm;

        /**
         * fnumber.increment counter
         */
        public int[] fnTab = new int[1024];

        // LFO
        public int lfoAm;
        public int lfoPm;

        public byte lfoAmDepth;
        public byte lfoPmDepthRange;
        public int lfoAmCnt;
        public int lfoAmInc;
        public int lfoPmCnt;
        public int lfoPmInc;

        /**
         * 23 bit noise shift register
         */
        public int noiseRng;
        /**
         * current noise 'phase'
         */
        public int noiseP;
        /**
         * current noise period
         */
        public int noiseF;

        /**
         * waveform select enable flag
         */
        public byte waveSel;

        /**
         * timer counters
         */
        public int[] t = new int[2];
        /** timer enable */
        public byte[] st = new byte[2];

        /**
         * Delta-T ADPCM unit (Y8950)
         */
        public DeltaT deltaT = new DeltaT();

        // Keyboard and I/O ports interface

        public byte portDirection;
        public byte portLatch;
        public PortReadHandler portReadHandler;
        public PortWriteHandler portWriteHandler;
        public PortReadHandler keyboardReadHandler;
        public PortWriteHandler keyboardWriteHandler;

        // external event Callback handlers

        /**
         * TIMER handler
         */
        public TimerHandler timerHandler;
        /**
         * IRQ handler
         */
        public IrqHandler irqHandler;
        /**
         * stream update handler
         */
        public UpdateHandler updateHandler;

        /**
         * chip type
         */
        public byte type;
        /**
         * address register
         */
        public byte address;
        /**
         * status flag
         */
        public byte status;
        /**
         * status mask
         */
        public byte statusMask;
        /**
         * Reg.08 : CSM,notesel,etc.
         */
        public byte mode;

        /**
         * master clock  (Hz)
         */
        public int clock;
        /**
         * sampling rate (Hz)
         */
        public int rate;
        /**
         * frequency base
         */
        public double freqBase;
        /** Timer base time (==sampling time) */
        //attotime TimerBase;

        /**
         * phase modulation input (SLOT 2)
         */
        public int phaseModulation;
        public int[] output = new int[1];
        /**
         * for Y8950 DELTA-T, chip is mono, that 4 here is just for safety
         */
        public int[] outputDeltaT = new int[4];

        /**
         * mapping of register number (offset) to slot number used by the emulator
         */
        private static final int[] slot_array = new int[] {
                0, 2, 4, 1, 3, 5, -1, -1,
                6, 8, 10, 7, 9, 11, -1, -1,
                12, 14, 16, 13, 15, 17, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1
        };

        /**
         * key scale level
         * table is 3dB/octave , DV converts this into 6dB/octave
         * 0.1875 is bit 0 weight of the envelope counter (volume) expressed in the 'decibel' scale
         */
        private static final double DV = 0.1875 / 2.0;
        private static final int[] kslTab = new int[] {
                // OCT 0
                (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                // OCT 1
                (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                (int) (0.000 / DV), (int) (0.750 / DV), (int) (1.125 / DV), (int) (1.500 / DV),
                (int) (1.875 / DV), (int) (2.250 / DV), (int) (2.625 / DV), (int) (3.000 / DV),
                // OCT 2
                (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV),
                (int) (0.000 / DV), (int) (1.125 / DV), (int) (1.875 / DV), (int) (2.625 / DV),
                (int) (3.000 / DV), (int) (3.750 / DV), (int) (4.125 / DV), (int) (4.500 / DV),
                (int) (4.875 / DV), (int) (5.250 / DV), (int) (5.625 / DV), (int) (6.000 / DV),
                // OCT 3
                (int) (0.000 / DV), (int) (0.000 / DV), (int) (0.000 / DV), (int) (1.875 / DV),
                (int) (3.000 / DV), (int) (4.125 / DV), (int) (4.875 / DV), (int) (5.625 / DV),
                (int) (6.000 / DV), (int) (6.750 / DV), (int) (7.125 / DV), (int) (7.500 / DV),
                (int) (7.875 / DV), (int) (8.250 / DV), (int) (8.625 / DV), (int) (9.000 / DV),
                // OCT 4
                (int) (0.000 / DV), (int) (0.000 / DV), (int) (3.000 / DV), (int) (4.875 / DV),
                (int) (6.000 / DV), (int) (7.125 / DV), (int) (7.875 / DV), (int) (8.625 / DV),
                (int) (9.000 / DV), (int) (9.750 / DV), (int) (10.125 / DV), (int) (10.500 / DV),
                (int) (10.875 / DV), (int) (11.250 / DV), (int) (11.625 / DV), (int) (12.000 / DV),
                // OCT 5
                (int) (0.000 / DV), (int) (3.000 / DV), (int) (6.000 / DV), (int) (7.875 / DV),
                (int) (9.000 / DV), (int) (10.125 / DV), (int) (10.875 / DV), (int) (11.625 / DV),
                (int) (12.000 / DV), (int) (12.750 / DV), (int) (13.125 / DV), (int) (13.500 / DV),
                (int) (13.875 / DV), (int) (14.250 / DV), (int) (14.625 / DV), (int) (15.000 / DV),
                // OCT 6
                (int) (0.000 / DV), (int) (6.000 / DV), (int) (9.000 / DV), (int) (10.875 / DV),
                (int) (12.000 / DV), (int) (13.125 / DV), (int) (13.875 / DV), (int) (14.625 / DV),
                (int) (15.000 / DV), (int) (15.750 / DV), (int) (16.125 / DV), (int) (16.500 / DV),
                (int) (16.875 / DV), (int) (17.250 / DV), (int) (17.625 / DV), (int) (18.000 / DV),
                // OCT 7
                (int) (0.000 / DV), (int) (9.000 / DV), (int) (12.000 / DV), (int) (13.875 / DV),
                (int) (15.000 / DV), (int) (16.125 / DV), (int) (16.875 / DV), (int) (17.625 / DV),
                (int) (18.000 / DV), (int) (18.750 / DV), (int) (19.125 / DV), (int) (19.500 / DV),
                (int) (19.875 / DV), (int) (20.250 / DV), (int) (20.625 / DV), (int) (21.000 / DV)
        };

        /**
         * 0 / 3.0 / 1.5 / 6.0 dB/OCT
         */
        private static final int[] ksl_shift = new int[] {31, 1, 2, 0};

        /**
         * sustain level table (3dB per step)
         * 0 - 15: 0, 3, 6, 9,12,15,18,21,24,27,30,33,36,39,42,93 (dB)
         */
        private static int sc(double db) {
            return (int) (db * (2.0 / ENV_STEP));
        }

        private static final int[] slTab = new int[] {
                sc(0), sc(1), sc(2), sc(3), sc(4), sc(5), sc(6), sc(7),
                sc(8), sc(9), sc(10), sc(11), sc(12), sc(13), sc(14), sc(31)
        };

        private static final int RATE_STEPS = 8;
        private static final byte[] egInc = new byte[] {
                //cycle:0  1  2  3  4  5  6  7
                /* 0 */ 0, 1, 0, 1, 0, 1, 0, 1, // rates 00..12 0 (increment by 0 or 1)
                /* 1 */ 0, 1, 0, 1, 1, 1, 0, 1, // rates 00..12 1
                /* 2 */ 0, 1, 1, 1, 0, 1, 1, 1, // rates 00..12 2
                /* 3 */ 0, 1, 1, 1, 1, 1, 1, 1, // rates 00..12 3

                /* 4 */ 1, 1, 1, 1, 1, 1, 1, 1, // rate 13 0 (increment by 1)
                /* 5 */ 1, 1, 1, 2, 1, 1, 1, 2, // rate 13 1
                /* 6 */ 1, 2, 1, 2, 1, 2, 1, 2, // rate 13 2
                /* 7 */ 1, 2, 2, 2, 1, 2, 2, 2, // rate 13 3

                /* 8 */ 2, 2, 2, 2, 2, 2, 2, 2, // rate 14 0 (increment by 2)
                /* 9 */ 2, 2, 2, 4, 2, 2, 2, 4, // rate 14 1
                /*10 */ 2, 4, 2, 4, 2, 4, 2, 4, // rate 14 2
                /*11 */ 2, 4, 4, 4, 2, 4, 4, 4, // rate 14 3

                /*12 */ 4, 4, 4, 4, 4, 4, 4, 4, // rates 15 0, 15 1, 15 2, 15 3 (increment by 4)
                /*13 */ 8, 8, 8, 8, 8, 8, 8, 8, // rates 15 2, 15 3 for attack
                /*14 */ 0, 0, 0, 0, 0, 0, 0, 0, // infinity rates for attack and decay(s)
        };

        private static byte o(int a) {
            return (byte) (a * RATE_STEPS);
        }

        /**
         * Envelope Generator rates (16 + 64 rates + 16 RKS)
         * note that there is no o(13) in this table - it's directly in the code
         */
        private static final byte[] egRateSelect = new byte[] {
                // 16 infinite time rates
                o(14), o(14), o(14), o(14), o(14), o(14), o(14), o(14),
                o(14), o(14), o(14), o(14), o(14), o(14), o(14), o(14),

                // rates 00-12
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

                // rate 13
                o(4), o(5), o(6), o(7),

                // rate 14
                o(8), o(9), o(10), o(11),

                // rate 15
                o(12), o(12), o(12), o(12),

                // 16 dummy rates (same as 15 3)
                o(12), o(12), o(12), o(12), o(12), o(12), o(12), o(12),
                o(12), o(12), o(12), o(12), o(12), o(12), o(12), o(12),

        };

        private static byte o2(int a) {
            return (byte) (a * 1);
        }

        /**
         * Envelope Generator counter shifts (16 + 64 rates + 16 RKS)
         *
         * rate  0,    1,    2,    3,   4,   5,   6,  7,  8,  9,  10, 11, 12, 13, 14, 15
         * shift 12,   11,   10,   9,   8,   7,   6,  5,  4,  3,  2,  1,  0,  0,  0,  0
         * mask  4095, 2047, 1023, 511, 255, 127, 63, 31, 15, 7,  3,  1,  0,  0,  0,  0
         */
        private static final byte[] egRateShift = new byte[] {
                // 16 infinite time rates
                o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),
                o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),

                // rates 00-12
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

                // rate 13
                o2(0), o2(0), o2(0), o2(0),

                // rate 14
                o2(0), o2(0), o2(0), o2(0),

                // rate 15
                o2(0), o2(0), o2(0), o2(0),

                // 16 dummy rates (same as 15 3)
                o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),
                o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0), o2(0),
        };

        private static final byte ML = 2;

        /**
         * multiple table
         */
        private static final byte[] mulTab = new byte[] {
                // 1/2, 1, 2, 3, 4, 5, 6, 7, 8, 9,10,10,12,12,15,15
                (byte) (0.50 * ML), (byte) (1.00 * ML), (byte) (2.00 * ML), (byte) (3.00 * ML), (byte) (4.00 * ML), (byte) (5.00 * ML), (byte) (6.00 * ML), (byte) (7.00 * ML),
                (byte) (8.00 * ML), (byte) (9.00 * ML), (byte) (10.00 * ML), (byte) (10.00 * ML), (byte) (12.00 * ML), (byte) (12.00 * ML), (byte) (15.00 * ML), (byte) (15.00 * ML)
        };

        /**
         * TL_TAB_LEN is calculated as:
         * 12 - sinus amplitude bits     (Y axis)
         * 2  - sinus sign bit           (Y axis)
         * TL_RES_LEN - sinus resolution (X axis)
         */
        private static final int TL_TAB_LEN = 12 * 2 * TL_RES_LEN;

        private int[] tlTab = new int[TL_TAB_LEN];

        private static final int ENV_QUIET = TL_TAB_LEN >> 4;

        /**
         * sin waveform table in 'decibel' scale
         * four waveforms on OPL2 type chips
         */
        private int[] sinTab = new int[SIN_LEN * 4];

        private static final int LFO_AM_TAB_ELEMENTS = 210;

        /**
         * LFO Amplitude Modulation table (verified on real YM3812)
         * 27 output levels (triangle waveform); 1 level takes one of: 192, 256 or 448 samples
         * <p>
         * Length: 210 elements.
         * <p>
         * Each of the elements has to be repeated
         * exactly 64 times (on 64 consecutive samples).
         * The whole table takes: 64 * 210 = 13440 samples.
         * <p>
         * When AM = 1 data is used directly
         * When AM = 0 data is divided by 4 before being used (losing precision is important)
         */
        private static final byte[] lfoAmTable = new byte[] {
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

        /**
         * LFO Phase Modulation table (verified on real YM3812)
         */
        private static final byte[] lfoPmTable = new byte[] {
                // FNUM2/FNUM = 00 0xxxxxxx (0x0000)
                0, 0, 0, 0, 0, 0, 0, 0, // LFO PM depth = 0
                0, 0, 0, 0, 0, 0, 0, 0, // LFO PM depth = 1

                // FNUM2/FNUM = 00 1xxxxxxx (0x0080)
                0, 0, 0, 0, 0, 0, 0, 0, // LFO PM depth = 0
                1, 0, 0, 0, -1, 0, 0, 0, // LFO PM depth = 1

                // FNUM2/FNUM = 01 0xxxxxxx (0x0100)
                1, 0, 0, 0, -1, 0, 0, 0, // LFO PM depth = 0
                2, 1, 0, -1, -2, -1, 0, 1, // LFO PM depth = 1

                // FNUM2/FNUM = 01 1xxxxxxx (0x0180)
                1, 0, 0, 0, -1, 0, 0, 0, // LFO PM depth = 0
                3, 1, 0, -1, -3, -1, 0, 1, // LFO PM depth = 1

                // FNUM2/FNUM = 10 0xxxxxxx (0x0200)
                2, 1, 0, -1, -2, -1, 0, 1, // LFO PM depth = 0
                4, 2, 0, -2, -4, -2, 0, 2, // LFO PM depth = 1

                // FNUM2/FNUM = 10 1xxxxxxx (0x0280)
                2, 1, 0, -1, -2, -1, 0, 1, // LFO PM depth = 0
                5, 2, 0, -2, -5, -2, 0, 2, // LFO PM depth = 1

                // FNUM2/FNUM = 11 0xxxxxxx (0x0300)
                3, 1, 0, -1, -3, -1, 0, 1, // LFO PM depth = 0
                6, 3, 0, -3, -6, -3, 0, 3, // LFO PM depth = 1

                // FNUM2/FNUM = 11 1xxxxxxx (0x0380)
                3, 1, 0, -1, -3, -1, 0, 1, // LFO PM depth = 0
                7, 3, 0, -3, -7, -3, 0, 3 // LFO PM depth = 1
        };

        // synchronized level of common table

        private int numLock = 0;

        private Slot slot7_1() {
            return this.channels[7].slots[SLOT1];
        }

        private Slot slot7_2() {
            return this.channels[7].slots[SLOT2];
        }

        private Slot slot8_1() {
            return this.channels[8].slots[SLOT1];
        }

        private Slot slot8_2() {
            return this.channels[8].slots[SLOT2];
        }

        /**
         * status set and IRQ handling
         */
        private void setStatus(int flag) {
            // set status flag
            this.status |= (byte) flag;
            if ((this.status & 0x80) == 0) {
                if ((this.status & this.statusMask) != 0) { // IRQ on
                    this.status |= 0x80;
                    // Callback user interrupt handler (IRQ is OFF to ON)
                    if (this.irqHandler != null) this.irqHandler.accept(1);
                }
            }
        }

        /**
         * status reset and IRQ handling
         */
        private void resetStatus(int flag) {
            // reset status flag
            this.status &= (byte) ~(byte) flag;
            if ((this.status & 0x80) != 0) {
                if ((this.status & this.statusMask) == 0) {
                    this.status &= 0x7f;
                    // Callback user interrupt handler (IRQ is ON to OFF)
                    if (this.irqHandler != null) this.irqHandler.accept(0);
                }
            }
        }

        /**
         * IRQ mask set
         */
        private void setStatusMask(int flag) {
            this.statusMask = (byte) flag;
            // IRQ handling check
            setStatus(0);
            resetStatus(0);
        }

        /**
         * advance LFO to next sample
         */
        private void advanceLfo() {
            // LFO
            this.lfoAmCnt += this.lfoAmInc;
            if (this.lfoAmCnt >= (LFO_AM_TAB_ELEMENTS << LFO_SH)) // lfo_am_table is 210 elements long
                this.lfoAmCnt -= (LFO_AM_TAB_ELEMENTS << LFO_SH);

            byte tmp = lfoAmTable[this.lfoAmCnt >> LFO_SH];

            if (this.lfoAmDepth != 0)
                this.lfoAm = tmp;
            else
                this.lfoAm = (byte) (tmp >> 2);

            this.lfoPmCnt += this.lfoPmInc;
            this.lfoPm = ((this.lfoPmCnt >> LFO_SH) & 7) | this.lfoPmDepthRange;
        }

        private void refreshEg() {
            Channel ch;
            Slot op;
            int i;
            int new_vol;

            for (i = 0; i < 9 * 2; i++) {
                ch = this.channels[i / 2];
                op = ch.slots[i & 1];

                // Envelope Generator
                switch (op.state) {
                case EG_ATT: // attack phase
                    if ((this.egCnt & ((1 << op.egShAr) - 1)) == 0) {
                        new_vol = op.volume + ((~op.volume *
                                (egInc[op.egSelAr + ((this.egCnt >> op.egShAr) & 7)])
                        ) >> 3);
                        if (new_vol <= MIN_ATT_INDEX) {
                            op.volume = MIN_ATT_INDEX;
                            op.state = EG_DEC;
                        }
                    }
                    break;
//                case EG_DEC: // decay phase
//                    if ( !(this.eg_cnt & ((1<<Op.eg_sh_dr)-1) ) ) {
//                        new_vol = Op.volume + eg_inc[Op.eg_sel_dr + ((this.eg_cnt>>Op.eg_sh_dr)&7)];
//
//                        if ( new_vol >= Op.sl )
//                            Op.state = EG_SUS;
//                    }
//                    break;
//                case EG_SUS: // sustain phase
//                    if ( !Op.eg_type) percussive mode {
//                        new_vol = Op.volume + eg_inc[Op.eg_sel_rr + ((this.eg_cnt>>Op.eg_sh_rr)&7)];
//
//                        if ( !(this.eg_cnt & ((1<<Op.eg_sh_rr)-1) ) ) {
//                            if ( new_vol >= MAX_ATT_INDEX )
//                                Op.volume = MAX_ATT_INDEX;
//                        }
//                    }
//                    break;
//                case EG_REL: // release phase
//                    if ( !(this.eg_cnt & ((1<<Op.eg_sh_rr)-1) ) ) {
//                        new_vol = Op.volume + eg_inc[Op.eg_sel_rr + ((this.eg_cnt>>Op.eg_sh_rr)&7)];
//                        if ( new_vol >= MAX_ATT_INDEX ) {
//                            Op.volume = MAX_ATT_INDEX;
//                            Op.state = EG_OFF;
//                        }
//                    }
//                    break;
//                default:
//                    break;
                }
            }
        }

        /**
         * advance to next sample
         */
        private void advance() {
            Channel ch;
            Slot op;

            this.egTimer += this.egTimerAdd;

            while (this.egTimer >= this.egTimerOverflow) {
                this.egTimer -= this.egTimerOverflow;

                this.egCnt++;

                for (int i = 0; i < 9 * 2; i++) {
                    ch = this.channels[i / 2];
                    op = ch.slots[i & 1];

                    // Envelope Generator
                    switch (op.state) {
                    case EG_ATT: // attack phase
                        if ((this.egCnt & ((1 << op.egShAr) - 1)) == 0) {
                            op.volume += (~op.volume *
                                    (egInc[op.egSelAr + ((this.egCnt >> op.egShAr) & 7)])
                            ) >> 3;

                            if (op.volume <= MIN_ATT_INDEX) {
                                op.volume = MIN_ATT_INDEX;
                                op.state = EG_DEC;
                            }

                        }
                        break;

                    case EG_DEC: // decay phase
                        if ((this.egCnt & ((1 << op.egShDr) - 1)) == 0) {
                            op.volume += egInc[op.egSelDr + ((this.egCnt >> op.egShDr) & 7)];

                            if (op.volume >= op.sl)
                                op.state = EG_SUS;

                        }
                        break;

                    case EG_SUS: // sustain phase

                        // this is important behaviour:
                        // one can change percusive/non-percussive modes on the fly and
                        // the chip will remain in sustain phase - verified on real YM3812

                        if (op.egType != 0) { // non-percussive mode
                            // do nothing
                        } else { // percussive mode
                            // during sustain phase chip adds Release Rate (in percussive mode)
                            if ((this.egCnt & ((1 << op.egShRr) - 1)) == 0) {
                                op.volume += egInc[op.egSelRr + ((this.egCnt >> op.egShRr) & 7)];

                                if (op.volume >= MAX_ATT_INDEX)
                                    op.volume = MAX_ATT_INDEX;
                            }
                            // else do nothing in sustain phase
                        }
                        break;

                    case EG_REL: // release phase
                        if ((this.egCnt & ((1 << op.egShRr) - 1)) == 0) {
                            op.volume += egInc[op.egSelRr + ((this.egCnt >> op.egShRr) & 7)];

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

            for (int i = 0; i < 9 * 2; i++) {
                ch = this.channels[i / 2];
                op = ch.slots[i & 1];

                // Phase Generator
                if (op.vib != 0) {
                    byte block;
                    int blockFNum = ch.blockFNum;

                    int fnumLfo = (blockFNum & 0x0380) >> 7;

                    int lfoFnTableIndexOffset = lfoPmTable[this.lfoPm + 16 * fnumLfo];

                    if (lfoFnTableIndexOffset != 0) { // LFO phase modulation active
                        blockFNum += lfoFnTableIndexOffset;
                        block = (byte) ((blockFNum & 0x1c00) >> 10);
                        op.cnt += (this.fnTab[blockFNum & 0x03ff] >> (7 - block)) * op.mul;
                    } else { // LFO phase modulation  = zero
                        op.cnt += op.incr;
                    }
                } else { // LFO phase modulation disabled for this Operator
                    op.cnt += op.incr;
                }
            }

            // The Noise Generator of the YM3812 is 23-bit shift register.
            //  Period is equal to 2^23-2 samples.
            //  Register works at sampling frequency of the chip, so output
            //  can change on every sample.
            //
            //  Output of the register and input to the bit 22 is:
            //  bit0 XOR bit14 XOR bit15 XOR bit22
            //
            //  Simply use bit 22 as the noise output.

            this.noiseP += this.noiseF;
            int i = this.noiseP >> FREQ_SH; // number of events (shifts of the shift register)
            this.noiseP &= FREQ_MASK;
            while (i != 0) {
                //            int j = ( (this.noise_rng) ^ (this.noise_rng>>14) ^ (this.noise_rng>>15) ^ (this.noise_rng>>22) ) & 1;
                //            this.noise_rng = (j<<22) | (this.noise_rng>>1);

                // Instead of doing all the logic operations above, we
                // use a trick here (and use bit 0 as the noise output).
                // The difference is only that the noise bit changes one
                // step ahead. This doesn't matter since we don't know
                // what is real state of the noise_rng after the reset.

                if ((this.noiseRng & 1) != 0) this.noiseRng ^= 0x800302;
                this.noiseRng >>= 1;

                i--;
            }
        }

        private int opCalc(int phase, int env, int pm, int wave_tab) {
            int p = (env << 4) + sinTab[wave_tab + ((((phase & ~FREQ_MASK) + (pm << 16)) >> FREQ_SH) & SIN_MASK)];

            if (p >= TL_TAB_LEN)
                return 0;
            return tlTab[p];
        }

        private int opCalc1(int phase, int env, int pm, int wave_tab) {
            int p = (env << 4) + sinTab[wave_tab + ((((phase & ~FREQ_MASK) + pm) >> FREQ_SH) & SIN_MASK)];

            if (p >= TL_TAB_LEN)
                return 0;
            return tlTab[p];
        }

        /**
         * calculate output
         */
        private void calcCh(Channel ch) {

            if (ch.muted != 0)
                return;

            this.phaseModulation = 0;

            // slot 1
            Slot slot = ch.slots[SLOT1];
            int env = slot.volume_calc(this.lfoAm);
            int out = slot.op1Out[0] + slot.op1Out[1];
            slot.op1Out[0] = slot.op1Out[1];
            //slot.connect1 += slot.op1_out[0];
            if (slot.ptrConnect1 == 0) this.output[0] += slot.op1Out[0];
            else this.phaseModulation += slot.op1Out[0];
            slot.op1Out[1] = 0;
            if (env < ENV_QUIET) {
                if (slot.fb == 0)
                    out = 0;
                slot.op1Out[1] = opCalc1(slot.cnt, env, (out << slot.fb), slot.waveTable);
            }

            // slot 2
            slot = ch.slots[SLOT2];
            env = slot.volume_calc(this.lfoAm);
            if (env < ENV_QUIET)
                this.output[0] += opCalc(slot.cnt, env, this.phaseModulation, slot.waveTable);
        }

        /**
         * calculate rhythm
         * <p>
         * operators used in the rhythm sounds generation process:
         * <p>
         * Envelope Generator:
         * <pre>
         * channel  Operator  register number   Bass  High  Snare Tom  Top
         * / slot   number    TL ARDR SLRR Wave Drum  Hat   Drum  Tom  Cymbal
         *  6 / 0   12        50  70   90   f0  +
         *  6 / 1   15        53  73   93   f3  +
         *  7 / 0   13        51  71   91   f1        +
         *  7 / 1   16        54  74   94   f4              +
         *  8 / 0   14        52  72   92   f2                    +
         *  8 / 1   17        55  75   95   f5                          +
         * </pre>
         * Phase Generator:
         * <pre>
         * channel  Operator  register number   Bass  High  Snare Tom  Top
         * / slot   number    MULTIPLE          Drum  Hat   Drum  Tom  Cymbal
         *  6 / 0   12        30                +
         *  6 / 1   15        33                +
         *  7 / 0   13        31                      +     +           +
         *  7 / 1   16        34                -----  n o t  u s e d -----
         *  8 / 0   14        32                                  +
         *  8 / 1   17        35                      +                 +
         * </pre>
         * <pre>
         * channel  Operator  register number   Bass  High  Snare Tom  Top
         * number   number    BLK/FNUM2 FNUM    Drum  Hat   Drum  Tom  Cymbal
         *    6     12,15     B6        A6      +
         *
         *    7     13,16     B7        A7            +     +           +
         *
         *    8     14,17     B8        A8            +           +     +
         * </pre>
         */
        private void calcRh(Channel[] ch, int noise) {

            // Bass Drum (verified on real YM3812):
            //  - depends on the channel 6 'connect' register:
            //    when connect = 0 it works the same as in normal (non-rhythm) mode (op1.op2.out)
            //    when connect = 1 _only_ Operator 2 is present on output (op2.out), Operator 1 is ignored
            //  - output sample always is multiplied by 2

            this.phaseModulation = 0;
            // slot 1
            Slot slot = ch[6].slots[SLOT1];
            int env = slot.volume_calc(this.lfoAm);

            int out = slot.op1Out[0] + slot.op1Out[1];
            slot.op1Out[0] = slot.op1Out[1];

            if (slot.con == 0)
                this.phaseModulation = slot.op1Out[0];
            // else ignore output of Operator 1

            slot.op1Out[1] = 0;
            if (env < ENV_QUIET) {
                if (slot.fb == 0)
                    out = 0;
                slot.op1Out[1] = opCalc1(slot.cnt, env, (out << slot.fb), slot.waveTable);
            }

            // slot 2
            slot = ch[6].slots[SLOT2];
            env = slot.volume_calc(this.lfoAm);
            if (env < ENV_QUIET && this.muteSpc[0] == 0)
                this.output[0] += opCalc(slot.cnt, env, this.phaseModulation, slot.waveTable) * 2;

            // Phase generation is based on:
            // HH  (13) channel 7.slot 1 combined with channel 8.slot 2 (same combination as TOP CYMBAL but different output phases)
            // SD  (16) channel 7.slot 1
            // TOM (14) channel 8.slot 1
            // TOP (17) channel 7.slot 1 combined with channel 8.slot 2 (same combination as HIGH HAT but different output phases)

            // Envelope generation based on:
            // HH  channel 7.slot1
            // SD  channel 7.slot2
            // TOM channel 8.slot1
            // TOP channel 8.slot2

            // The following formulas can be well optimized.
            // I leave them in direct form for now (in case I've missed something).

            // High Hat (verified on real YM3812)
            env = slot7_1().volume_calc(this.lfoAm);
            if (env < ENV_QUIET && this.muteSpc[4] == 0) {

                // high hat phase generation:
                //  phase = d0 or 234 (based on frequency only)
                //  phase = 34 or 2d0 (based on noise)

                // base frequency derived from Operator 1 in channel 7
                byte bit7 = (byte) (((slot7_1().cnt >> FREQ_SH) >> 7) & 1);
                byte bit3 = (byte) (((slot7_1().cnt >> FREQ_SH) >> 3) & 1);
                byte bit2 = (byte) (((slot7_1().cnt >> FREQ_SH) >> 2) & 1);

                byte res1 = (byte) ((bit2 ^ bit7) | bit3);

                // when res1 = 0 phase = 0x000 | 0xd0;
                // when res1 = 1 phase = 0x200 | (0xd0>>2);
                int phase = res1 != 0 ? (0x200 | (0xd0 >> 2)) : 0xd0;

                // enable gate based on frequency of Operator 2 in channel 8
                byte bit5e = (byte) (((slot8_2().cnt >> FREQ_SH) >> 5) & 1);
                byte bit3e = (byte) (((slot8_2().cnt >> FREQ_SH) >> 3) & 1);

                byte res2 = (byte) (bit3e ^ bit5e);

                // when res2 = 0 pass the phase from calculation above (res1);
                // when res2 = 1 phase = 0x200 | (0xd0>>2);
                if (res2 != 0)
                    phase = (0x200 | (0xd0 >> 2));


                // when phase & 0x200 is set and noise=1 then phase = 0x200|0xd0
                // when phase & 0x200 is set and noise=0 then phase = 0x200|(0xd0>>2), ie no change
                if ((phase & 0x200) != 0) {
                    if (noise != 0)
                        phase = 0x200 | 0xd0;
                } else {
                    // when phase & 0x200 is clear and noise=1 then phase = 0xd0>>2
                    // when phase & 0x200 is clear and noise=0 then phase = 0xd0, ie no change
                    if (noise != 0)
                        phase = 0xd0 >> 2;
                }

                this.output[0] += opCalc(phase << FREQ_SH, env, 0, slot7_1().waveTable) * 2;
            }

            // Snare Drum (verified on real YM3812)
            env = slot7_2().volume_calc(this.lfoAm);
            if (env < ENV_QUIET && this.muteSpc[1] == 0) {
                // base frequency derived from Operator 1 in channel 7
                byte bit8 = (byte) (((slot7_1().cnt >> FREQ_SH) >> 8) & 1);

                // when bit8 = 0 phase = 0x100;
                // when bit8 = 1 phase = 0x200;
                int phase = bit8 != 0 ? 0x200 : 0x100;

                // Noise bit XOR'es phase by 0x100
                // when noisebit = 0 pass the phase from calculation above
                // when noisebit = 1 phase ^= 0x100;
                // in other words: phase ^= (noisebit<<8);
                if (noise != 0)
                    phase ^= 0x100;

                this.output[0] += opCalc(phase << FREQ_SH, env, 0, slot7_2().waveTable) * 2;
            }

            // Tom Tom (verified on real YM3812)
            env = slot8_1().volume_calc(this.lfoAm);
            if (env < ENV_QUIET && this.muteSpc[2] == 0)
                this.output[0] += opCalc(slot8_1().cnt, env, 0, slot8_1().waveTable) * 2;

            // Top Cymbal (verified on real YM3812)
            env = slot8_2().volume_calc(this.lfoAm);
            if (env < ENV_QUIET && this.muteSpc[3] == 0) {
                // base frequency derived from Operator 1 in channel 7
                byte bit7 = (byte) (((slot7_1().cnt >> FREQ_SH) >> 7) & 1);
                byte bit3 = (byte) (((slot7_1().cnt >> FREQ_SH) >> 3) & 1);
                byte bit2 = (byte) (((slot7_1().cnt >> FREQ_SH) >> 2) & 1);

                byte res1 = (byte) ((bit2 ^ bit7) | bit3);

                // when res1 = 0 phase = 0x000 | 0x100;
                // when res1 = 1 phase = 0x200 | 0x100;
                int phase = res1 != 0 ? 0x300 : 0x100;

                // enable gate based on frequency of Operator 2 in channel 8
                byte bit5e = (byte) (((slot8_2().cnt >> FREQ_SH) >> 5) & 1);
                byte bit3e = (byte) (((slot8_2().cnt >> FREQ_SH) >> 3) & 1);

                byte res2 = (byte) (bit3e ^ bit5e);
                // when res2 = 0 pass the phase from calculation above (res1);
                // when res2 = 1 phase = 0x200 | 0x100;
                if (res2 != 0)
                    phase = 0x300;

                this.output[0] += opCalc(phase << FREQ_SH, env, 0, slot8_2().waveTable) * 2;
            }
        }

        /**
         * generic table initialize
         */
        private int initTables() {

            for (int x = 0; x < TL_RES_LEN; x++) {
                double m = (1 << 16) / Math.pow(2, (x + 1) * (ENV_STEP / 4.0) / 8.0);
                m = Math.floor(m);

                // we never reach (1<<16) here due to the (x+1)
                // result fits within 16 bits at maximum

                int n = (int) m; // 16 bits here
                n >>= 4; // 12 bits here
                if ((n & 1) != 0) // round to nearest
                    n = (n >> 1) + 1;
                else
                    n = n >> 1;
                // 11 bits here (rounded)
                n <<= 1; // 12 bits here (as in real chip)
                tlTab[x * 2 + 0] = n;
                tlTab[x * 2 + 1] = -tlTab[x * 2 + 0];

                for (int i = 1; i < 12; i++) {
                    tlTab[x * 2 + 0 + i * 2 * TL_RES_LEN] = tlTab[x * 2 + 0] >> i;
                    tlTab[x * 2 + 1 + i * 2 * TL_RES_LEN] = -tlTab[x * 2 + 0 + i * 2 * TL_RES_LEN];
                }
                //#if 0
                //   Debug.printf("tl %04i", x*2);
                //   for (i=0; i<12; i++)
                //    Debug.printf(", [%02i] %5i", i*2, tl_tab[ x*2 /*+1 + i*2*TL_RES_LEN ] );
                //   Debug.printf("\n");
                //#endif
            }
            // Debug.printf("FMthis.C: TL_TAB_LEN = %i elements (%i bytes)\n",TL_TAB_LEN, (int)sizeof(tl_tab));


            for (int i = 0; i < SIN_LEN; i++) {
                // non-standard sinus
                double m = Math.sin(((i * 2) + 1) * Math.PI / SIN_LEN); // checked against the real chip

                // we never reach zero here due to ((i*2)+1)

                double o;
                if (m > 0.0)
                    o = 8 * Math.log(1.0 / m) / Math.log(2.0); // convert to 'decibels'
                else
                    o = 8 * Math.log(-1.0 / m) / Math.log(2.0); // convert to 'decibels'

                o = o / (ENV_STEP / 4);

                int n = (int) (2.0 * o);
                if ((n & 1) != 0) // round to nearest
                    n = (n >> 1) + 1;
                else
                    n = n >> 1;

                sinTab[i] = n * 2 + (m >= 0.0 ? 0 : 1);

                // Debug.printf("FMthis.C: sin [%4i (hex=%03x)]= %4i (tl_tab value=%5i)\n", i, i, sin_tab[i], tl_tab[sin_tab[i]] );
            }

            for (int i = 0; i < SIN_LEN; i++) {
                // waveform 1:  __      __
                //             /  \____/  \____
                // output only first half of the sinus waveform (positive one)

                if ((i & (1 << (SIN_BITS - 1))) != 0)
                    sinTab[1 * SIN_LEN + i] = TL_TAB_LEN;
                else
                    sinTab[1 * SIN_LEN + i] = sinTab[i];

                // waveform 2:  __  __  __  __
                //             /  \/  \/  \/  \
                // abs(sin)

                sinTab[2 * SIN_LEN + i] = sinTab[i & (SIN_MASK >> 1)];

                // waveform 3:  _   _   _   _
                //             / |_/ |_/ |_/ |_
                // abs(output only first quarter of the sinus waveform)

                if ((i & (1 << (SIN_BITS - 2))) != 0)
                    sinTab[3 * SIN_LEN + i] = TL_TAB_LEN;
                else
                    sinTab[3 * SIN_LEN + i] = sinTab[i & (SIN_MASK >> 2)];

                //Debug.printf("FMthis.C: sin1[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[1*SIN_LEN+i], tl_tab[sin_tab[1*SIN_LEN+i]] );
                //Debug.printf("FMthis.C: sin2[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[2*SIN_LEN+i], tl_tab[sin_tab[2*SIN_LEN+i]] );
                //Debug.printf("FMthis.C: sin3[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[3*SIN_LEN+i], tl_tab[sin_tab[3*SIN_LEN+i]] );
            }
            //Debug.printf("FMthis.C: ENV_QUIET= %08x (dec*8=%i)\n", ENV_QUIET, ENV_QUIET*8);

            return 1;
        }

        private void initialize() {

            // frequency base
            this.freqBase = (this.rate != 0) ? ((double) this.clock / 72.0) / this.rate : 0;

            //Debug.printf("freqbase=%f\n", this.freqbase);

            // Timer base time
            //this.TimerBase = attotime_mul(ATTOTIME_IN_HZ(this.clock), 72);

            // make fnumber . increment counter table
            for (int i = 0; i < 1024; i++) {
                // opn phase increment counter = 20bit
                this.fnTab[i] = (int) ((double) i * 64 * this.freqBase * (1 << (FREQ_SH - 10))); // -10 because chip works with 10.10 fixed point, while we use 16.16
            }

            for (int i = 0; i < 9; i++)
                this.channels[i].muted = 0x00;
            for (int i = 0; i < 6; i++)
                this.muteSpc[i] = 0x00;


            // Amplitude modulation: 27 output levels (triangle waveform); 1 level takes one of: 192, 256 or 448 samples
            // One entry from LFO_AM_TABLE lasts for 64 samples
            this.lfoAmInc = (int) ((1.0 / 64.0) * (1 << LFO_SH) * this.freqBase);

            // Vibrato: 8 output levels (triangle waveform); 1 level takes 1024 samples
            this.lfoPmInc = (int) ((1.0 / 1024.0) * (1 << LFO_SH) * this.freqBase);

            //System.err.printf ("this.lfo_am_inc = %8x ; this.lfo_pm_inc = %8x\n", this.lfo_am_inc, this.lfo_pm_inc);

            // Noise generator: a step takes 1 sample
            this.noiseF = (int) ((1.0 / 1.0) * (1 << FREQ_SH) * this.freqBase);

            this.egTimerAdd = (int) ((1 << EG_SH) * this.freqBase);
            this.egTimerOverflow = (1) * (1 << EG_SH);
            //Debug.printf("OPLinit eg_timer_add=%8x eg_timer_overflow=%8x\n", this.eg_timer_add, this.eg_timer_overflow);
        }

        /**
         * update phase increment counter of Operator (also update the EG rates if necessary)
         */
        private void calcFcSlot(Channel ch, Slot slot) {
            // (frequency) phase increment counter
            slot.incr = ch.fc * slot.mul;
            int ksr = ch.kCode >> slot.KSR;

            if (slot.ksr != ksr) {
                slot.ksr = (byte) ksr;

                // calculate envelope generator rates
                if ((slot.ar + slot.ksr) < 16 + 62) {
                    slot.egShAr = egRateShift[slot.ar + slot.ksr];
                    slot.egSelAr = egRateSelect[slot.ar + slot.ksr];
                } else {
                    slot.egShAr = 0;
                    slot.egSelAr = 13 * RATE_STEPS;
                }
                slot.egShDr = egRateShift[slot.dr + slot.ksr];
                slot.egSelDr = egRateSelect[slot.dr + slot.ksr];
                slot.egShRr = egRateShift[slot.rr + slot.ksr];
                slot.egSelRr = egRateSelect[slot.rr + slot.ksr];
            }
        }

        /**
         * set multi,am,vib,EG-TYP,KSR,mul
         */
        private void setMul(int sl, int v) {
            Channel ch = this.channels[sl / 2];
            Slot slot = ch.slots[sl & 1];
            slot.setMul(v);
            calcFcSlot(ch, slot);
        }

        /**
         * set ksl & tl
         */
        private void setKslTl(int sl, int v) {
            Channel ch = this.channels[sl / 2];
            Slot slot = ch.slots[sl & 1];

            slot.ksl = (byte) ksl_shift[v >> 6];
            slot.tl = (v & 0x3f) << (ENV_BITS - 1 - 7); // 7 bits TL (bit 6 = always 0)

            slot.tll = slot.tl + (ch.kslBase >> slot.ksl);
        }

        /**
         * set attack rate & decay rate
         */
        private void setArDr(int sl, int v) {
            Channel ch = this.channels[sl / 2];
            Slot slot = ch.slots[sl & 1];

            slot.ar = (v >> 4) != 0 ? 16 + ((v >> 4) << 2) : 0;

            if ((slot.ar + slot.ksr) < 16 + 62) {
                slot.egShAr = egRateShift[slot.ar + slot.ksr];
                slot.egSelAr = egRateSelect[slot.ar + slot.ksr];
            } else {
                slot.egShAr = 0;
                slot.egSelAr = 13 * RATE_STEPS;
            }

            slot.dr = (v & 0x0f) != 0 ? 16 + ((v & 0x0f) << 2) : 0;
            slot.egShDr = egRateShift[slot.dr + slot.ksr];
            slot.egSelDr = egRateSelect[slot.dr + slot.ksr];
        }

        /**
         * set sustain level & release rate
         */
        private void setSlRr(int sl, int v) {
            Channel ch = this.channels[sl / 2];
            Slot slot = ch.slots[sl & 1];

            slot.sl = slTab[v >> 4];

            slot.rr = (v & 0x0f) != 0 ? 16 + ((v & 0x0f) << 2) : 0;
            slot.egShRr = egRateShift[slot.rr + slot.ksr];
            slot.egSelRr = egRateSelect[slot.rr + slot.ksr];
        }

        /**
         * write a value v to register r on opl chip
         */
        private void writeReg(int r, int v) {
            Channel ch;
            int slot;
            int blockFNum;

            // adjust bus to 8 bits
            r &= 0xff;
            v &= 0xff;

            switch (r & 0xe0) {
            case 0x00: // 00-1f:control
                switch (r & 0x1f) {
                case 0x01: // waveform select enable
                    if ((this.type & SUB_TYPE_WAVESEL) != 0) {
                        this.waveSel = (byte) (v & 0x20);
                        // do not change the waveform previously selected
                    }
                    break;
                case 0x02: // Timer 1
                    this.t[0] = (256 - v) * 4;
                    break;
                case 0x03: // Timer 2
                    this.t[1] = (256 - v) * 16;
                    break;
                case 0x04: // IRQ clear / mask and Timer enable
                    if ((v & 0x80) != 0) { // IRQ flag clear
                        resetStatus(0x7f - 0x08); // don't reset BFRDY flag or we will have to call DeltaT module to set the flag
                    } else { // set IRQ mask ,timer enable
                        byte st1 = (byte) (v & 1);
                        byte st2 = (byte) ((v >> 1) & 1);

                        // IRQRST, T1MSK, t2MSK, EOSMSK, BRMSK, x, ST2, ST1
                        resetStatus(v & (0x78 - 0x08));
                        setStatusMask((~v) & 0x78);

                        // timer 2
                        if (this.st[1] != st2) {
                            //attotime period = st2 ? attotime_mul(this.TimerBase, this.T[1]) : attotime_zero;
                            this.st[1] = st2;
                            //if (this.timer_handler) (this.timer_handler)(this.TimerParam,1,period);
                        }
                        // timer 1
                        if (this.st[0] != st1) {
                            //attotime period = st1 ? attotime_mul(this.TimerBase, this.T[0]) : attotime_zero;
                            this.st[0] = st1;
                            //if (this.timer_handler) (this.timer_handler)(this.TimerParam,0,period);
                        }
                    }
                    break;
                case 0x06: // Key Board OUT
                    if ((this.type & SUB_TYPE_KEYBOARD) != 0) {
                        if (this.keyboardWriteHandler != null)
                            this.keyboardWriteHandler.accept((byte) v);
                        //# ifdef _DEBUG
                        //else
                        //Debug.printf("Y8950: write unmapped KEYBOARD port\n");
                        //#endif
                    }
                    break;
                case 0x07: // DELTA-T control 1 : START,REC,MEMDATA,REPT,SPOFF,x,x,RST
                    if ((this.type & SUB_TYPE_ADPCM) != 0)
                        this.deltaT.write(r - 0x07, v);
                    break;
                case 0x08: // MODE,DELTA-T control 2 : CSM,NOTESEL,x,x,smpl,da/ad,64k,rom
                    this.mode = (byte) v;
                    if ((this.type & SUB_TYPE_ADPCM) != 0)
                        this.deltaT.write(r - 0x07, v & 0x0f); // mask 4 LSBs in register 08 for DELTA-T unit
                    //#endif
                    break;

                //#if BUILD_Y8950
                case 0x09: // START ADD
                case 0x0a:
                case 0x0b: // STOP ADD
                case 0x0c:
                case 0x0d: // PRESCALE
                case 0x0e:
                case 0x0f: // ADPCM data write
                case 0x10: // DELTA-N
                case 0x11: // DELTA-N
                case 0x12: // ADPCM volume
                    if ((this.type & SUB_TYPE_ADPCM) != 0)
                        this.deltaT.write(r - 0x07, v);
                    break;

                case 0x15: // DAC data high 8 bits (F7,F6...F2)
                case 0x16: // DAC data low 2 bits (F1, F0 in bits 7,6)
                case 0x17: // DAC data shift (S2,S1,S0 in bits 2,1,0)
//Debug.printf("FMthis.C: DAC data register written, but not implemented reg=%02x val=%02x\n", r, v);
                    break;

                case 0x18: // I/O CTRL (Direction)
                    if ((this.type & SUB_OPL_TYPE_IO) != 0)
                        this.portDirection = (byte) (v & 0x0f);
                    break;
                case 0x19: // I/O DATA
                    if ((this.type & SUB_OPL_TYPE_IO) != 0) {
                        this.portLatch = (byte) v;
                        if (this.portWriteHandler != null)
                            this.portWriteHandler.accept((byte) (v & this.portDirection));
                    }
                    break;
                default:
//Debug.printf("FMthis.C: write to unknown register: %02x\n", r);
                    break;
                }
                break;
            case 0x20: // am ON, vib ON, ksr, eg_type, mul
                slot = slot_array[r & 0x1f];
                if (slot < 0) return;
                setMul(slot, v);
                break;
            case 0x40:
                slot = slot_array[r & 0x1f];
                if (slot < 0) return;
                setKslTl(slot, v);
                break;
            case 0x60:
                slot = slot_array[r & 0x1f];
                if (slot < 0) return;
                setArDr(slot, v);
                break;
            case 0x80:
                slot = slot_array[r & 0x1f];
                if (slot < 0) return;
                setSlRr(slot, v);
                break;
            case 0xa0:
                if (r == 0xbd) { // am depth, vibrato depth, r,bd,sd,tom,tc,hh
                    this.lfoAmDepth = (byte) (v & 0x80);
                    this.lfoPmDepthRange = (byte) ((v & 0x40) != 0 ? 8 : 0);

                    this.rhythm = (byte) (v & 0x3f);

                    if ((this.rhythm & 0x20) != 0) {
                        // BD key on/off
                        if ((v & 0x10) != 0) {
                            this.channels[6].slots[SLOT1].keyOn(2);
                            this.channels[6].slots[SLOT2].keyOn(2);
                        } else {
                            this.channels[6].slots[SLOT1].keyOff(~(int) 2);
                            this.channels[6].slots[SLOT2].keyOff(~(int) 2);
                        }
                        // HH key on/off
                        if ((v & 0x01) != 0) this.channels[7].slots[SLOT1].keyOn(2);
                        else this.channels[7].slots[SLOT1].keyOff(~(int) 2);
                        // SD key on/off
                        if ((v & 0x08) != 0) this.channels[7].slots[SLOT2].keyOn(2);
                        else this.channels[7].slots[SLOT2].keyOff(~(int) 2);
                        // TOM key on/off
                        if ((v & 0x04) != 0) this.channels[8].slots[SLOT1].keyOn(2);
                        else this.channels[8].slots[SLOT1].keyOff(~(int) 2);
                        // TOP-CY key on/off
                        if ((v & 0x02) != 0) this.channels[8].slots[SLOT2].keyOn(2);
                        else this.channels[8].slots[SLOT2].keyOff(~(int) 2);
                    } else {
                        // BD key off
                        this.channels[6].slots[SLOT1].keyOff(~(int) 2);
                        this.channels[6].slots[SLOT2].keyOff(~(int) 2);
                        // HH key off
                        this.channels[7].slots[SLOT1].keyOff(~(int) 2);
                        // SD key off
                        this.channels[7].slots[SLOT2].keyOff(~(int) 2);
                        // TOM key off
                        this.channels[8].slots[SLOT1].keyOff(~(int) 2);
                        // TOP-CY off
                        this.channels[8].slots[SLOT2].keyOff(~(int) 2);
                    }
                    return;
                }
                // keyon,block,fnum
                if ((r & 0x0f) > 8) return;
                ch = this.channels[r & 0x0f];
                if ((r & 0x10) == 0) { // a0-a8
                    blockFNum = (ch.blockFNum & 0x1f00) | v;
                } else { // b0-b8
                    blockFNum = ((v & 0x1f) << 8) | (ch.blockFNum & 0xff);

                    if ((v & 0x20) != 0) {
                        ch.slots[SLOT1].keyOn(1);
                        ch.slots[SLOT2].keyOn(1);
                    } else {
                        ch.slots[SLOT1].keyOff(~(int) 1);
                        ch.slots[SLOT2].keyOff(~(int) 1);
                    }
                }
                // update
                if (ch.blockFNum != blockFNum) {
                    byte block = (byte) (blockFNum >> 10);

                    ch.blockFNum = blockFNum;

                    ch.kslBase = kslTab[blockFNum >> 6];
                    ch.fc = this.fnTab[blockFNum & 0x03ff] >> (7 - block);

                    // BLK 2,1,0 bits . bits 3,2,1 of kcode
                    ch.kCode = (byte) ((ch.blockFNum & 0x1c00) >> 9);

                    // the info below is actually opposite to what is stated in the Manuals (verifed on real YM3812)
                    // if notesel == 0 . lsb of kcode is bit 10 (MSB) of fnum
                    // if notesel == 1 . lsb of kcode is bit 9 (MSB-1) of fnum
                    if ((this.mode & 0x40) != 0)
                        ch.kCode |= (byte) ((ch.blockFNum & 0x100) >> 8); // notesel == 1
                    else
                        ch.kCode |= (byte) ((ch.blockFNum & 0x200) >> 9); // notesel == 0

                    // refresh Total Level in both SLOTs of this channel
                    ch.slots[SLOT1].tll = ch.slots[SLOT1].tl + (ch.kslBase >> ch.slots[SLOT1].ksl);
                    ch.slots[SLOT2].tll = ch.slots[SLOT2].tl + (ch.kslBase >> ch.slots[SLOT2].ksl);

                    // refresh frequency counter in both SLOTs of this channel
                    calcFcSlot(ch, ch.slots[SLOT1]);
                    calcFcSlot(ch, ch.slots[SLOT2]);
                }
                break;
            case 0xc0:
                // FB,C
                if ((r & 0x0f) > 8) return;
                ch = this.channels[r & 0x0f];
                ch.slots[SLOT1].fb = (byte) (((v >> 1) & 7) != 0 ? ((v >> 1) & 7) + 7 : 0);
                ch.slots[SLOT1].con = (byte) (v & 1);
                ch.slots[SLOT1].ptrConnect1 = ch.slots[SLOT1].con != 0 ? 0 : 1;
                break;
            case 0xe0: // waveform select
                // simply ignore write to the waveform select register if selecting not enabled in test register
                if (this.waveSel != 0) {
                    slot = slot_array[r & 0x1f];
                    if (slot < 0) return;
                    ch = this.channels[slot / 2];

                    ch.slots[slot & 1].waveTable = (v & 0x03) * SIN_LEN;
                }
                break;
            }
        }

        private void reset() {
            this.egTimer = 0;
            this.egCnt = 0;

            this.noiseRng = 1; // noise shift register
            this.mode = 0; // normal mode
            resetStatus(0x7f);

            // reset with register write
            writeReg(0x01, 0); // wavesel disable
            writeReg(0x02, 0); // Timer1
            writeReg(0x03, 0); // Timer2
            writeReg(0x04, 0); // IRQ mask clear
            for (int i = 0xff; i >= 0x20; i--) writeReg(i, 0);

            // reset Operator parameters
            for (int c = 0; c < 9; c++) {
                this.channels[c].reset();
            }

            if ((this.type & SUB_TYPE_ADPCM) != 0) {
                this.deltaT.reset(this.freqBase, this.outputDeltaT);
            }
        }

        /**
         * Create one of virtual YM3812/YM3526/Y8950
         *
         * @param clock is chip clock in Hz
         * @param rate  is sampling rate
         */
        private Opl(int clock, int rate, int type) {
            this.type = (byte) type;
            this.clock = clock;
            this.rate = rate;

            // init Global tables
            initialize();

            if (type == TYPE_Y8950) {
                this.deltaT.init();
            }

            // reset
            this.reset();
        }

        /**
         * Destroy one of virtual YM3812
         */
        private void destroy() {
        }

        // Optional handlers

        private void setTimerHandler(TimerHandler timerHandler) {
            this.timerHandler = timerHandler;
        }

        private void setIRQHandler(IrqHandler irqHandler) {
            this.irqHandler = irqHandler;
        }

        private void setUpdateHandler(UpdateHandler updateHandler) {
            this.updateHandler = updateHandler;
        }

        private int write(int a, int v) {
            if ((a & 1) == 0) { // address port
                this.address = (byte) (v & 0xff);
            } else { // data port
                if (this.updateHandler != null) this.updateHandler.accept(0);
                writeReg(this.address, v);
            }
            return this.status >> 7;
        }

        private byte read(int a) {
            if ((a & 1) == 0) {
                // status port

                if ((this.type & SUB_TYPE_ADPCM) != 0) { // Y8950
                    return (byte) ((this.status & (this.statusMask | 0x80)) | (this.deltaT.pcmBsy & 1));
                }

                // opl and OPL2
                return (byte) (this.status & (this.statusMask | 0x80));
            }

            // data port
            switch (this.address) {
            case 0x05: // KeyBoard IN
                if ((this.type & SUB_TYPE_KEYBOARD) != 0) {
                    if (this.keyboardReadHandler != null)
                        return this.keyboardReadHandler.get();
                    //Debug.printf("Y8950: read unmapped KEYBOARD port\n");
                }
                return 0;

            case 0x0f: // ADPCM-DATA
                if ((this.type & SUB_TYPE_ADPCM) != 0) {
                    byte val;

                    val = this.deltaT.read();
                    // Debug.printf("Y8950: read ADPCM value read=%02x\n",val);
                    return val;
                }
                return 0;

            case 0x19: // I/O DATA
                if ((this.type & SUB_OPL_TYPE_IO) != 0) {
                    if (this.portReadHandler != null)
                        return this.portReadHandler.get();
                    //Debug.printf("Y8950:read unmapped I/O port\n");
                }
                return 0;
            case 0x1a: // PCM-DATA
                if ((this.type & SUB_TYPE_ADPCM) != 0) {
                    //Debug.printf("Y8950 A/D conversion is accessed but not implemented !\n");
                    return (byte) 0x80; // 2's complement PCM data - result from A/D conversion
                }
                return 0;
            }

            return (byte) 0xff;
        }

        /**
         * CSM Key Controll
         */
        private void csmKeyControll(Channel ch) {
            ch.slots[SLOT1].keyOn(4);
            ch.slots[SLOT2].keyOn(4);

            // The key off should happen exactly one sample later - not implemented correctly yet

            ch.slots[SLOT1].keyOff(~(int) 4);
            ch.slots[SLOT2].keyOff(~(int) 4);
        }

        private int oplTimerOver(int c) {
            if (c != 0) { // Timer B
                setStatus(0x20);
            } else { // Timer A
                setStatus(0x40);
                // CSM mode key,TL controll
                if ((this.mode & 0x80) != 0) { // CSM mode total level latch and auto key on
                    int ch;
                    if (this.updateHandler != null) this.updateHandler.accept(0);
                    for (ch = 0; ch < 9; ch++)
                        csmKeyControll(this.channels[ch]);
                }
            }
            // reload timer
            //if (this.timer_handler) (this.timer_handler)(this.TimerParam,c,attotime_mul(this.TimerBase, this.T[c]));
            return this.status >> 7;
        }

        private void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < 9; curChn++)
                this.channels[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
            for (byte curChn = 0; curChn < 6; curChn++)
                this.muteSpc[curChn] = (byte) ((muteMask >> (9 + curChn)) & 0x01);
        }

        public void updateOne(int[][] buffer, int length) {
            switch (type) {
                case TYPE_YM3812 -> {
                    byte rhythm = (byte) (this.rhythm & 0x20);
                    int[] bufL = buffer[0];
                    int[] bufR = buffer[1];

                    if (length == 0) {
                        this.refreshEg();
                        return;
                    }

                    for (int i = 0; i < length; i++) {

                        this.output[0] = 0;

                        this.advanceLfo();

                        // FM part
                        this.calcCh(this.channels[0]);
                        this.calcCh(this.channels[1]);
                        this.calcCh(this.channels[2]);
                        this.calcCh(this.channels[3]);
                        this.calcCh(this.channels[4]);
                        this.calcCh(this.channels[5]);

                        if (rhythm == 0) {
                            this.calcCh(this.channels[6]);
                            this.calcCh(this.channels[7]);
                            this.calcCh(this.channels[8]);
                        } else { // Rhythm part
                            this.calcRh(this.channels, (this.noiseRng >> 0) & 1);
                        }

                        int lt = this.output[0];

                        lt >>= FINAL_SH;

                        // limit check
                        //lt = limit( lt , MAXOUT, MINOUT );

                        // store to Sound buffer
                        bufL[i] = lt;
                        bufR[i] = lt;

                        this.advance();
                    }
                }
                case TYPE_Y8950 -> {
                    byte rhythm = (byte) (this.rhythm & 0x20);
                    DeltaT deltaT = this.deltaT;
                    int[] bufL = buffer[0];
                    int[] bufR = buffer[1];
    
                    for (int i = 0; i < length; i++) {
                        //Debug.printf("clock=%d:rate=%d:freqbase=%d:cnt=%d", chip.clock, chip.rate, chip.freqbase, cnt++);

                        this.output[0] = 0;
                        this.outputDeltaT[0] = 0;
    
                        this.advanceLfo();
    
                        // deltaT ADPCM
                        if ((deltaT.portState & 0x80) != 0 && this.muteSpc[5] == 0)
                            deltaT.calcAdpcm();
    
                        // FM part
                        this.calcCh(this.channels[0]);
                        //Debug.printf("P_CH[0] this.output[0]=%d", this.output[0]);
                        this.calcCh(this.channels[1]);
                        //Debug.printf("P_CH[1] this.output[0]=%d", this.output[0]);
                        this.calcCh(this.channels[2]);
                        //Debug.printf("P_CH[2] this.output[0]=%d", this.output[0]);
                        this.calcCh(this.channels[3]);
                        //Debug.printf("P_CH[3] this.output[0]=%d", this.output[0]);
                        this.calcCh(this.channels[4]);
                        //Debug.printf("P_CH[4] this.output[0]=%d %d %d", this.output[0], this.P_CH[4].SLOT[SLOT1].op1_out[0], this.P_CH[4].SLOT[SLOT1].op1_out[1]);
                        this.calcCh(this.channels[5]);
                        //Debug.printf("P_CH[5] this.output[0]=%d", this.output[0]);
    
                        if (rhythm == 0) {
                            this.calcCh(this.channels[6]);
                            //Debug.printf("P_CH[6] this.output[0]=%d", this.output[0]);
                            this.calcCh(this.channels[7]);
                            //Debug.printf("P_CH[7] this.output[0]=%d", this.output[0]);
                            this.calcCh(this.channels[8]);
                            //Debug.printf("P_CH[8] this.output[0]=%d", this.output[0]);
                        } else { // Rhythm part
                            this.calcRh(this.channels, (this.noiseRng >> 0) & 1);
                            //Debug.printf("P_CH[0R] this.output[0]=%d", this.output[0]);
                        }

                        int lt = this.output[0] + (this.outputDeltaT[0] >> 11);
                        //Debug.printf("this.output_deltat[0]=%d acc=%d now_step=%d adpcmd=%d", this.output_deltat[0] ,deltaT.acc ,deltaT.now_step , deltaT.adpcmd);
                        lt >>= FINAL_SH;
    
                        // limit check
                        //lt = limit( lt , MAXOUT, MINOUT );
    
                        // store to Sound buffer
                        bufL[i] = lt;
                        bufR[i] = lt;
    
                        this.advance();
                    }
                }
            }
        }

        public void writePcmRom(int romSize, int dataStart, int dataLength, byte[] romData) {
            if (this.deltaT.memorySize != romSize) {
                this.deltaT.memory = new byte[romSize];
                this.deltaT.memorySize = romSize;
                Arrays.fill(this.deltaT.memory, 0, romSize, (byte) 0xff);
                this.deltaT.calcMemMask();
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            System.arraycopy(romData, 0, this.deltaT.memory, dataStart, dataLength);
        }

        public void writePcmRom(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
            if (this.deltaT.memorySize != romSize) {
                this.deltaT.memory = new byte[romSize];
                this.deltaT.memorySize = romSize;
                Arrays.fill(this.deltaT.memory, 0, romSize, (byte) 0xff);
                this.deltaT.calcMemMask();
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            System.arraycopy(romData, srcStartAddress, this.deltaT.memory, dataStart, dataLength);
        }
    }
}
