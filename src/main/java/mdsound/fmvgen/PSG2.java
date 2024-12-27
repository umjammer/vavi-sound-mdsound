package mdsound.fmvgen;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import mdsound.fmvgen.effect.ReversePhase;


public class PSG2 extends mdsound.fmgen.PSG {

    protected int[] panpot = new int[3];
    protected int[] phaseReset = new int[3];
    protected boolean[] phaseResetBefore = new boolean[3];
    protected int[] duty = new int[3];
    private final Fmvgen.Effects effects;
    private final int efcStartCh;
    private final byte[][] user = new byte[][] {new byte[64], new byte[64], new byte[64], new byte[64], new byte[64], new byte[64]};
    private int userDefCounter = 0;
    private final BiFunction<Integer, Integer, Integer>[] tblGetSample;
    private final int num;
    protected double ncountDbl;
    private static final double ncountDiv = 32.0;

    {
        List<BiFunction<Integer, Integer, Integer>> a = Arrays.asList(
                this::getSampleFromDuty,
                this::getSampleFromDuty,
                this::getSampleFromDuty,
                this::getSampleFromDuty,
                this::getSampleFromDuty,
                this::getSampleFromDuty,
                this::getSampleFromDuty,
                this::getSampleFromDuty,
                this::getSampleFromTriangle,
                this::getSampleFromSaw,
                this::getSampleFromUserDef,
                this::getSampleFromUserDef,
                this::getSampleFromUserDef,
                this::getSampleFromUserDef,
                this::getSampleFromUserDef,
                this::getSampleFromUserDef
        );
        tblGetSample = a.toArray(BiFunction[]::new);
    }

    public PSG2(int num, Fmvgen.Effects effects, int efcStartCh) {
        this.num = num;
        this.effects = effects;
        this.efcStartCh = efcStartCh;
    }

    @Override
    public void setReg(int regNum, int data) {
        if (regNum >= 0x10) return;

        reg[regNum] = (byte) data;
        int tmp;
        switch (regNum) {
        case 0: // ChA Fine Tune
        case 1: // ChA Coarse Tune
            tmp = ((reg[0] & 0xff) + (reg[1] & 0xff) * 256) & 0xfff;
            sPeriod[0] = tmp != 0 ? tPeriodBase / tmp : tPeriodBase;
            duty[0] = (reg[1] & 0xff) >> 4;
            duty[0] = duty[0] < 8 ? (7 - duty[0]) : duty[0];
            break;

        case 2: // ChB Fine Tune
        case 3: // ChB Coarse Tune
            tmp = (((reg[2] & 0xff) + (reg[3] & 0xff) * 256) & 0xfff);
            sPeriod[1] = tmp != 0 ? tPeriodBase / tmp : tPeriodBase;
            duty[1] = (reg[3] & 0xff) >> 4;
            duty[1] = duty[1] < 8 ? (7 - duty[1]) : duty[1];
            break;

        case 4: // ChC Fine Tune
        case 5: // ChC Coarse Tune
            tmp = (((reg[4] & 0xff) + (reg[5] & 0xff) * 256) & 0xfff);
            sPeriod[2] = tmp != 0 ? tPeriodBase / tmp : tPeriodBase;
            duty[2] = (reg[5] & 0xff) >> 4;
            duty[2] = duty[2] < 8 ? (7 - duty[2]) : duty[2];
            break;

        case 6: // Noise generator control
            data &= 0x1f;
            nPeriod = data != 0 ? nPeriodBase / data : nPeriodBase;
            break;

        case 7:
            if ((data & 0x09) == 0) {
                phaseResetBefore[0] = false;
            }
            if ((data & 0x12) == 0) {
                phaseResetBefore[1] = false;
            }
            if ((data & 0x24) == 0) {
                phaseResetBefore[2] = false;
            }
            break;
        case 8:
            oLevel[0] = (mask & 1) != 0 ? emitTable[(data & 15) * 2 + 1] : 0;
            panpot[0] = data >> 6;
            panpot[0] = panpot[0] == 0 ? 3 : panpot[0];
            phaseReset[0] = (byte) ((data & 0x20) != 0 ? 1 : 0);
            break;

        case 9:
            oLevel[1] = (mask & 2) != 0 ? emitTable[(data & 15) * 2 + 1] : 0;
            panpot[1] = data >> 6;
            panpot[1] = panpot[1] == 0 ? 3 : panpot[1];
            phaseReset[1] = (byte) ((data & 0x20) != 0 ? 1 : 0);
            break;

        case 10:
            oLevel[2] = (mask & 4) != 0 ? emitTable[(data & 15) * 2 + 1] : 0;
            panpot[2] = data >> 6;
            panpot[2] = panpot[2] == 0 ? 3 : panpot[2];
            phaseReset[2] = (data & 0x20) != 0 ? 1 : 0;
            break;

        case 11: // Envelop period
        case 12:
            tmp = ((reg[11] & 0xff) + (reg[12] & 0xff) * 256) & 0xffff;
            ePeriod = tmp != 0 ? ePeriodBase / tmp : ePeriodBase * 2;
            break;

        case 13: // Envelop shape
            eCount = 0;
            envelop = envelopTable[data & 15];
            break;

        case 14: // Define Wave data
            if ((data & 0x80) != 0) userDefCounter = 0;
            user[((data & 0x70) >> 4) % 6][userDefCounter & 63] = (byte) (data & 0xf);
            //logger.log(Level.TRACE, "%d : WF %d %d %d ", ((data & 0x70) >> 4) % 6, userDefCounter & 63, (byte)(data & 0xf), data);
            userDefCounter++;
            break;
        }
    }

    private final int[] chEnable = new int[3];
    private final int[] nEnable = new int[3];
    private final Integer[] p = new Integer[3];

    @Override
    public void mix(int[] dest, int nSamples) {
        int r7 = ~(reg[7] & 0xff);

        if (((r7 & 0x3f) | ((reg[8] | reg[9] | reg[10]) & 0x1f)) != 0) {
            chEnable[0] = (((r7 & 0x01) != 0) && (sPeriod[0] <= (1 << toneShift))) ? 15 : 0;
            chEnable[1] = (((r7 & 0x02) != 0) && (sPeriod[1] <= (1 << toneShift))) ? 15 : 0;
            chEnable[2] = (((r7 & 0x04) != 0) && (sPeriod[2] <= (1 << toneShift))) ? 15 : 0;
            nEnable[0] = (r7 & 0x08) != 0 ? 1 : 0;
            nEnable[1] = (r7 & 0x10) != 0 ? 1 : 0;
            nEnable[2] = (r7 & 0x20) != 0 ? 1 : 0;
            p[0] = ((mask & 1) != 0 && (reg[8] & 0x10) != 0) ? null : 0;
            p[1] = ((mask & 2) != 0 && (reg[9] & 0x10) != 0) ? null : 1;
            p[2] = ((mask & 4) != 0 && (reg[10] & 0x10) != 0) ? null : 2;
            if (!phaseResetBefore[0] && phaseReset[0] != 0 && (r7 & 0x09) != 0) {
                sCount[0] = 0;
                phaseResetBefore[0] = true;
            }
            if (!phaseResetBefore[1] && phaseReset[1] != 0 && (r7 & 0x12) != 0) {
                sCount[1] = 0;
                phaseResetBefore[1] = true;
            }
            if (!phaseResetBefore[2] && phaseReset[2] != 0 && (r7 & 0x24) != 0) {
                sCount[2] = 0;
                phaseResetBefore[2] = true;
            }

            int noise, sample, sampleL, sampleR, revSampleL, revSampleR;
            int env;
            int nv = 0;

            if (p[0] != null && p[1] != null && p[2] != null) {
                // No Envelope
                if ((r7 & 0x38) == 0) {
                    int ptrDest = 0;
                    // Noiseless
                    for (int i = 0; i < nSamples; i++) {
                        sampleL = 0;
                        sampleR = 0;
                        revSampleL = 0;
                        revSampleR = 0;

                        for (int j = 0; j < (1 << overSampling); j++) {
                            for (int k = 0; k < 3; k++) {
                                sample = tblGetSample[duty[k]].apply(k, oLevel[k]);
                                int[] l = new int[] {sample};
                                int[] r = new int[] {sample};
                                effects.distortion.mix(efcStartCh + k, l, r);
                                effects.chorus.mix(efcStartCh + k, l, r);
                                effects.hpflpf.mix(efcStartCh + k, l, r);
                                l[0] = (panpot[k] & 2) != 0 ? l[0] : 0;
                                r[0] = (panpot[k] & 1) != 0 ? r[0] : 0;
                                l[0] *= ReversePhase.ssg[num][k][0];
                                r[0] *= ReversePhase.ssg[num][k][1];
                                revSampleL += (int) (l[0] * effects.reverb.sendLevel[efcStartCh + k] * 0.6);
                                revSampleR += (int) (r[0] * effects.reverb.sendLevel[efcStartCh + k] * 0.6);
                                sampleL += l[0];
                                sampleR += r[0];
                                sCount[k] += sPeriod[k];
                            }

                        }
                        sampleL /= (1 << overSampling);
                        sampleR /= (1 << overSampling);
                        revSampleL /= (1 << overSampling);
                        revSampleR /= (1 << overSampling);

                        dest[ptrDest + 0] += sampleL;
                        dest[ptrDest + 1] += sampleR;
                        effects.reverb.storeDataC(revSampleL, revSampleR);
                        ptrDest += 2;

                        visVolume = sampleL;
                    }
                } else {
                    int ptrDest = 0;
                    // Noise
                    for (int i = 0; i < nSamples; i++) {
                        sampleL = 0;
                        sampleR = 0;
                        revSampleL = 0;
                        revSampleR = 0;
                        sample = 0;
                        for (int j = 0; j < (1 << overSampling); j++) {
                            noise = noiseTable[((int) ncountDbl >> (noiseShift + overSampling + 6) & (noiseTableSize - 1))]
                                    >> ((int) ncountDbl >> (noiseShift + overSampling + 1));

                            ncountDbl += ((double) nPeriod / ((reg[6] & 0x20) != 0 ? ncountDiv : 1.0));

                            for (int k = 0; k < 3; k++) {
                                sample = tblGetSample[duty[k]].apply(k, oLevel[k]);
                                int[] l = new int[] {sample};
                                int[] r = new int[] {sample};

                                //ノイズ
                                nv = ((sCount[k] >> (toneShift + overSampling)) & 0 | (nEnable[k] & noise)) - 1;
                                sample = (oLevel[k] + nv) ^ nv;
                                l[0] += sample;
                                r[0] += sample;

                                effects.distortion.mix(efcStartCh + k, l, r);
                                effects.chorus.mix(efcStartCh + k, l, r);
                                effects.hpflpf.mix(efcStartCh + k, l, r);
                                effects.compressor.mix(efcStartCh + k, l, r);
                                l[0] = (panpot[k] & 2) != 0 ? l[0] : 0;
                                r[0] = (panpot[k] & 1) != 0 ? r[0] : 0;
                                l[0] *= ReversePhase.ssg[num][k][0];
                                r[0] *= ReversePhase.ssg[num][k][1];
                                revSampleL += (int) (l[0] * effects.reverb.sendLevel[efcStartCh + k] * 0.6);
                                revSampleR += (int) (r[0] * effects.reverb.sendLevel[efcStartCh + k] * 0.6);
                                sampleL += l[0];
                                sampleR += r[0];
                                sCount[k] += sPeriod[k];
                            }
                        }

                        sampleL /= (1 << overSampling);
                        sampleR /= (1 << overSampling);
                        dest[ptrDest + 0] += sampleL;
                        dest[ptrDest + 1] += sampleR;
                        effects.reverb.storeDataC(revSampleL, revSampleR);
                        ptrDest += 2;

                        visVolume = sampleL;
                    }
                }

                // Balancing the accounts by skipping the envelope calculations
                eCount = (eCount >> 8) + (ePeriod >> (8 - overSampling)) * nSamples;
                if (eCount >= (1 << (envShift + 6 + overSampling - 8))) {
                    if ((reg[0x0d] & 0x0b) != 0x0a)
                        eCount |= (1 << (envShift + 5 + overSampling - 8));
                    eCount &= (1 << (envShift + 6 + overSampling - 8)) - 1;
                }
                eCount <<= 8;
            } else {
                int ptrDest = 0;
                // With envelope
                for (int i = 0; i < nSamples; i++) {
                    sampleL = 0;
                    sampleR = 0;
                    revSampleL = 0;
                    revSampleR = 0;

                    for (int j = 0; j < (1 << overSampling); j++) {
                        env = envelop[eCount >> (envShift + overSampling)];
                        eCount += ePeriod;
                        if (eCount >= (1 << (envShift + 6 + overSampling))) {
                            if ((reg[0x0d] & 0x0b) != 0x0a)
                                eCount |= (1 << (envShift + 5 + overSampling));
                            eCount &= (1 << (envShift + 6 + overSampling)) - 1;
                        }
                        noise = noiseTable[((int) ncountDbl >> (noiseShift + overSampling + 6) & (noiseTableSize - 1))]
                                >> ((int) ncountDbl >> (noiseShift + overSampling + 1));
                        ncountDbl += (nPeriod / ((reg[6] & 0x20) != 0 ? ncountDiv : 1.0));

                        for (int k = 0; k < 3; k++) {
                            int lv = (p[k] == null ? env : oLevel[k]);
                            sample = tblGetSample[duty[k]].apply(k, lv);
                            int[] l = new int[] {sample};
                            int[] r = new int[] {sample};

                            //ノイズ
                            nv = ((sCount[k] >> (toneShift + overSampling)) & 0 | (nEnable[k] & noise)) - 1;
                            sample = (lv + nv) ^ nv;
                            l[0] += sample;
                            r[0] += sample;

                            effects.distortion.mix(efcStartCh + k, l, r);
                            effects.chorus.mix(efcStartCh + k, l, r);
                            effects.hpflpf.mix(efcStartCh + k, l, r);
                            effects.compressor.mix(efcStartCh + k, l, r);
                            l[0] = (panpot[k] & 2) != 0 ? l[0] : 0;
                            r[0] = (panpot[k] & 1) != 0 ? r[0] : 0;
                            l[0] *= ReversePhase.ssg[num][k][0];
                            r[0] *= ReversePhase.ssg[num][k][1];
                            revSampleL += (int) (l[0] * effects.reverb.sendLevel[efcStartCh + k] * 0.6);
                            revSampleR += (int) (r[0] * effects.reverb.sendLevel[efcStartCh + k] * 0.6);
                            sampleL += l[0];
                            sampleR += r[0];
                            sCount[k] += sPeriod[k];
                        }
                    }
                    sampleL /= (1 << overSampling);
                    sampleR /= (1 << overSampling);
                    revSampleL /= (1 << overSampling);
                    revSampleR /= (1 << overSampling);

                    dest[ptrDest + 0] += sampleL;
                    dest[ptrDest + 1] += sampleR;
                    effects.reverb.storeDataC(revSampleL, revSampleR);
                    ptrDest += 2;

                    visVolume = sampleL;
                }
            }
        }
    }

    private int getSampleFromUserDef(int k, int lv) {
        if (chEnable[k] == 0) return 0;

        // User defined
        int pos = (sCount[k] >> (toneShift + overSampling - 3 - 2)) & 63;
        int n = user[duty[k] - 10][pos] & chEnable[k];
        int x = n - 8;
        return (lv * x) >> 2;
    }

    private int getSampleFromSaw(int k, int lv) {
        if (chEnable[k] == 0) return 0;

        int n = ((sCount[k] >> (toneShift + overSampling - 3)) & chEnable[k]);
        // Sawtooth Wave
        int x = n < 7 ? n : (n - 16);
        return (lv * x) >> 2;
    }

    private int getSampleFromTriangle(int k, int lv) {
        if (chEnable[k] == 0) return 0;

        int n = ((sCount[k] >> (toneShift + overSampling - 3)) & chEnable[k]);
        // Triangle wave
        int x = n < 8 ? (n - 4) : (15 - 4 - n);
        return (lv * x) >> 1;
    }

    private int getSampleFromDuty(int k, int lv) {
        if (chEnable[k] == 0) return 0;

        int n = ((sCount[k] >> (toneShift + overSampling - 3)) & chEnable[k]);
        // Square wave
        int x = n > duty[k] ? 0 : -1;
        return (lv + x) ^ x;
    }
}
