/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import mdsound.Instrument;
import mdsound.chips.Ym3438;
import mdsound.chips.Ym3438Const;

import static uk.co.omgdrv.simplevgm.fm.MdFmProvider.FM_STATUS_BUSY_BIT_MASK;


/**
 * Ym3438 (OPN2 (cmos)) NukeYKT (vavi: (simple(buffering) + mdsound(core))) version.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-01-24 nsano initial version <br>
 */
public class VaviYm3438Inst extends Instrument.BaseInstrument {

    private final Ym3438[] chips = {new Ym3438(), new Ym3438()};

    private final int[][] ym3438_accm = new int[24][2];
    int ym3438_cycles = 0;
    int[] ym3438_sample = new int[2];

    double rateRatioAcc = 0;
    double sampleRateCalcAcc = 0;

    private final Queue<Integer> commandQueue = new ConcurrentLinkedQueue<>();
    private final AtomicLong queueSize = new AtomicLong();

    private static final int MASTER_CLOCK_HZ = 7_670_442; // MD_NTSC FM CLOCK
    private static final int CLOCK_HZ = MASTER_CLOCK_HZ / 6;
    private static double CYCLE_PER_MS = CLOCK_HZ / 1000.0;
    private final static double rateRatio = 44100 / 1000.0 / CYCLE_PER_MS;

    private final int[] mask = {0, 0};

    public VaviYm3438Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "YM3438vavi(" + Ym3438Const.chip_type + ")";
    }

    @Override
    public String getShortName() {
        return "OPN2cmos";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset(0, 0);
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId].reset(samplingRate, clock);
        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized int write(int chipId, int port, int adr, int data) {
        writeInternal(0 + (port & 1) * 2, adr);
        writeInternal(1 + (port & 1) * 2, data);
        return 0;
    }

    private void writeInternal(int addr, int data) {
//logger.log(Level.DEBUG, "addr: " + addr + ", data: " + data);
        commandQueue.offer(addr);
        queueSize.addAndGet(1);
        commandQueue.offer(data);
        queueSize.addAndGet(1);
    }

    private boolean isReadyWrite(int chipId) {
        boolean isBusyState = (chips[chipId].read(0) & FM_STATUS_BUSY_BIT_MASK) > 0;
        boolean isWriteInProgress = chips[chipId].isWriteAddrEn() || chips[chipId].isWriteDataEn();
        return !isBusyState && !isWriteInProgress;
    }

    private void spinOnce(int chipId) {
        if (queueSize.get() > 1 && isReadyWrite(chipId)) {
            chips[chipId].write(commandQueue.poll(), commandQueue.poll());
            queueSize.addAndGet(-2);
        }
        chips[chipId].clock(ym3438_accm[ym3438_cycles]);
        ym3438_cycles = (ym3438_cycles + 1) % 24;
        if (ym3438_cycles == 0) {
            ym3438_sample[0] = 0;
            ym3438_sample[1] = 0;
            for (int j = 0; j < 24; j++) {
                ym3438_sample[0] += ym3438_accm[j][0];
                ym3438_sample[1] += ym3438_accm[j][1];
            }
            lastL = (lastL + ym3438_sample[0]) >> 1;
            lastR = (lastR + ym3438_sample[1]) >> 1;
        }
    }

    private int lastL = 0;
    private int lastR = 0;

    private void updateInternal(int chipId, int[] buf_lr, int offset, int samples) {
        offset <<= 1;
        sampleRateCalcAcc += samples / rateRatio;
        int total = (int) (sampleRateCalcAcc + 1);
        for (int i = 0; i < total; i++) {
            spinOnce(chipId);
            rateRatioAcc += rateRatio;
            if (rateRatioAcc > 1) {
                buf_lr[offset++] = lastL << 4;
                buf_lr[offset++] = lastR << 4;
//logger.log(Level.DEBUG, (lastL << 4) + ", " + (lastR << 4));
                rateRatioAcc--;
            }
        }
        sampleRateCalcAcc -= total;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        int[] buffer = new int[4];
        for (int i = 0; i < samples; i++) {
            updateInternal(chipId, buffer, 0, 1);
            outputs[0][i] = buffer[0];
            outputs[1][i] = buffer[1];
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].reset(0, 0);
    }

    // TODO 2612
    @Override
    public synchronized void setMask(int chipId, int ch) {
        mask[chipId] |= 1 << ch;
        int mask = this.mask[chipId];
        if ((mask & 0b0010_0000) == 0) mask &= 0b1011_1111;
        else mask |= 0b0100_0000;
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }
}

