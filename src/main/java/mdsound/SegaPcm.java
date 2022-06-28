package mdsound;


public class SegaPcm extends Instrument.BaseInstrument {

    public SegaPcm() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
        //0..Main
    }

    @Override
    public int start(byte chipId, int clock) {
        int intf_bank = 0;
        return device_start_segapcm(chipId, clock, intf_bank);
    }

    @Override
    public int start(byte chipId, int samplingrate, int clockValue, Object... option) {
        return device_start_segapcm(chipId, clockValue, (int) option[0]);
    }

    @Override
    public void stop(byte chipId) {
        device_stop_segapcm(chipId);
    }

    @Override
    public void reset(byte chipId) {
        device_reset_segapcm(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        SEGAPCM_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    /**
     * SEGA 16ch 8bit PCM
     */
    public static class SegaPcmState {

        public final int BANK_256 = 11;
        public final int BANK_512 = 12;
        public final int BANK_12M = 13;
        public final int BANK_MASK7 = 0x70 << 16;
        public final int BANK_MASKF = 0xf0 << 16;
        public final int BANK_MASKF8 = 0xf8 << 16;

        public static class SegaPcmInterface {
            public int bank;
        }

        public byte[] ram;
        public int ptrRam = 0;
        public byte[] low = new byte[16];
        public int romSize;
        public byte[] rom;
        public int ptrRom = 0;

        public int bankShift;
        public int bankMask;
        public int rgnMask;
        public SegaPcmInterface intf = new SegaPcmInterface();
        public byte[] muted = new byte[16];

        public void update(int[][] outputs, int samples) {
            int rgnmask = this.rgnMask;
            int ch;

            for (int i = 0; i < samples; i++) {
                outputs[0][i] = 0;
                outputs[1][i] = 0;
            }

            // reg      function
            // ------------------------------------------------
            // 0x00     ?
            // 0x01     ?
            // 0x02     volume left
            // 0x03     volume right
            // 0x04     loop address (08-15)
            // 0x05     loop address (16-23)
            // 0x06     end address
            // 0x07     address delta
            // 0x80     ?
            // 0x81     ?
            // 0x82     ?
            // 0x83     ?
            // 0x84     current address (08-15), 00-07 is internal?
            // 0x85     current address (16-23)
            // 0x86     bit 0: channel disable?
            //          bit 1: loop disable
            //          other bits: bank
            // 0x87     ?

            /* loop over channels */
            for (ch = 0; ch < 16; ch++) {

                int ptrRegs = this.ptrRam + 8 * ch;

                /* only process active channels */
                if ((this.ram[ptrRegs + 0x86] & 1) == 0 && this.muted[ch] == 0) {
                    int ptrRom = this.ptrRom + ((this.ram[ptrRegs + 0x86] & this.bankMask) << this.bankShift);
                    //System.err.printf("this.ram[ptrRegs + 0x86]:%x", this.ram[ptrRegs + 0x86]);
                    //System.err.printf("this.bankmask:%x", this.bankmask);
                    //System.err.printf("this.bankshift:%x", this.bankshift);
                    int addr = (this.ram[ptrRegs + 0x85] << 16) | (this.ram[ptrRegs + 0x84] << 8) | this.low[ch];
                    int loop = (this.ram[ptrRegs + 0x05] << 16) | (this.ram[ptrRegs + 0x04] << 8);
                    byte end = (byte) (this.ram[ptrRegs + 6] + 1);
                    int i;

                    /* loop over samples on this channel */
                    for (i = 0; i < samples; i++) {
                        byte v = 0;

                        /* handle looping if we've hit the end */
                        if ((addr >> 16) == end) {
                            if ((this.ram[ptrRegs + 0x86] & 2) != 0) {
                                this.ram[ptrRegs + 0x86] |= 1;
                                break;
                            } else addr = loop;
                        }

                        /* fetch the sample */
                        if (ptrRom + ((addr >> 8) & rgnmask) < this.rom.length) {
                            v = (byte) (this.rom[ptrRom + ((addr >> 8) & rgnmask)] - 0x80);
                        }
                        //# ifdef _DEBUG
                        //                    if ((romusage[(addr >> 8) & rgnmask] & 0x03) == 0x02 && (regs[2] || regs[3]))
                        //                        printf("Access to empty ROM section! (0x%06lX)\n",
                        //                               ((regs[0x86] & this.bankmask) << this.bankshift) + (addr >> 8) & rgnmask);
                        //                    romusage[(addr >> 8) & rgnmask] |= 0x01;
                        //#endif

                        /* apply panning and advance */
                        // fixed Bitmask for volume multiplication, thanks to ctr -Valley Bell
                        outputs[0][i] += v * (this.ram[ptrRegs + 2] & 0x7F);
                        outputs[1][i] += v * (this.ram[ptrRegs + 3] & 0x7F);
                        addr = (addr + this.ram[ptrRegs + 7]) & 0xffffff;

                    }

                    /* store back the updated address */
                    this.ram[ptrRegs + 0x84] = (byte) (addr >> 8);
                    this.ram[ptrRegs + 0x85] = (byte) (addr >> 16);
                    this.low[ch] = (byte) (((this.ram[ptrRegs + 0x86] & 1) != 0) ? 0 : addr);
                }
            }
        }

        public int start(int clock, int intfBank) {
            final int STD_ROM_SIZE = 0x80000;

            SegaPcmInterface intf = this.intf;
            intf.bank = intfBank;

            this.romSize = STD_ROM_SIZE;
            this.rom = new byte[STD_ROM_SIZE];
            this.ptrRom = 0;
            this.ram = new byte[0x800];

            for (int i = 0; i < STD_ROM_SIZE; i++) {
                this.rom[i] = (byte) 0x80;
            }

            this.bankShift = (byte) (intf.bank);
            int mask = intf.bank >> 16;
            if (mask == 0)
                mask = BANK_MASK7 >> 16;

            this.rgnMask = STD_ROM_SIZE - 1;
            int romMask;
            for (romMask = 1; romMask < STD_ROM_SIZE; romMask *= 2) ;
            romMask--;

            this.bankMask = mask & (romMask >> this.bankShift);

            for (mask = 0; mask < 16; mask++)
                this.muted[mask] = 0x00;

            return clock / 128;
        }

        public void stop() {
            this.rom = null;
            this.ram = null;
        }

        public void reset() {
            //memset(this.ram, 0xFF, 0x800);
            for (int i = 0; i < 0x800; i++) {
                this.ram[i] = (byte) 0xff;
            }
        }

        private void write(int offset, byte data) {
            this.ram[offset & 0x07ff] = data;
        }

        public byte read(int offset) {
            return this.ram[offset & 0x07ff];
        }

        public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
            if (this.romSize != romSize) {
                long mask, rom_mask;

                this.rom = new byte[romSize];
                this.romSize = romSize;
                //memset(this.rom, 0x80, romSize);
                for (int i = 0; i < romSize; i++) {
                    this.rom[i] = (byte) 0x80;
                }

                // recalculate bankmask
                mask = this.intf.bank >> 16;
                if (mask == 0)
                    mask = BANK_MASK7 >> 16;

                for (rom_mask = 1; rom_mask < (long) romSize; rom_mask *= 2) ;
                rom_mask--;
                this.rgnMask = (int) rom_mask; // fix for ROMs with e.g 0x60000 bytes (stupid M1)

                this.bankMask = (int) (mask & (rom_mask >> this.bankShift));
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            if (dataLength >= 0) System.arraycopy(romData, 0, this.rom, dataStart, dataLength);
        }

        public void writeRom2(int romSize, int dataStart, int dataLength, byte[] romData, int SrcStartAdr) {
            if (this.romSize != romSize) {
                long mask, rom_mask;

                this.rom = new byte[romSize];
                this.romSize = romSize;
                //memset(this.rom, 0x80, romSize);
                for (int i = 0; i < romSize; i++) {
                    this.rom[i] = (byte) 0x80;
                }

                // recalculate bankmask
                mask = this.intf.bank >> 16;
                if (mask == 0)
                    mask = BANK_MASK7 >> 16;

                for (rom_mask = 1; rom_mask < (long) romSize; rom_mask *= 2) ;
                rom_mask--;
                this.rgnMask = (int) rom_mask; // fix for ROMs with e.g 0x60000 bytes (stupid M1)

                this.bankMask = (int) (mask & (rom_mask >> this.bankShift));
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            if (dataLength >= 0) System.arraycopy(romData, SrcStartAdr, this.rom, dataStart, dataLength);
        }

        public void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < 16; curChn++)
                this.muted[curChn] = (byte) ((muteMask >> curChn) & 0x01);
        }
    }

    private static final int MAX_CHIPS = 0x02;
    public SegaPcmState[] SPCMData = new SegaPcmState[] {new SegaPcmState(), new SegaPcmState()};

    @Override
    public String getName() {
        return "SEGA PCM";
    }

    @Override
    public String getShortName() {
        return "SPCM";
    }

    public void SEGAPCM_update(byte chipId, int[][] outputs, int samples) {
        SegaPcmState spcm = SPCMData[chipId];
        spcm.update(outputs, samples);
    }

    public int device_start_segapcm(byte chipId, int clock, int intf_bank) {
        if (chipId >= MAX_CHIPS)
            return 0;

        SegaPcmState spcm = SPCMData[chipId];
        return spcm.start(clock, intf_bank);
    }

    public void device_stop_segapcm(byte chipId) {
        SegaPcmState spcm = SPCMData[chipId];
        spcm.stop();
    }

    public void device_reset_segapcm(byte chipId) {
        SegaPcmState spcm = SPCMData[chipId];
        spcm.reset();
    }

    private void sega_pcm_w(byte chipId, int offset, byte data) {
        SegaPcmState spcm = SPCMData[chipId];
        spcm.write(offset, data);
    }

    public byte sega_pcm_r(byte chipId, int offset) {
        SegaPcmState spcm = SPCMData[chipId];
        return spcm.read(offset);
    }

    public void sega_pcm_write_rom(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        SegaPcmState spcm = SPCMData[chipId];
        spcm.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void sega_pcm_write_rom2(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        SegaPcmState spcm = SPCMData[chipId];
        spcm.writeRom2(romSize, dataStart, dataLength, romData, srcStartAdr);
    }

    public void segapcm_set_mute_mask(byte chipId, int muteMask) {
        SegaPcmState spcm = SPCMData[chipId];
        spcm.setMuteMask(muteMask);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        sega_pcm_w(chipId, adr, (byte) data);
        return 0;
    }

    /*
     * Generic get_info
     */
    /*DEVICE_GET_INFO( SegaPcm ) {
            case DEVINFO_STR_NAME:       strcpy(info.s, "Sega PCM");     break;
            case DEVINFO_STR_FAMILY:     strcpy(info.s, "Sega custom");     break;
            case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
            case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
    }*/
}
