package test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import dotnet4j.io.File;
import mdsound.MDSound;
import mdsound.chips.C140;
import mdsound.instrument.*;
import test.soundManager.DriverAction;
import test.soundManager.Pack;
import test.soundManager.RingBuffer;
import test.soundManager.SoundManager;
import vavi.util.ByteUtil;
import vavi.util.Debug;
import vavi.util.StringUtil;


public class Program {

    static String[] args;

    /**
     * アプリケーションのメイン エントリ ポイントです。
     */
    public static void main(String[] args) {
        Program.args = args;
        new FrmMain();
    }

    //----

    private static final int SamplingRate = 44100;
    private static final int SamplingBuffer = 1024 * 8;
    private short[] frames = new short[SamplingBuffer * 4];
    MDSound mds;

    private SourceDataLine audioOutput = null;

    private byte[] vgmBuf = null;
    private int vgmPcmPtr;
    private int vgmPcmBaseAdr;
    private int vgmAdr;
    private int vgmEof;
    private boolean vgmAnalyze;
    private VgmStream[] vgmStreams = new VgmStream[0x100];

    private byte[] bufYM2610AdpcmA = null;
    private byte[] bufYM2610AdpcmB = null;

    private static class VgmStream {

        public int chipId;
        public int port;
        public int cmd;

        public int databankId;
        public int stepsize;
        public int stepbase;

        public int frequency;

        public int dataStartOffset;
        public int lengthMode;
        public int dataLength;

        public boolean sw;

        public int blockId;

        public int wkDataAdr;
        public int wkDataLen;
        public double wkDataStep;
    }

    private short[] emuRenderBuf = new short[2];
    private int packCounter = 0;
    private Pack pack = new Pack();

    int driverSeqCounter = 0;
    int emuSeqCounter = 0;

    private final static int PCM_BANK_COUNT = 0x40;
    private VgmPcmBank[] pcmBanks = new VgmPcmBank[PCM_BANK_COUNT];

    public static class VgmPcmData {
        public int dataSize;
        public byte[] data;
        public int dataStart;
    }

    public static class VgmPcmBank {
        public int bankCount;
        public List<VgmPcmData> bank = new ArrayList<>();
        public int dataSize;
        public byte[] data;
        public int dataPos;
        public int bnkPos;
    }

    test.soundManager.SoundManager sm;
    private SoundManager.Enq enq;
    //    private static RealChip rc = null;
//    private static RSoundChip rsc = null;
    private RingBuffer emuRecvBuffer = null;

    /** */
    public Program() {
        // 実チップ(OPNA)を探す(無ければrscはnull)
//        rc = new RealChip();
//        rsc = rc.SearchOPNA();
//        if (rsc != null) {
//            rsc.init();
//        }

        mount();

        mds = new MDSound(SamplingRate, SamplingBuffer, null);

        try {
            audioOutput = AudioSystem.getSourceDataLine(new AudioFormat(SamplingRate, 16, 2, true, false));
            audioOutput.open();
            audioOutput.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** */
    private void mount() {
        sm = new test.soundManager.SoundManager();
        DriverAction driverAction = new DriverAction();
        driverAction.main = this::driverActionMain;
        driverAction.final_ = this::driverActionFinal;

//        if (rsc == null) {
//            sm.setup(driverAction, null, softInitYM2608(0x56), softResetYM2608(0x56));
//        } else {
        sm.setup(driverAction, this::realChipAction, softInitYM2608(-1), softResetYM2608(-1));
//        }

        enq = sm.getDriverDataEnqueue();
        emuRecvBuffer = sm.getEmuRecvBuffer();
    }

    /** */
    private void driverActionMain() {
        oneFrameVGM();
    }

    /** */
    private void driverActionFinal() {
        Pack[] data;

//         if (rsc == null) {
//             data = SoftResetYM2608(0x56);
//             dataEnq(DriverSeqCounter, 0x56, 0, -1, -1, data);
//         } else {
        data = softResetYM2608(-1);
        dataEnq(driverSeqCounter, -1, 0, -1, -1, (Object) data);
//         }
    }

    /** */
    private void realChipAction(long counter, int dev, int typ, int adr, int val, Object[] ex) {
        if (adr >= 0) {
//                rsc.setRegister(adr, val);
        } else {
            sm.setInterrupt();
            try {
                Pack[] data = (Pack[]) ex;
//                for (Pack dat : data) {
//                    rsc.setRegister(dat.adr, dat.val);
//                }
//                rc.WaitOPNADPCMData(true);
            } finally {
                sm.resetInterrupt();
            }
        }
    }

    /** */
    private void unmount() {
        sm.requestStop();
        while (sm.isRunningAsync()) ;
        sm.release();
    }

    /** */
    public void dataEnq(int counter, int dev, int typ, int adr, int val, Object... ex) {
        while (!enq.apply(counter, dev, typ, adr, val, ex)) Thread.yield();
    }

    /** */
    private static Pack[] softInitYM2608(int dev) {
        List<Pack> data = new ArrayList<>();

        data.add(new Pack(dev, 0, 0x2d, 0x00));
        data.add(new Pack(dev, 0, 0x29, 0x82));
        data.add(new Pack(dev, 0, 0x07, 0x38)); // Psg TONE でリセット
        for (int i = 0xb4; i < 0xb4 + 3; i++) {
            data.add(new Pack(dev, 0, i, 0xc0));
            data.add(new Pack(dev, 0, 0x100 + i, 0xc0));
        }

        return data.toArray(Pack[]::new);
    }

    /** */
    private static Pack[] softResetYM2608(int dev) {
        List<Pack> data = new ArrayList<>();

        // FM全チャネルキーオフ
        data.add(new Pack(dev, 0, 0x28, 0x00));
        data.add(new Pack(dev, 0, 0x28, 0x01));
        data.add(new Pack(dev, 0, 0x28, 0x02));
        data.add(new Pack(dev, 0, 0x28, 0x04));
        data.add(new Pack(dev, 0, 0x28, 0x05));
        data.add(new Pack(dev, 0, 0x28, 0x06));

        // FM TL=127
        for (int i = 0x40; i < 0x4F + 1; i++) {
            data.add(new Pack(dev, 0, i, 0x7f));
            data.add(new Pack(dev, 0, 0x100 + i, 0x7f));
        }
        // FM ML/DT
        for (int i = 0x30; i < 0x3F + 1; i++) {
            data.add(new Pack(dev, 0, i, 0x0));
            data.add(new Pack(dev, 0, 0x100 + i, 0x0));
        }
        // FM AR,DR,SR,KS,AMON
        for (int i = 0x50; i < 0x7F + 1; i++) {
            data.add(new Pack(dev, 0, i, 0x0));
            data.add(new Pack(dev, 0, 0x100 + i, 0x0));
        }
        // FM SL,RR
        for (int i = 0x80; i < 0x8F + 1; i++) {
            data.add(new Pack(dev, 0, i, 0xff));
            data.add(new Pack(dev, 0, 0x100 + i, 0xff));
        }
        // FM F-Num, FB/CONNECT
        for (int i = 0x90; i < 0xBF + 1; i++) {
            data.add(new Pack(dev, 0, i, 0x0));
            data.add(new Pack(dev, 0, 0x100 + i, 0x0));
        }
        // FM PAN/AMS/PMS
        for (int i = 0xB4; i < 0xB6 + 1; i++) {
            data.add(new Pack(dev, 0, i, 0xc0));
            data.add(new Pack(dev, 0, 0x100 + i, 0xc0));
        }
        data.add(new Pack(dev, 0, 0x22, 0x00)); // HW LFO
        data.add(new Pack(dev, 0, 0x24, 0x00)); // Timer-A(1)
        data.add(new Pack(dev, 0, 0x25, 0x00)); // Timer-A(2)
        data.add(new Pack(dev, 0, 0x26, 0x00)); // Timer-B
        data.add(new Pack(dev, 0, 0x27, 0x30)); // Timer Controller
        data.add(new Pack(dev, 0, 0x29, 0x80)); // FM4-6 Enable

        // SSG 音程(2byte*3ch)
        for (int i = 0x00; i < 0x05 + 1; i++) {
            data.add(new Pack(dev, 0, i, 0x00));
        }
        data.add(new Pack(dev, 0, 0x06, 0x00)); // SSG ノイズ周波数
        data.add(new Pack(dev, 0, 0x07, 0x38)); // SSG ミキサ
        // SSG ボリューム(3ch)
        for (int i = 0x08; i < 0x0A + 1; i++) {
            data.add(new Pack(dev, 0, i, 0x00));
        }
        // SSG Envelope
        for (int i = 0x0B; i < 0x0D + 1; i++) {
            data.add(new Pack(dev, 0, i, 0x00));
        }

        // RHYTHM
        data.add(new Pack(dev, 0, 0x10, 0xBF)); // 強制発音停止
        data.add(new Pack(dev, 0, 0x11, 0x00)); // Total Level
        data.add(new Pack(dev, 0, 0x18, 0x00)); // BD音量
        data.add(new Pack(dev, 0, 0x19, 0x00)); // SD音量
        data.add(new Pack(dev, 0, 0x1A, 0x00)); // CYM音量
        data.add(new Pack(dev, 0, 0x1B, 0x00)); // HH音量
        data.add(new Pack(dev, 0, 0x1C, 0x00)); // TOM音量
        data.add(new Pack(dev, 0, 0x1D, 0x00)); // RIM音量

        // ADPCM
        data.add(new Pack(dev, 0, 0x100 + 0x00, 0x21)); // ADPCMリセット
        data.add(new Pack(dev, 0, 0x100 + 0x01, 0x06)); // ADPCM消音
        data.add(new Pack(dev, 0, 0x100 + 0x10, 0x9C)); // FLAGリセット

        return data.toArray(Pack[]::new);
    }

    public void prePlay(String fileName) {
        sm.requestStop();
        while (sm.isRunningAsync()) {
            Thread.yield();
        }

        driverSeqCounter = sm.getDriverSeqCounterDelay();
        driverSeqCounter = 0;

        play(fileName);

        sm.requestStart();
        while (!sm.isRunningAsync()) {
//Debug.println("loop");
            emuCallback();
            Thread.yield();
        }

//        if (rsc == null) {
//            sm.RequestStopAtRealChipSender();
//            while (sm.IsRunningAtRealChipSender()) ;
//        } else {
        sm.requestStopAtEmuChipSender();
        while (sm.isRunningAtEmuChipSender()) Thread.yield();
Debug.println("done");
//        }
    }

    public void stop() {
        sm.requestStop();
        while (sm.isRunningAsync()) Thread.yield();
    }

    public void close() {
        sm.requestStop();
        while (sm.isRunningAsync()) Thread.yield();

        unmount();

//        if (rc != null) {
//            rc.close();
//        }

        audioOutput.close();
    }

    private void play(String fileName) {

        vgmBuf = File.readAllBytes(fileName);
Debug.println("\n" + StringUtil.getDump(vgmBuf, 128));

        for (int i = 0; i < pcmBanks.length; i++) {
            pcmBanks[i] = new VgmPcmBank();
        }

        // ヘッダーを読み込めるサイズをもっているかチェック
        if (vgmBuf.length < 0x40) {
Debug.printf("not enough vgm buffer size: ", vgmBuf.length);
            return;
        }

        // ヘッダーから情報取得

        int vgm = ByteUtil.readLeInt(vgmBuf, 0x00);
        if (vgm != 0x206d6756) {
Debug.printf("not a vgm file, suspect vgz, %08x", vgm);
            return;
        }

        int version = ByteUtil.readLeInt(vgmBuf, 0x08);
        if (version < 0x0150) {
Debug.printf("version is after 1.50, %04x", version);
            return;
        }

        vgmEof = ByteUtil.readLeInt(vgmBuf, 0x04);

        int vgmDataOffset = ByteUtil.readLeInt(vgmBuf, 0x34);
        if (vgmDataOffset == 0) {
            vgmDataOffset = 0x40;
        } else {
            vgmDataOffset += 0x34;
        }

        vgmAdr = vgmDataOffset;
        vgmAnalyze = true;

        MDSound.Chip[] chips;
        List<MDSound.Chip> lstChip = new ArrayList<>();
        MDSound.Chip chip;

        if (ByteUtil.readLeInt(vgmBuf, 0x0c) != 0) {
            chip = new MDSound.Chip();
            chip.id = 0;
            chip.instrument = new Sn76496Inst();
            chip.samplingRate = SamplingRate;
            chip.clock = ByteUtil.readLeInt(vgmBuf, 0x0c);
            chip.volume = 0;
            if (version < 0x0150) {
                chip.option = new Object[] {9, 0, 16, 0};
            } else {
                chip.option = new Object[] {vgmBuf[0x28] & 0xff, vgmBuf[0x29] & 0xff, vgmBuf[0x2a] & 0xff, vgmBuf[0x2b] & 0xff};
            }
            lstChip.add(chip);
        }

        if (ByteUtil.readLeInt(vgmBuf, 0x10) != 0) {
            chip = new MDSound.Chip();
            chip.id = 0;
            Ym2413Inst ym2413 = new Ym2413Inst();
            chip.instrument = ym2413;
            chip.samplingRate = SamplingRate;
            chip.clock = ByteUtil.readLeInt(vgmBuf, 0x10);
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (ByteUtil.readLeInt(vgmBuf, 0x2c) != 0) {
            chip = new MDSound.Chip();
            chip.id = 0;
            MameYm2612Inst ym2612 = new MameYm2612Inst();
            chip.instrument = ym2612;
            chip.samplingRate = SamplingRate;
            chip.clock = ByteUtil.readLeInt(vgmBuf, 0x2c);
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (ByteUtil.readLeInt(vgmBuf, 0x30) != 0) {
            chip = new MDSound.Chip();
            chip.id = 0;
            X68SoundYm2151Inst ym2151 = new X68SoundYm2151Inst();
            chip.instrument = ym2151;
            chip.samplingRate = SamplingRate;
            chip.clock = ByteUtil.readLeInt(vgmBuf, 0x30);
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (ByteUtil.readLeInt(vgmBuf, 0x38) != 0 && 0x38 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            SegaPcmInst segapcm = new SegaPcmInst();
            chip.instrument = segapcm;
            chip.samplingRate = SamplingRate;
            chip.clock = ByteUtil.readLeInt(vgmBuf, 0x38);
            chip.option = new Object[] {ByteUtil.readLeInt(vgmBuf, 0x3c)};
            chip.volume = 0;

            lstChip.add(chip);
        }

        if (ByteUtil.readLeInt(vgmBuf, 0x44) != 0 && 0x44 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            Ym2203Inst ym2203 = new Ym2203Inst();
            chip.instrument = ym2203;
            chip.samplingRate = SamplingRate;
            chip.clock = ByteUtil.readLeInt(vgmBuf, 0x44);
            chip.volume = 0;
            chip.setVolumes.put("FM", ym2203::setFMVolume);
            chip.setVolumes.put("PSG", ym2203::setPSGVolume);
            chip.option = null;
            lstChip.add(chip);
        }

        if (ByteUtil.readLeInt(vgmBuf, 0x48) != 0 && 0x48 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            Ym2608Inst ym2608 = new Ym2608Inst();
            chip.instrument = ym2608;
            chip.samplingRate = SamplingRate;
            chip.clock = ByteUtil.readLeInt(vgmBuf, 0x48);
            chip.volume = 0;
            chip.setVolumes.put("FM", ym2608::setFMVolume);
            chip.setVolumes.put("PSG", ym2608::setPSGVolume);
            chip.setVolumes.put("Rhythm", ym2608::setRhythmVolume);
            chip.setVolumes.put("Adpcm", ym2608::setAdpcmVolume);
            chip.option = null;
            lstChip.add(chip);
        }
//        if (getLE32(0x48) != 0 && 0x48 < vgmDataOffset - 3) {
//            chips = new MDSound.Chip();
//            chips.id = 0;
//            mdsound.instrument.Ym2609Inst Ym2609Inst = new mdsound.instrument.Ym2609Inst();
//            chips.instrument = Ym2609Inst;
//            chips.samplingRate = SamplingRate;
//            chips.clock = getLE32(0x48);
//            chips.volume = 0;
//            chips.option = null;
//            lstChip.add(chips);
//        }

        if (ByteUtil.readLeInt(vgmBuf, 0x4c) != 0 && 0x4c < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            Ym2610Inst ym2610 = new Ym2610Inst();
            chip.instrument = ym2610;
            chip.samplingRate = SamplingRate;
            chip.clock = ByteUtil.readLeInt(vgmBuf, 0x4c) & 0x7fff_ffff;
            chip.volume = 0;
            chip.setVolumes.put("FM", ym2610::setFMVolume);
            chip.setVolumes.put("PSG", ym2610::setPSGVolume);
            chip.setVolumes.put("AdpcmA", ym2610::setAdpcmAVolume);
            chip.setVolumes.put("AdpcmB", ym2610::setAdpcmBVolume);
            chip.option = null;
            bufYM2610AdpcmA = null;
            bufYM2610AdpcmB = null;
            lstChip.add(chip);
        }

        if (ByteUtil.readLeInt(vgmBuf, 0x50) != 0 && 0x50 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            Ym3812Inst ym3812 = new Ym3812Inst();
            chip.instrument = ym3812;
            chip.samplingRate = SamplingRate;
            chip.clock = ByteUtil.readLeInt(vgmBuf, 0x50) & 0x7fff_ffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (ByteUtil.readLeInt(vgmBuf, 0x54) != 0 && 0x54 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            Ym3526Inst ym3526 = new Ym3526Inst();
            chip.instrument = ym3526;
            chip.samplingRate = SamplingRate;
            chip.clock = ByteUtil.readLeInt(vgmBuf, 0x54) & 0x7fff_ffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (ByteUtil.readLeInt(vgmBuf, 0x5c) != 0 && 0x5c < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            YmF262Inst ymf262 = new YmF262Inst();
            chip.instrument = ymf262;
            chip.samplingRate = SamplingRate;
            chip.clock = ByteUtil.readLeInt(vgmBuf, 0x5c) & 0x7fff_ffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);

//            chips = new MDSound.Chip();
//            chips.id = 0;
//            chips.instrument = YmF278bInst();
//            chips.samplingRate = SamplingRate;
//            chips.clock = getLE32(0x5c) & 0x7fff_ffff;
//            chips.volume = 0;
//            chips.option = null;
//            lstChip.add(chips);
        }

        if (ByteUtil.readLeInt(vgmBuf, 0x58) != 0 && 0x58 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            Y8950Inst y8950 = new Y8950Inst();
            chip.instrument = y8950;
            chip.samplingRate = SamplingRate;
            chip.clock = ByteUtil.readLeInt(vgmBuf, 0x58) & 0x7fff_ffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (ByteUtil.readLeInt(vgmBuf, 0x60) != 0 && 0x60 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            YmF278bInst ymf278b = new YmF278bInst();
            chip.instrument = ymf278b;
            chip.samplingRate = SamplingRate;
            chip.clock = ByteUtil.readLeInt(vgmBuf, 0x60) & 0x7fff_ffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (ByteUtil.readLeInt(vgmBuf, 0x64) != 0 && 0x64 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            YmF271Inst ymf271 = new YmF271Inst();
            chip.instrument = ymf271;
            chip.samplingRate = SamplingRate;
            chip.clock = ByteUtil.readLeInt(vgmBuf, 0x64) & 0x7fff_ffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (ByteUtil.readLeInt(vgmBuf, 0x68) != 0 && 0x68 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            YmZ280bInst ymz280b = new YmZ280bInst();
            chip.instrument = ymz280b;
            chip.samplingRate = SamplingRate;
            chip.clock = ByteUtil.readLeInt(vgmBuf, 0x68) & 0x7fff_ffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (ByteUtil.readLeInt(vgmBuf, 0x74) != 0 && 0x74 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            MameAy8910Inst ay8910 = new MameAy8910Inst();
            chip.instrument = ay8910;
            chip.samplingRate = SamplingRate;
            chip.clock = ByteUtil.readLeInt(vgmBuf, 0x74) & 0x7fff_ffff;
            chip.clock /= 2;
            if ((vgmBuf[0x79] & 0x10) != 0) chip.clock /= 2;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (version >= 0x0161 && 0x80 < vgmDataOffset - 3) {

            if (ByteUtil.readLeInt(vgmBuf, 0x80) != 0 && 0x80 < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                DmgInst gb = new DmgInst();
                chip.instrument = gb;
                chip.samplingRate = SamplingRate;
                chip.clock = ByteUtil.readLeInt(vgmBuf, 0x80);
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (ByteUtil.readLeInt(vgmBuf, 0x84) != 0 && 0x84 < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                IntFNesInst nes_intf = new IntFNesInst();
                chip.instrument = nes_intf;
                chip.samplingRate = SamplingRate;
                chip.clock = ByteUtil.readLeInt(vgmBuf, 0x84);
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (ByteUtil.readLeInt(vgmBuf, 0x88) != 0 && 0x88 < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                MultiPcmInst multipcm = new MultiPcmInst();
                chip.instrument = multipcm;
                chip.samplingRate = SamplingRate;
                chip.clock = ByteUtil.readLeInt(vgmBuf, 0x88) & 0x7fff_ffff;
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (ByteUtil.readLeInt(vgmBuf, 0x90) != 0 && 0x90 < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                OkiM6258Inst okim6258 = new OkiM6258Inst();
                chip.instrument = okim6258;
                chip.samplingRate = SamplingRate;
                chip.clock = ByteUtil.readLeInt(vgmBuf, 0x90) & 0xbfff_ffff;
                chip.volume = 0;
                chip.option = new Object[] {vgmBuf[0x94] & 0xff};
                okim6258.okim6258_set_srchg_cb((byte) 0, Program::changeChipSampleRate, chip);
                lstChip.add(chip);
            }

            if (ByteUtil.readLeInt(vgmBuf, 0x98) != 0 && 0x98 < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                OkiM6295Inst okim6295 = new OkiM6295Inst();
                chip.instrument = okim6295;
                chip.samplingRate = SamplingRate;
                chip.clock = ByteUtil.readLeInt(vgmBuf, 0x98) & 0xbfff_ffff;
                chip.volume = 0;
                chip.option = null;
                okim6295.okim6295_set_srchg_cb((byte) 0, Program::changeChipSampleRate, chip);
                lstChip.add(chip);
            }

            if (ByteUtil.readLeInt(vgmBuf, 0x9c) != 0 && 0x9c < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                K051649Inst k051649 = new K051649Inst();
                chip.instrument = k051649;
                chip.samplingRate = SamplingRate;
                chip.clock = ByteUtil.readLeInt(vgmBuf, 0x9c);
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (ByteUtil.readLeInt(vgmBuf, 0xa0) != 0 && 0xa0 < vgmDataOffset - 3) {
                K054539Inst k054539 = new K054539Inst();
                int max = (ByteUtil.readLeInt(vgmBuf, 0xa0) & 0x4000_0000) != 0 ? 2 : 1;
                for (int i = 0; i < max; i++) {
                    chip = new MDSound.Chip();
                    chip.id = i;
                    chip.instrument = k054539;
                    chip.samplingRate = SamplingRate;
                    chip.clock = ByteUtil.readLeInt(vgmBuf, 0xa0) & 0x3fff_ffff;
                    chip.volume = 0;
                    chip.option = new Object[] {vgmBuf[0x95]};

                    lstChip.add(chip);
                }
            }

            if (ByteUtil.readLeInt(vgmBuf, 0xa4) != 0 && 0xa4 < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                HuC6280Inst huc8910 = new HuC6280Inst();
                chip.instrument = huc8910;
                chip.samplingRate = SamplingRate;
                chip.clock = ByteUtil.readLeInt(vgmBuf, 0xa4);
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (ByteUtil.readLeInt(vgmBuf, 0xa8) != 0 && 0xa8 < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                C140Inst c140 = new C140Inst();
                chip.instrument = c140;
                chip.samplingRate = SamplingRate;
                chip.clock = ByteUtil.readLeInt(vgmBuf, 0xa8);
                chip.volume = 0;
                chip.option = new Object[] {C140.Type.valueOf(vgmBuf[0x96] & 0xff)};
                lstChip.add(chip);
            }

            if (ByteUtil.readLeInt(vgmBuf, 0xac) != 0 && 0xac < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                K053260Inst k053260 = new K053260Inst();
                chip.instrument = k053260;
                chip.samplingRate = SamplingRate;
                chip.clock = ByteUtil.readLeInt(vgmBuf, 0xac);
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (ByteUtil.readLeInt(vgmBuf, 0xb4) != 0 && 0xb4 < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                CtrQSoundInst qsound = new CtrQSoundInst();
                chip.instrument = qsound;
                chip.samplingRate = SamplingRate;
                chip.clock = ByteUtil.readLeInt(vgmBuf, 0xb4);
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (version >= 0x170 && 0xdc < vgmDataOffset - 3) {
                if (version >= 0x171) {
                    if (ByteUtil.readLeInt(vgmBuf, 0xc0) != 0 && 0xc0 < vgmDataOffset - 3) {
                        chip = new MDSound.Chip();
                        chip.id = 0;
                        WsAudioInst wswan = new WsAudioInst();
                        chip.instrument = wswan;
                        chip.samplingRate = SamplingRate;
                        chip.clock = ByteUtil.readLeInt(vgmBuf, 0xc0);
                        chip.volume = 0;
                        chip.option = null;

                        lstChip.add(chip);
                    }

                    if (ByteUtil.readLeInt(vgmBuf, 0xdc) != 0 && 0xdc < vgmDataOffset - 3) {
                        chip = new MDSound.Chip();
                        chip.id = 0;
                        C352Inst c352 = new C352Inst();
                        chip.instrument = c352;
                        chip.samplingRate = SamplingRate;
                        chip.clock = ByteUtil.readLeInt(vgmBuf, 0xdc);
                        chip.volume = 0;
                        chip.option = new Object[] {vgmBuf[0xd6]};

                        lstChip.add(chip);
                    }

                    if (ByteUtil.readLeInt(vgmBuf, 0xe0) != 0 && 0xe0 < vgmDataOffset - 3) {
                        chip = new MDSound.Chip();
                        chip.id = 0;
                        Ga20Inst ga20 = new Ga20Inst();
                        chip.instrument = ga20;
                        chip.samplingRate = SamplingRate;
                        chip.clock = ByteUtil.readLeInt(vgmBuf, 0xe0);
                        chip.volume = 0;
                        chip.option = null;

                        lstChip.add(chip);
                    }
                }
            }
        }

        chips = lstChip.toArray(MDSound.Chip[]::new);
Debug.println("chips: " + chips.length);
        mds.init(SamplingRate, SamplingBuffer, chips);
    }

    public static void changeChipSampleRate(MDSound.Chip chip, int NewSmplRate) {
        MDSound.Chip caa = chip;

        if (caa.samplingRate == NewSmplRate) return;

        // quick and dirty hack to make sample rate changes work
        caa.samplingRate = NewSmplRate;
        if (caa.samplingRate < SamplingRate) caa.resampler = 0x01;
        else if (caa.samplingRate == SamplingRate) caa.resampler = 0x02;
        else if (caa.samplingRate > SamplingRate) caa.resampler = 0x03;
        caa.smpP = 1;
        caa.smpNext -= caa.smpLast;
        caa.smpLast = 0x00;
    }

    static int dummy = 0;

    private void emuCallback() {
        int len = 512;
        byte[] frames = new byte[len];
        int bufCnt = len / 4;
        int seqCnt = sm.getSeqCounter();
        emuSeqCounter = seqCnt - bufCnt;
        emuSeqCounter = Math.max(emuSeqCounter, 0);

        for (int i = 0; i < bufCnt; i++) {
            mds.update(emuRenderBuf, 0, 2, this::oneFrameVGMaaa);

            ByteUtil.writeLeShort(emuRenderBuf[0], frames, i * 4 + 0);
            ByteUtil.writeLeShort(emuRenderBuf[1], frames, i * 4 + 2);
//            Debug.print("adr[%8x] : Wait[%8d] : [%8d]/[%8d]\n", vgmAdr, 0, 0, 0);
//            dummy++;
//            dummy %= 500;
//            frames[i * 2 + 0] = (short) dummy; // (dummy < 100 ? 0xfff : 0x000);
        }
if (dummy++ > 300) System.exit(1);
Debug.println("\n" + StringUtil.getDump(frames, 32));
        audioOutput.write(frames, 0, len / 2);
    }

    private void oneFrameVGMaaa() {
        if (driverSeqCounter > 0) {
            driverSeqCounter--;
            return;
        }

        oneFrameVGM();
    }

    private void oneFrameVGM() {

        if (!vgmAnalyze) {
Debug.println("vgmAnalyz is flase");
            return;
        }

        int p;
        int si;
        int rAdr;
        int rDat;

        if (vgmAdr == vgmBuf.length || vgmAdr == vgmEof) {
            vgmAnalyze = false;
            sm.requestStopAtDataMaker();
Debug.println("eof");
            return;
        }

//Debug.println("vgmAdr: " + vgmAdr);
        byte cmd = vgmBuf[vgmAdr];
        //Debug.print(" adr[%x]:cmd[%x]\r\n", vgmAdr, cmd);
        switch (cmd & 0xff) {
        case 0x4f: // GG Psg
        case 0x50: // Psg
            mds.write(Sn76489Inst.class, 0, 0, 0, vgmBuf[vgmAdr + 1] & 0xff);
            //mds.write(SN76496Inst.class, 0, vgmBuf[vgmAdr + 1]);
            vgmAdr += 2;
            break;
        case 0x51: // YM2413
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            //mds.write(YM2413Inst.class, 0, rAdr, rDat);
            break;
        case 0x52: // Ym2612Inst Port0
        case 0x53: // Ym2612Inst Port1
            p = (cmd == 0x52) ? 0 : 1;
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            mds.write(Ym2612Inst.class, 0, p, rAdr, rDat);

            break;
        case 0x54: // YM2151
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            //Debug.print(" adr[%x]:cmd[%x]:adr[%x]:Dar[%x]\r\n", vgmAdr, cmd,rAdr,rDat);
            mds.write(Ym2151Inst.class, 0, 0, rAdr, rDat);
            break;
        case 0x55: // YM2203
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            //mds.writeY
            // 2203(0, rAdr, rDat);

            break;
//        case 0x56: // YM2608 Port0
//            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
//            rDat = vgmBuf[vgmAdr + 2] & 0xff;
//            vgmAdr += 3;
//            mds.write(Ym2608Inst.class, 0, 0, rAdr, rDat);
//
//            break;
//        case 0x57: // YM2608 Port1
//            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
//            rDat = vgmBuf[vgmAdr + 2] & 0xff;
//            vgmAdr += 3;
//            mds.write(Ym2608Inst.class, 0, 1, rAdr, rDat);
//
//            break;
        case 0x56: // YM2609 Port0
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
//            if (rsc == null) dataEnq(DriverSeqCounter, 0x56, 0, 0 * 0x100 + rAdr, rDat);
//            else
            dataEnq(driverSeqCounter, -1, 0, 0 * 0x100 + rAdr, rDat);
            //mds.write(Ym2609Inst.class, 0, 0, rAdr, rDat);
            break;
        case 0x57: // YM2609 Port1
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
//            if (rsc == null) dataEnq(DriverSeqCounter, 0x56, 0, 1 * 0x100 + rAdr, rDat);
//            else
            dataEnq(driverSeqCounter, -1, 0, 1 * 0x100 + rAdr, rDat);
            //mds.write(YM2609Inst.class, 0, 1, rAdr, rDat);

            break;
        case 0x58: // YM2610 Port0
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            //mds.write(YM2610Inst.class, 0, 0, rAdr, rDat);

            break;
        case 0x59: // YM2610 Port1
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            //mds.write(YM2610Inst.class, 0, 1, rAdr, rDat);

            break;
        case 0x5a: // YM3812
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            //mds.write(YM3812Inst.class, 0, rAdr, rDat);

            break;
        case 0x5b: // YM3526
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            //mds.write(YM3526Inst.class, 0, rAdr, rDat);

            break;
        case 0x5c: // Y8950Inst
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            //mds.write(Y8950Inst.class, 0, rAdr, rDat);

            break;
        case 0x5D: // YMZ280B
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            // mds.write(YMZ280BInst.class, 0, rAdr, rDat);

            break;
        case 0x5e: // YMF262 Port0
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;

            mds.write(YmF262Inst.class, 0, 0, rAdr, rDat);
            //mds.write(YMF278BInst.class, 0, 0, rAdr, rDat);
            //Debug.printf("P0:adr%2x:dat%2x", rAdr, rDat);
            break;
        case 0x5f: // YMF262 Port1
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;

            mds.write(YmF262Inst.class, 0, 1, rAdr, rDat);
            //mds.write(YMF278BInst.class, 0, 1, rAdr, rDat);
            //Debug.printf("P1:adr%2x:dat%2x", rAdr, rDat);

            break;
        case 0x61: // Wait n samples
            vgmAdr++;
            driverSeqCounter += ByteUtil.readLeShort(vgmBuf, vgmAdr);
            vgmAdr += 2;
            break;
        case 0x62: // Wait 735 samples
            vgmAdr++;
            driverSeqCounter += 735;
            break;
        case 0x63: // Wait 882 samples
            vgmAdr++;
            driverSeqCounter += 882;
            break;
        case 0x64: // override length of 0x62/0x63
            vgmAdr += 4;
            break;
        case 0x66: // end of Sound data
            vgmAdr = vgmBuf.length;
            break;
        case 0x67: // data block
            vgmPcmBaseAdr = vgmAdr + 7;
            int bAdr = vgmAdr + 7;
            int bType = vgmBuf[vgmAdr + 2] & 0xff;
            int bLen = ByteUtil.readLeInt(vgmBuf, vgmAdr + 3);
            //byte chipId = 0;
            if ((bLen & 0x8000_0000) != 0) {
                bLen &= 0x7fff_ffff;
                //chipId = 1;
            }

            switch (bType & 0xc0) {
            case 0x00:
            case 0x40:
                //addPCMData(bType, bLen, bAdr);
                vgmAdr += bLen + 7;
                break;
            case 0x80:
                int romSize = ByteUtil.readLeInt(vgmBuf, vgmAdr + 7);
                int startAddress = ByteUtil.readLeInt(vgmBuf, vgmAdr + 0x0B);
                switch (bType & 0xff) {
                case 0x80:
                    // SEGA PCM
                    //mds.write(SEGAPCMPCMDataInst.class, chipId, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;
                case 0x81:

                    // YM2608/YM2609
                    List<Pack> data = Arrays.asList(
                            new Pack(0, 0, 0x100 + 0x00, 0x20),
                            new Pack(0, 0, 0x100 + 0x00, 0x21),
                            new Pack(0, 0, 0x100 + 0x00, 0x00),

                            new Pack(0, 0, 0x100 + 0x10, 0x00),
                            new Pack(0, 0, 0x100 + 0x10, 0x80),

                            new Pack(0, 0, 0x100 + 0x00, 0x61),
                            new Pack(0, 0, 0x100 + 0x00, 0x68),
                            new Pack(0, 0, 0x100 + 0x01, 0x00),

                            new Pack(0, 0, 0x100 + 0x02, startAddress >> 2),
                            new Pack(0, 0, 0x100 + 0x03, startAddress >> 10),
                            new Pack(0, 0, 0x100 + 0x04, 0xff),
                            new Pack(0, 0, 0x100 + 0x05, 0xff),
                            new Pack(0, 0, 0x100 + 0x0c, 0xff),
                            new Pack(0, 0, 0x100 + 0x0d, 0xff));

                    // データ転送
                    for (int cnt = 0; cnt < bLen - 8; cnt++) {
                        data.add(new Pack(0, 0, 0x100 + 0x08, vgmBuf[vgmAdr + 15 + cnt] & 0xff));
                    }
                    data.add(new Pack(0, 0, 0x100 + 0x00, 0x00));
                    data.add(new Pack(0, 0, 0x100 + 0x10, 0x80));

//                    if (rsc == null) dataEnq(DriverSeqCounter, 0x56, 0, -1, -1, data.toArray());
//                    else {
                    dataEnq(driverSeqCounter, -1, 0, -1, -1, data.toArray());
                    driverSeqCounter += bLen;
//                    }

                    break;

                case 0x82:
                    if (bufYM2610AdpcmA == null || bufYM2610AdpcmA.length != romSize)
                        bufYM2610AdpcmA = new byte[romSize];
                    for (int cnt = 0; cnt < bLen - 8; cnt++) {
                        bufYM2610AdpcmA[startAddress + cnt] = vgmBuf[vgmAdr + 15 + cnt];
                    }
                    //mds.write(YM2610_SetAdpcmAInst.class, 0, bufYM2610AdpcmA);
                    break;
                case 0x83:
                    if (bufYM2610AdpcmB == null || bufYM2610AdpcmB.length != romSize)
                        bufYM2610AdpcmB = new byte[romSize];
                    for (int cnt = 0; cnt < bLen - 8; cnt++) {
                        bufYM2610AdpcmB[startAddress + cnt] = vgmBuf[vgmAdr + 15 + cnt];
                    }
                    //mds.write(YM2610_SetAdpcmBInst.class, 0, bufYM2610AdpcmB);
                    break;

                case 0x84:
                    //mds.write(YMF278BPCMDataInst.class, chipId, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x85:
                    //mds.write(YMF271PCMDataInst.class, chipId, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x86:
                    //mds.write(YMZ280BPCMDataInst.class, chipId, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x88:
                    //mds.write(Y8950PCMDataInst.class, chipId, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x89:
                    //mds.write(MultiPCMPCMDataInst.class, chipId, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x8b:
                    //mds.write(OKIM6295PCMDataInst.class, chipId, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x8c:
                    //mds.write(K054539PCMDataInst.class, chipId, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x8d:
                    //mds.write(C140PCMDataInst.class, chipId, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x8e:
                    //mds.write(K053260PCMDataInst.class, chipId, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x8f:
                    mds.writeQSoundPCMData((byte) 0, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x92:
                    //mds.write(C352PCMDataInst.class, 0, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x93:
                    //mds.write(GA20PCMDataInst.class, 0, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;
                }
                vgmAdr += bLen + 7;
                break;
            default:
                vgmAdr += bLen + 7;
                break;
            }
            break;
        case 0x68: // PCM RAM writes
            int chipType = vgmBuf[vgmAdr + 2] & 0xff;
            int chipReadOffset = ByteUtil.readLe24(vgmBuf, vgmAdr + 3);
            int chipWriteOffset = ByteUtil.readLe24(vgmBuf, vgmAdr + 6);
            int chipDataSize = ByteUtil.readLe24(vgmBuf, vgmAdr + 9);
            if (chipDataSize == 0) chipDataSize = 0x100_0000;
            Integer pcmAdr = getPCMAddressFromPCMBank(chipType, chipReadOffset);
            if (pcmAdr != null && chipType == 0x01) {
                //mds.write(RF5C68PCMDataInst.class, 0, chipWriteOffset, chipDataSize, pcmBanks[chipType].data, pcmAdr);
            }

            vgmAdr += 12;
            break;
        case 0x70: // Wait 1 sample
        case 0x71: // Wait 2 sample
        case 0x72: // Wait 3 sample
        case 0x73: // Wait 4 sample
        case 0x74: // Wait 5 sample
        case 0x75: // Wait 6 sample
        case 0x76: // Wait 7 sample
        case 0x77: // Wait 8 sample
        case 0x78: // Wait 9 sample
        case 0x79: // Wait 10 sample
        case 0x7a: // Wait 11 sample
        case 0x7b: // Wait 12 sample
        case 0x7c: // Wait 13 sample
        case 0x7d: // Wait 14 sample
        case 0x7e: // Wait 15 sample
        case 0x7f: // Wait 16 sample
            driverSeqCounter += cmd - 0x6f;
            vgmAdr++;
            break;
        case 0x80: // write adr2A and wait 0 sample
        case 0x81: // write adr2A and wait 1 sample
        case 0x82: // write adr2A and wait 2 sample
        case 0x83: // write adr2A and wait 3 sample
        case 0x84: // write adr2A and wait 4 sample
        case 0x85: // write adr2A and wait 5 sample
        case 0x86: // write adr2A and wait 6 sample
        case 0x87: // write adr2A and wait 7 sample
        case 0x88: // write adr2A and wait 8 sample
        case 0x89: // write adr2A and wait 9 sample
        case 0x8a: // write adr2A and wait 10 sample
        case 0x8b: // write adr2A and wait 11 sample
        case 0x8c: // write adr2A and wait 12 sample
        case 0x8d: // write adr2A and wait 13 sample
        case 0x8e: // write adr2A and wait 14 sample
        case 0x8f: // write adr2A and wait 15 sample
            mds.write(Ym2612Inst.class, 0, 0, 0x2a, vgmBuf[vgmPcmPtr++] & 0xff);
            driverSeqCounter += cmd - 0x80;
            vgmAdr++;
            break;
        case 0x90:
            vgmAdr++;
            si = vgmBuf[vgmAdr++] & 0xff;
            vgmStreams[si].chipId = vgmBuf[vgmAdr++] & 0xff;
            vgmStreams[si].port = vgmBuf[vgmAdr++] & 0xff;
            vgmStreams[si].cmd = vgmBuf[vgmAdr++] & 0xff;
            break;
        case 0x91:
            vgmAdr++;
            si = vgmBuf[vgmAdr++] & 0xff;
            vgmStreams[si].databankId = vgmBuf[vgmAdr++] & 0xff;
            vgmStreams[si].stepsize = vgmBuf[vgmAdr++] & 0xff;
            vgmStreams[si].stepbase = vgmBuf[vgmAdr++] & 0xff;
            break;
        case 0x92:
            vgmAdr++;
            si = vgmBuf[vgmAdr++] & 0xff;
            vgmStreams[si].frequency = ByteUtil.readLeInt(vgmBuf, vgmAdr);
            vgmAdr += 4;
            break;
        case 0x93:
            vgmAdr++;
            si = vgmBuf[vgmAdr++] & 0xff;
            vgmStreams[si].dataStartOffset = ByteUtil.readLeInt(vgmBuf, vgmAdr);
            vgmAdr += 4;
            vgmStreams[si].lengthMode = vgmBuf[vgmAdr++] & 0xff;
            vgmStreams[si].dataLength = ByteUtil.readLeInt(vgmBuf, vgmAdr);
            vgmAdr += 4;

            vgmStreams[si].sw = true;
            vgmStreams[si].wkDataAdr = vgmStreams[si].dataStartOffset;
            vgmStreams[si].wkDataLen = vgmStreams[si].dataLength;
            vgmStreams[si].wkDataStep = 1.0;

            break;
        case 0x94:
            vgmAdr++;
            si = vgmBuf[vgmAdr++] & 0xff;
            vgmStreams[si].sw = false;
            break;
        case 0x95:
            vgmAdr++;
            si = vgmBuf[vgmAdr++] & 0xff;
            vgmStreams[si].blockId = (int) ByteUtil.readLeShort(vgmBuf, vgmAdr) & 0xffff;
            vgmAdr += 2;
            p = vgmBuf[vgmAdr++] & 0xff;
            if ((p & 1) > 0) {
                vgmStreams[si].lengthMode |= 0x80;
            }
            if ((p & 16) > 0) {
                vgmStreams[si].lengthMode |= 0x10;
            }

            vgmStreams[si].sw = true;
            vgmStreams[si].wkDataAdr = vgmStreams[si].dataStartOffset;
            vgmStreams[si].wkDataLen = vgmStreams[si].dataLength;
            vgmStreams[si].wkDataStep = 1.0;

            break;
        case 0xa0: // AY8910
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            mds.write(Ay8910Inst.class, 0, 0, rAdr, rDat);

            break;
        case 0xb3: // GB DMG
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            //mds.write(DMGInst.class, 0, rAdr, rDat);
            break;
        case 0xb4: // NES
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            //mds.write(NESInst.class, 0, rAdr, rDat);
            break;
        case 0xb5: // MultiPCM
            rAdr = vgmBuf[vgmAdr + 1] & 0x7f;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            //mds.write(MultiPCMInst.class, 0, rAdr, rDat);
            break;
        case 0xb7:
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            mds.write(OkiM6258Inst.class, 0, 0, rAdr, rDat);
            break;
        case 0xb8:
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            //mds.write(OKIM6295Inst.class, 0, rAdr, rDat);
            break;
        case 0xb9: // OotakeHuC6280
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            //mds.write(HuC6280Inst.class, (byte) 0, rAdr, rDat);
            break;
        case 0xba: // K053260Inst
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            //mds.write(K053260Inst.class, (byte) 0, rAdr, rDat);

            break;
        case 0xbc: // WSwan
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            mds.write(WsAudioInst.class, 0, 0, rAdr, rDat);

            break;
        case 0xc6: // WSwan write memory
            int wsOfs = (vgmBuf[vgmAdr + 1] & 0xff) * 0x100 + (vgmBuf[vgmAdr + 2] & 0xff);
            rDat = vgmBuf[vgmAdr + 3] & 0xff;
            vgmAdr += 4;
            mds.writeWsAudioMem(0, wsOfs, rDat & 0xff);

            break;
        case 0xbf: // GA20
            rAdr = vgmBuf[vgmAdr + 1] & 0xff;
            rDat = vgmBuf[vgmAdr + 2] & 0xff;
            vgmAdr += 3;
            //mds.write(GA20Inst.class, 0, rAdr, rDat);

            break;
        case 0xc0: // segaPCM
            //mds.write(SEGAPCMInst.class, 0, (vgmBuf[vgmAdr + 0x01] & 0xff) | ((vgmBuf[vgmAdr + 0x02] & 0xff) << 8), vgmBuf[vgmAdr + 0x03] & 0xff);
            vgmAdr += 4;
            break;
        case 0xc3: // MultiPCM
            int multiPCM_ch = vgmBuf[vgmAdr + 1] & 0x7f;
            int multiPCM_adr = (vgmBuf[vgmAdr + 2] & 0xff) + (vgmBuf[vgmAdr + 3] & 0xff) * 0x100;
            vgmAdr += 4;
            //mds.write(MultiPCMSetBankInst.class, 0, multiPCM_ch, multiPCM_adr);
            break;
        case 0xc4: // QSoundInst
            mds.write(QSoundInst.class, 0, 0, 0x00, vgmBuf[vgmAdr + 1] & 0xff);
            mds.write(QSoundInst.class, 0, 0, 0x01, vgmBuf[vgmAdr + 2] & 0xff);
            mds.write(QSoundInst.class, 0, 0, 0x02, vgmBuf[vgmAdr + 3] & 0xff);
            //rDat = vgmBuf[vgmAdr + 3];
            //if (rsc == null) dataEnq(DriverSeqCounter, 0xc4, 0, vgmBuf[vgmAdr + 1] * 0x100 + vgmBuf[vgmAdr + 2], rDat);
            vgmAdr += 4;
            break;
        case 0xd0: // YMF278B
            int ymf278b_port = vgmBuf[vgmAdr + 1] & 0x7f;
            int ymf278b_offset = vgmBuf[vgmAdr + 2] & 0xff;
            rDat = vgmBuf[vgmAdr + 3];
            int ymf278b_chipId = (vgmBuf[vgmAdr + 1] & 0x80) != 0 ? 1 : 0;
            vgmAdr += 4;
            //mds.write(YMF278BInst.class, ymf278b_chipId, ymf278b_port, ymf278b_offset, rDat);
            break;
        case 0xd1: // YMF271
            int ymf271_port = vgmBuf[vgmAdr + 1] & 0x7f;
            int ymf271_offset = vgmBuf[vgmAdr + 2] & 0xff;
            rDat = vgmBuf[vgmAdr + 3] & 0xff;
            int ymf271_chipId = (vgmBuf[vgmAdr + 1] & 0x80) != 0 ? 1 : 0;
            vgmAdr += 4;
            //mds.write(YMF271Inst.class, ymf271_chipId, ymf271_port, ymf271_offset, rDat);
            break;
        case 0xd2: // SCC1(K051649Inst?)
            int scc1_port = vgmBuf[vgmAdr + 1] & 0x7f;
            int scc1_offset = vgmBuf[vgmAdr + 2] & 0xff;
            rDat = vgmBuf[vgmAdr + 3] & 0xff;
            int scc1_chipId = (vgmBuf[vgmAdr + 1] & 0x80) != 0 ? 1 : 0;
            vgmAdr += 4;
            //mds.write(K051649Inst.class, scc1_chipId, (scc1_port << 1) | 0x00, scc1_offset);
            //mds.write(K051649Inst.class, scc1_chipId, (scc1_port << 1) | 0x01, rDat);
            break;
        case 0xd3: // K054539Inst
            int k054539_adr = (vgmBuf[vgmAdr + 1] & 0x7f) * 0x100 + (vgmBuf[vgmAdr + 2] & 0xff);
            rDat = vgmBuf[vgmAdr + 3] & 0xff;
            int chipId = (vgmBuf[vgmAdr + 1] & 0x80) != 0 ? 1 : 0;
            vgmAdr += 4;
            //mds.write(K054539Inst.class, chipId, k054539_adr, rDat);
            break;
        case 0xd4: // C140Inst
            int c140_adr = (vgmBuf[vgmAdr + 1] & 0x7f) * 0x100 + (vgmBuf[vgmAdr + 2] & 0xff);
            rDat = vgmBuf[vgmAdr + 3] & 0xff;
            int c140_chipId = (vgmBuf[vgmAdr + 1] & 0x80) != 0 ? 1 : 0;
            vgmAdr += 4;
            //mds.write(C140Inst.class, c140_chipId, (int)c140_adr, rDat);
            break;
        case 0xe0: // seek to offset in PCM data bank
            vgmPcmPtr = ByteUtil.readLeInt(vgmBuf, vgmAdr + 1) + vgmPcmBaseAdr;
            vgmAdr += 5;
            break;
        case 0xe1: // C352Inst
            int adr = (vgmBuf[vgmAdr + 1] & 0xff) * 0x100 + (vgmBuf[vgmAdr + 2] & 0xff);
            int dat = (vgmBuf[vgmAdr + 3] & 0xff) * 0x100 + (vgmBuf[vgmAdr + 4] & 0xff);
            vgmAdr += 5;
            //mds.write(C352Inst.class, 0, adr, dat);

            break;
        default:
            // わからんコマンド
            Debug.printf(Level.WARNING, "%02x", vgmBuf[vgmAdr++]);
        }
    }

    /** @return nullable */
    private Integer getPCMAddressFromPCMBank(int chipType, int chipReadOffset) {
        if (chipType >= PCM_BANK_COUNT) return null;

        if (chipReadOffset >= pcmBanks[chipType].dataSize) return null;

        return chipReadOffset;
    }

    private void oneFrameVGMStream() {
        while (emuRecvBuffer.lookUpCounter() <= emuSeqCounter) {
            int[] packCounter_ = new int[1];
            int[] dev_ = new int[1];
            int[] typ_ = new int[1];
            int[] adr_ = new int[1];
            int[] val_ = new int[1];
            Object[][] ex_ = new Object[1][];
            boolean ret = emuRecvBuffer.deq(packCounter_, dev_, typ_, adr_, val_, ex_);
            packCounter = packCounter_[0];
            pack.dev = dev_[0];
            pack.typ = typ_[0];
            pack.adr = adr_[0];
            pack.val = val_[0];
            pack.ex = ex_[0];
            if (!ret) break;
            sendEmuData(packCounter, pack.dev, pack.typ, pack.adr, pack.val, pack.ex);
        }
        emuSeqCounter++;

        for (int i = 0; i < 0x100; i++) {

            if (!vgmStreams[i].sw) continue;
            if (vgmStreams[i].chipId != 0x02) continue; // とりあえずYM2612のみ

            while (vgmStreams[i].wkDataStep >= 1.0) {
                mds.write(Ym2612Inst.class, 0, vgmStreams[i].port, vgmStreams[i].cmd, vgmBuf[vgmPcmBaseAdr + vgmStreams[i].wkDataAdr] & 0xff);
                vgmStreams[i].wkDataAdr++;
                vgmStreams[i].dataLength--;
                vgmStreams[i].wkDataStep -= 1.0;
            }
            vgmStreams[i].wkDataStep += (double) vgmStreams[i].frequency / (double) SamplingRate;

            if (vgmStreams[i].dataLength <= 0) {
                vgmStreams[i].sw = false;
            }
        }
    }

    private void sendEmuData(int counter, int dev, int typ, int adr, int val, Object... ex) {
        switch (dev) {
        case 0x56:
            if (adr >= 0) {
                mds.write(Ym2609Inst.class, 0, adr >> 8, adr, val);
            } else {
                sm.setInterrupt();
                try {
                    Pack[] data = (Pack[]) ex;
                    for (Pack dat : data) {
                        mds.write(Ym2609Inst.class, 0, dat.adr >> 8, dat.adr, dat.val);
                    }
                } finally {
                    sm.resetInterrupt();
                }
            }
            break;
        }
    }
}
