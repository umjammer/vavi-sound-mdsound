package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import mdsound.Instrument;
import mdsound.chips.Sn76489;


public class Sn76489Inst extends Instrument.BaseInstrument {

    public static final int DefaultPSGClockValue = 3579545;

    private static final int MAX_CHIPS = 2;

    public Sn76489[] chips = new Sn76489[] {new Sn76489(), new Sn76489()};

    public void writeGGStereo(int chipId, int data) {
        Sn76489 chip = chips[chipId];
        chip.writeGGStereo(data);
    }

    @Override
    public String getName() {
        return "SN76489";
    }

    @Override
    public String getShortName() {
        return "DCSG";
    }

    public Sn76489Inst() {
        // 0..Main
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(int chipId, int clock) {
        return start(chipId, DefaultPSGClockValue, clock);
    }

    @Override
    public int start(int chipId, int samplingRate, int clockValue, Object... option) {
        chips[chipId] = new Sn76489();
        Sn76489 chip = chips[chipId];
        return chip.start(samplingRate, clockValue);
    }

    @Override
    public void reset(int chipId) {
        Sn76489 chip = chips[chipId];
        chip.reset();
    }

    @Override
    public void stop(int chipId) {
        chips[chipId] = null;
    }

    @Override
    public void update(int chipId, int[][] buffer, int length) {
        Sn76489 chip = chips[chipId];
        int[][] volumes = chip.update(buffer, length);

        visVolume[chipId][0][0] = volumes[0][0];
        visVolume[chipId][0][1] = volumes[0][1];
    }

    private void writeSN76489(int chipId, int data) {
        Sn76489 chip = chips[chipId];
        chip.write(data);
    }

    /** @param val mask */
    public void setMute(int chipId, int val) {
        Sn76489 chip = chips[chipId];
        chip.setMute(val);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        writeSN76489(chipId, data);
        return 0;
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
