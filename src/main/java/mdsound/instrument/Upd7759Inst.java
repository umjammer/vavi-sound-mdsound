/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.Upd7759;


/**
 * Upd7759Inst.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-02 nsano initial version <br>
 */
public class Upd7759Inst extends Instrument.BaseInstrument implements PcmEnabledInstrument {

    private static final int MAX_CHIPS = 0x02;

    private final Upd7759[] chips = {new Upd7759(), new Upd7759()};

    public Upd7759Inst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "uPD7759";
    }

    @Override
    public String getShortName() {
        return "uPD7759";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].device_reset_upd7759();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        return chips[chipId].device_start_upd7759(clock);
    }

    @Override
    public int read(int chipId, int adr) {
        return 0;
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].upd7759_write(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].upd7759_update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].device_stop_upd7759();
    }

    @Override
    public void setMask(int chipId, int ch) {
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }

    /** @param extras 0: srcOffset */
    @Override
    public void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
        int srcOffset = (int) extras[0];
        int romSize = (int) extras[1];
        chips[chipId].writeRom(romSize, offset, length, buf, srcOffset);
    }

    public Upd7759 getChip(int ChipID) {
        return chips[ChipID];
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x11e, 1d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "UPD7759");
            case "FAMILY" -> result.put(getName(), "NEC ADPCM");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
