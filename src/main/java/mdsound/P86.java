package mdsound;

public class P86 extends Instrument.BaseInstrument {

    P86Status info = new P86Status();

    @Override
    public String getName() {
        return "PC-9801-86";
    }

    @Override
    public String getShortName() {
        return "P86";
    }

    @Override
    public void reset(byte chipId) {
        info.init();
    }

    @Override
    public int start(byte chipId, int clock) {
        return start(chipId, clock, 0);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        info.samplingRate = clock;
        reset(chipId);
        return clock;
    }

    @Override
    public void stop(byte chipId) {
        // none
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        info.update(outputs, samples);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        return info.write(port, adr, data);
    }

    public int loadPcm(byte chipId, byte address, byte data, byte[] pcmData) {
        return info.loadPcm(0, address, data, pcmData);
    }

    // from PMDWin
    static class P86Status {
        private double samplingRate = 44100.0;
        private byte[] pcmData = null;
        private static final int MAXInst = 256;
        private Inst[] inst = new Inst[MAXInst];

        // 補完するか？
        private boolean interpolation = false;
        // 再生周波数
        private int rate;
        // 元データの周波数
        private int srcRate;
        // 音程(fnum)
        private int pitch;
        // 音量
        private int vol;
        // P86 保存用メモリポインタ
        private int _addr;
        // 発音中PCMデータ番地
        private int currentOffset;
        // 発音中PCMデータ番地（小数部）
        private int currestOffsetX;
        // 残りサイズ
        private int remainingSize;
        // 発音開始PCMデータ番地
        private int startOffset;
        // PCMデータサイズ
        private int size;
        // PCMアドレス加算値 (整数部)
        private int addsize1;
        // PCMアドレス加算値 (小数部)
        private int addSize2;
        // リピート開始位置
        private int repeatOffset;
        // リピート後のサイズ
        private int repeatSize;
        // リリース開始位置
        private int releaseOffset;
        // リリース後のサイズ
        private int releaseSize;
        // リピートするかどうかのflag
        private boolean repeatFlag;
        // リリースするかどうかのflag
        private boolean releaseFlag1;
        // リリースしたかどうかのflag
        private boolean releaseFlag2;

        // パンデータ１(bit0=左/bit1=右/bit2=逆)
        private int panFlag;
        // パンデータ２(音量を下げるサイドの音量値)
        private int panDat;
        // 発音中?flag
        private boolean playing;

        private int volume;
        // 音量テーブル
        private int[][] volumeTable;
        private static final int[] rateTable = new int[] {4135, 5513, 8270, 11025, 16540, 22050, 33080, 44100};

        static class Inst {
            public int start = 0;
            public int size = 0;

            public Inst(byte[] pcmData, int i) {
                this.start = pcmData[i * 6 + 0 + 12 + 1 + 3] +
                        pcmData[i * 6 + 1 + 12 + 1 + 3] * 0x100 +
                        pcmData[i * 6 + 2 + 12 + 1 + 3] * 0x10000; // - 0x610;
                this.size =  pcmData[i * 6 + 3 + 12 + 1 + 3] +
                        pcmData[i * 6 + 4 + 12 + 1 + 3] * 0x100 +
                        pcmData[i * 6 + 5 + 12 + 1 + 3] * 0x10000;
            }
        }

        // from PMDWin p86drv.cpp
        public int loadPcm(int port, byte address, byte data, byte[] pcmData) {
            this.pcmData = pcmData;

            for (int i = 0; i < MAXInst; i++) {
                inst[i] = new Inst(pcmData, i);
            }

            return 0;
        }

        /**
         * 初期化(内部処理)
         */
        private void init() {

            interpolation = false;
            rate = (int) samplingRate;
            srcRate = rateTable[4]; // 16.54kHz
            pitch = 0;
            vol = 0;

            currentOffset = 0;
            currestOffsetX = 0;
            remainingSize = 0;
            startOffset = 0;
            size = 0;
            addsize1 = 0;
            addSize2 = 0;
            repeatOffset = 0;
            repeatSize = 0;
            releaseOffset = 0;
            releaseSize = 0;
            repeatFlag = false;
            releaseFlag1 = false;
            releaseFlag2 = false;

            panFlag = 0;
            panDat = 0;
            playing = false;

            volume = 0;
            setVolume(0);
        }

        /**
         * 音量調整用
         */
        private void setVolume(int volume) {
            makeVolumeTable(volume);
        }

        /**
         * 音量テーブル作成
         */
        private void makeVolumeTable(int volume) {
            volumeTable = new int[16][];
            int aVolumeTemp = (int) (0x1000 * Math.pow(10.0, volume / 40.0));
            if (this.volume != aVolumeTemp) {
                this.volume = aVolumeTemp;
                for (int i = 0; i < 16; i++) {
                    volumeTable[i] = new int[256];
                    // temp = pow(2.0, (i + 15) / 2.0) * aVolume / 0x18000;
                    double temp = i * this.volume / 256;
                    for (int j = 0; j < 256; j++) {
                        volumeTable[i][j] = (int) ((byte) j * temp);
                    }
                }
            }
        }

        /**
         * 真ん中（一次補間なし）
         */
        private void doubleTrans(int[][] buffer, int samples) {
            for (int i = 0; i < samples; i++) {
                int data = volumeTable[vol][pcmData[currentOffset]];

                data = (short) Math.max(Math.min(data, Short.MAX_VALUE), Short.MIN_VALUE);
                buffer[0][i] += data;
                buffer[1][i] += data;

                if (addAddress()) {
                    playing = false;
                    return;
                }
            }
        }

        /**
         * 真ん中（逆相、一次補間なし）
         */
        private void doubleTransG(int[][] buffer, int samples) {
            for (int i = 0; i < samples; i++) {
                int data = volumeTable[vol][pcmData[currentOffset]];

                buffer[0][i] += data;
                buffer[1][i] -= data;

                if (addAddress()) {
                    playing = false;
                    return;
                }
            }
        }

        /**
         * 左寄り（一次補間なし）
         */
        private void leftTrans(int[][] buffer, int samples) {
            for (int i = 0; i < samples; i++) {
                int data = volumeTable[vol][pcmData[currentOffset]];

                buffer[0][i] += data;
                data = data * panDat / (256 / 2);
                buffer[1][i] += data;

                if (addAddress()) {
                    playing = false;
                    return;
                }
            }
        }

        /**
         * 左寄り（逆相、一次補間なし）
         */
        private void leftTransG(int[][] buffer, int samples) {
            for (int i = 0; i < samples; i++) {
                int data = volumeTable[vol][pcmData[currentOffset]];

                buffer[0][i] += data;
                data = data * panDat / (256 / 2);
                buffer[1][i] -= data;

                if (addAddress()) {
                    playing = false;
                    return;
                }
            }
        }

        /**
         * 右寄り（一次補間なし）
         */
        private void rightTrans(int[][] buffer, int samples) {
            for (int i = 0; i < samples; i++) {
                int data = volumeTable[vol][pcmData[currentOffset]];

                buffer[1][i] += data;
                data = data * panDat / (256 / 2);
                buffer[0][i] += data;

                if (addAddress()) {
                    playing = false;
                    return;
                }
            }
        }

        /**
         * 右寄り（逆相、一次補間なし）
         */
        private void rightTransG(int[][] buffer, int samples) {
            for (int i = 0; i < samples; i++) {
                int data = volumeTable[vol][pcmData[currentOffset]];

                buffer[1][i] -= data;
                data = data * panDat / (256 / 2);
                buffer[0][i] += data;

                if (addAddress()) {
                    playing = false;
                    return;
                }
            }
        }

        private boolean addAddress() {
            currestOffsetX += addSize2;
            if (currestOffsetX >= 0x1000) {
                currestOffsetX -= 0x1000;
                currentOffset++;
                remainingSize--;
            }
            currentOffset += addsize1;
            remainingSize -= addsize1;

            if (remainingSize > 1) { // 一次補間対策
                return false;
            } else if (!repeatFlag || releaseFlag2) {
                return true;
            }

            remainingSize = repeatSize;
            currentOffset = repeatOffset;
            return false;
        }

        public void update(int[][] outputs, int samples) {
            if (!playing) return;
            if (remainingSize <= 1) { // 一次補間対策
                playing = false;
                return;
            }
            switch (panFlag) {
            case 0:
                doubleTrans(outputs, samples);
                break;
            case 1:
                leftTrans(outputs, samples);
                break;
            case 2:
                rightTrans(outputs, samples);
                break;
            case 3:
                doubleTrans(outputs, samples);
                break;
            case 4:
                doubleTransG(outputs, samples);
                break;
            case 5:
                leftTransG(outputs, samples);
                break;
            case 6:
                rightTransG(outputs, samples);
                break;
            case 7:
                doubleTransG(outputs, samples);
                break;
            }
        }

        public int write(int port, int adr, int data) {
            switch ((byte) port) {
            case 0x00: // Init
                break;
            case 0x01: // LoadPcm
                break;
            case 0x02: // 音色
                startOffset = inst[data].start;
                size = inst[data].size;
                repeatFlag = false;
                releaseFlag1 = false;
                break;
            case 0x03: // パン
                panFlag = adr;
                panDat = data;
                break;
            case 0x04: // 音量
                vol = (byte) data;
                break;
            case 0x05: // ontei
                int srcRate = adr >> 5;
                int pitch = (adr & 0x1f) * 0x10000 + data;
                if (srcRate < 0 || srcRate > 7)
                    break;
                if (pitch > 0x1f_ffff)
                    break;

                this.pitch = pitch;
                this.srcRate = rateTable[srcRate];

                //System.err.printf("pitch:%x srcrate:%x", pitch, srcrate);
                pitch = (int) (pitch * this.srcRate / (long) samplingRate);

                addSize2 = (pitch & 0xffff) >> 4;
                addsize1 = pitch >> 16;

                break;
            case 0x06: // loop
                break;
            case 0x07: // play
                currentOffset = startOffset;
                currestOffsetX = 0;
                remainingSize = size;
                playing = true;
                releaseFlag2 = false;
                break;
            case 0x08: // stop
                playing = false;
                break;
            case 0x09: // keyoff
                if (releaseFlag1) { // リリースが設定されているか?
                    currentOffset = releaseOffset;
                    remainingSize = releaseSize;
                    releaseFlag2 = true; // リリースした
                } else {
                    playing = false;
                }
                break;
            }

            return 0;
        }
    }
}
