/*
 * license:BSD-3-Clause
 *
 * copyright-holders:R. Belmont, superctr
 */

package mdsound.chips;

import static mdsound.chips.C352.Flag.BUSY;
import static mdsound.chips.C352.Flag.FILTER;
import static mdsound.chips.C352.Flag.KEYOFF;
import static mdsound.chips.C352.Flag.KEYON;
import static mdsound.chips.C352.Flag.LDIR;
import static mdsound.chips.C352.Flag.LINK;
import static mdsound.chips.C352.Flag.LOOP;
import static mdsound.chips.C352.Flag.LOOPHIST;
import static mdsound.chips.C352.Flag.MULAW;
import static mdsound.chips.C352.Flag.PHASEFL;
import static mdsound.chips.C352.Flag.PHASEFR;
import static mdsound.chips.C352.Flag.PHASERL;
import static mdsound.chips.C352.Flag.REVERSE;


/**
 * C352.c - Namco C352 custom PCM chips emulation
 * v2.0
 * By R. Belmont
 * Rewritten and improved by superctr
 * Additional code by cync and the hoot development team
 *
 * Thanks to Cap of VivaNonno for info and The_Author for preliminary reverse-engineering
 *
 * Chip specs:
 * 32 voices
 * Supports 8-bit linear and 8-bit muLaw samples
 * Output: digital, 16 bit, 4 channels
 * Output sample rate is the input clock / (288 * 2).
 */
public class C352 {

    private static final short[] mulawTable = new short[256];
    private final int[] out = new int[4];

    static {
        int j = 0;
        for (int i = 0; i < 128; i++) {
            mulawTable[i] = (short) (j << 5);
            if (i < 16)
                j += 1;
            else if (i < 24)
                j += 2;
            else if (i < 48)
                j += 4;
            else if (i < 100)
                j += 8;
            else
                j += 16;
        }
        for (int i = 128; i < 256; i++)
            mulawTable[i] = (short) ((~mulawTable[i - 128]) & 0xffe0);
    }

    private final int[] flags = new int[32];

    private static final int VOICES = 32;

    enum Flag {
        /** channel is busy */
        BUSY(0x8000),
        /** Keyon */
        KEYON(0x4000),
        /** Keyoff */
        KEYOFF(0x2000),
        /** Loop Trigger */
        LOOPTRG(0x1000),
        /** Loop History */
        LOOPHIST(0x0800),
        /** Frequency Modulation */
        FM(0x0400),
        /** Rear Left invert phase 180 degrees */
        PHASERL(0x0200),
        /** Front Left invert phase 180 degrees */
        PHASEFL(0x0100),
        /** invert phase 180 degrees (e.g. flip sign of sample) */
        PHASEFR(0x0080),
        /** loop direction */
        LDIR(0x0040),
        /** "long-format" sample (can't loop, not sure what else it means) */
        LINK(0x0020),
        /** play noise instead of sample */
        NOISE(0x0010),
        /** sample is mulaw instead of linear 8-bit PCM */
        MULAW(0x0008),
        /** don't apply filter */
        FILTER(0x0004),
        /** loop backwards */
        REVLOOP(0x0003),
        /** loop forward */
        LOOP(0x0002),
        /** play sample backwards */
        REVERSE(0x0001);
        final int v;
        boolean isOn(int f) { return (f & v) != 0; }
        Flag(int v) {
            this.v = v;
        }
    }

    private static class Voice {

        private int pos = 0;
        private int counter = 0;

        private short sample = 0;
        private short lastSample = 0;

        private int volF = 0;
        private int volR = 0;
        private final int[] currVol = new int[] {0, 0, 0, 0};
        private int freq = 0;
        private int flags = 0;

        private int waveBank = 0;
        private int waveStart = 0;
        private int waveEnd = 0;
        private int waveLoop = 0;

        private int mute = 0;

        private void ramp(int ch, int val) {
            if ((this.flags & Flag.FILTER.v) != 0) {
                this.currVol[ch] = val;
                return;
            }

            short volDelta = (short) (this.currVol[ch] - val);
            if (volDelta != 0)
                this.currVol[ch] = this.currVol[ch] + ((volDelta > 0) ? -1 : 1);
//logger.log(Level.TRACE, "this.curr_vol[ch%d] = %d val=%d".formatted(ch, this.curr_vol[ch], val));
        }

        private void fetchSample(int s) {
            this.sample = (short) (s << 8);
            if (MULAW.isOn(this.flags)) {

                this.sample = mulawTable[s & 0xff];
            }

            int pos = this.pos & 0xffff;

            if (LOOP.isOn(this.flags) && REVERSE.isOn(this.flags)) {
                // backwards > forwards
                if (LDIR.isOn(this.flags) && pos == this.waveLoop)
                    this.flags &= 0xffbf;
                    // forwards > backwards
                else if (!LDIR.isOn(this.flags) && pos == this.waveEnd)
                    this.flags |= LDIR.v;

                this.pos = this.pos + (LDIR.isOn(this.flags) ? -1 : 1);
            } else if (pos == this.waveEnd) {
                if (LINK.isOn(this.flags) && LOOP.isOn(this.flags)) {
                    this.pos = (this.waveStart << 16) | this.waveLoop;
                    this.flags |= LOOPHIST.v;
                } else if (LOOP.isOn(this.flags)) {
                    this.pos = (this.pos & 0xff0000) | this.waveLoop;
                    this.flags |= LOOPHIST.v;
                } else {
                    this.flags |= KEYOFF.v;
                    this.flags &= 0x7fff;
                    this.sample = 0;
                }
            } else {
                this.pos = this.pos + (REVERSE.isOn(this.flags) ? -1 : 1);
            }
        }

        private void setRegisters(int address, int val) {
            switch (address % 8) {
            case 0:
                this.volF = val;
                break;
            case 1:
                this.volR = val;
                break;
            case 2:
                this.freq = val;
//logger.log(Level.TRACE, "this.v[ch%d].freq = %d".formatted(ch, val));
                break;
            case 3:
                this.flags = val;
                break;
            case 4:
                this.waveBank = val;
                break;
            case 5:
                this.waveStart = val;
                break;
            case 6:
                this.waveEnd = val;
                break;
            case 7:
                this.waveLoop = val;
                break;
            }
        }

        private void keyOnOff() {
            if (KEYON.isOn(this.flags)) {
                this.pos = (this.waveBank << 16) | this.waveStart;

                this.sample = 0;
                this.lastSample = 0;
                this.counter = 0xffff;

                this.flags |= BUSY.v;
                this.flags &= 0xb7ff;

                this.currVol[0] = this.currVol[1] = 0;
                this.currVol[2] = this.currVol[3] = 0;
            } else if (KEYOFF.isOn(this.flags)) {
                this.flags &= 0x5fff;
                this.counter = 0xffff;
            }
        }

        private int read(int address) {
            return switch (address % 8) {
                case 0 -> this.volF;
                case 1 -> this.volR;
                case 2 -> this.freq;
                case 3 -> this.flags;
                case 4 -> this.waveBank;
                case 5 -> this.waveStart;
                case 6 -> this.waveEnd;
                case 7 -> this.waveLoop;
                default -> 0;
            };
        }
    }

    private int sampleRateBase;
    private int divider;

    private Voice[] voices = new Voice[VOICES];

    private int random;
    // control flags, purpose unknown.
    private int control;

    private byte[] wave;
    private int waveSize;
    private int waveMask;

    // flag from VGM header
    private int muteRear; // ugly

    private static int muteAllRear = 0x00;

    private void fetchSample(Voice v) {
//logger.log(Level.TRACE, "v.sample = %d  v.pos = %d  this.wave_mask = %d  v.flags =%d ".formatted(v.sample, v.pos, this.wave_mask, v.flags));

        v.lastSample = v.sample;

        if ((v.flags & 0x0010) != 0) {
            this.random = (this.random >> 1) ^ ((-(this.random & 1)) & 0xfff6);
            v.sample = (short) this.random;
        } else {
            int s = v.pos < this.wave.length ? this.wave[v.pos & this.waveMask] : 0;

            v.fetchSample(s);
        }
    }

    public void update(int[][] outputs, int samples) {
//        int[] out = new int[4];

        for (int i = 0; i < samples; i++) {
            outputs[0][i] = 0;
            outputs[1][i] = 0;
        }

        for (int i = 0; i < samples; i++) {
            out[0] = out[1] = out[2] = out[3] = 0;

            for (int j = 0; j < VOICES; j++) {

                Voice v = this.voices[j];
                short s = 0;
                flags[j] = v.flags;

//logger.log(Level.TRACE, " v.flags=%d".formatted(v.flags));
                if (BUSY.isOn(v.flags)) {
                    int nextCounter = v.counter + v.freq;

                    if ((nextCounter & 0x10000) != 0) {
                        fetchSample(v);
//logger.log(Level.TRACE, "fetch");
//logger.log(Level.TRACE, " ch=%d 0=%d  1=%d  2=%d  3=%d".formatted(j, _out[0], _out[1], _out[2], _out[3]));
                    }

                    if (((nextCounter ^ v.counter) & 0x18000) != 0) {
                        v.ramp(0, (v.volF & 0xff00) >> 8);
                        v.ramp(1, v.volF & 0xff);
                        v.ramp(2, (v.volR & 0xff00) >> 8);
                        v.ramp(3, v.volR & 0xff);
                    }

                    v.counter = nextCounter & 0xffff;
//logger.log(Level.TRACE, " v.freq=%d".formatted(v.freq));
//logger.log(Level.TRACE, " v.counter=%d".formatted(v.counter));

                    s = v.sample;

                    // Interpolate samples
                    if (!FILTER.isOn(v.flags))
                        s = (short) (v.lastSample + (v.counter * (v.sample - v.lastSample) >> 16));
                }

                if (this.voices[j].mute == 0) {
                    // Left
                    out[0] += (((PHASEFL.isOn(v.flags) ? -s : s) * v.currVol[0]) >> 9);
                    out[2] += (((PHASEFR.isOn(v.flags) ? -s : s) * v.currVol[2]) >> 9);
                    // Right
                    out[1] += (((PHASERL.isOn(v.flags) ? -s : s) * v.currVol[1]) >> 9);
                    out[3] += (((PHASERL.isOn(v.flags) ? -s : s) * v.currVol[3]) >> 9);
                }

//logger.log(Level.TRACE, "out [0]=%d  [1]=%d  [2]=%d  [3]=%d".formatted(_out[0] , _out[1] , _out[2] , _out[3]));
            }

            outputs[0][i] += out[0];
            outputs[1][i] += out[1];
            if (this.muteRear == 0 && muteAllRear == 0) {
                outputs[0][i] += out[2];
                outputs[1][i] += out[3];
            }
//            outputs[0][i] = Math.max(Math.min(outputs[0][i], Short.MAX_VALUE), Short.MIN_VALUE);
//            outputs[1][i] = Math.max(Math.min(outputs[1][i], Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public int start(int clock, int clkdiv) {
        this.wave = null;
        this.waveSize = 0x00;

        this.divider = clkdiv != 0 ? clkdiv : 288;
        this.sampleRateBase = (clock & 0x7fff_ffff) / this.divider;
        this.muteRear = (clock & 0x8000_0000) >> 31;

        this.voices = new Voice[VOICES];
        for (int i = 0; i < VOICES; i++) {
            this.voices[i] = new Voice();
        }

        setMuteMask(0x0000_0000);

        return this.sampleRateBase;
    }

    public void stop() {
        this.wave = null;
    }

    public void reset() {
        int muteMask = getMuteMask();

        // clear all channels states
        this.voices = new Voice[VOICES];
        for (int i = 0; i < VOICES; i++) {
            this.voices[i] = new Voice();
        }

        // init noise generator
        this.random = 0x1234;
        this.control = 0;

        setMuteMask(muteMask);
    }

    public int read(int address) {
        if (address < 0x100) {
            int ch = address / 8;
            return this.voices[ch].read(address);
        } else if (address == 0x200)
            return this.control;
        else
            return 0;
    }

    public void write(int address, int val) {
//logger.log(Level.TRACE, "address = %d  val = %d".formatted(address, val));

        if (address < 0x100) { // Channel registers, see map above.
            int ch = address / 8;
            this.voices[ch].setRegisters(address, val);
        } else if (address == 0x200) {
            this.control = val;
//logger.log(Level.TRACE, "C352 control register write: %04x".formatted(val));
        } else if (address == 0x202) { // execute keyons/keyoffs
            for (int i = 0; i < VOICES; i++) {
                this.voices[i].keyOnOff();
            }
        }
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
        writeRom(romSize, dataStart, dataLength, romData, 0);
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
//logger.log(Level.TRACE, "romSize=%x , dataStart=%x , dataLength=%x".formatted(romSize, dataStart, dataLength));
        if (this.waveSize != romSize) {
            this.wave = new byte[romSize];
            this.waveSize = romSize;
            for (this.waveMask = 1; this.waveMask < this.waveSize; this.waveMask <<= 1)
                ;
            this.waveMask--;
            for (int i = 0; i < romSize; i++) {
                this.wave[i] = (byte) 0xff;
            }
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        System.arraycopy(romData, srcStartAdr, this.wave, dataStart, dataLength);
    }

    public void setMuteMask(int muteMask) {
        for (int curChn = 0; curChn < VOICES; curChn++)
            this.voices[curChn].mute = (muteMask >> curChn) & 0x01;
    }

    public int getMuteMask() {
        int muteMask = 0x0000_0000;
        for (int curChn = 0; curChn < VOICES; curChn++)
            muteMask |= (this.voices[curChn].mute << curChn);

        return muteMask;
    }

    public static void setOptions(int flags) { // ugly
        muteAllRear = (flags & 0x01) >> 0;
    }

    public int[] getFlags() {
        return flags;
    }
}
