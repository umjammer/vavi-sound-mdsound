/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.chips;

import java.util.List;

import mdsound.MDSound;
import mdsound.instrument.Es5505Inst;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Es5505Test.
 */
class Es5505Test {

    /** Taito F3 */
    static final int CLOCK = 30476100 / 2;

    /** a square wave of 256 words, 16 bit little endian */
    static byte[] squareWave() {
        byte[] rom = new byte[256 * 2];
        for (int i = 0; i < 256; i++) {
            short s = (short) (i < 128 ? 0x4000 : -0x4000);
            rom[i * 2] = (byte) s;
            rom[i * 2 + 1] = (byte) (s >> 8);
        }
        return rom;
    }

    /** writes a 16 bit register as VGM does, byte by byte */
    static void writeReg(Es5505 chip, int reg, int data) {
        chip.write8(reg * 2, data >> 8);
        chip.write8(reg * 2 + 1, data & 0xff);
    }

    /** plays the whole 256 words looped on voice 0, a word per sample */
    static void keyOn(Es5505 chip) {
        writeReg(chip, 0x0f, 0x00); // PAGE: voice 0
        writeReg(chip, 0x01, 0x0400); // FC: 1.0
        writeReg(chip, 0x02, 0x0000); // STRT
        writeReg(chip, 0x03, 0x0000);
        writeReg(chip, 0x04, (255 << 9) >> 16); // END
        writeReg(chip, 0x05, (255 << 9) & 0xffff);
        writeReg(chip, 0x06, 0xfff0); // K2: filters open
        writeReg(chip, 0x07, 0xfff0); // K1
        writeReg(chip, 0x08, 0xff00); // LVOL
        writeReg(chip, 0x09, 0xff00); // RVOL
        writeReg(chip, 0x0a, 0x0000); // ACC
        writeReg(chip, 0x0b, 0x0000);
        writeReg(chip, 0x00, 0x0c08); // CR: LP3|LP4, LPE (loop), run
    }

    @Test
    void testPlay() {
        Es5505 chip = new Es5505();
        assertEquals(CLOCK / 512, chip.start(CLOCK, 1));
        chip.reset();
        byte[] rom = squareWave();
        chip.writeRom(rom.length, 0, rom.length, rom, 0);
        keyOn(chip);

        int samples = 1024;
        int[][] outputs = new int[2][samples];
        chip.update(outputs, samples);

        // a word per sample, the period is 256 samples
        int crossings = 0;
        int max = 0;
        for (int i = 1; i < samples; i++) {
            if (Integer.signum(outputs[0][i]) != Integer.signum(outputs[0][i - 1]) && outputs[0][i] != 0) crossings++;
            max = Math.max(max, Math.abs(outputs[0][i]));
        }
        assertTrue(max > 1000, "no sound: " + max);
        assertTrue(crossings >= 6 && crossings <= 9, "crossings: " + crossings);
        assertEquals(outputs[0][500], outputs[1][500]);

        // read back via the register
        writeReg(chip, 0x0f, 0x00);
        assertEquals(0x0c08 | 0xf000, (chip.read8(0) << 8) | chip.read8(1));
        assertTrue(chip.isEnabled(0));
    }

    @Test
    void testStop() {
        Es5505 chip = new Es5505();
        chip.start(CLOCK, 1);
        chip.reset();
        byte[] rom = squareWave();
        chip.writeRom(rom.length, 0, rom.length, rom, 0);
        keyOn(chip);
        writeReg(chip, 0x00, 0x0c00); // CR: no loop

        int[][] outputs = new int[2][1024];
        chip.update(outputs, 1024);
        assertEquals(0, outputs[0][1000]);
        assertTrue(!chip.isEnabled(0));
    }

    @Test
    void testSampleRateChange() {
        int rate = 44100;

        MDSound.Chip chip = new MDSound.Chip();
        chip.instrument = new Es5505Inst();
        chip.samplingRate = rate;
        chip.clock = CLOCK;
        chip.option = new Object[] {1};

        MDSound mds = new MDSound();
        mds.init(rate, 512, List.of(chip));
        assertEquals(CLOCK / 512, chip.samplingRate);
        assertEquals(0x01, chip.resampler);

        // ACT: 16 voices, 59523Hz
        mds.write(Es5505Inst.class, 0, 0, 0x1b, 0x0f);
        assertEquals(CLOCK / (16 * 16), chip.samplingRate);
        assertTrue(chip.resampler == 0x03 || chip.resampler == 0x00, "resampler: " + chip.resampler);

        short[] buf = new short[1024];
        mds.update(buf, 0, buf.length, null);
    }
}
