package mdsound;

import java.util.function.BiConsumer;


public class OkiM6258 extends Instrument.BaseInstrument {

    /**
     * OKI MSM6258 ADPCM
     * <p>
     * TODO:
     *   3-bit ADPCM support
     *   Recording?
     */
    public static class OkiM6258State {
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

        public byte status;

        public int masterClock;    /* master clock frequency */
        public int divider;         /* master clock divider */
        public byte adpcmType;       /* 3/4 bit ADPCM select */
        public byte dataIn;          /* ADPCM data-in register */
        public byte nibbleShift;     /* nibble select */
        //sound_stream *stream; /* which stream are we playing on? */

        public byte outputBits;
        public int outputMask;

        // Valley Bell: Added a small queue to prevent race conditions.
        public byte[] dataBuf = new byte[8];
        public byte dataInLast;
        public byte dataBufPos;
        // Data Empty Values:
        // 00 - data written, but not read yet
        // 01 - read data, waiting for next write
        // 02 - tried to read, but had no data
        public byte dataEmpty;
        // Valley Bell: Added pan
        public byte pan;
        public int lastSmpl;

        public int signal;
        public int step;

        public byte[] clockBuffer = new byte[0x04];
        public int initialClock;
        public byte initialDiv;

        public dlgSRATE_CALLBACK smpRateFunc;
        public MDSound.Chip smpRateData;

        /**
         * get the VCLK/sampling frequency
         */
        private int getVclk() {
            int clkRnd;

            clkRnd = this.masterClock;
            clkRnd += this.divider / 2;    // for better rounding - should help some of the streams
            return clkRnd / this.divider;
        }

        private short clockAdpcm(byte nibble) {
            int max = (int) this.outputMask - 1;
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

        public interface dlgSRATE_CALLBACK extends BiConsumer<MDSound.Chip, Integer> {
        }

        /* step size index shift table */
        private static final int[] indexShift = new int[] {-1, -1, -1, -1, 2, 4, 6, 8};

        /* lookup table for the precomputed difference */
        private static int[] diffLookup = new int[49 * 16];

        public static byte internal10Bit = 0x00;

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
//                System.System.err.printf("diff_lookup[%d]=%d ", step * 16 + nib, diff_lookup[step * 16 + nib]);
                }
            }
        }

        /**
         * update the Sound chip so that it is in sync with CPU execution
         */
        public void update(int[][] outputs, int samples) {
            int[] bufL = outputs[0];
            int[] bufR = outputs[1];
            int ind = 0;

            //memset(outputs[0], 0, samples * sizeof(*outputs[0]));

            if ((this.status & STATUS_PLAYING) != 0) {
                int nibbleShift = this.nibbleShift;

                while (samples != 0) {
                    //System.System.err.printf("status={0} this.nibbleShift={1} ", this.status, this.nibbleShift);
                    /* Compute the new amplitude and update the current step */
                    //int nibble = (this.data_in >> nibbleShift) & 0xf;
                    int nibble;
                    short sample;

                    //System.System.err.printf("this.data_empty={0} ", this.data_empty);
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

                    /* Output to the buffer */
                    //INT16 sample = clock_adpcm(chip, nibble);
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

                    //*buffer++ = sample;
                    //System.System.err.printf("this.pan={0} sample={1} ", this.pan, sample);
                    bufL[ind] = ((this.pan & 0x02) != 0) ? 0x00 : sample;
                    bufR[ind] = ((this.pan & 0x01) != 0) ? 0x00 : sample;
                    //System.err.printf("001  bufL[{0}]={1}  bufR[{2}]={3}", ind, bufL[ind], ind, bufR[ind]);
                    samples--;
                    ind++;
                }

                /* Update the parameters */
                this.nibbleShift = (byte) nibbleShift;
            } else {
                /* Fill with 0 */
                while ((samples--) != 0) {
//                    System.System.err.printf("passed ");
                    //*buffer++ = 0;
                    bufL[ind] = 0;
                    bufR[ind] = 0;
                    ind++;
                }
            }
        }

        /**
         * Starts emulation of an OKIM6258-compatible chip
         */
        private int start(int clock, int divider, int adpcmType, int output12Bits) {
            this.initialClock = clock;
            this.initialDiv = (byte) divider;
            this.masterClock = clock;
            this.adpcmType = (byte) adpcmType;
            this.clockBuffer[0x00] = (byte) ((clock & 0x000000FF) >> 0);
            this.clockBuffer[0x01] = (byte) ((clock & 0x0000FF00) >> 8);
            this.clockBuffer[0x02] = (byte) ((clock & 0x00FF0000) >> 16);
            this.clockBuffer[0x03] = (byte) ((clock & 0xFF000000) >> 24);

            /* D/A precision is 10-bits but 12-bit data can be output serially to an external DAC */
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

        private void reset() {
            this.masterClock = this.initialClock;
            this.clockBuffer[0x00] = (byte) ((this.initialClock & 0x000000FF) >> 0);
            this.clockBuffer[0x01] = (byte) ((this.initialClock & 0x0000FF00) >> 8);
            this.clockBuffer[0x02] = (byte) ((this.initialClock & 0x00FF0000) >> 16);
            this.clockBuffer[0x03] = (byte) ((this.initialClock & 0xFF000000) >> 24);
            this.divider = dividers[this.initialDiv];
            if (this.smpRateFunc != null) {
                this.smpRateFunc.accept(this.smpRateData, this.getVclk());
                //System.err.printf("passed");
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
        private void setDivider(int val) {
            this.divider = dividers[val];
            if (this.smpRateFunc != null)
                this.smpRateFunc.accept(this.smpRateData, this.getVclk());
        }

        /**
         * set the master clock
         */
        private void setClock(int val) {
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
         * write to the control port of an OKIM6258-compatible chip
         */
        private void data_write(/*offs_t offset, */byte data) {
            if (this.dataEmpty >= 0x02) {
                this.dataBufPos = 0x00;
            }
            this.dataInLast = data;
            this.dataBuf[this.dataBufPos & 0x0F] = data;
            this.dataBufPos += 0x01;
            this.dataBufPos &= 0xf7;
            if ((this.dataBufPos >> 4) == (this.dataBufPos & 0x0F)) {
                //System.err.printf("Warning: FIFO full!\n");
                this.dataBufPos = (byte) ((this.dataBufPos & 0xF0) | ((this.dataBufPos - 1) & 0x07));
            }
            this.dataEmpty = 0x00;
        }

        /**
         * write to the control port of an OKIM6258-compatible chip
         */
        private void ctrl_w(/*offs_t offset, */byte data) {
            if ((data & COMMAND_STOP) != 0) {
                //System.err.printf("COMMAND:STOP");
                //this.status &= (byte)(~((byte)STATUS_PLAYING | (byte)STATUS_RECORDING)));
                this.status &= (byte) (0x2 + 0x4);
                return;
            }

            if ((data & COMMAND_PLAY) != 0) {
                //System.err.printf("COMMAND:PLAY");
                if ((this.status & STATUS_PLAYING) == 0) {
                    this.status |= STATUS_PLAYING;

                    /* Also reset the ADPCM parameters */
                    this.signal = -2;
                    this.step = 0;
                    this.nibbleShift = 0;

                    this.dataBuf[0x00] = data;
                    this.dataBufPos = 0x01;  // write pos 01, read pos 00
                    this.dataEmpty = 0x00;
                }
                this.step = 0;
                this.nibbleShift = 0;
            } else {
                //this.status &= ~STATUS_PLAYING;
                this.status &= 0xd;
            }

            if ((data & COMMAND_RECORD) != 0) {
                //System.err.printf("M6258: Record enabled\n");
                this.status |= STATUS_RECORDING;
            } else {
                //this.status &= ~STATUS_RECORDING;
                this.status &= 0xb;
            }
        }

        private void setClockByte(byte port, byte val) {
            this.clockBuffer[port] = val;
        }

        private void writePan(byte data) {
            this.pan = data;
        }

        private void write(byte port, byte data) {
            //System.System.err.printf("port=%2x data=%2x \n", port, data);
            switch (port) {
            case 0x00:
                ctrl_w(/*0x00, */data);
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

        public void setCallback(dlgSRATE_CALLBACK callbackFunc, MDSound.Chip chip) {
            // set Sample Rate Change Callback routine
            this.smpRateFunc = callbackFunc;
            this.smpRateData = chip;
        }
    }

    private static final int MAX_CHIPS = 0x02;
    public OkiM6258State[] okiM6258Data = new OkiM6258State[] {new OkiM6258State(), new OkiM6258State()};

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        OkiM6258State chip = okiM6258Data[chipID];
        chip.update(outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    private int device_start_okim6258(byte chipID, int clock, int divider, int adpcm_type, int output_12bits) {
        if (chipID >= MAX_CHIPS)
            return 0;

        OkiM6258State info = okiM6258Data[chipID];
        return info.start(clock, divider, adpcm_type, output_12bits);
    }

    /**
     * stop emulation of an OKIM6258-compatible chip
     */
    private void device_stop_okim6258(byte chipID) {
        okiM6258Data[chipID] = null;
    }

    private void device_reset_okim6258(byte chipID) {
        OkiM6258State info = okiM6258Data[chipID];
        info.reset();
    }

    private void okim6258_set_divider(byte chipID, int val) {
        OkiM6258State info = okiM6258Data[chipID];
        info.setDivider(val);
    }

    private void okim6258_set_clock(byte chipID, int val) {
        OkiM6258State info = okiM6258Data[chipID];
        info.setClock(val);
    }

    private int okim6258_get_vclk(byte chipID) {
        OkiM6258State info = okiM6258Data[chipID];
        return info.getVclk();
    }

    /**
     * read the status port of an OKIM6258-compatible chip
     */
        /*byte okim6258_status_r(byte chipID, offs_t offset) {
            OkiM6258State info = &OKIM6258Data[chipID];

            return (info.status & STATUS_PLAYING) ? 0x00 : 0x80;
        }*/

    private void okim6258_data_w(byte chipID, /*offs_t offset, */byte data) {
        OkiM6258State info = okiM6258Data[chipID];
        info.data_write(data);
    }

    private void okim6258_ctrl_w(byte chipID, /*offs_t offset, */byte data) {
        OkiM6258State info = okiM6258Data[chipID];
        info.ctrl_w(data);
    }

    private void okim6258_set_clock_byte(byte chipID, byte Byte, byte val) {
        OkiM6258State info = okiM6258Data[chipID];
        info.setClock(val);
    }

    private void okim6258_pan_w(byte chipID, byte data) {
        OkiM6258State info = okiM6258Data[chipID];
        info.writePan(data);
    }

    /**
     * Generic get_info
     */
        /*DEVICE_GET_INFO( OkiM6258 ) {
            switch (state) {
                // --- the following bits of info are returned as 64-bit signed integers --- //
                case DEVINFO_INT_TOKEN_BYTES:     info.i = sizeof(OkiM6258State);   break;

                // --- the following bits of info are returned as pointers to data or functions --- //
                case DEVINFO_FCT_START:       info.start = DEVICE_START_NAME(OkiM6258);  break;
                case DEVINFO_FCT_STOP:       // nothing //        break;
                case DEVINFO_FCT_RESET:       info.reset = DEVICE_RESET_NAME(OkiM6258);  break;

                // --- the following bits of info are returned as NULL-terminated strings --- //
                case DEVINFO_STR_NAME:       strcpy(info.s, "OKI6258");     break;
                case DEVINFO_STR_FAMILY:     strcpy(info.s, "OKI ADPCM");    break;
                case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");      break;
                case DEVINFO_STR_SOURCE_FILE:      strcpy(info.s, __FILE__);     break;
                case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
            }
        }

        DEFINE_LEGACY_SOUND_DEVICE(OKIM6258, OkiM6258);*/

    public OkiM6258() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
        //0..Main
    }

    @Override
    public int start(byte chipID, int clock) {
        return start(chipID, 44100, clock, 0);
    }

    @Override
    public int start(byte chipID, int samplingrate, int clockValue, Object... option) {
        int divider = ((int) option[0] & 0x03) >> 0;
        int adpcm_type = ((int) option[0] & 0x04) >> 2;
        int output_12bits = ((int) option[0] & 0x08) >> 3;
        return (int) device_start_okim6258(chipID, clockValue, divider, adpcm_type, output_12bits);
    }

    @Override
    public String getName() {
        return "OKIM6258";
    }

    @Override
    public String getShortName() {
        return "OKI5";
    }

    @Override
    public void stop(byte chipID) {
        device_stop_okim6258(chipID);
    }

    @Override
    public void reset(byte chipID) {
        device_reset_okim6258(chipID);
    }

    private void okim6258_write(byte chipID, byte port, byte data) {
        OkiM6258State info = okiM6258Data[chipID];
        info.write(port, data);
    }

    public void okim6258_set_options(int options) {
        OkiM6258State.internal10Bit = (byte) ((options >> 0) & 0x01);
    }

    public void okim6258_set_srchg_cb(byte chipID, OkiM6258State.dlgSRATE_CALLBACK callbackFunc, MDSound.Chip chip) {
        OkiM6258State info = okiM6258Data[chipID];
        info.setCallback(callbackFunc, chip);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        okim6258_write(chipID, (byte) adr, (byte) data);
        return 0;
    }
}
