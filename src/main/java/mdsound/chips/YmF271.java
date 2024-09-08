package mdsound.chips;

import java.util.Arrays;
import java.util.function.Function;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;


/**
 * YmF271.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2022-07-08 nsano initial version <br>
 */ /*
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
public class YmF271 {

    // TODO stop public access, toJson?
    public static class Slot {

        private int extEn;
        private int extOut;
        public int lfoFreq;
        public int lfoWave;
        public int pms;
        public int ams;
        public int detune;
        public int multiple;
        public int tl;
        public int keyScale;
        public int ar;
        public int decay1rate, decay2rate;
        public int decay1lvl;
        public int relrate;
        public int block;
        private int fnsHi;
        public int fns;
        public int feedback;
        public int waveForm;
        public int accon;
        public int algorithm;
        public int ch0Level, ch1Level, ch2Level, ch3Level;

        public int startAddr;
        public int loopAddr;
        public int endAddr;
        private int altLoop;
        public int fs;
        public int srcNote, srcb;

        private int step;
        private int stepPtr;

        public int active;
        public int bits;

        // envelope generator
        public int volume;
        private int envState;
        private int envAttackStep; // volume increase step in attack state
        private int envDecay1Step;
        private int envDecay2Step;
        private int envReleaseStep;

        private int feedbackModulation0;
        private int feedbackModulation1;

        private int lfoPhase, lfoStep;
        private int lfoAmplitude;
        private double lfoPhasemod;

        private static final double[] multiple_table = new double[] {0.5, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        private static final double[] pow_table = new double[] {128, 256, 512, 1024, 2048, 4096, 8192, 16384, 0.5, 1, 2, 4, 8, 16, 32, 64};
        private static final double[] fs_frequency = new double[] {1.0 / 1.0, 1.0 / 2.0, 1.0 / 4.0, 1.0 / 8.0};

        private void calculateStep() {
            double st;

            if (this.waveForm == 7) {
                // external waveform (PCM)
                st = (2 * (this.fns | 2048)) * pow_table[this.block] * fs_frequency[this.fs];
                st = st * multiple_table[this.multiple];

                // LFO phase modulation
                st *= this.lfoPhasemod;

                st /= 524288d / 65536; // pre-multiply with 65536

                this.step = (int) st;
            } else {
                // internal waveform (FM)
                st = (double) (2 * this.fns) * pow_table[this.block];
                st = st * multiple_table[this.multiple] * (double) (SIN_LEN);

                // LFO phase modulation
                st *= this.lfoPhasemod;

                st /= 536870912d / 65536; // pre-multiply with 65536

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

        @SuppressFBWarnings("SF_SWITCH_NO_DEFAULT")
        private void updateEnvelope() {
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
                int decayLevel = 255 - (this.decay1lvl << 4);
                this.volume -= this.envDecay1Step;

                if (!checkEnvelopeEnd() && (this.volume >> ENV_VOLUME_SHIFT) <= decayLevel) {
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

        private void update() {
            this.lfoPhase += this.lfoStep;

            this.lfoAmplitude = lutALfo[this.lfoWave][(this.lfoPhase >> LFO_SHIFT) & (LFO_LENGTH - 1)];
            this.lfoPhasemod = lutPlfo[this.lfoWave][this.pms][(this.lfoPhase >> LFO_SHIFT) & (LFO_LENGTH - 1)];

            this.calculateStep();
        }

        private int calculateOp(int inp) {
            int env, slotOutput, slotInput = 0;

            this.updateEnvelope();
            this.update();
            env = calculateVolume();

            if (inp == OP_INPUT_FEEDBACK) {
                // from own feedback
                slotInput = (this.feedbackModulation0 + this.feedbackModulation1) / 2;
                this.feedbackModulation0 = this.feedbackModulation1;
            } else if (inp != OP_INPUT_NONE) {
                // from previous slot output
                slotInput = (inp << (SIN_BITS - 2)) * modulationLevel[this.feedback];
            }

            slotOutput = lutWaves[this.waveForm][((this.stepPtr + slotInput) >> 16) & SIN_MASK] & 0xffff;
            slotOutput = (slotOutput * env) >> 16;
            this.stepPtr += this.step;

            return slotOutput;
        }

        @SuppressFBWarnings("SF_SWITCH_NO_DEFAULT")
        private int calculateVolume() {
            // Note: Actually every one of these stores only int (16.16 fixed point),
            //       but the calculations need long.
            int volume;
            int envVolume;
            int lfoVolume = 65536;

            switch (ams) {
            case 0:
                lfoVolume = 65536;
                break; // 0dB
            case 1:
                lfoVolume = 65536 - ((lfoAmplitude * 33124) >> 16);
                break; // 5.90625dB
            case 2:
                lfoVolume = 65536 - ((lfoAmplitude * 16742) >> 16);
                break; // 11.8125dB
            case 3:
                lfoVolume = 65536 - ((lfoAmplitude * 4277) >> 16);
                break; // 23.625dB
            }

            envVolume = (lutEnvVolume[255 - (this.volume >> ENV_VOLUME_SHIFT)] * lfoVolume) >> 16;

            volume = (envVolume * lutTotalLevel[tl]) >> 16;

            return volume;
        }

        private void keyOn(double[] lutLfo, double[] lutAr, double[] lutDc) {
            this.step = 0;
            this.stepPtr = 0;

            this.active = 1;

            this.calculateStep();
            initEnvelope(lutAr, lutDc);
            this.init(lutLfo);
            this.feedbackModulation0 = 0;
            this.feedbackModulation1 = 0;
        }

        private void initEnvelope(double[] lutAr, double[] lutDc) {
            int keycode, rate;
            int decay_level = 255 - (decay1lvl << 4);

            if (waveForm != 7) {
                keycode = getInternalKeycode(block, fns);
            } else {
                keycode = getExternalKeycode(block, fns & 0x7ff);
                // keycode = (keycode + slot.srcb * 4 + slot.srcnote) / 2; // not sure
            }

            // init attack state
            rate = getKeyscaledRate(ar * 2, keycode, keyScale);
            envAttackStep = (rate < 4) ? 0 : (int) (((255 - 0) / lutAr[rate]) * 65536.0);

            // init decay1 state
            rate = getKeyscaledRate(decay1rate * 2, keycode, keyScale);
            envDecay1Step = (rate < 4) ? 0 : (int) (((255 - decay_level) / lutDc[rate]) * 65536.0);

            // init decay2 state
            rate = getKeyscaledRate(decay2rate * 2, keycode, keyScale);
            envDecay2Step = (rate < 4) ? 0 : (int) (((255 - 0) / lutDc[rate]) * 65536.0);

            // init release state
            rate = getKeyscaledRate(relrate * 4, keycode, keyScale);
            envReleaseStep = (rate < 4) ? 0 : (int) (((255 - 0) / lutAr[rate]) * 65536.0);

            volume = (255 - 160) << ENV_VOLUME_SHIFT; // -60db
            envState = ENV_ATTACK;
        }

        private void updatePcm(int[] mixP, int length, int ptrMixP, Function<Integer, Integer> readMemory) {
            if (this.active == 0) {
                return;
            }

            //if (this.waveform != 7) {
            // Debug.printf("Waveform %d in update_pcm !!!\n", this.waveform);
            //}

            for (int i = 0; i < length; i++) {
                // loop
                if ((this.stepPtr >> 16) > this.endAddr) {
                    this.stepPtr = this.stepPtr - ((this.endAddr << 16) + this.loopAddr << 16);
                    if ((this.stepPtr >> 16) > this.endAddr) {
                        // overflow
                        this.stepPtr &= 0xffff;
                        this.stepPtr |= (this.loopAddr << 16);
                        if ((this.stepPtr >> 16) > this.endAddr) {
                            // still overflow? (triggers in rdft2, rarely)
                            this.stepPtr &= 0xffff;
                            this.stepPtr |= (this.endAddr << 16);
                        }
                    }
                }

                int sample;
                if (this.bits == 8) {
                    // 8bit
                    sample = (readMemory.apply(this.startAddr + (this.stepPtr >> 16)) & 0xff) << 8;
                } else {
                    // 12bit
                    if ((this.stepPtr & 0x10000) != 0)
                        sample = (readMemory.apply(this.startAddr + (this.stepPtr >> 17) * 3 + 2) & 0xff) << 8
                                | (readMemory.apply(this.startAddr + (this.stepPtr >> 17) * 3 + 1) << 4) & 0xf0;
                    else
                        sample = (readMemory.apply(this.startAddr + (this.stepPtr >> 17) * 3) & 0xff) << 8
                                | readMemory.apply(this.startAddr + (this.stepPtr >> 17) * 3 + 1) & 0xf0;
                }

                this.updateEnvelope();
                this.update();

                int finalVolume = this.calculateVolume();

                int ch0Vol = ((finalVolume * lutAttenuation[this.ch0Level]) >> 16) & 0xffff;
                int ch1Vol = ((finalVolume * lutAttenuation[this.ch1Level]) >> 16) & 0xffff;

//                if (ch0Vol > 65536) ch0Vol = 65536;
//                if (ch1Vol > 65536) ch1Vol = 65536;

                mixP[ptrMixP++] += (sample * ch0Vol) >> 16;
                mixP[ptrMixP++] += (sample * ch1Vol) >> 16;

                // go to next step
                this.stepPtr += this.step;
            }
        }
    }

    private static class Group {
        private int sync, pfm;
        private int muted;
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

    /*
     * Notes about the LFO Frequency Table below:
     *
     * There are 2 known errors in the LFO table listed in the original manual.
     *
     * Both 201 & 202 are listed as 3.74490.  202 has been computed/corrected to 3.91513
     * 232 was listed as 13.35547 but has been replaced with the correct value of 14.35547.
     *
     * Corrections are computed values based on formulas by Olivier Galibert & Nicola Salmoria listed below:
     * </pre>
     *
     *  LFO period seems easy to compute:
     *
     *  Olivier Galibert's version                       Nicola Salmoria's version
     *
     *  int lfo_period(int entry) {           or         int calc_lfo_period(int entry) {
     *    int ma, ex;                                      entry = 256 - entry;
     *    entry = 256-entry;
     *    ma = entry & 15;                                 if (entry < 16) {
     *    ex = entry >> 4;                                    return (entry & 0x0f) << 7;
     *    if(ex)                                           }
     *      return (ma | 16) << (ex+6);                    else {
     *    else                                               int shift = 6 + (entry >> 4);
     *      return ma << 7;                                  return (0x10 + (entry & 0x0f)) << shift;
     *  }                                                  }
     *  lfo_freq = 44100 / lfo_period                    }
     *
     *   <pre>
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
    private static short[][] lutWaves = new short[8][];
    private static double[][][] lutPlfo = new double[][][] {new double[8][], new double[8][], new double[8][], new double[8][]};
    private static int[][] lutALfo = new int[4][];
    private double[] lutAr = new double[64];
    private double[] lutDc = new double[64];
    private double[] lutLfo = new double[256];
    private static int[] lutAttenuation = new int[16];
    private static int[] lutTotalLevel = new int[128];
    private static int[] lutEnvVolume = new int[256];

    private Slot[] slots = new Slot[] {
            new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(),
            new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(),
            new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(),
            new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(),
            new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(),
            new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot(), new Slot()
    };
    private Group[] groups = new Group[] {
            new Group(), new Group(), new Group(), new Group(),
            new Group(), new Group(), new Group(), new Group(),
            new Group(), new Group(), new Group(), new Group()
    };

    private byte[] regsMain = new byte[0x10];

    private int timerA, timerB;
    private int timerAVal = 0, timerBVal = 0;
    private int irqState;
    private int status;
    private int enable;

    private int extAddress;
    private int extRw;
    private int extReadLatch;

    private byte[] memBase;
    private int memSize;
    private int clock;

    private int[] mixBuffer;

    private static int getKeyscaledRate(int rate, int keyCode, int keyScale) {
        int newRate = rate + RKS_Table[keyCode][keyScale];

        if (newRate > 63) {
            newRate = 63;
        }
        if (newRate < 0) {
            newRate = 0;
        }
        return newRate;
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

    private void updatePcm(int slotNum, int[] mixP, int length, int ptrMixP) {
        Slot slot = this.slots[slotNum];
        slot.updatePcm(mixP, length, ptrMixP, this::readMemory);
    }

    // calculates the output of one FM Operator
    private int calculateOp(int slotNum, int inp) {
        Slot slot = this.slots[slotNum];
        return slot.calculateOp(inp);
    }

    private void setFeedback(int slotnum, int inp) {
        Slot slot = this.slots[slotnum];
        slot.feedbackModulation1 = (((inp << (SIN_BITS - 2)) * feedbackLevel[slot.feedback]) / 16);
    }

    public void update(int[][] outputs, int samples) {

        Arrays.fill(this.mixBuffer, 0, samples * 2, (byte) 0);

        for (int j = 0; j < 12; j++) {
            Group slotGroup = this.groups[j];
            int[] mixP = this.mixBuffer;
            int ptrMixp = 0;

            if (slotGroup.muted != 0)
                continue;

//if (slotGroup.pfm && slotGroup.sync != 3) {
// Debug.printf("YmF271 Group %d: PFM, Sync = %d, Waveform Slot1 = %d, Slot2 = %d, Slot3 = %d, Slot4 = %d\n",
//  j, slotGroup.sync, this.slots[j + 0].waveform, this.slots[j + 12].waveform, this.slots[j + 24].waveform, this.slots[j + 36].waveform);
//}

            switch (slotGroup.sync) {
            // 4 Operator FM
            case 0: {
                int slot1 = j + (0 * 12);
                int slot2 = j + (1 * 12);
                int slot3 = j + (2 * 12);
                int slot4 = j + (3 * 12);

                if (this.slots[slot1].active != 0) {
                    for (int i = 0; i < samples; i++) {
                        int output1 = 0, output2 = 0, output3 = 0, output4 = 0;
                        int phaseMod1, phaseMod2, phaseMod3;
                        switch (this.slots[slot1].algorithm) {
                        // <--------|
                        // +--[S1]--|--+--[S3]--+--[S2]--+--[S4]-.
                        case 0:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            phaseMod3 = calculateOp(slot3, phaseMod1);
                            phaseMod2 = calculateOp(slot2, phaseMod3);
                            output4 = calculateOp(slot4, phaseMod2);
                            break;

                        // <-----------------|
                        // +--[S1]--+--[S3]--|--+--[S2]--+--[S4]-.
                        case 1:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            phaseMod3 = calculateOp(slot3, phaseMod1);
                            setFeedback(slot1, phaseMod3);
                            phaseMod2 = calculateOp(slot2, phaseMod3);
                            output4 = calculateOp(slot4, phaseMod2);
                            break;

                        // <--------|
                        // +--[S1]--|
                        //          |
                        //  --[S3]--+--[S2]--+--[S4]-.
                        case 2:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            phaseMod3 = calculateOp(slot3, OP_INPUT_NONE);
                            phaseMod2 = calculateOp(slot2, (phaseMod1 + phaseMod3) / 1);
                            output4 = calculateOp(slot4, phaseMod2);
                            break;

                        //          <--------|
                        //          +--[S1]--|
                        //                   |
                        //  --[S3]--+--[S2]--+--[S4]-.
                        case 3:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            phaseMod3 = calculateOp(slot3, OP_INPUT_NONE);
                            phaseMod2 = calculateOp(slot2, phaseMod3);
                            output4 = calculateOp(slot4, (phaseMod1 + phaseMod2) / 1);
                            break;

                        //              --[S2]--|
                        // <--------|           |
                        // +--[S1]--|--+--[S3]--+--[S4]-.
                        case 4:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            phaseMod3 = calculateOp(slot3, phaseMod1);
                            phaseMod2 = calculateOp(slot2, OP_INPUT_NONE);
                            output4 = calculateOp(slot4, (phaseMod3 + phaseMod2) / 1);
                            break;

                        //           --[S2]-----|
                        // <-----------------|  |
                        // +--[S1]--+--[S3]--|--+--[S4]-.
                        case 5:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            phaseMod3 = calculateOp(slot3, phaseMod1);
                            setFeedback(slot1, phaseMod3);
                            phaseMod2 = calculateOp(slot2, OP_INPUT_NONE);
                            output4 = calculateOp(slot4, (phaseMod3 + phaseMod2) / 1);
                            break;

                        //  --[S2]-----+--[S4]--|
                        //                      |
                        // <--------|           |
                        // +--[S1]--|--+--[S3]--+-.
                        case 6:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            output3 = calculateOp(slot3, phaseMod1);
                            phaseMod2 = calculateOp(slot2, OP_INPUT_NONE);
                            output4 = calculateOp(slot4, phaseMod2);
                            break;

                        //  --[S2]--+--[S4]-----|
                        //                      |
                        // <-----------------|  |
                        // +--[S1]--+--[S3]--|--+-.
                        case 7:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            phaseMod3 = calculateOp(slot3, phaseMod1);
                            setFeedback(slot1, phaseMod3);
                            output3 = phaseMod3;
                            phaseMod2 = calculateOp(slot2, OP_INPUT_NONE);
                            output4 = calculateOp(slot4, phaseMod2);
                            break;

                        //  --[S3]--+--[S2]--+--[S4]--|
                        //                            |
                        // <--------|                 |
                        // +--[S1]--|-----------------+-.
                        case 8:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            output1 = phaseMod1;
                            phaseMod3 = calculateOp(slot3, OP_INPUT_NONE);
                            phaseMod2 = calculateOp(slot2, phaseMod3);
                            output4 = calculateOp(slot4, phaseMod2);
                            break;

                        //          <--------|
                        //          +--[S1]--|
                        //                   |
                        //  --[S3]--|        |
                        //  --[S2]--+--[S4]--+-.
                        case 9:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            output1 = phaseMod1;
                            phaseMod3 = calculateOp(slot3, OP_INPUT_NONE);
                            phaseMod2 = calculateOp(slot2, OP_INPUT_NONE);
                            output4 = calculateOp(slot4, (phaseMod3 + phaseMod2) / 1);
                            break;

                        //              --[S4]--|
                        //              --[S2]--|
                        // <--------|           |
                        // +--[S1]--|--+--[S3]--+-.
                        case 10:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            output3 = calculateOp(slot3, phaseMod1);
                            output2 = calculateOp(slot2, OP_INPUT_NONE);
                            output4 = calculateOp(slot4, OP_INPUT_NONE);
                            break;

                        //           --[S4]-----|
                        //           --[S2]-----|
                        // <-----------------|  |
                        // +--[S1]--+--[S3]--|--+-.
                        case 11:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            phaseMod3 = calculateOp(slot3, phaseMod1);
                            setFeedback(slot1, phaseMod3);
                            output3 = phaseMod3;
                            output2 = calculateOp(slot2, OP_INPUT_NONE);
                            output4 = calculateOp(slot4, OP_INPUT_NONE);
                            break;

                        //             |--+--[S4]--|
                        // <--------|  |--+--[S3]--|
                        // +--[S1]--|--|--+--[S2]--+-.
                        case 12:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            output3 = calculateOp(slot3, phaseMod1);
                            output2 = calculateOp(slot2, phaseMod1);
                            output4 = calculateOp(slot4, phaseMod1);
                            break;

                        //  --[S3]--+--[S2]--|
                        //                   |
                        //  --[S4]-----------|
                        // <--------|        |
                        // +--[S1]--|--------+-.
                        case 13:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            output1 = phaseMod1;
                            phaseMod3 = calculateOp(slot3, OP_INPUT_NONE);
                            output2 = calculateOp(slot2, phaseMod3);
                            output4 = calculateOp(slot4, OP_INPUT_NONE);
                            break;

                        //  --[S2]-----+--[S4]--|
                        //                      |
                        // <--------|  +--[S3]--|
                        // +--[S1]--|--|--------+-.
                        case 14:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            output1 = phaseMod1;
                            output3 = calculateOp(slot3, phaseMod1);
                            phaseMod2 = calculateOp(slot2, OP_INPUT_NONE);
                            output4 = calculateOp(slot4, phaseMod2);
                            break;

                        //  --[S4]-----|
                        //  --[S2]-----|
                        //  --[S3]-----|
                        // <--------|  |
                        // +--[S1]--|--+-.
                        case 15:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            output1 = phaseMod1;
                            output3 = calculateOp(slot3, OP_INPUT_NONE);
                            output2 = calculateOp(slot2, OP_INPUT_NONE);
                            output4 = calculateOp(slot4, OP_INPUT_NONE);
                            break;
                        }

                        mixP[ptrMixp++] += (int) ((output1 * lutAttenuation[this.slots[slot1].ch0Level]) +
                                (output2 * lutAttenuation[this.slots[slot2].ch0Level]) +
                                (output3 * lutAttenuation[this.slots[slot3].ch0Level]) +
                                (output4 * lutAttenuation[this.slots[slot4].ch0Level])) >> 16;
                        mixP[ptrMixp++] += (int) ((output1 * lutAttenuation[this.slots[slot1].ch1Level]) +
                                (output2 * lutAttenuation[this.slots[slot2].ch1Level]) +
                                (output3 * lutAttenuation[this.slots[slot3].ch1Level]) +
                                (output4 * lutAttenuation[this.slots[slot4].ch1Level])) >> 16;
                    }
                }
                break;
            }

            // 2x 2 Operator FM
            case 1: {
                for (int op = 0; op < 2; op++) {
                    int slot1 = j + ((op + 0) * 12);
                    int slot3 = j + ((op + 2) * 12);

                    mixP = this.mixBuffer;
                    if (this.slots[slot1].active != 0) {
                        for (int i = 0; i < samples; i++) {
                            int output1 = 0, output3 = 0;
                            int phaseMod1, phaseMod3;
                            switch (this.slots[slot1].algorithm & 3) {
                            // <--------|
                            // +--[S1]--|--+--[S3]-.
                            case 0:
                                phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phaseMod1);
                                output3 = calculateOp(slot3, phaseMod1);
                                break;

                            // <-----------------|
                            // +--[S1]--+--[S3]--|-.
                            case 1:
                                phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                                phaseMod3 = calculateOp(slot3, phaseMod1);
                                setFeedback(slot1, phaseMod3);
                                output3 = phaseMod3;
                                break;

                            //  --[S3]-----|
                            // <--------|  |
                            // +--[S1]--|--+-.
                            case 2:
                                phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phaseMod1);
                                output1 = phaseMod1;
                                output3 = calculateOp(slot3, OP_INPUT_NONE);
                                break;
                            //
                            // <--------|  +--[S3]--|
                            // +--[S1]--|--|--------+-.
                            case 3:
                                phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                                setFeedback(slot1, phaseMod1);
                                output1 = phaseMod1;
                                output3 = calculateOp(slot3, phaseMod1);
                                break;
                            }

                            mixP[ptrMixp++] += ((output1 * lutAttenuation[this.slots[slot1].ch0Level]) +
                                    (output3 * lutAttenuation[this.slots[slot3].ch0Level])) >> 16;
                            mixP[ptrMixp++] += ((output1 * lutAttenuation[this.slots[slot1].ch1Level]) +
                                    (output3 * lutAttenuation[this.slots[slot3].ch1Level])) >> 16;
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
                //mixP = this.mix_buffer;

                if (this.slots[slot1].active != 0) {
                    for (int i = 0; i < samples; i++) {
                        int output1 = 0, output2 = 0, output3 = 0;
                        int phaseMod1, phaseMod3;
                        switch (this.slots[slot1].algorithm & 7) {
                        // <--------|
                        // +--[S1]--|--+--[S3]--+--[S2]-.
                        case 0:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            phaseMod3 = calculateOp(slot3, phaseMod1);
                            output2 = calculateOp(slot2, phaseMod3);
                            break;

                        // <-----------------|
                        // +--[S1]--+--[S3]--|--+--[S2]-.
                        case 1:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            phaseMod3 = calculateOp(slot3, phaseMod1);
                            setFeedback(slot1, phaseMod3);
                            output2 = calculateOp(slot2, phaseMod3);
                            break;

                        //  --[S3]-----|
                        // <--------|  |
                        // +--[S1]--|--+--[S2]-.
                        case 2:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            phaseMod3 = calculateOp(slot3, OP_INPUT_NONE);
                            output2 = calculateOp(slot2, (phaseMod1 + phaseMod3) / 1);
                            break;

                        //  --[S3]--+--[S2]--|
                        // <--------|        |
                        // +--[S1]--|--------+-.
                        case 3:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            output1 = phaseMod1;
                            phaseMod3 = calculateOp(slot3, OP_INPUT_NONE);
                            output2 = calculateOp(slot2, phaseMod3);
                            break;

                        //              --[S2]--|
                        // <--------|           |
                        // +--[S1]--|--+--[S3]--+-.
                        case 4:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            output3 = calculateOp(slot3, phaseMod1);
                            output2 = calculateOp(slot2, OP_INPUT_NONE);
                            break;

                        //              --[S2]--|
                        // <-----------------|  |
                        // +--[S1]--+--[S3]--|--+-.
                        case 5:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            phaseMod3 = calculateOp(slot3, phaseMod1);
                            setFeedback(slot1, phaseMod3);
                            output3 = phaseMod3;
                            output2 = calculateOp(slot2, OP_INPUT_NONE);
                            break;

                        //  --[S2]-----|
                        //  --[S3]-----|
                        // <--------|  |
                        // +--[S1]--|--+-.
                        case 6:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            output1 = phaseMod1;
                            output3 = calculateOp(slot3, OP_INPUT_NONE);
                            output2 = calculateOp(slot2, OP_INPUT_NONE);
                            break;

                        //              --[S2]--|
                        // <--------|  +--[S3]--|
                        // +--[S1]--|--|--------+-.
                        case 7:
                            phaseMod1 = calculateOp(slot1, OP_INPUT_FEEDBACK);
                            setFeedback(slot1, phaseMod1);
                            output1 = phaseMod1;
                            output3 = calculateOp(slot3, phaseMod1);
                            output2 = calculateOp(slot2, OP_INPUT_NONE);
                            break;
                        }

                        mixP[ptrMixp++] += (int) ((output1 * lutAttenuation[this.slots[slot1].ch0Level]) +
                                (output2 * lutAttenuation[this.slots[slot2].ch0Level]) +
                                (output3 * lutAttenuation[this.slots[slot3].ch0Level])) >> 16;
                        mixP[ptrMixp++] += (int) ((output1 * lutAttenuation[this.slots[slot1].ch1Level]) +
                                (output2 * lutAttenuation[this.slots[slot2].ch1Level]) +
                                (output3 * lutAttenuation[this.slots[slot3].ch1Level])) >> 16;
                    }
                }

                mixP = this.mixBuffer;
                updatePcm(j + (3 * 12), mixP, samples, ptrMixp);
                break;
            }

            // PCM
            case 3: {
                updatePcm(j + (0 * 12), mixP, samples, ptrMixp);
                updatePcm(j + (1 * 12), mixP, samples, ptrMixp);
                updatePcm(j + (2 * 12), mixP, samples, ptrMixp);
                updatePcm(j + (3 * 12), mixP, samples, ptrMixp);
                break;
            }
            }
        }

        int[] mixP = this.mixBuffer;
        int ptrMixp = 0;
        for (int i = 0; i < samples; i++) {
            outputs[0][i] = mixP[ptrMixp++] >> 2;
            outputs[1][i] = mixP[ptrMixp++] >> 2;
        }
    }

    private void writeRegister(int slotnum, int reg, int data) {
        Slot slot = this.slots[slotnum];

        switch (reg) {
        case 0x0:
            slot.extEn = (data & 0x80) != 0 ? 1 : 0;
            slot.extOut = (data >> 3) & 0xf;

            if ((data & 1) != 0) {
                // key on
                slot.keyOn(this.lutLfo, this.lutAr, this.lutDc);
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
            slot.lfoWave = data & 3;
            slot.pms = (data >> 3) & 0x7;
            slot.ams = (data >> 6) & 0x3;
            break;

        case 0x3:
            slot.multiple = data & 0xf;
            slot.detune = (data >> 4) & 0x7;
            break;

        case 0x4:
            slot.tl = data & 0x7f;
            break;

        case 0x5:
            slot.ar = data & 0x1f;
            slot.keyScale = (data >> 5) & 0x7;
            break;

        case 0x6:
            slot.decay1rate = data & 0x1f;
            break;

        case 0x7:
            slot.decay2rate = data & 0x1f;
            break;

        case 0x8:
            slot.relrate = data & 0xf;
            slot.decay1lvl = (data >> 4) & 0xf;
            break;

        case 0x9:
            // write frequency and block here
            slot.fns = (slot.fnsHi << 8 & 0x0f00) | data;
            slot.block = slot.fnsHi >> 4 & 0xf;
            break;

        case 0xa:
            slot.fnsHi = data;
            break;

        case 0xb:
            slot.waveForm = data & 0x7;
            slot.feedback = (data >> 4) & 0x7;
            slot.accon = (data & 0x80) != 0 ? 1 : 0;
            break;

        case 0xc:
            slot.algorithm = data & 0xf;
            break;

        case 0xd:
            slot.ch0Level = data >> 4;
            slot.ch1Level = data & 0xf;
            break;

        case 0xe:
            slot.ch2Level = data >> 4;
            slot.ch3Level = data & 0xf;
            break;

        default:
            break;
        }
    }

    private void writeFm(int bank, int address, int data) {
        int groupNum = fmTab[address & 0xf];
        int reg = (address >> 4) & 0xf;
        int syncReg;
        int syncMode;

        if (groupNum == -1) {
            //Debug.printf("ymf271_write_fm invalid group %02X %02X", address, data);
            return;
        }

        // check if the register is a synchronized register
        syncReg = 0;
        switch (reg) {
        case 0:
        case 9:
        case 10:
        case 12:
        case 13:
        case 14:
            syncReg = 1;
            break;

        default:
            break;
        }

        // check if the slot is key on slot for synchronizing
        syncMode = 0;
        switch (this.groups[groupNum].sync) {
        // 4 slot mode
        case 0:
            if (bank == 0)
                syncMode = 1;
            break;

        // 2x 2 slot mode
        case 1:
            if (bank == 0 || bank == 1)
                syncMode = 1;
            break;

        // 3 slot + 1 slot mode
        case 2:
            if (bank == 0)
                syncMode = 1;
            break;

        default:
            break;
        }

        // key-on slot & synced register
        if (syncMode != 0 && syncReg != 0) {
            switch (this.groups[groupNum].sync) {
            // 4 slot mode
            case 0:
                writeRegister((12 * 0) + groupNum, reg, data);
                writeRegister((12 * 1) + groupNum, reg, data);
                writeRegister((12 * 2) + groupNum, reg, data);
                writeRegister((12 * 3) + groupNum, reg, data);
                break;

            // 2x 2 slot mode
            case 1:
                if (bank == 0) {
                    // Slot 1 - Slot 3
                    writeRegister((12 * 0) + groupNum, reg, data);
                    writeRegister((12 * 2) + groupNum, reg, data);
                } else {
                    // Slot 2 - Slot 4
                    writeRegister((12 * 1) + groupNum, reg, data);
                    writeRegister((12 * 3) + groupNum, reg, data);
                }
                break;

            // 3 slot + 1 slot mode (1 slot is handled normally)
            case 2:
                writeRegister((12 * 0) + groupNum, reg, data);
                writeRegister((12 * 1) + groupNum, reg, data);
                writeRegister((12 * 2) + groupNum, reg, data);
                break;
            }
        } else {
            // write register normally
            writeRegister((12 * bank) + groupNum, reg, data);
        }
    }

    private void writePcm(int address, int data) {
        int slotnum = pcmTab[address & 0xf];
        Slot slot;
        if (slotnum == -1) {
            //Debug.printf("ymf271_write_pcm invalid slot %02X %02X", address, data);
            return;
        }
        slot = this.slots[slotnum];

        switch ((address >> 4) & 0xf) {
        case 0x0:
            slot.startAddr &= 0xffff_ff00; // ~0xff;
            slot.startAddr |= data;
            break;

        case 0x1:
            slot.startAddr &= 0xffff_00ff; // ~0xff00;
            slot.startAddr |= data << 8;
            break;

        case 0x2:
            slot.startAddr &= 0xff00_ffff; // ~0xff0000;
            slot.startAddr |= (data & 0x7f) << 16;
            slot.altLoop = (data & 0x80) != 0 ? 1 : 0;
            //if (slot.altloop)
            // System.err.println("YmF271 A/L, contact MAMEdev");
            break;

        case 0x3:
            slot.endAddr &= 0xffff_ff00; // ~0xff;
            slot.endAddr |= data;
            break;

        case 0x4:
            slot.endAddr &= 0xffff_00ff; // ~0xff00;
            slot.endAddr |= data << 8;
            break;

        case 0x5:
            slot.endAddr &= 0xff00_ffff; // ~0xff0000;
            slot.endAddr |= (data & 0x7f) << 16;
            break;

        case 0x6:
            slot.loopAddr &= 0xffff_ff00; // ~0xff;
            slot.loopAddr |= data;
            break;

        case 0x7:
            slot.loopAddr &= 0xffff_00ff; // ~0xff00;
            slot.loopAddr |= data << 8;
            break;

        case 0x8:
            slot.loopAddr &= 0xff00_ffff; // ~0xff0000;
            slot.loopAddr |= (data & 0x7f) << 16;
            break;

        case 0x9:
            slot.fs = data & 0x3;
            slot.bits = (data & 0x4) != 0 ? 12 : 8;
            slot.srcNote = (data >> 3) & 0x3;
            slot.srcb = (data >> 5) & 0x7;
            break;

        default:
            break;
        }
    }

    private int readMemory(int offset) {
        offset &= 0x7f_ffff;
        if (offset < this.memSize)
            return this.memBase[offset] & 0xff;
        else
            return 0;
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings("SF_SWITCH_NO_DEFAULT")
    private void writeTimer(int address, int data) {
        if ((address & 0xf0) == 0) {
            int groupNum = fmTab[address & 0xf];
            if (groupNum == -1) {
                //Debug.printf("ymf271_write_timer invalid group %2x %2x", address, data);
                return;
            }
            Group group = this.groups[groupNum];

            group.sync = data & 0x3;
            group.pfm = data >> 7;
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
//                if (~this.enable & data & 1) {
//                    attotime period = attotime::from_hz (this.clock) * (384 * 4 * (256 - this.timerA));
//                    this.timA.adjust((data & 1) ? period : attotime::never, 0);
//                }

                // timer B load
//                if (~this.enable & data & 2) {
//                    attotime period = attotime::from_hz (this.clock) * (384 * 16 * (256 - this.timerB));
//                    this.timB.adjust((data & 2) ? period : attotime::never, 0);
//                }

                // timer A reset
                if ((data & 0x10) != 0) {
                    this.irqState &= 0xffff_fffe; // ~1;
                    this.status &= 0xfe; // ~1;

//                    if (!this.irq_handler.isnull() && ~this.irqstate & 2)
//                        this.irq_handler(0);
                }

                // timer B reset
                if ((data & 0x20) != 0) {
                    this.irqState &= 0xffff_fffd; // ~2;
                    this.status &= 0xfd; // ~2;

//                    if (!this.irq_handler.isnull() && ~this.irqstate & 1)
//                        this.irq_handler(0);
                }

                this.enable = data;
                break;

            case 0x14:
                this.extAddress &= 0xffff_ff00; // ~0xff;
                this.extAddress |= data;
                break;
            case 0x15:
                this.extAddress &= 0xffff_00ff; // ~0xff00;
                this.extAddress |= data << 8;
                break;
            case 0x16:
                this.extAddress &= 0xff00_ffff; // ~0xff0000;
                this.extAddress |= (data & 0x7f) << 16;
                this.extRw = (data & 0x80) != 0 ? 1 : 0;
                break;
            case 0x17:
                this.extAddress = (this.extAddress + 1) & 0x7f_ffff;
//                if (!this.ext_rw && !this.ext_write_handler.isnull())
//                    this.ext_write_handler(this.ext_address, data);
                break;
            }
        }
    }

    public void write(int offset, int data) {
        this.regsMain[offset & 0xf] = (byte) data;

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
            writeFm(0, this.regsMain[0x0] & 0xff, data);
            break;

        case 0x3:
            writeFm(1, this.regsMain[0x2] & 0xff, data);
            break;

        case 0x5:
            writeFm(2, this.regsMain[0x4] & 0xff, data);
            break;

        case 0x7:
            writeFm(3, this.regsMain[0x6] & 0xff, data);
            break;

        case 0x9:
            writePcm(this.regsMain[0x8] & 0xff, data);
            break;

        case 0xd:
            writeTimer(this.regsMain[0xc] & 0xff, data);
            break;

        default:
            break;
        }
    }

    public int read(int offset) {
        switch (offset & 0xf) {
        case 0x0:
            return this.status;

        case 0x1:
            // statusreg 2
            return 0;

        case 0x2: {
            int ret;
            if (this.extRw == 0)
                return 0xff;

            ret = this.extReadLatch;
            this.extAddress = (this.extAddress + 1) & 0x7f_ffff;
            this.extReadLatch = readMemory(this.extAddress);
            return ret;
        }

        default:
            break;
        }

        return 0xff;
    }

    static {
        for (int i = 0; i < 8; i++)
            lutWaves[i] = new short[SIN_LEN];

        for (int i = 0; i < 4 * 8; i++)
            lutPlfo[i >> 3][i & 7] = new double[LFO_LENGTH];

        for (int i = 0; i < 4; i++)
            lutALfo[i] = new int[LFO_LENGTH];

        for (int i = 0; i < SIN_LEN; i++) {
            double m = Math.sin(((i * 2) + 1) * Math.PI / SIN_LEN);
            double m2 = Math.sin(((i * 4) + 1) * Math.PI / SIN_LEN);

            // Waveform 0: sin(wt)    (0 <= wt <= 2PI)
            lutWaves[0][i] = (short) (m * MAXOUT);

            // Waveform 1: sin?(wt)   (0 <= wt <= PI)     -sin?(wt)  (PI <= wt <= 2PI)
            lutWaves[1][i] = (i < (SIN_LEN / 2)) ? (short) ((m * m) * MAXOUT) : (short) ((m * m) * MINOUT);

            // Waveform 2: sin(wt)    (0 <= wt <= PI)     -sin(wt)   (PI <= wt <= 2PI)
            lutWaves[2][i] = (i < (SIN_LEN / 2)) ? (short) (m * MAXOUT) : (short) (-m * MAXOUT);

            // Waveform 3: sin(wt)    (0 <= wt <= PI)     0
            lutWaves[3][i] = (i < (SIN_LEN / 2)) ? (short) (m * MAXOUT) : (short) 0;

            // Waveform 4: sin(2wt)   (0 <= wt <= PI)     0
            lutWaves[4][i] = (i < (SIN_LEN / 2)) ? (short) (m2 * MAXOUT) : (short) 0;

            // Waveform 5: |sin(2wt)| (0 <= wt <= PI)     0
            lutWaves[5][i] = (i < (SIN_LEN / 2)) ? (short) (Math.abs(m2) * MAXOUT) : (short) 0;

            // Waveform 6:     1      (0 <= wt <= 2PI)
            lutWaves[6][i] = (short) (1 * MAXOUT);

            lutWaves[7][i] = 0;
        }

        for (int i = 0; i < LFO_LENGTH; i++) {
            int triWave;
            double ftriWave, fsawWave;
            double[] pLfo = new double[4];

            // LFO phase modulation
            pLfo[0] = 0;

            fsawWave = ((i % (LFO_LENGTH / 2)) * PLFO_MAX) / (double) ((LFO_LENGTH / 2) - 1);
            pLfo[1] = (i < (LFO_LENGTH / 2)) ? fsawWave : fsawWave - PLFO_MAX;

            pLfo[2] = (i < (LFO_LENGTH / 2)) ? PLFO_MAX : PLFO_MIN;

            ftriWave = ((i % (LFO_LENGTH / 4)) * PLFO_MAX) / (double) (LFO_LENGTH / 4);
            switch (i / (LFO_LENGTH / 4)) {
            case 0:
                pLfo[3] = ftriWave;
                break;
            case 1:
                pLfo[3] = PLFO_MAX - ftriWave;
                break;
            case 2:
                pLfo[3] = 0 - ftriWave;
                break;
            case 3:
                pLfo[3] = 0 - (PLFO_MAX - ftriWave);
                break;
            default:
                pLfo[3] = 0; // assert(0);
                break;
            }

            for (int j = 0; j < 4; j++) {
                lutPlfo[j][0][i] = Math.pow(2.0, 0.0);
                lutPlfo[j][1][i] = Math.pow(2.0, (3.378 * pLfo[j]) / 1200.0);
                lutPlfo[j][2][i] = Math.pow(2.0, (5.0646 * pLfo[j]) / 1200.0);
                lutPlfo[j][3][i] = Math.pow(2.0, (6.7495 * pLfo[j]) / 1200.0);
                lutPlfo[j][4][i] = Math.pow(2.0, (10.1143 * pLfo[j]) / 1200.0);
                lutPlfo[j][5][i] = Math.pow(2.0, (20.1699 * pLfo[j]) / 1200.0);
                lutPlfo[j][6][i] = Math.pow(2.0, (40.1076 * pLfo[j]) / 1200.0);
                lutPlfo[j][7][i] = Math.pow(2.0, (79.307 * pLfo[j]) / 1200.0);
            }

            // LFO amplitude modulation
            lutALfo[0][i] = 0;

            lutALfo[1][i] = ALFO_MAX - ((i * ALFO_MAX) / LFO_LENGTH);

            lutALfo[2][i] = (i < (LFO_LENGTH / 2)) ? ALFO_MAX : ALFO_MIN;

            triWave = ((i % (LFO_LENGTH / 2)) * ALFO_MAX) / (LFO_LENGTH / 2);
            lutALfo[3][i] = (i < (LFO_LENGTH / 2)) ? ALFO_MAX - triWave : triWave;
        }

        for (int i = 0; i < 256; i++) {
            lutEnvVolume[i] = (int) (65536.0 / Math.pow(10.0, ((double) i / (256.0 / 96.0)) / 20.0));
        }

        for (int i = 0; i < 16; i++) {
            lutAttenuation[i] = (int) (65536.0 / Math.pow(10.0, channelAttenuationTable[i] / 20.0));
        }
        for (int i = 0; i < 128; i++) {
            double db = 0.75 * (double) i;
            lutTotalLevel[i] = (int) (65536.0 / Math.pow(10.0, db / 20.0));
        }
    }

    private void initTables() {
        // timing may use a non-standard XTAL
        double clockCorrection = (double) (STD_CLOCK) / (double) (this.clock);
        for (int i = 0; i < 256; i++) {
            this.lutLfo[i] = LFO_frequency_table[i] * clockCorrection;
        }

        for (int i = 0; i < 64; i++) {
            // attack/release rate in number of samples
            this.lutAr[i] = (ARTime[i] * clockCorrection * 44100.0) / 1000.0;
        }
        for (int i = 0; i < 64; i++) {
            // decay rate in number of samples
            this.lutDc[i] = (DCTime[i] * clockCorrection * 44100.0) / 1000.0;
        }
    }

    public int start(int clock) {
        this.clock = clock;

        //intf = (device.static_config != NULL) ? (final ymf271_interface *)device.static_config : &defintrf;

        this.memSize = 0x00;
        this.memBase = null;

        initTables();
        //init_state(chips);
        //this.stream = stream_create(device, 0, 2, device.clock/384, ymf271_update);

        //this.mix_buffer = auto_alloc_array(machine, int, 44100*2);
        this.mixBuffer = new int[44100 * 2]; // (int*)malloc(44100 * 2 * sizeof(int));

        for (int i = 0; i < 12; i++)
            this.groups[i].muted = 0x00;

        return clock / 384;
    }

    public void stop() {
        this.memBase = null;

        for (int i = 0; i < 8; i++) {
            lutWaves[i] = null;
        }
        for (int i = 0; i < 4 * 8; i++) {
            lutPlfo[i >> 3][i & 7] = null;
        }

        for (int i = 0; i < 4; i++) {
            lutALfo[i] = null;
        }

        this.mixBuffer = null;
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

        // if (!this.irq_handler.isnull())
        //  this.irq_handler(0);
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
        if (this.memSize != romSize) {
            this.memBase = new byte[romSize];
            this.memSize = romSize;
            Arrays.fill(this.memBase, 0, romSize, (byte) 0xff);
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        System.arraycopy(romData, 0, this.memBase, dataStart, dataLength);
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        if (this.memSize != romSize) {
            this.memBase = new byte[romSize];
            this.memSize = romSize;
            Arrays.fill(this.memBase, 0, romSize, (byte) 0xff);
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        System.arraycopy(romData, srcStartAddress, this.memBase, dataStart, dataLength);
    }

    public void setMuteMask(int muteMask) {
        for (byte curChn = 0; curChn < 12; curChn++)
            this.groups[curChn].muted = (muteMask >> curChn) & 0x01;
    }

    public Slot getSlot(int slot) {
        return slots[slot];
    }

    public int getSync(int g) {
        return groups[g].sync;
    }
}
