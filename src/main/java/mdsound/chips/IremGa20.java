package mdsound.chips;

import java.util.Arrays;


/**
 * Irem GA20 PCM Sound Chip
 * <p>
 * It's not currently known whether this chips is stereo.
 * <p>
 * <p>
 * Revisions:
 * <p>
 * 04-15-2002 Acho A. Tang
 * - rewrote channel mixing
 * - added prelimenary volume and sample rate emulation
 * <p>
 * 05-30-2002 Acho A. Tang
 * - applied hyperbolic gain control to volume and used
 * a musical-note style progression in sample rate
 * calculation(still very inaccurate)
 * <p>
 * 02-18-2004 R. Belmont
 * - sample rate calculation reverse-engineered.
 * Thanks to Fujix, Yasuhiro Ogawa, the Guru, and Tormod
 * for real PCB samples that made this possible.
 * <p>
 * 02-03-2007 R. Belmont
 * - Cleaned up faux x86 assembly.
 */
public class IremGa20 {

    private static final int MAX_VOL = 256;

    private static class Channel {
        private int rate;
        //int size;
        private int start;
        private int pos;
        private int frac;
        private int end;
        private int volume;
        private int pan;
        //int effect;
        private byte play;
        private byte muted;

        private void reset() {
            this.rate = 0;
            //this.size = 0;
            this.start = 0;
            this.pos = 0;
            this.frac = 0;
            this.end = 0;
            this.volume = 0;
            this.pan = 0;
            //this.effect = 0;
            this.play = 0;
        }

        private void write(int offset, byte data) {
            switch (offset & 0x7) {
            case 0: // start address low
                this.start = ((this.start) & 0xff000) | (data << 4);
                break;

            case 1: // start address high
                this.start = ((this.start) & 0x00ff0) | (data << 12);
                break;

            case 2: // end address low
                this.end = ((this.end) & 0xff000) | (data << 4);
                break;

            case 3: // end address high
                this.end = ((this.end) & 0x00ff0) | (data << 12);
                break;

            case 4:
                this.rate = 0x1000000 / (256 - data);
                break;

            case 5: // AT: gain control
                this.volume = (data * MAX_VOL) / (data + 10);
                break;

            case 6: // AT: this is always written 2(enabling both channels?)
                this.play = data;
                this.pos = this.start;
                this.frac = 0;
                break;
            }
        }
    }

    private byte[] rom;
    private int romSize;
    private int[] regs = new int[0x40];
    private Channel[] channel = new Channel[] {
            new Channel(),
            new Channel(),
            new Channel(),
            new Channel()
    };

    public void update(int[][] outputs, int samples) {
        class Update {
            int rate, pos, frac, end, vol, play;

            Update(Channel ch) {
                rate = ch.rate;
                pos = ch.pos;
                frac = ch.frac;
                end = ch.end - 0x20;
                vol = ch.volume;
                play = (int) ((ch.muted == 0) ? ch.play : 0);
            }

            private int update() {
                int sample = 0;
                if (this.play != 0) {
                    sample = ((rom[this.pos] & 0xff) - 0x80) * this.vol;
                    this.frac += this.rate;
                    this.pos += this.frac >> 24;
                    this.frac &= 0xffffff;
                    this.play = (this.pos < this.end) ? 1 : 0;
                }
                return sample;
            }

            private void finish(Channel ch) {
                ch.pos = this.pos;
                ch.frac = this.frac;
                if (ch.muted == 0)
                    ch.play = (byte) this.play;
            }
        }

        Update[] us = new Update[4];

        // precache some values
        for (int c = 0; c < 4; c++) {
            us[c] = new Update(this.channel[c]);
        }

        for (int i = 0; i < samples; i++) {
            int sampleOut = 0;
            // update the 4 channels inline
            for (int c = 0; c < 4; c++) {
                sampleOut += us[c].update();
            }
            sampleOut >>= 2;
            outputs[0][i] = sampleOut; // L
            outputs[1][i] = sampleOut; // R
        }

        /* update the regs now */
        for (int c = 0; c < 4; c++) {
            us[c].finish(this.channel[c]);
        }
    }

    public void write(int offset, byte data) {
//        Debug.printf("GA20:  Offset %02x, data %04x\n",offset,data);

        int channel = offset >> 3;

        this.regs[offset] = data;

        this.channel[channel].write(offset, data);
    }

    public byte read(int offset) {
        int channel = offset >> 3;

        switch (offset & 0x7) {
        case 7: // Voice status.  bit 0 is 1 if active. (routine around 0xccc in rtypeleo)
            return (byte) (this.channel[channel].play != 0 ? 1 : 0);

        default:
            //Debug.printf("GA20: read unk. register %d, channel %d\n", offset & 0xf, channel);
            break;
        }

        return 0;
    }

    private void resetChannels() {
        for (int i = 0; i < 4; i++) {
            this.channel[i].reset();
        }
    }

    public void reset() {
        resetChannels();
        Arrays.fill(this.regs, 0, 0x40, 0x00);
    }

    public int start(int clock) {
        /* Initialize our chips structure */
        this.rom = null;
        this.romSize = 0x00;

        resetChannels();

        for (int i = 0; i < 0x40; i++)
            this.regs[i] = 0;

        for (int i = 0; i < 4; i++)
            this.channel[i].muted = 0x00;

        return clock / 4;
    }

    public void stop() {
        this.rom = null;
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
        if (this.romSize != romSize) {
            this.rom = new byte[romSize];
            this.romSize = romSize;

            Arrays.fill(this.rom, 0, romSize, (byte) 0xff);
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        System.arraycopy(romData, 0, this.rom, dataStart, dataLength);
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        if (this.romSize != romSize) {
            this.rom = new byte[romSize];
            this.romSize = romSize;

            Arrays.fill(this.rom, 0, romSize, (byte) 0xff);
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        System.arraycopy(romData, srcStartAddress, this.rom, dataStart, dataLength);
    }

    public void setMuteMask(int muteMask) {
        for (byte curChn = 0; curChn < 4; curChn++)
            this.channel[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
    }
}
