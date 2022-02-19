package MDSound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import MDSound.Common.QuadConsumer;
import MDSound.Common.QuadFunction;
import MDSound.Common.TriConsumer;
import MDSound.np.np_nes_fds;

    public class MDSound
    {

        private final int DefaultSamplingRate = 44100;
        private final int DefaultSamplingBuffer = 512;

        private int SamplingRate = DefaultSamplingRate;
        private int SamplingBuffer = DefaultSamplingBuffer;
        private int[][] StreamBufs = null;
        public dacControl dacControl = null;

        private Chip[] insts = null;
        private Map<enmInstrumentType, Instrument[]> dicInst = new HashMap<enmInstrumentType, Instrument[]>();

        private int[][] buffer = null;
        private int[][] buff = new int[][] { new int[1], new int[1] };

        private List<int[]> sn76489Mask = Arrays.asList(new int[][] { new int[] { 15, 15 } });// psgはmuteを基準にしているのでビットが逆です
        private List<int[]> ym2612Mask = Arrays.asList(new int[][] { new int[] { 0, 0 } });
        private List<int[]> ym2203Mask = Arrays.asList(new int[][] { new int[] { 0, 0 } });
        private List<int[]> segapcmMask = Arrays.asList(new int[][] { new int[] { 0, 0 } });
        private List<int[]> qsoundMask = Arrays.asList(new int[][] { new int[] { 0, 0 } });
        private List<int[]> qsoundCtrMask = Arrays.asList(new int[][] { new int[] { 0, 0 } });
        private List<int[]> okim6295Mask = Arrays.asList(new int[][] { new int[] { 0, 0 } });
        private List<int[]> c140Mask = Arrays.asList(new int[][] { new int[] { 0, 0 } });
        private List<int[]> ay8910Mask = Arrays.asList(new int[][] { new int[] { 0, 0 } });
        private List<int[]> huc6280Mask = Arrays.asList(new int[][] { new int[] { 0, 0 } });
        private List<int[]> nesMask = Arrays.asList(new int[][] { new int[] { 0, 0 } });
        private List<int[]> saa1099Mask = Arrays.asList(new int[][] { new int[] { 0, 0 } });
        private List<int[]> x1_010Mask = Arrays.asList(new int[][] { new int[] { 0, 0 } });
        private List<int[]> WSwanMask = Arrays.asList(new int[][] { new int[] { 0, 0 } });

        private int[][][] rf5c164Vol = new int[][][] {
            new int[][] { new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2] }
            ,new int[][] { new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2], new int[2] }
        };

        private int[][] ym2612Key = new int[][] { new int[6], new int[6] };
        private int[][] ym2151Key = new int[][] { new int[8], new int[8] };
        private int[][] ym2203Key = new int[][] { new int[6], new int[6] };
        private int[][] ym2608Key = new int[][] { new int[11], new int[11] };
        private int[][] ym2609Key = new int[][] { new int[12 + 12 + 3 + 1], new int[28] };
        private int[][] ym2610Key = new int[][] { new int[11], new int[11] };

        private boolean incFlag = false;
        private Object lockobj = new Object();
        private byte ResampleMode = 0;

        private final int FIXPNT_BITS = 11;
        private final int FIXPNT_FACT = (1 << (int)FIXPNT_BITS);
        private final int FIXPNT_MASK = (FIXPNT_FACT - 1);


        public static int np_nes_apu_volume;
        public static int np_nes_dmc_volume;
        public static int np_nes_fds_volume;
        public static int np_nes_fme7_volume;
        public static int np_nes_mmc5_volume;
        public static int np_nes_n106_volume;
        public static int np_nes_vrc6_volume;
        public static int np_nes_vrc7_volume;

        public visWaveBuffer visWaveBuffer = new visWaveBuffer();

// #if DEBUG
        long sw = System.currentTimeMillis();
// #endif
        
        private int getfriction(int x) { return ((x) & FIXPNT_MASK); }
        
        private int getnfriction(int x) { return ((FIXPNT_FACT - (x)) & FIXPNT_MASK); }
        
        private int fpi_floor(int x) { return (int)((x) & ~FIXPNT_MASK); }
        
        private int fpi_ceil(int x) { return (int)((x + FIXPNT_MASK) & ~FIXPNT_MASK); }
        
        private int fp2i_floor(int x) { return ((x) / FIXPNT_FACT); }
        
        private int fp2i_ceil(int x) { return ((x + FIXPNT_MASK) / FIXPNT_FACT); }


        public enum enmInstrumentType 
        {
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

        public static class Chip
        {
            public interface dlgUpdate extends TriConsumer<Byte, int[][], Integer>{};
            public interface dlgStart extends QuadFunction<Byte, Integer, Integer, Object[], Integer>{};
            public interface dlgStop extends Consumer<Byte>{};
            public interface dlgReset extends Consumer<Byte>{};
            public interface dlgAdditionalUpdate extends QuadConsumer<Chip, Byte, int[][], Integer>{};

            public Instrument Instrument = null;
            public dlgUpdate Update = null;
            public dlgStart Start = null;
            public dlgStop Stop = null;
            public dlgReset Reset = null;
            public dlgAdditionalUpdate AdditionalUpdate = null;

            public enmInstrumentType type = enmInstrumentType.None;
            public byte ID = 0;
            public int SamplingRate = 0;
            public int Clock = 0;
            public int Volume = 0;
            public int VisVolume = 0;

            public byte Resampler;
            public int SmpP;
            public int SmpLast;
            public int SmpNext;
            public int[] LSmpl;
            public int[] NSmpl;

            public Object[] Option = null;

            int tVolume;
            public int getTVolume() { return tVolume; }
            int VolumeBalance = 0x100;
            public int getVolumeBalance() { return VolumeBalance; };
            int tVolumeBalance;
            public int gettVolumeBalance() { return tVolumeBalance; }
        }

        public MDSound()
        {
            Init(DefaultSamplingRate, DefaultSamplingBuffer, null);
        }

        public MDSound(int SamplingRate, int SamplingBuffer, Chip[] insts)
        {
            Init(SamplingRate, SamplingBuffer, insts);
        }

        public void Init(int SamplingRate, int SamplingBuffer, Chip[] insts)
        {
            synchronized (lockobj)
            {
                this.SamplingRate = SamplingRate;
                this.SamplingBuffer = SamplingBuffer;
                this.insts = insts;

                buffer = new int[][] { new int[1], new int[1] };
                StreamBufs = new int[][] { new int[0x100], new int[0x100] };

                incFlag = false;

                if (insts == null) return;

                dicInst.clear();

                //ボリューム値から実際の倍数を求める
                int total = 0;
                double[] mul = new double[1];
                for (Chip inst : insts)
                {
                    if (inst.type == enmInstrumentType.Nes) inst.Volume = 0;
                    int balance = GetRegulationVoulme(inst, mul);
                    //16384 = 0x4000 = short.MAXValue + 1
                    total += (int)((((int)(16384.0 * Math.pow(10.0, 0 / 40.0)) * balance) >> 8) * mul[0]) / insts.length;
                }
                //総ボリューム値から最大ボリュームまでの倍数を求める
                //volumeMul = (double)(16384.0 / insts.length) / total;
                volumeMul = (double)16384.0 / total;
                //ボリューム値から実際の倍数を求める
                for (Chip inst : insts)
                {
                    if ((inst.VolumeBalance & 0x8000) != 0)
                        inst.tVolumeBalance =
                            (GetRegulationVoulme(inst, mul) * (inst.VolumeBalance & 0x7fff) + 0x80) >> 8;
                    else
                        inst.tVolumeBalance =
                            inst.VolumeBalance;
                    //int n = (((int)(16384.0 * Math.pow(10.0, inst.Volume / 40.0)) * inst.tVolumeBalance) >> 8) / insts.length;
                    int n = (((int)(16384.0 * Math.pow(10.0, inst.Volume / 40.0)) * inst.tVolumeBalance) >> 8) ;
                    inst.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
                }

                for (Chip inst : insts)
                {
                    inst.SamplingRate = inst.Start.apply(inst.ID, inst.SamplingRate, inst.Clock, inst.Option);
                    inst.Reset.accept(inst.ID);

                    if (dicInst.containsKey(inst.type))
                    {
                        List<Instrument> lst = Arrays.asList(dicInst.get(inst.type));
                        lst.add(inst.Instrument);
                        dicInst.put(inst.type, lst.toArray(new Instrument[lst.size()]));
                    }
                    else
                    {
                        dicInst.put(inst.type, new Instrument[] { inst.Instrument });
                    }

                    SetupResampler(inst);
                }

                dacControl = new dacControl(SamplingRate, this);

                sn76489Mask = new ArrayList<int[]>();
                if (dicInst.containsKey(enmInstrumentType.SN76489)) for (int i = 0; i < dicInst.get(enmInstrumentType.SN76489).length; i++) sn76489Mask.add(new int[] { 15, 15 });
                ym2203Mask = new ArrayList<int[]>();
                if (dicInst.containsKey(enmInstrumentType.YM2203)) for (int i = 0; i < dicInst.get(enmInstrumentType.YM2203).length; i++) ym2203Mask.add(new int[] { 0, 0 });
                ym2612Mask = new ArrayList<int[]>();
                if (dicInst.containsKey(enmInstrumentType.YM2612)) for (int i = 0; i < dicInst.get(enmInstrumentType.YM2612).length; i++) ym2612Mask.add(new int[] { 0, 0 });
                else ym2612Mask.add(new int[] { 0, 0 });
                segapcmMask = new ArrayList<int[]>();
                if (dicInst.containsKey(enmInstrumentType.SEGAPCM)) for (int i = 0; i < dicInst.get(enmInstrumentType.SEGAPCM).length; i++) segapcmMask.add(new int[] { 0, 0 });
                qsoundMask = new ArrayList<int[]>();
                if (dicInst.containsKey(enmInstrumentType.QSound)) for (int i = 0; i < dicInst.get(enmInstrumentType.QSound).length; i++) qsoundMask.add(new int[] { 0, 0 });
                qsoundCtrMask = new ArrayList<int[]>();
                if (dicInst.containsKey(enmInstrumentType.QSoundCtr)) for (int i = 0; i < dicInst.get(enmInstrumentType.QSoundCtr).length; i++) qsoundCtrMask.add(new int[] { 0, 0 });
                c140Mask = new ArrayList<int[]>();
                if (dicInst.containsKey(enmInstrumentType.C140)) for (int i = 0; i < dicInst.get(enmInstrumentType.C140).length; i++) c140Mask.add(new int[] { 0, 0 });
                ay8910Mask = new ArrayList<int[]>();
                if (dicInst.containsKey(enmInstrumentType.AY8910)) for (int i = 0; i < dicInst.get(enmInstrumentType.AY8910).length; i++) ay8910Mask.add(new int[] { 0, 0 });
            }
        }

        private int GetRegulationVoulme(Chip inst,double[] mul)
        {
            mul[0] = 1;
            int[] CHIP_VOLS = new int[]//CHIP_COUNT
            {
                0x80, 0x200/*0x155*/, 0x100, 0x100, 0x180, 0xB0, 0x100, 0x80,	// 00-07
		        0x80, 0x100, 0x100, 0x100, 0x100, 0x100, 0x100, 0x98,			// 08-0F
		        0x80, 0xE0/*0xCD*/, 0x100, 0xC0, 0x100, 0x40, 0x11E, 0x1C0,		// 10-17
		        0x100/*110*/, 0xA0, 0x100, 0x100, 0x100, 0xB3, 0x100, 0x100,	// 18-1F
		        0x20, 0x100, 0x100, 0x100, 0x40, 0x20, 0x100, 0x40,			// 20-27
		        0x280
            };

            if (inst.type == enmInstrumentType.YM2413)
            {
                mul[0] = 0.5;
                return CHIP_VOLS[0x01];
            }
            else if (inst.type == enmInstrumentType.YM2612)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x02];
            }
            else if (inst.type == enmInstrumentType.YM2151)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x03];
            }
            else if (inst.type == enmInstrumentType.SEGAPCM)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x04];
            }
            else if (inst.type == enmInstrumentType.RF5C68)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x05];
            }
            else if (inst.type == enmInstrumentType.YM2203)
            {
                mul[0] = 1;
                //mul=0.5 //SSG
                return CHIP_VOLS[0x06];
            }
            else if (inst.type == enmInstrumentType.YM2608)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x07];
            }
            else if (inst.type == enmInstrumentType.YM2610)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x08];
            }
            else if (inst.type == enmInstrumentType.YM3812)
            {
                mul[0] = 2;
                return CHIP_VOLS[0x09];
            }
            else if (inst.type == enmInstrumentType.YM3526)
            {
                mul[0] = 2;
                return CHIP_VOLS[0x0a];
            }
            else if (inst.type == enmInstrumentType.Y8950)
            {
                mul[0] = 2;
                return CHIP_VOLS[0x0b];
            }
            else if (inst.type == enmInstrumentType.YMF262)
            {
                mul[0] = 2;
                return CHIP_VOLS[0x0c];
            }
            else if (inst.type == enmInstrumentType.YMF278B)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x0d];
            }
            else if (inst.type == enmInstrumentType.YMF271)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x0e];
            }
            else if (inst.type == enmInstrumentType.YMZ280B)
            {
                mul[0] = 0x20 / 19.0;
                return CHIP_VOLS[0x0f];
            }
            else if (inst.type == enmInstrumentType.RF5C164)
            {
                mul[0] = 0x2;
                return CHIP_VOLS[0x10];
            }
            else if (inst.type == enmInstrumentType.PWM)
            {
                mul[0] = 0x1;
                return CHIP_VOLS[0x11];
            }
            else if (inst.type == enmInstrumentType.AY8910)
            {
                mul[0] = 0x2;
                return CHIP_VOLS[0x12];
            }
            else if (inst.type == enmInstrumentType.DMG)
            {
                mul[0] = 0x2;
                return CHIP_VOLS[0x13];
            }
            else if (inst.type == enmInstrumentType.Nes)
            {
                mul[0] = 0x2;
                return CHIP_VOLS[0x14];
            }
            else if (inst.type == enmInstrumentType.MultiPCM)
            {
                mul[0] = 4;
                return CHIP_VOLS[0x15];
            }
            //else if (inst.type == enmInstrumentType.UPD7759)
            //{
            //    mul[0] = 1;
            //    return CHIP_VOLS[0x16];
            //}
            else if (inst.type == enmInstrumentType.OKIM6258)
            {
                mul[0] = 2;
                return CHIP_VOLS[0x17];
            }
            else if (inst.type == enmInstrumentType.OKIM6295)
            {
                mul[0] = 2;
                return CHIP_VOLS[0x18];
            }
            else if (inst.type == enmInstrumentType.K051649)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x19];
            }
            else if (inst.type == enmInstrumentType.K054539)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x1a];
            }
            else if (inst.type == enmInstrumentType.HuC6280)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x1b];
            }
            else if (inst.type == enmInstrumentType.C140)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x1c];
            }
            else if (inst.type == enmInstrumentType.K053260)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x1d];
            }
            else if (inst.type == enmInstrumentType.POKEY)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x1e];
            }
            else if (inst.type == enmInstrumentType.QSound || inst.type == enmInstrumentType.QSoundCtr)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x1f];
            }
            //else if (inst.type == enmInstrumentType.SCSP)
            //{
            //    mul[0] = 8;
            //    return CHIP_VOLS[0x20];
            //}
            //else if (inst.type == enmInstrumentType.WSwan)
            //{
            //    mul[0] = 1;
            //    return CHIP_VOLS[0x21];
            //}
            //else if (inst.type == enmInstrumentType.VSU)
            //{
            //    mul[0] = 1;
            //    return CHIP_VOLS[0x22];
            //}
            else if (inst.type == enmInstrumentType.SAA1099)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x23];
            }
            //else if (inst.type == enmInstrumentType.ES5503)
            //{
            //    mul[0] = 8;
            //    return CHIP_VOLS[0x24];
            //}
            //else if (inst.type == enmInstrumentType.ES5506)
            //{
            //    mul[0] = 16;
            //    return CHIP_VOLS[0x25];
            //}
            else if (inst.type == enmInstrumentType.X1_010)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x26];
            }
            else if (inst.type == enmInstrumentType.C352)
            {
                mul[0] = 8;
                return CHIP_VOLS[0x27];
            }
            else if (inst.type == enmInstrumentType.GA20)
            {
                mul[0] = 1;
                return CHIP_VOLS[0x28];
            }

            mul[0] = 1;
            return 0x100;
        }

        public String GetDebugMsg()
        {
            return debugMsg;
        }

        private void SetupResampler(Chip chip)
        {
            if (chip.SamplingRate == 0)
            {
                chip.Resampler = (byte) 0xff;
                return;
            }

            if (chip.SamplingRate < SamplingRate)
            {
                chip.Resampler = 0x01;
            }
            else if (chip.SamplingRate == SamplingRate)
            {
                chip.Resampler = 0x02;
            }
            else if (chip.SamplingRate > SamplingRate)
            {
                chip.Resampler = 0x03;
            }
            if (chip.Resampler == 0x01 || chip.Resampler == 0x03)
            {
                if (ResampleMode == 0x02 || (ResampleMode == 0x01 && chip.Resampler == 0x03))
                    chip.Resampler = 0x00;
            }

            chip.SmpP = 0x00;
            chip.SmpLast = 0x00;
            chip.SmpNext = 0x00;
            chip.LSmpl = new int[2];
            chip.LSmpl[0] = 0x00;
            chip.LSmpl[1] = 0x00;
            chip.NSmpl = new int[2];
            if (chip.Resampler == 0x01)
            {
                // Pregenerate first Sample (the upsampler is always one too late)
                int[][] buf = new int[][] { new int[1], new int[1] };
                chip.Update.accept(chip.ID, buf, 1);
                chip.NSmpl[0] = buf[0x00][0x00];
                chip.NSmpl[1] = buf[0x01][0x00];
            }
            else
            {
                chip.NSmpl[0] = 0x00;
                chip.NSmpl[1] = 0x00;
            }

        }


        private int a, b, i;

        public int Update(short[] buf, int offset, int sampleCount, Runnable frame)
        {
            synchronized (lockobj)
            {
        
                for (i = 0; i < sampleCount && offset + i < buf.length; i += 2)
                {

                    frame.run();

                    dacControl.update();

                    a = 0;
                    b = 0;

                    buffer[0][0] = 0;
                    buffer[1][0] = 0;
                    ResampleChipStream(insts, buffer, 1);
                    //if (buffer[0][0] != 0) System.err.printf("{0}", buffer[0][0]);
                    a += buffer[0][0];
                    b += buffer[1][0];

                    if (incFlag)
                    {
                        a += buf[offset + i + 0];
                        b += buf[offset + i + 1];
                    }

                    Clip(a, b);

                    buf[offset + i + 0] = (short)a;
                    buf[offset + i + 1] = (short)b;
                    visWaveBuffer.Enq((short)a, (short)b);
                }

                return Math.min(i, sampleCount);

            }
        }


        //public int Update(short[] buf, int offset, int sampleCount, Runnable frame)
        //{
        //    synchronized (lockobj)
        //    {
        //        int a, b;

        //        for (int i = 0; i < sampleCount; i += 2)
        //        {

        //            frame.Invoke();

        //            a = 0;
        //            b = 0;

        //            buffer[0][0] = 0;
        //            buffer[1][0] = 0;
        //            ResampleChipStream(insts, buffer, 1);

        //            if (insts != null && insts.length > 0)
        //            {
        //                for (int j = 0; j < insts.length; j++)
        //                {
        //                    buff[0][0] = 0;
        //                    buff[1][0] = 0;

        //                    int mul = insts[j].Volume;
        //                    mul = (int)(16384.0 * Math.pow(10.0, mul / 40.0));

        //                    insts[j].Update.Invoke(insts[j].ID, buff, 1);

        //                    buffer[0][0] += (short)((Limit(buff[0][0], 0x7fff, -0x8000) * mul) >> 14);
        //                    buffer[1][0] += (short)((Limit(buff[1][0], 0x7fff, -0x8000) * mul) >> 14);
        //                }
        //            }

        //            a += buffer[0][0];
        //            b += buffer[1][0];

        //            if (incFlag)
        //            {
        //                a += buf[offset + i + 0];
        //                b += buf[offset + i + 1];
        //            }

        //            Clip(a, b);

        //            buf[offset + i + 0] = (short)a;
        //            buf[offset + i + 1] = (short)b;

        //        }

        //        return sampleCount;

        //    }
        //}

        
        private void Clip(int a, int b)
        {
            if ((int)(a + 32767) > (int)(32767 * 2))
            {
                if ((int)(a + 32767) >= (int)(32767 * 2))
                {
                    a = 32767;
                }
                else
                {
                    a = -32767;
                }
            }
            if ((int)(b + 32767) > (int)(32767 * 2))
            {
                if ((int)(b + 32767) >= (int)(32767 * 2))
                {
                    b = 32767;
                }
                else
                {
                    b = -32767;
                }
            }
        }

        
        public static int Limit(int v, int max, int min)
        {
            return v > max ? max : (v < min ? min : v);
        }

        private int[][] tempSample = new int[][] { new int[1], new int[1] };
        private int[][] StreamPnt = new int[][] { new int[0x100], new int[0x100] };
        private int ClearLength = 1;
        public static String debugMsg;
        private double volumeMul;

        private void ResampleChipStream(Chip[] insts, int[][] RetSample, int Length)
        {
            if (insts == null || insts.length < 1) return;
            if (Length > tempSample[0].length)
            {
                tempSample = new int[][] { new int[Length], new int[Length] };
            }
            if (Length > StreamPnt[0].length)
            {
                StreamPnt = new int[][] { new int[Length], new int[Length] };
            }

            Chip inst;
            int[] CurBufL;
            int[] CurBufR;
            //int[][] StreamPnt = new int[0x02][] { new int[0x100], new int[0x100] };
            int InBase;
            int InPos;
            int InPosNext;
            int OutPos;
            int SmpFrc;  // Sample Friction
            int InPre = 0;
            int InNow;
            int InPosL;
            long TempSmpL;
            long TempSmpR;
            int TempS32L;
            int TempS32R;
            int SmpCnt;   // must be signed, else I'm getting calculation errors
            int CurSmpl;
            long ChipSmpRate;

            //for (int i = 0; i < 0x100; i++)
            //{
            //    StreamBufs[0][i] = 0;
            //    StreamBufs[1][i] = 0;
            //}

            //Arrays.fill(StreamBufs[0], 0, 0x100);
            //Arrays.fill(StreamBufs[1], 0, 0x100);
            //CurBufL = StreamBufs[0x00];
            //CurBufR = StreamBufs[0x01];

            // This Do-While-Loop gets and resamples the chip output of one or more chips.
            // It's a loop to support the AY8910 paired with the YM2203/YM2608/YM2610.
            for (int i = 0; i < insts.length; i++)
            {
                Arrays.fill(StreamBufs[0], 0);
                Arrays.fill(StreamBufs[1], 0);
                CurBufL = StreamBufs[0x00];
                CurBufR = StreamBufs[0x01];

                inst = insts[i];
                //double volume = inst.Volume/100.0;
                int mul = inst.tVolume;
                //if (inst.type == enmInstrumentType.Nes) mul = 0;
                //mul = (int)(16384.0 * Math.pow(10.0, mul / 40.0));//16384 = 0x4000 = short.MAXValue + 1

                //if (i != 0 && insts[i].LSmpl[0] != 0) System.err.printf("{0} {1}", insts[i].LSmpl[0], insts[0].LSmpl == insts[i].LSmpl);
                //System.err.printf("{0} {1}", inst.type, inst.Resampler);
                //System.err.printf("{0}", inst.Resampler);
                switch (inst.Resampler)
                {
                    case 0x00:  // old, but very fast resampler
                        inst.SmpLast = inst.SmpNext;
                        inst.SmpP += Length;
                        inst.SmpNext = (int)((long)inst.SmpP * inst.SamplingRate / SamplingRate);
                        if (inst.SmpLast >= inst.SmpNext)
                        {
                            tempSample[0][0] = Limit((inst.LSmpl[0] * mul) >> 15, 0x7fff, -0x8000);
                            tempSample[1][0] = Limit((inst.LSmpl[1] * mul) >> 15, 0x7fff, -0x8000);

                            //RetSample[0][0] += (int)(inst.LSmpl[0] * volume);
                            //RetSample[1][0] += (int)(inst.LSmpl[1] * volume);
                        }
                        else
                        {
                            SmpCnt = (int)(inst.SmpNext - inst.SmpLast);
                            ClearLength = SmpCnt;
                            //inst.Update(inst.ID, StreamBufs, SmpCnt);
                            for (int ind = 0; ind < SmpCnt; ind++)
                            {
                                buff[0][0] = 0;
                                buff[1][0] = 0;
                                inst.Update.accept(inst.ID, buff, 1);

                                StreamBufs[0][ind] += Limit((buff[0][0] * mul) >> 15, 0x7fff, -0x8000);
                                StreamBufs[1][ind] += Limit((buff[1][0] * mul) >> 15, 0x7fff, -0x8000);
                                //StreamBufs[0][ind] += (short)((Limit(buff[0][0], 0x7fff, -0x8000) * mul) >> 14);
                                //StreamBufs[1][ind] += (short)((Limit(buff[1][0], 0x7fff, -0x8000) * mul) >> 14);

                                //StreamBufs[0][ind] += (int)(buff[0][0] * volume);
                                //StreamBufs[1][ind] += (int)(buff[1][0] * volume);
                            }

                            if (SmpCnt == 1)
                            {
                                tempSample[0][0] = Limit((CurBufL[0] * mul) >> 15, 0x7fff, -0x8000);
                                tempSample[1][0] = Limit((CurBufR[0] * mul) >> 15, 0x7fff, -0x8000);

                                //tempSample[0][0] = (short)((Limit(CurBufL[0x00], 0x7fff, -0x8000) * mul) >> 14);
                                //tempSample[1][0] = (short)((Limit(CurBufR[0x00], 0x7fff, -0x8000) * mul) >> 14);

                                //RetSample[0][0] += (int)(CurBufL[0x00] * volume);
                                //RetSample[1][0] += (int)(CurBufR[0x00] * volume);
                                inst.LSmpl[0] = CurBufL[0x00];
                                inst.LSmpl[1] = CurBufR[0x00];
                            }
                            else if (SmpCnt == 2)
                            {
                                tempSample[0][0] = Limit(((CurBufL[0] + CurBufL[1]) * mul) >> (15 + 1), 0x7fff, -0x8000);
                                tempSample[1][0] = Limit(((CurBufR[0] + CurBufR[1]) * mul) >> (15 + 1), 0x7fff, -0x8000);

                                //tempSample[0][0] = (short)(((Limit((CurBufL[0x00] + CurBufL[0x01]), 0x7fff, -0x8000) * mul) >> 14) >> 1);
                                //tempSample[1][0] = (short)(((Limit((CurBufR[0x00] + CurBufR[0x01]), 0x7fff, -0x8000) * mul) >> 14) >> 1);

                                //RetSample[0][0] += (int)((int)((CurBufL[0x00] + CurBufL[0x01]) * volume) >> 1);
                                //RetSample[1][0] += (int)((int)((CurBufR[0x00] + CurBufR[0x01]) * volume) >> 1);
                                inst.LSmpl[0] = CurBufL[0x01];
                                inst.LSmpl[1] = CurBufR[0x01];
                            }
                            else
                            {
                                TempS32L = CurBufL[0x00];
                                TempS32R = CurBufR[0x00];
                                for (CurSmpl = 0x01; CurSmpl < SmpCnt; CurSmpl++)
                                {
                                    TempS32L += CurBufL[CurSmpl];
                                    TempS32R += CurBufR[CurSmpl];
                                }
                                tempSample[0][0] = Limit(((TempS32L * mul) >> 15) / SmpCnt, 0x7fff, -0x8000);
                                tempSample[1][0] = Limit(((TempS32R * mul) >> 15) / SmpCnt, 0x7fff, -0x8000);
                                //tempSample[0][0] = (short)(((Limit(TempS32L, 0x7fff, -0x8000) * mul) >> 14) / SmpCnt);
                                //tempSample[1][0] = (short)(((Limit(TempS32R, 0x7fff, -0x8000) * mul) >> 14) / SmpCnt);

                                //RetSample[0][0] += (int)(TempS32L * volume / SmpCnt);
                                //RetSample[1][0] += (int)(TempS32R * volume / SmpCnt);
                                inst.LSmpl[0] = CurBufL[SmpCnt - 1];
                                inst.LSmpl[1] = CurBufR[SmpCnt - 1];
                            }
                        }
                        break;
                    case 0x01:  // Upsampling
                        ChipSmpRate = inst.SamplingRate;
                        InPosL = (int)(FIXPNT_FACT * inst.SmpP * ChipSmpRate / SamplingRate);
                        InPre = (int)fp2i_floor(InPosL);
                        InNow = (int)fp2i_ceil(InPosL);

                        //if (inst.type == enmInstrumentType.YM2612)
                        //{
                        //    System.System.err.printf("InPosL={0} , InPre={1} , InNow={2} , inst.SmpNext={3}", InPosL, InPre, InNow, inst.SmpNext);
                        //}

                        CurBufL[0x00] = inst.LSmpl[0];
                        CurBufR[0x00] = inst.LSmpl[1];
                        CurBufL[0x01] = inst.NSmpl[0];
                        CurBufR[0x01] = inst.NSmpl[1];
                        for (int ind = 0; ind < (int)(InNow - inst.SmpNext); ind++)
                        {
                            StreamPnt[0x00][ind] = CurBufL[0x02 + ind];
                            StreamPnt[0x01][ind] = CurBufR[0x02 + ind];
                        }
                        //inst.Update(inst.ID, StreamPnt, (int)(InNow - inst.SmpNext));
                        for (int ind = 0; ind < (int)(InNow - inst.SmpNext); ind++)
                        {
                            buff[0][0] = 0;
                            buff[1][0] = 0;
                            inst.Update.accept(inst.ID, buff, 1);

                            StreamPnt[0][0] = Limit((buff[0][0] * mul) >> 15, 0x7fff, -0x8000);
                            StreamPnt[1][0] = Limit((buff[1][0] * mul) >> 15, 0x7fff, -0x8000);
                            //StreamPnt[0][ind] += (short)((Limit(buff[0][0], 0x7fff, -0x8000) * mul) >> 14);
                            //StreamPnt[1][ind] += (short)((Limit(buff[1][0], 0x7fff, -0x8000) * mul) >> 14);
                            //StreamPnt[0][ind] += (int)(buff[0][0] * volume);
                            //StreamPnt[1][ind] += (int)(buff[1][0] * volume);
                        }
                        for (int ind = 0; ind < (int)(InNow - inst.SmpNext); ind++)
                        {
                            CurBufL[0x02 + ind] = StreamPnt[0x00][ind];
                            CurBufR[0x02 + ind] = StreamPnt[0x01][ind];
                        }

                        InBase = FIXPNT_FACT + (int)(InPosL - (int)inst.SmpNext * FIXPNT_FACT);
                        SmpCnt = (int)FIXPNT_FACT;
                        inst.SmpLast = InPre;
                        inst.SmpNext = InNow;
                        for (OutPos = 0x00; OutPos < Length; OutPos++)
                        {
                            InPos = InBase + (int)(FIXPNT_FACT * OutPos * ChipSmpRate / SamplingRate);

                            InPre = fp2i_floor(InPos);
                            InNow = fp2i_ceil(InPos);
                            SmpFrc = getfriction(InPos);

                            // Linear interpolation
                            TempSmpL = ((long)CurBufL[InPre] * (FIXPNT_FACT - SmpFrc)) +
                                        ((long)CurBufL[InNow] * SmpFrc);
                            TempSmpR = ((long)CurBufR[InPre] * (FIXPNT_FACT - SmpFrc)) +
                                        ((long)CurBufR[InNow] * SmpFrc);
                            //RetSample[0][OutPos] += (int)(TempSmpL * volume / SmpCnt);
                            //RetSample[1][OutPos] += (int)(TempSmpR * volume / SmpCnt);
                            tempSample[0][OutPos] = (int)(TempSmpL / SmpCnt);
                            tempSample[1][OutPos] = (int)(TempSmpR / SmpCnt);
                        }
                        inst.LSmpl[0] = CurBufL[InPre];
                        inst.LSmpl[1] = CurBufR[InPre];
                        inst.NSmpl[0] = CurBufL[InNow];
                        inst.NSmpl[1] = CurBufR[InNow];
                        inst.SmpP += Length;
                        break;
                    case 0x02:  // Copying
                        inst.SmpNext = inst.SmpP * inst.SamplingRate / SamplingRate;
                        //inst.Update(inst.ID, StreamBufs, (int)Length);
                        ClearLength = (int)Length;
                        for (int ind = 0; ind < Length; ind++)
                        {
                            buff[0][0] = 0;
                            buff[1][0] = 0;
                            inst.Update.accept(inst.ID, buff, 1);

                            StreamBufs[0][ind] = Limit((buff[0][0] * mul) >> 15, 0x7fff, -0x8000);
                            StreamBufs[1][ind] = Limit((buff[1][0] * mul) >> 15, 0x7fff, -0x8000);
                            //StreamBufs[0][ind] = (short)((Limit(buff[0][0], 0x7fff, -0x8000) * mul) >> 14);
                            //StreamBufs[1][ind] = (short)((Limit(buff[1][0], 0x7fff, -0x8000) * mul) >> 14);

                            //StreamBufs[0][ind] = (int)(buff[0][0] * volume);
                            //StreamBufs[1][ind] = (int)(buff[1][0] * volume);
                        }
                        for (OutPos = 0x00; OutPos < Length; OutPos++)
                        {
                            tempSample[0][OutPos] = (int)(CurBufL[OutPos]);
                            tempSample[1][OutPos] = (int)(CurBufR[OutPos]);
                        }
                        inst.SmpP += Length;
                        inst.SmpLast = inst.SmpNext;
                        break;
                    case 0x03:  // Downsampling
                        ChipSmpRate = inst.SamplingRate;
                        InPosL = (int)(FIXPNT_FACT * (inst.SmpP + Length) * ChipSmpRate / SamplingRate);
                        inst.SmpNext = (int)fp2i_ceil(InPosL);

                        CurBufL[0x00] = inst.LSmpl[0];
                        CurBufR[0x00] = inst.LSmpl[1];

                        for (int ind = 0; ind < (int)(inst.SmpNext - inst.SmpLast); ind++)
                        {
                            StreamPnt[0x00][ind] = CurBufL[0x01 + ind];
                            StreamPnt[0x01][ind] = CurBufR[0x01 + ind];
                        }
                        //inst.Update(inst.ID, StreamPnt, (int)(inst.SmpNext - inst.SmpLast));
                        for (int ind = 0; ind < (int)(inst.SmpNext - inst.SmpLast); ind++)
                        {
                            buff[0][0] = 0;
                            buff[1][0] = 0;
                            inst.Update.accept(inst.ID, buff, 1);
                            //System.err.printf("{0} : {1}", i, buff[0][0]);

                            StreamPnt[0][ind] = Limit((buff[0][0] * mul) >> 15, 0x7fff, -0x8000);
                            StreamPnt[1][ind] = Limit((buff[1][0] * mul) >> 15, 0x7fff, -0x8000);
                            //StreamPnt[0][ind] += (short)((Limit(buff[0][0], 0x7fff, -0x8000) * mul) >> 14);
                            //StreamPnt[1][ind] += (short)((Limit(buff[1][0], 0x7fff, -0x8000) * mul) >> 14);
                            //StreamPnt[0][ind] += (int)(buff[0][0] * volume);
                            //StreamPnt[1][ind] += (int)(buff[1][0] * volume);
                        }
                        for (int ind = 0; ind < (int)(inst.SmpNext - inst.SmpLast); ind++)
                        {
                            CurBufL[0x01 + ind] = StreamPnt[0x00][ind];
                            CurBufR[0x01 + ind] = StreamPnt[0x01][ind];
                        }

                        InPosL = (int)(FIXPNT_FACT * inst.SmpP * ChipSmpRate / SamplingRate);
                        // I'm adding 1.0 to avoid negative indexes
                        InBase = FIXPNT_FACT + (int)(InPosL - (int)inst.SmpLast * FIXPNT_FACT);
                        InPosNext = InBase;
                        for (OutPos = 0x00; OutPos < Length; OutPos++)
                        {
                            //InPos = InBase + ((int)32)(FIXPNT_FACT * OutPos * ChipSmpRate / SampleRate);
                            InPos = InPosNext;
                            InPosNext = InBase + (int)(FIXPNT_FACT * (OutPos + 1) * ChipSmpRate / SamplingRate);

                            // first frictional Sample
                            SmpFrc = getnfriction(InPos);
                            if (SmpFrc != 0)
                            {
                                InPre = fp2i_floor(InPos);
                                TempSmpL = (long)CurBufL[InPre] * SmpFrc;
                                TempSmpR = (long)CurBufR[InPre] * SmpFrc;
                            }
                            else
                            {
                                TempSmpL = TempSmpR = 0x00;
                            }
                            SmpCnt = (int)SmpFrc;

                            // last frictional Sample
                            SmpFrc = getfriction(InPosNext);
                            InPre = fp2i_floor(InPosNext);
                            if (SmpFrc != 0)
                            {
                                TempSmpL += (long)CurBufL[InPre] * SmpFrc;
                                TempSmpR += (long)CurBufR[InPre] * SmpFrc;
                                SmpCnt += (int)SmpFrc;
                            }

                            // whole Samples in between
                            //InPre = fp2i_floor(InPosNext);
                            InNow = fp2i_ceil(InPos);
                            SmpCnt += (int)((InPre - InNow) * FIXPNT_FACT);    // this is faster
                            while (InNow < InPre)
                            {
                                TempSmpL += (long)CurBufL[InNow] * FIXPNT_FACT;
                                TempSmpR += (long)CurBufR[InNow] * FIXPNT_FACT;
                                //SmpCnt ++;
                                InNow++;
                            }

                            //RetSample[0][OutPos] += (int)(TempSmpL * volume / SmpCnt);
                            //RetSample[1][OutPos] += (int)(TempSmpR * volume / SmpCnt);
                            tempSample[0][OutPos] = (int)(TempSmpL / SmpCnt);
                            tempSample[1][OutPos] = (int)(TempSmpR / SmpCnt);
                        }

                        inst.LSmpl[0] = CurBufL[InPre];
                        inst.LSmpl[1] = CurBufR[InPre];
                        inst.SmpP += Length;
                        inst.SmpLast = inst.SmpNext;
                        break;
                    default:
                        inst.SmpP += SamplingRate;
                        break;  // do absolutely nothing
                }

                if (inst.SmpLast >= inst.SamplingRate)
                {
                    inst.SmpLast -= inst.SamplingRate;
                    inst.SmpNext -= inst.SamplingRate;
                    inst.SmpP -= SamplingRate;
                }

                inst.AdditionalUpdate.accept(inst, inst.ID, tempSample, (int)Length);
                for (int j = 0; j < Length; j++)
                {
                    RetSample[0][j] += tempSample[0][j];
                    RetSample[1][j] += tempSample[1][j];
                }

                //if (tempSample[0][0] != 0) System.err.printf("{0} {1} {2}", i, tempSample[0][0], inst.Resampler);

            }

            return;
        }

        private int GetChipVolume(VGMX_CHP_EXTRA16 TempCX, byte ChipID, byte ChipNum, byte ChipCnt, int SN76496VGMHeaderClock, String strSystemNameE, boolean DoubleSSGVol)
        {
            // ChipID: ID of Chip
            //		Bit 7 - Is Paired Chip
            // ChipNum: chip number (0 - first chip, 1 - second chip)
            // ChipCnt: chip volume divider (number of used chips)
            int[] CHIP_VOLS = new int[]//CHIP_COUNT
            {
                0x80, 0x200/*0x155*/, 0x100, 0x100, 0x180, 0xB0, 0x100, 0x80,	// 00-07
		        0x80, 0x100, 0x100, 0x100, 0x100, 0x100, 0x100, 0x98,			// 08-0F
		        0x80, 0xE0/*0xCD*/, 0x100, 0xC0, 0x100, 0x40, 0x11E, 0x1C0,		// 10-17
		        0x100/*110*/, 0xA0, 0x100, 0x100, 0x100, 0xB3, 0x100, 0x100,	// 18-1F
		        0x20, 0x100, 0x100, 0x100, 0x40, 0x20, 0x100, 0x40,			// 20-27
		        0x280
            };
            int Volume;
            byte CurChp;
            //VGMX_CHP_EXTRA16 TempCX;
            VGMX_CHIP_DATA16 TempCD;

            Volume = CHIP_VOLS[ChipID & 0x7F];
            switch (ChipID & 0xff)
            {
                case 0x00:  // SN76496
                            // if T6W28, set Volume Divider to 01
                    if ((SN76496VGMHeaderClock & 0x80000000) != 0)
                    {
                        // The T6W28 consists of 2 "half" chips.
                        ChipNum = 0x01;
                        ChipCnt = 0x01;
                    }
                    break;
                case 0x18:  // OKIM6295
                            // CP System 1 patch
                    if ((strSystemNameE != null || !strSystemNameE.isEmpty()) && strSystemNameE.indexOf("CP") == 0)
                        Volume = 110;
                    break;
                case 0x86:  // YM2203's AY
                    Volume /= 2;
                    break;
                case 0x87:  // YM2608's AY
                            // The YM2608 outputs twice as loud as the YM2203 here.
                            //Volume *= 1;
                    break;
                case 0x88:  // YM2610's AY
                            //Volume *= 1;
                    break;
            }
            if (ChipCnt > 1)
                Volume /= ChipCnt;

            //TempCX = VGMH_Extra.Volumes;
            for (CurChp = 0x00; CurChp < TempCX.ChipCnt; CurChp++)
            {
                TempCD = TempCX.CCData[CurChp];
                if (TempCD.Type == ChipID && (TempCD.Flags & 0x01) == ChipNum)
                {
                    // Bit 15 - absolute/relative volume
                    //	0 - absolute
                    //	1 - relative (0x0100 = 1.0, 0x80 = 0.5, etc.)
                    if ((TempCD.Data & 0x8000) != 0)
                        Volume = (int)((Volume * (TempCD.Data & 0x7FFF) + 0x80) >> 8);
                    else
                    {
                        Volume = TempCD.Data;
                        if ((ChipID & 0x80) != 0 && DoubleSSGVol)
                            Volume *= 2;
                    }
                    break;
                }
            }

            return Volume;
        }

        public class VGMX_CHIP_DATA16
        {
            public byte Type;
            public byte Flags;
            public int Data;
        }

        public class VGMX_CHP_EXTRA16
        {
            public byte ChipCnt;
            public VGMX_CHIP_DATA16[] CCData;
        }




        // #region AY8910

        public void WriteAY8910(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.AY8910)) return;

                ((ay8910)(dicInst.get(enmInstrumentType.AY8910)[0])).Write(ChipID, 0, Adr, Data);
                //((ay8910_mame)(dicInst.get(enmInstrumentType.AY8910)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteAY8910(int ChipIndex, byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.AY8910)) return;

                ((ay8910)(dicInst.get(enmInstrumentType.AY8910)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void setVolumeAY8910(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.AY8910)) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.AY8910) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void setAY8910Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ay8910Mask.get(0)[chipID] |= ch;
                if (!dicInst.containsKey(enmInstrumentType.AY8910)) return;
                ((ay8910)(dicInst.get(enmInstrumentType.AY8910)[0])).AY8910_SetMute((byte)chipID, ay8910Mask.get(0)[chipID]);
            }
        }

        public void setAY8910Mask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ay8910Mask.get(ChipIndex)[chipID] |= ch;
                if (!dicInst.containsKey(enmInstrumentType.AY8910)) return;
                ((ay8910)(dicInst.get(enmInstrumentType.AY8910)[ChipIndex])).AY8910_SetMute((byte)chipID, ay8910Mask.get(ChipIndex)[chipID]);
            }
        }

        public void resetAY8910Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ay8910Mask.get(0)[chipID] &= ~ch;
                if (!dicInst.containsKey(enmInstrumentType.AY8910)) return;
                ((ay8910)(dicInst.get(enmInstrumentType.AY8910)[0])).AY8910_SetMute((byte)chipID, ay8910Mask.get(0)[chipID]);
            }
        }

        public void resetAY8910Mask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ay8910Mask.get(ChipIndex)[chipID] &= ~ch;
                if (!dicInst.containsKey(enmInstrumentType.AY8910)) return;
                ((ay8910)(dicInst.get(enmInstrumentType.AY8910)[ChipIndex])).AY8910_SetMute((byte)chipID, ay8910Mask.get(ChipIndex)[chipID]);
            }
        }

        public int[][][] getAY8910VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.AY8910)) return null;
            return ((ay8910)dicInst.get(enmInstrumentType.AY8910)[0]).visVolume;
        }

        public int[][][] getAY8910VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.AY8910)) return null;
            return ((ay8910)dicInst.get(enmInstrumentType.AY8910)[ChipIndex]).visVolume;
        }

        // #endregion


        // #region AY8910mame

        public void WriteAY8910mame(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.AY8910mame)) return;
                if (dicInst.get(enmInstrumentType.AY8910mame)[0] == null) return;

                ((ay8910_mame)(dicInst.get(enmInstrumentType.AY8910mame)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteAY8910mame(int ChipIndex, byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.AY8910mame)) return;
                if (dicInst.get(enmInstrumentType.AY8910mame)[ChipIndex] == null) return;

                ((ay8910_mame)(dicInst.get(enmInstrumentType.AY8910mame)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void setVolumeAY8910mame(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.AY8910mame)) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.AY8910mame) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void setAY8910mameMask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ay8910Mask.get(0)[chipID] |= ch;
                if (!dicInst.containsKey(enmInstrumentType.AY8910mame)) return;
                if (dicInst.get(enmInstrumentType.AY8910mame)[0] == null) return;
                ((ay8910_mame)(dicInst.get(enmInstrumentType.AY8910mame)[0])).SetMute((byte)chipID, ay8910Mask.get(0)[chipID]);
            }
        }

        public void setAY8910mameMask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ay8910Mask.get(ChipIndex)[chipID] |= ch;
                if (!dicInst.containsKey(enmInstrumentType.AY8910mame)) return;
                if (dicInst.get(enmInstrumentType.AY8910mame)[ChipIndex] == null) return;

                ((ay8910_mame)(dicInst.get(enmInstrumentType.AY8910mame)[ChipIndex])).SetMute((byte)chipID, ay8910Mask.get(ChipIndex)[chipID]);
            }
        }

        public void resetAY8910mameMask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ay8910Mask.get(0)[chipID] &= ~ch;
                if (!dicInst.containsKey(enmInstrumentType.AY8910mame)) return;
                if (dicInst.get(enmInstrumentType.AY8910mame)[0] == null) return;

                ((ay8910_mame)(dicInst.get(enmInstrumentType.AY8910mame)[0])).SetMute((byte)chipID, ay8910Mask.get(0)[chipID]);
            }
        }

        public void resetAY8910mameMask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ay8910Mask.get(ChipIndex)[chipID] &= ~ch;
                if (!dicInst.containsKey(enmInstrumentType.AY8910mame)) return;
                if (dicInst.get(enmInstrumentType.AY8910mame)[ChipIndex] == null) return;

                ((ay8910_mame)(dicInst.get(enmInstrumentType.AY8910mame)[ChipIndex])).SetMute((byte)chipID, ay8910Mask.get(ChipIndex)[chipID]);
            }
        }

        public int[][][] getAY8910mameVisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.AY8910mame)) return null;
            if (dicInst.get(enmInstrumentType.AY8910mame)[0] == null) return null;
            return ((ay8910_mame)dicInst.get(enmInstrumentType.AY8910mame)[0]).visVolume;
        }

        public int[][][] getAY8910mameVisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.AY8910mame)) return null;
            if (dicInst.get(enmInstrumentType.AY8910mame)[ChipIndex] == null) return null;

            return ((ay8910_mame)dicInst.get(enmInstrumentType.AY8910mame)[ChipIndex]).visVolume;
        }

        // #endregion

        // #region WSwan

        public void WriteWSwan(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.WSwan)) return;
                if (dicInst.get(enmInstrumentType.WSwan)[0] == null) return;

                ((ws_audio)(dicInst.get(enmInstrumentType.WSwan)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteWSwan(int ChipIndex, byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.WSwan)) return;
                if (dicInst.get(enmInstrumentType.WSwan)[ChipIndex] == null) return;

                ((ws_audio)(dicInst.get(enmInstrumentType.WSwan)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteWSwanMem(byte ChipID, int Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.WSwan)) return;
                if (dicInst.get(enmInstrumentType.WSwan)[0] == null) return;

                ((ws_audio)(dicInst.get(enmInstrumentType.WSwan)[0])).WriteMem(ChipID, Adr, Data);
            }
        }

        public void WriteWSwanMem(int ChipIndex, byte ChipID, int Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.WSwan)) return;
                if (dicInst.get(enmInstrumentType.WSwan)[0] == null) return;

                ((ws_audio)(dicInst.get(enmInstrumentType.WSwan)[ChipIndex])).WriteMem(ChipID, Adr, Data);
            }
        }

        public void setVolumeWSwan(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.WSwan)) return;
            if (dicInst.get(enmInstrumentType.WSwan)[0] == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.WSwan) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void setWSwanMask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ay8910Mask.get(0)[chipID] |= ch;
                if (!dicInst.containsKey(enmInstrumentType.WSwan)) return;
                if (dicInst.get(enmInstrumentType.WSwan)[0] == null) return;
                ((ws_audio)(dicInst.get(enmInstrumentType.WSwan)[0])).SetMute((byte)chipID, WSwanMask.get(0)[chipID]);
            }
        }

        public void setWSwanMask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                WSwanMask.get(ChipIndex)[chipID] |= ch;
                if (!dicInst.containsKey(enmInstrumentType.WSwan)) return;
                if (dicInst.get(enmInstrumentType.WSwan)[ChipIndex] == null) return;
                ((ws_audio)(dicInst.get(enmInstrumentType.WSwan)[ChipIndex])).SetMute((byte)chipID, WSwanMask.get(ChipIndex)[chipID]);
            }
        }

        public void resetWSwanMask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                WSwanMask.get(0)[chipID] &= ~ch;
                if (!dicInst.containsKey(enmInstrumentType.WSwan)) return;
                if (dicInst.get(enmInstrumentType.WSwan)[0] == null) return;
                ((ws_audio)(dicInst.get(enmInstrumentType.WSwan)[0])).SetMute((byte)chipID, WSwanMask.get(0)[chipID]);
            }
        }

        public void resetWSwanMask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                WSwanMask.get(ChipIndex)[chipID] &= ~ch;
                if (!dicInst.containsKey(enmInstrumentType.WSwan)) return;
                if (dicInst.get(enmInstrumentType.WSwan)[ChipIndex] == null) return;
                ((ws_audio)(dicInst.get(enmInstrumentType.WSwan)[ChipIndex])).SetMute((byte)chipID, WSwanMask.get(ChipIndex)[chipID]);
            }
        }

        public int[][][] getWSwanVisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.WSwan)) return null;
            if (dicInst.get(enmInstrumentType.WSwan)[0] == null) return null;
            return ((ws_audio)dicInst.get(enmInstrumentType.WSwan)[0]).visVolume;
        }

        public int[][][] getWSwanVisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.WSwan)) return null;
            if (dicInst.get(enmInstrumentType.WSwan)[ChipIndex] == null) return null;
            return ((ws_audio)dicInst.get(enmInstrumentType.WSwan)[ChipIndex]).visVolume;
        }

        // #endregion


        // #region SAA1099

        public void WriteSAA1099(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SAA1099)) return;

                ((saa1099)(dicInst.get(enmInstrumentType.SAA1099)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteSAA1099(int ChipIndex, byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SAA1099)) return;

                ((saa1099)(dicInst.get(enmInstrumentType.SAA1099)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }


        public void setVolumeSAA1099(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.SAA1099)) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.SAA1099) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void setSAA1099Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                saa1099Mask.get(0)[chipID] |= ch;
                if (!dicInst.containsKey(enmInstrumentType.SAA1099)) return;
                ((saa1099)(dicInst.get(enmInstrumentType.SAA1099)[0])).SAA1099_SetMute((byte)chipID, saa1099Mask.get(0)[chipID]);
            }
        }

        public void setSAA1099Mask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                saa1099Mask.get(ChipIndex)[chipID] |= ch;
                if (!dicInst.containsKey(enmInstrumentType.SAA1099)) return;
                ((saa1099)(dicInst.get(enmInstrumentType.SAA1099)[ChipIndex])).SAA1099_SetMute((byte)chipID, saa1099Mask.get(ChipIndex)[chipID]);
            }
        }

        public void resetSAA1099Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                saa1099Mask.get(0)[chipID] &= ~ch;
                if (!dicInst.containsKey(enmInstrumentType.SAA1099)) return;
                ((saa1099)(dicInst.get(enmInstrumentType.SAA1099)[0])).SAA1099_SetMute((byte)chipID, saa1099Mask.get(0)[chipID]);
            }
        }

        public void resetSAA1099Mask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                saa1099Mask.get(ChipIndex)[chipID] &= ~ch;
                if (!dicInst.containsKey(enmInstrumentType.SAA1099)) return;
                ((saa1099)(dicInst.get(enmInstrumentType.SAA1099)[ChipIndex])).SAA1099_SetMute((byte)chipID, saa1099Mask.get(ChipIndex)[chipID]);
            }
        }

        public int[][][] getSAA1099VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.SAA1099)) return null;
            return ((saa1099)dicInst.get(enmInstrumentType.SAA1099)[0]).visVolume;
        }

        public int[][][] getSAA1099VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.SAA1099)) return null;
            return ((saa1099)dicInst.get(enmInstrumentType.SAA1099)[ChipIndex]).visVolume;
        }

        // #endregion

        // #region POKEY

        public void WritePOKEY(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.POKEY)) return;

                ((pokey)(dicInst.get(enmInstrumentType.POKEY)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WritePOKEY(int ChipIndex, byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.POKEY)) return;

                ((pokey)(dicInst.get(enmInstrumentType.POKEY)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        // #endregion



        // #region X1_010

        public void WriteX1_010(byte ChipID, int Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.X1_010)) return;

                ((x1_010)(dicInst.get(enmInstrumentType.X1_010)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteX1_010(int ChipIndex, byte ChipID, int Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.X1_010)) return;

                ((x1_010)(dicInst.get(enmInstrumentType.X1_010)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteX1_010PCMData(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.X1_010)) return;

                //((qsound)(dicInst.get(enmInstrumentType.QSound)[0])).qsound_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
                ((x1_010)(dicInst.get(enmInstrumentType.X1_010)[0])).x1_010_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        public void WriteX1_010PCMData(int ChipIndex, byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.X1_010)) return;

                //((qsound)(dicInst.get(enmInstrumentType.QSound)[ChipIndex])).qsound_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
                ((x1_010)(dicInst.get(enmInstrumentType.X1_010)[ChipIndex])).x1_010_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        public void setVolumeX1_010(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.X1_010)) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.X1_010) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void setX1_010Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                x1_010Mask.get(0)[chipID] |= ch;
                if (!dicInst.containsKey(enmInstrumentType.X1_010)) return;
                ((x1_010)(dicInst.get(enmInstrumentType.X1_010)[0])).x1_010_set_mute_mask((byte)chipID, (int)x1_010Mask.get(0)[chipID]);
            }
        }

        public void setX1_010Mask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                x1_010Mask.get(ChipIndex)[chipID] |= ch;
                if (!dicInst.containsKey(enmInstrumentType.X1_010)) return;
                ((x1_010)(dicInst.get(enmInstrumentType.X1_010)[ChipIndex])).x1_010_set_mute_mask((byte)chipID, (int)x1_010Mask.get(ChipIndex)[chipID]);
            }
        }

        public void resetX1_010Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                x1_010Mask.get(0)[chipID] &= ~ch;
                if (!dicInst.containsKey(enmInstrumentType.X1_010)) return;
                ((x1_010)(dicInst.get(enmInstrumentType.X1_010)[0])).x1_010_set_mute_mask((byte)chipID, (int)x1_010Mask.get(0)[chipID]);
            }
        }

        public void resetX1_010Mask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                x1_010Mask.get(ChipIndex)[chipID] &= ~ch;
                if (!dicInst.containsKey(enmInstrumentType.X1_010)) return;
                ((x1_010)(dicInst.get(enmInstrumentType.X1_010)[ChipIndex])).x1_010_set_mute_mask((byte)chipID, (int)x1_010Mask.get(ChipIndex)[chipID]);
            }
        }

        public int[][][] getX1_010VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.X1_010)) return null;
            return ((x1_010)dicInst.get(enmInstrumentType.X1_010)[0]).visVolume;
        }

        public int[][][] getX1_010VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.X1_010)) return null;
            return ((x1_010)dicInst.get(enmInstrumentType.X1_010)[ChipIndex]).visVolume;
        }

        // #endregion


        // #region SN76489

        public void WriteSN76489(byte ChipID, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SN76489)) return;

                dicInst.get(enmInstrumentType.SN76489)[0].Write(ChipID, 0, 0, Data);
            }
        }

        public void WriteSN76489(int ChipIndex,byte ChipID, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SN76489)) return;

                dicInst.get(enmInstrumentType.SN76489)[ChipIndex].Write(ChipID, 0, 0, Data);
            }
        }

        public void WriteSN76489GGPanning(byte ChipID, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SN76489)) return;

                ((sn76489)(dicInst.get(enmInstrumentType.SN76489)[0])).SN76489_GGStereoWrite(ChipID, Data);
            }
        }

        public void WriteSN76489GGPanning(int ChipIndex, byte ChipID, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SN76489)) return;

                ((sn76489)(dicInst.get(enmInstrumentType.SN76489)[ChipIndex])).SN76489_GGStereoWrite(ChipID, Data);
            }
        }

        // #endregion


        // #region SN76496

        public void WriteSN76496(byte ChipID, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SN76496)) return;

                dicInst.get(enmInstrumentType.SN76496)[0].Write(ChipID, 0, 0, Data);
            }
        }

        public void WriteSN76496(int ChipIndex, byte ChipID, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SN76496)) return;

                dicInst.get(enmInstrumentType.SN76496)[ChipIndex].Write(ChipID, 0, 0, Data);
            }
        }

        public void WriteSN76496GGPanning(byte ChipID, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SN76496)) return;

                ((SN76496)(dicInst.get(enmInstrumentType.SN76496)[0])).SN76496_GGStereoWrite(ChipID, 0, 0, Data);
            }
        }

        public void WriteSN76496GGPanning(int ChipIndex, byte ChipID, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SN76496)) return;

                ((SN76496)(dicInst.get(enmInstrumentType.SN76496)[ChipIndex])).SN76496_GGStereoWrite(ChipID, 0, 0, Data);
            }
        }

        // #endregion


        // #region YM2612

        public void WriteYM2612(byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2612)) return;

                dicInst.get(enmInstrumentType.YM2612)[0].Write(ChipID, 0, (byte)(0 + (Port & 1) * 2), Adr);
                dicInst.get(enmInstrumentType.YM2612)[0].Write(ChipID, 0, (byte)(1 + (Port & 1) * 2), Data);
            }
        }

        public void WriteYM2612(int ChipIndex, byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2612)) return;

                dicInst.get(enmInstrumentType.YM2612)[ChipIndex].Write(ChipID, 0, (byte)(0 + (Port & 1) * 2), Adr);
                dicInst.get(enmInstrumentType.YM2612)[ChipIndex].Write(ChipID, 0, (byte)(1 + (Port & 1) * 2), Data);
            }
        }

        public void PlayPCM_YM2612X(int ChipIndex, byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2612)) return;
                if (!(dicInst.get(enmInstrumentType.YM2612)[ChipIndex] instanceof YM2612X)) return;
                ((YM2612X)dicInst.get(enmInstrumentType.YM2612)[ChipIndex]).XGMfunction.PlayPCM(ChipID, Adr, Data);
            }
        }

        // #endregion


        // #region YM3438

        public void WriteYM3438(byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM3438)) return;

                dicInst.get(enmInstrumentType.YM3438)[0].Write(ChipID, 0, (byte)(0 + (Port & 1) * 2), Adr);
                dicInst.get(enmInstrumentType.YM3438)[0].Write(ChipID, 0, (byte)(1 + (Port & 1) * 2), Data);
            }
        }

        public void WriteYM3438(int ChipIndex,byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM3438)) return;

                dicInst.get(enmInstrumentType.YM3438)[ChipIndex].Write(ChipID, 0, (byte)(0 + (Port & 1) * 2), Adr);
                dicInst.get(enmInstrumentType.YM3438)[ChipIndex].Write(ChipID, 0, (byte)(1 + (Port & 1) * 2), Data);
            }
        }

        public void PlayPCM_YM3438X(int ChipIndex, byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM3438)) return;
                if (!(dicInst.get(enmInstrumentType.YM3438)[ChipIndex] instanceof ym3438X)) return;
                ((ym3438X)dicInst.get(enmInstrumentType.YM3438)[ChipIndex]).XGMfunction.PlayPCM(ChipID, Adr, Data);
            }
        }

        // #endregion


        // #region YM2612

        public void WriteYM2612mame(byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2612mame)) return;

                dicInst.get(enmInstrumentType.YM2612mame)[0].Write(ChipID, 0, (byte)(0 + (Port & 1) * 2), Adr);
                dicInst.get(enmInstrumentType.YM2612mame)[0].Write(ChipID, 0, (byte)(1 + (Port & 1) * 2), Data);
            }
        }

        public void WriteYM2612mame(int ChipIndex, byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2612mame)) return;

                dicInst.get(enmInstrumentType.YM2612mame)[ChipIndex].Write(ChipID, 0, (byte)(0 + (Port & 1) * 2), Adr);
                dicInst.get(enmInstrumentType.YM2612mame)[ChipIndex].Write(ChipID, 0, (byte)(1 + (Port & 1) * 2), Data);
            }
        }

        public void PlayPCM_YM2612mameX(int ChipIndex, byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2612mame)) return;
                if (!(dicInst.get(enmInstrumentType.YM2612mame)[ChipIndex] instanceof ym2612mameX)) return;
                ((ym2612mameX)dicInst.get(enmInstrumentType.YM2612mame)[ChipIndex]).XGMfunction.PlayPCM(ChipID, Adr, Data);
            }
        }

        // #endregion


        // #region PWM

        public void WritePWM(byte ChipID, byte Adr, int Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.PWM)) return;

                dicInst.get(enmInstrumentType.PWM)[0].Write(ChipID, 0, Adr, (int)Data);
                // (byte)((adr & 0xf0)>>4),(int)((adr & 0xf)*0x100+data));
            }
        }

        public void WritePWM(int ChipIndex,byte ChipID, byte Adr, int Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.PWM)) return;

                dicInst.get(enmInstrumentType.PWM)[ChipIndex].Write(ChipID, 0, Adr, (int)Data);
            }
        }

        // #endregion


        // #region RF5C164

        public void WriteRF5C164(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C164)) return;

                dicInst.get(enmInstrumentType.RF5C164)[0].Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteRF5C164(int ChipIndex,byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C164)) return;

                dicInst.get(enmInstrumentType.RF5C164)[ChipIndex].Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteRF5C164PCMData(byte ChipID, int RAMStartAdr, int RAMDataLength, byte[] SrcData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C164)) return;

                ((scd_pcm)(dicInst.get(enmInstrumentType.RF5C164)[0])).rf5c164_write_ram2(ChipID, RAMStartAdr, RAMDataLength, SrcData, SrcStartAdr);
            }
        }

        public void WriteRF5C164PCMData(int ChipIndex,byte ChipID, int RAMStartAdr, int RAMDataLength, byte[] SrcData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C164)) return;

                ((scd_pcm)(dicInst.get(enmInstrumentType.RF5C164)[ChipIndex])).rf5c164_write_ram2(ChipID, RAMStartAdr, RAMDataLength, SrcData, SrcStartAdr);
            }
        }

        public void WriteRF5C164MemW(byte ChipID, int Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C164)) return;

                ((scd_pcm)(dicInst.get(enmInstrumentType.RF5C164)[0])).rf5c164_mem_w(ChipID, Adr, Data);
            }
        }

        public void WriteRF5C164MemW(int ChipIndex,byte ChipID, int Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C164)) return;

                ((scd_pcm)(dicInst.get(enmInstrumentType.RF5C164)[ChipIndex])).rf5c164_mem_w(ChipID, Adr, Data);
            }
        }

        // #endregion


        // #region RF5C68

        public void WriteRF5C68(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C68)) return;

                dicInst.get(enmInstrumentType.RF5C68)[0].Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteRF5C68(int ChipIndex, byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C68)) return;

                dicInst.get(enmInstrumentType.RF5C68)[ChipIndex].Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteRF5C68PCMData(byte ChipID, int RAMStartAdr, int RAMDataLength, byte[] SrcData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C68)) return;

                ((rf5c68)(dicInst.get(enmInstrumentType.RF5C68)[0])).rf5c68_write_ram2(ChipID, (int)RAMStartAdr, (int)RAMDataLength, SrcData, SrcStartAdr);
            }
        }

        public void WriteRF5C68PCMData(int ChipIndex, byte ChipID, int RAMStartAdr, int RAMDataLength, byte[] SrcData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C68)) return;

                ((rf5c68)(dicInst.get(enmInstrumentType.RF5C68)[ChipIndex])).rf5c68_write_ram2(ChipID, (int)RAMStartAdr, (int)RAMDataLength, SrcData, SrcStartAdr);
            }
        }

        public void WriteRF5C68MemW(byte ChipID, int Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C68)) return;

                ((rf5c68)(dicInst.get(enmInstrumentType.RF5C68)[0])).rf5c68_mem_w(ChipID, (int)Adr, Data);
            }
        }

        public void WriteRF5C68MemW(int ChipIndex, byte ChipID, int Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C68)) return;

                ((rf5c68)(dicInst.get(enmInstrumentType.RF5C68)[ChipIndex])).rf5c68_mem_w(ChipID, (int)Adr, Data);
            }
        }

        // #endregion


        // #region C140

        public void WriteC140(byte ChipID, int Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.C140)) return;

                dicInst.get(enmInstrumentType.C140)[0].Write(ChipID, 0, (int)Adr, Data);
            }
        }

        public void WriteC140(int ChipIndex, byte ChipID, int Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.C140)) return;

                dicInst.get(enmInstrumentType.C140)[ChipIndex].Write(ChipID, 0, (int)Adr, Data);
            }
        }

        public void WriteC140PCMData(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.C140)) return;

                ((c140)(dicInst.get(enmInstrumentType.C140)[0])).c140_write_rom2(ChipID, ROMSize, DataStart, DataLength, ROMData, SrcStartAdr);
            }
        }

        public void WriteC140PCMData(int ChipIndex, byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.C140)) return;

                ((c140)(dicInst.get(enmInstrumentType.C140)[ChipIndex])).c140_write_rom2(ChipID, ROMSize, DataStart, DataLength, ROMData, SrcStartAdr);
            }
        }

        // #endregion


        // #region YM3812

        public void WriteYM3812(int ChipID, int rAdr, int rDat)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM3812)) return;

                ((ym3812)(dicInst.get(enmInstrumentType.YM3812)[0])).Write((byte)ChipID, 0, rAdr, rDat);
            }
        }

        public void WriteYM3812(int ChipIndex,int ChipID, int rAdr, int rDat)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM3812)) return;

                ((ym3812)(dicInst.get(enmInstrumentType.YM3812)[ChipIndex])).Write((byte)ChipID, 0, rAdr, rDat);
            }
        }

        // #endregion


        // #region C352

        public void WriteC352(byte ChipID, int Adr, int Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.C352)) return;

                dicInst.get(enmInstrumentType.C352)[0].Write(ChipID, 0, (int)Adr, (int)Data);
            }
        }

        public void WriteC352(int ChipIndex,byte ChipID, int Adr, int Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.C352)) return;

                dicInst.get(enmInstrumentType.C352)[ChipIndex].Write(ChipID, 0, (int)Adr, (int)Data);
            }
        }

        public void WriteC352PCMData(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.C352)) return;

                ((c352)(dicInst.get(enmInstrumentType.C352)[0])).c352_write_rom2(ChipID, ROMSize, (int)DataStart, (int)DataLength, ROMData, SrcStartAdr);
            }
        }

        public void WriteC352PCMData(int ChipIndex,byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.C352)) return;

                ((c352)(dicInst.get(enmInstrumentType.C352)[ChipIndex])).c352_write_rom2(ChipID, ROMSize, (int)DataStart, (int)DataLength, ROMData, SrcStartAdr);
            }
        }

        // #endregion


        // #region YMF271

        public void WriteYMF271PCMData(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMF271)) return;

                ((ymf271)(dicInst.get(enmInstrumentType.YMF271)[0])).ymf271_write_rom(ChipID, ROMSize, DataStart, DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        public void WriteYMF271PCMData(int ChipIndex, byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMF271)) return;

                ((ymf271)(dicInst.get(enmInstrumentType.YMF271)[ChipIndex])).ymf271_write_rom(ChipID, ROMSize, DataStart, DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        // #endregion


        // #region YMF278B

        public void WriteYMF278BPCMData(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMF278B)) return;

                ((ymf278b)(dicInst.get(enmInstrumentType.YMF278B)[0])).ymf278b_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        public void WriteYMF278BPCMData(int ChipIndex, byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMF278B)) return;

                ((ymf278b)(dicInst.get(enmInstrumentType.YMF278B)[ChipIndex])).ymf278b_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        public void WriteYMF278BPCMRAMData(byte ChipID, int RAMSize, int DataStart, int DataLength, byte[] RAMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMF278B)) return;

                ((ymf278b)(dicInst.get(enmInstrumentType.YMF278B)[0])).ymf278b_write_ram(ChipID, (int)DataStart, (int)DataLength, RAMData, (int)SrcStartAdr);
            }
        }

        public void WriteYMF278BPCMRAMData(int ChipIndex, byte ChipID, int RAMSize, int DataStart, int DataLength, byte[] RAMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMF278B)) return;

                ((ymf278b)(dicInst.get(enmInstrumentType.YMF278B)[ChipIndex])).ymf278b_write_ram(ChipID, (int)DataStart, (int)DataLength, RAMData, (int)SrcStartAdr);
            }
        }

        // #endregion


        // #region YMZ280B

        public void WriteYMZ280BPCMData(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMZ280B)) return;

                ((ymz280b)(dicInst.get(enmInstrumentType.YMZ280B)[0])).ymz280b_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        public void WriteYMZ280BPCMData(int ChipIndex, byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMZ280B)) return;

                ((ymz280b)(dicInst.get(enmInstrumentType.YMZ280B)[ChipIndex])).ymz280b_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        // #endregion


        // #region Y8950

        public void WriteY8950PCMData(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.Y8950)) return;

                ((y8950)(dicInst.get(enmInstrumentType.Y8950)[0])).y8950_write_data_pcmrom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        public void WriteY8950PCMData(int ChipIndex, byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.Y8950)) return;

                ((y8950)(dicInst.get(enmInstrumentType.Y8950)[ChipIndex])).y8950_write_data_pcmrom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        // #endregion


        // #region OKIM6258

        public void WriteOKIM6258(byte ChipID, byte Port, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.OKIM6258)) return;

                ((okim6258)(dicInst.get(enmInstrumentType.OKIM6258)[0])).Write(ChipID, 0, Port, Data);
            }
        }

        public void WriteOKIM6258(int ChipIndex,byte ChipID, byte Port, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.OKIM6258)) return;

                ((okim6258)(dicInst.get(enmInstrumentType.OKIM6258)[ChipIndex])).Write(ChipID, 0, Port, Data);
            }
        }

        // #endregion


        // #region OKIM6295

        public void WriteOKIM6295(byte ChipID, byte Port, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.OKIM6295)) return;

                ((okim6295)(dicInst.get(enmInstrumentType.OKIM6295)[0])).Write(ChipID, 0, Port, Data);
            }
        }

        public void WriteOKIM6295(int ChipIndex, byte ChipID, byte Port, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.OKIM6295)) return;

                ((okim6295)(dicInst.get(enmInstrumentType.OKIM6295)[ChipIndex])).Write(ChipID, 0, Port, Data);
            }
        }

        public void WriteOKIM6295PCMData(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.OKIM6295)) return;

                ((okim6295)(dicInst.get(enmInstrumentType.OKIM6295)[0])).okim6295_write_rom2(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, SrcStartAdr);
            }
        }

        public void WriteOKIM6295PCMData(int ChipIndex,byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.OKIM6295)) return;

                ((okim6295)(dicInst.get(enmInstrumentType.OKIM6295)[ChipIndex])).okim6295_write_rom2(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, SrcStartAdr);
            }
        }

        // #endregion


        // #region SEGAPCM

        public void WriteSEGAPCM(byte ChipID, int Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SEGAPCM)) return;

                ((segapcm)(dicInst.get(enmInstrumentType.SEGAPCM)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteSEGAPCM(int ChipIndex, byte ChipID, int Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SEGAPCM)) return;

                ((segapcm)(dicInst.get(enmInstrumentType.SEGAPCM)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteSEGAPCMPCMData(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SEGAPCM)) return;

                ((segapcm)(dicInst.get(enmInstrumentType.SEGAPCM)[0])).sega_pcm_write_rom2(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, SrcStartAdr);
            }
        }

        public void WriteSEGAPCMPCMData(int ChipIndex,byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SEGAPCM)) return;

                ((segapcm)(dicInst.get(enmInstrumentType.SEGAPCM)[ChipIndex])).sega_pcm_write_rom2(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, SrcStartAdr);
            }
        }

        // #endregion


        // #region YM2151

        public void WriteYM2151(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2151)) return;

                ((dicInst.get(enmInstrumentType.YM2151)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteYM2151(int ChipIndex,byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2151)) return;

                ((dicInst.get(enmInstrumentType.YM2151)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteYM2151mame(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2151mame)) return;

                ((dicInst.get(enmInstrumentType.YM2151mame)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteYM2151mame(int ChipIndex,byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2151mame)) return;

                ((dicInst.get(enmInstrumentType.YM2151mame)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteYM2151x68sound(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2151x68sound)) return;

                ((dicInst.get(enmInstrumentType.YM2151x68sound)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteYM2151x68sound(int ChipIndex,byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2151x68sound)) return;

                ((dicInst.get(enmInstrumentType.YM2151x68sound)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        // #endregion


        // #region YM2203

        public void WriteYM2203(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2203)) return;

                ((ym2203)(dicInst.get(enmInstrumentType.YM2203)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteYM2203(int ChipIndex,byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2203)) return;

                ((ym2203)(dicInst.get(enmInstrumentType.YM2203)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        // #endregion


        // #region YM2608

        public void WriteYM2608(byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2608)) return;

                ((ym2608)(dicInst.get(enmInstrumentType.YM2608)[0])).Write(ChipID, 0, (Port * 0x100 + Adr), Data);
            }
        }

        public void WriteYM2608(int ChipIndex,byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2608)) return;

                ((ym2608)(dicInst.get(enmInstrumentType.YM2608)[ChipIndex])).Write(ChipID, 0, (Port * 0x100 + Adr), Data);
            }
        }

        public byte[] GetADPCMBufferYM2608(byte ChipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2608)) return null;

                return ((ym2608)(dicInst.get(enmInstrumentType.YM2608)[0])).GetADPCMBuffer(ChipID);
            }
        }

        public byte[] GetADPCMBufferYM2608(int ChipIndex,byte ChipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2608)) return null;

                return ((ym2608)(dicInst.get(enmInstrumentType.YM2608)[ChipIndex])).GetADPCMBuffer(ChipID);
            }
        }

        public int ReadStatusExYM2608(byte ChipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2608)) throw new IllegalStateException();

                return ((ym2608)(dicInst.get(enmInstrumentType.YM2608)[0])).ReadStatusEx(ChipID);
            }
        }

        public int ReadStatusExYM2608(int ChipIndex,byte ChipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2608)) throw new IllegalStateException();

                return ((ym2608)(dicInst.get(enmInstrumentType.YM2608)[ChipIndex])).ReadStatusEx(ChipID);
            }
        }

        // #endregion


        // #region YM2609

        public void WriteYM2609(byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2609)) return;

                ((ym2609)(dicInst.get(enmInstrumentType.YM2609)[0])).Write(ChipID, 0, (Port * 0x100 + Adr), Data);
            }
        }

        public void WriteYM2609(int ChipIndex, byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2609)) return;

                ((ym2609)(dicInst.get(enmInstrumentType.YM2609)[ChipIndex])).Write(ChipID, 0, (Port * 0x100 + Adr), Data);
            }
        }

        public void WriteYM2609_SetAdpcmA(byte ChipID, byte[] Buf)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2609)) return;

                ((ym2609)(dicInst.get(enmInstrumentType.YM2609)[0])).SetAdpcmA(ChipID, Buf, Buf.length);
            }
        }

        public void WriteYM2609_SetAdpcmA(int ChipIndex, byte ChipID, byte[] Buf)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2609)) return;

                ((ym2609)(dicInst.get(enmInstrumentType.YM2609)[ChipIndex])).SetAdpcmA(ChipID, Buf, Buf.length);
            }
        }

        // #endregion


        // #region YM2610

        public void WriteYM2610(byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2610)) return;

                ((ym2610)(dicInst.get(enmInstrumentType.YM2610)[0])).Write(ChipID, 0, (Port * 0x100 + Adr), Data);
            }
        }

        public void WriteYM2610(int ChipIndex, byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2610)) return;

                ((ym2610)(dicInst.get(enmInstrumentType.YM2610)[ChipIndex])).Write(ChipID, 0, (Port * 0x100 + Adr), Data);
            }
        }

        public void WriteYM2610_SetAdpcmA(byte ChipID, byte[] Buf)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2610)) return;

                ((ym2610)(dicInst.get(enmInstrumentType.YM2610)[0])).YM2610_setAdpcmA(ChipID, Buf, Buf.length);
            }
        }

        public void WriteYM2610_SetAdpcmA(int ChipIndex, byte ChipID, byte[] Buf)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2610)) return;

                ((ym2610)(dicInst.get(enmInstrumentType.YM2610)[ChipIndex])).YM2610_setAdpcmA(ChipID, Buf, Buf.length);
            }
        }

        public void WriteYM2610_SetAdpcmB(byte ChipID, byte[] Buf)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2610)) return;

                ((ym2610)(dicInst.get(enmInstrumentType.YM2610)[0])).YM2610_setAdpcmB(ChipID, Buf, Buf.length);
            }
        }

        public void WriteYM2610_SetAdpcmB(int ChipIndex, byte ChipID, byte[] Buf)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2610)) return;

                ((ym2610)(dicInst.get(enmInstrumentType.YM2610)[ChipIndex])).YM2610_setAdpcmB(ChipID, Buf, Buf.length);
            }
        }

        // #endregion


        // #region YMF262

        public void WriteYMF262(byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMF262)) return;

                ((ymf262)(dicInst.get(enmInstrumentType.YMF262)[0])).Write(ChipID, 0, (Port * 0x100 + Adr), Data);
            }
        }

        public void WriteYMF262(int ChipIndex,byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMF262)) return;

                ((ymf262)(dicInst.get(enmInstrumentType.YMF262)[ChipIndex])).Write(ChipID, 0, (Port * 0x100 + Adr), Data);
            }
        }

        // #endregion


        // #region YMF271

        public void WriteYMF271(byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMF271)) return;

                ((ymf271)(dicInst.get(enmInstrumentType.YMF271)[0])).Write(ChipID, Port, Adr, Data);
            }
        }

        public void WriteYMF271(int ChipIndex,byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMF271)) return;

                ((ymf271)(dicInst.get(enmInstrumentType.YMF271)[ChipIndex])).Write(ChipID, Port, Adr, Data);
            }
        }

        // #endregion


        // #region YMF278B

        public void WriteYMF278B(byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMF278B)) return;
                ((ymf278b)(dicInst.get(enmInstrumentType.YMF278B)[0])).Write(ChipID, Port, Adr, Data);
            }
        }

        public void WriteYMF278B(int ChipIndex, byte ChipID, byte Port, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMF278B)) return;
                ((ymf278b)(dicInst.get(enmInstrumentType.YMF278B)[ChipIndex])).Write(ChipID, Port, Adr, Data);
            }
        }

        // #endregion


        // #region YM3526

        public void WriteYM3526(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM3526)) return;

                ((ym3526)(dicInst.get(enmInstrumentType.YM3526)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteYM3526(int ChipIndex, byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM3526)) return;

                ((ym3526)(dicInst.get(enmInstrumentType.YM3526)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        // #endregion


        // #region Y8950

        public void WriteY8950(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.Y8950)) return;

                ((y8950)(dicInst.get(enmInstrumentType.Y8950)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteY8950(int ChipIndex,byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.Y8950)) return;

                ((y8950)(dicInst.get(enmInstrumentType.Y8950)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        // #endregion


        // #region YMZ280B

        public void WriteYMZ280B(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMZ280B)) return;

                ((ymz280b)(dicInst.get(enmInstrumentType.YMZ280B)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteYMZ280B(int ChipIndex, byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMZ280B)) return;

                ((ymz280b)(dicInst.get(enmInstrumentType.YMZ280B)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        // #endregion


        // #region HuC6280

        public void WriteHuC6280(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.HuC6280)) return;

                ((Ootake_PSG)(dicInst.get(enmInstrumentType.HuC6280)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteHuC6280(int ChipIndex, byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.HuC6280)) return;

                ((Ootake_PSG)(dicInst.get(enmInstrumentType.HuC6280)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        public byte ReadHuC6280(byte ChipID, byte Adr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.HuC6280)) return 0;

                return ((Ootake_PSG)(dicInst.get(enmInstrumentType.HuC6280)[0])).HuC6280_Read(ChipID, Adr);
            }
        }

        public byte ReadHuC6280(int ChipIndex,byte ChipID, byte Adr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.HuC6280)) return 0;

                return ((Ootake_PSG)(dicInst.get(enmInstrumentType.HuC6280)[ChipIndex])).HuC6280_Read(ChipID, Adr);
            }
        }

        // #endregion


        // #region GA20

        public void WriteGA20(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.GA20)) return;

                ((iremga20)(dicInst.get(enmInstrumentType.GA20)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteGA20(int ChipIndex, byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.GA20)) return;

                ((iremga20)(dicInst.get(enmInstrumentType.GA20)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        // #endregion


        // #region YM2413

        public void WriteYM2413(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2413)) return;

                ((ym2413)(dicInst.get(enmInstrumentType.YM2413)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteYM2413(int ChipIndex, byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2413)) return;

                ((ym2413)(dicInst.get(enmInstrumentType.YM2413)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        // #endregion


        // #region K051649

        public void WriteK051649(byte ChipID, int Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.K051649)) return;

                ((K051649)(dicInst.get(enmInstrumentType.K051649)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteK051649(int ChipIndex,byte ChipID, int Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.K051649)) return;

                ((K051649)(dicInst.get(enmInstrumentType.K051649)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        // #endregion


        // #region K053260

        public void WriteK053260(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.K053260)) return;

                ((K053260)(dicInst.get(enmInstrumentType.K053260)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteK053260(int ChipIndex, byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.K053260)) return;

                ((K053260)(dicInst.get(enmInstrumentType.K053260)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteK053260PCMData(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.K053260)) return;

                ((K053260)(dicInst.get(enmInstrumentType.K053260)[0])).k053260_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        public void WriteK053260PCMData(int ChipIndex, byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.K053260)) return;

                ((K053260)(dicInst.get(enmInstrumentType.K053260)[ChipIndex])).k053260_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        // #endregion


        // #region K054539

        public void WriteK054539(byte ChipID, int Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.K054539)) return;

                ((K054539)(dicInst.get(enmInstrumentType.K054539)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteK054539(int ChipIndex,byte ChipID, int Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.K054539)) return;

                ((K054539)(dicInst.get(enmInstrumentType.K054539)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteK054539PCMData(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.K054539)) return;

                ((K054539)(dicInst.get(enmInstrumentType.K054539)[0])).k054539_write_rom2(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        public void WriteK054539PCMData(int ChipIndex, byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.K054539)) return;

                ((K054539)(dicInst.get(enmInstrumentType.K054539)[ChipIndex])).k054539_write_rom2(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        // #endregion


        // #region PPZ8

        public void WritePPZ8(byte ChipID, int port, int address, int data, byte[] addtionalData)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.PPZ8)) return;

                //if (port == 0x03)
                //{
                //    ((PPZ8)(dicInst.get(enmInstrumentType.PPZ8)[0])).LoadPcm(ChipID, (byte)address, (byte)data, addtionalData);
                //}
                //else
                //{
                   ((PPZ8)(dicInst.get(enmInstrumentType.PPZ8)[0])).Write(ChipID, port, address, data);
                //}
            }
        }

        public void WritePPZ8(int ChipIndex, byte ChipID, int port, int address, int data, byte[] addtionalData)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.PPZ8)) return;

                //if (port == 0x03)
                //{
                //    ((PPZ8)(dicInst.get(enmInstrumentType.PPZ8)[0])).LoadPcm(ChipID, (byte)address, (byte)data, addtionalData);
                //}
                //else
                //{
                ((PPZ8)(dicInst.get(enmInstrumentType.PPZ8)[ChipIndex])).Write(ChipID, port, address, data);
                //}
            }
        }

        public void WritePPZ8PCMData(byte ChipID, int address, int data, byte[][] PCMData)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.PPZ8)) return;

                ((PPZ8)(dicInst.get(enmInstrumentType.PPZ8)[0])).LoadPcm(ChipID, (byte)address, (byte)data, PCMData);
            }
        }

        public void WritePPZ8PCMData(int ChipIndex, byte ChipID, int address, int data, byte[][] PCMData)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.PPZ8)) return;

                ((PPZ8)(dicInst.get(enmInstrumentType.PPZ8)[ChipIndex])).LoadPcm(ChipID, (byte)address, (byte)data, PCMData);
            }
        }

        public int[][][] getPPZ8VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.PPZ8)) return null;
            return ((PPZ8)dicInst.get(enmInstrumentType.PPZ8)[0]).visVolume;
        }

        public int[][][] getPPZ8VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.PPZ8)) return null;
            return ((PPZ8)dicInst.get(enmInstrumentType.PPZ8)[ChipIndex]).visVolume;
        }

        // #endregion


        // #region PPSDRV

        public void WritePPSDRV(byte ChipID, int port, int address, int data, byte[] addtionalData)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.PPSDRV)) return;

                ((PPSDRV)(dicInst.get(enmInstrumentType.PPSDRV)[0])).Write(ChipID, port, address, data);
            }
        }

        public void WritePPSDRV(int ChipIndex, byte ChipID, int port, int address, int data, byte[] addtionalData)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.PPSDRV)) return;

                ((PPSDRV)(dicInst.get(enmInstrumentType.PPSDRV)[ChipIndex])).Write(ChipID, port, address, data);
            }
        }

        public void WritePPSDRVPCMData(byte ChipID, byte[] PCMData)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.PPSDRV)) return;

                ((PPSDRV)(dicInst.get(enmInstrumentType.PPSDRV)[0])).Load(ChipID, PCMData);
            }
        }

        public void WritePPSDRVPCMData(int ChipIndex, byte ChipID, byte[] PCMData)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.PPSDRV)) return;

                ((PPSDRV)(dicInst.get(enmInstrumentType.PPSDRV)[ChipIndex])).Load(ChipID, PCMData);
            }
        }

        // #endregion


        // #region P86

        public void WriteP86(byte ChipID, int port, int address, int data, byte[] addtionalData)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.P86)) return;

                //if (port == 0x03)
                //{
                //    ((P86)(dicInst.get(enmInstrumentType.P86)[0])).LoadPcm(ChipID, (byte)address, (byte)data, addtionalData);
                //}
                //else
                //{
                ((P86)(dicInst.get(enmInstrumentType.P86)[0])).Write(ChipID, port, address, data);
                //}
            }
        }

        public void WriteP86(int ChipIndex, byte ChipID, int port, int address, int data, byte[] addtionalData)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.P86)) return;

                //if (port == 0x03)
                //{
                //    ((P86)(dicInst.get(enmInstrumentType.P86)[0])).LoadPcm(ChipID, (byte)address, (byte)data, addtionalData);
                //}
                //else
                //{
                ((P86)(dicInst.get(enmInstrumentType.P86)[ChipIndex])).Write(ChipID, port, address, data);
                //}
            }
        }

        public void WriteP86PCMData(byte ChipID, int address, int data, byte[] PCMData)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.P86)) return;

                ((P86)(dicInst.get(enmInstrumentType.P86)[0])).LoadPcm(ChipID, (byte)address, (byte)data, PCMData);
            }
        }

        public void WriteP86PCMData(int ChipIndex, byte ChipID, int address, int data, byte[] PCMData)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.P86)) return;

                ((P86)(dicInst.get(enmInstrumentType.P86)[ChipIndex])).LoadPcm(ChipID, (byte)address, (byte)data, PCMData);
            }
        }

        // #endregion


        // #region QSound

        public void WriteQSound(byte ChipID, int adr, byte dat)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.QSound)) return;

                ((qsound)(dicInst.get(enmInstrumentType.QSound)[0])).qsound_w(ChipID, adr, dat);
            }
        }

        public void WriteQSound(int ChipIndex, byte ChipID, int adr, byte dat)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.QSound)) return;

                ((qsound)(dicInst.get(enmInstrumentType.QSound)[ChipIndex])).qsound_w(ChipID, adr, dat);
            }
        }

        public void WriteQSoundPCMData(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.QSound)) return;

                ((qsound)(dicInst.get(enmInstrumentType.QSound)[0])).qsound_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        public void WriteQSoundPCMData(int ChipIndex, byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.QSound)) return;

                ((qsound)(dicInst.get(enmInstrumentType.QSound)[ChipIndex])).qsound_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        // #endregion


        // #region QSoundCtr

        public void WriteQSoundCtr(byte ChipID, int adr, byte dat)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.QSoundCtr)) return;

                //((qsound)(dicInst.get(enmInstrumentType.QSound)[0])).qsound_w(ChipID, adr, dat);
                ((Qsound_ctr)(dicInst.get(enmInstrumentType.QSoundCtr)[0])).qsound_w(ChipID, adr, dat);
            }
        }

        public void WriteQSoundCtr(int ChipIndex, byte ChipID, int adr, byte dat)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.QSoundCtr)) return;

                //((qsound)(dicInst.get(enmInstrumentType.QSound)[ChipIndex])).qsound_w(ChipID, adr, dat);
                ((Qsound_ctr)(dicInst.get(enmInstrumentType.QSoundCtr)[ChipIndex])).qsound_w(ChipID, adr, dat);
            }
        }

        public void WriteQSoundCtrPCMData(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.QSoundCtr)) return;

                //((qsound)(dicInst.get(enmInstrumentType.QSound)[0])).qsound_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
                ((Qsound_ctr)(dicInst.get(enmInstrumentType.QSoundCtr)[0])).qsound_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        public void WriteQSoundCtrPCMData(int ChipIndex, byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.QSoundCtr)) return;

                //((qsound)(dicInst.get(enmInstrumentType.QSound)[ChipIndex])).qsound_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
                ((Qsound_ctr)(dicInst.get(enmInstrumentType.QSoundCtr)[ChipIndex])).qsound_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        // #endregion


        // #region GA20

        public void WriteGA20PCMData(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.GA20)) return;

                ((iremga20)(dicInst.get(enmInstrumentType.GA20)[0])).iremga20_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        public void WriteGA20PCMData(int ChipIndex, byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.GA20)) return;

                ((iremga20)(dicInst.get(enmInstrumentType.GA20)[ChipIndex])).iremga20_write_rom(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        // #endregion


        // #region DMG

        public void WriteDMG(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.DMG)) return;

                ((gb)(dicInst.get(enmInstrumentType.DMG)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteDMG(int ChipIndex,byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.DMG)) return;

                ((gb)(dicInst.get(enmInstrumentType.DMG)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        public gb.gb_sound_t ReadDMG(byte ChipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.DMG)) return null;

                return ((gb)(dicInst.get(enmInstrumentType.DMG)[0])).GetSoundData(ChipID);
            }

        }

        public gb.gb_sound_t ReadDMG(int ChipIndex,byte ChipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.DMG)) return null;

                return ((gb)(dicInst.get(enmInstrumentType.DMG)[ChipIndex])).GetSoundData(ChipID);
            }

        }

        public void setDMGMask(byte ChipID, int ch)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.DMG)) return;
                if (dicInst.get(enmInstrumentType.DMG)[0] == null) return;

                int maskStatus = ((gb)(dicInst.get(enmInstrumentType.DMG)[0])).gameboy_sound_get_mute_mask(ChipID);
                maskStatus |= (int)(1 << ch);//ch:0 - 3
                ((gb)(dicInst.get(enmInstrumentType.DMG)[0])).gameboy_sound_set_mute_mask(ChipID, maskStatus);
            }
        }

        public void resetDMGMask(byte ChipID, int ch)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.DMG)) return;
                if (dicInst.get(enmInstrumentType.DMG)[0] == null) return;

                int maskStatus = ((gb)(dicInst.get(enmInstrumentType.DMG)[0])).gameboy_sound_get_mute_mask(ChipID);
                maskStatus &= (int)(~(1 << ch));//ch:0 - 3
                ((gb)(dicInst.get(enmInstrumentType.DMG)[0])).gameboy_sound_set_mute_mask(ChipID, maskStatus);
            }
        }

        public void setDMGMask(int ChipIndex, byte ChipID, int ch)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.DMG)) return;
                if (dicInst.get(enmInstrumentType.DMG)[ChipIndex] == null) return;

                int maskStatus = ((gb)(dicInst.get(enmInstrumentType.DMG)[ChipIndex])).gameboy_sound_get_mute_mask(ChipID);
                maskStatus |= (int)(1 << ch);//ch:0 - 3
                ((gb)(dicInst.get(enmInstrumentType.DMG)[ChipIndex])).gameboy_sound_set_mute_mask(ChipID, maskStatus);
            }
        }

        public void resetDMGMask(int ChipIndex, byte ChipID, int ch)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.DMG)) return;
                if (dicInst.get(enmInstrumentType.DMG)[ChipIndex] == null) return;

                int maskStatus = ((gb)(dicInst.get(enmInstrumentType.DMG)[ChipIndex])).gameboy_sound_get_mute_mask(ChipID);
                maskStatus &= (int)(~(1 << ch));//ch:0 - 3
                ((gb)(dicInst.get(enmInstrumentType.DMG)[ChipIndex])).gameboy_sound_set_mute_mask(ChipID, maskStatus);
            }
        }

        // #endregion


        // #region NES

        public void WriteNES(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return;

                ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteNES(int ChipIndex,byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return;

                ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteNESRam(byte ChipID, int DataStart, int DataLength, byte[] RAMData, int RAMDataStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return;

                ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[0])).nes_write_ram(ChipID, DataStart, DataLength, RAMData, RAMDataStartAdr);
            }
        }

        public void WriteNESRam(int ChipIndex, byte ChipID, int DataStart, int DataLength, byte[] RAMData, int RAMDataStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return;

                ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[ChipIndex])).nes_write_ram(ChipID, DataStart, DataLength, RAMData, RAMDataStartAdr);
            }
        }

        public byte[] ReadNESapu(byte ChipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return null;

                return ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[0])).nes_r_apu(ChipID);
            }
        }

        public byte[] ReadNESapu(int ChipIndex, byte ChipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return null;

                return ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[ChipIndex])).nes_r_apu(ChipID);
            }
        }

        public byte[] ReadNESdmc(byte ChipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return null;

                return ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[0])).nes_r_dmc(ChipID);
            }
        }

        public byte[] ReadNESdmc(int ChipIndex, byte ChipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return null;

                return ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[ChipIndex])).nes_r_dmc(ChipID);
            }
        }

        // #endregion


        // #region VRC6

        int[] vrc6AddressTable = new int[]
        {
            0x9000,0x9001,0x9002,0x9003,
            0xa000,0xa001,0xa002,0xa003,
            0xb000,0xb001,0xb002,0xb003
        };

        public void WriteVRC6(int ChipIndex, byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.VRC6)) return;

                ((VRC6)(dicInst.get(enmInstrumentType.VRC6)[ChipIndex])).Write(ChipID, 0, vrc6AddressTable[Adr], Data);
            }
        }

        // #endregion


        // #region MultiPCM

        public void WriteMultiPCM(byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.MultiPCM)) return;

                ((multipcm)(dicInst.get(enmInstrumentType.MultiPCM)[0])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteMultiPCM(int ChipIndex,byte ChipID, byte Adr, byte Data)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.MultiPCM)) return;

                ((multipcm)(dicInst.get(enmInstrumentType.MultiPCM)[ChipIndex])).Write(ChipID, 0, Adr, Data);
            }
        }

        public void WriteMultiPCMSetBank(byte ChipID, byte Ch, int Adr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.MultiPCM)) return;

                ((multipcm)(dicInst.get(enmInstrumentType.MultiPCM)[0])).multipcm_bank_write(ChipID, Ch, (int)Adr);
            }
        }

        public void WriteMultiPCMSetBank(int ChipIndex,byte ChipID, byte Ch, int Adr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.MultiPCM)) return;

                ((multipcm)(dicInst.get(enmInstrumentType.MultiPCM)[ChipIndex])).multipcm_bank_write(ChipID, Ch, (int)Adr);
            }
        }

        public void WriteMultiPCMPCMData(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.MultiPCM)) return;

                ((multipcm)(dicInst.get(enmInstrumentType.MultiPCM)[0])).multipcm_write_rom2(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        public void WriteMultiPCMPCMData(int ChipIndex, byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int SrcStartAdr)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.MultiPCM)) return;

                ((multipcm)(dicInst.get(enmInstrumentType.MultiPCM)[ChipIndex])).multipcm_write_rom2(ChipID, (int)ROMSize, (int)DataStart, (int)DataLength, ROMData, (int)SrcStartAdr);
            }
        }

        // #endregion


        // #region FDS

        public np_nes_fds.NES_FDS ReadFDS(byte ChipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return null;

                return ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[0])).nes_r_fds(ChipID);
            }
        }

        public np_nes_fds.NES_FDS ReadFDS(int ChipIndex, byte ChipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return null;

                return ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[ChipIndex])).nes_r_fds(ChipID);
            }
        }

        // #endregion



        public void SetVolumeYM2151(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2151)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2151) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeYM2151mame(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2151mame)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2151mame) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeYM2151x68sound(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2151x68sound)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2151x68sound) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeYM2203(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2203)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2203) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeYM2203FM(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2203)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2203) continue;
                ((ym2203)c.Instrument).SetFMVolume((byte)0, vol);
                ((ym2203)c.Instrument).SetFMVolume((byte)1, vol);
            }
        }

        public void SetVolumeYM2203PSG(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2203)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2203) continue;
                ((ym2203)c.Instrument).SetPSGVolume((byte)0, vol);
                ((ym2203)c.Instrument).SetPSGVolume((byte)1, vol);
            }
        }

        public void SetVolumeYM2413(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2413)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2413) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void setVolumeHuC6280(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.HuC6280)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.HuC6280) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeYM2608(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2608)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2608) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeYM2608FM(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2608)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2608) continue;
                ((ym2608)c.Instrument).SetFMVolume((byte) 0, vol);
                ((ym2608)c.Instrument).SetFMVolume((byte) 1, vol);
            }
        }

        public void SetVolumeYM2608PSG(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2608)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2608) continue;
                ((ym2608)c.Instrument).SetPSGVolume((byte) 0, vol);
                ((ym2608)c.Instrument).SetPSGVolume((byte) 1, vol);
            }
        }

        public void SetVolumeYM2608Rhythm(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2608)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2608) continue;
                ((ym2608)c.Instrument).SetRhythmVolume((byte) 0, vol);
                ((ym2608)c.Instrument).SetRhythmVolume((byte) 1, vol);
            }
        }

        public void SetVolumeYM2608Adpcm(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2608)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2608) continue;
                ((ym2608)c.Instrument).SetAdpcmVolume((byte) 0, vol);
                ((ym2608)c.Instrument).SetAdpcmVolume((byte) 1, vol);
            }
        }

        public void SetVolumeYM2609FM(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2609)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2609) continue;
                ((ym2609)c.Instrument).SetFMVolume((byte) 0, vol);
                ((ym2609)c.Instrument).SetFMVolume((byte) 1, vol);
            }
        }

        public void SetVolumeYM2609PSG(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2609)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2609) continue;
                ((ym2609)c.Instrument).SetPSGVolume((byte) 0, vol);
                ((ym2609)c.Instrument).SetPSGVolume((byte) 1, vol);
            }
        }

        public void SetVolumeYM2609Rhythm(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2609)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2609) continue;
                ((ym2609)c.Instrument).SetRhythmVolume((byte) 0, vol);
                ((ym2609)c.Instrument).SetRhythmVolume((byte) 1, vol);
            }
        }

        public void SetVolumeYM2609Adpcm(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2609)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2609) continue;
                ((ym2609)c.Instrument).SetAdpcmVolume((byte) 0, vol);
                ((ym2609)c.Instrument).SetAdpcmVolume((byte) 1, vol);
            }
        }

        public void SetVolumeYM2610(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2610)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2610) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeYM2610FM(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2610)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2610) continue;
                ((ym2610)c.Instrument).SetFMVolume((byte) 0, vol);
                ((ym2610)c.Instrument).SetFMVolume((byte) 1, vol);
            }
        }

        public void SetVolumeYM2610PSG(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2610)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2610) continue;
                ((ym2610)c.Instrument).SetPSGVolume((byte) 0, vol);
                ((ym2610)c.Instrument).SetPSGVolume((byte) 1, vol);
            }
        }

        public void SetVolumeYM2610AdpcmA(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2610)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2610) continue;
                ((ym2610)c.Instrument).SetAdpcmAVolume((byte) 0, vol);
                ((ym2610)c.Instrument).SetAdpcmAVolume((byte) 1, vol);
            }
        }

        public void SetVolumeYM2610AdpcmB(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2610)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2610) continue;
                ((ym2610)c.Instrument).SetAdpcmBVolume((byte) 0, vol);
                ((ym2610)c.Instrument).SetAdpcmBVolume((byte) 1, vol);
            }
        }

        public void SetVolumeYM2612(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2612)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM2612) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeYM3438(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM3438)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM3438) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeSN76489(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.SN76489)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.SN76489) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeRF5C164(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.RF5C164)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.RF5C164) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumePWM(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.PWM)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.PWM) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeOKIM6258(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.OKIM6258)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.OKIM6258) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeMpcmX68k(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.mpcmX68k)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.mpcmX68k) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeOKIM6295(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.OKIM6295)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.OKIM6295) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeC140(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.C140)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.C140) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeC352(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.C352)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.C352) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetRearMute(byte flag)
        {
            if (!dicInst.containsKey(enmInstrumentType.C352)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.C352) continue;
                for (int i = 0; i < dicInst.get(enmInstrumentType.C352).length; i++)
                    ((c352)dicInst.get(enmInstrumentType.C352)[i]).c352_set_options(flag);
            }
        }

        public void SetVolumeK051649(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.K051649)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.K051649) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeK053260(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.K053260)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.K053260) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeRF5C68(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.RF5C68)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.RF5C68) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeYM3812(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM3812)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM3812) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeY8950(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.Y8950)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.Y8950) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeYM3526(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM3526)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YM3526) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeK054539(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.K054539)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.K054539) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeQSound(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.QSound)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.QSound) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeQSoundCtr(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.QSoundCtr)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.QSoundCtr) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeDMG(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.DMG)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.DMG) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeGA20(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.GA20)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.GA20) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeYMZ280B(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YMZ280B)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YMZ280B) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeYMF271(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YMF271)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YMF271) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeYMF262(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YMF262)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YMF262) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeYMF278B(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.YMF278B)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.YMF278B) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeMultiPCM(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.MultiPCM)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.MultiPCM) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeSegaPCM(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.SEGAPCM)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.SEGAPCM) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeSAA1099(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.SAA1099)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.SAA1099) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumePPZ8(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.PPZ8)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.PPZ8) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeNES(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.Nes)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type == enmInstrumentType.Nes)
                {
                    c.Volume = Math.max(Math.min(vol, 20), -192);
                    //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                    int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                    //16384 = 0x4000 = short.MAXValue + 1
                    c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
                    ((nes_intf)c.Instrument).SetVolumeAPU(vol);
                }
            }
        }

        public void SetVolumeDMC(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.DMC)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type == enmInstrumentType.DMC)
                {
                    c.Volume = Math.max(Math.min(vol, 20), -192);
                    //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                    int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                    //16384 = 0x4000 = short.MAXValue + 1
                    c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
                    ((nes_intf)c.Instrument).SetVolumeDMC(vol);
                }
            }
        }

        public void SetVolumeFDS(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.FDS)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type == enmInstrumentType.FDS)
                {
                    c.Volume = Math.max(Math.min(vol, 20), -192);
                    //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                    int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                    //16384 = 0x4000 = short.MAXValue + 1
                    c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
                    ((nes_intf)c.Instrument).SetVolumeFDS(vol);
                }
            }
        }

        public void SetVolumeMMC5(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.MMC5)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.MMC5) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeN160(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.N160)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.N160) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeVRC6(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.VRC6)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.VRC6) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeVRC7(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.VRC7)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.VRC7) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        public void SetVolumeFME7(int vol)
        {
            if (!dicInst.containsKey(enmInstrumentType.FME7)) return;

            if (insts == null) return;

            for (Chip c : insts)
            {
                if (c.type != enmInstrumentType.FME7) continue;
                c.Volume = Math.max(Math.min(vol, 20), -192);
                //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / insts.length;
                int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8);
                //16384 = 0x4000 = short.MAXValue + 1
                c.tVolume = Math.max(Math.min((int)(n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }



        public int[] ReadSN76489Register()
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SN76489)) return null;
                return ((sn76489)(dicInst.get(enmInstrumentType.SN76489)[0])).SN76489_Chip[0].Registers;
            }
        }

        public int[] ReadSN76489Register(int ChipIndex)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SN76489)) return null;
                return ((sn76489)(dicInst.get(enmInstrumentType.SN76489)[ChipIndex])).SN76489_Chip[0].Registers;
            }
        }

        public int[][] ReadYM2612Register(byte chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2612)) return null;
                return ((ym2612)(dicInst.get(enmInstrumentType.YM2612)[0])).YM2612_Chip[chipID].REG;
            }
        }

        public int[][] ReadYM2612Register(int ChipIndex,byte chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2612)) return null;
                return ((ym2612)(dicInst.get(enmInstrumentType.YM2612)[ChipIndex])).YM2612_Chip[chipID].REG;
            }
        }

        public scd_pcm.pcm_chip_ ReadRf5c164Register(int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C164)) return null;
                if (((scd_pcm)(dicInst.get(enmInstrumentType.RF5C164)[0])).PCM_Chip == null || ((scd_pcm)(dicInst.get(enmInstrumentType.RF5C164)[0])).PCM_Chip.length < 1) return null;
                return ((scd_pcm)(dicInst.get(enmInstrumentType.RF5C164)[0])).PCM_Chip[chipID];
            }
        }

        public scd_pcm.pcm_chip_ ReadRf5c164Register(int ChipIndex,int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C164)) return null;
                if (((scd_pcm)(dicInst.get(enmInstrumentType.RF5C164)[ChipIndex])).PCM_Chip == null || ((scd_pcm)(dicInst.get(enmInstrumentType.RF5C164)[ChipIndex])).PCM_Chip.length < 1) return null;
                return ((scd_pcm)(dicInst.get(enmInstrumentType.RF5C164)[ChipIndex])).PCM_Chip[chipID];
            }
        }

        public rf5c68.rf5c68_state ReadRf5c68Register(int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C68)) return null;
                if (((rf5c68)(dicInst.get(enmInstrumentType.RF5C68)[0])).RF5C68Data == null || ((rf5c68)(dicInst.get(enmInstrumentType.RF5C68)[0])).RF5C68Data.length < 1) return null;
                return ((rf5c68)(dicInst.get(enmInstrumentType.RF5C68)[0])).RF5C68Data[chipID];
            }
        }

        public rf5c68.rf5c68_state ReadRf5c68Register(int ChipIndex, int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C68)) return null;
                if (((rf5c68)(dicInst.get(enmInstrumentType.RF5C68)[ChipIndex])).RF5C68Data == null || ((rf5c68)(dicInst.get(enmInstrumentType.RF5C68)[ChipIndex])).RF5C68Data.length < 1) return null;
                return ((rf5c68)(dicInst.get(enmInstrumentType.RF5C68)[ChipIndex])).RF5C68Data[chipID];
            }
        }

        public c140.c140_state ReadC140Register(int cur)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.C140)) return null;
                return ((c140)dicInst.get(enmInstrumentType.C140)[0]).C140Data[cur];
            }
        }

        public c140.c140_state ReadC140Register(int ChipIndex,int cur)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.C140)) return null;
                return ((c140)dicInst.get(enmInstrumentType.C140)[ChipIndex]).C140Data[cur];
            }
        }

        public int[] ReadC352Flag(int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.C352)) return null;
                return ((c352)dicInst.get(enmInstrumentType.C352)[0]).flags[chipID];
            }
        }

        public int[] ReadC352Flag(int ChipIndex,int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.C352)) return null;
                return ((c352)dicInst.get(enmInstrumentType.C352)[ChipIndex]).flags[chipID];
            }
        }

        public multipcm._MultiPCM ReadMultiPCMRegister(int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.MultiPCM)) return null;
                return ((multipcm)(dicInst.get(enmInstrumentType.MultiPCM)[0])).multipcm_r(chipID);
            }
        }

        public multipcm._MultiPCM ReadMultiPCMRegister(int ChipIndex,int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.MultiPCM)) return null;
                return ((multipcm)(dicInst.get(enmInstrumentType.MultiPCM)[ChipIndex])).multipcm_r(chipID);
            }
        }

        public ymf271.YMF271Chip ReadYMF271Register(int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMF271)) return null;
                if (dicInst.get(enmInstrumentType.YMF271)[0] == null) return null;
                return ((ymf271)(dicInst.get(enmInstrumentType.YMF271)[0])).YMF271Data[chipID];
            }
        }

        public ymf271.YMF271Chip ReadYMF271Register(int ChipIndex, int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YMF271)) return null;
                if (dicInst.get(enmInstrumentType.YMF271)[ChipIndex] == null) return null;
                return ((ymf271)(dicInst.get(enmInstrumentType.YMF271)[ChipIndex])).YMF271Data[chipID];
            }
        }


        public okim6258.okim6258_state ReadOKIM6258Status(int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.OKIM6258)) return null;
                return ((okim6258)dicInst.get(enmInstrumentType.OKIM6258)[0]).OKIM6258Data[chipID];
            }
        }

        public okim6258.okim6258_state ReadOKIM6258Status(int ChipIndex,int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.OKIM6258)) return null;
                return ((okim6258)dicInst.get(enmInstrumentType.OKIM6258)[ChipIndex]).OKIM6258Data[chipID];
            }
        }

        public okim6295.okim6295_state ReadOKIM6295Status(int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.OKIM6295)) return null;
                return ((okim6295)dicInst.get(enmInstrumentType.OKIM6295)[0]).OKIM6295Data[chipID];
            }
        }

        public okim6295.okim6295_state ReadOKIM6295Status(int ChipIndex, int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.OKIM6295)) return null;
                return ((okim6295)dicInst.get(enmInstrumentType.OKIM6295)[ChipIndex]).OKIM6295Data[chipID];
            }
        }

        public segapcm.segapcm_state ReadSegaPCMStatus(int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SEGAPCM)) return null;
                return ((segapcm)dicInst.get(enmInstrumentType.SEGAPCM)[0]).SPCMData[chipID];
            }
        }

        public segapcm.segapcm_state ReadSegaPCMStatus(int ChipIndex,int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.SEGAPCM)) return null;
                return ((segapcm)dicInst.get(enmInstrumentType.SEGAPCM)[ChipIndex]).SPCMData[chipID];
            }
        }


        public Ootake_PSG.huc6280_state ReadHuC6280Status(int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.HuC6280)) return null;
                return ((Ootake_PSG)dicInst.get(enmInstrumentType.HuC6280)[0]).GetState((byte)chipID);
            }
        }

        public Ootake_PSG.huc6280_state ReadHuC6280Status(int ChipIndex,int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.HuC6280)) return null;
                return ((Ootake_PSG)dicInst.get(enmInstrumentType.HuC6280)[ChipIndex]).GetState((byte)chipID);
            }
        }

        public K051649.k051649_state ReadK051649Status(int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.K051649)) return null;
                return ((K051649)dicInst.get(enmInstrumentType.K051649)[0]).GetK051649_State((byte)chipID);
            }
        }

        public K051649.k051649_state ReadK051649Status(int ChipIndex,int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.K051649)) return null;
                return ((K051649)dicInst.get(enmInstrumentType.K051649)[ChipIndex]).GetK051649_State((byte)chipID);
            }
        }

        public int[][] ReadRf5c164Volume(int chipID)
        {
            synchronized (lockobj)
            {
                return rf5c164Vol[chipID];
            }
        }

        public PPZ8.PPZChannelWork[] ReadPPZ8Status(int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.PPZ8)) return null;
                return ((PPZ8)dicInst.get(enmInstrumentType.PPZ8)[0]).GetPPZ8_State((byte)chipID);
            }
        }

        public PPZ8.PPZChannelWork[] ReadPPZ8Status(int ChipIndex, int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.PPZ8)) return null;
                return ((PPZ8)dicInst.get(enmInstrumentType.PPZ8)[ChipIndex]).GetPPZ8_State((byte)chipID);
            }
        }



        public int[] ReadYM2612KeyOn(byte chipID)
        {
            synchronized (lockobj)
            {
                int[] keys = new int[((ym2612)(dicInst.get(enmInstrumentType.YM2612)[0])).YM2612_Chip[chipID].CHANNEL.length];
                for (int i = 0; i < keys.length; i++)
                    keys[i] = ((ym2612)(dicInst.get(enmInstrumentType.YM2612)[0])).YM2612_Chip[chipID].CHANNEL[i].KeyOn;
                return keys;
            }
        }

        public int[] ReadYM2612KeyOn(int ChipIndex,byte chipID)
        {
            synchronized (lockobj)
            {
                int[] keys = new int[((ym2612)(dicInst.get(enmInstrumentType.YM2612)[ChipIndex])).YM2612_Chip[chipID].CHANNEL.length];
                for (int i = 0; i < keys.length; i++)
                    keys[i] = ((ym2612)(dicInst.get(enmInstrumentType.YM2612)[ChipIndex])).YM2612_Chip[chipID].CHANNEL[i].KeyOn;
                return keys;
            }
        }

        public int[] ReadYM2151KeyOn(int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2151)) return null;
                for (int i = 0; i < 8; i++)
                {
                    //ym2151Key[chipID][i] = ((ym2151)(iYM2151)).YM2151_Chip[chipID].CHANNEL[i].KeyOn;
                }
                return ym2151Key[chipID];
            }
        }

        public int[] ReadYM2203KeyOn(int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2203)) return null;
                for (int i = 0; i < 6; i++)
                {
                    //ym2203Key[chipID][i] = ((ym2203)(iYM2203)).YM2203_Chip[chipID].CHANNEL[i].KeyOn;
                }
                return ym2203Key[chipID];
            }
        }

        public int[] ReadYM2608KeyOn(int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2608)) return null;
                for (int i = 0; i < 11; i++)
                {
                    //ym2608Key[chipID][i] = ((ym2608)(iYM2608)).YM2608_Chip[chipID].CHANNEL[i].KeyOn;
                }
                return ym2608Key[chipID];
            }
        }

        public int[] ReadYM2609KeyOn(int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2609)) return null;
                for (int i = 0; i < 11; i++)
                {
                    //ym2608Key[chipID][i] = ((ym2608)(iYM2608)).YM2608_Chip[chipID].CHANNEL[i].KeyOn;
                }
                return ym2609Key[chipID];
            }
        }

        public int[] ReadYM2610KeyOn(int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2610)) return null;
                for (int i = 0; i < 11; i++)
                {
                    //ym2610Key[chipID][i] = ((ym2610)(iYM2610)).YM2610_Chip[chipID].CHANNEL[i].KeyOn;
                }
                return ym2610Key[chipID];
            }
        }




        public void setSN76489Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                sn76489Mask.get(0)[chipID] &= ~ch;
                if (!dicInst.containsKey(enmInstrumentType.SN76489)) return;
                ((sn76489)(dicInst.get(enmInstrumentType.SN76489)[0])).SN76489_SetMute((byte)chipID, sn76489Mask.get(0)[chipID]);
            }
        }

        public void setSN76489Mask(int ChipIndex,int chipID, int ch)
        {
            synchronized (lockobj)
            {
                sn76489Mask.get(ChipIndex)[chipID] &= ~ch;
                if (!dicInst.containsKey(enmInstrumentType.SN76489)) return;
                ((sn76489)(dicInst.get(enmInstrumentType.SN76489)[ChipIndex])).SN76489_SetMute((byte)chipID, sn76489Mask.get(ChipIndex)[chipID]);
            }
        }

        public void setYM2612Mask(int chipID,int ch)
        {
            synchronized (lockobj)
            {
                ym2612Mask.get(0)[chipID] |= 1<<ch;
                if (dicInst.containsKey(enmInstrumentType.YM2612))
                {
                    ((ym2612)(dicInst.get(enmInstrumentType.YM2612)[0])).YM2612_SetMute((byte)chipID, ym2612Mask.get(0)[chipID]);
                }
                if (dicInst.containsKey(enmInstrumentType.YM2612mame))
                {
                    ((ym2612mame)(dicInst.get(enmInstrumentType.YM2612mame)[0])).SetMute((byte)chipID, (int)ym2612Mask.get(0)[chipID]);
                }
                if (dicInst.containsKey(enmInstrumentType.YM3438))
                {
                    int mask = (int)ym2612Mask.get(0)[chipID];
                    if ((mask & 0b0010_0000) == 0) mask &= 0b1011_1111;
                    else mask |= 0b0100_0000;
                    ((ym3438)(dicInst.get(enmInstrumentType.YM3438)[0])).OPN2_SetMute((byte)chipID, mask);
                }
            }
        }

        public void setYM2612Mask(int ChipIndex,int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ym2612Mask.get(ChipIndex)[chipID] |= 1<<ch;
                if (dicInst.containsKey(enmInstrumentType.YM2612))
                {
                    ((ym2612)(dicInst.get(enmInstrumentType.YM2612)[ChipIndex])).YM2612_SetMute((byte)chipID, ym2612Mask.get(ChipIndex)[chipID]);
                }
                if (dicInst.containsKey(enmInstrumentType.YM2612mame))
                {
                    ((ym2612mame)(dicInst.get(enmInstrumentType.YM2612mame)[ChipIndex])).SetMute((byte)chipID, (int)ym2612Mask.get(ChipIndex)[chipID]);
                }
                if (dicInst.containsKey(enmInstrumentType.YM3438))
                {
                    int mask = (int)ym2612Mask.get(ChipIndex)[chipID];
                    if ((mask & 0b0010_0000) == 0) mask &= 0b1011_1111;
                    else mask |= 0b0100_0000;
                   ((ym3438)(dicInst.get(enmInstrumentType.YM3438)[ChipIndex])).OPN2_SetMute((byte)chipID, mask);
                }
            }
        }

        public void setYM2203Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ym2203Mask.get(0)[chipID] |= ch;
                if (!dicInst.containsKey(enmInstrumentType.YM2203)) return;
                ((ym2203)(dicInst.get(enmInstrumentType.YM2203)[0])).YM2203_SetMute((byte)chipID, ym2203Mask.get(0)[chipID]);
            }
        }
        public void setYM2203Mask(int ChipIndex,int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ym2203Mask.get(ChipIndex)[chipID] |= ch;
                if (!dicInst.containsKey(enmInstrumentType.YM2203)) return;
                ((ym2203)(dicInst.get(enmInstrumentType.YM2203)[ChipIndex])).YM2203_SetMute((byte)chipID, ym2203Mask.get(ChipIndex)[chipID]);
            }
        }

        public void setRf5c164Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C164)) return;
                ((scd_pcm)(dicInst.get(enmInstrumentType.RF5C164)[0])).PCM_Chip[chipID].Channel[ch].Muted = 1;
            }
        }

        public void setRf5c164Mask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C164)) return;
                ((scd_pcm)(dicInst.get(enmInstrumentType.RF5C164)[ChipIndex])).PCM_Chip[chipID].Channel[ch].Muted = 1;
            }
        }

        public void setRf5c68Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C68)) return;
                ((rf5c68)(dicInst.get(enmInstrumentType.RF5C68)[0])).RF5C68Data[chipID].chan[ch].Muted = 1;
            }
        }

        public void setRf5c68Mask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C68)) return;
                ((rf5c68)(dicInst.get(enmInstrumentType.RF5C68)[ChipIndex])).RF5C68Data[chipID].chan[ch].Muted = 1;
            }
        }

        public void setC140Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                c140Mask.get(0)[chipID] |= (int)ch;
                if (!dicInst.containsKey(enmInstrumentType.C140)) return;
                ((c140)(dicInst.get(enmInstrumentType.C140)[0])).c140_set_mute_mask((byte)chipID, c140Mask.get(0)[chipID]);
            }
        }

        public void setC140Mask(int ChipIndex,int chipID, int ch)
        {
            synchronized (lockobj)
            {
                c140Mask.get(ChipIndex)[chipID] |= (int)ch;
                if (!dicInst.containsKey(enmInstrumentType.C140)) return;
                ((c140)(dicInst.get(enmInstrumentType.C140)[ChipIndex])).c140_set_mute_mask((byte)chipID, c140Mask.get(ChipIndex)[chipID]);
            }
        }

        public void setSegaPcmMask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                segapcmMask.get(0)[chipID] |= (int)ch;
                if (!dicInst.containsKey(enmInstrumentType.SEGAPCM)) return;
                ((segapcm)(dicInst.get(enmInstrumentType.SEGAPCM)[0])).segapcm_set_mute_mask((byte)chipID, segapcmMask.get(0)[chipID]);
            }
        }

        public void setSegaPcmMask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                segapcmMask.get(ChipIndex)[chipID] |= (int)ch;
                if (!dicInst.containsKey(enmInstrumentType.SEGAPCM)) return;
                ((segapcm)(dicInst.get(enmInstrumentType.SEGAPCM)[ChipIndex])).segapcm_set_mute_mask((byte)chipID, segapcmMask.get(ChipIndex)[chipID]);
            }
        }

        public void setQSoundMask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ch = (1 << ch);
                qsoundMask.get(0)[chipID] |= (int)ch;
                if (dicInst.containsKey(enmInstrumentType.QSound))
                {
                    ((qsound)(dicInst.get(enmInstrumentType.QSound)[0])).qsound_set_mute_mask((byte)chipID, qsoundMask.get(0)[chipID]);
                }
            }
        }

        public void setQSoundMask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ch = (1 << ch);
                qsoundMask.get(ChipIndex)[chipID] |= (int)ch;
                if (!dicInst.containsKey(enmInstrumentType.QSound))
                {
                    ((qsound)(dicInst.get(enmInstrumentType.QSound)[ChipIndex])).qsound_set_mute_mask((byte)chipID, qsoundMask.get(ChipIndex)[chipID]);
                }
            }
        }

        public void setQSoundCtrMask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ch = (1 << ch);
                qsoundCtrMask.get(0)[chipID] |= (int)ch;
                if (dicInst.containsKey(enmInstrumentType.QSoundCtr))
                {
                    ((Qsound_ctr)(dicInst.get(enmInstrumentType.QSoundCtr)[0])).qsound_set_mute_mask((byte)chipID, qsoundCtrMask.get(0)[chipID]);
                }
            }
        }

        public void setQSoundCtrMask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ch = (1 << ch);
                qsoundCtrMask.get(ChipIndex)[chipID] |= (int)ch;
                if (!dicInst.containsKey(enmInstrumentType.QSoundCtr))
                {
                    ((Qsound_ctr)(dicInst.get(enmInstrumentType.QSoundCtr)[ChipIndex])).qsound_set_mute_mask((byte)chipID, qsoundCtrMask.get(ChipIndex)[chipID]);
                }
            }
        }

        public void setOKIM6295Mask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                okim6295Mask.get(ChipIndex)[chipID] |= (int)ch;
                if (!dicInst.containsKey(enmInstrumentType.OKIM6295)) return;
                ((okim6295)(dicInst.get(enmInstrumentType.OKIM6295)[ChipIndex])).okim6295_set_mute_mask((byte)chipID, okim6295Mask.get(ChipIndex)[chipID]);
            }
        }

        public okim6295.okim6295Info GetOKIM6295Info(int ChipIndex, int chipID)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.OKIM6295)) return null;
                return ((okim6295)(dicInst.get(enmInstrumentType.OKIM6295)[ChipIndex])).ReadChInfo((byte)chipID);
            }
        }

        public void setHuC6280Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                huc6280Mask.get(0)[chipID] |= ch;
                if (!dicInst.containsKey(enmInstrumentType.HuC6280)) return;
                ((Ootake_PSG)(dicInst.get(enmInstrumentType.HuC6280)[0])).HuC6280_SetMute((byte)chipID, huc6280Mask.get(0)[chipID]);
            }
        }

        public void setHuC6280Mask(int ChipIndex,int chipID, int ch)
        {
            synchronized (lockobj)
            {
                huc6280Mask.get(ChipIndex)[chipID] |= ch;
                if (!dicInst.containsKey(enmInstrumentType.HuC6280)) return;
                ((Ootake_PSG)(dicInst.get(enmInstrumentType.HuC6280)[ChipIndex])).HuC6280_SetMute((byte)chipID, huc6280Mask.get(ChipIndex)[chipID]);
            }
        }

        public void setNESMask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                nesMask.get(0)[chipID] |= (int)(0x1 << ch);
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return;
                ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[0])).nes_set_mute_mask((byte)chipID, nesMask.get(0)[chipID]);
            }
        }

        public void setNESMask(int ChipIndex,int chipID, int ch)
        {
            synchronized (lockobj)
            {
                nesMask.get(ChipIndex)[chipID] |= (int)(0x1 << ch);
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return;
                ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[ChipIndex])).nes_set_mute_mask((byte)chipID, nesMask.get(ChipIndex)[chipID]);
            }
        }

        public void setFDSMask(int chipID)
        {
            synchronized (lockobj)
            {
                nesMask.get(0)[chipID] |= 0x20;
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return;
                ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[0])).nes_set_mute_mask((byte)chipID, nesMask.get(0)[chipID]);
            }
        }

        public void setFDSMask(int ChipIndex,int chipID)
        {
            synchronized (lockobj)
            {
                nesMask.get(ChipIndex)[chipID] |= 0x20;
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return;
                ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[ChipIndex])).nes_set_mute_mask((byte)chipID, nesMask.get(ChipIndex)[chipID]);
            }
        }



        public void resetSN76489Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                sn76489Mask.get(0)[chipID] |= ch;
                if (!dicInst.containsKey(enmInstrumentType.SN76489)) return;
                ((sn76489)(dicInst.get(enmInstrumentType.SN76489)[0])).SN76489_SetMute((byte)chipID, sn76489Mask.get(0)[chipID]);
            }
        }

        public void resetSN76489Mask(int ChipIndex,int chipID, int ch)
        {
            synchronized (lockobj)
            {
                sn76489Mask.get(ChipIndex)[chipID] |= ch;
                if (!dicInst.containsKey(enmInstrumentType.SN76489)) return;
                ((sn76489)(dicInst.get(enmInstrumentType.SN76489)[ChipIndex])).SN76489_SetMute((byte)chipID, sn76489Mask.get(ChipIndex)[chipID]);
            }
        }


        public void resetYM2612Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ym2612Mask.get(0)[chipID] &= ~(1<<ch);
                if (dicInst.containsKey(enmInstrumentType.YM2612))
                {
                    ((ym2612)(dicInst.get(enmInstrumentType.YM2612)[0])).YM2612_SetMute((byte)chipID, ym2612Mask.get(0)[chipID]);
                }
                if (dicInst.containsKey(enmInstrumentType.YM2612mame))
                {
                    ((ym2612mame)(dicInst.get(enmInstrumentType.YM2612mame)[0])).SetMute((byte)chipID, (int)ym2612Mask.get(0)[chipID]);
                }
                if (dicInst.containsKey(enmInstrumentType.YM3438))
                {
                    int mask = (int)ym2612Mask.get(0)[chipID];
                    if ((mask & 0b0010_0000) == 0) mask &= 0b1011_1111;
                    else mask |= 0b0100_0000;
                    ((ym3438)(dicInst.get(enmInstrumentType.YM3438)[0])).OPN2_SetMute((byte)chipID, mask);
                }
            }
        }

        public void resetYM2612Mask(int ChipIndex,int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ym2612Mask.get(ChipIndex)[chipID] &= ~(1<<ch);
                if (dicInst.containsKey(enmInstrumentType.YM2612))
                {
                    ((ym2612)(dicInst.get(enmInstrumentType.YM2612)[ChipIndex])).YM2612_SetMute((byte)chipID, ym2612Mask.get(ChipIndex)[chipID]);
                }
                if (dicInst.containsKey(enmInstrumentType.YM2612mame))
                {
                    ((ym2612mame)(dicInst.get(enmInstrumentType.YM2612mame)[ChipIndex])).SetMute((byte)chipID, (int)ym2612Mask.get(ChipIndex)[chipID]);
                }
                if (dicInst.containsKey(enmInstrumentType.YM3438))
                {
                    int mask = (int)ym2612Mask.get(ChipIndex)[chipID];
                    if ((mask & 0b0010_0000) == 0) mask &= 0b1011_1111;
                    else mask |= 0b0100_0000;
                    ((ym3438)(dicInst.get(enmInstrumentType.YM3438)[ChipIndex])).OPN2_SetMute((byte)chipID, mask);
                }
            }
        }

        public void resetYM2203Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ym2203Mask.get(0)[chipID] &= ~ch;
                if (!dicInst.containsKey(enmInstrumentType.YM2203)) return;
                ((ym2203)(dicInst.get(enmInstrumentType.YM2203)[0])).YM2203_SetMute((byte)chipID, ym2203Mask.get(0)[chipID]);
            }
        }

        public void resetYM2203Mask(int ChipIndex,int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ym2203Mask.get(ChipIndex)[chipID] &= ~ch;
                if (!dicInst.containsKey(enmInstrumentType.YM2203)) return;
                ((ym2203)(dicInst.get(enmInstrumentType.YM2203)[ChipIndex])).YM2203_SetMute((byte)chipID, ym2203Mask.get(ChipIndex)[chipID]);
            }
        }

        public void resetRf5c164Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C164)) return;
                ((scd_pcm)(dicInst.get(enmInstrumentType.RF5C164)[0])).PCM_Chip[chipID].Channel[ch].Muted = 0;
            }
        }

        public void resetRf5c164Mask(int ChipIndex,int chipID, int ch)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C164)) return;
                ((scd_pcm)(dicInst.get(enmInstrumentType.RF5C164)[ChipIndex])).PCM_Chip[chipID].Channel[ch].Muted = 0;
            }
        }

        public void resetRf5c68Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C68)) return;
                ((rf5c68)(dicInst.get(enmInstrumentType.RF5C68)[0])).RF5C68Data[chipID].chan[ch].Muted = 0;
            }
        }

        public void resetRf5c68Mask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                if (!dicInst.containsKey(enmInstrumentType.RF5C68)) return;
                ((rf5c68)(dicInst.get(enmInstrumentType.RF5C68)[ChipIndex])).RF5C68Data[chipID].chan[ch].Muted = 0;
            }
        }

        public void resetC140Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                c140Mask.get(0)[chipID] &= ~(int)ch;
                if (!dicInst.containsKey(enmInstrumentType.C140)) return;
                ((c140)(dicInst.get(enmInstrumentType.C140)[0])).c140_set_mute_mask((byte)chipID, c140Mask.get(0)[chipID]);
            }
        }

        public void resetC140Mask(int ChipIndex,int chipID, int ch)
        {
            synchronized (lockobj)
            {
                c140Mask.get(ChipIndex)[chipID] &= ~(int)ch;
                if (!dicInst.containsKey(enmInstrumentType.C140)) return;
                ((c140)(dicInst.get(enmInstrumentType.C140)[ChipIndex])).c140_set_mute_mask((byte)chipID, c140Mask.get(ChipIndex)[chipID]);
            }
        }

        public void resetSegaPcmMask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                segapcmMask.get(0)[chipID] &= ~(int)ch;
                if (!dicInst.containsKey(enmInstrumentType.SEGAPCM)) return;
                 ((segapcm)(dicInst.get(enmInstrumentType.SEGAPCM)[0])).segapcm_set_mute_mask((byte)chipID, segapcmMask.get(0)[chipID]);
            }
        }

        public void resetSegaPcmMask(int ChipIndex,int chipID, int ch)
        {
            synchronized (lockobj)
            {
                segapcmMask.get(ChipIndex)[chipID] &= ~(int)ch;
                if (!dicInst.containsKey(enmInstrumentType.SEGAPCM)) return;
                ((segapcm)(dicInst.get(enmInstrumentType.SEGAPCM)[ChipIndex])).segapcm_set_mute_mask((byte)chipID, segapcmMask.get(ChipIndex)[chipID]);
            }
        }

        public void resetQSoundMask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ch = (1 << ch);
                qsoundMask.get(0)[chipID] &= ~(int)ch;
                if (dicInst.containsKey(enmInstrumentType.QSound))
                {
                    ((qsound)(dicInst.get(enmInstrumentType.QSound)[0])).qsound_set_mute_mask((byte)chipID, qsoundMask.get(0)[chipID]);
                }
            }
        }

        public void resetQSoundMask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ch = (1 << ch);
                qsoundMask.get(ChipIndex)[chipID] &= ~(int)ch;
                if (!dicInst.containsKey(enmInstrumentType.QSound))
                {
                    ((qsound)(dicInst.get(enmInstrumentType.QSound)[ChipIndex])).qsound_set_mute_mask((byte)chipID, qsoundMask.get(ChipIndex)[chipID]);
                }
            }
        }

        public void resetQSoundCtrMask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ch = (1 << ch);
                qsoundCtrMask.get(0)[chipID] &= ~(int)ch;
                if (dicInst.containsKey(enmInstrumentType.QSoundCtr))
                {
                    ((Qsound_ctr)(dicInst.get(enmInstrumentType.QSoundCtr)[0])).qsound_set_mute_mask((byte)chipID, qsoundCtrMask.get(0)[chipID]);
                }
            }
        }

        public void resetQSoundCtrMask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                ch = (1 << ch);
                qsoundCtrMask.get(ChipIndex)[chipID] &= ~(int)ch;
                if (!dicInst.containsKey(enmInstrumentType.QSoundCtr))
                {
                    ((Qsound_ctr)(dicInst.get(enmInstrumentType.QSoundCtr)[ChipIndex])).qsound_set_mute_mask((byte)chipID, qsoundCtrMask.get(ChipIndex)[chipID]);
                }
            }
        }

        public void resetOKIM6295Mask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                okim6295Mask.get(ChipIndex)[chipID] &= ~(int)ch;
                if (!dicInst.containsKey(enmInstrumentType.OKIM6295)) return;
                ((okim6295)(dicInst.get(enmInstrumentType.OKIM6295)[ChipIndex])).okim6295_set_mute_mask((byte)chipID, okim6295Mask.get(ChipIndex)[chipID]);
            }
        }

        public void resetHuC6280Mask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                huc6280Mask.get(0)[chipID] &= ~ch;
                if (!dicInst.containsKey(enmInstrumentType.HuC6280)) return;
                ((Ootake_PSG)(dicInst.get(enmInstrumentType.HuC6280)[0])).HuC6280_SetMute((byte)chipID, huc6280Mask.get(0)[chipID]);
            }
        }

        public void resetHuC6280Mask(int ChipIndex,int chipID, int ch)
        {
            synchronized (lockobj)
            {
                huc6280Mask.get(ChipIndex)[chipID] &= ~ch;
                if (!dicInst.containsKey(enmInstrumentType.HuC6280)) return;
                ((Ootake_PSG)(dicInst.get(enmInstrumentType.HuC6280)[ChipIndex])).HuC6280_SetMute((byte)chipID, huc6280Mask.get(ChipIndex)[chipID]);
            }
        }

        public void resetNESMask(int chipID, int ch)
        {
            synchronized (lockobj)
            {
                nesMask.get(0)[chipID] &= (int)~(0x1 << ch);
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return;
                ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[0])).nes_set_mute_mask((byte)chipID, nesMask.get(0)[chipID]);
            }
        }

        public void resetNESMask(int ChipIndex, int chipID, int ch)
        {
            synchronized (lockobj)
            {
                nesMask.get(ChipIndex)[chipID] &= (int)~(0x1 << ch);
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return;
                ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[ChipIndex])).nes_set_mute_mask((byte)chipID, nesMask.get(ChipIndex)[chipID]);
            }
        }

        public void resetFDSMask(int chipID)
        {
            synchronized (lockobj)
            {
                nesMask.get(0)[chipID] &= ~(int)0x20;
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return;
                ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[0])).nes_set_mute_mask((byte)chipID, nesMask.get(0)[chipID]);
            }
        }

        public void resetFDSMask(int ChipIndex,int chipID)
        {
            synchronized (lockobj)
            {
                nesMask.get(ChipIndex)[chipID] &= ~(int)0x20;
                if (!dicInst.containsKey(enmInstrumentType.Nes)) return;
                ((nes_intf)(dicInst.get(enmInstrumentType.Nes)[ChipIndex])).nes_set_mute_mask((byte)chipID, nesMask.get(ChipIndex)[chipID]);
            }
        }



        public int[][][] getYM2151VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2151)) return null;
            return (dicInst.get(enmInstrumentType.YM2151)[0]).visVolume;
        }

        public int[][][] getYM2151VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2151)) return null;
            return (dicInst.get(enmInstrumentType.YM2151)[ChipIndex]).visVolume;
        }

        public int[][][] getYM2203VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2203)) return null;
            return ((ym2203)dicInst.get(enmInstrumentType.YM2203)[0]).visVolume;
        }

        public int[][][] getYM2203VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2203)) return null;
            return ((ym2203)dicInst.get(enmInstrumentType.YM2203)[ChipIndex]).visVolume;
        }

        public int[][][] getYM2413VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2413)) return null;
            return ((ym2413)dicInst.get(enmInstrumentType.YM2413)[0]).visVolume;
        }

        public int[][][] getYM2413VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2413)) return null;
            return ((ym2413)dicInst.get(enmInstrumentType.YM2413)[ChipIndex]).visVolume;
        }

        public int[][][] getYM2608VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2608)) return null;
            return ((ym2608)dicInst.get(enmInstrumentType.YM2608)[0]).visVolume;
        }

        public int[][][] getYM2608VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2608)) return null;
            return ((ym2608)dicInst.get(enmInstrumentType.YM2608)[ChipIndex]).visVolume;
        }

        public int[][][] getYM2609VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2609)) return null;
            return ((ym2608)dicInst.get(enmInstrumentType.YM2609)[0]).visVolume;
        }

        public int[][][] getYM2609VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2609)) return null;
            return ((ym2608)dicInst.get(enmInstrumentType.YM2609)[ChipIndex]).visVolume;
        }

        public int[][][] getYM2610VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2610)) return null;
            return ((ym2610)dicInst.get(enmInstrumentType.YM2610)[0]).visVolume;
        }

        public int[][][] getYM2610VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2610)) return null;
            return ((ym2610)dicInst.get(enmInstrumentType.YM2610)[ChipIndex]).visVolume;
        }

        public int[][][] getYM2612VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2612))
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2612mame)) return null;
                return (dicInst.get(enmInstrumentType.YM2612mame)[0]).visVolume;
            }
            return (dicInst.get(enmInstrumentType.YM2612)[0]).visVolume;
        }

        public int[][][] getYM2612VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM2612))
            {
                if (!dicInst.containsKey(enmInstrumentType.YM2612mame)) return null;
                return (dicInst.get(enmInstrumentType.YM2612mame)[ChipIndex]).visVolume;
            }
            return (dicInst.get(enmInstrumentType.YM2612)[ChipIndex]).visVolume;
        }

        public int[][][] getSN76489VisVolume()
        {
            if (dicInst.containsKey(enmInstrumentType.SN76489))
            {
                return ((sn76489)dicInst.get(enmInstrumentType.SN76489)[0]).visVolume;
            }
            else if (dicInst.containsKey(enmInstrumentType.SN76496))
            {
                return ((SN76496)dicInst.get(enmInstrumentType.SN76496)[0]).visVolume;
            }
            return null;
        }

        public int[][][] getSN76489VisVolume(int ChipIndex)
        {
            if (dicInst.containsKey(enmInstrumentType.SN76489))
            {
                return ((sn76489)dicInst.get(enmInstrumentType.SN76489)[ChipIndex]).visVolume;
            }
            else if (dicInst.containsKey(enmInstrumentType.SN76496))
            {
                return ((SN76496)dicInst.get(enmInstrumentType.SN76496)[ChipIndex]).visVolume;
            }
            return null;
        }

        public int[][][] getHuC6280VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.HuC6280)) return null;
            return ((Ootake_PSG)dicInst.get(enmInstrumentType.HuC6280)[0]).visVolume;
        }

        public int[][][] getHuC6280VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.HuC6280)) return null;
            return ((Ootake_PSG)dicInst.get(enmInstrumentType.HuC6280)[ChipIndex]).visVolume;
        }

        public int[][][] getRF5C164VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.RF5C164)) return null;
            return ((scd_pcm)dicInst.get(enmInstrumentType.RF5C164)[0]).visVolume;
        }

        public int[][][] getRF5C164VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.RF5C164)) return null;
            return ((scd_pcm)dicInst.get(enmInstrumentType.RF5C164)[ChipIndex]).visVolume;
        }

        public int[][][] getPWMVisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.PWM)) return null;
            return ((pwm)dicInst.get(enmInstrumentType.PWM)[0]).visVolume;
        }

        public int[][][] getPWMVisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.PWM)) return null;
            return ((pwm)dicInst.get(enmInstrumentType.PWM)[ChipIndex]).visVolume;
        }

        public int[][][] getOKIM6258VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.OKIM6258)) return null;
            return ((okim6258)dicInst.get(enmInstrumentType.OKIM6258)[0]).visVolume;
        }

        public int[][][] getOKIM6258VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.OKIM6258)) return null;
            return ((okim6258)dicInst.get(enmInstrumentType.OKIM6258)[ChipIndex]).visVolume;
        }

        public int[][][] getOKIM6295VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.OKIM6295)) return null;
            return ((okim6295)dicInst.get(enmInstrumentType.OKIM6295)[0]).visVolume;
        }

        public int[][][] getOKIM6295VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.OKIM6295)) return null;
            return ((okim6295)dicInst.get(enmInstrumentType.OKIM6295)[ChipIndex]).visVolume;
        }

        public int[][][] getC140VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.C140)) return null;
            return ((c140)dicInst.get(enmInstrumentType.C140)[0]).visVolume;
        }

        public int[][][] getC140VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.C140)) return null;
            return ((c140)dicInst.get(enmInstrumentType.C140)[ChipIndex]).visVolume;
        }

        public int[][][] getSegaPCMVisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.SEGAPCM)) return null;
            return ((segapcm)dicInst.get(enmInstrumentType.SEGAPCM)[0]).visVolume;
        }

        public int[][][] getSegaPCMVisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.SEGAPCM)) return null;
            return ((segapcm)dicInst.get(enmInstrumentType.SEGAPCM)[ChipIndex]).visVolume;
        }

        public int[][][] getC352VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.C352)) return null;
            return ((c352)dicInst.get(enmInstrumentType.C352)[0]).visVolume;
        }

        public int[][][] getC352VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.C352)) return null;
            return ((c352)dicInst.get(enmInstrumentType.C352)[ChipIndex]).visVolume;
        }

        public int[][][] getK051649VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.K051649)) return null;
            return ((K051649)dicInst.get(enmInstrumentType.K051649)[0]).visVolume;
        }

        public int[][][] getK051649VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.K051649)) return null;
            return ((K051649)dicInst.get(enmInstrumentType.K051649)[ChipIndex]).visVolume;
        }

        public int[][][] getK054539VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.K054539)) return null;
            return ((K054539)dicInst.get(enmInstrumentType.K054539)[0]).visVolume;
        }

        public int[][][] getK054539VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.K054539)) return null;
            return ((K054539)dicInst.get(enmInstrumentType.K054539)[ChipIndex]).visVolume;
        }




        public int[][][] getNESVisVolume()
        {
            return null;
            //if (!dicInst.containsKey(enmInstrumentType.Nes)) return null;
            //return dicInst.get(enmInstrumentType.Nes).visVolume;
        }

        public int[][][] getDMCVisVolume()
        {
            return null;
            //if (!dicInst.containsKey(enmInstrumentType.DMC)) return null;
            //return dicInst.get(enmInstrumentType.DMC).visVolume;
        }

        public int[][][] getFDSVisVolume()
        {
            return null;
            //if (!dicInst.containsKey(enmInstrumentType.FDS)) return null;
            //return dicInst.get(enmInstrumentType.FDS).visVolume;
        }

        public int[][][] getMMC5VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.MMC5)) return null;
            return dicInst.get(enmInstrumentType.MMC5)[0].visVolume;
        }

        public int[][][] getMMC5VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.MMC5)) return null;
            return dicInst.get(enmInstrumentType.MMC5)[ChipIndex].visVolume;
        }

        public int[][][] getN160VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.N160)) return null;
            return dicInst.get(enmInstrumentType.N160)[0].visVolume;
        }

        public int[][][] getN160VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.N160)) return null;
            return dicInst.get(enmInstrumentType.N160)[ChipIndex].visVolume;
        }

        public int[][][] getVRC6VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.VRC6)) return null;
            return dicInst.get(enmInstrumentType.VRC6)[0].visVolume;
        }

        public int[][][] getVRC6VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.VRC6)) return null;
            return dicInst.get(enmInstrumentType.VRC6)[ChipIndex].visVolume;
        }

        public int[][][] getVRC7VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.VRC7)) return null;
            return dicInst.get(enmInstrumentType.VRC7)[0].visVolume;
        }

        public int[][][] getVRC7VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.VRC7)) return null;
            return dicInst.get(enmInstrumentType.VRC7)[ChipIndex].visVolume;
        }

        public int[][][] getFME7VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.FME7)) return null;
            return dicInst.get(enmInstrumentType.FME7)[0].visVolume;
        }

        public int[][][] getFME7VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.FME7)) return null;
            return dicInst.get(enmInstrumentType.FME7)[ChipIndex].visVolume;
        }

        public int[][][] getYM3526VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.YM3526)) return null;
            return dicInst.get(enmInstrumentType.YM3526)[0].visVolume;
        }

        public int[][][] getYM3526VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM3526)) return null;
            return dicInst.get(enmInstrumentType.YM3526)[ChipIndex].visVolume;
        }

        public int[][][] getY8950VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.Y8950)) return null;
            return dicInst.get(enmInstrumentType.Y8950)[0].visVolume;
        }

        public int[][][] getY8950VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.Y8950)) return null;
            return dicInst.get(enmInstrumentType.Y8950)[ChipIndex].visVolume;
        }

        public int[][][] getYM3812VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.YM3812)) return null;
            return dicInst.get(enmInstrumentType.YM3812)[0].visVolume;
        }

        public int[][][] getYM3812VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.YM3812)) return null;
            return dicInst.get(enmInstrumentType.YM3812)[ChipIndex].visVolume;
        }

        public int[][][] getYMF262VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.YMF262)) return null;
            return dicInst.get(enmInstrumentType.YMF262)[0].visVolume;
        }

        public int[][][] getYMF262VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.YMF262)) return null;
            return dicInst.get(enmInstrumentType.YMF262)[ChipIndex].visVolume;
        }

        public int[][][] getYMF278BVisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.YMF278B)) return null;
            return dicInst.get(enmInstrumentType.YMF278B)[0].visVolume;
        }

        public int[][][] getYMF278BVisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.YMF278B)) return null;
            return dicInst.get(enmInstrumentType.YMF278B)[ChipIndex].visVolume;
        }

        public int[][][] getYMZ280BVisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.YMZ280B)) return null;
            return dicInst.get(enmInstrumentType.YMZ280B)[0].visVolume;
        }

        public int[][][] getYMZ280BVisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.YMZ280B)) return null;
            return dicInst.get(enmInstrumentType.YMZ280B)[ChipIndex].visVolume;
        }

        public int[][][] getYMF271VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.YMF271)) return null;
            return dicInst.get(enmInstrumentType.YMF271)[0].visVolume;
        }

        public int[][][] getYMF271VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.YMF271)) return null;
            return dicInst.get(enmInstrumentType.YMF271)[ChipIndex].visVolume;
        }

        public int[][][] getRF5C68VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.RF5C68)) return null;
            return dicInst.get(enmInstrumentType.RF5C68)[0].visVolume;
        }

        public int[][][] getRF5C68VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.RF5C68)) return null;
            return dicInst.get(enmInstrumentType.RF5C68)[ChipIndex].visVolume;
        }

        public int[][][] getMultiPCMVisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.MultiPCM)) return null;
            return dicInst.get(enmInstrumentType.MultiPCM)[0].visVolume;
        }
        public int[][][] getMultiPCMVisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.MultiPCM)) return null;
            return dicInst.get(enmInstrumentType.MultiPCM)[ChipIndex].visVolume;
        }

        public int[][][] getK053260VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.K053260)) return null;
            return dicInst.get(enmInstrumentType.K053260)[0].visVolume;
        }
        public int[][][] getK053260VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.K053260)) return null;
            return dicInst.get(enmInstrumentType.K053260)[ChipIndex].visVolume;
        }

        public int[][][] getQSoundVisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.QSound)) return null;
            return dicInst.get(enmInstrumentType.QSound)[0].visVolume;
        }
        public int[][][] getQSoundVisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.QSound)) return null;
            return dicInst.get(enmInstrumentType.QSound)[ChipIndex].visVolume;
        }

        public int[][][] getQSoundCtrVisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.QSoundCtr)) return null;
            return dicInst.get(enmInstrumentType.QSoundCtr)[0].visVolume;
        }
        public int[][][] getQSoundCtrVisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.QSoundCtr)) return null;
            return dicInst.get(enmInstrumentType.QSoundCtr)[ChipIndex].visVolume;
        }

        public int[][][] getGA20VisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.GA20)) return null;
            return dicInst.get(enmInstrumentType.GA20)[0].visVolume;
        }
        public int[][][] getGA20VisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.GA20)) return null;
            return dicInst.get(enmInstrumentType.GA20)[ChipIndex].visVolume;
        }

        public int[][][] getDMGVisVolume()
        {
            if (!dicInst.containsKey(enmInstrumentType.DMG)) return null;
            return dicInst.get(enmInstrumentType.DMG)[0].visVolume;
        }

        public int[][][] getDMGVisVolume(int ChipIndex)
        {
            if (!dicInst.containsKey(enmInstrumentType.DMG)) return null;
            return dicInst.get(enmInstrumentType.DMG)[ChipIndex].visVolume;
        }



        /// <summary>
        /// Left全体ボリュームの取得(視覚効果向け)
        /// </summary>
        public int getTotalVolumeL()
        {
            synchronized (lockobj)
            {
                int v = 0;
                for (int i = 0; i < buffer[0].length; i++)
                {
                    v = Math.max(v,Math.abs(buffer[0][i]));
                }
                return v;
            }
        }

        /// <summary>
        /// Right全体ボリュームの取得(視覚効果向け)
        /// </summary>
        public int getTotalVolumeR()
        {
            synchronized (lockobj)
            {
                int v = 0;
                for (int i = 0; i < buffer[1].length; i++)
                {
                    v = Math.max(v,Math.abs(buffer[1][i]));
                }
                return v;
            }
        }



        public void setIncFlag()
        {
            synchronized (lockobj)
            {
                incFlag = true;
            }
        }

        public void resetIncFlag()
        {
            synchronized (lockobj)
            {
                incFlag = false;
            }
        }

    }

