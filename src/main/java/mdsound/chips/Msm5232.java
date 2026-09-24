/*
 * license:GPL-2.0+
 *
 * copyright-holders: Jarek Burczynski, Hiromitsu Shioya, Angelo Salese
 */

package mdsound.chips;

import java.util.Arrays;


/**
 * OKI MSM5232RS 8 channel tone generator.
 * <p>
 * Eight voices in two groups of four. A voice is a square wave taken off a binary counter at four
 * footages, 16', 8', 4' and 2', each an octave above the last, and a group sums its voices per
 * footage into four outputs that its control register enables. The envelope is an external
 * capacitor the chip charges for the attack and discharges for the decay and release. A pitch code
 * of {@code 0xd8} and above plays the noise generator instead of a tone.
 * <p>
 * Registers:
 * <pre>
 * 00-07 voice: bit 7 key on, bit 6-0 pitch code (0x00-0x57, 0x21 is A4 at 2119040 Hz on 8')
 * 08/09 group 1/2 attack (0-7)
 * 0a/0b group 1/2 decay (0-15)
 * 0c/0d group 1/2 control: bit 4 ARM (hold until key off), bit 3-0 2'/4'/8'/16' enable
 * 10-1a output volume (0x80 = 100%) of the 11 outputs: group 1 2'..16', group 2 2'..16', solo 8', solo 16', noise
 * 1e/1f group 1/2 TA7630 external volume (0-15)
 * 20-23 clock, little endian, applied on 23
 * </pre>
 * The registers from 10 on are not the chip's: they are libvgm's, standing in for the mixer the
 * chip is wired to on a board.
 * <p>
 * Ported from libvgm's emu/cores/msm5232.c (MAME's, modified by Mao and cam900). It runs at its
 * native rate, the clock over 16.
 *
 * @author Jarek Burczynski
 * @author Hiromitsu Shioya
 */
public class Msm5232 {

    public static final int CHANNELS = 8;
    public static final int OUTPUTS = 11;

    private static final int CLOCK_RATE_DIVIDER = 16;
    private static final int STEP_SH = 16;
    private static final int VMIN = 0;
    private static final int VMAX = 32768;

    private static int rom(int counter, int binDiv) {
        return counter | (binDiv << 9);
    }

    /** pitch code to programmable counter (9 bits) and binary counter shift (3 bits) */
    private static final int[] ROM = {
            rom(506, 7), rom(478, 7), rom(451, 7), rom(426, 7), rom(402, 7), rom(379, 7), rom(358, 7), rom(338, 7),
            rom(319, 7), rom(301, 7), rom(284, 7), rom(268, 7), rom(253, 7), rom(478, 6), rom(451, 6), rom(426, 6),
            rom(402, 6), rom(379, 6), rom(358, 6), rom(338, 6), rom(319, 6), rom(301, 6), rom(284, 6), rom(268, 6),
            rom(253, 6), rom(478, 5), rom(451, 5), rom(426, 5), rom(402, 5), rom(379, 5), rom(358, 5), rom(338, 5),
            rom(319, 5), rom(301, 5), rom(284, 5), rom(268, 5), rom(253, 5), rom(478, 4), rom(451, 4), rom(426, 4),
            rom(402, 4), rom(379, 4), rom(358, 4), rom(338, 4), rom(319, 4), rom(301, 4), rom(284, 4), rom(268, 4),
            rom(253, 4), rom(478, 3), rom(451, 3), rom(426, 3), rom(402, 3), rom(379, 3), rom(358, 3), rom(338, 3),
            rom(319, 3), rom(301, 3), rom(284, 3), rom(268, 3), rom(253, 3), rom(478, 2), rom(451, 2), rom(426, 2),
            rom(402, 2), rom(379, 2), rom(358, 2), rom(338, 2), rom(319, 2), rom(301, 2), rom(284, 2), rom(268, 2),
            rom(253, 2), rom(478, 1), rom(451, 1), rom(426, 1), rom(402, 1), rom(379, 1), rom(358, 1), rom(338, 1),
            rom(319, 1), rom(301, 1), rom(284, 1), rom(268, 1), rom(253, 1), rom(253, 1), rom(253, 1), rom(13, 7)
    };

    /*
     * Resistance values are guesswork, default capacitance is mentioned in the datasheets.
     * Expected timings are 2ms, 40ms and 250ms respectively with a 1uF capacitor.
     */
    private static final double R51 = 870.0; // attack resistance
    private static final double R52 = 17400.0; // decay 1 resistance
    private static final double R53 = 101000.0; // decay 2 resistance

    public static class Voice {
        /** 0: tone, 1: noise */
        int mode;
        int tgCountPeriod;
        int tgCount;
        /** 8 bit binary counter */
        int tgCnt;
        int tgOut16, tgOut8, tgOut4, tgOut2;
        int egVol;
        /** 0: attack, 1: decay, 2: release, -1: off */
        int egSect;
        int counter;
        int eg;
        boolean egArm;
        double arRate, drRate, rrRate;
        int pitch;
        int gf;

        /** the pitch code the voice was last keyed on with, -1 before */
        public int getPitch() { return pitch; }
        /** the key, bit 7 of the voice register */
        public boolean isKeyOn() { return gf != 0; }
        public boolean isNoise() { return mode == 1; }
        /** 0: attack, 1: decay, 2: release, -1: off */
        public int getEgSection() { return egSect; }
        /** the envelope, 0 to 2048 */
        public int getEgVolume() { return egVol; }
    }

    private final Voice[] voices = new Voice[CHANNELS];

    private int noiseRng;
    private int noiseOut;
    private int noiseStep;
    private int noiseCnt;
    private int noiseClocks;

    private int control1, control2;
    private final int[] enOut16 = new int[2], enOut8 = new int[2], enOut4 = new int[2], enOut2 = new int[2];

    private final byte[] clockBuffer = new byte[4];
    private int initialClock;
    private int clock;
    private int sampleRate;
    private int updateStep;

    private Runnable sampleRateChanged;

    private final double[] capacitors = new double[CHANNELS];
    private final double[] arTbl = new double[8];
    private final double[] drTbl = new double[16];

    /** TA7630 external volume, 0..15, [0]: group 1, [1]: group 2 */
    private final int[] extVol = new int[2];
    /** per output volume, 0x80 = 100% */
    private final int[] perOutVol = new int[OUTPUTS];
    private final boolean[] muted = new boolean[OUTPUTS];
    private final double[] volCtrl = new double[16];

    public Msm5232() {
        for (int i = 0; i < CHANNELS; i++) voices[i] = new Voice();
        double db = 0.0;
        double dbStep = 1.50; // 1.50 dB step (at least, maybe more)
        double dbStepInc = 0.125;
        for (int i = 0; i < 16; i++) {
            double max = 100.0 / Math.pow(10.0, db / 20.0);
            volCtrl[15 - i] = max / 100.0;
            db += dbStep;
            dbStep += dbStepInc;
        }
    }

    /**
     * @param capacitors the external capacitors in Farads, 1 µF each when null
     * @return the sampling rate
     */
    public int start(int clock, double[] capacitors) {
        this.initialClock = clock;
        this.clock = clock;
        writeLe32(clockBuffer, clock);
        for (int i = 0; i < CHANNELS; i++)
            this.capacitors[i] = capacitors != null ? capacitors[i] : 1e-6;
        sampleRate = getRate();
        initTables();
        setMuteMask(0);
        reset();
        return sampleRate;
    }

    public void reset() {
        setClock(initialClock);
        writeLe32(clockBuffer, clock);

        for (int i = 0; i < CHANNELS; i++) {
            initVoice(i);
            write(i, 0x80);
            write(i, 0x00);
        }
        noiseRng = 1;
        noiseOut = 0;
        noiseCnt = 0;
        noiseClocks = 0;
        control1 = control2 = 0;
        Arrays.fill(enOut16, 0);
        Arrays.fill(enOut8, 0);
        Arrays.fill(enOut4, 0);
        Arrays.fill(enOut2, 0);

        // TA7630 external volume defaults: max
        extVol[0] = 0x0f;
        extVol[1] = 0x0f;

        // enable 0..7 by default, mute 8..10 (not connected in Taito machines)
        for (int i = 0; i < OUTPUTS; i++)
            perOutVol[i] = i >= 8 ? 0 : 0x80;
    }

    private static void writeLe32(byte[] buffer, int value) {
        buffer[0] = (byte) value;
        buffer[1] = (byte) (value >> 8);
        buffer[2] = (byte) (value >> 16);
        buffer[3] = (byte) (value >> 24);
    }

    private void initTables() {
        // emulating at the native sample rate
        updateStep = (1 << STEP_SH) / CLOCK_RATE_DIVIDER;
        noiseStep = (1 << STEP_SH) * CLOCK_RATE_DIVIDER / 128;

        double clockScale = clock / 2119040.0;
        for (int i = 0; i < 8; i++) {
            int rcpDutyCycle = 1 << ((i & 4) != 0 ? (i & ~2) : i); // bit 1 is ignored if bit 2 is set
            arTbl[i] = (rcpDutyCycle / clockScale) * R51;
            drTbl[i] = (rcpDutyCycle / clockScale) * R52;
            drTbl[i + 8] = (rcpDutyCycle / clockScale) * R53;
        }
    }

    private void initVoice(int i) {
        Voice v = voices[i];
        v.arRate = arTbl[0] * capacitors[i];
        v.drRate = drTbl[0] * capacitors[i];
        v.rrRate = drTbl[0] * capacitors[i];
        v.egSect = -1;
        v.eg = 0;
        v.egVol = 0;
        v.counter = 0;
        v.mode = 0;
        v.pitch = -1;
        v.gf = 0;

        v.tgCountPeriod = 1;
        v.tgCount = 1;
        v.tgCnt = 0;
        v.tgOut16 = v.tgOut8 = v.tgOut4 = v.tgOut2 = 0;
    }

    public int getRate() {
        return clock / CLOCK_RATE_DIVIDER;
    }

    /** @param clock 0 takes it from the clock registers */
    private void setClock(int clock) {
        int oldClock = this.clock;
        if (clock != 0)
            this.clock = clock;
        else
            this.clock = (clockBuffer[0] & 0xff) | (clockBuffer[1] & 0xff) << 8 | (clockBuffer[2] & 0xff) << 16 | (clockBuffer[3] & 0xff) << 24;

        if (oldClock != this.clock) {
            sampleRate = getRate();
            if (sampleRateChanged != null) sampleRateChanged.run();
            initTables(); // the AR/DR tables rely on the actual clock
        }
    }

    public void setSampleRateChanged(Runnable sampleRateChanged) {
        this.sampleRateChanged = sampleRateChanged;
    }

    private void egVoicesAdvance() {
        for (Voice v : voices) {
            switch (v.egSect) {
                case 0 -> { // attack, capacitor charge
                    if (v.eg < VMAX) {
                        v.counter -= (int) ((VMAX - v.eg) / v.arRate);
                        if (v.counter <= 0) {
                            int n = -v.counter / sampleRate + 1;
                            v.counter += n * sampleRate;
                            v.eg += n;
                            if (v.eg > VMAX)
                                v.eg = VMAX;
                        }
                    }
                    // when ARM=0, EG switches to decay as soon as cap is charged to VT (EG inversion voltage; about 80% of MAX)
                    if (!v.egArm) {
                        if (v.eg >= VMAX * 80 / 100)
                            v.egSect = 1;
                    }
                    // ARM=1: stay at max until key off
                    v.egVol = v.eg / 16;
                }
                case 1 -> { // decay, capacitor discharge
                    if (v.eg > VMIN) {
                        v.counter -= (int) ((v.eg - VMIN) / v.drRate);
                        if (v.counter <= 0) {
                            int n = -v.counter / sampleRate + 1;
                            v.counter += n * sampleRate;
                            v.eg -= n;
                            if (v.eg < VMIN)
                                v.eg = VMIN;
                        }
                    } else {
                        v.egSect = -1;
                    }
                    v.egVol = v.eg / 16;
                }
                case 2 -> { // release, capacitor discharge
                    if (v.eg > VMIN) {
                        v.counter -= (int) ((v.eg - VMIN) / v.rrRate);
                        if (v.counter <= 0) {
                            int n = -v.counter / sampleRate + 1;
                            v.counter += n * sampleRate;
                            v.eg -= n;
                            if (v.eg < VMIN)
                                v.eg = VMIN;
                        }
                    } else {
                        v.egSect = -1;
                    }
                    v.egVol = v.eg / 16; // 32768/16 = 2048 max
                }
                default -> {}
            }
        }
    }

    private int o2, o4, o8, o16, solo8, solo16;

    private void tgGroupAdvance(int groupIdx) {
        o2 = o4 = o8 = o16 = solo8 = solo16 = 0;
        for (int i = 0; i < 4; i++) {
            Voice v = voices[groupIdx * 4 + i];
            int out2 = 0, out4 = 0, out8 = 0, out16 = 0;
            if (v.tgCountPeriod == 0) // not initialized yet
                continue;
            if (v.mode == 0) { // generate square tone
                int left = 1 << STEP_SH;
                do {
                    int nextEvent = left;
                    if ((v.tgCnt & v.tgOut16) != 0) out16 += v.tgCount;
                    if ((v.tgCnt & v.tgOut8) != 0) out8 += v.tgCount;
                    if ((v.tgCnt & v.tgOut4) != 0) out4 += v.tgCount;
                    if ((v.tgCnt & v.tgOut2) != 0) out2 += v.tgCount;

                    v.tgCount -= nextEvent;
                    while (v.tgCount <= 0) {
                        v.tgCount += v.tgCountPeriod;
                        v.tgCnt = (v.tgCnt + 1) & 0xff;
                        if ((v.tgCnt & v.tgOut16) != 0) out16 += v.tgCountPeriod;
                        if ((v.tgCnt & v.tgOut8) != 0) out8 += v.tgCountPeriod;
                        if ((v.tgCnt & v.tgOut4) != 0) out4 += v.tgCountPeriod;
                        if ((v.tgCnt & v.tgOut2) != 0) out2 += v.tgCountPeriod;
                        if (v.tgCount > 0) break;
                        v.tgCount += v.tgCountPeriod;
                        v.tgCnt = (v.tgCnt + 1) & 0xff;
                        if ((v.tgCnt & v.tgOut16) != 0) out16 += v.tgCountPeriod;
                        if ((v.tgCnt & v.tgOut8) != 0) out8 += v.tgCountPeriod;
                        if ((v.tgCnt & v.tgOut4) != 0) out4 += v.tgCountPeriod;
                        if ((v.tgCnt & v.tgOut2) != 0) out2 += v.tgCountPeriod;
                    }
                    if ((v.tgCnt & v.tgOut16) != 0) out16 -= v.tgCount;
                    if ((v.tgCnt & v.tgOut8) != 0) out8 -= v.tgCount;
                    if ((v.tgCnt & v.tgOut4) != 0) out4 -= v.tgCount;
                    if ((v.tgCnt & v.tgOut2) != 0) out2 -= v.tgCount;
                    left -= nextEvent;
                } while (left > 0);
            } else { // generate noise
                if ((noiseClocks & 8) != 0) out16 += (1 << STEP_SH);
                if ((noiseClocks & 4) != 0) out8 += (1 << STEP_SH);
                if ((noiseClocks & 2) != 0) out4 += (1 << STEP_SH);
                if ((noiseClocks & 1) != 0) out2 += (1 << STEP_SH);
            }
            // signed output
            if (!muted[groupIdx * 4 + i]) {
                o16 += ((out16 - (1 << (STEP_SH - 1))) * v.egVol) >> STEP_SH;
                o8 += ((out8 - (1 << (STEP_SH - 1))) * v.egVol) >> STEP_SH;
                o4 += ((out4 - (1 << (STEP_SH - 1))) * v.egVol) >> STEP_SH;
                o2 += ((out2 - (1 << (STEP_SH - 1))) * v.egVol) >> STEP_SH;
            }
            if (i == 3 && groupIdx == 1) {
                if (!muted[8])
                    solo16 += ((out16 - (1 << (STEP_SH - 1))) << 11) >> STEP_SH;
                if (!muted[9])
                    solo8 += ((out8 - (1 << (STEP_SH - 1))) << 11) >> STEP_SH;
            }
        }
        // mask outputs
        o16 &= enOut16[groupIdx];
        o8 &= enOut8[groupIdx];
        o4 &= enOut4[groupIdx];
        o2 &= enOut2[groupIdx];
    }

    private void updateNoise() {
        int cnt = (noiseCnt += noiseStep) >> STEP_SH;
        noiseCnt &= (1 << STEP_SH) - 1;
        while (cnt > 0) {
            int tmp = noiseRng & (1 << 16);
            if ((noiseRng & 1) != 0)
                noiseRng ^= 0x24000;
            noiseRng >>>= 1;
            if ((noiseRng & (1 << 16)) != tmp)
                noiseClocks++;
            cnt--;
        }
        noiseOut = !muted[10] && (noiseRng & (1 << 16)) != 0 ? 1 : 0;
    }

    public void update(int[][] outputs, int samples) {
        int[] outL = outputs[0];
        int[] outR = outputs[1];

        for (int i = 0; i < samples; i++) {
            egVoicesAdvance();

            tgGroupAdvance(0);
            int g1o2 = (o2 * perOutVol[0]) >> 7;
            int g1o4 = (o4 * perOutVol[1]) >> 7;
            int g1o8 = (o8 * perOutVol[2]) >> 7;
            int g1o16 = (o16 * perOutVol[3]) >> 7;

            tgGroupAdvance(1);
            int g2o2 = (o2 * perOutVol[4]) >> 7;
            int g2o4 = (o4 * perOutVol[5]) >> 7;
            int g2o8 = (o8 * perOutVol[6]) >> 7;
            int g2o16 = (o16 * perOutVol[7]) >> 7;
            int so8 = (solo8 * perOutVol[8]) >> 7;
            int so16 = (solo16 * perOutVol[9]) >> 7;

            updateNoise();
            int nOut = noiseOut * perOutVol[10]; // 0 or "maximum volume"

            int mix = (int) ((g1o2 + g1o4 + g1o8 + g1o16) * volCtrl[extVol[0]]) +
                    (int) ((g2o2 + g2o4 + g2o8 + g2o16) * volCtrl[extVol[1]]) +
                    so8 + so16 + nOut;

            outL[i] = mix;
            outR[i] = mix;
        }
    }

    public void write(int reg, int value) {
        if (reg < 0x08) {
            Voice v = voices[reg & 7];
            v.gf = (value & 0x80) >> 7;
            if ((value & 0x80) != 0) {
                if (value >= 0xd8) {
                    v.mode = 1; // noise
                    v.egSect = 0; // key on
                } else {
                    int pitchIndex = Math.min(value & 0x7f, 0x57);
                    int pg = ROM[pitchIndex];
                    v.pitch = pitchIndex;
                    v.tgCountPeriod = ((pg & 0x1ff) * updateStep) / 2;
                    if (v.tgCountPeriod == 0)
                        v.tgCountPeriod = 1;
                    v.tgCount = v.tgCountPeriod;
                    v.tgCnt = 0;

                    int n = (pg >> 9) & 7; // bit number for 16' output
                    v.tgOut16 = 1 << n;
                    n = n > 0 ? n - 1 : 0;
                    v.tgOut8 = 1 << n;
                    n = n > 0 ? n - 1 : 0;
                    v.tgOut4 = 1 << n;
                    n = n > 0 ? n - 1 : 0;
                    v.tgOut2 = 1 << n;

                    v.mode = 0;
                    v.egSect = 0;
                }
            } else {
                v.egSect = v.egArm ? 1 : 2; // decay : release
            }
        } else {
            switch (reg) {
                case 0x08 -> { // group1 attack
                    for (int i = 0; i < 4; i++) voices[i].arRate = arTbl[value & 7] * capacitors[i];
                }
                case 0x09 -> { // group2 attack
                    for (int i = 0; i < 4; i++) voices[i + 4].arRate = arTbl[value & 7] * capacitors[i + 4];
                }
                case 0x0a -> { // group1 decay
                    for (int i = 0; i < 4; i++) voices[i].drRate = drTbl[value & 0xf] * capacitors[i];
                }
                case 0x0b -> { // group2 decay
                    for (int i = 0; i < 4; i++) voices[i + 4].drRate = drTbl[value & 0xf] * capacitors[i + 4];
                }
                case 0x0c -> { // group1 control
                    control1 = value;
                    control(0, value);
                }
                case 0x0d -> { // group2 control
                    control2 = value;
                    control(1, value);
                }
                case 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1a -> // per output volume
                        perOutVol[reg & 0x0f] = Math.min(value & 0xff, 0x80);
                case 0x1e -> extVol[0] = value & 0x0f; // TA7630 external volume, group 1
                case 0x1f -> extVol[1] = value & 0x0f; // TA7630 external volume, group 2
                case 0x20, 0x21, 0x22 -> clockBuffer[reg & 0x03] = (byte) value;
                case 0x23 -> {
                    clockBuffer[reg & 0x03] = (byte) value;
                    setClock(0);
                }
            }
        }
    }

    private void control(int group, int value) {
        for (int i = 0; i < 4; i++) {
            Voice v = voices[group * 4 + i];
            if ((value & 0x10) != 0 && v.egSect == 1)
                v.egSect = 0;
            v.egArm = (value & 0x10) != 0;
        }
        enOut16[group] = (value & 1) != 0 ? ~0 : 0;
        enOut8[group] = (value & 2) != 0 ? ~0 : 0;
        enOut4[group] = (value & 4) != 0 ? ~0 : 0;
        enOut2[group] = (value & 8) != 0 ? ~0 : 0;
    }

    /** @param muteMask bit 0-7: voices, 8: solo 16', 9: solo 8', 10: noise */
    public void setMuteMask(int muteMask) {
        for (int i = 0; i < OUTPUTS; i++)
            muted[i] = ((muteMask >> i) & 1) != 0;
    }

    //----

    public Voice getVoice(int ch) {
        return voices[ch];
    }

    public int getClock() {
        return clock;
    }

    /** @return group 1 or 2's control register: bit 4 ARM, bit 3-0 2'/4'/8'/16' enable */
    public int getControl(int group) {
        return group == 0 ? control1 : control2;
    }

    public int getExtVol(int group) {
        return extVol[group];
    }
}
