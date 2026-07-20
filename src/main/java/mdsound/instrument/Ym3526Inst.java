package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Ym3526;
import mdsound.chips.Ym3526;


public class Ym3526Inst extends Instrument.BaseInstrument {

    private static final int CHIP_SAMPLING_MODE = 0;

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

    public Ym3526Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        int rate = (clock & 0x7fff_ffff) / 72;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) || CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;

        // stream system initialize
        chips[chipId].init(clock, rate);

        // YM3526 setup
        chips[chipId].setTimerHandler(Ym3526Inst::handlerTimer);
        chips[chipId].setIrqHandler(Ym3526Inst::handlerIRQ);
        chips[chipId].setUpdateHandler(this::updateStream);

        return rate;
    }

    @Override
    public int read(int chipId, int adr) {
        return chips[chipId].read(adr & 1);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < MAX_CHIPS;
        chips[chipId].write(0x00, adr);
        chips[chipId].write(0x01, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].updateOne(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].shutdown();
    }

    @Override
    public void setMask(int chipId, int ch) {
//        chips[chipId].setMuteMask(ch); // TODO
    }

    @Override
    public void resetMask(int chipId, int ch) {
//        chips[chipId].setMuteMask(~ch); // TODO
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

    public void writeControlPort(int chipId, int offset, byte data) {
        chips[chipId].write(0, data);
    }

    public void writePort(int chipId, int offset, byte data) {
        chips[chipId].write(1, data);
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 2d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "statusPort" -> result.put(getName(), read(chipId, 0));
            case "port" -> result.put(getName(), read(chipId, 1));
            case "info" -> {
                Ym3526 chip = chips[chipId];
                for (int ch = 0; ch < Ym3526.CHANNELS; ch++) {
                    result.put("channels." + ch + ".keyOn", chip.isKeyOn(ch));
                    result.put("channels." + ch + ".fnum", chip.getFnum(ch));
                    result.put("channels." + ch + ".block", chip.getBlock(ch));
                    result.put("channels." + ch + ".totalLevel", chip.getTotalLevel(ch));
                    result.put("channels." + ch + ".mute", chip.isMuted(ch));
                }
            }
        }
        return result;
    }

    /** whether the operator is a carrier, as the chip has its connection set */
    public boolean isCarrier(int chipId, int ch, int slot) {
        return chips[chipId].isCarrier(ch, slot);
    }

    /** whether the rhythm section is on, as the chip has it */
    public boolean isRhythm(int chipId) {
        return chips[chipId].isRhythm();
    }
}
