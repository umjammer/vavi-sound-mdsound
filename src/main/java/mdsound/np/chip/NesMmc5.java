package mdsound.np.chip;

import mdsound.MDSound;
import mdsound.np.Device.SoundChip;
import mdsound.np.chip.DeviceInfo.TrackInfo;
import mdsound.np.chip.DeviceInfo.BasicTrackInfo;
import mdsound.np.cpu.Km6502;


public class NesMmc5 implements SoundChip {
    public final double DEFAULT_CLOCK = 1789772.0;
    public final int DEFAULT_RATE = 44100;

    public enum OPT {
        NONLINEAR_MIXER, PHASE_REFRESH, END
    }

    protected int[] option = new int[(int) OPT.END.ordinal()];
    protected int mask;
    protected int[][] sm = new int[][] {new int[3], new int[3]}; // stereo panning
    protected byte[] ram = new byte[0x6000 - 0x5c00];
    protected byte[] reg = new byte[8];
    protected byte[] mReg = new byte[2];
    // PCM channel
    public byte pcm;
    // PCM channel
    public boolean pcmMode;
    // PCM channel reads need CPU access
    protected Km6502 cpu;

    // frequency divider
    protected int[] sCounter = new int[2];
    // phase counter
    protected int[] sPhase = new int[2];

    protected int[] duty = new int[2];
    protected int[] volume = new int[2];
    protected int[] freq = new int[2];
    protected int[] out = new int[3];
    protected boolean[] enable = new boolean[2];

    // エンベロープ有効フラグ
    protected boolean[] envelopeDisable = new boolean[2];
    // エンベロープループ
    protected boolean[] envelopeLoop = new boolean[2];
    protected boolean[] envelopeWrite = new boolean[2];
    protected int[] envelopeDivPeriod = new int[2];
    protected int[] envelopeDiv = new int[2];
    protected int[] envelopeCounter = new int[2];

    protected int[] lengthCounter = new int[2];

    protected int frameSequenceCount;

    protected double clock, rate;
    protected int[] squareTable = new int[32];
    protected int[] pcmTable = new int[256];
    protected BasicTrackInfo[] trkInfo = new BasicTrackInfo[3];

    public NesMmc5() {
        cpu = null;
        setClock(DEFAULT_CLOCK);
        setRate(DEFAULT_RATE);
        option[OPT.NONLINEAR_MIXER.ordinal()] = 1; // true;
        option[OPT.PHASE_REFRESH.ordinal()] = 1; // true;
        frameSequenceCount = 0;

        // square nonlinear mix, same as 2A03
        squareTable[0] = 0;
        for (int i = 1; i < 32; i++)
            squareTable[i] = (int) ((8192.0 * 95.88) / (8128.0 / i + 100));

        // 2A03 style nonlinear pcm mix with double the bits
        //pcm_table[0] = 0;
        //int wd = 22638;
        //for(int d=1;d<256; ++d)
        //    pcm_table[d] = (int)((8192.0*159.79)/(100.0+1.0/((double)d/wd)));

        // linear pcm mix (actual hardware seems closer to this)
        pcmTable[0] = 0;
        double pcm_scale = 32.0;
        for (int d = 1; d < 256; ++d)
            pcmTable[d] = (int) ((double) (d) * pcm_scale);

        // stereo mix
        for (int c = 0; c < 2; ++c)
            for (int t = 0; t < 3; ++t)
                sm[c][t] = 128;
    }

    @Override
    public void reset() {
        int i;

        sCounter[0] = 0;
        sCounter[1] = 0;
        sPhase[0] = 0;
        sPhase[1] = 0;

        envelopeDiv[0] = 0;
        envelopeDiv[1] = 0;
        lengthCounter[0] = 0;
        lengthCounter[1] = 0;
        envelopeCounter[0] = 0;
        envelopeCounter[1] = 0;
        frameSequenceCount = 0;

        for (i = 0; i < 8; i++)
            write(0x5000 + i, 0);

        write(0x5015, 0);

        for (i = 0; i < 3; ++i) out[i] = 0;

        mask = 0;
        pcm = 0; // PCM channel
        pcmMode = false; // write mode

        setRate(rate);
    }

    @Override
    public void setOption(int id, int val) {
        if (id < OPT.END.ordinal()) option[id] = val;
    }

    @Override
    public void setClock(double c) {
        this.clock = c;
    }

    @Override
    public void setRate(double r) {
        rate = r != 0 ? r : DEFAULT_RATE;
    }

    public void frameSequence() {
        // 240hz clock
        for (int i = 0; i < 2; ++i) {
            boolean divider = false;
            if (envelopeWrite[i]) {
                envelopeWrite[i] = false;
                envelopeCounter[i] = 15;
                envelopeDiv[i] = 0;
            } else {
                ++envelopeDiv[i];
                if (envelopeDiv[i] > envelopeDivPeriod[i]) {
                    divider = true;
                    envelopeDiv[i] = 0;
                }
            }
            if (divider) {
                if (envelopeLoop[i] && envelopeCounter[i] == 0)
                    envelopeCounter[i] = 15;
                else if (envelopeCounter[i] > 0)
                    --envelopeCounter[i];
            }
        }

        // MMC5 length counter is clocked at 240hz, unlike 2A03
        for (int i = 0; i < 2; ++i) {
            if (!envelopeLoop[i] && (lengthCounter[i] > 0))
                --lengthCounter[i];
        }
    }

    private static final short[][] sqrTbl = new short[][] {
            new short[] {0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            new short[] {0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            new short[] {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0},
            new short[] {1, 1, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}
    };

    private int calcSqr(int i, int clocks) {

        sCounter[i] += clocks;
        while (sCounter[i] > freq[i]) {
            sPhase[i] = (sPhase[i] + 1) & 15;
            sCounter[i] -= (freq[i] + 1);
        }

        int ret = 0;
        if (lengthCounter[i] > 0) {
            // note MMC5 does not silence the highest 8 frequencies like APU,
            // because this is done by the sweep unit.

            int v = envelopeDisable[i] ? volume[i] : envelopeCounter[i];
            ret = sqrTbl[duty[i]][sPhase[i]] != 0 ? v : 0;
        }

        return ret;
    }

    public void tickFrameSequence(int clocks) {
        frameSequenceCount += clocks;
        while (frameSequenceCount > 7458) {
            frameSequence();
            frameSequenceCount -= 7458;
        }
    }

    @Override
    public void tick(int clocks) {
        out[0] = calcSqr(0, clocks);
        out[1] = calcSqr(1, clocks);
        out[2] = pcm;
    }

    private int[] m = new int[3];

    @Override
    public int render(int[] b) {
        out[0] = (mask & 1) != 0 ? 0 : out[0];
        out[1] = (mask & 2) != 0 ? 0 : out[1];
        out[2] = (mask & 4) != 0 ? 0 : out[2];

        if (option[OPT.NONLINEAR_MIXER.ordinal()] != 0) {
            // squares nonlinear
            int voltage = squareTable[out[0] + out[1]];
            m[0] = out[0] << 6;
            m[1] = out[1] << 6;
            int _ref = m[0] + m[1];
            if (_ref > 0) {
                m[0] = (m[0] * voltage) / _ref;
                m[1] = (m[1] * voltage) / _ref;
            } else {
                m[0] = voltage;
                m[1] = voltage;
            }

            // pcm nonlinear
            m[2] = pcmTable[out[2]];
        } else {
            // squares
            m[0] = out[0] << 6;
            m[1] = out[1] << 6;

            // pcm channel
            m[2] = out[2] << 5;
        }

        // note polarity is flipped on output

        b[0] = m[0] * -sm[0][0];
        b[0] += m[1] * -sm[0][1];
        b[0] += m[2] * -sm[0][2];
        b[0] >>= 7;

        b[1] = m[0] * -sm[1][0];
        b[1] += m[1] * -sm[1][1];
        b[1] += m[2] * -sm[1][2];
        b[1] >>= 7;

        MDSound.np_nes_mmc5_volume = Math.abs(b[0]);

        return 2;
    }

    public static final byte[] length_table = new byte[] {
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

    @Override
    public boolean write(int adr, int val, int id /*= 0*/) {
        int ch;

        if ((0x5c00 <= adr) && (adr < 0x5ff0)) {
            ram[adr & 0x3ff] = (byte) val;
            return true;
        } else if ((0x5000 <= adr) && (adr < 0x5008)) {
            reg[adr & 0x7] = (byte) val;
        }

        switch (adr) {
        case 0x5000:
        case 0x5004:
            ch = (adr >> 2) & 1;
            volume[ch] = val & 15;
            envelopeDisable[ch] = ((val >> 4) & 1) != 0;
            envelopeLoop[ch] = ((val >> 5) & 1) != 0;
            envelopeDivPeriod[ch] = (val & 15);
            duty[ch] = (val >> 6) & 3;
            break;

        case 0x5002:
        case 0x5006:
            ch = (adr >> 2) & 1;
            freq[ch] = val + (freq[ch] & 0x700);
            if (sCounter[ch] > freq[ch]) sCounter[ch] = freq[ch];
            break;

        case 0x5003:
        case 0x5007:
            ch = (adr >> 2) & 1;
            freq[ch] = (freq[ch] & 0xff) + ((val & 7) << 8);
            if (sCounter[ch] > freq[ch]) sCounter[ch] = freq[ch];
            // phase reset
            if (option[OPT.PHASE_REFRESH.ordinal()] != 0)
                sPhase[ch] = 0;
            envelopeWrite[ch] = true;
            if (enable[ch]) {
                lengthCounter[ch] = length_table[(val >> 3) & 0x1f];
            }
            break;

        // PCM channel control
        case 0x5010:
            pcmMode = ((val & 1) != 0); // 0 = write, 1 = read
            break;

        // PCM channel control
        case 0x5011:
            if (!pcmMode) {
                val &= 0xFF;
                if (val != 0) pcm = (byte) val;
            }
            break;

        case 0x5015:
            enable[0] = (val & 1) != 0;
            enable[1] = (val & 2) != 0;
            if (!enable[0])
                lengthCounter[0] = 0;
            if (!enable[1])
                lengthCounter[1] = 0;
            break;

        case 0x5205:
            mReg[0] = (byte) val;
            break;

        case 0x5206:
            mReg[1] = (byte) val;
            break;

        default:
            return false;

        }
        return true;
    }

    @Override
    public boolean read(int adr, int[] val, int id/* = 0*/) {
        // in PCM read mode, reads from $8000-$C000 automatically load the PCM output
        if (pcmMode && (0x8000 <= adr) && (adr < 0xC000) && cpu != null) {
            pcmMode = false; // prevent recursive entry
            int[] pcm_read = new int[] { 0 };
            cpu.read(adr, pcm_read, id);
            pcm_read[0] &= 0xFF;
            if (pcm_read[0] != 0)
                pcm = (byte) pcm_read[0];
            pcmMode = true;
        }

        if ((0x5000 <= adr) && (adr < 0x5008)) {
            val[0] = reg[adr & 0x7];
            return true;
        } else if (adr == 0x5015) {
            val[0] = (enable[1] ? 2 : 0) | (enable[0] ? 1 : 0);
            return true;
        }

        if ((0x5c00 <= adr) && (adr < 0x5ff0)) {
            val[0] = ram[adr & 0x3ff];
            return true;
        } else if (adr == 0x5205) {
            val[0] = (mReg[0] * mReg[1]) & 0xff;
            return true;
        } else if (adr == 0x5206) {
            val[0] = (mReg[0] * mReg[1]) >> 8;
            return true;
        }

        return false;
    }

    @Override
    public void setStereoMix(int trk, short mixL, short mixR) {
        if (trk < 0) return;
        if (trk > 2) return;
        sm[0][trk] = mixL;
        sm[1][trk] = mixR;
    }

    public TrackInfo getTrackInfo(int trk) {
        //assert(trk < 3);

        if (trk < 2) { // square
            trkInfo[trk]._freq = freq[trk];
            if (freq[trk] != 0)
                trkInfo[trk].freq = clock / 16 / (freq[trk] + 1);
            else
                trkInfo[trk].freq = 0;

            trkInfo[trk].output = out[trk];
            trkInfo[trk].maxVolume = 15;
            trkInfo[trk].volume = volume[trk] + (envelopeDisable[trk] ? 0 : 0x10);
            trkInfo[trk].key = (envelopeDisable[trk] ? (volume[trk] > 0) : (envelopeCounter[trk] > 0));
            trkInfo[trk].tone = duty[trk];
        } else { // pcm
            trkInfo[trk]._freq = 0;
            trkInfo[trk].freq = 0;
            trkInfo[trk].output = out[2];
            trkInfo[trk].maxVolume = 255;
            trkInfo[trk].volume = pcm;
            trkInfo[trk].key = false;
            trkInfo[trk].tone = pcmMode ? 1 : 0;
        }

        return trkInfo[trk];
    }

    // pcm read mode requires CPU read access
    public void setCPU(Km6502 cpu) {
        this.cpu = cpu;
    }

    @Override
    public void setMask(int mask) {
        this.mask = mask;
    }
}

