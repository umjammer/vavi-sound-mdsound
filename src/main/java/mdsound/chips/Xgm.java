package mdsound.chips;

import dotnet4j.util.compat.QuadFunction;


public class Xgm {

    private static class Pcm {
        private int priority = 0;
        private int startAddr = 0;
        private int endAddr = 0;
        private int addr = 0;
        private int inst = 0;
        private boolean isPlaying = false;
        private int data = 0;
    }

    private static class SampleID {
        private int addr = 0;
        private int size = 0;
    }

    /** Information on each channel */
    private final Pcm[] xgmPcm = new Pcm[4];
    /** PCM data set */
    private final byte[] pcmBuf = null;
    /** PCM Table */
    private final SampleID[] sampleID = new SampleID[63];

    private double pcmStep;
    private double pcmExecDelta;
    private boolean dacEnable = false;
    private final Object lockObj = new Object();
    private boolean ox2b = false;

    public void reset(int sampleRate) {
        pcmStep = sampleRate / 14000.0;
        stop();
    }

    public void stop() {
        pcmExecDelta = 0.0;
        dacEnable = false;
        ox2b = false;

        for (int i = 0; i < 4; i++) {
            if (xgmPcm[i] == null) xgmPcm[i] = new Pcm();
            xgmPcm[i].isPlaying = false;
        }
        for (int i = 0; i < 63; i++) sampleID[i] = new SampleID();
    }

    public void write(int port, int adr, int data) {
        //
        // OPN2 is a type in which the address and data are sent in two separate transmissions.
        // First address (adr = 0)
        // Second data (adr = 1)
        //

        if (port + adr == 0) {
            // 0x2b : Address containing the DAC switch
            if (data == 0x2b) ox2b = true;
            else ox2b = false;
        }
        if (ox2b && port == 0 && adr == 1) {
            // 0x80 : Bit 7 (1: ON, 0: OFF) indicates the DAC switch
            dacEnable = (data & 0x80) != 0;
            ox2b = false;
        }
    }

    public void update(int samples, QuadFunction<Byte, Integer, Integer, Integer, Integer> write) {
        for (int i = 0; i < samples; i++) {
            while ((int) pcmExecDelta <= 0) {
                write(0, 0, 0x2a);
                write(0, 1, oneFramePCM());
                pcmExecDelta += pcmStep;
            }
            pcmExecDelta -= 1.0;
        }
    }

    public void playPCM(int x, int id) {
        int priority = x & 0xc;
        int channel = x & 0x3;

        synchronized (lockObj) {
            // Can only be played if it has high priority or is muted
            if (xgmPcm[channel].priority > priority && xgmPcm[channel].isPlaying) return;

            if (id == 0 || id > sampleID.length || sampleID[id - 1].size == 0) {
                // If the ID is 0 or an undefined ID is specified, the sound will stop.
                xgmPcm[channel].priority = 0;
                xgmPcm[channel].isPlaying = false;
                return;
            }

            // Sound start instruction
            xgmPcm[channel].priority = priority;
            xgmPcm[channel].startAddr = sampleID[id - 1].addr;
            xgmPcm[channel].endAddr = sampleID[id - 1].addr + sampleID[id - 1].size;
            xgmPcm[channel].addr = sampleID[id - 1].addr;
            xgmPcm[channel].inst = id;
            xgmPcm[channel].isPlaying = true;
        }
    }

    private short oneFramePCM() {
        if (!dacEnable) return 0x80; // 0x80: Silence (or rather the center of the waveform?)

        // Waveform Synthesis
        int o = 0;
        synchronized (lockObj) {
            for (int i = 0; i < 4; i++) {
                if (!xgmPcm[i].isPlaying) continue;
                byte d = xgmPcm[i].addr < pcmBuf.length ? pcmBuf[xgmPcm[i].addr++] : (byte) 0;
                o += d;
                xgmPcm[i].data = Math.abs(d);
                if (xgmPcm[i].addr >= xgmPcm[i].endAddr) {
                    xgmPcm[i].isPlaying = false;
                    xgmPcm[i].data = 0;
                }
            }
        }

        o = (short) Math.clamp(o, Byte.MIN_VALUE + 1, Byte.MAX_VALUE); // clipping
        o += 0x80; // Move to the center position in OPN2

        return (short) o;
    }
}
