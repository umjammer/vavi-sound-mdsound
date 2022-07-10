package mdsound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dotnet4j.util.compat.QuadConsumer;
import mdsound.chips.C140;
import mdsound.chips.GbSound;
import mdsound.chips.OotakeHuC6280;
import mdsound.chips.K051649;
import mdsound.chips.MultiPCM;
import mdsound.chips.OkiM6258;
import mdsound.chips.OkiM6295;
import mdsound.chips.PPZ8Status;
import mdsound.chips.PcmChip;
import mdsound.chips.Rf5c68;
import mdsound.chips.SegaPcm;
import mdsound.chips.YmF271;
import mdsound.instrument.*;
import mdsound.np.NpNesFds;
import vavi.util.Debug;


public class MDSound {

    private static final int DefaultSamplingRate = 44100;
    private static final int DefaultSamplingBuffer = 512;

    private int samplingRate = DefaultSamplingRate;
    private int samplingBuffer = DefaultSamplingBuffer;
    private int[][] streamBufs = null;
    public DacControl dacControl = null;

    private Chip[] chips = null;
    private Map<Class<? extends Instrument>, Instrument[]> instruments = new HashMap<>();

    private int[][] buffer = null;
    private int[][] buff = new int[][] {new int[1], new int[1]};

    private List<int[]> sn76489Mask = Arrays.asList(new int[][] {new int[] {15, 15}}); // psgはmuteを基準にしているのでビットが逆です
    private List<int[]> ym2612Mask = Arrays.asList(new int[][] {new int[] {0, 0}});
    private List<int[]> ym2203Mask = Arrays.asList(new int[][] {new int[] {0, 0}});
    private List<int[]> segapcmMask = Arrays.asList(new int[][] {new int[] {0, 0}});
    private List<int[]> qsoundMask = Arrays.asList(new int[][] {new int[] {0, 0}});
    private List<int[]> qsoundCtrMask = Arrays.asList(new int[][] {new int[] {0, 0}});
    private List<int[]> okim6295Mask = Arrays.asList(new int[][] {new int[] {0, 0}});
    private List<int[]> c140Mask = Arrays.asList(new int[][] {new int[] {0, 0}});
    private List<int[]> ay8910Mask = Arrays.asList(new int[][] {new int[] {0, 0}});
    private List<int[]> huc6280Mask = Arrays.asList(new int[][] {new int[] {0, 0}});
    private List<int[]> nesMask = Arrays.asList(new int[][] {new int[] {0, 0}});
    private List<int[]> saa1099Mask = Arrays.asList(new int[][] {new int[] {0, 0}});
    private List<int[]> x1_010Mask = Arrays.asList(new int[][] {new int[] {0, 0}});
    private final List<int[]> WsAudioMask = Arrays.asList(new int[][] {new int[] {0, 0}});

    private final int[][][] rf5c164Vol = new int[][][] {
            new int[][] {new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2]},
            new int[][] {new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2]}
    };

    private final int[][] ym2612Key = new int[][] {new int[6], new int[6]};
    private final int[][] ym2151Key = new int[][] {new int[8], new int[8]};
    private final int[][] ym2203Key = new int[][] {new int[6], new int[6]};
    private final int[][] ym2608Key = new int[][] {new int[11], new int[11]};
    private final int[][] ym2609Key = new int[][] {new int[12 + 12 + 3 + 1], new int[28]};
    private final int[][] ym2610Key = new int[][] {new int[11], new int[11]};

    private boolean incFlag = false;
    private final Object lockobj = new Object();
    private byte resampleMode = 0;

    private static final int FIXPNT_BITS = 11;
    private static final int FIXPNT_FACT = (1 << FIXPNT_BITS);
    private static final int FIXPNT_MASK = (FIXPNT_FACT - 1);


    public static int np_nes_apu_volume;
    public static int np_nes_dmc_volume;
    public static int np_nes_fds_volume;
    public static int np_nes_fme7_volume;
    public static int np_nes_mmc5_volume;
    public static int np_nes_n106_volume;
    public static int np_nes_vrc6_volume;
    public static int np_nes_vrc7_volume;

    public VisWaveBuffer visWaveBuffer = new VisWaveBuffer();

// #if DEBUG
    long sw = System.currentTimeMillis();
// #endif

    private static int getfriction(int x) {
        return ((x) & FIXPNT_MASK);
    }

    private static int getnfriction(int x) {
        return ((FIXPNT_FACT - (x)) & FIXPNT_MASK);
    }

    private static int fpi_floor(int x) {
        return (x) & ~FIXPNT_MASK;
    }

    private static int fpi_ceil(int x) {
        return (x + FIXPNT_MASK) & ~FIXPNT_MASK;
    }

    private static int fp2i_floor(int x) {
        return ((x) / FIXPNT_FACT);
    }

    private static int fp2i_ceil(int x) {
        return ((x + FIXPNT_MASK) / FIXPNT_FACT);
    }

    public static class Chip {
        public interface AdditionalUpdate extends QuadConsumer<Chip, Byte, int[][], Integer> {
        }

        public Instrument instrument = null;
        public AdditionalUpdate additionalUpdate = null;

        //        public InstrumentType type = None.class;
        public byte id = 0;
        public int samplingRate = 0;
        public int clock = 0;
        public int volume = 0;
        public int visVolume = 0;

        public byte resampler;
        public int smpP;
        public int smpLast;
        public int smpNext;
        public int[] lsmpl;
        public int[] nsmpl;

        public Object[] option = null;

        int tVolume;

        public int getTVolume() {
            return tVolume;
        }

        int volumeBalance = 0x100;

        public int getVolumeBalance() {
            return volumeBalance;
        }

        int tVolumeBalance;

        public int gettVolumeBalance() {
            return tVolumeBalance;
        }
    }

    public MDSound() {
        this(DefaultSamplingRate, DefaultSamplingBuffer, null);
    }

    public MDSound(int samplingRate, int samplingBuffer, Chip[] insts) {
        init(samplingRate, samplingBuffer, insts);
    }

    public void init(int samplingRate, int samplingBuffer, Chip[] insts) {
        synchronized (lockobj) {
            this.samplingRate = samplingRate;
            this.samplingBuffer = samplingBuffer;
            this.chips = insts;

            buffer = new int[][] {new int[1], new int[1]};
            streamBufs = new int[][] {new int[0x100], new int[0x100]};

            incFlag = false;

            if (insts == null) return;

            instruments.clear();

            // ボリューム値から実際の倍数を求める
            int total = 0;
            double[] mul = new double[1];
            for (Chip inst : insts) {
                if (inst.instrument instanceof IntFNesInst) inst.volume = 0;
                int balance = getRegulationVolume(inst, mul);
                //16384 = 0x4000 = short.MAXValue + 1
                total += (int) ((((int) (16384.0 * Math.pow(10.0, 0 / 40.0)) * balance) >> 8) * mul[0]) / insts.length;
            }
            // 総ボリューム値から最大ボリュームまでの倍数を求める
            volumeMul = 16384.0 / total;
            // ボリューム値から実際の倍数を求める
            for (Chip inst : insts) {
                if ((inst.volumeBalance & 0x8000) != 0)
                    inst.tVolumeBalance = (getRegulationVolume(inst, mul) * (inst.volumeBalance & 0x7fff) + 0x80) >> 8;
                else
                    inst.tVolumeBalance = inst.volumeBalance;
                int n = (((int) (16384.0 * Math.pow(10.0, inst.volume / 40.0)) * inst.tVolumeBalance) >> 8);
                inst.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }

            for (Chip inst : insts) {
                inst.samplingRate = inst.instrument.start(inst.id, inst.samplingRate, inst.clock, inst.option);
                inst.instrument.reset(inst.id);

                if (instruments.containsKey(inst.instrument.getClass())) {
                    List<Instrument> lst = new ArrayList<>(Arrays.asList(instruments.get(inst.instrument.getClass())));
                    lst.add(inst.instrument);
                    instruments.put(inst.instrument.getClass(), lst.toArray(Instrument[]::new));
                } else {
                    instruments.put(inst.instrument.getClass(), new Instrument[] {inst.instrument});
                }

                setupResampler(inst);
            }
instruments.forEach((k, v) -> Debug.println(k + ": " + Arrays.toString(v)));

            dacControl = new DacControl(samplingRate, this);

            sn76489Mask = new ArrayList<>();
            if (instruments.containsKey(Sn76489Inst.class))
                for (int i = 0; i < instruments.get(Sn76489Inst.class).length; i++)
                    sn76489Mask.add(new int[] {15, 15});
            ym2203Mask = new ArrayList<>();
            if (instruments.containsKey(Ym2203Inst.class))
                for (int i = 0; i < instruments.get(Ym2203Inst.class).length; i++) ym2203Mask.add(new int[] {0, 0});
            ym2612Mask = new ArrayList<>();
            if (instruments.containsKey(Ym2612Inst.class))
                for (int i = 0; i < instruments.get(Ym2612Inst.class).length; i++) ym2612Mask.add(new int[] {0, 0});
            else ym2612Mask.add(new int[] {0, 0});
            segapcmMask = new ArrayList<>();
            if (instruments.containsKey(SegaPcmInst.class))
                for (int i = 0; i < instruments.get(SegaPcmInst.class).length; i++)
                    segapcmMask.add(new int[] {0, 0});
            qsoundMask = new ArrayList<>();
            if (instruments.containsKey(QSoundInst.class))
                for (int i = 0; i < instruments.get(QSoundInst.class).length; i++) qsoundMask.add(new int[] {0, 0});
            qsoundCtrMask = new ArrayList<>();
            if (instruments.containsKey(CtrQSoundInst.class))
                for (int i = 0; i < instruments.get(CtrQSoundInst.class).length; i++)
                    qsoundCtrMask.add(new int[] {0, 0});
            c140Mask = new ArrayList<>();
            if (instruments.containsKey(C140Inst.class))
                for (int i = 0; i < instruments.get(C140Inst.class).length; i++) c140Mask.add(new int[] {0, 0});
            ay8910Mask = new ArrayList<>();
            if (instruments.containsKey(Ay8910Inst.class))
                for (int i = 0; i < instruments.get(Ay8910Inst.class).length; i++) ay8910Mask.add(new int[] {0, 0});
        }
    }

    // default, 0x80, 1
    // UPD7759, 0x11E, 1
    // SCSP, 0x20, 8
    // VSU, 0x100, 1
    // ES5503, 0x40, 8
    // ES5506, 0x20, 16
    private int getRegulationVolume(Chip inst, double[] mul) {
        var r = inst.instrument.getRegulationVolume();
        mul[0] = r.getItem2();
        return r.getItem1();
    }

    public String getDebugMsg() {
        return debugMsg;
    }

    private void setupResampler(Chip chip) {
        if (chip.samplingRate == 0) {
            chip.resampler = (byte) 0xff;
            return;
        }

        if (chip.samplingRate < samplingRate) {
            chip.resampler = 0x01;
        } else if (chip.samplingRate == samplingRate) {
            chip.resampler = 0x02;
        } else if (chip.samplingRate > samplingRate) {
            chip.resampler = 0x03;
        }
        if (chip.resampler == 0x01 || chip.resampler == 0x03) {
            if (resampleMode == 0x02 || (resampleMode == 0x01 && chip.resampler == 0x03))
                chip.resampler = 0x00;
        }

        chip.smpP = 0x00;
        chip.smpLast = 0x00;
        chip.smpNext = 0x00;
        chip.lsmpl = new int[2];
        chip.lsmpl[0] = 0x00;
        chip.lsmpl[1] = 0x00;
        chip.nsmpl = new int[2];
        if (chip.resampler == 0x01) {
            // Pregenerate first Sample (the upsampler is always one too late)
            int[][] buf = new int[][] {new int[1], new int[1]};
            chip.instrument.update(chip.id, buf, 1);
            chip.nsmpl[0] = buf[0x00][0x00];
            chip.nsmpl[1] = buf[0x01][0x00];
        } else {
            chip.nsmpl[0] = 0x00;
            chip.nsmpl[1] = 0x00;
        }
    }

    private int a, b, i;

    public int update(short[] buf, int offset, int sampleCount, Runnable frame) {
        synchronized (lockobj) {

            for (i = 0; i < sampleCount && offset + i < buf.length; i += 2) {

                frame.run();

                dacControl.update();

                a = 0;
                b = 0;

                buffer[0][0] = 0;
                buffer[1][0] = 0;
                resampleChipStream(chips, buffer, 1);
                //if (buffer[0][0] != 0) Debug.printf("%d", buffer[0][0]);
                a += buffer[0][0];
                b += buffer[1][0];

                if (incFlag) {
                    a += buf[offset + i + 0];
                    b += buf[offset + i + 1];
                }

                clip(a, b);

                buf[offset + i + 0] = (short) a;
                buf[offset + i + 1] = (short) b;
                visWaveBuffer.enq((short) a, (short) b);
            }

            return Math.min(i, sampleCount);
        }
    }

    private void clip(int a, int b) {
        if ((a + 32767) > (32767 * 2)) {
            if ((a + 32767) >= (32767 * 2)) {
                a = 32767;
            } else {
                a = -32767;
            }
        }
        if ((b + 32767) > (32767 * 2)) {
            if ((b + 32767) >= (32767 * 2)) {
                b = 32767;
            } else {
                b = -32767;
            }
        }
    }

    public static int limit(int v, int max, int min) {
        return v > max ? max : Math.max(v, min);
    }

    private int[][] tempSample = new int[][] {new int[1], new int[1]};
    private int[][] streamPnt = new int[][] {new int[0x100], new int[0x100]};
    private int clearLength = 1;
    public static String debugMsg;
    private double volumeMul;

    private void resampleChipStream(Chip[] insts, int[][] retSample, int length) {
        if (insts == null || insts.length < 1) return;
        if (length > tempSample[0].length) {
            tempSample = new int[][] {new int[length], new int[length]};
        }
        if (length > streamPnt[0].length) {
            streamPnt = new int[][] {new int[length], new int[length]};
        }

        Chip inst;
        int[] curBufL;
        int[] curBufR;
        int inBase;
        int inPos;
        int InPosNext;
        int outPos;
        int smpFrc; // Sample Friction
        int inPre = 0;
        int inNow;
        int inPosL;
        long tempSmpL;
        long tempSmpR;
        int tempS32L;
        int tempS32R;
        int smpCnt; // must be signed, else I'm getting calculation errors
        int CurSmpl;
        long chipSmpRate;

        // This Do-While-Loop gets and resamples the chips output of one or more chips.
        // It's a loop to support the AY8910 paired with the Ym2203Inst/Ym2608Inst/Ym2610Inst.
        for (Chip chip : insts) {
            Arrays.fill(streamBufs[0], 0);
            Arrays.fill(streamBufs[1], 0);
            curBufL = streamBufs[0x00];
            curBufR = streamBufs[0x01];

            inst = chip;
            int mul = inst.tVolume;

            //if (i != 0 && chips[i].LSmpl[0] != 0) Debug.printf("%d %d", chips[i].LSmpl[0], chips[0].LSmpl == chips[i].LSmpl);
            //Debug.printf("%d %d", inst.type, inst.Resampler);
            //Debug.printf("%d", inst.Resampler);
            switch (inst.resampler) {
            case 0x00: // old, but very fast resampler
                inst.smpLast = inst.smpNext;
                inst.smpP += length;
                inst.smpNext = (int) ((long) inst.smpP * inst.samplingRate / samplingRate);
                if (inst.smpLast >= inst.smpNext) {
                    tempSample[0][0] = limit((inst.lsmpl[0] * mul) >> 15, 0x7fff, -0x8000);
                    tempSample[1][0] = limit((inst.lsmpl[1] * mul) >> 15, 0x7fff, -0x8000);
                } else {
                    smpCnt = inst.smpNext - inst.smpLast;
                    clearLength = smpCnt;
                    for (int ind = 0; ind < smpCnt; ind++) {
                        buff[0][0] = 0;
                        buff[1][0] = 0;
                        inst.instrument.update(inst.id, buff, 1);

                        streamBufs[0][ind] += limit((buff[0][0] * mul) >> 15, 0x7fff, -0x8000);
                        streamBufs[1][ind] += limit((buff[1][0] * mul) >> 15, 0x7fff, -0x8000);
                    }

                    if (smpCnt == 1) {
                        tempSample[0][0] = limit((curBufL[0] * mul) >> 15, 0x7fff, -0x8000);
                        tempSample[1][0] = limit((curBufR[0] * mul) >> 15, 0x7fff, -0x8000);

                        inst.lsmpl[0] = curBufL[0x00];
                        inst.lsmpl[1] = curBufR[0x00];
                    } else if (smpCnt == 2) {
                        tempSample[0][0] = limit(((curBufL[0] + curBufL[1]) * mul) >> (15 + 1), 0x7fff, -0x8000);
                        tempSample[1][0] = limit(((curBufR[0] + curBufR[1]) * mul) >> (15 + 1), 0x7fff, -0x8000);

                        inst.lsmpl[0] = curBufL[0x01];
                        inst.lsmpl[1] = curBufR[0x01];
                    } else {
                        tempS32L = curBufL[0x00];
                        tempS32R = curBufR[0x00];
                        for (CurSmpl = 0x01; CurSmpl < smpCnt; CurSmpl++) {
                            tempS32L += curBufL[CurSmpl];
                            tempS32R += curBufR[CurSmpl];
                        }
                        tempSample[0][0] = limit(((tempS32L * mul) >> 15) / smpCnt, 0x7fff, -0x8000);
                        tempSample[1][0] = limit(((tempS32R * mul) >> 15) / smpCnt, 0x7fff, -0x8000);

                        inst.lsmpl[0] = curBufL[smpCnt - 1];
                        inst.lsmpl[1] = curBufR[smpCnt - 1];
                    }
                }
                break;
            case 0x01: // up sampling
                chipSmpRate = inst.samplingRate;
                inPosL = (int) (FIXPNT_FACT * inst.smpP * chipSmpRate / samplingRate);
                inPre = fp2i_floor(inPosL);
                inNow = fp2i_ceil(inPosL);

                //if (inst.type == Ym2612Inst.class) {
                //    Debug.printf("inPosL=%d , inPre=%d , inNow=%d , inst.SmpNext=%d", inPosL, inPre, inNow, inst.SmpNext);
                //}

                curBufL[0x00] = inst.lsmpl[0];
                curBufR[0x00] = inst.lsmpl[1];
                curBufL[0x01] = inst.nsmpl[0];
                curBufR[0x01] = inst.nsmpl[1];
                for (int ind = 0; ind < (inNow - inst.smpNext); ind++) {
                    streamPnt[0x00][ind] = curBufL[0x02 + ind];
                    streamPnt[0x01][ind] = curBufR[0x02 + ind];
                }
                for (int ind = 0; ind < (inNow - inst.smpNext); ind++) {
                    buff[0][0] = 0;
                    buff[1][0] = 0;
                    inst.instrument.update(inst.id, buff, 1);

                    streamPnt[0][0] = limit((buff[0][0] * mul) >> 15, 0x7fff, -0x8000);
                    streamPnt[1][0] = limit((buff[1][0] * mul) >> 15, 0x7fff, -0x8000);
                }
                for (int ind = 0; ind < (inNow - inst.smpNext); ind++) {
                    curBufL[0x02 + ind] = streamPnt[0x00][ind];
                    curBufR[0x02 + ind] = streamPnt[0x01][ind];
                }

                inBase = FIXPNT_FACT + (inPosL - inst.smpNext * FIXPNT_FACT);
                smpCnt = FIXPNT_FACT;
                inst.smpLast = inPre;
                inst.smpNext = inNow;
                for (outPos = 0x00; outPos < length; outPos++) {
                    inPos = inBase + (int) (FIXPNT_FACT * outPos * chipSmpRate / samplingRate);

                    inPre = fp2i_floor(inPos);
                    inNow = fp2i_ceil(inPos);
                    smpFrc = getfriction(inPos);

                    // linear interpolation
                    tempSmpL = ((long) curBufL[inPre] * (FIXPNT_FACT - smpFrc)) +
                            ((long) curBufL[inNow] * smpFrc);
                    tempSmpR = ((long) curBufR[inPre] * (FIXPNT_FACT - smpFrc)) +
                            ((long) curBufR[inNow] * smpFrc);
                    tempSample[0][outPos] = (int) (tempSmpL / smpCnt);
                    tempSample[1][outPos] = (int) (tempSmpR / smpCnt);
                }
                inst.lsmpl[0] = curBufL[inPre];
                inst.lsmpl[1] = curBufR[inPre];
                inst.nsmpl[0] = curBufL[inNow];
                inst.nsmpl[1] = curBufR[inNow];
                inst.smpP += length;
                break;
            case 0x02: // copying
                inst.smpNext = inst.smpP * inst.samplingRate / samplingRate;
                clearLength = length;
                for (int ind = 0; ind < length; ind++) {
                    buff[0][0] = 0;
                    buff[1][0] = 0;
                    inst.instrument.update(inst.id, buff, 1);

                    streamBufs[0][ind] = limit((buff[0][0] * mul) >> 15, 0x7fff, -0x8000);
                    streamBufs[1][ind] = limit((buff[1][0] * mul) >> 15, 0x7fff, -0x8000);
                }
                for (outPos = 0x00; outPos < length; outPos++) {
                    tempSample[0][outPos] = curBufL[outPos];
                    tempSample[1][outPos] = curBufR[outPos];
                }
                inst.smpP += length;
                inst.smpLast = inst.smpNext;
                break;
            case 0x03: // down sampling
                chipSmpRate = inst.samplingRate;
                inPosL = (int) (FIXPNT_FACT * (inst.smpP + length) * chipSmpRate / samplingRate);
                inst.smpNext = fp2i_ceil(inPosL);

                curBufL[0x00] = inst.lsmpl[0];
                curBufR[0x00] = inst.lsmpl[1];

                for (int ind = 0; ind < (inst.smpNext - inst.smpLast); ind++) {
                    streamPnt[0x00][ind] = curBufL[0x01 + ind];
                    streamPnt[0x01][ind] = curBufR[0x01 + ind];
                }
                for (int ind = 0; ind < (inst.smpNext - inst.smpLast); ind++) {
                    buff[0][0] = 0;
                    buff[1][0] = 0;
                    inst.instrument.update(inst.id, buff, 1);
                    //Debug.printf("%d : %d", i, buff[0][0]);

                    streamPnt[0][ind] = limit((buff[0][0] * mul) >> 15, 0x7fff, -0x8000);
                    streamPnt[1][ind] = limit((buff[1][0] * mul) >> 15, 0x7fff, -0x8000);
                }
                for (int ind = 0; ind < inst.smpNext - inst.smpLast; ind++) {
                    curBufL[0x01 + ind] = streamPnt[0x00][ind];
                    curBufR[0x01 + ind] = streamPnt[0x01][ind];
                }

                inPosL = (int) (FIXPNT_FACT * inst.smpP * chipSmpRate / samplingRate);
                // I'm adding 1.0 to avoid negative indexes
                inBase = FIXPNT_FACT + (inPosL - inst.smpLast * FIXPNT_FACT);
                InPosNext = inBase;
                for (outPos = 0x00; outPos < length; outPos++) {
                    inPos = InPosNext;
                    InPosNext = inBase + (int) (FIXPNT_FACT * (outPos + 1) * chipSmpRate / samplingRate);

                    // first frictional Sample
                    smpFrc = getnfriction(inPos);
                    if (smpFrc != 0) {
                        inPre = fp2i_floor(inPos);
                        tempSmpL = (long) curBufL[inPre] * smpFrc;
                        tempSmpR = (long) curBufR[inPre] * smpFrc;
                    } else {
                        tempSmpL = tempSmpR = 0x00;
                    }
                    smpCnt = smpFrc;

                    // last frictional Sample
                    smpFrc = getfriction(InPosNext);
                    inPre = fp2i_floor(InPosNext);
                    if (smpFrc != 0) {
                        tempSmpL += (long) curBufL[inPre] * smpFrc;
                        tempSmpR += (long) curBufR[inPre] * smpFrc;
                        smpCnt += smpFrc;
                    }

                    // whole Samples in between
                    inNow = fp2i_ceil(inPos);
                    smpCnt += (inPre - inNow) * FIXPNT_FACT; // this is faster
                    while (inNow < inPre) {
                        tempSmpL += (long) curBufL[inNow] * FIXPNT_FACT;
                        tempSmpR += (long) curBufR[inNow] * FIXPNT_FACT;
                        inNow++;
                    }

                    tempSample[0][outPos] = (int) (tempSmpL / smpCnt);
                    tempSample[1][outPos] = (int) (tempSmpR / smpCnt);
                }

                inst.lsmpl[0] = curBufL[inPre];
                inst.lsmpl[1] = curBufR[inPre];
                inst.smpP += length;
                inst.smpLast = inst.smpNext;
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
                for (int j = 0; j < length; j++) {
                    retSample[0][j] += tempSample[0][j];
                    retSample[1][j] += tempSample[1][j];
                }
            }

            //if (tempSample[0][0] != 0) Debug.printf(Level.FINE, "%d %d %d", i, tempSample[0][0], inst.Resampler);
        }
    }

    private int getChipVolume(VGMX_CHP_EXTRA16 tempCX, byte chipId, byte chipNum, byte chipCnt, int sn76496VGMHeaderClock, String strSystemNameE, boolean doubleSSGVol) {
        // chipId: ID of Chip
        //  Bit 7 - Is Paired Chip
        // chipNum: chips number (0 - first chips, 1 - second chips)
        // chipCnt: chips volume divider (number of used chips)
        final int[] CHIP_VOLS = new int[] { // CHIP_COUNT
                0x80, 0x200/*0x155*/, 0x100, 0x100, 0x180, 0xB0, 0x100, 0x80, // 00-07
                0x80, 0x100, 0x100, 0x100, 0x100, 0x100, 0x100, 0x98,   // 08-0F
                0x80, 0xE0/*0xCD*/, 0x100, 0xC0, 0x100, 0x40, 0x11E, 0x1C0,  // 10-17
                0x100/*110*/, 0xA0, 0x100, 0x100, 0x100, 0xB3, 0x100, 0x100, // 18-1F
                0x20, 0x100, 0x100, 0x100, 0x40, 0x20, 0x100, 0x40,   // 20-27
                0x280
        };
        int volume;
        byte curChp;
        //VGMX_CHP_EXTRA16 tempCX;
        VGMX_CHIP_DATA16 tempCD;

        volume = CHIP_VOLS[chipId & 0x7F];
        switch (chipId & 0xff) {
        case 0x00: // Sn76496Inst
            // if T6W28, set volume Divider to 01
            if ((sn76496VGMHeaderClock & 0x80000000) != 0) {
                // The T6W28 consists of 2 "half" chips.
                chipNum = 0x01;
                chipCnt = 0x01;
            }
            break;
        case 0x18: // OkiM6295Inst
            // CP System 1 patch
            if ((strSystemNameE != null && !strSystemNameE.isEmpty()) && strSystemNameE.indexOf("CP") == 0)
                volume = 110;
            break;
        case 0x86: // Ym2203Inst's AY
            volume /= 2;
            break;
        case 0x87: // Ym2608Inst's AY
            // The Ym2608Inst outputs twice as loud as the Ym2203Inst here.
            //volume *= 1;
            break;
        case 0x88: // Ym2610Inst's AY
            //volume *= 1;
            break;
        }
        if (chipCnt > 1)
            volume /= chipCnt;

        for (curChp = 0x00; curChp < tempCX.chipCnt; curChp++) {
            tempCD = tempCX.ccData[curChp];
            if (tempCD.type == chipId && (tempCD.flags & 0x01) == chipNum) {
                // Bit 15 - absolute/relative volume
                // 0 - absolute
                // 1 - relative (0x0100 = 1.0, 0x80 = 0.5, etc.)
                if ((tempCD.data & 0x8000) != 0)
                    volume = (volume * (tempCD.data & 0x7FFF) + 0x80) >> 8;
                else {
                    volume = tempCD.data;
                    if ((chipId & 0x80) != 0 && doubleSSGVol)
                        volume *= 2;
                }
                break;
            }
        }

        return volume;
    }

    public static class VGMX_CHIP_DATA16 {
        public byte type;
        public byte flags;
        public int data;
    }

    public static class VGMX_CHP_EXTRA16 {
        public byte chipCnt;
        public VGMX_CHIP_DATA16[] ccData;
    }

//#region AY8910

    public void writeAY8910(byte chipId, byte adr, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ay8910Inst.class)) return;

            instruments.get(Ay8910Inst.class)[0].write(chipId, 0, adr, data);
        }
    }

    public void writeAY8910(int chipIndex, byte chipId, byte adr, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ay8910Inst.class)) return;

            instruments.get(Ay8910Inst.class)[chipIndex].write(chipId, 0, adr, data);
        }
    }

    public void setVolumeAY8910(int vol) {
        if (!instruments.containsKey(Ay8910Inst.class)) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ay8910Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setAY8910Mask(int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(0)[chipId] |= ch;
            if (!instruments.containsKey(Ay8910Inst.class)) return;
            ((Ay8910Inst) (instruments.get(Ay8910Inst.class)[0])).setMute((byte) chipId, ay8910Mask.get(0)[chipId]);
        }
    }

    public void setAY8910Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(chipIndex)[chipId] |= ch;
            if (!instruments.containsKey(Ay8910Inst.class)) return;
            ((Ay8910Inst) (instruments.get(Ay8910Inst.class)[chipIndex])).setMute((byte) chipId, ay8910Mask.get(chipIndex)[chipId]);
        }
    }

    public void resetAY8910Mask(int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(0)[chipId] &= ~ch;
            if (!instruments.containsKey(Ay8910Inst.class)) return;
            ((Ay8910Inst) (instruments.get(Ay8910Inst.class)[0])).setMute((byte) chipId, ay8910Mask.get(0)[chipId]);
        }
    }

    public void resetAY8910Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(chipIndex)[chipId] &= ~ch;
            if (!instruments.containsKey(Ay8910Inst.class)) return;
            ((Ay8910Inst) (instruments.get(Ay8910Inst.class)[chipIndex])).setMute((byte) chipId, ay8910Mask.get(chipIndex)[chipId]);
        }
    }

//#endregion

//#region MameAy8910Inst

    public void writeAY8910Mame(byte chipId, byte adr, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(MameAy8910Inst.class)) return;
            if (instruments.get(MameAy8910Inst.class)[0] == null) return;

            instruments.get(MameAy8910Inst.class)[0].write(chipId, 0, adr, data);
        }
    }

    public void writeAY8910Mame(int chipIndex, byte chipId, byte adr, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(MameAy8910Inst.class)) return;
            if (instruments.get(MameAy8910Inst.class)[chipIndex] == null) return;

            instruments.get(MameAy8910Inst.class)[chipIndex].write(chipId, 0, adr, data);
        }
    }

    public void setVolumeAY8910Mame(int vol) {
        if (!instruments.containsKey(MameAy8910Inst.class)) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof MameAy8910Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setAy8910MameMask(int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(0)[chipId] |= ch;
            if (!instruments.containsKey(MameAy8910Inst.class)) return;
            if (instruments.get(MameAy8910Inst.class)[0] == null) return;
            ((MameAy8910Inst) (instruments.get(MameAy8910Inst.class)[0])).setMute((byte) chipId, ay8910Mask.get(0)[chipId]);
        }
    }

    public void setAy8910MameMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(chipIndex)[chipId] |= ch;
            if (!instruments.containsKey(MameAy8910Inst.class)) return;
            if (instruments.get(MameAy8910Inst.class)[chipIndex] == null) return;

            ((MameAy8910Inst) (instruments.get(MameAy8910Inst.class)[chipIndex])).setMute((byte) chipId, ay8910Mask.get(chipIndex)[chipId]);
        }
    }

    public void resetAy8910MameMask(int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(0)[chipId] &= ~ch;
            if (!instruments.containsKey(MameAy8910Inst.class)) return;
            if (instruments.get(MameAy8910Inst.class)[0] == null) return;

            ((MameAy8910Inst) (instruments.get(MameAy8910Inst.class)[0])).setMute((byte) chipId, ay8910Mask.get(0)[chipId]);
        }
    }

    public void resetAy8910MameMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(chipIndex)[chipId] &= ~ch;
            if (!instruments.containsKey(MameAy8910Inst.class)) return;
            if (instruments.get(MameAy8910Inst.class)[chipIndex] == null) return;

            ((MameAy8910Inst) (instruments.get(MameAy8910Inst.class)[chipIndex])).setMute((byte) chipId, ay8910Mask.get(chipIndex)[chipId]);
        }
    }

//#endregion

//#region WsAudioInst

    public void writeWsAudio(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(WsAudioInst.class)) return;
            if (instruments.get(WsAudioInst.class)[0] == null) return;

            instruments.get(WsAudioInst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeWsAudio(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(WsAudioInst.class)) return;
            if (instruments.get(WsAudioInst.class)[chipIndex] == null) return;

            instruments.get(WsAudioInst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void writeWsAudioMem(byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(WsAudioInst.class)) return;
            if (instruments.get(WsAudioInst.class)[0] == null) return;

            ((WsAudioInst) (instruments.get(WsAudioInst.class)[0])).WriteMem(chipId, Adr, Data);
        }
    }

    public void writeWsAudioMem(int chipIndex, byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(WsAudioInst.class)) return;
            if (instruments.get(WsAudioInst.class)[0] == null) return;

            ((WsAudioInst) (instruments.get(WsAudioInst.class)[chipIndex])).WriteMem(chipId, Adr, Data);
        }
    }

    public void setVolumeWsAudio(int vol) {
        if (!instruments.containsKey(WsAudioInst.class)) return;
        if (instruments.get(WsAudioInst.class)[0] == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof WsAudioInst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / chips.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setWsAudioMask(int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(0)[chipId] |= ch;
            if (!instruments.containsKey(WsAudioInst.class)) return;
            if (instruments.get(WsAudioInst.class)[0] == null) return;
            ((WsAudioInst) (instruments.get(WsAudioInst.class)[0])).SetMute((byte) chipId, WsAudioMask.get(0)[chipId]);
        }
    }

    public void setWsAudioMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            WsAudioMask.get(chipIndex)[chipId] |= ch;
            if (!instruments.containsKey(WsAudioInst.class)) return;
            if (instruments.get(WsAudioInst.class)[chipIndex] == null) return;
            ((WsAudioInst) (instruments.get(WsAudioInst.class)[chipIndex])).SetMute((byte) chipId, WsAudioMask.get(chipIndex)[chipId]);
        }
    }

    public void resetWsAudioMask(int chipId, int ch) {
        synchronized (lockobj) {
            WsAudioMask.get(0)[chipId] &= ~ch;
            if (!instruments.containsKey(WsAudioInst.class)) return;
            if (instruments.get(WsAudioInst.class)[0] == null) return;
            ((WsAudioInst) (instruments.get(WsAudioInst.class)[0])).SetMute((byte) chipId, WsAudioMask.get(0)[chipId]);
        }
    }

    public void resetWsAudioMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            WsAudioMask.get(chipIndex)[chipId] &= ~ch;
            if (!instruments.containsKey(WsAudioInst.class)) return;
            if (instruments.get(WsAudioInst.class)[chipIndex] == null) return;
            ((WsAudioInst) (instruments.get(WsAudioInst.class)[chipIndex])).SetMute((byte) chipId, WsAudioMask.get(chipIndex)[chipId]);
        }
    }

//#endregion

//#region Saa1099Inst

    public void writeSaa1099(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Saa1099Inst.class)) return;

            instruments.get(Saa1099Inst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeSaa1099(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Saa1099Inst.class)) return;

            instruments.get(Saa1099Inst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void setVolumeSaa1099(int vol) {
        if (!instruments.containsKey(Saa1099Inst.class)) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Saa1099Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / chips.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setSaa1099Mask(int chipId, int ch) {
        synchronized (lockobj) {
            saa1099Mask.get(0)[chipId] |= ch;
            if (!instruments.containsKey(Saa1099Inst.class)) return;
            ((Saa1099Inst) (instruments.get(Saa1099Inst.class)[0])).setMute((byte) chipId, saa1099Mask.get(0)[chipId]);
        }
    }

    public void setSaa1099Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            saa1099Mask.get(chipIndex)[chipId] |= ch;
            if (!instruments.containsKey(Saa1099Inst.class)) return;
            ((Saa1099Inst) (instruments.get(Saa1099Inst.class)[chipIndex])).setMute((byte) chipId, saa1099Mask.get(chipIndex)[chipId]);
        }
    }

    public void resetSaa1099Mask(int chipId, int ch) {
        synchronized (lockobj) {
            saa1099Mask.get(0)[chipId] &= ~ch;
            if (!instruments.containsKey(Saa1099Inst.class)) return;
            ((Saa1099Inst) (instruments.get(Saa1099Inst.class)[0])).setMute((byte) chipId, saa1099Mask.get(0)[chipId]);
        }
    }

    public void resetSaa1099Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            saa1099Mask.get(chipIndex)[chipId] &= ~ch;
            if (!instruments.containsKey(Saa1099Inst.class)) return;
            ((Saa1099Inst) (instruments.get(Saa1099Inst.class)[chipIndex])).setMute((byte) chipId, saa1099Mask.get(chipIndex)[chipId]);
        }
    }

//#endregion

//#region PokeyInst

    public void writePokey(byte chipId, byte adr, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(PokeyInst.class)) return;

            instruments.get(PokeyInst.class)[0].write(chipId, 0, adr, data);
        }
    }

    public void writePokey(int chipIndex, byte chipId, byte adr, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(PokeyInst.class)) return;

            instruments.get(PokeyInst.class)[chipIndex].write(chipId, 0, adr, data);
        }
    }

//#endregion

//#region X1_010Inst

    public void writeX1_010(byte chipId, int adr, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(X1_010Inst.class)) return;

            instruments.get(X1_010Inst.class)[0].write(chipId, 0, adr, data);
        }
    }

    public void writeX1_010(int chipIndex, byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(X1_010Inst.class)) return;

            instruments.get(X1_010Inst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void writeX1_010PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(X1_010Inst.class)) return;

            //((QSoundInst)(instruments.get(QSoundInst.class)[0])).qsound_write_rom(chipId, (int)romSize, (int)dataStart, (int)dataLength, romData, (int)SrcStartAdr);
            ((X1_010Inst) (instruments.get(X1_010Inst.class)[0])).x1_010_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void writeX1_010PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(X1_010Inst.class)) return;

            //((QSoundInst)(instruments.get(QSoundInst.class)[chipIndex])).qsound_write_rom(chipId, (int)romSize, (int)dataStart, (int)dataLength, romData, (int)SrcStartAdr);
            ((X1_010Inst) (instruments.get(X1_010Inst.class)[chipIndex])).x1_010_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void setVolumeX1_010(int vol) {
        if (!instruments.containsKey(X1_010Inst.class)) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof X1_010Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / chips.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setX1_010Mask(int chipId, int ch) {
        synchronized (lockobj) {
            x1_010Mask.get(0)[chipId] |= ch;
            if (!instruments.containsKey(X1_010Inst.class)) return;
            ((X1_010Inst) (instruments.get(X1_010Inst.class)[0])).x1_010_set_mute_mask((byte) chipId, x1_010Mask.get(0)[chipId]);
        }
    }

    public void setX1_010Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            x1_010Mask.get(chipIndex)[chipId] |= ch;
            if (!instruments.containsKey(X1_010Inst.class)) return;
            ((X1_010Inst) (instruments.get(X1_010Inst.class)[chipIndex])).x1_010_set_mute_mask((byte) chipId, x1_010Mask.get(chipIndex)[chipId]);
        }
    }

    public void resetX1_010Mask(int chipId, int ch) {
        synchronized (lockobj) {
            x1_010Mask.get(0)[chipId] &= ~ch;
            if (!instruments.containsKey(X1_010Inst.class)) return;
            ((X1_010Inst) (instruments.get(X1_010Inst.class)[0])).x1_010_set_mute_mask((byte) chipId, x1_010Mask.get(0)[chipId]);
        }
    }

    public void resetX1_010Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            x1_010Mask.get(chipIndex)[chipId] &= ~ch;
            if (!instruments.containsKey(X1_010Inst.class)) return;
            ((X1_010Inst) (instruments.get(X1_010Inst.class)[chipIndex])).x1_010_set_mute_mask((byte) chipId, x1_010Mask.get(chipIndex)[chipId]);
        }
    }

//#endregion

//#region Sn76489Inst

    public void writeSn76489(byte chipId, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Sn76489Inst.class)) return;

            instruments.get(Sn76489Inst.class)[0].write(chipId, 0, 0, Data);
        }
    }

    public void writeSn76489(int chipIndex, byte chipId, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Sn76489Inst.class)) return;

            instruments.get(Sn76489Inst.class)[chipIndex].write(chipId, 0, 0, Data);
        }
    }

    public void writeSn76489GGPanning(byte chipId, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Sn76489Inst.class)) return;

            ((Sn76489Inst) (instruments.get(Sn76489Inst.class)[0])).writeGGStereo(chipId, Data);
        }
    }

    public void writeSn76489GGPanning(int chipIndex, byte chipId, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Sn76489Inst.class)) return;

            ((Sn76489Inst) (instruments.get(Sn76489Inst.class)[chipIndex])).writeGGStereo(chipId, Data);
        }
    }

//#endregion

//#region Sn76496Inst

    public void writeSn76496(byte chipId, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Sn76496Inst.class)) return;

            instruments.get(Sn76496Inst.class)[0].write(chipId, 0, 0, data);
        }
    }

    public void writeSn76496(int chipIndex, byte chipId, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Sn76496Inst.class)) return;

            instruments.get(Sn76496Inst.class)[chipIndex].write(chipId, 0, 0, data);
        }
    }

    public void writeSn76496GGPanning(byte chipId, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Sn76496Inst.class)) return;

            ((Sn76496Inst) (instruments.get(Sn76496Inst.class)[0])).writeGGStereo(chipId, 0, 0, data);
        }
    }

    public void writeSn76496GGPanning(int chipIndex, byte chipId, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Sn76496Inst.class)) return;

            ((Sn76496Inst) (instruments.get(Sn76496Inst.class)[chipIndex])).writeGGStereo(chipId, 0, 0, data);
        }
    }

//#endregion

//#region Ym2612Inst

    public void writeYm2612(byte chipId, byte port, byte adr, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2612Inst.class)) return;

            instruments.get(Ym2612Inst.class)[0].write(chipId, 0, (byte) (0 + (port & 1) * 2), adr);
            instruments.get(Ym2612Inst.class)[0].write(chipId, 0, (byte) (1 + (port & 1) * 2), data);
        }
    }

    public void writeYm2612(int chipIndex, byte chipId, byte port, byte adr, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2612Inst.class)) return;

            instruments.get(Ym2612Inst.class)[chipIndex].write(chipId, 0, (byte) (0 + (port & 1) * 2), adr);
            instruments.get(Ym2612Inst.class)[chipIndex].write(chipId, 0, (byte) (1 + (port & 1) * 2), data);
        }
    }

    public void playPCMYm2612X(int chipIndex, byte chipId, byte port, byte adr, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2612Inst.class)) return;
            if (!(instruments.get(Ym2612Inst.class)[chipIndex] instanceof Ym2612XInst)) return;
            ((Ym2612XInst) instruments.get(Ym2612Inst.class)[chipIndex]).XGMfunction.playPCM(chipId, adr, data);
        }
    }

//#endregion

//#region Ym3438Inst

    public void writeYm3438(byte chipId, byte port, byte adr, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym3438Inst.class)) return;

            instruments.get(Ym3438Inst.class)[0].write(chipId, 0, (byte) (0 + (port & 1) * 2), adr);
            instruments.get(Ym3438Inst.class)[0].write(chipId, 0, (byte) (1 + (port & 1) * 2), data);
        }
    }

    public void writeYm3438(int chipIndex, byte chipId, byte port, byte adr, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym3438Inst.class)) return;

            instruments.get(Ym3438Inst.class)[chipIndex].write(chipId, 0, (byte) (0 + (port & 1) * 2), adr);
            instruments.get(Ym3438Inst.class)[chipIndex].write(chipId, 0, (byte) (1 + (port & 1) * 2), data);
        }
    }

    public void playPCMYm3438X(int chipIndex, byte chipId, byte port, byte adr, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym3438Inst.class)) return;
            if (!(instruments.get(Ym3438Inst.class)[chipIndex] instanceof Ym3438XInst)) return;
            ((Ym3438XInst) instruments.get(Ym3438Inst.class)[chipIndex]).xgmFunction.playPCM(chipId, adr, data);
        }
    }

//#endregion

//#region Ym2612Inst

    public void writeYm2612Mame(byte chipId, byte port, byte adr, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(MameYm2612Inst.class)) return;

            instruments.get(MameYm2612Inst.class)[0].write(chipId, 0, (byte) (0 + (port & 1) * 2), adr);
            instruments.get(MameYm2612Inst.class)[0].write(chipId, 0, (byte) (1 + (port & 1) * 2), data);
        }
    }

    public void writeYm2612Mame(int chipIndex, byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(MameYm2612Inst.class)) return;

            instruments.get(MameYm2612Inst.class)[chipIndex].write(chipId, 0, (byte) (0 + (Port & 1) * 2), Adr);
            instruments.get(MameYm2612Inst.class)[chipIndex].write(chipId, 0, (byte) (1 + (Port & 1) * 2), Data);
        }
    }

    public void playPCMYm2612MameX(int chipIndex, byte chipId, byte port, byte adr, byte data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(MameYm2612Inst.class)) return;
            if (!(instruments.get(MameYm2612Inst.class)[chipIndex] instanceof MameXYm2612Inst)) return;
            ((MameXYm2612Inst) instruments.get(MameYm2612Inst.class)[chipIndex]).XGMfunction.playPCM(chipId, adr, data);
        }
    }

//#endregion

//#region PwmInst

    public void writePwm(byte chipId, byte adr, int data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(PwmInst.class)) return;

            instruments.get(PwmInst.class)[0].write(chipId, 0, adr, data);
            // (byte)((adr & 0xf0)>>4),(int)((adr & 0xf)*0x100+data));
        }
    }

    public void writePwm(int chipIndex, byte chipId, byte adr, int data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(PwmInst.class)) return;

            instruments.get(PwmInst.class)[chipIndex].write(chipId, 0, adr, data);
        }
    }

//#endregion

//#region ScdPcmInst

    public void writeScdPcm(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(ScdPcmInst.class)) return;

            instruments.get(ScdPcmInst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeScdPcm(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(ScdPcmInst.class)) return;

            instruments.get(ScdPcmInst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteScdPcmPCMData(byte chipId, int RAMStartAdr, int RAMdataLength, byte[] SrcData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(ScdPcmInst.class)) return;

            ((ScdPcmInst) (instruments.get(ScdPcmInst.class)[0])).rf5c164_write_ram2(chipId, RAMStartAdr, RAMdataLength, SrcData, SrcStartAdr);
        }
    }

    public void WriteScdPcmPCMData(int chipIndex, byte chipId, int RAMStartAdr, int RAMdataLength, byte[] SrcData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(ScdPcmInst.class)) return;

            ((ScdPcmInst) (instruments.get(ScdPcmInst.class)[chipIndex])).rf5c164_write_ram2(chipId, RAMStartAdr, RAMdataLength, SrcData, SrcStartAdr);
        }
    }

    public void WriteScdPcmMemW(byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(ScdPcmInst.class)) return;

            ((ScdPcmInst) (instruments.get(ScdPcmInst.class)[0])).rf5c164_mem_w(chipId, Adr, Data);
        }
    }

    public void WriteScdPcmMemW(int chipIndex, byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(ScdPcmInst.class)) return;

            ((ScdPcmInst) (instruments.get(ScdPcmInst.class)[chipIndex])).rf5c164_mem_w(chipId, Adr, Data);
        }
    }

//#endregion

//#region Rf5c68Inst

    public void WriteRf5c68(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Rf5c68Inst.class)) return;

            instruments.get(Rf5c68Inst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteRf5c68(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Rf5c68Inst.class)) return;

            instruments.get(Rf5c68Inst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteRf5c68PCMData(byte chipId, int RAMStartAdr, int RAMdataLength, byte[] SrcData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Rf5c68Inst.class)) return;

            ((Rf5c68Inst) (instruments.get(Rf5c68Inst.class)[0])).rf5c68_write_ram2(chipId, RAMStartAdr, RAMdataLength, SrcData, SrcStartAdr);
        }
    }

    public void WriteRf5c68PCMData(int chipIndex, byte chipId, int RAMStartAdr, int RAMdataLength, byte[] SrcData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Rf5c68Inst.class)) return;

            ((Rf5c68Inst) (instruments.get(Rf5c68Inst.class)[chipIndex])).rf5c68_write_ram2(chipId, RAMStartAdr, RAMdataLength, SrcData, SrcStartAdr);
        }
    }

    public void WriteRf5c68MemW(byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Rf5c68Inst.class)) return;

            ((Rf5c68Inst) (instruments.get(Rf5c68Inst.class)[0])).rf5c68_mem_w(chipId, Adr, Data);
        }
    }

    public void WriteRf5c68MemW(int chipIndex, byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Rf5c68Inst.class)) return;

            ((Rf5c68Inst) (instruments.get(Rf5c68Inst.class)[chipIndex])).rf5c68_mem_w(chipId, Adr, Data);
        }
    }

//#endregion

//#region C140Inst

    public void WriteC140(byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(C140Inst.class)) return;

            instruments.get(C140Inst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteC140(int chipIndex, byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(C140Inst.class)) return;

            instruments.get(C140Inst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteC140PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(C140Inst.class)) return;

            ((C140Inst) (instruments.get(C140Inst.class)[0])).c140_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteC140PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(C140Inst.class)) return;

            ((C140Inst) (instruments.get(C140Inst.class)[chipIndex])).c140_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion

//#region Ym3812Inst

    public void writeYm3812(int chipId, int rAdr, int rDat) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym3812Inst.class)) return;

            instruments.get(Ym3812Inst.class)[0].write((byte) chipId, 0, rAdr, rDat);
        }
    }

    public void writeYm3812(int chipIndex, int chipId, int rAdr, int rDat) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym3812Inst.class)) return;

            instruments.get(Ym3812Inst.class)[chipIndex].write((byte) chipId, 0, rAdr, rDat);
        }
    }

//#endregion

//#region C352Inst

    public void WriteC352(byte chipId, int Adr, int Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(C352Inst.class)) return;

            instruments.get(C352Inst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteC352(int chipIndex, byte chipId, int Adr, int Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(C352Inst.class)) return;

            instruments.get(C352Inst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteC352PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(C352Inst.class)) return;

            ((C352Inst) (instruments.get(C352Inst.class)[0])).c352_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteC352PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(C352Inst.class)) return;

            ((C352Inst) (instruments.get(C352Inst.class)[chipIndex])).c352_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion

//#region YmF271Inst

    public void WriteYmf271PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmF271Inst.class)) return;

            ((YmF271Inst) (instruments.get(YmF271Inst.class)[0])).ymf271_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteYmf271PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmF271Inst.class)) return;

            ((YmF271Inst) (instruments.get(YmF271Inst.class)[chipIndex])).ymf271_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion

//#region YmF278bInst

    public void WriteYmF278bPCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmF278bInst.class)) return;

            ((YmF278bInst) (instruments.get(YmF278bInst.class)[0])).ymf278b_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteYmF278bPCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmF278bInst.class)) return;

            ((YmF278bInst) (instruments.get(YmF278bInst.class)[chipIndex])).ymf278b_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteYmF278bPCMramData(byte chipId, int RAMSize, int dataStart, int dataLength, byte[] ramData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmF278bInst.class)) return;

            ((YmF278bInst) (instruments.get(YmF278bInst.class)[0])).ymf278b_write_ram(chipId, dataStart, dataLength, ramData, SrcStartAdr);
        }
    }

    public void WriteYmF278bPCMramData(int chipIndex, byte chipId, int RAMSize, int dataStart, int dataLength, byte[] ramData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmF278bInst.class)) return;

            ((YmF278bInst) (instruments.get(YmF278bInst.class)[chipIndex])).ymf278b_write_ram(chipId, dataStart, dataLength, ramData, SrcStartAdr);
        }
    }

//#endregion

//#region YmZ280bInst

    public void WriteYmZ280bPCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmZ280bInst.class)) return;

            ((YmZ280bInst) (instruments.get(YmZ280bInst.class)[0])).ymz280b_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteYmZ280bPCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmZ280bInst.class)) return;

            ((YmZ280bInst) (instruments.get(YmZ280bInst.class)[chipIndex])).ymz280b_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion

//#region Y8950Inst

    public void WriteY8950PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Y8950Inst.class)) return;

            ((Y8950Inst) (instruments.get(Y8950Inst.class)[0])).y8950_write_data_pcmrom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteY8950PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Y8950Inst.class)) return;

            ((Y8950Inst) (instruments.get(Y8950Inst.class)[chipIndex])).y8950_write_data_pcmrom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion

//#region OkiM6258Inst

    public void writeOkiM6258(byte chipId, byte Port, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(OkiM6258Inst.class)) return;

            instruments.get(OkiM6258Inst.class)[0].write(chipId, 0, Port, Data);
        }
    }

    public void writeOkiM6258(int chipIndex, byte chipId, byte Port, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(OkiM6258Inst.class)) return;

            instruments.get(OkiM6258Inst.class)[chipIndex].write(chipId, 0, Port, Data);
        }
    }

//#endregion

//#region OkiM6295Inst

    public void WriteOkiM6295(byte chipId, byte Port, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(OkiM6295Inst.class)) return;

            instruments.get(OkiM6295Inst.class)[0].write(chipId, 0, Port, Data);
        }
    }

    public void WriteOkiM6295(int chipIndex, byte chipId, byte Port, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(OkiM6295Inst.class)) return;

            instruments.get(OkiM6295Inst.class)[chipIndex].write(chipId, 0, Port, Data);
        }
    }

    public void WriteOkiM6295PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(OkiM6295Inst.class)) return;

            ((OkiM6295Inst) (instruments.get(OkiM6295Inst.class)[0])).okim6295_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteOkiM6295PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(OkiM6295Inst.class)) return;

            ((OkiM6295Inst) (instruments.get(OkiM6295Inst.class)[chipIndex])).okim6295_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion

//#region SegaPcmInst

    public void WriteSegaPcm(byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(SegaPcmInst.class)) return;

            instruments.get(SegaPcmInst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteSegaPcm(int chipIndex, byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(SegaPcmInst.class)) return;

            instruments.get(SegaPcmInst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteSegaPcmPCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(SegaPcmInst.class)) return;

            ((SegaPcmInst) (instruments.get(SegaPcmInst.class)[0])).sega_pcm_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteSegaPcmPCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(SegaPcmInst.class)) return;

            ((SegaPcmInst) (instruments.get(SegaPcmInst.class)[chipIndex])).sega_pcm_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion

//#region Ym2151Inst

    public void writeYm2151(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2151Inst.class)) return;

            ((instruments.get(Ym2151Inst.class)[0])).write(chipId, 0, Adr, Data);
        }
    }

    public void writeYm2151(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2151Inst.class)) return;

            ((instruments.get(Ym2151Inst.class)[chipIndex])).write(chipId, 0, Adr, Data);
        }
    }

    public void WriteYm2151Mame(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(MameYm2151Inst.class)) return;

            ((instruments.get(MameYm2151Inst.class)[0])).write(chipId, 0, Adr, Data);
        }
    }

    public void WriteYm2151Mame(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(MameYm2151Inst.class)) return;

            ((instruments.get(MameYm2151Inst.class)[chipIndex])).write(chipId, 0, Adr, Data);
        }
    }

    public void WriteYm2151X68Sound(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(X68SoundYm2151Inst.class)) return;

            ((instruments.get(X68SoundYm2151Inst.class)[0])).write(chipId, 0, Adr, Data);
        }
    }

    public void WriteYm2151X68Sound(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(X68SoundYm2151Inst.class)) return;

            ((instruments.get(X68SoundYm2151Inst.class)[chipIndex])).write(chipId, 0, Adr, Data);
        }
    }

//#endregion

//#region Ym2203Inst

    public void writeYm2203(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2203Inst.class)) return;

            instruments.get(Ym2203Inst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeYm2203(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2203Inst.class)) return;

            instruments.get(Ym2203Inst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

//#endregion

//#region Ym2608Inst

    public void writeYm2608(byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2608Inst.class)) return;

            instruments.get(Ym2608Inst.class)[0].write(chipId, 0, (Port * 0x100 + Adr), Data);
        }
    }

    public void writeYm2608(int chipIndex, byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2608Inst.class)) return;

            instruments.get(Ym2608Inst.class)[chipIndex].write(chipId, 0, (Port * 0x100 + Adr), Data);
        }
    }

    public byte[] GetADPCMBufferYm2608(byte chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2608Inst.class)) return null;

            return ((Ym2608Inst) (instruments.get(Ym2608Inst.class)[0])).GetADPCMBuffer(chipId);
        }
    }

    public byte[] GetADPCMBufferYm2608(int chipIndex, byte chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2608Inst.class)) return null;

            return ((Ym2608Inst) (instruments.get(Ym2608Inst.class)[chipIndex])).GetADPCMBuffer(chipId);
        }
    }

    public int ReadStatusExYm2608(byte chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2608Inst.class)) throw new IllegalStateException();

            return ((Ym2608Inst) (instruments.get(Ym2608Inst.class)[0])).ReadStatusEx(chipId);
        }
    }

    public int ReadStatusExYm2608(int chipIndex, byte chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2608Inst.class)) throw new IllegalStateException();

            return ((Ym2608Inst) (instruments.get(Ym2608Inst.class)[chipIndex])).ReadStatusEx(chipId);
        }
    }

//#endregion

//#region Ym2609Inst

    public void writeYm2609(byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2609Inst.class)) return;

            instruments.get(Ym2609Inst.class)[0].write(chipId, 0, (Port * 0x100 + Adr), Data);
        }
    }

    public void writeYm2609(int chipIndex, byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2609Inst.class)) return;

            instruments.get(Ym2609Inst.class)[chipIndex].write(chipId, 0, (Port * 0x100 + Adr), Data);
        }
    }

    public void WriteYm2609_SetAdpcmA(byte chipId, byte[] Buf) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2609Inst.class)) return;

            ((Ym2609Inst) (instruments.get(Ym2609Inst.class)[0])).SetAdpcmA(chipId, Buf, Buf.length);
        }
    }

    public void WriteYm2609_SetAdpcmA(int chipIndex, byte chipId, byte[] Buf) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2609Inst.class)) return;

            ((Ym2609Inst) (instruments.get(Ym2609Inst.class)[chipIndex])).SetAdpcmA(chipId, Buf, Buf.length);
        }
    }

//#endregion


//#region Ym2610Inst

    public void writeYm2610(byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2610Inst.class)) return;

            instruments.get(Ym2610Inst.class)[0].write(chipId, 0, (Port * 0x100 + Adr), Data);
        }
    }

    public void writeYm2610(int chipIndex, byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2610Inst.class)) return;

            instruments.get(Ym2610Inst.class)[chipIndex].write(chipId, 0, (Port * 0x100 + Adr), Data);
        }
    }

    public void writeYm2610SetAdpcmA(byte chipId, byte[] Buf) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2610Inst.class)) return;

            ((Ym2610Inst) (instruments.get(Ym2610Inst.class)[0])).setAdpcmA(chipId, Buf, Buf.length);
        }
    }

    public void writeYm2610SetAdpcmA(int chipIndex, byte chipId, byte[] Buf) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2610Inst.class)) return;

            ((Ym2610Inst) (instruments.get(Ym2610Inst.class)[chipIndex])).setAdpcmA(chipId, Buf, Buf.length);
        }
    }

    public void writeYm2610SetAdpcmB(byte chipId, byte[] Buf) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2610Inst.class)) return;

            ((Ym2610Inst) (instruments.get(Ym2610Inst.class)[0])).setAdpcmB(chipId, Buf, Buf.length);
        }
    }

    public void writeYm2610SetAdpcmB(int chipIndex, byte chipId, byte[] Buf) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2610Inst.class)) return;

            ((Ym2610Inst) (instruments.get(Ym2610Inst.class)[chipIndex])).setAdpcmB(chipId, Buf, Buf.length);
        }
    }

//#endregion


//#region YmF262Inst

    public void writeYmF262(byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmF262Inst.class)) return;

            instruments.get(YmF262Inst.class)[0].write(chipId, 0, (Port * 0x100 + Adr), Data);
        }
    }

    public void writeYmF262(int chipIndex, byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmF262Inst.class)) return;

            instruments.get(YmF262Inst.class)[chipIndex].write(chipId, 0, (Port * 0x100 + Adr), Data);
        }
    }

//#endregion


//#region YmF271Inst

    public void writeYmf271(byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmF271Inst.class)) return;

            instruments.get(YmF271Inst.class)[0].write(chipId, Port, Adr, Data);
        }
    }

    public void writeYmf271(int chipIndex, byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmF271Inst.class)) return;

            instruments.get(YmF271Inst.class)[chipIndex].write(chipId, Port, Adr, Data);
        }
    }

//#endregion


//#region YmF278bInst

    public void writeYmF278b(byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmF278bInst.class)) return;
            instruments.get(YmF278bInst.class)[0].write(chipId, Port, Adr, Data);
        }
    }

    public void writeYmF278b(int chipIndex, byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmF278bInst.class)) return;
            instruments.get(YmF278bInst.class)[chipIndex].write(chipId, Port, Adr, Data);
        }
    }

//#endregion


//#region Ym3526Inst

    public void writeYm3526(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym3526Inst.class)) return;

            instruments.get(Ym3526Inst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeYm3526(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym3526Inst.class)) return;

            instruments.get(Ym3526Inst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

//#endregion


//#region Y8950Inst

    public void writeY8950(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Y8950Inst.class)) return;

            instruments.get(Y8950Inst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeY8950(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Y8950Inst.class)) return;

            instruments.get(Y8950Inst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

//#endregion


//#region YmZ280bInst

    public void writeYmZ280b(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmZ280bInst.class)) return;

            instruments.get(YmZ280bInst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeYmZ280b(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmZ280bInst.class)) return;

            instruments.get(YmZ280bInst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

//#endregion


//#region HuC6280Inst

    public void writeOotakePsg(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(HuC6280Inst.class)) return;

            instruments.get(HuC6280Inst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeOotakePsg(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(HuC6280Inst.class)) return;

            instruments.get(HuC6280Inst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public byte ReadOotakePsg(byte chipId, byte Adr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(HuC6280Inst.class)) return 0;

            return ((HuC6280Inst) (instruments.get(HuC6280Inst.class)[0])).read(chipId, Adr);
        }
    }

    public byte ReadOotakePsg(int chipIndex, byte chipId, byte Adr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(HuC6280Inst.class)) return 0;

            return ((HuC6280Inst) (instruments.get(HuC6280Inst.class)[chipIndex])).read(chipId, Adr);
        }
    }

//#endregion


//#region Ga20Inst

    public void WriteIremga20(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ga20Inst.class)) return;

            instruments.get(Ga20Inst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteIremga20(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ga20Inst.class)) return;

            instruments.get(Ga20Inst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

//#endregion


//#region Ym2413Inst

    public void writeYm2413(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2413Inst.class)) return;

            instruments.get(Ym2413Inst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeYm2413(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2413Inst.class)) return;

            instruments.get(Ym2413Inst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

//#endregion


//#region K051649Inst

    public void WriteK051649(byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(K051649Inst.class)) return;

            instruments.get(K051649Inst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteK051649(int chipIndex, byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(K051649Inst.class)) return;

            instruments.get(K051649Inst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

//#endregion


//#region K053260Inst

    public void WriteK053260(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(K053260Inst.class)) return;

            instruments.get(K053260Inst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteK053260(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(K053260Inst.class)) return;

            instruments.get(K053260Inst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteK053260PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(K053260Inst.class)) return;

            ((K053260Inst) (instruments.get(K053260Inst.class)[0])).k053260_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteK053260PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(K053260Inst.class)) return;

            ((K053260Inst) (instruments.get(K053260Inst.class)[chipIndex])).k053260_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion


//#region K054539Inst

    public void WriteK054539(byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(K054539Inst.class)) return;

            instruments.get(K054539Inst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteK054539(int chipIndex, byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(K054539Inst.class)) return;

            instruments.get(K054539Inst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void writeK054539PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(K054539Inst.class)) return;

            ((K054539Inst) (instruments.get(K054539Inst.class)[0])).k054539_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void writeK054539PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(K054539Inst.class)) return;

            ((K054539Inst) (instruments.get(K054539Inst.class)[chipIndex])).k054539_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion


//#region Ppz8Inst

    public void WritePPZ8(byte chipId, int port, int address, int data, byte[] addtionalData) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ppz8Inst.class)) return;

            //if (port == 0x03) {
            //    ((Ppz8Inst)(instruments.get(Ppz8Inst.class)[0])).LoadPcm(chipId, (byte)address, (byte)data, addtionalData);
            //} else {
            instruments.get(Ppz8Inst.class)[0].write(chipId, port, address, data);
            //}
        }
    }

    public void WritePPZ8(int chipIndex, byte chipId, int port, int address, int data, byte[] addtionalData) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ppz8Inst.class)) return;

            //if (port == 0x03) {
            //    ((Ppz8Inst)(instruments.get(Ppz8Inst.class)[0])).LoadPcm(chipId, (byte)address, (byte)data, addtionalData);
            //} else {
            instruments.get(Ppz8Inst.class)[chipIndex].write(chipId, port, address, data);
            //}
        }
    }

    public void WritePPZ8PCMData(byte chipId, int address, int data, byte[][] PCMData) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ppz8Inst.class)) return;

            ((Ppz8Inst) (instruments.get(Ppz8Inst.class)[0])).loadPcm(chipId, (byte) address, (byte) data, PCMData);
        }
    }

    public void WritePPZ8PCMData(int chipIndex, byte chipId, int address, int data, byte[][] PCMData) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ppz8Inst.class)) return;

            ((Ppz8Inst) (instruments.get(Ppz8Inst.class)[chipIndex])).loadPcm(chipId, (byte) address, (byte) data, PCMData);
        }
    }

//#endregion


//#region PpsDrvInst

    public void WritePPSDRV(byte chipId, int port, int address, int data, byte[] addtionalData) {
        synchronized (lockobj) {
            if (!instruments.containsKey(PpsDrvInst.class)) return;

            instruments.get(PpsDrvInst.class)[0].write(chipId, port, address, data);
        }
    }

    public void WritePPSDRV(int chipIndex, byte chipId, int port, int address, int data, byte[] addtionalData) {
        synchronized (lockobj) {
            if (!instruments.containsKey(PpsDrvInst.class)) return;

            instruments.get(PpsDrvInst.class)[chipIndex].write(chipId, port, address, data);
        }
    }

    public void WritePPSDRVPCMData(byte chipId, byte[] PCMData) {
        synchronized (lockobj) {
            if (!instruments.containsKey(PpsDrvInst.class)) return;

            ((PpsDrvInst) (instruments.get(PpsDrvInst.class)[0])).load(chipId, PCMData);
        }
    }

    public void WritePPSDRVPCMData(int chipIndex, byte chipId, byte[] PCMData) {
        synchronized (lockobj) {
            if (!instruments.containsKey(PpsDrvInst.class)) return;

            ((PpsDrvInst) (instruments.get(PpsDrvInst.class)[chipIndex])).load(chipId, PCMData);
        }
    }

//#endregion


//#region P86Inst

    public void writeP86(byte chipId, int port, int address, int data, byte[] addtionalData) {
        synchronized (lockobj) {
            if (!instruments.containsKey(P86Inst.class)) return;

            //if (port == 0x03) {
            //    ((P86Inst)(instruments.get(P86Inst.class)[0])).LoadPcm(chipId, (byte)address, (byte)data, addtionalData);
            //} else {
            instruments.get(P86Inst.class)[0].write(chipId, port, address, data);
            //}
        }
    }

    public void writeP86(int chipIndex, byte chipId, int port, int address, int data, byte[] addtionalData) {
        synchronized (lockobj) {
            if (!instruments.containsKey(P86Inst.class)) return;

            //if (port == 0x03) {
            //    ((P86Inst)(instruments.get(P86Inst.class)[0])).LoadPcm(chipId, (byte)address, (byte)data, addtionalData);
            //} else {
            instruments.get(P86Inst.class)[chipIndex].write(chipId, port, address, data);
            //}
        }
    }

    public void writeP86PCMData(byte chipId, int address, int data, byte[] pcmData) {
        synchronized (lockobj) {
            if (!instruments.containsKey(P86Inst.class)) return;

            ((P86Inst) (instruments.get(P86Inst.class)[0])).loadPcm(chipId, (byte) address, (byte) data, pcmData);
        }
    }

    public void writeP86PCMData(int chipIndex, byte chipId, int address, int data, byte[] pcmData) {
        synchronized (lockobj) {
            if (!instruments.containsKey(P86Inst.class)) return;

            ((P86Inst) (instruments.get(P86Inst.class)[chipIndex])).loadPcm(chipId, (byte) address, (byte) data, pcmData);
        }
    }

//#endregion


//#region QSoundInst

    public void writeQSound(byte chipId, int adr, byte dat) {
        synchronized (lockobj) {
            if (!instruments.containsKey(QSoundInst.class)) return;

            ((QSoundInst) (instruments.get(QSoundInst.class)[0])).qsound_w(chipId, adr, dat);
        }
    }

    public void writeQSound(int chipIndex, byte chipId, int adr, byte dat) {
        synchronized (lockobj) {
            if (!instruments.containsKey(QSoundInst.class)) return;

            ((QSoundInst) (instruments.get(QSoundInst.class)[chipIndex])).qsound_w(chipId, adr, dat);
        }
    }

    public void writeQSoundPCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(QSoundInst.class)) return;

            ((QSoundInst) (instruments.get(QSoundInst.class)[0])).qsound_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void writeQSoundPCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(QSoundInst.class)) return;

            ((QSoundInst) (instruments.get(QSoundInst.class)[chipIndex])).qsound_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion


//#region CtrQSoundInst

    public void WriteQSoundCtr(byte chipId, int adr, byte dat) {
        synchronized (lockobj) {
            if (!instruments.containsKey(CtrQSoundInst.class)) return;

            //((QSoundInst)(instruments.get(QSoundInst.class)[0])).qsound_w(chipId, adr, dat);
            ((CtrQSoundInst) (instruments.get(CtrQSoundInst.class)[0])).qsound_w(chipId, adr, dat);
        }
    }

    public void WriteQSoundCtr(int chipIndex, byte chipId, int adr, byte dat) {
        synchronized (lockobj) {
            if (!instruments.containsKey(CtrQSoundInst.class)) return;

            //((QSoundInst)(instruments.get(QSoundInst.class)[chipIndex])).qsound_w(chipId, adr, dat);
            ((CtrQSoundInst) (instruments.get(CtrQSoundInst.class)[chipIndex])).qsound_w(chipId, adr, dat);
        }
    }

    public void WriteQSoundCtrPCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(CtrQSoundInst.class)) return;

            //((QSoundInst)(instruments.get(QSoundInst.class)[0])).qsound_write_rom(chipId, (int)romSize, (int)dataStart, (int)dataLength, romData, (int)SrcStartAdr);
            ((CtrQSoundInst) (instruments.get(CtrQSoundInst.class)[0])).qsound_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteQSoundCtrPCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(CtrQSoundInst.class)) return;

            //((QSoundInst)(instruments.get(QSoundInst.class)[chipIndex])).qsound_write_rom(chipId, (int)romSize, (int)dataStart, (int)dataLength, romData, (int)SrcStartAdr);
            ((CtrQSoundInst) (instruments.get(CtrQSoundInst.class)[chipIndex])).qsound_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion


//#region Ga20Inst

    public void WriteIremga20PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ga20Inst.class)) return;

            ((Ga20Inst) (instruments.get(Ga20Inst.class)[0])).iremga20_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteIremga20PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ga20Inst.class)) return;

            ((Ga20Inst) (instruments.get(Ga20Inst.class)[chipIndex])).iremga20_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion


//#region DmgInst

    public void writeGb(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(DmgInst.class)) return;

            instruments.get(DmgInst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeGb(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(DmgInst.class)) return;

            instruments.get(DmgInst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public GbSound ReadGb(byte chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(DmgInst.class)) return null;

            return ((DmgInst) (instruments.get(DmgInst.class)[0])).getSoundData(chipId);
        }

    }

    public GbSound ReadGb(int chipIndex, byte chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(DmgInst.class)) return null;

            return ((DmgInst) (instruments.get(DmgInst.class)[chipIndex])).getSoundData(chipId);
        }

    }

    public void setGbMask(byte chipId, int ch) {
        synchronized (lockobj) {
            if (!instruments.containsKey(DmgInst.class)) return;
            if (instruments.get(DmgInst.class)[0] == null) return;

            int maskStatus = ((DmgInst) (instruments.get(DmgInst.class)[0])).getMuteMask(chipId);
            maskStatus |= 1 << ch;//ch:0 - 3
            ((DmgInst) (instruments.get(DmgInst.class)[0])).setMuteMask(chipId, maskStatus);
        }
    }

    public void resetGbMask(byte chipId, int ch) {
        synchronized (lockobj) {
            if (!instruments.containsKey(DmgInst.class)) return;
            if (instruments.get(DmgInst.class)[0] == null) return;

            int maskStatus = ((DmgInst) (instruments.get(DmgInst.class)[0])).getMuteMask(chipId);
            maskStatus &= ~(1 << ch);//ch:0 - 3
            ((DmgInst) (instruments.get(DmgInst.class)[0])).setMuteMask(chipId, maskStatus);
        }
    }

    public void setGbMask(int chipIndex, byte chipId, int ch) {
        synchronized (lockobj) {
            if (!instruments.containsKey(DmgInst.class)) return;
            if (instruments.get(DmgInst.class)[chipIndex] == null) return;

            int maskStatus = ((DmgInst) (instruments.get(DmgInst.class)[chipIndex])).getMuteMask(chipId);
            maskStatus |= 1 << ch;//ch:0 - 3
            ((DmgInst) (instruments.get(DmgInst.class)[chipIndex])).setMuteMask(chipId, maskStatus);
        }
    }

    public void resetGbMask(int chipIndex, byte chipId, int ch) {
        synchronized (lockobj) {
            if (!instruments.containsKey(DmgInst.class)) return;
            if (instruments.get(DmgInst.class)[chipIndex] == null) return;

            int maskStatus = ((DmgInst) (instruments.get(DmgInst.class)[chipIndex])).getMuteMask(chipId);
            maskStatus &= ~(1 << ch);//ch:0 - 3
            ((DmgInst) (instruments.get(DmgInst.class)[chipIndex])).setMuteMask(chipId, maskStatus);
        }
    }

//#endregion


//#region NES

    public void writeNES(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(IntFNesInst.class)) return;

            instruments.get(IntFNesInst.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeNES(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(IntFNesInst.class)) return;

            instruments.get(IntFNesInst.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteNESRam(byte chipId, int dataStart, int dataLength, byte[] ramData, int RAMdataStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(IntFNesInst.class)) return;

            ((IntFNesInst) (instruments.get(IntFNesInst.class)[0])).nes_write_ram(chipId, dataStart, dataLength, ramData, RAMdataStartAdr);
        }
    }

    public void WriteNESRam(int chipIndex, byte chipId, int dataStart, int dataLength, byte[] ramData, int RAMdataStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(IntFNesInst.class)) return;

            ((IntFNesInst) (instruments.get(IntFNesInst.class)[chipIndex])).nes_write_ram(chipId, dataStart, dataLength, ramData, RAMdataStartAdr);
        }
    }

    public byte[] ReadNESapu(byte chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(IntFNesInst.class)) return null;

            return ((IntFNesInst) (instruments.get(IntFNesInst.class)[0])).nes_r_apu(chipId);
        }
    }

    public byte[] ReadNESapu(int chipIndex, byte chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(IntFNesInst.class)) return null;

            return ((IntFNesInst) (instruments.get(IntFNesInst.class)[chipIndex])).nes_r_apu(chipId);
        }
    }

    public byte[] ReadNESdmc(byte chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(IntFNesInst.class)) return null;

            return ((IntFNesInst) (instruments.get(IntFNesInst.class)[0])).nes_r_dmc(chipId);
        }
    }

    public byte[] ReadNESdmc(int chipIndex, byte chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(IntFNesInst.class)) return null;

            return ((IntFNesInst) (instruments.get(IntFNesInst.class)[chipIndex])).nes_r_dmc(chipId);
        }
    }

//#endregion


//#region Vrc6Inst

    int[] vrc6AddressTable = new int[] {
            0x9000, 0x9001, 0x9002, 0x9003,
            0xa000, 0xa001, 0xa002, 0xa003,
            0xb000, 0xb001, 0xb002, 0xb003
    };

    public void WriteVRC6(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Vrc6Inst.class)) return;

            instruments.get(Vrc6Inst.class)[chipIndex].write(chipId, 0, vrc6AddressTable[Adr], Data);
        }
    }

//#endregion


//#region MultiPCM

    public void WriteMultiPCM(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(MultiPCM.class)) return;

            instruments.get(MultiPCM.class)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteMultiPCM(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(MultiPCM.class)) return;

            instruments.get(MultiPCM.class)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteMultiPCMSetBank(byte chipId, byte Ch, int Adr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(MultiPCM.class)) return;

            ((MultiPcmInst) (instruments.get(MultiPCM.class)[0])).multipcm_bank_write(chipId, Ch, Adr);
        }
    }

    public void WriteMultiPCMSetBank(int chipIndex, byte chipId, byte Ch, int Adr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(MultiPCM.class)) return;

            ((MultiPcmInst) (instruments.get(MultiPCM.class)[chipIndex])).multipcm_bank_write(chipId, Ch, Adr);
        }
    }

    public void WriteMultiPCMPCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(MultiPCM.class)) return;

            ((MultiPcmInst) (instruments.get(MultiPCM.class)[0])).multipcm_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteMultiPCMPCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!instruments.containsKey(MultiPCM.class)) return;

            ((MultiPcmInst) (instruments.get(MultiPCM.class)[chipIndex])).multipcm_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion


//#region FDS

    public NpNesFds readFDS(byte chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(IntFNesInst.class)) return null;

            return ((IntFNesInst) (instruments.get(IntFNesInst.class)[0])).nes_r_fds(chipId);
        }
    }

    public NpNesFds readFDS(int chipIndex, byte chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(IntFNesInst.class)) return null;

            return ((IntFNesInst) (instruments.get(IntFNesInst.class)[chipIndex])).nes_r_fds(chipId);
        }
    }

//#endregion

//#region SetVolume

    public void setVolumeYm2151(int vol) {
        if (!instruments.containsKey(Ym2151Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2151Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / chips.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setVolumeYm2151Mame(int vol) {
        if (!instruments.containsKey(MameYm2151Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof MameYm2151Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / chips.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYm2151X68Sound(int vol) {
        if (!instruments.containsKey(X68SoundYm2151Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof X68SoundYm2151Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / chips.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYm2203(int vol) {
        if (!instruments.containsKey(Ym2203Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2203Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / chips.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYm2203FM(int vol) {
        if (!instruments.containsKey(Ym2203Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2203Inst)) continue;
            ((Ym2203Inst) c.instrument).SetFMVolume((byte) 0, vol);
            ((Ym2203Inst) c.instrument).SetFMVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYm2203PSG(int vol) {
        if (!instruments.containsKey(Ym2203Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2203Inst)) continue;
            ((Ym2203Inst) c.instrument).SetPSGVolume((byte) 0, vol);
            ((Ym2203Inst) c.instrument).SetPSGVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYm2413(int vol) {
        if (!instruments.containsKey(Ym2413Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2413Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / chips.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setVolumeOotakePsg(int vol) {
        if (!instruments.containsKey(HuC6280Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof HuC6280Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / chips.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYm2608(int vol) {
        if (!instruments.containsKey(Ym2608Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2608Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / chips.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYm2608FM(int vol) {
        if (!instruments.containsKey(Ym2608Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2608Inst)) continue;
            ((Ym2608Inst) c.instrument).SetFMVolume((byte) 0, vol);
            ((Ym2608Inst) c.instrument).SetFMVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYm2608PSG(int vol) {
        if (!instruments.containsKey(Ym2608Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2608Inst)) continue;
            ((Ym2608Inst) c.instrument).SetPSGVolume((byte) 0, vol);
            ((Ym2608Inst) c.instrument).SetPSGVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYm2608Rhythm(int vol) {
        if (!instruments.containsKey(Ym2608Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2608Inst)) continue;
            ((Ym2608Inst) c.instrument).SetRhythmVolume((byte) 0, vol);
            ((Ym2608Inst) c.instrument).SetRhythmVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYm2608Adpcm(int vol) {
        if (!instruments.containsKey(Ym2608Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2608Inst)) continue;
            ((Ym2608Inst) c.instrument).SetAdpcmVolume((byte) 0, vol);
            ((Ym2608Inst) c.instrument).SetAdpcmVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYm2609FM(int vol) {
        if (!instruments.containsKey(Ym2609Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2609Inst)) continue;
            ((Ym2609Inst) c.instrument).SetFMVolume((byte) 0, vol);
            ((Ym2609Inst) c.instrument).SetFMVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYm2609PSG(int vol) {
        if (!instruments.containsKey(Ym2609Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2609Inst)) continue;
            ((Ym2609Inst) c.instrument).SetPSGVolume((byte) 0, vol);
            ((Ym2609Inst) c.instrument).SetPSGVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYm2609Rhythm(int vol) {
        if (!instruments.containsKey(Ym2609Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2609Inst)) continue;
            ((Ym2609Inst) c.instrument).SetRhythmVolume((byte) 0, vol);
            ((Ym2609Inst) c.instrument).SetRhythmVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYm2609Adpcm(int vol) {
        if (!instruments.containsKey(Ym2609Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2609Inst)) continue;
            ((Ym2609Inst) c.instrument).SetAdpcmVolume((byte) 0, vol);
            ((Ym2609Inst) c.instrument).SetAdpcmVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYm2610(int vol) {
        if (!instruments.containsKey(Ym2610Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2610Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / chips.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYm2610FM(int vol) {
        if (!instruments.containsKey(Ym2610Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2610Inst)) continue;
            ((Ym2610Inst) c.instrument).SetFMVolume((byte) 0, vol);
            ((Ym2610Inst) c.instrument).SetFMVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYm2610PSG(int vol) {
        if (!instruments.containsKey(Ym2610Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2610Inst)) continue;
            ((Ym2610Inst) c.instrument).SetPSGVolume((byte) 0, vol);
            ((Ym2610Inst) c.instrument).SetPSGVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYm2610AdpcmA(int vol) {
        if (!instruments.containsKey(Ym2610Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2610Inst)) continue;
            ((Ym2610Inst) c.instrument).SetAdpcmAVolume((byte) 0, vol);
            ((Ym2610Inst) c.instrument).SetAdpcmAVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYm2610AdpcmB(int vol) {
        if (!instruments.containsKey(Ym2610Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2610Inst)) continue;
            ((Ym2610Inst) c.instrument).SetAdpcmBVolume((byte) 0, vol);
            ((Ym2610Inst) c.instrument).SetAdpcmBVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYm2612(int vol) {
        if (!instruments.containsKey(Ym2612Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym2612Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYm3438(int vol) {
        if (!instruments.containsKey(Ym3438Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym3438Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeSn76489(int vol) {
        if (!instruments.containsKey(Sn76489Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Sn76489Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeScdPcm(int vol) {
        if (!instruments.containsKey(ScdPcmInst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof ScdPcmInst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumePwm(int vol) {
        if (!instruments.containsKey(PwmInst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof PwmInst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeOkiM6258(int vol) {
        if (!instruments.containsKey(OkiM6258Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof OkiM6258Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeMpcmX68k(int vol) {
        if (!instruments.containsKey(X68kMPcmInst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof X68kMPcmInst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeOkiM6295(int vol) {
        if (!instruments.containsKey(OkiM6295Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof OkiM6295Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeC140(int vol) {
        if (!instruments.containsKey(C140Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof C140Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeC352(int vol) {
        if (!instruments.containsKey(C352Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof C352Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetRearMute(byte flag) {
        if (!instruments.containsKey(C352Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof C352Inst)) continue;
            for (int i = 0; i < instruments.get(C352Inst.class).length; i++)
                ((C352Inst) instruments.get(C352Inst.class)[i]).c352_set_options(flag);
        }
    }

    public void SetVolumeK051649(int vol) {
        if (!instruments.containsKey(K051649Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof K051649Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeK053260(int vol) {
        if (!instruments.containsKey(K053260Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof K053260Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeRf5c68(int vol) {
        if (!instruments.containsKey(Rf5c68Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Rf5c68Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYm3812(int vol) {
        if (!instruments.containsKey(Ym3812Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym3812Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeY8950(int vol) {
        if (!instruments.containsKey(Y8950Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Y8950Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYm3526(int vol) {
        if (!instruments.containsKey(Ym3526Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ym3526Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeK054539(int vol) {
        if (!instruments.containsKey(K054539Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof K054539Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeQSound(int vol) {
        if (!instruments.containsKey(QSoundInst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof QSoundInst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeQSoundCtr(int vol) {
        if (!instruments.containsKey(CtrQSoundInst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof CtrQSoundInst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setVolumeGb(int vol) {
        if (!instruments.containsKey(DmgInst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof DmgInst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setVolumeIremga20(int vol) {
        if (!instruments.containsKey(Ga20Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ga20Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setVolumeYmZ280b(int vol) {
        if (!instruments.containsKey(YmZ280bInst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof YmZ280bInst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setVolumeYmf271(int vol) {
        if (!instruments.containsKey(YmF271Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof YmF271Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYmF262(int vol) {
        if (!instruments.containsKey(YmF262Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof YmF262Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYmF278b(int vol) {
        if (!instruments.containsKey(YmF278bInst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof YmF278bInst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setVolumeMultiPCM(int vol) {
        if (!instruments.containsKey(MultiPCM.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof MultiPCM)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setVolumeSegaPCM(int vol) {
        if (!instruments.containsKey(SegaPcmInst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof SegaPcmInst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeSaa1099(int vol) {
        if (!instruments.containsKey(Saa1099Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Saa1099Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumePPZ8(int vol) {
        if (!instruments.containsKey(Ppz8Inst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof Ppz8Inst)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeNES(int vol) {
        if (!instruments.containsKey(IntFNesInst.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (c.instrument instanceof IntFNesInst) {
                c.volume = Math.max(Math.min(vol, 20), -192);
                int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
                c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
                ((IntFNesInst) c.instrument).setVolumeAPU(vol);
            }
        }
    }

    public void SetVolumeDMC(int vol) {
        if (!instruments.containsKey(IntFNesInst.DMC.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (c.instrument instanceof IntFNesInst.DMC) {
                c.volume = Math.max(Math.min(vol, 20), -192);
                int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
                c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
                ((IntFNesInst) c.instrument).setVolumeDMC(vol);
            }
        }
    }

    public void SetVolumeFDS(int vol) {
        if (!instruments.containsKey(IntFNesInst.FDS.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (c.instrument instanceof IntFNesInst.FDS) {
                c.volume = Math.max(Math.min(vol, 20), -192);
                int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
                c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
                ((IntFNesInst) c.instrument).setVolumeFDS(vol);
            }
        }
    }

    public void SetVolumeMMC5(int vol) {
        if (!instruments.containsKey(IntFNesInst.MMC5.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof IntFNesInst.MMC5)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeN160(int vol) {
        if (!instruments.containsKey(IntFNesInst.N160.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof IntFNesInst.N160)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeVRC6(int vol) {
        if (!instruments.containsKey(IntFNesInst.VRC6.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof IntFNesInst.VRC6)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeVRC7(int vol) {
        if (!instruments.containsKey(IntFNesInst.VRC7.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof IntFNesInst.VRC7)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeFME7(int vol) {
        if (!instruments.containsKey(IntFNesInst.FME7.class)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (!(c.instrument instanceof IntFNesInst.FME7)) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

//#endregion

//#region ReadRegister

    public int[] ReadSn76489Register() {
        synchronized (lockobj) {
            if (!instruments.containsKey(Sn76489Inst.class)) return null;
            return ((Sn76489Inst) (instruments.get(Sn76489Inst.class)[0])).chips[0].registers;
        }
    }

    public int[] ReadSn76489Register(int chipIndex) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Sn76489Inst.class)) return null;
            return ((Sn76489Inst) (instruments.get(Sn76489Inst.class)[chipIndex])).chips[0].registers;
        }
    }

    public int[][] ReadYm2612Register(byte chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2612Inst.class)) return null;
            return ((Ym2612Inst) (instruments.get(Ym2612Inst.class)[0])).chips[chipId].getRegisters();
        }
    }

    public int[][] ReadYm2612Register(int chipIndex, byte chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2612Inst.class)) return null;
            return ((Ym2612Inst) (instruments.get(Ym2612Inst.class)[chipIndex])).chips[chipId].getRegisters();
        }
    }

    public PcmChip ReadRf5c164Register(int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(ScdPcmInst.class)) return null;
            if (((ScdPcmInst) (instruments.get(ScdPcmInst.class)[0])).PCM_Chip == null || ((ScdPcmInst) (instruments.get(ScdPcmInst.class)[0])).PCM_Chip.length < 1)
                return null;
            return ((ScdPcmInst) (instruments.get(ScdPcmInst.class)[0])).PCM_Chip[chipId];
        }
    }

    public PcmChip ReadRf5c164Register(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(ScdPcmInst.class)) return null;
            if (((ScdPcmInst) (instruments.get(ScdPcmInst.class)[chipIndex])).PCM_Chip == null || ((ScdPcmInst) (instruments.get(ScdPcmInst.class)[chipIndex])).PCM_Chip.length < 1)
                return null;
            return ((ScdPcmInst) (instruments.get(ScdPcmInst.class)[chipIndex])).PCM_Chip[chipId];
        }
    }

    public Rf5c68 ReadRf5c68Register(int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Rf5c68Inst.class)) return null;
            if (((Rf5c68Inst) (instruments.get(Rf5c68Inst.class)[0])).rf5C68Data == null || ((Rf5c68Inst) (instruments.get(Rf5c68Inst.class)[0])).rf5C68Data.length < 1)
                return null;
            return ((Rf5c68Inst) (instruments.get(Rf5c68Inst.class)[0])).rf5C68Data[chipId];
        }
    }

    public Rf5c68 ReadRf5c68Register(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Rf5c68Inst.class)) return null;
            if (((Rf5c68Inst) (instruments.get(Rf5c68Inst.class)[chipIndex])).rf5C68Data == null || ((Rf5c68Inst) (instruments.get(Rf5c68Inst.class)[chipIndex])).rf5C68Data.length < 1)
                return null;
            return ((Rf5c68Inst) (instruments.get(Rf5c68Inst.class)[chipIndex])).rf5C68Data[chipId];
        }
    }

    public C140 ReadC140Register(int cur) {
        synchronized (lockobj) {
            if (!instruments.containsKey(C140Inst.class)) return null;
            return ((C140Inst) instruments.get(C140Inst.class)[0]).c140Data[cur];
        }
    }

    public C140 ReadC140Register(int chipIndex, int cur) {
        synchronized (lockobj) {
            if (!instruments.containsKey(C140Inst.class)) return null;
            return ((C140Inst) instruments.get(C140Inst.class)[chipIndex]).c140Data[cur];
        }
    }

    public int[] ReadC352Flag(int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(C352Inst.class)) return null;
            return ((C352Inst) instruments.get(C352Inst.class)[0]).getFlags((byte) chipId);
        }
    }

    public int[] ReadC352Flag(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(C352Inst.class)) return null;
            return ((C352Inst) instruments.get(C352Inst.class)[chipIndex]).getFlags((byte) chipId);
        }
    }

    public MultiPCM ReadMultiPCMRegister(int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(MultiPCM.class)) return null;
            return ((MultiPcmInst) (instruments.get(MultiPCM.class)[0])).multipcm_r(chipId);
        }
    }

    public MultiPCM ReadMultiPCMRegister(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(MultiPCM.class)) return null;
            return ((MultiPcmInst) (instruments.get(MultiPCM.class)[chipIndex])).multipcm_r(chipId);
        }
    }

    public YmF271 ReadYmf271Register(int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmF271Inst.class)) return null;
            if (instruments.get(YmF271Inst.class)[0] == null) return null;
            return ((YmF271Inst) (instruments.get(YmF271Inst.class)[0])).ymf271Chips[chipId];
        }
    }

    public YmF271 ReadYmf271Register(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(YmF271Inst.class)) return null;
            if (instruments.get(YmF271Inst.class)[chipIndex] == null) return null;
            return ((YmF271Inst) (instruments.get(YmF271Inst.class)[chipIndex])).ymf271Chips[chipId];
        }
    }

//#endregion

//#region ReadStatus

    public OkiM6258 ReadOkiM6258Status(int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(OkiM6258Inst.class)) return null;
            return ((OkiM6258Inst) instruments.get(OkiM6258Inst.class)[0]).okiM6258Data[chipId];
        }
    }

    public OkiM6258 ReadOkiM6258Status(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(OkiM6258Inst.class)) return null;
            return ((OkiM6258Inst) instruments.get(OkiM6258Inst.class)[chipIndex]).okiM6258Data[chipId];
        }
    }

    public OkiM6295 ReadOkiM6295Status(int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(OkiM6295Inst.class)) return null;
            return ((OkiM6295Inst) instruments.get(OkiM6295Inst.class)[0]).chips[chipId];
        }
    }

    public OkiM6295 ReadOkiM6295Status(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(OkiM6295Inst.class)) return null;
            return ((OkiM6295Inst) instruments.get(OkiM6295Inst.class)[chipIndex]).chips[chipId];
        }
    }

    public SegaPcm ReadSegaPCMStatus(int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(SegaPcmInst.class)) return null;
            return ((SegaPcmInst) instruments.get(SegaPcmInst.class)[0]).SPCMData[chipId];
        }
    }

    public SegaPcm ReadSegaPCMStatus(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(SegaPcmInst.class)) return null;
            return ((SegaPcmInst) instruments.get(SegaPcmInst.class)[chipIndex]).SPCMData[chipId];
        }
    }


    public OotakeHuC6280 ReadOotakePsgStatus(int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(HuC6280Inst.class)) return null;
            return ((HuC6280Inst) instruments.get(HuC6280Inst.class)[0]).GetState((byte) chipId);
        }
    }

    public OotakeHuC6280 ReadOotakePsgStatus(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(HuC6280Inst.class)) return null;
            return ((HuC6280Inst) instruments.get(HuC6280Inst.class)[chipIndex]).GetState((byte) chipId);
        }
    }

    public K051649 ReadK051649Status(int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(K051649Inst.class)) return null;
            return ((K051649Inst) instruments.get(K051649Inst.class)[0]).GetK051649_State((byte) chipId);
        }
    }

    public K051649 ReadK051649Status(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(K051649Inst.class)) return null;
            return ((K051649Inst) instruments.get(K051649Inst.class)[chipIndex]).GetK051649_State((byte) chipId);
        }
    }

    public int[][] ReadRf5c164Volume(int chipId) {
        synchronized (lockobj) {
            return rf5c164Vol[chipId];
        }
    }

    public PPZ8Status.Channel[] readPPZ8Status(int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ppz8Inst.class)) return null;
            return ((Ppz8Inst) instruments.get(Ppz8Inst.class)[0]).getPPZ8State((byte) chipId);
        }
    }

    public PPZ8Status.Channel[] readPPZ8Status(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ppz8Inst.class)) return null;
            return ((Ppz8Inst) instruments.get(Ppz8Inst.class)[chipIndex]).getPPZ8State((byte) chipId);
        }
    }

//#endregion

//#region KeyOn

    public int[] ReadYm2612KeyOn(byte chipId) {
        synchronized (lockobj) {
            return ((Ym2612Inst) (instruments.get(Ym2612Inst.class)[0])).chips[chipId].keyStatuses();
        }
    }

    public int[] ReadYm2612KeyOn(int chipIndex, byte chipId) {
        synchronized (lockobj) {
            return ((Ym2612Inst) (instruments.get(Ym2612Inst.class)[chipIndex])).chips[chipId].keyStatuses();
        }
    }

    public int[] ReadYm2151KeyOn(int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2151Inst.class)) return null;
            for (int i = 0; i < 8; i++) {
                //ym2151Key[chipId][i] = ((Ym2151Inst)(iYm2151)).Ym2151_Chip[chipId].CHANNEL[i].KeyOn;
            }
            return ym2151Key[chipId];
        }
    }

    public int[] ReadYm2203KeyOn(int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2203Inst.class)) return null;
            for (int i = 0; i < 6; i++) {
                //ym2203Key[chipId][i] = ((Ym2203Inst)(iYm2203)).Ym2203_Chip[chipId].CHANNEL[i].KeyOn;
            }
            return ym2203Key[chipId];
        }
    }

    public int[] ReadYm2608KeyOn(int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2608Inst.class)) return null;
            for (int i = 0; i < 11; i++) {
                //ym2608Key[chipId][i] = ((Ym2608Inst)(iYm2608)).Ym2608_Chip[chipId].CHANNEL[i].KeyOn;
            }
            return ym2608Key[chipId];
        }
    }

    public int[] ReadYm2609KeyOn(int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2609Inst.class)) return null;
            for (int i = 0; i < 11; i++) {
                //ym2608Key[chipId][i] = ((Ym2608Inst)(iYm2608)).Ym2608_Chip[chipId].CHANNEL[i].KeyOn;
            }
            return ym2609Key[chipId];
        }
    }

    public int[] ReadYm2610KeyOn(int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Ym2610Inst.class)) return null;
            for (int i = 0; i < 11; i++) {
                //ym2610Key[chipId][i] = ((Ym2610Inst)(iYm2610)).Ym2610_Chip[chipId].CHANNEL[i].KeyOn;
            }
            return ym2610Key[chipId];
        }
    }

//#endregion

//#region SetMask

    public void setSn76489Mask(int chipId, int ch) {
        synchronized (lockobj) {
            sn76489Mask.get(0)[chipId] &= ~ch;
            if (!instruments.containsKey(Sn76489Inst.class)) return;
            ((Sn76489Inst) (instruments.get(Sn76489Inst.class)[0])).setMute((byte) chipId, sn76489Mask.get(0)[chipId]);
        }
    }

    public void setSn76489Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            sn76489Mask.get(chipIndex)[chipId] &= ~ch;
            if (!instruments.containsKey(Sn76489Inst.class)) return;
            ((Sn76489Inst) (instruments.get(Sn76489Inst.class)[chipIndex])).setMute((byte) chipId, sn76489Mask.get(chipIndex)[chipId]);
        }
    }

    public void setYm2612Mask(int chipId, int ch) {
        synchronized (lockobj) {
            ym2612Mask.get(0)[chipId] |= 1 << ch;
            if (instruments.containsKey(Ym2612Inst.class)) {
                ((Ym2612Inst) (instruments.get(Ym2612Inst.class)[0])).setMute((byte) chipId, ym2612Mask.get(0)[chipId]);
            }
            if (instruments.containsKey(MameYm2612Inst.class)) {
                ((MameYm2612Inst) (instruments.get(MameYm2612Inst.class)[0])).SetMute((byte) chipId, ym2612Mask.get(0)[chipId]);
            }
            if (instruments.containsKey(Ym3438Inst.class)) {
                int mask = ym2612Mask.get(0)[chipId];
                if ((mask & 0b0010_0000) == 0) mask &= 0b1011_1111;
                else mask |= 0b0100_0000;
                ((Ym3438Inst) (instruments.get(Ym3438Inst.class)[0])).setMute((byte) chipId, mask);
            }
        }
    }

    public void setYm2612Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ym2612Mask.get(chipIndex)[chipId] |= 1 << ch;
            if (instruments.containsKey(Ym2612Inst.class)) {
                ((Ym2612Inst) (instruments.get(Ym2612Inst.class)[chipIndex])).setMute((byte) chipId, ym2612Mask.get(chipIndex)[chipId]);
            }
            if (instruments.containsKey(MameYm2612Inst.class)) {
                ((MameYm2612Inst) (instruments.get(MameYm2612Inst.class)[chipIndex])).SetMute((byte) chipId, ym2612Mask.get(chipIndex)[chipId]);
            }
            if (instruments.containsKey(Ym3438Inst.class)) {
                int mask = ym2612Mask.get(chipIndex)[chipId];
                if ((mask & 0b0010_0000) == 0) mask &= 0b1011_1111;
                else mask |= 0b0100_0000;
                ((Ym3438Inst) (instruments.get(Ym3438Inst.class)[chipIndex])).setMute((byte) chipId, mask);
            }
        }
    }

    public void setYm2203Mask(int chipId, int ch) {
        synchronized (lockobj) {
            ym2203Mask.get(0)[chipId] |= ch;
            if (!instruments.containsKey(Ym2203Inst.class)) return;
            ((Ym2203Inst) (instruments.get(Ym2203Inst.class)[0])).setMute((byte) chipId, ym2203Mask.get(0)[chipId]);
        }
    }

    public void setYm2203Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ym2203Mask.get(chipIndex)[chipId] |= ch;
            if (!instruments.containsKey(Ym2203Inst.class)) return;
            ((Ym2203Inst) (instruments.get(Ym2203Inst.class)[chipIndex])).setMute((byte) chipId, ym2203Mask.get(chipIndex)[chipId]);
        }
    }

    public void setRf5c164Mask(int chipId, int ch) {
        synchronized (lockobj) {
            if (!instruments.containsKey(ScdPcmInst.class)) return;
            ((ScdPcmInst) (instruments.get(ScdPcmInst.class)[0])).PCM_Chip[chipId].setMuteCh(ch, 1);
        }
    }

    public void setRf5c164Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            if (!instruments.containsKey(ScdPcmInst.class)) return;
            ((ScdPcmInst) (instruments.get(ScdPcmInst.class)[chipIndex])).PCM_Chip[chipId].setMuteCh(ch, 1);
        }
    }

    public void setRf5c68Mask(int chipId, int ch) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Rf5c68Inst.class)) return;
            ((Rf5c68Inst) (instruments.get(Rf5c68Inst.class)[0])).rf5C68Data[chipId].setMute(ch, 1);
        }
    }

    public void setRf5c68Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Rf5c68Inst.class)) return;
            ((Rf5c68Inst) (instruments.get(Rf5c68Inst.class)[chipIndex])).rf5C68Data[chipId].setMute(ch, 1);
        }
    }

    public void setC140Mask(int chipId, int ch) {
        synchronized (lockobj) {
            c140Mask.get(0)[chipId] |= ch;
            if (!instruments.containsKey(C140Inst.class)) return;
            ((C140Inst) (instruments.get(C140Inst.class)[0])).c140_set_mute_mask((byte) chipId, c140Mask.get(0)[chipId]);
        }
    }

    public void setC140Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            c140Mask.get(chipIndex)[chipId] |= ch;
            if (!instruments.containsKey(C140Inst.class)) return;
            ((C140Inst) (instruments.get(C140Inst.class)[chipIndex])).c140_set_mute_mask((byte) chipId, c140Mask.get(chipIndex)[chipId]);
        }
    }

    public void setSegaPcmMask(int chipId, int ch) {
        synchronized (lockobj) {
            segapcmMask.get(0)[chipId] |= ch;
            if (!instruments.containsKey(SegaPcmInst.class)) return;
            ((SegaPcmInst) (instruments.get(SegaPcmInst.class)[0])).segapcm_set_mute_mask((byte) chipId, segapcmMask.get(0)[chipId]);
        }
    }

    public void setSegaPcmMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            segapcmMask.get(chipIndex)[chipId] |= ch;
            if (!instruments.containsKey(SegaPcmInst.class)) return;
            ((SegaPcmInst) (instruments.get(SegaPcmInst.class)[chipIndex])).segapcm_set_mute_mask((byte) chipId, segapcmMask.get(chipIndex)[chipId]);
        }
    }

    public void setQSoundMask(int chipId, int ch) {
        synchronized (lockobj) {
            ch = (1 << ch);
            qsoundMask.get(0)[chipId] |= ch;
            if (instruments.containsKey(QSoundInst.class)) {
                ((QSoundInst) (instruments.get(QSoundInst.class)[0])).qsound_set_mute_mask((byte) chipId, qsoundMask.get(0)[chipId]);
            }
        }
    }

    public void setQSoundMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ch = (1 << ch);
            qsoundMask.get(chipIndex)[chipId] |= ch;
            if (!instruments.containsKey(QSoundInst.class)) {
                ((QSoundInst) (instruments.get(QSoundInst.class)[chipIndex])).qsound_set_mute_mask((byte) chipId, qsoundMask.get(chipIndex)[chipId]);
            }
        }
    }

    public void setQSoundCtrMask(int chipId, int ch) {
        synchronized (lockobj) {
            ch = (1 << ch);
            qsoundCtrMask.get(0)[chipId] |= ch;
            if (instruments.containsKey(CtrQSoundInst.class)) {
                ((CtrQSoundInst) (instruments.get(CtrQSoundInst.class)[0])).qsound_set_mute_mask((byte) chipId, qsoundCtrMask.get(0)[chipId]);
            }
        }
    }

    public void setQSoundCtrMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ch = (1 << ch);
            qsoundCtrMask.get(chipIndex)[chipId] |= ch;
            if (!instruments.containsKey(CtrQSoundInst.class)) {
                ((CtrQSoundInst) (instruments.get(CtrQSoundInst.class)[chipIndex])).qsound_set_mute_mask((byte) chipId, qsoundCtrMask.get(chipIndex)[chipId]);
            }
        }
    }

    public void setOkiM6295Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            okim6295Mask.get(chipIndex)[chipId] |= ch;
            if (!instruments.containsKey(OkiM6295Inst.class)) return;
            ((OkiM6295Inst) (instruments.get(OkiM6295Inst.class)[chipIndex])).okim6295_set_mute_mask((byte) chipId, okim6295Mask.get(chipIndex)[chipId]);
        }
    }

    public OkiM6295.ChannelInfo getOkiM6295Info(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!instruments.containsKey(OkiM6295Inst.class)) return null;
            return ((OkiM6295Inst) (instruments.get(OkiM6295Inst.class)[chipIndex])).readChInfo((byte) chipId);
        }
    }

    public void setHuUC6280Mask(int chipId, int ch) {
        synchronized (lockobj) {
            huc6280Mask.get(0)[chipId] |= ch;
            if (!instruments.containsKey(HuC6280Inst.class)) return;
            ((HuC6280Inst) (instruments.get(HuC6280Inst.class)[0])).setMute((byte) chipId, huc6280Mask.get(0)[chipId]);
        }
    }

    public void setHuC6280Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            huc6280Mask.get(chipIndex)[chipId] |= ch;
            if (!instruments.containsKey(HuC6280Inst.class)) return;
            ((HuC6280Inst) (instruments.get(HuC6280Inst.class)[chipIndex])).setMute((byte) chipId, huc6280Mask.get(chipIndex)[chipId]);
        }
    }

    public void setNESMask(int chipId, int ch) {
        synchronized (lockobj) {
            nesMask.get(0)[chipId] |= 0x1 << ch;
            if (!instruments.containsKey(IntFNesInst.class)) return;
            ((IntFNesInst) (instruments.get(IntFNesInst.class)[0])).nes_set_mute_mask((byte) chipId, nesMask.get(0)[chipId]);
        }
    }

    public void setNESMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            nesMask.get(chipIndex)[chipId] |= 0x1 << ch;
            if (!instruments.containsKey(IntFNesInst.class)) return;
            ((IntFNesInst) (instruments.get(IntFNesInst.class)[chipIndex])).nes_set_mute_mask((byte) chipId, nesMask.get(chipIndex)[chipId]);
        }
    }

    public void setFDSMask(int chipId) {
        synchronized (lockobj) {
            nesMask.get(0)[chipId] |= 0x20;
            if (!instruments.containsKey(IntFNesInst.class)) return;
            ((IntFNesInst) (instruments.get(IntFNesInst.class)[0])).nes_set_mute_mask((byte) chipId, nesMask.get(0)[chipId]);
        }
    }

    public void setFDSMask(int chipIndex, int chipId) {
        synchronized (lockobj) {
            nesMask.get(chipIndex)[chipId] |= 0x20;
            if (!instruments.containsKey(IntFNesInst.class)) return;
            ((IntFNesInst) (instruments.get(IntFNesInst.class)[chipIndex])).nes_set_mute_mask((byte) chipId, nesMask.get(chipIndex)[chipId]);
        }
    }

//#endregion

//#region ResetMask

    public void resetSn76489Mask(int chipId, int ch) {
        synchronized (lockobj) {
            sn76489Mask.get(0)[chipId] |= ch;
            if (!instruments.containsKey(Sn76489Inst.class)) return;
            ((Sn76489Inst) (instruments.get(Sn76489Inst.class)[0])).setMute((byte) chipId, sn76489Mask.get(0)[chipId]);
        }
    }

    public void resetSn76489Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            sn76489Mask.get(chipIndex)[chipId] |= ch;
            if (!instruments.containsKey(Sn76489Inst.class)) return;
            ((Sn76489Inst) (instruments.get(Sn76489Inst.class)[chipIndex])).setMute((byte) chipId, sn76489Mask.get(chipIndex)[chipId]);
        }
    }


    public void resetYm2612Mask(int chipId, int ch) {
        synchronized (lockobj) {
            ym2612Mask.get(0)[chipId] &= ~(1 << ch);
            if (instruments.containsKey(Ym2612Inst.class)) {
                ((Ym2612Inst) (instruments.get(Ym2612Inst.class)[0])).setMute((byte) chipId, ym2612Mask.get(0)[chipId]);
            }
            if (instruments.containsKey(MameYm2612Inst.class)) {
                ((MameYm2612Inst) (instruments.get(MameYm2612Inst.class)[0])).SetMute((byte) chipId, ym2612Mask.get(0)[chipId]);
            }
            if (instruments.containsKey(Ym3438Inst.class)) {
                int mask = ym2612Mask.get(0)[chipId];
                if ((mask & 0b0010_0000) == 0) mask &= 0b1011_1111;
                else mask |= 0b0100_0000;
                ((Ym3438Inst) (instruments.get(Ym3438Inst.class)[0])).setMute((byte) chipId, mask);
            }
        }
    }

    public void resetYm2612Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ym2612Mask.get(chipIndex)[chipId] &= ~(1 << ch);
            if (instruments.containsKey(Ym2612Inst.class)) {
                ((Ym2612Inst) (instruments.get(Ym2612Inst.class)[chipIndex])).setMute((byte) chipId, ym2612Mask.get(chipIndex)[chipId]);
            }
            if (instruments.containsKey(MameYm2612Inst.class)) {
                ((MameYm2612Inst) (instruments.get(MameYm2612Inst.class)[chipIndex])).SetMute((byte) chipId, ym2612Mask.get(chipIndex)[chipId]);
            }
            if (instruments.containsKey(Ym3438Inst.class)) {
                int mask = ym2612Mask.get(chipIndex)[chipId];
                if ((mask & 0b0010_0000) == 0) mask &= 0b1011_1111;
                else mask |= 0b0100_0000;
                ((Ym3438Inst) (instruments.get(Ym3438Inst.class)[chipIndex])).setMute((byte) chipId, mask);
            }
        }
    }

    public void resetYm2203Mask(int chipId, int ch) {
        synchronized (lockobj) {
            ym2203Mask.get(0)[chipId] &= ~ch;
            if (!instruments.containsKey(Ym2203Inst.class)) return;
            ((Ym2203Inst) (instruments.get(Ym2203Inst.class)[0])).setMute((byte) chipId, ym2203Mask.get(0)[chipId]);
        }
    }

    public void resetYm2203Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ym2203Mask.get(chipIndex)[chipId] &= ~ch;
            if (!instruments.containsKey(Ym2203Inst.class)) return;
            ((Ym2203Inst) (instruments.get(Ym2203Inst.class)[chipIndex])).setMute((byte) chipId, ym2203Mask.get(chipIndex)[chipId]);
        }
    }

    public void resetRf5c164Mask(int chipId, int ch) {
        synchronized (lockobj) {
            if (!instruments.containsKey(ScdPcmInst.class)) return;
            ((ScdPcmInst) (instruments.get(ScdPcmInst.class)[0])).PCM_Chip[chipId].setMuteCh(ch, 0);
        }
    }

    public void resetRf5c164Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            if (!instruments.containsKey(ScdPcmInst.class)) return;
            ((ScdPcmInst) (instruments.get(ScdPcmInst.class)[chipIndex])).PCM_Chip[chipId].setMuteCh(ch, 0);
        }
    }

    public void resetRf5c68Mask(int chipId, int ch) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Rf5c68Inst.class)) return;
            ((Rf5c68Inst) (instruments.get(Rf5c68Inst.class)[0])).rf5C68Data[chipId].setMute(ch, 0);
        }
    }

    public void resetRf5c68Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            if (!instruments.containsKey(Rf5c68Inst.class)) return;
            ((Rf5c68Inst) (instruments.get(Rf5c68Inst.class)[chipIndex])).rf5C68Data[chipId].setMute(ch, 0);
        }
    }

    public void resetC140Mask(int chipId, int ch) {
        synchronized (lockobj) {
            c140Mask.get(0)[chipId] &= ~(int) ch;
            if (!instruments.containsKey(C140Inst.class)) return;
            ((C140Inst) (instruments.get(C140Inst.class)[0])).c140_set_mute_mask((byte) chipId, c140Mask.get(0)[chipId]);
        }
    }

    public void resetC140Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            c140Mask.get(chipIndex)[chipId] &= ~(int) ch;
            if (!instruments.containsKey(C140Inst.class)) return;
            ((C140Inst) (instruments.get(C140Inst.class)[chipIndex])).c140_set_mute_mask((byte) chipId, c140Mask.get(chipIndex)[chipId]);
        }
    }

    public void resetSegaPcmMask(int chipId, int ch) {
        synchronized (lockobj) {
            segapcmMask.get(0)[chipId] &= ~(int) ch;
            if (!instruments.containsKey(SegaPcmInst.class)) return;
            ((SegaPcmInst) (instruments.get(SegaPcmInst.class)[0])).segapcm_set_mute_mask((byte) chipId, segapcmMask.get(0)[chipId]);
        }
    }

    public void resetSegaPcmMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            segapcmMask.get(chipIndex)[chipId] &= ~(int) ch;
            if (!instruments.containsKey(SegaPcmInst.class)) return;
            ((SegaPcmInst) (instruments.get(SegaPcmInst.class)[chipIndex])).segapcm_set_mute_mask((byte) chipId, segapcmMask.get(chipIndex)[chipId]);
        }
    }

    public void resetQSoundMask(int chipId, int ch) {
        synchronized (lockobj) {
            ch = (1 << ch);
            qsoundMask.get(0)[chipId] &= ~(int) ch;
            if (instruments.containsKey(QSoundInst.class)) {
                ((QSoundInst) (instruments.get(QSoundInst.class)[0])).qsound_set_mute_mask((byte) chipId, qsoundMask.get(0)[chipId]);
            }
        }
    }

    public void resetQSoundMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ch = (1 << ch);
            qsoundMask.get(chipIndex)[chipId] &= ~(int) ch;
            if (!instruments.containsKey(QSoundInst.class)) {
                ((QSoundInst) (instruments.get(QSoundInst.class)[chipIndex])).qsound_set_mute_mask((byte) chipId, qsoundMask.get(chipIndex)[chipId]);
            }
        }
    }

    public void resetQSoundCtrMask(int chipId, int ch) {
        synchronized (lockobj) {
            ch = (1 << ch);
            qsoundCtrMask.get(0)[chipId] &= ~(int) ch;
            if (instruments.containsKey(CtrQSoundInst.class)) {
                ((CtrQSoundInst) (instruments.get(CtrQSoundInst.class)[0])).qsound_set_mute_mask((byte) chipId, qsoundCtrMask.get(0)[chipId]);
            }
        }
    }

    public void resetQSoundCtrMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ch = (1 << ch);
            qsoundCtrMask.get(chipIndex)[chipId] &= ~(int) ch;
            if (!instruments.containsKey(CtrQSoundInst.class)) {
                ((CtrQSoundInst) (instruments.get(CtrQSoundInst.class)[chipIndex])).qsound_set_mute_mask((byte) chipId, qsoundCtrMask.get(chipIndex)[chipId]);
            }
        }
    }

    public void resetOkiM6295Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            okim6295Mask.get(chipIndex)[chipId] &= ~(int) ch;
            if (!instruments.containsKey(OkiM6295Inst.class)) return;
            ((OkiM6295Inst) (instruments.get(OkiM6295Inst.class)[chipIndex])).okim6295_set_mute_mask((byte) chipId, okim6295Mask.get(chipIndex)[chipId]);
        }
    }

    public void resetOotakePsgMask(int chipId, int ch) {
        synchronized (lockobj) {
            huc6280Mask.get(0)[chipId] &= ~ch;
            if (!instruments.containsKey(HuC6280Inst.class)) return;
            ((HuC6280Inst) (instruments.get(HuC6280Inst.class)[0])).setMute((byte) chipId, huc6280Mask.get(0)[chipId]);
        }
    }

    public void resetOotakePsgMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            huc6280Mask.get(chipIndex)[chipId] &= ~ch;
            if (!instruments.containsKey(HuC6280Inst.class)) return;
            ((HuC6280Inst) (instruments.get(HuC6280Inst.class)[chipIndex])).setMute((byte) chipId, huc6280Mask.get(chipIndex)[chipId]);
        }
    }

    public void resetNESMask(int chipId, int ch) {
        synchronized (lockobj) {
            nesMask.get(0)[chipId] &= ~(0x1 << ch);
            if (!instruments.containsKey(IntFNesInst.class)) return;
            ((IntFNesInst) (instruments.get(IntFNesInst.class)[0])).nes_set_mute_mask((byte) chipId, nesMask.get(0)[chipId]);
        }
    }

    public void resetNESMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            nesMask.get(chipIndex)[chipId] &= ~(0x1 << ch);
            if (!instruments.containsKey(IntFNesInst.class)) return;
            ((IntFNesInst) (instruments.get(IntFNesInst.class)[chipIndex])).nes_set_mute_mask((byte) chipId, nesMask.get(chipIndex)[chipId]);
        }
    }

    public void resetFDSMask(int chipId) {
        synchronized (lockobj) {
            nesMask.get(0)[chipId] &= ~(int) 0x20;
            if (!instruments.containsKey(IntFNesInst.class)) return;
            ((IntFNesInst) (instruments.get(IntFNesInst.class)[0])).nes_set_mute_mask((byte) chipId, nesMask.get(0)[chipId]);
        }
    }

    public void resetFDSMask(int chipIndex, int chipId) {
        synchronized (lockobj) {
            nesMask.get(chipIndex)[chipId] &= ~(int) 0x20;
            if (!instruments.containsKey(IntFNesInst.class)) return;
            ((IntFNesInst) (instruments.get(IntFNesInst.class)[chipIndex])).nes_set_mute_mask((byte) chipId, nesMask.get(chipIndex)[chipId]);
        }
    }

//#endregion

//#region VisVolume

    public Set<Instrument> getFirstInstruments() {
        return instruments.values().stream().map(is -> is[0]).collect(Collectors.toSet());
    }

/*
    public int[][][] getYm2151VisVolume() {
        if (!instruments.containsKey(Ym2151Inst.class)) return null;
        return (instruments.get(Ym2151Inst.class)[0]).getVisVolume();
    }

    public int[][][] getYm2151VisVolume(int chipIndex) {
        if (!instruments.containsKey(Ym2151Inst.class)) return null;
        return (instruments.get(Ym2151Inst.class)[chipIndex]).getVisVolume();
    }

    public int[][][] getYm2203VisVolume() {
        if (!instruments.containsKey(Ym2203Inst.class)) return null;
        return instruments.get(Ym2203Inst.class)[0].getVisVolume();
    }

    public int[][][] getYm2203VisVolume(int chipIndex) {
        if (!instruments.containsKey(Ym2203Inst.class)) return null;
        return instruments.get(Ym2203Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getYm2413VisVolume() {
        if (!instruments.containsKey(Ym2413Inst.class)) return null;
        return instruments.get(Ym2413Inst.class)[0].getVisVolume();
    }

    public int[][][] getYm2413VisVolume(int chipIndex) {
        if (!instruments.containsKey(Ym2413Inst.class)) return null;
        return instruments.get(Ym2413Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getYm2608VisVolume() {
        if (!instruments.containsKey(Ym2608Inst.class)) return null;
        return instruments.get(Ym2608Inst.class)[0].getVisVolume();
    }

    public int[][][] getYm2608VisVolume(int chipIndex) {
        if (!instruments.containsKey(Ym2608Inst.class)) return null;
        return instruments.get(Ym2608Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getYm2609VisVolume() {
        if (!instruments.containsKey(Ym2609Inst.class)) return null;
        return instruments.get(Ym2609Inst.class)[0].getVisVolume();
    }

    public int[][][] getYm2609VisVolume(int chipIndex) {
        if (!instruments.containsKey(Ym2609Inst.class)) return null;
        return instruments.get(Ym2609Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getYm2610VisVolume() {
        if (!instruments.containsKey(Ym2610Inst.class)) return null;
        return instruments.get(Ym2610Inst.class)[0].getVisVolume();
    }

    public int[][][] getYm2610VisVolume(int chipIndex) {
        if (!instruments.containsKey(Ym2610Inst.class)) return null;
        return instruments.get(Ym2610Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getYm2612VisVolume() {
        if (!instruments.containsKey(Ym2612Inst.class)) {
            if (!instruments.containsKey(MameYm2612Inst.class)) return null;
            return (instruments.get(MameYm2612Inst.class)[0]).getVisVolume();
        }
        return (instruments.get(Ym2612Inst.class)[0]).getVisVolume();
    }

    public int[][][] getYm2612VisVolume(int chipIndex) {
        if (!instruments.containsKey(Ym2612Inst.class)) {
            if (!instruments.containsKey(MameYm2612Inst.class)) return null;
            return (instruments.get(MameYm2612Inst.class)[chipIndex]).getVisVolume();
        }
        return (instruments.get(Ym2612Inst.class)[chipIndex]).getVisVolume();
    }

    public int[][][] getSn76489VisVolume() {
        if (instruments.containsKey(Sn76489Inst.class)) {
            return instruments.get(Sn76489Inst.class)[0].getVisVolume();
        } else if (instruments.containsKey(Sn76496Inst.class)) {
            return instruments.get(Sn76496Inst.class)[0].getVisVolume();
        }
        return null;
    }

    public int[][][] getSn76489VisVolume(int chipIndex) {
        if (instruments.containsKey(Sn76489Inst.class)) {
            return instruments.get(Sn76489Inst.class)[chipIndex].getVisVolume();
        } else if (instruments.containsKey(Sn76496Inst.class)) {
            return instruments.get(Sn76496Inst.class)[chipIndex].getVisVolume();
        }
        return null;
    }

    public int[][][] getOotakePsgVisVolume() {
        if (!instruments.containsKey(HuC6280Inst.class)) return null;
        return instruments.get(HuC6280Inst.class)[0].getVisVolume();
    }

    public int[][][] getOotakePsgVisVolume(int chipIndex) {
        if (!instruments.containsKey(HuC6280Inst.class)) return null;
        return instruments.get(HuC6280Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getScdPcmVisVolume() {
        if (!instruments.containsKey(ScdPcmInst.class)) return null;
        return instruments.get(ScdPcmInst.class)[0].getVisVolume();
    }

    public int[][][] getScdPcmVisVolume(int chipIndex) {
        if (!instruments.containsKey(ScdPcmInst.class)) return null;
        return instruments.get(ScdPcmInst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getPwmVisVolume() {
        if (!instruments.containsKey(PwmInst.class)) return null;
        return instruments.get(PwmInst.class)[0].getVisVolume();
    }

    public int[][][] getPwmVisVolume(int chipIndex) {
        if (!instruments.containsKey(PwmInst.class)) return null;
        return instruments.get(PwmInst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getOkiM6258VisVolume() {
        if (!instruments.containsKey(OkiM6258Inst.class)) return null;
        return instruments.get(OkiM6258Inst.class)[0].getVisVolume();
    }

    public int[][][] getOkiM6258VisVolume(int chipIndex) {
        if (!instruments.containsKey(OkiM6258Inst.class)) return null;
        return instruments.get(OkiM6258Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getOkiM6295VisVolume() {
        if (!instruments.containsKey(OkiM6295Inst.class)) return null;
        return instruments.get(OkiM6295Inst.class)[0].getVisVolume();
    }

    public int[][][] getOkiM6295VisVolume(int chipIndex) {
        if (!instruments.containsKey(OkiM6295Inst.class)) return null;
        return instruments.get(OkiM6295Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getC140VisVolume() {
        if (!instruments.containsKey(C140Inst.class)) return null;
        return instruments.get(C140Inst.class)[0].getVisVolume();
    }

    public int[][][] getC140VisVolume(int chipIndex) {
        if (!instruments.containsKey(C140Inst.class)) return null;
        return instruments.get(C140Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getSegaPCMVisVolume() {
        if (!instruments.containsKey(SegaPcmInst.class)) return null;
        return instruments.get(SegaPcmInst.class)[0].getVisVolume();
    }

    public int[][][] getSegaPCMVisVolume(int chipIndex) {
        if (!instruments.containsKey(SegaPcmInst.class)) return null;
        return instruments.get(SegaPcmInst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getC352VisVolume() {
        if (!instruments.containsKey(C352Inst.class)) return null;
        return instruments.get(C352Inst.class)[0].getVisVolume();
    }

    public int[][][] getC352VisVolume(int chipIndex) {
        if (!instruments.containsKey(C352Inst.class)) return null;
        return instruments.get(C352Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getK051649VisVolume() {
        if (!instruments.containsKey(K051649Inst.class)) return null;
        return instruments.get(K051649Inst.class)[0].getVisVolume();
    }

    public int[][][] getK051649VisVolume(int chipIndex) {
        if (!instruments.containsKey(K051649Inst.class)) return null;
        return instruments.get(K051649Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getK054539VisVolume() {
        if (!instruments.containsKey(K054539Inst.class)) return null;
        return instruments.get(K054539Inst.class)[0].getVisVolume();
    }

    public int[][][] getK054539VisVolume(int chipIndex) {
        if (!instruments.containsKey(K054539Inst.class)) return null;
        return instruments.get(K054539Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getNESVisVolume() {
        return null;
        //if (!instruments.containsKey(IntFNesInst.class)) return null;
        //return instruments.get(IntFNesInst.class) .getVisVolume();
    }

    public int[][][] getDMCVisVolume() {
        return null;
        //if (!instruments.containsKey(DMC.class)) return null;
        //return instruments.get(DMC.class) .getVisVolume();
    }

    public int[][][] getFDSVisVolume() {
        return null;
        //if (!instruments.containsKey(FDS.class)) return null;
        //return instruments.get(FDS.class) .getVisVolume();
    }

    public int[][][] getMMC5VisVolume() {
        if (!instruments.containsKey(IntFNesInst.MMC5.class)) return null;
        return instruments.get(IntFNesInst.MMC5.class)[0].getVisVolume();
    }

    public int[][][] getMMC5VisVolume(int chipIndex) {
        if (!instruments.containsKey(IntFNesInst.MMC5.class)) return null;
        return instruments.get(IntFNesInst.MMC5.class)[chipIndex].getVisVolume();
    }

    public int[][][] getN160VisVolume() {
        if (!instruments.containsKey(IntFNesInst.N160.class)) return null;
        return instruments.get(IntFNesInst.N160.class)[0].getVisVolume();
    }

    public int[][][] getN160VisVolume(int chipIndex) {
        if (!instruments.containsKey(IntFNesInst.N160.class)) return null;
        return instruments.get(IntFNesInst.N160.class)[chipIndex].getVisVolume();
    }

    public int[][][] getVRC6VisVolume() {
        if (!instruments.containsKey(Vrc6Inst.class)) return null;
        return instruments.get(Vrc6Inst.class)[0].getVisVolume();
    }

    public int[][][] getVRC6VisVolume(int chipIndex) {
        if (!instruments.containsKey(Vrc6Inst.class)) return null;
        return instruments.get(Vrc6Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getVRC7VisVolume() {
        if (!instruments.containsKey(IntFNesInst.VRC7.class)) return null;
        return instruments.get(IntFNesInst.VRC7.class)[0].getVisVolume();
    }

    public int[][][] getVRC7VisVolume(int chipIndex) {
        if (!instruments.containsKey(IntFNesInst.VRC7.class)) return null;
        return instruments.get(IntFNesInst.VRC7.class)[chipIndex].getVisVolume();
    }

    public int[][][] getFME7VisVolume() {
        if (!instruments.containsKey(IntFNesInst.FME7.class)) return null;
        return instruments.get(IntFNesInst.FME7.class)[0].getVisVolume();
    }

    public int[][][] getFME7VisVolume(int chipIndex) {
        if (!instruments.containsKey(IntFNesInst.FME7.class)) return null;
        return instruments.get(IntFNesInst.FME7.class)[chipIndex].getVisVolume();
    }

    public int[][][] getYm3526VisVolume() {
        if (!instruments.containsKey(Ym3526Inst.class)) return null;
        return instruments.get(Ym3526Inst.class)[0].getVisVolume();
    }

    public int[][][] getYm3526VisVolume(int chipIndex) {
        if (!instruments.containsKey(Ym3526Inst.class)) return null;
        return instruments.get(Ym3526Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getY8950VisVolume() {
        if (!instruments.containsKey(Y8950Inst.class)) return null;
        return instruments.get(Y8950Inst.class)[0].getVisVolume();
    }

    public int[][][] getY8950VisVolume(int chipIndex) {
        if (!instruments.containsKey(Y8950Inst.class)) return null;
        return instruments.get(Y8950Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getYm3812VisVolume() {
        if (!instruments.containsKey(Ym3812Inst.class)) return null;
        return instruments.get(Ym3812Inst.class)[0].getVisVolume();
    }

    public int[][][] getYm3812VisVolume(int chipIndex) {
        if (!instruments.containsKey(Ym3812Inst.class)) return null;
        return instruments.get(Ym3812Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getYmF262VisVolume() {
        if (!instruments.containsKey(YmF262Inst.class)) return null;
        return instruments.get(YmF262Inst.class)[0].getVisVolume();
    }

    public int[][][] getYmF262VisVolume(int chipIndex) {
        if (!instruments.containsKey(YmF262Inst.class)) return null;
        return instruments.get(YmF262Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getYmF278bVisVolume() {
        if (!instruments.containsKey(YmF278bInst.class)) return null;
        return instruments.get(YmF278bInst.class)[0].getVisVolume();
    }

    public int[][][] getYmF278bVisVolume(int chipIndex) {
        if (!instruments.containsKey(YmF278bInst.class)) return null;
        return instruments.get(YmF278bInst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getYmZ280bVisVolume() {
        if (!instruments.containsKey(YmZ280bInst.class)) return null;
        return instruments.get(YmZ280bInst.class)[0].getVisVolume();
    }

    public int[][][] getYmZ280bVisVolume(int chipIndex) {
        if (!instruments.containsKey(YmZ280bInst.class)) return null;
        return instruments.get(YmZ280bInst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getYmf271VisVolume() {
        if (!instruments.containsKey(YmF271Inst.class)) return null;
        return instruments.get(YmF271Inst.class)[0].getVisVolume();
    }

    public int[][][] getYmf271VisVolume(int chipIndex) {
        if (!instruments.containsKey(YmF271Inst.class)) return null;
        return instruments.get(YmF271Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getRf5c68VisVolume() {
        if (!instruments.containsKey(Rf5c68Inst.class)) return null;
        return instruments.get(Rf5c68Inst.class)[0].getVisVolume();
    }

    public int[][][] getRf5c68VisVolume(int chipIndex) {
        if (!instruments.containsKey(Rf5c68Inst.class)) return null;
        return instruments.get(Rf5c68Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getMultiPCMVisVolume() {
        if (!instruments.containsKey(MultiPCM.class)) return null;
        return instruments.get(MultiPCM.class)[0].getVisVolume();
    }

    public int[][][] getMultiPCMVisVolume(int chipIndex) {
        if (!instruments.containsKey(MultiPCM.class)) return null;
        return instruments.get(MultiPCM.class)[chipIndex].getVisVolume();
    }

    public int[][][] getK053260VisVolume() {
        if (!instruments.containsKey(K053260Inst.class)) return null;
        return instruments.get(K053260Inst.class)[0].getVisVolume();
    }

    public int[][][] getK053260VisVolume(int chipIndex) {
        if (!instruments.containsKey(K053260Inst.class)) return null;
        return instruments.get(K053260Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getQSoundVisVolume() {
        if (!instruments.containsKey(QSoundInst.class)) return null;
        return instruments.get(QSoundInst.class)[0].getVisVolume();
    }

    public int[][][] getQSoundVisVolume(int chipIndex) {
        if (!instruments.containsKey(QSoundInst.class)) return null;
        return instruments.get(QSoundInst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getQSoundCtrVisVolume() {
        if (!instruments.containsKey(CtrQSoundInst.class)) return null;
        return instruments.get(CtrQSoundInst.class)[0].getVisVolume();
    }

    public int[][][] getQSoundCtrVisVolume(int chipIndex) {
        if (!instruments.containsKey(CtrQSoundInst.class)) return null;
        return instruments.get(CtrQSoundInst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getIremga20VisVolume() {
        if (!instruments.containsKey(Ga20Inst.class)) return null;
        return instruments.get(Ga20Inst.class)[0].getVisVolume();
    }

    public int[][][] getIremga20VisVolume(int chipIndex) {
        if (!instruments.containsKey(Ga20Inst.class)) return null;
        return instruments.get(Ga20Inst.class)[chipIndex].getVisVolume();
    }

    public int[][][] getGbVisVolume() {
        if (!instruments.containsKey(DmgInst.class)) return null;
        return instruments.get(DmgInst.class)[0].getVisVolume();
    }

    public int[][][] getGbVisVolume(int chipIndex) {
        if (!instruments.containsKey(DmgInst.class)) return null;
        return instruments.get(DmgInst.class)[chipIndex].getVisVolume();
    }
*/

    /**
     * Left全体ボリュームの取得(視覚効果向け)
     */
    public int getTotalVolumeL() {
        synchronized (lockobj) {
            int v = 0;
            for (int i = 0; i < buffer[0].length; i++) {
                v = Math.max(v, Math.abs(buffer[0][i]));
            }
            return v;
        }
    }

    /**
     * Right全体ボリュームの取得(視覚効果向け)
     */
    public int getTotalVolumeR() {
        synchronized (lockobj) {
            int v = 0;
            for (int i = 0; i < buffer[1].length; i++) {
                v = Math.max(v, Math.abs(buffer[1][i]));
            }
            return v;
        }
    }

//#endregion

    public void setIncFlag() {
        synchronized (lockobj) {
            incFlag = true;
        }
    }

    public void resetIncFlag() {
        synchronized (lockobj) {
            incFlag = false;
        }
    }
}

