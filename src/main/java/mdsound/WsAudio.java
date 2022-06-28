package mdsound;

import java.util.Arrays;


public class WsAudio extends Instrument.BaseInstrument {

    private static final int DefaultWSwanClockValue = 3072000;
    private WSAState[] chip = new WSAState[] {new WSAState(DefaultWSwanClockValue), new WSAState(DefaultWSwanClockValue)};
    private int sampleRate = 44100;
    private int masterClock = DefaultWSwanClockValue;
    private double sampleCounter = 0;
    private int[][] frm = new int[][] {new int[1], new int[1]};
    private int[][] before = new int[][] {new int[1], new int[1]};

    @Override
    public String getName() {
        return "WonderSwan";
    }

    @Override
    public String getShortName() {
        return "WSwan";
    }

    @Override
    public void reset(byte chipId) {
        chip[chipId].ws_audio_reset();
    }

    @Override
    public int start(byte chipId, int clock) {
        return start(chipId, clock, DefaultWSwanClockValue);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        chip[chipId].init(clock, clockValue);
        sampleRate = clock;
        masterClock = clockValue;

        visVolume = new int[2][][];
        visVolume[0] = new int[2][];
        visVolume[1] = new int[2][];
        visVolume[0][0] = new int[2];
        visVolume[1][0] = new int[2];
        visVolume[0][1] = new int[2];
        visVolume[1][1] = new int[2];

        return clock;
    }

    @Override
    public void stop(byte chipId) {
        chip[chipId].stop();
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        for (int i = 0; i < samples; i++) {
            outputs[0][i] = 0;
            outputs[1][i] = 0;

            sampleCounter += (masterClock / 128.0) / sampleRate;
            int upc = (int) sampleCounter;
            while (sampleCounter >= 1) {
                chip[chipId].update(1, frm);

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

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        chip[chipId].writeAudioPort((byte) (adr + 0x80), (byte) data);
        return 0;
    }

    public int WriteMem(byte chipId, int adr, int data) {
        chip[chipId].writeRamByte(adr, (byte) data);
        return 0;
    }

    public void SetMute(byte chipId, int v) {
    }

    private static class WSAState {

        /**
         * Initial I/O values
         */
        private static final byte[] initialIoValue = new byte[] {
                0x00, // 0
                0x00, // 1
                (byte) 0x9d, // 2
                (byte) 0xbb, // 3
                0x00, // 4
                0x00, // 5
                0x00, // 6
                0x26, // 7
                (byte) 0xfe, // 8
                (byte) 0xde, // 9
                (byte) 0xf9, // a
                (byte) 0xfb, // b
                (byte) 0xdb, // c
                (byte) 0xd7, // d
                0x7f, // e
                (byte) 0xf5, // f
                0x00, // 10
                0x00, // 11
                0x00, // 12
                0x00, // 13
                0x01, // 14
                0x00, // 15
                (byte) 0x9e, // 16
                (byte) 0x9b, // 17
                0x00, // 18
                0x00, // 19
                0x00, // 1a
                0x00, // 1b
                (byte) 0x99, // 1c
                (byte) 0xfd, // 1d
                (byte) 0xb7, // 1e
                (byte) 0xdf, // 1f
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
                (byte) 0xFF, // 80
                0x07, // 81
                (byte) 0xFF, // 82
                0x07, // 83
                (byte) 0xFF, // 84
                0x07, // 85
                (byte) 0xFF, // 86
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
                (byte) 0x87 - 2, // a0
                0x00, // a1
                0x00, // a2
                0x00, // a3
                0x0, // a4 2b
                0x0, // a5 7f
                0x4f, // a6
                (byte) 0xff, // a7 cf ?
                0x00, // a8
                0x00, // a9
                0x00, // aa
                0x00, // ab
                0x00, // ac
                0x00, // ad
                0x00, // ae
                0x00, // af
                0x00, // b0
                (byte) 0xdb, // b1
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
                (byte) 0x83, // be
                0x00, // bf
                0x2f, // c0
                0x3f, // c1
                (byte) 0xff, // c2
                (byte) 0xff, // c3
                0x00, // c4
                0x00, // c5
                0x00, // c6
                0x00, // c7

                (byte) 0xd1, // c8?
                (byte) 0xd1, // c9
                (byte) 0xd1, // ca
                (byte) 0xd1, // cb
                (byte) 0xd1, // cc
                (byte) 0xd1, // cd
                (byte) 0xd1, // ce
                (byte) 0xd1, // cf
                (byte) 0xd1, // d0
                (byte) 0xd1, // d1
                (byte) 0xd1, // d2
                (byte) 0xd1, // d3
                (byte) 0xd1, // d4
                (byte) 0xd1, // d5
                (byte) 0xd1, // d6
                (byte) 0xd1, // d7
                (byte) 0xd1, // d8
                (byte) 0xd1, // d9
                (byte) 0xd1, // da
                (byte) 0xd1, // db
                (byte) 0xd1, // dc
                (byte) 0xd1, // dd
                (byte) 0xd1, // de
                (byte) 0xd1, // df
                (byte) 0xd1, // e0
                (byte) 0xd1, // e1
                (byte) 0xd1, // e2
                (byte) 0xd1, // e3
                (byte) 0xd1, // e4
                (byte) 0xd1, // e5
                (byte) 0xd1, // e6
                (byte) 0xd1, // e7
                (byte) 0xd1, // e8
                (byte) 0xd1, // e9
                (byte) 0xd1, // ea
                (byte) 0xd1, // eb
                (byte) 0xd1, // ec
                (byte) 0xd1, // ed
                (byte) 0xd1, // ee
                (byte) 0xd1, // ef
                (byte) 0xd1, // f0
                (byte) 0xd1, // f1
                (byte) 0xd1, // f2
                (byte) 0xd1, // f3
                (byte) 0xd1, // f4
                (byte) 0xd1, // f5
                (byte) 0xd1, // f6
                (byte) 0xd1, // f7
                (byte) 0xd1, // f8
                (byte) 0xd1, // f9
                (byte) 0xd1, // fa
                (byte) 0xd1, // fb
                (byte) 0xd1, // fc
                (byte) 0xd1, // fd
                (byte) 0xd1, // fe
                (byte) 0xd1 //ff
        };

        private enum IORam {
            SNDP(0x80),
            SNDV(0x88),
            SNDSWP(0x8C),
            SWPSTP(0x8D),
            NSCTL(0x8E),
            WAVDTP(0x8F),
            SNDMOD(0x90),
            SNDOUT(0x91),
            PCSRL(0x92),
            PCSRH(0x93),
            DMASL(0x40),
            DMASH(0x41),
            DMASB(0x42),
            DMADB(0x43),
            DMADL(0x44),
            DMADH(0x45),
            DMACL(0x46),
            DMACH(0x47),
            DMACTL(0x48),
            SDMASL(0x4A),
            SDMASH(0x4B),
            SDMASB(0x4C),
            SDMACL(0x4E),
            SDMACH(0x4F),
            SDMACTL(0x52);
            final int v;

            IORam(int v) {
                this.v = v;
            }

            static IORam valueOf(int v) {
                return Arrays.stream(values()).filter(e -> e.v == v).findFirst().get();
            }
        }

        // SoundDMA の転送間隔
        // 実際の数値が分からないので、予想です
        // サンプリング周期から考えてみて以下のようにした
        // 12KHz = 1.00HBlank = 256cycles間隔
        // 16KHz = 0.75HBlank = 192cycles間隔
        // 20KHz = 0.60HBlank = 154cycles間隔
        // 24KHz = 0.50HBlank = 128cycles間隔
        private static final int[] DMACycles = new int[] {256, 192, 154, 128};

        private static class WS_AUDIO {
            public int wave;
            public byte lvol;
            public byte rvol;
            public int offset;
            public int delta;
            public byte pos;
            public byte muted;
        }

        /** Ratio */
        private static class RATIO_CNTR {
            /** counter increment */
            public long inc;
            /** current value */
            public long val;

            private void setRatio(int mul, int div) {
                this.inc = (((long) mul << 20) + div / 2) / div;
            }

            private void step() {
                this.val += this.inc;
            }

            private void reset() {
                this.val = 0;
            }

            private void resetPreStep() {
                this.val = ((long) 1 << 20) - this.inc;
            }

            private int getVal() {
                return (int) (this.val >> 20);
            }

            private void mask() {
                this.val &= (((long) 1 << 20) - 1);
            }
        }

        public WS_AUDIO[] audios = new WS_AUDIO[] {
                new WS_AUDIO(), new WS_AUDIO(), new WS_AUDIO(), new WS_AUDIO()
        };
        public RATIO_CNTR hBlankTmr = new RATIO_CNTR();
        public short sweepTime;
        public byte sweepStep;
        public short sweepCount;
        public int sweepFreq;
        public byte noiseType;
        public int noiseRng;
        public int mainVolume;
        public byte pcmVolumeLeft;
        public byte pcmVolumeRight;

        public byte[] ioRam = new byte[0x100];
        public byte[] internalRam;

        public int clock = DEFAULT_CLOCK;
        public int smplrate = DEFAULT_CLOCK / 128;
        public float ratEmul;

        WSAState(int masterClock) {
            clock = masterClock;
            smplrate = clock / 128;
        }

        private static final int DEFAULT_CLOCK = 3072000;

        private void init(int sampleRate, int masterClock) {
            clock = masterClock;
            smplrate = clock / 128;

            // actual size is 64 KB, but the audio chip can only access 16 KB
            this.internalRam = new byte[0x4000];// ((int)8*)malloc(0x4000);

            this.ratEmul = (float) this.clock * 65536.0f / (float) this.smplrate;
            // one step every 256 cycles
            this.hBlankTmr.setRatio(this.clock, this.smplrate * 256);

            setMuteMask(0x00);
        }

        private void ws_audio_reset() {
            int muteMask = getMuteMask();
            this.audios = new WS_AUDIO[] {new WS_AUDIO(), new WS_AUDIO(), new WS_AUDIO(), new WS_AUDIO()};
            setMuteMask(muteMask);

            this.sweepTime = 0;
            this.sweepStep = 0;
            this.noiseType = 0;
            this.noiseRng = 1;
            this.mainVolume = 0x02; // 0x04
            this.pcmVolumeLeft = 0;
            this.pcmVolumeRight = 0;

            this.hBlankTmr.reset();

            for (int i = 0x80; i < 0xc9; i++)
                writeAudioPort((byte) i, initialIoValue[i]);
        }

        private void stop() {
        }

        /** OSWAN の擬似乱数の処理と同等のつもり */
        private static final int[] noiseMask = new int[] {
                0b11,
                0b110011,
                0b11011,
                0b1010011,
                0b101,
                0b1001,
                0b10001,
                0b11101
        };

        private static final int[] noiseBit = new int[] {
                0b1000_0000_0000_0000,
                0b0100_0000_0000_0000,
                0b0010_0000_0000_0000,
                0b0001_0000_0000_0000,
                0b0000_1000_0000_0000,
                0b0000_0100_0000_0000,
                0b0000_0010_0000_0000,
                0b0000_0001_0000_0000
        };

        private void update(int length, int[][] buffer) {
            int[] bufL = buffer[0];
            int[] bufR = buffer[1];
            for (int i = 0; i < length; i++) {
                int swpCount;

                this.hBlankTmr.step();
                for (swpCount = this.hBlankTmr.getVal(); swpCount > 0; swpCount--)
                    process();
                this.hBlankTmr.mask();

                int l = 0, r = 0;

                for (byte ch = 0; ch < 4; ch++) {
                    if (this.audios[ch].muted != 0)
                        continue;

                    if ((ch == 1) && ((this.ioRam[IORam.SNDMOD.v] & 0x20) != 0)) {
                        // Voice出力
                        short w = this.ioRam[0x89];
                        w -= 0x80;
                        l += this.pcmVolumeLeft * w;
                        r += this.pcmVolumeRight * w;
                    } else if ((this.ioRam[IORam.SNDMOD.v] & (1 << ch)) != 0) {
                        if ((ch == 3) && ((this.ioRam[IORam.SNDMOD.v] & 0x80) != 0)) {
                            //Noise

                            int Masked, XorReg;

                            this.audios[ch].offset += this.audios[ch].delta;
                            byte cnt = (byte) (this.audios[ch].offset >> 16);
                            this.audios[ch].offset &= 0xffff;
                            while (cnt > 0) {
                                cnt--;

                                this.noiseRng &= noiseBit[this.noiseType] - 1;
                                if (this.noiseRng == 0) this.noiseRng = noiseBit[this.noiseType] - 1;

                                Masked = this.noiseRng & noiseMask[this.noiseType];
                                XorReg = 0;
                                while (Masked != 0) {
                                    XorReg ^= Masked & 1;
                                    Masked >>= 1;
                                }
                                if (XorReg != 0)
                                    this.noiseRng |= noiseBit[this.noiseType];
                                this.noiseRng >>= 1;
                            }

                            this.ioRam[IORam.PCSRL.v] = (byte) (this.noiseRng & 0xff);
                            this.ioRam[IORam.PCSRH.v] = (byte) ((this.noiseRng >> 8) & 0x7f);

                            short w = (short) ((this.noiseRng & 1) != 0 ? 0x7f : -0x80);
                            l += this.audios[ch].lvol * w;
                            r += this.audios[ch].rvol * w;
                        } else {
                            this.audios[ch].offset += this.audios[ch].delta;
                            byte cnt = (byte) (this.audios[ch].offset >> 16);
                            this.audios[ch].offset &= 0xffff;
                            this.audios[ch].pos += cnt;
                            this.audios[ch].pos &= 0x1f;
                            short w = this.internalRam[(this.audios[ch].wave & 0xFFF0) + (this.audios[ch].pos >> 1)];
                            if ((this.audios[ch].pos & 1) == 0)
                                w = (short) ((w << 4) & 0xf0); // 下位ニブル
                            else
                                w = (short) (w & 0xf0); // 上位ニブル
                            w -= 0x80;
                            l += this.audios[ch].lvol * w;
                            r += this.audios[ch].rvol * w;
                        }
                    }
                }

                bufL[i] = l * this.mainVolume;
                bufR[i] = r * this.mainVolume;
            }
        }

        void writeAudioPort(byte port, byte value) {
            int i;
            float freq;

            this.ioRam[port] = value;

            switch (port & 0xff) {
            // 0x80-0x87の周波数レジスタについて
            // - ロックマン&フォルテの0x0fの曲では、周波数=0xFFFF の音が不要
            // - デジモンディープロジェクトの0x0dの曲のノイズは 周波数=0x07FF で音を出す
            // →つまり、0xFFFF の時だけ音を出さないってことだろうか。
            //   でも、0x07FF の時も音を出さないけど、ノイズだけ音を出すのかも。
            case 0x80:
            case 0x81:
                i = (((int) this.ioRam[0x81]) << 8) + ((int) this.ioRam[0x80]);
                if (i == 0xffff)
                    freq = 0;
                else
                    freq = 1.0f / (2048 - (i & 0x7ff));
                this.audios[0].delta = (int) (freq * this.ratEmul);
                break;
            case 0x82:
            case 0x83:
                i = (((int) this.ioRam[0x83]) << 8) + ((int) this.ioRam[0x82]);
                if (i == 0xffff)
                    freq = 0;
                else
                    freq = 1.0f / (2048 - (i & 0x7ff));
                this.audios[1].delta = (int) (freq * this.ratEmul);
                break;
            case 0x84:
            case 0x85:
                i = (((int) this.ioRam[0x85]) << 8) + ((int) this.ioRam[0x84]);
                this.sweepFreq = i;
                if (i == 0xffff)
                    freq = 0;
                else
                    freq = 1.0f / (2048 - (i & 0x7ff));
                this.audios[2].delta = (int) (freq * this.ratEmul);
                break;
            case 0x86:
            case 0x87:
                i = (((int) this.ioRam[0x87]) << 8) + ((int) this.ioRam[0x86]);
                if (i == 0xffff)
                    freq = 0;
                else
                    freq = 1.0f / (2048 - (i & 0x7ff));
                this.audios[3].delta = (int) (freq * this.ratEmul);
                break;
            case 0x88:
                this.audios[0].lvol = (byte) ((value >> 4) & 0xf);
                this.audios[0].rvol = (byte) (value & 0xf);
                break;
            case 0x89:
                this.audios[1].lvol = (byte) ((value >> 4) & 0xf);
                this.audios[1].rvol = (byte) (value & 0xf);
                break;
            case 0x8A:
                this.audios[2].lvol = (byte) ((value >> 4) & 0xf);
                this.audios[2].rvol = (byte) (value & 0xf);
                break;
            case 0x8B:
                this.audios[3].lvol = (byte) ((value >> 4) & 0xf);
                this.audios[3].rvol = (byte) (value & 0xf);
                break;
            case 0x8C:
                this.sweepStep = value;
                break;
            case 0x8D:
                // Sweepの間隔は 1/375[s] = 2.666..[ms]
                // CPU Clockで言うと 3072000/375 = 8192[cycles]
                // ここの設定値をnとすると、8192[cycles]*(n+1) 間隔でSweepすることになる
                //
                // これを HBlank (256cycles) の間隔で言うと、
                //　8192/256 = 32
                // なので、32[HBlank]*(n+1) 間隔となる
                this.sweepTime = (short) ((((short) value) + 1) << 5);
                this.sweepCount = this.sweepTime;
                break;
            case 0x8E:
                this.noiseType = (byte) (value & 7);
                if ((value & 8) != 0) this.noiseRng = 1; // ノイズカウンターリセット
                break;
            case 0x8F:
                this.audios[0].wave = value << 6;
                this.audios[1].wave = this.audios[0].wave + 0x10;
                this.audios[2].wave = this.audios[1].wave + 0x10;
                this.audios[3].wave = this.audios[2].wave + 0x10;
                break;
            case 0x90: // SNDMOD
                break;
            case 0x91:
                // ここでのボリューム調整は、内蔵 Speaker に対しての調整だけらしいので、
                // ヘッドフォン接続されていると認識させれば問題無いらしい。
                this.ioRam[port] |= 0x80;
                break;
            case 0x92:
            case 0x93:
                break;
            case 0x94:
                this.pcmVolumeLeft = (byte) ((value & 0xc) * 2);
                this.pcmVolumeRight = (byte) (((value << 2) & 0xc) * 2);
                break;
            case 0x52:
                //if (value&0x80)
                // ws_timer_set(2, DMACycles[value&3]);
                break;
            }
        }

        private byte readPort(byte port) {
            return (this.ioRam[port]);
        }

        // HBlank 間隔で呼ばれる
        // Note: Must be called every 256 cycles (3072000 Hz clock), i.e. at 12000 Hz
        private void process() {
            if (this.sweepStep != 0 && (this.ioRam[IORam.SNDMOD.v] & 0x40) != 0) {
                if (this.sweepCount < 0) {
                    this.sweepCount = this.sweepTime;
                    this.sweepFreq += this.sweepStep;
                    this.sweepFreq &= 0x7FF;

                    float freq = 1.0f / (2048 - this.sweepFreq);
                    this.audios[2].delta = (int) (freq * this.ratEmul);
                }
                this.sweepCount--;
            }
        }

        private void soundDma() {
            if ((this.ioRam[IORam.SDMACTL.v] & 0x88) == 0x80) {
                int i = (this.ioRam[IORam.SDMACH.v] << 8) | this.ioRam[IORam.SDMACL.v];
                int j = (this.ioRam[IORam.SDMASB.v] << 16) | (this.ioRam[IORam.SDMASH.v] << 8) | this.ioRam[IORam.SDMASL.v];
                byte b = this.internalRam[j & 0x3FFF];

                this.ioRam[0x89] = b;
                i--;
                j++;
                if (i < 32) {
                    i = 0;
                    this.ioRam[IORam.SDMACTL.v] &= 0x7F;
                } else {
                    // set DMA timer
                    //ws_timer_set(2, DMACycles[SDMACTL&3]);
                }
                this.ioRam[IORam.SDMASB.v] = (byte) ((j >> 16) & 0xFF);
                this.ioRam[IORam.SDMASH.v] = (byte) ((j >> 8) & 0xFF);
                this.ioRam[IORam.SDMASL.v] = (byte) (j & 0xFF);
                this.ioRam[IORam.SDMACH.v] = (byte) ((i >> 8) & 0xFF);
                this.ioRam[IORam.SDMACL.v] = (byte) (i & 0xFF);
            }
        }

        private void writeRamByte(int offset, byte value) {
            // RAM - 16 KB (WS) / 64 KB (WSC) internal RAM
            this.internalRam[offset & 0x3FFF] = value;
        }

        private byte readRamByte(int offset) {
            return this.internalRam[offset & 0x3FFF];
        }

        private void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < 4; curChn++)
                this.audios[curChn].muted = (byte) ((muteMask >> curChn) & 0x01);
        }

        private int getMuteMask() {
            int muteMask = 0x00;
            for (byte curChn = 0; curChn < 4; curChn++)
                muteMask |= this.audios[curChn].muted << curChn;

            return muteMask;
        }
    }
}
