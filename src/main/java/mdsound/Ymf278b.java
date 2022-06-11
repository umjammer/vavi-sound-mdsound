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


public class Ymf278b extends Instrument.BaseInstrument {

    @Override
    public void reset(byte chipID) {
        device_reset_ymf278b(chipID);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipID, int clock) {
        return device_start_ymf278b(chipID, YMF278BChip.YMF278B_STD_CLOCK, null, null);
    }

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        String romPath = null;
        Function<String, Stream> romStream = null;

        if (option != null && option.length > 0) {
            if (option[0] instanceof String) {
                romPath = (String) option[0];
                romStream = null;
            }
            if (option[0] instanceof Function /*<String, Stream>*/) {
                romPath = null;
                romStream = (Function<String, Stream>) option[0];
            }
        }
        return device_start_ymf278b(chipID, clockValue, romPath, romStream);
    }

    @Override
    public void stop(byte chipID) {
        device_stop_ymf278b(chipID);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        ymf278b_pcm_update(chipID, outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    /*
       YMF278B  FM + Wave table Synthesizer (OPL4)

       Timer and PCM YMF278B.  The FM is shared with the Ymf262.

       This chip roughly splits the difference between the Sega 315-5560 MultiPCM
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
               fixed crash if MAME is run with no Sound.
       June 4, 2003 -  Changed to dual-license with LGPL for use in OpenMSX.
                       OpenMSX contributed a bugfix where looped samples were
                not being addressed properly, causing pitch fluctuation.
       August 15, 2010 - Backport to MAME-style C from OpenMSX
    */
    public static class YMF278BChip {

        private static final int YMF278B_STD_CLOCK = 33868800;   /* standard clock for OPL4 */

        public static class ymf278b_interface {
            /* irq callback */
            public callback irq_callback;// (int state);
        }

        private int ROMFileSize = 0x00;
        private byte[] ROMFile = null;

        private static final int EG_SH = 16; // 16.16 fixed point (EG timing)
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

        private static final int EG_REV = 5; // pseudo Reverb
        private static final int EG_DMP = 6; // damp

        // Pan values, units are -3dB, i.e. 8.
        private static final int[] panLeft = new int[] {
                0, 8, 16, 24, 32, 40, 48, 256, 256, 0, 0, 0, 0, 0, 0, 0
        };
        private static final int[] panRight = new int[] {
                0, 0, 0, 0, 0, 0, 0, 0, 256, 256, 48, 40, 32, 24, 16, 8
        };

        // Mixing levels, units are -3dB, and add some marging to avoid clipping
        private static final int[] mix_level = new int[] {
                8, 16, 24, 32, 40, 48, 56, 256 + 8
        };

        // decay level table (3dB per step)
        // 0 - 15: 0, 3, 6, 9,12,15,18,21,24,27,30,33,36,39,42,93 (dB)
        // private static int SC(int db) { return (int)(db * (2.0 / ENV_STEP)); }
        private static int sc(int db) {
            return db / 3 * 0x20;
        }

        private static final int[] dl_tab = new int[] {
                sc(0), sc(3), sc(6), sc(9), sc(12), sc(15), sc(18), sc(21),
                sc(24), sc(27), sc(30), sc(33), sc(36), sc(39), sc(42), sc(93)
        };

        private static final int RATE_STEPS = 8;
        private static final byte[] egInc = new byte[] {
                //cycle:0  1   2  3   4  5   6  7
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

        private static byte o(int a) {
            return (byte) (a * RATE_STEPS);
        }

        private static final byte[] eg_rate_select = new byte[] {
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

        // rate  0,    1,    2,    3,   4,   5,   6,  7,  8,  9,  10, 11, 12, 13, 14, 15
        // shift 12,   11,   10,   9,   8,   7,   6,  5,  4,  3,  2,  1,  0,  0,  0,  0
        // mask  4095, 2047, 1023, 511, 255, 127, 63, 31, 15, 7,  3,  1,  0,  0,  0,  0
        private static byte o2(int a) {
            return (byte) (a);
        }

        private static final byte[] eg_rate_shift = new byte[] {
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

        // number of steps to take in quarter of Lfo frequency
        // TODO check if frequency matches real chip
        private static int o3(double a) {
            return (int) ((EG_TIMER_OVERFLOW / a) / 6);
        }

        private static final int[] lfo_period = new int[] {
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

        public interface callback extends Consumer<Integer> {
        }

        static class Slot {

            public int startAddr;
            public int loopAddr;
            public int endAddr;
            /* fixed-point frequency step */
            public int step;
            /* fixed-point pointer into the sample */
            public int stepPtr;
            public int pos;
            public short sample1, sample2;

            public int envVol;

            public int lfoCnt;
            public int lfoStep;
            public int lfoMax;
            /* wavetable number */
            public short wave;
            /* f-number */
            public short fn;
            /* octave */
            public byte oct;
            /* pseudo-Reverb */
            public byte prvb;
            /* level direct */
            public byte ld;
            /* total level */
            public byte tl;
            /* panpot */
            public byte pan;
            /* LFO */
            public byte lfo;
            /* vibrato */
            public byte vib;
            /* AM level */
            public byte am;

            public byte ar;
            public byte d1R;
            public int dl;
            public byte d2R;
            public byte rc;        /* rate correction */
            public byte rr;

            public byte bits;      /* width of the samples */
            public byte active;        /* slot keyed on */

            public byte state;
            public byte lfoActive;

            public byte muted;

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
                this.lfoMax = lfo_period[0];

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

                this.lfo = (byte) newlfo;
                this.lfoMax = lfo_period[this.lfo];
            }
        }

        public Slot[] slots = new Slot[] {
                new Slot(), new Slot(), new Slot(), new Slot(),
                new Slot(), new Slot(), new Slot(), new Slot(),
                new Slot(), new Slot(), new Slot(), new Slot(),
                new Slot(), new Slot(), new Slot(), new Slot(),
                new Slot(), new Slot(), new Slot(), new Slot(),
                new Slot(), new Slot(), new Slot(), new Slot()
        };

        /* Global envelope generator counter. */
        public int egCnt;

        public byte waveTblHdr;
        public byte memMode;
        public int memAdr;

        public byte exp;

        public int fmL, fmR;
        public int pcmL, pcmR;

        public byte portA, portB, portC;
        public callback irq_callback;

        public int romSize;
        public byte[] rom;
        public int ramSize;
        public byte[] ram;
        public int clock;

        // precalculated attenuation values with some marging for enveloppe and pan levels
        public int[] volume = new int[256 * 4];

        public byte[] regs = new byte[256];

        public Ymf262.OPL3 fmChip = new Ymf262.OPL3();
        // that saves a whole lot of CPU
        public byte fmEnabled;
        //sound_stream * stream;
        public Ymf262 ymf262 = new Ymf262();

        private void advance() {
            Slot op;
            int i;
            byte rate;
            byte shift;
            byte select;

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
                case EG_ATT:    // attack phase
                    rate = (byte) op.computeRate(op.ar);
                    if (rate < 4)
                        break;

                    shift = eg_rate_shift[rate];
                    if ((this.egCnt & ((1 << shift) - 1)) == 0) {
                        select = eg_rate_select[rate];
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
                case EG_DEC:    // decay phase
                    rate = (byte) op.computeRate(op.d1R);
                    if (rate < 4)
                        break;

                    shift = eg_rate_shift[rate];
                    if ((this.egCnt & ((1 << shift) - 1)) == 0) {
                        select = eg_rate_select[rate];
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
                    rate = (byte) op.computeRate(op.d2R);
                    if (rate < 4)
                        break;

                    shift = eg_rate_shift[rate];
                    if ((this.egCnt & ((1 << shift) - 1)) == 0) {
                        select = eg_rate_select[rate];
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
                    rate = (byte) op.computeRate(op.rr);
                    if (rate < 4)
                        break;

                    shift = eg_rate_shift[rate];
                    if ((this.egCnt & ((1 << shift) - 1)) == 0) {
                        select = eg_rate_select[rate];
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
                    rate = (byte) op.computeRate(5);
                    //if (rate < 4)
                    // break;

                    shift = eg_rate_shift[rate];
                    if ((this.egCnt & ((1 << shift) - 1)) == 0) {
                        select = eg_rate_select[rate];
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
                    shift = eg_rate_shift[rate];
                    if ((this.egCnt & ((1 << shift) - 1)) == 0) {
                        select = eg_rate_select[rate];
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
                    //System.err.printf(...);
                    //#endif
                    break;
                }
            }
        }

        private byte readMem(int address) {
            if (address < this.romSize)
                return this.rom[address & 0x3fffff];
            else if (address < this.romSize + this.ramSize)
                return this.ram[address - (this.romSize & 0x3fffff)];
            else
                return (byte) 255; // TODO check
        }

        private Tuple<byte[], Integer> readMemAddr(int address) {
            if (address < this.romSize) {
                return new Tuple<byte[], Integer>(this.rom, address & 0x3fffff);
            } else if (address < this.romSize + this.ramSize) {
                return new Tuple<byte[], Integer>(this.ram, (int) (address - (this.romSize & 0x3fffff)));
            } else
                return null; // TODO check
        }

        private void writeMem(int address, byte value) {
            if (address < this.romSize)
                return; // can't write to ROM
            else if (address < this.romSize + this.ramSize) {
                //System.err.printf("adr:{0:x06} dat:{1:x02}", address, value);
                this.ram[address - this.romSize] = value;
            } else
                return; // can't write to unmapped memory
        }

        private short getSample(Slot op) {
            short sample;
            int addr;
            Tuple<byte[], Integer> addrp;

            switch (op.bits) {
            case 0:
                // 8 bit
                sample = (short) (readMem((int) (op.startAddr + op.pos)) << 8);
                break;
            case 1:
                // 12 bit
                addr = (int) (op.startAddr + ((op.pos / 2) * 3));
                addrp = readMemAddr((int) addr);
                if ((op.pos & 1) != 0)
                    sample = (short) ((addrp.Item1[addrp.Item2 + 2] << 8) | ((addrp.Item1[addrp.Item2 + 1] << 4) & 0xF0));
                else
                    sample = (short) ((addrp.Item1[addrp.Item2 + 0] << 8) | (addrp.Item1[addrp.Item2 + 1] & 0xF0));
                break;
            case 2:
                // 16 bit
                addr = (int) (op.startAddr + (op.pos * 2));
                addrp = readMemAddr((int) addr);
                sample = (short) ((addrp.Item1[addrp.Item2 + 0] << 8) | addrp.Item1[addrp.Item2 + 1]);
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
            step = (int) (slot.fn | 1024);
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

        private void writeA(byte reg, byte data) {
            switch (reg) {
            case 0x02:
                //this.timer_a_count = data;
                //ymf278b_timer_a_reset(chip);
                break;
            case 0x03:
                //this.timer_b_count = data;
                //ymf278b_timer_b_reset(chip);
                break;
            case 0x04:
                    /*if(data & 0x80)
                        this.current_irq = 0;
                    else
                    {
                        byte old_enable = this.enable;
                        this.enable = data;
                        this.current_irq &= ~data;
                        if((old_enable ^ data) & 1)
                            ymf278b_timer_a_reset(chip);
                        if((old_enable ^ data) & 2)
                            ymf278b_timer_b_reset(chip);
                    }
                    ymf278b_irq_check(chip);*/
                break;
            default:
                //#ifdef _DEBUG
                //   System.err.printf("YMF278B:  Port A write %02x, %02x\n", reg, data);
                //#endif
                this.ymf262.ymf262_write(this.fmChip, 1, data);
                //this.Ymf262.Write(0, 0, reg, data);
                if ((reg & 0xF0) == 0xB0 && (data & 0x20) != 0)  // Key On set
                    this.fmEnabled = 0x01;
                else if (reg == 0xBD && (data & 0x1F) != 0)  // one of the Rhythm bits set
                    this.fmEnabled = 0x01;
                break;
            }
        }

        private void writeB(byte reg, byte data) {
            switch (reg) {
            case 0x05:  // OPL3/OPL4 Enable
                // Bit 1 enables OPL4 WaveTable Synth
                this.exp = data;
                this.ymf262.ymf262_write(this.fmChip, 3, data & ~0x02);
                break;
            default:
                this.ymf262.ymf262_write(this.fmChip, 3, data);
                if ((reg & 0xF0) == 0xB0 && (data & 0x20) != 0)
                    this.fmEnabled = 0x01;
                break;
            }
            //#ifdef _DEBUG
            // System.err.printf("YMF278B:  Port B write %02x, %02x\n", reg, data);
            //#endif
        }

        private void writeC(byte reg, byte data) {
            //System.err.printf("ymf278b_C_w reg:{0:x02} dat:{1:x02}", reg, data);

            // Handle slot registers specifically
            if (reg >= 0x08 && reg <= 0xF7) {
                int snum = (reg - 8) % 24;
                Slot slot = this.slots[snum];
                int _base;
                Tuple<byte[], Integer> buf;
                int oct;
                int step;

                switch ((reg - 8) / 24) {
                case 0:
                    //loadTime = time + LOAD_DELAY;

                    slot.wave = (short) ((slot.wave & 0x100) | data);
                    _base = (slot.wave < 384 || this.waveTblHdr == 0) ?
                            (slot.wave * 12) :
                            (this.waveTblHdr * 0x80000 + ((slot.wave - 384) * 12));
                    buf = this.readMemAddr(_base);

                    slot.bits = (byte) ((buf.Item1[buf.Item2 + 0] & 0xC0) >> 6);
                    slot.setLfo((buf.Item1[buf.Item2 + 7] >> 3) & 7);
                    slot.vib = (byte) (buf.Item1[buf.Item2 + 7] & 7);
                    slot.ar = (byte) (buf.Item1[buf.Item2 + 8] >> 4);
                    slot.d1R = (byte) (buf.Item1[buf.Item2 + 8] & 0xF);
                    slot.dl = (int) (dl_tab[buf.Item1[buf.Item2 + 9] >> 4]);
                    slot.d2R = (byte) (buf.Item1[buf.Item2 + 9] & 0xF);
                    slot.rc = (byte) (buf.Item1[buf.Item2 + 10] >> 4);
                    slot.rr = (byte) (buf.Item1[buf.Item2 + 10] & 0xF);
                    slot.am = (byte) (buf.Item1[buf.Item2 + 11] & 7);
                    slot.startAddr = (int) (buf.Item1[buf.Item2 + 2] | (buf.Item1[buf.Item2 + 1] << 8) | ((buf.Item1[buf.Item2 + 0] & 0x3F) << 16));
                    slot.loopAddr = (int) (buf.Item1[buf.Item2 + 4] + (buf.Item1[buf.Item2 + 3] << 8));
                    slot.endAddr = (int) (((buf.Item1[buf.Item2 + 6] + (buf.Item1[buf.Item2 + 5] << 8)) ^ 0xFFFF));

                    if ((this.regs[reg + 4] & 0x080) != 0)
                        keyOnHelper(slot);
                    break;
                case 1:
                    slot.wave = (short) ((slot.wave & 0xFF) | ((data & 0x1) << 8));
                    slot.fn = (short) ((slot.fn & 0x380) | (data >> 1));

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
                    slot.fn = (short) ((slot.fn & 0x07F) | ((data & 0x07) << 7));
                    slot.prvb = (byte) ((data & 0x08) >> 3);
                    slot.oct = (byte) ((data & 0xF0) >> 4);

                    oct = slot.oct;
                    if ((oct & 8) != 0)
                        oct |= -8;
                    oct += 5;
                    step = (int) (slot.fn | 1024);
                    if (oct >= 0)
                        step <<= oct;
                    else
                        step >>= -oct;
                    slot.step = step;
                    break;
                case 3:
                    slot.tl = (byte) (data >> 1);
                    slot.ld = (byte) (data & 0x1);

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
                        slot.pan = (byte) (data & 0x0F);

                    if ((data & 0x020) != 0) {
                        // LFO reset
                        slot.lfoActive = 0;
                        slot.lfoCnt = 0;
                        slot.lfoMax = lfo_period[slot.vib];
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
                    slot.vib = (byte) (data & 0x7);
                    slot.setLfo((data >> 3) & 0x7);
                    break;
                case 6:
                    slot.ar = (byte) (data >> 4);
                    slot.d1R = (byte) (data & 0xF);
                    break;
                case 7:
                    slot.dl = (int) (dl_tab[data >> 4]);
                    slot.d2R = (byte) (data & 0xF);
                    break;
                case 8:
                    slot.rc = (byte) (data >> 4);
                    slot.rr = (byte) (data & 0xF);
                    break;
                case 9:
                    slot.am = (byte) (data & 0x7);
                    break;
                }
            } else {
                // All non-slot registers
                switch (reg & 0xff) {
                case 0x00: // TEST
                case 0x01:
                    break;

                case 0x02:
                    this.waveTblHdr = (byte) ((data >> 2) & 0x7);
                    this.memMode = (byte) (data & 1);
                    break;

                case 0x03:
                    this.memAdr = (this.memAdr & 0x00FFFF) | (data << 16);
                    break;

                case 0x04:
                    this.memAdr = (this.memAdr & 0xFF00FF) | (data << 8);
                    break;

                case 0x05:
                    this.memAdr = (this.memAdr & 0xFFFF00) | data;
                    break;

                case 0x06:  // memory data
                    //busyTime = time + MEM_WRITE_DELAY;
                    this.writeMem(this.memAdr, data);
                    this.memAdr = (this.memAdr + 1) & 0xFFFFFF;
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

            this.regs[reg] = data;
        }

        private byte readReg(byte reg) {
            // no need to call updateStream(time)
            byte result;
            switch (reg) {
            case 2: // 3 upper bits are device ID
                result = (byte) ((this.regs[2] & 0x1F) | 0x20);
                break;

            case 6: // Memory Data Register
                //busyTime = time + MEM_READ_DELAY;
                result = this.readMem(this.memAdr);
                this.memAdr = (this.memAdr + 1) & 0xFFFFFF;
                break;

            default:
                result = this.regs[reg];
                break;
            }

            return result;
        }

        private byte peekReg(byte reg) {
            byte result;

            switch (reg) {
            case 2: // 3 upper bits are device ID
                result = (byte) ((this.regs[2] & 0x1F) | 0x20);
                break;

            case 6: // Memory Data Register
                result = this.readMem(this.memAdr);
                break;

            default:
                result = this.regs[reg];
                break;
            }
            return result;
        }

        private byte readStatus() {
            byte result = 0;
            //if (time < busyTime)
            // result |= 0x01;
            //if (time < loadTime)
            // result |= 0x02;
            return result;
        }

        public void updatePcm(int[][] outputs, int samples) {
            int i;
            int j;
            int vl;
            int vr;

            if (this.fmEnabled != 0) {
                /* memset is done by ymf262_update */
                this.ymf262.ymf262_update_one(this.fmChip, outputs, samples);
                // apply FM mixing level
                vl = mix_level[this.fmL] - 8;
                vl = this.volume[vl];
                vr = mix_level[this.fmR] - 8;
                vr = this.volume[vr];
                // make FM softer by 3 db
                //vl = (vl * 0xB5) >> 8; vr = (vr * 0xB5) >> 8;
                vl = (vl * 0xB5) >> 7;
                vr = (vr * 0xB5) >> 7;
                for (j = 0; j < samples; j++) {
                    outputs[0][j] = (outputs[0][j] * vl) >> 15;
                    outputs[1][j] = (outputs[1][j] * vr) >> 15;
                }
            } else {
                for (i = 0; i < samples; i++) {
                    outputs[0][i] = 0x00;
                    outputs[1][i] = 0x00;
                }
                //memset(outputs[0], 0x00, samples * sizeof(stream_sample_t));
                //memset(outputs[1], 0x00, samples * sizeof(stream_sample_t));
            }

            if (this.anyActive() == 0) {
                // TODO update internal state, even if muted
                // TODO also mute individual channels
                return;
            }

            vl = mix_level[this.pcmL];
            vr = mix_level[this.pcmR];
            for (j = 0; j < samples; j++) {
                for (i = 0; i < 24; i++) {
                    Slot sl;
                    short sample;
                    int vol;
                    int volLeft;
                    int volRight;

                    sl = this.slots[i];
                    if (sl.active == 0 || sl.muted != 0) {
                        //outputs[0][j] += 0;
                        //outputs[1][j] += 0;
                        continue;
                    }

                    sample = (short) ((sl.sample1 * (0x10000 - sl.stepPtr) + sl.sample2 * sl.stepPtr) >> 16);
                    vol = sl.tl + (sl.envVol >> 2) + sl.computeAm();

                    volLeft = vol + panLeft[sl.pan] + vl;
                    volRight = vol + panRight[sl.pan] + vr;
                    // TODO prob doesn't happen in real chip
                    //volLeft  = std::max(0, volLeft);
                    //volRight = std::max(0, volRight);
                    volLeft &= 0x3FF;   // catch negative Volume values in a hardware-like way
                    volRight &= 0x3FF;  // (anything beyond 0x100 results in *0)

                    outputs[0][j] += (sample * this.volume[volLeft]) >> 17;
                    outputs[1][j] += (sample * this.volume[volRight]) >> 17;

                    if (sl.lfoActive != 0 && sl.vib != 0) {
                        int oct;
                        int step;

                        oct = sl.oct;
                        if ((oct & 8) != 0)
                            oct |= -8;
                        oct += 5;
                        step = (int) ((sl.fn | 1024) + sl.computeVib());
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
                            sl.pos = (int) (sl.pos - sl.endAddr + sl.loopAddr);
                        else
                            sl.pos++;
                    }
                }
                this.advance();
            }
        }

        public void write(int offset, byte data) {
            switch (offset) {
            case 0:
                this.portA = data;
                this.ymf262.ymf262_write(this.fmChip, offset, data);
                break;

            case 1:
                this.writeA(this.portA, data);
                break;

            case 2:
                this.portB = data;
                this.ymf262.ymf262_write(this.fmChip, offset, data);
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
System.err.printf("YMF278B: unexpected write at offset %X to Ymf278b = %02X\n", offset, data);
                break;
            }
        }

        private void clearRam() {
            for (int i = 0; i < this.ramSize; i++) this.ram[i] = 0;
        }

        private void loadRom(String romPath, Function<String, Stream> romStream) throws IOException {
            Path ROM_FILENAME = Paths.get("yrw801.rom");
            if (romPath != null || !romPath.isEmpty()) {
                ROM_FILENAME = Paths.get(romPath, ROM_FILENAME.toString());
            }

            if (ROMFileSize == 0) {
                ROMFileSize = 0x00200000;

                ROMFile = null;
                if (romStream == null) {
                    if (Files.exists(ROM_FILENAME)) {
                        try {
                            ROMFile = Files.readAllBytes(ROM_FILENAME);
                            ROMFileSize = ROMFile.length;
                        } catch (Exception e) {
                        }
                    }
                } else {
                    try (Stream st = romStream.apply(ROM_FILENAME.toString())) {
                        ROMFile = Common.readAllBytes(st);
                    }
                    ROMFileSize = (int) ROMFile.length;
                }
            }

            this.romSize = ROMFileSize;
            if (ROMFile != null && ROMFile.length > 0) {
                this.rom = new byte[this.romSize];
                for (int i = 0; i < this.romSize; i++) {
                    if (i < ROMFile.length) this.rom[i] = ROMFile[i];
                    else this.rom[i] = 0;
                }
            } else {
                this.rom = new byte[this.romSize];
                for (int i = 0; i < this.romSize; i++) this.rom[i] = 0;
            }
        }

        private int init(int clock, callback cb, String romPath, Function<String, Stream> romStream) {
            int rate;

            rate = clock / 768;
            //if (((CHIP_SAMPLING_MODE & 0x01) && rate < CHIP_SAMPLE_RATE) ||
            // CHIP_SAMPLING_MODE == 0x02)
            // rate = CHIP_SAMPLE_RATE;
            this.fmChip = this.ymf262.ymf262_init(clock * 8 / 19, rate);
            this.fmEnabled = 0x00;

            this.rom = null;
            this.irq_callback = cb;
            //this.timer_a = timer_alloc(device.machine, ymf278b_timer_a_tick, chip);
            //this.timer_b = timer_alloc(device.machine, ymf278b_timer_b_tick, chip);
            this.clock = clock;

            try {
                loadRom(romPath, romStream);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            this.ramSize = 0x00080000;
            this.ram = new byte[this.ramSize];// (byte*)malloc(this.RAMSize);
            clearRam();

            return rate;
        }

        public int start(int clock, String romPath, Function<String, Stream> romStream) {
            ymf278b_interface intf = new ymf278b_interface();
            //this.device = device;
            //intf = (device.static_config != NULL) ? (final ymf278b_interface *)device.static_config : &defintrf;
            //intf = defintrf;

            int rate = this.init(clock, intf.irq_callback, romPath, romStream);
            //this.stream = stream_create(device, 0, 2, device.clock/768, chip, ymf278b_pcm_update);

            this.memAdr = 0; // avoid UMR

            // Volume table, 1 = -0.375dB, 8 = -3dB, 256 = -96dB
            for (int i = 0; i < 256; i++) {
                int vol_mul = 0x20 - (i & 0x0F);    // 0x10 values per 6 db
                int vol_shift = 5 + (i >> 4);       // approximation: -6 dB == divide by two (shift right)
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
            this.ymf262.ymf262_shutdown(this.fmChip);
            this.rom = null;
        }

        public void reset() {
            this.ymf262.ymf262_reset_chip(this.fmChip);
            this.fmEnabled = 0x00;

            this.egCnt = 0;

            for (int i = 0; i < 24; i++)
                this.slots[i].reset();
            for (int i = 255; i >= 0; i--)  // reverse order to avoid UMR
                this.writeC((byte) i, (byte) 0);

            this.waveTblHdr = this.memMode = 0;
            this.memAdr = 0;
            this.fmL = this.fmR = 3;
            this.pcmL = this.pcmR = 0;
            //busyTime = time;
            //loadTime = time;
        }

        public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
            if (this.romSize != romSize) {
                this.rom = new byte[romSize];
                this.romSize = (int) romSize;
                for (int i = 0; i < romSize; i++) this.rom[i] = (byte) 0xff;
                //memset(this.rom, 0xFF, romSize);
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            if (dataLength >= 0)
                System.arraycopy(romData, 0, this.rom, dataStart, dataLength);
        }

        public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
            if (this.romSize != romSize) {
                this.rom = new byte[romSize];// (byte*)realloc(this.rom, romSize);
                this.romSize = (int) romSize;
                for (int i = 0; i < romSize; i++) this.rom[i] = (byte) 0xff;
                //memset(this.rom, 0xFF, romSize);
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
                dataLength = (int) this.ramSize - dataStart;

            if (dataLength >= 0)
                System.arraycopy(ramData, srcStartAddress, this.ram, dataStart, dataLength);
        }

        public void setMuteMask(int muteMaskFM, int muteMaskWT) {
            this.ymf262.ymf262_set_mutemask(this.fmChip, muteMaskFM);
            for (byte curChn = 0; curChn < 24; curChn++)
                this.slots[curChn].muted = (byte) ((muteMaskWT >> curChn) & 0x01);
        }
    }

    private static final int MAX_CHIPS = 0x10;
    private final YMF278BChip[] ymF278BData = new YMF278BChip[] {new YMF278BChip(), new YMF278BChip()};

    @Override
    public String getName() {
        return "YMF278B";
    }

    @Override
    public String getShortName() {
        return "OPL4";
    }

    public void ymf278b_pcm_update(byte chipID, int[][] outputs, int samples) {
        YMF278BChip chip = ymF278BData[chipID];
        chip.updatePcm(outputs, samples);
    }

    public void ymf278b_w(byte chipID, int offset, byte data) {
        YMF278BChip chip = ymF278BData[chipID];
        chip.write(offset, data);
    }

    public int device_start_ymf278b(byte chipID, int clock, String romPath, Function<String, Stream> romStream) {
        if (chipID >= MAX_CHIPS)
            return 0;

        YMF278BChip chip = ymF278BData[chipID];
        return chip.start(clock, romPath, romStream);
    }

    public void device_stop_ymf278b(byte chipID) {
        YMF278BChip chip = ymF278BData[chipID];
        chip.stop();
    }

    public void device_reset_ymf278b(byte chipID) {
        YMF278BChip chip = ymF278BData[chipID];
        chip.reset();
    }

    public void ymf278b_write_rom(byte chipID, int romSize, int dataStart, int dataLength, byte[] romData) {
        YMF278BChip chip = ymF278BData[chipID];
        chip.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void ymf278b_write_rom(byte chipID, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        YMF278BChip chip = ymF278BData[chipID];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    public void ymf278b_write_ram(byte chipID, int dataStart, int dataLength, byte[] ramData, int srcStartAddress) {
        YMF278BChip chip = ymF278BData[chipID];
        chip.writeRam(dataStart, dataLength, ramData, srcStartAddress);
    }

    public void ymf278b_set_mute_mask(byte chipID, int muteMaskFM, int muteMaskWT) {
        YMF278BChip chip = ymF278BData[chipID];
        chip.setMuteMask(muteMaskFM, muteMaskWT);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        ymf278b_w(chipID, (port << 1) | 0x00, (byte) adr);
        ymf278b_w(chipID, (port << 1) | 0x01, (byte) data);
        return 0;
    }
}

