/*
 * Gens: PWM audio emulator.
 *
 * Copyright (c) 1999-2002 by St駱hane Dallongeville
 * Copyright (c) 2003-2004 by St駱hane Akhoun
 * Copyright (c) 2008-2009 by David Korth
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.PwmChip;


public class PwmInst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x02;
    private PwmChip[] chips = new PwmChip[] {new PwmChip(), new PwmChip()};

    @Override
    public String getName() {
        return "PWM";
    }

    @Override
    public String getShortName() {
        return "PWM";
    }

    public PwmInst() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
        //0..Main
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        PwmChip chip = chips[chipId];

        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int start(byte chipId, int samplingRate, int clockValue, Object... option) {
        return start(chipId, clockValue);
    }

    @Override
    public int start(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        PwmChip chip = chips[chipId];

        int rate = 22020; // that's the rate the PWM is mostly used
        if ((Instrument.BaseInstrument.CHIP_SAMPLING_MODE == 0x01 && rate < Instrument.BaseInstrument.CHIP_SAMPLE_RATE) ||
                Instrument.BaseInstrument.CHIP_SAMPLING_MODE == 0x02)
            rate = Instrument.BaseInstrument.CHIP_SAMPLE_RATE;
        chip.start(clock, rate);
        return rate;
    }

    @Override
    public void stop(byte chipId) {
    }

    @Override
    public void reset(byte chipId) {
        PwmChip chip = chips[chipId];
        chip.init();
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        PwmChip chip = chips[chipId];
        chip.writeChannel((byte) adr, data);
        return 0;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0xE0/*0xCD*/, 1d);
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        result.put("pwm", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        return result;
    }
}
