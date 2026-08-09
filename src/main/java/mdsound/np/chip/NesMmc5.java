/*
 * NSFPlay/NFSPlug project by Brezza.
 *
 * https://web.archive.org/web/20160301201825/http://www.pokipoki.org/dsa/
 */

package mdsound.np.chip;

import java.util.function.Consumer;

import mdsound.np.Device.SoundChip;
import mdsound.np.chip.DeviceInfo.TrackInfo;
import mdsound.np.chip.DeviceInfo.BasicTrackInfo;
import mdsound.np.cpu.Km6502;


public class NesMmc5 implements SoundChip {

    private static final double DEFAULT_CLOCK = 1789772.0;
    private static final int DEFAULT_RATE = 44100;

    public enum OPT {
        NONLINEAR_MIXER, PHASE_REFRESH, END
    }

    private final int[] option = new int[OPT.END.ordinal()];
    private int mask;
    private final int[][] sm = {new int[3], new int[3]}; // stereo panning
    private final byte[] ram = new byte[0x6000 - 0x5c00];
    private final byte[] reg = new byte[8];
    private final int[] mReg = new int[2];
    /** PCM channel */
    public int pcm;
    /** PCM channel */
    public boolean pcmMode;
    /** PCM channel reads need CPU access */
    private Km6502 cpu;

    /** frequency divider */
    private final int[] sCounter = new int[2];
    /** phase counter */
    private final int[] sPhase = new int[2];

    private final int[] duty = new int[2];
    private final int[] volume = new int[2];
    private final int[] freq = new int[2];
    private final int[] out = new int[3];
    private final boolean[] enable = new boolean[2];

    // Envelope Enabled Flag
    private final boolean[] envelopeDisable = new boolean[2];
    // Envelope Loop
    private final boolean[] envelopeLoop = new boolean[2];
    private final boolean[] envelopeWrite = new boolean[2];
    private final int[] envelopeDivPeriod = new int[2];
    private final int[] envelopeDiv = new int[2];
    private final int[] envelopeCounter = new int[2];

    private final int[] lengthCounter = new int[2];

    private int frameSequenceCount;

    private double clock;
    private double rate;
    private final int[] squareTable = new int[32];
    private final int[] pcmTable = new int[256];
    private final BasicTrackInfo[] trackInfo = new BasicTrackInfo[3];

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

//        // 2A03 style nonlinear pcm mix with double the bits
//        pcm_table[0] = 0;
//        int wd = 22638;
//        for (int d = 1; d < 256; ++d)
//            pcm_table[d] = (int) ((8192.0 * 159.79) / (100.0 + 1.0 / ((double) d / wd)));

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

        for (int i = 0; i < 8; i++)
            write(0x5000 + i, 0);

        write(0x5015, 0);

        for (int i = 0; i < 3; ++i) out[i] = 0;

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

    private void frameSequence() {
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

    private static final int[][] sqrTbl = {
            {0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0},
            {1, 1, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}
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

    private final int[] m = new int[3];

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

        if (listener != null) listener.accept(new int[] {-1, -1, -1, -1, Math.abs(b[0]), -1, -1, -1});

        return 2;
    }

    private static final int[] length_table = {
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

    @Override
    public boolean write(int adr, int val, int id /* = 0 */) {
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
                val &= 0xff;
                if (val != 0) pcm = val;
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
            mReg[0] = val;
            break;

        case 0x5206:
            mReg[1] = val;
            break;

        default:
            return false;

        }
        return true;
    }

    @Override
    public boolean read(int adr, /* ref */ int[] val, int id /* = 0 */) {
        // in PCM read mode, reads from $8000-$C000 automatically load the PCM output
        if (pcmMode && (0x8000 <= adr) && (adr < 0xC000) && cpu != null) {
            pcmMode = false; // prevent recursive entry
            int[] pcm_read = {0};
            cpu.read(adr, pcm_read, id);
            if (pcm_read[0] != 0)
                pcm = pcm_read[0];
            pcmMode = true;
        }

        if ((0x5000 <= adr) && (adr < 0x5008)) {
            val[0] = reg[adr & 0x7] & 0xff;
            return true;
        } else if (adr == 0x5015) {
            val[0] = (enable[1] ? 2 : 0) | (enable[0] ? 1 : 0);
            return true;
        }

        if ((0x5c00 <= adr) && (adr < 0x5ff0)) {
            val[0] = ram[adr & 0x3ff] & 0xff;
            return true;
        } else if (adr == 0x5205) {
            val[0] = (mReg[0] * mReg[1]) & 0xff;
            return true;
        } else if (adr == 0x5206) {
            val[0] = ((mReg[0] * mReg[1]) & 0xff00) >> 8;
            return true;
        }

        return false;
    }

    @Override
    public void setStereoMix(int track, int mixL, int mixR) {
        if (track < 0) return;
        if (track > 2) return;
        sm[0][track] = mixL;
        sm[1][track] = mixR;
    }

    public TrackInfo getTrackInfo(int track) {
        assert track < 3;

        if (track < 2) { // square
            trackInfo[track]._freq = freq[track];
            if (freq[track] != 0)
                trackInfo[track].freq = clock / 16 / (freq[track] + 1);
            else
                trackInfo[track].freq = 0;

            trackInfo[track].output = out[track];
            trackInfo[track].maxVolume = 15;
            trackInfo[track].volume = volume[track] + (envelopeDisable[track] ? 0 : 0x10);
            trackInfo[track].key = (envelopeDisable[track] ? (volume[track] > 0) : (envelopeCounter[track] > 0));
            trackInfo[track].tone = duty[track];
        } else { // pcm
            trackInfo[track]._freq = 0;
            trackInfo[track].freq = 0;
            trackInfo[track].output = out[2];
            trackInfo[track].maxVolume = 255;
            trackInfo[track].volume = pcm;
            trackInfo[track].key = false;
            trackInfo[track].tone = pcmMode ? 1 : 0;
        }

        return trackInfo[track];
    }

    /** pcm read mode requires CPU read access */
    public void setCPU(Km6502 cpu) {
        this.cpu = cpu;
    }

    @Override
    public void setMask(int mask) {
        this.mask = mask;
    }

    private Consumer<int[]> listener;

    @Override
    public void setListener(Consumer<int[]> listener) {
        this.listener = listener;
    }
}
