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
    public void reset(byte chipID) {
        info.init();
    }

    @Override
    public int start(byte chipID, int clock) {
        return start(chipID, clock, 0);
    }

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        info.samplingRate = clock;
        reset(chipID);
        return clock;
    }

    @Override
    public void stop(byte chipID) {
        // none
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        info.update(outputs, samples);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        return info.write(port, adr, data);
    }

    public int loadPcm(byte chipID, byte address, byte data, byte[] pcmData) {
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
        private int startOfs;
        // 発音中PCMデータ番地（小数部）
        private int startOfsX;
        // 残りサイズ
        private int size;
        // 発音開始PCMデータ番地
        private int _startOfs;
        // PCMデータサイズ
        private int _size;
        // PCMアドレス加算値 (整数部)
        private int addsize1;
        // PCMアドレス加算値 (小数部)
        private int addsize2;
        // リピート開始位置
        private int repeatOfs;
        // リピート後のサイズ
        private int repeatSize;
        // リリース開始位置
        private int releaseOfs;
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
        private boolean play86Flag;

        private int aVolume;
        // 音量テーブル
        private int[][] volumeTable;
        private static final int[] ratetable = new int[] {4135, 5513, 8270, 11025, 16540, 22050, 33080, 44100};

        static class Inst {
            public int start = 0;
            public int size = 0;
        }

        //from PMDWin p86drv.cpp
        public int loadPcm(int port, byte address, byte data, byte[] pcmData) {
            this.pcmData = pcmData;

            for (int i = 0; i < MAXInst; i++) {
                inst[i] = new Inst();

                inst[i].start =
                        pcmData[i * 6 + 0 + 12 + 1 + 3] +
                                pcmData[i * 6 + 1 + 12 + 1 + 3] * 0x100 +
                                pcmData[i * 6 + 2 + 12 + 1 + 3] * 0x10000; // - 0x610;
                inst[i].size =
                        pcmData[i * 6 + 3 + 12 + 1 + 3] +
                                pcmData[i * 6 + 4 + 12 + 1 + 3] * 0x100 +
                                pcmData[i * 6 + 5 + 12 + 1 + 3] * 0x10000;
            }

            return 0;
        }

        /**
         * 初期化(内部処理)
         */
        private void init() {

            interpolation = false;
            rate = (int) samplingRate;
            srcRate = ratetable[4];     // 16.54kHz
            pitch = 0;
            vol = 0;

            startOfs = 0;
            startOfsX = 0;
            size = 0;
            _startOfs = 0;
            _size = 0;
            addsize1 = 0;
            addsize2 = 0;
            repeatOfs = 0;
            repeatSize = 0;
            releaseOfs = 0;
            releaseSize = 0;
            repeatFlag = false;
            releaseFlag1 = false;
            releaseFlag2 = false;

            panFlag = 0;
            panDat = 0;
            play86Flag = false;

            aVolume = 0;
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
            if (aVolume != aVolumeTemp) {
                aVolume = aVolumeTemp;
                for (int i = 0; i < 16; i++) {
                    volumeTable[i] = new int[256];
                    //@ temp = pow(2.0, (i + 15) / 2.0) * AVolume / 0x18000;
                    double temp = i * aVolume / 256;
                    for (int j = 0; j < 256; j++) {
                        volumeTable[i][j] = (int) ((byte) j * temp);
                    }
                }
            }
        }

        /**
         * 真ん中（一次補間なし）
         */
        private void doubleTrans(int[][] dest, int nsamples) {
            for (int i = 0; i < nsamples; i++) {
                int data = volumeTable[vol][pcmData[startOfs]];

                data = (short) Math.max(Math.min(data, Short.MAX_VALUE), Short.MIN_VALUE);
                dest[0][i] += data;
                dest[1][i] += data;

                if (addAddress()) {
                    play86Flag = false;
                    return;
                }
            }
        }

        /**
         * 真ん中（逆相、一次補間なし）
         */
        private void doubleTransG(int[][] dest, int nsamples) {
            for (int i = 0; i < nsamples; i++) {
                int data = volumeTable[vol][pcmData[startOfs]];

                dest[0][i] += data;
                dest[1][i] -= data;

                if (addAddress()) {
                    play86Flag = false;
                    return;
                }
            }
        }

        /**
         * 左寄り（一次補間なし）
         */
        private void leftTrans(int[][] dest, int nsamples) {
            for (int i = 0; i < nsamples; i++) {
                int data = volumeTable[vol][pcmData[startOfs]];

                dest[0][i] += data;
                data = data * panDat / (256 / 2);
                dest[1][i] += data;

                if (addAddress()) {
                    play86Flag = false;
                    return;
                }
            }
        }

        /**
         * 左寄り（逆相、一次補間なし）
         */
        private void leftTransG(int[][] dest, int nsamples) {
            for (int i = 0; i < nsamples; i++) {
                int data = volumeTable[vol][pcmData[startOfs]];

                dest[0][i] += data;
                data = data * panDat / (256 / 2);
                dest[1][i] -= data;

                if (addAddress()) {
                    play86Flag = false;
                    return;
                }
            }
        }

        /**
         * 右寄り（一次補間なし）
         */
        private void rightTrans(int[][] dest, int nsamples) {
            for (int i = 0; i < nsamples; i++) {
                int data = volumeTable[vol][pcmData[startOfs]];

                dest[1][i] += data;
                data = data * panDat / (256 / 2);
                dest[0][i] += data;

                if (addAddress()) {
                    play86Flag = false;
                    return;
                }
            }
        }

        /**
         * 右寄り（逆相、一次補間なし）
         */
        private void rightTransG(int[][] dest, int nsamples) {
            for (int i = 0; i < nsamples; i++) {
                int data = volumeTable[vol][pcmData[startOfs]];

                dest[1][i] -= data;
                data = data * panDat / (256 / 2);
                dest[0][i] += data;

                if (addAddress()) {
                    play86Flag = false;
                    return;
                }
            }
        }

        private boolean addAddress() {
            startOfsX += addsize2;
            if (startOfsX >= 0x1000) {
                startOfsX -= 0x1000;
                startOfs++;
                size--;
            }
            startOfs += addsize1;
            size -= addsize1;

            if (size > 1) {       // 一次補間対策
                return false;
            } else if (!repeatFlag || releaseFlag2) {
                return true;
            }

            size = repeatSize;
            startOfs = repeatOfs;
            return false;
        }

        public void update(int[][] outputs, int samples) {
            if (play86Flag == false) return;
            if (size <= 1) {       // 一次補間対策
                play86Flag = false;
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
                _startOfs = inst[data].start;
                _size = inst[data].size;
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
                int _srcrate = adr >> 5;
                int _ontei = (adr & 0x1f) * 0x10000 + data;
                if (_srcrate < 0 || _srcrate > 7)
                    break;
                if (_ontei > 0x1f_ffff)
                    break;

                pitch = _ontei;
                srcRate = ratetable[_srcrate];

                //System.err.printf("_ontei:%x srcrate:%x", _ontei, srcrate);
                _ontei = (int) (_ontei * srcRate / (long) samplingRate);

                addsize2 = (_ontei & 0xffff) >> 4;
                addsize1 = _ontei >> 16;

                break;
            case 0x06: // loop
                break;
            case 0x07: // play
                startOfs = _startOfs;
                startOfsX = 0;
                size = _size;
                play86Flag = true;
                releaseFlag2 = false;
                break;
            case 0x08://stop
                play86Flag = false;
                break;
            case 0x09://keyoff
                if (releaseFlag1) {       // リリースが設定されているか?
                    startOfs = releaseOfs;
                    size = releaseSize;
                    releaseFlag2 = true;       // リリースした
                } else {
                    play86Flag = false;
                }
                break;
            }

            return 0;
        }
    }
}
