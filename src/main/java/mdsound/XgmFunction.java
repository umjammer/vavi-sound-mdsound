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

    public void reset(byte chipId, int sampleRate) {
        pcmStep[chipId] = sampleRate / 14000.0;
        stop(chipId);
    }

    public void stop(byte chipId) {
        pcmExecDelta[chipId] = 0.0;
        dacEnable[chipId] = 0;
        ox2b = false;

        for (int i = 0; i < 4; i++) {
            if (xgmpcm[chipId][i] == null) xgmpcm[chipId][i] = new XGMPCM();
            xgmpcm[chipId][i].isPlaying = false;
        }
        for (int i = 0; i < 63; i++) sampleID[chipId][i] = new XGMSampleID();
    }

    public void write(byte chipId, int port, int adr, int data) {
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
            dacEnable[chipId] = (byte) (data & 0x80);
            ox2b = false;
        }
    }

    public void update(byte chipId, int samples, QuadFunction<Byte, Integer, Integer, Integer, Integer> Write) {
        for (int i = 0; i < samples; i++) {
            while ((int) pcmExecDelta[chipId] <= 0) {
                write(chipId, 0, 0, 0x2a);
                write(chipId, 0, 1, oneFramePCM(chipId));
                pcmExecDelta[chipId] += pcmStep[chipId];
            }
            pcmExecDelta[chipId] -= 1.0;
        }
    }

    public void PlayPCM(byte chipId, byte X, byte id) {
        byte priority = (byte) (X & 0xc);
        byte channel = (byte) (X & 0x3);

        synchronized (lockobj[chipId]) {
            //優先度が高い場合または消音中の場合のみ発音できる
            if (xgmpcm[chipId][channel].Priority > priority && xgmpcm[chipId][channel].isPlaying) return;

            if (id == 0 || id > sampleID[chipId].length || sampleID[chipId][id - 1].size == 0) {
                //IDが0の場合や、定義されていないIDが指定された場合は発音を停止する
                xgmpcm[chipId][channel].Priority = 0;
                xgmpcm[chipId][channel].isPlaying = false;
                return;
            }

            //発音開始指示
            xgmpcm[chipId][channel].Priority = priority;
            xgmpcm[chipId][channel].startAddr = sampleID[chipId][id - 1].addr;
            xgmpcm[chipId][channel].endAddr = sampleID[chipId][id - 1].addr + sampleID[chipId][id - 1].size;
            xgmpcm[chipId][channel].addr = sampleID[chipId][id - 1].addr;
            xgmpcm[chipId][channel].inst = id;
            xgmpcm[chipId][channel].isPlaying = true;
        }
    }

    private short oneFramePCM(byte chipId) {
        if (dacEnable[chipId] == 0) return 0x80;//0x80 : 無音状態(...というよりも波形の中心となる場所?)

        //波形合成
        short o = 0;
        synchronized (lockobj[chipId]) {
            for (int i = 0; i < 4; i++) {
                if (!xgmpcm[chipId][i].isPlaying) continue;
                byte d = xgmpcm[chipId][i].addr < pcmBuf[chipId].length ? pcmBuf[chipId][xgmpcm[chipId][i].addr++] : (byte) 0;
                o += d;
                xgmpcm[chipId][i].data = (byte) Math.abs(d);
                if (xgmpcm[chipId][i].addr >= xgmpcm[chipId][i].endAddr) {
                    xgmpcm[chipId][i].isPlaying = false;
                    xgmpcm[chipId][i].data = 0;
                }
            }
        }

        o = (short) Math.min(Math.max(o, (Byte.MIN_VALUE & 0xff) + 1), Byte.MAX_VALUE & 0xff); //クリッピング
        o += 0x80;//OPN2での中心の位置に移動する

        return o;
    }

}
