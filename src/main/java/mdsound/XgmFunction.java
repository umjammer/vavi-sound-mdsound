package mdsound;

import mdsound.Common.QuadFunction;


public class XgmFunction {
    public static class XGMPCM {
        public int Priority = 0;
        public int startAddr = 0;
        public int endAddr = 0;
        public int addr = 0;
        public int inst = 0;
        public boolean isPlaying = false;
        public byte data = 0;
    }

    public static class XGMSampleID {
        public int addr = 0;
        public int size = 0;
    }

    /** 各チャンネルの情報 */
    public XGMPCM[][] xgmpcm = new XGMPCM[][] {new XGMPCM[4], new XGMPCM[4]};
    /** PCMデータ群 */
    public byte[][] pcmBuf = new byte[][] {null, null};
    /** PCMテーブル */
    public XGMSampleID[][] sampleID = new XGMSampleID[][] {new XGMSampleID[63], new XGMSampleID[63]};

    private double[] pcmStep = new double[2];
    private double[] pcmExecDelta = new double[2];
    private byte[] dacEnable = new byte[] {0, 0};
    private Object[] lockobj = new Object[] {new Object(), new Object()};
    private boolean ox2b = false;

    public void reset(byte chipID, int sampleRate) {
        pcmStep[chipID] = sampleRate / 14000.0;
        stop(chipID);
    }

    public void stop(byte chipID) {
        pcmExecDelta[chipID] = 0.0;
        dacEnable[chipID] = 0;
        ox2b = false;

        for (int i = 0; i < 4; i++) {
            if (xgmpcm[chipID][i] == null) xgmpcm[chipID][i] = new XGMPCM();
            xgmpcm[chipID][i].isPlaying = false;
        }
        for (int i = 0; i < 63; i++) sampleID[chipID][i] = new XGMSampleID();
    }

    public void write(byte chipID, int port, int adr, int data) {
        //
        // OPN2はアドレスとデータが二回に分けて送信されるタイプ
        // 一回目 アドレス (adr = 0)
        // 一回目 データ   (adr = 1)
        //

        if (port + adr == 0) {
            // 0x2b : DACのスイッチが含まれるアドレス
            if (data == 0x2b) ox2b = true;
            else ox2b = false;
        }
        if (ox2b && port == 0 && adr == 1) {
            // 0x80 : DACのスイッチを意味するbit7(1:ON 0:OFF)
            dacEnable[chipID] = (byte) (data & 0x80);
            ox2b = false;
        }
    }

    public void update(byte chipID, int samples, QuadFunction<Byte, Integer, Integer, Integer, Integer> Write) {
        for (int i = 0; i < samples; i++) {
            while ((int) pcmExecDelta[chipID] <= 0) {
                write(chipID, 0, 0, 0x2a);
                write(chipID, 0, 1, oneFramePCM(chipID));
                pcmExecDelta[chipID] += pcmStep[chipID];
            }
            pcmExecDelta[chipID] -= 1.0;
        }
    }

    public void PlayPCM(byte chipID, byte X, byte id) {
        byte priority = (byte) (X & 0xc);
        byte channel = (byte) (X & 0x3);

        synchronized (lockobj[chipID]) {
            //優先度が高い場合または消音中の場合のみ発音できる
            if (xgmpcm[chipID][channel].Priority > priority && xgmpcm[chipID][channel].isPlaying) return;

            if (id == 0 || id > sampleID[chipID].length || sampleID[chipID][id - 1].size == 0) {
                //IDが0の場合や、定義されていないIDが指定された場合は発音を停止する
                xgmpcm[chipID][channel].Priority = 0;
                xgmpcm[chipID][channel].isPlaying = false;
                return;
            }

            //発音開始指示
            xgmpcm[chipID][channel].Priority = priority;
            xgmpcm[chipID][channel].startAddr = sampleID[chipID][id - 1].addr;
            xgmpcm[chipID][channel].endAddr = sampleID[chipID][id - 1].addr + sampleID[chipID][id - 1].size;
            xgmpcm[chipID][channel].addr = sampleID[chipID][id - 1].addr;
            xgmpcm[chipID][channel].inst = id;
            xgmpcm[chipID][channel].isPlaying = true;
        }
    }

    private short oneFramePCM(byte chipID) {
        if (dacEnable[chipID] == 0) return 0x80;//0x80 : 無音状態(...というよりも波形の中心となる場所?)

        //波形合成
        short o = 0;
        synchronized (lockobj[chipID]) {
            for (int i = 0; i < 4; i++) {
                if (!xgmpcm[chipID][i].isPlaying) continue;
                byte d = xgmpcm[chipID][i].addr < pcmBuf[chipID].length ? (byte) pcmBuf[chipID][xgmpcm[chipID][i].addr++] : (byte) 0;
                o += d;
                xgmpcm[chipID][i].data = (byte) Math.abs((int) d);
                if (xgmpcm[chipID][i].addr >= xgmpcm[chipID][i].endAddr) {
                    xgmpcm[chipID][i].isPlaying = false;
                    xgmpcm[chipID][i].data = 0;
                }
            }
        }

        o = (short) Math.min(Math.max(o, (Byte.MIN_VALUE & 0xff) + 1), Byte.MAX_VALUE & 0xff); //クリッピング
        o += 0x80;//OPN2での中心の位置に移動する

        return o;
    }

}
