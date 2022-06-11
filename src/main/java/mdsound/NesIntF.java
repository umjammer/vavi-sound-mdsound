package mdsound;

import java.util.Arrays;

import mdsound.np.NpNesApu;
import mdsound.np.NpNesDmc;
import mdsound.np.NpNesFds;


public class NesIntF extends Instrument.BaseInstrument {
    @Override
    public void reset(byte chipID) {
        device_reset_nes(chipID);
    }

    @Override
    public int start(byte chipID, int clock) {
        return device_start_nes(chipID, clock);
    }

    @Override
    public int start(byte chipID, int samplingRate, int clockValue, Object... Option) {
        return device_start_nes(chipID, clockValue);
    }

    @Override
    public void stop(byte chipID) {
        device_stop_nes(chipID);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        nes_stream_update(chipID, outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    public NesIntF() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    /*
     * MAME / MESS functions
     *
     * Note: FDS core from NSFPlay is always used
     */
    public static class NesState {
        // 音量設定
        public void setVolumeAPU(int db) {
            db = Math.min(db, 20);
            if (db > -192)
                apuVolume = (int) (16384.0 * Math.pow(10.0, db / 40.0));
            else
                apuVolume = 0;
        }

        public void setVolumeDMC(int db) {
            db = Math.min(db, 20);
            if (db > -192)
                dmcVolume = (int) (16384.0 * Math.pow(10.0, db / 40.0));
            else
                dmcVolume = 0;
        }

        public void setVolumeFDS(int db) {
            db = Math.min(db, 20);
            if (db > -192)
                fdsVolume = (int) (16384.0 * Math.pow(10.0, db / 40.0));
            else
                fdsVolume = 0;
        }

        private int apuVolume = 0;
        private int dmcVolume = 0;
        private int fdsVolume = 0;

        public NpNesApu chip_apu;
        public NpNesDmc chip_dmc;
        public NpNesFds chip_fds;
        public byte[] memory;

        private static int nesOptions = 0x8000;

        public void update(int[][] outputs, int samples) {
            int curSmpl;
            int[] bufferA = new int[2];
            int[] bufferD = new int[2];
            int[] bufferF = new int[2];

            //            switch (EMU_CORE)
            //            {
            //# ifdef ENABLE_ALL_CORES
            //                case EC_MAME:
            //                    nes_psg_update_sound(this.chip_apu, outputs, samples);
            //                   break;
            //#endif
            //                case EC_NSFPLAY:
            for (curSmpl = 0x00; curSmpl < samples; curSmpl++) {
                this.chip_apu.render(bufferA);
                this.chip_dmc.render(bufferD);
                outputs[0][curSmpl] = (short) ((limit(bufferA[0], 0x7fff, -0x8000) * apuVolume) >> 14);
                outputs[1][curSmpl] = (short) ((limit(bufferA[1], 0x7fff, -0x8000) * apuVolume) >> 14);
                outputs[0][curSmpl] += (short) ((limit(bufferD[0], 0x7fff, -0x8000) * dmcVolume) >> 14);
                outputs[1][curSmpl] += (short) ((limit(bufferD[1], 0x7fff, -0x8000) * dmcVolume) >> 14);
                MDSound.np_nes_apu_volume = Math.abs(bufferA[0]);
                MDSound.np_nes_dmc_volume = Math.abs(bufferD[0]);
            }
            //                    break;
            //            }

            if (this.chip_fds != null) {
                for (curSmpl = 0x00; curSmpl < samples; curSmpl++) {
                    this.chip_fds.render(bufferF);
                    outputs[0][curSmpl] += (short) ((limit(bufferF[0], 0x7fff, -0x8000) * fdsVolume) >> 14);
                    outputs[1][curSmpl] += (short) ((limit(bufferF[1], 0x7fff, -0x8000) * fdsVolume) >> 14);
                    MDSound.np_nes_fds_volume = Math.abs(bufferF[0]);
                }
            }
        }

        public static int limit(int v, int max, int min) {
            return v > max ? max : (Math.max(v, min));
        }

        private int start(int clock) {
            boolean enableFDS = ((clock >> 31) & 0x01) != 0;
            clock &= 0x7FFFFFFF;

            int rate = clock / 4;
            if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) ||
                    CHIP_SAMPLING_MODE == 0x02)
                rate = CHIP_SAMPLE_RATE;

            this.chip_apu = new NpNesApu(clock, rate);

            this.chip_dmc = new NpNesDmc(clock, rate);

            this.chip_dmc.setAPU(this.chip_apu);

            this.memory = new byte[0x8000];
            Arrays.fill(this.memory, (byte) 0);
            this.chip_dmc.setMemory(this.memory, -0x8000);

            if (enableFDS) {
                this.chip_fds = new NpNesFds(clock, rate);
                // If it returns NULL, that's okay.
            } else {
                this.chip_fds = null;
            }
            setChipOption();

            return rate;
        }

        private void stop() {
            if (this.memory != null) {
                this.memory = null;
            }
            this.chip_apu = null;
            this.chip_dmc = null;
            this.chip_fds = null;
        }

        private void reset() {
            this.chip_apu.reset();
            this.chip_dmc.reset();
            if (this.chip_fds != null)
                this.chip_fds.reset();
        }

        private void write(int offset, byte data) {
            switch (offset & 0xE0) {
            case 0x00: // NES APU
                this.chip_apu.write(0x4000 | offset, data);
                this.chip_dmc.write(0x4000 | offset, data);
                break;
            case 0x20: // FDS register
                if (this.chip_fds == null)
                    break;
                if (offset == 0x3F)
                    this.chip_fds.write(0x4023, data);
                else
                    this.chip_fds.write(0x4080 | (offset & 0x1F), data);
                break;
            case 0x40: // FDS wave RAM
            case 0x60:
                if (this.chip_fds == null)
                    break;
                this.chip_fds.write(0x4000 | offset, data);
                break;
            }
        }

        public void writeRam(int dataStart, int dataLength, byte[] ramData, int ramdataStartAdr) {
            int remainBytes;
            int ptrramData = 0;

            if (dataStart >= 0x10000)
                return;

            if (dataStart < 0x8000) {
                if (dataStart + dataLength <= 0x8000)
                    return;

                remainBytes = (int) (0x8000 - dataStart);
                dataStart = 0x8000;
                //ramData += remainBytes;
                ptrramData = remainBytes;
                dataLength -= (int) remainBytes;
            }

            remainBytes = 0x00;
            if (dataStart + dataLength > 0x10000) {
                remainBytes = (int) dataLength;
                dataLength = 0x10000 - dataStart;
                remainBytes -= (int) dataLength;
            }

            //memcpy(this.Memory + (dataStart - 0x8000), ramData, dataLength);
            for (int i = 0; i < dataLength; i++) {
                this.memory[(dataStart - 0x8000) + i] = ramData[ptrramData + i + ramdataStartAdr];
            }

            if (remainBytes != 0) {
                if (remainBytes > 0x8000)
                    remainBytes = 0x8000;
                //memcpy(this.Memory, ramData + dataLength, remainBytes);
                for (int i = 0; i < remainBytes; i++) {
                    this.memory[ptrramData + dataLength + i] = ramData[ptrramData + dataLength + i + ramdataStartAdr];
                }
            }
        }

        public byte[] readApu() {
            if (this.chip_apu == null) return null;
            return this.chip_apu.reg;
        }

        public byte[] readDmc() {
            if (this.chip_dmc == null) return null;

            return this.chip_dmc.reg;
        }

        public NpNesFds readDds() {
            if (this.chip_fds == null) return null;

            return this.chip_fds;
        }

        private void nes_set_option(int options) {
            nesOptions = options;
        }

        private void setChipOption() {
            byte curOpt;

            if ((nesOptions & 0x8000) != 0)
                return;

            //    switch (EMU_CORE)
            //    {
            //# ifdef ENABLE_ALL_CORES
            //        case EC_MAME:
            //            // no options for MAME's NES core
            //            break;
            //#endif
            //        case EC_NSFPLAY:
            // shared APU/DMC options
            for (curOpt = 0; curOpt < 2; curOpt++) {
                this.chip_apu.setOption(curOpt, (nesOptions >> curOpt) & 0x01);
                this.chip_dmc.setOption(curOpt, (nesOptions >> curOpt) & 0x01);
            }
            // APU-only options
            for (; curOpt < 4; curOpt++)
                this.chip_apu.setOption(curOpt - 2 + 2, (nesOptions >> curOpt) & 0x01);
            // DMC-only options
            for (; curOpt < 10; curOpt++)
                this.chip_dmc.setOption(curOpt - 4 + 2, (nesOptions >> curOpt) & 0x01);
            //            break;
            //    }
            if (this.chip_fds != null) {
                // FDS options
                // I skip the Cutoff frequency here, since it's not a boolean value.
                for (curOpt = 12; curOpt < 14; curOpt++)
                    this.chip_fds.setOption(curOpt - 12 + 1, (nesOptions >> curOpt) & 0x01);
            }
        }

        public void setMuteMask(int muteMask) {
            if (this.chip_apu != null)
                this.chip_apu.setMask((muteMask & 0x03) >> 0);
            if (this.chip_dmc != null)
                this.chip_dmc.setMask((muteMask & 0x1C) >> 2);
            if (this.chip_fds != null)
                this.chip_fds.setMask((muteMask & 0x20) >> 5);
        }
    }

    private static final byte MAX_CHIPS = 0x02;
    private NesState[] nesAPUData = new NesState[] {new NesState(), new NesState()};

    @Override
    public String getName() {
        return "NES";
    }

    @Override
    public String getShortName() {
        return "NES";
    }

    public void nes_stream_update(byte chipID, int[][] outputs, int samples) {
        NesState info = nesAPUData[chipID];
        info.update(outputs, samples);
    }

    private int device_start_nes(byte chipID, int clock) {
        if (chipID >= MAX_CHIPS)
            return 0;

        NesState info = nesAPUData[chipID];
        return info.start(clock);
    }

    private void device_stop_nes(byte chipID) {
        NesState info = nesAPUData[chipID];
        info.stop();
    }

    private void device_reset_nes(byte chipID) {
        NesState info = nesAPUData[chipID];
        info.reset();
    }

    private void nes_w(byte chipID, int offset, byte data) {
        NesState info = nesAPUData[chipID];
        info.write(offset, data);
    }

    public void nes_write_ram(byte chipID, int dataStart, int dataLength, byte[] ramData) {
        nes_write_ram(chipID, dataStart, dataLength, ramData, 0);
    }

    public void nes_write_ram(byte chipID, int dataStart, int dataLength, byte[] ramData, int ramdataStartAdr) {
        NesState info = nesAPUData[chipID];
        info.writeRam(dataStart, dataLength, ramData, ramdataStartAdr);
    }

    public byte[] nes_r_apu(byte chipID) {
        NesState info = nesAPUData[chipID];
        return info.readApu();
    }

    public byte[] nes_r_dmc(byte chipID) {
        NesState info = nesAPUData[chipID];
        return info.readDmc();
    }

    public NpNesFds nes_r_fds(byte chipID) {
        NesState info = nesAPUData[chipID];
        return info.readDds();
    }

    private void nes_set_emu_core(byte emulator) {
    }

    private void nes_set_chip_option(byte chipID) {
        NesState info = nesAPUData[chipID];
        info.setChipOption();
    }

    public void nes_set_mute_mask(byte chipID, int muteMask) {
        NesState info = nesAPUData[chipID];
        info.setMuteMask(muteMask);
    }

    public void setVolumeAPU(int db) {
        for (NesState info : nesAPUData) info.setVolumeAPU(db);
    }

    public void setVolumeDMC(int db) {
        for (NesState info : nesAPUData) info.setVolumeDMC(db);
    }

    public void setVolumeFDS(int db) {
        for (NesState info : nesAPUData) info.setVolumeFDS(db);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        nes_w(chipID, adr, (byte) data);
        return 0;
    }
}
