package mdsound;

import java.util.function.BiConsumer;


/**
 * streaming ADPCM driver
 * by Aaron Giles
 * <p>
 * Library to transcode from an ADPCM source to raw PCM.
 * Written by Buffoni Mirko in 08/06/97
 * References: various sources and documents.
 * <p>
 * HJB 08/31/98
 * modified to use an automatically selected oversampling factor
 * for the current sample rate
 * <p>
 * Mish 21/7/99
 * Updated to allow multiple OKI chips with different sample rates
 * <p>
 * R. Belmont 31/10/2003
 * Updated to allow a driver to use both MSM6295s and "raw" ADPCM voices (gcpinbal)
 * Also added some error trapping for MAME_DEBUG builds
 */
public class OkiM6295 extends Instrument.BaseInstrument {

    public OkiM6295() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
        //0..Main
    }

    @Override
    public int start(byte chipID, int clock) {
        return device_start_okim6295(chipID, (int) clock);
    }

    @Override
    public int start(byte chipID, int samplingrate, int clockValue, Object... option) {
        return device_start_okim6295(chipID, (int) clockValue);
    }

    @Override
    public void stop(byte chipID) {
        device_stop_okim6295(chipID);
    }

    @Override
    public void reset(byte chipID) {
        device_reset_okim6295(chipID);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        okim6295_update(chipID, outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    /**
     *  OKIM 6295 ADPCM chip:
     *
     *  Command bytes are sent:
     *
     *      1xxx xxxx = start of 2-byte command sequence, xxxxxxx is the sample number to trigger
     *      abcd vvvv = second half of command; one of the abcd bits is set to indicate which Voice
     *                  the v bits seem to be volumed
     *
     *      0abc d000 = stop playing; one or more of the abcd bits is set to indicate which Voice(s)
     *
     *  Status is read:
     *
     *      ???? abcd = one bit per Voice, set to 0 if nothing is playing, or 1 if it is active
     */
    public static class OkiM6295State {

        public static final int VOICES = 4;

        public Voice[] voices = new Voice[] {
                new Voice(), new Voice(), new Voice(), new Voice()
        };

        public static class Adpcm {
            public int signal;
            public int step;

            /* step size index shift table */
            private static final int[] index_shift = new int[] {-1, -1, -1, -1, 2, 4, 6, 8};

            /* lookup table for the precomputed difference */
            private static int[] diff_lookup = new int[49 * 16];

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
                    int stepval = (int) Math.floor(16.0 * Math.pow(11.0 / 10.0, (double) step));

                    /* loop over all nibbles and compute the difference */
                    for (int nib = 0; nib < 16; nib++) {
                        diff_lookup[step * 16 + nib] = nbl2bit[nib][0] *
                                (stepval * nbl2bit[nib][1] +
                                        stepval / 2 * nbl2bit[nib][2] +
                                        stepval / 4 * nbl2bit[nib][3] +
                                        stepval / 8);
                    }
                }
            }

            /**
             * reset the ADPCM stream
             */
            public void reset() {
                /* reset the signal/step */
                this.signal = -2;
                this.step = 0;
            }

            /**
             * clock the next ADPCM byte
             */
            public short clock(byte nibble) {
//            System.err.printf("nibble=%d diff_lookup[%d]=%d\n", nibble, this.step * 16 + (nibble & 15), diff_lookup[this.step * 16 + (nibble & 15)]);
//            System.err.printf("1this.signal=%d\n", this.signal);
                this.signal += diff_lookup[this.step * 16 + (nibble & 15)];

                /* clamp to the maximum */
                if (this.signal > 2047)
                    this.signal = 2047;
                else if (this.signal < -2048)
                    this.signal = -2048;

//            System.err.printf("2this.signal=%d\n", this.signal);
                /* adjust the step size and clamp */
                this.step += index_shift[nibble & 7];
//            System.err.printf("3this.signal=%d\n", this.signal);
                if (this.step > 48)
                    this.step = 48;
                else if (this.step < 0)
                    this.step = 0;

//            System.err.printf("4this.signal=%d\n", this.signal);
                /* return the signal */
                return (short) this.signal;
            }
        }

        /**
         * volume lookup table. The manual lists only 9 steps, ~3dB per step. Given the dB values,
           that seems to map to a 5-bit volume control. Any volume parameter beyond the 9th index
           results in silent playback. */
        private static final int[] volume_table = new int[] {
                0x20, //   0 dB
                0x16, //  -3.2 dB
                0x10, //  -6.0 dB
                0x0b, //  -9.2 dB
                0x08, // -12.0 dB
                0x06, // -14.5 dB
                0x04, // -18.0 dB
                0x03, // -20.5 dB
                0x02, // -24.0 dB
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
        };

        // that's enough for VGMPlay's update rate
        private static final int MAX_SAMPLE_CHUNK = 0x10;

        /* struct describing a single playing ADPCM Voice */
        public static class Voice {
            /* 1 if we are actively playing */
            public byte playing;

            /* pointer to the base memory location */
            public int baseOffset;
            /* current sample number */
            public int sample;
            /* total samples to play */
            public int count;

            public Adpcm adpcm = new Adpcm();/* current ADPCM state */
            public int volume;          /* output volume */
            public byte Muted;
        }

        //running_device *device;
        public int command;
        public byte bank_installed;
        public int bankOffs;
        public byte pin7State;
        public byte nmkMode;
        public byte[] nmkBank = new byte[4];
        //sound_stream *stream; /* which stream are we playing on? */
        public int masterClock;    /* master clock frequency */
        public int initialClock;

        public int romSize = 0;
        public int ptrROM;
        public byte[] ROM;

        public dlgSRATE_CALLBACK smpRateFunc;
        public MDSound.Chip smpRateData;

        public interface dlgSRATE_CALLBACK extends BiConsumer<MDSound.Chip, Integer> {
        }

        /*
         * general ADPCM decoding routine
         */

        private static final int NMK_BNKTBLBITS = 8;
        private static final int NMK_BNKTBLSIZE = 0x100;//(1 << NMK_BNKTBLBITS);  // 0x100
        private static final int NMK_TABLESIZE = (4 * NMK_BNKTBLSIZE);    // 0x400
        private static final int NMK_TABLEMASK = (NMK_TABLESIZE - 1);     // 0x3FF

        private static final int NMK_BANKBITS = 16;
        private static final int NMK_BANKSIZE = 0x10000;//(1 << NMK_BANKBITS);      // 0x10000
        private static final int NMK_BANKMASK = (NMK_BANKSIZE - 1);       // 0xFFFF
        private static final int NMK_ROMBASE = (4 * NMK_BANKSIZE);        // 0x40000

        private byte memory_raw_read_byte(int offset) {
            int curOfs;

            if (this.nmkMode == 0) {
                curOfs = this.bankOffs | offset;
            } else {
                byte BankID;

                if (offset < NMK_TABLESIZE && (this.nmkMode & 0x80) != 0) {
                    // pages sample table
                    BankID = (byte) (offset >> NMK_BNKTBLBITS);
                    curOfs = offset & NMK_TABLEMASK;    // 0x3FF, not 0xFF
                } else {
                    BankID = (byte) (offset >> NMK_BANKBITS);
                    curOfs = offset & NMK_BANKMASK;
                }
                curOfs |= (this.nmkBank[BankID & 0x03] << NMK_BANKBITS);
                // I modified MAME to write a clean sample ROM.
                // (Usually it moves the data by NMK_ROMBASE.)
                //curOfs += NMK_ROMBASE;
            }
            if (curOfs < this.romSize)
                return this.ROM[curOfs];
            else
                return 0x00;
        }

        private void generate_adpcm(Voice voice, short[] buffer, int samples) {
            int ptrBuffer = 0;

            /* if this Voice is active */
            if (voice.playing != 0) {
                //System.err.printf("base_offset[%x] sample[%x] count[%x]\n", Voice.base_offset, Voice.sample, Voice.count);
                int iBase = voice.baseOffset;
                int sample = voice.sample;
                int count = voice.count;

                /* loop while we still have samples to generate */
                while (samples != 0) {
                    /* compute the new amplitude and update the current step */
                    //int nibble = memory_raw_read_byte(this.device.space(), base + sample / 2) >> (((sample & 1) << 2) ^ 4);
                    //System.err.printf("nibblecal1[%d]2[%d]\n", iBase + sample / 2, (((sample & 1) << 2) ^ 4));
                    byte nibble = (byte) (memory_raw_read_byte(iBase + sample / 2) >> (((sample & 1) << 2) ^ 4));
                    //System.err.printf( "nibble[%x]\n", nibble);

                    /* output to the buffer, scaling by the volume */
                    /* signal in range -2048..2047, volume in range 2..32 => signal * volume / 2 in range -32768..32767 */
                    buffer[ptrBuffer++] = (short) (voice.adpcm.clock(nibble) * voice.volume / 2);
                    //System.err.printf("*buffer[%d]\n", buffer[ptrBuffer-1]);
                    samples--;

                    /* next! */
                    if (++sample >= count) {
                        voice.playing = 0;
                        break;
                    }
                }

                /* update the parameters */
                voice.sample = sample;
            }

            /* fill the rest with silence */
            while (samples-- != 0) {
                buffer[ptrBuffer++] = 0;
            }
        }

        private void update(int[][] outputs, int samples) {
            //System.err.printf("samples:%d\n"        , samples);
            for (int i = 0; i < samples; i++) {
                outputs[0][i] = 0;
            }

            for (int i = 0; i < VOICES; i++) {
                Voice voice = this.voices[i];
                chInfo.chInfo[i].mask = voice.Muted == 0;
                if (voice.Muted == 0) {
                    int[][] buffer = outputs;
                    int ptrBuffer = 0;
                    short[] sample_data = new short[MAX_SAMPLE_CHUNK];
                    int remaining = samples;

                    /* loop while we have samples remaining */
                    while (remaining != 0) {
                        int Samples = Math.min(remaining, MAX_SAMPLE_CHUNK);
                        int samp;

                        generate_adpcm(voice, sample_data, Samples);
                        for (samp = 0; samp < Samples; samp++) {
                            buffer[0][ptrBuffer++] += sample_data[samp];
                            //if (sample_data[samp] != 0) {
                            //    System.err.printf("ch:%d sampledata[%d]=%d count:%d sample:%d"
                            //    , i, samp, sample_data[samp]
                            //    , Voice.count, Voice.sample);
                            //}
                        }

                        remaining -= samples;
                    }
                }
            }

            if (samples >= 0) System.arraycopy(outputs[0], 0, outputs[1], 0, samples);
        }

        private int start(int clock) {
            this.command = -1;
            this.bankOffs = 0;
            this.nmkMode = 0x00;
            //memset(this.nmk_bank, 0x00, 4 * sizeof((int)8));
            for (int i = 0; i < 4; i++) {
                this.nmkBank[i] = 0x00;
            }

            this.initialClock = clock;
            this.masterClock = clock & 0x7FFF_FFFF;
            this.pin7State = (byte) (((int) clock & 0x80000000) >> 31);
            chInfo.masterClock = this.masterClock;
            chInfo.pin7State = this.pin7State;

            /* generate the name and create the stream */
            int divisor = this.pin7State != 0 ? 132 : 165;

            return this.masterClock / divisor;
        }

        private void stop() {
            this.ROM = null;
            this.romSize = 0x00;
        }

        private void reset() {
            this.command = -1;
            this.bankOffs = 0;
            this.nmkMode = 0x00;
            //memset(this.nmk_bank, 0x00, 4 * sizeof((int)8));
            for (int i = 0; i < 4; i++) {
                this.nmkBank[i] = 0x00;
            }
            this.masterClock = this.initialClock & 0x7FFFFFFF;
            chInfo.masterClock = this.masterClock;
            this.pin7State = (byte) ((this.initialClock & 0x80000000) >> 31);
            chInfo.pin7State = (byte) ((this.initialClock & 0x80000000) >> 31);

            for (int voice = 0; voice < OkiM6295State.VOICES; voice++) {
                this.voices[voice].volume = 0;
                this.voices[voice].adpcm.reset();

                this.voices[voice].playing = 0;
            }
        }

        /**
         * set the base of the bank for a given Voice on a given chip
         */
        private void setBankBase(int iBase) {

            /* if we are setting a non-zero base, and we have no bank, allocate one */
            //if (this.bank_installed == 0 && iBase != 0)
            //{
            /* @Override our memory map with a bank */
            //memory_install_read_bank(device.space(), 0x00000, 0x3ffff, 0, 0, device.tag());
            //this.bank_installed = 1;// TRUE;
            //}

            /* if we have a bank number, set the base pointer */
            //if (this.bank_installed != 0)
            //{
            //this.bank_offs = iBase;
            //memory_set_bankptr(device.machine, device.tag(), device.region.super.u8 + base);
            //}
            this.bankOffs = iBase;
        }

        private void clockChanged() {
            int divisor;
            divisor = this.pin7State != 0 ? 132 : 165;
            this.smpRateFunc.accept(this.smpRateData, this.masterClock / divisor);
        }

        /**
         * adjust pin 7, which controls the internal clock division
         */
        private void setPin7(int pin7) {
            //int divisor = pin7 ? 132 : 165;

            chInfo.pin7State = (byte) pin7;
            this.pin7State = (byte) pin7;
            clockChanged();
        }

        private byte read(int offset) {
            int result = 0xf0;  /* naname expects bits 4-7 to be 1 */

            /* set the bit to 1 if something is playing on a given channel */
            for (int i = 0; i < VOICES; i++) {
                Voice voice = this.voices[i];

                /* set the bit if it's playing */
                if (voice.playing != 0)
                    result |= 1 << i;
            }

            return (byte) result;
        }

        /**
         * write to the data port of an OKIM6295-compatible chip
         */
        private void writeCommand(byte data) {
            /* if a command is pending, process the second half */
            if (this.command != -1) {
                int temp = data >> 4, i, start, stop;
                int iBase;

                /* the manual explicitly says that it's not possible to start multiple voices at the same time */
//    if (temp != 0 && temp != 1 && temp != 2 && temp != 4 && temp != 8)
//     System.err.printf("OKI6295 start %x contact MAMEDEV\n", temp);

                /* determine which Voice(s) (Voice is set by a 1 bit in the upper 4 bits of the second byte) */
                for (i = 0; i < VOICES; i++, temp >>= 1) {
                    if ((temp & 1) != 0) {
                        Voice voice = this.voices[i];

                        /* determine the start/stop positions */
                        iBase = this.command * 8;

                        //start  = memory_raw_read_byte(device.space(), base + 0) << 16;
                        start = memory_raw_read_byte(iBase + 0) << 16;
                        start |= memory_raw_read_byte(iBase + 1) << 8;
                        start |= memory_raw_read_byte(iBase + 2) << 0;
                        start &= 0x3ffff;
                        chInfo.chInfo[i].stAdr = start;

                        stop = memory_raw_read_byte(iBase + 3) << 16;
                        stop |= memory_raw_read_byte(iBase + 4) << 8;
                        stop |= memory_raw_read_byte(iBase + 5) << 0;
                        stop &= 0x3ffff;
                        chInfo.chInfo[i].edAdr = stop;

                        /* set up the Voice to play this sample */
                        if (start < stop) {
                            if (voice.playing == 0) /* fixes Got-cha and Steel Force */ {
                                voice.playing = 1;
                                voice.baseOffset = start;
                                voice.sample = 0;
                                voice.count = 2 * (stop - start + 1);

                                /* also reset the ADPCM parameters */
                                voice.adpcm.reset();
                                voice.volume = volume_table[data & 0x0f];
                                chInfo.keyon[i] = true;
                            } else {
                                //System.err.printf("OKIM6295:'%s' requested to play sample %02x on non-stopped Voice\n",device.tag(),this.command);
                                // just displays warnings when seeking
                                //System.err.printf("OKIM6295: Voice %u requested to play sample %02x on non-stopped Voice\n",i,this.command);
                            }
                        }
                        /* invalid samples go here */
                        else {
                            //System.err.printf("OKIM6295:'%s' requested to play invalid sample %02x\n",device.tag(),this.command);
                            //System.err.printf("OKIM6295: Voice %d  requested to play invalid sample {1:X2} StartAddr {2:X} StopAdr {3:X} \n", i, this.command, start, stop);
                            voice.playing = 0;
                        }
                    }
                }

                /* reset the command */
                this.command = -1;
            } else if ((data & 0x80) != 0) {
                /* if this is the start of a command, remember the sample number for next time */
                this.command = data & 0x7f;
            } else {
                /* otherwise, see if this is a silence command */
                int temp = data >> 3, i;

                /* update the stream, then turn it off */
                //stream_update(this.stream);

                /* determine which Voice(s) (Voice is set by a 1 bit in bits 3-6 of the command */
                for (i = 0; i < OkiM6295State.VOICES; i++, temp >>= 1) {
                    if ((temp & 1) != 0) {
                        Voice voice = this.voices[i];

                        voice.playing = 0;
                    }
                }
            }
        }

        private void write(int offset, byte data) {
            switch (offset) {
            case 0x00:
                writeCommand(data);
                break;
            case 0x08:
                this.masterClock &= ~((int) 0x000000FF);
                this.masterClock |= data << 0;
                chInfo.masterClock = this.masterClock;
                break;
            case 0x09:
                this.masterClock &= ~((int) 0x0000FF00);
                this.masterClock |= data << 8;
                chInfo.masterClock = this.masterClock;
                break;
            case 0x0A:
                this.masterClock &= ~((int) 0x00FF0000);
                this.masterClock |= data << 16;
                chInfo.masterClock = this.masterClock;
                break;
            case 0x0B:
                data &= 0x7F;
                this.masterClock &= ~((int) 0xFF000000);
                this.masterClock |= data << 24;
                clockChanged();
                chInfo.masterClock = this.masterClock;
                break;
            case 0x0C:
                setPin7(data);
                break;
            case 0x0E:  // NMK112 bank switch enable
                this.nmkMode = data;
                break;
            case 0x0F:
                setBankBase(data << 18);
                break;
            case 0x10:
            case 0x11:
            case 0x12:
            case 0x13:
                this.nmkBank[offset & 0x03] = data;
                chInfo.nmkBank[offset & 0x03] = data;
                break;
            }
        }

        public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
            if (this.romSize != romSize) {
                this.ROM = new byte[romSize];// (byte*)realloc(this.ROM, romSize);
                this.romSize = (int) romSize;
                //printf("OKIM6295: New ROM Size: 0x%05X\n", romSize);
                //memset(this.ROM, 0xFF, romSize);
                for (int i = 0; i < romSize; i++) {
                    this.ROM[i] = (byte) 0xff;
                }
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            if (dataLength >= 0) System.arraycopy(romData, 0, this.ROM, 0 + dataStart, dataLength);
        }

        public void writeRom2(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddr) {
            //System.err.printf("OKIM6295:okim6295_write_rom2: chipID:%d romSize:{1:X} dataStart:{2:X} dataLength:{3:X} srcStartAddr:{4:X}\n", chipID, romSize, dataStart, dataLength, srcStartAddr);
            if (this.romSize != romSize) {
                this.ROM = new byte[romSize];// (byte*)realloc(this.ROM, romSize);
                this.romSize = (int) romSize;
                //printf("OKIM6295: New ROM Size: 0x%05X\n", romSize);
                //memset(this.ROM, 0xFF, romSize);
                for (int i = 0; i < romSize; i++) {
                    this.ROM[i] = (byte) 0xff;
                }
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            //System.err.printf("{0:X02} ", this.ROM[i + dataStart]);
            if (dataLength >= 0) System.arraycopy(romData, 0 + srcStartAddr, this.ROM, 0 + dataStart, dataLength);
        }

        public void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < OkiM6295State.VOICES; curChn++)
                this.voices[curChn].Muted = (byte) ((muteMask >> curChn) & 0x01);
        }

        public void setCallback(dlgSRATE_CALLBACK callbackFunc, MDSound.Chip dataPtr) {
            // set Sample Rate Change Callback routine
            this.smpRateFunc = callbackFunc;
            this.smpRateData = dataPtr;
        }

        public static class ChannelInfo {
            public static class Channel {
                public boolean mask = false;
                public int stAdr = 0;
                public int edAdr = 0;
            }

            public int masterClock = 0;
            public byte pin7State = 0;
            public byte[] nmkBank = new byte[4];
            public boolean[] keyon = new boolean[4];
            public Channel[] chInfo = new Channel[] {new Channel(), new Channel(), new Channel(), new Channel()};
        }

        public ChannelInfo readChInfo() {
            chInfo.keyon[0] = false;
            chInfo.keyon[1] = false;
            chInfo.keyon[2] = false;
            chInfo.keyon[3] = false;
            return chInfo;
        }

        private ChannelInfo chInfo = new ChannelInfo();
    }

    private static final int MAX_CHIPS = 0x02;
    public OkiM6295State[] OKIM6295Data = new OkiM6295State[] {new OkiM6295State(), new OkiM6295State()};

    @Override
    public String getName() {
        return "OKIM6295";
    }

    @Override
    public String getShortName() {
        return "OKI9";
    }

    /**
     * update the Sound chip so that it is in sync with CPU execution
     */
    private void okim6295_update(byte chipID, int[][] outputs, int samples) {
        OkiM6295State chip = OKIM6295Data[chipID];
        chip.update(outputs, samples);
    }

    /**
     * start emulation of an OKIM6295-compatible chip
     */
    private int device_start_okim6295(byte chipID, int clock) {
        if (chipID >= MAX_CHIPS)
            return 0;

        OkiM6295State info = OKIM6295Data[chipID];
        return info.start(clock);
    }

    private void device_stop_okim6295(byte chipID) {
        OkiM6295State chip = OKIM6295Data[chipID];
        chip.stop();
    }

    /**
     * stop emulation of an OKIM6295-compatible chip
     */
    private void device_reset_okim6295(byte chipID) {
        OkiM6295State info = OKIM6295Data[chipID];
        info.reset();
    }

    /**
     * read the status port of an OKIM6295-compatible chip
     */
    private byte okim6295_r(byte chipID, int offset) {
        OkiM6295State info = OKIM6295Data[chipID];
        return info.read(offset);
    }

    private void okim6295_w(byte chipID, int offset, byte data) {
        OkiM6295State chip = OKIM6295Data[chipID];
        chip.write(offset, data);
    }

    public void okim6295_write_rom(byte chipID, int romSize, int dataStart, int dataLength, byte[] romData) {
        OkiM6295State chip = OKIM6295Data[chipID];
        chip.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void okim6295_write_rom2(byte chipID, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddr) {
        OkiM6295State chip = OKIM6295Data[chipID];
        chip.writeRom2(romSize, dataStart, dataLength, romData, srcStartAddr);
    }

    public void okim6295_set_mute_mask(byte chipID, int muteMask) {
        OkiM6295State chip = OKIM6295Data[chipID];
        chip.setMuteMask(muteMask);
    }

    public void okim6295_set_srchg_cb(byte chipID, OkiM6295State.dlgSRATE_CALLBACK CallbackFunc, MDSound.Chip DataPtr) {
        OkiM6295State info = OKIM6295Data[chipID];
        info.setCallback(CallbackFunc, DataPtr);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        okim6295_w(chipID, adr, (byte) data);
        return 0;
    }

    public OkiM6295State.ChannelInfo readChInfo(byte chipID) {
        OkiM6295State info = OKIM6295Data[chipID];
        return info.readChInfo();
    }

    /**
         * Generic get_info
         */
        /*DEVICE_GET_INFO( OkiM6295 ) {
   switch (state) {
    // --- the following bits of info are returned as 64-bit signed integers --- //
    case DEVINFO_INT_TOKEN_BYTES:    info.i = sizeof(OkiM6295State);    break;
    case DEVINFO_INT_DATABUS_WIDTH_0:   info.i = 8;         break;
    case DEVINFO_INT_ADDRBUS_WIDTH_0:   info.i = 18;         break;
    case DEVINFO_INT_ADDRBUS_SHIFT_0:   info.i = 0;         break;

    // --- the following bits of info are returned as pointers to data --- //
    case DEVINFO_PTR_DEFAULT_MEMORY_MAP_0:  info.default_map8 = ADDRESS_MAP_NAME(OkiM6295);break;

    // --- the following bits of info are returned as pointers to functions --- //
    case DEVINFO_FCT_START:      info.start = DEVICE_START_NAME( OkiM6295 ); break;
    case DEVINFO_FCT_RESET:      info.reset = DEVICE_RESET_NAME( OkiM6295 ); break;

    // --- the following bits of info are returned as NULL-terminated strings --- //
    case DEVINFO_STR_NAME:      strcpy(info.s, "OKI6295");      break;
    case DEVINFO_STR_FAMILY:     strcpy(info.s, "OKI ADPCM");     break;
    case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
    case DEVINFO_STR_SOURCE_FILE:    strcpy(info.s, __FILE__);      break;
    case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
   }
  }*/
}
