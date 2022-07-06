package mdsound;

public class X1_010 extends Instrument.BaseInstrument {

    @Override
    public String getName() {
        return "X1-010";
    }

    @Override
    public String getShortName() {
        return "X1-010";
    }

    public X1_010() {
    }

    @Override
    public void reset(byte chipId) {
        device_reset_x1_010(chipId);
    }

    @Override
    public int start(byte chipId, int clock) {
        return device_start_x1_010(chipId, 16000000);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        return device_start_x1_010(chipId, clockValue);
    }

    @Override
    public void stop(byte chipId) {
        device_stop_x1_010(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        seta_update(chipId, outputs, samples);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        seta_sound_w(chipId, (port << 8) | adr, (byte) data);
        return 0;
    }

    /*
           -= Seta Hardware =-

         driver by   Luca Elia (l.elia@tin.it)

         rewrite by Manbow-J(manbowj@hamal.freemail.ne.jp)

         X1-010 Seta Custom Sound Chip (80 Pin PQFP)

     Custom programmed Mitsubishi M60016 Gate Array, 3608 gates, 148 Max I/O ports

     The X1-010 is 16 Voices Sound generator, each channel gets it's
     waveform from RAM (128 bytes per waveform, 8 bit unsigned data)
     or sampling PCM(8bit unsigned data).

    Registers:
     8 registers per channel (mapped to the lower bytes of 16 words on the 68K)

     Reg:    Bits:       Meaning:

     0       7654 3---
       ---- -2--   PCM/Waveform repeat flag (0:Ones 1:Repeat) (*1)
       ---- --1-   Sound out select (0:PCM 1:Waveform)
       ---- ---0   Key on / off

     1       7654 ----   PCM Volume 1 (L?)
       ---- 3210   PCM Volume 2 (R?)
          Waveform No.

     2                   PCM Frequency
          Waveform Pitch Lo

     3                   Waveform Pitch Hi

     4                   PCM Sample Start / 0x1000           [Start/End in bytes]
          Waveform Envelope Time

     5                   PCM Sample End 0x100 - (Sample End / 0x1000)    [PCM ROM is Max 1MB?]
          Waveform Envelope No.
     6                   Reserved
     7                   Reserved

     offset 0x0000 - 0x0fff  Wave form data
     offset 0x1000 - 0x1fff  Envelope data

     *1 : when 0 is specified, hardware interrupt is caused(allways return soon)

    */
    private static class X1010State {

        private static final int VERBOSE_SOUND = 0;
        private static final int VERBOSE_REGISTER_WRITE = 0;
        private static final int VERBOSE_REGISTER_READ = 0;

        private static final int SETA_NUM_CHANNELS = 16;

        // Frequency fixed decimal shift bits
        private static final int FREQ_BASE_BITS = 14;
        // wave form envelope fixed decimal shift bits
        private static final int ENV_BASE_BITS = 16;
        // Volume base
        private static final int VOL_BASE = 2 * 32 * 256 / 30;

        // this structure defines the parameters for a channel
        private static class Channel {
            public byte status;
            // volume / wave form no.
            public byte volume;
            // frequency / pitch lo
            public byte frequency;
            // reserved / pitch hi
            public byte pitchHi;
            // start address / envelope time
            public byte start;
            // end address / envelope no.
            public byte end;
            public byte[] reserve = new byte[2];
        }

        // Variables only used here

        public int rate; // Output sampling rate (Hz)
        public int romSize;
        public byte[] rom;
        public int soundEnable; // Sound output enable/disable
        public byte[] reg = new byte[0x2000]; // X1-010 Register & wave form area
        public int[] smpOffset = new int[SETA_NUM_CHANNELS];
        public int[] envOffset = new int[SETA_NUM_CHANNELS];

        public int baseClock;

        public byte[] muted = new byte[SETA_NUM_CHANNELS];

        /**
         * generate Sound to the mix buffer
         */
        private void update(int[][] outputs, int samples) {
            // mixer buffer zero clear
            for (int i = 0; i < samples; i++) {
                outputs[0][i] = 0;
                outputs[1][i] = 0;
            }

            //  if( this.sound_enable == 0 ) return;

            for (int ch = 0; ch < SETA_NUM_CHANNELS; ch++) {
                //reg = (Channel)this.reg[ch * 8];// sizeof(Channel)];
                if ((this.reg[ch * 8 + 0] & 1) != 0 && this.muted[ch] == 0) { // reg.status
                    // Key On
                    int[] bufL = outputs[0];
                    int[] bufR = outputs[1];

                    int div = (this.reg[ch * 8 + 0] & 0x80) != 0 ? 1 : 0;
                    if ((this.reg[ch * 8 + 0] & 2) == 0) { // PCM sampling
                        int start = this.reg[ch * 8 + 4] * 0x1000; // +4 reg.start
                        int end = (0x100 - this.reg[ch * 8 + 5]) * 0x1000; // +5 reg.end
                        int volL = ((this.reg[ch * 8 + 1] >> 4) & 0xf) * VOL_BASE; // +1 reg.volume
                        int volR = ((this.reg[ch * 8 + 1] >> 0) & 0xf) * VOL_BASE; // +1 reg.volume
                        int smpOffs = this.smpOffset[ch];
                        int freq = this.reg[ch * 8 + 2] >> div;//+2 reg.frequency
                        // Meta Fox does write the frequency register, but this is a hack to make it "work" with the current setup
                        // This is broken for Arbalester (it writes 8), but that'll be fixed later.
                        if (freq == 0) freq = 4;
                        int smpStep = (int) ((float) this.baseClock / 8192.0f
                                * freq * (1 << FREQ_BASE_BITS) / (float) this.rate + 0.5f);
                        if (smpOffs == 0) {
                            //Debug.printf("Play sample %p - %p, channel %X volume %d:%d freq %X step %X offset %X\n",
                            // start, end, ch, volL, volR, freq, smpStep, smpOffs);
                        }
                        for (int i = 0; i < samples; i++) {
                            int delta = smpOffs >> FREQ_BASE_BITS;
                            // sample ended?
                            if (start + delta >= end) {
                                this.reg[ch * 8 + 0] &= 0xfe; // ~0x01: Key off +0: reg.status
                                break;
                            }
                            byte data = this.rom[start + delta];
                            bufL[i] += (data * volL / 256);
                            bufR[i] += (data * volR / 256);
                            smpOffs += smpStep;
                        }
                        this.smpOffset[ch] = smpOffs;
                    } else { // Wave form
                        int start = this.reg[ch * 8 + 1] * 128 + 0x1000;
                        int smpOffs = this.smpOffset[ch];
                        int freq = ((this.reg[ch * 8 + 3] << 8) + this.reg[ch * 8 + 2]) >> div;
                        int smpStep = (int) ((float) this.baseClock / 128.0 / 1024.0 / 4.0 * freq * (1 << FREQ_BASE_BITS) / (float) this.rate + 0.5f);

                        int env = this.reg[ch * 8 + 5] * 128;
                        int envOffs = this.envOffset[ch];
                        int envStep = (int) (
                                (float) this.baseClock / 128.0 / 1024.0 / 4.0
                                        * this.reg[ch * 8 + 4] * (1 << ENV_BASE_BITS) / (float) this.rate + 0.5f
                        );
                        // Print some more debug info
                        if (smpOffs == 0) {
                            //Debug.printf("Play waveform %X, channel %X volume %X freq %4X step %X offset %X\n",
                            //reg.volume, ch, reg.end, freq, smpStep, smpOffs);
                        }
                        for (int i = 0; i < samples; i++) {
                            int delta = envOffs >> ENV_BASE_BITS;
                            // Envelope one shot mode
                            if ((this.reg[ch * 8 + 0] & 4) != 0 && delta >= 0x80) {
                                this.reg[ch * 8 + 0] &= 0xfe;// ~0x01; // Key off
                                break;
                            }
                            int vol = this.reg[env + (delta & 0x7f)];
                            int volL = ((vol >> 4) & 0xf) * VOL_BASE;
                            int volR = ((vol >> 0) & 0xf) * VOL_BASE;
                            byte data = this.reg[start + ((smpOffs >> FREQ_BASE_BITS) & 0x7f)];
                            bufL[i] += (data * volL / 256);
                            bufR[i] += (data * volR / 256);
                            smpOffs += smpStep;
                            envOffs += envStep;
                        }
                        this.smpOffset[ch] = smpOffs;
                        this.envOffset[ch] = envOffs;
                    }
                }
            }
        }

        private int start(int clock) {
            this.romSize = 0x00;
            this.rom = null;
            this.baseClock = clock;
            this.rate = clock / 512;
            if (((CHIP_SAMPLING_MODE & 0x01) != 0 && this.rate < CHIP_SAMPLE_RATE) ||
                    CHIP_SAMPLING_MODE == 0x02)
                this.rate = CHIP_SAMPLE_RATE;

            for (int i = 0; i < SETA_NUM_CHANNELS; i++) {
                this.smpOffset[i] = 0;
                this.envOffset[i] = 0;
            }
            // Print some more debug info
            //Debug.printf("masterclock = %d rate = %d\n", device.clock(), this.rate);

            return this.rate;
        }

        private void stop() {
            this.rom = null;
        }

        private void reset() {
            for (int i = 0; i < 0x2000; i++) this.reg[i] = 0;
            //memset(this.HI_WORD_BUF, 0, 0x2000);
            for (int i = 0; i < SETA_NUM_CHANNELS; i++) this.smpOffset[i] = 0;
            for (int i = 0; i < SETA_NUM_CHANNELS; i++) this.envOffset[i] = 0;
        }

        private byte sound_r(int offset) {
            return this.reg[offset];
        }

        private void sound_w(int offset, byte data) {
            int channel = offset / 8;
            int reg = offset % 8;

            if (channel < SETA_NUM_CHANNELS && reg == 0
                    && (this.reg[offset] & 1) == 0 && (data & 1) != 0) {
                this.smpOffset[channel] = 0;
                this.envOffset[channel] = 0;
            }
            //Debug.printf("%s: offset %6X : data %2X\n", device.machine().describe_context(), offset, data);
            this.reg[offset] = data;
        }

        public void write_rom(int romSize, int dataStart, int dataLength, byte[] romData, int romDataStartAddress/* = 0*/) {
            if (this.romSize != romSize) {
                this.rom = new byte[romSize];
                this.romSize = romSize;
                for (int i = 0; i < romSize; i++) this.rom[i] = (byte) 0xff;
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            if (dataLength >= 0)
                System.arraycopy(romData, romDataStartAddress, this.rom, dataStart, dataLength);
        }

        public void set_mute_mask(int muteMask) {
            for (byte curChn = 0; curChn < SETA_NUM_CHANNELS; curChn++)
                this.muted[curChn] = (byte) ((muteMask >> curChn) & 0x01);
        }
    }

    private static final int MAX_CHIPS = 0x02;
    private X1010State[] x1010Data = new X1010State[] {new X1010State(), new X1010State()};

    private void seta_update(byte chipId, int[][] outputs, int samples) {
        X1010State info = x1010Data[chipId];
        info.update(outputs, samples);
    }

    private int device_start_x1_010(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        X1010State info = x1010Data[chipId];
        return info.start(clock);
    }

    private void device_stop_x1_010(byte chipId) {
        X1010State info = x1010Data[chipId];
        info.stop();
    }

    private void device_reset_x1_010(byte chipId) {
        X1010State info = x1010Data[chipId];
        info.reset();
    }

  /*void seta_sound_enable_w(device_t *device, int data) {
   X1010State *info = get_safe_token(device);
   info.sound_enable = data;
  }*/

    // Use these for 8 bit CPUs

    private byte seta_sound_r(byte chipId, int offset) {
        X1010State info = x1010Data[chipId];
        return info.sound_r(offset);
    }

    private void seta_sound_w(byte chipId, int offset, byte data) {
        X1010State info = x1010Data[chipId];
        info.sound_w(offset, data);
    }

    public void x1_010_write_rom(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int romDataStartAddress/* = 0*/) {
        X1010State info = x1010Data[chipId];
        info.write_rom(romSize, dataStart, dataLength, romData, romDataStartAddress);
    }

    public void x1_010_set_mute_mask(byte chipId, int muteMask) {
        X1010State info = x1010Data[chipId];
        info.set_mute_mask(muteMask);
    }

    // Use these for 16 bit CPUs

  /*READ16_DEVICE_HANDLER( seta_sound_word_r ) {
   //X1010State *info = get_safe_token(device);
   X1010State *info = &X1010Data[chipId];
   int ret;

   ret = info.HI_WORD_BUF[offset]<<8;
   ret += (seta_sound_r( device, offset )&0xff);
   LOG_REGISTER_READ(( "%s: Read X1-010 Offset:%04X Data:%04X\n", device.machine().describe_context(), offset, ret ));
   return ret;
  }

  WRITE16_DEVICE_HANDLER( seta_sound_word_w ) {
   //X1010State *info = get_safe_token(device);
   X1010State *info = &X1010Data[chipId];
   info.HI_WORD_BUF[offset] = (data>>8)&0xff;
   seta_sound_w( device, offset, data&0xff );
   Debug.printf(( "%s: Write X1-010 Offset:%04X Data:%04X\n", device.machine().describe_context(), offset, data ));
  }*/

    /**
     * Generic get_info
     */
  /*DEVICE_GET_INFO( X1_010 ) {
   switch (state) {
    // --- the following bits of info are returned as 64-bit signed integers ---
    case DEVINFO_INT_TOKEN_BYTES:     info.i = sizeof(X1010State);    break;

    // --- the following bits of info are returned as pointers to data or functions ---
    case DEVINFO_FCT_START:       info.start = DEVICE_START_NAME( X1_010 );   break;
    case DEVINFO_FCT_STOP: // Nothing //         break;
    case DEVINFO_FCT_RESET: // Nothing //         break;

    // --- the following bits of info are returned as NULL-terminated strings ---
    case DEVINFO_STR_NAME:       strcpy(info.s, "X1-010");      break;
    case DEVINFO_STR_FAMILY:     strcpy(info.s, "Seta custom");     break;
    case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
    case DEVINFO_STR_SOURCE_FILE:      strcpy(info.s, __FILE__);      break;
    case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
   }
  }
  DEFINE_LEGACY_SOUND_DEVICE(X1_010, X1_010);*/
}
