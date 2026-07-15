/*
 * license: BSD-3-Clause
 *
 * copyright-holders: Ernesto Corvi, Alex W. Jackson
 */

package mdsound.chips;

import java.util.Arrays;


/**
 * Konami 053260 PCM Sound Chip (MAME)
 * <p>
 Konami 053260 KDSC

 The 053260 is a four voice PCM/ADPCM sound chip that
 also incorporates four 8-bit ports for communication
 between a main CPU and audio CPU. The chip's output
 is compatible with a YM3012 DAC, and it has a digital
 auxiliary input compatible with the output of a YM2151.
 Some games (e.g. Simpsons) only connect one channel of
 the YM2151, but others (e.g. Thunder Cross II) connect
 both channels for stereo mixing.

 The 053260 has a 21-bit address bus and 8-bit data bus
 to ROM, allowing it to access up to 2 megabytes of
 sample data. Sample data can be either signed 8-bit
 PCM or a custom 4-bit ADPCM format. It is possible for
 two 053260 chips to share access to the same ROMs
 (used by Over Drive)

 The 053260 has separate address and data buses to the
 audio CPU controlling it and to the main CPU. Both data
 buses are 8 bit. The audio CPU address bus has 6 lines
 (64 addressable registers, but fewer than 48 are
 actually used) while the main CPU "bus" has only 1 line
 (2 addressable registers). All registers on the audio
 CPU side seem to be either read-only or write-only,
 although some games write 0 to all the registers in a
 loop at startup (including otherwise read-only or
 entirely unused registers).
 On the main CPU side, reads and writes to the same
 address access different communication ports.

 The sound data ROMs of Simpsons and Vendetta have
 "headers" listing all the samples in the ROM, their
 formats ("PCM" or "KADPCM"), start and end addresses.
 The header data doesn't seem to be used by the hardware
 (none of the other games have headers) but provides
 useful information about the chip.

 2004-02-28 (Oliver Achten)
 Fixed ADPCM decoding. Games sound much better now.

 2014-10-06 (Alex W. Jackson)
 Rewrote from scratch in C++; implemented communication
 ports properly; used the actual up counters instead of
 converting to fractional sample position; fixed ADPCM
 decoding bugs; added documentation.
 *
 * @author Ernesto Corvi
 * @author Alex W. Jackson
 */
public class K053260 {

    private static final int BASE_SHIFT = 16;

    private static class Channel {
        private int rate;
        private int size;
        private int start;
        private int bank;
        private int volume;
        private int play;
        private int dir;
        private int pan;
        private int pos;
        private int loop;
        /* packed PCM ( 4 bit signed ) */
        private int ppcm;
        private int ppcmData;
        private byte muted;

        private void reset() {
            this.rate = 0;
            this.size = 0;
            this.start = 0;
            this.bank = 0;
            this.volume = 0;
            this.play = 0;
            this.dir = 0;
            this.pan = 0;
            this.pos = 0;
            this.loop = 0;
            this.ppcm = 0;
            this.ppcmData = 0;
        }

        private void setup(int r, int v) {
            switch ((r - 8) & 0x07) {
            case 0: // sample rate low
                this.rate &= 0x0f00;
                this.rate |= v;
                break;

            case 1: // sample rate high
                this.rate &= 0x00ff;
                this.rate |= (v & 0x0f) << 8;
                break;

            case 2: // size low
                this.size &= 0xff00;
                this.size |= v;
                break;

            case 3: // size high
                this.size &= 0x00ff;
                this.size |= v << 8;
                break;

            case 4: // start low
                this.start &= 0xff00;
                this.start |= v;
                break;

            case 5: // start high
                this.start &= 0x00ff;
                this.start |= v << 8;
                break;

            case 6: // bank
                this.bank = v & 0xff;
                break;

            case 7: // volume is 7 bits. Convert to 8 bits now.
                this.volume = ((v & 0x7f) << 1) | (v & 1);
                break;
            }
        }

        private void checkBounds(int romSize) {

            int channelStart = (this.bank << 16) + this.start;
            int channelEnd = channelStart + this.size - 1;

            if (channelStart > romSize) {
//logger.log(Level.TRACE, "K53260: Attempting to start playing past the end of the ROM ( start = %06x, end = %06x ).".formatted(channelStart, channelEnd));

                this.play = 0;

                return;
            }

            if (channelEnd > romSize) {
//logger.log(Level.TRACE, "K53260: Attempting to play past the end of the ROM ( start = %06x, end = %06x ).".formatted(channelStart, channelEnd));

                this.size = romSize - channelStart;
            }
//logger.log(Level.TRACE, "K053260: Sample Start = %06x, Sample End = %06x, Sample rate = %04x, PPCM = %s".formatted(channelStart, channelEnd, this.rate, this.pPcm ? "yes" : "no"));
        }
    }

    private int mode;
    private int clock;
    private final int[] regs = new int[0x30];
    private byte[] rom;
    private int romSize;
    private int[] deltaTable;
    private final Channel[] channels = new Channel[] {new Channel(), new Channel(), new Channel(), new Channel()};

    /** for the view: the chip clock, needed to turn a rate into a note. */
    public int getClock() {
        return this.clock;
    }

    /** for the view: sample rate register of channel {@code ch}. */
    public int getRate(int ch) {
        return this.channels[ch].rate;
    }

    /** for the view: sample size of channel {@code ch}. */
    public int getSize(int ch) {
        return this.channels[ch].size;
    }

    /** for the view: sample start address of channel {@code ch}. */
    public int getStart(int ch) {
        return this.channels[ch].start;
    }

    /** for the view: bank of channel {@code ch}. */
    public int getBank(int ch) {
        return this.channels[ch].bank;
    }

    /** for the view: volume of channel {@code ch}. */
    public int getVolume(int ch) {
        return this.channels[ch].volume;
    }

    /** for the view: pan of channel {@code ch}. */
    public int getPan(int ch) {
        return this.channels[ch].pan;
    }

    /** for the view: whether channel {@code ch} is sounding. */
    public int getPlay(int ch) {
        return this.channels[ch].play;
    }

    /** for the view: playback direction of channel {@code ch}. */
    public int getDir(int ch) {
        return this.channels[ch].dir;
    }

    /** for the view: loop flag of channel {@code ch}. */
    public int getLoop(int ch) {
        return this.channels[ch].loop;
    }

    /** for the view: 4bit-PCM flag of channel {@code ch}. */
    public int getPpcm(int ch) {
        return this.channels[ch].ppcm;
    }

    /** for the view: the pitch delta of channel {@code ch}, for the note display. */
    public int getDelta(int ch) {
        return this.deltaTable[this.channels[ch].rate];
    }

    private void initDeltaTable(int rate, int clock) {
        double base = rate;
        double max = clock; /* Hz */

        for (int i = 0; i < 0x1000; i++) {
            double v = 0x1000 - i;
            double target = max / v;
            double fixed = 1 << BASE_SHIFT;

            int val;
            if (target != 0 && base != 0) {
                target = fixed / (base / target);
                val = (int) target;
                if (val == 0)
                    val = 1;
            } else
                val = 1;

            this.deltaTable[i] = val;
        }
    }

    private static int limit(int val, int max, int min) {
        if (val > max)
            val = max;
        else if (val < min)
            val = min;

        return val;
    }

    private static final int MAXOUT = 0x8000;
    private static final int MINOUT = -0x8000;

    private static final byte[] dPcmCnv = new byte[] {0, 1, 2, 4, 8, 16, 32, 64, -128, -64, -32, -16, -8, -4, -2, -1};
    private final int[] lVol = new int[4];
    private final int[] rVol = new int[4];
    private final int[] play = new int[4];
    private final int[] dir = new int[4];
    private final int[] loop = new int[4];
    private final int[] pPcm = new int[4];
    //byte[] rom = new byte[4];
    private final int[] ptrRom = new int[4];
    private final int[] delta = new int[4];
    private final int[] end = new int[4];
    private final int[] pos = new int[4];
    private final byte[] pPcmData = new byte[4];

    public void reset() {
        for (int i = 0; i < 4; i++) {
            this.channels[i].reset();
        }
    }

    public void update(int[][] outputs, int samples) {
        /* precache some values */
        for (int i = 0; i < 4; i++) {
            if (this.channels[i].muted != 0) {
                play[i] = 0;
                continue;
            }
            //rom[i] = this.rom[this.channels[i].start + (this.channels[i].bank << 16)];
            ptrRom[i] = this.channels[i].start + (this.channels[i].bank << 16);
            delta[i] = this.deltaTable[this.channels[i].rate];
            lVol[i] = this.channels[i].volume * this.channels[i].pan;
            rVol[i] = this.channels[i].volume * (8 - this.channels[i].pan);
            end[i] = this.channels[i].size;
            pos[i] = this.channels[i].pos;
            play[i] = this.channels[i].play;
            dir[i] = this.channels[i].dir;
            loop[i] = this.channels[i].loop;
            pPcm[i] = this.channels[i].ppcm;
            pPcmData[i] = (byte) this.channels[i].ppcmData;
            if (pPcm[i] != 0)
                delta[i] /= 2;
        }

        for (int j = 0; j < samples; j++) {

            int dataL = 0, dataR = 0;

            for (int i = 0; i < 4; i++) {
                /* see if the Voice is on */
                if (play[i] != 0) {
                    /* see if we're done */
                    if ((pos[i] >> BASE_SHIFT) >= end[i]) {

                        pPcmData[i] = 0;
                        if (loop[i] != 0)
                            pos[i] = 0;
                        else {
                            play[i] = 0;
                            continue;
                        }
                    }
                    int position = pos[i] >> BASE_SHIFT;

                    byte d;
                    if (pPcm[i] != 0) { // Packed PCM
                        // we only update the signal if we're starting or a real Sound sample has gone by
                        // this is all due to the dynamic sample rate conversion
                        if (pos[i] == 0 || ((pos[i] ^ (pos[i] - delta[i])) & 0x8000) == 0x8000) {
                            int newData;
                            if ((pos[i] & 0x8000) != 0) {
                                newData = ((this.rom[ptrRom[i] + ((dir[i] > 0) ? -position : position)]) >> 4) & 0x0f; // high nybble
                            } else {
                                newData = ((this.rom[ptrRom[i] + ((dir[i] > 0) ? -position : position)])) & 0x0f; // low nybble
                            }

                            pPcmData[i] += dPcmCnv[newData];
                        }


                        d = pPcmData[i];

                        pos[i] += delta[i];
                    } else { // PCM
                        d = this.rom[ptrRom[i] + ((dir[i] > 0) ? -position : position)];

                        pos[i] += delta[i];
                    }

                    if ((this.mode & 2) != 0) {
                        dataL += (d * lVol[i]) >> 2;
                        dataR += (d * rVol[i]) >> 2;
                    }
                }
            }

            outputs[1][j] = limit(dataL, MAXOUT, MINOUT);
            outputs[0][j] = limit(dataR, MAXOUT, MINOUT);
        }

        // update the regs now
        for (int i = 0; i < 4; i++) {
            if (this.channels[i].muted != 0)
                continue;
            this.channels[i].pos = pos[i];
            this.channels[i].play = play[i];
            this.channels[i].ppcmData = pPcmData[i];
        }
    }

    public int start(int clock) {

        int rate = clock / 32;

        // Initialize our chips structure

        this.mode = 0;
        this.clock = clock;

        this.rom = null;
        this.romSize = 0x00;

        // has to be done by the player after calling device_start
        //DEVICE_RESET_CALL(k053260);

        for (int i = 0; i < 0x30; i++)
            this.regs[i] = 0;

        this.deltaTable = new int[0x1000];

        initDeltaTable(rate, clock);

        // setup SH1 timer if necessary
        //if (this.intf.irq)
        // device.machine().scheduler().timer_pulse( attotime::from_hz(device.clock()) * 32, this.intf.irq, "this.intf.irq" );

        for (int i = 0; i < 4; i++)
            this.channels[i].muted = 0x00;

        return rate;
    }

    public void stop() {
        this.rom = null;
    }

    public void write(int offset, int data) {

        if (offset > 0x2f) {
//logger.log(Level.TRACE, "K053260: Writing past registers\n");
            return;
        }

        //this.channel.update();

        // before we update the regs, we need to check for a latched reg
        if (offset == 0x28) {
            int t = this.regs[offset] ^ data;

            for (int c = 0; c < 4; c++) {
                this.channels[c].dir = (data & (16 << c)) != 0 ? 1 : 0;
                if ((t & (1 << c)) != 0) {
                    if ((data & (1 << c)) != 0) {
                        this.channels[c].play = 1;
                        this.channels[c].pos = 0;
                        this.channels[c].ppcmData = 0;
                        this.channels[c].checkBounds(this.romSize);
                    } else
                        this.channels[c].play = 0;
                }
            }

            this.regs[offset] = data;
            return;
        }

        // update regs
        this.regs[offset] = data;

        // communication registers
        if (offset < 8)
            return;

        // channel setup
        if (offset < 0x28) {
            int ch = (offset - 8) / 8;
            this.channels[ch].setup(offset, data);

            return;
        }

        switch (offset) {
        case 0x2a: // loop, pPcm
            for (int c = 0; c < 4; c++)
                this.channels[c].loop = (data & (1 << c)) != 0 ? 1 : 0;

            for (int c = 4; c < 8; c++)
                this.channels[c - 4].ppcm = (data & (1 << c)) != 0 ? 1 : 0;
            break;

        case 0x2c: // pan
            this.channels[0].pan = data & 7;
            this.channels[1].pan = (data >> 3) & 7;
            break;

        case 0x2d: // more pan
            this.channels[2].pan = data & 7;
            this.channels[3].pan = (data >> 3) & 7;
            break;

        case 0x2f: // control
            this.mode = data & 7;
            // bit 0 = read ROM
            // bit 1 = enable Sound output
            // bit 2 = unknown
            break;
        }
    }

    public int read(int offset) {
        switch (offset) {
        case 0x29: { // channel status
            int status = 0;

            for (int c = 0; c < 4; c++)
                status |= this.channels[c].play << c;

            return status;
        }
        //break;

        case 0x2e: // read ROM
            if ((this.mode & 1) != 0) {
                int offs = this.channels[0].start + (this.channels[0].pos >> BASE_SHIFT) + (this.channels[0].bank << 16);

                this.channels[0].pos += (1 << 16);

                if (offs > this.romSize) {
//logger.log(Level.TRACE, "%s: K53260: Attempting to read past ROM size in ROM Read Mode (offs = %06x, size = %06x).".formatted(device.machine().describe_context(),offs,this.rom_size));
//logger.log(Level.TRACE, "K53260: Attempting to read past ROM size in ROM Read Mode (offs = %06x, size = %06x).".formatted(offs, this.rom_size));

                    return 0;
                }

                return this.rom[offs];
            }
            break;
        }

        return this.regs[offset];
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
        writeRom(romSize, dataStart, dataLength, romData, 0);
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        if (this.romSize != romSize) {
            this.rom = new byte[romSize];
            this.romSize = romSize;

            Arrays.fill(this.rom, 0, romSize, (byte) 0xff);
        }

        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        System.arraycopy(romData, srcStartAdr, this.rom, dataStart, dataLength);
    }

    public void setMuteMask(int muteMask) {
        for (byte curChn = 0; curChn < 4; curChn++)
            this.channels[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
    }
}
