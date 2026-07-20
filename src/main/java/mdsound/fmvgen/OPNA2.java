/*
 * https://github.com/kuma4649/MDSound
 */

package mdsound.fmvgen;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import mdsound.fmgen.Opna;
import mdsound.fmvgen.Fmvgen.Effects;
import mdsound.fmvgen.effect.ReversePhase;


/** YM2609(OPNA2) */
public class OPNA2 extends Opna.OPNABase {

    public static final float[] panTable = new float[] {1.0f, 0.7512f, 0.4512f, 0.0500f};

    /** Rhythm Sound Source */
    private Rhythm[] rhythm;

    /** Overall rhythm volume */
    private byte rhythmTl;
    private int rhythmTVol;
    /** Rhythm Key */
    private byte rhythmKey;

    protected FM6[] fm6;
    protected PSG2[] psg2;
    protected AdpcmB[] adpcmB;
    protected AdpcmA adpcmA;

    protected byte prescale;

    public Effects effects;

    private final ReversePhase reversePhase = ReversePhase.getInstance();

    private static class Rhythm {
        /** pan */
        private byte pan;
        /** level */
        private byte level;
        /** volume */
        private int volume;
        /** sample */
        private int[] sample;
        /** size */
        private int size;
        /** position */
        private int pos;
        /** step */
        private int step;
        /** sampling rate */
        private int rate;
        public final int efcCh;
        public final int num;
        public final Effects effects;

        public Rhythm(int num, Effects effects, int efcCh) {
            this.effects = effects;
            this.efcCh = efcCh;
            this.num = num;
        }
    }

    public static class Whdr {
        public int chunkSize;
        public int tag;
        public int nch;
        public int rate;
        public int avgbytes;
        public int align;
        public int bps;
        public int size;
    }

    /**
     * construction
     */
    public void init(int clock) {
        this.effects = new Effects(clock);

        fm6 = new FM6[] {
                new FM6(0, effects, 0),
                new FM6(1, effects, 6)
        };
        psg2 = new PSG2[] {
                new PSG2(0, effects, 12),
                new PSG2(1, effects, 15),
                new PSG2(2, effects, 18),
                new PSG2(3, effects, 21)
        };
        adpcmB = new AdpcmB[] {
                new AdpcmB(0, effects, 24),
                new AdpcmB(1, effects, 25),
                new AdpcmB(2, effects, 26)
        };
        rhythm = new Rhythm[] {
                new Rhythm(0, effects, 27),
                new Rhythm(1, effects, 28),
                new Rhythm(2, effects, 29),
                new Rhythm(3, effects, 30),
                new Rhythm(4, effects, 31),
                new Rhythm(5, effects, 32)
        };
        adpcmA = new AdpcmA(0, effects, 33);

        for (int i = 0; i < 6; i++) {
            rhythm[i].sample = null;
            rhythm[i].pos = 0;
            rhythm[i].size = 0;
            rhythm[i].volume = 0;
        }
        rhythmTVol = 0;

        for (int i = 0; i < 2; i++) {
            fm6[i].parent = this;
        }

        for (int i = 0; i < 3; i++) {
            adpcmB[i].adpcmMask = (i == 0) ? 0x3ffff : 0xffffff;
            adpcmB[i].adpcmNotice = 4;
            adpcmB[i].deltaN = 256;
            adpcmB[i].adpcmVol = 0;
            adpcmB[i].control2 = 0;
            adpcmB[i].shiftBit = (i == 0) ? 6 : 9;
            adpcmB[i].parent = this;
        }

        csmCh = ch[2];
    }

    protected void finalinze() {
        adpcmBuf = null;
        for (int i = 0; i < 6; i++) {
            rhythm[i].sample = null;
        }
    }

    @Override
    public boolean init(int c, int r) {
        return init(c, r, false, "");
    }

    public boolean init(int c, int r, boolean ipFlag, String path) {
        return init(c, r, ipFlag, fname -> createRhythmFileStream(path, fname), null, 0);
    }

    public boolean init(int c, int r, boolean ipFlag,
                        Function<String, InputStream> appendFileReaderCallback /* = null */,
                        byte[] adpcmA /* = null */, int adpcmaSize /* = 0 */) {
        rate = 8000;
        try {
            loadRhythmSample(appendFileReaderCallback);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (adpcmB[0].adpcmBuf == null)
            adpcmB[0].adpcmBuf = new byte[0x4_0000];
        if (adpcmB[0].adpcmBuf == null)
            return false;
        if (adpcmB[1].adpcmBuf == null)
            adpcmB[1].adpcmBuf = new byte[0x100_0000];
        if (adpcmB[1].adpcmBuf == null)
            return false;
        if (adpcmB[2].adpcmBuf == null)
            adpcmB[2].adpcmBuf = new byte[0x100_0000];
        if (adpcmB[2].adpcmBuf == null)
            return false;

        setAdpcmA(adpcmA, adpcmaSize);

        if (!setRate(c, r, ipFlag))
            return false;
        if (!super.init(c, r, ipFlag))
            return false;

        reset();

        setVolumeFM(0);
        setVolumePSG(0);
        setVolumeADPCM(0);
        setVolumeRhythmTotal(0);
        for (int i = 0; i < 6; i++)
            setVolumeRhythm(0, 0);
        setChannelMask(0);

        return true;
    }

    public void setAdpcmA(byte[] _adpcma, int _adpcma_size) {
        adpcmA.buf = _adpcma;
        adpcmA.size = _adpcma_size;
    }

    /**
     * Sampling rate change
     */
    @Override
    public boolean setRate(int c, int r, boolean ipflag /* = false */) {
        if (!super.setRate(c, r, ipflag))
            return false;

        rebuildTimeTable();
        for (int i = 0; i < 6; i++) {
            rhythm[i].step = rhythm[i].rate * 1024 / r;
        }

        for (int i = 0; i < 3; i++) {
            adpcmB[i].adplBase = (int) ((int) (8192.0 * (clock / 72.0) / r));
            adpcmB[i].adplD = adpcmB[i].deltaN * adpcmB[i].adplBase >> 16;
        }

        adpcmA.step = (int) ((double) (c) / 54 * 8192 / r);
        return true;
    }

    /**
     * Synthesis
     * @param buffer Destination
     * @param samples Number of composite samples
     */
    public void mix(int[] buffer, int samples) {
        fm6[0].mix(buffer, samples, regTc);
        fm6[1].mix(buffer, samples, regTc);
        psg2[0].mix(buffer, samples);
        psg2[1].mix(buffer, samples);
        psg2[2].mix(buffer, samples);
        psg2[3].mix(buffer, samples);
        adpcmB[0].mix(buffer, samples);
        adpcmB[1].mix(buffer, samples);
        adpcmB[2].mix(buffer, samples);
        rhythmMix(buffer, samples);
        adpcmA.mix(buffer, samples);
        effects.ep3band.mix(buffer, samples);
    }

    /**
     * Reset
     */
    @Override
    public void reset() {
        reg29 = 0x1f;
        rhythmKey = 0;
        limitAddr = 0x3ffff;
        super.reset();

        setPreScaler(0);

        fm6[0].reset();
        fm6[1].reset();

        psg2[0].reset();
        psg2[1].reset();
        psg2[2].reset();
        psg2[3].reset();

        for (int i = 0; i < 3; i++) {
            adpcmB[i].reset();
        }
    }

    @Override
    protected void rebuildTimeTable() {
        super.rebuildTimeTable();

        int p = prescale;
        prescale = (byte) 0xff; // -1;
        setPreScaler(p);
    }

    @Override
    public void setPreScaler(int p) {
        super.setPreScaler(p);

        byte[][] table = new byte[][] {new byte[] {6, 4}, new byte[] {3, 2}, new byte[] {2, 1}};
        byte[] table2 = new byte[] {108, 77, 71, 67, 62, 44, 8, 5};
        // 512
        if (prescale != p) {
            prescale = (byte) p;
            //assert(0 <= prescale && prescale< 3);

            int fmclock = clock / table[p][0] / 12;

            rate = psgRate;

            // Ratio of synthesis frequency to output frequency
            //assert(fmclock< (0x80000000 >> FM_RATIOBITS));
            int ratio = ((fmclock << Fmvgen.FM_RATIOBITS) + rate / 2) / rate;

            setTimerBase(fmclock);
            //makeTimeTable(ratio);
            fm6[0].chip.setRatio(ratio);
            fm6[1].chip.setRatio(ratio);

            psg2[0].setClock(clock / table[p][1], psgRate);
            psg2[1].setClock(clock / table[p][1], psgRate);
            psg2[2].setClock(clock / table[p][1], psgRate);
            psg2[3].setClock(clock / table[p][1], psgRate);

            for (int i = 0; i < 8; i++) {
                lfoTable[i] = (ratio << (2 + Fmvgen.FM_LFOCBITS - Fmvgen.FM_RATIOBITS)) / table2[i];
            }
        }
    }

    /**
     * Set data in the register array
     */
    @Override
    public void setReg(int addr, int data) {
        addr &= 0x3ff;

        if (addr < 0x10) {
            psg2[0].setReg(addr, (byte) data);
            return;
        } else if (addr >= 0x10 && addr < 0x20) {
            rhythmSetReg(addr, (byte) data);
            return;
        } else if (addr >= 0xc0 && addr < 0xcc) {
            effects.ep3band.setReg(addr & 0xf, (byte) data);
            return;
        } else if (addr >= 0xcc && addr < 0xd9) {
            effects.reversePhase.setReg(addr - 0xcc, (byte) data);
            return;
        } else if (addr >= 0x100 && addr < 0x111) {
            adpcmbSetReg(0, addr - 0x100, (byte) data);
            return;
        } else if (addr >= 0x111 && addr < 0x118) {
            adpcmA.setReg(addr - 0x111, (byte) data);
            return;
        } else if (addr >= 0x118 && addr < 0x120) {
            return;
        } else if (addr >= 0x120 && addr < 0x130) {
            psg2[1].setReg(addr - 0x120, (byte) data);
            return;
        } else if (addr >= 0x200 && addr < 0x210) {
            psg2[2].setReg(addr - 0x200, (byte) data);
            return;
        } else if (addr >= 0x210 && addr < 0x220) {
            psg2[3].setReg(addr - 0x210, (byte) data);
            return;
        } else if (addr >= 0x300 && addr < 0x311) {
            adpcmbSetReg(1, addr - 0x300, (byte) data);
            return;
        } else if (addr >= 0x311 && addr < 0x322) {
            adpcmbSetReg(2, addr - 0x311, (byte) data);
            return;
        } else if (addr >= 0x322 && addr < 0x325) {
            effects.reverb.setReg(addr - 0x322, (byte) data);
            if (addr == 0x323) {
                effects.distortion.setReg(0, (byte) data); // Channel change shares address
                effects.chorus.setReg(0, (byte) data);
                effects.hpflpf.setReg(0, (byte) data);
                effects.compressor.setReg(0, (byte) data);
            }
            return;
        } else if (addr >= 0x325 && addr < 0x328) {
            effects.distortion.setReg(addr - 0x324, (byte) data); // Distortion address 0 is shared with reverb
            return;
        } else if (addr >= 0x328 && addr < 0x32C) {
            effects.chorus.setReg(addr - 0x327, (byte) data); // Address 0 of chorus is shared with reverb
            return;
        } else if (addr >= 0x32C && addr < 0x330) {
            return;
        } else if (addr >= 0x3c0 && addr < 0x3c6) {
            effects.hpflpf.setReg(addr - 0x3bf, (byte) data);
            return;
        } else if (addr >= 0x3c6 && addr < 0x3cd) {
            effects.compressor.setReg(addr - 0x3c5, (byte) data);
            return;
        }

        if (addr < 0x200) {
            fmSetReg(0, addr, (byte) data);
        } else {
            fmSetReg(1, addr - 0x200, (byte) data);
        }
    }

    public void fmSetReg(int ch, int addr, byte data) {
        fm6[ch].setReg(addr, data);
    }

    public void adpcmbSetReg(int ch, int addr, byte data) {
        adpcmB[ch].setReg(addr, data);
    }

    public void rhythmSetReg(int addr, byte data) {
        switch (addr) {
        // Rhythm
        case 0x10: // DM/KEYON
            if ((data & 0x80) == 0) { // KEY ON
                rhythmKey |= (byte) (data & 0x3f);
                if ((data & 0x01) != 0) rhythm[0].pos = 0;
                if ((data & 0x02) != 0) rhythm[1].pos = 0;
                if ((data & 0x04) != 0) rhythm[2].pos = 0;
                if ((data & 0x08) != 0) rhythm[3].pos = 0;
                if ((data & 0x10) != 0) rhythm[4].pos = 0;
                if ((data & 0x20) != 0) rhythm[5].pos = 0;
            } else { // DUMP
                rhythmKey &= (byte) (~(byte) data);
            }
            break;

        case 0x11:
            rhythmTl = (byte) (~data & 63);
            break;

        case 0x18: // Bass Drum
        case 0x19: // Snare Drum
        case 0x1a: // Top Cymbal
        case 0x1b: // Hihat
        case 0x1c: // Tom-tom
        case 0x1d: // Rim shot
            rhythm[addr & 7].pan = (byte) ((data >> 6) & 3);
            rhythm[addr & 7].level = (byte) (~data & 31);
            break;
        }
    }

    @Override
    public int getReg(int addr) {
        return 0;
    }

    // Volume Settings

    @Override
    public void setVolumeFM(int db) {
        db = Math.min(db, 20);
        if (db > -192) {
            fm6[0].fmVolume = (int) (16384.0 * Math.pow(10.0, db / 40.0));
            fm6[1].fmVolume = (int) (16384.0 * Math.pow(10.0, db / 40.0));
        } else {
            fm6[0].fmVolume = 0;
            fm6[1].fmVolume = 0;
        }
    }

    @Override
    public void setVolumePSG(int db) {
        psg2[0].setVolume(db);
        psg2[1].setVolume(db);
        psg2[2].setVolume(db);
        psg2[3].setVolume(db);
    }

    public void setVolumeADPCM(int db) {
        db = Math.min(db, 20);
        if (db > -192) {
            adpcmB[0].adpcmVol = (int) (65536.0 * Math.pow(10.0, db / 40.0));
            adpcmB[1].adpcmVol = (int) (65536.0 * Math.pow(10.0, db / 40.0));
            adpcmB[2].adpcmVol = (int) (65536.0 * Math.pow(10.0, db / 40.0));
        } else {
            adpcmB[0].adpcmVol = 0;
            adpcmB[1].adpcmVol = 0;
            adpcmB[2].adpcmVol = 0;
        }

        adpcmB[0].adpcmVolume = (adpcmB[0].adpcmVol * adpcmB[0].adpcmLevel) >> 12;
        adpcmB[1].adpcmVolume = (adpcmB[1].adpcmVol * adpcmB[1].adpcmLevel) >> 12;
        adpcmB[2].adpcmVolume = (adpcmB[2].adpcmVol * adpcmB[2].adpcmLevel) >> 12;
    }

    /**
     * Channel Mask Settings
     */
    @Override
    public void setChannelMask(int mask) {
        for (int i = 0; i < 6; i++) {
            fm6[0].ch[i].mute(!((mask & (1 << i)) == 0));
            fm6[1].ch[i].mute(!((mask & (1 << i)) == 0));
        }

        psg2[0].setChannelMask(mask >> 6);
        psg2[1].setChannelMask(mask >> 6);
        psg2[2].setChannelMask(mask >> 6);
        psg2[3].setChannelMask(mask >> 6);

        adpcmB[0].adpcmMask_ = (mask & (1 << 9)) != 0;
        adpcmB[1].adpcmMask_ = (mask & (1 << 9)) != 0;
        adpcmB[2].adpcmMask_ = (mask & (1 << 9)) != 0;

        rhythmMask_ = (mask >> 10) & ((1 << 6) - 1);
    }

    public byte[] getADPCMBuffer() {
        return adpcmBuf;
    }

    /**
     * Rhythm Synthesis
     */
    private void rhythmMix(int[] buffer, int count) {
        if (rhythmTVol < 128 && rhythm[0].sample != null && ((rhythmKey & 0x3f) != 0)) {
            int limit = count * 2;
            visRtmVolume[0] = 0;
            visRtmVolume[1] = 0;
            for (int i = 0; i < 6; i++) {
                Rhythm r = rhythm[i];
                if ((rhythmKey & (1 << i)) != 0 && r.level < 128) {
                    int db = Math.clamp(rhythmTl + rhythmTVol + r.level + r.volume, -31, 127);
                    int vol = tlTable[Fmvgen.FM_TLPOS + (db << (Fmvgen.FM_TLBITS - 7))] >> 4;
                    int maskL = -((r.pan >> 1) & 1);
                    int maskR = -(r.pan & 1);

                    if ((rhythmMask_ & (1 << i)) != 0) {
                        maskL = maskR = 0;
                    }

                    for (int dest = 0; dest < limit && r.pos < r.size; dest += 2) {
                        int sample = (r.sample[r.pos / 1024] * vol) >> 12;
                        r.pos += r.step;

                        int[] sL = new int[] {sample};
                        int[] sR = new int[] {sample};
                        effects.distortion.mix(r.efcCh, sL, sR);
                        effects.chorus.mix(r.efcCh, sL, sR);
                        effects.hpflpf.mix(r.efcCh, sL, sR);
                        effects.compressor.mix(r.efcCh, sL, sR);

                        sL[0] = sL[0] & maskL;
                        sR[0] = sR[0] & maskR;
                        sL[0] *= reversePhase.rhythm[i][0];
                        sR[0] *= reversePhase.rhythm[i][1];
                        int revSampleL = (int) (sL[0] * effects.reverb.sendLevel[r.efcCh]);
                        int revSampleR = (int) (sR[0] * effects.reverb.sendLevel[r.efcCh]);
                        buffer[dest + 0] += sL[0];
                        buffer[dest + 1] += sR[0];
                        effects.reverb.storeDataC(revSampleL, revSampleR);
                        visRtmVolume[0] += sample & maskL;
                        visRtmVolume[1] += sample & maskR;
                    }
                }
            }
        }
    }

    private static InputStream createRhythmFileStream(String dir, String fname) {
        try {
            Path path = dir == null || dir.isEmpty() ? Path.of(fname) : Path.of(dir, fname);
            return Files.exists(path) ? Files.newInputStream(path) : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean loadRhythmSample(String path) throws IOException {
        return loadRhythmSample(fname -> createRhythmFileStream(path, fname));
    }

    /**
     * Loading rhythm sounds
     */
    public boolean loadRhythmSample(Function<String, InputStream> appendFileReaderCallback) throws IOException {
        String[] rhythmName = {
                "bd", "sd", "top", "hh", "tom", "rim",
        };

        int i;
        for (i = 0; i < 6; i++)
            rhythm[i].pos = ~(int) 0;

        for (i = 0; i < 6; i++) {
            byte[] buf;
            int filePtr;

            int fSize;
            String fileName = "2608_%s.wav".formatted(rhythmName[i]);

            try (InputStream st = appendFileReaderCallback.apply(fileName)) {
                buf = st != null ? st.readAllBytes() : null;
            }

            if (buf == null) {
                if (i != 5)
                    break;
                String fileNameRym = "2608_rym.wav";
                try (InputStream st = appendFileReaderCallback.apply(fileNameRym)) {
                    buf = st != null ? st.readAllBytes() : null;
                }
            }

            Whdr whdr = new Whdr();

            filePtr = 0x10;
            byte[] bufWhdr = new byte[4 + 2 + 2 + 4 + 4 + 2 + 2 + 2];
            System.arraycopy(buf, filePtr, bufWhdr, 0, bufWhdr.length);

            whdr.chunkSize = (bufWhdr[0] & 0xff) + (bufWhdr[1] & 0xff) * 0x100 + (bufWhdr[2] & 0xff) * 0x1_0000 + (bufWhdr[3] & 0xff) * 0x1_0000;
            whdr.tag = (bufWhdr[4] & 0xff) + (bufWhdr[5] & 0xff) * 0x100;
            whdr.nch = (bufWhdr[6] & 0xff) + (bufWhdr[7] & 0xff) * 0x100;
            whdr.rate = (bufWhdr[8] & 0xff) + (bufWhdr[9] & 0xff) * 0x100 + (bufWhdr[10] & 0xff) * 0x1_0000 + (bufWhdr[11] & 0xff) * 0x1_0000;
            whdr.avgbytes = (bufWhdr[12] & 0xff) + (bufWhdr[13] & 0xff) * 0x100 + (bufWhdr[14] & 0xff) * 0x1_0000 + (bufWhdr[15] & 0xff) * 0x1_0000;
            whdr.align = (bufWhdr[16] & 0xff) + (bufWhdr[17] & 0xff) * 0x100;
            whdr.bps = (bufWhdr[18] & 0xff) + (bufWhdr[19] & 0xff) * 0x100;
            whdr.size = (bufWhdr[20] & 0xff) + (bufWhdr[21] & 0xff) * 0x100;

            byte[] subChunkName = new byte[4];
            fSize = 4 + whdr.chunkSize;
            do {
                filePtr += fSize;
                for (int ind = 0; ind < 4; ind++) {
                    subChunkName[ind] = buf[filePtr++];
                }
                for (int ind = 0; ind < 4; ind++) {
                    bufWhdr[ind] = buf[filePtr++];
                }
                fSize = (bufWhdr[0] & 0xff) + (bufWhdr[1] & 0xff) * 0x100 + (bufWhdr[2] & 0xff) * 0x1_0000 + (bufWhdr[3] & 0xff) * 0x1_0000;
            } while ('d' != subChunkName[0] && 'a' != subChunkName[1] && 't' != subChunkName[2] && 'a' != subChunkName[3]);

            fSize /= 2;
            if (fSize >= 0x100000 || whdr.tag != 1 || whdr.nch != 1)
                break;
            fSize = Math.max(fSize, 1 << 13);

            rhythm[i].sample = null;
            rhythm[i].sample = new int[fSize];
            if (rhythm[i].sample == null)
                break;
            byte[] bufSample = new byte[fSize * 2];
            for (int ind = 0; ind < (fSize * 2); ind++) {
                bufSample[ind] = buf[filePtr++];
            }
            for (int si = 0; si < fSize; si++) {
                rhythm[i].sample[si] = (bufSample[si * 2] & 0xff) + (bufSample[si * 2 + 1] & 0xff) * 0x100;
            }

            rhythm[i].rate = whdr.rate;
            rhythm[i].step = rhythm[i].rate * 1024 / rate;
            rhythm[i].pos = rhythm[i].size = fSize * 1024;
        }
        if (i != 6) {
            for (i = 0; i < 6; i++) {
                rhythm[i].sample = null;
            }
            return false;
        }
        return true;
    }

    /**
     * Volume Settings
     */
    public void setVolumeRhythmTotal(int db) {
        db = Math.min(db, 20);
        rhythmTVol = -(db * 2 / 3);
    }

    public void setVolumeRhythm(int index, int db) {
        db = Math.min(db, 20);
        rhythm[index].volume = -(db * 2 / 3);
    }

    @Override
    public void setTimerA(int addr, int data) {
        super.setTimerA(addr, data);
    }

    @Override
    public void setTimerB(int data) {
        super.setTimerB(data);
    }

    @Override
    public void setTimerControl(int data) {
        super.setTimerControl(data);
    }

    // Ym2609Inst
    public int[] update() {
        int[] updateBuffer = new int[2];
        updateBuffer[0] = this.effects.reverb.getDataFromPosL() >> 1;
        updateBuffer[1] = this.effects.reverb.getDataFromPosR() >> 1;

        this.effects.reverb.storeDataC(this.effects.reverb.getDataFromPosL() >> 1, this.effects.reverb.getDataFromPosR() >> 1);
        this.effects.reverb.clearDataAtPos();

        this.mix(updateBuffer, 1);

        this.effects.reverb.updatePos();

        return updateBuffer;
    }
}
