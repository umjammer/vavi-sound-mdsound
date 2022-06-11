package mdsound.fmvgen.effect;

//
// 3バンドイコライザー(https://vstcpp.wpblog.jp/?p=1417 より)
//
public class Eq3band {
    private float fL, fR;
    private int sampleRate = 44100;

    // エフェクターのパラメーター
    private boolean lowSw = false;
    private float lowFreq = 400.0f; // 低音域の周波数。50Hz～1kHz程度
    private float lowGain = 2.0f;   // 低音域のゲイン(増幅値)。-15～15dB程度
    private float lowQ = (float) (1.0f / Math.sqrt(2.0f));

    private boolean midSw = false;
    private float midfreq = 1000.0f; // 中音域の周波数。500Hz～4kHz程度
    private float midGain = -4.0f;   // 中音域のゲイン(増幅値)。-15～15dB程度
    private float midQ = (float) (1.0f / Math.sqrt(2.0f));

    private boolean highSw = false;
    private float highFreq = 4000.0f; // 高音域の周波数。1kHz～12kHz程度
    private float highGain = 4.0f;    // 高音域のゲイン(増幅値)。-15～15dB程度
    private float highQ = (float) (1.0f / Math.sqrt(2.0f));

    //パラメータのdefault値は
    //low
    // freq:126
    // gain:141
    // Q:67
    //mid
    // freq:162
    // gain:102
    // Q:67
    //high
    // freq:192
    // gain:154
    // Q:67

    // 内部変数
    private CMyFilter lowL = new CMyFilter(), lowR = new CMyFilter();
    private CMyFilter midL = new CMyFilter(), midR = new CMyFilter();
    private CMyFilter highL = new CMyFilter(), highR = new CMyFilter(); // フィルタークラス(https://vstcpp.wpblog.jp/?page_id=728 より)

    public Eq3band(int sampleRate/* = 44100*/) {
        this.sampleRate = sampleRate;
        if (CMyFilter.freqTable == null) CMyFilter.makeTable();
        updateParam();
    }

    public void mix(int[] buffer, int nsamples) {
        for (int i = 0; i < nsamples; i++) {
            fL = buffer[i * 2 + 0] / CMyFilter.convInt;
            fR = buffer[i * 2 + 1] / CMyFilter.convInt;


            // inL[]、inR[]、outL[]、outR[]はそれぞれ入力信号と出力信号のバッファ(左右)
            // wavelenghtはバッファのサイズ、サンプリング周波数は44100Hzとする
            // 入力信号にエフェクトをかける
            // 入力信号にフィルタをかける
            if (lowSw) {
                fL = lowL.process(fL);
                fR = lowR.process(fR);
            }
            if (midSw) {
                fL = midL.process(fL);
                fR = midR.process(fR);
            }
            if (highSw) {
                fL = highL.process(fL);
                fR = highR.process(fR);
            }


            buffer[i * 2 + 0] = (int) (fL * CMyFilter.convInt);
            buffer[i * 2 + 1] = (int) (fR * CMyFilter.convInt);
        }
    }

    public void setReg(int adr, byte data) {
        switch (adr & 0xf) {
        case 0:
            lowSw = data != 0;
            break;
        case 1:
            lowFreq = CMyFilter.freqTable[data];
            break;
        case 2:
            lowGain = CMyFilter.gainTable[data];
            break;
        case 3:
            lowQ = CMyFilter.qTable[data];
            break;

        case 4:
            midSw = data != 0;
            break;
        case 5:
            midfreq = CMyFilter.freqTable[data];
            break;
        case 6:
            midGain = CMyFilter.gainTable[data];
            break;
        case 7:
            midQ = CMyFilter.qTable[data];
            break;

        case 8:
            highSw = data != 0;
            break;
        case 9:
            highFreq = CMyFilter.freqTable[data];
            break;
        case 10:
            highGain = CMyFilter.gainTable[data];
            break;
        case 11:
            highQ = CMyFilter.qTable[data];
            break;
        }

        updateParam();
    }

    private void updateParam() {
        // 低音域を持ち上げる(ローシェルフ)フィルタ設定(左右分)
        lowL.lowShelf(lowFreq, lowQ, lowGain, sampleRate);
        lowR.lowShelf(lowFreq, lowQ, lowGain, sampleRate);
        // 中音域を持ち上げる(ピーキング)フィルタ設定(左右分)
        midL.peaking(midfreq, midQ, midGain, sampleRate);
        midL.peaking(midfreq, midQ, midGain, sampleRate);
        // 高音域を持ち上げる(ローシェルフ)フィルタ設定(左右分)
        highL.highShelf(highFreq, highQ, highGain, sampleRate);
        highR.highShelf(highFreq, highQ, highGain, sampleRate);
    }
}
