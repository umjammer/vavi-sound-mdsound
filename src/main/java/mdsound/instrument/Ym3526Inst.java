package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Ym3526;


public class Ym3526Inst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 3579545;
    public static final int MAX_CHIPS = 0x02;

    private final Ym3526[] chips = {new Ym3526(), new Ym3526()};

    @Override
    public String getName() {
        return "YM3526";
    }

    @Override
    public String getShortName() {
        return "OPL";
    }

    @Override
    public void reset(int chipId) {
        visVolume = new int[][][] {
                {{0, 0}},
                {{0, 0}}
        };

        Ym3526 chip = chips[chipId];
        chip.reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        if (chipId >= MAX_CHIPS) return 0;

        int rate = (clock & 0x7fff_ffff) / 72;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;

        // stream system initialize
        Ym3526 chip = chips[chipId];
        chip.init(clock, rate);

        // YM3526 setup
        chip.setTimerHandler(Ym3526Inst::handlerTimer);
        chip.setIrqHandler(Ym3526Inst::handlerIRQ);
        chip.setUpdateHandler(this::updateStream);

        return rate;
    }

    @Override
    public int read(int chipId, int adr) {
        Ym3526 info = chips[chipId];
        return info.read(adr & 1);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        Ym3526 chip = chips[chipId];
        if (chip == null || chip.chip == null) return 0;
        chip.write(0x00, adr);
        chip.write(0x01, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        Ym3526 chip = chips[chipId];
        chip.updateOne(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        Ym3526 chip = chips[chipId];
        chip.shutdown();
    }

    private final int[][] dummyBuf = {null, null};

    private void updateStream(/*, int interval */) {
        chips[0].updateOne(dummyBuf, 0);
    }

    /** IRQ Handler */
    private static void handlerIRQ(int irq) {
    }

    /** TimerHandler from Fm.c */
    private static void handlerTimer(int c, int period) {
        if (period == 0) { // Reset FM Timer
        } else { // Start FM Timer
        }
    }

    public int readStatusPort(int chipId, int offset) {
        return read(chipId, 0);
    }

    public int readPort(int chipId, int offset) {
        return read(chipId, 1);
    }

    public void writeControlPort(int chipId, int offset, byte data) {
        Ym3526 chip = chips[chipId];
        chip.write(0, data);
    }

    public void writePort(int chipId, int offset, byte data) {
        Ym3526 chip = chips[chipId];
        chip.write(1, data);
    }

    public void setMuteMask(int chipId, int muteMask) {
        Ym3526 chip = chips[chipId];
        chip.setMuteMask(muteMask);
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 2d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        }
        return result;
    }
}
