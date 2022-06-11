package mdsound;

import mdsound.np.chip.NesVrc6;
import dotnet4j.Tuple;


public class VRC6 extends Instrument.BaseInstrument {
    @Override
    public String getName() {
        return "VRC6";
    }

    @Override
    public String getShortName() {
        return "VRC6";
    }

    private double apu_clock_rest = 0;

    @Override
    public void reset(byte chipID) {
        vrc6[chipID].reset();
    }

    @Override
    public int start(byte chipID, int clock) {
        return start(chipID, clock, 100, null);
    }

    @Override
    public int start(byte chipID, int sampleRate, int clockValue, Object... option) {
        vrc6[chipID].setClock(clockValue);
        vrc6[chipID].setRate(sampleRate);

        if (option != null && option.length > 0) {
            for (int i = 0; i < option.length; i++) {
                if (option[i] instanceof Tuple) // <Integer, Integer>
                {
                    Tuple<Integer, Integer> item = (Tuple<Integer, Integer>) option[i];
                    vrc6[chipID].setOption(item.Item1, item.Item2);
                }
            }
        }
        setVolumeVRC6(0);

        return sampleRate;
    }

    @Override
    public void stop(byte chipID) {
        vrc6[chipID].reset();
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        b[0] = 0;
        b[1] = 0;

        double apu_clock_per_sample = 0;
        apu_clock_per_sample = vrc6[chipID].clock / vrc6[chipID].rate;
        apu_clock_rest += apu_clock_per_sample;
        int apu_clocks = (int) (apu_clock_rest);
        if (apu_clocks > 0) apu_clock_rest -= apu_clocks;

        vrc6[chipID].tick((int) apu_clocks);
        vrc6[chipID].render(b);

        outputs[0][0] += (short) ((limit(b[0], 0x7fff, -0x8000) * volume) >> 12); // 12 以下だと音割れる
        outputs[1][0] += (short) ((limit(b[1], 0x7fff, -0x8000) * volume) >> 12); // 12 以下だと音割れる
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        vrc6[chipID].write(adr, data);
        return 0;
    }

    private NesVrc6[] vrc6;
    private int[] b = new int[2];
    private int volume = 0;

    public VRC6() {
        vrc6 = new NesVrc6[] {new NesVrc6(), new NesVrc6()};
        setVolumeVRC6(0);
    }

    public void setVolumeVRC6(int db) {
        db = Math.min(db, 20);
        if (db > -192)
            volume = (int) (16384.0 * Math.pow(10.0, db / 40.0));
        else
            volume = 0;
    }

    public static int limit(int v, int max, int min) {
        return v > max ? max : Math.max(v, min);
    }
}
