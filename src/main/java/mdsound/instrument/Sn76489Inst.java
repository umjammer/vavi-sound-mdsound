package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import mdsound.Instrument;
import mdsound.chips.Sn76489;


public class Sn76489Inst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 3579545;
    public static final int MAX_CHIPS = 2;

    private final Sn76489[] chips = {new Sn76489(), new Sn76489()};

    private final int[] mask = {15, 15}; // Since psg is based on mute, the bits are reversed.

    public Sn76489Inst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "SN76489";
    }

    @Override
    public String getShortName() {
        return "DCSG";
    }

    @Override
    public void init() {
        mask[0] = 15;
        mask[1] = 15;
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        return chips[chipId].start(samplingRate, clock);
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write(data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] buffer, int length) {
        chips[chipId].update(buffer, length);

        visVolume[chipId][0][0] = chips[chipId].getVolume()[0][0];
        visVolume[chipId][0][1] = chips[chipId].getVolume()[0][1];
    }

    @Override
    public void stop(int chipId) {
    }

    @Override
    public synchronized void setMask(int chipId, int ch) {
        mask[chipId] &= ~ch;
        chips[chipId].setMute(mask[chipId]);
    }

    @Override
    public synchronized void resetMask(int chipId, int ch) {
        mask[chipId] |= ch;
        chips[chipId].setMute(mask[chipId]);
    }

    // ----

    public synchronized int[] readRegister() {
//        return chips[0].registers;
        return new int[4];
    }

    public synchronized void setPan(int chipId, int data) {
        chips[chipId].writeGGStereo(data);
    }

    // ----

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
