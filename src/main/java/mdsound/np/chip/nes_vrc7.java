package mdsound.np.chip;
import mdsound.MDSound;
import mdsound.Common;
import mdsound.np.IDevice.ISoundChip;
import mdsound.np.chip.IDeviceInfo.ITrackInfo;
import mdsound.np.chip.IDeviceInfo.TrackInfoBasic;

    public class nes_vrc7 implements ISoundChip
    {
        protected int mask;
        protected int patch_set;
        protected int[][] sm = new int[][] { new int[6], new int[6] }; // stereo mix
        protected short[] buf = new short[2];
        protected Emu2413.OPLL opll;
        protected Emu2413 emu2413 = new Emu2413();
        protected int divider; // clock divider
        protected double clock, rate;
        protected TrackInfoBasic[] trkinfo = new TrackInfoBasic[6];

        public nes_vrc7()
        {
            patch_set = (int)Emu2413.OPLL_TONE_ENUM.OPLL_VRC7_RW_TONE.ordinal();

            opll = emu2413.OPLL_new(3579545, (int)Common.SampleRate);
            emu2413.OPLL_reset_patch(opll, (int)patch_set);
            SetClock(Common.NsfClock);// DEFAULT_CLOCK);

            for (int c = 0; c < 2; ++c)
                for (int t = 0; t < 6; ++t)
                    sm[c][t] = 128;
        }

        protected void finalinze()
        {
            opll = null;
            //OPLL_delete(opll);
        }

        public void SetPatchSet(int p)
        {
            patch_set = p;
        }

        @Override public void SetClock(double c)
        {
            clock = c / 36;
        }

        @Override public void SetRate(double r)
        {
            //rate = r ? r : DEFAULT_RATE;
            //(void)r; // rate is ignored
            rate = 49716;
            emu2413.OPLL_set_quality(opll, 1); // quality always on (not really a CPU hog)
            emu2413.OPLL_set_rate(opll, (int)rate);
        }

        @Override public void Reset()
        {
            for (int i = 0; i < 0x40; ++i)
            {
                Write(0x9010, i);
                Write(0x9030, 0);
            }

            divider = 0;
            emu2413.OPLL_reset_patch(opll, (int)patch_set);
            emu2413.OPLL_reset(opll);
        }

        @Override public void SetStereoMix(int trk, short mixl, short mixr)
        {
            if (trk < 0) return;
            if (trk > 5) return;
            sm[0][trk] = mixl;
            sm[1][trk] = mixr;
        }

        public ITrackInfo GetTrackInfo(int trk)
        {
            if (opll != null && trk < 6)
            {
                trkinfo[trk].max_volume = 15;
                trkinfo[trk].volume = 15 - ((opll.reg[0x30 + trk]) & 15);
                trkinfo[trk]._freq = (int)(opll.reg[0x10 + trk] + ((opll.reg[0x20 + trk] & 1) << 8));
                int blk = (opll.reg[0x20 + trk] >> 1) & 7;
                trkinfo[trk].freq = clock * trkinfo[trk]._freq / (double)(0x80000 >> blk);
                trkinfo[trk].tone = (opll.reg[0x30 + trk] >> 4) & 15;
                trkinfo[trk].key = (opll.reg[0x20 + trk] & 0x10) != 0 ? true : false;
                return trkinfo[trk];
            }
            else
                return null;
        }

        public byte[] GetVRC7regs()
        {
            return opll.reg;
        }

        public class ChipKeyInfo
        {
            public boolean[] On = null;
            public boolean[] Off = null;

            public ChipKeyInfo(int n)
            {
                On = new boolean[n];
                Off = new boolean[n];
                //for (int i = 0; i < n; i++) Off[i] = true;
            }
        }

        private ChipKeyInfo ki = new ChipKeyInfo(6);
        private ChipKeyInfo kiRet = new ChipKeyInfo(6);

        public ChipKeyInfo getVRC7KeyInfo(int chipID)
        {
            for (int ch = 0; ch < 6; ch++)
            {
                kiRet.Off[ch] = ki.Off[ch];
                kiRet.On[ch] = ki.On[ch];
                ki.On[ch] = false;
            }
            return kiRet;
        }

        @Override public boolean Write(int adr, int val, int id/* = 0*/)
        {
            if (adr == 0x9010)
            {
                emu2413.OPLL_writeIO(opll, 0, val);
                return true;
            }
            if (adr == 0x9030)
            {
                emu2413.OPLL_writeIO(opll, 1, val);
                if (opll.adr >= 0x20 && opll.adr<=0x25)
                {
                    int ch = opll.adr - 0x20;
                    int k = val & 0x10;
                    if (k == 0)
                    {
                        ki.Off[ch] = true;
                    }
                    else
                    {
                        if(ki.Off[ch]) ki.On[ch] = true;
                        ki.Off[ch] = false;
                    }
                }
                return true;
            }
            else
                return false;
        }

        @Override public boolean Read(int adr, int val, int id/* = 0*/)
        {
            return false;
        }

        @Override public void Tick(int clocks)
        {
            divider += clocks;
            while (divider >= 36)
            {
                divider -= 36;
                emu2413.OPLL_calc(opll);
            }
        }

        @Override public int Render(int[] b)//b[2])
        {
            b[0] = b[1] = 0;
            for (int i = 0; i < 6; ++i)
            {
                int val = (mask & (1 << i)) != 0 ? 0 : opll.slot[(i << 1) | 1].output[1];
                b[0] += val * sm[0][i];
                b[1] += val * sm[1][i];
            }
            b[0] >>= (7 - 4);
            b[1] >>= (7 - 4);

            // master volume adjustment
            final int MASTER = (int)(0.8 * 256.0);
            b[0] = (b[0] * MASTER) >> 8;
            b[1] = (b[1] * MASTER) >> 8;

            MDSound.np_nes_vrc7_volume = Math.abs(b[0]);

            return 2;
        }


        @Override public void SetMask(int m)
        {
            mask = m;
            if (opll != null) emu2413.OPLL_setMask(opll, (int)m);
        }

        @Override public void SetOption(int id, int val)
        {
            throw new UnsupportedOperationException();
        }
    }

