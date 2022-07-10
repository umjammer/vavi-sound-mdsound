package mdsound.chips;

import java.util.function.BiConsumer;

import mdsound.MDSound;


/**
 * OKI MSM6258 ADPCM
 * <p>
 * TODO:
 *   3-bit ADPCM support
 *   Recording?
 */
public class OkiM6258 {
    private static final int FOSC_DIV_BY_1024 = 0;
    private static final int FOSC_DIV_BY_768 = 1;
    private static final int FOSC_DIV_BY_512 = 2;

    private static final int TYPE_3BITS = 0;
    private static final int TYPE_4BITS = 1;

    private static final int OUTPUT_10BITS = 0;
    private static final int OUTPUT_12BITS = 1;

    private static final int COMMAND_STOP = 1 << 0;
    private static final int COMMAND_PLAY = 1 << 1;
    private static final int COMMAND_RECORD = 1 << 2;

    private static final int STATUS_PLAYING = 1 << 1;
    private static final int STATUS_RECORDING = 1 << 2;

    private static final int[] dividers = new int[] {1024, 768, 512, 512};

    private static final int QUEUE_SIZE = (1 << 1);
    private static final int QUEUE_MASK = (QUEUE_SIZE - 1);

    private byte status;

    /** master clock frequency */
    private int masterClock;
    /** master clock divider */
    private int divider;
    /** 3/4 bit ADPCM select */
    private byte adpcmType;
    /** ADPCM data-in register */
    private byte dataIn;
    /** nibble select */
    private byte nibbleShift;

    private byte outputBits;
    private int outputMask;

    // Valley Bell: Added a small queue to prevent race conditions.
    private byte[] dataBuf = new byte[8];
    private byte dataInLast;
    private byte dataBufPos;
    // Data Empty Values:
    // 00 - data written, but not read yet
    // 01 - read data, waiting for next write
    // 02 - tried to read, but had no data
    private byte dataEmpty;
    // Valley Bell: Added pan
    private byte pan;
    private int lastSmpl;

    private int signal;
    private int step;

    private byte[] clockBuffer = new byte[0x04];
    private int initialClock;
    private byte initialDiv;

    private SampleRateCallback smpRateFunc;
    private MDSound.Chip smpRateData;

    /**
     * get the VCLK/sampling frequency
     */
    public int getVclk() {
        int clkRnd;

        clkRnd = this.masterClock;
        clkRnd += this.divider / 2; // for better rounding - should help some of the streams
        return clkRnd / this.divider;
    }

    private short clockAdpcm(byte nibble) {
        int max = this.outputMask - 1;
        int min = -(int) this.outputMask;

        int sample = diffLookup[this.step * 16 + (nibble & 15)];
        this.signal = ((sample << 8) + (this.signal * 245)) >> 8;

        /* clamp to the maximum */
        if (this.signal > max)
            this.signal = max;
        else if (this.signal < min)
            this.signal = min;

        /* adjust the step size and clamp */
        this.step += indexShift[nibble & 7];
        if (this.step > 48)
            this.step = 48;
        else if (this.step < 0)
            this.step = 0;

        /* return the signal scaled up to 32767 */
        return (short) (this.signal << 4);
    }

    public interface SampleRateCallback extends BiConsumer<MDSound.Chip, Integer> {
    }

    /* step size index shift table */
    private static final int[] indexShift = new int[] {-1, -1, -1, -1, 2, 4, 6, 8};

    /* lookup table for the precomputed difference */
    private static int[] diffLookup = new int[49 * 16];

    private static byte internal10Bit = 0x00;

    /*
     * compute the difference tables
     */
    static {
        /* nibble to bit map */
        final int[][] nbl2bit = new int[][] {
                new int[] {1, 0, 0, 0}, new int[] {1, 0, 0, 1}, new int[] {1, 0, 1, 0}, new int[] {1, 0, 1, 1},
                new int[] {1, 1, 0, 0}, new int[] {1, 1, 0, 1}, new int[] {1, 1, 1, 0}, new int[] {1, 1, 1, 1},
                new int[] {-1, 0, 0, 0}, new int[] {-1, 0, 0, 1}, new int[] {-1, 0, 1, 0}, new int[] {-1, 0, 1, 1},
                new int[] {-1, 1, 0, 0}, new int[] {-1, 1, 0, 1}, new int[] {-1, 1, 1, 0}, new int[] {-1, 1, 1, 1}
        };

        /* loop over all possible steps */
        for (int step = 0; step <= 48; step++) {
            /* compute the step value */
            int stepVal = (int) Math.floor(16.0 * Math.pow(11.0 / 10.0, step));

            /* loop over all nibbles and compute the difference */
            for (int nib = 0; nib < 16; nib++) {
                diffLookup[step * 16 + nib] = nbl2bit[nib][0] *
                        (stepVal * nbl2bit[nib][1] +
                                stepVal / 2 * nbl2bit[nib][2] +
                                stepVal / 4 * nbl2bit[nib][3] +
                                stepVal / 8);
//                Debug.printf("diff_lookup[%d]=%d ", step * 16 + nib, diff_lookup[step * 16 + nib]);
            }
        }
    }

    /**
     * update the Sound chips so that it is in sync with CPU execution
     */
    public void update(int[][] outputs, int samples) {
        int[] bufL = outputs[0];
        int[] bufR = outputs[1];
        int ind = 0;

        //memset(outputs[0], 0, samples * sizeof(*outputs[0]));

        if ((this.status & STATUS_PLAYING) != 0) {
            int nibbleShift = this.nibbleShift;

            while (samples != 0) {
                //Debug.printf("status=%d this.nibbleShift=%d ", this.status, this.nibbleShift);
                // Compute the new amplitude and update the current step
                //int nibble = (this.data_in >> nibbleShift) & 0xf;
                int nibble;
                short sample;

                //Debug.printf("this.data_empty=%d ", this.data_empty);
                if (nibbleShift == 0) {
                    // 1st nibble - get data
                    if (this.dataEmpty == 0) {
                        this.dataIn = this.dataBuf[this.dataBufPos >> 4];
                        this.dataBufPos += 0x10;
                        this.dataBufPos &= 0x7f;
                        if ((this.dataBufPos >> 4) == (this.dataBufPos & 0x0F))
                            this.dataEmpty++;
                    } else {
                        if (this.dataEmpty < 0x80)
                            this.dataEmpty++;
                    }
                }
                nibble = (this.dataIn >> nibbleShift) & 0xf;

                // Output to the buffer
                //INT16 sample = clock_adpcm(chips, nibble);
                if (this.dataEmpty < 0x02) {
                    sample = this.clockAdpcm((byte) nibble);
                    this.lastSmpl = sample;
                } else {
                    // Valley Bell: data_empty behaviour (loosely) ported from XM6
                    if (this.dataEmpty >= 0x02 + 0x01) {
                        this.dataEmpty -= 0x01;
                        //if (this.signal < 0)
                        //    this.signal++;
                        //else if (this.signal > 0)
                        //    this.signal--;
                        this.signal = this.signal * 15 / 16;
                        this.lastSmpl = this.signal << 4;
                    }
                    sample = (short) this.lastSmpl;
                }

                nibbleShift ^= 4;

                //Debug.printf("this.pan=%d sample=%d ", this.pan, sample);
                bufL[ind] = ((this.pan & 0x02) != 0) ? 0x00 : sample;
                bufR[ind] = ((this.pan & 0x01) != 0) ? 0x00 : sample;
                //Debug.printf("001  bufL[%d]=%d  bufR[%d]=%d", ind, bufL[ind], ind, bufR[ind]);
                samples--;
                ind++;
            }

            // Update the parameters
            this.nibbleShift = (byte) nibbleShift;
        } else {
            // Fill with 0
            while ((samples--) != 0) {
//                    Debug.printf("passed ");
                bufL[ind] = 0;
                bufR[ind] = 0;
                ind++;
            }
        }
    }

    /**
     * Starts emulation of an OKIM6258-compatible chips
     */
    public int start(int clock, int divider, int adpcmType, int output12Bits) {
        this.initialClock = clock;
        this.initialDiv = (byte) divider;
        this.masterClock = clock;
        this.adpcmType = (byte) adpcmType;
        this.clockBuffer[0x00] = (byte) ((clock & 0x000000FF) >> 0);
        this.clockBuffer[0x01] = (byte) ((clock & 0x0000FF00) >> 8);
        this.clockBuffer[0x02] = (byte) ((clock & 0x00FF0000) >> 16);
        this.clockBuffer[0x03] = (byte) ((clock & 0xFF000000) >>> 24);

        // D/A precision is 10-bits but 12-bit data can be output serially to an external DAC
        this.outputBits = (byte) ((output12Bits != 0) ? 12 : 10);
        if (internal10Bit != 0)
            this.outputMask = 1 << (this.outputBits - 1);
        else
            this.outputMask = (1 << (12 - 1));
        this.divider = dividers[divider];

        this.signal = -2;
        this.step = 0;

        return this.getVclk();
    }

    public void reset() {
        this.masterClock = this.initialClock;
        this.clockBuffer[0x00] = (byte) ((this.initialClock & 0x000000FF) >> 0);
        this.clockBuffer[0x01] = (byte) ((this.initialClock & 0x0000FF00) >> 8);
        this.clockBuffer[0x02] = (byte) ((this.initialClock & 0x00FF0000) >> 16);
        this.clockBuffer[0x03] = (byte) ((this.initialClock & 0xFF000000) >>> 24);
        this.divider = dividers[this.initialDiv];
        if (this.smpRateFunc != null) {
            this.smpRateFunc.accept(this.smpRateData, this.getVclk());
            //Debug.printf("passed");
        }

        this.signal = -2;
        this.step = 0;
        this.status = 0;

        // Valley Bell: Added reset of the Data In register.
        this.dataIn = 0x00;
        this.dataBuf[0] = this.dataBuf[1] = 0x00;
        this.dataBufPos = 0x00;
        this.dataEmpty = (byte) 0xFF;
        this.pan = 0x00;
    }

    /**
     * set the master clock divider
     */
    public void setDivider(int val) {
        this.divider = dividers[val];
        if (this.smpRateFunc != null)
            this.smpRateFunc.accept(this.smpRateData, this.getVclk());
    }

    /**
     * set the master clock
     */
    public void setClock(int val) {
        if (val != 0) {
            this.masterClock = val;
        } else {
            this.masterClock = (this.clockBuffer[0x00] << 0) |
                    (this.clockBuffer[0x01] << 8) |
                    (this.clockBuffer[0x02] << 16) |
                    (this.clockBuffer[0x03] << 24);
        }
        if (this.smpRateFunc != null)
            this.smpRateFunc.accept(this.smpRateData, this.getVclk());
    }

    /**
     * write to the control port of an OKIM6258-compatible chips
     */
    public void data_write(/*offs_t offset, */byte data) {
        if (this.dataEmpty >= 0x02) {
            this.dataBufPos = 0x00;
        }
        this.dataInLast = data;
        this.dataBuf[this.dataBufPos & 0x0F] = data;
        this.dataBufPos += 0x01;
        this.dataBufPos &= 0xf7;
        if ((this.dataBufPos >> 4) == (this.dataBufPos & 0x0F)) {
            //Debug.printf("Warning: FIFO full!\n");
            this.dataBufPos = (byte) ((this.dataBufPos & 0xF0) | ((this.dataBufPos - 1) & 0x07));
        }
        this.dataEmpty = 0x00;
    }

    /**
     * write to the control port of an OKIM6258-compatible chips
     */
    public void writeControl(/*offs_t offset, */byte data) {
        if ((data & COMMAND_STOP) != 0) {
            //Debug.printf("COMMAND:STOP");
            this.status &= (byte) (0x2 + 0x4);
            return;
        }

        if ((data & COMMAND_PLAY) != 0) {
            //Debug.printf("COMMAND:PLAY");
            if ((this.status & STATUS_PLAYING) == 0) {
                this.status |= STATUS_PLAYING;

                // Also reset the ADPCM parameters
                this.signal = -2;
                this.step = 0;
                this.nibbleShift = 0;

                this.dataBuf[0x00] = data;
                this.dataBufPos = 0x01; // write pos 01, read pos 00
                this.dataEmpty = 0x00;
            }
            this.step = 0;
            this.nibbleShift = 0;
        } else {
            this.status &= 0xd;
        }

        if ((data & COMMAND_RECORD) != 0) {
            //Debug.printf("M6258: Record enabled\n");
            this.status |= STATUS_RECORDING;
        } else {
            this.status &= 0xb;
        }
    }

    private void setClockByte(byte port, byte val) {
        this.clockBuffer[port] = val;
    }

    public void writePan(byte data) {
        this.pan = data;
    }

    public void write(byte port, byte data) {
        //Debug.printf("port=%2x data=%2x \n", port, data);
        switch (port) {
        case 0x00:
            writeControl(/*0x00, */data);
            break;
        case 0x01:
            write((byte) 0x00, data);
            break;
        case 0x02:
            writePan(data);
            break;
        case 0x08:
        case 0x09:
        case 0x0A:
            setClockByte((byte) (port & 0x03), data);
            break;
        case 0x0B:
            setClockByte((byte) (port & 0x03), data);
            setClock(0);
            break;
        case 0x0C:
            setDivider(data);
            break;
        }
    }

    public void setCallback(SampleRateCallback callbackFunc, MDSound.Chip chip) {
        // set sample rate change callback routine
        this.smpRateFunc = callbackFunc;
        this.smpRateData = chip;
    }

    public static void setOptions(int options) {
        internal10Bit = (byte) ((options >> 0) & 0x01);
    }

    public int getPan() {
        return pan;
    }

    public int getMasterClock() {
        return masterClock;
    }

    public int getDivider() {
        return divider;
    }

    public int getDataIn() {
        return dataIn;
    }

    public int getStatus() {
        return status;
    }
}
