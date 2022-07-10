package mdsound.chips;

import java.util.Arrays;


public class Ym3438 {

    private enum Eg {
        Attack,
        Decay,
        Sustain,
        Release;

        static Eg valueOf(int v) {
            return Arrays.stream(values()).filter(e -> e.ordinal() == v).findFirst().get();
        }
    }

    private static class Opn2WriteBuf {
        private long time;
        private byte port;
        private byte data;
    }

    private int cycles;
    private int slot;
    private int channel;
    private short mol, mor;

    // IO

    private short writeData;
    private byte writeA;
    private byte writeD;
    private byte writeAEn;
    private byte writeDEn;
    private byte writeBusy;
    private byte writeBusyCnt;
    private byte writeFmAddress;
    private byte writeFmData;
    private byte writeFmModeA;
    private short address;
    private byte data;
    private byte pinTestIn;
    private byte pinIrq;
    private byte busy;

    // LFO

    private byte lfoEn;
    private byte lfoFreq;
    private byte lfoPm;
    private byte lfoAm;
    private byte lfoCnt;
    private byte lfoInc;
    private byte lfoQuotient;

    // Phase generator

    private short pgFnum;
    private byte pgBlock;
    private byte pgKcode;
    private int[] pgInc = new int[24];
    private int[] pgPhase = new int[24];
    private byte[] pgReset = new byte[24];
    private int pgRead;
    // Envelope generator
    private byte egCycle;
    private byte egCycleStop;
    private byte egShift;
    private byte egShiftLock;
    private byte egTimerLowLock;
    private short egTimer;
    private byte egTimerInc;
    private short egQuotient;
    private byte egCustomTimer;
    private byte egRate;
    private byte egKsv;
    private byte egInc;
    private byte egRateMax;
    private byte[] egSl = new byte[2];
    private byte egLfoAm;
    private byte[] egTl = new byte[2];
    private byte[] egState = new byte[24];
    private short[] egLevel = new short[24];
    private short[] egOut = new short[24];
    private byte[] egKon = new byte[24];
    private byte[] egKonCsm = new byte[24];
    private byte[] egKonLatch = new byte[24];
    private byte[] egCsmMode = new byte[24];
    private byte[] egSsgEnable = new byte[24];
    private byte[] egSsgPgrstLatch = new byte[24];
    private byte[] egSsgRepeatLatch = new byte[24];
    private byte[] egSsgHoldUpLatch = new byte[24];
    private byte[] egSsgDir = new byte[24];
    private byte[] egSsgInv = new byte[24];
    private int[] egRead = new int[2];
    private byte egReadInc;

    // FM

    private short[][] fmOp1 = new short[][] {new short[2], new short[2], new short[2], new short[2], new short[2], new short[2]};
    private short[] fmOp2 = new short[6];
    private short[] fmOut = new short[24];
    private short[] fmMod = new short[24];

    // Channel

    private short[] chAcc = new short[6];
    private short[] chOut = new short[6];
    private short chLock;
    private byte chLockL;
    private byte chLockR;
    private short chRead;

    // Timer

    private short timerACnt;
    private short timerAReg;
    private byte timerALoadLock;
    private byte timerALoad;
    private byte timerAEnable;
    private byte timerAReset;
    private byte timerALoadLatch;
    private byte timerAOverflowFlag;
    private byte timerAOverflow;

    private short timerBCnt;
    private byte timerBSubCnt;
    private short timerBReg;
    private byte timerBLoadLock;
    private byte timerBLoad;
    private byte timerBEnable;
    private byte timerBReset;
    private byte timerBLoadLatch;
    private byte timerBOverflowFlag;
    private byte timerBOverflow;

    // Register set

    private byte[] modeTest21 = new byte[8];
    private byte[] modeTest2C = new byte[8];
    private byte modeCh3;
    private byte modeKonChannel;
    private byte[] modeKonOperator = new byte[4];
    private byte[] modeKon = new byte[24];
    private byte modeCsm;
    private byte modeKonCsm;
    private byte dacEn;
    private short dacData;

    private byte[] ks = new byte[24];
    private byte[] ar = new byte[24];
    private byte[] sr = new byte[24];
    private byte[] dt = new byte[24];
    private byte[] multi = new byte[24];
    private byte[] sl = new byte[24];
    private byte[] rr = new byte[24];
    private byte[] dr = new byte[24];
    private byte[] am = new byte[24];
    private byte[] tl = new byte[24];
    private byte[] ssgEg = new byte[24];

    private short[] fnum = new short[6];
    private byte[] block = new byte[6];
    private byte[] kcode = new byte[6];
    private short[] fnum3Ch = new short[6];
    private byte[] block3Ch = new byte[6];
    private byte[] kcode3Ch = new byte[6];
    private byte regA4;
    private byte regAc;
    private byte[] connect = new byte[6];
    private byte[] fb = new byte[6];
    private byte[] panL = new byte[6], panR = new byte[6];
    private byte[] ams = new byte[6];
    private byte[] pms = new byte[6];

    private int[] mute = new int[7];
    private int rateRatio;
    private int sampleCnt;
    private int[] oldSamples = new int[2];
    private int[] samples = new int[2];

    private long writeBufSampleCnt;
    private int writeBufCur;
    private int writeBufLast;
    private long writeBufLastTime;
    private Opn2WriteBuf[] writeBuf = new Opn2WriteBuf[2048];

    public Ym3438() {
        for (int i = 0; i < writeBuf.length; i++) {
            writeBuf[i] = new Ym3438.Opn2WriteBuf();
        }
    }

    private void doIO() {
        this.writeAEn = (byte) ((this.writeA & 0x03) == 0x01 ? 1 : 0);
        this.writeDEn = (byte) ((this.writeD & 0x03) == 0x01 ? 1 : 0);
        //Debug.printf("aen:%d den:%d\n", this.write_a_en, this.write_d_en);
        this.writeA <<= 1;
        this.writeD <<= 1;
        // BUSY Counter
        this.busy = this.writeBusy;
        this.writeBusyCnt += this.writeBusy;
        this.writeBusy = (byte) (((this.writeBusy != 0 && ((this.writeBusyCnt >> 5) == 0)) || this.writeDEn != 0) ? 1 : 0);
        this.writeBusyCnt &= 0x1f;
    }

    private void doRegWrite() {
        int slot = this.slot % 12;
        int address;
        int channel = this.channel;
        if (this.writeFmData != 0) {
            if (Ym3438Const.opOffset[slot] == (this.address & 0x107)) {
                if ((this.address & 0x08) != 0) {
                    slot += 12; // OP2? OP4?
                }
                address = this.address & 0xf0;
                switch (address) {
                case 0x30: //DT MULTI
                    this.multi[slot] = (byte) (this.data & 0x0f);
                    if (this.multi[slot] == 0) {
                        this.multi[slot] = 1;
                    } else {
                        this.multi[slot] <<= 1;
                    }
                    this.dt[slot] = (byte) ((this.data >> 4) & 0x07);
                    break;
                case 0x40: //TL
                    this.tl[slot] = (byte) (this.data & 0x7f);
                    break;
                case 0x50: // KS AR
                    this.ar[slot] = (byte) (this.data & 0x1f);
                    this.ks[slot] = (byte) ((this.data >> 6) & 0x03);
                    break;
                case 0x60: // AM DR
                    this.dr[slot] = (byte) (this.data & 0x1f);
                    this.am[slot] = (byte) ((this.data >> 7) & 0x01);
                    break;
                case 0x70: //SR
                    this.sr[slot] = (byte) (this.data & 0x1f);
                    break;
                case 0x80: //SL RR
                    this.rr[slot] = (byte) (this.data & 0x0f);
                    this.sl[slot] = (byte) ((this.data >> 4) & 0x0f);
                    this.sl[slot] |= (byte) ((this.sl[slot] + 1) & 0x10);
                    break;
                case 0x90:
                    this.ssgEg[slot] = (byte) (this.data & 0x0f);
                    break;
                default:
                    break;
                }
            }

            if (Ym3438Const.chOffset[channel] == (this.address & 0x103)) {
                address = this.address & 0xfc;
                switch (address) {
                case 0xa0: //Fnum, Block, kcode
                    this.fnum[channel] = (short) ((this.data & 0xff) | ((this.regA4 & 0x07) << 8));
                    this.block[channel] = (byte) ((this.regA4 >> 3) & 0x07);
                    this.kcode[channel] = (byte) ((byte) (this.block[channel] << 2) | Ym3438Const.fnNote[this.fnum[channel] >> 7]);
                    break;
                case 0xa4: // a4?
                    this.regA4 = (byte) (this.data & 0xff);
                    break;
                case 0xa8: // fnum, block, kcode 3ch
                    this.fnum3Ch[channel] = (short) ((this.data & 0xff) | ((this.regAc & 0x07) << 8));
                    this.block3Ch[channel] = (byte) ((this.regAc >> 3) & 0x07);
                    this.kcode3Ch[channel] = (byte) ((byte) (this.block3Ch[channel] << 2) | Ym3438Const.fnNote[this.fnum3Ch[channel] >> 7]);
                    break;
                case 0xac: //ac?
                    this.regAc = (byte) (this.data & 0xff);
                    break;
                case 0xb0: // Connect FeedBack
                    this.connect[channel] = (byte) (this.data & 0x07);
                    this.fb[channel] = (byte) ((this.data >> 3) & 0x07);
                    break;
                case 0xb4: //Modulate Pan
                    this.pms[channel] = (byte) (this.data & 0x07);
                    this.ams[channel] = (byte) ((this.data >> 4) & 0x03);
                    this.panL[channel] = (byte) ((this.data >> 7) & 0x01);
                    this.panR[channel] = (byte) ((this.data >> 6) & 0x01);
                    break;
                default:
                    break;
                }
            }
        }
        if (this.writeAEn != 0 || this.writeDEn != 0) {
            if (this.writeAEn != 0) { // True?
                this.writeFmData = 0;
            }
            if (this.writeFmAddress != 0 && this.writeDEn != 0) {
                this.writeFmData = 1;
            }

            if (this.writeAEn != 0) {
                if ((this.writeData & 0xf0) != 0x00) {
                    this.address = this.writeData;
                    this.writeFmAddress = 1;
                } else {
                    this.writeFmAddress = 0;
                }
            }
            //Debug.printf("d_en:%d wdata:%d adr:%d\n", this.write_d_en, this.write_data, this.address);
            if (this.writeDEn != 0 && (this.writeData & 0x100) == 0) {
                switch (this.address) {
                case 0x21: // LSI test 1
                    for (int i = 0; i < 8; i++) {
                        this.modeTest21[i] = (byte) ((this.writeData >> i) & 0x01);
                    }
                    break;
                case 0x22: // LFO control
                    if (((this.writeData >> 3) & 0x01) != 0) {
                        this.lfoEn = 0x7f;
                    } else {
                        this.lfoEn = 0;
                    }
                    this.lfoFreq = (byte) (this.writeData & 0x07);
                    break;
                case 0x24: // Timer A
                    this.timerAReg &= 0x03;
                    this.timerAReg |= (byte) ((this.writeData & 0xff) << 2);
                    break;
                case 0x25:
                    this.timerAReg &= 0x3fc;
                    this.timerAReg |= (byte) (this.writeData & 0x03);
                    break;
                case 0x26: // Timer B
                    this.timerBReg = (byte) (this.writeData & 0xff);
                    break;
                case 0x27: // CSM, Timer control
                    this.modeCh3 = (byte) ((this.writeData & 0xc0) >> 6);
                    this.modeCsm = (byte) (this.modeCh3 == 2 ? 1 : 0);
                    this.timerALoad = (byte) (this.writeData & 0x01);
                    this.timerAEnable = (byte) ((this.writeData >> 2) & 0x01);
                    this.timerAReset = (byte) ((this.writeData >> 4) & 0x01);
                    this.timerBLoad = (byte) ((this.writeData >> 1) & 0x01);
                    this.timerBEnable = (byte) ((this.writeData >> 3) & 0x01);
                    this.timerBReset = (byte) ((this.writeData >> 5) & 0x01);
                    break;
                case 0x28: // Key on/off
                    for (int i = 0; i < 4; i++) {
                        this.modeKonOperator[i] = (byte) ((this.writeData >> (4 + i)) & 0x01);
                    }
                    if ((this.writeData & 0x03) == 0x03) {
                        // Invalid address
                        this.modeKonChannel = (byte) 0xff;
                    } else {
                        this.modeKonChannel = (byte) ((this.writeData & 0x03) + ((this.writeData >> 2) & 1) * 3);
                    }
//Debug.printf("kon_ope:%d:%d:%d:%d kon_ch:%d\n", this.modeKonChannel[0], this.modeKonChannel[1]
//, this.modeKonChannel[2], this.modeKonChannel[3], this.modeKonChannel);
                    break;
                case 0x2a: // DAC data
                    this.dacData &= 0x01;
                    this.dacData |= (short) ((this.writeData ^ 0x80) << 1);
                    break;
                case 0x2b: // DAC enable
                    this.dacEn = (byte) (this.writeData >> 7);
                    break;
                case 0x2c: // LSI test 2
                    for (int i = 0; i < 8; i++) {
                        this.modeTest2C[i] = (byte) ((this.writeData >> i) & 0x01);
                    }
                    this.dacData &= 0x1fe;
                    this.dacData |= this.modeTest2C[3];
                    this.egCustomTimer = (byte) (((this.modeTest2C[7] == 0) && (this.modeTest2C[6] != 0)) ? 1 : 0); //todo
                    break;
                default:
                    break;
                }
            }
            if (this.writeAEn != 0) {
                this.writeFmModeA = (byte) (this.writeData & 0xff);
            }
        }

        if (this.writeFmData != 0) {
            this.data = (byte) (this.writeData & 0xff);
        }
    }

    private void phaseCalcIncrement() {
        int fnum = this.pgFnum;
        int fnum_h = fnum >> 4;
        int fm;
        int basefreq;
        byte lfo = this.lfoPm;
        byte lfo_l = (byte) (lfo & 0x0f);
        byte pms = this.pms[this.channel];
        byte dt = this.dt[this.slot];
        byte dt_l = (byte) (dt & 0x03);
        byte detune = 0;
        byte block, note;
        byte sum, sum_h, sum_l;
        byte kcode = this.pgKcode;

        fnum <<= 1;
        if ((lfo_l & 0x08) != 0) {
            lfo_l ^= 0x0f;
        }
        fm = (fnum_h >> Ym3438Const.pgLfoSh1[pms][lfo_l]) + (fnum_h >> Ym3438Const.pgLfoSh2[pms][lfo_l]);
        if (pms > 5) {
            fm <<= pms - 5;
        }
        fm >>= 2;
        if ((lfo & 0x10) != 0) {
            fnum -= fm;
        } else {
            fnum += fm;
        }
        fnum &= 0xfff;

        basefreq = (fnum << this.pgBlock) >> 2;
        //Debug.printf("040   basefreq:%d fnum:%d this.pg_block:%d\n", basefreq, fnum, this.pg_block);

        // Apply detune
        if (dt_l != 0) {
            if (kcode > 0x1c) {
                kcode = 0x1c;
            }
            block = (byte) (kcode >> 2);
            note = (byte) (kcode & 0x03);
            sum = (byte) (block + 9 + (((dt_l == 3) ? 1 : 0) | (dt_l & 0x02)));
            sum_h = (byte) (sum >> 1);
            sum_l = (byte) (sum & 0x01);
            detune = (byte) (Ym3438Const.pgDetune[(sum_l << 2) | note] >> (9 - sum_h));
        }
        if ((dt & 0x04) != 0) {
            basefreq -= detune;
        } else {
            basefreq += detune;
        }
        basefreq &= 0x1ffff;
        this.pgInc[this.slot] = (basefreq * this.multi[this.slot]) >> 1;
        this.pgInc[this.slot] &= 0xfffff;
    }

    private void phaseGenerate() {
        int slot;
        // Mask increment
        slot = (this.slot + 20) % 24;
        if (this.pgReset[slot] != 0) {
            this.pgInc[slot] = 0;
        }
        // Phase step
        slot = (this.slot + 19) % 24;
        this.pgPhase[slot] += this.pgInc[slot];
        this.pgPhase[slot] &= 0xfffff;
        if (this.pgReset[slot] != 0 || this.modeTest21[3] != 0) {
            this.pgPhase[slot] = 0;
        }
    }

    private void envelopeSSGEG() {
        int slot = this.slot;
        byte direction = 0;
        this.egSsgPgrstLatch[slot] = 0;
        this.egSsgRepeatLatch[slot] = 0;
        this.egSsgHoldUpLatch[slot] = 0;
        this.egSsgInv[slot] = 0;
        if ((this.ssgEg[slot] & 0x08) != 0) {
            direction = this.egSsgDir[slot];
            if ((this.egLevel[slot] & 0x200) != 0) {
                // Reset
                if ((this.ssgEg[slot] & 0x03) == 0x00) {
                    this.egSsgPgrstLatch[slot] = 1;
                }
                // Repeat
                if ((this.ssgEg[slot] & 0x01) == 0x00) {
                    this.egSsgRepeatLatch[slot] = 1;
                }
                // Inverse
                if ((this.ssgEg[slot] & 0x03) == 0x02) {
                    direction ^= 1;
                }
                if ((this.ssgEg[slot] & 0x03) == 0x03) {
                    direction = 1;
                }
            }
            // Hold up
            if (this.egKonLatch[slot] != 0
                    && ((this.ssgEg[slot] & 0x07) == 0x05 || (this.ssgEg[slot] & 0x07) == 0x03)) {
                this.egSsgHoldUpLatch[slot] = 1;
            }
            direction &= this.egKon[slot];
            this.egSsgInv[slot] = (byte) (
                    (
                            this.egSsgDir[slot]
                                    ^ ((this.ssgEg[slot] >> 2) & 0x01)
                    )
                            & this.egKon[slot]
            );
        }
        this.egSsgDir[slot] = direction;
        this.egSsgEnable[slot] = (byte) ((this.ssgEg[slot] >> 3) & 0x01);
    }

    private void envelopeADSR() {
        int slot = (this.slot + 22) % 24;

        byte nkon = this.egKonLatch[slot];
        //Debug.printf("nkon:%d\n", nkon);
        byte okon = this.egKon[slot];
        byte kon_event;
        byte koff_event;
        byte eg_off;
        short level;
        short nextlevel = 0;
        short ssg_level;
        byte nextstate = this.egState[slot];
        short inc = 0;
        this.egRead[0] = this.egReadInc;
        this.egReadInc = (byte) (this.egInc > 0 ? 1 : 0);

        // Reset phase generator
        this.pgReset[slot] = (byte) (((nkon != 0 && okon == 0) || this.egSsgPgrstLatch[slot] != 0) ? 1 : 0);

        // KeyOn/Off
        kon_event = (byte) (((nkon != 0 && okon == 0) || (okon != 0 && this.egSsgRepeatLatch[slot] != 0)) ? 1 : 0);
        koff_event = (byte) ((okon != 0 && nkon == 0) ? 1 : 0);

        ssg_level = level = this.egLevel[slot];

        if (this.egSsgInv[slot] != 0) {
            // Inverse
            ssg_level = (short) (512 - level);
            ssg_level &= 0x3ff;
        }
        if (koff_event != 0) {
            level = ssg_level;
        }
        if (this.egSsgEnable[slot] != 0) {
            eg_off = (byte) (level >> 9);
        } else {
            eg_off = (byte) ((level & 0x3f0) == 0x3f0 ? 1 : 0);
        }
        nextlevel = level;
        //Debug.printf("nextlevel:%d this.eg_state[slot]:%d slot:%d\n", nextlevel, this.eg_state[slot],slot);
        if (kon_event != 0) {
            nextstate = (byte) Eg.Attack.ordinal();
            // Instant attack
            if (this.egRateMax != 0) {
                nextlevel = 0;
            } else if (this.egState[slot] == (byte) Eg.Attack.ordinal() && level != 0 && this.egInc != 0 && nkon != 0) {
                inc = (short) ((~level << this.egInc) >> 5);
            }
            //Debug.printf("inc:%d\n", inc);
        } else {
            switch (Eg.valueOf(this.egState[slot])) {
            case Attack:
                if (level == 0) {
                    nextstate = (byte) Eg.Decay.ordinal();
                } else if (this.egInc != 0 && this.egRateMax == 0 && nkon != 0) {
                    inc = (short) ((~level << this.egInc) >> 5);
                }
                //Debug.printf("ainc:%d\n", inc);
                break;
            case Decay:
                if ((level >> 5) == this.egSl[1]) {
                    nextstate = (byte) Eg.Sustain.ordinal();
                } else if (eg_off == 0 && this.egInc != 0) {
                    inc = (short) (1 << (this.egInc - 1));
                    if (this.egSsgEnable[slot] != 0) {
                        inc <<= 2;
                    }
                }
                //Debug.printf("dinc:%d\n", inc);
                break;
            case Sustain:
            case Release:
                if (eg_off == 0 && this.egInc != 0) {
                    inc = (short) (1 << (this.egInc - 1));
                    if (this.egSsgEnable[slot] != 0) {
                        inc <<= 2;
                    }
                }
                //Debug.printf("srinc:%d\n", inc);
                break;
            default:
                break;
            }
            if (nkon == 0) {
                nextstate = (byte) Eg.Release.ordinal();
                //Debug.printf("1rel\n", inc);
            }
        }
        if (this.egKonCsm[slot] != 0) {
            nextlevel |= (short) (this.egTl[1] << 3);
        }

        // Envelope off
        if (kon_event == 0 && this.egSsgHoldUpLatch[slot] == 0 && this.egState[slot] != (byte) Eg.Attack.ordinal() && eg_off != 0) {
            nextstate = (byte) Eg.Release.ordinal();
            nextlevel = 0x3ff;
            //Debug.printf("2rel\n", inc);
        }

        nextlevel += inc;
        //Debug.printf("nextlevel:%d\n", nextlevel);

        this.egKon[slot] = this.egKonLatch[slot];
        this.egLevel[slot] = (short) (nextlevel & 0x3ff);
        this.egState[slot] = nextstate;
        //Debug.printf("this.eg_level[slot]:%d slot:%d\n", this.eg_level[slot], slot);
    }

    private void envelopePrepare() {
        byte rate;
        byte sum;
        byte inc = 0;
        int slot = this.slot;
        byte rate_sel;

        // Prepare increment
        rate = (byte) ((this.egRate << 1) + this.egKsv);

        if (rate > 0x3f) {
            rate = 0x3f;
        }

        sum = (byte) (((rate >> 2) + this.egShiftLock) & 0x0f);
        if (this.egRate != 0 && this.egQuotient == 2) {
            if (rate < 48) {
                switch (sum) {
                case 12:
                    inc = 1;
                    break;
                case 13:
                    inc = (byte) ((rate >> 1) & 0x01);
                    break;
                case 14:
                    inc = (byte) (rate & 0x01);
                    break;
                default:
                    break;
                }
            } else {
                inc = (byte) (Ym3438Const.egStephi[rate & 0x03][this.egTimerLowLock] + (rate >> 2) - 11);
                if (inc > 4) {
                    inc = 4;
                }
            }
        }
        this.egInc = inc;
        this.egRateMax = (byte) ((rate >> 1) == 0x1f ? 1 : 0);

        // Prepare rate & ksv
        rate_sel = this.egState[slot];
        if ((this.egKon[slot] != 0 && this.egSsgRepeatLatch[slot] != 0)
                || (this.egKon[slot] == 0 && this.egKonLatch[slot] != 0)) {
            rate_sel = (byte) Eg.Attack.ordinal();
        }
        switch (Eg.valueOf(rate_sel)) {
        case Attack:
            this.egRate = this.ar[slot];
            break;
        case Decay:
            this.egRate = this.dr[slot];
            break;
        case Sustain:
            this.egRate = this.sr[slot];
            break;
        case Release:
            this.egRate = (byte) ((this.rr[slot] << 1) | 0x01);
            break;
        default:
            break;
        }
        this.egKsv = (byte) (this.pgKcode >> (this.ks[slot] ^ 0x03));
        if (this.am[slot] != 0) {
            this.egLfoAm = (byte) (this.lfoAm >> Ym3438Const.egAmShift[this.ams[this.channel]]);
        } else {
            this.egLfoAm = 0;
        }
        // Delay TL & SL value
        this.egTl[1] = this.egTl[0];
        this.egTl[0] = this.tl[slot];
        this.egSl[1] = this.egSl[0];
        this.egSl[0] = this.sl[slot];
    }

    private void envelopeGenerate() {
        int slot = (this.slot + 23) % 24;
        short level;

        level = this.egLevel[slot];
        //Debug.printf("level:%d\n", level);

        if (this.egSsgInv[slot] != 0) {
            // Inverse
            level = (short) (512 - level);
        }
        if (this.modeTest21[5] != 0) {
            level = 0;
        }
        level &= 0x3ff;

        // Apply AM LFO
        level += this.egLfoAm;

        // Apply TL
        if (!(this.modeCsm != 0 && this.channel == 2 + 1)) {
            level += (short) (this.egTl[0] << 3);
        }
        if (level > 0x3ff) {
            level = 0x3ff;
        }
        this.egOut[slot] = level;
        //Debug.printf("this.eg_out[slot]:%d slot:%d\n", this.eg_out[slot], slot);
    }

    private void updateLFO() {
        if ((this.lfoQuotient & Ym3438Const.lfoCycles[this.lfoFreq]) == Ym3438Const.lfoCycles[this.lfoFreq]) {
            this.lfoQuotient = 0;
            this.lfoCnt++;
        } else {
            this.lfoQuotient += this.lfoInc;
        }
        this.lfoCnt &= this.lfoEn;
    }

    private void fmPrepare() {
        int slot = (this.slot + 6) % 24;
        int channel = this.channel;
        short mod, mod1, mod2;
        int op = slot / 6;
        byte connect = this.connect[channel];
        int prevslot = (this.slot + 18) % 24;

        // Calculate modulation
        mod1 = mod2 = 0;

        if (Ym3438Const.fmAlgorithm[op][0][connect] != 0) {
            mod2 |= this.fmOp1[channel][0];
        }
        if (Ym3438Const.fmAlgorithm[op][1][connect] != 0) {
            mod1 |= this.fmOp1[channel][1];
        }
        if (Ym3438Const.fmAlgorithm[op][2][connect] != 0) {
            mod1 |= this.fmOp2[channel];
        }
        if (Ym3438Const.fmAlgorithm[op][3][connect] != 0) {
            mod2 |= this.fmOut[prevslot];
        }
        if (Ym3438Const.fmAlgorithm[op][4][connect] != 0) {
            mod1 |= this.fmOut[prevslot];
        }
        mod = (short) (mod1 + mod2);
        if (op == 0) {
            // Feedback
            mod = (short) (mod >> (10 - this.fb[channel]));
            if (this.fb[channel] == 0) {
                mod = 0;
            }
        } else {
            mod >>= 1;
        }
        this.fmMod[slot] = mod;

        slot = (this.slot + 18) % 24;
        // OP1
        if (slot / 6 == 0) {
            this.fmOp1[channel][1] = this.fmOp1[channel][0];
            this.fmOp1[channel][0] = this.fmOut[slot];
        }
        // OP2
        if (slot / 6 == 2) {
            this.fmOp2[channel] = this.fmOut[slot];
        }
    }

    private void chGenerate() {
        int slot = (this.slot + 18) % 24;
        int channel = this.channel;
        int op = slot / 6;
        int test_dac = this.modeTest2C[5];
        short acc = this.chAcc[channel];
        short add = (short) test_dac;
        short sum = 0;
        if (op == 0 && test_dac == 0) {
            acc = 0;
        }
        if (Ym3438Const.fmAlgorithm[op][5][this.connect[channel]] != 0 && test_dac == 0) {
            add += (short) (this.fmOut[slot] >> 5);
            //Debug.printf("040   this.fm_out[slot]:%d slot:%d\n", this.fm_out[slot], slot);
        }
        sum = (short) (acc + add);
        //Debug.printf("040   acc:%d add:%d\n", acc, add);
        // Clamp
        if (sum > 255) {
            sum = 255;
        } else if (sum < -256) {
            sum = -256;
        }

        if (op == 0 || test_dac != 0) {
            this.chOut[channel] = this.chAcc[channel];
        }
        this.chAcc[channel] = sum;
    }

    private void chOutput() {
        int cycles = this.cycles;
        int channel = this.channel;
        int test_dac = this.modeTest2C[5];
        short out_;
        short sign;
        int out_en;
        this.chRead = this.chLock;
        if (this.slot < 12) {
            // Ch 4,5,6
            channel++;
        }
        if ((cycles & 3) == 0) {
            if (test_dac == 0) {
                // Lock value
                this.chLock = this.chOut[channel];
            }
            this.chLockL = this.panL[channel];
            this.chLockR = this.panR[channel];
        }
        // Ch 6
        if (((cycles >> 2) == 1 && this.dacEn != 0) || test_dac != 0) {
            out_ = this.dacData;
            out_ <<= 7;
            out_ >>= 7;
        } else {
            out_ = this.chLock;
        }

        //this.mol = 0;
        //this.mor = 0;
        if (Ym3438Const.chip_type == Ym3438Const.Type.ym2612) {

            out_en = (((cycles & 3) == 3) || test_dac != 0) ? 1 : 0;
            // Ym2612Inst DAC emulation(not verified)
            sign = (short) (out_ >> 8);
            if (out_ >= 0) {
                out_++;
                sign++;
            }

            this.mol = sign;
            this.mor = sign;

            if (this.chLockL != 0 && out_en != 0) {
                this.mol = out_;
            }
            //else {
            //    this.mol = sign;
            //}
            //Debug.printf("040   out:%d sign:%d\n", out_, sign);
            if (this.chLockR != 0 && out_en != 0) {
                this.mor = out_;
            }
            //else {
            //    this.mor = sign;
            //}
            // Amplify signal
            this.mol *= 3;
            this.mor *= 3;
        } else {
            this.mol = 0;
            this.mor = 0;

            out_en = (((cycles & 3) != 0) || test_dac != 0) ? 1 : 0;
            // Discrete YM3438 seems has the ladder effect too
            if (out_ >= 0 && Ym3438Const.chip_type == Ym3438Const.Type.discrete) {
                out_++;
            }
            if (this.chLockL != 0 && out_en != 0) {
                this.mol = out_;
            }
            if (this.chLockR != 0 && out_en != 0) {
                this.mor = out_;
            }
        }
    }

    private void fmGenerate() {
        int slot = (this.slot + 19) % 24;
        // Calculate phase
        short phase = (short) ((this.fmMod[slot] + (this.pgPhase[slot] >> 10)) & 0x3ff);
        //Debug.printf("040   this.fm_mod[slot]:%d this.pg_phase[slot]:%d\n", this.fm_mod[slot], this.pg_phase[slot]);
        short quarter;
        short level;
        short output;
        if ((phase & 0x100) != 0) {
            quarter = (short) ((phase ^ 0xff) & 0xff);
        } else {
            quarter = (short) (phase & 0xff);
        }
        level = (short) Ym3438Const.logSinRom[quarter];
        // Apply envelope
        level += (short) (this.egOut[slot] << 2);
        //Debug.printf("040   quarter:%d this.eg_out[slot]:%d slot:%d\n", quarter, this.eg_out[slot], slot);
        // Transform
        if (level > 0x1fff) {
            level = 0x1fff;
        }
        output = (short) (((Ym3438Const.expRom[(level & 0xff) ^ 0xff] | 0x400) << 2) >> (level >> 8));
        //Debug.printf("040   output:%d level:%d\n", output, level);
        if ((phase & 0x200) != 0) {
            output = (short) (((~output) ^ (this.modeTest21[4] << 13)) + 1);
        } else {
            output = (short) (output ^ (this.modeTest21[4] << 13));
        }
        output <<= 2;
        output >>= 2;
        this.fmOut[slot] = output;
    }

    private void doTimerA() {
        short time;
        byte load;
        load = this.timerAOverflow;
        if (this.cycles == 2) {
            // Lock load value
            load |= (byte) ((this.timerALoadLock == 0 && this.timerALoad != 0) ? 1 : 0);
            this.timerALoadLock = this.timerALoad;
            if (this.modeCsm != 0) {
                // CSM KeyOn
                this.modeKonCsm = load;
            } else {
                this.modeKonCsm = 0;
            }
        }
        // Load counter
        if (this.timerALoadLatch != 0) {
            time = this.timerAReg;
        } else {
            time = this.timerACnt;
        }
        this.timerALoadLatch = load;
        // Increase counter
        if ((this.cycles == 1 && this.timerALoadLock != 0) || this.modeTest21[2] != 0) {
            time++;
        }
        // Set overflow flag
        if (this.timerAReset != 0) {
            this.timerAReset = 0;
            this.timerAOverflowFlag = 0;
        } else {
            this.timerAOverflowFlag |= (byte) (this.timerAOverflow & this.timerAEnable);
        }
        this.timerAOverflow = (byte) (time >> 10);
        this.timerACnt = (short) (time & 0x3ff);
    }

    private void doTimerB() {
        short time;
        byte load;
        load = this.timerBOverflow;
        if (this.cycles == 2) {
            // Lock load value
            load |= (byte) ((this.timerBLoadLock == 0 && this.timerBLoad != 0) ? 1 : 0);
            this.timerBLoadLock = this.timerBLoad;
        }
        // Load counter
        if (this.timerBLoadLatch != 0) {
            time = this.timerBReg;
        } else {
            time = this.timerBCnt;
        }
        this.timerBLoadLatch = load;
        // Increase counter
        if (this.cycles == 1) {
            this.timerBSubCnt++;
        }
        if ((this.timerBSubCnt == 0x10 && this.timerBLoadLock != 0) || this.modeTest21[2] != 0) {
            time++;
        }
        this.timerBSubCnt &= 0x0f;
        // Set overflow flag
        if (this.timerBReset != 0) {
            this.timerBReset = 0;
            this.timerBOverflowFlag = 0;
        } else {
            this.timerBOverflowFlag |= (byte) (this.timerBOverflow & this.timerBEnable);
        }
        this.timerBOverflow = (byte) (time >> 8);
        this.timerBCnt = (byte) (time & 0xff);
    }

    private void keyOn() {
        // Key On
        this.egKonLatch[this.slot] = this.modeKon[this.slot];
        this.egKonCsm[this.slot] = 0;
        //Debug.printf("this.eg_kon_latch[this.slot]:%d slot:%d\n", this.eg_kon_latch[this.slot], this.slot);
        if (this.channel == 2 && this.modeKonCsm != 0) {
            // CSM Key On
            this.egKonLatch[this.slot] = 1;
            this.egKonCsm[this.slot] = 1;
        }
        if (this.cycles == this.modeKonChannel) {
            // OP1
            this.modeKon[this.channel] = this.modeKonOperator[0];
            // OP2
            this.modeKon[this.channel + 12] = this.modeKonOperator[1];
            // OP3
            this.modeKon[this.channel + 6] = this.modeKonOperator[2];
            // OP4
            this.modeKon[this.channel + 18] = this.modeKonOperator[3];
        }
    }

    public void reset(int rate, int clock) {
        int i, rateratio;
        rateratio = this.rateRatio;
        //chips = new Ym3438_();
        this.egOut = new short[24];
        this.egLevel = new short[24];
        this.egState = new byte[24];
        this.multi = new byte[24];
        this.panL = new byte[6];
        this.panR = new byte[6];

        for (i = 0; i < 24; i++) {
            this.egOut[i] = 0x3ff;
            this.egLevel[i] = 0x3ff;
            this.egState[i] = (byte) Eg.Release.ordinal();
            this.multi[i] = 1;
        }
        for (i = 0; i < 6; i++) {
            this.panL[i] = 1;
            this.panR[i] = 1;
        }
        if (rate != 0) {
            this.rateRatio = (int) (((144L * rate) << 10) / clock);// RSM_FRAC) / clock);
        } else {
            this.rateRatio = rateratio;
        }
        //System.err.printfsw = true;
        //Debug.printf("rateratio%d rate%d clock%d\n", this.rateratio,rate,clock);
        //System.err.printfsw = false;
    }

    private void clock(int[] buffer) {
        //Debug.printf("010 mol:%d mor:%d\n", this.mol, this.mor);

        this.lfoInc = this.modeTest21[1];
        this.pgRead >>= 1;
        this.egRead[1] >>= 1;
        this.egCycle++;
        // Lock envelope generator timer value
        if (this.cycles == 1 && this.egQuotient == 2) {
            if (this.egCycleStop != 0) {
                this.egShiftLock = 0;
            } else {
                this.egShiftLock = (byte) (this.egShift + 1);
            }
            this.egTimerLowLock = (byte) (this.egTimer & 0x03);
        }
        // Cycle specific functions
        switch (this.cycles) {
        case 0:
            this.lfoPm = (byte) (this.lfoCnt >> 2);
            if ((this.lfoCnt & 0x40) != 0) {
                this.lfoAm = (byte) (this.lfoCnt & 0x3f);
            } else {
                this.lfoAm = (byte) (this.lfoCnt ^ 0x3f);
            }
            this.lfoAm <<= 1;
            break;
        case 1:
            this.egQuotient++;
            this.egQuotient %= 3;
            this.egCycle = 0;
            this.egCycleStop = 1;
            this.egShift = 0;
            this.egTimerInc |= (byte) (this.egQuotient >> 1);
            this.egTimer = (short) (this.egTimer + this.egTimerInc);
            this.egTimerInc = (byte) (this.egTimer >> 12);
            this.egTimer &= 0xfff;
            break;
        case 2:
            this.pgRead = this.pgPhase[21] & 0x3ff;
            this.egRead[1] = this.egOut[0];
            break;
        case 13:
            this.egCycle = 0;
            this.egCycleStop = 1;
            this.egShift = 0;
            this.egTimer = (short) (this.egTimer + this.egTimerInc);
            this.egTimerInc = (byte) (this.egTimer >> 12);
            this.egTimer &= 0xfff;
            break;
        case 23:
            this.lfoInc |= 1;
            break;
        }


        this.egTimer &= (short) (~(this.modeTest21[5] << this.egCycle));
        if ((((this.egTimer >> this.egCycle) | (this.pinTestIn & this.egCustomTimer)) & this.egCycleStop) != 0) {
            this.egShift = this.egCycle;
            this.egCycleStop = 0;
        }

        //Debug.printf("020 mol:%d mor:%d\n", this.mol, this.mor);

        doIO();

        //Debug.printf("030 mol:%d mor:%d\n", this.mol, this.mor);

        doTimerA();
        doTimerB();
        keyOn();

        //Debug.printf("040 mol:%d mor:%d\n", this.mol, this.mor);

        chOutput();
        //Debug.printf("045 mol:%d mor:%d\n", this.mol, this.mor);
        chGenerate();

        //Debug.printf("050 mol:%d mor:%d\n", this.mol, this.mor);

        fmPrepare();
        fmGenerate();

        //Debug.printf("060 mol:%d mor:%d\n", this.mol, this.mor);

        phaseGenerate();
        phaseCalcIncrement();

        //Debug.printf("070 mol:%d mor:%d\n", this.mol, this.mor);

        envelopeADSR();
        envelopeGenerate();
        envelopeSSGEG();
        envelopePrepare();

        //Debug.printf("080 mol:%d mor:%d\n", this.mol, this.mor);

        // Prepare fnum & block
        if (this.modeCh3 != 0) {
            // Channel 3 special mode
            switch (this.slot) {
            case 1: // OP1
                this.pgFnum = this.fnum3Ch[1];
                this.pgBlock = this.block3Ch[1];
                this.pgKcode = this.kcode3Ch[1];
                break;
            case 7: // OP3
                this.pgFnum = this.fnum3Ch[0];
                this.pgBlock = this.block3Ch[0];
                this.pgKcode = this.kcode3Ch[0];
                break;
            case 13: // OP2
                this.pgFnum = this.fnum3Ch[2];
                this.pgBlock = this.block3Ch[2];
                this.pgKcode = this.kcode3Ch[2];
                break;
            case 19: // OP4
            default:
                this.pgFnum = this.fnum[(this.channel + 1) % 6];
                this.pgBlock = this.block[(this.channel + 1) % 6];
                this.pgKcode = this.kcode[(this.channel + 1) % 6];
                break;
            }
        } else {
            this.pgFnum = this.fnum[(this.channel + 1) % 6];
            this.pgBlock = this.block[(this.channel + 1) % 6];
            this.pgKcode = this.kcode[(this.channel + 1) % 6];
        }

        //Debug.printf("090 mol:%d mor:%d\n", this.mol, this.mor);

        updateLFO();
        doRegWrite();
        this.cycles = (this.cycles + 1) % 24;
        this.slot = this.cycles;
        this.channel = this.cycles % 6;

        //Debug.printf("100 mol:%d mor:%d\n", this.mol, this.mor);

        buffer[0] = this.mol;
        buffer[1] = this.mor;

        //Debug.printf("110 mol:%d mor:%d\n", this.mol, this.mor);
    }

    private void writeInternal(int port, byte data) {
        //if (port == 1 && data == 0xf1)
        //{
        //    System.err.printfOn();
        //}
        //Debug.printf("port:%x data:%x\n", port, data);

        port &= 3;
        this.writeData = (short) (((port << 7) & 0x100) | data);
        if ((port & 1) != 0) {
            // Data
            this.writeD |= 1;
        } else {
            // Address
            this.writeA |= 1;
        }
    }

    private void setTestPin(int value) {
        this.pinTestIn = (byte) (value & 1);
    }

    private int readTestPin() {
        if (this.modeTest2C[7] == 0) {
            return 0;
        }
        return this.cycles == 23 ? 1 : 0;
    }

    private int readIRQPin() {
        return this.timerAOverflowFlag | this.timerBOverflowFlag;
    }

    private byte read(int port) {
        if ((port & 3) == 0 || Ym3438Const.chip_type == Ym3438Const.Type.asic) {
            if (this.modeTest21[6] != 0) {
                // Read test data
                //int slot = (this.cycles + 18) % 24;
                short testdata = (short) (((this.pgRead & 0x01) << 15)
                        | (((this.egRead[this.modeTest21[0]]) & 0x01) << 14));
                if (this.modeTest2C[4] != 0) {
                    testdata |= (short) (this.chRead & 0x1ff);
                } else {
                    testdata |= (short) (this.fmOut[(this.slot + 18) % 24] & 0x3fff);
                }
                if (this.modeTest21[7] != 0) {
                    return (byte) (testdata & 0xff);
                } else {
                    return (byte) (testdata >> 8);
                }
            } else {
                return (byte) ((this.busy << 7) | (this.timerBOverflowFlag << 1)
                        | this.timerAOverflowFlag);
            }
        }
        return 0;
    }

    private int[] dmyBuffer = new int[2];
    private int[] grBuffer = new int[2];
    private int[] buf = new int[2];

    public void setChipType(Ym3438Const.Type type) {
        switch (type) {
        case asic:
            Ym3438Const.use_filter = 0;
            break;
        case discrete:
            Ym3438Const.use_filter = 0;
            break;
        case ym2612:
            Ym3438Const.use_filter = 1;
            break;
        case ym2612_u:
            type = Ym3438Const.Type.ym2612;
            Ym3438Const.use_filter = 0;
            break;
        case asic_lp:
            type = Ym3438Const.Type.asic;
            Ym3438Const.use_filter = 1;
            break;
        }

        Ym3438Const.chip_type = type;
    }

    public void write(int port, byte data) {
        long time1, time2;
        long skip;

        if ((this.writeBuf[this.writeBufLast].port & 0x04) != 0) {
            this.writeInternal(this.writeBuf[this.writeBufLast].port & 0X03,
                    this.writeBuf[this.writeBufLast].data);

            this.writeBufCur = (this.writeBufLast + 1) % 2048; // OPN_WRITEBUF_SIZE;
            skip = this.writeBuf[this.writeBufLast].time - this.writeBufSampleCnt;
            this.writeBufSampleCnt = this.writeBuf[this.writeBufLast].time;
            while (skip-- != 0) {
                this.clock(dmyBuffer);
            }
        }

        this.writeBuf[this.writeBufLast].port = (byte) ((port & 0x03) | 0x04);
        this.writeBuf[this.writeBufLast].data = data;
        time1 = this.writeBufLastTime + 15; // OPN_WRITEBUF_DELAY;
        time2 = this.writeBufSampleCnt;

        if (time1 < time2) {
            time1 = time2;
        }

        this.writeBuf[this.writeBufLast].time = time1;
        this.writeBufLastTime = time1;
        this.writeBufLast = (this.writeBufLast + 1) % 2048;// OPN_WRITEBUF_SIZE;
    }

    public void update(int[] buf) {
        int i;
        int mute;

        while (this.sampleCnt >= this.rateRatio) {
            this.oldSamples[0] = this.samples[0];
            this.oldSamples[1] = this.samples[1];
            this.samples[0] = this.samples[1] = 0;
            for (i = 0; i < 24; i++) {
                switch (this.cycles >> 2) {
                case 0: // Ch 2
                    mute = this.mute[1];
                    break;
                case 1: // Ch 6, DAC
                    mute = this.mute[5 + this.dacEn];
                    break;
                case 2: // Ch 4
                    mute = this.mute[3];
                    break;
                case 3: // Ch 1
                    mute = this.mute[0];
                    break;
                case 4: // Ch 5
                    mute = this.mute[4];
                    break;
                case 5: // Ch 3
                    mute = this.mute[2];
                    break;
                default:
                    mute = 0;
                    break;
                }
                this.clock(grBuffer);
                //Debug.printf("l%d r%d\n", buffer[0], buffer[1]);
                if (mute == 0) {
                    this.samples[0] += grBuffer[0];
                    this.samples[1] += grBuffer[1];
                }

                while (this.writeBuf[this.writeBufCur].time <= this.writeBufSampleCnt) {
                    if ((this.writeBuf[this.writeBufCur].port & 0x04) == 0) {
                        break;
                    }
                    this.writeBuf[this.writeBufCur].port &= 0x03;
                    this.writeInternal(this.writeBuf[this.writeBufCur].port,
                            this.writeBuf[this.writeBufCur].data);
                    this.writeBufCur = (this.writeBufCur + 1) % 2048;// OPN_WRITEBUF_SIZE;
                }
                this.writeBufSampleCnt++;
            }
            if (Ym3438Const.use_filter == 0) {
                this.samples[0] *= 11;// OUTPUT_FACTOR;
                this.samples[1] *= 11;// OUTPUT_FACTOR;
            } else {
                //this.samples[0] = this.oldsamples[0] + FILTER_CUTOFF_I * (this.samples[0] * OUTPUT_FACTOR_F - this.oldsamples[0]);
                //this.samples[1] = this.oldsamples[1] + FILTER_CUTOFF_I * (this.samples[1] * OUTPUT_FACTOR_F - this.oldsamples[1]);
                this.samples[0] = (int) (this.oldSamples[0] + (1 - 0.512331301282628) * (this.samples[0] * 12 - this.oldSamples[0]));
                this.samples[1] = (int) (this.oldSamples[1] + (1 - 0.512331301282628) * (this.samples[1] * 12 - this.oldSamples[1]));
            }
            this.sampleCnt -= this.rateRatio;
            //Debug.printf("samplecnt%d\n", this.samplecnt);
        }
        buf[0] = (this.oldSamples[0] * (this.rateRatio - this.sampleCnt)
                + this.samples[0] * this.sampleCnt) / this.rateRatio;
        buf[1] = (this.oldSamples[1] * (this.rateRatio - this.sampleCnt)
                + this.samples[1] * this.sampleCnt) / this.rateRatio;
        //Debug.printf("bl%d br%d this.oldsamples[0]%d this.samples[0]%d\n", buf[0], buf[1], this.oldsamples[0], this.samples[0]);
        this.sampleCnt += 1 << 10;// RSM_FRAC;
    }

    public void setOptions(byte flags) {
        switch ((flags >> 3) & 0x03) {
        case 0x00: // Ym2612
        default:
            setChipType(Ym3438Const.Type.ym2612);
            break;
        case 0x01: // ASIC YM3438
            setChipType(Ym3438Const.Type.asic);
            break;
        case 0x02: // Discrete YM3438
            setChipType(Ym3438Const.Type.discrete);
            break;
        case 0x03: // Ym2612 without filter emulation
            setChipType(Ym3438Const.Type.ym2612_u);
            break;
        }
    }

    public void setMuteMask(int mute) {
        for (int i = 0; i < 7; i++) {
            this.mute[i] = (mute >> i) & 0x01;
        }
    }

    public void setMute(int ch, boolean mute) {
        this.mute[ch & 0x7] = mute ? 1 : 0;
    }
}
