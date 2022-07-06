package mdsound;

public class Rf5c68 extends Instrument.BaseInstrument {

    @Override
    public String getName() {
        return "RF5C68";
    }

    @Override
    public String getShortName() {
        return "RF68";
    }

    @Override
    public void reset(byte chipId) {
        visVolume = new int[][][] {
                {new int[] {0, 0}},
                {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipId, int clock) {
        return start(chipId, clock, 0);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        return device_start_rf5c68(chipId, clockValue);
    }

    @Override
    public void stop(byte chipId) {
        device_stop_rf5c68(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        rf5c68_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        rf5c68_w(chipId, adr, (byte) data);
        return 0;
    }

    //
    // ricoh RF5C68(or clone) PCM controller
    //
    public static class Rf5c68State {

        private static final int NUM_CHANNELS = 8;
        private static final int STEAM_STEP = 0x800;

        public static class Channel {
            public byte enable;
            public byte env;
            public byte pan;
            public byte start;
            public int addr;
            public int step;
            public int loopst;
            public byte muted;

            public boolean key = false;//発音中ならtrue
            public boolean keyOn = false;//キーオンしたときにtrue(falseは受け取り側が行う)

            public void reset() {
                this.enable = 0;
                this.env = 0;
                this.pan = 0;
                this.start = 0;
                this.addr = 0;
                this.step = 0;
                this.loopst = 0;
            }
        }

        public static class MemStream {
            public int baseAddr;
            public int endAddr;
            public int curAddr;
            public int curStep;
            public byte[] memPnt;

            private void flush(byte[] data) {
                if (this.curAddr >= this.endAddr)
                    return;

                System.arraycopy(this.memPnt, (this.curAddr - this.baseAddr), data, this.curAddr, this.endAddr - this.curAddr);
                this.curAddr = this.endAddr;
            }

            /*
             * stream update
             */
            private void checkSample(int addr, int speed, byte[] data) {
                int smplSpd = (speed >= 0x0800) ? (speed >> 11) : 1;
                if (addr >= this.curAddr) {
                    // Is the stream too fast? (e.g. about to catch up the output)
                    if (addr - this.curAddr <= smplSpd * 5) {
                        // Yes - delay the stream
                        this.curAddr -= smplSpd * 4;
                        if (this.curAddr < this.baseAddr)
                            this.curAddr = this.baseAddr;
                    }
                } else {
                    // Is the stream too slow? (e.g. the output is about to catch up the stream)
                    if (this.curAddr - addr <= smplSpd * 5) {
                        if (this.curAddr + smplSpd * 4 >= this.endAddr) {
                            this.flush(data);
                        } else {
                            System.arraycopy(this.memPnt, (this.curAddr - this.baseAddr), data, this.curAddr, smplSpd * 4);
                            this.curAddr += smplSpd * 4;
                        }
                    }
                }
            }

            public void reset() {
                this.baseAddr = 0x0000;
                this.curAddr = 0x0000;
                this.endAddr = 0x0000;
                this.curStep = 0x0000;
                this.memPnt = null;
            }

            public void drain(int samples, byte[] data) {
                if (samples != 0 && this.curAddr < this.endAddr) {
                    this.curStep += STEAM_STEP * samples;
                    if (this.curStep >= 0x0800) {
                        int i = this.curStep >> 11;
                        this.curStep &= 0x07FF;

                        if (this.curAddr + i > this.endAddr)
                            i = this.endAddr - this.curAddr;

                        System.arraycopy(this.memPnt, (this.curAddr - this.baseAddr), data, this.curAddr, i);
                        this.curAddr += i;
                    }
                }
            }
        }

        public Channel[] chan = new Channel[] {
                new Channel(), new Channel(), new Channel(), new Channel(),
                new Channel(), new Channel(), new Channel(), new Channel()
        };
        public byte cbank;
        public byte wbank;
        public byte enable;
        public int datasize;
        public byte[] data;
        public MemStream memstrm = new MemStream();

        private void update(int[][] outputs, int samples) {
            int[] left = outputs[0];
            int[] right = outputs[1];

            // start with clean buffers
            for (int ind = 0; ind < samples; ind++) {
                left[ind] = 0;
                right[ind] = 0;
            }

            // bail if not enabled
            if (this.enable == 0)
                return;

            // loop over channels
            for (int i = 0; i < NUM_CHANNELS; i++) {
                Channel chan = this.chan[i];

                // if this channel is active, accumulate samples
                if (chan.enable != 0) {
                    int lv = (chan.pan & 0x0f) * chan.env;
                    int rv = ((chan.pan >> 4) & 0x0f) * chan.env;

                    // loop over the sample buffer
                    for (int j = 0; j < samples; j++) {
                        int sample;

                        // trigger sample Callback
                        /*if(this.sample_callback) {
                            if(((chan.addr >> 11) & 0xfff) == 0xfff)
                                this.sample_callback(this.device,((chan.addr >> 11)/0x2000));
                        }*/

                        this.memstrm.checkSample((chan.addr >> 11) & 0xFFFF, chan.step, this.data);
                        // fetch the sample and handle looping
                        sample = this.data[(chan.addr >> 11) & 0xffff];
                        if (sample == (byte) 0xff) {
                            chan.addr = chan.loopst << 11;
                            sample = this.data[(chan.addr >> 11) & 0xffff];

                            // if we loop to a loop point, we're effectively dead
                            if (sample == (byte) 0xff) {
                                chan.key = false;
                                break;
                            }
                        }
                        chan.key = true;
                        chan.addr += chan.step;

                        if (chan.muted == 0) {
                            // add to the buffer
                            if ((sample & 0x80) != 0) {
                                sample &= 0x7f;
                                left[j] += (sample * lv) >> 5;
                                right[j] += (sample * rv) >> 5;
                            } else {
                                left[j] -= (sample * lv) >> 5;
                                right[j] -= (sample * rv) >> 5;
                            }
                        }

                        //Debug.printf("Ch:%d L:%d R:%d", i, outputs[0][j], outputs[1][j]);
                    }
                }
            }

            this.memstrm.drain(samples, this.data);
        }

        /**
         * start
         */
        private int start(int clock) {
            this.datasize = 0x10000;
            this.data = new byte[this.datasize];

            for (int chn = 0; chn < NUM_CHANNELS; chn++)
                this.chan[chn].muted = 0x00;

            return (clock & 0x7FFFFFFF) / 384;
        }

        private void stop() {
            this.data = null;
        }

        private void reset() {
            // Clear the PCM memory.
            //memset(this.data, 0x00, this.datasize);
            for (int ind = 0; ind < this.datasize; ind++) this.data[ind] = 0;
            this.enable = 0;
            this.cbank = 0;
            this.wbank = 0;

            // clear channel registers
            for (int i = 0; i < NUM_CHANNELS; i++) {
                this.chan[i].reset();
            }

            this.memstrm.reset();
        }

        /**
         * write register
         */
        public void write(int offset, byte data) {
            Channel chan = this.chan[this.cbank];
            int i;

            // force the stream to update first
            //stream_update(this.stream);

            // switch off the address
            switch (offset) {
            case 0x00: // envelope
                chan.env = data;
                break;

            case 0x01: // pan
                chan.pan = data;
                break;

            case 0x02: // FDL
                chan.step = (chan.step & 0xff00) | (data & 0x00ff);
                break;

            case 0x03: // FDH
                chan.step = (chan.step & 0x00ff) | ((data << 8) & 0xff00);
                break;

            case 0x04: // LSL
                chan.loopst = (chan.loopst & 0xff00) | (data & 0x00ff);
                break;

            case 0x05: // LSH
                chan.loopst = (chan.loopst & 0x00ff) | ((data << 8) & 0xff00);
                break;

            case 0x06: // ST
                chan.start = data;
                if (chan.enable == 0)
                    chan.addr = chan.start << (8 + 11);
                break;

            case 0x07: // control reg
                this.enable = (byte) ((data >> 7) & 1);
                if ((data & 0x40) != 0)
                    this.cbank = (byte) (data & 7);
                else
                    this.wbank = (byte) (data & 15);
                break;

            case 0x08: // channel on/off reg
                for (i = 0; i < 8; i++) {
                    byte old = this.chan[i].enable;

                    this.chan[i].enable = (byte) ((~data >> i) & 1);

                    if (old == 0 && this.chan[i].enable != 0) this.chan[i].keyOn = true;

                    if (this.chan[i].enable == 0)
                        this.chan[i].addr = this.chan[i].start << (8 + 11);
                }
                break;
            }
        }

        /**
         * read memory
         */
        private byte readMemory(int offset) {
            return this.data[this.wbank * 0x1000 + offset];
        }

        /**
         * write memory
         */
        public void writeMemory(int offset, byte data) {
            this.memstrm.flush(this.data);
            this.data[this.wbank * 0x1000 | offset] = data;
        }

        private void writeRam(int dataStart, int dataLength, byte[] ramData) {
            MemStream ms = this.memstrm;
            int bytCnt;

            dataStart |= this.wbank * 0x1000;
            if (dataStart >= this.datasize)
                return;
            if (dataStart + dataLength > this.datasize)
                dataLength = this.datasize - dataStart;

            this.memstrm.flush(this.data);

            ms.baseAddr = dataStart;
            ms.curAddr = ms.baseAddr;
            ms.endAddr = ms.baseAddr + dataLength;
            ms.curStep = 0x0000;
            ms.memPnt = ramData;

            bytCnt = 0x40; // SegaSonic Arcade: Run! Run! Run! needs such a high value
            if (ms.curAddr + bytCnt > ms.endAddr)
                bytCnt = ms.endAddr - ms.curAddr;

            System.arraycopy(ms.memPnt, (ms.curAddr - ms.baseAddr), this.data, ms.curAddr, bytCnt);
            ms.curAddr += bytCnt;
        }

        public void writeRam2(int dataStart, int dataLength, byte[] ramData, int srcStartAdr) {
            MemStream ms = this.memstrm;
            int bytCnt;

            dataStart |= this.wbank * 0x1000;
            if (dataStart >= this.datasize)
                return;
            if (dataStart + dataLength > this.datasize)
                dataLength = this.datasize - dataStart;

            this.memstrm.flush(this.data);

            ms.baseAddr = dataStart;
            ms.curAddr = ms.baseAddr;
            ms.endAddr = ms.baseAddr + dataLength;
            ms.curStep = 0x0000;
            byte[] dat = new byte[dataLength];
            System.arraycopy(ramData, srcStartAdr + 0, dat, 0, dataLength);
            ms.memPnt = dat;

            bytCnt = 0x40; // SegaSonic Arcade: Run! Run! Run! needs such a high value
            if (ms.curAddr + bytCnt > ms.endAddr)
                bytCnt = ms.endAddr - ms.curAddr;

            System.arraycopy(ms.memPnt, (ms.curAddr - ms.baseAddr), this.data, ms.curAddr, bytCnt);
            ms.curAddr += bytCnt;
        }

        private void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < NUM_CHANNELS; curChn++)
                this.chan[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
        }
    }

    private static final int MAX_CHIPS = 0x02;
    public Rf5c68State[] rf5C68Data = new Rf5c68State[] {new Rf5c68State(), new Rf5c68State()};

    private void rf5c68_update(byte chipId, int[][] outputs, int samples) {
        Rf5c68State chip = rf5C68Data[chipId];
        chip.update(outputs, samples);
    }

    private int device_start_rf5c68(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        Rf5c68State chip = rf5C68Data[chipId];
        return chip.start(clock);
    }

    private void device_stop_rf5c68(byte chipId) {
        Rf5c68State chip = rf5C68Data[chipId];
        chip.stop();
    }

    private void device_reset_rf5c68(byte chipId) {
        Rf5c68State chip = rf5C68Data[chipId];
        chip.reset();
    }

    public void rf5c68_w(byte chipId, int offset, byte data) {
        Rf5c68State chip = rf5C68Data[chipId];
        chip.write(offset, data);
    }

    private byte rf5c68_mem_r(byte chipId, int offset) {
        Rf5c68State chip = rf5C68Data[chipId];
        return chip.readMemory(offset);
    }

    public void rf5c68_mem_w(byte chipId, int offset, byte data) {
        Rf5c68State chip = rf5C68Data[chipId];
        chip.writeMemory(offset, data);
    }

    private void rf5c68_write_ram(byte chipId, int dataStart, int dataLength, byte[] ramData) {
        Rf5c68State chip = rf5C68Data[chipId];
        chip.writeRam(dataStart, dataLength, ramData);
    }

    public void rf5c68_write_ram2(byte chipId, int dataStart, int dataLength, byte[] ramData, int srcStartAdr) {
        Rf5c68State chip = rf5C68Data[chipId];
        chip.writeRam2(dataStart, dataLength, ramData, srcStartAdr);
    }

    private void rf5c68_set_mute_mask(byte chipId, int muteMask) {
        Rf5c68State chip = rf5C68Data[chipId];
        chip.setMuteMask(muteMask);
    }

    /*
     * Generic get_info
     */
    /*DEVICE_GET_INFO( Rf5c68 ) {
            case DEVINFO_STR_NAME:       strcpy(info.s, "RF5C68");      break;
            case DEVINFO_STR_FAMILY:     strcpy(info.s, "Ricoh PCM");     break;
            case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
            case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
        }
    }*/
}
