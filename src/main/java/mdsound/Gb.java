// license:BSD-3-Clause
// copyright-holders:Wilbert Pol, Anthony Kruize
// thanks-to:Shay Green

package mdsound;


/**
 * Game Boy Sound emulation (c) Anthony Kruize (trandor@labyrinth.net.au)
 *
 * Anyways, Sound on the Game Boy consists of 4 separate 'channels'
 *   Sound1 = Quadrangular waves with SWEEP and ENVELOPE functions  (NR10,11,12,13,14)
 *   Sound2 = Quadrangular waves with ENVELOPE functions (NR21,22,23,24)
 *   Sound3 = Wave patterns from WaveRAM (NR30,31,32,33,34)
 *   Sound4 = White noise with an envelope (NR41,42,43,44)
 *
 * Each Sound channel has 2 modes, namely ON and OFF...  whoa
 *
 * These tend to be the two most important equations in
 * converting between Hertz and GB frequency registers:
 * (Sounds will have a 2.4% higher frequency on Super GB.)
 *       Gb = 2048 - (131072 / Hz)
 *       Hz = 131072 / (2048 - Gb)
 *
 * Changes:
 *
 *   10/2/2002       AK - Preliminary Sound code.
 *   13/2/2002       AK - Added a hack for mode 4, other fixes.
 *   23/2/2002       AK - Use lookup tables, added sweep to mode 1. Re-wrote the square
 *                        wave generation.
 *   13/3/2002       AK - Added mode 3, better lookup tables, other adjustments.
 *   15/3/2002       AK - Mode 4 can now change frequencies.
 *   31/3/2002       AK - Accidently forgot to handle counter/consecutive for mode 1.
 *    3/4/2002       AK - Mode 1 sweep can still occur if shift is 0.  Don't let frequency
 *                        go past the maximum allowed value. Fixed Mode 3 length table.
 *                        Slight adjustment to Mode 4's period table generation.
 *    5/4/2002       AK - Mode 4 is done correctly, using a polynomial counter instead
 *                        of being a total hack.
 *    6/4/2002       AK - Slight tweak to mode 3's frequency calculation.
 *   13/4/2002       AK - Reset envelope value when Sound is initialized.
 *   21/4/2002       AK - Backed out the mode 3 frequency calculation change.
 *                        Merged init functions into gameboy_sound_w().
 *   14/5/2002       AK - Removed magic numbers in the fixed point math.
 *   12/6/2002       AK - Merged SOUNDx structs into one Sound struct.
 *  26/10/2002       AK - Finally fixed channel 3!
 * xx/4-5/2016       WP - Rewrote Sound core. Most of the code is not optimized yet.

 TODO:
 - Implement different behavior of CGB-02.
 - Implement different behavior of CGB-05.
 - Perform more tests on real hardware to figure out when the frequency counters are
 reloaded.
 - Perform more tests on real hardware to understand when changes to the noise divisor
 and shift kick in.
 - Optimize the channel update methods.
 */
public class Gb extends Instrument.BaseInstrument {

    @Override
    public void reset(byte chipID) {
        resetDevice(chipID);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipID, int clock) {
        return startDevice(chipID, 4194304);
    }

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        return startDevice(chipID, (int) clockValue);
    }

    @Override
    public void stop(byte chipID) {
        stopDevice(chipID);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        updateDevice(chipID, outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    public static class GbSound {

        private static final int RC_SHIFT = 16;

        static class Ratio {
            public int inc; // counter increment
            public int val; // current value

            private void setRatio(int mul, int div) {
                this.inc = (int) ((((long) mul << RC_SHIFT) + div / 2) / div);
            }
        }

        /*
         * CONSTANTS
         */

        private static final int NR10 = 0x00;
        private static final int NR11 = 0x01;
        private static final int NR12 = 0x02;
        private static final int NR13 = 0x03;
        private static final int NR14 = 0x04;
        // 0x05
        private static final int NR21 = 0x06;
        private static final int NR22 = 0x07;
        private static final int NR23 = 0x08;
        private static final int NR24 = 0x09;
        private static final int NR30 = 0x0A;
        private static final int NR31 = 0x0B;
        private static final int NR32 = 0x0C;
        private static final int NR33 = 0x0D;
        private static final int NR34 = 0x0E;
        // 0x0F
        private static final int NR41 = 0x10;
        private static final int NR42 = 0x11;
        private static final int NR43 = 0x12;
        private static final int NR44 = 0x13;
        private static final int NR50 = 0x14;
        private static final int NR51 = 0x15;
        private static final int NR52 = 0x16;
        // 0x17 - 0x1F
        private static final int AUD3W0 = 0x20;
        private static final int AUD3W1 = 0x21;
        private static final int AUD3W2 = 0x22;
        private static final int AUD3W3 = 0x23;
        private static final int AUD3W4 = 0x24;
        private static final int AUD3W5 = 0x25;
        private static final int AUD3W6 = 0x26;
        private static final int AUD3W7 = 0x27;
        private static final int AUD3W8 = 0x28;
        private static final int AUD3W9 = 0x29;
        private static final int AUD3WA = 0x2A;
        private static final int AUD3WB = 0x2B;
        private static final int AUD3WC = 0x2C;
        private static final int AUD3WD = 0x2D;
        private static final int AUD3WE = 0x2E;
        private static final int AUD3WF = 0x2F;

        private static final int FRAME_CYCLES = 8192;

        /* Represents wave duties of 12.5%, 25%, 50% and 75% */
        private static final int[][] waveDutyTable = new int[][] {
                new int[] {-1, -1, -1, -1, -1, -1, -1, 1},
                new int[] {1, -1, -1, -1, -1, -1, -1, 1},
                new int[] {1, -1, -1, -1, -1, 1, 1, 1},
                new int[] {-1, 1, 1, 1, 1, 1, 1, -1}
        };

        /*
         * TYPE DEFINITIONS
         */

        public static class Sound {
            /* Common */
            public byte[] reg = new byte[5];
            public boolean on;
            public byte channel;
            public byte length;
            public byte lengthMask;
            public boolean lengthCounting;
            public boolean lengthEnabled;
            /* Mode 1, 2, 3 */
            public int cyclesLeft;
            public byte duty;
            /* Mode 1, 2, 4 */
            public boolean envelopeEnabled;
            public byte envelopeValue;
            public byte envelopeDirection;
            public byte envelopeTime;
            public byte envelopeCount;
            public byte signal;
            /* Mode 1 */
            public int frequency;
            public int frequencyCounter;
            public boolean sweepEnabled;
            public boolean sweepNegModeUsed;
            public byte sweepShift;
            public int sweepDirection;
            public byte sweepTime;
            public byte sweepCount;
            /* Mode 3 */
            public byte level;
            public byte offset;
            public int dutyCount;
            public byte currentSample;
            public boolean sampleReading;
            /* Mode 4 */
            public boolean noiseShort;
            public int noiseLfsr;
            public byte muted;

            public void tickLength() {
                if (this.lengthEnabled) {
                    this.length = (byte) ((this.length + 1) & this.lengthMask);
                    if (this.length == 0) {
                        this.on = false;
                        this.lengthCounting = false;
                    }
                }
            }

            public int calculateNextSweep() {
                int new_frequency;
                this.sweepNegModeUsed = (this.sweepDirection < 0);
                new_frequency = this.frequency + this.sweepDirection * (this.frequency >> this.sweepShift);

                if (new_frequency > 0x7FF) {
                    this.on = false;
                }

                return new_frequency;
            }

            private void applyNextSweep() {
                int new_frequency = calculateNextSweep();

                if (this.on && this.sweepShift > 0) {
                    this.frequency = (int) new_frequency;
                    this.reg[3] = (byte) (this.frequency & 0xFF);
                }
            }

            private void tickSweep() {
                this.sweepCount = (byte) ((this.sweepCount - 1) & 0x07);
                if (this.sweepCount == 0) {
                    this.sweepCount = this.sweepTime;

                    if (this.sweepEnabled && this.sweepTime > 0) {

                        applyNextSweep();

                        calculateNextSweep();
                    }
                }
            }

            private void tickEnvelope() {
                if (this.envelopeEnabled) {
                    this.envelopeCount = (byte) ((this.envelopeCount - 1) & 0x07);

                    if (this.envelopeCount == 0) {
                        this.envelopeCount = this.envelopeTime;

                        if (this.envelopeCount != 0) {
                            byte new_envelope_value = (byte) (this.envelopeValue + this.envelopeDirection);

                            if (new_envelope_value >= 0 && new_envelope_value <= 15) {
                                this.envelopeValue = new_envelope_value;
                            } else {
                                this.envelopeEnabled = false;
                            }
                        }
                    }
                }
            }

            public boolean dacEnabled() {
                return ((this.channel != 3) ? (this.reg[2] & 0xF8) : (this.reg[0] & 0x80)) != 0;
            }

            private void updateSquareChannel(int cycles) {
                if (this.on) {
                    // compensate for leftover cycles
                    if (this.cyclesLeft > 0) {
                        cycles += this.cyclesLeft;
                        this.cyclesLeft = 0;
                    }

                    while (cycles > 0) {
                        // Emit sample(s)
                        if (cycles < 4) {
                            this.cyclesLeft = cycles;
                            cycles = 0;
                        } else {
                            cycles -= 4;
                            this.frequencyCounter = (int) ((this.frequencyCounter + 1) & 0x7FF);
                            if (this.frequencyCounter == 0) {
                                this.dutyCount = (this.dutyCount + 1) & 0x07;
                                this.signal = (byte) (waveDutyTable[this.duty][this.dutyCount]);

                                // Reload frequency counter
                                this.frequencyCounter = this.frequency;
                            }
                        }
                    }
                }
            }
        }

        public static class SoundC {
            public byte on;
            public byte volLeft;
            public byte volRight;
            public byte mode1Left;
            public byte mode1Right;
            public byte mode2Left;
            public byte mode2Right;
            public byte mode3Left;
            public byte mode3Right;
            public byte mode4Left;
            public byte mode4Right;
            public int cycles;
            public boolean waveRamLocked;
        }

        private static final int MODE_DMG = 0x00;
        private static final int MODE_CGB04 = 0x01;

        public int rate;

        public Sound snd1;
        public Sound snd2;
        public Sound snd3;
        public Sound snd4;
        public SoundC sndControl;

        public byte[] sndRegs = new byte[0x30];

        public Ratio cycleCntr;

        public byte gbMode;

        private byte boostWaveChn = 0x00;

        /*
         * IMPLEMENTATION
         */

        private void soundWriteInternal(byte offset, byte data) {
            /* Store the value */
            byte oldData = this.sndRegs[offset];

            if (this.sndControl.on != 0) {
                this.sndRegs[offset] = data;
            }

            switch (offset) {
            /* MODE 1 */
            case NR10: /* Sweep (R/W) */
                this.snd1.reg[0] = data;
                this.snd1.sweepShift = (byte) (data & 0x7);
                this.snd1.sweepDirection = (data & 0x8) != 0 ? -1 : 1;
                this.snd1.sweepTime = (byte) ((data & 0x70) >> 4);
                if ((oldData & 0x08) != 0 && (data & 0x08) == 0 && this.snd1.sweepNegModeUsed) {
                    this.snd1.on = false;
                }
                break;
            case NR11: /* Sound length/Wave pattern duty (R/W) */
                this.snd1.reg[1] = data;
                if (this.sndControl.on != 0) {
                    this.snd1.duty = (byte) ((data & 0xc0) >> 6);
                }
                this.snd1.length = (byte) (data & 0x3f);
                this.snd1.lengthCounting = true;
                break;
            case NR12: /* Envelope (R/W) */
                this.snd1.reg[2] = data;
                this.snd1.envelopeValue = (byte) (data >> 4);
                this.snd1.envelopeDirection = (byte) ((data & 0x8) != 0 ? 1 : -1);
                this.snd1.envelopeTime = (byte) (data & 0x07);
                if (!this.snd1.dacEnabled()) {
                    this.snd1.on = false;
                }
                break;
            case NR13: /* Frequency lo (R/W) */
                this.snd1.reg[3] = data;
                // Only enabling the frequency line breaks blarggs's Sound test // #5
                // This condition may not be correct
                if (!this.snd1.sweepEnabled) {
                    this.snd1.frequency = (int) (((this.snd1.reg[4] & 0x7) << 8) | this.snd1.reg[3]);
                }
                break;
            case NR14: /* Frequency hi / Initialize (R/W) */
                this.snd1.reg[4] = data;
            {
                boolean length_was_enabled = this.snd1.lengthEnabled;

                this.snd1.lengthEnabled = (data & 0x40) != 0;
                this.snd1.frequency = ((this.sndRegs[NR14] & 0x7) << 8) | this.snd1.reg[3];

                if (!length_was_enabled && (this.sndControl.cycles & FRAME_CYCLES) == 0 && this.snd1.lengthCounting) {
                    if (this.snd1.lengthEnabled) {
                        this.snd1.tickLength();
                    }
                }

                if ((data & 0x80) != 0) {
                    this.snd1.on = true;
                    this.snd1.envelopeEnabled = true;
                    this.snd1.envelopeValue = (byte) (this.snd1.reg[2] >> 4);
                    this.snd1.envelopeCount = this.snd1.envelopeTime;
                    this.snd1.sweepCount = this.snd1.sweepTime;
                    this.snd1.sweepNegModeUsed = false;
                    this.snd1.signal = 0;
                    this.snd1.length = (byte) (this.snd1.reg[1] & 0x3f); // VGM log fix -Valley Bell
                    this.snd1.lengthCounting = true;
                    this.snd1.frequency = ((this.snd1.reg[4] & 0x7) << 8) | this.snd1.reg[3];
                    this.snd1.frequencyCounter = this.snd1.frequency;
                    this.snd1.cyclesLeft = 0;
                    this.snd1.dutyCount = 0;
                    this.snd1.sweepEnabled = (this.snd1.sweepShift != 0) || (this.snd1.sweepTime != 0);
                    if (!this.snd1.dacEnabled()) {
                        this.snd1.on = false;
                    }
                    if (this.snd1.sweepShift > 0) {
                        this.snd1.calculateNextSweep();
                    }

                    if (this.snd1.length == 0 && this.snd1.lengthEnabled && (this.sndControl.cycles & FRAME_CYCLES) == 0) {
                        this.snd1.tickLength();
                    }
                } else {
                    // This condition may not be correct
                    if (!this.snd1.sweepEnabled) {
                        this.snd1.frequency = ((this.snd1.reg[4] & 0x7) << 8) | this.snd1.reg[3];
                    }
                }
            }
            break;

            /* MODE 2 */
            case NR21: /* Sound length/Wave pattern duty (R/W) */
                this.snd2.reg[1] = data;
                if (this.sndControl.on != 0) {
                    this.snd2.duty = (byte) ((data & 0xc0) >> 6);
                }
                this.snd2.length = (byte) (data & 0x3f);
                this.snd2.lengthCounting = true;
                break;
            case NR22: /* Envelope (R/W) */
                this.snd2.reg[2] = data;
                this.snd2.envelopeValue = (byte) (data >> 4);
                this.snd2.envelopeDirection = (byte) ((data & 0x8) != 0 ? 1 : -1);
                this.snd2.envelopeTime = (byte) (data & 0x07);
                if (!this.snd2.dacEnabled()) {
                    this.snd2.on = false;
                }
                break;
            case NR23: /* Frequency lo (R/W) */
                this.snd2.reg[3] = data;
                this.snd2.frequency = ((this.snd2.reg[4] & 0x7) << 8) | this.snd2.reg[3];
                break;
            case NR24: /* Frequency hi / Initialize (R/W) */
                this.snd2.reg[4] = data;
            {
                boolean length_was_enabled = this.snd2.lengthEnabled;

                this.snd2.lengthEnabled = (data & 0x40) != 0;

                if (!length_was_enabled && (this.sndControl.cycles & FRAME_CYCLES) == 0 && this.snd2.lengthCounting) {
                    if (this.snd2.lengthEnabled) {
                        this.snd2.tickLength();
                    }
                }

                if ((data & 0x80) != 0) {
                    this.snd2.on = true;
                    this.snd2.envelopeEnabled = true;
                    this.snd2.envelopeValue = (byte) (this.snd2.reg[2] >> 4);
                    this.snd2.envelopeCount = this.snd2.envelopeTime;
                    this.snd2.frequency = ((this.snd2.reg[4] & 0x7) << 8) | this.snd2.reg[3];
                    this.snd2.frequencyCounter = this.snd2.frequency;
                    this.snd2.cyclesLeft = 0;
                    this.snd2.dutyCount = 0;
                    this.snd2.signal = 0;
                    this.snd2.length = (byte) (this.snd2.reg[1] & 0x3f); // VGM log fix -Valley Bell
                    this.snd2.lengthCounting = true;

                    if (!this.snd2.dacEnabled()) {
                        this.snd2.on = false;
                    }

                    if (this.snd2.length == 0 && this.snd2.lengthEnabled && (this.sndControl.cycles & FRAME_CYCLES) == 0) {
                        this.snd2.tickLength();
                    }
                } else {
                    this.snd2.frequency = (int) (((this.snd2.reg[4] & 0x7) << 8) | this.snd2.reg[3]);
                }
            }
            break;

            /* MODE 3 */
            case NR30: /* Sound On/Off (R/W) */
                this.snd3.reg[0] = data;
                if (!this.snd3.dacEnabled()) {
                    this.snd3.on = false;
                }
                break;
            case NR31: /* Sound Length (R/W) */
                this.snd3.reg[1] = data;
                this.snd3.length = data;
                this.snd3.lengthCounting = true;
                break;
            case NR32: /* Select Output Level */
                this.snd3.reg[2] = data;
                this.snd3.level = (byte) ((data & 0x60) >> 5);
                break;
            case NR33: /* Frequency lo (W) */
                this.snd3.reg[3] = data;
                this.snd3.frequency = ((this.snd3.reg[4] & 0x7) << 8) | this.snd3.reg[3];
                break;
            case NR34: /* Frequency hi / Initialize (W) */
                this.snd3.reg[4] = data;
            {
                boolean length_was_enabled = this.snd3.lengthEnabled;

                this.snd3.lengthEnabled = (data & 0x40) != 0 ? true : false;

                if (!length_was_enabled && (this.sndControl.cycles & FRAME_CYCLES) == 0 && this.snd3.lengthCounting) {
                    if (this.snd3.lengthEnabled) {
                        this.snd3.tickLength();
                    }
                }

                if ((data & 0x80) != 0) {
                    if (this.snd3.on && this.snd3.frequencyCounter == 0x7ff) {
                        corruptWaveRam();
                    }
                    this.snd3.on = true;
                    this.snd3.offset = 0;
                    this.snd3.duty = 1;
                    this.snd3.dutyCount = 0;
                    this.snd3.length = this.snd3.reg[1];    // VGM log fix -Valley Bell
                    this.snd3.lengthCounting = true;
                    this.snd3.frequency = ((this.snd3.reg[4] & 0x7) << 8) | this.snd3.reg[3];
                    this.snd3.frequencyCounter = this.snd3.frequency;
                    // There is a tiny bit of delay in starting up the wave channel(?)
                    //
                    // Results from older code where corruption of wave ram was triggered when sample_reading == true:
                    // 4 breaks test 09 (read wram), fixes test 10 (write trigger), breaks test 12 (write wram)
                    // 6 fixes test 09 (read wram), breaks test 10 (write trigger), fixes test 12 (write wram)
                    this.snd3.cyclesLeft = 0 + 6;
                    this.snd3.sampleReading = false;

                    if (!this.snd3.dacEnabled()) {
                        this.snd3.on = false;
                    }

                    if (this.snd3.length == 0 && this.snd3.lengthEnabled && (this.sndControl.cycles & FRAME_CYCLES) == 0) {
                        this.snd3.tickLength();
                    }
                } else {
                    this.snd3.frequency = ((this.snd3.reg[4] & 0x7) << 8) | this.snd3.reg[3];
                }
            }
            break;

            /* MODE 4 */
            case NR41: /* Sound Length (R/W) */
                this.snd4.reg[1] = data;
                this.snd4.length = (byte) (data & 0x3f);
                this.snd4.lengthCounting = true;
                break;
            case NR42: /* Envelope (R/W) */
                this.snd4.reg[2] = data;
                this.snd4.envelopeValue = (byte) (data >> 4);
                this.snd4.envelopeDirection = (byte) ((data & 0x8) != 0 ? 1 : -1);
                this.snd4.envelopeTime = (byte) (data & 0x07);
                if (!this.snd4.dacEnabled()) {
                    this.snd4.on = false;
                }
                break;
            case NR43: /* Polynomial Counter/Frequency */
                this.snd4.reg[3] = data;
                this.snd4.noiseShort = (data & 0x8) != 0;
                break;
            case NR44: /* Counter/Consecutive / Initialize (R/W)  */
                this.snd4.reg[4] = data;
            {
                boolean length_was_enabled = this.snd4.lengthEnabled;

                this.snd4.lengthEnabled = (data & 0x40) != 0 ? true : false;

                if (!length_was_enabled && (this.sndControl.cycles & FRAME_CYCLES) == 0 && this.snd4.lengthCounting) {
                    if (this.snd4.lengthEnabled) {
                        this.snd4.tickLength();
                    }
                }

                if ((data & 0x80) != 0) {
                    this.snd4.on = true;
                    this.snd4.envelopeEnabled = true;
                    this.snd4.envelopeValue = (byte) (this.snd4.reg[2] >> 4);
                    this.snd4.envelopeCount = this.snd4.envelopeTime;
                    this.snd4.frequencyCounter = 0;
                    this.snd4.cyclesLeft = noisePeriodCycles();
                    this.snd4.signal = -1;
                    this.snd4.noiseLfsr = 0x7fff;
                    this.snd4.length = (byte) (this.snd4.reg[1] & 0x3f); // VGM log fix -Valley Bell
                    this.snd4.lengthCounting = true;

                    if (!this.snd4.dacEnabled()) {
                        this.snd4.on = false;
                    }

                    if (this.snd4.length == 0 && this.snd4.lengthEnabled && (this.sndControl.cycles & FRAME_CYCLES) == 0) {
                        this.snd4.tickLength();
                    }
                }
            }
            break;

            /* CONTROL */
            case NR50: /* Channel Control / On/Off / Volume (R/W)  */
                this.sndControl.volLeft = (byte) (data & 0x7);
                this.sndControl.volRight = (byte) ((data & 0x70) >> 4);
                break;
            case NR51: /* Selection of Sound Output Terminal */
                this.sndControl.mode1Right = (byte) (data & 0x1);
                this.sndControl.mode1Left = (byte) ((data & 0x10) >> 4);
                this.sndControl.mode2Right = (byte) ((data & 0x2) >> 1);
                this.sndControl.mode2Left = (byte) ((data & 0x20) >> 5);
                this.sndControl.mode3Right = (byte) ((data & 0x4) >> 2);
                this.sndControl.mode3Left = (byte) ((data & 0x40) >> 6);
                this.sndControl.mode4Right = (byte) ((data & 0x8) >> 3);
                this.sndControl.mode4Left = (byte) ((data & 0x80) >> 7);
                break;
            case NR52: // Sound On/Off (R/W)
                // Only bit 7 is writable, writing to bits 0-3 does NOT enable or disable Sound. They are read-only.
                if ((data & 0x80) == 0) {
                    // On DMG the length counters are not affected and not clocked
                    // powering off should actually clear all registers
                    apuPowerOff();
                } else {
                    if (this.sndControl.on == 0) {
                        // When switching on, the next step should be 0.
                        this.sndControl.cycles |= 7 * FRAME_CYCLES;
                    }
                }
                this.sndControl.on = (byte) ((data & 0x80) != 0 ? 1 : 0);// true : false;
                this.sndRegs[NR52] = (byte) (data & 0x80);
                break;
            }
        }

        public void corruptWaveRam() {
            if (this.gbMode != MODE_DMG)
                return;

            if (this.snd3.offset < 8) {
                this.sndRegs[AUD3W0] = this.sndRegs[AUD3W0 + (this.snd3.offset / 2)];
            } else {
                int i;
                for (i = 0; i < 4; i++) {
                    this.sndRegs[AUD3W0 + i] = this.sndRegs[AUD3W0 + ((this.snd3.offset / 2) & ~0x03) + i];
                }
            }
        }

        public void apuPowerOff() {
            switch (this.gbMode) {
            case MODE_DMG:
                soundWriteInternal((byte) NR10, (byte) 0x00);
                this.snd1.duty = 0;
                this.sndRegs[NR11] = 0;
                soundWriteInternal((byte) NR12, (byte) 0x00);
                soundWriteInternal((byte) NR13, (byte) 0x00);
                soundWriteInternal((byte) NR14, (byte) 0x00);
                this.snd1.lengthCounting = false;
                this.snd1.sweepNegModeUsed = false;

                this.sndRegs[NR21] = 0;
                soundWriteInternal((byte) NR22, (byte) 0x00);
                soundWriteInternal((byte) NR23, (byte) 0x00);
                soundWriteInternal((byte) NR24, (byte) 0x00);
                this.snd2.lengthCounting = false;

                soundWriteInternal((byte) NR30, (byte) 0x00);
                soundWriteInternal((byte) NR32, (byte) 0x00);
                soundWriteInternal((byte) NR33, (byte) 0x00);
                soundWriteInternal((byte) NR34, (byte) 0x00);
                this.snd3.lengthCounting = false;
                this.snd3.currentSample = 0;

                this.sndRegs[NR41] = 0;
                soundWriteInternal((byte) NR42, (byte) 0x00);
                soundWriteInternal((byte) NR43, (byte) 0x00);
                soundWriteInternal((byte) NR44, (byte) 0x00);
                this.snd4.lengthCounting = false;
                this.snd4.cyclesLeft = noisePeriodCycles();
                break;
            case MODE_CGB04:
                soundWriteInternal((byte) NR10, (byte) 0x00);
                this.snd1.duty = 0;
                soundWriteInternal((byte) NR11, (byte) 0x00);
                soundWriteInternal((byte) NR12, (byte) 0x00);
                soundWriteInternal((byte) NR13, (byte) 0x00);
                soundWriteInternal((byte) NR14, (byte) 0x00);
                this.snd1.lengthCounting = false;
                this.snd1.sweepNegModeUsed = false;

                soundWriteInternal((byte) NR21, (byte) 0x00);
                soundWriteInternal((byte) NR22, (byte) 0x00);
                soundWriteInternal((byte) NR23, (byte) 0x00);
                soundWriteInternal((byte) NR24, (byte) 0x00);
                this.snd2.lengthCounting = false;

                soundWriteInternal((byte) NR30, (byte) 0x00);
                soundWriteInternal((byte) NR31, (byte) 0x00);
                soundWriteInternal((byte) NR32, (byte) 0x00);
                soundWriteInternal((byte) NR33, (byte) 0x00);
                soundWriteInternal((byte) NR34, (byte) 0x00);
                this.snd3.lengthCounting = false;
                this.snd3.currentSample = 0;

                soundWriteInternal((byte) NR41, (byte) 0x00);
                soundWriteInternal((byte) NR42, (byte) 0x00);
                soundWriteInternal((byte) NR43, (byte) 0x00);
                soundWriteInternal((byte) NR44, (byte) 0x00);
                this.snd4.lengthCounting = false;
                this.snd4.cyclesLeft = noisePeriodCycles();
                break;
            }

            this.snd1.on = false;
            this.snd2.on = false;
            this.snd3.on = false;
            this.snd4.on = false;

            this.sndControl.waveRamLocked = false;

            for (int i = NR44 + 1; i < NR52; i++) {
                soundWriteInternal((byte) i, (byte) 0x00);
            }
        }

        private void updateWaveChannel(Sound snd, int cycles) {
            if (snd.on) {
                // compensate for leftover cycles
                if (snd.cyclesLeft > 0) {
                    cycles += snd.cyclesLeft;
                    snd.cyclesLeft = 0;
                }

                while (cycles > 0) {
                    // Emit current sample

                    // cycles -= 2
                    if (cycles < 2) {
                        snd.cyclesLeft = cycles;
                        cycles = 0;
                    } else {
                        cycles -= 2;

                        // Calculate next state
                        snd.frequencyCounter = (int) ((snd.frequencyCounter + 1) & 0x7FF);
                        snd.sampleReading = false;
                        if (this.gbMode == MODE_DMG && snd.frequencyCounter == 0x7ff)
                            snd.offset = (byte) ((snd.offset + 1) & 0x1F);
                        if (snd.frequencyCounter == 0) {
                            // Read next sample
                            snd.sampleReading = true;
                            if (this.gbMode == MODE_CGB04)
                                snd.offset = (byte) ((snd.offset + 1) & 0x1F);
                            snd.currentSample = (byte) (this.sndRegs[AUD3W0 + (snd.offset / 2)]);
                            if ((snd.offset & 0x01) == 0) {
                                snd.currentSample >>= 4;
                            }
                            snd.currentSample = (byte) ((snd.currentSample & 0x0F) - 8);
                            if (boostWaveChn != 0)

                                snd.currentSample <<= 1;

                            snd.signal = (byte) (snd.level != 0 ? snd.currentSample / (1 << (snd.level - 1)) : 0);

                            // Reload frequency counter
                            snd.frequencyCounter = snd.frequency;
                        }
                    }
                }
            }
        }

        private void updateNoiseChannel(Sound snd, int cycles) {
            while (cycles > 0) {
                if (cycles < snd.cyclesLeft) {
                    if (snd.on) {
                        // generate samples
                    }

                    snd.cyclesLeft -= cycles;
                    cycles = 0;
                } else {
                    int feedback;

                    if (snd.on) {
                        // generate samples
                    }

                    cycles -= snd.cyclesLeft;
                    snd.cyclesLeft = noisePeriodCycles();

                    /* Using a Polynomial Counter (aka Linear Feedback Shift Register)
                     Mode 4 has a 15 bit counter so we need to shift the
                     bits around accordingly */
                    feedback = ((snd.noiseLfsr >> 1) ^ snd.noiseLfsr) & 1;
                    snd.noiseLfsr = (snd.noiseLfsr >> 1) | (feedback << 14);
                    if (snd.noiseShort) {
                        snd.noiseLfsr = (snd.noiseLfsr & ~(1 << 6)) | (feedback << 6);
                    }
                    snd.signal = (byte) ((snd.noiseLfsr & 1) != 0 ? -1 : 1);
                }
            }
        }

        private void updateState(int cycles) {
            int old_cycles;

            if (this.sndControl.on == 0)
                return;

            old_cycles = this.sndControl.cycles;
            this.sndControl.cycles += cycles;

            if ((old_cycles / FRAME_CYCLES) != (this.sndControl.cycles / FRAME_CYCLES)) {
                // Left over cycles in current frame
                int cycles_current_frame = FRAME_CYCLES - (old_cycles & (FRAME_CYCLES - 1));

                this.snd1.updateSquareChannel(cycles_current_frame);
                this.snd2.updateSquareChannel(cycles_current_frame);
                updateWaveChannel(this.snd3, cycles_current_frame);
                updateNoiseChannel(this.snd4, cycles_current_frame);

                cycles -= cycles_current_frame;

                // Switch to next frame
                switch ((this.sndControl.cycles / FRAME_CYCLES) & 0x07) {
                case 0:
                    // length
                    this.snd1.tickLength();
                    this.snd2.tickLength();
                    this.snd3.tickLength();
                    this.snd4.tickLength();
                    break;
                case 2:
                    // sweep
                    this.snd1.tickSweep();
                    // length
                    this.snd1.tickLength();
                    this.snd2.tickLength();
                    this.snd3.tickLength();
                    this.snd4.tickLength();
                    break;
                case 4:
                    // length
                    this.snd1.tickLength();
                    this.snd2.tickLength();
                    this.snd3.tickLength();
                    this.snd4.tickLength();
                    break;
                case 6:
                    // sweep
                    this.snd1.tickSweep();
                    // length
                    this.snd1.tickLength();
                    this.snd2.tickLength();
                    this.snd3.tickLength();
                    this.snd4.tickLength();
                    break;
                case 7:
                    // update envelope
                    this.snd1.tickEnvelope();
                    this.snd2.tickEnvelope();
                    this.snd4.tickEnvelope();
                    break;
                }
            }

            this.snd1.updateSquareChannel(cycles);
            this.snd2.updateSquareChannel(cycles);
            updateWaveChannel(this.snd3, cycles);
            updateNoiseChannel(this.snd4, cycles);
        }

        public int noisePeriodCycles() {
            return divisor[this.snd4.reg[3] & 7] << (this.snd4.reg[3] >> 4);
        }

        public void setOptions(byte flags) {
            boostWaveChn = (byte) ((flags & 0x01) >> 0);
        }

        public byte readWave(int offset) {
            if (this.snd3.on) {
                if (this.gbMode == MODE_DMG)
                    return (byte) (this.snd3.sampleReading ? this.sndRegs[AUD3W0 + (this.snd3.offset / 2)] : 0xFF);
                else if (this.gbMode == MODE_CGB04)
                    return this.sndRegs[AUD3W0 + (this.snd3.offset / 2)];
            }

            return this.sndRegs[AUD3W0 + offset];
        }

        public void writeWave(int offset, byte data) {
            if (this.snd3.on) {
                if (this.gbMode == MODE_DMG) {
                    if (this.snd3.sampleReading) {
                        this.sndRegs[AUD3W0 + (this.snd3.offset / 2)] = data;
                    }
                } else if (this.gbMode == MODE_CGB04) {
                    this.sndRegs[AUD3W0 + (this.snd3.offset / 2)] = data;
                }
            } else {
                this.sndRegs[AUD3W0 + offset] = data;
            }
        }

        private static final byte[] readMask = new byte[] {
                (byte) 0x80, 0x3F, 0x00, (byte) 0xFF, (byte) 0xBF, (byte) 0xFF, 0x3F, 0x00, (byte) 0xFF, (byte) 0xBF, 0x7F, (byte) 0xFF, (byte) 0x9F, (byte) 0xFF, (byte) 0xBF, (byte) 0xFF,
                (byte) 0xFF, 0x00, 0x00, (byte) 0xBF, 0x00, 0x00, 0x70, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };

        public byte readSound(int offset) {
            if (offset < AUD3W0) {
                if (this.sndControl.on != 0) {
                    if (offset == NR52) {
                        return (byte) (
                                (this.sndRegs[NR52] & 0xf0)
                                        | (this.snd1.on ? 1 : 0)
                                        | (this.snd2.on ? 2 : 0)
                                        | (this.snd3.on ? 4 : 0)
                                        | (this.snd4.on ? 8 : 0)
                                        | 0x70);
                    }
                    return (byte) (this.sndRegs[offset] | readMask[offset & 0x3F]);
                } else {
                    return readMask[offset & 0x3F];
                }
            } else if (offset <= AUD3WF) {
                return readWave(offset - AUD3W0);
            }
            return (byte) 0xFF;
        }

        private void writeSound(int offset, byte data) {
            if (offset < AUD3W0) {
                if (this.gbMode == MODE_DMG) {
                    /* Only register NR52 is accessible if the Sound controller is disabled */
                    if (this.sndControl.on == 0 && offset != NR52 && offset != NR11 && offset != NR21 && offset != NR31 && offset != NR41)
                        return;
                } else if (this.gbMode == MODE_CGB04) {
                    /* Only register NR52 is accessible if the Sound controller is disabled */
                    if (this.sndControl.on == 0 && offset != NR52)
                        return;
                }

                this.soundWriteInternal((byte) offset, data);
            } else if (offset <= AUD3WF) {
                writeWave(offset - AUD3W0, data);
            }
        }

        private static final int[] divisor = new int[] {8, 16, 32, 48, 64, 80, 96, 112};

        public void update(int[][] outputs, int samples) {
            int sample, left, right;
            int i;
            int cycles;

            for (i = 0; i < samples; i++) {
                left = right = 0;

                this.cycleCntr.val += this.cycleCntr.inc;
                cycles = (int) (this.cycleCntr.val >> RC_SHIFT);
                this.cycleCntr.val &= ((1 << RC_SHIFT) - 1);
                this.updateState(cycles);

                /* Mode 1 - Wave with Envelope and Sweep */
                if (this.snd1.on && this.snd1.muted == 0) {
                    sample = this.snd1.signal * this.snd1.envelopeValue;

                    if (this.sndControl.mode1Left != 0)
                        left += sample;
                    if (this.sndControl.mode1Right != 0)
                        right += sample;
                }

                /* Mode 2 - Wave with Envelope */
                if (this.snd2.on && this.snd2.muted == 0) {
                    sample = this.snd2.signal * this.snd2.envelopeValue;
                    if (this.sndControl.mode2Left != 0)
                        left += sample;
                    if (this.sndControl.mode2Right != 0)
                        right += sample;
                }

                /* Mode 3 - Wave patterns from WaveRAM */
                if (this.snd3.on && this.snd3.muted == 0) {
                    sample = this.snd3.signal;
                    if (this.sndControl.mode3Left != 0)
                        left += sample;
                    if (this.sndControl.mode3Right != 0)
                        right += sample;
                }

                /* Mode 4 - Noise with Envelope */
                if (this.snd4.on && this.snd4.muted == 0) {
                    sample = this.snd4.signal * this.snd4.envelopeValue;
                    if (this.sndControl.mode4Left != 0)
                        left += sample;
                    if (this.sndControl.mode4Right != 0)
                        right += sample;
                }

                /* Adjust for master volume */
                left *= this.sndControl.volLeft;
                right *= this.sndControl.volRight;

                /* pump up the volume */
                left <<= 6;
                right <<= 6;

                /* Update the buffers */
                outputs[0][i] = left;
                outputs[1][i] = right;
            }

            this.sndRegs[NR52] = (byte) ((this.sndRegs[NR52] & 0xf0)
                    | (this.snd1.on ? 1 : 0)
                    | ((this.snd2.on ? 1 : 0) << 1)
                    | ((this.snd3.on ? 1 : 0) << 2)
                    | ((this.snd4.on ? 1 : 0) << 3));
        }

        public int start(int clock) {
            this.cycleCntr = new Ratio();
            this.snd1 = new Sound();
            this.snd2 = new Sound();
            this.snd3 = new Sound();
            this.snd4 = new Sound();
            this.sndControl = new SoundC();
            //memset(Gb, 0x00, sizeof(GbSound));

            this.rate = (clock & 0x7FFFFFFF) / 64;
            if (((CHIP_SAMPLING_MODE & 0x01) != 0 && this.rate < CHIP_SAMPLE_RATE) ||
                    CHIP_SAMPLING_MODE == 0x02)
                this.rate = CHIP_SAMPLE_RATE;

            this.gbMode = (byte) ((clock & 0x80000000) != 0 ? MODE_CGB04 : MODE_DMG);
            this.cycleCntr.setRatio(clock & 0x7FFFFFFF, this.rate);

            setMuteMask(0x00);
            //this.BoostWaveChn = 0x00;

            return this.rate;
        }

        public void reset() {
            int muteMask;

            muteMask = getMuteMask();

            this.cycleCntr.val = 0;

            this.snd1 = new Sound();
            this.snd2 = new Sound();
            this.snd3 = new Sound();
            this.snd4 = new Sound();
            //memset(&this.snd_1, 0, sizeof(this.snd_1));
            //memset(&this.snd_2, 0, sizeof(this.snd_2));
            //memset(&this.snd_3, 0, sizeof(this.snd_3));
            //memset(&this.snd_4, 0, sizeof(this.snd_4));

            setMuteMask(muteMask);

            this.snd1.channel = 1;
            this.snd1.lengthMask = 0x3F;
            this.snd2.channel = 2;
            this.snd2.lengthMask = 0x3F;
            this.snd3.channel = 3;
            this.snd3.lengthMask = (byte) 0xFF;
            this.snd4.channel = 4;
            this.snd4.lengthMask = 0x3F;

            this.soundWriteInternal((byte) NR52, (byte) 0x00);
            switch (this.gbMode) {
            case MODE_DMG:
                this.sndRegs[AUD3W0] = (byte) 0xac;
                this.sndRegs[AUD3W1] = (byte) 0xdd;
                this.sndRegs[AUD3W2] = (byte) 0xda;
                this.sndRegs[AUD3W3] = 0x48;
                this.sndRegs[AUD3W4] = 0x36;
                this.sndRegs[AUD3W5] = 0x02;
                this.sndRegs[AUD3W6] = (byte) 0xcf;
                this.sndRegs[AUD3W7] = 0x16;
                this.sndRegs[AUD3W8] = 0x2c;
                this.sndRegs[AUD3W9] = 0x04;
                this.sndRegs[AUD3WA] = (byte) 0xe5;
                this.sndRegs[AUD3WB] = 0x2c;
                this.sndRegs[AUD3WC] = (byte) 0xac;
                this.sndRegs[AUD3WD] = (byte) 0xdd;
                this.sndRegs[AUD3WE] = (byte) 0xda;
                this.sndRegs[AUD3WF] = 0x48;
                break;
            case MODE_CGB04:
                this.sndRegs[AUD3W0] = 0x00;
                this.sndRegs[AUD3W1] = (byte) 0xFF;
                this.sndRegs[AUD3W2] = 0x00;
                this.sndRegs[AUD3W3] = (byte) 0xFF;
                this.sndRegs[AUD3W4] = 0x00;
                this.sndRegs[AUD3W5] = (byte) 0xFF;
                this.sndRegs[AUD3W6] = 0x00;
                this.sndRegs[AUD3W7] = (byte) 0xFF;
                this.sndRegs[AUD3W8] = 0x00;
                this.sndRegs[AUD3W9] = (byte) 0xFF;
                this.sndRegs[AUD3WA] = 0x00;
                this.sndRegs[AUD3WB] = (byte) 0xFF;
                this.sndRegs[AUD3WC] = 0x00;
                this.sndRegs[AUD3WD] = (byte) 0xFF;
                this.sndRegs[AUD3WE] = 0x00;
                this.sndRegs[AUD3WF] = (byte) 0xFF;
                break;
            }
        }

        public void setMuteMask(int muteMask) {
            if (this.snd1 != null) this.snd1.muted = (byte) ((muteMask >> 0) & 0x01);
            if (this.snd2 != null) this.snd2.muted = (byte) ((muteMask >> 1) & 0x01);
            if (this.snd3 != null) this.snd3.muted = (byte) ((muteMask >> 2) & 0x01);
            if (this.snd4 != null) this.snd4.muted = (byte) ((muteMask >> 3) & 0x01);
        }

        public int getMuteMask() {
            if (this.snd1 == null) return 0;
            if (this.snd2 == null) return 0;
            if (this.snd3 == null) return 0;
            if (this.snd4 == null) return 0;

            int muteMask = (this.snd1.muted << 0) |
                    (this.snd2.muted << 1) |
                    (this.snd3.muted << 2) |
                    (this.snd4.muted << 3);

            return muteMask;
        }
    }

    private static final int MAX_CHIPS = 0x02;
    private GbSound[] gbSoundData = new GbSound[] {new GbSound(), new GbSound()};

    public byte readWave(byte chipID, int offset) {
        GbSound gb = gbSoundData[chipID];
        return gb.readWave(offset);
    }

    public void writeWave(byte chipID, int offset, byte data) {
        GbSound gb = gbSoundData[chipID];
        gb.writeWave(offset, data);
    }

    public byte readSound(byte chipID, int offset) {
        GbSound gb = gbSoundData[chipID];
        return gb.readSound(offset);
    }

    private void writeSound(byte chipID, int offset, byte data) {
        GbSound gb = gbSoundData[chipID];
        gb.writeSound(offset, data);
    }

    @Override
    public String getName() {
        return "Gameboy DMG";
    }

    @Override
    public String getShortName() {
        return "DMG";
    }

    public void updateDevice(byte chipID, int[][] outputs, int samples) {
        GbSound gb = gbSoundData[chipID];
        gb.update(outputs, samples);
    }

    public int startDevice(byte chipID, int clock) {
        if (chipID >= MAX_CHIPS)
            return 0;

        GbSound gb = gbSoundData[chipID];
        return gb.start(clock);
    }

    public void stopDevice(byte chipID) {
    }

    public void resetDevice(byte chipID) {
        GbSound gb = gbSoundData[chipID];
        gb.reset();
    }

    public void setMuteMask(byte chipID, int muteMask) {
        GbSound gb = gbSoundData[chipID];
        gb.setMuteMask(muteMask);
    }

    public int getMuteMask(byte chipID) {
        GbSound gb = gbSoundData[chipID];
        return gb.getMuteMask();
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        writeSound(chipID, adr, (byte) data);
        return 0;
    }

    public GbSound getSoundData(byte chipId) {
        return gbSoundData[chipId];
    }
}
