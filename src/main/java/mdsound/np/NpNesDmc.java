package mdsound.np;

import java.util.Random;


// Ported from NSFPlay to VGMPlay (including C++ . C conversion)
// by Valley Bell on 25 September 2013
// Updated to NSFPlay 2.3 on 26 September 2013
// (Note: Encoding is UTF-8)
//
// NSFPlay tag2.4のソースを移植 2021/08/14
//
// https://github.com/bbbradsmith/nsfplay/releases/tag/2.4
//
public class NpNesDmc {

    public NpNesApu nes_apu = null;

    // Master Clock: 21477272 (NTSC)
    // APU Clock = Master Clock / 12
    private static final double DEFAULT_CLOCK = 1789772.0;
    private static final int DEFAULT_CLK_PAL = 1662607;
    private static final int DEFAULT_RATE = 44100;

    /**
     * Bottom Half of APU
     */
    public enum OPT {
        ENABLE_4011 /*= 0*/,
        ENABLE_PNOISE,
        UNMUTE_ON_RESET,
        DPCM_ANTI_CLICK,
        NONLINEAR_MIXER,
        RANDOMIZE_NOISE,
        TRI_MUTE,
        RANDOMIZE_TRI,
        DPCM_REVERSE,
        END
    }

    public Counter counter_ = new Counter();

    private Random rnd = new Random();

    public int[][][][] tndTable; //[2][16][16][128];

    public int[] option = new int[10];// OPT_END];
    public int mask;
    public int[][] sm = new int[][] {new int[3], new int[3]};
    public byte[] reg = new byte[0x20];
    public int lenReg;
    public int adrReg;
    public Device orgMemory;
    public byte[] memory;
    public int ptrMemory;
    public int[] out = new int[3];
    public int daddress;
    public int dLength;
    public int[] data = new int[1];
    public boolean empty;
    public short damp;
    public int dacLsb;
    public boolean dmcPop;
    public int dmcPopOffset;
    public int dmcPopFollow;
    public double clock;
    public int rate;
    public int pal;
    public int mode;
    public boolean irq;

    // frequency dividers
    public int[] counter = new int[3];
    // triangle phase
    public int tPhase;
    // noise frequency
    public int nFreq;
    // DPCM frequency
    public int dFreq;

    public int triFreq;
    public int linearCounter;
    public int linearCounterReload;
    public boolean linearCounterHalt;
    public boolean linearCounterControl;

    public int noiseVolume;
    public int noise, noiseTap;

    // noise envelope
    public boolean envelopeLoop;
    public boolean envelopeDisable;
    public boolean envelopeWrite;
    public int envelopeDivPeriod;
    public int envelopeDiv;
    public int envelopeCounter;

    public boolean[] enable = new boolean[2];
    // 0=tri, 1=noise
    public int[] lengthCounter = new int[2];

    // frame sequencer

    // apu is clocked by DMC's frame sequencer
    public NpNesApu apu;
    // current cycle count
    public int frameSequenceCount;
    // CPU cycles per FrameSequence
    public int frameSequenceLength;
    // current step of frame sequence
    public int frameSequenceStep;
    // 4/5 steps per frame
    public int frameSequenceSteps;
    public boolean frameIrq;
    public boolean frameIrqEnable;

    //public NES_CPU cpu; // IRQ needs CPU access

    public Counter tickCount = new Counter();
    public int tickLast;

    private int getDamp() {
        return (this.damp << 1) | this.dacLsb;
    }

    private static final int GETA_BITS = 20;
    private static final int[][] wavLenTable = new int[][] {
            new int[] { // NTSC
                    4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068
            },
            new int[] { // PAL
                    4, 8, 14, 30, 60, 88, 118, 148, 188, 236, 354, 472, 708, 944, 1890, 3778
            }
    };

    private static final int[][] freqTable = new int[][] {
            new int[] { // NTSC
                    428, 380, 340, 320, 286, 254, 226, 214, 190, 160, 142, 128, 106, 84, 72, 54
            },
            new int[] { // PAL
                    398, 354, 316, 298, 276, 236, 210, 198, 176, 148, 132, 118, 98, 78, 66, 50
            }
    };

    private static final int[] BitReverse = new int[] {
            0x00, 0x80, 0x40, 0xC0, 0x20, 0xA0, 0x60, 0xE0, 0x10, 0x90, 0x50, 0xD0, 0x30, 0xB0, 0x70, 0xF0,
            0x08, 0x88, 0x48, 0xC8, 0x28, 0xA8, 0x68, 0xE8, 0x18, 0x98, 0x58, 0xD8, 0x38, 0xB8, 0x78, 0xF8,
            0x04, 0x84, 0x44, 0xC4, 0x24, 0xA4, 0x64, 0xE4, 0x14, 0x94, 0x54, 0xD4, 0x34, 0xB4, 0x74, 0xF4,
            0x0C, 0x8C, 0x4C, 0xCC, 0x2C, 0xAC, 0x6C, 0xEC, 0x1C, 0x9C, 0x5C, 0xDC, 0x3C, 0xBC, 0x7C, 0xFC,
            0x02, 0x82, 0x42, 0xC2, 0x22, 0xA2, 0x62, 0xE2, 0x12, 0x92, 0x52, 0xD2, 0x32, 0xB2, 0x72, 0xF2,
            0x0A, 0x8A, 0x4A, 0xCA, 0x2A, 0xAA, 0x6A, 0xEA, 0x1A, 0x9A, 0x5A, 0xDA, 0x3A, 0xBA, 0x7A, 0xFA,
            0x06, 0x86, 0x46, 0xC6, 0x26, 0xA6, 0x66, 0xE6, 0x16, 0x96, 0x56, 0xD6, 0x36, 0xB6, 0x76, 0xF6,
            0x0E, 0x8E, 0x4E, 0xCE, 0x2E, 0xAE, 0x6E, 0xEE, 0x1E, 0x9E, 0x5E, 0xDE, 0x3E, 0xBE, 0x7E, 0xFE,
            0x01, 0x81, 0x41, 0xC1, 0x21, 0xA1, 0x61, 0xE1, 0x11, 0x91, 0x51, 0xD1, 0x31, 0xB1, 0x71, 0xF1,
            0x09, 0x89, 0x49, 0xC9, 0x29, 0xA9, 0x69, 0xE9, 0x19, 0x99, 0x59, 0xD9, 0x39, 0xB9, 0x79, 0xF9,
            0x05, 0x85, 0x45, 0xC5, 0x25, 0xA5, 0x65, 0xE5, 0x15, 0x95, 0x55, 0xD5, 0x35, 0xB5, 0x75, 0xF5,
            0x0D, 0x8D, 0x4D, 0xCD, 0x2D, 0xAD, 0x6D, 0xED, 0x1D, 0x9D, 0x5D, 0xDD, 0x3D, 0xBD, 0x7D, 0xFD,
            0x03, 0x83, 0x43, 0xC3, 0x23, 0xA3, 0x63, 0xE3, 0x13, 0x93, 0x53, 0xD3, 0x33, 0xB3, 0x73, 0xF3,
            0x0B, 0x8B, 0x4B, 0xCB, 0x2B, 0xAB, 0x6B, 0xEB, 0x1B, 0x9B, 0x5B, 0xDB, 0x3B, 0xBB, 0x7B, 0xFB,
            0x07, 0x87, 0x47, 0xC7, 0x27, 0xA7, 0x67, 0xE7, 0x17, 0x97, 0x57, 0xD7, 0x37, 0xB7, 0x77, 0xF7,
            0x0F, 0x8F, 0x4F, 0xCF, 0x2F, 0xAF, 0x6F, 0xEF, 0x1F, 0x9F, 0x5F, 0xDF, 0x3F, 0xBF, 0x7F, 0xFF,
    };

    public void setMask(int m) {
        this.mask = m;
    }

    public void setStereoMix(int trk, short mixl, short mixr) {
        if (trk < 0) return;
        if (trk > 2) return;
        this.sm[0][trk] = mixl;
        this.sm[1][trk] = mixr;
    }

    //TrackInfo getTrackInfo(int trk) {
    //    switch (trk) {
    //        case 0:
    //            trkinfo[trk].max_volume = 255;
    //            trkinfo[0].key = (linear_counter > 0 && length_counter[0] > 0 && enable[0]);
    //            trkinfo[0].volume = 0;
    //            trkinfo[0]._freq = tri_freq;
    //            if (trkinfo[0]._freq)
    //                trkinfo[0].freq = clock / 32 / (trkinfo[0]._freq + 1);
    //            else
    //                trkinfo[0].freq = 0;
    //            trkinfo[0].tone = -1;
    //            trkinfo[0].output = out[0];
    //            break;
    //        case 1:
    //            trkinfo[1].max_volume = 15;
    //            trkinfo[1].volume = noise_volume + (envelope_disable ? 0 : 0x10) + (envelope_loop ? 0x20 : 0);
    //            trkinfo[1].key = length_counter[1] > 0 && enable[1] &&
    //                             (envelope_disable ? (noise_volume > 0) : (envelope_counter > 0));
    //            trkinfo[1]._freq = reg[0x400e - 0x4008] & 0xF;
    //            trkinfo[1].freq = clock / double(wavlen_table[pal][trkinfo[1]._freq] * ((noise_tap & (1 << 6)) ? 93 : 1));
    //            trkinfo[1].tone = noise_tap & (1 << 6);
    //            trkinfo[1].output = out[1];
    //            break;
    //        case 2:
    //            trkinfo[2].max_volume = 127;
    //            trkinfo[2].volume = reg[0x4011 - 0x4008] & 0x7F;
    //            trkinfo[2].key = dlength > 0;
    //            trkinfo[2]._freq = reg[0x4010 - 0x4008] & 0xF;
    //            trkinfo[2].freq = clock / double(freq_table[pal][trkinfo[2]._freq]);
    //            trkinfo[2].tone = (0xc000 | (adr_reg << 6));
    //            trkinfo[2].output = (damp << 1) | dac_lsb;
    //            break;
    //        default:
    //            return NULL;
    //    }
    //    return trkinfo[trk];
    //}

    private void sequenceFrame(int s) {
        //Debug.printf("FrameSequence: %d\n",s);

        if (s > 3) return; // no operation in step 4

        if (this.apu != null) {
            this.apu.sequenceFrame(s);
        }

        if (s == 0 && (this.frameSequenceSteps == 4)) {
            if (this.frameIrqEnable) this.frameIrq = true;
            //cpu.updateIRQ(NES_CPU::IRQD_FRAME, frame_irq & frame_irq_enable);
        }

        // 240hz clock
        // triangle linear counter
        if (this.linearCounterHalt) {
            this.linearCounter = this.linearCounterReload;
        } else {
            if (this.linearCounter > 0) --this.linearCounter;
        }
        if (!this.linearCounterControl) {
            this.linearCounterHalt = false;
        }

        // $4009 unuse address
        //this.reg[1] = (byte)(
        //    (this.linear_counter != 0 ? 4 : 0) //triangle
        //    | (this.length_counter[1]!=0 ? 8:0) //noise
        //    | (this.active ? 0x10 : 0) //dmc
        //    );

        // noise envelope
        boolean divider = false;
        if (this.envelopeWrite) {
            this.envelopeWrite = false;
            this.envelopeCounter = 15;
            this.envelopeDiv = 0;
        } else {
            ++this.envelopeDiv;
            if (this.envelopeDiv > this.envelopeDivPeriod) {
                divider = true;
                this.envelopeDiv = 0;
            }
        }
        if (divider) {
            if (this.envelopeLoop && this.envelopeCounter == 0)
                this.envelopeCounter = 15;
            else if (this.envelopeCounter > 0)
                --this.envelopeCounter; // TODO: Make this work.
        }

        // 120hz clock
        if ((s & 1) == 0) {
            // triangle length counter
            if (!this.linearCounterControl && (this.lengthCounter[0] > 0))
                --this.lengthCounter[0];

            // noise length counter
            if (!this.envelopeLoop && (this.lengthCounter[1] > 0))
                --this.lengthCounter[1];
        }
    }

    private static final int[] triTbl = new int[] {
            15, 14, 13, 12, 11, 10, 9, 8,
            7, 6, 5, 4, 3, 2, 1, 0,
            0, 1, 2, 3, 4, 5, 6, 7,
            8, 9, 10, 11, 12, 13, 14, 15
    };

    /** 三角波チャンネルの計算 戻り値は0-15 */
    private int calcTri(int clocks) {
        byte tri = 0;
        if (this.linearCounter > 0 && this.lengthCounter[0] > 0
                && (this.option[OPT.TRI_MUTE.ordinal()] == 0 || this.triFreq > 0)) {
            tri = 1;
            this.counter[0] -= clocks;
            while (this.counter[0] < 0) {
                this.tPhase = (this.tPhase + 1) & 31;
                this.counter[0] += this.triFreq + 1;
            }
        }
        // Note: else-block added by VB
        //else if (this.option[(int)OPT.OPT_TRI_NULL] != 0) {
        //    if (this.tphase != 0 && this.tphase < 31) {
        //        // Finish the Triangle wave to prevent clicks.
        //        this.counter[0] += clocks;
        //        while (this.counter[0] > this.tri_freq && this.tphase != 0)
        //        {
        //            this.tphase = (this.tphase + 1) & 31;
        //            this.counter[0] -= (this.tri_freq + 1);
        //        }
        //    }
        //}

        this.reg[0x10] = tri;
        return triTbl[this.tPhase];
    }

    /** ノイズチャンネルの計算 戻り値は0-127
    // 低サンプリングレートで合成するとエイリアスノイズが激しいので
    // ノイズだけはこの関数内で高クロック合成し、簡易なサンプリングレート
    // 変換を行っている。
     */
    private int calcNoise(int clocks) {
        byte noi = 1;

        int env, last, count, accum, clocksAccum;

        env = this.envelopeDisable ? this.noiseVolume : this.envelopeCounter;
        if (this.lengthCounter[1] < 1) {
            env = 0;
        }

        if (env == 0) noi = 0;
        this.reg[0x11] = noi;

        last = (this.noise & 0x4000) != 0 ? 0 : env;

        if (clocks < 1) return last;

        // simple anti-aliasing (noise requires it, even when oversampling is off)
        count = 0;
        accum = this.counter[1] * last;
        int accumClocks = this.counter[1];
        //# ifdef _DEBUG
        //            int start_clocks = counter[1];
        //#endif
        if (this.counter[1] < 0) { // only happens on startup when using the randomize noise option
            accum = 0;
            accumClocks = 0;
        }

        this.counter[1] -= clocks;
        // assert(this.nfreq > 0); // prevent infinite loop
        while (this.counter[1] < 0) {
            // tick the noise generator
            int feedback = (this.noise & 1) ^ (((this.noise & this.noiseTap) != 0) ? 1 : 0);
            this.noise = (this.noise >> 1) | (feedback << 14);

            last = (this.noise & 0x4000) != 0 ? 0 : env;
            accum += (last * this.nFreq);
            this.counter[1] += this.nFreq;
            ++count;
            accumClocks += this.nFreq;
        }

        if (count < 1) { // no change over interval, don't anti-alias
            return last;
        }

        accum -= last * this.counter[1]; // remove these samples which belong in the next calc
        accumClocks -= this.counter[1];
        //# ifdef _DEBUG
        //if (start_clocks >= 0) assert(accumClocks == clocks); // these should be equal
        //#endif

        int average = accum / accumClocks;
        //assert(average <= 15); // above this would indicate overflow
        return average;
    }

    // Tick the DMC for the number of clocks, and return output counter;
    private int calcDmc(int clocks) {
        this.counter[2] -= clocks;
        //assert(dfreq > 0); // prevent infinite loop
        while (this.counter[2] < 0) {
            this.counter[2] += this.dFreq;

            if (this.data[0] > 0x100) { // data = 0x100 when shift register is empty
                if (!this.empty) {
                    if ((this.data[0] & 1) != 0 && (this.damp < 63))
                        this.damp++;
                    else if ((this.data[0] & 1) == 0 && (0 < this.damp))
                        this.damp--;
                }
                this.data[0] >>= 1;
            }

            if (this.data[0] <= 0x100) { // shift register is empty
                if (this.dLength > 0) {
                    this.data[0] = this.memory[this.daddress + this.ptrMemory];
                    //cpu.StealCycles(4); // DMC read takes 3 or 4 CPU cycles, usually 4
                    // (checking for the 3-cycle case would require sub-instruction emulation)
                    this.data[0] &= 0xFF; // read 8 bits
                    if (this.option[OPT.DPCM_REVERSE.ordinal()] != 0) this.data[0] = BitReverse[this.data[0]];
                    this.data[0] |= 0x10000; // use an extra bit to signal end of data
                    this.empty = false;
                    this.daddress = ((this.daddress + 1) & 0xFFFF) | 0x8000;
                    --this.dLength;
                    if (this.dLength == 0) {
                        if ((this.mode & 1) != 0) { // looped DPCM = auto-reload
                            this.daddress = ((this.adrReg << 6) | 0xC000);
                            this.dLength = (this.lenReg << 4) + 1;
                        } else if ((this.mode & 2) != 0) { // IRQ and not looped
                            this.irq = true;
                            //cpu.updateIRQ(NES_CPU::IRQD_DMC, true);
                        }
                    }
                } else {
                    this.data[0] = 0x10000; // DMC will do nothing
                    this.empty = true;
                }
            }
        }

        this.reg[0x12] = (byte) (this.empty ? 0 : 1);// dpc;
        return (this.damp << 1) + this.dacLsb;
    }

    private int calcDmcOrg(int clocks) {
        this.counter[2] -= clocks;
        //assert(dfreq > 0); // prevent infinite loop
        while (this.counter[2] < 0) {
            this.counter[2] += this.dFreq;

            if (this.data[0] > 0x100) { // data = 0x100 when shift register is empty
                if (!this.empty) {
                    if ((this.data[0] & 1) != 0 && (this.damp < 63))
                        this.damp++;
                    else if ((this.data[0] & 1) == 0 && (0 < this.damp))
                        this.damp--;
                }
                this.data[0] >>= 1;
            }

            if (this.data[0] <= 0x100) { // shift register is empty
                if (this.dLength > 0) {
                    this.orgMemory.read(this.daddress, this.data);
                    //this.data = this.memory[this.daddress + this.ptrMemory];
                    //cpu.StealCycles(4); // DMC read takes 3 or 4 CPU cycles, usually 4
                    // (checking for the 3-cycle case would require sub-instruction emulation)
                    this.data[0] &= 0xFF; // read 8 bits
                    if (this.option[OPT.DPCM_REVERSE.ordinal()] != 0) this.data[0] = BitReverse[this.data[0]];
                    this.data[0] |= 0x10000; // use an extra bit to signal end of data
                    this.empty = false;
                    this.daddress = ((this.daddress + 1) & 0xFFFF) | 0x8000;
                    --this.dLength;
                    if (this.dLength == 0) {
                        if ((this.mode & 1) != 0) { // looped DPCM = auto-reload
                            this.daddress = ((this.adrReg << 6) | 0xC000);
                            this.dLength = (this.lenReg << 4) + 1;
                        } else if ((this.mode & 2) != 0) { // IRQ and not looped
                            this.irq = true;
                            //cpu.UpdateIRQ(NES_CPU::IRQD_DMC, true);
                        }
                    }
                } else {
                    this.data[0] = 0x10000; // DMC will do nothing
                    this.empty = true;
                }
            }
        }

        this.reg[0x12] = (byte) (this.empty ? 0 : 1);// dpc;
        return (this.damp << 1) + this.dacLsb;
    }

    public void tickFrameSequence(int clocks) {
        this.frameSequenceCount += clocks;
        while (this.frameSequenceCount > this.frameSequenceLength) {
            sequenceFrame(this.frameSequenceStep);
            this.frameSequenceCount -= this.frameSequenceLength;
            ++this.frameSequenceStep;
            if (this.frameSequenceStep >= this.frameSequenceSteps)
                this.frameSequenceStep = 0;
        }
    }

    public void tick(int clocks) {
        this.out[0] = calcTri(clocks);
        this.out[1] = calcNoise(clocks);
        this.out[2] = calcDmc(clocks);
    }

    public void orgTick(int clocks) {
        this.out[0] = calcTri(clocks);
        this.out[1] = calcNoise(clocks);
        this.out[2] = calcDmcOrg(clocks);
    }

    private int[] m = new int[3];

    public int render(int[] b) {
        int clocks;

        this.tickCount.iup(); // increase counter (overflows after 255)
        clocks = (this.tickCount.value() - this.tickLast) & 0xFF;
        tickFrameSequence(clocks);
        tick(clocks);
        this.tickLast = this.tickCount.value();

        this.out[0] = (this.mask & 1) != 0 ? 0 : this.out[0];
        this.out[1] = (this.mask & 2) != 0 ? 0 : this.out[1];
        this.out[2] = (this.mask & 4) != 0 ? 0 : this.out[2];

        m[0] = this.tndTable[0][this.out[0]][0][0];
        m[1] = this.tndTable[0][0][this.out[1]][0];
        m[2] = this.tndTable[0][0][0][this.out[2]];

        if (this.option[OPT.NONLINEAR_MIXER.ordinal()] != 0) {
            int _ref = m[0] + m[1] + m[2];
            int voltage = this.tndTable[1][this.out[0]][this.out[1]][this.out[2]];
            int i;
            if (_ref != 0) {
                for (i = 0; i < 3; ++i)
                    m[i] = (m[i] * voltage) / _ref;
            } else {
                for (i = 0; i < 3; ++i)
                    m[i] = voltage;
            }
        }

        // anti-click nullifies any 4011 write but preserves nonlinearity
        if (this.option[OPT.DPCM_ANTI_CLICK.ordinal()] != 0) {
            if (this.dmcPop) {// $4011 will cause pop this frame
                // adjust offset to counteract pop
                this.dmcPopOffset += this.dmcPopFollow - m[2];
                this.dmcPop = false;

                // prevent overflow, keep headspace at edges
                final int OFFSET_MAX = (1 << 30) - (4 << 16);
                if (this.dmcPopOffset > OFFSET_MAX) this.dmcPopOffset = OFFSET_MAX;
                if (this.dmcPopOffset < -OFFSET_MAX) this.dmcPopOffset = -OFFSET_MAX;
            }
            this.dmcPopFollow = m[2]; // remember previous position

            m[2] += this.dmcPopOffset; // apply offset

            // TODO implement this in a better way
            // roll off offset (not ideal, but prevents overflow)
            if (this.dmcPopOffset > 0) --this.dmcPopOffset;
            else if (this.dmcPopOffset < 0) ++this.dmcPopOffset;
        }

        b[0] = m[0] * this.sm[0][0];
        b[0] += m[1] * this.sm[0][1];
        b[0] += m[2] * this.sm[0][2];
        b[0] >>= 5;

        b[1] = m[0] * this.sm[1][0];
        b[1] += m[1] * this.sm[1][1];
        b[1] += m[2] * this.sm[1][2];
        b[1] >>= 5;

        return 2;
    }

    public int renderOrg(int[] b) {
        //int clocks;

        this.out[0] = (this.mask & 1) != 0 ? 0 : this.out[0];
        this.out[1] = (this.mask & 2) != 0 ? 0 : this.out[1];
        this.out[2] = (this.mask & 4) != 0 ? 0 : this.out[2];

        m[0] = this.tndTable[0][this.out[0]][0][0];
        m[1] = this.tndTable[0][0][this.out[1]][0];
        m[2] = this.tndTable[0][0][0][this.out[2]];

        if (this.option[OPT.NONLINEAR_MIXER.ordinal()] != 0) {
            int _ref = m[0] + m[1] + m[2];
            int voltage = this.tndTable[1][this.out[0]][this.out[1]][this.out[2]];
            int i;
            if (_ref != 0) {
                for (i = 0; i < 3; ++i)
                    m[i] = (m[i] * voltage) / _ref;
            } else {
                for (i = 0; i < 3; ++i)
                    m[i] = voltage;
            }
        }

        // anti-click nullifies any 4011 write but preserves nonlinearity
        if (this.option[OPT.DPCM_ANTI_CLICK.ordinal()] != 0) {
            if (this.dmcPop) { // $4011 will cause pop this frame
                // adjust offset to counteract pop
                this.dmcPopOffset += this.dmcPopFollow - m[2];
                this.dmcPop = false;

                // prevent overflow, keep headspace at edges
                int OFFSET_MAX = ((1 << 30) - (4 << 16));
                if (this.dmcPopOffset > OFFSET_MAX) this.dmcPopOffset = OFFSET_MAX;
                if (this.dmcPopOffset < -OFFSET_MAX) this.dmcPopOffset = -OFFSET_MAX;
            }
            this.dmcPopFollow = m[2]; // remember previous position

            m[2] += this.dmcPopOffset; // apply offset

            // TODO implement this in a better way
            // roll off offset (not ideal, but prevents overflow)
            if (this.dmcPopOffset > 0) --this.dmcPopOffset;
            else if (this.dmcPopOffset < 0) ++this.dmcPopOffset;
        }

        b[0] = m[0] * this.sm[0][0];
        b[0] += m[1] * this.sm[0][1];
        b[0] += m[2] * this.sm[0][2];
        b[0] >>= 7 - 3;

        b[1] = m[0] * this.sm[1][0];
        b[1] += m[1] * this.sm[1][1];
        b[1] += m[2] * this.sm[1][2];
        b[1] >>= 7 - 3;

        return 2;
    }

    public void setClock(double c) {
        this.clock = (int) (c);

        if (Math.abs(this.clock - DEFAULT_CLK_PAL) <= 1000) // check for approximately DEFAULT_CLK_PAL
            setPal(true);
        else
            setPal(false);
    }

    public void setRate(double r) {
        this.rate = (int) (r != 0 ? r : DEFAULT_RATE);

        this.tickCount.init(this.clock, this.rate);
        this.tickLast = 0;
    }

    private void setPal(boolean is_pal) {
        this.pal = (is_pal ? 1 : 0);
        // set CPU cycles in frame_sequence
        this.frameSequenceLength = is_pal ? 8314 : 7458;
    }

    public void setAPU(NpNesApu apu_) {
        this.apu = apu_;
    }

    // Initializing TRI, NOISE, DPCM mixing table
    private void initializeTNDTable(double wt, double wn, double wd) {
        // volume adjusted by 0.95 based on empirical measurements
        final double MASTER = 8192.0 * 0.95;
        // truthfully, the nonlinear curve does not appear to match well
        // with my tests, triangle in particular seems too quiet relatively.
        // do more testing of the APU/DMC DAC later

        // Linear Mixer
        for (int t = 0; t < 16; t++) {
            for (int n = 0; n < 16; n++) {
                for (int d = 0; d < 128; d++) {
                    this.tndTable[0][t][n][d] = (int) (MASTER * (3.0 * t + 2.0 * n + d) / 208.0);
                }
            }
        }
        // Non-Linear Mixer
        this.tndTable[1][0][0][0] = 0;
        for (int t = 0; t < 16; t++) {
            for (int n = 0; n < 16; n++) {
                for (int d = 0; d < 128; d++) {
                    if (t != 0 || n != 0 || d != 0)
                        this.tndTable[1][t][n][d] = (int) ((MASTER * 159.79) / (100.0 + 1.0 / ((double) t / wt + (double) n / wn + (double) d / wd)));
                }
            }
        }
    }

    public void reset() {
        this.mask = 0;

        initializeTNDTable(8227, 12241, 22638);

        this.counter[0] = 0;
        this.counter[1] = 0;
        this.counter[2] = 0;
        this.tPhase = 0;
        this.nFreq = wavLenTable[0][0];
        this.dFreq = freqTable[0][0];
        this.triFreq = 0;
        this.linearCounter = 0;
        this.linearCounterReload = 0;
        this.linearCounterHalt = false;
        this.linearCounterControl = false;
        this.noiseVolume = 0;
        this.noise = 0;
        this.noiseTap = 0;
        this.envelopeLoop = false;
        this.envelopeDisable = false;
        this.envelopeWrite = false;
        this.envelopeDivPeriod = 0;
        this.envelopeDiv = 0;
        this.envelopeCounter = 0;
        this.enable[0] = false;
        this.enable[1] = false;
        this.lengthCounter[0] = 0;
        this.lengthCounter[1] = 0;
        this.frameIrq = false;
        this.frameIrqEnable = false;
        this.frameSequenceCount = 0;
        this.frameSequenceSteps = 4;
        this.frameSequenceStep = 0;
        //cpu.updateIRQ(NES_CPU::IRQD_FRAME, false);

        for (int i = 0; i < 0x0f; i++)
            write(0x4008 + i, 0);
        write(0x4017, 0x40);

        this.irq = false;
        write(0x4015, 0x00);
        if (this.option[OPT.UNMUTE_ON_RESET.ordinal()] != 0)
            write(0x4015, 0x0f);
        //cpu.updateIRQ(NES_CPU::IRQD_DMC, false);

        this.out[0] = this.out[1] = this.out[2] = 0;
        this.damp = 0;
        this.dmcPop = false;
        this.dmcPopOffset = 0;
        this.dmcPopFollow = 0;
        this.dacLsb = 0;
        this.data[0] = 0x100;
        this.empty = true;
        this.adrReg = 0;
        this.dLength = 0;
        this.lenReg = 0;
        this.daddress = 0;
        this.noise = 1;
        this.noiseTap = (1 << 1);
        if (this.option[OPT.RANDOMIZE_NOISE.ordinal()] != 0) {
            this.noise |= rnd.nextInt();
            this.counter[1] = -(rnd.nextInt() & 511);
        }
        if (this.option[OPT.RANDOMIZE_TRI.ordinal()] != 0) {
            this.tPhase |= rnd.nextInt() & 31;
            this.counter[0] = -(rnd.nextInt() & 2047);
        }

        setRate(this.rate);
    }

    public void setMemory(byte[] r, int ptr) {
        this.memory = r;
        this.ptrMemory = ptr;
    }

    public void setMemoryOrg(Device r) {
        this.orgMemory = r;
    }

    public void setOption(int id, int val) {
        if (id < OPT.END.ordinal()) {
            this.option[id] = val;
            if (id == OPT.NONLINEAR_MIXER.ordinal())
                initializeTNDTable(8227, 12241, 22638);
        }
    }

    private static final byte[] lengthTable = new byte[] {
            0x0A, (byte) 0xFE,
            0x14, 0x02,
            0x28, 0x04,
            0x50, 0x06,
            (byte) 0xA0, 0x08,
            0x3C, 0x0A,
            0x0E, 0x0C,
            0x1A, 0x0E,
            0x0C, 0x10,
            0x18, 0x12,
            0x30, 0x14,
            0x60, 0x16,
            (byte) 0xC0, 0x18,
            0x48, 0x1A,
            0x10, 0x1C,
            0x20, 0x1E
    };

    public boolean write(int adr, int val) {
        if (adr == 0x4015) {
            this.enable[0] = (val & 4) != 0;
            this.enable[1] = (val & 8) != 0;

            if (!this.enable[0]) {
                this.lengthCounter[0] = 0;
                //this.tphase = 0; // KUMA:止めたら出力も0にしたい
            }
            if (!this.enable[1]) {
                this.lengthCounter[1] = 0;
            }

            if ((val & 16) != 0 && this.dLength == 0) {
                //this.enable[2] = this.active = true;
                this.daddress = (0xC000 | (this.adrReg << 6));
                this.dLength = (this.lenReg << 4) + 1;
                //this.irq = false;
            } else if ((val & 16) == 0) {
                this.dLength = 0;
                //.enable[2] = this.active = false;
            }

            this.irq = false;
            //cpu.updateIRQ(NES_CPU::IRQD_DMC, false);

            this.reg[adr - 0x4008] = (byte) val;
            return true;
        }

        if (adr == 0x4017) {
            //Debug.printf("4017 = %02X\n", val);
            this.frameIrqEnable = ((val & 0x40) != 0x40);
            if (this.frameIrqEnable) this.frameIrq = false;
            //cpu.updateIRQ(NES_CPU::IRQD_FRAME, false);

            this.frameSequenceCount = 0;
            if ((val & 0x80) != 0) {
                this.frameSequenceSteps = 5;
                this.frameSequenceStep = 0;
                sequenceFrame(this.frameSequenceStep);
                ++this.frameSequenceStep;
            } else {
                this.frameSequenceSteps = 4;
                this.frameSequenceStep = 1;
            }
        }

        if (adr < 0x4008 || 0x4013 < adr)
            return false;

        this.reg[adr - 0x4008] = (byte) (val & 0xff);

        //Debug.printf("$%04X %02X\n", adr, val);

        switch (adr) {

        // tri

        case 0x4008:
            this.linearCounterControl = ((val >> 7) & 1) != 0;
            this.linearCounterReload = val & 0x7F;
            break;

        case 0x4009:
            break;

        case 0x400a:
            this.triFreq = val | (this.triFreq & 0x700);
            break;

        case 0x400b:
            this.triFreq = (this.triFreq & 0xff) | ((val & 0x7) << 8);
            this.linearCounterHalt = true;
            if (this.enable[0]) {
                this.lengthCounter[0] = lengthTable[(val >> 3) & 0x1f];
            }
            break;

        // noise

        case 0x400c:
            this.noiseVolume = val & 15;
            this.envelopeDivPeriod = val & 15;
            this.envelopeDisable = ((val >> 4) & 1) != 0;
            this.envelopeLoop = ((val >> 5) & 1) != 0;
            break;

        case 0x400d:
            break;

        case 0x400e:
            if (this.option[OPT.ENABLE_PNOISE.ordinal()] != 0)
                this.noiseTap = (val & 0x80) != 0 ? (1 << 6) : (1 << 1);
            else
                this.noiseTap = 1 << 1;
            this.nFreq = wavLenTable[this.pal][val & 15];
            break;

        case 0x400f:
            if (this.enable[1]) {
                this.lengthCounter[1] = lengthTable[(val >> 3) & 0x1f];
            }
            this.envelopeWrite = true;
            break;

        // dmc

        case 0x4010:
            this.mode = (val >> 6) & 3;
            if ((this.mode & 2) == 0) {
                this.irq = false;
                //cpu.UpdateIRQ(NES_CPU::IRQD_DMC, false);
            }
            this.dFreq = freqTable[this.pal][val & 15];
            break;

        case 0x4011:
            if (this.option[OPT.ENABLE_4011.ordinal()] != 0) {
                this.damp = (short) ((val >> 1) & 0x3f);
                this.dacLsb = val & 1;
                this.dmcPop = true;
            }
            break;

        case 0x4012:
            this.adrReg = val & 0xff;
            // ここで dadAress は更新されない
            break;

        case 0x4013:
            this.lenReg = val & 0xff;
            // ここで length は更新されない
            break;

        default:
            return false;
        }

        return true;
    }

    public boolean read(int adr, int[] val) {
        if (adr == 0x4015) {
            val[0] |= (this.irq ? 0x80 : 0)
                    | (this.frameIrq ? 0x40 : 0)
                    | (this.dLength > 0 ? 0x10 : 0)
                    | (this.lengthCounter[1] != 0 ? 0x08 : 0)
                    | (this.lengthCounter[0] != 0 ? 0x04 : 0)
            ;

            this.frameIrq = false;
            //cpu.updateIRQ(NES_CPU::IRQD_FRAME, false);
            return true;
        } else if (0x4008 <= adr && adr <= 0x4014) {
            val[0] |= this.reg[adr - 0x4008];
            return true;
        } else
            return false;
    }

    public NpNesDmc(int clock, int rate) {

        // this.setClock(DEFAULT_CLOCK);
        // this.setRate(DEFAULT_RATE);
        // this.setPal(false);
        this.setClock(clock); // does setPal, too
        this.setRate(rate);
        this.option[OPT.ENABLE_4011.ordinal()] = 1;
        this.option[OPT.ENABLE_PNOISE.ordinal()] = 1;
        this.option[OPT.UNMUTE_ON_RESET.ordinal()] = 1;
        this.option[OPT.DPCM_ANTI_CLICK.ordinal()] = 0;
        this.option[OPT.NONLINEAR_MIXER.ordinal()] = 1;
        this.option[OPT.RANDOMIZE_NOISE.ordinal()] = 1;
        this.option[OPT.RANDOMIZE_TRI.ordinal()] = 1;
        this.option[OPT.TRI_MUTE.ordinal()] = 1;
        this.option[OPT.DPCM_REVERSE.ordinal()] = 0;

        this.tndTable = new int[2][][][];
        for (int i = 0; i < 2; i++) {
            this.tndTable[i] = new int[16][][];
            for (int j = 0; j < 16; j++) {
                this.tndTable[i][j] = new int[16][];
                for (int k = 0; k < 16; k++) {
                    this.tndTable[i][j][k] = new int[128];
                }
            }
        }
        this.tndTable[0][0][0][0] = 0;
        this.tndTable[1][0][0][0] = 0;

        this.apu = null;
        this.frameSequenceCount = 0;
        this.frameSequenceLength = 7458;
        this.frameSequenceSteps = 4;

        for (int c = 0; c < 2; ++c)
            for (int t = 0; t < 3; ++t)
                this.sm[c][t] = 128;
    }
}
