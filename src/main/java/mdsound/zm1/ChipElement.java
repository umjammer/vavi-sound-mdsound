package mdsound.zm1;


public abstract class ChipElement {

    // from fmgen
    private static int[] kftable = new int[64];
    private static int[] kctable = {
            5197, 5506, 5833, 6180, 6180, 6547, 6937, 7349,
            7349, 7786, 8249, 8740, 8740, 9259, 9810, 10394,
    };

    protected Operator operator;

    public ChipElement(Operator operator) {
        this.operator = operator;
        makeTable();
    }

    public abstract void write(int adress, int data);

    /**
     * from fmgen
     */
    public void setKCKF(int kc, int kf) {
        int oct = (int) (19 - ((kc >> 4) & 7));

        //printf("%p", this);
        int kcv = kctable[kc & 0x0f];
        kcv = (kcv + 2) / 4 * 4;
        //printf(" %.4x", kcv);
        int dp = (int) (kcv * kftable[kf & 0x3f]);
        //printf(" %.4x %.4x %.8x", kcv, kftable[kf & 0x3f], dp >> oct);
        dp >>= 16 + 3;
        dp <<= 16 + 3;
        dp >>= oct;
        int bn = (kc >> 2) & 31;
        //op[0].SetDPBN(dp, bn);
        //op[1].SetDPBN(dp, bn);
        //op[2].SetDPBN(dp, bn);
        //op[3].SetDPBN(dp, bn);
    }

    /**
     * from fmgen
     */
    public void makeTable() {
        // 100/64 cent =  2^(i*100/64*1200)
        for (int i = 0; i < 64; i++) {
            kftable[i] = (int) (0x10000 * Math.pow(2.0, i / 768.0));
        }
    }
}
