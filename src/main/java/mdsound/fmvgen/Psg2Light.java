package mdsound.fmvgen;

import java.util.List;
import java.util.function.BiFunction;

import mdsound.fmgen.PSG;

import static mdsound.fmgen.Fmgen.storeSample;


public class Psg2Light extends PSG {

    protected byte[] panpot = new byte[3];
    protected byte[] panpotLM = new byte[3];
    protected byte[] panpotRM = new byte[3];
    public static final float[] panTable = {1.0f, 0.8756f, 0.7512f, 0.6012f, 0.4512f, 0.2506f, 0.0500f, 0.0250f};
    protected byte[] phaseReset = new byte[3];
    protected boolean[] phaseResetBefore = new boolean[3];
    protected byte[] duty = new byte[3];
    private final byte[][] user = {new byte[64], new byte[64], new byte[64], new byte[64], new byte[64], new byte[64]};
    private int userDefCounter = 0;
    private int userDefNum = 0;
    private BiFunction<Integer, Integer, Integer>[] tblGetSample;
    protected double ncountDbl;
    private final double ncountDiv = 32.0;

    public Psg2Light() {
        makeTblGetSample();
    }

    @Override
    public void setReg(int regnum, int data) {
        if (regnum >= 0x10) return;

        reg[regnum] = (byte) data;
        int tmp;
        switch (regnum) {
            case 0:     // ChA Fine Tune
            case 1:     // ChA Coarse Tune
                tmp = ((reg[0] + reg[1] * 256) & 0xfff);
                sPeriod[0] = (int) (tmp != 0 ? tPeriodBase / tmp : tPeriodBase);
                duty[0] = (byte) (reg[1] >> 4);
                duty[0] = (byte) (duty[0] < 8 ? (7 - duty[0]) : duty[0]);
                break;

            case 2:     // ChB Fine Tune
            case 3:     // ChB Coarse Tune
                tmp = ((reg[2] + reg[3] * 256) & 0xfff);
                sPeriod[1] = (int) (tmp != 0 ? tPeriodBase / tmp : tPeriodBase);
                duty[1] = (byte) (reg[3] >> 4);
                duty[1] = (byte) (duty[1] < 8 ? (7 - duty[1]) : duty[1]);
                break;

            case 4:     // ChC Fine Tune
            case 5:     // ChC Coarse Tune
                tmp = ((reg[4] + reg[5] * 256) & 0xfff);
                sPeriod[2] = (int) (tmp != 0 ? tPeriodBase / tmp : tPeriodBase);
                duty[2] = (byte) (reg[5] >> 4);
                duty[2] = (byte) (duty[2] < 8 ? (7 - duty[2]) : duty[2]);
                break;

            case 6:     // Noise generator control
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
                oLevel[0] = (int) ((mask & 1) != 0 ? emitTable[(data & 15) * 2 + 1] : 0);
                panpot[0] = (byte) (data >> 6);
                panpot[0] = (byte) (panpot[0] == 0 ? 3 : panpot[0]);
                phaseReset[0] = (byte) ((data & 0x20) != 0 ? 1 : 0);
                break;

            case 9:
                oLevel[1] = (int) ((mask & 2) != 0 ? emitTable[(data & 15) * 2 + 1] : 0);
                panpot[1] = (byte) (data >> 6);
                panpot[1] = (byte) (panpot[1] == 0 ? 3 : panpot[1]);
                phaseReset[1] = (byte) ((data & 0x20) != 0 ? 1 : 0);
                break;

            case 10:
                oLevel[2] = (int) ((mask & 4) != 0 ? emitTable[(data & 15) * 2 + 1] : 0);
                panpot[2] = (byte) (data >> 6);
                panpot[2] = (byte) (panpot[2] == 0 ? 3 : panpot[2]);
                phaseReset[2] = (byte) ((data & 0x20) != 0 ? 1 : 0);
                break;

            case 11:    // Envelop period
            case 12:
                tmp = ((reg[11] + reg[12] * 256) & 0xffff);
                ePeriod = (int) (tmp != 0 ? ePeriodBase / tmp : ePeriodBase * 2);
                break;

            case 13:    // Envelop shape
                eCount = 0;
                envelop = envelopTable[data & 15];
                if ((data & 0x80) != 0) userDefCounter = 0;
                userDefNum = ((data & 0x70) >> 4) % 6;
                break;

            case 14:    // Define Wave Data
                user[userDefNum][userDefCounter & 63] = (byte) data;
                //Console.WriteLine("{3} : WF {0} {1} {2} ", ((data & 0x70) >> 4) % 6, userDefCounter & 63, (byte)(data & 0xf), data);
                userDefCounter++;
                break;

            case 15:    // Pan mul
                int ch = (data >> 6) & 0x3;
                if (ch == 3) break;
                panpotLM[ch] = (byte) ((data >> 3) & 7);
                panpotRM[ch] = (byte) (data & 7);
                break;
        }

    }

    private final byte[] chenable = new byte[3];
    private final byte[] nenable = new byte[3];
    private Integer[] p = new Integer[3];

    @Override
    public void mix(int[] dest, int nSamples) {
        byte r7 = (byte) ~reg[7];

        if (((r7 & 0x3f) | ((reg[8] | reg[9] | reg[10]) & 0x1f)) != 0) {
            chenable[0] = (byte) ((((r7 & 0x01) != 0) && (sPeriod[0] <= (int) (1 << toneShift))) ? 15 : 0);
            chenable[1] = (byte) ((((r7 & 0x02) != 0) && (sPeriod[1] <= (int) (1 << toneShift))) ? 15 : 0);
            chenable[2] = (byte) ((((r7 & 0x04) != 0) && (sPeriod[2] <= (int) (1 << toneShift))) ? 15 : 0);
            nenable[0] = (byte) ((r7 & 0x08) != 0 ? 1 : 0);
            nenable[1] = (byte) ((r7 & 0x10) != 0 ? 1 : 0);
            nenable[2] = (byte) ((r7 & 0x20) != 0 ? 1 : 0);
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
                            int revBit = 0x80;
                            for (int k = 0; k < 3; k++) {
                                sample = tblGetSample[duty[k]].apply(k, oLevel[k]);
                                int L = sample;
                                int R = sample;
                                L = (panpot[k] & 2) != 0 ? (int) (L * panTable[panpotLM[k]]) : 0;
                                R = (panpot[k] & 1) != 0 ? (int) (R * panTable[panpotRM[k]]) : 0;
                                L *= (reg[15] & revBit) != 0 ? 1 : -1;
                                revBit >>= 1;
                                R *= (reg[15] & revBit) != 0 ? 1 : -1;
                                revBit >>= 1;
                                sampleL += L;
                                sampleR += R;
                                sCount[k] += sPeriod[k];
                            }

                        }
                        sampleL /= (1 << overSampling);
                        sampleR /= (1 << overSampling);
                        revSampleL /= (1 << overSampling);
                        revSampleR /= (1 << overSampling);

                        dest[ptrDest + 0] = storeSample(dest[ptrDest + 0], sampleL);
                        dest[ptrDest + 1] = storeSample(dest[ptrDest + 1], sampleR);
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
                            noise = noiseTable[((int) ncountDbl >> (noiseShift + overSampling + 6) & (noiseTableSize - 1))] >>
                                    ((int) ncountDbl >> (noiseShift + overSampling + 1));

                            ncountDbl += ((double) nPeriod / ((reg[6] & 0x20) != 0 ? ncountDiv : 1.0));

                            int revBit = 0x80;
                            for (int k = 0; k < 3; k++) {
                                sample = tblGetSample[duty[k]].apply(k, oLevel[k]);
                                int L = sample;
                                int R = sample;

                                // Noise
                                nv = ((sCount[k] >> (toneShift + overSampling)) & 0 | (nenable[k] & noise)) - 1;
                                sample = (int) ((oLevel[k] + nv) ^ nv);
                                L += sample;
                                R += sample;

                                L = (panpot[k] & 2) != 0 ? (int) (L * panTable[panpotLM[k]]) : 0;
                                R = (panpot[k] & 1) != 0 ? (int) (R * panTable[panpotRM[k]]) : 0;
                                L *= (reg[15] & revBit) != 0 ? 1 : -1;
                                revBit >>= 1;
                                R *= (reg[15] & revBit) != 0 ? 1 : -1;
                                revBit >>= 1;
                                sampleL += L;
                                sampleR += R;
                                sCount[k] += sPeriod[k];
                            }
                        }

                        sampleL /= (1 << overSampling);
                        sampleR /= (1 << overSampling);
                        dest[ptrDest + 0] = storeSample(dest[ptrDest + 0], sampleL);
                        dest[ptrDest + 1] = storeSample(dest[ptrDest + 1], sampleR);
                        ptrDest += 2;

                        visVolume = sampleL;

                    }
                }

                // Balancing the accounts by skipping the envelope calculations
                eCount = (int) ((eCount >> 8) + (ePeriod >> (8 - overSampling)) * nSamples);
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
                        noise = noiseTable[((int) ncountDbl >> (noiseShift + overSampling + 6) & (noiseTableSize - 1))] >>
                                ((int) ncountDbl >> (noiseShift + overSampling + 1));
                        ncountDbl += (nPeriod / ((reg[6] & 0x20) != 0 ? ncountDiv : 1.0));

                        int revBit = 0x80;
                        for (int k = 0; k < 3; k++) {
                            int lv = (p[k] == null ? env : oLevel[k]);
                            sample = tblGetSample[duty[k]].apply(k, lv);
                            int L = sample;
                            int R = sample;

                            // Noise
                            nv = ((sCount[k] >> (toneShift + overSampling)) & 0 | (nenable[k] & noise)) - 1;
                            sample = (int) ((lv + nv) ^ nv);
                            L += sample;
                            R += sample;

                            L = (panpot[k] & 2) != 0 ? (int) (L * panTable[panpotLM[k]]) : 0;
                            R = (panpot[k] & 1) != 0 ? (int) (R * panTable[panpotRM[k]]) : 0;
                            L *= (reg[15] & revBit) != 0 ? 1 : -1;
                            revBit >>= 1;
                            R *= (reg[15] & revBit) != 0 ? 1 : -1;
                            revBit >>= 1;
                            sampleL += L;
                            sampleR += R;
                            sCount[k] += sPeriod[k];
                        }

                    }
                    sampleL /= (1 << overSampling);
                    sampleR /= (1 << overSampling);
                    revSampleL /= (1 << overSampling);
                    revSampleR /= (1 << overSampling);

                    dest[ptrDest + 0] = storeSample(dest[ptrDest + 0], sampleL);
                    dest[ptrDest + 1] = storeSample(dest[ptrDest + 1], sampleR);
                    ptrDest += 2;

                    visVolume = sampleL;

                }
            }
        }
    }

    private void makeTblGetSample() {
        tblGetSample = List.<BiFunction<Integer, Integer, Integer>>of(
                this::GetSampleFromDuty,
                this::GetSampleFromDuty,
                this::GetSampleFromDuty,
                this::GetSampleFromDuty,
                this::GetSampleFromDuty,
                this::GetSampleFromDuty,
                this::GetSampleFromDuty,
                this::GetSampleFromDuty,
                this::GetSampleFromTriangle,
                this::GetSampleFromSaw,
                this::GetSampleFromUserDef,
                this::GetSampleFromUserDef,
                this::GetSampleFromUserDef,
                this::GetSampleFromUserDef,
                this::GetSampleFromUserDef,
                this::GetSampleFromUserDef
        ).toArray(BiFunction[]::new);
    }

    private int GetSampleFromUserDef(int k, int lv) {
        if (chenable[k] == 0) return 0;

        // User defined
        int pos = (sCount[k] >> (toneShift + overSampling - 3 - 2)) & 63;
        int n = user[duty[k] - 10][pos];
        int x = n - 128;
        return (int) ((lv * x) >> 7);
    }

    private int GetSampleFromSaw(int k, int lv) {
        if (chenable[k] == 0) return 0;

        int n = ((int) (sCount[k] >> (toneShift + overSampling - 3)) & chenable[k]);
        // Sawtooth Wave
        int x = n < 7 ? n : (n - 16);
        return (int) ((lv * x) >> 2);
    }

    private int GetSampleFromTriangle(int k, int lv) {
        if (chenable[k] == 0) return 0;

        int n = ((int) (sCount[k] >> (toneShift + overSampling - 3)) & chenable[k]);
        // Triangle wave
        int x = n < 8 ? (n - 4) : (15 - 4 - n);
        return (int) ((lv * x) >> 1);
    }

    private int GetSampleFromDuty(int k, int lv) {
        if (chenable[k] == 0) return 0;

        int n = ((int) (sCount[k] >> (toneShift + overSampling - 3)) & chenable[k]);
        // Square wave
        int x = n > duty[k] ? 0 : -1;
        return (int) ((lv + x) ^ x);
    }
}

