/*
 * YmF262 - software implementation of YMF262
 *                  FM Sound generator type Opl3
 *
 * Copyright Jarek Burczynski
 *
 * ----
 *
 * Originally based on ADLIBEMU.C, an AdLib/OPL2 emulation library by Ken Silverman
 * Copyright (C) 1998-2001 Ken Silverman
 *
 * ----
 *
 *  Copyright (C) 2002-2010  The DOSBox Team
 *  OPL2/Opl3 emulation library
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

package mdsound.chips;

import java.util.Arrays;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


/**
 * YmF262 - software implementation of YMF262
 *                  FM Sound generator type Opl3
 * <pre>
 Revision History:

 03-03-2003: initial release
 - thanks to Olivier Galibert and Chris Hardy for YMF262 and YAC512 chips
 - thanks to Stiletto for the datasheets

 Features as listed in 4MF262A6 data sheet:
 1. Registers are compatible with YM3812 (OPL2) FM Sound source.
 2. Up to six sounds can be used as four-Operator melody sounds for variety.
 3. 18 simultaneous melody sounds, or 15 melody sounds with 5 rhythm sounds (with two operators).
 4. 6 four-Operator melody sounds and 6 two-Operator melody sounds, or 6 four-Operator melody
 sounds, 3 two-Operator melody sounds and 5 rhythm sounds (with four operators).
 5. 8 selectable waveforms.
 6. 4-channel Sound output.
 7. YMF262 compatible DAC (YAC512) is available.
 8. LFO for vibrato and tremolo effects.
 9. 2 programmable timers.
 10. Shorter register access time compared with YM3812.
 11. 5V single supply silicon gate CMOS process.
 12. 24 Pin SOP Package (YMF262-MPcm), 48 Pin SQFP Package (YMF262-S).

 differences between OPL2 and Opl3 not documented in Yamaha datasheets:
 - sinus table is a little different: the negative part is off by one...

 - in order to enable selection of four different waveforms on OPL2
 one must set bit 5 in register 0x01(test).
 on Opl3 this bit is ignored and 4-waveForm select works *always*.
 (Don't confuse this with Opl3's 8-waveForm select.)

 - Envelope Generator: all 15 x rates take zero time on Opl3
 (on OPL2 15 0 and 15 1 rates take some time while 15 2 and 15 3 rates
 take zero time)

 - channel calculations: output of Operator 1 is in perfect sync with
 output of Operator 2 on Opl3; on OPL and OPL2 output of Operator 1
 is always delayed by one sample compared to output of Operator 2

 differences between OPL2 and Opl3 shown in datasheets:
 - YMF262 does not support CSM mode
 * </pre>
 *
 * @author Ken Silverman (AdLibEmu)
 * @author Jarek Burczynski
 * @version 0.2
 */
public class YmF262 {

//    private static final boolean OPLTYPE_IS_OPL3 = true;

    /** DosBox OPL (AdLibEmu) ... uses {@link AdlibOpl3} */
    public static final int EC_DBOPL = 0x00;
    /** YMF262 core from MAME ... uses {@link MameOpl3}  */
    public static final int EC_MAME = 0x01;

    public interface UpdateHandler extends Runnable {
    }

    /** common chip operation */
    private interface Opl3 {
        void update(int[][] buffers, int length);
        void stop();
        void reset();
        int read(int a);
        int write(int a, int v);
        void setMuteMask(int muteMask);
    }

    private Opl3 opl3;

    /**
     * @param emuCore {@link #EC_DBOPL} or {@link #EC_MAME}
     * @param updateHandler works when {@code emuCore} is {@link #EC_DBOPL} only, nullable
     */
    public void start(int emuCore, int clock, int rate, UpdateHandler updateHandler) {
        switch (emuCore) {
        case EC_MAME:
            opl3 = new MameOpl3(clock, rate, MameOpl3.OPL3_TYPE_YMF262);
            break;
        case EC_DBOPL:
            opl3 = new AdlibOpl3(clock, rate, updateHandler);
            break;
        }
    }

    public void stop() {
        opl3.stop();
    }

    public void reset() {
        opl3.reset();
    }

    public int read(int offset) {
        return opl3.read(offset & 0x03);
    }

    public void write(int offset, int data) {
        opl3.write(offset & 3, data);
    }

    public void update(int[][] outputs, int samples) {
        opl3.update(outputs, samples);
    }

    public void setMuteMask(int muteMask) {
        opl3.setMuteMask(muteMask);
    }

    /**
     * YmF262 MAME version
     *
     * @author Jarek Burczynski
    */
    public static class MameOpl3 implements Opl3 {

        public interface TimerHandler extends BiConsumer<Integer, Integer> {
        }

        public interface IrqHandler extends Consumer<Integer> {
        }

        public static class Channel {

            public static class Slot {

                // Envelope Generator phases

                private static final int EG_ATT = 4;
                private static final int EG_DEC = 3;
                private static final int EG_SUS = 2;
                private static final int EG_REL = 1;
                private static final int EG_OFF = 0;

                private static int sc(double db) {
                    return (int) (db * (2.0 / ENV_STEP));
                }

                /**
                 * sustain level table (3dB per step)
                 * 0 - 15: 0, 3, 6, 9,12,15,18,21,24,27,30,33,36,39,42,93 (dB)
                 */
                private static final int[] slTab = {
                        sc(0), sc(1), sc(2), sc(3), sc(4), sc(5), sc(6), sc(7),
                        sc(8), sc(9), sc(10), sc(11), sc(12), sc(13), sc(14), sc(31)
                };


                private static final int RATE_STEPS = 8;

                private static final int[] egInc = {
                        // cycle:0  1  2  3  4  5  6  7
                        /*  0 */ 0, 1, 0, 1, 0, 1, 0, 1, // rates 00..12 0 (increment by 0 or 1)
                        /*  1 */ 0, 1, 0, 1, 1, 1, 0, 1, // rates 00..12 1
                        /*  2 */ 0, 1, 1, 1, 0, 1, 1, 1, // rates 00..12 2
                        /*  3 */ 0, 1, 1, 1, 1, 1, 1, 1, // rates 00..12 3

                        /*  4 */ 1, 1, 1, 1, 1, 1, 1, 1, // rate 13 0 (increment by 1)
                        /*  5 */ 1, 1, 1, 2, 1, 1, 1, 2, // rate 13 1
                        /*  6 */ 1, 2, 1, 2, 1, 2, 1, 2, // rate 13 2
                        /*  7 */ 1, 2, 2, 2, 1, 2, 2, 2, // rate 13 3

                        /*  8 */ 2, 2, 2, 2, 2, 2, 2, 2, // rate 14 0 (increment by 2)
                        /*  9 */ 2, 2, 2, 4, 2, 2, 2, 4, // rate 14 1
                        /* 10 */ 2, 4, 2, 4, 2, 4, 2, 4, // rate 14 2
                        /* 11 */ 2, 4, 4, 4, 2, 4, 4, 4, // rate 14 3

                        /* 12 */ 4, 4, 4, 4, 4, 4, 4, 4, // rates 15 0, 15 1, 15 2, 15 3 for decay
                        /* 13 */ 8, 8, 8, 8, 8, 8, 8, 8, // rates 15 0, 15 1, 15 2, 15 3 for attack (zero time)
                        /* 14 */ 0, 0, 0, 0, 0, 0, 0, 0, // infinity rates for attack and decay(s)
                };

                /**
                 * note that there is no O(13) in this table - it's directly in the code
                 */
                private static int o(int a) {
                    return a * RATE_STEPS;
                }

                /**
                 * Envelope Generator rates (16 + 64 rates + 16 RKS)
                 */
                private static final int[] egRateSelect = {
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

                private static int o1(int a) {
                    return a * 1;
                }

                /*
                 * Envelope Generator counter shifts (16 + 64 rates + 16 RKS)
                 * <pre>
                 * rate  0,    1,    2,    3,   4,   5,   6,  7,  8,  9,  10, 11, 12, 13, 14, 15
                 * shift 12,   11,   10,   9,   8,   7,   6,  5,  4,  3,  2,  1,  0,  0,  0,  0
                 * mask  4095, 2047, 1023, 511, 255, 127, 63, 31, 15, 7,  3,  1,  0,  0,  0,  0
                 * </pre>
                 */
                private static final int[] egRateShift = {
                        // 16 infinite time rates
                        o1(0), o1(0), o1(0), o1(0), o1(0), o1(0), o1(0), o1(0),
                        o1(0), o1(0), o1(0), o1(0), o1(0), o1(0), o1(0), o1(0),
                        // rates 00-12
                        o1(12), o1(12), o1(12), o1(12),
                        o1(11), o1(11), o1(11), o1(11),
                        o1(10), o1(10), o1(10), o1(10),
                        o1(9), o1(9), o1(9), o1(9),
                        o1(8), o1(8), o1(8), o1(8),
                        o1(7), o1(7), o1(7), o1(7),
                        o1(6), o1(6), o1(6), o1(6),
                        o1(5), o1(5), o1(5), o1(5),
                        o1(4), o1(4), o1(4), o1(4),
                        o1(3), o1(3), o1(3), o1(3),
                        o1(2), o1(2), o1(2), o1(2),
                        o1(1), o1(1), o1(1), o1(1),
                        o1(0), o1(0), o1(0), o1(0),
                        // rate 13
                        o1(0), o1(0), o1(0), o1(0),
                        // rate 14
                        o1(0), o1(0), o1(0), o1(0),
                        // rate 15
                        o1(0), o1(0), o1(0), o1(0),
                        // 16 dummy rates (same as 15 3)
                        o1(0), o1(0), o1(0), o1(0), o1(0), o1(0), o1(0), o1(0),
                        o1(0), o1(0), o1(0), o1(0), o1(0), o1(0), o1(0), o1(0),
                };

                /** attack rate: AR<<2 */
                protected int ar;
                /** decay rate:  DR<<2 */
                protected int dr;
                /** release rate:RR<<2 */
                protected int rr;
                /** key scale rate */
                protected int KSR;
                /** keyScale level */
                protected int ksl;
                /** key scale rate: kcode>>KSR */
                protected int ksr;
                /** multiple: mul_tab[ML] */
                protected int mul;

                // Phase Generator

                /** frequency counter */
                protected int cnt;
                /** frequency counter step */
                protected int incR;
                /** feedback shift value */
                protected int fb;

                private int calcVolume(int lfoAm) {
                    return tll + volume + (lfoAm & amMask);
                }

                public void envGen(int egCnt) {
                    // Envelope Generator
                    switch (this.state) {
                    case EG_ATT: // attack phase
                        if ((egCnt & this.egMAr) == 0) {
                            this.volume += (~this.volume *
                                    (egInc[this.egSelAr + ((egCnt >> this.egShAr) & 7)])
                            ) >> 3;

                            if (this.volume <= MIN_ATT_INDEX) {
                                this.volume = MIN_ATT_INDEX;
                                this.state = EG_DEC;
                            }
                        }
                        break;

                    case EG_DEC: // decay phase
                        if ((egCnt & this.egMDr) == 0) {
                            this.volume += egInc[this.egSelDr + ((egCnt >> this.egShDr) & 7)];

                            if (this.volume >= this.sl)
                                this.state = EG_SUS;
                        }
                        break;

                    case EG_SUS: // sustain phase

                        // this is important behaviour:
                        // one can change percusive/non-percussive modes on the fly and
                        // the chips will remain in sustain phase - verified on real YM3812

                        if (this.egType != 0) { // non-percussive mode
                            // do nothing
                        } else { // percussive mode
                            // during sustain phase chips adds Release Rate (in percussive mode)
                            if ((egCnt & this.egMRr) == 0) {
                                this.volume += egInc[this.egSelRr + ((egCnt >> this.egShRr) & 7)];

                                if (this.volume >= MAX_ATT_INDEX)
                                    this.volume = MAX_ATT_INDEX;
                            }
                            // else do nothing in sustain phase
                        }
                        break;

                    case EG_REL: // release phase
                        if ((egCnt & this.egMRr) == 0) {
                            this.volume += egInc[this.egSelRr + ((egCnt >> this.egShRr) & 7)];

                            if (this.volume >= MAX_ATT_INDEX) {
                                this.volume = MAX_ATT_INDEX;
                                this.state = EG_OFF;
                            }
                        }
                        break;

                    default:
                        break;
                    }
                }

                private static int calcOp(int phase, int env, int pm, int wave_tab) {
                    int p = (env << 4) + sinTab[wave_tab + ((((phase & ~FREQ_MASK) + (pm << 16)) >> FREQ_SH) & SIN_MASK)];

                    if (p >= TL_TAB_LEN)
                        return 0;
                    return tlTab[p];
                }

                private static int calcOp1(int phase, int env, int pm, int wave_tab) {
                    int p = (env << 4) + sinTab[wave_tab + ((((phase & ~FREQ_MASK) + pm) >> FREQ_SH) & SIN_MASK)];

                    if (p >= TL_TAB_LEN)
                        return 0;
                    return tlTab[p];
                }

                public void calc1(int lfoAm) {
                    int env = this.calcVolume(lfoAm);
                    int out = this.op1Out[0] + this.op1Out[1];
                    this.op1Out[0] = this.op1Out[1];
                    this.op1Out[1] = 0;
                    if (env < ENV_QUIET) {
                        if (this.fb == 0)
                            out = 0;
                        this.op1Out[1] = calcOp1(this.cnt, env, (out << this.fb), this.waveTable);
                    }
                    //this.connect += this.op1_out[1];
                    this.connect.setValue(this.connect.getValue() + this.op1Out[1]);
//logger.log(Level.TRACE, "out0=%5i vol0=%4i ".formatted(this.op1_out[1], env ));
                }

                public void calc(int lfoAm, int phaseModulation) {
                    int env = this.calcVolume(lfoAm);
                    if (env < ENV_QUIET) {
                        this.connect.setValue(this.connect.getValue() + calcOp(this.cnt, env, phaseModulation, this.waveTable));
                    }
//logger.log(Level.TRACE, "out1=%5i vol1=%4i".formatted(op_calc(this.Cnt, env, this.phase_modulation, this.wavetable), env));
                }

                public void calcRhythm(int lfoAm) {
                    int env = this.calcVolume(lfoAm);

                    int out = this.op1Out[0] + this.op1Out[1];
                    this.op1Out[0] = this.op1Out[1];

                    this.op1Out[1] = 0;
                    if (env < ENV_QUIET) {
                        if (this.fb == 0)
                            out = 0;
                        this.op1Out[1] = calcOp1(this.cnt, env, (out << this.fb), this.waveTable);
                    }
                }

                public void advance(int blockFNum, int lfoPm, int[] fnTab) {
                    // Phase Generator
                    if (this.vib != 0) {
                        int fnumLfo = (blockFNum & 0x0380) >> 7;

                        int lfoFnTableIndexOffset = lfoPmTable[lfoPm + 16 * fnumLfo];

                        if (lfoFnTableIndexOffset != 0) { // LFO phase modulation active
                            blockFNum += lfoFnTableIndexOffset;
                            int block = (blockFNum & 0x1c00) >> 10;
                            this.cnt += (fnTab[blockFNum & 0x03ff] >> (7 - block)) * this.mul;
                        } else { // LFO phase modulation  = zero
                            this.cnt += this.incR;
                        }
                    } else { // LFO phase modulation disabled for this Operator
                        this.cnt += this.incR;
                    }
                }

                public void setArDr(int v) {
                    this.ar = (v >> 4) != 0 ? 16 + ((v >> 4) << 2) : 0;

                    if ((this.ar + this.ksr) < 16 + 60) { // verified on real YMF262 - all 15 x rates take "zero" time
                        this.egShAr = egRateShift[this.ar + this.ksr];
                        this.egMAr = (1 << this.egShAr) - 1;
                        this.egSelAr = egRateSelect[this.ar + this.ksr];
                    } else {
                        this.egShAr = 0;
                        this.egMAr = (1 << this.egShAr) - 1;
                        this.egSelAr = 13 * RATE_STEPS;
                    }

                    this.dr = (v & 0x0f) != 0 ? 16 + ((v & 0x0f) << 2) : 0;
                    this.egShDr = egRateShift[this.dr + this.ksr];
                    this.egMDr = (1 << this.egShDr) - 1;
                    this.egSelDr = egRateSelect[this.dr + this.ksr];
                }

                public void setSrRr(int v) {
                    this.sl = slTab[v >> 4];

                    this.rr = (v & 0x0f) != 0 ? 16 + ((v & 0x0f) << 2) : 0;
                    this.egShRr = egRateShift[this.rr + this.ksr];
                    this.egMRr = (1 << this.egShRr) - 1;
                    this.egSelRr = egRateSelect[this.rr + this.ksr];
                }

                public void setMul(int v) {
                    this.mul = mulTab[v & 0x0f];
                    this.KSR = (v & 0x10) != 0 ? 0 : 2;
                    this.egType = v & 0x20;
                    this.vib = v & 0x40;
                    this.amMask = (v & 0x80) != 0 ? ~0 : 0;
                }

                public void setKslTl(int v) {
                    this.ksl = Channel.kslShift[v >> 6];
                    this.tl = (v & 0x3f) << (ENV_BITS - 1 - 7); // 7 bits TL (bit 6 = always 0)
                }

                public static class Connect {
                    public MameOpl3 opl3 = null;
                    public int index = 0;

                    public void setValue(int value) {
                        if (index < 18) {
                            opl3.chanOut[index] = value;
                        } else if (index == 18) {
                            opl3.phaseModulation = value;
                        } else {
                            opl3.phaseModulation2 = value;
                        }
                    }

                    public int getValue() {
                        if (index < 18) {
                            return opl3.chanOut[index];
                        } else if (index == 18) {
                            return opl3.phaseModulation;
                        } else {
                            return opl3.phaseModulation2;
                        }
                    }
                }

                /** slot output pointer  */
                protected final Connect connect = new Connect();
                /** slot1 output for feedback  */
                protected final int[] op1Out = new int[2];
                /** connection (algorithm) type  */
                protected int con;

                // Envelope Generator
                /** percussive/non-percussive mode  */
                protected int egType;
                /** phase type  */
                protected int state;
                /** total level: TL << 2  */
                protected int tl;
                /** adjusted now TL  */
                protected int tll;
                /** envelope counter  */
                protected int volume;
                /** sustain level: sl_tab[SL]  */
                protected int sl;

                /** (attack state)  */
                protected int egMAr;
                /** (attack state)  */
                protected int egShAr;
                /** (attack state)  */
                protected int egSelAr;
                /** (decay state)  */
                protected int egMDr;
                /** (decay state)  */
                protected int egShDr;
                /** (decay state)  */
                protected int egSelDr;
                /** (release state)  */
                protected int egMRr;
                /** (release state)  */
                protected int egShRr;
                /** (release state)  */
                protected int egSelRr;

                /** 0 = KEY OFF, >0 = KEY ON */
                protected int key;

                // LFO
                /** LFO Amplitude Modulation enable mask  */
                protected int amMask;
                /** LFO Phase Modulation enable flag (active high) */
                protected int vib;

                /** waveForm select  */
                protected int waveformNumber;
                protected int waveTable;

//                /** speedup: pump up the struct size to power of 2 */
//                public int[] reserved = new int[128 - 100];

                private void keyOn(int keySet) {
                    if (this.key == 0) {
                        // restart Phase Generator
                        this.cnt = 0;
                        // phase -> Attack
                        this.state = EG_ATT;
                    }
                    this.key |= keySet;
                }

                private void keyOff(int keyClr) {
                    if (this.key != 0) {
                        this.key &= keyClr;

                        if (this.key == 0) {
                            // phase -> Release
                            if (this.state > EG_REL)
                                this.state = EG_REL;
                        }
                    }
                }

                /** update phase increment counter of Operator (also update the EG rates if necessary) */
                private void calcFc(int fc, int kCode) {
                    // (frequency) phase increment counter
                    this.incR = fc * this.mul;
                    int ksr = kCode >> this.KSR;

                    if (this.ksr != ksr) {
                        this.ksr = ksr;

                        // calculate envelope generator rates
                        if ((this.ar + this.ksr) < 16 + 60) {
                            this.egShAr = egRateShift[this.ar + this.ksr];
                            this.egMAr = (1 << this.egShAr) - 1;
                            this.egSelAr = egRateSelect[this.ar + this.ksr];
                        } else {
                            this.egShAr = 0;
                            this.egMAr = (1 << this.egShAr) - 1;
                            this.egSelAr = 13 * RATE_STEPS;
                        }
                        this.egShDr = egRateShift[this.dr + this.ksr];
                        this.egMDr = (1 << this.egShDr) - 1;
                        this.egSelDr = egRateSelect[this.dr + this.ksr];
                        this.egShRr = egRateShift[this.rr + this.ksr];
                        this.egMRr = (1 << this.egShRr) - 1;
                        this.egSelRr = egRateSelect[this.rr + this.ksr];
                    }
                }
            }

            // key scale level

            /** 0.1875 is bit 0 weight of the envelope counter (volume) expressed in the 'decibel' scale  */
            private static final double DV = 0.1875 / 2.0;

            /** table is 3dB/octave , DV converts this into 6dB/octave  */
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

            /** 0 / 3.0 / 1.5 / 6.0 dB/OCT  */
            private static final int[] kslShift = new int[] {31, 1, 2, 0};

            protected final Slot[] slots = {new Slot(), new Slot()};

            /** block+fNum */
            protected int blockFNum;
            /** Freq. Increment base */
            protected int fc;
            /** KeyScaleLevel Base step */
            protected int kslBase;
            /** key code (for key scaling) */
            protected int kCode;

            // there are 12 2-Operator channels which can be combined in pairs
            // to form six 4-Operator channel, they are:
            //   0 and 3,
            //   1 and 4,
            //   2 and 5,
            //   9 and 12,
            //  10 and 13,
            //  11 and 14

            /**
             * set to 1 if this channel forms up a 4op channel
             * with another channel(only used by first of Pair of channels, ie 0,1,2 and 9,10,11)
             */
            protected int extended;
            protected int muted;

            //speedup:pump up the struct size to power of 2
//            public int[] reserved = new int[512 - 272];

            private void update() {
                // update channel passed as a parameter and a channel at CH+=3;
                if (this.extended != 0) { // we've just switched to combined 4 Operator mode
                } else { // we've just switched to normal 2 Operator mode
                }
            }
        }

        private static final int FINAL_SH = 0;

        /** 16.16 fixed point (frequency calculations)  */
        private static final int FREQ_SH = 16;
        /** 16.16 fixed point (EG timing)  */
        private static final int EG_SH = 16;
        /**  8.24 fixed point (LFO calculations)  */
        private static final int LFO_SH = 24;

        private static final int FREQ_MASK = (1 << FREQ_SH) - 1;

        // envelope output entries
        private static final int ENV_BITS = 10;
        private static final int ENV_LEN = 1 << ENV_BITS;
        private static final double ENV_STEP = 128.0 / ENV_LEN;

        private static final int MAX_ATT_INDEX = (1 << (ENV_BITS - 1)) - 1; // 511
        private static final int MIN_ATT_INDEX = 0;

        // sin-wave entries
        private static final int SIN_BITS = 10;
        private static final int SIN_LEN = 1 << SIN_BITS;
        private static final int SIN_MASK = SIN_LEN - 1;

        private static final int TL_RES_LEN = 256; // 8 bits addressing (real chips)

        // register number to channel number , slot offset
        private static final int SLOT1 = 0;
        private static final int SLOT2 = 1;

        /** mapping of register number (offset) to slot number used by the emulator  */
        private static final int[] slotArray = {
                0, 2, 4, 1, 3, 5, -1, -1,
                6, 8, 10, 7, 9, 11, -1, -1,
                12, 14, 16, 13, 15, 17, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1
        };

        private static final int ML = 2;
        /** multiple table  */
        private static final int[] mulTab = {
                // 1/2, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 12, 12, 15, 15
                (int) (0.50 * ML), (int) (1.00 * ML), (int) (2.00 * ML), (int) (3.00 * ML), (int) (4.00 * ML), (int) (5.00 * ML), (int) (6.00 * ML), (int) (7.00 * ML),
                (int) (8.00 * ML), (int) (9.00 * ML), (int) (10.00 * ML), (int) (10.00 * ML), (int) (12.00 * ML), (int) (12.00 * ML), (int) (15.00 * ML), (int) (15.00 * ML)
        };

        /*
         * TL_TAB_LEN is calculated as:
         *
         *  (12+1)=13 - sinus amplitude bits     (Y axis)
         *  additional 1: to compensate for calculations of negative part of waveForm
         *  (if we don't add it then the greatest possible _negative_ value would be -2
         *  and we really need -1 for waveForm //#7)
         *  2  - sinus sign bit           (Y axis)
         *  TL_RES_LEN - sinus resolution (X axis)
         */
        private static final int TL_TAB_LEN = 13 * 2 * TL_RES_LEN;
        private static final int[] tlTab = new int[TL_TAB_LEN];

        private static final int ENV_QUIET = TL_TAB_LEN >> 4;

        /**
         * sin waveForm table in 'decibel' scale
         * there are eight waveforms on Opl3 chips
         */
        private static final int[] sinTab = new int[SIN_LEN * 8];

        private static final int LFO_AM_TAB_ELEMENTS = 210;

        /**
         * LFO Amplitude Modulation table (verified on real YM3812)
         * <pre>
         * 27 output levels (triangle waveForm); 1 level takes one of: 192, 256 or 448 samples
         *
         * Length: 210 elements.
         *
         * Each of the elements has to be repeated
         * exactly 64 times (on 64 consecutive samples).
         * The whole table takes: 64 * 210 = 13440 samples.
         *
         * When AM = 1 data is used directly
         * When AM = 0 data is divided by 4 before being used (losing precision is important)
         * </pre>
         */
        private static final int[] lfoAmTable = {
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

        /** LFO Phase Modulation table (verified on real YM3812) */
        private static final int[] lfoPmTable = {
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

        /** 36 operators, 8 waveforms */
        private static final int OPL3_TYPE_YMF262 = 0;

        /** Opl3 chips have 18 channels */
        protected final Channel[] channels = {
                new Channel(), new Channel(), new Channel(), new Channel(),
                new Channel(), new Channel(), new Channel(), new Channel(),
                new Channel(), new Channel(), new Channel(), new Channel(),
                new Channel(), new Channel(), new Channel(), new Channel(),
                new Channel(), new Channel()
        };

        /** channels output masks (0xffffffff = enable); 4 masks per one channel */
        protected final int[] pan = new int[18 * 4];
        /** output control values 1 per one channel (1 value contains 4 masks) */
        protected final int[] panCtrlValue = new int[18];
        /** for the 5 Rhythm Channels */
        protected final int[] muteSpc = new int[5];

        /** 18 channels */
        protected final int[] chanOut = new int[18];
        /** phase modulation input (SLOT 2) */
        protected int phaseModulation;
        /** phase modulation input (SLOT 3 in 4 Operator channels) */
        protected int phaseModulation2;

        /** Global envelope generator counter */
        protected int egCnt;
        /** Global envelope generator counter works at frequency = chipclock/288 (288=8*36) */
        protected int egTimer;
        /** step of eg_timer */
        protected int egTimerAdd;
        /** envelope generator timer overlfows every 1 sample (on real chips) */
        protected int egTimerOverflow;

        /** fnumber->increment counter */
        protected final int[] fnTab = new int[1024];

        // LFO
        protected int lfoAm;
        protected int lfoPm;
        protected int lfoAmDepth;
        protected int lfoPmDepthRange;
        protected int lfoAmCnt;
        protected int lfoAmInc;
        protected int lfoPmCnt;
        protected int lfoPmInc;

        /** 23 bit noise shift register */
        protected int noiseRng;
        /** current noise 'phase' */
        protected int noiseP;
        /** current noise period */
        protected int noiseF;

        /** Opl3 extension enable flag */
        protected int mode;

        /** Rhythm mode */
        protected int rhythm;

        /** timer counters */
        protected final int[] T = new int[2];
        /** timer enable */
        protected final int[] st = new int[2];

        /** address register */
        protected int address;
        /** status flag */
        protected int status;
        /** status mask */
        protected int statusMask;

        /** NTS (note select) */
        protected int nts;

        // external event Callback handlers

        /** TIMER handler */
        protected TimerHandler timerHandler;
        /** IRQ handler */
        protected IrqHandler irqHandler;
        /** stream update handler */
        protected UpdateHandler updateHandler;

        /** chips type */
        private final int type;
        /** master clock  (Hz) */
        private final int clock;
        /** sampling rate (Hz) */
        private final int rate;
        /** frequency base */
        private double freqBase;

        private void initChs() {
            for (Channel ch : channels) {
                ch.slots[0].connect.opl3 = this;
                ch.slots[0].connect.index = 0;
                ch.slots[1].connect.opl3 = this;
                ch.slots[1].connect.index = 0;
            }
        }

        // work table
        private Channel.Slot slot7_1() {
            return this.channels[7].slots[SLOT1];
        }

        private Channel.Slot slot7_2() {
            return this.channels[7].slots[SLOT2];
        }

        private Channel.Slot slot8_1() {
            return this.channels[8].slots[SLOT1];
        }

        private Channel.Slot slot8_2() {
            return this.channels[8].slots[SLOT2];
        }

        /** status set and IRQ handling */
        private void setStatus(int flag) {
            // set status flag masking out disabled IRQs
            this.status |= flag & this.statusMask;
            if ((this.status & 0x80) == 0) {
                if ((this.status & 0x7f) != 0) { // IRQ on
                    this.status |= 0x80;
                    // Callback user interrupt handler (IRQ is OFF to ON)
                    if (this.irqHandler != null) this.irqHandler.accept(1);
                }
            }
        }

        /** status reset and IRQ handling */
        private void resetStatus(int flag) {
            // reset status flag
            this.status &= ~flag;
            if ((this.status & 0x80) != 0) {
                if ((this.status & 0x7f) == 0) {
                    this.status &= 0x7f;
                    // Callback user interrupt handler (IRQ is ON to OFF)
                    if (this.irqHandler != null) this.irqHandler.accept(0);
                }
            }
        }

        /** IRQ mask set */
        private void setStatusMask(int flag) {
            this.statusMask = flag;
            // IRQ handling check
            setStatus(0);
            resetStatus(0);
        }

        /** advance LFO to next sample  */
        private void advanceLfo() {
            int tmp;

            // LFO
            this.lfoAmCnt += this.lfoAmInc;
            if (this.lfoAmCnt >= (LFO_AM_TAB_ELEMENTS << LFO_SH)) // lfo_am_table is 210 elements long
                this.lfoAmCnt -= (LFO_AM_TAB_ELEMENTS << LFO_SH);

            tmp = lfoAmTable[this.lfoAmCnt >>> LFO_SH];

            if (this.lfoAmDepth != 0)
                this.lfoAm = tmp;
            else
                this.lfoAm = tmp >> 2;

            this.lfoPmCnt += this.lfoPmInc;
            this.lfoPm = ((this.lfoPmCnt >> LFO_SH) & 7) | this.lfoPmDepthRange;
        }

        /** advance to next sample  */
        private void advance() {
            this.egTimer += this.egTimerAdd;

            while (this.egTimer >= this.egTimerOverflow) {
                this.egTimer -= this.egTimerOverflow;

                this.egCnt++;

                for (int i = 0; i < 9 * 2 * 2; i++) {
                    Channel ch = this.channels[i / 2];
                    Channel.Slot op = ch.slots[i & 1];
                    op.envGen(this.egCnt);
                }
            }

            for (int i = 0; i < 9 * 2 * 2; i++) {
                Channel ch = this.channels[i / 2];
                Channel.Slot op = ch.slots[i & 1];

                op.advance(ch.blockFNum, this.lfoPm, this.fnTab);
            }

            // The Noise Generator of the YM3812 is 23-bit shift register.
            // Period is equal to 2^23-2 samples.
            // Register works at sampling frequency of the chips, so output
            // can change on every sample.
            //
            // Output of the register and input to the bit 22 is:
            // bit0 XOR bit14 XOR bit15 XOR bit22
            //
            // Simply use bit 22 as the noise output.

            this.noiseP += this.noiseF;
            int i = this.noiseP >> FREQ_SH; // number of events (shifts of the shift register)
            this.noiseP &= FREQ_MASK;
            while (i != 0) {
//                int j = ((this.noise_rng) ^ (this.noise_rng >> 14) ^ (this.noise_rng >> 15) ^ (this.noise_rng >> 22)) & 1;
//                this.noise_rng = (j << 22) | (this.noise_rng >> 1);

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

        /**
         * calculate output of a standard 2 Operator channel
         * (or 1st part of a 4-Op channel)
         */
        private void calcChannel(Channel ch) {
            if (ch.muted != 0) return;

            this.phaseModulation = 0;
            this.phaseModulation2 = 0;

            // slot 1
            Channel.Slot slot = ch.slots[SLOT1];
            slot.calc1(this.lfoAm);

            // slot 2
            slot = ch.slots[SLOT2];
            slot.calc(this.lfoAm, this.phaseModulation);
        }

        /** calculate output of a 2nd part of 4-Op channel */
        private void calcChannelExt(Channel ch) {
            if (ch.muted != 0)
                return;

            this.phaseModulation = 0;

            // slot 1
            Channel.Slot slot = ch.slots[SLOT1];
            int env = slot.calcVolume(this.lfoAm);
            if (env < ENV_QUIET) {
                slot.connect.setValue(slot.connect.getValue() + Channel.Slot.calcOp(slot.cnt, env, this.phaseModulation2, slot.waveTable));
            }
            // slot 2
            slot = ch.slots[SLOT2];
            env = slot.calcVolume(this.lfoAm);
            if (env < ENV_QUIET) {
                slot.connect.setValue(slot.connect.getValue() + Channel.Slot.calcOp(slot.cnt, env, this.phaseModulation, slot.waveTable));
            }
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

        /** calculate rhythm  */
        private void calcChannelRhythm(Channel[] ch, int num, int noise) {
            int[] chanOut = this.chanOut;

            // Bass Drum (verified on real YM3812):
            // - depends on the channel 6 'connect' register:
            //     when connect = 0 it works the same as in normal (non-rhythm) mode (op1.op2.out)
            //     when connect = 1 _only_ Operator 2 is present on output (op2.out), Operator 1 is ignored
            // - output sample always is multiplied by 2

            this.phaseModulation = 0;

            // slot 1
            Channel.Slot slot = ch[6 + num].slots[SLOT1];
            slot.calcRhythm(this.lfoAm);
            if (slot.con == 0)
                this.phaseModulation = slot.op1Out[0];

            // slot 2
            slot = ch[6 + num].slots[SLOT2];
            int env = slot.calcVolume(this.lfoAm);
            if (env < ENV_QUIET && this.muteSpc[0] == 0)
                chanOut[6] += Channel.Slot.calcOp(slot.cnt, env, this.phaseModulation, slot.waveTable) * 2;

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
            env = slot7_1().calcVolume(this.lfoAm);
            if (env < ENV_QUIET && this.muteSpc[4] == 0) {

                // high hat phase generation:
                //  phase = d0 or 234 (based on frequency only)
                //  phase = 34 or 2d0 (based on noise)

                // base frequency derived from Operator 1 in channel 7
                int bit7 = ((slot7_1().cnt >> FREQ_SH) >> 7) & 1;
                int bit3 = ((slot7_1().cnt >> FREQ_SH) >> 3) & 1;
                int bit2 = ((slot7_1().cnt >> FREQ_SH) >> 2) & 1;

                int res1 = (bit2 ^ bit7) | bit3;

                // when res1 = 0 phase = 0x000 | 0xd0;
                // when res1 = 1 phase = 0x200 | (0xd0>>2);
                int phase = res1 != 0 ? (0x200 | (0xd0 >> 2)) : 0xd0;

                // enable gate based on frequency of Operator 2 in channel 8
                int bit5e = ((slot8_2().cnt >> FREQ_SH) >> 5) & 1;
                int bit3e = ((slot8_2().cnt >> FREQ_SH) >> 3) & 1;

                int res2 = bit3e ^ bit5e;

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

                chanOut[7] += Channel.Slot.calcOp(phase << FREQ_SH, env, 0, slot7_1().waveTable) * 2;
            }

            // Snare Drum (verified on real YM3812)
            env = slot7_2().calcVolume(this.lfoAm);
            if (env < ENV_QUIET && this.muteSpc[1] == 0) {
                // base frequency derived from Operator 1 in channel 7
                int bit8 = ((slot7_1().cnt >> FREQ_SH) >> 8) & 1;

                // when bit8 = 0 phase = 0x100;
                // when bit8 = 1 phase = 0x200;
                int phase = bit8 != 0 ? 0x200 : 0x100;

                // Noise bit XOR'es phase by 0x100
                // when noisebit = 0 pass the phase from calculation above
                // when noisebit = 1 phase ^= 0x100;
                // in other words: phase ^= (noisebit<<8);
                if (noise != 0)
                    phase ^= 0x100;

                chanOut[7] += Channel.Slot.calcOp(phase << FREQ_SH, env, 0, slot7_2().waveTable) * 2;
            }

            // Tom Tom (verified on real YM3812)
            env = slot8_1().calcVolume(this.lfoAm);
            if (env < ENV_QUIET && this.muteSpc[2] == 0)
                chanOut[8] += Channel.Slot.calcOp(slot8_1().cnt, env, 0, slot8_1().waveTable) * 2;

            // Top Cymbal (verified on real YM3812)
            env = slot8_2().calcVolume(this.lfoAm);
            if (env < ENV_QUIET && this.muteSpc[3] == 0) {
                // base frequency derived from Operator 1 in channel 7
                int bit7 = ((slot7_1().cnt >> FREQ_SH) >> 7) & 1;
                int bit3 = ((slot7_1().cnt >> FREQ_SH) >> 3) & 1;
                int bit2 = ((slot7_1().cnt >> FREQ_SH) >> 2) & 1;
                int res1 = (bit2 ^ bit7) | bit3;

                // when res1 = 0 phase = 0x000 | 0x100;
                // when res1 = 1 phase = 0x200 | 0x100;
                int phase = res1 != 0 ? 0x300 : 0x100;

                // enable gate based on frequency of Operator 2 in channel 8
                int bit5e = ((slot8_2().cnt >> FREQ_SH) >> 5) & 1;
                int bit3e = ((slot8_2().cnt >> FREQ_SH) >> 3) & 1;

                int res2 = bit3e ^ bit5e;
                // when res2 = 0 pass the phase from calculation above (res1);
                // when res2 = 1 phase = 0x200 | 0x100;
                if (res2 != 0)
                    phase = 0x300;

                chanOut[8] += Channel.Slot.calcOp(phase << FREQ_SH, env, 0, slot8_2().waveTable) * 2;
            }
        }

        // generic table initialize
        static {

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
                n <<= 1; // 12 bits here (as in real chips)
                tlTab[x * 2 + 0] = n;
                tlTab[x * 2 + 1] = ~tlTab[x * 2 + 0]; // this *is* different from OPL2 (verified on real YMF262)

                for (int i = 1; i < 13; i++) {
                    tlTab[x * 2 + 0 + i * 2 * TL_RES_LEN] = tlTab[x * 2 + 0] >> i;
                    tlTab[x * 2 + 1 + i * 2 * TL_RES_LEN] = ~tlTab[x * 2 + 0 + i * 2 * TL_RES_LEN]; // this *is* different from OPL2 (verified on real YMF262)
                }
//logger.log(Level.TRACE, "tl %04i".formatted(x*2));
//for (i=0; i<13; i++)
// logger.log(Level.TRACE, ", [%02i] %5i".formatted(i*2, tl_tab[ x*2 +0 + i*2*TL_RES_LEN ] )); // positive
//logger.log(Level.TRACE, "\n");

//logger.log(Level.TRACE, "tl %04i".formatted(x*2));
//for (i=0; i<13; i++)
// logger.log(Level.TRACE, ", [%02i] %5i".formatted(i*2, tl_tab[ x*2 +1 + i*2*TL_RES_LEN ] )); /* negative
//logger.log(Level.TRACE, "\n");
            }

            for (int i = 0; i < SIN_LEN; i++) {
                // non-standard sinus
                double m = Math.sin(((i * 2) + 1) * Math.PI / SIN_LEN); // checked against the real chips

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

//logger.log(Level.TRACE, YMF262.C: sin [%4i (hex=%03x)]= %4i (tl_tab value=%5i)".formatted(i, i, sin_tab[i], tl_tab[sin_tab[i]]));
            }

            for (int i = 0; i < SIN_LEN; i++) {
                // these 'pictures' represent _two_ cycles
                // waveForm 1:  __      __
                //             /  \____/  \____
                // output only first half of the sinus waveForm (positive one)

                if ((i & (1 << (SIN_BITS - 1))) != 0)
                    sinTab[1 * SIN_LEN + i] = TL_TAB_LEN;
                else
                    sinTab[1 * SIN_LEN + i] = sinTab[i];

                // waveForm 2:  __  __  __  __
                //             /  \/  \/  \/  \
                // abs(sin)

                sinTab[2 * SIN_LEN + i] = sinTab[i & (SIN_MASK >> 1)];

                // waveForm 3:  _   _   _   _
                //             / |_/ |_/ |_/ |_
                // abs(output only first quarter of the sinus waveForm)

                if ((i & (1 << (SIN_BITS - 2))) != 0)
                    sinTab[3 * SIN_LEN + i] = TL_TAB_LEN;
                else
                    sinTab[3 * SIN_LEN + i] = sinTab[i & (SIN_MASK >> 2)];

                // waveForm 4:
                //             /\  ____/\  ____
                //               \/      \/
                // output whole sinus waveForm in half the cycle(step=2) and output 0 on the other half of cycle

                if ((i & (1 << (SIN_BITS - 1))) != 0)
                    sinTab[4 * SIN_LEN + i] = TL_TAB_LEN;
                else
                    sinTab[4 * SIN_LEN + i] = sinTab[i * 2];

                // waveForm 5:
                //             /\/\____/\/\____
                //
                // output abs(whole sinus) waveForm in half the cycle(step=2) and output 0 on the other half of cycle

                if ((i & (1 << (SIN_BITS - 1))) != 0)
                    sinTab[5 * SIN_LEN + i] = TL_TAB_LEN;
                else
                    sinTab[5 * SIN_LEN + i] = sinTab[(i * 2) & (SIN_MASK >> 1)];

                // waveForm 6: ____    ____
                //
                //                 ____    ____
                // output maximum in half the cycle and output minimum on the other half of cycle

                if ((i & (1 << (SIN_BITS - 1))) != 0)
                    sinTab[6 * SIN_LEN + i] = 1; // negative
                else
                    sinTab[6 * SIN_LEN + i] = 0; // positive

                // waveForm 7:
                //             |\____  |\____
                //                   \|      \|
                // output sawtooth waveForm

                int x;
                if ((i & (1 << (SIN_BITS - 1))) != 0)
                    x = ((SIN_LEN - 1) - i) * 16 + 1; // negative: from 8177 to 1
                else
                    x = i * 16; //positive: from 0 to 8176 */

                if (x > TL_TAB_LEN)
                    x = TL_TAB_LEN; // clip to the allowed range

                sinTab[7 * SIN_LEN + i] = x;

//logger.log(Level.TRACE, "sin1[%4i]= %4i (tl_tab value=%5i)".formatted(i, sin_tab[1*SIN_LEN+i], tl_tab[sin_tab[1*SIN_LEN+i]]));
//logger.log(Level.TRACE, "sin2[%4i]= %4i (tl_tab value=%5i)".formatted(i, sin_tab[2*SIN_LEN+i], tl_tab[sin_tab[2*SIN_LEN+i]]));
//logger.log(Level.TRACE, "sin3[%4i]= %4i (tl_tab value=%5i)".formatted(i, sin_tab[3*SIN_LEN+i], tl_tab[sin_tab[3*SIN_LEN+i]]));
//logger.log(Level.TRACE, "sin4[%4i]= %4i (tl_tab value=%5i)".formatted(i, sin_tab[4*SIN_LEN+i], tl_tab[sin_tab[4*SIN_LEN+i]]));
//logger.log(Level.TRACE, "sin5[%4i]= %4i (tl_tab value=%5i)".formatted(i, sin_tab[5*SIN_LEN+i], tl_tab[sin_tab[5*SIN_LEN+i]]));
//logger.log(Level.TRACE, "sin6[%4i]= %4i (tl_tab value=%5i)".formatted(i, sin_tab[6*SIN_LEN+i], tl_tab[sin_tab[6*SIN_LEN+i]]));
//logger.log(Level.TRACE, "sin7[%4i]= %4i (tl_tab value=%5i)".formatted(i, sin_tab[7*SIN_LEN+i], tl_tab[sin_tab[7*SIN_LEN+i]]));
            }
//logger.log(Level.TRACE, "ENV_QUIET= %08x (dec*8=%i)".formatted(ENV_QUIET, ENV_QUIET * 8));
        }

        private void init() {
            initChs();

            // frequency base
            this.freqBase = (this.rate) != 0 ? ((double) this.clock / (8.0 * 36)) / this.rate : 0;

//logger.log(Level.TRACE, "freqBase=%f".formatted(this.freqBase));

            // Timer base time
            //this.TimerBase = attotime_mul(ATTOTIME_IN_HZ(this.clock), 8*36);

            // make fnumber . increment counter table
            for (int i = 0; i < 1024; i++) {
                // opn phase increment counter = 20bit
                this.fnTab[i] = (int) ((double) i * 64 * this.freqBase * (1 << (FREQ_SH - 10))); // -10 because chips works with 10.10 fixed point, while we use 16.16
//logger.log(Level.TRACE, "fn_tab[%4i] = %08x (dec=%8i)".formatted(i, this.fn_tab[i] >> 6, this.fn_tab[i] >> 6));
            }

            // Amplitude modulation: 27 output levels (triangle waveForm); 1 level takes one of: 192, 256 or 448 samples
            // One entry from LFO_AM_TABLE lasts for 64 samples
            this.lfoAmInc = (int) ((1.0 / 64.0) * (1 << LFO_SH) * this.freqBase);

            // Vibrato: 8 output levels (triangle waveForm); 1 level takes 1024 samples
            this.lfoPmInc = (int) ((1.0 / 1024.0) * (1 << LFO_SH) * this.freqBase);

//logger.log(Level.TRACE, "this.lfo_am_inc = %8x ; this.lfo_pm_inc = %8x".formatted(this.lfo_am_inc, this.lfo_pm_inc));

            // Noise generator: a step takes 1 sample
            this.noiseF = (int) ((1.0 / 1.0) * (1 << FREQ_SH) * this.freqBase);

            this.egTimerAdd = (int) ((1 << EG_SH) * this.freqBase);
            this.egTimerOverflow = 1 * (1 << EG_SH);
//logger.log(Level.TRACE, "eg_timer_add=%8x eg_timer_overflow=%8x".formatted(this.eg_timer_add, this.eg_timer_overflow));
        }

        @Override
        public void reset() {
            this.egTimer = 0;
            this.egCnt = 0;

            this.noiseRng = 1; // noise shift register
            this.nts = 0; // note split
            resetStatus(0x60);

            // reset with register write
            writeReg(0x01, 0); // test register
            writeReg(0x02, 0); // Timer1
            writeReg(0x03, 0); // Timer2
            writeReg(0x04, 0); // IRQ mask clear

            // FIX IT registers 101, 104 and 105

            // FIX IT (dont change CH.D, CH.C, CH.B and CH.A in C0-C8 registers)
            for (int c = 0xff; c >= 0x20; c--)
                writeReg(c, 0);
            // FIX IT (dont change CH.D, CH.C, CH.B and CH.A in C0-C8 registers)
            for (int c = 0x1ff; c >= 0x120; c--)
                writeReg(c, 0);

            // reset Operator parameters
            for (int c = 0; c < 9 * 2; c++) {
                Channel ch = this.channels[c];
                for (int s = 0; s < 2; s++) {
                    ch.slots[s].state = Channel.Slot.EG_OFF;
                    ch.slots[s].volume = MAX_ATT_INDEX;
                }
            }
        }

        /**
         * Create one of virtual YMF262
         *
         * @param clock is chips clock in Hz
         * @param rate  is sampling rate
         */
        private MameOpl3(int clock, int rate, int type) {
            this.type = type;
            this.clock = clock;
            this.rate = rate;

            // init Global tables
            this.init();

            // reset chips
            this.reset();
        }

        /**
         * Destroy one of virtual YMF262
         */
        @Override
        public void stop() {
        }

        /**
         * Optional handlers
         */
        private void setTimerHandler(TimerHandler timerHandler) {
            this.timerHandler = timerHandler;
        }

        private void setIRQHandler(IrqHandler irqHandler) {
            this.irqHandler = irqHandler;
        }

        private void setUpdateHandler(UpdateHandler updateHandler) {
            this.updateHandler = updateHandler;
        }

        /** YMF262 I/O interface */
        @Override
        public int write(int a, int v) {
            // data bus is 8 bits
            v &= 0xff;

            switch (a & 3) {
            case 0: // address port 0 (register set //#1)
                this.address = v;
                break;

            case 1: // data port - ignore A1
            case 3: // data port - ignore A1
                if (this.updateHandler != null) this.updateHandler.run(/* , 0 */);
                writeReg(this.address, v);
                break;

            case 2: // address port 1 (register set //#2)

                // verified on real YMF262:
                // in Opl3 mode:
                //   address line A1 is stored during *address* write and ignored during *data* write.
                //
                // in OPL2 mode:
                //   register set#2 writes go to register set#1 (ignoring A1)
                //   verified on registers from set#2: 0x01, 0x04, 0x20-0xef
                //   The only exception is register 0x05.

                if ((this.mode & 1) != 0) {
                    // Opl3 mode
                    this.address = v | 0x100;
                } else {
                    // in OPL2 mode the only accessible in set //#2 is register 0x05
                    if (v == 5)
                        this.address = v | 0x100;
                    else
                        this.address = v; // verified range: 0x01, 0x04, 0x20-0xef(set //#2 becomes set //#1 in opl2 mode)
                }
                break;
            }

            return this.status >> 7;
        }

        @Override
        public int read(int a) {
            // Note on status register:
            //
            // YM3526(OPL) and YM3812(OPL2) return bit2 and bit1 in HIGH state
            //
            // YMF262(Opl3) always returns bit2 and bit1 in LOW state
            // which can be used to identify the chips
            //
            // YMF278(OPL4) returns bit2 in LOW and bit1 in HIGH state ??? info from manual - not verified

            if (a == 0) {
                // status port
                return this.status;
            }

            return 0x00; // verified on real YMF262
        }

        private int timerOver(int c) {
            if (c != 0) { // Timer B
                setStatus(0x20);
            } else { // Timer A
                setStatus(0x40);
            }
            // reload timer
            //if (this.timer_handler) (this.timer_handler)(this.TimerParam,c,attotime_mul(this.TimerBase, this.T[c]));
            return this.status >> 7;
        }

        /**
         * set multi,am,vib,EG-TYP,KSR,mul
         */
        private void setMul(int s, int v) {
            Channel ch = this.channels[s / 2];
            Channel ch3 = ((s / 2 - 3) >= 0 && (s / 2 - 3) < this.channels.length) ? this.channels[s / 2 - 3] : null;
            Channel.Slot slot = ch.slots[s & 1];

            slot.setMul(v);

            if ((this.mode & 1) != 0) {
                int chanNo = s / 2;

                // in Opl3 mode
                // DO THIS:
                //  if this is one of the slots of 1st channel forming up a 4-Op channel
                //   do normal operation
                //  else normal 2 Operator function
                // OR THIS:
                //  if this is one of the slots of 2nd channel forming up a 4-Op channel
                //   update it using channel data of 1st channel of a Pair
                //  else normal 2 Operator function
                switch (chanNo) {
                case 0:
                case 1:
                case 2:
                case 9:
                case 10:
                case 11:
                    if (ch.extended != 0) {
                        // normal
                        slot.calcFc(ch.fc, ch.kCode);
                    } else {
                        // normal
                        slot.calcFc(ch.fc, ch.kCode);
                    }
                    break;
                case 3:
                case 4:
                case 5:
                case 12:
                case 13:
                case 14:
                    if (ch3.extended != 0) {
                        // update this slot using frequency data for 1st channel of a Pair
                        slot.calcFc(ch3.fc, ch3.kCode);
                    } else {
                        // normal
                        slot.calcFc(ch.fc, ch.kCode);
                    }
                    break;
                default:
                    // normal
                    slot.calcFc(ch.fc, ch.kCode);
                    break;
                }
            } else {
                // in OPL2 mode
                slot.calcFc(ch.fc, ch.kCode);
            }
        }

        /** set ksl & tl */
        private void setKslTl(int s, int v) {
            Channel ch = this.channels[s / 2];
            //Channel ch3 = this.P_CH[s / 2 - 3];
            Channel ch3 = ((s / 2 - 3) >= 0 && (s / 2 - 3) < this.channels.length) ? this.channels[s / 2 - 3] : null;
            Channel.Slot slot = ch.slots[s & 1];

            slot.setKslTl(v);

            if ((this.mode & 1) != 0) {
                int chan_no = s / 2;

                // in Opl3 mode
                // DO THIS:
                //  if this is one of the slots of 1st channel forming up a 4-Op channel
                //   do normal operation
                //  else normal 2 Operator function
                // OR THIS:
                //  if this is one of the slots of 2nd channel forming up a 4-Op channel
                //   update it using channel data of 1st channel of a Pair
                //  else normal 2 Operator function
                switch (chan_no) {
                case 0:
                case 1:
                case 2:
                case 9:
                case 10:
                case 11:
                    if (ch.extended != 0) {
                        // normal
                        slot.tll = slot.tl + (ch.kslBase >> slot.ksl);
                    } else {
                        // normal
                        slot.tll = slot.tl + (ch.kslBase >> slot.ksl);
                    }
                    break;
                case 3:
                case 4:
                case 5:
                case 12:
                case 13:
                case 14:
                    if (ch3.extended != 0) {
                        // update this slot using frequency data for 1st channel of a Pair
                        slot.tll = slot.tl + (ch3.kslBase >> slot.ksl);
                    } else {
                        // normal
                        slot.tll = slot.tl + (ch.kslBase >> slot.ksl);
                    }
                    break;
                default:
                    // normal
                    slot.tll = slot.tl + (ch.kslBase >> slot.ksl);
                    break;
                }
            } else {
                // in OPL2 mode
                slot.tll = slot.tl + (ch.kslBase >> slot.ksl);
            }
        }

        /** set attack rate & decay rate */
        private void setArDr(int s, int v) {
            Channel ch = this.channels[s / 2];
            Channel.Slot slot = ch.slots[s & 1];

            slot.setArDr(v);
        }

        /** set sustain level & release rate */
        private void setSlRr(int s, int v) {
            Channel ch = this.channels[s / 2];
            Channel.Slot slot = ch.slots[s & 1];

            slot.setSrRr(v);
        }

        /** write a value v to register r on OPL chips */
        private void writeReg(int r, int v) {
            Channel ch;
            Channel chP3;
            Channel chM3;
            int[] chanOut = this.chanOut;
            int chOffset = 0;
            int slot;
            int blockFNum;

            if ((r & 0x100) != 0) {
                switch (r) {
                case 0x101: // test register
                    return;

                case 0x104: { // 6 channels enable
                    int prev;

                    ch = this.channels[0]; // channel 0
                    prev = ch.extended;
                    ch.extended = (v >> 0) & 1;
                    if (prev != ch.extended)
                        ch.update();
                    // channel 1
                    ch = this.channels[1];
                    prev = ch.extended;
                    ch.extended = (v >> 1) & 1;
                    if (prev != ch.extended)
                        ch.update();
                    // channel 2
                    ch = this.channels[2];
                    prev = ch.extended;
                    ch.extended = (v >> 2) & 1;
                    if (prev != ch.extended)
                        ch.update();


                    ch = this.channels[9]; // channel 9
                    prev = ch.extended;
                    ch.extended = (v >> 3) & 1;
                    if (prev != ch.extended)
                        ch.update();
                    // channel 10
                    ch = this.channels[10];
                    prev = ch.extended;
                    ch.extended = (v >> 4) & 1;
                    if (prev != ch.extended)
                        ch.update();
                    // channel 11
                    ch = this.channels[11];
                    prev = ch.extended;
                    ch.extended = (v >> 5) & 1;
                    if (prev != ch.extended)
                        ch.update();
                }
                return;

                case 0x105: // Opl3 extensions enable register

                    this.mode = v & 0x01; // Opl3 mode when bit0=1 otherwise it is OPL2 mode

                    // following behaviour was tested on real YMF262,
                    // switching Opl3/OPL2 modes on the fly:
                    //  - does not change the waveForm previously selected (unless when ....)
                    //  - does not update ch.A, ch.B, ch.C and ch.D output selectors (registers c0-c8) (unless when ....)
                    //  - does not disable channels 9-17 on Opl3.OPL2 switch
                    //  - does not switch 4 Operator channels back to 2 Operator channels

                    return;

                default:
//if (r < 0x120)
// logger.log(Level.TRACE, "write to unknown register (set#2): %03x value=%02x".formatted(r, v));
                    break;
                }

                chOffset = 9; // register page //#2 starts from channel 9 (counting from 0)
            }

            // adjust bus to 8 bits
            r &= 0xff;
            v &= 0xff;

            switch (r & 0xe0) {
            case 0x00: // 00-1f:control
                switch (r & 0x1f) {
                case 0x01: // test register
                    break;
                case 0x02: // Timer 1
                    this.T[0] = (256 - v) * 4;
                    break;
                case 0x03: // Timer 2
                    this.T[1] = (256 - v) * 16;
                    break;
                case 0x04: // IRQ clear / mask and Timer enable
                    if ((v & 0x80) != 0) { // IRQ flags clear
                        this.resetStatus(0x60);
                    } else { // set IRQ mask ,timer enable
                        int st1 = v & 1;
                        int st2 = (v >> 1) & 1;

                        // IRQRST,T1MSK,t2MSK,x,x,x,ST2,ST1
                        this.resetStatus(v & 0x60);
                        this.setStatusMask((~v) & 0x60);

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
                case 0x08: // x,NTS,x,x, x,x,x,x
                    this.nts = v;
                    break;

                default:
//logger.log(Level.TRACE, "write to unknown register: %02x value=%02x".formatted(r, v));
                    break;
                }
                break;
            case 0x20: // am ON, vib ON, ksr, eg_type, mul
                slot = slotArray[r & 0x1f];
                if (slot < 0) return;
                setMul(slot + chOffset * 2, v);
                break;
            case 0x40:
                slot = slotArray[r & 0x1f];
                if (slot < 0) return;
                setKslTl(slot + chOffset * 2, v);
                break;
            case 0x60:
                slot = slotArray[r & 0x1f];
                if (slot < 0) return;
                setArDr(slot + chOffset * 2, v);
                break;
            case 0x80:
                slot = slotArray[r & 0x1f];
                if (slot < 0) return;
                setSlRr(slot + chOffset * 2, v);
                break;
            case 0xa0:
                if (r == 0xbd) { // am depth, vibrato depth, r,bd,sd,tom,tc,hh
                    if (chOffset != 0) // 0xbd register is present in set //#1 only
                        return;

                    this.lfoAmDepth = v & 0x80;
                    this.lfoPmDepthRange = (v & 0x40) != 0 ? 8 : 0;

                    this.rhythm = v & 0x3f;

                    if ((this.rhythm & 0x20) != 0) {
                        // BD key on/off
                        if ((v & 0x10) != 0) {
                            this.channels[6].slots[SLOT1].keyOn(2);
                            this.channels[6].slots[SLOT2].keyOn(2);
                        } else {
                            this.channels[6].slots[SLOT1].keyOff(~2);
                            this.channels[6].slots[SLOT2].keyOff(~2);
                        }
                        // HH key on/off
                        if ((v & 0x01) != 0) this.channels[7].slots[SLOT1].keyOn(2);
                        else this.channels[7].slots[SLOT1].keyOff(~2);
                        // SD key on/off
                        if ((v & 0x08) != 0) this.channels[7].slots[SLOT2].keyOn(2);
                        else this.channels[7].slots[SLOT2].keyOff(~2);
                        // TOM key on/off
                        if ((v & 0x04) != 0) this.channels[8].slots[SLOT1].keyOn(2);
                        else this.channels[8].slots[SLOT1].keyOff(~2);
                        // TOP-CY key on/off
                        if ((v & 0x02) != 0) this.channels[8].slots[SLOT2].keyOn(2);
                        else this.channels[8].slots[SLOT2].keyOff(~2);
                    } else {
                        // BD key off
                        this.channels[6].slots[SLOT1].keyOff(~2);
                        this.channels[6].slots[SLOT2].keyOff(~2);
                        // HH key off
                        this.channels[7].slots[SLOT1].keyOff(~2);
                        // SD key off
                        this.channels[7].slots[SLOT2].keyOff(~2);
                        // TOM key off
                        this.channels[8].slots[SLOT1].keyOff(~2);
                        // TOP-CY off
                        this.channels[8].slots[SLOT2].keyOff(~2);
                    }
                    return;
                }

                // keyon,block,fNum
                if ((r & 0x0f) > 8) return;
                ch = this.channels[(r & 0x0f) + chOffset];
                chP3 = (((r & 0xf) + chOffset + 3) >= 0 && ((r & 0xf) + chOffset + 3) < this.channels.length) ? this.channels[(r & 0xf) + chOffset + 3] : null;
                chM3 = (((r & 0xf) + chOffset - 3) >= 0 && ((r & 0xf) + chOffset - 3) < this.channels.length) ? this.channels[(r & 0xf) + chOffset - 3] : null;

                if ((r & 0x10) == 0) { // a0-a8
                    blockFNum = (ch.blockFNum & 0x1f00) | v;
                } else { // b0-b8
                    blockFNum = ((v & 0x1f) << 8) | (ch.blockFNum & 0xff);

                    if ((this.mode & 1) != 0) {
                        int chan_no = (r & 0x0f) + chOffset;

                        // in Opl3 mode
                        // DO THIS:
                        //  if this is 1st channel forming up a 4-Op channel
                        //   ALSO keyon/off slots of 2nd channel forming up 4-Op channel
                        //  else normal 2 Operator function keyon/off
                        // OR THIS:
                        //  if this is 2nd channel forming up 4-Op channel just do nothing
                        //  else normal 2 Operator function keyon/off
                        switch (chan_no) {
                        case 0:
                        case 1:
                        case 2:
                        case 9:
                        case 10:
                        case 11:
                            if (ch.extended != 0) {
                                // if this is 1st channel forming up a 4-Op channel
                                // ALSO keyon/off slots of 2nd channel forming up 4-Op channel
                                if ((v & 0x20) != 0) {
                                    ch.slots[SLOT1].keyOn(1);
                                    ch.slots[SLOT2].keyOn(1);
                                    chP3.slots[SLOT1].keyOn(1);
                                    chP3.slots[SLOT2].keyOn(1);
                                } else {
                                    ch.slots[SLOT1].keyOff(~(int) 1);
                                    ch.slots[SLOT2].keyOff(~(int) 1);
                                    chP3.slots[SLOT1].keyOff(~(int) 1);
                                    chP3.slots[SLOT2].keyOff(~(int) 1);
                                }
                            } else {
                                // else normal 2 Operator function keyon/off
                                if ((v & 0x20) != 0) {
                                    ch.slots[SLOT1].keyOn(1);
                                    ch.slots[SLOT2].keyOn(1);
                                } else {
                                    ch.slots[SLOT2].keyOff(~(int) 1);
                                    ch.slots[SLOT2].keyOff(~(int) 1);
                                }
                            }
                            break;

                        case 3:
                        case 4:
                        case 5:
                        case 12:
                        case 13:
                        case 14:
                            if (chM3.extended != 0) {
                                // if this is 2nd channel forming up 4-Op channel just do nothing
                            } else {
                                // else normal 2 Operator function keyon/off
                                if ((v & 0x20) != 0) {
                                    ch.slots[SLOT1].keyOn(1);
                                    ch.slots[SLOT2].keyOn(1);
                                } else {
                                    ch.slots[SLOT1].keyOff(~(int) 1);
                                    ch.slots[SLOT2].keyOff(~(int) 1);
                                }
                            }
                            break;

                        default:
                            if ((v & 0x20) != 0) {
                                ch.slots[SLOT1].keyOn(1);
                                ch.slots[SLOT2].keyOn(1);
                            } else {
                                ch.slots[SLOT1].keyOff(~(int) 1);
                                ch.slots[SLOT2].keyOff(~(int) 1);
                            }
                            break;
                        }
                    } else {
                        if ((v & 0x20) != 0) {
                            ch.slots[SLOT1].keyOn(1);
                            ch.slots[SLOT2].keyOn(1);
                        } else {
                            ch.slots[SLOT1].keyOff(~(int) 1);
                            ch.slots[SLOT2].keyOff(~(int) 1);
                        }
                    }
                }
                // update
                if (ch.blockFNum != blockFNum) {
                    int block = blockFNum >> 10;

                    ch.blockFNum = blockFNum;

                    ch.kslBase = Channel.kslTab[blockFNum >> 6];
                    ch.fc = this.fnTab[blockFNum & 0x03ff] >> (7 - block);

                    // BLK 2,1,0 bits . bits 3,2,1 of kcode
                    ch.kCode = (ch.blockFNum & 0x1c00) >> 9;

                    // the info below is actually opposite to what is stated in the Manuals (verifed on real YMF262)
                    // if notesel == 0 . lsb of kcode is bit 10 (MSB) of fNum
                    // if notesel == 1 . lsb of kcode is bit 9 (MSB-1) of fNum
                    if ((this.nts & 0x40) != 0)
                        ch.kCode |= (ch.blockFNum & 0x100) >> 8; // notesel == 1
                    else
                        ch.kCode |= (ch.blockFNum & 0x200) >> 9; // notesel == 0

                    if ((this.mode & 1) != 0) {
                        int chan_no = (r & 0x0f) + chOffset;
                        // in Opl3 mode
                        // DO THIS:
                        //  if this is 1st channel forming up a 4-Op channel
                        //   ALSO update slots of 2nd channel forming up 4-Op channel
                        //  else normal 2 Operator function keyon/off
                        // OR THIS:
                        //  if this is 2nd channel forming up 4-Op channel just do nothing
                        //  else normal 2 Operator function keyon/off
                        switch (chan_no) {
                        case 0:
                        case 1:
                        case 2:
                        case 9:
                        case 10:
                        case 11:
                            if (ch.extended != 0) {
                                // if this is 1st channel forming up a 4-Op channel
                                // ALSO update slots of 2nd channel forming up 4-Op channel

                                // refresh Total Level in FOUR SLOTs of this channel and channel+3 using data from THIS channel
                                ch.slots[SLOT1].tll = ch.slots[SLOT1].tl + (ch.kslBase >> ch.slots[SLOT1].ksl);
                                ch.slots[SLOT2].tll = ch.slots[SLOT2].tl + (ch.kslBase >> ch.slots[SLOT2].ksl);
                                chP3.slots[SLOT1].tll = chP3.slots[SLOT1].tl + (ch.kslBase >> chP3.slots[SLOT1].ksl);
                                chP3.slots[SLOT2].tll = chP3.slots[SLOT2].tl + (ch.kslBase >> chP3.slots[SLOT2].ksl);

                                // refresh frequency counter in FOUR SLOTs of this channel and channel+3 using data from THIS channel
                                ch.slots[SLOT1].calcFc(ch.fc, ch.kCode);
                                ch.slots[SLOT2].calcFc(ch.fc, ch.kCode);
                                chP3.slots[SLOT1].calcFc(ch.fc, ch.kCode);
                                chP3.slots[SLOT2].calcFc(ch.fc, ch.kCode);
                            } else {
                                // else normal 2 Operator function
                                // refresh Total Level in both SLOTs of this channel
                                ch.slots[SLOT1].tll = ch.slots[SLOT1].tl + (ch.kslBase >> ch.slots[SLOT1].ksl);
                                ch.slots[SLOT2].tll = ch.slots[SLOT2].tl + (ch.kslBase >> ch.slots[SLOT2].ksl);

                                // refresh frequency counter in both SLOTs of this channel
                                ch.slots[SLOT1].calcFc(ch.fc, ch.kCode);
                                ch.slots[SLOT2].calcFc(ch.fc, ch.kCode);
                            }
                            break;

                        case 3:
                        case 4:
                        case 5:
                        case 12:
                        case 13:
                        case 14:
                            if (chM3.extended != 0) {
                                //if this is 2nd channel forming up 4-Op channel just do nothing
                            } else {
                                //else normal 2 Operator function
                                // refresh Total Level in both SLOTs of this channel
                                ch.slots[SLOT1].tll = ch.slots[SLOT1].tl + (ch.kslBase >> ch.slots[SLOT1].ksl);
                                ch.slots[SLOT2].tll = ch.slots[SLOT2].tl + (ch.kslBase >> ch.slots[SLOT2].ksl);

                                // refresh frequency counter in both SLOTs of this channel
                                ch.slots[SLOT1].calcFc(ch.fc, ch.kCode);
                                ch.slots[SLOT2].calcFc(ch.fc, ch.kCode);
                            }
                            break;

                        default:
                            // refresh Total Level in both SLOTs of this channel
                            ch.slots[SLOT1].tll = ch.slots[SLOT1].tl + (ch.kslBase >> ch.slots[SLOT1].ksl);
                            ch.slots[SLOT2].tll = ch.slots[SLOT2].tl + (ch.kslBase >> ch.slots[SLOT2].ksl);

                            // refresh frequency counter in both SLOTs of this channel
                            ch.slots[SLOT1].calcFc(ch.fc, ch.kCode);
                            ch.slots[SLOT2].calcFc(ch.fc, ch.kCode);
                            break;
                        }
                    } else {
                        // in OPL2 mode

                        // refresh Total Level in both SLOTs of this channel
                        ch.slots[SLOT1].tll = ch.slots[SLOT1].tl + (ch.kslBase >> ch.slots[SLOT1].ksl);
                        ch.slots[SLOT2].tll = ch.slots[SLOT2].tl + (ch.kslBase >> ch.slots[SLOT2].ksl);

                        // refresh frequency counter in both SLOTs of this channel
                        ch.slots[SLOT1].calcFc(ch.fc, ch.kCode);
                        ch.slots[SLOT2].calcFc(ch.fc, ch.kCode);
                    }
                }
                break;

            case 0xc0:
                // ch.D, ch.C, ch.B, ch.A, FB(3bits), C
                if ((r & 0xf) > 8) return;

                ch = this.channels[(r & 0xf) + chOffset];
                chP3 = (((r & 0xf) + chOffset + 3) >= 0 && ((r & 0xf) + chOffset + 3) < this.channels.length) ? this.channels[(r & 0xf) + chOffset + 3] : null;
                chM3 = (((r & 0xf) + chOffset - 3) >= 0 && ((r & 0xf) + chOffset - 3) < this.channels.length) ? this.channels[(r & 0xf) + chOffset - 3] : null;

                if ((this.mode & 1) != 0) {
                    int base = ((r & 0xf) + chOffset) * 4;

                    // Opl3 mode
                    this.pan[base] = (v & 0x10) != 0 ? ~0 : 0; // ch.A
                    this.pan[base + 1] = (v & 0x20) != 0 ? ~0 : 0; // ch.B
                    this.pan[base + 2] = (v & 0x40) != 0 ? ~0 : 0; // ch.C
                    this.pan[base + 3] = (v & 0x80) != 0 ? ~0 : 0; // ch.D
                } else {
                    int _base = ((r & 0xf) + chOffset) * 4;

                    // OPL2 mode - always enabled
                    this.pan[_base] = ~(int) 0; // ch.A
                    this.pan[_base + 1] = ~(int) 0; // ch.B
                    this.pan[_base + 2] = ~(int) 0; // ch.C
                    this.pan[_base + 3] = ~(int) 0; // ch.D
                }

                this.panCtrlValue[(r & 0xf) + chOffset] = v; // store control value for Opl3/OPL2 mode switching on the fly

                ch.slots[SLOT1].fb = ((v >> 1) & 7) != 0 ? ((v >> 1) & 7) + 7 : 0;
                ch.slots[SLOT1].con = v & 1;

                if ((this.mode & 1) != 0) {
                    int chanNo = (r & 0x0f) + chOffset;

                    switch (chanNo) {
                    case 0:
                    case 1:
                    case 2:
                    case 9:
                    case 10:
                    case 11:
                        if (ch.extended != 0) {
                            int conn = (ch.slots[SLOT1].con << 1) | (chP3.slots[SLOT1].con << 0);
                            switch (conn) {
                            case 0:
                                // 1 -> 2 -> 3 -> 4 - out
                                ch.slots[SLOT1].connect.index = 18;
                                ch.slots[SLOT2].connect.index = 19;
                                chP3.slots[SLOT1].connect.index = 18;
                                chP3.slots[SLOT2].connect.index = chanNo + 3;
                                break;
                            case 1:
                                // 1 -> 2 -\
                                // 3 -> 4 -+- out
                                ch.slots[SLOT1].connect.index = 18;
                                ch.slots[SLOT2].connect.index = chanNo;
                                chP3.slots[SLOT1].connect.index = 18;
                                chP3.slots[SLOT2].connect.index = chanNo + 3;
                                break;
                            case 2:
                                // 1 -----------\
                                // 2 -> 3 -> 4 -+- out
                                ch.slots[SLOT1].connect.index = chanNo;
                                ch.slots[SLOT2].connect.index = 19;
                                chP3.slots[SLOT1].connect.index = 18;
                                chP3.slots[SLOT2].connect.index = chanNo + 3;
                                break;
                            case 3:
                                // 1 ------\
                                // 2 -> 3 -+- out
                                // 4 ------/
                                ch.slots[SLOT1].connect.index = chanNo;
                                ch.slots[SLOT2].connect.index = 19;
                                chP3.slots[SLOT1].connect.index = chanNo + 3;
                                chP3.slots[SLOT2].connect.index = chanNo + 3;
                                break;
                            }
                        } else {
                            // 2 operators mode
                            ch.slots[SLOT1].connect.index = ch.slots[SLOT1].con != 0 ? ((r & 0xf) + chOffset) : 18;
                            ch.slots[SLOT2].connect.index = (r & 0xf) + chOffset;
                        }
                        break;

                    case 3:
                    case 4:
                    case 5:
                    case 12:
                    case 13:
                    case 14:
                        if (chM3.extended != 0) {
                            int conn = (chM3.slots[SLOT1].con << 1) | (ch.slots[SLOT1].con << 0);
                            switch (conn) {
                            case 0:
                                // 1 -> 2 -> 3 -> 4 - out
                                chM3.slots[SLOT1].connect.index = 18;
                                chM3.slots[SLOT2].connect.index = 19;
                                ch.slots[SLOT1].connect.index = 18;
                                ch.slots[SLOT2].connect.index = chanNo;
                                break;
                            case 1:
                                // 1 -> 2 -\
                                // 3 -> 4 -+- out
                                chM3.slots[SLOT1].connect.index = 18;
                                chM3.slots[SLOT2].connect.index = chanNo - 3;
                                ch.slots[SLOT1].connect.index = 18;
                                ch.slots[SLOT2].connect.index = chanNo;
                                break;
                            case 2:
                                // 1 -----------\
                                // 2 -> 3 -> 4 -+- out
                                chM3.slots[SLOT1].connect.index = chanNo - 3;
                                chM3.slots[SLOT2].connect.index = 19;
                                ch.slots[SLOT1].connect.index = 18;
                                ch.slots[SLOT2].connect.index = chanNo;
                                break;
                            case 3:
                                // 1 ------\
                                // 2 -> 3 -+- out
                                // 4 ------/
                                chM3.slots[SLOT1].connect.index = chanNo - 3;
                                chM3.slots[SLOT2].connect.index = 19;
                                ch.slots[SLOT1].connect.index = chanNo;
                                ch.slots[SLOT2].connect.index = chanNo;
                                break;
                            }
                        } else {
                            // 2 operators mode
                            ch.slots[SLOT1].connect.index = ch.slots[SLOT1].con != 0 ? ((r & 0xf) + chOffset) : 18;
                            ch.slots[SLOT2].connect.index = (r & 0xf) + chOffset;
                        }
                        break;

                    default:
                        // 2 operators mode
                        ch.slots[SLOT1].connect.index = ch.slots[SLOT1].con != 0 ? ((r & 0xf) + chOffset) : 18;
                        ch.slots[SLOT2].connect.index = (r & 0xf) + chOffset;
                        break;
                    }
                } else {
                    // OPL2 mode - always 2 operators mode
                    ch.slots[SLOT1].connect.index = ch.slots[SLOT1].con != 0 ? ((r & 0xf) + chOffset) : 18;
                    ch.slots[SLOT2].connect.index = (r & 0xf) + chOffset;
                }
                break;

            case 0xe0: // waveForm select
                slot = slotArray[r & 0x1f];
                if (slot < 0) return;

                slot += chOffset * 2;

                ch = this.channels[slot / 2];

                // store 3-bit value written regardless of current OPL2 or Opl3 mode... (verified on real YMF262)
                v &= 7;
                ch.slots[slot & 1].waveformNumber = v;

                // ... but select only waveforms 0-3 in OPL2 mode
                if ((this.mode & 1) == 0) {
                    v &= 3; // we're in OPL2 mode
                }
                ch.slots[slot & 1].waveTable = v * SIN_LEN;
                break;
            }
        }

        @Override
        public void setMuteMask(int muteMask) {
            for (int curChn = 0; curChn < 18; curChn++)
                this.channels[curChn].muted = (muteMask >> curChn) & 0x01;
            for (int curChn = 0; curChn < 5; curChn++)
                this.muteSpc[curChn] = (muteMask >> (curChn + 18)) & 0x01;
        }

        /**
         * Generate samples for one of the YMF262's
         *
         * @param buffers is table of 4 pointers to the buffers: CH.A, CH.B, CH.C and CH.D
         * @param length  is the number of samples that should be generated
         */
        @Override
        public void update(int[][] buffers, int length) {
            int rhythm = this.rhythm & 0x20;

            int[] ch_a = buffers[0];
            int[] ch_b = buffers[1];

            for (int i = 0; i < length; i++) {
                int a, b, c, d;

                this.advanceLfo();

                // clear channel outputs
                Arrays.fill(this.chanOut, 0, 18, (byte) 0);

                // register set #1
                this.calcChannel(this.channels[0]); // extended 4op ch#0 part 1 or 2op ch#0
                if (this.channels[0].extended != 0)
                    this.calcChannelExt(this.channels[3]); // extended 4op ch#0 part 2
                else
                    this.calcChannel(this.channels[3]); // standard 2op ch#3

                this.calcChannel(this.channels[1]); // extended 4op ch#1 part 1 or 2op ch#1
                if (this.channels[1].extended != 0)
                    this.calcChannelExt(this.channels[4]); // extended 4op ch#1 part 2
                else
                    this.calcChannel(this.channels[4]); // standard 2op ch#4

                this.calcChannel(this.channels[2]); // extended 4op ch#2 part 1 or 2op ch#2
                if (this.channels[2].extended != 0)
                    this.calcChannelExt(this.channels[5]); // extended 4op ch#2 part 2
                else
                    this.calcChannel(this.channels[5]); // standard 2op ch#5

                if (rhythm == 0) {
                    this.calcChannel(this.channels[6]);
                    this.calcChannel(this.channels[7]);
                    this.calcChannel(this.channels[8]);
                } else { // Rhythm part
                    this.calcChannelRhythm(this.channels, 0, (this.noiseRng >> 0) & 1);
                }

                // register set //#2
                this.calcChannel(this.channels[9]);
                if (this.channels[9].extended != 0)
                    this.calcChannelExt(this.channels[12]);
                else
                    this.calcChannel(this.channels[12]);

                this.calcChannel(this.channels[10]);
                if (this.channels[10].extended != 0)
                    this.calcChannelExt(this.channels[13]);
                else
                    this.calcChannel(this.channels[13]);

                this.calcChannel(this.channels[11]);
                if (this.channels[11].extended != 0)
                    this.calcChannelExt(this.channels[14]);
                else
                    this.calcChannel(this.channels[14]);

                // channels 15,16,17 are fixed 2-Operator channels only
                this.calcChannel(this.channels[15]);
                this.calcChannel(this.channels[16]);
                this.calcChannel(this.channels[17]);

                // accumulator register set #1
                a = this.chanOut[0] & this.pan[0];
                b = this.chanOut[0] & this.pan[1];
                c = this.chanOut[0] & this.pan[2];
                d = this.chanOut[0] & this.pan[3];

                a += this.chanOut[1] & this.pan[4];
                b += this.chanOut[1] & this.pan[5];
                c += this.chanOut[1] & this.pan[6];
                d += this.chanOut[1] & this.pan[7];
                a += this.chanOut[2] & this.pan[8];
                b += this.chanOut[2] & this.pan[9];
                c += this.chanOut[2] & this.pan[10];
                d += this.chanOut[2] & this.pan[11];

                a += this.chanOut[3] & this.pan[12];
                b += this.chanOut[3] & this.pan[13];
                c += this.chanOut[3] & this.pan[14];
                d += this.chanOut[3] & this.pan[15];
                a += this.chanOut[4] & this.pan[16];
                b += this.chanOut[4] & this.pan[17];
                c += this.chanOut[4] & this.pan[18];
                d += this.chanOut[4] & this.pan[19];
                a += this.chanOut[5] & this.pan[20];
                b += this.chanOut[5] & this.pan[21];
                c += this.chanOut[5] & this.pan[22];
                d += this.chanOut[5] & this.pan[23];

                a += this.chanOut[6] & this.pan[24];
                b += this.chanOut[6] & this.pan[25];
                c += this.chanOut[6] & this.pan[26];
                d += this.chanOut[6] & this.pan[27];
                a += this.chanOut[7] & this.pan[28];
                b += this.chanOut[7] & this.pan[29];
                c += this.chanOut[7] & this.pan[30];
                d += this.chanOut[7] & this.pan[31];
                a += this.chanOut[8] & this.pan[32];
                b += this.chanOut[8] & this.pan[33];
                c += this.chanOut[8] & this.pan[34];
                d += this.chanOut[8] & this.pan[35];

                // accumulator register set #2
                a += this.chanOut[9] & this.pan[36];
                b += this.chanOut[9] & this.pan[37];
                c += this.chanOut[9] & this.pan[38];
                d += this.chanOut[9] & this.pan[39];
                a += this.chanOut[10] & this.pan[40];
                b += this.chanOut[10] & this.pan[41];
                c += this.chanOut[10] & this.pan[42];
                d += this.chanOut[10] & this.pan[43];
                a += this.chanOut[11] & this.pan[44];
                b += this.chanOut[11] & this.pan[45];
                c += this.chanOut[11] & this.pan[46];
                d += this.chanOut[11] & this.pan[47];

                a += this.chanOut[12] & this.pan[48];
                b += this.chanOut[12] & this.pan[49];
                c += this.chanOut[12] & this.pan[50];
                d += this.chanOut[12] & this.pan[51];
                a += this.chanOut[13] & this.pan[52];
                b += this.chanOut[13] & this.pan[53];
                c += this.chanOut[13] & this.pan[54];
                d += this.chanOut[13] & this.pan[55];
                a += this.chanOut[14] & this.pan[56];
                b += this.chanOut[14] & this.pan[57];
                c += this.chanOut[14] & this.pan[58];
                d += this.chanOut[14] & this.pan[59];

                a += this.chanOut[15] & this.pan[60];
                b += this.chanOut[15] & this.pan[61];
                c += this.chanOut[15] & this.pan[62];
                d += this.chanOut[15] & this.pan[63];
                a += this.chanOut[16] & this.pan[64];
                b += this.chanOut[16] & this.pan[65];
                c += this.chanOut[16] & this.pan[66];
                d += this.chanOut[16] & this.pan[67];
                a += this.chanOut[17] & this.pan[68];
                b += this.chanOut[17] & this.pan[69];
                c += this.chanOut[17] & this.pan[70];
                d += this.chanOut[17] & this.pan[71];

                a >>= FINAL_SH;
                b >>= FINAL_SH;
                c >>= FINAL_SH;
                d >>= FINAL_SH;

                // store to Sound buffer
                ch_a[i] = a + c;
                ch_b[i] = b + d;

//logger.log(Level.TRACE, "%d %d".formatted(ch_a[i], ch_b[i]));

                advance();
            }
        }
    }

    /**
     * YmF262 DosBox version
     *
     * @author Ken Silverman
     * @see "Ken Silverman's official web site: http://www.advsys.net/ken"
     * @see DosboxYm3812
     */
    public static class AdlibOpl3 implements Opl3 {

//#if defined(OPLTYPE_IS_OPL3)
        private static final int NUM_CHANNELS = 18;
//#else
//        private static final int NUM_CHANNELS = 9;
//#endif

        private static final int MAXOPERATORS = NUM_CHANNELS * 2;

        private static final double FL05 = 0.5;
        private static final double FL2 = 2.0;

        // fixed-point calculations using 16+16
        private static final int FIXEDPT = 0x10000;
        // fixed-point calculations using 8+24
        private static final int FIXEDPT_LFO = 0x1000000;

        // clocking of the chips
        private double intFreqU() {
            return this.chipClock / 288.0;
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

        // second Operator set for Opl3
        private static final int ARC_SECONDSET = 0x100;

        private static final int OP_ACT_OFF = 0x00;
        // regular channel activated (bitmasked)
        private static final int OP_ACT_NORMAL = 0x01;
        // percussion channel activated (bitmasked)
        private static final int OP_ACT_PERC = 0x02;

        private static final int BLOCKBUF_SIZE = 512;

        // vibrato constants
        private static final int VIBTAB_SIZE = 8;
        private static final int VIBFAC = 70 / 50000; // no braces, integer mul/div

        // tremolo constants and table
        private static final int TREMTAB_SIZE = 53;
        private static final double TREM_FREQ = 3.7; // tremolo at 3.7hz

        /*
         * Operator struct definition
         * For OPL2 all 9 channels consist of two operators each, carrier and modulator.
         * Channel x has operators x as modulator and operators (9+x) as carrier.
         * For Opl3 all 18 channels consist either of two operators (2op mode) or four
         * operators (4op mode) which is determined through register4 of the second
         * adlib register set.
         * Only the channels 0,1,2 (first set) and 9,10,11 (second set) can act as
         * 4op channels. The two additional operators for a channel y come from the
         * 2op channel y+3 so the operators y, (9+y), y+3, (9+y)+3 make up a 4op
         * channel.
         */
        public static class Operator {

            /** waveForm precision (10 bits) */
            private static final int WAVEPREC = 1024;

            // vibrato/tremolo tables */
            private static final int[] vibTable = new int[VIBTAB_SIZE];
            private static final int[] tremTable = new int[TREMTAB_SIZE * 2];

            private static final int[] vibValConst = new int[BLOCKBUF_SIZE];
            private static final int[] tremValConst = new int[BLOCKBUF_SIZE];

            /** key scale level lookup table */
            private static final double[] kslMul = {
                    0.0, 0.5, 0.25, 1.0 // . 0, 3, 1.5, 6 dB/oct
            };

            /** start of the waveForm */
            private static final int[] waveForm = {
                    WAVEPREC,
                    WAVEPREC >> 1,
                    WAVEPREC,
                    (WAVEPREC * 3) >> 2,
                    0,
                    0,
                    (WAVEPREC * 5) >> 2,
                    WAVEPREC << 1
            };

            /** length of the waveForm as mask */
            private static final int[] waveMask = {
                    WAVEPREC - 1,
                    WAVEPREC - 1,
                    (WAVEPREC >> 1) - 1,
                    (WAVEPREC >> 1) - 1,
                    WAVEPREC - 1,
                    ((WAVEPREC * 3) >> 2) - 1,
                    WAVEPREC >> 1,
                    WAVEPREC - 1
            };

            /** where the first entry resides */
            private static final int[] waveStart = {
                    0,
                    WAVEPREC >> 1,
                    0,
                    WAVEPREC >> 2,
                    0,
                    0,
                    0,
                    WAVEPREC >> 3
            };

            /** envelope generator function constants */
            private static final double[] attackConst = {
                    1 / 2.82624,
                    1 / 2.25280,
                    1 / 1.88416,
                    1 / 1.59744
            };

            private static final double[] decRelConst = {
                    1 / 39.28064,
                    1 / 31.41608,
                    1 / 26.17344,
                    1 / 22.44608
            };

            private static final Random rnd = new Random();

            /** wave form table */
            private static final short[] wavTable = new short[WAVEPREC * 3];

            static  {
                // create vibrato table
                //
                vibTable[0] = 8;
                vibTable[1] = 4;
                vibTable[2] = 0;
                vibTable[3] = -4;
                for (int i = 4; i < VIBTAB_SIZE; i++) vibTable[i] = vibTable[i - 4] * -1;

                //
                int[] tremTableInt = new int[TREMTAB_SIZE];

                Arrays.fill(vibValConst, 0);

                // create tremolo table
                //
                for (int i = 0; i < 14; i++) tremTableInt[i] = i - 13; // upwards (13 to 26 . -0.5/6 to 0)
                for (int i = 14; i < 41; i++) tremTableInt[i] = -i + 14; // downwards (26 to 0 . 0 to -1/6)
                for (int i = 41; i < 53; i++) tremTableInt[i] = i - 40 - 26; // upwards (1 to 12 . -1/6 to -0.5/6)

                for (int i = 0; i < TREMTAB_SIZE; i++) {
                    // 0.0 .. -26/26*4.8/6 == [0.0 .. -0.8], 4/53 steps == [1 .. 0.57]
                    double trem_val1 = ((double) tremTableInt[i]) * 4.8 / 26.0 / 6.0; // 4.8db
                    double trem_val2 = ((tremTableInt[i] / 4) * 1.2) / 6.0 / 6.0; // 1.2db (larger stepping)

                    tremTable[i] = (int) (Math.pow(FL2, trem_val1) * FIXEDPT);
                    tremTable[TREMTAB_SIZE + i] = (int) (Math.pow(FL2, trem_val2) * FIXEDPT);
                }

                //
                Arrays.fill(tremValConst, FIXEDPT);

                // create waveForm tables
                //
                for (int i = 0; i < (WAVEPREC >> 1); i++) {
                    wavTable[(i << 1) + WAVEPREC] = (short) (16384 * Math.sin((double) (i << 1) * Math.PI * 2 / WAVEPREC));
                    wavTable[(i << 1) + 1 + WAVEPREC] = (short) (16384 * Math.sin((double) ((i << 1) + 1) * Math.PI * 2 / WAVEPREC));
                    wavTable[i] = wavTable[(i << 1) + WAVEPREC];
                }
                for (int i = 0; i < (WAVEPREC >> 3); i++) {
                    wavTable[i + (WAVEPREC << 1)] = (short) (wavTable[i + (WAVEPREC >> 3)] - 16384);
                    wavTable[i + ((WAVEPREC * 17) >> 3)] = (short) (wavTable[i + (WAVEPREC >> 2)] + 16384);
                }

                // key scale level table verified ([table in book] * 8 / 3)
                //
                ksLev[7][0] = 0;
                ksLev[7][1] = 24;
                ksLev[7][2] = 32;
                ksLev[7][3] = 37;
                ksLev[7][4] = 40;
                ksLev[7][5] = 43;
                ksLev[7][6] = 45;
                ksLev[7][7] = 47;
                ksLev[7][8] = 48;
                for (int i = 9; i < 16; i++) ksLev[7][i] = i + 41;
                for (int j = 6; j >= 0; j--) {
                    for (int i = 0; i < 16; i++) {
                        int oct = ksLev[j + 1][i] - 8;
                        if (oct < 0) oct = 0;
                        ksLev[j][i] = oct;
                    }
                }
            }

            /** current output/last output (used for feedback) */
            private int cVal, lastCVal;
            /** time (position in waveForm) and time increment */
            private long tCount, wfPos, tInc;
            /** and amplification (envelope) */
            private double amp, stepAmp;
            /** volume */
            private double vol;
            /** sustain level */
            private double sustainLevel;
            /** feedback amount */
            private int mfbi;
            /** attack rate function coefficients */
            private double a0, a1, a2, a3;
            /** decay/release rate functions */
            private double decayMul, releaseMul;
            /** current state of Operator (attack/decay/sustain/release/off) */
            private int opState;
            private int tOff;
            /** highest three bits of the frequency, used for vibrato calculations */
            private int freqHigh;
            /** start of selected waveForm */
            private int curWFormPtr;
            /** mask for selected waveForm */
            private int curWMask;
            /** activity state (regular, percussion) */
            private int actState;
            /** keep sustain level when decay finished */
            private boolean susKeep;
            /** vibrato/tremolo enable bits */
            private boolean vibrato, tremolo;

            // variables used to provide non-continuous envelopes

            /** for non-standard sample rates we need to determine how many samples have passed */
            private int generatorPos;
            /** current (standardized) sample position */
            private int curEnvStep;
            /** number of std samples of one step (for attack/decay/release mode) */
            private int envStepA, envStepD, envStepR;
            /** position of 8-cyclic step skipping (always 2^x to check against mask) */
            private int step_skip_pos_a;
            /** bitmask that determines if a step is skipped (respective bit is zero then) */
            private int envStepSkipA;

            /** base of a 4op channel/part of a 4op channel */
            private boolean is4Op, is4OpAttached;
            /** opl3 stereo panning amount */
            private int leftPan, rightPan;

            public void reset() {
                this.opState = OF_TYPE_OFF;
                this.actState = OP_ACT_OFF;
                this.amp = 0.0;
                this.stepAmp = 0.0;
                this.vol = 0.0;
                this.tCount = 0;
                this.tInc = 0;
                this.tOff = 0;
                this.curWMask = Operator.waveMask[0];
                this.curWFormPtr = Operator.waveForm[0];
                this.freqHigh = 0;

                this.generatorPos = 0;
                this.curEnvStep = 0;
                this.envStepA = 0;
                this.envStepD = 0;
                this.envStepR = 0;
                this.step_skip_pos_a = 0;
                this.envStepSkipA = 0;

                this.is4Op = false;
                this.is4OpAttached = false;
                this.leftPan = 1;
                this.rightPan = 1;
            }

            public void advance(int vib, int generator_add) {
                wfPos = tCount; // waveForm position

                // advance waveForm time
                tCount += tInc;
                tCount += (int) (tInc * vib / FIXEDPT);

                generatorPos += generator_add;
            }

            public static void advanceDrums(Operator op1, int vib1, Operator op2, int vib2, Operator op3, int vib3, int generatorAdd) {
                long c1 = op1.tCount / FIXEDPT;
                long c3 = op3.tCount / FIXEDPT;
                int phaseBit = (((c1 & 0x88) ^ ((c1 << 5) & 0x80)) | ((c3 ^ (c3 << 2)) & 0x20)) != 0 ? 0x02 : 0x00;

                int noiseBit = rnd.nextInt() & 1; // rand() & 1;

                long snare_phase_bit = (((op1.tCount / FIXEDPT) / 0x100) & 1);

                // Hihat
                long inttm = (phaseBit << 8) | (0x34 << (phaseBit ^ (noiseBit << 1)));
                op1.wfPos = inttm * FIXEDPT; // waveForm position
                // advance waveForm time
                op1.tCount += op1.tInc;
                op1.tCount += op1.tInc * vib1 / FIXEDPT;
                op1.generatorPos += generatorAdd;

                // Snare
                inttm = ((1 + snare_phase_bit) ^ noiseBit) << 8;
                op2.wfPos = inttm * FIXEDPT; // waveForm position
                // advance waveForm time
                op2.tCount += op2.tInc;
                op2.tCount += op2.tInc * vib2 / FIXEDPT;
                op2.generatorPos += generatorAdd;

                // Cymbal
                inttm = (1 + phaseBit) << 8;
                op3.wfPos = inttm * FIXEDPT; // waveForm position
                // advance waveForm time
                op3.tCount += op3.tInc;
                op3.tCount += op3.tInc * vib3 / FIXEDPT;
                op3.generatorPos += generatorAdd;
            }

            /**
             * output level is sustained, mode changes only when Operator is turned off (.release)
             * or when the keep-sustained bit is turned off (.sustain_nokeep)
             */
            public void output(int modulator, int trem) {
                if (opState != OF_TYPE_OFF) {
                    lastCVal = cVal;
                    int i = (int) ((wfPos + modulator) / FIXEDPT);

                    // wform: -16384 to 16383 (0x4000)
                    // trem :  32768 to 65535 (0x10000)
                    // stepAmp: 0.0 to 1.0
                    // vol  : 1/2^14 to 1/2^29 (/0x4000; /1../0x8000)

                    cVal = (int) (stepAmp * vol * wavTable[curWFormPtr + (i & curWMask)] * trem / 16.0);
                }
            }

            /** no action, Operator is off */
            public void off() {
            }

            /**
             * output level is sustained, mode changes only when Operator is turned off (.release)
             * or when the keep-sustained bit is turned off (.sustain_nokeep)
             */
            public void sustain() {
                int num_steps_add = generatorPos / FIXEDPT; // number of (standardized) samples
                for (int ct = 0; ct < num_steps_add; ct++) {
                    curEnvStep++;
                }
                generatorPos -= num_steps_add * FIXEDPT;
            }

            /** Operator in release mode, if output level reaches zero the Operator is turned off */
            public void release() {
                // ??? boundary?
                if (amp > 0.00000001) {
                    // release phase
                    amp *= releaseMul;
                }

                int num_steps_add = generatorPos / FIXEDPT; // number of (standardized) samples
                for (int ct = 0; ct < num_steps_add; ct++) {
                    curEnvStep++; // sample counter
                    if ((curEnvStep & envStepR) == 0) {
                        if (amp <= 0.00000001) {
                            // release phase finished, turn off this Operator
                            amp = 0.0;
                            if (opState == OF_TYPE_REL) {
                                opState = OF_TYPE_OFF;
                            }
                        }
                        stepAmp = amp;
                    }
                }
                generatorPos -= num_steps_add * FIXEDPT;
            }

            /**
             * Operator in decay mode, if sustain level is reached the output level is either
             * kept (sustain level keep enabled) or the Operator is switched into release mode
             */
            public void decay() {

                if (amp > sustainLevel) {
                    // decay phase
                    amp *= decayMul;
                }

                int num_steps_add = generatorPos / FIXEDPT; // number of (standardized) samples
                for (int ct = 0; ct < num_steps_add; ct++) {
                    curEnvStep++;
                    if ((curEnvStep & envStepD) == 0) {
                        if (amp <= sustainLevel) {
                            // decay phase finished, sustain level reached
                            if (susKeep) {
                                // keep sustain level (until turned off)
                                opState = OF_TYPE_SUS;
                                amp = sustainLevel;
                            } else {
                                // next: release phase
                                opState = OF_TYPE_SUS_NOKEEP;
                            }
                        }
                        stepAmp = amp;
                    }
                }
                generatorPos -= num_steps_add * FIXEDPT;
            }

            /**
             * Operator in attack mode, if full output level is reached,
             * the Operator is switched into decay mode
             */
            public void attack() {
                amp = ((a3 * amp + a2) * amp + a1) * amp + a0;

                int num_steps_add = generatorPos / FIXEDPT; // number of (standardized) samples
                for (int ct = 0; ct < num_steps_add; ct++) {
                    curEnvStep++; // next sample
                    if ((curEnvStep & envStepA) == 0) { // check if next step already reached
                        if (amp > 1.0) {
                            // attack phase finished, next: decay
                            opState = OF_TYPE_DEC;
                            amp = 1.0;
                            stepAmp = 1.0;
                        }
                        step_skip_pos_a = (step_skip_pos_a << 1) & 0xff;
                        if (step_skip_pos_a == 0) step_skip_pos_a = 1;
                        if ((step_skip_pos_a & envStepSkipA) != 0) { // check if required to skip next step
                            stepAmp = amp;
                        }
                    }
                }
                generatorPos -= num_steps_add * FIXEDPT;
            }

            public void checkEgAttack() {
                if (((this.curEnvStep + 1) & this.envStepA) == 0) {
                    // check if next step already reached
                    if (this.a0 >= 1.0) {
                        // attack phase finished, next: decay
                        this.opState = OF_TYPE_DEC;
                        this.amp = 1.0;
                        this.stepAmp = 1.0;
                    }
                }
            }

            private static final int[] stepSkipMask = { 0xff,  0xfe,  0xee,  0xba,  0xaa};

            public void changeAttackRate(int attackRate, double recIpSamp) {
                if (attackRate != 0) {

                    double f = Math.pow(FL2, (double) attackRate + (tOff >> 2) - 1) * attackConst[tOff & 3] * recIpSamp;
                    // attack rate coefficients
                    a0 = 0.0377 * f;
                    a1 = 10.73 * f + 1;
                    a2 = -17.57 * f;
                    a3 = 7.42 * f;

                    int stepSkip = attackRate * 4 + tOff;
                    int steps = stepSkip >> 2;
                    envStepA = (1 << (steps <= 12 ? 12 - steps : 0)) - 1;

                    int stepNum = (stepSkip <= 48) ? (4 - (stepSkip & 3)) : 0;
                    envStepSkipA = stepSkipMask[stepNum];

//#if defined(OPLTYPE_IS_OPL3)
                    if (stepSkip >= 60) {
//#else
//                    if (stepSkip >= 62) {
//#endif
                        a0 = 2.0; // something that triggers an immediate transition to amp:=1.0
                        a1 = 0.0;
                        a2 = 0.0;
                        a3 = 0.0;
                    }
                } else {
                    // attack disabled
                    a0 = 0.0;
                    a1 = 1.0;
                    a2 = 0.0;
                    a3 = 0.0;
                    envStepA = 0;
                    envStepSkipA = 0;
                }
            }

            public void changeDecayRate(int decayRate, double recIpSamp) {
                // decayMul should be 1.0 when decayRate == 0
                if (decayRate != 0) {
                    double f = -7.4493 * decRelConst[tOff & 3] * recIpSamp;
                    decayMul = Math.pow(FL2, f * Math.pow(FL2, decayRate + (tOff >> 2)));
                    int steps = (decayRate * 4 + tOff) >> 2;
                    envStepD = (1 << (steps <= 12 ? 12 - steps : 0)) - 1;
                } else {
                    decayMul = 1.0;
                    envStepD = 0;
                }
            }

            public void changeReleaseRate(int releaseRate, double recIpSamp) {
                // releaseMul should be 1.0 when releaseRate == 0
                if (releaseRate != 0) {
                    double f = -7.4493 * decRelConst[tOff & 3] * recIpSamp;
                    releaseMul = Math.pow(FL2, f * Math.pow(FL2, releaseRate + (tOff >> 2)));
                    int steps = (releaseRate * 4 + tOff) >> 2;
                    envStepR = (1 << (steps <= 12 ? 12 - steps : 0)) - 1;
                } else {
                    releaseMul = 1.0;
                    envStepR = 0;
                }
            }

            public void changeSustainLevel(int sustainLevel) {
                // sustainLevel should be 0.0 when sustainLevel == 15 (max)
                if (sustainLevel < 15) {
                    this.sustainLevel = Math.pow(FL2, (double) sustainLevel * (-FL05));
                } else {
                    this.sustainLevel = 0.0;
                }
            }

            public void changeWaveform(int regBase, int[] wave_sel) {
//#if defined(OPLTYPE_IS_OPL3)
                if (regBase >= ARC_SECONDSET) regBase -= (ARC_SECONDSET - 22); // second set starts at 22
//#endif
                // waveForm selection
                curWMask = waveMask[wave_sel[regBase]];
                //op_pt.cur_wform = wavTable[waveForm[this.wave_sel[regbase]]];
                curWFormPtr = waveForm[wave_sel[regBase]];
                // (might need to be adapted to waveForm type here...)
            }

            public void changeKeepSustain(boolean susKeep) {
                this.susKeep = susKeep;
                if (opState == OF_TYPE_SUS) {
                    if (!susKeep)
                        opState = OF_TYPE_SUS_NOKEEP;
                } else if (opState == OF_TYPE_SUS_NOKEEP) {
                    if (susKeep)
                        opState = OF_TYPE_SUS;
                }
            }

            /** enable/disable vibrato/tremolo LFO effects */
            public void changeVibrato(int regBase, int[] adlibReg) {
                this.vibrato = (adlibReg[ARC_TVS_KSR_MUL + regBase] & 0x40) != 0;
                this.tremolo = (adlibReg[ARC_TVS_KSR_MUL + regBase] & 0x80) != 0;
            }

            /** change amount of self-feedback */
            public void changeFeedback(int feedback) {
                if (feedback != 0)
                    mfbi = (int) (Math.pow(FL2, (feedback >> 1) + 8));
                else
                    mfbi = 0;
            }

            public void changeFrequency(int chanBase, int regBase, int[] adlibReg, double[] frqMul, double recIpSamp) {
                // frequency
                int frn = ((adlibReg[ARC_KON_BNUM + chanBase] & 3) << 8) + adlibReg[ARC_FREQ_NUM + chanBase];
                // block number/octave
                int oct = ((adlibReg[ARC_KON_BNUM + chanBase] >> 2) & 7);
                this.freqHigh = (frn >> 7) & 7;

                // keysplit
                int noteSel = (adlibReg[8] >> 6) & 1;
                this.tOff = ((frn >> 9) & (noteSel ^ 1)) | ((frn >> 8) & noteSel);
                this.tOff += (oct << 1);

                // envelope scaling (KSR)
                if ((adlibReg[ARC_TVS_KSR_MUL + regBase] & 0x10) == 0) this.tOff >>= 2;

                // 20+a0+b0:
                this.tInc = (long) ((((double) (frn << oct)) * frqMul[adlibReg[ARC_TVS_KSR_MUL + regBase] & 15]));
                // 40+a0+b0:
assert frn >> 6 < 16 : "%08x, %x, %x".formatted(frn, adlibReg[ARC_KON_BNUM + chanBase], adlibReg[ARC_FREQ_NUM + chanBase]);
                double volIn = (double) (adlibReg[ARC_KSL_OUTLEV + regBase] & 63) +
                        (kslMul[adlibReg[ARC_KSL_OUTLEV + regBase] >> 6] * ksLev[oct][frn >> 6]);
                this.vol = Math.pow(FL2, volIn * -0.125 - 14);

                // Operator frequency changed, care about features that depend on it
                this.changeAttackRate(adlibReg[ARC_ATTR_DECR + regBase] >> 4, recIpSamp);
                this.changeDecayRate(adlibReg[ARC_ATTR_DECR + regBase] & 15, recIpSamp);
                this.changeReleaseRate(adlibReg[ARC_SUSL_RELR + regBase] & 15, recIpSamp);
            }

            public void enable(int regBase, int act_type, int[] wave_sel) {
                // check if this is really an off-on transition
                int wselbase = regBase;
                if (actState == OP_ACT_OFF) {
                    if (wselbase >= ARC_SECONDSET)
                        wselbase -= (ARC_SECONDSET - 22); // second set starts at 22

                    tCount = waveStart[wave_sel[wselbase]] * FIXEDPT;

                    // start with attack mode
                    opState = OF_TYPE_ATT;
                    actState |= act_type;
                }
            }

            public void disable(int act_type) {
                // check if this is really an on-off transition
                if (this.actState != OP_ACT_OFF) {
                    this.actState &= ~act_type;
                    if (this.actState == OP_ACT_OFF) {
                        if (this.opState != OF_TYPE_OFF)
                            this.opState = OF_TYPE_REL;
                    }
                }
            }
        }

        // vibrato value tables (used per-Operator)
        private static final int[] vibValVar1 = new int[BLOCKBUF_SIZE];
        private static final int[] vibValVar2 = new int[BLOCKBUF_SIZE];

        /** frequency multiplicator lookup table */
        private static final double[] frqMulTab = {
                0.5, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 12, 12, 15, 15
        };

        // calculated frequency multiplication values (depend on sampling rate)

        // key scale levels */
        private static final int[][] ksLev = {
                new int[16], new int[16], new int[16], new int[16], new int[16], new int[16], new int[16], new int[16]
        };

        // map a channel number to the register offset of the modulator (=register base) */
        private static final int[] modulatorBase = {
                0, 1, 2,
                8, 9, 10,
                16, 17, 18
        };

        // map a register base to a modulator Operator number or Operator number */
        private static final int[] regBase2Modop = {
                0, 1, 2, 0, 1, 2, 0, 0, 3, 4, 5, 3, 4, 5, 0, 0, 6, 7, 8, 6, 7, 8, // first set
                18, 19, 20, 18, 19, 20, 0, 0, 21, 22, 23, 21, 22, 23, 0, 0, 24, 25, 26, 24, 25, 26 // second set
        };
        private static final int[] regBase2op = {
                0, 1, 2, 9, 10, 11, 0, 0, 3, 4, 5, 12, 13, 14, 0, 0, 6, 7, 8, 15, 16, 17, // first set
                18, 19, 20, 27, 28, 29, 0, 0, 21, 22, 23, 30, 31, 32, 0, 0, 24, 25, 26, 33, 34, 35 // second set
        };

        // per-chips variables

        /** adlib register set (including second set) */
        private final int[] adlibReg = new int[512];

        /** waveForm selection */
        private final int[] waveSel = new int[44];

        private final Operator[] ops = new Operator[MAXOPERATORS];
        private final int[] muteChn = new int[NUM_CHANNELS + 5];
        private final int chipClock;

        private final int intSamplerate;

        private int status;
        private int oplIndex;
        private int oplAddr;

        // vibrato/tremolo increment/counter
        private int vibTabPos;
        private final int vibTabAdd;
        private int tremTabPos;
        private final int tremTabAdd;

        /** should be a chips parameter */
        private final int generatorAdd;

        /** inverse of sampling rate */
        private final double recIpSamp;
        private final double[] frqMul = new double[16];

        /** stream update handler */
        private final UpdateHandler updateHandler;

        private interface Operators extends Consumer<Operator> {
        }

        private final Operators[] opFuncs = new Operators[] {
                Operator::attack,
                Operator::decay,
                Operator::release,
                Operator::sustain, // sustain phase (keeping level)
                Operator::release, // sustain_nokeep phase (release-style)
                Operator::off
        };

        /** @param updateHandler nullable */
        private AdlibOpl3(int clock, int sampleRate, UpdateHandler updateHandler) {
            //logger.log(Level.TRACE, "clock:%d rate:%d".formatted(clock, sampleRate));

            this.chipClock = clock;
            this.intSamplerate = sampleRate;
            this.updateHandler = updateHandler;

            this.generatorAdd = (int) (this.intFreqU() * FIXEDPT / this.intSamplerate);

            this.recIpSamp = 1.0 / (double) this.intSamplerate;
            for (int i = 15; i >= 0; i--) {
                this.frqMul[i] = frqMulTab[i] * this.intFreqU() / (double) Operator.WAVEPREC * (double) FIXEDPT * this.recIpSamp;
            }

            // vibrato at ~6.1 ?? (opl3 docs say 6.1, opl4 docs say 6.0, Y8950 docs say 6.4)
            this.vibTabAdd = (int) (VIBTAB_SIZE * FIXEDPT_LFO / 8192 * this.intFreqU() / this.intSamplerate);
            this.vibTabPos = 0;

            // tremolo at 3.7hz
            this.tremTabAdd = (int) ((double) TREMTAB_SIZE * TREM_FREQ * FIXEDPT_LFO / (double) this.intSamplerate);
            this.tremTabPos = 0;
        }

        @Override
        public void stop() {
        }

        @Override
        public void reset() {

            Arrays.fill(this.adlibReg, (byte) 0x00);
            for (int i = 0; i < MAXOPERATORS; i++) this.ops[i] = new Operator();
            Arrays.fill(this.waveSel, (byte) 0x00);

            for (int i = 0; i < MAXOPERATORS; i++) {
                this.ops[i].reset();
            }

            this.status = 0;
            this.oplIndex = 0;
            this.oplAddr = 0;
        }

        @Override
        public int write(int addr, int val) {
            if ((addr & 1) != 0) {
//logger.log(Level.TRACE, "adr=%x  dat=%x".formatted(this.opl_addr, val));
                writeInternal(this.oplAddr, val);
            } else
//#if defined(OPLTYPE_IS_OPL3)
                this.oplAddr = val | ((addr & 2) << 7);
//#else
//                this.oplAddr = val;
//#endif
            return 0;
        }

        private void writeInternal(int idx, int val) {
            int secondSet = idx & 0x100;
            this.adlibReg[idx] = val;

            switch (idx & 0xf0) {
            case ARC_CONTROL:
                // here we check for the second set registers, too:
                switch (idx) {
                case 0x02: // timer1 counter
                case 0x03: // timer2 counter
                    break;
                case 0x04:
                    // IRQ reset, timer mask/start
                    if ((val & 0x80) != 0) {
                        // clear IRQ int in status register
                        this.status &= 0x9f; // ~0x60;
                    } else {
                        this.status = 0;
                    }
                    break;
//#if defined(OPLTYPE_IS_OPL3)
                case 0x04 | ARC_SECONDSET:
                    // 4op enable/disable switches for each possible channel
                    this.ops[0].is4Op = (val & 1) > 0;
                    this.ops[3].is4OpAttached = this.ops[0].is4Op;
                    this.ops[1].is4Op = (val & 2) > 0;
                    this.ops[4].is4OpAttached = this.ops[1].is4Op;
                    this.ops[2].is4Op = (val & 4) > 0;
                    this.ops[5].is4OpAttached = this.ops[2].is4Op;
                    this.ops[18].is4Op = (val & 8) > 0;
                    this.ops[21].is4OpAttached = this.ops[18].is4Op;
                    this.ops[19].is4Op = (val & 16) > 0;
                    this.ops[22].is4OpAttached = this.ops[19].is4Op;
                    this.ops[20].is4Op = (val & 32) > 0;
                    this.ops[23].is4OpAttached = this.ops[20].is4Op;
                    break;
                case 0x05 | ARC_SECONDSET:
                    break;
//#endif
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
                int num = idx & 7;
                int base = (idx - ARC_TVS_KSR_MUL) & 0xff;
                if ((num < 6) && (base < 22)) {
                    int modOp = regBase2Modop[secondSet != 0 ? (base + 22) : base];
                    int regBase = base + secondSet;
                    int chanBase = secondSet != 0 ? (modOp - 18 + ARC_SECONDSET) : modOp;

                    // change tremolo/vibrato and sustain keeping of this Operator
                    Operator op = this.ops[modOp + ((num < 3) ? 0 : 9)];
                    op.changeKeepSustain((adlibReg[ARC_TVS_KSR_MUL + regBase] & 0x20) > 0);
                    op.changeVibrato(regBase, this.adlibReg);

                    // change frequency calculations of this Operator as
                    // key scale rate and frequency multiplicator can be changed
//#if defined(OPLTYPE_IS_OPL3)
                    if ((this.adlibReg[0x105] & 1) != 0 && (this.ops[modOp].is4OpAttached)) {
                        // Operator uses frequency of channel
                        op.changeFrequency(chanBase - 3, regBase, this.adlibReg, this.frqMul, this.recIpSamp);
                    } else {
                        op.changeFrequency(chanBase, regBase, this.adlibReg, this.frqMul, this.recIpSamp);
                    }
//#else
//                    op.changeFrequency(chanBase, base, this.adlibReg, this.frqMul, this.recIpSamp);
//#endif
                }
            }
            break;
            case ARC_KSL_OUTLEV:
            case ARC_KSL_OUTLEV + 0x10: {
                // key scale level; output rate
                int num = idx & 7;
                int base = (idx - ARC_KSL_OUTLEV) & 0xff;
                if ((num < 6) && (base < 22)) {
                    int modOp = regBase2Modop[secondSet != 0 ? (base + 22) : base];
                    int chanBase = secondSet != 0 ? (modOp - 18 + ARC_SECONDSET) : modOp;

                    // change frequency calculations of this Operator as
                    // key scale level and output rate can be changed
//#if defined(OPLTYPE_IS_OPL3)
                    Operator op = this.ops[modOp + ((num < 3) ? 0 : 9)];
                    int regBase = base + secondSet;
                    if ((this.adlibReg[0x105] & 1) != 0 && (this.ops[modOp].is4OpAttached)) {
                        // Operator uses frequency of channel
                        op.changeFrequency(chanBase - 3, regBase, this.adlibReg, this.frqMul, this.recIpSamp);
                    } else {
                        op.changeFrequency(chanBase, regBase, this.adlibReg, this.frqMul, this.recIpSamp);
                    }
//#else
//                    op.changeFrequency(chanBase, base, this.adlibReg, this.frqMul, this.recIpSamp);
//#endif
                }
            }
            break;
            case ARC_ATTR_DECR:
            case ARC_ATTR_DECR + 0x10: {
                // attack/decay rates
                int num = idx & 7;
                int base = (idx - ARC_ATTR_DECR) & 0xff;
                if ((num < 6) && (base < 22)) {
                    int regBase = base + secondSet;

                    // change attack rate and decay rate of this Operator
                    Operator op = this.ops[regBase2op[secondSet != 0 ? (base + 22) : base]];
                    op.changeAttackRate(adlibReg[ARC_ATTR_DECR + regBase] >> 4, this.recIpSamp);
                    op.changeDecayRate(adlibReg[ARC_ATTR_DECR + regBase] & 15, this.recIpSamp);
                }
            }
            break;
            case ARC_SUSL_RELR:
            case ARC_SUSL_RELR + 0x10: {
                // sustain level; release rate
                int num = idx & 7;
                int base = (idx - ARC_SUSL_RELR) & 0xff;
                if ((num < 6) && (base < 22)) {
                    int regBase = base + secondSet;

                    // change sustain level and release rate of this Operator
                    Operator op = this.ops[regBase2op[secondSet != 0 ? (base + 22) : base]];
                    op.changeReleaseRate(adlibReg[ARC_SUSL_RELR + regBase] & 15, this.recIpSamp);
                    op.changeSustainLevel(adlibReg[ARC_SUSL_RELR + regBase] >> 4);
                }
            }
            break;
            case ARC_FREQ_NUM: {
                // 0xa0-0xa8 low8 frequency
                int base = (idx - ARC_FREQ_NUM) & 0xff;
                if (base < 9) {
                    int opBase = secondSet != 0 ? (base + 18) : base;
//#if defined(OPLTYPE_IS_OPL3)
                    if ((this.adlibReg[0x105] & 1) != 0 && this.ops[opBase].is4OpAttached) break;
//#endif
                    // regBase of modulator:
                    int modBase = modulatorBase[base] + secondSet;

                    int chanBase = base + secondSet;

                    this.ops[opBase].changeFrequency(chanBase, modBase, this.adlibReg, this.frqMul, this.recIpSamp);
                    this.ops[opBase + 9].changeFrequency(chanBase, modBase + 3, this.adlibReg, this.frqMul, this.recIpSamp);
//#if defined(OPLTYPE_IS_OPL3)
                    // for 4op channels all four operators are modified to the frequency of the channel
                    if ((this.adlibReg[0x105] & 1) != 0 && this.ops[secondSet != 0 ? (base + 18) : base].is4Op) {
                        this.ops[opBase + 3].changeFrequency(chanBase, modBase + 8, this.adlibReg, this.frqMul, this.recIpSamp);
                        this.ops[opBase + 3 + 9].changeFrequency(chanBase, modBase + 3 + 8, this.adlibReg, this.frqMul, this.recIpSamp);
                    }
//#endif
                }
            }
            break;
            case ARC_KON_BNUM: {
                if (this.updateHandler != null) // hack for DOSBox logs
                    this.updateHandler.run();
                if (idx == ARC_PERC_MODE) {
//#if defined(OPLTYPE_IS_OPL3)
                    if (secondSet != 0) return;
//#endif

                    if ((val & 0x30) == 0x30) { // BassDrum active
                        this.ops[6].enable(16, OP_ACT_PERC, this.waveSel);
                        this.ops[6].changeFrequency(6, 16, this.adlibReg, this.frqMul, this.recIpSamp);
                        this.ops[6 + 9].enable(16 + 3, OP_ACT_PERC, this.waveSel);
                        this.ops[6 + 9].changeFrequency(6, 16 + 3, this.adlibReg, this.frqMul, this.recIpSamp);
                    } else {
                        this.ops[6].disable(OP_ACT_PERC);
                        this.ops[6 + 9].disable(OP_ACT_PERC);
                    }
                    if ((val & 0x28) == 0x28) { // Snare active
                        this.ops[16].enable(17 + 3, OP_ACT_PERC, this.waveSel);
                        this.ops[16].changeFrequency(7, 17 + 3, this.adlibReg, this.frqMul, this.recIpSamp);
                    } else {
                        this.ops[16].disable(OP_ACT_PERC);
                    }
                    if ((val & 0x24) == 0x24) { // TomTom active
                        this.ops[8].enable(18, OP_ACT_PERC, this.waveSel);
                        this.ops[8].changeFrequency(8, 18, this.adlibReg, this.frqMul, this.recIpSamp);
                    } else {
                        this.ops[8].disable(OP_ACT_PERC);
                    }
                    if ((val & 0x22) == 0x22) { // Cymbal active
                        this.ops[8 + 9].enable(18 + 3, OP_ACT_PERC, this.waveSel);
                        this.ops[8 + 9].changeFrequency(8, 18 + 3, this.adlibReg, this.frqMul, this.recIpSamp);
                    } else {
                        this.ops[8 + 9].disable(OP_ACT_PERC);
                    }
                    if ((val & 0x21) == 0x21) { // Hihat active
                        this.ops[7].enable(17, OP_ACT_PERC, this.waveSel);
                        this.ops[7].changeFrequency(7, 17, this.adlibReg, this.frqMul, this.recIpSamp);
                    } else {
                        this.ops[7].disable(OP_ACT_PERC);
                    }

                    break;
                }
                // regular 0xb0-0xb8
                int base = (idx - ARC_KON_BNUM) & 0xff;
                if (base < 9) {
                    int opBase = secondSet != 0 ? (base + 18) : base;
                    // regBase of modulator:
                    int modBase = modulatorBase[base] + secondSet;

//#if defined(OPLTYPE_IS_OPL3)
                    if ((this.adlibReg[0x105] & 1) != 0 && this.ops[opBase].is4OpAttached) break;
//#endif
                    if ((val & 32) != 0) {
                        // Operator switched on
                        this.ops[opBase].enable(modBase, OP_ACT_NORMAL, this.waveSel); // modulator (if 2op)
                        this.ops[opBase + 9].enable(modBase + 3, OP_ACT_NORMAL, this.waveSel); // carrier (if 2op)
//#if defined(OPLTYPE_IS_OPL3)
                        // for 4op channels all four operators are switched on
                        if ((this.adlibReg[0x105] & 1) != 0 && this.ops[opBase].is4Op) {
                            // turn on chan+3 operators as well
                            this.ops[opBase + 3].enable(modBase + 8, OP_ACT_NORMAL, this.waveSel);
                            this.ops[opBase + 3 + 9].enable(modBase + 3 + 8, OP_ACT_NORMAL, this.waveSel);
                        }
//#endif
                    } else {
                        // Operator switched off
                        this.ops[opBase].disable(OP_ACT_NORMAL);
                        this.ops[opBase + 9].disable(OP_ACT_NORMAL);
//#if defined(OPLTYPE_IS_OPL3)
                        // for 4op channels all four operators are switched off
                        if ((this.adlibReg[0x105] & 1) != 0 && this.ops[opBase].is4Op) {
                            // turn off chan+3 operators as well
                            this.ops[opBase + 3].disable(OP_ACT_NORMAL);
                            this.ops[opBase + 3 + 9].disable(OP_ACT_NORMAL);
                        }
//#endif
                    }

                    int chanBase = base + secondSet;

                    // change frequency calculations of modulator and carrier (2op) as
                    // the frequency of the channel has changed
                    this.ops[opBase].changeFrequency(chanBase, modBase, this.adlibReg, this.frqMul, this.recIpSamp);
                    this.ops[opBase + 9].changeFrequency(chanBase, modBase + 3, this.adlibReg, this.frqMul, this.recIpSamp);
//#if defined(OPLTYPE_IS_OPL3)
                    // for 4op channels all four operators are modified to the frequency of the channel
                    if ((this.adlibReg[0x105] & 1) != 0 && this.ops[secondSet != 0 ? (base + 18) : base].is4Op) {
                        // change frequency calculations of chan+3 operators as well
                        this.ops[opBase + 3].changeFrequency(chanBase, modBase + 8, this.adlibReg, this.frqMul, this.recIpSamp);
                        this.ops[opBase + 3 + 9].changeFrequency(chanBase, modBase + 3 + 8, this.adlibReg, this.frqMul, this.recIpSamp);
                    }
//#endif
                }
            }
            break;
            case ARC_FEEDBACK: {
                // 0xc0-0xc8 feedback/modulation type (AM/FM)
                int base = (idx - ARC_FEEDBACK) & 0xff;
                if (base < 9) {
                    int opBase = secondSet != 0 ? (base + 18) : base;
                    int chanBase = base + secondSet;
                    this.ops[opBase].changeFeedback(adlibReg[ARC_FEEDBACK + chanBase] & 14);
//#if defined(OPLTYPE_IS_OPL3)
                    // Opl3 panning
                    this.ops[opBase].leftPan = ((val & 0x10) >> 4);
                    this.ops[opBase].rightPan = ((val & 0x20) >> 5);
                    this.ops[opBase].leftPan += ((val & 0x40) >> 6);
                    this.ops[opBase].rightPan += ((val & 0x80) >> 7);
//#endif
                }
            }
            break;
            case ARC_WAVE_SEL:
            case ARC_WAVE_SEL + 0x10: {
                int num = idx & 7;
                int base = (idx - ARC_WAVE_SEL) & 0xff;
                if ((num < 6) && (base < 22)) {
//#if defined(OPLTYPE_IS_OPL3)
                    int wselBase = secondSet != 0 ? (base + 22) : base; // for easier mapping onto wave_sel[]
                    // change waveForm
                    if ((this.adlibReg[0x105] & 1) != 0)
                        this.waveSel[wselBase] = val & 7; // opl3 mode enabled, all waveforms accessible
                    else this.waveSel[wselBase] = val & 3;
                    Operator op = this.ops[regBase2Modop[wselBase] + ((num < 3) ? 0 : 9)];
                    op.changeWaveform(wselBase, this.waveSel);
//#else
//                    // wave selection enabled, change waveForm
//                    if (this.adlibReg[0x01] & 0x20) {
//                        this.wave_sel[base] = val & 3;
//                        op = this.Op[regBase2Modop[base] + ((num < 3) ? 0 : 9)];
//                        op.changeWaveform(base, this.waveSel);
//                    }
//#endif
                }
            }
            break;
            default:
                break;
            }
        }

        @Override
        public int read(int port) {
            // opl3-detection routines require ret&6 to be zero
//#if defined(OPLTYPE_IS_OPL3)
            if ((port & 1) == 0) {
                return this.status;
            }
            return 0x00;
//#else
//            // opl2 - detection routines require ret & 6 to be 6
//            if ((port & 1) == 0) {
//                return this.status | 6;
//            }
//            return 0xff;
//#endif
        }

        public void writeIndex(int port, int val) {
            this.oplIndex = val;
//#if defined(OPLTYPE_IS_OPL3)
            if ((port & 3) != 0) {
                // possibly second set
                if (((this.adlibReg[0x105] & 1) != 0) || (this.oplIndex == 5)) this.oplIndex |= ARC_SECONDSET;
            }
//#endif
        }

        // be careful with this
        // uses cptr and chanval, outputs into outbufl(/outbufr)
        // for opl3 check if opl3-mode is enabled (which uses stereo panning)
        //
        // Changes by Valley Bell:
        // - Changed to always output to both channels
        // - added parameter "chn" to fix panning for 4-Op channels and the Rhythm Cymbal

        private final int[] vibLut = new int[BLOCKBUF_SIZE];
        private final int[] tremLut = new int[BLOCKBUF_SIZE];

        @Override
        public void update(int[][] sndPtr, int numSamples) {
            int endSamples;

            // second output buffer (right channel for opl3 stereo)
            int[] outBufL = sndPtr[0];
            int[] outBufR = sndPtr[1];

            // vibrato/tremolo lookup tables (Global, to possibly be used by all operators)

            int vibTShift;
            int maxChannel = NUM_CHANNELS;

            int[] vibVal1, vibVal2, vibVal3, vibVal4;
            int[] tremVal1, tremVal2, tremVal3, tremVal4;

//#if defined(OPLTYPE_IS_OPL3)
            if ((this.adlibReg[0x105] & 1) == 0) maxChannel = NUM_CHANNELS / 2;
//#endif

            if (numSamples == 0) {
                for (int curCh = 0; curCh < maxChannel; curCh++) {
                    if ((this.adlibReg[ARC_PERC_MODE] & 0x20) != 0 && (curCh >= 6 && curCh < 9))
                        continue;

//#if defined(OPLTYPE_IS_OPL3)
                    int opP;
                    if (curCh < 9)
                        opP = curCh;
                    else
                        opP = curCh + 9; // second set is operator18-operator35
                    if (this.ops[opP].is4OpAttached)
                        continue;
//#else
//                    opP = curCh;
//#endif

                    if (this.ops[opP + 0].opState == OF_TYPE_ATT)
                        this.ops[opP + 0].checkEgAttack();
                    if (this.ops[opP + 9].opState == OF_TYPE_ATT)
                        this.ops[opP + 9].checkEgAttack();
                }

                return;
            }

            for (int curSmp = 0; curSmp < numSamples; curSmp += endSamples) {
                endSamples = numSamples - curSmp;

                for (int i = 0; i < endSamples; i++) {
                    outBufL[i] = 0;
//#if defined(OPLTYPE_IS_OPL3)
                    outBufR[i] = 0;
//#endif
                }

                // calculate vibrato/tremolo lookup tables
                vibTShift = ((this.adlibReg[ARC_PERC_MODE] & 0x40) == 0) ? 1 : 0; // 14cents/7cents switching
                for (int i = 0; i < endSamples; i++) {
                    // cycle through vibrato table
                    this.vibTabPos += this.vibTabAdd;
                    if (this.vibTabPos / FIXEDPT_LFO >= VIBTAB_SIZE)
                        this.vibTabPos -= VIBTAB_SIZE * FIXEDPT_LFO;
                    vibLut[i] = Operator.vibTable[this.vibTabPos / FIXEDPT_LFO] >> vibTShift; // 14cents (14/100 of a semitone) or 7cents

                    // cycle through tremolo table
                    this.tremTabPos += this.tremTabAdd;
                    if (this.tremTabPos / FIXEDPT_LFO >= TREMTAB_SIZE)
                        this.tremTabPos -= TREMTAB_SIZE * FIXEDPT_LFO;
                    if ((this.adlibReg[ARC_PERC_MODE] & 0x80) != 0)
                        tremLut[i] = Operator.tremTable[this.tremTabPos / FIXEDPT_LFO];
                    else
                        tremLut[i] = Operator.tremTable[TREMTAB_SIZE + this.tremTabPos / FIXEDPT_LFO];
                }

                if ((this.adlibReg[ARC_PERC_MODE] & 0x20) != 0) {
                    if ((this.muteChn[NUM_CHANNELS + 0]) == 0) {
                        // BassDrum
                        int opP = 6;
                        if ((this.adlibReg[ARC_FEEDBACK + 6] & 1) != 0) {
                            // additive synthesis
                            if (this.ops[opP + 9].opState != OF_TYPE_OFF) {
                                if (this.ops[opP + 9].vibrato) {
                                    vibVal1 = vibValVar1;
                                    for (int i = 0; i < endSamples; i++)
                                        vibVal1[i] = (vibLut[i] * this.ops[opP + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                                } else
                                    vibVal1 = Operator.vibValConst;
                                if (this.ops[opP + 9].tremolo)
                                    tremVal1 = tremLut; // tremolo enabled, use table
                                else
                                    tremVal1 = Operator.tremValConst;

                                // calculate channel output
                                for (int i = 0; i < endSamples; i++) {

                                    this.ops[opP + 9].advance(vibVal1[i], this.generatorAdd);
                                    opFuncs[this.ops[opP + 9].opState].accept(this.ops[opP + 9]);

                                    this.ops[opP + 9].output(0, tremVal1[i]);
                                    int chanVal = this.ops[opP + 9].cVal * 2;

                                    if ((this.adlibReg[0x105] & 1) != 0) {
                                        outBufL[i] += chanVal * this.ops[opP].leftPan;
                                        outBufR[i] += chanVal * this.ops[opP].rightPan;
                                    } else {
                                        outBufL[i] += chanVal;
                                        outBufR[i] += chanVal;
                                    }
                                }
                            }
                        } else {
                            // frequency modulation
                            if ((this.ops[opP + 9].opState != OF_TYPE_OFF) || (this.ops[opP + 0].opState != OF_TYPE_OFF)) {
                                if ((this.ops[opP + 0].vibrato) && (this.ops[opP + 0].opState != OF_TYPE_OFF)) {
                                    vibVal1 = vibValVar1;
                                    for (int i = 0; i < endSamples; i++)
                                        vibVal1[i] = (vibLut[i] * this.ops[opP + 0].freqHigh / 8) * FIXEDPT * VIBFAC;
                                } else
                                    vibVal1 = Operator.vibValConst;
                                if ((this.ops[opP + 9].vibrato) && (this.ops[opP + 9].opState != OF_TYPE_OFF)) {
                                    vibVal2 = vibValVar2;
                                    for (int i = 0; i < endSamples; i++)
                                        vibVal2[i] = (vibLut[i] * this.ops[opP + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                                } else
                                    vibVal2 = Operator.vibValConst;
                                if (this.ops[opP + 0].tremolo)
                                    tremVal1 = tremLut; // tremolo enabled, use table
                                else
                                    tremVal1 = Operator.tremValConst;
                                if (this.ops[opP + 9].tremolo)
                                    tremVal2 = tremLut; // tremolo enabled, use table
                                else
                                    tremVal2 = Operator.tremValConst;

                                // calculate channel output
                                for (int i = 0; i < endSamples; i++) {
                                    this.ops[opP + 0].advance(vibVal1[i], this.generatorAdd);
                                    opFuncs[this.ops[opP + 0].opState].accept(this.ops[opP + 0]);

                                    this.ops[opP + 0].output((this.ops[opP + 0].lastCVal + this.ops[opP + 0].cVal) * this.ops[opP + 0].mfbi / 2, tremVal1[i]);

                                    this.ops[opP + 9].advance(vibVal2[i], this.generatorAdd);
                                    opFuncs[this.ops[opP + 9].opState].accept(this.ops[opP + 9]);

                                    this.ops[opP + 9].output(this.ops[opP + 0].cVal * FIXEDPT, tremVal2[i]);

                                    int chanVal = this.ops[opP + 9].cVal * 2;

                                    if ((this.adlibReg[0x105] & 1) != 0) {
                                        outBufL[i] += chanVal * this.ops[opP + 0].leftPan;
                                        outBufR[i] += chanVal * this.ops[opP + 0].rightPan;
                                    } else {
                                        outBufL[i] += chanVal;
                                        outBufR[i] += chanVal;
                                    }
                                }
                            }
                        }
                    } // end if (! Muted)

                    // TomTom (j=8)
                    if ((this.muteChn[NUM_CHANNELS + 2] == 0) && this.ops[8].opState != OF_TYPE_OFF) {
                        int opP = 8;
                        if (this.ops[opP].vibrato) {
                            vibVal3 = vibValVar1;
                            for (int i = 0; i < endSamples; i++)
                                vibVal3[i] = (vibLut[i] * this.ops[opP].freqHigh / 8) * FIXEDPT * VIBFAC;
                        } else
                            vibVal3 = Operator.vibValConst;

                        if (this.ops[opP].tremolo)
                            tremVal3 = tremLut; // tremolo enabled, use table
                        else
                            tremVal3 = Operator.tremValConst;

                        // calculate channel output
                        for (int i = 0; i < endSamples; i++) {
                            this.ops[opP].advance(vibVal3[i], this.generatorAdd);
                            opFuncs[this.ops[opP].opState].accept(this.ops[opP]); // TomTom

                            this.ops[opP].output(0, tremVal3[i]);
                            int chanVal = this.ops[opP].cVal * 2;

                            if ((this.adlibReg[0x105] & 1) != 0) {
                                outBufL[i] += chanVal * this.ops[opP + 0].leftPan;
                                outBufR[i] += chanVal * this.ops[opP + 0].rightPan;
                            } else {
                                outBufL[i] += chanVal;
                                outBufR[i] += chanVal;
                            }
                        }
                    }

                    // Snare/Hihat (j=7), Cymbal (j=8)
                    if ((this.ops[7].opState != OF_TYPE_OFF) || (this.ops[16].opState != OF_TYPE_OFF) ||
                            (this.ops[17].opState != OF_TYPE_OFF)) {
                        int opP = 7;
                        if ((this.ops[opP + 0].vibrato) && (this.ops[opP + 0].opState != OF_TYPE_OFF)) {
                            vibVal1 = vibValVar1;
                            for (int i = 0; i < endSamples; i++)
                                vibVal1[i] = (vibLut[i] * this.ops[opP + 0].freqHigh / 8) * FIXEDPT * VIBFAC;
                        } else
                            vibVal1 = Operator.vibValConst;
                        if ((this.ops[opP + 9].vibrato) && (this.ops[opP + 9].opState == OF_TYPE_OFF)) {
                            vibVal2 = vibValVar2;
                            for (int i = 0; i < endSamples; i++)
                                vibVal2[i] = (vibLut[i] * this.ops[opP + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                        } else
                            vibVal2 = Operator.vibValConst;

                        if (this.ops[opP + 0].tremolo)
                            tremVal1 = tremLut; // tremolo enabled, use table
                        else
                            tremVal1 = Operator.tremValConst;
                        if (this.ops[opP + 9].tremolo)
                            tremVal2 = tremLut; // tremolo enabled, use table
                        else
                            tremVal2 = Operator.tremValConst;

                        opP = 8;
                        if ((this.ops[opP + 9].vibrato) && (this.ops[opP + 9].opState == OF_TYPE_OFF)) {
                            vibVal4 = vibValVar2;
                            for (int i = 0; i < endSamples; i++)
                                vibVal4[i] = (vibLut[i] * this.ops[opP + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                        } else
                            vibVal4 = Operator.vibValConst;

                        if (this.ops[opP + 9].tremolo) tremVal4 = tremLut; // tremolo enabled, use table
                        else tremVal4 = Operator.tremValConst;

                        // calculate channel output
                        for (int i = 0; i < endSamples; i++) {
                            Operator.advanceDrums(this.ops[7], vibVal1[i], this.ops[7 + 9], vibVal2[i], this.ops[8 + 9], vibVal4[i], this.generatorAdd);

                            if ((this.muteChn[NUM_CHANNELS + 4]) == 0) {
                                opFuncs[this.ops[7].opState].accept(this.ops[7]); // Hihat

                                this.ops[7].output(0, tremVal1[i]);
                            } else
                                this.ops[7].cVal = 0;

                            if ((this.muteChn[NUM_CHANNELS + 1]) == 0) {
                                opFuncs[this.ops[7 + 9].opState].accept(this.ops[7 + 9]); // Snare

                                this.ops[7 + 9].output(0, tremVal2[i]);
                            } else
                                this.ops[7 + 9].cVal = 0;

                            if ((this.muteChn[NUM_CHANNELS + 3]) == 0) {
                                opFuncs[this.ops[8 + 9].opState].accept(this.ops[8 + 9]); // Cymbal

                                this.ops[8 + 9].output(0, tremVal4[i]);
                            } else
                                this.ops[8 + 9].cVal = 0;

                            // fix panning of the snare -Valley Bell
                            int chanVal = (this.ops[7].cVal + this.ops[7 + 9].cVal) * 2;

                            if ((this.adlibReg[0x105] & 1) != 0) {
                                outBufL[i] += chanVal * this.ops[0 + 7].leftPan;
                                outBufR[i] += chanVal * this.ops[0 + 7].rightPan;
                            } else {
                                outBufL[i] += chanVal;
                                outBufR[i] += chanVal;
                            }

                            chanVal = this.ops[8 + 9].cVal * 2;

                            if ((this.adlibReg[0x105] & 1) != 0) {
                                outBufL[i] += chanVal * this.ops[0 + 8].leftPan;
                                outBufR[i] += chanVal * this.ops[0 + 8].rightPan;
                            } else {
                                outBufL[i] += chanVal;
                                outBufR[i] += chanVal;
                            }
                        }
                    }
                }

                for (int curCh = maxChannel - 1; curCh >= 0; curCh--) {

                    if (this.muteChn[curCh] != 0)
                        continue;

                    // skip drum/percussion operators
                    if ((this.adlibReg[ARC_PERC_MODE] & 0x20) != 0 && (curCh >= 6) && (curCh < 9)) continue;

                    int opP;
                    int k = curCh;
                    Operator op;
//#if defined(OPLTYPE_IS_OPL3)
                    if (curCh < 9) {
                        op = this.ops[curCh];
                        opP = curCh;
                    } else {
                        op = this.ops[curCh + 9]; // second set is operator18-operator35
                        opP = curCh + 9;
                        k += (-9 + 256); // second set uses registers 0x100 onwards
                    }
                    // check if this Operator is part of a 4-Op
                    if (op.is4OpAttached) continue; // this is more correct
//#else
//                    op = this.ops[curCh];
//#endif

                    // check for FM/AM
                    if ((this.adlibReg[ARC_FEEDBACK + k] & 1) != 0) {
//#if defined(OPLTYPE_IS_OPL3)
                        if (op.is4Op) { // this is more correct
                            if ((this.adlibReg[ARC_FEEDBACK + k + 3] & 1) != 0) {
                                // AM-AM-style synthesis (op1[fb] + (op2 * op3) + op4)
                                if (op.opState != OF_TYPE_OFF) {
                                    if (op.vibrato) {
                                        vibVal1 = vibValVar1;
                                        for (int i = 0; i < endSamples; i++)
                                            vibVal1[i] = (vibLut[i] * op.freqHigh / 8) * FIXEDPT * VIBFAC;
                                    } else
                                        vibVal1 = Operator.vibValConst;
                                    if (op.tremolo)
                                        tremVal1 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal1 = Operator.tremValConst;

                                    // calculate channel output
                                    for (int i = 0; i < endSamples; i++) {
                                        op.advance(vibVal1[i], this.generatorAdd);
                                        opFuncs[op.opState].accept(op);
                                        op.output((op.lastCVal + op.cVal) * op.mfbi / 2, tremVal1[i]);

                                        int chanVal = op.cVal;
                                        if ((this.adlibReg[0x105] & 1) != 0) {
                                            outBufL[i] += chanVal * this.ops[opP + 3].leftPan;
                                            outBufR[i] += chanVal * this.ops[opP + 3].rightPan;
                                        } else {
                                            outBufL[i] += chanVal;
                                            outBufR[i] += chanVal;
                                        }
                                    }
                                }

                                if ((this.ops[opP + 3].opState != OF_TYPE_OFF) || (this.ops[opP + 9].opState != OF_TYPE_OFF)) {
                                    if ((this.ops[opP + 9].vibrato) && (this.ops[opP + 9].opState != OF_TYPE_OFF)) {
                                        vibVal1 = vibValVar1;
                                        for (int i = 0; i < endSamples; i++)
                                            vibVal1[i] = (vibLut[i] * this.ops[opP + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                                    } else
                                        vibVal1 = Operator.vibValConst;
                                    if (this.ops[opP + 9].tremolo)
                                        tremVal1 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal1 = Operator.tremValConst;
                                    if (this.ops[opP + 3].tremolo)
                                        tremVal2 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal2 = Operator.tremValConst;

                                    // calculate channel output
                                    for (int i = 0; i < endSamples; i++) {
                                        this.ops[opP + 9].advance(vibVal1[i], this.generatorAdd);
                                        opFuncs[this.ops[opP + 9].opState].accept(this.ops[opP + 9]);
                                        this.ops[opP + 9].output(0, tremVal1[i]);

                                        this.ops[opP + 3].advance(0, this.generatorAdd);
                                        opFuncs[this.ops[opP + 3].opState].accept(this.ops[opP + 3]);
                                        this.ops[opP + 3].output(this.ops[opP + 9].cVal * FIXEDPT, tremVal2[i]);

                                        int chanVal = this.ops[opP + 3].cVal;
                                        if ((this.adlibReg[0x105] & 1) != 0) {
                                            outBufL[i] += chanVal * this.ops[opP + 3].leftPan;
                                            outBufR[i] += chanVal * this.ops[opP + 3].rightPan;
                                        } else {
                                            outBufL[i] += chanVal;
                                            outBufR[i] += chanVal;
                                        }
                                    }
                                }

                                if (this.ops[opP + 3 + 9].opState != OF_TYPE_OFF) {
                                    if (this.ops[opP + 3 + 9].tremolo)
                                        tremVal1 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal1 = Operator.tremValConst;

                                    // calculate channel output
                                    for (int i = 0; i < endSamples; i++) {
                                        this.ops[opP + 3 + 9].advance(0, this.generatorAdd);
                                        opFuncs[this.ops[opP + 3 + 9].opState].accept(this.ops[opP + 3 + 9]);
                                        this.ops[opP + 3 + 9].output(0, tremVal1[i]);

                                        int chanVal = this.ops[opP + 3 + 9].cVal;
                                        if ((this.adlibReg[0x105] & 1) != 0) {
                                            outBufL[i] += chanVal * this.ops[opP + 3].leftPan;
                                            outBufR[i] += chanVal * this.ops[opP + 3].rightPan;
                                        } else {
                                            outBufL[i] += chanVal;
                                            outBufR[i] += chanVal;
                                        }
                                    }
                                }
                            } else {
                                // AM-FM-style synthesis (op1[fb] + (op2 * op3 * op4))
                                if (this.ops[opP + 0].opState != OF_TYPE_OFF) {
                                    if (this.ops[opP + 0].vibrato) {
                                        vibVal1 = vibValVar1;
                                        for (int i = 0; i < endSamples; i++)
                                            vibVal1[i] = (vibLut[i] * this.ops[opP + 0].freqHigh / 8) * FIXEDPT * VIBFAC;
                                    } else
                                        vibVal1 = Operator.vibValConst;
                                    if (this.ops[opP + 0].tremolo)
                                        tremVal1 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal1 = Operator.tremValConst;

                                    // calculate channel output
                                    for (int i = 0; i < endSamples; i++) {
                                        this.ops[opP + 0].advance(vibVal1[i], this.generatorAdd);
                                        opFuncs[this.ops[opP + 0].opState].accept(this.ops[opP + 0]);
                                        this.ops[opP + 0].output((this.ops[opP + 0].lastCVal + this.ops[opP + 0].cVal) * this.ops[opP + 0].mfbi / 2, tremVal1[i]);

                                        int chanVal = this.ops[opP + 0].cVal;
                                        if ((this.adlibReg[0x105] & 1) != 0) {
                                            outBufL[i] += chanVal * this.ops[opP + 3].leftPan;
                                            outBufR[i] += chanVal * this.ops[opP + 3].rightPan;
                                        } else {
                                            outBufL[i] += chanVal;
                                            outBufR[i] += chanVal;
                                        }
                                    }
                                }

                                if ((this.ops[opP + 9].opState != OF_TYPE_OFF) || (this.ops[opP + 3].opState != OF_TYPE_OFF) || (this.ops[opP + 3 + 9].opState != OF_TYPE_OFF)) {
                                    if ((this.ops[opP + 9].vibrato) && (this.ops[opP + 9].opState != OF_TYPE_OFF)) {
                                        vibVal1 = vibValVar1;
                                        for (int i = 0; i < endSamples; i++)
                                            vibVal1[i] = (vibLut[i] * this.ops[opP + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                                    } else
                                        vibVal1 = Operator.vibValConst;
                                    if (this.ops[opP + 9].tremolo)
                                        tremVal1 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal1 = Operator.tremValConst;
                                    if (this.ops[opP + 3].tremolo)
                                        tremVal2 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal2 = Operator.tremValConst;
                                    if (this.ops[opP + 3 + 9].tremolo)
                                        tremVal3 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal3 = Operator.tremValConst;

                                    // calculate channel output
                                    for (int i = 0; i < endSamples; i++) {
                                        this.ops[opP + 9].advance(vibVal1[i], this.generatorAdd);
                                        opFuncs[this.ops[opP + 9].opState].accept(this.ops[opP + 9]);
                                        this.ops[opP + 9].output(0, tremVal1[i]);

                                        this.ops[opP + 3].advance(0, this.generatorAdd);
                                        opFuncs[this.ops[opP + 3].opState].accept(this.ops[opP + 3]);
                                        this.ops[opP + 3].output(this.ops[opP + 9].cVal * FIXEDPT, tremVal2[i]);

                                        this.ops[opP + 3 + 9].advance(0, this.generatorAdd);
                                        opFuncs[this.ops[opP + 3 + 9].opState].accept(this.ops[opP + 3 + 9]);
                                        this.ops[opP + 3 + 9].output(this.ops[opP + 3].cVal * FIXEDPT, tremVal3[i]);

                                        int chanVal = this.ops[opP + 3 + 9].cVal;
                                        if ((this.adlibReg[0x105] & 1) != 0) {
                                            outBufL[i] += chanVal * this.ops[opP + 3].leftPan;
                                            outBufR[i] += chanVal * this.ops[opP + 3].rightPan;
                                        } else {
                                            outBufL[i] += chanVal;
                                            outBufR[i] += chanVal;
                                        }
                                    }
                                }
                            }
                            continue;
                        }
//#endif
                        // 2op additive synthesis
                        if ((this.ops[opP + 9].opState == OF_TYPE_OFF) && (this.ops[opP + 0].opState == OF_TYPE_OFF))
                            continue;
                        if ((this.ops[opP + 0].vibrato) && (this.ops[opP + 0].opState != OF_TYPE_OFF)) {
                            vibVal1 = vibValVar1;
                            for (int i = 0; i < endSamples; i++)
                                vibVal1[i] = (vibLut[i] * this.ops[opP + 0].freqHigh / 8) * FIXEDPT * VIBFAC;
                        } else
                            vibVal1 = Operator.vibValConst;
                        if ((this.ops[opP + 9].vibrato) && (this.ops[opP + 9].opState != OF_TYPE_OFF)) {
                            vibVal2 = vibValVar2;
                            for (int i = 0; i < endSamples; i++)
                                vibVal2[i] = (vibLut[i] * this.ops[opP + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                        } else
                            vibVal2 = Operator.vibValConst;
                        if (this.ops[opP + 0].tremolo)
                            tremVal1 = tremLut; // tremolo enabled, use table
                        else
                            tremVal1 = Operator.tremValConst;
                        if (this.ops[opP + 9].tremolo)
                            tremVal2 = tremLut; // tremolo enabled, use table
                        else
                            tremVal2 = Operator.tremValConst;

                        // calculate channel output
                        for (int i = 0; i < endSamples; i++) {
                            // carrier1
                            this.ops[opP + 0].advance(vibVal1[i], this.generatorAdd);
                            opFuncs[this.ops[opP + 0].opState].accept(this.ops[opP + 0]);

                            this.ops[opP + 0].output((this.ops[opP + 0].lastCVal + this.ops[opP + 0].cVal) * this.ops[opP + 0].mfbi / 2, tremVal1[i]);

                            // carrier2
                            this.ops[opP + 9].advance(vibVal2[i], this.generatorAdd);
                            opFuncs[this.ops[opP + 9].opState].accept(this.ops[opP + 9]);

                            this.ops[opP + 9].output(0, tremVal2[i]);

                            int chanVal = this.ops[opP + 9].cVal + this.ops[opP + 0].cVal;

                            if ((this.adlibReg[0x105] & 1) != 0) {
                                outBufL[i] += chanVal * this.ops[opP + 0].leftPan;
                                outBufR[i] += chanVal * this.ops[opP + 0].rightPan;
                            } else {
                                outBufL[i] += chanVal;
                                outBufR[i] += chanVal;
                            }
                        }
                    } else {
//#if defined(OPLTYPE_IS_OPL3)
                        if (op.is4Op) { // this is more correct
                            if ((this.adlibReg[ARC_FEEDBACK + k + 3] & 1) != 0) {
                                // FM-AM-style synthesis ((op1[fb] * op2) + (op3 * op4))
                                if ((this.ops[opP + 0].opState != OF_TYPE_OFF) || (this.ops[opP + 9].opState != OF_TYPE_OFF)) {
                                    if ((this.ops[opP + 0].vibrato) && (this.ops[opP + 0].opState != OF_TYPE_OFF)) {
                                        vibVal1 = vibValVar1;
                                        for (int i = 0; i < endSamples; i++)
                                            vibVal1[i] = (vibLut[i] * this.ops[opP + 0].freqHigh / 8) * FIXEDPT * VIBFAC;
                                    } else
                                        vibVal1 = Operator.vibValConst;
                                    if ((this.ops[opP + 9].vibrato) && (this.ops[opP + 9].opState != OF_TYPE_OFF)) {
                                        vibVal2 = vibValVar2;
                                        for (int i = 0; i < endSamples; i++)
                                            vibVal2[i] = (vibLut[i] * this.ops[opP + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                                    } else
                                        vibVal2 = Operator.vibValConst;
                                    if (this.ops[opP + 0].tremolo)
                                        tremVal1 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal1 = Operator.tremValConst;
                                    if (this.ops[opP + 9].tremolo)
                                        tremVal2 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal2 = Operator.tremValConst;

                                    // calculate channel output
                                    for (int i = 0; i < endSamples; i++) {
                                        this.ops[opP + 0].advance(vibVal1[i], this.generatorAdd);
                                        opFuncs[this.ops[opP + 0].opState].accept(this.ops[opP + 0]);

                                        this.ops[opP + 0].output((this.ops[opP + 0].lastCVal + this.ops[opP + 0].cVal) * this.ops[opP + 0].mfbi / 2, tremVal1[i]);

                                        this.ops[opP + 9].advance(vibVal2[i], this.generatorAdd);
                                        opFuncs[this.ops[opP + 9].opState].accept(this.ops[opP + 9]);

                                        this.ops[opP + 9].output(this.ops[opP + 0].cVal * FIXEDPT, tremVal2[i]);

                                        int chanVal = this.ops[opP + 9].cVal;
                                        if ((this.adlibReg[0x105] & 1) != 0) {
                                            outBufL[i] += chanVal * this.ops[opP + 3].leftPan;
                                            outBufR[i] += chanVal * this.ops[opP + 3].rightPan;
                                        } else {
                                            outBufL[i] += chanVal;
                                            outBufR[i] += chanVal;
                                        }
                                    }
                                }

                                if ((this.ops[opP + 3].opState != OF_TYPE_OFF) || (this.ops[opP + 3 + 9].opState != OF_TYPE_OFF)) {
                                    if (this.ops[opP + 3].tremolo)
                                        tremVal1 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal1 = Operator.tremValConst;
                                    if (this.ops[opP + 3 + 9].tremolo)
                                        tremVal2 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal2 = Operator.tremValConst;

                                    // calculate channel output
                                    for (int i = 0; i < endSamples; i++) {
                                        this.ops[opP + 3].advance(0, this.generatorAdd);
                                        opFuncs[this.ops[opP + 3].opState].accept(this.ops[opP + 3]);
                                        this.ops[opP + 3].output(0, tremVal1[i]);

                                        this.ops[opP + 3 + 9].advance(0, this.generatorAdd);
                                        opFuncs[this.ops[opP + 3 + 9].opState].accept(this.ops[opP + 3 + 9]);
                                        this.ops[opP + 3 + 9].output(this.ops[opP + 3].cVal * FIXEDPT, tremVal2[i]);

                                        int chanVal = this.ops[opP + 3 + 9].cVal;
                                        if ((this.adlibReg[0x105] & 1) != 0) {
                                            outBufL[i] += chanVal * this.ops[opP + 3].leftPan;
                                            outBufR[i] += chanVal * this.ops[opP + 3].rightPan;
                                        } else {
                                            outBufL[i] += chanVal;
                                            outBufR[i] += chanVal;
                                        }
                                    }
                                }
                            } else {
                                // FM-FM-style synthesis (op1[fb] * op2 * op3 * op4)
                                if ((this.ops[opP + 0].opState != OF_TYPE_OFF) || (this.ops[opP + 9].opState != OF_TYPE_OFF) ||
                                        (this.ops[opP + 3].opState != OF_TYPE_OFF) || (this.ops[opP + 3 + 9].opState != OF_TYPE_OFF)) {
                                    if ((this.ops[opP + 0].vibrato) && (this.ops[opP + 0].opState != OF_TYPE_OFF)) {
                                        vibVal1 = vibValVar1;
                                        for (int i = 0; i < endSamples; i++)
                                            vibVal1[i] = (vibLut[i] * this.ops[opP + 0].freqHigh / 8) * FIXEDPT * VIBFAC;
                                    } else
                                        vibVal1 = Operator.vibValConst;
                                    if ((this.ops[opP + 9].vibrato) && (this.ops[opP + 9].opState != OF_TYPE_OFF)) {
                                        vibVal2 = vibValVar2;
                                        for (int i = 0; i < endSamples; i++)
                                            vibVal2[i] = (vibLut[i] * this.ops[opP + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                                    } else
                                        vibVal2 = Operator.vibValConst;
                                    if (this.ops[opP + 0].tremolo)
                                        tremVal1 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal1 = Operator.tremValConst;
                                    if (this.ops[opP + 9].tremolo)
                                        tremVal2 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal2 = Operator.tremValConst;
                                    if (this.ops[opP + 3].tremolo)
                                        tremVal3 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal3 = Operator.tremValConst;
                                    if (this.ops[opP + 3 + 9].tremolo)
                                        tremVal4 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal4 = Operator.tremValConst;

                                    // calculate channel output
                                    for (int i = 0; i < endSamples; i++) {
                                        this.ops[opP + 0].advance(vibVal1[i], this.generatorAdd);
                                        opFuncs[this.ops[opP + 0].opState].accept(this.ops[opP + 0]);
                                        this.ops[opP + 0].output((this.ops[opP + 0].lastCVal + this.ops[opP + 0].cVal) * this.ops[opP + 0].mfbi / 2, tremVal1[i]);

                                        this.ops[opP + 9].advance(vibVal2[i], this.generatorAdd);
                                        opFuncs[this.ops[opP + 9].opState].accept(this.ops[opP + 9]);
                                        this.ops[opP + 9].output(this.ops[opP + 0].cVal * FIXEDPT, tremVal2[i]);

                                        this.ops[opP + 3].advance(0, this.generatorAdd);
                                        opFuncs[this.ops[opP + 3].opState].accept(this.ops[opP + 3]);
                                        this.ops[opP + 3].output(this.ops[opP + 9].cVal * FIXEDPT, tremVal3[i]);

                                        this.ops[opP + 3 + 9].advance(0, this.generatorAdd);
                                        opFuncs[this.ops[opP + 3 + 9].opState].accept(this.ops[opP + 3 + 9]);
                                        this.ops[opP + 3 + 9].output(this.ops[opP + 3].cVal * FIXEDPT, tremVal4[i]);

                                        int chanVal = this.ops[opP + 3 + 9].cVal;
                                        if ((this.adlibReg[0x105] & 1) != 0) {
                                            outBufL[i] += chanVal * this.ops[opP + 3].leftPan;
                                            outBufR[i] += chanVal * this.ops[opP + 3].rightPan;
                                        } else {
                                            outBufL[i] += chanVal;
                                            outBufR[i] += chanVal;
                                        }
                                    }
                                }
                            }
                            continue;
                        }
//#endif
                        // 2op frequency modulation
                        if ((this.ops[opP + 9].opState == OF_TYPE_OFF) && (this.ops[opP + 0].opState == OF_TYPE_OFF))
                            continue;
                        if ((this.ops[opP + 0].vibrato) && (this.ops[opP + 0].opState != OF_TYPE_OFF)) {
                            vibVal1 = vibValVar1;
                            for (int i = 0; i < endSamples; i++)
                                vibVal1[i] = (vibLut[i] * this.ops[opP + 0].freqHigh / 8) * FIXEDPT * VIBFAC;
                        } else
                            vibVal1 = Operator.vibValConst;
                        if ((this.ops[opP + 9].vibrato) && (this.ops[opP + 9].opState != OF_TYPE_OFF)) {
                            vibVal2 = vibValVar2;
                            for (int i = 0; i < endSamples; i++)
                                vibVal2[i] = (vibLut[i] * this.ops[opP + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                        } else
                            vibVal2 = Operator.vibValConst;
                        if (this.ops[opP + 0].tremolo)
                            tremVal1 = tremLut; // tremolo enabled, use table
                        else
                            tremVal1 = Operator.tremValConst;
                        if (this.ops[opP + 9].tremolo)
                            tremVal2 = tremLut; // tremolo enabled, use table
                        else
                            tremVal2 = Operator.tremValConst;

                        // calculate channel output
                        for (int i = 0; i < endSamples; i++) {
                            // modulator
                            this.ops[opP + 0].advance(vibVal1[i], this.generatorAdd);
                            opFuncs[this.ops[opP + 0].opState].accept(this.ops[opP + 0]);

                            this.ops[opP + 0].output((this.ops[opP + 0].lastCVal + this.ops[opP + 0].cVal) * this.ops[opP + 0].mfbi / 2, tremVal1[i]);

                            // carrier
                            this.ops[opP + 9].advance(vibVal2[i], this.generatorAdd);
                            opFuncs[this.ops[opP + 9].opState].accept(this.ops[opP + 9]);

                            this.ops[opP + 9].output(this.ops[opP + 0].cVal * FIXEDPT, tremVal2[i]);

                            int chanVal = this.ops[opP + 9].cVal;

                            if ((this.adlibReg[0x105] & 1) != 0) {
                                outBufL[i] += chanVal * this.ops[opP + 0].leftPan;
                                outBufR[i] += chanVal * this.ops[opP + 0].rightPan;
                            } else {
                                outBufL[i] += chanVal;
                                outBufR[i] += chanVal;
                            }
                        }
                    }
                }

//#if defined(OPLTYPE_IS_OPL3)
//                if (adlibreg[0x105] & 1) {
//                    // convert to 16bit samples (stereo)
//                    for (i = 0; i < endSamples; i++) {
//                        clipit16(outBufL[i], sndPtr++);
//                        clipit16(outBufR[i], sndPtr++);
//                    }
//                } else {
//                    // convert to 16bit samples (mono)
//                    for (i = 0; i < endSamples; i++) {
//                        clipit16(outBufL[i], sndPtr++);
//                        clipit16(outBufL[i], sndPtr++);
//                    }
//                }
//#else
//                // convert to 16bit samples
//                for (i = 0; i < endsamples; i++)
//                    clipit16(outBufL[i], sndptr++);
//#endif

//logger.log(Level.TRACE, "bufl:%d bufr:%d".formatted(outBufL[curSmp], outBufR[curSmp]));
            }
        }

        @Override
        public void setMuteMask(int muteMask) {
            for (int curChn = 0; curChn < NUM_CHANNELS + 5; curChn++)
                this.muteChn[curChn] = (muteMask >> curChn) & 0x01;
        }
    }
}
