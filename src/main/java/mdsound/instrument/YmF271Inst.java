package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.YmF271;


public class YmF271Inst extends Instrument.BaseInstrument implements PcmEnabledInstrument {

    public static final int DefaultClockValue = 16934400;
    public static final int MAX_CHIPS = 0x10;

    private final YmF271[] chips = {new YmF271(), new YmF271()};

    @Override
    public String getName() {
        return "YMF271";
    }

    @Override
    public String getShortName() {
        return "OPX";
    }

    public YmF271Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
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
        chips[chipId].write((port << 1) | 0x00, adr);
        chips[chipId].write((port << 1) | 0x01, data);
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

    //----

    public static final int[] slotTbl = { // TODO public
            0, 24, 12, 36,
            1, 25, 13, 37,
            2, 26, 14, 38,
            3, 27, 15, 39,

            4, 28, 16, 40,
            5, 29, 17, 41,
            6, 30, 18, 42,
            7, 31, 19, 43,

            8, 32, 20, 44,
            9, 33, 21, 45,
            10, 34, 22, 46,
            11, 35, 23, 47,
    };

    private synchronized Map<String, Object> getInfo(int chipId) {
        YmF271 chip = chips[chipId];

        Map<String, Object> newParam = new HashMap<>();
        for (int i = 0; i < 48; i++) {
            int slot = slotTbl[i];

            YmF271.Slot slt = chip.getSlot(slot);
            newParam.put("slots." + slot + ".volume", slt.volume);
            newParam.put("slots." + slot + ".ch0Level", slt.ch0Level);
            newParam.put("slots." + slot + ".ch1Level", slt.ch1Level);
            newParam.put("slots." + slot + ".pan", (slt.ch1Level << 4) | (slt.ch0Level & 0xf));
            newParam.put("slots." + slot + ".pantp", (slt.ch3Level & 0xf0) | ((slt.ch2Level >> 4) & 0xf));
            newParam.put("slots." + slot + ".inst.0", slt.ar);
            newParam.put("slots." + slot + ".inst.1", slt.decay1rate);
            newParam.put("slots." + slot + ".inst.2", slt.decay2rate);
            newParam.put("slots." + slot + ".inst.3", slt.relrate);
            newParam.put("slots." + slot + ".inst.4", slt.decay1lvl);
            newParam.put("slots." + slot + ".inst.5", slt.tl);
            newParam.put("slots." + slot + ".inst.6", slt.keyScale);
            newParam.put("slots." + slot + ".inst.7", slt.multiple);
            newParam.put("slots." + slot + ".inst.8", slt.detune);
            newParam.put("slots." + slot + ".inst.9", slt.waveForm);
            newParam.put("slots." + slot + ".inst.10", slt.feedback);
            newParam.put("slots." + slot + ".inst.11", slt.accon);
            newParam.put("slots." + slot + ".inst.12", slt.algorithm);

            newParam.put("slots." + slot + ".inst.13", slt.block);
            newParam.put("slots." + slot + ".inst.14", slt.fns);

            newParam.put("slots." + slot + ".inst.15", slt.startAddr);
            newParam.put("slots." + slot + ".inst.16", slt.endAddr);
            newParam.put("slots." + slot + ".inst.17", slt.loopAddr);

            newParam.put("slots." + slot + ".inst.18", slt.fs);
            newParam.put("slots." + slot + ".inst.19", slt.bits == 12 ? 1 : 0);
            newParam.put("slots." + slot + ".inst.20", slt.srcNote);
            newParam.put("slots." + slot + ".inst.21", slt.srcb);

            newParam.put("slots." + slot + ".inst.22", slt.lfoFreq);
            newParam.put("slots." + slot + ".inst.23", slt.lfoWave);
            newParam.put("slots." + slot + ".inst.24", slt.pms);
            newParam.put("slots." + slot + ".inst.25", slt.ams);

            newParam.put("slots." + slot + ".active", slt.active != 0);

            if (i % 4 == 0) {
                newParam.put("slots." + slot + ".sync", chip.getSync(i / 4));
            }
        }
        return newParam;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "YMF271");
            case "FAMILY" -> result.put(getName(), "Yamaha FM");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
            case "info" -> { return getInfo(chipId); }
        }
        return result;
    }
}
