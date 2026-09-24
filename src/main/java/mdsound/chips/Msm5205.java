// license:BSD-3-Clause
// copyright-holders:Aaron Giles, eito, cam900, Valley Bell, Mao

package mdsound.chips;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Arrays;
import java.util.function.Consumer;

import static java.lang.System.getLogger;


/**
 * OKI MSM5205 ADPCM (and its successor the MSM6585).
 * <p>
 * The chip decodes one nibble per tick of its sampling clock, the master clock over a prescaler
 * the S1/S2 pins pick. The host feeds the nibbles, which libvgm's core queues in an eight entry
 * FIFO; a tick with nothing queued holds the last level.
 * <p>
 * Registers, as the VGM command {@code 0x32} and the DAC stream address them:
 * <pre>
 * 0 reset (non zero holds the chip reset, silent)
 * 1 data, the low nibble (3 bit mode uses the low 3 bits)
 * 2 VCK, clocks a nibble on a rising edge in slave mode (S1 = S2 = 1)
 * 4 prescaler, bit 0 = S1, bit 1 = S2
 * 5 bit width, 0 = 3 bit, else 4 bit
 * </pre>
 * Ported from libvgm's emu/cores/msm5205.c.
 */
public class Msm5205 {

    private static final Logger logger = getLogger(Msm5205.class.getName());

    private static final int PIN_S1 = 0x01;
    private static final int PIN_S2 = 0x02;

    private static final int[] indexShift = {-1, -1, -1, -1, 2, 4, 6, 8};

    private static final int[] diffLookup = new int[49 * 16];

    static {
        int[][] nbl2bit = {
                {1, 0, 0, 0}, {1, 0, 0, 1}, {1, 0, 1, 0}, {1, 0, 1, 1},
                {1, 1, 0, 0}, {1, 1, 0, 1}, {1, 1, 1, 0}, {1, 1, 1, 1},
                {-1, 0, 0, 0}, {-1, 0, 0, 1}, {-1, 0, 1, 0}, {-1, 0, 1, 1},
                {-1, 1, 0, 0}, {-1, 1, 0, 1}, {-1, 1, 1, 0}, {-1, 1, 1, 1}
        };
        for (int step = 0; step <= 48; step++) {
            int stepVal = (int) Math.floor(16.0 * Math.pow(11.0 / 10.0, step));
            for (int nib = 0; nib < 16; nib++) {
                diffLookup[step * 16 + nib] = nbl2bit[nib][0] *
                        (stepVal * nbl2bit[nib][1] +
                                stepVal / 2 * nbl2bit[nib][2] +
                                stepVal / 4 * nbl2bit[nib][3] +
                                stepVal / 8);
            }
        }
    }

    private int masterClock;
    private int signal;
    private int step;
    private int vclk;

    private final int[] dataBuf = new int[8];
    /** bit 6-4: write position, bit 2-0: read position */
    private int dataBufPos;

    private int reset;
    private int initPrescaler;
    private int prescaler;
    private int initBitWidth;
    private int bitWidth;
    private boolean muted;

    private boolean isMsm6585;

    private Consumer<Integer> sampleRateChanged;

    /** the last nibble decoded, for the view */
    private int lastData;
    /** output samples since a nibble was last decoded, for the view */
    private int idleSamples = Integer.MAX_VALUE;

    private int prescaler() {
        if (isMsm6585) {
            // prescalers: 160, 80, 40, 20
            return (prescaler & PIN_S1) != 0 ?
                    ((prescaler & PIN_S2) != 0 ? 20 : 80) :
                    ((prescaler & PIN_S2) != 0 ? 40 : 160);
        } else {
            // prescalers: 96, 64, 48, 1
            return (prescaler & PIN_S1) != 0 ?
                    ((prescaler & PIN_S2) != 0 ? 1 /* slave mode */ : 64) :
                    ((prescaler & PIN_S2) != 0 ? 48 : 96);
        }
    }

    private int clockAdpcm(int data) {
        if (reset != 0) {
            step = 0;
            signal = 0;
            return 0;
        }

        if (bitWidth == 3) data <<= 1;
        data &= 0x0f;

        int sample = diffLookup[step * 16 + data];
        signal = ((sample << 8) + (signal * 245)) >> 8;
        signal = Math.max(-2048, Math.min(2047, signal));

        step += indexShift[data & 7];
        step = Math.max(0, Math.min(48, step));

        lastData = data;
        idleSamples = 0;
        return signal;
    }

    /** @return the pending nibble, or -1 when the FIFO is empty */
    private int pop() {
        int readPos = dataBufPos & 0x07;
        int writePos = (dataBufPos >> 4) & 0x07;
        if (readPos == writePos) return -1;
        int data = dataBuf[readPos];
        dataBufPos = (writePos << 4) | ((readPos + 1) & 0x07);
        return data;
    }

    /**
     * @param prescaler bit 0: S1, bit 1: S2
     * @param bitWidth 3 or 4, 0 is 4
     * @param isMsm6585 the MSM6585's prescalers instead of the MSM5205's
     * @return the sampling rate
     */
    public int start(int clock, int prescaler, int bitWidth, boolean isMsm6585) {
        this.masterClock = clock;
        this.initPrescaler = prescaler;
        this.initBitWidth = bitWidth == 0 ? 4 : bitWidth;
        this.isMsm6585 = isMsm6585;
        this.muted = false;
        reset();
        return getRate();
    }

    public void reset() {
        signal = -2;
        step = 0;
        vclk = 0;
        reset = 0;
        Arrays.fill(dataBuf, 0);
        dataBufPos = 0;
        prescaler = initPrescaler;
        bitWidth = initBitWidth;
        lastData = 0;
        idleSamples = Integer.MAX_VALUE;

        if (sampleRateChanged != null) sampleRateChanged.accept(getRate());
    }

    public void update(int[][] outputs, int samples) {
        for (int i = 0; i < samples; i++) {
            int sample = 0;
            if (idleSamples != Integer.MAX_VALUE) idleSamples++;

            if (!muted && reset == 0) {
                int data;
                if (prescaler() != 1 && (data = pop()) >= 0) { // not slave mode
                    sample = clockAdpcm(data);
                } else {
                    sample = signal;
                }
            }

            outputs[0][i] = outputs[1][i] = sample << 4;
        }
    }

    public void write(int offset, int data) {
        switch (offset) {
            case 0 -> { // reset
                int old = reset;
                reset = data;
                if ((old ^ data) != 0) {
                    signal = 0;
                    step = 0;
                }
                if (reset != 0) dataBufPos = 0;
            }
            case 1 -> { // data
                int writePos = (dataBufPos >> 4) & 0x07;
                int readPos = dataBufPos & 0x07;
                if (((writePos + 1) & 0x07) == readPos) {
                    logger.log(Level.TRACE, "MSM5205 FIFO overflow");
                    return;
                }
                dataBuf[writePos] = data & 0xff;
                dataBufPos = (((writePos + 1) & 0x07) << 4) | readPos;
            }
            case 2 -> { // VCK
                int old = vclk;
                vclk = data;
                if (prescaler() == 1 && ((old ^ data) & 1) != 0 && vclk != 0) { // slave mode, rising edge
                    if (!muted && reset == 0) {
                        int nibble = pop();
                        if (nibble >= 0) clockAdpcm(nibble);
                    }
                }
            }
            case 4 -> { // prescaler
                int old = prescaler;
                prescaler = data;
                if (((old ^ data) & (PIN_S1 | PIN_S2)) != 0 && sampleRateChanged != null)
                    sampleRateChanged.accept(getRate());
            }
            case 5 -> bitWidth = data != 0 ? 4 : 3; // bit width
        }
    }

    public int getRate() {
        return masterClock / prescaler();
    }

    public void setClock(int clock) {
        masterClock = clock;
        if (sampleRateChanged != null) sampleRateChanged.accept(getRate());
    }

    public void setMute(boolean muted) {
        this.muted = muted;
    }

    public void setSampleRateChanged(Consumer<Integer> sampleRateChanged) {
        this.sampleRateChanged = sampleRateChanged;
    }

    //----

    public int getMasterClock() {
        return masterClock;
    }

    public int getSignal() {
        return signal;
    }

    public int getStep() {
        return step;
    }

    public boolean isReset() {
        return reset != 0;
    }

    public boolean isMuted() {
        return muted;
    }

    public int getBitWidth() {
        return bitWidth;
    }

    public int getLastData() {
        return lastData;
    }

    /** @return output samples since a nibble was last decoded, {@link Integer#MAX_VALUE} if never */
    public int getIdleSamples() {
        return idleSamples;
    }

    public boolean isMsm6585() {
        return isMsm6585;
    }
}
