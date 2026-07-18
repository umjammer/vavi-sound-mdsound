package mdsound.instrument;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.Map;

import mdsound.Instrument;
import mdsound.chips.Ym2612;

import static java.lang.System.getLogger;


public class Ym2612Inst extends Instrument.BaseInstrument {

    private static final Logger logger = getLogger(Ym2612Inst.class.getName());

    public static final int DefaultFMClockValue = 7670454;
    public static final int MAX_CHIPS = 2;

    private final Ym2612[] chips = {new Ym2612(), new Ym2612()};

    private final int[] mask = {0, 0};
    private final int[][] keyOn = {new int[6], new int[6]};

    public Ym2612Inst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public void init() {
        mask[0] = 0;
        mask[1] = 0;
    }

    @Override
    public String getName() {
        return "Ym2612";
    }

    @Override
    public String getShortName() {
        return "OPN2";
    }

    @Override
    public void reset(int chipId) {
        assert chipId < MAX_CHIPS;
        Ym2612 chip = chips[chipId];
        chip.reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        if (clock == 0) clock = DefaultFMClockValue;

        chips[chipId].init(clock, samplingRate, clock);
        chips[chipId].reset();

        // Operation option settings
        if (option != null && option.length > 0 && option[0] instanceof Integer flags) {
logger.log(Level.DEBUG, "option: " + flags);
            chips[chipId].setOptions(flags & 0x3);
        }

        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized int write(int chipId, int port, int adr, int data) {
        assert chipId < MAX_CHIPS;
        chips[chipId].write(0 + (port & 1) * 2, adr);
        chips[chipId].write(1 + (port & 1) * 2, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        assert chipId < MAX_CHIPS;

        chips[chipId].update(outputs, samples);
        chips[chipId].updateDacAndTimers(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
    }

    // TODO 2612
    @Override
    public synchronized void setMask(int chipId, int ch) {
        assert chipId < MAX_CHIPS;
        mask[chipId] |= 1 << ch;
        chips[chipId].setMute(mask[chipId]);
    }

    @Override
    public synchronized void resetMask(int chipId, int ch) {
        assert chipId < MAX_CHIPS;
        mask[chipId] &= ~(1 << ch);
        chips[chipId].setMute(mask[chipId]);
    }

    //----

    @Override
    public Map<String, Object> getView(int chipId, String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "registers" ->
                    result.put(getName(), new int[][][] {chips[0].getRegisters(), chips[0].getRegisters()});
            case "keyOn" -> result.put(getName(), chips[chipId].keyStatuses());
        }
        return result;
    }
}
