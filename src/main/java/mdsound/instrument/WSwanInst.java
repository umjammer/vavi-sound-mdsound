package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.WSwan;


public class WSwanInst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = WSwan.DEFAULT_CLOCK;

    private final WSwan[] chips = {new WSwan(), new WSwan()};

    private final int[] mask = {0, 0};

    public WSwanInst() {
        visVolume = new int[][][] {{{0, 0}, {0, 0}}};
    }

    @Override
    public String getName() {
        return "WonderSwan";
    }

    @Override
    public String getShortName() {
        return "WSwan";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId].init(clock);

        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write((adr + 0x80) & 0xff, data & 0xff);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        for (int i = 0; i < samples; i++) {
            outputs[0][i] = 0;
            outputs[1][i] = 0;

            chips[chipId].update(samples, outputs);
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].stop();
    }

    @Override
    public synchronized void setMask(int chipId, int ch) {
        mask[chipId] |= ch;
    }

    @Override
    public synchronized void resetMask(int chipId, int ch) {
        mask[chipId] &= ~ch;
    }

    // ----

    public synchronized void writeMemory(int chipId, int adr, int data) {
        chips[chipId].writeRam(adr, data);
    }

    //----

    public void setVolume(int vol) {
        // TODO
//        c.volume = Math.max(Math.min(vol, 20), -192);
//        //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / chips.length;
//        int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
//        //16384 = 0x4000 = short.MAXValue + 1
//        c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
    }

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Object... args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "NAME" -> result.put(getName(), "WonderSwan");
            case "FAMILY" -> result.put(getName(), "Bandai custom");
            case "VERSION" -> result.put(getName(), "1.0");
            case "info" -> {
                WSwan chip = chips[chipId];
                result.put("clock", chip.getClock());
                for (int ch = 0; ch < WSwan.CHANNELS; ch++) {
                    result.put("channels." + ch + ".enable", chip.isEnabled(ch));
                    result.put("channels." + ch + ".divider", chip.getDivider(ch));
                    result.put("channels." + ch + ".volumeL", chip.getVolumeL(ch));
                    result.put("channels." + ch + ".volumeR", chip.getVolumeR(ch));
                    result.put("channels." + ch + ".mute", chip.isMuted(ch));
                }
            }
        }
        return result;
    }
}
