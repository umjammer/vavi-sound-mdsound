package mdsound.np.chip;

import java.util.function.Consumer;

import mdsound.np.Device.SoundChip;
import mdsound.np.chip.DeviceInfo.BasicTrackInfo;


public class NesFme7 implements SoundChip {

    // stereo mix
    protected int[][] sm = new int[][] {new int[3], new int[3]};
    protected short[] buf = new short[2];
    protected Emu2149 emu2149;
    // clock divider
    protected int divider;
    protected double clock, rate;
    protected BasicTrackInfo[] tarckInfo = new BasicTrackInfo[5];

    protected static final int DIVIDER = 8 * 2;
    public static final double DEFAULT_CLOCK = 1789772.0;
    public static final int DEFAULT_RATE = 44100;

    public NesFme7() {
        emu2149 = new Emu2149((int) DEFAULT_CLOCK, DEFAULT_RATE);

        for (int c = 0; c < 2; ++c)
            for (int t = 0; t < 3; ++t)
                sm[c][t] = 128;
    }

    @Override
    public void setClock(double c) {
        this.clock = c * 2.0;
    }

    @Override
    public void setRate(double r) {
        //rate = r ? r : DEFAULT_RATE;
        rate = DEFAULT_CLOCK / (double) DIVIDER; // TODO rewrite Psg to integrate with clock
        emu2149.psg.setRate((int) rate);
    }

    @Override
    public void reset() {
        for (int i = 0; i < 16; ++i) { // blank all registers
            write(0xC000, i);
            write(0xE000, 0);
        }
        write(0xC000, 0x07); // disable all tones
        write(0xE000, 0x3F);

        divider = 0;
        emu2149.psg.reset();
    }

    @Override
    public boolean write(int adress, int value, int id/*=0*/) {
        if (adress == 0xC000) {
            emu2149.psg.writeIO(0, value);
            return true;
        }
        if (adress == 0xE000) {
            emu2149.psg.writeIO(1, value);
            return true;
        } else
            return false;
    }

    /** @param value OUT */
    @Override
    public boolean read(int adress, int[] value, int id/* = 0*/) {
        value[0] = emu2149.psg.readReg(adress);
        return false;
    }

    @Override
    public void tick(int clocks) {
        divider += clocks;
        while (divider >= DIVIDER) {
            divider -= DIVIDER;
            emu2149.psg.calcPsg();
        }
    }

    @Override
    public int render(int[] b) {
        Emu2149.Psg psg = emu2149.psg;
        b[0] = b[1] = 0;

        for (int i = 0; i < 3; ++i) {
            // note negative polarity
            b[0] -= psg.cout[i] * sm[0][i];
            b[1] -= psg.cout[i] * sm[1][i];
        }
        b[0] >>= (7 - 4);
        b[1] >>= (7 - 4);

        // master volume adjustment
        final int master = (int) (0.64 * 256.0);
        b[0] = (b[0] * master) >> 8;
        b[1] = (b[1] * master) >> 8;

        if (listener != null) listener.accept(new int[] {-1, -1, -1, Math.abs(b[0]),-1, -1, -1, -1});

        return 2;
    }

    @Override
    public void setStereoMix(int track, short mixL, short mixR) {
        if (track < 0) return;
        if (track > 2) return;
        sm[0][track] = mixL;
        sm[1][track] = mixR;
    }

    public DeviceInfo.TrackInfo getTrackInfo(int track) {
        //assert(track < 5);

        Emu2149.Psg psg = emu2149.psg;
        if (track < 3) {
            tarckInfo[track]._freq = psg.freq[track];
            if (psg.freq[track] != 0)
                tarckInfo[track].freq = psg.clk / 32.0 / psg.freq[track];
            else
                tarckInfo[track].freq = 0;

            tarckInfo[track].output = psg.cout[track];
            tarckInfo[track].maxVolume = 15;
            tarckInfo[track].volume = psg.volume[track] >> 1;
            tarckInfo[track].key = ((~(psg.tMask[track])) & 1) != 0;
            tarckInfo[track].tone = (psg.tMask[track] != 0 ? 2 : 0) + (psg.nMask[track] != 0 ? 1 : 0);
        } else if (track == 3) { // envelope
            tarckInfo[track]._freq = psg.envFreq;
            if (psg.envFreq != 0)
                tarckInfo[track].freq = psg.clk / 512.0 / psg.envFreq;
            else
                tarckInfo[track].freq = 0;

            if (psg.envContinue != 0 && psg.envAlternate != 0 && psg.envHold == 0) { // triangle wave
                tarckInfo[track].freq *= 0.5f; // sounds an octave down
            }

            tarckInfo[track].output = psg.volTbl[psg.envPtr];
            tarckInfo[track].maxVolume = 0;
            tarckInfo[track].volume = 0;
            tarckInfo[track].key = (((psg.volume[0] | psg.volume[1] | psg.volume[2]) & 32) != 0);
            tarckInfo[track].tone =
                    (psg.envContinue != 0 ? 8 : 0) |
                    (psg.envAttack != 0 ? 4 : 0) |
                    (psg.envAlternate != 0 ? 2 : 0) |
                    (psg.envHold != 0 ? 1 : 0);
        } else if (track == 4) { // noise
            tarckInfo[track]._freq = psg.noiseFreq >> 1;
            if (tarckInfo[track]._freq > 0)
                tarckInfo[track].freq = psg.clk / 16.0 / psg.noiseFreq;
            else
                tarckInfo[track].freq = 0;

            tarckInfo[track].output = psg.noiseSeed & 1;
            tarckInfo[track].maxVolume = 0;
            tarckInfo[track].volume = 0;
            tarckInfo[track].key = false;
            tarckInfo[track].tone = 0;
        }
        return tarckInfo[track];
    }

    @Override
    public void setMask(int mask) {
        emu2149.psg.setMask(mask);
    }

    @Override
    public void setOption(int id, int value) {
        throw new UnsupportedOperationException();
    }

    private Consumer<int[]> listener;

    public void setListener(Consumer<int[]> listener) {
        this.listener = listener;
    }
}

