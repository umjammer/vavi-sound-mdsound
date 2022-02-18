package MDSound.np.chip;

public class emu2149
    {
        private final int EMU2149_VOL_DEFAULT = 1;
        private final int EMU2149_VOL_YM2149 = 0;
        private final int EMU2149_VOL_AY_3_8910 = 1;

        private int PSG_MASK_CH(int x) {
            return (1 << (x));
        }

        public class PSG
        {
            /* Volume Table */
            public int[] voltbl; //*

            public byte[] reg = new byte[0x20];
            public int _out;
            public int[] cout = new int[3];

            public int clk, rate, base_incr, quality;

            public int[] count = new int[3];
            public int[] volume = new int[3];
            public int[] freq = new int[3];
            public int[] edge = new int[3];
            public int[] tmask = new int[3];
            public int[] nmask = new int[3];
            public int mask;

            public int base_count;

            public int env_volume;
            public int env_ptr;
            public int env_face;

            public int env_continue;
            public int env_attack;
            public int env_alternate;
            public int env_hold;
            public int env_pause;
            public int env_reset;

            public int env_freq;
            public int env_count;

            public int noise_seed;
            public int noise_count;
            public int noise_freq;

            /* rate converter */
            public int realstep;
            public int psgtime;
            public int psgstep;

            /* I/O Ctrl */
            public int adr;

        }

        private static int[][] voltbl = new int[][] {
            new int[]{
                  0x00, 0x01, 0x01, 0x02, 0x02, 0x03, 0x03, 0x04, 0x05, 0x06, 0x07, 0x09, 0x0B, 0x0D, 0x0F, 0x12
                , 0x16, 0x1A, 0x1F, 0x25, 0x2D, 0x35, 0x3F, 0x4C, 0x5A, 0x6A, 0x7F, 0x97, 0xB4, 0xD6, 0xEB, 0xFF
            },
            new int[]{
                  0x00, 0x00, 0x01, 0x01, 0x02, 0x02, 0x03, 0x03, 0x05, 0x05, 0x07, 0x07, 0x0B, 0x0B, 0x0F, 0x0F
                , 0x16, 0x16, 0x1F, 0x1F, 0x2D, 0x2D, 0x3F, 0x3F, 0x5A, 0x5A, 0x7F, 0x7F, 0xB4, 0xB4, 0xFF, 0xFF
            }
        };


        private final int GETA_BITS = 24;

        private void internal_refresh(PSG psg)
        {
            if (psg.quality != 0)
            {
                psg.base_incr = 1 << GETA_BITS;
                psg.realstep = (int)((1 << 31) / psg.rate);
                psg.psgstep = (int)((1 << 31) / (psg.clk / 16));
                psg.psgtime = 0;
            }
            else
            {
                psg.base_incr =
                  (int)((double)psg.clk * (1 << GETA_BITS) / (16 * psg.rate));
            }
        }

        public void PSG_set_rate(PSG psg, int r)
        {
            psg.rate = r!=0 ? r : 44100;
            internal_refresh(psg);
        }

        public void PSG_set_quality(PSG psg, int q)
        {
            psg.quality = q;
            internal_refresh(psg);
        }

        public PSG PSG_new(int c, int r)
        {
            PSG psg;

            psg = new PSG();
            if (psg == null)
                return null;

            PSG_setVolumeMode(psg, EMU2149_VOL_DEFAULT);
            psg.clk = c;
            psg.rate = r != 0 ? r : 44100;
            PSG_set_quality(psg, 0);

            return psg;
        }

        public void PSG_setVolumeMode(PSG psg, int type)
        {
            switch (type)
            {
                case 1:
                    psg.voltbl = voltbl[EMU2149_VOL_YM2149];
                    break;
                case 2:
                    psg.voltbl = voltbl[EMU2149_VOL_AY_3_8910];
                    break;
                default:
                    psg.voltbl = voltbl[EMU2149_VOL_DEFAULT];
                    break;
            }
        }

        public int PSG_setMask(PSG psg, int mask)
        {
            int ret = 0;
            if (psg != null)
            {
                ret = psg.mask;
                psg.mask = mask;
            }
            return ret;
        }

        public int PSG_toggleMask(PSG psg, int mask)
        {
            int ret = 0;
            if (psg!=null)
            {
                ret = psg.mask;
                psg.mask ^= mask;
            }
            return ret;
        }

        public void PSG_reset(PSG psg)
        {
            int i;

            psg.base_count = 0;

            for (i = 0; i < 3; i++)
            {
                psg.cout[i] = 0;
                psg.count[i] = 0x1000;
                psg.freq[i] = 0;
                psg.edge[i] = 0;
                psg.volume[i] = 0;
            }

            psg.mask = 0;

            for (i = 0; i < 16; i++)
                psg.reg[i] = 0;
            psg.adr = 0;

            psg.noise_seed = 0xffff;
            psg.noise_count = 0x40;
            psg.noise_freq = 0;
               
            psg.env_volume = 0;
            psg.env_ptr = 0;
            psg.env_freq = 0;
            psg.env_count = 0;
            psg.env_pause = 1;
               
            psg._out = 0;
        }

        public void PSG_delete(PSG psg)
        {
            psg = null;
        }

        public byte PSG_readIO(PSG psg)
        {
            return (byte)(psg.reg[psg.adr]);
        }

        public byte PSG_readReg(PSG psg, int reg)
        {
            return (byte)(psg.reg[reg & 0x1f]);

        }

        public void PSG_writeIO(PSG psg, int adr, int val)
        {
            if ((adr & 1)!=0)
                PSG_writeReg(psg, psg.adr, val);
            else
                psg.adr = val & 0x1f;
        }

        public short calc(PSG psg)
        {

            int i, noise;
            int incr;
            int mix = 0;

            psg.base_count += psg.base_incr;
            incr = (psg.base_count >> GETA_BITS);
            psg.base_count &= (1 << GETA_BITS) - 1;

            /* Envelope */
            psg.env_count += incr;
            while (psg.env_count >= 0x10000 && psg.env_freq != 0)
            {
                if (psg.env_pause == 0)
                {
                    if (psg.env_face != 0)
                        psg.env_ptr = (psg.env_ptr + 1) & 0x3f;
                    else
                        psg.env_ptr = (psg.env_ptr + 0x3f) & 0x3f;
                }

                if ((psg.env_ptr & 0x20) != 0) /* if carry or borrow */
                {
                    if (psg.env_continue != 0)
                    {
                        if ((psg.env_alternate ^ psg.env_hold) != 0) psg.env_face ^= 1;
                        if (psg.env_hold != 0) psg.env_pause = 1;
                        psg.env_ptr = (int)((psg.env_face != 0) ? 0 : 0x1f);
                    }
                    else
                    {
                        psg.env_pause = 1;
                        psg.env_ptr = 0;
                    }
                }

                psg.env_count -= psg.env_freq;
            }

            /* Noise */
            psg.noise_count += incr;
            if ((psg.noise_count & 0x40)!=0)
            {
                if ((psg.noise_seed & 1)!=0)
                    psg.noise_seed ^= 0x24000;
                psg.noise_seed >>= 1;
                psg.noise_count -= psg.noise_freq;
            }
            noise = (int)(psg.noise_seed & 1);

            /* Tone */
            for (i = 0; i < 3; i++)
            {
                psg.count[i] += incr;
                if( (psg.count[i] & 0x1000)!=0)
                {
                    if (psg.freq[i] > 1)
                    {
                        psg.edge[i] = (~psg.edge[i]) & 1;
                        psg.count[i] -= psg.freq[i];
                    }
                    else
                    {
                        psg.edge[i] = 1;
                    }
                }

                psg.cout[i] = 0; // maintaining cout for stereo mix

                if ((psg.mask & PSG_MASK_CH(i))!=0)
                    continue;

                if ((psg.tmask[i]!=0 || psg.edge[i]!=0) && (psg.nmask[i]!=0 || noise!=0))
                {
                    if ((psg.volume[i] & 32)==0)
                        psg.cout[i] = (int)psg.voltbl[psg.volume[i] & 31];
                    else
                        psg.cout[i] = (int)psg.voltbl[psg.env_ptr];

                    mix += psg.cout[i];
                }
            }

            return (short)mix;
        }

        public short PSG_calc(PSG psg)
        {
            if (psg.quality == 0)
                return (short)(calc(psg) << 4);

            /* Simple rate converter */
            while (psg.realstep > psg.psgtime)
            {
                psg.psgtime += psg.psgstep;
                psg._out += calc(psg);
                psg._out >>= 1;
            }

            psg.psgtime = psg.psgtime - psg.realstep;

            return (short)(psg._out << 4);
        }

        public void PSG_writeReg(PSG psg, int reg, int val)
        {
            int c;

            if (reg > 15) return;

            psg.reg[reg] = (byte)(val & 0xff);
            switch (reg)
            {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                    c = (int)(reg >> 1);
                    psg.freq[c] = (int)(((psg.reg[c * 2 + 1] & 15) << 8) + psg.reg[c * 2]);
                    break;

                case 6:
                    psg.noise_freq = (val == 0) ? 1 : ((val & 31) << 1);
                    break;

                case 7:
                    psg.tmask[0] = (val & 1);
                    psg.tmask[1] = (val & 2);
                    psg.tmask[2] = (val & 4);
                    psg.nmask[0] = (val & 8);
                    psg.nmask[1] = (val & 16);
                    psg.nmask[2] = (val & 32);
                    break;

                case 8:
                case 9:
                case 10:
                    psg.volume[reg - 8] = val << 1;

                    break;

                case 11:
                case 12:
                    psg.env_freq = (int)((psg.reg[12] << 8) + psg.reg[11]);
                    break;

                case 13:
                    psg.env_continue = (val >> 3) & 1;
                    psg.env_attack = (val >> 2) & 1;
                    psg.env_alternate = (val >> 1) & 1;
                    psg.env_hold = val & 1;
                    psg.env_face = psg.env_attack;
                    psg.env_pause = 0;
                    psg.env_count = 0x10000 - psg.env_freq;
                    psg.env_ptr = (int)(psg.env_face!=0 ? 0 : 0x1f);
                    break;

                case 14:
                case 15:
                default:
                    break;
            }

            return;
        }


    }
