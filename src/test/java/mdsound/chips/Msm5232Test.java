/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.chips;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Msm5232Test.
 */
class Msm5232Test {

    /** the clock the datasheet tunes to: pitch 0x21 on 8' is 440 Hz */
    static final int CLOCK = 2119040;

    /** renders {@code seconds} of voice 0 keyed on at {@code pitch} with only group 1's {@code footage} enabled */
    static int[] render(int pitch, int footage, double seconds) {
        Msm5232 chip = new Msm5232();
        int rate = chip.start(CLOCK, null);
        chip.write(0x08, 0); // fastest attack
        chip.write(0x0c, 0x10 | footage); // ARM: hold while the key is down
        chip.write(0x00, 0x80 | pitch);
        int n = (int) (rate * seconds);
        int[][] out = new int[2][n];
        chip.update(out, n);
        return out[0];
    }

    /** rising zero crossings per second over the second half, where the attack is over */
    static double frequency(int[] wave, int rate) {
        int crossings = 0;
        int from = wave.length / 2;
        for (int i = from + 1; i < wave.length; i++)
            if (wave[i - 1] < 0 && wave[i] >= 0) crossings++;
        return crossings * (double) rate / (wave.length - from);
    }

    @Test
    void testRate() {
        assertEquals(CLOCK / 16, new Msm5232().start(CLOCK, null));
    }

    @Test
    void testA440() {
        int rate = CLOCK / 16;
        double f8 = frequency(render(0x21, 0x02, 1.0), rate);
        assertEquals(440, f8, 440 * 0.01, "8'");
        double f16 = frequency(render(0x21, 0x01, 1.0), rate);
        assertEquals(220, f16, 220 * 0.01, "16'");
    }

    @Test
    void testSilentUntilEnabled() {
        int[] wave = render(0x21, 0x00, 0.2);
        for (int s : wave) assertEquals(0, s);
    }

    @Test
    void testReleaseDecays() {
        Msm5232 chip = new Msm5232();
        int rate = chip.start(CLOCK, null);
        chip.write(0x0c, 0x02);
        chip.write(0x00, 0x80 | 0x21);
        int[][] out = new int[2][rate];
        chip.update(out, rate / 10);
        assertTrue(chip.getVoice(0).getEgVolume() > 0);
        chip.write(0x00, 0x21); // key off
        chip.update(out, rate);
        assertEquals(0, chip.getVoice(0).getEgVolume());
        assertEquals(-1, chip.getVoice(0).getEgSection());
    }
}
