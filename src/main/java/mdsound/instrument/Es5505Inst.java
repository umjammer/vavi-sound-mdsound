/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import mdsound.Instrument.BaseInstrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.Es5505;
import vavi.util.compat.Tuple;


/**
 * Es5505Inst.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-02 nsano initial version <br>
 */
public class Es5505Inst extends BaseInstrument implements PcmEnabledInstrument {

    private final Es5505[] chips = {new Es5505(), new Es5505()};

    @Override
    public String getName() {
        return "Ensoniq ES5505";
    }

    @Override
    public String getShortName() {
        return "ES5505";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    /** @param option 0: number of output channels (optional) */
    @Override
    public int start(int chipId, int sampleRate, int clock, Object... option) {
        int channels = option.length > 0 && option[0] instanceof Integer c ? c : 1;
        return chips[chipId].start(clock, channels);
    }

    @Override
    public int read(int chipId, int adr) {
        return chips[chipId].read8(adr & 0x7f);
    }

    /**
     * the same as VGM command 0xbe and 0xd6
     *
     * @param adr bit 7: 16 bit data, bit 6-0: byte offset
     */
    @Override
    public int write(int chipId, int port, int adr, int data) {
        if ((adr & 0x80) != 0)
            chips[chipId].write16(adr & 0x7f, data & 0xffff);
        else
            chips[chipId].write8(adr & 0x7f, data & 0xff);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].update(outputs, samples);
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].stop();
    }

    @Override
    public void setMask(int chipId, int ch) {
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }

    @Override
    public void setSamplingRateCallback(int chipId, Consumer<Integer> callback) {
        chips[chipId].setSampleRateChanged(callback);
    }

    public void setMute(int chipId, int v) {
        chips[chipId].setMuteMask(v);
    }

    /**
     * the same as VGM data block 0x90
     *
     * @param offset data start, bit 31: 8 bit rom, bit 29-28: region
     * @param extras 0: srcOffset, 1: romSize
     */
    @Override
    public void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
        int srcOffset = (int) extras[0];
        int romSize = (int) extras[1];
        chips[chipId].writeRom(romSize, offset, length, buf, srcOffset);
    }

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x20, 16d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Object... args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "info" -> {
                Es5505 chip = chips[chipId];
                for (int v = 0; v < Es5505.VOICES; v++) {
                    result.put("channels." + v + ".enable", chip.isEnabled(v));
                    result.put("channels." + v + ".frequency", chip.getFrequency(v));
                    result.put("channels." + v + ".volumeL", chip.getVolumeL(v));
                    result.put("channels." + v + ".volumeR", chip.getVolumeR(v));
                    result.put("channels." + v + ".output", chip.getOutput(v));
                    result.put("channels." + v + ".mute", chip.isMuted(v));
                }
            }
        }
        return result;
    }
}
