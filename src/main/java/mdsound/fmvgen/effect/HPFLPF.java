
package mdsound.fmvgen.effect;

/**
 * フィルタークラス
 *
 * @see "https://vstcpp.wpblog.jp/?page_id=728"
 */
public class HPFLPF {
    // エフェクターのパラメーター
    private int clock;

    private int maxCh;

    private ChInfo[] chInfo = null;

    private float[] fBuf = new float[] {
            0f, 0f
    };

    private int currentCh = 0;

    private static class ChInfo {
        public Filter highPassL = new Filter();
        public Filter highPassR = new Filter();
        public Filter lowPassL = new Filter();
        public Filter lowPassR = new Filter();

        public boolean hsw = false;
        public float hFreq = 1000f;
        public float hQ = (float) (1.0f / Math.sqrt(2.0f));
        public boolean lsw = false;
        public float lFreq = 300f;
        public float lQ = (float) (1.0f / Math.sqrt(2.0f));
    }

    public HPFLPF(int clock, int maxCh) {
        this.clock = clock;
        this.maxCh = maxCh;
        init();
    }

    public void init() {
        chInfo = new ChInfo[maxCh];
        for (int i = 0; i < chInfo.length; i++) {
            chInfo[i] = new ChInfo();

            // 内部変数
            // 高音域のみ通す(低音域をカットする)フィルタ設定(左右分)
            // カットする周波数の目安は20Hz～300Hz程度
            // 増幅量が大きくなれば、カットオフ周波数も大きくするとよい
            chInfo[i].hFreq = 1000f;
            chInfo[i].hQ = (float) (1.0f / Math.sqrt(2.0f));
            chInfo[i].lFreq = 300f;
            chInfo[i].lQ = (float) (1.0f / Math.sqrt(2.0f));

            chInfo[i].hsw = false;
            chInfo[i].highPassL = new Filter();
            chInfo[i].highPassR = new Filter();

            chInfo[i].lsw = false;
            chInfo[i].lowPassL = new Filter();
            chInfo[i].lowPassR = new Filter();

            updateParam(i);
        }
    }

    private void updateParam(int ch) {
        chInfo[ch].highPassL.highPass(chInfo[ch].hFreq, chInfo[ch].hQ, clock);
        chInfo[ch].highPassR.highPass(chInfo[ch].hFreq, chInfo[ch].hQ, clock);
        chInfo[ch].lowPassL.lowPass(chInfo[ch].lFreq, chInfo[ch].lQ, clock);
        chInfo[ch].lowPassR.lowPass(chInfo[ch].lFreq, chInfo[ch].lQ, clock);
    }

    private void updateParamHPF(int ch) {
        chInfo[ch].highPassL.highPass(chInfo[ch].hFreq, chInfo[ch].hQ, clock);
        chInfo[ch].highPassR.highPass(chInfo[ch].hFreq, chInfo[ch].hQ, clock);
    }

    private void updateParamLPF(int ch) {
        chInfo[ch].lowPassL.lowPass(chInfo[ch].lFreq, chInfo[ch].lQ, clock);
        chInfo[ch].lowPassR.lowPass(chInfo[ch].lFreq, chInfo[ch].lQ, clock);
    }

    public void mix(int ch, int[] inL, int[] inR) {
        mix(ch, inL, inR, 1);
    }

    public void mix(int ch, int[] inL, int[] inR, int waveLength) {
        if (ch < 0)
            return;
        if (ch >= maxCh)
            return;
        if (chInfo == null)
            return;
        if (chInfo[ch] == null)
            return;
        if (!chInfo[ch].hsw && !chInfo[ch].lsw)
            return;

        fBuf[0] = inL[0] / Filter.convInt;
        fBuf[1] = inR[0] / Filter.convInt;

        // 入力信号にフィルタを適用する
        if (chInfo[ch].hsw) {
            fBuf[0] = chInfo[ch].highPassL.process(fBuf[0]);
            fBuf[1] = chInfo[ch].highPassR.process(fBuf[1]);
        }

        if (chInfo[ch].lsw) {
            fBuf[0] = chInfo[ch].lowPassL.process(fBuf[0]);
            fBuf[1] = chInfo[ch].lowPassR.process(fBuf[1]);
        }

        inL[0] = (int) (fBuf[0] * Filter.convInt);
        inR[0] = (int) (fBuf[1] * Filter.convInt);
    }

    public void setReg(int adr, byte data) {
        switch (adr) {
        case 0:
            currentCh = Math.max(Math.min(data & 0x3f, 38), 0);
            if ((data & 0x80) != 0)
                init();
            updateParam(currentCh);
            break;

        case 1:
            chInfo[currentCh].lsw = (data != 0);
            updateParamLPF(currentCh);
            break;
        case 2:
            chInfo[currentCh].lFreq = Filter.freqTable[data];
            updateParamLPF(currentCh);
            break;
        case 3:
            chInfo[currentCh].lQ = Filter.qTable[data];
            updateParamLPF(currentCh);
            break;

        case 4:
            chInfo[currentCh].hsw = (data != 0);
            updateParamHPF(currentCh);
            break;
        case 5:
            chInfo[currentCh].hFreq = Filter.freqTable[data];
            updateParamHPF(currentCh);
            break;
        case 6:
            chInfo[currentCh].hQ = Filter.qTable[data];
            updateParamHPF(currentCh);
            break;
        }
    }
}
