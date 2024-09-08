package mdsound.np;


//
// NES 2A03
//
// Ported from NSFPlay 2.2 to VGMPlay (including C++ . C conversion)
// by Valley Bell on 24 September 2013
// Updated to NSFPlay 2.3 on 26 September 2013
// (Note: Encoding is UTF-8)
public class NpNesApu {

    // Master Clock: 21477272 (NTSC)
    // APU Clock = Master Clock / 12
    private static final double DEFAULT_CLOCK = 1789772.0; // not sure if this shouldn't be 1789772,667 instead
    private static final int DEFAULT_RATE = 44100;

    /**
     * Upper half of APU
     */
    public enum OPT {
        UNMUTE_ON_RESET,
        NONLINEAR_MIXER,
        PHASE_REFRESH,
        DUTY_SWAP,
        END
    }

    enum SQR {
        DUMMY,
        SQR0_MASK,
        SQR1_MASK,
    }

    // 各種オプション
    public int[] option = new int[OPT.END.ordinal()];
    public int mask;
    public int[][] sm = new int[][] {new int[2], new int[2]};

    public int gclock;
    public byte[] reg = new byte[0x20];
    public int[] out = new int[2];
    public double rate, clock;

    // nonlinear mixer
    public int[] squareTable = new int[32];

    // frequency divider
    public int[] sCounter = new int[2];
    // phase counter
    public int[] sPhase = new int[2];

    public int[] duty = new int[2];
    public int[] volume = new int[2];
    public int[] freq = new int[2];
    public int[] sfreq = new int[2];

    public boolean[] sweepEnable = new boolean[2];
    public boolean[] sweepMode = new boolean[2];
    public boolean[] sweepWrite = new boolean[2];
    public int[] sweepDivPeriod = new int[2];
    public int[] sweepDiv = new int[2];
    public int[] sweepAmount = new int[2];

    public boolean[] envelopeDisable = new boolean[2];
    public boolean[] envelopeLoop = new boolean[2];
    public boolean[] envelopeWrite = new boolean[2];
    public int[] envelopeDivPeriod = new int[2];
    public int[] envelopeDiv = new int[2];
    public int[] envelopeCounter = new int[2];

    public int[] lengthCounter = new int[2];

    public boolean[] enable = new boolean[2];

    public Counter tickCount = new Counter();
    public int tickLast;

    private void sweepSqr(int i) {
        int shifted = this.freq[i] >> this.sweepAmount[i];
        if (i == 0 && this.sweepMode[i]) shifted += 1;
        this.sfreq[i] = this.freq[i] + (this.sweepMode[i] ? -shifted : shifted);
        //Debug.printf("shifted[%d] = %d (%d >> %d)\n",i,shifted,this.freq[i],this.sweep_amount[i]);
    }

    public void sequenceFrame(int s) {
        int i;

        //Debug.printf("sequenceFrame(%d)\n", s);

        if (s > 3) return; // no operation in step 4

        // 240hz clock
        for (i = 0; i < 2; ++i) {
            boolean divider = false;
            if (this.envelopeWrite[i]) {
                this.envelopeWrite[i] = false;
                this.envelopeCounter[i] = 15;
                this.envelopeDiv[i] = 0;
            } else {
                ++this.envelopeDiv[i];
                if (this.envelopeDiv[i] > this.envelopeDivPeriod[i]) {
                    divider = true;
                    this.envelopeDiv[i] = 0;
                }
            }
            if (divider) {
                if (this.envelopeLoop[i] && this.envelopeCounter[i] == 0)
                    this.envelopeCounter[i] = 15;
                else if (this.envelopeCounter[i] > 0)
                    --this.envelopeCounter[i];
            }
        }

        // 120hz clock
        if ((s & 1) == 0)
            for (i = 0; i < 2; ++i) {
                if (!this.envelopeLoop[i] && (this.lengthCounter[i] > 0))
                    --this.lengthCounter[i];

                if (this.sweepEnable[i]) {
                    //Debug.printf("Clock sweep: %d\n", i);

                    --this.sweepDiv[i];
                    if (this.sweepDiv[i] <= 0) {
                        sweepSqr(i); // calculate new sweep target

                        //Debug.printf("sweep_div[%d] (0/%d)\n",i,this.sweep_div_period[i]);
                        //Debug.printf("freq[%d]=%d > sfreq[%d]=%d\n",i,this.freq[i],i,this.sfreq[i]);

                        if (this.freq[i] >= 8 && this.sfreq[i] < 0x800 && this.sweepAmount[i] > 0) { // update frequency if appropriate
                            this.freq[i] = Math.max(this.sfreq[i], 0);
                            if (this.sCounter[i] > this.freq[i]) this.sCounter[i] = this.freq[i];
                        }
                        this.sweepDiv[i] = this.sweepDivPeriod[i] + 1;

                        //Debug.printf("freq[%d]=%d\n",i,this.freq[i]);
                    }

                    if (this.sweepWrite[i]) {
                        this.sweepDiv[i] = this.sweepDivPeriod[i] + 1;
                        this.sweepWrite[i] = false;
                    }
                }
            }

    }

    private static final short[][] sqrTbl = new short[][] {
            new short[] {0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            new short[] {0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            new short[] {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0},
            new short[] {1, 1, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}
    };

    private int calcSqr(int i, int clocks) {
        int ret = 0;

        this.sCounter[i] += clocks;
        while (this.sCounter[i] > this.freq[i]) {
            this.sPhase[i] = (this.sPhase[i] + 1) & 15;
            this.sCounter[i] -= (this.freq[i] + 1);
        }

        //int ret = 0;
        if (this.lengthCounter[i] > 0 &&
                this.freq[i] >= 8 &&
                this.sfreq[i] < 0x800
        ) {
            int v = this.envelopeDisable[i] ? this.volume[i] : this.envelopeCounter[i];
            ret = sqrTbl[this.duty[i]][this.sPhase[i]] != 0 ? v : 0;
        }

        return ret;
    }

    public boolean read(int adr, int[] val) {
        if (0x4000 <= adr && adr < 0x4008) {
            val[0] |= this.reg[adr & 0x7];
            return true;
        } else if (adr == 0x4015) {
            val[0] |= (this.lengthCounter[1] != 0 ? 2 : 0) | (this.lengthCounter[0] != 0 ? 1 : 0);
            return true;
        } else
            return false;
    }

    public void tick(int clocks) {
        this.out[0] = calcSqr(0, clocks);
        this.out[1] = calcSqr(1, clocks);
    }

    private int[] m = new int[2];

    // 生成される波形の振幅は0-8191
    public int render(int[] b) {
        this.tickCount.iup();
        tick((this.tickCount.value() - this.tickLast) & 0xff);
        this.tickLast = this.tickCount.value();

        this.out[0] = (this.mask & 1) != 0 ? 0 : this.out[0];
        this.out[1] = (this.mask & 2) != 0 ? 0 : this.out[1];

        if (this.option[OPT.NONLINEAR_MIXER.ordinal()] != 0) {
            int voltage;
            int _ref;

            voltage = this.squareTable[this.out[0] + this.out[1]];
            m[0] = this.out[0] << 6;
            m[1] = this.out[1] << 6;
            _ref = m[0] + m[1];
            if (_ref > 0) {
                m[0] = (m[0] * voltage) / _ref;
                m[1] = (m[1] * voltage) / _ref;
            } else {
                m[0] = voltage;
                m[1] = voltage;
            }
        } else {
            m[0] = this.out[0] << 6;
            m[1] = this.out[1] << 6;
        }

        // Shifting is (x-2) to match the volume of MAME's NES APU Sound core
        b[0] = m[0] * this.sm[0][0];
        b[0] += m[1] * this.sm[0][1];
        b[0] >>= 5; // was 7, but is now 8 for bipolar square

        b[1] = m[0] * this.sm[1][0];
        b[1] += m[1] * this.sm[1][1];
        b[1] >>= 5; // see above

        return 2;
    }

    public int renderOrg(int[] b) {
        this.out[0] = (this.mask & 1) != 0 ? 0 : this.out[0];
        this.out[1] = (this.mask & 2) != 0 ? 0 : this.out[1];

        if (this.option[OPT.NONLINEAR_MIXER.ordinal()] != 0) {
            int voltage;
            int _ref;

            voltage = this.squareTable[this.out[0] + this.out[1]];
            m[0] = this.out[0] << 6;
            m[1] = this.out[1] << 6;
            _ref = m[0] + m[1];
            if (_ref > 0) {
                m[0] = (m[0] * voltage) / _ref;
                m[1] = (m[1] * voltage) / _ref;
            } else {
                m[0] = voltage;
                m[1] = voltage;
            }
        } else {
            m[0] = this.out[0] << 6;
            m[1] = this.out[1] << 6;
        }

        // Shifting is (x-2) to match the volume of MAME's NES APU Sound core
        b[0] = m[0] * this.sm[0][0];
        b[0] += m[1] * this.sm[0][1];
        b[0] >>= 7 - 3; // was 7, but is now 8 for bipolar square

        b[1] = m[0] * this.sm[1][0];
        b[1] += m[1] * this.sm[1][1];
        b[1] >>= 7 - 3; // see above

        return 2;
    }

    public void reset() {
        int i;
        this.gclock = 0;
        this.mask = 0;

        this.sCounter[0] = 0;
        this.sCounter[1] = 0;
        this.sPhase[0] = 0;
        this.sPhase[0] = 0;

        this.sweepDiv[0] = 1;
        this.sweepDiv[1] = 1;
        this.envelopeDiv[0] = 0;
        this.envelopeDiv[1] = 0;
        this.lengthCounter[0] = 0;
        this.lengthCounter[1] = 0;
        this.envelopeCounter[0] = 0;
        this.envelopeCounter[1] = 0;

        for (i = 0x4000; i < 0x4008; i++)
            write(i, 0);

        write(0x4015, 0);
        if (this.option[OPT.UNMUTE_ON_RESET.ordinal()] != 0)
            write(0x4015, 0x0f);

        for (i = 0; i < 2; i++)
            this.out[i] = 0;

        setRate(this.rate);
    }

    public void setOption(int id, int val) {
        if (id < OPT.END.ordinal()) this.option[id] = val;
    }

    public void setClock(double c) {
        this.clock = c;
    }

    public void setRate(double r) {
        this.rate = r != 0 ? r : DEFAULT_RATE;

        this.tickCount.init(this.clock, this.rate);
        this.tickLast = 0;
    }

    public void setMask(int m) {
        this.mask = m;
    }

    public void setStereoMix(int trk, short mixl, short mixr) {
        if (trk < 0) return;
        if (trk > 1) return;
        this.sm[0][trk] = mixl;
        this.sm[1][trk] = mixr;
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
        int ch;

        if (0x4000 <= adr && adr < 0x4008) {
            //Debug.printf("$%04X = %02X\n",adr,val);

            adr &= 0xf;
            ch = adr >> 2;
            switch (adr) {
            case 0x0:
            case 0x4:
                this.volume[ch] = val & 15;
                this.envelopeDisable[ch] = ((val >> 4) & 1) != 0;
                this.envelopeLoop[ch] = ((val >> 5) & 1) != 0;
                this.envelopeDivPeriod[ch] = val & 15;
                this.duty[ch] = (val >> 6) & 3;
                if (this.option[OPT.DUTY_SWAP.ordinal()] != 0) {
                    if (this.duty[ch] == 1) this.duty[ch] = 2;
                    else if (this.duty[ch] == 2) this.duty[ch] = 1;
                }
                break;

            case 0x1:
            case 0x5:
                this.sweepEnable[ch] = ((val >> 7) & 1) != 0;
                this.sweepDivPeriod[ch] = ((val >> 4) & 7);
                this.sweepMode[ch] = ((val >> 3) & 1) != 0;
                this.sweepAmount[ch] = val & 7;
                this.sweepWrite[ch] = true;
                sweepSqr(ch);
                break;

            case 0x2:
            case 0x6:
                this.freq[ch] = val | (this.freq[ch] & 0x700);
                sweepSqr(ch);
                if (this.sCounter[ch] > this.freq[ch]) this.sCounter[ch] = this.freq[ch];
                break;

            case 0x3:
            case 0x7:
                this.freq[ch] = (this.freq[ch] & 0xff) | ((val & 0x7) << 8);

                if (this.option[OPT.PHASE_REFRESH.ordinal()] != 0)
                    this.sPhase[ch] = 0;
                this.envelopeWrite[ch] = true;
                if (this.enable[ch]) {
                    this.lengthCounter[ch] = lengthTable[(val >> 3) & 0x1f];
                }
                sweepSqr(ch);
                if (this.sCounter[ch] > this.freq[ch]) this.sCounter[ch] = this.freq[ch];
                break;

            default:
                return false;
            }
            this.reg[adr] = (byte) val;
            return true;
        } else if (adr == 0x4015) {
            this.enable[0] = (val & 1) != 0;
            this.enable[1] = (val & 2) != 0;

            if (!this.enable[0])
                this.lengthCounter[0] = 0;
            if (!this.enable[1])
                this.lengthCounter[1] = 0;

            this.reg[adr - 0x4000] = (byte) val;
            return true;
        }

        // 4017 is handled in NpNesDmc.c
        //else if (adr == 0x4017) {
        //}

        return false;
    }

    public NpNesApu(int clock, int rate) {
        this.setClock(clock);
        this.setRate(rate);
        this.option[OPT.UNMUTE_ON_RESET.ordinal()] = 1;
        this.option[OPT.PHASE_REFRESH.ordinal()] = 1;
        this.option[OPT.NONLINEAR_MIXER.ordinal()] = 1;
        this.option[OPT.DUTY_SWAP.ordinal()] = 0;

        this.squareTable[0] = 0;
        for (int i = 1; i < 32; i++)
            this.squareTable[i] = (int) ((8192.0 * 95.88) / (8128.0 / i + 100));

        for (int c = 0; c < 2; ++c)
            for (int t = 0; t < 2; ++t)
                this.sm[c][t] = 128;
    }
}

