/*
 * license:BSD-3-Clause
 *
 * copyright-holders: Aaron Giles
 */

package mdsound.chips;

import java.util.Arrays;


/**
 * Ensoniq ES5505 "OTIS"
 * <p>
 * the chip core follows MAME (src/devices/sound/es5506.cpp),
 * the byte wide register access and the sample rom follow VGMPlay (chips/es5506.c) for the VGM format.
 *
 * @author Aaron Giles
 */
public class Es5505 extends Es550x {

    private static final int VOLUME_BIT_ES5505 = 8;
    private static final int ADDRESS_INTEGER_BIT_ES5505 = 20;
    private static final int ADDRESS_FRAC_BIT_ES5505 = 9;

    @Override
    protected int getLp(int control) {
        return (control >> 10) & LP_MASK;
    }

    @Override
    protected int getCa(int control) {
        return (control >> 8) & 3;
    }

    @Override
    protected int getBank(int control) {
        return (control >> 2) & 1;
    }

    /**
     * device-specific startup
     *
     * @param channels number of output channels 1 .. 4
     * @return sample rate
     */
    public int start(int clock, int channels) {
        deviceStart(clock);

        // only override the number of channels if the value is in the valid range 1 .. 4
        this.channels = (1 <= channels && channels <= 4) ? channels : 1; // 1 channel by default, for backward compatibility

        // compute the tables
        computeTables(VOLUME_BIT_ES5505, 4, 4); // 4 bit exponent, 4 bit mantissa

        // 20 bit integer and 9 bit fraction
        getAccumMask(ADDRESS_INTEGER_BIT_ES5505, ADDRESS_FRAC_BIT_ES5505);

        activeVoices = 0x1f;
        sampleRate = masterClock / (16 * 32);
        return sampleRate;
    }

    @Override
    public void reset() {
        for (Voice voice : voices) {
            boolean muted = voice.muted;
            long exBank = voice.exBank;
            voice.control = CONTROL_STOPMASK;
            voice.freqCount = 0;
            voice.start = 0;
            voice.end = 0;
            voice.accum = 0;
            voice.lVol = 1 << (VOLUME_BIT_ES5505 - 1);
            voice.rVol = 1 << (VOLUME_BIT_ES5505 - 1);
            voice.eCount = 0;
            voice.k1 = 0;
            voice.k2 = 0;
            voice.o4n1 = voice.o3n1 = voice.o3n2 = voice.o2n1 = voice.o2n2 = voice.o1n1 = 0;
            voice.exBank = exBank;
            voice.muted = muted;
        }
        currentPage = 0;
        mode = 0;
        irqv = 0x80;
        activeVoices = 0x1f;
        updateSampleRate();
    }

    @Override
    protected void updateEnvelopes(Voice voice) {
        // no envelopes in ES5505
        voice.eCount = 0;
    }

    // ES5505 : BLE is ignored when LPE = 0
    @Override
    protected long checkForEndForward(Voice voice, long accum) {
        // are we past the end?
        if (accum > voice.end) {
            // generate interrupt if required
            if ((voice.control & CONTROL_IRQE) != 0)
                voice.control |= CONTROL_IRQ;

            // handle the different types of looping
            switch (voice.control & CONTROL_LOOPMASK) {
                // non-looping
                case 0, CONTROL_BLE -> voice.control |= CONTROL_STOP0;
                // uni-directional looping
                case CONTROL_LPE -> accum = (voice.start + (accum - voice.end)) & addressAccMask;
                // bi-directional looping
                case CONTROL_LPE | CONTROL_BLE -> {
                    accum = (voice.end - (accum - voice.end)) & addressAccMask;
                    voice.control ^= CONTROL_DIR;
                }
            }
        }
        return accum;
    }

    @Override
    protected long checkForEndReverse(Voice voice, long accum) {
        // are we past the end?
        if (accum < voice.start) {
            // generate interrupt if required
            if ((voice.control & CONTROL_IRQE) != 0)
                voice.control |= CONTROL_IRQ;

            // handle the different types of looping
            switch (voice.control & CONTROL_LOOPMASK) {
                // non-looping
                case 0, CONTROL_BLE -> voice.control |= CONTROL_STOP0;
                // uni-directional looping
                case CONTROL_LPE -> accum = (voice.end - (voice.start - accum)) & addressAccMask;
                // bi-directional looping
                case CONTROL_LPE | CONTROL_BLE -> {
                    accum = (voice.start + (voice.start - accum)) & addressAccMask;
                    voice.control ^= CONTROL_DIR;
                }
            }
        }
        return accum;
    }

    @Override
    protected void generateSamples(int[][] outputs, int samples) {
        int[] curSample = new int[12];
        // loop while we still have samples to generate
        for (int i = 0; i < samples; i++) {
            // loop over voices
            Arrays.fill(curSample, 0);
            for (int v = 0; v <= activeVoices; v++) {
                Voice voice = voices[v];

                // This special case does not appear to match the behaviour observed in the es5505 in
                // actual Ensoniq synthesizers: those, it turns out, do set loop start and end to the
                // same value, and expect the voice to keep running. Examples can be found among the
                // transwaves on the VFX / SD-1 series of synthesizers.

                int voiceChannel = getCa(voice.control);
                int channel = voiceChannel % channels;
                int l = channel << 1;

                // generate from the appropriate source
                // no compressed sample support
                generatePcm(voice, curSample, l);

                // does this voice have it's IRQ bit raised?
                generateIrq(voice, v);
            }

            // generated samples are 20-bit signed, range is [-2^19 .. 2^19-1], mixed down to stereo 16 bit
            int left = 0, right = 0;
            for (int c = 0; c < channels; c++) {
                left += Math.clamp(curSample[c << 1], -(1 << 19), (1 << 19) - 1);
                right += Math.clamp(curSample[(c << 1) + 1], -(1 << 19), (1 << 19) - 1);
            }
            outputs[0][i] = left >> 4;
            outputs[1][i] = right >> 4;
        }
    }

    //
    // registers
    //

    private static boolean accessingBits0_7(int memMask) {
        return (memMask & 0x00ff) != 0;
    }

    private static boolean accessingBits8_15(int memMask) {
        return (memMask & 0xff00) != 0;
    }

    /** handle a write to the selected ES5505 register */
    private void regWriteLow(Voice voice, int offset, int data, int memMask) {
        switch (offset) {
            case 0x00: // CR
                voice.control |= 0xf000; // bit 15-12 always 1
                if (accessingBits0_7(memMask)) {
                    voice.control &= ~0x00ff;
                    voice.control |= data & 0x00ff;
                }
                if (accessingBits8_15(memMask))
                    voice.control = (voice.control & ~0x0f00) | (data & 0x0f00);
                break;

            case 0x01: // FC
                if (accessingBits0_7(memMask))
                    voice.freqCount = (voice.freqCount & ~getAddressAccShiftedVal(0x00fe, 1)) | getAddressAccShiftedVal(data & 0x00fe, 1);
                if (accessingBits8_15(memMask))
                    voice.freqCount = (voice.freqCount & ~getAddressAccShiftedVal(0xff00, 1)) | getAddressAccShiftedVal(data & 0xff00, 1);
                break;

            case 0x02: // STRT (hi)
                if (accessingBits0_7(memMask))
                    voice.start = (voice.start & ~getAddressAccShiftedVal(0x00ff_0000)) | getAddressAccShiftedVal((long) (data & 0x00ff) << 16);
                if (accessingBits8_15(memMask))
                    voice.start = (voice.start & ~getAddressAccShiftedVal(0x1f00_0000)) | getAddressAccShiftedVal((long) (data & 0x1f00) << 16);
                break;

            case 0x03: // STRT (lo)
                if (accessingBits0_7(memMask))
                    voice.start = (voice.start & ~getAddressAccShiftedVal(0x0000_00e0)) | getAddressAccShiftedVal(data & 0x00e0);
                if (accessingBits8_15(memMask))
                    voice.start = (voice.start & ~getAddressAccShiftedVal(0x0000_ff00)) | getAddressAccShiftedVal(data & 0xff00);
                break;

            case 0x04: // END (hi)
                if (accessingBits0_7(memMask))
                    voice.end = (voice.end & ~getAddressAccShiftedVal(0x00ff_0000)) | getAddressAccShiftedVal((long) (data & 0x00ff) << 16);
                if (accessingBits8_15(memMask))
                    voice.end = (voice.end & ~getAddressAccShiftedVal(0x1f00_0000)) | getAddressAccShiftedVal((long) (data & 0x1f00) << 16);
                break;

            case 0x05: // END (lo)
                if (accessingBits0_7(memMask))
                    voice.end = (voice.end & ~getAddressAccShiftedVal(0x0000_00e0)) | getAddressAccShiftedVal(data & 0x00e0);
                if (accessingBits8_15(memMask))
                    voice.end = (voice.end & ~getAddressAccShiftedVal(0x0000_ff00)) | getAddressAccShiftedVal(data & 0xff00);
                break;

            case 0x06: // K2
                if (accessingBits0_7(memMask))
                    voice.k2 = (voice.k2 & ~0x00f0) | (data & 0x00f0);
                if (accessingBits8_15(memMask))
                    voice.k2 = (voice.k2 & ~0xff00) | (data & 0xff00);
                break;

            case 0x07: // K1
                if (accessingBits0_7(memMask))
                    voice.k1 = (voice.k1 & ~0x00f0) | (data & 0x00f0);
                if (accessingBits8_15(memMask))
                    voice.k1 = (voice.k1 & ~0xff00) | (data & 0xff00);
                break;

            case 0x08: // LVOL
                if (accessingBits8_15(memMask))
                    voice.lVol = (voice.lVol & ~0xff) | ((data & 0xff00) >> 8);
                break;

            case 0x09: // RVOL
                if (accessingBits8_15(memMask))
                    voice.rVol = (voice.rVol & ~0xff) | ((data & 0xff00) >> 8);
                break;

            case 0x0a: // ACC (hi)
                if (accessingBits0_7(memMask))
                    voice.accum = (voice.accum & ~getAddressAccShiftedVal(0x00ff_0000)) | getAddressAccShiftedVal((long) (data & 0x00ff) << 16);
                if (accessingBits8_15(memMask))
                    voice.accum = (voice.accum & ~getAddressAccShiftedVal(0x1f00_0000)) | getAddressAccShiftedVal((long) (data & 0x1f00) << 16);
                break;

            case 0x0b: // ACC (lo)
                if (accessingBits0_7(memMask))
                    voice.accum = (voice.accum & ~getAddressAccShiftedVal(0x0000_00ff)) | getAddressAccShiftedVal(data & 0x00ff);
                if (accessingBits8_15(memMask))
                    voice.accum = (voice.accum & ~getAddressAccShiftedVal(0x0000_ff00)) | getAddressAccShiftedVal(data & 0xff00);
                break;

            case 0x0c: // unused
                break;

            default:
                regWriteGlobal(offset, data, memMask);
                break;
        }
    }

    private void regWriteHigh(Voice voice, int offset, int data, int memMask) {
        switch (offset) {
            case 0x00: // CR
                voice.control |= 0xf000; // bit 15-12 always 1
                if (accessingBits0_7(memMask))
                    voice.control = (voice.control & ~0x00ff) | (data & 0x00ff);
                if (accessingBits8_15(memMask))
                    voice.control = (voice.control & ~0x0f00) | (data & 0x0f00);
                break;

            case 0x01: // O4(n-1)
                voice.o4n1 = writeFilter(voice.o4n1, data, memMask);
                break;

            case 0x02: // O3(n-1)
                voice.o3n1 = writeFilter(voice.o3n1, data, memMask);
                break;

            case 0x03: // O3(n-2)
                voice.o3n2 = writeFilter(voice.o3n2, data, memMask);
                break;

            case 0x04: // O2(n-1)
                voice.o2n1 = writeFilter(voice.o2n1, data, memMask);
                break;

            case 0x05: // O2(n-2)
                voice.o2n2 = writeFilter(voice.o2n2, data, memMask);
                break;

            case 0x06: // O1(n-1)
                voice.o1n1 = writeFilter(voice.o1n1, data, memMask);
                break;

            case 0x07:
            case 0x08:
            case 0x09:
            case 0x0a:
            case 0x0b:
            case 0x0c: // unused
                break;

            default:
                regWriteGlobal(offset, data, memMask);
                break;
        }
    }

    private static int writeFilter(int value, int data, int memMask) {
        if (accessingBits0_7(memMask))
            value = (value & ~0x00ff) | (data & 0x00ff);
        if (accessingBits8_15(memMask))
            value = (short) ((value & ~0xff00) | (data & 0xff00));
        return value;
    }

    private void regWriteTest(int offset, int data, int memMask) {
        switch (offset) {
            case 0x00: // CH0L
            case 0x01: // CH0R
            case 0x02: // CH1L
            case 0x03: // CH1R
            case 0x04: // CH2L
            case 0x05: // CH2R
            case 0x06: // CH3L
            case 0x07: // CH3R
                break;

            case 0x08: // SERMODE
                mode |= 0x7f8; // bit 10-3 always 1
                if (accessingBits8_15(memMask))
                    mode = (mode & ~0xf800) | (data & 0xf800); // MSB[4:0] (unknown purpose)
                if (accessingBits0_7(memMask))
                    mode = (mode & ~0x0007) | (data & 0x0007); // SONY/BB, TEST, A/D
                break;

            case 0x09: // PAR
                break;

            default:
                regWriteGlobal(offset, data, memMask);
                break;
        }
    }

    /** the registers accessible from all pages */
    private void regWriteGlobal(int offset, int data, int memMask) {
        switch (offset) {
            case 0x0d: // ACT
                if (accessingBits0_7(memMask)) {
                    activeVoices = data & 0x1f;
                    updateSampleRate();
                }
                break;

            case 0x0e: // IRQV - read only
                break;

            case 0x0f: // PAGE
                if (accessingBits0_7(memMask))
                    currentPage = data & 0x7f;
                break;
        }
    }

    /**
     * 16 bit register write
     *
     * @param offset register 0x00 .. 0x0f
     */
    public void write(int offset, int data, int memMask) {
        Voice voice = voices[currentPage & 0x1f];

        // switch off the page and register
        if (currentPage < 0x20)
            regWriteLow(voice, offset, data, memMask);
        else if (currentPage < 0x40)
            regWriteHigh(voice, offset, data, memMask);
        else
            regWriteTest(offset, data, memMask);
    }

    /** read from the specified ES5505 register */
    private int regReadLow(Voice voice, int offset) {
        return switch (offset) {
            case 0x00 -> voice.control | 0xf000; // CR
            case 0x01 -> (int) getAddressAccRes(voice.freqCount, 1); // FC
            case 0x02 -> (int) (getAddressAccRes(voice.start) >> 16); // STRT (hi)
            case 0x03 -> (int) getAddressAccRes(voice.start); // STRT (lo)
            case 0x04 -> (int) (getAddressAccRes(voice.end) >> 16); // END (hi)
            case 0x05 -> (int) getAddressAccRes(voice.end); // END (lo)
            case 0x06 -> voice.k2; // K2
            case 0x07 -> voice.k1; // K1
            case 0x08 -> voice.lVol << 8; // LVOL
            case 0x09 -> voice.rVol << 8; // RVOL
            case 0x0a -> (int) (getAddressAccRes(voice.accum) >> 16); // ACC (hi)
            case 0x0b -> (int) getAddressAccRes(voice.accum); // ACC (lo)
            default -> regReadGlobal(offset);
        } & 0xffff;
    }

    private int regReadHigh(Voice voice, int offset) {
        return switch (offset) {
            case 0x00 -> voice.control | 0xf000; // CR
            case 0x01 -> voice.o4n1; // O4(n-1)
            case 0x02 -> voice.o3n1; // O3(n-1)
            case 0x03 -> voice.o3n2; // O3(n-2)
            case 0x04 -> voice.o2n1; // O2(n-1)
            case 0x05 -> voice.o2n2; // O2(n-2)
            case 0x06 -> { // O1(n-1)
                // special case for the Taito F3 games: they set the accumulator on a stopped
                // voice and assume the filters continue to process the data. They then read
                // the O1(n-1) in order to extract raw data from the sound ROMs. Since we don't
                // want to waste time filtering stopped channels, we just look for a read from
                // this register on a stopped voice, and return the raw sample data at the
                // accumulator
                if ((voice.control & CONTROL_STOPMASK) != 0)
                    voice.o1n1 = readSample(voice, getIntegerAddr(voice.accum, 0));
                yield voice.o1n1;
            }
            default -> regReadGlobal(offset);
        } & 0xffff;
    }

    private int regReadTest(int offset) {
        return switch (offset) {
            case 0x08 -> mode | 0x7f8; // SERMODE
            default -> regReadGlobal(offset);
        } & 0xffff;
    }

    /** the registers accessible from all pages, and unused ones */
    private int regReadGlobal(int offset) {
        return switch (offset) {
            case 0x0d -> activeVoices; // ACT
            case 0x0e -> { // IRQV
                int result = irqv;
                updateInternalIrqState();
                yield result;
            }
            case 0x0f -> currentPage; // PAGE
            default -> 0;
        };
    }

    /**
     * 16 bit register read
     *
     * @param offset register 0x00 .. 0x0f
     */
    public int read(int offset) {
        Voice voice = voices[currentPage & 0x1f];

        // switch off the page and register
        if (currentPage < 0x20)
            return regReadLow(voice, offset);
        else if (currentPage < 0x40)
            return regReadHigh(voice, offset);
        else
            return regReadTest(offset);
    }

    //
    // VGM
    //

    /**
     * byte wide access as VGM does, an even offset is the upper byte of the register (offset / 2).
     * offsets from 0x40 select the voice bank.
     */
    public void write8(int offset, int data) {
        data &= 0xff;
        if (offset < 0x40) {
            if ((offset & 1) == 0)
                write(offset >> 1, data << 8, 0xff00);
            else
                write(offset >> 1, data, 0x00ff);
        } else {
            setVoiceBank(offset & 0x1f, (long) data << 20);
        }
    }

    /** 16 bit access as VGM does, offset is the same as {@link #write8} */
    public void write16(int offset, int data) {
        if (offset < 0x40) {
            write8(offset, (data >> 8) & 0xff);
            write8(offset | 1, data & 0xff);
        } else {
            setVoiceBank(offset & 0x1f, (long) (data & 0xffff) << 20);
        }
    }

    /** byte wide read, offset is the same as {@link #write8} */
    public int read8(int offset) {
        int result = read((offset >> 1) & 0x0f);
        return (offset & 1) == 0 ? (result >> 8) & 0xff : result & 0xff;
    }
}
