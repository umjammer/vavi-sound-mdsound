/*
 * FM Sound Generator
 *
 * Copyright (C) cisc 1998, 2001.
 */

package mdsound.fmgen;


/**
 * Timer.
 *
 * @author cisc
 */
public abstract class Timer {

    void reset() {
        timerACount = 0;
        timerBCount = 0;
    }

    /**
     * Advance the sound source's timer by t [10^(-6) seconds].
     * Returns true when the internal state of the sound source changes (timer overflow).
     */
    public boolean count(int us) {
        boolean event = false;

        if (timerACount != 0) {
            timerACount -= us << 16;
            if (timerACount <= 0) {
                event = true;
                timerA();

                while (timerACount <= 0)
                    timerACount += timerA;

                if ((regTc & 4) != 0)
                    setStatus(1);
            }
        }
        if (timerBCount != 0) {
            timerBCount -= us << 12;
            if (timerBCount <= 0) {
                event = true;
                while (timerBCount <= 0)
                    timerBCount += timerB;

                if ((regTc & 8) != 0)
                    setStatus(2);
            }
        }
        return event;
    }

    /**
     * Returns the time [μsec] required until one of the sound source's timers overflows.
     * If the timer is stopped, it returns 0.
     */
    public int getNextEvent() {
        int ta = ((timerACount + 0xffff) >> 16) - 1;
        int tb = ((timerBCount + 0xfff) >> 12) - 1;
        return (Math.min(ta, tb)) + 1;
    }

    void setStatus(int bit) {
    }

    void resetStatus(int bit) {
    }

    protected void setTimerBase(int clock) {
        timerStep = (int) (1000000.0 * 65536 / clock);
    }

    protected void setTimerA(int addr, int data) {
        int tmp;
        regTa[addr & 1] = data & 0xff;
        tmp = (regTa[0] << 2) + (regTa[1] & 3);
        timerA = (1024 - tmp) * timerStep;
//logger.log(Level.TRACE, "Timer A = %d   %d us".formatted(tmp, timerA >> 16));
    }

    protected void setTimerB(int data) {
        timerB = (256 - (data & 0xff)) * timerStep;
//logger.log(Level.TRACE, "Timer B = %d   %d us".formatted(data, timerB >> 12));
    }

    protected void setTimerControl(int data) {
        int tmp = regTc ^ (data & 0xff);
        regTc = data & 0xff;

        if ((data & 0x10) != 0)
            resetStatus(1);
        if ((data & 0x20) != 0)
            resetStatus(2);

        if ((tmp & 0x01) != 0)
            timerACount = (data & 1) != 0 ? timerA : 0;
        if ((tmp & 0x02) != 0)
            timerBCount = (data & 2) != 0 ? timerB : 0;
    }

    protected int status;
    protected int regTc;

    private void timerA() {
    }

    private final int[] regTa = new int[2];

    private int timerA, timerACount;
    private int timerB, timerBCount;
    private int timerStep;

    /** the value register {@code 0x26} was set to, worked back out of the timer period */
    int getTimerBRegister() {
        return timerStep == 0 ? 0 : 256 - timerB / timerStep;
    }

    /** the timer control register, {@code 0x27}: bits 6-7 pick channel 3's mode */
    int getTimerControl() {
        return this.regTc;
    }
}
