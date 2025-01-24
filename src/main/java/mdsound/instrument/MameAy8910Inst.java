/*
 * from libvgm  https://github.com/ValleyBell/libvgm/blob/7f460775717c6287827aa7f13a4599f9c95b7a11/emu/cores/ay8910.c
 *
 * license:BSD-3-Clause
 * copyright-holders:Couriersud
 */

package mdsound.instrument;

import mdsound.Instrument;
import mdsound.chips.Ay8910;


public class MameAy8910Inst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 1789750;

    private int sampleRate = 44100;

    private final Ay8910[] chips = {new Ay8910(), new Ay8910()};

    private int masterClock = DefaultClockValue;
    private double sampleCounter = 0;
    private final int[][] frm = {new int[1], new int[1]};
    private final int[][] before = {new int[1], new int[1]};

    public final int[] mask = {0, 0};

    @Override
    public String getName() {
        return "AY8910mame";
    }

    @Override
    public String getShortName() {
        return "AY10m";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        sampleRate = samplingRate;
        masterClock = clock / 4;
        chips[chipId].init(clock, 0, 0);

        visVolume = new int[2][][];
        visVolume[0] = new int[2][];
        visVolume[1] = new int[2][];
        visVolume[0][0] = new int[2];
        visVolume[1][0] = new int[2];
        visVolume[0][1] = new int[2];
        visVolume[1][1] = new int[2];

        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].writeReg(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        for (int i = 0; i < samples; i++) {
            outputs[0][i] = 0;
            outputs[1][i] = 0;

            sampleCounter += (double) masterClock / sampleRate;
            int upc = (int) sampleCounter;
            while (sampleCounter >= 1) {
                chips[chipId].updateOne(1, frm);

                outputs[0][i] += frm[0][0];
                outputs[1][i] += frm[1][0];

                sampleCounter -= 1.0;
            }

            if (upc != 0) {
                outputs[0][i] /= upc;
                outputs[1][i] /= upc;
                before[0][i] = outputs[0][i];
                before[1][i] = outputs[1][i];
            } else {
                outputs[0][i] = before[0][i];
                outputs[1][i] = before[1][i];
            }
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
        chips[chipId].setMuteMask(mask[chipId]);
    }

    @Override
    public synchronized void resetMask(int chipId, int ch) {
        mask[chipId] &= ~ch;
        chips[chipId].setMuteMask(mask[chipId]);
    }

    // ----

    public void setVolume(int vol) {
        // TODO
//        c.volume = Math.max(Math.min(vol, 20), -192);
//        int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
//        c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
    }
}
