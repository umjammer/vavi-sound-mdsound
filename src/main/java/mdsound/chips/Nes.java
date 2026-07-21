package mdsound.chips;

import java.util.Arrays;
import java.util.function.Consumer;

import mdsound.np.NpNesApu;
import mdsound.np.NpNesDmc;
import mdsound.np.NpNesFds;


/**
 * MAME / MESS functions
 *
 * Note: FDS core from NSFPlay is always used
 */
public class Nes {

    // Volume Settings
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

    private final NpNesApu nesApu;
    private final NpNesDmc nesDmc;
    private NpNesFds nesFds;
    private byte[] memory;

    public Nes() {
        nesApu = new NpNesApu();
        nesDmc = new NpNesDmc();
        nesDmc.nes_apu = nesApu;

        setVolumeAPU(0);
        setVolumeDMC(0);
        setVolumeFDS(0);
    }

    private static int nesOptions = 0x8000;

    public void update(int[][] outputs, int samples) {
        int[] bufferA = new int[2];
        int[] bufferD = new int[2];
        int[] bufferF = new int[2];

//        switch (EMU_CORE) {
//#ifdef ENABLE_ALL_CORES
//        case EC_MAME:
//            nes_psg_update_sound(this.chip_apu, outputs, samples);
//            break;
//#endif
//        case EC_NSFPLAY:
            for (int curSmpl = 0x00; curSmpl < samples; curSmpl++) {
                this.nesApu.render(bufferA);
                this.nesDmc.render(bufferD);
                outputs[0][curSmpl] = (short) ((Math.clamp(bufferA[0], -0x8000, 0x7fff) * apuVolume) >> 14);
                outputs[1][curSmpl] = (short) ((Math.clamp(bufferA[1], -0x8000, 0x7fff) * apuVolume) >> 14);
                outputs[0][curSmpl] += (short) ((Math.clamp(bufferD[0], -0x8000, 0x7fff) * dmcVolume) >> 14);
                outputs[1][curSmpl] += (short) ((Math.clamp(bufferD[1], -0x8000, 0x7fff) * dmcVolume) >> 14);
                if (listener != null) listener.accept(new int[] {Math.abs(bufferA[0]), Math.abs(bufferD[0]), -1, -1, -1, -1, -1, -1});
            }
//            break;
//        }

        if (nesFds != null) {
            for (int curSmpl = 0x00; curSmpl < samples; curSmpl++) {
                this.nesFds.render(bufferF);
                outputs[0][curSmpl] += (short) ((Math.clamp(bufferF[0], -0x8000, 0x7fff) * fdsVolume) >> 14);
                outputs[1][curSmpl] += (short) ((Math.clamp(bufferF[1], -0x8000, 0x7fff) * fdsVolume) >> 14);
                if (listener != null) listener.accept(new int[] {-1, -1, Math.abs(bufferF[0]), -1, -1, -1, -1, -1});
            }
        }
    }

    public void start(int clock, int rate) {
        boolean enableFDS = ((clock >> 31) & 0x01) != 0;
        clock &= 0x7fff_ffff;

        nesApu.init(clock, rate);

        nesDmc.init(clock, rate);

        this.nesDmc.setAPU(this.nesApu);

        this.memory = new byte[0x8000];
        Arrays.fill(this.memory, (byte) 0);
        this.nesDmc.setMemory(this.memory, -0x8000);

        if (enableFDS) {
            nesFds = new NpNesFds();
            nesFds.init(clock, rate);
            // If it returns NULL, that's okay.
        }
        setChipOption();
    }

    public void stop() {
        if (this.memory != null) {
            this.memory = null;
        }
        nesFds = null;
    }

    public void reset() {
        this.nesApu.reset();
        this.nesDmc.reset();
        if (nesFds != null)
            this.nesFds.reset();
    }

    public void write(int offset, int data) {
        switch (offset & 0xE0) {
        case 0x00: // NES APU
            this.nesApu.write(0x4000 | offset, data);
            this.nesDmc.write(0x4000 | offset, data);
            break;
        case 0x20: // FDS register
            if (nesFds == null)
                return;
            if (offset == 0x3F)
                this.nesFds.write(0x4023, data);
            else
                this.nesFds.write(0x4080 | (offset & 0x1F), data);
            break;
        case 0x40: // FDS wave RAM
        case 0x60:
            if (nesFds == null)
                return;
            this.nesFds.write(0x4000 | offset, data);
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

    public NpNesApu readApu() {
        return this.nesApu;
    }

    public NpNesDmc readDmc() {
        return this.nesDmc;
    }

    public NpNesFds readFds() {
        return this.nesFds;
    }

    private static void nes_set_option(int options) {
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
            this.nesApu.setOption(curOpt, (nesOptions >> curOpt) & 0x01);
            this.nesDmc.setOption(curOpt, (nesOptions >> curOpt) & 0x01);
        }
        // APU-only options
        for (; curOpt < 4; curOpt++)
            this.nesApu.setOption(curOpt - 2 + 2, (nesOptions >> curOpt) & 0x01);
        // DMC-only options
        for (; curOpt < 10; curOpt++)
            this.nesDmc.setOption(curOpt - 4 + 2, (nesOptions >> curOpt) & 0x01);
//            break;
//    }
        // FDS options
        // I skip the Cutoff frequency here, since it's not a boolean value.
        for (curOpt = 12; curOpt < 14; curOpt++)
            this.nesFds.setOption(curOpt - 12 + 1, (nesOptions >> curOpt) & 0x01);
    }

    public void setMuteMask(int muteMask) {
        this.nesApu.setMask((muteMask & 0x03) >> 0);
        this.nesDmc.setMask((muteMask & 0x1C) >> 2);
        this.nesFds.setMask((muteMask & 0x20) >> 5);
    }

    private Consumer<int[]> listener;

    public void setListener(Consumer<int[]> listener) {
        this.listener = listener;
    }
}
