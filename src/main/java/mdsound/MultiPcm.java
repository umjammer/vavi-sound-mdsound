package mdsound;

public class MultiPcm extends Instrument.BaseInstrument {

    @Override
    public void reset(byte chipID) {
        device_reset_multipcm(chipID);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipID, int clock) {
        return device_start_multipcm(chipID, clock);
    }

    @Override
    public int start(byte chipID, int samplingrate, int clockValue, Object... option) {
        return device_start_multipcm(chipID, (int) clockValue);
    }

    @Override
    public void stop(byte chipID) {
        device_stop_multipcm(chipID);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        MultiPCM_update(chipID, outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    /*
     * Sega System 32 Multi/Model 1/Model 2 custom PCM chip (315-5560) emulation.
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
          bits 1-7 = volume attenuate (0=max, 7f=min)
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
     * - The YM278B manual states that the chip supports 512 instruments. The MultiPCM probably supports them
     * too but the high bit position is unknown (probably reg 2 low bit). Any game use more than 256?
     */
    public static class _MultiPCM {

        //????
        private static final double MULTIPCM_CLOCKDIV = 180.0;

        public static class _Sample {
            public int start;
            public int loop;
            public int end;
            public byte ar, dr1, dr2, dl, rr;
            public byte krs;
            public byte lfovib;
            public byte am;
        }

        enum _STATE {ATTACK, DECAY1, DECAY2, RELEASE}

        static class _EG {
            public int volume; //
            public _STATE state;
            public int step = 0;
            //step vals
            public int AR; // Attack
            public int d1R; // Decay1
            public int D2R; // Decay2
            public int RR; // Release
            public int DL; // Decay level

            public void update() {
                switch (this.state) {
                case ATTACK:
                    this.volume += this.AR;
                    if (this.volume >= (0x3ff << EG_SHIFT)) {
                        this.state = _STATE.DECAY1;
                        if (this.d1R >= (0x400 << EG_SHIFT)) // Skip DECAY1, go directly to DECAY2
                            this.state = _STATE.DECAY2;
                        this.volume = 0x3ff << EG_SHIFT;
                    }
                    break;
                case DECAY1:
                    this.volume -= this.d1R;
                    if (this.volume <= 0)
                        this.volume = 0;
                    if (this.volume >> EG_SHIFT <= (this.DL << (10 - 4)))
                        this.state = _STATE.DECAY2;
                    break;
                case DECAY2:
                    this.volume -= this.D2R;
                    if (this.volume <= 0)
                        this.volume = 0;
                    break;
                case RELEASE:
                    this.volume -= this.RR;
                    if (this.volume <= 0) {
                        this.volume = 0;
                    }
                    break;
                default:
                    break;
                }
            }
        }

        /*
         * LFO  SECTION
         */
        static class _LFO {
            public int phase;
            public int phase_step;
            public int[] table;
            public int[] scale;

            private int stepP() {
                this.phase += this.phase_step;
                int p = this.table[(this.phase >> SHIFT) & 0xff];
                p = this.scale[p + 128];
                return p << (_MultiPCM.SHIFT - SHIFT);
            }

            private int stepA() {
                this.phase += this.phase_step;
                int p = this.table[(this.phase >> SHIFT) & 0xff];
                p = this.scale[p];
                return p << (_MultiPCM.SHIFT - SHIFT);
            }

            private static final int SHIFT = 8;

            private static int LFIX(float v) {
                return (int) ((float) (1 << SHIFT) * v);
            }

            //Convert DB to multiply amplitude
            private static int DB(float v) {
                return LFIX((float) Math.pow(10.0, v / 20.0));
            }

            //Convert cents to step increment
            private static int CENTS(float v) {
                return LFIX((float) Math.pow(2.0, v / 1200.0));
            }

            private int[] plfoTri = new int[256];
            private int[] alfoTri = new int[256];

            private static final float[] LFOFreq = new float[] {0.168f, 2.019f, 3.196f, 4.206f, 5.215f, 5.888f, 6.224f, 7.066f};  //Hz;
            private static final float[] PSCALE = new float[] {0.0f, 3.378f, 5.065f, 6.750f, 10.114f, 20.170f, 40.180f, 79.307f}; //cents
            private static final float[] ASCALE = new float[] {0.0f, 0.4f, 0.8f, 1.5f, 3.0f, 6.0f, 12.0f, 24.0f};                 //DB
            private int[][] pScales = new int[][] {new int[256], new int[256], new int[256], new int[256], new int[256], new int[256], new int[256], new int[256]};
            private int[][] aScales = new int[][] {new int[256], new int[256], new int[256], new int[256], new int[256], new int[256], new int[256], new int[256]};

            {
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
                    alfoTri[i] = a;
                    plfoTri[i] = p;
                }

                for (int s = 0; s < 8; ++s) {
                    float limit = PSCALE[s];
                    for (int i = -128; i < 128; ++i) {
                        pScales[s][i + 128] = CENTS((limit * (float) i) / 128.0F);
                    }
                    limit = -ASCALE[s];
                    for (int i = 0; i < 256; ++i) {
                        aScales[s][i] = DB((limit * (float) i) / 256.0F);
                    }
                }
            }

            private void computeStep(int lfof, int lfos, int alfo, float rate) {
                float step = (float) (LFOFreq[lfof] * 256.0 / rate);
                this.phase_step = (int) ((float) (1 << SHIFT) * step);
                if (alfo != 0) {
                    this.table = alfoTri;
                    this.scale = aScales[lfos];
                } else {
                    this.table = plfoTri;
                    this.scale = pScales[lfos];
                }
            }
        }

        public static class _SLOT {
            public byte num;
            public byte[] regs = new byte[8];
            public int playing;
            public _Sample sample;
            public int base;
            public int offset;
            public int step;
            public int pan, tl;
            public int dstTL;
            public int tlStep;
            public int prev;
            public _EG eg = null;
            public _LFO plfo = null;   //Phase Lfo
            public _LFO alfo = null;   //AM Lfo

            public byte muted;

            private int updateEG() {
                eg.update();
                switch (this.eg.state) {
                case RELEASE:
                    if (this.eg.volume <= 0) {
                        this.playing = 0;
                    }
                    break;
                default:
                    return 1 << SHIFT;
                }
                return lin2expvol[this.eg.volume >> EG_SHIFT];
            }

            private void calcEG(int[] arStep, int[] drStep) {
                int octave = ((this.regs[3] >> 4) - 1) & 0xf;
                int rate;
                if ((octave & 8) != 0) octave = octave - 16;
                if (this.sample.krs != 0xf)
                    rate = (octave + this.sample.krs) * 2 + ((this.regs[3] >> 3) & 1);
                else
                    rate = 0;

                this.eg.AR = getRate(arStep, rate, this.sample.ar);
                this.eg.d1R = getRate(drStep, rate, this.sample.dr1);
                this.eg.D2R = getRate(drStep, rate, this.sample.dr2);
                this.eg.RR = getRate(drStep, rate, this.sample.rr);
                this.eg.DL = 0xf - this.sample.dl;
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

            public void setPitch(int[] fnsTable, float rate) {
                int oct = ((this.regs[3] >> 4) - 1) & 0xf;
                int pitch = ((this.regs[3] & 0xf) << 6) | (this.regs[2] >> 2);
                pitch = fnsTable[pitch];
                if ((oct & 0x8) != 0)
                    pitch >>= 16 - oct;
                else
                    pitch <<= oct;
                this.step = (int) (pitch / rate);
            }

            public void keyOn(_Sample[] samples, int[] arStep, int[] drStep, int bankL, int bankR) {
                this.sample = samples[this.regs[1]];
                this.playing = 1;
                this.base = this.sample.start;
                this.offset = 0;
                this.prev = 0;
                this.tl = this.dstTL << SHIFT;

                this.calcEG(arStep, drStep);
                this.eg.state = _STATE.ATTACK;
                this.eg.volume = 0;

                if (this.base >= 0x100000) {
                    if ((this.pan & 8) != 0)
                        this.base = (this.base & 0xfffff) | bankL;
                    else
                        this.base = (this.base & 0xfffff) | bankR;
                }
            }

            public void keyOff() {
                if (this.playing != 0) {
                    if (this.sample.rr != 0xf)
                        this.eg.state = _STATE.RELEASE;
                    else
                        this.playing = 0;
                }
            }

            public void interpolate(int[] tlSteps, int data) {
                this.dstTL = (data >> 1) & 0x7f;
                if ((data & 1) == 0)    //Interpolate TL
                {
                    if ((this.tl >> SHIFT) > this.dstTL)
                        this.tlStep = tlSteps[0]; // decrease
                    else
                        this.tlStep = tlSteps[1]; // increase
                } else
                    this.tl = this.dstTL << SHIFT;
            }

            public void lfo(byte data, float rate) {
                if (data != 0) {
                    this.plfo.computeStep((this.regs[6] >> 3) & 7, this.regs[6] & 7, 0, rate);
                    this.alfo.computeStep((this.regs[6] >> 3) & 7, this.regs[7] & 7, 1, rate);
                }
            }
        }

        private static int[] lin2expvol = new int[0x400];

        public _Sample[] samples = new _Sample[0x200];        // Max 512 samples
        public _SLOT[] slots = new _SLOT[28];
        public int curSlot;
        public int address;
        public int bankR, bankL;
        public float rate;
        public int romMask;
        public int romSize;
        public byte[] rom;
        // I include these in the chip because they depend on the chip clock
        public int[] arStep = new int[0x40], drStep = new int[0x40];    // Envelope step table
        public int[] fnsTable = new int[0x400];      // Frequency step table

        private byte isInit = 0x00;
        private int[] LPANTABLE = new int[0x800], RPANTABLE = new int[0x800];

        private static int fix(float v) {
            return ((int) ((float) (1 << SHIFT) * (v)));
        }

        private static final int[] val2chan = new int[] {
                0, 1, 2, 3, 4, 5, 6, -1,
                7, 8, 9, 10, 11, 12, 13, -1,
                14, 15, 16, 17, 18, 19, 20, -1,
                21, 22, 23, 24, 25, 26, 27, -1,
        };

        private static final int SHIFT = 12;

        //private double MULTIPCM_RATE = 44100.0;

        /*
         * ENVELOPE SECTION
         */

        //Times are based on a 44100Hz timesuper. It's adjusted to the actual sampling rate on startup

        private static final double[] BaseTimes = new double[] {
                0, 0, 0, 0, 6222.95, 4978.37, 4148.66, 3556.01, 3111.47, 2489.21, 2074.33, 1778.00, 1555.74, 1244.63, 1037.19, 889.02,
                777.87, 622.31, 518.59, 444.54, 388.93, 311.16, 259.32, 222.27, 194.47, 155.60, 129.66, 111.16, 97.23, 77.82, 64.85, 55.60,
                48.62, 38.91, 32.43, 27.80, 24.31, 19.46, 16.24, 13.92, 12.15, 9.75, 8.12, 6.98, 6.08, 4.90, 4.08, 3.49,
                3.04, 2.49, 2.13, 1.90, 1.72, 1.41, 1.18, 1.04, 0.91, 0.73, 0.59, 0.50, 0.45, 0.45, 0.45, 0.45
        };

        private static final double AR2DR = 14.32833;
        private int[] tlSteps = new int[2];

        private static final int EG_SHIFT = 16;

        private void writeSlot(_SLOT slot, int reg, byte data) {
            slot.regs[reg] = data;

            switch (reg) {
            case 0: // PANPOT
                slot.pan = (data >> 4) & 0xf;
                break;
            case 1: { // sample
                // according to YMF278 sample write causes some base params written to the regs (envelope+lfos)
                // the game should never change the sample while playing.
                _Sample sample = this.samples[slot.regs[1]];
                writeSlot(slot, 6, sample.lfovib);
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
                    slot.keyOn(samples, arStep, drStep, bankL, bankR);
                } else {
                    slot.keyOff();
                }
            }
            break;
            case 5: { // TL+Interpolation
                slot.interpolate(tlSteps, data);
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

        public void update(int[][] outputs, int samples) {
            int[][] datap = new int[2][];

            datap[0] = outputs[0];
            datap[1] = outputs[1];

            for (int j = 0; j < samples; j++) {
                datap[0][j] = 0;
                datap[1][j] = 0;
            }

            for (int i = 0; i < samples; ++i) {
                int smpl = 0;
                int smpr = 0;
                for (int sl = 0; sl < 28; ++sl) {
                    _SLOT slot = this.slots[sl];
                    if (slot.playing != 0 && slot.muted == 0) {
                        int vol = (slot.tl >> SHIFT) | (slot.pan << 7);
                        int adr = slot.offset >> SHIFT;
                        int sample;
                        int step = slot.step;
                        int csample = (short) (this.rom[(slot.base + adr) & this.romMask] << 8);
                        int fpart = slot.offset & ((1 << SHIFT) - 1);
                        sample = (csample * fpart + slot.prev * ((1 << SHIFT) - fpart)) >> SHIFT;

                        if ((slot.regs[6] & 7) != 0) { // Vibrato enabled
                            step = step * slot.plfo.stepP();
                            step >>= SHIFT;
                        }

                        slot.offset += step;
                        if (slot.offset >= (slot.sample.end << SHIFT)) {
                            slot.offset = slot.sample.loop << SHIFT;
                        }
                        if ((adr ^ (slot.offset >> SHIFT)) != 0) {
                            slot.prev = csample;
                        }

                        if ((slot.tl >> SHIFT) != slot.dstTL)
                            slot.tl += slot.tlStep;

                        if ((slot.regs[7] & 7) != 0) { // Tremolo enabled
                            sample = sample * slot.alfo.stepA();
                            sample >>= SHIFT;
                        }

                        sample = (sample * slot.updateEG()) >> 10;

                        smpl += (LPANTABLE[vol] * sample) >> SHIFT;
                        smpr += (RPANTABLE[vol] * sample) >> SHIFT;
                    }
                }
                datap[0][i] = smpl;
                datap[1][i] = smpr;
            }
        }

        public int start(int clock) {
            for (int i = 0; i < this.slots.length; i++) {
                this.slots[i] = new _SLOT();
                this.slots[i].eg = new _EG();
                this.slots[i].alfo = new _LFO();
                this.slots[i].plfo = new _LFO();
            }
            for (int i = 0; i < this.samples.length; i++) {
                this.samples[i] = new _Sample();
            }
            this.romMask = 0x00;
            this.romSize = 0x00;
            this.rom = null;
            this.rate = (float) (clock / MULTIPCM_CLOCKDIV);

            if (isInit == 0) {
                // Volume+pan table
                for (int i = 0; i < 0x800; ++i) {
                    float segaDB = 0;
                    float tl;
                    float lpan, rPan;

                    byte iTL = (byte) (i & 0x7f);
                    byte iPAN = (byte) ((i >> 7) & 0xf);

                    segaDB = (float) (iTL * (-24.0) / (float) 0x40);

                    tl = (float) Math.pow(10.0, segaDB / 20.0);


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

                isInit = 0x01;
            }

            // Pitch steps
            for (int i = 0; i < 0x400; ++i) {
                float fcent = (float) (this.rate * (1024.0 + (float) i) / 1024.0);
                this.fnsTable[i] = (int) ((float) (1 << SHIFT) * fcent);
            }

            // Envelope steps
            for (int i = 0; i < 0x40; ++i) {
                // Times are based on 44100 clock, adjust to real chip clock
                this.arStep[i] = (int) ((float) (0x400 << EG_SHIFT) / (BaseTimes[i] * 44100.0 / (1000.0)));
                this.drStep[i] = (int) ((float) (0x400 << EG_SHIFT) / (BaseTimes[i] * AR2DR * 44100.0 / (1000.0)));
            }
            this.arStep[0] = this.arStep[1] = this.arStep[2] = this.arStep[3] = 0;
            this.arStep[0x3f] = 0x400 << EG_SHIFT;
            this.drStep[0] = this.drStep[1] = this.drStep[2] = this.drStep[3] = 0;

            // TL Interpolation steps
            // lower
            tlSteps[0] = -(int) ((float) (0x80 << SHIFT) / (78.2 * 44100.0 / 1000.0));
            // raise
            tlSteps[1] = (int) ((float) (0x80 << SHIFT) / (78.2 * 2 * 44100.0 / 1000.0));

            // build the linear.exponential ramps
            for (int i = 0; i < 0x400; ++i) {
                float db = -(float) ((96.0 - (96.0 * (float) i / (float) 0x400)));
                lin2expvol[i] = (int) (Math.pow(10.0, db / 20.0) * (float) (1 << SHIFT));
            }

            setBank(0x00, 0x00);

            return (int) (this.rate + 0.5);
        }

        public void stop() {
            this.rom = null;
        }

        public void reset() {
            for (int i = 0; i < 28; ++i) {
                this.slots[i].num = (byte) i;
                this.slots[i].playing = 0;
            }
        }

        private void write(int offset, byte data) {
            switch (offset) {
            case 0: // Data write
                writeSlot(this.slots[this.curSlot], this.address, data);
                break;
            case 1:
                this.curSlot = val2chan[data & 0x1f];
                //System.err.printf("curSlot%s", this.curSlot);
                break;
            case 2:
                this.address = (int) ((data > 7) ? 7 : data);
                break;
            }
            /*this.curSlot = val2chan[(offset >> 3) & 0x1F];
            this.address = offset & 0x07;
            writeSlot(this.slots + this.curSlot, this.address, data);*/
        }

        public void setBank(int leftoffs, int rightoffs) {
            this.bankL = leftoffs;
            this.bankR = rightoffs;
        }

        public void writeBank(byte offset, int data) {
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

                for (int i = 0; i < romSize; i++) this.rom[i] = -1;//0xff;
                //memset(this.ROM, 0xFF, romSize);
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            for (int i = 0; i < dataLength; i++) this.rom[i + dataStart] = (byte) romData[i];
            //memcpy(this.ROM + dataStart, romData, dataLength);

            if (dataStart < 0x200 * 12) {
                for (int curSmpl = 0; curSmpl < 512; curSmpl++) {
                    _Sample tempSmpl = this.samples[curSmpl];
                    //ptSample = (byte*)this.ROM + curSmpl * 12;
                    int ptSample = curSmpl * 12;
                    tempSmpl.start = (this.rom[ptSample + 0] << 16) | (this.rom[ptSample + 1] << 8) | (this.rom[ptSample + 2] << 0);
                    tempSmpl.loop = (this.rom[ptSample + 3] << 8) | (this.rom[ptSample + 4] << 0);
                    tempSmpl.end = 0xffff - ((this.rom[ptSample + 5] << 8) | (this.rom[ptSample + 6] << 0));
                    tempSmpl.lfovib = this.rom[ptSample + 7];
                    tempSmpl.dr1 = (byte) (this.rom[ptSample + 8] & 0xf);
                    tempSmpl.ar = (byte) ((this.rom[ptSample + 8] >> 4) & 0xf);
                    tempSmpl.dr2 = (byte) (this.rom[ptSample + 9] & 0xf);
                    tempSmpl.dl = (byte) ((this.rom[ptSample + 9] >> 4) & 0xf);
                    tempSmpl.rr = (byte) (this.rom[ptSample + 10] & 0xf);
                    tempSmpl.krs = (byte) ((this.rom[ptSample + 10] >> 4) & 0xf);
                    tempSmpl.am = (byte) (this.rom[ptSample + 11]);
                }
            }
        }

        public void write_rom2(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
            if (this.romSize != romSize) {
                this.rom = new byte[romSize];
                this.romSize = romSize;

                for (this.romMask = 1; this.romMask < romSize; this.romMask <<= 1)
                    ;
                this.romMask--;

                for (int i = 0; i < romSize; i++) this.rom[i] = -1;//0xff;
                //memset(this.ROM, 0xFF, romSize);
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            if (dataLength >= 0) System.arraycopy(romData, srcStartAddress, this.rom, dataStart, dataLength);

            if (dataStart < 0x200 * 12) {
                for (int curSmpl = 0; curSmpl < 512; curSmpl++) {
                    _Sample tempSmpl = this.samples[curSmpl];
                    //ptSample = (byte*)this.ROM + curSmpl * 12;
                    int ptSample = curSmpl * 12;
                    tempSmpl.start = (this.rom[ptSample + 0] << 16) | (this.rom[ptSample + 1] << 8) | (this.rom[ptSample + 2] << 0);
                    tempSmpl.loop = (this.rom[ptSample + 3] << 8) | (this.rom[ptSample + 4] << 0);
                    tempSmpl.end = 0xffff - ((this.rom[ptSample + 5] << 8) | (this.rom[ptSample + 6] << 0));
                    tempSmpl.lfovib = this.rom[ptSample + 7];
                    tempSmpl.dr1 = (byte) (this.rom[ptSample + 8] & 0xf);
                    tempSmpl.ar = (byte) ((this.rom[ptSample + 8] >> 4) & 0xf);
                    tempSmpl.dr2 = (byte) (this.rom[ptSample + 9] & 0xf);
                    tempSmpl.dl = (byte) ((this.rom[ptSample + 9] >> 4) & 0xf);
                    tempSmpl.rr = (byte) (this.rom[ptSample + 10] & 0xf);
                    tempSmpl.krs = (byte) ((this.rom[ptSample + 10] >> 4) & 0xf);
                    tempSmpl.am = this.rom[ptSample + 11];
                    //System.err.printf("LFOVIB%d  AM%d", this.ROM[ptSample + 7], this.ROM[ptSample + 11]);
                }
            }
        }

        public void set_mute_mask(int muteMask) {
            for (byte curChn = 0; curChn < 28; curChn++)
                this.slots[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
        }
    }

    private static final int MAX_CHIPS = 0x02;
    private _MultiPCM[] multiPCMData = new _MultiPCM[] {new _MultiPCM(), new _MultiPCM()};

    @Override
    public String getName() {
        return "Multi PCM";
    }

    @Override
    public String getShortName() {
        return "mPCM";
    }

    public void MultiPCM_update(byte chipID, int[][] outputs, int samples) {
        _MultiPCM ptChip = multiPCMData[chipID];
        ptChip.update(outputs, samples);
    }

    public _MultiPCM multipcm_r(int chipID) {
        return multiPCMData[chipID];
    }

    public int device_start_multipcm(byte chipID, int clock) {
        if (chipID >= MAX_CHIPS)
            return 0;

        _MultiPCM ptChip = multiPCMData[chipID];
        return ptChip.start(clock);
    }

    public void device_stop_multipcm(byte chipID) {
        _MultiPCM ptChip = multiPCMData[chipID];
        ptChip.stop();
    }

    public void device_reset_multipcm(byte chipID) {
        _MultiPCM ptChip = multiPCMData[chipID];
        ptChip.reset();
    }

    private void multipcm_w(byte chipID, int offset, byte data) {
        _MultiPCM ptChip = multiPCMData[chipID];
        ptChip.write(offset, data);
    }

    /* MAME/M1 access functions */
    public void multipcm_set_bank(byte chipID, int leftoffs, int rightoffs) {
        _MultiPCM ptChip = multiPCMData[chipID];
        ptChip.setBank(leftoffs, rightoffs);
    }

    public void multipcm_bank_write(byte chipID, byte offset, int data) {
        _MultiPCM ptChip = multiPCMData[chipID];
        ptChip.writeBank(offset, data);
    }

    public void multipcm_write_rom(byte chipID, int romSize, int dataStart, int dataLength, byte[] romData) {
        _MultiPCM ptChip = multiPCMData[chipID];
        ptChip.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void multipcm_write_rom2(byte chipID, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        _MultiPCM ptChip = multiPCMData[chipID];
        ptChip.write_rom2(romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    public void multipcm_set_mute_mask(byte chipID, int muteMask) {
        _MultiPCM ptChip = multiPCMData[chipID];
        ptChip.set_mute_mask(muteMask);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        multipcm_w(chipID, adr, (byte) data);
        return 0;
    }

    /**
     * Generic get_info
     */
        /*DEVICE_GET_INFO( MultiPcm ) {
            switch (state) {
                // --- the following bits of info are returned as 64-bit signed integers ---
                case DEVINFO_INT_TOKEN_BYTES:     info.i = sizeof(MultiPCM);    break;

                // --- the following bits of info are returned as pointers to data or functions ---
                case DEVINFO_FCT_START:       info.start = DEVICE_START_NAME( MultiPcm );  break;
                case DEVINFO_FCT_STOP:       // Nothing          break;
                case DEVINFO_FCT_RESET:       // Nothing          break;

                // --- the following bits of info are returned as NULL-terminated strings ---
                case DEVINFO_STR_NAME:       strcpy(info.s, "Sega/Yamaha 315-5560");  break;
                case DEVINFO_STR_FAMILY:     strcpy(info.s, "Sega custom");     break;
                case DEVINFO_STR_VERSION:     strcpy(info.s, "2.0");       break;
                case DEVINFO_STR_SOURCE_FILE:      strcpy(info.s, __FILE__);      break;
                case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
            }
        }*/

    //DEFINE_LEGACY_SOUND_DEVICE(MULTIPCM, MultiPcm);
}
