package mdsound;


/**
 Irem GA20 PCM Sound Chip

 It's not currently known whether this chip is stereo.


 Revisions:

 04-15-2002 Acho A. Tang
 - rewrote channel mixing
 - added prelimenary volume and sample rate emulation

 05-30-2002 Acho A. Tang
 - applied hyperbolic gain control to volume and used
 a musical-note style progression in sample rate
 calculation(still very inaccurate)

 02-18-2004 R. Belmont
 - sample rate calculation reverse-engineered.
 Thanks to Fujix, Yasuhiro Ogawa, the Guru, and Tormod
 for real PCB samples that made this possible.

 02-03-2007 R. Belmont
 - Cleaned up faux x86 assembly.
 */
public class Iremga20 extends Instrument.BaseInstrument {

    @Override
    public void reset(byte chipID) {
        device_reset_iremga20(chipID);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipID, int clock) {
        return device_start_iremga20(chipID, 3579545);
    }

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        return device_start_iremga20(chipID, clockValue);
    }

    @Override
    public void stop(byte chipID) {
        device_stop_iremga20(chipID);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        IremGA20_update(chipID, outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    public static class Ga20State {

        private static final int MAX_VOL = 256;

        public static class Channel {
            public int rate;
            //int size;
            public int start;
            public int pos;
            public int frac;
            public int end;
            public int volume;
            public int pan;
            //int effect;
            public byte play;
            public byte muted;

            public void reset() {
                this.rate = 0;
                //this.size = 0;
                this.start = 0;
                this.pos = 0;
                this.frac = 0;
                this.end = 0;
                this.volume = 0;
                this.pan = 0;
                //this.effect = 0;
                this.play = 0;
            }

            private void write(int offset, byte data) {
                switch (offset & 0x7) {
                case 0: /* start address low */
                    this.start = ((this.start) & 0xff000) | (data << 4);
                    break;

                case 1: /* start address high */
                    this.start = ((this.start) & 0x00ff0) | (data << 12);
                    break;

                case 2: /* end address low */
                    this.end = ((this.end) & 0xff000) | (data << 4);
                    break;

                case 3: /* end address high */
                    this.end = ((this.end) & 0x00ff0) | (data << 12);
                    break;

                case 4:
                    this.rate = 0x1000000 / (256 - data);
                    break;

                case 5: //AT: gain control
                    this.volume = (data * MAX_VOL) / (data + 10);
                    break;

                case 6: //AT: this is always written 2(enabling both channels?)
                    this.play = data;
                    this.pos = this.start;
                    this.frac = 0;
                    break;
                }
            }
        }

        public byte[] rom;
        public int romSize;
        public int[] regs = new int[0x40];
        public Channel[] channel = new Channel[] {
                new Channel(),
                new Channel(),
                new Channel(),
                new Channel()
        };

        private void update(int[][] outputs, int samples) {
            int[] rate = new int[4], pos = new int[4], frac = new int[4], end = new int[4], vol = new int[4], play = new int[4];

            // precache some values
            for (int i = 0; i < 4; i++) {
                rate[i] = this.channel[i].rate;
                pos[i] = this.channel[i].pos;
                frac[i] = this.channel[i].frac;
                end[i] = this.channel[i].end - 0x20;
                vol[i] = this.channel[i].volume;
                play[i] = (int) ((this.channel[i].muted == 0) ? this.channel[i].play : 0);
            }

            byte[] pSamples = this.rom;
            int[] outL = outputs[0];
            int[] outR = outputs[1];

            for (int i = 0; i < samples; i++) {
                int sampleout = 0;

                // update the 4 channels inline
                if (play[0] != 0) {
                    sampleout += (pSamples[pos[0]] - 0x80) * vol[0];
                    frac[0] += rate[0];
                    pos[0] += frac[0] >> 24;
                    frac[0] &= 0xffffff;
                    play[0] = (pos[0] < end[0]) ? 1 : 0;
                }
                if (play[1] != 0) {
                    sampleout += (pSamples[pos[1]] - 0x80) * vol[1];
                    frac[1] += rate[1];
                    pos[1] += frac[1] >> 24;
                    frac[1] &= 0xffffff;
                    play[1] = (pos[1] < end[1]) ? 1 : 0;
                }
                if (play[2] != 0) {
                    sampleout += (pSamples[pos[2]] - 0x80) * vol[2];
                    frac[2] += rate[2];
                    pos[2] += frac[2] >> 24;
                    frac[2] &= 0xffffff;
                    play[2] = (pos[2] < end[2]) ? 1 : 0;
                }
                if (play[3] != 0) {
                    sampleout += (pSamples[pos[3]] - 0x80) * vol[3];
                    frac[3] += rate[3];
                    pos[3] += frac[3] >> 24;
                    frac[3] &= 0xffffff;
                    play[3] = (pos[3] < end[3]) ? 1 : 0;
                }

                sampleout >>= 2;
                outL[i] = sampleout;
                outR[i] = sampleout;
            }

            /* update the regs now */
            for (int i = 0; i < 4; i++) {
                this.channel[i].pos = pos[i];
                this.channel[i].frac = frac[i];
                if (this.channel[i].muted == 0)
                    this.channel[i].play = (byte) play[i];
            }
        }

        private void write(int offset, byte data) {
//        System.err.printf("GA20:  Offset %02x, data %04x\n",offset,data);

            int channel = offset >> 3;

            this.regs[offset] = data;

            this.channel[channel].write(offset, data);
        }

        public byte read(int offset) {
            int channel = offset >> 3;

            switch (offset & 0x7) {
            case 7: // Voice status.  bit 0 is 1 if active. (routine around 0xccc in rtypeleo)
                return (byte) (this.channel[channel].play != 0 ? 1 : 0);

            default:
                //System.err.printf("GA20: read unk. register %d, channel %d\n", offset & 0xf, channel);
                break;
            }

            return 0;
        }

        private void resetChannels() {
            for (int i = 0; i < 4; i++) {
                this.channel[i].reset();
            }
        }

        private void reset() {
            resetChannels();
            for (int i = 0; i < 0x40; i++) this.regs[i] = 0x00;
            //memset(this.regs, 0x00, 0x40 * sizeof(int));
        }

        private int start(int clock) {
            /* Initialize our chip structure */
            this.rom = null;
            this.romSize = 0x00;

            resetChannels();

            for (int i = 0; i < 0x40; i++)
                this.regs[i] = 0;

            for (int i = 0; i < 4; i++)
                this.channel[i].muted = 0x00;

            return clock / 4;
        }

        public void stop() {
            this.rom = null;
        }

        public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
            if (this.romSize != romSize) {
                this.rom = new byte[romSize];
                this.romSize = romSize;

                for (int i = 0; i < romSize; i++) this.rom[i] = (byte) 0xff;
                //memset(this.rom, 0xFF, romSize);
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;


            if (dataLength >= 0) System.arraycopy(romData, 0, this.rom, dataStart, dataLength);
        }

        public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
            if (this.romSize != romSize) {
                this.rom = new byte[romSize];
                this.romSize = romSize;

                for (int i = 0; i < romSize; i++) this.rom[i] = (byte) 0xff;
                //memset(this.rom, 0xFF, romSize);
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;


            if (dataLength >= 0) System.arraycopy(romData, srcStartAddress, this.rom, dataStart, dataLength);
        }

        private void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < 4; curChn++)
                this.channel[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
        }
    }

    private static final int MAX_CHIPS = 0x02;
    private Ga20State[] ga20Data = new Ga20State[] {new Ga20State(), new Ga20State()};

    @Override
    public String getName() {
        return "Irem GA20";
    }

    @Override
    public String getShortName() {
        return "GA20";
    }

    private void IremGA20_update(byte chipID, int[][] outputs, int samples) {
        Ga20State chip = ga20Data[chipID];
        chip.update(outputs, samples);
    }

    private void irem_ga20_w(byte chipID, int offset, byte data) {
        Ga20State chip = ga20Data[chipID];
        chip.write(offset, data);
    }

    public byte irem_ga20_r(byte chipID, int offset) {
        Ga20State chip = ga20Data[chipID];
        return chip.read(offset);
    }

    private void device_reset_iremga20(byte chipID) {
        Ga20State chip = ga20Data[chipID];
        chip.reset();
    }

    private int device_start_iremga20(byte chipID, int clock) {
        if (chipID >= MAX_CHIPS)
            return 0;

        Ga20State chip = ga20Data[chipID];
        return chip.start(clock);
    }

    public void device_stop_iremga20(byte chipID) {
        Ga20State chip = ga20Data[chipID];
        chip.stop();
    }

    public void iremga20_write_rom(byte chipID, int romSize, int dataStart, int dataLength, byte[] romData) {
        Ga20State chip = ga20Data[chipID];
        chip.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void iremga20_write_rom(byte chipID, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        Ga20State chip = ga20Data[chipID];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    private void iremga20_set_mute_mask(byte chipID, int muteMask) {
        Ga20State chip = ga20Data[chipID];
        chip.setMuteMask(muteMask);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        irem_ga20_w(chipID, adr, (byte) data);
        return 0;
    }
}
