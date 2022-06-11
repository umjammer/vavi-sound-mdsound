/*
    Copyright (C) 2004 Ki

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package mdsound;


public class OotakePsg extends Instrument.BaseInstrument {

    /*
    Ootake
    ・キューの参照処理をシンプルにした。テンポの安定性および音質の向上。
    ・オーバーサンプリングしないようにした。（筆者の主観もあるが、PSGの場合、響きの
    美しさが損なわれてしまうケースが多いため。速度的にもアップ）
    ・ノイズの音質・音量を実機並みに調整した。v0.72
    ・ノイズの周波数に0x1Fが書き込まれたときは、0x1Eと同じ周波数で音量を半分にして
    鳴らすようにした。v0.68
    ・現状は再生サンプルレートは44.1KHz固定とした。(CD-DA再生時の速度アップのため)
    ・DDA音の発声が終了したときにいきなり波形を0にせず、フェードアウトさせるように
    し、ノイズを軽減した。v0.57
    ・DDAモード(サンプリング発声)のときの波形データのノイズが多く含まれている部分
    をカットしして、音質を上げた。音量も調節した。v0.59
    ・ノイズ音の音質・音量を調整して、実機の雰囲気に近づけた。v0.68
    ・waveIndexの初期化とDDAモード時の動作を見直して実機の動作に近づけた。v0.63
    ・waveIndexの初期化時にwaveテーブルも初期化するようにした。ファイヤープロレス
    リング、Ｆ１トリプルバトルなどの音が実機に近づいた。v0.65
    ・waveの波形の正負を実機同様にした。v0.74
    ・waveの最小値が-14になるようにし音質を整えた。v0.74
    ・クリティカルセクションは必要ない(書き込みが同時に行われるわけではない)ような
    ので、省略し高速化した。v1.09
    ・キュー処理(ApuQueue.c)をここに統合して高速化した。v1.10
    ・低音領域のボリュームを上げて実機並みの聞こえやすさに近づけた。v1.46
    ・LFO処理のの実装。"はにいいんざすかい"のOPや、フラッシュハイダースの効果音が
    実機の音に近づいた。v1.59

    Copyright(C)2006-2012 Kitao Nakamura.
    改造版・後継版を公開なさるときは必ずソースコードを添付してください。
    その際に事後でかまいませんので、ひとことお知らせいただけると幸いです。
    商的な利用は禁じます。
    あとは「GNU General Public License(一般公衆利用許諾契約書)」に準じます。

                [DEV NOTE]

                MAL   --- 0 - 15 (15 で -0[dB], １減るごとに -3.0 [dB])
                AL   --- 0 - 31 (31 で -0[dB], １減るごとに -1.5 [dB])
                LAL/RAL  --- 0 - 15 (15 で -0[dB], １減るごとに -3.0 [dB])

                次のように解釈しなおす。

                MAL*2  --- 0 - 30 (30 で -0[dB], １減るごとに -1.5 [dB])
                AL   --- 0 - 31 (31 で -0[dB], １減るごとに -1.5 [dB])
                LAL/RAL*2 --- 0 - 30 (30 で -0[dB], １減るごとに -1.5 [dB])


                dB = 20 * log10(OUT/IN)

                dB / 20 = log10(OUT/IN)

                OUT/IN = 10^(dB/20)

                IN(最大出力) を 1.0 とすると、

                OUT = 10^(dB/20)

                                -91 <= -(MAL*2 + AL + LAL(RAL)*2) <= 0

                だから、最も小さい音は、

                    -91 * 1.5 [dB] = -136.5 [dB] = 10^(-136.5/20) ~= 1.496236e-7 [倍]

                となる。

                  1e-7 オーダーの値は、固定小数点で表現しようとすると、小数部だけで
                24 ビット以上必要で、なおかつ１６ビットの音声を扱うためには +16ビット
                だから 24+16 = 40ビット以上必要になる。よって、32 ビットの処理系で
                ＰＣＥの音声を固定小数点で表現するのはつらい。そこで、波形の計算は
                float で行なうことにする。

                  float から出力形式に変換するのはＡＰＵの仕事とする。

                [2004.4.28] やっぱり Sint32 で実装することにした(微小な値は無視する)。

                  ＣＰＵとPSG は同じＩＣにパッケージしてあるのだが、
                実際にはPSG はＣＰＵの１／２のクロックで動作すると考えて良いようだ。
                よって、PSG の動作周波数 Fpsg は、

                    Fpsg = 21.47727 [MHz] / 3 / 2 = 3.579545 [MHz]

                となる。

                たとえば３２サンプルを１周期とする波形が再生されるとき、
                この周波数の周期でサンプルを１つずつ拾い出すと、

                    MPcm = 3579545 / 32 = 111860.78125 [Hz]

                というマジックナンバーが得られる（ファミコンと同じ）。
                ただし、再生周波数が固定では曲の演奏ができないので、
                FRQ なる周波数パラメータを用いて再生周波数を変化させる。
                FRQ はPSG のレジスタに書き込まれる１２ビット長のパラメータで、
                ↑で得られたマジックナンバーの「割る数」になっている。

                上の３２サンプルを１周期とする波形が再生されるとき、
                この波形の周波数 F は、FRQ を用いて、

                    F = MPcm / FRQ [Hz]  (FRQ != 0)

                となる。

                  ＰＣの再生サンプリング周波数が Fpc [Hz] だとすると、
                １周期３２サンプルの波形の再生周波数 F2 は  F2 = Fpc / 32 [Hz]。
                よって、ＰＣの１サンプルに対して、ＰＣＥの１サンプルを拾い出す
                カウンタの進み幅 I は

                    I = F / F2 = 32 * F / Fpc = Fpsg / FRQ / Fpc [単位なし]

                となる。

                [NOISE CHANNEL]

                  擬似ノイズの生成にはＭ系列(maximum length sequence)が用いられる。
                Ｍ系列のビット長は未調査につき不明。
                ここでは仮に１５ビットとして実装を行なう。
                出力は１ビットで、D0 がゼロのときは負の値、１のときは正の値とする。

                ＰＣの１サンプルに対して、ＰＣＥの１サンプルを拾い出す
                カウンタの進み幅 I は、

                    I = Fpsg / 64 / FRQ / Fpc  (FRQ != 0)

                となる。

                [再生クオリティ向上について] 2004.6.22

                  エミュレータでは、PSG のレジスタにデータが書き込まれるまで、
                次に発声すべき音がわからない。レジスタにデータが書き込まれたときに、
                サウンドバッファを更新したいのだけど、あいにく現在の実装では、
                サウンドバッファの更新は別スレッドで行なわれていて、
                エミュレーションスレッドから任意の時間に更新することができない。

                  これまでの再生では、サウンドバッファの更新時のレジスタ設定のみが
                有効だったが、これだと例えばサウンドバッファ更新の合間に一瞬だけ
                出力された音などが無視されてしまう。これは特にＤＤＡモードやノイズが
                リズムパートとして使用される上で問題になる。

                  レジスタに書き込まれた値をきちんと音声出力に反映させるには、
                過去に書き込まれたレジスタの値(いつ、どのレジスタに、何が書き込まれたか)
                を保存しておいて、サウンドバッファ更新時にこれを参照する方法が
                考えられる。どのくらい過去までレジスタの値を保存しておくかは、
                サウンドバッファの長さにもよると思われるが、とりあえずは試行錯誤で
                決めることにする。

                  PSG レジスタへの書き込み動作はエミュレーションスレッドで
                行なわれ、サウンドバッファ更新はその専用スレッドで行なわれる。
                これだと、エミュレーションスレッドがレジスタのキューに書き込みを
                行なっている最中に、サウンドバッファ更新スレッドがキューから
                読み出しを行なってしまい、アクセスが衝突する。この問題を解決するには、

                    １．サウンドバッファの更新を別スレッドで行なわない
                    ２．キューのアクセス部分を排他処理にする

                の２とおりが考えられる。とりあえず２の方法をとることにする。
    */
    public static class HuC6280State {

        private static final int N_CHANNEL = 6;

        /**
         * Kitao更新。
         * PSGはオーバーサンプリングすると響きの美しさが損なわれてしまうので
         * オーバーサンプリングしないようにした。速度的にもアップ。
         */
        private static final double OVERSAMPLE_RATE = 1.0;

        /**
         * Kitao 追加。
         * PSG音量の減少値。* 6.0 は各チャンネル足したぶんを割る意味。
         * 大きいほど音は減る。CDDA が 100% のときにちょうど良いぐらいの音量に合わせよう。
         * v2.19,v2.37,v2.39,v2.62更新
         */
        private static final double PSG_DECLINE = 21.8500 * 6.0;

        /**
         * ※ PSG_DECLINE の値を変更した場合、減退率のベスト値も変更する必要がある。
         * 雀探物語２(マイナスが小さいとPSGが目立ちすぎてADPCMが聴きづらい)，
         * 大魔界村(マイナスが大きいと音篭り),
         * ソルジャーブレイドで、PSG_DECLINE = (14.4701*6.0) で
         * 減退率 -1.0498779900db 前後が飛び抜けていい響き(うちの環境で主観)。
         * モトローダー(マイナスやや大き目がいい),1941(マイナス小さめがいい)なども微妙な値変更で大きく変わる。
         */
        private static final int NOISE_TABLE_VALUE_front = -18;
        /**
         * キレと聴きやすさで-18:-1をベストとした。
         * 最大値が大きい(+に近い)と重い音に。２つの値が離れていると重い音に。
         * フォーメーションサッカー，大魔界村のエンディングのドラムなどで調整。
         * v1.46,v2.40,v2.62更新
         */
        private static final int NOISE_TABLE_VALUE_rear = -1;

        /**  ※VOL_TABLE_DECLINEによってこの値の最適値も変化する。 */
        /**
         * 0.30599899951。Kitao追加。サンプリング音の消音時の音の減退量。
         * ソルジャーブレイド,将棋初心者無用の音声で調整。
         * 基本的にこの値が小さいほうがノイズが減る(逆のケースもある)。v2.40
         * サンプリングドラムの音色が決まるので大事な値。
         * 値が大きすぎるとファイナルソルジャーやソルジャーブレイド,モトローダーなどでドラムがしょぼくなる。
         */
        private static final double SAMPLE_FADE_DECLINE = 0.305998999951;

        public static class PSG {
            /**
             * -1.05809999010 で雀探物語2 OK。
             * Kitao 追加。音量テーブルの減少値。マイナスが大きいほど小さい音が聞こえづらくなる。
             * マイナスが小さすぎると平面的な音になる。
             * v2.19,v2.37,v2.39,v2.40,v2.62,v2.65更新
             */
            private static final double VOL_TABLE_DECLINE = -1.05809999010;

            public int frq;
            public boolean bOn;
            public boolean bDDA;
            public int volume;
            public int volumeL;
            public int volumeR;
            public int outVolumeL;
            public int outVolumeR;
            public int[] wave = new int[32];
            public int waveIndex;
            public int ddaSample;
            public int phase;
            public int deltaPhase;
            public boolean bNoiseOn;
            public int noiseFrq;
            public int deltaNoisePhase;

            private static int[] volumeTable = new int[92];

            /*
             * ボリュームテーブルの作成
             * Kitao 更新。
             * 低音量の音が実機より聞こえづらいので、減退率をVOL_TABLE_DECLINE[db](試行錯誤したベスト値)とし、
             * ノーマライズ処理をするようにした。v1.46
             * おそらく、実機もアンプを通って出力される際にノーマライズ処理されている。
             */
            static {
                volumeTable[0] = 0; // Kitao 追加
                for (int i = 1; i <= 91; i++) {
                    double v = 91 - i;
                    // VOL_TABLE_DECLINE: 小さくしすぎると音が平面的な傾向に。ソルジャーブレイドで調整。v1.46。
                    volumeTable[i] = (int) (32768.0 * Math.pow(10.0, v * VOL_TABLE_DECLINE / 20.0));
                }
            }

            public void mainVolume(int mainVolumeL, int mainVolumeR) {
                outVolumeL = volumeTable[volume + (mainVolumeL + volumeL) * 2];
                outVolumeR = volumeTable[volume + (mainVolumeR + volumeR) * 2];
            }
        }

        public double sampleRate;
        public double psgFrq;
        public double resmplRate;

        public PSG[] psg = new PSG[8]; // 6, 7 is unused
        public int[] ddaFadeOutL = new int[8]; // Kitao追加
        public int[] ddaFadeOutR = new int[8]; // Kitao追加
        public int channel; // 0 - 5;
        public int mainVolumeL; // 0 - 15
        public int mainVolumeR; // 0 - 15
        public int lfoFrq;
        // v1.59から非使用。過去verのステートロードのために残してある。
        public boolean bLfoOn = false;
        public int lfoCtrl;
        // v1.59から非使用。過去verのステートロードのために残してある。
        public int lfoShift = 0;
        //Kitao追加
        public int psgVolumeEffect;
        //Kitao追加
        public double volume;
        //Kitao追加。v1.08
        public double vol;
        public boolean[] bPsgMute = new boolean[8]; // Kitao追加。v1.29

        // for debug purpose
        public byte[] port = new byte[16];

        // Kitao追加。DDA再生中にWaveデータが書き換えられたらTRUE
        public boolean bWaveCrash;
        // はにいいんざすかいパッチ用。v2.60
        public boolean bHoneyInTheSky;

        private static int[] noiseTable = new int[32768];

        /*
         * ノイズテーブルの作成
         */
        static {
            int reg = 0x100;

            for (int i = 0; i < 32768; i++) {
                int bit0 = reg & 1;
                int bit1 = (reg & 2) >> 1;
                int bit14 = (bit0 ^ bit1);
                reg >>= 1;
                reg |= (bit14 << 14);
                // Kitao更新。 ノイズのボリュームと音質を調整した。
                noiseTable[i] = (bit0 != 0) ? NOISE_TABLE_VALUE_front : NOISE_TABLE_VALUE_rear;
            }
        }

        /**
         * PSG ポートの書き込みに対する動作を記述します。
         */
        private void writeReg(byte reg, byte data) {
            int i;
            int frq; // Kitao 追加
            PSG psgChn;

            this.port[reg & 15] = data;

            switch (reg & 15) {
            case 0: // register select
                this.channel = data & 7;
                break;

            case 1: // main volume
                this.mainVolumeL = (data >> 4) & 0x0F;
                this.mainVolumeR = data & 0x0F;

                /* LMAL, RMAL は全チャネルの音量に影響する */
                for (i = 0; i < N_CHANNEL; i++) {
                    psgChn = this.psg[i];
                    psgChn.mainVolume(this.mainVolumeL, this.mainVolumeR);
                }
                break;

            case 2: // frequency low
                psgChn = this.psg[this.channel];
                psgChn.frq &= ~(int) 0xFF;
                psgChn.frq |= data;
                // Kitao更新。update_frequencyは、速度アップのためサブルーチンにせず直接実行するようにした。
                frq = (psgChn.frq - 1) & 0xFFF;
                if (frq != 0)
                    psgChn.deltaPhase = (int) ((65536.0 * 256.0 * 8.0 * this.resmplRate) / (double) frq + 0.5); //Kitao更新。速度アップのためfrq以外は定数計算にした。精度向上のため、先に値の小さいOVERSAMPLE_RATEのほうで割るようにした。+0.5は四捨五入で精度アップ。プチノイズ軽減のため。
                else
                    psgChn.deltaPhase = 0;
                break;

            case 3: // frequency high
                psgChn = this.psg[this.channel];
                psgChn.frq &= ~(int) 0xF00;
                psgChn.frq |= (int) ((data & 0x0F) << 8);
                // Kitao更新。update_frequencyは、速度アップのためサブルーチンにせず直接実行するようにした。
                frq = (psgChn.frq - 1) & 0xFFF;
                if (frq != 0)
                    psgChn.deltaPhase = (int) ((65536.0 * 256.0 * 8.0 * this.resmplRate) / (double) frq + 0.5); //Kitao更新。速度アップのためfrq以外は定数計算にした。精度向上のため、先に値の小さいOVERSAMPLE_RATEのほうで割るようにした。+0.5は四捨五入で精度アップ。プチノイズ軽減のため。
                else
                    psgChn.deltaPhase = 0;
                break;

            case 4: // ON, DDA, AL
                psgChn = this.psg[this.channel];
                if (this.bHoneyInTheSky) { //はにいいんざすかいのポーズ時に、微妙なボリューム調整タイミングの問題でプチノイズが載ってしまうので、現状はパッチ処理で対応。v2.60更新
                    if ((psgChn.bOn) && (data == 0)) { //発声中にdataが0の場合、LRボリュームも0にリセット。はにいいんざすかいのポーズ時のノイズが解消。(data & 0x1F)だけが0のときにリセットすると、サイレントデバッガーズ等でNG。発声してない時にリセットするとアトミックロボでNG。ｖ2.55
                        //System.err.printf("test %X %X %X %X",this.Channel,psgChn.bOn,this.MainVolumeL,this.MainVolumeR);
                        if ((this.mainVolumeL & 1) == 0) // メインボリュームのbit0が0のときだけ処理(はにいいんざすかいでイレギュラーな0xE。他のゲームは0xF。※ヘビーユニットも0xEだった)。これがないとミズバク大冒険で音が出ない。実機の仕組みと同じかどうかは未確認。v2.53追加
                            psgChn.volumeL = 0;
                        if ((this.mainVolumeR & 1) == 0) // 右チャンネルも同様とする
                            psgChn.volumeR = 0;
                    }
                }

                psgChn.bOn = ((data & 0x80) != 0);
                if ((psgChn.bDDA) && ((data & 0x40) == 0)) { // DDAからWAVEへ切り替わるとき or DDAから消音するとき
                    // Kitao追加。DDAはいきなり消音すると目立つノイズが載るのでフェードアウトする。
                    this.ddaFadeOutL[this.channel] = (int) ((double) (psgChn.ddaSample * psgChn.outVolumeL) *
                            ((1 + (1 >> 3) + (1 >> 4) + (1 >> 5) + (1 >> 7) + (1 >> 12) + (1 >> 14) + (1 >> 15)) * SAMPLE_FADE_DECLINE)); //元の音量。v2.65更新
                    this.ddaFadeOutR[this.channel] = (int) ((double) (psgChn.ddaSample * psgChn.outVolumeR) *
                            ((1 + (1 >> 3) + (1 >> 4) + (1 >> 5) + (1 >> 7) + (1 >> 12) + (1 >> 14) + (1 >> 15)) * SAMPLE_FADE_DECLINE));

                }
                psgChn.bDDA = ((data & 0x40) != 0);

                // Kitao追加。dataのbit7,6が01のときにWaveインデックスをリセットする。
                // DDAモード時にWaveデータを書き込んでいた場合はここでWaveデータを修復（初期化）する。ファイヤープロレスリング。
                if ((data & 0xC0) == 0x40) {
                    psgChn.waveIndex = 0;
                    if (this.bWaveCrash) {
                        for (i = 0; i < 32; i++)
                            psgChn.wave[i] = -14; // Waveデータを最小値で初期化
                        this.bWaveCrash = false;
                    }
                }

                psgChn.volume = data & 0x1F;
                psgChn.mainVolume(this.mainVolumeL, this.mainVolumeR);
                break;

            case 5: // LAL, RAL
                psgChn = this.psg[this.channel];
                psgChn.volumeL = (data >> 4) & 0xF;
                psgChn.volumeR = data & 0xF;
                psgChn.mainVolume(this.mainVolumeL, this.mainVolumeR);
                break;

            case 6: // wave data
                // Kitao 更新。DDAモードのときもWaveデータを更新するようにした。v0.63。ファイヤープロレスリング
                psgChn = this.psg[this.channel];
                data &= 0x1F;
                this.bWaveCrash = false; // Kitao追加
                if (!psgChn.bOn) { // Kitao追加。音を鳴らしていないときだけWaveデータを更新する。v0.65。F1トリプルバトルのエンジン音。
                    psgChn.wave[psgChn.waveIndex++] = 17 - data; // 17。Kitao更新。一番心地よく響く値に。ミズバク大冒険，モトローダー，ドラゴンスピリット等で調整。
                    psgChn.waveIndex &= 0x1F;
                }
                if (psgChn.bDDA) {
                    // Kitao更新。ノイズ軽減のため6より下側の値はカットするようにした。v0.59
                    if (data < 6) // サイバーナイトで6に決定
                        data = 6; // ノイズが多いので小さな値はカット
                    psgChn.ddaSample = 11 - data; //サイバーナイトで11に決定。ドラムの音色が最適。v0.74

                    if (!psgChn.bOn) // DDAモード時にWaveデータを書き換えた場合
                        this.bWaveCrash = true;
                }
                break;

            case 7: // noise on, noise frq
                if (this.channel >= 4) {
                    psgChn = this.psg[this.channel];
                    psgChn.bNoiseOn = ((data & 0x80) != 0);
                    psgChn.noiseFrq = (int) (0x1F - (data & 0x1F));
                    if (psgChn.noiseFrq == 0)
                        psgChn.deltaNoisePhase = (int) ((2048.0 * this.resmplRate) + 0.5); // Kitao更新
                    else
                        psgChn.deltaNoisePhase = (int) ((2048.0 * this.resmplRate) / (double) psgChn.noiseFrq + 0.5); //Kitao更新
                }
                break;

            case 8: // LFO frequency
                this.lfoFrq = data;
                // Kitaoテスト用
                // System.err.printf("LFO Frq = %X",this.LfoFrq);
                break;

            case 9: // LFO control
                // Kitao更新。シンプルに実装してみた。実機で同じ動作かは未確認。はにいいんざすかいの音が似るように実装。v1.59
                if ((data & 0x80) != 0) { // bit7を立てて呼ぶと恐らくリセット
                    this.psg[1].phase = 0; // LfoFrqは初期化しない。はにいいんざすかい。
                    // System.err.printf("LFO control = %X",data);
                }
                this.lfoCtrl = data & 7; // ドロップロックほらホラで 5 が使われる。v1.61更新
                if ((this.lfoCtrl & 4) != 0)
                    this.lfoCtrl = 0; // ドロップロックほらホラ。実機で聴いた感じはLFOオフと同じ音のようなのでbit2が立っていた(負の数扱い？)ら0と同じこととする。
                //System.err.printf("LFO control = %X,  Frq =%X",data,this.LfoFrq);
                break;

            default:    // invalid write
                break;
            }
        }

        // Kitao追加
        private void set_VOL() {
            if (this.psgVolumeEffect == 0)
                //this.VOL = 0.0; //ミュート
                this.vol = 1.0 / 128.0;
            else if (this.psgVolumeEffect == 3)
                // 3/4。v1.29追加
                this.vol = this.volume / (OVERSAMPLE_RATE * 4.0 / 3.0);
            else
                // Kitao追加。_PsgVolumeEffect=ボリューム調節効果。
                this.vol = this.volume / (OVERSAMPLE_RATE * this.psgVolumeEffect);
        }

        /**
         * PSG の出力をミックスします。
         *
         * @param pDst 出力先バッファ Kitao更新。PSG専用バッファにしたためSint16に。
         * @param nSample 書き出すサンプル数
         */
        private void mix(int[][] pDst, int nSample) {
            // Kitao 追加
            int sample;
            int lfo;
            // Kitao追加。6chぶんのサンプルを足していくためのバッファ。精度を維持するために必要。
            // 6chぶん合計が終わった後に、これをSint16に変換して書き込むようにした。
            int sampleAllL;
            // Kitao 追加。上のＲチャンネル用
            int sampleAllR;
            // Kitao 追加。DDA音量,ノイズ音量計算用
            int smp;
            int[] bufL = pDst[0];
            int[] bufR = pDst[1];

            for (int j = 0; j < nSample; j++) {
                sampleAllL = 0;
                sampleAllR = 0;
                for (int i = 0; i < N_CHANNEL; i++) {
                    PSG psgChn = this.psg[i];

                    if ((psgChn.bOn) && ((i != 1) || (this.lfoCtrl == 0)) && (!this.bPsgMute[i])) { // Kitao更新
                        if (psgChn.bDDA) {
                            smp = psgChn.ddaSample * psgChn.outVolumeL;
                            sampleAllL += smp + (smp >> 3) + (smp >> 4) + (smp >> 5) + (smp >> 7) + (smp >> 12) + (smp >> 14) + (smp >> 15); //Kitao更新。サンプリング音の音量を実機並みに調整。v2.39,v2.40,v2.62,v2.65再調整した。
                            smp = psgChn.ddaSample * psgChn.outVolumeR;
                            sampleAllR += smp + (smp >> 3) + (smp >> 4) + (smp >> 5) + (smp >> 7) + (smp >> 12) + (smp >> 14) + (smp >> 15); //Kitao更新。サンプリング音の音量を実機並みに調整。v2.39,v2.40,v2.62,v2.65再調整した。
                        } else if (psgChn.bNoiseOn) {
                            sample = noiseTable[psgChn.phase >> 17];

                            if (psgChn.noiseFrq == 0) { // Kitao追加。noiseFrq=0(dataに0x1Fが書き込まれた)のときは音量が通常の半分とした。（ファイヤープロレスリング３、パックランド、桃太郎活劇、がんばれゴルフボーイズなど）
                                smp = sample * psgChn.outVolumeL;
                                sampleAllL += (smp >> 1) + (smp >> 12) + (smp >> 14); //(1/2 + 1/4096 + (1/32768 + 1/32768))
                                smp = sample * psgChn.outVolumeR;
                                sampleAllR += (smp >> 1) + (smp >> 12) + (smp >> 14);
                            } else { // 通常
                                smp = sample * psgChn.outVolumeL;
                                sampleAllL += smp + (smp >> 11) + (smp >> 14) + (smp >> 15); // Kitao更新。ノイズの音量を実機並みに調整(1 + 1/2048 + 1/16384 + 1/32768)。この"+1/32768"で絶妙(主観。大魔界村,ソルジャーブレイドなど)になる。v2.62更新
                                smp = sample * psgChn.outVolumeR;
                                sampleAllR += smp + (smp >> 11) + (smp >> 14) + (smp >> 15); // Kitao更新。ノイズの音量を実機並みに調整
                            }

                            psgChn.phase += psgChn.deltaNoisePhase; //Kitao更新
                        } else if (psgChn.deltaPhase != 0) {
                            // Kitao更新。オーバーサンプリングしないようにした。
                            sample = psgChn.wave[psgChn.phase >> 27];
                            if (psgChn.frq < 128)
                                sample -= sample >> 2; // 低周波域の音量を制限。ブラッドギアのスタート時などで実機と同様の音に。ソルジャーブレイドなども実機に近くなった。v2.03

                            sampleAllL += sample * psgChn.outVolumeL; // Kitao更新
                            sampleAllR += sample * psgChn.outVolumeR; // Kitao更新

                            // Kitao更新。Lfoオンが有効になるようにし、Lfoの掛かり具合を実機に近づけた。v1.59
                            if ((i == 0) && (this.lfoCtrl > 0)) {
                                // _LfoCtrlが1のときに0回シフト(そのまま)で、はにいいんざすかいが実機の音に近い。
                                // _LfoCtrlが3のときに4回シフトで、フラッシュハイダースが実機の音に近い。
                                lfo = this.psg[1].wave[this.psg[1].phase >> 27] << (int) ((this.lfoCtrl - 1) << 1); //v1.60更新
                                this.psg[0].phase += (int) ((double) (65536.0 * 256.0 * 8.0 * this.resmplRate) / (double) (this.psg[0].frq + lfo) + 0.5);
                                this.psg[1].phase += (int) ((double) (65536.0 * 256.0 * 8.0 * this.resmplRate) / (double) (this.psg[1].frq * this.lfoFrq) + 0.5); //v1.60更新
                            } else
                                psgChn.phase += psgChn.deltaPhase;
                        }
                    }
                    // Kitao追加。DDA消音時はノイズ軽減のためフェードアウトで消音する。
                    // ベラボーマン(「わしがばくだはかせじゃ」から数秒後)やパワーテニス(タイトル曲終了から数秒後。点数コール)，将棋初心者無用(音声)等で効果あり。
                    if (this.ddaFadeOutL[i] > 0)
                        --this.ddaFadeOutL[i];
                    else if (this.ddaFadeOutL[i] < 0)
                        ++this.ddaFadeOutL[i];
                    if (this.ddaFadeOutR[i] > 0)
                        --this.ddaFadeOutR[i];
                    else if (this.ddaFadeOutR[i] < 0)
                        ++this.ddaFadeOutR[i];
                    sampleAllL += this.ddaFadeOutL[i];
                    sampleAllR += this.ddaFadeOutR[i];
                }
                //Kitao更新。6ch合わさったところで、ボリューム調整してバッファに書き込む。
                sampleAllL = (int) ((double) sampleAllL * this.vol);
                //if ((sampleAllL>32767)||(sampleAllL<-32768)) System.err.printf("PSG Sachitta!");//test用
                //  if (sampleAllL> 32767) sampleAllL= 32767; // Volをアップしたのでサチレーションチェックが必要。v2.39
                //  if (sampleAllL<-32768) sampleAllL=-32768; //  パックランドでUFO等にやられたときぐらいで、通常のゲームでは起こらない。音量の大きなビックリマンワールドもOK。パックランドも通常はOKでサチレーションしたときでもわずかなので音質的に大丈夫。
                //  なので音質的には、PSGを２つのDirectXチャンネルに分けて鳴らすべき(処理は重くなる)だが、現状はパックランドでもサチレーション処理だけで音質的に問題なし(速度優先)とする。
                sampleAllR = (int) ((double) sampleAllR * this.vol);
                //if ((sampleAllR>32767)||(sampleAllR<-32768)) System.err.printf("PSG Satitta!");//test用
                //  if (sampleAllR> 32767) sampleAllR= 32767; // Volをアップしたのでサチレーションチェックが必要。v2.39
                //  if (sampleAllR<-32768) sampleAllR=-32768; //
                bufL[j] = sampleAllL << 1;
                bufR[j] = sampleAllR << 1;
            }
        }

        // Kitao更新
        private void reset() {
            int i, j;

            for (i = 0; i < 8; i++) {
                this.psg[i] = new PSG();
            }

            this.ddaFadeOutL = new int[8]; // Kitao追加
            this.ddaFadeOutR = new int[8]; // Kitao追加
            this.mainVolumeL = 0;
            this.mainVolumeR = 0;
            this.lfoFrq = 0;
            this.lfoCtrl = 0;
            this.channel = 0; // Kitao追加。v2.65
            this.bWaveCrash = false; //Kitao追加

            // Kitao更新。v0.65．waveデータを初期化。
            for (i = 0; i < N_CHANNEL; i++)
                for (j = 0; j < 32; j++)
                    this.psg[i].wave[j] = -14; // 最小値で初期化。ファイプロ，フォーメーションサッカー'90，F1トリプルバトルで必要。
            for (j = 0; j < 32; j++)
                this.psg[3].wave[j] = 17; // ch3は最大値で初期化。F1トリプルバトル。v2.65
        }

        /**
         * PSG を初期化します。
         */
        private HuC6280State(int clock, int sampleRate) {

            this.psgFrq = clock & 0x7FFFFFFF;
            setHoneyInTheSky(((clock >> 31) & 0x01) != 0);

            this.psgVolumeEffect = 0;
            this.volume = 0;
            this.vol = 0.0;

            setVolume(); // Kitao追加

            reset();

            this.sampleRate = sampleRate;
            this.resmplRate = this.psgFrq / OVERSAMPLE_RATE / this.sampleRate;
        }

        /**
         * PSG を破棄します。
         */
        protected void finalize() {
        }

        /**
         * PSG ポートの読み出しに対する動作を記述します。
         */
        private byte read(int regNum) {
            if (regNum == 0)
                return (byte) this.channel;

            return this.port[regNum & 15];
        }

        /**
         * PSG ポートの書き込みに対する動作を記述します。
         */
        private void write(int regNum, byte data) {
            writeReg((byte) regNum, data);
        }

        // Kitao追加。PSGのボリュームも個別に設定可能にした。
        private void setVolume() {
            this.volume = 1.0 / PSG_DECLINE;
            set_VOL();
        }

        // Kitao 追加
        private void resetVolumeReg() {
            this.mainVolumeL = 0;
            this.mainVolumeR = 0;
            for (int i = 0; i < N_CHANNEL; i++) {
                this.psg[i].volume = 0;
                this.psg[i].outVolumeL = 0;
                this.psg[i].outVolumeR = 0;
                this.ddaFadeOutL[i] = 0;
                this.ddaFadeOutR[i] = 0;
            }
        }

        // Kitao 追加
        private void setMutePsgChannel(int num, boolean bMute) {
            this.bPsgMute[num] = bMute;
            if (bMute) {
                this.ddaFadeOutL[num] = 0;
                this.ddaFadeOutR[num] = 0;
            }
        }

        private void setMuteMask(int muteMask) {
            for (byte curChn = 0x00; curChn < N_CHANNEL; curChn++)
                setMutePsgChannel(curChn, ((muteMask >> curChn) & 0x01) != 0);
        }

        // Kitao追加
        private boolean getMutePsgChannel(int num) {
            return this.bPsgMute[num];
        }

        // Kitao追加。v2.60
        private void setHoneyInTheSky(boolean bHoneyInTheSky) {
            this.bHoneyInTheSky = bHoneyInTheSky;
        }
    }

    private HuC6280State[] chip = new HuC6280State[2];
    private static final int DefaultHuC6280ClockValue = 3579545;

    @Override
    public String getName() {
        return "HuC6280";
    }

    @Override
    public String getShortName() {
        return "HuC8";
    }

    public OotakePsg() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
        //0..Main
    }

    @Override
    public int start(byte chipID, int clock) {
        start(chipID, clock, DefaultHuC6280ClockValue);

        return clock;
    }

    @Override
    public int start(byte chipID, int samplingRate, int clockValue, Object... option) {
        chip[chipID] = new HuC6280State(clockValue, samplingRate);

        return samplingRate;
    }

    @Override
    public void stop(byte chipID) {
        if (chip[chipID] == null) return;
        chip[chipID] = null;
    }

    @Override
    public void reset(byte chipID) {
        if (chip[chipID] == null) return;
        chip[chipID].reset();
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        if (chip[chipID] == null) return;
        chip[chipID].mix(outputs, samples);

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    private int HuC6280_Write(byte chipID, byte adr, byte data) {
        if (chip[chipID] == null) return 0;
        chip[chipID].writeReg(adr, data);
        return 0;
    }

    public byte HuC6280_Read(byte chipID, byte adr) {
        if (chip[chipID] == null) return 0;
        return chip[chipID].read(adr);
    }

    public void HuC6280_SetMute(byte chipID, int val) {
        if (chip[chipID] == null) return;

        chip[chipID].setMuteMask(val);
    }

    public void SetVolume(byte chipID, int db) {
        if (chip[chipID] == null) return;
    }

    public HuC6280State GetState(byte chipID) {
        return chip[chipID];
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        return HuC6280_Write(chipID, (byte) adr, (byte) data);
    }
}
