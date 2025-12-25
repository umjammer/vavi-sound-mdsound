/*
 * NSFPlay/NFSPlug project by Brezza.
 *
 * https://web.archive.org/web/20160301201825/http://www.pokipoki.org/dsa/
 */

package mdsound.np;

import java.util.Random;

import mdsound.np.cpu.Km6502;
import mdsound.np.cpu.Km6502.IRQDevices;
import vavi.util.Debug;


/**
 * NpNesDmc.
 *
 * @author Brezza
 * @author Valley Bell
 * @version Ported the source code of NSFPlay tag2.4 2021/08/14
 *          Updated to NSFPlay 2.3 on 26 September 2013
 *          Ported from NSFPlay to VGMPlay (including C++ . C conversion) by Valley Bell on 25 September 2013
 * @see "https://github.com/bbbradsmith/nsfplay/releases/tag/2.4"
 */
public class NpNesDmc {

    public NpNesApu nes_apu = null;

    // Master Clock: 21477272 (NTSC)
    // APU Clock = Master Clock / 12
    public static final double DEFAULT_CLOCK = 1789772.0;
    public static final int DEFAULT_CLK_PAL = 1662607;
    public static final int DEFAULT_RATE = 44100;

    /**
     * Bottom Half of APU
     */
    public enum OPT {
        ENABLE_4011 /* = 0 */,
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

    private final Counter counter_ = new Counter();

    private final Random rnd = new Random();

    private int[][][][] tndTable; // [2][16][16][128];

    private final int[] option = new int[10]; // OPT_END];
    private int mask;
    private int[][] sm = {new int[3], new int[3]};
    public final int[] reg = new int[0x20];
    private int lenReg;
    private int adrReg;
    private Device orgMemory;
    private byte[] memory;
    private int ptrMemory;
    private final int[] out = new int[3];
    private int dAddress;
    private int dLength;
    private final int[] data = new int[1];
    private boolean empty;
    private short damp;
    private int dacLsb;
    private boolean dmcPop;
    private int dmcPopOffset;
    private int dmcPopFollow;
    private double clock;
    private int rate;
    private int pal;
    private int mode;
    private boolean irq;

    /** frequency dividers */
    private final int[] counter = new int[3];
    /** triangle phase */
    private int tPhase;
    /** noise frequency */
    private int nFreq;
    /** DPCM frequency */
    private int dFreq;

    private int triFreq;
    private int linearCounter;
    private int linearCounterReload;
    private boolean linearCounterHalt;
    private boolean linearCounterControl;

    private int noiseVolume;
    private int noise, noiseTap;

    // noise envelope
    private boolean envelopeLoop;
    private boolean envelopeDisable;
    private boolean envelopeWrite;
    private int envelopeDivPeriod;
    private int envelopeDiv;
    private int envelopeCounter;

    private final boolean[] enable = new boolean[2];
    // 0=tri, 1=noise
    private final int[] lengthCounter = new int[2];

    // frame sequencer

    // apu is clocked by DMC's frame sequencer
    private NpNesApu apu;
    // current cycle count
    private int frameSequenceCount;
    // CPU cycles per FrameSequence
    private int frameSequenceLength;
    // current step of frame sequence
    private int frameSequenceStep;
    // 4/5 steps per frame
    private int frameSequenceSteps;
    private boolean frameIrq;
    private boolean frameIrqEnable;

    /** IRQ needs CPU access */
    public Km6502 cpu;

    private final Counter tickCount = new Counter();
    private long tickLast;

    private int getDamp() {
        return (this.damp << 1) | this.dacLsb;
    }

    private static final int GETA_BITS = 20;
    private static final int[][] wavLenTable = {
            {4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068}, // NTSC
            {4, 8, 14, 30, 60, 88, 118, 148, 188, 236, 354, 472, 708, 944, 1890, 3778} // PAL
    };

    private static final int[][] freqTable = {
            {428, 380, 340, 320, 286, 254, 226, 214, 190, 160, 142, 128, 106, 84, 72, 54}, // NTSC
            {398, 354, 316, 298, 276, 236, 210, 198, 176, 148, 132, 118, 98, 78, 66, 50} // PAL
    };

    private static final int[] BitReverse = {
            0x00, 0x80, 0x40, 0xc0, 0x20, 0xa0, 0x60, 0xe0, 0x10, 0x90, 0x50, 0xd0, 0x30, 0xb0, 0x70, 0xf0,
            0x08, 0x88, 0x48, 0xc8, 0x28, 0xa8, 0x68, 0xe8, 0x18, 0x98, 0x58, 0xd8, 0x38, 0xb8, 0x78, 0xf8,
            0x04, 0x84, 0x44, 0xc4, 0x24, 0xa4, 0x64, 0xe4, 0x14, 0x94, 0x54, 0xd4, 0x34, 0xb4, 0x74, 0xf4,
            0x0c, 0x8c, 0x4c, 0xcc, 0x2c, 0xac, 0x6c, 0xec, 0x1c, 0x9c, 0x5c, 0xdc, 0x3c, 0xbc, 0x7c, 0xfc,
            0x02, 0x82, 0x42, 0xc2, 0x22, 0xa2, 0x62, 0xe2, 0x12, 0x92, 0x52, 0xd2, 0x32, 0xb2, 0x72, 0xf2,
            0x0a, 0x8a, 0x4a, 0xca, 0x2a, 0xaa, 0x6a, 0xea, 0x1a, 0x9a, 0x5a, 0xda, 0x3a, 0xba, 0x7a, 0xfa,
            0x06, 0x86, 0x46, 0xc6, 0x26, 0xa6, 0x66, 0xe6, 0x16, 0x96, 0x56, 0xd6, 0x36, 0xb6, 0x76, 0xf6,
            0x0e, 0x8e, 0x4e, 0xce, 0x2e, 0xae, 0x6e, 0xee, 0x1e, 0x9e, 0x5e, 0xde, 0x3e, 0xbe, 0x7e, 0xfe,
            0x01, 0x81, 0x41, 0xc1, 0x21, 0xa1, 0x61, 0xe1, 0x11, 0x91, 0x51, 0xd1, 0x31, 0xb1, 0x71, 0xf1,
            0x09, 0x89, 0x49, 0xc9, 0x29, 0xa9, 0x69, 0xe9, 0x19, 0x99, 0x59, 0xd9, 0x39, 0xb9, 0x79, 0xf9,
            0x05, 0x85, 0x45, 0xc5, 0x25, 0xa5, 0x65, 0xe5, 0x15, 0x95, 0x55, 0xd5, 0x35, 0xb5, 0x75, 0xf5,
            0x0d, 0x8d, 0x4d, 0xcd, 0x2d, 0xad, 0x6d, 0xed, 0x1d, 0x9d, 0x5d, 0xdd, 0x3d, 0xbd, 0x7d, 0xfd,
            0x03, 0x83, 0x43, 0xc3, 0x23, 0xa3, 0x63, 0xe3, 0x13, 0x93, 0x53, 0xd3, 0x33, 0xb3, 0x73, 0xf3,
            0x0b, 0x8b, 0x4b, 0xcb, 0x2b, 0xab, 0x6b, 0xeb, 0x1b, 0x9b, 0x5b, 0xdb, 0x3b, 0xbb, 0x7b, 0xfb,
            0x07, 0x87, 0x47, 0xc7, 0x27, 0xa7, 0x67, 0xe7, 0x17, 0x97, 0x57, 0xd7, 0x37, 0xb7, 0x77, 0xf7,
            0x0f, 0x8f, 0x4f, 0xcf, 0x2f, 0xaf, 0x6f, 0xef, 0x1f, 0x9f, 0x5f, 0xdf, 0x3f, 0xbf, 0x7f, 0xff,
    };

    public void setMask(int m) {
        this.mask = m;
    }

    public void setStereoMix(int trk, int mixL, int mixR) {
        if (trk < 0) return;
        if (trk > 2) return;
        this.sm[0][trk] = mixL;
        this.sm[1][trk] = mixR;
    }

//    TrackInfo getTrackInfo(int trk) {
//        switch (trk) {
//            case 0:
//                trkInfo[trk].max_volume = 255;
//                trkInfo[0].key = (linear_counter > 0 && length_counter[0] > 0 && enable[0]);
//                trkInfo[0].volume = 0;
//                trkInfo[0]._freq = tri_freq;
//                if (trkInfo[0]._freq)
//                    trkInfo[0].freq = clock / 32 / (trkInfo[0]._freq + 1);
//                else
//                    trkInfo[0].freq = 0;
//                trkInfo[0].tone = -1;
//                trkInfo[0].output = out[0];
//                break;
//            case 1:
//                trkInfo[1].max_volume = 15;
//                trkInfo[1].volume = noise_volume + (envelope_disable ? 0 : 0x10) + (envelope_loop ? 0x20 : 0);
//                trkInfo[1].key = length_counter[1] > 0 && enable[1] &&
//                        (envelope_disable ? (noise_volume > 0) : (envelope_counter > 0));
//                trkInfo[1]._freq = reg[0x400e - 0x4008] & 0xF;
//                trkInfo[1].freq = clock / double(wavLen_table[pal][trkInfo[1]._freq] * ((noise_tap & (1 << 6)) ? 93 : 1));
//                trkInfo[1].tone = noise_tap & (1 << 6);
//                trkInfo[1].output = out[1];
//                break;
//            case 2:
//                trkInfo[2].max_volume = 127;
//                trkInfo[2].volume = reg[0x4011 - 0x4008] & 0x7F;
//                trkInfo[2].key = dLength > 0;
//                trkInfo[2]._freq = reg[0x4010 - 0x4008] & 0xF;
//                trkInfo[2].freq = clock / double(freq_table[pal][trkInfo[2]._freq]);
//                trkInfo[2].tone = (0xc000 | (adr_reg << 6));
//                trkInfo[2].output = (damp << 1) | dac_lsb;
//                break;
//            default:
//                return NULL;
//        }
//        return trkInfo[trk];
//    }

    private void sequenceFrame(int s) {
//logger.log(Level.TRACE, "FrameSequence: %d".formatted(s));

        if (s > 3) return; // no operation in step 4

        if (this.apu != null) {
            this.apu.sequenceFrame(s);
        }

        if (s == 0 && (this.frameSequenceSteps == 4)) {
            if (this.frameIrqEnable) this.frameIrq = true;
            if (this.cpu != null) this.cpu.updateIRQ(IRQDevices.IRQD_FRAME, this.frameIrq & this.frameIrqEnable);
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

//        // $4009 unused address
//        this.reg[1] = (int)(
//            (this.linear_counter != 0 ? 4 : 0) // triangle
//            | (this.length_counter[1]!=0 ? 8:0) // noise
//            | (this.active ? 0x10 : 0) //dmc
//            );

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

    private static final int[] triTbl = {
            15, 14, 13, 12, 11, 10, 9, 8,
            7, 6, 5, 4, 3, 2, 1, 0,
            0, 1, 2, 3, 4, 5, 6, 7,
            8, 9, 10, 11, 12, 13, 14, 15
    };

    /** Calculates triangle wave channel. Returns 0-15. */
    private int calcTri(long clocks) {
        int tri = 0;
        if (this.linearCounter > 0 && this.lengthCounter[0] > 0
                && (this.option[OPT.TRI_MUTE.ordinal()] == 0 || this.triFreq > 0)) {
            tri = 1;
            this.counter[0] -= (int) clocks;
            while (this.counter[0] < 0) {
                this.tPhase = (this.tPhase + 1) & 31;
                this.counter[0] += this.triFreq + 1;
            }
        }
        // Note: else-block added by VB
//        else if (this.option[(int) OPT.OPT_TRI_NULL] != 0) {
//            if (this.tphase != 0 && this.tphase < 31) {
//                // Finish the Triangle wave to prevent clicks.
//                this.counter[0] += clocks;
//                while (this.counter[0] > this.tri_freq && this.tphase != 0) {
//                    this.tphase = (this.tphase + 1) & 31;
//                    this.counter[0] -= (this.tri_freq + 1);
//                }
//            }
//        }

        this.reg[0x10] = tri;
        return triTbl[this.tPhase];
    }

    /**
     * Calculates the noise channel. Returns 0-127.
     * When synthesized at a low sampling rate, alias noise is severe,
     * so only the noise is synthesized at a high clock rate within this function,
     * and simple sampling rate conversion is performed.
     */
    private int calcNoise(long clocks) {
        int noi = 1;

        int env, last, count, accum, clocksAccum;

        env = this.envelopeDisable ? this.noiseVolume : this.envelopeCounter;
        if (this.lengthCounter[1] < 1) {
            env = 0;
        }

        if (env == 0) noi = 0;
        this.reg[0x11] = noi;

        last = (this.noise & 0x4000) != 0 ? 0 : env;

        if (clocks < 1) return last;

        // simple antialiasing (noise requires it, even when oversampling is off)
        count = 0;
        accum = this.counter[1] * last;
        int accumClocks = this.counter[1];
int startClocks = counter[1];
        if (this.counter[1] < 0) { // only happens on startup when using the randomize noise option
            accum = 0;
            accumClocks = 0;
        }

        this.counter[1] -= (int) clocks;
        // assert(this.nFreq > 0); // prevent infinite loop
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
if (startClocks >= 0) { assert(accumClocks == clocks); } // these should be equal

        int average = accum / accumClocks;
        //assert(average <= 15); // above this would indicate overflow
        return average;
    }

    // Tick the DMC for the number of clocks, and return output counter;
    private int calcDmc(long clocks) {
        this.counter[2] -= (int) clocks;
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
                    this.data[0] = this.memory[this.dAddress + this.ptrMemory] & 0xff;
                    //cpu.stealCycles(4); // DMC read takes 3 or 4 CPU cycles, usually 4
                    // (checking for the 3-cycle case would require sub-instruction emulation)
                    this.data[0] &= 0xff; // read 8 bits
                    if (this.option[OPT.DPCM_REVERSE.ordinal()] != 0) this.data[0] = BitReverse[this.data[0]];
                    this.data[0] |= 0x1_0000; // use an extra bit to signal end of data
                    this.empty = false;
                    this.dAddress = ((this.dAddress + 1) & 0xffff) | 0x8000;
                    --this.dLength;
                    if (this.dLength == 0) {
                        if ((this.mode & 1) != 0) { // looped DPCM = auto-reload
                            this.dAddress = ((this.adrReg << 6) | 0xc000);
                            this.dLength = (this.lenReg << 4) + 1;
                        } else if ((this.mode & 2) != 0) { // IRQ and not looped
                            this.irq = true;
                            if (this.cpu != null) this.cpu.updateIRQ(IRQDevices.IRQD_DMC, true);
                        }
                    }
                } else {
                    this.data[0] = 0x1_0000; // DMC will do nothing
                    this.empty = true;
                }
            }
        }

        this.reg[0x12] = this.empty ? 0 : 1;// dpc;
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
                    this.orgMemory.read(this.dAddress, /* ref */ this.data);
                    //this.data = this.memory[this.dAddress + this.ptrMemory];
                    //cpu.StealCycles(4); // DMC read takes 3 or 4 CPU cycles, usually 4
                    // (checking for the 3-cycle case would require sub-instruction emulation)
                    this.data[0] &= 0xff; // read 8 bits
                    if (this.option[OPT.DPCM_REVERSE.ordinal()] != 0) this.data[0] = BitReverse[this.data[0]];
                    this.data[0] |= 0x1_0000; // use an extra bit to signal end of data
                    this.empty = false;
                    this.dAddress = ((this.dAddress + 1) & 0xffff) | 0x8000;
                    --this.dLength;
                    if (this.dLength == 0) {
                        if ((this.mode & 1) != 0) { // looped DPCM = auto-reload
                            this.dAddress = ((this.adrReg << 6) | 0xc000);
                            this.dLength = (this.lenReg << 4) + 1;
                        } else if ((this.mode & 2) != 0) { // IRQ and not looped
                            this.irq = true;
                            if (this.cpu != null) this.cpu.updateIRQ(IRQDevices.IRQD_DMC, true);
                        }
                    }
                } else {
                    this.data[0] = 0x1_0000; // DMC will do nothing
                    this.empty = true;
                }
            }
        }

        this.reg[0x12] = this.empty ? 0 : 1;// dpc;
        return (this.damp << 1) + this.dacLsb;
    }

    public void tickFrameSequence(long clocks) {
        this.frameSequenceCount += (int) clocks;
        while (this.frameSequenceCount > this.frameSequenceLength) {
            sequenceFrame(this.frameSequenceStep);
            this.frameSequenceCount -= this.frameSequenceLength;
            ++this.frameSequenceStep;
            if (this.frameSequenceStep >= this.frameSequenceSteps)
                this.frameSequenceStep = 0;
        }
    }

    public void tick(long clocks) {
        this.out[0] = calcTri(clocks);
        this.out[1] = calcNoise(clocks);
        this.out[2] = calcDmc(clocks);
    }

    public void orgTick(int clocks) {
        this.out[0] = calcTri(clocks);
        this.out[1] = calcNoise(clocks);
        this.out[2] = calcDmcOrg(clocks);
    }

    private final int[] m = new int[3];

    public int render(int[] b) {

        this.tickCount.iup(); // increase counter (overflows after 255)
        long clocks = (this.tickCount.value() - this.tickLast) & 0xff;
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
        this.clock = (int) c;

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
        if (this.cpu != null) this.cpu.updateIRQ(IRQDevices.IRQD_FRAME, false);

        for (int i = 0; i < 0x0f; i++)
            write(0x4008 + i, 0);
        write(0x4017, 0x40);

        this.irq = false;
        write(0x4015, 0x00);
        if (this.option[OPT.UNMUTE_ON_RESET.ordinal()] != 0)
            write(0x4015, 0x0f);
        if (this.cpu != null) this.cpu.updateIRQ(IRQDevices.IRQD_DMC, false);

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
        this.dAddress = 0;
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

    private static final int[] lengthTable = {
            0x0a, 0xfe,
            0x14, 0x02,
            0x28, 0x04,
            0x50, 0x06,
            0xa0, 0x08,
            0x3c, 0x0a,
            0x0e, 0x0c,
            0x1a, 0x0e,
            0x0c, 0x10,
            0x18, 0x12,
            0x30, 0x14,
            0x60, 0x16,
            0xc0, 0x18,
            0x48, 0x1a,
            0x10, 0x1c,
            0x20, 0x1e
    };

    public boolean write(int adr, int val) {
        if (adr == 0x4015) {
            this.enable[0] = (val & 4) != 0;
            this.enable[1] = (val & 8) != 0;

            if (!this.enable[0]) {
                this.lengthCounter[0] = 0;
                //this.tPhase = 0; // TODO KUMA When it stops, I want the output to be 0
            }
            if (!this.enable[1]) {
                this.lengthCounter[1] = 0;
            }

            if ((val & 16) != 0 && this.dLength == 0) {
                //this.enable[2] = this.active = true;
                this.dAddress = (0xC000 | (this.adrReg << 6));
                this.dLength = (this.lenReg << 4) + 1;
                //this.irq = false;
            } else if ((val & 16) == 0) {
                this.dLength = 0;
                //this.enable[2] = this.active = false;
            }

            this.irq = false;
            if (this.cpu != null) this.cpu.updateIRQ(IRQDevices.IRQD_DMC, false);

            this.reg[adr - 0x4008] = val;
            return true;
        }

        if (adr == 0x4017) {
//logger.log(Level.TRACE, "4017 = %02X".formatted(val));
            this.frameIrqEnable = ((val & 0x40) != 0x40);
            if (this.frameIrqEnable) this.frameIrq = false;
            if (this.cpu != null) this.cpu.updateIRQ(IRQDevices.IRQD_FRAME, false);

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

        this.reg[adr - 0x4008] = val & 0xff;

//logger.log(Level.TRACE, "$%04X %02X".formatted(adr, val));

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
                if (this.cpu != null) this.cpu.updateIRQ(IRQDevices.IRQD_DMC, false);
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
            // dadAress is not updated here
            break;

        case 0x4013:
            this.lenReg = val & 0xff;
            // length is not updated here
            break;

        default:
            return false;
        }

        return true;
    }

    public boolean read(int adr, /* ref */ int[] val) {
        if (adr == 0x4015) {
            val[0] |= (this.irq ? 0x80 : 0) |
                    (this.frameIrq ? 0x40 : 0) |
                    (this.dLength > 0 ? 0x10 : 0) |
                    (this.lengthCounter[1] != 0 ? 0x08 : 0) |
                    (this.lengthCounter[0] != 0 ? 0x04 : 0);

            this.frameIrq = false;
            if (this.cpu != null) this.cpu.updateIRQ(IRQDevices.IRQD_FRAME, false);
            return true;
        } else if (0x4008 <= adr && adr <= 0x4014) {
            val[0] |= this.reg[adr - 0x4008];
            return true;
        } else
            return false;
    }

    public void init(int clock, int rate) {

//        this.setClock(DEFAULT_CLOCK);
//        this.setRate(DEFAULT_RATE);
//        this.setPal(false);
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
