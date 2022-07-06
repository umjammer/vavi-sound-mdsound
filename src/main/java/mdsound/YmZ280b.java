/**
 * Yamaha YMZ280B driver
 * <p>
 * by Aaron Giles
 */

package mdsound;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.logging.Level;

import vavi.util.Debug;


public class YmZ280b extends Instrument.BaseInstrument {

    @Override
    public void reset(byte chipId) {
        device_reset_ymz280b(chipId);
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipId, int clock) {
        return device_start_ymz280b(chipId, 16934400);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        return device_start_ymz280b(chipId, clockValue);
    }

    @Override
    public void stop(byte chipId) {
        device_stop_ymz280b(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        ymz280b_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private int write(byte chipId, byte offset, byte data) {
        ymz280b_w(chipId, 0x00, offset);
        ymz280b_w(chipId, 0x01, data);
        return 0;
    }

    /**
     * Yamaha YMZ280B driver
     * by Aaron Giles
     * <p>
     * YMZ280B 8-Channel PCMD8 PCM/ADPCM Decoder
     * <p>
     * Features as listed in LSI-4MZ280B3 data sheet:
     * Voice data stored in external memory can be played back simultaneously for up to eight voices
     * Voice data format can be selected from 4-bit ADPCM, 8-bit PCM and 16-bit PCM
     * Controller of Voice data external memory
     * Up to 16M bytes of ROM or SRAM (x 8 bits, access time 150ms max) can be connected
     * Continuous access is possible
     * Loop playback between selective addresses is possible
     * Voice data playback frequency control
     * 4-bit ADPCM ................ 0.172 to 44.1kHz in 256 steps
     * 8-bit PCM, 16-bit PCM ...... 0.172 to 88.2kHz in 512 steps
     * 256 steps total level and 16 steps panpot can be set
     * Voice signal is output in stereo 16-bit 2's complement MSB-first format
     * <p>
     * TODO:
     * - Is memory handling 100% correct? At the moment, Konami firebeat.c is the only
     * hardware currently emulated that uses external handlers.
     * It also happens to be the only one using 16-bit PCM.
     * <p>
     * Some other drivers (eg. bishi.c, bfm_sc4/5.c) also use ROM readback.
     */
    public static class YmZ280bState {

        public interface Callback extends Consumer<Integer> {
        }

        public static class Interface {
            public Callback irqCallback; // irq Callback */
        }

        private void updateIrqStateTimerCommon(Object param, int voiceNum) {
        }

        private static final int MAX_SAMPLE_CHUNK = 0x10000;

        private static final int FRAC_BITS = 14;
        private static final int FRAC_ONE = 1 << FRAC_BITS;
        private static final int FRAC_MASK = FRAC_ONE - 1;

        private static final int INTERNAL_BUFFER_SIZE = 1 << 15;

        /** struct describing a single playing ADPCM Voice */
        static class Voice {

            /** lookup table for the precomputed difference */
            private static final int[] diffLookup = new int[16];

            /*
             * compute the difference tables
             */
            static {
                // loop over all nibbles and compute the difference
                for (int nib = 0; nib < 16; nib++) {
                    int value = (nib & 0x07) * 2 + 1;
                    diffLookup[nib] = (nib & 0x08) != 0 ? -value : value;
                }
            }

            /** step size index shift table */
            private static final int[] indexScale = new int[] {0x0e6, 0x0e6, 0x0e6, 0x0e6, 0x133, 0x199, 0x200, 0x266};

            /*+ 1 if we are actively playing */
            public byte playing;

            /** 1 if the key is on */
            public byte keyon;
            /** 1 if looping is enabled */
            public byte looping;
            /** current playback mode */
            public byte mode;
            /** frequency */
            public int fnum;
            /** output level */
            public byte level;
            /** panning */
            public byte pan;

            /** start address, in nibbles */
            public int start;
            /** stop address, in nibbles */
            public int stop;
            /** loop start address, in nibbles */
            public int loopStart;
            /** loop end address, in nibbles */
            public int loopEnd;
            /** current position, in nibbles */
            public int position;

            /** current ADPCM signal */
            public int signal;
            /** current ADPCM step */
            public int step;

            /** signal at loop start */
            public int loopSignal;
            /** step at loop start */
            public int loopStep;
            /** number of loops so far */
            public int loopCount;

            /** output volume (left) */
            public int outputLeft;
            /** output volume (right) */
            public int outputRight;
            /** step value for frequency conversion */
            public int outputStep;
            /** current fractional position */
            public int outputPos;
            /** last sample output */
            public short lastSample;
            /** current sample target */
            public short currSample;
            /** 1 if the IRQ state is updated by timer */
            public byte irqSchedule;
            /** used for muting */
            public byte muted;

            private void updateVolumes() {
                if (this.pan == 8) {
                    this.outputLeft = this.level;
                    this.outputRight = this.level;
                } else if (this.pan < 8) {
                    this.outputLeft = this.level;

                    // pan 1 is hard-left, what's pan 0? for now assume same as pan 1
                    this.outputRight = (this.pan == 0) ? 0 : this.level * (this.pan - 1) / 7;
                } else {
                    this.outputLeft = this.level * (15 - this.pan) / 7;
                    this.outputRight = this.level;
                }
            }

            /**
             * general ADPCM decoding routine
             */
            private int generateAdpcm(byte[] base, int size, short[] buffer, int samples) {
                int ptrBuffer = 0;

                // two cases: first cases is non-looping
                if (this.looping == 0) {
                    // loop while we still have samples to generate
                    while (samples != 0) {
                        // compute the new amplitude and update the current step
                        int val = readMemory(base, size, position / 2) >> ((~position & 1) << 2);
                        signal += (step * diffLookup[val & 15]) / 8;

                        /* clamp to the maximum */
                        if (signal > 32767)
                            signal = 32767;
                        else if (signal < -32768)
                            signal = -32768;

                        // adjust the step size and clamp
                        step = (step * indexScale[val & 7]) >> 8;
                        if (step > 0x6000)
                            step = 0x6000;
                        else if (step < 0x7f)
                            step = 0x7f;

                        // output to the buffer, scaling by the volume
                        buffer[ptrBuffer++] = (short) signal;
                        samples--;

                        // next!
                        position++;
                        if (position >= this.stop) {
                            if (samples == 0)
                                samples |= 0x10000;

                            break;
                        }
                    }
                } else { // second case: looping
                    // loop while we still have samples to generate
                    while (samples != 0) {
                        // compute the new amplitude and update the current step
                        int val = readMemory(base, size, position / 2) >> ((~position & 1) << 2);
                        signal += (step * diffLookup[val & 15]) / 8;

                        // clamp to the maximum
                        if (signal > 32767)
                            signal = 32767;
                        else if (signal < -32768)

                            signal = -32768;

                        // adjust the step size and clamp
                        step = (step * indexScale[val & 7]) >> 8;
                        if (step > 0x6000)
                            step = 0x6000;
                        else if (step < 0x7f)
                            step = 0x7f;

                        // output to the buffer, scaling by the volume
                        buffer[ptrBuffer++] = (short) signal;
                        samples--;

                        // next!
                        position++;
                        if (position == this.loopStart && this.loopCount == 0) {
                            this.loopSignal = signal;
                            this.loopStep = step;
                        }
                        if (position >= this.loopEnd) {
                            if (this.keyon != 0) {
                                position = this.loopStart;
                                signal = this.loopSignal;
                                step = this.loopStep;
                                this.loopCount++;
                            }
                        }
                        if (position >= this.stop) {
                            if (samples == 0)
                                samples |= 0x10000;

                            break;
                        }
                    }
                }

                return samples;
            }

            /**
             * general 8-bit PCM decoding routine
             */
            private int generate_pcm8(byte[] base, int size, short[] buffer, int samples) {
                int val;
                int ptrBuffer = 0;

                // two cases: first cases is non-looping
                if (this.looping == 0) {
                    // loop while we still have samples to generate
                    while (samples != 0) {
                        // fetch the current value
                        val = readMemory(base, size, position / 2);

                        // output to the buffer, scaling by the volume
                        buffer[ptrBuffer++] = (short) (val * 256);
                        samples--;

                        // next!
                        position += 2;
                        if (position >= this.stop) {
                            if (samples == 0)
                                samples |= 0x10000;

                            break;
                        }
                    }
                }  else { // second case: looping
                    // loop while we still have samples to generate
                    while (samples != 0) {
                        // fetch the current value
                        val = readMemory(base, size, position / 2);

                        // output to the buffer, scaling by the volume
                        buffer[ptrBuffer++] = (short) (val * 256);
                        samples--;

                        // next!
                        position += 2;
                        if (position >= this.loopEnd) {
                            if (this.keyon != 0)
                                position = this.loopStart;
                        }
                        if (position >= this.stop) {
                            if (samples == 0)
                                samples |= 0x10000;

                            break;
                        }
                    }
                }

                return samples;
            }

            /**
             * general 16-bit PCM decoding routine
             */
            private int generatePcm16(byte[] _base, int size, short[] buffer, int samples) {
                int val;
                int ptrBuffer = 0;

                /* is it even used in any MAME game? */
                //popmessage("YMZ280B 16-bit PCM contact MAMEDEV");

                /* two cases: first cases is non-looping */
                if (this.looping == 0) {
                    /* loop while we still have samples to generate */
                    while (samples != 0) {
                        /* fetch the current value */
                        //val = (short)((base[position / 2 + 1] << 8) + base[position / 2 + 0]);
                        val = (short) ((readMemory(_base, size, position / 2 + 0) << 8) + readMemory(_base, size, position / 2 + 1));
                        // Note: Last MAME updates say it's: ((position / 2 + 1) << 8) + (position / 2 + 0);

                        /* output to the buffer, scaling by the volume */
                        buffer[ptrBuffer++] = (short) val;
                        samples--;

                        /* next! */
                        position += 4;
                        if (position >= this.stop) {
                            if (samples == 0)
                                samples |= 0x10000;

                            break;
                        }
                    }
                } else { // second case: looping
                    // loop while we still have samples to generate
                    while (samples != 0) {
                        // fetch the current value */
                        val = (short) ((readMemory(_base, size, position / 2 + 0) << 8)
                                + readMemory(_base, size, position / 2 + 1));

                        // output to the buffer, scaling by the volume
                        buffer[ptrBuffer++] = (short) val;
                        samples--;

                        // next!
                        position += 4;
                        if (position >= this.loopEnd) {
                            if (this.keyon != 0)
                                position = this.loopStart;
                        }
                        if (position >= this.stop) {
                            if (samples == 0)
                                samples |= 0x10000;

                            break;
                        }
                    }
                }

                return samples;
            }

            void reset() {
                this.currSample = 0;
                this.lastSample = 0;
                this.outputPos = FRAC_ONE;
                this.playing = 0;
            }
        }

        private static byte readMemory(byte[] _base, int size, int offset) {
            offset &= 0xffffff;
            if (offset < size)
                return _base[offset];
            else
                return 0;
        }

        /** pointer to the base of the region */
        public byte[] regionBase;
        public int regionSize;
        /** currently accessible register */
        public byte currentRegister;
        /** current status register */
        public byte statusRegister;
        /** current IRQ state */
        public byte irqState;
        /** current IRQ mask */
        public byte irqMask;
        /** current IRQ enable */
        public byte irqEnable;
        /** key on enable */
        public byte keyonEnable;
        /** external memory enable */
        public byte extMemEnable;
        /** external memory prefetched data */
        public byte extReadlatch;
        public int extMemAddressHi;
        public int extMemAddressMid;
        /** where the CPU can read the ROM */
        public int extMemAddress;
        /** master clock frequency */
        public double masterClock;
        public double rate;
        /** IRQ Callback */
        public Callback irqCallback;
        /** the 8 voices */
        public Voice[] voices = new Voice[] {
                new Voice(), new Voice(), new Voice(), new Voice(),
                new Voice(), new Voice(), new Voice(), new Voice()
        };

        public short[] scratch;

        private void updateIrqState() {
            int irqBits = this.statusRegister & this.irqMask;

            // always off if the enable is off
            if (this.irqEnable == 0)
                irqBits = 0;

            // update the state if changed
            if (irqBits != 0 && this.irqState == 0) {
                this.irqState = 1;
                if (this.irqCallback != null)
                    this.irqCallback.accept(1);
                //else Debug.printf("YMZ280B: IRQ generated, but no Callback specified!");
            } else if (irqBits == 0 && this.irqState != 0) {
                this.irqState = 0;
                if (this.irqCallback != null)
                    this.irqCallback.accept(0);
                //else Debug.printf("YMZ280B: IRQ generated, but no Callback specified!");
            }
        }

        private void update_step(Voice voice) {
            double frequency;
            // compute the frequency
            if (voice.mode == 1)
                frequency = this.masterClock * (double) ((voice.fnum & 0x0ff) + 1) * (1.0 / 256.0);
            else
                frequency = this.masterClock * (double) ((voice.fnum & 0x1ff) + 1) * (1.0 / 256.0);
            voice.outputStep = (int) (frequency * (double) FRAC_ONE / this.rate);
        }

        /**
         * handle a write to the current register
         */
        public void writeToRegister(int data) {

            // lower registers follow a pattern
            if ((this.currentRegister & 0xff) < 0x80) {
                Voice voice = this.voices[(this.currentRegister >> 2) & 7];

                switch (this.currentRegister & 0xe3) {
                case 0x00: // pitch low 8 bits
                    voice.fnum = (voice.fnum & 0x100) | (data & 0xff);

                    this.update_step(voice);
                    break;

                case 0x01: // pitch upper 1 bit, loop, key on, mode
                    voice.fnum = (voice.fnum & 0xff) | ((data & 0x01) << 8);
                    voice.looping = (byte) ((data & 0x10) >> 4);
                    if ((data & 0x60) == 0) data &= 0x7f; // ignore mode setting and set to same state as KON=0
                    else voice.mode = (byte) ((data & 0x60) >> 5);

                    if (voice.keyon == 0 && (data & 0x80) != 0 && this.keyonEnable != 0) {
                        voice.playing = 1;
                        voice.position = voice.start;
                        voice.signal = voice.loopSignal = 0;
                        voice.step = voice.loopStep = 0x7f;
                        voice.loopCount = 0;

                        // if update_irq_state_timer is set, cancel it.
                        voice.irqSchedule = 0;
                    }
                    // new code from MAME 0.143u4
                    else if (voice.keyon != 0 && (data & 0x80) == 0) {
                        voice.playing = 0;

                        // if update_irq_state_timer is set, cancel it.
                        voice.irqSchedule = 0;
                    }
                    voice.keyon = (byte) ((data & 0x80) >> 7);

                    this.update_step(voice);
                    break;

                case 0x02: // total level
                    voice.level = (byte) data;

                    voice.updateVolumes();
                    break;

                case 0x03: // pan
                    voice.pan = (byte) (data & 0x0f);

                    voice.updateVolumes();
                    break;

                case 0x20: // start address high
                    voice.start = (voice.start & (0x00ffff << 1)) | (data << 17);
                    break;

                case 0x21: // loop start address high
                    voice.loopStart = (voice.loopStart & (0x00ffff << 1)) | (data << 17);
                    break;

                case 0x22: // loop end address high
                    voice.loopEnd = (voice.loopEnd & (0x00ffff << 1)) | (data << 17);
                    break;

                case 0x23: // stop address high
                    voice.stop = (voice.stop & (0x00ffff << 1)) | (data << 17);
                    break;

                case 0x40: // start address middle
                    voice.start = (voice.start & (0xff00ff << 1)) | (data << 9);
                    break;

                case 0x41: // loop start address middle
                    voice.loopStart = (voice.loopStart & (0xff00ff << 1)) | (data << 9);
                    break;

                case 0x42: // loop end address middle
                    voice.loopEnd = (voice.loopEnd & (0xff00ff << 1)) | (data << 9);
                    break;

                case 0x43: // stop address middle
                    voice.stop = (voice.stop & (0xff00ff << 1)) | (data << 9);
                    break;

                case 0x60: // start address low
                    voice.start = (voice.start & (0xffff00 << 1)) | (data << 1);
                    break;

                case 0x61: // loop start address low
                    voice.loopStart = (voice.loopStart & (0xffff00 << 1)) | (data << 1);
                    break;

                case 0x62: // loop end address low
                    voice.loopEnd = (voice.loopEnd & (0xffff00 << 1)) | (data << 1);
                    break;

                case 0x63: // stop address low
                    voice.stop = (voice.stop & (0xffff00 << 1)) | (data << 1);
                    break;

                default:
Debug.printf(Level.WARNING, "YMZ280B: unknown register write %02X = %02X\n", this.currentRegister, data);
                    break;
                }
            } else { // upper registers are special
                switch (this.currentRegister & 0xff) {
                // DSP related (not implemented yet)
                case 0x80: // d0-2: DSP Rch, d3: enable Rch (0: yes, 1: no), d4-6: DSP Lch, d7: enable Lch (0: yes, 1: no)
                case 0x81: // d0: enable control of $82 (0: yes, 1: no)
                case 0x82: // DSP data
//Debug.printf("YMZ280B: DSP register write %02X = %02X\n", this.current_register, data);
                    break;

                case 0x84: // ROM readback / RAM write (high)
                    this.extMemAddressHi = data << 16;
                    break;

                case 0x85: // ROM readback / RAM write (middle)
                    this.extMemAddressMid = data << 8;
                    break;

                case 0x86: // ROM readback / RAM write (low) . update latch
                    this.extMemAddress = this.extMemAddressHi | this.extMemAddressMid | data;
                    if (this.extMemEnable != 0)
                        this.extReadlatch = readMemory(this.regionBase, this.regionSize, this.extMemAddress);
                    break;

                case 0x87: // RAM write
                    if (this.extMemEnable != 0) {
//                        if (!this.ext_ram_write.isnull())
//                            this.ext_ram_write(this.ext_mem_address, data);
//                        else
//                            Debug.printf("YMZ280B attempted RAM write to %X\n", this.ext_mem_address);
                        this.extMemAddress = (this.extMemAddress + 1) & 0xffffff;
                    }
                    break;

                case 0xfe: // IRQ mask
                    this.irqMask = (byte) data;

                    this.updateIrqState();
                    break;

                case 0xff: // IRQ enable, test, etc
                    this.extMemEnable = (byte) ((data & 0x40) >> 6);
                    this.irqEnable = (byte) ((data & 0x10) >> 4);

                    this.updateIrqState();

                    if (this.keyonEnable != 0 && (data & 0x80) == 0) {
                        for (int i = 0; i < 8; i++) {
                            this.voices[i].playing = 0;

                            // if update_irq_state_timer is set, cancel it.
                            this.voices[i].irqSchedule = 0;
                        }
                    } else if (this.keyonEnable == 0 && (data & 0x80) != 0) {
                        for (int i = 0; i < 8; i++) {
                            if (this.voices[i].keyon != 0 && this.voices[i].looping != 0)
                                this.voices[i].playing = 1;
                        }
                    }
                    this.keyonEnable = (byte) ((data & 0x80) >> 7);
                    break;

                default:
Debug.printf("YMZ280B: unknown register write %02X = %02X\n", this.currentRegister, data);
                    break;
                }
            }
        }

        /**
         * determine the status bits
         */
        private int computeStatus() {
            byte result;

            result = this.statusRegister;

            // clear the IRQ state
            this.statusRegister = 0;
            this.updateIrqState();

            return result;
        }

        private void updateIrqStateTimerCommon(int voiceNum) {
            Voice voice = this.voices[voiceNum];

            if (voice.irqSchedule == 0) return;

            voice.playing = 0;
            this.statusRegister |= (byte) (1 << voiceNum);

            this.updateIrqState();
            voice.irqSchedule = 0;
        }

        /**
         * update the Sound chip so that it is in sync with CPU execution
         */
        public void update(int[][] outputs, int samples) {
            int[] lAcc = outputs[0];
            int[] rAcc = outputs[1];

            // clear out the accumulator
            for (int i = 0; i < samples; i++) {
                lAcc[i] = 0;
                rAcc[i] = 0;
            }

            // loop over voices
            for (int v = 0; v < 8; v++) {

                Voice voice = this.voices[v];
                short prev = voice.lastSample;
                short curr = voice.currSample;
                short[] currData = this.scratch;
                int currDataP = 0;
                int[] lDest = lAcc;
                int[] rDest = rAcc;
                int destP = 0;
                int newSamples, samplesLeft;
                int finalPos;
                int remaining = samples;
                int lVol = voice.outputLeft;
                int rVol = voice.outputRight;

                // skip if muted
                if (voice.muted != 0)
                    continue;

                // quick out if we're not playing and we're at 0
                if (voice.playing == 0 && curr == 0 && prev == 0) {
                    // make sure next Sound plays immediately
                    voice.outputPos = FRAC_ONE;

                    continue;
                }

                // finish off the current sample

                // interpolate */
                while (remaining > 0 && voice.outputPos < FRAC_ONE) {
                    int interpSample = (((int) prev * (FRAC_ONE - voice.outputPos)) + ((int) curr * voice.outputPos)) >> FRAC_BITS;

                    lDest[destP] += interpSample * lVol;
                    rDest[destP] += interpSample * rVol;
                    destP++;
                    voice.outputPos += voice.outputStep;
                    remaining--;
                }

                // if we're over, continue; otherwise, we're done
                if (voice.outputPos >= FRAC_ONE)
                    voice.outputPos -= FRAC_ONE;
                else
                    continue;

                // compute how many new samples we need
                finalPos = voice.outputPos + remaining * voice.outputStep;
                newSamples = (finalPos + FRAC_ONE) >> FRAC_BITS;
                if (newSamples > MAX_SAMPLE_CHUNK)
                    newSamples = MAX_SAMPLE_CHUNK;
                samplesLeft = newSamples;

                // generate them into our buffer
                samplesLeft = switch (voice.playing << 7 | voice.mode) {
                    case 0x81 -> voice.generateAdpcm(this.regionBase, this.regionSize, this.scratch, newSamples);
                    case 0x82 -> voice.generate_pcm8(this.regionBase, this.regionSize, this.scratch, newSamples);
                    case 0x83 -> voice.generatePcm16(this.regionBase, this.regionSize, this.scratch, newSamples);
                    default -> {
                        Arrays.fill(this.scratch, 0, newSamples, (byte) 0);
                        yield 0;
                    }
                };

                /* if there are leftovers, ramp back to 0 */
                if (samplesLeft != 0) {
                    /* note: samplesLeft bit 16 is set if the Voice was finished at the same time the function ended */
                    int base;
                    int i, t;

                    samplesLeft &= 0xffff;
                    base = newSamples - samplesLeft;
                    t = (base == 0) ? curr : this.scratch[base - 1];

                    for (i = 0; i < samplesLeft; i++) {
                        if (t < 0) t = -((-t * 15) >> 4);
                        else if (t > 0) t = (t * 15) >> 4;
                        this.scratch[base + i] = (short) t;
                    }

                    // if we hit the end and IRQs are enabled, signal it
                    if (base != 0) {
                        voice.playing = 0;

                        // set update_irq_state_timer. IRQ is signaled on next CPU execution.
                        //timer_set(this.device.machine, attotime_zero, chip, 0, update_irq_state_cb[v]);
                        voice.irqSchedule = 1;
                    }
                }

                // advance forward one sample
                prev = curr;
                curr = currData[currDataP++];

                // then sample-rate convert with linear interpolation
                while (remaining > 0) {
                    // interpolate
                    while (remaining > 0 && voice.outputPos < FRAC_ONE) {
                        int interp_sample = (((int) prev * (FRAC_ONE - voice.outputPos)) + ((int) curr * voice.outputPos)) >> FRAC_BITS;

                        lDest[destP] += interp_sample * lVol;
                        rDest[destP] += interp_sample * rVol;
                        destP++;
                        voice.outputPos += voice.outputStep;
                        remaining--;
                    }

                    // if we're over, grab the next samples
                    if (voice.outputPos >= FRAC_ONE) {
                        voice.outputPos -= FRAC_ONE;
                        prev = curr;
                        curr = currData[currDataP++];
                    }
                }

                // remember the last samples
                voice.lastSample = prev;
                voice.currSample = curr;
            }

            for (int v = 0; v < samples; v++) {
                outputs[0][v] /= 256;
                outputs[1][v] /= 256;
            }

            for (int v = 0; v < 8; v++)
                updateIrqStateTimerCommon(v);
        }

        /**
         * start emulation of the YMZ280B
         */
        public int start(int clock) {
            // initialize the rest of the structure
            this.masterClock = (double) clock / 384.0;

            this.rate = this.masterClock * 2.0;

            this.regionSize = 0x00;
            this.regionBase = null;

            // allocate memory
            this.scratch = new short[MAX_SAMPLE_CHUNK];

            for (int c = 0; c < 8; c++)
                this.voices[c].muted = 0x00;

            return (int) this.rate;
        }

        public void stop() {
            this.regionBase = null;
        }

        public void reset() {
            // new code from MAME 0.143u4

            // initial clear registers
            for (int i = 0xff; i >= 0; i--) {
                if (i == 0x83 || (i >= 88 && i <= 0xfd))
                    continue; // avoid too many debug messages
                this.currentRegister = (byte) i;
                this.writeToRegister(0);
            }

            this.currentRegister = 0;
            this.statusRegister = 0;

            /* clear other Voice parameters */
            for (int i = 0; i < 8; i++) {
                this.voices[i].reset();
            }
        }

        public byte read(int offset) {
            if ((offset & 1) == 0) {

                if (this.extMemEnable == 0)
                    return (byte) 0xff;

                // read from external memory
                byte ret = this.extReadlatch;
                ret = readMemory(this.regionBase, this.regionSize, this.extMemAddress);
                this.extMemAddress = (this.extMemAddress + 1) & 0xffffff;
                return ret;
            } else {
                return (byte) this.computeStatus();
            }
        }

        public void write(int offset, byte data) {
            if ((offset & 1) == 0)
                this.currentRegister = data;
            else {
                this.writeToRegister(data);
            }
        }

        public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
            if (this.regionSize != romSize) {
                this.regionBase = new byte[romSize];
                this.regionSize = romSize;
                Arrays.fill(this.regionBase, 0, romSize, (byte) 0xff);
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            System.arraycopy(romData, 0, this.regionBase, dataStart, dataLength);
        }

        public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
            if (this.regionSize != romSize) {
                this.regionBase = new byte[romSize];
                this.regionSize = romSize;
                Arrays.fill(this.regionBase, 0, romSize, (byte) 0xff);
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            System.arraycopy(romData, srcStartAddress, this.regionBase, dataStart, dataLength);
        }

        public void set_mute_mask(int muteMask) {
            for (int c = 0; c < 8; c++)
                this.voices[c].muted = (byte) ((muteMask >> c) & 0x01);
        }
    }

    private static final int MAX_CHIPS = 0x10;
    private YmZ280bState[] chips = new YmZ280bState[] {new YmZ280bState(), new YmZ280bState()};

    @Override
    public String getName() {
        return "YMZ280B";
    }

    @Override
    public String getShortName() {
        return "YMZ";
    }

    /**
     * update the Sound chip so that it is in sync with CPU execution
     */
    public void ymz280b_update(byte chipId, int[][] outputs, int samples) {
        YmZ280bState chip = chips[chipId];
        chip.update(outputs, samples);
    }

    /**
     * start emulation of the YMZ280B
     */
    public int device_start_ymz280b(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        YmZ280bState chip = chips[chipId];
        return chip.start(clock);
    }

    public void device_stop_ymz280b(byte chipId) {
        YmZ280bState chip = chips[chipId];
        chip.stop();
    }

    public void device_reset_ymz280b(byte chipId) {
        YmZ280bState chip = chips[chipId];
        chip.reset();    }

    /**
     * handle external accesses
     */
    public byte ymz280b_r(byte chipId, int offset) {
        YmZ280bState chip = chips[chipId];
        return chip.read(offset);
    }

    public void ymz280b_w(byte chipId, int offset, byte data) {
        YmZ280bState chip = chips[chipId];
        chip.write(offset, data);
    }

    public void ymz280b_write_rom(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        YmZ280bState chip = chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void ymz280b_write_rom(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        YmZ280bState chip = chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAddress);
    }


    public void ymz280b_set_mute_mask(byte chipId, int muteMask) {
        YmZ280bState chip = chips[chipId];
        chip.set_mute_mask(muteMask);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        return write(chipId, (byte) adr, (byte) data);
    }

    /**
     * Generic get_info
     */
    /*DEVICE_GET_INFO( YmZ280b ) {
            case DEVINFO_STR_NAME:       strcpy(info.s, "YMZ280B");      break;
            case DEVINFO_STR_FAMILY:     strcpy(info.s, "Yamaha Wavetable");   break;
            case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
            case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
    }*/
}


