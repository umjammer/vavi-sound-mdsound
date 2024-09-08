package mdsound.chips;

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Function;

import mdsound.MDSound;


/**
 * OKIM 6295 ADPCM chips:
 * <p>
 * Command bytes are sent:
 * <p>
 * 1xxx xxxx = start of 2-byte command sequence, xxxxxxx is the sample number to trigger
 * abcd vvvv = second half of command; one of the abcd bits is set to indicate which Voice
 * the v bits seem to be volumed
 * <p>
 * 0abc d000 = stop playing; one or more of the abcd bits is set to indicate which Voice(s)
 * <p>
 * Status is read:
 * <p>
 * ???? abcd = one bit per Voice, set to 0 if nothing is playing, or 1 if it is active
 */
public class OkiM6295 {

    private static final int VOICES = 4;

    private final Voice[] voices = new Voice[] {
            new Voice(), new Voice(), new Voice(), new Voice()
    };

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
    private static class Adpcm {
        private int signal;
        private int step;

        /* step size index shift table */
        private static final int[] indexShift = {-1, -1, -1, -1, 2, 4, 6, 8};

        /* lookup table for the precomputed difference */
        private static int[] diffLookup = new int[49 * 16];

        /*
         * compute the difference tables
         */
        static {
            // nibble to bit map
            int[][] nbl2bit = {
                    {1, 0, 0, 0}, {1, 0, 0, 1}, {1, 0, 1, 0}, {1, 0, 1, 1},
                    {1, 1, 0, 0}, {1, 1, 0, 1}, {1, 1, 1, 0}, {1, 1, 1, 1},
                    {-1, 0, 0, 0}, {-1, 0, 0, 1}, {-1, 0, 1, 0}, {-1, 0, 1, 1},
                    {-1, 1, 0, 0}, {-1, 1, 0, 1}, {-1, 1, 1, 0}, {-1, 1, 1, 1}
            };

            // loop over all possible steps
            for (int step = 0; step <= 48; step++) {
                // compute the step value
                int stepVal = (int) Math.floor(16.0 * Math.pow(11.0 / 10.0, step));

                // loop over all nibbles and compute the difference
                for (int nib = 0; nib < 16; nib++) {
                    diffLookup[step * 16 + nib] = nbl2bit[nib][0] *
                            (stepVal * nbl2bit[nib][1] +
                                    stepVal / 2 * nbl2bit[nib][2] +
                                    stepVal / 4 * nbl2bit[nib][3] +
                                    stepVal / 8);
                }
            }
        }

        /**
         * reset the ADPCM stream
         */
        private void reset() {
            // reset the signal/step
            this.signal = -2;
            this.step = 0;
        }

        /**
         * clock the next ADPCM byte
         */
        private short clock(int nibble) {
//logger.log(Level.DEBUG, String.format("nibble=%d diff_lookup[%d]=%d\n", nibble, this.step * 16 + (nibble & 15), diff_lookup[this.step * 16 + (nibble & 15)]));
//logger.log(Level.DEBUG, String.format("1this.signal=%d\n", this.signal));
            this.signal += diffLookup[this.step * 16 + (nibble & 15)];

            // clamp to the maximum
            if (this.signal > 2047)
                this.signal = 2047;
            else if (this.signal < -2048)
                this.signal = -2048;

//logger.log(Level.DEBUG, String.format("2this.signal=%d\n", this.signal));
            // adjust the step size and clamp
            this.step += indexShift[nibble & 7];
//logger.log(Level.DEBUG, String.format("3this.signal=%d\n", this.signal));
            if (this.step > 48)
                this.step = 48;
            else if (this.step < 0)
                this.step = 0;

//logger.log(Level.DEBUG, String.format("4this.signal=%d\n", this.signal));
            // return the signal
            return (short) this.signal;
        }
    }

    /**
     * volume lookup table. The manual lists only 9 steps, ~3dB per step. Given the dB values,
     * that seems to map to a 5-bit volume control. Any volume parameter beyond the 9th index
     * results in silent playback.
     */
    private static final int[] volumeTable = {
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

    /** that's enough for VGMPlay's update rate */
    private static final int MAX_SAMPLE_CHUNK = 0x10;

    /** struct describing a single playing ADPCM Voice */
    private static class Voice {
        /** 1 if we are actively playing */
        private int playing;

        /** pointer to the base memory location */
        private int baseOffset;
        /** current sample number */
        private int sample;
        /** total samples to play */
        private int count;

        /** current ADPCM state */
        private Adpcm adpcm = new Adpcm();
        /** output volume */
        private int volume;
        private int muted;

        private void generateAdpcm(short[] buffer, int samples, Function<Integer, Integer> read) {
            int ptrBuffer = 0;

            // if this Voice is active
            if (playing != 0) {
                //logger.log(Level.DEBUG, String.format("base_offset[%x] sample[%x] count[%x]\n", Voice.base_offset, Voice.sample, Voice.count);
                int iBase = baseOffset;
                int sample = this.sample;
                int count = this.count;

                // loop while we still have samples to generate
                while (samples != 0) {
                    // compute the new amplitude and update the current step
                    //int nibble = memory_raw_read_byte(this.device.space(), base + sample / 2) >> (((sample & 1) << 2) ^ 4);
                    //logger.log(Level.DEBUG, String.format("nibblecal1[%d]2[%d]\n", iBase + sample / 2, (((sample & 1) << 2) ^ 4));
                    int nibble = read.apply((iBase + sample / 2) >> (((sample & 1) << 2) ^ 4));
                    //logger.log(Level.DEBUG, String.format( "nibble[%x]\n", nibble);

                    // output to the buffer, scaling by the volume
                    // signal in range -2048..2047, volume in range 2..32 => signal * volume / 2 in range -32768..32767
                    buffer[ptrBuffer++] = (short) (adpcm.clock(nibble) * volume / 2);
                    //logger.log(Level.DEBUG, String.format("*buffer[%d]\n", buffer[ptrBuffer-1]);
                    samples--;

                    // next!
                    if (++sample >= count) {
                        playing = 0;
                        break;
                    }
                }

                // update the parameters
                this.sample = sample;
            }

            // fill the rest with silence
            while (samples-- != 0) {
                buffer[ptrBuffer++] = 0;
            }
        }
    }

    private int command;
    private int bankInstalled;
    private int bankOffs;
    private int pin7State;
    private int nmkMode;
    private int[] nmkBank = new int[4];
    /** master clock frequency */
    private int masterClock;
    private int initialClock;

    private int romSize = 0;
    private int ptrROM;
    private byte[] rom;

    private SamplingRateCallback samplingRateFunc;
    private MDSound.Chip smpRateData;

    public interface SamplingRateCallback extends BiConsumer<MDSound.Chip, Integer> {
    }

    // general ADPCM decoding routine

    private static final int NMK_BNKTBLBITS = 8;
    private static final int NMK_BNKTBLSIZE = 0x100;
    private static final int NMK_TABLESIZE = 4 * NMK_BNKTBLSIZE;
    private static final int NMK_TABLEMASK = NMK_TABLESIZE - 1;

    private static final int NMK_BANKBITS = 16;
    private static final int NMK_BANKSIZE = 0x10000;
    private static final int NMK_BANKMASK = NMK_BANKSIZE - 1;
    private static final int NMK_ROMBASE = 4 * NMK_BANKSIZE;

    private int readRawMemoryByte(int offset) {
        int curOfs;

        if (this.nmkMode == 0) {
            curOfs = this.bankOffs | offset;
        } else {
            int bankID;
            if (offset < NMK_TABLESIZE && (this.nmkMode & 0x80) != 0) {
                // pages sample table
                bankID = offset >> NMK_BNKTBLBITS;
                curOfs = offset & NMK_TABLEMASK; // 0x3FF, not 0xff
            } else {
                bankID = offset >> NMK_BANKBITS;
                curOfs = offset & NMK_BANKMASK;
            }
            curOfs |= (this.nmkBank[bankID & 0x03] << NMK_BANKBITS);
            // I modified MAME to write a clean sample ROM.
            // (Usually it moves the data by NMK_ROMBASE.)
            //curOfs += NMK_ROMBASE;
        }
        if (curOfs < this.romSize)
            return this.rom[curOfs] & 0xff;
        else
            return 0x00;
    }

    public void update(int[][] outputs, int samples) {
        //logger.log(Level.DEBUG, String.format("samples:%d\n"        , samples));
        for (int i = 0; i < samples; i++) {
            outputs[0][i] = 0;
        }

        for (int i = 0; i < VOICES; i++) {
            Voice voice = this.voices[i];
            chInfo.chInfo[i].mask = voice.muted == 0;
            if (voice.muted == 0) {
                int ptrBuffer = 0;
                short[] sampleData = new short[MAX_SAMPLE_CHUNK];
                int remaining = samples;

                // loop while we have samples remaining
                while (remaining != 0) {
                    int _samples = Math.min(remaining, MAX_SAMPLE_CHUNK);

                    voice.generateAdpcm(sampleData, _samples, this::readRawMemoryByte);
                    for (int samp = 0; samp < _samples; samp++) {
                        outputs[0][ptrBuffer++] += sampleData[samp];
                        //if (sampleData[samp] != 0) {
                        //    logger.log(Level.DEBUG, String.format("ch:%d sampledata[%d]=%d count:%d sample:%d"
                        //    , i, samp, sampleData[samp]
                        //    , Voice.count, Voice.sample));
                        //}
                    }

                    remaining -= samples;
                }
            }
        }

        System.arraycopy(outputs[0], 0, outputs[1], 0, samples);
    }

    public int start(int clock) {
        this.command = -1;
        this.bankOffs = 0;
        this.nmkMode = 0x00;
        for (int i = 0; i < 4; i++) {
            this.nmkBank[i] = 0x00;
        }

        this.initialClock = clock;
        this.masterClock = clock & 0x7fff_ffff;
        this.pin7State = (clock & 0x8000_0000) >> 31;
        chInfo.masterClock = this.masterClock;
        chInfo.pin7State = this.pin7State;

        // generate the name and create the stream
        int divisor = this.pin7State != 0 ? 132 : 165;

        return this.masterClock / divisor;
    }

    public void stop() {
        this.rom = null;
        this.romSize = 0x00;
    }

    public void reset() {
        this.command = -1;
        this.bankOffs = 0;
        this.nmkMode = 0x00;
        for (int i = 0; i < 4; i++) {
            this.nmkBank[i] = 0x00;
        }
        this.masterClock = this.initialClock & 0x7fff_ffff;
        chInfo.masterClock = this.masterClock;
        this.pin7State = (this.initialClock & 0x8000_0000) >> 31;
        chInfo.pin7State = (this.initialClock & 0x8000_0000) >> 31;

        for (int voice = 0; voice < OkiM6295.VOICES; voice++) {
            this.voices[voice].volume = 0;
            this.voices[voice].adpcm.reset();

            this.voices[voice].playing = 0;
        }
    }

    /**
     * set the base of the bank for a given Voice on a given chips
     */
    private void setBankBase(int iBase) {

        // if we are setting a non-zero base, and we have no bank, allocate one
//        if (this.bank_installed == 0 && iBase != 0) {
//            override our memory map with a bank
//            memory_install_read_bank(device.space(), 0x00000, 0x3ffff, 0, 0, device.tag());
//            this.bank_installed = 1;// TRUE;
//        }

        // if we have a bank number, set the base pointer
//        if (this.bank_installed != 0) {
//            this.bank_offs = iBase;
//            memory_set_bankptr(device.machine, device.tag(), device.region.super.u8 + base);
//        }
        this.bankOffs = iBase;
    }

    private void clockChanged() {
        int divisor;
        divisor = this.pin7State != 0 ? 132 : 165;
        this.samplingRateFunc.accept(this.smpRateData, this.masterClock / divisor);
    }

    /**
     * adjust pin 7, which controls the internal clock division
     */
    private void setPin7(int pin7) {
        //int divisor = pin7 ? 132 : 165;

        chInfo.pin7State = pin7;
        this.pin7State = pin7;
        clockChanged();
    }

    public int read(int offset) {
        int result = 0xf0; // naname expects bits 4-7 to be 1

        // set the bit to 1 if something is playing on a given channel
        for (int i = 0; i < VOICES; i++) {
            Voice voice = this.voices[i];

            // set the bit if it's playing
            if (voice.playing != 0)
                result |= 1 << i;
        }

        return result;
    }

    /**
     * write to the data port of an OKIM6295-compatible chips
     */
    private void writeCommand(int data) {
        // if a command is pending, process the second half
        if (this.command != -1) {
            int temp = data >> 4, i, start, stop;

            // the manual explicitly says that it's not possible to start multiple voices at the same time
//if (temp != 0 && temp != 1 && temp != 2 && temp != 4 && temp != 8)
// logger.log(Level.DEBUG, String.format("OKI6295 start %x contact MAMEDEV\n", temp);

            // determine which Voice(s) (Voice is set by a 1 bit in the upper 4 bits of the second byte)
            for (i = 0; i < VOICES; i++, temp >>= 1) {
                if ((temp & 1) != 0) {
                    Voice voice = this.voices[i];

                    // determine the start/stop positions
                    int iBase = this.command * 8;

                    start = readRawMemoryByte(iBase + 0) << 16;
                    start |= readRawMemoryByte(iBase + 1) << 8;
                    start |= readRawMemoryByte(iBase + 2) << 0;
                    start &= 0x3_ffff;
                    chInfo.chInfo[i].stAdr = start;

                    stop = readRawMemoryByte(iBase + 3) << 16;
                    stop |= readRawMemoryByte(iBase + 4) << 8;
                    stop |= readRawMemoryByte(iBase + 5) << 0;
                    stop &= 0x3_ffff;
                    chInfo.chInfo[i].edAdr = stop;

                    // set up the Voice to play this sample
                    if (start < stop) {
                        if (voice.playing == 0) { // fixes Got-cha and Steel Force
                            voice.playing = 1;
                            voice.baseOffset = start;
                            voice.sample = 0;
                            voice.count = 2 * (stop - start + 1);

                            // also reset the ADPCM parameters
                            voice.adpcm.reset();
                            voice.volume = volumeTable[data & 0x0f];
                            chInfo.keyon[i] = true;
                        } else {
//logger.log(Level.DEBUG, String.format("OKIM6295:'%s' requested to play sample %02x on non-stopped Voice\n",device.tag(),this.command));
                            // just displays warnings when seeking
//logger.log(Level.DEBUG, String.format("OKIM6295: Voice %u requested to play sample %02x on non-stopped Voice\n",i,this.command));
                        }
                    } else { // invalid samples go here
//logger.log(Level.DEBUG, String.format("OKIM6295:'%s' requested to play invalid sample %02x\n",device.tag(),this.command));
//logger.log(Level.DEBUG, String.format("OKIM6295: Voice %d  requested to play invalid sample %2X StartAddr %X StopAdr %X \n", i, this.command, start, stop));
                        voice.playing = 0;
                    }
                }
            }

            // reset the command
            this.command = -1;
        } else if ((data & 0x80) != 0) {
            // if this is the start of a command, remember the sample number for next time
            this.command = data & 0x7f;
        } else {
            // otherwise, see if this is a silence command
            int temp = data >> 3, i;

            // determine which Voice(s) (Voice is set by a 1 bit in bits 3-6 of the command
            for (i = 0; i < VOICES; i++, temp >>= 1) {
                if ((temp & 1) != 0) {
                    Voice voice = this.voices[i];

                    voice.playing = 0;
                }
            }
        }
    }

    public void write(int offset, int data) {
        switch (offset) {
        case 0x00:
            writeCommand(data);
            break;
        case 0x08:
            this.masterClock &= ~((int) 0x0000_00FF);
            this.masterClock |= data << 0;
            chInfo.masterClock = this.masterClock;
            break;
        case 0x09:
            this.masterClock &= ~((int) 0x0000_FF00);
            this.masterClock |= data << 8;
            chInfo.masterClock = this.masterClock;
            break;
        case 0x0A:
            this.masterClock &= ~((int) 0x00FF_0000);
            this.masterClock |= data << 16;
            chInfo.masterClock = this.masterClock;
            break;
        case 0x0B:
            data &= 0x7F;
            this.masterClock &= ~((int) 0xff00_0000);
            this.masterClock |= data << 24;
            clockChanged();
            chInfo.masterClock = this.masterClock;
            break;
        case 0x0C:
            setPin7(data);
            break;
        case 0x0E: // NMK112 bank switch enable
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
            this.rom = new byte[romSize];
            this.romSize = romSize;
//logger.log(Level.DEBUG, String.format("OKIM6295: New ROM Size: 0x%05X\n", romSize));
            Arrays.fill(this.rom, 0, romSize, (byte) 0xff);
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        System.arraycopy(romData, 0, this.rom, dataStart, dataLength);
    }

    public void writeRom2(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddr) {
//logger.log(Level.DEBUG, String.format("OKIM6295::writeRom2: chipId:%d romSize:%x dataStart:%x dataLength:%x srcStartAddr:%x\n", chipId, romSize, dataStart), dataLength, srcStartAddr));
        if (this.romSize != romSize) {
            this.rom = new byte[romSize];
            this.romSize = romSize;
//logger.log(Level.DEBUG, String.format("OKIM6295: New ROM Size: 0x%05X\n", romSize);
            Arrays.fill(this.rom, 0, romSize, (byte) 0xff);
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

//logger.log(Level.DEBUG, String.format("%02x ", this.ROM[i + dataStart]);
        if (dataLength >= 0) System.arraycopy(romData, srcStartAddr, this.rom, dataStart, dataLength);
    }

    public void setMuteMask(int muteMask) {
        for (int curChn = 0; curChn < VOICES; curChn++)
            this.voices[curChn].muted = (muteMask >> curChn) & 0x01;
    }

    public void setCallback(SamplingRateCallback callbackFunc, MDSound.Chip dataPtr) {
        // set Sample Rate Change Callback routine
        this.samplingRateFunc = callbackFunc;
        this.smpRateData = dataPtr;
    }

    public static class ChannelInfo {
        public static class Channel {
            private boolean mask = false;
            public int stAdr = 0;
            public int edAdr = 0;
        }

        public int masterClock = 0;
        public int pin7State = 0;
        public int[] nmkBank = new int[4];
        public boolean[] keyon = new boolean[4];
        public ChannelInfo.Channel[] chInfo = new ChannelInfo.Channel[] {new ChannelInfo.Channel(), new ChannelInfo.Channel(), new ChannelInfo.Channel(), new ChannelInfo.Channel()};
    }

    public ChannelInfo readChInfo() {
        chInfo.keyon[0] = false;
        chInfo.keyon[1] = false;
        chInfo.keyon[2] = false;
        chInfo.keyon[3] = false;
        return chInfo;
    }

    private final ChannelInfo chInfo = new ChannelInfo();
}
