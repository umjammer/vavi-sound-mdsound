/*
 * license:BSD-3-Clause
 *
 * copyright-holders: Olivier Galibert, R. Belmont, hap, superctr
 */

package mdsound.chips;


/**
 * ZOOM ZSG-2 custom wave-table synthesizer
 * <p>
 * Written by Olivier Galibert
 * MAME conversion by R. Belmont
 * Working emulation by The Talentuous Hands Of The Popularious hap
 * Properly working emulation by superctr
 * ---
 * <p>
 * Register map:
 * 000-5fe : Channel specific registers (48 channels)
 * (high)   (low)
 * +000 : xxxxxxxx -------- : Start address (low)
 * +000 : -------- xxxxxxxx :   Unknown register (usually cleared)
 * +002 : xxxxxxxx -------- : Address page
 * : -------- xxxxxxxx : Start address (high)
 * +004 : -------- -------- :   Unknown register (usually cleared)
 * +006 : -----x-- -------- :   Unknown bit, always set
 * +008 : xxxxxxxx xxxxxxxx : Frequency
 * +00a : xxxxxxxx -------- : DSP ch 3 (right) output gain
 * : -------- xxxxxxxx : Loop address (low)
 * +00c : xxxxxxxx xxxxxxxx : End address
 * +00e : xxxxxxxx -------- : DSP ch 2 (Left) output gain
 * : -------- xxxxxxxx : Loop address (high)
 * +010 : xxxxxxxx xxxxxxxx : Initial filter time constant
 * +012 : xxxxxxxx xxxxxxxx : Current filter time constant
 * +014 : xxxxxxxx xxxxxxxx : Initial volume
 * +016 : xxxxxxxx xxxxxxxx : Current volume?
 * +018 : xxxxxxxx xxxxxxxx : Target filter time constant
 * +01a : xxxxxxxx -------- : DSP ch 1 (chorus) output gain
 * : -------- xxxxxxxx : Filter ramping speed
 * +01c : xxxxxxxx xxxxxxxx : Target volume
 * +01e : xxxxxxxx -------- : DSP ch 0 (reverb) output gain
 * : -------- xxxxxxxx : Filter ramping speed
 * 600-604 : Key on flags (each bit corresponds to a channel)
 * 608-60c : Key off flags (each bit corresponds to a channel)
 * 618     : Unknown register (usually 0x5cbc is written)
 * 61a     : Unknown register (usually 0x5cbc is written)
 * 620     : Unknown register (usually 0x0128 is written)
 * 628     : Unknown register (usually 0x0066 is written)
 * 630     : Unknown register (usually 0x0001 is written)
 * 638     : ROM readback address low
 * 63a     : ROM readback address high
 * 63c     : ROM readback word low
 * 63e     : ROM readback word high
 * <p>
 * ---
 * <p>
 * Additional notes on the sample format, reverse-engineered
 * by Olivier Galibert and David Haywood:
 * <p>
 * The zoom sample rom is decomposed in 0x40000 bytes pages.  Each page
 * starts by a header and is followed by compressed samples.
 * <p>
 * The header is a vector of 16 bytes structures composed of 4 32bits
 * little-endian values representing:
 * - sample start position in bytes, always a multiple of 4
 * - sample end position in bytes, minus 4, always...
 * - loop position in bytes, always....
 * - flags, probably
 * <p>
 * It is interesting to note that this header is *not* parsed by the
 * ZSG.  The main program reads the rom through appropriate ZSG
 * commands, and use the results in subsequent register setups.  It's
 * not even obvious that the ZSG cares about the pages, it may just
 * see the address space as linear.  In the same line, the
 * interpretation of the flags is obviously dependent on the main
 * program, not the ZSG, but some of the bits are directly copied to
 * some of the registers.
 * <p>
 * The samples are compressed with a 2:1 ratio.  Each block of 4-bytes
 * becomes 4 16-bits samples.  Reading the 4 bytes as a *little-endian*
 * 32bits values, the structure is:
 * <p>
 * 42222222 51111111 60000000 ssss3333
 * <p>
 * 's' is a 4-bit scale value.  '0000000', '1111111', '2222222' and
 * '6543333' are signed 7-bits values corresponding to the 4 samples.
 * To compute the final 16bits value, left-align and shift right by s.
 * Yes, that simple.
 * <p>
 * ---
 * <p>
 * TODO
 * - Filter and ramping behavior might not be perfect.
 * - clicking / popping noises in gdarius, raystorm: maybe the sample ROMs are bad dumps?
 * - memory reads out of range sometimes
 *
 * @author Olivier Galibert
 * @author R. Belmont
 * @author hap
 * @author superctr
 */
public class Zsg2 {

    private static final int STATUS_ACTIVE = 0x8000;

    // 16 registers per channel, 48 channels
    private static class Channel {

        private int[] v = new int[16];
        private int status;
        private int cur_pos;
        private int step_ptr;
        private int step;
        private int start_pos;
        private int end_pos;
        private int loop_pos;
        private int page;

        private int vol;
        private int vol_initial;
        private int vol_target;
        private int vol_delta;

        private int output_cutoff;
        private int output_cutoff_initial;
        private int output_cutoff_target;
        private int output_cutoff_delta;

        private int emphasis_filter_state;

        private int output_filter_state;

        // Attenuation for output channels
        private int[] output_gain = new int[4];

        private int[] samples = new int[5]; // +1 history
    }

    private int[] m_gain_tab;
    private final int[] m_reg = new int[32];

    private Channel[] m_chan;
    private int m_sample_count;

    private int[] m_mem_base;
    private int m_read_address;
    private int[] m_mem_copy;
    private int m_mem_blocks;
    private short[] m_full_samples;

    private static final int EMPHASIS_INITIAL_BIAS = 0;
    // Adjusts the cutoff constant of the filter by right-shifting the filter state.
    // The current value gives a -6dB cutoff frequency at about 81.5 Hz, assuming
    // sample playback at 32.552 kHz.
    private static final int EMPHASIS_FILTER_SHIFT = (16 - 10);
    private static final int EMPHASIS_ROUNDING = 0x20;
    // Adjusts the output amplitude by right-shifting the filtered output. Should be
    // kept relative to the filter shift. A too low value will cause clipping, while
    // too high will cause quantization noise.
    private static final int EMPHASIS_OUTPUT_SHIFT = 1;

    // device type definition
    // DEFINE_DEVICE_TYPE(ZSG2, zsg2_device, "zsg2", "ZOOM ZSG-2")

    /**
     * device-specific startup
     */
    public void start() {
        m_chan = new Channel[48];

        //m_stream = stream_alloc(0, 4, clock() / 768);
        m_mem_blocks = m_mem_base.length;
        m_mem_copy = new int[m_mem_blocks];
        m_full_samples = new short[m_mem_blocks * 4 + 4]; // +4 is for empty block

        // register for savestates
        //save_pointer(NAME(m_mem_copy), m_mem_blocks / sizeof(int32_t));
        //save_pointer(NAME(m_full_samples), (m_mem_blocks * 4 + 4) / sizeof(int16_t));
        //save_item(NAME(m_read_address));

        // Generate the output gain table. Assuming -1dB per step for now.
        for (int i = 1; i < 32; i++) {
            double val = Math.pow(10, -(31 - i) / 20.0) * 65535.0;
            m_gain_tab[i] = (short) val;
        }
        m_gain_tab[0] = 0;
    }

    /**
     * device-specific reset
     */
    public void device_reset() {
        m_read_address = 0;

        // stop playing and clear all channels
        control_w(4, 0xffff);
        control_w(5, 0xffff);
        control_w(6, 0xffff);

        for (int ch = 0; ch < 48; ch++)
            for (int reg = 0; reg < 0x10; reg++)
                chan_w(ch, reg, 0);
        m_sample_count = 0;

//#if DEBUG
//        for (int i = 0; i < m_mem_blocks; i++)
//            prepare_samples(i);
//
//        FILE * f;
//
//        f = fopen("zoom_samples.bin", "wb");
//        fwrite(m_mem_copy.get(), 1, m_mem_blocks * 4, f);
//        fclose(f);
//
//        f = fopen("zoom_samples.raw", "wb");
//        fwrite(m_full_samples.get(), 2, m_mem_blocks * 4, f);
//        fclose(f);
//#endif
    }

    /** */
    public int read_memory(int offset) {
        if (offset >= m_mem_blocks)
            return 0;

        //if (m_ext_read_handler.isunset())
        return m_mem_base[offset];

        //return m_ext_read_handler(offset);
    }

    private short[] prepare_samples(int offset, /* out */ int[] ptr) {
        int block = read_memory(offset);

        if (block == 0) {
            ptr[0] = m_mem_blocks;
            return m_full_samples; // overflow or 0
        }

        if (block == m_mem_copy[offset]) {
            ptr[0] = offset * 4;
            return m_full_samples; // cached
        }

        m_mem_copy[offset] = block;
        offset *= 4;

        // decompress 32 bit block to 4 16-bit samples
        // 42222222 51111111 60000000 ssss3333
        m_full_samples[offset | 0] = (short) (block >> 8 & 0x7f);
        m_full_samples[offset | 1] = (short) (block >> 16 & 0x7f);
        m_full_samples[offset | 2] = (short) (block >> 24 & 0x7f);
        m_full_samples[offset | 3] = (short) ((block >> (8 + 1) & 0x40) | (block >> (16 + 2) & 0x20) | (block >> (24 + 3) & 0x10) | (block & 0xf));

        // sign-extend and shift
        byte shift = (byte) (block >> 4 & 0xf);
        for (int i = offset; i < (offset + 4); i++) {
            m_full_samples[i] <<= 9;
            m_full_samples[i] >>= shift;
        }

        ptr[0] = offset;
        return m_full_samples;
    }

    /** Fill the buffer with filtered samples */
    private void filter_samples(/* ref */ Channel[] ch) {
        int[] ptr = new int[1];
        short[] raw_samples = prepare_samples(ch[0].page | ch[0].cur_pos, /* out */ ptr);
        ch[0].samples[0] = ch[0].samples[4]; // we want to remember the last sample

        for (int i = 0; i < 4; i++) {
            ch[0].emphasis_filter_state += raw_samples[ptr[0] + i] - ((ch[0].emphasis_filter_state + EMPHASIS_ROUNDING) >> EMPHASIS_FILTER_SHIFT);

            int sample = ch[0].emphasis_filter_state >> EMPHASIS_OUTPUT_SHIFT;
            ch[0].samples[i + 1] = (short) Math.min(Math.max(sample, -32768), 32767);
        }
    }

    /**
     * handle a stream update
     */
    public void update(int[][] outputs, int samples) {
        for (int i = 0; i < samples; i++) {
            int[] mix = new int[4];

            // loop over all channels
            for (int j = 0; j < m_chan.length; j++) {
                Channel elem = m_chan[j];
                if ((~elem.status & STATUS_ACTIVE) != 0)
                    continue;

                elem.step_ptr += elem.step;
                if ((elem.step_ptr & 0xffff_0000) != 0) {
                    if (++elem.cur_pos >= elem.end_pos) {
                        // loop sample
                        elem.cur_pos = elem.loop_pos;
                        if ((elem.cur_pos + 1) >= elem.end_pos) {
                            // end of sample
                            elem.vol = 0; //this should help the channel allocation just a bit
                            elem.status &= ~STATUS_ACTIVE;
                            continue;
                        }
                    }

                    if (elem.cur_pos == elem.start_pos)
                        elem.emphasis_filter_state = EMPHASIS_INITIAL_BIAS;

                    elem.step_ptr &= 0xffff;
                    Channel[] tmp = new Channel[] {elem};
                    filter_samples(/* ref */ tmp);
                    elem = tmp[0];
                }

                int sample_pos = elem.step_ptr >> 14 & 3;
                int sample = elem.samples[sample_pos];

                // linear interpolation (hardware certainly does something similar)
                sample += ((short) (elem.step_ptr << 2 & 0xffff) * (short) (elem.samples[sample_pos + 1] - sample)) >> 16;

                // another filter...
                elem.output_filter_state += (sample - (elem.output_filter_state >> 16)) * elem.output_cutoff;
                sample = elem.output_filter_state >> 16;

                // To prevent DC bias, we need to slowly discharge the filter when the output filter cutoff is 0
                if (elem.output_cutoff == 0)
                    elem.output_filter_state >>= 1;

                sample = (sample * elem.vol) >> 16;

                for (int output = 0; output < 4; output++) {
                    int output_gain = elem.output_gain[output] & 0x1f; // left / right
                    int output_sample = sample;

                    if ((elem.output_gain[output] & 0x80) != 0) // perhaps ?
                        output_sample = -output_sample;

                    mix[output] += (output_sample * m_gain_tab[output_gain & 0x1f]) >> 16;
                }

                // Apply ramping every other update
                // It's possible key on is handled on the other sample
                if ((m_sample_count & 1) != 0) {
                    elem.vol = ramp(elem.vol, elem.vol_target, elem.vol_delta);
                    elem.output_cutoff = ramp(elem.output_cutoff, elem.output_cutoff_target, elem.output_cutoff_delta);
                }
            }

            for (int output = 0; output < 4; output++)
                outputs[output][i] = Math.min(Math.max(mix[output], -32768), 32767);
        }
        m_sample_count++;
    }

    /** */
    private void chan_w(int ch, int reg, int data) {
        switch (reg) {
            case 0x0:
                // lo byte: unknown, 0 on most games
                // hi byte: start address low
                m_chan[ch].start_pos = (m_chan[ch].start_pos & 0xff00) | (data >> 8 & 0xff);
                break;

            case 0x1:
                // lo byte: start address high
                // hi byte: address page
                m_chan[ch].start_pos = (m_chan[ch].start_pos & 0x00ff) | (data << 8 & 0xff00);
                m_chan[ch].page = data << 8 & 0xff0000;
                break;

            case 0x2:
                // no function? always 0
                break;

            case 0x3:
                // unknown, always 0x0400. is this a flag?
                m_chan[ch].status &= 0x8000;
                m_chan[ch].status |= data & 0x7fff;
                break;

            case 0x4:
                // frequency
                m_chan[ch].step = data + 1;
                break;

            case 0x5:
                // lo byte: loop address low
                // hi byte: right output gain (direct)
                m_chan[ch].loop_pos = (m_chan[ch].loop_pos & 0xff00) | (data & 0xff);
                m_chan[ch].output_gain[3] = (data & 0xff99) >> 8;
                break;

            case 0x6:
                // end address
                m_chan[ch].end_pos = data;
                break;

            case 0x7:
                // lo byte: loop address high
                // hi byte: left output gain (direct)
                m_chan[ch].loop_pos = (m_chan[ch].loop_pos & 0x00ff) | (data << 8 & 0xff00);
                m_chan[ch].output_gain[2] = (data & 0xff00) >> 8;
                break;

            case 0x8:
                // IIR lowpass time constant (initial, latched on key on)
                m_chan[ch].output_cutoff_initial = data;
                break;

            case 0x9:
                // writes 0 at key on
                m_chan[ch].output_cutoff = data;
                break;

            case 0xa:
                // volume (initial, latched on key on)
                m_chan[ch].vol_initial = data;
                break;

            case 0xb:
                // writes 0 at key on
                m_chan[ch].vol = data;
                break;

            case 0xc:
                // IIR lowpass time constant (target)
                m_chan[ch].output_cutoff_target = data;
                break;

            case 0xd:
                // hi byte: DSP channel 1 (chorus) gain
                // lo byte: Filter ramping speed
                m_chan[ch].output_gain[1] = (data & 0xff00) >> 8;
                m_chan[ch].output_cutoff_delta = get_ramp(data & 0xff);
                break;

            case 0xe:
                // volume target
                m_chan[ch].vol_target = data;
                break;

            case 0xf:
                // hi byte: DSP channel 0 (reverb) gain
                // lo byte: Volume ramping speed
                m_chan[ch].output_gain[0] = (data & 0xff00) >> 8;
                m_chan[ch].vol_delta = get_ramp(data & 0xff);
                break;

            default:
                break;
        }

        m_chan[ch].v[reg] = data;
    }

    private int chan_r(int ch, int reg) {
        switch (reg) {
            case 0x3:
                // no games read from this.
                return m_chan[ch].status;
            case 0x9:
                // pretty certain, though no games actually read from this.
                return m_chan[ch].output_cutoff;
            case 0xb: // Only later games (taitogn) read this register...
                // GNet games use some of the flags to decide which channels to kill when
                // all the channels are busy. (take raycris song #23 as an example)
                return m_chan[ch].vol;
            default:
                break;
        }

        return m_chan[ch].v[reg];
    }

    // Convert ramping register value to something more usable.
    // Upper 4 bits is a shift amount, lower 4 bits is a 2's complement value.
    // Get ramp amount by sign extending the low 4 bits, XOR by 8, then
    // shifting it by the upper 4 bits.
    // CPU uses a lookup table (stored in gdarius sound cpu ROM at 0x6332) to
    // calculate this value, for now I'm generating an opproximate inverse.
    private static int get_ramp(int val) {
        int frac = val << 12; // sign extend
        frac = (short) ((frac >> 12) ^ 8) << (val >> 4);
        return frac >> 4;
    }

    private static int ramp(int current, int target, int delta) {
        int rampval = current + delta;
        if (delta < 0 && rampval < target)
            rampval = target;
        else if (delta >= 0 && rampval > target)
            rampval = target;

        return rampval;
    }

    /** */
    private void control_w(int reg, int data) {
        switch (reg) {
            case 0x00:
            case 0x01:
            case 0x02: {
                // key on
                int _base = (reg & 3) << 4;
                for (int i = 0; i < 16; i++) {
                    if ((data & (1 << i)) != 0) {
                        int ch = _base | i;
                        m_chan[ch].status |= STATUS_ACTIVE;
                        m_chan[ch].cur_pos = m_chan[ch].start_pos - 1;
                        m_chan[ch].step_ptr = 0x10000;
                        // Ignoring the "initial volume" for now because it causes lots of clicking
                        m_chan[ch].vol = 0; // m_chan[ch].vol_initial;
                        m_chan[ch].vol_delta = 0x0400; // register 06 ?
                        m_chan[ch].output_cutoff = m_chan[ch].output_cutoff_initial;
                        m_chan[ch].output_filter_state = 0;
                    }
                }
                break;
            }

            case 0x04:
            case 0x05:
            case 0x06: {
                // key off
                int _base = (reg & 3) << 4;
                for (int i = 0; i < 16; i++) {
                    if ((data & (1 << i)) != 0) {
                        int ch = _base | i;
                        m_chan[ch].vol = 0;
                        m_chan[ch].status &= ~STATUS_ACTIVE;
                    }
                }
                break;
            }

//            case 0x0c: //These registers are sometimes written to by the CPU. Unknown purpose.
//                break;
//            case 0x0d:
//                break;
//            case 0x10:
//                break;
//
//            case 0x18:
//                break;

            case 0x1c:
                // rom readback address low (low 2 bits always 0)
                //if ((data & 3) != 0) popmessage("ZSG2 address %04X, contact MAMEdev", data);
                m_read_address = (m_read_address & 0x3fff_c000) | (data >> 2 & 0x0000_3fff);
                break;
            case 0x1d:
                // rom read back address high
                m_read_address = (m_read_address & 0x0000_3fff) | (data << 14 & 0x3fff_c000);
                break;

            default:
                if (reg < 0x20)
                    m_reg[reg] = data;
//logger.log(Level.ERROR, "ZSG2 control   %02X = %04X\n", reg, data & 0xffff);
                break;
        }
    }

    private int control_r(int reg) {
        switch (reg) {
            case 0x14:
                // memory bus busy?
                // right before reading memory, it polls until low 8 bits are 0
                return 0;

            case 0x1e:
                // rom read back word low
                return read_memory(m_read_address) & 0xffff;
            case 0x1f:
                // rom read back word high
                return (read_memory(m_read_address) & 0xffff_0000) >>> 16;

            default:
                if (reg < 0x20)
                    return m_reg[reg];
                break;
        }

        return 0;
    }

    /** */
    public void write(int offset, int data, int mem_mask) {
        // we only support full 16-bit accesses
        if (mem_mask != 0xffff) {
            //popmessage("ZSG2 write mask %04X, contact MAMEdev", mem_mask);
            return;
        }

        //m_stream.update();

        if (offset < 0x300) {
            int chan = offset >> 4;
            int reg = offset & 0xf;

            chan_w(chan, reg, data);
        } else {
            control_w(offset - 0x300, data);
        }
    }

    public int read(int offset, int mem_mask) {
        // we only support full 16-bit accesses
        if (mem_mask != 0xffff) {
            //popmessage("ZSG2 read mask %04X, contact MAMEdev", mem_mask);
            return 0;
        }

        if (offset < 0x300) {
            int chan = offset >> 4;
            int reg = offset & 0xf;

            return chan_r(chan, reg);
        } else {
            return control_r(offset - 0x300);
        }
    }
}
