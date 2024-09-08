package mdsound.chips;


/**
 * ricoh RF5C68(or clone) PCM controller
 */
public class Rf5c68 {

    private static final int NUM_CHANNELS = 8;
    private static final int STEAM_STEP = 0x800;

    public static class Channel {
        public int enable;
        public int env;
        public int pan;
        private int start;
        private int addr;
        public int step;
        private int loopst;
        private int muted;

        public boolean key = false; // 発音中ならtrue
        public boolean keyOn = false; // キーオンしたときにtrue(falseは受け取り側が行う)

        private void reset() {
            this.enable = 0;
            this.env = 0;
            this.pan = 0;
            this.start = 0;
            this.addr = 0;
            this.step = 0;
            this.loopst = 0;
        }
    }

    private static class MemStream {
        private int baseAddr;
        private int endAddr;
        private int curAddr;
        private int curStep;
        private byte[] memPnt;

        private void flush(byte[] data) {
            if (this.curAddr >= this.endAddr)
                return;

            System.arraycopy(this.memPnt, (this.curAddr - this.baseAddr), data, this.curAddr, this.endAddr - this.curAddr);
            this.curAddr = this.endAddr;
        }

        /**
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

        private void reset() {
            this.baseAddr = 0x0000;
            this.curAddr = 0x0000;
            this.endAddr = 0x0000;
            this.curStep = 0x0000;
            this.memPnt = null;
        }

        private void drain(int samples, byte[] data) {
            if (samples != 0 && this.curAddr < this.endAddr) {
                this.curStep += STEAM_STEP * samples;
                if (this.curStep >= 0x0800) {
                    int i = this.curStep >> 11;
                    this.curStep &= 0x07ff;

                    if (this.curAddr + i > this.endAddr)
                        i = this.endAddr - this.curAddr;

                    System.arraycopy(this.memPnt, (this.curAddr - this.baseAddr), data, this.curAddr, i);
                    this.curAddr += i;
                }
            }
        }
    }

    private final Channel[] channels = new Channel[] {
            new Channel(), new Channel(), new Channel(), new Channel(),
            new Channel(), new Channel(), new Channel(), new Channel()
    };
    private int cbank;
    private int wbank;
    private int enable;
    private int datasize;
    private byte[] data;
    private final MemStream memstrm = new MemStream();

    public void update(int[][] outputs, int samples) {
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
            Channel chan = this.channels[i];

            // if this channel is active, accumulate samples
            if (chan.enable != 0) {
                int lv = (chan.pan & 0x0f) * chan.env;
                int rv = ((chan.pan >> 4) & 0x0f) * chan.env;

                // loop over the sample buffer
                for (int j = 0; j < samples; j++) {
                    int sample;

                    // trigger sample Callback
//                    if (this.sample_callback) {
//                        if (((channels.addr >> 11) & 0xfff) == 0xfff)
//                            this.sample_callback(this.device, ((channels.addr >> 11) / 0x2000));
//                    }

                    this.memstrm.checkSample((chan.addr >> 11) & 0xffff, chan.step, this.data);
                    // fetch the sample and handle looping
                    sample = this.data[(chan.addr >> 11) & 0xffff] & 0xff;
                    if (sample == 0xff) {
                        chan.addr = chan.loopst << 11;
                        sample = this.data[(chan.addr >> 11) & 0xffff] & 0xff;

                        // if we loop to a loop point, we're effectively dead
                        if (sample == 0xff) {
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
    public int start(int clock) {
        this.datasize = 0x1_0000;
        this.data = new byte[this.datasize];

        for (int chn = 0; chn < NUM_CHANNELS; chn++)
            this.channels[chn].muted = 0x00;

        return (clock & 0x7fff_ffff) / 384;
    }

    public void stop() {
        this.data = null;
    }

    public void reset() {
        // Clear the PCM memory.
        //memset(this.data, 0x00, this.datasize);
        for (int ind = 0; ind < this.datasize; ind++) this.data[ind] = 0;
        this.enable = 0;
        this.cbank = 0;
        this.wbank = 0;

        // clear channel registers
        for (int i = 0; i < NUM_CHANNELS; i++) {
            this.channels[i].reset();
        }

        this.memstrm.reset();
    }

    /**
     * write register
     */
    public void write(int offset, int data) {
        Channel chan = this.channels[this.cbank];
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
            this.enable = (data >> 7) & 1;
            if ((data & 0x40) != 0)
                this.cbank = data & 7;
            else
                this.wbank = data & 15;
            break;

        case 0x08: // channel on/off reg
            for (i = 0; i < 8; i++) {
                int old = this.channels[i].enable;

                this.channels[i].enable = (~data >> i) & 1;

                if (old == 0 && this.channels[i].enable != 0) this.channels[i].keyOn = true;

                if (this.channels[i].enable == 0)
                    this.channels[i].addr = this.channels[i].start << (8 + 11);
            }
            break;
        }
    }

    /**
     * read memory
     */
    public int readMemory(int offset) {
        return this.data[this.wbank * 0x1000 + offset] & 0xff;
    }

    /**
     * write memory
     */
    public void writeMemory(int offset, int data) {
        this.memstrm.flush(this.data);
        this.data[this.wbank * 0x1000 | offset] = (byte) data;
    }

    public void writeRam(int dataStart, int dataLength, byte[] ramData) {
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

    public void setMuteMask(int muteMask) {
        for (int c = 0; c < NUM_CHANNELS; c++)
            this.channels[c].muted = (muteMask >> c) & 0x01;
    }

    public void setMute(int ch, int mute) {
        channels[ch].muted = mute;
    }

    public Channel getChannel(int ch) {
        return channels[ch];
    }
}
