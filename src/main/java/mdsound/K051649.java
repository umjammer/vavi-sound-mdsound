package mdsound;


/*
 Konami 051649 - SCC1 Sound as used in Haunted Castle, City Bomber

 This file is pieced together by Bryan McPhail from a combination of
 Namco Sound, Amuse by Cab, Haunted Castle schematics and whoever first
 figured out SCC!

 The 051649 is a 5 channel Sound generator, each channel gets its
 waveform from RAM (32 bytes per waveform, 8 bit signed data).

 This Sound chip is the same as the Sound chip in some Konami
 megaROM cartridges for the MSX. It is actually well researched
 and documented:

 http://bifi.msxnet.org/msxnet/tech/scc.html

 Thanks to Sean Young (sean@mess.org) for some bugfixes.

 K052539 is more or less equivalent to this chip except channel 5
 does not share waveram with channel 4.
 */
public class K051649 extends Instrument.BaseInstrument {

    @Override
    public void reset(byte chipID) {
        device_reset_k051649(chipID);
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipID, int clock) {
        return device_start_k051649(chipID, clock);
    }

    @Override
    public int start(byte chipID, int SamplingRate, int clockValue, Object... Option) {
        if (scc1Data[chipID] == null) {
            scc1Data[chipID] = new K051649State();
        }

        int sampRate = device_start_k051649(chipID, clockValue);
        //int flags = 1;
        //if (Option != null && Option.length > 0) flags = (int)(byte)Option[0];
        //k054539_init_flags(chipID, flags);

        return sampRate;
    }

    @Override
    public void stop(byte chipID) {
        device_stop_k051649(chipID);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        k051649_update(chipID, outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    public static class K051649State {

        K051649State() {
            for (int i = 0; i < channelList.length; i++) {
                channelList[i] = new K051649State.Channel();
            }
        }

        private static final int FREQ_BITS = 16;
        private static final int DEF_GAIN = 8;

        /* this structure defines the parameters for a channel */
        public static class Channel {
            public long counter;
            public int frequency;
            public int volume;
            public int key;
            public byte[] waveRam = new byte[32];        /* 19991207.CAB */
            public byte muted;

            public void reset() {
                this.frequency = 0;
                this.volume = 0;
                this.counter = 0;
                this.key = 0;
            }
        }

        public Channel[] channelList = new Channel[5];

        /* Global Sound parameters */
        public int mclock, rate;

        /* mixer tables and internal buffers */
        public short[] mixerTable;
        public int mixerTablePtr;
        public short[] mixerLookup;
        public int mixerLookupPtr;
        public short[] mixer_buffer;
        public int mixerBufferPtr;

        public int curReg;
        public byte test;

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
        private void update(int[][] outputs, int samples) {
            Channel[] voice = this.channelList;
            int[] buffer = outputs[0];
            int[] buffer2 = outputs[1];
            short[] mix;
            int mixPtr = 0;

            // zap the contents of the mixer buffer
            if (this.mixer_buffer == null || this.mixer_buffer.length < samples) {
                this.mixer_buffer = new short[samples];
            }
            for (int i = 0; i < samples; i++) this.mixer_buffer[i] = 0;

            for (int j = 0; j < 5; j++) {
                // channel is halted for freq < 9
                if (voice[j].frequency > 8 && voice[j].muted == 0) {
                    byte[] w = voice[j].waveRam;            /* 19991207.CAB */
                    int v = voice[j].volume * voice[j].key;
                    int c = (int) voice[j].counter;
                    /* Amuse source:  Cab suggests this method gives greater resolution */
                    /* Sean Young 20010417: the formula is really: f = clock/(16*(f+1)) */
                    int step = (int) (((long) this.mclock * (1 << FREQ_BITS)) / (float) ((voice[j].frequency + 1) * 16 * (this.rate / 32)) + 0.5);

                    mix = this.mixer_buffer;
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
            mix = this.mixer_buffer;
            mixPtr = 0;
            for (int i = 0; i < samples; i++) {
                buffer[i] = buffer2[i] = this.mixerTable[this.mixerLookupPtr + mix[mixPtr++]];
                i++;
            }
        }

        private int start(int clock) {
            /* get stream channels */
            this.mclock = clock & 0x7FFFFFFF;
            this.rate = this.mclock / 16;

            /* allocate a buffer to mix into - 1 second's worth should be more than enough */
            this.mixer_buffer = new short[this.rate];

            /* build the mixer table */
            makeMixerTable( 5);

            for (byte curChn = 0; curChn < 5; curChn++)
                this.channelList[curChn].muted = 0x00;

            return this.rate;
        }

        private void reset() {
            Channel[] voice = this.channelList;

            // reset all the voices
            for (int i = 0; i < 5; i++) {
                voice[i].reset();
            }

            // other parameters
            this.test = 0x00;
            this.curReg = 0x00;
        }

        private void writeWaveForm(int offset, byte data) {
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

        private byte readWaveForm(int offset) {
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

        private void writeWaveFormK05239(int offset, byte data) {
            // waveram is read-only?
            if ((this.test & 0x40) != 0)
                return;

            this.channelList[offset >> 5].waveRam[offset & 0x1f] = (byte) data;
        }

        private byte readWaveFormK05239(int offset) {
            // test-register bit 6 exposes the internal counter
            if ((this.test & 0x40) != 0) {
                offset += (int) ((this.channelList[offset >> 5].counter >> FREQ_BITS));
            }
            return this.channelList[offset >> 5].waveRam[offset & 0x1f];
        }

        private void writeVolume(int offset, byte data) {
            this.channelList[offset & 0x7].volume = data & 0xf;
        }

        private void writeFrequency(int offset, byte data) {
            Channel chn = this.channelList[offset >> 1];

            // test-register bit 5 resets the internal counter
            if ((this.test & 0x20) != 0)
                chn.counter = 0xffffffffffffffffl;//~0
            else if (chn.frequency < 9)
                chn.counter |= ((1 << FREQ_BITS) - 1);

            // update frequency
            if ((offset & 1) != 0)
                chn.frequency = (chn.frequency & 0x0FF) | ((data << 8) & 0xF00);
            else
                chn.frequency = (chn.frequency & 0xF00) | (data << 0);
            chn.counter &= 0xFFFF0000; // Valley Bell: Behaviour according to openMSX
        }

        private void wtiteKeyOnOff(int offset, byte data) {
            for (int i = 0; i < 5; i++) {
                this.channelList[i].key = data & 1;
                data >>= 1;
            }
        }

        private void writeTest(int offset, byte data) {
            this.test = data;
        }

        private void write(int offset, byte data) {
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

        private void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < 5; curChn++)
                this.channelList[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
        }
    }

    private static final int MAX_CHIPS = 0x02;
    private K051649State[] scc1Data = new K051649State[MAX_CHIPS];

    @Override
    public String getName() {
        return "K051649";
    }

    @Override
    public String getShortName() {
        return "K051";
    }

    /* generate Sound to the mix buffer */
    private void k051649_update(byte chipID, int[][] outputs, int samples) {
        K051649State info = scc1Data[chipID];
        info.update(outputs, samples);
    }

    private int device_start_k051649(byte chipID, int clock) {
        if (chipID >= MAX_CHIPS)
            return 0;

        K051649State info = scc1Data[chipID];
        return info.start(clock);
    }

    private void device_stop_k051649(byte chipID) {
        K051649State info = scc1Data[chipID];
    }

    private void device_reset_k051649(byte chipID) {
        K051649State info = scc1Data[chipID];
        info.reset();
    }

    //
    private void k051649_waveform_w(byte chipID, int offset, byte data) {
        K051649State info = scc1Data[chipID];
        info.writeWaveForm(offset, data);
    }

    private byte k051649_waveform_r(byte chipID, int offset) {
        K051649State info = scc1Data[chipID];
        return info.readWaveForm(offset);
    }

    /* SY 20001114: Channel 5 doesn't share the waveform with channel 4 on this chip */
    private void k052539_waveform_w(byte chipID, int offset, byte data) {
        K051649State info = scc1Data[chipID];
        info.writeWaveFormK05239(offset, data);
    }

    private byte k052539_waveform_r(byte chipID, int offset) {
        K051649State info = scc1Data[chipID];
        return info.readWaveFormK05239(offset);
    }

    private void k051649_volume_w(byte chipID, int offset, byte data) {
        K051649State info = scc1Data[chipID];
        info.writeVolume(offset, data);
    }

    private void k051649_frequency_w(byte chipID, int offset, byte data) {
        K051649State info = scc1Data[chipID];
        info.writeFrequency(offset, data);
    }

    private void k051649_keyonoff_w(byte chipID, int offset, byte data) {
        K051649State info = scc1Data[chipID];
        info.wtiteKeyOnOff(offset, data);
    }

    private void k051649_test_w(byte chipID, int offset, byte data) {
        K051649State info = scc1Data[chipID];
        info.writeTest(offset, data);
    }

    private byte k051649_test_r(byte chipID, int offset) {
        // reading the test register sets it to $ff!
        k051649_test_w(chipID, offset, (byte) 0xff);
        return (byte) 0xff;
    }

    private void k051649_w(byte chipID, int offset, byte data) {
        K051649State info = scc1Data[chipID];
        info.write(offset, data);
    }

    private void k051649_set_mute_mask(byte chipID, int muteMask) {
        K051649State info = scc1Data[chipID];
        info.setMuteMask(muteMask);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        k051649_w(chipID, adr, (byte) data);
        return 0;
    }

    public K051649State GetK051649_State(byte chipID) {
        return scc1Data[chipID];
    }

    /**
     * Generic get_info
     */
        /*DEVICE_GET_INFO( k051649 )
        {
            switch (state)
            {
                // --- the following bits of info are returned as 64-bit signed integers ---
                case DEVINFO_INT_TOKEN_BYTES:     info.i = sizeof(K051649State);    break;

                // --- the following bits of info are returned as pointers to data or functions ---
                case DEVINFO_FCT_START:       info.start = DEVICE_START_NAME( k051649 );  break;
                case DEVINFO_FCT_STOP:       // nothing //         break;
                case DEVINFO_FCT_RESET:       info.reset = DEVICE_RESET_NAME( k051649 );  break;

                // --- the following bits of info are returned as NULL-terminated strings ---
                case DEVINFO_STR_NAME:       strcpy(info.s, "K051649");      break;
                case DEVINFO_STR_FAMILY:     strcpy(info.s, "Konami custom");    break;
                case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
                case DEVINFO_STR_SOURCE_FILE:      strcpy(info.s, __FILE__);      break;
                case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
            }
        }*/


    //DEFINE_LEGACY_SOUND_DEVICE(K051649, k051649);

}
