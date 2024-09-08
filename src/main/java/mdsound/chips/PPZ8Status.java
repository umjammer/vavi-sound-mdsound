package mdsound.chips;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;

import static dotnet4j.util.compat.CollectionUtilities.toByteArray;
import static java.lang.System.getLogger;


/**
 * PPZ8Status.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2022-07-08 nsano initial version <br>
 */
public class PPZ8Status {

    private static final Logger logger = getLogger(PPZ8Status.class.getName());

    private byte[][] pcmData = new byte[2][];
    private boolean[] isPVI = new boolean[2];
    private int bank = 0;
    private int ptr = 0;
    private boolean interrupt = false;
    private int adpcmEmu;
    private short[][] volumeTable = new short[][] {
            new short[256], new short[256], new short[256], new short[256],
            new short[256], new short[256], new short[256], new short[256],
            new short[256], new short[256], new short[256], new short[256],
            new short[256], new short[256], new short[256], new short[256]
    };
    private double samplingRate = 44100.0;
    private int PCM_VOLUME;
    private int volume;

    public static class Channel {
        public int loopStartOffset;
        public int loopEndOffset;
        public boolean playing;
        public int pan;
        private double panL;
        private double panR;
        public int srcFrequency;
        public int volume;
        public int frequency;
        public boolean KeyOn;

        private int _loopStartOffset;
        private int _loopEndOffset;
        //private int _frequency;
        private int _srcFrequency;

        public int bank;
        public int ptr;
        public int end;
        private double delta;
        public int num;

        private void init() {
            this.srcFrequency = 16000;
            this.pan = 5;
            this.panL = 1.0;
            this.panR = 1.0;
            this.volume = 8;
            //this._frequency = 0;
            this._loopStartOffset = -1;
            this._loopEndOffset = -1;
        }
    }

    private Channel[] chWk = new Channel[] {
            new Channel(), new Channel(), new Channel(), new Channel(),
            new Channel(), new Channel(), new Channel(), new Channel()
    };
    private Channel[] chWkBk = new Channel[] {
            new Channel(), new Channel(), new Channel(), new Channel(),
            new Channel(), new Channel(), new Channel(), new Channel()
    };

    private static int checkPZI(byte[] pcmData) {
        if (pcmData == null)
            return 5;
        if (!(pcmData[0] == 'P' && pcmData[1] == 'Z' && pcmData[2] == 'I'))
            return 2;

        return 0;
    }

    private static int checkPVI(byte[] pcmData) {
        if (pcmData == null)
            return 5;
        if (!(pcmData[0] == 'P' && pcmData[1] == 'V' && pcmData[2] == 'I'))
            return 2;

        return 0;
    }

    /**
     * 0x00 初期化
     */
    public void init() {
        bank = 0;
        ptr = 0;
        interrupt = false;
        for (int i = 0; i < 8; i++) {
            chWk[i].init();
        }
        PCM_VOLUME = 0;
        volume = 0;
        setAllVolume(12);
    }

    public void makeVolumeTable(int vol) {

        volume = vol;
        int aVolume = (int) (0x1000 * Math.pow(10.0, vol / 40.0));

        for (int i = 0; i < 16; i++) {
            double temp = Math.pow(2.0, (i + PCM_VOLUME) / 2.0) * aVolume / 0x18000;
            for (int j = 0; j < 256; j++) {
                volumeTable[i][j] = (short) (Math.max(Math.min((j - 128) * temp, Short.MAX_VALUE), Short.MIN_VALUE));
            }
        }
    }

    /**
     * 0x01 PCM発音
     *
     * @param al PCMチャンネル(0-7)
     * @param dx PCMの音色番号
     */
    public void playPCM(int al, int dx) {
        logger.log(Level.TRACE, String.format("ppz8em: PlayPCM: ch:%d @:%d", al, dx));

        int bank = (dx & 0x8000) != 0 ? 1 : 0;
        int num = dx & 0x7fff;
        chWk[al].bank = bank;
        chWk[al].num = num;

        if (pcmData[bank] != null) {
            chWk[al].ptr = (pcmData[bank][num * 0x12 + 32] & 0xff)
                    + (pcmData[bank][num * 0x12 + 1 + 32] & 0xff) * 0x100
                    + (pcmData[bank][num * 0x12 + 2 + 32] & 0xff) * 0x1_0000
                    + (pcmData[bank][num * 0x12 + 3 + 32] & 0xff) * 0x10_00000
                    + 0x20 + 0x12 * 128;
            if (chWk[al].ptr >= pcmData[bank].length) {
                chWk[al].ptr = pcmData[bank].length - 1;
            }
            chWk[al].end = chWk[al].ptr
                    + (pcmData[bank][num * 0x12 + 4 + 32] & 0xff)
                    + (pcmData[bank][num * 0x12 + 5 + 32] & 0xff) * 0x100
                    + (pcmData[bank][num * 0x12 + 6 + 32] & 0xff) * 0x1_0000
                    + (pcmData[bank][num * 0x12 + 7 + 32] & 0xff) * 0x10_00000
            ;
            if (chWk[al].end >= pcmData[bank].length) {
                chWk[al].end = pcmData[bank].length - 1;
            }


            chWk[al].loopStartOffset = chWk[al]._loopStartOffset;
            if (chWk[al]._loopStartOffset == -1) {
                chWk[al].loopStartOffset = 0
                        + (pcmData[bank][num * 0x12 + 8 + 32] & 0xff)
                        + (pcmData[bank][num * 0x12 + 9 + 32] & 0xff) * 0x100
                        + (pcmData[bank][num * 0x12 + 10 + 32] & 0xff) * 0x10_000
                        + (pcmData[bank][num * 0x12 + 11 + 32] & 0xff) * 0x100_0000;
            }
            chWk[al].loopEndOffset = chWk[al]._loopEndOffset;
            if (chWk[al]._loopEndOffset == -1) {
                chWk[al].loopEndOffset = 0
                        + (pcmData[bank][num * 0x12 + 12 + 32] & 0xff)
                        + (pcmData[bank][num * 0x12 + 13 + 32] & 0xff) * 0x100
                        + (pcmData[bank][num * 0x12 + 14 + 32] & 0xff) * 0x10_000
                        + (pcmData[bank][num * 0x12 + 15 + 32] & 0xff) * 0x100_0000;
            }
            if (chWk[al].loopStartOffset == 0xffff && chWk[al].loopEndOffset == 0xffff) {
                chWk[al].loopStartOffset = -1;
                chWk[al].loopEndOffset = -1;
            }

            chWk[al].srcFrequency = chWk[al]._srcFrequency;
        }

        interrupt = false;
        chWk[al].playing = true;
        chWk[al].KeyOn = true;
    }

    /**
     * 0x02 PCM停止
     *
     * @param al PCMチャンネル(0-7)
     */
    public void stopPCM(int al) {
        logger.log(Level.TRACE, String.format("ppz8em: StopPCM: ch:%d", al));
        chWk[al].playing = false;
    }

    /**
     * 0x03 PVIファイルの読み込み＆PCMへの変換
     *
     * @param bank    0:PCMバッファ0  1:PCMバッファ1
     * @param mode    0:.PVI (ADPCM)  1:.PZI(PCM)
     * @param pcmData ファイル内容
     */
    public int loadPcm(int bank, int mode, byte[][] pcmData) {
        logger.log(Level.TRACE, String.format("ppz8em: LoadPCM: bank:%d mode:%d", bank, mode));

        bank &= 1;
        mode &= 1;
        int ret;
        this.pcmData = pcmData;

        if (mode == 0) // PVI形式
            ret = checkPVI(pcmData[bank]);
        else // PZI形式
            ret = checkPZI(pcmData[bank]);

        if (ret == 0) {
            //this.pcmData[bank] = new byte[pcmData[bank].length];
            //Arrays.fill(pcmData, this.pcmData[bank], pcmData[bank].length);
            isPVI[bank] = mode == 0;
            if (isPVI[bank]) {
                ret = convertPviAdpcmToPziPcm(bank);
            }
        }

        return ret;
    }

    /**
     * 0x04 ステータスの読み込み
     *
     * @param al
     */
    public void readStatus(int al) {
        switch (al) {
        case 0xd:
            logger.log(Level.TRACE, "ppz8em: ReadStatus: PCM0のテーブルアドレス");
            bank = 0;
            ptr = 0;
            break;
        case 0xe:
            logger.log(Level.TRACE, "ppz8em: ReadStatus: PCM1のテーブルアドレス");
            bank = 1;
            ptr = 0;
            break;
        }
    }

    /**
     * 0x07 ボリュームの変更
     *
     * @param al PCMチャネル(0~7)
     * @param dx ボリューム(0-15 / 0-255)
     */
    public void setVolume(int al, int dx) {
        logger.log(Level.TRACE, String.format("ppz8em: SetVolume: Ch:%d vol:%d", al, dx));
        chWk[al].volume = dx;
    }

    /**
     * 0x0B PCMの音程周波数の指定
     *
     * @param al PCMチャネル(0~7)
     * @param dx PCMの音程周波数DX
     * @param cx PCMの音程周波数CX
     */
    public void setFrequency(int al, int dx, int cx) {
        logger.log(Level.TRACE, String.format("ppz8em: SetFrequency: 0x%8x", dx * 0x10000 + cx));
        chWk[al].frequency = dx * 0x10000 + cx;
    }

    /**
     * 0x0e ループポインタの設定
     *
     * @param al        PCMチャネル(0~7)
     * @param lpStOfsDX ループ開始オフセットDX
     * @param lpStOfsCX ループ開始オフセットCX
     * @param lpEdOfsDI ループ終了オフセットDI
     * @param lpEdOfsSI ループ終了オフセットSI
     */
    public void setLoopPoint(int al, int lpStOfsDX, int lpStOfsCX, int lpEdOfsDI, int lpEdOfsSI) {
        logger.log(Level.TRACE, String.format("ppz8em: SetLoopPoint: St:0x%8x Ed:0x%8x"
                , lpStOfsDX * 0x10000 + lpStOfsCX, lpEdOfsDI * 0x10000 + lpEdOfsSI));
        al &= 7;
        chWk[al]._loopStartOffset = lpStOfsDX * 0x10000 + lpStOfsCX;
        chWk[al]._loopEndOffset = lpEdOfsDI * 0x10000 + lpEdOfsSI;

        if (chWk[al]._loopStartOffset == 0xffff || chWk[al]._loopStartOffset >= chWk[al]._loopEndOffset) {
            chWk[al]._loopStartOffset = -1;
            chWk[al]._loopEndOffset = -1;
        }
    }

    /**
     * 0x12 PCMの割り込みを停止
     */
    public void stopInterrupt() {
        logger.log(Level.TRACE, "ppz8em: stopInterrupt");
        interrupt = true;
    }

    /**
     * 0x13 PAN指定
     *
     * @param al PCMチャネル(0~7)
     * @param dx PAN(0~9)
     */
    public void setPan(int al, int dx) {
        logger.log(Level.TRACE, String.format("ppz8em:sSetPan: %d", dx));
        chWk[al].pan = dx;
        chWk[al].panL = (chWk[al].pan < 6 ? 1.0 : (0.25 * (9 - chWk[al].pan)));
        chWk[al].panR = (chWk[al].pan > 4 ? 1.0 : (0.25 * chWk[al].pan));
    }

    /**
     * 0x15 元データ周波数設定
     *
     * @param al PCMチャネル(0~7)
     * @param dx 元周波数
     */
    public void setSrcFrequency(int al, int dx) {
        logger.log(Level.TRACE, String.format("ppz8em: setSrcFrequency: %d", dx));
        chWk[al]._srcFrequency = dx;
    }

    /**
     * 0x16 全体ボリューム
     */
    public void setAllVolume(int vol) {
        logger.log(Level.TRACE, String.format("ppz8em: SetAllVolume: %d", vol));
        if (vol < 16 && vol != PCM_VOLUME) {
            PCM_VOLUME = vol;
            makeVolumeTable(volume);
        }
    }

    /**
     * 音量調整用
     */
    public void setVolume(int vol) {
        if (vol != volume) {
            makeVolumeTable(vol);
        }
    }

    /**
     * 0x18  ﾁｬﾈﾙ7のADPCMのエミュレート設定
     *
     * @param al 0:ﾁｬﾈﾙ7でADPCMのエミュレートしない  1:する
     */
    public void setAdpcmEmu(int al) {
        logger.log(Level.TRACE, String.format("ppz8em: setAdpcmEmu: %d", al));
        adpcmEmu = al;
    }

    /**
     * 0x19 常駐解除許可、禁止設定
     *
     * @param v 0:常駐解除許可 1:常駐解除禁止
     */
    public void setReleaseFlag(int v) {
        // なにもしない
    }

    public void update(int[][] outputs, int samples) {
        if (interrupt) return;

        for (int j = 0; j < samples; j++) {
            int l = 0, r = 0;
            for (int i = 0; i < 8; i++) {
                if (pcmData[chWk[i].bank] == null) continue;
                if (!chWk[i].playing) continue;
                if (chWk[i].pan == 0) continue;

                if (i == 6) {
                    //Debug.printf(VolumeTable[chWk[i].volume][pcmData[chWk[i].bank][chWk[i].ptr]] * chWk[i].panL);
                }

                int n = chWk[i].ptr >= pcmData[chWk[i].bank].length ? 0x80 : pcmData[chWk[i].bank][chWk[i].ptr];
                l += (int) (volumeTable[chWk[i].volume][n] * chWk[i].panL);
                r += (int) (volumeTable[chWk[i].volume][n] * chWk[i].panR);
                chWk[i].delta += ((float) chWk[i].srcFrequency * (long) chWk[i].frequency / (long) 0x8000) / samplingRate;
                chWk[i].ptr += (int) chWk[i].delta;
                chWk[i].delta -= (int) chWk[i].delta;

                if (chWk[i].ptr >= chWk[i].end) {
                    if (chWk[i].loopStartOffset != -1) {
                        chWk[i].ptr -= chWk[i].loopEndOffset - chWk[i].loopStartOffset;
                    } else {
                        chWk[i].playing = false;
                    }
                }
            }

            l = (short) Math.max(Math.min(l, Short.MAX_VALUE), Short.MIN_VALUE);
            r = (short) Math.max(Math.min(r, Short.MAX_VALUE), Short.MIN_VALUE);
            outputs[0][j] += l;
            outputs[1][j] += r;
        }
    }

    public int convertPviAdpcmToPziPcm(int bank) {
        int[] table1 = new int[] {
                1, 3, 5, 7, 9, 11, 13, 15,
                -1, -3, -5, -7, -9, -11, -13, -15,
        };
        int[] table2 = new int[] {
                57, 57, 57, 57, 77, 102, 128, 153,
                57, 57, 57, 57, 77, 102, 128, 153,
        };

        List<Byte> o = new ArrayList<>();

        // ヘッダの生成
        o.add((byte) 'P');
        o.add((byte) 'Z');
        o.add((byte) 'I');
        o.add((byte) '1');
        for (int i = 4; i < 0x0b; i++) o.add((byte) 0);
        byte instCount = pcmData[bank][0xb];
        o.add(instCount);
        for (int i = 0xc; i < 0x20; i++) o.add((byte) 0);

        // 音色テーブルのコンバート
        long size2 = 0;
        for (int i = 0; i < instCount; i++) {
            int startaddress = ((pcmData[bank][i * 4 + 0x10] & 0xff) + (pcmData[bank][i * 4 + 0x11] & 0xff) * 0x100) << (5 + 1);
            int size = (((pcmData[bank][i * 4 + 0x12] & 0xff) + (pcmData[bank][i * 4 + 0x13] & 0xff) * 0x100)
                    - ((pcmData[bank][i * 4 + 0x10] & 0xff) + (pcmData[bank][i * 4 + 0x11] & 0xff) * 0x100) + 1)
                    << (5 + 1);// endAdr - startAdr
            size2 += size;
            short rate = 16000; // 16kHz

            o.add((byte) startaddress);
            o.add((byte) (startaddress >> 8));
            o.add((byte) (startaddress >> 16));
            o.add((byte) (startaddress >> 24));
            o.add((byte) size);
            o.add((byte) (size >> 8));
            o.add((byte) (size >> 16));
            o.add((byte) (size >> 24));
            o.add((byte) 0xff);
            o.add((byte) 0xff);
            o.add((byte) 0);
            o.add((byte) 0); // loop_start
            o.add((byte) 0xff);
            o.add((byte) 0xff);
            o.add((byte) 0);
            o.add((byte) 0); // loop_end
            o.add((byte) rate);
            o.add((byte) (rate >> 8)); // rate
        }

        for (int i = instCount; i < 128; i++) {
            o.add((byte) 0);
            o.add((byte) 0);
            o.add((byte) 0);
            o.add((byte) 0);
            o.add((byte) 0);
            o.add((byte) 0);
            o.add((byte) 0);
            o.add((byte) 0);
            o.add((byte) 0xff);
            o.add((byte) 0xff);
            o.add((byte) 0);
            o.add((byte) 0); // loop_start
            o.add((byte) 0xff);
            o.add((byte) 0xff);
            o.add((byte) 0);
            o.add((byte) 0); // loop_end
            short rate = 16000; // 16kHz
            o.add((byte) rate);
            o.add((byte) (rate >> 8)); // rate
        }

        // ADPCM > PCM に変換
        int psrcPtr = 0x10 + 4 * 128;
        for (int i = 0; i < instCount; i++) {
            int xN = 0x80; // Xn (ADPCM>PCM 変換用)
            int deltaN = 127; // deltaN(ADPCM>PCM 変換用)

            int size = (((pcmData[bank][i * 4 + 0x12] & 0xff) + (pcmData[bank][i * 4 + 0x13] & 0xff) * 0x100)
                    - ((pcmData[bank][i * 4 + 0x10] & 0xff) + (pcmData[bank][i * 4 + 0x11] & 0xff) * 0x100) + 1)
                    << (5 + 1); // endAdr - startAdr

            for (int j = 0; j < size / 2; j++) {
                int psrc = pcmData[bank][psrcPtr++] & 0xff;

                int n = xN + table1[(psrc >> 4) & 0x0f] * deltaN / 8;
                //Debug.printf(n);
                xN = Math.max(Math.min(n, 32767), -32768);

                n = deltaN * table2[(psrc >> 4) & 0x0f] / 64;
                //Debug.printf(n);
                deltaN = Math.max(Math.min(n, 24576), 127);

                o.add((byte) (xN / (32768 / 128) + 128));


                n = xN + table1[psrc & 0x0f] * deltaN / 8;
                //Debug.printf(n);
                xN = Math.max(Math.min(n, 32767), -32768);

                n = deltaN * table2[psrc & 0x0f] / 64;
                //Debug.printf(n);
                deltaN = Math.max(Math.min(n, 24576), 127);

                o.add((byte) (xN / (32768 / 128) + 128));
            }
        }

        pcmData[bank] = toByteArray(o);
        //File.writeAllBytes("a.raw", pcmData[bank]);
        return 0;
    }

    public Channel[] getChannels() {
        for (int ch = 0; ch < 8; ch++) {
            chWkBk[ch].bank = chWk[ch].bank;
            chWkBk[ch].delta = chWk[ch].delta;
            chWkBk[ch].end = chWk[ch].end;
            chWkBk[ch].frequency = chWk[ch].frequency;
            chWkBk[ch].loopEndOffset = chWk[ch].loopEndOffset;
            chWkBk[ch].loopStartOffset = chWk[ch].loopStartOffset;
            chWkBk[ch].num = chWk[ch].num;
            chWkBk[ch].pan = chWk[ch].pan;
            chWkBk[ch].panL = chWk[ch].panL;
            chWkBk[ch].panR = chWk[ch].panR;
            chWkBk[ch].playing = chWk[ch].playing;
            chWkBk[ch].ptr = chWk[ch].ptr;
            chWkBk[ch].srcFrequency = chWk[ch].srcFrequency;
            chWkBk[ch].volume = chWk[ch].volume;
            chWkBk[ch].KeyOn = chWk[ch].KeyOn;

            chWk[ch].KeyOn = false;
        }
        return chWkBk;
    }

    public void reset() {
        pcmData = new byte[2][];
        isPVI = new boolean[2];
        chWk = new Channel[] {
                new Channel(), new Channel(), new Channel(), new Channel(),
                new Channel(), new Channel(), new Channel(), new Channel()
        };

        bank = 0;
        ptr = 0;
        interrupt = false;
        adpcmEmu = 0;
        volumeTable = new short[][] {
                new short[256], new short[256], new short[256], new short[256],
                new short[256], new short[256], new short[256], new short[256],
                new short[256], new short[256], new short[256], new short[256],
                new short[256], new short[256], new short[256], new short[256]
        };

        PCM_VOLUME = 0;
        volume = 0;
    }

    public int write(int port, int adr, int data) {
        switch (port) {
        case 0x00:
            init();
            break;
        case 0x01:
            playPCM(adr, data);
            break;
        case 0x02:
            stopPCM(adr);
            break;
        case 0x03: // LoadPCM
            break;
        case 0x04: // ReadStatus
            readStatus(adr);
            break;
        case 0x07:
            setVolume(adr, data);
            break;
        case 0x0b:
            setFrequency(adr, data >> 16, data);
            break;
        case 0x0e:
            setLoopPoint(port >> 8, adr >> 16, adr, data >> 16, data);
            break;
        case 0x12:
            stopInterrupt();
            break;
        case 0x13:
            setPan(adr, data);
            break;
        case 0x15:
            setSrcFrequency(adr, data);
            break;
        case 0x16:
            setAllVolume(data);
            break;
        case 0x18:
            setAdpcmEmu(adr);
            break;
        case 0x19:
            setReleaseFlag(data);
            break;
        }
        return 0;
    }

    public void setSamplingRate(int clock) {
        samplingRate = clock;
    }
}
