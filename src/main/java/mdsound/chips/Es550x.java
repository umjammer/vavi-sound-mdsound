/*
 * license:BSD-3-Clause
 *
 * copyright-holders: Aaron Giles
 */

package mdsound.chips;

import java.util.function.Consumer;


/**
 * Ensoniq ES5505/6 driver
 *
 * by Aaron Giles
 <pre>
Ensoniq OTIS - ES5505                                            Ensoniq OTTO - ES5506

  OTIS is a VLSI device designed in a 2 micron double metal        OTTO is a VLSI device designed in a 1.5 micron double metal
   CMOS process. The device is the next generation of audio         CMOS process. The device is the next generation of audio
   technology from ENSONIQ. This new chip achieves a new            technology from ENSONIQ. All calculations in the device are
   level of audio fidelity performance. These improvements          made with at least 18-bit accuracy.
   are achieved through the use of frequency interpolation
   and on board real time digital filters. All calculations       The major features of OTTO are:
   in the device are made with at least 16 bit accuracy.           - 68 pin PLCC package
                                                                   - On chip real time digital filters
 The major features of OTIS are:                                   - Frequency interpolation
  - 48 Pin dual in line package                                    - 32 independent voices
  - On chip real time digital filters                              - Loop start and stop posistions for each voice
  - Frequency interpolation                                        - Bidirectional and reverse looping
  - 32 independent voices (up from 25 in DOCII)                    - 68000 compatibility for asynchronous bus communication
  - Loop start and stop positions for each voice                   - separate host and sound memory interface
  - Bidirectional and reverse looping                              - 6 channel stereo serial communication port
  - 68000 compatibility for asynchronous bus communication         - Programmable clocks for defining serial protocol
  - On board pulse width modulation D to A                         - Internal volume multiplication and stereo panning
  - 4 channel stereo serial communication port                     - A to D input for pots and wheels
  - Internal volume multiplication and stereo panning              - Hardware support for envelopes
  - A to D input for pots and wheels                               - Support for dual OTTO systems
  - Up to 10MHz operation                                          - Optional compressed data format for sample data
                                                                   - Up to 16MHz operation
              ______    ______
            _|o     \__/      |_
 A17/D13 - |_|1             48|_| - VSS                                                           A A A A A A
            _|                |_                                                                  2 1 1 1 1 1 A
 A18/D14 - |_|2             47|_| - A16/D12                                                       0 9 8 7 6 5 1
            _|                |_                                                                  / / / / / / 4
 A19/D15 - |_|3             46|_| - A15/D11                                   H H H H H H H V V H D D D D D D /
            _|                |_                                              D D D D D D D S D D 1 1 1 1 1 1 D
      BS - |_|4             45|_| - A14/D10                                   0 1 2 3 4 5 6 S D 7 5 4 3 2 1 0 9
            _|                |_                                             ------------------------------------+
  PWZERO - |_|5             44|_| - A13/D9                                  / 9 8 7 6 5 4 3 2 1 6 6 6 6 6 6 6 6  |
            _|                |_                                           /                    8 7 6 5 4 3 2 1  |
    SER0 - |_|6             43|_| - A12/D8                                |                                      |
            _|       E        |_                                      SER0|10                                  60|A13/D8
    SER1 - |_|7      N      42|_| - A11/D7                            SER1|11                                  59|A12/D7
            _|       S        |_                                      SER2|12                                  58|A11/D6
    SER2 - |_|8      O      41|_| - A10/D6                            SER3|13              ENSONIQ             57|A10/D5
            _|       N        |_                                      SER4|14                                  56|A9/D4
    SER3 - |_|9      I      40|_| - A9/D5                             SER5|15                                  55|A8/D3
            _|       Q        |_                                      WCLK|16                                  54|A7/D2
 SERWCLK - |_|10            39|_| - A8/D4                            LRCLK|17               ES5506             53|A6/D1
            _|                |_                                      BCLK|18                                  52|A5/D0
   SERLR - |_|11            38|_| - A7/D3                             RESB|19                                  51|A4
            _|                |_                                       HA5|20                                  50|A3
 SERBCLK - |_|12     E      37|_| - A6/D2                              HA4|21                OTTO              49|A2
            _|       S        |_                                       HA3|22                                  48|A1
     RLO - |_|13     5      36|_| - A5/D1                              HA2|23                                  47|A0
            _|       5        |_                                       HA1|24                                  46|BS1
     RHI - |_|14     0      35|_| - A4/D0                              HA0|25                                  45|BS0
            _|       5        |_                                    POT_IN|26                                  44|DTACKB
     LLO - |_|15            34|_| - CLKIN                                 |   2 2 2 3 3 3 3 3 3 3 3 3 3 4 4 4 4  |
            _|                |_                                          |   7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3  |
     LHI - |_|16            33|_| - CAS                                   +--------------------------------------+
            _|                |_                                              B E E B E B B D S B B B E K B W W
     POT - |_|17     O      32|_| - AMUX                                      S B L N L S S D S S X S   L Q / /
            _|       T        |_                                              E E R E H M C V V A U A   C R R R
   DTACK - |_|18     I      31|_| - RAS                                       R R D H           R M C     I M
            _|       S        |_                                              _ D                 A
     R/W - |_|19            30|_| - E                                         T
            _|                |_                                              O
      MS - |_|20            29|_| - IRQ                                       P
            _|                |_
      CS - |_|21            28|_| - A3
            _|                |_
     RES - |_|22            27|_| - A2
            _|                |_
     VSS - |_|23            26|_| - A1
            _|                |_
     VDD - |_|24            25|_| - A0
             |________________|
 * </pre>
 *
 * @author Aaron Giles
 */
public abstract class Es550x {

    protected static final int LP3 = 1;
    protected static final int LP4 = 2;
    protected static final int LP_MASK = LP3 | LP4;

    /** constants for volumes */
    private static final int VOLUME_ACC_BIT = 20;

    /** constants for address */
    protected static final int ADDRESS_FRAC_BIT = 11;

    private static final int FINE_FILTER_BIT = 16;
    private static final int FILTER_BIT = 12;
    protected static final int FILTER_SHIFT = FINE_FILTER_BIT - FILTER_BIT;

    private static final int ULAW_MAXBITS = 8;

    protected static final int CONTROL_BS1 = 0x8000;
    protected static final int CONTROL_BS0 = 0x4000;
    protected static final int CONTROL_CMPD = 0x2000;
    protected static final int CONTROL_CA2 = 0x1000;
    protected static final int CONTROL_CA1 = 0x0800;
    protected static final int CONTROL_CA0 = 0x0400;
    protected static final int CONTROL_LP4 = 0x0200;
    protected static final int CONTROL_LP3 = 0x0100;
    protected static final int CONTROL_IRQ = 0x0080;
    protected static final int CONTROL_DIR = 0x0040;
    protected static final int CONTROL_IRQE = 0x0020;
    protected static final int CONTROL_BLE = 0x0010;
    protected static final int CONTROL_LPE = 0x0008;
    protected static final int CONTROL_LEI = 0x0004;
    protected static final int CONTROL_STOP1 = 0x0002;
    protected static final int CONTROL_STOP0 = 0x0001;

    protected static final int CONTROL_BSMASK = CONTROL_BS1 | CONTROL_BS0;
    protected static final int CONTROL_CAMASK = CONTROL_CA2 | CONTROL_CA1 | CONTROL_CA0;
    protected static final int CONTROL_LPMASK = CONTROL_LP4 | CONTROL_LP3;
    protected static final int CONTROL_LOOPMASK = CONTROL_BLE | CONTROL_LPE;
    protected static final int CONTROL_STOPMASK = CONTROL_STOP1 | CONTROL_STOP0;

    // ES5505 has sightly different control bit
    protected static final int CONTROL_5505_LP4 = 0x0800;
    protected static final int CONTROL_5505_LP3 = 0x0400;
    protected static final int CONTROL_5505_CA1 = 0x0200;
    protected static final int CONTROL_5505_CA0 = 0x0100;

    protected static final int CONTROL_5505_LPMASK = CONTROL_5505_LP4 | CONTROL_5505_LP3;
    protected static final int CONTROL_5505_CAMASK = CONTROL_5505_CA1 | CONTROL_5505_CA0;

    /** struct describing a single playing voice */
    protected static class Voice {

        // external state
        /** control register */
        int control;
        /** frequency count register */
        long freqCount;
        /** start register */
        long start;
        /** left volume register */
        int lVol;
        /** end register */
        long end;
        /** left volume ramp register */
        int lvRamp;
        /** accumulator register */
        long accum;
        /** right volume register */
        int rVol;
        /** right volume ramp register */
        int rvRamp;
        /** envelope count register */
        int eCount;
        /** k2 register */
        int k2;
        /** k2 ramp register */
        int k2Ramp;
        /** k1 register */
        int k1;
        /** k1 ramp register */
        int k1Ramp;
        /** filter storage O4(n-1) */
        int o4n1;
        /** filter storage O3(n-1) */
        int o3n1;
        /** filter storage O3(n-2) */
        int o3n2;
        /** filter storage O2(n-1) */
        int o2n1;
        /** filter storage O2(n-2) */
        int o2n2;
        /** filter storage O1(n-1) */
        int o1n1;
        /** external address bank */
        long exBank;

        // internal state
        /** index of this voice */
        int index;
        /** filter count */
        int filtCount;

        boolean muted;
    }

    // internal state

    /** current sample rate */
    protected int sampleRate;
    /** master clock frequency */
    protected int masterClock;
    /** right shift accumulator for generate integer address */
    private int addressAccShift;
    /** accumulator mask */
    protected long addressAccMask;
    /** right shift volume for generate integer volume */
    private int volumeShift;
    /** right shift output for output normalizing */
    private int volumeAccShift;
    /** current register page */
    protected int currentPage;
    /** number of active voices */
    protected int activeVoices = 0x1f;
    /** MODE register */
    protected int mode;
    /** IRQV register */
    protected int irqv = 0x80;
    /** current voice index value */
    private int voiceIndex;

    /** the 32 voices */
    protected final Voice[] voices = new Voice[32];

    {
        for (int i = 0; i < voices.length; i++) voices[i] = new Voice();
    }

    private short[] ulawLookup;
    private int[] volumeLookup;

    /** number of output channels: 1 .. 6 */
    protected int channels;

    /** sample rom per bank, 16 bit words */
    protected final short[][] regions = new short[4][];

    /** callback for when sample rate is changed */
    private Consumer<Integer> sampleRateChanged;

    public void setSampleRateChanged(Consumer<Integer> sampleRateChanged) {
        this.sampleRateChanged = sampleRateChanged;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getVoiceIndex() {
        return voiceIndex;
    }

    protected int getBank(int control) {
        return 0;
    }

    protected int getCa(int control) {
        return 0;
    }

    protected int getLp(int control) {
        return 0;
    }

    private static long lShiftSigned(long val, int shift) {
        return (shift >= 0) ? val << shift : val >>> (-shift);
    }

    private static long rShiftSigned(long val, int shift) {
        return (shift >= 0) ? val >> shift : val << (-shift);
    }

    private int getVolume(int volume) {
        return volumeLookup[(int) rShiftSigned(volume, volumeShift)];
    }

    protected long getAddressAccShiftedVal(long val, int bias) {
        return lShiftSigned(val, addressAccShift - bias);
    }

    protected long getAddressAccShiftedVal(long val) {
        return getAddressAccShiftedVal(val, 0);
    }

    protected long getAddressAccRes(long val, int bias) {
        return (shiftRight(val, addressAccShift - bias));
    }

    protected long getAddressAccRes(long val) {
        return getAddressAccRes(val, 0);
    }

    /** unsigned version of rshift_signed, u64 in the original */
    private static long shiftRight(long val, int shift) {
        return (shift >= 0) ? val >>> shift : val << (-shift);
    }

    protected long getIntegerAddr(long accum, int bias) {
        return ((accum + ((long) bias << ADDRESS_FRAC_BIT)) & addressAccMask) >>> ADDRESS_FRAC_BIT;
    }

    private long getSample(int sample, int volume) {
        return rShiftSigned((long) sample * getVolume(volume), volumeAccShift);
    }

    //
    // device
    //

    /** device-specific startup */
    protected void deviceStart(int clock) {
        // initialize the rest of the structure
        masterClock = clock;
        irqv = 0x80;
    }

    /** device-specific reset */
    public void reset() {
    }

    /** device-specific stop */
    public void stop() {
    }

    /** sets the sample rate from the number of active voices, and tells the host */
    protected void updateSampleRate() {
        sampleRate = masterClock / (16 * (activeVoices + 1));
        if (sampleRateChanged != null)
            sampleRateChanged.accept(sampleRate);
    }

    /** update the IRQ state */
    private void updateIrqState() {
        // ES5505/6 irq line has been set high - inform the host
        // no host cpu to inform here
    }

    protected void updateInternalIrqState() {
        // Host (cpu) has just read the voice interrupt vector (voice IRQ ack).
        //
        // Reset the voice vector to show the IRQB line is low (top bit set).
        // If we have any stacked interrupts (other voices waiting to be
        // processed - with their IRQ bit set) then they will be moved into
        // the vector next time the voice is processed.  In emulation
        // terms they get updated next time generate_samples() is called.

        irqv = 0x80;
    }

    /** compute static tables */
    protected void computeTables(int totalVolumeBit, int exponentBit, int mantissaBit) {
        // allocate ulaw lookup table
        ulawLookup = new short[1 << ULAW_MAXBITS];

        // generate ulaw lookup table
        for (int i = 0; i < (1 << ULAW_MAXBITS); i++) {
            int rawVal = ((i << (16 - ULAW_MAXBITS)) | (1 << (15 - ULAW_MAXBITS))) & 0xffff;
            int exponent = rawVal >> 13;
            int mantissa = (rawVal << 3) & 0xffff;

            if (exponent == 0)
                ulawLookup[i] = (short) ((short) mantissa >> 7);
            else {
                mantissa = (mantissa >> 1) | (~mantissa & 0x8000);
                ulawLookup[i] = (short) ((short) mantissa >> (7 - exponent));
            }
        }

        int volumeBit = exponentBit + mantissaBit;
        volumeShift = totalVolumeBit - volumeBit;
        int volumeLen = 1 << volumeBit;
        // allocate volume lookup table
        volumeLookup = new int[volumeLen];

        // generate volume lookup table
        int exponentShift = 1 << exponentBit;
        int exponentMask = exponentShift - 1;

        int mantissaLen = 1 << mantissaBit;
        int mantissaMask = mantissaLen - 1;
        int mantissaShift = exponentShift - mantissaBit - 1;

        for (int i = 0; i < volumeLen; i++) {
            int exponent = (i >> mantissaBit) & exponentMask;
            int mantissa = (i & mantissaMask) | mantissaLen;

            volumeLookup[i] = (int) (((long) mantissa << mantissaShift) >>> (exponentShift - exponent));
        }
        volumeAccShift = (16 + exponentMask) - VOLUME_ACC_BIT;

        // init the voices
        for (int j = 0; j < 32; j++) {
            voices[j].index = j;
            voices[j].control = CONTROL_STOPMASK;
            voices[j].lVol = 1 << (totalVolumeBit - 1);
            voices[j].rVol = 1 << (totalVolumeBit - 1);
        }
    }

    /** get address accumulator mask */
    protected void getAccumMask(int addressInteger, int addressFrac) {
        addressAccShift = ADDRESS_FRAC_BIT - addressFrac;
        addressAccMask = lShiftSigned((((1L << addressInteger) - 1) << addressFrac) | ((1L << addressFrac) - 1), addressAccShift);
        if (addressAccShift > 0)
            addressAccMask |= (1L << addressAccShift) - 1;
    }

    /** interpolate between two samples */
    private int interpolate(int sample1, int sample2, long accum) {
        int shifted = 1 << ADDRESS_FRAC_BIT;
        int mask = shifted - 1;
        int frac = (int) (accum & mask & addressAccMask);
        return (sample1 * (shifted - frac) + sample2 * frac) >> ADDRESS_FRAC_BIT;
    }

    // apply lowpass/highpass result
    private static int applyLowpass(int out, int cutoff, int in) {
        return ((cutoff >>> FILTER_SHIFT) * (out - in) / (1 << FILTER_BIT)) + in;
    }

    private static int applyHighpass(int out, int cutoff, int in, int prev) {
        return out - prev + ((cutoff >>> FILTER_SHIFT) * in) / (1 << (FILTER_BIT + 1)) + in / 2;
    }

    /** apply the 4-pole digital filter to the sample */
    private int applyFilters(Voice voice, int sample) {
        // pole 1 is always low-pass using K1
        sample = applyLowpass(sample, voice.k1, voice.o1n1);
        voice.o1n1 = sample;

        // pole 2 is always low-pass using K1
        sample = applyLowpass(sample, voice.k1, voice.o2n1);
        voice.o2n2 = voice.o2n1;
        voice.o2n1 = sample;

        // remaining poles depend on the current filter setting
        switch (getLp(voice.control)) {
            case 0 -> {
                // pole 3 is high-pass using K2
                sample = applyHighpass(sample, voice.k2, voice.o3n1, voice.o2n2);
                voice.o3n2 = voice.o3n1;
                voice.o3n1 = sample;

                // pole 4 is high-pass using K2
                sample = applyHighpass(sample, voice.k2, voice.o4n1, voice.o3n2);
                voice.o4n1 = sample;
            }
            case LP3 -> {
                // pole 3 is low-pass using K1
                sample = applyLowpass(sample, voice.k1, voice.o3n1);
                voice.o3n2 = voice.o3n1;
                voice.o3n1 = sample;

                // pole 4 is high-pass using K2
                sample = applyHighpass(sample, voice.k2, voice.o4n1, voice.o3n2);
                voice.o4n1 = sample;
            }
            case LP4 -> {
                // pole 3 is low-pass using K2
                sample = applyLowpass(sample, voice.k2, voice.o3n1);
                voice.o3n2 = voice.o3n1;
                voice.o3n1 = sample;

                // pole 4 is low-pass using K2
                sample = applyLowpass(sample, voice.k2, voice.o4n1);
                voice.o4n1 = sample;
            }
            case LP3 | LP4 -> {
                // pole 3 is low-pass using K1
                sample = applyLowpass(sample, voice.k1, voice.o3n1);
                voice.o3n2 = voice.o3n1;
                voice.o3n1 = sample;

                // pole 4 is low-pass using K2
                sample = applyLowpass(sample, voice.k2, voice.o4n1);
                voice.o4n1 = sample;
            }
        }
        return sample;
    }

    /** update the envelopes */
    protected abstract void updateEnvelopes(Voice voice);

    /**
     * check for loop end and loop appropriately
     * @return new accumulator
     */
    protected abstract long checkForEndForward(Voice voice, long accum);

    /**
     * check for loop end and loop appropriately
     * @return new accumulator
     */
    protected abstract long checkForEndReverse(Voice voice, long accum);

    /** tell each voice to generate samples */
    protected abstract void generateSamples(int[][] outputs, int samples);

    protected void updateIndex(Voice voice) {
        voiceIndex = voice.index;
    }

    /** @return unsigned 16 bit word */
    protected int readSample(Voice voice, long addr) {
        updateIndex(voice);
        short[] rom = regions[getBank(voice.control)];
        if (rom == null)
            return 0;
        long index = voice.exBank + addr;
        return index < rom.length ? rom[(int) index] & 0xffff : 0;
    }

    /** general u-law decoding routine */
    protected void generateUlaw(Voice voice, int[] dest, int l) {
        int freqCount = (int) voice.freqCount;
        long accum = voice.accum & addressAccMask;

        // outer loop, in case we switch directions
        if ((voice.control & CONTROL_STOPMASK) == 0) {
            // fetch two samples
            int val1 = readSample(voice, getIntegerAddr(accum, 0));
            int val2 = readSample(voice, getIntegerAddr(accum, 1));

            // decompress u-law
            val1 = ulawLookup[val1 >> (16 - ULAW_MAXBITS)];
            val2 = ulawLookup[val2 >> (16 - ULAW_MAXBITS)];

            generate(voice, dest, l, val1, val2, accum, freqCount);
        } else {
            // if we stopped, process any additional envelope
            if (voice.eCount != 0)
                updateEnvelopes(voice);
            voice.accum = accum;
        }
    }

    /** general PCM decoding routine */
    protected void generatePcm(Voice voice, int[] dest, int l) {
        int freqCount = (int) voice.freqCount;
        long accum = voice.accum & addressAccMask;

        // outer loop, in case we switch directions
        if ((voice.control & CONTROL_STOPMASK) == 0) {
            // fetch two samples
            int val1 = (short) readSample(voice, getIntegerAddr(accum, 0));
            int val2 = (short) readSample(voice, getIntegerAddr(accum, 1));

            generate(voice, dest, l, val1, val2, accum, freqCount);
        } else {
            // if we stopped, process any additional envelope
            if (voice.eCount != 0)
                updateEnvelopes(voice);
            voice.accum = accum;
        }
    }

    /** the common part of generate_ulaw and generate_pcm, after the samples are fetched */
    private void generate(Voice voice, int[] dest, int l, int val1, int val2, long accum, int freqCount) {
        boolean forward = (voice.control & CONTROL_DIR) == 0;

        // interpolate
        val1 = interpolate(val1, val2, accum);
        if (forward)
            accum = (accum + (freqCount & 0xffff_ffffL)) & addressAccMask;
        else
            accum = (accum - (freqCount & 0xffff_ffffL)) & addressAccMask;

        // apply filters
        val1 = applyFilters(voice, val1);

        // update filters/volumes
        if (voice.eCount != 0)
            updateEnvelopes(voice);

        // apply volumes and add
        if (!voice.muted) {
            dest[l] += (int) getSample(val1, voice.lVol);
            dest[l + 1] += (int) getSample(val1, voice.rVol);
        }

        // check for loop end
        if (forward)
            accum = checkForEndForward(voice, accum);
        else
            accum = checkForEndReverse(voice, accum);

        voice.accum = accum;
    }

    /** general interrupt handling routine */
    protected void generateIrq(Voice voice, int v) {
        // does this voice have it's IRQ bit raised?
        if ((voice.control & CONTROL_IRQ) != 0) {
            // only update voice vector if existing IRQ is acked by host
            if ((irqv & 0x80) != 0) {
                // latch voice number into vector, and set high bit low
                irqv = v & 0x1f;

                // take down IRQ bit on voice
                voice.control &= ~CONTROL_IRQ;

                // inform host of irq
                updateIrqState();
            }
        }
    }

    /**
     * sound_stream_update - handle a stream update
     *
     * @param outputs [2][samples] stereo, all the output channels are mixed down
     */
    public void update(int[][] outputs, int samples) {
        generateSamples(outputs, samples);
    }

    /**
     * write a sample rom image, the way VGM data blocks carry it.
     *
     * @param romSize the whole size of the region
     * @param dataStart bit 31: 8 bit rom, bit 29-28: region, the rest: start offset
     */
    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData, int srcOffset) {
        int region = (dataStart >>> 28) & 0x03;
        boolean is8bit = ((dataStart >>> 31) & 0x01) != 0;
        dataStart &= 0x0fff_ffff;

        // in 16 bit words
        int words = is8bit ? romSize : romSize / 2;
        if (regions[region] == null || regions[region].length != words)
            regions[region] = new short[words];
        short[] rom = regions[region];

        if (is8bit) {
            if (dataStart > words)
                return;
            if (dataStart + dataLength > words)
                dataLength = words - dataStart;
            for (int i = 0; i < dataLength; i++)
                rom[dataStart + i] = (short) ((romData[srcOffset + i] & 0xff) << 8);
        } else {
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;
            // little endian words
            for (int i = 0; i < dataLength; i++) {
                int p = dataStart + i;
                int b = romData[srcOffset + i] & 0xff;
                if ((p & 1) == 0)
                    rom[p >> 1] = (short) ((rom[p >> 1] & 0xff00) | b);
                else
                    rom[p >> 1] = (short) ((rom[p >> 1] & 0x00ff) | (b << 8));
            }
        }
    }

    /** voice bank, used for the external address bank */
    public void setVoiceBank(int voice, long bank) {
        voices[voice].exBank = bank;
    }

    public void setMuteMask(int muteMask) {
        for (int v = 0; v < 32; v++)
            voices[v].muted = ((muteMask >> v) & 0x01) != 0;
    }

    //
    // for view
    //

    public static final int VOICES = 32;

    public int getActiveVoices() {
        return activeVoices + 1;
    }

    public boolean isEnabled(int v) {
        return v <= activeVoices && (voices[v].control & CONTROL_STOPMASK) == 0;
    }

    /** the frequency count register, fraction bits included */
    public int getFrequency(int v) {
        return (int) getAddressAccRes(voices[v].freqCount, 1);
    }

    public int getVolumeL(int v) {
        return voices[v].lVol;
    }

    public int getVolumeR(int v) {
        return voices[v].rVol;
    }

    public int getOutput(int v) {
        return getCa(voices[v].control);
    }

    public boolean isMuted(int v) {
        return voices[v].muted;
    }
}
