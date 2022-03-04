package mdsound;

import java.util.Arrays;

enum EG_PARAM
    {
        eg_num_attack,
        eg_num_decay,
        eg_num_sustain,
        eg_num_release;
        static EG_PARAM valueOf(int v) { return Arrays.stream(values()).filter(e -> e.ordinal() == v).findFirst().get(); }
    }

    public class ym3438 extends Instrument
    {
        @Override public String getName() { return "YM3438"; }
        @Override public String getShortName()  { return "OPN2cmos"; }

        private int[] dmyBuffer = new int[2];
        private int[] grBuffer = new int[2];
        private int[] gsBuffer = new int[2];
        private ym3438_[] ym3438_ = new ym3438_[] { new ym3438_(), new ym3438_() };
        private int[] buf = new int[2];

        private void OPN2_DoIO(ym3438_ chip)
        {
            chip.write_a_en = (byte)((chip.write_a & 0x03) == 0x01 ? 1 : 0);
            chip.write_d_en = (byte)((chip.write_d & 0x03) == 0x01 ? 1 : 0);
            //mlog("aen:{0} den:{1}\n", chip.write_a_en, chip.write_d_en);
            chip.write_a <<= 1;
            chip.write_d <<= 1;
            //BUSY Counter
            chip.busy = chip.write_busy;
            chip.write_busy_cnt += chip.write_busy;
            chip.write_busy = (byte)(((chip.write_busy != 0 && ((chip.write_busy_cnt >> 5) == 0)) || chip.write_d_en != 0) ? 1 : 0);
            chip.write_busy_cnt &= 0x1f;
        }

        private void OPN2_DoRegWrite(ym3438_ chip)
        {
            int i;
            int slot = chip.slot % 12;
            int address;
            int channel = chip.channel;
            if (chip.write_fm_data != 0)
            {
                if (ym3438_const.op_offset[slot] == (chip.address & 0x107))
                {
                    if ((chip.address & 0x08) != 0)
                    {
                        slot += 12; // OP2? OP4?
                    }
                    address = (int)(chip.address & 0xf0);
                    switch (address)
                    {
                        case 0x30: //DT MULTI
                            chip.multi[slot] = (byte)(chip.data & 0x0f);
                            if (chip.multi[slot] == 0)
                            {
                                chip.multi[slot] = 1;
                            }
                            else
                            {
                                chip.multi[slot] <<= 1;
                            }
                            chip.dt[slot] = (byte)((chip.data >> 4) & 0x07);
                            break;
                        case 0x40: //TL
                            chip.tl[slot] = (byte)(chip.data & 0x7f);
                            break;
                        case 0x50: // KS AR
                            chip.ar[slot] = (byte)(chip.data & 0x1f);
                            chip.ks[slot] = (byte)((chip.data >> 6) & 0x03);
                            break;
                        case 0x60: // AM DR
                            chip.dr[slot] = (byte)(chip.data & 0x1f);
                            chip.am[slot] = (byte)((chip.data >> 7) & 0x01);
                            break;
                        case 0x70: //SR
                            chip.sr[slot] = (byte)(chip.data & 0x1f);
                            break;
                        case 0x80: //SL RR
                            chip.rr[slot] = (byte)(chip.data & 0x0f);
                            chip.sl[slot] = (byte)((chip.data >> 4) & 0x0f);
                            chip.sl[slot] |= (byte)((chip.sl[slot] + 1) & 0x10);
                            break;
                        case 0x90:
                            chip.ssg_eg[slot] = (byte)(chip.data & 0x0f);
                            break;
                        default:
                            break;
                    }
                }

                if (ym3438_const.ch_offset[channel] == (chip.address & 0x103))
                {
                    address = (int)(chip.address & 0xfc);
                    switch (address)
                    {
                        case 0xa0: //Fnum, Block, kcode
                            chip.fnum[channel] = (short)((chip.data & 0xff) | ((chip.reg_a4 & 0x07) << 8));
                            chip.block[channel] = (byte)((chip.reg_a4 >> 3) & 0x07);
                            chip.kcode[channel] = (byte)((byte)(chip.block[channel] << 2) | ym3438_const.fn_note[chip.fnum[channel] >> 7]);
                            break;
                        case 0xa4: // a4?
                            chip.reg_a4 = (byte)(chip.data & 0xff);
                            break;
                        case 0xa8: // fnum, block, kcode 3ch
                            chip.fnum_3ch[channel] = (short)((chip.data & 0xff) | ((chip.reg_ac & 0x07) << 8));
                            chip.block_3ch[channel] = (byte)((chip.reg_ac >> 3) & 0x07);
                            chip.kcode_3ch[channel] = (byte)((byte)(chip.block_3ch[channel] << 2) | ym3438_const.fn_note[chip.fnum_3ch[channel] >> 7]);
                            break;
                        case 0xac: //ac?
                            chip.reg_ac = (byte)(chip.data & 0xff);
                            break;
                        case 0xb0: // Connect FeedBack
                            chip.connect[channel] = (byte)(chip.data & 0x07);
                            chip.fb[channel] = (byte)((chip.data >> 3) & 0x07);
                            break;
                        case 0xb4: //Modulate Pan
                            chip.pms[channel] = (byte)(chip.data & 0x07);
                            chip.ams[channel] = (byte)((chip.data >> 4) & 0x03);
                            chip.pan_l[channel] = (byte)((chip.data >> 7) & 0x01);
                            chip.pan_r[channel] = (byte)((chip.data >> 6) & 0x01);
                            break;
                        default:
                            break;
                    }
                }
            }
            if (chip.write_a_en != 0 || chip.write_d_en != 0)
            {
                if (chip.write_a_en != 0)
                { // True?
                    chip.write_fm_data = 0;
                }
                if (chip.write_fm_address != 0 && chip.write_d_en != 0)
                {
                    chip.write_fm_data = 1;
                }

                if (chip.write_a_en != 0)
                {
                    if ((chip.write_data & 0xf0) != 0x00)
                    {
                        chip.address = chip.write_data;
                        chip.write_fm_address = 1;
                    }
                    else
                    {
                        chip.write_fm_address = 0;
                    }
                }
                //mlog("d_en:{0} wdata:{1} adr:{2}\n", chip.write_d_en, chip.write_data, chip.address);
                if (chip.write_d_en != 0 && (chip.write_data & 0x100) == 0)
                {
                    switch (chip.address)
                    {
                        case 0x21: /* LSI test 1 */
                            for (i = 0; i < 8; i++)
                            {
                                chip.mode_test_21[i] = (byte)((chip.write_data >> i) & 0x01);
                            }
                            break;
                        case 0x22: /* LFO control */
                            if (((chip.write_data >> 3) & 0x01) != 0)
                            {
                                chip.lfo_en = 0x7f;
                            }
                            else
                            {
                                chip.lfo_en = 0;
                            }
                            chip.lfo_freq = (byte)(chip.write_data & 0x07);
                            break;
                        case 0x24: /* Timer A */
                            chip.timer_a_reg &= 0x03;
                            chip.timer_a_reg |= (byte)((chip.write_data & 0xff) << 2);
                            break;
                        case 0x25:
                            chip.timer_a_reg &= 0x3fc;
                            chip.timer_a_reg |= (byte)(chip.write_data & 0x03);
                            break;
                        case 0x26: /* Timer B */
                            chip.timer_b_reg = (byte)(chip.write_data & 0xff);
                            break;
                        case 0x27: /* CSM, Timer control */
                            chip.mode_ch3 = (byte)((chip.write_data & 0xc0) >> 6);
                            chip.mode_csm = (byte)(chip.mode_ch3 == 2 ? 1 : 0);
                            chip.timer_a_load = (byte)(chip.write_data & 0x01);
                            chip.timer_a_enable = (byte)((chip.write_data >> 2) & 0x01);
                            chip.timer_a_reset = (byte)((chip.write_data >> 4) & 0x01);
                            chip.timer_b_load = (byte)((chip.write_data >> 1) & 0x01);
                            chip.timer_b_enable = (byte)((chip.write_data >> 3) & 0x01);
                            chip.timer_b_reset = (byte)((chip.write_data >> 5) & 0x01);
                            break;
                        case 0x28: /* Key on/off */
                            for (i = 0; i < 4; i++)
                            {
                                chip.mode_kon_operator[i] = (byte)((chip.write_data >> (4 + i)) & 0x01);
                            }
                            if ((chip.write_data & 0x03) == 0x03)
                            {
                                /* Invalid address */
                                chip.mode_kon_channel = (byte) 0xff;
                            }
                            else
                            {
                                chip.mode_kon_channel = (byte)((chip.write_data & 0x03) + ((chip.write_data >> 2) & 1) * 3);
                            }
                            //mlog("kon_ope:{0}:{1}:{2}:{3} kon_ch:{4}\n"
                                //, chip.mode_kon_operator[0]
                                //, chip.mode_kon_operator[1]
                                //, chip.mode_kon_operator[2]
                                //, chip.mode_kon_operator[3]
                                //, chip.mode_kon_channel
                                //);
                            break;
                        case 0x2a: /* DAC data */
                            chip.dacdata &= 0x01;
                            chip.dacdata |= (short)((chip.write_data ^ 0x80) << 1);
                            break;
                        case 0x2b: /* DAC enable */
                            chip.dacen = (byte)(chip.write_data >> 7);
                            break;
                        case 0x2c: /* LSI test 2 */
                            for (i = 0; i < 8; i++)
                            {
                                chip.mode_test_2c[i] = (byte)((chip.write_data >> i) & 0x01);
                            }
                            chip.dacdata &= 0x1fe;
                            chip.dacdata |= (short)(chip.mode_test_2c[3]);
                            chip.eg_custom_timer = (byte)(((chip.mode_test_2c[7] == 0) && (chip.mode_test_2c[6] != 0)) ? 1 : 0); //todo
                            break;
                        default:
                            break;
                    }
                }
                if (chip.write_a_en != 0)
                {
                    chip.write_fm_mode_a = (byte)(chip.write_data & 0xff);
                }
            }

            if (chip.write_fm_data != 0)
            {
                chip.data = (byte)(chip.write_data & 0xff);
            }
        }

        public void OPN2_PhaseCalcIncrement(ym3438_ chip)
        {
            int fnum = chip.pg_fnum;
            int fnum_h = fnum >> 4;
            int fm;
            int basefreq;
            byte lfo = chip.lfo_pm;
            byte lfo_l = (byte)(lfo & 0x0f);
            byte pms = chip.pms[chip.channel];
            byte dt = chip.dt[chip.slot];
            byte dt_l = (byte)(dt & 0x03);
            byte detune = 0;
            byte block, note;
            byte sum, sum_h, sum_l;
            byte kcode = (byte)(chip.pg_kcode);

            fnum <<= 1;
            if ((lfo_l & 0x08) != 0)
            {
                lfo_l ^= 0x0f;
            }
            fm = (fnum_h >> (int)ym3438_const.pg_lfo_sh1[pms][lfo_l]) + (fnum_h >> (int)ym3438_const.pg_lfo_sh2[pms][lfo_l]);
            if (pms > 5)
            {
                fm <<= pms - 5;
            }
            fm >>= 2;
            if ((lfo & 0x10) != 0)
            {
                fnum -= fm;
            }
            else
            {
                fnum += fm;
            }
            fnum &= 0xfff;

            basefreq = (fnum << chip.pg_block) >> 2;
            //System.err.printf("040   basefreq:{0} fnum:{1} chip.pg_block:{2}\n", basefreq, fnum, chip.pg_block);

            /* Apply detune */
            if (dt_l != 0)
            {
                if (kcode > 0x1c)
                {
                    kcode = 0x1c;
                }
                block = (byte)(kcode >> 2);
                note = (byte)(kcode & 0x03);
                sum = (byte)(block + 9 + (((dt_l == 3) ? 1 : 0) | (dt_l & 0x02)));
                sum_h = (byte)(sum >> 1);
                sum_l = (byte)(sum & 0x01);
                detune = (byte)(ym3438_const.pg_detune[(sum_l << 2) | note] >> (9 - sum_h));
            }
            if ((dt & 0x04) != 0)
            {
                basefreq -= detune;
            }
            else
            {
                basefreq += detune;
            }
            basefreq &= 0x1ffff;
            chip.pg_inc[chip.slot] = (basefreq * chip.multi[chip.slot]) >> 1;
            chip.pg_inc[chip.slot] &= 0xfffff;


        }

        public void OPN2_PhaseGenerate(ym3438_ chip)
        {
            int slot;
            /* Mask increment */
            slot = (chip.slot + 20) % 24;
            if (chip.pg_reset[slot] != 0)
            {
                chip.pg_inc[slot] = 0;
            }
            /* Phase step */
            slot = (chip.slot + 19) % 24;
            chip.pg_phase[slot] += chip.pg_inc[slot];
            chip.pg_phase[slot] &= 0xfffff;
            if (chip.pg_reset[slot] != 0 || chip.mode_test_21[3] != 0)
            {
                chip.pg_phase[slot] = 0;
            }
        }

        public void OPN2_EnvelopeSSGEG(ym3438_ chip)
        {
            int slot = chip.slot;
            byte direction = 0;
            chip.eg_ssg_pgrst_latch[slot] = 0;
            chip.eg_ssg_repeat_latch[slot] = 0;
            chip.eg_ssg_hold_up_latch[slot] = 0;
            chip.eg_ssg_inv[slot] = 0;
            if ((chip.ssg_eg[slot] & 0x08) != 0)
            {
                direction = chip.eg_ssg_dir[slot];
                if ((chip.eg_level[slot] & 0x200) != 0)
                {
                    /* Reset */
                    if ((chip.ssg_eg[slot] & 0x03) == 0x00)
                    {
                        chip.eg_ssg_pgrst_latch[slot] = 1;
                    }
                    /* Repeat */
                    if ((chip.ssg_eg[slot] & 0x01) == 0x00)
                    {
                        chip.eg_ssg_repeat_latch[slot] = 1;
                    }
                    /* Inverse */
                    if ((chip.ssg_eg[slot] & 0x03) == 0x02)
                    {
                        direction ^= 1;
                    }
                    if ((chip.ssg_eg[slot] & 0x03) == 0x03)
                    {
                        direction = 1;
                    }
                }
                /* Hold up */
                if (chip.eg_kon_latch[slot] != 0
                 && ((chip.ssg_eg[slot] & 0x07) == 0x05 || (chip.ssg_eg[slot] & 0x07) == 0x03))
                {
                    chip.eg_ssg_hold_up_latch[slot] = 1;
                }
                direction &= chip.eg_kon[slot];
                chip.eg_ssg_inv[slot] = (byte)(
                        (
                            chip.eg_ssg_dir[slot]
                            ^ ((chip.ssg_eg[slot] >> 2) & 0x01)
                        )
                        & chip.eg_kon[slot]
                );
            }
            chip.eg_ssg_dir[slot] = direction;
            chip.eg_ssg_enable[slot] = (byte)((chip.ssg_eg[slot] >> 3) & 0x01);
        }

        private void OPN2_EnvelopeADSR(ym3438_ chip)
        {
            int slot = (chip.slot + 22) % 24;

            byte nkon = chip.eg_kon_latch[slot];
            //mlog("nkon:{0}\n", nkon);
            byte okon = chip.eg_kon[slot];
            byte kon_event;
            byte koff_event;
            byte eg_off;
            short level;
            short nextlevel = 0;
            short ssg_level;
            byte nextstate = chip.eg_state[slot];
            short inc = 0;
            chip.eg_read[0] = chip.eg_read_inc;
            chip.eg_read_inc = (byte)(chip.eg_inc > 0 ? 1 : 0);

            /* Reset phase generator */
            chip.pg_reset[slot] = (byte)(((nkon != 0 && okon == 0) || chip.eg_ssg_pgrst_latch[slot] != 0) ? 1 : 0);

            /* KeyOn/Off */
            kon_event = (byte)(((nkon != 0 && okon == 0) || (okon != 0 && chip.eg_ssg_repeat_latch[slot] != 0)) ? 1 : 0);
            koff_event = (byte)((okon != 0 && nkon == 0) ? 1 : 0);

            ssg_level = level = (short)chip.eg_level[slot];

            if (chip.eg_ssg_inv[slot] != 0)
            {
                /* Inverse */
                ssg_level = (short)(512 - level);
                ssg_level &= 0x3ff;
            }
            if (koff_event != 0)
            {
                level = ssg_level;
            }
            if (chip.eg_ssg_enable[slot] != 0)
            {
                eg_off = (byte)(level >> 9);
            }
            else
            {
                eg_off = (byte)((level & 0x3f0) == 0x3f0 ? 1 : 0);
            }
            nextlevel = level;
            //mlog("nextlevel:{0} chip.eg_state[slot]:{1} slot:{2}\n", nextlevel, chip.eg_state[slot],slot);
            if (kon_event != 0)
            {
                nextstate = (byte)EG_PARAM.eg_num_attack.ordinal();
                /* Instant attack */
                if (chip.eg_ratemax != 0)
                {
                    nextlevel = 0;
                }
                else if (chip.eg_state[slot] == (byte)EG_PARAM.eg_num_attack.ordinal() && level != 0 && chip.eg_inc != 0 && nkon != 0)
                {
                    inc = (short)((~level << chip.eg_inc) >> 5);
                }
                //mlog("inc:{0}\n", inc);
            }
            else
            {
                switch (EG_PARAM.valueOf(chip.eg_state[slot]))
                {
                    case eg_num_attack:
                        if (level == 0)
                        {
                            nextstate = (byte)EG_PARAM.eg_num_decay.ordinal();
                        }
                        else if (chip.eg_inc != 0 && chip.eg_ratemax == 0 && nkon != 0)
                        {
                            inc = (short)((~level << chip.eg_inc) >> 5);
                        }
                        //mlog("ainc:{0}\n", inc);
                        break;
                    case eg_num_decay:
                        if ((level >> 5) == chip.eg_sl[1])
                        {
                            nextstate = (byte)EG_PARAM.eg_num_sustain.ordinal();
                        }
                        else if (eg_off == 0 && chip.eg_inc != 0)
                        {
                            inc = (short)(1 << (chip.eg_inc - 1));
                            if (chip.eg_ssg_enable[slot] != 0)
                            {
                                inc <<= 2;
                            }
                        }
                        //mlog("dinc:{0}\n", inc);
                        break;
                    case eg_num_sustain:
                    case eg_num_release:
                        if (eg_off == 0 && chip.eg_inc != 0)
                        {
                            inc = (short)(1 << (chip.eg_inc - 1));
                            if (chip.eg_ssg_enable[slot] != 0)
                            {
                                inc <<= 2;
                            }
                        }
                        //mlog("srinc:{0}\n", inc);
                        break;
                    default:
                        break;
                }
                if (nkon == 0)
                {
                    nextstate = (byte)EG_PARAM.eg_num_release.ordinal();
                    //mlog("1rel\n", inc);
                }
            }
            if (chip.eg_kon_csm[slot] != 0)
            {
                nextlevel |= (short)(chip.eg_tl[1] << 3);
            }

            /* Envelope off */
            if (kon_event == 0 && chip.eg_ssg_hold_up_latch[slot] == 0 && chip.eg_state[slot] != (byte)EG_PARAM.eg_num_attack.ordinal() && eg_off != 0)
            {
                nextstate = (byte)EG_PARAM.eg_num_release.ordinal();
                nextlevel = 0x3ff;
                //mlog("2rel\n", inc);
            }

            nextlevel += inc;
            //mlog("nextlevel:{0}\n", nextlevel);

            chip.eg_kon[slot] = chip.eg_kon_latch[slot];
            chip.eg_level[slot] = (short)((short)nextlevel & 0x3ff);
            chip.eg_state[slot] = nextstate;
            //mlog("chip.eg_level[slot]:{0} slot:{1}\n", chip.eg_level[slot], slot);
        }

        private void OPN2_EnvelopePrepare(ym3438_ chip)
        {
            byte rate;
            byte sum;
            byte inc = 0;
            int slot = chip.slot;
            byte rate_sel;

            /* Prepare increment */
            rate = (byte)((chip.eg_rate << 1) + chip.eg_ksv);

            if (rate > 0x3f)
            {
                rate = 0x3f;
            }

            sum = (byte)(((rate >> 2) + chip.eg_shift_lock) & 0x0f);
            if (chip.eg_rate != 0 && chip.eg_quotient == 2)
            {
                if (rate < 48)
                {
                    switch (sum)
                    {
                        case 12:
                            inc = 1;
                            break;
                        case 13:
                            inc = (byte)((rate >> 1) & 0x01);
                            break;
                        case 14:
                            inc = (byte)(rate & 0x01);
                            break;
                        default:
                            break;
                    }
                }
                else
                {
                    inc = (byte)(ym3438_const.eg_stephi[rate & 0x03][chip.eg_timer_low_lock] + (rate >> 2) - 11);
                    if (inc > 4)
                    {
                        inc = 4;
                    }
                }
            }
            chip.eg_inc = inc;
            chip.eg_ratemax = (byte)((rate >> 1) == 0x1f ? 1 : 0);

            /* Prepare rate & ksv */
            rate_sel = chip.eg_state[slot];
            if ((chip.eg_kon[slot] != 0 && chip.eg_ssg_repeat_latch[slot] != 0)
             || (chip.eg_kon[slot] == 0 && chip.eg_kon_latch[slot] != 0))
            {
                rate_sel = (byte)EG_PARAM.eg_num_attack.ordinal();
            }
            switch (EG_PARAM.valueOf(rate_sel))
            {
                case eg_num_attack:
                    chip.eg_rate = chip.ar[slot];
                    break;
                case eg_num_decay:
                    chip.eg_rate = chip.dr[slot];
                    break;
                case eg_num_sustain:
                    chip.eg_rate = chip.sr[slot];
                    break;
                case eg_num_release:
                    chip.eg_rate = (byte)((chip.rr[slot] << 1) | 0x01);
                    break;
                default:
                    break;
            }
            chip.eg_ksv = (byte)(chip.pg_kcode >> (chip.ks[slot] ^ 0x03));
            if (chip.am[slot] != 0)
            {
                chip.eg_lfo_am = (byte)(chip.lfo_am >> ym3438_const.eg_am_shift[chip.ams[chip.channel]]);
            }
            else
            {
                chip.eg_lfo_am = 0;
            }
            /* Delay TL & SL value */
            chip.eg_tl[1] = chip.eg_tl[0];
            chip.eg_tl[0] = chip.tl[slot];
            chip.eg_sl[1] = chip.eg_sl[0];
            chip.eg_sl[0] = chip.sl[slot];
        }

        private void OPN2_EnvelopeGenerate(ym3438_ chip)
        {
            int slot = (chip.slot + 23) % 24;
            short level;

            level = chip.eg_level[slot];
            //mlog("level:{0}\n", level);

            if (chip.eg_ssg_inv[slot] != 0)
            {
                /* Inverse */
                level = (short)(512 - level);
            }
            if (chip.mode_test_21[5] != 0)
            {
                level = 0;
            }
            level &= 0x3ff;

            /* Apply AM LFO */
            level += chip.eg_lfo_am;

            /* Apply TL */
            if (!(chip.mode_csm != 0 && chip.channel == 2 + 1))
            {
                level += (short)(chip.eg_tl[0] << 3);
            }
            if (level > 0x3ff)
            {
                level = 0x3ff;
            }
            chip.eg_out[slot] = level;
            //mlog("chip.eg_out[slot]:{0} slot:{1}\n", chip.eg_out[slot], slot);
        }

        private void OPN2_UpdateLFO(ym3438_ chip)
        {
            if ((chip.lfo_quotient & ym3438_const.lfo_cycles[chip.lfo_freq]) == ym3438_const.lfo_cycles[chip.lfo_freq])
            {
                chip.lfo_quotient = 0;
                chip.lfo_cnt++;
            }
            else
            {
                chip.lfo_quotient += chip.lfo_inc;
            }
            chip.lfo_cnt &= chip.lfo_en;
        }

        private void OPN2_FMPrepare(ym3438_ chip)
        {
            int slot = (chip.slot + 6) % 24;
            int channel = chip.channel;
            short mod, mod1, mod2;
            int op = slot / 6;
            byte connect = chip.connect[channel];
            int prevslot = (chip.slot + 18) % 24;

            /* Calculate modulation */
            mod1 = mod2 = 0;

            if (ym3438_const.fm_algorithm[op][0][connect] != 0)
            {
                mod2 |= chip.fm_op1[channel][0];
            }
            if (ym3438_const.fm_algorithm[op][1][connect] != 0)
            {
                mod1 |= chip.fm_op1[channel][1];
            }
            if (ym3438_const.fm_algorithm[op][2][connect] != 0)
            {
                mod1 |= chip.fm_op2[channel];
            }
            if (ym3438_const.fm_algorithm[op][3][connect] != 0)
            {
                mod2 |= chip.fm_out[prevslot];
            }
            if (ym3438_const.fm_algorithm[op][4][connect] != 0)
            {
                mod1 |= chip.fm_out[prevslot];
            }
            mod = (short)(mod1 + mod2);
            if (op == 0)
            {
                /* Feedback */
                mod = (short)(mod >> (10 - chip.fb[channel]));
                if (chip.fb[channel] == 0)
                {
                    mod = 0;
                }
            }
            else
            {
                mod >>= 1;
            }
            chip.fm_mod[slot] = (short)mod;

            slot = (chip.slot + 18) % 24;
            /* OP1 */
            if (slot / 6 == 0)
            {
                chip.fm_op1[channel][1] = chip.fm_op1[channel][0];
                chip.fm_op1[channel][0] = chip.fm_out[slot];
            }
            /* OP2 */
            if (slot / 6 == 2)
            {
                chip.fm_op2[channel] = chip.fm_out[slot];
            }
        }

        private void OPN2_ChGenerate(ym3438_ chip)
        {
            int slot = (chip.slot + 18) % 24;
            int channel = chip.channel;
            int op = slot / 6;
            int test_dac = (int)(chip.mode_test_2c[5]);
            short acc = chip.ch_acc[channel];
            short add = (short)test_dac;
            short sum = 0;
            if (op == 0 && test_dac == 0)
            {
                acc = 0;
            }
            if (ym3438_const.fm_algorithm[op][5][chip.connect[channel]] != 0 && test_dac == 0)
            {
                add += (short)(chip.fm_out[slot] >> 5);
                //mlog("040   chip.fm_out[slot]:{0} slot:{1}\n", chip.fm_out[slot], slot);
            }
            sum = (short)(acc + add);
            //mlog("040   acc:{0} add:{1}\n", acc, add);
            /* Clamp */
            if (sum > 255)
            {
                sum = 255;
            }
            else if (sum < -256)
            {
                sum = -256;
            }

            if (op == 0 || test_dac != 0)
            {
                chip.ch_out[channel] = chip.ch_acc[channel];
            }
            chip.ch_acc[channel] = sum;
        }

        private void OPN2_ChOutput(ym3438_ chip)
        {
            int cycles = chip.cycles;
            int channel = chip.channel;
            int test_dac = (int)(chip.mode_test_2c[5]);
            short out_;
            short sign;
            int out_en;
            chip.ch_read = chip.ch_lock;
            if (chip.slot < 12)
            {
                /* Ch 4,5,6 */
                channel++;
            }
            if ((cycles & 3) == 0)
            {
                if (test_dac == 0)
                {
                    /* Lock value */
                    chip.ch_lock = chip.ch_out[channel];
                }
                chip.ch_lock_l = chip.pan_l[channel];
                chip.ch_lock_r = chip.pan_r[channel];
            }
            /* Ch 6 */
            if (((cycles >> 2) == 1 && chip.dacen != 0) || test_dac != 0)
            {
                out_ = (short)chip.dacdata;
                out_ <<= 7;
                out_ >>= 7;
            }
            else
            {
                out_ = chip.ch_lock;
            }

            //chip.mol = 0;
            //chip.mor = 0;
            if (ym3438_const.chip_type == ym3438_const.ym3438_type.ym2612)
            {

                out_en = (int)((((cycles & 3) == 3) || test_dac != 0) ? 1 : 0);
                /* YM2612 DAC emulation(not verified) */
                sign = (short)(out_ >> 8);
                if (out_ >= 0)
                {
                    out_++;
                    sign++;
                }

                chip.mol = sign;
                chip.mor = sign;

                if (chip.ch_lock_l != 0 && out_en != 0)
                {
                    chip.mol = out_;
                }
                //else
                //{
                //    chip.mol = sign;
                //}
                //System.err.printf("040   out:{0} sign:{1}\n", out_, sign);
                if (chip.ch_lock_r != 0 && out_en != 0)
                {
                    chip.mor = out_;
                }
                //else
                //{
                //    chip.mor = sign;
                //}
                /* Amplify signal */
                chip.mol *= 3;
                chip.mor *= 3;
            }
            else
            {
                chip.mol = 0;
                chip.mor = 0;

                out_en = (int)((((cycles & 3) != 0) || test_dac != 0) ? 1 : 0);
                /* Discrete YM3438 seems has the ladder effect too */
                if (out_ >= 0 && ym3438_const.chip_type == ym3438_const.ym3438_type.discrete)
                {
                    out_++;
                }
                if (chip.ch_lock_l != 0 && out_en != 0)
                {
                    chip.mol = out_;
                }
                if (chip.ch_lock_r != 0 && out_en != 0)
                {
                    chip.mor = out_;
                }
            }
        }

        private void OPN2_FMGenerate(ym3438_ chip)
        {
            int slot = (chip.slot + 19) % 24;
            /* Calculate phase */
            short phase = (short)((chip.fm_mod[slot] + (chip.pg_phase[slot] >> 10)) & 0x3ff);
            //mlog("040   chip.fm_mod[slot]:{0} chip.pg_phase[slot]:{1}\n", chip.fm_mod[slot], chip.pg_phase[slot]);
            short quarter;
            short level;
            short output;
            if ((phase & 0x100) != 0)
            {
                quarter = (short)((phase ^ 0xff) & 0xff);
            }
            else
            {
                quarter = (short)(phase & 0xff);
            }
            level = (short) ym3438_const.logsinrom[quarter];
            /* Apply envelope */
            level += (short)(chip.eg_out[slot] << 2);
            //mlog("040   quarter:{0} chip.eg_out[slot]:{1} slot:{2}\n", quarter, chip.eg_out[slot], slot);
            /* Transform */
            if (level > 0x1fff)
            {
                level = 0x1fff;
            }
            output = (short)(((ym3438_const.exprom[(level & 0xff) ^ 0xff] | 0x400) << 2) >> (level >> 8));
            //mlog("040   output:{0} level:{1}\n", output, level);
            if ((phase & 0x200) != 0)
            {
                output = (short)(((~output) ^ (chip.mode_test_21[4] << 13)) + 1);
            }
            else
            {
                output = (short)(output ^ (chip.mode_test_21[4] << 13));
            }
            output <<= 2;
            output >>= 2;
            chip.fm_out[slot] = output;
        }

        private void OPN2_DoTimerA(ym3438_ chip)
        {
            short time;
            byte load;
            load = chip.timer_a_overflow;
            if (chip.cycles == 2)
            {
                /* Lock load value */
                load |= (byte)((chip.timer_a_load_lock == 0 && chip.timer_a_load != 0) ? 1 : 0);
                chip.timer_a_load_lock = chip.timer_a_load;
                if (chip.mode_csm != 0)
                {
                    /* CSM KeyOn */
                    chip.mode_kon_csm = load;
                }
                else
                {
                    chip.mode_kon_csm = 0;
                }
            }
            /* Load counter */
            if (chip.timer_a_load_latch != 0)
            {
                time = chip.timer_a_reg;
            }
            else
            {
                time = chip.timer_a_cnt;
            }
            chip.timer_a_load_latch = load;
            /* Increase counter */
            if ((chip.cycles == 1 && chip.timer_a_load_lock != 0) || chip.mode_test_21[2] != 0)
            {
                time++;
            }
            /* Set overflow flag */
            if (chip.timer_a_reset != 0)
            {
                chip.timer_a_reset = 0;
                chip.timer_a_overflow_flag = 0;
            }
            else
            {
                chip.timer_a_overflow_flag |= (byte)(chip.timer_a_overflow & chip.timer_a_enable);
            }
            chip.timer_a_overflow = (byte)(time >> 10);
            chip.timer_a_cnt = (short)(time & 0x3ff);
        }

        private void OPN2_DoTimerB(ym3438_ chip)
        {
            short time;
            byte load;
            load = chip.timer_b_overflow;
            if (chip.cycles == 2)
            {
                /* Lock load value */
                load |= (byte)((chip.timer_b_load_lock == 0 && chip.timer_b_load != 0) ? 1 : 0);
                chip.timer_b_load_lock = chip.timer_b_load;
            }
            /* Load counter */
            if (chip.timer_b_load_latch != 0)
            {
                time = chip.timer_b_reg;
            }
            else
            {
                time = chip.timer_b_cnt;
            }
            chip.timer_b_load_latch = load;
            /* Increase counter */
            if (chip.cycles == 1)
            {
                chip.timer_b_subcnt++;
            }
            if ((chip.timer_b_subcnt == 0x10 && chip.timer_b_load_lock != 0) || chip.mode_test_21[2] != 0)
            {
                time++;
            }
            chip.timer_b_subcnt &= 0x0f;
            /* Set overflow flag */
            if (chip.timer_b_reset != 0)
            {
                chip.timer_b_reset = 0;
                chip.timer_b_overflow_flag = 0;
            }
            else
            {
                chip.timer_b_overflow_flag |= (byte)(chip.timer_b_overflow & chip.timer_b_enable);
            }
            chip.timer_b_overflow = (byte)(time >> 8);
            chip.timer_b_cnt = (byte)(time & 0xff);
        }

        private void OPN2_KeyOn(ym3438_ chip)
        {
            /* Key On */
            chip.eg_kon_latch[chip.slot] = chip.mode_kon[chip.slot];
            chip.eg_kon_csm[chip.slot] = 0;
            //mlog("chip.eg_kon_latch[chip.slot]:{0} slot:{1}\n", chip.eg_kon_latch[chip.slot], chip.slot);
            if (chip.channel == 2 && chip.mode_kon_csm != 0)
            {
                /* CSM Key On */
                chip.eg_kon_latch[chip.slot] = 1;
                chip.eg_kon_csm[chip.slot] = 1;
            }
            if (chip.cycles == chip.mode_kon_channel)
            {
                /* OP1 */
                chip.mode_kon[chip.channel] = chip.mode_kon_operator[0];
                /* OP2 */
                chip.mode_kon[chip.channel + 12] = chip.mode_kon_operator[1];
                /* OP3 */
                chip.mode_kon[chip.channel + 6] = chip.mode_kon_operator[2];
                /* OP4 */
                chip.mode_kon[chip.channel + 18] = chip.mode_kon_operator[3];
            }
        }

        private void OPN2_Reset(ym3438_ chip, int rate, int clock)
        {
            int i, rateratio;
            rateratio = (int)chip.rateratio;
            //chip = new ym3438_();
            chip.eg_out = new short[24];
            chip.eg_level = new short[24];
            chip.eg_state = new byte[24];
            chip.multi = new byte[24];
            chip.pan_l = new byte[6];
            chip.pan_r = new byte[6];

            for (i = 0; i < 24; i++)
            {
                chip.eg_out[i] = 0x3ff;
                chip.eg_level[i] = 0x3ff;
                chip.eg_state[i] = (byte)EG_PARAM.eg_num_release.ordinal();
                chip.multi[i] = 1;
            }
            for (i = 0; i < 6; i++)
            {
                chip.pan_l[i] = 1;
                chip.pan_r[i] = 1;
            }
            if (rate != 0)
            {
                chip.rateratio = (int)((((long)(144 * rate)) << 10) / clock);// RSM_FRAC) / clock);
            }
            else
            {
                chip.rateratio = (int)rateratio;
            }
            //mlogsw = true;
            //mlog("rateratio{0} rate{1} clock{2}\n", chip.rateratio,rate,clock);
            //mlogsw = false;
        }

        public void OPN2_SetChipType(ym3438_const.ym3438_type type)
        {
            switch (type)
            {
                case asic:
                    ym3438_const.use_filter = 0;
                    break;
                case discrete:
                    ym3438_const.use_filter = 0;
                    break;
                case ym2612:
                    ym3438_const.use_filter = 1;
                    break;
                case ym2612_u:
                    type = ym3438_const.ym3438_type.ym2612;
                    ym3438_const.use_filter = 0;
                    break;
                case asic_lp:
                    type = ym3438_const.ym3438_type.asic;
                    ym3438_const.use_filter = 1;
                    break;
            }

            ym3438_const.chip_type = type;
        }

        private void OPN2_Clock(ym3438_ chip, int[] buffer)
        {
            //System.err.printf("010 mol:{0} mor:{1}\n", chip.mol, chip.mor);

            chip.lfo_inc = (byte)(chip.mode_test_21[1]);
            chip.pg_read >>= 1;
            chip.eg_read[1] >>= 1;
            chip.eg_cycle++;
            /* Lock envelope generator timer value */
            if (chip.cycles == 1 && chip.eg_quotient == 2)
            {
                if (chip.eg_cycle_stop != 0)
                {
                    chip.eg_shift_lock = 0;
                }
                else
                {
                    chip.eg_shift_lock = (byte)(chip.eg_shift + 1);
                }
                chip.eg_timer_low_lock = (byte)(chip.eg_timer & 0x03);
            }
            /* Cycle specific functions */
            switch (chip.cycles)
            {
                case 0:
                    chip.lfo_pm = (byte)(chip.lfo_cnt >> 2);
                    if ((chip.lfo_cnt & 0x40) != 0)
                    {
                        chip.lfo_am = (byte)(chip.lfo_cnt & 0x3f);
                    }
                    else
                    {
                        chip.lfo_am = (byte)(chip.lfo_cnt ^ 0x3f);
                    }
                    chip.lfo_am <<= 1;
                    break;
                case 1:
                    chip.eg_quotient++;
                    chip.eg_quotient %= 3;
                    chip.eg_cycle = 0;
                    chip.eg_cycle_stop = 1;
                    chip.eg_shift = 0;
                    chip.eg_timer_inc |= (byte)(chip.eg_quotient >> 1);
                    chip.eg_timer = (short)(chip.eg_timer + chip.eg_timer_inc);
                    chip.eg_timer_inc = (byte)(chip.eg_timer >> 12);
                    chip.eg_timer &= 0xfff;
                    break;
                case 2:
                    chip.pg_read = chip.pg_phase[21] & 0x3ff;
                    chip.eg_read[1] = chip.eg_out[0];
                    break;
                case 13:
                    chip.eg_cycle = 0;
                    chip.eg_cycle_stop = 1;
                    chip.eg_shift = 0;
                    chip.eg_timer = (short)(chip.eg_timer + chip.eg_timer_inc);
                    chip.eg_timer_inc = (byte)(chip.eg_timer >> 12);
                    chip.eg_timer &= 0xfff;
                    break;
                case 23:
                    chip.lfo_inc |= 1;
                    break;
            }


            chip.eg_timer &= (short)(~(chip.mode_test_21[5] << chip.eg_cycle));
            if ((((chip.eg_timer >> chip.eg_cycle) | (chip.pin_test_in & chip.eg_custom_timer)) & chip.eg_cycle_stop) != 0)
            {
                chip.eg_shift = chip.eg_cycle;
                chip.eg_cycle_stop = 0;
            }

            //System.err.printf("020 mol:{0} mor:{1}\n", chip.mol, chip.mor);

            OPN2_DoIO(chip);

            //System.err.printf("030 mol:{0} mor:{1}\n", chip.mol, chip.mor);

            OPN2_DoTimerA(chip);
            OPN2_DoTimerB(chip);
            OPN2_KeyOn(chip);

            //System.err.printf("040 mol:{0} mor:{1}\n", chip.mol, chip.mor);

            OPN2_ChOutput(chip);
            //System.err.printf("045 mol:{0} mor:{1}\n", chip.mol, chip.mor);
            OPN2_ChGenerate(chip);

            //System.err.printf("050 mol:{0} mor:{1}\n", chip.mol, chip.mor);

            OPN2_FMPrepare(chip);
            OPN2_FMGenerate(chip);

            //System.err.printf("060 mol:{0} mor:{1}\n", chip.mol, chip.mor);

            OPN2_PhaseGenerate(chip);
            OPN2_PhaseCalcIncrement(chip);

            //System.err.printf("070 mol:{0} mor:{1}\n", chip.mol, chip.mor);

            OPN2_EnvelopeADSR(chip);
            OPN2_EnvelopeGenerate(chip);
            OPN2_EnvelopeSSGEG(chip);
            OPN2_EnvelopePrepare(chip);

            //System.err.printf("080 mol:{0} mor:{1}\n", chip.mol, chip.mor);

            /* Prepare fnum & block */
            if (chip.mode_ch3 != 0)
            {
                /* Channel 3 special mode */
                switch (chip.slot)
                {
                    case 1: /* OP1 */
                        chip.pg_fnum = chip.fnum_3ch[1];
                        chip.pg_block = chip.block_3ch[1];
                        chip.pg_kcode = chip.kcode_3ch[1];
                        break;
                    case 7: /* OP3 */
                        chip.pg_fnum = chip.fnum_3ch[0];
                        chip.pg_block = chip.block_3ch[0];
                        chip.pg_kcode = chip.kcode_3ch[0];
                        break;
                    case 13: /* OP2 */
                        chip.pg_fnum = chip.fnum_3ch[2];
                        chip.pg_block = chip.block_3ch[2];
                        chip.pg_kcode = chip.kcode_3ch[2];
                        break;
                    case 19: /* OP4 */
                    default:
                        chip.pg_fnum = chip.fnum[(chip.channel + 1) % 6];
                        chip.pg_block = chip.block[(chip.channel + 1) % 6];
                        chip.pg_kcode = chip.kcode[(chip.channel + 1) % 6];
                        break;
                }
            }
            else
            {
                chip.pg_fnum = chip.fnum[(chip.channel + 1) % 6];
                chip.pg_block = chip.block[(chip.channel + 1) % 6];
                chip.pg_kcode = chip.kcode[(chip.channel + 1) % 6];
            }

            //System.err.printf("090 mol:{0} mor:{1}\n", chip.mol, chip.mor);

            OPN2_UpdateLFO(chip);
            OPN2_DoRegWrite(chip);
            chip.cycles = (chip.cycles + 1) % 24;
            chip.slot = chip.cycles;
            chip.channel = chip.cycles % 6;

            //System.err.printf("100 mol:{0} mor:{1}\n", chip.mol, chip.mor);

            buffer[0] = chip.mol;
            buffer[1] = chip.mor;

            //System.err.printf("110 mol:{0} mor:{1}\n", chip.mol, chip.mor);
        }

        private void OPN2_Write(ym3438_ chip, int port, byte data)
        {
            //if (port == 1 && data == 0xf1)
            //{
            //    mlogOn();
            //}
            //mlog("port:{0:x} data:{1:x}\n", port, data);

            port &= 3;
            chip.write_data = (short)(((port << 7) & 0x100) | data);
            if ((port & 1) != 0)
            {
                /* Data */
                chip.write_d |= 1;
            }
            else
            {
                /* Address */
                chip.write_a |= 1;
            }
        }

        private void OPN2_SetTestPin(ym3438_ chip, int value)
        {
            chip.pin_test_in = (byte)(value & 1);
        }

        private int OPN2_ReadTestPin(ym3438_ chip)
        {
            if (chip.mode_test_2c[7] == 0)
            {
                return 0;
            }
            return (int)(chip.cycles == 23 ? 1 : 0);
        }

        private int OPN2_ReadIRQPin(ym3438_ chip)
        {
            return (int)(chip.timer_a_overflow_flag | chip.timer_b_overflow_flag);
        }

        private byte OPN2_Read(ym3438_ chip, int port)
        {
            if ((port & 3) == 0 || ym3438_const.chip_type == ym3438_const.ym3438_type.asic)
            {
                if (chip.mode_test_21[6] != 0)
                {
                    /* Read test data */
                    //int slot = (chip.cycles + 18) % 24;
                    short testdata = (short)(((chip.pg_read & 0x01) << 15)
                                    | (((chip.eg_read[chip.mode_test_21[0]]) & 0x01) << 14));
                    if (chip.mode_test_2c[4] != 0)
                    {
                        testdata |= (short)(chip.ch_read & 0x1ff);
                    }
                    else
                    {
                        testdata |= (short)(chip.fm_out[(chip.slot + 18) % 24] & 0x3fff);
                    }
                    if (chip.mode_test_21[7] != 0)
                    {
                        return (byte)(testdata & 0xff);
                    }
                    else
                    {
                        return (byte)(testdata >> 8);
                    }
                }
                else
                {
                    return (byte)((chip.busy << 7) | (chip.timer_b_overflow_flag << 1)
                         | chip.timer_a_overflow_flag);
                }
            }
            return 0;
        }

        private void OPN2_WriteBuffered(byte ChipID, int port, byte data)
        {
            ym3438_ chip = ym3438_[ChipID];
            long time1, time2;
            long skip;

            if ((chip.writebuf[chip.writebuf_last].port & 0x04) != 0)
            {
                OPN2_Write(chip, (int)(chip.writebuf[chip.writebuf_last].port & 0X03),
                           chip.writebuf[chip.writebuf_last].data);

                chip.writebuf_cur = (chip.writebuf_last + 1) % 2048;// OPN_WRITEBUF_SIZE;
                skip = chip.writebuf[chip.writebuf_last].time - chip.writebuf_samplecnt;
                chip.writebuf_samplecnt = chip.writebuf[chip.writebuf_last].time;
                while (skip-- != 0)
                {
                    OPN2_Clock(chip, dmyBuffer);
                }
            }

            chip.writebuf[chip.writebuf_last].port = (byte)((port & 0x03) | 0x04);
            chip.writebuf[chip.writebuf_last].data = data;
            time1 = chip.writebuf_lasttime + 15;// OPN_WRITEBUF_DELAY;
            time2 = chip.writebuf_samplecnt;

            if (time1 < time2)
            {
                time1 = time2;
            }

            chip.writebuf[chip.writebuf_last].time = time1;
            chip.writebuf_lasttime = time1;
            chip.writebuf_last = (chip.writebuf_last + 1) % 2048;// OPN_WRITEBUF_SIZE;
        }

        private void OPN2_GenerateResampled(byte ChipID, int[] buf)
        {
            ym3438_ chip = ym3438_[ChipID];
            int i;
            int mute;

            while (chip.samplecnt >= chip.rateratio)
            {
                chip.oldsamples[0] = chip.samples[0];
                chip.oldsamples[1] = chip.samples[1];
                chip.samples[0] = chip.samples[1] = 0;
                for (i = 0; i < 24; i++)
                {
                    switch (chip.cycles >> 2)
                    {
                        case 0: // Ch 2
                            mute = chip.mute[1];
                            break;
                        case 1: // Ch 6, DAC
                            mute = chip.mute[5 + chip.dacen];
                            break;
                        case 2: // Ch 4
                            mute = chip.mute[3];
                            break;
                        case 3: // Ch 1
                            mute = chip.mute[0];
                            break;
                        case 4: // Ch 5
                            mute = chip.mute[4];
                            break;
                        case 5: // Ch 3
                            mute = chip.mute[2];
                            break;
                        default:
                            mute = 0;
                            break;
                    }
                    OPN2_Clock(chip, grBuffer);
                    //System.err.printf("l{0} r{1}\n", buffer[0], buffer[1]);
                    if (mute == 0)
                    {
                        chip.samples[0] += grBuffer[0];
                        chip.samples[1] += grBuffer[1];
                    }

                    while (chip.writebuf[chip.writebuf_cur].time <= chip.writebuf_samplecnt)
                    {
                        if ((chip.writebuf[chip.writebuf_cur].port & 0x04) == 0)
                        {
                            break;
                        }
                        chip.writebuf[chip.writebuf_cur].port &= 0x03;
                        OPN2_Write(chip, chip.writebuf[chip.writebuf_cur].port,
                                      chip.writebuf[chip.writebuf_cur].data);
                        chip.writebuf_cur = (chip.writebuf_cur + 1) % 2048;// OPN_WRITEBUF_SIZE;
                    }
                    chip.writebuf_samplecnt++;
                }
                if (ym3438_const.use_filter == 0)
                {
                    chip.samples[0] *= 11;// OUTPUT_FACTOR;
                    chip.samples[1] *= 11;// OUTPUT_FACTOR;
                }
                else
                {
                    //chip.samples[0] = chip.oldsamples[0] + FILTER_CUTOFF_I * (chip.samples[0] * OUTPUT_FACTOR_F - chip.oldsamples[0]);
                    //chip.samples[1] = chip.oldsamples[1] + FILTER_CUTOFF_I * (chip.samples[1] * OUTPUT_FACTOR_F - chip.oldsamples[1]);
                    chip.samples[0] = (int)(chip.oldsamples[0] + (1 - 0.512331301282628) * (chip.samples[0] * 12 - chip.oldsamples[0]));
                    chip.samples[1] = (int)(chip.oldsamples[1] + (1 - 0.512331301282628) * (chip.samples[1] * 12 - chip.oldsamples[1]));
                }
                chip.samplecnt -= chip.rateratio;
                //System.err.printf("samplecnt{0}\n", chip.samplecnt);
            }
            buf[0] = (int)((chip.oldsamples[0] * (chip.rateratio - chip.samplecnt)
                             + chip.samples[0] * chip.samplecnt) / chip.rateratio);
            buf[1] = (int)((chip.oldsamples[1] * (chip.rateratio - chip.samplecnt)
                             + chip.samples[1] * chip.samplecnt) / chip.rateratio);
            //mlog("bl{0} br{1} chip.oldsamples[0]{2} chip.samples[0]{3}\n", buf[0], buf[1], chip.oldsamples[0], chip.samples[0]);
            chip.samplecnt += 1 << 10;// RSM_FRAC;
        }

        private void OPN2_GenerateStream(byte ChipID, int[][] sndptr, int numsamples)
        {
            int i;
            //int[] smpl, smpr;
            //smpl = sndptr[0];
            //smpr = sndptr[1];

            for (i = 0; i < numsamples; i++)
            {
                OPN2_GenerateResampled(ChipID, gsBuffer);
                //smpl[i] = gsBuffer[0];
                //smpr[i] = gsBuffer[1];
                sndptr[0][i] = gsBuffer[0];
                sndptr[1][i] = gsBuffer[1];
            }
        }

        public void OPN2_SetOptions(byte flags)
        {
            switch ((flags >> 3) & 0x03)
            {
                case 0x00: // YM2612
                default:
                    OPN2_SetChipType(ym3438_const.ym3438_type.ym2612);
                    break;
                case 0x01: // ASIC YM3438
                    OPN2_SetChipType(ym3438_const.ym3438_type.asic);
                    break;
                case 0x02: // Discrete YM3438
                    OPN2_SetChipType(ym3438_const.ym3438_type.discrete);
                    break;
                case 0x03: // YM2612 without filter emulation
                    OPN2_SetChipType(ym3438_const.ym3438_type.ym2612_u);
                    break;
            }
        }

        public void OPN2_SetMute(byte ChipID, int mute)
        {
            int i;
            for (i = 0; i < 7; i++)
            {
                ym3438_[ChipID].mute[i] = (mute >> (int)i) & 0x01;
            }
        }
        public void OPN2_SetMute(byte ChipID, int ch,boolean mute)
        {
            ym3438_[ChipID].mute[ch & 0x7] = (int)(mute ? 1 : 0);
        }




        public ym3438()
        {
            visVolume = new int[][][] {
                new int[][] { new int[] { 0, 0 } }
                , new int[][] { new int[] { 0, 0 } }
            };

            for (int j = 0; j < 2; j++)
            {
                for (int i = 0; i < ym3438_[j].writebuf.length; i++)
                {
                    ym3438_[j].writebuf[i] = new opn2_writebuf();
                }
            }
        }

        @Override public int Start(byte ChipID, int clock)
        {
            return Start(ChipID, clock, 0, null);
        }

        @Override public int Start(byte ChipID, int clock, int clockValue, Object... option)
        {
            //this.clock = clock;
            //this.clockValue = clockValue;
            //this.option = option;
            //OPN2_SetChipType(ym3438_const.ym3438_type.ym2612_u);//.discrete);//.asic);//.ym2612);
            OPN2_Reset(ym3438_[ChipID], clock, clockValue);
            return clock;
        }

        @Override public void Stop(byte ChipID)
        {
            OPN2_Reset(ym3438_[ChipID], 0, 0);
        }

        @Override public void Reset(byte ChipID)
        {
            OPN2_Reset(ym3438_[ChipID], 0, 0);
        }

        @Override public void Update(byte ChipID, int[][] outputs, int samples)
        {
            OPN2_GenerateStream(ChipID, outputs, (int)samples);

            visVolume[ChipID][0][0] = outputs[0][0];
            visVolume[ChipID][0][1] = outputs[1][0];
        }

        @Override public int Write(byte ChipID, int port, int adr, int data)
        {
            OPN2_WriteBuffered(ChipID, (int)adr, (byte)data);
            return 0;
        }

    }




     class ym3438_
    {
        public int cycles;
        public int slot;
        public int channel;
        public short mol, mor;
        /* IO */
        public short write_data;
        public byte write_a;
        public byte write_d;
        public byte write_a_en;
        public byte write_d_en;
        public byte write_busy;
        public byte write_busy_cnt;
        public byte write_fm_address;
        public byte write_fm_data;
        public byte write_fm_mode_a;
        public short address;
        public byte data;
        public byte pin_test_in;
        public byte pin_irq;
        public byte busy;
        /* LFO */
        public byte lfo_en;
        public byte lfo_freq;
        public byte lfo_pm;
        public byte lfo_am;
        public byte lfo_cnt;
        public byte lfo_inc;
        public byte lfo_quotient;
        /* Phase generator */
        public short pg_fnum;
        public byte pg_block;
        public byte pg_kcode;
        public int[] pg_inc = new int[24];
        public int[] pg_phase = new int[24];
        public byte[] pg_reset = new byte[24];
        public int pg_read;
        /* Envelope generator */
        public byte eg_cycle;
        public byte eg_cycle_stop;
        public byte eg_shift;
        public byte eg_shift_lock;
        public byte eg_timer_low_lock;
        public short eg_timer;
        public byte eg_timer_inc;
        public short eg_quotient;
        public byte eg_custom_timer;
        public byte eg_rate;
        public byte eg_ksv;
        public byte eg_inc;
        public byte eg_ratemax;
        public byte[] eg_sl = new byte[2];
        public byte eg_lfo_am;
        public byte[] eg_tl = new byte[2];
        public byte[] eg_state = new byte[24];
        public short[] eg_level = new short[24];
        public short[] eg_out = new short[24];
        public byte[] eg_kon = new byte[24];
        public byte[] eg_kon_csm = new byte[24];
        public byte[] eg_kon_latch = new byte[24];
        public byte[] eg_csm_mode = new byte[24];
        public byte[] eg_ssg_enable = new byte[24];
        public byte[] eg_ssg_pgrst_latch = new byte[24];
        public byte[] eg_ssg_repeat_latch = new byte[24];
        public byte[] eg_ssg_hold_up_latch = new byte[24];
        public byte[] eg_ssg_dir = new byte[24];
        public byte[] eg_ssg_inv = new byte[24];
        public int[] eg_read = new int[2];
        public byte eg_read_inc;
        /* FM */
        public short[][] fm_op1 = new short[][] { new short[2], new short[2], new short[2], new short[2], new short[2], new short[2] };
        public short[] fm_op2 = new short[6];
        public short[] fm_out = new short[24];
        public short[] fm_mod = new short[24];
        /* Channel */
        public short[] ch_acc = new short[6];
        public short[] ch_out = new short[6];
        public short ch_lock;
        public byte ch_lock_l;
        public byte ch_lock_r;
        public short ch_read;
        /* Timer */
        public short timer_a_cnt;
        public short timer_a_reg;
        public byte timer_a_load_lock;
        public byte timer_a_load;
        public byte timer_a_enable;
        public byte timer_a_reset;
        public byte timer_a_load_latch;
        public byte timer_a_overflow_flag;
        public byte timer_a_overflow;

        public short timer_b_cnt;
        public byte timer_b_subcnt;
        public short timer_b_reg;
        public byte timer_b_load_lock;
        public byte timer_b_load;
        public byte timer_b_enable;
        public byte timer_b_reset;
        public byte timer_b_load_latch;
        public byte timer_b_overflow_flag;
        public byte timer_b_overflow;

        /* Register set */
        public byte[] mode_test_21 = new byte[8];
        public byte[] mode_test_2c = new byte[8];
        public byte mode_ch3;
        public byte mode_kon_channel;
        public byte[] mode_kon_operator = new byte[4];
        public byte[] mode_kon = new byte[24];
        public byte mode_csm;
        public byte mode_kon_csm;
        public byte dacen;
        public short dacdata;

        public byte[] ks = new byte[24];
        public byte[] ar = new byte[24];
        public byte[] sr = new byte[24];
        public byte[] dt = new byte[24];
        public byte[] multi = new byte[24];
        public byte[] sl = new byte[24];
        public byte[] rr = new byte[24];
        public byte[] dr = new byte[24];
        public byte[] am = new byte[24];
        public byte[] tl = new byte[24];
        public byte[] ssg_eg = new byte[24];

        public short[] fnum = new short[6];
        public byte[] block = new byte[6];
        public byte[] kcode = new byte[6];
        public short[] fnum_3ch = new short[6];
        public byte[] block_3ch = new byte[6];
        public byte[] kcode_3ch = new byte[6];
        public byte reg_a4;
        public byte reg_ac;
        public byte[] connect = new byte[6];
        public byte[] fb = new byte[6];
        public byte[] pan_l = new byte[6], pan_r = new byte[6];
        public byte[] ams = new byte[6];
        public byte[] pms = new byte[6];

        public int[] mute = new int[7];
        public int rateratio;
        public int samplecnt;
        public int[] oldsamples = new int[2];
        public int[] samples = new int[2];

        public long writebuf_samplecnt;
        public int writebuf_cur;
        public int writebuf_last;
        public long writebuf_lasttime;
        public opn2_writebuf[] writebuf = new opn2_writebuf[2048];// OPN_WRITEBUF_SIZE];
    }

     class opn2_writebuf
    {
        public long time;
        public byte port;
        public byte data;
    }

