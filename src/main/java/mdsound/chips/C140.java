package mdsound.chips;

import java.util.Arrays;

import vavi.util.Debug;


/**
 * C140
 *
 * Simulator based on AMUSE sources.
 * The C140 sound chip is used by Namco System 2 and System 21
 * The 219 ASIC (which incorporates a modified C140) is used by Namco NA-1 and NA-2
 * This chip controls 24 channels (C140) or 16 (219) of PCM.
 * 16 bytes are associated with each channel.
 * Channels can be 8 bit signed PCM, or 12 bit signed PCM.
 *
 * Timer behavior is not yet handled.
 *
 * Unmapped registers:
 *     0x1f8:timer interval?   (Nx0.1 ms)
 *     0x1fa:irq ack? timer restart?
 *     0x1fe:timer switch?(0:off 1:on)
 *
 * --------------
 *
 *     ASIC "219" notes
 *
 *     On the 219 ASIC used on NA-1 and NA-2, the high registers have the following
 *     meaning instead:
 *     0x1f7: bank for voices 0-3
 *     0x1f1: bank for voices 4-7
 *     0x1f3: bank for voices 8-11
 *     0x1f5: bank for voices 12-15
 *
 *     Some games (bkrtmaq, xday2) write to 0x1fd for voices 12-15 instead.  Probably the bank registers
 *     mirror at 1f8, in which case 1ff is also 0-3, 1f9 is also 4-7, 1fb is also 8-11, and 1fd is also 12-15.
 *
 *     Each bank is 0x20000 (128k), and the voice addresses on the 219 are all multiplied by 2.
 *     Additionally, the 219's base pitch is the same as the C352's (42667).  But these changes
 *     are IMO not sufficient to make this a separate file - all the other registers are
 *     fully compatible.
 *
 *     Finally, the 219 only has 16 voices.
 *
 * 2000.06.26  CAB     fixed compressed pcm playback
 * 2002.07.20  R.Belmont   added support for multiple banking types
 * 2006.01.08  R.Belmont   added support for NA-1/2 "219" derivative
 */
public class C140 {

    private static final int MAX_VOICE = 24;

    private static class Voice {
        private int ptoffset;
        private int pos;
        private int key;
        //--work
        private int lastdt;
        private int prevdt;
        private int dltdt;
        //--reg
        private int rvol;
        private int lvol;
        private int frequency;
        private int bank;
        private int mode;

        private int sample_start;
        private int sample_end;
        private int sample_loop;
        private int muted;

        private void init_voice() {
            this.key = 0;
            this.ptoffset = 0;
            this.rvol = 0;
            this.lvol = 0;
            this.frequency = 0;
            this.bank = 0;
            this.mode = 0;
            this.sample_start = 0;
            this.sample_end = 0;
            this.sample_loop = 0;
        }
    }

    private int sampleRate;
    private Type bankingType;
    /** internal buffers */
    private int[] mixerBufferLeft;
    private int[] mixerBufferRight;

    private int baserate;
    private int pRomSize;
    private byte[] pRom;
    private byte[] reg;

    private int[] pcmTbl; // 2000.06.26 CAB

    private Voice[] voi;

    public C140() {
        this.reg = new byte[0x200];
        this.pcmTbl = new int[8];
        this.voi = new Voice[MAX_VOICE];
        for (int j = 0; j < MAX_VOICE; j++) {
            this.voi[j] = new Voice();
            this.voi[j].init_voice();
        }
    }

    public byte read(int offset) {
        offset &= 0x1ff;
        return this.reg[offset];
    }

    private static final int[] asic219banks = new int[] {0x1f7, 0x1f1, 0x1f3, 0x1f5};

    /**
     * compute the actual address of a sample given its
     * address and banking registers, as well as the board type.
     * <p>
     * I suspect in "real life" this works like the Sega MultiPCM where the banking
     * is done by a small PAL or GAL external to the Sound chips, which can be switched
     * per-game or at least per-PCB revision as addressing range needs grow.
     */
    private int findSample(int adrs, int bank, int voice) {
        adrs = (bank << 16) + adrs;

        switch (bankingType) {
        case SYSTEM2:
            // System 2 banking
            return ((adrs & 0x20_0000) >> 2) | (adrs & 0x7_ffff);

        case SYSTEM21:
            // System 21 banking.
            // similar to System 2's.
            return ((adrs & 0x30_0000) >> 1) | (adrs & 0x7_ffff);

        case ASIC219:
            // ASIC219's banking is fairly simple
            return ((this.reg[asic219banks[voice / 4]] & 0x3) * 0x2_0000) | adrs;
        }

        return 0;
    }

    public void write(int offset, int data) {
        offset &= 0x1ff;

        // mirror the bank registers on the 219, fixes bkrtmaq (and probably xday2 based on notes in the HLE)
        if ((offset >= 0x1f8) && (this.bankingType == Type.ASIC219)) {
            offset -= 8;
        }

        this.reg[offset] = (byte) data;
        if (offset < 0x180) {
            Voice v = this.voi[offset >> 4];

            if ((offset & 0xf) == 0x5) {
                if ((data & 0x80) != 0) {
                    //voice_registers vreg = (voice_registers)this.REG[offset & 0x1f0];
                    int vreg = offset & 0x1f0;
                    v.key = 1;
                    v.ptoffset = 0;
                    v.pos = 0;
                    v.lastdt = 0;
                    v.prevdt = 0;
                    v.dltdt = 0;
                    v.bank = this.reg[vreg + 4];// vreg.bank;
                    v.mode = data;

                    // on the 219 asic, addresses are in words
                    if (this.bankingType == Type.ASIC219) {
                        v.sample_loop = (((this.reg[vreg + 10] & 0xff) * 256) | (this.reg[vreg + 11] & 0xff)) * 2;
                        v.sample_start = (((this.reg[vreg + 6] & 0xff) * 256) | (this.reg[vreg + 7] & 0xff)) * 2;
                        v.sample_end = (((this.reg[vreg + 8] & 0xff) * 256) | (this.reg[vreg + 9] & 0xff)) * 2;

//                        Debug.printf("219: play v %d mode %02x start %x loop %x end %x\n",
//                                offset >> 4, v.mode,
//                                find_sample(info, v.sample_start, v.bank, offset >> 4),
//                                find_sample(info, v.sample_loop, v.bank, offset >> 4),
//                                find_sample(info, v.sample_end, v.bank, offset >> 4));
                    } else {
                        v.sample_loop = ((this.reg[vreg + 10] & 0xff) << 8) | (this.reg[vreg + 11] & 0xff);
                        v.sample_start = ((this.reg[vreg + 6] & 0xff) << 8) | (this.reg[vreg + 7] & 0xff);
                        v.sample_end = ((this.reg[vreg + 8] & 0xff) << 8) | (this.reg[vreg + 9] & 0xff);
                    }
                } else {
                    v.key = 0;
                }
            }
        }
    }

    public enum Type {
        SYSTEM2,
        SYSTEM21,
        ASIC219;

        public static Type valueOf(int v) {
            return Arrays.stream(values()).filter(e -> e.ordinal() == v).findFirst().get();
        }
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
        if (this.pRomSize != romSize) {
            this.pRom = new byte[romSize];
            this.pRomSize = romSize;
            for (int i = 0; i < romSize; i++) this.pRom[i] = (byte) 0xff;
            //memset(this.pRom, 0xff, romSize);
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        if (dataLength >= 0) System.arraycopy(romData, 0, this.pRom, dataStart, dataLength);
    }

    public void writeRom2(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        if (this.pRomSize != romSize) {
            this.pRom = new byte[romSize];
            this.pRomSize = romSize;
            for (int i = 0; i < romSize; i++) this.pRom[i] = (byte) 0xff;
            //memset(this.pRom, 0xff, romSize);
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        if (dataLength >= 0) System.arraycopy(romData, srcStartAdr, this.pRom, dataStart, dataLength);

        //Debug.printf("c140_write_rom2:%d:%d:%d:%d:%d", chipId, romSize, dataStart, dataLength, srcStartAdr);
    }

    public void setMuteMask(int muteMask) {
        for (byte curChn = 0; curChn < MAX_VOICE; curChn++)
            this.voi[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
    }

    public void update(int[][] outputs, int samples) {
        int rvol, lvol;
        int dt;
        int sdt;
        int st, ed, sz;

        int pSampleData;
        int frequency, delta, offset, pos;
        int cnt, voiceCnt;
        int lastdt, prevdt, dltdt;
        float pbase = (float) this.baserate * 2.0f / (float) this.sampleRate;

        int[] lmix, rmix;

        if (samples > this.sampleRate) samples = this.sampleRate;

        // zap the contents of the mixer buffer
        for (int ind = 0; ind < samples; ind++) {
            this.mixerBufferLeft[ind] = 0;
            this.mixerBufferRight[ind] = 0;
        }
        if (this.pRom == null)
            return;

        // get the number of voices to update
        voiceCnt = (this.bankingType == Type.ASIC219) ? 16 : 24;

        //--- audio update
        for (int i = 0; i < voiceCnt; i++) {
            Voice v = this.voi[i];
            int vreg = i * 16;

            if (v.key == 0 || v.muted != 0) continue;
            frequency = ((this.reg[vreg + 2] & 0xff) << 8) | (this.reg[vreg + 3] & 0xff);

            // Abort Voice if no frequency value set
            if (frequency == 0) continue;

            // Delta =  frequency * ((8MHz/374)*2 / sample rate)
            delta = (int) (frequency * pbase);

            // Calculate left/right channel volumes
            lvol = ((this.reg[vreg + 1] & 0xff) << 5) / MAX_VOICE; //32ch . 24ch
            rvol = ((this.reg[vreg + 0] & 0xff) << 5) / MAX_VOICE;

            // Set mixer outputs base pointers
            lmix = this.mixerBufferLeft;
            rmix = this.mixerBufferRight;

            // Retrieve sample start/end and calculate size
            st = (int) v.sample_start;
            ed = (int) v.sample_end;
            sz = ed - st;

            // Retrieve base pointer to the sample data
            pSampleData = findSample(st, v.bank, i);

            // Fetch back previous data pointers
            offset = v.ptoffset;
            pos = v.pos;
            lastdt = v.lastdt;
            prevdt = v.prevdt;
            dltdt = v.dltdt;

            // Switch on data type - compressed PCM is only for C140
            if ((v.mode & 8) != 0 && (this.bankingType != Type.ASIC219)) {
                // compressed PCM (maybe correct...)
                // Loop for enough to fill sample buffer as requested
                for (int j = 0; j < samples; j++) {
                    offset += delta;
                    cnt = (offset >> 16) & 0x7fff;
                    offset &= 0xffff;
                    pos += cnt;
                    // Check for the end of the sample
                    if (pos >= sz) {
                        //Debug.printf("C140 pos[%x]", pos);
                        //debugCnt = 20;
                        // Check if it's a looping sample, either stop or loop
                        if ((v.mode & 0x10) != 0) {
                            pos = v.sample_loop - st;
                        } else {
                            v.key = 0;
                            break;
                        }
                    }

                    // Read the chosen sample byte
                    dt = this.pRom[pSampleData + pos];

                    // decompress to 13bit range         //2000.06.26 CAB
                    sdt = dt >> 3; // signed
                    if (sdt < 0) sdt = (sdt << (dt & 7)) - this.pcmTbl[dt & 7];
                    else sdt = (sdt << (dt & 7)) + this.pcmTbl[dt & 7];

                    prevdt = lastdt;
                    lastdt = sdt;
                    dltdt = (lastdt - prevdt);

                    // Caclulate the sample value
                    dt = ((dltdt * offset) >> 16) + prevdt;

                    // Write the data to the sample buffers
                    lmix[j] += (dt * lvol) >> (5 + 5);
                    rmix[j] += (dt * rvol) >> (5 + 5);
                }
            } else {
                // linear 8bit signed PCM
                for (int j = 0; j < samples; j++) {
                    offset += delta;
                    cnt = (offset >> 16) & 0x7fff;
                    offset &= 0xffff;
                    pos += cnt;
                    // Check for the end of the sample
                    if (pos >= sz) {
                        //Debug.printf("C140 pos[%x]", pos);
                        //debugCnt = 20;
                        // Check if it's a looping sample, either stop or loop
                        if ((v.mode & 0x10) != 0) {
                            pos = v.sample_loop - st;
                        } else {
                            v.key = 0;
                            break;
                        }
                    }

                    if (cnt != 0) {
                        prevdt = lastdt;

                        if (this.bankingType == Type.ASIC219) {
                            lastdt = this.pRom[pSampleData + (pos ^ 0x01)];

                            // Sign + magnitude format
                            if ((v.mode & 0x01) != 0 && ((lastdt & 0x80) != 0)) {
                                lastdt = -(lastdt & 0x7f);
                            }
                            // Sign flip
                            if ((v.mode & 0x40) != 0)
                                lastdt = -lastdt;

                        } else {
                            lastdt = this.pRom[pSampleData + pos];
                        }

                        dltdt = lastdt - prevdt;
                    }

                    // Caclulate the sample value
                    dt = ((dltdt * offset) >> 16) + prevdt;

                    // Write the data to the sample buffers
                    lmix[j] += (dt * lvol) >> 5;
                    rmix[j] += (dt * rvol) >> 5;
                }
            }

            // Save positional data for next Callback
            v.ptoffset = offset;
            v.pos = pos;
            v.lastdt = lastdt;
            v.prevdt = prevdt;
            v.dltdt = dltdt;
        }

        // render to MAME's stream buffer
        lmix = this.mixerBufferLeft;
        rmix = this.mixerBufferRight;

        int[] dest1 = outputs[0];
        int[] dest2 = outputs[1];
        for (int i = 0; i < samples; i++) {
            dest1[i] = lmix[i] << 3;
            dest2[i] = rmix[i] << 3;
//            if (debugCnt > 0) {
//                debugCnt--;
//                Debug.printf("%x  %d", lmix[i]);
//            }
        }
    }

    public void start(int clockValue, int sampleRate, Type bankingType) {
        if (clockValue < 1000000)
            this.baserate = clockValue;
        else
            this.baserate = clockValue / 384; // based on MAME's notes on Namco System II
        this.sampleRate = sampleRate;
        this.bankingType = bankingType;

        this.pRomSize = 0x00;
        this.pRom = null;

        // make decompress pcm table 2000.06.26 CAB
        int segbase = 0;
        for (int i = 0; i < 8; i++) {
            this.pcmTbl[i] = segbase; //segment base value
            segbase += 16 << i;
        }

        // done at device_reset
        for (int i = 0; i < MAX_VOICE; i++) this.voi[i].init_voice();

        // allocate a Pair of buffers to mix into - 1 second's worth should be more than enough
        this.mixerBufferLeft = new int[this.sampleRate];
        this.mixerBufferRight = new int[this.sampleRate];

        for (int i = 0; i < MAX_VOICE; i++)
            this.voi[i].muted = 0x00;
    }

    public void stop() {
        this.pRom = null;
        this.mixerBufferLeft = null;
        this.mixerBufferRight = null;
    }

    public void setBase(byte[] base) {
        pRom = base;
    }
}
