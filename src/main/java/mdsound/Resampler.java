/*
 * https://github.com/kuma4649/MDSound
 */

package mdsound;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Arrays;
import java.util.List;

import mdsound.MDSound.Chip;

import static java.lang.System.getLogger;


/**
 * Resampler.
 */
class Resampler {

    private static final Logger logger = getLogger(Resampler.class.getName());

    private static final int DefaultSamplingRate = 44100;
    private static final int DefaultSamplingBuffer = 512;

    private static final int FIXPNT_BITS = 11;
    private static final int FIXPNT_FACT = (1 << FIXPNT_BITS);
    private static final int FIXPNT_MASK = (FIXPNT_FACT - 1);

    private int resampleMode = 0;

    private int samplingRate = DefaultSamplingRate;
    private int samplingBuffer = DefaultSamplingBuffer;
    private int[][] streamBufs = null;

    private final int[][] buff = {new int[1], new int[1]};

    private int[][] tempSample = {new int[1], new int[1]};
    private int[][] streamPnt = {new int[0x100], new int[0x100]};
    private int clearLength = 1;

    private List<Chip> insts;

    private static int getFriction(int x) {
        return x & FIXPNT_MASK;
    }

    private static int getnFriction(int x) {
        return (FIXPNT_FACT - (x)) & FIXPNT_MASK;
    }

    private static int fpi_floor(int x) {
        return x & ~FIXPNT_MASK;
    }

    private static int fpi_ceil(int x) {
        return (x + FIXPNT_MASK) & ~FIXPNT_MASK;
    }

    private static int fp2i_floor(long x) {
        return (int) (x / FIXPNT_FACT);
    }

    private static int fp2i_ceil(long x) {
        return (int) ((x + FIXPNT_MASK) / FIXPNT_FACT);
    }

    public int getResampleMode() {
        return resampleMode;
    }

    /** */
    public void init(List<Chip> insts, int samplingRate, int samplingBuffer) {
        this.insts = insts;
        this.samplingRate = samplingRate;
        this.samplingBuffer = samplingBuffer;
        streamBufs = new int[][] {new int[0x100], new int[0x100]};
    }

private int CC = 0;
private static int INTERVAL = 1024;

    private Chip inst;
    private int[] curBufL;
    private int[] curBufR;
    private int inBase;
    private int inPos;
    private int inPosNext;
    private int outPos;
    private int smpFrc; // Sample Friction
    private int inPre = 0;
    private int inNow;
    private long inPosL;
    private int tempSmpL;
    private int tempSmpR;
    private int tempS32L;
    private int tempS32R;
    private int smpCnt; // must be signed, else I'm getting calculation errors
    private int curSmpl;
    private int chipSmpRate;

    private int mul;

private boolean noInst = false;

    /** */
    public void resample(int[][] retSample, int length) {
        if (insts == null || insts.isEmpty()) {
if (!noInst) {
 logger.log(Level.WARNING, "no insts");
 noInst = true;
}
            return;
        }
        if (length > tempSample[0].length) {
            tempSample = new int[][] {new int[length], new int[length]};
        }
        if (length > streamPnt[0].length) {
            streamPnt = new int[][] {new int[length], new int[length]};
        }

        // This Do-While-Loop gets and resamples the chips output of one or more chips.
        // It's a loop to support the AY8910 paired with the Ym2203Inst/Ym2608Inst/Ym2610Inst.
        for (Chip chip : insts) {
            Arrays.fill(streamBufs[0], 0);
            Arrays.fill(streamBufs[1], 0);
            curBufL = streamBufs[0x00];
            curBufR = streamBufs[0x01];

            this.inst = chip;
            mul = inst.tVolume;

//if (i != 0 && chips[i].LSmpl[0] != 0) logger.log(Level.DEBUG, "%d %d".formatted(chips[i].LSmpl[0], chips[0].LSmpl == chips[i].LSmpl));
//logger.log(Level.TRACE, "%s, resample: %d, mul: %d".formatted(inst.instrument.getName(), inst.resampler, mul));
//logger.log(Level.TRACE, "resampler: %d".formatted(inst.resampler));
            switch (inst.resampler) {
                case 0x00: // old, but very fast resampler
                    old(length);
                    break;
                case 0x01: // up sampling
                    up(length);
                    break;
                case 0x02: // copying
                    copy(length);
                    break;
                case 0x03: // down sampling
                    down(length);
                    break;
                default:
                    inst.smpP += samplingRate;
                    break; // do absolutely nothing
            }

            if (inst.smpLast >= inst.samplingRate) {
                inst.smpLast -= inst.samplingRate;
                inst.smpNext -= inst.samplingRate;
                inst.smpP -= samplingRate;
            }

            if (inst.additionalUpdate != null) {
                inst.additionalUpdate.accept(inst, inst.id, tempSample, length);
            }
            for (int j = 0; j < length; j++) {
                retSample[0][j] += tempSample[0][j];
                retSample[1][j] += tempSample[1][j];
            }
//if (tempSample[0][0] != 0) logger.log(Level.DEBUG, "%d %d %d".formatted(i, tempSample[0][0], inst.resampler));
//logger.log(Level.TRACE, "%s: %04x, %04x".formatted(inst.instrument.getName(), streamBufs[0][0], streamBufs[1][0]));
        }
CC++;
    }

    /** old, but very fast resampler */
    private void old(int length) {
        inst.smpLast = inst.smpNext;
        inst.smpP += length;
        inst.smpNext = inst.smpP * inst.samplingRate / samplingRate;
        if (inst.smpLast >= inst.smpNext) {
            tempSample[0][0] = Math.clamp((inst.lSmpl[0] * mul) >> 15, -0x8000, 0x7fff);
            tempSample[1][0] = Math.clamp((inst.lSmpl[1] * mul) >> 15, -0x8000, 0x7fff);
        } else {
            smpCnt = inst.smpNext - inst.smpLast;
            clearLength = smpCnt;
            for (int ind = 0; ind < smpCnt; ind++) {
                buff[0][0] = 0;
                buff[1][0] = 0;
                inst.instrument.update(inst.id, buff, 1);

                streamBufs[0][ind] += Math.clamp((buff[0][0] * mul) >> 15, -0x8000, 0x7fff);
                streamBufs[1][ind] += Math.clamp((buff[1][0] * mul) >> 15, -0x8000, 0x7fff);
if ((CC % INTERVAL) == 0) {
 logger.log(Level.DEBUG, "%s[%d] O: %+04d, %+04d".formatted(inst.instrument.getName(), ind, streamBufs[0][ind], streamBufs[1][ind]));
}
            }

            if (smpCnt == 1) {
                tempSample[0][0] = Math.clamp((curBufL[0] * mul) >> 15, -0x8000, 0x7fff);
                tempSample[1][0] = Math.clamp((curBufR[0] * mul) >> 15, -0x8000, 0x7fff);

                inst.lSmpl[0] = curBufL[0x00];
                inst.lSmpl[1] = curBufR[0x00];
            } else if (smpCnt == 2) {
                tempSample[0][0] = Math.clamp(((curBufL[0] + curBufL[1]) * mul) >> (15 + 1), -0x8000, 0x7fff);
                tempSample[1][0] = Math.clamp(((curBufR[0] + curBufR[1]) * mul) >> (15 + 1), -0x8000, 0x7fff);

                inst.lSmpl[0] = curBufL[0x01];
                inst.lSmpl[1] = curBufR[0x01];
            } else {
                tempS32L = curBufL[0x00];
                tempS32R = curBufR[0x00];
                for (curSmpl = 0x01; curSmpl < smpCnt; curSmpl++) {
                    tempS32L += curBufL[curSmpl];
                    tempS32R += curBufR[curSmpl];
                }
                tempSample[0][0] = Math.clamp(((tempS32L * mul) >> 15) / smpCnt, -0x8000, 0x7fff);
                tempSample[1][0] = Math.clamp(((tempS32R * mul) >> 15) / smpCnt, -0x8000, 0x7fff);

                inst.lSmpl[0] = curBufL[smpCnt - 1];
                inst.lSmpl[1] = curBufR[smpCnt - 1];
            }
        }
    }

    /** up sampling */
    private void up(int length) {
        chipSmpRate = inst.samplingRate;
        inPosL = (long) FIXPNT_FACT * inst.smpP * chipSmpRate / samplingRate;
        inPre = fp2i_floor(inPosL);
        inNow = fp2i_ceil(inPosL);

//logger.log(Level.TRACE, "inPosL=%d, inst.smpP=%d, inPre=%d, inNow=%d, inst.SmpNext=%d".formatted(inPosL, inst.smpP, inPre, inNow, inst.smpNext));

        curBufL[0x00] = inst.lSmpl[0];
        curBufR[0x00] = inst.lSmpl[1];
        curBufL[0x01] = inst.nSmpl[0];
        curBufR[0x01] = inst.nSmpl[1];
        for (int ind = 0; ind < (inNow - inst.smpNext); ind++) {
            streamPnt[0x00][ind] = curBufL[0x02 + ind];
            streamPnt[0x01][ind] = curBufR[0x02 + ind];
        }
        for (int ind = 0; ind < (inNow - inst.smpNext); ind++) {
            buff[0][0] = 0;
            buff[1][0] = 0;
            inst.instrument.update(inst.id, buff, 1);

            streamPnt[0][0] = Math.clamp((buff[0][0] * mul) >> 15, -0x8000, 0x7fff);
            streamPnt[1][0] = Math.clamp((buff[1][0] * mul) >> 15, -0x8000, 0x7fff);
if ((CC % INTERVAL) == 0) {
 logger.log(Level.DEBUG, "%s[%d] U: %+04d, %+04d".formatted(inst.instrument.getName(), ind, streamPnt[0][0], streamPnt[1][0]));
}
        }
        for (int ind = 0; ind < inNow - inst.smpNext; ind++) {
            curBufL[0x02 + ind] = streamPnt[0x00][ind];
            curBufR[0x02 + ind] = streamPnt[0x01][ind];
        }

        inBase = (int) (FIXPNT_FACT + (inPosL - inst.smpNext * FIXPNT_FACT));
        smpCnt = FIXPNT_FACT;
        inst.smpLast = inPre;
        inst.smpNext = inNow;
        for (outPos = 0x00; outPos < length; outPos++) {
            inPos = inBase + (FIXPNT_FACT * outPos * chipSmpRate / samplingRate);

            inPre = fp2i_floor(inPos);
            inNow = fp2i_ceil(inPos);
            smpFrc = getFriction(inPos);

            // linear interpolation
            tempSmpL = (curBufL[inPre] * (FIXPNT_FACT - smpFrc)) +
                    (curBufL[inNow] * smpFrc);
            tempSmpR = (curBufR[inPre] * (FIXPNT_FACT - smpFrc)) +
                    (curBufR[inNow] * smpFrc);
            tempSample[0][outPos] = tempSmpL / smpCnt;
            tempSample[1][outPos] = tempSmpR / smpCnt;
        }
        inst.lSmpl[0] = curBufL[inPre];
        inst.lSmpl[1] = curBufR[inPre];
        inst.nSmpl[0] = curBufL[inNow];
        inst.nSmpl[1] = curBufR[inNow];
        inst.smpP += length;
    }

    /** copying */
    private void copy(int length) {
        inst.smpNext = inst.smpP * inst.samplingRate / samplingRate;
        clearLength = length;
        for (int ind = 0; ind < length; ind++) {
            buff[0][0] = 0;
            buff[1][0] = 0;
            inst.instrument.update(inst.id, buff, 1);

            streamBufs[0][ind] = Math.clamp((buff[0][0] * mul) >> 15, -0x8000, 0x7fff);
            streamBufs[1][ind] = Math.clamp((buff[1][0] * mul) >> 15, -0x8000, 0x7fff);
if ((CC % INTERVAL) == 0) {
 logger.log(Level.DEBUG, "%s[%d] C: %+04d, %+04d".formatted(inst.instrument.getName(), ind, streamBufs[0][ind], streamBufs[1][ind]));
}
        }
        for (outPos = 0x00; outPos < length; outPos++) {
            tempSample[0][outPos] = curBufL[outPos];
            tempSample[1][outPos] = curBufR[outPos];
        }
        inst.smpP += length;
        inst.smpLast = inst.smpNext;
    }

    /** down sampling */
    private void down(int length) {
        chipSmpRate = inst.samplingRate;
        inPosL = (long) FIXPNT_FACT * (inst.smpP + length) * chipSmpRate / samplingRate;
        inst.smpNext = fp2i_ceil(inPosL);

        curBufL[0x00] = inst.lSmpl[0];
        curBufR[0x00] = inst.lSmpl[1];

        for (int ind = 0; ind < (inst.smpNext - inst.smpLast); ind++) {
            streamPnt[0x00][ind] = curBufL[0x01 + ind];
            streamPnt[0x01][ind] = curBufR[0x01 + ind];
        }
        for (int ind = 0; ind < (inst.smpNext - inst.smpLast); ind++) {
            buff[0][0] = 0;
            buff[1][0] = 0;
            inst.instrument.update(inst.id, buff, 1);

            streamPnt[0][ind] = Math.clamp((buff[0][0] * mul) >> 15, -0x8000, 0x7fff);
            streamPnt[1][ind] = Math.clamp((buff[1][0] * mul) >> 15, -0x8000, 0x7fff);
if ((CC % INTERVAL) == 0) {
 logger.log(Level.DEBUG, "%s[%d] D: %+04d, %+04d".formatted(inst.instrument.getName(), ind, streamPnt[0][0], streamPnt[1][0]));
}
        }
        for (int ind = 0; ind < inst.smpNext - inst.smpLast; ind++) {
            curBufL[0x01 + ind] = streamPnt[0x00][ind];
            curBufR[0x01 + ind] = streamPnt[0x01][ind];
        }

        inPosL = (long) FIXPNT_FACT * inst.smpP * chipSmpRate / samplingRate;
        // I'm adding 1.0 to avoid negative indexes
        inBase = (int) (FIXPNT_FACT + (inPosL - inst.smpLast * FIXPNT_FACT));
        inPosNext = inBase;
        for (outPos = 0x00; outPos < length; outPos++) {
            inPos = inPosNext;
            inPosNext = inBase + (int) (((long) FIXPNT_FACT * (outPos + 1) * chipSmpRate) / samplingRate);

            // first frictional Sample
            smpFrc = getnFriction(inPos);
            if (smpFrc != 0) {
                inPre = fp2i_floor(inPos);
                tempSmpL = curBufL[inPre] * smpFrc;
                tempSmpR = curBufR[inPre] * smpFrc;
            } else {
                tempSmpL = tempSmpR = 0x00;
            }
            smpCnt = smpFrc;

            // last frictional Sample
            smpFrc = getFriction(inPosNext);
            inPre = fp2i_floor(inPosNext);
            if (smpFrc != 0) {
                tempSmpL += curBufL[Math.clamp(inPre, 0, curBufL.length)] * smpFrc;
                tempSmpR += curBufR[Math.clamp(inPre, 0, curBufL.length)] * smpFrc;
                smpCnt += smpFrc;
            }

            // whole Samples in between
            inNow = fp2i_ceil(inPos);
            smpCnt += (inPre - inNow) * FIXPNT_FACT; // this is faster
            while (inNow < inPre) {
                tempSmpL += curBufL[inNow] * FIXPNT_FACT;
                tempSmpR += curBufR[inNow] * FIXPNT_FACT;
                inNow++;
            }

            tempSample[0][outPos] = tempSmpL / smpCnt;
            tempSample[1][outPos] = tempSmpR / smpCnt;
        }

        inst.lSmpl[0] = curBufL[Math.clamp(inPre, 0, curBufL.length)];
        inst.lSmpl[1] = curBufR[Math.clamp(inPre, 0, curBufL.length)];
        inst.smpP += length;
        inst.smpLast = inst.smpNext;
    }
}
