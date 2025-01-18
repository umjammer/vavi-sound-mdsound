package mdsound.instrument;

import mdsound.Instrument;
import mdsound.np.chip.NesVrc6;
import dotnet4j.util.compat.Tuple;


public class Vrc6Inst extends Instrument.BaseInstrument {

    private double apu_clock_rest = 0;

    private final NesVrc6[] vrc6;
    private final int[] b = new int[2];
    private int volume = 0;

    private static final int[] vrc6AddressTable = {
            0x9000, 0x9001, 0x9002, 0x9003,
            0xa000, 0xa001, 0xa002, 0xa003,
            0xb000, 0xb001, 0xb002, 0xb003
    };

    public Vrc6Inst() {
        vrc6 = new NesVrc6[] {new NesVrc6(), new NesVrc6()};
        setVolumeVRC6(0);
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
        vrc6[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate) {
        return start(chipId, samplingRate, 100);
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        vrc6[chipId].setClock(clock);
        vrc6[chipId].setRate(samplingRate);

        if (option != null && option.length > 0) {
            for (Object o : option) {
                if (o instanceof Tuple) { // <Integer, Integer>
                    Tuple<Integer, Integer> item = (Tuple<Integer, Integer>) o;
                    vrc6[chipId].setOption(item.getItem1(), item.getItem2());
                }
            }
        }
        setVolumeVRC6(0);

        return samplingRate;
    }

    @Override
    public void stop(int chipId) {
        vrc6[chipId].reset();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        b[0] = 0;
        b[1] = 0;

        double apu_clock_per_sample = 0;
        apu_clock_per_sample = vrc6[chipId].clock / vrc6[chipId].rate;
        apu_clock_rest += apu_clock_per_sample;
        int apu_clocks = (int) (apu_clock_rest);
        if (apu_clocks > 0) apu_clock_rest -= apu_clocks;

        vrc6[chipId].tick(apu_clocks);
        vrc6[chipId].render(b);

        outputs[0][0] += (short) ((limit(b[0], 0x7fff, -0x8000) * volume) >> 12); // If it's below 12, the sound will distort.
        outputs[1][0] += (short) ((limit(b[1], 0x7fff, -0x8000) * volume) >> 12); // If it's below 12, the sound will distort.
    }

    @Override
    public synchronized int write(int chipIndex, int chipId, int adr, int data) {
        vrc6[chipId].write(vrc6AddressTable[adr], data);
        return 0;
    }

    public void setVolumeVRC6(int db) {
        db = Math.min(db, 20);
        if (db > -192)
            volume = (int) (16384.0 * Math.pow(10.0, db / 40.0));
        else
            volume = 0;
    }

    private static int limit(int v, int max, int min) {
        return v > max ? max : Math.max(v, min);
    }
}
