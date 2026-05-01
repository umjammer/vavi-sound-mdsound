package mdsound.instrument;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.np.chip.NesVrc6;


public class Vrc6Inst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 100;

    private double apu_clock_rest = 0;

    private final NesVrc6[] chips;
    private int volume = 0;

    private static final int[] vrc6AddressTable = {
            0x9000, 0x9001, 0x9002, 0x9003,
            0xa000, 0xa001, 0xa002, 0xa003,
            0xb000, 0xb001, 0xb002, 0xb003
    };

    public Vrc6Inst() {
        chips = new NesVrc6[] {new NesVrc6(), new NesVrc6()};
        setVolume(0);
    }

    @Override
    public String getName() {
        return "Vrc6Inst";
    }

    @Override
    public String getShortName() {
        return "Vrc6Inst";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId].setClock(clock);
        chips[chipId].setRate(samplingRate);

        if (option != null && option.length > 0) {
            for (Object o : option) {
                if (o instanceof Tuple) { // <Integer, Integer>
                    Tuple<Integer, Integer> item = (Tuple<Integer, Integer>) o;
                    chips[chipId].setOption(item.getItem1(), item.getItem2());
                }
            }
        }
        setVolume(0);

        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized int write(int chipIndex, int chipId, int adr, int data) {
        chips[chipId].write(vrc6AddressTable[adr], data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {

        double apu_clock_per_sample = 0;
        apu_clock_per_sample = chips[chipId].clock / chips[chipId].rate;
        apu_clock_rest += apu_clock_per_sample;
        int apu_clocks = (int) (apu_clock_rest);
        if (apu_clocks > 0) apu_clock_rest -= apu_clocks;

        int[] b = new int[2];

        chips[chipId].tick(apu_clocks);
        chips[chipId].render(b);

        outputs[0][0] += (short) ((limit(b[0], 0x7fff, -0x8000) * volume) >> 12); // If it's below 12, the sound will distort.
        outputs[1][0] += (short) ((limit(b[1], 0x7fff, -0x8000) * volume) >> 12); // If it's below 12, the sound will distort.
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public void setMask(int chipId, int ch) {
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }

    private static int limit(int v, int max, int min) {
        return v > max ? max : Math.max(v, min);
    }

    // ----

    public void setVolume(int db) {
        db = Math.min(db, 20);
        if (db > -192)
            volume = (int) (16384.0 * Math.pow(10.0, db / 40.0));
        else
            volume = 0;
    }
}
