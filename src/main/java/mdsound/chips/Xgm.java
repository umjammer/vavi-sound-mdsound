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
        private final int addr = 0;
        private final int size = 0;
    }

    /** Information on each channel */
    private final Pcm[][] xgmPcm = {new Pcm[4], new Pcm[4]};
    /** PCM data set */
    private final byte[][] pcmBuf = {null, null};
    /** PCM Table */
    private final SampleID[][] sampleID = {new SampleID[63], new SampleID[63]};

    private final double[] pcmStep = new double[2];
    private final double[] pcmExecDelta = new double[2];
    private final byte[] dacEnable = {0, 0};
    private final Object[] lockobj = {new Object(), new Object()};
    private boolean ox2b = false;

    public void reset(int chipId, int sampleRate) {
        pcmStep[chipId] = sampleRate / 14000.0;
        stop(chipId);
    }

    public void stop(int chipId) {
        pcmExecDelta[chipId] = 0.0;
        dacEnable[chipId] = 0;
        ox2b = false;

        for (int i = 0; i < 4; i++) {
            if (xgmPcm[chipId][i] == null) xgmPcm[chipId][i] = new Pcm();
            xgmPcm[chipId][i].isPlaying = false;
        }
        for (int i = 0; i < 63; i++) sampleID[chipId][i] = new SampleID();
    }

    public void write(int chipId, int port, int adr, int data) {
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
            dacEnable[chipId] = (byte) (data & 0x80);
            ox2b = false;
        }
    }

    public void update(int chipId, int samples, QuadFunction<Byte, Integer, Integer, Integer, Integer> Write) {
        for (int i = 0; i < samples; i++) {
            while ((int) pcmExecDelta[chipId] <= 0) {
                write(chipId, 0, 0, 0x2a);
                write(chipId, 0, 1, oneFramePCM(chipId));
                pcmExecDelta[chipId] += pcmStep[chipId];
            }
            pcmExecDelta[chipId] -= 1.0;
        }
    }

    public void playPCM(int chipId, int X, int id) {
        int priority = X & 0xc;
        int channel = X & 0x3;

        synchronized (lockobj[chipId]) {
            // Can only be played if it has high priority or is muted
            if (xgmPcm[chipId][channel].priority > priority && xgmPcm[chipId][channel].isPlaying) return;

            if (id == 0 || id > sampleID[chipId].length || sampleID[chipId][id - 1].size == 0) {
                // If the ID is 0 or an undefined ID is specified, the sound will stop.
                xgmPcm[chipId][channel].priority = 0;
                xgmPcm[chipId][channel].isPlaying = false;
                return;
            }

            // Sound start instruction
            xgmPcm[chipId][channel].priority = priority;
            xgmPcm[chipId][channel].startAddr = sampleID[chipId][id - 1].addr;
            xgmPcm[chipId][channel].endAddr = sampleID[chipId][id - 1].addr + sampleID[chipId][id - 1].size;
            xgmPcm[chipId][channel].addr = sampleID[chipId][id - 1].addr;
            xgmPcm[chipId][channel].inst = id;
            xgmPcm[chipId][channel].isPlaying = true;
        }
    }

    private short oneFramePCM(int chipId) {
        if (dacEnable[chipId] == 0) return 0x80; // 0x80: Silence (or rather the center of the waveform?)

        // Waveform Synthesis
        int o = 0;
        synchronized (lockobj[chipId]) {
            for (int i = 0; i < 4; i++) {
                if (!xgmPcm[chipId][i].isPlaying) continue;
                byte d = xgmPcm[chipId][i].addr < pcmBuf[chipId].length ? pcmBuf[chipId][xgmPcm[chipId][i].addr++] : (byte) 0;
                o += d;
                xgmPcm[chipId][i].data = Math.abs(d);
                if (xgmPcm[chipId][i].addr >= xgmPcm[chipId][i].endAddr) {
                    xgmPcm[chipId][i].isPlaying = false;
                    xgmPcm[chipId][i].data = 0;
                }
            }
        }

        o = (short) Math.min(Math.max(o, Byte.MIN_VALUE + 1), Byte.MAX_VALUE); // clipping
        o += 0x80; // Move to the center position in OPN2

        return (short) o;
    }
}
