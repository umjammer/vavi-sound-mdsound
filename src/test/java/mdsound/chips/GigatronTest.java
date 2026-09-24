/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.chips;

import mdsound.instrument.GigatronInst;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * GigatronTest.
 */
class GigatronTest {

    /** scanlines per second, what mml2vgm writes as the clock */
    static final int CLOCK = (int) (521.0 * 59.98);

    /** o5 a in mml2vgm's FNUM_Gigatron.txt, taken from ROMv1's notesTable */
    static final int A880 = 0x0e6b;

    /** renders 1 s of channel 1 playing {@code key} as a pulse, the way mml2vgm writes it */
    static int[] render(int sampleRate, int key) {
        GigatronInst inst = new GigatronInst();
        inst.start(0, sampleRate, CLOCK);
        inst.write(0, 0, 0x01fb, 2); // wavX: pulse
        inst.write(0, 0, 0x01fc, key & 0x7f);
        inst.write(0, 0, 0x01fd, key >> 7);
        int[][] out = new int[2][sampleRate];
        inst.update(0, out, sampleRate);
        return out[0];
    }

    /** rising crossings of the mean per second */
    static double frequency(int[] wave, int rate) {
        double mean = 0;
        for (int s : wave) mean += s;
        mean /= wave.length;
        int crossings = 0;
        for (int i = 1; i < wave.length; i++)
            if (wave[i - 1] < mean && wave[i] >= mean) crossings++;
        return crossings * (double) rate / wave.length;
    }

    @Test
    void testA880() {
        for (int rate : new int[] {44100, 48000, 22050})
            assertEquals(880, frequency(render(rate, A880), rate), 880 * 0.01, "at " + rate);
    }

    @Test
    void testWriteReachesChip() {
        int[] wave = render(44100, A880);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int s : wave) { min = Math.min(min, s); max = Math.max(max, s); }
        assertTrue(max - min > 0x1000, "silent: " + min + ".." + max);
        assertTrue(min >= Short.MIN_VALUE && max <= Short.MAX_VALUE, "clips: " + min + ".." + max);
    }

    @Test
    void testHighKeyDoesNotThrow() {
        // wavX with bit 7 set indexes the table past 0x7f, which a signed byte index broke
        GigatronInst inst = new GigatronInst();
        inst.start(0, 44100, CLOCK);
        inst.write(0, 0, 0x01fb, 0x83);
        inst.write(0, 0, 0x01fa, 0xc0);
        inst.write(0, 0, 0x01fd, 0x7f);
        inst.update(0, new int[2][44100], 44100);
    }

    @Test
    void testMask() {
        GigatronInst inst = new GigatronInst();
        inst.start(0, 44100, CLOCK);
        inst.write(0, 0, 0x01fb, 2);
        inst.write(0, 0, 0x01fc, A880 & 0x7f);
        inst.write(0, 0, 0x01fd, A880 >> 7);
        inst.setMask(0, 0);
        int[][] out = new int[2][4410];
        inst.update(0, out, 4410);
        int first = out[0][4409];
        for (int i = 10; i < out[0].length; i++) assertEquals(first, out[0][i], "muted channel still sounds"); // [0..] is before the first tick
        inst.resetMask(0, 0);
        inst.update(0, out, 4410);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int s : out[0]) { min = Math.min(min, s); max = Math.max(max, s); }
        assertTrue(max - min > 0x1000, "unmuted channel silent");
    }
}
