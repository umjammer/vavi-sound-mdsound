package mdsound.chips;

import dotnet4j.util.compat.QuadFunction;


public class Xgm {

    private static class Pcm {
        private int priority = 0;
        private int startAddr = 0;
        private int endAddr = 0;
        private int addr = 0;
        private int inst = 0;
        private boolean isPlaying = false;
        private int data = 0;
    }

    private static class SampleID {
        private int addr = 0;
        private int size = 0;
    }

    /** 各チャンネルの情報 */
    private Pcm[][] xgmPcm = new Pcm[][] {new Pcm[4], new Pcm[4]};
    /** PCMデータ群 */
    private byte[][] pcmBuf = new byte[][] {null, null};
    /** PCMテーブル */
    private SampleID[][] sampleID = new SampleID[][] {new SampleID[63], new SampleID[63]};

    private double[] pcmStep = new double[2];
    private double[] pcmExecDelta = new double[2];
    private byte[] dacEnable = new byte[] {0, 0};
    private Object[] lockobj = new Object[] {new Object(), new Object()};
    private boolean ox2b = false;

    public void reset(int chipId, int sampleRate) {
        pcmStep[chipId] = sampleRate / 14000.0;
        stop(chipId);
    }

    public void stop(int chipId) {
        pcmExecDelta[chipId] = 0.0;
        dacEnable[chipId] = 0;
        ox2b = false;

        for (int i = 0; i < 4; i++) {
            if (xgmPcm[chipId][i] == null) xgmPcm[chipId][i] = new Pcm();
            xgmPcm[chipId][i].isPlaying = false;
        }
        for (int i = 0; i < 63; i++) sampleID[chipId][i] = new SampleID();
    }

    public void write(int chipId, int port, int adr, int data) {
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

    public void update(int chipId, int samples, QuadFunction<Byte, Integer, Integer, Integer, Integer> Write) {
        for (int i = 0; i < samples; i++) {
            while ((int) pcmExecDelta[chipId] <= 0) {
                write(chipId, 0, 0, 0x2a);
                write(chipId, 0, 1, oneFramePCM(chipId));
                pcmExecDelta[chipId] += pcmStep[chipId];
            }
            pcmExecDelta[chipId] -= 1.0;
        }
    }

    public void playPCM(int chipId, int X, int id) {
        int priority = X & 0xc;
        int channel = X & 0x3;

        synchronized (lockobj[chipId]) {
            // 優先度が高い場合または消音中の場合のみ発音できる
            if (xgmPcm[chipId][channel].priority > priority && xgmPcm[chipId][channel].isPlaying) return;

            if (id == 0 || id > sampleID[chipId].length || sampleID[chipId][id - 1].size == 0) {
                // IDが0の場合や、定義されていないIDが指定された場合は発音を停止する
                xgmPcm[chipId][channel].priority = 0;
                xgmPcm[chipId][channel].isPlaying = false;
                return;
            }

            // 発音開始指示
            xgmPcm[chipId][channel].priority = priority;
            xgmPcm[chipId][channel].startAddr = sampleID[chipId][id - 1].addr;
            xgmPcm[chipId][channel].endAddr = sampleID[chipId][id - 1].addr + sampleID[chipId][id - 1].size;
            xgmPcm[chipId][channel].addr = sampleID[chipId][id - 1].addr;
            xgmPcm[chipId][channel].inst = id;
            xgmPcm[chipId][channel].isPlaying = true;
        }
    }

    private short oneFramePCM(int chipId) {
        if (dacEnable[chipId] == 0) return 0x80; //0x80 : 無音状態(...というよりも波形の中心となる場所?)

        // 波形合成
        int o = 0;
        synchronized (lockobj[chipId]) {
            for (int i = 0; i < 4; i++) {
                if (!xgmPcm[chipId][i].isPlaying) continue;
                byte d = xgmPcm[chipId][i].addr < pcmBuf[chipId].length ? pcmBuf[chipId][xgmPcm[chipId][i].addr++] : (byte) 0;
                o += d;
                xgmPcm[chipId][i].data = Math.abs(d);
                if (xgmPcm[chipId][i].addr >= xgmPcm[chipId][i].endAddr) {
                    xgmPcm[chipId][i].isPlaying = false;
                    xgmPcm[chipId][i].data = 0;
                }
            }
        }

        o = (short) Math.min(Math.max(o, Byte.MIN_VALUE + 1), Byte.MAX_VALUE); //クリッピング
        o += 0x80; // OPN2での中心の位置に移動する

        return (short) o;
    }
}
