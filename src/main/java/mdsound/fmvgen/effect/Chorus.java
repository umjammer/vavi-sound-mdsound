
package mdsound.fmvgen.effect;

/**
 * Chorus Flanger implementation example
 *
 * @see "https://vstcpp.wpblog.jp/?p=1797"
 */
public class Chorus {

    private final float clock;
    private final int maxCh;
    private ChInfo[] chInfo = null;
    private int currentCh = 0;

    public static class ChInfo {
        public boolean sw;

        /** Chorus effect level. Between 0.0 and 1.0 */
        public float mix = 0.3f;
        /** Chorus fluctuation interval. 0Hz to 16Hz */
        public float rate = 3.0f;
        /** Depth of chorus fluctuation. Approximately 5.0 to 200.0 samples */
        public float depth = 10.0f;
        /** Chorus feedback amount. Between 0.0 and 1.0 */
        public float feedback = 0.3f;

        /**
         * Ring Buffer
         * @see "https://vstcpp.wpblog.jp/?p=1505"
         */
        public RingBuffer ringBufL, ringBufR;

        /**
         * Set the delay time by converting it into a number of samples
         * The sample position to be read will move according to the depth,
         * so make sure that the interval does not become 0 or less when it moves.
         * For now, let’s take about 1000 samples.
         * (Interval is a ring buffer. See https://vstcpp.wpblog.jp/?p=1505)
         */
        public int delaySample;

        public float theta;
//        public float speed;

        public ChInfo(int clock) {
            delaySample = 10;
            theta = 0; // The angle θ of the sine function to fluctuate the delay reading position. The initial value is 0.

            sw = false;
            ringBufL = new RingBuffer(clock, 0.02f);
            ringBufR = new RingBuffer(clock, 0.02f);
            ringBufL.setInterval(delaySample);
            ringBufR.setInterval(delaySample);
        }
    }

    public Chorus(int clock, int maxCh) {
        this.clock = (float) clock;
        this.maxCh = maxCh;
        init();
    }

    public void init() {
        chInfo = new ChInfo[maxCh];
        for (int i = 0; i < chInfo.length; i++) {
            chInfo[i] = new ChInfo((int) clock);
        }
    }

    /**
     * 線形補間関数
     * v1とv2を割合tで線形補間する。tは0.0～1.0の範囲とする
     * tが0.0の時v1の値となり、tが1.0の時v2の値となる
     */
    private static float lerp(float v1, float v2, float t) {
        return (1.0f - t) * v1 + t * v2;
    }

    public void mix(int ch, int[] inL, int[] inR) {
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

        ChInfo ci = chInfo[ch];
        float finL = inL[0] / 21474.83647f;
        float finR = inR[0] / 21474.83647f;
        float speed = (2.0f * 3.14159265f * ci.rate) / clock; // 揺らぎのスピード。角速度ωと同じ。

        // inL[]、inR[]、outL[]、outR[]はそれぞれ入力信号と出力信号のバッファ(左右)
        // wavelenghtはバッファのサイズ、サンプリング周波数は44100Hzとする

        // 入力信号にコーラスかける
        // 角度θに角速度を加える
        ci.theta += speed;

        // 読み込み位置を揺らす量を計算
        // sin()関数の結果にdepthを掛ける
        float a = (float) (Math.sin(ci.theta) * ci.depth);

        // 読み込み位置を揺らした際の前後の整数値を取得(あとで線形補間するため)
        int p1 = (int) a;
        int p2 = (int) (a + 1);

        // 前後の整数値から読み込み位置の値を線形補間で割り出す
        float lerpL1 = lerp(ci.ringBufL.read(p1), ci.ringBufL.read(p2), a - (float) p1);
        float lerpR1 = lerp(ci.ringBufR.read(p1), ci.ringBufR.read(p2), a - (float) p1);

        // 入力信号にディレイ信号を混ぜる
        float tmpL = (1.0f - ci.mix) * finL + ci.mix * lerpL1;
        float tmpR = (1.0f - ci.mix) * finR + ci.mix * lerpR1;

        // ディレイ信号として入力信号とフィードバック信号をリングバッファに書き込み
        ci.ringBufL.write((1.0f - ci.feedback) * finL + ci.feedback * tmpL);
        ci.ringBufR.write((1.0f - ci.feedback) * finR + ci.feedback * tmpR);

        // リングバッファの状態を更新する
        ci.ringBufL.update();
        ci.ringBufR.update();

        // 出力信号に書き込む
        finL = tmpL;
        finR = tmpR;

        inL[0] = (int) (finL * 21474.83647f);
        inR[0] = (int) (finR * 21474.83647f);
    }

    public void setReg(int adr, byte data) {
        if (adr == 0) {
            currentCh = Math.max(Math.min(data & 0x3f, 38), 0);
            if ((data & 0x80) != 0)
                init();
        } else if (adr == 1) {
            chInfo[currentCh].sw = ((data & 0x80) != 0);
            chInfo[currentCh].mix = (data & 0x7f) / 127.0f;
        } else if (adr == 2) {
            chInfo[currentCh].rate = 16.0f * (data & 0x7f) / 127.0f;
        } else if (adr == 3) {
            chInfo[currentCh].depth = 195.0f * (data & 0x7f) / 127.0f + 5.0f;
        } else if (adr == 4) {
            chInfo[currentCh].feedback = (data & 0x7f) / 127.0f;
        }
    }
}
