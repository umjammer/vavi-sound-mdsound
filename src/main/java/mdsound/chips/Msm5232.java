/*
 * license:GPL-2.0+
 *
 * copyright-holders: Jarek Burczynski, Hiromitsu Shioya
 */

package mdsound.chips;


/**
 * OKI MSM5232RS 8 channel tone generator
 *
 * @author Jarek Burczynski
 * @author Hiromitsu Shioya
 */
public class Msm5232 {

    public Msm5232() {
        initMSM5232_ROM();
    }

    private int clock = 0;

    private static class VOICE {

        private byte mode;

        private int TG_count_period;
        private int TG_count;

        private byte TG_cnt;     // 7 bits binary counter (frequency output)
        private byte TG_out16;   // bit number (of TG_cnt) for 16' output
        private byte TG_out8;    // bit number (of TG_cnt) for  8' output
        private byte TG_out4;    // bit number (of TG_cnt) for  4' output
        private byte TG_out2;    // bit number (of TG_cnt) for  2' output

        private int egvol;
        private int eg_sect;
        private int counter;
        private int eg;

        private byte eg_arm;     // attack/release mode

        private double ar_rate;
        private double dr_rate;
        private double rr_rate;

        private int pitch;      // current pitch data

        private int GF;
    }

    // internal state

    private VOICE[] m_voi = new VOICE[8];

    private int[] m_EN_out16 = new int[2]; // enable 16' output masks for both groups (0-disabled ; ~0 -enabled)
    private int[] m_EN_out8 = new int[2];  // enable 8'  output masks
    private int[] m_EN_out4 = new int[2];  // enable 4'  output masks
    private int[] m_EN_out2 = new int[2];  // enable 2'  output masks

    private int m_noise_cnt;
    private int m_noise_step;
    private int m_noise_rng;
    private int m_noise_clocks;     // number of the noise_rng (output) level changes

    private int m_UpdateStep;

    // rate tables
    private double[] m_ar_tbl = new double[8];
    private double[] m_dr_tbl = new double[16];

    private int m_control1;
    private int m_control2;

    private int m_gate;         // current state of the GATE output

    private int m_chip_clock;   // chip clock in Hz
    private int m_rate;         // sample rate in Hz

    private double[] m_external_capacitance = new double[8]; // in Farads, eg 0.39e-6 = 0.36 uF (microFarads)

    private final int CLOCK_RATE_DIVIDER = 16;

    //	DEFINE_DEVICE_TYPE(MSM5232, msm5232_device, "msm5232", "MSM5232")

    /**
     * device-specific startup
     */
    public void device_start() {
        int rate = clock / CLOCK_RATE_DIVIDER;

        init(clock, rate);
    }

    /**
     * device-specific reset
     */
    public void device_reset() {
        for (int i = 0; i < 8; i++) {
            write(i, 0x80);
            write(i, 0x00);
        }
        m_noise_cnt = 0;
        m_noise_rng = 1;
        m_noise_clocks = 0;

        m_control1 = 0;
        m_EN_out16[0] = 0;
        m_EN_out8[0] = 0;
        m_EN_out4[0] = 0;
        m_EN_out2[0] = 0;

        m_control2 = 0;
        m_EN_out16[1] = 0;
        m_EN_out8[1] = 0;
        m_EN_out4[1] = 0;
        m_EN_out2[1] = 0;

        gate_update();
    }

    /**
     * device-specific stop
     */
    public void device_stop() {
    }

    private void set_capacitors(double cap1, double cap2, double cap3, double cap4, double cap5, double cap6, double cap7, double cap8) {
        m_external_capacitance[0] = cap1;
        m_external_capacitance[1] = cap2;
        m_external_capacitance[2] = cap3;
        m_external_capacitance[3] = cap4;
        m_external_capacitance[4] = cap5;
        m_external_capacitance[5] = cap6;
        m_external_capacitance[6] = cap7;
        m_external_capacitance[7] = cap8;
    }

    // Default chip clock is 2119040 Hz
    // At this clock chip generates exactly 440.0 Hz signal on 8' output when pitch data=0x21

    // ROM table to convert from pitch data into data for programmable counter and binary counter
    // Chip has 88x12bits ROM   (addressing (in hex) from 0x00 to 0x57)
    private short ROM(int counter, int bindiv) {
        return (short) (counter | (bindiv << 9));
    }

    private short[] MSM5232_ROM = new short[88];

    private void initMSM5232_ROM() {
        // higher values are Programmable Counter data (9 bits)
        // lesser values are Binary Counter shift data (3 bits)

        /* 0 */
        MSM5232_ROM[0x00] = ROM(506, 7);
        /* 1 */
        MSM5232_ROM[0x01] = ROM(478, 7);
        /* 2 */
        MSM5232_ROM[0x02] = ROM(451, 7);
        /* 3 */
        MSM5232_ROM[0x03] = ROM(426, 7);
        /* 4 */
        MSM5232_ROM[0x04] = ROM(402, 7);
        /* 5 */
        MSM5232_ROM[0x05] = ROM(379, 7);
        /* 6 */
        MSM5232_ROM[0x06] = ROM(358, 7);
        /* 7 */
        MSM5232_ROM[0x07] = ROM(338, 7);
        /* 8 */
        MSM5232_ROM[0x08] = ROM(319, 7);
        /* 9 */
        MSM5232_ROM[0x09] = ROM(301, 7);
        /* A */
        MSM5232_ROM[0x0a] = ROM(284, 7);
        /* B */
        MSM5232_ROM[0x0b] = ROM(268, 7);
        /* C */
        MSM5232_ROM[0x0c] = ROM(253, 7);
        /* D */
        MSM5232_ROM[0x0d] = ROM(478, 6);
        /* E */
        MSM5232_ROM[0x0e] = ROM(451, 6);
        /* F */
        MSM5232_ROM[0x0f] = ROM(426, 6);
        /*10 */
        MSM5232_ROM[0x10] = ROM(402, 6);
        /*11 */
        MSM5232_ROM[0x11] = ROM(379, 6);
        /*12 */
        MSM5232_ROM[0x12] = ROM(358, 6);
        /*13 */
        MSM5232_ROM[0x13] = ROM(338, 6);
        /*14 */
        MSM5232_ROM[0x14] = ROM(319, 6);
        /*15 */
        MSM5232_ROM[0x15] = ROM(301, 6);
        /*16 */
        MSM5232_ROM[0x16] = ROM(284, 6);
        /*17 */
        MSM5232_ROM[0x17] = ROM(268, 6);
        /*18 */
        MSM5232_ROM[0x18] = ROM(253, 6);
        /*19 */
        MSM5232_ROM[0x19] = ROM(478, 5);
        /*1A */
        MSM5232_ROM[0x1a] = ROM(451, 5);
        /*1B */
        MSM5232_ROM[0x1b] = ROM(426, 5);
        /*1C */
        MSM5232_ROM[0x1c] = ROM(402, 5);
        /*1D */
        MSM5232_ROM[0x1d] = ROM(379, 5);
        /*1E */
        MSM5232_ROM[0x1e] = ROM(358, 5);
        /*1F */
        MSM5232_ROM[0x1f] = ROM(338, 5);
        /*20 */
        MSM5232_ROM[0x20] = ROM(319, 5);
        /*21 */
        MSM5232_ROM[0x21] = ROM(301, 5);
        /*22 */
        MSM5232_ROM[0x22] = ROM(284, 5);
        /*23 */
        MSM5232_ROM[0x23] = ROM(268, 5);
        /*24 */
        MSM5232_ROM[0x24] = ROM(253, 5);
        /*25 */
        MSM5232_ROM[0x25] = ROM(478, 4);
        /*26 */
        MSM5232_ROM[0x26] = ROM(451, 4);
        /*27 */
        MSM5232_ROM[0x27] = ROM(426, 4);
        /*28 */
        MSM5232_ROM[0x28] = ROM(402, 4);
        /*29 */
        MSM5232_ROM[0x29] = ROM(379, 4);
        /*2A */
        MSM5232_ROM[0x2a] = ROM(358, 4);
        /*2B */
        MSM5232_ROM[0x2b] = ROM(338, 4);
        /*2C */
        MSM5232_ROM[0x2c] = ROM(319, 4);
        /*2D */
        MSM5232_ROM[0x2d] = ROM(301, 4);
        /*2E */
        MSM5232_ROM[0x2e] = ROM(284, 4);
        /*2F */
        MSM5232_ROM[0x2f] = ROM(268, 4);
        /*30 */
        MSM5232_ROM[0x30] = ROM(253, 4);
        /*31 */
        MSM5232_ROM[0x31] = ROM(478, 3);
        /*32 */
        MSM5232_ROM[0x32] = ROM(451, 3);
        /*33 */
        MSM5232_ROM[0x33] = ROM(426, 3);
        /*34 */
        MSM5232_ROM[0x34] = ROM(402, 3);
        /*35 */
        MSM5232_ROM[0x35] = ROM(379, 3);
        /*36 */
        MSM5232_ROM[0x36] = ROM(358, 3);
        /*37 */
        MSM5232_ROM[0x37] = ROM(338, 3);
        /*38 */
        MSM5232_ROM[0x38] = ROM(319, 3);
        /*39 */
        MSM5232_ROM[0x39] = ROM(301, 3);
        /*3A */
        MSM5232_ROM[0x3a] = ROM(284, 3);
        /*3B */
        MSM5232_ROM[0x3b] = ROM(268, 3);
        /*3C */
        MSM5232_ROM[0x3c] = ROM(253, 3);
        /*3D */
        MSM5232_ROM[0x3d] = ROM(478, 2);
        /*3E */
        MSM5232_ROM[0x3e] = ROM(451, 2);
        /*3F */
        MSM5232_ROM[0x3f] = ROM(426, 2);
        /*40 */
        MSM5232_ROM[0x40] = ROM(402, 2);
        /*41 */
        MSM5232_ROM[0x41] = ROM(379, 2);
        /*42 */
        MSM5232_ROM[0x42] = ROM(358, 2);
        /*43 */
        MSM5232_ROM[0x43] = ROM(338, 2);
        /*44 */
        MSM5232_ROM[0x44] = ROM(319, 2);
        /*45 */
        MSM5232_ROM[0x45] = ROM(301, 2);
        /*46 */
        MSM5232_ROM[0x46] = ROM(284, 2);
        /*47 */
        MSM5232_ROM[0x47] = ROM(268, 2);
        /*48 */
        MSM5232_ROM[0x48] = ROM(253, 2);
        /*49 */
        MSM5232_ROM[0x49] = ROM(478, 1);
        /*4A */
        MSM5232_ROM[0x4a] = ROM(451, 1);
        /*4B */
        MSM5232_ROM[0x4b] = ROM(426, 1);
        /*4C */
        MSM5232_ROM[0x4c] = ROM(402, 1);
        /*4D */
        MSM5232_ROM[0x4d] = ROM(379, 1);
        /*4E */
        MSM5232_ROM[0x4e] = ROM(358, 1);
        /*4F */
        MSM5232_ROM[0x4f] = ROM(338, 1);
        /*50 */
        MSM5232_ROM[0x50] = ROM(319, 1);
        /*51 */
        MSM5232_ROM[0x51] = ROM(301, 1);
        /*52 */
        MSM5232_ROM[0x52] = ROM(284, 1);
        /*53 */
        MSM5232_ROM[0x53] = ROM(268, 1);
        /*54 */
        MSM5232_ROM[0x54] = ROM(253, 1);
        /*55 */
        MSM5232_ROM[0x55] = ROM(253, 1);
        /*56 */
        MSM5232_ROM[0x56] = ROM(253, 1);
        /*57 */
        MSM5232_ROM[0x57] = ROM(13, 7);
    }
//#undef ROM

    private int STEP_SH = (16);    /* step calculations accuracy */

    /*
     * Resistance values are guesswork, default capacitance is mentioned in the datasheets
     *
     * Two errors in the datasheet, one probable, one certain
     * - it mentions 0.39uF caps, but most boards have 1uF caps and expect datasheet timings
     *
     * - the 330ms timing of decay2 has been measured to be 250ms (which
     *   also matches the duty cycle information for the rest of the table)
     *
     * In both cases it ends up with smaller resistor values, which are
     * easier to do on-die.
     *
     * The timings are for a 90% charge/discharge of the external
     * capacitor through three possible resistors, one for attack, two for
     * decay.
     *
     * Expected timings are 2ms, 40ms and 250ms respectively with a 1uF
     * capacitor.
     *
     * exp(-t/(r*c)) = (100% - 90%) => r = -r/(log(0.1)*c)
     *
     *   2ms ->    870 ohms
     *  40ms ->  17400 ohms
     * 250ms -> 101000 ohms
     */

    private final double R51 = 870;    // attack resistance
    private final double R52 = 17400;    // decay 1 resistance
    private final double R53 = 101000;    // decay 2 resistance

    private void init_tables() {
        // sample rate = chip clock !!!  But :
        // highest possible frequency is chipclock/13/16 (pitch data=0x57)
        // at 2MHz : 2000000/13/16 = 9615 Hz

        m_UpdateStep = (int) (int) ((double) (1 << STEP_SH) * (double) (m_rate) / (double) (m_chip_clock));
        //logger.log(Level.TRACE, "clock=%i Hz rate=%i Hz, UpdateStep=%i\n", m_chip_clock, m_rate, m_UpdateStep);

        double scale = (double) (m_chip_clock) / (double) (m_rate);
        m_noise_step = (int) (((1 << STEP_SH) / 128.0) * scale); // step of the rng reg in 16.16 format
        //logger.log(Level.TRACE, "noise step=%8x\n", m_noise_step);

        for (int i = 0; i < 8; i++) {
            double clockscale = (double) (m_chip_clock) / 2119040.0;
            int rcp_duty_cycle = 1 << ((i & 4) != 0 ? (i & ~2) : i); // bit 1 is ignored if bit 2 is set
            m_ar_tbl[i] = (rcp_duty_cycle / clockscale) * R51;
        }

        for (int i = 0; i < 8; i++) {
            double clockscale = (double) (m_chip_clock) / 2119040.0;
            int rcp_duty_cycle = 1 << ((i & 4) != 0 ? (i & ~2) : i); // bit 1 is ignored if bit 2 is set
            m_dr_tbl[i] = (rcp_duty_cycle / clockscale) * R52;
            m_dr_tbl[i + 8] = (rcp_duty_cycle / clockscale) * R53;
        }
    }

    private void init_voice(int i) {
        m_voi[i].ar_rate = m_ar_tbl[0] * m_external_capacitance[i];
        m_voi[i].dr_rate = m_dr_tbl[0] * m_external_capacitance[i];
        m_voi[i].rr_rate = m_dr_tbl[0] * m_external_capacitance[i]; /* this is finalant value */
        m_voi[i].eg_sect = -1;
        m_voi[i].eg = 0;
        m_voi[i].eg_arm = 0;
        m_voi[i].pitch = -1;
    }

    private void gate_update() {
        int new_state = (m_control2 & 0x20) != 0 ? m_voi[7].GF : 0;

        if (m_gate != new_state) {
            m_gate = new_state;
            //m_gate_handler_cb(new_state);
        }
    }

    public void init(int clock, int rate) {
        m_chip_clock = clock;
        m_rate = rate != 0 ? rate : 44100; // avoid division by 0

        init_tables();

        for (int j = 0; j < 8; j++) {
            m_voi[j] = new VOICE();
            init_voice(j);
        }
    }

    private void write(int offset, int data) {
        if (offset > 0x0d)
            return;

        //m_stream.update();

        if (offset < 0x08) { // pitch
            int ch = offset & 7;

            m_voi[ch].GF = ((data & 0x80) >> 7);
            if (ch == 7)
                gate_update();

            if ((data & 0x80) != 0) {
                if (data >= 0xd8) {
//if ((data&0x7f) != 0x5f) logger.log(Level.TRACE, "MSM5232: WRONG PITCH CODE = %2x\n",data&0x7f);
                    m_voi[ch].mode = 1;     /* noise mode */
                    m_voi[ch].eg_sect = 0;  /* Key On */
                } else {
                    if (m_voi[ch].pitch != (data & 0x7f)) {
                        int n;
                        short pg;

                        m_voi[ch].pitch = data & 0x7f;

                        pg = MSM5232_ROM[data & 0x7f];

                        m_voi[ch].TG_count_period = (int) ((pg & 0x1ff) * m_UpdateStep / 2);

                        n = (pg >> 9) & 7; /* n = bit number for 16' output */
                        m_voi[ch].TG_out16 = (byte) (1 << n);
                        /* for 8' it is bit n-1 (bit 0 if n-1<0) */
                        /* for 4' it is bit n-2 (bit 0 if n-2<0) */
                        /* for 2' it is bit n-3 (bit 0 if n-3<0) */
                        n = (n > 0) ? n - 1 : 0;
                        m_voi[ch].TG_out8 = (byte) (1 << n);

                        n = (n > 0) ? n - 1 : 0;
                        m_voi[ch].TG_out4 = (byte) (1 << n);

                        n = (n > 0) ? n - 1 : 0;
                        m_voi[ch].TG_out2 = (byte) (1 << n);
                    }
                    m_voi[ch].mode = 0;     /* tone mode */
                    m_voi[ch].eg_sect = 0;  /* Key On */
                }
            } else {
                if (m_voi[ch].eg_arm == 0)    /* arm = 0 */
                    m_voi[ch].eg_sect = 2;  /* Key Off -> go to release */
                else                            /* arm = 1 */
                    m_voi[ch].eg_sect = 1;  /* Key Off -> go to decay */
            }
        } else {
            int i;
            switch (offset) {
                case 0x08:  /* group1 attack */
                    for (i = 0; i < 4; i++)
                        m_voi[i].ar_rate = m_ar_tbl[data & 0x7] * m_external_capacitance[i];
                    break;

                case 0x09:  /* group2 attack */
                    for (i = 0; i < 4; i++)
                        m_voi[i + 4].ar_rate = m_ar_tbl[data & 0x7] * m_external_capacitance[i + 4];
                    break;

                case 0x0a:  /* group1 decay */
                    for (i = 0; i < 4; i++)
                        m_voi[i].dr_rate = m_dr_tbl[data & 0xf] * m_external_capacitance[i];
                    break;

                case 0x0b:  /* group2 decay */
                    for (i = 0; i < 4; i++)
                        m_voi[i + 4].dr_rate = m_dr_tbl[data & 0xf] * m_external_capacitance[i + 4];
                    break;

                case 0x0c:  /* group1 control */

                        /*if (m_control1 != data)
                            logger.log(Level.TRACE, "msm5232: control1 ctrl=%x OE=%x\n", data&0xf0, data&0x0f);*/

                        /*if (data & 0x10)
                            popmessage("msm5232: control1 ctrl=%2x\n", data);*/

                    m_control1 = data;

                    for (i = 0; i < 4; i++) {
                        if ((data & 0x10) != 0 && (m_voi[i].eg_sect == 1))
                            m_voi[i].eg_sect = 0;
                        m_voi[i].eg_arm = (byte) (data & 0x10);
                    }

                    m_EN_out16[0] = (int) ((data & 1) != 0 ? ~0 : 0);
                    m_EN_out8[0] = (int) ((data & 2) != 0 ? ~0 : 0);
                    m_EN_out4[0] = (int) ((data & 4) != 0 ? ~0 : 0);
                    m_EN_out2[0] = (int) ((data & 8) != 0 ? ~0 : 0);

                    break;

                case 0x0d:  /* group2 control */

                        /*if (m_control2 != data)
                            logger.log(Level.TRACE, "msm5232: control2 ctrl=%x OE=%x\n", data&0xf0, data&0x0f);*/

                        /*if (data & 0x10)
                            popmessage("msm5232: control2 ctrl=%2x\n", data);*/

                    m_control2 = data;
                    gate_update();

                    for (i = 0; i < 4; i++) {
                        if ((data & 0x10) != 0 && (m_voi[i + 4].eg_sect == 1))
                            m_voi[i + 4].eg_sect = 0;
                        m_voi[i + 4].eg_arm = (byte) (data & 0x10);
                    }

                    m_EN_out16[1] = (int) ((data & 1) != 0 ? ~0 : 0);
                    m_EN_out8[1] = (int) ((data & 2) != 0 ? ~0 : 0);
                    m_EN_out4[1] = (int) ((data & 4) != 0 ? ~0 : 0);
                    m_EN_out2[1] = (int) ((data & 8) != 0 ? ~0 : 0);

                    break;
            }
        }
    }

    private final int VMIN = 0;
    private final int VMAX = 32768;

    private void EG_voices_advance() {
        int samplerate = m_rate;
        int i, j = 0;

        i = 8;
        do {
            VOICE voi = m_voi[j];
            switch (voi.eg_sect) {
                case 0: /* attack */
                    /* capacitor charge */
                    if (voi.eg < VMAX) {
                        voi.counter -= (int) ((VMAX - voi.eg) / voi.ar_rate);
                        if (voi.counter <= 0) {
                            int n = -voi.counter / samplerate + 1;
                            voi.counter += n * samplerate;
                            if ((voi.eg += n) > VMAX)
                                voi.eg = VMAX;
                        }
                    }
                    /* when ARM=0, EG switches to decay as soon as cap is charged to VT (EG inversion voltage; about 80% of MAX) */
                    if (voi.eg_arm == 0) {
                        if (voi.eg >= VMAX * 80 / 100) {
                            voi.eg_sect = 1;
                        }
                    } else
                        /* ARM=1 */ {
                        /* when ARM=1, EG stays at maximum until key off */
                    }
                    voi.egvol = voi.eg / 16; /*32768/16 = 2048 max*/
                    break;

                case 1: /* decay */
                    /* capacitor discharge */
                    if (voi.eg > VMIN) {
                        voi.counter -= (int) ((voi.eg - VMIN) / voi.dr_rate);
                        if (voi.counter <= 0) {
                            int n = -voi.counter / samplerate + 1;
                            voi.counter += n * samplerate;
                            if ((voi.eg -= n) < VMIN)
                                voi.eg = VMIN;
                        }
                    } else /* voi->eg <= VMIN */ {
                        voi.eg_sect = -1;
                    }
                    voi.egvol = voi.eg / 16; /*32768/16 = 2048 max*/
                    break;

                case 2: /* release */

                    /* capacitor discharge */
                    if (voi.eg > VMIN) {
                        voi.counter -= (int) ((voi.eg - VMIN) / voi.rr_rate);
                        if (voi.counter <= 0) {
                            int n = -voi.counter / samplerate + 1;
                            voi.counter += n * samplerate;
                            if ((voi.eg -= n) < VMIN)
                                voi.eg = VMIN;
                        }
                    } else /* voi->eg <= VMIN */ {
                        voi.eg_sect = -1;
                    }

                    voi.egvol = voi.eg / 16; /*32768/16 = 2048 max*/

                    break;

                default:
                    break;
            }

            j++;
            i--;
        } while (i > 0);

    }

    private int o2, o4, o8, o16, solo8, solo16;

    private void TG_group_advance(int groupIdx) {
        int i, j = groupIdx * 4;

        o2 = o4 = o8 = o16 = solo8 = solo16 = 0;

        i = 4;
        do {
            VOICE voi = m_voi[j];
            int out2, out4, out8, out16;

            out2 = out4 = out8 = out16 = 0;

            if (voi.mode == 0)   /* generate square tone */ {
                int left = 1 << STEP_SH;
                do {
                    int nextevent = left;

                    if ((voi.TG_cnt & voi.TG_out16) != 0) out16 += voi.TG_count;
                    if ((voi.TG_cnt & voi.TG_out8) != 0) out8 += voi.TG_count;
                    if ((voi.TG_cnt & voi.TG_out4) != 0) out4 += voi.TG_count;
                    if ((voi.TG_cnt & voi.TG_out2) != 0) out2 += voi.TG_count;

                    voi.TG_count -= nextevent;

                    while (voi.TG_count <= 0) {
                        voi.TG_count += voi.TG_count_period;
                        voi.TG_cnt++;
                        if ((voi.TG_cnt & voi.TG_out16) != 0) out16 += voi.TG_count_period;
                        if ((voi.TG_cnt & voi.TG_out8) != 0) out8 += voi.TG_count_period;
                        if ((voi.TG_cnt & voi.TG_out4) != 0) out4 += voi.TG_count_period;
                        if ((voi.TG_cnt & voi.TG_out2) != 0) out2 += voi.TG_count_period;

                        if (voi.TG_count > 0)
                            break;

                        voi.TG_count += voi.TG_count_period;
                        voi.TG_cnt++;
                        if ((voi.TG_cnt & voi.TG_out16) != 0) out16 += voi.TG_count_period;
                        if ((voi.TG_cnt & voi.TG_out8) != 0) out8 += voi.TG_count_period;
                        if ((voi.TG_cnt & voi.TG_out4) != 0) out4 += voi.TG_count_period;
                        if ((voi.TG_cnt & voi.TG_out2) != 0) out2 += voi.TG_count_period;
                    }
                    if ((voi.TG_cnt & voi.TG_out16) != 0) out16 -= voi.TG_count;
                    if ((voi.TG_cnt & voi.TG_out8) != 0) out8 -= voi.TG_count;
                    if ((voi.TG_cnt & voi.TG_out4) != 0) out4 -= voi.TG_count;
                    if ((voi.TG_cnt & voi.TG_out2) != 0) out2 -= voi.TG_count;

                    left -= nextevent;

                } while (left > 0);
            } else    /* generate noise */ {
                if ((m_noise_clocks & 8) != 0) out16 += (1 << STEP_SH);
                if ((m_noise_clocks & 4) != 0) out8 += (1 << STEP_SH);
                if ((m_noise_clocks & 2) != 0) out4 += (1 << STEP_SH);
                if ((m_noise_clocks & 1) != 0) out2 += (1 << STEP_SH);
            }

            /* calculate signed output */
            o16 += ((out16 - (1 << (STEP_SH - 1))) * voi.egvol) >> STEP_SH;
            o8 += ((out8 - (1 << (STEP_SH - 1))) * voi.egvol) >> STEP_SH;
            o4 += ((out4 - (1 << (STEP_SH - 1))) * voi.egvol) >> STEP_SH;
            o2 += ((out2 - (1 << (STEP_SH - 1))) * voi.egvol) >> STEP_SH;

            if (i == 1 && groupIdx == 1) {
                solo16 += ((out16 - (1 << (STEP_SH - 1))) << 11) >> STEP_SH;
                solo8 += ((out8 - (1 << (STEP_SH - 1))) << 11) >> STEP_SH;
            }

            j++;
            i--;
        } while (i > 0);

        /* cut off disabled output lines */
        o16 &= (int) m_EN_out16[groupIdx];
        o8 &= (int) m_EN_out8[groupIdx];
        o4 &= (int) m_EN_out4[groupIdx];
        o2 &= (int) m_EN_out2[groupIdx];
    }

    /* MAME Interface */
    private void device_post_load() {
        init_tables();
    }

    private void set_clock(int clock) {
        if (m_chip_clock != clock) {
            //m_stream->update();
            m_chip_clock = clock;
            m_rate = clock / CLOCK_RATE_DIVIDER;
            init_tables();
            //m_stream->set_sample_rate(m_rate);
        }
    }

    /**
     * handle a stream update
     */
    private void sound_stream_update(int[][] outputs, int length) {
        int[] buf1 = outputs[0];
        int[] buf2 = outputs[1];
        int[] buf3 = outputs[2];
        int[] buf4 = outputs[3];
        int[] buf5 = outputs[4];
        int[] buf6 = outputs[5];
        int[] buf7 = outputs[6];
        int[] buf8 = outputs[7];
        int[] bufsolo1 = outputs[8];
        int[] bufsolo2 = outputs[9];
        int[] bufnoise = outputs[10];
        int i;

        for (i = 0; i < length; i++) {
            /* calculate all voices' envelopes */
            EG_voices_advance();

            TG_group_advance(0);   /* calculate tones group 1 */
            buf1[i] = Math.min(Math.max(o2, -32767), 32768);
            buf2[i] = Math.min(Math.max(o4, -32767), 32768);
            buf3[i] = Math.min(Math.max(o8, -32767), 32768);
            buf4[i] = Math.min(Math.max(o16, -32767), 32768);
            //SAVE_SINGLE_CHANNEL(0, o2);
            //SAVE_SINGLE_CHANNEL(1, o4);
            //SAVE_SINGLE_CHANNEL(2, o8);
            //SAVE_SINGLE_CHANNEL(3, o16);

            TG_group_advance(1);   /* calculate tones group 2 */
            buf5[i] = Math.min(Math.max(o2, -32767), 32768);
            buf6[i] = Math.min(Math.max(o4, -32767), 32768);
            buf7[i] = Math.min(Math.max(o8, -32767), 32768);
            buf8[i] = Math.min(Math.max(o16, -32767), 32768);
            bufsolo1[i] = Math.min(Math.max(solo8, -32767), 32768);
            bufsolo2[i] = Math.min(Math.max(solo16, -32767), 32768);
            //SAVE_SINGLE_CHANNEL(4, o2)
            //SAVE_SINGLE_CHANNEL(5, o4)
            //SAVE_SINGLE_CHANNEL(6, o8)
            //SAVE_SINGLE_CHANNEL(7, o16)

            //SAVE_ALL_CHANNELS

            /* update noise generator */
            {
                int cnt = (m_noise_cnt += m_noise_step) >> STEP_SH;
                m_noise_cnt &= ((1 << STEP_SH) - 1);
                while (cnt > 0) {
                    int tmp = m_noise_rng & (1 << 16);        /* store current level */

                    if ((m_noise_rng & 1) != 0)
                        m_noise_rng ^= 0x24000;
                    m_noise_rng >>= 1;

                    if ((m_noise_rng & (1 << 16)) != tmp)   /* level change detect */
                        m_noise_clocks++;

                    cnt--;
                }
            }

            bufnoise[i] = (int) ((m_noise_rng & (1 << 16)) != 0 ? 32768.0 : 0.0);
        }
    }
}
