/*
 * license:BSD-3-Clause
 *
 * copyright-holders: R. Belmont, superctr, Valley Bell
 */

package mdsound.chips;

import java.util.Arrays;

import mdsound.Common;
import vavi.util.Debug;


/*
C219.c

Simulator based on AMUSE sources.
The C140 sound chip is used by Namco System 2 and System 21
The 219 ASIC (which incorporates a modified C140) is used by Namco NA-1 and NA-2
This chip controls 24 channels (C140) or 16 (219) of PCM.
16 bytes are associated with each channel.
Channels can be 8 bit signed PCM, or 12 bit signed PCM.

Timer behavior is not yet handled.

Unmapped registers:
    0x1f8:timer interval?   (Nx0.1 ms)
    0x1fa:irq ack? timer restart?
    0x1fe:timer switch?(0:off 1:on)

--------------

    ASIC "219" notes

    On the 219 ASIC used on NA-1 and NA-2, the high registers have the following
    meaning instead:
    0x1f7: bank for voices 0-3
    0x1f1: bank for voices 4-7
    0x1f3: bank for voices 8-11
    0x1f5: bank for voices 12-15

    Some games (bkrtmaq, xday2) write to 0x1fd for voices 12-15 instead.  Probably the bank registers
    mirror at 1f8, in which case 1ff is also 0-3, 1f9 is also 4-7, 1fb is also 8-11, and 1fd is also 12-15.

    Each bank is 0x20000 (128k), and the voice addresses on the 219 are all multiplied by 2.
    Additionally, the 219's base pitch is the same as the C352's (42667).  But these changes
    are IMO not sufficient to make this a separate file - all the other registers are
    fully compatible.

    Finally, the 219 only has 16 voices.
*/
/*
    2000.06.26  CAB     fixed compressed pcm playback
    2002.07.20  R. Belmont   added support for multiple banking types
    2006.01.08  R. Belmont   added support for NA-1/2 "219" derivative
    2018.11.15  Valley Bell  split "219" from C140 code, ported channel update + MuLaw table from superctr's C352 core
*/
public class C219 {

    private static final int C219_MODE_MULAW = 0x01; // sample is mulaw instead of linear 8-bit PCM
    private static final int C219_MODE_NOISE = 0x04; // play noise instead of sample
    private static final int C219_MODE_L_INV = 0x08; // left speaker phase invert
    private static final int C219_MODE_LOOP = 0x10; // loop
    private static final int C219_MODE_INVERT = 0x40; // invert phase
    private static final int C219_MODE_KEYON = 0x80;  // key on

    private static final int MAX_VOICE = 16;

    private static class VRegs {

        int volumeRight;
        int volumeLeft;
        int frequencyMsb;
        int frequencyLsb;
        int bank;
        int mode;
        int startMsb;
        int startLsb;
        int endMsb;
        int endLsb;
        int loopMsb;
        int loopLsb;
        byte[] reserved = new byte[4];

        VRegs(int[] regs, int offset) {
            volumeRight = regs[offset++];
            volumeLeft = regs[offset++];
            frequencyMsb = regs[offset++];
            frequencyLsb = regs[offset++];
            bank = regs[offset++];
            mode = regs[offset++];
            startMsb = regs[offset++];
            startLsb = regs[offset++];
            endMsb = regs[offset++];
            endLsb = regs[offset++];
            loopMsb = regs[offset++];
            loopLsb = regs[offset++];
        }
    }

    private static class Voice {

        int pos;
        int pOfs;
        short sample;
        short lastSample;

        int sampleStart;
        int sampleEnd;
        int sampleLoop;
        int key;
        int muted;
    }

    private int sampleRate;

    private int pRomSize;
    private int pRomMask;
    private byte[] pRom;
    private final int[] regs = new int[0x200];

    private short random;

    private final int[] muLawTable = new int[256];

    private final Voice[] voices = {
            new Voice(), new Voice(), new Voice(), new Voice(), new Voice(), new Voice(), new Voice(), new Voice(),
            new Voice(), new Voice(), new Voice(), new Voice(), new Voice(), new Voice(), new Voice(), new Voice(),
    };

    static final short[] asic219banks = {0x1f7, 0x1f1, 0x1f3, 0x1f5};

    /**
       find_sample: compute the actual address of a sample given it's
       address and banking registers, as well as the board type.

       I suspect in "real life" this works like the Sega MultiPCM where the banking
       is done by a small PAL or GAL external to the sound chip, which can be switched
       per-game or at least per-PCB revision as addressing range needs grow.
     */
    private int findSample(int adrs, int voice) {
        // ASIC219's banking is fairly simple
        int bank = this.regs[asic219banks[voice / 4]];
        return ((bank << 17) | adrs) & this.pRomMask;
    }

    private int keyonStatusRead(int offset) {
        //m_stream.update();
        Voice v = this.voices[offset >> 4];

        // suzuka 8 hours and final lap games read from here, expecting bit 6 to be an in-progress sample flag.
        // four trax also expects bit 4 high for some specific channels to make engine noises to work properly
        // (sounds kinda bogus when player crashes in an object and jump spin, needs real HW verification)
        return (v.key != 0 ? 0x40 : 0x00) | (this.regs[offset] & 0x3f);
    }

    public int read(int offset) {
        offset &= 0x1ff;
        if (offset >= 0x1f8 && (offset & 0x001) != 0)
            offset &= ~0x008;

        // assume same as c140
        // TODO: what happens here on reading unmapped voice regs?
        if ((offset & 0xf) == 0x5 && offset < 0x100)
            return keyonStatusRead(offset);

        return this.regs[offset];
    }

    public void write(int offset, int data) {
        offset &= 0x1ff;
        // mirror the bank registers on the 219, fixes bkrtmaq (and probably xday2 based on notes in the HLE)
        if (offset >= 0x1f8 && (offset & 0x001) != 0)
            offset &= ~0x008;

        this.regs[offset] = data;
        if (offset < 0x100) {
            Voice v = this.voices[offset >> 4];

            if ((offset & 0xf) == 0x5) {
                if ((data & C219_MODE_KEYON) != 0) {
                    VRegs vReg = new VRegs(this.regs, offset & 0x1f0);

                    // on the 219 asic, addresses are in words
                    v.sampleLoop = ((vReg.loopMsb << 8) | vReg.loopLsb) * 2;
                    v.sampleStart = ((vReg.startMsb << 8) | vReg.startLsb) * 2;
                    v.sampleEnd = ((vReg.endMsb << 8) | vReg.endLsb) * 2;
                    v.key = 1;
                    v.pos = v.sampleStart;
                    v.pOfs = 0xffff;
                    v.sample = 0;
                    v.lastSample = 0;

//                    logger.log(Level.ERROR, "219: play v %d mode %02x start %x loop %x end %x".formatted(
//                            offset >> 4, vReg.mode,
//                            find_sample(v.sampleStart, vReg.bank, offset >> 4),
//                            find_sample(v.sampleLoop, vReg.bank, offset >> 4),
//                            find_sample(v.sampleEnd, vReg.bank, offset >> 4)));
                } else {
                    v.key = 0;
                }
            }
        }
    }

    private void fetchSample(int vid) {
        Voice v = this.voices[vid];
        VRegs vReg = new VRegs(this.regs, vid * 16);

        v.lastSample = v.sample;

        if ((vReg.mode & C219_MODE_NOISE) != 0) {
            this.random = (short) ((this.random >> 1) ^ ((-(this.random & 1)) & 0xfff6));
            v.sample = this.random;
        } else {
            int addr = findSample(v.pos, vid);

            if ((vReg.mode & C219_MODE_MULAW) != 0)
                v.sample = (short) this.muLawTable[this.pRom[addr] & 0xff];
            else
                v.sample = (short) (this.pRom[addr] << 8);

            v.pos++;
            if (v.pos == v.sampleEnd) {
                if ((vReg.mode & C219_MODE_LOOP) != 0) {
                    v.pos = v.sampleLoop;
                } else {
                    v.key = 0;
                    v.sample = 0;
                }
            }
        }
    }

    public void update(int samples, int[][] outputs) {
        int[] out = new int[2];

        Arrays.fill(outputs[0], 0);
        Arrays.fill(outputs[1], 0);
        if (this.pRom == null)
            return;

        for (int i = 0; i < samples; i++) {
            out[0] = out[1] = 0;

            for (int j = 0; j < MAX_VOICE; j++) {
                Voice v = this.voices[j];
                VRegs vReg = new VRegs(this.regs, j * 16);

                if (v.key != 0 && v.muted == 0) {
                    int frequency = (vReg.frequencyMsb << 8) | vReg.frequencyLsb;

                    int newOfs = v.pOfs + frequency;
                    v.pOfs = newOfs & 0xffff;
                    if ((newOfs & 0x10000) != 0)
                        fetchSample(j);

                    // Interpolate samples
                    int s = v.lastSample + (int) ((long) v.pOfs * (v.sample - v.lastSample) >> 16);

                    if ((vReg.mode & C219_MODE_INVERT) != 0)
                        s = -s;
                    out[0] += (((vReg.mode & C219_MODE_INVERT) != 0 ? -s : s) * vReg.volumeLeft);
                    out[1] += (s * vReg.volumeRight);
                }
            }

            outputs[0][i] += (out[0] >> 9);
            outputs[1][i] += (out[1] >> 9);
        }
    }

    public int start(int clock) {

        //info.sampleRate = cfg.clock / 576;	// sample rate according to superctr
        this.sampleRate = clock / 288;    // TODO: output at 43 KHz and fix sample reading/interpolation code

        this.pRomSize = 0x00;
        this.pRomMask = 0x00;
        this.pRom = null;

        short j = 0;
        for (int i = 0; i < 128; i++) {
            this.muLawTable[i] = j << 5;
            if (i < 16)
                j += 1;
            else if (i < 24)
                j += 2;
            else if (i < 48)
                j += 4;
            else if (i < 100)
                j += 8;
            else
                j += 16;
        }
        for (int i = 128; i < 256; i++)
            this.muLawTable[i] = (~this.muLawTable[i - 128]) & ~0x1f;

        setMuteMask(0x00_0000);

        return this.sampleRate;
    }

    public void stop() {
    }

    public void reset() {
        Arrays.fill(this.regs, 0);

        for (int i = 0; i < MAX_VOICE; i++) {
            Voice v = this.voices[i];

            v.pOfs = 0;
            v.pos = 0;
        }

        // init noise generator
        this.random = 0x1234;
    }

    private void allocRom(int memsize) {

        if (this.pRomSize == memsize)
            return;

        this.pRom = new byte[memsize];
        this.pRomSize = memsize;
        this.pRomMask = Common.pow2_mask(memsize);
        Arrays.fill(this.pRom, (byte) 0xff);
    }

    public void writeRom(int offset, int length, byte[] data, int srcOffset, int romSize) {
        if (this.pRom == null || romSize > this.pRomSize) {
            allocRom(romSize);
        }
        if (offset > this.pRomSize)
            return;
        if (offset + length > this.pRomSize)
            length = this.pRomSize - offset;

        System.arraycopy(data, srcOffset, this.pRom, offset, length);
Debug.println("rom: " + length);
    }

    public void setMuteMask(int muteMask) {
        for (int curChn = 0; curChn < MAX_VOICE; curChn++)
            this.voices[curChn].muted = (muteMask >> curChn) & 0x01;
    }
}
