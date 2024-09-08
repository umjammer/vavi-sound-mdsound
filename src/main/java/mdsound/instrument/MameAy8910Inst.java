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

    private final Ay8910[] chip = new Ay8910[2];
    private static final int DefaultAY8910ClockValue = 1789750;
    private int sampleRate = 44100;
    private int masterClock = DefaultAY8910ClockValue;
    private double sampleCounter = 0;
    private final int[][] frm = new int[][] {new int[1], new int[1]};
    private final int[][] before = new int[][] {new int[1], new int[1]};

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
        chip[chipId].reset();
    }

    @Override
    public int start(int chipId, int clock) {
        return start(chipId, clock, DefaultAY8910ClockValue);
    }

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        Ay8910 ch = new Ay8910();
        sampleRate = clock;
        masterClock = clockValue / 4;
        ch.init(clockValue, (byte) 0, (byte) 0);

        chip[chipId] = ch;

        visVolume = new int[2][][];
        visVolume[0] = new int[2][];
        visVolume[1] = new int[2][];
        visVolume[0][0] = new int[2];
        visVolume[1][0] = new int[2];
        visVolume[0][1] = new int[2];
        visVolume[1][1] = new int[2];

        return clock;
    }

    @Override
    public void stop(int chipId) {
        chip[chipId].stop();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        for (int i = 0; i < samples; i++) {
            outputs[0][i] = 0;
            outputs[1][i] = 0;

            sampleCounter += (double) masterClock / sampleRate;
            int upc = (int) sampleCounter;
            while (sampleCounter >= 1) {
                chip[chipId].updateOne(1, frm);

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

            //outputs[0][i] <<= 0;
            //outputs[1][i] <<= 0;
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chip[chipId].writeReg(adr, data);
        return 0;
    }

    public void setMute(int chipId, int mask) {
        chip[chipId].setMuteMask(mask);
    }
}
