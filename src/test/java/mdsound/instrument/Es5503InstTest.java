/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import java.util.List;
import java.util.function.Consumer;

import mdsound.MDSound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Es5503InstTest.
 */
class Es5503InstTest {

    @Test
    void testSampleRateChangeResetsResampler() {
        int rate = 44100;
        int clock = 7159090;

        MDSound.Chip chip = new MDSound.Chip();
        chip.instrument = new Es5503Inst();
        chip.samplingRate = rate;
        chip.clock = clock;
        chip.option = new Object[] {2, (Consumer<Integer>) sr -> chip.samplingRate = sr};

        MDSound mds = new MDSound();
        mds.init(rate, 512, List.of(chip));

        // after reset only 1 oscillator: (clock / 8) / 3, way above the output rate
        assertEquals((clock / 8) / 3, chip.samplingRate);
        int down = chip.resampler;

        // enable 32 oscillators: (clock / 8) / 34 = 26320, below the output rate
        mds.write(Es5503Inst.class, 0, 0, 0xe1, 31 << 1);
        assertEquals((clock / 8) / 34, chip.samplingRate);
        assertEquals(0x01, chip.resampler, "should be up sampling now, was " + down);
        assertEquals(0, chip.smpLast);

        short[] buf = new short[1024];
        mds.update(buf, 0, buf.length, null);
    }
}
