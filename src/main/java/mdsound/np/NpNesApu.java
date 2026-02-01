/*
 MAME/MESS NES APU CORE

 Based on the Nofrendo/Nosefart NES N2A03 sound emulation core written by
 Matthew Conte (matt@conte.com) and redesigned for use in MAME/MESS by
 Who Wants to Know? (wwtk@mail.com)

 This core is written with the advise and consent of Matthew Conte and is
 released under the GNU Public License.  This core is freely avaiable for
 use in any freeware project, subject to the following terms:

 Any modifications to this code must be duly noted in the source and
 approved by Matthew Conte and myself prior to public submission.

 timing notes:
 master = 21477270
 2A03 clock = master/12
 sequencer = master/89490 or CPU/7457

 ---

 NES_APU.C

 Actual NES APU interface.

 LAST MODIFIED 02/29/2004

 - Based on Matthew Conte's Nofrendo/Nosefart core and redesigned to
 use MAME system calls and to enable multiple APUs.  Sound at this
 point should be just about 100% accurate, though I cannot tell for
 certain as yet.

 A queue interface is also available for additional speed.  However,
 the implementation is not yet 100% (DPCM sounds are inaccurate),
 so it is disabled by default.

 ---

 BUGFIXES:

 - Various bugs concerning the DPCM channel fixed. (Oliver Achten)
 - Fixed $4015 read behaviour. (Oliver Achten)
 */

package mdsound.np;

import mdsound.np.Device.Counter;


/**
 * NES 2A03
 *
 * @version Ported from NSFPlay 2.2 to VGMPlay (including C++ -> C conversion) by Valley Bell on 24 September 2013<br/>
 *          Updated to NSFPlay 2.3 on 26 September 2013
 * @author Matthew Conte
 * @author Valley Bell
 */
public class NpNesApu {

    // Master Clock: 21477272 (NTSC)
    // APU Clock = Master Clock / 12
    public static final double DEFAULT_CLOCK = 1789772.0; // not sure if this shouldn't be 1789772,667 instead
    public static final int DEFAULT_RATE = 44100;

    /**
     * Upper half of APU
     */
    public enum OPT {
        UNMUTE_ON_RESET,
        NONLINEAR_MIXER,
        PHASE_REFRESH,
        DUTY_SWAP;
        static final int END = values().length;
    }

    enum SQR {
        DUMMY,
        SQR0_MASK,
        SQR1_MASK,
    }

    // Various options
    private final int[] option = new int[OPT.END];
    private int mask;
    public final int[][] sm = {new int[2], new int[2]};

    private int gClock;
    public final int[] reg = new int[0x20];
    private final int[] out = new int[2];
    private double rate, clock;

    // nonlinear mixer
    public final int[] squareTable = new int[32];

    // frequency divider
    private final int[] sCounter = new int[2];
    // phase counter
    private final int[] sPhase = new int[2];

    private final int[] duty = new int[2];
    private final int[] volume = new int[2];
    private final int[] freq = new int[2];
    private final int[] sFreq = new int[2];

    private final boolean[] sweepEnable = new boolean[2];
    private final boolean[] sweepMode = new boolean[2];
    private final boolean[] sweepWrite = new boolean[2];
    private final int[] sweepDivPeriod = new int[2];
    private final int[] sweepDiv = new int[2];
    private final int[] sweepAmount = new int[2];

    private final boolean[] envelopeDisable = new boolean[2];
    private final boolean[] envelopeLoop = new boolean[2];
    private final boolean[] envelopeWrite = new boolean[2];
    private final int[] envelopeDivPeriod = new int[2];
    private final int[] envelopeDiv = new int[2];
    private final int[] envelopeCounter = new int[2];

    private final int[] lengthCounter = new int[2];

    private final boolean[] enable = new boolean[2];

    private final Counter tickCount = new Counter();
    private long tickLast;

    private void sweepSqr(int i) {
        int shifted = this.freq[i] >> this.sweepAmount[i];
        if (i == 0 && this.sweepMode[i]) shifted += 1;
        this.sFreq[i] = this.freq[i] + (this.sweepMode[i] ? -shifted : shifted);
//logger.log(Level.TRACE, "shifted[%d] = %d (%d >> %d)".formatted(i, shifted, this.freq[i], this.sweep_amount[i]));
    }

    public void sequenceFrame(int s) {
//logger.log(Level.TRACE, "sequenceFrame(%d)".formatted(s));

        if (s > 3) return; // no operation in step 4

        // 240hz clock
        for (int i = 0; i < 2; ++i) {
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
            for (int i = 0; i < 2; ++i) {
                if (!this.envelopeLoop[i] && (this.lengthCounter[i] > 0))
                    --this.lengthCounter[i];

                if (this.sweepEnable[i]) {
//logger.log(Level.TRACE, "Clock sweep: %d".formatted(i));

                    --this.sweepDiv[i];
                    if (this.sweepDiv[i] <= 0) {
                        sweepSqr(i); // calculate new sweep target

//logger.log(Level.TRACE, "sweep_div[%d] (0/%d)".formatted(i, this.sweep_div_period[i]));
//logger.log(Level.TRACE, "freq[%d]=%d > sFreq[%d]=%d".formatted(i, this.freq[i], i, this.sFreq[i]));

                        if (this.freq[i] >= 8 && this.sFreq[i] < 0x800 && this.sweepAmount[i] > 0) { // update frequency if appropriate
                            this.freq[i] = Math.max(this.sFreq[i], 0);
                            if (this.sCounter[i] > this.freq[i]) this.sCounter[i] = this.freq[i];
                        }
                        this.sweepDiv[i] = this.sweepDivPeriod[i] + 1;

//logger.log(Level.TRACE, "freq[%d]=%d".formatted(i, this.freq[i]));
                    }

                    if (this.sweepWrite[i]) {
                        this.sweepDiv[i] = this.sweepDivPeriod[i] + 1;
                        this.sweepWrite[i] = false;
                    }
                }
            }
    }

    private static final int[][] sqrTbl = {
            {0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0},
            {1, 1, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}
    };

    private int calcSqr(int i, long clocks) {
        this.sCounter[i] += clocks;
        while (this.sCounter[i] > this.freq[i]) {
            this.sPhase[i] = (this.sPhase[i] + 1) & 15;
            this.sCounter[i] -= (this.freq[i] + 1);
        }

        int ret = 0;
        if (this.lengthCounter[i] > 0 &&
                this.freq[i] >= 8 &&
                this.sFreq[i] < 0x800
        ) {
            int v = this.envelopeDisable[i] ? this.volume[i] : this.envelopeCounter[i];
            ret = sqrTbl[this.duty[i]][this.sPhase[i]] != 0 ? v : 0;
        }

        return ret;
    }

    public boolean read(int adr, /* ref */ int[] val) {
        if (0x4000 <= adr && adr < 0x4008) {
            val[0] |= this.reg[adr & 0x7];
            return true;
        } else if (adr == 0x4015) {
            val[0] |= (this.lengthCounter[1] != 0 ? 2 : 0) | (this.lengthCounter[0] != 0 ? 1 : 0);
            return true;
        } else
            return false;
    }

    public void tick(long clocks) {
        this.out[0] = calcSqr(0, clocks);
        this.out[1] = calcSqr(1, clocks);
    }

    private final int[] m = new int[2];

    // The amplitude of the generated waveform is 0-8191.
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

    // w/o tick
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
//logger.log(Level.TRACE, "b[0] = " + b[0] + ", b[1] = " + b[1]);
        return 2;
    }

    public void reset() {
        this.gClock = 0;
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

        for (int i = 0x4000; i < 0x4008; i++)
            write(i, 0);

        write(0x4015, 0);
        if (this.option[OPT.UNMUTE_ON_RESET.ordinal()] != 0)
            write(0x4015, 0x0f);

        for (int i = 0; i < 2; i++)
            this.out[i] = 0;

        setRate(this.rate);
    }

    public void setOption(int id, int val) {
        if (id < OPT.END) this.option[id] = val;
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

    public void setStereoMix(int trk, int mixL, int mixR) {
        if (trk < 0) return;
        if (trk > 1) return;
        this.sm[0][trk] = mixL;
        this.sm[1][trk] = mixR;
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

        if (0x4000 <= adr && adr < 0x4008) {
//logger.log(Level.TRACE, "$%04X = %02X".formatted(adr, val));

            adr &= 0xf;
            int ch = adr >> 2;
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
            this.reg[adr] = val;
            return true;
        } else if (adr == 0x4015) {
            this.enable[0] = (val & 1) != 0;
            this.enable[1] = (val & 2) != 0;

            if (!this.enable[0])
                this.lengthCounter[0] = 0;
            if (!this.enable[1])
                this.lengthCounter[1] = 0;

            this.reg[adr - 0x4000] = val;
            return true;
        }

        // 4017 is handled in NpNesDmc.c
        //else if (adr == 0x4017) {
        //}

        return false;
    }

    public void init(int clock, int rate) {
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
