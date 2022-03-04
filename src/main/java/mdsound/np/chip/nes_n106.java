﻿package mdsound.np.chip;

import mdsound.MDSound;
import mdsound.np.IDevice.ISoundChip;
import mdsound.np.chip.IDeviceInfo.ITrackInfo;
import mdsound.np.chip.IDeviceInfo.TrackInfoBasic;

public class nes_n106 implements ISoundChip
{
    public static class TrackInfoN106 extends TrackInfoBasic
    {
        public int wavelen;
        public short[] wave = new short[256];
        public IDeviceInfo Clone()
        {
            TrackInfoN106 ti = new TrackInfoN106();
            ti.output = output;
            ti.volume = volume;
            ti.max_volume = max_volume;
            ti._freq = _freq;
            ti.freq = freq;
            ti.key = key;
            ti.tone = tone;
            ti.wavelen = wavelen;
            ti.wave = new short[256];
            for (int i = 0; i < ti.wave.length; i++) ti.wave[i] = wave[i];
            return ti;
        }
    }

        public final double DEFAULT_CLOCK = 1789772.0;
        public final int DEFAULT_RATE = 44100;

        public enum OPT
        {
            SERIAL,
            END
        };

        protected double rate, clock;
        protected int mask;
        protected int[][] sm = new int[][] { new int[8], new int[8] }; // stereo mix
        protected int[] fout = new int[8]; // current output
        protected TrackInfoN106[] trkinfo = new TrackInfoN106[8];
        protected int[] option = new int[(int)OPT.END.ordinal()];

        protected boolean master_disable;
        protected int[] reg = new int[0x80]; // all state is contained here
        protected int reg_select;
        protected boolean reg_advance;
        protected int tick_channel;
        protected int tick_clock;
        protected int render_channel;
        protected int render_clock;
        protected int render_subclock;


        public nes_n106()
        {
            option[(int)OPT.SERIAL.ordinal()] = 0;
            SetClock(DEFAULT_CLOCK);
            SetRate(DEFAULT_RATE);
            for (int i = 0; i < 8; ++i)
            {
                sm[0][i] = 128;
                sm[1][i] = 128;
                trkinfo[i] = new TrackInfoN106();
            }
            Reset();
        }

        protected void finalinze()
        {
        }

        @Override public void SetStereoMix(int trk, short mixl, short mixr)
        {
            if (trk < 0 || trk >= 8) return;
            trk = 7 - trk; // displayed channels are inverted
            sm[0][trk] = mixl;
            sm[1][trk] = mixr;
        }

        public ITrackInfo GetTrackInfo(int trk)
        {
            int channels = get_channels();
            int channel = 7 - trk; // invert the track display

            TrackInfoN106 t = trkinfo[channel];

            if (trk >= channels)
            {
                t.max_volume = 15;
                t.volume = 0;
                t._freq = 0;
                t.wavelen = 0;
                t.tone = -1;
                t.output = 0;
                t.key = false;
                t.freq = 0;
            }
            else
            {
                t.max_volume = 15;
                t.volume = get_vol(channel);
                t._freq = get_freq(channel);
                t.wavelen = (int)get_len(channel);
                t.tone = (int)get_off(channel);
                t.output = fout[channel];

                t.key = (t.volume > 0) && (t._freq > 0);
                t.freq = ((double)(t._freq) * clock) / (double)(15 * 65536 * channels * t.wavelen);
                t.halt = get_channels() > trk;
                for (int i = 0; i < t.wavelen; ++i)
                    t.wave[i] = (short)get_sample((int)((i + t.tone) & 0xFF));
            }

            return t;
        }

        public ITrackInfo[] GetTracksInfo()
        {
            try
            {
                for (int i = 0; i < 8; i++)
                {
                    GetTrackInfo(i);
                }

                return trkinfo;
            }
            catch (Exception e)
            {
                return null;
            }
        }


        @Override public void SetClock(double c)
        {
            clock = c;
        }

        @Override public void SetRate(double r)
        {
            rate = r;
        }

        @Override public void SetMask(int m)
        {
            // bit reverse the mask,
            // N163 waves are displayed in reverse order
            mask = 0
                | ((m & (1 << 0)) != 0 ? (1 << 7) : 0)
                | ((m & (1 << 1)) != 0 ? (1 << 6) : 0)
                | ((m & (1 << 2)) != 0 ? (1 << 5) : 0)
                | ((m & (1 << 3)) != 0 ? (1 << 4) : 0)
                | ((m & (1 << 4)) != 0 ? (1 << 3) : 0)
                | ((m & (1 << 5)) != 0 ? (1 << 2) : 0)
                | ((m & (1 << 6)) != 0 ? (1 << 1) : 0)
                | ((m & (1 << 7)) != 0 ? (1 << 0) : 0);
        }

        @Override public void SetOption(int id, int val)
        {
            if (id < (int)OPT.END.ordinal()) option[id] = val;
        }

        @Override public void Reset()
        {
            master_disable = false;
            for (int i = 0; i < reg.length; i++) reg[i] = 0;
            reg_select = 0;
            reg_advance = false;
            tick_channel = 0;
            tick_clock = 0;
            render_channel = 0;
            render_clock = 0;
            render_subclock = 0;

            for (int i = 0; i < 8; ++i) fout[i] = 0;

            Write(0xE000, 0x00); // master disable off
            Write(0xF800, 0x80); // select $00 with auto-increment
            for (int i = 0; i < 0x80; ++i) // set all regs to 0
            {
                Write(0x4800, 0x00);
            }
            Write(0xF800, 0x00); // select $00 without auto-increment
        }

        @Override public void Tick(int clocks)
        {
            if (master_disable) return;

            int channels = get_channels();

            tick_clock +=(int) clocks;
            render_clock += (int)clocks; // keep render in sync
            while (tick_clock > 0)
            {
                int channel = 7 - tick_channel;

                int phase = get_phase(channel);
                int freq = get_freq(channel);
                int len = get_len(channel);
                int off = get_off(channel);
                int vol = (int)get_vol(channel);

                // accumulate 24-bit phase
                phase = (phase + freq) & 0x00FFFFFF;

                // wrap phase if wavelength exceeded
                int hilen = len << 16;
                while (phase >= hilen) phase -= hilen;

                // write back phase
                set_phase(phase, channel);

                // fetch sample (note: N163 output is centred at 8, and inverted w.r.t 2A03)
                int sample = 8 - get_sample(((phase >> 16) + off) & 0xFF);
                fout[channel] = (int)(sample * vol);

                // cycle to next channel every 15 clocks
                tick_clock -= 15;
                ++tick_channel;
                if (tick_channel >= channels)
                    tick_channel = 0;
            }
        }

        private int[] MIX = new int[] { 256 / 1, 256 / 1, 256 / 2, 256 / 3, 256 / 4, 256 / 5, 256 / 6, 256 / 6, 256 / 6 };

        @Override public int Render(int[] b)//b[2])
        {
            b[0] = 0;
            b[1] = 0;
            if (master_disable) return 2;

            int channels = get_channels();

            if (option[(int)OPT.SERIAL.ordinal()] != 0) // hardware accurate serial multiplexing
            {
                // this could be made more efficient than going clock-by-clock
                // but this way is simpler
                int clocks = render_clock;
                while (clocks > 0)
                {
                    int c = 7 - render_channel;
                    if (0 == ((mask >> c) & 1))
                    {
                        b[0] += fout[c] * sm[0][c];
                        b[1] += fout[c] * sm[1][c];
                    }

                    ++render_subclock;
                    if (render_subclock >= 15) // each channel gets a 15-cycle slice
                    {
                        render_subclock = 0;
                        ++render_channel;
                        if (render_channel >= channels)
                            render_channel = 0;
                    }
                    --clocks;
                }

                // increase output level by 1 bits (7 bits already added from sm)
                b[0] <<= 1;
                b[1] <<= 1;

                // average the output
                if (render_clock > 0)
                {
                    b[0] /= render_clock;
                    b[1] /= render_clock;
                }
                render_clock = 0;
            }
            else // just mix all channels
            {
                for (int i = (8 - channels); i < 8; ++i)
                {
                    if (0 == ((mask >> (7-i)) & 1))
                    {
                        b[0] += fout[i] * sm[0][i];
                        b[1] += fout[i] * sm[1][i];
                    }
                }

                // mix together, increase output level by 8 bits, roll off 7 bits from sm
                b[0] = (b[0] * MIX[channels]) >> 7;
                b[1] = (b[1] * MIX[channels]) >> 7;
                // when approximating the serial multiplex as a straight mix, once the
                // multiplex frequency gets below the nyquist frequency an average mix
                // begins to sound too quiet. To approximate this effect, I don't attenuate
                // any further after 6 channels are active.
            }

            // 8 bit approximation of master volume
            // max N163 vol vs max APU square
            // unfortunately, games have been measured as low as 3.4x and as high as 8.5x
            // with higher volumes on Erika, King of Kings, and Rolling Thunder
            // and lower volumes on others. Using 6.0x as a rough "one size fits all".
            final double MASTER_VOL = 6.0 * 1223.0;
            final double MAX_OUT = 15.0 * 15.0 * 256.0; // max digital value
            final int GAIN = (int)((MASTER_VOL / MAX_OUT) * 256.0f);
            b[0] = (b[0] * GAIN) >> 8;
            b[1] = (b[1] * GAIN) >> 8;

            MDSound.np_nes_n106_volume = Math.abs(b[0]);

            return 2;
        }

        @Override public boolean Write(int adr, int val, int id/*=0*/)
        {
            if (adr == 0xE000) // master disable
            {
                master_disable = ((val & 0x40) != 0);
                return true;
            }
            else if (adr == 0xF800) // register select
            {
                reg_select = (val & 0x7F);
                reg_advance = (val & 0x80) != 0;
                return true;
            }
            else if (adr == 0x4800) // register write
            {
                reg[reg_select] = val;
                if (reg_advance)
                    reg_select = (reg_select + 1) & 0x7F;
                return true;
            }
            return false;
        }

        @Override public boolean Read(int adr, int val, int id)
        {
            if (adr == 0x4800) // register read
            {
                val = reg[reg_select];
                if (reg_advance)
                    reg_select = (reg_select + 1) & 0x7F;
                return true;
            }
            return false;
        }

        //
        // register decoding/encoding functions
        //

        private int get_phase(int channel)
        {
            // 24-bit phase stored in channel regs 1/3/5
            channel = channel << 3;
            return (reg[0x41 + channel])
                + (reg[0x43 + channel] << 8)
                + (reg[0x45 + channel] << 16);
        }

        private int get_freq(int channel)
        {
            // 19-bit frequency stored in channel regs 0/2/4
            channel = channel << 3;
            return (reg[0x40 + channel])
                + (reg[0x42 + channel] << 8)
                + ((reg[0x44 + channel] & 0x03) << 16);
        }

        private int get_off(int channel)
        {
            // 8-bit offset stored in channel reg 6
            channel = channel << 3;
            return reg[0x46 + channel];
        }

        private int get_len(int channel)
        {
            // 6-bit<<3 length stored obscurely in channel reg 4
            channel = channel << 3;
            return 256 - (reg[0x44 + channel] & 0xFC);
        }

        private int get_vol(int channel)
        {
            // 4-bit volume stored in channel reg 7
            channel = channel << 3;
            return (int)(reg[0x47 + channel] & 0x0F);
        }

        private int get_sample(int index)
        {
            // every sample becomes 2 samples in regs
            return (int)((index & 1) != 0 ?
                ((reg[index >> 1] >> 4) & 0x0F) :
                (reg[index >> 1] & 0x0F));
        }

        private int get_channels()
        {
            // 3-bit channel count stored in reg 0x7F
            return (int)(((reg[0x7F] >> 4) & 0x07) + 1);
        }

        private void set_phase(int phase, int channel)
        {
            // 24-bit phase stored in channel regs 1/3/5
            channel = channel << 3;
            reg[0x41 + channel] = phase & 0xFF;
            reg[0x43 + channel] = (phase >> 8) & 0xFF;
            reg[0x45 + channel] = (phase >> 16) & 0xFF;
        }

    }
