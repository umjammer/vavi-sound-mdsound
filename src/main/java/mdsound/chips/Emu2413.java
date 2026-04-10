/*
 * Copyright (C) 2020 Mitsutaka Okazaki
 */

package mdsound.chips;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import mdsound.chips.Emu2413.Slot.Update;

import static java.lang.System.getLogger;


/**
 * emu2413
 * <p>
 * This source refers to the following documents. The author would like to thank all the authors who have
 * contributed to the writing of them.
 * - [YM2413 notes](http://www.smspower.org/Development/YM2413) by andete
 * - ymf262.c by Jarek Burczynski
 * - [VRC7 presets](https://siliconpr0n.org/archive/doku.php?id=vendor:yamaha:opl2#opll_vrc7_patch_format) by Nuke.YKT
 * - YMF281B presets by Chabin
 *
 * @author Mitsutaka Okazaki
 * @version  v1.5.9
 * @see "https://github.com/digital-sound-antiques/emu2413"
 */
public class Emu2413 {

    private static final Logger logger = getLogger(Emu2413.class.getName());

    public enum Tone {
        _2413,
        VRC7,
        _281B
    }

    /* voice data */
    public static class Patch {

        public int tl, fb, eg, ml, ar, dr, sl, rr, kr, kl, am, pm, ws;
    }

    /** slot */
    static class Slot {

        private enum EgState {ATTACK, DECAY, SUSTAIN, RELEASE, DAMP, UNKNOWN}

        enum Update {
            WS(1),
            TLL(2),
            RKS(4),
            EG(8),
            ALL(255);
            final int v;

            Update(int v) {
                this.v = v;
            }
        }

        static final int BD1 = 12;
        static final int BD2 = 13;
        static final int HH = 14;
        static final int SD = 15;
        static final int TOM = 16;
        static final int CYM = 17;

        private int number;

        /**
         * type flags:
         * 000000SM
         *       |+-- M: 0:modulator 1:carrier
         *       +--- S: 0:normal 1:single slot mode (sd, tom, hh or cym)
         */
        private int type;

        /** voice parameter */
        private Patch patch;

        // slot output

        /** output value, latest and previous. */
        private final int[] output = new int[2];

        // phase generator (pg)

        /** wave table */
        private int[] waveTable;
        /** pg phase */
        private int pgPhase;
        /** pg output, as index of wave table */
        private int pgOut;
        /** if 1, pgPhase is preserved when key-on */
        private int pg_keep;
        /** (block << 9) | f-number */
        private int blkFNum;
        /** f-number (9 bits) */
        private int fNum;
        /** block (3 bits) */
        private int blk;

        // envelope generator (eg)

        /** current state */
        private EgState egState;
        /** current volume */
        private int volume;
        /** key-on flag 1:on 0:off */
        private int keyFlag;
        /** key-sus option 1:on 0:off */
        private int susFlag;
        /** total level + key scale level*/
        private int tll;
        /** key scale offset (rks) for eg speed */
        private int rks;
        /** eg speed rate high 4bits */
        private int egRateH;
        /** eg speed rate low 2bits */
        private int egRateL;
        /** shift for eg global counter, controls envelope speed */
        private int egShift;
        /** eg output */
        private int egOut;

        private void reset(int number) {
            this.number = number;
            type = number % 2;
            pg_keep = 0;
            waveTable = waveTableMap[0];
            pgPhase = 0;
            output[0] = 0;
            output[1] = 0;
            egState = EgState.RELEASE;
            egShift = 0;
            rks = 0;
            tll = 0;
            keyFlag = 0;
            susFlag = 0;
            blkFNum = 0;
            blk = 0;
            fNum = 0;
            volume = 0;
            pgOut = 0;
            egOut = EG_MUTE;
            this.patch = NullPatch;
        }

        private void slotOn() {
            this.keyFlag = 1;
            this.egState = EgState.DAMP;
            this.requestUpdate(Update.EG.v);
        }

        private void slotOff() {
            this.keyFlag = 0;
            if ((this.type & 1) != 0) {
                this.egState = EgState.RELEASE;
                this.requestUpdate(Update.EG.v);
            }
        }

        private void setSlotVolume(int volume) {
            this.volume = volume;
            requestUpdate(Update.TLL.v);
        }

        private void calcEnvelope(Slot buddy, int egCounter, int test) {

            int mask = (1 << egShift) - 1;

            if (egState == EgState.ATTACK) {
                if (0 < egOut && 0 < egRateH && (egCounter & mask & ~3) == 0) {
                    int s = lookupAttackStep(egCounter);
                    if (0 < s) {
                        egOut = Math.max(0, egOut - (egOut >> s) - 1);
                    }
                }
            } else {
                if (egRateH > 0 && (egCounter & mask) == 0) {
                    egOut = Math.min(EG_MUTE, egOut + lookupDecayStep(egCounter));
                }
            }

            switch (egState) {
                case DAMP:
                    // DAMP to ATTACK transition is occured when the envelope reaches EG_MAX (max attenuation but it's not mute).
                    // Do not forget to check (egCounter & mask) == 0 to synchronize it with the progress of the envelope.
                    if (egOut >= EG_MAX && (egCounter & mask) == 0) {
                        startEnvelope();
                        if ((type & 1) != 0) {
                            if (pg_keep == 0) {
                                pgPhase = 0;
                            }
                            if (buddy != null && buddy.pg_keep == 0) {
                                buddy.pgPhase = 0;
                            }
                        }
                    }
                    break;

                case ATTACK:
                    if (egOut == 0) {
                        egState = EgState.DECAY;
                        requestUpdate(Update.EG.v);
                    }
                    break;

                case DECAY:
                    // DECAY to SUSTAIN transition must be checked at every cycle regardless of the conditions of the envelope rate and
                    // counter. i.e. the transition is not synchronized with the progress of the envelope.
                    if ((egOut >> 3) == this.patch.sl) {
                        egState = EgState.SUSTAIN;
                        requestUpdate(Update.EG.v);
                    }
                    break;

                case SUSTAIN:
                case RELEASE:
                default:
                    break;
            }

            if (test != 0) {
                egOut = 0;
            }
        }

        private int updateRequests; /* flags to debounce update */

        public void requestUpdate(int flag) {
            updateRequests |= flag;
        }

        public void commitSlotUpdate() {
//#if OPLL_DEBUG
            if (lastEgState != egState) {
                debugPrintSlotInfo();
                lastEgState = egState;
            }
//#endif

            if ((updateRequests & Update.WS.v) != 0) {
                waveTable = waveTableMap[Slot.this.patch.ws];
            }

            if ((updateRequests & Update.TLL.v) != 0) {
                if ((type & 1) == 0) {
                    tll = tllTable[blkFNum >> 5][Slot.this.patch.tl][Slot.this.patch.kl];
                } else {
                    tll = tllTable[blkFNum >> 5][volume][Slot.this.patch.kl];
                }
            }

            if ((updateRequests & Update.RKS.v) != 0) {
                rks = rksTable[blkFNum >> 8][Slot.this.patch.kr];
            }

            if ((updateRequests & (Update.RKS.v | Update.EG.v)) != 0) {
                int pRate = this.getParameterRate();

                if (pRate == 0) {
                    egShift = 0;
                    egRateH = 0;
                    egRateL = 0;
                    return;
                }

                egRateH = Math.min(15, pRate + (rks >> 2));
                egRateL = rks & 3;
                if (egState == EgState.ATTACK) {
                    egShift = (0 < egRateH && egRateH < 12) ? (13 - egRateH) : 0;
                } else {
                    egShift = (egRateH < 13) ? (13 - egRateH) : 0;
                }
            }

            updateRequests = 0;
        }

        private int getParameterRate() {

            if ((type & 1) == 0 && keyFlag == 0) {
                return 0;
            }

            switch (egState) {
                case ATTACK:
                    return Slot.this.patch.ar;
                case DECAY:
                    return Slot.this.patch.dr;
                case SUSTAIN:
                    return Slot.this.patch.eg != 0 ? 0 : Slot.this.patch.rr;
                case RELEASE:
                    if (susFlag != 0) {
                        return 5;
                    } else if (Slot.this.patch.eg != 0) {
                        return Slot.this.patch.rr;
                    } else {
                        return 7;
                    }
                case DAMP:
                    return DAMPER_RATE;
                default:
                    return 0;
            }
        }

        public void calcPhase(int pmPhase, int reset) {
            int pm = Slot.this.patch.pm != 0 ? pmTable[(fNum >> 6) & 7][(pmPhase >> 10) & 7] : 0;
            if (reset != 0) {
                pgPhase = 0;
            }
            pgPhase += (((fNum & 0x1ff) * 2 + pm) * mlTable[Slot.this.patch.ml]) << blk >> 2;
            pgPhase &= (DP_WIDTH - 1);
            pgOut = pgPhase >> DP_BASE_BITS;
        }

        public int lookupAttackStep(int counter) {
            int index;

            return switch (egRateH) {
                case 12 -> {
                    index = (counter & 0xc) >> 1;
                    yield 4 - egStepTables[egRateL][index];
                }
                case 13 -> {
                    index = (counter & 0xc) >> 1;
                    yield 3 - egStepTables[egRateL][index];
                }
                case 14 -> {
                    index = (counter & 0xc) >> 1;
                    yield 2 - egStepTables[egRateL][index];
                }
                case 0, 15 -> 0;
                default -> {
                    index = counter >> egShift;
                    yield egStepTables[egRateL][index & 7] != 0 ? 4 : 0;
                }
            };
        }

        public int lookupDecayStep(int counter) {
            int index;

            return switch (egRateH) {
                case 0 -> 0;
                case 13 -> {
                    index = ((counter & 0xc) >> 1) | (counter & 1);
                    yield egStepTables[egRateL][index];
                }
                case 14 -> {
                    index = ((counter & 0xc) >> 1);
                    yield egStepTables[egRateL][index] + 1;
                }
                case 15 -> 2;
                default -> {
                    index = counter >> egShift;
                    yield egStepTables[egRateL][index & 7];
                }
            };
        }

        public void startEnvelope() {
            if (Math.min(15, Slot.this.patch.ar + (rks >> 2)) == 15) {
                egState = EgState.DECAY;
                egOut = 0;
            } else {
                egState = EgState.ATTACK;
            }
            requestUpdate(Update.EG.v);
        }

        public int toLinear(int h, int am) {
            if (egOut > EG_MAX)
                return 0;

            int att = Math.min(EG_MUTE, (egOut + tll + am)) << 4;
            return lookupExpTable(h + att);
        }

        /** output: -4095...4095 */
        private static int lookupExpTable(int i) {
            // from andete's expression
            int t = expTable[(i & 0xff) ^ 0xff] + 1024;
            int res = t >> ((i & 0x7f00) >> 8);
            return ((i & 0x8000) != 0 ? ~res : res) << 1;
        }

        public int calcSlotTom() {
            return this.toLinear(this.waveTable[this.pgOut], 0);
        }

        /** Specify phase offset directly based on 10-bit (1024-length) sine table */
        private static int pd(int phase) {
            return ((PG_BITS < 10) ? (phase >> (10 - PG_BITS)) : (phase << (PG_BITS - 10)));
        }

        public int calcSlotSnare(int noise) {
            int phase;

            if (bit(this.pgOut, PG_BITS - 2) != 0)
                phase = (noise & 1) != 0 ? pd(0x300) : pd(0x200);
            else
                phase = (noise & 1) != 0 ? pd(0x0) : pd(0x100);

            return this.toLinear(this.waveTable[phase], 0);
        }

        public int calcSlotCym(int short_noise) {
            int phase = short_noise != 0 ? pd(0x300) : pd(0x100);

            return this.toLinear(this.waveTable[phase], 0);
        }

        public int calcSlotHat(int noise, int short_noise) {
            int phase;

            if (short_noise != 0)
                phase = (noise & 1) != 0 ? pd(0x2d0) : pd(0x234);
            else
                phase = (noise & 1) != 0 ? pd(0x34) : pd(0xd0);

            return this.toLinear(this.waveTable[phase], 0);
        }

        private int calcSlotCar(int fm, int lfo_am) {
            int am = this.patch.am != 0 ? lfo_am : 0;

            this.output[1] = this.output[0];
            this.output[0] = this.toLinear(this.waveTable[(this.pgOut + 2 * (fm >> 1)) & (PG_WIDTH - 1)], am);

            return this.output[0];
        }

        private int calcSlotMod(int lfo_am) {
            int fm = this.patch.fb > 0 ? ((this.output[1] + this.output[0]) >> (9 - this.patch.fb)) : 0;
            int am = this.patch.am != 0 ? lfo_am : 0;

            this.output[1] = this.output[0];
            this.output[0] = this.toLinear(this.waveTable[(this.pgOut + fm) & (PG_WIDTH - 1)], am);

            return this.output[0];
        }

//#if OPLL_DEBUG

        private EgState lastEgState;

        private void debugPrintPatch() {
            Patch p = Slot.this.patch;
logger.log(Level.TRACE, "[slot#{0} am:{1} pm:{2} eg:{3} kr:{4} ml:{5} kl:{6} tl:{7} ws:{8} fb:{9} A:{10} D:{11} S:{12} R:{13}]",
 number, //
 p.am, p.pm, p.eg, p.kr, p.ml, //
 p.kl, p.tl, p.ws, p.fb, //
 p.ar, p.dr, p.eg, p.sl, p.rr);
        }

        private String debugEgStateName() {
            return switch (egState) {
                case ATTACK -> "attack";
                case DECAY -> "decay";
                case SUSTAIN -> "sustain";
                case RELEASE -> "release";
                case DAMP -> "damp";
                default -> "unknown";
            };
        }

        private void debugPrintSlotInfo() {
            String name = debugEgStateName();
logger.log(Level.TRACE, "[slot#{0} state:{1} fNum:{2:03x} rate:{3}-{4}]",
 number, name, blkFNum, egRateH,
        egRateL);
            debugPrintPatch();
        }

//#endif
    }

    // mask
    private static int OPLL_MASK_CH(int x) {
        return 1 << x;
    }

    private static final int OPLL_MASK_HH = (1 << (9));
    private static final int OPLL_MASK_CYM = (1 << (10));
    private static final int OPLL_MASK_TOM = (1 << (11));
    private static final int OPLL_MASK_SD = (1 << (12));
    private static final int OPLL_MASK_BD = (1 << (13));
    private static final int OPLL_MASK_RHYTHM = (OPLL_MASK_HH | OPLL_MASK_CYM | OPLL_MASK_TOM | OPLL_MASK_SD | OPLL_MASK_BD);

    /** rate converter */
    private static class RateConv {

        /*
         * LW is truncate length of sinc(x) calculation.
         * Lower LW is faster, higher LW results better quality.
         * LW must be a non-zero positive even number, no upper limit.
         * LW=16 or greater is recommended when upsampling.
         * LW=8 is practically okay for downsampling.
         */
        private static final int LW = 16;

        // resolution of sinc(x) table. sinc(x) where 0.0<=x<1.0 corresponds to sinc_table[0...SINC_RESO-1]
        private static final int SINC_RESO = 256;
        private static final int SINC_AMP_BITS = 12;

        /** double hamming(double x) { return 0.54 - 0.46 * cos(2 * PI * x); } */
        private static double blackman(double x) {
            return 0.42 - 0.5 * Math.cos(2 * _PI_ * x) + 0.08 * Math.cos(4 * _PI_ * x);
        }

        private static double sinC(double x) {
            return (x == 0.0 ? 1.0 : Math.sin(_PI_ * x) / (_PI_ * x));
        }

        private static double windowed_sinC(double x) {
            return blackman(0.5 + 0.5 * x / (LW / 2)) * sinC(x);
        }

        private static int lookup_sinc_table(int[] table, double x) {
            int index = (int) (x * SINC_RESO);
            if (index < 0)
                index = -index;
            return table[Math.min(SINC_RESO * LW / 2 - 1, index)];
        }

        private final int ch;
        private double timer;
        private final double f_ratio;
        private final int[] sinc_table;
        private final short[][] buf;

        /** f_inp: input frequency. f_out: output frequency, ch: number of channels */
        RateConv(double f_inp, double f_out, int ch) {
            this.ch = ch;
            this.f_ratio = f_inp / f_out;
            this.buf = new short[ch][];
            for (int i = 0; i < ch; i++) {
                this.buf[i] = new short[LW];
            }

            // create sinc_table for positive 0 <= x < LW/2
            this.sinc_table = new int[SINC_RESO * LW / 2];
            for (int i = 0; i < SINC_RESO * LW / 2; i++) {
                double x = (double) i / SINC_RESO;
                if (f_out < f_inp) {
                    // for down-sampling
                    this.sinc_table[i] = (int) ((1 << SINC_AMP_BITS) * windowed_sinC(x / this.f_ratio) / this.f_ratio);
                } else {
                    // for up-sampling
                    this.sinc_table[i] = (int) ((1 << SINC_AMP_BITS) * windowed_sinC(x));
                }
            }
        }

        /**
         * get resampled data from this converter at f_out.
         * this function must be called f_out / f_inp times per one putData call.
         */
        private short getData(int ch) {
            short[] buf = this.buf[ch];
            int sum = 0;
            double dn;
            timer += f_ratio;
            dn = timer - Math.floor(timer);
            timer = dn;

            for (int k = 0; k < LW; k++) {
                double x = ((double) k - (LW / 2 - 1)) - dn;
                sum += buf[k] * lookup_sinc_table(sinc_table, x);
            }
            return (short) (sum >> SINC_AMP_BITS);
        }

        /** put original data to this converter at f_inp. */
        private void putData(int ch, short data) {
            short[] buf = this.buf[ch];
            for (int i = 0; i < LW - 1; i++) {
                buf[i] = buf[i + 1];
            }
            buf[LW - 1] = data;
        }

        private void reset() {
            timer = 0;
            for (int i = 0; i < ch; i++) {
                for (int j = 0; j < LW; j++) {
                    buf[i][j] = 0;
                }
            }
        }
    }

    private int clk;
    private int rate;

    private int chipType;

    private int adr;

    private double inpStep;
    private double outStep;
    private double outTime;

    private final int[] reg = new int[0x40];
    private int testFlag;
    private int slotKeyStatus;
    private int rhythmMode;

    private int egCounter;

    private int pmPhase;
    private int amPhase;

    private int lfoAm;

    private int noise;
    private int shortNoise;

    private final int[] patchNumber = new int[9];
    private final Slot[] slot = new Slot[18];
    private final Patch[][] patch = {
            new Patch[2], new Patch[2], new Patch[2], new Patch[2],
            new Patch[2], new Patch[2], new Patch[2], new Patch[2],
            new Patch[2], new Patch[2], new Patch[2], new Patch[2],
            new Patch[2], new Patch[2], new Patch[2], new Patch[2],
            new Patch[2], new Patch[2], new Patch[2]
    };

    private final int[] pan = new int[16];
    private final float[][] panFine = {
            new float[2], new float[2], new float[2], new float[2],
            new float[2], new float[2], new float[2], new float[2],
            new float[2], new float[2], new float[2], new float[2],
            new float[2], new float[2], new float[2], new float[2]};

    private int mask;

    /* channel output */
    /* 0..8:tone 9:bd 10:hh 11:sd 12:tom 13:cym */
    private final short[] chOut = new short[14];

    private final short[] mixOut = new short[2];

    private RateConv conv;

    private int panCh = 0;

    private static final double _PI_ = 3.14159265358979323846264338327950288;

    private static final int OPLL_TONE_NUM = 3;
    private static final short[][] defaultInst = {
            {
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 0: User
                    0x71, 0x61, 0x1e, 0x17, 0xd0, 0x78, 0x00, 0x17, // 1: Violin
                    0x13, 0x41, 0x1a, 0x0d, 0xd8, 0xf7, 0x23, 0x13, // 2: Guitar
                    0x13, 0x01, 0x99, 0x00, 0xf2, 0xc4, 0x21, 0x23, // 3: Piano
                    0x11, 0x61, 0x0e, 0x07, 0x8d, 0x64, 0x70, 0x27, // 4: Flute
                    0x32, 0x21, 0x1e, 0x06, 0xe1, 0x76, 0x01, 0x28, // 5: Clarinet
                    0x31, 0x22, 0x16, 0x05, 0xe0, 0x71, 0x00, 0x18, // 6: Oboe
                    0x21, 0x61, 0x1d, 0x07, 0x82, 0x81, 0x11, 0x07, // 7: Trumpet
                    0x33, 0x21, 0x2d, 0x13, 0xb0, 0x70, 0x00, 0x07, // 8: Organ
                    0x61, 0x61, 0x1b, 0x06, 0x64, 0x65, 0x10, 0x17, // 9: Horn
                    0x41, 0x61, 0x0b, 0x18, 0x85, 0xf0, 0x81, 0x07, // A: Synthesizer
                    0x33, 0x01, 0x83, 0x11, 0xea, 0xef, 0x10, 0x04, // B: Harpsichord
                    0x17, 0xc1, 0x24, 0x07, 0xf8, 0xf8, 0x22, 0x12, // C: Vibraphone
                    0x61, 0x50, 0x0c, 0x05, 0xd2, 0xf5, 0x40, 0x42, // D: Synthsizer Bass
                    0x01, 0x01, 0x55, 0x03, 0xe9, 0x90, 0x03, 0x02, // E: Acoustic Bass
                    0x41, 0x41, 0x89, 0x03, 0xf1, 0xe4, 0xc0, 0x13, // F: Electric Guitar
                    0x01, 0x01, 0x18, 0x0f, 0xdf, 0xf8, 0x6a, 0x6d, // R: Bass Drum (from VRC7)
                    0x01, 0x01, 0x00, 0x00, 0xc8, 0xd8, 0xa7, 0x68, // R: High-Hat(M) / Snare Drum(C) (from VRC7)
                    0x05, 0x01, 0x00, 0x00, 0xf8, 0xaa, 0x59, 0x55, // R: Tom-tom(M) / Top Cymbal(C) (from VRC7)
            },
            {
                    /* VRC7 presets from Nuke.YKT */
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x03, 0x21, 0x05, 0x06, 0xe8, 0x81, 0x42, 0x27,
                    0x13, 0x41, 0x14, 0x0d, 0xd8, 0xf6, 0x23, 0x12,
                    0x11, 0x11, 0x08, 0x08, 0xfa, 0xb2, 0x20, 0x12,
                    0x31, 0x61, 0x0c, 0x07, 0xa8, 0x64, 0x61, 0x27,
                    0x32, 0x21, 0x1e, 0x06, 0xe1, 0x76, 0x01, 0x28,
                    0x02, 0x01, 0x06, 0x00, 0xa3, 0xe2, 0xf4, 0xf4,
                    0x21, 0x61, 0x1d, 0x07, 0x82, 0x81, 0x11, 0x07,
                    0x23, 0x21, 0x22, 0x17, 0xa2, 0x72, 0x01, 0x17,
                    0x35, 0x11, 0x25, 0x00, 0x40, 0x73, 0x72, 0x01,
                    0xb5, 0x01, 0x0f, 0x0F, 0xa8, 0xa5, 0x51, 0x02,
                    0x17, 0xc1, 0x24, 0x07, 0xf8, 0xf8, 0x22, 0x12,
                    0x71, 0x23, 0x11, 0x06, 0x65, 0x74, 0x18, 0x16,
                    0x01, 0x02, 0xd3, 0x05, 0xc9, 0x95, 0x03, 0x02,
                    0x61, 0x63, 0x0c, 0x00, 0x94, 0xC0, 0x33, 0xf6,
                    0x21, 0x72, 0x0d, 0x00, 0xc1, 0xd5, 0x56, 0x06,
                    0x01, 0x01, 0x18, 0x0f, 0xdf, 0xf8, 0x6a, 0x6d,
                    0x01, 0x01, 0x00, 0x00, 0xc8, 0xd8, 0xa7, 0x68,
                    0x05, 0x01, 0x00, 0x00, 0xf8, 0xaa, 0x59, 0x55,
            },
            {
                    /* YMF281B presets */
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 0: User
                    0x62, 0x21, 0x1a, 0x07, 0xf0, 0x6f, 0x00, 0x16, // 1: Electric Strings (form Chabin's patch)
                    0x40, 0x10, 0x45, 0x00, 0xf6, 0x83, 0x73, 0x63, // 2: Bow Wow (based on plgDavid's patch, KSL fixed)
                    0x13, 0x01, 0x99, 0x00, 0xf2, 0xc3, 0x21, 0x23, // 3: Electric Guitar (similar to YM2413 but different dr(C))
                    0x01, 0x61, 0x0b, 0x0f, 0xf9, 0x64, 0x70, 0x17, // 4: Organ (based on Chabin, tl/dr fixed)
                    0x32, 0x21, 0x1e, 0x06, 0xe1, 0x76, 0x01, 0x28, // 5: Clarinet (identical to YM2413)
                    0x60, 0x01, 0x82, 0x0e, 0xf9, 0x61, 0x20, 0x27, // 6: Saxophone (based on plgDavid, pm/eg fixed)
                    0x21, 0x61, 0x1c, 0x07, 0x84, 0x81, 0x11, 0x07, // 7: Trumpet (similar to YM2413 but different tl/dr(M))
                    0x37, 0x32, 0xc9, 0x01, 0x66, 0x64, 0x40, 0x28, // 8: Street Organ (from Chabin)
                    0x01, 0x21, 0x07, 0x03, 0xa5, 0x71, 0x51, 0x07, // 9: Synth Brass (based on Chabin, tl fixed)
                    0x06, 0x01, 0x5e, 0x07, 0xf3, 0xf3, 0xf6, 0x13, // A: Electric Piano (based on Chabin, dr/rr/kr fixed)
                    0x00, 0x00, 0x18, 0x06, 0xf5, 0xf3, 0x20, 0x23, // B: Bass (based on Chabin, eg fixed)
                    0x17, 0xc1, 0x24, 0x07, 0xf8, 0xf8, 0x22, 0x12, // C: Vibraphone (identical to YM2413)
                    0x35, 0x64, 0x00, 0x00, 0xff, 0xf3, 0x77, 0xf5, // D: Chimes (from plgDavid)
                    0x11, 0x31, 0x00, 0x07, 0xdd, 0xf3, 0xff, 0xfb, // E: Tom Tom II (from plgDavid)
                    0x3a, 0x21, 0x00, 0x07, 0x80, 0x84, 0x0f, 0xf5, // F: Noise (based on plgDavid, ar fixed)
                    0x01, 0x01, 0x18, 0x0f, 0xdf, 0xf8, 0x6a, 0x6d, // R: Bass Drum (identical to YM2413)
                    0x01, 0x01, 0x00, 0x00, 0xc8, 0xd8, 0xa7, 0x68, // R: High-Hat(M) / Snare Drum(C) (identical to YM2413)
                    0x05, 0x01, 0x00, 0x00, 0xf8, 0xaa, 0x59, 0x55, // R: Tom-tom(M) / Top Cymbal(C) (identical to YM2413)
            }
    };

    // sine table
    private static final int PG_BITS = 10; /* 2^10 = 1024 length sine table */
    private static final int PG_WIDTH = (1 << PG_BITS);

    // phase increment counter
    private static final int DP_BITS = 19;
    private static final int DP_WIDTH = (1 << DP_BITS);
    private static final int DP_BASE_BITS = (DP_BITS - PG_BITS);

    // dynamic range of envelope output
    private static final double EG_STEP = 0.375;
    private static final int EG_BITS = 7;
    private static final int EG_MUTE = ((1 << EG_BITS) - 1);
    private static final int EG_MAX = (EG_MUTE - 4);

    // dynamic range of total level
    private static final double TL_STEP = 0.75;
    private static final int TL_BITS = 6;

    // dynamic range of sustain level
    private static final double SL_STEP = 3.0;
    private static final int SL_BITS = 4;

    /** damper speed before key-on. key-scale affects. */
    private static final int DAMPER_RATE = 12;

    private static int tl2Eg(int d) {
        return d << 1;
    }

    /** expTable[x] = round((exp2((double)x / 256.0) - 1) * 1024) */
    private static final int[] expTable = {
            0, 3, 6, 8, 11, 14, 17, 20, 22, 25, 28, 31, 34, 37, 40, 42,
            45, 48, 51, 54, 57, 60, 63, 66, 69, 72, 75, 78, 81, 84, 87, 90,
            93, 96, 99, 102, 105, 108, 111, 114, 117, 120, 123, 126, 130, 133, 136, 139,
            142, 145, 148, 152, 155, 158, 161, 164, 168, 171, 174, 177, 181, 184, 187, 190,
            194, 197, 200, 204, 207, 210, 214, 217, 220, 224, 227, 231, 234, 237, 241, 244,
            248, 251, 255, 258, 262, 265, 268, 272, 276, 279, 283, 286, 290, 293, 297, 300,
            304, 308, 311, 315, 318, 322, 326, 329, 333, 337, 340, 344, 348, 352, 355, 359,
            363, 367, 370, 374, 378, 382, 385, 389, 393, 397, 401, 405, 409, 412, 416, 420,
            424, 428, 432, 436, 440, 444, 448, 452, 456, 460, 464, 468, 472, 476, 480, 484,
            488, 492, 496, 501, 505, 509, 513, 517, 521, 526, 530, 534, 538, 542, 547, 551,
            555, 560, 564, 568, 572, 577, 581, 585, 590, 594, 599, 603, 607, 612, 616, 621,
            625, 630, 634, 639, 643, 648, 652, 657, 661, 666, 670, 675, 680, 684, 689, 693,
            698, 703, 708, 712, 717, 722, 726, 731, 736, 741, 745, 750, 755, 760, 765, 770,
            774, 779, 784, 789, 794, 799, 804, 809, 814, 819, 824, 829, 834, 839, 844, 849,
            854, 859, 864, 869, 874, 880, 885, 890, 895, 900, 906, 911, 916, 921, 927, 932,
            937, 942, 948, 953, 959, 964, 969, 975, 980, 986, 991, 996, 1002, 1007, 1013, 1018
    };
    /** fullSin_table[x] = round(-log2(sin((x + 0.5) * PI / (PG_WIDTH / 4) / 2)) * 256) */
    private static final int[] fullSinTable = { // PG_WIDTH
            2137, 1731, 1543, 1419, 1326, 1252, 1190, 1137, 1091, 1050, 1013, 979, 949, 920, 894, 869,
            846, 825, 804, 785, 767, 749, 732, 717, 701, 687, 672, 659, 646, 633, 621, 609,
            598, 587, 576, 566, 556, 546, 536, 527, 518, 509, 501, 492, 484, 476, 468, 461,
            453, 446, 439, 432, 425, 418, 411, 405, 399, 392, 386, 380, 375, 369, 363, 358,
            352, 347, 341, 336, 331, 326, 321, 316, 311, 307, 302, 297, 293, 289, 284, 280,
            276, 271, 267, 263, 259, 255, 251, 248, 244, 240, 236, 233, 229, 226, 222, 219,
            215, 212, 209, 205, 202, 199, 196, 193, 190, 187, 184, 181, 178, 175, 172, 169,
            167, 164, 161, 159, 156, 153, 151, 148, 146, 143, 141, 138, 136, 134, 131, 129,
            127, 125, 122, 120, 118, 116, 114, 112, 110, 108, 106, 104, 102, 100, 98, 96,
            94, 92, 91, 89, 87, 85, 83, 82, 80, 78, 77, 75, 74, 72, 70, 69,
            67, 66, 64, 63, 62, 60, 59, 57, 56, 55, 53, 52, 51, 49, 48, 47,
            46, 45, 43, 42, 41, 40, 39, 38, 37, 36, 35, 34, 33, 32, 31, 30,
            29, 28, 27, 26, 25, 24, 23, 23, 22, 21, 20, 20, 19, 18, 17, 17,
            16, 15, 15, 14, 13, 13, 12, 12, 11, 10, 10, 9, 9, 8, 8, 7,
            7, 7, 6, 6, 5, 5, 5, 4, 4, 4, 3, 3, 3, 2, 2, 2,
            2, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0,

            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    };

    private static final int[] halfSinTable = new int[PG_WIDTH];
    private static final int[][] waveTableMap = {
            new int[PG_WIDTH], new int[PG_WIDTH]
    }; // { fullSin_table, halfSinTable };

    /**
     * pitch modulator
     * offset to fNum, rough approximation of 14 cents depth.
     */
    private static final int[][] pmTable = {
            {0, 0, 0, 0, 0, 0, 0, 0},    // fNum = 000xxxxxx
            {0, 0, 1, 0, 0, 0, -1, 0},   // fNum = 001xxxxxx
            {0, 1, 2, 1, 0, -1, -2, -1}, // fNum = 010xxxxxx
            {0, 1, 3, 1, 0, -1, -3, -1}, // fNum = 011xxxxxx
            {0, 2, 4, 2, 0, -2, -4, -2}, // fNum = 100xxxxxx
            {0, 2, 5, 2, 0, -2, -5, -2}, // fNum = 101xxxxxx
            {0, 3, 6, 3, 0, -3, -6, -3}, // fNum = 110xxxxxx
            {0, 3, 7, 3, 0, -3, -7, -3}, // fNum = 111xxxxxx
    };

    /**
     * amplitude lfo table
     * The following envelop pattern is verified on real YM2413.
     * each element repeats 64 cycles
     */
    private static final int[] amTable = {
            0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1,  //
            2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3,  //
            4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5,  //
            6, 6, 6, 6, 6, 6, 6, 6, 7, 7, 7, 7, 7, 7, 7, 7,  //
            8, 8, 8, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9, 9, 9,  //
            10, 10, 10, 10, 10, 10, 10, 10, 11, 11, 11, 11, 11, 11, 11, 11, //
            12, 12, 12, 12, 12, 12, 12, 12,                                 //
            13, 13, 13,                                                     //
            12, 12, 12, 12, 12, 12, 12, 12,                                 //
            11, 11, 11, 11, 11, 11, 11, 11, 10, 10, 10, 10, 10, 10, 10, 10, //
            9, 9, 9, 9, 9, 9, 9, 9, 8, 8, 8, 8, 8, 8, 8, 8,  //
            7, 7, 7, 7, 7, 7, 7, 7, 6, 6, 6, 6, 6, 6, 6, 6,  //
            5, 5, 5, 5, 5, 5, 5, 5, 4, 4, 4, 4, 4, 4, 4, 4,  //
            3, 3, 3, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2,  //
            1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0};

    /**
     * envelope decay increment step table
     * based on andete's research
     */
    private static final int[][] egStepTables = {
            {0, 1, 0, 1, 0, 1, 0, 1},
            {0, 1, 0, 1, 1, 1, 0, 1},
            {0, 1, 1, 1, 0, 1, 1, 1},
            {0, 1, 1, 1, 1, 1, 1, 1},
    };

    private static final int[] mlTable = {
            1, 1 * 2, 2 * 2, 3 * 2, 4 * 2, 5 * 2, 6 * 2, 7 * 2,
            8 * 2, 9 * 2, 10 * 2, 10 * 2, 12 * 2, 12 * 2, 15 * 2, 15 * 2
    };

    //#define dB2(x) ((x)*2)
    //        static double klTable[16] = {dB2(0.000),  dB2(9.000),  dB2(12.000), dB2(13.875), dB2(15.000), dB2(16.125),
    //                              dB2(16.875), dB2(17.625), dB2(18.000), dB2(18.750), dB2(19.125), dB2(19.500),
    //                              dB2(19.875), dB2(20.250), dB2(20.625), dB2(21.000)};
    private static final double[] klTable = {
            0.000 * 2, 9.000 * 2, 12.000 * 2, 13.875 * 2, 15.000 * 2, 16.125 * 2,
            16.875 * 2, 17.625 * 2, 18.000 * 2, 18.750 * 2, 19.125 * 2, 19.500 * 2,
            19.875 * 2, 20.250 * 2, 20.625 * 2, 21.000 * 2
    };

    private static int[][][] tllTable; // new int[8 * 16][1 << TL_BITS][4]

    private static final int[][] rksTable = {
            new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2],
            new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2]
    };

    private static final Patch NullPatch = new Patch() {{
        am = 0;
        ar = 0;
        dr = 0;
        eg = 0;
        fb = 0;
        kl = 0;
        kr = 0;
        ml = 0;
        pm = 0;
        rr = 0;
        sl = 0;
        tl = 0;
        ws = 0;
    }};

    private static final Patch[][][] defaultPatch = {
            {
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()}
            },
            {
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()}
            },
            {
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()},
                    {new Patch(), new Patch()}
            }
    };

    //
    // Internal Sample Rate Converter
    //

    // Note: to disable internal rate converter, set clock/72 to output sampling rate.

    /**
     * Create tables
     */
    private static void makeSinTable() {
        for (int x = 0; x < PG_WIDTH / 4; x++) {
            fullSinTable[PG_WIDTH / 4 + x] = fullSinTable[PG_WIDTH / 4 - x - 1];
        }

        for (int x = 0; x < PG_WIDTH / 2; x++) {
            fullSinTable[PG_WIDTH / 2 + x] = 0x8000 | fullSinTable[x];
        }

        System.arraycopy(fullSinTable, 0, halfSinTable, 0, PG_WIDTH / 2);

        for (int x = PG_WIDTH / 2; x < PG_WIDTH; x++)
            halfSinTable[x] = 0xfff;
        for (int j = 0; j < PG_WIDTH; j++) {
            waveTableMap[0][j] = fullSinTable[j];
            waveTableMap[1][j] = halfSinTable[j];
        }
    }

    private static void makeTllTable() {
        if (tllTable == null) {
            tllTable = new int[8 * 16][][];
            for (int i = 0; i < 8 * 16; i++) {
                tllTable[i] = new int[1 << TL_BITS][];
                for (int j = 0; j < 1 << TL_BITS; j++) {
                    tllTable[i][j] = new int[4];
                    for (int k = 0; k < 4; k++) {
                        tllTable[i][j][k] = 0;
                    }
                }
            }
        }

        for (int fnum = 0; fnum < 16; fnum++) {
            for (int block = 0; block < 8; block++) {
                for (int tl = 0; tl < 64; tl++) {
                    for (int kl = 0; kl < 4; kl++) {
                        if (kl == 0) {
                            tllTable[(block << 4) | fnum][tl][kl] = tl2Eg(tl);
                        } else {
                            int tmp = (int) (klTable[fnum] - (3.000) * 2 * (7 - block));
                            if (tmp <= 0)
                                tllTable[(block << 4) | fnum][tl][kl] = tl2Eg(tl);
                            else
                                tllTable[(block << 4) | fnum][tl][kl] = (int) ((tmp >> (3 - kl)) / EG_STEP) + tl2Eg(tl);
                        }
                    }
                }
            }
        }
    }

    private static void makeRksTable() {
        for (int fNum8 = 0; fNum8 < 2; fNum8++)
            for (int block = 0; block < 8; block++) {
                rksTable[(block << 1) | fNum8][1] = (block << 1) + fNum8;
                rksTable[(block << 1) | fNum8][0] = block >> 1;
            }
    }

    private void makeDefaultPatch() {
        for (int i = 0; i < OPLL_TONE_NUM; i++)
            for (int j = 0; j < 19; j++)
                getDefaultPatch(i, j, defaultPatch);
    }

    private boolean table_initialized = false;

    private void initializeTables() {
        makeTllTable();
        makeRksTable();
        makeSinTable();
        makeDefaultPatch();
        table_initialized = true;
    }

    //
    // Synthesizing
    //

    // utility macros

    private Slot mod(int x) {
        return this.slot[x << 1];
    }

    private Slot car(int x) {
        return this.slot[(x << 1) | 1];
    }

    private static int bit(int s, int b) {
        return (s >> b) & 1;
    }

    private void updateKeyStatus() {
        int r14 = this.reg[0x0e];
        int rhythmMode = bit(r14, 5);
        int newSlotKeyStatus = 0;
        int updatedStatus;

        for (int ch = 0; ch < 9; ch++)
            if ((this.reg[0x20 + ch] & 0x10) != 0)
                newSlotKeyStatus |= 3 << (ch * 2);

        if (rhythmMode != 0) {
            if ((r14 & 0x10) != 0)
                newSlotKeyStatus |= 3 << Slot.BD1;

            if ((r14 & 0x01) != 0)
                newSlotKeyStatus |= 1 << Slot.HH;

            if ((r14 & 0x08) != 0)
                newSlotKeyStatus |= 1 << Slot.SD;

            if ((r14 & 0x04) != 0)
                newSlotKeyStatus |= 1 << Slot.TOM;

            if ((r14 & 0x02) != 0)
                newSlotKeyStatus |= 1 << Slot.CYM;
        }

        updatedStatus = this.slotKeyStatus ^ newSlotKeyStatus;

        if (updatedStatus != 0) {
            for (int i = 0; i < 18; i++)
                if (bit(updatedStatus, i) != 0) {
                    if (bit(newSlotKeyStatus, i) != 0) {
                        this.slot[i].slotOn();
                    } else {
                        this.slot[i].slotOff();
                    }
                }
        }

        this.slotKeyStatus = newSlotKeyStatus;
    }

    private void setPatch(int ch, int num) {
        this.patchNumber[ch] = num;
        mod(ch).patch = this.patch[num][0];
        car(ch).patch = this.patch[num][1];
        mod(ch).requestUpdate(Update.ALL.v);
        car(ch).requestUpdate(Update.ALL.v);
    }

    private void setSusFlag(int ch, int flag) {
        car(ch).susFlag = flag;
        car(ch).requestUpdate(Update.EG.v);
        if ((mod(ch).type & 1) != 0) {
            mod(ch).susFlag = flag;
            mod(ch).requestUpdate(Update.EG.v);
        }
    }

    /* set volume ( volume : 6bit, register value << 2 ) */
    private void setVolume(int ch, int volume) {
        car(ch).volume = volume;
        car(ch).requestUpdate(Update.TLL.v);
    }

    /* set f-Number ( fNum : 9bit ) */
    private void setFNumber(int ch, int fNum) {
        Slot car = car(ch);
        Slot mod = mod(ch);
        car.fNum = fNum;
        car.blkFNum = (car.blkFNum & 0xe00) | (fNum & 0x1ff);
        mod.fNum = fNum;
        mod.blkFNum = (mod.blkFNum & 0xe00) | (fNum & 0x1ff);
        car.requestUpdate(Update.EG.v | Update.RKS.v | Update.TLL.v);
        mod.requestUpdate(Update.EG.v | Update.RKS.v | Update.TLL.v);
    }

    /* set block data (blk : 3bit ) */
    private void setBlock(int ch, int blk) {
        Slot car = car(ch);
        Slot mod = mod(ch);
        car.blk = blk;
        car.blkFNum = ((blk & 7) << 9) | (car.blkFNum & 0x1ff);
        mod.blk = blk;
        mod.blkFNum = ((blk & 7) << 9) | (mod.blkFNum & 0x1ff);
        car.requestUpdate(Update.EG.v | Update.RKS.v | Update.TLL.v);
        mod.requestUpdate(Update.EG.v | Update.RKS.v | Update.TLL.v);
    }

    private void updateRhythmMode() {
        int newRhythmMode = (this.reg[0x0e] >> 5) & 1;

        if (this.rhythmMode != newRhythmMode) {

            if (newRhythmMode != 0) {
                this.slot[Slot.HH].type = 3;
                this.slot[Slot.HH].pg_keep = 1;
                this.slot[Slot.SD].type = 3;
                this.slot[Slot.TOM].type = 3;
                this.slot[Slot.CYM].type = 3;
                this.slot[Slot.CYM].pg_keep = 1;
                setPatch(6, 16);
                setPatch(7, 17);
                setPatch(8, 18);
                this.slot[Slot.HH].setSlotVolume(((this.reg[0x37] >> 4) & 15) << 2);
                this.slot[Slot.TOM].setSlotVolume(((this.reg[0x38] >> 4) & 15) << 2);
            } else {
                this.slot[Slot.HH].type = 0;
                this.slot[Slot.HH].pg_keep = 0;
                this.slot[Slot.SD].type = 1;
                this.slot[Slot.TOM].type = 0;
                this.slot[Slot.CYM].type = 1;
                this.slot[Slot.CYM].pg_keep = 0;
                setPatch(6, this.reg[0x36] >> 4);
                setPatch(7, this.reg[0x37] >> 4);
                setPatch(8, this.reg[0x38] >> 4);
            }
        }

        this.rhythmMode = newRhythmMode;
    }

    private void update_ampm() {
        if ((this.testFlag & 2) != 0) {
            this.pmPhase = 0;
            this.amPhase = 0;
        } else {
            this.pmPhase += (this.testFlag & 8) != 0 ? 1024 : 1;
            this.amPhase += (this.testFlag & 8) != 0 ? 64 : 1;
        }
        this.lfoAm = amTable[(this.amPhase >> 6) % amTable.length]; // sizeof(amTable)];
    }

    private void update_noise(int cycle) {
        for (int i = 0; i < cycle; i++) {
            if ((this.noise & 1) != 0) {
                this.noise ^= 0x800200;
            }
            this.noise >>= 1;
        }
    }

    private void update_short_noise() {
        int pgHh = this.slot[Slot.HH].pgOut;
        int pgCym = this.slot[Slot.CYM].pgOut;

        int hBit2 = bit(pgHh, PG_BITS - 8);
        int hBit7 = bit(pgHh, PG_BITS - 3);
        int hBit3 = bit(pgHh, PG_BITS - 7);

        int cBit3 = bit(pgCym, PG_BITS - 7);
        int cBit5 = bit(pgCym, PG_BITS - 5);

        this.shortNoise = (hBit2 ^ hBit7) | (hBit3 ^ cBit5) | (cBit3 ^ cBit5);
    }

    private void update_slots() {
        this.egCounter++;

        for (int i = 0; i < 18; i++) {
            Slot slot = this.slot[i];
            Slot buddy = null;
            if (slot.type == 0) {
                buddy = this.slot[i + 1];
            }
            if (slot.type == 1) {
                buddy = this.slot[i - 1];
            }
            if (slot.updateRequests != 0) {
                slot.commitSlotUpdate();
            }
            slot.calcEnvelope(buddy, this.egCounter, this.testFlag & 1);
            slot.calcPhase(this.pmPhase, this.testFlag & 4);
        }
    }

    private static int mo(int x) {
        return -x >> 1;
    }

    private static int ro(int x) {
        return x;
    }

    private void updateOutput() {
        short[] out;

        update_ampm();
        update_short_noise();
        update_slots();

        out = this.chOut;

        // CH1-6
        for (int i = 0; i < 6; i++) {
            if ((this.mask & OPLL_MASK_CH(i)) == 0) {
                out[i] = (short) mo(car(i).calcSlotCar(mod(i).calcSlotMod(this.lfoAm), this.lfoAm));
            }
        }

        // CH7
        if (this.rhythmMode == 0) {
            if ((this.mask & OPLL_MASK_CH(6)) == 0) {
                out[6] = (short) mo(car(6).calcSlotCar(mod(6).calcSlotMod(this.lfoAm), this.lfoAm));
            }
        } else {
            if ((this.mask & OPLL_MASK_BD) == 0) {
                out[9] = (short) ro(car(6).calcSlotCar(mod(6).calcSlotMod(this.lfoAm), this.lfoAm));
            }
        }
        update_noise(14);

        // CH8
        if (this.rhythmMode == 0) {
            if ((this.mask & OPLL_MASK_CH(7)) == 0) {
                out[7] = (short) mo(car(7).calcSlotCar(mod(7).calcSlotMod(this.lfoAm), this.lfoAm));
            }
        } else {
            if ((this.mask & OPLL_MASK_HH) == 0) {
                out[10] = (short) ro(mod(7).calcSlotHat(this.noise, this.shortNoise));
            }
            if ((this.mask & OPLL_MASK_SD) == 0) {
                out[11] = (short) ro(car(7).calcSlotSnare(this.noise));
            }
        }
        update_noise(2);

        // CH9
        if (this.rhythmMode == 0) {
            if ((this.mask & OPLL_MASK_CH(8)) == 0) {
                out[8] = (short) mo(car(8).calcSlotCar(mod(8).calcSlotMod(this.lfoAm), this.lfoAm));
            }
        } else {
            if ((this.mask & OPLL_MASK_TOM) == 0) {
                out[12] = (short) ro(mod(8).calcSlotTom());
            }
            if ((this.mask & OPLL_MASK_CYM) == 0) {
                out[13] = (short) ro(car(8).calcSlotCym(this.shortNoise));
            }
        }
        update_noise(2);
    }

    private void mixOutput() {
        short out = 0;
        for (int i = 0; i < 14; i++) {
            out += this.chOut[i];
        }
        if (this.conv != null) {
            this.conv.putData(0, out);
        } else {
            this.mixOut[0] = out;
        }
    }

    private void mixOutputStereo() {
        short[] out = this.mixOut;
        out[0] = out[1] = 0;
        for (int i = 0; i < 14; i++) {
            if ((this.pan[i] & 2) != 0)
                out[0] += (short) (this.chOut[i] * this.panFine[i][0]);
            if ((this.pan[i] & 1) != 0)
                out[1] += (short) (this.chOut[i] * this.panFine[i][1]);
        }
        if (this.conv != null) {
            this.conv.putData(0, out[0]);
            this.conv.putData(1, out[1]);
        }
    }

    //
    // External Interfaces
    //

    /** */
    public void init(int clk, int rate) {
        if (!table_initialized) {
            initializeTables();
        }

        for (int i = 0; i < 19; i++) {
            this.patch[i] = new Patch[2]; // NullPatch;
            for (int j = 0; j < 2; j++) {
                this.patch[i][j] = new Patch();
            }
        }

        this.clk = clk;
        this.rate = rate;
        this.mask = 0;
        this.conv = null;
        this.mixOut[0] = 0;
        this.mixOut[1] = 0;

        reset();
        setChipType(0);
        resetPatch(0);
    }

    private void resetRateConversionParams() {
        double fOut = this.rate;
        double fInp = this.clk / 72.0;

        this.outTime = 0;
        this.outStep = fInp;
        this.inpStep = fOut;

        if (this.conv != null) {
            this.conv = null;
        }

        if (Math.floor(fInp) != fOut && Math.floor(fInp + 0.5) != fOut) {
            this.conv = new RateConv(fInp, fOut, 2);
        }

        if (this.conv != null) {
            this.conv.reset();
        }
    }

    /** */
    public void reset() {
        this.adr = 0;

        this.pmPhase = 0;
        this.amPhase = 0;

        this.noise = 0x1;
        this.mask = 0;

        this.rhythmMode = 0;
        this.slotKeyStatus = 0;
        this.egCounter = 0;

        resetRateConversionParams();

        for (int i = 0; i < 18; i++) {
            this.slot[i] = new Slot();
            this.slot[i].reset(i);
        }

        for (int i = 0; i < 9; i++) {
            setPatch(i, 0);
        }

        for (int i = 0; i < 0x40; i++)
            writeReg(i, 0);

        for (int i = 0; i < 15; i++) {
            this.pan[i] = 3;
            this.panFine[i][1] = this.panFine[i][0] = 1.0f;
        }

        for (int i = 0; i < 14; i++) {
            this.chOut[i] = 0;
        }
    }

    /** */
    public void forceRefresh() {
        for (int i = 0; i < 9; i++) {
            setPatch(i, this.patchNumber[i]);
        }

        for (int i = 0; i < 18; i++) {
            this.slot[i].requestUpdate(Update.ALL.v);
        }
    }

    /** */
    public void setRate(int rate) {
        this.rate = rate;
        resetRateConversionParams();
    }

    /** */
    public void setQuality(int q) {
    }

    /** */
    public void setChipType(int type) {
        this.chipType = type;
    }

    /** */
    public void writeReg(int reg, int data) {
        int ch;

        if (reg >= 0x40) {
            extendFunction(reg, data);
            return;
        }

        // mirror registers
        if ((0x19 <= reg && reg <= 0x1f) || (0x29 <= reg && reg <= 0x2f) || (0x39 <= reg && reg <= 0x3f)) {
            reg -= 9;
        }

        this.reg[reg] = data;

        switch (reg) {
            case 0x00:
                this.patch[0][0].am = (data >> 7) & 1;
                this.patch[0][0].pm = (data >> 6) & 1;
                this.patch[0][0].eg = (data >> 5) & 1;
                this.patch[0][0].kr = (data >> 4) & 1;
                this.patch[0][0].ml = (data) & 15;
                for (int i = 0; i < 9; i++) {
                    if (this.patchNumber[i] == 0) {
                        mod(i).requestUpdate(Update.RKS.v | Update.EG.v);
                    }
                }
                break;

            case 0x01:
                this.patch[0][1].am = (data >> 7) & 1;
                this.patch[0][1].pm = (data >> 6) & 1;
                this.patch[0][1].eg = (data >> 5) & 1;
                this.patch[0][1].kr = (data >> 4) & 1;
                this.patch[0][1].ml = (data) & 15;
                for (int i = 0; i < 9; i++) {
                    if (this.patchNumber[i] == 0) {
                        car(i).requestUpdate(Update.RKS.v | Update.EG.v);
                    }
                }
                break;

            case 0x02:
                this.patch[0][0].kl = (data >> 6) & 3;
                this.patch[0][0].tl = (data) & 63;
                for (int i = 0; i < 9; i++) {
                    if (this.patchNumber[i] == 0) {
                        mod(i).requestUpdate(Update.TLL.v);
                    }
                }
                break;

            case 0x03:
                this.patch[0][1].kl = (data >> 6) & 3;
                this.patch[0][1].ws = (data >> 4) & 1;
                this.patch[0][0].ws = (data >> 3) & 1;
                this.patch[0][0].fb = (data) & 7;
                for (int i = 0; i < 9; i++) {
                    if (this.patchNumber[i] == 0) {
                        mod(i).requestUpdate(Update.WS.v);
                        car(i).requestUpdate(Update.WS.v | Update.TLL.v);
                    }
                }
                break;

            case 0x04:
                this.patch[0][0].ar = (data >> 4) & 15;
                this.patch[0][0].dr = (data) & 15;
                for (int i = 0; i < 9; i++) {
                    if (this.patchNumber[i] == 0) {
                        mod(i).requestUpdate(Update.EG.v);
                    }
                }
                break;

            case 0x05:
                this.patch[0][1].ar = (data >> 4) & 15;
                this.patch[0][1].dr = (data) & 15;
                for (int i = 0; i < 9; i++) {
                    if (this.patchNumber[i] == 0) {
                        car(i).requestUpdate(Update.EG.v);
                    }
                }
                break;

            case 0x06:
                this.patch[0][0].sl = (data >> 4) & 15;
                this.patch[0][0].rr = (data) & 15;
                for (int i = 0; i < 9; i++) {
                    if (this.patchNumber[i] == 0) {
                        mod(i).requestUpdate(Update.EG.v);
                    }
                }
                break;

            case 0x07:
                this.patch[0][1].sl = (data >> 4) & 15;
                this.patch[0][1].rr = (data) & 15;
                for (int i = 0; i < 9; i++) {
                    if (this.patchNumber[i] == 0) {
                        car(i).requestUpdate(Update.EG.v);
                    }
                }
                break;

            case 0x0e:
                if (this.chipType == 1)
                    break;
                updateRhythmMode();
                updateKeyStatus();
                break;

            case 0x0f:
                this.testFlag = data;
                break;

            case 0x10:
            case 0x11:
            case 0x12:
            case 0x13:
            case 0x14:
            case 0x15:
            case 0x16:
            case 0x17:
            case 0x18:
                ch = reg - 0x10;
                setFNumber(ch, data + ((this.reg[0x20 + ch] & 1) << 8));
                break;

            case 0x20:
            case 0x21:
            case 0x22:
            case 0x23:
            case 0x24:
            case 0x25:
            case 0x26:
            case 0x27:
            case 0x28:
                ch = reg - 0x20;
                setFNumber(ch, ((data & 1) << 8) + this.reg[0x10 + ch]);
                setBlock(ch, (data >> 1) & 7);
                setSusFlag(ch, (data >> 5) & 1);
                updateKeyStatus();
                break;

            case 0x30:
            case 0x31:
            case 0x32:
            case 0x33:
            case 0x34:
            case 0x35:
            case 0x36:
            case 0x37:
            case 0x38:
                if ((this.reg[0x0e] & 32) != 0 && (reg >= 0x36)) {
                    switch (reg) {
                        case 0x37:
                            mod(7).setSlotVolume(((data >> 4) & 15) << 2);
                            break;
                        case 0x38:
                            mod(8).setSlotVolume(((data >> 4) & 15) << 2);
                            break;
                        default:
                            break;
                    }
                } else {
                    setPatch(reg - 0x30, (data >> 4) & 15);
                }
                setVolume(reg - 0x30, (data & 15) << 2);
                break;

            default:
                break;
        }
    }

    private void extendFunction(int reg, int data) {
        switch (reg) {
            case 0x40: // Pan Channel specification
                this.panCh = Math.clamp(data, 0, 13);
                break;
            case 0x41: // Pan value specification
                this.pan[this.panCh] = ((data & 0xf0) != 0 ? 0x02 : 0x00) |
                        ((data & 0x0f) != 0 ? 0x01 : 0x00);
                this.pan[this.panCh] = (this.pan[this.panCh] == 0)
                        ? 3
                        : this.pan[this.panCh];

                this.panFine[this.panCh][0] = 1.0f * (data >> 4) / 15.0f;
                this.panFine[this.panCh][1] = 1.0f * (data & 0xf) / 15.0f;
                break;
        }
    }

    /** */
    public void writeIO(int adr, int val) {
        if ((adr & 1) != 0)
            writeReg(this.adr, val);
        else
            this.adr = val;
    }

    /** */
    public void setPan(int ch, int pan) {
        this.pan[ch & 15] = pan;
    }

    /** */
    public void setPanFine(int ch, float[] pan) {
        this.panFine[ch & 15][0] = pan[0];
        this.panFine[ch & 15][1] = pan[1];
    }

    /** */
    public void dumpToPatch(short[] dump, int startAdr, Patch[][] patch) {
        if (patch[startAdr][0] == null) patch[startAdr][0] = new Patch();
        if (patch[startAdr][1] == null) patch[startAdr][1] = new Patch();

        patch[startAdr][0].am = (dump[0 + startAdr * 8] >> 7) & 1;
        patch[startAdr][1].am = (dump[1 + startAdr * 8] >> 7) & 1;
        patch[startAdr][0].pm = (dump[0 + startAdr * 8] >> 6) & 1;
        patch[startAdr][1].pm = (dump[1 + startAdr * 8] >> 6) & 1;
        patch[startAdr][0].eg = (dump[0 + startAdr * 8] >> 5) & 1;
        patch[startAdr][1].eg = (dump[1 + startAdr * 8] >> 5) & 1;
        patch[startAdr][0].kr = (dump[0 + startAdr * 8] >> 4) & 1;
        patch[startAdr][1].kr = (dump[1 + startAdr * 8] >> 4) & 1;
        patch[startAdr][0].ml = (dump[0 + startAdr * 8]) & 15;
        patch[startAdr][1].ml = (dump[1 + startAdr * 8]) & 15;
        patch[startAdr][0].kl = (dump[2 + startAdr * 8] >> 6) & 3;
        patch[startAdr][1].kl = (dump[3 + startAdr * 8] >> 6) & 3;
        patch[startAdr][0].tl = (dump[2 + startAdr * 8]) & 63;
        patch[startAdr][1].tl = 0;
        patch[startAdr][0].fb = (dump[3 + startAdr * 8]) & 7;
        patch[startAdr][1].fb = 0;
        patch[startAdr][0].ws = (dump[3 + startAdr * 8] >> 3) & 1;
        patch[startAdr][1].ws = (dump[3 + startAdr * 8] >> 4) & 1;
        patch[startAdr][0].ar = (dump[4 + startAdr * 8] >> 4) & 15;
        patch[startAdr][1].ar = (dump[5 + startAdr * 8] >> 4) & 15;
        patch[startAdr][0].dr = (dump[4 + startAdr * 8]) & 15;
        patch[startAdr][1].dr = (dump[5 + startAdr * 8]) & 15;
        patch[startAdr][0].sl = (dump[6 + startAdr * 8] >> 4) & 15;
        patch[startAdr][1].sl = (dump[7 + startAdr * 8] >> 4) & 15;
        patch[startAdr][0].rr = (dump[6 + startAdr * 8]) & 15;
        patch[startAdr][1].rr = (dump[7 + startAdr * 8]) & 15;
    }

    /** */
    public void getDefaultPatch(int type, int num, Patch[][][] patch) {
        dumpToPatch(defaultInst[type], num, patch[type]);
    }

    /** */
    public void setPatch(short[] dump) {
        Patch[][] patch = new Patch[2][];
        for (int i = 0; i < 19; i++) {
            dumpToPatch(dump, i, patch);
            this.patch[i][0] = patch[i][0];
            this.patch[i][1] = patch[i][1];
        }
    }

    /** */
    public void patchToDump(Patch[] patch, int[] dump) {
        dump[0] = (patch[0].am << 7) + (patch[0].pm << 6) + (patch[0].eg << 5) + (patch[0].kr << 4) + patch[0].ml;
        dump[1] = (patch[1].am << 7) + (patch[1].pm << 6) + (patch[1].eg << 5) + (patch[1].kr << 4) + patch[1].ml;
        dump[2] = (patch[0].kl << 6) + patch[0].tl;
        dump[3] = (patch[1].kl << 6) + (patch[1].ws << 4) + (patch[0].ws << 3) + patch[0].fb;
        dump[4] = (patch[0].ar << 4) + patch[0].dr;
        dump[5] = (patch[1].ar << 4) + patch[1].dr;
        dump[6] = (patch[0].sl << 4) + patch[0].rr;
        dump[7] = (patch[1].sl << 4) + patch[1].rr;
    }

    /** */
    public void copyPatch(int num, Patch[] patch) {
        this.patch[num][0].am = patch[0].am;
        this.patch[num][0].ar = patch[0].ar;
        this.patch[num][0].dr = patch[0].dr;
        this.patch[num][0].eg = patch[0].eg;
        this.patch[num][0].fb = patch[0].fb;
        this.patch[num][0].kl = patch[0].kl;
        this.patch[num][0].kr = patch[0].kr;
        this.patch[num][0].ml = patch[0].ml;
        this.patch[num][0].pm = patch[0].pm;
        this.patch[num][0].rr = patch[0].rr;
        this.patch[num][0].sl = patch[0].sl;
        this.patch[num][0].tl = patch[0].tl;
        this.patch[num][0].ws = patch[0].ws;

        this.patch[num][1].am = patch[1].am;
        this.patch[num][1].ar = patch[1].ar;
        this.patch[num][1].dr = patch[1].dr;
        this.patch[num][1].eg = patch[1].eg;
        this.patch[num][1].fb = patch[1].fb;
        this.patch[num][1].kl = patch[1].kl;
        this.patch[num][1].kr = patch[1].kr;
        this.patch[num][1].ml = patch[1].ml;
        this.patch[num][1].pm = patch[1].pm;
        this.patch[num][1].rr = patch[1].rr;
        this.patch[num][1].sl = patch[1].sl;
        this.patch[num][1].tl = patch[1].tl;
        this.patch[num][1].ws = patch[1].ws;
    }

    /** */
    public void resetPatch(int type) {
        for (int i = 0; i < 19; i++)
            copyPatch(i, defaultPatch[type][i]);
    }

    /** */
    public short calc() {
        while (this.outStep > this.outTime) {
            this.outTime += this.inpStep;
            updateOutput();
            mixOutput();
        }
        this.outTime -= this.outStep;
        if (this.conv != null) {
            this.mixOut[0] = this.conv.getData(0);
        }
        return this.mixOut[0];
    }

    /** */
    public void calcStereo(int[] out) {
        while (this.outStep > this.outTime) {
            this.outTime += this.inpStep;
            updateOutput();
            mixOutputStereo();
        }
        this.outTime -= this.outStep;
        if (this.conv != null) {
            out[0] = this.conv.getData(0);
            out[1] = this.conv.getData(1);
        } else {
            out[0] = this.mixOut[0];
            out[1] = this.mixOut[1];
        }
    }

    /** */
    public int setMask(int mask) {
        int ret = this.mask;
        this.mask = mask;
        return ret;
    }

    /** */
    public int toggleMask(int mask) {
        int ret = this.mask;
        this.mask ^= mask;
        return ret;
    }
}
