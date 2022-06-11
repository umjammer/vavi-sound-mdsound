package mdsound.np;

import java.util.ArrayList;
import java.util.List;


public interface Device {
    void reset();

    boolean write(int adr, int val, int id);

    default boolean write(int adr, int val) {
        return write(adr, val, 0);
    }

    boolean read(int adr, int[] val, int id);

    default boolean read(int adr, int[] val) {
        return read(adr, val, 0);
    }

    void setOption(int id, int val);

    interface Renderable extends Device {
        /**
         * 音声のレンダリング
         *
         * @param b 合成されたデータを格納する配列．
         *             b[0]が左チャンネル，b[1]が右チャンネルの音声データ．
         * @return 合成したデータのサイズ．1ならモノラル．2ならステレオ．0は合成失敗．
         */
        int render(int[] b);

        /**
         * chip update/operation is now bound to CPU clocks
         * Render() now simply mixes and outputs Sound
         */
        void tick(int clocks);
    }

    /**
     * 音声合成チップ
     */
    interface SoundChip extends Renderable {
        /**
         * Soundchip clocked by M2 (NTSC = ~1.789MHz)
         */
        @Override void tick(int clocks);

        /**
         * チップの動作クロックを設定
         *
         * @param clock 動作周波数
         */
        void setClock(double clock);

        /**
         * 音声合成レート設定
         *
         * @param rate 出力周波数
         */
        void setRate(double rate);

        /**
         * Channel mask.
         */
        void setMask(int mask);

        /**
         * Stereo mix.
         * mixl = 0-256
         * mixr = 0-256
         * 128 = neutral
         * 256 = double
         * 0 = nil
         * <0 = inverted
         */
        void setStereoMix(int trk, short mixl, short mixr);

        /**
         * Track info for keyboard view.
         */
        //TrackInfo getTrackInfo(int trk) { return null; }
    }

    class Bus implements Device {
        protected List<Device> vd = new ArrayList<>();

        /**
         * リセット
         *
         * <p>
         * 取り付けられている全てのデバイスの，Resetメソッドを呼び出す．
         * 呼び出し順序は，デバイスが取り付けられた順序に等しい．
         * </P>
         */
        @Override
        public void reset() {

            for (Device it : vd)
                it.reset();
        }

        /**
         * 全デバイスの取り外し
         */
        public void detachAll() {
            vd.clear();
        }

        /**
         * デバイスの取り付け
         *
         * <p>
         * このバスにデバイスを取り付ける．
         * </P>
         *
         * @param d 取り付けるデバイスへのポインタ
         */
        public void attach(Device d) {
            vd.add(d);
        }

        /**
         * 書き込み
         *
         * <p>
         * 取り付けられている全てのデバイスの，Writeメソッドを呼び出す．
         * 呼び出し順序は，デバイスが取り付けられた順序に等しい．
         * </P>
         */
        @Override
        public boolean write(int adr, int val, int id/* = 0*/) {
            boolean ret = false;
            for (Device it : vd)
                ret |= it.write(adr, val);
            return ret;
        }

        /**
         * 読み込み
         *
         * <p>
         * 取り付けられている全てのデバイスのReadメソッドを呼び出す．
         * 呼び出し順序は，デバイスが取り付けられた順序に等しい．
         * 帰り値は有効な(Readメソッドがtrueを返却した)デバイスの
         * 返り値の論理和．
         * </P>
         */
        @Override
        public boolean read(int adr, int[] val, int id/* = 0*/) {
            boolean ret = false;
            int[] vtmp = new int[] { 0 };

            val[0] = 0;
            for (Device it : vd) {
                if (it.read(adr, vtmp)) {
                    val[0] |= vtmp[0];
                    ret = true;
                }
            }
            return ret;
        }

        @Override
        public void setOption(int id, int val) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * レイヤー
     *
     * <p>
     * バスと似ているが，読み書きの動作を全デバイスに伝播させない．
     * 最初に読み書きに成功したデバイスを発見した時点で終了する．
     * </P>
     */
    class Layer extends Bus {

        /**
         * 書き込み
         *
         * <p>
         * 取り付けられているデバイスのWriteメソッドを呼び出す．
         * 呼び出し順序は，デバイスが取り付けられた順序に等しい．
         * Writeに成功したデバイスが見つかった時点で終了．
         * </P>
         */
        @Override
        public boolean write(int adr, int val, int id/* = 0*/) {
            for (Device it : vd) {
                if (it.write(adr, val)) return true;
            }

            return false;
        }

        /**
         * 読み込み
         *
         * <p>
         * 取り付けられているデバイスのReadメソッドを呼び出す．
         * 呼び出し順序は，デバイスが取り付けられた順序に等しい．
         * Readに成功したデバイスが見つかった時点で終了．
         * </P>
         */
        @Override
        public boolean read(int adr, int[] val, int id/* = 0*/) {
            val[0] = 0;
            for (Device it : vd) {
                if (it.read(adr, val)) return true;
            }

            return false;
        }
    }
}

class Counter {
    // Note: For increased speed, I'll inline all of NSFPlay's Counter member functions.
    private static final int COUNTER_SHIFT = 24;

    public double ratio;
    public int val, step;

    void setCycle(int s) {
        this.step = (int) (this.ratio / (s + 1));
    }

    void iup() {
        this.val += this.step;
    }

    int value() {
        return this.val >> COUNTER_SHIFT;
    }

    void init(double clk, double rate) {
        this.ratio = (1 << COUNTER_SHIFT) * (1.0 * clk / rate);
        this.step = (int) (this.ratio + 0.5);
        this.val = 0;
    }
}
