package mdsound;


import dotnet4j.util.compat.TriConsumer;


public class Ym2612 extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 2;
    private static final int DefaultFMClockValue = 7670454;

    public Ym2612Context[] chips = new Ym2612Context[] {null, null};

    @Override
    public String getName() {
        return "Ym2612";
    }

    @Override
    public String getShortName() {
        return "OPN2";
    }

    public Ym2612() {
        //0..Main
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }


    @Override
    public int start(byte chipId, int clock) {
        Ym2612Context ym2612 = new Ym2612Context(DefaultFMClockValue, clock, 0);
        chips[chipId] = ym2612;
        reset(chipId);

        return clock;
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        if ((clockValue == 0)) return 0;
        if (clock == 0) {
            clock = DefaultFMClockValue;
        }

        Ym2612Context ym2612 = new Ym2612Context(clockValue, clock, clockValue);
        chips[chipId] = ym2612;
        reset(chipId);

        // 動作オプション設定
        if (option != null) {
            if (option.length > 0) {
                if (option[0] instanceof Integer) {
                    int optFlags = (int) option[0];
                    // bit0:DAC_Highpass_Enable
                    // bit1:SSGEG_Enable
                    ym2612.setOptions(optFlags & 0x3);
                }
            }
        }

        return clock;
    }

    @Override
    public void stop(byte chipId) {
        chips[chipId] = null;
    }

    @Override
    public void reset(byte chipId) {
        Ym2612Context chip = chips[chipId];
        if (chip == null) return;

        chip.reset();
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        Ym2612Context chip = chips[chipId];
        if (chip == null) return;

        chip.YM2612_Update(outputs, samples);
        chip.YM2612_DacAndTimers_Update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }


    @Override
    public int write(byte chipId, int port, int adr, int data) {
        Ym2612Context chip = chips[chipId];
        if (chip == null) return 0;

        return chip.write((byte) adr, (byte) data);
    }

    public void YM2612_SetMute(byte chipId, int v) {
        Ym2612Context chip = chips[chipId];
        if (chip == null) return;

        chip.setMute(v);
    }

    /**
     * Ym2612.C : Ym2612 emulator
     * <p>
     * Almost constantes are taken from the MAME core
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
    public static class Ym2612Context {

        //Just for tests stuff...
        //
        //#define COEF_MOD       0.5
        //#define MAX_OUT        ((int) (((1 << MAX_OUT_BITS) - 1) * COEF_MOD))
        private static final int OUTPUT_BITS = 15;

        /** Change it if you need to do long update */
        private static final int MAX_UPDATE_LENGHT = 0x100; // for in_vgm

        // Gens always uses 16 bits Sound (in 32 bits buffer) and do the convertion later if needed.

        /* OUTPUT_BITS 15 is MAME's volume level */
//        private static final int OUTPUT_BITS = 15;

        /** DAC_SHIFT makes sure that FM and DAC volume has the same volume */
        private static final int DAC_SHIFT = OUTPUT_BITS - 9;

        private static final int ATTACK = 0;
        private static final int DECAY = 1;
        private static final int SUBSTAIN = 2;
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

        private static final int SIN_LENGHT = 1 << SIN_HBITS;
        private static final int ENV_LENGHT = 1 << ENV_HBITS;
        private static final int LFO_LENGHT = 1 << LFO_HBITS;

        /** Env + TL scaling + LFO */
        private static final int TL_LENGHT = ENV_LENGHT * 3;

        private static final int SIN_MASK = SIN_LENGHT - 1;
        private static final int ENV_MASK = ENV_LENGHT - 1;
        private static final int LFO_MASK = LFO_LENGHT - 1;

        /** ENV_MAX = 96 dB */
        private static final double ENV_STEP = 96.0 / ENV_LENGHT;

        private static final int ENV_ATTACK = (ENV_LENGHT * 0) << ENV_LBITS;
        private static final int ENV_DECAY = (ENV_LENGHT * 1) << ENV_LBITS;
        private static final int ENV_END = (ENV_LENGHT * 2) << ENV_LBITS;

        private static final int MAX_OUT_BITS = SIN_HBITS + SIN_LBITS + 2; // Modulation = -4 <-. +4
        private static final int MAX_OUT = (1 << MAX_OUT_BITS) - 1;

        private static final int OUT_BITS = OUTPUT_BITS - 2;
        private static final int OUT_SHIFT = MAX_OUT_BITS - OUT_BITS;
        private static final int LIMIT_CH_OUT = (int) ((1 << OUT_BITS) * 1.5) - 1;

        private static final int PG_CUT_OFF = (int) (78.0 / ENV_STEP);
        private static final int ENV_CUT_OFF = (int) (68.0 / ENV_STEP);

        private static final int AR_RATE = 399128;
        private static final int DR_RATE = 5514396;

        /** FIXED (LFO_FMS_BASE gives somethink as 1) */
        private static final int LFO_FMS_LBITS = 9;
        private static final int LFO_FMS_BASE = (int) (0.05946309436 * 0.0338 * (double) (1 << LFO_FMS_LBITS));

        private static final int S0 = 0; // Stupid typo of the Ym2612
        private static final int S1 = 2;
        private static final int S2 = 1;
        private static final int S3 = 3;

        // Partie variables

        /** SINUS TABLE (pointer on TL TABLE) */
        static int[] SIN_TAB = new int[SIN_LENGHT];
        /** TOTAL LEVEL TABLE (positif and minus) */
        static int[] TL_TAB = new int[TL_LENGHT * 2];
        /** ENV CURVE TABLE (attack & decay) */
        private static int[] ENV_TAB = new int[2 * ENV_LENGHT + 8];

        /** Conversion from decay to attack phase */
        static int[] DECAY_TO_ATTACK = new int[ENV_LENGHT];

        /** Frequency step table */
        int[] fincTab = new int[2048];

        /** Attack rate table */
        int[] arTab = new int[128];
        /** Decay rate table */
        int[] drTab = new int[96];
        /** Detune table */
        int[][] dtTab = new int[][] {
                new int[32], new int[32], new int[32], new int[32],
                new int[32], new int[32], new int[32], new int[32]
        };
        /** Substain level table */
        static int[] SL_TAB = new int[16];
        /** Table for NULL rate */
        int[] nullRate = new int[32];

        /** LFO AMS TABLE (adjusted for 11.8 dB) */
        static int[] LFO_ENV_TAB = new int[LFO_LENGHT];
        /** LFO FMS TABLE */
        static int[] LFO_FREQ_TAB = new int[LFO_LENGHT];

        /** Interpolation table */
        // int INTER_TAB[MAX_UPDATE_LENGHT];

        /** LFO step table */
        int[] lfoIncTab = new int[8];

        interface UpdateChan extends TriConsumer<Channel, int[][], Integer> {
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
        private static int intCnt;

        private int YM2612_Enable;
        private int YM2612_Improv;

        //int DAC_Enable = 1;

        private int[][] YM_Buf = new int[2][];

        /** enable SSG-EG envelope (causes inacurate Sound sometimes - rodrigo) */
        private static int enableSsgEg = 1;
        /** sometimes it creates a terrible noise */
        private int dacHighpassEnable = 1;

        public static int[] vol = new int[2];

        public static class Channel {

            public void reset() {
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
                    this.fnum[j] = 0;
                    this.foct[j] = 0;
                    this.kc[j] = 0;

                    this.slots[j].reset();
                }
            }

            public void do0xB4(byte data) {
                if ((data & 0x80) != 0) this.left = -1;
                else this.left = 0;

                if ((data & 0x40) != 0) this.right = -1;
                else this.right = 0;

                this.ams = LFO_AMS_TAB[(data >> 4) & 3];
                this.fms = LFO_FMS_TAB[data & 7];

                if (this.slots[0].amSOn != 0) this.slots[0].ams = this.ams;
                else this.slots[0].ams = 31;
                if (this.slots[1].amSOn != 0) this.slots[1].ams = this.ams;
                else this.slots[1].ams = 31;
                if (this.slots[2].amSOn != 0) this.slots[2].ams = this.ams;
                else this.slots[2].ams = 31;
                if (this.slots[3].amSOn != 0) this.slots[3].ams = this.ams;
                else this.slots[3].ams = 31;
            }

            public static class Slot {
                /** paramètre detune */
                public int[] dt;
                /** paramètre "multiple de fréquence" */
                public int mul;
                /** Total Level = volume lorsque l'enveloppe est au plus haut */
                public int tl;
                /** Total Level ajusted */
                public int tll;
                /** Sustin Level (ajusted) = volume où l'enveloppe termine sa première phase de régression */
                public int sll;
                /** Key Scale Rate Shift = facteur de prise en compte du KSL dans la variations de l'enveloppe */
                public int ksrS;
                /** Key Scale Rate = cette valeur est calculée par rapport à la fréquence actuelle, elle va influer
                 * sur les différents paramètres de l'enveloppe comme l'attaque, le decay ...  comme dans la réalité ! */
                public int ksr;
                /** Type enveloppe SSG */
                public int SEG;
                /** Attack Rate (table pointeur) = Taux d'attaque (AR[KSR]) */
                public int[] ar;
                public int arIndex;
                /** Decay Rate (table pointeur) = Taux pour la régression (DR[KSR]) */
                public int[] dr;
                public int drIndex;
                /** Sustin Rate (table pointeur) = Taux pour le maintien (SR[KSR]) */
                public int[] sr;
                public int srIndex;
                /** Release Rate (table pointeur) = Taux pour le relâchement (RR[KSR]) */
                public int[] rr;
                public int rrIndex;
                /** Frequency Count = compteur-fréquence pour déterminer l'amplitude actuelle (SIN[Finc >> 16]) */
                public int fCnt;
                /** frequency step = pas d'incrémentation du compteur-fréquence */
                public int fInc;
                /** plus le pas est grand, plus la fréquence est aïgu (ou haute)
                 /** Envelope current phase = cette variable permet de savoir dans quelle phase */
                public int eCurp;
                // de l'enveloppe on se trouve, par exemple phase d'attaque ou phase de maintenue ...
                // en fonction de la valeur de cette variable, on va appeler une fonction permettant
                // de mettre à jour l'enveloppe courante.
                /** Envelope counter = le compteur-enveloppe permet de savoir où l'on se trouve dans l'enveloppe */
                public int eCnt;
                /** Envelope step courant */
                public int eInc;
                /** Envelope counter limite pour la prochaine phase */
                public int eCmp;
                /** Envelope step for Attack = pas d'incrémentation du compteur durant la phase d'attaque
                 * cette valeur est égal à AR[KSR] */
                public int eIncA;
                /** Envelope step for Decay = pas d'incrémentation du compteur durant la phase de regression
                 cette valeur est égal à DR[KSR] */
                public int eIncD;
                /** Envelope step for Sustain = pas d'incrémentation du compteur durant la phase de maintenue
                 * cette valeur est égal à SR[KSR] */
                public int eIncS;
                /** Envelope step for Release = pas d'incrémentation du compteur durant la phase de relâchement
                  cette valeur est égal à RR[KSR] */
                public int eIncR;
                /** pointeur of SLOT output = pointeur permettant de connecter la sortie de ce slot à l'entrée
                  d'un autre ou carrement à la sortie de la voie */
                public int[] OUTp;
                /** input data of the slot = données en entrée du slot */
                public int INd;
                /** Change envelop mask. */
                public int chgEnM;
                /** AMS depth level of this SLOT = degré de modulation de l'amplitude par le LFO */
                public int ams;
                /** AMS enable flag = drapeau d'activation de l'AMS */
                public int amSOn;

                public void reset() {
                    this.fCnt = 0;
                    this.fInc = 0;
                    this.eCnt = ENV_END; // Put it at the end of Decay phase...
                    this.eInc = 0;
                    this.eCmp = 0;
                    this.eCurp = RELEASE;

                    this.chgEnM = 0;
                }

                public void keyOn() {
                    if (this.eCurp == RELEASE) { // la touche est-elle relâchée ?
                        this.fCnt = 0;

                        // Fix Ecco 2 splash Sound

                        this.eCnt = (DECAY_TO_ATTACK[ENV_TAB[this.eCnt >> ENV_LBITS]] + ENV_ATTACK) & this.chgEnM;
                        this.chgEnM = -1;

                        this.eInc = this.eIncA;
                        this.eCmp = ENV_DECAY;
                        this.eCurp = ATTACK;
                    }
                }

                public void keyOff() {
                    if (this.eCurp != RELEASE) { // la touche est-elle appuyée ?
                        if (this.eCnt < ENV_DECAY) { // attack phase ?
                            this.eCnt = (ENV_TAB[this.eCnt >> ENV_LBITS] << ENV_LBITS) + ENV_DECAY;
                        }

                        this.eInc = this.eIncR;
                        this.eCmp = ENV_END;
                        this.eCurp = RELEASE;
                    }
                }

                public void do0x60(byte data, int ams, int[] drTab, int[] nullRate) {
                    if ((this.amSOn = (data & 0x80)) != 0) this.ams = ams;
                    else this.ams = 31;

                    if ((data &= 0x1F) > 0) {
                        this.dr = drTab;
                        this.drIndex = data << 1;
                    } else {
                        this.dr = nullRate;
                        this.drIndex = 0;
                    }

                    this.eIncD = this.dr[this.drIndex + this.ksr];
                    if (this.eCurp == DECAY) this.eInc = this.eIncD;
                }

                interface EnvNextEvent extends Runnable {
                }

                /** Next Enveloppe phase functions pointer table */
                private final EnvNextEvent[] envNextEvents = new EnvNextEvent[] {
                        this::attack,
                        this::decay,
                        this::substain,
                        this::release,
                        this::null_,
                        this::null_,
                        this::null_,
                        this::null_
                };

                private void calcFInc(int fInc, int kc) {
                    int ksr;

                    this.fInc = (fInc + this.dt[kc]) * this.mul;

                    ksr = kc >> this.ksrS; // keycode atténuation

                    //Debug.printf(debug_file, "FINC = %d  this.Finc = %d\n", fInc, this.Finc);

                    if (this.ksr != ksr) { // si le KSR a changé alors
                        // les différents taux pour l'enveloppe sont mis à jour
                        this.ksr = ksr;

                        this.eIncA = this.ar[this.arIndex + ksr];
                        this.eIncD = this.dr[this.drIndex + ksr];
                        this.eIncS = this.sr[this.srIndex + ksr];
                        this.eIncR = this.rr[this.rrIndex + ksr];

                        if (this.eCurp == ATTACK) this.eInc = this.eIncA;
                        else if (this.eCurp == DECAY) this.eInc = this.eIncD;
                        else if (this.eCnt < ENV_END) {
                            if (this.eCurp == SUBSTAIN) this.eInc = this.eIncS;
                            else if (this.eCurp == RELEASE) this.eInc = this.eIncR;
                        }

                        //  Debug.printf(debug_file, "KSR = %.4X  EincA = %.8X EincD = %.8X EincS = %.8X EincR = %.8X\n", ksr, this.EincA, this.EincD, this.EincS, this.EincR);
                    }
                }

                private void null_() {
                }

                private void attack() {
                    // Verified with Gynoug even in HQ (explode SFX)
                    this.eCnt = ENV_DECAY;

                    this.eInc = this.eIncD;
                    this.eCmp = this.sll;
                    this.eCurp = DECAY;
                }

                private void decay() {
                    // Verified with Gynoug even in HQ (explode SFX)
                    this.eCnt = this.sll;

                    this.eInc = this.eIncS;
                    this.eCmp = ENV_END;
                    this.eCurp = SUBSTAIN;
                }

                private void substain() {
                    if (enableSsgEg != 0) {
                        if ((this.SEG & 8) != 0) { // SSG envelope type
                            if ((this.SEG & 1) != 0) {
                                this.eCnt = ENV_END;
                                this.eInc = 0;
                                this.eCmp = ENV_END + 1;
                            } else {
                                // re KEY ON

                                // this.Fcnt = 0;
                                // this.ChgEnM = 0xFFFFFFFF;

                                this.eCnt = 0;
                                this.eInc = this.eIncA;
                                this.eCmp = ENV_DECAY;
                                this.eCurp = ATTACK;
                            }

                            this.SEG ^= (this.SEG & 2) << 1;
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

                private void release() {
                    this.eCnt = ENV_END;
                    this.eInc = 0;
                    this.eCmp = ENV_END + 1;
                }
            }

            /** anciennes sorties slot 0 (pour le feed back) */
            public int[] s0Out = new int[4];
            /** ancienne sortie de la voie (son brut) */
            public int oldOutD;
            /** sortie de la voie (son brut) */
            public int outD;
            /** LEFT enable flag */
            public int left;
            /** RIGHT enable flag */
            public int right;
            /** Algorythm = détermine les connections entre les opérateurs */
            public int algo;
            /** shift count of self feed back = degré de "Feed-Back" du SLOT 1 (il est son unique entrée) */
            public int fb;
            /** Fréquency Modulation Sensitivity of channel = degré de modulation de la fréquence sur la voie par le LFO */
            public int fms;
            /** Amplitude Modulation Sensitivity of channel = degré de modulation de l'amplitude sur la voie par le LFO */
            public int ams;
            /** hauteur fréquence de la voie (+ 3 pour le mode spécial) */
            public int[] fnum = new int[4];
            /** octave de la voie (+ 3 pour le mode spécial) */
            public int[] foct = new int[4];
            /** Key Code = valeur fonction de la fréquence (voir KSR pour les slots, KSR = KC >> KSR_S) */
            public int[] kc = new int[4];
            /** four slot.operators = les 4 slots de la voie */
            public Slot[] slots = new Slot[] {new Slot(), new Slot(), new Slot(), new Slot()};
            /** Frequency step recalculation flag */
            public int ffLag;
            /** Maxim: channel mute flag */
            public int mute;

            public int keyOn;
            public int[] fmVol = new int[2];
            public int[] fmSlotVol = new int[4];

            private void calcFInc(int[] fincTab) {
                int fInc = fincTab[this.fnum[0]] >> (7 - this.foct[0]);
                int kc = this.kc[0];

                this.slots[0].calcFInc(fInc, kc);
                this.slots[1].calcFInc(fInc, kc);
                this.slots[2].calcFInc(fInc, kc);
                this.slots[3].calcFInc(fInc, kc);
            }

            private void keyOn(int nsl) {
                Slot sl = this.slots[nsl]; // on recupère le bon pointeur de slot
                sl.keyOn();
            }

            private void keyOff(int nsl) {
                Slot sl = this.slots[nsl]; // on recupère le bon pointeur de slot
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

            private  void doLimit() {
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

        /** Horloge Ym2612 */
        public int clock;
        /** Sample Rate (11025/22050/44100) */
        public int rate;
        /** TimerBase calculation */
        public int timerBase;
        /** Ym2612 Status (timer overflow) */
        public int status;
        /** addresse pour l'écriture dans l'OPN A (propre à l'émulateur) */
        public int opnAAdr;
        /** addresse pour l'écriture dans l'OPN B (propre à l'émulateur) */
        public int opnBAdr;
        /** LFO counter = compteur-fréquence pour le LFO */
        public int lfoCnt;
        /** LFO step counter = pas d'incrémentation du compteur-fréquence du LFO */
        public int lfoInc;
        // plus le pas est grand, plus la fréquence est grande
        /** timerA limit = valeur jusqu'à laquelle le timer A doit compter */
        public int timerA;
        public int timerAL;
        /** timerA counter = valeur courante du Timer A */
        public int timerACnt;
        /** timerB limit = valeur jusqu'à laquelle le timer B doit compter */
        public int timerB;
        public int timerBL;
        /** timerB counter = valeur courante du Timer B */
        public int timerBCnt;
        /** Mode actuel des voie 3 et 6 (normal / spécial) */
        public int mode;
        /** DAC enabled flag */
        public int dac;
        /** DAC data */
        public int dacData;
        public int dacHighpass;
        /** Fréquence de base, se calcul par rapport à l'horlage et au sample rate */
        public double frequence;
        /** Interpolation Counter */
        public int interCnt;
        /** Interpolation Step */
        public int interStep;
        /** Les 6 voies du Ym2612 */
        public Channel[] channels = new Channel[] {new Channel(), new Channel(), new Channel(), new Channel(), new Channel(), new Channel()};
        /** Sauvegardes des valeurs de tout les registres, c'est facultatif */
        public int[][] regs = new int[][] {new int[0x100], new int[0x100]};
        /** cela nous rend le débuggage plus facile */
//        private static final int MAX_UPDATE_LENGHT = 0x100;

        /** Temporary calculated LFO AMS (adjusted for 11.8 dB) * */
        public int[] lfoEnvUp = new int[MAX_UPDATE_LENGHT];
        /** Temporary calculated LFO FMS * */
        public int[] lfoFreqUp = new int[MAX_UPDATE_LENGHT];

        /** current phase calculation * */
        public int in0, in1, in2, in3;
        /** current enveloppe calculation * */
        public int en0, en1, en2, en3;

        public int dacMute;

        private void controlCsmKey() {
            this.channels[2].keyOn(0);
            this.channels[2].keyOn(1);
            this.channels[2].keyOn(2);
            this.channels[2].keyOn(3);
        }

        private int setSlot(int adr, byte data) {
            Channel ch;
            Channel.Slot slot;
            int nch, nsl;

            if ((nch = adr & 3) == 3) return 1;
            nsl = (adr >> 2) & 3;

            if ((adr & 0x100) != 0) nch += 3;

            ch = this.channels[nch];
            slot = ch.slots[nsl];

            switch (adr & 0xF0) {
            case 0x30:
                if ((slot.mul = (data & 0x0F)) != 0) slot.mul <<= 1;
                else slot.mul = 1;

                slot.dt = dtTab[(data >> 4) & 7];

                ch.slots[0].fInc = -1;

                //Debug.printf(debug_file, "CHANNEL[%d], SLOT[%d] DTMUL = %.2X\n", nch, nsl, data & 0x7F);
                break;

            case 0x40:
                slot.tl = data & 0x7F;

                // SOR2 do a lot of TL adjustement and this fix R.Shinobi jump Sound...
                updateSpecial();

                slot.tll = slot.tl << (ENV_HBITS - 7);

                //Debug.printf(debug_file, "CHANNEL[%d], SLOT[%d] TL = %.2X\n", nch, nsl, slot.TL);
                break;

            case 0x50:
                slot.ksrS = 3 - (data >> 6);

                ch.slots[0].fInc = -1;

                if ((data &= 0x1F) != 0) {
                    slot.ar = arTab;
                    slot.arIndex = data << 1;
                } else {
                    slot.ar = nullRate;
                    slot.arIndex = 0;
                }

                slot.eIncA = slot.ar[slot.arIndex + slot.ksr];
                if (slot.eCurp == ATTACK) slot.eInc = slot.eIncA;

                //Debug.printf(debug_file, "CHANNEL[%d], SLOT[%d] AR = %.2X  EincA = %.6X\n", nch, nsl, data, slot.EincA);
                break;

            case 0x60:
                slot.do0x60(data, ch.ams, drTab, nullRate);

                //Debug.printf(debug_file, "CHANNEL[%d], SLOT[%d] AMS = %d  DR = %.2X  EincD = %.6X\n", nch, nsl, slot.AMSon, data, slot.EincD);
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
                if ((slot.eCurp == SUBSTAIN) && (slot.eCnt < ENV_END)) slot.eInc = slot.eIncS;

                //Debug.printf(debug_file, "CHANNEL[%d], SLOT[%d] SR = %.2X  EincS = %.6X\n", nch, nsl, data, slot.EincS);
                break;

            case 0x80:
                slot.sll = SL_TAB[data >> 4];

                slot.rr = drTab;
                slot.rrIndex = ((data & 0xF) << 2) + 2;

                slot.eIncR = slot.rr[slot.rrIndex + slot.ksr];
                if ((slot.eCurp == RELEASE) && (slot.eCnt < ENV_END)) slot.eInc = slot.eIncR;

                //Debug.printf(debug_file, "CHANNEL[%d], SLOT[%d] slot = %.8X\n", nch, nsl, slot.SLL);
                //Debug.printf(debug_file, "CHANNEL[%d], SLOT[%d] RR = %.2X  EincR = %.2X\n", nch, nsl, ((data & 0xF) << 1) | 2, slot.EincR);
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
                    if ((data & 0x08) != 0) slot.SEG = data & 0x0F;
                    else slot.SEG = 0;

                    //Debug.printf(debug_file, "CHANNEL[%d], SLOT[%d] SSG-EG = %.2X\n", nch, nsl, data);
                }
                break;
            }

            return 0;
        }

        private int setChannel(int adr, byte data) {
            Channel ch;
            int num;

            if ((num = adr & 3) == 3) return 1;

            switch (adr & 0xFC) {
            case 0xA0:
                if ((adr & 0x100) != 0) num += 3;
                ch = this.channels[num];

                updateSpecial();

                ch.fnum[0] = (ch.fnum[0] & 0x700) + data;
                ch.kc[0] = (ch.foct[0] << 2) | FKEY_TAB[ch.fnum[0] >> 7];

                ch.slots[0].fInc = -1;

                //Debug.printf(debug_file, "CHANNEL[%d] part1 FNUM = %d  KC = %d\n", num, ch.FNUM[0], ch.KC[0]);
                break;

            case 0xA4:
                if ((adr & 0x100) != 0) num += 3;
                ch = this.channels[num];

                updateSpecial();

                ch.fnum[0] = (ch.fnum[0] & 0x0FF) + ((data & 0x07) << 8);
                ch.foct[0] = (data & 0x38) >> 3;
                ch.kc[0] = (ch.foct[0] << 2) | FKEY_TAB[ch.fnum[0] >> 7];

                ch.slots[0].fInc = -1;

                //Debug.printf(debug_file, "CHANNEL[%d] part2 FNUM = %d  FOCT = %d  KC = %d\n", num, ch.FNUM[0], ch.FOCT[0], ch.KC[0]);
                break;

            case 0xA8:
                if (adr < 0x100) {
                    num++;

                    updateSpecial();

                    this.channels[2].fnum[num] = (this.channels[2].fnum[num] & 0x700) + data;
                    this.channels[2].kc[num] = (this.channels[2].foct[num] << 2) | FKEY_TAB[this.channels[2].fnum[num] >> 7];

                    this.channels[2].slots[0].fInc = -1;

                    //Debug.printf(debug_file, "CHANNEL[2] part1 FNUM[%d] = %d  KC[%d] = %d\n", num, this.CHANNEL[2].FNUM[num], num, this.CHANNEL[2].KC[num]);
                }
                break;

            case 0xAC:
                if (adr < 0x100) {
                    num++;

                    updateSpecial();

                    this.channels[2].fnum[num] = (this.channels[2].fnum[num] & 0x0FF) + ((data & 0x07) << 8);
                    this.channels[2].foct[num] = (data & 0x38) >> 3;
                    this.channels[2].kc[num] = (this.channels[2].foct[num] << 2) | FKEY_TAB[this.channels[2].fnum[num] >> 7];

                    this.channels[2].slots[0].fInc = -1;

                    //Debug.printf(debug_file, "CHANNEL[2] part2 FNUM[%d] = %d  FOCT[%d] = %d  KC[%d] = %d\n", num, this.CHANNEL[2].FNUM[num], num, this.CHANNEL[2].FOCT[num], num, this.CHANNEL[2].KC[num]);
                }
                break;

            case 0xB0:
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

                //Debug.printf(debug_file, "CHANNEL[%d] ALGO = %d  FB = %d\n", num, ch.ALGO, ch.FB);
                break;

            case 0xB4:
                if ((adr & 0x100) != 0) num += 3;
                ch = this.channels[num];

                updateSpecial();

                ch.do0xB4(data);

                //Debug.printf(debug_file, "CHANNEL[%d] AMS = %d  FMS = %d\n", num, ch.AMS, ch.FMS);
                break;
            }

            return 0;
        }

        private int setData(int adr, byte data) {
            Channel ch;
            int nch;

            switch (adr) {
            case 0x22:
                if ((data & 8) != 0) {
                    // Cool Spot music 1, LFO modified severals time which
                    // distord the Sound, have to check that on a real genesis...

                    this.lfoInc = lfoIncTab[data & 7];

                    //Debug.printf(debug_file, "\nLFO Enable, LFOinc = %.8X   %d\n", this.LFOinc, data & 7);
                } else {
                    this.lfoInc = this.lfoCnt = 0;

                    //Debug.printf(debug_file, "\nLFO Disable\n");
                }
                break;

            case 0x24:
                this.timerA = (this.timerA & 0x003) | (((int) data) << 2);

                if (this.timerAL != (1024 - this.timerA) << 12) {
                    this.timerACnt = this.timerAL = (1024 - this.timerA) << 12;

                    //Debug.printf(debug_file, "Timer A Set = %.8X\n", this.TimerAcnt);
                }
                break;

            case 0x25:
                this.timerA = (this.timerA & 0x3fc) | (data & 3);

                if (this.timerAL != (1024 - this.timerA) << 12) {
                    this.timerACnt = this.timerAL = (1024 - this.timerA) << 12;

                    //Debug.printf(debug_file, "Timer A Set = %.8X\n", this.TimerAcnt);
                }
                break;

            case 0x26:
                this.timerB = data;

                if (this.timerBL != (256 - this.timerB) << (4 + 12)) {
                    this.timerBCnt = this.timerBL = (256 - this.timerB) << (4 + 12);

                    //Debug.printf(debug_file, "Timer B Set = %.8X\n", this.TimerBcnt);
                }
                break;

            case 0x27:
                // Paramètre divers
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

                //Debug.printf(debug_file, "Mode reg = %.2X\n", data);
                break;

            case 0x28:
                if ((nch = data & 3) == 3) return 1;

                if ((data & 4) != 0) nch += 3;
                ch = this.channels[nch];

                updateSpecial();

                if ((data & 0x10) != 0) ch.keyOn(S0); // On appuie sur la touche pour le slot 1
                else ch.keyOff(S0); // On relâche la touche pour le slot 1
                if ((data & 0x20) != 0) ch.keyOn(S1); // On appuie sur la touche pour le slot 3
                else ch.keyOff(S1); // On relâche la touche pour le slot 3
                if ((data & 0x40) != 0) ch.keyOn(S2); // On appuie sur la touche pour le slot 2
                else ch.keyOff(S2); // On relâche la touche pour le slot 2
                if ((data & 0x80) != 0) ch.keyOn(S3); // On appuie sur la touche pour le slot 4
                else ch.keyOff(S3); // On relâche la touche pour le slot 4

                //Debug.printf(debug_file, "CHANNEL[%d]  KEY %.1X\n", nch, ((data & 0xf0) >> 4));
                break;

            case 0x2A:
                this.dacData = (int) data - 0x80 << DAC_SHIFT; // donnée du DAC
                break;

            case 0x2B:
                if ((this.dac ^ (data & 0x80)) != 0) updateSpecial();

                this.dac = data & 0x80; // activation/désactivation du DAC
                break;
            }

            return 0;
        }

        // Gens

        private void updateSpecial() {
//            if (YM_Len && YM2612_Enable) {
//                YM2612_Update(YM_Buf, YM_Len);
//
//                YM_Buf[0] = Seg_L + Sound_Extrapol[VDP_Current_Line + 1][0];
//                YM_Buf[1] = Seg_R + Sound_Extrapol[VDP_Current_Line + 1][0];
//                YM_Len = 0;
//            }
        }

        private void getCurrentPhase(Channel ch) {
            this.in0 = ch.slots[S0].fCnt;
            this.in1 = ch.slots[S1].fCnt;
            this.in2 = ch.slots[S2].fCnt;
            this.in3 = ch.slots[S3].fCnt;
        }

        private void updatePhaseLfo(Channel ch, int i, int lfoFreq) {
            if ((lfoFreq = (ch.fms * this.lfoFreqUp[i]) >> (LFO_HBITS - 1)) > 0) {
                ch.slots[S0].fCnt += ch.slots[S0].fInc + ((ch.slots[S0].fInc * lfoFreq) >> LFO_FMS_LBITS);
                ch.slots[S1].fCnt += ch.slots[S1].fInc + ((ch.slots[S1].fInc * lfoFreq) >> LFO_FMS_LBITS);
                ch.slots[S2].fCnt += ch.slots[S2].fInc + ((ch.slots[S2].fInc * lfoFreq) >> LFO_FMS_LBITS);
                ch.slots[S3].fCnt += ch.slots[S3].fInc + ((ch.slots[S3].fInc * lfoFreq) >> LFO_FMS_LBITS);
            } else {
                ch.slots[S0].fCnt += ch.slots[S0].fInc;
                ch.slots[S1].fCnt += ch.slots[S1].fInc;
                ch.slots[S2].fCnt += ch.slots[S2].fInc;
                ch.slots[S3].fCnt += ch.slots[S3].fInc;
            }
        }

        private void getCurrentEnv(Channel ch) {
            if ((ch.slots[S0].SEG & 4) != 0) {
                if ((this.en0 = ENV_TAB[(ch.slots[S0].eCnt >> ENV_LBITS)] + ch.slots[S0].tll) > ENV_MASK)
                    this.en0 = 0;
                else this.en0 ^= ENV_MASK;
            } else this.en0 = ENV_TAB[(ch.slots[S0].eCnt >> ENV_LBITS)] + ch.slots[S0].tll;
            if ((ch.slots[S1].SEG & 4) != 0) {
                if ((this.en1 = ENV_TAB[(ch.slots[S1].eCnt >> ENV_LBITS)] + ch.slots[S1].tll) > ENV_MASK)
                    this.en1 = 0;
                else this.en1 ^= ENV_MASK;
            } else this.en1 = ENV_TAB[(ch.slots[S1].eCnt >> ENV_LBITS)] + ch.slots[S1].tll;
            if ((ch.slots[S2].SEG & 4) != 0) {
                if ((this.en2 = ENV_TAB[(ch.slots[S2].eCnt >> ENV_LBITS)] + ch.slots[S2].tll) > ENV_MASK)
                    this.en2 = 0;
                else this.en2 ^= ENV_MASK;
            } else this.en2 = ENV_TAB[(ch.slots[S2].eCnt >> ENV_LBITS)] + ch.slots[S2].tll;
            if ((ch.slots[S3].SEG & 4) != 0) {
                if ((this.en3 = ENV_TAB[(ch.slots[S3].eCnt >> ENV_LBITS)] + ch.slots[S3].tll) > ENV_MASK)
                    this.en3 = 0;
                else this.en3 ^= ENV_MASK;
            } else this.en3 = ENV_TAB[(ch.slots[S3].eCnt >> ENV_LBITS)] + ch.slots[S3].tll;
        }

        private void getCurrentEnvLfo(Channel ch, int i, int lfoEnv) {
            lfoEnv = this.lfoEnvUp[i];

            if ((ch.slots[S0].SEG & 4) != 0) {
                if ((this.en0 = ENV_TAB[(ch.slots[S0].eCnt >> ENV_LBITS)] + ch.slots[S0].tll) > ENV_MASK)
                    this.en0 = 0;
                else this.en0 = (this.en0 ^ ENV_MASK) + (lfoEnv >> ch.slots[S0].ams);
            } else
                this.en0 = ENV_TAB[(ch.slots[S0].eCnt >> ENV_LBITS)] + ch.slots[S0].tll + (lfoEnv >> ch.slots[S0].ams);
            if ((ch.slots[S1].SEG & 4) != 0) {
                if ((this.en1 = ENV_TAB[(ch.slots[S1].eCnt >> ENV_LBITS)] + ch.slots[S1].tll) > ENV_MASK)
                    this.en1 = 0;
                else this.en1 = (this.en1 ^ ENV_MASK) + (lfoEnv >> ch.slots[S1].ams);
            } else
                this.en1 = ENV_TAB[(ch.slots[S1].eCnt >> ENV_LBITS)] + ch.slots[S1].tll + (lfoEnv >> ch.slots[S1].ams);
            if ((ch.slots[S2].SEG & 4) != 0) {
                if ((this.en2 = ENV_TAB[(ch.slots[S2].eCnt >> ENV_LBITS)] + ch.slots[S2].tll) > ENV_MASK)
                    this.en2 = 0;
                else this.en2 = (this.en2 ^ ENV_MASK) + (lfoEnv >> ch.slots[S2].ams);
            } else
                this.en2 = ENV_TAB[(ch.slots[S2].eCnt >> ENV_LBITS)] + ch.slots[S2].tll + (lfoEnv >> ch.slots[S2].ams);
            if ((ch.slots[S3].SEG & 4) != 0) {
                if ((this.en3 = ENV_TAB[(ch.slots[S3].eCnt >> ENV_LBITS)] + ch.slots[S3].tll) > ENV_MASK)
                    this.en3 = 0;
                else this.en3 = (this.en3 ^ ENV_MASK) + (lfoEnv >> ch.slots[S3].ams);
            } else
                this.en3 = ENV_TAB[(ch.slots[S3].eCnt >> ENV_LBITS)] + ch.slots[S3].tll + (lfoEnv >> ch.slots[S3].ams);
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

        private void doAlgo6(Channel CH) {
            doFeedback(CH);
            this.in1 += CH.s0Out[1];
            CH.outD = (TL_TAB[SIN_TAB[(this.in3 >> SIN_LBITS) & SIN_MASK] + this.en3] +
                    TL_TAB[SIN_TAB[(this.in1 >> SIN_LBITS) & SIN_MASK] + this.en1] +
                    TL_TAB[SIN_TAB[(this.in2 >> SIN_LBITS) & SIN_MASK] + this.en2]) >> OUT_SHIFT;
            CH.doLimit();
        }

        private void doAlgo7(Channel ch) {
            doFeedback(ch);
            ch.outD = (TL_TAB[SIN_TAB[(this.in3 >> SIN_LBITS) & SIN_MASK] + this.en3] +
                    TL_TAB[SIN_TAB[(this.in1 >> SIN_LBITS) & SIN_MASK] + this.en1] +
                    TL_TAB[SIN_TAB[(this.in2 >> SIN_LBITS) & SIN_MASK] + this.en2] + ch.s0Out[1]) >> OUT_SHIFT;
            ch.doLimit();
        }

        private void doOutput(Channel ch, int[][] buf, int i) {
            buf[0][i] += ch.outD & ch.left;
            buf[1][i] += ch.outD & ch.right;
            vol[0] = Math.max(vol[0], Math.abs(ch.outD & ch.left));
            vol[1] = Math.max(vol[1], Math.abs(ch.outD & ch.right));
        }

        private void doOutputInt0(Channel ch, int[][] buf, int i) {
            if (((intCnt += this.interStep) & 0x04000) != 0) {
                intCnt &= 0x3FFF;
                buf[0][i] += ch.oldOutD & ch.left;
                buf[1][i] += ch.oldOutD & ch.right;
                vol[0] = Math.max(vol[0], Math.abs(ch.oldOutD & ch.left));
                vol[1] = Math.max(vol[1], Math.abs(ch.oldOutD & ch.right));
            } else i--;
        }

        private void doOutputInt1(Channel ch, int[][] buf, int i) {
            ch.oldOutD = (ch.outD + ch.oldOutD) >> 1;
            if (((intCnt += this.interStep) & 0x04000) != 0) {
                intCnt &= 0x3FFF;
                buf[0][i] += ch.oldOutD & ch.left;
                buf[1][i] += ch.oldOutD & ch.right;
                vol[0] = Math.max(vol[0], Math.abs(ch.oldOutD & ch.left));
                vol[1] = Math.max(vol[1], Math.abs(ch.oldOutD & ch.right));
            } else i--;
        }

        private void doOutputInt2(Channel ch, int[][] buf, int i) {
            if (((intCnt += this.interStep) & 0x04000) != 0) {
                intCnt &= 0x3FFF;
                ch.oldOutD = (ch.outD + ch.oldOutD) >> 1;
                buf[0][i] += ch.oldOutD & ch.left;
                buf[1][i] += ch.oldOutD & ch.right;
                vol[0] = Math.max(vol[0], Math.abs(ch.oldOutD & ch.left));
                vol[1] = Math.max(vol[1], Math.abs(ch.oldOutD & ch.right));
            } else i--;
            ch.oldOutD = ch.outD;
        }

        private void doOutputInt(Channel ch, int[][] buf, int i) {
            if (((intCnt += this.interStep) & 0x04000) != 0) {
                intCnt &= 0x3FFF;
                ch.oldOutD = (((intCnt ^ 0x3FFF) * ch.outD) + (intCnt * ch.oldOutD)) >> 14;
                buf[0][i] += ch.oldOutD & ch.left;
                buf[1][i] += ch.oldOutD & ch.right;
                vol[0] = Math.max(vol[0], Math.abs(ch.oldOutD & ch.left));
                vol[1] = Math.max(vol[1], Math.abs(ch.oldOutD & ch.right));
            } else i--;
            ch.oldOutD = ch.outD;
        }

        private void updateChanAlgo0(Channel ch, int[][] buf, int length) {
            if (ch.slots[S3].eCnt == ENV_END) return;

            //Debug.printf(debug_file, "\n\nAlgo 0 len = %d\n\n", length);

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

            //Debug.printf(debug_file, "\n\nAlgo 1 len = %d\n\n", length);

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

            //Debug.printf(debug_file, "\n\nAlgo 2 len = %d\n\n", length);

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

            //Debug.printf(debug_file, "\n\nAlgo 3 len = %d\n\n", length);

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

            //Debug.printf(debug_file, "\n\nAlgo 4 len = %d\n\n", length);

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

            //Debug.printf(debug_file, "\n\nAlgo 5 len = %d\n\n", length);

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

            //Debug.printf(debug_file, "\n\nAlgo 6 len = %d\n\n", length);

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

            //Debug.printf(debug_file, "\n\nAlgo 7 len = %d\n\n", length);

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
            int envLfo = 0, freqLfo = 0;

            if (ch.slots[S3].eCnt == ENV_END) return;

            //Debug.printf(debug_file, "\n\nAlgo 0 LFO len = %d\n\n", length);

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
            int envLfo = 0, freqLfo = 0;

            if (ch.slots[S3].eCnt == ENV_END) return;

            //Debug.printf(debug_file, "\n\nAlgo 1 LFO len = %d\n\n", length);

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
            int i, envLfo = 0, freqLfo = 0;

            if (ch.slots[S3].eCnt == ENV_END) return;

            //Debug.printf(debug_file, "\n\nAlgo 2 LFO len = %d\n\n", length);

            for (i = 0; i < length; i++) {
                getCurrentPhase(ch);
                updatePhaseLfo(ch, i, freqLfo);
                getCurrentEnvLfo(ch, i, envLfo);
                ch.updateEnv();
                doAlgo2(ch);
                doOutput(ch, buf, i);
            }
        }

        private void updateChanAlgo3LFO(Channel ch, int[][] buf, int length) {
            int envLFO = 0, freqLfo = 0;

            if (ch.slots[S3].eCnt == ENV_END) return;

            //Debug.printf(debug_file, "\n\nAlgo 3 LFO len = %d\n\n", length);

            for (int i = 0; i < length; i++) {
                getCurrentPhase(ch);
                updatePhaseLfo(ch, i, freqLfo);
                getCurrentEnvLfo(ch, i, envLFO);
                ch.updateEnv();
                doAlgo3(ch);
                doOutput(ch, buf, i);
            }
        }

        private void updateChanAlgo4LFO(Channel ch, int[][] buf, int length) {
            int envLfo = 0, freqLfo = 0;

            if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END)) return;

            //Debug.printf(debug_file, "\n\nAlgo 4 LFO len = %d\n\n", length);

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
            int envLfo = 0, freqLfo = 0;

            if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) &&
                    (ch.slots[S3].eCnt == ENV_END)) return;

            //Debug.printf(debug_file, "\n\nAlgo 5 LFO len = %d\n\n", length);

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
            int i, envLfo = 0, freqLfo = 0;

            if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END)) return;

            // Debug.printf(debug_file, "\n\nAlgo 6 LFO len = %d\n\n", length);

            for (i = 0; i < length; i++) {
                getCurrentPhase(ch);
                updatePhaseLfo(ch, i, freqLfo);
                getCurrentEnvLfo(ch, i, envLfo);
                ch.updateEnv();
                doAlgo6(ch);
                doOutput(ch, buf, i);
            }
        }

        private void updateChanAlgo7LFO(Channel ch, int[][] buf, int length) {
            int envLfo = 0, freqLfo = 0;

            if ((ch.slots[S0].eCnt == ENV_END) && (ch.slots[S1].eCnt == ENV_END) &&
                    (ch.slots[S2].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END))
                return;

            //Debug.printf(debug_file, "\n\nAlgo 7 LFO len = %d\n\n", length);

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

            //Debug.printf(debug_file, "\n\nAlgo 0 len = %d\n\n", length);

            intCnt = this.interCnt;

            for (int i = 0; i < length; i++) {
                getCurrentPhase(ch);
                ch.updatePhase();
                getCurrentEnv(ch);
                ch.updateEnv();
                doAlgo0(ch);
                doOutputInt(ch, buf, i);
            }
        }

        private void updateChanAlgo1Int(Channel ch, int[][] buf, int length) {

            if (ch.slots[S3].eCnt == ENV_END) return;

            //Debug.printf(debug_file, "\n\nAlgo 1 len = %d\n\n", length);

            intCnt = this.interCnt;

            for (int i = 0; i < length; i++) {
                getCurrentPhase(ch);
                ch.updatePhase();
                getCurrentEnv(ch);
                ch.updateEnv();
                doAlgo1(ch);
                doOutputInt(ch, buf, i);
            }
        }

        private void updateChanAlgo2Int(Channel ch, int[][] buf, int length) {

            if (ch.slots[S3].eCnt == ENV_END) return;

            // Debug.printf(debug_file, "\n\nAlgo 2 len = %d\n\n", length);

            intCnt = this.interCnt;

            for (int i = 0; i < length; i++) {
                getCurrentPhase(ch);
                ch.updatePhase();
                getCurrentEnv(ch);
                ch.updateEnv();
                doAlgo2(ch);
                doOutputInt(ch, buf, i);
            }
        }

        private void updateChanAlgo3Int(Channel ch, int[][] buf, int length) {

            if (ch.slots[S3].eCnt == ENV_END) return;

            //Debug.printf(debug_file, "\n\nAlgo 3 len = %d\n\n", length);

            intCnt = this.interCnt;

            for (int i = 0; i < length; i++) {
                getCurrentPhase(ch);
                ch.updatePhase();
                getCurrentEnv(ch);
                ch.updateEnv();
                doAlgo3(ch);
                doOutputInt(ch, buf, i);
            }
        }

        private void updateChanAlgo4Int(Channel ch, int[][] buf, int length) {

            if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END)) return;

            //Debug.printf(debug_file, "\n\nAlgo 4 len = %d\n\n", length);

            intCnt = this.interCnt;

            for (int i = 0; i < length; i++) {
                getCurrentPhase(ch);
                ch.updatePhase();
                getCurrentEnv(ch);
                ch.updateEnv();
                doAlgo4(ch);
                doOutputInt(ch, buf, i);
            }
        }

        private void updateChanAlgo5Int(Channel ch, int[][] buf, int length) {

            if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) &&
                    (ch.slots[S3].eCnt == ENV_END)) return;

            //Debug.printf(debug_file, "\n\nAlgo 5 len = %d\n\n", length);

            intCnt = this.interCnt;

            for (int i = 0; i < length; i++) {
                getCurrentPhase(ch);
                ch.updatePhase();
                getCurrentEnv(ch);
                ch.updateEnv();
                doAlgo5(ch);
                doOutputInt(ch, buf, i);
            }
        }

        private void updateChanAlgo6Int(Channel ch, int[][] buf, int length) {

            if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) &&
                    (ch.slots[S3].eCnt == ENV_END)) return;

            //Debug.printf(debug_file, "\n\nAlgo 6 len = %d\n\n", length);

            intCnt = this.interCnt;

            for (int i = 0; i < length; i++) {
                getCurrentPhase(ch);
                ch.updatePhase();
                getCurrentEnv(ch);
                ch.updateEnv();
                doAlgo6(ch);
                doOutputInt(ch, buf, i);
            }
        }

        private void updateChanAlgo7Int(Channel ch, int[][] buf, int length) {

            if ((ch.slots[S0].eCnt == ENV_END) && (ch.slots[S1].eCnt == ENV_END) &&
                    (ch.slots[S2].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END))
                return;

            //Debug.printf(debug_file, "\n\nAlgo 7 len = %d\n\n", length);

            intCnt = this.interCnt;

            for (int i = 0; i < length; i++) {
                getCurrentPhase(ch);
                ch.updatePhase();
                getCurrentEnv(ch);
                ch.updateEnv();
                doAlgo7(ch);
                doOutputInt(ch, buf, i);
            }
        }

        private void updateChanAlgo0LFOInt(Channel ch, int[][] buf, int length) {
            int envLfo = 0, freqLfo = 0;

            if (ch.slots[S3].eCnt == ENV_END) return;

            //Debug.printf(debug_file, "\n\nAlgo 0 LFO len = %d\n\n", length);

            intCnt = this.interCnt;

            for (int i = 0; i < length; i++) {
                getCurrentPhase(ch);
                updatePhaseLfo(ch, i, freqLfo);
                getCurrentEnvLfo(ch, i, envLfo);
                ch.updateEnv();
                doAlgo0(ch);
                doOutputInt(ch, buf, i);
            }
        }

        private void updateChanAlgo1LFOInt(Channel ch, int[][] buf, int length) {
            int envLfo = 0, freqLfo = 0;

            if (ch.slots[S3].eCnt == ENV_END) return;

            //Debug.printf(debug_file, "\n\nAlgo 1 LFO len = %d\n\n", length);

            intCnt = this.interCnt;

            for (int i = 0; i < length; i++) {
                getCurrentPhase(ch);
                updatePhaseLfo(ch, i, freqLfo);
                getCurrentEnvLfo(ch, i, envLfo);
                ch.updateEnv();
                doAlgo1(ch);
                doOutputInt(ch, buf, i);
            }
        }

        private void updateChanAlgo2LFOInt(Channel ch, int[][] buf, int length) {
            int envLfo = 0, freqLfo = 0;

            if (ch.slots[S3].eCnt == ENV_END) return;

            //Debug.printf(debug_file, "\n\nAlgo 2 LFO len = %d\n\n", length);

            intCnt = this.interCnt;

            for (int i = 0; i < length; i++) {
                getCurrentPhase(ch);
                updatePhaseLfo(ch, i, freqLfo);
                getCurrentEnvLfo(ch, i, envLfo);
                ch.updateEnv();
                doAlgo2(ch);
                doOutputInt(ch, buf, i);
            }
        }

        private void updateChanAlgo3LFOInt(Channel ch, int[][] buf, int length) {
            int envLfo = 0, freqLfo = 0;

            if (ch.slots[S3].eCnt == ENV_END) return;

            //Debug.printf(debug_file, "\n\nAlgo 3 LFO len = %d\n\n", length);

            intCnt = this.interCnt;

            for (int i = 0; i < length; i++) {
                getCurrentPhase(ch);
                updatePhaseLfo(ch, i, freqLfo);
                getCurrentEnvLfo(ch, i, envLfo);
                ch.updateEnv();
                doAlgo3(ch);
                doOutputInt(ch, buf, i);
            }
        }

        private void updateChanAlgo4LFOInt(Channel ch, int[][] buf, int length) {
            int envLfo = 0, freqLfo = 0;

            if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END)) return;

            //Debug.printf(debug_file, "\n\nAlgo 4 LFO len = %d\n\n", length);

            intCnt = this.interCnt;

            for (int i = 0; i < length; i++) {
                getCurrentPhase(ch);
                updatePhaseLfo(ch, i, freqLfo);
                getCurrentEnvLfo(ch, i, envLfo);
                ch.updateEnv();
                doAlgo4(ch);
                doOutputInt(ch, buf, i);
            }
        }

        private void updateChanAlgo5LFOInt(Channel ch, int[][] buf, int length) {
            int envLfo = 0, freqLfo = 0;

            if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END)) return;

            //Debug.printf(debug_file, "\n\nAlgo 5 LFO len = %d\n\n", length);

            intCnt = this.interCnt;

            for (int i = 0; i < length; i++) {
                getCurrentPhase(ch);
                updatePhaseLfo(ch, i, freqLfo);
                getCurrentEnvLfo(ch, i, envLfo);
                ch.updateEnv();
                doAlgo5(ch);
                doOutputInt(ch, buf, i);
            }
        }

        private void updateChanAlgo6LFOInt(Channel ch, int[][] buf, int length) {
            int envLfo = 0, freqLfo = 0;

            if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END)) return;

            //Debug.printf(debug_file, "\n\nAlgo 6 LFO len = %d\n\n", length);

            intCnt = this.interCnt;

            for (int i = 0; i < length; i++) {
                getCurrentPhase(ch);
                updatePhaseLfo(ch, i, freqLfo);
                getCurrentEnvLfo(ch, i, envLfo);
                ch.updateEnv();
                doAlgo6(ch);
                doOutputInt(ch, buf, i);
            }
        }

        private void updateChanAlgo7LFOInt(Channel ch, int[][] buf, int length) {
            int envLfo = 0, freqLfo = 0;

            if ((ch.slots[S0].eCnt == ENV_END) && (ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END))
                return;

            //Debug.printf(debug_file, "\n\nAlgo 7 LFO len = %d\n\n", length);

            intCnt = this.interCnt;

            for (int i = 0; i < length; i++) {
                getCurrentPhase(ch);
                updatePhaseLfo(ch, i, freqLfo);
                getCurrentEnvLfo(ch, i, envLfo);
                ch.updateEnv();
                doAlgo7(ch);
                doOutputInt(ch, buf, i);
            }
        }

        // Initialisation de l'émulateur Ym2612
        private Ym2612Context(int clock, int rate, int interpolation) {

            //Debug.printf(debug_file, "Ym2612 logging :\n\n");

            this.clock = clock;
            this.rate = rate;

            // 144 = 12 * (prescale * 2) = 12 * 6 * 2
            // prescale set to 6 by default

            this.frequence = ((double) this.clock / (double) this.rate) / 144.0;
            this.timerBase = (int) (this.frequence * 4096.0);

            if ((interpolation != 0) && (this.frequence > 1.0)) {
                this.interStep = (int) ((1.0 / this.frequence) * (double) (0x4000));
                this.interCnt = 0;

                // We recalculate rate and frequence after interpolation

                this.rate = this.clock / 144;
                this.frequence = 1.0;
            } else {
                this.interStep = 0x4000;
                this.interCnt = 0;
            }

            //Debug.printf(debug_file, "Ym2612 frequence = %g rate = %d  interp step = %.8X\n\n", this.Frequence, this.rate, this.Inter_Step);

            // Tableau TL :
            // [0     -  4095] = +output  [4095  - ...] = +output overflow (fill with 0)
            // [12288 - 16383] = -output  [16384 - ...] = -output overflow (fill with 0)

            for (int i = 0; i < TL_LENGHT; i++) {
                if (i >= PG_CUT_OFF) // Ym2612 cut off Sound after 78 dB (14 bits output ?)
                {
                    TL_TAB[TL_LENGHT + i] = TL_TAB[i] = 0;
                } else {
                    double x = MAX_OUT; // Max output
                    x /= Math.pow(10, (ENV_STEP * i) / 20); // Decibel . Voltage

                    TL_TAB[i] = (int) x;
                    TL_TAB[TL_LENGHT + i] = -TL_TAB[i];
                }

                //Debug.printf(debug_file, "TL_TAB[%d] = %.8X    TL_TAB[%d] = %.8X\n", i, TL_TAB[i], TL_LENGHT + i, TL_TAB[TL_LENGHT + i]);
            }

            //Debug.printf(debug_file, "\n\n\n\n");

            // Tableau SIN :
            // SIN_TAB[x][y] = sin(x) * y;
            // x = phase and y = volume

            SIN_TAB[0] = SIN_TAB[SIN_LENGHT / 2] = PG_CUT_OFF;

            for (int i = 1; i <= SIN_LENGHT / 4; i++) {
                double x = Math.sin(2.0 * Math.PI * (double) (i) / (double) (SIN_LENGHT)); // Sinus
                x = 20 * Math.log10(1 / x); // convert to dB

                int j = (int) (x / ENV_STEP); // Get TL range

                if (j > PG_CUT_OFF) j = PG_CUT_OFF;

                SIN_TAB[i] = SIN_TAB[(SIN_LENGHT / 2) - i] = j;
                SIN_TAB[(SIN_LENGHT / 2) + i] = SIN_TAB[SIN_LENGHT - i] = TL_LENGHT + j;

                //Debug.printf(debug_file, "SIN[%d][0] = %.8X    SIN[%d][0] = %.8X    SIN[%d][0] = %.8X    SIN[%d][0] = %.8X\n", i, SIN_TAB[i][0], (SIN_LENGHT / 2) - i, SIN_TAB[(SIN_LENGHT / 2) - i][0], (SIN_LENGHT / 2) + i, SIN_TAB[(SIN_LENGHT / 2) + i][0], SIN_LENGHT - i, SIN_TAB[SIN_LENGHT - i][0]);
            }

            //Debug.printf(debug_file, "\n\n\n\n");

            // Tableau LFO (LFO wav) :

            for (int i = 0; i < LFO_LENGHT; i++) {
                double x = Math.sin(2.0 * Math.PI * (double) i / (double) (LFO_LENGHT)); // Sinus
                x += 1.0;
                x /= 2.0; // positive only
                x *= 11.8 / ENV_STEP; // ajusted to MAX enveloppe modulation

                LFO_ENV_TAB[i] = (int) x;

                x = Math.sin(2.0 * Math.PI * (double) i / (double) LFO_LENGHT); // Sinus
                x *= (1 << (LFO_HBITS - 1)) - 1;

                LFO_FREQ_TAB[i] = (int) x;

                //Debug.printf(debug_file, "LFO[%d] = %.8X\n", i, LFO_ENV_TAB[i]);
            }

            //Debug.printf(debug_file, "\n\n\n\n");

            // Tableau Enveloppe :
            // ENV_TAB[0] . ENV_TAB[ENV_LENGHT - 1] = attack curve
            // ENV_TAB[ENV_LENGHT] . ENV_TAB[2 * ENV_LENGHT - 1] = decay curve

            for (int i = 0; i < ENV_LENGHT; i++) {
                // Attack curve (x^8 - music level 2 Vectorman 2)
                double x = Math.pow(((double) ((ENV_LENGHT - 1) - i) / (double) (ENV_LENGHT)), 8);
                x *= ENV_LENGHT;

                ENV_TAB[i] = (int) x;

                // Decay curve (just linear)
                x = Math.pow(((double) i / (double) ENV_LENGHT), 1);
                x *= ENV_LENGHT;

                ENV_TAB[ENV_LENGHT + i] = (int) x;

                //Debug.printf(debug_file, "ATTACK[%d] = %d   DECAY[%d] = %d\n", i, ENV_TAB[i], i, ENV_TAB[ENV_LENGHT + i]);
            }

            ENV_TAB[ENV_END >> ENV_LBITS] = ENV_LENGHT - 1; // for the stopped state

            // Tableau pour la conversion Attack . Decay and Decay . Attack

            for (int i = 0, j = ENV_LENGHT - 1; i < ENV_LENGHT; i++) {
                while (j > 0 && (ENV_TAB[j] < i)) j--;

                DECAY_TO_ATTACK[i] = j << ENV_LBITS;
            }

            // Tableau pour le Substain Level

            for (int i = 0; i < 15; i++) {
                double x = i * 3; // 3 and not 6 (Mickey Mania first music for test)
                x /= ENV_STEP;

                int j = (int) x;
                j <<= ENV_LBITS;

                SL_TAB[i] = j + ENV_DECAY;
            }

            int j = ENV_LENGHT - 1; // special case : volume off
            j <<= ENV_LBITS;
            SL_TAB[15] = j + ENV_DECAY;

            // Tableau Frequency Step

            for (int i = 0; i < 2048; i++) {
                double x = (double) (i) * this.frequence;

                x *= 1 << (SIN_LBITS + SIN_HBITS - (21 - 7));

                x /= 2.0; // because MUL = value * 2

                fincTab[i] = (int) x;
            }

            // Tableaux Attack & Decay rate

            for (int i = 0; i < 4; i++) {
                arTab[i] = 0;
                drTab[i] = 0;
            }

            for (int i = 0; i < 60; i++) {
                double x = this.frequence;

                x *= 1.0 + ((i & 3) * 0.25); // bits 0-1 : x1.00, x1.25, x1.50, x1.75
                x *= 1 << ((i >> 2)); // bits 2-5 : shift bits (x2^0 - x2^15)
                x *= ENV_LENGHT << ENV_LBITS; // on ajuste pour le tableau ENV_TAB

                arTab[i + 4] = (int) (x / AR_RATE);
                drTab[i + 4] = (int) (x / DR_RATE);
            }

            for (int i = 64; i < 96; i++) {
                arTab[i] = arTab[63];
                drTab[i] = drTab[63];

                nullRate[i - 64] = 0;
            }

            // Tableau Detune

            for (int i = 0; i < 4; i++) {
                for (j = 0; j < 32; j++) {
                    double x = (double) DT_DEF_TAB[(i << 5) + j] * this.frequence * (double) (1 << (SIN_LBITS + SIN_HBITS - 21));

                    dtTab[i + 0][j] = (int) x;
                    dtTab[i + 4][j] = (int) -x;
                }
            }

            // Tableau LFO

            j = (this.rate * this.interStep) / 0x4000;

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

        private int end() {
            return 0;
        }

        private int reset() {
            //  Debug.printf(debug_file, "\n\nStarting reseting Ym2612 ...\n\n");

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

            for (int i = 0xB6; i >= 0xB4; i--) {
                write((byte) 0, (byte) i);
                write((byte) 2, (byte) i);
                write((byte) 1, (byte) 0xC0);
                write((byte) 3, (byte) 0xC0);
            }

            for (int i = 0xB2; i >= 0x22; i--) {
                write((byte) 0, (byte) i);
                write((byte) 2, (byte) i);
                write((byte) 1, (byte) 0);
                write((byte) 3, (byte) 0);
            }

            write((byte) 0, (byte) 0x2A);
            write((byte) 1, (byte) 0x80);

            //  Debug.printf(debug_file, "\n\nFinishing reseting Ym2612 ...\n\n");

            return 0;
        }

        private int read() {
            /*  static int cnt = 0;

              if(cnt++ == 50) {
                cnt = 0;
                return this.Status;
              }
              else return this.Status | 0x80;
            */
            return this.status;
        }

        private int write(byte adr, byte data) {

            data &= 0xFF;
            adr &= 0x03;

            switch (adr) {
            case 0:
                this.opnAAdr = data;
                break;

            case 1:
                // Trivial optimisation
                if (this.opnAAdr == 0x2A) {
                    this.dacData = ((int) data - 0x80) << DAC_SHIFT;
                    return 0;
                }

                int d = this.opnAAdr & 0xF0;

                if (d >= 0x30) {
                    if (this.regs[0][this.opnAAdr] == data) return 2;
                    this.regs[0][this.opnAAdr] = data;

                    //if (GYM_Dumping) Update_GYM_Dump(1, this.OPNAadr, data);

                    if (d < 0xA0) { // SLOT
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
                this.opnBAdr = data;
                break;

            case 3:
                d = this.opnBAdr & 0xF0;

                if (d >= 0x30) {
                    if (this.regs[1][this.opnBAdr] == data) return 2;
                    this.regs[1][this.opnBAdr] = data;

                    // if (GYM_Dumping) Update_GYM_Dump(2, this.OPNBadr, data);

                    if (d < 0xA0) { // SLOT
                        setSlot(this.opnBAdr + 0x100, data);
                    } else { // CHANNEL
                        setChannel(this.opnBAdr + 0x100, data);
                    }
                } else return 1;
                break;
            }

            return 0;
        }

        private int getMute() {
            int result = 0;
            for (int i = 0; i < 6; ++i) {
                result |= this.channels[i].mute << i;
            }
            result |= this.dacMute << 6;
            //result &= !enableSsgEg;
            return result;
        }

        private void setMute(int val) {
            for (int i = 0; i < 6; ++i) {
                this.channels[i].mute = (val >> i) & 1;
            }
            this.dacMute = (val >> 6) & 1;
            //enableSsgEg = !(val & 1);
        }

        private void setOptions(int flags) {
            dacHighpassEnable = (flags >> 0) & 0x01;
            enableSsgEg = (flags >> 1) & 0x01;
        }

        private void clearBuffer(int[][] buffer, int length) {
            // the MAME core does this before updating,
            // but the Gens core does this before mixing
            int[] bufL = buffer[0];
            int[] bufR = buffer[1];

            for (int i = 0; i < length; i++) {
                bufL[i] = 0x0000;
                bufR[i] = 0x0000;
            }
        }

        private void YM2612_Update(int[][] buf, int length) {
            int i, j, algoType;

// #if DEBUG
            //Debug.printf(debug_file, "\n\nStarting generating Sound...\n\n");
// #endif

            // Mise ?jour des pas des compteurs-fréquences s'ils ont 騁?modifi駸

            if (this.channels[0].slots[0].fInc == -1) this.channels[0].calcFInc(this.fincTab);
            if (this.channels[1].slots[0].fInc == -1) this.channels[1].calcFInc(this.fincTab);
            if (this.channels[2].slots[0].fInc == -1) {
                if ((this.mode & 0x40) > 0) {
                    this.channels[2].slots[S0].calcFInc(fincTab[this.channels[2].fnum[2]] >> (7 - this.channels[2].foct[2]), this.channels[2].kc[2]);
                    this.channels[2].slots[S1].calcFInc(fincTab[this.channels[2].fnum[3]] >> (7 - this.channels[2].foct[3]), this.channels[2].kc[3]);
                    this.channels[2].slots[S2].calcFInc(fincTab[this.channels[2].fnum[1]] >> (7 - this.channels[2].foct[1]), this.channels[2].kc[1]);
                    this.channels[2].slots[S3].calcFInc(fincTab[this.channels[2].fnum[0]] >> (7 - this.channels[2].foct[0]), this.channels[2].kc[0]);
                } else {
                    this.channels[2].calcFInc(this.fincTab);
                }
            }
            if (this.channels[3].slots[0].fInc == -1) this.channels[3].calcFInc(this.fincTab);
            if (this.channels[4].slots[0].fInc == -1) this.channels[4].calcFInc(this.fincTab);
            if (this.channels[5].slots[0].fInc == -1) this.channels[5].calcFInc(this.fincTab);

            if ((this.interStep & 0x04000) > 0) algoType = 0;
            else algoType = 16;

            if ((this.lfoInc) != 0) {
                // Precalcul LFO wav

                for (i = 0; i < length; i++) {
                    j = ((this.lfoCnt += this.lfoInc) >> LFO_LBITS) & LFO_MASK;

                    this.lfoEnvUp[i] = LFO_ENV_TAB[j];
                    this.lfoFreqUp[i] = LFO_FREQ_TAB[j];

                    //Debug.printf(debug_file, "LFO_ENV_UP[%d] = %d   LFO_FREQ_UP[%d] = %d\n", i, this.LFO_ENV_UP[i], i, this.LFO_FREQ_UP[i]);
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
            if (this.channels[5].mute == 0
                    && (this.dac == 0)) {
                vol[0] = 0;
                vol[1] = 0;
                updateChan[this.channels[5].algo + algoType].accept(this.channels[5], buf, length);
                this.channels[5].fmVol[0] = vol[0];
                this.channels[5].fmVol[1] = vol[1];
            }

            this.interCnt = intCnt;

            //Debug.printf(debug_file, "\n\nFinishing generating Sound...\n\n");
        }

        // higher values reduce highpass on DAC
        //enum highpass {
        //  fract = 15,
        //  highpass_shift = 9
        //};

        private void YM2612_DacAndTimers_Update(int[][] buffer, int length) {

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

                    //Debug.printf(debug_file, "Counter A overflow\n");

                    if ((this.mode & 0x80) != 0) controlCsmKey();
                }
            }

            if ((this.mode & 2) != 0) { // Timer B ON ?
                if ((this.timerBCnt -= i) <= 0) {
                    this.status |= (this.mode & 0x08) >> 2;
                    this.timerBCnt += this.timerBL;

                    //Debug.printf(debug_file, "Counter B overflow\n");
                }
            }
        }
    }
}
