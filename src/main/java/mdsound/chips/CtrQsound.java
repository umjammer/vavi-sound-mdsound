package mdsound.chips;

import java.util.Arrays;


/**
 * Capcom DL-1425 QSound emulator
 *
 * by superctr (Ian Karlsson)
 * with thanks to Valley Bell
 * 2018-05-12 - 2018-05-15
 */
public class CtrQsound {

    private static class Adpcm {
        //private int start_addr = 0;
        //private int end_addr = 0;
        //private int bank = 0;
        //private short volume = 0;
        //private int flag = 0;
        private short cur_vol = 0;
        private short step_size = 0;
        private int cur_addr = 0;
    }

    // Q1 Fir
    private static class Fir {
        private int tapCount = 0; // usually 95
        private int delayPos = 0;
        //private short table_pos = 0;
        private short[] taps = new short[95];
        private short[] delay_line = new short[95];

        // Apply the FIR filter used as the Q1 transfer function
        private int fir(short input) {
            int output = 0, tap = 0;

            for (; tap < (this.tapCount - 1); tap++) {
                output -= (this.taps[tap] * this.delay_line[this.delayPos++]) << 2;

                if (this.delayPos >= this.tapCount - 1)
                    this.delayPos = 0;
            }

            output -= (this.taps[tap] * input) << 2;

            this.delay_line[this.delayPos++] = input;
            if (this.delayPos >= this.tapCount - 1)
                this.delayPos = 0;

            return output;
        }
    }

    // Delay line
    private static class Delay {
        //private short delay;
        //private short volume;
        private short writePos;
        private short readPos;
        private short[] delayLine = new short[51];
    }

    private static class Echo {
        //private int end_pos;

        //private short feedback;
        private short length;
        private short last_sample;
        private short[] delayLine = new short[1024];
        private short delay_pos;

        // The echo effect is pretty simple. A moving average filter is used on
        // the output from the delay line to smooth samples over time.
        private short echo(int input) {
            // get average of last 2 samples from the delay line
            int new_sample;
            int oldSample = this.delayLine[this.delay_pos];
            int lastSample = this.last_sample;

            this.last_sample = (short) oldSample;
            oldSample = (oldSample + lastSample) >> 1;

            // add current sample to the delay line
            //new_sample = input + ((oldSample * this.feedback) << 2);
            new_sample = input + ((oldSample * registerMap[0x93]) << 2);
            this.delayLine[this.delay_pos++] = (short) (new_sample >> 16);

            if (this.delay_pos >= this.length)
                this.delay_pos = 0;

            return (short) oldSample;
        }
    }

    private byte[] romData;
    private int romSize;
    private int romMask;
    private int muteMask;

    //

    private int dataLatch;
    private short[] _out = new short[2];

    private short[][][] panTables = new short[][][] {
            new short[][] {new short[98], new short[98]},
            new short[][] {new short[98], new short[98]}
    };

    private Adpcm[] adpcm = new Adpcm[3];

    private short[] voiceOutput = new short[16 + 3];

    private Echo echo = new Echo();

    private Fir[] filter = new Fir[2];
    private Fir[] altFilter = new Fir[2];

    private Delay[] wet = new Delay[2];
    private Delay[] dry = new Delay[2];

    private int state;

    private int stateCounter;
    private byte readyFlag;

    private static int[] registerMap = new int[256];

    private static final short[] dryMixTable = new short[] {
            -16384, -16384, -16384, -16384, -16384, -16384, -16384, -16384,
            -16384, -16384, -16384, -16384, -16384, -16384, -16384, -16384,
            -16384, -14746, -13107, -11633, -10486, -9175, -8520, -7209,
            -6226, -5226, -4588, -3768, -3277, -2703, -2130, -1802,
            0
    };

    private static final short[] wetMixTable = new short[] {
            0, -1638, -1966, -2458, -2949, -3441, -4096, -4669,
            -4915, -5120, -5489, -6144, -7537, -8831, -9339, -9830,
            -10240, -10322, -10486, -10568, -10650, -11796, -12288, -12288,
            -12534, -12648, -12780, -12829, -12943, -13107, -13418, -14090,
            -16384
    };

    private static final short[] linearMixTable = new short[] {
            -16379, -16338, -16257, -16135, -15973, -15772, -15531, -15251,
            -14934, -14580, -14189, -13763, -13303, -12810, -12284, -11729,
            -11729, -11144, -10531, -9893, -9229, -8543, -7836, -7109,
            -6364, -5604, -4829, -4043, -3246, -2442, -1631, -817,
            0
    };

    private static final short[][] filterData = new short[][] {
            new short[] { // d53 - 0
                    0, 0, 0, 6, 44, -24, -53, -10, 59, -40, -27, 1, 39, -27, 56, 127, 174, 36, -13, 49,
                    212, 142, 143, -73, -20, 66, -108, -117, -399, -265, -392, -569, -473, -71, 95, -319, -218, -230, 331, 638,
                    449, 477, -180, 532, 1107, 750, 9899, 3828, -2418, 1071, -176, 191, -431, 64, 117, -150, -274, -97, -238, 165,
                    166, 250, -19, 4, 37, 204, 186, -6, 140, -77, -1, 1, 18, -10, -151, -149, -103, -9, 55, 23,
                    -102, -97, -11, 13, -48, -27, 5, 18, -61, -30, 64, 72, 0, 0, 0,
            },
            new short[] { // db2 - 1 - default left filter
                    0, 0, 0, 85, 24, -76, -123, -86, -29, -14, -20, -7, 6, -28, -87, -89, -5, 100, 154, 160,
                    150, 118, 41, -48, -78, -23, 59, 83, -2, -176, -333, -344, -203, -66, -39, 2, 224, 495, 495, 280,
                    432, 1340, 2483, 5377, 1905, 658, 0, 97, 347, 285, 35, -95, -78, -82, -151, -192, -171, -149, -147, -113,
                    -22, 71, 118, 129, 127, 110, 71, 31, 20, 36, 46, 23, -27, -63, -53, -21, -19, -60, -92, -69,
                    -12, 25, 29, 30, 40, 41, 29, 30, 46, 39, -15, -74, 0, 0, 0,
            },
            new short[] { // e11 - 2 - default right filter
                    0, 0, 0, 23, 42, 47, 29, 10, 2, -14, -54, -92, -93, -70, -64, -77, -57, 18, 94, 113,
                    87, 69, 67, 50, 25, 29, 58, 62, 24, -39, -131, -256, -325, -234, -45, 58, 78, 223, 485, 496,
                    127, 6, 857, 2283, 2683, 4928, 1328, 132, 79, 314, 189, -80, -90, 35, -21, -186, -195, -99, -136, -258,
                    -189, 82, 257, 185, 53, 41, 84, 68, 38, 63, 77, 14, -60, -71, -71, -120, -151, -84, 14, 29,
                    -8, 7, 66, 69, 12, -3, 54, 92, 52, -6, -15, -2, 0, 0, 0,
            },
            new short[] { // e70 - 3
                    0, 0, 0, 2, -28, -37, -17, 0, -9, -22, -3, 35, 52, 39, 20, 7, -6, 2, 55, 121,
                    129, 67, 8, 1, 9, -6, -16, 16, 66, 96, 118, 130, 75, -47, -92, 43, 223, 239, 151, 219,
                    440, 475, 226, 206, 940, 2100, 2663, 4980, 865, 49, -33, 186, 231, 103, 42, 114, 191, 184, 116, 29,
                    -47, -72, -21, 60, 96, 68, 31, 32, 63, 87, 76, 39, 7, 14, 55, 85, 67, 18, -12, -3,
                    21, 34, 29, 6, -27, -49, -37, -2, 16, 0, -21, -16, 0, 0, 0,
            },
            new short[] { // ecf - 4
                    0, 0, 0, 48, 7, -22, -29, -10, 24, 54, 59, 29, -36, -117, -185, -213, -185, -99, 13, 90,
                    83, 24, -5, 23, 53, 47, 38, 56, 67, 57, 75, 107, 16, -242, -440, -355, -120, -33, -47, 152,
                    501, 472, -57, -292, 544, 1937, 2277, 6145, 1240, 153, 47, 200, 152, 36, 64, 134, 74, -82, -208, -266,
                    -268, -188, -42, 65, 74, 56, 89, 133, 114, 44, -3, -1, 17, 29, 29, -2, -76, -156, -187, -151,
                    -85, -31, -5, 7, 20, 32, 24, -5, -20, 6, 48, 62, 0, 0, 0,
            }
    };

    private static final short[] filterData2 = new short[] {
            // f2e - following 95 values used for "disable output" filter
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,

            // f73 - following 45 values used for "mode 2" filter (overlaps with f2e)
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0,
            -371, -196, -268, -512, -303, -315, -184, -76, 276, -256, 298, 196, 990, 236, 1114, -126, 4377, 6549, 791,

            // fa0 - filtering disabled (for 95-taps) (use fa3 or fa4 for mode2 filters)
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, -16384, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };

    private static final short[] adpcmStepTable = new short[] {
            154, 154, 128, 102, 77, 58, 58, 58,
            58, 58, 58, 58, 77, 102, 128, 154
    };

    // DSP states
    private enum State {
        INIT1(0x288),
        INIT2(0x61a),
        REFRESH1(0x039),
        REFRESH2(0x04f),
        NORMAL1(0x314),
        NORMAL2(0x6b2);
        final int v;

        State(int v) {
            this.v = v;
        }

        static State valueOf(int v) {
            return Arrays.stream(values()).filter(e -> e.v == v).findFirst().get();
        }
    }

    private enum Pan {
        LEFT(0),
        RIGHT(1),
        DRY(0),
        WET(1);
        final int v;

        Pan(int v) {
            this.v = v;
        }
    }

    public CtrQsound() {
        for (int i = 0; i < adpcm.length; i++) adpcm[i] = new Adpcm();
        for (int i = 0; i < filter.length; i++) filter[i] = new Fir();
        for (int i = 0; i < altFilter.length; i++) altFilter[i] = new Fir();
        for (int i = 0; i < wet.length; i++) wet[i] = new Delay();
        for (int i = 0; i < dry.length; i++) dry[i] = new Delay();
    }

    private static int clamp(int x, int low, int high) {
        if (x > high)
            return high;
        return Math.max(x, low);
    }

    private void initPanTables() {
        for (int i = 0; i < 33; i++) {
            // dry mixing levels
            this.panTables[Pan.LEFT.v][Pan.DRY.v][i] = dryMixTable[i];
            this.panTables[Pan.RIGHT.v][Pan.DRY.v][i] = dryMixTable[32 - i];
            // wet mixing levels
            this.panTables[Pan.LEFT.v][Pan.WET.v][i] = wetMixTable[i];
            this.panTables[Pan.RIGHT.v][Pan.WET.v][i] = wetMixTable[32 - i];
            // linear panning, only for dry component. wet component is muted.
            this.panTables[Pan.LEFT.v][Pan.DRY.v][i + 0x30] = linearMixTable[i];
            this.panTables[Pan.RIGHT.v][Pan.DRY.v][i + 0x30] = linearMixTable[32 - i];
        }
    }

    private void initRegisterMap() {
        // unused registers
        for (int i = 0; i < 256; i++) registerMap[i] = 0;// null;

        // PCM registers
        for (int i = 0; i < 16; i++) { // PCM voices
            //register_map[(i << 3) + 0] = (int)this.Voice[(i + 1) % 16].bank; // Bank applies to the next channel
            //register_map[(i << 3) + 1] = (int)this.Voice[i].addr; // Current sample position and start position.
            //register_map[(i << 3) + 2] = (int)this.Voice[i].rate; // 4.12 fixed point decimal.
            //register_map[(i << 3) + 3] = (int)this.Voice[i].phase;
            //register_map[(i << 3) + 4] = (int)this.Voice[i].loop_len;
            //register_map[(i << 3) + 5] = (int)this.Voice[i].end_addr;
            //register_map[(i << 3) + 6] = (int)this.Voice[i].volume;
            registerMap[(i << 3) + 7] = 0;// null; // unused
            //register_map[i + 0x80] = (int)this.voice_pan[i];
            //register_map[i + 0xba] = (int)this.Voice[i].echo;
        }

        // ADPCM registers
        //for (i = 0; i < 3; i++) { // ADPCM voices
        // ADPCM sample rate is fixed to 8khz. (one channel is updated every third sample)
        //register_map[(i << 2) + 0xca] = (int)this.adpcm[i].start_addr;
        //register_map[(i << 2) + 0xcb] = (int)this.adpcm[i].end_addr;
        //register_map[(i << 2) + 0xcc] = (int)this.adpcm[i].bank;
        //register_map[(i << 2) + 0xcd] = (int)this.adpcm[i].volume;
        //register_map[i + 0xd6] = (int)this.adpcm[i].flag; // non-zero to start ADPCM playback
        //register_map[i + 0x90] = (int)this.voice_pan[16 + i];
        //}

        // QSound registers
        //register_map[0x93] = (int)this.echo.feedback;
        //register_map[0xd9] = (int)this.echo.end_pos;
        //register_map[0xe2] = (int)this.delay_update; // non-zero to update delays
        //register_map[0xe3] = (int)this.next_state;
        //for (i = 0; i < 2; i++) { // left, right
        // Wet
        //register_map[(i << 1) + 0xda] = (int)this.filter[i].table_pos;
        //register_map[(i << 1) + 0xde] = (int)this.wet[i].delay;
        //register_map[(i << 1) + 0xe4] = (int)this.wet[i].volume;
        // Dry
        //register_map[(i << 1) + 0xdb] = (int)this.alt_filter[i].table_pos;
        //register_map[(i << 1) + 0xdf] = (int)this.dry[i].delay;
        //register_map[(i << 1) + 0xe5] = (int)this.dry[i].volume;
        //}
    }

    private short get_sample(int bank, int address) {
        int rom_addr;
        byte sample_data;

        if (this.romMask == 0) return 0; // no ROM loaded
        if ((bank & 0x8000) == 0) return 0; // ignore attempts to read from DSP program ROM

        bank &= 0x7FFF;
        rom_addr = (bank << 16) | (address << 0);

        sample_data = rom_addr < this.romData.length ? this.romData[rom_addr] : (byte) 0;
        //Debug.printf("adr:%10x dat:%02x", rom_addr, sample_data);

        return (short) ((sample_data << 8) | (sample_data << 0)); // MAME currently expands the 8 bit ROM data to 16 bits this way.
    }

    //private short[] get_filter_table(, int offset) {
    // int index;

    // if (offset >= 0xf2e && offset < 0xfff)
    //  return qsound_filter_data2[offset - 0xf2e]; // overlapping filter data

    // index = (offset - 0xd53) / 95;
    // if (index >= 0 && index < 5)
    //  return qsound_filter_data[index]; // normal tables

    // return null; // no filter found.
    //}

    private Short getFilterTable(int offset) {
        if (offset >= 0xf2e && offset < 0xfff)
            return filterData2[offset - 0xf2e]; // overlapping filter data

        int index = (offset - 0xd53) / 95;
        if (index >= 0 && index < 5)
            return filterData[index][(offset - 0xd53) % 95]; // normal tables

        return null; // no filter found.
    }

    // updates one DSP sample
    private void update_sample() {
        switch (State.valueOf(this.state)) {
        default:
        case INIT1:
        case INIT2:
            state_init();
            break;
        case REFRESH1:
            state_refresh_filter_1();
            break;
        case REFRESH2:
            state_refresh_filter_2();
            break;
        case NORMAL1:
        case NORMAL2:
            state_normal_update();
            break;
        }
    }

    // Initialization routine
    private void state_init() {
        int mode = (this.state == State.INIT2.v) ? 1 : 0;

        // we're busy for 4 samples, including the filter refresh.
        if (this.stateCounter >= 2) {
            this.stateCounter = 0;
            this.state = registerMap[0xe3];
            return;
        } else if (this.stateCounter == 1) {
            this.stateCounter++;
            return;
        }

        for (int i = 0; i < this.adpcm.length; i++) this.adpcm[i] = new Adpcm();
        for (int i = 0; i < this.filter.length; i++) this.filter[i] = new Fir();
        for (int i = 0; i < this.altFilter.length; i++) this.altFilter[i] = new Fir();
        for (int i = 0; i < this.wet.length; i++) this.wet[i] = new Delay();
        for (int i = 0; i < this.dry.length; i++) this.dry[i] = new Delay();
        this.echo = new Echo();

        for (int i = 0; i < 19; i++) {
            registerMap[i + 0x80] = 0x120;
            this.voiceOutput[i] = 0;
        }

        for (int i = 0; i < 16; i++) {
            registerMap[(i << 3) + 0] = 0x8000;
        }
        for (int i = 0; i < 3; i++) {
            registerMap[(i << 2) + 0xcc] = 0x8000;
        }
        if (mode == 0) {
            // mode 1
            registerMap[(0 << 1) + 0xde] = 0;
            registerMap[(0 << 1) + 0xdf] = 46;
            registerMap[(1 << 1) + 0xde] = 0;
            registerMap[(1 << 1) + 0xdf] = 48;
            registerMap[(0 << 1) + 0xda] = 0xdb2;
            registerMap[(1 << 1) + 0xda] = 0xe11;
            registerMap[0xd9] = 0x554 + 6;
            registerMap[0xe3] = State.REFRESH1.v;
        } else {
            // mode 2
            registerMap[(0 << 1) + 0xde] = 1;
            registerMap[(0 << 1) + 0xdf] = 0;
            registerMap[(1 << 1) + 0xde] = 0;
            registerMap[(1 << 1) + 0xdf] = 0;
            registerMap[(0 << 1) + 0xda] = 0xf73;
            registerMap[(1 << 1) + 0xda] = 0xfa4;
            registerMap[(0 << 1) + 0xdb] = 0xf73;
            registerMap[(1 << 1) + 0xdb] = 0xfa4;
            registerMap[0xd9] = 0x53c + 6;
            registerMap[0xe3] = State.REFRESH2.v;
        }

        registerMap[(0 << 1) + 0xe4] = 0x3fff;
        registerMap[(0 << 1) + 0xe5] = 0x3fff;
        registerMap[(1 << 1) + 0xe4] = 0x3fff;
        registerMap[(1 << 1) + 0xe5] = 0x3fff;

        //this.delay_update = 1;
        registerMap[0xe2] = 1;
        this.readyFlag = 0;
        this.stateCounter = 1;
    }

    // Updates filter parameters for mode 1
    private void state_refresh_filter_1() {
        for (int ch = 0; ch < 2; ch++) {
            this.filter[ch].delayPos = 0;
            this.filter[ch].tapCount = 95;

            //table = get_filter_table(chips, (int)this.filter[ch].table_pos);
            //if (table != null) {
            // for (int i = 0; i < 95; i++) this.filter[ch].taps[i] = table[i];
            //}

            //short? dat = get_filter_table(chips, (int)this.filter[ch].table_pos);
            Short dat = getFilterTable(registerMap[(ch << 1) + 0xda]);
            if (dat != null) {
                for (int i = 0; i < 95; i++) {
                    //dat = get_filter_table(chips, (int)(this.filter[ch].table_pos + i));
                    dat = getFilterTable(registerMap[(ch << 1) + 0xda] + i);
                    if (dat == null) break;
                    this.filter[ch].taps[i] = dat;
                }
            }
        }

        //this.state = this.next_state = (int)State.NORMAL1;
        this.state = registerMap[0xe3] = State.NORMAL1.v;
    }

    // Updates filter parameters for mode 2
    private void state_refresh_filter_2() {
        for (int ch = 0; ch < 2; ch++) {
            this.filter[ch].delayPos = 0;
            this.filter[ch].tapCount = 45;

            //table = get_filter_table(chips, (int)this.filter[ch].table_pos);
            //if (table != null)
            // for (int i = 0; i < 45; i++) this.filter[ch].taps[i] = table[i];

            Short dat = getFilterTable(registerMap[(ch << 1) + 0xda]);
            if (dat != null) {
                for (int i = 0; i < 45; i++) {
                    dat = getFilterTable(registerMap[(ch << 1) + 0xda] + i);
                    if (dat == null) break;
                    this.filter[ch].taps[i] = dat;
                }
            }

            this.altFilter[ch].delayPos = 0;
            this.altFilter[ch].tapCount = 44;

            //table = get_filter_table(chips, (int)this.alt_filter[ch].table_pos);
            //if (table != null)
            // for (int i = 0; i < 44; i++) this.alt_filter[ch].taps[i] = table[i];

            dat = getFilterTable(registerMap[(ch << 1) + 0xdb]);
            if (dat != null) {
                for (int i = 0; i < 44; i++) {
                    dat = getFilterTable(registerMap[(ch << 1) + 0xdb] + i);
                    if (dat == null) break;
                    this.altFilter[ch].taps[i] = dat;
                }
            }
        }

        this.state = registerMap[0xe3] = State.NORMAL2.v;
    }

    // Updates a PCM Voice. There are 16 voices, each are updated every sample
    // with full rate and volume control.
    private short updatePcm(int voiceNo, int echoOut) {
        //if (voiceNo != 2) return 0;

        //qsound_voice v = this.Voice[voiceNo];
        int new_phase;
        short output;

        if ((this.muteMask & (1 << voiceNo)) != 0)
            return 0;

        // Read sample from rom and apply volume
        //output = (short)((v.volume * get_sample(chips, v.bank, v.addr)) >> 14);
        output = (short) ((registerMap[(voiceNo << 3) + 6]
                * get_sample(registerMap[(((voiceNo - 1 + 16) % 16) << 3) + 0], registerMap[(voiceNo << 3) + 1])) >> 14);
        //Debug.printf("output:%d vadr:%d", output, register_map[(voiceNo << 3) + 1]);

        //if (voiceNo == 2) {
        //MDSound.debugMsg = String.format("%d:%d:%d:%d",
        //register_map[(voiceNo << 3) + 6], register_map[(voiceNo << 3) + 0], register_map[(voiceNo << 3) + 1], register_map[(voiceNo << 3) + 5]);
        //}

        //echoOut += (output * v.echo) << 2;
        echoOut += (output * registerMap[voiceNo + 0xba]) << 2;

        // Add delta to the phase and loop back if required
        //new_phase = v.rate + ((v.addr << 12) | (v.phase >> 4));
        int a = (registerMap[(voiceNo << 3) + 1] << 12) | (registerMap[(voiceNo << 3) + 3] >> 4);
        a = (a & 0x0800_0000) != 0 ? (a | 0xf000_0000) : a;
        new_phase = registerMap[(voiceNo << 3) + 2] + a;

        //if ((new_phase >> 12) >= v.end_addr)
        if ((new_phase >> 12) >= (short) registerMap[(voiceNo << 3) + 5]) {
            //new_phase -= (v.loop_len << 12);
            a = (registerMap[(voiceNo << 3) + 4] << 12);
            a = (a & 0x0800_0000) != 0 ? (a | 0xf000_0000) : a;
            new_phase -= a;// (register_map[(voiceNo << 3) + 4] << 12);
        }

        //if (voiceNo == 0) {
        //Debug.printf("Bf:%d", new_phase);
        //}

        new_phase = clamp(new_phase, -0x800_0000, 0x7FF_FFFF);

        //if (voiceNo == 0) {
        //Debug.printf("Af:%d", new_phase);
        //}
        //v.addr = (int)(new_phase >> 12);
        registerMap[(voiceNo << 3) + 1] = new_phase >> 12;
        //v.phase = (int)((new_phase << 4) & 0xffff);
        registerMap[(voiceNo << 3) + 3] = (new_phase << 4) & 0xffff;

        return output;
    }

    // Updates an ADPCM Voice. There are 3 voices, one is updated every sample
    // (effectively making the ADPCM rate 1/3 of the master sample rate), and
    // volume is set when starting samples only.
    // The ADPCM algorithm is supposedly similar to Yamaha ADPCM. It also seems
    // like Capcom never used it, so this was not emulated in the earlier QSound
    // emulators.
    private void updateAdpcm(int voice_no, int nibble) {
        Adpcm v = this.adpcm[voice_no];

        int delta;
        byte step;

        if (this.muteMask != 0 & (1 << (16 + voice_no)) != 0) {
            this.voiceOutput[16 + voice_no] = 0;
            return;
        }

        if (nibble == 0) {
            // Mute Voice when it reaches the end address.
            //if (v.cur_addr == v.end_addr)
            if (v.cur_addr == registerMap[(voice_no << 2) + 0xcb])
                v.cur_vol = 0;

            // Playback start flag
            //if (v.flag != 0)
            if (registerMap[voice_no + 0xd6] != 0) {
                this.voiceOutput[16 + voice_no] = 0;
                //v.flag = 0;
                registerMap[voice_no + 0xd6] = 0;
                v.step_size = 10;
                //v.cur_vol = v.volume;
                v.cur_vol = (short) registerMap[(voice_no << 2) + 0xcd];
                //v.cur_addr = v.start_addr;
                v.cur_addr = registerMap[(voice_no << 2) + 0xca];
            }

            // get top nibble
            //step = (byte)(get_sample(chips, v.bank, v.cur_addr) >> 8);
            step = (byte) (get_sample(registerMap[(voice_no << 2) + 0xcc], v.cur_addr) >> 8);
        } else {
            // get bottom nibble
            //step = (byte)(get_sample(chips, v.bank, v.cur_addr++) >> 4);
            step = (byte) (get_sample(registerMap[(voice_no << 2) + 0xcc], v.cur_addr++) >> 4);
        }

        // shift with sign extend
        step >>= 4;

        // delta = (0.5 + abs(v.step)) * v.step_size
        delta = ((1 + Math.abs(step << 1)) * v.step_size) >> 1;
        if (step <= 0)
            delta = -delta;
        delta += this.voiceOutput[16 + voice_no];
        delta = clamp((short) delta, -32768, 32767);

        this.voiceOutput[16 + voice_no] = (short) ((delta * v.cur_vol) >> 16);

        v.step_size = (short) ((adpcmStepTable[8 + step] * v.step_size) >> 6);
        v.step_size = (short) clamp(v.step_size, 1, 2000);
    }

    // Process a sample update
    private void state_normal_update() {
        int echoInput = 0;

        this.readyFlag = (byte) 0x80;

        // recalculate echo length
        if (this.state == State.NORMAL2.v)
            //this.echo.length = (short)(this.echo.end_pos - 0x53c);
            this.echo.length = (short) (registerMap[0xd9] - 0x53c);
        else
            //this.echo.length = (short)(this.echo.end_pos - 0x554);
            this.echo.length = (short) (registerMap[0xd9] - 0x554);

        this.echo.length = (short) clamp(this.echo.length, 0, 1024);

        // update PCM voices
        for (int v = 0; v < 16; v++)
            this.voiceOutput[v] = updatePcm(v, echoInput);

        // update ADPCM voices (one every third sample)
        updateAdpcm(this.stateCounter % 3, this.stateCounter / 3);

        short echoOutput = this.echo.echo(echoInput);

        // now, we do the magic stuff
        for (int ch = 0; ch < 2; ch++) {
            // Echo is output on the unfiltered component of the left channel and
            // the filtered component of the right channel.
            int wet = (ch == 1) ? echoOutput << 14 : 0;
            int dry = (ch == 0) ? echoOutput << 14 : 0;
            int output = 0;

            for (int v = 0; v < 19; v++) {
                //int pan_index = (int)(this.voice_pan[v] - 0x110);
                int pan_index = registerMap[v + 0x80] - 0x110;
                if (pan_index > 97)
                    pan_index = 97;

                // Apply different volume tables on the dry and wet inputs.
                dry -= (this.voiceOutput[v] * this.panTables[ch][Pan.DRY.v][pan_index]);
                wet -= (this.voiceOutput[v] * this.panTables[ch][Pan.WET.v][pan_index]);
            }

            // Saturate accumulated voices
            dry = clamp(dry, -0x1fffffff, 0x1fffffff) << 2;
            wet = clamp(wet, -0x1fffffff, 0x1fffffff) << 2;

            // Apply FIR filter on 'wet' input
            wet = this.filter[ch].fir((short) (wet >> 16));

            // in mode 2, we do this on the 'dry' input too
            if (this.state == State.NORMAL2.v)
                dry = this.altFilter[ch].fir((short) (dry >> 16));

            // output goes through a delay line and attenuation
            output = delay(false, ch, this.wet[ch], wet) + delay(true, ch, this.dry[ch], dry);

            // DSP round function
            output = ((output + 0x2000) & ~0x3fff) >> 14;
            this._out[ch] = (short) clamp(output, -0x7fff, 0x7fff);

            //if (this.delay_update != 0)
            if (registerMap[0xe2] != 0) {
                delayedUpdate(false, ch, this.wet[ch]);
                delayedUpdate(true, ch, this.dry[ch]);
            }
        }

        registerMap[0xe2] = 0;

        // after 6 samples, the next state is executed.
        this.stateCounter++;
        if (this.stateCounter > 5) {
            this.stateCounter = 0;
            this.state = registerMap[0xe3];
        }
    }

    // Apply delay line and component volume
    private int delay(boolean isDry, int ch, Delay d, int input) {
        int output;

        d.delayLine[d.writePos++] = (short) (input >> 16);
        if (d.writePos >= 51)
            d.writePos = 0;

        output = d.delayLine[d.readPos++] * registerMap[(ch << 1) + (isDry ? 0xe5 : 0xe4)];
        if (d.readPos >= 51)
            d.readPos = 0;

        return output;
    }

    // Update the delay read position to match new delay length
    private void delayedUpdate(boolean isDry, int ch, Delay d) {
        short new_read_pos = (short) ((d.writePos - registerMap[(ch << 1) + (isDry ? 0xdf : 0xde)]) % 51);
        if (new_read_pos < 0)
            new_read_pos += 51;

        d.readPos = new_read_pos;
    }

    public int start(int clock) {
        this.romData = null;
        this.romSize = 0x00;
        this.romMask = 0x00;

        setMuteMask(0x00000);

        this.initPanTables();
        this.initRegisterMap();

        return clock / 2 / 1248;
    }

    public void reset() {
        this.readyFlag = 0;
        this._out[0] = this._out[1] = 0;
        this.state = 0;
        this.stateCounter = 0;
    }

    public void write(int offset, byte data) {
        switch (offset) {
        case 0:
            this.dataLatch = (this.dataLatch & 0x00ff) | (data << 8);
            break;
        case 1:
            this.dataLatch = (this.dataLatch & 0xff00) | data;
            break;
        case 2:
            writeData(data, this.dataLatch);
            break;
        default:
            break;
        }
    }

    public byte read(int offset) {
        return this.readyFlag;
    }

    public void writeData(byte address, int data) {
        int[] destination = registerMap;
        if (destination != null) destination[address] = data;
        this.readyFlag = 0;
    }

    public void update(int[][] outputs, int samples) {
        for (int curSmpl = 0; curSmpl < samples; curSmpl++) {
            outputs[0][curSmpl] = 0;
            outputs[1][curSmpl] = 0;

            this.update_sample();

            outputs[0][curSmpl] = this._out[0];
            outputs[1][curSmpl] = this._out[1];
        }
    }

    public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        if (this.romSize != romSize) {
            this.romData = new byte[romSize];
            this.romSize = romSize;
            Arrays.fill(this.romData, (byte) 0xff);
            this.romMask = 0xffff_ffff;
        }
        if (dataStart > romSize)
            return;
        if (dataStart + dataLength > romSize)
            dataLength = romSize - dataStart;

        if (dataLength >= 0)
            System.arraycopy(romData, srcStartAddress, this.romData, dataStart, dataLength);
    }

    public void setMuteMask(int muteMask) {
        this.muteMask = muteMask;
    }

    public void waitBusy() {
        while (this.readyFlag == 0) {
            this.update_sample();
        }
    }

    // byte EMU_CORE = 0x00;
    // fix broken optimization of old VGMs causing problems with the new core
    byte key_on_hack = 0x00;
    int[] start_addr_cache = new int[16];
    int[] pitch_cache = new int[16];
    int _data_latch;

    public int start2(int clock) {
        start_addr_cache = new int[16];
        pitch_cache = new int[16];

        if (clock < 10000000) {
            clock *= 15;
            key_on_hack = 1;
        }
        return start(clock);
    }

    public void write2(int offset, byte data) {
        if (key_on_hack != 0) {
            int ch;
            switch (offset) {
            // need to handle three cases, as vgm_cmp can remove writes to both phase and bank
            // registers, depending on version.
            // - start address was written before end/loop, but phase register is written
            // - as above, but phase is not written (we use bank as a backup then)
            // - Voice parameters are written during a note (we can't rewrite the address then)
            case 0:
                _data_latch = (_data_latch & 0x00ff) | (data << 8);
                break;
            case 1:
                _data_latch = (_data_latch & 0xff00) | data;
                break;
            case 2:
                if ((data & 0xff) > 0x7f)
                    break;
                ch = data >> 3;

                switch (data & 7) {
                case 1: // Start addr. write
                    start_addr_cache[ch] = _data_latch;
                    break;
                case 2: // Pitch write
                    // (old HLE assumed writing a non-zero value after a zero value was Key On)
                    if (pitch_cache[ch] == 0 && _data_latch != 0)
                        writeData((byte) ((ch << 3) + 1), start_addr_cache[ch]);
                    pitch_cache[ch] = _data_latch;
                    break;
                case 3: // Phase (old HLE also assumed this was Key On)
                    writeData((byte) ((ch << 3) + 1), start_addr_cache[ch]);
                    break;
                default:
                    break;
                }
                break;
            }
        }
        write(offset, data);

        // need to wait until the chips is ready before we start writing to it ...
        // we do this by time travel.
        if (offset == 2 && (data & 0xff) == 0xe3)
            waitBusy();
    }
}
