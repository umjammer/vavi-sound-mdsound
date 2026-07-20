/*
The MIT License (MIT)

Copyright (c) 2001-2019 Mitsutaka Okazaki

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package mdsound.np.chip;


/**
 * YM2413 emulator
 * <pre>
 *  2001 01-08 : Version 0.10 -- 1st version.
 *  2001 01-15 : Version 0.20 -- semi-public version.
 *  2001 01-16 : Version 0.30 -- 1st public version.
 *  2001 01-17 : Version 0.31 -- Fixed bassdrum problem.
 *             : Version 0.32 -- LPF implemented.
 *  2001 01-18 : Version 0.33 -- Fixed the drum problem, refine the mix-down method.
 *                            -- Fixed the LFO bug.
 *  2001 01-24 : Version 0.35 -- Fixed the drum problem,
 *                               support undocumented EG behavior.
 *  2001 02-02 : Version 0.38 -- Improved the performance.
 *                               Fixed the hi-hat and cymbal model.
 *                               Fixed the default percussive datas.
 *                               Noise reduction.
 *                               Fixed the feedback problem.
 *  2001 03-03 : Version 0.39 -- Fixed some drum bugs.
 *                               Improved the performance.
 *  2001 03-04 : Version 0.40 -- Improved the feedback.
 *                               Change the default table size.
 *                               Clock and Rate can be changed during play.
 *  2001 06-24 : Version 0.50 -- Improved the hi-hat and the cymbal tone.
 *                               Added VRC7 patch (OPLL_reset_patch is changed).
 *                               Fixed OPLL_reset() bug.
 *                               Added OPLL_setMask, OPLL_getMask and OPLL_toggleMask.
 *                               Added OPLL_writeIO.
 *  2001 09-28 : Version 0.51 -- Removed the noise table.
 *  2002 01-28 : Version 0.52 -- Added Stereo mode.
 *  2002 02-07 : Version 0.53 -- Fixed some drum bugs.
 *  2002 02-20 : Version 0.54 -- Added the best quality mode.
 *  2002 03-02 : Version 0.55 -- Removed OPLL_init & OPLL_close.
 *  2002 05-30 : Version 0.60 -- Fixed HH&CYM generator and all Voice datas.
 *  2004 04-10 : Version 0.61 -- Added YMF281B tone (defined by Chabin).
 *
 *  References:
 *    fmopl.c        -- 1999,2000 written by Tatsuyuki Satoh (MAME development).
 *    fmopl.c(fixed) -- (C) 2002 Jarek Burczynski.
 *    s_opl.c        -- 2001 written by Mamiya (NEZplug development).
 *    Fmgen.cpp      -- 1999,2000 written by cisc.
 *    fmpac.ill      -- 2000 created by NARUTO.
 *    MSX-Datapack
 *    YMU757 data sheet
 *    YM2143 data sheet
 * </pre>
 * @author Mitsutaka Okazaki
 * @version 0.61
 */
public class Emu2413 {

    /** opll */
    public static class Opll {

        public enum Tone {
            VRC7_RW,
            VRC7_FT36,
            VRC7_FT35,
            VRC7_MO,
            VRC7_KT2,
            VRC7_KT1,
            _2413,
            _281B
        }

        /** slot */
        static class Slot {

            /**
             * Opll internal interfaces
             */
            private static final int BD1 = 12;
            private static final int BD2 = 13;
            private static final int HH = 14;
            private static final int SD = 15;
            private static final int TOM = 16;
            private static final int CYM = 17;

            private static int dbPos(double x) {
                return (int) (x / DB_STEP);
            }

            private static int dbNeg(double x) {
                return (int) (DB_MUTE + DB_MUTE + x / DB_STEP);
            }

            /** Convert Amp(0 to EG_HEIGHT) to Phase(0 to 8PI). */
            private static int wave2_8pi(int e) {
                return e << (2 + PG_BITS - SLOT_AMP_BITS);
            }

            private static int eg2Db(int d) {
                return (d * (int) (EG_STEP / DB_STEP));
            }

            /** Voice data */
            private static class Patch {
                private int tl, fb, eg, ml, ar, dr, sl, rr, kr, kl, am, pm, wf;
            }

            private Patch patch;

            /** 0 : modulator 1 : carrier */
            private int type;

            // OUTPUT
            private int feedback;
            final int[] output = new int[2]; // Output value of slot */

            // for Phase Generator (PG)

            /** Wave table */
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
            /** B-synchronized */
            private int block;
            /** Current volume */
            private int volume;
            /** Sustain 1 = ON, 0 = OFF */
            private int sustain;
            /** Total Level + Key scale level*/
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

            /** Initializing */
            private void reset(int type) {
                this.type = type;
                this.sinTbl = Opll.waveForm[0];
                this.phase = 0;
                this.dPhase = 0;
                this.output[0] = 0;
                this.output[1] = 0;
                this.feedback = 0;
                this.egMode = EgState.FINISH;
                this.egPhase = Opll.EG_DP_WIDTH;
                this.egDPhase = 0;
                this.rks = 0;
                this.tll = 0;
                this.sustain = 0;
                this.fNum = 0;
                this.block = 0;
                this.volume = 0;
                this.pgOut = 0;
                this.egOut = 0;
                this.patch = Opll.NullPatch;
            }

            /** Slot key on  */
            public void slotOn() {
                this.egMode = EgState.ATTACK;
                this.egPhase = 0;
                this.phase = 0;
                this.updateEg();
            }

            /** Slot key on without resetting the phase */
            public void slotOnStayingPhase() {
                this.egMode = EgState.ATTACK;
                this.egPhase = 0;
                this.updateEg();
            }

            /* Slot key off */
            public void slotOff() {
                if (this.egMode == EgState.ATTACK)
                    this.egPhase = expandBits(AR_ADJUST_TABLE[highBits(this.egPhase, EG_DP_BITS - EG_BITS)], EG_BITS, EG_DP_BITS);
                this.egMode = EgState.RELEASE;
                this.updateEg();
            }

            public void setVolume(int volume) {
                this.volume = volume;
            }

            /** EG */
            public void calcEnvelope(int lfo) {
                int egout;

                switch (egMode) {
                    case ATTACK:
                        egout = AR_ADJUST_TABLE[highBits(egPhase, EG_DP_BITS - EG_BITS)];
                        egPhase += egDPhase;
                        if ((EG_DP_WIDTH & egPhase) != 0 || (Slot.this.patch.ar == 15)) {
                            egout = 0;
                            egPhase = 0;
                            egMode = EgState.DECAY;
                            updateEg();
                        }
                        break;

                    case DECAY:
                        egout = highBits(egPhase, EG_DP_BITS - EG_BITS);
                        egPhase += egDPhase;
                        if (egPhase >= sl[Slot.this.patch.sl]) {
                            if ((Slot.this.patch.eg) != 0) {
                                egPhase = sl[Slot.this.patch.sl];
                                egMode = EgState.SUSHOLD;
                                updateEg();
                            } else {
                                egPhase = sl[Slot.this.patch.sl];
                                egMode = EgState.SUSTAIN;
                                updateEg();
                            }
                        }
                        break;

                    case SUSHOLD:
                        egout = highBits(egPhase, EG_DP_BITS - EG_BITS);
                        if (Slot.this.patch.eg == 0) {
                            egMode = EgState.SUSTAIN;
                            updateEg();
                        }
                        break;

                    case SUSTAIN:
                    case RELEASE:
                        egout = highBits(egPhase, EG_DP_BITS - EG_BITS);
                        egPhase += egDPhase;
                        if (egout >= (1 << EG_BITS)) {
                            egMode = EgState.FINISH;
                            egout = (1 << EG_BITS) - 1;
                        }
                        break;

                    case SETTLE:
                        egout = highBits(egPhase, EG_DP_BITS - EG_BITS);
                        egPhase += egDPhase;
                        if (egout >= (1 << EG_BITS)) {
                            egMode = EgState.ATTACK;
                            egout = (1 << EG_BITS) - 1;
                            updateEg();
                        }
                        break;

                    case FINISH:
                        egout = (1 << EG_BITS) - 1;
                        break;

                    default:
                        egout = (1 << EG_BITS) - 1;
                        break;
                }

                if (Slot.this.patch.am != 0)
                    egout = eg2Db(egout + tll) + lfo;
                else {
                    egout = eg2Db(egout + tll);
                    //logger.log(Level.TRACE, "egOut %d slot.tll %d (e_int32)(EG_STEP/DB_STEP) %d".formatted(egOut, slot.tll, (int) (EG_STEP / DB_STEP)));
                }

                if (egout >= DB_MUTE)
                    egout = DB_MUTE - 1;

                egOut = egout | 3;
            }

            /**
             * Calc Parameters
             */
            public int calcEgDPhase() {
                return this.egMode.calcEgDPhase(this);
            }

            public void updatePg() {
                this.dPhase = dphaseTable[this.fNum][this.block][this.patch.ml];
            }

            public void updateTll() {
                this.tll = (this.type == 0) ? tllTable[this.fNum >> 5][this.block][this.patch.tl][this.patch.kl] : tllTable[this.fNum >> 5][this.block][this.volume][this.patch.kl];
            }

            public void updateRks() {
                this.rks = rksTable[this.fNum >> 8][this.block][this.patch.kr];
            }

            public void updateWf() {
                this.sinTbl = waveForm[this.patch.wf];
            }

            public void updateEg() {
                this.egDPhase = this.calcEgDPhase();
            }

            public void updateAll() {
                this.updatePg();
                this.updateTll();
                this.updateRks();
                this.updateWf();
                this.updateEg(); // EG should be updated last.
            }

            /* Change a rhythm Voice */
            public void setSlotPatch(Patch patch) {
                this.patch = patch;
            }

            /** CARRIER */
            public int calcCar(int fm) {
                if (this.egOut >= (DB_MUTE - 1)) {
                    //logger.log(Level.TRACE, "calc_slot_car: output over");
                    this.output[0] = 0;
                } else {
                    //logger.log(Level.TRACE, "calc_slot_car: this.egOut %d".formatted(this.egOut));
                    this.output[0] = db2linTable[this.sinTbl[(this.pgOut + wave2_8pi(fm)) & (PG_WIDTH - 1)] + this.egOut];
                }

                this.output[1] = (this.output[1] + this.output[0]) >> 1;
                return this.output[1];
            }

            /** MODULATOR */
            public int calcMod() {
                this.output[1] = this.output[0];

                if (this.egOut >= (DB_MUTE - 1)) {
                    this.output[0] = 0;
                } else if (this.patch.fb != 0) {
                    int fm = wave2_4pi(this.feedback) >> (7 - this.patch.fb);
                    this.output[0] = db2linTable[this.sinTbl[(this.pgOut + fm) & (PG_WIDTH - 1)] + this.egOut];
                } else {
                    this.output[0] = db2linTable[this.sinTbl[this.pgOut] + this.egOut];
                }

                this.feedback = (this.output[1] + this.output[0]) >> 1;

                return this.feedback;
            }

            /** TOM */
            public int calcTom() {
                if (this.egOut >= (DB_MUTE - 1))
                    return 0;

                return db2linTable[this.sinTbl[this.pgOut] + this.egOut];
            }

            /** SNARE */
            public int calcSnare(int noise) {
                if (this.egOut >= (DB_MUTE - 1))
                    return 0;

                if (bit(this.pgOut, 7) != 0)
                    return db2linTable[(noise != 0 ? dbPos(0.0) : dbPos(15.0)) + this.egOut];
                else
                    return db2linTable[(noise != 0 ? dbNeg(0.0) : dbNeg(15.0)) + this.egOut];
            }

            /** TOP-CYM */
            public int calcCym(int pgOut_hh) {
                int dbOut;

                if (this.egOut >= (DB_MUTE - 1))
                    return 0;
                else if ((
                        // the same as fmopl.c
                        ((bit(pgOut_hh, PG_BITS - 8) ^ bit(pgOut_hh, PG_BITS - 1)) | bit(pgOut_hh, PG_BITS - 7)) ^
                                // different from fmopl.c
                                (bit(this.pgOut, PG_BITS - 7) & ~bit(this.pgOut, PG_BITS - 5))) != 0
                )
                    dbOut = dbNeg(3.0);
                else
                    dbOut = dbPos(3.0);

                return db2linTable[dbOut + this.egOut];
            }

            /** HI-HAT */
            public int calcHat(int pgOutCym, int noise) {
                int dbout;

                if (this.egOut >= (DB_MUTE - 1))
                    return 0;
                else if ((
                        // the same as fmopl.c
                        ((bit(this.pgOut, PG_BITS - 8) ^ bit(this.pgOut, PG_BITS - 1)) | bit(this.pgOut, PG_BITS - 7)) ^
                                // different from fmopl.c
                                (bit(pgOutCym, PG_BITS - 7) & ((~bit(pgOutCym, PG_BITS - 5)) & 1))) != 0
                ) {
                    if (noise != 0)
                        dbout = dbNeg(12.0);
                    else
                        dbout = dbNeg(24.0);
                } else {
                    if (noise != 0)
                        dbout = dbPos(12.0);
                    else
                        dbout = dbPos(24.0);
                }

                return db2linTable[dbout + this.egOut];
            }

            /** PG */
            public void calcPhase(int lfo) {
                if (this.patch.pm != 0)
                    this.phase += (this.dPhase * lfo) >> PM_AMP_BITS;
                else
                    this.phase += this.dPhase;

                this.phase &= DP_WIDTH - 1;

                this.pgOut = highBits(this.phase, DP_BASE_BITS);
            }

            /** dB to Liner table */
            private static final int[] db2linTable = new int[(DB_MUTE + DB_MUTE) * 2];

            /* Table for dB(0 -- (1<<DB_BITS)-1) to Liner(0 -- DB2LIN_AMP_WIDTH) */
            static {
                for (int i = 0; i < DB_MUTE + DB_MUTE; i++) {
                    db2linTable[i] = (int) ((double) ((1 << DB2LIN_AMP_BITS) - 1) * Math.pow(10, -(double) i * DB_STEP / 20));
                    if (i >= DB_MUTE) db2linTable[i] = 0;
                    db2linTable[i + DB_MUTE + DB_MUTE] = -db2linTable[i];
                }
            }
        }

        /** Mask */
        public static int maskCh(int x) {
            return 1 << x;
        }

        private static final int MASK_HH = 1 << 9;
        private static final int MASK_CYM = 1 << 10;
        private static final int MASK_TOM = 1 << 11;
        private static final int MASK_SD = 1 << 12;
        private static final int MASK_BD = 1 << 13;
        private static final int MASK_RHYTHM = MASK_HH | MASK_CYM | MASK_TOM | MASK_SD | MASK_BD;

        int adr;
        private int _out;

        private int realStep;
        private int opllTime;
        private int opllStep;
        private int prev, next;
        private final int[] sPrev = new int[2];
        private final int[] sNext = new int[2];
        private final int[] pan = new int[16];

        /** Register */
        final int[] reg = new int[0x40];

        /** the register file as the chip has it; OPLL registers are only ever written */
        public int[] getRegisters() {
            return this.reg;
        }
        private final int[] slotOnFlag = new int[18];

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
        private final int[] patchNumber = new int[9];
        private final int[] keyStatus = new int[9];

        /** Slot */
        final Slot[] slot = new Slot[18];

        /** Voice data */
        private final Slot.Patch[][] patch = {
                new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2],
                new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2],
                new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2],
                new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2],
                new Slot.Patch[2], new Slot.Patch[2], new Slot.Patch[2]
        };
        /** flag for check patch update */
        private final int[] patchUpdate = new int[2];

        private int mask;

        private Slot mod(int x) {
            return this.slot[x << 1];
        }

        private Slot car(int x) {
            return this.slot[(x << 1) | 1];
        }

        /** Channel key on */
        private void keyOn(int i) {
            if (this.slotOnFlag[i * 2] == 0)
                this.mod(i).slotOn();
            if (this.slotOnFlag[i * 2 + 1] == 0)
                this.car(i).slotOn();
            this.keyStatus[i] = 1;
        }

        /** Channel key off */
        private void keyOff(int i) {
            if (this.slotOnFlag[i * 2 + 1] != 0)
                this.car(i).slotOff();
            this.keyStatus[i] = 0;
        }

        private void keyOnBD() {
            this.keyOn(6);
        }

        private void keyOnSD() {
            if (this.slotOnFlag[Slot.SD] == 0)
                this.car(7).slotOn();
        }

        private void keyOnTOM() {
            if (this.slotOnFlag[Slot.TOM] == 0)
                this.mod(8).slotOn();
        }

        private void keyOnHH() {
            if (this.slotOnFlag[Slot.HH] == 0)
                this.mod(7).slotOnStayingPhase();
        }

        private void keyOnCYM() {
            if (this.slotOnFlag[Slot.CYM] == 0)
                this.car(8).slotOnStayingPhase();
        }

        // Drum key off
        private void keyOffBD() {
            this.keyOff(6);
        }

        private void keyOffSD() {
            if (this.slotOnFlag[Slot.SD] != 0)
                this.car(7).slotOff();
        }

        private void keyOffTOM() {
            if (this.slotOnFlag[Slot.TOM] != 0)
                this.mod(8).slotOff();
        }

        private void keyOffHH() {
            if (this.slotOnFlag[Slot.HH] != 0)
                this.mod(7).slotOff();
        }

        private void keyOffCYM() {
            if (this.slotOnFlag[Slot.CYM] != 0)
                this.car(8).slotOff();
        }

        /** Change a Voice */
        private void setPatch(int i, int num) {
            this.patchNumber[i] = num;
            this.mod(i).patch = this.patch[num][0];
            this.car(i).patch = this.patch[num][1];
        }

        /** Set sustain parameter */
        private void setSustain(int c, int sustain) {
            this.car(c).sustain = sustain;
            if (this.mod(c).type != 0)
                this.mod(c).sustain = sustain;
        }

        /** Volume : 6bit ( Volume register << 2 ) */
        private void setVolume(int c, int volume) {
            this.car(c).volume = volume;
        }

        /** Set F-Number ( fNum : 9bit ) */
        private void setFNumber(int c, int fNum) {
            this.car(c).fNum = fNum;
            this.mod(c).fNum = fNum;
        }

        /** Set synchronized data (block : 3bit ) */
        private void setBlock(int c, int block) {
            this.car(c).block = block;
            this.mod(c).block = block;
        }

        /** Change Rhythm Mode */
        private void updateRhythmMode() {
            if ((this.patchNumber[6] & 0x10) != 0) {
                if ((this.slotOnFlag[Slot.BD2] | (this.reg[0x0e] & 0x20)) == 0) {
                    this.slot[Slot.BD1].egMode = EgState.FINISH;
                    this.slot[Slot.BD2].egMode = EgState.FINISH;
                    this.setPatch(6, this.reg[0x36] >> 4);
                }
            } else if ((this.reg[0x0e] & 0x20) != 0) {
                this.patchNumber[6] = 16;
                this.slot[Slot.BD1].egMode = EgState.FINISH;
                this.slot[Slot.BD2].egMode = EgState.FINISH;
                this.slot[Slot.BD1].setSlotPatch(this.patch[16][0]);
                this.slot[Slot.BD2].setSlotPatch(this.patch[16][1]);
            }

            if ((this.patchNumber[7] & 0x10) != 0) {
                if (!((this.slotOnFlag[Slot.HH] != 0 && this.slotOnFlag[Slot.SD] != 0) || (this.reg[0x0e] & 0x20) != 0)) {
                    this.slot[Slot.HH].type = 0;
                    this.slot[Slot.HH].egMode = EgState.FINISH;
                    this.slot[Slot.SD].egMode = EgState.FINISH;
                    this.setPatch(7, this.reg[0x37] >> 4);
                }
            } else if ((this.reg[0x0e] & 0x20) != 0) {
                this.patchNumber[7] = 17;
                this.slot[Slot.HH].type = 1;
                this.slot[Slot.HH].egMode = EgState.FINISH;
                this.slot[Slot.SD].egMode = EgState.FINISH;
                this.slot[Slot.HH].setSlotPatch(this.patch[17][0]);
                this.slot[Slot.SD].setSlotPatch(this.patch[17][1]);
            }

            if ((this.patchNumber[8] & 0x10) != 0) {
                if (!((this.slotOnFlag[Slot.CYM] != 0 && this.slotOnFlag[Slot.TOM] != 0) || (this.reg[0x0e] & 0x20) != 0)) {
                    this.slot[Slot.TOM].type = 0;
                    this.slot[Slot.TOM].egMode = EgState.FINISH;
                    this.slot[Slot.CYM].egMode = EgState.FINISH;
                    this.setPatch(8, this.reg[0x38] >> 4);
                }
            } else if ((this.reg[0x0e] & 0x20) != 0) {
                this.patchNumber[8] = 18;
                this.slot[Slot.TOM].type = 1;
                this.slot[Slot.TOM].egMode = EgState.FINISH;
                this.slot[Slot.CYM].egMode = EgState.FINISH;
                this.slot[Slot.TOM].setSlotPatch(this.patch[18][0]);
                this.slot[Slot.CYM].setSlotPatch(this.patch[18][1]);
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
            this.patch[num][0].wf = patch[0].wf;

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
            this.patch[num][1].wf = patch[1].wf;
        }

        private static final int[] sl = {
                s2e(0.0), s2e(3.0), s2e(6.0), s2e(9.0), s2e(12.0), s2e(15.0), s2e(18.0), s2e(21.0),
                s2e(24.0), s2e(27.0), s2e(30.0), s2e(33.0), s2e(36.0), s2e(39.0), s2e(42.0), s2e(48.0)
        };

        public void init(int clk, int rate) {

            defaultPatch = new Slot.Patch[OPLL_TONE_NUM][][];
            for (int i = 0; i < OPLL_TONE_NUM; i++) {
                defaultPatch[i] = new Slot.Patch[19][];
                for (int j = 0; j < 19; j++) {
                    defaultPatch[i][j] = new Slot.Patch[2];
                    for (int k = 0; k < 2; k++) {
                        defaultPatch[i][j][k] = new Slot.Patch();
                    }
                }
            }

            makeTables(clk, rate);

            //this.vrc7_mode = 0x00;

            for (int i = 0; i < 19; i++) {
                this.patch[i][0] = new Slot.Patch();
                this.patch[i][1] = new Slot.Patch();
            }

            //for (i = 0; i < 14; i++)
            //    centre_panning(this.pan[i]);

            this.mask = 0;

            reset();
            resetPatch(6); // original was 0, that makes no drums
        }

        /** Reset patch data by system default. */
        public void resetPatch(int type) {
            for (int i = 0; i < 19; i++) {
                this.copyPatch(i, defaultPatch[type][i]);
            }
        }

        /** Reset whole of Opll except patch data. */
        public void reset() {
            this.adr = 0;
            this._out = 0;

            this.pmPhase = 0;
            this.amPhase = 0;

            this.noiseSeed = 0xffff;
            this.mask = 0;

            for (int i = 0; i < 18; i++) {
                this.slot[i] = new Slot();
                this.slot[i].reset(i % 2);
            }

            for (int i = 0; i < 9; i++) {
                this.keyStatus[i] = 0;
                this.setPatch(i, 0);
            }

            for (int i = 0; i < 0x40; i++)
                writeReg(i, 0);

//# ifndef EMU2413_COMPACTION
            this.realStep = (1 << 31) / rate;
            this.opllStep = (1 << 31) / (clk / 72);
            this.opllTime = 0;
            for (int i = 0; i < 14; i++) {
                this.pan[i] = 3; // original was 2, that makes monaural
            }
            this.sPrev[0] = this.sPrev[1] = 0;
            this.sNext[0] = this.sNext[1] = 0;
//#endif
        }

        /** Force Refresh (When external program changes some parameters). */
        public void forceRefresh() {
            for (int i = 0; i < 9; i++)
                this.setPatch(i, this.patchNumber[i]);

            for (int i = 0; i < 18; i++) {
                this.slot[i].updatePg();
                this.slot[i].updateRks();
                this.slot[i].updateTll();
                this.slot[i].updateWf();
                this.slot[i].updateEg();
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

        private int calcInternal() {
            int inst = 0, perc = 0, _out = 0;
            int i;

            updateAmPm();
            updateNoise();

            for (i = 0; i < 18; i++) {
                this.slot[i].calcPhase(this.lfoPm);
                this.slot[i].calcEnvelope(this.lfoAm);
            }

            for (i = 0; i < 6; i++)
                if ((this.mask & maskCh(i)) == 0 && (this.car(i).egMode != EgState.FINISH))
                    inst += this.car(i).calcCar(this.mod(i).calcMod());

            // CH6
            if (this.patchNumber[6] <= 15) {
                if ((this.mask & maskCh(6)) == 0 && (this.car(6).egMode != EgState.FINISH))
                    inst += this.car(6).calcCar(this.mod(6).calcMod());
            } else {
                if ((this.mask & MASK_BD) == 0 && (this.car(6).egMode != EgState.FINISH))
                    perc += this.car(6).calcCar(this.mod(6).calcMod());
            }

            // CH7
            if (this.patchNumber[7] <= 15) {
                if ((this.mask & maskCh(7)) == 0 && (this.car(7).egMode != EgState.FINISH))
                    inst += this.car(7).calcCar(this.mod(7).calcMod());
            } else {
                if ((this.mask & MASK_HH) == 0 && (this.mod(7).egMode != EgState.FINISH))
                    perc += this.mod(7).calcHat(this.car(8).pgOut, this.noiseSeed & 1);
                if ((this.mask & MASK_SD) == 0 && (this.car(7).egMode != EgState.FINISH))
                    perc -= this.car(7).calcSnare(this.noiseSeed & 1);
            }

            // CH8
            if (this.patchNumber[8] <= 15) {
                if ((this.mask & maskCh(8)) == 0 && (this.car(8).egMode != EgState.FINISH))
                    inst += this.car(8).calcCar(this.mod(8).calcMod());
            } else {
                if ((this.mask & MASK_TOM) == 0 && (this.mod(8).egMode != EgState.FINISH))
                    perc += this.mod(8).calcTom();
                if ((this.mask & MASK_CYM) == 0 && (this.car(8).egMode != EgState.FINISH))
                    perc -= this.car(8).calcCym(this.mod(7).pgOut);
            }
            // end if (! this.vrc7_mode)

            _out = inst + (perc << 1);
            return _out << 3;
        }

        public int calc() {
            if (this.quality == 0)
                return calcInternal();

            while (this.realStep > this.opllTime) {
                this.opllTime += this.opllStep;
                this.prev = this.next;
                this.next = calcInternal();
            }

            this.opllTime -= this.realStep;
            this._out = (int) (((double) this.next * (this.opllStep - this.opllTime) +
                    (double) this.prev * this.opllTime) / this.opllStep);

            return this._out;
        }

        public int setMask(int mask) {
            int ret = this.mask;
            this.mask = mask;
            return ret;
        }

        private int toggleMask(int mask) {
            int ret = this.mask;
            this.mask ^= mask;
            return ret;
        }

        /** I/O Ctrl */
        public void writeReg(int reg, int data) {
//logger.log(Level.TRACE, "OPLL_writeReg:reg:%d:data:%d".formatted(reg,data));

            data = data & 0xff;
            reg = reg & 0x3f;
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
                        this.mod(i).updatePg();
                        this.mod(i).updateRks();
                        this.mod(i).updateEg();
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
                        this.car(i).updatePg();
                        this.car(i).updateRks();
                        this.car(i).updateEg();
                    }
                }
                break;

            case 0x02:
                this.patch[0][0].kl = (data >> 6) & 3;
                this.patch[0][0].tl = (data) & 63;
                for (int i = 0; i < 9; i++) {
                    if (this.patchNumber[i] == 0) {
                        this.mod(i).updateTll();
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
                        this.mod(i).updateWf();
                        this.car(i).updateWf();
                    }
                }
                break;

            case 0x04:
                this.patch[0][0].ar = (data >> 4) & 15;
                this.patch[0][0].dr = (data) & 15;
                for (int i = 0; i < 9; i++) {
                    if (this.patchNumber[i] == 0) {
                        this.mod(i).updateEg();
                    }
                }
                break;

            case 0x05:
                this.patch[0][1].ar = (data >> 4) & 15;
                this.patch[0][1].dr = (data) & 15;
                for (int i = 0; i < 9; i++) {
                    if (this.patchNumber[i] == 0) {
                        this.car(i).updateEg();
                    }
                }
                break;

            case 0x06:
                this.patch[0][0].sl = (data >> 4) & 15;
                this.patch[0][0].rr = (data) & 15;
                for (int i = 0; i < 9; i++) {
                    if (this.patchNumber[i] == 0) {
                        this.mod(i).updateEg();
                    }
                }
                break;

            case 0x07:
                this.patch[0][1].sl = (data >> 4) & 15;
                this.patch[0][1].rr = (data) & 15;
                for (int i = 0; i < 9; i++) {
                    if (this.patchNumber[i] == 0) {
                        this.car(i).updateEg();
                    }
                }
                break;

            case 0x0e:
                this.updateRhythmMode();
                if ((data & 0x20) != 0) {
                    if ((data & 0x10) != 0)
                        this.keyOnBD();
                    else
                        this.keyOffBD();
                    if ((data & 0x8) != 0)
                        this.keyOnSD();
                    else
                        this.keyOffSD();
                    if ((data & 0x4) != 0)
                        this.keyOnTOM();
                    else
                        this.keyOffTOM();
                    if ((data & 0x2) != 0)
                        this.keyOnCYM();
                    else
                        this.keyOffCYM();
                    if ((data & 0x1) != 0)
                        this.keyOnHH();
                    else
                        this.keyOffHH();
                }
                this.updateKeyStatus();

                this.mod(6).updateAll();
                this.car(6).updateAll();
                this.mod(7).updateAll();
                this.car(7).updateAll();
                this.mod(8).updateAll();
                this.car(8).updateAll();

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
                int ch = reg - 0x10;
                this.setFNumber(ch, data + ((this.reg[0x20 + ch] & 1) << 8));
                this.mod(ch).updateAll();
                this.car(ch).updateAll();
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
                this.setFNumber(ch, ((data & 1) << 8) + this.reg[0x10 + ch]);
                this.setBlock(ch, (data >> 1) & 7);
                this.setSustain(ch, (data >> 5) & 1);
                if ((data & 0x10) != 0)
                    this.keyOn(ch);
                else
                    this.keyOff(ch);
                this.mod(ch).updateAll();
                this.car(ch).updateAll();
                this.updateKeyStatus();
                this.updateRhythmMode();
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
                int i = (data >> 4) & 15;
                int v = data & 15;
                if ((this.reg[0x0e] & 0x20) != 0 && (reg >= 0x36)) {
                    switch (reg) {
                    case 0x37:
                        this.mod(7).setVolume(i << 2);
                        break;
                    case 0x38:
                        this.mod(8).setVolume(i << 2);
                        break;
                    default:
                        break;
                    }
                } else {
                    this.setPatch(reg - 0x30, i);
                }
                this.setVolume(reg - 0x30, v << 2);
                this.mod(reg - 0x30).updateAll();
                this.car(reg - 0x30).updateAll();
                break;

            default:
                break;
            }
        }

        public void writeIO(int adr, int val) {
            if ((adr & 1) != 0)
                writeReg(this.adr, val);
            else
                this.adr = val;
        }

        /** STEREO MODE (OPT) */
        public void setPan(int ch, int pan) {
            this.pan[ch & 15] = pan & 3;
        }

        private void calcStereoInternal(int[] out) {
            int[] b = {0, 0, 0, 0}; // Ignore, Right, Left, Center */
            int[] r = {0, 0, 0, 0}; // Ignore, Right, Left, Center */

            updateAmPm();
            updateNoise();

            for (int i = 0; i < 18; i++) {
                this.slot[i].calcPhase(this.lfoPm);
                this.slot[i].calcEnvelope(this.lfoAm);
            }

            for (int i = 0; i < 6; i++)
                if ((this.mask & maskCh(i)) == 0 && (this.car(i).egMode != EgState.FINISH))
                    b[this.pan[i]] += this.car(i).calcCar(this.mod(i).calcMod());

            if (this.patchNumber[6] <= 15) {
                if ((this.mask & maskCh(6)) == 0 && (this.car(6).egMode != EgState.FINISH))
                    b[this.pan[6]] += this.car(6).calcCar(this.mod(6).calcMod());
            } else {
                if ((this.mask & MASK_BD) == 0 && (this.car(6).egMode != EgState.FINISH))
                    b[this.pan[9]] += this.car(6).calcCar(this.mod(6).calcMod());
            }

            if (this.patchNumber[7] <= 15) {
                if ((this.mask & maskCh(7)) == 0 && (this.car(7).egMode != EgState.FINISH))
                    b[this.pan[7]] += this.car(7).calcCar(this.mod(7).calcMod());
            } else {
                if ((this.mask & MASK_HH) == 0 && (this.mod(7).egMode != EgState.FINISH))
                    r[this.pan[10]] += this.mod(7).calcHat(this.car(8).pgOut, this.noiseSeed & 1);
                if ((this.mask & MASK_SD) == 0 && (this.car(7).egMode != EgState.FINISH))
                    r[this.pan[11]] -= this.car(7).calcSnare(this.noiseSeed & 1);
            }

            if (this.patchNumber[8] <= 15) {
                if ((this.mask & maskCh(8)) == 0 && (this.car(8).egMode != EgState.FINISH))
                    b[this.pan[8]] += this.car(8).calcCar(this.mod(8).calcMod());
            } else {
                if ((this.mask & MASK_TOM) == 0 && (this.mod(8).egMode != EgState.FINISH))
                    r[this.pan[12]] += this.mod(8).calcTom();
                if ((this.mask & MASK_CYM) == 0 && (this.car(8).egMode != EgState.FINISH))
                    r[this.pan[13]] -= this.car(8).calcCym(this.mod(7).pgOut);
            }

            out[1] = (b[1] + b[3] + ((r[1] + r[3]) << 1)) << 3;
            out[0] = (b[2] + b[3] + ((r[2] + r[3]) << 1)) << 3;
        }

        public void calcStereo(int[] out) {
            if (this.quality == 0) {
                calcStereoInternal(out);
                return;
            }

            while (this.realStep > this.opllTime) {
                this.opllTime += this.opllStep;
                this.sPrev[0] = this.sNext[0];
                this.sPrev[1] = this.sNext[1];
                calcStereoInternal(this.sNext);
            }

            this.opllTime -= this.realStep;
            out[0] = (int) (((double) this.sNext[0] * (this.opllStep - this.opllTime) +
                    (double) this.sPrev[0] * this.opllTime) / this.opllStep);
            out[1] = (int) (((double) this.sNext[1] * (this.opllStep - this.opllTime) +
                    (double) this.sPrev[1] * this.opllTime) / this.opllStep);
        }

        private static final int OPLL_TONE_NUM = 8;
        private static final byte[][] default_inst = {
                // patch set by rainwarrior (8/01/2012)
                // http://forumthis.nesdev.com/viewtopic.php?f=6&t=9141
                {
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x03, 0x21, 0x05, 0x06, (byte) 0xB8, (byte) 0x82, 0x42, 0x27, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x13, 0x41, 0x13, 0x0D, (byte) 0xD8, (byte) 0xD6, 0x23, 0x12, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x31, 0x11, 0x08, 0x08, (byte) 0xFA, (byte) 0x9A, 0x22, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

                        0x31, 0x61, 0x18, 0x07, 0x78, 0x64, 0x30, 0x27, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x22, 0x21, 0x1E, 0x06, (byte) 0xF0, 0x76, 0x08, 0x28, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x02, 0x01, 0x06, 0x00, (byte) 0xF0, (byte) 0xF2, 0x03, (byte) 0xF5, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x61, 0x1D, 0x07, (byte) 0x82, (byte) 0x81, 0x16, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

                        0x23, 0x21, 0x1A, 0x17, (byte) 0xCF, 0x72, 0x25, 0x17, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x15, 0x11, 0x25, 0x00, 0x4F, 0x71, 0x00, 0x11, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        (byte) 0x85, 0x01, 0x12, 0x0F, (byte) 0x99, (byte) 0xA2, 0x40, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x07, (byte) 0xC1, 0x69, 0x07, (byte) 0xF3, (byte) 0xF5, (byte) 0xA7, 0x12, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

                        0x71, 0x23, 0x0D, 0x06, 0x66, 0x75, 0x23, 0x16, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x02, (byte) 0xD3, 0x05, (byte) 0xA3, (byte) 0x92, (byte) 0xF7, 0x52, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x61, 0x63, 0x0C, 0x00, (byte) 0x94, (byte) 0xAF, 0x34, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x62, 0x0D, 0x00, (byte) 0xB1, (byte) 0xA0, 0x54, 0x17, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                },

                // patch set by quietust (1/18/2004), used in FamiTracker 0.3.6
                // Source: http://nesdev.com/cgi-bin/wwwthreads/showpost.pl?Board=NESemdev&Number=1440
                {
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x03, 0x21, 0x04, 0x06, (byte) 0x8D, (byte) 0xF2, 0x42, 0x17, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x13, 0x41, 0x05, 0x0E, (byte) 0x99, (byte) 0x96, 0x63, 0x12, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x31, 0x11, 0x10, 0x0A, (byte) 0xF0, (byte) 0x9C, 0x32, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

                        0x21, 0x61, 0x1D, 0x07, (byte) 0x9F, 0x64, 0x20, 0x27, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x22, 0x21, 0x1E, 0x06, (byte) 0xF0, 0x76, 0x08, 0x28, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x02, 0x01, 0x06, 0x00, (byte) 0xF0, (byte) 0xF2, 0x03, (byte) 0x95, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x61, 0x1C, 0x07, (byte) 0x82, (byte) 0x81, 0x16, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

                        0x23, 0x21, 0x1A, 0x17, (byte) 0xEF, (byte) 0x82, 0x25, 0x15, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x25, 0x11, 0x1F, 0x00, (byte) 0x86, 0x41, 0x20, 0x11, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        (byte) 0x85, 0x01, 0x1F, 0x0F, (byte) 0xE4, (byte) 0xA2, 0x11, 0x12, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x07, (byte) 0xC1, 0x2B, 0x45, (byte) 0xB4, (byte) 0xF1, 0x24, (byte) 0xF4, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

                        0x61, 0x23, 0x11, 0x06, (byte) 0x96, (byte) 0x96, 0x13, 0x16, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x02, (byte) 0xD3, 0x05, (byte) 0x82, (byte) 0xA2, 0x31, 0x51, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x61, 0x22, 0x0D, 0x02, (byte) 0xC3, 0x7F, 0x24, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x62, 0x0E, 0x00, (byte) 0xA1, (byte) 0xA0, 0x44, 0x17, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                },

                // patch set by Mitsutaka Okazaki used in FamiTracker 0.3.5 and prior (6/24/2001)
                {
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x33, 0x01, 0x09, 0x0e, (byte) 0x94, (byte) 0x90, 0x40, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x13, 0x41, 0x0f, 0x0d, (byte) 0xce, (byte) 0xd3, 0x43, 0x13, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x12, 0x1b, 0x06, (byte) 0xff, (byte) 0xd2, 0x00, 0x32, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

                        0x61, 0x61, 0x1b, 0x07, (byte) 0xaf, 0x63, 0x20, 0x28, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x22, 0x21, 0x1e, 0x06, (byte) 0xf0, 0x76, 0x08, 0x28, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x66, 0x21, 0x15, 0x00, (byte) 0x93, (byte) 0x94, 0x20, (byte) 0xf8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x61, 0x1c, 0x07, (byte) 0x82, (byte) 0x81, 0x10, 0x17, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

                        0x23, 0x21, 0x20, 0x1f, (byte) 0xc0, 0x71, 0x07, 0x47, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x25, 0x31, 0x26, 0x05, 0x64, 0x41, 0x18, (byte) 0xf8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x17, 0x21, 0x28, 0x07, (byte) 0xff, (byte) 0x83, 0x02, (byte) 0xf8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        (byte) 0x97, (byte) 0x81, 0x25, 0x07, (byte) 0xcf, (byte) 0xc8, 0x02, 0x14, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

                        0x21, 0x21, 0x54, 0x0f, (byte) 0x80, 0x7f, 0x07, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x01, 0x56, 0x03, (byte) 0xd3, (byte) 0xb2, 0x43, 0x58, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x31, 0x21, 0x0c, 0x03, (byte) 0x82, (byte) 0xc0, 0x40, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x01, 0x0c, 0x03, (byte) 0xd4, (byte) 0xd3, 0x40, (byte) 0x84, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

                        0x04, 0x21, 0x28, 0x00, (byte) 0xdf, (byte) 0xf8, (byte) 0xff, (byte) 0xf8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x23, 0x22, 0x00, 0x00, (byte) 0xa8, (byte) 0xf8, (byte) 0xf8, (byte) 0xf8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x25, 0x18, 0x00, 0x00, (byte) 0xf8, (byte) 0xa9, (byte) 0xf8, 0x55, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                },

                /* VRC7 TONES by okazaki@angel.ne.jp (4/10/2004) */
                {
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x33, 0x01, 0x09, 0x0e, (byte) 0x94, (byte) 0x90, 0x40, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x13, 0x41, 0x0f, 0x0d, (byte) 0xce, (byte) 0xd3, 0x43, 0x13, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x12, 0x1b, 0x06, (byte) 0xff, (byte) 0xd2, 0x00, 0x32, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x61, 0x61, 0x1b, 0x07, (byte) 0xaf, 0x63, 0x20, 0x28, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x22, 0x21, 0x1e, 0x06, (byte) 0xf0, 0x76, 0x08, 0x28, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x66, 0x21, 0x15, 0x00, (byte) 0x93, (byte) 0x94, 0x20, (byte) 0xf8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x61, 0x1c, 0x07, (byte) 0x82, (byte) 0x81, 0x10, 0x17, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x23, 0x21, 0x20, 0x1f, (byte) 0xc0, 0x71, 0x07, 0x47, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x25, 0x31, 0x26, 0x05, 0x64, 0x41, 0x18, (byte) 0xf8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x17, 0x21, 0x28, 0x07, (byte) 0xff, (byte) 0x83, 0x02, (byte) 0xf8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        (byte) 0x97, (byte) 0x81, 0x25, 0x07, (byte) 0xcf, (byte) 0xc8, 0x02, 0x14, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x21, 0x54, 0x0f, (byte) 0x80, 0x7f, 0x07, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x01, 0x56, 0x03, (byte) 0xd3, (byte) 0xb2, 0x43, 0x58, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x31, 0x21, 0x0c, 0x03, (byte) 0x82, (byte) 0xc0, 0x40, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x01, 0x0c, 0x03, (byte) 0xd4, (byte) 0xd3, 0x40, (byte) 0x84, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x07, 0x21, 0x14, 0x00, (byte) 0xee, (byte) 0xf8, (byte) 0xff, (byte) 0xf8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x31, 0x00, 0x00, (byte) 0xf8, (byte) 0xf7, (byte) 0xf8, (byte) 0xf7, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x25, 0x11, 0x00, 0x00, (byte) 0xf8, (byte) 0xfa, (byte) 0xf8, 0x55, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                },

                // patch set 2 by kevtris (11/15/1999)
                // http://kevtris.org/nes/vrcvii.txt
                {
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x31, 0x22, 0x23, 0x07, (byte) 0xF0, (byte) 0xF0, (byte) 0xE8, (byte) 0xF7, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x03, 0x31, 0x68, 0x05, (byte) 0xF2, 0x74, 0x79, (byte) 0x9C, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x51, 0x72, 0x04, (byte) 0xF1, (byte) 0xD3, (byte) 0x9D, (byte) 0x8B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

                        0x22, 0x61, 0x1B, 0x05, (byte) 0xC0, (byte) 0xA1, (byte) 0xF8, (byte) 0xE8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x22, 0x61, 0x2C, 0x03, (byte) 0xD2, (byte) 0xA1, (byte) 0xA7, (byte) 0xE8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x31, 0x22, (byte) 0xFA, 0x01, (byte) 0xF1, (byte) 0xF1, (byte) 0xF4, (byte) 0xEE, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x61, 0x28, 0x06, (byte) 0xF1, (byte) 0xF1, (byte) 0xCE, (byte) 0x9B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

                        0x27, 0x61, 0x60, 0x00, (byte) 0xF0, (byte) 0xF0, (byte) 0xff, (byte) 0xFD, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x60, 0x21, 0x2B, 0x06, (byte) 0x85, (byte) 0xF1, 0x79, (byte) 0x9D, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x31, (byte) 0xA1, (byte) 0xff, 0x0A, 0x53, 0x62, 0x5E, (byte) 0xAF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x03, (byte) 0xA1, 0x70, 0x0F, (byte) 0xD4, (byte) 0xA3, (byte) 0x94, (byte) 0xBE, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

                        0x2B, 0x61, (byte) 0xE4, 0x07, (byte) 0xF6, (byte) 0x93, (byte) 0xBD, (byte) 0xAC, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x63, (byte) 0xED, 0x07, 0x77, (byte) 0xF1, (byte) 0xC7, (byte) 0xE8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x61, 0x2A, 0x03, (byte) 0xF3, (byte) 0xE2, (byte) 0xB6, (byte) 0xD9, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x63, 0x37, 0x03, (byte) 0xF3, (byte) 0xE2, (byte) 0xB6, (byte) 0xD9, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                },

                // patch set 1 by kevtris (11/14/1999)
                // http://kevtris.org/nes/vrcvii.txt
                {
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x05, 0x03, 0x10, 0x06, 0x74, (byte) 0xA1, 0x13, (byte) 0xF4, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x05, 0x01, 0x16, 0x00, (byte) 0xF9, (byte) 0xA2, 0x15, (byte) 0xF5, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x41, 0x11, 0x00, (byte) 0xA0, (byte) 0xA0, (byte) 0x83, (byte) 0x95, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x41, 0x17, 0x00, 0x60, (byte) 0xF0, (byte) 0x83, (byte) 0x95, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x24, 0x41, 0x1F, 0x00, 0x50, (byte) 0xB0, (byte) 0x94, (byte) 0x94, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x05, 0x01, 0x0B, 0x04, 0x65, (byte) 0xA0, 0x54, (byte) 0x95, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x11, 0x41, 0x0E, 0x04, 0x70, (byte) 0xC7, 0x13, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x02, 0x44, 0x16, 0x06, (byte) 0xE0, (byte) 0xE0, 0x31, 0x35, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x48, 0x22, 0x22, 0x07, 0x50, (byte) 0xA1, (byte) 0xA5, (byte) 0xF4, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x05, (byte) 0xA1, 0x18, 0x00, (byte) 0xA2, (byte) 0xA2, (byte) 0xF5, (byte) 0xF5, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x07, (byte) 0x81, 0x2B, 0x05, (byte) 0xA5, (byte) 0xA5, 0x03, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x41, 0x08, 0x08, (byte) 0xA0, (byte) 0xA0, (byte) 0x83, (byte) 0x95, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x61, 0x12, 0x00, (byte) 0x93, (byte) 0x92, 0x74, 0x75, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x62, 0x21, 0x00, (byte) 0x84, (byte) 0x85, 0x34, 0x15, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x62, 0x0E, 0x00, (byte) 0xA1, (byte) 0xA0, 0x34, 0x15, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                },

                /* YM2413 tone by okazaki@angel.ne.jp (4/10/2004) */
                {
                        0x49, 0x4c, 0x4c, 0x32, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x61, 0x61, 0x1e, 0x17, (byte) 0xf0, 0x7f, 0x00, 0x17, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x13, 0x41, 0x16, 0x0e, (byte) 0xfd, (byte) 0xf4, 0x23, 0x23, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x03, 0x01, (byte) 0x9a, 0x04, (byte) 0xf3, (byte) 0xf3, 0x13, (byte) 0xf3, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x11, 0x61, 0x0e, 0x07, (byte) 0xfa, 0x64, 0x70, 0x17, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x22, 0x21, 0x1e, 0x06, (byte) 0xf0, 0x76, 0x00, 0x28, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x22, 0x16, 0x05, (byte) 0xf0, 0x71, 0x00, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x61, 0x1d, 0x07, (byte) 0x82, (byte) 0x80, 0x17, 0x17, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x23, 0x21, 0x2d, 0x16, (byte) 0x90, (byte) 0x90, 0x00, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x21, 0x1b, 0x06, 0x64, 0x65, 0x10, 0x17, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x21, 0x0b, 0x1a, (byte) 0x85, (byte) 0xa0, 0x70, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x23, 0x01, (byte) 0x83, 0x10, (byte) 0xff, (byte) 0xb4, 0x10, (byte) 0xf4, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        (byte) 0x97, (byte) 0xc1, 0x20, 0x07, (byte) 0xff, (byte) 0xf4, 0x22, 0x22, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x61, 0x00, 0x0c, 0x05, (byte) 0xc2, (byte) 0xf6, 0x40, 0x44, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x01, 0x56, 0x03, (byte) 0x94, (byte) 0xc2, 0x03, 0x12, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x01, (byte) 0x89, 0x03, (byte) 0xf1, (byte) 0xe4, (byte) 0xf0, 0x23, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x07, 0x21, 0x14, 0x00, (byte) 0xee, (byte) 0xf8, (byte) 0xff, (byte) 0xf8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x31, 0x00, 0x00, (byte) 0xf8, (byte) 0xf7, (byte) 0xf8, (byte) 0xf7, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x25, 0x11, 0x00, 0x00, (byte) 0xf8, (byte) 0xfa, (byte) 0xf8, 0x55, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                },

                /* YMF281B tone by Chabin (4/10/2004) */
                {
                        0x49, 0x4c, 0x4c, 0x32, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x62, 0x21, 0x1a, 0x07, (byte) 0xf0, 0x6f, 0x00, 0x16, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x10, 0x44, 0x02, (byte) 0xf6, (byte) 0xf4, 0x54, 0x23, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x03, 0x01, (byte) 0x97, 0x04, (byte) 0xf3, (byte) 0xf3, 0x13, (byte) 0xf3, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x61, 0x0a, 0x0f, (byte) 0xfa, 0x64, 0x70, 0x17, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x22, 0x21, 0x1e, 0x06, (byte) 0xf0, 0x76, 0x00, 0x28, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x61, (byte) 0x8a, 0x0e, (byte) 0xc0, 0x61, 0x00, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x21, 0x61, 0x1b, 0x07, (byte) 0x84, (byte) 0x80, 0x17, 0x17, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x37, 0x32, (byte) 0xc9, 0x01, 0x66, 0x64, 0x40, 0x28, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x21, 0x06, 0x03, (byte) 0xa5, 0x71, 0x51, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x06, 0x11, 0x5e, 0x07, (byte) 0xf3, (byte) 0xf2, (byte) 0xf6, 0x11, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x20, 0x18, 0x06, (byte) 0xf5, (byte) 0xf3, 0x20, 0x26, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        (byte) 0x97, 0x41, 0x20, 0x07, (byte) 0xff, (byte) 0xf4, 0x22, 0x22, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x65, 0x61, 0x15, 0x00, (byte) 0xf7, (byte) 0xf3, 0x16, (byte) 0xf4, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x31, 0x0e, 0x07, (byte) 0xfa, (byte) 0xf3, (byte) 0xff, (byte) 0xff, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x48, 0x61, 0x09, 0x07, (byte) 0xf1, (byte) 0x94, (byte) 0xf0, (byte) 0xf5, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x07, 0x21, 0x14, 0x00, (byte) 0xee, (byte) 0xf8, (byte) 0xff, (byte) 0xf8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x31, 0x00, 0x00, (byte) 0xf8, (byte) 0xf7, (byte) 0xf8, (byte) 0xf7, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x25, 0x11, 0x00, 0x00, (byte) 0xf8, (byte) 0xfa, (byte) 0xf8, 0x55, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                }
        };

        /* Size of Sintable ( 8 -- 18 can be used. 9 recommended.) */
        private static final int PG_BITS = 9;
        private static final int PG_WIDTH = (1 << PG_BITS);

        /* Phase increment counter */
        private static final int DP_BITS = 18;
        private static final int DP_WIDTH = (1 << DP_BITS);
        private static final int DP_BASE_BITS = (DP_BITS - PG_BITS);

        /* Dynamic range (Accuracy of sin table) */
        private static final int DB_BITS = 8;
        private static final double DB_STEP = (48.0 / (1 << DB_BITS));
        private static final int DB_MUTE = (1 << DB_BITS);

        /* Dynamic range of envelope */
        private static final double EG_STEP = 0.375;
        private static final int EG_BITS = 7;
        private static final int EG_MUTE = (1 << EG_BITS);

        /* Dynamic range of total level */
        private static final double TL_STEP = 0.75;
        private static final int TL_BITS = 6;
        private static final int TL_MUTE = (1 << TL_BITS);

        /* Dynamic range of sustain level */
        private static final double SL_STEP = 3.0;
        private static final int SL_BITS = 4;
        private static final int SL_MUTE = (1 << SL_BITS);

        private static int TL2EG(int d) {
            return (d * (int) (TL_STEP / EG_STEP));
        }

        private static int sl2eg(int d) {
            return (d * (int) (SL_STEP / EG_STEP));
        }

        // Bits for liner value
        private static final int DB2LIN_AMP_BITS = 8;
        private static final int SLOT_AMP_BITS = DB2LIN_AMP_BITS;

        // Bits for envelope phase incremental counter
        private static final int EG_DP_BITS = 22;
        private static final int EG_DP_WIDTH = 1 << EG_DP_BITS;

        // Bits for Pitch and Amp modulator
        private static final int PM_PG_BITS = 8;
        private static final int PM_PG_WIDTH = 1 << PM_PG_BITS;
        private static final int PM_DP_BITS = 16;
        private static final int PM_DP_WIDTH = 1 << PM_DP_BITS;
        private static final int AM_PG_BITS = 8;
        private static final int AM_PG_WIDTH = 1 << AM_PG_BITS;
        private static final int AM_DP_BITS = 16;
        private static final int AM_DP_WIDTH = 1 << AM_DP_BITS;

        // PM table is calcurated by PM_AMP * pow(2, PM_DEPTH * sin(x) / 1200)
        private static final int PM_AMP_BITS = 8;
        private static final int PM_AMP = (1 << PM_AMP_BITS);

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
            return (c & ((1 << b) - 1));
        }

        /** Expand x which is s bits to d bits. */
        private static int expandBits(int x, int s, int d) {
            return (x << (d - s));
        }

        /** Expand x which is s bits to d bits and fill expanded bits '1' */
        private static int expandBitsX(int x, int s, int d) {
            return ((x << (d - s)) | ((1 << (d - s)) - 1));
        }

        /** Adjust envelope speed which depends on sampling rate. */
        private int adjustRate(int x) {
            return (rate == 49716 ? x : (int) ((double) x * clk / 72 / rate + 0.5));
        }        /* added 0.5 to round the value*/

        private static int bit(int s, int b) {
            return (s >> b) & 1;
        }

        /** Input clock */
        private int clk = 844451141;
        /** Sampling rate */
        private int rate = 3354932;

        // WaveTable for each envelope amp
        private static final int[] fullSinTable = new int[PG_WIDTH];
        private static final int[] halfSinTable = new int[PG_WIDTH];

        private static final int[][] waveForm = new int[2][]; // { fullsintable, halfsintable };

        // LFO Table
        private final int[] pmTable = new int[PM_PG_WIDTH];
        private final int[] amTable = new int[AM_PG_WIDTH];

        // Phase delta for LFO
        private int pmDPhase;
        private int amDPhase;

        /** Liner to Log curve conversion table (for Attack rate). */
        private static final int[] AR_ADJUST_TABLE = new int[1 << EG_BITS];

        /** Empty Voice data */
        private static final Slot.Patch NullPatch = new Slot.Patch();

        /** Basic Voice data */
        private Slot.Patch[][][] defaultPatch = null;

        /** Definition of envelope mode */
        public enum EgState {
            READY {
                int calcEgDPhase(Slot slot) {
                    return 0;
                }
            }, ATTACK {
                int calcEgDPhase(Slot slot) {
                    return dPhaseARTable[slot.patch.ar][slot.rks];
                }
            }, DECAY {
                int calcEgDPhase(Slot slot) {
                    return dPhaseDRTable[slot.patch.dr][slot.rks];
                }
            }, SUSHOLD {
                int calcEgDPhase(Slot slot) {
                    return 0;
                }
            }, SUSTAIN {
                int calcEgDPhase(Slot slot) {
                    return dPhaseDRTable[slot.patch.rr][slot.rks];
                }
            }, RELEASE {
                int calcEgDPhase(Slot slot) {
                    if (slot.sustain != 0)
                        return dPhaseDRTable[5][slot.rks];
                    else if (slot.patch.eg != 0)
                        return dPhaseDRTable[slot.patch.rr][slot.rks];
                    else
                        return dPhaseDRTable[7][slot.rks];
                }
            }, SETTLE {
                int calcEgDPhase(Slot slot) {
                    return dPhaseDRTable[15][0];
                }
            }, FINISH {
                int calcEgDPhase(Slot slot) {
                    return 0;
                }
            };
            abstract int calcEgDPhase(Slot slot);
        }

        /** Phase incR table for Attack */
        private static final int[][] dPhaseARTable = {
                new int[16], new int[16], new int[16], new int[16], new int[16], new int[16], new int[16], new int[16],
                new int[16], new int[16], new int[16], new int[16], new int[16], new int[16], new int[16], new int[16]
        };

        /** Phase incR table for Decay and Release */
        private static final int[][] dPhaseDRTable = {
                new int[16], new int[16], new int[16], new int[16], new int[16], new int[16], new int[16], new int[16],
                new int[16], new int[16], new int[16], new int[16], new int[16], new int[16], new int[16], new int[16]
        };

        // KSL + TL Table
        private static int[][][][] tllTable;

        private static int[][][] rksTable;

        /** Phase incR table for PG */
        private static int[][][] dphaseTable;

        //
        // Create tables
        //

        /** Table for AR to LogCurve. */
        private static void makeAdjustTable() {
            AR_ADJUST_TABLE[0] = (1 << EG_BITS) - 1;
            for (int i = 1; i < (1 << EG_BITS); i++)
                AR_ADJUST_TABLE[i] = (int) ((double) (1 << EG_BITS) - 1 - ((1 << EG_BITS) - 1) * Math.log(i) / Math.log(127));
        }

        /** Liner(+0.0 - +1.0) to dB((1<<DB_BITS) - 1 -- 0) */
        private static int lin2db(double d) {
            if (d == 0)
                return (DB_MUTE - 1);
            else
                return Math.min(-(int) (20.0 * Math.log10(d) / DB_STEP), DB_MUTE - 1); // 0 -- 127
        }

        /** Sin Table */
        private static void makeSinTable() {
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
        }

        private static double saw(double phase) {
            if (phase <= Math.PI / 2)
                return phase * 2 / Math.PI;
            else if (phase <= Math.PI * 3 / 2)
                return 2.0 - (phase * 2 / Math.PI);
            else
                return -4.0 + phase * 2 / Math.PI;
        }

        /** Table for Pitch Modulator */
        private void makePmTable() {
            for (int i = 0; i < PM_PG_WIDTH; i++)
//                pmTable[i] = (e_int32) ((double) PM_AMP * pow (2, (double) PM_DEPTH * sin (2.0 * PI * i / PM_PG_WIDTH) / 1200));
                pmTable[i] = (int) ((double) PM_AMP * Math.pow(2, PM_DEPTH * saw(2.0 * Math.PI * i / PM_PG_WIDTH) / 1200));
        }

        /** Table for Amp Modulator */
        private void makeAmTable() {
            for (int i = 0; i < AM_PG_WIDTH; i++)
//                amTable[i] = (e_int32) ((double) AM_DEPTH / 2 / DB_STEP * (1.0 + sin (2.0 * PI * i / PM_PG_WIDTH)));
                amTable[i] = (int) (AM_DEPTH / 2 / DB_STEP * (1.0 + saw(2.0 * Math.PI * i / PM_PG_WIDTH)));
        }

        /** Phase increment counter table */
        private void makeDPhaseTable() {
            int[] mlTable = {
                    1, 1 * 2, 2 * 2, 3 * 2, 4 * 2, 5 * 2, 6 * 2, 7 * 2, 8 * 2, 9 * 2, 10 * 2, 10 * 2, 12 * 2, 12 * 2, 15 * 2, 15 * 2
            };

            for (int fnum = 0; fnum < 512; fnum++)
                for (int block = 0; block < 8; block++)
                    for (int ml = 0; ml < 16; ml++)
                        dphaseTable[fnum][block][ml] = adjustRate(((fnum * mlTable[ml]) << block) >> (20 - DP_BITS));
        }

        private static void makeTllTable() {
//#define dB2(x) ((x)*2)

            double[] klTable = {
                    0.000 * 2, 9.000 * 2, 12.000 * 2, 13.875 * 2, 15.000 * 2, 16.125 * 2, 16.875 * 2, 17.625 * 2,
                    18.000 * 2, 18.750 * 2, 19.125 * 2, 19.500 * 2, 19.875 * 2, 20.250 * 2, 20.625 * 2, 21.000 * 2
            };

            for (int fnum = 0; fnum < 16; fnum++)
                for (int block = 0; block < 8; block++)
                    for (int tl = 0; tl < 64; tl++)
                        for (int kl = 0; kl < 4; kl++) {
                            if (kl == 0) {
                                tllTable[fnum][block][tl][kl] = TL2EG(tl);
                            } else {
                                int tmp = (int) (klTable[fnum] - 3.000 * 2 * (7 - block));
                                if (tmp <= 0)
                                    tllTable[fnum][block][tl][kl] = TL2EG(tl);
                                else
                                    tllTable[fnum][block][tl][kl] = (int) ((tmp >> (3 - kl)) / EG_STEP) + TL2EG(tl);
                            }
                        }
        }

//#ifdef USE_SPEC_ENV_SPEED
//        static final double[][] attacktime = {
//          {0, 0, 0, 0},
//          {1730.15, 1400.60, 1153.43, 988.66},
//          {865.08, 700.30, 576.72, 494.33},
//          {432.54, 350.15, 288.36, 247.16},
//          {216.27, 175.07, 144.18, 123.58},
//          {108.13, 87.54, 72.09, 61.79},
//          {54.07, 43.77, 36.04, 30.90},
//          {27.03, 21.88, 18.02, 15.45},
//          {13.52, 10.94, 9.01, 7.72},
//          {6.76, 5.47, 4.51, 3.86},
//          {3.38, 2.74, 2.25, 1.93},
//          {1.69, 1.37, 1.13, 0.97},
//          {0.84, 0.70, 0.60, 0.54},
//          {0.50, 0.42, 0.34, 0.30},
//          {0.28, 0.22, 0.18, 0.14},
//          {0.00, 0.00, 0.00, 0.00}
//        };
//
//        static final double[][] decaytime = {
//          {0, 0, 0, 0},
//          {20926.60, 16807.20, 14006.00, 12028.60},
//          {10463.30, 8403.58, 7002.98, 6014.32},
//          {5231.64, 4201.79, 3501.49, 3007.16},
//          {2615.82, 2100.89, 1750.75, 1503.58},
//          {1307.91, 1050.45, 875.37, 751.79},
//          {653.95, 525.22, 437.69, 375.90},
//          {326.98, 262.61, 218.84, 187.95},
//          {163.49, 131.31, 109.42, 93.97},
//          {81.74, 65.65, 54.71, 46.99},
//          {40.87, 32.83, 27.36, 23.49},
//          {20.44, 16.41, 13.68, 11.75},
//          {10.22, 8.21, 6.84, 5.87},
//          {5.11, 4.10, 3.42, 2.94},
//          {2.55, 2.05, 1.71, 1.47},
//          {1.27, 1.27, 1.27, 1.27}
//        };
//#endif

        /** Rate Table for Attack */
        private void makeDPhaseARTable() {

            for (int ar = 0; ar < 16; ar++)
                for (int rks = 0; rks < 16; rks++) {
                    int rm = ar + (rks >> 2);
                    int rl = rks & 3;
                    if (rm > 15)
                        rm = 15;
                    switch (ar) {
                    case 0:
                        dPhaseARTable[ar][rks] = 0;
                        break;
                    case 15:
                        dPhaseARTable[ar][rks] = 0; // EG_DP_WIDTH
                        break;
                    default:
                        dPhaseARTable[ar][rks] = adjustRate(3 * (rl + 4) << (rm + 1));
                        break;
                    }
                }
        }

        /** Rate Table for Decay and Release */
        private void makeDPhaseDRTable() {
            for (int dr = 0; dr < 16; dr++)
                for (int rks = 0; rks < 16; rks++) {
                    int rm = dr + (rks >> 2);
                    int rl = rks & 3;
                    if (rm > 15)
                        rm = 15;
                    switch (dr) {
                    case 0:
                        dPhaseDRTable[dr][rks] = 0;
                        break;
                    default:
                        dPhaseDRTable[dr][rks] = adjustRate((rl + 4) << (rm - 1));
                        break;
                    }
                }
        }

        public static void makeRksTable() {
            for (int fnum8 = 0; fnum8 < 2; fnum8++)
                for (int block = 0; block < 8; block++)
                    for (int kr = 0; kr < 2; kr++) {
                        if (kr != 0)
                            rksTable[fnum8][block][kr] = (block << 1) + fnum8;
                        else
                            rksTable[fnum8][block][kr] = block >> 1;
                    }
        }

        private static void OPLL_dump2patch(byte[] dump, int type, int ptr, Slot.Patch[][][] patch) {
            patch[type][ptr][0].am = (dump[0 + ptr * 16] >> 7) & 1;
            patch[type][ptr][1].am = (dump[1 + ptr * 16] >> 7) & 1;
            patch[type][ptr][0].pm = ((dump[0 + ptr * 16] >> 6) & 1);
            patch[type][ptr][1].pm = ((dump[1 + ptr * 16] >> 6) & 1);
            patch[type][ptr][0].eg = ((dump[0 + ptr * 16] >> 5) & 1);
            patch[type][ptr][1].eg = ((dump[1 + ptr * 16] >> 5) & 1);
            patch[type][ptr][0].kr = ((dump[0 + ptr * 16] >> 4) & 1);
            patch[type][ptr][1].kr = ((dump[1 + ptr * 16] >> 4) & 1);
            patch[type][ptr][0].ml = ((dump[0 + ptr * 16]) & 15);
            patch[type][ptr][1].ml = ((dump[1 + ptr * 16]) & 15);
            patch[type][ptr][0].kl = ((dump[2 + ptr * 16] >> 6) & 3);
            patch[type][ptr][1].kl = ((dump[3 + ptr * 16] >> 6) & 3);
            patch[type][ptr][0].tl = ((dump[2 + ptr * 16]) & 63);
            patch[type][ptr][0].fb = ((dump[3 + ptr * 16]) & 7);
            patch[type][ptr][0].wf = ((dump[3 + ptr * 16] >> 3) & 1);
            patch[type][ptr][1].wf = ((dump[3 + ptr * 16] >> 4) & 1);
            patch[type][ptr][0].ar = ((dump[4 + ptr * 16] >> 4) & 15);
            patch[type][ptr][1].ar = ((dump[5 + ptr * 16] >> 4) & 15);
            patch[type][ptr][0].dr = ((dump[4 + ptr * 16]) & 15);
            patch[type][ptr][1].dr = ((dump[5 + ptr * 16]) & 15);
            patch[type][ptr][0].sl = ((dump[6 + ptr * 16] >> 4) & 15);
            patch[type][ptr][1].sl = ((dump[7 + ptr * 16] >> 4) & 15);
            patch[type][ptr][0].rr = ((dump[6 + ptr * 16]) & 15);
            patch[type][ptr][1].rr = ((dump[7 + ptr * 16]) & 15);
        }

        private static void OPLL_getDefaultPatch(int type, int num, Slot.Patch[][][] patch) {
            OPLL_dump2patch(default_inst[type], type, num, patch);
        }

        private void makeDefaultPatch() {
            for (int i = 0; i < OPLL_TONE_NUM; i++)
                for (int j = 0; j < 19; j++)
                    OPLL_getDefaultPatch(i, j, defaultPatch);

        }

        private static void OPLL_setPatch(Opll opll, byte[] dump) {
            Slot.Patch[][][] patch = new Slot.Patch[OPLL_TONE_NUM][][];
            patch[0] = new Slot.Patch[2][];
            for (int i = 0; i < 19; i++) {
                OPLL_dump2patch(dump, 0, i, patch);
                opll.patch[0][i] = patch[0][0][i];
                opll.patch[1][i] = patch[0][1][i];
            }
        }

        private static void OPLL_patch2dump(Slot.Patch[] patch, byte[] dump) {
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

        private void internalRefresh() {
            makeDPhaseTable();
            makeDPhaseARTable();
            makeDPhaseDRTable();
            pmDPhase = adjustRate((int) (PM_SPEED * PM_DP_WIDTH / (clk / 72d)));
            amDPhase = adjustRate((int) (AM_SPEED * AM_DP_WIDTH / (clk / 72d)));
        }

        private void makeTables(int c, int r) {
            if (c != clk) {
                clk = c;
                makePmTable();
                makeAmTable();
                makeAdjustTable();
                makeTllTable();
                makeRksTable();
                makeSinTable();
                makeDefaultPatch();
            }

            if (r != rate) {
                rate = r;
                internalRefresh();
            }
        }

        static {
            waveForm[0] = fullSinTable;
            waveForm[1] = halfSinTable;

            tllTable = new int[16][][][];
            for (int i = 0; i < 16; i++) {
                tllTable[i] = new int[8][][];
                for (int j = 0; j < 8; j++) {
                    tllTable[i][j] = new int[(1 << TL_BITS)][];
                    for (int k = 0; k < (1 << TL_BITS); k++) {
                        tllTable[i][j][k] = new int[4];
                    }
                }
            }

            rksTable = new int[2][][];
            for (int i = 0; i < 2; i++) {
                rksTable[i] = new int[8][];
                for (int j = 0; j < 8; j++) {
                    rksTable[i][j] = new int[2];
                }
            }

            dphaseTable = new int[512][][];
            for (int i = 0; i < 512; i++) {
                dphaseTable[i] = new int[8][];
                for (int j = 0; j < 8; j++) {
                    dphaseTable[i][j] = new int[16];
                }
            }
        }
    }

    public Emu2413() {
    }
}
