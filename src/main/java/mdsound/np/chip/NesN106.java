package mdsound.np.chip;

import java.util.Arrays;
import java.util.function.Consumer;

import mdsound.np.Device.SoundChip;
import mdsound.np.chip.DeviceInfo.BasicTrackInfo;


public class NesN106 implements SoundChip {

    public static class TrackInfo extends BasicTrackInfo {
        public int wavelen;
        public short[] wave = new short[256];

        public DeviceInfo clone() {
            NesN106.TrackInfo ti = new NesN106.TrackInfo();
            ti.output = output;
            ti.volume = volume;
            ti.maxVolume = maxVolume;
            ti._freq = _freq;
            ti.freq = freq;
            ti.key = key;
            ti.tone = tone;
            ti.wavelen = wavelen;
            ti.wave = new short[256];
            System.arraycopy(wave, 0, ti.wave, 0, ti.wave.length);
            return ti;
        }
    }

    public static final double DEFAULT_CLOCK = 1789772.0;
    public static final int DEFAULT_RATE = 44100;

    public enum OPT {
        SERIAL,
        END
    }

    protected double rate, clock;
    protected int mask;
    protected int[][] sm = new int[][] {new int[8], new int[8]}; // stereo mix
    protected int[] fout = new int[8]; // current output
    protected TrackInfo[] trkInfo = new TrackInfo[8];
    protected int[] option = new int[(int) OPT.END.ordinal()];

    protected boolean master_disable;
    protected int[] reg = new int[0x80]; // all state is contained here
    protected int regSelect;
    protected boolean regAdvance;
    protected int tickChannel;
    protected int tickClock;
    protected int renderChannel;
    protected int renderClock;
    protected int renderSubClock;

    public NesN106() {
        option[OPT.SERIAL.ordinal()] = 0;
        setClock(DEFAULT_CLOCK);
        setRate(DEFAULT_RATE);
        for (int i = 0; i < 8; ++i) {
            sm[0][i] = 128;
            sm[1][i] = 128;
            trkInfo[i] = new TrackInfo();
        }
        reset();
    }

    @Override
    public void setStereoMix(int trk, short mixl, short mixr) {
        if (trk < 0 || trk >= 8) return;
        trk = 7 - trk; // displayed channels are inverted
        sm[0][trk] = mixl;
        sm[1][trk] = mixr;
    }

    public DeviceInfo.TrackInfo getTrackInfo(int trk) {
        int channels = getChannels();
        int channel = 7 - trk; // invert the track display

        TrackInfo t = trkInfo[channel];

        if (trk >= channels) {
            t.maxVolume = 15;
            t.volume = 0;
            t._freq = 0;
            t.wavelen = 0;
            t.tone = -1;
            t.output = 0;
            t.key = false;
            t.freq = 0;
        } else {
            t.maxVolume = 15;
            t.volume = getVol(channel);
            t._freq = getFreq(channel);
            t.wavelen = getLen(channel);
            t.tone = getOff(channel);
            t.output = fout[channel];

            t.key = (t.volume > 0) && (t._freq > 0);
            t.freq = ((double) (t._freq) * clock) / (double) (15 * 65536 * channels * t.wavelen);
            t.halt = getChannels() > trk;
            for (int i = 0; i < t.wavelen; ++i)
                t.wave[i] = (short) getSample((i + t.tone) & 0xff);
        }

        return t;
    }

    public DeviceInfo.TrackInfo[] getTracksInfo() {
        try {
            for (int i = 0; i < 8; i++) {
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
        rate = r;
    }

    @Override
    public void setMask(int m) {
        // bit reverse the mask,
        // N163 waves are displayed in reverse order
        mask = 0
                | ((m & (1 << 0)) != 0 ? (1 << 7) : 0)
                | ((m & (1 << 1)) != 0 ? (1 << 6) : 0)
                | ((m & (1 << 2)) != 0 ? (1 << 5) : 0)
                | ((m & (1 << 3)) != 0 ? (1 << 4) : 0)
                | ((m & (1 << 4)) != 0 ? (1 << 3) : 0)
                | ((m & (1 << 5)) != 0 ? (1 << 2) : 0)
                | ((m & (1 << 6)) != 0 ? (1 << 1) : 0)
                | ((m & (1 << 7)) != 0 ? (1 << 0) : 0);
    }

    @Override
    public void setOption(int id, int val) {
        if (id < OPT.END.ordinal()) option[id] = val;
    }

    @Override
    public void reset() {
        master_disable = false;
        Arrays.fill(reg, 0);
        regSelect = 0;
        regAdvance = false;
        tickChannel = 0;
        tickClock = 0;
        renderChannel = 0;
        renderClock = 0;
        renderSubClock = 0;

        for (int i = 0; i < 8; ++i) fout[i] = 0;

        write(0xE000, 0x00); // master disable off
        write(0xF800, 0x80); // select $00 with auto-increment
        for (int i = 0; i < 0x80; ++i) { // set all regs to 0
            write(0x4800, 0x00);
        }
        write(0xF800, 0x00); // select $00 without auto-increment
    }

    @Override
    public void tick(int clocks) {
        if (master_disable) return;

        int channels = getChannels();

        tickClock += clocks;
        renderClock += clocks; // keep render in sync
        while (tickClock > 0) {
            int channel = 7 - tickChannel;

            int phase = getPhase(channel);
            int freq = getFreq(channel);
            int len = getLen(channel);
            int off = getOff(channel);
            int vol = getVol(channel);

            // accumulate 24-bit phase
            phase = (phase + freq) & 0x00FFFFFF;

            // wrap phase if wavelength exceeded
            int hilen = len << 16;
            while (phase >= hilen) phase -= hilen;

            // write back phase
            setPhase(phase, channel);

            // fetch sample (note: N163 output is centred at 8, and inverted w.r.t 2A03)
            int sample = 8 - getSample(((phase >> 16) + off) & 0xff);
            fout[channel] = sample * vol;

            // cycle to next channel every 15 clocks
            tickClock -= 15;
            ++tickChannel;
            if (tickChannel >= channels)
                tickChannel = 0;
        }
    }

    private static final int[] MIX = new int[] {256 / 1, 256 / 1, 256 / 2, 256 / 3, 256 / 4, 256 / 5, 256 / 6, 256 / 6, 256 / 6};

    @Override
    public int render(int[] b) {
        b[0] = 0;
        b[1] = 0;
        if (master_disable) return 2;

        int channels = getChannels();

        if (option[OPT.SERIAL.ordinal()] != 0) { // hardware accurate serial multiplexing
            // this could be made more efficient than going clock-by-clock
            // but this way is simpler
            int clocks = renderClock;
            while (clocks > 0) {
                int c = 7 - renderChannel;
                if (0 == ((mask >> c) & 1)) {
                    b[0] += fout[c] * sm[0][c];
                    b[1] += fout[c] * sm[1][c];
                }

                ++renderSubClock;
                if (renderSubClock >= 15) { // each channel gets a 15-cycle slice
                    renderSubClock = 0;
                    ++renderChannel;
                    if (renderChannel >= channels)
                        renderChannel = 0;
                }
                --clocks;
            }

            // increase output level by 1 bits (7 bits already added from sm)
            b[0] <<= 1;
            b[1] <<= 1;

            // average the output
            if (renderClock > 0) {
                b[0] /= renderClock;
                b[1] /= renderClock;
            }
            renderClock = 0;
        } else { // just mix all channels
            for (int i = (8 - channels); i < 8; ++i) {
                if (0 == ((mask >> (7 - i)) & 1)) {
                    b[0] += fout[i] * sm[0][i];
                    b[1] += fout[i] * sm[1][i];
                }
            }

            // mix together, increase output level by 8 bits, roll off 7 bits from sm
            b[0] = (b[0] * MIX[channels]) >> 7;
            b[1] = (b[1] * MIX[channels]) >> 7;
            // when approximating the serial multiplex as a straight mix, once the
            // multiplex frequency gets below the nyquist frequency an average mix
            // begins to Sound too quiet. To approximate this effect, I don't attenuate
            // any further after 6 channels are active.
        }

        // 8 bit approximation of master volume
        // max N163 vol vs max APU square
        // unfortunately, games have been measured as low as 3.4x and as high as 8.5x
        // with higher volumes on Erika, King of Kings, and Rolling Thunder
        // and lower volumes on others. Using 6.0x as a rough "one size fits all".
        final double MASTER_VOL = 6.0 * 1223.0;
        final double MAX_OUT = 15.0 * 15.0 * 256.0; // max digital value
        final int GAIN = (int) ((MASTER_VOL / MAX_OUT) * 256.0f);
        b[0] = (b[0] * GAIN) >> 8;
        b[1] = (b[1] * GAIN) >> 8;

        if (listener != null) listener.accept(new int[] {-1, -1, -1, -1, -1, Math.abs(b[0]), -1, -1});

        return 2;
    }

    @Override
    public boolean write(int adr, int val, int id/*=0*/) {
        if (adr == 0xE000) { // master disable
            master_disable = ((val & 0x40) != 0);
            return true;
        } else if (adr == 0xF800) { // register select
            regSelect = (val & 0x7F);
            regAdvance = (val & 0x80) != 0;
            return true;
        } else if (adr == 0x4800) { // register write
            reg[regSelect] = val;
            if (regAdvance)
                regSelect = (regSelect + 1) & 0x7F;
            return true;
        }
        return false;
    }

    @Override
    public boolean read(int adr, int[] val, int id) {
        if (adr == 0x4800) { // register read
            val[0] = reg[regSelect];
            if (regAdvance)
                regSelect = (regSelect + 1) & 0x7F;
            return true;
        }
        return false;
    }

    //
    // register decoding/encoding functions
    //

    private int getPhase(int channel) {
        // 24-bit phase stored in channel regs 1/3/5
        channel = channel << 3;
        return (reg[0x41 + channel])
                + (reg[0x43 + channel] << 8)
                + (reg[0x45 + channel] << 16);
    }

    private int getFreq(int channel) {
        // 19-bit frequency stored in channel regs 0/2/4
        channel = channel << 3;
        return (reg[0x40 + channel])
                + (reg[0x42 + channel] << 8)
                + ((reg[0x44 + channel] & 0x03) << 16);
    }

    private int getOff(int channel) {
        // 8-bit offset stored in channel reg 6
        channel = channel << 3;
        return reg[0x46 + channel];
    }

    private int getLen(int channel) {
        // 6-bit<<3 length stored obscurely in channel reg 4
        channel = channel << 3;
        return 256 - (reg[0x44 + channel] & 0xFC);
    }

    private int getVol(int channel) {
        // 4-bit volume stored in channel reg 7
        channel = channel << 3;
        return reg[0x47 + channel] & 0x0F;
    }

    private int getSample(int index) {
        // every sample becomes 2 samples in regs
        return (index & 1) != 0 ?
                ((reg[index >> 1] >> 4) & 0x0F) :
                (reg[index >> 1] & 0x0F);
    }

    private int getChannels() {
        // 3-bit channel count stored in reg 0x7F
        return ((reg[0x7F] >> 4) & 0x07) + 1;
    }

    private void setPhase(int phase, int channel) {
        // 24-bit phase stored in channel regs 1/3/5
        channel = channel << 3;
        reg[0x41 + channel] = phase & 0xff;
        reg[0x43 + channel] = (phase >> 8) & 0xff;
        reg[0x45 + channel] = (phase >> 16) & 0xff;
    }

    private Consumer<int[]> listener;

    public void setListener(Consumer<int[]> listener) {
        this.listener = listener;
    }
}
