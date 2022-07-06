package mdsound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import dotnet4j.util.compat.QuadConsumer;
import dotnet4j.util.compat.QuadFunction;
import dotnet4j.util.compat.TriConsumer;
import mdsound.np.NpNesFds;


public class MDSound {

    private static final int DefaultSamplingRate = 44100;
    private static final int DefaultSamplingBuffer = 512;

    private int samplingRate = DefaultSamplingRate;
    private int samplingBuffer = DefaultSamplingBuffer;
    private int[][] streamBufs = null;
    public DacControl dacControl = null;

    private Chip[] insts = null;
    private Map<InstrumentType, Instrument[]> dicInst = new HashMap<>();

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
    private final List<int[]> WSwanMask = Arrays.asList(new int[][] {new int[] {0, 0}});

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

    public enum InstrumentType {
        None,
        YM2612,
        SN76489,
        RF5C164,
        PWM,
        C140,
        OKIM6258,
        OKIM6295,
        SEGAPCM,
        YM2151,
        YM2203,
        YM2608,
        YM2610,
        AY8910,
        YM2413,
        HuC6280,
        C352,
        K054539,
        YM2609,
        K051649,
        Nes,
        DMC,
        FDS,
        MMC5,
        N160,
        VRC6,
        VRC7,
        FME7,
        MultiPCM,
        YMF262,
        YMF271,
        YMF278B,
        YMZ280B,
        DMG,
        QSound,
        GA20,
        K053260,
        Y8950,
        RF5C68,
        YM2151mame,
        YM2151x68sound,
        YM3438,
        mpcmX68k,
        YM3812,
        YM3526,
        QSoundCtr,
        PPZ8,
        PPSDRV,
        SAA1099,
        X1_010,
        P86,
        YM2612mame,
        SN76496,
        POKEY,
        WSwan,
        AY8910mame
    }

    public static class Chip {
        public interface Update extends TriConsumer<Byte, int[][], Integer> {
        }

        public interface Start extends QuadFunction<Byte, Integer, Integer, Object[], Integer> {
        }

        public interface Stop extends Consumer<Byte> {
        }

        public interface Reset extends Consumer<Byte> {
        }

        public interface AdditionalUpdate extends QuadConsumer<Chip, Byte, int[][], Integer> {
        }

        public Instrument instrument = null;
        public Update update = null;
        public Start start = null;
        public Stop stop = null;
        public Reset reset = null;
        public AdditionalUpdate additionalUpdate = null;

        public InstrumentType type = InstrumentType.None;
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
        init(DefaultSamplingRate, DefaultSamplingBuffer, null);
    }

    public MDSound(int samplingRate, int samplingBuffer, Chip[] insts) {
        init(samplingRate, samplingBuffer, insts);
    }

    public void init(int samplingRate, int samplingBuffer, Chip[] insts) {
        synchronized (lockobj) {
            this.samplingRate = samplingRate;
            this.samplingBuffer = samplingBuffer;
            this.insts = insts;

            buffer = new int[][] {new int[1], new int[1]};
            streamBufs = new int[][] {new int[0x100], new int[0x100]};

            incFlag = false;

            if (insts == null) return;

            dicInst.clear();

            // ボリューム値から実際の倍数を求める
            int total = 0;
            double[] mul = new double[1];
            for (Chip inst : insts) {
                if (inst.type == InstrumentType.Nes) inst.volume = 0;
                int balance = getRegulationVoulme(inst, mul);
                //16384 = 0x4000 = short.MAXValue + 1
                total += (int) ((((int) (16384.0 * Math.pow(10.0, 0 / 40.0)) * balance) >> 8) * mul[0]) / insts.length;
            }
            // 総ボリューム値から最大ボリュームまでの倍数を求める
            volumeMul = 16384.0 / total;
            // ボリューム値から実際の倍数を求める
            for (Chip inst : insts) {
                if ((inst.volumeBalance & 0x8000) != 0)
                    inst.tVolumeBalance = (getRegulationVoulme(inst, mul) * (inst.volumeBalance & 0x7fff) + 0x80) >> 8;
                else
                    inst.tVolumeBalance = inst.volumeBalance;
                int n = (((int) (16384.0 * Math.pow(10.0, inst.volume / 40.0)) * inst.tVolumeBalance) >> 8);
                inst.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }

            for (Chip inst : insts) {
                inst.samplingRate = inst.start.apply(inst.id, inst.samplingRate, inst.clock, inst.option);
                inst.reset.accept(inst.id);

                if (dicInst.containsKey(inst.type)) {
                    List<Instrument> lst = new ArrayList<>(Arrays.asList(dicInst.get(inst.type)));
                    lst.add(inst.instrument);
                    dicInst.put(inst.type, lst.toArray(Instrument[]::new));
                } else {
                    dicInst.put(inst.type, new Instrument[] {inst.instrument});
                }

                setupResampler(inst);
            }

            dacControl = new DacControl(samplingRate, this);

            sn76489Mask = new ArrayList<>();
            if (dicInst.containsKey(InstrumentType.SN76489))
                for (int i = 0; i < dicInst.get(InstrumentType.SN76489).length; i++)
                    sn76489Mask.add(new int[] {15, 15});
            ym2203Mask = new ArrayList<>();
            if (dicInst.containsKey(InstrumentType.YM2203))
                for (int i = 0; i < dicInst.get(InstrumentType.YM2203).length; i++) ym2203Mask.add(new int[] {0, 0});
            ym2612Mask = new ArrayList<>();
            if (dicInst.containsKey(InstrumentType.YM2612))
                for (int i = 0; i < dicInst.get(InstrumentType.YM2612).length; i++) ym2612Mask.add(new int[] {0, 0});
            else ym2612Mask.add(new int[] {0, 0});
            segapcmMask = new ArrayList<>();
            if (dicInst.containsKey(InstrumentType.SEGAPCM))
                for (int i = 0; i < dicInst.get(InstrumentType.SEGAPCM).length; i++)
                    segapcmMask.add(new int[] {0, 0});
            qsoundMask = new ArrayList<>();
            if (dicInst.containsKey(InstrumentType.QSound))
                for (int i = 0; i < dicInst.get(InstrumentType.QSound).length; i++) qsoundMask.add(new int[] {0, 0});
            qsoundCtrMask = new ArrayList<>();
            if (dicInst.containsKey(InstrumentType.QSoundCtr))
                for (int i = 0; i < dicInst.get(InstrumentType.QSoundCtr).length; i++)
                    qsoundCtrMask.add(new int[] {0, 0});
            c140Mask = new ArrayList<>();
            if (dicInst.containsKey(InstrumentType.C140))
                for (int i = 0; i < dicInst.get(InstrumentType.C140).length; i++) c140Mask.add(new int[] {0, 0});
            ay8910Mask = new ArrayList<>();
            if (dicInst.containsKey(InstrumentType.AY8910))
                for (int i = 0; i < dicInst.get(InstrumentType.AY8910).length; i++) ay8910Mask.add(new int[] {0, 0});
        }
    }

    private int getRegulationVoulme(Chip inst, double[] mul) {
        mul[0] = 1;
        final int[] CHIP_VOLS = new int[] { // CHIP_COUNT
                0x80, 0x200/*0x155*/, 0x100, 0x100, 0x180, 0xB0, 0x100, 0x80, // 00-07
                0x80, 0x100, 0x100, 0x100, 0x100, 0x100, 0x100, 0x98,         // 08-0F
                0x80, 0xE0/*0xCD*/, 0x100, 0xC0, 0x100, 0x40, 0x11E, 0x1C0,   // 10-17
                0x100/*110*/, 0xA0, 0x100, 0x100, 0x100, 0xB3, 0x100, 0x100,  // 18-1F
                0x20, 0x100, 0x100, 0x100, 0x40, 0x20, 0x100, 0x40,           // 20-27
                0x280
        };

        if (inst.type == InstrumentType.YM2413) {
            mul[0] = 0.5;
            return CHIP_VOLS[0x01];
        } else if (inst.type == InstrumentType.YM2612) {
            mul[0] = 1;
            return CHIP_VOLS[0x02];
        } else if (inst.type == InstrumentType.YM2151) {
            mul[0] = 1;
            return CHIP_VOLS[0x03];
        } else if (inst.type == InstrumentType.SEGAPCM) {
            mul[0] = 1;
            return CHIP_VOLS[0x04];
        } else if (inst.type == InstrumentType.RF5C68) {
            mul[0] = 1;
            return CHIP_VOLS[0x05];
        } else if (inst.type == InstrumentType.YM2203) {
            mul[0] = 1;
            //mul=0.5 //SSG
            return CHIP_VOLS[0x06];
        } else if (inst.type == InstrumentType.YM2608) {
            mul[0] = 1;
            return CHIP_VOLS[0x07];
        } else if (inst.type == InstrumentType.YM2610) {
            mul[0] = 1;
            return CHIP_VOLS[0x08];
        } else if (inst.type == InstrumentType.YM3812) {
            mul[0] = 2;
            return CHIP_VOLS[0x09];
        } else if (inst.type == InstrumentType.YM3526) {
            mul[0] = 2;
            return CHIP_VOLS[0x0a];
        } else if (inst.type == InstrumentType.Y8950) {
            mul[0] = 2;
            return CHIP_VOLS[0x0b];
        } else if (inst.type == InstrumentType.YMF262) {
            mul[0] = 2;
            return CHIP_VOLS[0x0c];
        } else if (inst.type == InstrumentType.YMF278B) {
            mul[0] = 1;
            return CHIP_VOLS[0x0d];
        } else if (inst.type == InstrumentType.YMF271) {
            mul[0] = 1;
            return CHIP_VOLS[0x0e];
        } else if (inst.type == InstrumentType.YMZ280B) {
            mul[0] = 0x20 / 19.0;
            return CHIP_VOLS[0x0f];
        } else if (inst.type == InstrumentType.RF5C164) {
            mul[0] = 0x2;
            return CHIP_VOLS[0x10];
        } else if (inst.type == InstrumentType.PWM) {
            mul[0] = 0x1;
            return CHIP_VOLS[0x11];
        } else if (inst.type == InstrumentType.AY8910) {
            mul[0] = 0x2;
            return CHIP_VOLS[0x12];
        } else if (inst.type == InstrumentType.DMG) {
            mul[0] = 0x2;
            return CHIP_VOLS[0x13];
        } else if (inst.type == InstrumentType.Nes) {
            mul[0] = 0x2;
            return CHIP_VOLS[0x14];
        } else if (inst.type == InstrumentType.MultiPCM) {
            mul[0] = 4;
            return CHIP_VOLS[0x15];
        }
//        else if (inst.type == InstrumentType.UPD7759) {
//            mul[0] = 1;
//            return CHIP_VOLS[0x16];
//        }
        else if (inst.type == InstrumentType.OKIM6258) {
            mul[0] = 2;
            return CHIP_VOLS[0x17];
        } else if (inst.type == InstrumentType.OKIM6295) {
            mul[0] = 2;
            return CHIP_VOLS[0x18];
        } else if (inst.type == InstrumentType.K051649) {
            mul[0] = 1;
            return CHIP_VOLS[0x19];
        } else if (inst.type == InstrumentType.K054539) {
            mul[0] = 1;
            return CHIP_VOLS[0x1a];
        } else if (inst.type == InstrumentType.HuC6280) {
            mul[0] = 1;
            return CHIP_VOLS[0x1b];
        } else if (inst.type == InstrumentType.C140) {
            mul[0] = 1;
            return CHIP_VOLS[0x1c];
        } else if (inst.type == InstrumentType.K053260) {
            mul[0] = 1;
            return CHIP_VOLS[0x1d];
        } else if (inst.type == InstrumentType.POKEY) {
            mul[0] = 1;
            return CHIP_VOLS[0x1e];
        } else if (inst.type == InstrumentType.QSound || inst.type == InstrumentType.QSoundCtr) {
            mul[0] = 1;
            return CHIP_VOLS[0x1f];
        }
        //else if (inst.type == InstrumentType.SCSP) {
        //    mul[0] = 8;
        //    return CHIP_VOLS[0x20];
        //}
        //else if (inst.type == InstrumentType.WSwan) {
        //    mul[0] = 1;
        //    return CHIP_VOLS[0x21];
        //}
        //else if (inst.type == InstrumentType.VSU) {
        //    mul[0] = 1;
        //    return CHIP_VOLS[0x22];
        //}
        else if (inst.type == InstrumentType.SAA1099) {
            mul[0] = 1;
            return CHIP_VOLS[0x23];
        }
        //else if (inst.type == InstrumentType.ES5503) {
        //    mul[0] = 8;
        //    return CHIP_VOLS[0x24];
        //}
        //else if (inst.type == InstrumentType.ES5506) {
        //    mul[0] = 16;
        //    return CHIP_VOLS[0x25];
        //}
        else if (inst.type == InstrumentType.X1_010) {
            mul[0] = 1;
            return CHIP_VOLS[0x26];
        } else if (inst.type == InstrumentType.C352) {
            mul[0] = 8;
            return CHIP_VOLS[0x27];
        } else if (inst.type == InstrumentType.GA20) {
            mul[0] = 1;
            return CHIP_VOLS[0x28];
        }

        mul[0] = 1;
        return 0x100;
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
            chip.update.accept(chip.id, buf, 1);
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
                resampleChipStream(insts, buffer, 1);
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

        // This Do-While-Loop gets and resamples the chip output of one or more chips.
        // It's a loop to support the AY8910 paired with the YM2203/YM2608/YM2610.
        for (Chip chip : insts) {
            Arrays.fill(streamBufs[0], 0);
            Arrays.fill(streamBufs[1], 0);
            curBufL = streamBufs[0x00];
            curBufR = streamBufs[0x01];

            inst = chip;
            int mul = inst.tVolume;

            //if (i != 0 && insts[i].LSmpl[0] != 0) Debug.printf("%d %d", insts[i].LSmpl[0], insts[0].LSmpl == insts[i].LSmpl);
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
                        inst.update.accept(inst.id, buff, 1);

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

                //if (inst.type == InstrumentType.Ym2612) {
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
                    inst.update.accept(inst.id, buff, 1);

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
                    inst.update.accept(inst.id, buff, 1);

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
                    inst.update.accept(inst.id, buff, 1);
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
        // chipNum: chip number (0 - first chip, 1 - second chip)
        // chipCnt: chip volume divider (number of used chips)
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
        case 0x00: // Sn76496
            // if T6W28, set volume Divider to 01
            if ((sn76496VGMHeaderClock & 0x80000000) != 0) {
                // The T6W28 consists of 2 "half" chips.
                chipNum = 0x01;
                chipCnt = 0x01;
            }
            break;
        case 0x18: // OKIM6295
            // CP System 1 patch
            if ((strSystemNameE != null && !strSystemNameE.isEmpty()) && strSystemNameE.indexOf("CP") == 0)
                volume = 110;
            break;
        case 0x86: // YM2203's AY
            volume /= 2;
            break;
        case 0x87: // YM2608's AY
            // The YM2608 outputs twice as loud as the YM2203 here.
            //volume *= 1;
            break;
        case 0x88: // YM2610's AY
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
            if (!dicInst.containsKey(InstrumentType.AY8910)) return;

            dicInst.get(InstrumentType.AY8910)[0].write(chipId, 0, adr, data);
        }
    }

    public void writeAY8910(int chipIndex, byte chipId, byte adr, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.AY8910)) return;

            dicInst.get(InstrumentType.AY8910)[chipIndex].write(chipId, 0, adr, data);
        }
    }

    public void setVolumeAY8910(int vol) {
        if (!dicInst.containsKey(InstrumentType.AY8910)) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.AY8910) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setAY8910Mask(int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(0)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.AY8910)) return;
            ((Ay8910) (dicInst.get(InstrumentType.AY8910)[0])).setMute((byte) chipId, ay8910Mask.get(0)[chipId]);
        }
    }

    public void setAY8910Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(chipIndex)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.AY8910)) return;
            ((Ay8910) (dicInst.get(InstrumentType.AY8910)[chipIndex])).setMute((byte) chipId, ay8910Mask.get(chipIndex)[chipId]);
        }
    }

    public void resetAY8910Mask(int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(0)[chipId] &= ~ch;
            if (!dicInst.containsKey(InstrumentType.AY8910)) return;
            ((Ay8910) (dicInst.get(InstrumentType.AY8910)[0])).setMute((byte) chipId, ay8910Mask.get(0)[chipId]);
        }
    }

    public void resetAY8910Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(chipIndex)[chipId] &= ~ch;
            if (!dicInst.containsKey(InstrumentType.AY8910)) return;
            ((Ay8910) (dicInst.get(InstrumentType.AY8910)[chipIndex])).setMute((byte) chipId, ay8910Mask.get(chipIndex)[chipId]);
        }
    }

    public int[][][] getAY8910VisVolume() {
        if (!dicInst.containsKey(InstrumentType.AY8910)) return null;
        return dicInst.get(InstrumentType.AY8910)[0].getVisVolume();
    }

    public int[][][] getAY8910VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.AY8910)) return null;
        return dicInst.get(InstrumentType.AY8910)[chipIndex].getVisVolume();
    }

//#endregion

//#region AY8910mame

    public void writeAY8910Mame(byte chipId, byte adr, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.AY8910mame)) return;
            if (dicInst.get(InstrumentType.AY8910mame)[0] == null) return;

            dicInst.get(InstrumentType.AY8910mame)[0].write(chipId, 0, adr, data);
        }
    }

    public void writeAY8910Mame(int chipIndex, byte chipId, byte adr, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.AY8910mame)) return;
            if (dicInst.get(InstrumentType.AY8910mame)[chipIndex] == null) return;

            dicInst.get(InstrumentType.AY8910mame)[chipIndex].write(chipId, 0, adr, data);
        }
    }

    public void setVolumeAY8910Mame(int vol) {
        if (!dicInst.containsKey(InstrumentType.AY8910mame)) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.AY8910mame) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setAY8910mameMask(int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(0)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.AY8910mame)) return;
            if (dicInst.get(InstrumentType.AY8910mame)[0] == null) return;
            ((Ay8910Mame) (dicInst.get(InstrumentType.AY8910mame)[0])).setMute((byte) chipId, ay8910Mask.get(0)[chipId]);
        }
    }

    public void setAY8910mameMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(chipIndex)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.AY8910mame)) return;
            if (dicInst.get(InstrumentType.AY8910mame)[chipIndex] == null) return;

            ((Ay8910Mame) (dicInst.get(InstrumentType.AY8910mame)[chipIndex])).setMute((byte) chipId, ay8910Mask.get(chipIndex)[chipId]);
        }
    }

    public void resetAY8910mameMask(int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(0)[chipId] &= ~ch;
            if (!dicInst.containsKey(InstrumentType.AY8910mame)) return;
            if (dicInst.get(InstrumentType.AY8910mame)[0] == null) return;

            ((Ay8910Mame) (dicInst.get(InstrumentType.AY8910mame)[0])).setMute((byte) chipId, ay8910Mask.get(0)[chipId]);
        }
    }

    public void resetAY8910mameMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(chipIndex)[chipId] &= ~ch;
            if (!dicInst.containsKey(InstrumentType.AY8910mame)) return;
            if (dicInst.get(InstrumentType.AY8910mame)[chipIndex] == null) return;

            ((Ay8910Mame) (dicInst.get(InstrumentType.AY8910mame)[chipIndex])).setMute((byte) chipId, ay8910Mask.get(chipIndex)[chipId]);
        }
    }

    public int[][][] getAY8910mameVisVolume() {
        if (!dicInst.containsKey(InstrumentType.AY8910mame)) return null;
        if (dicInst.get(InstrumentType.AY8910mame)[0] == null) return null;
        return dicInst.get(InstrumentType.AY8910mame)[0].getVisVolume();
    }

    public int[][][] getAY8910mameVisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.AY8910mame)) return null;
        if (dicInst.get(InstrumentType.AY8910mame)[chipIndex] == null) return null;

        return dicInst.get(InstrumentType.AY8910mame)[chipIndex].getVisVolume();
    }

//#endregion

//#region WSwan

    public void writeWSwan(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.WSwan)) return;
            if (dicInst.get(InstrumentType.WSwan)[0] == null) return;

            dicInst.get(InstrumentType.WSwan)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeWSwan(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.WSwan)) return;
            if (dicInst.get(InstrumentType.WSwan)[chipIndex] == null) return;

            dicInst.get(InstrumentType.WSwan)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void writeWSwanMem(byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.WSwan)) return;
            if (dicInst.get(InstrumentType.WSwan)[0] == null) return;

            ((WsAudio) (dicInst.get(InstrumentType.WSwan)[0])).WriteMem(chipId, Adr, Data);
        }
    }

    public void writeWSwanMem(int chipIndex, byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.WSwan)) return;
            if (dicInst.get(InstrumentType.WSwan)[0] == null) return;

            ((WsAudio) (dicInst.get(InstrumentType.WSwan)[chipIndex])).WriteMem(chipId, Adr, Data);
        }
    }

    public void setVolumeWSwan(int vol) {
        if (!dicInst.containsKey(InstrumentType.WSwan)) return;
        if (dicInst.get(InstrumentType.WSwan)[0] == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.WSwan) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setWSwanMask(int chipId, int ch) {
        synchronized (lockobj) {
            ay8910Mask.get(0)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.WSwan)) return;
            if (dicInst.get(InstrumentType.WSwan)[0] == null) return;
            ((WsAudio) (dicInst.get(InstrumentType.WSwan)[0])).SetMute((byte) chipId, WSwanMask.get(0)[chipId]);
        }
    }

    public void setWSwanMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            WSwanMask.get(chipIndex)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.WSwan)) return;
            if (dicInst.get(InstrumentType.WSwan)[chipIndex] == null) return;
            ((WsAudio) (dicInst.get(InstrumentType.WSwan)[chipIndex])).SetMute((byte) chipId, WSwanMask.get(chipIndex)[chipId]);
        }
    }

    public void resetWSwanMask(int chipId, int ch) {
        synchronized (lockobj) {
            WSwanMask.get(0)[chipId] &= ~ch;
            if (!dicInst.containsKey(InstrumentType.WSwan)) return;
            if (dicInst.get(InstrumentType.WSwan)[0] == null) return;
            ((WsAudio) (dicInst.get(InstrumentType.WSwan)[0])).SetMute((byte) chipId, WSwanMask.get(0)[chipId]);
        }
    }

    public void resetWSwanMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            WSwanMask.get(chipIndex)[chipId] &= ~ch;
            if (!dicInst.containsKey(InstrumentType.WSwan)) return;
            if (dicInst.get(InstrumentType.WSwan)[chipIndex] == null) return;
            ((WsAudio) (dicInst.get(InstrumentType.WSwan)[chipIndex])).SetMute((byte) chipId, WSwanMask.get(chipIndex)[chipId]);
        }
    }

    public int[][][] getWSwanVisVolume() {
        if (!dicInst.containsKey(InstrumentType.WSwan)) return null;
        if (dicInst.get(InstrumentType.WSwan)[0] == null) return null;
        return dicInst.get(InstrumentType.WSwan)[0].getVisVolume();
    }

    public int[][][] getWSwanVisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.WSwan)) return null;
        if (dicInst.get(InstrumentType.WSwan)[chipIndex] == null) return null;
        return dicInst.get(InstrumentType.WSwan)[chipIndex].getVisVolume();
    }

//#endregion

//#region SAA1099

    public void writeSAA1099(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SAA1099)) return;

            dicInst.get(InstrumentType.SAA1099)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeSAA1099(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SAA1099)) return;

            dicInst.get(InstrumentType.SAA1099)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void setVolumeSAA1099(int vol) {
        if (!dicInst.containsKey(InstrumentType.SAA1099)) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.SAA1099) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setSAA1099Mask(int chipId, int ch) {
        synchronized (lockobj) {
            saa1099Mask.get(0)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.SAA1099)) return;
            ((Saa1099) (dicInst.get(InstrumentType.SAA1099)[0])).SAA1099_SetMute((byte) chipId, saa1099Mask.get(0)[chipId]);
        }
    }

    public void setSAA1099Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            saa1099Mask.get(chipIndex)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.SAA1099)) return;
            ((Saa1099) (dicInst.get(InstrumentType.SAA1099)[chipIndex])).SAA1099_SetMute((byte) chipId, saa1099Mask.get(chipIndex)[chipId]);
        }
    }

    public void resetSAA1099Mask(int chipId, int ch) {
        synchronized (lockobj) {
            saa1099Mask.get(0)[chipId] &= ~ch;
            if (!dicInst.containsKey(InstrumentType.SAA1099)) return;
            ((Saa1099) (dicInst.get(InstrumentType.SAA1099)[0])).SAA1099_SetMute((byte) chipId, saa1099Mask.get(0)[chipId]);
        }
    }

    public void resetSAA1099Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            saa1099Mask.get(chipIndex)[chipId] &= ~ch;
            if (!dicInst.containsKey(InstrumentType.SAA1099)) return;
            ((Saa1099) (dicInst.get(InstrumentType.SAA1099)[chipIndex])).SAA1099_SetMute((byte) chipId, saa1099Mask.get(chipIndex)[chipId]);
        }
    }

    public int[][][] getSAA1099VisVolume() {
        if (!dicInst.containsKey(InstrumentType.SAA1099)) return null;
        return dicInst.get(InstrumentType.SAA1099)[0].getVisVolume();
    }

    public int[][][] getSAA1099VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.SAA1099)) return null;
        return dicInst.get(InstrumentType.SAA1099)[chipIndex].getVisVolume();
    }

//#endregion

//#region POKEY

    public void writePOKEY(byte chipId, byte adr, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.POKEY)) return;

            dicInst.get(InstrumentType.POKEY)[0].write(chipId, 0, adr, data);
        }
    }

    public void writePOKEY(int chipIndex, byte chipId, byte adr, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.POKEY)) return;

            dicInst.get(InstrumentType.POKEY)[chipIndex].write(chipId, 0, adr, data);
        }
    }

//#endregion

//#region X1_010

    public void writeX1_010(byte chipId, int adr, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.X1_010)) return;

            dicInst.get(InstrumentType.X1_010)[0].write(chipId, 0, adr, data);
        }
    }

    public void writeX1_010(int chipIndex, byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.X1_010)) return;

            dicInst.get(InstrumentType.X1_010)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void writeX1_010PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.X1_010)) return;

            //((QSound)(dicInst.get(InstrumentType.QSound)[0])).qsound_write_rom(chipId, (int)romSize, (int)dataStart, (int)dataLength, romData, (int)SrcStartAdr);
            ((X1_010) (dicInst.get(InstrumentType.X1_010)[0])).x1_010_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void writeX1_010PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.X1_010)) return;

            //((QSound)(dicInst.get(InstrumentType.QSound)[chipIndex])).qsound_write_rom(chipId, (int)romSize, (int)dataStart, (int)dataLength, romData, (int)SrcStartAdr);
            ((X1_010) (dicInst.get(InstrumentType.X1_010)[chipIndex])).x1_010_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void setVolumeX1_010(int vol) {
        if (!dicInst.containsKey(InstrumentType.X1_010)) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.X1_010) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setX1_010Mask(int chipId, int ch) {
        synchronized (lockobj) {
            x1_010Mask.get(0)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.X1_010)) return;
            ((X1_010) (dicInst.get(InstrumentType.X1_010)[0])).x1_010_set_mute_mask((byte) chipId, x1_010Mask.get(0)[chipId]);
        }
    }

    public void setX1_010Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            x1_010Mask.get(chipIndex)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.X1_010)) return;
            ((X1_010) (dicInst.get(InstrumentType.X1_010)[chipIndex])).x1_010_set_mute_mask((byte) chipId, x1_010Mask.get(chipIndex)[chipId]);
        }
    }

    public void resetX1_010Mask(int chipId, int ch) {
        synchronized (lockobj) {
            x1_010Mask.get(0)[chipId] &= ~ch;
            if (!dicInst.containsKey(InstrumentType.X1_010)) return;
            ((X1_010) (dicInst.get(InstrumentType.X1_010)[0])).x1_010_set_mute_mask((byte) chipId, x1_010Mask.get(0)[chipId]);
        }
    }

    public void resetX1_010Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            x1_010Mask.get(chipIndex)[chipId] &= ~ch;
            if (!dicInst.containsKey(InstrumentType.X1_010)) return;
            ((X1_010) (dicInst.get(InstrumentType.X1_010)[chipIndex])).x1_010_set_mute_mask((byte) chipId, x1_010Mask.get(chipIndex)[chipId]);
        }
    }

    public int[][][] getX1_010VisVolume() {
        if (!dicInst.containsKey(InstrumentType.X1_010)) return null;
        return dicInst.get(InstrumentType.X1_010)[0].getVisVolume();
    }

    public int[][][] getX1_010VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.X1_010)) return null;
        return dicInst.get(InstrumentType.X1_010)[chipIndex].getVisVolume();
    }

//#endregion

//#region SN76489

    public void writeSN76489(byte chipId, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SN76489)) return;

            dicInst.get(InstrumentType.SN76489)[0].write(chipId, 0, 0, Data);
        }
    }

    public void writeSN76489(int chipIndex, byte chipId, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SN76489)) return;

            dicInst.get(InstrumentType.SN76489)[chipIndex].write(chipId, 0, 0, Data);
        }
    }

    public void writeSN76489GGPanning(byte chipId, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SN76489)) return;

            ((Sn76489) (dicInst.get(InstrumentType.SN76489)[0])).SN76489_GGStereoWrite(chipId, Data);
        }
    }

    public void writeSN76489GGPanning(int chipIndex, byte chipId, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SN76489)) return;

            ((Sn76489) (dicInst.get(InstrumentType.SN76489)[chipIndex])).SN76489_GGStereoWrite(chipId, Data);
        }
    }

//#endregion

//#region Sn76496

    public void writeSN76496(byte chipId, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SN76496)) return;

            dicInst.get(InstrumentType.SN76496)[0].write(chipId, 0, 0, data);
        }
    }

    public void writeSN76496(int chipIndex, byte chipId, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SN76496)) return;

            dicInst.get(InstrumentType.SN76496)[chipIndex].write(chipId, 0, 0, data);
        }
    }

    public void writeSN76496GGPanning(byte chipId, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SN76496)) return;

            ((Sn76496) (dicInst.get(InstrumentType.SN76496)[0])).SN76496_GGStereoWrite(chipId, 0, 0, data);
        }
    }

    public void writeSN76496GGPanning(int chipIndex, byte chipId, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SN76496)) return;

            ((Sn76496) (dicInst.get(InstrumentType.SN76496)[chipIndex])).SN76496_GGStereoWrite(chipId, 0, 0, data);
        }
    }

//#endregion

//#region Ym2612

    public void writeYM2612(byte chipId, byte port, byte adr, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2612)) return;

            dicInst.get(InstrumentType.YM2612)[0].write(chipId, 0, (byte) (0 + (port & 1) * 2), adr);
            dicInst.get(InstrumentType.YM2612)[0].write(chipId, 0, (byte) (1 + (port & 1) * 2), data);
        }
    }

    public void writeYM2612(int chipIndex, byte chipId, byte port, byte adr, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2612)) return;

            dicInst.get(InstrumentType.YM2612)[chipIndex].write(chipId, 0, (byte) (0 + (port & 1) * 2), adr);
            dicInst.get(InstrumentType.YM2612)[chipIndex].write(chipId, 0, (byte) (1 + (port & 1) * 2), data);
        }
    }

    public void playPCMYM2612X(int chipIndex, byte chipId, byte port, byte adr, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2612)) return;
            if (!(dicInst.get(InstrumentType.YM2612)[chipIndex] instanceof Ym2612X)) return;
            ((Ym2612X) dicInst.get(InstrumentType.YM2612)[chipIndex]).XGMfunction.PlayPCM(chipId, adr, data);
        }
    }

//#endregion

//#region YM3438

    public void writeYM3438(byte chipId, byte port, byte adr, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM3438)) return;

            dicInst.get(InstrumentType.YM3438)[0].write(chipId, 0, (byte) (0 + (port & 1) * 2), adr);
            dicInst.get(InstrumentType.YM3438)[0].write(chipId, 0, (byte) (1 + (port & 1) * 2), data);
        }
    }

    public void writeYM3438(int chipIndex, byte chipId, byte port, byte adr, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM3438)) return;

            dicInst.get(InstrumentType.YM3438)[chipIndex].write(chipId, 0, (byte) (0 + (port & 1) * 2), adr);
            dicInst.get(InstrumentType.YM3438)[chipIndex].write(chipId, 0, (byte) (1 + (port & 1) * 2), data);
        }
    }

    public void playPCMYM3438X(int chipIndex, byte chipId, byte port, byte adr, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM3438)) return;
            if (!(dicInst.get(InstrumentType.YM3438)[chipIndex] instanceof Ym3438X)) return;
            ((Ym3438X) dicInst.get(InstrumentType.YM3438)[chipIndex]).xgmFunction.PlayPCM(chipId, adr, data);
        }
    }

//#endregion

//#region Ym2612

    public void writeYM2612Mame(byte chipId, byte port, byte adr, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2612mame)) return;

            dicInst.get(InstrumentType.YM2612mame)[0].write(chipId, 0, (byte) (0 + (port & 1) * 2), adr);
            dicInst.get(InstrumentType.YM2612mame)[0].write(chipId, 0, (byte) (1 + (port & 1) * 2), data);
        }
    }

    public void writeYM2612Mame(int chipIndex, byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2612mame)) return;

            dicInst.get(InstrumentType.YM2612mame)[chipIndex].write(chipId, 0, (byte) (0 + (Port & 1) * 2), Adr);
            dicInst.get(InstrumentType.YM2612mame)[chipIndex].write(chipId, 0, (byte) (1 + (Port & 1) * 2), Data);
        }
    }

    public void playPCMYM2612MameX(int chipIndex, byte chipId, byte port, byte adr, byte data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2612mame)) return;
            if (!(dicInst.get(InstrumentType.YM2612mame)[chipIndex] instanceof Ym2612MameX)) return;
            ((Ym2612MameX) dicInst.get(InstrumentType.YM2612mame)[chipIndex]).XGMfunction.PlayPCM(chipId, adr, data);
        }
    }

//#endregion

//#region PWM

    public void writePWM(byte chipId, byte adr, int data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.PWM)) return;

            dicInst.get(InstrumentType.PWM)[0].write(chipId, 0, adr, data);
            // (byte)((adr & 0xf0)>>4),(int)((adr & 0xf)*0x100+data));
        }
    }

    public void writePWM(int chipIndex, byte chipId, byte adr, int data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.PWM)) return;

            dicInst.get(InstrumentType.PWM)[chipIndex].write(chipId, 0, adr, data);
        }
    }

//#endregion

//#region RF5C164

    public void writeRF5C164(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C164)) return;

            dicInst.get(InstrumentType.RF5C164)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeRF5C164(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C164)) return;

            dicInst.get(InstrumentType.RF5C164)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteRF5C164PCMData(byte chipId, int RAMStartAdr, int RAMdataLength, byte[] SrcData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C164)) return;

            ((ScdPcm) (dicInst.get(InstrumentType.RF5C164)[0])).rf5c164_write_ram2(chipId, RAMStartAdr, RAMdataLength, SrcData, SrcStartAdr);
        }
    }

    public void WriteRF5C164PCMData(int chipIndex, byte chipId, int RAMStartAdr, int RAMdataLength, byte[] SrcData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C164)) return;

            ((ScdPcm) (dicInst.get(InstrumentType.RF5C164)[chipIndex])).rf5c164_write_ram2(chipId, RAMStartAdr, RAMdataLength, SrcData, SrcStartAdr);
        }
    }

    public void WriteRF5C164MemW(byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C164)) return;

            ((ScdPcm) (dicInst.get(InstrumentType.RF5C164)[0])).rf5c164_mem_w(chipId, Adr, Data);
        }
    }

    public void WriteRF5C164MemW(int chipIndex, byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C164)) return;

            ((ScdPcm) (dicInst.get(InstrumentType.RF5C164)[chipIndex])).rf5c164_mem_w(chipId, Adr, Data);
        }
    }

//#endregion

//#region RF5C68

    public void WriteRF5C68(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C68)) return;

            dicInst.get(InstrumentType.RF5C68)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteRF5C68(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C68)) return;

            dicInst.get(InstrumentType.RF5C68)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteRF5C68PCMData(byte chipId, int RAMStartAdr, int RAMdataLength, byte[] SrcData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C68)) return;

            ((Rf5c68) (dicInst.get(InstrumentType.RF5C68)[0])).rf5c68_write_ram2(chipId, RAMStartAdr, RAMdataLength, SrcData, SrcStartAdr);
        }
    }

    public void WriteRF5C68PCMData(int chipIndex, byte chipId, int RAMStartAdr, int RAMdataLength, byte[] SrcData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C68)) return;

            ((Rf5c68) (dicInst.get(InstrumentType.RF5C68)[chipIndex])).rf5c68_write_ram2(chipId, RAMStartAdr, RAMdataLength, SrcData, SrcStartAdr);
        }
    }

    public void WriteRF5C68MemW(byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C68)) return;

            ((Rf5c68) (dicInst.get(InstrumentType.RF5C68)[0])).rf5c68_mem_w(chipId, Adr, Data);
        }
    }

    public void WriteRF5C68MemW(int chipIndex, byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C68)) return;

            ((Rf5c68) (dicInst.get(InstrumentType.RF5C68)[chipIndex])).rf5c68_mem_w(chipId, Adr, Data);
        }
    }

//#endregion

//#region C140

    public void WriteC140(byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.C140)) return;

            dicInst.get(InstrumentType.C140)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteC140(int chipIndex, byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.C140)) return;

            dicInst.get(InstrumentType.C140)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteC140PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.C140)) return;

            ((C140) (dicInst.get(InstrumentType.C140)[0])).c140_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteC140PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.C140)) return;

            ((C140) (dicInst.get(InstrumentType.C140)[chipIndex])).c140_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion

//#region YM3812

    public void writeYM3812(int chipId, int rAdr, int rDat) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM3812)) return;

            dicInst.get(InstrumentType.YM3812)[0].write((byte) chipId, 0, rAdr, rDat);
        }
    }

    public void writeYM3812(int chipIndex, int chipId, int rAdr, int rDat) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM3812)) return;

            dicInst.get(InstrumentType.YM3812)[chipIndex].write((byte) chipId, 0, rAdr, rDat);
        }
    }

//#endregion

//#region C352

    public void WriteC352(byte chipId, int Adr, int Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.C352)) return;

            dicInst.get(InstrumentType.C352)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteC352(int chipIndex, byte chipId, int Adr, int Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.C352)) return;

            dicInst.get(InstrumentType.C352)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteC352PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.C352)) return;

            ((C352) (dicInst.get(InstrumentType.C352)[0])).c352_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteC352PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.C352)) return;

            ((C352) (dicInst.get(InstrumentType.C352)[chipIndex])).c352_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion

//#region YMF271

    public void WriteYMF271PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMF271)) return;

            ((Ymf271) (dicInst.get(InstrumentType.YMF271)[0])).ymf271_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteYMF271PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMF271)) return;

            ((Ymf271) (dicInst.get(InstrumentType.YMF271)[chipIndex])).ymf271_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion

//#region YMF278B

    public void WriteYMF278BPCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMF278B)) return;

            ((YmF278b) (dicInst.get(InstrumentType.YMF278B)[0])).ymf278b_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteYMF278BPCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMF278B)) return;

            ((YmF278b) (dicInst.get(InstrumentType.YMF278B)[chipIndex])).ymf278b_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteYMF278BPCMramData(byte chipId, int RAMSize, int dataStart, int dataLength, byte[] ramData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMF278B)) return;

            ((YmF278b) (dicInst.get(InstrumentType.YMF278B)[0])).ymf278b_write_ram(chipId, dataStart, dataLength, ramData, SrcStartAdr);
        }
    }

    public void WriteYMF278BPCMramData(int chipIndex, byte chipId, int RAMSize, int dataStart, int dataLength, byte[] ramData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMF278B)) return;

            ((YmF278b) (dicInst.get(InstrumentType.YMF278B)[chipIndex])).ymf278b_write_ram(chipId, dataStart, dataLength, ramData, SrcStartAdr);
        }
    }

//#endregion

//#region YMZ280B

    public void WriteYMZ280BPCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMZ280B)) return;

            ((YmZ280b) (dicInst.get(InstrumentType.YMZ280B)[0])).ymz280b_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteYMZ280BPCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMZ280B)) return;

            ((YmZ280b) (dicInst.get(InstrumentType.YMZ280B)[chipIndex])).ymz280b_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion

//#region Y8950

    public void WriteY8950PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.Y8950)) return;

            ((Y8950) (dicInst.get(InstrumentType.Y8950)[0])).y8950_write_data_pcmrom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteY8950PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.Y8950)) return;

            ((Y8950) (dicInst.get(InstrumentType.Y8950)[chipIndex])).y8950_write_data_pcmrom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion

//#region OKIM6258

    public void writeOKIM6258(byte chipId, byte Port, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.OKIM6258)) return;

            dicInst.get(InstrumentType.OKIM6258)[0].write(chipId, 0, Port, Data);
        }
    }

    public void writeOKIM6258(int chipIndex, byte chipId, byte Port, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.OKIM6258)) return;

            dicInst.get(InstrumentType.OKIM6258)[chipIndex].write(chipId, 0, Port, Data);
        }
    }

//#endregion

//#region OKIM6295

    public void WriteOKIM6295(byte chipId, byte Port, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.OKIM6295)) return;

            dicInst.get(InstrumentType.OKIM6295)[0].write(chipId, 0, Port, Data);
        }
    }

    public void WriteOKIM6295(int chipIndex, byte chipId, byte Port, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.OKIM6295)) return;

            dicInst.get(InstrumentType.OKIM6295)[chipIndex].write(chipId, 0, Port, Data);
        }
    }

    public void WriteOKIM6295PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.OKIM6295)) return;

            ((OkiM6295) (dicInst.get(InstrumentType.OKIM6295)[0])).okim6295_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteOKIM6295PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.OKIM6295)) return;

            ((OkiM6295) (dicInst.get(InstrumentType.OKIM6295)[chipIndex])).okim6295_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion

//#region SEGAPCM

    public void WriteSEGAPCM(byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SEGAPCM)) return;

            dicInst.get(InstrumentType.SEGAPCM)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteSEGAPCM(int chipIndex, byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SEGAPCM)) return;

            dicInst.get(InstrumentType.SEGAPCM)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteSEGAPCMPCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SEGAPCM)) return;

            ((SegaPcm) (dicInst.get(InstrumentType.SEGAPCM)[0])).sega_pcm_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteSEGAPCMPCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SEGAPCM)) return;

            ((SegaPcm) (dicInst.get(InstrumentType.SEGAPCM)[chipIndex])).sega_pcm_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion

//#region YM2151

    public void writeYM2151(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2151)) return;

            ((dicInst.get(InstrumentType.YM2151)[0])).write(chipId, 0, Adr, Data);
        }
    }

    public void writeYM2151(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2151)) return;

            ((dicInst.get(InstrumentType.YM2151)[chipIndex])).write(chipId, 0, Adr, Data);
        }
    }

    public void WriteYM2151mame(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2151mame)) return;

            ((dicInst.get(InstrumentType.YM2151mame)[0])).write(chipId, 0, Adr, Data);
        }
    }

    public void WriteYM2151mame(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2151mame)) return;

            ((dicInst.get(InstrumentType.YM2151mame)[chipIndex])).write(chipId, 0, Adr, Data);
        }
    }

    public void WriteYM2151x68sound(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2151x68sound)) return;

            ((dicInst.get(InstrumentType.YM2151x68sound)[0])).write(chipId, 0, Adr, Data);
        }
    }

    public void WriteYM2151x68sound(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2151x68sound)) return;

            ((dicInst.get(InstrumentType.YM2151x68sound)[chipIndex])).write(chipId, 0, Adr, Data);
        }
    }

//#endregion

//#region YM2203

    public void writeYM2203(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2203)) return;

            dicInst.get(InstrumentType.YM2203)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeYM2203(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2203)) return;

            dicInst.get(InstrumentType.YM2203)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

//#endregion

//#region YM2608

    public void writeYM2608(byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2608)) return;

            dicInst.get(InstrumentType.YM2608)[0].write(chipId, 0, (Port * 0x100 + Adr), Data);
        }
    }

    public void writeYM2608(int chipIndex, byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2608)) return;

            dicInst.get(InstrumentType.YM2608)[chipIndex].write(chipId, 0, (Port * 0x100 + Adr), Data);
        }
    }

    public byte[] GetADPCMBufferYM2608(byte chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2608)) return null;

            return ((Ym2608) (dicInst.get(InstrumentType.YM2608)[0])).GetADPCMBuffer(chipId);
        }
    }

    public byte[] GetADPCMBufferYM2608(int chipIndex, byte chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2608)) return null;

            return ((Ym2608) (dicInst.get(InstrumentType.YM2608)[chipIndex])).GetADPCMBuffer(chipId);
        }
    }

    public int ReadStatusExYM2608(byte chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2608)) throw new IllegalStateException();

            return ((Ym2608) (dicInst.get(InstrumentType.YM2608)[0])).ReadStatusEx(chipId);
        }
    }

    public int ReadStatusExYM2608(int chipIndex, byte chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2608)) throw new IllegalStateException();

            return ((Ym2608) (dicInst.get(InstrumentType.YM2608)[chipIndex])).ReadStatusEx(chipId);
        }
    }

//#endregion

//#region YM2609

    public void writeYM2609(byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2609)) return;

            dicInst.get(InstrumentType.YM2609)[0].write(chipId, 0, (Port * 0x100 + Adr), Data);
        }
    }

    public void writeYM2609(int chipIndex, byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2609)) return;

            dicInst.get(InstrumentType.YM2609)[chipIndex].write(chipId, 0, (Port * 0x100 + Adr), Data);
        }
    }

    public void WriteYM2609_SetAdpcmA(byte chipId, byte[] Buf) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2609)) return;

            ((Ym2609) (dicInst.get(InstrumentType.YM2609)[0])).SetAdpcmA(chipId, Buf, Buf.length);
        }
    }

    public void WriteYM2609_SetAdpcmA(int chipIndex, byte chipId, byte[] Buf) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2609)) return;

            ((Ym2609) (dicInst.get(InstrumentType.YM2609)[chipIndex])).SetAdpcmA(chipId, Buf, Buf.length);
        }
    }

//#endregion


//#region YM2610

    public void writeYM2610(byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2610)) return;

            dicInst.get(InstrumentType.YM2610)[0].write(chipId, 0, (Port * 0x100 + Adr), Data);
        }
    }

    public void writeYM2610(int chipIndex, byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2610)) return;

            dicInst.get(InstrumentType.YM2610)[chipIndex].write(chipId, 0, (Port * 0x100 + Adr), Data);
        }
    }

    public void writeYM2610SetAdpcmA(byte chipId, byte[] Buf) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2610)) return;

            ((Ym2610) (dicInst.get(InstrumentType.YM2610)[0])).YM2610_setAdpcmA(chipId, Buf, Buf.length);
        }
    }

    public void writeYM2610SetAdpcmA(int chipIndex, byte chipId, byte[] Buf) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2610)) return;

            ((Ym2610) (dicInst.get(InstrumentType.YM2610)[chipIndex])).YM2610_setAdpcmA(chipId, Buf, Buf.length);
        }
    }

    public void writeYM2610SetAdpcmB(byte chipId, byte[] Buf) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2610)) return;

            ((Ym2610) (dicInst.get(InstrumentType.YM2610)[0])).YM2610_setAdpcmB(chipId, Buf, Buf.length);
        }
    }

    public void writeYM2610SetAdpcmB(int chipIndex, byte chipId, byte[] Buf) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2610)) return;

            ((Ym2610) (dicInst.get(InstrumentType.YM2610)[chipIndex])).YM2610_setAdpcmB(chipId, Buf, Buf.length);
        }
    }

//#endregion


//#region YMF262

    public void writeYMF262(byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMF262)) return;

            dicInst.get(InstrumentType.YMF262)[0].write(chipId, 0, (Port * 0x100 + Adr), Data);
        }
    }

    public void writeYMF262(int chipIndex, byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMF262)) return;

            dicInst.get(InstrumentType.YMF262)[chipIndex].write(chipId, 0, (Port * 0x100 + Adr), Data);
        }
    }

//#endregion


//#region YMF271

    public void writeYMF271(byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMF271)) return;

            dicInst.get(InstrumentType.YMF271)[0].write(chipId, Port, Adr, Data);
        }
    }

    public void writeYMF271(int chipIndex, byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMF271)) return;

            dicInst.get(InstrumentType.YMF271)[chipIndex].write(chipId, Port, Adr, Data);
        }
    }

//#endregion


//#region YMF278B

    public void writeYMF278B(byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMF278B)) return;
            dicInst.get(InstrumentType.YMF278B)[0].write(chipId, Port, Adr, Data);
        }
    }

    public void writeYMF278B(int chipIndex, byte chipId, byte Port, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMF278B)) return;
            dicInst.get(InstrumentType.YMF278B)[chipIndex].write(chipId, Port, Adr, Data);
        }
    }

//#endregion


//#region YM3526

    public void writeYM3526(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM3526)) return;

            dicInst.get(InstrumentType.YM3526)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeYM3526(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM3526)) return;

            dicInst.get(InstrumentType.YM3526)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

//#endregion


//#region Y8950

    public void writeY8950(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.Y8950)) return;

            dicInst.get(InstrumentType.Y8950)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeY8950(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.Y8950)) return;

            dicInst.get(InstrumentType.Y8950)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

//#endregion


//#region YMZ280B

    public void writeYMZ280B(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMZ280B)) return;

            dicInst.get(InstrumentType.YMZ280B)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeYMZ280B(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMZ280B)) return;

            dicInst.get(InstrumentType.YMZ280B)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

//#endregion


//#region HuC6280

    public void writeHuC6280(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.HuC6280)) return;

            dicInst.get(InstrumentType.HuC6280)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeHuC6280(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.HuC6280)) return;

            dicInst.get(InstrumentType.HuC6280)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public byte ReadHuC6280(byte chipId, byte Adr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.HuC6280)) return 0;

            return ((OotakePsg) (dicInst.get(InstrumentType.HuC6280)[0])).HuC6280_Read(chipId, Adr);
        }
    }

    public byte ReadHuC6280(int chipIndex, byte chipId, byte Adr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.HuC6280)) return 0;

            return ((OotakePsg) (dicInst.get(InstrumentType.HuC6280)[chipIndex])).HuC6280_Read(chipId, Adr);
        }
    }

//#endregion


//#region GA20

    public void WriteGA20(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.GA20)) return;

            dicInst.get(InstrumentType.GA20)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteGA20(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.GA20)) return;

            dicInst.get(InstrumentType.GA20)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

//#endregion


//#region YM2413

    public void writeYM2413(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2413)) return;

            dicInst.get(InstrumentType.YM2413)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeYM2413(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2413)) return;

            dicInst.get(InstrumentType.YM2413)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

//#endregion


//#region K051649

    public void WriteK051649(byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.K051649)) return;

            dicInst.get(InstrumentType.K051649)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteK051649(int chipIndex, byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.K051649)) return;

            dicInst.get(InstrumentType.K051649)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

//#endregion


//#region K053260

    public void WriteK053260(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.K053260)) return;

            dicInst.get(InstrumentType.K053260)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteK053260(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.K053260)) return;

            dicInst.get(InstrumentType.K053260)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteK053260PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.K053260)) return;

            ((K053260) (dicInst.get(InstrumentType.K053260)[0])).k053260_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteK053260PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.K053260)) return;

            ((K053260) (dicInst.get(InstrumentType.K053260)[chipIndex])).k053260_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion


//#region K054539

    public void WriteK054539(byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.K054539)) return;

            dicInst.get(InstrumentType.K054539)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteK054539(int chipIndex, byte chipId, int Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.K054539)) return;

            dicInst.get(InstrumentType.K054539)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void writeK054539PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.K054539)) return;

            ((K054539) (dicInst.get(InstrumentType.K054539)[0])).k054539_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void writeK054539PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.K054539)) return;

            ((K054539) (dicInst.get(InstrumentType.K054539)[chipIndex])).k054539_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion


//#region PPZ8

    public void WritePPZ8(byte chipId, int port, int address, int data, byte[] addtionalData) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.PPZ8)) return;

            //if (port == 0x03) {
            //    ((PPZ8)(dicInst.get(InstrumentType.PPZ8)[0])).LoadPcm(chipId, (byte)address, (byte)data, addtionalData);
            //} else {
            dicInst.get(InstrumentType.PPZ8)[0].write(chipId, port, address, data);
            //}
        }
    }

    public void WritePPZ8(int chipIndex, byte chipId, int port, int address, int data, byte[] addtionalData) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.PPZ8)) return;

            //if (port == 0x03) {
            //    ((PPZ8)(dicInst.get(InstrumentType.PPZ8)[0])).LoadPcm(chipId, (byte)address, (byte)data, addtionalData);
            //} else {
            dicInst.get(InstrumentType.PPZ8)[chipIndex].write(chipId, port, address, data);
            //}
        }
    }

    public void WritePPZ8PCMData(byte chipId, int address, int data, byte[][] PCMData) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.PPZ8)) return;

            ((PPZ8) (dicInst.get(InstrumentType.PPZ8)[0])).loadPcm(chipId, (byte) address, (byte) data, PCMData);
        }
    }

    public void WritePPZ8PCMData(int chipIndex, byte chipId, int address, int data, byte[][] PCMData) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.PPZ8)) return;

            ((PPZ8) (dicInst.get(InstrumentType.PPZ8)[chipIndex])).loadPcm(chipId, (byte) address, (byte) data, PCMData);
        }
    }

    public int[][][] getPPZ8VisVolume() {
        if (!dicInst.containsKey(InstrumentType.PPZ8)) return null;
        return dicInst.get(InstrumentType.PPZ8)[0].getVisVolume();
    }

    public int[][][] getPPZ8VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.PPZ8)) return null;
        return dicInst.get(InstrumentType.PPZ8)[chipIndex].getVisVolume();
    }

//#endregion


//#region PPSDRV

    public void WritePPSDRV(byte chipId, int port, int address, int data, byte[] addtionalData) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.PPSDRV)) return;

            dicInst.get(InstrumentType.PPSDRV)[0].write(chipId, port, address, data);
        }
    }

    public void WritePPSDRV(int chipIndex, byte chipId, int port, int address, int data, byte[] addtionalData) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.PPSDRV)) return;

            dicInst.get(InstrumentType.PPSDRV)[chipIndex].write(chipId, port, address, data);
        }
    }

    public void WritePPSDRVPCMData(byte chipId, byte[] PCMData) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.PPSDRV)) return;

            ((PPSDRV) (dicInst.get(InstrumentType.PPSDRV)[0])).load(chipId, PCMData);
        }
    }

    public void WritePPSDRVPCMData(int chipIndex, byte chipId, byte[] PCMData) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.PPSDRV)) return;

            ((PPSDRV) (dicInst.get(InstrumentType.PPSDRV)[chipIndex])).load(chipId, PCMData);
        }
    }

//#endregion


//#region P86

    public void writeP86(byte chipId, int port, int address, int data, byte[] addtionalData) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.P86)) return;

            //if (port == 0x03) {
            //    ((P86)(dicInst.get(InstrumentType.P86)[0])).LoadPcm(chipId, (byte)address, (byte)data, addtionalData);
            //} else {
            dicInst.get(InstrumentType.P86)[0].write(chipId, port, address, data);
            //}
        }
    }

    public void writeP86(int chipIndex, byte chipId, int port, int address, int data, byte[] addtionalData) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.P86)) return;

            //if (port == 0x03) {
            //    ((P86)(dicInst.get(InstrumentType.P86)[0])).LoadPcm(chipId, (byte)address, (byte)data, addtionalData);
            //} else {
            dicInst.get(InstrumentType.P86)[chipIndex].write(chipId, port, address, data);
            //}
        }
    }

    public void writeP86PCMData(byte chipId, int address, int data, byte[] pcmData) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.P86)) return;

            ((P86) (dicInst.get(InstrumentType.P86)[0])).loadPcm(chipId, (byte) address, (byte) data, pcmData);
        }
    }

    public void writeP86PCMData(int chipIndex, byte chipId, int address, int data, byte[] pcmData) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.P86)) return;

            ((P86) (dicInst.get(InstrumentType.P86)[chipIndex])).loadPcm(chipId, (byte) address, (byte) data, pcmData);
        }
    }

//#endregion


//#region QSound

    public void WriteQSound(byte chipId, int adr, byte dat) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.QSound)) return;

            ((QSound) (dicInst.get(InstrumentType.QSound)[0])).qsound_w(chipId, adr, dat);
        }
    }

    public void WriteQSound(int chipIndex, byte chipId, int adr, byte dat) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.QSound)) return;

            ((QSound) (dicInst.get(InstrumentType.QSound)[chipIndex])).qsound_w(chipId, adr, dat);
        }
    }

    public void WriteQSoundPCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.QSound)) return;

            ((QSound) (dicInst.get(InstrumentType.QSound)[0])).qsound_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteQSoundPCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.QSound)) return;

            ((QSound) (dicInst.get(InstrumentType.QSound)[chipIndex])).qsound_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion


//#region QSoundCtr

    public void WriteQSoundCtr(byte chipId, int adr, byte dat) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.QSoundCtr)) return;

            //((QSound)(dicInst.get(InstrumentType.QSound)[0])).qsound_w(chipId, adr, dat);
            ((QSoundCtr) (dicInst.get(InstrumentType.QSoundCtr)[0])).qsound_w(chipId, adr, dat);
        }
    }

    public void WriteQSoundCtr(int chipIndex, byte chipId, int adr, byte dat) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.QSoundCtr)) return;

            //((QSound)(dicInst.get(InstrumentType.QSound)[chipIndex])).qsound_w(chipId, adr, dat);
            ((QSoundCtr) (dicInst.get(InstrumentType.QSoundCtr)[chipIndex])).qsound_w(chipId, adr, dat);
        }
    }

    public void WriteQSoundCtrPCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.QSoundCtr)) return;

            //((QSound)(dicInst.get(InstrumentType.QSound)[0])).qsound_write_rom(chipId, (int)romSize, (int)dataStart, (int)dataLength, romData, (int)SrcStartAdr);
            ((QSoundCtr) (dicInst.get(InstrumentType.QSoundCtr)[0])).qsound_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteQSoundCtrPCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.QSoundCtr)) return;

            //((QSound)(dicInst.get(InstrumentType.QSound)[chipIndex])).qsound_write_rom(chipId, (int)romSize, (int)dataStart, (int)dataLength, romData, (int)SrcStartAdr);
            ((QSoundCtr) (dicInst.get(InstrumentType.QSoundCtr)[chipIndex])).qsound_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion


//#region GA20

    public void WriteGA20PCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.GA20)) return;

            ((Iremga20) (dicInst.get(InstrumentType.GA20)[0])).iremga20_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteGA20PCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.GA20)) return;

            ((Iremga20) (dicInst.get(InstrumentType.GA20)[chipIndex])).iremga20_write_rom(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion


//#region DMG

    public void writeDMG(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.DMG)) return;

            dicInst.get(InstrumentType.DMG)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeDMG(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.DMG)) return;

            dicInst.get(InstrumentType.DMG)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public Gb.GbSound ReadDMG(byte chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.DMG)) return null;

            return ((Gb) (dicInst.get(InstrumentType.DMG)[0])).getSoundData(chipId);
        }

    }

    public Gb.GbSound ReadDMG(int chipIndex, byte chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.DMG)) return null;

            return ((Gb) (dicInst.get(InstrumentType.DMG)[chipIndex])).getSoundData(chipId);
        }

    }

    public void setDMGMask(byte chipId, int ch) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.DMG)) return;
            if (dicInst.get(InstrumentType.DMG)[0] == null) return;

            int maskStatus = ((Gb) (dicInst.get(InstrumentType.DMG)[0])).getMuteMask(chipId);
            maskStatus |= 1 << ch;//ch:0 - 3
            ((Gb) (dicInst.get(InstrumentType.DMG)[0])).setMuteMask(chipId, maskStatus);
        }
    }

    public void resetDMGMask(byte chipId, int ch) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.DMG)) return;
            if (dicInst.get(InstrumentType.DMG)[0] == null) return;

            int maskStatus = ((Gb) (dicInst.get(InstrumentType.DMG)[0])).getMuteMask(chipId);
            maskStatus &= ~(1 << ch);//ch:0 - 3
            ((Gb) (dicInst.get(InstrumentType.DMG)[0])).setMuteMask(chipId, maskStatus);
        }
    }

    public void setDMGMask(int chipIndex, byte chipId, int ch) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.DMG)) return;
            if (dicInst.get(InstrumentType.DMG)[chipIndex] == null) return;

            int maskStatus = ((Gb) (dicInst.get(InstrumentType.DMG)[chipIndex])).getMuteMask(chipId);
            maskStatus |= 1 << ch;//ch:0 - 3
            ((Gb) (dicInst.get(InstrumentType.DMG)[chipIndex])).setMuteMask(chipId, maskStatus);
        }
    }

    public void resetDMGMask(int chipIndex, byte chipId, int ch) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.DMG)) return;
            if (dicInst.get(InstrumentType.DMG)[chipIndex] == null) return;

            int maskStatus = ((Gb) (dicInst.get(InstrumentType.DMG)[chipIndex])).getMuteMask(chipId);
            maskStatus &= ~(1 << ch);//ch:0 - 3
            ((Gb) (dicInst.get(InstrumentType.DMG)[chipIndex])).setMuteMask(chipId, maskStatus);
        }
    }

//#endregion


//#region NES

    public void writeNES(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.Nes)) return;

            dicInst.get(InstrumentType.Nes)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void writeNES(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.Nes)) return;

            dicInst.get(InstrumentType.Nes)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteNESRam(byte chipId, int dataStart, int dataLength, byte[] ramData, int RAMdataStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.Nes)) return;

            ((NesIntF) (dicInst.get(InstrumentType.Nes)[0])).nes_write_ram(chipId, dataStart, dataLength, ramData, RAMdataStartAdr);
        }
    }

    public void WriteNESRam(int chipIndex, byte chipId, int dataStart, int dataLength, byte[] ramData, int RAMdataStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.Nes)) return;

            ((NesIntF) (dicInst.get(InstrumentType.Nes)[chipIndex])).nes_write_ram(chipId, dataStart, dataLength, ramData, RAMdataStartAdr);
        }
    }

    public byte[] ReadNESapu(byte chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.Nes)) return null;

            return ((NesIntF) (dicInst.get(InstrumentType.Nes)[0])).nes_r_apu(chipId);
        }
    }

    public byte[] ReadNESapu(int chipIndex, byte chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.Nes)) return null;

            return ((NesIntF) (dicInst.get(InstrumentType.Nes)[chipIndex])).nes_r_apu(chipId);
        }
    }

    public byte[] ReadNESdmc(byte chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.Nes)) return null;

            return ((NesIntF) (dicInst.get(InstrumentType.Nes)[0])).nes_r_dmc(chipId);
        }
    }

    public byte[] ReadNESdmc(int chipIndex, byte chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.Nes)) return null;

            return ((NesIntF) (dicInst.get(InstrumentType.Nes)[chipIndex])).nes_r_dmc(chipId);
        }
    }

//#endregion


//#region VRC6

    int[] vrc6AddressTable = new int[] {
            0x9000, 0x9001, 0x9002, 0x9003,
            0xa000, 0xa001, 0xa002, 0xa003,
            0xb000, 0xb001, 0xb002, 0xb003
    };

    public void WriteVRC6(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.VRC6)) return;

            dicInst.get(InstrumentType.VRC6)[chipIndex].write(chipId, 0, vrc6AddressTable[Adr], Data);
        }
    }

//#endregion


//#region MultiPCM

    public void WriteMultiPCM(byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.MultiPCM)) return;

            dicInst.get(InstrumentType.MultiPCM)[0].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteMultiPCM(int chipIndex, byte chipId, byte Adr, byte Data) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.MultiPCM)) return;

            dicInst.get(InstrumentType.MultiPCM)[chipIndex].write(chipId, 0, Adr, Data);
        }
    }

    public void WriteMultiPCMSetBank(byte chipId, byte Ch, int Adr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.MultiPCM)) return;

            ((MultiPcm) (dicInst.get(InstrumentType.MultiPCM)[0])).multipcm_bank_write(chipId, Ch, Adr);
        }
    }

    public void WriteMultiPCMSetBank(int chipIndex, byte chipId, byte Ch, int Adr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.MultiPCM)) return;

            ((MultiPcm) (dicInst.get(InstrumentType.MultiPCM)[chipIndex])).multipcm_bank_write(chipId, Ch, Adr);
        }
    }

    public void WriteMultiPCMPCMData(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.MultiPCM)) return;

            ((MultiPcm) (dicInst.get(InstrumentType.MultiPCM)[0])).multipcm_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

    public void WriteMultiPCMPCMData(int chipIndex, byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.MultiPCM)) return;

            ((MultiPcm) (dicInst.get(InstrumentType.MultiPCM)[chipIndex])).multipcm_write_rom2(chipId, romSize, dataStart, dataLength, romData, SrcStartAdr);
        }
    }

//#endregion


//#region FDS

    public NpNesFds readFDS(byte chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.Nes)) return null;

            return ((NesIntF) (dicInst.get(InstrumentType.Nes)[0])).nes_r_fds(chipId);
        }
    }

    public NpNesFds readFDS(int chipIndex, byte chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.Nes)) return null;

            return ((NesIntF) (dicInst.get(InstrumentType.Nes)[chipIndex])).nes_r_fds(chipId);
        }
    }

//#endregion


    public void setVolumeYM2151(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2151)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2151) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setVolumeYM2151Mame(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2151mame)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2151mame) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYM2151x68sound(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2151x68sound)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2151x68sound) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYM2203(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2203)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2203) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYM2203FM(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2203)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2203) continue;
            ((Ym2203) c.instrument).SetFMVolume((byte) 0, vol);
            ((Ym2203) c.instrument).SetFMVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYM2203PSG(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2203)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2203) continue;
            ((Ym2203) c.instrument).SetPSGVolume((byte) 0, vol);
            ((Ym2203) c.instrument).SetPSGVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYM2413(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2413)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2413) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setVolumeHuC6280(int vol) {
        if (!dicInst.containsKey(InstrumentType.HuC6280)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.HuC6280) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYM2608(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2608)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2608) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYM2608FM(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2608)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2608) continue;
            ((Ym2608) c.instrument).SetFMVolume((byte) 0, vol);
            ((Ym2608) c.instrument).SetFMVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYM2608PSG(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2608)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2608) continue;
            ((Ym2608) c.instrument).SetPSGVolume((byte) 0, vol);
            ((Ym2608) c.instrument).SetPSGVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYM2608Rhythm(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2608)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2608) continue;
            ((Ym2608) c.instrument).SetRhythmVolume((byte) 0, vol);
            ((Ym2608) c.instrument).SetRhythmVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYM2608Adpcm(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2608)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2608) continue;
            ((Ym2608) c.instrument).SetAdpcmVolume((byte) 0, vol);
            ((Ym2608) c.instrument).SetAdpcmVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYM2609FM(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2609)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2609) continue;
            ((Ym2609) c.instrument).SetFMVolume((byte) 0, vol);
            ((Ym2609) c.instrument).SetFMVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYM2609PSG(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2609)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2609) continue;
            ((Ym2609) c.instrument).SetPSGVolume((byte) 0, vol);
            ((Ym2609) c.instrument).SetPSGVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYM2609Rhythm(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2609)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2609) continue;
            ((Ym2609) c.instrument).SetRhythmVolume((byte) 0, vol);
            ((Ym2609) c.instrument).SetRhythmVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYM2609Adpcm(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2609)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2609) continue;
            ((Ym2609) c.instrument).SetAdpcmVolume((byte) 0, vol);
            ((Ym2609) c.instrument).SetAdpcmVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYM2610(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2610)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2610) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYM2610FM(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2610)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2610) continue;
            ((Ym2610) c.instrument).SetFMVolume((byte) 0, vol);
            ((Ym2610) c.instrument).SetFMVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYM2610PSG(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2610)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2610) continue;
            ((Ym2610) c.instrument).SetPSGVolume((byte) 0, vol);
            ((Ym2610) c.instrument).SetPSGVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYM2610AdpcmA(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2610)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2610) continue;
            ((Ym2610) c.instrument).SetAdpcmAVolume((byte) 0, vol);
            ((Ym2610) c.instrument).SetAdpcmAVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYM2610AdpcmB(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2610)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2610) continue;
            ((Ym2610) c.instrument).SetAdpcmBVolume((byte) 0, vol);
            ((Ym2610) c.instrument).SetAdpcmBVolume((byte) 1, vol);
        }
    }

    public void SetVolumeYM2612(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM2612)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM2612) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYM3438(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM3438)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM3438) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeSN76489(int vol) {
        if (!dicInst.containsKey(InstrumentType.SN76489)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.SN76489) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeRF5C164(int vol) {
        if (!dicInst.containsKey(InstrumentType.RF5C164)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.RF5C164) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumePWM(int vol) {
        if (!dicInst.containsKey(InstrumentType.PWM)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.PWM) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeOKIM6258(int vol) {
        if (!dicInst.containsKey(InstrumentType.OKIM6258)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.OKIM6258) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeMpcmX68k(int vol) {
        if (!dicInst.containsKey(InstrumentType.mpcmX68k)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.mpcmX68k) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeOKIM6295(int vol) {
        if (!dicInst.containsKey(InstrumentType.OKIM6295)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.OKIM6295) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeC140(int vol) {
        if (!dicInst.containsKey(InstrumentType.C140)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.C140) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeC352(int vol) {
        if (!dicInst.containsKey(InstrumentType.C352)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.C352) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetRearMute(byte flag) {
        if (!dicInst.containsKey(InstrumentType.C352)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.C352) continue;
            for (int i = 0; i < dicInst.get(InstrumentType.C352).length; i++)
                ((C352) dicInst.get(InstrumentType.C352)[i]).c352_set_options(flag);
        }
    }

    public void SetVolumeK051649(int vol) {
        if (!dicInst.containsKey(InstrumentType.K051649)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.K051649) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeK053260(int vol) {
        if (!dicInst.containsKey(InstrumentType.K053260)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.K053260) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeRF5C68(int vol) {
        if (!dicInst.containsKey(InstrumentType.RF5C68)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.RF5C68) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYM3812(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM3812)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM3812) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeY8950(int vol) {
        if (!dicInst.containsKey(InstrumentType.Y8950)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.Y8950) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYM3526(int vol) {
        if (!dicInst.containsKey(InstrumentType.YM3526)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YM3526) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeK054539(int vol) {
        if (!dicInst.containsKey(InstrumentType.K054539)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.K054539) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeQSound(int vol) {
        if (!dicInst.containsKey(InstrumentType.QSound)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.QSound) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeQSoundCtr(int vol) {
        if (!dicInst.containsKey(InstrumentType.QSoundCtr)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.QSoundCtr) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setVolumeDMG(int vol) {
        if (!dicInst.containsKey(InstrumentType.DMG)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.DMG) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setVolumeGA20(int vol) {
        if (!dicInst.containsKey(InstrumentType.GA20)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.GA20) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setVolumeYMZ280B(int vol) {
        if (!dicInst.containsKey(InstrumentType.YMZ280B)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YMZ280B) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setVolumeYMF271(int vol) {
        if (!dicInst.containsKey(InstrumentType.YMF271)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YMF271) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYMF262(int vol) {
        if (!dicInst.containsKey(InstrumentType.YMF262)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YMF262) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeYMF278B(int vol) {
        if (!dicInst.containsKey(InstrumentType.YMF278B)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.YMF278B) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setVolumeMultiPCM(int vol) {
        if (!dicInst.containsKey(InstrumentType.MultiPCM)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.MultiPCM) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void setVolumeSegaPCM(int vol) {
        if (!dicInst.containsKey(InstrumentType.SEGAPCM)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.SEGAPCM) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeSAA1099(int vol) {
        if (!dicInst.containsKey(InstrumentType.SAA1099)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.SAA1099) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumePPZ8(int vol) {
        if (!dicInst.containsKey(InstrumentType.PPZ8)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.PPZ8) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeNES(int vol) {
        if (!dicInst.containsKey(InstrumentType.Nes)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type == InstrumentType.Nes) {
                c.volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
                ((NesIntF) c.instrument).setVolumeAPU(vol);
            }
        }
    }

    public void SetVolumeDMC(int vol) {
        if (!dicInst.containsKey(InstrumentType.DMC)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type == InstrumentType.DMC) {
                c.volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
                ((NesIntF) c.instrument).setVolumeDMC(vol);
            }
        }
    }

    public void SetVolumeFDS(int vol) {
        if (!dicInst.containsKey(InstrumentType.FDS)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type == InstrumentType.FDS) {
                c.volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
                ((NesIntF) c.instrument).setVolumeFDS(vol);
            }
        }
    }

    public void SetVolumeMMC5(int vol) {
        if (!dicInst.containsKey(InstrumentType.MMC5)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.MMC5) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeN160(int vol) {
        if (!dicInst.containsKey(InstrumentType.N160)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.N160) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeVRC6(int vol) {
        if (!dicInst.containsKey(InstrumentType.VRC6)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.VRC6) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeVRC7(int vol) {
        if (!dicInst.containsKey(InstrumentType.VRC7)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.VRC7) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    public void SetVolumeFME7(int vol) {
        if (!dicInst.containsKey(InstrumentType.FME7)) return;

        if (insts == null) return;

        for (Chip c : insts) {
            if (c.type != InstrumentType.FME7) continue;
            c.volume = Math.max(Math.min(vol, 20), -192);
            //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
            int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
            //16384 = 0x4000 = short.MAXValue + 1
            c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }


    public int[] ReadSN76489Register() {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SN76489)) return null;
            return ((Sn76489) (dicInst.get(InstrumentType.SN76489)[0])).chips[0].registers;
        }
    }

    public int[] ReadSN76489Register(int chipIndex) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SN76489)) return null;
            return ((Sn76489) (dicInst.get(InstrumentType.SN76489)[chipIndex])).chips[0].registers;
        }
    }

    public int[][] ReadYM2612Register(byte chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2612)) return null;
            return ((Ym2612) (dicInst.get(InstrumentType.YM2612)[0])).chips[chipId].regs;
        }
    }

    public int[][] ReadYM2612Register(int chipIndex, byte chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2612)) return null;
            return ((Ym2612) (dicInst.get(InstrumentType.YM2612)[chipIndex])).chips[chipId].regs;
        }
    }

    public ScdPcm.PcmChip ReadRf5c164Register(int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C164)) return null;
            if (((ScdPcm) (dicInst.get(InstrumentType.RF5C164)[0])).PCM_Chip == null || ((ScdPcm) (dicInst.get(InstrumentType.RF5C164)[0])).PCM_Chip.length < 1)
                return null;
            return ((ScdPcm) (dicInst.get(InstrumentType.RF5C164)[0])).PCM_Chip[chipId];
        }
    }

    public ScdPcm.PcmChip ReadRf5c164Register(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C164)) return null;
            if (((ScdPcm) (dicInst.get(InstrumentType.RF5C164)[chipIndex])).PCM_Chip == null || ((ScdPcm) (dicInst.get(InstrumentType.RF5C164)[chipIndex])).PCM_Chip.length < 1)
                return null;
            return ((ScdPcm) (dicInst.get(InstrumentType.RF5C164)[chipIndex])).PCM_Chip[chipId];
        }
    }

    public Rf5c68.Rf5c68State ReadRf5c68Register(int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C68)) return null;
            if (((Rf5c68) (dicInst.get(InstrumentType.RF5C68)[0])).rf5C68Data == null || ((Rf5c68) (dicInst.get(InstrumentType.RF5C68)[0])).rf5C68Data.length < 1)
                return null;
            return ((Rf5c68) (dicInst.get(InstrumentType.RF5C68)[0])).rf5C68Data[chipId];
        }
    }

    public Rf5c68.Rf5c68State ReadRf5c68Register(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C68)) return null;
            if (((Rf5c68) (dicInst.get(InstrumentType.RF5C68)[chipIndex])).rf5C68Data == null || ((Rf5c68) (dicInst.get(InstrumentType.RF5C68)[chipIndex])).rf5C68Data.length < 1)
                return null;
            return ((Rf5c68) (dicInst.get(InstrumentType.RF5C68)[chipIndex])).rf5C68Data[chipId];
        }
    }

    public C140.C140State ReadC140Register(int cur) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.C140)) return null;
            return ((C140) dicInst.get(InstrumentType.C140)[0]).c140Data[cur];
        }
    }

    public C140.C140State ReadC140Register(int chipIndex, int cur) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.C140)) return null;
            return ((C140) dicInst.get(InstrumentType.C140)[chipIndex]).c140Data[cur];
        }
    }

    public int[] ReadC352Flag(int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.C352)) return null;
            return ((C352) dicInst.get(InstrumentType.C352)[0]).getFlags((byte) chipId);
        }
    }

    public int[] ReadC352Flag(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.C352)) return null;
            return ((C352) dicInst.get(InstrumentType.C352)[chipIndex]).getFlags((byte) chipId);
        }
    }

    public MultiPcm.MultiPCM ReadMultiPCMRegister(int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.MultiPCM)) return null;
            return ((MultiPcm) (dicInst.get(InstrumentType.MultiPCM)[0])).multipcm_r(chipId);
        }
    }

    public MultiPcm.MultiPCM ReadMultiPCMRegister(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.MultiPCM)) return null;
            return ((MultiPcm) (dicInst.get(InstrumentType.MultiPCM)[chipIndex])).multipcm_r(chipId);
        }
    }

    public Ymf271.YMF271Chip ReadYMF271Register(int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMF271)) return null;
            if (dicInst.get(InstrumentType.YMF271)[0] == null) return null;
            return ((Ymf271) (dicInst.get(InstrumentType.YMF271)[0])).ymf271Chips[chipId];
        }
    }

    public Ymf271.YMF271Chip ReadYMF271Register(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YMF271)) return null;
            if (dicInst.get(InstrumentType.YMF271)[chipIndex] == null) return null;
            return ((Ymf271) (dicInst.get(InstrumentType.YMF271)[chipIndex])).ymf271Chips[chipId];
        }
    }


    public OkiM6258.OkiM6258State ReadOKIM6258Status(int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.OKIM6258)) return null;
            return ((OkiM6258) dicInst.get(InstrumentType.OKIM6258)[0]).okiM6258Data[chipId];
        }
    }

    public OkiM6258.OkiM6258State ReadOKIM6258Status(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.OKIM6258)) return null;
            return ((OkiM6258) dicInst.get(InstrumentType.OKIM6258)[chipIndex]).okiM6258Data[chipId];
        }
    }

    public OkiM6295.OkiM6295State ReadOKIM6295Status(int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.OKIM6295)) return null;
            return ((OkiM6295) dicInst.get(InstrumentType.OKIM6295)[0]).chips[chipId];
        }
    }

    public OkiM6295.OkiM6295State ReadOKIM6295Status(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.OKIM6295)) return null;
            return ((OkiM6295) dicInst.get(InstrumentType.OKIM6295)[chipIndex]).chips[chipId];
        }
    }

    public SegaPcm.SegaPcmState ReadSegaPCMStatus(int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SEGAPCM)) return null;
            return ((SegaPcm) dicInst.get(InstrumentType.SEGAPCM)[0]).SPCMData[chipId];
        }
    }

    public SegaPcm.SegaPcmState ReadSegaPCMStatus(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.SEGAPCM)) return null;
            return ((SegaPcm) dicInst.get(InstrumentType.SEGAPCM)[chipIndex]).SPCMData[chipId];
        }
    }


    public OotakePsg.HuC6280State ReadHuC6280Status(int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.HuC6280)) return null;
            return ((OotakePsg) dicInst.get(InstrumentType.HuC6280)[0]).GetState((byte) chipId);
        }
    }

    public OotakePsg.HuC6280State ReadHuC6280Status(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.HuC6280)) return null;
            return ((OotakePsg) dicInst.get(InstrumentType.HuC6280)[chipIndex]).GetState((byte) chipId);
        }
    }

    public K051649.K051649State ReadK051649Status(int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.K051649)) return null;
            return ((K051649) dicInst.get(InstrumentType.K051649)[0]).GetK051649_State((byte) chipId);
        }
    }

    public K051649.K051649State ReadK051649Status(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.K051649)) return null;
            return ((K051649) dicInst.get(InstrumentType.K051649)[chipIndex]).GetK051649_State((byte) chipId);
        }
    }

    public int[][] ReadRf5c164Volume(int chipId) {
        synchronized (lockobj) {
            return rf5c164Vol[chipId];
        }
    }

    public PPZ8.PPZ8Status.Channel[] readPPZ8Status(int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.PPZ8)) return null;
            return ((PPZ8) dicInst.get(InstrumentType.PPZ8)[0]).getPPZ8State((byte) chipId);
        }
    }

    public PPZ8.PPZ8Status.Channel[] readPPZ8Status(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.PPZ8)) return null;
            return ((PPZ8) dicInst.get(InstrumentType.PPZ8)[chipIndex]).getPPZ8State((byte) chipId);
        }
    }


    public int[] ReadYM2612KeyOn(byte chipId) {
        synchronized (lockobj) {
            int[] keys = new int[((Ym2612) (dicInst.get(InstrumentType.YM2612)[0])).chips[chipId].channels.length];
            for (int i = 0; i < keys.length; i++)
                keys[i] = ((Ym2612) (dicInst.get(InstrumentType.YM2612)[0])).chips[chipId].channels[i].keyOn;
            return keys;
        }
    }

    public int[] ReadYM2612KeyOn(int chipIndex, byte chipId) {
        synchronized (lockobj) {
            int[] keys = new int[((Ym2612) (dicInst.get(InstrumentType.YM2612)[chipIndex])).chips[chipId].channels.length];
            for (int i = 0; i < keys.length; i++)
                keys[i] = ((Ym2612) (dicInst.get(InstrumentType.YM2612)[chipIndex])).chips[chipId].channels[i].keyOn;
            return keys;
        }
    }

    public int[] ReadYM2151KeyOn(int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2151)) return null;
            for (int i = 0; i < 8; i++) {
                //ym2151Key[chipId][i] = ((Ym2151)(iYM2151)).YM2151_Chip[chipId].CHANNEL[i].KeyOn;
            }
            return ym2151Key[chipId];
        }
    }

    public int[] ReadYM2203KeyOn(int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2203)) return null;
            for (int i = 0; i < 6; i++) {
                //ym2203Key[chipId][i] = ((Ym2203)(iYM2203)).YM2203_Chip[chipId].CHANNEL[i].KeyOn;
            }
            return ym2203Key[chipId];
        }
    }

    public int[] ReadYM2608KeyOn(int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2608)) return null;
            for (int i = 0; i < 11; i++) {
                //ym2608Key[chipId][i] = ((Ym2608)(iYM2608)).YM2608_Chip[chipId].CHANNEL[i].KeyOn;
            }
            return ym2608Key[chipId];
        }
    }

    public int[] ReadYM2609KeyOn(int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2609)) return null;
            for (int i = 0; i < 11; i++) {
                //ym2608Key[chipId][i] = ((Ym2608)(iYM2608)).YM2608_Chip[chipId].CHANNEL[i].KeyOn;
            }
            return ym2609Key[chipId];
        }
    }

    public int[] ReadYM2610KeyOn(int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.YM2610)) return null;
            for (int i = 0; i < 11; i++) {
                //ym2610Key[chipId][i] = ((Ym2610)(iYM2610)).YM2610_Chip[chipId].CHANNEL[i].KeyOn;
            }
            return ym2610Key[chipId];
        }
    }


    public void setSN76489Mask(int chipId, int ch) {
        synchronized (lockobj) {
            sn76489Mask.get(0)[chipId] &= ~ch;
            if (!dicInst.containsKey(InstrumentType.SN76489)) return;
            ((Sn76489) (dicInst.get(InstrumentType.SN76489)[0])).SN76489_SetMute((byte) chipId, sn76489Mask.get(0)[chipId]);
        }
    }

    public void setSN76489Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            sn76489Mask.get(chipIndex)[chipId] &= ~ch;
            if (!dicInst.containsKey(InstrumentType.SN76489)) return;
            ((Sn76489) (dicInst.get(InstrumentType.SN76489)[chipIndex])).SN76489_SetMute((byte) chipId, sn76489Mask.get(chipIndex)[chipId]);
        }
    }

    public void setYM2612Mask(int chipId, int ch) {
        synchronized (lockobj) {
            ym2612Mask.get(0)[chipId] |= 1 << ch;
            if (dicInst.containsKey(InstrumentType.YM2612)) {
                ((Ym2612) (dicInst.get(InstrumentType.YM2612)[0])).YM2612_SetMute((byte) chipId, ym2612Mask.get(0)[chipId]);
            }
            if (dicInst.containsKey(InstrumentType.YM2612mame)) {
                ((Ym2612Mame) (dicInst.get(InstrumentType.YM2612mame)[0])).SetMute((byte) chipId, ym2612Mask.get(0)[chipId]);
            }
            if (dicInst.containsKey(InstrumentType.YM3438)) {
                int mask = ym2612Mask.get(0)[chipId];
                if ((mask & 0b0010_0000) == 0) mask &= 0b1011_1111;
                else mask |= 0b0100_0000;
                ((Ym3438) (dicInst.get(InstrumentType.YM3438)[0])).setMute((byte) chipId, mask);
            }
        }
    }

    public void setYM2612Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ym2612Mask.get(chipIndex)[chipId] |= 1 << ch;
            if (dicInst.containsKey(InstrumentType.YM2612)) {
                ((Ym2612) (dicInst.get(InstrumentType.YM2612)[chipIndex])).YM2612_SetMute((byte) chipId, ym2612Mask.get(chipIndex)[chipId]);
            }
            if (dicInst.containsKey(InstrumentType.YM2612mame)) {
                ((Ym2612Mame) (dicInst.get(InstrumentType.YM2612mame)[chipIndex])).SetMute((byte) chipId, ym2612Mask.get(chipIndex)[chipId]);
            }
            if (dicInst.containsKey(InstrumentType.YM3438)) {
                int mask = ym2612Mask.get(chipIndex)[chipId];
                if ((mask & 0b0010_0000) == 0) mask &= 0b1011_1111;
                else mask |= 0b0100_0000;
                ((Ym3438) (dicInst.get(InstrumentType.YM3438)[chipIndex])).setMute((byte) chipId, mask);
            }
        }
    }

    public void setYM2203Mask(int chipId, int ch) {
        synchronized (lockobj) {
            ym2203Mask.get(0)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.YM2203)) return;
            ((Ym2203) (dicInst.get(InstrumentType.YM2203)[0])).YM2203_SetMute((byte) chipId, ym2203Mask.get(0)[chipId]);
        }
    }

    public void setYM2203Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ym2203Mask.get(chipIndex)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.YM2203)) return;
            ((Ym2203) (dicInst.get(InstrumentType.YM2203)[chipIndex])).YM2203_SetMute((byte) chipId, ym2203Mask.get(chipIndex)[chipId]);
        }
    }

    public void setRf5c164Mask(int chipId, int ch) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C164)) return;
            ((ScdPcm) (dicInst.get(InstrumentType.RF5C164)[0])).PCM_Chip[chipId].channels[ch].muted = 1;
        }
    }

    public void setRf5c164Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C164)) return;
            ((ScdPcm) (dicInst.get(InstrumentType.RF5C164)[chipIndex])).PCM_Chip[chipId].channels[ch].muted = 1;
        }
    }

    public void setRf5c68Mask(int chipId, int ch) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C68)) return;
            ((Rf5c68) (dicInst.get(InstrumentType.RF5C68)[0])).rf5C68Data[chipId].chan[ch].muted = 1;
        }
    }

    public void setRf5c68Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C68)) return;
            ((Rf5c68) (dicInst.get(InstrumentType.RF5C68)[chipIndex])).rf5C68Data[chipId].chan[ch].muted = 1;
        }
    }

    public void setC140Mask(int chipId, int ch) {
        synchronized (lockobj) {
            c140Mask.get(0)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.C140)) return;
            ((C140) (dicInst.get(InstrumentType.C140)[0])).c140_set_mute_mask((byte) chipId, c140Mask.get(0)[chipId]);
        }
    }

    public void setC140Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            c140Mask.get(chipIndex)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.C140)) return;
            ((C140) (dicInst.get(InstrumentType.C140)[chipIndex])).c140_set_mute_mask((byte) chipId, c140Mask.get(chipIndex)[chipId]);
        }
    }

    public void setSegaPcmMask(int chipId, int ch) {
        synchronized (lockobj) {
            segapcmMask.get(0)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.SEGAPCM)) return;
            ((SegaPcm) (dicInst.get(InstrumentType.SEGAPCM)[0])).segapcm_set_mute_mask((byte) chipId, segapcmMask.get(0)[chipId]);
        }
    }

    public void setSegaPcmMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            segapcmMask.get(chipIndex)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.SEGAPCM)) return;
            ((SegaPcm) (dicInst.get(InstrumentType.SEGAPCM)[chipIndex])).segapcm_set_mute_mask((byte) chipId, segapcmMask.get(chipIndex)[chipId]);
        }
    }

    public void setQSoundMask(int chipId, int ch) {
        synchronized (lockobj) {
            ch = (1 << ch);
            qsoundMask.get(0)[chipId] |= ch;
            if (dicInst.containsKey(InstrumentType.QSound)) {
                ((QSound) (dicInst.get(InstrumentType.QSound)[0])).qsound_set_mute_mask((byte) chipId, qsoundMask.get(0)[chipId]);
            }
        }
    }

    public void setQSoundMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ch = (1 << ch);
            qsoundMask.get(chipIndex)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.QSound)) {
                ((QSound) (dicInst.get(InstrumentType.QSound)[chipIndex])).qsound_set_mute_mask((byte) chipId, qsoundMask.get(chipIndex)[chipId]);
            }
        }
    }

    public void setQSoundCtrMask(int chipId, int ch) {
        synchronized (lockobj) {
            ch = (1 << ch);
            qsoundCtrMask.get(0)[chipId] |= ch;
            if (dicInst.containsKey(InstrumentType.QSoundCtr)) {
                ((QSoundCtr) (dicInst.get(InstrumentType.QSoundCtr)[0])).qsound_set_mute_mask((byte) chipId, qsoundCtrMask.get(0)[chipId]);
            }
        }
    }

    public void setQSoundCtrMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ch = (1 << ch);
            qsoundCtrMask.get(chipIndex)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.QSoundCtr)) {
                ((QSoundCtr) (dicInst.get(InstrumentType.QSoundCtr)[chipIndex])).qsound_set_mute_mask((byte) chipId, qsoundCtrMask.get(chipIndex)[chipId]);
            }
        }
    }

    public void setOKIM6295Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            okim6295Mask.get(chipIndex)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.OKIM6295)) return;
            ((OkiM6295) (dicInst.get(InstrumentType.OKIM6295)[chipIndex])).okim6295_set_mute_mask((byte) chipId, okim6295Mask.get(chipIndex)[chipId]);
        }
    }

    public OkiM6295.OkiM6295State.ChannelInfo getOKIM6295Info(int chipIndex, int chipId) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.OKIM6295)) return null;
            return ((OkiM6295) (dicInst.get(InstrumentType.OKIM6295)[chipIndex])).readChInfo((byte) chipId);
        }
    }

    public void setHuC6280Mask(int chipId, int ch) {
        synchronized (lockobj) {
            huc6280Mask.get(0)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.HuC6280)) return;
            ((OotakePsg) (dicInst.get(InstrumentType.HuC6280)[0])).HuC6280_SetMute((byte) chipId, huc6280Mask.get(0)[chipId]);
        }
    }

    public void setHuC6280Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            huc6280Mask.get(chipIndex)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.HuC6280)) return;
            ((OotakePsg) (dicInst.get(InstrumentType.HuC6280)[chipIndex])).HuC6280_SetMute((byte) chipId, huc6280Mask.get(chipIndex)[chipId]);
        }
    }

    public void setNESMask(int chipId, int ch) {
        synchronized (lockobj) {
            nesMask.get(0)[chipId] |= 0x1 << ch;
            if (!dicInst.containsKey(InstrumentType.Nes)) return;
            ((NesIntF) (dicInst.get(InstrumentType.Nes)[0])).nes_set_mute_mask((byte) chipId, nesMask.get(0)[chipId]);
        }
    }

    public void setNESMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            nesMask.get(chipIndex)[chipId] |= 0x1 << ch;
            if (!dicInst.containsKey(InstrumentType.Nes)) return;
            ((NesIntF) (dicInst.get(InstrumentType.Nes)[chipIndex])).nes_set_mute_mask((byte) chipId, nesMask.get(chipIndex)[chipId]);
        }
    }

    public void setFDSMask(int chipId) {
        synchronized (lockobj) {
            nesMask.get(0)[chipId] |= 0x20;
            if (!dicInst.containsKey(InstrumentType.Nes)) return;
            ((NesIntF) (dicInst.get(InstrumentType.Nes)[0])).nes_set_mute_mask((byte) chipId, nesMask.get(0)[chipId]);
        }
    }

    public void setFDSMask(int chipIndex, int chipId) {
        synchronized (lockobj) {
            nesMask.get(chipIndex)[chipId] |= 0x20;
            if (!dicInst.containsKey(InstrumentType.Nes)) return;
            ((NesIntF) (dicInst.get(InstrumentType.Nes)[chipIndex])).nes_set_mute_mask((byte) chipId, nesMask.get(chipIndex)[chipId]);
        }
    }


    public void resetSN76489Mask(int chipId, int ch) {
        synchronized (lockobj) {
            sn76489Mask.get(0)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.SN76489)) return;
            ((Sn76489) (dicInst.get(InstrumentType.SN76489)[0])).SN76489_SetMute((byte) chipId, sn76489Mask.get(0)[chipId]);
        }
    }

    public void resetSN76489Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            sn76489Mask.get(chipIndex)[chipId] |= ch;
            if (!dicInst.containsKey(InstrumentType.SN76489)) return;
            ((Sn76489) (dicInst.get(InstrumentType.SN76489)[chipIndex])).SN76489_SetMute((byte) chipId, sn76489Mask.get(chipIndex)[chipId]);
        }
    }


    public void resetYM2612Mask(int chipId, int ch) {
        synchronized (lockobj) {
            ym2612Mask.get(0)[chipId] &= ~(1 << ch);
            if (dicInst.containsKey(InstrumentType.YM2612)) {
                ((Ym2612) (dicInst.get(InstrumentType.YM2612)[0])).YM2612_SetMute((byte) chipId, ym2612Mask.get(0)[chipId]);
            }
            if (dicInst.containsKey(InstrumentType.YM2612mame)) {
                ((Ym2612Mame) (dicInst.get(InstrumentType.YM2612mame)[0])).SetMute((byte) chipId, ym2612Mask.get(0)[chipId]);
            }
            if (dicInst.containsKey(InstrumentType.YM3438)) {
                int mask = ym2612Mask.get(0)[chipId];
                if ((mask & 0b0010_0000) == 0) mask &= 0b1011_1111;
                else mask |= 0b0100_0000;
                ((Ym3438) (dicInst.get(InstrumentType.YM3438)[0])).setMute((byte) chipId, mask);
            }
        }
    }

    public void resetYM2612Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ym2612Mask.get(chipIndex)[chipId] &= ~(1 << ch);
            if (dicInst.containsKey(InstrumentType.YM2612)) {
                ((Ym2612) (dicInst.get(InstrumentType.YM2612)[chipIndex])).YM2612_SetMute((byte) chipId, ym2612Mask.get(chipIndex)[chipId]);
            }
            if (dicInst.containsKey(InstrumentType.YM2612mame)) {
                ((Ym2612Mame) (dicInst.get(InstrumentType.YM2612mame)[chipIndex])).SetMute((byte) chipId, ym2612Mask.get(chipIndex)[chipId]);
            }
            if (dicInst.containsKey(InstrumentType.YM3438)) {
                int mask = ym2612Mask.get(chipIndex)[chipId];
                if ((mask & 0b0010_0000) == 0) mask &= 0b1011_1111;
                else mask |= 0b0100_0000;
                ((Ym3438) (dicInst.get(InstrumentType.YM3438)[chipIndex])).setMute((byte) chipId, mask);
            }
        }
    }

    public void resetYM2203Mask(int chipId, int ch) {
        synchronized (lockobj) {
            ym2203Mask.get(0)[chipId] &= ~ch;
            if (!dicInst.containsKey(InstrumentType.YM2203)) return;
            ((Ym2203) (dicInst.get(InstrumentType.YM2203)[0])).YM2203_SetMute((byte) chipId, ym2203Mask.get(0)[chipId]);
        }
    }

    public void resetYM2203Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ym2203Mask.get(chipIndex)[chipId] &= ~ch;
            if (!dicInst.containsKey(InstrumentType.YM2203)) return;
            ((Ym2203) (dicInst.get(InstrumentType.YM2203)[chipIndex])).YM2203_SetMute((byte) chipId, ym2203Mask.get(chipIndex)[chipId]);
        }
    }

    public void resetRf5c164Mask(int chipId, int ch) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C164)) return;
            ((ScdPcm) (dicInst.get(InstrumentType.RF5C164)[0])).PCM_Chip[chipId].channels[ch].muted = 0;
        }
    }

    public void resetRf5c164Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C164)) return;
            ((ScdPcm) (dicInst.get(InstrumentType.RF5C164)[chipIndex])).PCM_Chip[chipId].channels[ch].muted = 0;
        }
    }

    public void resetRf5c68Mask(int chipId, int ch) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C68)) return;
            ((Rf5c68) (dicInst.get(InstrumentType.RF5C68)[0])).rf5C68Data[chipId].chan[ch].muted = 0;
        }
    }

    public void resetRf5c68Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            if (!dicInst.containsKey(InstrumentType.RF5C68)) return;
            ((Rf5c68) (dicInst.get(InstrumentType.RF5C68)[chipIndex])).rf5C68Data[chipId].chan[ch].muted = 0;
        }
    }

    public void resetC140Mask(int chipId, int ch) {
        synchronized (lockobj) {
            c140Mask.get(0)[chipId] &= ~(int) ch;
            if (!dicInst.containsKey(InstrumentType.C140)) return;
            ((C140) (dicInst.get(InstrumentType.C140)[0])).c140_set_mute_mask((byte) chipId, c140Mask.get(0)[chipId]);
        }
    }

    public void resetC140Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            c140Mask.get(chipIndex)[chipId] &= ~(int) ch;
            if (!dicInst.containsKey(InstrumentType.C140)) return;
            ((C140) (dicInst.get(InstrumentType.C140)[chipIndex])).c140_set_mute_mask((byte) chipId, c140Mask.get(chipIndex)[chipId]);
        }
    }

    public void resetSegaPcmMask(int chipId, int ch) {
        synchronized (lockobj) {
            segapcmMask.get(0)[chipId] &= ~(int) ch;
            if (!dicInst.containsKey(InstrumentType.SEGAPCM)) return;
            ((SegaPcm) (dicInst.get(InstrumentType.SEGAPCM)[0])).segapcm_set_mute_mask((byte) chipId, segapcmMask.get(0)[chipId]);
        }
    }

    public void resetSegaPcmMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            segapcmMask.get(chipIndex)[chipId] &= ~(int) ch;
            if (!dicInst.containsKey(InstrumentType.SEGAPCM)) return;
            ((SegaPcm) (dicInst.get(InstrumentType.SEGAPCM)[chipIndex])).segapcm_set_mute_mask((byte) chipId, segapcmMask.get(chipIndex)[chipId]);
        }
    }

    public void resetQSoundMask(int chipId, int ch) {
        synchronized (lockobj) {
            ch = (1 << ch);
            qsoundMask.get(0)[chipId] &= ~(int) ch;
            if (dicInst.containsKey(InstrumentType.QSound)) {
                ((QSound) (dicInst.get(InstrumentType.QSound)[0])).qsound_set_mute_mask((byte) chipId, qsoundMask.get(0)[chipId]);
            }
        }
    }

    public void resetQSoundMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ch = (1 << ch);
            qsoundMask.get(chipIndex)[chipId] &= ~(int) ch;
            if (!dicInst.containsKey(InstrumentType.QSound)) {
                ((QSound) (dicInst.get(InstrumentType.QSound)[chipIndex])).qsound_set_mute_mask((byte) chipId, qsoundMask.get(chipIndex)[chipId]);
            }
        }
    }

    public void resetQSoundCtrMask(int chipId, int ch) {
        synchronized (lockobj) {
            ch = (1 << ch);
            qsoundCtrMask.get(0)[chipId] &= ~(int) ch;
            if (dicInst.containsKey(InstrumentType.QSoundCtr)) {
                ((QSoundCtr) (dicInst.get(InstrumentType.QSoundCtr)[0])).qsound_set_mute_mask((byte) chipId, qsoundCtrMask.get(0)[chipId]);
            }
        }
    }

    public void resetQSoundCtrMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            ch = (1 << ch);
            qsoundCtrMask.get(chipIndex)[chipId] &= ~(int) ch;
            if (!dicInst.containsKey(InstrumentType.QSoundCtr)) {
                ((QSoundCtr) (dicInst.get(InstrumentType.QSoundCtr)[chipIndex])).qsound_set_mute_mask((byte) chipId, qsoundCtrMask.get(chipIndex)[chipId]);
            }
        }
    }

    public void resetOKIM6295Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            okim6295Mask.get(chipIndex)[chipId] &= ~(int) ch;
            if (!dicInst.containsKey(InstrumentType.OKIM6295)) return;
            ((OkiM6295) (dicInst.get(InstrumentType.OKIM6295)[chipIndex])).okim6295_set_mute_mask((byte) chipId, okim6295Mask.get(chipIndex)[chipId]);
        }
    }

    public void resetHuC6280Mask(int chipId, int ch) {
        synchronized (lockobj) {
            huc6280Mask.get(0)[chipId] &= ~ch;
            if (!dicInst.containsKey(InstrumentType.HuC6280)) return;
            ((OotakePsg) (dicInst.get(InstrumentType.HuC6280)[0])).HuC6280_SetMute((byte) chipId, huc6280Mask.get(0)[chipId]);
        }
    }

    public void resetHuC6280Mask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            huc6280Mask.get(chipIndex)[chipId] &= ~ch;
            if (!dicInst.containsKey(InstrumentType.HuC6280)) return;
            ((OotakePsg) (dicInst.get(InstrumentType.HuC6280)[chipIndex])).HuC6280_SetMute((byte) chipId, huc6280Mask.get(chipIndex)[chipId]);
        }
    }

    public void resetNESMask(int chipId, int ch) {
        synchronized (lockobj) {
            nesMask.get(0)[chipId] &= ~(0x1 << ch);
            if (!dicInst.containsKey(InstrumentType.Nes)) return;
            ((NesIntF) (dicInst.get(InstrumentType.Nes)[0])).nes_set_mute_mask((byte) chipId, nesMask.get(0)[chipId]);
        }
    }

    public void resetNESMask(int chipIndex, int chipId, int ch) {
        synchronized (lockobj) {
            nesMask.get(chipIndex)[chipId] &= ~(0x1 << ch);
            if (!dicInst.containsKey(InstrumentType.Nes)) return;
            ((NesIntF) (dicInst.get(InstrumentType.Nes)[chipIndex])).nes_set_mute_mask((byte) chipId, nesMask.get(chipIndex)[chipId]);
        }
    }

    public void resetFDSMask(int chipId) {
        synchronized (lockobj) {
            nesMask.get(0)[chipId] &= ~(int) 0x20;
            if (!dicInst.containsKey(InstrumentType.Nes)) return;
            ((NesIntF) (dicInst.get(InstrumentType.Nes)[0])).nes_set_mute_mask((byte) chipId, nesMask.get(0)[chipId]);
        }
    }

    public void resetFDSMask(int chipIndex, int chipId) {
        synchronized (lockobj) {
            nesMask.get(chipIndex)[chipId] &= ~(int) 0x20;
            if (!dicInst.containsKey(InstrumentType.Nes)) return;
            ((NesIntF) (dicInst.get(InstrumentType.Nes)[chipIndex])).nes_set_mute_mask((byte) chipId, nesMask.get(chipIndex)[chipId]);
        }
    }

    public int[][][] getYM2151VisVolume() {
        if (!dicInst.containsKey(InstrumentType.YM2151)) return null;
        return (dicInst.get(InstrumentType.YM2151)[0]).getVisVolume();
    }

    public int[][][] getYM2151VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.YM2151)) return null;
        return (dicInst.get(InstrumentType.YM2151)[chipIndex]).getVisVolume();
    }

    public int[][][] getYM2203VisVolume() {
        if (!dicInst.containsKey(InstrumentType.YM2203)) return null;
        return dicInst.get(InstrumentType.YM2203)[0].getVisVolume();
    }

    public int[][][] getYM2203VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.YM2203)) return null;
        return dicInst.get(InstrumentType.YM2203)[chipIndex].getVisVolume();
    }

    public int[][][] getYM2413VisVolume() {
        if (!dicInst.containsKey(InstrumentType.YM2413)) return null;
        return dicInst.get(InstrumentType.YM2413)[0].getVisVolume();
    }

    public int[][][] getYM2413VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.YM2413)) return null;
        return dicInst.get(InstrumentType.YM2413)[chipIndex].getVisVolume();
    }

    public int[][][] getYM2608VisVolume() {
        if (!dicInst.containsKey(InstrumentType.YM2608)) return null;
        return dicInst.get(InstrumentType.YM2608)[0].getVisVolume();
    }

    public int[][][] getYM2608VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.YM2608)) return null;
        return dicInst.get(InstrumentType.YM2608)[chipIndex].getVisVolume();
    }

    public int[][][] getYM2609VisVolume() {
        if (!dicInst.containsKey(InstrumentType.YM2609)) return null;
        return dicInst.get(InstrumentType.YM2609)[0].getVisVolume();
    }

    public int[][][] getYM2609VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.YM2609)) return null;
        return dicInst.get(InstrumentType.YM2609)[chipIndex].getVisVolume();
    }

    public int[][][] getYM2610VisVolume() {
        if (!dicInst.containsKey(InstrumentType.YM2610)) return null;
        return dicInst.get(InstrumentType.YM2610)[0].getVisVolume();
    }

    public int[][][] getYM2610VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.YM2610)) return null;
        return dicInst.get(InstrumentType.YM2610)[chipIndex].getVisVolume();
    }

    public int[][][] getYM2612VisVolume() {
        if (!dicInst.containsKey(InstrumentType.YM2612)) {
            if (!dicInst.containsKey(InstrumentType.YM2612mame)) return null;
            return (dicInst.get(InstrumentType.YM2612mame)[0]).getVisVolume();
        }
        return (dicInst.get(InstrumentType.YM2612)[0]).getVisVolume();
    }

    public int[][][] getYM2612VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.YM2612)) {
            if (!dicInst.containsKey(InstrumentType.YM2612mame)) return null;
            return (dicInst.get(InstrumentType.YM2612mame)[chipIndex]).getVisVolume();
        }
        return (dicInst.get(InstrumentType.YM2612)[chipIndex]).getVisVolume();
    }

    public int[][][] getSN76489VisVolume() {
        if (dicInst.containsKey(InstrumentType.SN76489)) {
            return dicInst.get(InstrumentType.SN76489)[0].getVisVolume();
        } else if (dicInst.containsKey(InstrumentType.SN76496)) {
            return dicInst.get(InstrumentType.SN76496)[0].getVisVolume();
        }
        return null;
    }

    public int[][][] getSN76489VisVolume(int chipIndex) {
        if (dicInst.containsKey(InstrumentType.SN76489)) {
            return dicInst.get(InstrumentType.SN76489)[chipIndex].getVisVolume();
        } else if (dicInst.containsKey(InstrumentType.SN76496)) {
            return dicInst.get(InstrumentType.SN76496)[chipIndex].getVisVolume();
        }
        return null;
    }

    public int[][][] getHuC6280VisVolume() {
        if (!dicInst.containsKey(InstrumentType.HuC6280)) return null;
        return dicInst.get(InstrumentType.HuC6280)[0].getVisVolume();
    }

    public int[][][] getHuC6280VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.HuC6280)) return null;
        return dicInst.get(InstrumentType.HuC6280)[chipIndex].getVisVolume();
    }

    public int[][][] getRF5C164VisVolume() {
        if (!dicInst.containsKey(InstrumentType.RF5C164)) return null;
        return dicInst.get(InstrumentType.RF5C164)[0].getVisVolume();
    }

    public int[][][] getRF5C164VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.RF5C164)) return null;
        return dicInst.get(InstrumentType.RF5C164)[chipIndex].getVisVolume();
    }

    public int[][][] getPWMVisVolume() {
        if (!dicInst.containsKey(InstrumentType.PWM)) return null;
        return dicInst.get(InstrumentType.PWM)[0].getVisVolume();
    }

    public int[][][] getPWMVisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.PWM)) return null;
        return dicInst.get(InstrumentType.PWM)[chipIndex].getVisVolume();
    }

    public int[][][] getOKIM6258VisVolume() {
        if (!dicInst.containsKey(InstrumentType.OKIM6258)) return null;
        return dicInst.get(InstrumentType.OKIM6258)[0].getVisVolume();
    }

    public int[][][] getOKIM6258VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.OKIM6258)) return null;
        return dicInst.get(InstrumentType.OKIM6258)[chipIndex].getVisVolume();
    }

    public int[][][] getOKIM6295VisVolume() {
        if (!dicInst.containsKey(InstrumentType.OKIM6295)) return null;
        return dicInst.get(InstrumentType.OKIM6295)[0].getVisVolume();
    }

    public int[][][] getOKIM6295VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.OKIM6295)) return null;
        return dicInst.get(InstrumentType.OKIM6295)[chipIndex].getVisVolume();
    }

    public int[][][] getC140VisVolume() {
        if (!dicInst.containsKey(InstrumentType.C140)) return null;
        return dicInst.get(InstrumentType.C140)[0].getVisVolume();
    }

    public int[][][] getC140VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.C140)) return null;
        return dicInst.get(InstrumentType.C140)[chipIndex].getVisVolume();
    }

    public int[][][] getSegaPCMVisVolume() {
        if (!dicInst.containsKey(InstrumentType.SEGAPCM)) return null;
        return dicInst.get(InstrumentType.SEGAPCM)[0].getVisVolume();
    }

    public int[][][] getSegaPCMVisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.SEGAPCM)) return null;
        return dicInst.get(InstrumentType.SEGAPCM)[chipIndex].getVisVolume();
    }

    public int[][][] getC352VisVolume() {
        if (!dicInst.containsKey(InstrumentType.C352)) return null;
        return dicInst.get(InstrumentType.C352)[0].getVisVolume();
    }

    public int[][][] getC352VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.C352)) return null;
        return dicInst.get(InstrumentType.C352)[chipIndex].getVisVolume();
    }

    public int[][][] getK051649VisVolume() {
        if (!dicInst.containsKey(InstrumentType.K051649)) return null;
        return dicInst.get(InstrumentType.K051649)[0].getVisVolume();
    }

    public int[][][] getK051649VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.K051649)) return null;
        return dicInst.get(InstrumentType.K051649)[chipIndex].getVisVolume();
    }

    public int[][][] getK054539VisVolume() {
        if (!dicInst.containsKey(InstrumentType.K054539)) return null;
        return dicInst.get(InstrumentType.K054539)[0].getVisVolume();
    }

    public int[][][] getK054539VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.K054539)) return null;
        return dicInst.get(InstrumentType.K054539)[chipIndex].getVisVolume();
    }


    public int[][][] getNESVisVolume() {
        return null;
        //if (!dicInst.containsKey(InstrumentType.Nes)) return null;
        //return dicInst.get(InstrumentType.Nes) .getVisVolume();
    }

    public int[][][] getDMCVisVolume() {
        return null;
        //if (!dicInst.containsKey(InstrumentType.DMC)) return null;
        //return dicInst.get(InstrumentType.DMC) .getVisVolume();
    }

    public int[][][] getFDSVisVolume() {
        return null;
        //if (!dicInst.containsKey(InstrumentType.FDS)) return null;
        //return dicInst.get(InstrumentType.FDS) .getVisVolume();
    }

    public int[][][] getMMC5VisVolume() {
        if (!dicInst.containsKey(InstrumentType.MMC5)) return null;
        return dicInst.get(InstrumentType.MMC5)[0].getVisVolume();
    }

    public int[][][] getMMC5VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.MMC5)) return null;
        return dicInst.get(InstrumentType.MMC5)[chipIndex].getVisVolume();
    }

    public int[][][] getN160VisVolume() {
        if (!dicInst.containsKey(InstrumentType.N160)) return null;
        return dicInst.get(InstrumentType.N160)[0].getVisVolume();
    }

    public int[][][] getN160VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.N160)) return null;
        return dicInst.get(InstrumentType.N160)[chipIndex].getVisVolume();
    }

    public int[][][] getVRC6VisVolume() {
        if (!dicInst.containsKey(InstrumentType.VRC6)) return null;
        return dicInst.get(InstrumentType.VRC6)[0].getVisVolume();
    }

    public int[][][] getVRC6VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.VRC6)) return null;
        return dicInst.get(InstrumentType.VRC6)[chipIndex].getVisVolume();
    }

    public int[][][] getVRC7VisVolume() {
        if (!dicInst.containsKey(InstrumentType.VRC7)) return null;
        return dicInst.get(InstrumentType.VRC7)[0].getVisVolume();
    }

    public int[][][] getVRC7VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.VRC7)) return null;
        return dicInst.get(InstrumentType.VRC7)[chipIndex].getVisVolume();
    }

    public int[][][] getFME7VisVolume() {
        if (!dicInst.containsKey(InstrumentType.FME7)) return null;
        return dicInst.get(InstrumentType.FME7)[0].getVisVolume();
    }

    public int[][][] getFME7VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.FME7)) return null;
        return dicInst.get(InstrumentType.FME7)[chipIndex].getVisVolume();
    }

    public int[][][] getYM3526VisVolume() {
        if (!dicInst.containsKey(InstrumentType.YM3526)) return null;
        return dicInst.get(InstrumentType.YM3526)[0].getVisVolume();
    }

    public int[][][] getYM3526VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.YM3526)) return null;
        return dicInst.get(InstrumentType.YM3526)[chipIndex].getVisVolume();
    }

    public int[][][] getY8950VisVolume() {
        if (!dicInst.containsKey(InstrumentType.Y8950)) return null;
        return dicInst.get(InstrumentType.Y8950)[0].getVisVolume();
    }

    public int[][][] getY8950VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.Y8950)) return null;
        return dicInst.get(InstrumentType.Y8950)[chipIndex].getVisVolume();
    }

    public int[][][] getYM3812VisVolume() {
        if (!dicInst.containsKey(InstrumentType.YM3812)) return null;
        return dicInst.get(InstrumentType.YM3812)[0].getVisVolume();
    }

    public int[][][] getYM3812VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.YM3812)) return null;
        return dicInst.get(InstrumentType.YM3812)[chipIndex].getVisVolume();
    }

    public int[][][] getYMF262VisVolume() {
        if (!dicInst.containsKey(InstrumentType.YMF262)) return null;
        return dicInst.get(InstrumentType.YMF262)[0].getVisVolume();
    }

    public int[][][] getYMF262VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.YMF262)) return null;
        return dicInst.get(InstrumentType.YMF262)[chipIndex].getVisVolume();
    }

    public int[][][] getYMF278BVisVolume() {
        if (!dicInst.containsKey(InstrumentType.YMF278B)) return null;
        return dicInst.get(InstrumentType.YMF278B)[0].getVisVolume();
    }

    public int[][][] getYMF278BVisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.YMF278B)) return null;
        return dicInst.get(InstrumentType.YMF278B)[chipIndex].getVisVolume();
    }

    public int[][][] getYMZ280BVisVolume() {
        if (!dicInst.containsKey(InstrumentType.YMZ280B)) return null;
        return dicInst.get(InstrumentType.YMZ280B)[0].getVisVolume();
    }

    public int[][][] getYMZ280BVisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.YMZ280B)) return null;
        return dicInst.get(InstrumentType.YMZ280B)[chipIndex].getVisVolume();
    }

    public int[][][] getYMF271VisVolume() {
        if (!dicInst.containsKey(InstrumentType.YMF271)) return null;
        return dicInst.get(InstrumentType.YMF271)[0].getVisVolume();
    }

    public int[][][] getYMF271VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.YMF271)) return null;
        return dicInst.get(InstrumentType.YMF271)[chipIndex].getVisVolume();
    }

    public int[][][] getRF5C68VisVolume() {
        if (!dicInst.containsKey(InstrumentType.RF5C68)) return null;
        return dicInst.get(InstrumentType.RF5C68)[0].getVisVolume();
    }

    public int[][][] getRF5C68VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.RF5C68)) return null;
        return dicInst.get(InstrumentType.RF5C68)[chipIndex].getVisVolume();
    }

    public int[][][] getMultiPCMVisVolume() {
        if (!dicInst.containsKey(InstrumentType.MultiPCM)) return null;
        return dicInst.get(InstrumentType.MultiPCM)[0].getVisVolume();
    }

    public int[][][] getMultiPCMVisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.MultiPCM)) return null;
        return dicInst.get(InstrumentType.MultiPCM)[chipIndex].getVisVolume();
    }

    public int[][][] getK053260VisVolume() {
        if (!dicInst.containsKey(InstrumentType.K053260)) return null;
        return dicInst.get(InstrumentType.K053260)[0].getVisVolume();
    }

    public int[][][] getK053260VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.K053260)) return null;
        return dicInst.get(InstrumentType.K053260)[chipIndex].getVisVolume();
    }

    public int[][][] getQSoundVisVolume() {
        if (!dicInst.containsKey(InstrumentType.QSound)) return null;
        return dicInst.get(InstrumentType.QSound)[0].getVisVolume();
    }

    public int[][][] getQSoundVisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.QSound)) return null;
        return dicInst.get(InstrumentType.QSound)[chipIndex].getVisVolume();
    }

    public int[][][] getQSoundCtrVisVolume() {
        if (!dicInst.containsKey(InstrumentType.QSoundCtr)) return null;
        return dicInst.get(InstrumentType.QSoundCtr)[0].getVisVolume();
    }

    public int[][][] getQSoundCtrVisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.QSoundCtr)) return null;
        return dicInst.get(InstrumentType.QSoundCtr)[chipIndex].getVisVolume();
    }

    public int[][][] getGA20VisVolume() {
        if (!dicInst.containsKey(InstrumentType.GA20)) return null;
        return dicInst.get(InstrumentType.GA20)[0].getVisVolume();
    }

    public int[][][] getGA20VisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.GA20)) return null;
        return dicInst.get(InstrumentType.GA20)[chipIndex].getVisVolume();
    }

    public int[][][] getDMGVisVolume() {
        if (!dicInst.containsKey(InstrumentType.DMG)) return null;
        return dicInst.get(InstrumentType.DMG)[0].getVisVolume();
    }

    public int[][][] getDMGVisVolume(int chipIndex) {
        if (!dicInst.containsKey(InstrumentType.DMG)) return null;
        return dicInst.get(InstrumentType.DMG)[chipIndex].getVisVolume();
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

