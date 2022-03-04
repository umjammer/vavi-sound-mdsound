package mdsound.np;

import java.util.ArrayList;
import java.util.List;

    public interface IDevice
    {
        void Reset();
        boolean Write(int adr, int val, int id);
        default boolean Write(int adr, int val) {
            return Write(adr, val, 0);
        }
        boolean Read(int adr, int val, int id);
        default boolean Read(int adr, int val) {
            return Read(adr, val, 0);
        }
        void SetOption(int id, int val);

    interface IRenderable extends IDevice
    {
        /**
         * 音声のレンダリング
         *
         * @param b[2] 合成されたデータを格納する配列．
         * b[0]が左チャンネル，b[1]が右チャンネルの音声データ．
         * @return 合成したデータのサイズ．1ならモノラル．2ならステレオ．0は合成失敗．
         */
        public abstract int Render(int[] b);

        /**
         *  chip update/operation is now bound to CPU clocks
         *  Render() now simply mixes and outputs sound
         */
        public abstract void Tick(int clocks);

    }

    /**
  * 音声合成チップ
  */
    public interface ISoundChip extends IRenderable
    {
        /**
         * Soundchip clocked by M2 (NTSC = ~1.789MHz)
         */
        public abstract @Override void Tick(int clocks);

        /**
         * チップの動作クロックを設定
         *
         * @param clock 動作周波数
         */
        public abstract void SetClock(double clock);

        /**
         * 音声合成レート設定
         *
         * @param rate 出力周波数
         */
        public abstract void SetRate(double rate);

        /**
         * Channel mask.
         */
        public abstract void SetMask(int mask);

        /**
         * Stereo mix.
         *   mixl = 0-256
         *   mixr = 0-256
         *     128 = neutral
         *     256 = double
         *     0 = nil
         *    <0 = inverted
         */
        public abstract void SetStereoMix(int trk, short mixl, short mixr);

        /**
         * Track info for keyboard view.
         */
        //ITrackInfo GetTrackInfo(int trk) { return null; }
    }

    class Bus implements IDevice
    {
        protected List<IDevice> vd = new ArrayList<IDevice>();

        /**
         * リセット
         *
         * <P>
         * 取り付けられている全てのデバイスの，Resetメソッドを呼び出す．
         * 呼び出し順序は，デバイスが取り付けられた順序に等しい．
         * </P>
         */
        @Override public void Reset()
        {

            for (IDevice it : vd)
                it.Reset();
        }

        /**
         * 全デバイスの取り外し
         */
        public void DetachAll()
        {
            vd.clear();
        }

        /**
         * デバイスの取り付け
         *
         * <P>
         * このバスにデバイスを取り付ける．
         * </P>
         *
         * @param d 取り付けるデバイスへのポインタ
         */
        public void Attach(IDevice d)
        {
            vd.add(d);
        }

        /**
         * 書き込み
         *
         * <P>
         * 取り付けられている全てのデバイスの，Writeメソッドを呼び出す．
         * 呼び出し順序は，デバイスが取り付けられた順序に等しい．
         * </P>
         */
        @Override public boolean Write(int adr, int val, int id/* = 0*/)
        {
            boolean ret = false;
            for (IDevice it : vd)
                ret |= it.Write(adr, val);
            return ret;
        }

        /**
         * 読み込み
         *
         * <P>
         * 取り付けられている全てのデバイスのReadメソッドを呼び出す．
         * 呼び出し順序は，デバイスが取り付けられた順序に等しい．
         * 帰り値は有効な(Readメソッドがtrueを返却した)デバイスの
         * 返り値の論理和．
         * </P>
         */
        @Override public boolean Read(int adr, int val, int id/* = 0*/)
        {
            boolean ret = false;
            int vtmp = 0;

            val = 0;
            for (IDevice it : vd)
            {
                if (it.Read(adr, vtmp))
                {
                    val |= vtmp;
                    ret = true;
                }
            }
            return ret;

        }

        @Override public void SetOption(int id, int val)
        {
            throw new UnsupportedOperationException();
        }
    }

    /**
 * レイヤー
 *
 * <P>
 * バスと似ているが，読み書きの動作を全デバイスに伝播させない．
 * 最初に読み書きに成功したデバイスを発見した時点で終了する．
 * </P>
 */
    class Layer extends Bus
    {

        /**
         * 書き込み
         *
         * <P>
         * 取り付けられているデバイスのWriteメソッドを呼び出す．
         * 呼び出し順序は，デバイスが取り付けられた順序に等しい．
         * Writeに成功したデバイスが見つかった時点で終了．
         * </P>
         */
        @Override public boolean Write(int adr, int val, int id/* = 0*/)
        {
            for (IDevice it : vd)
            {
                if (it.Write(adr, val)) return true;
            }

            return false;
        }

        /**
         * 読み込み
         *
         * <P>
         * 取り付けられているデバイスのReadメソッドを呼び出す．
         * 呼び出し順序は，デバイスが取り付けられた順序に等しい．
         * Readに成功したデバイスが見つかった時点で終了．
         * </P>
         */
        @Override public boolean Read(int adr, int val, int id/* = 0*/)
        {
            val = 0;
            for (IDevice it : vd)
            {
                if (it.Read(adr, val)) return true;
            }

            return false;
        }
    }


}
