package mdsound.chips;

import java.util.Arrays;


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

    // ????
    private static final double MULTIPCM_CLOCKDIV = 180.0;

    /*
     * ENVELOPE SECTION
     */
    private static class Eg {

        private static final int SHIFT = 16;

        // I include these in the chips because they depend on the chips clock
        // Envelope step table
        private static int[] arStep = new int[0x40], drStep = new int[0x40];

        private static int[] lin2expvol = new int[0x400];

        // Times are based on a 44100Hz timesuper. It's adjusted to the actual sampling rate on startup
        private static final double[] BaseTimes = new double[] {
                0, 0, 0, 0, 6222.95, 4978.37, 4148.66, 3556.01, 3111.47, 2489.21, 2074.33, 1778.00, 1555.74, 1244.63, 1037.19, 889.02,
                777.87, 622.31, 518.59, 444.54, 388.93, 311.16, 259.32, 222.27, 194.47, 155.60, 129.66, 111.16, 97.23, 77.82, 64.85, 55.60,
                48.62, 38.91, 32.43, 27.80, 24.31, 19.46, 16.24, 13.92, 12.15, 9.75, 8.12, 6.98, 6.08, 4.90, 4.08, 3.49,
                3.04, 2.49, 2.13, 1.90, 1.72, 1.41, 1.18, 1.04, 0.91, 0.73, 0.59, 0.50, 0.45, 0.45, 0.45, 0.45
        };

        //private double MULTIPCM_RATE = 44100.0;

        private static final double AR2DR = 14.32833;

        enum State {ATTACK, DECAY1, DECAY2, RELEASE}

        private int volume; //
        private Eg.State state;
        private int step = 0;
        //step vals
        /** Attack */
        private int ar;
        // Decay1
        private int d1r;
        // Decay2
        private int d2r;
        // Release
        private int rr;
        // Decay level
        private int dl;

        static {
            // Envelope steps
            for (int i = 0; i < 0x40; ++i) {
                // Times are based on 44100 clock, adjust to real chips clock
                arStep[i] = (int) ((float) (0x400 << SHIFT) / (BaseTimes[i] * 44100.0 / (1000.0)));
                drStep[i] = (int) ((float) (0x400 << SHIFT) / (BaseTimes[i] * AR2DR * 44100.0 / (1000.0)));
            }
            arStep[0] = arStep[1] = arStep[2] = arStep[3] = 0;
            arStep[0x3f] = 0x400 << SHIFT;
            drStep[0] = drStep[1] = drStep[2] = drStep[3] = 0;

            // build the linear.exponential ramps
            for (int i = 0; i < 0x400; ++i) {
                float db = -(float) ((96.0 - (96.0 * (float) i / (float) 0x400)));
                lin2expvol[i] = (int) (Math.pow(10.0, db / 20.0) * (float) (1 << SHIFT));
            }
        }

        private void update() {
            switch (this.state) {
            case ATTACK:
                this.volume += this.ar;
                if (this.volume >= (0x3ff << SHIFT)) {
                    this.state = Eg.State.DECAY1;
                    if (this.d1r >= (0x400 << SHIFT)) // Skip DECAY1, go directly to DECAY2
                        this.state = Eg.State.DECAY2;
                    this.volume = 0x3ff << SHIFT;
                }
                break;
            case DECAY1:
                this.volume -= this.d1r;
                if (this.volume <= 0)
                    this.volume = 0;
                if (this.volume >> SHIFT <= (this.dl << (10 - 4)))
                    this.state = Eg.State.DECAY2;
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

        private void calc(int rate, Slot.Sample sample) {
            this.ar = getRate(arStep, rate, sample.ar);
            this.d1r = getRate(drStep, rate, sample.dr1);
            this.d2r = getRate(drStep, rate, sample.dr2);
            this.rr = getRate(drStep, rate, sample.rr);
            this.dl = 0xf - sample.dl;
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

        private int update(Runnable whenRelease) {
            this.update();
            switch (this.state) {
            case RELEASE:
                if (this.volume <= 0) {
                    whenRelease.run();
                }
                break;
            default:
                return 1 << SHIFT;
            }
            return lin2expvol[this.volume >> SHIFT];
        }
    }

    /*
     * LFO  SECTION
     */
    static class Lfo {

        private static final int SHIFT = 8;

        private static int lFix(float v) {
            return (int) ((float) (1 << SHIFT) * v);
        }

        // Convert DB to multiply amplitude
        private static int db(float v) {
            return lFix((float) Math.pow(10.0, v / 20.0));
        }

        // Convert cents to step increment
        private static int cents(float v) {
            return lFix((float) Math.pow(2.0, v / 1200.0));
        }

        private static int[] pLfoTri = new int[256];
        private static int[] aLfoTri = new int[256];

        private static final float[] LFOFreq = new float[] {0.168f, 2.019f, 3.196f, 4.206f, 5.215f, 5.888f, 6.224f, 7.066f}; //Hz;
        private static final float[] PSCALE = new float[] {0.0f, 3.378f, 5.065f, 6.750f, 10.114f, 20.170f, 40.180f, 79.307f}; //cents
        private static final float[] ASCALE = new float[] {0.0f, 0.4f, 0.8f, 1.5f, 3.0f, 6.0f, 12.0f, 24.0f}; //DB
        private static int[][] pScales = new int[][] {new int[256], new int[256], new int[256], new int[256], new int[256], new int[256], new int[256], new int[256]};
        private static int[][] aScales = new int[][] {new int[256], new int[256], new int[256], new int[256], new int[256], new int[256], new int[256], new int[256]};

        static {
            for (int i = 0; i < 256; ++i) {
                int a; // amplitude
                int p; // phase

                // Tri
                if (i < 128)
                    a = 255 - (i * 2);
                else
                    a = (i * 2) - 256;
                if (i < 64)
                    p = i * 2;
                else if (i < 128)
                    p = 255 - i * 2;
                else if (i < 192)
                    p = 256 - i * 2;
                else
                    p = i * 2 - 511;
                aLfoTri[i] = a;
                pLfoTri[i] = p;
            }

            for (int s = 0; s < 8; ++s) {
                float limit = PSCALE[s];
                for (int i = -128; i < 128; ++i) {
                    pScales[s][i + 128] = cents((limit * (float) i) / 128.0F);
                }
                limit = -ASCALE[s];
                for (int i = 0; i < 256; ++i) {
                    aScales[s][i] = db((limit * (float) i) / 256.0F);
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
            p = this.scale[p + 128];
            return p << (MultiPCM.SHIFT - SHIFT);
        }

        private int stepA() {
            this.phase += this.phaseStep;
            int p = this.table[(this.phase >> SHIFT) & 0xff];
            p = this.scale[p];
            return p << (MultiPCM.SHIFT - SHIFT);
        }

        private void computeStep(int lfoF, int lfoS, int aLfo, float rate) {
            float step = (float) (LFOFreq[lfoF] * 256.0 / rate);
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

            private void writeRom(byte[] rom, int ptSample) {
                this.start = (rom[ptSample + 0] << 16) | (rom[ptSample + 1] << 8) | (rom[ptSample + 2] << 0);
                this.loop = (rom[ptSample + 3] << 8) | (rom[ptSample + 4] << 0);
                this.end = 0xffff - ((rom[ptSample + 5] << 8) | (rom[ptSample + 6] << 0));
                this.lfoVib = rom[ptSample + 7];
                this.dr1 = rom[ptSample + 8] & 0xf;
                this.ar = (rom[ptSample + 8] >> 4) & 0xf;
                this.dr2 = rom[ptSample + 9] & 0xf;
                this.dl = (rom[ptSample + 9] >> 4) & 0xf;
                this.rr = rom[ptSample + 10] & 0xf;
                this.krs = (rom[ptSample + 10] >> 4) & 0xf;
                this.am = rom[ptSample + 11];
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
        public Slot.Sample sample;
        private int base;
        private int offset;
        private int step;
        public int pan;
        private int tl;
        private int dstTL;
        private int tlStep;
        private int prev;
        private final Eg eg;
        // Phase Lfo
        private final Lfo pLfo;
        // AM Lfo
        private final Lfo aLfo;

        private int muted;

        Slot() {
            this.eg = new Eg();
            this.aLfo = new Lfo();
            this.pLfo = new Lfo();
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

        private void keyOn(Slot.Sample[] samples, int bankL, int bankR) {
            this.sample = samples[this.regs[1]];
            this.playing = 1;
            this.base = this.sample.start;
            this.offset = 0;
            this.prev = 0;
            this.tl = this.dstTL << SHIFT;

            this.calcEG();
            this.eg.state = Eg.State.ATTACK;
            this.eg.volume = 0;

            if (this.base >= 0x10_0000) {
                if ((this.pan & 8) != 0)
                    this.base = (this.base & 0xf_ffff) | bankL;
                else
                    this.base = (this.base & 0xf_ffff) | bankR;
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

        private int update(int cSample) {
            int adr = this.offset >> SHIFT;
            int step = this.step;

            int fPart = this.offset & ((1 << SHIFT) - 1);
            int sample = (cSample * fPart + this.prev * ((1 << SHIFT) - fPart)) >> SHIFT;

            if ((this.regs[6] & 7) != 0) { // Vibrato enabled
                step = step * this.pLfo.stepP();
                step >>= SHIFT;
            }

            this.offset += step;
            if (this.offset >= (this.sample.end << SHIFT)) {
                this.offset = this.sample.loop << SHIFT;
            }
            if ((adr ^ (this.offset >> SHIFT)) != 0) {
                this.prev = cSample;
            }

            if ((this.tl >> SHIFT) != this.dstTL)
                this.tl += this.tlStep;

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
    private int bankR, bankL;
    private float rate;
    private int romMask;
    private int romSize;
    private byte[] rom;
    // Frequency step table
    private final int[] fnsTable = new int[0x400];

    private static final int[] LPANTABLE = new int[0x800];
    private static final int[] RPANTABLE = new int[0x800];

    private static int fix(float v) {
        return (int) ((float) (1 << SHIFT) * v);
    }

    static {
        // Volume+pan table
        for (int i = 0; i < 0x800; i++) {

            int iTL = i & 0x7f;
            int iPAN = (i >> 7) & 0xf;

            float segaDB = (float) (iTL * (-24.0) / (float) 0x40);

            float tl = (float) Math.pow(10.0, segaDB / 20.0);

            float lpan, rPan;
            if (iPAN == 0x8) {
                lpan = rPan = 0.0F;
            } else if (iPAN == 0x0) {
                lpan = rPan = 1.0F;
            } else if ((iPAN & 0x8) != 0) {
                lpan = 1.0F;

                iPAN = (byte) (0x10 - iPAN);

                segaDB = (float) (iPAN * (-12.0) / (float) 0x4);

                rPan = (float) Math.pow(10.0, segaDB / 20.0);

                if ((iPAN & 0x7) == 7)
                    rPan = 0.0F;
            } else {
                rPan = 1.0F;

                segaDB = (float) (iPAN * (-12.0) / (float) 0x4);

                lpan = (float) Math.pow(10.0, segaDB / 20.0);
                if ((iPAN & 0x7) == 7)
                    lpan = 0.0F;
            }

            tl /= 4.0F;

            LPANTABLE[i] = fix(lpan * tl);
            RPANTABLE[i] = fix(rPan * tl);
        }
    }

    private static final int[] val2chan = new int[] {
            0, 1, 2, 3, 4, 5, 6, -1,
            7, 8, 9, 10, 11, 12, 13, -1,
            14, 15, 16, 17, 18, 19, 20, -1,
            21, 22, 23, 24, 25, 26, 27, -1,
    };

    private static final int SHIFT = 12;

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
                    int adr = slot.offset >> SHIFT;
                    int sample = slot.update((short) (this.rom[(slot.base + adr) & this.romMask] << 8));

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
            this.slots[s] = new Slot();
        }
        for (int i = 0; i < this.samples.length; i++) {
            this.samples[i] = new Slot.Sample();
        }
        this.romMask = 0x00;
        this.romSize = 0x00;
        this.rom = null;
        this.rate = (float) (clock / MULTIPCM_CLOCKDIV);

        // Pitch steps
        for (int j = 0; j < 0x400; j++) {
            float fCent = (float) (this.rate * (1024.0 + (float) j) / 1024.0);
            this.fnsTable[j] = (int) ((float) (1 << SHIFT) * fCent);
        }

        setBank(0x00, 0x00);

        return (int) (this.rate + 0.5);
    }

    public void stop() {
        this.rom = null;
    }

    public void reset() {
        for (int s = 0; s < 28; ++s) {
            this.slots[s].num = (byte) s;
            this.slots[s].playing = 0;
        }
    }

    public void write(int offset, int data) {
        switch (offset) {
        case 0: // data write
            writeSlot(this.slots[this.curSlot], this.address, data);
            break;
        case 1:
            this.curSlot = val2chan[data & 0x1f];
            //Debug.printf("curSlot%s", this.curSlot);
            break;
        case 2:
            this.address = Math.min(data, 7);
            break;
        }
        /*this.curSlot = val2chan[(offset >> 3) & 0x1F];
        this.address = offset & 0x07;
        writeSlot(this.slots + this.curSlot, this.address, data);*/
    }

    public void setBank(int leftOffset, int rightOffset) {
        this.bankL = leftOffset;
        this.bankR = rightOffset;
    }

    public void writeBank(int offset, int data) {
        if ((offset & 0x01) != 0)
            this.bankL = data << 16;
        if ((offset & 0x02) != 0)
            this.bankR = data << 16;
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
        if (this.romSize != romSize) {
            this.rom = new byte[romSize];
            this.romSize = romSize;

            for (this.romMask = 1; this.romMask < romSize; this.romMask <<= 1)
                ;
            this.romMask--;

            Arrays.fill(this.rom, 0, romSize, (byte) 0xff);
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        System.arraycopy(romData, 0, this.rom, dataStart, dataLength);

        if (dataStart < 0x200 * 12) {
            for (int i = 0; i < 512; i++) {
                Slot.Sample tempSample = this.samples[i];
                int sampleP = i * 12;
                tempSample.writeRom(this.rom, sampleP);
            }
        }
    }

    public void writeRom2(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        if (this.romSize != romSize) {
            this.rom = new byte[romSize];
            this.romSize = romSize;

            for (this.romMask = 1; this.romMask < romSize; this.romMask <<= 1)
                ;
            this.romMask--;

            Arrays.fill(this.rom, 0, romSize, (byte) 0xff);
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        System.arraycopy(romData, srcStartAddress, this.rom, dataStart, dataLength);

        if (dataStart < 0x200 * 12) {
            for (int curSmpl = 0; curSmpl < 512; curSmpl++) {
                Slot.Sample tempSmpl = this.samples[curSmpl];
                int ptSample = curSmpl * 12;
                tempSmpl.writeRom(this.rom, ptSample);
                //Debug.printf("LFOVIB%d  AM%d", this.ROM[ptSample + 7], this.ROM[ptSample + 11]);
            }
        }
    }

    public void setMuteMask(int muteMask) {
        for (byte curChn = 0; curChn < 28; curChn++)
            this.slots[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
    }

    private void writeSlot(Slot slot, int reg, int data) {
        slot.regs[reg] = data;

        switch (reg) {
        case 0: // PANPOT
            slot.pan = (data >> 4) & 0xf;
            break;
        case 1: { // sample
            // according to YMF278 sample write causes some base params written to the regs (envelope+lfos)
            // the game should never change the sample while playing.
            Slot.Sample sample = this.samples[slot.regs[1]];
            writeSlot(slot, 6, sample.lfoVib);
            writeSlot(slot, 7, sample.am);
        }
        break;
        case 2: // Pitch
        case 3: {
            slot.setPitch(this.fnsTable, this.rate);
        }
        break;
        case 4: { // KeyOn/Off (and more?)
            if ((data & 0x80) != 0) { // KeyOn
                slot.keyOn(samples, bankL, bankR);
            } else {
                slot.keyOff();
            }
        }
        break;
        case 5: { // TL+Interpolation
            slot.interpolate(data);
        }
        break;
        case 6: { // LFO freq+PLFO
            slot.lfo(data, this.rate);
        }
        break;
        case 7: { // ALFO
            slot.lfo(data, this.rate);
        }
        break;
        }
    }

    public Slot getSlot(int ch) {
        return slots[ch];
    }
}
