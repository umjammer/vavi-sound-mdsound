package mdsound.mame;

import java.util.function.Consumer;

import static mdsound.mame.Fm.OUTD_CENTER;


/**
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
 *  - renamed/removed some YM_DELTAT struct fields
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
 * TODO:
 *      Check size of the address register on the other chips....
 *
 * Version 0.72
 *
 * Sound chips that have this unit:
 * YM2608   OPNA
 * YM2610/B OPNB
 * Y8950    MSX AUDIO
 */
public class YmDeltaT {

    private static final int SHIFT = 16;

    public static final int EMULATION_MODE_NORMAL = 0;
    public static final int EMULATION_MODE_YM2610 = 1;

    public void reset2610(double freqBase, int[] outDelta) {
        this.freqBase = freqBase;
        this.outputPointer = outDelta;
        this.portShift = 8; // allways 8bits shift
        this.outputRange = 1 << 23;
        this.resetAdpcm(OUTD_CENTER, YmDeltaT.EMULATION_MODE_YM2610);
    }

    public void reset2608(double freqBase, int[] outDelta) {
        this.freqBase = freqBase;
        this.outputPointer = outDelta;
        this.portShift = 5;      /* always 5bits shift */ /* ASG */
        this.outputRange = 1 << 23;
        this.resetAdpcm(OUTD_CENTER, YmDeltaT.EMULATION_MODE_NORMAL);
    }

    public interface StatusChangeHandler extends Consumer<Byte> {
    }

    /* AT: rearranged and tigntened structure */
    public byte[] memory;
    /* pointer of output pointers   */
    public int[] outputPointer;
    /* pan : &output_pointer[pan]   */
    public int[] pan;
    public int panPtr;
    public double freqBase;
    public int memorySize;
    public int memoryMask;
    public int outputRange;
    /* current address      */
    public int nowAddr;
    /* currect step         */
    public int nowStep;
    /* step                 */
    public int step;
    /* start address        */
    public int start;
    /* limit address        */
    public int limit;
    /* end address          */
    public int end;
    /* delta scale          */
    public int delta;
    /* current volume       */
    public int volume;
    /* shift Measurement value*/
    public int acc;
    /* next Forecast        */
    public int adpCmd;
    /* current value        */
    public int adpCml;
    /* leveling value       */
    public int prevAcc;
    /* current rom data     */
    public byte nowData;
    /* current data from reg 08 */
    public byte cpuData;
    /* port status          */
    public byte portState;
    /* control reg: SAMPLE, DA/AD, RAM TYPE (x8bit / x1bit), ROM/RAM */
    public byte control2;
    /* address bits shift-left:
     ** 8 for YM2610,
     ** 5 for Y8950 and YM2608 */
    public byte portShift;

    /* address bits shift-right:
     ** 0 for ROM and x8bit DRAMs,
     ** 3 for x1 DRAMs */
    public byte dromPortShift;

    /* needed for reading/writing external memory */
    public byte memRead;

    /* handlers and parameters for the status flags support */
    public StatusChangeHandler statusSetHandler;
    public StatusChangeHandler statusResetHandler;

    /* note that different chips have these flags on different
     ** bits of the status register
     */
    /* this chip id */
    public Fm.BaseChip statusChangeWhichChip;
    /* 1 on End Of Sample (record/playback/cycle time of AD/DA converting has passed)*/
    public byte statusChangeEOSBit;
    /* 1 after recording 2 datas (2x4bits) or after reading/writing 1 data */
    public byte statusChangeBRDYBit;
    /* 1 if silence lasts for more than 290 miliseconds on ADPCM recording */
    public byte statusChangeZEROBit;

    /* neither Y8950 nor YM2608 can generate IRQ when PCMBSY bit changes, so instead of above,
     ** the statusflag gets ORed with PCM_BSY (below) (on each read of statusflag of Y8950 and YM2608)
     */

    /* 1 when ADPCM is playing; Y8950/YM2608 only */
    public byte pcmBsy;

    /* adpcm registers      */
    public byte[] reg = new byte[16];
    public int regPtr = 0;
    /* which chip we're emulating */
    public byte emulationMode;

    private static final int DELTA_MAX = 24576;
    private static final int DELTA_MIN = 127;
    private static final int DELTA_DEF = 127;

    private static final int DECODE_RANGE = 32768;
    private static final int DECODE_MIN = -(DECODE_RANGE);
    private static final int DECODE_MAX = (DECODE_RANGE) - 1;


    /* Forecast to next Forecast (rate = *8) */
    /* 1/8 , 3/8 , 5/8 , 7/8 , 9/8 , 11/8 , 13/8 , 15/8 */
    private static final int[] decodeTableB1 = new int[] {
            1, 3, 5, 7, 9, 11, 13, 15,
            -1, -3, -5, -7, -9, -11, -13, -15,
    };
    /* delta to next delta (rate= *64) */
    /* 0.9 , 0.9 , 0.9 , 0.9 , 1.2 , 1.6 , 2.0 , 2.4 */
    private static final int[] decodeTableB2 = new int[] {
            57, 57, 57, 57, 77, 102, 128, 153,
            57, 57, 57, 57, 77, 102, 128, 153
    };

    public byte readAdpcm() {
        byte v = 0;

        /* external memory read */
        if ((this.portState & 0xe0) == 0x20) {
            /* two dummy reads */
            if (this.memRead != 0) {
                this.nowAddr = this.start << 1;
                this.memRead--;
                return 0;
            }

            if (this.nowAddr != (this.end << 1)) {
                v = this.memory[this.nowAddr >> 1];

                /*System.err.pritnf("YM Delta-T memory read  $%08x, v=$%02x\n", this.now_addr >> 1, v);*/

                this.nowAddr += 2; /* two nibbles at a time */

                /* reset BRDY bit in status register, which means we are reading the memory now */
                if (this.statusResetHandler != null)
                    if (this.statusChangeBRDYBit != 0)
                        this.statusResetHandler.accept(this.statusChangeBRDYBit);

                /* setup a timer that will callback us in 10 master clock cycles for Y8950
                 * in the callback set the BRDY flag to 1 , which means we have another data ready.
                 * For now, we don't really do this; we simply reset and set the flag in zero time, so that the IRQ will work.
                 */
                /* set BRDY bit in status register */
                if (this.statusSetHandler != null)
                    if (this.statusChangeBRDYBit != 0)
                        this.statusSetHandler.accept(this.statusChangeBRDYBit);
            } else {
                /* set EOS bit in status register */
                if (this.statusSetHandler != null)
                    if (this.statusChangeEOSBit != 0)
                        this.statusSetHandler.accept(this.statusChangeEOSBit);
            }
        }

        return v;
    }

    /** 0-DRAM x1, 1-ROM, 2-DRAM x8, 3-ROM (3 is bad setting - not allowed by the manual) */
    private static final byte[] dramRightShift = new byte[] {3, 0, 0, 0};

    /** DELTA-T ADPCM write register */
    public void writeAdpcm(int r, int v) {
        if (r >= 0x10) return;
        this.reg[this.regPtr + r] = (byte) v; /* stock data */

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
            if (this.emulationMode == EMULATION_MODE_YM2610) {
                v |= 0x20;      /*  YM2610 always uses external memory and doesn't even have memory flag bit. */
            }

            this.portState = (byte) (v & (0x80 | 0x40 | 0x20 | 0x10 | 0x01)); /* start, rec, memory mode, repeat flag copy, reset(bit0) */

            if ((this.portState & 0x80) != 0)/* START,REC,MEMDATA,REPEAT,SPOFF,--,--,RESET */ {
                /* set PCM BUSY bit */
                this.pcmBsy = 1;

                /* start ADPCM */
                this.nowStep = 0;
                this.acc = 0;
                this.prevAcc = 0;
                this.adpCml = 0;
                this.adpCmd = DELTA_DEF;
                this.nowData = 0;
                if (this.start > this.end)
                    System.err.printf("DeltaT-Warning: Start: %06X, End: %06X\n", this.start, this.end);
            }


            if ((this.portState & 0x20) != 0) /* do we access external memory? */ {
                this.nowAddr = this.start << 1;
                this.memRead = 2;    /* two dummy reads needed before accesing external memory via register $08*/

                /* if yes, then let's check if ADPCM memory is mapped and big enough */
                if (this.memory == null) {
// #if DEBUG
                    System.err.print("YM Delta-T ADPCM rom not mapped\n");
// #endif
                    this.portState = 0x00;
                    this.pcmBsy = 0;
                } else {
                    if (this.end >= this.memorySize) /* Check End in Range */ {
// #if DEBUG
                        System.err.printf("YM Delta-T ADPCM end out of range: $%08x\n", this.end);
// #endif
                        this.end = this.memorySize - 1;
                    }
                    if (this.start >= this.memorySize)   /* Check Start in Range */ {
// #if DEBUG
                        System.err.printf("YM Delta-T ADPCM start out of range: $%08x\n", this.start);
// #endif
                        this.portState = 0x00;
                        this.pcmBsy = 0;
                    }
                }
            } else    /* we access CPU memory (ADPCM data register $08) so we only reset now_addr here */ {
                this.nowAddr = 0;
            }

            if ((this.portState & 0x01) != 0) {
                this.portState = 0x00;

                /* clear PCM BUSY bit (in status register) */
                this.pcmBsy = 0;

                /* set BRDY flag */
                if (this.statusSetHandler != null)
                    if (this.statusChangeBRDYBit != 0)
                        this.statusSetHandler.accept(this.statusChangeBRDYBit);
            }
            break;
        case 0x01:  /* L,R,-,-,SAMPLE,DA/AD,RAMTYPE,ROM */
            /* handle emulation mode */
            if (this.emulationMode == EMULATION_MODE_YM2610) {
                v |= 0x01;      /*  YM2610 always uses ROM as an external memory and doesn't have ROM/RAM memory flag bit. */
            }

            this.pan = this.outputPointer;
            this.panPtr = (v >> 6) & 0x03;
            if ((this.control2 & 3) != (v & 3)) {
                /*0-DRAM x1, 1-ROM, 2-DRAM x8, 3-ROM (3 is bad setting - not allowed by the manual) */
                if (this.dromPortShift != dramRightShift[v & 3]) {
                    this.dromPortShift = dramRightShift[v & 3];

                            /* final shift value depends on chip type and memory type selected:
                                    8 for YM2610 (ROM only),
                                    5 for ROM for Y8950 and YM2608,
                                    5 for x8bit DRAMs for Y8950 and YM2608,
                                    2 for x1bit DRAMs for Y8950 and YM2608.
                            */

                    /* refresh addresses */
                    this.start = (this.reg[this.regPtr + 0x3] * 0x0100 | this.reg[this.regPtr + 0x2]) << (this.portShift - this.dromPortShift);
                    this.end = (this.reg[this.regPtr + 0x5] * 0x0100 | this.reg[this.regPtr + 0x4]) << (this.portShift - this.dromPortShift);
                    this.end += (1 << (this.portShift - this.dromPortShift)) - 1;
                    this.limit = (this.reg[this.regPtr + 0xd] * 0x0100 | this.reg[this.regPtr + 0xc]) << (this.portShift - this.dromPortShift);
                }
            }
            this.control2 = (byte) v;
            break;
        case 0x02:  /* Start Address L */
        case 0x03:  /* Start Address H */
            this.start = (this.reg[this.regPtr + 0x3] * 0x0100 | this.reg[this.regPtr + 0x2]) << (this.portShift - this.dromPortShift);
            /*System.err.pritnf("DELTAT start: 02=%2x 03=%2x addr=%8x\n",this.reg[0x2], this.reg[0x3],this.start );*/
            break;
        case 0x04:  /* Stop Address L */
        case 0x05:  /* Stop Address H */
            this.end = (this.reg[this.regPtr + 0x5] * 0x0100 | this.reg[this.regPtr + 0x4]) << (this.portShift - this.dromPortShift);
            this.end += (1 << (this.portShift - this.dromPortShift)) - 1;
            /*System.err.pritnf("DELTAT end  : 04=%2x 05=%2x addr=%8x\n",this.reg[0x4], this.reg[0x5],this.end   );*/
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
            if ((this.portState & 0xe0) == 0x60) {
                if (this.memRead != 0) {
                    this.nowAddr = this.start << 1;
                    this.memRead = 0;
                }

                /*System.err.pritnf("YM Delta-T memory write $%08x, v=$%02x\n", this.now_addr >> 1, v);*/

                if (this.nowAddr != (this.end << 1)) {
                    this.memory[this.nowAddr >> 1] = (byte) v;
                    this.nowAddr += 2; /* two nibbles at a time */

                    /* reset BRDY bit in status register, which means we are processing the write */
                    if (this.statusResetHandler != null)
                        if (this.statusChangeBRDYBit != 0)
                            this.statusResetHandler.accept(this.statusChangeBRDYBit);

                    /* setup a timer that will callback us in 10 master clock cycles for Y8950
                     * in the callback set the BRDY flag to 1 , which means we have written the data.
                     * For now, we don't really do this; we simply reset and set the flag in zero time, so that the IRQ will work.
                     */
                    /* set BRDY bit in status register */
                    if (this.statusSetHandler != null)
                        if (this.statusChangeBRDYBit != 0)
                            this.statusSetHandler.accept(this.statusChangeBRDYBit);

                } else {
                    /* set EOS bit in status register */
                    if (this.statusSetHandler != null)
                        if (this.statusChangeEOSBit != 0)
                            this.statusSetHandler.accept(this.statusChangeEOSBit);
                }

                return;
            }

            /* ADPCM synthesis from CPU */
            if ((this.portState & 0xe0) == 0x80) {
                this.cpuData = (byte) v;

                /* Reset BRDY bit in status register, which means we are full of data */
                if (this.statusResetHandler != null)
                    if (this.statusChangeBRDYBit != 0)
                        this.statusResetHandler.accept(this.statusChangeBRDYBit);
                return;
            }

            break;
        case 0x09:  /* DELTA-N L (ADPCM Playback Prescaler) */
        case 0x0a:  /* DELTA-N H */
            this.delta = this.reg[this.regPtr + 0xa] * 0x0100 | this.reg[this.regPtr + 0x9];
            this.step = (int) ((double) (this.delta /* *(1<<(YM_DELTAT_SHIFT-16)) */) * (this.freqBase));
            /*System.err.pritnf("DELTAT deltan:09=%2x 0a=%2x\n",this.reg[0x9], this.reg[0xa]);*/
            break;
        case 0x0b:  /* Output level control (volume, linear) */ {
            int oldvol = this.volume;
            this.volume = (v & 0xff) * (this.outputRange / 256) / DECODE_RANGE;
            /*                              v     *     ((1<<16)>>8)        >>  15;
             *                       thus:   v     *     (1<<8)              >>  15;
             *                       thus: output_range must be (1 << (15+8)) at least
             *                               v     *     ((1<<23)>>8)        >>  15;
             *                               v     *     (1<<15)             >>  15;
             */
            /*System.err.pritnf("DELTAT vol = %2x\n",v&0xff);*/
            if (oldvol != 0) {
                this.adpCml = (int) ((double) this.adpCml / (double) oldvol * (double) this.volume);
            }
        }
        break;
        case 0x0c:  /* Limit Address L */
        case 0x0d:  /* Limit Address H */
            this.limit = (this.reg[this.regPtr + 0xd] * 0x0100 | this.reg[this.regPtr + 0xc]) << (this.portShift - this.dromPortShift);
            /*System.err.pritnf("DELTAT limit: 0c=%2x 0d=%2x addr=%8x\n",this.reg[0xc], this.reg[0xd],this.limit );*/
            break;
        }
    }

    public void resetAdpcm(int pan, int emulationMode) {
        this.nowAddr = 0;
        this.nowStep = 0;
        this.step = 0;
        this.start = 0;
        this.end = 0;
        this.limit = ~0; /* this way YM2610 and Y8950 (both of which don't have limit address reg) will still work */
        this.volume = 0;
        this.pan = this.outputPointer;
        this.panPtr = pan;
        this.acc = 0;
        this.prevAcc = 0;
        this.adpCmd = 127;
        this.adpCml = 0;
        this.emulationMode = (byte) emulationMode;
        this.portState = (byte) ((emulationMode == EMULATION_MODE_YM2610) ? 0x20 : 0);
        this.control2 = (byte) ((emulationMode == EMULATION_MODE_YM2610) ? 0x01 : 0);  /* default setting depends on the emulation mode. MSX demo called "facdemo_4" doesn't setup control2 register at all and still works */
        this.dromPortShift = dramRightShift[this.control2 & 3];

        /* The flag mask register disables the BRDY after the reset, however
         ** as soon as the mask is enabled the flag needs to be set. */

        /* set BRDY bit in status register */
        if (this.statusSetHandler != null)
            if (this.statusChangeBRDYBit != 0)
                this.statusSetHandler.accept(this.statusChangeBRDYBit);
    }

    public void postLoad(byte[] regs, int regPtr) {
        int r;

        // to keep adpcml
        this.volume = 0;
        // update
        for (r = 1; r < 16; r++)
            writeAdpcm(r, regs[regPtr + r]);
        this.reg = regs;
        this.regPtr = regPtr;

        // current rom data
        if (this.memory != null)
            this.nowData = this.memory[(this.nowAddr >> 1)];
    }

    public void saveState(Fm.DeviceConfig device) {
    }

    public void saveState() {
    }

    private void limit(int val, int max, int min) {
        if (val > max) val = max;
        else if (val < min) val = min;
    }

    private void synthesisFromExternalMemory() {
        int data;

        this.nowStep += this.step;
        if (this.nowStep >= (1 << SHIFT)) {
            int step = this.nowStep >> SHIFT;
            this.nowStep &= (1 << SHIFT) - 1;
            do {

                if (this.nowAddr == (this.limit << 1))
                    this.nowAddr = 0;

                if (this.nowAddr == (this.end << 1)) { // 12-06-2001 JB: corrected comparison. Was > instead of ==
                    if ((this.portState & 0x10) != 0) {
                        /* repeat start */
                        this.nowAddr = this.start << 1;
                        this.acc = 0;
                        this.adpCmd = DELTA_DEF;
                        this.prevAcc = 0;
                    } else {
                        /* set EOS bit in status register */
                        if (this.statusSetHandler != null)
                            if (this.statusChangeEOSBit != 0)
                                this.statusSetHandler.accept(this.statusChangeEOSBit);

                        /* clear PCM BUSY bit (reflected in status register) */
                        this.pcmBsy = 0;

                        this.portState = 0;
                        this.adpCml = 0;
                        this.prevAcc = 0;
                        return;
                    }
                }

                if ((this.nowAddr & 1) != 0) data = this.nowData & 0x0f;
                else {
                    this.nowData = this.memory[(this.nowAddr >> 1)];
                    data = this.nowData >> 4;
                }

                this.nowAddr++;
                /* 12-06-2001 JB: */
                /* YM2610 address register is 24 bits wide.*/
                /* The "+1" is there because we use 1 bit more for nibble calculations.*/
                /* WARNING: */
                /* Side effect: we should take the size of the mapped ROM into account */
                //this.now_addr &= ( (1<<(24+1))-1);
                this.nowAddr &= this.memoryMask;


                /* store accumulator value */
                this.prevAcc = this.acc;

                /* Forecast to next Forecast */
                this.acc += (decodeTableB1[data] * this.adpCmd / 8);
                limit(this.acc, DECODE_MAX, DECODE_MIN);

                /* delta to next delta */
                this.adpCmd = (this.adpCmd * decodeTableB2[data]) / 64;
                limit(this.adpCmd, DELTA_MAX, DELTA_MIN);

                /* ElSemi: Fix interpolator. */
                /*this.prev_acc = prev_acc + ((this.acc - prev_acc) / 2 );*/

            } while ((--step) != 0);

        }

        /* ElSemi: Fix interpolator. */
        this.adpCml = this.prevAcc * (int) ((1 << SHIFT) - this.nowStep);
        this.adpCml += (this.acc * (int) this.nowStep);
        this.adpCml = (this.adpCml >> SHIFT) * (int) this.volume;

        /* output for work of output channels (outd[OPNxxxx])*/
        this.pan[this.panPtr] += this.adpCml;
    }

    private void synthesisFromCPUMemory() {
        this.nowStep += this.step;
        if (this.nowStep >= (1 << SHIFT)) {
            int step = this.nowStep >> SHIFT;
            this.nowStep &= (1 << SHIFT) - 1;
            do {
                int data;
                if ((this.nowAddr & 1) != 0) {
                    data = this.nowData & 0x0f;

                    this.nowData = this.cpuData;

                    /* after we used CPU_data, we set BRDY bit in status register,
                     * which means we are ready to accept another byte of data */
                    if (this.statusSetHandler != null)
                        if (this.statusChangeBRDYBit != 0)
                            this.statusSetHandler.accept(this.statusChangeBRDYBit);
                } else {
                    data = this.nowData >> 4;
                }

                this.nowAddr++;

                /* store accumulator value */
                this.prevAcc = this.acc;

                /* Forecast to next Forecast */
                this.acc += (decodeTableB1[data] * this.adpCmd / 8);
                limit(this.acc, DECODE_MAX, DECODE_MIN);

                /* delta to next delta */
                this.adpCmd = (this.adpCmd * decodeTableB2[data]) / 64;
                limit(this.adpCmd, DELTA_MAX, DELTA_MIN);


            } while ((--step) != 0);

        }

        /* ElSemi: Fix interpolator. */
        this.adpCml = this.prevAcc * (int) ((1 << SHIFT) - this.nowStep);
        this.adpCml += (this.acc * (int) this.nowStep);
        this.adpCml = (this.adpCml >> SHIFT) * (int) this.volume;

        /* output for work of output channels (outd[OPNxxxx])*/
        this.pan[this.panPtr] += this.adpCml;
    }

    /** ADPCM B (Delta-T control type) */
    public void calcAdpcm() {

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

        if ((this.portState & 0xe0) == 0xa0) {
            synthesisFromExternalMemory();
            return;
        }

        if ((this.portState & 0xe0) == 0x80) {
            /* ADPCM synthesis from CPU-managed memory (from reg $08) */
            synthesisFromCPUMemory(); // change output based on data in ADPCM data reg ($08)
        }

        //todo: ADPCM analysis
        //  if ( (this.portstate & 0xe0)==0xc0 )
        //  if ( (this.portstate & 0xe0)==0xe0 )
    }

    public void calcMemMask() {
        int maskSize = 0x01;
        while (maskSize < this.memorySize)
            maskSize <<= 1;

        this.memoryMask = (maskSize << 1) - 1; // it's Mask<<1 because of the nibbles
    }
}
