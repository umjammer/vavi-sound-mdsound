
package test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import dotnet4j.io.File;
import mdsound.chips.C140;
import mdsound.instrument.*;
import mdsound.instrument.Ga20Inst;
import mdsound.MDSound;
import org.junit.jupiter.api.Test;
import test.SoundManager.DriverAction;
import test.SoundManager.Pack;
import test.SoundManager.RingBuffer;
import test.SoundManager.SoundManager;
import vavi.util.ByteUtil;
import vavi.util.Debug;


class Program {

    static String[] args;

    /**
     * アプリケーションのメイン エントリ ポイントです。
     */
    public static void main(String[] args) {
        Program.args = args;
        new FrmMain();
    }

    @Test
    void test1() throws Exception {
        Program.main(new String[0]);
        Thread.sleep(10000000);
    }

    @Test
    void test2() throws Exception {
        Program app = new Program();
        app.prePlay(System.getProperty("user.dir") + "/../MDPlayer/src/test/resources/samples/vgm/lemmings_012_tim7.vgm");
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

        public byte chipId;
        public byte port;
        public byte cmd;

        public byte databankId;
        public byte stepsize;
        public byte stepbase;

        public int frequency;

        public int dataStartOffset;
        public byte lengthMode;
        public int dataLength;

        public boolean sw;

        public int blockId;

        public int wkDataAdr;
        public int wkDataLen;
        public double wkDataStep;
    }

    private short[] emuRenderBuf = new short[2];
    private long packCounter = 0;
    private Pack pack = new Pack();

    long driverSeqCounter = 0;
    long emuSeqCounter = 0;

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

    test.SoundManager.SoundManager sm;
    private SoundManager.Enq enq;
    //    private static RealChip rc = null;
//    private static RSoundChip rsc = null;
    private RingBuffer emuRecvBuffer = null;

    /** */
    Program() {
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
        sm = new test.SoundManager.SoundManager();
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
//             DataEnq(DriverSeqCounter, 0x56, 0, -1, -1, data);
//         } else {
        data = softResetYM2608(-1);
        dataEnq(driverSeqCounter, -1, 0, -1, -1, data);
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
    public void dataEnq(long counter, int dev, int typ, int adr, int val, Object... ex) {
        while (!enq.apply(counter, dev, typ, adr, val, ex)) Thread.yield();
    }

    /** */
    private Pack[] softInitYM2608(int dev) {
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
    private Pack[] softResetYM2608(int dev) {
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
        for (int i = (byte) 0x80; i < 0x8F + 1; i++) {
            data.add(new Pack(dev, 0, i, 0xff));
            data.add(new Pack(dev, 0, 0x100 + i, 0xff));
        }
        // FM F-Num, FB/CONNECT
        for (int i = (byte) 0x90; i < 0xBF + 1; i++) {
            data.add(new Pack(dev, 0, i, 0x0));
            data.add(new Pack(dev, 0, 0x100 + i, 0x0));
        }
        // FM PAN/AMS/PMS
        for (int i = (byte) 0xB4; i < 0xB6 + 1; i++) {
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
            emuCallback();
            Thread.yield();
        }

//        if (rsc == null) {
//            sm.RequestStopAtRealChipSender();
//            while (sm.IsRunningAtRealChipSender()) ;
//        } else {
        sm.requestStopAtEmuChipSender();
        while (sm.isRunningAtEmuChipSender()) Thread.yield();
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

        for (int i = 0; i < pcmBanks.length; i++) {
            pcmBanks[i] = new VgmPcmBank();
        }

        // ヘッダーを読み込めるサイズをもっているかチェック
        if (vgmBuf.length < 0x40) return;

        // ヘッダーから情報取得

        int vgm = getLE32(0x00);
        if (vgm != 0x206d6756) return;

        int version = getLE32(0x08);
        //if (version < 0x0150) return;

        vgmEof = getLE32(0x04);

        int vgmDataOffset = getLE32(0x34);
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

        if (getLE32(0x0c) != 0) {
            chip = new MDSound.Chip();
            chip.id = 0;
            chip.instrument = new Sn76496Inst();
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x0c);
            chip.volume = 0;
            if (version < 0x0150) {
                chip.option = new Object[] {
                        (byte) 9,
                        (byte) 0,
                        (byte) 16,
                        (byte) 0
                };
            } else {
                chip.option = new Object[] {
                        vgmBuf[0x28],
                        vgmBuf[0x29],
                        vgmBuf[0x2a],
                        vgmBuf[0x2b]
                };
            }
            lstChip.add(chip);
        }

        if (getLE32(0x10) != 0) {
            chip = new MDSound.Chip();
            chip.id = 0;
            Ym2413Inst ym2413 = new Ym2413Inst();
            chip.instrument = ym2413;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x10);
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x2c) != 0) {
            chip = new MDSound.Chip();
            chip.id = 0;
            MameYm2612Inst ym2612 = new MameYm2612Inst();
            chip.instrument = ym2612;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x2c);
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x30) != 0) {
            chip = new MDSound.Chip();
            chip.id = 0;
            X68SoundYm2151Inst ym2151 = new X68SoundYm2151Inst();
            chip.instrument = ym2151;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x30);
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x38) != 0 && 0x38 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            SegaPcmInst segapcm = new SegaPcmInst();
            chip.instrument = segapcm;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x38);
            chip.option = new Object[] {(int) getLE32(0x3c)};
            chip.volume = 0;

            lstChip.add(chip);
        }

        if (getLE32(0x44) != 0 && 0x44 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            Ym2203Inst ym2203 = new Ym2203Inst();
            chip.instrument = ym2203;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x44);
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x48) != 0 && 0x48 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            Ym2608Inst ym2608 = new Ym2608Inst();
            chip.instrument = ym2608;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x48);
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }
        //if (GetLE32(0x48) != 0 && 0x48 < vgmDataOffset - 3) {
        //    chips = new MDSound.Chip();
        //    chips.ID = 0;
        //    mdsound.instrument.Ym2609Inst Ym2609Inst = new mdsound.instrument.Ym2609Inst();
        //    chips.Instrument = Ym2609Inst;
        //    chips.SamplingRate = SamplingRate;
        //    chips.Clock = GetLE32(0x48);
        //    chips.Volume = 0;
        //    chips.Option = null;
        //    lstChip.add(chips);
        //}

        if (getLE32(0x4c) != 0 && 0x4c < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            Ym2610Inst ym2610 = new Ym2610Inst();
            chip.instrument = ym2610;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x4c) & 0x7fffffff;
            chip.volume = 0;
            chip.option = null;
            bufYM2610AdpcmA = null;
            bufYM2610AdpcmB = null;
            lstChip.add(chip);
        }

        if (getLE32(0x50) != 0 && 0x50 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            Ym3812Inst ym3812 = new Ym3812Inst();
            chip.instrument = ym3812;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x50) & 0x7fffffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x54) != 0 && 0x54 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            Ym3526Inst ym3526 = new Ym3526Inst();
            chip.instrument = ym3526;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x54) & 0x7fffffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x5c) != 0 && 0x5c < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            YmF262Inst ymf262 = new YmF262Inst();
            chip.instrument = ymf262;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x5c) & 0x7fffffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);

            //chips = new MDSound.Chip();
            //chips.ID = 0;
            //chips.Instrument = YmF278bInst();
            //chips.SamplingRate = SamplingRate;
            //chips.Clock = getLE32(0x5c) & 0x7fffffff;
            //chips.Volume = 0;
            //chips.Option = null;
            //lstChip.add(chips);
        }

        if (getLE32(0x58) != 0 && 0x58 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            Y8950Inst y8950 = new Y8950Inst();
            chip.instrument = y8950;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x58) & 0x7fffffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x60) != 0 && 0x60 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            YmF278bInst ymf278b = new YmF278bInst();
            chip.instrument = ymf278b;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x60) & 0x7fffffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x64) != 0 && 0x64 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            YmF271Inst ymf271 = new YmF271Inst();
            chip.instrument = ymf271;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x64) & 0x7fffffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x68) != 0 && 0x68 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            YmZ280bInst ymz280b = new YmZ280bInst();
            chip.instrument = ymz280b;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x68) & 0x7fffffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x74) != 0 && 0x74 < vgmDataOffset - 3) {
            chip = new MDSound.Chip();
            chip.id = 0;
            MameAy8910Inst ay8910 = new MameAy8910Inst();
            chip.instrument = ay8910;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x74) & 0x7fffffff;
            chip.clock /= 2;
            if ((vgmBuf[0x79] & 0x10) != 0)
                chip.clock /= 2;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (version >= 0x0161 && 0x80 < vgmDataOffset - 3) {

            if (getLE32(0x80) != 0 && 0x80 < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                DmgInst gb = new DmgInst();
                chip.instrument = gb;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0x80);
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (getLE32(0x84) != 0 && 0x84 < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                IntFNesInst nes_intf = new IntFNesInst();
                chip.instrument = nes_intf;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0x84);
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (getLE32(0x88) != 0 && 0x88 < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                MultiPcmInst multipcm = new MultiPcmInst();
                chip.instrument = multipcm;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0x88) & 0x7fffffff;
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (getLE32(0x90) != 0 && 0x90 < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                OkiM6258Inst okim6258 = new OkiM6258Inst();
                chip.instrument = okim6258;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0x90) & 0xbfffffff;
                chip.volume = 0;
                chip.option = new Object[] {(int) vgmBuf[0x94]};
                okim6258.okim6258_set_srchg_cb((byte) 0, Program::changeChipSampleRate, chip);
                lstChip.add(chip);
            }

            if (getLE32(0x98) != 0 && 0x98 < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                OkiM6295Inst okim6295 = new OkiM6295Inst();
                chip.instrument = okim6295;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0x98) & 0xbfffffff;
                chip.volume = 0;
                chip.option = null;
                okim6295.okim6295_set_srchg_cb((byte) 0, Program::changeChipSampleRate, chip);
                lstChip.add(chip);
            }

            if (getLE32(0x9c) != 0 && 0x9c < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                K051649Inst k051649 = new K051649Inst();
                chip.instrument = k051649;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0x9c);
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (getLE32(0xa0) != 0 && 0xa0 < vgmDataOffset - 3) {
                K054539Inst k054539 = new K054539Inst();
                int max = (getLE32(0xa0) & 0x40000000) != 0 ? 2 : 1;
                for (int i = 0; i < max; i++) {
                    chip = new MDSound.Chip();
                    chip.id = (byte) i;
                    chip.instrument = k054539;
                    chip.samplingRate = SamplingRate;
                    chip.clock = getLE32(0xa0) & 0x3fffffff;
                    chip.volume = 0;
                    chip.option = new Object[] {vgmBuf[0x95]};

                    lstChip.add(chip);
                }
            }

            if (getLE32(0xa4) != 0 && 0xa4 < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                HuC6280Inst huc8910 = new HuC6280Inst();
                chip.instrument = huc8910;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0xa4);
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (getLE32(0xa8) != 0 && 0xa8 < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                C140Inst c140 = new C140Inst();
                chip.instrument = c140;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0xa8);
                chip.volume = 0;
                chip.option = new Object[] {C140.Type.valueOf(vgmBuf[0x96] & 0xff)};
                lstChip.add(chip);
            }

            if (getLE32(0xac) != 0 && 0xac < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                K053260Inst k053260 = new K053260Inst();
                chip.instrument = k053260;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0xac);
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (getLE32(0xb4) != 0 && 0xb4 < vgmDataOffset - 3) {
                chip = new MDSound.Chip();
                chip.id = 0;
                CtrQSoundInst qsound = new CtrQSoundInst();
                chip.instrument = qsound;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0xb4);
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (version >= 0x170 && 0xdc < vgmDataOffset - 3) {
                if (version >= 0x171) {
                    if (getLE32(0xc0) != 0 && 0xc0 < vgmDataOffset - 3) {
                        chip = new MDSound.Chip();
                        chip.id = 0;
                        WsAudioInst wswan = new WsAudioInst();
                        chip.instrument = wswan;
                        chip.samplingRate = SamplingRate;
                        chip.clock = getLE32(0xc0);
                        chip.volume = 0;
                        chip.option = null;

                        lstChip.add(chip);
                    }

                    if (getLE32(0xdc) != 0 && 0xdc < vgmDataOffset - 3) {
                        chip = new MDSound.Chip();
                        chip.id = 0;
                        C352Inst c352 = new C352Inst();
                        chip.instrument = c352;
                        chip.samplingRate = SamplingRate;
                        chip.clock = getLE32(0xdc);
                        chip.volume = 0;
                        chip.option = new Object[] {vgmBuf[0xd6]};

                        lstChip.add(chip);
                    }

                    if (getLE32(0xe0) != 0 && 0xe0 < vgmDataOffset - 3) {
                        chip = new MDSound.Chip();
                        chip.id = 0;
                        Ga20Inst ga20 = new Ga20Inst();
                        chip.instrument = ga20;
                        chip.samplingRate = SamplingRate;
                        chip.clock = getLE32(0xe0);
                        chip.volume = 0;
                        chip.option = null;

                        lstChip.add(chip);
                    }
                }
            }
        }

        chips = lstChip.toArray(MDSound.Chip[]::new);
        mds.init(SamplingRate, SamplingBuffer, chips);
    }

    public static void changeChipSampleRate(MDSound.Chip chip, int NewSmplRate) {
        MDSound.Chip caa = chip;

        if (caa.samplingRate == NewSmplRate)
            return;

        // quick and dirty hack to make sample rate changes work
        caa.samplingRate = NewSmplRate;
        if (caa.samplingRate < SamplingRate)
            caa.resampler = 0x01;
        else if (caa.samplingRate == SamplingRate)
            caa.resampler = 0x02;
        else if (caa.samplingRate > SamplingRate)
            caa.resampler = 0x03;
        caa.smpP = 1;
        caa.smpNext -= caa.smpLast;
        caa.smpLast = 0x00;
    }

    static int dummy = 0;

    private void emuCallback() {
        int len = 512;
        byte[] frames = new byte[len];
        long bufCnt = len / 4;
        long seqCnt = sm.getSeqCounter();
        emuSeqCounter = seqCnt - bufCnt;
        emuSeqCounter = Math.max(emuSeqCounter, 0);

        for (int i = 0; i < bufCnt; i++) {
            mds.update(emuRenderBuf, 0, 2, this::oneFrameVGMaaa);

            ByteUtil.writeLeShort(emuRenderBuf[0], frames, i * 4 + 0);
            ByteUtil.writeLeShort(emuRenderBuf[1], frames, i * 4 + 2);
            //Debug.print("adr[%8x] : Wait[%8d] : [%8d]/[%8d]\r\n", vgmAdr,0,0,0);
            //dummy++;
            //dummy %= 500;
            //frames[i * 2 + 0] = (short)dummy;// (dummy < 100 ? 0xfff : 0x000);
        }

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
            return;
        }

        byte p = 0;
        byte si = 0;
        byte rAdr = 0;
        byte rDat = 0;

        if (vgmAdr == vgmBuf.length || vgmAdr == vgmEof) {
            vgmAnalyze = false;
            sm.requestStopAtDataMaker();
            return;
        }

        byte cmd = vgmBuf[vgmAdr];
        //Debug.print(" adr[%x]:cmd[%x]\r\n", vgmAdr, cmd);
        switch (cmd & 0xff) {
        case 0x4f: // GG Psg
        case 0x50: // Psg
            mds.writeSn76489((byte) 0, vgmBuf[vgmAdr + 1]);
            //mds.writeSN76496(0, vgmBuf[vgmAdr + 1]);
            vgmAdr += 2;
            break;
        case 0x51: // YM2413
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.writeYM2413(0, rAdr, rDat);
            break;
        case 0x52: // Ym2612Inst Port0
        case 0x53: // Ym2612Inst Port1
            p = (byte) ((cmd == 0x52) ? 0 : 1);
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            mds.writeYm2612((byte) 0, p, rAdr, rDat);

            break;
        case 0x54: // YM2151
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //Debug.print(" adr[%x]:cmd[%x]:adr[%x]:Dar[%x]\r\n", vgmAdr, cmd,rAdr,rDat);
            mds.writeYm2151((byte) 0, rAdr, rDat);
            break;
        case 0x55: // YM2203
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.writeY
            // 2203(0, rAdr, rDat);

            break;
        //case 0x56: // YM2608 Port0
        //    rAdr = vgmBuf[vgmAdr + 1];
        //    rDat = vgmBuf[vgmAdr + 2];
        //    vgmAdr += 3;
        //    mds.writeYm2608(0, 0, rAdr, rDat);

        //    break;
        //case 0x57: // YM2608 Port1
        //    rAdr = vgmBuf[vgmAdr + 1];
        //    rDat = vgmBuf[vgmAdr + 2];
        //    vgmAdr += 3;
        //    mds.writeYm2608(0, 1, rAdr, rDat);

        //    break;
        case 0x56: // YM2609 Port0
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
//            if (rsc == null) DataEnq(DriverSeqCounter, 0x56, 0, 0 * 0x100 + rAdr, rDat);
//            else
            dataEnq(driverSeqCounter, -1, 0, 0 * 0x100 + rAdr, rDat);
            //mds.writeYm2609(0, 0, rAdr, rDat);
            break;
        case 0x57: // YM2609 Port1
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
//            if (rsc == null) DataEnq(DriverSeqCounter, 0x56, 0, 1 * 0x100 + rAdr, rDat);
//            else
            dataEnq(driverSeqCounter, -1, 0, 1 * 0x100 + rAdr, rDat);
            //mds.writeYM2609(0, 1, rAdr, rDat);

            break;
        case 0x58: // YM2610 Port0
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.writeYM2610(0, 0, rAdr, rDat);

            break;
        case 0x59: // YM2610 Port1
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.writeYM2610(0, 1, rAdr, rDat);

            break;
        case 0x5a: // YM3812
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.writeYM3812(0, rAdr, rDat);

            break;
        case 0x5b: // YM3526
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.writeYM3526(0, rAdr, rDat);

            break;
        case 0x5c: // Y8950Inst
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.writeY8950(0, rAdr, rDat);

            break;
        case 0x5D: // YMZ280B
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            // mds.writeYMZ280B(0, rAdr, rDat);

            break;
        case 0x5e: // YMF262 Port0
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;

            mds.writeYmF262((byte) 0, (byte) 0, rAdr, rDat);
            //mds.writeYMF278B(0, 0, rAdr, rDat);
            //Debug.printf("P0:adr%2x:dat%2x", rAdr, rDat);
            break;
        case 0x5f: // YMF262 Port1
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;

            mds.writeYmF262((byte) 0, (byte) 1, rAdr, rDat);
            //mds.writeYMF278B(0, 1, rAdr, rDat);
            //Debug.printf("P1:adr%2x:dat%2x", rAdr, rDat);

            break;
        case 0x61: // Wait n samples
            vgmAdr++;
            driverSeqCounter += getLE16(vgmAdr);
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
            byte bType = vgmBuf[vgmAdr + 2];
            int bLen = getLE32(vgmAdr + 3);
            //byte chipID = 0;
            if ((bLen & 0x80000000) != 0) {
                bLen &= 0x7fffffff;
                //chipID = 1;
            }

            switch (bType & 0xc0) {
            case 0x00:
            case 0x40:
                //addPCMData(bType, bLen, bAdr);
                vgmAdr += bLen + 7;
                break;
            case 0x80:
                int romSize = getLE32(vgmAdr + 7);
                int startAddress = getLE32(vgmAdr + 0x0B);
                switch (bType & 0xff) {
                case 0x80:
                    // SEGA PCM
                    //mds.writeSEGAPCMPCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
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

                            new Pack(0, 0, 0x100 + 0x02, (byte) (startAddress >> 2)),
                            new Pack(0, 0, 0x100 + 0x03, (byte) (startAddress >> 10)),
                            new Pack(0, 0, 0x100 + 0x04, 0xff),
                            new Pack(0, 0, 0x100 + 0x05, 0xff),
                            new Pack(0, 0, 0x100 + 0x0c, 0xff),
                            new Pack(0, 0, 0x100 + 0x0d, 0xff)
                    );

                    // データ転送
                    for (int cnt = 0; cnt < bLen - 8; cnt++) {
                        data.add(new Pack(0, 0, 0x100 + 0x08, vgmBuf[vgmAdr + 15 + cnt]));
                    }
                    data.add(new Pack(0, 0, 0x100 + 0x00, 0x00));
                    data.add(new Pack(0, 0, 0x100 + 0x10, 0x80));

//                    if (rsc == null) DataEnq(DriverSeqCounter, 0x56, 0, -1, -1, data.toArray());
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
                    //mds.writeYM2610_SetAdpcmA(0, bufYM2610AdpcmA);
                    break;
                case 0x83:
                    if (bufYM2610AdpcmB == null || bufYM2610AdpcmB.length != romSize)
                        bufYM2610AdpcmB = new byte[romSize];
                    for (int cnt = 0; cnt < bLen - 8; cnt++) {
                        bufYM2610AdpcmB[startAddress + cnt] = vgmBuf[vgmAdr + 15 + cnt];
                    }
                    //mds.writeYM2610_SetAdpcmB(0, bufYM2610AdpcmB);
                    break;

                case 0x84:
                    //mds.writeYMF278BPCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x85:
                    //mds.writeYMF271PCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x86:
                    //mds.writeYMZ280BPCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x88:
                    //mds.writeY8950PCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x89:
                    //mds.writeMultiPCMPCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x8b:
                    //mds.writeOKIM6295PCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x8c:
                    //mds.writeK054539PCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x8d:
                    //mds.writeC140PCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x8e:
                    //mds.writeK053260PCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x8f:
                    mds.writeQSoundPCMData((byte) 0, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x92:
                    //mds.writeC352PCMData(0, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x93:
                    //mds.writeGA20PCMData(0, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
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
            byte chipType = vgmBuf[vgmAdr + 2];
            int chipReadOffset = getLE24(vgmAdr + 3);
            int chipWriteOffset = getLE24(vgmAdr + 6);
            int chipDataSize = getLE24(vgmAdr + 9);
            if (chipDataSize == 0) chipDataSize = 0x1000000;
            Integer pcmAdr = getPCMAddressFromPCMBank(chipType, chipReadOffset);
            if (pcmAdr != null && chipType == 0x01) {
                //mds.writeRF5C68PCMData(0, chipWriteOffset, chipDataSize, pcmBanks[chipType].data, (int)pcmAdr);
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
            mds.writeYm2612((byte) 0, (byte) 0, (byte) 0x2a, vgmBuf[vgmPcmPtr++]);
            driverSeqCounter += cmd - 0x80;
            vgmAdr++;
            break;
        case 0x90:
            vgmAdr++;
            si = vgmBuf[vgmAdr++];
            vgmStreams[si].chipId = vgmBuf[vgmAdr++];
            vgmStreams[si].port = vgmBuf[vgmAdr++];
            vgmStreams[si].cmd = vgmBuf[vgmAdr++];
            break;
        case 0x91:
            vgmAdr++;
            si = vgmBuf[vgmAdr++];
            vgmStreams[si].databankId = vgmBuf[vgmAdr++];
            vgmStreams[si].stepsize = vgmBuf[vgmAdr++];
            vgmStreams[si].stepbase = vgmBuf[vgmAdr++];
            break;
        case 0x92:
            vgmAdr++;
            si = vgmBuf[vgmAdr++];
            vgmStreams[si].frequency = getLE32(vgmAdr);
            vgmAdr += 4;
            break;
        case 0x93:
            vgmAdr++;
            si = vgmBuf[vgmAdr++];
            vgmStreams[si].dataStartOffset = getLE32(vgmAdr);
            vgmAdr += 4;
            vgmStreams[si].lengthMode = vgmBuf[vgmAdr++];
            vgmStreams[si].dataLength = getLE32(vgmAdr);
            vgmAdr += 4;

            vgmStreams[si].sw = true;
            vgmStreams[si].wkDataAdr = vgmStreams[si].dataStartOffset;
            vgmStreams[si].wkDataLen = vgmStreams[si].dataLength;
            vgmStreams[si].wkDataStep = 1.0;

            break;
        case 0x94:
            vgmAdr++;
            si = vgmBuf[vgmAdr++];
            vgmStreams[si].sw = false;
            break;
        case 0x95:
            vgmAdr++;
            si = vgmBuf[vgmAdr++];
            vgmStreams[si].blockId = getLE16(vgmAdr);
            vgmAdr += 2;
            p = vgmBuf[vgmAdr++];
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
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            mds.writeAY8910((byte) 0, rAdr, rDat);

            break;
        case 0xb3: // GB DMG
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteDMG((byte) 0, rAdr, rDat);
            break;
        case 0xb4: // NES
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteNES((byte) 0, rAdr, rDat);
            break;
        case 0xb5: // MultiPCM
            rAdr = (byte) (vgmBuf[vgmAdr + 1] & 0x7f);
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteMultiPCM((byte) 0, rAdr, rDat);
            break;
        case 0xb7:
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            mds.writeOkiM6258((byte) 0, rAdr, rDat);
            break;
        case 0xb8:
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteOKIM6295(0, rAdr, rDat);
            break;
        case 0xb9: // OotakeHuC6280
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteHuC6280((byte) 0, rAdr, rDat);
            break;
        case 0xba: // K053260Inst
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteK053260((byte) 0, rAdr, rDat);

            break;
        case 0xbc: // WSwan
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            mds.writeWsAudio((byte) 0, rAdr, rDat);

            break;
        case 0xc6: // WSwan write memory
            int wsOfs = vgmBuf[vgmAdr + 1] * 0x100 + vgmBuf[vgmAdr + 2];
            rDat = vgmBuf[vgmAdr + 3];
            vgmAdr += 4;
            mds.writeWsAudioMem((byte) 0, wsOfs, rDat);

            break;
        case 0xbf: // GA20
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteGA20(0, rAdr, rDat);

            break;
        case 0xc0: // segaPCM
            //mds.WriteSEGAPCM(0, (int)((vgmBuf[vgmAdr + 0x01] & 0xFF) | ((vgmBuf[vgmAdr + 0x02] & 0xFF) << 8)), vgmBuf[vgmAdr + 0x03]);
            vgmAdr += 4;
            break;
        case 0xc3: // MultiPCM
            byte multiPCM_ch = (byte) (vgmBuf[vgmAdr + 1] & 0x7f);
            int multiPCM_adr = vgmBuf[vgmAdr + 2] + vgmBuf[vgmAdr + 3] * 0x100;
            vgmAdr += 4;
            //mds.WriteMultiPCMSetBank(0, multiPCM_ch, multiPCM_adr);
            break;
        case 0xc4: // QSoundInst
            mds.writeQSound((byte) 0, 0x00, vgmBuf[vgmAdr + 1]);
            mds.writeQSound((byte) 0, 0x01, vgmBuf[vgmAdr + 2]);
            mds.writeQSound((byte) 0, 0x02, vgmBuf[vgmAdr + 3]);
            //rDat = vgmBuf[vgmAdr + 3];
            //if (rsc == null) DataEnq(DriverSeqCounter, 0xc4, 0, vgmBuf[vgmAdr + 1] * 0x100 + vgmBuf[vgmAdr + 2], rDat);
            vgmAdr += 4;
            break;
        case 0xd0: // YMF278B
            byte ymf278b_port = (byte) (vgmBuf[vgmAdr + 1] & 0x7f);
            byte ymf278b_offset = vgmBuf[vgmAdr + 2];
            rDat = vgmBuf[vgmAdr + 3];
            byte ymf278b_chipid = (byte) ((vgmBuf[vgmAdr + 1] & 0x80) != 0 ? 1 : 0);
            vgmAdr += 4;
            //mds.writeYMF278B(ymf278b_chipid, ymf278b_port, ymf278b_offset, rDat);
            break;
        case 0xd1: // YMF271
            byte ymf271_port = (byte) (vgmBuf[vgmAdr + 1] & 0x7f);
            byte ymf271_offset = vgmBuf[vgmAdr + 2];
            rDat = vgmBuf[vgmAdr + 3];
            byte ymf271_chipid = (byte) ((vgmBuf[vgmAdr + 1] & 0x80) != 0 ? 1 : 0);
            vgmAdr += 4;
            //mds.writeYMF271(ymf271_chipid, ymf271_port, ymf271_offset, rDat);
            break;
        case 0xd2: // SCC1(K051649Inst?)
            int scc1_port = vgmBuf[vgmAdr + 1] & 0x7f;
            byte scc1_offset = vgmBuf[vgmAdr + 2];
            rDat = vgmBuf[vgmAdr + 3];
            byte scc1_chipid = (byte) ((vgmBuf[vgmAdr + 1] & 0x80) != 0 ? 1 : 0);
            vgmAdr += 4;
            //mds.writeK051649(scc1_chipid, (scc1_port << 1) | 0x00, scc1_offset);
            //mds.writeK051649(scc1_chipid, (scc1_port << 1) | 0x01, rDat);
            break;
        case 0xd3: // K054539Inst
            int k054539_adr = (vgmBuf[vgmAdr + 1] & 0x7f) * 0x100 + vgmBuf[vgmAdr + 2];
            rDat = vgmBuf[vgmAdr + 3];
            byte chipid = (byte) ((vgmBuf[vgmAdr + 1] & 0x80) != 0 ? 1 : 0);
            vgmAdr += 4;
            //mds.writeK054539(chipid, k054539_adr, rDat);
            break;
        case 0xd4: // C140Inst
            int c140_adr = (vgmBuf[vgmAdr + 1] & 0x7f) * 0x100 + vgmBuf[vgmAdr + 2];
            rDat = vgmBuf[vgmAdr + 3];
            byte c140_chipid = (byte) ((vgmBuf[vgmAdr + 1] & 0x80) != 0 ? 1 : 0);
            vgmAdr += 4;
            //mds.writeC140(c140_chipid, (int)c140_adr, rDat);
            break;
        case 0xe0: // seek to offset in PCM data bank
            vgmPcmPtr = getLE32(vgmAdr + 1) + vgmPcmBaseAdr;
            vgmAdr += 5;
            break;
        case 0xe1: // C352Inst
            int adr = (vgmBuf[vgmAdr + 1] & 0xff) * 0x100 + (vgmBuf[vgmAdr + 2] & 0xff);
            int dat = (vgmBuf[vgmAdr + 3] & 0xff) * 0x100 + (vgmBuf[vgmAdr + 4] & 0xff);
            vgmAdr += 5;
            //mds.writeC352(0, adr, dat);

            break;
        default:
            // わからんコマンド
            Debug.printf(Level.WARNING, "%02x", vgmBuf[vgmAdr++]);
        }
    }

    private Integer getPCMAddressFromPCMBank(byte chipType, int chipReadOffset) {
        if (chipType >= PCM_BANK_COUNT)
            return null;

        if (chipReadOffset >= pcmBanks[chipType].dataSize)
            return null;

        return chipReadOffset;
    }

    private void oneFrameVGMStream() {
        while (emuRecvBuffer.lookUpCounter() <= emuSeqCounter) {
            boolean ret = emuRecvBuffer.deq(packCounter, pack.dev, pack.typ, pack.adr, pack.val, pack.ex);
            if (!ret) break;
            sendEmuData(packCounter, pack.dev, pack.typ, pack.adr, pack.val, pack.ex);
        }
        emuSeqCounter++;

        for (int i = 0; i < 0x100; i++) {

            if (!vgmStreams[i].sw) continue;
            if (vgmStreams[i].chipId != 0x02) continue; // とりあえずYM2612のみ

            while (vgmStreams[i].wkDataStep >= 1.0) {
                mds.writeYm2612((byte) 0, vgmStreams[i].port, vgmStreams[i].cmd, vgmBuf[vgmPcmBaseAdr + vgmStreams[i].wkDataAdr]);
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

    private void sendEmuData(long counter, int dev, int typ, int adr, int val, Object... ex) {
        switch (dev) {
        case 0x56:
            if (adr >= 0) {
                mds.writeYm2609((byte) 0, (byte) (adr >> 8), (byte) adr, (byte) val);
            } else {
                sm.setInterrupt();
                try {
                    Pack[] data = (Pack[]) ex;
                    for (Pack dat : data) {
                        mds.writeYm2609((byte) 0, (byte) (dat.adr >> 8), (byte) dat.adr, (byte) dat.val);
                    }
                } finally {
                    sm.resetInterrupt();
                }
            }
            break;
        }
    }

    @Deprecated
    private int getLE16(int adr) {
        int dat;
        dat = (int) vgmBuf[adr] + (int) vgmBuf[adr + 1] * 0x100;

        return dat;
    }

    @Deprecated
    private int getLE24(int adr) {
        int dat;
        dat = (int) vgmBuf[adr] + (int) vgmBuf[adr + 1] * 0x100 + (int) vgmBuf[adr + 2] * 0x10000;

        return dat;
    }

    @Deprecated
    private int getLE32(int adr) {
        int dat;
        dat = (int) vgmBuf[adr] + (int) vgmBuf[adr + 1] * 0x100 + (int) vgmBuf[adr + 2] * 0x10000 + (int) vgmBuf[adr + 3] * 0x1000000;

        return dat;
    }
}
