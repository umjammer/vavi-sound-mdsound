package mdsound.fmvgen.effect;

public class CRingBuffur {

    // 読み込み位置
    private int rpos;
    // 書き込み位置
    private int wpos;
    // 内部バッファ
    private float[] buf;
    private int rbSize;

    /** 初期化を行う */
    public CRingBuffur(int clock, float RB/* = 4.0f*/) {
        rbSize = (int) (clock * RB);
        rpos = 0;
        wpos = (int) (rbSize / 2.0); // とりあえずバッファサイズの半分ぐらいにしておく

        buf = new float[rbSize];
    }

    /**
     * 読み込み位置と書き込み位置の間隔を設定する関数
     * ディレイエフェクターの場合はそのまま遅延時間(ディレイタイム)になる
     */
    public void setInterval(int interval) {
        // 読み込み位置と書き込み位置の間隔を設定

        // 値が0以下やバッファサイズ以上にならないよう処理
        interval = interval % rbSize;
        if (interval <= 0) {
            interval = 1;
        }

        // 書き込み位置を読み込み位置からinterval分だけ離して設定
        wpos = (rpos + interval) % rbSize;
    }

    /**
     * 内部バッファの読み込み位置 {@link #rpos} のデータを読み込む関数
     * @param pos 読み込み位置 {@link #rpos} からの相対位置
     * (相対位置(pos)はコーラスやピッチシフタなどのエフェクターで利用する)
     */
    public float read(int pos/* = 0*/) {
        // 読み込み位置(rpos)と相対位置(pos)から実際に読み込む位置を計算する。
        int tmp = rpos + pos;
        while (tmp < 0) {
            tmp += rbSize;
        }
        tmp %= rbSize; // バッファサイズ以上にならないよう処理

        // 読み込み位置の値を返す
        return buf[tmp];
    }

    /**
     * 内部バッファの書き込み位置 {@link #wpos} にデータを書き込む関数
     */
    public void write(float in_) {
        // 書き込み位置(wpos)に値を書き込む
        buf[wpos] = in_;
    }

    /**
     * 内部バッファの読み込み位置{@link #rpos}、書き込み位置{@link #wpos}を一つ進める関数
     */
    public void update() {
        // 内部バッファの読み込み位置(rpos)、書き込み位置(wpos)を一つ進める
        rpos = (rpos + 1) % rbSize;
        wpos = (wpos + 1) % rbSize;
    }
}
