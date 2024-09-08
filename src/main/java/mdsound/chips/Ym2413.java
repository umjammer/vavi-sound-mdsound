package mdsound.chips;

import java.util.function.Consumer;


/**
 * YM2413 emulator written by Mitsutaka Okazaki 2001
 * <p>
 * 2001 01-08 : Version 0.10 -- 1st version.
 * 2001 01-15 : Version 0.20 -- semi-public version.
 * 2001 01-16 : Version 0.30 -- 1st public version.
 * 2001 01-17 : Version 0.31 -- Fixed bassdrum problem.
 *            : Version 0.32 -- LPF implemented.
 * 2001 01-18 : Version 0.33 -- Fixed the drum problem, refine the mix-down method.
 *                           -- Fixed the LFO bug.
 * 2001 01-24 : Version 0.35 -- Fixed the drum problem,
 *                              support undocumented EG behavior.
 * 2001 02-02 : Version 0.38 -- Improved the performance.
 *                              Fixed the hi-hat and cymbal model.
 *                              Fixed the default percussive datas.
 *                              Noise reduction.
 *                              Fixed the feedback problem.
 * 2001 03-03 : Version 0.39 -- Fixed some drum bugs.
 *                              Improved the performance.
 * 2001 03-04 : Version 0.40 -- Improved the feedback.
 *                              Change the default table size.
 *                              Clock and Rate can be changed during play.
 * 2001 06-24 : Version 0.50 -- Improved the hi-hat and the cymbal tone.
 *                              Added VRC7 patch (OPLL_reset_patch is changed).
 *                              Fixed OPLL_reset() bug.
 *                              Added OPLL_setMask, OPLL_getMask and OPLL_toggleMask.
 *                              Added OPLL_writeIO.
 * 2001 09-28 : Version 0.51 -- Removed the noise table.
 * 2002 01-28 : Version 0.52 -- Added Stereo mode.
 * 2002 02-07 : Version 0.53 -- Fixed some drum bugs.
 * 2002 02-20 : Version 0.54 -- Added the best quality mode.
 * 2002 03-02 : Version 0.55 -- Removed OPLL_init & OPLL_close.
 * 2002 05-30 : Version 0.60 -- Fixed HH&CYM generator and all Voice datas.
 * 2004 04-10 : Version 0.61 -- Added YMF281B tone (defined by Chabin).
 * <p>
 * References:
 *  fmopl.c        -- 1999,2000 written by Tatsuyuki Satoh (MAME development).
 *  fmopl.c(fixed) -- (C) 2002 Jarek Burczynski.
 *  s_opl.c        -- 2001 written by Mamiya (NEZplug development).
 *  Fmgen.cpp      -- 1999,2000 written by cisc.
 *  fmpac.ill      -- 2000 created by NARUTO.
 *  MSX-Datapack
 *  YMU757 data sheet
 *  YM2143 data sheet
 * <p>
 * Additions by Maxim:
 * - per-channel panning
 */
public class Ym2413 {

    /** Mask */
    private static int maskCh(int x) {
        return 1 << x;
    }

    private int MASK_HH = 1 << 9;
    private int MASK_CYM = 1 << 10;
    private int MASK_TOM = 1 << 11;
    private int MASK_SD = 1 << 12;
    private int MASK_BD = 1 << 13;
    //private int OPLL_MASK_RHYTHM = 0x1f << 9;//(OPLL_MASK_HH | OPLL_MASK_CYM | OPLL_MASK_TOM | OPLL_MASK_SD | OPLL_MASK_BD);

    private enum Tone {_2413, _VRC7, _281B}

    /** slot */
    private static class Slot {

        private static final int BD1 = 12;
        private static final int BD2 = 13;
        private static final int HH = 14;
        private static final int SD = 15;
        private static final int TOM = 16;
        private static final int CYM = 17;

        /**
         * Calc Parameters
         */
        private int calcEgDphase(int[][] dPhaseArTable, int[][] dPhaseDrTable) {

            switch (egMode) {
            case ATTACK:
                return dPhaseArTable[this.patch.ar][rks];

            case DECAY:
                return dPhaseDrTable[this.patch.dr][rks];

            case SUSHOLD:
                return 0;

            case SUSTAIN:
                return dPhaseDrTable[this.patch.rr][rks];

            case RELEASE:
                if (sustain != 0)
                    return dPhaseDrTable[5][rks];
                else if (this.patch.eg != 0)
                    return dPhaseDrTable[this.patch.rr][rks];
                else
                    return dPhaseDrTable[7][rks];

            case SETTLE:
                return dPhaseDrTable[15][0];

            case FINISH:
                return 0;

            default:
                return 0;
            }
        }

        /**
         * Initializing
         */
        private void reset(int type) {
            this.type = type;
            sinTbl = waveForm[0];
            phase = 0;
            dPhase = 0;
            output[0] = 0;
            output[1] = 0;
            feedback = 0;
            egMode = EgState.FINISH;
            egPhase = EG_DP_WIDTH;
            egDPhase = 0;
            rks = 0;
            tll = 0;
            sustain = 0;
            fNum = 0;
            block = 0;
            volume = 0;
            pgOut = 0;
            egOut = 0;
            this.patch = nullPatch;
        }

        /* PG */
        private void calcPhase(int lfo) {
            if (this.patch.pm != 0)
                phase += (dPhase * lfo) >> PM_AMP_BITS;
            else
                phase += dPhase;

            phase &= DP_WIDTH - 1;

            pgOut = highBits(phase, DP_BASE_BITS);
        }

        /**
         * CARRIOR
         */
        private int calcCar(int fm) {
            if (egOut >= (DB_MUTE - 1)) {
                //Debug.printf("calc_slot_car: output over");
                output[0] = 0;
            } else {
                //Debug.printf("calc_slot_car: slot.egout %d", slot.egout);
                output[0] = db2LinTable[sinTbl[(pgOut + wave2_8pi(fm)) & (PG_WIDTH - 1)] + egOut];
            }

            output[1] = (output[1] + output[0]) >> 1;
            return output[1];
        }

        /** MODULATOR */
        private int calcMod() {
            output[1] = output[0];

            if (egOut >= (DB_MUTE - 1)) {
                output[0] = 0;
            } else if (this.patch.fb != 0) {
                int fm = wave2_4pi(feedback) >> (7 - this.patch.fb);
                output[0] = db2LinTable[sinTbl[(pgOut + fm) & (PG_WIDTH - 1)] + egOut];
            } else {
                output[0] = db2LinTable[sinTbl[pgOut] + egOut];
            }

            feedback = (output[1] + output[0]) >> 1;

            return feedback;
        }

        /** TOM */
        private int calcTom() {
            if (egOut >= (DB_MUTE - 1))
                return 0;

            return db2LinTable[sinTbl[pgOut] + egOut];

        }

        /** SNARE */
        private int calcSnare(int noise) {
            if (egOut >= (DB_MUTE - 1))
                return 0;

            if (bit(pgOut, 7) != 0)
                return db2LinTable[(noise != 0 ? dbPos(0.0) : dbPos(15.0)) + egOut];
            else
                return db2LinTable[(noise != 0 ? dbNeg(0.0) : dbNeg(15.0)) + egOut];
        }

        /**
         * TOP-CYM
         */
        private int calcCym(int pgOutHh) {
            int dbOut;

            if (egOut >= (DB_MUTE - 1))
                return 0;
            else if ((
                    // the same as fmopl.c
                    ((bit(pgOutHh, PG_BITS - 8) ^ bit(pgOutHh, PG_BITS - 1)) | bit(pgOutHh, PG_BITS - 7)) ^
                            // different from fmopl.c
                            (bit(pgOut, PG_BITS - 7) & ~bit(pgOut, PG_BITS - 5))) != 0
            )
                dbOut = dbNeg(3.0);
            else
                dbOut = dbPos(3.0);

            return db2LinTable[dbOut + egOut];
        }

        /**
         * HI-HAT
         */
        private int calcHat(int pgOutCym, int noise) {
            int dbOut;

            if (egOut >= (DB_MUTE - 1))
                return 0;
            else if ((
                    // the same as fmopl.c
                    ((bit(pgOut, PG_BITS - 8) ^ bit(pgOut, PG_BITS - 1)) | bit(pgOut, PG_BITS - 7)) ^
                            // different from fmopl.c
                            (bit(pgOutCym, PG_BITS - 7) & ~bit(pgOutCym, PG_BITS - 5))) != 0
            ) {
                if (noise != 0)
                    dbOut = dbNeg(12.0);
                else
                    dbOut = dbNeg(24.0);
            } else {
                if (noise != 0)
                    dbOut = dbPos(12.0);
                else
                    dbOut = dbPos(24.0);
            }

            return db2LinTable[dbOut + egOut];
        }

        /**
         * EG
         */
        private void calcEnvelope(int lfo, Consumer<Slot> updateEg) {
            int egOut = switch (egMode) {
                case ATTACK -> {
                    int _egOut = arAdjustTable[highBits(egPhase, EG_DP_BITS - EG_BITS)];
                    egPhase += egDPhase;
                    if ((EG_DP_WIDTH & egPhase) != 0 || (this.patch.ar == 15)) {
                        _egOut = 0;
                        egPhase = 0;
                        egMode = EgState.DECAY;
                        updateEg.accept(this);
                    }
                    yield _egOut;
                }
                case DECAY -> {
                    int _egOut = highBits(egPhase, EG_DP_BITS - EG_BITS);
                    egPhase += egDPhase;
                    if (egPhase >= sl[this.patch.sl]) {
                        if ((this.patch.eg) != 0) {
                            egPhase = sl[this.patch.sl];
                            egMode = EgState.SUSHOLD;
                            updateEg.accept(this);
                        } else {
                            egPhase = sl[this.patch.sl];
                            egMode = EgState.SUSTAIN;
                            updateEg.accept(this);
                        }
                    }
                    yield _egOut;
                }
                case SUSHOLD -> {
                    int _egOut = highBits(egPhase, EG_DP_BITS - EG_BITS);
                    if (this.patch.eg == 0) {
                        egMode = EgState.SUSTAIN;
                        updateEg.accept(this);
                    }
                    yield _egOut;
                }
                case SUSTAIN, RELEASE -> {
                    int _egOut = highBits(egPhase, EG_DP_BITS - EG_BITS);
                    egPhase += egDPhase;
                    if (_egOut >= (1 << EG_BITS)) {
                        egMode = EgState.FINISH;
                        _egOut = (1 << EG_BITS) - 1;
                    }
                    yield _egOut;
                }
                case SETTLE -> {
                    int _egOut = highBits(egPhase, EG_DP_BITS - EG_BITS);
                    egPhase += egDPhase;
                    if (_egOut >= (1 << EG_BITS)) {
                        egMode = EgState.ATTACK;
                        _egOut = (1 << EG_BITS) - 1;
                        updateEg.accept(this);
                    }
                    yield _egOut;
                }
                case FINISH -> (1 << EG_BITS) - 1;
                default -> (1 << EG_BITS) - 1;
            };

            if (this.patch.am != 0)
                egOut = eg2db(egOut + tll) + lfo;
            else {
                egOut = eg2db(egOut + tll);
                //Debug.printf("egOut %d slot.tll %d (e_int32)(EG_STEP/DB_STEP) %d", egOut, slot.tll, (short)(EG_STEP / DB_STEP));
            }

            if (egOut >= DB_MUTE)
                egOut = DB_MUTE - 1;

            this.egOut = egOut | 3;
        }

        private void updateTll() {
            tll = (type == 0) ? tllTable[fNum >> 5][block][this.patch.tl][this.patch.kl] : tllTable[fNum >> 5][block][volume][this.patch.kl];
        }

        private void updateRks() {
            rks = rksTable[fNum >> 8][block][this.patch.kr];
        }

        private void updateWf() {
            sinTbl = waveForm[this.patch.wf];
        }

        /** Voice data */
        private static class Patch {
            private int tl, fb, eg, ml, ar, dr, sl, rr, kr, kl, am, pm, wf;

            private void copy(Slot.Patch other) {
                this.am = other.am;
                this.ar = other.ar;
                this.dr = other.dr;
                this.eg = other.eg;
                this.fb = other.fb;
                this.kl = other.kl;
                this.kr = other.kr;
                this.ml = other.ml;
                this.pm = other.pm;
                this.rr = other.rr;
                this.sl = other.sl;
                this.tl = other.tl;
                this.wf = other.wf;
            }
        }

        private Slot.Patch patch;

        /** 0 : modulator 1 : carrier */
        private int type;

        // OUTPUT

        private int feedback;
        /** Output value of slot */
        private int[] output = new int[2];

        // for Phase Generator (PG)

        /** Wavetable */
        private int[] sinTbl;
        /** Phase */
        private int phase;
        /** Phase increment amount */
        private int dPhase;
        /** output */
        private int pgOut;

        // for Envelope Generator (EG)

        /** F-Number */
        private int fNum;
        /** Bsynchronized */
        private int block;
        /** Current volume */
        private int volume;
        /** Sustain 1 = ON, 0 = OFF */
        private int sustain;
        /** Total Level + Key scale level */
        private int tll;
        /** Key scale offset (Rks) */
        private int rks;
        /** Current state */
        private EgState egMode;
        /** Phase */
        private int egPhase;
        /** Phase increment amount */
        private int egDPhase;
        /** output */
        private int egOut;
    }

    private byte vrc7Mode;
    private byte adr;
    private int out;

//#ifndef EMU2413_COMPACTION
    private int realStep;
    private int opllTime;
    private int opllStep;
    private int prev, next;
    private int[] sPrev = new int[2];
    private int[] sNext = new int[2];
    private float[][] pan = new float[][] {new float[2], new float[2], new float[2], new float[2], new float[2], new float[2], new float[2],
            new float[2], new float[2], new float[2], new float[2], new float[2], new float[2], new float[2]};
//#endif

    // Register
    private byte[] reg = new byte[0x40];
    private int[] slotOnFlag = new int[18];

    // Pitch Modulator
    private int pmPhase;
    private int lfoPm;

    // Amp Modulator
    private int amPhase;
    private int lfoAm;

    private int quality;

    /** Noise Generator */
    private int noiseSeed;

    // Channel data
    private int[] patchNumber = new int[9];
    private int[] keyStatus = new int[9];

    /** Slot */
    private Slot[] slot = new Slot[18];

    /** Voice data */
    private Slot.Patch[][] patch = new Slot.Patch[][] {
            new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2]
            , new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2]
            , new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2]
            , new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2]
            , new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2]
    };
    /** flag for check patch update */
    private int[] patch_update = new int[2];

    private int mask;

    /** Note: Dump size changed to 8 per instrument, since 9-15 were unused. -VB */
    private static final int OPLL_TONE_NUM = 1;
    private static final byte[][] default_inst = new byte[][] {
            new byte[] {
                    // YM2413 tone by okazaki@angel.ne.jp
                    0x49, 0x4c, 0x4c, 0x32, 0x00, 0x00, 0x00, 0x00,
                    0x61, 0x61, 0x1e, 0x17, (byte) 0xf0, 0x7f, 0x00, 0x17,
                    0x13, 0x41, 0x16, 0x0e, (byte) 0xfd, (byte) 0xf4, 0x23, 0x23,
                    0x03, 0x01, (byte) 0x9a, 0x04, (byte) 0xf3, (byte) 0xf3, 0x13, (byte) 0xf3,
                    0x11, 0x61, 0x0e, 0x07, (byte) 0xfa, 0x64, 0x70, 0x17,
                    0x22, 0x21, 0x1e, 0x06, (byte) 0xf0, 0x76, 0x00, 0x28,
                    0x21, 0x22, 0x16, 0x05, (byte) 0xf0, 0x71, 0x00, 0x18,
                    0x21, 0x61, 0x1d, 0x07, (byte) 0x82, (byte) 0x80, 0x17, 0x17,
                    0x23, 0x21, 0x2d, 0x16, (byte) 0x90, (byte) 0x90, 0x00, 0x07,
                    0x21, 0x21, 0x1b, 0x06, 0x64, 0x65, 0x10, 0x17,
                    0x21, 0x21, 0x0b, 0x1a, (byte) 0x85, (byte) 0xa0, 0x70, 0x07,
                    0x23, 0x01, (byte) 0x83, 0x10, (byte) 0xff, (byte) 0xb4, 0x10, (byte) 0xf4,
                    (byte) 0x97, (byte) 0xc1, 0x20, 0x07, (byte) 0xff, (byte) 0xf4, 0x22, 0x22,
                    0x61, 0x00, 0x0c, 0x05, (byte) 0xc2, (byte) 0xf6, 0x40, 0x44,
                    0x01, 0x01, 0x56, 0x03, (byte) 0x94, (byte) (byte) 0xc2, 0x03, 0x12,
                    0x21, 0x01, (byte) 0x89, 0x03, (byte) 0xf1, (byte) 0xe4, (byte) 0xf0, 0x23,
                    0x07, 0x21, 0x14, 0x00, (byte) 0xee, (byte) 0xf8, (byte) 0xff, (byte) 0xf8,
                    0x01, 0x31, 0x00, 0x00, (byte) 0xf8, (byte) 0xf7, (byte) 0xf8, (byte) 0xf7,
                    0x25, 0x11, 0x00, 0x00, (byte) 0xf8, (byte) 0xfa, (byte) 0xf8, 0x55,
            }
    };

    // Size of Sintable ( 8 -- 18 can be used. 9 recommended.)
    private static final int PG_BITS = 9;
    private static final int PG_WIDTH = (1 << 9);

    // Phase increment counter
    private static final int DP_BITS = 18;
    private static final int DP_WIDTH = (1 << 18);
    private static final int DP_BASE_BITS = 18 - 9;

    // Dynamic range (Accuracy of sin table)
    private static final double DB_STEP = 48.0 / (1 << 8);
    private static final int DB_MUTE = 1 << 8;

    // Dynamic range of envelope
    private static final double EG_STEP = 0.375;
    private static final int EG_BITS = 7;

    /** Dynamic range of total level */
    private static final double TL_STEP = 0.75;

    /** Dynamic range of sustine level */
    private static final double SL_STEP = 3.0;

    private static int eg2db(int d) {
        return d * (int) (EG_STEP / DB_STEP);
    }

    private static int tl2eg(int d) {
        return d * (int) (TL_STEP / EG_STEP);
    }

    private static int sl2eg(int d) {
        return d * (int) (SL_STEP / EG_STEP);
    }

    private static int dbPos(double x) {
        return (int) (x / DB_STEP);
    }

    private static int dbNeg(double x) {
        return (int) (DB_MUTE + DB_MUTE + x / DB_STEP);
    }

    /* Bits for liner value */
    private static final int DB2LIN_AMP_BITS = 8;
    private static final int SLOT_AMP_BITS = 8;

    /* Bits for envelope phase incremental counter */
    private static final int EG_DP_BITS = 22;
    private static final int EG_DP_WIDTH = 1 << 22;

    /* Bits for Pitch and Amp modulator */
    private static final int PM_PG_BITS = 8;
    private static final int PM_PG_WIDTH = (1 << 8);
    private static final int PM_DP_BITS = 16;
    private static final int PM_DP_WIDTH = (1 << 16);
    private static final int AM_PG_BITS = 8;
    private static final int AM_PG_WIDTH = (1 << 8);
    private static final int AM_DP_BITS = 16;
    private static final int AM_DP_WIDTH = (1 << 16);

    // PM table is calculated by PM_AMP * pow(2 , PM_DEPTH * sin(x) / 1200)
    private static final int PM_AMP_BITS = 8;
    private static final int PM_AMP = 1 << 8;

    // PM speed(Hz) and depth(cent)
    private static final double PM_SPEED = 6.4;
    private static final double PM_DEPTH = 13.75;

    // AM speed(Hz) and depth(dB)
    private static final double AM_SPEED = 3.6413;
    private static final double AM_DEPTH = 4.875;

    /** Cut the lower b bit(s) off. */
    private static int highBits(int c, int b) {
        return c >> b;
    }

    /** Leave the lower b bit(s). */
    private static int lowBits(int c, int b) {
        return c & ((1 << b) - 1);
    }

    /** Expand x which is s bits to d bits. */
    private static int expandBits(int x, int s, int d) {
        return x << (d - s);
    }

    /** Expand x which is s bits to d bits and fill expanded bits '1' */
    private static int expandBitsX(int x, int s, int d) {
        return (x << (d - s)) | ((1 << (d - s)) - 1);
    }

    /** Adjust envelope speed which depends on sampling rate. */
    private int adjustRate(int x) {
        return rate == 49716 ? x : (int) ((double) x * clock / 72 / rate + 0.5); //  added 0.5 to round the value
    }

    private Slot mod(int x) {
        return (this.slot[x << 1]);
    }

    private Slot car(int x) {
        return this.slot[(x << 1) | 1];
    }

    private static int bit(int s, int b) {
        return ((s >> b) & 1);
    }

    /** Input clock */
    private int clock = 844451141;
    /** Sampling rate */
    private int rate = 3354932;

    // WaveTable for each envelope amp
    private static int[] fullSinTable = new int[PG_WIDTH];
    private static int[] halfSinTable = new int[PG_WIDTH];

    private static int[][] waveForm = new int[2][];

    // LFO Table
    private static int[] pmTable = new int[PM_PG_WIDTH];
    private static int[] amTable = new int[AM_PG_WIDTH];

    // Phase delta for LFO
    private int pmDPhase;
    private int amDPhase;

    /** dB to Liner table */
    private static short[] db2LinTable = new short[(DB_MUTE + DB_MUTE) * 2];

    /** Liner to Log curve conversion table (for Attack rate). */
    private static int[] arAdjustTable = new int[1 << EG_BITS];

    /** Empty Voice data */
    private static final Slot.Patch nullPatch = new Slot.Patch();

    /** Basic Voice data */
    private Slot.Patch[][] defaultPatch;

    /** Definition of envelope mode */
    private enum EgState {
        READY, ATTACK, DECAY, SUSHOLD, SUSTAIN, RELEASE, SETTLE, FINISH
    }

    /** Phase incr table for Attack */
    private int[][] dPhaseArTable = new int[][] {
            new int[16], new int[16], new int[16], new int[16],
            new int[16], new int[16], new int[16], new int[16],
            new int[16], new int[16], new int[16], new int[16],
            new int[16], new int[16], new int[16], new int[16]};
    /** Phase incr table for Decay and Release */
    private int[][] dPhaseDrTable = new int[][] {
            new int[16], new int[16], new int[16], new int[16],
            new int[16], new int[16], new int[16], new int[16],
            new int[16], new int[16], new int[16], new int[16],
            new int[16], new int[16], new int[16], new int[16]};

    /** KSL + TL Table */
    private static int[][][][] tllTable;
    private static int[][][] rksTable;

    /** Phase incr table for PG */
    private int[][][] dPhaseTable;

    /* Table for AR to LogCurve. */
    static {
        arAdjustTable[0] = (1 << EG_BITS) - 1;
        for (int i = 1; i < (1 << EG_BITS); i++)
            arAdjustTable[i] = (int) ((double) (1 << EG_BITS) - 1 - ((1 << EG_BITS) - 1) * Math.log(i) / Math.log(127));
    }

    /* Table for dB(0 -- (1<<DB_BITS)-1) to Liner(0 -- DB2LIN_AMP_WIDTH) */
    static {
        for (int i = 0; i < DB_MUTE + DB_MUTE; i++) {
            db2LinTable[i] = (short) ((double) ((1 << DB2LIN_AMP_BITS) - 1) * Math.pow(10, -(double) i * DB_STEP / 20));
            if (i >= DB_MUTE) db2LinTable[i] = 0;
            db2LinTable[i + DB_MUTE + DB_MUTE] = (short) (-db2LinTable[i]);
        }
    }

    /** Liner(+0.0 - +1.0) to dB((1<<DB_BITS) - 1 -- 0) */
    private static int lin2db(double d) {
        if (d == 0)
            return (DB_MUTE - 1);
        else {
            // 0 -- 127
            int i = -(int) (20.0 * Math.log10(d) / DB_STEP);
            return Math.min(i, DB_MUTE - 1);
        }
    }

    /* Sin Table */
    static {
        for (int i = 0; i < PG_WIDTH / 4; i++) {
            fullSinTable[i] = lin2db(Math.sin(2.0 * Math.PI * i / PG_WIDTH));
        }

        for (int i = 0; i < PG_WIDTH / 4; i++) {
            fullSinTable[PG_WIDTH / 2 - 1 - i] = fullSinTable[i];
        }

        for (int i = 0; i < PG_WIDTH / 2; i++) {
            fullSinTable[PG_WIDTH / 2 + i] = DB_MUTE + DB_MUTE + fullSinTable[i];
        }

        System.arraycopy(fullSinTable, 0, halfSinTable, 0, PG_WIDTH / 2);
        for (int i = PG_WIDTH / 2; i < PG_WIDTH; i++)
            halfSinTable[i] = fullSinTable[0];

        waveForm[0] = fullSinTable;
        waveForm[1] = halfSinTable;
    }

    private static double saw(double phase) {
        if (phase <= Math.PI / 2)
            return phase * 2 / Math.PI;
        else if (phase <= Math.PI * 3 / 2)
            return 2.0 - (phase * 2 / Math.PI);
        else
            return -4.0 + phase * 2 / Math.PI;
    }

    /* Table for Pitch Modulator */
    static {
        for (int i = 0; i < PM_PG_WIDTH; i++)
            pmTable[i] = (int) ((double) PM_AMP * Math.pow(2, PM_DEPTH * saw(2.0 * Math.PI * i / PM_PG_WIDTH) / 1200));
    }

    /* Table for Amp Modulator */
    static {
        for (int i = 0; i < AM_PG_WIDTH; i++)
            amTable[i] = (int) (AM_DEPTH / 2 / DB_STEP * (1.0 + saw(2.0 * Math.PI * i / PM_PG_WIDTH)));
    }

    /** Phase increment counter table */
    private void makeDPhaseTable() {
        int[] mlTable = new int[] {
                1, 1 * 2, 2 * 2, 3 * 2, 4 * 2, 5 * 2, 6 * 2, 7 * 2, 8 * 2,
                9 * 2, 10 * 2, 10 * 2, 12 * 2, 12 * 2, 15 * 2, 15 * 2
        };

        dPhaseTable = new int[512][][];
        for (int fNum = 0; fNum < 512; fNum++) {
            dPhaseTable[fNum] = new int[8][];
            for (int block = 0; block < 8; block++) {
                dPhaseTable[fNum][block] = new int[16];
                for (int ml = 0; ml < 16; ml++) {
                    int x = fNum * mlTable[ml];
                    x = x << block;
                    x = x >> (20 - DP_BITS);
                    dPhaseTable[fNum][block][ml] = (rate == 49716 ? x : (int) ((double) (x) * clock / 72 / rate + 0.5));
                }
            }
        }
    }

    static {

        double[] klTable = new double[] {
                2 * 0.000, 2 * 9.000, 2 * 12.000, 2 * 13.875
                , 2 * 15.000, 2 * 16.125, 2 * 16.875, 2 * 17.625
                , 2 * 18.000, 2 * 18.750, 2 * 19.125, 2 * 19.500
                , 2 * 19.875, 2 * 20.250, 2 * 20.625, 2 * 21.000
        };

        tllTable = new int[16][][][];
        for (int fNum = 0; fNum < 16; fNum++) {
            tllTable[fNum] = new int[8][][];
            for (int block = 0; block < 8; block++) {
                tllTable[fNum][block] = new int[64][];
                for (int tl = 0; tl < 64; tl++) {
                    tllTable[fNum][block][tl] = new int[4];
                    for (int kl = 0; kl < 4; kl++) {
                        if (kl == 0) {
                            tllTable[fNum][block][tl][kl] = tl * (int) (TL_STEP / EG_STEP);
                        } else {
                            int tmp = (int) (klTable[fNum] - 2 * 3.000 * (7 - block));
                            if (tmp <= 0)
                                tllTable[fNum][block][tl][kl] = tl * (int) (TL_STEP / EG_STEP);
                            else
                                tllTable[fNum][block][tl][kl] = (int) (((tmp >> (3 - kl)) / EG_STEP) + (tl * (int) (TL_STEP / EG_STEP)));
                        }
                    }
                }
            }
        }
    }

//# ifdef USE_SPEC_ENV_SPEED
    private static final double[][] attackTime = new double[][] {
            new double[] {0, 0, 0, 0},
            new double[] {1730.15, 1400.60, 1153.43, 988.66},
            new double[] {865.08, 700.30, 576.72, 494.33},
            new double[] {432.54, 350.15, 288.36, 247.16},
            new double[] {216.27, 175.07, 144.18, 123.58},
            new double[] {108.13, 87.54, 72.09, 61.79},
            new double[] {54.07, 43.77, 36.04, 30.90},
            new double[] {27.03, 21.88, 18.02, 15.45},
            new double[] {13.52, 10.94, 9.01, 7.72},
            new double[] {6.76, 5.47, 4.51, 3.86},
            new double[] {3.38, 2.74, 2.25, 1.93},
            new double[] {1.69, 1.37, 1.13, 0.97},
            new double[] {0.84, 0.70, 0.60, 0.54},
            new double[] {0.50, 0.42, 0.34, 0.30},
            new double[] {0.28, 0.22, 0.18, 0.14},
            new double[] {0.00, 0.00, 0.00, 0.00}
    };

    private static final double[][] decayTime = new double[][] {
            new double[] {0, 0, 0, 0},
            new double[] {20926.60, 16807.20, 14006.00, 12028.60},
            new double[] {10463.30, 8403.58, 7002.98, 6014.32},
            new double[] {5231.64, 4201.79, 3501.49, 3007.16},
            new double[] {2615.82, 2100.89, 1750.75, 1503.58},
            new double[] {1307.91, 1050.45, 875.37, 751.79},
            new double[] {653.95, 525.22, 437.69, 375.90},
            new double[] {326.98, 262.61, 218.84, 187.95},
            new double[] {163.49, 131.31, 109.42, 93.97},
            new double[] {81.74, 65.65, 54.71, 46.99},
            new double[] {40.87, 32.83, 27.36, 23.49},
            new double[] {20.44, 16.41, 13.68, 11.75},
            new double[] {10.22, 8.21, 6.84, 5.87},
            new double[] {5.11, 4.10, 3.42, 2.94},
            new double[] {2.55, 2.05, 1.71, 1.47},
            new double[] {1.27, 1.27, 1.27, 1.27}
    };
//#endif

    /* Rate Table for Attack */
    private void makeDPhaseArTable() {

//# ifdef USE_SPEC_ENV_SPEED
        int[][] attackTable = new int[][] {
                new int[4], new int[4], new int[4], new int[4],
                new int[4], new int[4], new int[4], new int[4],
                new int[4], new int[4], new int[4], new int[4],
                new int[4], new int[4], new int[4], new int[4]
        };

        for (int rm = 0; rm < 16; rm++)
            for (int rl = 0; rl < 4; rl++) {
                if (rm == 0)
                    attackTable[rm][rl] = 0;
                else if (rm == 15)
                    attackTable[rm][rl] = EG_DP_WIDTH;
                else
                    attackTable[rm][rl] = (int) ((double) (1 << EG_DP_BITS) / (attackTime[rm][rl] * 3579545 / 72000));

            }
//#endif

        for (int ar = 0; ar < 16; ar++)
            for (int rks = 0; rks < 16; rks++) {
                int rm = ar + (rks >> 2);
                int rl = rks & 3;
                if (rm > 15)
                    rm = 15;
                switch (ar) {
                case 0:
                    dPhaseArTable[ar][rks] = 0;
                    break;
                case 15:
                    dPhaseArTable[ar][rks] = 0;
                    break;
                default:
                    dPhaseArTable[ar][rks] = (rate == 49716 ? attackTable[rm][rl] : (int) ((double) (attackTable[rm][rl]) * clock / 72 / rate + 0.5));
                    break;
                }
            }
    }

    /** Rate Table for Decay and Release */
    private void makeDPhaseDrTable() {
//# ifdef USE_SPEC_ENV_SPEED
        int[][] decaytable = new int[][] {
                new int[4], new int[4], new int[4], new int[4],
                new int[4], new int[4], new int[4], new int[4],
                new int[4], new int[4], new int[4], new int[4],
                new int[4], new int[4], new int[4], new int[4]
        };

        for (int rm = 0; rm < 16; rm++)
            for (int rl = 0; rl < 4; rl++)
                if (rm == 0)
                    decaytable[rm][rl] = 0;
                else
                    decaytable[rm][rl] = (int) ((double) (1 << EG_DP_BITS) / (decayTime[rm][rl] * 3579545 / 72000));
//#endif

        for (int dr = 0; dr < 16; dr++)
            for (int rks = 0; rks < 16; rks++) {
                int rm = dr + (rks >> 2);
                int rl = rks & 3;
                if (rm > 15)
                    rm = 15;
                switch (dr) {
                case 0:
                    dPhaseDrTable[dr][rks] = 0;
                    break;
                default:
                    dPhaseDrTable[dr][rks] = (rate == 49716 ? decaytable[rm][rl] : (int) ((double) (decaytable[rm][rl]) * clock / 72 / rate + 0.5));
                    break;
                }
            }
    }

    static {
        rksTable = new int[2][][];
        for (int fNum8 = 0; fNum8 < 2; fNum8++) {
            rksTable[fNum8] = new int[8][];
            for (int block = 0; block < 8; block++) {
                rksTable[fNum8][block] = new int[2];
                for (int kr = 0; kr < 2; kr++) {
                    if (kr != 0)
                        rksTable[fNum8][block][kr] = (block << 1) + fNum8;
                    else
                        rksTable[fNum8][block][kr] = block >> 1;
                }
            }
        }
    }

    private void dump2patch(byte[] dump, int startAdr, Slot.Patch[][] patch) {
        patch[startAdr][0].am = (dump[startAdr * 8 + 0] >> 7) & 1;
        patch[startAdr][1].am = (dump[startAdr * 8 + 1] >> 7) & 1;
        patch[startAdr][0].pm = (dump[startAdr * 8 + 0] >> 6) & 1;
        patch[startAdr][1].pm = (dump[startAdr * 8 + 1] >> 6) & 1;
        patch[startAdr][0].eg = (dump[startAdr * 8 + 0] >> 5) & 1;
        patch[startAdr][1].eg = (dump[startAdr * 8 + 1] >> 5) & 1;
        patch[startAdr][0].kr = (dump[startAdr * 8 + 0] >> 4) & 1;
        patch[startAdr][1].kr = (dump[startAdr * 8 + 1] >> 4) & 1;
        patch[startAdr][0].ml = (dump[startAdr * 8 + 0]) & 15;
        patch[startAdr][1].ml = (dump[startAdr * 8 + 1]) & 15;
        patch[startAdr][0].kl = (dump[startAdr * 8 + 2] >> 6) & 3;
        patch[startAdr][1].kl = (dump[startAdr * 8 + 3] >> 6) & 3;
        patch[startAdr][0].tl = (dump[startAdr * 8 + 2]) & 63;
        patch[startAdr][0].fb = (dump[startAdr * 8 + 3]) & 7;
        patch[startAdr][0].wf = (dump[startAdr * 8 + 3] >> 3) & 1;
        patch[startAdr][1].wf = (dump[startAdr * 8 + 3] >> 4) & 1;
        patch[startAdr][0].ar = (dump[startAdr * 8 + 4] >> 4) & 15;
        patch[startAdr][1].ar = (dump[startAdr * 8 + 5] >> 4) & 15;
        patch[startAdr][0].dr = (dump[startAdr * 8 + 4]) & 15;
        patch[startAdr][1].dr = (dump[startAdr * 8 + 5]) & 15;
        patch[startAdr][0].sl = (dump[startAdr * 8 + 6] >> 4) & 15;
        patch[startAdr][1].sl = (dump[startAdr * 8 + 7] >> 4) & 15;
        patch[startAdr][0].rr = (dump[startAdr * 8 + 6]) & 15;
        patch[startAdr][1].rr = (dump[startAdr * 8 + 7]) & 15;
    }

    private void getDefaultPatch(int num, Slot.Patch[][] patch) {
        dump2patch(default_inst[0], num, patch);
    }

    private void makeDefaultPatch() {
        for (int j = 0; j < 19; j++)
            getDefaultPatch(j, defaultPatch);
    }

    private void setPatch(byte[] dump) {
        Slot.Patch[][] patch = new Slot.Patch[2][];

        for (int i = 0; i < 19; i++) {
            dump2patch(dump, i, patch);
            this.patch[0][i] = patch[0][i];
            this.patch[1][i] = patch[1][i];
        }
    }

    private void patch2dump(Slot.Patch[] patch, byte[] dump) {
        dump[0] = (byte) ((patch[0].am << 7) + (patch[0].pm << 6) + (patch[0].eg << 5) + (patch[0].kr << 4) + patch[0].ml);
        dump[1] = (byte) ((patch[1].am << 7) + (patch[1].pm << 6) + (patch[1].eg << 5) + (patch[1].kr << 4) + patch[1].ml);
        dump[2] = (byte) ((patch[0].kl << 6) + patch[0].tl);
        dump[3] = (byte) ((patch[1].kl << 6) + (patch[1].wf << 4) + (patch[0].wf << 3) + patch[0].fb);
        dump[4] = (byte) ((patch[0].ar << 4) + patch[0].dr);
        dump[5] = (byte) ((patch[1].ar << 4) + patch[1].dr);
        dump[6] = (byte) ((patch[0].sl << 4) + patch[0].rr);
        dump[7] = (byte) ((patch[1].sl << 4) + patch[1].rr);
        dump[8] = 0;
        dump[9] = 0;
        dump[10] = 0;
        dump[11] = 0;
        dump[12] = 0;
        dump[13] = 0;
        dump[14] = 0;
        dump[15] = 0;
    }

    // Ym2413 internal interfaces

    private void updatePg(Slot slot) {
        slot.dPhase = dPhaseTable[slot.fNum][slot.block][slot.patch.ml];
    }

    private void updateEg(Slot slot) {
        slot.egDPhase = slot.calcEgDphase(this.dPhaseArTable, this.dPhaseDrTable);
    }

    private void updateAll(Slot slot) {
        updatePg(slot);
        slot.updateTll();
        slot.updateRks();
        slot.updateWf();
        updateEg(slot); // EG should be updated last.
    }

    /** Slot key on */
    private void slotOn(Slot slot) {
        slot.egMode = EgState.ATTACK;
        slot.egPhase = 0;
        slot.phase = 0;
        updateEg(slot);
    }

    /** Slot key on without reseting the phase */
    private void slotOn2(Slot slot) {
        slot.egMode = EgState.ATTACK;
        slot.egPhase = 0;
        updateEg(slot);
    }

    /** Slot key off */
    private void slotOff(Slot slot) {
        if (slot.egMode == EgState.ATTACK)
            slot.egPhase = expandBits(arAdjustTable[highBits(slot.egPhase, EG_DP_BITS - EG_BITS)], EG_BITS, EG_DP_BITS);
        slot.egMode = EgState.RELEASE;
        updateEg(slot);
    }

    /** Channel key on */
    private void keyOn(int i) {
        if (this.slotOnFlag[i * 2] == 0)
            slotOn(mod(i));
        if (this.slotOnFlag[i * 2 + 1] == 0)
            slotOn(car(i));
        this.keyStatus[i] = 1;
    }

    /** Channel key off */
    private void keyOff(int i) {
        if (this.slotOnFlag[i * 2 + 1] != 0)
            slotOff(car(i));
        this.keyStatus[i] = 0;
    }

    private void keyOnBD() {
        keyOn(6);
    }

    private void keyOnSD() {
        if (this.slotOnFlag[Slot.SD] == 0)
            slotOn(car(7));
    }

    private void keyOnTOM() {
        if (this.slotOnFlag[Slot.TOM] == 0)
            slotOn(mod(8));
    }

    private void keyOn_HH() {
        if (this.slotOnFlag[Slot.HH] == 0)
            slotOn2(mod(7));
    }

    private void keyOnCYM() {
        if (this.slotOnFlag[Slot.CYM] == 0)
            slotOn2(car(8));
    }

    /* Drum key off */
    private void keyOffBD() {
        keyOff(6);
    }

    private void keyOffSD() {
        if (this.slotOnFlag[Slot.SD] != 0)
            slotOff(car(7));
    }

    private void keyOffTOM() {
        if (this.slotOnFlag[Slot.TOM] != 0)
            slotOff(mod(8));
    }

    private void keyOffHH() {
        if (this.slotOnFlag[Slot.HH] != 0)
            slotOff(mod(7));
    }

    private void keyOffCYM() {
        if (this.slotOnFlag[Slot.CYM] != 0)
            slotOff(car(8));
    }

    /** Change a voice */
    private void setPatch(int i, int num) {
        this.patchNumber[i] = num;
        mod(i).patch = this.patch[num][0];
        car(i).patch = this.patch[num][1];
    }

    /** Change a rhythm Voice */
    private void setSlotPatch(Slot slot, Slot.Patch patch) {
        slot.patch = patch;
    }

    /** Set sustain parameter */
    private void setSustain(int c, int sustain) {
        car(c).sustain = sustain;
        if (mod(c).type != 0)
            mod(c).sustain = sustain;
    }

    /** Volume: 6bit (volume register << 2) */
    private void setVolume(int c, int volume) {
        car(c).volume = volume;
    }

    private void setSlotVolume(Slot slot, int volume) {
        slot.volume = volume;
    }

    /** Set F-Number (fNum: 9bit) */
    private void setFnumber(int c, int fNum) {
        car(c).fNum = fNum;
        mod(c).fNum = fNum;
    }

    /** Set B synchronized data (block: 3bit) */
    private void setBlock(int c, int block) {
        car(c).block = block;
        mod(c).block = block;
    }

    /** Change rhythm mode */
    private void updateRhythmMode() {
        if ((this.patchNumber[6] & 0x10) != 0) {
            if ((this.slotOnFlag[Slot.BD2] | (this.reg[0x0e] & 0x20)) == 0) {
                this.slot[Slot.BD1].egMode = EgState.FINISH;
                this.slot[Slot.BD2].egMode = EgState.FINISH;
                setPatch(6, this.reg[0x36] >> 4);
            }
        } else if ((this.reg[0x0e] & 0x20) != 0) {
            this.patchNumber[6] = 16;
            this.slot[Slot.BD1].egMode = EgState.FINISH;
            this.slot[Slot.BD2].egMode = EgState.FINISH;
            setSlotPatch(this.slot[Slot.BD1], this.patch[16][0]);
            setSlotPatch(this.slot[Slot.BD2], this.patch[16][1]);
        }

        if ((this.patchNumber[7] & 0x10) != 0) {
            if (!((this.slotOnFlag[Slot.HH] != 0 && this.slotOnFlag[Slot.SD] != 0) || (this.reg[0x0e] & 0x20) != 0)) {
                this.slot[Slot.HH].type = 0;
                this.slot[Slot.HH].egMode = EgState.FINISH;
                this.slot[Slot.SD].egMode = EgState.FINISH;
                setPatch(7, this.reg[0x37] >> 4);
            }
        } else if ((this.reg[0x0e] & 0x20) != 0) {
            this.patchNumber[7] = 17;
            this.slot[Slot.HH].type = 1;
            this.slot[Slot.HH].egMode = EgState.FINISH;
            this.slot[Slot.SD].egMode = EgState.FINISH;
            setSlotPatch(this.slot[Slot.HH], this.patch[17][0]);
            setSlotPatch(this.slot[Slot.SD], this.patch[17][1]);
        }

        if ((this.patchNumber[8] & 0x10) != 0) {
            if (!((this.slotOnFlag[Slot.CYM] != 0 && this.slotOnFlag[Slot.TOM] != 0) || (this.reg[0x0e] & 0x20) != 0)) {
                this.slot[Slot.TOM].type = 0;
                this.slot[Slot.TOM].egMode = EgState.FINISH;
                this.slot[Slot.CYM].egMode = EgState.FINISH;
                setPatch(8, this.reg[0x38] >> 4);
            }
        } else if ((this.reg[0x0e] & 0x20) != 0) {
            this.patchNumber[8] = 18;
            this.slot[Slot.TOM].type = 1;
            this.slot[Slot.TOM].egMode = EgState.FINISH;
            this.slot[Slot.CYM].egMode = EgState.FINISH;
            setSlotPatch(this.slot[Slot.TOM], this.patch[18][0]);
            setSlotPatch(this.slot[Slot.CYM], this.patch[18][1]);
        }
    }

    private void updateKeyStatus() {
        for (int ch = 0; ch < 9; ch++)
            this.slotOnFlag[ch * 2] = this.slotOnFlag[ch * 2 + 1] = (this.reg[0x20 + ch]) & 0x10;

        if ((this.reg[0x0e] & 0x20) != 0) {
            this.slotOnFlag[Slot.BD1] |= (this.reg[0x0e] & 0x10);
            this.slotOnFlag[Slot.BD2] |= (this.reg[0x0e] & 0x10);
            this.slotOnFlag[Slot.SD] |= (this.reg[0x0e] & 0x08);
            this.slotOnFlag[Slot.HH] |= (this.reg[0x0e] & 0x01);
            this.slotOnFlag[Slot.TOM] |= (this.reg[0x0e] & 0x04);
            this.slotOnFlag[Slot.CYM] |= (this.reg[0x0e] & 0x02);
        }
    }

    private void copyPatch(int num, Slot.Patch[] patch) {
        this.patch[num][0].copy(patch[0]);
        this.patch[num][1].copy(patch[1]);
    }

    private void internalRefresh() {
        makeDPhaseTable();
        makeDPhaseArTable();
        makeDPhaseDrTable();
        pmDPhase = adjustRate((int) (PM_SPEED * PM_DP_WIDTH / (clock / 72.)));
        amDPhase = adjustRate((int) (AM_SPEED * AM_DP_WIDTH / (clock / 72.)));
    }

    private void makeTables(int c, int r) {
        if (c != clock) {
            clock = c;
            makeDefaultPatch();
        }

        if (r != rate) {
            rate = r;
            internalRefresh();
        }
    }

    /** */
    public Ym2413(int clock, int samplingRate, byte[] patch) {

        makeTables(clock, samplingRate);

        defaultPatch = new Slot.Patch[19][];
        for (int i = 0; i < 19; i++) {
            defaultPatch[i] = new Slot.Patch[2];
            for (int j = 0; j < 2; j++) {
                defaultPatch[i][j] = new Slot.Patch();
            }
        }

        this.vrc7Mode = 0x00;

        for (int i = 0; i < 19; i++) {
            this.patch[i][0] = new Slot.Patch();
            this.patch[i][1] = new Slot.Patch();
        }

        for (int i = 0; i < 14; i++)
            centrePanning(this.pan[i]);

        this.mask = 0;

        reset();
        resetPatch(0);

        if (patch != null ) {
            setPatch(patch);
        }
    }

    /** Reset patch datas by system default. */
    private void resetPatch(int type) {
        for (int i = 0; i < 19; i++) {
            copyPatch(i, defaultPatch[i]);
        }
    }

    /** Reset whole of Ym2413 except patch datas. */
    public void reset() {
        this.adr = 0;
        this.out = 0;

        this.pmPhase = 0;
        this.amPhase = 0;

        this.noiseSeed = 0xffff;
        //this.mask = 0;

        for (int i = 0; i < 18; i++) {
            this.slot[i] = new Slot();
            this.slot[i].reset(i % 2);
        }

        for (int i = 0; i < 9; i++) {
            this.keyStatus[i] = 0;
            setPatch(i, 0);
        }

        for (int i = 0; i < 0x40; i++)
            writeReg(i, 0);

//# ifndef EMU2413_COMPACTION
        this.realStep = (1 << 31) / rate;
        this.opllStep = (1 << 31) / (clock / 72);
        this.opllTime = 0;
//            for (int i = 0; i < 14; i++) {
//                centre_panning(this.pan[i]);
//                this.pan[i][0] = 1.0f;
//                this.pan[i][1] = 1.0f;
//            }
        this.sPrev[0] = this.sPrev[1] = 0;
        this.sNext[0] = this.sNext[1] = 0;
//#endif
    }

    /** Force Refresh (When external program changes some parameters). */
    private void forceRefresh() {
        for (int i = 0; i < 9; i++)
            setPatch(i, this.patchNumber[i]);

        for (int i = 0; i < 18; i++) {
            updatePg(this.slot[i]);
            this.slot[i].updateRks();
            this.slot[i].updateTll();
            this.slot[i].updateWf();
            updateEg(this.slot[i]);
        }
    }

    public void setRate(int r) {
        if (this.quality != 0)
            rate = 49716;
        else
            rate = r;
        internalRefresh();
        rate = r;
    }

    public void setQuality(int q) {
        this.quality = q;
        setRate(rate);
    }

    // Generate wave data

    /** Convert Amp(0 to EG_HEIGHT) to Phase(0 to 2PI). */
    private static int wave2_2pi(int e) {
        return e << (PG_BITS - SLOT_AMP_BITS);
    }

    /** Convert Amp(0 to EG_HEIGHT) to Phase(0 to 4PI). */
    private static int wave2_4pi(int e) {
        return e << (1 + PG_BITS - SLOT_AMP_BITS);
    }

    /** Convert Amp(0 to EG_HEIGHT) to Phase(0 to 8PI). */
    private static int wave2_8pi(int e) {
        return e << (2 + PG_BITS - SLOT_AMP_BITS);
    }

    /** Update AM, PM unit */
    private void updateAmPm() {
        this.pmPhase = (this.pmPhase + pmDPhase) & (PM_DP_WIDTH - 1);
        this.amPhase = (this.amPhase + amDPhase) & (AM_DP_WIDTH - 1);
        this.lfoAm = amTable[highBits(this.amPhase, AM_DP_BITS - AM_PG_BITS)];
        this.lfoPm = pmTable[highBits(this.pmPhase, PM_DP_BITS - PM_PG_BITS)];
    }

    /** Update Noise unit */
    private void updateNoise() {
        if ((this.noiseSeed & 1) != 0) this.noiseSeed ^= 0x8003020;
        this.noiseSeed >>= 1;
    }

    private static int s2e(double x) {
        return sl2eg((int) (x / SL_STEP)) << (EG_DP_BITS - EG_BITS);
    }

    private static final int[] sl = new int[] {
            s2e(0.0), s2e(3.0), s2e(6.0), s2e(9.0),
            s2e(12.0), s2e(15.0), s2e(18.0), s2e(21.0),
            s2e(24.0), s2e(27.0), s2e(30.0), s2e(33.0),
            s2e(36.0), s2e(39.0), s2e(42.0), s2e(48.0)
    };

    private short calc() {
        int inst = 0, perc = 0;

        updateAmPm();
        updateNoise();

        for (int i = 0; i < 18; i++) {
            this.slot[i].calcPhase(this.lfoPm);
            this.slot[i].calcEnvelope(this.lfoAm, this::updateEg);
        }

        for (int i = 0; i < 6; i++)
            if ((this.mask & maskCh(i)) == 0 && (car(i).egMode != EgState.FINISH))
                inst += car(i).calcCar(mod(i).calcMod());

        if (this.vrc7Mode == 0) {
            // CH6
            if (this.patchNumber[6] <= 15) {
                if ((this.mask & maskCh(6)) == 0 && (car(6).egMode != EgState.FINISH))
                    inst += car(6).calcCar(mod(6).calcMod());
            } else {
                if ((this.mask & MASK_BD) == 0 && (car(6).egMode != EgState.FINISH))
                    perc += car(6).calcCar(mod(6).calcMod());
            }

            // CH7
            if (this.patchNumber[7] <= 15) {
                if ((this.mask & maskCh(7)) == 0 && (car(7).egMode != EgState.FINISH))
                    inst += car(7).calcCar(mod(7).calcMod());
            } else {
                if ((this.mask & MASK_HH) == 0 && (mod(7).egMode != EgState.FINISH))
                    perc += mod(7).calcHat(car(8).pgOut, this.noiseSeed & 1);
                if ((this.mask & MASK_SD) == 0 && (car(7).egMode != EgState.FINISH))
                    perc -= car(7).calcSnare(this.noiseSeed & 1);
            }

            // CH8
            if (this.patchNumber[8] <= 15) {
                if ((this.mask & maskCh(8)) == 0 && (car(8).egMode != EgState.FINISH))
                    inst += car(8).calcCar(mod(8).calcMod());
            } else {
                if ((this.mask & MASK_TOM) == 0 && (mod(8).egMode != EgState.FINISH))
                    perc += mod(8).calcTom();
                if ((this.mask & MASK_CYM) == 0 && (car(8).egMode != EgState.FINISH))
                    perc -= car(8).calcCym(mod(7).pgOut);
            }
        }

        int out = inst + (perc << 1);
        return (short) out;
    }

//#ifdef EMU2413_COMPACTION

//        private short OPLL_calc() {
//            return calc();
//        }

//#else

    private short calc_() {
        if (this.quality == 0)
            return calc();

        while (this.realStep > this.opllTime) {
            this.opllTime += this.opllStep;
            this.prev = this.next;
            this.next = calc();
        }

        this.opllTime -= this.realStep;
        this.out = (short) (((double) this.next * (this.opllStep - this.opllTime)
                + (double) this.prev * this.opllTime) / this.opllStep);

        return (short) this.out;
    }

//#endif

    public void setMuteMask(int muteMask) {

        for (int c = 0; c < 14; c++) {
            int mask;
            if (c < 9) {
                mask = maskCh(c);
            } else {
                mask = switch (c) {
                    case 9 -> MASK_BD;
                    case 10 -> MASK_SD;
                    case 11 -> MASK_TOM;
                    case 12 -> MASK_CYM;
                    case 13 -> MASK_HH;
                    default -> 0;
                };
            }
            if (((muteMask >> c) & 0x01) != 0)
                this.mask |= mask;
            else
                this.mask &= ~mask;
        }
    }

    private void setChipMode(byte Mode) {
        // Enable/Disable VRC7 Mode (with only 6 instead of 9 channels and no rhythm part)
        this.vrc7Mode = Mode;
    }

    // I/O Ctrl

    public void writeReg(int reg, int data) {
        //Debug.printf("OPLL_writeReg:reg:%d:data:%d", reg,data);

        int v, ch;

        data = data & 0xff;
        reg = reg & 0x3f;
        this.reg[reg] = (byte) data;

        switch (reg) {
        case 0x00:
            this.patch[0][0].am = (data >> 7) & 1;
            this.patch[0][0].pm = (data >> 6) & 1;
            this.patch[0][0].eg = (data >> 5) & 1;
            this.patch[0][0].kr = (data >> 4) & 1;
            this.patch[0][0].ml = (data) & 15;
            for (int i = 0; i < 9; i++) {
                if (this.patchNumber[i] == 0) {
                    updatePg(mod(i));
                    mod(i).updateRks();
                    updateEg(mod(i));
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
                    updatePg(car(i));
                    car(i).updateRks();
                    updateEg(car(i));
                }
            }
            break;

        case 0x02:
            this.patch[0][0].kl = (data >> 6) & 3;
            this.patch[0][0].tl = (data) & 63;
            for (int i = 0; i < 9; i++) {
                if (this.patchNumber[i] == 0) {
                    mod(i).updateTll();
                }
            }
            break;

        case 0x03:
            this.patch[0][1].kl = (data >> 6) & 3;
            this.patch[0][1].wf = (data >> 4) & 1;
            this.patch[0][0].wf = (data >> 3) & 1;
            this.patch[0][0].fb = (data) & 7;
            for (int i = 0; i < 9; i++) {
                if (this.patchNumber[i] == 0) {
                    mod(i).updateWf();
                    car(i).updateWf();
                }
            }
            break;

        case 0x04:
            this.patch[0][0].ar = (data >> 4) & 15;
            this.patch[0][0].dr = (data) & 15;
            for (int i = 0; i < 9; i++) {
                if (this.patchNumber[i] == 0) {
                    updateEg(mod(i));
                }
            }
            break;

        case 0x05:
            this.patch[0][1].ar = (data >> 4) & 15;
            this.patch[0][1].dr = (data) & 15;
            for (int i = 0; i < 9; i++) {
                if (this.patchNumber[i] == 0) {
                    updateEg(car(i));
                }
            }
            break;

        case 0x06:
            this.patch[0][0].sl = (data >> 4) & 15;
            this.patch[0][0].rr = (data) & 15;
            for (int i = 0; i < 9; i++) {
                if (this.patchNumber[i] == 0) {
                    updateEg(mod(i));
                }
            }
            break;

        case 0x07:
            this.patch[0][1].sl = (data >> 4) & 15;
            this.patch[0][1].rr = (data) & 15;
            for (int i = 0; i < 9; i++) {
                if (this.patchNumber[i] == 0) {
                    updateEg(car(i));
                }
            }
            break;

        case 0x0e:
            if (this.vrc7Mode != 0)
                break;
            updateRhythmMode();
            if ((data & 0x20) != 0) {
                if ((data & 0x10) != 0)
                    keyOnBD();
                else
                    keyOffBD();
                if ((data & 0x8) != 0)
                    keyOnSD();
                else
                    keyOffSD();
                if ((data & 0x4) != 0)
                    keyOnTOM();
                else
                    keyOffTOM();
                if ((data & 0x2) != 0)
                    keyOnCYM();
                else
                    keyOffCYM();
                if ((data & 0x1) != 0)
                    keyOn_HH();
                else
                    keyOffHH();
            }
            updateKeyStatus();

            updateAll(mod(6));
            updateAll(car(6));
            updateAll(mod(7));
            updateAll(car(7));
            updateAll(mod(8));
            updateAll(car(8));

            break;

        case 0x0f:
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
            if (this.vrc7Mode != 0 && ch >= 6)
                break;
            setFnumber(ch, data + ((this.reg[0x20 + ch] & 1) << 8));
            updateAll(mod(ch));
            updateAll(car(ch));
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
            if (this.vrc7Mode != 0 && ch >= 6)
                break;
            setFnumber(ch, ((data & 1) << 8) + this.reg[0x10 + ch]);
            setBlock(ch, (data >> 1) & 7);
            setSustain(ch, (data >> 5) & 1);
            if (ch < 0x06 || (this.reg[0x0E] & 0x20) == 0) {
                // Valley Bell Fix: prevent commands 0x26-0x28 from turning
                // the drums (BD, SD, CYM) off
                if ((data & 0x10) != 0)
                    keyOn(ch);
                else
                    keyOff(ch);
            }
            updateAll(mod(ch));
            updateAll(car(ch));
            updateKeyStatus();
            updateRhythmMode();
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
            if (this.vrc7Mode != 0 && reg >= 0x36)
                break;
            int i = (data >> 4) & 15;
            v = data & 15;
            if ((this.reg[0x0e] & 0x20) != 0 && (reg >= 0x36)) {
                switch (reg) {
                case 0x37:
                    setSlotVolume(mod(7), i << 2);
                    break;
                case 0x38:
                    setSlotVolume(mod(8), i << 2);
                    break;
                default:
                    break;
                }
            } else {
                setPatch(reg - 0x30, i);
            }
            setVolume(reg - 0x30, v << 2);
            updateAll(mod(reg - 0x30));
            updateAll(car(reg - 0x30));
            break;

        default:
            break;

        }
    }

    private void writeIO(int adr, int val) {
        if ((adr & 1) != 0)
            writeReg(this.adr, val);
        else
            this.adr = (byte) val;
    }

//#ifndef EMU2413_COMPACTION

    /** STEREO MODE (OPT) */
    public void setPan(int ch, int pan) {
        int fnlCh;

        if (ch >= 14)
            return;

        if (ch < 9)
            fnlCh = ch;
        else
            fnlCh = 13 - (ch - 9);
        calcPanning(this.pan[fnlCh], pan); // Maxim
    }

    private void calcStereo(int[] out) {
        // Maxim: added stereo control (multiply each side by a float in this.pan[ch][side])
        int l = 0, r = 0;

        updateAmPm();
        updateNoise();

        for (int i = 0; i < 18; i++) {
            this.slot[i].calcPhase(this.lfoPm);
            this.slot[i].calcEnvelope(this.lfoAm, this::updateEg);
        }

        for (int i = 0; i < 6; i++)
            if ((this.mask & maskCh(i)) == 0 && (car(i).egMode != EgState.FINISH)) {
                int channel = car(i).calcCar(mod(i).calcMod());
                if (this.pan[i][0] == 1.0f) {
                    l += channel;
                    r += channel;
                } else {
                    l += (int) (channel * this.pan[i][0]);
                    r += (int) (channel * this.pan[i][1]);
                }
            }

        if (this.vrc7Mode == 0) {
            if (this.patchNumber[6] <= 15) {
                if ((this.mask & maskCh(6)) == 0 && (car(6).egMode != EgState.FINISH)) {
                    int channel = car(6).calcCar(mod(6).calcMod());
                    if (this.pan[6][0] == 1.0f) {
                        l += channel;
                        r += channel;
                    } else {
                        l += (int) (channel * this.pan[6][0]);
                        r += (int) (channel * this.pan[6][1]);
                    }

                }
            } else {
                if ((this.mask & MASK_BD) == 0 && (car(6).egMode != EgState.FINISH)) {
                    int channel = car(6).calcCar(mod(6).calcMod()) << 1;
                    if (this.pan[9][0] == 1.0f) {
                        l += channel;
                        r += channel;
                    } else {
                        l += (int) (channel * this.pan[9][0]);
                        r += (int) (channel * this.pan[9][1]);
                    }
                }
            }

            if (this.patchNumber[7] <= 15) {
                if ((this.mask & maskCh(7)) == 0 && (car(7).egMode != EgState.FINISH)) {
                    int channel = car(7).calcCar(mod(7).calcMod());
                    if (this.pan[7][0] == 1.0f) {
                        l += channel;
                        r += channel;
                    } else {
                        l += (int) (channel * this.pan[7][0]);
                        r += (int) (channel * this.pan[7][1]);
                    }
                }
            } else {
                if ((this.mask & MASK_HH) == 0 && (mod(7).egMode != EgState.FINISH)) {
                    int channel = mod(7).calcHat(car(8).pgOut, this.noiseSeed & 1) << 1;
                    if (this.pan[10][0] == 1.0f) {
                        l += channel;
                        r += channel;
                    } else {
                        l += (int) (channel * this.pan[10][0]);
                        r += (int) (channel * this.pan[10][1]);
                    }
                }
                if ((this.mask & MASK_SD) == 0 && (car(7).egMode != EgState.FINISH)) {
                    int channel = -(car(7).calcSnare(this.noiseSeed & 1) << 1); // this one is negated
                    if (this.pan[11][0] == 1.0f) {
                        l += channel;
                        r += channel;
                    } else {
                        l += (int) (channel * this.pan[11][0]);
                        r += (int) (channel * this.pan[11][1]);
                    }
                }
            }

            if (this.patchNumber[8] <= 15) {
                if ((this.mask & maskCh(8)) == 0 && (car(8).egMode != EgState.FINISH)) {
                    int channel = car(8).calcCar(mod(8).calcMod());
                    if (this.pan[8][0] == 1.0f) {
                        l += channel;
                        r += channel;
                    } else {
                        l += (int) (channel * this.pan[8][0]);
                        r += (int) (channel * this.pan[8][1]);
                    }
                }
            } else {
                if ((this.mask & MASK_TOM) == 0 && (mod(8).egMode != EgState.FINISH)) {
                    int channel = mod(8).calcTom() << 1;
                    if (this.pan[12][0] == 1.0f) {
                        l += channel;
                        r += channel;
                    } else {
                        l += (int) (channel * this.pan[12][0]);
                        r += (int) (channel * this.pan[12][1]);
                    }
                }
                if ((this.mask & MASK_CYM) == 0 && (car(8).egMode != EgState.FINISH)) {
                    int channel = -(car(8).calcCym(mod(7).pgOut) << 1); // negated
                    if (this.pan[13][0] == 1.0f) {
                        l += channel;
                        r += channel;
                    } else {
                        l += (int) (channel * this.pan[13][0]);
                        r += (int) (channel * this.pan[13][1]);
                    }
                }
            }
        }
        out[0] = l << 3;
        out[1] = r << 3;
    }

    public void calcStereo(int[][] out, int samples) {
        int[] bufMO = out[0];
        int[] bufRO = out[1];
        int[] buffers = new int[2];

        for (int i = 0; i < samples; i++) {
            if (this.quality == 0) {
                calcStereo(buffers);
                bufMO[i] = buffers[0];
                bufRO[i] = buffers[1];
            } else {
                while (this.realStep > this.opllTime) {
                    this.opllTime += this.opllStep;
                    this.sPrev[0] = this.sNext[0];
                    this.sPrev[1] = this.sNext[1];
                    calcStereo(this.sNext);
                }

                this.opllTime -= this.realStep;
                bufMO[i] = (int) (((double) this.sNext[0] * (this.opllStep - this.opllTime)
                        + (double) this.sPrev[0] * this.opllTime) / this.opllStep);
                bufRO[i] = (int) (((double) this.sNext[1] * (this.opllStep - this.opllTime)
                        + (double) this.sPrev[1] * this.opllTime) / this.opllStep);
            }
            bufMO[i] <<= 1;
            bufRO[i] <<= 1;

            //Debug.printf("OPLL_calc_stereo:out[0][%d]:%d:out[1][%d]:%d:samples:%d", i, out[0][i], out[1][i], samples);
        }
        //Debug.printf("elapsed:%d:%d:%d:%d:%d:%d", e0,e1,e2,e3,e4,e5);
    }

//#endif /* EMU2413_COMPACTION */

    private static final double SQRT2 = 1.414213562;
    private static final int RANGE = 512;

    private void calcPanning(float[] channels, int position) {
        if (position > RANGE / 2)
            position = RANGE / 2;
        else if (position < -RANGE / 2)
            position = -RANGE / 2;
        position += RANGE / 2; // make -256..0..256 . 0..256..512

        // Equal power law: equation is
        // right = sin( position / range * pi / 2) * sqrt( 2 )
        // left is equivalent to right with position = range - position
        // position is in the range 0 .. RANGE
        // RANGE / 2 = centre, result = 1.0f
        channels[1] = (float) (Math.sin((double) position / RANGE * Math.PI / 2) * SQRT2);
        position = RANGE - position;
        channels[0] = (float) (Math.sin((double) position / RANGE * Math.PI / 2) * SQRT2);
    }

    /**
     * Reset the panning values to the centre position
     */
    private void centrePanning(float[] channels) {
        channels[0] = channels[1] = 1.0f;
    }
}
