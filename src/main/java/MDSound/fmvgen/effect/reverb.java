﻿
package MDSound.fmvgen.effect;

public class reverb {
    private int[][] Buf = new int[][] {
        null, null
    };

    private int Pos = 0;

    private int Delta = 0;

    public double[] SendLevel = null;

    private int currentCh = 0;

    private int Chs = 0;

    public reverb(int bufSize, int ch) {
        this.Buf = new int[][] {
            new int[bufSize], new int[bufSize]
        };
        Chs = ch;
        initParams();
    }

    public void initParams() {
        this.Pos = 0;
        this.currentCh = 0;
        SetDelta(64);

        this.SendLevel = new double[Chs];
        for (int i = 0; i < Chs; i++) {
            SetSendLevel(i, 0);
        }

        int bufSize = Buf[0].length;
        this.Buf = new int[][] {
            new int[bufSize], new int[bufSize]
        };
    }

    public void SetDelta(int n) {
        this.Delta = (int) Buf[0].length / 128 * Math.max(Math.min(n, 127), 0);
    }

    public void SetSendLevel(int ch, int n) {
        if (n == 0) {
            SendLevel[ch] = 0;
            return;
        }
        // SendLevel[ch] = 1.0 / (2 << Math.max(Math.min((15 - n), 15), 0));
        n = Math.max(Math.min(n, 15), 0);
        SendLevel[ch] = 1.0 * sl[n];
        // System.err.printf("{0} {1}", ch, SendLevel[ch]);
    }

    private double[] sl = new double[] {
        0.0050000, 0.0150000, 0.0300000, 0.0530000, 0.0680000,
        0.0800000, 0.0960000, 0.1300000, 0.2000000, 0.3000000, 0.4000000,
        0.5000000, 0.6000000, 0.7000000, 0.8000000, 0.9000000
    };

    public int GetDataFromPos(int LorR) {
        if (LorR == 0)
            return Buf[0][Pos];
        return Buf[1][Pos];
    }

    public int GetDataFromPosL() {
        return Buf[0][Pos];
    }

    public int GetDataFromPosR() {
        return Buf[1][Pos];
    }

    public void ClearDataAtPos() {
        Buf[0][Pos] = 0;
        Buf[1][Pos] = 0;
    }

    public void UpdatePos() {
        Pos = (1 + Pos) % Buf[0].length;
    }

    // public void StoreData(int ch, int v)
    // {
    // int ptr = (Delta + Pos) % Buf.length;
    // Buf[ptr] += (int)(v * SendLevel[ch]);
    // }

    //
    // public void StoreData(int LorR, int v)
    // {
    // int ptr = (Delta + Pos) % Buf[0].length;
    // Buf[LorR][ptr] += (int)(v);
    // }

    public void StoreDataL(int v) {
        int ptr = (Delta + Pos) % Buf[0].length;
        Buf[0][ptr] += (int) (v);
    }

    public void StoreDataR(int v) {
        int ptr = (Delta + Pos) % Buf[0].length;
        Buf[1][ptr] += (int) (v);
    }

    public void StoreDataC(int vL, int vR) {
        int ptr = (Delta + Pos) % Buf[0].length;
        Buf[0][ptr] += (int) (vL);
        Buf[1][ptr] += (int) (vR);
    }

    public void SetReg(int adr, byte data) {
        if (adr == 0) {
            SetDelta(data & 0x7f);
        } else if (adr == 1) {
            currentCh = Math.max(Math.min(data & 0x3f, 38), 0);
            if ((data & 0x80) != 0)
                initParams();
        } else if (adr == 2) {
            SetSendLevel(currentCh, data & 0xf);
        }
    }
}
