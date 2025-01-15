package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Ym3526;


public class Ym3526Inst extends Instrument.BaseInstrument {

    private static final int DefaultYM3526ClockValue = 3579545;

    private static final int MAX_CHIPS = 0x02;
    static Ym3526[] chips = new Ym3526[2];

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
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };

        Ym3526 info = chips[chipId];
        info.reset();
    }

    @Override
    public int start(int chipId, int samplingRate) {
        if (chipId >= MAX_CHIPS) return 0;

        int rate = (DefaultYM3526ClockValue & 0x7fff_ffff) / 72;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;

        // stream system initialize
        Ym3526 info = chips[chipId] = new Ym3526(DefaultYM3526ClockValue, rate);

        // YM3526 setup
        info.setTimerHandler(Ym3526Inst::TimerHandler);
        info.setIrqHandler(Ym3526Inst::IRQHandler);
        info.setUpdateHandler(this::_stream_update);

        return rate;
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        if (chipId >= MAX_CHIPS) return 0;

        int rate = (clock & 0x7fff_ffff) / 72;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;

        // stream system initialize
        Ym3526 info = chips[chipId] = new Ym3526(clock, rate);

        // YM3526 setup
        info.setTimerHandler(Ym3526Inst::TimerHandler);
        info.setIrqHandler(Ym3526Inst::IRQHandler);
        info.setUpdateHandler(this::_stream_update);

        return rate;
    }

    @Override
    public void stop(int chipId) {
        Ym3526 info = chips[chipId];
        info.shutdown();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        Ym3526 chip = chips[chipId];
        chip.updateOne(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        Ym3526 chip = chips[chipId];
        if (chip == null || chip.chip == null) return 0;
        chip.write(0x00, adr);
        chip.write(0x01, data);
        return 0;
    }

    private final int[][] dummyBuf = new int[][] {null, null};

    private void _stream_update(/*, int interval */) {
        chips[0].updateOne(dummyBuf, 0);
    }

    /** IRQ Handler */
    private static void IRQHandler(int irq) {
    }

    /** TimerHandler from Fm.c */
    private static void TimerHandler(int c, int period) {
        if (period == 0) { // Reset FM Timer
        } else { // Start FM Timer
        }
    }

    public int ym3526_r(int chipId, int offset) {
        Ym3526 info = chips[chipId];
        return info.read(offset & 1);
    }

    public int ym3526_status_port_r(int chipId, int offset) {
        return ym3526_r(chipId, 0);
    }

    public int ym3526_read_port_r(int chipId, int offset) {
        return ym3526_r(chipId, 1);
    }

    public void ym3526_control_port_w(int chipId, int offset, byte data) {
        Ym3526 chip = chips[chipId];
        chip.write(0, data);
    }

    public void ym3526_write_port_w(int chipId, int offset, byte data) {
        Ym3526 chip = chips[chipId];
        chip.write(1, data);
    }

    public void ym3526_set_mute_mask(int chipId, int muteMask) {
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
