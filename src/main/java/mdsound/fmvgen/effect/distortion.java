﻿
package mdsound.fmvgen.effect;

// フィルタークラス(https://vstcpp.wpblog.jp/?page_id=728 より)
public class distortion {
    // エフェクターのパラメーター
    private int clock;

    private int maxCh;

    private ChInfo[] chInfo = null;

    private float[] fbuf = new float[] {
        0f, 0f
    };

    private int currentCh = 0;

    private class ChInfo {
        public boolean sw = false;

        public CMyFilter highpassL = new CMyFilter();

        public CMyFilter highpassR = new CMyFilter();

        public float gain = 300.0f;// 増幅量。10～300程度(dB換算で20dB～50dB程度)

        public float volume = 0.1f;// 出力信号の音量。0.0～1.0の範囲
    }

    public distortion(int clock, int maxCh) {
        this.clock = clock;
        this.maxCh = maxCh;
        Init();
    }

    public void Init() {
        chInfo = new ChInfo[maxCh];
        for (int i = 0; i < chInfo.length; i++) {
            chInfo[i] = new ChInfo();
            chInfo[i].sw = false;

            // 内部変数
            // 高音域のみ通す(低音域をカットする)フィルタ設定(左右分)
            // カットする周波数の目安は20Hz～300Hz程度
            // 増幅量が大きくなれば、カットオフ周波数も大きくするとよい
            chInfo[i].highpassL = new CMyFilter();
            chInfo[i].highpassL.HighPass(200.0f, (float) (1.0f / Math.sqrt(2.0f)), clock);
            chInfo[i].highpassR = new CMyFilter();
            chInfo[i].highpassR.HighPass(200.0f, (float) (1.0f / Math.sqrt(2.0f)), clock);
            chInfo[i].gain = 300.0f;
            chInfo[i].volume = 0.1f;
        }

    }

    public void Mix(int ch, int inL, int inR) {
        Mix(ch, inL, inR, 1);
    }

    public void Mix(int ch, int inL, int inR, int wavelength) {
        if (ch < 0)
            return;
        if (ch >= maxCh)
            return;
        if (chInfo == null)
            return;
        if (chInfo[ch] == null)
            return;
        if (!chInfo[ch].sw)
            return;

        fbuf[0] = inL / 21474.83647f;
        fbuf[1] = inR / 21474.83647f;

        // inL[]、inR[]、outL[]、outR[]はそれぞれ入力信号と出力信号のバッファ(左右)
        // wavelenghtはバッファのサイズ、サンプリング周波数は44100Hzとする

        // 入力信号にエフェクターを適用する
        for (int i = 0; i < wavelength * 2; i += 2) {
            // 入力信号にフィルタを適用する
            float tmpL = chInfo[ch].highpassL.Process(fbuf[i + 0]);
            // 入力信号にゲインを掛けて増幅する
            tmpL = chInfo[ch].gain * tmpL;

            // 振幅の最大値(ここでは-1.0～1.0)を超えたものをクリッピングする
            if (tmpL > 1.0) {
                tmpL = 1.0f;
            }
            if (tmpL < -1.0) {
                tmpL = -1.0f;
            }

            // 右側の入力信号も同様に処理
            float tmpR = chInfo[ch].highpassR.Process(fbuf[i + 1]);
            tmpR = chInfo[ch].gain * tmpR;
            if (tmpR > 1.0) {
                tmpR = 1.0f;
            }
            if (tmpR < -1.0) {
                tmpR = -1.0f;
            }

            // 入力信号にフィルタをかける
            fbuf[i + 0] = chInfo[ch].volume * tmpL;
            fbuf[i + 1] = chInfo[ch].volume * tmpR;
        }

        inL = (int) (fbuf[0] * 21474.83647f);
        inR = (int) (fbuf[1] * 21474.83647f);
    }

    public void SetReg(int adr, byte data) {
        if (adr == 0) {
            currentCh = Math.max(Math.min(data & 0x3f, 38), 0);
            if ((data & 0x80) != 0)
                Init();
        } else if (adr == 1) {
            chInfo[currentCh].sw = ((data & 0x80) != 0);
            chInfo[currentCh].volume = (data & 0x7f) / 320.0f;
        } else if (adr == 2) {
            chInfo[currentCh].gain = (1000.0f - 20.0f) * (data & 0x7f) / 128.0f + 20.0f;
        } else if (adr == 3) {
            float f = 1000.0f * (data & 0x7f) / 256.0f;
            chInfo[currentCh].highpassL.HighPass(f, (float) (1.0f / Math.sqrt(2.0f)), clock);
            chInfo[currentCh].highpassR.HighPass(f, (float) (1.0f / Math.sqrt(2.0f)), clock);
        }
    }
}
