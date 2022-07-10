package mdsound.fmvgen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

import mdsound.Common;
import mdsound.fmgen.Opna;
import mdsound.fmvgen.Fmvgen.Effects;
import dotnet4j.io.File;
import dotnet4j.io.FileAccess;
import dotnet4j.io.FileMode;
import dotnet4j.io.FileStream;
import dotnet4j.io.Path;
import dotnet4j.io.Stream;
import mdsound.fmvgen.effect.ReversePhase;

import static mdsound.fmgen.Fmgen.limit;


/** YM2609(OPNA2) */
public class OPNA2 extends Opna.OPNABase {
    public static final float[] panTable = new float[] {1.0f, 0.7512f, 0.4512f, 0.0500f};

    // リズム音源関係
    private Rhythm[] rhythm;

    private byte rhythmTl; // リズム全体の音量
    private int rhythmTVol;
    private byte rhythmKey; // リズムのキー

    protected FM6[] fm6;
    protected PSG2[] psg2;
    protected AdpcmB[] adpcmB;
    protected AdpcmA adpcmA;

    protected byte prescale;

    public Effects effects;

    public static class Rhythm {
        // ぱん
        public byte pan;
        // おんりょう
        public byte level;
        // おんりょうせってい
        public int volume;
        // さんぷる
        public int[] sample;
        // さいず
        public int size;
        // いち
        public int pos;
        // すてっぷち
        public int step;
        // さんぷるのれーと
        public int rate;
        public int efcCh;
        public int num;
        public Effects effects;

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
     * 構築
     */
    public OPNA2(int clock) {
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

    public boolean init(int c, int r) {
        return init(c, r, false, "");
    }

    public boolean init(int c, int r, boolean ipFlag, String path) {
        return init(c, r, ipFlag, fname -> createRhythmFileStream(path, fname), null, 0);
    }

    public boolean init(int c, int r, boolean ipFlag,
                        Function<String, Stream> appendFileReaderCallback/* = null*/,
                        byte[] adpcmA/* = null*/, int adpcmaSize/* = 0*/) {
        rate = 8000;
        try {
            loadRhythmSample(appendFileReaderCallback);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (adpcmB[0].adpcmBuf == null)
            adpcmB[0].adpcmBuf = new byte[0x40000];
        if (adpcmB[0].adpcmBuf == null)
            return false;
        if (adpcmB[1].adpcmBuf == null)
            adpcmB[1].adpcmBuf = new byte[0x1000000];
        if (adpcmB[1].adpcmBuf == null)
            return false;
        if (adpcmB[2].adpcmBuf == null)
            adpcmB[2].adpcmBuf = new byte[0x1000000];
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
     * サンプリングレート変更
     */
    public boolean setRate(int c, int r, boolean ipflag/* = false*/) {
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
     * 合成
     * @param buffer 合成先
     * @param samples 合成サンプル数
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
     * リセット
     */
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

    protected void rebuildTimeTable() {
        super.rebuildTimeTable();

        int p = prescale;
        prescale = (byte) 0xff;//-1;
        setPreScaler(p);
    }

    public void setPreScaler(int p) {
        super.setPreScaler(p);

        final byte[][] table = new byte[][] {new byte[] {6, 4}, new byte[] {3, 2}, new byte[] {2, 1}};
        final byte[] table2 = new byte[] {108, 77, 71, 67, 62, 44, 8, 5};
        // 512
        if (prescale != p) {
            prescale = (byte) p;
            //assert(0 <= prescale && prescale< 3);

            int fmclock = clock / table[p][0] / 12;

            rate = psgRate;

            // 合成周波数と出力周波数の比
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
     * レジスタアレイにデータを設定
     */
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
                effects.distortion.setReg(0, (byte) data); // channel 変更はアドレスを共有
                effects.chorus.setReg(0, (byte) data);
                effects.hpflpf.setReg(0, (byte) data);
                effects.compressor.setReg(0, (byte) data);
            }
            return;
        } else if (addr >= 0x325 && addr < 0x328) {
            effects.distortion.setReg(addr - 0x324, (byte) data); // distortion のアドレス 0 はリバーブと共有
            return;
        } else if (addr >= 0x328 && addr < 0x32C) {
            effects.chorus.setReg(addr - 0x327, (byte) data); // chorus のアドレス 0 はリバーブと共有
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


    public int getReg(int addr) {
        return 0;
    }

    // 音量設定
    public void setVolumeFM(int db) {
        db = Math.min(db, 20);
        if (db > -192) {
            fm6[0].fmvolume = (int) (16384.0 * Math.pow(10.0, db / 40.0));
            fm6[1].fmvolume = (int) (16384.0 * Math.pow(10.0, db / 40.0));
        } else {
            fm6[0].fmvolume = 0;
            fm6[1].fmvolume = 0;
        }
    }

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
     * チャンネルマスクの設定
     */
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
     * リズム合成
     */
    private void rhythmMix(int[] buffer, int count) {
        if (rhythmTVol < 128 && rhythm[0].sample != null && ((rhythmKey & 0x3f) != 0)) {
            int limit = count * 2;
            visRtmVolume[0] = 0;
            visRtmVolume[1] = 0;
            for (int i = 0; i < 6; i++) {
                Rhythm r = rhythm[i];
                if ((rhythmKey & (1 << i)) != 0 && r.level < 128) {
                    int db = limit(rhythmTl + rhythmTVol + r.level + r.volume, 127, -31);
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
                        sL[0] *= ReversePhase.rhythm[i][0];
                        sR[0] *= ReversePhase.rhythm[i][1];
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

    private FileStream createRhythmFileStream(String dir, String fname) {
        String path = dir == null || dir.isEmpty() ? fname : Path.combine(dir, fname);
        return File.exists(path) ? new FileStream(path, FileMode.Open, FileAccess.Read) : null;
    }

    public boolean loadRhythmSample(String path) throws IOException {
        return loadRhythmSample(fname -> createRhythmFileStream(path, fname));
    }

    /**
     * リズム音を読みこむ
     */
    public boolean loadRhythmSample(Function<String, Stream> appendFileReaderCallback) throws IOException {
        final String[] rhythmName = {
                "bd", "sd", "top", "hh", "tom", "rim",
        };

        int i;
        for (i = 0; i < 6; i++)
            rhythm[i].pos = ~(int) 0;

        for (i = 0; i < 6; i++) {
            byte[] buf;
            int filePtr;

            int fSize;
            String fileName = String.format("2608_%s.wav", rhythmName[i]);

            try (Stream st = appendFileReaderCallback.apply(fileName)) {
                buf = Common.readAllBytes(st);
            }

            if (buf == null) {
                if (i != 5)
                    break;
                String fileNameRym = "2608_rym.wav";
                try (Stream st = appendFileReaderCallback.apply(fileNameRym)) {
                    buf = Common.readAllBytes(st);
                }
            }

            Whdr whdr = new Whdr();

            filePtr = 0x10;
            byte[] bufWhdr = new byte[4 + 2 + 2 + 4 + 4 + 2 + 2 + 2];
            System.arraycopy(buf, filePtr, bufWhdr, 0, bufWhdr.length);

            whdr.chunkSize = bufWhdr[0] + bufWhdr[1] * 0x100 + bufWhdr[2] * 0x10000 + bufWhdr[3] * 0x10000;
            whdr.tag = bufWhdr[4] + bufWhdr[5] * 0x100;
            whdr.nch = bufWhdr[6] + bufWhdr[7] * 0x100;
            whdr.rate = bufWhdr[8] + bufWhdr[9] * 0x100 + bufWhdr[10] * 0x10000 + bufWhdr[11] * 0x10000;
            whdr.avgbytes = bufWhdr[12] + bufWhdr[13] * 0x100 + bufWhdr[14] * 0x10000 + bufWhdr[15] * 0x10000;
            whdr.align = bufWhdr[16] + bufWhdr[17] * 0x100;
            whdr.bps = bufWhdr[18] + bufWhdr[19] * 0x100;
            whdr.size = bufWhdr[20] + bufWhdr[21] * 0x100;

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
                fSize = bufWhdr[0] + bufWhdr[1] * 0x100 + bufWhdr[2] * 0x10000 + bufWhdr[3] * 0x10000;
            } while ('d' != subChunkName[0] && 'a' != subChunkName[1] && 't' != subChunkName[2] && 'a' != subChunkName[3]);

            fSize /= 2;
            if (fSize >= 0x100000 || whdr.tag != 1 || whdr.nch != 1)
                break;
            fSize = Math.max(fSize, (1 << 31) / 1024);

            rhythm[i].sample = null;
            rhythm[i].sample = new int[fSize];
            if (rhythm[i].sample == null)
                break;
            byte[] bufSample = new byte[fSize * 2];
            for (int ind = 0; ind < (fSize * 2); ind++) {
                bufSample[ind] = buf[filePtr++];
            }
            for (int si = 0; si < fSize; si++) {
                rhythm[i].sample[si] = (short) (bufSample[si * 2] + bufSample[si * 2 + 1] * 0x100);
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
     * 音量設定
     */
    public void setVolumeRhythmTotal(int db) {
        db = Math.min(db, 20);
        rhythmTVol = -(db * 2 / 3);
    }

    public void setVolumeRhythm(int index, int db) {
        db = Math.min(db, 20);
        rhythm[index].volume = -(db * 2 / 3);
    }

    public void setTimerA(int addr, int data) {
        super.setTimerA(addr, data);
    }

    public void setTimerB(int data) {
        super.setTimerB(data);
    }

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
