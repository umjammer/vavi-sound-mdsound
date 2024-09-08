package mdsound.chips;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

import dotnet4j.io.Stream;
import dotnet4j.util.compat.Tuple;
import mdsound.Common;
import vavi.util.Debug;


/**
   YMF278B  FM + Wave table Synthesizer (OPL4)

   Timer and PCM YMF278B.  The FM is shared with the YmF262.

   This chips roughly splits the difference between the Sega 315-5560 MultiPCM
   (Multi32, Model 1/2) and YMF 292-F SCSP (later Model 2, STV, Saturn, Model 3).

   Features as listed in LSI-4MF2782 data sheet:
    FM Synthesis (same as YMF262)
     1. Sound generation mode
         Two-operater mode
          Generates eighteen voices or fifteen voices plus five rhythm sounds simultaneously
         Four-Operator mode
          Generates six voices in four-Operator mode plus six voices in two-Operator mode simultaneously,
          or generates six voices in four-Operator mode plus three voices in two-Operator mode plus five
          rhythm sounds simultaneously
     2. Eight selectable waveforms
     3. Stereo output
    Wave Table Synthesis
     1. Generates twenty-four voices simultaneously
     2. 44.1kHz sampling rate for output Sound data
     3. Selectable from 8-bit, 12-bit and 16-bit word lengths for wave data
     4. Stereo output (16-stage panpot for each Voice)
    Wave data
     1. Accepts 32M bit external memory at maximum
     2. Up to 512 wave tables
     3. External ROM or SRAM can be connected. With SRAM connected, the CPU can download wave data
     4. Outputs chips select signals for 1Mbit, 4Mbit, 8Mbit or 16Mbit memory
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
           fixed crash if MAME is run with no Sound.
   June 4, 2003 -  Changed to dual-license with LGPL for use in OpenMSX.
                   OpenMSX contributed a bugfix where looped samples were
            not being addressed properly, causing pitch fluctuation.
   August 15, 2010 - Backport to MAME-style C from OpenMSX
*/
public class YmF278b {

    /** standard clock for OPL4 */
    public static final int YMF278B_STD_CLOCK = 33868800;

    public static class Interface {
        /** irq Callback */
        public Callback irqCallback; // (int state);
    }

    private int romFileSize = 0x00;
    private byte[] romFile = null;

    // 16 fixed point (EG timing)
    private static final int EG_SH = 16;
    private static final int EG_TIMER_OVERFLOW = (1 << EG_SH);

    // envelope output entries

    private static final int ENV_BITS = 10;
    private static final int ENV_LEN = (1 << ENV_BITS);
    private static final double ENV_STEP = (128.0 / ENV_LEN);
    private static final int MAX_ATT_INDEX = ((1 << (ENV_BITS - 1)) - 1); // 511
    private static final int MIN_ATT_INDEX = 0;

    // Envelope Generator phases

    private static final int EG_ATT = 4;
    private static final int EG_DEC = 3;
    private static final int EG_SUS = 2;
    private static final int EG_REL = 1;
    private static final int EG_OFF = 0;

    /** pseudo Reverb */
    private static final int EG_REV = 5;
    /** damp */
    private static final int EG_DMP = 6;

    /** Pan values, units are -3dB, i.e. 8. */
    private static final int[] panLeft = new int[] {
            0, 8, 16, 24, 32, 40, 48, 256, 256, 0, 0, 0, 0, 0, 0, 0
    };
    private static final int[] panRight = new int[] {
            0, 0, 0, 0, 0, 0, 0, 0, 256, 256, 48, 40, 32, 24, 16, 8
    };

    /** Mixing levels, units are -3dB, and add some marging to avoid clipping */
    private static final int[] mix_level = new int[] {
            8, 16, 24, 32, 40, 48, 56, 256 + 8
    };

    private static int sc(int db) {
        return db / 3 * 0x20;
    }

    /**
     * decay level table (3dB per step)
     * 0 - 15: 0,3,6,9,12,15,18,21,24,27,30,33,36,39,42,93 (dB)
     */
    private static final int[] dl_tab = new int[] {
            sc(0), sc(3), sc(6), sc(9), sc(12), sc(15), sc(18), sc(21),
            sc(24), sc(27), sc(30), sc(33), sc(36), sc(39), sc(42), sc(93)
    };

    private static final int RATE_STEPS = 8;
    private static final int[] egInc = new int[] {
            // cycle: 0  1  2  3  4  5  6  7
            0, 1, 0, 1, 0, 1, 0, 1, //  0  rates 00..12 0 (increment by 0 or 1)
            0, 1, 0, 1, 1, 1, 0, 1, //  1  rates 00..12 1
            0, 1, 1, 1, 0, 1, 1, 1, //  2  rates 00..12 2
            0, 1, 1, 1, 1, 1, 1, 1, //  3  rates 00..12 3

            1, 1, 1, 1, 1, 1, 1, 1, //  4  rate 13 0 (increment by 1)
            1, 1, 1, 2, 1, 1, 1, 2, //  5  rate 13 1
            1, 2, 1, 2, 1, 2, 1, 2, //  6  rate 13 2
            1, 2, 2, 2, 1, 2, 2, 2, //  7  rate 13 3

            2, 2, 2, 2, 2, 2, 2, 2, //  8  rate 14 0 (increment by 2)
            2, 2, 2, 4, 2, 2, 2, 4, //  9  rate 14 1
            2, 4, 2, 4, 2, 4, 2, 4, // 10  rate 14 2
            2, 4, 4, 4, 2, 4, 4, 4, // 11  rate 14 3

            4, 4, 4, 4, 4, 4, 4, 4, // 12  rates 15 0, 15 1, 15 2, 15 3 for decay
            8, 8, 8, 8, 8, 8, 8, 8, // 13  rates 15 0, 15 1, 15 2, 15 3 for attack (zero time)
            0, 0, 0, 0, 0, 0, 0, 0, // 14  infinity rates for attack and decay(s)
    };

    private static int o(int a) {
        return a * RATE_STEPS;
    }

    // rate  0,    1,    2,    3,   4,   5,   6,  7,  8,  9,  10, 11, 12, 13, 14, 15
    // shift 12,   11,   10,   9,   8,   7,   6,  5,  4,  3,  2,  1,  0,  0,  0,  0
    // mask  4095, 2047, 1023, 511, 255, 127, 63, 31, 15, 7,  3,  1,  0,  0,  0,  0
    private static final int[] egRateSelect = new int[] {
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
            o(4), o(5), o(6), o(7),
            o(8), o(9), o(10), o(11),
            o(12), o(12), o(12), o(12),
    };

    private static int o2(int a) {
        return a;
    }

    private static final int[] egRateShift = new int[] {
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
            o2(0), o2(0), o2(0), o2(0),
            o2(0), o2(0), o2(0), o2(0),
            o2(0), o2(0), o2(0), o2(0),
    };

    private static int o3(double a) {
        return (int) ((EG_TIMER_OVERFLOW / a) / 6);
    }

    /**
     * number of steps to take in quarter of Lfo frequency
     * TODO check if frequency matches real chips
     */
    private static final int[] lfoPeriod = new int[] {
            o3(0.168), o3(2.019), o3(3.196), o3(4.206),
            o3(5.215), o3(5.888), o3(6.224), o3(7.066)
    };

    private static int o4(double a) {
        return (int) (a * 65536);
    }

    private static final int[] vibDepth = new int[] {
            o4(0), o4(3.378), o4(5.065), o4(6.750),
            o4(10.114), o4(20.170), o4(40.106), o4(79.307)
    };

    private static int sc2(double db) {
        return (int) (db * (2.0 / ENV_STEP));
    }

    private static final int[] amDepth = new int[] {
            sc2(0), sc2(1.781), sc2(2.906), sc2(3.656),
            sc2(4.406), sc2(5.906), sc2(7.406), sc2(11.91)
    };

    public interface Callback extends Consumer<Integer> {
    }

    private static class Slot {

        private int startAddr;
        private int loopAddr;
        private int endAddr;
        /** fixed-point frequency step */
        private int step;
        /** fixed-point pointer into the sample */
        private int stepPtr;
        private int pos;
        private int sample1, sample2;

        private int envVol;

        private int lfoCnt;
        private int lfoStep;
        private int lfoMax;
        /** wavetable number */
        private int wave;
        /** f-number */
        private int fn;
        /** octave */
        private int oct;
        /** pseudo-Reverb */
        private int prvb;
        /** level direct */
        private int ld;
        /** total level */
        private int tl;
        /** panpot */
        private int pan;
        /** LFO */
        private int lfo;
        /** vibrato */
        private int vib;
        /** AM level */
        private int am;

        private int ar;
        private int d1R;
        private int dl;
        private int d2R;
        /** rate correction */
        private int rc;
        private int rr;

        /** width of the samples */
        private int bits;
        /** slot keyed on */
        private int active;

        private int state;
        private int lfoActive;

        private int muted;

        private void reset() {
            this.wave = this.fn = this.oct = this.prvb = this.ld = this.tl = this.pan =
                    this.lfo = this.vib = this.am = 0;
            this.ar = this.d1R = this.d2R = this.rc = this.rr = 0;
            this.dl = 0;
            this.step = this.stepPtr = 0;
            this.bits = 0;
            this.startAddr = this.loopAddr = this.endAddr = 0;
            this.envVol = MAX_ATT_INDEX;

            this.lfoActive = 0;
            this.lfoCnt = this.lfoStep = 0;
            this.lfoMax = lfoPeriod[0];

            this.state = EG_OFF;
            this.active = 0;

            // not strictly needed, but avoid UMR on savestate
            this.pos = 0;
            this.sample1 = this.sample2 = 0;
        }

        private int computeRate(int val) {
            int res;
            int oct;

            if (val == 0)
                return 0;
            else if (val == 15)
                return 63;

            if (this.rc != 15) {
                oct = this.oct;

                if ((oct & 8) != 0) {
                    oct |= -8;
                }
                res = (oct + this.rc) * 2 + ((this.fn & 0x200) != 0 ? 1 : 0) + val * 4;
            } else {
                res = val * 4;
            }

            if (res < 0)
                res = 0;
            else if (res > 63)
                res = 63;

            return res;
        }

        private int computeVib() {
            return (((this.lfoStep << 8) / this.lfoMax) * vibDepth[this.vib]) >> 24;
        }

        private int computeAm() {
            if (this.lfoActive != 0 && this.am != 0)
                return (((this.lfoStep << 8) / this.lfoMax) * amDepth[this.am]) >> 12;
            else
                return 0;
        }

        private void setLfo(int newlfo) {
            this.lfoStep = (((this.lfoStep << 8) / this.lfoMax) * newlfo) >> 8;
            this.lfoCnt = (((this.lfoCnt << 8) / this.lfoMax) * newlfo) >> 8;

            this.lfo = newlfo;
            this.lfoMax = lfoPeriod[this.lfo];
        }
    }

    private final Slot[] slots = new Slot[] {
            new Slot(), new Slot(), new Slot(), new Slot(),
            new Slot(), new Slot(), new Slot(), new Slot(),
            new Slot(), new Slot(), new Slot(), new Slot(),
            new Slot(), new Slot(), new Slot(), new Slot(),
            new Slot(), new Slot(), new Slot(), new Slot(),
            new Slot(), new Slot(), new Slot(), new Slot()
    };

    /** Global envelope generator counter. */
    private int egCnt;

    private int waveTblHdr;
    private int memMode;
    private int memAdr;

    private int exp;

    private int fmL, fmR;
    private int pcmL, pcmR;

    private int portA, portB, portC;
    private Callback irq_callback;

    private int romSize;
    private byte[] rom;
    private int ramSize;
    private byte[] ram;
    private int clock;

    /** precalculated attenuation values with some marging for enveloppe and pan levels */
    private final int[] volume = new int[256 * 4];

    private final byte[] regs = new byte[256];

    /** that saves a whole lot of CPU */
    private byte fmEnabled;
    private final YmF262 ymf262 = new YmF262();

    private void advance() {
        Slot op;
        int i;
        int rate;
        int shift;
        int select;

        this.egCnt++;
        for (i = 0; i < 24; i++) {
            op = this.slots[i];

            if (op.lfoActive != 0) {
                op.lfoCnt++;
                if (op.lfoCnt < op.lfoMax) {
                    op.lfoStep++;
                } else if (op.lfoCnt < (op.lfoMax * 3)) {
                    op.lfoStep--;
                } else {
                    op.lfoStep++;
                    if (op.lfoCnt == (op.lfoMax * 4))
                        op.lfoCnt = 0;
                }
            }

            // Envelope Generator
            switch (op.state) {
            case EG_ATT: // attack phase
                rate = op.computeRate(op.ar);
                if (rate < 4)
                    break;

                shift = egRateShift[rate];
                if ((this.egCnt & ((1 << shift) - 1)) == 0) {
                    select = egRateSelect[rate];
                    op.envVol += (~op.envVol * egInc[select + ((this.egCnt >> shift) & 7)]) >> 3;
                    if (op.envVol <= MIN_ATT_INDEX) {
                        op.envVol = MIN_ATT_INDEX;
                        if (op.dl != 0)
                            op.state = EG_DEC;
                        else
                            op.state = EG_SUS;
                    }
                }
                break;
            case EG_DEC: // decay phase
                rate = op.computeRate(op.d1R);
                if (rate < 4)
                    break;

                shift = egRateShift[rate];
                if ((this.egCnt & ((1 << shift) - 1)) == 0) {
                    select = egRateSelect[rate];
                    op.envVol += egInc[select + ((this.egCnt >> shift) & 7)];

                    if ((op.envVol > dl_tab[6]) && op.prvb != 0)
                        op.state = EG_REV;
                    else {
                        if (op.envVol >= op.dl)
                            op.state = EG_SUS;
                    }
                }
                break;
            case EG_SUS: // sustain phase
                rate = op.computeRate(op.d2R);
                if (rate < 4)
                    break;

                shift = egRateShift[rate];
                if ((this.egCnt & ((1 << shift) - 1)) == 0) {
                    select = egRateSelect[rate];
                    op.envVol += egInc[select + ((this.egCnt >> shift) & 7)];

                    if ((op.envVol > dl_tab[6]) && op.prvb != 0)
                        op.state = EG_REV;
                    else {
                        if (op.envVol >= MAX_ATT_INDEX) {
                            op.envVol = MAX_ATT_INDEX;
                            op.active = 0;
                        }
                    }
                }
                break;
            case EG_REL: // release phase
                rate = op.computeRate(op.rr);
                if (rate < 4)
                    break;

                shift = egRateShift[rate];
                if ((this.egCnt & ((1 << shift) - 1)) == 0) {
                    select = egRateSelect[rate];
                    op.envVol += egInc[select + ((this.egCnt >> shift) & 7)];

                    if ((op.envVol > dl_tab[6]) && op.prvb != 0)
                        op.state = EG_REV;
                    else {
                        if (op.envVol >= MAX_ATT_INDEX) {
                            op.envVol = MAX_ATT_INDEX;
                            op.active = 0;
                        }
                    }
                }
                break;
            case EG_REV: // pseudo Reverb
                // TODO improve env_vol update
                rate = op.computeRate(5);
                //if (rate < 4)
                // break;

                shift = egRateShift[rate];
                if ((this.egCnt & ((1 << shift) - 1)) == 0) {
                    select = egRateSelect[rate];
                    op.envVol += egInc[select + ((this.egCnt >> shift) & 7)];

                    if (op.envVol >= MAX_ATT_INDEX) {
                        op.envVol = MAX_ATT_INDEX;
                        op.active = 0;
                    }
                }
                break;
            case EG_DMP: // damping
                // TODO improve env_vol update, damp is just fastest decay now
                rate = 56;
                shift = egRateShift[rate];
                if ((this.egCnt & ((1 << shift) - 1)) == 0) {
                    select = egRateSelect[rate];
                    op.envVol += egInc[select + ((this.egCnt >> shift) & 7)];

                    if (op.envVol >= MAX_ATT_INDEX) {
                        op.envVol = MAX_ATT_INDEX;
                        op.active = 0;
                    }
                }
                break;
            case EG_OFF:
                // nothing
                break;

            default:
                //# ifdef _DEBUG
                //Debug.printf(...);
                //#endif
                break;
            }
        }
    }

    private int readMem(int address) {
        if (address < this.romSize)
            return this.rom[address & 0x3f_ffff] & 0xff;
        else if (address < this.romSize + this.ramSize)
            return this.ram[address - (this.romSize & 0x3f_ffff)] & 0xff;
        else
            return 255; // TODO check
    }

    private Tuple<byte[], Integer> readMemAddr(int address) {
        if (address < this.romSize) {
            return new Tuple<>(this.rom, address & 0x3f_ffff);
        } else if (address < this.romSize + this.ramSize) {
            return new Tuple<>(this.ram, address - (this.romSize & 0x3f_ffff));
        } else
            return null; // TODO check
    }

    private void writeMem(int address, int value) {
        if (address < this.romSize) {
        } // can't write to ROM
        else if (address < this.romSize + this.ramSize) {
            //Debug.printf("adr:%06x dat:%02x", address, value);
            this.ram[address - this.romSize] = (byte) value;
        } else {
        } // can't write to unmapped memory
    }

    private int getSample(Slot op) {
        int sample;
        int addr;
        Tuple<byte[], Integer> addrp;

        switch (op.bits) {
        case 0:
            // 8 bit
            sample = readMem(op.startAddr + op.pos) << 8;
            break;
        case 1:
            // 12 bit
            addr = op.startAddr + ((op.pos / 2) * 3);
            addrp = readMemAddr(addr);
            if ((op.pos & 1) != 0)
                sample = (addrp.getItem1()[addrp.getItem2() + 2] << 8) | ((addrp.getItem1()[addrp.getItem2() + 1] << 4) & 0xF0);
            else
                sample = (addrp.getItem1()[addrp.getItem2() + 0] << 8) | (addrp.getItem1()[addrp.getItem2() + 1] & 0xF0);
            break;
        case 2:
            // 16 bit
            addr = op.startAddr + (op.pos * 2);
            addrp = readMemAddr(addr);
            sample = ((addrp.getItem1()[addrp.getItem2() + 0] << 8) | addrp.getItem1()[addrp.getItem2() + 1]) & 0xffff;
            break;
        default:
            // TODO unspecified
            sample = 0;
            break;
        }
        return sample;
    }

    private int anyActive() {
        int i;

        for (i = 0; i < 24; i++) {
            if (this.slots[i].active != 0)
                return 1;
        }
        return 0;
    }

    private void keyOnHelper(Slot slot) {
        int oct;
        int step;

        slot.active = 1;

        oct = slot.oct;
        if ((oct & 8) != 0)
            oct |= -8;
        oct += 5;
        step = slot.fn | 1024;
        if (oct >= 0)
            step <<= oct;
        else
            step >>= -oct;
        slot.step = step;
        slot.state = EG_ATT;
        slot.stepPtr = 0;
        slot.pos = 0;
        slot.sample1 = this.getSample(slot);
        slot.pos = 1;
        slot.sample2 = this.getSample(slot);
    }

    private void writeA(int reg, int data) {
        switch (reg) {
        case 0x02:
//            this.timer_a_count = data;
//            ymf278b_timer_a_reset(chips);
            break;
        case 0x03:
//            this.timer_b_count = data;
//            ymf278b_timer_b_reset(chips);
            break;
        case 0x04:
//            if (data & 0x80)
//                this.current_irq = 0;
//            else {
//                byte old_enable = this.enable;
//                this.enable = data;
//                this.current_irq &= ~data;
//                if ((old_enable ^ data) & 1)
//                    ymf278b_timer_a_reset(chips);
//                if ((old_enable ^ data) & 2)
//                    ymf278b_timer_b_reset(chips);
//            }
//            ymf278b_irq_check(chips);
            break;
        default:
//Debug.printf("YMF278B:  Port A write %02x, %02x\n", reg, data);
            this.ymf262.write(1, data);
            //this.YmF262.Write(0, 0, reg, data);
            if ((reg & 0xF0) == 0xB0 && (data & 0x20) != 0) // Key On set
                this.fmEnabled = 0x01;
            else if (reg == 0xBD && (data & 0x1F) != 0) // one of the Rhythm bits set
                this.fmEnabled = 0x01;
            break;
        }
    }

    private void writeB(int reg, int data) {
        switch (reg) {
        case 0x05: // Opl3/OPL4 Enable
            // Bit 1 enables OPL4 WaveTable Synth
            this.exp = data;
            this.ymf262.write(3, data & ~0x02);
            break;
        default:
            this.ymf262.write(3, data);
            if ((reg & 0xF0) == 0xB0 && (data & 0x20) != 0)
                this.fmEnabled = 0x01;
            break;
        }
//#ifdef _DEBUG
// Debug.printf("YMF278B:  Port B write %02x, %02x\n", reg, data);
//#endif
    }

    private void writeC(int reg, int data) {
        //Debug.printf("ymf278b_C_w reg:%02x dat:%02x", reg, data);

        // Handle slot registers specifically
        if (reg >= 0x08 && reg <= 0xF7) {
            int snum = (reg - 8) % 24;
            Slot slot = this.slots[snum];
            int base;
            Tuple<byte[], Integer> buf;
            int oct;
            int step;

            switch ((reg - 8) / 24) {
            case 0:
                //loadTime = time + LOAD_DELAY;

                slot.wave = (slot.wave & 0x100) | data;
                base = (slot.wave < 384 || this.waveTblHdr == 0) ?
                        (slot.wave * 12) :
                        (this.waveTblHdr * 0x80000 + ((slot.wave - 384) * 12));
                buf = this.readMemAddr(base);

                slot.bits = (buf.getItem1()[buf.getItem2() + 0] & 0xC0) >> 6;
                slot.setLfo((buf.getItem1()[buf.getItem2() + 7] >> 3) & 7);
                slot.vib = buf.getItem1()[buf.getItem2() + 7] & 7;
                slot.ar = (buf.getItem1()[buf.getItem2() + 8] >> 4) & 0xff;
                slot.d1R = buf.getItem1()[buf.getItem2() + 8] & 0xF;
                slot.dl = dl_tab[buf.getItem1()[buf.getItem2() + 9] >> 4] & 0xff;
                slot.d2R = buf.getItem1()[buf.getItem2() + 9] & 0xF;
                slot.rc = (buf.getItem1()[buf.getItem2() + 10] >> 4) & 0xff;
                slot.rr = buf.getItem1()[buf.getItem2() + 10] & 0xF;
                slot.am = buf.getItem1()[buf.getItem2() + 11] & 7;
                slot.startAddr = (buf.getItem1()[buf.getItem2() + 2] & 0xff) | ((buf.getItem1()[buf.getItem2() + 1] & 0xff) << 8) | ((buf.getItem1()[buf.getItem2() + 0] & 0x3F) << 16);
                slot.loopAddr = (buf.getItem1()[buf.getItem2() + 4] & 0xff) + ((buf.getItem1()[buf.getItem2() + 3] & 0xff) << 8);
                slot.endAddr = (((buf.getItem1()[buf.getItem2() + 6] & 0xff) + ((buf.getItem1()[buf.getItem2() + 5] & 0xff) << 8)) ^ 0xffFF);

                if ((this.regs[reg + 4] & 0x080) != 0)
                    keyOnHelper(slot);
                break;
            case 1:
                slot.wave = (slot.wave & 0xff) | ((data & 0x1) << 8);
                slot.fn = (slot.fn & 0x380) | (data >> 1);

                oct = slot.oct;
                if ((oct & 8) != 0)
                    oct |= -8;
                oct += 5;
                step = slot.fn | 1024;
                if (oct >= 0)
                    step <<= oct;
                else
                    step >>= -oct;
                slot.step = step;
                break;
            case 2:
                slot.fn = (slot.fn & 0x07F) | ((data & 0x07) << 7);
                slot.prvb = (data & 0x08) >> 3;
                slot.oct = (data & 0xF0) >> 4;

                oct = slot.oct;
                if ((oct & 8) != 0)
                    oct |= -8;
                oct += 5;
                step = slot.fn | 1024;
                if (oct >= 0)
                    step <<= oct;
                else
                    step >>= -oct;
                slot.step = step;
                break;
            case 3:
                slot.tl = data >> 1;
                slot.ld = data & 0x1;

                // TODO
                if (slot.ld != 0) {
                    // directly change volume
                } else {
                    // interpolate volume
                }
                break;
            case 4:
                if ((data & 0x10) != 0) {
                    // output to DO1 pin:
                    // this pin is not used in moonsound
                    // we emulate this by muting the Sound
                    slot.pan = 8; // both left/right -inf dB
                } else
                    slot.pan = data & 0x0F;

                if ((data & 0x020) != 0) {
                    // LFO reset
                    slot.lfoActive = 0;
                    slot.lfoCnt = 0;
                    slot.lfoMax = lfoPeriod[slot.vib];
                    slot.lfoStep = 0;
                } else {
                    // LFO activate
                    slot.lfoActive = 1;
                }

                switch (data >> 6) {
                case 0: // tone off, no damp
                    if (slot.active != 0 && (slot.state != EG_REV))
                        slot.state = EG_REL;
                    break;
                case 2: // tone on, no damp
                    if ((this.regs[reg] & 0x080) == 0)
                        keyOnHelper(slot);
                    break;
                case 1: // tone off, damp
                case 3: // tone on,  damp
                    slot.state = EG_DMP;
                    break;
                }
                break;
            case 5:
                slot.vib = data & 0x7;
                slot.setLfo((data >> 3) & 0x7);
                break;
            case 6:
                slot.ar = data >> 4;
                slot.d1R = data & 0xF;
                break;
            case 7:
                slot.dl = dl_tab[data >> 4];
                slot.d2R = data & 0xF;
                break;
            case 8:
                slot.rc = data >> 4;
                slot.rr = data & 0xF;
                break;
            case 9:
                slot.am = data & 0x7;
                break;
            }
        } else {
            // All non-slot registers
            switch (reg & 0xff) {
            case 0x00: // TEST
            case 0x01:
                break;

            case 0x02:
                this.waveTblHdr = (data >> 2) & 0x7;
                this.memMode = data & 1;
                break;

            case 0x03:
                this.memAdr = (this.memAdr & 0x00_ffff) | (data << 16);
                break;

            case 0x04:
                this.memAdr = (this.memAdr & 0xff_00ff) | (data << 8);
                break;

            case 0x05:
                this.memAdr = (this.memAdr & 0xff_ff00) | data;
                break;

            case 0x06: // memory data
                //busyTime = time + MEM_WRITE_DELAY;
                this.writeMem(this.memAdr, data);
                this.memAdr = (this.memAdr + 1) & 0xff_ffff;
                break;

            case 0xF8:
                // TODO use these
                this.fmL = data & 0x7;
                this.fmR = (data >> 3) & 0x7;
                break;

            case 0xF9:
                this.pcmL = data & 0x7;
                this.pcmR = (data >> 3) & 0x7;
                break;
            }
        }

        this.regs[reg] = (byte) data;
    }

    private int readReg(int reg) {
        // no need to call updateStream(time)
        int result;
        switch (reg) {
        case 2: // 3 upper bits are device ID
            result = ((this.regs[2] & 0x1F) | 0x20);
            break;

        case 6: // Memory data Register
            //busyTime = time + MEM_READ_DELAY;
            result = this.readMem(this.memAdr);
            this.memAdr = (this.memAdr + 1) & 0xff_ffff;
            break;

        default:
            result = this.regs[reg] & 0xff;
            break;
        }

        return result;
    }

    private int peekReg(int reg) {
        int result = switch (reg) {
            case 2 -> (this.regs[2] & 0x1f) | 0x20; // 3 upper bits are device ID
            case 6 -> this.readMem(this.memAdr); // Memory data Register
            default -> this.regs[reg] & 0xff;
        };

        return result;
    }

    private int readStatus() {
        int result = 0;
//        if (time < busyTime)
//            result |= 0x01;
//        if (time < loadTime)
//            result |= 0x02;
        return result;
    }

    public void updatePcm(int[][] outputs, int samples) {

        if (this.fmEnabled != 0) {
            this.ymf262.update(outputs, samples);
            // apply FM mixing level
            int vl = mix_level[this.fmL] - 8;
            vl = this.volume[vl];
            int vr = mix_level[this.fmR] - 8;
            vr = this.volume[vr];
            // make FM softer by 3 db
            vl = (vl * 0xB5) >> 7;
            vr = (vr * 0xB5) >> 7;
            for (int j = 0; j < samples; j++) {
                outputs[0][j] = (outputs[0][j] * vl) >> 15;
                outputs[1][j] = (outputs[1][j] * vr) >> 15;
            }
        } else {
            for (int i = 0; i < samples; i++) {
                outputs[0][i] = 0x00;
                outputs[1][i] = 0x00;
            }
        }

        if (this.anyActive() == 0) {
            // TODO update internal state, even if muted
            // TODO also mute individual channels
            return;
        }

        int vl = mix_level[this.pcmL];
        int vr = mix_level[this.pcmR];
        for (int j = 0; j < samples; j++) {
            for (int i = 0; i < 24; i++) {
                Slot sl;
                int sample;
                int vol;
                int volLeft;
                int volRight;

                sl = this.slots[i];
                if (sl.active == 0 || sl.muted != 0) {
                    //outputs[0][j] += 0;
                    //outputs[1][j] += 0;
                    continue;
                }

                sample = (sl.sample1 * (0x10000 - sl.stepPtr) + sl.sample2 * sl.stepPtr) >> 16;
                vol = sl.tl + (sl.envVol >> 2) + sl.computeAm();

                volLeft = vol + panLeft[sl.pan] + vl;
                volRight = vol + panRight[sl.pan] + vr;
                // TODO prob doesn't happen in real chips
                //volLeft  = std::max(0, volLeft);
                //volRight = std::max(0, volRight);
                volLeft &= 0x3ff; // catch negative Volume values in a hardware-like way
                volRight &= 0x3ff; // (anything beyond 0x100 results in *0)

                outputs[0][j] += (sample * this.volume[volLeft]) >> 17;
                outputs[1][j] += (sample * this.volume[volRight]) >> 17;

                if (sl.lfoActive != 0 && sl.vib != 0) {
                    int oct;
                    int step;

                    oct = sl.oct;
                    if ((oct & 8) != 0)
                        oct |= -8;
                    oct += 5;
                    step = (sl.fn | 1024) + sl.computeVib();
                    if (oct >= 0)
                        step <<= oct;
                    else
                        step >>= -oct;
                    sl.stepPtr += step;
                } else
                    sl.stepPtr += sl.step;

                while (sl.stepPtr >= 0x10000) {
                    sl.stepPtr -= 0x10000;
                    sl.sample1 = sl.sample2;

                    sl.sample2 = this.getSample(sl);
                    if (sl.pos >= sl.endAddr)
                        sl.pos = sl.pos - sl.endAddr + sl.loopAddr;
                    else
                        sl.pos++;
                }
            }
            this.advance();
        }
    }

    public void write(int offset, int data) {
        switch (offset) {
        case 0:
            this.portA = data;
            this.ymf262.write(offset, data);
            break;

        case 1:
            this.writeA(this.portA, data);
            break;

        case 2:
            this.portB = data;
            this.ymf262.write(offset, data);
            break;

        case 3:
            this.writeB(this.portB, data);
            break;

        case 4:
            this.portC = data;
            break;

        case 5:
            // PCM regs are only accessible if NEW2 is set
            if ((~this.exp & 2) != 0)
                break;

            this.writeC(this.portC, data);
            break;

        default:
            Debug.printf(Level.WARNING, "YMF278B: unexpected write at offset %X to YmF278b = %02X\n", offset, data);
            break;
        }
    }

    private void clearRam() {
        Arrays.fill(this.ram, 0, this.ramSize, (byte) 0);
    }

    private void loadRom(String romPath, Function<String, Stream> romStream) {
        Path romFilename = Paths.get("yrw801.rom");
        if (romPath != null && !romPath.isEmpty()) {
            romFilename = Paths.get(romPath).resolve(romFilename);
        }

        if (romFileSize == 0) {
            romFileSize = 0x0020_0000;

            romFile = null;
            if (romStream == null) {
                if (Files.exists(romFilename)) {
                    try {
                        romFile = Files.readAllBytes(romFilename);
                        romFileSize = romFile.length;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            } else {
                try (Stream st = romStream.apply(romFilename.toString())) {
                    romFile = Common.readAllBytes(st);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                romFileSize = romFile.length;
            }
        }

        this.romSize = romFileSize;
        if (romFile != null && romFile.length > 0) {
            this.rom = new byte[this.romSize];
            for (int i = 0; i < this.romSize; i++) {
                if (i < romFile.length) this.rom[i] = romFile[i];
                else this.rom[i] = 0;
            }
        } else {
            this.rom = new byte[this.romSize];
        }
    }

    private int init(int clock, Callback cb, String romPath, Function<String, Stream> romStream) {
        int rate;

        rate = clock / 768;
        //if (((CHIP_SAMPLING_MODE & 0x01) && rate < CHIP_SAMPLE_RATE) ||
        // CHIP_SAMPLING_MODE == 0x02)
        // rate = CHIP_SAMPLE_RATE;
        ymf262.start(ymf262.EC_DBOPL, clock * 8 / 19, rate, null);
        this.fmEnabled = 0x00;

        this.rom = null;
        this.irq_callback = cb;
        //this.timer_a = timer_alloc(device.machine, ymf278b_timer_a_tick, chips);
        //this.timer_b = timer_alloc(device.machine, ymf278b_timer_b_tick, chips);
        this.clock = clock;

        loadRom(romPath, romStream);
        this.ramSize = 0x0008_0000;
        this.ram = new byte[this.ramSize];
        clearRam();

        return rate;
    }

    public int start(int clock, String romPath, Function<String, Stream> romStream) {
        Interface intf = new Interface();
        //this.device = device;
        //intf = (device.static_config != NULL) ? (final Interface *)device.static_config : &defintrf;
        //intf = defintrf;

        int rate = this.init(clock, intf.irqCallback, romPath, romStream);
        //this.stream = stream_create(device, 0, 2, device.clock/768, chips, ymf278b_pcm_update);

        this.memAdr = 0; // avoid UMR

        // Volume table, 1 = -0.375dB, 8 = -3dB, 256 = -96dB
        for (int i = 0; i < 256; i++) {
            int vol_mul = 0x20 - (i & 0x0F); // 0x10 values per 6 db
            int vol_shift = 5 + (i >> 4); // approximation: -6 dB == divide by two (shift right)
            this.volume[i] = (0x8000 * vol_mul) >> vol_shift;
        }
        //this.volume[i] = (int)(32768 * Math.pow(2.0, (-0.375 / 6) * i));
        for (int i = 256; i < 256 * 4; i++)
            this.volume[i] = 0;
        for (int i = 0; i < 24; i++)
            this.slots[i].muted = 0x00;

        return rate;
    }

    public void stop() {
        this.ymf262.stop();
        this.rom = null;
    }

    public void reset() {
        this.ymf262.reset();
        this.fmEnabled = 0x00;

        this.egCnt = 0;

        for (int i = 0; i < 24; i++)
            this.slots[i].reset();
        for (int i = 255; i >= 0; i--) // reverse order to avoid UMR
            this.writeC(i, 0);

        this.waveTblHdr = this.memMode = 0;
        this.memAdr = 0;
        this.fmL = this.fmR = 3;
        this.pcmL = this.pcmR = 0;
//        busyTime = time;
//        loadTime = time;
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
        if (this.romSize != romSize) {
            this.rom = new byte[romSize];
            this.romSize = romSize;
            Arrays.fill(this.rom, 0, romSize, (byte) 0xff);
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        System.arraycopy(romData, 0, this.rom, dataStart, dataLength);
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        if (this.romSize != romSize) {
            this.rom = new byte[romSize];
            this.romSize = romSize;
            Arrays.fill(this.rom, 0, romSize, (byte) 0xff);
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        if (dataLength >= 0)
            System.arraycopy(romData, srcStartAddress, this.rom, dataStart, dataLength);
    }

    public void writeRam(int dataStart, int dataLength, byte[] ramData, int srcStartAddress) {
        if (dataStart > this.ramSize)
            return;
        if (dataStart + dataLength > this.ramSize)
            dataLength = this.ramSize - dataStart;

        if (dataLength >= 0)
            System.arraycopy(ramData, srcStartAddress, this.ram, dataStart, dataLength);
    }

    public void setMuteMask(int muteMaskFM, int muteMaskWT) {
        this.ymf262.setMuteMask(muteMaskFM);
        for (byte curChn = 0; curChn < 24; curChn++)
            this.slots[curChn].muted = (muteMaskWT >> curChn) & 0x01;
    }
}
