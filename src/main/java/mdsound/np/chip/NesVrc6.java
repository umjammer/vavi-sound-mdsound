package mdsound.np.chip;

import java.util.function.Consumer;

import mdsound.np.Device.SoundChip;
import mdsound.np.chip.DeviceInfo.TrackInfo;
import mdsound.np.chip.DeviceInfo.BasicTrackInfo;


public class NesVrc6 implements SoundChip {
    public static final double DEFAULT_CLOCK = 1789772.0;
    public static final int DEFAULT_RATE = 44100;

    // frequency divider
    protected int[] counter = new int[3];
    // phase counter
    protected int[] phase = new int[3];
    // adjusted frequency
    protected int[] freq2 = new int[3];
    // saw 14-stage counter
    protected int count14;

    protected int mask;
    // stereo mix
    protected int[][] sm = new int[][] {new int[3], new int[3]};
    protected int[] duty = new int[2];
    protected int[] volume = new int[3];
    protected int[] enable = new int[3];
    protected int[] gate = new int[3];
    protected int[] freq = new int[3];
    protected boolean halt;
    protected int freqShift;
    public double clock, rate;
    protected int[] out = new int[3];
    protected BasicTrackInfo[] trkInfo = new BasicTrackInfo[3];


    public NesVrc6() {
        setClock(DEFAULT_CLOCK);
        setRate(DEFAULT_RATE);

        halt = false;
        freqShift = 0;

        for (int c = 0; c < 2; ++c) {
            for (int t = 0; t < 3; ++t) {
                sm[c][t] = 128;
            }
        }

        for (int t = 0; t < 3; ++t) {
            trkInfo[t] = new BasicTrackInfo();
        }
    }

    @Override
    public void setStereoMix(int trk, short mixl, short mixr) {
        if (trk < 0) return;
        if (trk > 2) return;
        sm[0][trk] = mixl;
        sm[1][trk] = mixr;
    }

    public DeviceInfo.TrackInfo getTrackInfo(int trk) {
        if (trk < 2) {
            trkInfo[trk].maxVolume = 15;
            trkInfo[trk].volume = volume[trk];
            trkInfo[trk]._freq = freq2[trk];
            trkInfo[trk].freq = freq2[trk] != 0 ? clock / 16 / (freq2[trk] + 1) : 0;
            trkInfo[trk].tone = duty[trk];
            trkInfo[trk].key = (volume[trk] > 0) && enable[trk] != 0 && gate[trk] == 0;
            trkInfo[trk].freqP = freq[trk];
            return trkInfo[trk];
        } else if (trk == 2) {
            trkInfo[2].maxVolume = 255;
            trkInfo[2].volume = volume[2];
            trkInfo[2]._freq = freq2[2];
            trkInfo[2].freq = freq2[2] != 0 ? clock / 14 / (freq2[2] + 1) : 0;
            trkInfo[2].tone = -1;
            trkInfo[2].key = (enable[2] > 0);
            trkInfo[2].halt = halt;
            trkInfo[2].freqShift = freqShift;
            trkInfo[2].freqP = freq[2];
            return trkInfo[2];
        } else
            return null;
    }

    public TrackInfo[] getTracksInfo() {
        try {
            for (int i = 0; i < 3; i++) {
                getTrackInfo(i);
            }

            return trkInfo;
        } catch (Exception e) {
e.printStackTrace();
            return null;
        }
    }

    @Override
    public void setClock(double c) {
        clock = c;
    }

    @Override
    public void setRate(double r) {
        rate = r != 0 ? r : DEFAULT_RATE;
    }

    @Override
    public void reset() {
        write(0x9003, 0);
        for (int i = 0; i < 3; i++) {
            write(0x9000 + i, 0);
            write(0xa000 + i, 0);
            write(0xb000 + i, 0);
        }
        count14 = 0;
        mask = 0;
        counter[0] = 0;
        counter[1] = 0;
        counter[2] = 0;
        phase[0] = 0;
        phase[0] = 1;
        phase[0] = 2;
    }

    static final short[][] sqrTbl = new short[][] {
            new short[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
            new short[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1},
            new short[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1},
            new short[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1},
            new short[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1},
            new short[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1},
            new short[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1},
            new short[] {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1}
    };

    private short calcSqr(int i, int clocks) {
        if (enable[i] == 0)
            return 0;

        if (!halt) {
            counter[i] += clocks;
            while (counter[i] > freq2[i]) {
                phase[i] = (phase[i] + 1) & 15;
                counter[i] -= (freq2[i] + 1);
            }
        }

        return (short) ((gate[i] != 0
                || sqrTbl[duty[i]][phase[i]] != 0) ? volume[i] : 0);
    }

    private short calcSaw(int clocks) {
        if (enable[2] == 0)
            return 0;

        if (!halt) {
            counter[2] += clocks;
            while (counter[2] > freq2[2]) {
                counter[2] -= (freq2[2] + 1);

                // accumulate saw
                ++count14;
                if (count14 >= 14) {
                    count14 = 0;
                    phase[2] = 0;
                } else if (0 == (count14 & 1)) { // only accumulate on even ticks
                    phase[2] = (phase[2] + volume[2]) & 0xff; // note 8-bit wrapping behaviour
                }
            }
        }

        // only top 5 bits of saw are output
        return (short) (phase[2] >> 3);
    }

    @Override
    public void tick(int clocks) {
        out[0] = calcSqr(0, clocks);
        out[1] = calcSqr(1, clocks);
        out[2] = calcSaw(clocks);
    }

    private int[] m = new int[3];

    @Override
    public int render(int[] b) {
        m[0] = out[0];
        m[1] = out[1];
        m[2] = out[2];

        // note: signal is inverted compared to 2A03

        m[0] = (mask & 1) != 0 ? 0 : -m[0];
        m[1] = (mask & 2) != 0 ? 0 : -m[1];
        m[2] = (mask & 4) != 0 ? 0 : -m[2];

        b[0] = m[0] * sm[0][0];
        b[0] += m[1] * sm[0][1];
        b[0] += m[2] * sm[0][2];
        // b[0] >>= (7 - 7);

        b[1] = m[0] * sm[1][0];
        b[1] += m[1] * sm[1][1];
        b[1] += m[2] * sm[1][2];
        // b[1] >>= (7 - 7);

        // master volume adjustment
        final int MASTER = (int) (256.0 * 1223.0 / 1920.0);
        b[0] = (b[0] * MASTER) >> 8;
        b[1] = (b[1] * MASTER) >> 8;

        if (listener != null) listener.accept(new int[] {-1, -1, -1, -1, -1, -1, Math.abs(b[0]), -1});

        return 2;
    }

    private static final int[] cmap = new int[] {0, 0, 1, 2};

    @Override
    public boolean write(int adr, int val, int id/*=0*/) {
        int ch;

        switch (adr) {
        case 0x9000:
        case 0xa000:
            ch = cmap[(adr >> 12) & 3];
            volume[ch] = val & 15;
            duty[ch] = (val >> 4) & 7;
            gate[ch] = (val >> 7) & 1;
            break;
        case 0xb000:
            volume[2] = val & 63;
            break;

        case 0x9001:
        case 0xa001:
        case 0xb001:
            ch = cmap[(adr >> 12) & 3];
            freq[ch] = (freq[ch] & 0xf00) | val;
            freq2[ch] = (freq[ch] >> freqShift);
            if (counter[ch] > freq2[ch]) counter[ch] = freq2[ch];
            break;

        case 0x9002:
        case 0xa002:
        case 0xb002:
            ch = cmap[(adr >> 12) & 3];
            freq[ch] = ((val & 0xf) << 8) + (freq[ch] & 0xff);
            freq2[ch] = (freq[ch] >> freqShift);
            if (counter[ch] > freq2[ch]) counter[ch] = freq2[ch];
            if (enable[ch] == 0) { // if enable is being turned on, phase should be reset
                if (ch == 2) {
                    count14 = 0; // reset saw
                }
                phase[ch] = 0;
            }
            enable[ch] = (val >> 7) & 1;
            break;

        case 0x9003:
            halt = (val & 1) != 0;
            freqShift = (val & 4) != 0 ? 8 :
                    (val & 2) != 0 ? 4 :
                            0;
            freq2[0] = (freq[0] >> freqShift);
            freq2[1] = (freq[1] >> freqShift);
            freq2[2] = (freq[2] >> freqShift);
            if (counter[0] > freq2[0]) counter[0] = freq2[0];
            if (counter[1] > freq2[1]) counter[1] = freq2[1];
            if (counter[2] > freq2[2]) counter[2] = freq2[2];
            break;

        default:
            return false;
        }

        return true;
    }


    @Override
    public boolean read(int adr, int[] val, int id/* = 0*/) {
        return false;
    }

    @Override
    public void setMask(int m) {
        mask = m;
    }

    @Override
    public void setOption(int id, int val) {
        throw new UnsupportedOperationException();
    }

    private Consumer<int[]> listener;

    public void setListener(Consumer<int[]> listener) {
        this.listener = listener;
    }
}

