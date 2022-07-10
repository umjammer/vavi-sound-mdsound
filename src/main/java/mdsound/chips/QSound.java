/*
 * Capcom Q-Sound system
 */

package mdsound.chips;


/**
 * Capcom System QSound(tm)
 *
 Driver by Paul Leaman (paul@vortexcomputing.demon.co.uk)
 and Miguel Angel Horna (mahorna@teleline.es)

 A 16 channel stereo sample player.

 QSpace position is simulated by panning the Sound in the stereo space.

 Register
 0  xxbb   xx = unknown bb = start high address
 1  ssss   ssss = sample start address
 2  pitch
 3  unknown (always 0x8000)
 4  loop offset from end address
 5  end
 6  master channel volume
 7  not used
 8  Balance (left=0x0110  centre=0x0120 right=0x0130)
 9  unknown (most fixed samples use 0 for this register)

 Many thanks to CAB (the author of Amuse), without whom this probably would
 never have been finished.

 If anybody has some information about this hardware, please send it to me
 to mahorna@teleline.es or 432937@cepsz.unizar.es.
 http://teleline.terra.es/personal/mahorna
 */
public class QSound {

    /** default 4MHz clock */
    public static final int CLOCK = 4000000;

    /** Clock divider */
    private static final int CLOCKDIV = 166;
    private static final int CHANNELS = 16;

    private static class Channel {
        // bank
        private int bank;
        // start/cur address
        private int address;
        // loop address
        private int loop;
        // end address
        private int end;
        // frequency
        private int freq;
        // master volume
        private int vol;

        // work variables

        // key on / key off
        private byte enabled;
        // left volume
        private int lVol;
        // right volume
        private int rVol;
        // current offset counter
        private int stepPtr;

        private byte muted;

        void update(int[][] outputs, int samples, byte[] sampleRom, int sampleRomLength) {
            if (this.enabled != 0 && this.muted == 0) {
                int[] pOutL = outputs[0];
                int[] pOutR = outputs[1];

                int ptr = 0;
                for (int j = samples - 1; j >= 0; j--) {
                    int advance = (this.stepPtr >> 12);
                    this.stepPtr &= 0xfff;
                    this.stepPtr += this.freq;

                    if (advance != 0) {
                        this.address += advance;
                        if (this.freq != 0 && this.address >= this.end) {
                            if (this.loop != 0) {
                                // Reached the end, restart the loop
                                this.address -= this.loop;

                                // Make sure we don't overflow (what does the real chips do in this case?)
                                if (this.address >= this.end)
                                    this.address = this.end - this.loop;

                                this.address &= 0xffff;
                            } else {
                                // Reached the end of a non-looped sample
                                //this.enabled = 0;
                                this.address--; // ensure that old ripped VGMs still work
                                this.stepPtr += 0x1000;
                                break;
                            }
                        }
                    }

                    int offset = (this.bank | this.address) % sampleRomLength;
                    byte sample = sampleRom[offset];

                    pOutL[ptr] += ((sample * this.lVol * this.vol) >> 14);
                    pOutR[ptr] += ((sample * this.rVol * this.vol) >> 14);
                    ptr++;
                }
            }
        }
    }

    private Channel[] channel = new Channel[CHANNELS];

    /** register latch data */
    private int data;
    /** Q Sound sample ROM */
    private byte[] sampleRom;
    private int sampleRomLength;

    /** Pan volume table */
    private static int[] panTable = new int[33];

    static {
        // Create pan table
        for (int i = 0; i < 33; i++)
            panTable[i] = (int) ((256 / Math.sqrt(32.0)) * Math.sqrt(i));
    }

    public void setCommand(byte address, int data) {
        int ch = 0, reg = 0;

        // direct Sound reg
        if ((address & 0xff) < 0x80) {
            ch = address >> 3;
            reg = address & 0x07;
        }
        // >= 0x80 is probably for the dsp?
        else if ((address & 0xff) < 0x90) {
            ch = address & 0x0F;
            reg = 8;
        } else if ((address & 0xff) >= 0xba && (address & 0xff) < 0xca) {
            ch = address - 0xba;
            reg = 9;
        } else {
            // Unknown registers
            ch = 99;
            reg = 99;
        }

        switch (reg) {
        case 0:
            // bank, high bits unknown
            ch = (ch + 1) & 0x0f; // strange ...
            this.channel[ch].bank = (data & 0x7f) << 16; // Note: The most recent MAME doesn't do "& 0x7F"
//# ifdef _DEBUG
//if (data && !(data & 0x8000))
// Debug.printf("QSound Ch %u: Bank = %04x\n", ch, data);
//#endif
            break;
        case 1: // start/cur address
            this.channel[ch].address = data;
            break;
        case 2: // frequency
            this.channel[ch].freq = data;
            // This was working with the old code, but breaks the songs with the new one.
            // And I'm pretty sure the hardware won't do this. -Valley Bell
                /*if (!data) {
                    // key off
                    this.channel[ch].enabled = 0;
                }*/
            break;
        case 3:
//if (this.channel[ch].enabled && data != 0x8000)
// Debug.printf("QSound Ch %u: KeyOn = %04x\n", ch, data);
            // key on (does the value matter? it always writes 0x8000)
            //this.channel[ch].enabled = 1;
            this.channel[ch].enabled = (byte) ((data & 0x8000) >> 15);
            this.channel[ch].stepPtr = 0;
            break;
        case 4: // loop address
            this.channel[ch].loop = data;
            break;
        case 5: // end address
            this.channel[ch].end = data;
            break;
        case 6: // master volume
//if (!this.channel[ch].enabled && data)
// Debug.printf("QSound update warning - please report!\n");
            this.channel[ch].vol = data;
            break;
        case 7: // unused?
//Debug.printf("UNUSED QSOUND REG 7=%04x", data);
            break;
        case 8: {
            // panning (left=0x0110, centre=0x0120, right=0x0130)
            // looks like it doesn't write other values than that
            int pan = (data & 0x3f) - 0x10;
            if (pan > 0x20)
                pan = 0x20;
            if (pan < 0)
                pan = 0;

            this.channel[ch].rVol = panTable[pan];
            this.channel[ch].lVol = panTable[0x20 - pan];
        }
        break;
        case 9: // unknown
//Debug.printf("QSOUND REG 9=%04x",data);
            break;
        default:
//Debug.printf("%s: write_data %02x = %04x\n", machine().describe_context(), address, data);
            break;
        }
//Debug.printf("QSOUND WRITE %02x CH%02d-R%02d =%04x\n", address, ch, reg, data);
    }

    public int start(int clock) {
        this.sampleRom = null;
        this.sampleRomLength = 0x00;

        // init Sound regs
        for (int i = 0; i < this.channel.length; i++) this.channel[i] = new Channel();

        for (int i = 0; i < CHANNELS; i++) {
            this.channel[i].muted = 0x00;
        }

        return clock / CLOCKDIV;
    }

    public void reset() {
        // init Sound regs
        for (int i = 0; i < this.channel.length; i++) this.channel[i] = new Channel();

        for (int adr = 0x7f; adr >= 0; adr--)
            this.setCommand((byte) adr, 0);
        for (int adr = 0x80; adr < 0x90; adr++)
            this.setCommand((byte) adr, 0x120);
    }

    public void write(int offset, byte data) {
        switch (offset) {
        case 0:
            this.data = (this.data & 0xff) | (data << 8);
            break;

        case 1:
            this.data = (this.data & 0xff00) | data;
            break;

        case 2:
            this.setCommand(data, this.data);
            break;

        default:
            //Debug.printf("%s: unexpected QSound write to offset %d == %02X\n", device.machine().describe_context(), offset, data);
            //Debug.printf("QSound: unexpected QSound write to offset %d == %02X\n", offset, data);
            break;
        }
    }

    public byte read(int offset) {
        // Port ready bit (0x80 if ready)
        return (byte) 0x80;
    }

    public void update(int[][] outputs, int samples) {
        for (int i = 0; i < samples; i++) {
            outputs[0][i] = 0x00;
            outputs[1][i] = 0x00;
        }
        if (this.sampleRomLength == 0)
            return;

        for (int i = 0; i < CHANNELS; i++) {
            this.channel[i].update(outputs, samples, sampleRom, sampleRomLength);
        }
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
        if (this.sampleRomLength != romSize) {
            this.sampleRom = new byte[romSize];
            this.sampleRomLength = romSize;
            for (int i = 0; i < romSize; i++) this.sampleRom[i] = (byte) 0xff;
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        if (dataLength >= 0)
            System.arraycopy(romData, 0, this.sampleRom, 0 + dataStart, dataLength);
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        if (this.sampleRomLength != romSize) {
            this.sampleRom = new byte[romSize];
            this.sampleRomLength = romSize;
            for (int i = 0; i < romSize; i++) this.sampleRom[i] = (byte) 0xff;
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        if (dataLength >= 0)
            System.arraycopy(romData, 0 + srcStartAddress, this.sampleRom, 0 + dataStart, dataLength);
    }

    public void setMuteMask(int muteMask) {
        for (byte curChn = 0; curChn < CHANNELS; curChn++)
            this.channel[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
    }

    public void stop() {
        this.sampleRom = null;
    }
}
