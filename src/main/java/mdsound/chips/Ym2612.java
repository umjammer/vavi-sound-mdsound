package mdsound.chips;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import dotnet4j.util.compat.TriConsumer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import static java.lang.System.getLogger;


/**
 * Ym2612.C : Ym2612 emulator
 * <p>
 * Almost constants are taken from the MAME core
 * <p>
 * This source is a part of Gens project
 * Written by Stéphane Dallongeville (gens@consolemul.com)
 * Copyright (c) 2002 by Stéphane Dallongeville
 * <p>
 * Modified by Maxim, Blargg
 * - removed non-Sound-related functionality
 * - added high-pass PCM filter
 * - added per-channel muting control
 * - made it use a context struct to allow multiple
 * instances
 */
public class Ym2612 {

    private static final Logger logger = getLogger(Ym2612.class.getName());

    //Just for tests stuff...
    //
//    private static final double COEF_MOD = 0.5;
//    private static final int MAX_OUT = ((int) (((1 << MAX_OUT_BITS) - 1) * COEF_MOD));
    private static final int OUTPUT_BITS = 15;

    /** Change it if you need to do long update */
    private static final int MAX_UPDATE_LENGTH = 0x100; // for in_vgm

    // Gens always uses 16 bits Sound (in 32 bits buffer) and do the conversion later if needed.

//    /** OUTPUT_BITS 15 is MAME's volume level */
//    private static final int OUTPUT_BITS = 15;

    /** DAC_SHIFT makes sure that FM and DAC volume has the same volume */
    private static final int DAC_SHIFT = OUTPUT_BITS - 9;

    private static final int ATTACK = 0;
    private static final int DECAY = 1;
    private static final int SUSTAIN = 2;
    private static final int RELEASE = 3;

    /** Sinus phase counter int part */
    private static final int SIN_HBITS = 12;
    /** Sinus phase counter float part (best setting) */
    private static final int SIN_LBITS = 26 - SIN_HBITS;

    /** Env phase counter int part */
    private static final int ENV_HBITS = 12;
    /** Env phase counter float part (best setting) */
    private static final int ENV_LBITS = 28 - ENV_HBITS;

    /** LFO phase counter int part */
    private static final int LFO_HBITS = 10;
    /** LFO phase counter float part (best setting) */
    private static final int LFO_LBITS = 28 - LFO_HBITS;

    private static final int SIN_LENGTH = 1 << SIN_HBITS;
    private static final int ENV_LENGTH = 1 << ENV_HBITS;
    private static final int LFO_LENGTH = 1 << LFO_HBITS;

    /** Env + TL scaling + LFO */
    private static final int TL_LENGTH = ENV_LENGTH * 3;

    private static final int SIN_MASK = SIN_LENGTH - 1;
    private static final int ENV_MASK = ENV_LENGTH - 1;
    private static final int LFO_MASK = LFO_LENGTH - 1;

    /** ENV_MAX = 96 dB */
    private static final double ENV_STEP = 96.0 / ENV_LENGTH;

    private static final int ENV_ATTACK = (ENV_LENGTH * 0) << ENV_LBITS;
    private static final int ENV_DECAY = (ENV_LENGTH * 1) << ENV_LBITS;
    private static final int ENV_END = (ENV_LENGTH * 2) << ENV_LBITS;

    private static final int MAX_OUT_BITS = SIN_HBITS + SIN_LBITS + 2; // Modulation = -4 <-. +4
    private static final int MAX_OUT = (1 << MAX_OUT_BITS) - 1;

    private static final int OUT_BITS = OUTPUT_BITS - 2;
    private static final int OUT_SHIFT = MAX_OUT_BITS - OUT_BITS;
    private static final int LIMIT_CH_OUT = (int) ((1 << OUT_BITS) * 1.5) - 1;

    private static final int PG_CUT_OFF = (int) (78.0 / ENV_STEP);
    private static final int ENV_CUT_OFF = (int) (68.0 / ENV_STEP);

    private static final int AR_RATE = 399128;
    private static final int DR_RATE = 5514396;

    /** FIXED (LFO_FMS_BASE gives something as 1) */
    private static final int LFO_FMS_LBITS = 9;
    private static final int LFO_FMS_BASE = (int) (0.05946309436 * 0.0338 * (double) (1 << LFO_FMS_LBITS));

    private static final int S0 = 0; // Stupid typo of the Ym2612
    private static final int S1 = 2;
    private static final int S2 = 1;
    private static final int S3 = 3;

    // Variable part

    /** SINUS TABLE (pointer on TL TABLE) */
    private static final int[] SIN_TAB = new int[SIN_LENGTH];
    /** TOTAL LEVEL TABLE (positive and minus) */
    private static final int[] TL_TAB = new int[TL_LENGTH * 2];
    /** ENV CURVE TABLE (attack & decay) */
    private static final int[] ENV_TAB = new int[2 * ENV_LENGTH + 8];

    /** Conversion from decay to attack phase */
    private static final int[] DECAY_TO_ATTACK = new int[ENV_LENGTH];

    /** Frequency step table */
    private final int[] fIncTab = new int[2048];

    /** Attack rate table */
    private final int[] arTab = new int[128];
    /** Decay rate table */
    private final int[] drTab = new int[96];
    /** Detune table */
    private int[][] dtTab = new int[][] {
            new int[32], new int[32], new int[32], new int[32],
            new int[32], new int[32], new int[32], new int[32]
    };
    /** Sustain level table */
    private static final int[] SL_TAB = new int[16];
    /** Table for NULL rate */
    private final int[] nullRate = new int[32];

    /** LFO AMS TABLE (adjusted for 11.8 dB) */
    private static final int[] LFO_ENV_TAB = new int[LFO_LENGTH];
    /** LFO FMS TABLE */
    private static final int[] LFO_FREQ_TAB = new int[LFO_LENGTH];

//    /** Interpolation table */
//    private int INTER_TAB[MAX_UPDATE_LENGTH];

    /** LFO step table */
    private final int[] lfoIncTab = new int[8];

    public interface UpdateChan extends TriConsumer<Channel, int[][], Integer> {
    }

    /** Update Channel functions pointer table */
    private final UpdateChan[] updateChan = new UpdateChan[] {
            this::updateChanAlgo0,
            this::updateChanAlgo1,
            this::updateChanAlgo2,
            this::updateChanAlgo3,
            this::updateChanAlgo4,
            this::updateChanAlgo5,
            this::updateChanAlgo6,
            this::updateChanAlgo7,

            this::updateChanAlgo0LFO,
            this::updateChanAlgo1LFO,
            this::updateChanAlgo2LFO,
            this::updateChanAlgo3LFO,
            this::updateChanAlgo4LFO,
            this::updateChanAlgo5LFO,
            this::updateChanAlgo6LFO,
            this::updateChanAlgo7LFO,

            this::updateChanAlgo0Int,
            this::updateChanAlgo1Int,
            this::updateChanAlgo2Int,
            this::updateChanAlgo3Int,
            this::updateChanAlgo4Int,
            this::updateChanAlgo5Int,
            this::updateChanAlgo6Int,
            this::updateChanAlgo7Int,

            this::updateChanAlgo0LFOInt,
            this::updateChanAlgo1LFOInt,
            this::updateChanAlgo2LFOInt,
            this::updateChanAlgo3LFOInt,
            this::updateChanAlgo4LFOInt,
            this::updateChanAlgo5LFOInt,
            this::updateChanAlgo6LFOInt,
            this::updateChanAlgo7LFOInt,

            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null
    };

    private static final int[] DT_DEF_TAB = new int[] {
            // FD = 0
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            // FD = 1
            0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2,
            2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6, 7, 8, 8, 8, 8,
            // FD = 2
            1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5,
            5, 6, 6, 7, 8, 8, 9, 10, 11, 12, 13, 14, 16, 16, 16, 16,
            // FD = 3
            2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6, 7,
            8, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 20, 22, 22, 22, 22
    };

    private static final int[] FKEY_TAB = new int[] {
            0, 0, 0, 0,
            0, 0, 0, 1,
            2, 3, 3, 3,
            3, 3, 3, 3
    };

    private static final int[] LFO_AMS_TAB = new int[] {
            31, 4, 1, 0
    };

    private static final int[] LFO_FMS_TAB = new int[] {
            LFO_FMS_BASE * 0, LFO_FMS_BASE * 1,
            LFO_FMS_BASE * 2, LFO_FMS_BASE * 3,
            LFO_FMS_BASE * 4, LFO_FMS_BASE * 6,
            LFO_FMS_BASE * 12, LFO_FMS_BASE * 24
    };

    /** Interpolation calculation */
    private long intCnt;

    private int YM2612_Enable;
    private int YM2612_Improv;

//    int DAC_Enable = 1;

    private int[][] YM_Buf = new int[2][];

    /** enable SSG-EG envelope (causes inaccurate Sound sometimes - rodrigo) */
    private static int enableSsgEg = 1; // TODO
    /** sometimes it creates a terrible noise */
    private int dacHighpassEnable = 1;

    private static final int[] vol = new int[2];

    private static class Channel {

        private void reset() {
            this.oldOutD = 0;
            this.outD = 0;
            this.left = -1;
            this.right = -1;
            this.algo = 0;
            this.fb = 31;
            this.fms = 0;
            this.ams = 0;

            for (int j = 0; j < 4; j++) {
                this.s0Out[j] = 0;
                this.fNum[j] = 0;
                this.fOct[j] = 0;
                this.kc[j] = 0;

                this.slots[j].reset();
            }
        }

        private void do0xB4(int data) {
            if ((data & 0x80) != 0) this.left = -1;
            else this.left = 0;

            if ((data & 0x40) != 0) this.right = -1;
            else this.right = 0;

            this.ams = LFO_AMS_TAB[(data >> 4) & 3];
            this.fms = LFO_FMS_TAB[data & 7];

            if (this.slots[0].amsOn != 0) this.slots[0].ams = this.ams;
            else this.slots[0].ams = 31;
            if (this.slots[1].amsOn != 0) this.slots[1].ams = this.ams;
            else this.slots[1].ams = 31;
            if (this.slots[2].amsOn != 0) this.slots[2].ams = this.ams;
            else this.slots[2].ams = 31;
            if (this.slots[3].amsOn != 0) this.slots[3].ams = this.ams;
            else this.slots[3].ams = 31;
        }

        private static class Slot {
            /** detune parameter */
            private int[] dt;
            /** "frequency multiple" parameter */
            private int mul;
            /** Total Level = volume when the envelope is at its highest */
            private int tl;
            /** Total Level adjusted */
            private int tll;
            /** Sustain Level (adjusted) = volume where the envelope ends its first phase of regression */
            private int sll;
            /** Key Scale Rate Shift = factor for taking into account KSL in envelope variations */
            private int ksrS;
            /**
             * Key Scale Rate = This value is calculated in relation to the current frequency, it will influence
             * the different parameters of the envelope such as attack, decay... as in reality!
             */
            private int ksr;
            /** SSG envelope type */
            private int seg;
            /** Attack Rate (pointer table) = Attack Rate (ar[ksr]) */
            private int[] ar;
            private int arIndex;
            /** Decay Rate (pointer table) = Rate for regression (dr[ksr]) */
            private int[] dr;
            private int drIndex;
            /** Austin Rate (pointer table) = Rate for maintenance (sur[ksr]) */
            private int[] sr;
            private int srIndex;
            /** Release Rate (pointer table) = Rate for release (rr[ksr]) */
            private int[] rr;
            private int rrIndex;
            /** Frequency Count = frequency counter to determine current amplitude (SIN[fInc >> 16]) */
            private int fCnt;
            /** frequency step = no incrementation of the frequency counter */
            private int fInc;
            /**
             * the larger the step, the higher the frequency
             * Envelope current phase = this variable allows you to know in which phase
             */
            private int eCurp;
            // of the envelope we find ourselves, for example attack phase or sustain phase...
            // depending on the value of this variable, we will call a function to update
            // the current envelope
            /** Envelope counter = the envelope counter lets you know where you are in the envelope */
            private int eCnt;
            /** Envelope step current */
            private int eInc;
            /** Envelope counter limit for next phase */
            private int eCmp;
            /**
             * Envelope step for Attack = no increment of the counter during the
             * attack phase this value is equal to {@code ar[ksr]}
             */
            private int eIncA;
            /**
             * Envelope step for Decay = no increment of the counter during the
             * regression phase this value is equal to {@code dr[ksr]}
             */
            private int eIncD;
            /**
             * Envelope step for Sustain = no increment of the counter during the
             * sustain phase this value is equal to {@code sr[ksr]}
             */
            private int eIncS;
            /**
             * Envelope step for Release = no increment of the counter during the
             * release phase this value is equal to {@code rr[ksr]}
             */
            private int eIncR;
            /**
             * pointer of SLOT output = pointer allowing to connect the output of this
             * slot to the input of another or directly to the output of the track
             */
            private int[] outP;
            /** input data of the slot = input data of the slot */
            private int inD;
            /** Change envelop mask. */
            private int chgEnM;
            /** AMS depth level of this SLOT = degree of modulation of the amplitude param the LFO */
            private int ams;
            /** AMS enable flag = AMS activation flag */
            private int amsOn;

            private void reset() {
                this.fCnt = 0;
                this.fInc = 0;
                this.eCnt = ENV_END; // Put it at the end of Decay phase...
                this.eInc = 0;
                this.eCmp = 0;
                this.eCurp = RELEASE;

                this.chgEnM = 0;
            }

            private void keyOn() {
                if (this.eCurp == RELEASE) { // is the key released?
                    this.fCnt = 0;

                    // Fix Ecco 2 splash Sound

                    this.eCnt = (DECAY_TO_ATTACK[ENV_TAB[this.eCnt >> ENV_LBITS]] + ENV_ATTACK) & this.chgEnM;
                    this.chgEnM = -1;

                    this.eInc = this.eIncA;
                    this.eCmp = ENV_DECAY;
                    this.eCurp = ATTACK;
                }
            }

            private void keyOff() {
                if (this.eCurp != RELEASE) { // is the key pressed?
                    if (this.eCnt < ENV_DECAY) { // attack phase ?
                        this.eCnt = (ENV_TAB[this.eCnt >> ENV_LBITS] << ENV_LBITS) + ENV_DECAY;
                    }

                    this.eInc = this.eIncR;
                    this.eCmp = ENV_END;
                    this.eCurp = RELEASE;
                }
            }

            private void do0x60(int data, int ams, int[] drTab, int[] nullRate) {
                if ((this.amsOn = (data & 0x80)) != 0) this.ams = ams;
                else this.ams = 31;

                if ((data &= 0x1f) > 0) {
                    this.dr = drTab;
                    this.drIndex = data << 1;
                } else {
                    this.dr = nullRate;
                    this.drIndex = 0;
                }

                this.eIncD = this.dr[this.drIndex + this.ksr];
                if (this.eCurp == DECAY) this.eInc = this.eIncD;
            }

            private interface EnvNextEvent extends Runnable {
            }

            /** Next Envelope phase functions pointer table */
            private final Channel.Slot.EnvNextEvent[] envNextEvents = new Channel.Slot.EnvNextEvent[] {
                    this::attack,
                    this::decay,
                    this::sustain,
                    this::release,
                    this::null_,
                    this::null_,
                    this::null_,
                    this::null_
            };

            private void calcFInc(int fInc, int kc) {
                int ksr;

                this.fInc = (fInc + this.dt[kc]) * this.mul;

                ksr = kc >> this.ksrS; // keycode attenuation

//logger.log(Level.DEBUG, String.format("fInc = %d  this.fInc = %d", fInc, this.fInc));

                if (this.ksr != ksr) { // if the KSR has changed then
                    // the different rates for the envelope are updated
                    this.ksr = ksr;

                    this.eIncA = this.ar[this.arIndex + ksr];
                    this.eIncD = this.dr[this.drIndex + ksr];
                    this.eIncS = this.sr[this.srIndex + ksr];
                    this.eIncR = this.rr[this.rrIndex + ksr];

                    if (this.eCurp == ATTACK) this.eInc = this.eIncA;
                    else if (this.eCurp == DECAY) this.eInc = this.eIncD;
                    else if (this.eCnt < ENV_END) {
                        if (this.eCurp == SUSTAIN) this.eInc = this.eIncS;
                        else if (this.eCurp == RELEASE) this.eInc = this.eIncR;
                    }

//logger.log(Level.DEBUG, "ksr = %.4X  eIncA = %.8X eIncD = %.8X eIncS = %.8X eIncR = %.8X", ksr, this.eIncA, this.eIncD, this.eIncS, this.eIncR);
                }
            }

            /** @see EnvNextEvent */
            private void null_() {
            }

            /** @see EnvNextEvent */
            private void attack() {
                // Verified with Gynoug even in HQ (explode SFX)
                this.eCnt = ENV_DECAY;

                this.eInc = this.eIncD;
                this.eCmp = this.sll;
                this.eCurp = DECAY;
            }

            /** @see EnvNextEvent */
            private void decay() {
                // Verified with Gynoug even in HQ (explode SFX)
                this.eCnt = this.sll;

                this.eInc = this.eIncS;
                this.eCmp = ENV_END;
                this.eCurp = SUSTAIN;
            }

            /** @see EnvNextEvent */
            private void sustain() {
                if (enableSsgEg != 0) {
                    if ((this.seg & 8) != 0) { // SSG envelope type
                        if ((this.seg & 1) != 0) {
                            this.eCnt = ENV_END;
                            this.eInc = 0;
                            this.eCmp = ENV_END + 1;
                        } else {
                            // re KEY ON

                            // this.fCnt = 0;
                            // this.chgEnM = 0xffff_ffff;

                            this.eCnt = 0;
                            this.eInc = this.eIncA;
                            this.eCmp = ENV_DECAY;
                            this.eCurp = ATTACK;
                        }

                        this.seg ^= (this.seg & 2) << 1;
                    } else {
                        this.eCnt = ENV_END;
                        this.eInc = 0;
                        this.eCmp = ENV_END + 1;
                    }
                } else {
                    this.eCnt = ENV_END;
                    this.eInc = 0;
                    this.eCmp = ENV_END + 1;
                }
            }

            /** @see EnvNextEvent */
            private void release() {
                this.eCnt = ENV_END;
                this.eInc = 0;
                this.eCmp = ENV_END + 1;
            }
        }

        /** old slot 0 outputs (for feedback) */
        private final int[] s0Out = new int[4];
        /** old track output (raw sound) */
        private int oldOutD;
        /** track output (raw sound) */
        private int outD;
        /** LEFT enable flag */
        private int left;
        /** RIGHT enable flag */
        private int right;
        /** Algorithm = determines the connections between operators */
        private int algo;
        /** shift count of self feed back = degree of "Feed-Back" of SLOT 1 (it is its only input) */
        private int fb;
        /** Frequency Modulation Sensitivity of channel = degree of frequency modulation on the channel param the LFO */
        private int fms;
        /** Amplitude Modulation Sensitivity of channel = degree of amplitude modulation on the channel param the LFO */
        private int ams;
        /** height frequency of the track (+ 3 for special mode) */
        private final int[] fNum = new int[4];
        /** octave of the track (+ 3 for special mode) */
        private final int[] fOct = new int[4];
        /** Key Code = value depending on frequency (see KSR for slots, KSR = KC >> KSR_S) */
        private final int[] kc = new int[4];
        /** four slot operators = the 4 slots of the track */
        private final Slot[] slots = new Slot[] {new Slot(), new Slot(), new Slot(), new Slot()};
        /** Frequency step recalculation flag */
        private int fFlag;
        /** Maxim: channel mute flag */
        private int mute;

        private int keyOn;
        private final int[] fmVol = new int[2];
        private final int[] fmSlotVol = new int[4];

        private void calcFInc(int[] fIncTab) {
            int fInc = fIncTab[this.fNum[0]] >> (7 - this.fOct[0]);
            int kc = this.kc[0];

            this.slots[0].calcFInc(fInc, kc);
            this.slots[1].calcFInc(fInc, kc);
            this.slots[2].calcFInc(fInc, kc);
            this.slots[3].calcFInc(fInc, kc);
        }

        private void keyOn(int nsl) {
            Slot sl = this.slots[nsl]; // we get the right slot pointer
            sl.keyOn();
        }

        private void keyOff(int nsl) {
            Slot sl = this.slots[nsl]; // we get the right slot pointer
            sl.keyOff();
        }

        private void updateEnv() {
            if ((this.slots[S0].eCnt += this.slots[S0].eInc) >= this.slots[S0].eCmp)
                this.slots[S0].envNextEvents[this.slots[S0].eCurp].run();

            if ((this.slots[S1].eCnt += this.slots[S1].eInc) >= this.slots[S1].eCmp)
                this.slots[S1].envNextEvents[this.slots[S1].eCurp].run();

            if ((this.slots[S2].eCnt += this.slots[S2].eInc) >= this.slots[S2].eCmp)
                this.slots[S2].envNextEvents[this.slots[S2].eCurp].run();

            if ((this.slots[S3].eCnt += this.slots[S3].eInc) >= this.slots[S3].eCmp)
                this.slots[S3].envNextEvents[this.slots[S3].eCurp].run();
        }

        private void doLimit() {
            if (this.outD > LIMIT_CH_OUT) this.outD = LIMIT_CH_OUT;
            else if (this.outD < -LIMIT_CH_OUT) this.outD = -LIMIT_CH_OUT;
        }

        private void updatePhase() {
            this.slots[S0].fCnt += this.slots[S0].fInc;
            this.slots[S1].fCnt += this.slots[S1].fInc;
            this.slots[S2].fCnt += this.slots[S2].fInc;
            this.slots[S3].fCnt += this.slots[S3].fInc;
        }
    }

    /** Ym2612 clock */
    private int clock;
    /** Sample Rate (11025/22050/44100) */
    private int rate;
    /** TimerBase calculation */
    private final int timerBase;
    /** Ym2612 Status (timer overflow) */
    private int status;
    /** address for writing to OPN A (emulator specific) */
    private int opnAAdr;
    /** address for writing to OPN B (emulator specific) */
    private int opnBAdr;
    /** LFO counter = frequency counter for the LFO */
    private int lfoCnt;
    /** LFO step counter = no increment of LFO frequency counter */
    private int lfoInc;
    // the larger the step, the higher the frequency
    /** timerA limit = value up to which timer A should count */
    private int timerA;
    private int timerAL;
    /** timerA counter = current value of Timer A */
    private int timerACnt;
    /** timerB limit = value up to which timer B should count */
    private int timerB;
    private int timerBL;
    /** timerB counter = current value of Timer B */
    private int timerBCnt;
    /** Current mode of tracks 3 and 6 (normal / special) */
    private int mode;
    /** DAC enabled flag */
    private int dac;
    /** DAC data */
    private int dacData;
    private int dacHighpass;
    /** Base frequency, calculated relative to the clock and sample rate */
    private double frequency;
    /** Interpolation Counter */
    private long interCnt;
    /** Interpolation Step */
    private final long interStep;
    /** The 6 channels of the Ym2612 */
    private final Channel[] channels = new Channel[] {new Channel(), new Channel(), new Channel(), new Channel(), new Channel(), new Channel()};
    /** Saving the values of all registers is optional */
    private final int[][] regs = new int[][] {new int[0x100], new int[0x100]};
//    /** This makes debugging easier for us */
//    private static final int MAX_UPDATE_LENGTH = 0x100;

    /** Temporary calculated LFO AMS (adjusted for 11.8 dB) * */
    private final int[] lfoEnvUp = new int[MAX_UPDATE_LENGTH];
    /** Temporary calculated LFO FMS * */
    private final int[] lfoFreqUp = new int[MAX_UPDATE_LENGTH];

    /** current phase calculation * */
    private int in0, in1, in2, in3;
    /** current envelope calculation* */
    private int en0, en1, en2, en3;

    private int dacMute;

    private void controlCsmKey() {
        this.channels[2].keyOn(0);
        this.channels[2].keyOn(1);
        this.channels[2].keyOn(2);
        this.channels[2].keyOn(3);
    }

    @SuppressFBWarnings("SF_SWITCH_NO_DEFAULT")
    private int setSlot(int adr, int data) {
        int nch;

        if ((nch = adr & 3) == 3) return 1;
        int nsl = (adr >> 2) & 3;

        if ((adr & 0x100) != 0) nch += 3;

        Channel ch = this.channels[nch];
        Channel.Slot slot = ch.slots[nsl];

        switch (adr & 0xf0) {
        case 0x30:
            if ((slot.mul = (data & 0x0f)) != 0) slot.mul <<= 1;
            else slot.mul = 1;

            slot.dt = dtTab[(data >> 4) & 7];

            ch.slots[0].fInc = -1;

//logger.log(Level.DEBUG, String.format("CHANNEL[%d], SLOT[%d] DTMUL = %.2X", nch, nsl, data & 0x7F));
            break;

        case 0x40:
            slot.tl = data & 0x7f;

            // SOR2 do a lot of TL adjustment and this fix R.Shinobi jump Sound...
            updateSpecial();

            slot.tll = slot.tl << (ENV_HBITS - 7);

//logger.log(Level.DEBUG, String.format("CHANNEL[%d], SLOT[%d] TL = %.2X", nch, nsl, slot.TL));
            break;

        case 0x50:
            slot.ksrS = 3 - (data >> 6);

            ch.slots[0].fInc = -1;

            if ((data &= 0x1f) != 0) {
                slot.ar = arTab;
                slot.arIndex = data << 1;
            } else {
                slot.ar = nullRate;
                slot.arIndex = 0;
            }

            slot.eIncA = slot.ar[slot.arIndex + slot.ksr];
            if (slot.eCurp == ATTACK) slot.eInc = slot.eIncA;

//logger.log(Level.DEBUG, String.format("CHANNEL[%d], SLOT[%d] AR = %.2X  EincA = %.6X", nch, nsl, data, slot.EincA));
            break;

        case 0x60:
            slot.do0x60(data, ch.ams, drTab, nullRate);

//logger.log(Level.DEBUG, String.format("CHANNEL[%d], SLOT[%d] AMS = %d  DR = %.2X  EincD = %.6X", nch, nsl, slot.AMSon, data, slot.EincD));
            break;

        case 0x70:
            if ((data &= 0x1F) > 0) {
                slot.sr = drTab;
                slot.srIndex = data << 1;
            } else {
                slot.sr = nullRate;
                slot.srIndex = 0;
            }

            slot.eIncS = slot.sr[slot.srIndex + slot.ksr];
            if ((slot.eCurp == SUSTAIN) && (slot.eCnt < ENV_END)) slot.eInc = slot.eIncS;

//logger.log(Level.DEBUG, String.format("CHANNEL[%d], SLOT[%d] SR = %.2X  EincS = %.6X", nch, nsl, data, slot.EincS));
            break;

        case 0x80:
            slot.sll = SL_TAB[data >> 4];

            slot.rr = drTab;
            slot.rrIndex = ((data & 0xf) << 2) + 2;

            slot.eIncR = slot.rr[slot.rrIndex + slot.ksr];
            if ((slot.eCurp == RELEASE) && (slot.eCnt < ENV_END)) slot.eInc = slot.eIncR;

//logger.log(Level.DEBUG, String.format("CHANNEL[%d], SLOT[%d] slot = %.8X", nch, nsl, slot.SLL));
//logger.log(Level.DEBUG, String.format("CHANNEL[%d], SLOT[%d] RR = %.2X  EincR = %.2X", nch, nsl, ((data & 0xF) << 1) | 2, slot.EincR));
            break;

        case 0x90:
            // SSG-EG envelope shapes :
            //
            // E  At Al H
            //
            // 1  0  0  0  \\\\
            //
            // 1  0  0  1  \___
            //
            // 1  0  1  0  \/\/
            //              ___
            // 1  0  1  1  \
            //
            // 1  1  0  0  ////
            //              ___
            // 1  1  0  1  /
            //
            // 1  1  1  0  /\/\
            //
            // 1  1  1  1  /___
            //
            // E  = SSG-EG enable
            // At = Start negate
            // Al = Altern
            // H  = Hold
            if (enableSsgEg != 0) {
                if ((data & 0x08) != 0) slot.seg = data & 0x0f;
                else slot.seg = 0;

//logger.log(Level.DEBUG, String.format("CHANNEL[%d], SLOT[%d] SSG-EG = %.2X", nch, nsl, data));
            }
            break;
        }

        return 0;
    }

    @SuppressFBWarnings("SF_SWITCH_NO_DEFAULT")
    private int setChannel(int adr, int data) {
        Channel ch;
        int num;

        if ((num = adr & 3) == 3) return 1;

        switch (adr & 0xfc) {
        case 0xa0:
            if ((adr & 0x100) != 0) num += 3;
            ch = this.channels[num];

            updateSpecial();

            ch.fNum[0] = (ch.fNum[0] & 0x700) + data;
            ch.kc[0] = (ch.fOct[0] << 2) | FKEY_TAB[ch.fNum[0] >> 7];

            ch.slots[0].fInc = -1;

//logger.log(Level.DEBUG, String.format("CHANNEL[%d] part1 FNUM = %d  KC = %d", num, ch.FNUM[0], ch.KC[0]));
            break;

        case 0xa4:
            if ((adr & 0x100) != 0) num += 3;
            ch = this.channels[num];

            updateSpecial();

            ch.fNum[0] = (ch.fNum[0] & 0x0ff) + ((data & 0x07) << 8);
            ch.fOct[0] = (data & 0x38) >> 3;
            ch.kc[0] = (ch.fOct[0] << 2) | FKEY_TAB[ch.fNum[0] >> 7];

            ch.slots[0].fInc = -1;

//logger.log(Level.DEBUG, String.format("CHANNEL[%d] part2 FNUM = %d  FOCT = %d  KC = %d", num, ch.FNUM[0], ch.FOCT[0], ch.KC[0]));
            break;

        case 0xa8:
            if (adr < 0x100) {
                num++;

                updateSpecial();

                this.channels[2].fNum[num] = (this.channels[2].fNum[num] & 0x700) + data;
                this.channels[2].kc[num] = (this.channels[2].fOct[num] << 2) | FKEY_TAB[this.channels[2].fNum[num] >> 7];

                this.channels[2].slots[0].fInc = -1;

//logger.log(Level.DEBUG, String.format("CHANNEL[2] part1 FNUM[%d] = %d  KC[%d] = %d", num, this.CHANNEL[2].FNUM[num], num, this.CHANNEL[2].KC[num]));
            }
            break;

        case 0xac:
            if (adr < 0x100) {
                num++;

                updateSpecial();

                this.channels[2].fNum[num] = (this.channels[2].fNum[num] & 0x0ff) + ((data & 0x07) << 8);
                this.channels[2].fOct[num] = (data & 0x38) >> 3;
                this.channels[2].kc[num] = (this.channels[2].fOct[num] << 2) | FKEY_TAB[this.channels[2].fNum[num] >> 7];

                this.channels[2].slots[0].fInc = -1;

//logger.log(Level.DEBUG, String.format("CHANNEL[2] part2 FNUM[%d] = %d  FOCT[%d] = %d  KC[%d] = %d", num, this.CHANNEL[2].FNUM[num], num, this.CHANNEL[2].FOCT[num], num, this.CHANNEL[2].KC[num]));
            }
            break;

        case 0xb0:
            if ((adr & 0x100) != 0) num += 3;
            ch = this.channels[num];

            if (ch.algo != (data & 7)) {
                // Fix VectorMan 2 heli Sound (level 1)
                updateSpecial();

                ch.algo = data & 7;

                ch.slots[0].chgEnM = 0;
                ch.slots[1].chgEnM = 0;
                ch.slots[2].chgEnM = 0;
                ch.slots[3].chgEnM = 0;
            }

            ch.fb = 9 - ((data >> 3) & 7); // Real thing ?

            //if(ch.FB = ((data >> 3) & 7)) ch.FB = 9 - ch.FB; // Thunder force 4 (music stage 8), Gynoug, Aladdin bug Sound...
            //else ch.FB = 31;

//logger.log(Level.DEBUG, String.format("CHANNEL[%d] ALGO = %d  FB = %d", num, ch.ALGO, ch.FB));
            break;

        case 0xb4:
            if ((adr & 0x100) != 0) num += 3;
            ch = this.channels[num];

            updateSpecial();

            ch.do0xB4(data);

//logger.log(Level.DEBUG, String.format("CHANNEL[%d] AMS = %d  FMS = %d", num, ch.AMS, ch.FMS));
            break;
        }

        return 0;
    }

    @SuppressFBWarnings("SF_SWITCH_NO_DEFAULT")
    private int setData(int adr, int data) {
        Channel ch;
        int nch;

        switch (adr) {
        case 0x22:
            if ((data & 8) != 0) {
                // Cool Spot music 1, LFO modified several time which
                // distort the Sound, have to check that on a real genesis...

                this.lfoInc = lfoIncTab[data & 7];

//logger.log(Level.DEBUG, String.format("\nLFO Enable, LFOinc = %.8X   %d", this.LFOinc, data & 7));
            } else {
                this.lfoInc = this.lfoCnt = 0;

//logger.log(Level.DEBUG, String.format("\nLFO Disable"));
            }
            break;

        case 0x24:
            this.timerA = (this.timerA & 0x003) | (data << 2);

            if (this.timerAL != (1024 - this.timerA) << 12) {
                this.timerACnt = this.timerAL = (1024 - this.timerA) << 12;

//logger.log(Level.DEBUG, String.format("Timer A Set = %.8X", this.TimerAcnt));
            }
            break;

        case 0x25:
            this.timerA = (this.timerA & 0x3fc) | (data & 3);

            if (this.timerAL != (1024 - this.timerA) << 12) {
                this.timerACnt = this.timerAL = (1024 - this.timerA) << 12;

//logger.log(Level.DEBUG, String.format("Timer A Set = %.8X", this.TimerAcnt));
            }
            break;

        case 0x26:
            this.timerB = data;

            if (this.timerBL != (256 - this.timerB) << (4 + 12)) {
                this.timerBCnt = this.timerBL = (256 - this.timerB) << (4 + 12);

//logger.log(Level.DEBUG, String.format("Timer B Set = %.8X", this.TimerBcnt));
            }
            break;

        case 0x27:
            // Miscellaneous settings
            // b7 = CSM MODE
            // b6 = 3 slot mode
            // b5 = reset b
            // b4 = reset a
            // b3 = timer enable b
            // b2 = timer enable a
            // b1 = load b
            // b0 = load a

            if (((data ^ this.mode) & 0x40) != 0) {
                // We changed the channel 2 mode, so recalculate phase step
                // This fix the punch Sound in Street of Rage 2

                updateSpecial();

                this.channels[2].slots[0].fInc = -1; // recalculate phase step
            }

            //if((data & 2) && (this.Status & 2)) this.TimerBcnt = this.TimerBL;
            //if((data & 1) && (this.Status & 1)) this.TimerAcnt = this.TimerAL;

            this.status &= (~data >> 4) & (data >> 2); // Reset Status

            this.mode = data;

//logger.log(Level.DEBUG, String.format("Mode reg = %.2X", data));
            break;

        case 0x28:
            if ((nch = data & 3) == 3) return 1;

            if ((data & 4) != 0) nch += 3;
            ch = this.channels[nch];

            updateSpecial();

            if ((data & 0x10) != 0) ch.keyOn(S0); // Press the key for slot 1
            else ch.keyOff(S0); // Release the key for slot 1
            if ((data & 0x20) != 0) ch.keyOn(S1); // Press the key for slot 3
            else ch.keyOff(S1); // Release the key for slot 3
            if ((data & 0x40) != 0) ch.keyOn(S2); // Press the key for slot 2
            else ch.keyOff(S2); // Release the key for slot 2
            if ((data & 0x80) != 0) ch.keyOn(S3); // Press the key for slot 4
            else ch.keyOff(S3); // Release the key for slot 4

    //logger.log(Level.DEBUG, String.format("CHANNEL[%d]  KEY %.1X", nch, ((data & 0xf0) >> 4));
            break;

        case 0x2A:
            this.dacData = (data - 0x80) << DAC_SHIFT; // DAC data
            break;

        case 0x2B:
            if ((this.dac ^ (data & 0x80)) != 0) updateSpecial();

            this.dac = data & 0x80; // DAC activation/deactivation
            break;
        }

        return 0;
    }

    // Gens

    private void updateSpecial() {
//        if (YM_Len && YM2612_Enable) {
//            YM2612_Update(YM_Buf, YM_Len);
//
//            YM_Buf[0] = Seg_L + Sound_Extrapol[VDP_Current_Line + 1][0];
//            YM_Buf[1] = Seg_R + Sound_Extrapol[VDP_Current_Line + 1][0];
//            YM_Len = 0;
//        }
    }

    private void getCurrentPhase(Channel ch) {
        this.in0 = ch.slots[S0].fCnt;
        this.in1 = ch.slots[S1].fCnt;
        this.in2 = ch.slots[S2].fCnt;
        this.in3 = ch.slots[S3].fCnt;
    }

    private void updatePhaseLfo(Channel ch, int i, /* ref */ int[] lfoFreq) {
        if ((lfoFreq[0] = (ch.fms * this.lfoFreqUp[i]) >> (LFO_HBITS - 1)) != 0) {
            ch.slots[S0].fCnt += ch.slots[S0].fInc + ((ch.slots[S0].fInc * lfoFreq[0]) >> LFO_FMS_LBITS);
            ch.slots[S1].fCnt += ch.slots[S1].fInc + ((ch.slots[S1].fInc * lfoFreq[0]) >> LFO_FMS_LBITS);
            ch.slots[S2].fCnt += ch.slots[S2].fInc + ((ch.slots[S2].fInc * lfoFreq[0]) >> LFO_FMS_LBITS);
            ch.slots[S3].fCnt += ch.slots[S3].fInc + ((ch.slots[S3].fInc * lfoFreq[0]) >> LFO_FMS_LBITS);
        } else {
            ch.slots[S0].fCnt += ch.slots[S0].fInc;
            ch.slots[S1].fCnt += ch.slots[S1].fInc;
            ch.slots[S2].fCnt += ch.slots[S2].fInc;
            ch.slots[S3].fCnt += ch.slots[S3].fInc;
        }
    }

    private void getCurrentEnv(Channel ch) {
        if ((ch.slots[S0].seg & 4) != 0) {
            if ((this.en0 = ENV_TAB[(ch.slots[S0].eCnt >> ENV_LBITS)] + ch.slots[S0].tll) > ENV_MASK)
                this.en0 = 0;
            else this.en0 ^= ENV_MASK;
        } else this.en0 = ENV_TAB[(ch.slots[S0].eCnt >> ENV_LBITS)] + ch.slots[S0].tll;
        if ((ch.slots[S1].seg & 4) != 0) {
            if ((this.en1 = ENV_TAB[(ch.slots[S1].eCnt >> ENV_LBITS)] + ch.slots[S1].tll) > ENV_MASK)
                this.en1 = 0;
            else this.en1 ^= ENV_MASK;
        } else this.en1 = ENV_TAB[(ch.slots[S1].eCnt >> ENV_LBITS)] + ch.slots[S1].tll;
        if ((ch.slots[S2].seg & 4) != 0) {
            if ((this.en2 = ENV_TAB[(ch.slots[S2].eCnt >> ENV_LBITS)] + ch.slots[S2].tll) > ENV_MASK)
                this.en2 = 0;
            else this.en2 ^= ENV_MASK;
        } else this.en2 = ENV_TAB[(ch.slots[S2].eCnt >> ENV_LBITS)] + ch.slots[S2].tll;
        if ((ch.slots[S3].seg & 4) != 0) {
            if ((this.en3 = ENV_TAB[(ch.slots[S3].eCnt >> ENV_LBITS)] + ch.slots[S3].tll) > ENV_MASK)
                this.en3 = 0;
            else this.en3 ^= ENV_MASK;
        } else this.en3 = ENV_TAB[(ch.slots[S3].eCnt >> ENV_LBITS)] + ch.slots[S3].tll;
    }

    private void getCurrentEnvLfo(Channel ch, int i, /* ref */ int[] lfoEnv) {
        lfoEnv[0] = this.lfoEnvUp[i];

        if ((ch.slots[S0].seg & 4) != 0) {
            if ((this.en0 = ENV_TAB[(ch.slots[S0].eCnt >> ENV_LBITS)] + ch.slots[S0].tll) > ENV_MASK)
                this.en0 = 0;
            else this.en0 = (this.en0 ^ ENV_MASK) + (lfoEnv[0] >> ch.slots[S0].ams);
        } else
            this.en0 = ENV_TAB[(ch.slots[S0].eCnt >> ENV_LBITS)] + ch.slots[S0].tll + (lfoEnv[0] >> ch.slots[S0].ams);
        if ((ch.slots[S1].seg & 4) != 0) {
            if ((this.en1 = ENV_TAB[(ch.slots[S1].eCnt >> ENV_LBITS)] + ch.slots[S1].tll) > ENV_MASK)
                this.en1 = 0;
            else this.en1 = (this.en1 ^ ENV_MASK) + (lfoEnv[0] >> ch.slots[S1].ams);
        } else
            this.en1 = ENV_TAB[(ch.slots[S1].eCnt >> ENV_LBITS)] + ch.slots[S1].tll + (lfoEnv[0] >> ch.slots[S1].ams);
        if ((ch.slots[S2].seg & 4) != 0) {
            if ((this.en2 = ENV_TAB[(ch.slots[S2].eCnt >> ENV_LBITS)] + ch.slots[S2].tll) > ENV_MASK)
                this.en2 = 0;
            else this.en2 = (this.en2 ^ ENV_MASK) + (lfoEnv[0] >> ch.slots[S2].ams);
        } else
            this.en2 = ENV_TAB[(ch.slots[S2].eCnt >> ENV_LBITS)] + ch.slots[S2].tll + (lfoEnv[0] >> ch.slots[S2].ams);
        if ((ch.slots[S3].seg & 4) != 0) {
            if ((this.en3 = ENV_TAB[(ch.slots[S3].eCnt >> ENV_LBITS)] + ch.slots[S3].tll) > ENV_MASK)
                this.en3 = 0;
            else this.en3 = (this.en3 ^ ENV_MASK) + (lfoEnv[0] >> ch.slots[S3].ams);
        } else
            this.en3 = ENV_TAB[(ch.slots[S3].eCnt >> ENV_LBITS)] + ch.slots[S3].tll + (lfoEnv[0] >> ch.slots[S3].ams);
    }

    private void doFeedback0(Channel ch) {
        this.in0 += (ch.s0Out[0] + ch.s0Out[1]) >> ch.fb;
        ch.s0Out[0] = TL_TAB[SIN_TAB[(this.in0 >> SIN_LBITS) & SIN_MASK] + this.en0];
    }

    private void doFeedback(Channel ch) {
        this.in0 += (ch.s0Out[0] + ch.s0Out[1]) >> ch.fb;
        ch.s0Out[1] = ch.s0Out[0];
        ch.s0Out[0] = TL_TAB[SIN_TAB[(this.in0 >> SIN_LBITS) & SIN_MASK] + this.en0];
    }

    private void doFeedback2(Channel ch) {
        this.in0 += (ch.s0Out[0] + ch.s0Out[1]) >> ch.fb;
        ch.s0Out[1] = ch.s0Out[0] >> 2;
        ch.s0Out[0] = TL_TAB[SIN_TAB[(this.in0 >> SIN_LBITS) & SIN_MASK] + this.en0];
    }

    private void doFeedback3(Channel ch) {
        this.in0 += (ch.s0Out[0] + ch.s0Out[1] + ch.s0Out[2] + ch.s0Out[3]) >> ch.fb;
        ch.s0Out[3] = ch.s0Out[2] >> 1;
        ch.s0Out[2] = ch.s0Out[1] >> 1;
        ch.s0Out[1] = ch.s0Out[0] >> 1;
        ch.s0Out[0] = TL_TAB[SIN_TAB[(this.in0 >> SIN_LBITS) & SIN_MASK] + this.en0];
    }

    private void doAlgo0(Channel ch) {
        doFeedback(ch);
        this.in1 += ch.s0Out[1];
        this.in2 += TL_TAB[SIN_TAB[(this.in1 >> SIN_LBITS) & SIN_MASK] + this.en1];
        this.in3 += TL_TAB[SIN_TAB[(this.in2 >> SIN_LBITS) & SIN_MASK] + this.en2];
        ch.outD = (TL_TAB[SIN_TAB[(this.in3 >> SIN_LBITS) & SIN_MASK] + this.en3]) >> OUT_SHIFT;
    }

    private void doAlgo1(Channel ch) {
        doFeedback(ch);
        this.in2 += ch.s0Out[1] + TL_TAB[SIN_TAB[(this.in1 >> SIN_LBITS) & SIN_MASK] + this.en1];
        this.in3 += TL_TAB[SIN_TAB[(this.in2 >> SIN_LBITS) & SIN_MASK] + this.en2];
        ch.outD = (TL_TAB[SIN_TAB[(this.in3 >> SIN_LBITS) & SIN_MASK] + this.en3]) >> OUT_SHIFT;
    }

    private void doAlgo2(Channel ch) {
        doFeedback(ch);
        this.in2 += TL_TAB[SIN_TAB[(this.in1 >> SIN_LBITS) & SIN_MASK] + this.en1];
        this.in3 += ch.s0Out[1] + TL_TAB[SIN_TAB[(this.in2 >> SIN_LBITS) & SIN_MASK] + this.en2];
        ch.outD = (TL_TAB[SIN_TAB[(this.in3 >> SIN_LBITS) & SIN_MASK] + this.en3]) >> OUT_SHIFT;
    }

    private void doAlgo3(Channel ch) {
        doFeedback(ch);
        this.in1 += ch.s0Out[1];
        this.in3 += TL_TAB[SIN_TAB[(this.in1 >> SIN_LBITS) & SIN_MASK] + this.en1] +
                TL_TAB[SIN_TAB[(this.in2 >> SIN_LBITS) & SIN_MASK] + this.en2];
        ch.outD = (TL_TAB[SIN_TAB[(this.in3 >> SIN_LBITS) & SIN_MASK] + this.en3]) >> OUT_SHIFT;
    }

    private void doAlgo4(Channel ch) {
        doFeedback(ch);
        this.in1 += ch.s0Out[1];
        this.in3 += TL_TAB[SIN_TAB[(this.in2 >> SIN_LBITS) & SIN_MASK] + this.en2];
        ch.outD = (TL_TAB[SIN_TAB[(this.in3 >> SIN_LBITS) & SIN_MASK] + this.en3] +
                TL_TAB[SIN_TAB[(this.in1 >> SIN_LBITS) & SIN_MASK] + this.en1]) >> OUT_SHIFT;
        ch.doLimit();
    }

    private void doAlgo5(Channel ch) {
        doFeedback(ch);
        this.in1 += ch.s0Out[1];
        this.in2 += ch.s0Out[1];
        this.in3 += ch.s0Out[1];
        ch.outD = (TL_TAB[SIN_TAB[(this.in3 >> SIN_LBITS) & SIN_MASK] + this.en3] +
                TL_TAB[SIN_TAB[(this.in1 >> SIN_LBITS) & SIN_MASK] + this.en1] +
                TL_TAB[SIN_TAB[(this.in2 >> SIN_LBITS) & SIN_MASK] + this.en2]) >> OUT_SHIFT;
        ch.doLimit();
    }

    private void doAlgo6(Channel ch) {
        doFeedback(ch);
        this.in1 += ch.s0Out[1];
        ch.outD = (TL_TAB[SIN_TAB[(this.in3 >> SIN_LBITS) & SIN_MASK] + this.en3] +
                TL_TAB[SIN_TAB[(this.in1 >> SIN_LBITS) & SIN_MASK] + this.en1] +
                TL_TAB[SIN_TAB[(this.in2 >> SIN_LBITS) & SIN_MASK] + this.en2]) >> OUT_SHIFT;
        ch.doLimit();
    }

    private void doAlgo7(Channel ch) {
        doFeedback(ch);
        ch.outD = (TL_TAB[SIN_TAB[(this.in3 >> SIN_LBITS) & SIN_MASK] + this.en3] +
                TL_TAB[SIN_TAB[(this.in1 >> SIN_LBITS) & SIN_MASK] + this.en1] +
                TL_TAB[SIN_TAB[(this.in2 >> SIN_LBITS) & SIN_MASK] + this.en2] + ch.s0Out[1]) >> OUT_SHIFT;
        ch.doLimit();
    }

    private static void doOutput(Channel ch, int[][] buf, int i) {
        buf[0][i] += ch.outD & ch.left;
        buf[1][i] += ch.outD & ch.right;
logger.log(Level.DEBUG, String.format("fm: %04x, %04x", buf[0][i], buf[1][i]));
        vol[0] = Math.max(vol[0], Math.abs(ch.outD & ch.left));
        vol[1] = Math.max(vol[1], Math.abs(ch.outD & ch.right));
    }

    private int doOutputInt0(Channel ch, int[][] buf, /* ref */ int i) {
        if (((intCnt += this.interStep) & 0x04000) != 0) {
            intCnt &= 0x3fff;
            buf[0][i] += ch.oldOutD & ch.left;
            buf[1][i] += ch.oldOutD & ch.right;
            vol[0] = Math.max(vol[0], Math.abs(ch.oldOutD & ch.left));
            vol[1] = Math.max(vol[1], Math.abs(ch.oldOutD & ch.right));
        } else i--;
        return i;
    }

    private int doOutputInt1(Channel ch, int[][] buf, /* ref */ int i) {
        ch.oldOutD = (ch.outD + ch.oldOutD) >> 1;
        if (((intCnt += this.interStep) & 0x04000) != 0) {
            intCnt &= 0x3fff;
            buf[0][i] += ch.oldOutD & ch.left;
            buf[1][i] += ch.oldOutD & ch.right;
            vol[0] = Math.max(vol[0], Math.abs(ch.oldOutD & ch.left));
            vol[1] = Math.max(vol[1], Math.abs(ch.oldOutD & ch.right));
        } else i--;
        return i;
    }

    private int doOutputInt2(Channel ch, int[][] buf, /* ref */ int i) {
        if (((intCnt += this.interStep) & 0x04000) != 0) {
            intCnt &= 0x3fff;
            ch.oldOutD = (ch.outD + ch.oldOutD) >> 1;
            buf[0][i] += ch.oldOutD & ch.left;
            buf[1][i] += ch.oldOutD & ch.right;
            vol[0] = Math.max(vol[0], Math.abs(ch.oldOutD & ch.left));
            vol[1] = Math.max(vol[1], Math.abs(ch.oldOutD & ch.right));
        } else i--;
        ch.oldOutD = ch.outD;
        return i;
    }

    private int doOutputInt(Channel ch, int[][] buf, /* ref */ int i) {
        if (((intCnt += this.interStep) & 0x04000) != 0) {
            intCnt &= 0x3fff;
            ch.oldOutD = (int) ((((intCnt ^ 0x3fff) * ch.outD) + (intCnt * ch.oldOutD)) >> 14);
            buf[0][i] += ch.oldOutD & ch.left;
            buf[1][i] += ch.oldOutD & ch.right;
            vol[0] = Math.max(vol[0], Math.abs(ch.oldOutD & ch.left));
            vol[1] = Math.max(vol[1], Math.abs(ch.oldOutD & ch.right));
        } else i--;
        ch.oldOutD = ch.outD;
        return i;
    }

    private void updateChanAlgo0(Channel ch, int[][] buf, int length) {
        if (ch.slots[S3].eCnt == ENV_END) return;

logger.log(Level.TRACE, String.format("Algo 0 len = %d", length));

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            ch.updatePhase();
            getCurrentEnv(ch);
            ch.updateEnv();
            doAlgo0(ch);
            doOutput(ch, buf, i);
        }
    }

    private void updateChanAlgo1(Channel ch, int[][] buf, int length) {
        if (ch.slots[S3].eCnt == ENV_END) return;

logger.log(Level.TRACE, String.format("Algo 1 len = %d", length));

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            ch.updatePhase();
            getCurrentEnv(ch);
            ch.updateEnv();
            doAlgo1(ch);
            doOutput(ch, buf, i);
        }
    }

    private void updateChanAlgo2(Channel ch, int[][] buf, int length) {
        if (ch.slots[S3].eCnt == ENV_END) return;

logger.log(Level.TRACE, String.format("Algo 2 len = %d", length));

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            ch.updatePhase();
            getCurrentEnv(ch);
            ch.updateEnv();
            doAlgo2(ch);
            doOutput(ch, buf, i);
        }
    }

    private void updateChanAlgo3(Channel ch, int[][] buf, int length) {
        if (ch.slots[S3].eCnt == ENV_END) return;

logger.log(Level.TRACE, String.format("Algo 3 len = %d", length));

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            ch.updatePhase();
            getCurrentEnv(ch);
            ch.updateEnv();
            doAlgo3(ch);
            doOutput(ch, buf, i);
        }
    }

    private void updateChanAlgo4(Channel ch, int[][] buf, int length) {
        if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END)) return;

logger.log(Level.TRACE, String.format("Algo 4 len = %d", length));

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            ch.updatePhase();
            getCurrentEnv(ch);
            ch.updateEnv();
            doAlgo4(ch);
            doOutput(ch, buf, i);
        }
    }

    private void updateChanAlgo5(Channel ch, int[][] buf, int length) {

        if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) &&
                (ch.slots[S3].eCnt == ENV_END)) return;

logger.log(Level.TRACE, String.format("Algo 5 len = %d", length));

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            ch.updatePhase();
            getCurrentEnv(ch);
            ch.updateEnv();
            doAlgo5(ch);
            doOutput(ch, buf, i);
        }
    }

    private void updateChanAlgo6(Channel ch, int[][] buf, int length) {

        if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) &&
                (ch.slots[S3].eCnt == ENV_END)) return;

logger.log(Level.TRACE, String.format("Algo 6 len = %d", length));

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            ch.updatePhase();
            getCurrentEnv(ch);
            ch.updateEnv();
            doAlgo6(ch);
            doOutput(ch, buf, i);
        }
    }

    private void updateChanAlgo7(Channel ch, int[][] buf, int length) {

        if ((ch.slots[S0].eCnt == ENV_END) && (ch.slots[S1].eCnt == ENV_END) &&
                (ch.slots[S2].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END))
            return;

logger.log(Level.TRACE, String.format("Algo 7 len = %d", length));

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            ch.updatePhase();
            getCurrentEnv(ch);
            ch.updateEnv();
            doAlgo7(ch);
            doOutput(ch, buf, i);
        }
    }

    private void updateChanAlgo0LFO(Channel ch, int[][] buf, int length) {
        int[] envLfo = new int[1], freqLfo = new int[1];

        if (ch.slots[S3].eCnt == ENV_END) return;

logger.log(Level.TRACE, String.format("Algo 0 LFO len = %d", length));

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            updatePhaseLfo(ch, i, freqLfo);
            getCurrentEnvLfo(ch, i, envLfo);
            ch.updateEnv();
            doAlgo0(ch);
            doOutput(ch, buf, i);
        }
    }

    private void updateChanAlgo1LFO(Channel ch, int[][] buf, int length) {
        int[] envLfo = new int[1], freqLfo = new int[1];

        if (ch.slots[S3].eCnt == ENV_END) return;

logger.log(Level.TRACE, String.format("Algo 1 LFO len = %d", length));

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            updatePhaseLfo(ch, i, freqLfo);
            getCurrentEnvLfo(ch, i, envLfo);
            ch.updateEnv();
            doAlgo1(ch);
            doOutput(ch, buf, i);
        }
    }

    private void updateChanAlgo2LFO(Channel ch, int[][] buf, int length) {
        int[] envLfo = new int[1], freqLfo = new int[1];

        if (ch.slots[S3].eCnt == ENV_END) return;

logger.log(Level.TRACE, String.format("Algo 2 LFO len = %d", length));

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            updatePhaseLfo(ch, i, freqLfo);
            getCurrentEnvLfo(ch, i, envLfo);
            ch.updateEnv();
            doAlgo2(ch);
            doOutput(ch, buf, i);
        }
    }

    private void updateChanAlgo3LFO(Channel ch, int[][] buf, int length) {
        int[] envLfo = new int[1], freqLfo = new int[1];

        if (ch.slots[S3].eCnt == ENV_END) return;

logger.log(Level.TRACE, String.format("Algo 3 LFO len = %d", length));

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            updatePhaseLfo(ch, i, freqLfo);
            getCurrentEnvLfo(ch, i, envLfo);
            ch.updateEnv();
            doAlgo3(ch);
            doOutput(ch, buf, i);
        }
    }

    private void updateChanAlgo4LFO(Channel ch, int[][] buf, int length) {
        int[] envLfo = new int[1], freqLfo = new int[1];

        if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END)) return;

logger.log(Level.TRACE, String.format("Algo 4 LFO len = %d", length));

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            updatePhaseLfo(ch, i, freqLfo);
            getCurrentEnvLfo(ch, i, envLfo);
            ch.updateEnv();
            doAlgo4(ch);
            doOutput(ch, buf, i);
        }
    }

    private void updateChanAlgo5LFO(Channel ch, int[][] buf, int length) {
        int[] envLfo = new int[1], freqLfo = new int[1];

        if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) &&
                (ch.slots[S3].eCnt == ENV_END)) return;

logger.log(Level.TRACE, String.format("Algo 5 LFO len = %d", length));

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            updatePhaseLfo(ch, i, freqLfo);
            getCurrentEnvLfo(ch, i, envLfo);
            ch.updateEnv();
            doAlgo5(ch);
            doOutput(ch, buf, i);
        }
    }

    private void updateChanAlgo6LFO(Channel ch, int[][] buf, int length) {
        int[] envLfo = new int[1], freqLfo = new int[1];

        if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END)) return;

logger.log(Level.TRACE, "Algo 6 LFO len = %d", length);

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            updatePhaseLfo(ch, i, freqLfo);
            getCurrentEnvLfo(ch, i, envLfo);
            ch.updateEnv();
            doAlgo6(ch);
            doOutput(ch, buf, i);
        }
    }

    private void updateChanAlgo7LFO(Channel ch, int[][] buf, int length) {
        int[] envLfo = new int[1], freqLfo = new int[1];

        if ((ch.slots[S0].eCnt == ENV_END) && (ch.slots[S1].eCnt == ENV_END) &&
                (ch.slots[S2].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END))
            return;

logger.log(Level.TRACE, String.format("Algo 7 LFO len = %d", length));

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            updatePhaseLfo(ch, i, freqLfo);
            getCurrentEnvLfo(ch, i, envLfo);
            ch.updateEnv();
            doAlgo7(ch);
            doOutput(ch, buf, i);
        }
    }

    private void updateChanAlgo0Int(Channel ch, int[][] buf, int length) {

        if (ch.slots[S3].eCnt == ENV_END) return;

logger.log(Level.TRACE, String.format("Algo 0 len = %d", length));

        intCnt = this.interCnt;

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            ch.updatePhase();
            getCurrentEnv(ch);
            ch.updateEnv();
            doAlgo0(ch);
            i = doOutputInt(ch, buf, i);
        }
    }

    private void updateChanAlgo1Int(Channel ch, int[][] buf, int length) {

        if (ch.slots[S3].eCnt == ENV_END) return;

logger.log(Level.TRACE, String.format("Algo 1 len = %d", length));

        intCnt = this.interCnt;

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            ch.updatePhase();
            getCurrentEnv(ch);
            ch.updateEnv();
            doAlgo1(ch);
            i = doOutputInt(ch, buf, i);
        }
    }

    private void updateChanAlgo2Int(Channel ch, int[][] buf, int length) {

        if (ch.slots[S3].eCnt == ENV_END) return;

logger.log(Level.TRACE, "Algo 2 len = %d", length);

        intCnt = this.interCnt;

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            ch.updatePhase();
            getCurrentEnv(ch);
            ch.updateEnv();
            doAlgo2(ch);
            i = doOutputInt(ch, buf, i);
        }
    }

    private void updateChanAlgo3Int(Channel ch, int[][] buf, int length) {

        if (ch.slots[S3].eCnt == ENV_END) return;

logger.log(Level.TRACE, String.format("Algo 3 len = %d", length));

        intCnt = this.interCnt;

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            ch.updatePhase();
            getCurrentEnv(ch);
            ch.updateEnv();
            doAlgo3(ch);
            i = doOutputInt(ch, buf, i);
        }
    }

    private void updateChanAlgo4Int(Channel ch, int[][] buf, int length) {

        if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END)) return;

logger.log(Level.TRACE, String.format("Algo 4 len = %d", length));

        intCnt = this.interCnt;

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            ch.updatePhase();
            getCurrentEnv(ch);
            ch.updateEnv();
            doAlgo4(ch);
            i = doOutputInt(ch, buf, i);
        }
    }

    private void updateChanAlgo5Int(Channel ch, int[][] buf, int length) {

        if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) &&
                (ch.slots[S3].eCnt == ENV_END)) return;

logger.log(Level.TRACE, String.format("Algo 5 len = %d", length));

        intCnt = this.interCnt;

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            ch.updatePhase();
            getCurrentEnv(ch);
            ch.updateEnv();
            doAlgo5(ch);
            i = doOutputInt(ch, buf, i);
        }
    }

    private void updateChanAlgo6Int(Channel ch, int[][] buf, int length) {

        if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) &&
                (ch.slots[S3].eCnt == ENV_END)) return;

logger.log(Level.TRACE, String.format("Algo 6 len = %d", length));

        intCnt = this.interCnt;

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            ch.updatePhase();
            getCurrentEnv(ch);
            ch.updateEnv();
            doAlgo6(ch);
            i = doOutputInt(ch, buf, i);
        }
    }

    private void updateChanAlgo7Int(Channel ch, int[][] buf, int length) {

        if ((ch.slots[S0].eCnt == ENV_END) && (ch.slots[S1].eCnt == ENV_END) &&
                (ch.slots[S2].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END))
            return;

logger.log(Level.TRACE, String.format("Algo 7 len = %d", length));

        intCnt = this.interCnt;

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            ch.updatePhase();
            getCurrentEnv(ch);
            ch.updateEnv();
            doAlgo7(ch);
            i = doOutputInt(ch, buf, i);
        }
    }

    private void updateChanAlgo0LFOInt(Channel ch, int[][] buf, int length) {
        int[] envLfo = new int[1], freqLfo = new int[1];

        if (ch.slots[S3].eCnt == ENV_END) return;

logger.log(Level.TRACE, String.format("Algo 0 LFO len = %d", length));

        intCnt = this.interCnt;

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            updatePhaseLfo(ch, i, freqLfo);
            getCurrentEnvLfo(ch, i, envLfo);
            ch.updateEnv();
            doAlgo0(ch);
            i = doOutputInt(ch, buf, i);
        }
    }

    private void updateChanAlgo1LFOInt(Channel ch, int[][] buf, int length) {
        int[] envLfo = new int[1], freqLfo = new int[1];

        if (ch.slots[S3].eCnt == ENV_END) return;

logger.log(Level.TRACE, String.format("Algo 1 LFO len = %d", length));

        intCnt = this.interCnt;

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            updatePhaseLfo(ch, i, freqLfo);
            getCurrentEnvLfo(ch, i, envLfo);
            ch.updateEnv();
            doAlgo1(ch);
            i = doOutputInt(ch, buf, i);
        }
    }

    private void updateChanAlgo2LFOInt(Channel ch, int[][] buf, int length) {
        int[] envLfo = new int[1], freqLfo = new int[1];

        if (ch.slots[S3].eCnt == ENV_END) return;

logger.log(Level.TRACE, String.format("Algo 2 LFO len = %d", length));

        intCnt = this.interCnt;

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            updatePhaseLfo(ch, i, freqLfo);
            getCurrentEnvLfo(ch, i, envLfo);
            ch.updateEnv();
            doAlgo2(ch);
            i = doOutputInt(ch, buf, i);
        }
    }

    private void updateChanAlgo3LFOInt(Channel ch, int[][] buf, int length) {
        int[] envLfo = new int[1], freqLfo = new int[1];

        if (ch.slots[S3].eCnt == ENV_END) return;

logger.log(Level.TRACE, String.format("Algo 3 LFO len = %d", length));

        intCnt = this.interCnt;

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            updatePhaseLfo(ch, i, freqLfo);
            getCurrentEnvLfo(ch, i, envLfo);
            ch.updateEnv();
            doAlgo3(ch);
            i = doOutputInt(ch, buf, i);
        }
    }

    private void updateChanAlgo4LFOInt(Channel ch, int[][] buf, int length) {
        int[] envLfo = new int[1], freqLfo = new int[1];

        if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END)) return;

logger.log(Level.TRACE, String.format("Algo 4 LFO len = %d", length));

        intCnt = this.interCnt;

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            updatePhaseLfo(ch, i, freqLfo);
            getCurrentEnvLfo(ch, i, envLfo);
            ch.updateEnv();
            doAlgo4(ch);
            i = doOutputInt(ch, buf, i);
        }
    }

    private void updateChanAlgo5LFOInt(Channel ch, int[][] buf, int length) {
        int[] envLfo = new int[1], freqLfo = new int[1];

        if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END)) return;

logger.log(Level.TRACE, String.format("Algo 5 LFO len = %d", length));

        intCnt = this.interCnt;

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            updatePhaseLfo(ch, i, freqLfo);
            getCurrentEnvLfo(ch, i, envLfo);
            ch.updateEnv();
            doAlgo5(ch);
            i = doOutputInt(ch, buf, i);
        }
    }

    private void updateChanAlgo6LFOInt(Channel ch, int[][] buf, int length) {
        int[] envLfo = new int[1], freqLfo = new int[1];

        if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END)) return;

logger.log(Level.TRACE, String.format("Algo 6 LFO len = %d", length));

        intCnt = this.interCnt;

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            updatePhaseLfo(ch, i, freqLfo);
            getCurrentEnvLfo(ch, i, envLfo);
            ch.updateEnv();
            doAlgo6(ch);
            i = doOutputInt(ch, buf, i);
        }
    }

    private void updateChanAlgo7LFOInt(Channel ch, int[][] buf, int length) {
        int[] envLfo = new int[1], freqLfo = new int[1];

        if ((ch.slots[S0].eCnt == ENV_END) && (ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END))
            return;

logger.log(Level.TRACE, String.format("Algo 7 LFO len = %d", length));

        intCnt = this.interCnt;

        for (int i = 0; i < length; i++) {
            getCurrentPhase(ch);
            updatePhaseLfo(ch, i, freqLfo);
            getCurrentEnvLfo(ch, i, envLfo);
            ch.updateEnv();
            doAlgo7(ch);
            i = doOutputInt(ch, buf, i);
        }
    }

    // Initializing the Ym2612 emulator
    public Ym2612(int clock, int rate, int interpolation) {

//logger.log(Level.TRACE, "Ym2612 logging");

        this.clock = clock;
        this.rate = rate;

        // 144 = 12 * (prescale * 2) = 12 * 6 * 2
        // prescale set to 6 by default

        this.frequency = ((double) this.clock / (double) this.rate) / 144.0;
        this.timerBase = (int) (this.frequency * 4096.0);

        if ((interpolation != 0) && (this.frequency > 1.0)) {
            this.interStep = (int) ((1.0 / this.frequency) * (double) (0x4000));
            this.interCnt = 0;

            // We recalculate rate and frequency after interpolation

            this.rate = this.clock / 144;
            this.frequency = 1.0;
        } else {
            this.interStep = 0x4000;
            this.interCnt = 0;
        }

        // ----

        // Frequency Step Table

        for (int i = 0; i < 2048; i++) {
            double x = (double) (i) * this.frequency;

            x *= 1 << (SIN_LBITS + SIN_HBITS - (21 - 7));

            x /= 2.0; // because MUL = value * 2

            fIncTab[i] = (int) x;
        }

        // Attack and Decay rate tables

        for (int i = 0; i < 4; i++) {
            arTab[i] = 0;
            drTab[i] = 0;
        }

        for (int i = 0; i < 60; i++) {
            double x = this.frequency;

            x *= 1.0 + ((i & 3) * 0.25);  // bits 0-1 : x1.00, x1.25, x1.50, x1.75
            x *= 1 << ((i >> 2));         // bits 2-5 : shift bits (x2^0 - x2^15)
            x *= ENV_LENGTH << ENV_LBITS; // we adjust for the ENV_TAB table

            arTab[i + 4] = (int) (x / AR_RATE);
            drTab[i + 4] = (int) (x / DR_RATE);
        }

        for (int i = 64; i < 96; i++) {
            arTab[i] = arTab[63];
            drTab[i] = drTab[63];

            nullRate[i - 64] = 0;
        }

        // Detune Table

        int j;
        for (int i = 0; i < 4; i++) {
            for (j = 0; j < 32; j++) {
                double x = (double) DT_DEF_TAB[(i << 5) + j] * this.frequency * (double) (1 << (SIN_LBITS + SIN_HBITS - 21));

                dtTab[i + 0][j] = (int) x;
                dtTab[i + 4][j] = (int) -x;
            }
        }

        // LFO Table

        j = (int) ((this.rate * this.interStep) / 0x4000);

        lfoIncTab[0] = (int) (3.98 * (double) (1 << (LFO_HBITS + LFO_LBITS)) / j);
        lfoIncTab[1] = (int) (5.56 * (double) (1 << (LFO_HBITS + LFO_LBITS)) / j);
        lfoIncTab[2] = (int) (6.02 * (double) (1 << (LFO_HBITS + LFO_LBITS)) / j);
        lfoIncTab[3] = (int) (6.37 * (double) (1 << (LFO_HBITS + LFO_LBITS)) / j);
        lfoIncTab[4] = (int) (6.88 * (double) (1 << (LFO_HBITS + LFO_LBITS)) / j);
        lfoIncTab[5] = (int) (9.63 * (double) (1 << (LFO_HBITS + LFO_LBITS)) / j);
        lfoIncTab[6] = (int) (48.1 * (double) (1 << (LFO_HBITS + LFO_LBITS)) / j);
        lfoIncTab[7] = (int) (72.2 * (double) (1 << (LFO_HBITS + LFO_LBITS)) / j);

        reset();
    }
//logger.log(Level.TRACE, String.format("Ym2612 frequency = %g rate = %d  interp step = %.8X", this.frequency, this.rate, this.interStep));

    static {
        // TL Table:
        // [0     -  4095] = +output  [4095  - ...] = +output overflow (fill with 0)
        // [12288 - 16383] = -output  [16384 - ...] = -output overflow (fill with 0)

        for (int i = 0; i < TL_LENGTH; i++) {
            if (i >= PG_CUT_OFF) { // Ym2612 cut off Sound after 78 dB (14 bits output ?)
                TL_TAB[TL_LENGTH + i] = TL_TAB[i] = 0;
            } else {
                double x = MAX_OUT; // Max output
                x /= Math.pow(10, (ENV_STEP * i) / 20); // Decibel -> Voltage

                TL_TAB[i] = (int) x;
                TL_TAB[TL_LENGTH + i] = -TL_TAB[i];
            }

//logger.log(Level.DEBUG, String.format("TL_TAB[%d] = %.8X    TL_TAB[%d] = %.8X", i, TL_TAB[i], TL_LENGTH + i, TL_TAB[TL_LENGTH + i]));
        }

        // SIN table:
        // SIN_TAB[x][y] = sin(x) * y;
        // x = phase and y = volume

        SIN_TAB[0] = SIN_TAB[SIN_LENGTH / 2] = PG_CUT_OFF;

        for (int i = 1; i <= SIN_LENGTH / 4; i++) {
            double x = Math.sin(2.0 * Math.PI * (double) (i) / (double) (SIN_LENGTH)); // Sinus
            x = 20 * Math.log10(1 / x); // convert to dB

            int j = (int) (x / ENV_STEP); // Get TL range

            if (j > PG_CUT_OFF) j = PG_CUT_OFF;

            SIN_TAB[i] = SIN_TAB[(SIN_LENGTH / 2) - i] = j;
            SIN_TAB[(SIN_LENGTH / 2) + i] = SIN_TAB[SIN_LENGTH - i] = TL_LENGTH + j;

//logger.log(Level.DEBUG, String.format("SIN[%d][0] = %.8X    SIN[%d][0] = %.8X    SIN[%d][0] = %.8X    SIN[%d][0] = %.8X", i, SIN_TAB[i][0], (SIN_LENGTH / 2) - i, SIN_TAB[(SIN_LENGTH / 2) - i][0], (SIN_LENGTH / 2) + i, SIN_TAB[(SIN_LENGTH / 2) + i][0], SIN_LENGTH - i, SIN_TAB[SIN_LENGTH - i][0]));
        }

        // LFO table (LFO wav):

        for (int i = 0; i < LFO_LENGTH; i++) {
            double x = Math.sin(2.0 * Math.PI * (double) i / (double) (LFO_LENGTH)); // Sinus
            x += 1.0;
            x /= 2.0; // positive only
            x *= 11.8 / ENV_STEP; // adjusted to MAX envelope modulation

            LFO_ENV_TAB[i] = (int) x;

            x = Math.sin(2.0 * Math.PI * (double) i / (double) LFO_LENGTH); // Sinus
            x *= (1 << (LFO_HBITS - 1)) - 1;

            LFO_FREQ_TAB[i] = (int) x;

//logger.log(Level.DEBUG, String.format("LFO[%d] = %.8X", i, LFO_ENV_TAB[i]));
        }

//logger.log(Level.DEBUG, "n");

        // Envelope Table:
        // ENV_TAB[0] . ENV_TAB[ENV_LENGTH - 1] = attack curve
        // ENV_TAB[ENV_LENGTH] . ENV_TAB[2 * ENV_LENGTH - 1] = decay curve

        for (int i = 0; i < ENV_LENGTH; i++) {
            // Attack curve (x^8 - music level 2 Vectorman 2)
            double x = Math.pow(((double) ((ENV_LENGTH - 1) - i) / (double) (ENV_LENGTH)), 8);
            x *= ENV_LENGTH;

            ENV_TAB[i] = (int) x;

            // Decay curve (just linear)
            x = Math.pow(((double) i / (double) ENV_LENGTH), 1);
            x *= ENV_LENGTH;

            ENV_TAB[ENV_LENGTH + i] = (int) x;

//logger.log(Level.DEBUG, String.format("ATTACK[%d] = %d   DECAY[%d] = %d", i, ENV_TAB[i], i, ENV_TAB[ENV_LENGTH + i]));
        }

        ENV_TAB[ENV_END >> ENV_LBITS] = ENV_LENGTH - 1; // for the stopped state

        // Table for Attack -> Decay and Decay -> Attack conversion

        for (int i = 0, j = ENV_LENGTH - 1; i < ENV_LENGTH; i++) {
            while (j > 0 && (ENV_TAB[j] < i)) j--;

            DECAY_TO_ATTACK[i] = j << ENV_LBITS;
        }

        // Table for Sustain Level

        for (int i = 0; i < 15; i++) {
            double x = i * 3; // 3 and not 6 (Mickey Mania first music for test)
            x /= ENV_STEP;

            int j = (int) x;
            j <<= ENV_LBITS;

            SL_TAB[i] = j + ENV_DECAY;
        }

        int j = ENV_LENGTH - 1; // special case : volume off
        j <<= ENV_LBITS;
        SL_TAB[15] = j + ENV_DECAY;
    }

    public int end() {
        return 0;
    }

    public int reset() {
//logger.log(Level.DEBUG, "Starting resetting Ym2612 ...");

        this.lfoCnt = 0;
        this.timerA = 0;
        this.timerAL = 0;
        this.timerACnt = 0;
        this.timerB = 0;
        this.timerBL = 0;
        this.timerBCnt = 0;
        this.dac = 0;
        this.dacData = 0;
        this.dacHighpass = 0;

        this.status = 0;

        this.opnAAdr = 0;
        this.opnBAdr = 0;
        this.interCnt = 0;

        for (int i = 0; i < 6; i++) {
            this.channels[i].reset();
        }

        for (int i = 0; i < 0x100; i++) {
            this.regs[0][i] = -1;
            this.regs[1][i] = -1;
        }

        for (int i = 0xb6; i >= 0xb4; i--) {
            write(0, i);
            write(1, 0xc0);
            write(2, i);
            write(3, 0xc0);
        }

        for (int i = 0xb2; i >= 0x22; i--) {
            write(0, i);
            write(1, 0);
            write(2, i);
            write(3, 0);
        }

        write(0, 0x2a);
        write(1, 0x80);

//logger.log(Level.DEBUG, "Finishing resetting Ym2612 ...");

        return 0;
    }

    public int read() {
//        static int cnt = 0;
//
//        if (cnt++ == 50) {
//            cnt = 0;
//            return this.Status;
//        } else return this.Status | 0x80;
        return this.status;
    }

    public int write(int adr, int data) {
        data &= 0xff;
        adr &= 0x03;
logger.log(Level.TRACE, String.format("fm%d: a: %02x, d: %02x", adr / 2, adr, data));

        switch (adr) {
        case 0:
            this.opnAAdr = data;
            break;

        case 1:
            // Trivial optimisation
            if (this.opnAAdr == 0x2a) {
                this.dacData = (data - 0x80) << DAC_SHIFT;
                return 0;
            }

            int d = this.opnAAdr & 0xf0;

            if (d >= 0x30) {
                if (this.regs[0][this.opnAAdr] == data) return 2;
                this.regs[0][this.opnAAdr] = data;

//if (GYM_Dumping) Update_GYM_Dump(1, this.OPNAadr, data);

                if (d < 0xa0) { // SLOT
                    setSlot(this.opnAAdr, data);
                } else { // CHANNEL
                    setChannel(this.opnAAdr, data);
                }
            } else { // Ym2612
                this.regs[0][this.opnAAdr] = data;

//if ((GYM_Dumping) && ((this.OPNAadr == 0x22) || (this.OPNAadr == 0x27) || (this.OPNAadr == 0x28)))
// Update_GYM_Dump(1, this.OPNAadr, data);

                setData(this.opnAAdr, data);
            }
            break;

        case 2:
            this.opnBAdr = data & 0xff;
            break;

        case 3:
            d = this.opnBAdr & 0xf0;

            if (d >= 0x30) {
                if (this.regs[1][this.opnBAdr] == data) return 2;
                this.regs[1][this.opnBAdr] = data;

// if (GYM_Dumping) Update_GYM_Dump(2, this.OPNBadr, data);

                if (d < 0xa0) { // SLOT
                    setSlot(this.opnBAdr + 0x100, data);
                } else { // CHANNEL
                    setChannel(this.opnBAdr + 0x100, data);
                }
            } else return 1;
            break;
        }

        return 0;
    }

    // mds
    public int getMute() {
        int result = 0;
        for (int i = 0; i < 6; ++i) {
            result |= this.channels[i].mute << i;
        }
        result |= this.dacMute << 6;
        //result &= !enableSsgEg;
        return result;
    }

    // mds
    public void setMute(int val) {
        for (int i = 0; i < 6; ++i) {
            this.channels[i].mute = (val >> i) & 1;
        }
        this.dacMute = (val >> 6) & 1;
        //enableSsgEg = !(val & 1);
    }

    // bit0:DAC_Highpass_Enable
    // bit1:SSGEG_Enable
    public void setOptions(int flags) {
        dacHighpassEnable = (flags >> 0) & 0x01;
        enableSsgEg = (flags >> 1) & 0x01;
    }

    public static void clearBuffer(int[][] buffer, int length) {
        // the MAME core does this before updating,
        // but the Gens core does this before mixing
        int[] bufL = buffer[0];
        int[] bufR = buffer[1];

        for (int i = 0; i < length; i++) {
            bufL[i] = 0x0000;
            bufR[i] = 0x0000;
        }
    }

    public void update(int[][] buf, int length) {
        int algoType;

//logger.log(Level.TRACE, "Starting generating Sound...");

        // update frequency counter steps if they have been modified

        if (this.channels[0].slots[0].fInc == -1) this.channels[0].calcFInc(this.fIncTab);
        if (this.channels[1].slots[0].fInc == -1) this.channels[1].calcFInc(this.fIncTab);
        if (this.channels[2].slots[0].fInc == -1) {
            if ((this.mode & 0x40) != 0) {
                this.channels[2].slots[S0].calcFInc(fIncTab[this.channels[2].fNum[2]] >> (7 - this.channels[2].fOct[2]), this.channels[2].kc[2]);
                this.channels[2].slots[S1].calcFInc(fIncTab[this.channels[2].fNum[3]] >> (7 - this.channels[2].fOct[3]), this.channels[2].kc[3]);
                this.channels[2].slots[S2].calcFInc(fIncTab[this.channels[2].fNum[1]] >> (7 - this.channels[2].fOct[1]), this.channels[2].kc[1]);
                this.channels[2].slots[S3].calcFInc(fIncTab[this.channels[2].fNum[0]] >> (7 - this.channels[2].fOct[0]), this.channels[2].kc[0]);
            } else {
                this.channels[2].calcFInc(this.fIncTab);
            }
        }
        if (this.channels[3].slots[0].fInc == -1) this.channels[3].calcFInc(this.fIncTab);
        if (this.channels[4].slots[0].fInc == -1) this.channels[4].calcFInc(this.fIncTab);
        if (this.channels[5].slots[0].fInc == -1) this.channels[5].calcFInc(this.fIncTab);

        // TODO vavi algoType is always 0
        if ((this.interStep & 0x04000) != 0) algoType = 0;
        else algoType = 16;

        if (this.lfoInc != 0) {
            // Precalculate LFO wav

            for (int i = 0; i < length; i++) {
                int j = ((this.lfoCnt += this.lfoInc) >> LFO_LBITS) & LFO_MASK;

                this.lfoEnvUp[i] = LFO_ENV_TAB[j];
                this.lfoFreqUp[i] = LFO_FREQ_TAB[j];

logger.log(Level.TRACE, String.format("LFO_ENV_UP[%d] = %d   LFO_FREQ_UP[%d] = %d", i, this.lfoEnvUp[i], i, this.lfoFreqUp[i]));
            }

            algoType |= 8;
        }

        if (this.channels[0].mute == 0) {
            vol[0] = 0;
            vol[1] = 0;
            updateChan[this.channels[0].algo + algoType].accept(this.channels[0], buf, length);
            this.channels[0].fmVol[0] = vol[0];
            this.channels[0].fmVol[1] = vol[1];
        }
        if (this.channels[1].mute == 0) {
            vol[0] = 0;
            vol[1] = 0;
            updateChan[this.channels[1].algo + algoType].accept(this.channels[1], buf, length);
            this.channels[1].fmVol[0] = vol[0];
            this.channels[1].fmVol[1] = vol[1];
        }
        if (this.channels[2].mute == 0) {
            vol[0] = 0;
            vol[1] = 0;
            updateChan[this.channels[2].algo + algoType].accept(this.channels[2], buf, length);
            this.channels[2].fmVol[0] = vol[0];
            this.channels[2].fmVol[1] = vol[1];
        }
        this.channels[2].fmSlotVol[0] = TL_TAB[SIN_TAB[(this.in0 >> SIN_LBITS) & SIN_MASK] + this.en0] >> OUT_SHIFT;
        this.channels[2].fmSlotVol[1] = TL_TAB[SIN_TAB[(this.in1 >> SIN_LBITS) & SIN_MASK] + this.en1] >> OUT_SHIFT;
        this.channels[2].fmSlotVol[2] = TL_TAB[SIN_TAB[(this.in2 >> SIN_LBITS) & SIN_MASK] + this.en2] >> OUT_SHIFT;
        this.channels[2].fmSlotVol[3] = TL_TAB[SIN_TAB[(this.in3 >> SIN_LBITS) & SIN_MASK] + this.en3] >> OUT_SHIFT;

        if (this.channels[3].mute == 0) {
            vol[0] = 0;
            vol[1] = 0;
            updateChan[this.channels[3].algo + algoType].accept(this.channels[3], buf, length);
            this.channels[3].fmVol[0] = vol[0];
            this.channels[3].fmVol[1] = vol[1];
        }
        if (this.channels[4].mute == 0) {
            vol[0] = 0;
            vol[1] = 0;
            updateChan[this.channels[4].algo + algoType].accept(this.channels[4], buf, length);
            this.channels[4].fmVol[0] = vol[0];
            this.channels[4].fmVol[1] = vol[1];
        }
        if (this.channels[5].mute == 0 && (this.dac == 0)) {
            vol[0] = 0;
            vol[1] = 0;
            updateChan[this.channels[5].algo + algoType].accept(this.channels[5], buf, length);
            this.channels[5].fmVol[0] = vol[0];
            this.channels[5].fmVol[1] = vol[1];
        }

        this.interCnt = intCnt;

//logger.log(Level.TRACE, "Finishing generating Sound...");
    }

//    /** higher values reduce highpass on DAC */
//    enum highpass {
//        fract =15,
//        highpass_shift =9
//    }

    public void updateDacAndTimers(int[][] buffer, int length) {

        if (this.dac != 0 && this.dacData != 0 && this.dacMute == 0) {

            int[] bufL = buffer[0];
            int[] bufR = buffer[1];

            for (int i = 0; i < length; i++) {
                int dac = (this.dacData << 15) - this.dacHighpass;
                if (dacHighpassEnable != 0) // else it's left at 0 and doesn't affect the Sound
                    this.dacHighpass += dac >> 9;
                dac >>= 15;
                bufL[i] += dac & this.channels[5].left;
                bufR[i] += dac & this.channels[5].right;
                this.channels[5].fmVol[0] = dac & this.channels[5].left;
                this.channels[5].fmVol[1] = dac & this.channels[5].right;
            }
        }

        int i = this.timerBase * length;

        if ((this.mode & 1) != 0) { // Timer A ON ?
            if ((this.timerACnt -= i) <= 0) {
                this.status |= (this.mode & 0x04) >> 2;
                this.timerACnt += this.timerAL;

//logger.log(Level.DEBUG, "Counter A overflow");

                if ((this.mode & 0x80) != 0) controlCsmKey();
            }
        }

        if ((this.mode & 2) != 0) { // Timer B ON ?
            if ((this.timerBCnt -= i) <= 0) {
                this.status |= (this.mode & 0x08) >> 2;
                this.timerBCnt += this.timerBL;

//logger.log(Level.DEBUG, "Counter B overflow");
            }
        }
    }

    // for mds
    public int[][] getRegisters() {
        return regs;
    }

    // for mds
    public int[] keyStatuses() {
        int[] keys = new int[channels.length];
        for (int i = 0; i < keys.length; i++)
            keys[i] = channels[i].keyOn;
        return keys;
    }
}
