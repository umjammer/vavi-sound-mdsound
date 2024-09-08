package mdsound.chips;

import mdsound.MDSound;


/**
 * PcmChip.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2022-07-08 nsano initial version <br>
 */
public class PcmChip {

    private static final int STEP_SHIFT = 11;

    public static class Channel {
        /** envelope register */
        private int env;
        /** pan register */
        public int pan;
        /** envelope & pan product letf */
        public int mulL;
        /** envelope & pan product right */
        public int mulR;
        /** start address register */
        private int stAddr;
        /** loop address register */
        private int loopAddr;
        /** current address register */
        private int addr;
        /** frequency register */
        private int step;
        /** frequency register binaire */
        public int stepB;
        /** channel on/off register */
        public int enable;
        /** wave data */
        private int data;
        private int muted;

        private void init() {
            this.enable = 0;
            this.env = 0;
            this.pan = 0;
            this.stAddr = 0;
            this.addr = 0;
            this.loopAddr = 0;
            this.step = 0;
            this.stepB = 0;
            this.data = 0;
        }
    }

    private float rate;
    private int smpl0Patch;
    private int enable;
    private int curChan;
    private int bank;

    private Channel[] channels = new Channel[] {
            new Channel(), new Channel(), new Channel(), new Channel(),
            new Channel(), new Channel(), new Channel(), new Channel()
    };
    private long ramSize;
    private byte[] ram;

    /**
     * Initialize the PCM chips.
     *
     * @param rate Sample rate.
     * @return 0 if successful.
     */
    public int init(int rate) {
        this.smpl0Patch = 0;
        for (int i = 0; i < 8; i++)
            this.channels[i].muted = 0x00;

        this.ramSize = 64 * 1024;
        this.ram = new byte[(int) this.ramSize];
        reset();
        setRate(rate);

        return 0;
    }

    /**
     * Reset the PCM chips.
     */
    public void reset() {
        // Clear the PCM memory.
        for (long j = 0; j < this.ramSize; j++) this.ram[(int) j] = 0x00;

        this.enable = 0;
        this.curChan = 0;
        this.bank = 0;

        // clear channel registers
        for (int i = 0; i < 8; i++) {
            Channel chan = this.channels[i];
            chan.init();
        }
    }

    /**
     * Change the PCM sample rate.
     *
     * @param rate New sample rate.
     */
    public void setRate(int rate) {
        if (rate == 0)
            return;

        this.rate = (float) (31.8 * 1024) / (float) rate;

        for (int i = 0; i < 8; i++) {
            this.channels[i].step = (int) ((float) this.channels[i].stepB * this.rate);
        }
    }

    /**
     * Write to a PCM register.
     *
     * @param reg  Register ID.
     * @param data data to write.
     */
    public void writeReg(int reg, int data) {
        int i;
        Channel chan = this.channels[this.curChan];

        data &= 0xff;

        switch (reg) {
        case 0x00: // evelope register
            chan.env = data;
            chan.mulL = (data * (chan.pan & 0x0F)) >> 5;
            chan.mulR = (data * (chan.pan >> 4)) >> 5;
            break;

        case 0x01: // pan register
            chan.pan = data;
            chan.mulL = ((data & 0x0F) * chan.env) >> 5;
            chan.mulR = ((data >> 4) * chan.env) >> 5;
            break;

        case 0x02: // frequency step (LB) registers
            chan.stepB &= 0xff00;
            chan.stepB += data;
            chan.step = (int) ((float) chan.stepB * this.rate);

            //Debug.printf("Step low = %.2X   Step calculated = %.8X", data, chan.Step);
            break;

        case 0x03: // frequency step (HB) registers
            chan.stepB &= 0x00FF;
            chan.stepB += data << 8;
            chan.step = (int) ((float) chan.stepB * this.rate);

            //Debug.printf("Step high = %.2X   Step calculated = %.8X", data, chan.Step);
            break;

        case 0x04:
            chan.loopAddr &= 0xff00;
            chan.loopAddr += data;

            //Debug.printf("Loop low = %.2X   Loop = %.8X", data, chan.Loop_Addr);
            break;

        case 0x05:
            /** loop address registers  */
            chan.loopAddr &= 0x00FF;
            chan.loopAddr += data << 8;

            //Debug.printf("Loop high = %.2X   Loop = %.8X", data, chan.Loop_Addr);
            break;

        case 0x06: // start address registers
            chan.stAddr = data << (STEP_SHIFT + 8);
            //chan.Addr = chan.St_Addr;

            //Debug.printf("Start addr = %.2X   New Addr = %.8X", data, chan.Addr);
            break;

        case 0x07: // control register
            // mod is H
            if ((data & 0x40) != 0) {
                // select channel
                this.curChan = data & 0x07;
            } else { // mod is L
                // pcm ram bank select
                this.bank = (data & 0x0F) << 12;
            }

            // sounding bit
            if ((data & 0x80) > 0)
                this.enable = 0xff; // Used as mask
            else
                this.enable = 0;

            //Debug.printf("General Enable = %.2X", data);
            break;

        case 0x08:
            // Sound on/off register
            data ^= 0xff;

            //Debug.printf("Channel Enable = %.2X", data);

            for (i = 0; i < 8; i++) {
                chan = this.channels[i];
                if (chan.enable == 0)
                    chan.addr = chan.stAddr;
            }

            for (i = 0; i < 8; i++) {
                this.channels[i].enable = data & (1 << i);
            }
            break;
        }
    }

    /**
     * Update the PCM buffer.
     *
     * @param buf    PCM buffer.
     * @param length Buffer length.
     */
    public int update(int[][] buf, int length) {
        int[] bufL = buf[0];
        int[] bufR = buf[1];

        // clear buffers
        for (int d = 0; d < length; d++) {
            bufL[d] = 0;
            bufR[d] = 0;
        }

        // if PCM disable, no Sound
        if (this.enable == 0) return 1;

        // for long update
        for (int i = 0; i < 8; i++) {
            Channel ch = this.channels[i];

            // only loop when sounding and on
            if (ch.enable > 0 && ch.muted == 0) {
                int addr = ch.addr >> STEP_SHIFT;
                //volL = &(PCM_Volume_Tab[ch.MUL_L << 8]);
                //volR = &(PCM_Volume_Tab[ch.MUL_R << 8]);

                for (int j = 0; j < length; j++) {
                    // test for loop signal
                    if ((this.ram[addr] & 0xff) == 0xff) {
                        ch.addr = (addr = ch.loopAddr) << STEP_SHIFT;
                        if (this.ram[addr] == 0xff)
                            break;
                        else
                            j--;
                    } else {
                        if ((this.ram[addr] & 0x80) != 0) {
                            ch.data = this.ram[addr] & 0x7F;
                            bufL[j] -= ch.data * ch.mulL;
                            bufR[j] -= ch.data * ch.mulR;
                        } else {
                            ch.data = this.ram[addr];
                            // this improves the Sound of Cosmic Fantasy Stories,
                            // although it's definately false behaviour
                            if (ch.data == 0 && this.smpl0Patch != 0)
                                ch.data = -0x7F;
                            bufL[j] += ch.data * ch.mulL;
                            bufR[j] += ch.data * ch.mulR;
                        }

                        // update address register
                        int k = addr + 1;
                        ch.addr = (ch.addr + ch.step) & 0x7FFFFFF;
                        addr = ch.addr >> STEP_SHIFT;

                        for (; k < addr; k++) {
                            if ((this.ram[k] & 0xff) == 0xff) {
                                ch.addr = (addr = ch.loopAddr) << STEP_SHIFT;
                                break;
                            }
                        }
                    }
                }

                if ((this.ram[addr] & 0xff) == 0xff) {
                    ch.addr = ch.loopAddr << STEP_SHIFT;
                }
            }
        }

        for (int j = 0; j < length; j++) {
            bufL[j] = MDSound.limit(bufL[j], Short.MAX_VALUE, Short.MIN_VALUE);
            bufR[j] = MDSound.limit(bufR[j], Short.MAX_VALUE, Short.MIN_VALUE);
        }

        return 0;
    }

    public void start(int clock) {
        this.smpl0Patch = (clock & 0x8000_0000) >>> 31;
    }

    public void writeMem(int offset, int data) {
        this.ram[this.bank | offset] = (byte) (data & 0xff);
    }

    public void writeRam(int dataStart, int dataLength, byte[] ramData) {
        dataStart |= this.bank;
        if (dataStart >= this.ramSize)
            return;
        if (dataStart + dataLength > this.ramSize)
            dataLength = (int) (this.ramSize - dataStart);

        if (dataLength >= 0) System.arraycopy(ramData, 0, this.ram, dataStart, dataLength);
    }

    public void writeRam2(int ramStartAdr, int ramdataLength, byte[] srcData, int srcStartAdr) {
        ramStartAdr |= this.bank;
        if (ramStartAdr >= this.ramSize)
            return;
        if (ramStartAdr + ramdataLength > this.ramSize)
            ramdataLength = (int) (this.ramSize - ramStartAdr);

        if (ramdataLength >= 0)
            System.arraycopy(srcData, srcStartAdr, this.ram, ramStartAdr, ramdataLength);
    }

    public void setMuteMask(int muteMask) {
        for (byte curChn = 0; curChn < 8; curChn++)
            this.channels[curChn].muted = (muteMask >> curChn) & 0x01;
    }

    public void setMuteCh(int ch, int mute) {
        this.channels[ch].muted = mute & 0x1;
    }

    public Channel getChannel(int ch) {
        return channels[ch];
    }
}
