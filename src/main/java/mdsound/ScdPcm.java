package mdsound;


public class ScdPcm extends Instrument.BaseInstrument {

    public static class PcmChip {

        private static final int STEP_SHIFT = 11;

        public static class Channel {
            /** envelope register */
            public int env;
            /** pan register */
            public int pan;
            /** envelope & pan product letf */
            public int mulL;
            /** envelope & pan product right */
            public int mulR;
            /** start address register */
            public int stAddr;
            /** loop address register */
            public int loopAddr;
            /** current address register */
            public int addr;
            /** frequency register */
            public int step;
            /** frequency register binaire */
            public int stepB;
            /** channel on/off register */
            public int enable;
            /** wave data */
            public int data;
            public int muted;

            public void init() {
                this.enable = 0;
                this.env = 0;
                this.pan = 0;
                this.stAddr = 0;
                this.addr = 0;
                this.loopAddr = 0;
                this.step = 0;
                this.stepB = 0;
                this.data = 0;
            }
        }

        public float rate;
        public int smpl0Patch;
        public int enable;
        public int curChan;
        public int bank;

        public Channel[] channels = new Channel[] {
                new Channel(), new Channel(), new Channel(), new Channel(),
                new Channel(), new Channel(), new Channel(), new Channel()
        };
        public long ramSize;
        public byte[] ram;

        /**
         * Initialize the PCM chip.
         *
         * @param rate Sample rate.
         * @return 0 if successful.
         */
        private int init(int rate) {
            this.smpl0Patch = 0;
            for (int i = 0; i < 8; i++)
                this.channels[i].muted = 0x00;

            this.ramSize = 64 * 1024;
            this.ram = new byte[(int) this.ramSize];
            reset();
            setRate(rate);

            return 0;
        }

        /**
         * Reset the PCM chip.
         */
        private void reset() {
            // Clear the PCM memory.
            for (long j = 0; j < this.ramSize; j++) this.ram[(int) j] = 0x00;

            this.enable = 0;
            this.curChan = 0;
            this.bank = 0;

            // clear channel registers
            for (int i = 0; i < 8; i++) {
                Channel chan = this.channels[i];
                chan.init();
            }
        }

        /**
         * Change the PCM sample rate.
         *
         * @param rate New sample rate.
         */
        private void setRate(int rate) {
            if (rate == 0)
                return;

            this.rate = (float) (31.8 * 1024) / (float) rate;

            for (int i = 0; i < 8; i++) {
                this.channels[i].step = (int) ((float) this.channels[i].stepB * this.rate);
            }
        }

        /**
         * Write to a PCM register.
         *
         * @param reg  Register ID.
         * @param data Data to write.
         */
        private void writeReg(int reg, int data) {
            int i;
            Channel chan = this.channels[this.curChan];

            data &= 0xFF;

            switch (reg) {
            case 0x00: // evelope register
                chan.env = data;
                chan.mulL = (data * (chan.pan & 0x0F)) >> 5;
                chan.mulR = (data * (chan.pan >> 4)) >> 5;
                break;

            case 0x01: // pan register
                chan.pan = data;
                chan.mulL = ((data & 0x0F) * chan.env) >> 5;
                chan.mulR = ((data >> 4) * chan.env) >> 5;
                break;

            case 0x02: // frequency step (LB) registers
                chan.stepB &= 0xFF00;
                chan.stepB += data;
                chan.step = (int) ((float) chan.stepB * this.rate);

                //Debug.printf("Step low = %.2X   Step calculated = %.8X", data, chan.Step);
                break;

            case 0x03: // frequency step (HB) registers
                chan.stepB &= 0x00FF;
                chan.stepB += data << 8;
                chan.step = (int) ((float) chan.stepB * this.rate);

                //Debug.printf("Step high = %.2X   Step calculated = %.8X", data, chan.Step);
                break;

            case 0x04:
                chan.loopAddr &= 0xFF00;
                chan.loopAddr += data;

                //Debug.printf("Loop low = %.2X   Loop = %.8X", data, chan.Loop_Addr);
                break;

            case 0x05:
                /** loop address registers  */
                chan.loopAddr &= 0x00FF;
                chan.loopAddr += data << 8;

                //Debug.printf("Loop high = %.2X   Loop = %.8X", data, chan.Loop_Addr);
                break;

            case 0x06: // start address registers
                chan.stAddr = data << (STEP_SHIFT + 8);
                //chan.Addr = chan.St_Addr;

                //Debug.printf("Start addr = %.2X   New Addr = %.8X", data, chan.Addr);
                break;

            case 0x07: // control register
                // mod is H
                if ((data & 0x40) != 0) {
                    // select channel
                    this.curChan = data & 0x07;
                } else { // mod is L
                    // pcm ram bank select
                    this.bank = (data & 0x0F) << 12;
                }

                // sounding bit
                if ((data & 0x80) > 0)
                    this.enable = 0xFF; // Used as mask
                else
                    this.enable = 0;

                //Debug.printf("General Enable = %.2X", data);
                break;

            case 0x08:
                // Sound on/off register
                data ^= 0xFF;

                //Debug.printf("Channel Enable = %.2X", data);

                for (i = 0; i < 8; i++) {
                    chan = this.channels[i];
                    if (chan.enable == 0)
                        chan.addr = chan.stAddr;
                }

                for (i = 0; i < 8; i++) {
                    this.channels[i].enable = data & (1 << i);
                }
                break;
            }
        }

        /**
         * Update the PCM buffer.
         *
         * @param buf    PCM buffer.
         * @param length Buffer length.
         */
        private int update(int[][] buf, int length) {
            int[] bufL = buf[0];
            int[] bufR = buf[1];

            // clear buffers
            for (int d = 0; d < length; d++) {
                bufL[d] = 0;
                bufR[d] = 0;
            }

            // if PCM disable, no Sound
            if (this.enable == 0) return 1;

            // for long update
            for (int i = 0; i < 8; i++) {
                Channel ch = this.channels[i];

                // only loop when sounding and on
                if (ch.enable > 0 && ch.muted == 0) {
                    int addr = ch.addr >> STEP_SHIFT;
                    //volL = &(PCM_Volume_Tab[ch.MUL_L << 8]);
                    //volR = &(PCM_Volume_Tab[ch.MUL_R << 8]);

                    for (int j = 0; j < length; j++) {
                        // test for loop signal
                        if ((this.ram[addr] & 0xff) == 0xFF) {
                            ch.addr = (addr = ch.loopAddr) << STEP_SHIFT;
                            if (this.ram[addr] == 0xFF)
                                break;
                            else
                                j--;
                        } else {
                            if ((this.ram[addr] & 0x80) != 0) {
                                ch.data = this.ram[addr] & 0x7F;
                                bufL[j] -= ch.data * ch.mulL;
                                bufR[j] -= ch.data * ch.mulR;
                            } else {
                                ch.data = this.ram[addr];
                                // this improves the Sound of Cosmic Fantasy Stories,
                                // although it's definately false behaviour
                                if (ch.data == 0 && this.smpl0Patch != 0)
                                    ch.data = -0x7F;
                                bufL[j] += ch.data * ch.mulL;
                                bufR[j] += ch.data * ch.mulR;
                            }

                            // update address register
                            int k = addr + 1;
                            ch.addr = (ch.addr + ch.step) & 0x7FFFFFF;
                            addr = ch.addr >> STEP_SHIFT;

                            for (; k < addr; k++) {
                                if ((this.ram[k] & 0xff) == 0xFF) {
                                    ch.addr = (addr = ch.loopAddr) << STEP_SHIFT;
                                    break;
                                }
                            }
                        }
                    }

                    if ((this.ram[addr] & 0xff) == 0xFF) {
                        ch.addr = ch.loopAddr << STEP_SHIFT;
                    }
                }
            }

            for (int j = 0; j < length; j++) {
                bufL[j] = MDSound.limit(bufL[j], Short.MAX_VALUE, Short.MIN_VALUE);
                bufR[j] = MDSound.limit(bufR[j], Short.MAX_VALUE, Short.MIN_VALUE);
            }

            return 0;
        }

        public void start(int clock) {
            this.smpl0Patch = (clock & 0x80000000) >> 31;
        }

        public void writeMem(int offset, byte data) {
            this.ram[this.bank | offset] = data;
        }

        public void writeRam(int dataStart, int dataLength, byte[] ramData) {
            dataStart |= this.bank;
            if (dataStart >= this.ramSize)
                return;
            if (dataStart + dataLength > this.ramSize)
                dataLength = (int) (this.ramSize - dataStart);

            if (dataLength >= 0) System.arraycopy(ramData, 0, this.ram, dataStart, dataLength);
        }

        public void writeRam2(int ramStartAdr, int ramdataLength, byte[] srcData, int srcStartAdr) {
            ramStartAdr |= this.bank;
            if (ramStartAdr >= this.ramSize)
                return;
            if (ramStartAdr + ramdataLength > this.ramSize)
                ramdataLength = (int) (this.ramSize - ramStartAdr);

            if (ramdataLength >= 0)
                System.arraycopy(srcData, srcStartAdr, this.ram, ramStartAdr, ramdataLength);
        }

        public void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < 8; curChn++)
                this.channels[curChn].muted = (muteMask >> curChn) & 0x01;
        }

        public void setMuteCh(int ch, int mute) {
            this.channels[ch].muted = mute & 0x1;
        }
    }

    //private static final int MAX_CHIPS = 0x02;
    public PcmChip[] PCM_Chip = new PcmChip[] {new PcmChip(), new PcmChip()};

    private int PCM_Init(byte chipId, int rate) {
        PcmChip chip = PCM_Chip[chipId];
        return chip.init(rate);
    }

    private void PCM_Reset(byte chipId) {
        PcmChip chip = PCM_Chip[chipId];
        chip.reset();
    }

    private void PCM_Set_Rate(byte chipId, int rate) {
        PcmChip chip = PCM_Chip[chipId];
        chip.setRate(rate);
    }

    private void PCM_Write_Reg(byte chipId, int reg, int data) {
        PcmChip chip = PCM_Chip[chipId];
        chip.writeReg(reg, data);
    }

    private int PCM_Update(byte chipId, int[][] buf, int length) {
        PcmChip chip = PCM_Chip[chipId];
        return chip.update(buf, length);
    }

    @Override
    public String getName() {
        return "RF5C164";
    }

    @Override
    public String getShortName() {
        return "RF5C";
    }

    public ScdPcm() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
        //0..Main
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        PcmChip chip = PCM_Chip[chipId];

        PCM_Update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    // samplingrate 未使用
    @Override
    public int start(byte chipId, int samplingrate, int clockValue, Object... option) {
        return start(chipId, clockValue);
    }

    @Override
    public int start(byte chipId, int clock) {
        if (chipId >= 0x02)
            return 0;

        int rate = (clock & 0x7FFFFFFF) / 384;
        if (((CHIP_SAMPLING_MODE & 0x01) != 0 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;
        PCM_Init(chipId, rate);

        PcmChip chip = PCM_Chip[chipId];
        chip.start(clock);
        return rate;
    }

    @Override
    public void stop(byte chipId) {
        PcmChip chip = PCM_Chip[chipId];
    }

    @Override
    public void reset(byte chipId) {
        PCM_Reset(chipId);
    }

    private void rf5c164_w(byte chipId, int offset, byte data) {
        PCM_Write_Reg(chipId, offset, data);
    }

    public void rf5c164_mem_w(byte chipId, int offset, byte data) {
        PcmChip chip = PCM_Chip[chipId];
        chip.writeMem(offset, data);
    }

    public void rf5c164_write_ram(byte chipId, int dataStart, int dataLength, byte[] ramData) {
        PcmChip chip = PCM_Chip[chipId];
        chip.writeRam(dataStart, dataLength, ramData);
    }

    public void rf5c164_write_ram2(byte chipId, int RAMStartAdr, int RAMdataLength, byte[] SrcData, int SrcStartAdr) {
        PcmChip chip = PCM_Chip[chipId];
        chip.writeRam2(RAMStartAdr, RAMdataLength, SrcData, SrcStartAdr);
    }

    public void rf5c164_set_mute_mask(byte chipId, int muteMask) {
        PcmChip chip = PCM_Chip[chipId];
        chip.setMuteMask(muteMask);
    }

    public void rf5c164_set_mute_Ch(byte chipId, int ch, int mute) {
        PcmChip chip = PCM_Chip[chipId];
        chip.setMuteCh(ch, mute);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        rf5c164_w(chipId, adr, (byte) data);
        return 0;
    }
}
