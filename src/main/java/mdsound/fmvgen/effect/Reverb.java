
package mdsound.fmvgen.effect;

public class Reverb {

    private int[][] buf;
    private int pos = 0;
    private int delta = 0;
    public double[] sendLevel = null;
    private int currentCh = 0;
    private int chs;

    public Reverb(int bufSize, int ch) {
        this.buf = new int[][] {
            new int[bufSize], new int[bufSize]
        };
        chs = ch;
        initParams();
    }

    public void initParams() {
        this.pos = 0;
        this.currentCh = 0;
        setDelta(64);

        this.sendLevel = new double[chs];
        for (int i = 0; i < chs; i++) {
            setSendLevel(i, 0);
        }

        int bufSize = buf[0].length;
        this.buf = new int[][] {
            new int[bufSize], new int[bufSize]
        };
    }

    public void setDelta(int n) {
        this.delta = buf[0].length / 128 * Math.max(Math.min(n, 127), 0);
    }

    public void setSendLevel(int ch, int n) {
        if (n == 0) {
            sendLevel[ch] = 0;
            return;
        }
        // SendLevel[ch] = 1.0 / (2 << Math.max(Math.min((15 - n), 15), 0));
        n = Math.max(Math.min(n, 15), 0);
        sendLevel[ch] = 1.0 * sl[n];
        // System.err.printf("{0} {1}", ch, SendLevel[ch]);
    }

    private static final double[] sl = new double[] {
        0.0050000, 0.0150000, 0.0300000, 0.0530000, 0.0680000,
        0.0800000, 0.0960000, 0.1300000, 0.2000000, 0.3000000, 0.4000000,
        0.5000000, 0.6000000, 0.7000000, 0.8000000, 0.9000000
    };

    public int getDataFromPos(int LorR) {
        if (LorR == 0)
            return buf[0][pos];
        return buf[1][pos];
    }

    public int getDataFromPosL() {
        return buf[0][pos];
    }

    public int getDataFromPosR() {
        return buf[1][pos];
    }

    public void clearDataAtPos() {
        buf[0][pos] = 0;
        buf[1][pos] = 0;
    }

    public void updatePos() {
        pos = (1 + pos) % buf[0].length;
    }

    // public void storeData(int ch, int v) {
    // int ptr = (Delta + Pos) % Buf.length;
    // Buf[ptr] += (int)(v * SendLevel[ch]);
    // }

    // public void storeData(int LorR, int v) {
    // int ptr = (Delta + Pos) % Buf[0].length;
    // Buf[LorR][ptr] += (int)(v);
    // }

    public void storeDataL(int v) {
        int ptr = (delta + pos) % buf[0].length;
        buf[0][ptr] += v;
    }

    public void storeDataR(int v) {
        int ptr = (delta + pos) % buf[0].length;
        buf[1][ptr] += v;
    }

    public void storeDataC(int vL, int vR) {
        int ptr = (delta + pos) % buf[0].length;
        buf[0][ptr] += vL;
        buf[1][ptr] += vR;
    }

    public void setReg(int adr, byte data) {
        if (adr == 0) {
            setDelta(data & 0x7f);
        } else if (adr == 1) {
            currentCh = Math.max(Math.min(data & 0x3f, 38), 0);
            if ((data & 0x80) != 0)
                initParams();
        } else if (adr == 2) {
            setSendLevel(currentCh, data & 0xf);
        }
    }
}
