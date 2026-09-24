/*
 * license:BSD-3-Clause
 *
 * copyright-holders: Bryan McPhail
 */

package mdsound.chips;

import java.util.Arrays;


/**
 * Konami 005289, the wavetable sound of the Bubble System and Nemesis (Gradius) boards.
 * <p>
 * It is not an SCC (K051649) variant, it came before it. The chip itself is only an address
 * generator for two channels: each counts down a 12 bit period and steps through 32 addresses of
 * a 4 bit wide prom. The board supplies the rest, an AY-3-8910's I/O ports pick one of eight
 * waveforms per channel (bit 7-5) and its volume (bit 3-0), and resistors and 4066 switches do the
 * conversion and the mixing. They are implemented here for convenience, as MAME and libvgm do.
 * <p>
 * Registers, as libvgm's vgm command {@code 0x42} addresses them:
 * <pre>
 * 0/1 control A/B: bit 7-5 waveform, bit 3-0 volume
 * 2/3 LD1/LD2: latch the 12 bit value as the channel's pitch (the period is 0xfff minus it)
 * 4/5 TG1/TG2: move the latched pitch into the channel's counter
 * </pre>
 * The prom, 0x100 bytes per channel, is loaded as RAM (vgm data block {@code 0xc3}).
 * <p>
 * Ported from libvgm's emu/cores/k005289.c (MAME's, improved by Mao and cam900). libvgm runs it
 * at the clock itself, here it runs at a 32nd of it, each sample the mean of its 32 clocks.
 *
 * @author Bryan McPhail (MAME)
 */
public class K005289 {

    public static final int CHANNELS = 2;

    public static final int PROM_SIZE = 0x200;

    /** clocks per output sample */
    private static final int CLOCK_DIVIDER = 32;

    public static class Voice {
        /** the latched pitch */
        private int pitch;
        /** the period in clocks less one of a wave step */
        private int freq;
        private int volume;
        private int waveform;
        private int counter;
        private int addr;

        /** the period in clocks less one of each of the wave's 32 steps */
        public int getFreq() {
            return freq;
        }

        public int getVolume() {
            return volume;
        }

        public int getWaveform() {
            return waveform;
        }
    }

    private final Voice[] voices = {new Voice(), new Voice()};

    private final byte[] prom = new byte[PROM_SIZE];

    private int clock;

    private int muteMask;

    /** @return the sampling rate */
    public int start(int clock) {
        this.clock = clock;
        Arrays.fill(prom, (byte) 0xff);
        muteMask = 0;
        reset();
        return getRate();
    }

    public void reset() {
        for (Voice v : voices) {
            v.pitch = 0;
            v.freq = 0;
            v.volume = 0;
            v.waveform = 0;
            v.counter = 0;
            v.addr = 0;
        }
    }

    public int getClock() {
        return clock;
    }

    public int getRate() {
        return clock / CLOCK_DIVIDER;
    }

    public void update(int[][] outputs, int samples) {
        for (int i = 0; i < samples; i++) {
            int mix = 0;
            for (int ch = 0; ch < CHANNELS; ch++) {
                Voice v = voices[ch];
                int base = (ch << 8) | (v.waveform << 5);
                int sum = 0;
                for (int t = 0; t < CLOCK_DIVIDER; t++) {
                    if (--v.counter < 0) {
                        v.addr = (v.addr + 1) & 0x1f;
                        v.counter = v.freq;
                    }
                    sum += (prom[base | v.addr] & 0x0f) - 8;
                }
                if ((muteMask & (1 << ch)) == 0)
                    mix += sum * v.volume;
            }
            // libvgm scales a clock by 16, this is the sum of 32 of them
            int out = mix / 2;
            outputs[0][i] = out;
            outputs[1][i] = out;
        }
    }

    /**
     * @param address 0/1 control A/B, 2/3 LD1/LD2, 4/5 TG1/TG2
     * @param data 12 bits
     */
    public void write(int address, int data) {
        Voice v = voices[address & 1];
        switch (address) {
            case 0, 1 -> {
                v.volume = data & 0x0f;
                v.waveform = (data >> 5) & 0x07;
            }
            case 2, 3 -> v.pitch = 0xfff - (data & 0x0fff);
            case 4, 5 -> v.freq = v.pitch;
        }
    }

    public void writeProm(int offset, byte[] data, int dataOffset, int length) {
        if (offset < 0 || offset >= PROM_SIZE)
            return;
        if (offset + length > PROM_SIZE)
            length = PROM_SIZE - offset;
        System.arraycopy(data, dataOffset, prom, offset, length);
    }

    /** @return the 32 steps of the wave a channel plays now, -8..7 */
    public int[] getWave(int ch) {
        int base = (ch << 8) | (voices[ch].waveform << 5);
        int[] wave = new int[32];
        for (int i = 0; i < 32; i++)
            wave[i] = (prom[base | i] & 0x0f) - 8;
        return wave;
    }

    public void setMuteMask(int muteMask) {
        this.muteMask = muteMask & 0x03;
    }

    public Voice getVoice(int ch) {
        return voices[ch];
    }
}
