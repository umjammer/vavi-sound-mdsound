package mdsound.chips;

/**
 Konami 051649 - SCC1 Sound as used in Haunted Castle, City Bomber

 This file is pieced together by Bryan McPhail from a combination of
 Namco Sound, Amuse by Cab, Haunted Castle schematics and whoever first
 figured out SCC!

 The 051649 is a 5 channel Sound generator, each channel gets its
 waveform from RAM (32 bytes per waveform, 8 bit signed data).

 This Sound chips is the same as the Sound chips in some Konami
 megaROM cartridges for the MSX. It is actually well researched
 and documented:

 http://bifi.msxnet.org/msxnet/tech/scc.html

 Thanks to Sean Young (sean@mess.org) for some bugfixes.

 K052539 is more or less equivalent to this chips except channel 5
 does not share waveram with channel 4.
 */
public class K051649 {

    public K051649() {
        for (int i = 0; i < channelList.length; i++) {
            channelList[i] = new Channel();
        }
    }

    private static final int FREQ_BITS = 16;
    private static final int DEF_GAIN = 8;

    /** this structure defines the parameters for a channel */
    public static class Channel {
        private long counter;
        public int frequency;
        public int volume;
        public int key;
        private byte[] waveRam = new byte[32];
        /** 19991207.CAB */
        private byte muted;

        private void reset() {
            this.frequency = 0;
            this.volume = 0;
            this.counter = 0;
            this.key = 0;
        }
    }

    private Channel[] channelList = new Channel[5];

    public Channel getChannel(int ch) {
        return channelList[ch];
    }

    /* Global Sound parameters */
    private int mClock, rate;

    /* mixer tables and internal buffers */
    private short[] mixerTable;
    private int mixerTablePtr;
    private short[] mixerLookup;
    private int mixerLookupPtr;
    private short[] mixerBuffer;
    private int mixerBufferPtr;

    private int curReg;
    private byte test;

    /* build a table to divide by the number of voices */
    private void makeMixerTable(int voices) {
        int count = voices * 256;

        /* allocate memory */
        this.mixerTable = new short[2 * count];
        this.mixerTablePtr = 0;

        /* find the middle of the table */
        this.mixerLookupPtr = count;

        /* fill in the table - 16 bit case */
        for (int i = 0; i < count; i++) {
            int val = i * DEF_GAIN * 16 / voices;
            if (val > 32768) val = 32768;
            this.mixerTable[this.mixerLookupPtr + i] = (short) val;
            this.mixerTable[this.mixerLookupPtr - i] = (short) (-val);
        }
    }

    /* generate Sound to the mix buffer */
    public void update(int[][] outputs, int samples) {
        Channel[] voice = this.channelList;
        int[] buffer = outputs[0];
        int[] buffer2 = outputs[1];
        short[] mix;
        int mixPtr = 0;

        // zap the contents of the mixer buffer
        if (this.mixerBuffer == null || this.mixerBuffer.length < samples) {
            this.mixerBuffer = new short[samples];
        }
        for (int i = 0; i < samples; i++) this.mixerBuffer[i] = 0;

        for (int j = 0; j < 5; j++) {
            // channel is halted for freq < 9
            if (voice[j].frequency > 8 && voice[j].muted == 0) {
                byte[] w = voice[j].waveRam; // 19991207.CAB
                int v = voice[j].volume * voice[j].key;
                int c = (int) voice[j].counter;
                /* Amuse source:  Cab suggests this method gives greater resolution */
                /* Sean Young 20010417: the formula is really: f = clock/(16*(f+1)) */
                int step = (int) (((long) this.mClock * (1 << FREQ_BITS)) / (float) ((voice[j].frequency + 1) * 16 * (this.rate / 32)) + 0.5);

                mix = this.mixerBuffer;
                mixPtr = 0;

                // add our contribution
                for (int i = 0; i < samples; i++) {
                    int offs;

                    c += step;
                    offs = (c >> FREQ_BITS) & 0x1f;
                    mix[mixPtr++] += (short) ((w[offs] * v) >> 3);
                }

                // update the counter for this Voice
                voice[j].counter = c;
            }
        }

        // mix it down
        mix = this.mixerBuffer;
        mixPtr = 0;
        for (int i = 0; i < samples; i++) {
            buffer[i] = buffer2[i] = this.mixerTable[this.mixerLookupPtr + mix[mixPtr++]];
            i++;
        }
    }

    public int start(int clock) {
        /* get stream channels */
        this.mClock = clock & 0x7FFFFFFF;
        this.rate = this.mClock / 16;

        /* allocate a buffer to mix into - 1 second's worth should be more than enough */
        this.mixerBuffer = new short[this.rate];

        /* build the mixer table */
        makeMixerTable(5);

        for (byte curChn = 0; curChn < 5; curChn++)
            this.channelList[curChn].muted = 0x00;

        return this.rate;
    }

    public void reset() {
        Channel[] voice = this.channelList;

        // reset all the voices
        for (int i = 0; i < 5; i++) {
            voice[i].reset();
        }

        // other parameters
        this.test = 0x00;
        this.curReg = 0x00;
    }

    public void writeWaveForm(int offset, byte data) {
        // waveram is read-only?
        if (((this.test & 0x40) != 0) || (((this.test & 0x80) != 0) && offset >= 0x60))
            return;

        //stream_update(this.stream);

        if (offset >= 0x60) {
            // channel 5 shares waveram with channel 4
            this.channelList[3].waveRam[offset & 0x1f] = data;
            this.channelList[4].waveRam[offset & 0x1f] = data;
        } else
            this.channelList[offset >> 5].waveRam[offset & 0x1f] = data;
    }

    public byte readWaveForm(int offset) {
        // test-register bits 6/7 expose the internal counter
        if ((this.test & 0xc0) != 0) {
            //stream_update(this.stream);

            if (offset >= 0x60)
                offset += (int) ((this.channelList[3 + (this.test >> 6 & 1)].counter >> FREQ_BITS));
            else if ((this.test & 0x40) != 0)
                offset += (int) ((this.channelList[offset >> 5].counter >> FREQ_BITS));
        }
        return this.channelList[offset >> 5].waveRam[offset & 0x1f];
    }

    public void writeWaveFormK05239(int offset, byte data) {
        // waveram is read-only?
        if ((this.test & 0x40) != 0)
            return;

        this.channelList[offset >> 5].waveRam[offset & 0x1f] = data;
    }

    public byte readWaveFormK05239(int offset) {
        // test-register bit 6 exposes the internal counter
        if ((this.test & 0x40) != 0) {
            offset += (int) ((this.channelList[offset >> 5].counter >> FREQ_BITS));
        }
        return this.channelList[offset >> 5].waveRam[offset & 0x1f];
    }

    public void writeVolume(int offset, byte data) {
        this.channelList[offset & 0x7].volume = data & 0xf;
    }

    public void writeFrequency(int offset, byte data) {
        Channel chn = this.channelList[offset >> 1];

        // test-register bit 5 resets the internal counter
        if ((this.test & 0x20) != 0)
            chn.counter = 0xffffffffffffffffL; // ~0
        else if (chn.frequency < 9)
            chn.counter |= ((1 << FREQ_BITS) - 1);

        // update frequency
        if ((offset & 1) != 0)
            chn.frequency = (chn.frequency & 0x0FF) | ((data << 8) & 0xF00);
        else
            chn.frequency = (chn.frequency & 0xF00) | (data << 0);
        chn.counter &= 0xFFFF0000; // Valley Bell: Behaviour according to openMSX
    }

    public void wtiteKeyOnOff(int offset, byte data) {
        for (int i = 0; i < 5; i++) {
            this.channelList[i].key = data & 1;
            data >>= 1;
        }
    }

    public void writeTest(int offset, byte data) {
        this.test = data;
    }

    public void write(int offset, byte data) {
        switch (offset & 1) {
        case 0x00:
            this.curReg = data;
            break;
        case 0x01:
            switch (offset >> 1) {
            case 0x00:
                writeWaveFormK05239(this.curReg, data);
                break;
            case 0x01:
                writeFrequency(this.curReg, data);
                break;
            case 0x02:
                writeVolume(this.curReg, data);
                break;
            case 0x03:
                wtiteKeyOnOff(this.curReg, data);
                break;
            case 0x04:
                writeWaveFormK05239(this.curReg, data);
                break;
            case 0x05:
                writeTest(this.curReg, data);
                break;
            }
            break;
        }
    }

    public void setMuteMask(int muteMask) {
        for (byte curChn = 0; curChn < 5; curChn++)
            this.channelList[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
    }

    public int getWaveRam(int ch, int index) {
        return channelList[ch].waveRam[index] & 0xff;
    }
}
