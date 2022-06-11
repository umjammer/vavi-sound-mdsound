
package mdsound.fmvgen.effect;

/**
 * フィルタークラス
 *
 * @see "https://vstcpp.wpblog.jp/?p=1939"
 */
public class Compressor {

    private int sampleRate = 44100;

    private int currentCh = 0;

    private int maxCh;

    private ChInfo[] chInfo = null;

    private float[] fBuf = new float[2];

    private static class ChInfo {

        public boolean sw = false;

        // エフェクターのパラメーター

        // 圧縮が始まる音圧。0.1～1.0程度
        public float threshold = 0.3f;
        // 圧縮する割合。2.0～10.0程度
        public float ratio = 2.0f;
        // 最終的な音量。1.0～3.0程度
        public float volume = 2.0f;

        // 内部変数

        /**
         * 音圧を検知するために使うローパスフィルタ
         * @see "https://vstcpp.wpblog.jp/?page_id=728"
         */
        public CMyFilter envfilterL, envfilterR;
        // 急激な音量変化を避けるためのローパスフィルタ
        public CMyFilter gainfilterL, gainfilterR;

        public  float envFreq = 30.0f;

        public float envQ = 1.0f;

        public float gainFreq = 5.0f;

        public float gainQ = 1.0f;

        ChInfo() {
            this.sw = false;

            this.threshold = 0.3f;
            this.ratio = 2.0f;
            this.volume = 2.0f;

            this.envFreq = 30.0f;
            this.envQ = 1.0f;
            this.gainFreq = 5.0f;
            this.gainQ = 1.0f;
            this.envfilterL = new CMyFilter();
            this.envfilterR = new CMyFilter(); // 音圧を検知するために使うローパスフィルタ
            this.gainfilterL = new CMyFilter();
            this.gainfilterR = new CMyFilter(); // 急激な音量変化を避けるためのローパスフィルタ
        }

        public void setReg(int adr, byte data, int sampleRate) {
            if (adr == 1) {
                this.sw = ((data & 0x80) != 0);
                this.volume = (data & 0x7f) / (127.0f / 4.0f);
            } else if (adr == 2) {
                this.threshold = Math.max(data / 255.0f, 0.1f);
            } else if (adr == 3) {
                this.ratio = Math.max(data / (255.0f / 10.0f), 1.0f);
            } else if (adr == 4) {
                this.envFreq = data / (255.0f / 80.0f);
                this.envfilterL.lowPass(this.envFreq, this.envQ, sampleRate);
                this.envfilterR.lowPass(this.envFreq, this.envQ, sampleRate);
            } else if (adr == 5) {
                this.envQ = CMyFilter.qTable[data];
                this.envfilterL.lowPass(this.envFreq, this.envQ, sampleRate);
                this.envfilterR.lowPass(this.envFreq, this.envQ, sampleRate);
            } else if (adr == 6) {
                this.gainFreq = data / (255.0f / 80.0f);
                this.gainfilterL.lowPass(this.gainFreq, this.gainQ, sampleRate);
                this.gainfilterR.lowPass(this.gainFreq, this.gainQ, sampleRate);
            } else if (adr == 7) {
                this.gainQ = CMyFilter.qTable[data];
                this.gainfilterL.lowPass(this.gainFreq, this.gainQ, sampleRate);
                this.gainfilterR.lowPass(this.gainFreq, this.gainQ, sampleRate);
            }
        }

        public void setLowPass(float envFreq, float envQ, float gainFreq, float gainQ, int sampleRate) {
            // カットオフ周波数が高いほど音圧変化に敏感になる。目安は10～50Hz程度
            this.envfilterL.lowPass(envFreq, envQ, sampleRate);
            this.envfilterR.lowPass(envFreq, envQ, sampleRate);
            // カットオフ周波数が高いほど急激な音量変化になる。目安は5～50Hz程度
            this.gainfilterL.lowPass(gainFreq, gainQ, sampleRate);
            this.gainfilterR.lowPass(gainFreq, gainQ, sampleRate);
        }
    }

    public Compressor(int sampleRate, int maxCh) {
        this.sampleRate = sampleRate;
        this.maxCh = maxCh;
        init();
    }

    public void init() {
        currentCh = 0;
        chInfo = new ChInfo[maxCh];
        for (int i = 0; i < chInfo.length; i++) {
            chInfo[i] = new ChInfo();
            setLowPass(i, 30.0f, 1.0f, 5.0f, 1.0f);
        }
    }

    public void mix(int ch, int[] inL, int[] inR) {
        mix(ch, inL, inR, 1);
    }

    public void mix(int ch, int[] inL, int[] inR, int waveLength) {
        if (ch < 0) return;
        if (ch >= maxCh) return;
        if (chInfo == null) return;
        if (chInfo[ch] == null) return;
        if (!chInfo[ch].sw) return;

        fBuf[0] = inL[0] / 21474.83647f;
        fBuf[1] = inR[0] / 21474.83647f;

        // inL[]、inR[]、outL[]、outR[]はそれぞれ入力信号と出力信号のバッファ(左右)
        // wavelenghtはバッファのサイズ、サンプリング周波数は44100Hzとする

        // 入力信号にエフェクトをかける
        // 入力信号の絶対値をとったものをローパスフィルタにかけて音圧を検知する
        float tmpL = chInfo[ch].envfilterL.process(Math.abs(fBuf[0]));
        float tmpR = chInfo[ch].envfilterR.process(Math.abs(fBuf[1]));

        // 音圧をもとに音量(ゲイン)を調整(左)
        float gainL = 1.0f;

        if (tmpL > chInfo[ch].threshold) {
            // スレッショルドを超えたので音量(ゲイン)を調節(圧縮)
            gainL = chInfo[ch].threshold + (tmpL - chInfo[ch].threshold) / chInfo[ch].ratio;
        }
        // 音量(ゲイン)が急激に変化しないようローパスフィルタを通す
        gainL = chInfo[ch].gainfilterL.process(gainL);

        // 左と同様に右も音圧をもとに音量(ゲイン)を調整
        float gainR = 1.0f;
        if (tmpR > chInfo[ch].threshold) {
            gainR = chInfo[ch].threshold + (tmpR - chInfo[ch].threshold) / chInfo[ch].ratio;
        }
        gainR = chInfo[ch].gainfilterR.process(gainR);

        // 入力信号に音量(ゲイン)をかけ、さらに最終的な音量を調整し出力する
        fBuf[0] = chInfo[ch].volume * gainL * fBuf[0];
        fBuf[1] = chInfo[ch].volume * gainR * fBuf[1];
        inL[0] = (int) (fBuf[0] * 21474.83647f);
        inR[0] = (int) (fBuf[1] * 21474.83647f);
    }

    // ローパスフィルターを設定
    private void setLowPass(int ch, float envFreq, float envQ, float gainFreq, float gainQ) {
        chInfo[ch].setLowPass(envFreq, envQ, gainFreq, gainQ, sampleRate);
    }

    public void setReg(int adr, byte data) {
        if (adr == 0) {
            currentCh = Math.max(Math.min(data & 0x3f, 38), 0);
            if ((data & 0x80) != 0)
                init();
        } else {
            chInfo[currentCh].setReg(adr, data, sampleRate);
        }
    }
}
