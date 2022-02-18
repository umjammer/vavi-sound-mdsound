package MDSound.np.chip;
import MDSound.MDSound;
import MDSound.np.IDevice.ISoundChip;
import MDSound.np.cpu.km6502;

    public class nes_mmc5 implements ISoundChip
    {
        public final double DEFAULT_CLOCK = 1789772.0;
        public final int DEFAULT_RATE = 44100;

        public enum OPT
        {
            NONLINEAR_MIXER
            , PHASE_REFRESH
            , END
        };

        protected int[] option=new int[(int)OPT.END.ordinal()];
        protected int mask;
        protected int[][] sm=new int[][] { new int[3], new int[3] }; // stereo panning
    protected byte[] ram=new byte[0x6000 - 0x5c00];
        protected byte[] reg=new byte[8];
        protected byte[] mreg=new byte[2];
        public byte pcm; // PCM channel
        public boolean pcm_mode; // PCM channel
        protected km6502 cpu; // PCM channel reads need CPU access

        protected int[] scounter=new int[2];            // frequency divider
        protected int[] sphase=new int[2];              // phase counter

        protected int[] duty=new int[2];
        protected int[] volume = new int[2];
        protected int[] freq = new int[2];
        protected int[] _out = new int[3];
    protected boolean[] enable = new boolean[2];

        protected boolean[] envelope_disable = new boolean[2];   // エンベロープ有効フラグ
        protected boolean[] envelope_loop = new boolean[2];      // エンベロープループ
        protected boolean[] envelope_write = new boolean[2];
        protected int[] envelope_div_period = new int[2];
        protected int[] envelope_div = new int[2];
        protected int[] envelope_counter = new int[2];

        protected int[] length_counter = new int[2];

        protected int frame_sequence_count;

        protected double clock, rate;
        protected int[] square_table = new int[32];
        protected int[] pcm_table = new int[256];
        protected TrackInfoBasic[] trkinfo = new TrackInfoBasic[3];

        public nes_mmc5()
        {
            cpu = null;
            SetClock(DEFAULT_CLOCK);
            SetRate(DEFAULT_RATE);
            option[(int)OPT.NONLINEAR_MIXER.ordinal()] = 1;//true;
            option[(int)OPT.PHASE_REFRESH.ordinal()] = 1;//true;
            frame_sequence_count = 0;

            // square nonlinear mix, same as 2A03
            square_table[0] = 0;
            for (int i = 1; i < 32; i++)
                square_table[i] = (int)((8192.0 * 95.88) / (8128.0 / i + 100));

            // 2A03 style nonlinear pcm mix with double the bits
            //pcm_table[0] = 0;
            //int wd = 22638;
            //for(int d=1;d<256; ++d)
            //    pcm_table[d] = (int)((8192.0*159.79)/(100.0+1.0/((double)d/wd)));

            // linear pcm mix (actual hardware seems closer to this)
            pcm_table[0] = 0;
            double pcm_scale = 32.0;
            for (int d = 1; d < 256; ++d)
                pcm_table[d] = (int)((double)(d) * pcm_scale);

            // stereo mix
            for (int c = 0; c < 2; ++c)
                for (int t = 0; t < 3; ++t)
                    sm[c][t] = 128;
        }

        protected void finalinze()
        {
        }

        @Override public void Reset()
        {
            int i;

            scounter[0] = 0;
            scounter[1] = 0;
            sphase[0] = 0;
            sphase[1] = 0;

            envelope_div[0] = 0;
            envelope_div[1] = 0;
            length_counter[0] = 0;
            length_counter[1] = 0;
            envelope_counter[0] = 0;
            envelope_counter[1] = 0;
            frame_sequence_count = 0;

            for (i = 0; i < 8; i++)
                Write((int)(0x5000 + i), 0);

            Write(0x5015, 0);

            for (i = 0; i < 3; ++i) _out[i] = 0;

            mask = 0;
            pcm = 0; // PCM channel
            pcm_mode = false; // write mode

            SetRate(rate);
        }

        @Override public void SetOption(int id, int val)
        {
            if (id < (int)OPT.END.ordinal()) option[id] = val;
        }

        @Override public void SetClock(double c)
        {
            this.clock = c;
        }

        @Override public void SetRate(double r)
        {
            rate = r!=0 ? r : DEFAULT_RATE;
        }

        public void FrameSequence()
        {
            // 240hz clock
            for (int i = 0; i < 2; ++i)
            {
                boolean divider = false;
                if (envelope_write[i])
                {
                    envelope_write[i] = false;
                    envelope_counter[i] = 15;
                    envelope_div[i] = 0;
                }
                else
                {
                    ++envelope_div[i];
                    if (envelope_div[i] > envelope_div_period[i])
                    {
                        divider = true;
                        envelope_div[i] = 0;
                    }
                }
                if (divider)
                {
                    if (envelope_loop[i] && envelope_counter[i] == 0)
                        envelope_counter[i] = 15;
                    else if (envelope_counter[i] > 0)
                        --envelope_counter[i];
                }
            }

            // MMC5 length counter is clocked at 240hz, unlike 2A03
            for (int i = 0; i < 2; ++i)
            {
                if (!envelope_loop[i] && (length_counter[i] > 0))
                    --length_counter[i];
            }
        }

        private short[][] sqrtbl = new short[][] {
      new short[]{0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
      new short[]{0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
      new short[]{0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0},
      new short[]{1, 1, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}
        };

        private int calc_sqr(int i, int clocks)
        {

            scounter[i] += clocks;
            while (scounter[i] > freq[i])
            {
                sphase[i] = (sphase[i] + 1) & 15;
                scounter[i] -= (freq[i] + 1);
            }

            int ret = 0;
            if (length_counter[i] > 0)
            {
                // note MMC5 does not silence the highest 8 frequencies like APU,
                // because this is done by the sweep unit.

                int v = (int)(envelope_disable[i] ? (int)volume[i] : envelope_counter[i]);
                ret = sqrtbl[duty[i]][sphase[i]] != 0 ? v : 0;
            }

            return ret;
        }

        public void TickFrameSequence(int clocks)
        {
            frame_sequence_count += (int)clocks;
            while (frame_sequence_count > 7458)
            {
                FrameSequence();
                frame_sequence_count -= 7458;
            }
        }

        @Override public void Tick(int clocks)
        {
            _out[0] = calc_sqr(0, clocks);
            _out[1] = calc_sqr(1, clocks);
            _out[2] = pcm;
        }

        private int[] m = new int[3];

        @Override public int Render(int[] b)//b[2])
        {
            _out[0] = (mask & 1) != 0 ? 0 : _out[0];
            _out[1] = (mask & 2) != 0 ? 0 : _out[1];
            _out[2] = (mask & 4) != 0 ? 0 : _out[2];

            if (option[(int)OPT.NONLINEAR_MIXER.ordinal()] != 0)
            {
                // squares nonlinear
                int voltage = square_table[_out[0] + _out[1]];
                m[0] = _out[0] << 6;
                m[1] = _out[1] << 6;
                int _ref = m[0] + m[1];
                if (_ref > 0)
                {
                    m[0] = (m[0] * voltage) / _ref;
                    m[1] = (m[1] * voltage) / _ref;
                }
                else
                {
                    m[0] = voltage;
                    m[1] = voltage;
                }

                // pcm nonlinear
                m[2] = pcm_table[_out[2]];
            }
            else
            {
                // squares
                m[0] = _out[0] << 6;
                m[1] = _out[1] << 6;

                // pcm channel
                m[2] = _out[2] << 5;
            }

            // note polarity is flipped on output

            b[0] = m[0] * -sm[0][0];
            b[0] += m[1] * -sm[0][1];
            b[0] += m[2] * -sm[0][2];
            b[0] >>= 7;

            b[1] = m[0] * -sm[1][0];
            b[1] += m[1] * -sm[1][1];
            b[1] += m[2] * -sm[1][2];
            b[1] >>= 7;

            MDSound.np_nes_mmc5_volume = Math.abs(b[0]);

            return 2;
        }

        public byte[] length_table = new byte[] {
        0x0A, (byte) 0xFE,
        0x14, 0x02,
        0x28, 0x04,
        0x50, 0x06,
        (byte) 0xA0, 0x08,
        0x3C, 0x0A,
        0x0E, 0x0C,
        0x1A, 0x0E,
        0x0C, 0x10,
        0x18, 0x12,
        0x30, 0x14,
        0x60, 0x16,
        (byte) 0xC0, 0x18,
        0x48, 0x1A,
        0x10, 0x1C,
        0x20, 0x1E
        };

        @Override public boolean Write(int adr, int val, int id /*= 0*/)
        {
            int ch;

            if ((0x5c00 <= adr) && (adr < 0x5ff0))
            {
                ram[adr & 0x3ff] = (byte)val;
                return true;
            }
            else if ((0x5000 <= adr) && (adr < 0x5008))
            {
                reg[adr & 0x7] = (byte)val;
            }

            switch (adr)
            {
                case 0x5000:
                case 0x5004:
                    ch = (int)((adr >> 2) & 1);
                    volume[ch] = val & 15;
                    envelope_disable[ch] = ((val >> 4) & 1) != 0;
                    envelope_loop[ch] = ((val >> 5) & 1) != 0;
                    envelope_div_period[ch] = (int)((val & 15));
                    duty[ch] = (val >> 6) & 3;
                    break;

                case 0x5002:
                case 0x5006:
                    ch = (int)((adr >> 2) & 1);
                    freq[ch] = val + (freq[ch] & 0x700);
                    if (scounter[ch] > freq[ch]) scounter[ch] = freq[ch];
                    break;

                case 0x5003:
                case 0x5007:
                    ch = (int)((adr >> 2) & 1);
                    freq[ch] = (freq[ch] & 0xff) + ((val & 7) << 8);
                    if (scounter[ch] > freq[ch]) scounter[ch] = freq[ch];
                    // phase reset
                    if (option[(int)OPT.PHASE_REFRESH.ordinal()] != 0)
                        sphase[ch] = 0;
                    envelope_write[ch] = true;
                    if (enable[ch])
                    {
                        length_counter[ch] = length_table[(val >> 3) & 0x1f];
                    }
                    break;

                // PCM channel control
                case 0x5010:
                    pcm_mode = ((val & 1) != 0); // 0 = write, 1 = read
                    break;

                // PCM channel control
                case 0x5011:
                    if (!pcm_mode)
                    {
                        val &= 0xFF;
                        if (val != 0) pcm = (byte)val;
                    }
                    break;

                case 0x5015:
                    enable[0] = (val & 1) != 0;
                    enable[1] = (val & 2) != 0;
                    if (!enable[0])
                        length_counter[0] = 0;
                    if (!enable[1])
                        length_counter[1] = 0;
                    break;

                case 0x5205:
                    mreg[0] = (byte)val;
                    break;

                case 0x5206:
                    mreg[1] = (byte)val;
                    break;

                default:
                    return false;

            }
            return true;
        }

        @Override public boolean Read(int adr, int val, int id/* = 0*/)
        {
            // in PCM read mode, reads from $8000-$C000 automatically load the PCM output
            if (pcm_mode && (0x8000 <= adr) && (adr < 0xC000) && cpu != null)
            {
                pcm_mode = false; // prevent recursive entry
                int pcm_read = 0;
                cpu.Read(adr, pcm_read, id);
                pcm_read &= 0xFF;
                if (pcm_read != 0)
                    pcm = (byte)pcm_read;
                pcm_mode = true;
            }

            if ((0x5000 <= adr) && (adr < 0x5008))
            {
                val = reg[adr & 0x7];
                return true;
            }
            else if (adr == 0x5015)
            {
                val = (int)((enable[1] ? 2 : 0) | (enable[0] ? 1 : 0));
                return true;
            }

            if ((0x5c00 <= adr) && (adr < 0x5ff0))
            {
                val = ram[adr & 0x3ff];
                return true;
            }
            else if (adr == 0x5205)
            {
                val = (int)((mreg[0] * mreg[1]) & 0xff);
                return true;
            }
            else if (adr == 0x5206)
            {
                val = (int)((mreg[0] * mreg[1]) >> 8);
                return true;
            }

            return false;
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
            //assert(trk < 3);

            if (trk < 2) // square
            {
                trkinfo[trk]._freq = freq[trk];
                if (freq[trk] != 0)
                    trkinfo[trk].freq = clock / 16 / (freq[trk] + 1);
                else
                    trkinfo[trk].freq = 0;

                trkinfo[trk].output = _out[trk];
                trkinfo[trk].max_volume = 15;
                trkinfo[trk].volume = (int)(volume[trk] + (envelope_disable[trk] ? 0 : 0x10));
                trkinfo[trk].key = (envelope_disable[trk] ? (volume[trk] > 0) : (envelope_counter[trk] > 0));
                trkinfo[trk].tone = (int)duty[trk];
            }
            else // pcm
            {
                trkinfo[trk]._freq = 0;
                trkinfo[trk].freq = 0;
                trkinfo[trk].output = _out[2];
                trkinfo[trk].max_volume = 255;
                trkinfo[trk].volume = pcm;
                trkinfo[trk].key = false;
                trkinfo[trk].tone = pcm_mode ? 1 : 0;
            }

            return trkinfo[trk];
        }

        // pcm read mode requires CPU read access
        public void SetCPU(km6502 cpu_)
        {
            cpu = cpu_;
        }

        @Override public void SetMask(int mask)
        {
            this.mask = mask;
        }

    }

