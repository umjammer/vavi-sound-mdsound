package mdsound;


/*
 Konami 053260 PCM Sound Chip

 2004-02-28: Fixed PPCM decoding. Games Sound much better now.
*/
public class K053260 extends Instrument.BaseInstrument {
    @Override
    public String getName() {
        return "K053260";
    }

    @Override
    public String getShortName() {
        return "K053";
    }

    @Override
    public void reset(byte chipID) {
        device_reset_k053260(chipID);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipID, int clock) {
        return (int) device_start_k053260(chipID, (int) 3579545);
    }

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        return device_start_k053260(chipID, (int) clockValue);
    }

    @Override
    public void stop(byte chipID) {
        device_stop_k053260(chipID);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        k053260_update(chipID, outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        k053260_w(chipID, adr, (byte) data);
        return 0;
    }

    public static class K053260State {

        private static final int BASE_SHIFT = 16;

        public static class Channel {
            public int rate;
            public int size;
            public int start;
            public int bank;
            public int volume;
            public int play;
            public int pan;
            public int pos;
            public int loop;
            public int ppcm; /* packed PCM ( 4 bit signed ) */
            public int ppcmData;
            public byte muted;

            public void reset() {
                this.rate = 0;
                this.size = 0;
                this.start = 0;
                this.bank = 0;
                this.volume = 0;
                this.play = 0;
                this.pan = 0;
                this.pos = 0;
                this.loop = 0;
                this.ppcm = 0;
                this.ppcmData = 0;
            }
        }

        public int mode;
        public int[] regs = new int[0x30];
        public byte[] rom;
        public int romSize;
        public int[] deltaTable;
        public Channel[] channels = new Channel[] {new Channel(), new Channel(), new Channel(), new Channel()};

        private void initDeltaTable(int rate, int clock) {
            double _base = rate;
            double max = clock; /* Hz */
            int val;

            for (int i = 0; i < 0x1000; i++) {
                double v = 0x1000 - i;
                double target = max / v;
                double _fixed = 1 << BASE_SHIFT;

                if (target != 0 && _base != 0) {
                    target = _fixed / (_base / target);
                    val = (int) target;
                    if (val == 0)
                        val = 1;
                } else
                    val = 1;

                this.deltaTable[i] = val;
            }
        }

        private static int limit(int val, int max, int min) {
            if (val > max)
                val = max;
            else if (val < min)
                val = min;

            return val;
        }

        private static final int MAXOUT = +0x8000;
        private static final int MINOUT = -0x8000;

        private static final byte[] dpcmcnv = new byte[] {0, 1, 2, 4, 8, 16, 32, 64, -128, -64, -32, -16, -8, -4, -2, -1};
        private int[] lvol = new int[4], rvol = new int[4], play = new int[4], loop = new int[4], ppcm = new int[4];
        //byte[] rom = new byte[4];
        private int[] ptrRom = new int[4];
        private int[] delta = new int[4], end = new int[4], pos = new int[4];
        private byte[] ppcmData = new byte[4];

        private void check_bounds(int channel) {

            int channelStart = (this.channels[channel].bank << 16) + this.channels[channel].start;
            int channel_end = channelStart + this.channels[channel].size - 1;

            if (channelStart > this.romSize) {
                //System.err.printf("K53260: Attempting to start playing past the end of the ROM ( start = %06x, end = %06x ).\n", channelStart, channel_end);

                this.channels[channel].play = 0;

                return;
            }

            if (channel_end > this.romSize) {
                //System.err.printf("K53260: Attempting to play past the end of the ROM ( start = %06x, end = %06x ).\n", channelStart, channel_end);

                this.channels[channel].size = this.romSize - channelStart;
            }
            //if (LOG) System.err.printf("K053260: Sample Start = %06x, Sample End = %06x, Sample rate = %04x, PPCM = %s\n", channelStart, channel_end, this.channels[channel].rate, this.channels[channel].ppcm ? "yes" : "no");
        }

        public void reset() {
            for (int i = 0; i < 4; i++) {
                this.channels[i].reset();
            }
        }

        public void update(int[][] outputs, int samples) {
            /* precache some values */
            for (int i = 0; i < 4; i++) {
                if (this.channels[i].muted != 0) {
                    play[i] = 0;
                    continue;
                }
                //rom[i] = this.rom[this.channels[i].start + (this.channels[i].bank << 16)];
                ptrRom[i] = this.channels[i].start + (this.channels[i].bank << 16);
                delta[i] = this.deltaTable[this.channels[i].rate];
                lvol[i] = this.channels[i].volume * this.channels[i].pan;
                rvol[i] = this.channels[i].volume * (8 - this.channels[i].pan);
                end[i] = this.channels[i].size;
                pos[i] = this.channels[i].pos;
                play[i] = this.channels[i].play;
                loop[i] = this.channels[i].loop;
                ppcm[i] = this.channels[i].ppcm;
                ppcmData[i] = (byte) this.channels[i].ppcmData;
                if (ppcm[i] != 0)
                    delta[i] /= 2;
            }

            for (int j = 0; j < samples; j++) {

                int dataL = 0, dataR = 0;

                for (int i = 0; i < 4; i++) {
                    /* see if the Voice is on */
                    if (play[i] != 0) {
                        /* see if we're done */
                        if ((pos[i] >> BASE_SHIFT) >= end[i]) {

                            ppcmData[i] = 0;
                            if (loop[i] != 0)
                                pos[i] = 0;
                            else {
                                play[i] = 0;
                                continue;
                            }
                        }

                        byte d;
                        if (ppcm[i] != 0) { /* Packed PCM */
                            /* we only update the signal if we're starting or a real Sound sample has gone by */
                            /* this is all due to the dynamic sample rate conversion */
                            if (pos[i] == 0 || ((pos[i] ^ (pos[i] - delta[i])) & 0x8000) == 0x8000) {
                                int newdata;
                                if ((pos[i] & 0x8000) != 0) {

                                    //newdata = ((rom[i][pos[i] >> BASE_SHIFT]) >> 4) & 0x0f; /*high nybble*/
                                    newdata = ((this.rom[ptrRom[i] + (pos[i] >> BASE_SHIFT)]) >> 4) & 0x0f; /*high nybble*/
                                } else {
                                    //newdata = ((rom[i][pos[i] >> BASE_SHIFT])) & 0x0f; /*low nybble*/
                                    newdata = ((this.rom[ptrRom[i] + (pos[i] >> BASE_SHIFT)])) & 0x0f; /*low nybble*/
                                }

                                /*ppcm_data[i] = (( ( ppcm_data[i] * 62 ) >> 6 ) + dpcmcnv[newdata]);

                                if ( ppcm_data[i] > 127 )
                                    ppcm_data[i] = 127;
                                else
                                    if ( ppcm_data[i] < -128 )
                                        ppcm_data[i] = -128;*/
                                ppcmData[i] += dpcmcnv[newdata];
                            }


                            d = ppcmData[i];

                            pos[i] += delta[i];
                        } else { /* PCM */
                            //d = rom[i][pos[i] >> BASE_SHIFT];
                            d = (byte) this.rom[ptrRom[i] + (pos[i] >> BASE_SHIFT)];

                            pos[i] += delta[i];
                        }

                        if ((this.mode & 2) != 0) {
                            dataL += (d * lvol[i]) >> 2;
                            dataR += (d * rvol[i]) >> 2;
                        }
                    }
                }

                outputs[1][j] = limit(dataL, MAXOUT, MINOUT);
                outputs[0][j] = limit(dataR, MAXOUT, MINOUT);
            }

            /* update the regs now */
            for (int i = 0; i < 4; i++) {
                if (this.channels[i].muted != 0)
                    continue;
                this.channels[i].pos = pos[i];
                this.channels[i].play = play[i];
                this.channels[i].ppcmData = ppcmData[i];
            }
        }

        public int device_start(int clock) {

            int rate = clock / 32;
            /* Initialize our chip structure */

            this.mode = 0;

            //this.rom = *region;
            //this.rom_size = region.bytes();
            this.rom = null;
            this.romSize = 0x00;

            // has to be done by the player after calling device_start
            //DEVICE_RESET_CALL(k053260);

            for (int i = 0; i < 0x30; i++)
                this.regs[i] = 0;

            //this.delta_table = auto_alloc_array( device.machine(), int, 0x1000 );
            this.deltaTable = new int[0x1000];

            initDeltaTable(rate, clock);

            /* register with the save state system */
            /*device.save_item(NAME(this.mode));
            device.save_item(NAME(this.regs));

            for ( i = 0; i < 4; i++ ) {
                device.save_item(NAME(this.channels[i].rate), i);
                device.save_item(NAME(this.channels[i].size), i);
                device.save_item(NAME(this.channels[i].start), i);
                device.save_item(NAME(this.channels[i].bank), i);
                device.save_item(NAME(this.channels[i].volume), i);
                device.save_item(NAME(this.channels[i].play), i);
                device.save_item(NAME(this.channels[i].pan), i);
                device.save_item(NAME(this.channels[i].pos), i);
                device.save_item(NAME(this.channels[i].loop), i);
                device.save_item(NAME(this.channels[i].ppcm), i);
                device.save_item(NAME(this.channels[i].ppcm_data), i);
            }*/

            /* setup SH1 timer if necessary */
            //if ( this.intf.irq )
            // device.machine().scheduler().timer_pulse( attotime::from_hz(device.clock()) * 32, this.intf.irq, "this.intf.irq" );

            for (int i = 0; i < 4; i++)
                this.channels[i].muted = 0x00;

            return rate;
        }

        public void stop() {
            this.rom = null;
        }

        public void write(int offset, byte data) {
            int i, t;
            int r = offset;
            int v = data;

            if (r > 0x2f) {
                //System.err.printf("K053260: Writing past registers\n");
                return;
            }

            //this.channel.update();

            /* before we update the regs, we need to check for a latched reg */
            if (r == 0x28) {
                t = this.regs[r] ^ v;

                for (i = 0; i < 4; i++) {
                    if ((t & (1 << i)) != 0) {
                        if ((v & (1 << i)) != 0) {
                            this.channels[i].play = 1;
                            this.channels[i].pos = 0;
                            this.channels[i].ppcmData = 0;
                            check_bounds(i);
                        } else
                            this.channels[i].play = 0;
                    }
                }

                this.regs[r] = v;
                return;
            }

            /* update regs */
            this.regs[r] = v;

            /* communication registers */
            if (r < 8)
                return;

            /* channel setup */
            if (r < 0x28) {
                int channel = (r - 8) / 8;

                switch ((r - 8) & 0x07) {
                case 0: /* sample rate low */
                    this.channels[channel].rate &= 0x0f00;
                    this.channels[channel].rate |= v;
                    break;

                case 1: /* sample rate high */
                    this.channels[channel].rate &= 0x00ff;
                    this.channels[channel].rate |= (v & 0x0f) << 8;
                    break;

                case 2: /* size low */
                    this.channels[channel].size &= 0xff00;
                    this.channels[channel].size |= v;
                    break;

                case 3: /* size high */
                    this.channels[channel].size &= 0x00ff;
                    this.channels[channel].size |= v << 8;
                    break;

                case 4: /* start low */
                    this.channels[channel].start &= 0xff00;
                    this.channels[channel].start |= v;
                    break;

                case 5: /* start high */
                    this.channels[channel].start &= 0x00ff;
                    this.channels[channel].start |= v << 8;
                    break;

                case 6: /* bank */
                    this.channels[channel].bank = v & 0xff;
                    break;

                case 7: /* volume is 7 bits. Convert to 8 bits now. */
                    this.channels[channel].volume = ((v & 0x7f) << 1) | (v & 1);
                    break;
                }

                return;
            }

            switch (r) {
            case 0x2a: /* loop, ppcm */
                for (i = 0; i < 4; i++)
                    this.channels[i].loop = (v & (1 << i)) != 0 ? 1 : 0;

                for (i = 4; i < 8; i++)
                    this.channels[i - 4].ppcm = (v & (1 << i)) != 0 ? 1 : 0;
                break;

            case 0x2c: /* pan */
                this.channels[0].pan = v & 7;
                this.channels[1].pan = (v >> 3) & 7;
                break;

            case 0x2d: /* more pan */
                this.channels[2].pan = v & 7;
                this.channels[3].pan = (v >> 3) & 7;
                break;

            case 0x2f: /* control */
                this.mode = v & 7;
                /* bit 0 = read ROM */
                /* bit 1 = enable Sound output */
                /* bit 2 = unknown */
                break;
            }
        }

        public byte read(int offset) {
            switch (offset) {
            case 0x29: { /* channel status */
                int i, status = 0;

                for (i = 0; i < 4; i++)
                    status |= this.channels[i].play << i;

                return (byte) status;
            }
            //break;

            case 0x2e: /* read ROM */
                if ((this.mode & 1) != 0) {
                    int offs = this.channels[0].start + (this.channels[0].pos >> BASE_SHIFT) + (this.channels[0].bank << 16);

                    this.channels[0].pos += (1 << 16);

                    if (offs > this.romSize) {
                        //System.err.printf("%s: K53260: Attempting to read past ROM size in ROM Read Mode (offs = %06x, size = %06x).\n", device.machine().describe_context(),offs,this.rom_size );
                        //System.err.printf("K53260: Attempting to read past ROM size in ROM Read Mode (offs = %06x, size = %06x).\n", offs, this.rom_size);

                        return 0;
                    }

                    return this.rom[offs];
                }
                break;
            }

            return (byte) this.regs[offset];
        }

        public void write_rom(int romSize, int dataStart, int dataLength, byte[] romData) {
            if (this.romSize != romSize) {
                this.rom = new byte[romSize];
                this.romSize = romSize;

                for (int i = 0; i < romSize; i++) this.rom[i] = (byte) 0xff;
                //memset(this.rom, 0xFF, romSize);
            }

            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            if (dataLength >= 0) System.arraycopy(romData, 0, this.rom, dataStart, dataLength);
        }

        public void write_rom(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
            if (this.romSize != romSize) {
                this.rom = new byte[romSize];// (byte*)realloc(this.rom, romSize);
                this.romSize = romSize;

                for (int i = 0; i < romSize; i++) this.rom[i] = (byte) 0xff;
                //memset(this.rom, 0xFF, romSize);
            }

            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            if (dataLength >= 0) System.arraycopy(romData, srcStartAdr, this.rom, dataStart, dataLength);
        }

        public void set_mute_mask(int muteMask) {
            for (byte curChn = 0; curChn < 4; curChn++)
                this.channels[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
        }
    }

    private static final int MAX_CHIPS = 0x02;
    private K053260State[] K053260Data = new K053260State[] {new K053260State(), new K053260State()};

    public void device_reset_k053260(byte chipID) {
        K053260State ic = K053260Data[chipID];
        ic.reset();
    }

    public void k053260_update(byte chipID, int[][] outputs, int samples) {
        K053260State ic = K053260Data[chipID];
        ic.update(outputs, samples);
    }

    public int device_start_k053260(byte chipID, int clock) {
        if (chipID >= MAX_CHIPS)
            return 0;

        K053260State ic = K053260Data[chipID];
        return ic.device_start(clock);
    }

    public void device_stop_k053260(byte chipID) {
        K053260State ic = K053260Data[chipID];
        ic.stop();
    }

    public void k053260_w(byte chipID, int offset, byte data) {
        K053260State ic = K053260Data[chipID];
        ic.write(offset, data);
    }

    public byte k053260_r(byte chipID, int offset) {
        K053260State ic = K053260Data[chipID];
        return ic.read(offset);
    }

    public void k053260_write_rom(byte chipID, int romSize, int dataStart, int dataLength, byte[] romData) {
        K053260State info = K053260Data[chipID];
        info.write_rom(romSize, dataStart, dataLength, romData);
    }

    public void k053260_write_rom(byte chipID, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        K053260State info = K053260Data[chipID];
        info.write_rom(romSize, dataStart, dataLength, romData, srcStartAdr);
    }

    public void k053260_set_mute_mask(byte chipID, int muteMask) {
        K053260State info = K053260Data[chipID];
        info.set_mute_mask(muteMask);
    }

    /**
     * Generic get_info
     */
        /*DEVICE_GET_INFO( k053260 )
        {
            switch (state)
            {
                // --- the following bits of info are returned as 64-bit signed integers --- //
                case DEVINFO_INT_TOKEN_BYTES:     info.i = sizeof(K053260State);    break;

                // --- the following bits of info are returned as pointers to data or functions --- //
                case DEVINFO_FCT_START:       info.start = DEVICE_START_NAME( k053260 );  break;
                case DEVINFO_FCT_STOP:       // nothing //         break;
                case DEVINFO_FCT_RESET:       info.reset = DEVICE_RESET_NAME( k053260 );  break;

                // --- the following bits of info are returned as NULL-terminated strings --- //
                case DEVINFO_STR_NAME:       strcpy(info.s, "K053260");      break;
                case DEVINFO_STR_FAMILY:      strcpy(info.s, "Konami custom");    break;
                case DEVINFO_STR_VERSION:      strcpy(info.s, "1.0");       break;
                case DEVINFO_STR_SOURCE_FILE:     strcpy(info.s, __FILE__);      break;
                case DEVINFO_STR_CREDITS:      strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
            }
        }

        DEFINE_LEGACY_SOUND_DEVICE(K053260, k053260);*/
}
