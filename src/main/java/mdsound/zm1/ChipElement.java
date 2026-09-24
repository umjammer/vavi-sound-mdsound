package mdsound.zm1;


abstract class ChipElement {

    /** fine tune table, 2^(i/768) in 16.16 fixed point, i = 0..63 (100/64 cent step) (from fmgen) */
    static final int[] kftable = new int[64];

    static {
        for (int i = 0; i < 64; i++) {
            kftable[i] = (int) (0x10000 * Math.pow(2.0, i / 768.0));
        }
    }

    final Operator operator;

    ChipElement(Operator operator) {
        this.operator = operator;
    }

    /** writes to unknown addresses are ignored */
    public abstract void write(int adress, int data);
}
