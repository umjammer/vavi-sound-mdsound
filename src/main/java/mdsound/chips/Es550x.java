/*
 * license:BSD-3-Clause
 *
 * copyright-holders: Aaron Giles
 */

package mdsound.chips;

import java.util.ArrayList;
import java.util.List;


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

    protected int clock() {
        return 0;
    }

    public <T> void set_region0(T tag) {
        //m_region0.set_tag(tag);
    }

    public <T> void set_region1(T tag) {
        //m_region1.set_tag(tag);
    }

    public <T> void set_region2(T tag) {
        //m_region2.set_tag(tag);
    }

    public <T> void set_region3(T tag) {
        //m_region3.set_tag(tag);
    }

    public void set_channels(int channels) {
        m_channels = channels;
    }

    public int get_voice_index() {
        return m_voice_index;
    }

    public Object irq_cb() {
        //return m_irq_cb.bind();
        return null;
    }

    public Object read_port_cb() {
        //return m_read_port_cb.bind();
        return null;
    }

    public Object sample_rate_changed() {
        //return m_sample_rate_changed_cb.bind();
        return null;
    }

    protected static final int LP3 = 1;
    protected static final int LP4 = 2;
    protected static final int LP_MASK = LP3 | LP4;
    /** constants for volumes */
    protected static final int VOLUME_ACC_BIT = 20;
    /** constants for address */
    protected static final int ADDRESS_FRAC_BIT = 11;

    /** struct describing a single playing voice */
    protected static class es550x_voice {

        // external state
        /** control register */
        protected int control = 0;
        /** frequency count register */
        protected long freqCount = 0;
        /** start register */
        protected long start = 0;
        /** left volume register */
        protected int lvol = 0;
        /** end register */
        protected long end = 0;
        /** left volume ramp register */
        protected int lvRamp = 0;
        /** accumulator register */
        protected long accum = 0;
        /** right volume register */
        protected int rVol = 0;
        /** right volume ramp register */
        protected int rvRamp = 0;
        /** envelope count register */
        protected int eCount = 0;
        /** k2 register */
        protected int k2 = 0;
        /** k2 ramp register */
        protected int k2Ramp = 0;
        /** k1 register */
        protected int k1 = 0;
        /** k1 ramp register */
        protected int k1Ramp = 0;
        /** filter storage O4(n-1) */
        protected int o4n1 = 0;
        /** filter storage O3(n-1) */
        protected int o3n1 = 0;
        /** filter storage O3(n-2) */
        protected int o3n2 = 0;
        /** filter storage O2(n-1) */
        protected int o2n1 = 0;
        /** filter storage O2(n-2) */
        protected int o2n2 = 0;
        /** filter storage O1(n-1) */
        protected int o1n1 = 0;
        /** external address bank */
        protected long exBank = 0;
        // internal state
        /** index of this voice */
        protected byte index = 0;
        /** filter count */
        protected byte filtCount = 0;
    }

    //virtual inline u32 get_bank(u32 control) { return 0; }
    //virtual inline u32 get_ca(u32 control) { return 0; }
    //virtual inline u32 get_lp(u32 control) { return 0; }

    private static long lShift_signed(long val, int shift) {
        return (shift >= 0) ? val << shift : val >> (-shift);
    }
//    private int rshift_signed(int val, int shift) { return (shift >= 0) ? val >> shift : val << (-shift); }
//    private int rshift_signed(long val, int shift) { return 0; }// (shift >= 0) ? val >> shift : val << (-shift); }
    private static long rShift_signed(long val, int shift) {
        return (shift >= 0) ? val >> shift : val << (-shift);
    }

    protected long get_volume(int volume) {
        return m_volume_lookup.get((int) rShift_signed(volume, m_volume_shift));
    }

    protected long get_address_acc_shifted_val(long val, int bias /* = 0 */) {
        return lShift_signed(val, m_address_acc_shift - bias);
    }

    protected long get_address_acc_res(long val, int bias /* = 0 */) {
        return rShift_signed(val, m_address_acc_shift - bias);
    }

    protected long get_integer_addr(long accum, int bias /* = 0 */) {
        return ((accum + ((long) bias << ADDRESS_FRAC_BIT)) & m_address_acc_mask) >> ADDRESS_FRAC_BIT;
    }

    protected long get_sample(int sample, int volume) {
        return rShift_signed((long) sample * get_volume(volume), (int) m_volume_acc_shift);
    }

    protected abstract void update_envelopes(/* ref */ es550x_voice[] voice);

    protected abstract void check_for_end_forward(/* ref */ es550x_voice[] voice, long accum);

    protected abstract void check_for_end_reverse(/* ref */ es550x_voice[] voice, long accum);

    protected abstract void generate_samples(int[][] outputs);

    //       inline void update_index(es550x_voice* voice) { m_voice_index = voice->index; }
    protected short read_sample(/* ref */ es550x_voice[] voice, int addr) {
        return 0;
    }

    //        internal state
    //       sound_stream* m_stream;               // which stream are we using
    /** current sample rate */
    protected int m_sample_rate;
    /** master clock frequency */
    protected int m_master_clock;
    /** right shift accumulator for generate integer address */
    private int m_address_acc_shift;
    /** accumulator mask */
    protected long m_address_acc_mask;
    /** right shift volume for generate integer volume */
    private int m_volume_shift;
    /** right shift output for output normalizing */
    private long m_volume_acc_shift;
    /** current register page */
    protected int m_current_page;
    /** number of active voices */
    protected int m_active_voices;
    /** MODE register */
    protected short m_mode;
    /** IRQV register */
    protected int m_irqv;
    /** current voice index value */
    private int m_voice_index;

    /** the 32 voices */
    protected final es550x_voice[] m_voice = new es550x_voice[32];

    private List<Short> m_ulaw_lookup;
    private List<Integer> m_volume_lookup;

//    optional_memory_region m_region0; // memory region where the sample ROM lives
//    optional_memory_region m_region1; // memory region where the sample ROM lives
//    optional_memory_region m_region2; // memory region where the sample ROM lives
//    optional_memory_region m_region3; // memory region where the sample ROM lives
    protected int m_channels; // number of output channels: 1 .. 6
//    devcb_write_line m_irq_cb; // irq callback
//    devcb_read16 m_read_port_cb; // input port read
//    devcb_write32 m_sample_rate_changed_cb; // callback for when sample rate is changed

    //
    // CONSTANTS
    //

    private static final int LOG_SERIAL = (1 << 1);
    private static final int VERBOSE = 0;

    private static final int RAINE_CHECK = 0;

    private static final int FINE_FILTER_BIT = 16;
    private static final int FILTER_BIT = 12;
    private static final int FILTER_SHIFT = FINE_FILTER_BIT - FILTER_BIT;
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
    protected static final int CONTROL_BSMASK = (CONTROL_BS1 | CONTROL_BS0);
    protected static final int CONTROL_CAMASK = (CONTROL_CA2 | CONTROL_CA1 | CONTROL_CA0);
    protected static final int CONTROL_LPMASK = (CONTROL_LP4 | CONTROL_LP3);
    protected static final int CONTROL_LOOPMASK = (CONTROL_BLE | CONTROL_LPE);
    protected static final int CONTROL_STOPMASK = (CONTROL_STOP1 | CONTROL_STOP0);
    // ES5505 has sightly different control bit
    protected static final int CONTROL_5505_LP4 = 0x0800;
    protected static final int CONTROL_5505_LP3 = 0x0400;
    protected static final int CONTROL_5505_CA1 = 0x0200;
    protected static final int CONTROL_5505_CA0 = 0x0100;
    protected static final int CONTROL_5505_LPMASK = (CONTROL_5505_LP4 | CONTROL_5505_LP3);
    protected static final int CONTROL_5505_CAMASK = (CONTROL_5505_CA1 | CONTROL_5505_CA0);

    public void es550x_device() {
    }

    /**
     * device-specific startup
     */
    public void device_start() {
        // initialize the rest of the structure
        m_master_clock = clock();
        m_irqv = 0x80;
    }

    /**
     * device_clock_changed
     */
    public void device_clock_changed() {
        m_master_clock = clock();
        m_sample_rate = m_master_clock / (16 * (m_active_voices + 1));
    }

    /**
     * device-specific reset
     */
    public void device_reset() {
    }

    /**
     * device-specific stop
     */
    public void device_stop() {
//#if ES5506_MAKE_WAVS
//	{
//		wav_close(m_wavraw);
//	}
//#endif
    }

    /**
     * update the IRQ state
     */
    public void update_irq_state() {
        // ES5505/6 irq line has been set high - inform the host
        //m_irq_cb(1); // IRQB set high
    }

    public void update_internal_irq_state() {
        // Host (cpu) has just read the voice interrupt vector (voice IRQ ack).
        //
        // Reset the voice vector to show the IRQB line is low (top bit set).
        // If we have any stacked interrupts (other voices waiting to be
        // processed - with their IRQ bit set) then they will be moved into
        // the vector next time the voice is processed.  In emulation
        // terms they get updated next time generate_samples() is called.

        m_irqv = 0x80;
        //m_irq_cb(0); // IRQB set low
    }

    /**
     * compute static tables
     */
    public void compute_tables(int total_volume_bit, int exponent_bit, int mantissa_bit) {
        // allocate ulaw lookup table
        m_ulaw_lookup = new ArrayList<>();
        for (int i = 0; i < (1 << ULAW_MAXBITS); i++)
            m_ulaw_lookup.add((short) 0);

        // generate ulaw lookup table
        for (int i = 0; i < (1 << ULAW_MAXBITS); i++) {
            short rawval = (short) ((i << (16 - ULAW_MAXBITS)) | (1 << (15 - ULAW_MAXBITS)));
            byte exponent = (byte) (rawval >> 13);
            int mantissa = (rawval << 3) & 0xffff;

            if (exponent == 0)
                m_ulaw_lookup.set(i, (short) (mantissa >> 7));
            else {
                mantissa = (mantissa >> 1) | (~mantissa & 0x8000);
                m_ulaw_lookup.set(i, (short) (mantissa >> (7 - exponent)));
            }
        }

        int volume_bit = (exponent_bit + mantissa_bit);
        m_volume_shift = total_volume_bit - volume_bit;
        int volume_len = 1 << volume_bit;
        // allocate volume lookup table
        m_volume_lookup = new ArrayList<>();
        for (int i = 0; i < volume_len; i++)
            m_volume_lookup.add(0);

        // generate volume lookup table
        int exponent_shift = 1 << exponent_bit;
        int exponent_mask = exponent_shift - 1;

        int mantissa_len = 1 << mantissa_bit;
        int mantissa_mask = (mantissa_len - 1);
        int mantissa_shift = exponent_shift - mantissa_bit - 1;

        for (int i = 0; i < volume_len; i++) {
            int exponent = (i >> mantissa_bit) & exponent_mask;
            int mantissa = (i & mantissa_mask) | mantissa_len;

            m_volume_lookup.set(i, (mantissa << mantissa_shift) >> (exponent_shift - exponent));
        }
        m_volume_acc_shift = (16 + exponent_mask) - VOLUME_ACC_BIT;

        // init the voices
        for (int j = 0; j < 32; j++) {
            m_voice[j].index = (byte) j;
            m_voice[j].control = CONTROL_STOPMASK;
            m_voice[j].lvol = 1 << (total_volume_bit - 1);
            m_voice[j].rVol = 1 << (total_volume_bit - 1);
        }
    }

    /**
     * get address accumulator mask
     */
    public void get_accum_mask(int address_integer, int address_frac) {
        m_address_acc_shift = ADDRESS_FRAC_BIT - address_frac;
        m_address_acc_mask = lShift_signed(
                (((1L << address_integer) - 1) << address_frac) | ((1L << address_frac) - 1),
                m_address_acc_shift);
        if (m_address_acc_shift > 0)
            m_address_acc_mask = m_address_acc_mask | ((1L << m_address_acc_shift) - 1);
    }

    /**
     * interpolate between two samples
     */
    private int interpolate(int sample1, int sample2, long accum) {
        int shifted = 1 << ADDRESS_FRAC_BIT;
        int mask = shifted - 1;
        accum &= mask & m_address_acc_mask;
        return (sample1 * (int) (shifted - accum) +
                sample2 * (int) (accum)) >> ADDRESS_FRAC_BIT;
    }

    /**
     * apply the 4-pole digital filter to the sample
     */
    // apply lowpass/highpass result
    private static int apply_lowpass(int _out, int cutoff, int _in) {
        return ((cutoff >> FILTER_SHIFT) * (_out - _in) / (1 << FILTER_BIT)) + _in;
    }

    private static int apply_highpass(int _out, int cutoff, int _in, int prev) {
        return _out - prev + ((cutoff >> FILTER_SHIFT) * _in) / (1 << (FILTER_BIT + 1)) + _in / 2;
    }

    // update poles from outputs
    private static void update_pole(/* ref */ int[] pole, int sample) {
        pole[0] = sample;
    }

    private static void update_2_pole(/* ref */ int[] prev, /* ref */ int[] pole, int sample) {
        prev[0] = pole[0];
        pole[0] = sample;
    }

    private static void apply_filters(/* ref */ es550x_voice[] voice, /* ref */ int[] sample) {
        // pole 1 is always low-pass using K1
        sample[0] = apply_lowpass(sample[0], voice[0].k1, voice[0].o1n1);
        int[] tmp = {voice[0].o1n1};
        update_pole(/* ref */ tmp, sample[0]);
        voice[0].o1n1 = tmp[0];

        // pole 2 is always low-pass using K1
        sample[0] = apply_lowpass(sample[0], voice[0].k1, voice[0].o2n1);
        tmp[0] = voice[0].o2n2; int[] tmp2 = {voice[0].o2n1};
        update_2_pole(/* ref */ tmp, /* ref */ tmp2, sample[0]);
        voice[0].o2n2 = tmp[0]; voice[0].o2n1 = tmp2[0];

        // remaining poles depend on the current filter setting
        switch (voice[0].control) { //(get_lp(voice.control))
            case 0:
                // pole 3 is high-pass using K2
                sample[0] = apply_highpass(sample[0], voice[0].k2, voice[0].o3n1, voice[0].o2n2);
                tmp[0] = voice[0].o3n2; tmp2[0] = voice[0].o3n1;
                update_2_pole(/* ref */ tmp, /* ref */ tmp2, sample[0]);
                voice[0].o3n2 = tmp[0]; voice[0].o3n1 = tmp2[0];
                // pole 4 is high-pass using K2
                sample[0] = apply_highpass(sample[0], voice[0].k2, voice[0].o4n1, voice[0].o3n2);
                tmp[0] = voice[0].o4n1;
                update_pole(/* ref */ tmp, sample[0]);
                voice[0].o4n1 = tmp[0];
                break;

            case LP3:
                // pole 3 is low-pass using K1
                sample[0] = apply_lowpass(sample[0], voice[0].k1, voice[0].o3n1);
                tmp[0] = voice[0].o3n2; tmp2[0] = voice[0].o3n1;
                update_2_pole(/* ref */ tmp, /* ref */ tmp2, sample[0]);
                voice[0].o3n2 = tmp[0]; voice[0].o3n1 = tmp2[0];
                // pole 4 is high-pass using K2
                sample[0] = apply_highpass(sample[0], voice[0].k2, voice[0].o4n1, voice[0].o3n2);
                tmp[0] = voice[0].o4n1;
                update_pole(/* ref */ tmp, sample[0]);
                voice[0].o4n1 = tmp[0];
                break;

            case LP4:
                // pole 3 is low-pass using K2
                sample[0] = apply_lowpass(sample[0], voice[0].k2, voice[0].o3n1);
                tmp[0] = voice[0].o3n2; tmp2[0] = voice[0].o3n1;
                update_2_pole(/* ref */ tmp, /* ref */ tmp2, sample[0]);
                voice[0].o3n2 = tmp[0]; voice[0].o3n1 = tmp2[0];
                // pole 4 is low-pass using K2
                sample[0] = apply_lowpass(sample[0], voice[0].k2, voice[0].o4n1);
                tmp[0] = voice[0].o4n1;
                update_pole(/* ref */ tmp, sample[0]);
                voice[0].o4n1 = tmp[0];
                break;

            case LP3 | LP4:
                // pole 3 is low-pass using K1
                sample[0] = apply_lowpass(sample[0], voice[0].k1, voice[0].o3n1);
                tmp[0] = voice[0].o3n2; tmp2[0] = voice[0].o3n1;
                update_2_pole(/* ref */ tmp, /* ref */tmp2, sample[0]);
                voice[0].o3n2 = tmp[0]; voice[0].o3n1 = tmp2[0];
                // pole 4 is low-pass using K2
                sample[0] = apply_lowpass(sample[0], voice[0].k2, voice[0].o4n1);
                tmp[0] = voice[0].o4n1;
                update_pole(/* ref */ tmp, sample[0]);
                voice[0].o4n1 = tmp[0];
                break;
        }
    }

    /**
     * generate_ulaw -- general u-law decoding routine
     */
    private void generate_ulaw(/* ref */ es550x_voice[] voice, int[] dest) {
        int freqCount = (int) voice[0].freqCount;
        long accum = voice[0].accum & m_address_acc_mask;

        // outer loop, in case we switch directions
        if ((voice[0].control & CONTROL_STOPMASK) == 0) {
            // two cases: first case is forward direction
            if ((voice[0].control & CONTROL_DIR) == 0) {
                // fetch two samples
                int val1 = read_sample(/* ref */ voice, (int) get_integer_addr(accum, 0));
                int val2 = read_sample(/* ref */ voice, (int) get_integer_addr(accum, 1));

                // decompress u-law
                val1 = m_ulaw_lookup.get(val1 >> (16 - ULAW_MAXBITS));
                val2 = m_ulaw_lookup.get(val2 >> (16 - ULAW_MAXBITS));

                // interpolate
                val1 = interpolate(val1, val2, accum);
                accum = (accum + freqCount) & m_address_acc_mask;

                // apply filters
                int[] tmp = {val1};
                apply_filters(/* ref */ voice, /* ref */ tmp);
                val1 = tmp[0];

                // update filters/volumes
                if (voice[0].eCount != 0)
                    update_envelopes(/* ref */ voice);

                // apply volumes and add
                dest[0] += (int) get_sample(val1, voice[0].lvol);
                dest[1] += (int) get_sample(val1, voice[0].rVol);

                // check for loop end
                check_for_end_forward(/* ref */ voice, accum);
            }

            // two cases: second case is backward direction
            else {
                // fetch two samples
                int val1 = read_sample(/* ref */ voice, (int) get_integer_addr(accum, 0));
                int val2 = read_sample(/* ref */ voice, (int) get_integer_addr(accum, 1));

                // decompress u-law
                val1 = m_ulaw_lookup.get(val1 >> (16 - ULAW_MAXBITS));
                val2 = m_ulaw_lookup.get(val2 >> (16 - ULAW_MAXBITS));

                // interpolate
                val1 = interpolate(val1, val2, accum);
                accum = (accum - freqCount) & m_address_acc_mask;

                // apply filters
                int[] tmp = {val1};
                apply_filters(/* ref */ voice, /* ref */ tmp);
                val1 = tmp[0];

                // update filters/volumes
                if (voice[0].eCount != 0)
                    update_envelopes(/* ref */ voice);

                // apply volumes and add
                dest[0] += (int) get_sample(val1, voice[0].lvol);
                dest[1] += (int) get_sample(val1, voice[0].rVol);

                // check for loop end
                check_for_end_reverse(/* ref */ voice, accum);
            }
        } else {
            // if we stopped, process any additional envelope
            if (voice[0].eCount != 0)
                update_envelopes(/* ref */ voice);
        }

        voice[0].accum = accum;
    }

    /**
     * general PCM decoding routine
     */
    protected void generate_pcm(/* ref */ Es550x.es550x_voice[] voice, int[] dest) {
        int freqCount = (int) voice[0].freqCount;
        long accum = voice[0].accum & m_address_acc_mask;

        // outer loop, in case we switch directions
        if ((voice[0].control & CONTROL_STOPMASK) == 0) {
            // two cases: first case is forward direction
            if ((voice[0].control & CONTROL_DIR) == 0) {
                // fetch two samples
                int val1 = read_sample(/* ref */ voice, (int) get_integer_addr(accum, 0));
                int val2 = read_sample(/* ref */ voice, (int) get_integer_addr(accum, 1));

                // interpolate
                val1 = interpolate(val1, val2, accum);
                accum = (accum + freqCount) & m_address_acc_mask;

                // apply filters
                int[] tmp = {val1};
                apply_filters(/* ref */ voice, /* ref */ tmp);
                val1 = tmp[0];

                // update filters/volumes
                if (voice[0].eCount != 0)
                    update_envelopes(/* ref */ voice);

                // apply volumes and add
                dest[0] += (int) get_sample(val1, voice[0].lvol);
                dest[1] += (int) get_sample(val1, voice[0].rVol);

                // check for loop end
                check_for_end_forward(/* ref */ voice, accum);
            }

            // two cases: second case is backward direction
            else {
                // fetch two samples
                int val1 = read_sample(/* ref */ voice, (int) get_integer_addr(accum, 0));
                int val2 = read_sample(/* ref */ voice, (int) get_integer_addr(accum, 1));

                // interpolate
                val1 = interpolate(val1, val2, accum);
                accum = (accum - freqCount) & m_address_acc_mask;

                // apply filters
                int[] tmp = {val1};
                apply_filters(/* ref */ voice, /* ref */ tmp);
                val1 = tmp[0];

                // update filters/volumes
                if (voice[0].eCount != 0)
                    update_envelopes(/* ref */ voice);

                // apply volumes and add
                dest[0] += (int) get_sample(val1, voice[0].lvol);
                dest[1] += (int) get_sample(val1, voice[0].rVol);

                // check for loop end
                check_for_end_reverse(/* ref */ voice, accum);
            }
        } else {
            // if we stopped, process any additional envelope
            if (voice[0].eCount != 0)
                update_envelopes(/* ref */ voice);
        }

        voice[0].accum = accum;
    }

    /**
     * general interrupt handling routine
     */
    protected void generate_irq(/* ref */ es550x_voice[] voice, int v) {
        // does this voice have it's IRQ bit raised?
        if ((voice[0].control & CONTROL_IRQ) != 0) {
            //LOG("es5506: IRQ raised on voice %d!!\n", v);

            // only update voice vector if existing IRQ is acked by host
            if ((m_irqv & 0x80) != 0) {
                // latch voice number into vector, and set high bit low
                m_irqv = (byte) (v & 0x1f);

                // take down IRQ bit on voice
                voice[0].control &= ~CONTROL_IRQ;

                // inform host of irq
                update_irq_state();
            }
        }
    }

    /**
     * sound_stream_update - handle a stream update
     */
    private void sound_stream_update(int[][] outputs) {
        // loop until all samples are output
        generate_samples(outputs);
    }
}
