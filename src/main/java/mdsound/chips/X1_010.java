package mdsound.chips;


/**
       -= Seta Hardware =-

     driver by   Luca Elia (l.elia@tin.it)

     rewrite by Manbow-J(manbowj@hamal.freemail.ne.jp)

     X1-010 Seta Custom Sound Chip (80 Pin PQFP)

 Custom programmed Mitsubishi M60016 Gate Array, 3608 gates, 148 Max I/O ports

 The X1-010 is 16 Voices Sound generator, each channel gets it's
 waveform from RAM (128 bytes per waveform, 8 bit unsigned data)
 or sampling PCM(8bit unsigned data).
<pre>
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
 </pre>
*/
public class X1_010 {

    private static final int VERBOSE_SOUND = 0;
    private static final int VERBOSE_REGISTER_WRITE = 0;
    private static final int VERBOSE_REGISTER_READ = 0;

    private static final int SETA_NUM_CHANNELS = 16;

    /** Frequency fixed decimal shift bits */
    private static final int FREQ_BASE_BITS = 14;
    // wave form envelope fixed decimal shift bits */
    private static final int ENV_BASE_BITS = 16;
    // Volume base */
    private static final int VOL_BASE = 2 * 32 * 256 / 30;

    // this structure defines the parameters for a channel */
    private static class Channel {
        private byte status;
        // volume / wave form no. */
        private byte volume;
        // frequency / pitch lo */
        private byte frequency;
        // reserved / pitch hi */
        private byte pitchHi;
        // start address / envelope time */
        private byte start;
        // end address / envelope no. */
        private byte end;
        private byte[] reserve = new byte[2];
    }

    // Variables only used here

    private int rate; // Output sampling rate (Hz) */
    private int romSize;
    private byte[] rom;
    private int soundEnable; // Sound output enable/disable */
    private byte[] reg = new byte[0x2000]; // X1-010 Register & wave form area */
    private int[] smpOffset = new int[SETA_NUM_CHANNELS];
    private int[] envOffset = new int[SETA_NUM_CHANNELS];

    private int baseClock;

    private byte[] muted = new byte[SETA_NUM_CHANNELS];

    /**
     * generate Sound to the mix buffer
     */
    public void update(int[][] outputs, int samples) {
        // mixer buffer zero clear
        for (int i = 0; i < samples; i++) {
            outputs[0][i] = 0;
            outputs[1][i] = 0;
        }

        //  if( this.sound_enable == 0 ) return;

        for (int ch = 0; ch < SETA_NUM_CHANNELS; ch++) {
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
                    int freq = this.reg[ch * 8 + 2] >> div; // +2 reg.frequency
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

    public void start(int clock, int rate) {
        this.romSize = 0x00;
        this.rom = null;
        this.baseClock = clock;
        this.rate = rate;

        for (int i = 0; i < SETA_NUM_CHANNELS; i++) {
            this.smpOffset[i] = 0;
            this.envOffset[i] = 0;
        }
        // Print some more debug info
        //Debug.printf("masterclock = %d rate = %d\n", device.clock(), this.rate);
    }

    public void stop() {
        this.rom = null;
    }

    public void reset() {
        for (int i = 0; i < 0x2000; i++) this.reg[i] = 0;
        //memset(this.HI_WORD_BUF, 0, 0x2000);
        for (int i = 0; i < SETA_NUM_CHANNELS; i++) this.smpOffset[i] = 0;
        for (int i = 0; i < SETA_NUM_CHANNELS; i++) this.envOffset[i] = 0;
    }

    public byte read(int offset) {
        return this.reg[offset];
    }

    public void write(int offset, byte data) {
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

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData, int romDataStartAddress/* = 0*/) {
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

    public void setMuteMask(int muteMask) {
        for (byte curChn = 0; curChn < SETA_NUM_CHANNELS; curChn++)
            this.muted[curChn] = (byte) ((muteMask >> curChn) & 0x01);
    }
}
