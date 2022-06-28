/*
 * Gens: PWM audio emulator.
 *
 * Copyright (c) 1999-2002 by St駱hane Dallongeville
 * Copyright (c) 2003-2004 by St駱hane Akhoun
 * Copyright (c) 2008-2009 by David Korth
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package mdsound;

public class Pwm extends Instrument.BaseInstrument {
    private static final int BUF_SIZE = 4;

//    private static final int CHILLY_WILLY_SCALE = 1;

    private static final byte[] FULL_TAB = new byte[] {
            0x40, 0x00, 0x00, (byte) 0x80,
            (byte) 0x80, 0x40, 0x00, 0x00,
            0x00, (byte) 0x80, 0x40, 0x00,
            0x00, 0x00, (byte) 0x80, 0x40,
    };

    private static class PwmChip {
        //#if CHILLY_WILLY_SCALE
        // TODO: Fix Chilly Willy's new scaling algorithm.
        private static final int PWM_Loudness = 0;
        //#endif

        public int[] fifoR = new int[8];
        public int[] fifoL = new int[8];
        public int rpR;
        public int wpR;
        public int rpL;
        public int wpL;
        public int cycles = 0;
        public int cycle;
        public int cycleCnt;
        public int int_;
        public int intCnt;
        public int mode;
        //unsigned int PWM_Enable;
        public int outR;
        public int outL;

        public int cycleTmp;
        public int cyclesTmp = 0;
        public int intTmp;
        public int fifoLTmp;
        public int fifoRTmp;

        //#if CHILLY_WILLY_SCALE
        // TODO: Fix Chilly Willy's new scaling algorithm.
        /** PWM scaling variables. */
        public int offset;
        public int scale;
        //int loudness;
        //#endif

        public int clock;

        /**
         * Initialize the PWM audio emulator.
         */
        private void init() {
            this.mode = 0;
            this.outR = 0;
            this.outL = 0;

            for (int i = 0; i < 8; i++) {
                this.fifoR[i] = 0x00;
                this.fifoL[i] = 0x00;
            }
            this.rpR = 0;
            this.wpR = 0;
            this.rpL = 0;
            this.wpL = 0;
            this.cycleTmp = 0;
            this.intTmp = 0;
            this.fifoLTmp = 0;
            this.fifoRTmp = 0;

            //PWM_Loudness = 0;
            setCycle(0);
            setInt(0);
        }

//#if CHILLY_WILLY_SCALE
        // TODO: Fix Chilly Willy's new scaling algorithm.
        private void recalcScale() {
            this.offset = (this.cycle / 2) + 1;
            this.scale = (0x7FFF00 / this.offset);
        }
//#endif

        private void setCycle(int cycle) {
            cycle--;
            this.cycle = (cycle & 0xFFF);
            this.cycleCnt = this.cycles;

//#if CHILLY_WILLY_SCALE
            // TODO: Fix Chilly Willy's new scaling algorithm.
            recalcScale();
//#endif
        }

        private void setInt(int int_time) {
            int_time &= 0x0F;
            if (int_time != 0)
                this.int_ = this.intCnt = int_time;
            else
                this.int_ = this.intCnt = 16;
        }

        private void clearTimer() {
            this.cycleCnt = 0;
        }

        /*
         * Shift PWM data.
         *
         * @param src Channel (L or R) with the source data.
         * @param dest Channel (L or R) for the destination.
         */
        //void PWM_SHIFT(src, dest) {
        // /* Make sure the source FIFO isn't empty.
        // if (PWM_RP_##src != PWM_WP_##src)        \
        // {            \
        //  /* Get destination channel output from the source channel FIFO. */   \
        //  PWM_Out_##dest = PWM_FIFO_##src[PWM_RP_##src];      \
        //             \
        //  /* Increment the source channel read pointer, resetting to 0 if it overflows. */ \
        //  PWM_RP_##src = (PWM_RP_##src + 1) & (PWM_BUF_SIZE - 1);     \
        // }            \
        //}

        /*static void PWM_Shift_Data(void) {
            switch (PWM_Mode & 0x0F) {
                case 0x01:
                case 0x0D:
                    // Rx_LL: Right . Ignore, Left . Left
                    PWM_SHIFT(L, L);
                    break;

                case 0x02:
                case 0x0E:
                    // Rx_LR: Right . Ignore, Left . Right
                    PWM_SHIFT(L, R);
                    break;

                case 0x04:
                case 0x07:
                    // RL_Lx: Right . Left, Left . Ignore
                    PWM_SHIFT(R, L);
                    break;

                case 0x05:
                case 0x09:
                    // RR_LL: Right . Right, Left . Left
                    PWM_SHIFT(L, L);
                    PWM_SHIFT(R, R);
                    break;

                case 0x06:
                case 0x0A:
                    // RL_LR: Right . Left, Left . Right
                    PWM_SHIFT(L, R);
                    PWM_SHIFT(R, L);
                    break;

                case 0x08:
                case 0x0B:
                    // RR_Lx: Right . Right, Left . Ignore
                    PWM_SHIFT(R, R);
                    break;

                case 0x00:
                case 0x03:
                case 0x0C:
                case 0x0F:
                default:
                    // Rx_Lx: Right . Ignore, Left . Ignore
                    break;
            }
        }

        void PWM_Update_Timer(unsigned int cycle) {
            // Don't do anything if PWM is disabled in the Sound menu.

            // Don't do anything if PWM isn't active.
            if ((PWM_Mode & 0x0F) == 0x00)
                return;

            if (PWM_Cycle == 0x00 || (PWM_Cycle_Cnt > cycle))
                return;

            PWM_Shift_Data();

            PWM_Cycle_Cnt += PWM_Cycle;

            PWM_Int_Cnt--;
            if (PWM_Int_Cnt == 0) {
                PWM_Int_Cnt = PWM_Int;

                if (PWM_Mode & 0x0080) {
                    // RPT => generate DREQ1 as well as INT
                    SH2_DMA1_Request(&M_SH2, 1);
                    SH2_DMA1_Request(&S_SH2, 1);
                }

                if (_32X_MINT & 1)
                    SH2_Interrupt(&M_SH2, 6);
                if (_32X_SINT & 1)
                    SH2_Interrupt(&S_SH2, 6);
            }
        }*/

        private int updateScale(int in) {
            if (in == 0)
                return 0;

            // TODO: Chilly Willy's new scaling algorithm breaks drx's Sonic 1 32X (with PWM drums).
//# ifdef CHILLY_WILLY_SCALE
            // Knuckles' Chaotix: Tachy Touch uses the values 0xF?? for negative values
            // This small modification fixes the terrible pops.
            in &= 0xFFF;
            if ((in & 0x800) != 0)
                in |= ~0xFFF;
            return ((in - this.offset) * this.scale) >> (8 - PWM_Loudness);
//#else
        }

        private void update(int[][] buf, int length) {
            int tmpOutL;
            int tmpOutR;
            int i;

            if (this.outL == 0 && this.outR == 0) {
                for (i = 0; i < length; i++) {
                    buf[0][i] = 0;
                    buf[1][i] = 0;
                }
                return;
            }

            // New PWM scaling algorithm provided by Chilly Willy on the Sonic Retro forums.
            tmpOutL = updateScale(this.outL);
            tmpOutR = updateScale(this.outR);

            for (i = 0; i < length; i++) {
                buf[0][i] = tmpOutL;
                buf[1][i] = tmpOutR;
            }
        }

        public int start(int clock) {
            int rate = 22020; // that's the rate the PWM is mostly used
            if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                    CHIP_SAMPLING_MODE == 0x02)
                rate = CHIP_SAMPLE_RATE;
            this.clock = clock;

            this.init();
            // allocate the stream
            //chip.stream = stream_create(device, 0, 2, device.clock / 384, chip, rf5c68_update);

            return rate;
        }

        private void writeChannel(byte channel, int data) {
            if (this.clock == 1) { // old-style commands
                switch (channel) {
                case 0x00:
                    this.outL = data;
                    break;
                case 0x01:
                    this.outR = data;
                    break;
                case 0x02:
                    this.setCycle(data);
                    break;
                case 0x03:
                    this.outL = data;
                    this.outR = data;
                    break;
                }
            } else {
                switch (channel) {
                case 0x00 / 2: // control register
                    this.setInt(data >> 8);
                    break;
                case 0x02 / 2: // cycle register
                    this.setCycle(data);
                    break;
                case 0x04 / 2: // l ch
                    this.outL = data;
                    break;
                case 0x06 / 2: // r ch
                    this.outR = data;
                    if (this.mode == 0) {
                        if (this.outL == this.outR) {
                            // fixes these terrible pops when
                            // starting/stopping/pausing the song
                            this.offset = data;
                            this.mode = 0x01;
                        }
                    }
                    break;
                case 0x08 / 2: // mono ch
                    this.outL = data;
                    this.outR = data;
                    if (this.mode == 0) {
                        this.offset = data;
                        this.mode = 0x01;
                    }
                    break;
                }
            }
        }
    }

    private static final int MAX_CHIPS = 0x02;
    private PwmChip[] chips = new PwmChip[] {new PwmChip(), new PwmChip()};

    @Override
    public String getName() {
        return "PWM";
    }

    @Override
    public String getShortName() {
        return "PWM";
    }

    public Pwm() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
        //0..Main
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        PwmChip chip = chips[chipId];

        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int start(byte chipId, int samplingRate, int clockValue, Object... option) {
        return start(chipId, clockValue);
    }

    @Override
    public int start(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        PwmChip chip = chips[chipId];
        return chip.start(clock);
    }

    @Override
    public void stop(byte chipId) {
    }

    @Override
    public void reset(byte chipId) {
        PwmChip chip = chips[chipId];
        chip.init();
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        PwmChip chip = chips[chipId];
        chip.writeChannel((byte) adr, data);
        return 0;
    }
}
