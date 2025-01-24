package mdsound.chips;

import java.util.Arrays;
import java.util.function.Function;

import mdsound.Common;


/**
 * Sega System 32 Multi/Model 1/Model 2 custom PCM chips (315-5560) emulation.
 *
 * by Miguel Angel Horna (ElSemi) for Model 2 Emulator and MAME.
 * Information by R.Belmont and the YMF278B (OPL4) manual.
 *
 * Voice registers:
 * 0: Pan
 * 1: Index of sample
 * 2: LSB of pitch (low 2 bits seem unused so)
 * 3: MSB of pitch (ooooppppppppppxx) (o=octave (4 bit signed), p=pitch (10 bits), x=unused?
 * 4: Voice control: top bit = 1 for key on, 0 for key off
 * 5: bit 0: 0: interpolate volume changes, 1: direct set volume,
 *    bits 1-7 = volume attenuate (0=max, 7f=min)
 * 6: LFO frequency + Phase LFO depth
 * 7: Amplitude LFO size
 *
 * The first sample ROM contains a variable length table with 12
 * bytes per instrument/sample. This is very similar to the YMF278B.
 *
 * The first 3 bytes are the offset into the file (big endian).
 * The next 2 are the loop start offset into the file (big endian)
 * The next 2 are the 2's complement of the total sample size (big endian)
 * The next byte is LFO freq + depth (copied to reg 6 ?)
 * The next 3 are envelope params (Attack, Decay1 and 2, sustain level, release, Key Rate Scaling)
 * The next byte is Amplitude LFO size (copied to reg 7 ?)
 *
 * TODO
 * - The YM278B manual states that the chips supports 512 instruments. The MultiPCM probably supports them
 * too but the high bit position is unknown (probably reg 2 low bit). Any game use more than 256?
 */
public class MultiPCM {

    private static final float MULTIPCM_CLOCKDIV = 180.0f; // 224.0

    private static int value_to_fixed(int bits, float value) {
        float float_shift = (float) (1 << bits);
        return (int) (float_shift * value);
    }

    /**
     * ENVELOPE SECTION
     */
    private static class Eg {

        // Times are based on a 44100Hz timesuper. It's adjusted to the actual sampling rate on startup
        private static final double[] BaseTimes = {
                0,          0,          0,          0,
                6222.95,    4978.37,    4148.66,    3556.01,
                3111.47,    2489.21,    2074.33,    1778.00,
                1555.74,    1244.63,    1037.19,    889.02,
                777.87,     622.31,     518.59,     444.54,
                388.93,     311.16,     259.32,     222.27,
                194.47,     155.60,     129.66,     111.16,
                97.23,      77.82,      64.85,      55.60,
                48.62,      38.91,      32.43,      27.80,
                24.31,      19.46,      16.24,      13.92,
                12.15,      9.75,       8.12,       6.98,
                6.08,       4.90,       4.08,       3.49,
                3.04,       2.49,       2.13,       1.90,
                1.72,       1.41,       1.18,       1.04,
                0.91,       0.73,       0.59,       0.50,
                0.45,       0.45,       0.45,       0.45
        };

//        private double MULTIPCM_RATE = 44100.0;

        private static final double AR2DR = 14.32833;

        private static final int[] lin2expVol = new int[0x400];

        private static final int SHIFT = 16;

        // I include these in the chips because they depend on the chips clock
        // Envelope step table
        private static final int[] arStep = new int[0x40];
        private static final int[] drStep = new int[0x40];

        enum State {ATTACK, DECAY1, DECAY2, RELEASE}

        private int volume; //
        private Eg.State state;
        private int step = 0;
        // step vals
        /** Attack */
        private int ar;
        /** Decay1 */
        private int d1r;
        /** Decay2 */
        private int d2r;
        /** Release */
        private int rr;
        /** Decay level */
        private int dl;

        static {
            // build the linear.exponential ramps
            for (int i = 0; i < 0x400; ++i) {
                float db = -(96.0f - (96.0f * i / 0x400f));
                float exp_volume = (float) (Math.pow(10.0, db / 20.0));
                lin2expVol[i] = value_to_fixed(MultiPCM.SHIFT, exp_volume);
            }

            // Envelope steps
            for (int i = 0; i < 0x40; ++i) {
                // Times are based on 44100 clock, adjust to real chips clock
                arStep[i] = (int) ((0x400 << SHIFT) / (BaseTimes[i] * 44100.0 / 1000.0));
                drStep[i] = (int) ((0x400 << SHIFT) / (BaseTimes[i] * AR2DR * 44100.0 / 1000.0));
            }
            arStep[0] = arStep[1] = arStep[2] = arStep[3] = 0;
            arStep[0x3f] = 0x400 << SHIFT;
            drStep[0] = drStep[1] = drStep[2] = drStep[3] = 0;
        }

        private void update() {
            switch (this.state) {
            case ATTACK:
                this.volume += this.ar;
                if (this.volume >= (0x3ff << SHIFT)) {
                    this.state = State.DECAY1;
                    if (this.d1r >= (0x400 << SHIFT)) // Skip DECAY1, go directly to DECAY2
                        this.state = State.DECAY2;
                    this.volume = 0x3ff << SHIFT;
                }
                break;
            case DECAY1:
                this.volume -= this.d1r;
                if (this.volume <= 0)
                    this.volume = 0;
                if (this.volume >> SHIFT <= (this.dl << (10 - 4)))
                    this.state = State.DECAY2;
                break;
            case DECAY2:
                this.volume -= this.d2r;
                if (this.volume <= 0)
                    this.volume = 0;
                break;
            case RELEASE:
                this.volume -= this.rr;
                if (this.volume <= 0) {
                    this.volume = 0;
                }
                break;
            }
        }

        public int update(Runnable whenRelease) {
            this.update();
            switch (this.state) {
                case ATTACK:
                case DECAY1:
                case DECAY2:
                    break;
                case RELEASE:
                    if (this.volume <= 0) {
                        whenRelease.run();
                    }
                    break;
                default:
                    return 1 << MultiPCM.SHIFT;
            }
            return lin2expVol[this.volume >> SHIFT];
        }

        private static int getRate(int[] steps, int rate, int val) {
            int r = 4 * val + rate;
            if (val == 0)
                return steps[0];
            if (val == 0xf)
                return steps[0x3f];
            if (r > 0x3f)
                r = 0x3f;
            return steps[r];
        }

        public void calc(int rate, Slot.Sample sample) {
            this.ar = getRate(arStep, rate, sample.ar);
            this.d1r = getRate(drStep, rate, sample.dr1);
            this.d2r = getRate(drStep, rate, sample.dr2);
            this.rr = getRate(drStep, rate, sample.rr);
            this.dl = 0xf - sample.dl;
        }
    }

    /**
     * LFO SECTION
     */
    static class Lfo {

        private static final int SHIFT = 8;

        private static final int[] pLfoTri = new int[256];
        private static final int[] aLfoTri = new int[256];

        private static final float[] LFOFreq = {0.168f, 2.019f, 3.196f, 4.206f, 5.215f, 5.888f, 6.224f, 7.066f}; // Hz;
        private static final float[] PSCALE = {0.0f, 3.378f, 5.065f, 6.750f, 10.114f, 20.170f, 40.180f, 79.307f}; // cents
        private static final float[] ASCALE = {0.0f, 0.4f, 0.8f, 1.5f, 3.0f, 6.0f, 12.0f, 24.0f}; // DB
        private static final int[][] pScales = {new int[256], new int[256], new int[256], new int[256], new int[256], new int[256], new int[256], new int[256]};
        private static final int[][] aScales = {new int[256], new int[256], new int[256], new int[256], new int[256], new int[256], new int[256], new int[256]};

        static {
            for (int i = 0; i < 256; ++i) {
                if (i < 64)
                    pLfoTri[i] = i * 2 + 128;
                else if (i < 128)
                    pLfoTri[i] = 383 - i * 2;
                else if (i < 192)
                    pLfoTri[i] = 384 - i * 2;
                else
                    pLfoTri[i] = i * 2 - 383;

                if (i < 128)
                    aLfoTri[i] = 255 - (i * 2);
                else
                    aLfoTri[i] = (i * 2) - 256;
            }

            for (int s = 0; s < 8; ++s) {
                float limit = PSCALE[s];
                for (int i = -128; i < 128; ++i) {
                    float value = (limit * (float) i) / 128.0f;
                    float converted = (float) Math.pow(2.0, value / 1200.0);
                    pScales[s][i + 128] = value_to_fixed(SHIFT, converted);
                }
                limit = -ASCALE[s];
                for (int i = 0; i < 256; ++i) {
                    float value = (limit * (float) i) / 256.0f;
                    float converted = (float) Math.pow(10.0, value / 20.0);
                    aScales[s][i] = value_to_fixed(SHIFT, converted);
                }
            }
        }

        private int phase;
        private int phaseStep;
        private int[] table;
        private int[] scale;

        private int stepP() {
            this.phase += this.phaseStep;
            int p = this.table[(this.phase >> SHIFT) & 0xff];
            p = this.scale[p];
            return p << (MultiPCM.SHIFT - SHIFT);
        }

        private int stepA() {
            this.phase += this.phaseStep;
            int p = this.table[(this.phase >> SHIFT) & 0xff];
            p = this.scale[p];
            return p << (MultiPCM.SHIFT - SHIFT);
        }

        private void computeStep(int lfoF, int lfoS, int aLfo, float rate) {
            float step = LFOFreq[lfoF] * 256.0f / rate;
            this.phaseStep = (int) ((float) (1 << SHIFT) * step);
            if (aLfo != 0) {
                this.table = aLfoTri;
                this.scale = aScales[lfoS];
            } else {
                this.table = pLfoTri;
                this.scale = pScales[lfoS];
            }
        }
    }

    public static class Slot {

        public static class Sample {
            public int start;
            public int loop;
            public int end;
            public int ar;
            public int dr1;
            public int dr2;
            public int dl;
            public int rr;
            public int krs;
            public int lfoVib;
            public int am;
            private int format;

            private void init(byte[] rom, int address) {
                this.start = ((rom[address + 0] & 0xff) << 16) | ((rom[address + 1] & 0xff) << 8) | ((rom[address + 2] & 0xff) << 0);
                this.format = rom[address >> 20] & 0xfe;
                this.start &= 0x3fffff;
                this.loop = ((rom[address + 3] & 0xff) << 8) | ((rom[address + 4] & 0xff) << 0);
                this.end = 0xffff - (((rom[address + 5] & 0xff) << 8) | ((rom[address + 6] & 0xff) << 0));
                this.lfoVib = rom[address + 7];
                this.dr1 = rom[address + 8] & 0xf;
                this.ar = (rom[address + 8] >> 4) & 0xf;
                this.dr2 = rom[address + 9] & 0xf;
                this.dl = (rom[address + 9] >> 4) & 0xf;
                this.rr = rom[address + 10] & 0xf;
                this.krs = (rom[address + 10] >> 4) & 0xf;
                this.am = rom[address + 11] & 0xff;
            }
        }

        // TL Interpolation steps
        private static final int[] tlSteps = new int[2];

        static {
            // lower
            tlSteps[0] = -(int) ((float) (0x80 << SHIFT) / (78.2 * 44100.0 / 1000.0));
            // raise
            tlSteps[1] = (int) ((float) (0x80 << SHIFT) / (78.2 * 2 * 44100.0 / 1000.0));
        }

        private int num;
        public int[] regs = new int[8];
        private int playing;
        public Sample sample;
        private int base;
        private int offset;
        private int step;
        public int pan;
        private int tl;
        private int dstTL;
        private int tlStep;
        private int prev;
        private final Eg eg;
        /** Phase Lfo */
        private final Lfo pLfo;
        /** AM Lfo */
        private final Lfo aLfo;

        private int muted;

        Slot() {
            this.eg = new Eg();
            this.aLfo = new Lfo();
            this.pLfo = new Lfo();
            this.sample = new Sample();
        }

        private void calcEG() {
            int octave = ((this.regs[3] >> 4) - 1) & 0xf;
            int rate;
            if ((octave & 8) != 0) octave = octave - 16;
            if (this.sample.krs != 0xf)
                rate = (octave + this.sample.krs) * 2 + ((this.regs[3] >> 3) & 1);
            else
                rate = 0;

            this.eg.calc(rate, this.sample);
        }

        private void setPitch(int[] fnsTable, float rate) {
            int oct = ((this.regs[3] >> 4) - 1) & 0xf;
            int pitch = ((this.regs[3] & 0xf) << 6) | (this.regs[2] >> 2);
            pitch = fnsTable[pitch];
            if ((oct & 0x8) != 0)
                pitch >>= 16 - oct;
            else
                pitch <<= oct;
            this.step = (int) (pitch / rate);
        }

        private void keyOn(Sample[] samples, int bankL, int bankR, int sega_banking) {
            this.sample = samples[this.regs[1]];
            this.playing = 1;
            this.base = this.sample.start;
            this.offset = 0;
            this.prev = 0;
            this.tl = this.dstTL << SHIFT;

            this.calcEG();
            this.eg.state = Eg.State.ATTACK;
            this.eg.volume = 0;

            if (sega_banking != 0) {
                this.base &= 0x1f_ffff;
                if (this.base >= 0x10_0000) {
                    if ((this.pan & 8) != 0)
                        this.base = (this.base & 0xf_ffff) | bankL;
                    else
                        this.base = (this.base & 0xf_ffff) | bankR;
                }
            }
        }

        private void keyOff() {
            if (this.playing != 0) {
                if (this.sample.rr != 0xf)
                    this.eg.state = Eg.State.RELEASE;
                else
                    this.playing = 0;
            }
        }

        private void interpolate(int data) {
            this.dstTL = (data >> 1) & 0x7f;
            if ((data & 1) == 0) { // Interpolate TL
                if ((this.tl >> SHIFT) > this.dstTL)
                    this.tlStep = tlSteps[0]; // decrease
                else
                    this.tlStep = tlSteps[1]; // increase
            } else
                this.tl = this.dstTL << SHIFT;
        }

        private void lfo(int data, float rate) {
            if (data != 0) {
                this.pLfo.computeStep((this.regs[6] >> 3) & 7, this.regs[6] & 7, 0, rate);
                this.aLfo.computeStep((this.regs[6] >> 3) & 7, this.regs[7] & 7, 1, rate);
            }
        }

        private int update(Function<Integer, Integer> read) {
            int sPos = this.offset >> SHIFT;
            int step = this.step;
            int cSample = 0;
            int fPart = this.offset & ((1 << SHIFT) - 1);
            int sample;

            if ((this.sample.format & 8) != 0) { // 12-bit linear
                int adr = this.base + (sPos >> 2) * 6;
                switch (sPos & 3) {
                    case 0: { // ab.c .... ....
                        short w0 = (short) (read.apply(adr) << 8 | ((read.apply(adr + 1) & 0xf) << 4));
                        cSample = w0;
                        break;
                    }
                    case 1: { // ..C. AB.. ....
                        short w0 = (short) ((read.apply(adr + 2) << 8) | (read.apply(adr + 1) & 0xf0));
                        cSample = w0;
                        break;
                    }
                    case 2: { // .... ..ab .c..
                        short w0 = (short) (read.apply(adr + 3) << 8 | ((read.apply(adr + 4) & 0xf) << 4));
                        cSample = w0;
                        break;
                    }
                    case 3: { // .... .... C.AB
                        short w0 = (short) ((read.apply(adr + 5) << 8) | (read.apply(adr + 4) & 0xf0));
                        cSample = w0;
                        break;
                    }
                }
            } else {
                cSample = (short) (read.apply(this.base + sPos) << 8);
            }

            sample = (cSample * fPart + this.prev * ((1 << SHIFT) - fPart)) >> SHIFT;

            if ((this.regs[6] & 7) != 0) { // Vibrato enabled
                step = step * this.pLfo.stepP();
                step >>= SHIFT;
            }

            this.offset += step;
            if (this.offset >= (this.sample.end << SHIFT)) {
                this.offset = this.sample.loop << SHIFT;
            }

            if ((sPos ^ (this.offset >> SHIFT)) != 0) {
                this.prev = cSample;
            }

            if ((this.tl >> SHIFT) != this.dstTL) {
                this.tl += this.tlStep;
            }

            if ((this.regs[7] & 7) != 0) { // Tremolo enabled
                sample = sample * this.aLfo.stepA();
                sample >>= SHIFT;
            }

            return (sample * this.eg.update(() -> this.playing = 0)) >> 10;
        }
    }

    // Max 512 samples
    private final Slot.Sample[] samples = new Slot.Sample[0x200];
    private final Slot[] slots = new Slot[28];
    private int curSlot;
    private int address;
    public int sega_banking;
    private int bankR, bankL;
    private float rate;

    // Frequency step table
    private final int[] fnsTable = new int[0x400];

    private int romMask;
    private int romSize;
    private byte[] rom;

    private static final int[] LPANTABLE = new int[0x800];
    private static final int[] RPANTABLE = new int[0x800];

    private static final int[] val2chan = {
            0, 1, 2, 3, 4, 5, 6, -1,
            7, 8, 9, 10, 11, 12, 13, -1,
            14, 15, 16, 17, 18, 19, 20, -1,
            21, 22, 23, 24, 25, 26, 27, -1,
    };

    private static final int SHIFT = 12;

    static {
        // Volume+pan table
        for (int level = 0; level < 0x80; ++level) {

            float vol_db = (float) level * -24.0f / 64.0f;
            float total_level = (float) Math.pow(10.0, vol_db / 20.0) / 4.0f;

            for (int pan = 0; pan < 0x10; ++pan) {
                float pan_left, pan_right;
                if (pan == 0x8) {
                    pan_left = 0.0f;
                    pan_right = 0.0f;
                } else if (pan == 0x0) {
                    pan_left = 1.0f;
                    pan_right = 1.0f;
                } else if ((pan & 0x8) != 0) {
                    int inverted_pan = 0x10 - pan;
                    float pan_vol_db = (float) inverted_pan * -12.0f / 4.0f;

                    pan_left = 1.0f;
                    pan_right = (float) Math.pow(10.0, pan_vol_db / 20.0);

                    if ((inverted_pan & 0x7) == 7)
                        pan_right = 0.0f;
                } else {
                    float pan_vol_db = (float) pan * -12.0f / 4.0f;

                    pan_left = (float) Math.pow(10.0, pan_vol_db / 20.0);
                    pan_right = 1.0f;

                    if ((pan & 0x7) == 7)
                        pan_left = 0.0f;
                }

                LPANTABLE[(pan << 7) | level] = value_to_fixed(SHIFT, pan_left * total_level);
                RPANTABLE[(pan << 7) | level] = value_to_fixed(SHIFT, pan_right * total_level);
            }
        }
    }

    public void update(int[][] outputs, int samples) {

        for (int i = 0; i < samples; i++) {
            outputs[0][i] = 0;
            outputs[1][i] = 0;
        }

        for (int i = 0; i < samples; i++) {
            int sampleL = 0;
            int sampleR = 0;
            for (int s = 0; s < 28; s++) {
                Slot slot = this.slots[s];
                if (slot.playing != 0 && slot.muted == 0) {
                    int vol = (slot.tl >> SHIFT) | (slot.pan << 7);

                    int sample = slot.update(offset -> rom[offset & romMask] & 0xff);

                    sampleL += (LPANTABLE[vol] * sample) >> SHIFT;
                    sampleR += (RPANTABLE[vol] * sample) >> SHIFT;
                }
            }
            outputs[0][i] = sampleL;
            outputs[1][i] = sampleR;
        }
    }

    public int start(int clock) {
        for (int s = 0; s < this.slots.length; s++) {
            this.slots[s] = new Slot(); // TODO use init
        }
        for (int i = 0; i < this.samples.length; i++) {
            this.samples[i] = new Slot.Sample(); // TODO use init
        }

        this.romMask = 0x00;
        this.romSize = 0x00;
        this.rom = null;
        this.rate = clock / MULTIPCM_CLOCKDIV;

        // Pitch steps
        for (int j = 0; j < 0x400; j++) {
            float fCent = this.rate * (1024.0f + j) / 1024.0f;
            this.fnsTable[j] = value_to_fixed(SHIFT, fCent);
        }

        this.sega_banking = 0;
        this.bankL = this.bankR = 0x00_0000;

        setMuteMask(0);

        return (int) (this.rate + 0.5);
    }

    public void stop() {
        this.rom = null;
    }

    public void reset() {
        for (int s = 0; s < 28; ++s) {
            this.slots[s].num = s;
            this.slots[s].playing = 0;
        }
    }

    public void write(int offset, int data) {
        switch (offset) {
        case 0: // data write
            if (this.curSlot == -1)
                return;
            writeSlot(this.slots[this.curSlot], this.address, data);
            break;
        case 1:
            this.curSlot = val2chan[data & 0x1f];
//logger.log(Level.TRACE, "curSlot%s".formatted(this.curSlot));
            break;
        case 2:
            this.address = Math.min(data, 7);
            break;
        // special SEGA banking
        case 0x10:  // 1 MB banking (Sega Model 1)
            this.sega_banking = 1;
            this.bankL = (data << 20) | 0x00_0000;
            this.bankR = (data << 20) | 0x08_0000;
            break;
        case 0x11:  // 512 KB banking - low bank (Sega Multi 32)
            this.sega_banking = 1;
            this.bankL = (data << 19);
            break;
        case 0x12:  // 512 KB banking - high bank (Sega Multi 32)
            this.sega_banking = 1;
            this.bankR = (data << 19);
            break;
        }
    }

    public void quickWrite(int offset, int data) {
        this.curSlot = val2chan[(offset >> 3) & 0x1F];
        this.address = offset & 0x07;
        if (this.curSlot == -1)
            return;
        writeSlot(this.slots[this.curSlot], this.address, data);
    }

    private void allocRom(int romSize) {
        this.rom = new byte[romSize];
        this.romSize = romSize;
        Arrays.fill(this.rom, 0, romSize, (byte) 0xff);

        this.romMask = Common.pow2_mask(romSize);
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
        writeRom(romSize, dataStart, dataLength, romData, 0);
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        if (this.rom == null || this.rom.length < romSize) {
            allocRom(romSize);
        }

        if (dataStart > this.romSize)
            return;
        if (dataStart + dataLength > this.romSize)
            dataLength = this.romSize - dataStart;

        System.arraycopy(romData, srcStartAddress, this.rom, dataStart, dataLength);
    }

    public void setMuteMask(int muteMask) {
        for (int ch = 0; ch < 28; ch++)
            this.slots[ch].muted = (muteMask >> ch) & 0x01;
    }

    private void writeSlot(Slot slot, int reg, int data) {
        slot.regs[reg] = data;

        switch (reg) {
            case 0 -> slot.pan = (data >> 4) & 0xf; // PANPOT
            case 1 -> { // sample
                // according to YMF278 sample write causes some base params written to the regs (envelope+lfos)
                // the game should never change the sample while playing.
                slot.sample.init(this.rom, ((slot.regs[1] | ((slot.regs[2] & 1) << 8)) * 12) & romMask);
                writeSlot(slot, 6, slot.sample.lfoVib);
                writeSlot(slot, 7, slot.sample.am);
            }
            case 2, 3 -> slot.setPitch(this.fnsTable, this.rate); // Pitch
            case 4 -> { // KeyOn/Off (and more?)
                if ((data & 0x80) != 0) { // KeyOn
                    slot.keyOn(this.samples, this.bankL, this.bankR, this.sega_banking);
                } else {
                    slot.keyOff();
                }
            }
            case 5 -> slot.interpolate(data); // TL+Interpolation
            case 6 -> slot.lfo(data, this.rate); // LFO freq+PLFO
            case 7 -> slot.lfo(data, this.rate); // ALFO
        }
    }

    public Slot getSlot(int ch) {
        return slots[ch];
    }
}
