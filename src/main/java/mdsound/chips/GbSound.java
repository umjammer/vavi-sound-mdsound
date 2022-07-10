// license:BSD-3-Clause
// copyright-holders:Wilbert Pol, Anthony Kruize
// thanks-to:Shay Green

package mdsound.chips;


/**
 * Game Boy Sound emulation (c) Anthony Kruize (trandor@labyrinth.net.au)
 * <p>
 * Anyways, Sound on the Game Boy consists of 4 separate 'channels'
 * Sound1 = Quadrangular waves with SWEEP and ENVELOPE functions  (NR10,11,12,13,14)
 * Sound2 = Quadrangular waves with ENVELOPE functions (NR21,22,23,24)
 * Sound3 = Wave patterns from WaveRAM (NR30,31,32,33,34)
 * Sound4 = White noise with an envelope (NR41,42,43,44)
 * <p>
 * Each Sound channel has 2 modes, namely ON and OFF...  whoa
 * <p>
 * These tend to be the two most important equations in
 * converting between Hertz and GB frequency registers:
 * (Sounds will have a 2.4% higher frequency on Super GB.)
 * Dmg = 2048 - (131072 / Hz)
 * Hz = 131072 / (2048 - Dmg)
 * <p>
 * Changes:
 * <p>
 * 10/2/2002       AK - Preliminary Sound code.
 * 13/2/2002       AK - Added a hack for mode 4, other fixes.
 * 23/2/2002       AK - Use lookup tables, added sweep to mode 1. Re-wrote the square
 *                      wave generation.
 * 13/3/2002       AK - Added mode 3, better lookup tables, other adjustments.
 * 15/3/2002       AK - Mode 4 can now change frequencies.
 * 31/3/2002       AK - Accidently forgot to handle counter/consecutive for mode 1.
 *  3/4/2002       AK - Mode 1 sweep can still occur if shift is 0.  Don't let frequency
 *                      go past the maximum allowed value. Fixed Mode 3 length table.
 *                      Slight adjustment to Mode 4's period table generation.
 *  5/4/2002       AK - Mode 4 is done correctly, using a polynomial counter instead
 *                      of being a total hack.
 *  6/4/2002       AK - Slight tweak to mode 3's frequency calculation.
 * 13/4/2002       AK - Reset envelope value when Sound is initialized.
 * 21/4/2002       AK - Backed out the mode 3 frequency calculation change.
 *                      Merged init functions into gameboy_sound_w().
 * 14/5/2002       AK - Removed magic numbers in the fixed point math.
 * 12/6/2002       AK - Merged SOUNDx structs into one Sound struct.
 * 26/10/2002      AK - Finally fixed channel 3!
 * xx/4-5/2016     WP - Rewrote Sound core. Most of the code is not optimized yet.
 * <p>
 * TODO:
 * - Implement different behavior of CGB-02.
 * - Implement different behavior of CGB-05.
 * - Perform more tests on real hardware to figure out when the frequency counters are
 * reloaded.
 * - Perform more tests on real hardware to understand when changes to the noise divisor
 * and shift kick in.
 * - Optimize the channel update methods.
 */
public class GbSound {

    static class Ratio {
        private static final int RC_SHIFT = 16;

        /** counter increment */
        private int inc;
        /** current value */
        private int value;

        private void setRatio(int mul, int div) {
            this.inc = (int) ((((long) mul << RC_SHIFT) + div / 2) / div);
        }

        private int update() {
            this.value += this.inc;
            int cycles = this.value >> RC_SHIFT;
            this.value &= ((1 << RC_SHIFT) - 1);
            return cycles;
        }
    }

    // CONSTANTS

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

    /** Represents wave duties of 12.5%, 25%, 50% and 75% */
    private static final int[][] waveDutyTable = new int[][] {
            new int[] {-1, -1, -1, -1, -1, -1, -1, 1},
            new int[] {1, -1, -1, -1, -1, -1, -1, 1},
            new int[] {1, -1, -1, -1, -1, 1, 1, 1},
            new int[] {-1, 1, 1, 1, 1, 1, 1, -1}
    };

    // TYPE DEFINITIONS

    public static class Sound {

        private static final int[] divisor = new int[] {8, 16, 32, 48, 64, 80, 96, 112};

        // Common
        public byte[] registers = new byte[5];
        private boolean on;
        private byte channel;
        public byte length;
        private byte lengthMask;
        private boolean lengthCounting;
        public boolean lengthEnabled;
        // Mode 1, 2, 3
        private int cyclesLeft;
        public byte duty;
        // Mode 1, 2, 4
        private boolean envelopeEnabled;
        public byte envelopeValue;
        public byte envelopeDirection;
        public byte envelopeTime;
        private byte envelopeCount;
        private byte signal;
        // Mode 1
        public int frequency;
        private int frequencyCounter;
        private boolean sweepEnabled;
        private boolean sweepNegModeUsed;
        public byte sweepShift;
        public int sweepDirection;
        public byte sweepTime;
        private byte sweepCount;
        // Mode 3
        public byte level;
        private byte offset;
        private int dutyCount;
        private byte currentSample;
        private boolean sampleReading;
        // Mode 4
        private boolean noiseShort;
        private int noiseLfsr;
        private byte muted;

        private void tickLength() {
            if (this.lengthEnabled) {
                this.length = (byte) ((this.length + 1) & this.lengthMask);
                if (this.length == 0) {
                    this.on = false;
                    this.lengthCounting = false;
                }
            }
        }

        private int calculateNextSweep() {
            int newFrequency;
            this.sweepNegModeUsed = (this.sweepDirection < 0);
            newFrequency = this.frequency + this.sweepDirection * (this.frequency >> this.sweepShift);

            if (newFrequency > 0x7FF) {
                this.on = false;
            }

            return newFrequency;
        }

        private void applyNextSweep() {
            int newFrequency = calculateNextSweep();

            if (this.on && this.sweepShift > 0) {
                this.frequency = newFrequency;
                this.registers[3] = (byte) (this.frequency & 0xFF);
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
                        byte newEnvelopeValue = (byte) (this.envelopeValue + this.envelopeDirection);

                        if (newEnvelopeValue >= 0 && newEnvelopeValue <= 15) {
                            this.envelopeValue = newEnvelopeValue;
                        } else {
                            this.envelopeEnabled = false;
                        }
                    }
                }
            }
        }

        private boolean dacEnabled() {
            return ((this.channel != 3) ? (this.registers[2] & 0xF8) : (this.registers[0] & 0x80)) != 0;
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
                        this.frequencyCounter = (this.frequencyCounter + 1) & 0x7FF;
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

        private void updateWaveChannel(int cycles, byte mode, byte boostWaveCh, byte[] registers) {
            if (on) {
                // compensate for leftover cycles
                if (cyclesLeft > 0) {
                    cycles += cyclesLeft;
                    cyclesLeft = 0;
                }

                while (cycles > 0) {
                    // Emit current sample

                    // cycles -= 2
                    if (cycles < 2) {
                        cyclesLeft = cycles;
                        cycles = 0;
                    } else {
                        cycles -= 2;

                        // Calculate next state
                        frequencyCounter = (frequencyCounter + 1) & 0x7FF;
                        sampleReading = false;
                        if (mode == MODE_DMG && frequencyCounter == 0x7ff)
                            offset = (byte) ((offset + 1) & 0x1F);
                        if (frequencyCounter == 0) {
                            // Read next sample
                            sampleReading = true;
                            if (mode == MODE_CGB04)
                                offset = (byte) ((offset + 1) & 0x1F);
                            currentSample = registers[AUD3W0 + (offset / 2)];
                            if ((offset & 0x01) == 0) {
                                currentSample >>= 4;
                            }
                            currentSample = (byte) ((currentSample & 0x0F) - 8);
                            if (boostWaveCh != 0)

                                currentSample <<= 1;

                            signal = (byte) (level != 0 ? currentSample / (1 << (level - 1)) : 0);

                            // Reload frequency counter
                            frequencyCounter = frequency;
                        }
                    }
                }
            }
        }

        private void updateNoiseChannel(int cycles) {
            while (cycles > 0) {
                if (cycles < cyclesLeft) {
                    if (on) {
                        // generate samples
                    }

                    cyclesLeft -= cycles;
                    cycles = 0;
                } else {
                    if (on) {
                        // generate samples
                    }

                    cycles -= cyclesLeft;
                    cyclesLeft = noisePeriodCycles();

                    // Using a Polynomial Counter (aka Linear Feedback Shift Register)
                    // Mode 4 has a 15 bit counter so we need to shift the
                    // bits around accordingly
                    int feedback = ((noiseLfsr >> 1) ^ noiseLfsr) & 1;
                    noiseLfsr = (noiseLfsr >> 1) | (feedback << 14);
                    if (noiseShort) {
                        noiseLfsr = (noiseLfsr & ~(1 << 6)) | (feedback << 6);
                    }
                    signal = (byte) ((noiseLfsr & 1) != 0 ? -1 : 1);
                }
            }
        }

        private int noisePeriodCycles() {
            return divisor[this.registers[3] & 7] << (this.registers[3] >> 4);
        }

        private void sweep(byte data, byte oldData) {
            this.registers[0] = data;
            this.sweepShift = (byte) (data & 0x7);
            this.sweepDirection = (data & 0x8) != 0 ? -1 : 1;
            this.sweepTime = (byte) ((data & 0x70) >> 4);
            if ((oldData & 0x08) != 0 && (data & 0x08) == 0 && this.sweepNegModeUsed) {
                this.on = false;
            }
        }

        private void initializeHiFrequency(byte data, int cycles) {
            this.registers[4] = data;

            boolean lengthWasEnabled = this.lengthEnabled;

            this.lengthEnabled = (data & 0x40) != 0;
            this.frequency = ((this.registers[NR14] & 0x7) << 8) | this.registers[3];

            if (!lengthWasEnabled && (cycles & FRAME_CYCLES) == 0 && this.lengthCounting) {
                if (this.lengthEnabled) {
                    this.tickLength();
                }
            }

            if ((data & 0x80) != 0) {
                this.on = true;
                this.envelopeEnabled = true;
                this.envelopeValue = (byte) (this.registers[2] >> 4);
                this.envelopeCount = this.envelopeTime;
                this.sweepCount = this.sweepTime;
                this.sweepNegModeUsed = false;
                this.signal = 0;
                this.length = (byte) (this.registers[1] & 0x3f); // VGM log fix -Valley Bell
                this.lengthCounting = true;
                this.frequency = ((this.registers[4] & 0x7) << 8) | this.registers[3];
                this.frequencyCounter = this.frequency;
                this.cyclesLeft = 0;
                this.dutyCount = 0;
                this.sweepEnabled = (this.sweepShift != 0) || (this.sweepTime != 0);
                if (!this.dacEnabled()) {
                    this.on = false;
                }
                if (this.sweepShift > 0) {
                    this.calculateNextSweep();
                }

                if (this.length == 0 && this.lengthEnabled && (cycles & FRAME_CYCLES) == 0) {
                    this.tickLength();
                }
            } else {
                // This condition may not be correct
                if (!this.sweepEnabled) {
                    this.frequency = ((this.registers[4] & 0x7) << 8) | this.registers[3];
                }
            }
        }

        private void initializeHiFrequency2(byte data, int cycles) {
            this.registers[4] = data;

            boolean lengthWasEnabled = this.lengthEnabled;

            this.lengthEnabled = (data & 0x40) != 0;

            if (!lengthWasEnabled && (cycles & FRAME_CYCLES) == 0 && this.lengthCounting) {
                if (this.lengthEnabled) {
                    this.tickLength();
                }
            }

            if ((data & 0x80) != 0) {
                this.on = true;
                this.envelopeEnabled = true;
                this.envelopeValue = (byte) (this.registers[2] >> 4);
                this.envelopeCount = this.envelopeTime;
                this.frequency = ((this.registers[4] & 0x7) << 8) | this.registers[3];
                this.frequencyCounter = this.frequency;
                this.cyclesLeft = 0;
                this.dutyCount = 0;
                this.signal = 0;
                this.length = (byte) (this.registers[1] & 0x3f); // VGM log fix -Valley Bell
                this.lengthCounting = true;

                if (!this.dacEnabled()) {
                    this.on = false;
                }

                if (this.length == 0 && this.lengthEnabled && (cycles & FRAME_CYCLES) == 0) {
                    this.tickLength();
                }
            } else {
                this.frequency = ((this.registers[4] & 0x7) << 8) | this.registers[3];
            }
        }

        private void initializeHiFrequency3(byte data, int cycles, byte mode) {
            this.registers[4] = data;

            boolean lengthWasEnabled = this.lengthEnabled;

            this.lengthEnabled = (data & 0x40) != 0;

            if (!lengthWasEnabled && (cycles & FRAME_CYCLES) == 0 && this.lengthCounting) {
                if (this.lengthEnabled) {
                    this.tickLength();
                }
            }

            if ((data & 0x80) != 0) {
                if (this.on && this.frequencyCounter == 0x7ff) {
                    corruptWaveRam(mode);
                }
                this.on = true;
                this.offset = 0;
                this.duty = 1;
                this.dutyCount = 0;
                this.length = this.registers[1]; // VGM log fix -Valley Bell
                this.lengthCounting = true;
                this.frequency = ((this.registers[4] & 0x7) << 8) | this.registers[3];
                this.frequencyCounter = this.frequency;
                // There is a tiny bit of delay in starting up the wave channel(?)
                //
                // Results from older code where corruption of wave ram was triggered when sample_reading == true:
                // 4 breaks test 09 (read wram), fixes test 10 (write trigger), breaks test 12 (write wram)
                // 6 fixes test 09 (read wram), breaks test 10 (write trigger), fixes test 12 (write wram)
                this.cyclesLeft = 0 + 6;
                this.sampleReading = false;

                if (!this.dacEnabled()) {
                    this.on = false;
                }

                if (this.length == 0 && this.lengthEnabled && (cycles & FRAME_CYCLES) == 0) {
                    this.tickLength();
                }
            } else {
                this.frequency = ((this.registers[4] & 0x7) << 8) | this.registers[3];
            }
        }

        private void corruptWaveRam(byte mode) {
            if (mode != MODE_DMG)
                return;

            if (this.offset < 8) {
                this.registers[AUD3W0] = this.registers[AUD3W0 + (this.offset / 2)];
            } else {
                int i;
                for (i = 0; i < 4; i++) {
                    this.registers[AUD3W0 + i] = this.registers[AUD3W0 + ((this.offset / 2) & ~0x03) + i];
                }
            }
        }

        private void initializeHiFrequency4(byte data, int cycles) {
            this.registers[4] = data;

            boolean length_was_enabled = this.lengthEnabled;

            this.lengthEnabled = (data & 0x40) != 0;

            if (!length_was_enabled && (cycles & FRAME_CYCLES) == 0 && this.lengthCounting) {
                if (this.lengthEnabled) {
                    this.tickLength();
                }
            }

            if ((data & 0x80) != 0) {
                this.on = true;
                this.envelopeEnabled = true;
                this.envelopeValue = (byte) (this.registers[2] >> 4);
                this.envelopeCount = this.envelopeTime;
                this.frequencyCounter = 0;
                this.cyclesLeft = this.noisePeriodCycles();
                this.signal = -1;
                this.noiseLfsr = 0x7fff;
                this.length = (byte) (this.registers[1] & 0x3f); // VGM log fix -Valley Bell
                this.lengthCounting = true;

                if (!this.dacEnabled()) {
                    this.on = false;
                }

                if (this.length == 0 && this.lengthEnabled && (cycles & FRAME_CYCLES) == 0) {
                    this.tickLength();
                }
            }
        }

        private void envelope(byte data) {
            this.registers[2] = data;
            this.envelopeValue = (byte) (data >> 4);
            this.envelopeDirection = (byte) ((data & 0x8) != 0 ? 1 : -1);
            this.envelopeTime = (byte) (data & 0x07);
            if (!this.dacEnabled()) {
                this.on = false;
            }
        }
    }

    public static class Controller {
        private byte on;
        private byte volumeLeft;
        private byte volumeRight;
        public byte mode1Left;
        public byte mode1Right;
        public byte mode2Left;
        public byte mode2Right;
        public byte mode3Left;
        public byte mode3Right;
        public byte mode4Left;
        public byte mode4Right;
        private int cycles;
        private boolean waveRamLocked;

        private void setData(byte data) {
            this.mode1Right = (byte) (data & 0x1);
            this.mode1Left = (byte) ((data & 0x10) >> 4);
            this.mode2Right = (byte) ((data & 0x2) >> 1);
            this.mode2Left = (byte) ((data & 0x20) >> 5);
            this.mode3Right = (byte) ((data & 0x4) >> 2);
            this.mode3Left = (byte) ((data & 0x40) >> 6);
            this.mode4Right = (byte) ((data & 0x8) >> 3);
            this.mode4Left = (byte) ((data & 0x80) >> 7);
        }

        private void setVolume(byte data) {
            this.volumeLeft = (byte) (data & 0x7);
            this.volumeRight = (byte) ((data & 0x70) >> 4);
        }
    }

    private static final int MODE_DMG = 0x00;
    private static final int MODE_CGB04 = 0x01;

    private int rate;

    public Sound sound1;
    public Sound sound2;
    public Sound sound3;
    public Sound sound4;
    public Controller controller;

    public byte[] registers = new byte[0x30];

    private Ratio cycleCounter;

    private byte mode;

    private byte boostWaveCh = 0x00;

    // IMPLEMENTATION

    private void write(byte offset, byte data) {
        // Store the value
        byte oldData = this.registers[offset];

        if (this.controller.on != 0) {
            this.registers[offset] = data;
        }

        switch (offset) {
        // MODE 1
        case NR10: // Sweep (R/W)
            this.sound1.sweep(data, oldData);
            break;
        case NR11: // Sound length/Wave pattern duty (R/W)
            this.sound1.registers[1] = data;
            if (this.controller.on != 0) {
                this.sound1.duty = (byte) ((data & 0xc0) >> 6);
            }
            this.sound1.length = (byte) (data & 0x3f);
            this.sound1.lengthCounting = true;
            break;
        case NR12: // Envelope (R/W)
            this.sound1.envelope(data);
            break;
        case NR13: // Frequency lo (R/W)
            this.sound1.registers[3] = data;
            // Only enabling the frequency line breaks blarggs's Sound test // #5
            // This condition may not be correct
            if (!this.sound1.sweepEnabled) {
                this.sound1.frequency = ((this.sound1.registers[4] & 0x7) << 8) | this.sound1.registers[3];
            }
            break;
        case NR14: // Frequency hi / Initialize (R/W)
            this.sound1.initializeHiFrequency(data, this.controller.cycles);
            break;

        // MODE 2
        case NR21: // Sound length/Wave pattern duty (R/W)
            this.sound2.registers[1] = data;
            if (this.controller.on != 0) {
                this.sound2.duty = (byte) ((data & 0xc0) >> 6);
            }
            this.sound2.length = (byte) (data & 0x3f);
            this.sound2.lengthCounting = true;
            break;
        case NR22: // Envelope (R/W)
            this.sound2.registers[2] = data;
            this.sound2.envelopeValue = (byte) (data >> 4);
            this.sound2.envelopeDirection = (byte) ((data & 0x8) != 0 ? 1 : -1);
            this.sound2.envelopeTime = (byte) (data & 0x07);
            if (!this.sound2.dacEnabled()) {
                this.sound2.on = false;
            }
            break;
        case NR23: // Frequency lo (R/W)
            this.sound2.registers[3] = data;
            this.sound2.frequency = ((this.sound2.registers[4] & 0x7) << 8) | this.sound2.registers[3];
            break;
        case NR24: // Frequency hi / Initialize (R/W)
            this.sound2.initializeHiFrequency2(data, this.controller.cycles);
            break;

        // MODE 3
        case NR30: // Sound On/Off (R/W)
            this.sound3.registers[0] = data;
            if (!this.sound3.dacEnabled()) {
                this.sound3.on = false;
            }
            break;
        case NR31: // Sound Length (R/W)
            this.sound3.registers[1] = data;
            this.sound3.length = data;
            this.sound3.lengthCounting = true;
            break;
        case NR32: // Select Output Level
            this.sound3.registers[2] = data;
            this.sound3.level = (byte) ((data & 0x60) >> 5);
            break;
        case NR33: // Frequency lo (W)
            this.sound3.registers[3] = data;
            this.sound3.frequency = ((this.sound3.registers[4] & 0x7) << 8) | this.sound3.registers[3];
            break;
        case NR34: // Frequency hi / Initialize (W)
            this.sound3.initializeHiFrequency3(data, this.controller.cycles, this.mode);
            break;

        // MODE 4
        case NR41: // Sound Length (R/W)
            this.sound4.registers[1] = data;
            this.sound4.length = (byte) (data & 0x3f);
            this.sound4.lengthCounting = true;
            break;
        case NR42: // Envelope (R/W)
            this.sound4.registers[2] = data;
            this.sound4.envelopeValue = (byte) (data >> 4);
            this.sound4.envelopeDirection = (byte) ((data & 0x8) != 0 ? 1 : -1);
            this.sound4.envelopeTime = (byte) (data & 0x07);
            if (!this.sound4.dacEnabled()) {
                this.sound4.on = false;
            }
            break;
        case NR43: // Polynomial Counter/Frequency
            this.sound4.registers[3] = data;
            this.sound4.noiseShort = (data & 0x8) != 0;
            break;
        case NR44: // Counter/Consecutive / Initialize (R/W)
            this.sound4.initializeHiFrequency4(data, this.controller.cycles);
            break;

        // CONTROL
        case NR50: // Channel Controller / On/Off / Volume (R/W)
            this.controller.setVolume(data);
            break;
        case NR51: // Selection of Sound Output Terminal
            this.controller.setData(data);
            break;
        case NR52: // Sound On/Off (R/W)
            // Only bit 7 is writable, writing to bits 0-3 does NOT enable or disable Sound. They are read-only.
            if ((data & 0x80) == 0) {
                // On DMG the length counters are not affected and not clocked
                // powering off should actually clear all registers
                apuPowerOff();
            } else {
                if (this.controller.on == 0) {
                    // When switching on, the next step should be 0.
                    this.controller.cycles |= 7 * FRAME_CYCLES;
                }
            }
            this.controller.on = (byte) ((data & 0x80) != 0 ? 1 : 0); // true : false;
            this.registers[NR52] = (byte) (data & 0x80);
            break;
        }
    }

    private void apuPowerOff() {
        switch (this.mode) {
        case MODE_DMG:
            write((byte) NR10, (byte) 0x00);
            this.sound1.duty = 0;
            this.registers[NR11] = 0;
            write((byte) NR12, (byte) 0x00);
            write((byte) NR13, (byte) 0x00);
            write((byte) NR14, (byte) 0x00);
            this.sound1.lengthCounting = false;
            this.sound1.sweepNegModeUsed = false;

            this.registers[NR21] = 0;
            write((byte) NR22, (byte) 0x00);
            write((byte) NR23, (byte) 0x00);
            write((byte) NR24, (byte) 0x00);
            this.sound2.lengthCounting = false;

            write((byte) NR30, (byte) 0x00);
            write((byte) NR32, (byte) 0x00);
            write((byte) NR33, (byte) 0x00);
            write((byte) NR34, (byte) 0x00);
            this.sound3.lengthCounting = false;
            this.sound3.currentSample = 0;

            this.registers[NR41] = 0;
            write((byte) NR42, (byte) 0x00);
            write((byte) NR43, (byte) 0x00);
            write((byte) NR44, (byte) 0x00);
            this.sound4.lengthCounting = false;
            this.sound4.cyclesLeft = this.sound4.noisePeriodCycles();
            break;
        case MODE_CGB04:
            write((byte) NR10, (byte) 0x00);
            this.sound1.duty = 0;
            write((byte) NR11, (byte) 0x00);
            write((byte) NR12, (byte) 0x00);
            write((byte) NR13, (byte) 0x00);
            write((byte) NR14, (byte) 0x00);
            this.sound1.lengthCounting = false;
            this.sound1.sweepNegModeUsed = false;

            write((byte) NR21, (byte) 0x00);
            write((byte) NR22, (byte) 0x00);
            write((byte) NR23, (byte) 0x00);
            write((byte) NR24, (byte) 0x00);
            this.sound2.lengthCounting = false;

            write((byte) NR30, (byte) 0x00);
            write((byte) NR31, (byte) 0x00);
            write((byte) NR32, (byte) 0x00);
            write((byte) NR33, (byte) 0x00);
            write((byte) NR34, (byte) 0x00);
            this.sound3.lengthCounting = false;
            this.sound3.currentSample = 0;

            write((byte) NR41, (byte) 0x00);
            write((byte) NR42, (byte) 0x00);
            write((byte) NR43, (byte) 0x00);
            write((byte) NR44, (byte) 0x00);
            this.sound4.lengthCounting = false;
            this.sound4.cyclesLeft = this.sound4.noisePeriodCycles();
            break;
        }

        this.sound1.on = false;
        this.sound2.on = false;
        this.sound3.on = false;
        this.sound4.on = false;

        this.controller.waveRamLocked = false;

        for (int i = NR44 + 1; i < NR52; i++) {
            write((byte) i, (byte) 0x00);
        }
    }

    private void updateState(int cycles) {
        if (this.controller.on == 0)
            return;

        int oldCycles = this.controller.cycles;
        this.controller.cycles += cycles;

        if ((oldCycles / FRAME_CYCLES) != (this.controller.cycles / FRAME_CYCLES)) {
            // Left over cycles in current frame
            int cyclesCurrentFrame = FRAME_CYCLES - (oldCycles & (FRAME_CYCLES - 1));

            updateSounds(cyclesCurrentFrame);

            cycles -= cyclesCurrentFrame;

            // Switch to next frame
            switch ((this.controller.cycles / FRAME_CYCLES) & 0x07) {
            case 0:
                // length
                this.tickLengthSounds();
                break;
            case 2:
                // sweep
                this.sound1.tickSweep();
                // length
                this.tickLengthSounds();
                break;
            case 4:
                // length
                this.tickLengthSounds();
                break;
            case 6:
                // sweep
                this.sound1.tickSweep();
                // length
                this.tickLengthSounds();
                break;
            case 7:
                // update envelope
                this.sound1.tickEnvelope();
                this.sound2.tickEnvelope();
                this.sound4.tickEnvelope();
                break;
            }
        }

        updateSounds(cycles);
    }

    void tickLengthSounds() {
        this.sound1.tickLength();
        this.sound2.tickLength();
        this.sound3.tickLength();
        this.sound4.tickLength();
    }

    void updateSounds(int cycles) {
        this.sound1.updateSquareChannel(cycles);
        this.sound2.updateSquareChannel(cycles);
        this.sound3.updateWaveChannel(cycles, this.mode, this.boostWaveCh, this.registers);
        this.sound4.updateNoiseChannel(cycles);
    }

    public void setOptions(byte flags) {
        boostWaveCh = (byte) ((flags & 0x01) >> 0);
    }

    public byte readWave(int offset) {
        if (this.sound3.on) {
            if (this.mode == MODE_DMG)
                return (byte) (this.sound3.sampleReading ? this.registers[AUD3W0 + (this.sound3.offset / 2)] : 0xFF);
            else if (this.mode == MODE_CGB04)
                return this.registers[AUD3W0 + (this.sound3.offset / 2)];
        }

        return this.registers[AUD3W0 + offset];
    }

    public void writeWave(int offset, byte data) {
        if (this.sound3.on) {
            if (this.mode == MODE_DMG) {
                if (this.sound3.sampleReading) {
                    this.registers[AUD3W0 + (this.sound3.offset / 2)] = data;
                }
            } else if (this.mode == MODE_CGB04) {
                this.registers[AUD3W0 + (this.sound3.offset / 2)] = data;
            }
        } else {
            this.registers[AUD3W0 + offset] = data;
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
            if (this.controller.on != 0) {
                if (offset == NR52) {
                    return (byte) (
                            (this.registers[NR52] & 0xf0)
                                    | (this.sound1.on ? 1 : 0)
                                    | (this.sound2.on ? 2 : 0)
                                    | (this.sound3.on ? 4 : 0)
                                    | (this.sound4.on ? 8 : 0)
                                    | 0x70);
                }
                return (byte) (this.registers[offset] | readMask[offset & 0x3F]);
            } else {
                return readMask[offset & 0x3F];
            }
        } else if (offset <= AUD3WF) {
            return readWave(offset - AUD3W0);
        }
        return (byte) 0xFF;
    }

    public void writeSound(int offset, byte data) {
        if (offset < AUD3W0) {
            if (this.mode == MODE_DMG) {
                // Only register NR52 is accessible if the Sound controller is disabled
                if (this.controller.on == 0 && offset != NR52 && offset != NR11 && offset != NR21 && offset != NR31 && offset != NR41)
                    return;
            } else if (this.mode == MODE_CGB04) {
                // Only register NR52 is accessible if the Sound controller is disabled
                if (this.controller.on == 0 && offset != NR52)
                    return;
            }

            this.write((byte) offset, data);
        } else if (offset <= AUD3WF) {
            writeWave(offset - AUD3W0, data);
        }
    }

    public void update(int[][] outputs, int samples) {
        for (int i = 0; i < samples; i++) {
            int left = 0, right = 0;

            this.updateState(this.cycleCounter.update());

            // Mode 1 - Wave with Envelope and Sweep
            if (this.sound1.on && this.sound1.muted == 0) {
                int sample = this.sound1.signal * this.sound1.envelopeValue;

                if (this.controller.mode1Left != 0)
                    left += sample;
                if (this.controller.mode1Right != 0)
                    right += sample;
            }

            // Mode 2 - Wave with Envelope
            if (this.sound2.on && this.sound2.muted == 0) {
                int sample = this.sound2.signal * this.sound2.envelopeValue;
                if (this.controller.mode2Left != 0)
                    left += sample;
                if (this.controller.mode2Right != 0)
                    right += sample;
            }

            // Mode 3 - Wave patterns from WaveRAM
            if (this.sound3.on && this.sound3.muted == 0) {
                int sample = this.sound3.signal;
                if (this.controller.mode3Left != 0)
                    left += sample;
                if (this.controller.mode3Right != 0)
                    right += sample;
            }

            // Mode 4 - Noise with Envelope
            if (this.sound4.on && this.sound4.muted == 0) {
                int sample = this.sound4.signal * this.sound4.envelopeValue;
                if (this.controller.mode4Left != 0)
                    left += sample;
                if (this.controller.mode4Right != 0)
                    right += sample;
            }

            // Adjust for master volume
            left *= this.controller.volumeLeft;
            right *= this.controller.volumeRight;

            // pump up the volume
            left <<= 6;
            right <<= 6;

            // Update the buffers
            outputs[0][i] = left;
            outputs[1][i] = right;
        }

        this.registers[NR52] = (byte) ((this.registers[NR52] & 0xf0)
                | (this.sound1.on ? 1 : 0)
                | ((this.sound2.on ? 1 : 0) << 1)
                | ((this.sound3.on ? 1 : 0) << 2)
                | ((this.sound4.on ? 1 : 0) << 3));
    }

    public void start(int clock, int rate) {
        this.cycleCounter = new Ratio();
        this.sound1 = new Sound();
        this.sound2 = new Sound();
        this.sound3 = new Sound();
        this.sound4 = new Sound();
        this.controller = new Controller();

        this.rate = rate;

        this.mode = (byte) ((clock & 0x80000000) != 0 ? MODE_CGB04 : MODE_DMG);
        this.cycleCounter.setRatio(clock & 0x7FFFFFFF, this.rate);

        setMuteMask(0x00);
    }

    public void reset() {
        int muteMask = getMuteMask();

        this.cycleCounter.value = 0;

        this.sound1 = new Sound();
        this.sound2 = new Sound();
        this.sound3 = new Sound();
        this.sound4 = new Sound();

        setMuteMask(muteMask);

        this.sound1.channel = 1;
        this.sound1.lengthMask = 0x3F;
        this.sound2.channel = 2;
        this.sound2.lengthMask = 0x3F;
        this.sound3.channel = 3;
        this.sound3.lengthMask = (byte) 0xFF;
        this.sound4.channel = 4;
        this.sound4.lengthMask = 0x3F;

        this.write((byte) NR52, (byte) 0x00);
        switch (this.mode) {
        case MODE_DMG:
            this.registers[AUD3W0] = (byte) 0xac;
            this.registers[AUD3W1] = (byte) 0xdd;
            this.registers[AUD3W2] = (byte) 0xda;
            this.registers[AUD3W3] = 0x48;
            this.registers[AUD3W4] = 0x36;
            this.registers[AUD3W5] = 0x02;
            this.registers[AUD3W6] = (byte) 0xcf;
            this.registers[AUD3W7] = 0x16;
            this.registers[AUD3W8] = 0x2c;
            this.registers[AUD3W9] = 0x04;
            this.registers[AUD3WA] = (byte) 0xe5;
            this.registers[AUD3WB] = 0x2c;
            this.registers[AUD3WC] = (byte) 0xac;
            this.registers[AUD3WD] = (byte) 0xdd;
            this.registers[AUD3WE] = (byte) 0xda;
            this.registers[AUD3WF] = 0x48;
            break;
        case MODE_CGB04:
            this.registers[AUD3W0] = 0x00;
            this.registers[AUD3W1] = (byte) 0xFF;
            this.registers[AUD3W2] = 0x00;
            this.registers[AUD3W3] = (byte) 0xFF;
            this.registers[AUD3W4] = 0x00;
            this.registers[AUD3W5] = (byte) 0xFF;
            this.registers[AUD3W6] = 0x00;
            this.registers[AUD3W7] = (byte) 0xFF;
            this.registers[AUD3W8] = 0x00;
            this.registers[AUD3W9] = (byte) 0xFF;
            this.registers[AUD3WA] = 0x00;
            this.registers[AUD3WB] = (byte) 0xFF;
            this.registers[AUD3WC] = 0x00;
            this.registers[AUD3WD] = (byte) 0xFF;
            this.registers[AUD3WE] = 0x00;
            this.registers[AUD3WF] = (byte) 0xFF;
            break;
        }
    }

    public void setMuteMask(int muteMask) {
        if (this.sound1 != null) this.sound1.muted = (byte) ((muteMask >> 0) & 0x01);
        if (this.sound2 != null) this.sound2.muted = (byte) ((muteMask >> 1) & 0x01);
        if (this.sound3 != null) this.sound3.muted = (byte) ((muteMask >> 2) & 0x01);
        if (this.sound4 != null) this.sound4.muted = (byte) ((muteMask >> 3) & 0x01);
    }

    public int getMuteMask() {
        if (this.sound1 == null) return 0;
        if (this.sound2 == null) return 0;
        if (this.sound3 == null) return 0;
        if (this.sound4 == null) return 0;

        int muteMask = (this.sound1.muted << 0) |
                (this.sound2.muted << 1) |
                (this.sound3.muted << 2) |
                (this.sound4.muted << 3);

        return muteMask;
    }
}
