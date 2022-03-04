package mdsound.np;

public class np_nes_fds
    {
        // Ported from NSFPlay 2.3 to VGMPlay (including C++ . C conversion)
        // by Valley Bell on 26 September 2013

        //# include <stdlib.h> // for rand, malloc
        //# include <string.h> // for memset
        //# include <stddef.h> // for NULL
        //# include <math.h> // for exp
        //# include "mamedef.h"
        //# include "../stdbool.h"
        //# include "np_nes_fds.h"


        private static final double DEFAULT_CLOCK = 1789772.0;
        private static final int DEFAULT_RATE = 44100;


        private enum OPT
        {
            OPT_CUTOFF,
            OPT_4085_RESET,
            OPT_WRITE_PROTECT,
            OPT_END
        };

        private enum EG
        {
            EMOD,
            EVOL
        };

        //final int RC_BITS = 12;
        private static final int RC_BITS = 12;

        private enum TG
        {
            TMOD,
            TWAV
        };


        // 8 bit approximation of master volume
        private static final double MASTER_VOL = (2.4 * 1223.0);   // max FDS vol vs max APU square (arbitrarily 1223)
        private static final double MAX_OUT = (32.0 * 63.0); // value that should map to master vol
        private int[] MASTER = new int[]{
    (int)((MASTER_VOL / MAX_OUT) * 256.0 * 2.0f / 2.0f),
    (int)((MASTER_VOL / MAX_OUT) * 256.0 * 2.0f / 3.0f),
    (int)((MASTER_VOL / MAX_OUT) * 256.0 * 2.0f / 4.0f),
    (int)((MASTER_VOL / MAX_OUT) * 256.0 * 2.0f / 5.0f) };


        // Although they were pretty much removed from any sound core in NSFPlay 2.3,
        // I find this counter structure very useful.
        private static final int COUNTER_SHIFT = 24;

        public Counter counter = new Counter();
        public class Counter
        {
            public double ratio;
            public int val, step;
        }

        private void COUNTER_setcycle(Counter cntr, int s)
        {
            cntr.step = (int)(cntr.ratio / (s + 1));
        }

        private void COUNTER_iup(Counter cntr)
        {
            cntr.val += cntr.step;
        }

        private int COUNTER_value(Counter cntr)
        {
            return (cntr.val >> COUNTER_SHIFT);
        }

        private void COUNTER_init(Counter cntr, double clk, double rate)
        {
            (cntr).ratio = (1 << COUNTER_SHIFT) * (1.0 * clk / rate);
            (cntr).step = (int)((cntr).ratio + 0.5);
            (cntr).val = 0;
        }

        public class NES_FDS
        {
            public double rate, clock;
            public int mask;
            public int[] sm = new int[2];    // stereo mix
            public int fout;     // current output
            public int[] option = new int[(int)OPT.OPT_END.ordinal()];

            public boolean master_io;
            public byte master_vol;
            public int last_freq;   // for trackinfo
            public int last_vol;    // for trackinfo

            // two wavetables
            //final enum { TMOD=0, TWAV=1 };
            public int[][] wave = new int[][] { new int[64], new int[64] };
            public int[] freq = new int[2];
            public int[] phase = new int[2];
            public boolean wav_write;
            public boolean wav_halt;
            public boolean env_halt;
            public boolean mod_halt;
            public int mod_pos;
            public int mod_write_pos;

            // two ramp envelopes
            //final enum { EMOD=0, EVOL=1 };
            public boolean[] env_mode = new boolean[2];
            public boolean[] env_disable = new boolean[2];
            public int[] env_timer = new int[2];
            public int[] env_speed = new int[2];
            public int[] env_out = new int[2];
            public int master_env_speed;

            // 1-pole RC lowpass filter
            public int rc_accum;
            public int rc_k;
            public int rc_l;

            public Counter tick_count=new Counter();
            public int tick_last;
        };

        public NES_FDS NES_FDS_Create(int clock, int rate)
        {
            NES_FDS fds;

            fds = new NES_FDS();// (NES_FDS*)malloc(sizeof(NES_FDS));
            if (fds == null)
                return null;
            //memset(fds, 0x00, sizeof(NES_FDS));

            fds.option[(int)OPT.OPT_CUTOFF.ordinal()] = 2000;
            fds.option[(int)OPT.OPT_4085_RESET.ordinal()] = 0;
            fds.option[(int)OPT.OPT_WRITE_PROTECT.ordinal()] = 0; // not used here, see nsfplay.cpp

            fds.rc_k = 0;
            fds.rc_l = (1 << RC_BITS);

            //NES_FDS_SetClock(fds, DEFAULT_CLOCK);
            //NES_FDS_SetRate(fds, DEFAULT_RATE);
            NES_FDS_SetClock(fds, clock);
            NES_FDS_SetRate(fds, rate);
            fds.sm[0] = 128;
            fds.sm[1] = 128;

            NES_FDS_Reset(fds);

            return fds;
        }

        public void NES_FDS_Destroy(NES_FDS chip)
        {
            chip = null;//無意味
            //free(chip);
        }

        public void NES_FDS_SetMask(NES_FDS chip, int m)
        {
            NES_FDS fds = chip;

            fds.mask = m & 1;
        }

        public void NES_FDS_SetStereoMix(NES_FDS chip, int trk, short mixl, short mixr)
        {
            NES_FDS fds = chip;

            if (trk < 0) return;
            if (trk > 1) return;
            fds.sm[0] = mixl;
            fds.sm[1] = mixr;
        }

        public void NES_FDS_SetClock(NES_FDS chip, double c)
        {
            NES_FDS fds = chip;

            fds.clock = c;
        }

        public void NES_FDS_SetRate(NES_FDS chip, double r)
        {
            NES_FDS fds = chip;
            double cutoff, leak;

            fds.rate = r;

            COUNTER_init(fds.tick_count, fds.clock, fds.rate);
            fds.tick_last = 0;

            // configure lowpass filter
            cutoff = (double)fds.option[(int)OPT.OPT_CUTOFF.ordinal()];
            leak = 0.0;
            if (cutoff > 0)
                leak = Math.exp(-2.0 * 3.14159 * cutoff / fds.rate);
            fds.rc_k = (int)(leak * (double)(1 << RC_BITS));
            fds.rc_l = (1 << RC_BITS) - fds.rc_k;
        }

        public void NES_FDS_SetOption(NES_FDS chip, int id, int val)
        {
            NES_FDS fds = chip;

            if (id < (int)OPT.OPT_END.ordinal()) fds.option[id] = val;

            // update cutoff immediately
            if (id == (int)OPT.OPT_CUTOFF.ordinal()) NES_FDS_SetRate(fds, fds.rate);
        }

        public void NES_FDS_Reset(NES_FDS chip)
        {
            NES_FDS fds = chip;
            int i;

            fds.master_io = true;
            fds.master_vol = 0;
            fds.last_freq = 0;
            fds.last_vol = 0;

            fds.rc_accum = 0;

            for (i = 0; i < 2; ++i)
            {
                for (int j = 0; j < fds.wave[i].length; j++) fds.wave[i][j] = 0; //memset(fds.wave[i], 0, sizeof(fds.wave[i]));
                fds.freq[i] = 0;
                fds.phase[i] = 0;
            }
            fds.wav_write = false;
            fds.wav_halt = true;
            fds.env_halt = true;
            fds.mod_halt = true;
            fds.mod_pos = 0;
            fds.mod_write_pos = 0;

            for (i = 0; i < 2; ++i)
            {
                fds.env_mode[i] = false;
                fds.env_disable[i] = true;
                fds.env_timer[i] = 0;
                fds.env_speed[i] = 0;
                fds.env_out[i] = 0;
            }
            fds.master_env_speed = 0xFF;

            // NOTE: the FDS BIOS reset only does the following related to audio:
            //   $4023 = $00
            //   $4023 = $83 enables master_io
            //   $4080 = $80 output volume = 0, envelope disabled
            //   $408A = $FF master envelope speed set to slowest
            NES_FDS_Write(fds, 0x4023, 0x00);
            NES_FDS_Write(fds, 0x4023, 0x83);
            NES_FDS_Write(fds, 0x4080, 0x80);
            NES_FDS_Write(fds, 0x408A, 0xFF);

            // reset other stuff
            NES_FDS_Write(fds, 0x4082, 0x00);   // wav freq 0
            NES_FDS_Write(fds, 0x4083, 0x80);   // wav disable
            NES_FDS_Write(fds, 0x4084, 0x80);   // mod strength 0
            NES_FDS_Write(fds, 0x4085, 0x00);   // mod position 0
            NES_FDS_Write(fds, 0x4086, 0x00);   // mod freq 0
            NES_FDS_Write(fds, 0x4087, 0x80);   // mod disable
            NES_FDS_Write(fds, 0x4089, 0x00);   // wav write disable, max Global volume}
        }

        public void Tick(NES_FDS fds, int clocks)
        {
            int vol_out;

            // clock envelopes
            if (!fds.env_halt && !fds.wav_halt && (fds.master_env_speed != 0))
            {
                int i;

                for (i = 0; i < 2; ++i)
                {
                    if (!fds.env_disable[i])
                    {
                        int period;

                        fds.env_timer[i] += clocks;
                        period = ((fds.env_speed[i] + 1) * fds.master_env_speed) << 3;
                        while (fds.env_timer[i] >= period)
                        {
                            // clock the envelope
                            if (fds.env_mode[i])
                            {
                                if (fds.env_out[i] < 32) ++fds.env_out[i];
                            }
                            else
                            {
                                if (fds.env_out[i] > 0) --fds.env_out[i];
                            }
                            fds.env_timer[i] -= period;
                        }
                    }
                }
            }

            // clock the mod table
            if (!fds.mod_halt)
            {
                int start_pos, end_pos, p;

                // advance phase, adjust for modulator
                start_pos = fds.phase[(int)TG.TMOD.ordinal()] >> 16;
                fds.phase[(int)TG.TMOD.ordinal()] += (clocks * fds.freq[(int)TG.TMOD.ordinal()]);
                end_pos = fds.phase[(int)TG.TMOD.ordinal()] >> 16;

                // wrap the phase to the 64-step table (+ 16 bit accumulator)
                fds.phase[(int)TG.TMOD.ordinal()] = fds.phase[(int)TG.TMOD.ordinal()] & 0x3FFFFF;

                // execute all clocked steps
                for (p = start_pos; p < end_pos; ++p)
                {
                    int wv = fds.wave[(int)TG.TMOD.ordinal()][p & 0x3F];
                    if (wv == 4)    // 4 resets mod position
                        fds.mod_pos = 0;
                    else
                    {
                        int[] BIAS = new int[] { 0, 1, 2, 4, 0, -4, -2, -1 };
                        fds.mod_pos += (int)BIAS[wv];
                        fds.mod_pos &= 0x7F;   // 7-bit clamp
                    }
                }
            }

            // clock the wav table
            if (!fds.wav_halt)
            {
                int mod, f;

                // complex mod calculation
                mod = 0;
                if (fds.env_out[(int)EG.EMOD.ordinal()] != 0)    // skip if modulator off
                {
                    // convert mod_pos to 7-bit signed
                    int pos = (int)((fds.mod_pos < 64) ? fds.mod_pos : (fds.mod_pos - 128));

                    // multiply pos by gain,
                    // shift off 4 bits but with odd "rounding" behaviour
                    int temp = (int)(pos * fds.env_out[(int)EG.EMOD.ordinal()]);
                    int rem = temp & 0x0F;
                    temp >>= 4;
                    if ((rem > 0) && ((temp & 0x80) == 0))
                    {
                        if (pos < 0) temp -= 1;
                        else temp += 2;
                    }

                    // wrap if range is exceeded
                    while (temp >= 192) temp -= 256;
                    while (temp < -64) temp += 256;

                    // multiply result by pitch,
                    // shift off 6 bits, round to nearest
                    temp = (int)(fds.freq[(int)TG.TWAV.ordinal()] * temp);
                    rem = temp & 0x3F;
                    temp >>= 6;
                    if (rem >= 32) temp += 1;

                    mod = temp;
                }

                // advance wavetable position
                f = (int)(fds.freq[(int)TG.TWAV.ordinal()] + mod);
                fds.phase[(int)TG.TWAV.ordinal()] = (int)(fds.phase[(int)TG.TWAV.ordinal()] + (clocks * f));
                fds.phase[(int)TG.TWAV.ordinal()] = fds.phase[(int)TG.TWAV.ordinal()] & 0x3FFFFF; // wrap

                // store for trackinfo
                fds.last_freq = (int)f;
            }

            // output volume caps at 32
            vol_out = (int)(fds.env_out[(int)EG.EVOL.ordinal()]);
            if (vol_out > 32) vol_out = 32;

            // final output
            if (!fds.wav_write)
                fds.fout = fds.wave[(int)TG.TWAV.ordinal()][(fds.phase[(int)TG.TWAV.ordinal()] >> 16) & 0x3F] * vol_out;

            // NOTE: during wav_halt, the unit still outputs (at phase 0)
            // and volume can affect it if the first sample is nonzero.
            // haven't worked out 100% of the conditions for volume to
            // effect (vol envelope does not seem to run, but am unsure)
            // but this implementation is very close to correct

            // store for trackinfo
            fds.last_vol = (int)vol_out;
        }

        public int NES_FDS_Render(NES_FDS chip, int[] b)//b[2])
        {
            NES_FDS fds = chip;

            /* // 8 bit approximation of master volume
                static final double MASTER_VOL = 2.4 * 1223.0; // max FDS vol vs max APU square (arbitrarily 1223)
                static final double MAX_OUT = 32.0f * 63.0f; // value that should map to master vol
                static final int MASTER[4] = {
                    (int)((MASTER_VOL / MAX_OUT) * 256.0 * 2.0f / 2.0f),
                    (int)((MASTER_VOL / MAX_OUT) * 256.0 * 2.0f / 3.0f),
                    (int)((MASTER_VOL / MAX_OUT) * 256.0 * 2.0f / 4.0f),
                    (int)((MASTER_VOL / MAX_OUT) * 256.0 * 2.0f / 5.0f) };*/

            int clocks;
            int v, rc_out, m;

            COUNTER_iup(fds.tick_count);
            clocks = (COUNTER_value(fds.tick_count) - fds.tick_last) & 0xFF;
            Tick(fds, clocks);
            fds.tick_last = COUNTER_value(fds.tick_count);

            v = fds.fout * MASTER[fds.master_vol] >> 8;

            // lowpass RC filter
            rc_out = ((fds.rc_accum * fds.rc_k) + (v * fds.rc_l)) >> RC_BITS;
            fds.rc_accum = rc_out;
            v = rc_out;

            // output mix
            m = fds.mask != 0 ? 0 : v;
            b[0] = (m * fds.sm[0]) >> 5;
            b[1] = (m * fds.sm[1]) >> 5;
            return 2;
        }

        public int NES_FDS_org_Render(NES_FDS chip, int[] b)//b[2])
        {
            NES_FDS fds = chip;

            /* // 8 bit approximation of master volume
                static final double MASTER_VOL = 2.4 * 1223.0; // max FDS vol vs max APU square (arbitrarily 1223)
                static final double MAX_OUT = 32.0f * 63.0f; // value that should map to master vol
                static final int MASTER[4] = {
                    (int)((MASTER_VOL / MAX_OUT) * 256.0 * 2.0f / 2.0f),
                    (int)((MASTER_VOL / MAX_OUT) * 256.0 * 2.0f / 3.0f),
                    (int)((MASTER_VOL / MAX_OUT) * 256.0 * 2.0f / 4.0f),
                    (int)((MASTER_VOL / MAX_OUT) * 256.0 * 2.0f / 5.0f) };*/

            //int clocks;
            int v, rc_out, m;

            v = fds.fout * MASTER[fds.master_vol] >> 8;

            // lowpass RC filter
            rc_out = ((fds.rc_accum * fds.rc_k) + (v * fds.rc_l)) >> RC_BITS;
            fds.rc_accum = rc_out;
            v = rc_out;

            // output mix
            m = fds.mask != 0 ? 0 : v;
            b[0] = (m * fds.sm[0]) >> (7 - 3);
            b[1] = (m * fds.sm[1]) >> (7 - 3);
            return 2;
        }

        public boolean NES_FDS_Write(NES_FDS chip, int adr, int val)
        {
            NES_FDS fds = chip;

            // $4023 master I/O enable/disable
            if (adr == 0x4023)
            {
                fds.master_io = ((val & 2) != 0);
                if (!fds.master_io) fds.fout = 0;//KUMA:止めたい！
                return true;
            }

            if (!fds.master_io)
                return false;
            if (adr < 0x4040 || adr > 0x408A)
                return false;

            if (adr < 0x4080)   // $4040-407F wave table write
            {
                if (fds.wav_write)
                    fds.wave[(int)TG.TWAV.ordinal()][adr - 0x4040] = (int)(val & 0x3F);
                return true;
            }

            switch (adr & 0x00FF)
            {
                case 0x80:  // $4080 volume envelope
                    fds.env_disable[(int)EG.EVOL.ordinal()] = ((val & 0x80) != 0);
                    fds.env_mode[(int)EG.EVOL.ordinal()] = ((val & 0x40) != 0);
                    fds.env_timer[(int)EG.EVOL.ordinal()] = 0;
                    fds.env_speed[(int)EG.EVOL.ordinal()] = val & 0x3F;
                    if (fds.env_disable[(int)EG.EVOL.ordinal()])
                        fds.env_out[(int)EG.EVOL.ordinal()] = fds.env_speed[(int)EG.EVOL.ordinal()];
                    return true;
                case 0x81:  // $4081 ---
                    return false;
                case 0x82:  // $4082 wave frequency low
                    fds.freq[(int)TG.TWAV.ordinal()] = (fds.freq[(int)TG.TWAV.ordinal()] & 0xF00) | val;
                    return true;
                case 0x83:  // $4083 wave frequency high / enables
                    fds.freq[(int)TG.TWAV.ordinal()] = (fds.freq[(int)TG.TWAV.ordinal()] & 0x0FF) | ((val & 0x0F) << 8);
                    fds.wav_halt = ((val & 0x80) != 0);
                    fds.env_halt = ((val & 0x40) != 0);
                    if (fds.wav_halt)
                        fds.phase[(int)TG.TWAV.ordinal()] = 0;
                    if (fds.env_halt)
                    {
                        fds.env_timer[(int)EG.EMOD.ordinal()] = 0;
                        fds.env_timer[(int)EG.EVOL.ordinal()] = 0;
                    }
                    return true;
                case 0x84:  // $4084 mod envelope
                    fds.env_disable[(int)EG.EMOD.ordinal()] = ((val & 0x80) != 0);
                    fds.env_mode[(int)EG.EMOD.ordinal()] = ((val & 0x40) != 0);
                    fds.env_timer[(int)EG.EMOD.ordinal()] = 0;
                    fds.env_speed[(int)EG.EMOD.ordinal()] = val & 0x3F;
                    if (fds.env_disable[(int)EG.EMOD.ordinal()])
                        fds.env_out[(int)EG.EMOD.ordinal()] = fds.env_speed[(int)EG.EMOD.ordinal()];
                    return true;
                case 0x85:  // $4085 mod position
                    fds.mod_pos = val & 0x7F;
                    // not hardware accurate., but prevents detune due to cycle inaccuracies
                    // (notably in Bio Miracle Bokutte Upa)
                    if (fds.option[(int)OPT.OPT_4085_RESET.ordinal()] != 0)
                        fds.phase[(int)TG.TMOD.ordinal()] = fds.mod_write_pos << 16;
                    return true;
                case 0x86:  // $4086 mod frequency low
                    fds.freq[(int)TG.TMOD.ordinal()] = (fds.freq[(int)TG.TMOD.ordinal()] & 0xF00) | val;
                    return true;
                case 0x87:  // $4087 mod frequency high / enable
                    fds.freq[(int)TG.TMOD.ordinal()] = (fds.freq[(int)TG.TMOD.ordinal()] & 0x0FF) | ((val & 0x0F) << 8);
                    fds.mod_halt = ((val & 0x80) != 0);
                    if (fds.mod_halt)
                        fds.phase[(int)TG.TMOD.ordinal()] = fds.phase[(int)TG.TMOD.ordinal()] & 0x3F0000; // reset accumulator phase
                    return true;
                case 0x88:  // $4088 mod table write
                    if (fds.mod_halt)
                    {
                        // writes to current playback position (there is no direct way to set phase)
                        fds.wave[(int)TG.TMOD.ordinal()][(fds.phase[(int)TG.TMOD.ordinal()] >> 16) & 0x3F] = (int)(val & 0x7F);
                        fds.phase[(int)TG.TMOD.ordinal()] = (fds.phase[(int)TG.TMOD.ordinal()] + 0x010000) & 0x3FFFFF;
                        fds.wave[(int)TG.TMOD.ordinal()][(fds.phase[(int)TG.TMOD.ordinal()] >> 16) & 0x3F] = (int)(val & 0x7F);
                        fds.phase[(int)TG.TMOD.ordinal()] = (fds.phase[(int)TG.TMOD.ordinal()] + 0x010000) & 0x3FFFFF;
                        fds.mod_write_pos = fds.phase[(int)TG.TMOD.ordinal()] >> 16;    // used by OPT_4085_RESET
                    }
                    return true;
                case 0x89:  // $4089 wave write enable, master volume
                    fds.wav_write = ((val & 0x80) != 0);
                    fds.master_vol = (byte)(val & 0x03);
                    return true;
                case 0x8A:  // $408A envelope speed
                    fds.master_env_speed = val;
                    // haven't tested whether this register resets phase on hardware,
                    // but this ensures my inplementation won't spam envelope clocks
                    // if this value suddenly goes low.
                    fds.env_timer[(int)EG.EMOD.ordinal()] = 0;
                    fds.env_timer[(int)EG.EVOL.ordinal()] = 0;
                    return true;
                default:
                    return false;
            }

            //return false;
        }

        public boolean NES_FDS_Read(NES_FDS chip, int adr, int val)
        {
            NES_FDS fds = chip;

            if (adr >= 0x4040 && adr < 0x407F)
            {
                // TODO: if wav_write is not enabled, the
                // read address may not be reliable? need
                // to test this on hardware.
                val = (int)fds.wave[(int)TG.TWAV.ordinal()][adr - 0x4040];
                return true;
            }

            if (adr == 0x4090)  // $4090 read volume envelope
            {
                val = fds.env_out[(int)EG.EVOL.ordinal()] | 0x40;
                return true;
            }

            if (adr == 0x4092)  // $4092 read mod envelope
            {
                val = fds.env_out[(int)EG.EMOD.ordinal()] | 0x40;
                return true;
            }

            return false;
        }
    }
