package mdsound.np.chip;

public class Emu2149 {
    private static final int VOL_DEFAULT = 1;
    private static final int VOL_YM2149 = 0;
    private static final int VOL_AY_3_8910 = 1;

    private static final int[][] VolTbl = {
            {0x00, 0x01, 0x01, 0x02, 0x02, 0x03, 0x03, 0x04, 0x05, 0x06, 0x07, 0x09, 0x0B, 0x0D, 0x0F, 0x12,
                    0x16, 0x1A, 0x1F, 0x25, 0x2D, 0x35, 0x3F, 0x4C, 0x5A, 0x6A, 0x7F, 0x97, 0xB4, 0xD6, 0xEB, 0xff},
            {0x00, 0x00, 0x01, 0x01, 0x02, 0x02, 0x03, 0x03, 0x05, 0x05, 0x07, 0x07, 0x0B, 0x0B, 0x0F, 0x0F,
                    0x16, 0x16, 0x1F, 0x1F, 0x2D, 0x2D, 0x3F, 0x3F, 0x5A, 0x5A, 0x7F, 0x7F, 0xB4, 0xB4, 0xff, 0xff}
    };

    static class Psg {
        private static int PSG_MASK_CH(int x) {
            return (1 << (x));
        }

        /* Volume Table */
        int[] volTbl;

        private final int[] reg = new int[0x20];
        private int _out;
        final int[] cout = new int[3];

        int clk;
        private int rate;
        private int baseIncr;
        private int quality;

        private final int[] count = new int[3];
        final int[] volume = new int[3];
        final int[] freq = new int[3];
        private final int[] edge = new int[3];
        final int[] tMask = new int[3];
        final int[] nMask = new int[3];
        private int mask;

        private int baseCount;

        private int envVolume;
        int envPtr;
        private int envFace;

        int envContinue;
        int envAttack;
        int envAlternate;
        int envHold;
        private int envPause;
        private int envReset;

        int envFreq;
        private int envCount;

        int noiseSeed;
        private int noiseCount;
        int noiseFreq;

        // rate converter
        private int realStep;
        private int psgTime;
        private int psgStep;

        // I/O Ctrl
        private int adr;

        private static final int GETA_BITS = 24;

        private void internalRefresh() {
            if (this.quality != 0) {
                this.baseIncr = 1 << GETA_BITS;
                this.realStep = (1 << 31) / this.rate;
                this.psgStep = (1 << 31) / (this.clk / 16);
                this.psgTime = 0;
            } else {
                this.baseIncr = (int) ((double) this.clk * (1 << GETA_BITS) / (16 * this.rate));
            }
        }

        public void setRate(int r) {
            this.rate = r != 0 ? r : 44100;
            internalRefresh();
        }

        public void setQuality(int q) {
            this.quality = q;
            internalRefresh();
        }

        public int setMask(int mask) {
            int ret = this.mask;
            this.mask = mask;
            return ret;
        }

        public int toggleMask(int mask) {
            int ret = this.mask;
            this.mask ^= mask;
            return ret;
        }

        public void reset() {
            int i;

            this.baseCount = 0;

            for (i = 0; i < 3; i++) {
                this.cout[i] = 0;
                this.count[i] = 0x1000;
                this.freq[i] = 0;
                this.edge[i] = 0;
                this.volume[i] = 0;
            }

            this.mask = 0;

            for (i = 0; i < 16; i++)
                this.reg[i] = 0;
            this.adr = 0;

            this.noiseSeed = 0xffff;
            this.noiseCount = 0x40;
            this.noiseFreq = 0;

            this.envVolume = 0;
            this.envPtr = 0;
            this.envFreq = 0;
            this.envCount = 0;
            this.envPause = 1;

            this._out = 0;
        }

        public int readIO() {
            return this.reg[this.adr];
        }

        public int readReg(int reg) {
            return this.reg[reg & 0x1f];

        }

        public void writeIO(int adr, int val) {
            if ((adr & 1) != 0)
                this.writeReg(this.adr, val);
            else
                this.adr = val & 0x1f;
        }

        public int calc() {

            int i, noise;
            int incr;
            int mix = 0;

            this.baseCount += this.baseIncr;
            incr = (this.baseCount >> GETA_BITS);
            this.baseCount &= (1 << GETA_BITS) - 1;

            /* Envelope */
            this.envCount += incr;
            while (this.envCount >= 0x10000 && this.envFreq != 0) {
                if (this.envPause == 0) {
                    if (this.envFace != 0)
                        this.envPtr = (this.envPtr + 1) & 0x3f;
                    else
                        this.envPtr = (this.envPtr + 0x3f) & 0x3f;
                }

                if ((this.envPtr & 0x20) != 0) { // if carry or borrow
                    if (this.envContinue != 0) {
                        if ((this.envAlternate ^ this.envHold) != 0) this.envFace ^= 1;
                        if (this.envHold != 0) this.envPause = 1;
                        this.envPtr = (this.envFace != 0) ? 0 : 0x1f;
                    } else {
                        this.envPause = 1;
                        this.envPtr = 0;
                    }
                }

                this.envCount -= this.envFreq;
            }

            // Noise
            this.noiseCount += incr;
            if ((this.noiseCount & 0x40) != 0) {
                if ((this.noiseSeed & 1) != 0)
                    this.noiseSeed ^= 0x24000;
                this.noiseSeed >>= 1;
                this.noiseCount -= this.noiseFreq;
            }
            noise = this.noiseSeed & 1;

            // Tone/
            for (i = 0; i < 3; i++) {
                this.count[i] += incr;
                if ((this.count[i] & 0x1000) != 0) {
                    if (this.freq[i] > 1) {
                        this.edge[i] = (~this.edge[i]) & 1;
                        this.count[i] -= this.freq[i];
                    } else {
                        this.edge[i] = 1;
                    }
                }

                this.cout[i] = 0; // maintaining cout for stereo mix

                if ((this.mask & PSG_MASK_CH(i)) != 0)
                    continue;

                if ((this.tMask[i] != 0 || this.edge[i] != 0) && (this.nMask[i] != 0 || noise != 0)) {
                    if ((this.volume[i] & 32) == 0)
                        this.cout[i] = this.volTbl[this.volume[i] & 31];
                    else
                        this.cout[i] = this.volTbl[this.envPtr];

                    mix += this.cout[i];
                }
            }

            return mix;
        }

        public int calcPsg() {
            if (this.quality == 0)
                return this.calc() << 4;

            // Simple rate converter
            while (this.realStep > this.psgTime) {
                this.psgTime += this.psgStep;
                this._out += this.calc();
                this._out >>= 1;
            }

            this.psgTime = this.psgTime - this.realStep;

            return this._out << 4;
        }

        public void writeReg(int reg, int val) {
            int c;

            if (reg > 15) return;

            this.reg[reg] = val & 0xff;
            switch (reg) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                c = reg >> 1;
                this.freq[c] = ((this.reg[c * 2 + 1] & 15) << 8) + this.reg[c * 2];
                break;

            case 6:
                this.noiseFreq = (val == 0) ? 1 : ((val & 31) << 1);
                break;

            case 7:
                this.tMask[0] = (val & 1);
                this.tMask[1] = (val & 2);
                this.tMask[2] = (val & 4);
                this.nMask[0] = (val & 8);
                this.nMask[1] = (val & 16);
                this.nMask[2] = (val & 32);
                break;

            case 8:
            case 9:
            case 10:
                this.volume[reg - 8] = val << 1;

                break;

            case 11:
            case 12:
                this.envFreq = ((this.reg[12] & 0xff) << 8) + (this.reg[11] & 0xff);
                break;

            case 13:
                this.envContinue = (val >> 3) & 1;
                this.envAttack = (val >> 2) & 1;
                this.envAlternate = (val >> 1) & 1;
                this.envHold = val & 1;
                this.envFace = this.envAttack;
                this.envPause = 0;
                this.envCount = 0x10000 - this.envFreq;
                this.envPtr = this.envFace != 0 ? 0 : 0x1f;
                break;

            case 14:
            case 15:
            default:
                break;
            }
        }
    }

    Psg psg;

    public Emu2149(int c, int r) {
        psg = new Psg();

        setVolumeMode(VOL_DEFAULT);
        psg.clk = c;
        psg.rate = r != 0 ? r : 44100;
        psg.setQuality(0);
    }

    public void setVolumeMode(int type) {
        switch (type) {
        case 1:
            psg.volTbl = VolTbl[VOL_YM2149];
            break;
        case 2:
            psg.volTbl = VolTbl[VOL_AY_3_8910];
            break;
        default:
            psg.volTbl = VolTbl[VOL_DEFAULT];
            break;
        }
    }
}
