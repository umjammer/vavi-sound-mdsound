package mdsound.chips;

import java.util.Arrays;


/**
 * WSwan.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2022-07-08 nsano initial version <br>
 */
public class WSwan {

    public static final int DEFAULT_CLOCK = 3072000;

    /**
     * Initial I/O values
     */
    private static final int[] initialIoValue = new int[] {
            0x00, // 0
            0x00, // 1
            0x9d, // 2
            0xbb, // 3
            0x00, // 4
            0x00, // 5
            0x00, // 6
            0x26, // 7
            0xfe, // 8
            0xde, // 9
            0xf9, // a
            0xfb, // b
            0xdb, // c
            0xd7, // d
            0x7f, // e
            0xf5, // f
            0x00, // 10
            0x00, // 11
            0x00, // 12
            0x00, // 13
            0x01, // 14
            0x00, // 15
            0x9e, // 16
            0x9b, // 17
            0x00, // 18
            0x00, // 19
            0x00, // 1a
            0x00, // 1b
            0x99, // 1c
            0xfd, // 1d
            0xb7, // 1e
            0xdf, // 1f
            0x30, // 20
            0x57, // 21
            0x75, // 22
            0x76, // 23
            0x15, // 24
            0x73, // 25
            0x77, // 26
            0x77, // 27
            0x20, // 28
            0x75, // 29
            0x50, // 2a
            0x36, // 2b
            0x70, // 2c
            0x67, // 2d
            0x50, // 2e
            0x77, // 2f
            0x57, // 30
            0x54, // 31
            0x75, // 32
            0x77, // 33
            0x75, // 34
            0x17, // 35
            0x37, // 36
            0x73, // 37
            0x50, // 38
            0x57, // 39
            0x60, // 3a
            0x77, // 3b
            0x70, // 3c
            0x77, // 3d
            0x10, // 3e
            0x73, // 3f
            0x00, // 40
            0x00, // 41
            0x00, // 42
            0x00, // 43
            0x00, // 44
            0x00, // 45
            0x00, // 46
            0x00, // 47
            0x00, // 48
            0x00, // 49
            0x00, // 4a
            0x00, // 4b
            0x00, // 4c
            0x00, // 4d
            0x00, // 4e
            0x00, // 4f
            0x00, // 50
            0x00, // 51
            0x00, // 52
            0x00, // 53
            0x00, // 54
            0x00, // 55
            0x00, // 56
            0x00, // 57
            0x00, // 58
            0x00, // 59
            0x00, // 5a
            0x00, // 5b
            0x00, // 5c
            0x00, // 5d
            0x00, // 5e
            0x00, // 5f
            0x0a, // 60
            0x00, // 61
            0x00, // 62
            0x00, // 63
            0x00, // 64
            0x00, // 65
            0x00, // 66
            0x00, // 67
            0x00, // 68
            0x00, // 69
            0x00, // 6a
            0x0f, // 6b
            0x00, // 6c
            0x00, // 6d
            0x00, // 6e
            0x00, // 6f
            0x00, // 70
            0x00, // 71
            0x00, // 72
            0x00, // 73
            0x00, // 74
            0x00, // 75
            0x00, // 76
            0x00, // 77
            0x00, // 78
            0x00, // 79
            0x00, // 7a
            0x00, // 7b
            0x00, // 7c
            0x00, // 7d
            0x00, // 7e
            0x00, // 7f
            0xff, // 80
            0x07, // 81
            0xff, // 82
            0x07, // 83
            0xff, // 84
            0x07, // 85
            0xff, // 86
            0x07, // 87
            0x00, // 88
            0x00, // 89
            0x00, // 8a
            0x00, // 8b
            0x00, // 8c
            0x1f, // 8d 1d ?
            0x00, // 8e
            0x00, // 8f
            0x00, // 90
            0x00, // 91
            0x00, // 92
            0x00, // 93
            0x00, // 94
            0x00, // 95
            0x00, // 96
            0x00, // 97
            0x00, // 98
            0x00, // 99
            0x00, // 9a
            0x00, // 9b
            0x00, // 9c
            0x00, // 9d
            0x03, // 9e
            0x00, // 9f
            0x87 - 2, // a0
            0x00, // a1
            0x00, // a2
            0x00, // a3
            0x0, // a4 2b
            0x0, // a5 7f
            0x4f, // a6
            0xff, // a7 cf ?
            0x00, // a8
            0x00, // a9
            0x00, // aa
            0x00, // ab
            0x00, // ac
            0x00, // ad
            0x00, // ae
            0x00, // af
            0x00, // b0
            0xdb, // b1
            0x00, // b2
            0x00, // b3
            0x00, // b4
            0x40, // b5
            0x00, // b6
            0x00, // b7
            0x00, // b8
            0x00, // b9
            0x01, // ba
            0x00, // bb
            0x42, // bc
            0x00, // bd
            0x83, // be
            0x00, // bf
            0x2f, // c0
            0x3f, // c1
            0xff, // c2
            0xff, // c3
            0x00, // c4
            0x00, // c5
            0x00, // c6
            0x00, // c7

            0xd1, // c8?
            0xd1, // c9
            0xd1, // ca
            0xd1, // cb
            0xd1, // cc
            0xd1, // cd
            0xd1, // ce
            0xd1, // cf
            0xd1, // d0
            0xd1, // d1
            0xd1, // d2
            0xd1, // d3
            0xd1, // d4
            0xd1, // d5
            0xd1, // d6
            0xd1, // d7
            0xd1, // d8
            0xd1, // d9
            0xd1, // da
            0xd1, // db
            0xd1, // dc
            0xd1, // dd
            0xd1, // de
            0xd1, // df
            0xd1, // e0
            0xd1, // e1
            0xd1, // e2
            0xd1, // e3
            0xd1, // e4
            0xd1, // e5
            0xd1, // e6
            0xd1, // e7
            0xd1, // e8
            0xd1, // e9
            0xd1, // ea
            0xd1, // eb
            0xd1, // ec
            0xd1, // ed
            0xd1, // ee
            0xd1, // ef
            0xd1, // f0
            0xd1, // f1
            0xd1, // f2
            0xd1, // f3
            0xd1, // f4
            0xd1, // f5
            0xd1, // f6
            0xd1, // f7
            0xd1, // f8
            0xd1, // f9
            0xd1, // fa
            0xd1, // fb
            0xd1, // fc
            0xd1, // fd
            0xd1, // fe
            0xd1 //ff
    };

    private static final int SNDP = 0x80;
    private static final int SNDV = 0x88;
    private static final int SNDSWP = 0x8c;
    private static final int SWPSTP = 0x8d;
    private static final int NSCTL = 0x8e;
    private static final int WAVDTP = 0x8f;
    private static final int SNDMOD = 0x90;
    private static final int SNDOUT = 0x91;
    private static final int PCSRL = 0x92;
    private static final int PCSRH = 0x93;
    private static final int DMASL = 0x40;
    private static final int DMASH = 0x41;
    private static final int DMASB = 0x42;
    private static final int DMADB = 0x43;
    private static final int DMADL = 0x44;
    private static final int DMADH = 0x45;
    private static final int DMACL = 0x46;
    private static final int DMACH = 0x47;
    private static final int DMACTL = 0x48;
    private static final int SDMASL = 0x4a;
    private static final int SDMASH = 0x4b;
    private static final int SDMASB = 0x4c;
    private static final int SDMACL = 0x4e;
    private static final int SDMACH = 0x4f;
    private static final int SDMACTL = 0x52;

    // SoundDMA transfer interval
    // I don't know the actual numbers, so it's just a guess.
    // Considering the sampling period, we did the following:
    // 12KHz = 1.00HBlank = 256 cycles interval
    // 16KHz = 0.75HBlank = 192 cycles interval
    // 20KHz = 0.60HBlank = 154 cycles interval
    // 24KHz = 0.50HBlank = 128 cycles interval
    private static final int[] DMACycles = new int[] {256, 192, 154, 128};

    private static class Channel {
        private int wave;
        private int lVol;
        private int rVol;
        private int offset;
        private int delta;
        private int pos;
        private int muted;

        public void reset() {
            this.wave = 0;
            this.lVol = 0;
            this.rVol = 0;
            this.offset = 0;
            this.delta = 0;
            this.pos = 0;
            this.muted = 0;
        }
    }

    /** Ratio */
    private static class RatioCounter {
        /** counter increment */
        private long inc;
        /** current value */
        private long val;

        private void setRatio(int mul, int div) {
            this.inc = ((mul << 20) + div / 2) / div;
        }

        private void step() {
            this.val += this.inc;
        }

        private void reset() {
            this.val = 0;
        }

        private void resetPreStep() {
            this.val = (1 << 20) - this.inc;
        }

        private int getVal() {
            return (int) (this.val >> 20);
        }

        private void mask() {
            this.val &= ((1 << 20) - 1);
        }
    }

    private final Channel[] audios = {
            new Channel(), new Channel(), new Channel(), new Channel()
    };
    private final RatioCounter blankTimer = new RatioCounter();
    private int sweepTime;
    private int sweepStep;
    private int sweepCount;
    private int sweepFreq;
    private int noiseType;
    private int noiseRng;
    private int mainVolume;
    private int pcmVolumeLeft;
    private int pcmVolumeRight;

    private final byte[] ioRam = new byte[0x100];
    private byte[] internalRam;

    private int clock;
    private int sampleRate;
    private float ratEmul;

    public WSwan() {
        this.clock = DEFAULT_CLOCK;
        sampleRate = this.clock / 128;
    }

    public void init(int clock) {
        this.clock = clock;
        this.sampleRate = this.clock / 128;

        // actual size is 64 KB, but the audio chips can only access 16 KB
        this.internalRam = new byte[0x4000];

        this.ratEmul = (float) this.clock * 65536.0f / (float) this.sampleRate;
        // one step every 256 cycles
        this.blankTimer.setRatio(this.clock, this.sampleRate * 256);

        setMuteMask(0x00);
    }

    public void reset() {
        int muteMask = getMuteMask();
        Arrays.stream(this.audios).forEach(Channel::reset);
        setMuteMask(muteMask);

        this.sweepTime = 0;
        this.sweepStep = 0;
        this.noiseType = 0;
        this.noiseRng = 1;
        this.mainVolume = 0x02; // 0x04
        this.pcmVolumeLeft = 0;
        this.pcmVolumeRight = 0;

        this.blankTimer.reset();

        for (int i = 0x80; i < 0xc9; i++)
            write(i, initialIoValue[i]);
    }

    public void stop() {
    }

    /** It is intended to be equivalent to OSWAN's pseudorandom number processing. */
    private static final int[] noiseMask = {
            0b11,
            0b110011,
            0b11011,
            0b1010011,
            0b101,
            0b1001,
            0b10001,
            0b11101
    };

    private static final int[] noiseBit = {
            0b1000_0000_0000_0000,
            0b0100_0000_0000_0000,
            0b0010_0000_0000_0000,
            0b0001_0000_0000_0000,
            0b0000_1000_0000_0000,
            0b0000_0100_0000_0000,
            0b0000_0010_0000_0000,
            0b0000_0001_0000_0000
    };

    private double sampleCounter = 0;
    private final int[][] frm = {new int[1], new int[1]};
    private final int[][] before = {new int[1], new int[1]};

    public void update(int length, int[][] outputs) {
        for (int i = 0; i < length; i++) {
            outputs[0][i] = 0;
            outputs[1][i] = 0;

            sampleCounter += (this.clock / 128.0) / sampleRate;
            int upc = (int) sampleCounter;
            while (sampleCounter >= 1) {
                updateInternal(1, frm);

                outputs[0][i] += frm[0][0];
                outputs[1][i] += frm[1][0];

                sampleCounter -= 1.0;
            }

            if (upc != 0) {
                outputs[0][i] /= upc;
                outputs[1][i] /= upc;
                before[0][i] = outputs[0][i];
                before[1][i] = outputs[1][i];
            } else {
                outputs[0][i] = before[0][i];
                outputs[1][i] = before[1][i];
            }

            outputs[0][i] <<= 2;
            outputs[1][i] <<= 2;
        }
    }

    private void updateInternal(int length, int[][] buffer) {
        int[] bufL = buffer[0];
        int[] bufR = buffer[1];
        for (int i = 0; i < length; i++) {
            int swpCount;

            this.blankTimer.step();
            for (swpCount = this.blankTimer.getVal(); swpCount > 0; swpCount--)
                process();
            this.blankTimer.mask();

            int l = 0, r = 0;

            for (int ch = 0; ch < 4; ch++) {
                if (this.audios[ch].muted != 0)
                    continue;

                if ((ch == 1) && ((this.ioRam[SNDMOD] & 0x20) != 0)) {
                    // Voice Output
                    int w = this.ioRam[0x89] & 0xff;
                    w -= 0x80;
                    l += this.pcmVolumeLeft * w;
                    r += this.pcmVolumeRight * w;
                } else if ((this.ioRam[SNDMOD] & (1 << ch)) != 0) {
                    if ((ch == 3) && ((this.ioRam[SNDMOD] & 0x80) != 0)) {
                        // Noise

                        int masked, xorReg;

                        this.audios[ch].offset += this.audios[ch].delta;
                        int cnt = this.audios[ch].offset >> 16;
                        this.audios[ch].offset &= 0xffff;
                        while (cnt > 0) {
                            cnt--;

                            this.noiseRng &= noiseBit[this.noiseType] - 1;
                            if (this.noiseRng == 0) this.noiseRng = noiseBit[this.noiseType] - 1;

                            masked = this.noiseRng & noiseMask[this.noiseType];
                            xorReg = 0;
                            while (masked != 0) {
                                xorReg ^= masked & 1;
                                masked >>= 1;
                            }
                            if (xorReg != 0)
                                this.noiseRng |= noiseBit[this.noiseType];
                            this.noiseRng >>= 1;
                        }

                        this.ioRam[PCSRL] = (byte) (this.noiseRng & 0xff);
                        this.ioRam[PCSRH] = (byte) ((this.noiseRng >> 8) & 0x7f);

                        int w = (this.noiseRng & 1) != 0 ? 0x7f : -0x80;
                        l += this.audios[ch].lVol * w;
                        r += this.audios[ch].rVol * w;
                    } else {
                        this.audios[ch].offset += this.audios[ch].delta;
                        int cnt = (this.audios[ch].offset >> 16) & 0xff;
                        this.audios[ch].offset &= 0xffff;
                        this.audios[ch].pos += cnt;
                        this.audios[ch].pos &= 0x1f;
                        int w = this.internalRam[(this.audios[ch].wave & 0xfff0) + (this.audios[ch].pos >> 1)] & 0xff;
                        if ((this.audios[ch].pos & 1) == 0)
                            w = (w << 4) & 0xf0; // Low nibble
                        else
                            w = w & 0xf0; // High nibble
                        w -= 0x80;
                        l += this.audios[ch].lVol * w;
                        r += this.audios[ch].rVol * w;
                    }
                }
            }

            bufL[i] = l * this.mainVolume;
            bufR[i] = r * this.mainVolume;
        }
    }

    public void write(int port, int value) {
        int i;
        float freq;

        this.ioRam[port] = (byte) value;

        switch (port) {
        // About frequency registers 0x80-0x87
        // - In the song 0x0f by "Rockman & Forte", the sound with frequency = 0xffFF is not needed.
        // - The noise in the song 0x0d from "Digimon D Project" is generated at frequency 0x07ff.
        // -> In other words, it means that no sound will be made when the value is 0xffff.
        //    But even when it's 0x07ff, it doesn't make any sound, maybe it just makes noise.
        case 0x80:
        case 0x81:
            i = ((this.ioRam[0x81] & 0xff) << 8) + (this.ioRam[0x80] & 0xff);
            if (i == 0xffff)
                freq = 0;
            else
                freq = 1.0f / (2048 - (i & 0x7ff));
            this.audios[0].delta = (int) (freq * this.ratEmul);
            break;
        case 0x82:
        case 0x83:
            i = ((this.ioRam[0x83] & 0xff) << 8) + (this.ioRam[0x82] & 0xff);
            if (i == 0xffff)
                freq = 0;
            else
                freq = 1.0f / (2048 - (i & 0x7ff));
            this.audios[1].delta = (int) (freq * this.ratEmul);
            break;
        case 0x84:
        case 0x85:
            i = ((this.ioRam[0x85] & 0xff) << 8) + (this.ioRam[0x84] & 0xff);
            this.sweepFreq = i;
            if (i == 0xffff)
                freq = 0;
            else
                freq = 1.0f / (2048 - (i & 0x7ff));
            this.audios[2].delta = (int) (freq * this.ratEmul);
            break;
        case 0x86:
        case 0x87:
            i = ((this.ioRam[0x87] & 0xff) << 8) + (this.ioRam[0x86] & 0xff);
            if (i == 0xffff)
                freq = 0;
            else
                freq = 1.0f / (2048 - (i & 0x7ff));
            this.audios[3].delta = (int) (freq * this.ratEmul);
            break;
        case 0x88:
            this.audios[0].lVol = (value >> 4) & 0xf;
            this.audios[0].rVol = value & 0xf;
            break;
        case 0x89:
            this.audios[1].lVol = (value >> 4) & 0xf;
            this.audios[1].rVol = value & 0xf;
            break;
        case 0x8A:
            this.audios[2].lVol = (value >> 4) & 0xf;
            this.audios[2].rVol = value & 0xf;
            break;
        case 0x8B:
            this.audios[3].lVol = (value >> 4) & 0xf;
            this.audios[3].rVol = value & 0xf;
            break;
        case 0x8C:
            this.sweepStep = value;
            break;
        case 0x8D:
            // The sweep interval is 1/375[s] = 2.666..[ms]
            // In terms of CPU clock, 3072000/375 = 8192[cycles]
            // If the setting value here is n, the sweep will be performed at intervals of 8192[cycles]*(n+1).
            //
            // In terms of HBlank (256 cycles) intervals,
            //　8192/256 = 32
            // So, the interval is 32[HBlank]*(n+1).
            this.sweepTime = ((value + 1) << 5) & 0xffff;
            this.sweepCount = this.sweepTime;
            break;
        case 0x8E:
            this.noiseType = value & 7;
            if ((value & 8) != 0) this.noiseRng = 1; // Noise Counter Reset
            break;
        case 0x8F:
            this.audios[0].wave = (value << 6) & 0xffff;
            this.audios[1].wave = (this.audios[0].wave + 0x10) & 0xffff;
            this.audios[2].wave = (this.audios[1].wave + 0x10) & 0xffff;
            this.audios[3].wave = (this.audios[2].wave + 0x10) & 0xffff;
            break;
        case 0x90: // SNDMOD
            break;
        case 0x91:
            // The volume adjustment here seems to be only for the built-in speaker, so
            // It seems that there is no problem if you make it recognize that headphones are connected.
            this.ioRam[port] |= (byte) 0x80;
            break;
        case 0x92:
        case 0x93:
            break;
        case 0x94:
            this.pcmVolumeLeft = (value & 0xc) * 2;
            this.pcmVolumeRight = ((value << 2) & 0xc) * 2;
            break;
        case 0x52:
            //if (value&0x80)
            // ws_timer_set(2, DMACycles[value&3]);
            break;
        }
    }

    public int read(int port) {
        return this.ioRam[port] & 0xff;
    }

    // Called at HBlank intervals
    // Note: Must be called every 256 cycles (3072000 Hz clock), i.e. at 12000 Hz
    private void process() {
        if (this.sweepStep != 0 && (this.ioRam[SNDMOD] & 0x40) != 0) {
            if (this.sweepCount < 0) {
                this.sweepCount = this.sweepTime;
                this.sweepFreq = (this.sweepFreq + this.sweepStep) & 0xffff;
                this.sweepFreq &= 0x7ff;

                float freq = 1.0f / (2048 - this.sweepFreq);
                this.audios[2].delta = (int) (freq * this.ratEmul);
            }
            this.sweepCount = (this.sweepCount - 1) & 0xffff;
        }
    }

    private void soundDma() {
        if ((this.ioRam[SDMACTL] & 0x88) == 0x80) {
            int i = ((this.ioRam[SDMACH] & 0xff) << 8) | (this.ioRam[SDMACL] & 0xff);
            int j = ((this.ioRam[SDMASB] & 0xff) << 16) | ((this.ioRam[SDMASH] & 0xff) << 8) | (this.ioRam[SDMASL] & 0xff);
            int b = this.internalRam[j & 0x3fff] & 0xff;

            this.ioRam[0x89] = (byte) b;
            i--;
            j++;
            if (i < 32) {
                i = 0;
                this.ioRam[SDMACTL] &= 0x7F;
            } else {
                // set DMA timer
                //ws_timer_set(2, DMACycles[SDMACTL&3]);
            }
            this.ioRam[SDMASB] = (byte) ((j >> 16) & 0xff);
            this.ioRam[SDMASH] = (byte) ((j >> 8) & 0xff);
            this.ioRam[SDMASL] = (byte) (j & 0xff);
            this.ioRam[SDMACH] = (byte) ((i >> 8) & 0xff);
            this.ioRam[SDMACL] = (byte) (i & 0xff);
        }
    }

    public void writeRam(int offset, int value) {
        // RAM - 16 KB (WS) / 64 KB (WSC) internal RAM
        this.internalRam[offset & 0x3fff] = (byte) value;
    }

    public int readRam(int offset) {
        return this.internalRam[offset & 0x3fff] & 0xff;
    }

    private void setMuteMask(int muteMask) {
        for (int c = 0; c < 4; c++)
            this.audios[c].muted = (muteMask >> c) & 0x01;
    }

    private int getMuteMask() {
        int muteMask = 0x00;
        for (int c = 0; c < 4; c++)
            muteMask |= this.audios[c].muted << c;

        return muteMask;
    }
}
