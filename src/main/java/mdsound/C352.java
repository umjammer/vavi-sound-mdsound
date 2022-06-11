// license:BSD-3-Clause
// copyright-holders:R. Belmont, superctr

package mdsound;

/*
    C352.c - Namco C352 custom PCM chip emulation
    v2.0
    By R. Belmont
    Rewritten and improved by superctr
    Additional code by cync and the hoot development team

    Thanks to Cap of VivaNonno for info and The_Author for preliminary reverse-engineering

    Chip specs:
    32 voices
    Supports 8-bit linear and 8-bit muLaw samples
    Output: digital, 16 bit, 4 channels
    Output sample rate is the input clock / (288 * 2).
 */
public class C352 extends Instrument.BaseInstrument {

    public C352() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
        //0..Main
    }

    @Override
    public void reset(byte chipID) {
        device_reset_c352(chipID);
    }

    @Override
    public int start(byte chipID, int clock) {
        return start(chipID, 44100, clock);
    }

    @Override
    public void stop(byte chipID) {
        device_stop_c352(chipID);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        c352_update(chipID, outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    @Override
    public int start(byte chipID, int SamplingRate, int clockValue, Object... Option) {
        byte bytC352ClkDiv = 0;
        if (Option == null || Option.length < 1) bytC352ClkDiv = 0;
        else bytC352ClkDiv = (byte) Option[0];

        return device_start_c352(chipID, clockValue, bytC352ClkDiv * 4);
    }

    public static class C352State {

        private static short[] mulawTable = new short[256];
        private int[] _out = new int[4];

        static {
            int j = 0;
            for (int i = 0; i < 128; i++) {
                mulawTable[i] = (short) (j << 5);
                if (i < 16)
                    j += 1;
                else if (i < 24)
                    j += 2;
                else if (i < 48)
                    j += 4;
                else if (i < 100)
                    j += 8;
                else
                    j += 16;
            }
            for (int i = 128; i < 256; i++)
                mulawTable[i] = (short) ((~mulawTable[i - 128]) & 0xffe0);
        }

        public int[] flags = new int[32];

        private static final int VOICES = 32;

        private enum Flag {
            // channel is busy
            BUSY(0x8000),
            // Keyon
            KEYON(0x4000),
            // Keyoff
            KEYOFF(0x2000),
            // Loop Trigger
            LOOPTRG(0x1000),
            // Loop History
            LOOPHIST(0x0800),
            // Frequency Modulation
            FM(0x0400),
            // Rear Left invert phase 180 degrees
            PHASERL(0x0200),
            // Front Left invert phase 180 degrees
            PHASEFL(0x0100),
            // invert phase 180 degrees (e.g. flip sign of sample)
            PHASEFR(0x0080),
            // loop direction
            LDIR(0x0040),
            // "long-format" sample (can't loop, not sure what else it means)
            LINK(0x0020),
            // play noise instead of sample
            NOISE(0x0010),
            // sample is mulaw instead of linear 8-bit PCM
            MULAW(0x0008),
            // don't apply filter
            FILTER(0x0004),
            // loop backwards
            REVLOOP(0x0003),
            // loop forward
            LOOP(0x0002),
            // play sample backwards
            REVERSE(0x0001);
            int v;

            Flag(int v) {
                this.v = v;
            }
        }

        public static class Voice {

            public int pos = 0;
            public int counter = 0;

            public short sample = 0;
            public short lastSample = 0;

            public int volF = 0;
            public int volR = 0;
            public byte[] currVol = new byte[] {0, 0, 0, 0};
            public int freq = 0;
            public int flags = 0;

            public int waveBank = 0;
            public int waveStart = 0;
            public int waveEnd = 0;
            public int waveLoop = 0;

            public byte mute = 0;

            private void ramp(int ch, byte val) {
                if ((this.flags & Flag.FILTER.v) != 0) {
                    this.currVol[ch] = val;
                    return;
                }

                short volDelta = (short) (this.currVol[ch] - val);
                if (volDelta != 0)
                    this.currVol[ch] = (byte) (this.currVol[ch] + ((volDelta > 0) ? -1 : 1));
                //System.err.printf("this.curr_vol[ch%d] = %d val=%d", ch, this.curr_vol[ch], val);
            }
        }

        public int sampleRateBase;
        public int divider;

        public Voice[] voices = new Voice[VOICES];

        public int random;
        public int control; // control flags, purpose unknown.

        public byte[] wave;
        public int wavesize;
        public int waveMask;

        public byte muteRear; // flag from VGM header

        private static byte muteAllRear = 0x00;

        private void fetchSample(Voice v) {
            //System.err.printf("v.sample = %d  v.pos = %d  this.wave_mask = %d  v.flags =%d ", v.sample, v.pos, this.wave_mask, v.flags);

            v.lastSample = v.sample;

            if ((v.flags & 0x0010) != 0) {
                this.random = (this.random >> 1) ^ ((-(this.random & 1)) & 0xfff6);
                v.sample = (short) this.random;
            } else {
                byte s;
                int pos;

                s = (byte) (v.pos < this.wave.length ? this.wave[v.pos & this.waveMask] : 0);

                v.sample = (short) (s << 8);
                if ((v.flags & 0x0008) != 0) {

                    v.sample = mulawTable[(byte) s];
                }

                pos = v.pos & 0xffff;

                if ((v.flags & 0x0002) != 0 && (v.flags & 0x0001) != 0) {
                    // backwards>forwards
                    if ((v.flags & 0x0040) != 0 && pos == v.waveLoop)
                        v.flags &= 0xffbf;
                    // forwards>backwards
                    else if ((v.flags & 0x0040) == 0 && pos == v.waveEnd)
                        v.flags |= 0x0040;

                    v.pos = v.pos + ((v.flags & 0x0040) != 0 ? -1 : 1);
                } else if (pos == v.waveEnd) {
                    if ((v.flags & 0x0020) != 0 && (v.flags & 0x0002) != 0) {
                        v.pos = (v.waveStart << 16) | v.waveLoop;
                        v.flags |= 0x0800;
                    }
                    else if ((v.flags & 0x0002) != 0) {
                        v.pos = (v.pos & 0xff0000) | v.waveLoop;
                        v.flags |= 0x0800;
                    } else {
                        v.flags |= 0x2000;
                        v.flags &= 0x7fff;
                        v.sample = 0;
                    }
                } else {
                    v.pos = v.pos + ((v.flags & 0x0001) != 0 ? -1 : 1);
                }
            }
        }

        private void update(int[][] outputs, int samples) {
            int i, j;
            short s;
            int next_counter;
            Voice v;

            //short[] _out = new short[4];

            for (i = 0; i < samples; i++) {
                outputs[0][i] = 0;
                outputs[1][i] = 0;
            }

            for (i = 0; i < samples; i++) {
                _out[0] = _out[1] = _out[2] = _out[3] = 0;

                for (j = 0; j < VOICES; j++) {

                    v = this.voices[j];
                    s = 0;
                    flags[j] = v.flags;

                    //System.err.printf(" v.flags=%d", v.flags);
                    if ((v.flags & 0x8000) != 0) {
                        next_counter = v.counter + v.freq;

                        if ((next_counter & 0x10000) != 0) {
                            fetchSample(v);
                            //System.err.printf("fetch");
                            //System.err.printf(" ch=%d 0=%d  1=%d  2=%d  3=%d",j, _out[0], _out[1], _out[2], _out[3]);
                        }

                        if (((next_counter ^ v.counter) & 0x18000) != 0) {
                            v.ramp(0, (byte) (v.volF >> 8));
                            v.ramp(1, (byte) (v.volF & 0xff));
                            v.ramp(2, (byte) (v.volR >> 8));
                            v.ramp(3, (byte) (v.volR & 0xff));
                        }

                        v.counter = next_counter & 0xffff;
                        //System.err.printf(" v.freq=%d", v.freq);
                        //System.err.printf(" v.counter=%d", v.counter);

                        s = v.sample;

                        // Interpolate samples
                        if ((v.flags & 0x0004) == 0)
                            s = (short) (v.lastSample + (v.counter * (v.sample - v.lastSample) >> 16));
                    }

                    if (this.voices[j].mute == 0) {
                        // Left
                        _out[0] += ((((v.flags & 0x0100) != 0 ? -s : s) * v.currVol[0]) >> 9);
                        _out[2] += ((((v.flags & 0x0080) != 0 ? -s : s) * v.currVol[2]) >> 9);
                        // Right
                        _out[1] += ((((v.flags & 0x0200) != 0 ? -s : s) * v.currVol[1]) >> 9);
                        _out[3] += ((((v.flags & 0x0200) != 0 ? -s : s) * v.currVol[3]) >> 9);
                    }

                    //System.err.printf("out [0]=%d  [1]=%d  [2]=%d  [3]=%d", _out[0] , _out[1] , _out[2] , _out[3]);
                }

                outputs[0][i] += _out[0];
                outputs[1][i] += _out[1];
                if (this.muteRear == 0 && muteAllRear == 0) {
                    outputs[0][i] += _out[2];
                    outputs[1][i] += _out[3];
                }
                //outputs[0][i] = Math.max(Math.min(outputs[0][i], Short.MAX_VALUE), Short.MIN_VALUE);
                //outputs[1][i] = Math.max(Math.min(outputs[1][i], Short.MAX_VALUE), Short.MIN_VALUE);
            }
        }

        private int start(int clock, int clkdiv) {
            this.wave = null;
            this.wavesize = 0x00;

            this.divider = clkdiv != 0 ? clkdiv : 288;
            this.sampleRateBase = (clock & 0x7FFFFFFF) / this.divider;
            this.muteRear = (byte) ((clock & 0x80000000) >> 31);

            this.voices = new Voice[VOICES];
            for (int i = 0; i < VOICES; i++) {
                this.voices[i] = new Voice();
            }

            setMuteMask(0x00000000);

            return this.sampleRateBase;
        }

        private void stop() {
            this.wave = null;
        }

        private void reset() {
            int muteMask = getMuteMask();

            // clear all channels states
            this.voices = new Voice[VOICES];
            for (int i = 0; i < VOICES; i++) {
                this.voices[i] = new Voice();
            }

            // init noise generator
            this.random = 0x1234;
            this.control = 0;

            setMuteMask(muteMask);
        }

        private int read(int address) {
            if (address < 0x100) {
                int ch = address / 8;
                switch (address % 8) {
                case 0:
                    return this.voices[ch].volF;
                case 1:
                    return this.voices[ch].volR;
                case 2:
                    return this.voices[ch].freq;
                case 3:
                    return this.voices[ch].flags;
                case 4:
                    return this.voices[ch].waveBank;
                case 5:
                    return this.voices[ch].waveStart;
                case 6:
                    return this.voices[ch].waveEnd;
                case 7:
                    return this.voices[ch].waveLoop;
                }
            } else if (address == 0x200)
                return this.control;
            else
                return 0;

            return 0;
        }

        private void write(int address, int val) {
            //System.err.printf("address = %d  val = %d", address, val);

            if (address < 0x100) { // Channel registers, see map above.
                int ch = address / 8;
                switch (address % 8) {
                case 0:
                    this.voices[ch].volF = val;
                    break;
                case 1:
                    this.voices[ch].volR = val;
                    break;
                case 2:
                    this.voices[ch].freq = val;
                    //System.err.printf("this.v[ch%d].freq = %d", ch, val);
                    break;
                case 3:
                    this.voices[ch].flags = val;
                    break;
                case 4:
                    this.voices[ch].waveBank = val;
                    break;
                case 5:
                    this.voices[ch].waveStart = val;
                    break;
                case 6:
                    this.voices[ch].waveEnd = val;
                    break;
                case 7:
                    this.voices[ch].waveLoop = val;
                    break;
                }
            } else if (address == 0x200) {
                this.control = val;
                //System.err.printf("C352 control register write: %04x\n",val);
            } else if (address == 0x202) { // execute keyons/keyoffs
                for (int i = 0; i < VOICES; i++) {
                    if ((this.voices[i].flags & 0x4000) != 0) {
                        this.voices[i].pos = (int) ((this.voices[i].waveBank << 16) | this.voices[i].waveStart);

                        this.voices[i].sample = 0;
                        this.voices[i].lastSample = 0;
                        this.voices[i].counter = 0xffff;

                        this.voices[i].flags |= 0x8000;
                        this.voices[i].flags &= 0xb7ff;

                        this.voices[i].currVol[0] = this.voices[i].currVol[1] = 0;
                        this.voices[i].currVol[2] = this.voices[i].currVol[3] = 0;
                    }
                    else if ((this.voices[i].flags & 0x2000) != 0) {
                        this.voices[i].flags &= 0x5fff;
                        this.voices[i].counter = 0xffff;
                    }
                }
            }
        }

        public void writeRom(int romSize, int dataStart, int dataLength, byte[] romData) {
            if (this.wavesize != romSize) {
                this.wave = new byte[romSize];
                this.wavesize = romSize;
                for (this.waveMask = 1; this.waveMask < this.wavesize; this.waveMask <<= 1)
                    ;
                this.waveMask--;
                for (int i = 0; i < romSize; i++) {
                    this.wave[i] = (byte) 0xff;
                }
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = (int) (romSize - dataStart);

            if (dataLength >= 0) System.arraycopy(romData, 0, this.wave, dataStart, dataLength);
        }

        public void writeRom2(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
            //System.err.printf("romSize=%x , dataStart=%x , dataLength=%x", romSize, dataStart, dataLength);
            if (this.wavesize != romSize) {
                this.wave = new byte[romSize];
                this.wavesize = romSize;
                for (this.waveMask = 1; this.waveMask < this.wavesize; this.waveMask <<= 1)
                    ;
                this.waveMask--;
                for (int i = 0; i < romSize; i++) {
                    this.wave[i] = (byte) 0xff;
                }
            }
            if (dataStart > romSize)
                return;
            if (dataStart + dataLength > romSize)
                dataLength = romSize - dataStart;

            if (dataLength >= 0) System.arraycopy(romData, srcStartAdr, this.wave, dataStart, dataLength);
        }

        private void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < VOICES; curChn++)
                this.voices[curChn].mute = (byte) ((muteMask >> curChn) & 0x01);
        }

        private int getMuteMask() {
            int muteMask = 0x00000000;
            for (byte curChn = 0; curChn < VOICES; curChn++)
                muteMask |= (this.voices[curChn].mute << curChn);

            return muteMask;
        }

        public static void setOptions(byte flags) {
            muteAllRear = (byte) ((flags & 0x01) >> 0);
        }
    }

    private static final int MAX_CHIPS = 0x02;
    private static C352State[] c352Data = new C352State[] {new C352State(), new C352State()};

    @Override
    public String getName() {
        return "C352";
    }

    @Override
    public String getShortName() {
        return "C352";
    }

    private void c352_update(byte chipID, int[][] outputs, int samples) {
        C352State c = c352Data[chipID];
        c.update(outputs, samples);
    }

    private int device_start_c352(byte chipID, int clock, int clkdiv) {
        if (chipID >= MAX_CHIPS)
            return 0;

        C352State c = c352Data[chipID];
        return c.start(clock, clkdiv);
    }

    private void device_stop_c352(byte chipID) {
        C352State c = c352Data[chipID];
        c.stop();
    }

    private void device_reset_c352(byte chipID) {
        C352State c = c352Data[chipID];
        c.reset();
    }

    private int c352_r(byte chipID, int address) {
        C352State c = c352Data[chipID];
        return c.read(address);
    }

    private void c352_w(byte chipID, int address, int val) {
        C352State c = c352Data[chipID];
        c.write(address, val);
    }

    public void c352_write_rom(byte chipID, int romSize, int dataStart, int dataLength, byte[] romData) {
        C352State c = c352Data[chipID];
        c.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void c352_write_rom2(byte chipID, int romSize, int dataStart, int dataLength,
                                byte[] romData, int srcStartAdr) {
        C352State c = c352Data[chipID];
        c.writeRom2(romSize, dataStart, dataLength, romData, srcStartAdr);
    }

    private void c352_set_mute_mask(byte chipID, int muteMask) {
        C352State c = c352Data[chipID];
        c.setMuteMask(muteMask);
    }

    private int c352_get_mute_mask(byte chipID) {
        C352State c = c352Data[chipID];
        return c.getMuteMask();
    }

    public void c352_set_options(byte flags) {
        C352State.setOptions(flags);
    }

    private int get_mute_mask(byte chipID) {
        C352State c = c352Data[chipID];
        return c.getMuteMask();
    }

    public int[] getFlags(byte chipID) {
        C352State c = c352Data[chipID];
        return c.flags;
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        c352_w(chipID, adr, data);
        return 0;
    }
}
