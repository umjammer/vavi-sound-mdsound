package mdsound;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import dotnet4j.util.compat.QuadConsumer;
import mdsound.instrument.NesInst;

import static java.lang.System.getLogger;


public class MDSound {

    private static final Logger logger = getLogger(MDSound.class.getName());

    private static final int DefaultSamplingRate = 44100;
    private static final int DefaultSamplingBuffer = 512;

    private int samplingRate = DefaultSamplingRate;
    private int samplingBuffer = DefaultSamplingBuffer;
    private int[][] streamBufs = null;
    public DacControl dacControl = null;

    private List<Chip> chips = null;

    public MDSound.Chip getChipInfo(Class<? extends Instrument> inst) {
        return chips.stream().filter(c -> c.instrument.getClass() == inst).findFirst().orElse(null);
    }

    private final Map<Class<? extends Instrument>, List<Instrument>> instruments = new HashMap<>();

    public <T extends Instrument> T inst(Class<T> clazz) {
        return inst(clazz, 0);
    }

    public <T extends Instrument> T inst(Class<T> clazz, int chipIndex) {
        if (instruments.containsKey(clazz)) {
            return clazz.cast(instruments.get(clazz).get(chipIndex));
        } else {
            throw new NoSuchElementException(clazz.getName());
        }
    }

    private int[][] buffer = null;
    private final int[][] buff = new int[][] {new int[1], new int[1]};

    private boolean incFlag = false;
    private final Object lockobj = new Object();
    private final int resampleMode = 0;

    private static final int FIXPNT_BITS = 11;
    private static final int FIXPNT_FACT = (1 << FIXPNT_BITS);
    private static final int FIXPNT_MASK = (FIXPNT_FACT - 1);

    public VisWaveBuffer visWaveBuffer = new VisWaveBuffer();

//#if DEBUG
    long sw = System.currentTimeMillis();
//#endif

    private static int getfriction(int x) {
        return x & FIXPNT_MASK;
    }

    private static int getnfriction(int x) {
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

    public static class Chip {

        public interface AdditionalUpdate extends QuadConsumer<Chip, Integer, int[][], Integer> {
        }
        public interface SetVolume extends BiConsumer<Integer, Double> {
        }

        public Instrument instrument = null;
        public AdditionalUpdate additionalUpdate = null;
        public Map<String, SetVolume> setVolumes = new HashMap<>();

        public static final String MAIN_TAG = "MAIN";

        {
            setVolumes.put(MAIN_TAG, this::setDefaultSetVolume);
        }

        public int id = 0;
        public int samplingRate = 0;
        public int clock = 0;
        public int volume = 0;
        public int visVolume = 0;

        public int resampler;
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

        public int getTVolumeBalance() {
            return tVolumeBalance;
        }

        public void setVolume(String tag, int vol, double volumeMul) {
            SetVolume setVolume = setVolumes.get(tag);
            if (setVolume != null)
                setVolumes.get(tag).accept(vol, volumeMul);
            else
                logger.log(Level.WARNING, "no such tag: " + tag);
        }

        public SetVolume mainWrappedSetVolume(SetVolume setVolume) {
            return (i, d) -> {
                setDefaultSetVolume(i, d);
                setVolume.accept(i, d);
            };
        }

        private void setDefaultSetVolume(int vol, double volumeMul) {
            this.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, this.volume / 40.0)) * this.tVolumeBalance) >> 8);
            this.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }

        // default, 0x80, 1
        // UPD7759, 0x11E, 1
        // SCSP, 0x20, 8
        // VSU, 0x100, 1
        // ES5503, 0x40, 8
        // ES5506, 0x20, 16
        private int getRegulationVolume(double[] mul) {
            var r = instrument.getRegulationVolume();
            mul[0] = r.getItem2();
            return r.getItem1();
        }

        // TODO naming
        public int volume1(double[] mul, int size) {
            if (this.instrument instanceof NesInst) this.volume = 0;
            int balance = this.getRegulationVolume(mul);
            //16384 = 0x4000 = short.MAXValue + 1
            return (int) ((((int) (16384.0 * Math.pow(10.0, 0 / 40.0)) * balance) >> 8) * mul[0]) / size;
        }

        // TODO naming
        public void volume2(double[] mul, double volumeMul) {
            if ((this.volumeBalance & 0x8000) != 0)
                this.tVolumeBalance = (this.getRegulationVolume(mul) * (this.volumeBalance & 0x7fff) + 0x80) >> 8;
            else
                this.tVolumeBalance = this.volumeBalance;
            int n = (((int) (16384.0 * Math.pow(10.0, this.volume / 40.0)) * this.tVolumeBalance) >> 8);
            this.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public MDSound() {
        this(DefaultSamplingRate, DefaultSamplingBuffer, null);
    }

    public MDSound(int samplingRate, int samplingBuffer, List<Chip> insts) {
        init(samplingRate, samplingBuffer, insts);
    }

    public void init(int samplingRate, int samplingBuffer, List<Chip> chips) {
        synchronized (lockobj) {
            this.samplingRate = samplingRate;
            this.samplingBuffer = samplingBuffer;
            this.chips = chips;

            buffer = new int[][] {new int[1], new int[1]};
            streamBufs = new int[][] {new int[0x100], new int[0x100]};

            incFlag = false;

            if (chips == null) {
logger.log(Level.WARNING, "no chips");
                return;
            }

            instruments.clear();

            // Calculate the actual multiple from the volume value
            int total = 0;
            double[] mul = new double[1];
            for (Chip chip : chips) {
                total += chip.volume1(mul, chips.size());
            }
            // Calculate the multiple from the total volume value to the maximum volume
            volumeMul = 16384.0 / total;
            // Calculate the actual multiple from the volume value
            for (Chip chip : chips) {
                chip.volume2(mul, volumeMul);
            }

            for (Chip chip : chips) {
                chip.samplingRate = chip.instrument.start(chip.id, chip.samplingRate, chip.clock, chip.option);
                chip.instrument.reset(chip.id);

                if (instruments.containsKey(chip.instrument.getClass())) {
                    instruments.get(chip.instrument.getClass()).add(chip.instrument);
                } else {
                    instruments.put(chip.instrument.getClass(), new ArrayList<>(List.of(chip.instrument)));
                }

                setupResampler(chip);
            }
instruments.forEach((k, v) -> logger.log(Level.DEBUG, "instrument: " + k.getSimpleName().replace("Inst", "") + ": chips: " + v.stream().map(Instrument::getName).collect(Collectors.joining(", ", "[", "]"))));

            dacControl = new DacControl(samplingRate, this);

            instruments.values().forEach(is -> is.forEach(Instrument::init));
        }
    }

    public String getDebugMsg() {
        return debugMsg;
    }

    private void setupResampler(Chip chip) {
        if (chip.samplingRate == 0) {
            chip.resampler = 0xff;
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

    public int update(short[] buf, int offset, int sampleCount, Runnable frame) {
        synchronized (lockobj) {

            int i;
            for (i = 0; i < sampleCount && offset + i < buf.length; i += 2) {

                if (frame != null) frame.run();

                if (dacControl != null) dacControl.update();

                int[] a = new int[1];
                int[] b = new int[1];

                buffer[0][0] = 0;
                buffer[1][0] = 0;
                resampleChipStream(chips, buffer, 1);
//if (buffer[0][0] != 0) logger.log(Level.DEBUG, "%d".formatted(buffer[0][0]));
                a[0] += buffer[0][0];
                b[0] += buffer[1][0];

                if (incFlag) {
                    a[0] += buf[offset + i + 0];
                    b[0] += buf[offset + i + 1];
                }

                clip(a, b);

                buf[offset + i + 0] = (short) a[0];
                buf[offset + i + 1] = (short) b[0];
logger.log(Level.TRACE, "[%d] %+04d, %+04d".formatted(i, a[0], b[0]));
                visWaveBuffer.enq((short) a[0], (short) b[0]);
            }

            return Math.min(i, sampleCount);
        }
    }

    private static void clip(int[] a, int[] b) {
        if (((a[0] + 32767) & 0xffff) > 32767 * 2) {
            if ((a[0] + 32767) >= (32767 * 2)) {
                a[0] = 32767;
            } else {
                a[0] = -32767;
            }
        }
        if (((b[0] + 32767) & 0xffff) > 32767 * 2) {
            if ((b[0] + 32767) >= (32767 * 2)) {
                b[0] = 32767;
            } else {
                b[0] = -32767;
            }
        }
    }

    public static int limit(int v, int max, int min) {
        return Math.min(max, Math.max(v, min));
    }

    private int[][] tempSample = new int[][] {new int[1], new int[1]};
    private int[][] streamPnt = new int[][] {new int[0x100], new int[0x100]};
    private int clearLength = 1;
    public static String debugMsg;
    private double volumeMul;

int CC = 0;
static int INTERVAL = 1024;
    private void resampleChipStream(List<Chip> insts, int[][] retSample, int length) {
        if (insts == null || insts.isEmpty()) {
logger.log(Level.WARNING, "no insts");
            return;
        }
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
        long inPosL;
        int tempSmpL;
        int tempSmpR;
        int tempS32L;
        int tempS32R;
        int smpCnt; // must be signed, else I'm getting calculation errors
        int CurSmpl;
        int chipSmpRate;

        // This Do-While-Loop gets and resamples the chips output of one or more chips.
        // It's a loop to support the AY8910 paired with the Ym2203Inst/Ym2608Inst/Ym2610Inst.
        for (Chip chip : insts) {
            Arrays.fill(streamBufs[0], 0);
            Arrays.fill(streamBufs[1], 0);
            curBufL = streamBufs[0x00];
            curBufR = streamBufs[0x01];

            inst = chip;
            int mul = inst.tVolume;

            //if (i != 0 && chips[i].LSmpl[0] != 0) logger.log(Level.DEBUG, "%d %d".formatted(chips[i].LSmpl[0], chips[0].LSmpl == chips[i].LSmpl));
//logger.log(Level.TRACE, "%s, resample: %d, mul: %d".formatted(inst.instrument.getName(), inst.resampler, mul));
//logger.log(Level.TRACE, "resampler: %d".formatted(inst.resampler));
            switch (inst.resampler) {
            case 0x00: // old, but very fast resampler
                inst.smpLast = inst.smpNext;
                inst.smpP += length;
                inst.smpNext = inst.smpP * inst.samplingRate / samplingRate;
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
if ((CC % INTERVAL) == 0) { logger.log(Level.DEBUG, "%s[%d] O: %+04d, %+04d".formatted(inst.instrument.getName(), ind, streamBufs[0][ind], streamBufs[1][ind]));}
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
                inPosL = (long) FIXPNT_FACT * inst.smpP * chipSmpRate / samplingRate;
                inPre = fp2i_floor(inPosL);
                inNow = fp2i_ceil(inPosL);

//logger.log(Level.TRACE, "inPosL=%d, inst.smpP=%d, inPre=%d, inNow=%d, inst.SmpNext=%d".formatted(inPosL, inst.smpP, inPre, inNow, inst.smpNext));

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
if ((CC % INTERVAL) == 0) { logger.log(Level.DEBUG, "%s[%d] U: %+04d, %+04d".formatted(inst.instrument.getName(), ind, streamPnt[0][0], streamPnt[1][0]));}
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
                    smpFrc = getfriction(inPos);

                    // linear interpolation
                    tempSmpL = (curBufL[inPre] * (FIXPNT_FACT - smpFrc)) +
                            (curBufL[inNow] * smpFrc);
                    tempSmpR = (curBufR[inPre] * (FIXPNT_FACT - smpFrc)) +
                            (curBufR[inNow] * smpFrc);
                    tempSample[0][outPos] = tempSmpL / smpCnt;
                    tempSample[1][outPos] = tempSmpR / smpCnt;
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
if ((CC % INTERVAL) == 0) { logger.log(Level.DEBUG, "%s[%d] C: %+04d, %+04d".formatted(inst.instrument.getName(), ind, streamBufs[0][ind], streamBufs[1][ind]));}
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
                inPosL = (long) FIXPNT_FACT * (inst.smpP + length) * chipSmpRate / samplingRate;
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

                    streamPnt[0][ind] = limit((buff[0][0] * mul) >> 15, 0x7fff, -0x8000);
                    streamPnt[1][ind] = limit((buff[1][0] * mul) >> 15, 0x7fff, -0x8000);
if ((CC % INTERVAL) == 0) { logger.log(Level.DEBUG, "%s[%d] D: %+04d, %+04d".formatted(inst.instrument.getName(), ind, streamPnt[0][0], streamPnt[1][0]));}
                }
                for (int ind = 0; ind < inst.smpNext - inst.smpLast; ind++) {
                    curBufL[0x01 + ind] = streamPnt[0x00][ind];
                    curBufR[0x01 + ind] = streamPnt[0x01][ind];
                }

                inPosL = (long) FIXPNT_FACT * inst.smpP * chipSmpRate / samplingRate;
                // I'm adding 1.0 to avoid negative indexes
                inBase = (int) (FIXPNT_FACT + (inPosL - inst.smpLast * FIXPNT_FACT));
                InPosNext = inBase;
                for (outPos = 0x00; outPos < length; outPos++) {
                    inPos = InPosNext;
                    InPosNext = inBase + (int) (((long) FIXPNT_FACT * (outPos + 1) * chipSmpRate) / samplingRate);

                    // first frictional Sample
                    smpFrc = getnfriction(inPos);
                    if (smpFrc != 0) {
                        inPre = fp2i_floor(inPos);
                        tempSmpL = curBufL[inPre] * smpFrc;
                        tempSmpR = curBufR[inPre] * smpFrc;
                    } else {
                        tempSmpL = tempSmpR = 0x00;
                    }
                    smpCnt = smpFrc;

                    // last frictional Sample
                    smpFrc = getfriction(InPosNext);
                    inPre = fp2i_floor(InPosNext);
                    if (smpFrc != 0) {
                        tempSmpL += curBufL[inPre] * smpFrc;
                        tempSmpR += curBufR[inPre] * smpFrc;
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

//#region unused?

    private static int getChipVolume(VGMX_CHP_EXTRA16 tempCX, int chipId, int chipNum, int chipCnt, int sn76496VGMHeaderClock, String strSystemNameE, boolean doubleSSGVol) {
        // chipId: ID of Chip
        //  Bit 7 - Is Paired Chip
        // chipNum: chips number (0 - first chips, 1 - second chips)
        // chipCnt: chips volume divider (number of used chips)
        int[] CHIP_VOLS = new int[] { // CHIP_COUNT
                0x80, 0x200 /* 0x155 */, 0x100, 0x100, 0x180, 0xB0, 0x100, 0x80, // 00-07
                0x80, 0x100, 0x100, 0x100, 0x100, 0x100, 0x100, 0x98,            // 08-0F
                0x80, 0xE0 /* 0xCD */, 0x100, 0xC0, 0x100, 0x40, 0x11E, 0x1C0,   // 10-17
                0x100 /* 110 */, 0xA0, 0x100, 0x100, 0x100, 0xB3, 0x100, 0x100,  // 18-1F
                0x20, 0x100, 0x100, 0x100, 0x40, 0x20, 0x100, 0x40,              // 20-27
                0x280
        };
        int volume;
        int curChp;
        //VGMX_CHP_EXTRA16 tempCX;
        VGMX_CHIP_DATA16 tempCD;

        volume = CHIP_VOLS[chipId & 0x7F];
        switch (chipId & 0xff) {
        case 0x00: // Sn76496
            // if T6W28, set volume Divider to 01
            if ((sn76496VGMHeaderClock & 0x8000_0000) != 0) {
                // The T6W28 consists of 2 "half" chips.
                chipNum = 0x01;
                chipCnt = 0x01;
            }
            break;
        case 0x18: // OkiM6295
            // CP System 1 patch
            if ((strSystemNameE != null && !strSystemNameE.isEmpty()) && strSystemNameE.indexOf("CP") == 0)
                volume = 110;
            break;
        case 0x86: // Ym2203's AY
            volume /= 2;
            break;
        case 0x87: // Ym2608's AY
            // The Ym2608 outputs twice as loud as the Ym2203 here.
            //volume *= 1;
            break;
        case 0x88: // Ym2610's AY
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
                    volume = (volume * (tempCD.data & 0x7fff) + 0x80) >> 8;
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
        public int type;
        public int flags;
        public int data;
    }

    public static class VGMX_CHP_EXTRA16 {
        public int chipCnt;
        public VGMX_CHIP_DATA16[] ccData;
    }

//#endregion

    public void write(Class<? extends Instrument> i, int chipId, int port, int adr, int data) {
        write(i, 0, chipId, port, adr, data);
    }

    public void write(Class<? extends Instrument> i, int chipIndex, int chipId, int port, int adr, int data) {
        synchronized (lockobj) {
            if (!instruments.containsKey(i)) {
//logger.log(Level.TRACE, "not contains: " + i);
                return;
            }

//logger.log(Level.TRACE, "mds: %02x".formatted(data)); // ok
            instruments.get(i).get(chipIndex).write(chipId, port, adr, data);
        }
    }

//#region SetVolume

    public void setVolume(String tag, Class<? extends Instrument> i, int vol) {
        if (!instruments.containsKey(i)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (c.instrument.getClass() != i) continue;
            c.setVolume(tag, vol, volumeMul);
        }
    }

//#endregion

//#region VisVolume

    public Set<Instrument> getFirstInstruments() {
        return instruments.values().stream().map(is -> is.get(0)).collect(Collectors.toSet());
    }

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
