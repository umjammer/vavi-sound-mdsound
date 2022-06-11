package mdsound;

public class Ymf271 extends Instrument.BaseInstrument {
    @Override
    public void reset(byte chipID) {
        device_reset_ymf271(chipID);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipID, int clock) {
        return device_start_ymf271(chipID, 16934400);
    }

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        return device_start_ymf271(chipID, clockValue);
    }

    @Override
    public void stop(byte chipID) {
        device_stop_ymf271(chipID);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        ymf271_update(chipID, outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    private int YMF271_Write(byte chipID, int Port, byte Offset, byte Data) {
        ymf271_w(chipID, (Port << 1) | 0x00, Offset);
        ymf271_w(chipID, (Port << 1) | 0x01, Data);
        return 0;
    }


    /*
        Yamaha YMF271-F "OPX" emulator v0.1
        By R. Belmont.
        Based in part on YMF278B emulator by R. Belmont and O. Galibert.
        12June04 update by Toshiaki Nijiura
        Copyright R. Belmont.

        This software is dual-licensed: it may be used in MAME and properly licensed
        MAME derivatives under the terms of the MAME license.  For use outside of
        MAME and properly licensed derivatives, it is available under the
        terms of the GNU Lesser General Public License (LGPL), version 2.1.
        You may read the LGPL at http://www.gnu.org/licenses/lgpl.html

        TODO:
        - A/L bit (alternate loop)
        - EN and EXT Out bits
        - Src B and Src NOTE bits
        - statusreg Busy and End bits
        - timer register 0x11
        - ch2/ch3 (4 speakers)
        - PFM (FM using external PCM waveform)
        - detune (should be same as on other Yamaha chips)
        - Acc On bit (some Sound effects in viprp1?). The documentation says
          "determines if slot output is accumulated(1), or output directly(0)"
        - Is memory handling 100% correct? At the moment, seibuspi.c is the only
          hardware currently emulated that uses external handlers.
    */
    public static class YMF271Chip {

        public static class Slot {

            public byte extEn;
            public byte extOut;
            public byte lfoFreq;
            public byte lfoWave;
            public byte pms, ams;
            public byte detune;
            public byte multiple;
            public byte tl;
            public byte keyScale;
            public byte ar;
            public byte decay1rate, decay2rate;
            public byte decay1lvl;
            public byte relrate;
            public byte block;
            public byte fnsHi;
            public int fns;
            public byte feedback;
            public byte waveForm;
            public byte accon;
            public byte algorithm;
            public byte ch0Level, ch1Level, ch2Level, ch3Level;

            public int startAddr;
            public int loopAddr;
            public int endAddr;
            public byte altLoop;
            public byte fs;
            public byte srcNote, srcb;

            public int step;
            public long stepPtr;

            public byte active;
            public byte bits;

            // envelope generator
            public int volume;
            public int envState;
            public int envAttackStep;      // volume increase step in attack state
            public int envDecay1Step;
            public int envDecay2Step;
            public int envReleaseStep;

            public long feedbackModulation0;
            public long feedbackModulation1;

            public int lfoPhase, lfoStep;
            public int lfoAmplitude;
            public double lfoPhasemod;

            private static final double[] multiple_table = new double[] {0.5, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
            private static final double[] pow_table = new double[] {128, 256, 512, 1024, 2048, 4096, 8192, 16384, 0.5, 1, 2, 4, 8, 16, 32, 64};
            private static final double[] fs_frequency = new double[] {1.0 / 1.0, 1.0 / 2.0, 1.0 / 4.0, 1.0 / 8.0};

            private void calculate_step() {
                double st;

                if (this.waveForm == 7) {
                    // external waveform (PCM)
                    st = (double) (2 * (this.fns | 2048)) * pow_table[this.block] * fs_frequency[this.fs];
                    st = st * multiple_table[this.multiple];

                    // LFO phase modulation
                    st *= this.lfoPhasemod;

                    st /= 524288 / 65536;     // pre-multiply with 65536

                    this.step = (int) st;
                } else {
                    // internal waveform (FM)
                    st = (double) (2 * this.fns) * pow_table[this.block];
                    st = st * multiple_table[this.multiple] * (double) (SIN_LEN);

                    // LFO phase modulation
                    st *= this.lfoPhasemod;

                    st /= 536870912 / 65536;  // pre-multiply with 65536

                    this.step = (int) st;
                }
            }

            private boolean checkEnvelopeEnd() {
                if (this.volume <= 0) {
                    this.active = 0;
                    this.volume = 0;
                    return true;
                }
                return false;
            }

            private void update_envelope() {
                switch (this.envState) {
                case ENV_ATTACK: {
                    this.volume += this.envAttackStep;

                    if (this.volume >= (255 << ENV_VOLUME_SHIFT)) {
                        this.volume = (255 << ENV_VOLUME_SHIFT);
                        this.envState = ENV_DECAY1;
                    }
                    break;
                }

                case ENV_DECAY1: {
                    int decay_level = 255 - (this.decay1lvl << 4);
                    this.volume -= this.envDecay1Step;

                    if (!checkEnvelopeEnd() && (this.volume >> ENV_VOLUME_SHIFT) <= decay_level) {
                        this.envState = ENV_DECAY2;
                    }
                    break;
                }

                case ENV_DECAY2: {
                    this.volume -= this.envDecay2Step;
                    checkEnvelopeEnd();
                    break;
                }

                case ENV_RELEASE: {
                    this.volume -= this.envReleaseStep;
                    checkEnvelopeEnd();
                    break;
                }
                }
            }

            private void init(double[] lutLfo) {
                this.lfoPhase = 0;
                this.lfoAmplitude = 0;
                this.lfoPhasemod = 0;

                this.lfoStep = (int) ((((double) LFO_LENGTH * lutLfo[this.lfoFreq]) / 44100.0) * 256.0);
            }

            private void update(int[][] lutAlfo, double[][][] lutPlfo) {
                this.lfoPhase += this.lfoStep;

                this.lfoAmplitude = lutAlfo[this.lfoWave][(this.lfoPhase >> LFO_SHIFT) & (LFO_LENGTH - 1)];
                this.lfoPhasemod = lutPlfo[this.lfoWave][this.pms][(this.lfoPhase >> LFO_SHIFT) & (LFO_LENGTH - 1)];

                this.calculate_step();
            }
        }

        public static class Group {
            public byte sync, pfm;
            public byte muted;
        }

        private static final int VERBOSE = 1;
        private static final int STD_CLOCK = 16934400;

        private static final int MAXOUT = 32767;
        private static final int MINOUT = -32768;

        private static final int SIN_BITS = 10;
        private static final int SIN_LEN = 1 << SIN_BITS;
        private static final int SIN_MASK = SIN_LEN - 1;

        private static final int LFO_LENGTH = 256;
        private static final int LFO_SHIFT = 8;
        private static final double PLFO_MAX = 1.0;
        private static final double PLFO_MIN = -1.0;
        private static final int ALFO_MAX = 65536;
        private static final int ALFO_MIN = 0;

        private static final int ENV_ATTACK = 0;
        private static final int ENV_DECAY1 = 1;
        private static final int ENV_DECAY2 = 2;
        private static final int ENV_RELEASE = 3;
        private static final int OP_INPUT_FEEDBACK = -1;
        private static final int OP_INPUT_NONE = -2;
        private static final int ENV_VOLUME_SHIFT = 16;
        private static final double INF = -1.0;

        private static final double[] ARTime = new double[] {
                INF, INF, INF, INF, 6188.12, 4980.68, 4144.76, 3541.04,
                3094.06, 2490.34, 2072.38, 1770.52, 1547.03, 1245.17, 1036.19, 885.26,
                773.51, 622.59, 518.10, 441.63, 386.76, 311.29, 259.05, 221.32,
                193.38, 155.65, 129.52, 110.66, 96.69, 77.82, 64.76, 55.33,
                48.34, 38.91, 32.38, 27.66, 24.17, 19.46, 16.19, 13.83,
                12.09, 9.73, 8.10, 6.92, 6.04, 4.86, 4.05, 3.46,
                3.02, 2.47, 2.14, 1.88, 1.70, 1.38, 1.16, 1.02,
                0.88, 0.70, 0.57, 0.48, 0.43, 0.43, 0.43, 0.07
        };

        private static final double[] DCTime = new double[] {
                INF, INF, INF, INF, 93599.64, 74837.91, 62392.02, 53475.56,
                46799.82, 37418.96, 31196.01, 26737.78, 23399.91, 18709.48, 15598.00, 13368.89,
                11699.95, 9354.74, 7799.00, 6684.44, 5849.98, 4677.37, 3899.50, 3342.22,
                2924.99, 2338.68, 1949.75, 1671.11, 1462.49, 1169.34, 974.88, 835.56,
                731.25, 584.67, 487.44, 417.78, 365.62, 292.34, 243.72, 208.89,
                182.81, 146.17, 121.86, 104.44, 91.41, 73.08, 60.93, 52.22,
                45.69, 36.55, 33.85, 26.09, 22.83, 18.28, 15.22, 13.03,
                11.41, 9.12, 7.60, 6.51, 5.69, 5.69, 5.69, 5.69
        };

        /* Notes about the LFO Frequency Table below:

            There are 2 known errors in the LFO table listed in the original manual.

            Both 201 & 202 are listed as 3.74490.  202 has been computed/corrected to 3.91513
            232 was listed as 13.35547 but has been replaced with the correct value of 14.35547.

          Corrections are computed values based on formulas by Olivier Galibert & Nicola Salmoria listed below:

        LFO period seems easy to compute:

        Olivier Galibert's version                       Nicola Salmoria's version

        int lfo_period(int entry)             or         int calc_lfo_period(int entry)
        {                                                {
          int ma, ex;                                      entry = 256 - entry;
          entry = 256-entry;
          ma = entry & 15;                                 if (entry < 16)
                                                           {
          ex = entry >> 4;                                    return (entry & 0x0f) << 7;
          if(ex)                                           }
            return (ma | 16) << (ex+6);                    else
          else                                             {
            return ma << 7;                                   int shift = 6 + (entry >> 4);
        }                                                     return (0x10 + (entry & 0x0f)) << shift;
                                                           }
        lfo_freq = 44100 / lfo_period                    }

        */

        private static final double[] LFO_frequency_table = new double[] {
                0.00066, 0.00068, 0.00070, 0.00073, 0.00075, 0.00078, 0.00081, 0.00084,
                0.00088, 0.00091, 0.00096, 0.00100, 0.00105, 0.00111, 0.00117, 0.00124,
                0.00131, 0.00136, 0.00140, 0.00145, 0.00150, 0.00156, 0.00162, 0.00168,
                0.00175, 0.00183, 0.00191, 0.00200, 0.00210, 0.00221, 0.00234, 0.00247,
                0.00263, 0.00271, 0.00280, 0.00290, 0.00300, 0.00312, 0.00324, 0.00336,
                0.00350, 0.00366, 0.00382, 0.00401, 0.00421, 0.00443, 0.00467, 0.00495,
                0.00526, 0.00543, 0.00561, 0.00580, 0.00601, 0.00623, 0.00647, 0.00673,
                0.00701, 0.00731, 0.00765, 0.00801, 0.00841, 0.00885, 0.00935, 0.00990,
                0.01051, 0.01085, 0.01122, 0.01160, 0.01202, 0.01246, 0.01294, 0.01346,
                0.01402, 0.01463, 0.01529, 0.01602, 0.01682, 0.01771, 0.01869, 0.01979,
                0.02103, 0.02171, 0.02243, 0.02320, 0.02403, 0.02492, 0.02588, 0.02692,
                0.02804, 0.02926, 0.03059, 0.03204, 0.03365, 0.03542, 0.03738, 0.03958,
                0.04206, 0.04341, 0.04486, 0.04641, 0.04807, 0.04985, 0.05176, 0.05383,
                0.05608, 0.05851, 0.06117, 0.06409, 0.06729, 0.07083, 0.07477, 0.07917,
                0.08411, 0.08683, 0.08972, 0.09282, 0.09613, 0.09969, 0.10353, 0.10767,
                0.11215, 0.11703, 0.12235, 0.12817, 0.13458, 0.14167, 0.14954, 0.15833,
                0.16823, 0.17365, 0.17944, 0.18563, 0.19226, 0.19938, 0.20705, 0.21533,
                0.22430, 0.23406, 0.24470, 0.25635, 0.26917, 0.28333, 0.29907, 0.31666,
                0.33646, 0.34731, 0.35889, 0.37126, 0.38452, 0.39876, 0.41410, 0.43066,
                0.44861, 0.46811, 0.48939, 0.51270, 0.53833, 0.56666, 0.59814, 0.63333,
                0.67291, 0.69462, 0.71777, 0.74252, 0.76904, 0.79753, 0.82820, 0.86133,
                0.89722, 0.93623, 0.97878, 1.02539, 1.07666, 1.13333, 1.19629, 1.26666,
                1.34583, 1.38924, 1.43555, 1.48505, 1.53809, 1.59509, 1.65640, 1.72266,
                1.79443, 1.87245, 1.95756, 2.05078, 2.15332, 2.26665, 2.39258, 2.53332,
                2.69165, 2.77848, 2.87109, 2.97010, 3.07617, 3.19010, 3.31280, 3.44531,
                3.58887, 3.74490, 3.91513, 4.10156, 4.30664, 4.53331, 4.78516, 5.06664,
                5.38330, 5.55696, 5.74219, 5.94019, 6.15234, 6.38021, 6.62560, 6.89062,
                7.17773, 7.48981, 7.83026, 8.20312, 8.61328, 9.06661, 9.57031, 10.13327,
                10.76660, 11.11391, 11.48438, 11.88039, 12.30469, 12.76042, 13.25120, 13.78125,
                14.35547, 14.97962, 15.66051, 16.40625, 17.22656, 18.13322, 19.14062, 20.26654,
                21.53320, 22.96875, 24.60938, 26.50240, 28.71094, 31.32102, 34.45312, 38.28125,
                43.06641, 49.21875, 57.42188, 68.90625, 86.13281, 114.84375, 172.26562, 344.53125
        };

        private static final int[][] RKS_Table = new int[][] {
                new int[] {0, 0, 0, 0, 0, 2, 4, 8},
                new int[] {0, 0, 0, 0, 1, 3, 5, 9},
                new int[] {0, 0, 0, 1, 2, 4, 6, 10},
                new int[] {0, 0, 0, 1, 3, 5, 7, 11},
                new int[] {0, 0, 1, 2, 4, 6, 8, 12},
                new int[] {0, 0, 1, 2, 5, 7, 9, 13},
                new int[] {0, 0, 1, 3, 6, 8, 10, 14},
                new int[] {0, 0, 1, 3, 7, 9, 11, 15},
                new int[] {0, 1, 2, 4, 8, 10, 12, 16},
                new int[] {0, 1, 2, 4, 9, 11, 13, 17},
                new int[] {0, 1, 2, 5, 10, 12, 14, 18},
                new int[] {0, 1, 2, 5, 11, 13, 15, 19},
                new int[] {0, 1, 3, 6, 12, 14, 16, 20},
                new int[] {0, 1, 3, 6, 13, 15, 17, 21},
                new int[] {0, 1, 3, 7, 14, 16, 18, 22},
                new int[] {0, 1, 3, 7, 15, 17, 19, 23},
                new int[] {0, 2, 4, 8, 16, 18, 20, 24},
                new int[] {0, 2, 4, 8, 17, 19, 21, 25},
                new int[] {0, 2, 4, 9, 18, 20, 22, 26},
                new int[] {0, 2, 4, 9, 19, 21, 23, 27},
                new int[] {0, 2, 5, 10, 20, 22, 24, 28},
                new int[] {0, 2, 5, 10, 21, 23, 25, 29},
                new int[] {0, 2, 5, 11, 22, 24, 26, 30},
                new int[] {0, 2, 5, 11, 23, 25, 27, 31},
                new int[] {0, 3, 6, 12, 24, 26, 28, 31},
                new int[] {0, 3, 6, 12, 25, 27, 29, 31},
                new int[] {0, 3, 6, 13, 26, 28, 30, 31},
                new int[] {0, 3, 6, 13, 27, 29, 31, 31},
                new int[] {0, 3, 7, 14, 28, 30, 31, 31},
                new int[] {0, 3, 7, 14, 29, 31, 31, 31},
                new int[] {0, 3, 7, 15, 30, 31, 31, 31},
                new int[] {0, 3, 7, 15, 31, 31, 31, 31}
        };

        private static final double[] channelAttenuationTable = new double[] {
                0.0, 2.5, 6.0, 8.5, 12.0, 14.5, 18.1, 20.6, 24.1, 26.6, 30.1, 32.6, 36.1, 96.1, 96.1, 96.1
        };

        private static final int[] modulationLevel = new int[] {16, 8, 4, 2, 1, 32, 64, 128};

        // feedback_level * 16
        private static final int[] feedbackLevel = new int[] {0, 1, 2, 4, 8, 16, 32, 64};

        // slot mapping assists
        private static final int[] fmTab = new int[] {0, 1, 2, -1, 3, 4, 5, -1, 6, 7, 8, -1, 9, 10, 11, -1};
        private static final int[] pcmTab = new int[] {0, 4, 8, -1, 12, 16, 20, -1, 24, 28, 32, -1, 36, 40, 44, -1};

        // lookup tables
        public short[][] lutWaves = new short[8][];
        public double[][][] lutPlfo = new double[][][] {new double[8][], new double[8][], new double[8][], new double[8][]};
        public int[][] lutAlfo = new int[4][];
        public double[] lutAr = new double[64];
        public double[] lutDc = new double[64];
        public double[] lutLfo = new double[256];
        public int[] lutAttenuation = new int[16];
        public int[] lutTotalLevel = new int[128];
        public int[] lutEnvVolume = new int[256];

        public Slot[] slots = new Slot[] {
                new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(),
                new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(),
                new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(),
                new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(),
                new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(),
                new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot()
                };
        public Group[] groups = new Group[] {
                new Group(), new Group(), new Group(), new Group(),
                new Group(), new Group(), new Group(), new Group(),
                new Group(), new Group(), new Group(), new Group()
        };

        public byte[] regsMain = new byte[0x10];

        public int timerA, timerB;
        public int timerAVal = 0, timerBVal = 0;
        public int irqState;
        public byte status;
        public byte enable;

        public int extAddress;
        public byte extRw;
        public byte extReadLatch;

        public byte[] memBase;
        public int memSize;
        public int clock;

        public int[] mix_buffer;

        private static int getKeyscaledRate(int rate, int keycode, int keyscale) {
            int newrate = rate + RKS_Table[keycode][keyscale];

            if (newrate > 63) {
                newrate = 63;
            }
            if (newrate < 0) {
                newrate = 0;
            }
            return newrate;
        }

        private static int getInternalKeycode(int block, int fns) {
            int n43;
            if (fns < 0x780) {
                n43 = 0;
            } else if (fns < 0x900) {
                n43 = 1;
            } else if (fns < 0xa80) {
                n43 = 2;
            } else {
                n43 = 3;
            }

            return ((block & 7) * 4) + n43;
        }

        private static int getExternalKeycode(int block, int fns) {
            int n43;
            if (fns < 0x100) {
                n43 = 0;
            } else if (fns < 0x300) {
                n43 = 1;
            } else if (fns < 0x500) {
                n43 = 2;
            } else {
                n43 = 3;
            }

            return ((block & 7) * 4) + n43;
        }

        private void initEnvelope(Slot slot) {
            int keycode, rate;
            int decay_level = 255 - (slot.decay1lvl << 4);

            if (slot.waveForm != 7) {
                keycode = getInternalKeycode(slot.block, (int) slot.fns);
            } else {
                keycode = getExternalKeycode(slot.block, (int) (slot.fns & 0x7ff));
                /* keycode = (keycode + slot.srcb * 4 + slot.srcnote) / 2; */ // not sure
            }

            // init attack state
            rate = getKeyscaledRate(slot.ar * 2, keycode, slot.keyScale);
            slot.envAttackStep = (rate < 4) ? 0 : (int) (((double) (255 - 0) / this.lutAr[rate]) * 65536.0);

            // init decay1 state
            rate = getKeyscaledRate(slot.decay1rate * 2, keycode, slot.keyScale);
            slot.envDecay1Step = (rate < 4) ? 0 : (int) (((double) (255 - decay_level) / this.lutDc[rate]) * 65536.0);

            // init decay2 state
            rate = getKeyscaledRate(slot.decay2rate * 2, keycode, slot.keyScale);
            slot.envDecay2Step = (rate < 4) ? 0 : (int) (((double) (255 - 0) / this.lutDc[rate]) * 65536.0);

            // init release state
            rate = getKeyscaledRate(slot.relrate * 4, keycode, slot.keyScale);
            slot.envReleaseStep = (rate < 4) ? 0 : (int) (((double) (255 - 0) / this.lutAr[rate]) * 65536.0);

            slot.volume = (255 - 160) << ENV_VOLUME_SHIFT;     // -60db
            slot.envState = ENV_ATTACK;
        }

        private int calculateSlotVolume(Slot slot) {
            // Note: Actually everyone of these stores only int (16.16 fixed point),
            //       but the calculations need long.
            int volume;
            long env_volume;
            long lfo_volume = 65536;

            switch (slot.ams) {
            case 0:
                lfo_volume = 65536;
                break;  // 0dB
            case 1:
                lfo_volume = 65536 - ((slot.lfoAmplitude * 33124) >> 16);
                break;  // 5.90625dB
            case 2:
                lfo_volume = 65536 - ((slot.lfoAmplitude * 16742) >> 16);
                break;  // 11.8125dB
            case 3:
                lfo_volume = 65536 - ((slot.lfoAmplitude * 4277) >> 16);
                break;   // 23.625dB
            }

            env_volume = (this.lutEnvVolume[255 - (slot.volume >> ENV_VOLUME_SHIFT)] * lfo_volume) >> 16;

            volume = (int) ((env_volume * this.lutTotalLevel[slot.tl]) >> 16);

            return volume;
        }

        private void updatePcm(int slotnum, int[] mixp, int length, int ptrMixp) {
            int i;
            long final_volume;
            short sample;
            long ch0_vol, ch1_vol; //, ch2_vol, ch3_vol;

            Slot slot = this.slots[slotnum];

            if (slot.active == 0) {
                return;
            }

            //# ifdef _DEBUG
            //if (slot.waveform != 7)
            //{
            //logerror("Waveform %d in update_pcm !!!\n", slot.waveform);
            //}
            //#endif

            for (i = 0; i < length; i++) {
                // loop
                if ((slot.stepPtr >> 16) > slot.endAddr) {
                    slot.stepPtr = slot.stepPtr - (((long) slot.endAddr << 16) + (long) slot.loopAddr << 16);
                    if ((slot.stepPtr >> 16) > slot.endAddr) {
                        // overflow
                        slot.stepPtr &= 0xffff;
                        slot.stepPtr |= ((long) slot.loopAddr << 16);
                        if ((slot.stepPtr >> 16) > slot.endAddr) {
                            // still overflow? (triggers in rdft2, rarely)
                            slot.stepPtr &= 0xffff;
                            slot.stepPtr |= ((long) slot.endAddr << 16);
                        }
                    }
                }

                if (slot.bits == 8) {
                    // 8bit
                    sample = (short) (readMemory((int) (slot.startAddr + (slot.stepPtr >> 16))) << 8);
                } else {
                    // 12bit
                    if ((slot.stepPtr & 0x10000) != 0)
                        sample = (short) (readMemory((int) (slot.startAddr + (slot.stepPtr >> 17) * 3 + 2)) << 8
                                | ((readMemory((int) (slot.startAddr + (slot.stepPtr >> 17) * 3 + 1)) << 4) & 0xf0));
                    else
                        sample = (short) (readMemory((int) (slot.startAddr + (slot.stepPtr >> 17) * 3)) << 8
                                | (readMemory((int) (slot.startAddr + (slot.stepPtr >> 17) * 3 + 1)) & 0xf0));
                }

                slot.update_envelope();
                slot.update(this.lutAlfo, this.lutPlfo);

                final_volume = calculateSlotVolume(slot);

                ch0_vol = (final_volume * this.lutAttenuation[slot.ch0Level]) >> 16;
                ch1_vol = (final_volume * this.lutAttenuation[slot.ch1Level]) >> 16;
                //  ch2_vol = (final_volume * this.lut_attenuation[slot.ch2_level]) >> 16;
                //  ch3_vol = (final_volume * this.lut_attenuation[slot.ch3_level]) >> 16;

                if (ch0_vol > 65536) ch0_vol = 65536;
                if (ch1_vol > 65536) ch1_vol = 65536;

                mixp[ptrMixp++] += (int) ((sample * ch0_vol) >> 16);
                mixp[ptrMixp++] += (int) ((sample * ch1_vol) >> 16);

                // go to next step
                slot.stepPtr += slot.step;
            }
        }

        // calculates the output of one FM Operator
        private long calculate_op(int slotnum, long inp) {
            Slot slot = this.slots[slotnum];
            long env, slot_output, slot_input = 0;

            slot.update_envelope();
            slot.update(this.lutAlfo, this.lutPlfo);
            env = calculateSlotVolume(slot);

            if (inp == OP_INPUT_FEEDBACK) {
                // from own feedback
                slot_input = (slot.feedbackModulation0 + slot.feedbackModulation1) / 2;
                slot.feedbackModulation0 = slot.feedbackModulation1;
            } else if (inp != OP_INPUT_NONE) {
                // from previous slot output
                slot_input = ((inp << (SIN_BITS - 2)) * modulationLevel[slot.feedback]);
            }

            slot_output = this.lutWaves[slot.waveForm][(int) ((slot.stepPtr + (long) slot_input) >> 16) & SIN_MASK];
            slot_output = (slot_output * env) >> 16;
            slot.stepPtr += slot.step;

            return slot_output;
        }

        private void setFeedback(int slotnum, long inp) {
            Slot slot = this.slots[slotnum];
            slot.feedbackModulation1 = (((inp << (SIN_BITS - 2)) * feedbackLevel[slot.feedback]) / 16);
        }

        public void update(int[][] outputs, int samples) {
            int i, j;
            int op;
            int[] mixp;
            int ptrMixp;

            for (i = 0; i < samples * 2; i++) this.mix_buffer[i] = 0;
            //memset(this.mix_buffer, 0, sizeof(this.mix_buffer[0]) * samples * 2);

            for (j = 0; j < 12; j++) {
                Group slot_group = this.groups[j];
                mixp = this.mix_buffer;//[0];
                ptrMixp = 0;

                if (slot_group.muted != 0)
                    continue;

                //# ifdef _DEBUG
                //if (slot_group.pfm && slot_group.sync != 3)
                //{
                //logerror("Ymf271 Group %d: PFM, Sync = %d, Waveform Slot1 = %d, Slot2 = %d, Slot3 = %d, Slot4 = %d\n",
                //j, slot_group.sync, this.slots[j + 0].waveform, this.slots[j + 12].waveform, this.slots[j + 24].waveform, this.slots[j + 36].waveform);
                //}
                //#endif

                switch (slot_group.sync) {
                // 4 Operator FM
                case 0: {
                    int slot1 = j + (0 * 12);
                    int slot2 = j + (1 * 12);
                    int slot3 = j + (2 * 12);
                    int slot4 = j + (3 * 12);
                    //mixp = this.mix_buffer;

                    if (this.slots[slot1].active != 0) {
                        for (i = 0; i < samples; i++) {
                            long output1 = 0, output2 = 0, output3 = 0, output4 = 0;
                            long phase_mod1 = 0, phase_mod2 = 0, phase_mod3 = 0;
                            switch (this.slots[slot1].algorithm) {
                            // <--------|
                            // +--[S1]--|--+--[S3]--+--[S2]--+--[S4]-.
                            case 0:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                phase_mod3 = calculate_op(slot3, phase_mod1);
                                phase_mod2 = calculate_op(slot2, phase_mod3);
                                output4 = calculate_op(slot4, phase_mod2);
                                break;

                            // <-----------------|
                            // +--[S1]--+--[S3]--|--+--[S2]--+--[S4]-.
                            case 1:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                phase_mod3 = calculate_op(slot3, phase_mod1);
                                setFeedback(slot1, phase_mod3);
                                phase_mod2 = calculate_op(slot2, phase_mod3);
                                output4 = calculate_op(slot4, phase_mod2);
                                break;

                            // <--------|
                            // +--[S1]--|
                            //          |
                            //  --[S3]--+--[S2]--+--[S4]-.
                            case 2:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                phase_mod3 = calculate_op(slot3, OP_INPUT_NONE);
                                phase_mod2 = calculate_op(slot2, (phase_mod1 + phase_mod3) / 1);
                                output4 = calculate_op(slot4, phase_mod2);
                                break;

                            //          <--------|
                            //          +--[S1]--|
                            //                   |
                            //  --[S3]--+--[S2]--+--[S4]-.
                            case 3:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                phase_mod3 = calculate_op(slot3, OP_INPUT_NONE);
                                phase_mod2 = calculate_op(slot2, phase_mod3);
                                output4 = calculate_op(slot4, (phase_mod1 + phase_mod2) / 1);
                                break;

                            //              --[S2]--|
                            // <--------|           |
                            // +--[S1]--|--+--[S3]--+--[S4]-.
                            case 4:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                phase_mod3 = calculate_op(slot3, phase_mod1);
                                phase_mod2 = calculate_op(slot2, OP_INPUT_NONE);
                                output4 = calculate_op(slot4, (phase_mod3 + phase_mod2) / 1);
                                break;

                            //           --[S2]-----|
                            // <-----------------|  |
                            // +--[S1]--+--[S3]--|--+--[S4]-.
                            case 5:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                phase_mod3 = calculate_op(slot3, phase_mod1);
                                setFeedback(slot1, phase_mod3);
                                phase_mod2 = calculate_op(slot2, OP_INPUT_NONE);
                                output4 = calculate_op(slot4, (phase_mod3 + phase_mod2) / 1);
                                break;

                            //  --[S2]-----+--[S4]--|
                            //                      |
                            // <--------|           |
                            // +--[S1]--|--+--[S3]--+-.
                            case 6:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                output3 = calculate_op(slot3, phase_mod1);
                                phase_mod2 = calculate_op(slot2, OP_INPUT_NONE);
                                output4 = calculate_op(slot4, phase_mod2);
                                break;

                            //  --[S2]--+--[S4]-----|
                            //                      |
                            // <-----------------|  |
                            // +--[S1]--+--[S3]--|--+-.
                            case 7:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                phase_mod3 = calculate_op(slot3, phase_mod1);
                                setFeedback(slot1, phase_mod3);
                                output3 = phase_mod3;
                                phase_mod2 = calculate_op(slot2, OP_INPUT_NONE);
                                output4 = calculate_op(slot4, phase_mod2);
                                break;

                            //  --[S3]--+--[S2]--+--[S4]--|
                            //                            |
                            // <--------|                 |
                            // +--[S1]--|-----------------+-.
                            case 8:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                output1 = phase_mod1;
                                phase_mod3 = calculate_op(slot3, OP_INPUT_NONE);
                                phase_mod2 = calculate_op(slot2, phase_mod3);
                                output4 = calculate_op(slot4, phase_mod2);
                                break;

                            //          <--------|
                            //          +--[S1]--|
                            //                   |
                            //  --[S3]--|        |
                            //  --[S2]--+--[S4]--+-.
                            case 9:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                output1 = phase_mod1;
                                phase_mod3 = calculate_op(slot3, OP_INPUT_NONE);
                                phase_mod2 = calculate_op(slot2, OP_INPUT_NONE);
                                output4 = calculate_op(slot4, (phase_mod3 + phase_mod2) / 1);
                                break;

                            //              --[S4]--|
                            //              --[S2]--|
                            // <--------|           |
                            // +--[S1]--|--+--[S3]--+-.
                            case 10:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                output3 = calculate_op(slot3, phase_mod1);
                                output2 = calculate_op(slot2, OP_INPUT_NONE);
                                output4 = calculate_op(slot4, OP_INPUT_NONE);
                                break;

                            //           --[S4]-----|
                            //           --[S2]-----|
                            // <-----------------|  |
                            // +--[S1]--+--[S3]--|--+-.
                            case 11:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                phase_mod3 = calculate_op(slot3, phase_mod1);
                                setFeedback(slot1, phase_mod3);
                                output3 = phase_mod3;
                                output2 = calculate_op(slot2, OP_INPUT_NONE);
                                output4 = calculate_op(slot4, OP_INPUT_NONE);
                                break;

                            //             |--+--[S4]--|
                            // <--------|  |--+--[S3]--|
                            // +--[S1]--|--|--+--[S2]--+-.
                            case 12:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                output3 = calculate_op(slot3, phase_mod1);
                                output2 = calculate_op(slot2, phase_mod1);
                                output4 = calculate_op(slot4, phase_mod1);
                                break;

                            //  --[S3]--+--[S2]--|
                            //                   |
                            //  --[S4]-----------|
                            // <--------|        |
                            // +--[S1]--|--------+-.
                            case 13:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                output1 = phase_mod1;
                                phase_mod3 = calculate_op(slot3, OP_INPUT_NONE);
                                output2 = calculate_op(slot2, phase_mod3);
                                output4 = calculate_op(slot4, OP_INPUT_NONE);
                                break;

                            //  --[S2]-----+--[S4]--|
                            //                      |
                            // <--------|  +--[S3]--|
                            // +--[S1]--|--|--------+-.
                            case 14:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                output1 = phase_mod1;
                                output3 = calculate_op(slot3, phase_mod1);
                                phase_mod2 = calculate_op(slot2, OP_INPUT_NONE);
                                output4 = calculate_op(slot4, phase_mod2);
                                break;

                            //  --[S4]-----|
                            //  --[S2]-----|
                            //  --[S3]-----|
                            // <--------|  |
                            // +--[S1]--|--+-.
                            case 15:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                output1 = phase_mod1;
                                output3 = calculate_op(slot3, OP_INPUT_NONE);
                                output2 = calculate_op(slot2, OP_INPUT_NONE);
                                output4 = calculate_op(slot4, OP_INPUT_NONE);
                                break;
                            }

                            mixp[ptrMixp++] += (int) ((output1 * this.lutAttenuation[this.slots[slot1].ch0Level]) +
                                    (output2 * this.lutAttenuation[this.slots[slot2].ch0Level]) +
                                    (output3 * this.lutAttenuation[this.slots[slot3].ch0Level]) +
                                    (output4 * this.lutAttenuation[this.slots[slot4].ch0Level])) >> 16;
                            mixp[ptrMixp++] += (int) ((output1 * this.lutAttenuation[this.slots[slot1].ch1Level]) +
                                    (output2 * this.lutAttenuation[this.slots[slot2].ch1Level]) +
                                    (output3 * this.lutAttenuation[this.slots[slot3].ch1Level]) +
                                    (output4 * this.lutAttenuation[this.slots[slot4].ch1Level])) >> 16;
                        }
                    }
                    break;
                }

                // 2x 2 Operator FM
                case 1: {
                    for (op = 0; op < 2; op++) {
                        int slot1 = j + ((op + 0) * 12);
                        int slot3 = j + ((op + 2) * 12);

                        mixp = this.mix_buffer;
                        if (this.slots[slot1].active != 0) {
                            for (i = 0; i < samples; i++) {
                                long output1 = 0, output3 = 0;
                                long phase_mod1, phase_mod3 = 0;
                                switch (this.slots[slot1].algorithm & 3) {
                                // <--------|
                                // +--[S1]--|--+--[S3]-.
                                case 0:
                                    phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                    setFeedback(slot1, phase_mod1);
                                    output3 = calculate_op(slot3, phase_mod1);
                                    break;

                                // <-----------------|
                                // +--[S1]--+--[S3]--|-.
                                case 1:
                                    phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                    phase_mod3 = calculate_op(slot3, phase_mod1);
                                    setFeedback(slot1, phase_mod3);
                                    output3 = phase_mod3;
                                    break;

                                //  --[S3]-----|
                                // <--------|  |
                                // +--[S1]--|--+-.
                                case 2:
                                    phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                    setFeedback(slot1, phase_mod1);
                                    output1 = phase_mod1;
                                    output3 = calculate_op(slot3, OP_INPUT_NONE);
                                    break;
                                //
                                // <--------|  +--[S3]--|
                                // +--[S1]--|--|--------+-.
                                case 3:
                                    phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                    setFeedback(slot1, phase_mod1);
                                    output1 = phase_mod1;
                                    output3 = calculate_op(slot3, phase_mod1);
                                    break;
                                }

                                mixp[ptrMixp++] += (int) ((output1 * this.lutAttenuation[this.slots[slot1].ch0Level]) +
                                        (output3 * this.lutAttenuation[this.slots[slot3].ch0Level])) >> 16;
                                mixp[ptrMixp++] += (int) ((output1 * this.lutAttenuation[this.slots[slot1].ch1Level]) +
                                        (output3 * this.lutAttenuation[this.slots[slot3].ch1Level])) >> 16;
                            }
                        }
                    }
                    break;
                }

                // 3 Operator FM + PCM
                case 2: {
                    int slot1 = j + (0 * 12);
                    int slot2 = j + (1 * 12);
                    int slot3 = j + (2 * 12);
                    //mixp = this.mix_buffer;

                    if (this.slots[slot1].active != 0) {
                        for (i = 0; i < samples; i++) {
                            long output1 = 0, output2 = 0, output3 = 0;
                            long phase_mod1 = 0, phase_mod3 = 0;
                            switch (this.slots[slot1].algorithm & 7) {
                            // <--------|
                            // +--[S1]--|--+--[S3]--+--[S2]-.
                            case 0:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                phase_mod3 = calculate_op(slot3, phase_mod1);
                                output2 = calculate_op(slot2, phase_mod3);
                                break;

                            // <-----------------|
                            // +--[S1]--+--[S3]--|--+--[S2]-.
                            case 1:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                phase_mod3 = calculate_op(slot3, phase_mod1);
                                setFeedback(slot1, phase_mod3);
                                output2 = calculate_op(slot2, phase_mod3);
                                break;

                            //  --[S3]-----|
                            // <--------|  |
                            // +--[S1]--|--+--[S2]-.
                            case 2:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                phase_mod3 = calculate_op(slot3, OP_INPUT_NONE);
                                output2 = calculate_op(slot2, (phase_mod1 + phase_mod3) / 1);
                                break;

                            //  --[S3]--+--[S2]--|
                            // <--------|        |
                            // +--[S1]--|--------+-.
                            case 3:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                output1 = phase_mod1;
                                phase_mod3 = calculate_op(slot3, OP_INPUT_NONE);
                                output2 = calculate_op(slot2, phase_mod3);
                                break;

                            //              --[S2]--|
                            // <--------|           |
                            // +--[S1]--|--+--[S3]--+-.
                            case 4:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                output3 = calculate_op(slot3, phase_mod1);
                                output2 = calculate_op(slot2, OP_INPUT_NONE);
                                break;

                            //              --[S2]--|
                            // <-----------------|  |
                            // +--[S1]--+--[S3]--|--+-.
                            case 5:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                phase_mod3 = calculate_op(slot3, phase_mod1);
                                setFeedback(slot1, phase_mod3);
                                output3 = phase_mod3;
                                output2 = calculate_op(slot2, OP_INPUT_NONE);
                                break;

                            //  --[S2]-----|
                            //  --[S3]-----|
                            // <--------|  |
                            // +--[S1]--|--+-.
                            case 6:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                output1 = phase_mod1;
                                output3 = calculate_op(slot3, OP_INPUT_NONE);
                                output2 = calculate_op(slot2, OP_INPUT_NONE);
                                break;

                            //              --[S2]--|
                            // <--------|  +--[S3]--|
                            // +--[S1]--|--|--------+-.
                            case 7:
                                phase_mod1 = calculate_op(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phase_mod1);
                                output1 = phase_mod1;
                                output3 = calculate_op(slot3, phase_mod1);
                                output2 = calculate_op(slot2, OP_INPUT_NONE);
                                break;
                            }

                            mixp[ptrMixp++] += (int) ((output1 * this.lutAttenuation[this.slots[slot1].ch0Level]) +
                                    (output2 * this.lutAttenuation[this.slots[slot2].ch0Level]) +
                                    (output3 * this.lutAttenuation[this.slots[slot3].ch0Level])) >> 16;
                            mixp[ptrMixp++] += (int) ((output1 * this.lutAttenuation[this.slots[slot1].ch1Level]) +
                                    (output2 * this.lutAttenuation[this.slots[slot2].ch1Level]) +
                                    (output3 * this.lutAttenuation[this.slots[slot3].ch1Level])) >> 16;
                        }
                    }

                    mixp = this.mix_buffer;
                    updatePcm(j + (3 * 12), mixp, samples, ptrMixp);
                    break;
                }

                // PCM
                case 3: {
                    updatePcm(j + (0 * 12), mixp, samples, ptrMixp);
                    updatePcm(j + (1 * 12), mixp, samples, ptrMixp);
                    updatePcm(j + (2 * 12), mixp, samples, ptrMixp);
                    updatePcm(j + (3 * 12), mixp, samples, ptrMixp);
                    break;
                }
                }
            }

            mixp = this.mix_buffer;
            ptrMixp = 0;
            for (i = 0; i < samples; i++) {
                outputs[0][i] = mixp[ptrMixp++] >> 2;
                outputs[1][i] = mixp[ptrMixp++] >> 2;
            }
        }

        private void writeRegister(int slotnum, int reg, byte data) {
            Slot slot = this.slots[slotnum];

            switch (reg) {
            case 0x0:
                slot.extEn = (byte) ((data & 0x80) != 0 ? 1 : 0);
                slot.extOut = (byte) ((data >> 3) & 0xf);

                if ((data & 1) != 0) {
                    // key on
                    slot.step = 0;
                    slot.stepPtr = 0;

                    slot.active = 1;

                    slot.calculate_step();
                    initEnvelope(slot);
                    slot.init(this.lutLfo);
                    slot.feedbackModulation0 = 0;
                    slot.feedbackModulation1 = 0;
                } else {
                    if (slot.active != 0) {
                        slot.envState = ENV_RELEASE;
                    }
                }
                break;

            case 0x1:
                slot.lfoFreq = data;
                break;

            case 0x2:
                slot.lfoWave = (byte) (data & 3);
                slot.pms = (byte) ((data >> 3) & 0x7);
                slot.ams = (byte) ((data >> 6) & 0x3);
                break;

            case 0x3:
                slot.multiple = (byte) (data & 0xf);
                slot.detune = (byte) ((data >> 4) & 0x7);
                break;

            case 0x4:
                slot.tl = (byte) (data & 0x7f);
                break;

            case 0x5:
                slot.ar = (byte) (data & 0x1f);
                slot.keyScale = (byte) ((data >> 5) & 0x7);
                break;

            case 0x6:
                slot.decay1rate = (byte) (data & 0x1f);
                break;

            case 0x7:
                slot.decay2rate = (byte) (data & 0x1f);
                break;

            case 0x8:
                slot.relrate = (byte) (data & 0xf);
                slot.decay1lvl = (byte) ((data >> 4) & 0xf);
                break;

            case 0x9:
                // write frequency and block here
                slot.fns = (int) ((slot.fnsHi << 8 & 0x0f00) | data);
                slot.block = (byte) (slot.fnsHi >> 4 & 0xf);
                break;

            case 0xa:
                slot.fnsHi = data;
                break;

            case 0xb:
                slot.waveForm = (byte) (data & 0x7);
                slot.feedback = (byte) ((data >> 4) & 0x7);
                slot.accon = (byte) ((data & 0x80) != 0 ? 1 : 0);
                break;

            case 0xc:
                slot.algorithm = (byte) (data & 0xf);
                break;

            case 0xd:
                slot.ch0Level = (byte) (data >> 4);
                slot.ch1Level = (byte) (data & 0xf);
                break;

            case 0xe:
                slot.ch2Level = (byte) (data >> 4);
                slot.ch3Level = (byte) (data & 0xf);
                break;

            default:
                break;
            }
        }

        private void writeFm(int bank, byte address, byte data) {
            int groupnum = fmTab[address & 0xf];
            int reg = (address >> 4) & 0xf;
            int sync_reg;
            int sync_mode;

            if (groupnum == -1) {
                //System.err.printf("ymf271_write_fm invalid group {0:X02} {1:X02}", address, data);
                return;
            }

            // check if the register is a synchronized register
            sync_reg = 0;
            switch (reg) {
            case 0:
            case 9:
            case 10:
            case 12:
            case 13:
            case 14:
                sync_reg = 1;
                break;

            default:
                break;
            }

            // check if the slot is key on slot for synchronizing
            sync_mode = 0;
            switch (this.groups[groupnum].sync) {
            // 4 slot mode
            case 0:
                if (bank == 0)
                    sync_mode = 1;
                break;

            // 2x 2 slot mode
            case 1:
                if (bank == 0 || bank == 1)
                    sync_mode = 1;
                break;

            // 3 slot + 1 slot mode
            case 2:
                if (bank == 0)
                    sync_mode = 1;
                break;

            default:
                break;
            }

            // key-on slot & synced register
            if (sync_mode != 0 && sync_reg != 0) {
                switch (this.groups[groupnum].sync) {
                // 4 slot mode
                case 0:
                    writeRegister((12 * 0) + groupnum, reg, data);
                    writeRegister((12 * 1) + groupnum, reg, data);
                    writeRegister((12 * 2) + groupnum, reg, data);
                    writeRegister((12 * 3) + groupnum, reg, data);
                    break;

                // 2x 2 slot mode
                case 1:
                    if (bank == 0) {
                        // Slot 1 - Slot 3
                        writeRegister((12 * 0) + groupnum, reg, data);
                        writeRegister((12 * 2) + groupnum, reg, data);
                    } else {
                        // Slot 2 - Slot 4
                        writeRegister((12 * 1) + groupnum, reg, data);
                        writeRegister((12 * 3) + groupnum, reg, data);
                    }
                    break;

                // 3 slot + 1 slot mode (1 slot is handled normally)
                case 2:
                    writeRegister((12 * 0) + groupnum, reg, data);
                    writeRegister((12 * 1) + groupnum, reg, data);
                    writeRegister((12 * 2) + groupnum, reg, data);
                    break;
                }
            } else {
                // write register normally
                writeRegister((12 * bank) + groupnum, reg, data);
            }
        }

        private void writePcm(byte address, byte data) {
            int slotnum = pcmTab[address & 0xf];
            Slot slot;
            if (slotnum == -1) {
                //System.err.printf("ymf271_write_pcm invalid slot {0:X02} {1:X02}", address, data);
                return;
            }
            slot = this.slots[slotnum];

            switch ((address >> 4) & 0xf) {
            case 0x0:
                slot.startAddr &= 0xffffff00;// ~0xff;
                slot.startAddr |= data;
                break;

            case 0x1:
                slot.startAddr &= 0xffff00ff;// ~0xff00;
                slot.startAddr |= (int) (data << 8);
                break;

            case 0x2:
                slot.startAddr &= 0xff00ffff;// ~0xff0000;
                slot.startAddr |= (int) ((data & 0x7f) << 16);
                slot.altLoop = (byte) ((data & 0x80) != 0 ? 1 : 0);
                //if (slot.altloop)
                // System.err.println("Ymf271 A/L, contact MAMEdev");
                break;

            case 0x3:
                slot.endAddr &= 0xffffff00;// ~0xff;
                slot.endAddr |= data;
                break;

            case 0x4:
                slot.endAddr &= 0xffff00ff;// ~0xff00;
                slot.endAddr |= (int) (data << 8);
                break;

            case 0x5:
                slot.endAddr &= 0xff00ffff;// ~0xff0000;
                slot.endAddr |= (int) ((data & 0x7f) << 16);
                break;

            case 0x6:
                slot.loopAddr &= 0xffffff00;// ~0xff;
                slot.loopAddr |= data;
                break;

            case 0x7:
                slot.loopAddr &= 0xffff00ff;// ~0xff00;
                slot.loopAddr |= (int) (data << 8);
                break;

            case 0x8:
                slot.loopAddr &= 0xff00ffff;// ~0xff0000;
                slot.loopAddr |= (int) ((data & 0x7f) << 16);
                break;

            case 0x9:
                slot.fs = (byte) (data & 0x3);
                slot.bits = (byte) ((data & 0x4) != 0 ? 12 : 8);
                slot.srcNote = (byte) ((data >> 3) & 0x3);
                slot.srcb = (byte) ((data >> 5) & 0x7);
                break;

            default:
                break;
            }
        }

        /*static TIMER_CALLBACK( ymf271_timer_a_tick )
        {
            YMF271Chip *chip = (YMF271Chip *)ptr;

            this.status |= 1;

            if (this.enable & 4)
            {
                this.irqstate |= 1;
                if (this.irq_callback) this.irq_callback(this.device, 1);
            }
        }

        static TIMER_CALLBACK( ymf271_timer_b_tick )
        {
            YMF271Chip *chip = (YMF271Chip *)ptr;

            this.status |= 2;

            if (this.enable & 8)
            {
                this.irqstate |= 2;
                if (this.irq_callback) this.irq_callback(this.device, 1);
            }
        }*/

        private byte readMemory(int offset) {
            /*if (m_ext_read_handler.isnull())
            {
                if (offset < this.mem_size)
                    return this.mem_base[offset];

                // 8MB chip limit (shouldn't happen)
                else if (offset > 0x7fffff)
                    return this.mem_base[offset & 0x7fffff];

                else
                    return 0;
            }
            else
                return m_ext_read_handler(offset);*/

            offset &= 0x7FFFFF;
            if (offset < this.memSize)
                return this.memBase[offset];
            else
                return 0;
        }

        private void writeTimer(byte address, byte data) {
            if ((address & 0xf0) == 0) {
                int groupnum = fmTab[address & 0xf];
                Group group;
                if (groupnum == -1) {
                    //System.err.printf("ymf271_write_timer invalid group {0:X02} {1:X02}", address, data);
                    return;
                }
                group = this.groups[groupnum];

                group.sync = (byte) (data & 0x3);
                group.pfm = (byte) (data >> 7);
            } else {
                switch (address) {
                case 0x10:
                    this.timerA = data;
                    break;

                case 0x11:
                    // According to Yamaha's documentation, this sets timer A upper 2 bits
                    // (it says timer A is 10 bits). But, PCB audio recordings proves
                    // otherwise: it doesn't affect timer A frequency. (see ms32.c tetrisp)
                    // Does this register have another function regarding timer A/B?
                    break;

                case 0x12:
                    this.timerB = data;
                    break;

                case 0x13:
                    // timer A load
                    //if (~this.enable & data & 1) {
                    //attotime period = attotime::from_hz(this.clock) * (384 * 4 * (256 - this.timerA));
                    //this.timA.adjust((data & 1) ? period : attotime::never, 0);
                    //}

                    // timer B load
                    //if (~this.enable & data & 2) {
                    //attotime period = attotime::from_hz(this.clock) * (384 * 16 * (256 - this.timerB));
                    //this.timB.adjust((data & 2) ? period : attotime::never, 0);
                    //}

                    // timer A reset
                    if ((data & 0x10) != 0) {
                        this.irqState &= 0xfffffffe;// ~1;
                        this.status &= 0xfe; //~1;

                        //if (!this.irq_handler.isnull() && ~this.irqstate & 2)
                        // this.irq_handler(0);
                    }

                    // timer B reset
                    if ((data & 0x20) != 0) {
                        this.irqState &= 0xfffffffd;//~2;
                        this.status &= 0xfd;// ~2;

                        //if (!this.irq_handler.isnull() && ~this.irqstate & 1)
                        // this.irq_handler(0);
                    }

                    this.enable = data;
                    break;

                case 0x14:
                    this.extAddress &= 0xffffff00;// ~0xff;
                    this.extAddress |= data;
                    break;
                case 0x15:
                    this.extAddress &= 0xffff00ff;// ~0xff00;
                    this.extAddress |= (int) (data << 8);
                    break;
                case 0x16:
                    this.extAddress &= 0xff00ffff;// ~0xff0000;
                    this.extAddress |= (int) ((data & 0x7f) << 16);
                    this.extRw = (byte) ((data & 0x80) != 0 ? 1 : 0);
                    break;
                case 0x17:
                    this.extAddress = (this.extAddress + 1) & 0x7fffff;
                    //if (!this.ext_rw && !this.ext_write_handler.isnull())
                    // this.ext_write_handler(this.ext_address, data);
                    break;
                }
            }
        }

        public void write(int offset, byte data) {
            this.regsMain[offset & 0xf] = data;

            switch (offset & 0xf) {
            case 0x0:
            case 0x2:
            case 0x4:
            case 0x6:
            case 0x8:
            case 0xc:
                // address regs
                break;

            case 0x1:
                writeFm(0, this.regsMain[0x0], data);
                break;

            case 0x3:
                writeFm(1, this.regsMain[0x2], data);
                break;

            case 0x5:
                writeFm(2, this.regsMain[0x4], data);
                break;

            case 0x7:
                writeFm(3, this.regsMain[0x6], data);
                break;

            case 0x9:
                writePcm(this.regsMain[0x8], data);
                break;

            case 0xd:
                writeTimer(this.regsMain[0xc], data);
                break;

            default:
                break;
            }
        }

        public byte read(int offset) {
            switch (offset & 0xf) {
            case 0x0:
                return this.status;

            case 0x1:
                // statusreg 2
                return 0;

            case 0x2: {
                byte ret;
                if (this.extRw == 0)
                    return (byte) 0xff;

                ret = this.extReadLatch;
                this.extAddress = (this.extAddress + 1) & 0x7fffff;
                this.extReadLatch = readMemory(this.extAddress);
                return ret;
            }

            default:
                break;
            }

            return (byte) 0xff;
        }


        private void initTables() {
            int i, j;
            double clock_correction;

            for (i = 0; i < 8; i++)
                this.lutWaves[i] = new short[SIN_LEN];

            for (i = 0; i < 4 * 8; i++)
                this.lutPlfo[i >> 3][i & 7] = new double[LFO_LENGTH];

            for (i = 0; i < 4; i++)
                this.lutAlfo[i] = new int[LFO_LENGTH];

            for (i = 0; i < SIN_LEN; i++) {
                double m = Math.sin(((i * 2) + 1) * Math.PI / SIN_LEN);
                double m2 = Math.sin(((i * 4) + 1) * Math.PI / SIN_LEN);

                // Waveform 0: sin(wt)    (0 <= wt <= 2PI)
                this.lutWaves[0][i] = (short) (m * MAXOUT);

                // Waveform 1: sin?(wt)   (0 <= wt <= PI)     -sin?(wt)  (PI <= wt <= 2PI)
                this.lutWaves[1][i] = (i < (SIN_LEN / 2)) ? (short) ((m * m) * MAXOUT) : (short) ((m * m) * MINOUT);

                // Waveform 2: sin(wt)    (0 <= wt <= PI)     -sin(wt)   (PI <= wt <= 2PI)
                this.lutWaves[2][i] = (i < (SIN_LEN / 2)) ? (short) (m * MAXOUT) : (short) (-m * MAXOUT);

                // Waveform 3: sin(wt)    (0 <= wt <= PI)     0
                this.lutWaves[3][i] = (i < (SIN_LEN / 2)) ? (short) (m * MAXOUT) : (short) 0;

                // Waveform 4: sin(2wt)   (0 <= wt <= PI)     0
                this.lutWaves[4][i] = (i < (SIN_LEN / 2)) ? (short) (m2 * MAXOUT) : (short) 0;

                // Waveform 5: |sin(2wt)| (0 <= wt <= PI)     0
                this.lutWaves[5][i] = (i < (SIN_LEN / 2)) ? (short) (Math.abs(m2) * MAXOUT) : (short) 0;

                // Waveform 6:     1      (0 <= wt <= 2PI)
                this.lutWaves[6][i] = (short) (1 * MAXOUT);

                this.lutWaves[7][i] = 0;
            }

            for (i = 0; i < LFO_LENGTH; i++) {
                int tri_wave;
                double ftri_wave, fsaw_wave;
                double[] plfo = new double[4];

                // LFO phase modulation
                plfo[0] = 0;

                fsaw_wave = ((i % (LFO_LENGTH / 2)) * PLFO_MAX) / (double) ((LFO_LENGTH / 2) - 1);
                plfo[1] = (i < (LFO_LENGTH / 2)) ? fsaw_wave : fsaw_wave - PLFO_MAX;

                plfo[2] = (i < (LFO_LENGTH / 2)) ? PLFO_MAX : PLFO_MIN;

                ftri_wave = ((i % (LFO_LENGTH / 4)) * PLFO_MAX) / (double) (LFO_LENGTH / 4);
                switch (i / (LFO_LENGTH / 4)) {
                case 0:
                    plfo[3] = ftri_wave;
                    break;
                case 1:
                    plfo[3] = PLFO_MAX - ftri_wave;
                    break;
                case 2:
                    plfo[3] = 0 - ftri_wave;
                    break;
                case 3:
                    plfo[3] = 0 - (PLFO_MAX - ftri_wave);
                    break;
                default:
                    plfo[3] = 0; /*assert(0);*/
                    break;
                }

                for (j = 0; j < 4; j++) {
                    this.lutPlfo[j][0][i] = Math.pow(2.0, 0.0);
                    this.lutPlfo[j][1][i] = Math.pow(2.0, (3.378 * plfo[j]) / 1200.0);
                    this.lutPlfo[j][2][i] = Math.pow(2.0, (5.0646 * plfo[j]) / 1200.0);
                    this.lutPlfo[j][3][i] = Math.pow(2.0, (6.7495 * plfo[j]) / 1200.0);
                    this.lutPlfo[j][4][i] = Math.pow(2.0, (10.1143 * plfo[j]) / 1200.0);
                    this.lutPlfo[j][5][i] = Math.pow(2.0, (20.1699 * plfo[j]) / 1200.0);
                    this.lutPlfo[j][6][i] = Math.pow(2.0, (40.1076 * plfo[j]) / 1200.0);
                    this.lutPlfo[j][7][i] = Math.pow(2.0, (79.307 * plfo[j]) / 1200.0);
                }

                // LFO amplitude modulation
                this.lutAlfo[0][i] = 0;

                this.lutAlfo[1][i] = ALFO_MAX - ((i * ALFO_MAX) / LFO_LENGTH);

                this.lutAlfo[2][i] = (i < (LFO_LENGTH / 2)) ? ALFO_MAX : ALFO_MIN;

                tri_wave = ((i % (LFO_LENGTH / 2)) * ALFO_MAX) / (LFO_LENGTH / 2);
                this.lutAlfo[3][i] = (i < (LFO_LENGTH / 2)) ? ALFO_MAX - tri_wave : tri_wave;
            }

            for (i = 0; i < 256; i++) {
                this.lutEnvVolume[i] = (int) (65536.0 / Math.pow(10.0, ((double) i / (256.0 / 96.0)) / 20.0));
            }

            for (i = 0; i < 16; i++) {
                this.lutAttenuation[i] = (int) (65536.0 / Math.pow(10.0, channelAttenuationTable[i] / 20.0));
            }
            for (i = 0; i < 128; i++) {
                double db = 0.75 * (double) i;
                this.lutTotalLevel[i] = (int) (65536.0 / Math.pow(10.0, db / 20.0));
            }

            // timing may use a non-standard XTAL
            clock_correction = (double) (STD_CLOCK) / (double) (this.clock);
            for (i = 0; i < 256; i++) {
                this.lutLfo[i] = LFO_frequency_table[i] * clock_correction;
            }

            for (i = 0; i < 64; i++) {
                // attack/release rate in number of samples
                this.lutAr[i] = (ARTime[i] * clock_correction * 44100.0) / 1000.0;
            }
            for (i = 0; i < 64; i++) {
                // decay rate in number of samples
                this.lutDc[i] = (DCTime[i] * clock_correction * 44100.0) / 1000.0;
            }
        }

        public int start(int clock) {
            this.clock = clock;

            //intf = (device.static_config != NULL) ? (final ymf271_interface *)device.static_config : &defintrf;

            this.memSize = 0x00;
            this.memBase = null;

            initTables();
            //init_state(chip);
            //this.stream = stream_create(device, 0, 2, device.clock/384, ymf271_update);

            //this.mix_buffer = auto_alloc_array(machine, int, 44100*2);
            this.mix_buffer = new int[44100 * 2];// (int*)malloc(44100 * 2 * sizeof(int));

            for (int i = 0; i < 12; i++)
                this.groups[i].muted = 0x00;

            return clock / 384;
        }

        public void stop() {
            this.memBase = null;

            for (int i = 0; i < 8; i++) {
                this.lutWaves[i] = null;
            }
            for (int i = 0; i < 4 * 8; i++) {
                this.lutPlfo[i >> 3][i & 7] = null;
            }

            for (int i = 0; i < 4; i++) {
                this.lutAlfo[i] = null;
            }

            this.mix_buffer = null;
        }

        public void reset() {
            for (int i = 0; i < 48; i++) {
                this.slots[i].active = 0;
                this.slots[i].volume = 0;
            }

            // reset timers and IRQ
            //this.timA.reset();
            //this.timB.reset();

            this.irqState = 0;
            this.status = 0;
            this.enable = 0;

            //if (!this.irq_handler.isnull())
            // this.irq_handler(0);
        }

        public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
            if (this.memSize != romSize) {
                this.memBase = new byte[romSize];// (byte*)realloc(this.mem_base, romSize);
                this.memSize = romSize;
                for (int i = 0; i < romSize; i++) this.memBase[i] = (byte) 0xff;
                //memset(this.mem_base, 0xFF, romSize);
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            if (dataLength >= 0) System.arraycopy(romData, 0, this.memBase, dataStart, dataLength);
        }

        public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
            if (this.memSize != romSize) {
                this.memBase = new byte[romSize];// (byte*)realloc(this.mem_base, romSize);
                this.memSize = romSize;
                for (int i = 0; i < romSize; i++) this.memBase[i] = (byte) 0xff;
                //memset(this.mem_base, 0xFF, romSize);
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            if (dataLength >= 0)
                System.arraycopy(romData, srcStartAddress, this.memBase, dataStart, dataLength);
        }

        public void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < 12; curChn++)
                this.groups[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
        }
    }

    private static final int MAX_CHIPS = 0x10;
    public YMF271Chip[] YMF271Data = new YMF271Chip[] {new YMF271Chip(), new YMF271Chip(),};// MAX_CHIPS];

    @Override
    public String getName() {
        return "YMF271";
    }

    @Override
    public String getShortName() {
        return "OPX";
    }

    public void ymf271_update(byte chipID, int[][] outputs, int samples) {
        YMF271Chip chip = YMF271Data[chipID];
        chip.update(outputs, samples);
    }

    public void ymf271_w(byte chipID, int offset, byte data) {
        YMF271Chip chip = YMF271Data[chipID];
        chip.write(offset, data);
    }

    public byte ymf271_r(byte chipID, int offset) {
        YMF271Chip chip = YMF271Data[chipID];
        return chip.read(offset);
    }

    public int device_start_ymf271(byte chipID, int clock) {
        if (chipID >= MAX_CHIPS)
            return 0;

        YMF271Chip chip = YMF271Data[chipID];
        return chip.start(clock);
    }

    public void device_stop_ymf271(byte chipID) {
        YMF271Chip chip = YMF271Data[chipID];
        chip.stop();
    }

    public void device_reset_ymf271(byte chipID) {
        YMF271Chip chip = YMF271Data[chipID];
        chip.reset();
    }

    public void ymf271_write_rom(byte chipID, int romSize, int dataStart, int dataLength, byte[] romData) {
        YMF271Chip chip = YMF271Data[chipID];
        chip.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void ymf271_write_rom(byte chipID, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        YMF271Chip chip = YMF271Data[chipID];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    public void ymf271_set_mute_mask(byte chipID, int muteMask) {
        YMF271Chip chip = YMF271Data[chipID];
        chip.setMuteMask(muteMask);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        YMF271_Write(chipID, port, (byte) adr, (byte) data);
        return 0;
    }

    /**
     * Generic get_info
     */
        /*DEVICE_GET_INFO( Ymf271 )
        {
            switch (state)
            {
                // --- the following bits of info are returned as 64-bit signed integers ---
                case DEVINFO_INT_TOKEN_BYTES:     info.i = sizeof(YMF271Chip);      break;

                // --- the following bits of info are returned as pointers to data or functions
                case DEVINFO_FCT_START:       info.start = DEVICE_START_NAME( Ymf271 );   break;
                case DEVINFO_FCT_STOP:       // Nothing         break;
                case DEVINFO_FCT_RESET:       info.reset = DEVICE_RESET_NAME( Ymf271 );   break;

                // --- the following bits of info are returned as NULL-terminated strings ---
                case DEVINFO_STR_NAME:       strcpy(info.s, "YMF271");      break;
                case DEVINFO_STR_FAMILY:     strcpy(info.s, "Yamaha FM");     break;
                case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
                case DEVINFO_STR_SOURCE_FILE:      strcpy(info.s, __FILE__);      break;
                case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
            }
        }*/
}
