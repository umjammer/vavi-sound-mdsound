/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.np;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import mdsound.np.chip.NesApu;
import mdsound.np.chip.NesDmc;
import mdsound.np.chip.NesFds;
import mdsound.np.chip.NesFme7;
import mdsound.np.chip.NesMmc5;
import mdsound.np.chip.NesN106;
import mdsound.np.chip.NesVrc6;
import mdsound.np.chip.NesVrc7;
import mdsound.np.cpu.Km6502;
import mdsound.np.memory.NesBank;
import mdsound.np.memory.NesMem;
import vavi.util.ByteUtil;

import static vavi.sound.SoundUtil.volume;


/**
 * NpTestProgram.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-01-23 nsano initial version <br>
 */
public class NpTestProgram {

    private static final Logger logger = Logger.getLogger(NpTestProgram.class.getName());

    static class Context {
        NesBank bank;
        NesMem mem;
        Km6502 cpu;
        NesApu apu;
        NesDmc dmc;
        NesFds fds;
        NesN106 n106;
        NesVrc6 vrc6;
        NesMmc5 mmc5;
        NesFme7 fme7;
        NesVrc7 vrc7;
    }

    private static final int SamplingRate = 44100;
    private static final double NsfClock = 1789772.0;

    private SourceDataLine audioOutput = null;
    private Thread playbackThread;
    private volatile boolean isPlaying = false;

    private final Context chip = new Context();
    private Device.Bus apuBus;
    private Device.Bus stack;
    private Device.Layer layer;
    private DCFilter dcf;
    private Filter lpf;

    // NSF Header info
    private int loadAddress;
    private int initAddress;
    private int playAddress;
    private int speedNtsc;
    private int speedPal;
    private int palNtsc;
    private final byte[] bankSwitch = new byte[8];
    private boolean useVrc6;
    private boolean useVrc7;
    private boolean useFds;
    private boolean useMmc5;
    private boolean useN106;
    private boolean useFme7;
    private byte[] body;
    private int bodySize;
    private int song = 0;

    private double cpuClockRest;
    private double apuClockRest;
    private double vgmSpeed = 1.0;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: NpTestProgram <nsf_file> [song_no]");
            return;
        }
        int song = 0;
        if (args.length > 1) {
            song = Integer.parseInt(args[1]) - 1; // 1-based to 0-based
            if (song < 0) song = 0;
        }

        new NpTestProgram().play(args[0], song);
    }

    public void play(String filename, int song) throws Exception {
        this.song = song;
        byte[] fileBuffer = Files.readAllBytes(Paths.get(filename));
        if (!parseHeader(fileBuffer)) {
            System.err.println("Invalid NSF file");
            return;
        }

        init();

        audioOutput = AudioSystem.getSourceDataLine(new AudioFormat(SamplingRate, 16, 2, true, false));
        audioOutput.open();
        volume(audioOutput, Double.parseDouble(System.getProperty("mdsound.volume", "0.2")));
        audioOutput.start();

        isPlaying = true;
        playbackThread = new Thread(this::playbackLoop);
        playbackThread.start();

        System.out.println("Playing " + filename + " song " + (song + 1));
        System.out.println("Press Enter to stop...");
        System.in.read();

        isPlaying = false;
        playbackThread.join();
        audioOutput.close();
    }

    private boolean parseHeader(byte[] buf) {
        if (ByteUtil.readLeInt(buf, 0) != 0x4d53454e) { // "NESM"
            return false;
        }

        if (buf.length < 0x80) {
            return false;
        }

        // version = buf[0x05] & 0xff;
        // songs = buf[0x06] & 0xff;
        // start = buf[0x07] & 0xff;
        loadAddress = (buf[0x08] & 0xff) | ((buf[0x09] & 0xff) << 8);
        initAddress = (buf[0x0a] & 0xff) | ((buf[0x0B] & 0xff) << 8);
        playAddress = (buf[0x0c] & 0xff) | ((buf[0x0D] & 0xff) << 8);

        speedNtsc = (buf[0x6e] & 0xff) | ((buf[0x6f] & 0xff) << 8);
        System.arraycopy(buf, 112, bankSwitch, 0, 8);
        speedPal = (buf[0x78] & 0xff) | ((buf[0x79] & 0xff) << 8);
        palNtsc = buf[0x7a] & 0xff;

        if (speedPal == 0) speedPal = 0x4e20;
        if (speedNtsc == 0) speedNtsc = 0x411A;

        int soundChip = buf[0x7b] & 0xff;

        useVrc6 = (soundChip & 1) != 0;
        useVrc7 = (soundChip & 2) != 0;
        useFds = (soundChip & 4) != 0;
        useMmc5 = (soundChip & 8) != 0;
        useN106 = (soundChip & 16) != 0;
        useFme7 = (soundChip & 32) != 0;

logger.log(Level.INFO, "vrc6: " + useVrc6 + ", vrc7: " + useVrc7 + ", fds: " + useFds + ", mmc5: " + useMmc5 + ", n106: " + useN106 + ", fme7: " + useFme7);
        body = new byte[buf.length - 0x80];
        System.arraycopy(buf, 128, body, 0, buf.length - 0x80);
        bodySize = buf.length - 0x80;

        return true;
    }

    private void init() {
        chip.bank = new NesBank();
        chip.mem = new NesMem();
        chip.cpu = new Km6502(true);
        chip.apu = new NesApu();
        chip.dmc = new NesDmc();
        chip.fds = new NesFds();
        chip.n106 = new NesN106();
        chip.vrc6 = new NesVrc6();
        chip.mmc5 = new NesMmc5();
        chip.fme7 = new NesFme7();
        chip.vrc7 = new NesVrc7();

        chip.apu.apu.init((int) NsfClock, SamplingRate);
        chip.apu.reset();
        chip.dmc.dmc.init((int) NsfClock, SamplingRate);
        chip.dmc.reset();
        chip.fds.fds.init((int) NsfClock, SamplingRate);
        chip.fds.reset();
        chip.n106.setClock(NsfClock);
        chip.n106.setRate(SamplingRate);
        chip.n106.reset();
        chip.vrc6.setClock(NsfClock);
        chip.vrc6.setRate(SamplingRate);
        chip.vrc6.reset();
        chip.mmc5.setClock(NsfClock);
        chip.mmc5.setRate(SamplingRate);
        chip.mmc5.reset();
        chip.mmc5.setCPU(chip.cpu);
        chip.fme7.setClock(NsfClock);
        chip.fme7.setRate(SamplingRate);
        chip.fme7.reset();
        chip.vrc7.setClock(NsfClock);
        chip.vrc7.setRate(SamplingRate);
        chip.vrc7.reset();

        chip.dmc.dmc.nes_apu = chip.apu.apu;
        chip.dmc.dmc.setAPU(chip.apu.apu);

        stack = new Device.Bus();
        layer = new Device.Layer();
        apuBus = new Device.Bus();

        dcf = new DCFilter();
        lpf = new Filter();
        lpf.setRate(SamplingRate);
        lpf.reset();
        dcf.setRate(SamplingRate);
        dcf.reset();
        dcf.setParam(270, 256 - 92); // Default HPF
        lpf.setParam(4700.0, 112); // Default LPF

        int bmax = 0;
        for (int i = 0; i < 8; i++)
            if (bmax < (bankSwitch[i] & 0xff))
                bmax = bankSwitch[i] & 0xff;

        chip.mem.setImage(body, loadAddress & 0xffff, bodySize);

        if (bmax != 0) {
            chip.bank.setImage(body, loadAddress & 0xffff, bodySize);
            for (int i = 0; i < 8; i++)
                chip.bank.setBankDefault(i + 8, bankSwitch[i] & 0xff);
        }

        stack.detachAll();
        layer.detachAll();
        apuBus.detachAll();

        apuBus.attach(chip.apu);
        apuBus.attach(chip.dmc);

        // Default options
        chip.apu.setOption(NpNesApu.OPT.UNMUTE_ON_RESET.ordinal(), 1);
        chip.apu.setOption(NpNesApu.OPT.NONLINEAR_MIXER.ordinal(), 1);
        chip.apu.setOption(NpNesApu.OPT.PHASE_REFRESH.ordinal(), 1);
        chip.apu.setOption(NpNesApu.OPT.DUTY_SWAP.ordinal(), 0);

        chip.dmc.setOption(NpNesDmc.OPT.ENABLE_4011.ordinal(), 1);
        chip.dmc.setOption(NpNesDmc.OPT.ENABLE_PNOISE.ordinal(), 1);
        chip.dmc.setOption(NpNesDmc.OPT.UNMUTE_ON_RESET.ordinal(), 1);
        chip.dmc.setOption(NpNesDmc.OPT.DPCM_ANTI_CLICK.ordinal(), 0);
        chip.dmc.setOption(NpNesDmc.OPT.NONLINEAR_MIXER.ordinal(), 1);
        chip.dmc.setOption(NpNesDmc.OPT.RANDOMIZE_NOISE.ordinal(), 1);
        chip.dmc.setOption(NpNesDmc.OPT.TRI_MUTE.ordinal(), 1);
        chip.dmc.setOption(NpNesDmc.OPT.RANDOMIZE_TRI.ordinal(), 1);
        chip.dmc.setOption(NpNesDmc.OPT.DPCM_REVERSE.ordinal(), 0);

        if (useFds) {
            chip.fds.setOption(0, 2000); // LPF
            chip.fds.setOption(1, 0); // 4085 Reset
            chip.mem.setFDSMode(true);
            chip.bank.setFDSMode(true);
            chip.bank.setBankDefault(6, bankSwitch[6] & 0xff);
            chip.bank.setBankDefault(7, bankSwitch[7] & 0xff);
            apuBus.attach(chip.fds);
        } else {
            chip.mem.setFDSMode(false);
            chip.bank.setFDSMode(false);
        }
        if (useN106) {
            chip.n106.setOption(0, 0); // Serial
            apuBus.attach(chip.n106);
        }
        if (useVrc6) {
            apuBus.attach(chip.vrc6);
        }
        if (useMmc5) {
            chip.mmc5.setOption(0, 1); // NonLinear
            chip.mmc5.setOption(1, 1); // PhaseRefresh
            apuBus.attach(chip.mmc5);
        }
        if (useFme7) {
            apuBus.attach(chip.fme7);
        }
        if (useVrc7) {
            apuBus.attach(chip.vrc7);
        }

        if (bmax > 0) layer.attach(chip.bank);
        layer.attach(chip.mem);

        stack.attach(apuBus);
        stack.attach(layer);

        chip.cpu.setMemory(stack);
        chip.dmc.setMemory(stack);

        reset();
    }

    private void reset() {
        apuClockRest = 0.0;
        cpuClockRest = 0.0;

        Region region = getRegion(palNtsc);
        double speed = 1000000.0 / ((region == Region.NTSC) ? speedNtsc : speedPal);

        layer.reset();
        chip.cpu.reset();

        chip.cpu.start(initAddress, playAddress, speed, song, (region == Region.PAL) ? 1 : 0, 0);
    }

    private enum Region {
        NTSC,
        PAL
    }

    private static Region getRegion(int flags) {
        if ((flags & 2) != 0) { // dual mode
            return ((flags & 1) != 0) ? Region.PAL : Region.NTSC;
        }
        return (flags == 1) ? Region.PAL : Region.NTSC;
    }

    private void playbackLoop() {
        int bufferSize = 2048; // samples
        short[] sampleBuffer = new short[bufferSize * 2]; // stereo
        byte[] audioBuffer = new byte[bufferSize * 4]; // 16-bit stereo

        while (isPlaying) {
            render(sampleBuffer, bufferSize);

            for (int i = 0; i < bufferSize; i++) {
                ByteUtil.writeLeShort(sampleBuffer[i * 2], audioBuffer, i * 4);
                ByteUtil.writeLeShort(sampleBuffer[i * 2 + 1], audioBuffer, i * 4 + 2);
            }
            audioOutput.write(audioBuffer, 0, audioBuffer.length);
        }
    }

    private void render(short[] b, int length) {
        int[] buf = new int[2];
        int[] out = new int[2];

        double apuClockPerSample = chip.cpu.NES_BASECYCLES / SamplingRate;
        double cpuClockPerSample = apuClockPerSample * vgmSpeed;

        for (int i = 0; i < length; i++) {
            // tick CPU
            cpuClockRest += cpuClockPerSample;
            int cpuClocks = (int) cpuClockRest;
            if (cpuClocks > 0) {
                int realCpuClocks = chip.cpu.exec(cpuClocks);
                cpuClockRest -= realCpuClocks;

                // tick APU frame sequencer
                chip.dmc.dmc.tickFrameSequence(realCpuClocks);
                if (useMmc5)
                    chip.mmc5.tickFrameSequence(realCpuClocks);
            }

            // tick APU / expansions
            apuClockRest += apuClockPerSample;
            int apuClocks = (int) apuClockRest;
            if (apuClocks > 0) {
                apuClockRest -= apuClocks;
            }

            // render Output
            chip.apu.tick(apuClocks);
            chip.apu.render(buf);
            out[0] = buf[0] * 2;
            out[1] = buf[1] * 2;

            chip.dmc.tick(apuClocks);
            chip.dmc.render(buf);
            out[0] += buf[0] * 2;
            out[1] += buf[1] * 2;

            if (useFds) {
                chip.fds.tick(apuClocks);
                chip.fds.render(buf);
                out[0] += buf[0] * 2;
                out[1] += buf[1] * 2;
            }

            if (useN106) {
                chip.n106.tick(apuClocks);
                chip.n106.render(buf);
                out[0] += buf[0] * 16;
                out[1] += buf[1] * 16;
            }

            if (useVrc6) {
                chip.vrc6.tick(apuClocks);
                chip.vrc6.render(buf);
                out[0] += buf[0] * 16;
                out[1] += buf[1] * 16;
            }

            if (useMmc5) {
                chip.mmc5.tick(apuClocks);
                chip.mmc5.render(buf);
                out[0] += buf[0] * 16;
                out[1] += buf[1] * 16;
            }

            if (useFme7) {
                chip.fme7.tick(apuClocks);
                chip.fme7.render(buf);
                out[0] += buf[0] * 32;
                out[1] += buf[1] * 32;
            }

            if (useVrc7) {
                chip.vrc7.tick(apuClocks);
                chip.vrc7.render(buf);
                out[0] += buf[0] * 16;
                out[1] += buf[1] * 16;
            }

            dcf.fastRender(out);
            lpf.fastRender(out);

            // Master volume adjustment (approximate)
            out[0] = (out[0] * 0x80) >> 9;
            out[1] = (out[1] * 0x80) >> 9;

            if (out[0] < -32767) out[0] = -32767;
            else if (out[0] > 32767) out[0] = 32767;

            if (out[1] < -32767) out[1] = -32767;
            else if (out[1] > 32767) out[1] = 32767;

            b[i * 2] = (short) out[0];
            b[i * 2 + 1] = (short) out[1];
        }
    }
}