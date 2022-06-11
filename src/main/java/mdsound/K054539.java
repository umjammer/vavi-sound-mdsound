package mdsound;

import java.util.Arrays;


/*
 Konami 054539 (TOP) PCM Sound Chip

 A lot of information comes from Amuse.
 Big thanks to them.

   Registers:
   00..ff: 20 bytes/channel, 8 channels
     00..02: pitch (lsb, mid, msb)
         03: volume (0=max, 0x40=-36dB)
         04: Reverb volume (idem)
     05: pan (1-f right, 10 middle, 11-1f left)
     06..07: Reverb delay (0=max, current computation non-trusted)
     08..0a: loop (lsb, mid, msb)
     0c..0e: start (lsb, mid, msb) (and current position ?)

   100.1ff: effects?
     13f: pan of the analog input (1-1f)

   200..20f: 2 bytes/channel, 8 channels
     00: type (b2-3), reverse (b5)
     01: loop (b0)

   214: Key on (b0-7 = channel 0-7)
   215: Key off          ""
   225: ?
   227: Timer frequency
   228: ?
   229: ?
   22a: ?
   22b: ?
   22c: Channel active? (b0-7 = channel 0-7)
   22d: Data read/write port
   22e: ROM/RAM select (00..7f == ROM banks, 80 = Reverb RAM)
   22f: Global control:
        .......x - Enable PCM
        ......x. - Timer related?
        ...x.... - Enable ROM/RAM readback from 0x22d
        ..x..... - Timer output enable?
        x....... - Disable register RAM updates

    The chip has an optional 0x8000 byte Reverb buffer.
    The Reverb delay is actually an offset in this buffer.
*/
public class K054539 extends Instrument.BaseInstrument {

    public K054539() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
        //0..Main
    }

    @Override
    public void reset(byte chipID) {
        device_reset_k054539(chipID);
    }

    @Override
    public int start(byte chipID, int clock) {
        return (int) device_start_k054539(chipID, (int) clock);
    }

    @Override
    public void stop(byte chipID) {
        device_stop_k054539(chipID);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        k054539_update(chipID, outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    @Override
    public int start(byte chipID, int SamplingRate, int clockValue, Object... Option) {
        int sampRate = (int) device_start_k054539(chipID, (int) clockValue);
        int flags = 1;
        if (Option != null && Option.length > 0) flags = (int) (byte) Option[0];
        k054539_init_flags(chipID, flags);

        return sampRate;
    }

    private static class K054539State {

        private static final int RESET_FLAGS = 0;
        private static final int REVERSE_STEREO = 1;
        private static final int DISABLE_REVERB = 2;
        private static final int UPDATE_AT_KEYON = 4;

        private static class Channel {
            public int pos;
            public int pfrac;
            public int val;
            public int pval;
        }

        public double[] voltab = new double[256];
        public double[] pantab = new double[0xf];

        public double[] gain = new double[8];
        public byte[][] posreg_latch = new byte[][] {
                new byte[3], new byte[3], new byte[3], new byte[3],
                new byte[3], new byte[3], new byte[3], new byte[3]};
        public int flags;

        public byte[] regs = new byte[0x230];
        public byte[] ram;
        public int ptrRam = 0;
        public int reverbPos;

        public int curPtr;
        public int curLimit;
        public byte[] curZone;
        public int ptrCurZone;
        public byte[] rom;
        public int ptrRom = 0;

        public int rom_size;
        public int rom_mask;

        public Channel[] channels = new Channel[] {
                new Channel(),
                new Channel(),
                new Channel(),
                new Channel(),
                new Channel(),
                new Channel(),
                new Channel(),
                new Channel()
        };

        public byte[] muted = new byte[8];

        public int clock;

        private int updateReg() {
            return this.regs[0x22f] & 0x80;
        }

        private void keyOn(int channel) {
            if (updateReg() == 0)
                this.regs[0x22c] |= (byte) (1 << channel);
        }

        private void keyOff(int channel) {
            if (updateReg() == 0)
                this.regs[0x22c] &= (byte) (~(1 << channel));
        }

        private int init(int clock) {

            if (clock < 1000000)    // if < 1 MHz, then it's the sample rate, not the clock
                clock *= 384;   // (for backwards compatibility with old VGM logs)
            this.clock = clock;
            // most of these are done in device_reset
            // memset(this.regs, 0, sizeof(this.regs));
            // memset(this.k054539_posreg_latch, 0, sizeof(this.k054539_posreg_latch)); //*
            this.flags |= UPDATE_AT_KEYON; //* make it default until proven otherwise

            this.ram = new byte[0x4000];
            // this.reverb_pos = 0;
            // this.cur_ptr = 0;
            // memset(this.ram, 0, 0x4000);

            /*final memory_region *region = (this.intf.rgn@Override != NULL) ? device.machine().region(this.intf.rgnoverride) : device.region();
            this.rom = *region;
            this.rom_size = region.bytes();
            this.rom_mask = 0xffffffffU;
            for(i=0; i<32; i++)
                if((1U<<i) >= this.rom_size) {
                    this.rom_mask = (1U<<i) - 1;
                    break;
                }*/
            this.rom = null;
            this.rom_size = 0;
            this.rom_mask = 0x00;

            //if(this.intf.irq)
            // One or more of the registers must be the timer period
            // And anyway, this particular frequency is probably wrong
            // 480 hz is TRUSTED by gokuparo disco stage - the looping sample doesn't line up otherwise
            // device.machine().scheduler().timer_pulse(attotime::from_hz(480), FUNC(k054539_irq), 0, info);

            //this.stream = device.machine().Sound().stream_alloc(*device, 0, 2, device.clock() / 384, info, k054539_update);

            //device.save_item(NAME(this.regs));
            //device.save_pointer(NAME(this.ram), 0x4000);
            //device.save_item(NAME(this.cur_ptr));

            return this.clock / 384;
        }

        private void resetZones() {
            int data = this.regs[0x22e];
            this.curZone = (data & 0xff) == 0x80 ? this.ram : this.rom;
            this.ptrCurZone = (data & 0xff) == 0x80 ? 0 : (0x20000 * data);
            this.curLimit = (data & 0xff) == 0x80 ? 0x4000 : 0x20000;
        }

        private void write(int offset, byte data) {

            boolean latch;
            int offs, ch, pan;
            byte[] regbase;
            int regptr;

            regbase = this.regs;
            latch = (this.flags & K054539State.UPDATE_AT_KEYON) != 0 && (regbase[0x22f] & 1) != 0;
            //System.err.printf("latch = {0} \n", latch);

            if (latch && offset < 0x100) {
                offs = (offset & 0x1f) - 0xc;
                ch = offset >> 5;

                if (offs >= 0 && offs <= 2) {
                    // latch writes to the position index registers
                    this.posreg_latch[ch][offs] = data;
                    //System.err.printf("this.k054539_posreg_latch[{0}][{1}] = {2} \n", ch, offs, data);
                    return;
                }
            } else
                switch (offset) {
                case 0x13f:
                    pan = (data >= 0x11 && data <= 0x1f) ? data - 0x11 : 0x18 - 0x11;
                    //if(this.intf.apan)
                    // this.intf.apan(this.device, this.pantab[pan], this.pantab[0xe - pan]);
                    break;

                case 0x214:
                    if (latch) {
                        for (ch = 0; ch < 8; ch++) {
                            if ((data & (1 << ch)) != 0) {
                                regptr = (ch << 5) + 0xc;

                                // update the chip at key-on
                                regbase[regptr + 0] = this.posreg_latch[ch][0];
                                regbase[regptr + 1] = this.posreg_latch[ch][1];
                                regbase[regptr + 2] = this.posreg_latch[ch][2];

                                this.keyOn(ch);
                            }
                        }
                    } else {
                        for (ch = 0; ch < 8; ch++)
                            if ((data & (1 << ch)) != 0)
                                this.keyOn(ch);
                    }
                    break;

                case 0x215:
                    for (ch = 0; ch < 8; ch++)
                        if ((data & (1 << ch)) != 0)
                            this.keyOff(ch);
                    break;

                        /*case 0x227:
                        {
                            attotime period = attotime::from_hz((float)(38 + data) * (clock()/384.0f/14400.0f)) / 2.0f;

                            m_timer.adjust(period, 0, period);

                            m_timer_state = 0;
                            m_timer_handler(m_timer_state);
                        }*/
                //break;

                case 0x22d:
                    if ((regbase[0x22e] & 0xff) == 0x80)
                        this.curZone[this.ptrCurZone + this.curPtr] = data;
                    this.curPtr++;
                    if (this.curPtr == this.curLimit)
                        this.curPtr = 0;
                    break;

                case 0x22e:
                    this.curZone = (data & 0xff) == 0x80 ? this.ram : this.rom;
                    this.ptrCurZone = (data & 0xff) == 0x80 ? 0 : (0x20000 * data);
                    this.curLimit = (data & 0xff) == 0x80 ? 0x4000 : 0x20000;
                    this.curPtr = 0;
                    break;

                        /*case 0x22f:
                            if (!(data & 0x20)) // Disable timer output?
                            {
                                m_timer_state = 0;
                                m_timer_handler(m_timer_state);
                            }
                        break;*/

                default:
                    //#if 0
                    //   if(regbase[offset] != data) {
                    //    if((offset & 0xff00) == 0) {
                    //     chanoff = offset & 0x1f;
                    //     if(chanoff < 4 || chanoff == 5 ||
                    //        (chanoff >=8 && chanoff <= 0xa) ||
                    //        (chanoff >= 0xc && chanoff <= 0xe))
                    //      break;
                    //    }
                    //    if(1 || ((offset >= 0x200) && (offset <= 0x210)))
                    //     break;
                    //    logerror("K054539 %03x = %02x\n", offset, data);
                    //   }
                    //#endif
                    break;
                }

            regbase[offset] = data;
        }

        public byte write(int offset) {
            switch (offset) {
            case 0x22d:
                if ((this.regs[0x22f] & 0x10) != 0) {
                    byte res = this.curZone[this.ptrCurZone + this.curPtr];
                    this.curPtr++;
                    if (this.curPtr == this.curLimit)
                        this.curPtr = 0;
                    return res;
                } else
                    return 0;
            case 0x22c:
                break;
            default:
                //LOG(("K054539 read %03x\n", offset));
                break;
            }
            return this.regs[offset];
        }

        private int device_start(int clock) {

            for (int i = 0; i < 8; i++)
                this.gain[i] = 1.0;
            this.flags = K054539State.RESET_FLAGS;

            //this.intf = (device.static_config() != NULL) ? (final k054539_interface *)device.static_config() : &defintrf;

                /*
                    I've tried various equations on volume control but none worked consistently.
                    The upper four channels in most MW/GX games simply need a significant boost
                    to Sound right. For example, the bass and smash Sound volumes in Violent Storm
                    have roughly the same values and the voices in Tokimeki Puzzledama are given
                    values smaller than those of the hihats. Needless to say the two K054539 chips
                    in Mystic Warriors are completely out of balance. Rather than forcing a
                    "one size fits all" function to the voltab the current invert exponential
                    appraoch seems most appropriate.
                */
            // Factor the 1/4 for the number of channels in the volume (1/8 is too harsh, 1/2 gives clipping)
            // vol=0 . no attenuation, vol=0x40 . -36dB
            for (int i = 0; i < 256; i++)
                this.voltab[i] = Math.pow(10.0, (-36.0 * (double) i / (double) 0x40) / 20.0) / 4.0;

            // Pan table for the left channel
            // Right channel is identical with inverted index
            // Formula is such that pan[i]**2+pan[0xe-i]**2 = 1 (constant output power)
            // and pan[0xe] = 1 (full panning)
            for (int i = 0; i < 0xf; i++)
                this.pantab[i] = Math.sqrt((double) i) / Math.sqrt((double) 0xe);

            //k054539_init_chip(device, info);

            //device.machine().save().register_postload(save_prepost_delegate(FUNC(reset_zones), info));

            for (int i = 0; i < 8; i++)
                this.muted[i] = 0x00;

            return this.init(clock);
        }

        private void stop() {
            this.rom = null;
            this.ram = null;
        }

        private void reset() {
            Arrays.fill(this.regs, (byte) 0);
            for (byte[] k054539PosregLatch : this.posreg_latch) {
                Arrays.fill(k054539PosregLatch, (byte) 0);
            }
            //this.k054539_flags |= K054539_UPDATE_AT_KEYON;

            this.reverbPos = 0;
            this.curPtr = 0;
            for (int i = 0; i < 0x4000; i++) {
                this.ram[i] = 0;
            }
        }

        private void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
            if (this.rom_size != romSize) {
                byte i;

                this.rom = new byte[romSize];
                this.rom_size = romSize;
                for (i = 0; i < romSize; i++) {
                    this.rom[i] = (byte) 0xff;
                }

                this.rom_mask = 0xFFFFFFFF;
                for (i = 0; i < 32; i++) {
                    if ((1 << i) >= this.rom_size) {
                        this.rom_mask = (1 << i) - 1;
                        break;
                    }
                }
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            if (dataLength >= 0) System.arraycopy(romData, 0, this.rom, dataStart, dataLength);
        }

        public void writeRom2(int romSize, int dataStart, int dataLength, byte[] romData, int startAdr) {
            if (this.rom_size != romSize) {
                byte i;

                this.rom = new byte[romSize];
                this.rom_size = romSize;
                for (int ind = 0; ind < romSize; ind++) {
                    this.rom[ind] = (byte) 0xff;
                }

                this.rom_mask = 0xFFFFFFFF;
                for (i = 0; i < 32; i++) {
                    if ((1 << i) >= this.rom_size) {
                        this.rom_mask = (1 << i) - 1;
                        break;
                    }
                }
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            if (dataLength >= 0) System.arraycopy(romData, startAdr, this.rom, dataStart, dataLength);
        }

        private void update(int[][] outputs, int samples) {
            final double VOL_CAP = 1.80;

            final short[] dpcm = new short[] {
                    0 << 8, 1 << 8, 4 << 8, 9 << 8, 16 << 8, 25 << 8, 36 << 8, 49 << 8,
                    -64 << 8, -49 << 8, -36 << 8, -25 << 8, -16 << 8, -9 << 8, -4 << 8, -1 << 8
            };

            byte[] rbase = this.ram;
            byte[] rom;
            int rom_mask;
            int i, ch;
            double lval, rval;
            byte[] base1, base2;
            int ptrBase1, ptrBase2;
            Channel[] chan;
            int ptrChan;
            int delta, vol, bval, pan;
            double cur_gain, lvol, rvol, rbvol;
            int rdelta;
            int cur_pos;
            int fdelta, pdelta;
            int cur_pfrac, cur_val, cur_pval;

            for (i = 0; i < samples; i++) {
                outputs[0][i] = 0;
                outputs[1][i] = 0;
            }

            if ((this.regs[0x22f] & 1) == 0) // Enable PCM
                return;

            rom = this.rom;
            rom_mask = this.rom_mask;

            for (i = 0; i != samples; i++) {
                // リバーブ
                if ((this.flags & K054539State.DISABLE_REVERB) == 0) {
                    //lval = rval = rbase[this.reverb_pos];
                    short val = (short) (rbase[this.reverbPos * 2] + rbase[this.reverbPos * 2 + 1] * 0x100);
                    lval = rval = val;
                } else
                    lval = rval = 0;
                //rbase[this.reverb_pos] = 0;
                //System.err.printf("rbase[this.reverb_pos(%d)] = %d \n", this.reverb_pos, lval);
                rbase[this.reverbPos * 2] = 0;
                rbase[this.reverbPos * 2 + 1] = 0;


                for (ch = 0; ch < 8; ch++)
                    if (((this.regs[0x22c] & (1 << ch)) != 0) && this.muted[ch] == 0) { // 0x22c ChannelActive
                        base1 = this.regs;
                        ptrBase1 = 0x20 * ch;
                        base2 = this.regs;
                        ptrBase2 = 0x200 + 0x2 * ch;
                        chan = this.channels;
                        ptrChan = ch;

                        // pitch
                        delta = base1[ptrBase1 + 0x00] | (base1[ptrBase1 + 0x01] << 8) | (base1[ptrBase1 + 0x02] << 16);

                        vol = base1[ptrBase1 + 0x03];

                        // 0x04 Reverb vol
                        bval = vol + base1[ptrBase1 + 0x04];
                        if (bval > 255)
                            bval = 255;

                        pan = base1[ptrBase1 + 0x05];
                        // DJ Main: 81-87 right, 88 middle, 89-8f left
                        if (pan >= 0x81 && pan <= 0x8f)
                            pan -= 0x81;
                        else if (pan >= 0x11 && pan <= 0x1f)
                            pan -= 0x11;
                        else
                            pan = 0x18 - 0x11;

                        cur_gain = this.gain[ch];

                        lvol = this.voltab[vol] * this.pantab[pan] * cur_gain;
                        if (lvol > VOL_CAP)
                            lvol = VOL_CAP;

                        rvol = this.voltab[vol] * this.pantab[0xe - pan] * cur_gain;
                        if (rvol > VOL_CAP)
                            rvol = VOL_CAP;

                        rbvol = this.voltab[bval] * cur_gain / 2;
                        if (rbvol > VOL_CAP)
                            rbvol = VOL_CAP;

                        //System.err.printf("ch=%d lvol=%d rvol=%d\n", ch, lvol, rvol);

                        rdelta = (base1[ptrBase1 + 6] | (base1[ptrBase1 + 7] << 8)) >> 3;
                        rdelta = (rdelta + this.reverbPos) & 0x3fff;

                        cur_pos = (int) ((base1[ptrBase1 + 0x0c] | (base1[ptrBase1 + 0x0d] << 8) | (base1[ptrBase1 + 0x0e] << 16)) & rom_mask);

                        if ((base2[ptrBase2 + 0] & 0x20) != 0) {
                            delta = -delta;
                            fdelta = +0x10000;
                            pdelta = -1;
                        } else {
                            fdelta = -0x10000;
                            pdelta = +1;
                        }

                        if (cur_pos != chan[ptrChan].pos) {
                            chan[ptrChan].pos = cur_pos;
                            cur_pfrac = 0;
                            cur_val = 0;
                            cur_pval = 0;
                        } else {
                            cur_pfrac = (int) chan[ptrChan].pfrac;
                            cur_val = chan[ptrChan].val;
                            cur_pval = chan[ptrChan].pval;
                        }

                        switch (base2[ptrBase2 + 0] & 0xc) {
                        case 0x0: { // 8bit pcm
                            cur_pfrac += delta;
                            while ((cur_pfrac & ~0xffff) != 0) {
                                cur_pfrac += fdelta;
                                cur_pos += pdelta;

                                cur_pval = cur_val;
                                cur_val = (short) (rom[cur_pos] << 8);
                                //if(cur_val == (INT16)0x8000 && (base2[1] & 1))
                                if ((rom[cur_pos] & 0xff) == 0x80 && (base2[ptrBase2 + 1] & 1) != 0) {
                                    cur_pos = (base1[ptrBase1 + 0x08] | (base1[ptrBase1 + 0x09] << 8) | (base1[ptrBase1 + 0x0a] << 16)) & rom_mask;
                                    cur_val = (short) (rom[cur_pos] << 8);
                                }
                                //if(cur_val == (INT16)0x8000)
                                if ((rom[cur_pos] & 0xff) == 0x80) {
                                    this.keyOff(ch);
                                    cur_val = 0;
                                    break;
                                }
                            }
                            //System.err.printf("ch=%d cur_pos=%d cur_val=%d\n", ch, cur_pos, cur_val);
                            //if(ch!=6) cur_val = 0;
                            break;
                        }

                        case 0x4: { // 16bit pcm lsb first
                            pdelta <<= 1;

                            cur_pfrac += delta;
                            while ((cur_pfrac & ~0xffff) != 0) {
                                cur_pfrac += fdelta;
                                cur_pos += pdelta;

                                cur_pval = cur_val;
                                cur_val = (short) (rom[cur_pos] | rom[cur_pos + 1] << 8);
                                if (cur_val == (short) (0x8000 - 0x10000) && (base2[ptrBase2 + 1] & 1) != 0) {
                                    cur_pos = (int) ((base1[ptrBase1 + 0x08] | (base1[ptrBase1 + 0x09] << 8) | (base1[ptrBase1 + 0x0a] << 16)) & rom_mask);
                                    cur_val = (short) (rom[cur_pos] | rom[cur_pos + 1] << 8);
                                }
                                if (cur_val == (short) (0x8000 - 0x10000)) {
                                    this.keyOff(ch);
                                    cur_val = 0;
                                    break;
                                }
                            }
                            //cur_val = 0;
                            break;
                        }

                        case 0x8: { // 4bit dpcm
                            cur_pos <<= 1;
                            cur_pfrac <<= 1;
                            if ((cur_pfrac & 0x10000) != 0) {
                                cur_pfrac &= 0xffff;
                                cur_pos |= 1;
                            }

                            cur_pfrac += delta;
                            while ((cur_pfrac & ~0xffff) != 0) {
                                cur_pfrac += fdelta;
                                cur_pos += pdelta;

                                cur_pval = cur_val;
                                cur_val = rom[cur_pos >> 1];
                                if ((cur_val & 0xff) == 0x88 && (base2[ptrBase2 + 1] & 1) != 0) {
                                    cur_pos = ((base1[ptrBase1 + 0x08] | (base1[ptrBase1 + 0x09] << 8) | (base1[ptrBase1 + 0x0a] << 16)) & rom_mask) << 1;
                                    cur_val = rom[cur_pos >> 1];
                                }
                                if ((cur_val & 0xff) == 0x88) {
                                    this.keyOff(ch);
                                    cur_val = 0;
                                    break;
                                }
                                if ((cur_pos & 1) != 0)
                                    cur_val >>= 4;
                                else
                                    cur_val &= 15;
                                cur_val = cur_pval + dpcm[cur_val];
                                if (cur_val < -32768)
                                    cur_val = -32768;
                                else if (cur_val > 32767)
                                    cur_val = 32767;
                            }

                            cur_pfrac >>= 1;
                            if ((cur_pos & 1) != 0)
                                cur_pfrac |= 0x8000;
                            cur_pos >>= 1;
                            //cur_val = 0;
                            break;
                        }
                        default:
                            //System.err.prtinf(("Unknown sample type %x for channel %d\n", base2[0] & 0xc, ch));
                            break;
                        }
                        lval += cur_val * lvol;
                        rval += cur_val * rvol;
                        //if (ch == 6) {
                        //    System.err.printf("ch={0} lval={1}\n", ch, lval);
                        //}
                        int ptr = (rdelta + this.reverbPos) & 0x1fff;
                        short valu = (short) (rbase[ptr * 2] + rbase[ptr * 2 + 1] * 0x100);
                        valu += (short) (cur_val * rbvol);
                        rbase[ptr * 2] = (byte) (valu & 0xff);
                        rbase[ptr * 2 + 1] = (byte) ((valu & 0xff00) >> 8);

                        chan[ptrChan].pos = cur_pos;
                        chan[ptrChan].pfrac = (int) cur_pfrac;
                        chan[ptrChan].pval = cur_pval;
                        chan[ptrChan].val = cur_val;

                        if (this.updateReg() == 0) {
                            base1[ptrBase1 + 0x0c] = (byte) (cur_pos & 0xff);
                            base1[ptrBase1 + 0x0d] = (byte) ((cur_pos >> 8) & 0xff);
                            base1[ptrBase1 + 0x0e] = (byte) ((cur_pos >> 16) & 0xff);
                        }
                    }
                this.reverbPos = (this.reverbPos + 1) & 0x1fff;
                outputs[0][i] = (int) lval;
                outputs[1][i] = (int) rval;
                outputs[0][i] <<= 1;
                outputs[1][i] <<= 1;
                //System.err.printf( "outputs[0][i] = %d\n", outputs[0][i]);
            }
        }

        public void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < 8; curChn++)
                this.muted[curChn] = (byte) ((muteMask >> curChn) & 0x01);
        }
    }

    private static final int MAX_CHIPS = 0x02;
    private static K054539State[] k054539Data = new K054539State[] {new K054539State(), new K054539State()};

    @Override
    public String getName() {
        return "K054539";
    }

    @Override
    public String getShortName() {
        return "K054";
    }

    public void k054539_init_flags(byte chipID, int flags) {
        K054539State info = k054539Data[chipID];
        info.flags = flags;
    }

    public void k054539_set_gain(byte chipID, int channel, double gain) {
        K054539State info = k054539Data[chipID];
        if (gain >= 0) info.gain[channel] = gain;
    }

    private void k054539_update(byte chipID, int[][] outputs, int samples) {
        K054539State info = k054539Data[chipID];
        info.update(outputs, samples);
    }

    private void k054539_w(byte chipID, int offset, byte data) {
        K054539State info = k054539Data[chipID];
        info.write(offset, data);
    }

    public byte k054539_r(byte chipID, int offset) {
        K054539State info = k054539Data[chipID];
        return info.write(offset);
    }

    private int device_start_k054539(byte chipID, int clock) {
        if (chipID >= MAX_CHIPS)
            return 0;

        K054539State info = k054539Data[chipID];
        return info.device_start(clock);
    }

    private void device_stop_k054539(byte chipID) {
        K054539State info = k054539Data[chipID];
        info.stop();
    }

    private void device_reset_k054539(byte chipID) {
        K054539State info = k054539Data[chipID];
        info.reset();
    }

    private void k054539_write_rom(byte chipID, int romSize, int dataStart, int dataLength,
                                   byte[] romData) {
        K054539State info = k054539Data[chipID];
        info.writeRom(romSize, dataStart, dataLength, romData);
    }

    void k054539_write_rom2(byte chipID, int romSize, int dataStart, int dataLength,
                            byte[] romData, int startAdr) {
        K054539State info = k054539Data[chipID];
        info.writeRom2(romSize, dataStart, dataLength, romData, startAdr);
    }

    public void k054539_set_mute_mask(byte chipID, int muteMask) {
        K054539State info = k054539Data[chipID];
        info.setMuteMask(muteMask);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        k054539_w(chipID, adr, (byte) data);
        return 0;
    }

    /**
     * Generic get_info
     */
        /*DEVICE_GET_INFO( k054539 )
        {
            switch (state)
            {
                // --- the following bits of info are returned as 64-bit signed integers --- //
                case DEVINFO_INT_TOKEN_BYTES:     info.i = sizeof(K054539State);    break;

                // --- the following bits of info are returned as pointers to data or functions --- //
                case DEVINFO_FCT_START:       info.start = DEVICE_START_NAME( k054539 );  break;
                case DEVINFO_FCT_STOP:       // nothing //         break;
                case DEVINFO_FCT_RESET:       // nothing //         break;

                // --- the following bits of info are returned as NULL-terminated strings --- //
                case DEVINFO_STR_NAME:       strcpy(info.s, "K054539");      break;
                case DEVINFO_STR_FAMILY:     strcpy(info.s, "Konami custom");    break;
                case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
                case DEVINFO_STR_SOURCE_FILE:      strcpy(info.s, __FILE__);      break;
                case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
            }
        }*/

    //DEFINE_LEGACY_SOUND_DEVICE(K054539, k054539);
}
