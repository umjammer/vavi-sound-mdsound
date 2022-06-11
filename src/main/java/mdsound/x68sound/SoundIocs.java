package mdsound.x68sound;

import mdsound.fmgen.OPM;


public class SoundIocs {

    private X68Sound x68Sound = null;

    public SoundIocs(X68Sound x68Sound) {
        this.x68Sound = x68Sound;
    }

    /**
     * 16bit値のバイトの並びを逆にして返す
     */
    private static int bSwapW(int data) {
        return (data >> 8) + ((data & 0xff) << 8);
    }

    /**
     * 32bit値のバイトの並びを逆にして返す
     */
    private static int bSwapL(int adrs) {
        return (adrs >> 24) + ((adrs & 0xff0000) >> 8) + ((adrs & 0xff00) << 8) + ((adrs & 0xff) << 24);
    }

    /**
     * $02:adpcmout $12:adpcmaot $22:adpcmlot $32:adpcmcot
     */
    private byte adpcmStat = 0;
    /**
     * OPM レジスタ $1B の内容
     */
    private byte opmReg1B = 0;
    private byte dmaErrCode = 0;

    private int adpcmCotAdrs;
    private int adpcmCotLen;

    /**
     * OPMのBUSY待ち
     */
    private void opmWait() {
        while ((x68Sound.opmPeek() & 0x80) != 0) ;
    }

    /**
     * IOCS _OPMSET ($68) の処理
     *
     * @param addr OPMレジスタナンバー(0～255)
     * @param data データ(0～255)
     */
    public void opmSet(int addr, int data) {
        if (addr == 0x1B) {
            opmReg1B = (byte) ((opmReg1B & 0xC0) | (data & 0x3F));
            data = opmReg1B;
        }

        opmWait();
        x68Sound.opmReg((byte) addr);
        opmWait();
        x68Sound.opmPoke((byte) data);
    }

    /**
     * IOCS _OPMSNS ($69) の処理
     *
     * @return bit 0 : タイマーAオーバーフローのとき1になる
     * bit 1 : タイマーBオーバーフローのとき1になる
     * bit 7 : 0ならばデータ書き込み可能
     */
    public int opmSns() {
        return x68Sound.opmPeek();
    }

    public Runnable opmIntProc = null;  // OPMのタイマー割り込み処理アドレス

    /**
     * IOCS _OPMINTST ($6A) の処理
     *
     * @param addr 割り込み処理アドレス, 0のときは割り込み禁止
     * @return 割り込みが設定された場合は 0
     * 既に割り込みが設定されている場合はその割り込み処理アドレスを返す
     */
    public Runnable opmIntSt(Runnable addr) {
        if (addr == null) { // 引数が0の時は割り込みを禁止する
            opmIntProc = null;
            x68Sound.opmInt(opmIntProc);
            return null;
        }
        if (opmIntProc != null) { // 既に設定されている場合は、その処理アドレスを返す
            return opmIntProc;
        }
        opmIntProc = addr;
        x68Sound.opmInt(opmIntProc); // OPMの割り込み処理アドレスを設定
        return null;
    }

    /**
     * DMA転送終了割り込み処理ルーチン
     */
    private void dmaIntProc() {
        if (adpcmStat == 0x32 && (x68Sound.dmaPeek((byte) 0x00) & 0x40) != 0) { // コンティニューモード時の処理
            x68Sound.dmaPoke((byte) 0x00, (byte) 0x40);   // BTCビットをクリア
            if (adpcmCotLen > 0) {
                int dmalen;
                dmalen = adpcmCotLen;
                if (dmalen > 0xFF00) { // 1度に転送できるバイト数は0xFF00
                    dmalen = 0xFF00;
                }
                x68Sound.dmaPokeL((byte) 0x1C, adpcmCotAdrs); // BARに次のDMA転送アドレスをセット
                x68Sound.dmaPokeW((byte) 0x1A, dmalen); // BTCに次のDMA転送バイト数をセット
                adpcmCotAdrs += dmalen;
                adpcmCotLen -= dmalen;

                x68Sound.dmaPoke((byte) 0x07, (byte) 0x48);   // コンティニューオペレーション設定
            }
            return;
        }
        if ((adpcmStat & 0x80) == 0) {
            x68Sound.ppiCtrl((byte) 0x01); // ADPCM 右出力 OFF
            x68Sound.ppiCtrl((byte) 0x03); // ADPCM 左出力 OFF
            x68Sound.adpcmPoke((byte) 0x01); // ADPCM 再生動作停止
        }
        adpcmStat = 0;
        x68Sound.dmaPoke((byte) 0x00, (byte) 0xFF); // DMA CSR の全ビットをクリア
    }

    /**
     * DMAエラー割り込み処理ルーチン
     */
    private void dmaErrIntProc() {
        dmaErrCode = x68Sound.dmaPeek((byte) 0x01); // エラーコードを DmaErrCode に保存

        x68Sound.ppiCtrl((byte) 0x01); // ADPCM右出力OFF
        x68Sound.ppiCtrl((byte) 0x03); // ADPCM左出力OFF
        x68Sound.adpcmPoke((byte) 0x01); // ADPCM再生動作停止

        adpcmStat = 0;
        x68Sound.dmaPoke((byte) 0x00, (byte) 0xFF);   // DMA CSR の全ビットをクリア
    }

    private static final byte[] PANTBL = new byte[] {3, 1, 2, 0};

    /**
     * サンプリング周波数とPANを設定してDMA転送を開始するルーチン
     *
     * @param mode サンプリング周波数 * 256 + PAN
     * @param ccr  DMA CCR に書き込むデータ
     */
    private void setAdpcmMode(int mode, byte ccr) {
        if (mode >= 0x0200) {
            mode -= 0x0200;
            opmReg1B &= 0x7F; // ADPCMのクロックは8MHz
        } else {
            opmReg1B |= 0x80; // ADPCMのクロックは4MHz
        }
        opmWait();
        x68Sound.opmReg((byte) 0x1B);
        opmWait();
        x68Sound.opmPoke(opmReg1B); // ADPCMのクロック設定(8or4MHz)
        byte ppiReg;
        ppiReg = (byte) (((mode >> 6) & 0x0C) | PANTBL[mode & 3]);
        ppiReg |= (byte) (x68Sound.ppiPeek() & 0xF0);
        x68Sound.dmaPoke((byte) 0x07, ccr); // DMA転送開始
        x68Sound.ppiPoke(ppiReg); // サンプリングレート＆PANをPPIに設定
    }

    /**
     * adpcmOut のメインルーチン
     *
     * @param stat ADPCM を停止させずに続けて DMA 転送を行う場合は $80
     *             DMA 転送終了後 ADPCM を停止させる場合は $00
     * @param len  DMA 転送バイト数
     * @param adrs DMA 転送アドレス
     */
    private void adpcmOutMain(byte stat, int mode, int len, int adrs) {
        while (adpcmStat != 0) ; // DMA転送終了待ち
        adpcmStat = (byte) (stat + 2);
        x68Sound.dmaPoke((byte) 0x05, (byte) 0x32);   // DMA OCR をチェイン動作なしに設定

        x68Sound.dmaPoke((byte) 0x00, (byte) 0xFF);   // DMA CSR の全ビットをクリア
        x68Sound.dmaPokeL((byte) 0x0C, adrs);  // DMA MAR にDMA転送アドレスをセット
        x68Sound.dmaPokeW((byte) 0x0A, len);   // DMA MTC にDMA転送バイト数をセット
        setAdpcmMode(mode, (byte) 0x88);   // サンプリング周波数とPANを設定してDMA転送開始

        x68Sound.adpcmPoke((byte) 0x02);   // ADPCM再生開始
    }

    /**
     * adpcmOut ($60) の処理
     *
     * @param addr ADPCM データアドレス
     * @param mode サンプリング周波数 (0～4) * 256 + PAN (0～3)
     * @param len  ADPCM データのバイト数
     */
    public void adpcmOut(int addr, int mode, int len) {
        int dmaLen;
        int dmaAdrsPtr = addr;
        while (adpcmStat != 0) ; // DMA転送終了待ち
        while (len > 0x0000FF00) {   // ADPCMデータが0xFF00バイト以上の場合は
            dmaLen = 0x0000FF00;    // 0xFF00バイトずつ複数回に分けてDMA転送を行う
            adpcmOutMain((byte) 0x80, mode, dmaLen, dmaAdrsPtr);
            dmaAdrsPtr += dmaLen;
            len -= dmaLen;
        }
        adpcmOutMain((byte) 0x00, mode, len, dmaAdrsPtr);
    }

    /**
     * IOCS _ADPCMAOT ($62) の処理
     *
     * @param tblPtr アレイチェインテーブルのアドレス
     * @param mode   サンプリング周波数(0～4)*256+PAN(0～3)
     * @param cnt    アレイチェインテーブルのブロック数
     */
    public void adpcmAot(int tblPtr, int mode, int cnt) {
        while (adpcmStat != 0) ;    // DMA転送終了待ち

        adpcmStat = 0x12;
        x68Sound.dmaPoke((byte) 0x05, (byte) 0x3A);   // DMA OCR をアレイチェイン動作に設定

        x68Sound.dmaPoke((byte) 0x00, (byte) 0xFF);   // DMA CSR の全ビットをクリア
        x68Sound.dmaPokeL((byte) 0x1C, tblPtr);   // DMA BAR にアレイチェインテーブルアドレスをセット
        x68Sound.dmaPokeW((byte) 0x1A, (int) cnt);   // DMA BTC にアレイチェインテーブルの個数をセット
        setAdpcmMode(mode, (byte) 0x88);   // サンプリング周波数とPANを設定してDMA転送開始

        x68Sound.adpcmPoke((byte) 0x02);   // ADPCM再生開始
    }

    /**
     * IOCS _ADPCMAOT ($64) の処理
     *
     * @param tblPtr リンクアレイチェインテーブルのアドレス
     * @param mode   サンプリング周波数 (0～4) * 256 + PAN (0～3)
     */
    public void adpcmLot(int tblPtr, int mode) {
        while (adpcmStat != 0) ;    // DMA転送終了待ち

        adpcmStat = 0x22;
        x68Sound.dmaPoke((byte) 0x05, (byte) 0x3E);   // DMA OCR をリンクアレイチェイン動作に設定

        x68Sound.dmaPoke((byte) 0x00, (byte) 0xFF);   // DMA CSR の全ビットをクリア
        x68Sound.dmaPokeL((byte) 0x1C, tblPtr);   // DMA BAR にリンクアレイチェインテーブルアドレスをセット
        setAdpcmMode(mode, (byte) 0x88);   // サンプリング周波数とPANを設定してDMA転送開始

        x68Sound.adpcmPoke((byte) 0x02);   // ADPCM再生開始
    }

    /**
     * コンティニューモードを利用して ADPCM 出力を行うサンプル
     * IOCS _ADPCMOUT と同じ処理を行うが、データバイト数が 0xFF00 バイト以上でも
     * すぐにリターンする。
     *
     * @param addr ADPCMデータアドレス
     * @param mode サンプリング周波数(0～4)*256+PAN(0～3)
     * @param len  ADPCMデータのバイト数
     */
    public void adpcmCot(int addr, int mode, int len) {
        int dmaLen;
        adpcmCotAdrs = addr;
        adpcmCotLen = len;
        while (adpcmStat != 0) ; // DMA転送終了待ち
        adpcmStat = 0x32;

        x68Sound.dmaPoke((byte) 0x05, (byte) 0x32);   // DMA OCR をチェイン動作なしに設定

        dmaLen = adpcmCotLen;
        if (dmaLen > 0xFF00) {   // ADPCMデータが0xFF00バイト以上の場合は
            dmaLen = 0xFF00;    // 0xFF00バイトずつ複数回に分けてDMA転送を行う
        }

        x68Sound.dmaPoke((byte) 0x00, (byte) 0xFF);   // DMA CSR の全ビットをクリア
        x68Sound.dmaPokeL((byte) 0x0C, adpcmCotAdrs); // DMA MAR にDMA転送アドレスをセット
        x68Sound.dmaPokeW((byte) 0x0A, dmaLen);    // DMA MTC にDMA転送バイト数をセット
        adpcmCotAdrs += dmaLen;
        adpcmCotLen -= dmaLen;
        if (adpcmCotLen <= 0) {
            setAdpcmMode(mode, (byte) 0x88);   // データバイト数が0xFF00以下の場合は通常転送
        } else {
            dmaLen = adpcmCotLen;
            if (dmaLen > 0xFF00) {
                dmaLen = 0xFF00;
            }
            x68Sound.dmaPokeL((byte) 0x1C, adpcmCotAdrs); // BARに次のDMA転送アドレスをセット
            x68Sound.dmaPokeW((byte) 0x1A, dmaLen);    // BTCに次のDMA転送バイト数をセット
            adpcmCotAdrs += dmaLen;
            adpcmCotLen -= dmaLen;
            setAdpcmMode(mode, (byte) 0xC8);   // DMA CNTビットを1にしてDMA転送開始
        }

        x68Sound.adpcmPoke((byte) 0x02); // ADPCM再生開始
    }

    /**
     * IOCS _ADPCMSNS ($66) の処理
     *
     * @return 0: 何もしていない
     * $02: _iocs_adpcmout で出力中
     * $12: _iocs_adpcmaot で出力中
     * $22: _iocs_adpcmlot で出力中
     * $32: _iocs_adpcmcot で出力中
     */
    public int adpcmSns() {
        return (adpcmStat & 0x7F);
    }

    /**
     * IOCS _ADPCMMOD ($67) の処理
     *
     * @param mode 0: ADPCM再生 終了
     *             1: ADPCM再生 一時停止
     *             2: ADPCM再生 再開
     */
    public void adpcmMod(int mode) {
        switch (mode) {
        case 0:
            adpcmStat = 0;
            x68Sound.ppiCtrl((byte) 0x01); // ADPCM右出力OFF
            x68Sound.ppiCtrl((byte) 0x03); // ADPCM左出力OFF
            x68Sound.adpcmPoke((byte) 0x01);   // ADPCM再生動作停止
            x68Sound.dmaPoke((byte) 0x07, (byte) 0x10);   // DMA SAB=1 (ソフトウェアアボート)
            break;
        case 1:
            x68Sound.dmaPoke((byte) 0x07, (byte) 0x20);   // DMA HLT=1 (ホルトオペレーション)
            break;
        case 2:
            x68Sound.dmaPoke((byte) 0x07, (byte) 0x08);   // DMA HLT=0 (ホルトオペレーション解除)
            break;
        }
    }

    /**
     * IOCSコールの初期化
     * DMAの割り込みを設定する
     */
    public void init() {
        x68Sound.dmaInt(this::dmaIntProc);
        x68Sound.dmaErrInt(this::dmaErrIntProc);
    }
}

