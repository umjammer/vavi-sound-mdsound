package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Ym3526;


public class Ym3526Inst extends Instrument.BaseInstrument {

    @Override
    public String getName() {
        return "YM3526";
    }

    @Override
    public String getShortName() {
        return "OPL";
    }

    @Override
    public void reset(byte chipId) {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };

        device_reset_ym3526(chipId);
    }

    private static final int DefaultYM3526ClockValue = 3579545;

    @Override
    public int start(byte chipId, int clock) {
        return device_start_ym3526(chipId, DefaultYM3526ClockValue);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        return device_start_ym3526(chipId, clockValue);
    }

    @Override
    public void stop(byte chipId) {
        device_stop_ym3526(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        ym3526_stream_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        ym3526_w(chipId, 0x00, (byte) adr);
        ym3526_w(chipId, 0x01, (byte) data);
        return 0;
    }

    private static final int MAX_CHIPS = 0x02;
    static Ym3526[] chips = new Ym3526[2];

    public void ym3526_stream_update(byte chipId, int[][] outputs, int samples) {
        Ym3526 info = chips[chipId];
        info.updateOne(outputs, samples);
    }

    private int[][] DUMMYBUF = new int[][] {null, null};

    private void _stream_update(/*, int interval*/) {
        chips[0].updateOne(DUMMYBUF, 0);
    }

    /** IRQ Handler */
    private void IRQHandler(int irq) {
    }

    /** TimerHandler from Fm.c */
    private void TimerHandler(int c, int period) {
        if (period == 0) { // Reset FM Timer
        } else { // Start FM Timer
        }
    }
    public int device_start_ym3526(byte chipId, int clock) {
        Ym3526 info;

        if (chipId >= MAX_CHIPS)
            return 0;

        int rate = (clock & 0x7FFFFFFF) / 72;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;

        // stream system initialize
        info = chips[chipId] = new Ym3526(clock, rate);

        // YM3526 setup
        info.setTimerHandler(this::TimerHandler);
        info.setIrqHandler(this::IRQHandler);
        info.setUpdateHandler(this::_stream_update);

        return rate;
    }

    public void device_stop_ym3526(byte chipId) {
        Ym3526 info = chips[chipId];
        info.shutdown();
    }

    public void device_reset_ym3526(byte chipId) {
        Ym3526 info = chips[chipId];
        info.reset();
    }

    public byte ym3526_r(byte chipId, int offset) {
        Ym3526 info = chips[chipId];
        return info.read(offset & 1);
    }

    public void ym3526_w(byte chipId, int offset, byte data) {
        Ym3526 info = chips[chipId];
        if (info == null || info.chip == null) return;

        info.write(offset & 1, data);
    }

    public byte ym3526_status_port_r(byte chipId, int offset) {
        return ym3526_r(chipId, 0);
    }

    public byte ym3526_read_port_r(byte chipId, int offset) {
        return ym3526_r(chipId, 1);
    }

    public void ym3526_control_port_w(byte chipId, int offset, byte data) {
        ym3526_w(chipId, 0, data);
    }

    public void ym3526_write_port_w(byte chipId, int offset, byte data) {
        ym3526_w(chipId, 1, data);
    }

    public void ym3526_set_mute_mask(byte chipId, int muteMask) {
        Ym3526 info = chips[chipId];
        info.setMuteMask(muteMask);
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 2d);
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        result.put("ym3526", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        return result;
    }
}
