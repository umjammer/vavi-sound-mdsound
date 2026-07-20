/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.C219;

import static java.lang.System.getLogger;


/**
 * C219.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-15 nsano initial version <br>
 */
public class C219Inst extends Instrument.BaseInstrument implements PcmEnabledInstrument {

    private static final Logger logger = getLogger(C219Inst.class.getName());

    public static final int MAX_CHIPS = 0x02;

    private final C219[] chips = {new C219(), new C219()};

    private final int[] mask = {0, 0};

    public C219Inst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "C219";
    }

    @Override
    public String getShortName() {
        return "C219";
    }

    @Override
    public void init() {
        mask[0] = 0;
        mask[1] = 0;
    }

    @Override
    public void reset(int chipId) {
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        int sampleRete = chips[chipId].start(clock);
logger.log(Level.DEBUG, "sampleRate: " + sampleRete);
        return sampleRete;
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
        chips[chipId].update(samples, outputs);

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
        chips[chipId].setMuteMask(mask[chipId]);
    }

    @Override
    public synchronized void resetMask(int chipId, int ch) {
        mask[chipId] &= ~(int) ch;
        chips[chipId].setMuteMask(mask[chipId]);
    }

    /** @param extras 0: srcOffset. 1: romSize */
    @Override
    public synchronized void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
        int srcOffset = (int) extras[0];
        int romSize = (int) extras[1];
        chips[chipId].writeRom(offset, length, buf, srcOffset, romSize);
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
            case "info" -> {
                C219 chip = chips[chipId];
                int[] regs = chip.getRegisters();
                // the C140 side of this pair reports bytes, so hand back the same shape
                byte[] copy = new byte[regs.length];
                for (int i = 0; i < regs.length; i++) copy[i] = (byte) regs[i];
                result.put("register", copy);
                for (int v = 0; v < C219.VOICES; v++) {
                    result.put("channels." + v + ".keyOn", chip.isKeyOn(v));
                    result.put("channels." + v + ".mute", chip.isMuted(v));
                }
            }
        }
        return result;
    }
}