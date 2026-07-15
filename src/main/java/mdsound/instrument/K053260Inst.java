package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.K053260;


public class K053260Inst extends Instrument.BaseInstrument implements PcmEnabledInstrument {

    public static final int MAX_CHIPS = 0x02;
    public static final int DefaultClockValue = 3579545;

    private final K053260[] chips = {new K053260(), new K053260()};

    public K053260Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "K053260";
    }

    @Override
    public String getShortName() {
        return "K053";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;
        return chips[chipId].start(clock);
    }

    @Override
    public int read(int chipId, int adr) {
        return chips[chipId].read(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].stop();
    }

    @Override
    public void setMask(int chipId, int ch) {
//        chips[chipId].setMuteMask(ch); // TODO
    }

    @Override
    public void resetMask(int chipId, int ch) {
//        chips[chipId].setMuteMask(~ch); // TODO
    }

    /** @param extras 0: srcOffset, 1: romSize */
    @Override
    public synchronized void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
        int srcOffset = (int) extras[0];
        int romSize = (int) extras[1];
        chips[chipId].writeRom(romSize, offset, length, buf, srcOffset);
    }

    /** what the K053260 keyboard view reads each frame: raw per-channel register state. */
    public synchronized Map<String, Object> getInfo(int chipId) {
        K053260 chip = chips[chipId];

        Map<String, Object> info = new HashMap<>();
        info.put("clock", chip.getClock());
        for (int ch = 0; ch < 4; ch++) {
            info.put("channels." + ch + ".freq", chip.getRate(ch));
            info.put("channels." + ch + ".size", chip.getSize(ch));
            info.put("channels." + ch + ".start", chip.getStart(ch));
            info.put("channels." + ch + ".bank", chip.getBank(ch));
            info.put("channels." + ch + ".volume", chip.getVolume(ch));
            info.put("channels." + ch + ".pan", chip.getPan(ch));
            info.put("channels." + ch + ".play", chip.getPlay(ch));
            info.put("channels." + ch + ".dir", chip.getDir(ch));
            info.put("channels." + ch + ".loop", chip.getLoop(ch));
            info.put("channels." + ch + ".ppcm", chip.getPpcm(ch));
            info.put("channels." + ch + ".delta", chip.getDelta(ch));
        }
        return info;
    }

    // ----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0xB3, 1d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "K053260");
            case "FAMILY" -> result.put(getName(), "Konami custom");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
