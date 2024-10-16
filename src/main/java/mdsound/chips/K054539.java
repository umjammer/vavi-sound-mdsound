package mdsound.chips;

import java.util.Arrays;

import vavi.util.win32.WAVE.data;


/**
 Konami 054539 (TOP) PCM Sound Chip

 A lot of information comes from Amuse.
 Big thanks to them.

   Registers:
   00..ff: 20 bytes/channel, 8 channels
     00..02: pitch (lsb, mid, msb)
         03: volume (0=max, 0x40=-36dB)
         04: Reverb volume (idem)
     05: pan (1-f right, 10 middle, 11-1f left)
     06..07: Reverb delay (0=max, current computation non-trusted)
     08..0a: loop (lsb, mid, msb)
     0c..0e: start (lsb, mid, msb) (and current position ?)

   100.1ff: effects?
     13f: pan of the analog input (1-1f)

   200..20f: 2 bytes/channel, 8 channels
     00: type (b2-3), reverse (b5)
     01: loop (b0)

   214: Key on (b0-7 = channel 0-7)
   215: Key off          ""
   225: ?
   227: Timer frequency
   228: ?
   229: ?
   22a: ?
   22b: ?
   22c: Channel active? (b0-7 = channel 0-7)
   22d: data read/write port
   22e: ROM/RAM select (00..7f == ROM banks, 80 = Reverb RAM)
   22f: Global control:
        .......x - Enable PCM
        ......x. - Timer related?
        ...x.... - Enable ROM/RAM readback from 0x22d
        ..x..... - Timer output enable?
        x....... - Disable register RAM updates

    The chips has an optional 0x8000 byte Reverb buffer.
    The Reverb delay is actually an offset in this buffer.
*/
public class K054539 {

    private static final int RESET_FLAGS = 0;
    private static final int REVERSE_STEREO = 1;
    private static final int DISABLE_REVERB = 2;
    private static final int UPDATE_AT_KEYON = 4;

    private static class Channel {
        private int pos;
        private int pfrac;
        private int val;
        private int pval;
    }

    private static double[] volTab = new double[256];
    private static double[] panTab = new double[0xf];

    static {
        /*
            I've tried various equations on volume control but none worked consistently.
            The upper four channels in most MW/GX games simply need a significant boost
            to Sound right. For example, the bass and smash Sound volumes in Violent Storm
            have roughly the same values and the voices in Tokimeki Puzzledama are given
            values smaller than those of the hihats. Needless to say the two K054539 chips
            in Mystic Warriors are completely out of balance. Rather than forcing a
            "one size fits all" function to the voltab the current invert exponential
            appraoch seems most appropriate.
        */
        // Factor the 1/4 for the number of channels in the volume (1/8 is too harsh, 1/2 gives clipping)
        // vol=0 . no attenuation, vol=0x40 . -36dB
        for (int i = 0; i < 256; i++)
            volTab[i] = Math.pow(10.0, (-36.0 * (double) i / (double) 0x40) / 20.0) / 4.0;

        // Pan table for the left channel
        // Right channel is identical with inverted index
        // Formula is such that pan[i]**2+pan[0xe-i]**2 = 1 (constant output power)
        // and pan[0xe] = 1 (full panning)
        for (int i = 0; i < 0xf; i++)
            panTab[i] = Math.sqrt(i) / Math.sqrt(0xe);
    }

    private double[] gain = new double[8];
    private byte[][] posRegLatch = new byte[][] {
            new byte[3], new byte[3], new byte[3], new byte[3],
            new byte[3], new byte[3], new byte[3], new byte[3]};
    private int flags;

    private byte[] regs = new byte[0x230];
    private byte[] ram;
    private int ptrRam = 0;
    private int reverbPos;

    private int curPtr;
    private int curLimit;
    private byte[] curZone;
    private int ptrCurZone;
    private byte[] rom;
    private int ptrRom = 0;

    private int romSize;
    private int romMask;

    private Channel[] channels = new Channel[] {
            new Channel(),
            new Channel(),
            new Channel(),
            new Channel(),
            new Channel(),
            new Channel(),
            new Channel(),
            new Channel()
    };

    private byte[] muted = new byte[8];

    private int clock;

    private int updateReg() {
        return this.regs[0x22f] & 0x80;
    }

    private void keyOn(int channel) {
        if (updateReg() == 0)
            this.regs[0x22c] |= (byte) (1 << channel);
    }

    private void keyOff(int channel) {
        if (updateReg() == 0)
            this.regs[0x22c] &= (byte) (~(1 << channel));
    }

    private int init(int clock) {

        if (clock < 1000000) // if < 1 MHz, then it's the sample rate, not the clock
            clock *= 384; // (for backwards compatibility with old VGM logs)
        this.clock = clock;
        // most of these are done in device_reset
        this.flags |= UPDATE_AT_KEYON; //make it default until proven otherwise

        this.ram = new byte[0x4000];

        this.rom = null;
        this.romSize = 0;
        this.romMask = 0x00;

        //if(this.intf.irq)
        // One or more of the registers must be the timer period
        // And anyway, this particular frequency is probably wrong
        // 480 hz is TRUSTED by gokuparo disco stage - the looping sample doesn't line up otherwise
        // device.machine().scheduler().timer_pulse(attotime::from_hz(480), FUNC(k054539_irq), 0, info);

        return this.clock / 384;
    }

    private void resetZones() {
        int data = this.regs[0x22e];
        this.curZone = (data & 0xff) == 0x80 ? this.ram : this.rom;
        this.ptrCurZone = (data & 0xff) == 0x80 ? 0 : (0x20000 * data);
        this.curLimit = (data & 0xff) == 0x80 ? 0x4000 : 0x20000;
    }

    public void write(int offset, int data) {

        byte[] regBase = this.regs;
        boolean latch = (this.flags & K054539.UPDATE_AT_KEYON) != 0 && (regBase[0x22f] & 1) != 0;
        //Debug.printf("latch = %d \n", latch);

        if (latch && offset < 0x100) {
            int offs = (offset & 0x1f) - 0xc;
            int ch = offset >> 5;

            if (offs >= 0 && offs <= 2) {
                // latch writes to the position index registers
                this.posRegLatch[ch][offs] = (byte) data;
                //Debug.printf("this.k054539_posreg_latch[%d][%d] = %d \n", ch, offs, data);
                return;
            }
        } else
            switch (offset) {
            case 0x13f:
                int pan = (data >= 0x11 && data <= 0x1f) ? data - 0x11 : 0x18 - 0x11;
                //if(this.intf.apan)
                // this.intf.apan(this.device, this.pantab[pan], this.pantab[0xe - pan]);
                break;

            case 0x214:
                if (latch) {
                    for (int ch = 0; ch < 8; ch++) {
                        if ((data & (1 << ch)) != 0) {
                            int regptr = (ch << 5) + 0xc;

                            // update the chips at key-on
                            regBase[regptr + 0] = this.posRegLatch[ch][0];
                            regBase[regptr + 1] = this.posRegLatch[ch][1];
                            regBase[regptr + 2] = this.posRegLatch[ch][2];

                            this.keyOn(ch);
                        }
                    }
                } else {
                    for (int ch = 0; ch < 8; ch++)
                        if ((data & (1 << ch)) != 0)
                            this.keyOn(ch);
                }
                break;

            case 0x215:
                for (int ch = 0; ch < 8; ch++)
                    if ((data & (1 << ch)) != 0)
                        this.keyOff(ch);
                break;

            /*case 0x227: {
                attotime period = attotime::from_hz((float)(38 + data) * (clock()/384.0f/14400.0f)) / 2.0f;

                m_timer.adjust(period, 0, period);

                m_timer_state = 0;
                m_timer_handler(m_timer_state);
            }*/
            //break;

            case 0x22d:
                if ((regBase[0x22e] & 0xff) == 0x80)
                    this.curZone[this.ptrCurZone + this.curPtr] = (byte) data;
                this.curPtr++;
                if (this.curPtr == this.curLimit)
                    this.curPtr = 0;
                break;

            case 0x22e:
                this.curZone = (data & 0xff) == 0x80 ? this.ram : this.rom;
                this.ptrCurZone = (data & 0xff) == 0x80 ? 0 : (0x20000 * data);
                this.curLimit = (data & 0xff) == 0x80 ? 0x4000 : 0x20000;
                this.curPtr = 0;
                break;

//            case 0x22f:
//                if (!(data & 0x20)) // Disable timer output? {
//                    m_timer_state = 0;
//                    m_timer_handler(m_timer_state);
//                }
//                break;

            default:
                break;
            }

        regBase[offset] = (byte) data;
    }

    public byte write(int offset) {
        switch (offset) {
        case 0x22d:
            if ((this.regs[0x22f] & 0x10) != 0) {
                byte res = this.curZone[this.ptrCurZone + this.curPtr];
                this.curPtr++;
                if (this.curPtr == this.curLimit)
                    this.curPtr = 0;
                return res;
            } else
                return 0;
        case 0x22c:
            break;
        default:
            //Debug.printf("K054539 read %03x\n", offset);
            break;
        }
        return this.regs[offset];
    }

    public int start(int clock) {
        for (int i = 0; i < 8; i++)
            this.gain[i] = 1.0;
        this.flags = RESET_FLAGS;

        for (int i = 0; i < 8; i++)
            this.muted[i] = 0x00;

        return this.init(clock);
    }

    public void stop() {
        this.rom = null;
        this.ram = null;
    }

    public void reset() {
        Arrays.fill(this.regs, (byte) 0);
        for (byte[] posRegLatch : this.posRegLatch) {
            Arrays.fill(posRegLatch, (byte) 0);
        }

        this.reverbPos = 0;
        this.curPtr = 0;
        for (int i = 0; i < 0x4000; i++) {
            this.ram[i] = 0;
        }
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
        if (this.romSize != romSize) {
            this.rom = new byte[romSize];
            this.romSize = romSize;
            for (byte i = 0; i < romSize; i++) {
                this.rom[i] = (byte) 0xff;
            }

            this.romMask = 0xffff_ffff;
            for (byte i = 0; i < 32; i++) {
                if ((1 << i) >= this.romSize) {
                    this.romMask = (1 << i) - 1;
                    break;
                }
            }
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        System.arraycopy(romData, 0, this.rom, dataStart, dataLength);
    }

    public void writeRom2(int romSize, int dataStart, int dataLength, byte[] romData, int startAdr) {
        if (this.romSize != romSize) {
            this.rom = new byte[romSize];
            this.romSize = romSize;
            for (int ind = 0; ind < romSize; ind++) {
                this.rom[ind] = (byte) 0xff;
            }

            this.romMask = 0xffff_ffff;
            for (byte i = 0; i < 32; i++) {
                if ((1 << i) >= this.romSize) {
                    this.romMask = (1 << i) - 1;
                    break;
                }
            }
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        System.arraycopy(romData, startAdr, this.rom, dataStart, dataLength);
    }

    public void update(int[][] outputs, int samples) {
        final double VOL_CAP = 1.80;

        short[] dPcm = new short[] {
                0 << 8, 1 << 8, 4 << 8, 9 << 8, 16 << 8, 25 << 8, 36 << 8, 49 << 8,
                -64 << 8, -49 << 8, -36 << 8, -25 << 8, -16 << 8, -9 << 8, -4 << 8, -1 << 8
        };

        for (int i = 0; i < samples; i++) {
            outputs[0][i] = 0;
            outputs[1][i] = 0;
        }

        if ((this.regs[0x22f] & 1) == 0) // Enable PCM
            return;

        for (int i = 0; i != samples; i++) {
            // リバーブ
            double lVal, rVal;
            if ((this.flags & DISABLE_REVERB) == 0) {
                //lVal = rVal = rbase[this.reverb_pos];
                short val = (short) (this.ram[this.reverbPos * 2] + this.ram[this.reverbPos * 2 + 1] * 0x100);
                lVal = rVal = val;
            } else
                lVal = rVal = 0;
            //Debug.printf("rbase[this.reverb_pos(%d)] = %d \n", this.reverb_pos, lVal);
            this.ram[this.reverbPos * 2] = 0;
            this.ram[this.reverbPos * 2 + 1] = 0;

            for (int ch = 0; ch < 8; ch++)
                if (((this.regs[0x22c] & (1 << ch)) != 0) && this.muted[ch] == 0) { // 0x22c ChannelActive
                    int regP1 = 0x20 * ch;
                    int regP2 = 0x200 + 0x2 * ch;

                    // pitch
                    int delta = this.regs[regP1 + 0x00] | (this.regs[regP1 + 0x01] << 8) | (this.regs[regP1 + 0x02] << 16);

                    int vol = this.regs[regP1 + 0x03] & 0xff;

                    // 0x04 Reverb vol
                    int bVal = vol + (this.regs[regP1 + 0x04] & 0xff);
                    if (bVal > 255)
                        bVal = 255;

                    int pan = this.regs[regP1 + 0x05] & 0xff;
                    // DJ Main: 81-87 right, 88 middle, 89-8f left
                    if (pan >= 0x81 && pan <= 0x8f)
                        pan -= 0x81;
                    else if (pan >= 0x11 && pan <= 0x1f)
                        pan -= 0x11;
                    else
                        pan = 0x18 - 0x11;

                    double curGain = this.gain[ch];

                    double lVol = volTab[vol] * panTab[pan] * curGain;
                    if (lVol > VOL_CAP)
                        lVol = VOL_CAP;

                    double rVol = volTab[vol] * panTab[0xe - pan] * curGain;
                    if (rVol > VOL_CAP)
                        rVol = VOL_CAP;

                    double rbVol = volTab[bVal] * curGain / 2;
                    if (rbVol > VOL_CAP)
                        rbVol = VOL_CAP;

                    //Debug.printf("ch=%d lVol=%d rVol=%d\n", ch, lVol, rVol);

                    int rDelta = (this.regs[regP1 + 6] | (this.regs[regP1 + 7] << 8)) >> 3;
                    rDelta = (rDelta + this.reverbPos) & 0x3fff;

                    int curPos = (this.regs[regP1 + 0x0c] | (this.regs[regP1 + 0x0d] << 8) | (this.regs[regP1 + 0x0e] << 16)) & this.romMask;

                    int fDelta, pDelta;
                    if ((this.regs[regP2 + 0] & 0x20) != 0) {
                        delta = -delta;
                        fDelta = 0x10000;
                        pDelta = -1;
                    } else {
                        fDelta = -0x10000;
                        pDelta = 1;
                    }

                    int curPfrac, curVal, curPval;
                    if (curPos != this.channels[ch].pos) {
                        this.channels[ch].pos = curPos;
                        curPfrac = 0;
                        curVal = 0;
                        curPval = 0;
                    } else {
                        curPfrac = this.channels[ch].pfrac;
                        curVal = this.channels[ch].val;
                        curPval = this.channels[ch].pval;
                    }

                    switch (this.regs[regP2 + 0] & 0xc) {
                    case 0x0: { // 8bit pcm
                        curPfrac += delta;
                        while ((curPfrac & ~0xffff) != 0) {
                            curPfrac += fDelta;
                            curPos += pDelta;

                            curPval = curVal;
                            curVal = (short) (this.rom[curPos] << 8);
                            if ((this.rom[curPos] & 0xff) == 0x80 && (this.regs[regP2 + 1] & 1) != 0) {
                                curPos = (this.regs[regP1 + 0x08] | (this.regs[regP1 + 0x09] << 8) | (this.regs[regP1 + 0x0a] << 16)) & this.romMask;
                                curVal = (short) (this.rom[curPos] << 8);
                            }
                            if ((this.rom[curPos] & 0xff) == 0x80) {
                                this.keyOff(ch);
                                curVal = 0;
                                break;
                            }
                        }
                        //Debug.printf("ch=%d curPos=%d curVal=%d\n", ch, curPos, curVal);
                        //if(ch!=6) curVal = 0;
                        break;
                    }

                    case 0x4: { // 16bit pcm lsb first
                        pDelta <<= 1;

                        curPfrac += delta;
                        while ((curPfrac & ~0xffff) != 0) {
                            curPfrac += fDelta;
                            curPos += pDelta;

                            curPval = curVal;
                            curVal = (short) (this.rom[curPos] | this.rom[curPos + 1] << 8);
                            if (curVal == (short) (0x8000 - 0x10000) && (this.regs[regP2 + 1] & 1) != 0) {
                                curPos = (this.regs[regP1 + 0x08] | (this.regs[regP1 + 0x09] << 8) | (this.regs[regP1 + 0x0a] << 16)) & this.romMask;
                                curVal = (short) (this.rom[curPos] | this.rom[curPos + 1] << 8);
                            }
                            if (curVal == (short) (0x8000 - 0x10000)) {
                                this.keyOff(ch);
                                curVal = 0;
                                break;
                            }
                        }
                        //curVal = 0;
                        break;
                    }

                    case 0x8: { // 4bit dPcm
                        curPos <<= 1;
                        curPfrac <<= 1;
                        if ((curPfrac & 0x10000) != 0) {
                            curPfrac &= 0xffff;
                            curPos |= 1;
                        }

                        curPfrac += delta;
                        while ((curPfrac & ~0xffff) != 0) {
                            curPfrac += fDelta;
                            curPos += pDelta;

                            curPval = curVal;
                            curVal = this.rom[curPos >> 1];
                            if ((curVal & 0xff) == 0x88 && (this.regs[regP2 + 1] & 1) != 0) {
                                curPos = ((this.regs[regP1 + 0x08] | (this.regs[regP1 + 0x09] << 8) | (this.regs[regP1 + 0x0a] << 16)) & this.romMask) << 1;
                                curVal = this.rom[curPos >> 1];
                            }
                            if ((curVal & 0xff) == 0x88) {
                                this.keyOff(ch);
                                curVal = 0;
                                break;
                            }
                            if ((curPos & 1) != 0)
                                curVal >>= 4;
                            else
                                curVal &= 15;
                            curVal = curPval + dPcm[curVal];
                            if (curVal < -32768)
                                curVal = -32768;
                            else if (curVal > 32767)
                                curVal = 32767;
                        }

                        curPfrac >>= 1;
                        if ((curPos & 1) != 0)
                            curPfrac |= 0x8000;
                        curPos >>= 1;
                        //curVal = 0;
                        break;
                    }
                    default:
                        //System.err.prtinf(("Unknown sample type %x for channel %d\n", base2[0] & 0xc, ch));
                        break;
                    }
                    lVal += curVal * lVol;
                    rVal += curVal * rVol;
                    //if (ch == 6) {
                    //    Debug.printf("ch=%d lVal=%d\n", ch, lVal);
                    //}
                    int ptr = (rDelta + this.reverbPos) & 0x1fff;
                    short valu = (short) (this.ram[ptr * 2] + this.ram[ptr * 2 + 1] * 0x100);
                    valu += (short) (curVal * rbVol);
                    this.ram[ptr * 2] = (byte) (valu & 0xff);
                    this.ram[ptr * 2 + 1] = (byte) ((valu & 0xff00) >> 8);

                    this.channels[ch].pos = curPos;
                    this.channels[ch].pfrac = curPfrac;
                    this.channels[ch].pval = curPval;
                    this.channels[ch].val = curVal;

                    if (this.updateReg() == 0) {
                        this.regs[regP1 + 0x0c] = (byte) (curPos & 0xff);
                        this.regs[regP1 + 0x0d] = (byte) ((curPos >> 8) & 0xff);
                        this.regs[regP1 + 0x0e] = (byte) ((curPos >> 16) & 0xff);
                    }
                }
            this.reverbPos = (this.reverbPos + 1) & 0x1fff;
            outputs[0][i] = (int) lVal;
            outputs[1][i] = (int) rVal;
            outputs[0][i] <<= 1;
            outputs[1][i] <<= 1;
            //Debug.printf( "outputs[0][i] = %d\n", outputs[0][i]);
        }
    }

    public void setMuteMask(int muteMask) {
        for (int c = 0; c < 8; c++)
            this.muted[c] = (byte) ((muteMask >> c) & 0x01);
    }

    public void intFlags(int flags) {
        this.flags = flags;
    }

    public void setGain(int channel, double gain) {
        this.gain[channel] = gain;
    }
}
