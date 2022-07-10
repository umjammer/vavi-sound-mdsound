package mdsound.chips;

import java.util.Arrays;

import mdsound.MDSound;
import mdsound.np.NpNesApu;
import mdsound.np.NpNesDmc;
import mdsound.np.NpNesFds;


/**
 * MAME / MESS functions
 *
 * Note: FDS core from NSFPlay is always used
 */
public class Nes {
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

    private NpNesApu chipApu;
    private NpNesDmc chipDmc;
    private NpNesFds chipFds;
    private byte[] memory;

    private static int nesOptions = 0x8000;

    public void update(int[][] outputs, int samples) {
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
        for (int curSmpl = 0x00; curSmpl < samples; curSmpl++) {
            this.chipApu.render(bufferA);
            this.chipDmc.render(bufferD);
            outputs[0][curSmpl] = (short) ((limit(bufferA[0], 0x7fff, -0x8000) * apuVolume) >> 14);
            outputs[1][curSmpl] = (short) ((limit(bufferA[1], 0x7fff, -0x8000) * apuVolume) >> 14);
            outputs[0][curSmpl] += (short) ((limit(bufferD[0], 0x7fff, -0x8000) * dmcVolume) >> 14);
            outputs[1][curSmpl] += (short) ((limit(bufferD[1], 0x7fff, -0x8000) * dmcVolume) >> 14);
            MDSound.np_nes_apu_volume = Math.abs(bufferA[0]);
            MDSound.np_nes_dmc_volume = Math.abs(bufferD[0]);
        }
//                    break;
//            }

        if (this.chipFds != null) {
            for (int curSmpl = 0x00; curSmpl < samples; curSmpl++) {
                this.chipFds.render(bufferF);
                outputs[0][curSmpl] += (short) ((limit(bufferF[0], 0x7fff, -0x8000) * fdsVolume) >> 14);
                outputs[1][curSmpl] += (short) ((limit(bufferF[1], 0x7fff, -0x8000) * fdsVolume) >> 14);
                MDSound.np_nes_fds_volume = Math.abs(bufferF[0]);
            }
        }
    }

    private static int limit(int v, int max, int min) {
        return v > max ? max : (Math.max(v, min));
    }

    public void start(int clock, int rate) {
        boolean enableFDS = ((clock >> 31) & 0x01) != 0;
        clock &= 0x7FFFFFFF;


        this.chipApu = new NpNesApu(clock, rate);

        this.chipDmc = new NpNesDmc(clock, rate);

        this.chipDmc.setAPU(this.chipApu);

        this.memory = new byte[0x8000];
        Arrays.fill(this.memory, (byte) 0);
        this.chipDmc.setMemory(this.memory, -0x8000);

        if (enableFDS) {
            this.chipFds = new NpNesFds(clock, rate);
            // If it returns NULL, that's okay.
        } else {
            this.chipFds = null;
        }
        setChipOption();
    }

    public void stop() {
        if (this.memory != null) {
            this.memory = null;
        }
        this.chipApu = null;
        this.chipDmc = null;
        this.chipFds = null;
    }

    public void reset() {
        this.chipApu.reset();
        this.chipDmc.reset();
        if (this.chipFds != null)
            this.chipFds.reset();
    }

    public void write(int offset, byte data) {
        switch (offset & 0xE0) {
        case 0x00: // NES APU
            this.chipApu.write(0x4000 | offset, data);
            this.chipDmc.write(0x4000 | offset, data);
            break;
        case 0x20: // FDS register
            if (this.chipFds == null)
                break;
            if (offset == 0x3F)
                this.chipFds.write(0x4023, data);
            else
                this.chipFds.write(0x4080 | (offset & 0x1F), data);
            break;
        case 0x40: // FDS wave RAM
        case 0x60:
            if (this.chipFds == null)
                break;
            this.chipFds.write(0x4000 | offset, data);
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

            remainBytes = 0x8000 - dataStart;
            dataStart = 0x8000;
            //ramData += remainBytes;
            ptrramData = remainBytes;
            dataLength -= remainBytes;
        }

        remainBytes = 0x00;
        if (dataStart + dataLength > 0x10000) {
            remainBytes = dataLength;
            dataLength = 0x10000 - dataStart;
            remainBytes -= dataLength;
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
        if (this.chipApu == null) return null;
        return this.chipApu.reg;
    }

    public byte[] readDmc() {
        if (this.chipDmc == null) return null;

        return this.chipDmc.reg;
    }

    public NpNesFds readDds() {
        if (this.chipFds == null) return null;

        return this.chipFds;
    }

    private void nes_set_option(int options) {
        nesOptions = options;
    }

    public void setChipOption() {
        byte curOpt;

        if ((nesOptions & 0x8000) != 0)
            return;

//        switch (EMU_CORE) {
//# ifdef ENABLE_ALL_CORES
//            case EC_MAME:
//              // no options for MAME's NES core
//              break;
//#endif
//            case EC_NSFPLAY:
            // shared APU/DMC options
            for (curOpt = 0; curOpt < 2; curOpt++) {
                this.chipApu.setOption(curOpt, (nesOptions >> curOpt) & 0x01);
                this.chipDmc.setOption(curOpt, (nesOptions >> curOpt) & 0x01);
            }
            // APU-only options
            for (; curOpt < 4; curOpt++)
                this.chipApu.setOption(curOpt - 2 + 2, (nesOptions >> curOpt) & 0x01);
            // DMC-only options
            for (; curOpt < 10; curOpt++)
                this.chipDmc.setOption(curOpt - 4 + 2, (nesOptions >> curOpt) & 0x01);
//            break;
//    }
        if (this.chipFds != null) {
            // FDS options
            // I skip the Cutoff frequency here, since it's not a boolean value.
            for (curOpt = 12; curOpt < 14; curOpt++)
                this.chipFds.setOption(curOpt - 12 + 1, (nesOptions >> curOpt) & 0x01);
        }
    }

    public void setMuteMask(int muteMask) {
        if (this.chipApu != null)
            this.chipApu.setMask((muteMask & 0x03) >> 0);
        if (this.chipDmc != null)
            this.chipDmc.setMask((muteMask & 0x1C) >> 2);
        if (this.chipFds != null)
            this.chipFds.setMask((muteMask & 0x20) >> 5);
    }
}
