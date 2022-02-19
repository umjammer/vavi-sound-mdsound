package MDSound.np.chip;
import MDSound.MDSound;
import MDSound.np.IDevice.ISoundChip;
import MDSound.np.chip.IDeviceInfo.ITrackInfo;
import MDSound.np.chip.IDeviceInfo.TrackInfoBasic;

    public class nes_fme7 implements ISoundChip
    {
        protected int[][] sm=new int[][]{new int[3],new int[3] }; // stereo mix
        protected short[] buf = new short[2];
        protected emu2149.PSG psg;
        protected emu2149 emu2149;
        protected int divider; // clock divider
        protected double clock, rate;
        protected TrackInfoBasic[] trkinfo = new TrackInfoBasic[5];
        protected final int DIVIDER = 8*2;
        public final double DEFAULT_CLOCK = 1789772.0;
        public final int DEFAULT_RATE = 44100;

        public nes_fme7()
        {
            emu2149 = new emu2149();
            psg = emu2149.PSG_new((int)DEFAULT_CLOCK, DEFAULT_RATE);

            for (int c = 0; c < 2; ++c)
                for (int t = 0; t < 3; ++t)
                    sm[c][t] = 128;
        }

        protected void finalinze()
        {
            if (psg != null)
                psg = null;
        }

        @Override public void SetClock(double c)
        {
            this.clock = c * 2.0;
        }

        @Override public void SetRate(double r)
        {
            //rate = r ? r : DEFAULT_RATE;
            rate = DEFAULT_CLOCK / (double)DIVIDER; // TODO rewrite PSG to integrate with clock
            if (psg!=null)
                emu2149.PSG_set_rate(psg, (int)rate);
        }

        @Override public void Reset()
        {
            for (int i = 0; i < 16; ++i) // blank all registers
            {
                Write(0xC000, (int)i);
                Write(0xE000, 0);
            }
            Write(0xC000, 0x07); // disable all tones
            Write(0xE000, 0x3F);

            divider = 0;
            if (psg != null)
                emu2149.PSG_reset(psg);
        }

        @Override public boolean Write(int adr, int val, int id/*=0*/)
        {
            if (adr == 0xC000)
            {
                if (psg != null)
                    emu2149.PSG_writeIO(psg, 0, val);
                return true;
            }
            if (adr == 0xE000)
            {
                if (psg != null)
                    emu2149.PSG_writeIO(psg, 1, val);
                return true;
            }
            else
                return false;
        }

        @Override public boolean Read(int adr, int val, int id/* = 0*/)
        {
            val = emu2149.PSG_readReg(psg, adr);
            return false;
        }

        @Override public void Tick(int clocks)
        {
            divider += (int)clocks;
            while (divider >= DIVIDER)
            {
                divider -= DIVIDER;
                if (psg!=null) emu2149.PSG_calc(psg);
            }
        }

        @Override public int Render(int[] b)//b[2])
        {
            b[0] = b[1] = 0;

            for (int i = 0; i < 3; ++i)
            {
                // note negative polarity
                b[0] -= psg.cout[i] * sm[0][i];
                b[1] -= psg.cout[i] * sm[1][i];
            }
            b[0] >>= (7 - 4);
            b[1] >>= (7 - 4);

            // master volume adjustment
            final int MASTER = (int)(0.64 * 256.0);
            b[0] = (b[0] * MASTER) >> 8;
            b[1] = (b[1] * MASTER) >> 8;

            MDSound.np_nes_fme7_volume = Math.abs(b[0]);

            return 2;
        }

        @Override public void SetStereoMix(int trk, short mixl, short mixr)
        {
            if (trk < 0) return;
            if (trk > 2) return;
            sm[0][trk] = mixl;
            sm[1][trk] = mixr;
        }

        public ITrackInfo GetTrackInfo(int trk)
        {
            //assert(trk < 5);

            if (psg!=null)
            {
                if (trk < 3)
                {
                    trkinfo[trk]._freq = psg.freq[trk];
                    if (psg.freq[trk]!=0)
                        trkinfo[trk].freq = psg.clk / 32.0 / psg.freq[trk];
                    else
                        trkinfo[trk].freq = 0;

                    trkinfo[trk].output = psg.cout[trk];
                    trkinfo[trk].max_volume = 15;
                    trkinfo[trk].volume = (int)(psg.volume[trk] >> 1);
                    //trkinfo[trk].key = (psg.cout[trk]>0)?true:false;
                    trkinfo[trk].key = ((~(psg.tmask[trk])) & 1) != 0;
                    trkinfo[trk].tone = (psg.tmask[trk]!=0 ? 2 : 0) + (psg.nmask[trk]!=0 ? 1 : 0);
                }
                else if (trk == 3) // envelope
                {
                    trkinfo[trk]._freq = psg.env_freq;
                    if (psg.env_freq!=0)
                        trkinfo[trk].freq = psg.clk / 512.0 / psg.env_freq;
                    else
                        trkinfo[trk].freq = 0;

                    if (psg.env_continue!=0 && psg.env_alternate!=0 && psg.env_hold==0) // triangle wave
                    {
                        trkinfo[trk].freq *= 0.5f; // sounds an octave down
                    }

                    trkinfo[trk].output = (int)psg.voltbl[psg.env_ptr];
                    trkinfo[trk].max_volume = 0;
                    trkinfo[trk].volume = 0;
                    trkinfo[trk].key = (((psg.volume[0] | psg.volume[1] | psg.volume[2]) & 32) != 0);
                    trkinfo[trk].tone =
                        (psg.env_continue!=0 ? 8 : 0) |
                        (psg.env_attack!=0 ? 4 : 0) |
                        (psg.env_alternate!=0 ? 2 : 0) |
                        (psg.env_hold!=0 ? 1 : 0);
                }
                else if (trk == 4) // noise
                {
                    trkinfo[trk]._freq = psg.noise_freq >> 1;
                    if (trkinfo[trk]._freq > 0)
                        trkinfo[trk].freq = psg.clk / 16.0 / psg.noise_freq;
                    else
                        trkinfo[trk].freq = 0;

                    trkinfo[trk].output = (int)(psg.noise_seed & 1);
                    trkinfo[trk].max_volume = 0;
                    trkinfo[trk].volume = 0;
                    //trkinfo[trk].key = ((psg.nmask[0]&psg.nmask[1]&psg.nmask[2]) == 0);
                    trkinfo[trk].key = false;
                    trkinfo[trk].tone = 0;
                }
            }
            return trkinfo[trk];
        }

        @Override public void SetMask(int mask)
        {
            if (psg!=null) emu2149.PSG_setMask(psg, (int)mask);
        }

        @Override public void SetOption(int id, int val)
        {
            throw new UnsupportedOperationException();
        }

    }

