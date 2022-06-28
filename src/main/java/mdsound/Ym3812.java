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
    public void reset(byte chipId) {
        device_reset_ym3812(chipId);
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    private static final int DefaultYM3812ClockValue = 3579545;

    @Override
    public int start(byte chipId, int clock) {
        return start(chipId, DefaultYM3812ClockValue, 44100);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        return device_start_ym3812(chipId, clockValue);
    }

    @Override
    public void stop(byte chipId) {
        device_stop_ym3812(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        ym3812_stream_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        ym3812_control_port_w(chipId, 0, (byte) adr);
        ym3812_write_port_w(chipId, 0, (byte) data);
        return 0;
    }

    private static final int EC_DBOPL = 0x00; // DosBox OPL (AdLibEmu)
    private static final int EC_MAME = 0x01; // YM3826 core from MAME

    public static class Ym3812State {
        public Opl chip;
    }

    private byte EMU_CORE = 0x00;

    private static final int MAX_CHIPS = 0x02;

    private Ym3812State[] ym3812Data = new Ym3812State[] {new Ym3812State(), new Ym3812State()};

    public interface Updatehandler extends Consumer<Ym3812State> {
    }

    private void irqHandler(Ym3812State param, int irq) {
        Ym3812State info = param;
    }

    private void timerHandler(Ym3812State param, int c, int period) {
        Ym3812State info = param;
        if (period == 0) { // Reset FM Timer
        } else { // Start FM Timer
        }
    }

    private void ym3812_stream_update(byte chipId, int[][] outputs, int samples) {
        Ym3812State info = ym3812Data[chipId];
        switch (EMU_CORE) {
        case EC_MAME:
            break;
        case EC_DBOPL:
            info.chip.getSample(outputs, samples);
            break;
        }
    }

    private int[][] DUMMYBUF = new int[][] {null, null};

    private void _stream_update(Ym3812State param/*, int interval*/) {
        Ym3812State info = param;

        switch (EMU_CORE) {
        case EC_MAME:
            break;
        case EC_DBOPL:
            info.chip.getSample(DUMMYBUF, 0);
            break;
        }
    }

    private int device_start_ym3812(byte chipId, int clock) {
        Ym3812State info;
        int rate;

        if (chipId >= MAX_CHIPS)
            return 0;

        info = ym3812Data[chipId];
        rate = (clock & 0x7FFFFFFF) / 72;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;
        //info.intf = device.static_config ? (final ym3812_interface *)device.static_config : &dummy;
        //info.intf = &dummy;
        //info.device = device;

        // stream system initialize
        switch (EMU_CORE) {
        case EC_MAME:
            break;
        case EC_DBOPL:
            info.chip = new Opl(clock & 0x7FFFFFFF, rate, this::_stream_update, info);
            break;
        }

        return rate;
    }

    private void device_stop_ym3812(byte chipId) {
        Ym3812State info = ym3812Data[chipId];
        switch (EMU_CORE) {
        case EC_MAME:
            break;
        case EC_DBOPL:
            info.chip.stop();
            break;
        }
    }

    private void device_reset_ym3812(byte chipId) {
        Ym3812State info = ym3812Data[chipId];
        switch (EMU_CORE) {
        case EC_MAME:
            break;
        case EC_DBOPL:
            info.chip.reset();
            break;
        }
    }

    private byte ym3812_r(byte chipId, int offset) {
        Ym3812State info = ym3812Data[chipId];
        switch (EMU_CORE) {
        case EC_MAME:
        case EC_DBOPL:
            return (byte) info.chip.readReg(offset & 0x01);
        default:
            return 0x00;
        }
    }

    private void ym3812_w(byte chipId, int offset, byte data) {
        Ym3812State info = ym3812Data[chipId];
        if (info == null || info.chip == null) return;

        switch (EMU_CORE) {
        case EC_MAME:
            break;
        case EC_DBOPL:
            info.chip.writeIO(offset & 1, data);
            break;
        }
    }

    private byte ym3812_status_port_r(byte chipId, int offset) {
        return ym3812_r(chipId, 0);
    }

    private byte ym3812_read_port_r(byte chipId, int offset) {
        return ym3812_r(chipId, 1);
    }

    private void ym3812_control_port_w(byte chipId, int offset, byte data) {
        ym3812_w(chipId, 0, data);
    }

    private void ym3812_write_port_w(byte chipId, int offset, byte data) {
        ym3812_w(chipId, 1, data);
    }

    public void ym3812_set_emu_core(byte Emulator) {
        EMU_CORE = (byte) ((Emulator < 0x02) ? Emulator : 0x00);
    }

    private void ym3812_set_mute_mask(byte chipId, int muteMask) {
        Ym3812State info = ym3812Data[chipId];
        switch (EMU_CORE) {
        case EC_MAME:
            break;
        case EC_DBOPL:
            info.chip.setMuteMask(muteMask);
            break;
        }
    }

    /**
     * Generic get_info
     */
    /*DEVICE_GET_INFO( Ym3812 ) {
        switch (state) {
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
    public static class Opl {

        private static final int NUM_CHANNELS = 9;

        private static final int MAXOPERATORS = (NUM_CHANNELS * 2);


        private static final double FL05 = 0.5;
        private static final double FL2 = 2.0;

        // fixed-point calculations using 16+16
        private static final int FIXEDPT = 0x10000;
        // fixed-point calculations using 8+24
        private static final int FIXEDPT_LFO = 0x1000000;

        // waveform precision (10 bits)
        private static final int WAVEPREC = 1024;

        // clocking of the chip
        private double intFreqU(double n) {
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

        private static final int ARC_SECONDSET = 0x100; // second Operator set for Opl3


        private static final int OP_ACT_OFF = 0x00;
        private static final int OP_ACT_NORMAL = 0x01; // regular channel activated (bitmasked)
        private static final int OP_ACT_PERC = 0x02; // percussion channel activated (bitmasked)

        private static final int BLOCKBUF_SIZE = 512;


        // vibrato constants
        private static final int VIBTAB_SIZE = 8;
        private static final double VIBFAC = 70 / 50000d; // no braces, integer mul/div

        // tremolo constants and table
        private static final int TREMTAB_SIZE = 53;
        private static final double TREM_FREQ = 3.7; // tremolo at 3.7hz

        // frequency multiplicator lookup table
        private static final double[] frqmul_tab = new double[] {
                0.5, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 12, 12, 15, 15
        };

        // map a register base to a modulator Operator number or Operator number
        private static final byte[] regBase2modOp = new byte[] {
                0, 1, 2, 0, 1, 2, 0, 0, 3, 4, 5, 3, 4, 5, 0, 0, 6, 7, 8, 6, 7, 8
        };


        // map a channel number to the register offset of the modulator (=register base)
        private static final byte[] modulatorbase = new byte[] {
                0, 1, 2,
                8, 9, 10,
                16, 17, 18
        };

        private static final byte[] regbase2op = new byte[] {
                0, 1, 2, 9, 10, 11, 0, 0, 3, 4, 5, 12, 13, 14, 0, 0, 6, 7, 8, 15, 16, 17
        };

        // vibrato value tables (used per-Operator)
        private int[] vibval_var1 = new int[BLOCKBUF_SIZE];
        private int[] vibval_var2 = new int[BLOCKBUF_SIZE];

        private interface OpFuncs extends Consumer<Op> {
        }

        private OpFuncs[] opFuncs = new OpFuncs[] {
                Op::attack,
                Op::decay,
                Op::release,
                Op::sustain, // sustain phase (keeping level)
                Op::release, // sustain_nokeep phase (release-style)
                Op::off
        };

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
        public static class Op {
            private static short[] wavTable = new short[WAVEPREC * 3]; // wave form table

            // vibrato/tremolo tables
            private static int[] vibTable = new int[VIBTAB_SIZE];
            private static int[] tremTable = new int[TREMTAB_SIZE * 2];

            private static int[] vibValConst = new int[BLOCKBUF_SIZE];
            private static int[] tremValConst = new int[BLOCKBUF_SIZE];

            // vibrato/trmolo value table pointers
            // moved to adlib_getsample

            // key scale level lookup table
            private static final double[] ksLMul = new double[] {
                    0.0, 0.5, 0.25, 1.0     // . 0, 3, 1.5, 6 dB/oct
            };

            // calculated frequency multiplication values (depend on sampling rate)
            //static double frqmul[16]; // moved to Opl

            // key scale levels
            private static byte[][] ksLev = new byte[][] {
                    new byte[16], new byte[16], new byte[16], new byte[16],
                    new byte[16], new byte[16], new byte[16], new byte[16]
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
            private static final int[] waveMask = new int[] {
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
            private static final double[] attackConst = new double[] {
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

            static {
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
                    double trem_val2 = (double) (tremTableInt[i] / 4) * 1.2 / 6.0 / 6.0; // 1.2db (larger stepping)

                    tremTable[i] = (int) (Math.pow(FL2, trem_val1) * FIXEDPT);
                    tremTable[TREMTAB_SIZE + i] = (int) (Math.pow(FL2, trem_val2) * FIXEDPT);
                }

                //
                for (int i = 0; i < BLOCKBUF_SIZE; i++) tremValConst[i] = FIXEDPT;


                // create waveform tables
                for (int i = 0; i < (WAVEPREC >> 1); i++) {
                    wavTable[(i << 1) + WAVEPREC] = (short) (16384 * Math.sin((double) ((i << 1)) * Math.PI * 2 / WAVEPREC));
                    wavTable[(i << 1) + 1 + WAVEPREC] = (short) (16384 * Math.sin((double) ((i << 1) + 1) * Math.PI * 2 / WAVEPREC));
                    wavTable[i] = wavTable[(i << 1) + WAVEPREC];
                    // alternative: (zero-less)
                    /*   wavtable[(i<<1)  +WAVEPREC] = (short)(16384*sin((double)((i<<2)+1)*PI/WAVEPREC));
                                wavtable[(i<<1)+1+WAVEPREC] = (short)(16384*sin((double)((i<<2)+3)*PI/WAVEPREC));
                                wavtable[i]     = wavtable[(i<<1)-1+WAVEPREC]; */
                }
                for (int i = 0; i < (WAVEPREC >> 3); i++) {
                    wavTable[i + (WAVEPREC << 1)] = (short) (wavTable[i + (WAVEPREC >> 3)] - 16384);
                    wavTable[i + ((WAVEPREC * 17) >> 3)] = (short) (wavTable[i + (WAVEPREC >> 2)] + 16384);
                }

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

            private static Random rand = new Random();

            private static void advanceDrums(Op op_pt1, int vib1, Op op_pt2, int vib2, Op op_pt3, int vib3, int generatorAdd) {
                int c1 = op_pt1.tcount / FIXEDPT;
                int c3 = op_pt3.tcount / FIXEDPT;
                int phasebit = (((c1 & 0x88) ^ ((c1 << 5) & 0x80)) | ((c3 ^ (c3 << 2)) & 0x20)) != 0 ? 0x02 : 0x00;

                int noisebit = rand.nextInt() & 1;

                int snare_phase_bit = (((op_pt1.tcount / FIXEDPT) / 0x100) & 1);

                // Hihat
                int inttm = (phasebit << 8) | (0x34 << (phasebit ^ (noisebit << 1)));
                op_pt1.wfPos = inttm * FIXEDPT; // waveform position
                // advance waveform time
                op_pt1.tcount += op_pt1.tinc;
                op_pt1.tcount += op_pt1.tinc * vib1 / FIXEDPT;
                op_pt1.generatorPos += generatorAdd;

                // Snare
                inttm = ((1 + snare_phase_bit) ^ noisebit) << 8;
                op_pt2.wfPos = inttm * FIXEDPT; // waveform position
                // advance waveform time
                op_pt2.tcount += op_pt2.tinc;
                op_pt2.tcount += op_pt2.tinc * vib2 / FIXEDPT;
                op_pt2.generatorPos += generatorAdd;

                // Cymbal
                inttm = (1 + phasebit) << 8;
                op_pt3.wfPos = inttm * FIXEDPT; // waveform position
                // advance waveform time
                op_pt3.tcount += op_pt3.tinc;
                op_pt3.tcount += op_pt3.tinc * vib3 / FIXEDPT;
                op_pt3.generatorPos += generatorAdd;
            }

            private void advance(int vib, int generatorAdd) {
                wfPos = tcount; // waveform position

                // advance waveform time
                tcount += tinc;
                tcount += tinc * vib / FIXEDPT;

                generatorPos += generatorAdd;
            }

            private void changeDecayRate(int decayRate, double recipSamp) {
                // decaymul should be 1.0 when decayRate==0
                if (decayRate != 0) {
                    int steps;

                    double f = -7.4493 * decrelconst[tOff & 3] * recipSamp;
                    decaymul = Math.pow(FL2, f * Math.pow(FL2, decayRate + (tOff >> 2)));
                    steps = (decayRate * 4 + tOff) >> 2;
                    env_step_d = (1 << (steps <= 12 ? 12 - steps : 0)) - 1;
                } else {
                    decaymul = 1.0;
                    env_step_d = 0;
                }
            }

            private void changeReleaseRate(int releaseRate, double recipSamp) {
                // releasemul should be 1.0 when releaseRate==0
                if (releaseRate != 0) {
                    int steps;

                    double f = -7.4493 * decrelconst[tOff & 3] * recipSamp;
                    releasemul = Math.pow(FL2, f * Math.pow(FL2, releaseRate + (tOff >> 2)));
                    steps = (releaseRate * 4 + tOff) >> 2;
                    envStepR = (1 << (steps <= 12 ? 12 - steps : 0)) - 1;
                } else {
                    releasemul = 1.0;
                    envStepR = 0;
                }
            }

            private void changeSustainLevel(int sustainLevel) {
                // sustainLevel should be 0.0 when sustainLevel==15 (max)
                if (sustainLevel < 15) {
                    this.sustainLevel = Math.pow(FL2, (double) sustainLevel * (-FL05));
                } else {
                    this.sustainLevel = 0.0;
                }
            }

            private void changeWaveForm(int regBase, byte[] waveSel) {
                // waveform selection
                curWMask = waveMask[waveSel[regBase]];
                curWForm = wavTable;
                curWFormPtr = waveform[waveSel[regBase]];
                // (might need to be adapted to waveform type here...)
            }

            private void changeKeepSustain(boolean susKeep) {
                if (opState == OF_TYPE_SUS) {
                    if (!susKeep)
                        opState = OF_TYPE_SUS_NOKEEP;
                } else if (opState == OF_TYPE_SUS_NOKEEP) {
                    if (susKeep)
                        opState = OF_TYPE_SUS;
                }
            }

            // enable/disable vibrato/tremolo LFO effects
            private void changeVibrato(int regBase, byte[] adlibReg) {
                vibrato = (adlibReg[ARC_TVS_KSR_MUL + regBase] & 0x40) != 0;
                tremolo = (adlibReg[ARC_TVS_KSR_MUL + regBase] & 0x80) != 0;
            }

            // change amount of self-feedback
            private void changeFeedback(int feedback) {
                if (feedback != 0)
                    mfbi = (int) (Math.pow(FL2, (feedback >> 1) + 8));
                else
                    mfbi = 0;
            }

            private void changeFrequency(int chanBase, int regBase, byte[] adlibReg, double[] frqMul, double recipSamp) {
                // frequency
                int frn = ((((int) adlibReg[ARC_KON_BNUM + chanBase]) & 3) << 8) + (int) adlibReg[ARC_FREQ_NUM + chanBase];
                // block number/octave
                int oct = ((((int) adlibReg[ARC_KON_BNUM + chanBase]) >> 2) & 7);
                freq_high = (frn >> 7) & 7;

                // keysplit
                int noteSel = (adlibReg[8] >> 6) & 1;
                tOff = ((frn >> 9) & (noteSel ^ 1)) | ((frn >> 8) & noteSel);
                tOff += (oct << 1);

                // envelope scaling (KSR)
                if ((adlibReg[ARC_TVS_KSR_MUL + regBase] & 0x10) == 0) tOff >>= 2;

                // 20+a0+b0:
                tinc = (int) ((((double) (frn << oct)) * frqMul[adlibReg[ARC_TVS_KSR_MUL + regBase] & 15]));
                // 40+a0+b0:
                double volIn = (double) (adlibReg[ARC_KSL_OUTLEV + regBase] & 63) +
                        ksLMul[adlibReg[ARC_KSL_OUTLEV + regBase] >> 6] * ksLev[oct][frn >> 6];
                vol = Math.pow(FL2, volIn * -0.125 - 14);

                // Operator frequency changed, care about features that depend on it
                changeAttackRate(adlibReg[ARC_ATTR_DECR + regBase] >> 4, recipSamp);
                changeDecayRate(adlibReg[ARC_ATTR_DECR + regBase] & 15, recipSamp);
                changeReleaseRate(adlibReg[ARC_SUSL_RELR + regBase] & 15, recipSamp);
            }

            private void enableOperator(int regbase, int act_type, byte[] wave_sel) {
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

            private void disableOperator(int act_type) {
                // check if this is really an on-off transition
                if (act_state != OP_ACT_OFF) {
                    act_state &= (~act_type);
                    if (act_state == OP_ACT_OFF) {
                        if (opState != OF_TYPE_OFF)
                            opState = OF_TYPE_REL;
                    }
                }
            }

            public void reset() {
                this.opState = OF_TYPE_OFF;
                this.act_state = OP_ACT_OFF;
                this.amp = 0.0;
                this.stepAmp = 0.0;
                this.vol = 0.0;
                this.tcount = 0;
                this.tinc = 0;
                this.tOff = 0;
                this.curWMask = waveMask[0];
                this.curWForm = wavTable;
                this.curWFormPtr = waveform[0];
                this.freq_high = 0;

                this.generatorPos = 0;
                this.curEnvStep = 0;
                this.envStepA = 0;
                this.env_step_d = 0;
                this.envStepR = 0;
                this.stepSkipPosA = 0;
                this.envStepSkipA = 0;
            }

            private static final byte[] stepSkipMask = new byte[] {(byte) 0xff, (byte) 0xfe, (byte) 0xee, (byte) 0xba, (byte) 0xaa};

            /** current output/last output (used for feedback) */
            public int cval, lastcval;
            /** time (position in waveform) and time increment */
            public int tcount, wfPos, tinc;
            /** and amplification (envelope) */
            public double amp, stepAmp;
            /** volume */
            public double vol;
            /** sustain level */
            public double sustainLevel;
            /** feedback amount */
            public int mfbi;
            /** attack rate function coefficients */
            public double a0, a1, a2, a3;
            /** decay/release rate functions */
            public double decaymul, releasemul;
            /** current state of Operator (attack/decay/sustain/release/off) */
            public int opState;
            public int tOff;
            /** highest three bits of the frequency, used for vibrato calculations */
            public int freq_high;
            /** start of selected waveform */
            public short[] curWForm;
            /** start of selected waveform */
            public int curWFormPtr;
            /** mask for selected waveform */
            public int curWMask;
            /** activity state (regular, percussion) */
            public int act_state;
            /** keep sustain level when decay finished */
            public boolean susKeep;
            /** vibrato/tremolo enable bits */
            public boolean vibrato, tremolo;

            // variables used to provide non-continuous envelopes

            /** for non-standard sample rates we need to determine how many samples have passed */
            public int generatorPos;
            /** current (standardized) sample position */
            public int curEnvStep;
            /** number of std samples of one step (for attack/decay/release mode) */
            public int envStepA, env_step_d, envStepR;
            /** position of 8-cyclic step skipping (always 2^x to check against mask) */
            public byte stepSkipPosA;
            /** bitmask that determines if a step is skipped (respective bit is zero then) */
            public int envStepSkipA;

            // output level is sustained, mode changes only when Operator is turned off (.release)
            // or when the keep-sustained bit is turned off (.sustain_nokeep)
            private void output(int modulator, int trem) {
                if (this.opState != OF_TYPE_OFF) {
                    int i;
                    this.lastcval = this.cval;
                    i = (this.wfPos + modulator) / FIXEDPT;

                    // wform: -16384 to 16383 (0x4000)
                    // trem :  32768 to 65535 (0x10000)
                    // step_amp: 0.0 to 1.0
                    // vol  : 1/2^14 to 1/2^29 (/0x4000; /1../0x8000)

                    this.cval = (int) (this.stepAmp * this.vol * this.curWForm[this.curWFormPtr + (i & this.curWMask)] * trem / 16.0);
                }
            }

            // no action, Operator is off
            private void off() {
            }

            // output level is sustained, mode changes only when Operator is turned off (.release)
            // or when the keep-sustained bit is turned off (.sustain_nokeep)
            private void sustain() {
                int num_steps_add = this.generatorPos / FIXEDPT; // number of (standardized) samples
                int ct;
                for (ct = 0; ct < num_steps_add; ct++) {
                    this.curEnvStep++;
                }
                this.generatorPos -= num_steps_add * FIXEDPT;
            }

            // Operator in release mode, if output level reaches zero the Operator is turned off
            private void release() {
                int num_steps_add;
                int ct;

                // ??? boundary?
                if (this.amp > 0.00000001) {
                    // release phase
                    this.amp *= this.releasemul;
                }

                num_steps_add = this.generatorPos / FIXEDPT; // number of (standardized) samples
                for (ct = 0; ct < num_steps_add; ct++) {
                    this.curEnvStep++; // sample counter
                    if ((this.curEnvStep & this.envStepR) == 0) {
                        if (this.amp <= 0.00000001) {
                            // release phase finished, turn off this Operator
                            this.amp = 0.0;
                            if (this.opState == OF_TYPE_REL) {
                                this.opState = OF_TYPE_OFF;
                            }
                        }
                        this.stepAmp = this.amp;
                    }
                }
                this.generatorPos -= num_steps_add * FIXEDPT;
            }

            // Operator in decay mode, if sustain level is reached the output level is either
            // kept (sustain level keep enabled) or the Operator is switched into release mode
            private void decay() {

                if (this.amp > this.sustainLevel) {
                    // decay phase
                    this.amp *= this.decaymul;
                }

                int numStepsAdd = this.generatorPos / FIXEDPT; // number of (standardized) samples
                for (int ct = 0; ct < numStepsAdd; ct++) {
                    this.curEnvStep++;
                    if ((this.curEnvStep & this.env_step_d) == 0) {
                        if (this.amp <= this.sustainLevel) {
                            // decay phase finished, sustain level reached
                            if (this.susKeep) {
                                // keep sustain level (until turned off)
                                this.opState = OF_TYPE_SUS;
                                this.amp = this.sustainLevel;
                            } else {
                                // next: release phase
                                this.opState = OF_TYPE_SUS_NOKEEP;
                            }
                        }
                        this.stepAmp = this.amp;
                    }
                }
                this.generatorPos -= numStepsAdd * FIXEDPT;
            }

            // Operator in attack mode, if full output level is reached,
            // the Operator is switched into decay mode
            private void attack() {

                this.amp = ((this.a3 * this.amp + this.a2) * this.amp + this.a1) * this.amp + this.a0;

                int numStepsAdd = this.generatorPos / FIXEDPT; // number of (standardized) samples
                for (int ct = 0; ct < numStepsAdd; ct++) {
                    this.curEnvStep++; // next sample
                    if ((this.curEnvStep & this.envStepA) == 0) { // check if next step already reached
                        if (this.amp > 1.0) {
                            // attack phase finished, next: decay
                            this.opState = OF_TYPE_DEC;
                            this.amp = 1.0;
                            this.stepAmp = 1.0;
                        }
                        this.stepSkipPosA <<= 1;
                        if (this.stepSkipPosA == 0) this.stepSkipPosA = 1;
                        if ((this.stepSkipPosA & this.envStepSkipA) != 0) { // check if required to skip next step
                            this.stepAmp = this.amp;
                        }
                    }
                }
                this.generatorPos -= numStepsAdd * FIXEDPT;
            }

            private void checkEgAttack() {
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

            private void changeAttackRate(int attackRate, double recipSamp) {
                if (attackRate != 0) {

                    double f = Math.pow(FL2, (double) attackRate + (tOff >> 2) - 1) * attackConst[tOff & 3] * recipSamp;
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

                    if (stepSkip >= 62) {
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
        }

        // per-chip variables
        public Op[] ops = new Op[MAXOPERATORS];
        public byte[] muteChn = new byte[NUM_CHANNELS + 5];
        public int chipClock;

        public int intSampleRate;

        public byte status;
        public int oplIndex;
        public int oplAddr;
        // adlib register set
        public byte[] adlibReg = new byte[256];
        // waveform selection
        public byte[] waveSel = new byte[22];

        // vibrato/tremolo increment/counter
        public int vibtabPos;
        public int vibtabAdd;
        public int tremtabPos;
        public int tremtabAdd;

        // should be a chip parameter
        public int generatorAdd;

        // inverse of sampling rate
        public double recipSamp;
        public double[] frqMul = new double[16];

        // stream update handler
        public Updatehandler updateHandler;
        // stream update parameter
        public Ym3812State updateParam;

        Opl(int clock, int sampleRate, Updatehandler updateHandler, Ym3812State param) {

            this.chipClock = clock;
            this.intSampleRate = sampleRate;
            this.updateHandler = updateHandler;
            this.updateParam = param;

            this.generatorAdd = (int) (intFreqU(this.chipClock) * FIXEDPT / this.intSampleRate);

            this.recipSamp = 1.0 / (double) this.intSampleRate;
            for (int i = 15; i >= 0; i--) {
                this.frqMul[i] = frqmul_tab[i] * intFreqU(this.chipClock) / (double) WAVEPREC * (double) FIXEDPT * this.recipSamp;
            }

            // vibrato at ~6.1 ?? (opl3 docs say 6.1, opl4 docs say 6.0, Y8950 docs say 6.4)
            this.vibtabAdd = (int) (VIBTAB_SIZE * FIXEDPT_LFO / 8192 * intFreqU(this.chipClock) / this.intSampleRate);
            this.vibtabPos = 0;

            // tremolo at 3.7hz
            this.tremtabAdd = (int) ((double) TREMTAB_SIZE * TREM_FREQ * FIXEDPT_LFO / (double) this.intSampleRate);
            this.tremtabPos = 0;
        }

        private void stop() {
        }

        private void reset() {
            int i;
            Op op;

            //memset(this.adlibreg, 0x00, sizeof(this.adlibreg));
            //memset(this.Op, 0x00, sizeof(Op) * MAXOPERATORS);
            //memset(this.wave_sel, 0x00, sizeof(this.wave_sel));
            Arrays.fill(this.adlibReg, (byte) 0);
            for (int ind = 0; ind < this.ops.length; ind++) this.ops[ind] = new Op();
            Arrays.fill(this.waveSel, (byte) 0);

            for (i = 0; i < MAXOPERATORS; i++) {
                op = this.ops[i];
                op.reset();
            }

            this.status = 0;
            this.oplIndex = 0;
            this.oplAddr = 0;
        }

        private void writeIO(int addr, byte val) {
            if ((addr & 1) != 0)
                write(this.oplAddr, val);
            else
                this.oplAddr = val;
        }

        private void write(int idx, byte val) {
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
                        // clear IRQ bits in status register
                        this.status &= 0x9f; // ~0x60;
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
                int num = idx & 7;
                int base = (idx - ARC_TVS_KSR_MUL) & 0xff;
                if ((num < 6) && (base < 22)) {
                    int modOp = regBase2modOp[secondSet != 0 ? (base + 22) : base];
                    int regBase = base + secondSet;
                    int chanBase = secondSet != 0 ? (modOp - 18 + ARC_SECONDSET) : modOp;

                    // change tremolo/vibrato and sustain keeping of this Operator
                    Op po = this.ops[modOp + ((num < 3) ? 0 : 9)];
                    po.changeKeepSustain((this.adlibReg[ARC_TVS_KSR_MUL + regBase] & 0x20) > 0);
                    po.changeVibrato(regBase, this.adlibReg);

                    // change frequency calculations of this Operator as
                    // key scale rate and frequency multiplicator can be changed
                    po.changeFrequency(chanBase, base, this.adlibReg, this.frqMul, this.recipSamp);
                }
            }
            break;
            case ARC_KSL_OUTLEV:
            case ARC_KSL_OUTLEV + 0x10: {
                // key scale level; output rate
                int num = idx & 7;
                int base = (idx - ARC_KSL_OUTLEV) & 0xff;
                if ((num < 6) && (base < 22)) {
                    int modOp = regBase2modOp[secondSet != 0 ? (base + 22) : base];
                    int chanBase = secondSet != 0 ? (modOp - 18 + ARC_SECONDSET) : modOp;

                    // change frequency calculations of this Operator as
                    // key scale level and output rate can be changed
                    Op op = this.ops[modOp + ((num < 3) ? 0 : 9)];
                    op.changeFrequency(chanBase, base, this.adlibReg, this.frqMul, this.recipSamp);
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
                    Op po = this.ops[regbase2op[secondSet != 0 ? (base + 22) : base]];
                    po.changeAttackRate(this.adlibReg[ARC_ATTR_DECR + regBase] >> 4, this.recipSamp);
                    po.changeDecayRate(this.adlibReg[ARC_ATTR_DECR + regBase] & 15, this.recipSamp);
                }
            }
            break;
            case ARC_SUSL_RELR:
            case ARC_SUSL_RELR + 0x10: {
                // sustain level; release rate
                int num = idx & 7;
                int base = (idx - ARC_SUSL_RELR) & 0xff;
                if ((num < 6) && (base < 22)) {
                    int regbase = base + secondSet;

                    // change sustain level and release rate of this Operator
                    Op po = this.ops[regbase2op[secondSet != 0 ? (base + 22) : base]];
                    po.changeReleaseRate(this.adlibReg[ARC_SUSL_RELR + regbase] & 15, this.recipSamp);
                    po.changeSustainLevel(this.adlibReg[ARC_SUSL_RELR + regbase] >> 4);
                }
            }
            break;
            case ARC_FREQ_NUM: {
                // 0xa0-0xa8 low8 frequency
                int base = (idx - ARC_FREQ_NUM) & 0xff;
                if (base < 9) {
                    int opBase = secondSet != 0 ? (base + 18) : base;
                    // regbase of modulator:
                    int modBase = modulatorbase[base] + secondSet;

                    int chanBase = base + secondSet;

                    this.ops[opBase].changeFrequency(chanBase, modBase, this.adlibReg, this.frqMul, this.recipSamp);
                    this.ops[opBase + 9].changeFrequency(chanBase, modBase + 3, this.adlibReg, this.frqMul, this.recipSamp);
                }
            }
            break;
            case ARC_KON_BNUM: {
                if (this.updateHandler != null) // hack for DOSBox logs
                    this.updateHandler.accept(this.updateParam);
                if (idx == ARC_PERC_MODE) {

                    if ((val & 0x30) == 0x30) { // BassDrum active
                        this.ops[6].enableOperator(16, OP_ACT_PERC, this.waveSel);
                        this.ops[6].changeFrequency(6, 16, this.adlibReg, this.frqMul, this.recipSamp);
                        this.ops[6 + 9].enableOperator(16 + 3, OP_ACT_PERC, this.waveSel);
                        this.ops[6 + 9].changeFrequency(6, 16 + 3, this.adlibReg, this.frqMul, this.recipSamp);
                    } else {
                        this.ops[6].disableOperator(OP_ACT_PERC);
                        this.ops[6 + 9].disableOperator(OP_ACT_PERC);
                    }
                    if ((val & 0x28) == 0x28) { // Snare active
                        this.ops[16].enableOperator(17 + 3, OP_ACT_PERC, this.waveSel);
                        this.ops[16].changeFrequency(7, 17 + 3, this.adlibReg, this.frqMul, this.recipSamp);
                    } else {
                        this.ops[16].disableOperator(OP_ACT_PERC);
                    }
                    if ((val & 0x24) == 0x24) { // TomTom active
                        this.ops[8].enableOperator(18, OP_ACT_PERC, this.waveSel);
                        this.ops[8].changeFrequency(8, 18, this.adlibReg, this.frqMul, this.recipSamp);
                    } else {
                        this.ops[8].disableOperator(OP_ACT_PERC);
                    }
                    if ((val & 0x22) == 0x22) { // Cymbal active
                        this.ops[8 + 9].enableOperator(18 + 3, OP_ACT_PERC, this.waveSel);
                        this.ops[8 + 9].changeFrequency(8, 18 + 3, this.adlibReg, this.frqMul, this.recipSamp);
                    } else {
                        this.ops[8 + 9].disableOperator(OP_ACT_PERC);
                    }
                    if ((val & 0x21) == 0x21) { // Hihat active
                        this.ops[7].enableOperator(17, OP_ACT_PERC, this.waveSel);
                        this.ops[7].changeFrequency(7, 17, this.adlibReg, this.frqMul, this.recipSamp);
                    } else {
                        this.ops[7].disableOperator(OP_ACT_PERC);
                    }

                    break;
                }
                // regular 0xb0-0xb8
                int base = (idx - ARC_KON_BNUM) & 0xff;
                if (base < 9) {
                    int opBase = secondSet != 0 ? (base + 18) : base;
                    // regbase of modulator:
                    int modBase = modulatorbase[base] + secondSet;

                    if ((val & 32) != 0) {
                        // Operator switched on
                        this.ops[opBase].enableOperator(modBase, OP_ACT_NORMAL, this.waveSel); // modulator (if 2op)
                        this.ops[opBase + 9].enableOperator(modBase + 3, OP_ACT_NORMAL, this.waveSel); // carrier (if 2op)
                    } else {
                        // Operator switched off
                        this.ops[opBase].disableOperator(OP_ACT_NORMAL);
                        this.ops[opBase + 9].disableOperator(OP_ACT_NORMAL);
                    }

                    int chanBase = base + secondSet;

                    // change frequency calculations of modulator and carrier (2op) as
                    // the frequency of the channel has changed
                    this.ops[opBase].changeFrequency(chanBase, modBase, this.adlibReg, this.frqMul, this.recipSamp);
                    this.ops[opBase + 9].changeFrequency(chanBase, modBase + 3, this.adlibReg, this.frqMul, this.recipSamp);
                }
            }
            break;
            case ARC_FEEDBACK: {
                // 0xc0-0xc8 feedback/modulation type (AM/FM)
                int base = (idx - ARC_FEEDBACK) & 0xff;
                if (base < 9) {
                    int opBase = secondSet != 0 ? (base + 18) : base;
                    int chanBase = base + secondSet;
                    this.ops[opBase].changeFeedback(this.adlibReg[ARC_FEEDBACK + chanBase] & 14);
                }
            }
            break;
            case ARC_WAVE_SEL:
            case ARC_WAVE_SEL + 0x10: {
                int num = idx & 7;
                int base = (idx - ARC_WAVE_SEL) & 0xff;
                if ((num < 6) && (base < 22)) {
                    if ((this.adlibReg[0x01] & 0x20) != 0) {
                        // wave selection enabled, change waveform
                        this.waveSel[base] = (byte) (val & 3);
                        Op po = this.ops[regBase2modOp[base] + ((num < 3) ? 0 : 9)];
                        po.changeWaveForm(base, this.waveSel);
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
        }

        private void writeIndex(int port, byte val) {
            oplIndex = val;
        }

        // be careful with this
        // uses cptr and chanVal, outputs into outBufL(/outBufR)
        // for opl3 check if opl3-mode is enabled (which uses stereo panning)
        //
        // Changes by Valley Bell:
        // - Changed to always output to both channels
        // - added parameter "chn" to fix panning for 4-Op channels and the Rhythm Cymbal
        private void outChannelValue(int chn, int[] outBufL, int[] outBufR, int i, int chanVal) {
            outBufL[i] += chanVal;
            outBufR[i] += chanVal;
        }

        private int[] vibLut = new int[BLOCKBUF_SIZE];
        private int[] tremLut = new int[BLOCKBUF_SIZE];

        private void getSample(int[][] sndPtr, int numSamples) {

            int endSamples;

            int[] outBufL = sndPtr[0];
            int[] outBufR = sndPtr[1];

            // vibrato/tremolo lookup tables (Global, to possibly be used by all operators)
            //int[] vib_lut = new int[BLOCKBUF_SIZE];
            //int[] trem_lut = new int[BLOCKBUF_SIZE];

            int samplesToProcess = numSamples;

            int curSmp;
            int vibTShift;
            int numChannels = NUM_CHANNELS;

            int[] vibVal1, vibVal2, vibVal3, vibVal4;
            int[] tremVal1, tremVal2, tremVal3, tremVal4;

            if (samplesToProcess == 0) {
                for (int curCh = 0; curCh < numChannels; curCh++) {
                    if ((this.adlibReg[ARC_PERC_MODE] & 0x20) != 0 && (curCh >= 6 && curCh < 9))
                        continue;

                    Op[] cPtr = this.ops;
                    int cPtrPtr = curCh;

                    if (cPtr[cPtrPtr + 0].opState == OF_TYPE_ATT)
                        cPtr[cPtrPtr + 0].checkEgAttack();
                    if (cPtr[cPtrPtr + 9].opState == OF_TYPE_ATT)
                        cPtr[cPtrPtr + 9].checkEgAttack();
                }

                return;
            }

            for (curSmp = 0; curSmp < samplesToProcess; curSmp += endSamples) {
                endSamples = samplesToProcess - curSmp;
                //if (endSamples>BLOCKBUF_SIZE) endSamples = BLOCKBUF_SIZE;

                //memset(outBufL, 0, endSamples * sizeof(int));
                for (int ind = 0; ind < endSamples; ind++) {
                    outBufL[ind] = 0;
                    outBufR[ind] = 0;
                }

                // calculate vibrato/tremolo lookup tables
                vibTShift = ((this.adlibReg[ARC_PERC_MODE] & 0x40) == 0) ? 1 : 0; // 14cents/7cents switching
                for (int i = 0; i < endSamples; i++) {
                    // cycle through vibrato table
                    this.vibtabPos += this.vibtabAdd;
                    if (this.vibtabPos / FIXEDPT_LFO >= VIBTAB_SIZE)
                        this.vibtabPos -= VIBTAB_SIZE * FIXEDPT_LFO;
                    vibLut[i] = Op.vibTable[this.vibtabPos / FIXEDPT_LFO] >> vibTShift; // 14cents (14/100 of a semitone) or 7cents

                    // cycle through tremolo table
                    this.tremtabPos += this.tremtabAdd;
                    if (this.tremtabPos / FIXEDPT_LFO >= TREMTAB_SIZE)
                        this.tremtabPos -= TREMTAB_SIZE * FIXEDPT_LFO;
                    if ((this.adlibReg[ARC_PERC_MODE] & 0x80) != 0)
                        tremLut[i] = Op.tremTable[this.tremtabPos / FIXEDPT_LFO];
                    else
                        tremLut[i] = Op.tremTable[TREMTAB_SIZE + this.tremtabPos / FIXEDPT_LFO];
                }

                if ((this.adlibReg[ARC_PERC_MODE] & 0x20) != 0) {
                    if ((this.muteChn[NUM_CHANNELS + 0]) == 0) {
                        // BassDrum
                        Op[] pos = this.ops;
                        int posP = 6;
                        if ((this.adlibReg[ARC_FEEDBACK + 6] & 1) != 0) {
                            // additive synthesis
                            if (pos[posP + 9].opState != OF_TYPE_OFF) {
                                if (pos[posP + 9].vibrato) {
                                    vibVal1 = vibval_var1;
                                    for (int i = 0; i < endSamples; i++)
                                        vibVal1[i] = (int) ((vibLut[i] * pos[posP + 9].freq_high / 8) * FIXEDPT * VIBFAC);
                                } else
                                    vibVal1 = Op.vibValConst;
                                if (pos[posP + 9].tremolo)
                                    tremVal1 = tremLut; // tremolo enabled, use table
                                else
                                    tremVal1 = Op.tremValConst;

                                // calculate channel output
                                for (int i = 0; i < endSamples; i++) {
                                    int chanval;

                                    pos[posP + 9].advance(vibVal1[i], this.generatorAdd);
                                    opFuncs[pos[posP + 9].opState].accept(pos[posP + 9]);
                                    pos[posP + 9].output(0, tremVal1[i]);

                                    chanval = pos[posP + 9].cval * 2;
                                    outChannelValue(0, outBufL, outBufR, i, chanval);
                                }
                            }
                        } else {
                            // frequency modulation
                            if ((pos[posP + 9].opState != OF_TYPE_OFF) || (pos[posP + 0].opState != OF_TYPE_OFF)) {
                                if ((pos[posP + 0].vibrato) && (pos[posP + 0].opState != OF_TYPE_OFF)) {
                                    vibVal1 = vibval_var1;
                                    for (int i = 0; i < endSamples; i++)
                                        vibVal1[i] = (int) ((vibLut[i] * pos[posP + 0].freq_high / 8) * FIXEDPT * VIBFAC);
                                } else
                                    vibVal1 = Op.vibValConst;
                                if ((pos[posP + 9].vibrato) && (pos[posP + 9].opState != OF_TYPE_OFF)) {
                                    vibVal2 = vibval_var2;
                                    for (int i = 0; i < endSamples; i++)
                                        vibVal2[i] = (int) ((vibLut[i] * pos[posP + 9].freq_high / 8) * FIXEDPT * VIBFAC);
                                } else
                                    vibVal2 = Op.vibValConst;
                                if (pos[posP + 0].tremolo)
                                    tremVal1 = tremLut; // tremolo enabled, use table
                                else
                                    tremVal1 = Op.tremValConst;
                                if (pos[posP + 9].tremolo)
                                    tremVal2 = tremLut; // tremolo enabled, use table
                                else
                                    tremVal2 = Op.tremValConst;

                                // calculate channel output
                                for (int i = 0; i < endSamples; i++) {
                                    int chanval;

                                    pos[posP + 0].advance(vibVal1[i], this.generatorAdd);
                                    opFuncs[pos[posP + 0].opState].accept(pos[posP + 0]);
                                    pos[posP + 0].output((pos[posP + 0].lastcval + pos[posP + 0].cval) * pos[posP + 0].mfbi / 2, tremVal1[i]);

                                    pos[posP + 9].advance(vibVal2[i], this.generatorAdd);
                                    opFuncs[pos[posP + 9].opState].accept(pos[posP + 9]);
                                    pos[posP + 9].output(pos[posP + 0].cval * FIXEDPT, tremVal2[i]);

                                    chanval = pos[posP + 9].cval * 2;
                                    outChannelValue(0, outBufL, outBufR, i, chanval);
                                }
                            }
                        }
                    }   // end if (! Muted)

                    //TomTom (j=8)
                    if ((this.muteChn[NUM_CHANNELS + 2]) == 0 && this.ops[8].opState != OF_TYPE_OFF) {
                        Op[] cPtr = this.ops;
                        int cPtrPtr = 8;
                        if (cPtr[cPtrPtr + 0].vibrato) {
                            vibVal3 = vibval_var1;
                            for (int i = 0; i < endSamples; i++)
                                vibVal3[i] = (int) ((vibLut[i] * cPtr[cPtrPtr + 0].freq_high / 8) * FIXEDPT * VIBFAC);
                        } else
                            vibVal3 = Op.vibValConst;

                        if (cPtr[cPtrPtr + 0].tremolo)
                            tremVal3 = tremLut; // tremolo enabled, use table
                        else
                            tremVal3 = Op.tremValConst;

                        // calculate channel output
                        for (int i = 0; i < endSamples; i++) {
                            int chanval;

                            cPtr[cPtrPtr + 0].advance(vibVal3[i], this.generatorAdd);
                            opFuncs[cPtr[cPtrPtr + 0].opState].accept(cPtr[cPtrPtr + 0]); //TomTom
                            cPtr[cPtrPtr + 0].output(0, tremVal3[i]);
                            chanval = cPtr[cPtrPtr + 0].cval * 2;
                            outChannelValue(0, outBufL, outBufR, i, chanval);
                        }
                    }

                    //Snare/Hihat (j=7), Cymbal (j=8)
                    if ((this.ops[7].opState != OF_TYPE_OFF) || (this.ops[16].opState != OF_TYPE_OFF) ||
                            (this.ops[17].opState != OF_TYPE_OFF)) {
                        Op[] pos = this.ops;
                        int posP = 7;
                        if ((pos[posP + 0].vibrato) && (pos[posP + 0].opState != OF_TYPE_OFF)) {
                            vibVal1 = vibval_var1;
                            for (int i = 0; i < endSamples; i++)
                                vibVal1[i] = (int) ((vibLut[i] * pos[posP + 0].freq_high / 8) * FIXEDPT * VIBFAC);
                        } else
                            vibVal1 = Op.vibValConst;
                        if ((pos[posP + 9].vibrato) && (pos[posP + 9].opState == OF_TYPE_OFF)) {
                            vibVal2 = vibval_var2;
                            for (int i = 0; i < endSamples; i++)
                                vibVal2[i] = (int) ((vibLut[i] * pos[posP + 9].freq_high / 8) * FIXEDPT * VIBFAC);
                        } else
                            vibVal2 = Op.vibValConst;

                        if (pos[posP + 0].tremolo)
                            tremVal1 = tremLut; // tremolo enabled, use table
                        else
                            tremVal1 = Op.tremValConst;
                        if (pos[posP + 9].tremolo)
                            tremVal2 = tremLut; // tremolo enabled, use table
                        else
                            tremVal2 = Op.tremValConst;

                        pos = this.ops;
                        posP = 8;
                        if ((pos[posP + 9].vibrato) && (pos[posP + 9].opState == OF_TYPE_OFF)) {
                            vibVal4 = vibval_var2;
                            for (int i = 0; i < endSamples; i++)
                                vibVal4[i] = (int) ((vibLut[i] * pos[posP + 9].freq_high / 8) * FIXEDPT * VIBFAC);
                        } else
                            vibVal4 = Op.vibValConst;

                        if (pos[posP + 9].tremolo) tremVal4 = tremLut; // tremolo enabled, use table
                        else tremVal4 = Op.tremValConst;

                        // calculate channel output
                        pos = this.ops; // set pos to something useful (else it stays at Op[8])
                        posP = 0;
                        for (int i = 0; i < endSamples; i++) {
                            int chanval;

                            Op.advanceDrums(this.ops[7], vibVal1[i], this.ops[7 + 9], vibVal2[i], this.ops[8 + 9], vibVal4[i], this.generatorAdd);

                            if ((this.muteChn[NUM_CHANNELS + 4]) == 0) {
                                opFuncs[this.ops[7].opState].accept(this.ops[7]); // Hihat
                                this.ops[7].output(0, tremVal1[i]);
                            } else
                                this.ops[7].cval = 0;

                            if ((this.muteChn[NUM_CHANNELS + 1]) == 0) {
                                opFuncs[this.ops[7 + 9].opState].accept(this.ops[7 + 9]); // Snare
                                this.ops[7 + 9].output(0, tremVal2[i]);
                            } else
                                this.ops[7 + 9].cval = 0;

                            if ((this.muteChn[NUM_CHANNELS + 3]) == 0) {
                                opFuncs[this.ops[8 + 9].opState].accept(this.ops[8 + 9]); // Cymbal
                                this.ops[8 + 9].output(0, tremVal4[i]);
                            } else
                                this.ops[8 + 9].cval = 0;

                            // fix panning of the snare -Valley Bell
                            chanval = (this.ops[7].cval + this.ops[7 + 9].cval) * 2;
                            outChannelValue(7, outBufL, outBufR, i, chanval);

                            chanval = this.ops[8 + 9].cval * 2;
                            outChannelValue(8, outBufL, outBufR, i, chanval);

                        }
                    }
                }

                for (int curCh = numChannels - 1; curCh >= 0; curCh--) {
                    int k;

                    if (this.muteChn[curCh] != 0)
                        continue;

                    // skip drum/percussion operators
                    if ((this.adlibReg[ARC_PERC_MODE] & 0x20) != 0 && (curCh >= 6) && (curCh < 9)) continue;

                    k = curCh;
                    Op[] ops = this.ops;
                    int posP = curCh;

                    // check for FM/AM
                    if ((this.adlibReg[ARC_FEEDBACK + k] & 1) != 0) {
                        // 2op additive synthesis
                        if ((ops[posP + 9].opState == OF_TYPE_OFF) && (ops[posP + 0].opState == OF_TYPE_OFF))
                            continue;
                        if ((ops[posP + 0].vibrato) && (ops[posP + 0].opState != OF_TYPE_OFF)) {
                            vibVal1 = vibval_var1;
                            for (int i = 0; i < endSamples; i++)
                                vibVal1[i] = (int) ((vibLut[i] * ops[posP + 0].freq_high / 8) * FIXEDPT * VIBFAC);
                        } else
                            vibVal1 = Op.vibValConst;
                        if ((ops[posP + 9].vibrato) && (ops[posP + 9].opState != OF_TYPE_OFF)) {
                            vibVal2 = vibval_var2;
                            for (int i = 0; i < endSamples; i++)
                                vibVal2[i] = (int) ((vibLut[i] * ops[posP + 9].freq_high / 8) * FIXEDPT * VIBFAC);
                        } else
                            vibVal2 = Op.vibValConst;
                        if (ops[posP + 0].tremolo)
                            tremVal1 = tremLut; // tremolo enabled, use table
                        else
                            tremVal1 = Op.tremValConst;
                        if (ops[posP + 9].tremolo)
                            tremVal2 = tremLut; // tremolo enabled, use table
                        else
                            tremVal2 = Op.tremValConst;

                        // calculate channel output
                        for (int i = 0; i < endSamples; i++) {
                            int chanval;

                            // carrier1
                            ops[posP + 0].advance(vibVal1[i], this.generatorAdd);
                            opFuncs[ops[posP + 0].opState].accept(ops[posP + 0]);
                            ops[posP + 0].output((ops[posP + 0].lastcval + ops[posP + 0].cval) * ops[posP + 0].mfbi / 2, tremVal1[i]);

                            // carrier2
                            ops[posP + 9].advance(vibVal2[i], this.generatorAdd);
                            opFuncs[ops[posP + 9].opState].accept(ops[posP + 9]);
                            ops[posP + 9].output(0, tremVal2[i]);

                            chanval = ops[posP + 9].cval + ops[posP + 0].cval;
                            outChannelValue(0, outBufL, outBufR, i, chanval);
                        }
                    } else {
                        // 2op frequency modulation
                        if ((ops[posP + 9].opState == OF_TYPE_OFF) && (ops[posP + 0].opState == OF_TYPE_OFF))
                            continue;
                        if ((ops[posP + 0].vibrato) && (ops[posP + 0].opState != OF_TYPE_OFF)) {
                            vibVal1 = vibval_var1;
                            for (int i = 0; i < endSamples; i++)
                                vibVal1[i] = (int) ((vibLut[i] * ops[posP + 0].freq_high / 8) * FIXEDPT * VIBFAC);
                        } else
                            vibVal1 = Op.vibValConst;
                        if ((ops[posP + 9].vibrato) && (ops[posP + 9].opState != OF_TYPE_OFF)) {
                            vibVal2 = vibval_var2;
                            for (int i = 0; i < endSamples; i++)
                                vibVal2[i] = (int) ((vibLut[i] * ops[posP + 9].freq_high / 8) * FIXEDPT * VIBFAC);
                        } else
                            vibVal2 = Op.vibValConst;
                        if (ops[posP + 0].tremolo)
                            tremVal1 = tremLut; // tremolo enabled, use table
                        else
                            tremVal1 = Op.tremValConst;
                        if (ops[posP + 9].tremolo)
                            tremVal2 = tremLut; // tremolo enabled, use table
                        else
                            tremVal2 = Op.tremValConst;

                        // calculate channel output
                        for (int i = 0; i < endSamples; i++) {
                            int chanval;

                            // modulator
                            ops[posP + 0].advance(vibVal1[i], this.generatorAdd);
                            opFuncs[ops[posP + 0].opState].accept(ops[posP + 0]);
                            ops[posP + 0].output((ops[posP + 0].lastcval + ops[posP + 0].cval) * ops[posP + 0].mfbi / 2, tremVal1[i]);

                            // carrier
                            ops[posP + 9].advance(vibVal2[i], this.generatorAdd);
                            opFuncs[ops[posP + 9].opState].accept(ops[posP + 9]);
                            ops[posP + 9].output(ops[posP + 0].cval * FIXEDPT, tremVal2[i]);

                            chanval = ops[posP + 9].cval;
                            outChannelValue(0, outBufL, outBufR, i, chanval);
                        }
                    }
                }

                // convert to 16bit samples
//                for (i=0;i<endSamples;i++)
//                    clipit16(outBufL[i],sndPtr++);
            }
        }

        private void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < NUM_CHANNELS + 5; curChn++)
                this.muteChn[curChn] = (byte) ((muteMask >> curChn) & 0x01);
        }
    }
}

