package mdsound.chips;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Arrays;

import static java.lang.System.getLogger;


// upd7759.h
// There are two modes for the uPD7759, selected through the !MD pin.
// This is the mode select input.  High is stand alone, low is slave.
// We're making the assumption that nobody switches modes through
// software.

/**
 * NEC UPD7759 ADPCM Speech Processor
 * by: Juergen Buchmueller, Mike Balfour, Howie Cohen,
 * Olivier Galibert, and Aaron Giles
 *
 * ----
 *
 * Description:
 *
 * The UPD7759 is a speech processing LSI that utilizes ADPCM to produce
 * speech or other sampled sounds.  It can directly address up to 1Mbit
 * (128k) of external data ROM, or the host CPU can control the speech
 * data transfer.  The UPD7759 is usually hooked up to a 640 kHz clock and
 * has one 8-bit input port, a start pin, a busy pin, and a clock output.
 *
 * The chip is composed of 3 parts:
 * - a clock divider
 * - a rom-reading engine
 * - an adpcm engine
 * - a 4-to-9 bit adpcm converter
 *
 * The clock divider takes the base 640KHz clock and divides it first
 * by a fixed divisor of 4 and then by a value between 9 and 32.  The
 * result gives a clock between 5KHz and 17.78KHz.  It's probably
 * possible, but not recommended and certainly out-of-spec, to push the
 * chip harder by reducing the divider.
 *
 * The rom-reading engine reads one byte every two divided clock cycles.
 * The factor two comes from the fact that a byte has two nibbles, i.e.
 * two samples.
 *
 * The apdcm engine takes bytes and interprets them as commands:
 *
 * 00000000                    sample end
 * 00dddddd                    silence
 * 01ffffff                    send the 256 following nibbles to the converter
 * 10ffffff nnnnnnnn           send the n+1 following nibbles to the converter
 * 11---rrr --ffffff nnnnnnnn  send the n+1 following nibbles to the converter, and repeat r+1 times
 *
 * "ffffff" is sent to the clock divider to be the base clock for the
 * adpcm converter, i.e., it's the sampling rate.  If the number of
 * nibbles to send is odd the last nibble is ignored.  The commands
 * are always 8-bit aligned.
 *
 * "dddddd" is the duration of the silence.  The base speed is unknown,
 * 1ms sounds reasonably.  It does not seem linked to the adpcm clock
 * speed because there often is a silence before any 01 or 10 command.
 *
 * The adpcm converter converts nibbles into 9-bit DAC values.  It has
 * an internal state of 4 bits that's used in conjunction with the
 * nibble to lookup which of the 256 possible steps is used.  Then
 * the state is changed according to the nibble value.  Essentially, the
 * higher the state, the bigger the steps are, and using big steps
 * increase the state.  Conversely, using small steps reduces the state.
 * This allows the engine to be a little more adaptative than a
 * classical ADPCM algorithm.
 *
 * The UPD7759 can run in two modes, master (also known as standalone)
 * and slave.  The mode is selected through the "md" pin.  No known
 * game changes modes on the fly, and it's unsure if that's even
 * possible to do.
 *
 *
 * Master mode:
 *
 * The output of the rom reader is directly connected to the adpcm
 * converter.  The controlling cpu only sends a sample number and the
 * 7759 plays it.
 *
 * The sample rom has a header at the beginning of the form
 *
 * nn 5a a5 69 55
 *
 * where nn is the number of the last sample.  This is then followed by
 * a vector of 2-bytes msb-first values, one per sample.  Multiplying
 * them by two gives the sample start offset in the rom.  A 0x00 marks
 * the end of each sample.
 *
 * It seems that the UPD7759 reads at least part of the rom header at
 * startup.  Games doing rom banking are careful to reset the chip after
 * each change.
 *
 *
 * Slave mode:
 *
 * The rom reader is completely disconnected.  The input port is
 * connected directly to the adpcm engine.  The first write to the input
 * port activates the engine (the value itself is ignored).  The engine
 * activates the clock output and waits for commands.  The clock speed
 * is unknown, but its probably a divider of 640KHz.  We use 40KHz here
 * because 80KHz crashes altbeast.  The chip probably has an internal
 * fifo to the converter and suspends the clock when the fifo is full.
 * The first command is always 0xFF.  A second 0xFF marks the end of the
 * sample and the engine stops.  OTOH, there is a 0x00 at the end too.
 * Go figure.
 */
public class Upd7759 {

    private static final Logger logger = getLogger(Upd7759.class.getName());

    public static final int UPD7759_STANDARD_CLOCK = 640000;

    //
    // finalants
    //

    // step value fractional bits
    private static final int FRAC_BITS = 20;
    private static final int FRAC_ONE = (1 << FRAC_BITS);
    private static final int FRAC_MASK = (FRAC_ONE - 1);

    /** chip states */
    public enum STATE {
        IDLE,
        DROP_DRQ,
        START,
        FIRST_REQ,
        LAST_SAMPLE,
        DUMMY1,
        ADDR_MSB,
        ADDR_LSB,
        DUMMY2,
        BLOCK_HEADER,
        NIBBLE_COUNT,
        NIBBLE_MSN,
        NIBBLE_LSN
    }

    //
    // Type definitions
    //

    //running_device device;
//    /** stream channel for playback */
//    sound_stream channel;

    // internal clock to output sample rate mapping
    /** current output sample position */
    private int pos;
    /** step value per output sample */
    private int step;
//    /** clock period */
//    attotime clock_period;
//    /** timer */
//    emu_timer timer;

    // I/O lines
    /** last data written to the sound chip */
    private int fifo_in;
    /** current state of the RESET line */
    private int reset;
    /** current state of the START line */
    private int start;
    /** current state of the DRQ line */
    private int drq;
//    /** drq callback */
//    void (*drqcallback)(running_device *device, int param);
//    /** drq callback */
//    void (*drqcallback)(int param);

    // internal state machine
    /** current overall chip state */
    private STATE state;
    /** number of clocks left in this state */
    private int clocks_left;
    /** number of ADPCM nibbles left to process */
    private short nibbles_left;
    /** number of repeats remaining in current repeat block */
    private byte repeat_count;
    /** state we will be in after the DRQ line is dropped */
    private STATE post_drq_state;
    /** clocks that will be left after the DRQ line is dropped */
    private int post_drq_clocks;
    /** requested sample number */
    private int req_sample;
    /** last sample number available */
    private int last_sample;
    /** header byte */
    private int block_header;
    /** number of UPD clocks per ADPCM nibble */
    private int sample_rate;
    /** did we get our first valid header yet? */
    private int first_valid_header;
    /** current ROM offset */
    private int offset;
    /** current ROM repeat offset */
    private int repeat_offset;

    // ADPCM processing
    /** ADPCM state index */
    private int adpcm_state;
    /** current byte of ADPCM data */
    private int adpcm_data;
    /** current sample value */
    private short sample;

    // ROM access
    private int romsize;
    /** pointer to ROM data or NULL for slave mode */
    private byte[] rom;
    private int romPtr = 0;
    /** pointer to ROM data or NULL for slave mode */
    private byte[] rombase;
    /** ROM offset to make save/restore easier */
    private int romoffset;
    /** 0 - Master, 1 - Slave */
    private int ChipMode;

    // Valley Bell: Added a FIFO buffer based on Sega Pico.
    private final byte[] data_buf = new byte[0x40];
    private int dbuf_pos_read;
    private int dbuf_pos_write;

    //
    // Local variables
    //

    private static final int[][] upd7759_step = {
            {0, 0, 1, 2, 3, 5, 7, 10, 0, 0, -1, -2, -3, -5, -7, -10},
            {0, 1, 2, 3, 4, 6, 8, 13, 0, -1, -2, -3, -4, -6, -8, -13},
            {0, 1, 2, 4, 5, 7, 10, 15, 0, -1, -2, -4, -5, -7, -10, -15},
            {0, 1, 3, 4, 6, 9, 13, 19, 0, -1, -3, -4, -6, -9, -13, -19},
            {0, 2, 3, 5, 8, 11, 15, 23, 0, -2, -3, -5, -8, -11, -15, -23},
            {0, 2, 4, 7, 10, 14, 19, 29, 0, -2, -4, -7, -10, -14, -19, -29},
            {0, 3, 5, 8, 12, 16, 22, 33, 0, -3, -5, -8, -12, -16, -22, -33},
            {1, 4, 7, 10, 15, 20, 29, 43, -1, -4, -7, -10, -15, -20, -29, -43},
            {1, 4, 8, 13, 18, 25, 35, 53, -1, -4, -8, -13, -18, -25, -35, -53},
            {1, 6, 10, 16, 22, 31, 43, 64, -1, -6, -10, -16, -22, -31, -43, -64},
            {2, 7, 12, 19, 27, 37, 51, 76, -2, -7, -12, -19, -27, -37, -51, -76},
            {2, 9, 16, 24, 34, 46, 64, 96, -2, -9, -16, -24, -34, -46, -64, -96},
            {3, 11, 19, 29, 41, 57, 79, 117, -3, -11, -19, -29, -41, -57, -79, -117},
            {4, 13, 24, 36, 50, 69, 96, 143, -4, -13, -24, -36, -50, -69, -96, -143},
            {4, 16, 29, 44, 62, 85, 118, 175, -4, -16, -29, -44, -62, -85, -118, -175},
            {6, 20, 36, 54, 76, 104, 144, 214, -6, -20, -36, -54, -76, -104, -144, -214}
    };

    private static final int[] upd7759_state_table = {-1, -1, 0, 0, 1, 2, 2, 3, -1, -1, 0, 0, 1, 2, 2, 3};

    /**
     * ADPCM sample updater
     */
    private void update_adpcm(int data) {
        // update the sample and the state
        this.sample += (short) upd7759_step[this.adpcm_state][data];
        this.adpcm_state += upd7759_state_table[data];

        // clamp the state to 0..15
        if (this.adpcm_state < 0)
            this.adpcm_state = 0;
        else if (this.adpcm_state > 15)
            this.adpcm_state = 15;
    }

    /**
     * Master chip state machine
     */
    private void get_fifo_data() {
        if (this.dbuf_pos_read == this.dbuf_pos_write) {
logger.log(Level.DEBUG, "Warning: UPD7759 reading empty FIFO!");
            return;
        }

        this.fifo_in = this.data_buf[this.dbuf_pos_read];
        this.dbuf_pos_read++;
        this.dbuf_pos_read &= 0x3F;
    }

    private void advance_state() {
        switch (this.state) {
            // Idle state: we stick around here while there's nothing to do
            case IDLE:
                this.clocks_left = 4;
                break;

            // drop DRQ state: update to the intended state
            case DROP_DRQ:
                this.drq = 0;

                if (this.ChipMode != 0)
                    get_fifo_data();    // Slave Mode only
                this.clocks_left = this.post_drq_clocks;
                this.state = this.post_drq_state;
                break;

            // Start state: we begin here as soon as a sample is triggered
            case START:
                this.req_sample = this.rom != null ? this.fifo_in : 0x10;
logger.log(Level.DEBUG, "UPD7759: req_sample = %02x".formatted(this.req_sample));
                // 35+ cycles after we get here, the /DRQ goes low
                //     (first byte (number of samples in ROM) should be sent in response)
                //
                // (35 is the minimum number of cycles I found during heavy tests.
                // Depending on the state the chip was in just before the /MD was set to 0 (reset, standby
                // or just-finished-playing-previous-sample) this number can range from 35 up to ~24000).
                // It also varies slightly from test to test, but not much - a few cycles at most.)
                this.clocks_left = 70; // 35 - breaks cotton
                this.state = STATE.FIRST_REQ;
                break;

            // First request state: issue a request for the first byte
            // The expected response will be the index of the last sample
            case FIRST_REQ:
logger.log(Level.DEBUG, "UPD7759: first data request");
                this.drq = 1;

                // 44 cycles later, we will latch this value and request another byte
                this.clocks_left = 44;
                this.state = STATE.LAST_SAMPLE;
                break;

            // Last sample state: latch the last sample value and issue a request for the second byte
            // The second byte read will be just a dummy
            case LAST_SAMPLE:
                this.last_sample = this.rom != null ? this.rom[this.romPtr + 0] : this.fifo_in;
logger.log(Level.DEBUG, "UPD7759: last_sample = %02x, requesting dummy 1".formatted(this.last_sample));
                this.drq = 1;

                // 28 cycles later, we will latch this value and request another byte
                this.clocks_left = 28; // 28 - breaks cotton
                this.state = ((this.req_sample > this.last_sample) ? STATE.IDLE : STATE.DUMMY1);
                break;

            // First dummy state: ignore any data here and issue a request for the third byte
            // The expected response will be the MSB of the sample address
            case DUMMY1:
logger.log(Level.DEBUG, "UPD7759: dummy1, requesting offset_hi");
                this.drq = 1;

                // 32 cycles later, we will latch this value and request another byte
                this.clocks_left = 32;
                this.state = STATE.ADDR_MSB;
                break;

            // Address MSB state: latch the MSB of the sample address and issue a request for the fourth byte
            // The expected response will be the LSB of the sample address
            case ADDR_MSB:
                this.offset = (this.rom != null ? this.rom[this.romPtr + (this.req_sample * 2 + 5)] : this.fifo_in) << 9;
logger.log(Level.DEBUG, "UPD7759: offset_hi = %02x, requesting offset_lo".formatted(this.offset >> 9));
                this.drq = 1;

                // 44 cycles later, we will latch this value and request another byte
                this.clocks_left = 44;
                this.state = STATE.ADDR_LSB;
                break;

            // Address LSB state: latch the LSB of the sample address and issue a request for the fifth byte
            // The expected response will be just a dummy
            case ADDR_LSB:
                this.offset |= (this.rom != null ? this.rom[this.romPtr + (this.req_sample * 2 + 6)] : this.fifo_in) << 1;
logger.log(Level.DEBUG, "UPD7759: offset_lo = %02x, requesting dummy 2".formatted((this.offset >> 1) & 0xff));
                this.drq = 1;

                // 36 cycles later, we will latch this value and request another byte
                this.clocks_left = 36;
                this.state = STATE.DUMMY2;
                break;

            // Second dummy state: ignore any data here and issue a request for the sixth byte
            // The expected response will be the first block header
            case DUMMY2:
                this.offset++;
                this.first_valid_header = 0;
logger.log(Level.DEBUG, "UPD7759: dummy2, requesting block header");
                this.drq = 1;

                // 36?? cycles later, we will latch this value and request another byte
                this.clocks_left = 36;
                this.state = STATE.BLOCK_HEADER;
                break;

            // Block header state: latch the header and issue a request for the first byte afterward
            case BLOCK_HEADER:

                // if we're in a repeat loop, reset the offset to the repeat point and decrement the count
                if (this.repeat_count != 0) {
                    this.repeat_count--;
                    this.offset = this.repeat_offset;
                }
                this.block_header = this.rom != null ? this.rom[this.romPtr + (this.offset++ & 0x1ffff)] : this.fifo_in;
logger.log(Level.DEBUG, "UPD7759: header (@%05x) = %02x, requesting next byte".formatted(this.offset, this.block_header));
                this.drq = 1;

                // our next step depends on the top two bits
                switch (this.block_header & 0xc0) {
                    case 0x00:  // silence
                        this.clocks_left = 1024 * ((this.block_header & 0x3f) + 1);
                        this.state = (this.block_header == 0 && this.first_valid_header != 0) ? STATE.IDLE : STATE.BLOCK_HEADER;
                        this.sample = 0;
                        this.adpcm_state = 0;
                        break;

                    case 0x40:  // 256 nibbles
                        this.sample_rate = (byte) ((this.block_header & 0x3f) + 1);
                        this.nibbles_left = 256;
                        this.clocks_left = 36; // just a guess
                        this.state = STATE.NIBBLE_MSN;
                        break;

                    case 0x80:  // n nibbles
                        this.sample_rate = (byte) ((this.block_header & 0x3f) + 1);
                        this.clocks_left = 36; // just a guess
                        this.state = STATE.NIBBLE_COUNT;
                        break;

                    case 0xc0:  // repeat loop
                        this.repeat_count = (byte) ((this.block_header & 7) + 1);
                        this.repeat_offset = this.offset;
                        this.clocks_left = 36; // just a guess
                        this.state = STATE.BLOCK_HEADER;
                        break;
                }

                // set a flag when we get the first non-zero header
                if (this.block_header != 0)
                    this.first_valid_header = 1;
                break;

            // Nibble count state: latch the number of nibbles to play and request another byte
            // The expected response will be the first data byte
            case NIBBLE_COUNT:
                this.nibbles_left = (short) ((this.rom != null ? this.rom[this.romPtr + (this.offset++ & 0x1ffff)] : this.fifo_in) + 1);
logger.log(Level.DEBUG, "UPD7759: nibble_count = %d, requesting next byte".formatted(this.nibbles_left));
                this.drq = 1;

                // 36?? cycles later, we will latch this value and request another byte
                this.clocks_left = 36; // just a guess
                this.state = STATE.NIBBLE_MSN;
                break;

            // MSN state: latch the data for this pair of samples and request another byte
            // The expected response will be the next sample data or another header
            case NIBBLE_MSN:
                this.adpcm_data = this.rom != null ? this.rom[this.romPtr + (this.offset++ & 0x1ffff)] : this.fifo_in;
                update_adpcm(this.adpcm_data >> 4);
                this.drq = 1;

                // we stay in this state until the time for this sample is complete
                this.clocks_left = this.sample_rate * 4;
                if (--this.nibbles_left == 0)
                    this.state = STATE.BLOCK_HEADER;
                else
                    this.state = STATE.NIBBLE_LSN;
                break;

            // LSN state: process the lower nibble
            case NIBBLE_LSN:
                update_adpcm(this.adpcm_data & 15);

                // we stay in this state until the time for this sample is complete
                this.clocks_left = this.sample_rate * 4;
                if (--this.nibbles_left == 0)
                    this.state = STATE.BLOCK_HEADER;
                else
                    this.state = STATE.NIBBLE_MSN;
                break;
        }

        // if there's a DRQ, fudge the state
        if (this.drq != 0) {
            this.post_drq_state = this.state;
            this.post_drq_clocks = this.clocks_left - 21;
            this.state = STATE.DROP_DRQ;
            this.clocks_left = 21;
        }
    }

    /**
     * Stream callback
     */
    public void upd7759_update(int[][] outputs, int samples) {
        //upd7759_state *chip = (upd7759_state *)param;
        int clocks_left = this.clocks_left;
        short sample = this.sample;
        int step = this.step;
        int pos = this.pos;
        int[] buffer = outputs[0];
        int[] buffer2 = outputs[1];
        int bufferPtr = 0;
        int bufferPtr2 = 0;

        // loop until done
        if (this.state != STATE.IDLE)
            while (samples != 0) {
                // store the current sample
                buffer[bufferPtr++] = sample << 7;
                buffer2[bufferPtr2++] = sample << 7;
                samples--;

                // advance by the number of clocks/output sample
                pos += step;

                // handle clocks, but only in standalone mode
                if (this.ChipMode == 0) {
                    while (this.rom != null && pos >= FRAC_ONE) {
                        int clocks_this_time = pos >> FRAC_BITS;
                        if (clocks_this_time > clocks_left)
                            clocks_this_time = clocks_left;

                        // clock once
                        pos -= clocks_this_time * FRAC_ONE;
                        clocks_left -= clocks_this_time;

                        // if we're out of clocks, time to handle the next state
                        if (clocks_left == 0) {
                            // advance one state; if we hit idle, bail
                            advance_state();
                            if (this.state == STATE.IDLE)
                                break;

                            // reimport the variables that we cached
                            clocks_left = this.clocks_left;
                            sample = this.sample;
                        }
                    }
                } else {
                    byte CntFour;

                    if (clocks_left == 0) {
                        advance_state();
                        clocks_left = this.clocks_left;
                    }

                    // advance the state (4x because of Clock Divider /4)
                    for (CntFour = 0; CntFour < 4; CntFour++) {
                        clocks_left--;
                        if (clocks_left == 0) {
                            advance_state();
                            clocks_left = this.clocks_left;
                        }
                    }
                }
            }

        // if we got out early, just zap the rest of the buffer
        if (samples != 0) {
            Arrays.fill(buffer, 0);
            Arrays.fill(buffer2, 0);
        }

        // flush the state back
        this.clocks_left = clocks_left;
        this.pos = pos;
    }

//    /**
//     * DRQ callback
//     */
//    static TIMER_CALLBACK(upd7759_slave_update) {
//        upd7759_state chip = (upd7759_state) ptr;
//        int8 olddrq = this.drq;
//
//        // update the stream
//        //stream_update(this.channel);
//
//        // advance the state
//        advance_state(chip);
//
//        // if the DRQ changed, update it
//        logger.log(Level.DEBUG, "slave_update: DRQ %d->%d\n", olddrq, this.drq);
//        if (olddrq != this.drq && this.drqcallback)
//            //(*this.drqcallback)(this.device, this.drq);
//            ( * this.drqcallback)(this.drq);
//
//        // set a timer to go off when that is done
//        //if (this.state != STATE_IDLE)
//        //	timer_adjust_oneshot(this.timer, attotime_mul(this.clock_period, this.clocks_left), 0);
//    }

    /**
     * Sound startup
     */
    private void upd7759_reset() {
        this.pos = 0;
        this.fifo_in = 0;
        this.drq = 0;
        this.state = STATE.IDLE;
        this.clocks_left = 0;
        this.nibbles_left = 0;
        this.repeat_count = 0;
        this.post_drq_state = STATE.IDLE;
        this.post_drq_clocks = 0;
        this.req_sample = 0;
        this.last_sample = 0;
        this.block_header = 0;
        this.sample_rate = 0;
        this.first_valid_header = 0;
        this.offset = 0;
        this.repeat_offset = 0;
        this.adpcm_state = 0;
        this.adpcm_data = 0;
        this.sample = 0;

        // Valley Bell: reset buffer
        this.data_buf[0] = this.data_buf[1] = 0x00;
        this.dbuf_pos_read = 0x00;
        this.dbuf_pos_write = 0x00;

        // turn off any timer
        //if (this.timer)
        //	timer_adjust_oneshot(this.timer, attotime_never, 0);
        if (this.ChipMode != 0)
            this.clocks_left = -1;
    }

    //static DEVICE_RESET( upd7759 )
    public void device_reset_upd7759() {
        //upd7759_reset(get_safe_token(device));
        upd7759_reset();
    }

    //static DEVICE_START( upd7759 )
    public int device_start_upd7759(int clock) {
        //this.device = device;
        this.ChipMode = (byte) ((clock & 0x8000_0000) >> 31);
        clock &= 0x7fff_ffff;

        // allocate a stream channel
        //this.channel = stream_create(device, 0, 1, device->clock()/4, chip, upd7759_update);

        // compute the stepping rate based on the chip's clock speed
        this.step = 4 * FRAC_ONE;

        // compute the clock period
        //this.clock_period = ATTOTIME_IN_HZ(device->clock());

        // set the intial state
        this.state = STATE.IDLE;

        // compute the ROM base or allocate a timer
        //this.rom = this.rombase = *device->region();
        this.romsize = 0x00;
        this.rom = this.rombase = null;
        this.romPtr = 0;
        //if (this.rom == NULL)
        //	this.timer = timer_alloc(device->machine, upd7759_slave_update, chip);
        this.romoffset = 0x00;

        // set the DRQ callback
        //this.drqcallback = intf->drqcallback;

        // assume /RESET and /START are both high
        this.reset = 1;
        this.start = 1;

        // toggle the reset line to finish the reset
        upd7759_reset();

        //register_for_save(chip, device);

        return clock / 4;
    }

    public void device_stop_upd7759() {
        this.rom = null;
        this.romPtr = 0;
        this.rombase = null;
    }

    /**
     * I/O handlers
     */
    public void upd7759_reset_w(int data) {
        // update the reset value
        int oldreset = this.reset;
        this.reset = (byte) ((data != 0) ? 1 : 0);

        // update the stream first
        //stream_update(this.channel);

        // on the falling edge, reset everything
        if (oldreset != 0 && this.reset == 0)
            upd7759_reset();
    }

    public void upd7759_start_w(int data) {
        // update the start value
        int oldstart = this.start;
        this.start = (byte) ((data != 0) ? 1 : 0);

logger.log(Level.DEBUG, "upd7759_start_w: %d->%d".formatted(oldstart, this.start));
        // update the stream first
        //stream_update(this.channel);

        // on the rising edge, if we're idle, start going, but not if we're held in reset
        if (this.state == STATE.IDLE && oldstart == 0 && this.start != 0 && this.reset != 0) {
            this.state = STATE.START;

            // for slave mode, start the timer going
            //if (this.timer)
            //	timer_adjust_oneshot(this.timer, attotime_zero, 0);
            this.clocks_left = 0;
        }
    }

    public void upd7759_port_w(int offset, int data) {
        // update the FIFO value

        if (this.ChipMode == 0) {
            this.fifo_in = data;
        } else {
            // Valley Bell: added FIFO buffer for Slave mode
            this.data_buf[this.dbuf_pos_write] = (byte) data;
            this.dbuf_pos_write++;
            this.dbuf_pos_write &= 0x3F;
        }
    }

    public int upd7759_busy_r() {
        // return /BUSY
        //upd7759_state *chip = get_safe_token(device);
        return (this.state == STATE.IDLE) ? 1 : 0;
    }

    //void upd7759_set_bank_base(running_device *device, int base)
    public void upd7759_set_bank_base(int base_) {
        this.rom = this.rombase;
        this.romPtr = base_;
        this.romoffset = base_;
    }

    public void upd7759_write(int Port, int Data) {
        switch (Port) {
            case 0x00:
                upd7759_reset_w(Data);
                break;
            case 0x01:
                upd7759_start_w(Data);
                break;
            case 0x02:
                upd7759_port_w(0x00, Data);
                break;
            case 0x03:
                upd7759_set_bank_base(Data * 0x20000);
                break;
        }
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        if (this.romsize != romSize) {
            this.rombase = new byte[romSize]; // (int8*) realloc(this.rombase, romSize);
            this.romsize = romSize;
            for (int i = 0; i < romSize; i++) this.rombase[i] = (byte) 0xff;

            this.rom = this.rombase;
            this.romPtr = this.romoffset;
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        System.arraycopy(romData, 0 + srcStartAdr, this.rombase, 0 + dataStart, dataLength);
    }
}

