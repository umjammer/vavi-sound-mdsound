package mdsound;


public class Saa1099 extends Instrument.BaseInstrument {

    @Override
    public String getName() {
        return "SAA1099";
    }

    @Override
    public String getShortName() {
        return "SAA";
    }

    public Saa1099() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public void reset(byte chipID) {
        device_reset_saa1099(chipID);
    }

    @Override
    public int start(byte chipID, int clock) {
        return device_start_saa1099(chipID, 8000000);
    }

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        return device_start_saa1099(chipID, clockValue);
    }

    @Override
    public void stop(byte chipID) {
        device_stop_saa1099(chipID);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        saa1099_update(chipID, outputs, samples);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        saa1099_control_w(chipID, 0, (byte) adr);
        saa1099_data_w(chipID, 0, (byte) data);
        return 0;
    }

    public void SAA1099_SetMute(byte chipID, int v) {
        saa1099_set_mute_mask(chipID, v);
    }

    /**
     Philips SAA1099 Sound driver

     By Juergen Buchmueller and Manuel Abadia

     SAA1099 register layout:
     ========================

     offs | 7654 3210 | description
     -----+-----------+---------------------------
     0x00 | ---- xxxx | Amplitude channel 0 (left)
     0x00 | xxxx ---- | Amplitude channel 0 (right)
     0x01 | ---- xxxx | Amplitude channel 1 (left)
     0x01 | xxxx ---- | Amplitude channel 1 (right)
     0x02 | ---- xxxx | Amplitude channel 2 (left)
     0x02 | xxxx ---- | Amplitude channel 2 (right)
     0x03 | ---- xxxx | Amplitude channel 3 (left)
     0x03 | xxxx ---- | Amplitude channel 3 (right)
     0x04 | ---- xxxx | Amplitude channel 4 (left)
     0x04 | xxxx ---- | Amplitude channel 4 (right)
     0x05 | ---- xxxx | Amplitude channel 5 (left)
     0x05 | xxxx ---- | Amplitude channel 5 (right)
     |           |
     0x08 | xxxx xxxx | Frequency channel 0
     0x09 | xxxx xxxx | Frequency channel 1
     0x0a | xxxx xxxx | Frequency channel 2
     0x0b | xxxx xxxx | Frequency channel 3
     0x0c | xxxx xxxx | Frequency channel 4
     0x0d | xxxx xxxx | Frequency channel 5
     |           |
     0x10 | ---- -xxx | Channel 0 octave select
     0x10 | -xxx ---- | Channel 1 octave select
     0x11 | ---- -xxx | Channel 2 octave select
     0x11 | -xxx ---- | Channel 3 octave select
     0x12 | ---- -xxx | Channel 4 octave select
     0x12 | -xxx ---- | Channel 5 octave select
     |           |
     0x14 | ---- ---x | Channel 0 frequency enable (0 = off, 1 = on)
     0x14 | ---- --x- | Channel 1 frequency enable (0 = off, 1 = on)
     0x14 | ---- -x-- | Channel 2 frequency enable (0 = off, 1 = on)
     0x14 | ---- x--- | Channel 3 frequency enable (0 = off, 1 = on)
     0x14 | ---x ---- | Channel 4 frequency enable (0 = off, 1 = on)
     0x14 | --x- ---- | Channel 5 frequency enable (0 = off, 1 = on)
     |           |
     0x15 | ---- ---x | Channel 0 noise enable (0 = off, 1 = on)
     0x15 | ---- --x- | Channel 1 noise enable (0 = off, 1 = on)
     0x15 | ---- -x-- | Channel 2 noise enable (0 = off, 1 = on)
     0x15 | ---- x--- | Channel 3 noise enable (0 = off, 1 = on)
     0x15 | ---x ---- | Channel 4 noise enable (0 = off, 1 = on)
     0x15 | --x- ---- | Channel 5 noise enable (0 = off, 1 = on)
     |           |
     0x16 | ---- --xx | Noise generator parameters 0
     0x16 | --xx ---- | Noise generator parameters 1
     |           |
     0x18 | --xx xxxx | Envelope generator 0 parameters
     0x18 | x--- ---- | Envelope generator 0 control enable (0 = off, 1 = on)
     0x19 | --xx xxxx | Envelope generator 1 parameters
     0x19 | x--- ---- | Envelope generator 1 control enable (0 = off, 1 = on)
     |           |
     0x1c | ---- ---x | All channels enable (0 = off, 1 = on)
     0x1c | ---- --x- | Synch & Reset generators
     */
    private static class Saa1099State {

        private static final int LEFT = 0x00;
        private static final int RIGHT = 0x01;

        /* this structure defines a channel */
        private static class Channel {
            /* frequency (0x00..0xff) */
            public int frequency;
            /* frequency enable */
            public int freqEnable;
            /* noise enable */
            public int noiseEnable;
            /* octave (0x00..0x07) */
            public int octave;
            /* amplitude (0x00..0x0f) */
            public int[] amplitude = new int[2];
            /* envelope (0x00..0x0f or 0x10 == off) */
            public int[] envelope = new int[2];

            /* vars to simulate the square wave */
            public double counter;
            public double freq;
            public int level;
            public byte muted;

            private void reset() {
                this.frequency = 0;
                this.octave = 0;
                this.amplitude[0] = 0;
                this.amplitude[1] = 0;
                this.envelope[0] = 0;
                this.envelope[1] = 0;
                this.freqEnable = 0;
                this.noiseEnable = 0;

                this.counter = 0;
                this.freq = 0;
                this.level = 0;
            }
        }

        /* this structure defines a noise channel */
        private static class Noise {
            /* vars to simulate the noise generator output */
            public double counter;
            public double freq;
            /* noise polynomal shifter */
            public int level;

            public void reset() {
                this.counter = 0;
                this.freq = 0;
                this.level = 0;
            }
        }

        private static final int[] amplitudeLookup = new int[] {
                0 * 32767 / 16, 1 * 32767 / 16, 2 * 32767 / 16, 3 * 32767 / 16,
                4 * 32767 / 16, 5 * 32767 / 16, 6 * 32767 / 16, 7 * 32767 / 16,
                8 * 32767 / 16, 9 * 32767 / 16, 10 * 32767 / 16, 11 * 32767 / 16,
                12 * 32767 / 16, 13 * 32767 / 16, 14 * 32767 / 16, 15 * 32767 / 16
        };

        private static final byte[][] envelope = new byte[][] {
                // zero amplitude
                new byte[] {
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
                },
                // maximum amplitude
                new byte[] {
                        15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15,
                        15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15,
                        15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15,
                        15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15,
                },
                /* single decay */
                new byte[] {
                        15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
                },
                /* repetitive decay */
                new byte[] {
                        15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0,
                        15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0,
                        15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0,
                        15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0
                },
                // single triangular
                new byte[] {
                        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                        15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
                },
                // repetitive triangular
                new byte[] {
                        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                        15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0,
                        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                        15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0
                },
                // single attack
                new byte[] {
                        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
                },
                // repetitive attack
                new byte[] {
                        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
                }
        };

        /** noise generators parameters */
        public int[] noiseParams = new int[2];
        /** envelope generators enable */
        public int[] envEnable = new int[2];
        /** envelope reversed for right channel */
        public int[] envReverseRight = new int[2];
        /** envelope generators mode */
        public int[] envMode = new int[2];
        /** non zero = 3 bits resolution */
        public int[] envBits = new int[2];
        /** envelope clock mode (non-zero external) */
        public int[] envClock = new int[2];
        /** current envelope step */
        public int[] envStep = new int[2];
        /** all channels enable */
        public int allChEnable;
        /** sync all channels */
        public int syncState;
        /** selected register */
        public int selectedReg;
        public Channel[] channels = new Channel[] {new Channel(), new Channel(), new Channel(), new Channel(), new Channel(), new Channel()}; /* channels */
        /** noise generators */
        public Noise[] noise = new Noise[] {new Noise(), new Noise()};
        public double sampleRate;
        public int masterClock;

        private void envelope(int ch) {
            if (this.envEnable[ch] != 0) {
                int step, mode, mask;
                mode = this.envMode[ch];
                // step from 0..63 and then loop in steps 32..63
                step = this.envStep[ch] =
                        ((this.envStep[ch] + 1) & 0x3f) | (this.envStep[ch] & 0x20);

                mask = 15;
                if (this.envBits[ch] != 0)
                    mask &= ~1; // 3 bit resolution, mask LSB

                this.channels[ch * 3 + 0].envelope[LEFT] =
                        this.channels[ch * 3 + 1].envelope[LEFT] =
                                this.channels[ch * 3 + 2].envelope[LEFT] = envelope[mode][step] & mask;
                if ((this.envReverseRight[ch] & 0x01) != 0) {
                    this.channels[ch * 3 + 0].envelope[RIGHT] =
                            this.channels[ch * 3 + 1].envelope[RIGHT] =
                                    this.channels[ch * 3 + 2].envelope[RIGHT] = (15 - envelope[mode][step]) & mask;
                } else {
                    this.channels[ch * 3 + 0].envelope[RIGHT] =
                            this.channels[ch * 3 + 1].envelope[RIGHT] =
                                    this.channels[ch * 3 + 2].envelope[RIGHT] = envelope[mode][step] & mask;
                }
            } else {
                // envelope mode off, set all envelope factors to 16
                this.channels[ch * 3 + 0].envelope[LEFT] =
                        this.channels[ch * 3 + 1].envelope[LEFT] =
                                this.channels[ch * 3 + 2].envelope[LEFT] =
                                        this.channels[ch * 3 + 0].envelope[RIGHT] =
                                                this.channels[ch * 3 + 1].envelope[RIGHT] =
                                                        this.channels[ch * 3 + 2].envelope[RIGHT] = 16;
            }
        }

        private void update(int[][] outputs, int samples) {
            // if the channels are disabled we're done
            if (this.allChEnable == 0) {
                // init output data
                for (int i = 0; i < samples; i++) {
                    outputs[LEFT][i] = 0;
                    outputs[RIGHT][i] = 0;
                }
                return;
            }

            for (int ch = 0; ch < 2; ch++) {
                switch (this.noiseParams[ch]) {
                case 0:
                    this.noise[ch].freq = this.masterClock / 256.0 * 2;
                    break;
                case 1:
                    this.noise[ch].freq = this.masterClock / 512.0 * 2;
                    break;
                case 2:
                    this.noise[ch].freq = this.masterClock / 1024.0 * 2;
                    break;
                case 3:
                    this.noise[ch].freq = this.channels[ch * 3].freq;
                    break;
                }
            }

            // clock fix thanks to http://www.vogons.org/viewtopic.php?p=344227#p344227
            int clk2div512 = (this.masterClock + 128) / 256;

            // fill all data needed
            for (int j = 0; j < samples; j++) {
                int output_l = 0, output_r = 0;

                // for each channel
                for (int ch = 0; ch < 6; ch++) {
                    if (this.channels[ch].freq == 0.0)
                        this.channels[ch].freq = (double) (clk2div512 << this.channels[ch].octave) /
                                (511.0 - (double) this.channels[ch].frequency);

                    // check the actual position in the square wave
                    this.channels[ch].counter -= this.channels[ch].freq;
                    while (this.channels[ch].counter < 0) {
                        // calculate new frequency now after the half wave is updated
                        this.channels[ch].freq = (double) (clk2div512 << this.channels[ch].octave) /
                                (511.0 - (double) this.channels[ch].frequency);

                        this.channels[ch].counter += this.sampleRate;
                        this.channels[ch].level ^= 1;

                        // eventually clock the envelope counters
                        if (ch == 1 && this.envClock[0] == 0)
                            this.envelope(0);
                        if (ch == 4 && this.envClock[1] == 0)
                            this.envelope(1);
                    }

                    if (this.channels[ch].muted != 0)
                        continue; // placed here to ensure that envelopes are updated

// #if false
//                    // if the noise is enabled
//                    if (this.channels[ch].noiseEnable != 0) {
//                        // if the noise level is high (noise 0: chan 0-2, noise 1: chan 3-5)
//                        if ((this.noise[ch / 3].level & 1) != 0) {
//                            // subtract to avoid overflows, also use only half amplitude
//                            output_l -= this.channels[ch].amplitude[LEFT] * this.channels[ch].envelope[LEFT] / 16 / 2;
//                            output_r -= this.channels[ch].amplitude[RIGHT] * this.channels[ch].envelope[RIGHT] / 16 / 2;
//                        }
//                    }
//
//                    // if the square wave is enabled
//                    if (this.channels[ch].freqEnable != 0) {
//                        // if the channel level is high
//                        if ((this.channels[ch].level & 1) != 0) {
//                            output_l += this.channels[ch].amplitude[LEFT] * this.channels[ch].envelope[LEFT] / 16;
//                            output_r += this.channels[ch].amplitude[RIGHT] * this.channels[ch].envelope[RIGHT] / 16;
//                        }
//                    }
// #else
                    // Now with bipolar output. -Valley Bell
                    if (this.channels[ch].noiseEnable != 0) {
                        if ((this.noise[ch / 3].level & 1) != 0) {
                            output_l += this.channels[ch].amplitude[LEFT] * this.channels[ch].envelope[LEFT] / 32 / 2;
                            output_r += this.channels[ch].amplitude[RIGHT] * this.channels[ch].envelope[RIGHT] / 32 / 2;
                        } else {
                            output_l -= this.channels[ch].amplitude[LEFT] * this.channels[ch].envelope[LEFT] / 32 / 2;
                            output_r -= this.channels[ch].amplitude[RIGHT] * this.channels[ch].envelope[RIGHT] / 32 / 2;
                        }
                    }

                    if (this.channels[ch].freqEnable != 0) {
                        if ((this.channels[ch].level & 1) != 0) {
                            output_l += this.channels[ch].amplitude[LEFT] * this.channels[ch].envelope[LEFT] / 32;
                            output_r += this.channels[ch].amplitude[RIGHT] * this.channels[ch].envelope[RIGHT] / 32;
                        } else {
                            output_l -= this.channels[ch].amplitude[LEFT] * this.channels[ch].envelope[LEFT] / 32;
                            output_r -= this.channels[ch].amplitude[RIGHT] * this.channels[ch].envelope[RIGHT] / 32;
                        }
                    }
// #endif
                }

                for (int ch = 0; ch < 2; ch++) {
                    // check the actual position in noise generator
                    this.noise[ch].counter -= this.noise[ch].freq;
                    while (this.noise[ch].counter < 0) {
                        this.noise[ch].counter += this.sampleRate;
                        if (((this.noise[ch].level & 0x4000) == 0) == ((this.noise[ch].level & 0x0040) == 0))
                            this.noise[ch].level = (this.noise[ch].level << 1) | 1;
                        else
                            this.noise[ch].level <<= 1;
                    }
                }
                // write Sound data to the buffer
                outputs[LEFT][j] = output_l / 6;
                outputs[RIGHT][j] = output_r / 6;
            }
        }

        private int start(int clock) {
            // copy Global parameters
            this.masterClock = clock;
            this.sampleRate = clock / 256.0;

            for (byte curChn = 0; curChn < 6; curChn++)
                this.channels[curChn].muted = 0x00;

            return (int) (this.sampleRate + 0.5);
        }

        private void reset() {
            for (byte curChn = 0; curChn < 6; curChn++) {
                Channel sachn = this.channels[curChn];
                sachn.reset();
            }
            for (byte curChn = 0; curChn < 2; curChn++) {
                this.noise[curChn].reset();

                this.noiseParams[1] = 0x00;
                this.envReverseRight[curChn] = 0x00;
                this.envMode[curChn] = 0x00;
                this.envBits[curChn] = 0x00;
                this.envClock[curChn] = 0x00;
                this.envEnable[curChn] = 0x00;
                this.envStep[curChn] = 0;
            }

            this.allChEnable = 0x00;
            this.syncState = 0x00;
        }

        private void control_w(int offset, byte data) {
            if ((data & 0xff) > 0x1c) {
                // Error!
                //throw new Exception("SAA1099: Unknown register selected\n");
            }

            this.selectedReg = data & 0x1f;
            if (this.selectedReg == 0x18 || this.selectedReg == 0x19) {
                // clock the envelope channels
                if (this.envClock[0] != 0)
                    this.envelope(0);
                if (this.envClock[1] != 0)
                    this.envelope(1);
            }
        }

        private void data_w(int offset, byte data) {
            int reg = this.selectedReg;
            int ch;

            switch (reg) {
            // channel i amplitude
            case 0x00:
            case 0x01:
            case 0x02:
            case 0x03:
            case 0x04:
            case 0x05:
                ch = reg & 7;
                this.channels[ch].amplitude[LEFT] = amplitudeLookup[data & 0x0f];
                this.channels[ch].amplitude[RIGHT] = amplitudeLookup[(data >> 4) & 0x0f];
                break;
            // channel i frequency
            case 0x08:
            case 0x09:
            case 0x0a:
            case 0x0b:
            case 0x0c:
            case 0x0d:
                ch = reg & 7;
                this.channels[ch].frequency = data & 0xff;
                break;
            // channel i octave
            case 0x10:
            case 0x11:
            case 0x12:
                ch = (reg - 0x10) << 1;
                this.channels[ch + 0].octave = data & 0x07;
                this.channels[ch + 1].octave = (data >> 4) & 0x07;
                break;
            // channel i frequency enable
            case 0x14:
                this.channels[0].freqEnable = data & 0x01;
                this.channels[1].freqEnable = data & 0x02;
                this.channels[2].freqEnable = data & 0x04;
                this.channels[3].freqEnable = data & 0x08;
                this.channels[4].freqEnable = data & 0x10;
                this.channels[5].freqEnable = data & 0x20;
                break;
            // channel i noise enable
            case 0x15:
                this.channels[0].noiseEnable = data & 0x01;
                this.channels[1].noiseEnable = data & 0x02;
                this.channels[2].noiseEnable = data & 0x04;
                this.channels[3].noiseEnable = data & 0x08;
                this.channels[4].noiseEnable = data & 0x10;
                this.channels[5].noiseEnable = data & 0x20;
                break;
            // noise generators parameters
            case 0x16:
                this.noiseParams[0] = data & 0x03;
                this.noiseParams[1] = (data >> 4) & 0x03;
                break;
            // envelope generators parameters
            case 0x18:
            case 0x19:
                ch = reg - 0x18;
                this.envReverseRight[ch] = data & 0x01;
                this.envMode[ch] = (data >> 1) & 0x07;
                this.envBits[ch] = data & 0x10;
                this.envClock[ch] = data & 0x20;
                this.envEnable[ch] = data & 0x80;
                // reset the envelope
                this.envStep[ch] = 0;
                break;
            // channels enable & reset generators
            case 0x1c:
                this.allChEnable = data & 0x01;
                this.syncState = data & 0x02;
                if ((data & 0x02) != 0) {
                    int i;

                    // Synch & Reset generators
                    //throw new Exception("SAA1099: -reg 0x1c- Chip reset\n");
                    for (i = 0; i < 6; i++) {
                        this.channels[i].level = 0;
                        this.channels[i].counter = 0.0;
                    }
                }
                break;
            default: // Error!
                //throw new Exception(String.format("SAA1099: Unknown operation (reg:{0:x02}, data:{1:x02})\n", reg, data));
                break;
            }
        }

        private void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < 6; curChn++)
                this.channels[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
        }
    }

    private static final int MAX_CHIPS = 0x02;
    private Saa1099State[] saa1099Data = new Saa1099State[] {new Saa1099State(), new Saa1099State()};

    private void saa1099_update(byte chipID, int[][] outputs, int samples) {
        Saa1099State saa = saa1099Data[chipID];

        saa.update(outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    private int device_start_saa1099(byte chipID, int clock) {
        if (chipID >= MAX_CHIPS)
            return 0;

        Saa1099State saa = saa1099Data[chipID];
        return saa.start(clock);
    }

    private void device_stop_saa1099(byte chipID) {
        Saa1099State saa = saa1099Data[chipID];
    }

    private void device_reset_saa1099(byte chipID) {
        Saa1099State saa = saa1099Data[chipID];
        saa.reset();
    }

    private void saa1099_control_w(byte chipID, int offset, byte data) {
        Saa1099State saa = saa1099Data[chipID];
        saa.control_w(offset, data);
    }

    private void saa1099_data_w(byte chipID, int offset, byte data) {
        Saa1099State saa = saa1099Data[chipID];
        saa.data_w(offset, data);
    }

    private void saa1099_set_mute_mask(byte chipID, int muteMask) {
        Saa1099State saa = saa1099Data[chipID];
        saa.setMuteMask(muteMask);
    }

    /**
     * Generic get_info
     */
        /*DEVICE_GET_INFO( Saa1099 )
        {
         switch (state)
         {
          // --- the following bits of info are returned as 64-bit signed integers ---
          case DEVINFO_INT_TOKEN_BYTES:     info.i = sizeof(saa1099_state);    break;

          // --- the following bits of info are returned as pointers to data or functions ---
          case DEVINFO_FCT_START:       info.start = DEVICE_START_NAME( Saa1099 );  break;
          case DEVINFO_FCT_STOP:       // Nothing //         break;
          case DEVINFO_FCT_RESET:       // Nothing //         break;

          // --- the following bits of info are returned as NULL-terminated strings ---
          case DEVINFO_STR_NAME:       strcpy(info.s, "SAA1099");      break;
          case DEVINFO_STR_FAMILY:     strcpy(info.s, "Philips");      break;
          case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
          case DEVINFO_STR_SOURCE_FILE:      strcpy(info.s, __FILE__);      break;
          case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
         }
        }

        DEFINE_LEGACY_SOUND_DEVICE(SAA1099, Saa1099);*/
}
