/*
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

package mdsound;

import java.util.Arrays;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import mdsound.Common.TriConsumer;


/*
 * 262intf.c
 *
 * MAME interface for YMF262 (Opl3) emulator
 */
public class YmF262 extends Instrument.BaseInstrument {
    @Override
    public void reset(byte chipId) {
        device_reset_ymf262(chipId);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipId, int clock) {
        return device_start_ymf262(chipId, 14318180);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        return device_start_ymf262(chipId, clockValue);
    }

    @Override
    public void stop(byte chipId) {
        device_stop_ymf262(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        ymf262_stream_update(chipId, outputs, samples);

        //common.write("output %d %d", outputs[0][0], outputs[1][0]);
        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private int YMF262_Write(byte chipId, int adr, byte data) {
        ymf262_w(chipId, (adr & 0x100) != 0 ? 0x02 : 0x00, (byte) (adr & 0xff));
        ymf262_w(chipId, (adr & 0x100) != 0 ? 0x03 : 0x01, data);
        return 0;
    }

    public static class Ymf262State {
        private final boolean OPLTYPE_IS_OPL3 = true;

        // DosBox OPL (AdLibEmu)
        private final byte EC_DBOPL = 0x00;
        // YMF262 core from MAME
        private final byte EC_MAME = 0x01;

        public Object chip;

        private byte emuCore = 0x00;

        public void setEmuCore(byte emulator) {
            emuCore = (byte) ((emulator < 0x02) ? emulator : 0x00);
        }

        private void irqHandler(int irq) {
        }

        private void timerHandler(int timer, int period) {
            if (period == 0) { // Reset FM Timer
                //timer_enable(info.timer[timer], 0);
            } else { // Start FM Timer
                //timer_adjust_oneshot(info.timer[timer], period, 0);
            }
        }

        public void update(int[][] outputs, int samples) {
            switch (emuCore) {
            case EC_MAME -> ((Opl3) chip).update(outputs, samples);
            case EC_DBOPL -> ((Opl2) chip).update(outputs, samples);
            }
        }

        private int[][] dummyBuf = new int[][] {null, null};

        private void updateHandler() {
            switch (emuCore) {
            case EC_MAME -> ((Opl3) chip).update(dummyBuf, 0);
            case EC_DBOPL -> ((Opl2) chip).update(dummyBuf, 0);
            }
        }

        public int start(int clock, int rate) {
            switch (emuCore) {
            case EC_MAME:
                chip = new Opl3(clock, rate, Opl3.OPL3_TYPE_YMF262);
                //assert_always(info.chip != NULL, "Error creating YMF262 chip");

                //info.stream = stream_create(device,0,4,rate,info,ymf262_stream_update);

                // YMF262 setup
                //ymf262_set_timer_handler(info.chip, timer_handler_262, info);
                //ymf262_set_irq_handler(info.chip, IRQHandler_262, info);
                //ymf262_set_update_handler(info.chip, _stream_update, info);

                //info.timer[0] = timer_alloc(device.machine, timer_callback_262_0, info);
                //info.timer[1] = timer_alloc(device.machine, timer_callback_262_1, info);
                break;
            case EC_DBOPL:
                chip = new Opl2(clock, rate, this::updateHandler);
                break;
            }

            return rate;
        }

        public void stop() {
            switch (emuCore) {
            case EC_MAME -> ((Opl3) chip).stop();
            case EC_DBOPL -> ((Opl2) chip).stop();
            }
        }

        public void reset() {
            switch (emuCore) {
            case EC_MAME -> ((Opl3) chip).reset();
            case EC_DBOPL -> ((Opl2) chip).reset();
            }
        }

        public byte read(int offset) {
            return switch (emuCore) {
                case EC_MAME -> ((Opl3) chip).read(offset & 3);
                case EC_DBOPL -> (byte) ((Opl2) chip).readReg(offset & 0x03);
                default -> 0x00;
            };
        }

        public void write(int offset, byte data) {
            switch (emuCore) {
            case EC_MAME:
                ((Opl3) chip).write(offset & 3, data);
                break;
            case EC_DBOPL:
                ((Opl2) chip).writeIO(offset & 3, data);
                break;
            }
        }

        public void setMuteMask(int muteMask) {
            switch (emuCore) {
            case EC_MAME:
                ((Opl3) chip).setMmuteMask(muteMask);
                break;
            case EC_DBOPL:
                ((Opl2) chip).setMuteMask(muteMask);
                break;
            }
        }
    }

    private static final int MAX_CHIPS = 0x02;
    private Ymf262State[] YMF262Data = new Ymf262State[] {new Ymf262State(), new Ymf262State()};

    public void ymf262_stream_update(byte chipId, int[][] outputs, int samples) {
        Ymf262State info = YMF262Data[chipId];
        info.update(outputs, samples);
    }

    public int device_start_ymf262(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        Ymf262State info = YMF262Data[chipId];
        int rate = clock / 288;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;

        //info.intf = device.static_config ? (final ymf262_interface *)device.static_config : &dummy;
        //info.intf = &dummy;
        //info.device = device;

        // stream system initialize
        return info.start(clock, rate);
    }

    public void device_stop_ymf262(byte chipId) {
        Ymf262State info = YMF262Data[chipId];
        info.stop();
    }

    /** reset */
    public void device_reset_ymf262(byte chipId) {
        Ymf262State info = YMF262Data[chipId];
        info.reset();
    }

    public byte ymf262_r(byte chipId, int offset) {
        Ymf262State info = YMF262Data[chipId];
        return info.read(offset);
    }

    public void ymf262_w(byte chipId, int offset, byte data) {
        Ymf262State info = YMF262Data[chipId];
        if (info.chip == null) return;

        info.write(offset, data);
    }

    public byte ymf262_status_r(byte chipId, int offset) {
        return ymf262_r(chipId, 0);
    }

    public void ymf262_register_a_w(byte chipId, int offset, byte data) {
        ymf262_w(chipId, 0, data);
    }

    public void ymf262_register_b_w(byte chipId, int offset, byte data) {
        ymf262_w(chipId, 2, data);
    }

    public void ymf262_data_a_w(byte chipId, int offset, byte data) {
        ymf262_w(chipId, 1, data);
    }

    public void ymf262_data_b_w(byte chipId, int offset, byte data) {
        ymf262_w(chipId, 3, data);
    }

    public void ymf262_set_emu_core(byte chipId, byte emulator) {
        Ymf262State info = YMF262Data[chipId];
        info.setEmuCore(emulator);
    }

    public void ymf262_set_mute_mask(byte chipId, int muteMask) {
        Ymf262State info = YMF262Data[chipId];
        info.setMuteMask(muteMask);
    }

    /**
     * Generic get_info
     */
    /*DEVICE_GET_INFO( YmF262 ) {
            case DEVINFO_STR_NAME:       strcpy(info.s, "YMF262");       break;
            case DEVINFO_STR_FAMILY:     strcpy(info.s, "Yamaha FM");      break;
            case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");        break;
            case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
        }
    }*/

    /*
    *
    * File: YmF262.c - software implementation of YMF262
    *                  FM Sound generator type Opl3
    *
    * Copyright Jarek Burczynski
    *
    * Version 0.2
    *

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
    7. YMF262 compabile DAC (YAC512) is available.
    8. LFO for vibrato and tremolo effedts.
    9. 2 programable timers.
    10. Shorter register access time compared with YM3812.
    11. 5V single supply silicon gate CMOS process.
    12. 24 Pin SOP Package (YMF262-MPcm), 48 Pin SQFP Package (YMF262-S).

    differences between OPL2 and Opl3 not documented in Yamaha datahasheets:
    - sinus table is a little different: the negative part is off by one...

    - in order to enable selection of four different waveforms on OPL2
    one must set bit 5 in register 0x01(test).
    on Opl3 this bit is ignored and 4-waveform select works *always*.
    (Don't confuse this with Opl3's 8-waveform select.)

    - Envelope Generator: all 15 x rates take zero time on Opl3
    (on OPL2 15 0 and 15 1 rates take some time while 15 2 and 15 3 rates
    take zero time)

    - channel calculations: output of Operator 1 is in perfect sync with
    output of Operator 2 on Opl3; on OPL and OPL2 output of Operator 1
    is always delayed by one sample compared to output of Operator 2

    differences between OPL2 and Opl3 shown in datasheets:
    - YMF262 does not support CSM mode
    */
    public static class Opl3 {

        public interface TimerHandler extends TriConsumer<Ymf262State, Integer, Integer> {
        }

        public interface IrqHandler extends BiConsumer<Ymf262State, Integer> {
        }

        public interface UpdateHandler extends Consumer<Ymf262State> {
        }

        public static class Channel {

            public static class Slot {

                // Envelope Generator phases

                private static final byte EG_ATT = 4;
                private static final byte EG_DEC = 3;
                private static final byte EG_SUS = 2;
                private static final byte EG_REL = 1;
                private static final byte EG_OFF = 0;


                private static int sc(double db) {
                    return (int) (db * (2.0 / ENV_STEP));
                }

                /**
                 * sustain level table (3dB per step)
                 * 0 - 15: 0, 3, 6, 9,12,15,18,21,24,27,30,33,36,39,42,93 (dB)
                 */
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

                        /*12 */ 4, 4, 4, 4, 4, 4, 4, 4, // rates 15 0, 15 1, 15 2, 15 3 for decay
                        /*13 */ 8, 8, 8, 8, 8, 8, 8, 8, // rates 15 0, 15 1, 15 2, 15 3 for attack (zero time)
                        /*14 */ 0, 0, 0, 0, 0, 0, 0, 0, // infinity rates for attack and decay(s)
                };

                /**
                 * note that there is no O(13) in this table - it's directly in the code
                 */
                private static byte o(int a) {
                    return (byte) ((byte) a * RATE_STEPS);
                }

                /**
                 * Envelope Generator rates (16 + 64 rates + 16 RKS)
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

                private static byte o1(int a) {
                    return (byte) ((byte) a * 1);
                }

                /*
                 * Envelope Generator counter shifts (16 + 64 rates + 16 RKS)
                 * <pre>
                 * rate  0,    1,    2,    3,   4,   5,   6,  7,  8,  9,  10, 11, 12, 13, 14, 15
                 * shift 12,   11,   10,   9,   8,   7,   6,  5,  4,  3,  2,  1,  0,  0,  0,  0
                 * mask  4095, 2047, 1023, 511, 255, 127, 63, 31, 15, 7,  3,  1,  0,  0,  0,  0
                 * </pre>
                 */
                private static final byte[] egRateShift = new byte[] {
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
                public int ar;
                /** decay rate:  DR<<2 */
                public int dr;
                /** release rate:RR<<2 */
                public int rr;
                /** key scale rate */
                public byte KSR;
                /** keyscale level */
                public byte ksl;
                /** key scale rate: kcode>>KSR */
                public byte ksr;
                /** multiple: mul_tab[ML] */
                public byte mul;

                // Phase Generator

                /** frequency counter */
                public int cnt;
                /** frequency counter step */
                public int incr;
                /** feedback shift value */
                public byte fb;

                private int calcVolume(int lfoAm) {
                    return tll + volume + (lfoAm & aMmask);
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

                        /* this is important behaviour:
                        one can change percusive/non-percussive modes on the fly and
                        the chip will remain in sustain phase - verified on real YM3812 */

                        if (this.egType != 0) { // non-percussive mode
                            // do nothing
                        } else { // percussive mode
                            // during sustain phase chip adds Release Rate (in percussive mode)
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
//System.err.printf("out0=%5i vol0=%4i ", this.op1_out[1], env );
                }

                public void calc(int lfoAm, int phaseModulation) {
                    int env = this.calcVolume(lfoAm);
                    if (env < ENV_QUIET) {
                        this.connect.setValue(this.connect.getValue() + calcOp(this.cnt, env, phaseModulation, this.waveTable));
                    }
//System.err.printf("out1=%5i vol1=%4i\n", op_calc(this.Cnt, env, this.phase_modulation, this.wavetable), env );
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
                            byte block = (byte) ((blockFNum & 0x1c00) >> 10);
                            this.cnt += (fnTab[blockFNum & 0x03ff] >> (7 - block)) * this.mul;
                        } else { // LFO phase modulation  = zero
                            this.cnt += this.incr;
                        }
                    } else { // LFO phase modulation disabled for this Operator
                        this.cnt += this.incr;
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
                    this.KSR = (byte) ((v & 0x10) != 0 ? 0 : 2);
                    this.egType = (byte) (v & 0x20);
                    this.vib = (byte) (v & 0x40);
                    this.aMmask = (byte) ((v & 0x80) != 0 ? ~0 : 0);
                }

                public void setKslTl(int v) {
                    this.ksl = (byte) Channel.kslShift[v >> 6];
                    this.tl = (v & 0x3f) << (ENV_BITS - 1 - 7); // 7 bits TL (bit 6 = always 0)
                }

                public static class Connect {
                    public Opl3 opl3 = null;
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
                public Connect connect = new Connect();
                /** slot1 output for feedback  */
                public int[] op1Out = new int[2];
                /** connection (algorithm) type  */
                public byte con;

                // Envelope Generator
                /** percussive/non-percussive mode  */
                public byte egType;
                /** phase type  */
                public byte state;
                /** total level: TL << 2  */
                public int tl;
                /** adjusted now TL  */
                public int tll;
                /** envelope counter  */
                public int volume;
                /** sustain level: sl_tab[SL]  */
                public int sl;

                /** (attack state)  */
                public int egMAr;
                /** (attack state)  */
                public byte egShAr;
                /** (attack state)  */
                public byte egSelAr;
                /** (decay state)  */
                public int egMDr;
                /** (decay state)  */
                public byte egShDr;
                /** (decay state)  */
                public byte egSelDr;
                /** (release state)  */
                public int egMRr;
                /** (release state)  */
                public byte egShRr;
                /** (release state)  */
                public byte egSelRr;

                /** 0 = KEY OFF, >0 = KEY ON      */
                public int key;

                // LFO
                /** LFO Amplitude Modulation enable mask  */
                public int aMmask;
                //  LFO Phase Modulation enable flag (active high)
                public byte vib;

                /** waveform select  */
                public byte waveformNumber;
                public int waveTable;

                // speedup: pump up the struct size to power of 2
//                public byte[] reserved = new byte[128 - 100];

                private void keyOn(int keySet) {
                    if (this.key == 0) {
                        // restart Phase Generator
                        this.cnt = 0;
                        // phase . Attack
                        this.state = EG_ATT;
                    }
                    this.key |= keySet;
                }

                private void keyOff(int keyClr) {
                    if (this.key != 0) {
                        this.key &= keyClr;

                        if (this.key == 0) {
                            // phase . Release
                            if (this.state > EG_REL)
                                this.state = EG_REL;
                        }
                    }
                }

                /**
                 * update phase increment counter of Operator (also update the EG rates if necessary)
                 */
                private void calcFc(int fc, byte kCode) {
                    // (frequency) phase increment counter
                    this.incr = fc * this.mul;
                    int ksr = kCode >> this.KSR;

                    if (this.ksr != ksr) {
                        this.ksr = (byte) ksr;

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

            public Slot[] slots = new Slot[] {new Slot(), new Slot()};

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
            public byte extended;
            public byte muted;

            //speedup:pump up the struct size to power of 2
//            public byte[] reserved = new byte[512 - 272];

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

        /** envelope output entries  */
        private static final int ENV_BITS = 10;
        private static final int ENV_LEN = 1 << ENV_BITS;
        private static final double ENV_STEP = 128.0 / ENV_LEN;

        private static final int MAX_ATT_INDEX = (1 << (ENV_BITS - 1)) - 1; // 511
        private static final int MIN_ATT_INDEX = 0;

        /** sinwave entries  */
        private static final int SIN_BITS = 10;
        private static final int SIN_LEN = 1 << SIN_BITS;
        private static final int SIN_MASK = SIN_LEN - 1;

        private static final int TL_RES_LEN = 256; // 8 bits addressing (real chip)

        // register number to channel number , slot offset
        private static final int SLOT1 = 0;
        private static final int SLOT2 = 1;

        /** mapping of register number (offset) to slot number used by the emulator  */
        private static final int[] slotArray = new int[] {
                0, 2, 4, 1, 3, 5, -1, -1,
                6, 8, 10, 7, 9, 11, -1, -1,
                12, 14, 16, 13, 15, 17, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1
        };

        /** multiple table  */
        private static final int ML = 2;
        private static final byte[] mulTab = new byte[] {
                // 1/2, 1, 2, 3, 4, 5, 6, 7, 8, 9,10,10,12,12,15,15
                (byte) (0.50 * ML), (byte) (1.00 * ML), (byte) (2.00 * ML), (byte) (3.00 * ML), (byte) (4.00 * ML), (byte) (5.00 * ML), (byte) (6.00 * ML), (byte) (7.00 * ML),
                (byte) (8.00 * ML), (byte) (9.00 * ML), (byte) (10.00 * ML), (byte) (10.00 * ML), (byte) (12.00 * ML), (byte) (12.00 * ML), (byte) (15.00 * ML), (byte) (15.00 * ML)
        };

        /*
         * TL_TAB_LEN is calculated as:
         *
         *  (12+1)=13 - sinus amplitude bits     (Y axis)
         *  additional 1: to compensate for calculations of negative part of waveform
         *  (if we don't add it then the greatest possible _negative_ value would be -2
         *  and we really need -1 for waveform // #7)
         *  2  - sinus sign bit           (Y axis)
         *  TL_RES_LEN - sinus resolution (X axis)
         */
        private static final int TL_TAB_LEN = 13 * 2 * TL_RES_LEN;
        private static int[] tlTab = new int[TL_TAB_LEN];

        private static final int ENV_QUIET = TL_TAB_LEN >> 4;

        /** sin waveform table in 'decibel' scale
         * there are eight waveforms on Opl3 chips */
        private static int[] sinTab = new int[SIN_LEN * 8];

        /** LFO Amplitude Modulation table (verified on real YM3812)
           27 output levels (triangle waveform); 1 level takes one of: 192, 256 or 448 samples

           Length: 210 elements.

            Each of the elements has to be repeated
            exactly 64 times (on 64 consecutive samples).
            The whole table takes: 64 * 210 = 13440 samples.

            When AM = 1 data is used directly
            When AM = 0 data is divided by 4 before being used (losing precision is important)
        */
        private static final int LFO_AM_TAB_ELEMENTS = 210;

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

        /**
         * 36 operators, 8 waveforms
         */
        private static final int OPL3_TYPE_YMF262 = 0;

        /**
         * Opl3 chips have 18 channels
         */
        public Channel[] channels = new Channel[] {
                new Channel(), new Channel(), new Channel(), new Channel(),
                new Channel(), new Channel(), new Channel(), new Channel(),
                new Channel(), new Channel(), new Channel(), new Channel(),
                new Channel(), new Channel(), new Channel(), new Channel(),
                new Channel(), new Channel()
        };

        /**
         * channels output masks (0xffffffff = enable); 4 masks per one channel
         */
        public int[] pan = new int[18 * 4];
        /**
         * output control values 1 per one channel (1 value contains 4 masks)
         */
        public int[] panCtrlValue = new int[18];
        /**
         * for the 5 Rhythm Channels
         */
        public byte[] muteSpc = new byte[5];

        /**
         * 18 channels
         */
        public int[] chanOut = new int[18];
        /**
         * phase modulation input (SLOT 2)
         */
        public int phaseModulation;
        /**
         * phase modulation input (SLOT 3 in 4 Operator channels)
         */
        public int phaseModulation2;

        /**
         * Global envelope generator counter
         */
        public int egCnt;
        /**
         * Global envelope generator counter works at frequency = chipclock/288 (288=8*36)
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
         * Opl3 extension enable flag
         */
        public byte mode;

        /**
         * Rhythm mode
         */
        public byte rhythm;

        /**
         * timer counters
         */
        public int[] T = new int[2];
        /**
         * timer enable
         */
        public byte[] st = new byte[2];

        /**
         * address register
         */
        public int address;
        /**
         * status flag
         */
        public byte status;
        /**
         * status mask
         */
        public byte statusmask;

        /**
         * NTS (note select)
         */
        public byte nts;

        // external event Callback handlers

        /**
         * TIMER handler
         */
        public TimerHandler timerHandler;
        /**
         * TIMER parameter
         */
        public Object TimerParam;
        /**
         * IRQ handler
         */
        public IrqHandler irqHandler;
        /**
         * IRQ parameter
         */
        public Ymf262State IRQParam;
        /**
         * stream update handler
         */
        public Opl3.UpdateHandler updateHandler;
        /**
         * stream update parameter
         */
        public Ymf262State UpdateParam;

        /**
         * chip type
         */
        private byte type;
        /**
         * master clock  (Hz)
         */
        private int clock;
        /**
         * sampling rate (Hz)
         */
        private int rate;
        /**
         * frequency base
         */
        private double freqbase;

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

        /**
         * status set and IRQ handling
         */
        private void setStatus(int flag) {
            // set status flag masking out disabled IRQs
            this.status |= (byte) (flag & this.statusmask);
            if ((this.status & 0x80) == 0) {
                if ((this.status & 0x7f) != 0) { // IRQ on
                    this.status |= 0x80;
                    // Callback user interrupt handler (IRQ is OFF to ON)
                    if (this.irqHandler != null) this.irqHandler.accept(this.IRQParam, 1);
                }
            }
        }

        /**
         * status reset and IRQ handling
         */
        private void resetStatus(int flag) {
            // reset status flag
            this.status &= (byte) ~flag;
            if ((this.status & 0x80) != 0) {
                if ((this.status & 0x7f) == 0) {
                    this.status &= 0x7f;
                    // Callback user interrupt handler (IRQ is ON to OFF)
                    if (this.irqHandler != null) this.irqHandler.accept(this.IRQParam, 0);
                }
            }
        }

        /**
         * IRQ mask set
         */
        private void setStatusMask(int flag) {
            this.statusmask = (byte) flag;
            // IRQ handling check
            setStatus(0);
            resetStatus(0);
        }

        /** advance LFO to next sample  */
        private void advanceLfo() {
            byte tmp;

            // LFO
            this.lfoAmCnt += this.lfoAmInc;
            if (this.lfoAmCnt >= (LFO_AM_TAB_ELEMENTS << LFO_SH)) // lfo_am_table is 210 elements long
                this.lfoAmCnt -= (LFO_AM_TAB_ELEMENTS << LFO_SH);

            tmp = lfoAmTable[this.lfoAmCnt >> LFO_SH];

            if (this.lfoAmDepth != 0)
                this.lfoAm = tmp;
            else
                this.lfoAm = (byte) (tmp >> 2);

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
            // Register works at sampling frequency of the chip, so output
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

        /**
         * calculate output of a 2nd part of 4-Op channel
         */
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

                chanOut[7] += Channel.Slot.calcOp(phase << FREQ_SH, env, 0, slot7_1().waveTable) * 2;
            }

            // Snare Drum (verified on real YM3812)
            env = slot7_2().calcVolume(this.lfoAm);
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
                n <<= 1; // 12 bits here (as in real chip)
                tlTab[x * 2 + 0] = n;
                tlTab[x * 2 + 1] = ~tlTab[x * 2 + 0]; // this *is* different from OPL2 (verified on real YMF262)

                for (int i = 1; i < 13; i++) {
                    tlTab[x * 2 + 0 + i * 2 * TL_RES_LEN] = tlTab[x * 2 + 0] >> i;
                    tlTab[x * 2 + 1 + i * 2 * TL_RES_LEN] = ~tlTab[x * 2 + 0 + i * 2 * TL_RES_LEN]; // this *is* different from OPL2 (verified on real YMF262)
                }
//System.err.printf("tl %04i", x*2);
//for (i=0; i<13; i++)
// System.err.printf(", [%02i] %5i", i*2, tl_tab[ x*2 +0 + i*2*TL_RES_LEN ] ); // positive
//System.err.printf("\n");

//System.err.printf("tl %04i", x*2);
//for (i=0; i<13; i++)
// System.err.printf(", [%02i] %5i", i*2, tl_tab[ x*2 +1 + i*2*TL_RES_LEN ] ); /* negative
//System.err.printf("\n");
            }

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

//System.err.printf("YMF262.C: sin [%4i (hex=%03x)]= %4i (tl_tab value=%5i)\n", i, i, sin_tab[i], tl_tab[sin_tab[i]]);
            }

            for (int i = 0; i < SIN_LEN; i++) {
                // these 'pictures' represent _two_ cycles
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

                // waveform 4:
                //             /\  ____/\  ____
                //               \/      \/
                // output whole sinus waveform in half the cycle(step=2) and output 0 on the other half of cycle

                if ((i & (1 << (SIN_BITS - 1))) != 0)
                    sinTab[4 * SIN_LEN + i] = TL_TAB_LEN;
                else
                    sinTab[4 * SIN_LEN + i] = sinTab[i * 2];

                // waveform 5:
                //             /\/\____/\/\____
                //
                // output abs(whole sinus) waveform in half the cycle(step=2) and output 0 on the other half of cycle

                if ((i & (1 << (SIN_BITS - 1))) != 0)
                    sinTab[5 * SIN_LEN + i] = TL_TAB_LEN;
                else
                    sinTab[5 * SIN_LEN + i] = sinTab[(i * 2) & (SIN_MASK >> 1)];

                // waveform 6: ____    ____
                //
                //                 ____    ____
                // output maximum in half the cycle and output minimum on the other half of cycle

                if ((i & (1 << (SIN_BITS - 1))) != 0)
                    sinTab[6 * SIN_LEN + i] = 1; // negative
                else
                    sinTab[6 * SIN_LEN + i] = 0; // positive

                // waveform 7:
                //             |\____  |\____
                //                   \|      \|
                // output sawtooth waveform

                int x;
                if ((i & (1 << (SIN_BITS - 1))) != 0)
                    x = ((SIN_LEN - 1) - i) * 16 + 1; // negative: from 8177 to 1
                else
                    x = i * 16; //positive: from 0 to 8176 */

                if (x > TL_TAB_LEN)
                    x = TL_TAB_LEN; // clip to the allowed range

                sinTab[7 * SIN_LEN + i] = x;

//System.err.printf("YMF262.C: sin1[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[1*SIN_LEN+i], tl_tab[sin_tab[1*SIN_LEN+i]]);
//System.err.printf("YMF262.C: sin2[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[2*SIN_LEN+i], tl_tab[sin_tab[2*SIN_LEN+i]]);
//System.err.printf("YMF262.C: sin3[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[3*SIN_LEN+i], tl_tab[sin_tab[3*SIN_LEN+i]]);
//System.err.printf("YMF262.C: sin4[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[4*SIN_LEN+i], tl_tab[sin_tab[4*SIN_LEN+i]]);
//System.err.printf("YMF262.C: sin5[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[5*SIN_LEN+i], tl_tab[sin_tab[5*SIN_LEN+i]]);
//System.err.printf("YMF262.C: sin6[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[6*SIN_LEN+i], tl_tab[sin_tab[6*SIN_LEN+i]]);
//System.err.printf("YMF262.C: sin7[%4i]= %4i (tl_tab value=%5i)\n", i, sin_tab[7*SIN_LEN+i], tl_tab[sin_tab[7*SIN_LEN+i]]);
            }
//System.err.printf("YMF262.C: ENV_QUIET= %08x (dec*8=%i)\n", ENV_QUIET, ENV_QUIET*8 );
        }

        private void init() {
            initChs();

            // frequency base
            this.freqbase = (this.rate) != 0 ? ((double) this.clock / (8.0 * 36)) / this.rate : 0;

            // System.err.printf("YMF262: freqbase=%f\n", this.freqbase);

            // Timer base time
            //this.TimerBase = attotime_mul(ATTOTIME_IN_HZ(this.clock), 8*36);

            // make fnumber . increment counter table
            for (int i = 0; i < 1024; i++) {
                // opn phase increment counter = 20bit
                this.fnTab[i] = (int) ((double) i * 64 * this.freqbase * (1 << (FREQ_SH - 10))); // -10 because chip works with 10.10 fixed point, while we use 16.16
//System.err.printf("YMF262.C: fn_tab[%4i] = %08x (dec=%8i)\n", i, this.fn_tab[i]>>6, this.fn_tab[i]>>6);
            }

            // Amplitude modulation: 27 output levels (triangle waveform); 1 level takes one of: 192, 256 or 448 samples
            // One entry from LFO_AM_TABLE lasts for 64 samples
            this.lfoAmInc = (int) ((1.0 / 64.0) * (1 << LFO_SH) * this.freqbase);

            // Vibrato: 8 output levels (triangle waveform); 1 level takes 1024 samples
            this.lfoPmInc = (int) ((1.0 / 1024.0) * (1 << LFO_SH) * this.freqbase);

//System.err.printf ("this.lfo_am_inc = %8x ; this.lfo_pm_inc = %8x\n", this.lfo_am_inc, this.lfo_pm_inc);

            // Noise generator: a step takes 1 sample
            this.noiseF = (int) ((1.0 / 1.0) * (1 << FREQ_SH) * this.freqbase);

            this.egTimerAdd = (int) ((1 << EG_SH) * this.freqbase);
            this.egTimerOverflow = (1) * (1 << EG_SH);
//System.err.printf("YMF262init eg_timer_add=%8x eg_timer_overflow=%8x\n", this.eg_timer_add, this.eg_timer_overflow);
        }

        private void reset() {
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
         * @param clock is chip clock in Hz
         * @param rate  is sampling rate
         */
        private Opl3(int clock, int rate, int type) {
            this.type = (byte) type;
            this.clock = clock;
            this.rate = rate;

            // init Global tables
            this.init();

            // reset chip
            this.reset();
        }

        /**
         * Destroy one of virtual YMF262
         */
        private void stop() {
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

        private void setUpdateHandler(Opl3.UpdateHandler updateHandler) {
            this.updateHandler = updateHandler;
        }

        /** YMF262 I/O interface */
        private int write(int a, int v) {
            // data bus is 8 bits
            v &= 0xff;

            switch (a & 3) {
            case 0: // address port 0 (register set // #1)
                this.address = v;
                break;

            case 1: // data port - ignore A1
            case 3: // data port - ignore A1
                if (this.updateHandler != null) this.updateHandler.accept(this.UpdateParam/*,0*/);
                writeReg(this.address, v);
                break;

            case 2: // address port 1 (register set // #2)

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
                    // in OPL2 mode the only accessible in set // #2 is register 0x05
                    if (v == 5)
                        this.address = v | 0x100;
                    else
                        this.address = v; // verified range: 0x01, 0x04, 0x20-0xef(set // #2 becomes set // #1 in opl2 mode)
                }
                break;
            }

            return this.status >> 7;
        }

        private byte read(int a) {
            // Note on status register:
            //
            // YM3526(OPL) and YM3812(OPL2) return bit2 and bit1 in HIGH state
            //
            // YMF262(Opl3) always returns bit2 and bit1 in LOW state
            // which can be used to identify the chip
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

        /**
         * set attack rate & decay rate
         */
        private void setArDr(int s, int v) {
            Channel ch = this.channels[s / 2];
            Channel.Slot slot = ch.slots[s & 1];

            slot.setArDr(v);
        }

        /**
         * set sustain level & release rate
         */
        private void setSlRr(int s, int v) {
            Channel ch = this.channels[s / 2];
            Channel.Slot slot = ch.slots[s & 1];

            slot.setSrRr(v);
        }

        /**
         * write a value v to register r on OPL chip
         */
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
                    byte prev;

                    ch = this.channels[0]; // channel 0
                    prev = ch.extended;
                    ch.extended = (byte) ((v >> 0) & 1);
                    if (prev != ch.extended)
                        ch.update();
                    // channel 1
                    ch = this.channels[1];
                    prev = ch.extended;
                    ch.extended = (byte) ((v >> 1) & 1);
                    if (prev != ch.extended)
                        ch.update();
                    // channel 2
                    ch = this.channels[2];
                    prev = ch.extended;
                    ch.extended = (byte) ((v >> 2) & 1);
                    if (prev != ch.extended)
                        ch.update();


                    ch = this.channels[9]; // channel 9
                    prev = ch.extended;
                    ch.extended = (byte) ((v >> 3) & 1);
                    if (prev != ch.extended)
                        ch.update();
                    // channel 10
                    ch = this.channels[10];
                    prev = ch.extended;
                    ch.extended = (byte) ((v >> 4) & 1);
                    if (prev != ch.extended)
                        ch.update();
                    // channel 11
                    ch = this.channels[11];
                    prev = ch.extended;
                    ch.extended = (byte) ((v >> 5) & 1);
                    if (prev != ch.extended)
                        ch.update();
                }
                return;

                case 0x105: // Opl3 extensions enable register

                    this.mode = (byte) (v & 0x01); // Opl3 mode when bit0=1 otherwise it is OPL2 mode

                    // following behaviour was tested on real YMF262,
                    // switching Opl3/OPL2 modes on the fly:
                    //  - does not change the waveform previously selected (unless when ....)
                    //  - does not update ch.A, ch.B, ch.C and ch.D output selectors (registers c0-c8) (unless when ....)
                    //  - does not disable channels 9-17 on Opl3.OPL2 switch
                    //  - does not switch 4 Operator channels back to 2 Operator channels

                    return;

                default:
// if (r < 0x120)
//  System.err.printf("YMF262: write to unknown register (set#2): %03x value=%02x\n", r, v);
                    break;
                }

                chOffset = 9; // register page // #2 starts from channel 9 (counting from 0)
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
                        byte st1 = (byte) (v & 1);
                        byte st2 = (byte) ((v >> 1) & 1);

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
                    this.nts = (byte) v;
                    break;

                default:
//System.err.printf("YMF262: write to unknown register: %02x value=%02x\n", r, v);
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
                    if (chOffset != 0) // 0xbd register is present in set // #1 only
                        return;

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
                    byte block = (byte) (blockFNum >> 10);

                    ch.blockFNum = blockFNum;

                    ch.kslBase = Channel.kslTab[blockFNum >> 6];
                    ch.fc = this.fnTab[blockFNum & 0x03ff] >> (7 - block);

                    // BLK 2,1,0 bits . bits 3,2,1 of kcode
                    ch.kCode = (byte) ((ch.blockFNum & 0x1c00) >> 9);

                    // the info below is actually opposite to what is stated in the Manuals (verifed on real YMF262)
                    // if notesel == 0 . lsb of kcode is bit 10 (MSB) of fnum
                    // if notesel == 1 . lsb of kcode is bit 9 (MSB-1) of fnum
                    if ((this.nts & 0x40) != 0)
                        ch.kCode |= (byte) ((ch.blockFNum & 0x100) >> 8); // notesel == 1
                    else
                        ch.kCode |= (byte) ((ch.blockFNum & 0x200) >> 9); // notesel == 0

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

                ch.slots[SLOT1].fb = (byte) (((v >> 1) & 7) != 0 ? ((v >> 1) & 7) + 7 : 0);
                ch.slots[SLOT1].con = (byte) (v & 1);

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
                            byte conn = (byte) ((ch.slots[SLOT1].con << 1) | (chP3.slots[SLOT1].con << 0));
                            switch (conn) {
                            case 0:
                                // 1 . 2 . 3 . 4 - out
                                ch.slots[SLOT1].connect.index = 18;
                                ch.slots[SLOT2].connect.index = 19;
                                chP3.slots[SLOT1].connect.index = 18;
                                chP3.slots[SLOT2].connect.index = chanNo + 3;
                                break;
                            case 1:
                                // 1 . 2 -\
                                //  3 . 4 -+- out
                                ch.slots[SLOT1].connect.index = 18;
                                ch.slots[SLOT2].connect.index = chanNo;
                                chP3.slots[SLOT1].connect.index = 18;
                                chP3.slots[SLOT2].connect.index = chanNo + 3;
                                break;
                            case 2:
                                // 1 -----------\
                                //    2 . 3 . 4 -+- out
                                ch.slots[SLOT1].connect.index = chanNo;
                                ch.slots[SLOT2].connect.index = 19;
                                chP3.slots[SLOT1].connect.index = 18;
                                chP3.slots[SLOT2].connect.index = chanNo + 3;
                                break;
                            case 3:
                                // 1 ------\
                                //   2 . 3 -+- out
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
                            byte conn = (byte) ((chM3.slots[SLOT1].con << 1) | (ch.slots[SLOT1].con << 0));
                            switch (conn) {
                            case 0:
                                // 1 . 2 . 3 . 4 - out
                                chM3.slots[SLOT1].connect.index = 18;
                                chM3.slots[SLOT2].connect.index = 19;
                                ch.slots[SLOT1].connect.index = 18;
                                ch.slots[SLOT2].connect.index = chanNo;
                                break;
                            case 1:
                                            /* 1 . 2 -\
                                               3 . 4 -+- out */
                                chM3.slots[SLOT1].connect.index = 18;
                                chM3.slots[SLOT2].connect.index = chanNo - 3;
                                ch.slots[SLOT1].connect.index = 18;
                                ch.slots[SLOT2].connect.index = chanNo;
                                break;
                            case 2:
                                            /* 1 -----------\
                                               2 . 3 . 4 -+- out */
                                chM3.slots[SLOT1].connect.index = chanNo - 3;
                                chM3.slots[SLOT2].connect.index = 19;
                                ch.slots[SLOT1].connect.index = 18;
                                ch.slots[SLOT2].connect.index = chanNo;
                                break;
                            case 3:
                                            /* 1 ------\
                                               2 . 3 -+- out
                                               4 ------/     */
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

            case 0xe0: // waveform select
                slot = slotArray[r & 0x1f];
                if (slot < 0) return;

                slot += chOffset * 2;

                ch = this.channels[slot / 2];

                // store 3-bit value written regardless of current OPL2 or Opl3 mode... (verified on real YMF262)
                v &= 7;
                ch.slots[slot & 1].waveformNumber = (byte) v;

                // ... but select only waveforms 0-3 in OPL2 mode
                if ((this.mode & 1) == 0) {
                    v &= 3; // we're in OPL2 mode
                }
                ch.slots[slot & 1].waveTable = v * SIN_LEN;
                break;
            }
        }

        public void setMmuteMask(int muteMask) {
            for (byte curChn = 0; curChn < 18; curChn++)
                this.channels[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
            for (byte curChn = 0; curChn < 5; curChn++)
                this.muteSpc[curChn] = (byte) ((muteMask >> (curChn + 18)) & 0x01);
        }

        /**
         * Generate samples for one of the YMF262's
         *
         * @param buffers is table of 4 pointers to the buffers: CH.A, CH.B, CH.C and CH.D
         * @param length  is the number of samples that should be generated
         */
        public void update(int[][] buffers, int length) {
            byte rhythm = (byte) (this.rhythm & 0x20);

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

                // register set // #2
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

                // accumulator register set // #1
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

//System.err.printf("%d %d", ch_a[i], ch_b[i]);

                advance();
            }
        }
    }

    /*
     * Originally based on ADLIBEMU.C, an AdLib/OPL2 emulation library by Ken Silverman
     * Copyright (C) 1998-2001 Ken Silverman
     * Ken Silverman's official web site: "http://www.advsys.net/ken"
     */
    public static class Opl2 {

        private static final int NUM_CHANNELS = 18;

        private static final int MAXOPERATORS = NUM_CHANNELS * 2;

        private static final double FL05 = 0.5;
        private static final double FL2 = 2.0;

        // fixed-point calculations using 16+16
        private static final int FIXEDPT = 0x10000;
        // fixed-point calculations using 8+24
        private static final int FIXEDPT_LFO = 0x1000000;

        // clocking of the chip
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
         * 2op channel y+3 so the operatorss y, (9+y), y+3, (9+y)+3 make up a 4op
         * channel.
         */
        public static class Operator {

            /** waveform precision (10 bits) */
            private static final int WAVEPREC = 1024;

            /** start of the waveform */
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

            /** length of the waveform as mask */
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

            /** where the first entry resides */
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

            /** envelope generator function constants */
            private static final double[] attackconst = new double[] {
                    1 / 2.82624,
                    1 / 2.25280,
                    1 / 1.88416,
                    1 / 1.59744
            };

            static Random rnd = new Random();

            /** wave form table */
            private static short[] wavtable = new short[WAVEPREC * 3];

            static {
                // create waveform tables
                for (int i = 0; i < (WAVEPREC >> 1); i++) {
                    wavtable[(i << 1) + WAVEPREC] = (short) (16384 * Math.sin((i << 1) * Math.PI * 2 / WAVEPREC));
                    wavtable[(i << 1) + 1 + WAVEPREC] = (short) (16384 * Math.sin(((i << 1) + 1) * Math.PI * 2 / WAVEPREC));
                    wavtable[i] = wavtable[(i << 1) + WAVEPREC];
                }
                for (int i = 0; i < (WAVEPREC >> 3); i++) {
                    wavtable[i + (WAVEPREC << 1)] = (short) (wavtable[i + (WAVEPREC >> 3)] - 16384);
                    wavtable[i + ((WAVEPREC * 17) >> 3)] = (short) (wavtable[i + (WAVEPREC >> 2)] + 16384);
                }
            }

            /** current output/last output (used for feedback) */
            public int cVal, lastcval;
            /** time (position in waveform) and time increment */
            public int tcount, wfpos, tInc;
            /** and amplification (envelope) */
            public double amp, step_amp;
            // volume */
            public double vol;
            // sustain level */
            public double sustainLevel;
            // feedback amount */
            public int mfbi;
            // attack rate function coefficients */
            public double a0, a1, a2, a3;
            // decay/release rate functions */
            public double decayMul, releaseMul;
            // current state of Operator (attack/decay/sustain/release/off) */
            public int opState;
            public int tOff;
            // highest three bits of the frequency, used for vibrato calculations */
            public int freqHigh;
            // start of selected waveform */
            public int ptrCur_wform;
            // mask for selected waveform */
            public int cur_wmask;
            // activity state (regular, percussion) */
            public int act_state;
            // keep sustain level when decay finished */
            public boolean sus_keep;
            // vibrato/tremolo enable bits */
            public boolean vibrato, tremolo;

            // variables used to provide non-continuous envelopes

            // for non-standard sample rates we need to determine how many samples have passed */
            public int generator_pos;
            // current (standardized) sample position */
            public int cur_env_step;
            // number of std samples of one step (for attack/decay/release mode) */
            public int envStepA, envStepD, envStepR;
            // position of 8-cyclic step skipping (always 2^x to check against mask) */
            public byte step_skip_pos_a;
            // bitmask that determines if a step is skipped (respective bit is zero then) */
            public int envStepSkipA;

            // base of a 4op channel/part of a 4op channel */
            public boolean is4op, is4OpAttached;
            // opl3 stereo panning amount */
            public int leftPan, rightPan;

            private static void advanceDrums(Operator op_pt1, int vib1, Operator op_pt2, int vib2, Operator op_pt3, int vib3, int generatorAdd) {
                int c1 = op_pt1.tcount / FIXEDPT;
                int c3 = op_pt3.tcount / FIXEDPT;
                int phasebit = (((c1 & 0x88) ^ ((c1 << 5) & 0x80)) | ((c3 ^ (c3 << 2)) & 0x20)) != 0 ? 0x02 : 0x00;

                int noisebit = rnd.nextInt() & 1;// rand() & 1;

                int snare_phase_bit = (((op_pt1.tcount / FIXEDPT) / 0x100) & 1);

                // Hihat
                int inttm = (phasebit << 8) | (0x34 << (phasebit ^ (noisebit << 1)));
                op_pt1.wfpos = inttm * FIXEDPT; // waveform position
                // advance waveform time
                op_pt1.tcount += op_pt1.tInc;
                op_pt1.tcount += op_pt1.tInc * vib1 / FIXEDPT;
                op_pt1.generator_pos += generatorAdd;

                // Snare
                inttm = ((1 + snare_phase_bit) ^ noisebit) << 8;
                op_pt2.wfpos = inttm * FIXEDPT; // waveform position
                // advance waveform time
                op_pt2.tcount += op_pt2.tInc;
                op_pt2.tcount += op_pt2.tInc * vib2 / FIXEDPT;
                op_pt2.generator_pos += generatorAdd;

                // Cymbal
                inttm = (1 + phasebit) << 8;
                op_pt3.wfpos = inttm * FIXEDPT; // waveform position
                // advance waveform time
                op_pt3.tcount += op_pt3.tInc;
                op_pt3.tcount += op_pt3.tInc * vib3 / FIXEDPT;
                op_pt3.generator_pos += generatorAdd;
            }

            private void disable(int act_type) {
                // check if this is really an on-off transition
                if (this.act_state != OP_ACT_OFF) {
                    this.act_state &= (~act_type);
                    if (this.act_state == OP_ACT_OFF) {
                        if (this.opState != OF_TYPE_OFF)
                            this.opState = OF_TYPE_REL;
                    }
                }
            }

            private void checkEgAttack() {
                if (((this.cur_env_step + 1) & this.envStepA) == 0) {
                    // check if next step already reached
                    if (this.a0 >= 1.0) {
                        // attack phase finished, next: decay
                        this.opState = OF_TYPE_DEC;
                        this.amp = 1.0;
                        this.step_amp = 1.0;
                    }
                }
            }

            // output level is sustained, mode changes only when Operator is turned off (.release)
            // or when the keep-sustained bit is turned off (.sustain_nokeep)
            private void output(int modulator, int trem) {
                if (opState != OF_TYPE_OFF) {
                    int i;
                    lastcval = cVal;
                    i = (wfpos + modulator) / FIXEDPT;

                    // wform: -16384 to 16383 (0x4000)
                    // trem :  32768 to 65535 (0x10000)
                    // step_amp: 0.0 to 1.0
                    // vol  : 1/2^14 to 1/2^29 (/0x4000; /1../0x8000)

                    cVal = (int) (step_amp * vol * wavtable[ptrCur_wform + (i & cur_wmask)] * trem / 16.0);
                }
            }

            // no action, Operator is off
            private void off() {
            }

            // output level is sustained, mode changes only when Operator is turned off (.release)
            // or when the keep-sustained bit is turned off (.sustain_nokeep)
            private void sustain() {
                int num_steps_add = generator_pos / FIXEDPT; // number of (standardized) samples
                int ct;
                for (ct = 0; ct < num_steps_add; ct++) {
                    cur_env_step++;
                }
                generator_pos -= num_steps_add * FIXEDPT;
            }

            // Operator in release mode, if output level reaches zero the Operator is turned off
            private void release() {
                int num_steps_add;
                int ct;

                // ??? boundary?
                if (amp > 0.00000001) {
                    // release phase
                    amp *= releaseMul;
                }

                num_steps_add = generator_pos / FIXEDPT; // number of (standardized) samples
                for (ct = 0; ct < num_steps_add; ct++) {
                    cur_env_step++; // sample counter
                    if ((cur_env_step & envStepR) == 0) {
                        if (amp <= 0.00000001) {
                            // release phase finished, turn off this Operator
                            amp = 0.0;
                            if (opState == OF_TYPE_REL) {
                                opState = OF_TYPE_OFF;
                            }
                        }
                        step_amp = amp;
                    }
                }
                generator_pos -= num_steps_add * FIXEDPT;
            }

            // Operator in decay mode, if sustain level is reached the output level is either
            // kept (sustain level keep enabled) or the Operator is switched into release mode
            private void decay() {
                int num_steps_add;
                int ct;

                if (amp > sustainLevel) {
                    // decay phase
                    amp *= decayMul;
                }

                num_steps_add = generator_pos / FIXEDPT; // number of (standardized) samples
                for (ct = 0; ct < num_steps_add; ct++) {
                    cur_env_step++;
                    if ((cur_env_step & envStepD) == 0) {
                        if (amp <= sustainLevel) {
                            // decay phase finished, sustain level reached
                            if (sus_keep) {
                                // keep sustain level (until turned off)
                                opState = OF_TYPE_SUS;
                                amp = sustainLevel;
                            } else {
                                // next: release phase
                                opState = OF_TYPE_SUS_NOKEEP;
                            }
                        }
                        step_amp = amp;
                    }
                }
                generator_pos -= num_steps_add * FIXEDPT;
            }

            // Operator in attack mode, if full output level is reached,
            // the Operator is switched into decay mode
            private void attack() {
                int num_steps_add;
                int ct;

                amp = ((a3 * amp + a2) * amp + a1) * amp + a0;

                num_steps_add = generator_pos / FIXEDPT; // number of (standardized) samples
                for (ct = 0; ct < num_steps_add; ct++) {
                    cur_env_step++; // next sample
                    if ((cur_env_step & envStepA) == 0) { // check if next step already reached
                        if (amp > 1.0) {
                            // attack phase finished, next: decay
                            opState = OF_TYPE_DEC;
                            amp = 1.0;
                            step_amp = 1.0;
                        }
                        step_skip_pos_a <<= 1;
                        if (step_skip_pos_a == 0) step_skip_pos_a = 1;
                        if ((step_skip_pos_a & envStepSkipA) != 0) { // check if required to skip next step
                            step_amp = amp;
                        }
                    }
                }
                generator_pos -= num_steps_add * FIXEDPT;
            }

            public void changeWaveform(int regbase, byte[] wave_sel) {
                //#if defined(OPLTYPE_IS_OPL3)
                if (regbase >= ARC_SECONDSET) regbase -= (ARC_SECONDSET - 22); // second set starts at 22
                //#endif
                // waveform selection
                cur_wmask = wavemask[wave_sel[regbase]];
                //op_pt.cur_wform = wavtable[waveform[this.wave_sel[regbase]]];
                ptrCur_wform = waveform[wave_sel[regbase]];
                // (might need to be adapted to waveform type here...)
            }

            public void enable(int regbase, int act_type, byte[] wave_sel) {
                // check if this is really an off-on transition
                if (act_state == OP_ACT_OFF) {
                    int wselbase = regbase;
                    if (wselbase >= ARC_SECONDSET)
                        wselbase -= (ARC_SECONDSET - 22); // second set starts at 22

                    tcount = wavestart[wave_sel[wselbase]] * FIXEDPT;

                    // start with attack mode
                    opState = OF_TYPE_ATT;
                    act_state |= act_type;
                }
            }

            private void advance(int vib, int generator_add) {
                wfpos = tcount; // waveform position

                // advance waveform time
                tcount += tInc;
                tcount += (tInc) * vib / FIXEDPT;

                generator_pos += generator_add;
            }

            public void changeAttackRate(int attackRate, double recipSamp) {
                if (attackRate != 0) {
                    final byte[] stepSkipMask = new byte[] {(byte) 0xff, (byte) 0xfe, (byte) 0xee, (byte) 0xba, (byte) 0xaa};
                    int stepSkip;
                    int steps;
                    int stepNum;

                    double f = Math.pow(FL2, (double) attackRate + (tOff >> 2) - 1) * attackconst[tOff & 3] * recipSamp;
                    // attack rate coefficients
                    a0 = 0.0377 * f;
                    a1 = 10.73 * f + 1;
                    a2 = -17.57 * f;
                    a3 = 7.42 * f;

                    stepSkip = attackRate * 4 + tOff;
                    steps = stepSkip >> 2;
                    envStepA = (1 << (steps <= 12 ? 12 - steps : 0)) - 1;

                    stepNum = (stepSkip <= 48) ? (4 - (stepSkip & 3)) : 0;
                    envStepSkipA = stepSkipMask[stepNum];

                    if (stepSkip >= 60) {
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

            public void changeDecayRate(int decayRate, double recipSamp) {
                // decayMul should be 1.0 when decayRate == 0
                if (decayRate != 0) {
                    double f = -7.4493 * decrelConst[tOff & 3] * recipSamp;
                    decayMul = Math.pow(FL2, f * Math.pow(FL2, decayRate + (tOff >> 2)));
                    int steps = (decayRate * 4 + tOff) >> 2;
                    envStepD = (1 << (steps <= 12 ? 12 - steps : 0)) - 1;
                } else {
                    decayMul = 1.0;
                    envStepD = 0;
                }
            }

            public void changeReleaseRate(int releaseRate, double recipSamp) {
                // releaseMul should be 1.0 when releaseRate == 0
                if (releaseRate != 0) {
                    int steps;

                    double f = -7.4493 * decrelConst[tOff & 3] * recipSamp;
                    releaseMul = Math.pow(FL2, f * Math.pow(FL2, releaseRate + (tOff >> 2)));
                    steps = (releaseRate * 4 + tOff) >> 2;
                    envStepR = (1 << (steps <= 12 ? 12 - steps : 0)) - 1;
                } else {
                    releaseMul = 1.0;
                    envStepR = 0;
                }
            }

            public void changeSustainLevel(int sustainlevel) {
                // sustainlevel should be 0.0 when sustainlevel == 15 (max)
                if (sustainlevel < 15) {
                    this.sustainLevel = Math.pow(FL2, (double) sustainlevel * (-FL05));
                } else {
                    this.sustainLevel = 0.0;
                }
            }

            public void changeKeepsuStain(boolean susKeep) {
                if (opState == OF_TYPE_SUS) {
                    if (!susKeep)
                        opState = OF_TYPE_SUS_NOKEEP;
                } else if (opState == OF_TYPE_SUS_NOKEEP) {
                    if (susKeep)
                        opState = OF_TYPE_SUS;
                }
            }

            // enable/disable vibrato/tremolo LFO effects
            public void changeVibrato(boolean vibrato, boolean tremolo) {
                this.vibrato = vibrato;
                this.tremolo = tremolo;
            }

            // change amount of self-feedback
            public void changeFeedback(int feedback) {
                if (feedback != 0)
                    mfbi = (int) (Math.pow(FL2, (feedback >> 1) + 8));
                else
                    mfbi = 0;
            }

            public void reset() {
                this.opState = OF_TYPE_OFF;
                this.act_state = OP_ACT_OFF;
                this.amp = 0.0;
                this.step_amp = 0.0;
                this.vol = 0.0;
                this.tcount = 0;
                this.tInc = 0;
                this.tOff = 0;
                this.cur_wmask = Operator.wavemask[0];
                this.ptrCur_wform = Operator.waveform[0];
                this.freqHigh = 0;

                this.generator_pos = 0;
                this.cur_env_step = 0;
                this.envStepA = 0;
                this.envStepD = 0;
                this.envStepR = 0;
                this.step_skip_pos_a = 0;
                this.envStepSkipA = 0;

                this.is4op = false;
                this.is4OpAttached = false;
                this.leftPan = 1;
                this.rightPan = 1;
            }
        }

        public interface UpdateHandler extends Runnable {
        }

        // vibrato/tremolo tables */
        private static int[] vibTable = new int[VIBTAB_SIZE];
        private static int[] tremTable = new int[TREMTAB_SIZE * 2];

        private static int[] vibValConst = new int[BLOCKBUF_SIZE];
        private static int[] tremValConst = new int[BLOCKBUF_SIZE];

        // vibrato value tables (used per-Operator) */
        private static int[] vibValVar1 = new int[BLOCKBUF_SIZE];
        private static int[] vibval_var2 = new int[BLOCKBUF_SIZE];

        // key scale level lookup table */
        private static final double[] kslMul = new double[] {
                0.0, 0.5, 0.25, 1.0 // . 0, 3, 1.5, 6 dB/oct
        };

        // frequency multiplicator lookup table */
        private static final double[] frqmul_tab = new double[] {
                0.5, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 12, 12, 15, 15
        };

        // calculated frequency multiplication values (depend on sampling rate)

        // key scale levels */
        private static byte[][] ksLev = new byte[][] {new byte[16], new byte[16], new byte[16], new byte[16], new byte[16], new byte[16], new byte[16], new byte[16]};

        // map a channel number to the register offset of the modulator (=register base) */
        private static final byte[] modulatorbase = new byte[] {
                0, 1, 2,
                8, 9, 10,
                16, 17, 18
        };

        // map a register base to a modulator Operator number or Operator number */
        private static final byte[] regbase2modop = new byte[] {
                0, 1, 2, 0, 1, 2, 0, 0, 3, 4, 5, 3, 4, 5, 0, 0, 6, 7, 8, 6, 7, 8, // first set
                18, 19, 20, 18, 19, 20, 0, 0, 21, 22, 23, 21, 22, 23, 0, 0, 24, 25, 26, 24, 25, 26 // second set
        };
        private static final byte[] regbase2op = new byte[] {
                0, 1, 2, 9, 10, 11, 0, 0, 3, 4, 5, 12, 13, 14, 0, 0, 6, 7, 8, 15, 16, 17, // first set
                18, 19, 20, 27, 28, 29, 0, 0, 21, 22, 23, 30, 31, 32, 0, 0, 24, 25, 26, 33, 34, 35 // second set
        };


        private static final double[] decrelConst = new double[] {
                1 / 39.28064,
                1 / 31.41608,
                1 / 26.17344,
                1 / 22.44608
        };

        // per-chip variables

        // adlib register set (including second set) */
        public byte[] adlibReg = new byte[512];

        // waveform selection */
        public byte[] waveSel = new byte[44];

        public Operator[] ops = new Operator[MAXOPERATORS];
        public byte[] muteChn = new byte[NUM_CHANNELS + 5];
        public int chipClock;

        public int intSamplerate;

        public byte status;
        public int oplIndex;
        public int oplAddr;

        // vibrato/tremolo increment/counter
        public int vibTabPos;
        public int vibTabAdd;
        public int tremTabPos;
        public int tremTabAdd;

        /** should be a chip parameter */
        public int generatorAdd;

        /** inverse of sampling rate */
        public double recLpSamp;
        public double[] frqMul = new double[16];

        /** stream update handler */
        public UpdateHandler updateHandler;
        /** stream update parameter */
//        public Ymf262State updateParam;

        private interface Operators extends Consumer<Operator> {
        }

        private Operators[] opFuncs = new Operators[] {
                Operator::attack,
                Operator::decay,
                Operator::release,
                Operator::sustain, // sustain phase (keeping level)
                Operator::release, // sustain_nokeep phase (release-style)
                Operator::off
        };

        static  {
            // create vibrato table
            vibTable[0] = 8;
            vibTable[1] = 4;
            vibTable[2] = 0;
            vibTable[3] = -4;
            for (int i = 4; i < VIBTAB_SIZE; i++) vibTable[i] = vibTable[i - 4] * -1;

            //
            int[] tremTableInt = new int[TREMTAB_SIZE];

            for (int i = 0; i < BLOCKBUF_SIZE; i++) vibValConst[i] = 0;

            // create tremolo table
            for (int i = 0; i < 14; i++) tremTableInt[i] = i - 13; // upwards (13 to 26 . -0.5/6 to 0)
            for (int i = 14; i < 41; i++) tremTableInt[i] = -i + 14; // downwards (26 to 0 . 0 to -1/6)
            for (int i = 41; i < 53; i++) tremTableInt[i] = i - 40 - 26; // upwards (1 to 12 . -1/6 to -0.5/6)

            for (int i = 0; i < TREMTAB_SIZE; i++) {
                // 0.0 .. -26/26*4.8/6 == [0.0 .. -0.8], 4/53 steps == [1 .. 0.57]
                double trem_val1 = ((double) tremTableInt[i]) * 4.8 / 26.0 / 6.0; // 4.8db
                double trem_val2 = ((double) (tremTableInt[i] / 4) * 1.2) / 6.0 / 6.0; // 1.2db (larger stepping)

                tremTable[i] = (int) (Math.pow(FL2, trem_val1) * FIXEDPT);
                tremTable[TREMTAB_SIZE + i] = (int) (Math.pow(FL2, trem_val2) * FIXEDPT);
            }

            //
            for (int i = 0; i < BLOCKBUF_SIZE; i++) tremValConst[i] = FIXEDPT;

            // key scale level table verified ([table in book]*8/3)
            ksLev[7][0] = 0;
            ksLev[7][1] = 24;
            ksLev[7][2] = 32;
            ksLev[7][3] = 37;
            ksLev[7][4] = 40;
            ksLev[7][5] = 43;
            ksLev[7][6] = 45;
            ksLev[7][7] = 47;
            ksLev[7][8] = 48;
            for (int i = 9; i < 16; i++) ksLev[7][i] = (byte) (i + 41);
            for (int j = 6; j >= 0; j--) {
                for (int i = 0; i < 16; i++) {
                    int oct = (int) ksLev[j + 1][i] - 8;
                    if (oct < 0) oct = 0;
                    ksLev[j][i] = (byte) oct;
                }
            }
        }

        private Opl2(int clock, int sampleRate, UpdateHandler updateHandler) {
            //System.err.printf("clock:%d rate:%d", clock, sampleRate);

            this.chipClock = clock;
            this.intSamplerate = sampleRate;
            this.updateHandler = updateHandler;

            this.generatorAdd = (int) (this.intFreqU() * FIXEDPT / this.intSamplerate);
    
            this.recLpSamp = 1.0 / (double) this.intSamplerate;
            for (int i = 15; i >= 0; i--) {
                this.frqMul[i] = frqmul_tab[i] * this.intFreqU() / (double) Operator.WAVEPREC * (double) FIXEDPT * this.recLpSamp;
            }
    
            // vibrato at ~6.1 ?? (opl3 docs say 6.1, opl4 docs say 6.0, Y8950 docs say 6.4)
            this.vibTabAdd = (int) (VIBTAB_SIZE * FIXEDPT_LFO / 8192 * this.intFreqU() / this.intSamplerate);
            this.vibTabPos = 0;
    
            // tremolo at 3.7hz
            this.tremTabAdd = (int) ((double) TREMTAB_SIZE * TREM_FREQ * FIXEDPT_LFO / (double) this.intSamplerate);
            this.tremTabPos = 0;
        }
    
        private void stop() {
        }
    
        private void reset() {

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

        private void writeIO(int addr, byte val) {
            if ((addr & 1) != 0) {
                //System.err.printf("adr=%x  dat=%x", this.opl_addr, val);
                write(this.oplAddr, val);
            } else
                this.oplAddr = val | ((addr & 2) << 7);
        }
    
        public void write(int idx, byte val) {
            int sScondSet = idx & 0x100;
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
                        this.status &= 0x9f;// ~0x60;
                    } else {
                        this.status = 0;
                    }
                    break;
                case 0x04 | ARC_SECONDSET:
                    // 4op enable/disable switches for each possible channel
                    this.ops[0].is4op = (val & 1) > 0;
                    this.ops[3].is4OpAttached = this.ops[0].is4op;
                    this.ops[1].is4op = (val & 2) > 0;
                    this.ops[4].is4OpAttached = this.ops[1].is4op;
                    this.ops[2].is4op = (val & 4) > 0;
                    this.ops[5].is4OpAttached = this.ops[2].is4op;
                    this.ops[18].is4op = (val & 8) > 0;
                    this.ops[21].is4OpAttached = this.ops[18].is4op;
                    this.ops[19].is4op = (val & 16) > 0;
                    this.ops[22].is4OpAttached = this.ops[19].is4op;
                    this.ops[20].is4op = (val & 32) > 0;
                    this.ops[23].is4OpAttached = this.ops[20].is4op;
                    break;
                case 0x05 | ARC_SECONDSET:
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
                int num = idx & 7;
                int base = (idx - ARC_TVS_KSR_MUL) & 0xff;
                if ((num < 6) && (base < 22)) {
                    int modOp = regbase2modop[sScondSet != 0 ? (base + 22) : base];
                    int regBase = base + sScondSet;
                    int chanBase = sScondSet != 0 ? (modOp - 18 + ARC_SECONDSET) : modOp;

                    // change tremolo/vibrato and sustain keeping of this Operator
                    Operator op = this.ops[modOp + ((num < 3) ? 0 : 9)];
                    op.changeKeepsuStain((adlibReg[ARC_TVS_KSR_MUL + regBase] & 0x20) > 0);
                    op.changeVibrato((adlibReg[ARC_TVS_KSR_MUL + regBase] & 0x40) != 0,
                            (adlibReg[ARC_TVS_KSR_MUL + regBase] & 0x80) != 0);

                    // change frequency calculations of this Operator as
                    // key scale rate and frequency multiplicator can be changed
                    if ((this.adlibReg[0x105] & 1) != 0 && (this.ops[modOp].is4OpAttached)) {
                        // Operator uses frequency of channel
                        changeFrequency(chanBase - 3, regBase, op);
                    } else {
                        changeFrequency(chanBase, regBase, op);
                    }
                }
            }
            break;
            case ARC_KSL_OUTLEV:
            case ARC_KSL_OUTLEV + 0x10: {
                // key scale level; output rate
                int num = idx & 7;
                int base = (idx - ARC_KSL_OUTLEV) & 0xff;
                if ((num < 6) && (base < 22)) {
                    int modOp = regbase2modop[sScondSet != 0 ? (base + 22) : base];
                    int chanBase = sScondSet != 0 ? (modOp - 18 + ARC_SECONDSET) : modOp;

                    // change frequency calculations of this Operator as
                    // key scale level and output rate can be changed
                    Operator op = this.ops[modOp + ((num < 3) ? 0 : 9)];
                    int regbase = base + sScondSet;
                    if ((this.adlibReg[0x105] & 1) != 0 && (this.ops[modOp].is4OpAttached)) {
                        // Operator uses frequency of channel
                        changeFrequency(chanBase - 3, regbase, op);
                    } else {
                        changeFrequency(chanBase, regbase, op);
                    }
                }
            }
            break;
            case ARC_ATTR_DECR:
            case ARC_ATTR_DECR + 0x10: {
                // attack/decay rates
                int num = idx & 7;
                int base = (idx - ARC_ATTR_DECR) & 0xff;
                if ((num < 6) && (base < 22)) {
                    int regbase = base + sScondSet;

                    // change attack rate and decay rate of this Operator
                    Operator op = this.ops[regbase2op[sScondSet != 0 ? (base + 22) : base]];
                    op.changeAttackRate(adlibReg[ARC_ATTR_DECR + regbase] >> 4, this.recLpSamp);
                    op.changeDecayRate(adlibReg[ARC_ATTR_DECR + regbase] & 15, this.recLpSamp);
                }
            }
            break;
            case ARC_SUSL_RELR:
            case ARC_SUSL_RELR + 0x10: {
                // sustain level; release rate
                int num = idx & 7;
                int base = (idx - ARC_SUSL_RELR) & 0xff;
                if ((num < 6) && (base < 22)) {
                    int regbase = base + sScondSet;

                    // change sustain level and release rate of this Operator
                    Operator op = this.ops[regbase2op[sScondSet != 0 ? (base + 22) : base]];
                    op.changeReleaseRate(adlibReg[ARC_SUSL_RELR + regbase] & 15, this.recLpSamp);
                    op.changeSustainLevel(adlibReg[ARC_SUSL_RELR + regbase] >> 4);
                }
            }
            break;
            case ARC_FREQ_NUM: {
                // 0xa0-0xa8 low8 frequency
                int base = (idx - ARC_FREQ_NUM) & 0xff;
                if (base < 9) {
                    int opBase = sScondSet != 0 ? (base + 18) : base;
                    if ((this.adlibReg[0x105] & 1) != 0 && this.ops[opBase].is4OpAttached) break;
                    // regbase of modulator:
                    int modBase = modulatorbase[base] + sScondSet;

                    int chanBase = base + sScondSet;

                    changeFrequency(chanBase, modBase, this.ops[opBase]);
                    changeFrequency(chanBase, modBase + 3, this.ops[opBase + 9]);
                    // for 4op channels all four operators are modified to the frequency of the channel
                    if ((this.adlibReg[0x105] & 1) != 0 && this.ops[sScondSet != 0 ? (base + 18) : base].is4op) {
                        changeFrequency(chanBase, modBase + 8, this.ops[opBase + 3]);
                        changeFrequency(chanBase, modBase + 3 + 8, this.ops[opBase + 3 + 9]);
                    }
                }
            }
            break;
            case ARC_KON_BNUM: {
                if (this.updateHandler != null) // hack for DOSBox logs
                    this.updateHandler.run();
                if (idx == ARC_PERC_MODE) {
                    if (sScondSet != 0) return;

                    if ((val & 0x30) == 0x30) { // BassDrum active
                        this.ops[6].enable(16, OP_ACT_PERC, this.waveSel);
                        changeFrequency(6, 16, this.ops[6]);
                        this.ops[6 + 9].enable(16 + 3, OP_ACT_PERC, this.waveSel);
                        changeFrequency(6, 16 + 3, this.ops[6 + 9]);
                    } else {
                        this.ops[6].disable(OP_ACT_PERC);
                        this.ops[6 + 9].disable(OP_ACT_PERC);
                    }
                    if ((val & 0x28) == 0x28) { // Snare active
                        this.ops[16].enable(17 + 3, OP_ACT_PERC, this.waveSel);
                        changeFrequency(7, 17 + 3, this.ops[16]);
                    } else {
                        this.ops[16].disable(OP_ACT_PERC);
                    }
                    if ((val & 0x24) == 0x24) { // TomTom active
                        this.ops[8].enable(18, OP_ACT_PERC, this.waveSel);
                        changeFrequency(8, 18, this.ops[8]);
                    } else {
                        this.ops[8].disable(OP_ACT_PERC);
                    }
                    if ((val & 0x22) == 0x22) { // Cymbal active
                        this.ops[8 + 9].enable(18 + 3, OP_ACT_PERC, this.waveSel);
                        changeFrequency(8, 18 + 3, this.ops[8 + 9]);
                    } else {
                        this.ops[8 + 9].disable(OP_ACT_PERC);
                    }
                    if ((val & 0x21) == 0x21) { // Hihat active
                        this.ops[7].enable(17, OP_ACT_PERC, this.waveSel);
                        changeFrequency(7, 17, this.ops[7]);
                    } else {
                        this.ops[7].disable(OP_ACT_PERC);
                    }

                    break;
                }
                // regular 0xb0-0xb8
                int base = (idx - ARC_KON_BNUM) & 0xff;
                if (base < 9) {
                    int opBase = sScondSet != 0 ? (base + 18) : base;
                    // regbase of modulator:
                    int modBase = modulatorbase[base] + sScondSet;

                    if ((this.adlibReg[0x105] & 1) != 0 && this.ops[opBase].is4OpAttached) break;
                    if ((val & 32) != 0) {
                        // Operator switched on
                        this.ops[opBase].enable(modBase, OP_ACT_NORMAL, this.waveSel); // modulator (if 2op)
                        this.ops[opBase + 9].enable(modBase + 3, OP_ACT_NORMAL, this.waveSel); // carrier (if 2op)
                        // for 4op channels all four operators are switched on
                        if ((this.adlibReg[0x105] & 1) != 0 && this.ops[opBase].is4op) {
                            // turn on chan+3 operators as well
                            this.ops[opBase + 3].enable(modBase + 8, OP_ACT_NORMAL, this.waveSel);
                            this.ops[opBase + 3 + 9].enable(modBase + 3 + 8, OP_ACT_NORMAL, this.waveSel);
                        }
                    } else {
                        // Operator switched off
                        this.ops[opBase].disable(OP_ACT_NORMAL);
                        this.ops[opBase + 9].disable(OP_ACT_NORMAL);
                        // for 4op channels all four operators are switched off
                        if ((this.adlibReg[0x105] & 1) != 0 && this.ops[opBase].is4op) {
                            // turn off chan+3 operators as well
                            this.ops[opBase + 3].disable(OP_ACT_NORMAL);
                            this.ops[opBase + 3 + 9].disable(OP_ACT_NORMAL);
                        }
                    }

                    int chanBase = base + sScondSet;

                    // change frequency calculations of modulator and carrier (2op) as
                    // the frequency of the channel has changed
                    changeFrequency(chanBase, modBase, this.ops[opBase]);
                    changeFrequency(chanBase, modBase + 3, this.ops[opBase + 9]);
                    // for 4op channels all four operators are modified to the frequency of the channel
                    if ((this.adlibReg[0x105] & 1) != 0 && this.ops[sScondSet != 0 ? (base + 18) : base].is4op) {
                        // change frequency calculations of chan+3 operators as well
                        changeFrequency(chanBase, modBase + 8, this.ops[opBase + 3]);
                        changeFrequency(chanBase, modBase + 3 + 8, this.ops[opBase + 3 + 9]);
                    }
                }
            }
            break;
            case ARC_FEEDBACK: {
                // 0xc0-0xc8 feedback/modulation type (AM/FM)
                int base = (idx - ARC_FEEDBACK) & 0xff;
                if (base < 9) {
                    int opBase = sScondSet != 0 ? (base + 18) : base;
                    int chanbase = base + sScondSet;
                    this.ops[opBase].changeFeedback(adlibReg[ARC_FEEDBACK + chanbase] & 14);
                    // Opl3 panning
                    this.ops[opBase].leftPan = ((val & 0x10) >> 4);
                    this.ops[opBase].rightPan = ((val & 0x20) >> 5);
                    this.ops[opBase].leftPan += ((val & 0x40) >> 6);
                    this.ops[opBase].rightPan += ((val & 0x80) >> 7);
                }
            }
            break;
            case ARC_WAVE_SEL:
            case ARC_WAVE_SEL + 0x10: {
                int num = idx & 7;
                int base = (idx - ARC_WAVE_SEL) & 0xff;
                if ((num < 6) && (base < 22)) {
                    int wselBase = sScondSet != 0 ? (base + 22) : base; // for easier mapping onto wave_sel[]
                    // change waveform
                    if ((this.adlibReg[0x105] & 1) != 0)
                        this.waveSel[wselBase] = (byte) (val & 7); // opl3 mode enabled, all waveforms accessible
                    else this.waveSel[wselBase] = (byte) (val & 3);
                    Operator op = this.ops[regbase2modop[wselBase] + ((num < 3) ? 0 : 9)];
                    op.changeWaveform(wselBase, this.waveSel);

                    // wave selection enabled, change waveform
                    //this.wave_sel[base] = val & 3;
                    //op = &this.Op[regbase2modop[base] + ((num < 3) ? 0 : 9)];
                    //change_waveform(base, op);
                    //}
                    //#endif
                }
            }
            break;
            default:
                break;
            }
        }

        public void changeFrequency(int chanBase, int regBase, Operator op) {
            // frequency
            int frn = ((((int) this.adlibReg[ARC_KON_BNUM + chanBase]) & 3) << 8) + (int) this.adlibReg[ARC_FREQ_NUM + chanBase];
            // block number/octave
            int oct = ((((int) this.adlibReg[ARC_KON_BNUM + chanBase]) >> 2) & 7);
            op.freqHigh = (frn >> 7) & 7;

            // keysplit
            int noteSel = (this.adlibReg[8] >> 6) & 1;
            op.tOff = ((frn >> 9) & (noteSel ^ 1)) | ((frn >> 8) & noteSel);
            op.tOff += (oct << 1);

            // envelope scaling (KSR)
            if ((this.adlibReg[ARC_TVS_KSR_MUL + regBase] & 0x10) == 0) op.tOff >>= 2;

            // 20+a0+b0:
            op.tInc = (int) ((((double) (frn << oct)) * this.frqMul[this.adlibReg[ARC_TVS_KSR_MUL + regBase] & 15]));
            // 40+a0+b0:
            double volIn = (double) (this.adlibReg[ARC_KSL_OUTLEV + regBase] & 63) +
                    (kslMul[this.adlibReg[ARC_KSL_OUTLEV + regBase] >> 6] * ksLev[oct][frn >> 6]);
            op.vol = Math.pow(FL2, volIn * -0.125 - 14);

            // Operator frequency changed, care about features that depend on it
            op.changeAttackRate(this.adlibReg[ARC_ATTR_DECR + regBase] >> 4, this.recLpSamp);
            op.changeDecayRate(this.adlibReg[ARC_ATTR_DECR + regBase] & 15, this.recLpSamp);
            op.changeReleaseRate(this.adlibReg[ARC_SUSL_RELR + regBase] & 15, this.recLpSamp);
        }

        private int readReg(int port) {
            // opl3-detection routines require ret&6 to be zero
            if ((port & 1) == 0) {
                return this.status;
            }
            return 0x00;
        }
    
        private void writeIndex(int port, byte val) {
            this.oplIndex = val;
            if ((port & 3) != 0) {
                // possibly second set
                if (((this.adlibReg[0x105] & 1) != 0) || (this.oplIndex == 5)) this.oplIndex |= ARC_SECONDSET;
            }
        }
    
        // be careful with this
        // uses cptr and chanval, outputs into outbufl(/outbufr)
        // for opl3 check if opl3-mode is enabled (which uses stereo panning)
        //
        // Changes by Valley Bell:
        // - Changed to always output to both channels
        // - added parameter "chn" to fix panning for 4-Op channels and the Rhythm Cymbal

        int[] vibLut = new int[BLOCKBUF_SIZE];
        int[] tremLut = new int[BLOCKBUF_SIZE];

        private void update(int[][] sndPtr, int numSamples) {
            int endSamples;

            // second output buffer (right channel for opl3 stereo)
            int[] outBufL = sndPtr[0];
            int[] outBufR = sndPtr[1];

            // vibrato/tremolo lookup tables (Global, to possibly be used by all operators)

            int vibTShift;
            int maxChannel = NUM_CHANNELS;

            int[] vibVal1, vibVal2, vibVal3, vibVal4;
            int[] tremVal1, tremVal2, tremVal3, tremVal4;

            int ccPtr = 0;

            if ((this.adlibReg[0x105] & 1) == 0) maxChannel = NUM_CHANNELS / 2;

            if (numSamples == 0) {
                for (int curCh = 0; curCh < maxChannel; curCh++) {
                    if ((this.adlibReg[ARC_PERC_MODE] & 0x20) != 0 && (curCh >= 6 && curCh < 9))
                        continue;

                    if (curCh < 9)
                        ccPtr = curCh;
                    else
                        ccPtr = curCh + 9; // second set is operator18-operator35
                    if (this.ops[ccPtr].is4OpAttached)
                        continue;

                    if (this.ops[ccPtr + 0].opState == OF_TYPE_ATT)
                        this.ops[ccPtr + 0].checkEgAttack();

                    if (this.ops[ccPtr + 9].opState == OF_TYPE_ATT)
                        this.ops[ccPtr + 9].checkEgAttack();
                }

                return;
            }

            for (int curSmp = 0; curSmp < numSamples; curSmp += endSamples) {
                endSamples = numSamples - curSmp;

                //memset(outBufL,0, endSamples*sizeof(int));
                for (int i = 0; i < endSamples; i++) outBufL[i] = 0;
                for (int i = 0; i < endSamples; i++) outBufR[i] = 0;

                // calculate vibrato/tremolo lookup tables
                vibTShift = ((this.adlibReg[ARC_PERC_MODE] & 0x40) == 0) ? 1 : 0; // 14cents/7cents switching
                for (int i = 0; i < endSamples; i++) {
                    // cycle through vibrato table
                    this.vibTabPos += this.vibTabAdd;
                    if (this.vibTabPos / FIXEDPT_LFO >= VIBTAB_SIZE)
                        this.vibTabPos -= VIBTAB_SIZE * FIXEDPT_LFO;
                    vibLut[i] = vibTable[this.vibTabPos / FIXEDPT_LFO] >> vibTShift; // 14cents (14/100 of a semitone) or 7cents

                    // cycle through tremolo table
                    this.tremTabPos += this.tremTabAdd;
                    if (this.tremTabPos / FIXEDPT_LFO >= TREMTAB_SIZE)
                        this.tremTabPos -= TREMTAB_SIZE * FIXEDPT_LFO;
                    if ((this.adlibReg[ARC_PERC_MODE] & 0x80) != 0)
                        tremLut[i] = tremTable[this.tremTabPos / FIXEDPT_LFO];
                    else
                        tremLut[i] = tremTable[TREMTAB_SIZE + this.tremTabPos / FIXEDPT_LFO];
                }

                if ((this.adlibReg[ARC_PERC_MODE] & 0x20) != 0) {
                    if ((this.muteChn[NUM_CHANNELS + 0]) == 0) {
                        // BassDrum
                        ccPtr = 6;
                        if ((this.adlibReg[ARC_FEEDBACK + 6] & 1) != 0) {
                            // additive synthesis
                            if (this.ops[ccPtr + 9].opState != OF_TYPE_OFF) {
                                if (this.ops[ccPtr + 9].vibrato) {
                                    vibVal1 = vibValVar1;
                                    for (int i = 0; i < endSamples; i++)
                                        vibVal1[i] = (vibLut[i] * this.ops[ccPtr + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                                } else
                                    vibVal1 = vibValConst;
                                if (this.ops[ccPtr + 9].tremolo)
                                    tremVal1 = tremLut; // tremolo enabled, use table
                                else
                                    tremVal1 = tremValConst;

                                // calculate channel output
                                for (int i = 0; i < endSamples; i++) {

                                    this.ops[ccPtr + 9].advance(vibVal1[i], this.generatorAdd);
                                    opFuncs[this.ops[ccPtr + 9].opState].accept(this.ops[ccPtr + 9]);

                                    this.ops[ccPtr + 9].output(0, tremVal1[i]);
                                    int chanVal = this.ops[ccPtr + 9].cVal * 2;

                                    if ((this.adlibReg[0x105] & 1) != 0) {
                                        outBufL[i] += chanVal * this.ops[ccPtr].leftPan;
                                        outBufR[i] += chanVal * this.ops[ccPtr].rightPan;
                                    } else {
                                        outBufL[i] += chanVal;
                                        outBufR[i] += chanVal;
                                    }
                                }
                            }
                        } else {
                            // frequency modulation
                            if ((this.ops[ccPtr + 9].opState != OF_TYPE_OFF) || (this.ops[ccPtr + 0].opState != OF_TYPE_OFF)) {
                                if ((this.ops[ccPtr + 0].vibrato) && (this.ops[ccPtr + 0].opState != OF_TYPE_OFF)) {
                                    vibVal1 = vibValVar1;
                                    for (int i = 0; i < endSamples; i++)
                                        vibVal1[i] = (vibLut[i] * this.ops[ccPtr + 0].freqHigh / 8) * FIXEDPT * VIBFAC;
                                } else
                                    vibVal1 = vibValConst;
                                if ((this.ops[ccPtr + 9].vibrato) && (this.ops[ccPtr + 9].opState != OF_TYPE_OFF)) {
                                    vibVal2 = vibval_var2;
                                    for (int i = 0; i < endSamples; i++)
                                        vibVal2[i] = (vibLut[i] * this.ops[ccPtr + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                                } else
                                    vibVal2 = vibValConst;
                                if (this.ops[ccPtr + 0].tremolo)
                                    tremVal1 = tremLut; // tremolo enabled, use table
                                else
                                    tremVal1 = tremValConst;
                                if (this.ops[ccPtr + 9].tremolo)
                                    tremVal2 = tremLut; // tremolo enabled, use table
                                else
                                    tremVal2 = tremValConst;

                                // calculate channel output
                                for (int i = 0; i < endSamples; i++) {
                                    this.ops[ccPtr + 0].advance(vibVal1[i], this.generatorAdd);
                                    opFuncs[this.ops[ccPtr + 0].opState].accept(this.ops[ccPtr + 0]);

                                    this.ops[ccPtr + 0].output((this.ops[ccPtr + 0].lastcval + this.ops[ccPtr + 0].cVal) * this.ops[ccPtr + 0].mfbi / 2, tremVal1[i]);

                                    this.ops[ccPtr + 9].advance(vibVal2[i], this.generatorAdd);
                                    opFuncs[this.ops[ccPtr + 9].opState].accept(this.ops[ccPtr + 9]);

                                    this.ops[ccPtr + 9].output(this.ops[ccPtr + 0].cVal * FIXEDPT, tremVal2[i]);

                                    int chanVal = this.ops[ccPtr + 9].cVal * 2;

                                    if ((this.adlibReg[0x105] & 1) != 0) {
                                        outBufL[i] += chanVal * this.ops[ccPtr + 0].leftPan;
                                        outBufR[i] += chanVal * this.ops[ccPtr + 0].rightPan;
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
                        Operator op = this.ops[8];
                        ccPtr = 8;
                        if (op.vibrato) {
                            vibVal3 = vibValVar1;
                            for (int i = 0; i < endSamples; i++)
                                vibVal3[i] = (vibLut[i] * op.freqHigh / 8) * FIXEDPT * VIBFAC;
                        } else
                            vibVal3 = vibValConst;

                        if (op.tremolo)
                            tremVal3 = tremLut; // tremolo enabled, use table
                        else
                            tremVal3 = tremValConst;

                        // calculate channel output
                        for (int i = 0; i < endSamples; i++) {
                            op.advance(vibVal3[i], this.generatorAdd);
                            opFuncs[op.opState].accept(op); // TomTom

                            op.output(0, tremVal3[i]);
                            int chanVal = op.cVal * 2;

                            if ((this.adlibReg[0x105] & 1) != 0) {
                                outBufL[i] += chanVal * this.ops[ccPtr + 0].leftPan;
                                outBufR[i] += chanVal * this.ops[ccPtr + 0].rightPan;
                            } else {
                                outBufL[i] += chanVal;
                                outBufR[i] += chanVal;
                            }
                        }
                    }

                    // Snare/Hihat (j=7), Cymbal (j=8)
                    if ((this.ops[7].opState != OF_TYPE_OFF) || (this.ops[16].opState != OF_TYPE_OFF) ||
                            (this.ops[17].opState != OF_TYPE_OFF)) {
                        Operator op = this.ops[7];
                        if ((op.vibrato) && (op.opState != OF_TYPE_OFF)) {
                            vibVal1 = vibValVar1;
                            for (int i = 0; i < endSamples; i++)
                                vibVal1[i] = (vibLut[i] * op.freqHigh / 8) * FIXEDPT * VIBFAC;
                        } else
                            vibVal1 = vibValConst;
                        Operator cPtr9 = this.ops[7 + 9];
                        if ((cPtr9.vibrato) && (cPtr9.opState == OF_TYPE_OFF)) {
                            vibVal2 = vibval_var2;
                            for (int i = 0; i < endSamples; i++)
                                vibVal2[i] = (vibLut[i] * cPtr9.freqHigh / 8) * FIXEDPT * VIBFAC;
                        } else
                            vibVal2 = vibValConst;

                        if (op.tremolo)
                            tremVal1 = tremLut; // tremolo enabled, use table
                        else
                            tremVal1 = tremValConst;
                        if (cPtr9.tremolo)
                            tremVal2 = tremLut; // tremolo enabled, use table
                        else
                            tremVal2 = tremValConst;

                        op = this.ops[8];
                        cPtr9 = this.ops[8 + 9];
                        if ((cPtr9.vibrato) && (cPtr9.opState == OF_TYPE_OFF)) {
                            vibVal4 = vibval_var2;
                            for (int i = 0; i < endSamples; i++)
                                vibVal4[i] = (vibLut[i] * cPtr9.freqHigh / 8) * FIXEDPT * VIBFAC;
                        } else
                            vibVal4 = vibValConst;

                        if (cPtr9.tremolo) tremVal4 = tremLut; // tremolo enabled, use table
                        else tremVal4 = tremValConst;

                        // calculate channel output
                        op = this.ops[0]; // set op to something useful (else it stays at Op[8])
                        ccPtr = 0;
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
                                outBufL[i] += chanVal * this.ops[ccPtr + 7].leftPan;
                                outBufR[i] += chanVal * this.ops[ccPtr + 7].rightPan;
                            } else {
                                outBufL[i] += chanVal;
                                outBufR[i] += chanVal;
                            }

                            chanVal = this.ops[8 + 9].cVal * 2;

                            if ((this.adlibReg[0x105] & 1) != 0) {
                                outBufL[i] += chanVal * this.ops[ccPtr + 8].leftPan;
                                outBufR[i] += chanVal * this.ops[ccPtr + 8].rightPan;
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

                    int k = curCh;
                    Operator op;
                    if (curCh < 9) {
                        op = this.ops[curCh];
                        ccPtr = curCh;
                    } else {
                        op = this.ops[curCh + 9]; // second set is operator18-operator35
                        ccPtr = curCh + 9;
                        k += (-9 + 256); // second set uses registers 0x100 onwards
                    }
                    // check if this Operator is part of a 4-Op
                    if (op.is4OpAttached) continue; // this is more correct

                    // check for FM/AM
                    if ((this.adlibReg[ARC_FEEDBACK + k] & 1) != 0) {
                        if (op.is4op) { // this is more correct
                            if ((this.adlibReg[ARC_FEEDBACK + k + 3] & 1) != 0) {
                                // AM-AM-style synthesis (op1[fb] + (op2 * op3) + op4)
                                if (op.opState != OF_TYPE_OFF) {
                                    if (op.vibrato) {
                                        vibVal1 = vibValVar1;
                                        for (int i = 0; i < endSamples; i++)
                                            vibVal1[i] = (vibLut[i] * op.freqHigh / 8) * FIXEDPT * VIBFAC;
                                    } else
                                        vibVal1 = vibValConst;
                                    if (op.tremolo)
                                        tremVal1 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal1 = tremValConst;

                                    // calculate channel output
                                    for (int i = 0; i < endSamples; i++) {
                                        op.advance(vibVal1[i], this.generatorAdd);
                                        opFuncs[op.opState].accept(op);
                                        op.output((op.lastcval + op.cVal) * op.mfbi / 2, tremVal1[i]);

                                        int chanVal = op.cVal;
                                        if ((this.adlibReg[0x105] & 1) != 0) {
                                            outBufL[i] += chanVal * this.ops[ccPtr + 3].leftPan;
                                            outBufR[i] += chanVal * this.ops[ccPtr + 3].rightPan;
                                        } else {
                                            outBufL[i] += chanVal;
                                            outBufR[i] += chanVal;
                                        }
                                    }
                                }

                                if ((this.ops[ccPtr + 3].opState != OF_TYPE_OFF) || (this.ops[ccPtr + 9].opState != OF_TYPE_OFF)) {
                                    if ((this.ops[ccPtr + 9].vibrato) && (this.ops[ccPtr + 9].opState != OF_TYPE_OFF)) {
                                        vibVal1 = vibValVar1;
                                        for (int i = 0; i < endSamples; i++)
                                            vibVal1[i] = (vibLut[i] * this.ops[ccPtr + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                                    } else
                                        vibVal1 = vibValConst;
                                    if (this.ops[ccPtr + 9].tremolo)
                                        tremVal1 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal1 = tremValConst;
                                    if (this.ops[ccPtr + 3].tremolo)
                                        tremVal2 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal2 = tremValConst;

                                    // calculate channel output
                                    for (int i = 0; i < endSamples; i++) {
                                        this.ops[ccPtr + 9].advance(vibVal1[i], this.generatorAdd);
                                        opFuncs[this.ops[ccPtr + 9].opState].accept(this.ops[ccPtr + 9]);
                                        this.ops[ccPtr + 9].output(0, tremVal1[i]);

                                        this.ops[ccPtr + 3].advance(0, this.generatorAdd);
                                        opFuncs[this.ops[ccPtr + 3].opState].accept(this.ops[ccPtr + 3]);
                                        this.ops[ccPtr + 3].output(this.ops[ccPtr + 9].cVal * FIXEDPT, tremVal2[i]);

                                        int chanVal = this.ops[ccPtr + 3].cVal;
                                        if ((this.adlibReg[0x105] & 1) != 0) {
                                            outBufL[i] += chanVal * this.ops[ccPtr + 3].leftPan;
                                            outBufR[i] += chanVal * this.ops[ccPtr + 3].rightPan;
                                        } else {
                                            outBufL[i] += chanVal;
                                            outBufR[i] += chanVal;
                                        }
                                    }
                                }

                                if (this.ops[ccPtr + 3 + 9].opState != OF_TYPE_OFF) {
                                    if (this.ops[ccPtr + 3 + 9].tremolo)
                                        tremVal1 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal1 = tremValConst;

                                    // calculate channel output
                                    for (int i = 0; i < endSamples; i++) {
                                        this.ops[ccPtr + 3 + 9].advance(0, this.generatorAdd);
                                        opFuncs[this.ops[ccPtr + 3 + 9].opState].accept(this.ops[ccPtr + 3 + 9]);
                                        this.ops[ccPtr + 3 + 9].output(0, tremVal1[i]);

                                        int chanVal = this.ops[ccPtr + 3 + 9].cVal;
                                        if ((this.adlibReg[0x105] & 1) != 0) {
                                            outBufL[i] += chanVal * this.ops[ccPtr + 3].leftPan;
                                            outBufR[i] += chanVal * this.ops[ccPtr + 3].rightPan;
                                        } else {
                                            outBufL[i] += chanVal;
                                            outBufR[i] += chanVal;
                                        }
                                    }
                                }
                            } else {
                                // AM-FM-style synthesis (op1[fb] + (op2 * op3 * op4))
                                if (this.ops[ccPtr + 0].opState != OF_TYPE_OFF) {
                                    if (this.ops[ccPtr + 0].vibrato) {
                                        vibVal1 = vibValVar1;
                                        for (int i = 0; i < endSamples; i++)
                                            vibVal1[i] = (vibLut[i] * this.ops[ccPtr + 0].freqHigh / 8) * FIXEDPT * VIBFAC;
                                    } else
                                        vibVal1 = vibValConst;
                                    if (this.ops[ccPtr + 0].tremolo)
                                        tremVal1 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal1 = tremValConst;

                                    // calculate channel output
                                    for (int i = 0; i < endSamples; i++) {
                                        this.ops[ccPtr + 0].advance(vibVal1[i], this.generatorAdd);
                                        opFuncs[this.ops[ccPtr + 0].opState].accept(this.ops[ccPtr + 0]);
                                        this.ops[ccPtr + 0].output((this.ops[ccPtr + 0].lastcval + this.ops[ccPtr + 0].cVal) * this.ops[ccPtr + 0].mfbi / 2, tremVal1[i]);

                                        int chanVal = this.ops[ccPtr + 0].cVal;
                                        if ((this.adlibReg[0x105] & 1) != 0) {
                                            outBufL[i] += chanVal * this.ops[ccPtr + 3].leftPan;
                                            outBufR[i] += chanVal * this.ops[ccPtr + 3].rightPan;
                                        } else {
                                            outBufL[i] += chanVal;
                                            outBufR[i] += chanVal;
                                        }
                                    }
                                }

                                if ((this.ops[ccPtr + 9].opState != OF_TYPE_OFF) || (this.ops[ccPtr + 3].opState != OF_TYPE_OFF) || (this.ops[ccPtr + 3 + 9].opState != OF_TYPE_OFF)) {
                                    if ((this.ops[ccPtr + 9].vibrato) && (this.ops[ccPtr + 9].opState != OF_TYPE_OFF)) {
                                        vibVal1 = vibValVar1;
                                        for (int i = 0; i < endSamples; i++)
                                            vibVal1[i] = (vibLut[i] * this.ops[ccPtr + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                                    } else
                                        vibVal1 = vibValConst;
                                    if (this.ops[ccPtr + 9].tremolo)
                                        tremVal1 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal1 = tremValConst;
                                    if (this.ops[ccPtr + 3].tremolo)
                                        tremVal2 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal2 = tremValConst;
                                    if (this.ops[ccPtr + 3 + 9].tremolo)
                                        tremVal3 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal3 = tremValConst;

                                    // calculate channel output
                                    for (int i = 0; i < endSamples; i++) {
                                        this.ops[ccPtr + 9].advance(vibVal1[i], this.generatorAdd);
                                        opFuncs[this.ops[ccPtr + 9].opState].accept(this.ops[ccPtr + 9]);
                                        this.ops[ccPtr + 9].output(0, tremVal1[i]);

                                        this.ops[ccPtr + 3].advance(0, this.generatorAdd);
                                        opFuncs[this.ops[ccPtr + 3].opState].accept(this.ops[ccPtr + 3]);
                                        this.ops[ccPtr + 3].output(this.ops[ccPtr + 9].cVal * FIXEDPT, tremVal2[i]);

                                        this.ops[ccPtr + 3 + 9].advance(0, this.generatorAdd);
                                        opFuncs[this.ops[ccPtr + 3 + 9].opState].accept(this.ops[ccPtr + 3 + 9]);
                                        this.ops[ccPtr + 3 + 9].output(this.ops[ccPtr + 3].cVal * FIXEDPT, tremVal3[i]);

                                        int chanVal = this.ops[ccPtr + 3 + 9].cVal;
                                        if ((this.adlibReg[0x105] & 1) != 0) {
                                            outBufL[i] += chanVal * this.ops[ccPtr + 3].leftPan;
                                            outBufR[i] += chanVal * this.ops[ccPtr + 3].rightPan;
                                        } else {
                                            outBufL[i] += chanVal;
                                            outBufR[i] += chanVal;
                                        }
                                    }
                                }
                            }
                            continue;
                        }
                        // 2op additive synthesis
                        if ((this.ops[ccPtr + 9].opState == OF_TYPE_OFF) && (this.ops[ccPtr + 0].opState == OF_TYPE_OFF))
                            continue;
                        if ((this.ops[ccPtr + 0].vibrato) && (this.ops[ccPtr + 0].opState != OF_TYPE_OFF)) {
                            vibVal1 = vibValVar1;
                            for (int i = 0; i < endSamples; i++)
                                vibVal1[i] = (vibLut[i] * this.ops[ccPtr + 0].freqHigh / 8) * FIXEDPT * VIBFAC;
                        } else
                            vibVal1 = vibValConst;
                        if ((this.ops[ccPtr + 9].vibrato) && (this.ops[ccPtr + 9].opState != OF_TYPE_OFF)) {
                            vibVal2 = vibval_var2;
                            for (int i = 0; i < endSamples; i++)
                                vibVal2[i] = (vibLut[i] * this.ops[ccPtr + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                        } else
                            vibVal2 = vibValConst;
                        if (this.ops[ccPtr + 0].tremolo)
                            tremVal1 = tremLut; // tremolo enabled, use table
                        else
                            tremVal1 = tremValConst;
                        if (this.ops[ccPtr + 9].tremolo)
                            tremVal2 = tremLut; // tremolo enabled, use table
                        else
                            tremVal2 = tremValConst;

                        // calculate channel output
                        for (int i = 0; i < endSamples; i++) {
                            // carrier1
                            this.ops[ccPtr + 0].advance(vibVal1[i], this.generatorAdd);
                            opFuncs[this.ops[ccPtr + 0].opState].accept(this.ops[ccPtr + 0]);

                            this.ops[ccPtr + 0].output((this.ops[ccPtr + 0].lastcval + this.ops[ccPtr + 0].cVal) * this.ops[ccPtr + 0].mfbi / 2, tremVal1[i]);

                            // carrier2
                            this.ops[ccPtr + 9].advance(vibVal2[i], this.generatorAdd);
                            opFuncs[this.ops[ccPtr + 9].opState].accept(this.ops[ccPtr + 9]);

                            this.ops[ccPtr + 9].output(0, tremVal2[i]);

                            int chanVal = this.ops[ccPtr + 9].cVal + this.ops[ccPtr + 0].cVal;

                            if ((this.adlibReg[0x105] & 1) != 0) {
                                outBufL[i] += chanVal * this.ops[ccPtr + 0].leftPan;
                                outBufR[i] += chanVal * this.ops[ccPtr + 0].rightPan;
                            } else {
                                outBufL[i] += chanVal;
                                outBufR[i] += chanVal;
                            }
                        }
                    } else {
                        if (op.is4op) { // this is more correct
                            if ((this.adlibReg[ARC_FEEDBACK + k + 3] & 1) != 0) {
                                // FM-AM-style synthesis ((op1[fb] * op2) + (op3 * op4))
                                if ((this.ops[ccPtr + 0].opState != OF_TYPE_OFF) || (this.ops[ccPtr + 9].opState != OF_TYPE_OFF)) {
                                    if ((this.ops[ccPtr + 0].vibrato) && (this.ops[ccPtr + 0].opState != OF_TYPE_OFF)) {
                                        vibVal1 = vibValVar1;
                                        for (int i = 0; i < endSamples; i++)
                                            vibVal1[i] = (vibLut[i] * this.ops[ccPtr + 0].freqHigh / 8) * FIXEDPT * VIBFAC;
                                    } else
                                        vibVal1 = vibValConst;
                                    if ((this.ops[ccPtr + 9].vibrato) && (this.ops[ccPtr + 9].opState != OF_TYPE_OFF)) {
                                        vibVal2 = vibval_var2;
                                        for (int i = 0; i < endSamples; i++)
                                            vibVal2[i] = (vibLut[i] * this.ops[ccPtr + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                                    } else
                                        vibVal2 = vibValConst;
                                    if (this.ops[ccPtr + 0].tremolo)
                                        tremVal1 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal1 = tremValConst;
                                    if (this.ops[ccPtr + 9].tremolo)
                                        tremVal2 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal2 = tremValConst;

                                    // calculate channel output
                                    for (int i = 0; i < endSamples; i++) {
                                        this.ops[ccPtr + 0].advance(vibVal1[i], this.generatorAdd);
                                        opFuncs[this.ops[ccPtr + 0].opState].accept(this.ops[ccPtr + 0]);
                                        this.ops[ccPtr + 0].output((this.ops[ccPtr + 0].lastcval + this.ops[ccPtr + 0].cVal) * this.ops[ccPtr + 0].mfbi / 2, tremVal1[i]);

                                        this.ops[ccPtr + 9].advance(vibVal2[i], this.generatorAdd);
                                        opFuncs[this.ops[ccPtr + 9].opState].accept(this.ops[ccPtr + 9]);
                                        this.ops[ccPtr + 9].output(this.ops[ccPtr + 0].cVal * FIXEDPT, tremVal2[i]);

                                        int chanVal = this.ops[ccPtr + 9].cVal;
                                        if ((this.adlibReg[0x105] & 1) != 0) {
                                            outBufL[i] += chanVal * this.ops[ccPtr + 3].leftPan;
                                            outBufR[i] += chanVal * this.ops[ccPtr + 3].rightPan;
                                        } else {
                                            outBufL[i] += chanVal;
                                            outBufR[i] += chanVal;
                                        }
                                    }
                                }

                                if ((this.ops[ccPtr + 3].opState != OF_TYPE_OFF) || (this.ops[ccPtr + 3 + 9].opState != OF_TYPE_OFF)) {
                                    if (this.ops[ccPtr + 3].tremolo)
                                        tremVal1 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal1 = tremValConst;
                                    if (this.ops[ccPtr + 3 + 9].tremolo)
                                        tremVal2 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal2 = tremValConst;

                                    // calculate channel output
                                    for (int i = 0; i < endSamples; i++) {
                                        this.ops[ccPtr + 3].advance(0, this.generatorAdd);
                                        opFuncs[this.ops[ccPtr + 3].opState].accept(this.ops[ccPtr + 3]);
                                        this.ops[ccPtr + 3].output(0, tremVal1[i]);

                                        this.ops[ccPtr + 3 + 9].advance(0, this.generatorAdd);
                                        opFuncs[this.ops[ccPtr + 3 + 9].opState].accept(this.ops[ccPtr + 3 + 9]);
                                        this.ops[ccPtr + 3 + 9].output(this.ops[ccPtr + 3].cVal * FIXEDPT, tremVal2[i]);

                                        int chanVal = this.ops[ccPtr + 3 + 9].cVal;
                                        if ((this.adlibReg[0x105] & 1) != 0) {
                                            outBufL[i] += chanVal * this.ops[ccPtr + 3].leftPan;
                                            outBufR[i] += chanVal * this.ops[ccPtr + 3].rightPan;
                                        } else {
                                            outBufL[i] += chanVal;
                                            outBufR[i] += chanVal;
                                        }
                                    }
                                }
                            } else {
                                // FM-FM-style synthesis (op1[fb] * op2 * op3 * op4)
                                if ((this.ops[ccPtr + 0].opState != OF_TYPE_OFF) || (this.ops[ccPtr + 9].opState != OF_TYPE_OFF) ||
                                        (this.ops[ccPtr + 3].opState != OF_TYPE_OFF) || (this.ops[ccPtr + 3 + 9].opState != OF_TYPE_OFF)) {
                                    if ((this.ops[ccPtr + 0].vibrato) && (this.ops[ccPtr + 0].opState != OF_TYPE_OFF)) {
                                        vibVal1 = vibValVar1;
                                        for (int i = 0; i < endSamples; i++)
                                            vibVal1[i] = (vibLut[i] * this.ops[ccPtr + 0].freqHigh / 8) * FIXEDPT * VIBFAC;
                                    } else
                                        vibVal1 = vibValConst;
                                    if ((this.ops[ccPtr + 9].vibrato) && (this.ops[ccPtr + 9].opState != OF_TYPE_OFF)) {
                                        vibVal2 = vibval_var2;
                                        for (int i = 0; i < endSamples; i++)
                                            vibVal2[i] = (vibLut[i] * this.ops[ccPtr + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                                    } else
                                        vibVal2 = vibValConst;
                                    if (this.ops[ccPtr + 0].tremolo)
                                        tremVal1 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal1 = tremValConst;
                                    if (this.ops[ccPtr + 9].tremolo)
                                        tremVal2 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal2 = tremValConst;
                                    if (this.ops[ccPtr + 3].tremolo)
                                        tremVal3 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal3 = tremValConst;
                                    if (this.ops[ccPtr + 3 + 9].tremolo)
                                        tremVal4 = tremLut; // tremolo enabled, use table
                                    else
                                        tremVal4 = tremValConst;

                                    // calculate channel output
                                    for (int i = 0; i < endSamples; i++) {
                                        this.ops[ccPtr + 0].advance(vibVal1[i], this.generatorAdd);
                                        opFuncs[this.ops[ccPtr + 0].opState].accept(this.ops[ccPtr + 0]);
                                        this.ops[ccPtr + 0].output((this.ops[ccPtr + 0].lastcval + this.ops[ccPtr + 0].cVal) * this.ops[ccPtr + 0].mfbi / 2, tremVal1[i]);

                                        this.ops[ccPtr + 9].advance(vibVal2[i], this.generatorAdd);
                                        opFuncs[this.ops[ccPtr + 9].opState].accept(this.ops[ccPtr + 9]);
                                        this.ops[ccPtr + 9].output(this.ops[ccPtr + 0].cVal * FIXEDPT, tremVal2[i]);

                                        this.ops[ccPtr + 3].advance(0, this.generatorAdd);
                                        opFuncs[this.ops[ccPtr + 3].opState].accept(this.ops[ccPtr + 3]);
                                        this.ops[ccPtr + 3].output(this.ops[ccPtr + 9].cVal * FIXEDPT, tremVal3[i]);

                                        this.ops[ccPtr + 3 + 9].advance(0, this.generatorAdd);
                                        opFuncs[this.ops[ccPtr + 3 + 9].opState].accept(this.ops[ccPtr + 3 + 9]);
                                        this.ops[ccPtr + 3 + 9].output(this.ops[ccPtr + 3].cVal * FIXEDPT, tremVal4[i]);

                                        int chanVal = this.ops[ccPtr + 3 + 9].cVal;
                                        if ((this.adlibReg[0x105] & 1) != 0) {
                                            outBufL[i] += chanVal * this.ops[ccPtr + 3].leftPan;
                                            outBufR[i] += chanVal * this.ops[ccPtr + 3].rightPan;
                                        } else {
                                            outBufL[i] += chanVal;
                                            outBufR[i] += chanVal;
                                        }
                                    }
                                }
                            }
                            continue;
                        }
                        // 2op frequency modulation
                        if ((this.ops[ccPtr + 9].opState == OF_TYPE_OFF) && (this.ops[ccPtr + 0].opState == OF_TYPE_OFF))
                            continue;
                        if ((this.ops[ccPtr + 0].vibrato) && (this.ops[ccPtr + 0].opState != OF_TYPE_OFF)) {
                            vibVal1 = vibValVar1;
                            for (int i = 0; i < endSamples; i++)
                                vibVal1[i] = (vibLut[i] * this.ops[ccPtr + 0].freqHigh / 8) * FIXEDPT * VIBFAC;
                        } else
                            vibVal1 = vibValConst;
                        if ((this.ops[ccPtr + 9].vibrato) && (this.ops[ccPtr + 9].opState != OF_TYPE_OFF)) {
                            vibVal2 = vibval_var2;
                            for (int i = 0; i < endSamples; i++)
                                vibVal2[i] = (vibLut[i] * this.ops[ccPtr + 9].freqHigh / 8) * FIXEDPT * VIBFAC;
                        } else
                            vibVal2 = vibValConst;
                        if (this.ops[ccPtr + 0].tremolo)
                            tremVal1 = tremLut; // tremolo enabled, use table
                        else
                            tremVal1 = tremValConst;
                        if (this.ops[ccPtr + 9].tremolo)
                            tremVal2 = tremLut; // tremolo enabled, use table
                        else
                            tremVal2 = tremValConst;

                        // calculate channel output
                        for (int i = 0; i < endSamples; i++) {
                            // modulator
                            this.ops[ccPtr + 0].advance(vibVal1[i], this.generatorAdd);
                            opFuncs[this.ops[ccPtr + 0].opState].accept(this.ops[ccPtr + 0]);

                            this.ops[ccPtr + 0].output((this.ops[ccPtr + 0].lastcval + this.ops[ccPtr + 0].cVal) * this.ops[ccPtr + 0].mfbi / 2, tremVal1[i]);

                            // carrier
                            this.ops[ccPtr + 9].advance(vibVal2[i], this.generatorAdd);
                            opFuncs[this.ops[ccPtr + 9].opState].accept(this.ops[ccPtr + 9]);

                            this.ops[ccPtr + 9].output(this.ops[ccPtr + 0].cVal * FIXEDPT, tremVal2[i]);

                            int chanVal = this.ops[ccPtr + 9].cVal;

                            if ((this.adlibReg[0x105] & 1) != 0) {
                                outBufL[i] += chanVal * this.ops[ccPtr + 0].leftPan;
                                outBufR[i] += chanVal * this.ops[ccPtr + 0].rightPan;
                            } else {
                                outBufL[i] += chanVal;
                                outBufR[i] += chanVal;
                            }
                        }
                    }
                }

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

                //System.err.printf("bufl:%d bufr:%d", outBufL[curSmp], outBufR[curSmp]);
            }
        }

        public void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < NUM_CHANNELS + 5; curChn++)
                this.muteChn[curChn] = (byte) ((muteMask >> curChn) & 0x01);
        }
    }

    @Override
    public String getName() {
        return "YMF262";
    }

    @Override
    public String getShortName() {
        return "Opl3";
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        return YMF262_Write(chipId, adr, (byte) data);
    }
}
