/* Nuked OPL3
 * Copyright (C) 2013-2020 Nuke.YKT
 *
 * This file is part of Nuked OPL3.
 *
 * Nuked OPL3 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1
 * of the License, or (at your option) any later version.
 *
 * Nuked OPL3 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Nuked OPL3. If not, see <https://www.gnu.org/licenses/>.

 *  Nuked OPL3 emulator.
 *  Thanks:
 *      MAME Development Team(Jarek Burczynski, Tatsuyuki Satoh):
 *          Feedback and Rhythm part calculation information.
 *      forums.submarine.org.uk(carbon14, opl3):
 *          Tremolo and phase generator calculation information.
 *      OPLx decapsulated(Matthew Gambrell, Olli Niemitalo):
 *          OPL2 ROMs.
 *      siliconpr0n.org(John McMaster, digshadow):
 *          YMF262 and VRC VII decaps and die shots.
 *
 * version: 1.8
 */

package mdsound.chips;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static java.lang.System.getLogger;


/**
 * Nuked OPL3
 *
 * @author Nuke.YKT
 * @version 1.8
 * @see "https://github.com/nukeykt/Nuked-OPL3"
 */
public class NukedYmF262 {

    private static final Logger logger = getLogger(NukedYmF262.class.getName());

    static final boolean OPL_ENABLE_STEREOEXT = false;

    static final int OPL_WRITEBUF_SIZE = 1024;
    static final int OPL_WRITEBUF_DELAY = 2;

    private static class Slot {

        Channel channel;
        Chip chip;
        final ShortBuffer out = ShortBuffer.allocate(1);
        final ShortBuffer fbMod = ShortBuffer.allocate(1);
        ShortBuffer mod;
        int prOut;
        int eg_rout;
        int eg_out;
        int eg_inc;
        EnvelopeGen eg_gen;
        int eg_rate;
        int eg_ksl;
        ByteBuffer trem;
        int reg_vib;
        int reg_type;
        int reg_ksr;
        int reg_mult;
        int reg_ksl;
        int reg_tl;
        int reg_ar;
        int reg_dr;
        int reg_sl;
        int reg_rr;
        int reg_wf;
        int key;
        int pg_reset;
        int pg_phase;
        int pg_phase_out;
        int slot_num;
    }

    private static class Channel {

        /** Don't use "slots" keyword to avoid conflict with Qt applications */
        Slot[] slots = new Slot[2];
        Channel pair;
        Chip chip;
        final ShortBuffer[] out = new ShortBuffer[4];

//#if OPL_ENABLE_STEREOEXT
//        int leftPan;
//        int rightPan;
//#endif

        int chType;
        int f_num;
        int block;
        int fb;
        int con;
        int alg;
        int ksv;
        int cha, chb;
        int chc, chd;
        int ch_num;
    }

    public static class WriteBuf {

        long time;
        public int reg;
        int data;
    }

    public static class Chip {

        Channel[] channel = new Channel[18];
        Slot[] slot = new Slot[36];
        int timer;
        long eg_timer;
        int eg_timerRem;
        int eg_state;
        int eg_add;
        int eg_timer_lo;
        int newM;
        int nts;
        int rhy;
        int vibPos;
        int vibShift;
        final ByteBuffer tremolo = ByteBuffer.allocate(1);
        int tremoloPos;
        int tremoloShift;
        int noise;
        final ByteBuffer _zeroMod = ByteBuffer.allocate(2);
        final ShortBuffer zeroMod = _zeroMod.asShortBuffer();
        int[] mixBuff = new int[4];
        int rm_hh_bit2;
        int rm_hh_bit3;
        int rm_hh_bit7;
        int rm_hh_bit8;
        int rm_tc_bit3;
        int rm_tc_bit5;

//#if OPL_ENABLE_STEREOEXT
//        int stereoExt;
//#endif

        // OPL3L
        public int rateRatio;
        public int sampleCnt;
        short[] oldSamples = new short[4];
        public short[] samples = new short[4];

        long writeBuf_sampleCnt;
        public int writeBuf_cur;
        int writeBuf_last;
        long writeBuf_lastTime;
        public WriteBuf[] writeBuf = new WriteBuf[OPL_WRITEBUF_SIZE];

        public Chip() {
            for (int i = 0; i < channel.length; i++) {
                channel[i] = new Channel();
            }
            for (int i = 0; i < slot.length; i++) {
                slot[i] = new Slot();
            }
            for (int i = 0; i < writeBuf.length; i++) {
                writeBuf[i] = new WriteBuf();
            }
        }
    }

//#if OPL_ENABLE_STEREOEXT && !defined OPL_SIN
//#ifndef _USE_MATH_DEFINES
//    private static final int _USE_MATH_DEFINES = 1;
//#endif

    /** input: [0, 256), output: [0, 65536] */
//    private static int OPL_SIN(int x) {
//        return (int) (Math.sin(x * Math.PI / 512.0) * 65536.0);
//    }

    /* Quirk: Some FM channels are output one sample later on the left side than the right. */
    private static final boolean OPL_QUIRK_CHANNELSAMPLEDELAY = !OPL_ENABLE_STEREOEXT;

    public static final int RSM_FRAC = 10;

    /** Channel types */
    private static final int ch_2op = 0;
    private static final int ch_4op = 1;
    private static final int ch_4op2 = 2;
    private static final int ch_drum = 3;

    /** Envelope key types */
    private static final int egk_norm = 0x01;
    private static final int egk_drum = 0x02;

    /** log-sin table */
    private static final int[] logSinRom = {
            0x859, 0x6c3, 0x607, 0x58b, 0x52e, 0x4e4, 0x4a6, 0x471,
            0x443, 0x41a, 0x3f5, 0x3d3, 0x3b5, 0x398, 0x37e, 0x365,
            0x34e, 0x339, 0x324, 0x311, 0x2ff, 0x2ed, 0x2dc, 0x2cd,
            0x2bd, 0x2af, 0x2a0, 0x293, 0x286, 0x279, 0x26d, 0x261,
            0x256, 0x24b, 0x240, 0x236, 0x22c, 0x222, 0x218, 0x20f,
            0x206, 0x1fd, 0x1f5, 0x1ec, 0x1e4, 0x1dc, 0x1d4, 0x1cd,
            0x1c5, 0x1be, 0x1b7, 0x1b0, 0x1a9, 0x1a2, 0x19b, 0x195,
            0x18f, 0x188, 0x182, 0x17c, 0x177, 0x171, 0x16b, 0x166,
            0x160, 0x15b, 0x155, 0x150, 0x14b, 0x146, 0x141, 0x13c,
            0x137, 0x133, 0x12e, 0x129, 0x125, 0x121, 0x11c, 0x118,
            0x114, 0x10f, 0x10b, 0x107, 0x103, 0x0ff, 0x0fb, 0x0f8,
            0x0f4, 0x0f0, 0x0ec, 0x0e9, 0x0e5, 0x0e2, 0x0de, 0x0db,
            0x0d7, 0x0d4, 0x0d1, 0x0cd, 0x0ca, 0x0c7, 0x0c4, 0x0c1,
            0x0be, 0x0bb, 0x0b8, 0x0b5, 0x0b2, 0x0af, 0x0ac, 0x0a9,
            0x0a7, 0x0a4, 0x0a1, 0x09f, 0x09c, 0x099, 0x097, 0x094,
            0x092, 0x08f, 0x08d, 0x08a, 0x088, 0x086, 0x083, 0x081,
            0x07f, 0x07d, 0x07a, 0x078, 0x076, 0x074, 0x072, 0x070,
            0x06e, 0x06c, 0x06a, 0x068, 0x066, 0x064, 0x062, 0x060,
            0x05e, 0x05c, 0x05b, 0x059, 0x057, 0x055, 0x053, 0x052,
            0x050, 0x04e, 0x04d, 0x04b, 0x04a, 0x048, 0x046, 0x045,
            0x043, 0x042, 0x040, 0x03f, 0x03e, 0x03c, 0x03b, 0x039,
            0x038, 0x037, 0x035, 0x034, 0x033, 0x031, 0x030, 0x02f,
            0x02e, 0x02d, 0x02b, 0x02a, 0x029, 0x028, 0x027, 0x026,
            0x025, 0x024, 0x023, 0x022, 0x021, 0x020, 0x01f, 0x01e,
            0x01d, 0x01c, 0x01b, 0x01a, 0x019, 0x018, 0x017, 0x017,
            0x016, 0x015, 0x014, 0x014, 0x013, 0x012, 0x011, 0x011,
            0x010, 0x00f, 0x00f, 0x00e, 0x00d, 0x00d, 0x00c, 0x00c,
            0x00b, 0x00a, 0x00a, 0x009, 0x009, 0x008, 0x008, 0x007,
            0x007, 0x007, 0x006, 0x006, 0x005, 0x005, 0x005, 0x004,
            0x004, 0x004, 0x003, 0x003, 0x003, 0x002, 0x002, 0x002,
            0x002, 0x001, 0x001, 0x001, 0x001, 0x001, 0x001, 0x001,
            0x000, 0x000, 0x000, 0x000, 0x000, 0x000, 0x000, 0x000
    };

    /** exp table */
    private static final int[] expRom = {
            0x7fa, 0x7f5, 0x7ef, 0x7ea, 0x7e4, 0x7df, 0x7da, 0x7d4,
            0x7cf, 0x7c9, 0x7c4, 0x7bf, 0x7b9, 0x7b4, 0x7ae, 0x7a9,
            0x7a4, 0x79f, 0x799, 0x794, 0x78f, 0x78a, 0x784, 0x77f,
            0x77a, 0x775, 0x770, 0x76a, 0x765, 0x760, 0x75b, 0x756,
            0x751, 0x74c, 0x747, 0x742, 0x73d, 0x738, 0x733, 0x72e,
            0x729, 0x724, 0x71f, 0x71a, 0x715, 0x710, 0x70b, 0x706,
            0x702, 0x6fd, 0x6f8, 0x6f3, 0x6ee, 0x6e9, 0x6e5, 0x6e0,
            0x6db, 0x6d6, 0x6d2, 0x6cd, 0x6c8, 0x6c4, 0x6bf, 0x6ba,
            0x6b5, 0x6b1, 0x6ac, 0x6a8, 0x6a3, 0x69e, 0x69a, 0x695,
            0x691, 0x68c, 0x688, 0x683, 0x67f, 0x67a, 0x676, 0x671,
            0x66d, 0x668, 0x664, 0x65f, 0x65b, 0x657, 0x652, 0x64e,
            0x649, 0x645, 0x641, 0x63c, 0x638, 0x634, 0x630, 0x62b,
            0x627, 0x623, 0x61e, 0x61a, 0x616, 0x612, 0x60e, 0x609,
            0x605, 0x601, 0x5fd, 0x5f9, 0x5f5, 0x5f0, 0x5ec, 0x5e8,
            0x5e4, 0x5e0, 0x5dc, 0x5d8, 0x5d4, 0x5d0, 0x5cc, 0x5c8,
            0x5c4, 0x5c0, 0x5bc, 0x5b8, 0x5b4, 0x5b0, 0x5ac, 0x5a8,
            0x5a4, 0x5a0, 0x59c, 0x599, 0x595, 0x591, 0x58d, 0x589,
            0x585, 0x581, 0x57e, 0x57a, 0x576, 0x572, 0x56f, 0x56b,
            0x567, 0x563, 0x560, 0x55c, 0x558, 0x554, 0x551, 0x54d,
            0x549, 0x546, 0x542, 0x53e, 0x53b, 0x537, 0x534, 0x530,
            0x52c, 0x529, 0x525, 0x522, 0x51e, 0x51b, 0x517, 0x514,
            0x510, 0x50c, 0x509, 0x506, 0x502, 0x4ff, 0x4fb, 0x4f8,
            0x4f4, 0x4f1, 0x4ed, 0x4ea, 0x4e7, 0x4e3, 0x4e0, 0x4dc,
            0x4d9, 0x4d6, 0x4d2, 0x4cf, 0x4cc, 0x4c8, 0x4c5, 0x4c2,
            0x4be, 0x4bb, 0x4b8, 0x4b5, 0x4b1, 0x4ae, 0x4ab, 0x4a8,
            0x4a4, 0x4a1, 0x49e, 0x49b, 0x498, 0x494, 0x491, 0x48e,
            0x48b, 0x488, 0x485, 0x482, 0x47e, 0x47b, 0x478, 0x475,
            0x472, 0x46f, 0x46c, 0x469, 0x466, 0x463, 0x460, 0x45d,
            0x45a, 0x457, 0x454, 0x451, 0x44e, 0x44b, 0x448, 0x445,
            0x442, 0x43f, 0x43c, 0x439, 0x436, 0x433, 0x430, 0x42d,
            0x42a, 0x428, 0x425, 0x422, 0x41f, 0x41c, 0x419, 0x416,
            0x414, 0x411, 0x40e, 0x40b, 0x408, 0x406, 0x403, 0x400
    };

    /**
     * freq mult table multiplied by 2
     * <p>
     * 1/2, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 12, 12, 15, 15
     */
    private static final int[] mt = {
            1, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 20, 24, 24, 30, 30
    };

    /** ksl table */
    private static final int[] kslRom = {
            0, 32, 40, 45, 48, 51, 53, 55, 56, 58, 59, 60, 61, 62, 63, 64
    };

    private static final int[] kslShift = {
            8, 1, 2, 0
    };

    /** envelope generator constants */
    private static final int[][] eg_incStep = {
            {0, 0, 0, 0},
            {1, 0, 0, 0},
            {1, 0, 1, 0},
            {1, 1, 1, 0}
    };

    /** address decoding */
    private static final int[] ad_slot = {
            0, 1, 2, 3, 4, 5, -1, -1, 6, 7, 8, 9, 10, 11, -1, -1,
            12, 13, 14, 15, 16, 17, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    };

    private static final int[] ch_slot = {
            0, 1, 2, 6, 7, 8, 12, 13, 14, 18, 19, 20, 24, 25, 26, 30, 31, 32
    };

//#if OPL_ENABLE_STEREOEXT

//    /** stereo extension panning table */
//    private static final int[] panpot_lut = new int[256];
//    private static int panpot_lut_build = 0;

//#endif

    /** Envelope generator */
    private interface EnvelopeSinFunc extends BiFunction<Integer, Integer, Short> {

    }

    private interface EnvelopeGenFunc extends Consumer<Slot> {

    }

    private static short OPL3_EnvelopeCalcExp(int level) {
        if (level > 0x1fff) {
            level = 0x1fff;
        }
        return (short) ((expRom[level & 0xff] << 1) >> (level >> 8));
    }

    private static short OPL3_EnvelopeCalcSin0(int phase, int envelope) {
        int out = 0;
        int neg = 0;
        phase &= 0x3ff;
        if ((phase & 0x200) != 0) {
            neg = 0xffff;
        }
        if ((phase & 0x100) != 0) {
            out = logSinRom[(phase & 0xff) ^ 0xff];
        } else {
            out = logSinRom[phase & 0xff];
        }
        return (short) (OPL3_EnvelopeCalcExp(out + (envelope << 3)) ^ neg);
    }

    private static short OPL3_EnvelopeCalcSin1(int phase, int envelope) {
        int out = 0;
        phase &= 0x3ff;
        if ((phase & 0x200) != 0) {
            out = 0x1000;
        } else if ((phase & 0x100) != 0) {
            out = logSinRom[(phase & 0xff) ^ 0xff];
        } else {
            out = logSinRom[phase & 0xff];
        }
        return OPL3_EnvelopeCalcExp(out + (envelope << 3));
    }

    private static short OPL3_EnvelopeCalcSin2(int phase, int envelope) {
        int out = 0;
        phase &= 0x3ff;
        if ((phase & 0x100) != 0) {
            out = logSinRom[(phase & 0xff) ^ 0xff];
        } else {
            out = logSinRom[phase & 0xff];
        }
        return OPL3_EnvelopeCalcExp(out + (envelope << 3));
    }

    private static short OPL3_EnvelopeCalcSin3(int phase, int envelope) {
        int out = 0;
        phase &= 0x3ff;
        if ((phase & 0x100) != 0) {
            out = 0x1000;
        } else {
            out = logSinRom[phase & 0xff];
        }
        return OPL3_EnvelopeCalcExp(out + (envelope << 3));
    }

    private static short OPL3_EnvelopeCalcSin4(int phase, int envelope) {
        int out = 0;
        int neg = 0;
        phase &= 0x3ff;
        if ((phase & 0x300) == 0x100) {
            neg = 0xffff;
        }
        if ((phase & 0x200) != 0) {
            out = 0x1000;
        } else if ((phase & 0x80) != 0) {
            out = logSinRom[((phase ^ 0xff) << 1) & 0xff];
        } else {
            out = logSinRom[(phase << 1) & 0xff];
        }
        return (short) (OPL3_EnvelopeCalcExp(out + (envelope << 3)) ^ neg);
    }

    private static short OPL3_EnvelopeCalcSin5(int phase, int envelope) {
        int out = 0;
        phase &= 0x3ff;
        if ((phase & 0x200) != 0) {
            out = 0x1000;
        } else if ((phase & 0x80) != 0) {
            out = logSinRom[((phase ^ 0xff) << 1) & 0xff];
        } else {
            out = logSinRom[(phase << 1) & 0xff];
        }
        return OPL3_EnvelopeCalcExp(out + (envelope << 3));
    }

    private static short OPL3_EnvelopeCalcSin6(int phase, int envelope) {
        int neg = 0;
        phase &= 0x3ff;
        if ((phase & 0x200) != 0) {
            neg = 0xffff;
        }
        return (short) (OPL3_EnvelopeCalcExp(envelope << 3) ^ neg);
    }

    private static short OPL3_EnvelopeCalcSin7(int phase, int envelope) {
        int out = 0;
        int neg = 0;
        phase &= 0x3ff;
        if ((phase & 0x200) != 0) {
            neg = 0xffff;
            phase = (phase & 0x1ff) ^ 0x1ff;
        }
        out = phase << 3;
        return (short) (OPL3_EnvelopeCalcExp(out + (envelope << 3)) ^ neg);
    }

    private static final EnvelopeSinFunc[] envelope_sin = {
            NukedYmF262::OPL3_EnvelopeCalcSin0,
            NukedYmF262::OPL3_EnvelopeCalcSin1,
            NukedYmF262::OPL3_EnvelopeCalcSin2,
            NukedYmF262::OPL3_EnvelopeCalcSin3,
            NukedYmF262::OPL3_EnvelopeCalcSin4,
            NukedYmF262::OPL3_EnvelopeCalcSin5,
            NukedYmF262::OPL3_EnvelopeCalcSin6,
            NukedYmF262::OPL3_EnvelopeCalcSin7
    };

    private enum EnvelopeGen {
        Attack,
        Decay,
        Sustain,
        Release
    }

    private static void OPL3_EnvelopeUpdateKSL(Slot slot) {
        short ksl = (short) ((kslRom[slot.channel.f_num >> 6] << 2)
                        - ((0x08 - slot.channel.block) << 5));
        if (ksl < 0) {
            ksl = 0;
        }
        slot.eg_ksl = ksl & 0xff;
    }

    private static void OPL3_EnvelopeCalc(Slot slot) {
        boolean nonzero;
        int rate;
        int rate_hi;
        int rate_lo;
        int reg_rate = 0;
        int ks;
        int eg_shift, shift;
        int eg_rout;
        int eg_inc;
        int eg_off;
        int reset = 0;
        slot.eg_out = (slot.eg_rout + (slot.reg_tl << 2)
                + (slot.eg_ksl >> kslShift[slot.reg_ksl]) + (slot.trem.get(0) & 0xff)) & 0xffff;
        if (slot.key != 0 && slot.eg_gen == EnvelopeGen.Release) {
            reset = 1;
            reg_rate = slot.reg_ar;
        } else {
            switch (slot.eg_gen) {
                case Attack:
                    reg_rate = slot.reg_ar;
                    break;
                case Decay:
                    reg_rate = slot.reg_dr;
                    break;
                case Sustain:
                    if (slot.reg_type == 0) {
                        reg_rate = slot.reg_rr;
                    }
                    break;
                case Release:
                    reg_rate = slot.reg_rr;
                    break;
            }
        }
        slot.pg_reset = reset;
        ks = slot.channel.ksv >> ((slot.reg_ksr ^ 1) << 1);
        nonzero = (reg_rate != 0);
        rate = ks + (reg_rate << 2);
        rate_hi = rate >> 2;
        rate_lo = rate & 0x03;
        if ((rate_hi & 0x10) != 0) {
            rate_hi = 0x0f;
        }
        eg_shift = rate_hi + slot.chip.eg_add;
        shift = 0;
        if (nonzero) {
            if (rate_hi < 12) {
                if (slot.chip.eg_state != 0) {
                    switch (eg_shift) {
                        case 12:
                            shift = 1;
                            break;
                        case 13:
                            shift = (rate_lo >> 1) & 0x01;
                            break;
                        case 14:
                            shift = rate_lo & 0x01;
                            break;
                        default:
                            break;
                    }
                }
            } else {
                shift = (rate_hi & 0x03) + eg_incStep[rate_lo][slot.chip.eg_timer_lo];
                if ((shift & 0x04) != 0) {
                    shift = 0x03;
                }
                if (shift == 0) {
                    shift = slot.chip.eg_state;
                }
            }
        }
        eg_rout = slot.eg_rout;
        eg_inc = 0;
        eg_off = 0;
        // Instant attack
        if (reset != 0 && rate_hi == 0x0f) {
            eg_rout = 0x00;
        }
        // Envelope off
        if ((slot.eg_rout & 0x1f8) == 0x1f8) {
            eg_off = 1;
        }
        if (slot.eg_gen != EnvelopeGen.Attack && reset == 0 && eg_off != 0) {
            eg_rout = 0x1ff;
        }
        switch (slot.eg_gen) {
            case Attack:
                if (slot.eg_rout == 0) {
                    slot.eg_gen = EnvelopeGen.Decay;
                } else if (slot.key != 0 && shift > 0 && rate_hi != 0x0f) {
                    eg_inc = (short) (~slot.eg_rout >>> (4 - shift));
                }
                break;
            case Decay:
                if ((slot.eg_rout >> 4) == slot.reg_sl) {
                    slot.eg_gen = EnvelopeGen.Sustain;
                } else if (eg_off == 0 && reset == 0 && shift > 0) {
                    eg_inc = 1 << (shift - 1);
                }
                break;
            case Sustain:
            case Release:
                if (eg_off == 0 && reset == 0 && shift > 0) {
                    eg_inc = 1 << (shift - 1);
                }
                break;
        }
        slot.eg_rout = (eg_rout + eg_inc) & 0x1ff;
        // Key off
        if (reset != 0) {
            slot.eg_gen = EnvelopeGen.Attack;
        }
        if (slot.key == 0) {
            slot.eg_gen = EnvelopeGen.Release;
        }
    }

    private static void OPL3_EnvelopeKeyOn(Slot slot, int type) {
        slot.key |= type;
    }

    private static void OPL3_EnvelopeKeyOff(Slot slot, int type) {
        slot.key &= ~type;
    }

    //
    // Phase Generator
    //

    private static void OPL3_PhaseGenerate(Slot slot) {
        Chip chip;
        int f_num;
        int baseFreq;
        int rm_xor, n_bit;
        int noise;
        int phase;

        chip = slot.chip;
        f_num = slot.channel.f_num;
        if (slot.reg_vib != 0) {
            int range;
            int vibpos;

            range = (f_num >> 7) & 7;
            vibpos = slot.chip.vibPos;

            if ((vibpos & 3) == 0) {
                range = 0;
            } else if ((vibpos & 1) != 0) {
                range >>= 1;
            }
            range >>= slot.chip.vibShift;

            if ((vibpos & 4) != 0) {
                range = -range;
            }
            f_num += range;
        }
        baseFreq = (f_num << slot.channel.block) >> 1;
        phase = (slot.pg_phase >> 9) & 0xffff;
        if (slot.pg_reset != 0) {
            slot.pg_phase = 0;
        }
        slot.pg_phase += (baseFreq * mt[slot.reg_mult]) >> 1;
        // Rhythm mode
        noise = chip.noise;
        slot.pg_phase_out = phase;
        if (slot.slot_num == 13) { // hh
            chip.rm_hh_bit2 = (phase >> 2) & 1;
            chip.rm_hh_bit3 = (phase >> 3) & 1;
            chip.rm_hh_bit7 = (phase >> 7) & 1;
            chip.rm_hh_bit8 = (phase >> 8) & 1;
        }
        if (slot.slot_num == 17 && (chip.rhy & 0x20) != 0) { // tc
            chip.rm_tc_bit3 = (phase >> 3) & 1;
            chip.rm_tc_bit5 = (phase >> 5) & 1;
        }
        if ((chip.rhy & 0x20) != 0) {
            rm_xor = (chip.rm_hh_bit2 ^ chip.rm_hh_bit7)
                    | (chip.rm_hh_bit3 ^ chip.rm_tc_bit5)
                    | (chip.rm_tc_bit3 ^ chip.rm_tc_bit5);
            switch (slot.slot_num) {
                case 13: // hh
                    slot.pg_phase_out = rm_xor << 9;
                    if ((rm_xor ^ (noise & 1)) != 0) {
                        slot.pg_phase_out |= 0xd0;
                    } else {
                        slot.pg_phase_out |= 0x34;
                    }
                    break;
                case 16: // sd
                    slot.pg_phase_out = (chip.rm_hh_bit8 << 9)
                            | ((chip.rm_hh_bit8 ^ (noise & 1)) << 8);
                    break;
                case 17: // tc
                    slot.pg_phase_out = (rm_xor << 9) | 0x80;
                    break;
                default:
                    break;
            }
        }
        n_bit = ((noise >> 14) ^ noise) & 0x01;
        chip.noise = (noise >> 1) | (n_bit << 22);
    }

    //
    // Slot
    //

    private static void OPL3_SlotWrite20(Slot slot, int data) {
        if (((data >> 7) & 0x01) != 0) {
            slot.trem = slot.chip.tremolo;
        } else {
            slot.trem = slot.chip._zeroMod;
        }
        slot.reg_vib = (data >> 6) & 0x01;
        slot.reg_type = (data >> 5) & 0x01;
        slot.reg_ksr = (data >> 4) & 0x01;
        slot.reg_mult = data & 0x0f;
    }

    private static void OPL3_SlotWrite40(Slot slot, int data) {
        slot.reg_ksl = (data >> 6) & 0x03;
        slot.reg_tl = data & 0x3f;
        OPL3_EnvelopeUpdateKSL(slot);
    }

    private static void OPL3_SlotWrite60(Slot slot, int data) {
        slot.reg_ar = (data >> 4) & 0x0f;
        slot.reg_dr = data & 0x0f;
    }

    private static void OPL3_SlotWrite80(Slot slot, int data) {
        slot.reg_sl = (data >> 4) & 0x0f;
        if (slot.reg_sl == 0x0f) {
            slot.reg_sl = 0x1f;
        }
        slot.reg_rr = data & 0x0f;
    }

    private static void OPL3_SlotWriteE0(Slot slot, int data) {
        slot.reg_wf = data & 0x07;
        if (slot.chip.newM == 0x00) {
            slot.reg_wf &= 0x03;
        }
    }

    private static void OPL3_SlotGenerate(Slot slot) {
        slot.out.put(0, envelope_sin[slot.reg_wf].apply(slot.pg_phase_out + slot.mod.get(0), slot.eg_out));
    }

    private static void OPL3_SlotCalcFB(Slot slot) {
        if (slot.channel.fb != 0x00) {
            slot.fbMod.put(0, (short) ((slot.prOut + slot.out.get(0)) >> (0x09 - slot.channel.fb)));
        } else {
            slot.fbMod.put(0, (short) 0);
        }
        slot.prOut = slot.out.get(0);
    }

    //
    // Channel
    //

//    static void OPL3_ChannelSetupAlg(opl3_channel channel);

    private static void OPL3_ChannelUpdateRhythm(Chip chip, int data) {
        Channel channel6;
        Channel channel7;
        Channel channel8;
        int chnum;

        chip.rhy = data & 0x3f;
        if ((chip.rhy & 0x20) != 0) {
            channel6 = chip.channel[6];
            channel7 = chip.channel[7];
            channel8 = chip.channel[8];
            channel6.out[0] = channel6.slots[1].out;
            channel6.out[1] = channel6.slots[1].out;
            channel6.out[2] = chip.zeroMod;
            channel6.out[3] = chip.zeroMod;
            channel7.out[0] = channel7.slots[0].out;
            channel7.out[1] = channel7.slots[0].out;
            channel7.out[2] = channel7.slots[1].out;
            channel7.out[3] = channel7.slots[1].out;
            channel8.out[0] = channel8.slots[0].out;
            channel8.out[1] = channel8.slots[0].out;
            channel8.out[2] = channel8.slots[1].out;
            channel8.out[3] = channel8.slots[1].out;
            for (chnum = 6; chnum < 9; chnum++) {
                chip.channel[chnum].chType = ch_drum;
            }
            OPL3_ChannelSetupAlg(channel6);
            OPL3_ChannelSetupAlg(channel7);
            OPL3_ChannelSetupAlg(channel8);
            // hh
            if ((chip.rhy & 0x01) != 0) {
                OPL3_EnvelopeKeyOn(channel7.slots[0], egk_drum);
            } else {
                OPL3_EnvelopeKeyOff(channel7.slots[0], egk_drum);
            }
            // tc
            if ((chip.rhy & 0x02) != 0) {
                OPL3_EnvelopeKeyOn(channel8.slots[1], egk_drum);
            } else {
                OPL3_EnvelopeKeyOff(channel8.slots[1], egk_drum);
            }
            // tom
            if ((chip.rhy & 0x04) != 0) {
                OPL3_EnvelopeKeyOn(channel8.slots[0], egk_drum);
            } else {
                OPL3_EnvelopeKeyOff(channel8.slots[0], egk_drum);
            }
            // sd
            if ((chip.rhy & 0x08) != 0) {
                OPL3_EnvelopeKeyOn(channel7.slots[1], egk_drum);
            } else {
                OPL3_EnvelopeKeyOff(channel7.slots[1], egk_drum);
            }
            // bd
            if ((chip.rhy & 0x10) != 0) {
                OPL3_EnvelopeKeyOn(channel6.slots[0], egk_drum);
                OPL3_EnvelopeKeyOn(channel6.slots[1], egk_drum);
            } else {
                OPL3_EnvelopeKeyOff(channel6.slots[0], egk_drum);
                OPL3_EnvelopeKeyOff(channel6.slots[1], egk_drum);
            }
        } else {
            for (chnum = 6; chnum < 9; chnum++) {
                chip.channel[chnum].chType = ch_2op;
                OPL3_ChannelSetupAlg(chip.channel[chnum]);
                OPL3_EnvelopeKeyOff(chip.channel[chnum].slots[0], egk_drum);
                OPL3_EnvelopeKeyOff(chip.channel[chnum].slots[1], egk_drum);
            }
        }
    }

    private static void OPL3_ChannelWriteA0(Channel channel, int data) {
        if ((channel.chip.newM & channel.chType) == ch_4op2) {
            return;
        }
        channel.f_num = (channel.f_num & 0x300) | data;
        channel.ksv = (channel.block << 1)
                | ((channel.f_num >> (0x09 - channel.chip.nts)) & 0x01);
        OPL3_EnvelopeUpdateKSL(channel.slots[0]);
        OPL3_EnvelopeUpdateKSL(channel.slots[1]);
        if ((channel.chip.newM & channel.chType) == ch_4op) {
            channel.pair.f_num = channel.f_num;
            channel.pair.ksv = channel.ksv;
            OPL3_EnvelopeUpdateKSL(channel.pair.slots[0]);
            OPL3_EnvelopeUpdateKSL(channel.pair.slots[1]);
        }
    }

    private static void OPL3_ChannelWriteB0(Channel channel, int data) {
        if ((channel.chip.newM & channel.chType) == ch_4op2) {
            return;
        }
        channel.f_num = (channel.f_num & 0xff) | ((data & 0x03) << 8);
        channel.block = (data >> 2) & 0x07;
        channel.ksv = (channel.block << 1)
                | ((channel.f_num >> (0x09 - channel.chip.nts)) & 0x01);
        OPL3_EnvelopeUpdateKSL(channel.slots[0]);
        OPL3_EnvelopeUpdateKSL(channel.slots[1]);
        if ((channel.chip.newM & channel.chType) == ch_4op) {
            channel.pair.f_num = channel.f_num;
            channel.pair.block = channel.block;
            channel.pair.ksv = channel.ksv;
            OPL3_EnvelopeUpdateKSL(channel.pair.slots[0]);
            OPL3_EnvelopeUpdateKSL(channel.pair.slots[1]);
        }
    }

    private static void OPL3_ChannelSetupAlg(Channel channel) {
        if (channel.chType == ch_drum) {
            if (channel.ch_num == 7 || channel.ch_num == 8) {
                channel.slots[0].mod = channel.chip.zeroMod;
                channel.slots[1].mod = channel.chip.zeroMod;
                return;
            }
            switch (channel.alg & 0x01) {
                case 0x00:
                    channel.slots[0].mod = channel.slots[0].fbMod;
                    channel.slots[1].mod = channel.slots[0].out;
                    break;
                case 0x01:
                    channel.slots[0].mod = channel.slots[0].fbMod;
                    channel.slots[1].mod = channel.chip.zeroMod;
                    break;
            }
            return;
        }
        if ((channel.alg & 0x08) != 0) {
            return;
        }
        if ((channel.alg & 0x04) != 0) {
            channel.pair.out[0] = channel.chip.zeroMod;
            channel.pair.out[1] = channel.chip.zeroMod;
            channel.pair.out[2] = channel.chip.zeroMod;
            channel.pair.out[3] = channel.chip.zeroMod;
            switch (channel.alg & 0x03) {
                case 0x00:
                    channel.pair.slots[0].mod = channel.pair.slots[0].fbMod;
                    channel.pair.slots[1].mod = channel.pair.slots[0].out;
                    channel.slots[0].mod = channel.pair.slots[1].out;
                    channel.slots[1].mod = channel.slots[0].out;
                    channel.out[0] = channel.slots[1].out;
                    channel.out[1] = channel.chip.zeroMod;
                    channel.out[2] = channel.chip.zeroMod;
                    channel.out[3] = channel.chip.zeroMod;
                    break;
                case 0x01:
                    channel.pair.slots[0].mod = channel.pair.slots[0].fbMod;
                    channel.pair.slots[1].mod = channel.pair.slots[0].out;
                    channel.slots[0].mod = channel.chip.zeroMod;
                    channel.slots[1].mod = channel.slots[0].out;
                    channel.out[0] = channel.pair.slots[1].out;
                    channel.out[1] = channel.slots[1].out;
                    channel.out[2] = channel.chip.zeroMod;
                    channel.out[3] = channel.chip.zeroMod;
                    break;
                case 0x02:
                    channel.pair.slots[0].mod = channel.pair.slots[0].fbMod;
                    channel.pair.slots[1].mod = channel.chip.zeroMod;
                    channel.slots[0].mod = channel.pair.slots[1].out;
                    channel.slots[1].mod = channel.slots[0].out;
                    channel.out[0] = channel.pair.slots[0].out;
                    channel.out[1] = channel.slots[1].out;
                    channel.out[2] = channel.chip.zeroMod;
                    channel.out[3] = channel.chip.zeroMod;
                    break;
                case 0x03:
                    channel.pair.slots[0].mod = channel.pair.slots[0].fbMod;
                    channel.pair.slots[1].mod = channel.chip.zeroMod;
                    channel.slots[0].mod = channel.pair.slots[1].out;
                    channel.slots[1].mod = channel.chip.zeroMod;
                    channel.out[0] = channel.pair.slots[0].out;
                    channel.out[1] = channel.slots[0].out;
                    channel.out[2] = channel.slots[1].out;
                    channel.out[3] = channel.chip.zeroMod;
                    break;
            }
        } else {
            switch (channel.alg & 0x01) {
                case 0x00:
                    channel.slots[0].mod = channel.slots[0].fbMod;
                    channel.slots[1].mod = channel.slots[0].out;
                    channel.out[0] = channel.slots[1].out;
                    channel.out[1] = channel.chip.zeroMod;
                    channel.out[2] = channel.chip.zeroMod;
                    channel.out[3] = channel.chip.zeroMod;
                    break;
                case 0x01:
                    channel.slots[0].mod = channel.slots[0].fbMod;
                    channel.slots[1].mod = channel.chip.zeroMod;
                    channel.out[0] = channel.slots[0].out;
                    channel.out[1] = channel.slots[1].out;
                    channel.out[2] = channel.chip.zeroMod;
                    channel.out[3] = channel.chip.zeroMod;
                    break;
            }
        }
    }

    private static void OPL3_ChannelUpdateAlg(Channel channel) {
        channel.alg = channel.con;
        if (channel.chip.newM != 0) {
            if (channel.chType == ch_4op) {
                channel.pair.alg = 0x04 | (channel.con << 1) | (channel.pair.con);
                channel.alg = 0x08;
                OPL3_ChannelSetupAlg(channel.pair);
            } else if (channel.chType == ch_4op2) {
                channel.alg = 0x04 | (channel.pair.con << 1) | (channel.con);
                channel.pair.alg = 0x08;
                OPL3_ChannelSetupAlg(channel);
            } else {
                OPL3_ChannelSetupAlg(channel);
            }
        } else {
            OPL3_ChannelSetupAlg(channel);
        }
    }

    private static void OPL3_ChannelWriteC0(Channel channel, int data) {
        channel.fb = (data & 0x0e) >> 1;
        channel.con = data & 0x01;
        OPL3_ChannelUpdateAlg(channel);
        if (channel.chip.newM != 0) {
            channel.cha = ((data >> 4) & 0x01) != 0 ? ~0 : 0;
            channel.chb = ((data >> 5) & 0x01) != 0 ? ~0 : 0;
            channel.chc = ((data >> 6) & 0x01) != 0 ? ~0 : 0;
            channel.chd = ((data >> 7) & 0x01) != 0 ? ~0 : 0;
        } else {
            channel.cha = channel.chb = 0xffff;
            // TODO: Verify on real chip if DAC2 output is disabled in compat mode
            channel.chc = channel.chd = 0;
        }
//#if OPL_ENABLE_STEREOEXT
//        if (channel.chip.stereoExt == 0) {
//            channel.leftPan = channel.cha << 16;
//            channel.rightPan = channel.chb << 16;
//        }
//#endif
    }

//#if OPL_ENABLE_STEREOEXT

//    private void OPL3_ChannelWriteD0(Channel channel, int data) {
//        if (channel.chip.stereoExt != 0) {
//            channel.leftPan = panpot_lut[data ^ 0xff];
//            channel.rightPan = panpot_lut[data];
//        }
//    }

//#endif

    private static void OPL3_ChannelKeyOn(Channel channel) {
        if (channel.chip.newM != 0) {
            if (channel.chType == ch_4op) {
                OPL3_EnvelopeKeyOn(channel.slots[0], egk_norm);
                OPL3_EnvelopeKeyOn(channel.slots[1], egk_norm);
                OPL3_EnvelopeKeyOn(channel.pair.slots[0], egk_norm);
                OPL3_EnvelopeKeyOn(channel.pair.slots[1], egk_norm);
            } else if (channel.chType == ch_2op || channel.chType == ch_drum) {
                OPL3_EnvelopeKeyOn(channel.slots[0], egk_norm);
                OPL3_EnvelopeKeyOn(channel.slots[1], egk_norm);
            }
        } else {
            OPL3_EnvelopeKeyOn(channel.slots[0], egk_norm);
            OPL3_EnvelopeKeyOn(channel.slots[1], egk_norm);
        }
    }

    private static void OPL3_ChannelKeyOff(Channel channel) {
        if (channel.chip.newM != 0) {
            if (channel.chType == ch_4op) {
                OPL3_EnvelopeKeyOff(channel.slots[0], egk_norm);
                OPL3_EnvelopeKeyOff(channel.slots[1], egk_norm);
                OPL3_EnvelopeKeyOff(channel.pair.slots[0], egk_norm);
                OPL3_EnvelopeKeyOff(channel.pair.slots[1], egk_norm);
            } else if (channel.chType == ch_2op || channel.chType == ch_drum) {
                OPL3_EnvelopeKeyOff(channel.slots[0], egk_norm);
                OPL3_EnvelopeKeyOff(channel.slots[1], egk_norm);
            }
        } else {
            OPL3_EnvelopeKeyOff(channel.slots[0], egk_norm);
            OPL3_EnvelopeKeyOff(channel.slots[1], egk_norm);
        }
    }

    private static void OPL3_ChannelSet4Op(Chip chip, int data) {
        for (int bit = 0; bit < 6; bit++) {
            int chnum = bit;
            if (bit >= 3) {
                chnum += 9 - 3;
            }
            if (((data >> bit) & 0x01) != 0) {
                chip.channel[chnum].chType = ch_4op;
                chip.channel[chnum + 3].chType = ch_4op2;
                OPL3_ChannelUpdateAlg(chip.channel[chnum]);
            } else {
                chip.channel[chnum].chType = ch_2op;
                chip.channel[chnum + 3].chType = ch_2op;
                OPL3_ChannelUpdateAlg(chip.channel[chnum]);
                OPL3_ChannelUpdateAlg(chip.channel[chnum + 3]);
            }
        }
    }

    private static short OPL3_ClipSample(int sample) {
        if (sample > 32767) {
            sample = 32767;
        } else if (sample < -32768) {
            sample = -32768;
        }
        return (short) sample;
    }

    private static void OPL3_ProcessSlot(Slot slot) {
        OPL3_SlotCalcFB(slot);
        OPL3_EnvelopeCalc(slot);
        OPL3_PhaseGenerate(slot);
        OPL3_SlotGenerate(slot);
    }

    public void OPL3_Generate4Ch(Chip chip, short[] buf4) {
        int[] mix = new int[2];
        int shift = 0;

        buf4[1] = OPL3_ClipSample(chip.mixBuff[1]);
        buf4[3] = OPL3_ClipSample(chip.mixBuff[3]);

//#if OPL_QUIRK_CHANNELSAMPLEDELAY
        for (int ii = 0; ii < 15; ii++)
//#else
//        for (int ii = 0; ii < 36; ii++)
//#endif
        {
assert chip.slot[ii].channel != null : "slot: " + ii + ", slot: @" + chip.slot.hashCode() + ", chip: @" + chip.hashCode();
            OPL3_ProcessSlot(chip.slot[ii]);
        }

        mix[0] = mix[1] = 0;
        for (int ii = 0; ii < 18; ii++) {
            Channel channel = chip.channel[ii];
            ShortBuffer[] out = channel.out;
            short accm = (short) (out[0].get(0) + out[1].get(0) + out[2].get(0) + out[3].get(0));
//#if OPL_ENABLE_STEREOEXT
//            mix[0] += (short) ((accm * channel.leftPan) >> 16);
//#else
            mix[0] += (short) (accm & channel.cha);
//#endif
            mix[1] += (short) (accm & channel.chc);
        }
        chip.mixBuff[0] = mix[0];
        chip.mixBuff[2] = mix[1];

//#if OPL_QUIRK_CHANNELSAMPLEDELAY
        for (int ii = 15; ii < 18; ii++) {
            OPL3_ProcessSlot(chip.slot[ii]);
        }
//#endif

        buf4[0] = OPL3_ClipSample(chip.mixBuff[0]);
        buf4[2] = OPL3_ClipSample(chip.mixBuff[2]);

//#if OPL_QUIRK_CHANNELSAMPLEDELAY
        for (int ii = 18; ii < 33; ii++) {
            OPL3_ProcessSlot(chip.slot[ii]);
        }
//#endif

        mix[0] = mix[1] = 0;
        for (int ii = 0; ii < 18; ii++) {
            Channel channel = chip.channel[ii];
            ShortBuffer[] out = channel.out;
            short accm = (short) (out[0].get(0) + out[1].get(0) + out[2].get(0) + out[3].get(0));
//#if OPL_ENABLE_STEREOEXT
//            mix[0] += (short) ((accm * channel.rightPan) >> 16);
//#else
            mix[0] += (short) (accm & channel.chb);
//#endif
            mix[1] += (short) (accm & channel.chd);
        }
        chip.mixBuff[1] = mix[0];
        chip.mixBuff[3] = mix[1];

//#if OPL_QUIRK_CHANNELSAMPLEDELAY
        for (int ii = 33; ii < 36; ii++) {
            OPL3_ProcessSlot(chip.slot[ii]);
        }
//#endif

        if ((chip.timer & 0x3f) == 0x3f) {
            chip.tremoloPos = (chip.tremoloPos + 1) % 210;
        }
        if (chip.tremoloPos < 105) {
            chip.tremolo.put(0, (byte) (chip.tremoloPos >> chip.tremoloShift));
        } else {
            chip.tremolo.put(0, (byte) ((210 - chip.tremoloPos) >> chip.tremoloShift));
        }

        if ((chip.timer & 0x3ff) == 0x3ff) {
            chip.vibPos = (chip.vibPos + 1) & 7;
        }

        chip.timer = (chip.timer + 1) & 0xffff;

        if (chip.eg_state != 0) {
            while (shift < 13 && ((chip.eg_timer >> shift) & 1) == 0) {
                shift++;
            }
            if (shift > 12) {
                chip.eg_add = 0;
            } else {
                chip.eg_add = shift + 1;
            }
            chip.eg_timer_lo = (int) (chip.eg_timer & 0x3);
        }

        if (chip.eg_timerRem != 0 || chip.eg_state != 0) {
            if (chip.eg_timer == 0xf_ffff_ffffL) {
                chip.eg_timer = 0;
                chip.eg_timerRem = 1;
            } else {
                chip.eg_timer++;
                chip.eg_timerRem = 0;
            }
        }

        chip.eg_state ^= 1;

        while (chip.writeBuf[chip.writeBuf_cur].time <= chip.writeBuf_sampleCnt) {
            if ((chip.writeBuf[chip.writeBuf_cur].reg & 0x200) == 0) {
                break;
            }
            chip.writeBuf[chip.writeBuf_cur].reg &= 0x1ff;
            OPL3_WriteReg(chip, chip.writeBuf[chip.writeBuf_cur].reg, chip.writeBuf[chip.writeBuf_cur].data);
            chip.writeBuf_cur = (chip.writeBuf_cur + 1) % OPL_WRITEBUF_SIZE;
        }
        chip.writeBuf_sampleCnt++;
    }

    public void OPL3_Generate(Chip chip, short[] buf) {
        short[] samples = new short[4];
        OPL3_Generate4Ch(chip, samples);
        buf[0] = samples[0];
        buf[1] = samples[1];
    }

    public void OPL3_Generate4ChResampled(Chip chip, short[] buf4) {
        while (chip.sampleCnt >= chip.rateRatio) {
            chip.oldSamples[0] = chip.samples[0];
            chip.oldSamples[1] = chip.samples[1];
            chip.oldSamples[2] = chip.samples[2];
            chip.oldSamples[3] = chip.samples[3];
            OPL3_Generate4Ch(chip, chip.samples);
            chip.sampleCnt -= chip.rateRatio;
        }
        buf4[0] = (short) ((chip.oldSamples[0] * (chip.rateRatio - chip.sampleCnt)
                + chip.samples[0] * chip.sampleCnt) / chip.rateRatio);
        buf4[1] = (short) ((chip.oldSamples[1] * (chip.rateRatio - chip.sampleCnt)
                + chip.samples[1] * chip.sampleCnt) / chip.rateRatio);
        buf4[2] = (short) ((chip.oldSamples[2] * (chip.rateRatio - chip.sampleCnt)
                + chip.samples[2] * chip.sampleCnt) / chip.rateRatio);
        buf4[3] = (short) ((chip.oldSamples[3] * (chip.rateRatio - chip.sampleCnt)
                + chip.samples[3] * chip.sampleCnt) / chip.rateRatio);
        chip.sampleCnt += 1 << RSM_FRAC;
    }

    public void OPL3_GenerateResampled(Chip chip, short[] buf, int off) {
        short[] samples = new short[4]; // why only 2 / 4 samples are used?
        OPL3_Generate4ChResampled(chip, samples);
        buf[off + 0] = samples[0];
        buf[off + 1] = samples[1];
    }

    public void OPL3_Reset(Chip chip, int sampleRate) {
        for (int slotnum = 0; slotnum < 36; slotnum++) {
            Slot slot = chip.slot[slotnum];
            slot.chip = chip;
            slot.mod = chip.zeroMod;
            slot.eg_rout = 0x1ff;
            slot.eg_out = 0x1ff;
            slot.eg_gen = EnvelopeGen.Release;
            slot.trem = chip._zeroMod;
            slot.slot_num = slotnum;
        }
        for (int channum = 0; channum < 18; channum++) {
            Channel channel = chip.channel[channum];
            int local_ch_slot = ch_slot[channum];
            channel.slots[0] = chip.slot[local_ch_slot];
            channel.slots[1] = chip.slot[local_ch_slot + 3];
            chip.slot[local_ch_slot].channel = channel;
            chip.slot[local_ch_slot + 3].channel = channel;
            if ((channum % 9) < 3) {
                channel.pair = chip.channel[channum + 3];
            } else if ((channum % 9) < 6) {
                channel.pair = chip.channel[channum - 3];
            }
            channel.chip = chip;
            channel.out[0] = chip.zeroMod;
            channel.out[1] = chip.zeroMod;
            channel.out[2] = chip.zeroMod;
            channel.out[3] = chip.zeroMod;
            channel.chType = ch_2op;
            channel.cha = 0xffff;
            channel.chb = 0xffff;
//#if OPL_ENABLE_STEREOEXT
//            channel.leftPan = 0x1_0000;
//            channel.rightPan = 0x1_0000;
//#endif
            channel.ch_num = channum;
            OPL3_ChannelSetupAlg(channel);
        }
        chip.noise = 1;
        chip.rateRatio = (int) (((long) sampleRate << RSM_FRAC) / 49716);
logger.log(Level.DEBUG, "rateRatio: " + chip.rateRatio);
        chip.tremoloShift = 4;
        chip.vibShift = 1;

//#if OPL_ENABLE_STEREOEXT
//        if (panpot_lut_build == 0) {
//            for (int i = 0; i < 256; i++) {
//                panpot_lut[i] = OPL_SIN(i);
//            }
//            panpot_lut_build = 1;
//        }
//#endif
    }

    public void OPL3_WriteReg(Chip chip, int reg, int v) {
        int high = (reg >> 8) & 0x01;
        int regm = reg & 0xff;
        switch (regm & 0xf0) {
            case 0x00:
                if (high != 0) {
                    switch (regm & 0x0f) {
                        case 0x04:
                            OPL3_ChannelSet4Op(chip, v);
                            break;
                        case 0x05:
                            chip.newM = v & 0x01;
//#if OPL_ENABLE_STEREOEXT
//                            chip.stereoExt = (v >> 1) & 0x01;
//#endif
                            break;
                    }
                } else {
                    switch (regm & 0x0f) {
                        case 0x08:
                            chip.nts = (v >> 6) & 0x01;
                            break;
                    }
                }
                break;
            case 0x20:
            case 0x30:
                if (ad_slot[regm & 0x1f] >= 0) {
                    OPL3_SlotWrite20(chip.slot[18 * high + ad_slot[regm & 0x1f]], v);
                }
                break;
            case 0x40:
            case 0x50:
                if (ad_slot[regm & 0x1f] >= 0) {
                    OPL3_SlotWrite40(chip.slot[18 * high + ad_slot[regm & 0x1f]], v);
                }
                break;
            case 0x60:
            case 0x70:
                if (ad_slot[regm & 0x1f] >= 0) {
                    OPL3_SlotWrite60(chip.slot[18 * high + ad_slot[regm & 0x1f]], v);
                }
                break;
            case 0x80:
            case 0x90:
                if (ad_slot[regm & 0x1f] >= 0) {
                    OPL3_SlotWrite80(chip.slot[18 * high + ad_slot[regm & 0x1f]], v);
                }
                break;
            case 0xe0:
            case 0xf0:
                if (ad_slot[regm & 0x1f] >= 0) {
                    OPL3_SlotWriteE0(chip.slot[18 * high + ad_slot[regm & 0x1f]], v);
                }
                break;
            case 0xa0:
                if ((regm & 0x0f) < 9) {
                    OPL3_ChannelWriteA0(chip.channel[9 * high + (regm & 0x0f)], v);
                }
                break;
            case 0xb0:
                if (regm == 0xbd && high == 0) {
                    chip.tremoloShift = (((v >> 7) ^ 1) << 1) + 2;
                    chip.vibShift = ((v >> 6) & 0x01) ^ 1;
                    OPL3_ChannelUpdateRhythm(chip, v);
                } else if ((regm & 0x0f) < 9) {
                    OPL3_ChannelWriteB0(chip.channel[9 * high + (regm & 0x0f)], v);
                    if ((v & 0x20) != 0) {
                        OPL3_ChannelKeyOn(chip.channel[9 * high + (regm & 0x0f)]);
                    } else {
                        OPL3_ChannelKeyOff(chip.channel[9 * high + (regm & 0x0f)]);
                    }
                }
                break;
            case 0xc0:
                if ((regm & 0x0f) < 9) {
                    OPL3_ChannelWriteC0(chip.channel[9 * high + (regm & 0x0f)], v);
                }
                break;
//#if OPL_ENABLE_STEREOEXT
//            case 0xd0:
//                if ((regm & 0x0f) < 9) {
//                    OPL3_ChannelWriteD0(chip.channel[9 * high + (regm & 0x0f)], v);
//                }
//                break;
//#endif
        }
    }

    public void OPL3_WriteRegBuffered(Chip chip, int reg, int v) {
        int writeBuf_last = chip.writeBuf_last;
        WriteBuf writeBuf = chip.writeBuf[writeBuf_last];

        if ((writeBuf.reg & 0x200) != 0) {
            OPL3_WriteReg(chip, writeBuf.reg & 0x1ff, writeBuf.data);

            chip.writeBuf_cur = (writeBuf_last + 1) % OPL_WRITEBUF_SIZE;
            chip.writeBuf_sampleCnt = writeBuf.time;
        }

        writeBuf.reg = reg | 0x200;
        writeBuf.data = v;
        long time1 = chip.writeBuf_lastTime + OPL_WRITEBUF_DELAY;
        long time2 = chip.writeBuf_sampleCnt;

        if (time1 < time2) {
            time1 = time2;
        }

        writeBuf.time = time1;
        chip.writeBuf_lastTime = time1;
        chip.writeBuf_last = (writeBuf_last + 1) % OPL_WRITEBUF_SIZE;
    }

    public void OPL3_Generate4ChStream(Chip chip, short[] sndPtr1, int p1, short[] sndPtr2, int p2, int numSamples) {
        short[] samples = new short[4];

        for (int i = 0; i < numSamples; i++) {
            OPL3_Generate4ChResampled(chip, samples);
            sndPtr1[p1 + 0] = samples[0];
            sndPtr1[p1 + 1] = samples[1];
            sndPtr2[p2 + 0] = samples[2];
            sndPtr2[p2 + 1] = samples[3];
            p1 += 2;
            p2 += 2;
        }
    }

    public void OPL3_GenerateStream(Chip chip, short[] sndPtr, int off, int numSamples) {
        for (int i = 0; i < numSamples; i++) {
            OPL3_GenerateResampled(chip, sndPtr, off);
            off += 2;
        }
    }
}
