/*
 * NSFPlay/NFSPlug project by Brezza.
 *
 * https://web.archive.org/web/20160301201825/http://www.pokipoki.org/dsa/
 */

package mdsound.np.chip;

import java.util.function.Consumer;

import mdsound.np.Device.SoundChip;
import mdsound.np.chip.DeviceInfo.BasicTrackInfo;


public class NesFme7 implements SoundChip {

    // stereo mix
    protected int[][] sm = {new int[3], new int[3]};
    protected short[] buf = new short[2];
    protected Emu2149 emu2149;
    // clock divider
    protected int divider;
    protected double clock, rate;
    protected BasicTrackInfo[] trackInfo = new BasicTrackInfo[5];

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
            write(0xc000, i);
            write(0xe000, 0);
        }
        write(0xc000, 0x07); // disable all tones
        write(0xe000, 0x3f);

        divider = 0;
        emu2149.psg.reset();
    }

    @Override
    public boolean write(int address, int value, int id /* = 0 */) {
        if (address == 0xc000) {
            emu2149.psg.writeIO(0, value);
            return true;
        }
        if (address == 0xe000) {
            emu2149.psg.writeIO(1, value);
            return true;
        } else
            return false;
    }

    /** @param value OUT */
    @Override
    public boolean read(int address, int[] value, int id /* = 0 */) {
        value[0] = emu2149.psg.readReg(address);
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
    public void setStereoMix(int track, int mixL, int mixR) {
        if (track < 0) return;
        if (track > 2) return;
        sm[0][track] = mixL;
        sm[1][track] = mixR;
    }

    public DeviceInfo.TrackInfo getTrackInfo(int track) {
        assert track < 5;

        Emu2149.Psg psg = emu2149.psg;
        if (track < 3) {
            trackInfo[track]._freq = psg.freq[track];
            if (psg.freq[track] != 0)
                trackInfo[track].freq = psg.clk / 32.0 / psg.freq[track];
            else
                trackInfo[track].freq = 0;

            trackInfo[track].output = psg.cout[track];
            trackInfo[track].maxVolume = 15;
            trackInfo[track].volume = psg.volume[track] >> 1;
            trackInfo[track].key = ((~(psg.tMask[track])) & 1) != 0;
            trackInfo[track].tone = (psg.tMask[track] != 0 ? 2 : 0) + (psg.nMask[track] != 0 ? 1 : 0);
        } else if (track == 3) { // envelope
            trackInfo[track]._freq = psg.envFreq;
            if (psg.envFreq != 0)
                trackInfo[track].freq = psg.clk / 512.0 / psg.envFreq;
            else
                trackInfo[track].freq = 0;

            if (psg.envContinue != 0 && psg.envAlternate != 0 && psg.envHold == 0) { // triangle wave
                trackInfo[track].freq *= 0.5f; // sounds an octave down
            }

            trackInfo[track].output = psg.volTbl[psg.envPtr];
            trackInfo[track].maxVolume = 0;
            trackInfo[track].volume = 0;
            trackInfo[track].key = (((psg.volume[0] | psg.volume[1] | psg.volume[2]) & 32) != 0);
            trackInfo[track].tone =
                    (psg.envContinue != 0 ? 8 : 0) |
                    (psg.envAttack != 0 ? 4 : 0) |
                    (psg.envAlternate != 0 ? 2 : 0) |
                    (psg.envHold != 0 ? 1 : 0);
        } else if (track == 4) { // noise
            trackInfo[track]._freq = psg.noiseFreq >> 1;
            if (trackInfo[track]._freq > 0)
                trackInfo[track].freq = psg.clk / 16.0 / psg.noiseFreq;
            else
                trackInfo[track].freq = 0;

            trackInfo[track].output = psg.noiseSeed & 1;
            trackInfo[track].maxVolume = 0;
            trackInfo[track].volume = 0;
            trackInfo[track].key = false;
            trackInfo[track].tone = 0;
        }
        return trackInfo[track];
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

