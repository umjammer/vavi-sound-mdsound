
package mdsound.np.cpu;

import java.util.function.BiConsumer;
import java.util.function.Function;

import mdsound.np.Device;


public class Km6502 implements Device {

    /* Accumulator */
    public int a;
    /* Status register */
    public int p;
    /* X register */
    public int x;
    /* Y register */
    public int y;
    /* Stack pointer */
    public int s;
    /* Program Counter */
    public int pc;
    /* interrupt request */
    public int iRequest;
    /* interrupt mask */
    public int iMask;
    /* (incremental)cycle counter */
    public int clock;
    public int lastcode;

    public int illegal;

    //pointer to Callback functions
    interface ReadHandler extends Function<Integer, Integer> {}
    interface WriterHandler extends BiConsumer<Integer, Integer>{}

    public ReadHandler readByte;
    public WriterHandler writeByte;

    enum Flags {
        C(0x01),
        Z(0x02),
        I(0x04),
        D(0x08),
        B(0x10),
        R(0x20),
        V(0x40),
        N(0x80);

        final int v;

        Flags(int v) {
            this.v = v;
        }
    }

    enum IRQ {
        INIT(1),
        RESET(2),
        NMI(4),
        BRK(8),
        INT(16);

        final int v;

        IRQ(int v) {
            this.v = v;
        }
    }

    public final int C_FLAG = Flags.C.v;
    public final int Z_FLAG = Flags.Z.v;
    public final int I_FLAG = Flags.I.v;
    public final int D_FLAG = Flags.D.v;
    public final int B_FLAG = Flags.B.v;
    public final int R_FLAG = Flags.R.v;
    public final int V_FLAG = Flags.V.v;
    public final int N_FLAG = Flags.N.v;
    public final int T_FLAG = 0;

    private static final int BASE_OF_ZERO = 0x0000;
    private static final int VEC_RESET = 0xFFFC;
    private static final int VEC_NMI = 0xFFFA;
    private static final int VEC_INT = 0xFFFE;
    private static final int VEC_BRK = VEC_INT;
    private static final int IRQ_INIT = IRQ.INIT.v;
    private static final int IRQ_RESET = IRQ.RESET.v;
    private static final int IRQ_NMI = IRQ.NMI.v;
    private static final int IRQ_BRK = IRQ.BRK.v;
    private static final int IRQ_INT = IRQ.INT.v;

    int K_READ(int adr) {
        return readByte.apply(adr);
    }

    void K_WRITE(int adr, int value) {
        writeByte.accept(adr, value);
    }

    static final byte[] fl_table = new byte[] {
        0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,

        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
        (byte) 0x80, (byte) 0x80,

        0x03, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
        0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
        0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
        0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
        0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
        0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
        0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,

        (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81,
        (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81,
        (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81,
        (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81,
        (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81,
        (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81,
        (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81,
        (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81,
        (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81,
        (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81,
        (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81,
        (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81,
        (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81,
        (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81,
        (byte) 0x81, (byte) 0x81,
    };

    public int FLAG_NZ(int w) {
        return fl_table[w & 0xff];
    }

    public int FLAG_NZC(int w) {
        return (fl_table[w & 0x01ff]);
    }

    public void KI_ADDCLOCK(int cycle) {
        this.clock += cycle;
    }

    public int KI_READWORD(int adr) {
        int ret = K_READ(adr);
        return ret + (K_READ((adr + 1) & 0xffff) << 8);
    }

    public int KI_READWORDZP(int adr) {
        int ret = K_READ(BASE_OF_ZERO + adr);
        return ret + (K_READ(BASE_OF_ZERO + ((adr + 1) & 0xff)) << 8);
    }

    public int KAI_IMM() {
        int ret = this.pc;
        this.pc = (this.pc + 1) & 0xffff;
        return ret;
    }

    public int KAI_IMM16() {
        int ret = this.pc;
        this.pc = (this.pc + 2) & 0xffff;
        return ret;
    }

    public int KAI_ABS() {
        return KI_READWORD(KAI_IMM16());
    }

    public int KAI_ABSX() {
        return (KAI_ABS() + this.x) & 0xffff;
    }

    public int KAI_ABSY() {
        return (KAI_ABS() + this.y) & 0xffff;
    }

    public int KAI_ZP() {
        return K_READ(KAI_IMM());
    }

    public int KAI_ZPX() {
        return (KAI_ZP() + this.x) & 0xff;
    }

    public int KAI_INDY() {
        return (KI_READWORDZP(KAI_ZP()) + this.y) & 0xffff;
    }

    public int KA_IMM() {
        int ret = this.pc;
        this.pc = (this.pc + 1) & 0xffff;
        return ret;
    }

    public int KA_IMM16() {
        int ret = this.pc;
        this.pc = (this.pc + 2) & 0xffff;
        return ret;
    }

    public int KA_ABS() {
        return KI_READWORD(KAI_IMM16());
    }

    public int KA_ABSX() {
        return (KAI_ABS() + this.x) & 0xffff;
    }

    public int KA_ABSY() {
        return (KAI_ABS() + this.y) & 0xffff;
    }

    public int KA_ZP() {
        return BASE_OF_ZERO + K_READ(KAI_IMM());
    }

    public int KA_ZPX() {
        return BASE_OF_ZERO + ((KAI_ZP() + this.x) & 0xff);
    }

    public int KA_ZPY() {
        return BASE_OF_ZERO + ((KAI_ZP() + this.y) & 0xff);
    }

    public int KA_INDX() {
        return KI_READWORDZP(KAI_ZPX());
    }

    public int KA_INDY() {
        return (KI_READWORDZP(KAI_ZP()) + this.y) & 0xffff;
    }

    public int KI_READWORDBUG(int adr) {
        int ret = K_READ(adr);
        return ret + (K_READ((adr & 0xFF00) + ((adr + 1) & 0xff)) << 8);
    }

    public int KA_ABSX_() {
        if (((this.pc) & 0xff) == 0xFF)
            KI_ADDCLOCK(1); // page break
        return KAI_ABSX();
    }

    public int KA_ABSY_() {
        if (((this.pc) & 0xff) == 0xFF)
            KI_ADDCLOCK(1); // page break
        return KAI_ABSY();
    }

    public int KA_INDY_() {
        int adr = KAI_INDY();
        if (((adr) & 0xff) == 0xFF)
            KI_ADDCLOCK(1); // page break
        return adr;
    }

    public void KM_ALUADDER(int src) {
        int w = this.a + src + (this.p & C_FLAG);
        this.p &= ~(int) (N_FLAG | V_FLAG | Z_FLAG | C_FLAG);
        this.p += FLAG_NZC(w) + ((((~this.a ^ src) & (this.a ^ w)) >> 1) & V_FLAG);
        this.a = (w) & 0xff;
    }

    public void KM_ALUADDER_D(int src) {
        int wl = (this.a & 0x0F) + (src & 0x0F) + (this.p & C_FLAG);
        int w = this.a + src + (this.p & C_FLAG);
        this.p &= ~(int) C_FLAG;
        if (wl > 0x9)
            w += 0x6;
        if (w > 0x9F) {
            this.p += C_FLAG;
            w += 0x60;
        }
        this.a = (w) & 0xff;
        KI_ADDCLOCK(1);
    }

    public void KMI_ADC(int src) {
        KM_ALUADDER(src);
    }

    public void KMI_ADC_D(int src) {
        KM_ALUADDER_D(src);
    }

    public void KMI_SBC(int src) {
        KM_ALUADDER(src ^ 0xFF);
    }

    public void KMI_SBC_D(int src) {
        KM_ALUADDER_D(((src ^ 0xFF) + (0x100 - 0x66)) & 0xff);
    }

    public void KM_CMP(int src) {
        int w = this.a + (src ^ 0xFF) + 1;
        this.p &= ~(int) (N_FLAG | Z_FLAG | C_FLAG);
        this.p += FLAG_NZC(w);
    }

    public void KM_CPX(int src) {
        int w = this.x + (src ^ 0xFF) + 1;
        this.p &= ~(int) (N_FLAG | Z_FLAG | C_FLAG);
        this.p += FLAG_NZC(w);
    }

    public void KM_CPY(int src) {
        int w = this.y + (src ^ 0xFF) + 1;
        this.p &= ~(int) (N_FLAG | Z_FLAG | C_FLAG);
        this.p += FLAG_NZC(w);
    }

    public void KM_BIT(int src) {
        int w = this.a & src;
        this.p &= ~(int) (N_FLAG | V_FLAG | Z_FLAG);
        this.p += (src & (N_FLAG | V_FLAG)) + (w != 0 ? 0 : Z_FLAG);
    }

    public void KM_AND(int src) {
        this.a &= src;
        this.p &= ~(int) (N_FLAG | Z_FLAG);
        this.p += FLAG_NZ(this.a);
    }

    public void KM_ORA(int src) {
        this.a |= src;
        this.p &= ~(int) (N_FLAG | Z_FLAG);
        this.p += FLAG_NZ(this.a);
    }

    public void KM_EOR(int src) {
        this.a ^= src;
        this.p &= ~(int) (N_FLAG | Z_FLAG);
        this.p += FLAG_NZ(this.a);
    }

    public int KM_DEC(int des) {
        int w = des - 1;
        this.p &= ~(int) (N_FLAG | Z_FLAG);
        this.p += FLAG_NZ(w);
        return (w) & 0xff;
    }

    public int KM_INC(int des) {
        int w = des + 1;
        this.p &= ~(int) (N_FLAG | Z_FLAG);
        this.p += FLAG_NZ(w);
        return (w) & 0xff;
    }

    public int KM_ASL(int des) {
        int w = des << 1;
        this.p &= ~(int) (N_FLAG | Z_FLAG | C_FLAG);
        this.p += FLAG_NZ(w) + ((des >> 7)/* & C_FLAG */);
        return (w) & 0xff;
    }

    public int KM_LSR(int des) {
        int w = des >> 1;
        this.p &= ~(int) (N_FLAG | Z_FLAG | C_FLAG);
        this.p += FLAG_NZ(w) + (des & C_FLAG);
        return w;
    }

    public int KM_LD(int src) {
        this.p &= ~(int) (N_FLAG | Z_FLAG);
        this.p += FLAG_NZ(src);
        return src;
    }

    public int KM_ROL(int des) {
        int w = (des << 1) + (this.p & C_FLAG);
        this.p &= ~(int) (N_FLAG | Z_FLAG | C_FLAG);
        this.p += FLAG_NZ(w) + ((des >> 7)/* & C_FLAG */);
        return (w) & 0xff;
    }

    public int KM_ROR(int des) {
        int w = (des >> 1) + ((this.p & C_FLAG) << 7);
        this.p &= ~(int) (N_FLAG | Z_FLAG | C_FLAG);
        this.p += FLAG_NZ(w) + (des & C_FLAG);
        return (w) & 0xff;
    }

    public void KM_BRA(int rel) {
        int oldPage = this.pc & 0xFF00;
        this.pc = (this.pc + (rel ^ 0x80) - 0x80) & 0xffff;
        KI_ADDCLOCK(1 + ((oldPage != (this.pc & 0xFF00)) ? 1 : 0));
    }

    public void KM_PUSH(int src) {
        K_WRITE(BASE_OF_ZERO + 0x100 + this.s, src);
        this.s = (this.s - 1) & 0xff;
    }

    public int KM_POP() {
        this.s = (this.s + 1) & 0xff;
        return K_READ(BASE_OF_ZERO + 0x100 + this.s);
    }

    /* --- ADC --- */

    public void opCode61() {
        KMI_ADC(K_READ(KA_INDX()));
    }

    public void D_Opco61() {
        KMI_ADC_D(K_READ(KA_INDX()));
    }

    public void opCode65() {
        KMI_ADC(K_READ(KA_ZP()));
    }

    public void D_Opco65() {
        KMI_ADC_D(K_READ(KA_ZP()));
    }

    public void opCode69() {
        KMI_ADC(K_READ(KA_IMM()));
    }

    public void D_Opco69() {
        KMI_ADC_D(K_READ(KA_IMM()));
    }

    public void opCode6D() {
        KMI_ADC(K_READ(KA_ABS()));
    }

    public void D_Opco6D() {
        KMI_ADC_D(K_READ(KA_ABS()));
    }

    public void opCode71() {
        KMI_ADC(K_READ(KA_INDY()));
    }

    public void D_Opco71() {
        KMI_ADC_D(K_READ(KA_INDY()));
    }

    public void opCode75() {
        KMI_ADC(K_READ(KA_ZPX()));
    }

    public void D_Opco75() {
        KMI_ADC_D(K_READ(KA_ZPX()));
    }

    public void opCode79() {
        KMI_ADC(K_READ(KA_ABSY()));
    }

    public void D_Opco79() {
        KMI_ADC_D(K_READ(KA_ABSY()));
    }

    public void opCode7D() {
        KMI_ADC(K_READ(KA_ABSX()));
    }

    public void D_Opco7D() {
        KMI_ADC_D(K_READ(KA_ABSX()));
    }

    /* --- AND --- */

    public void opCode21() {
        KM_AND(K_READ(KA_INDX()));
    }

    public void opCode25() {
        KM_AND(K_READ(KA_ZP()));
    }

    public void opCode29() {
        KM_AND(K_READ(KA_IMM()));
    }

    public void opCode2D() {
        KM_AND(K_READ(KA_ABS()));
    }

    public void opCode31() {
        KM_AND(K_READ(KA_INDY()));
    }

    public void opCode35() {
        KM_AND(K_READ(KA_ZPX()));
    }

    public void opCode39() {
        KM_AND(K_READ(KA_ABSY()));
    }

    public void opCode3D() {
        KM_AND(K_READ(KA_ABSX()));
    }

    /* --- ASL --- */

    public void opCode06() {
        int adr = KA_ZP();
        K_WRITE(adr, KM_ASL(K_READ(adr)));
    }

    public void opCode0E() {
        int adr = KA_ABS();
        K_WRITE(adr, KM_ASL(K_READ(adr)));
    }

    public void opCode16() {
        int adr = KA_ZPX();
        K_WRITE(adr, KM_ASL(K_READ(adr)));
    }

    public void opCode1E() {
        int adr = KA_ABSX();
        K_WRITE(adr, KM_ASL(K_READ(adr)));
    }

    public void opCode0A() { // 0A - ASL - Accumulator
        this.a = KM_ASL(this.a);
    }

    // --- BBRi ---

    /* --- BIT --- */

    public void opCode24() {
        KM_BIT(K_READ(KA_ZP()));
    }

    public void opCode2C() {
        KM_BIT(K_READ(KA_ABS()));
    }

    /* --- Bcc --- */

    public void opCode10() {
        int rel = K_READ(KA_IMM());
        if (((this.p & N_FLAG) == 0))
            KM_BRA(rel);
    }

    public void opCode30() {
        int rel = K_READ(KA_IMM());
        if (((this.p & N_FLAG) != 0))
            KM_BRA(rel);
    }

    public void opCode50() {
        int rel = K_READ(KA_IMM());
        if (((this.p & V_FLAG) == 0))
            KM_BRA(rel);
    }

    public void opCode70() {
        int rel = K_READ(KA_IMM());
        if (((this.p & V_FLAG) != 0))
            KM_BRA(rel);
    }

    public void opCode90() {
        int rel = K_READ(KA_IMM());
        if (((this.p & C_FLAG) == 0))
            KM_BRA(rel);
    }

    public void opCodeB0() {
        int rel = K_READ(KA_IMM());
        if (((this.p & C_FLAG) != 0))
            KM_BRA(rel);
    }

    public void opCodeD0() {
        int rel = K_READ(KA_IMM());
        if (((this.p & Z_FLAG) == 0))
            KM_BRA(rel);
    }

    public void opCodeF0() {
        int rel = K_READ(KA_IMM());
        if (((this.p & Z_FLAG) != 0))
            KM_BRA(rel);
    }

    /* --- BRK --- */

    public void opCode00() {
        this.pc = (this.pc + 1) & 0xffff;
        this.iRequest |= IRQ_BRK;
    }

    /// * --- BSR --- */

    /* --- CLC --- */
    public void opCode18() { /* 18 - CLC */
        this.p &= ~(int) C_FLAG;
    }

    /* --- CLD --- */
    public void opCodeD8() { /* D8 - CLD */
        this.p &= ~(int) D_FLAG;
    }

    /* --- CLI --- */
    public void opCode58() { /* 58 - CLI */
        this.p &= ~(int) I_FLAG;
    }

    /* --- CLV --- */
    public void opCodeB8() { /* B8 - CLV */
        this.p &= ~(int) V_FLAG;
    }

    /* --- CMP --- */
    public void opCodeC1() {
        KM_CMP(K_READ(KA_INDX()));
    }

    public void opCodeC5() {
        KM_CMP(K_READ(KA_ZP()));
    }

    public void opCodeC9() {
        KM_CMP(K_READ(KA_IMM()));
    }

    public void opCodeCD() {
        KM_CMP(K_READ(KA_ABS()));
    }

    public void opCodeD1() {
        KM_CMP(K_READ(KA_INDY()));
    }

    public void opCodeD5() {
        KM_CMP(K_READ(KA_ZPX()));
    }

    public void opCodeD9() {
        KM_CMP(K_READ(KA_ABSY()));
    }

    public void opCodeDD() {
        KM_CMP(K_READ(KA_ABSX()));
    }

    /* --- CPX --- */
    public void opCodeE0() {
        KM_CPX(K_READ(KA_IMM()));
    }

    public void opCodeE4() {
        KM_CPX(K_READ(KA_ZP()));
    }

    public void opCodeEC() {
        KM_CPX(K_READ(KA_ABS()));
    }

    /* --- CPY --- */
    public void opCodeC0() {
        KM_CPY(K_READ(KA_IMM()));
    }

    public void opCodeC4() {
        KM_CPY(K_READ(KA_ZP()));
    }

    public void opCodeCC() {
        KM_CPY(K_READ(KA_ABS()));
    }

    /* --- DEC --- */
    public void opCodeC6() {
        int adr = KA_ZP();
        K_WRITE(adr, KM_DEC(K_READ(adr)));
    }

    public void opCodeCE() {
        int adr = KA_ABS();
        K_WRITE(adr, KM_DEC(K_READ(adr)));
    }

    public void opCodeD6() {
        int adr = KA_ZPX();
        K_WRITE(adr, KM_DEC(K_READ(adr)));
    }

    public void opCodeDE() {
        int adr = KA_ABSX();
        K_WRITE(adr, KM_DEC(K_READ(adr)));
    }

    public void opCodeCA() { /* CA - DEX */
        this.x = KM_DEC(this.x);
    }

    public void opCode88() { /* 88 - DEY */
        this.y = KM_DEC(this.y);
    }

    /* --- EOR --- */

    public void opCode41() {
        KM_EOR(K_READ(KA_INDX()));
    }

    public void opCode45() {
        KM_EOR(K_READ(KA_ZP()));
    }

    public void opCode49() {
        KM_EOR(K_READ(KA_IMM()));
    }

    public void opCode4D() {
        KM_EOR(K_READ(KA_ABS()));
    }

    public void opCode51() {
        KM_EOR(K_READ(KA_INDY()));
    }

    public void opCode55() {
        KM_EOR(K_READ(KA_ZPX()));
    }

    public void opCode59() {
        KM_EOR(K_READ(KA_ABSY()));
    }

    public void opCode5D() {
        KM_EOR(K_READ(KA_ABSX()));
    }

    /* --- INC --- */
    public void opCodeE6() {
        int adr = KA_ZP();
        K_WRITE(adr, KM_INC(K_READ(adr)));
    }

    public void opCodeEE() {
        int adr = KA_ABS();
        K_WRITE(adr, KM_INC(K_READ(adr)));
    }

    public void opCodeF6() {
        int adr = KA_ZPX();
        K_WRITE(adr, KM_INC(K_READ(adr)));
    }

    public void opCodeFE() {
        int adr = KA_ABSX();
        K_WRITE(adr, KM_INC(K_READ(adr)));
    }

    public void opCodeE8() { // E8 - INX
        this.x = KM_INC(this.x);
    }

    public void opCodeC8() { // C8 - INY
        this.y = KM_INC(this.y);
    }

    /* --- JMP --- */
    public void opCode4C() {
        this.pc = KI_READWORD(KA_IMM16());
    }

    public void opCode6C() {
        this.pc = KI_READWORDBUG(KA_ABS());
    }

    /* --- JSR --- */
    public void opCode20() { /* 20 - JSR */
        int adr = KA_IMM();
        KM_PUSH((this.pc >> 8) & 0xff); /* !!! PC = NEXT - 1; !!! */
        KM_PUSH((this.pc) & 0xff);
        this.pc = KI_READWORD(adr);
    }

    /* --- LDA --- */
    public void opCodeA1() {
        this.a = KM_LD(K_READ(KA_INDX()));
    }

    public void opCodeA5() {
        this.a = KM_LD(K_READ(KA_ZP()));
    }

    public void opCodeA9() {
        this.a = KM_LD(K_READ(KA_IMM()));
    }

    public void opCodeAD() {
        this.a = KM_LD(K_READ(KA_ABS()));
    }

    public void opCodeB1() {
        this.a = KM_LD(K_READ(KA_INDY()));
    }

    public void opCodeB5() {
        this.a = KM_LD(K_READ(KA_ZPX()));
    }

    public void opCodeB9() {
        this.a = KM_LD(K_READ(KA_ABSY()));
    }

    public void opCodeBD() {
        this.a = KM_LD(K_READ(KA_ABSX()));
    }

    /* --- LDX --- */
    public void opCodeA2() {
        this.x = KM_LD(K_READ(KA_IMM()));
    }

    public void opCodeA6() {
        this.x = KM_LD(K_READ(KA_ZP()));
    }

    public void opCodeAE() {
        this.x = KM_LD(K_READ(KA_ABS()));
    }

    public void opCodeB6() {
        this.x = KM_LD(K_READ(KA_ZPY()));
    }

    public void opCodeBE() {
        this.x = KM_LD(K_READ(KA_ABSY()));
    }

    /* --- LDY --- */
    public void opCodeA0() {
        this.y = KM_LD(K_READ(KA_IMM()));
    }

    public void opCodeA4() {
        this.y = KM_LD(K_READ(KA_ZP()));
    }

    public void opCodeAC() {
        this.y = KM_LD(K_READ(KA_ABS()));
    }

    public void opCodeB4() {
        this.y = KM_LD(K_READ(KA_ZPX()));
    }

    public void opCodeBC() {
        this.y = KM_LD(K_READ(KA_ABSX()));
    }

    /* --- LSR --- */
    public void opCode46() {
        int adr = KA_ZP();
        K_WRITE(adr, KM_LSR(K_READ(adr)));
    }

    public void opCode4E() {
        int adr = KA_ABS();
        K_WRITE(adr, KM_LSR(K_READ(adr)));
    }

    public void opCode56() {
        int adr = KA_ZPX();
        K_WRITE(adr, KM_LSR(K_READ(adr)));
    }

    public void opCode5E() {
        int adr = KA_ABSX();
        K_WRITE(adr, KM_LSR(K_READ(adr)));
    }

    public void opCode4A() { /* 4A - LSR - Accumulator */
        this.a = KM_LSR(this.a);
    }

    /* --- NOP --- */
    public void opCodeEA() { /* EA - NOP */
    }

    /* --- ORA --- */
    public void opCode01() {
        KM_ORA(K_READ(KA_INDX()));
    }

    public void opCode05() {
        KM_ORA(K_READ(KA_ZP()));
    }

    public void opCode09() {
        KM_ORA(K_READ(KA_IMM()));
    }

    public void opCode0D() {
        KM_ORA(K_READ(KA_ABS()));
    }

    public void opCode11() {
        KM_ORA(K_READ(KA_INDY()));
    }

    public void opCode15() {
        KM_ORA(K_READ(KA_ZPX()));
    }

    public void opCode19() {
        KM_ORA(K_READ(KA_ABSY()));
    }

    public void opCode1D() {
        KM_ORA(K_READ(KA_ABSX()));
    }

    /* --- PHr PLr --- */
    public void opCode48() { /* 48 - PHA */
        KM_PUSH(this.a);
    }

    public void opCode08() { /* 08 - PHP */
        KM_PUSH((this.p | B_FLAG | R_FLAG) & ~T_FLAG);
    }

    public void opCode68() { /* 68 - PLA */
        this.a = KM_LD(KM_POP());
    }

    public void opCode28() { /* 28 - PLP */
        this.p = KM_POP() & ~T_FLAG;
    }

    /// * --- RMBi --- */
    /// * --- SMBi --- */

    /* --- ROL --- */
    public void opCode26() {
        int adr = KA_ZP();
        K_WRITE(adr, KM_ROL(K_READ(adr)));
    }

    public void opCode2E() {
        int adr = KA_ABS();
        K_WRITE(adr, KM_ROL(K_READ(adr)));
    }

    public void opCode36() {
        int adr = KA_ZPX();
        K_WRITE(adr, KM_ROL(K_READ(adr)));
    }

    public void opCode3E() {
        int adr = KA_ABSX();
        K_WRITE(adr, KM_ROL(K_READ(adr)));
    }

    public void opCode2A() { /* 2A - ROL - Accumulator */
        this.a = KM_ROL(this.a);
    }

    /* --- ROR --- */
    public void opCode66() {
        int adr = KA_ZP();
        K_WRITE(adr, KM_ROR(K_READ(adr)));
    }

    public void opCode6E() {
        int adr = KA_ABS();
        K_WRITE(adr, KM_ROR(K_READ(adr)));
    }

    public void opCode76() {
        int adr = KA_ZPX();
        K_WRITE(adr, KM_ROR(K_READ(adr)));
    }

    public void opCode7E() {
        int adr = KA_ABSX();
        K_WRITE(adr, KM_ROR(K_READ(adr)));
    }

    public void opCode6A() { /* 6A - ROR - Accumulator */
        this.a = KM_ROR(this.a);
    }

    public void opCode40() { /* 40 - RTI */
        this.p = KM_POP();
        this.pc = KM_POP();
        this.pc += KM_POP() << 8;
    }

    public void opCode60() { /* 60 - RTS */
        this.pc = KM_POP();
        this.pc += KM_POP() << 8;
        this.pc = (this.pc + 1) & 0xffff;
    }

    /* --- SBC --- */
    public void opCodeE1() {
        KMI_SBC(K_READ(KA_INDX()));
    }

    public void D_OpcoE1() {
        KMI_SBC_D(K_READ(KA_INDX()));
    }

    public void opCodeE5() {
        KMI_SBC(K_READ(KA_ZP()));
    }

    public void D_OpcoE5() {
        KMI_SBC_D(K_READ(KA_ZP()));
    }

    public void opCodeE9() {
        KMI_SBC(K_READ(KA_IMM()));
    }

    public void D_OpcoE9() {
        KMI_SBC_D(K_READ(KA_IMM()));
    }

    public void opCodeED() {
        KMI_SBC(K_READ(KA_ABS()));
    }

    public void D_OpcoED() {
        KMI_SBC_D(K_READ(KA_ABS()));
    }

    public void opCodeF1() {
        KMI_SBC(K_READ(KA_INDY()));
    }

    public void D_OpcoF1() {
        KMI_SBC_D(K_READ(KA_INDY()));
    }

    public void opCodeF5() {
        KMI_SBC(K_READ(KA_ZPX()));
    }

    public void D_OpcoF5() {
        KMI_SBC_D(K_READ(KA_ZPX()));
    }

    public void opCodeF9() {
        KMI_SBC(K_READ(KA_ABSY()));
    }

    public void D_OpcoF9() {
        KMI_SBC_D(K_READ(KA_ABSY()));
    }

    public void opCodeFD() {
        KMI_SBC(K_READ(KA_ABSX()));
    }

    public void D_OpcoFD() {
        KMI_SBC_D(K_READ(KA_ABSX()));
    }

    /* --- SEC --- */
    public void opCode38() { /* 38 - SEC */
        this.p |= C_FLAG;
    }

    /* --- SED --- */
    public void opCodeF8() { /* F8 - SED */
        this.p |= D_FLAG;
    }

    /* --- SEI --- */
    public void opCode78() { /* 78 - SEI */
        this.p |= I_FLAG;
    }

    /// * --- SET --- */

    /* --- STA --- */
    public void opCode81() {
        K_WRITE(KA_INDX(), this.a);
    }

    public void opCode85() {
        K_WRITE(KA_ZP(), this.a);
    }

    public void opCode8D() {
        K_WRITE(KA_ABS(), this.a);
    }

    public void opCode91() {
        K_WRITE(KA_INDY(), this.a);
    }

    public void opCode95() {
        K_WRITE(KA_ZPX(), this.a);
    }

    public void opCode99() {
        K_WRITE(KA_ABSY(), this.a);
    }

    public void opCode9D() {
        K_WRITE(KA_ABSX(), this.a);
    }

    /* --- STX --- */
    public void opCode86() {
        K_WRITE(KA_ZP(), this.x);
    }

    public void opCode8E() {
        K_WRITE(KA_ABS(), this.x);
    }

    public void opCode96() {
        K_WRITE(KA_ZPY(), this.x);
    }

    /* --- STY --- */
    public void opCode84() {
        K_WRITE(KA_ZP(), this.y);
    }

    public void opCode8C() {
        K_WRITE(KA_ABS(), this.y);
    }

    public void opCode94() {
        K_WRITE(KA_ZPX(), this.y);
    }

    /// * --- STZ --- */
    /// * --- TAMi --- */
    /// * --- TRB --- */
    /// * --- TSB --- */
    /// * --- TST --- */

    /* --- TAX --- */
    public void opCodeAA() { /* AA - TAX */
        this.x = KM_LD(this.a);
    }

    /* --- TAY --- */
    public void opCodeA8() { /* A8 - TAY */
        this.y = KM_LD(this.a);
    }

    /* --- TSX --- */
    public void opCodeBA() { /* BA - TSX */
        this.x = KM_LD(this.s);
    }

    /* --- TXA --- */
    public void opCode8A() { /* 8A - TXA */
        this.a = KM_LD(this.x);
    }

    /* --- TXS --- */
    public void opCode9A() { /* 9A - TXS */
        this.s = this.x;
    }

    /* --- TYA --- */
    public void opCode98() { /* 98 - TYA */
        this.a = KM_LD(this.y);
    }

    /* --- KIL --- */
    /* halts CPU */
    public void opCode02() {
        this.pc = (this.pc - 1) & 0xffff;
        this.p |= I_FLAG;
    }

    public void opCode12() {
        this.pc = (this.pc - 1) & 0xffff;
        this.p |= I_FLAG;
    }

    public void opCode22() {
        this.pc = (this.pc - 1) & 0xffff;
        this.p |= I_FLAG;
    }

    public void opCode32() {
        this.pc = (this.pc - 1) & 0xffff;
        this.p |= I_FLAG;
    }

    public void opCode42() {
        this.pc = (this.pc - 1) & 0xffff;
        this.p |= I_FLAG;
    }

    public void opCode52() {
        this.pc = (this.pc - 1) & 0xffff;
        this.p |= I_FLAG;
    }

    public void opCode62() {
        this.pc = (this.pc - 1) & 0xffff;
        this.p |= I_FLAG;
    }

    public void opCode72() {
        this.pc = (this.pc - 1) & 0xffff;
        this.p |= I_FLAG;
    }

    public void opCode92() {
        this.pc = (this.pc - 1) & 0xffff;
        this.p |= I_FLAG;
    }

    public void opCodeB2() {
        this.pc = (this.pc - 1) & 0xffff;
        this.p |= I_FLAG;
    }

    public void opCodeD2() {
        this.pc = (this.pc - 1) & 0xffff;
        this.p |= I_FLAG;
    }

    public void opCodeF2() {
        this.pc = (this.pc - 1) & 0xffff;
        this.p |= I_FLAG;
    }

    /* --- NOP --- */
    /* does nothing */
    public void opCode80() {
        KAI_IMM();
    }

    public void opCode82() {
        KAI_IMM();
    }

    public void opCodeC2() {
        KAI_IMM();
    }

    public void opCodeE2() {
        KAI_IMM();
    }

    public void opCode04() {
        KAI_ZP();
    }

    public void opCode14() {
        KAI_ZPX();
    }

    public void opCode34() {
        KAI_ZPX();
    }

    public void opCode44() {
        KAI_ZP();
    }

    public void opCode54() {
        KAI_ZPX();
    }

    public void opCode64() {
        KAI_ZP();
    }

    public void opCode74() {
        KAI_ZPX();
    }

    public void opCodeD4() {
        KAI_ZPX();
    }

    public void opCodeF4() {
        KAI_ZPX();
    }

    public void opCode89() {
        KAI_IMM();
    }

    public void opCode1A() {
    }

    public void opCode3A() {
    }

    public void opCode5A() {
    }

    public void opCode7A() {
    }

    public void opCodeDA() {
    }

    public void opCodeFA() {
    }

    public void opCode0C() {
        KAI_ABS();
    }

    public void opCode1C() {
        KA_ABSX_();
    }

    public void opCode3C() {
        KA_ABSX_();
    }

    public void opCode5C() {
        KA_ABSX_();
    }

    public void opCode7C() {
        KA_ABSX_();
    }

    public void opCodeDC() {
        KA_ABSX_();
    }

    public void opCodeFC() {
        KA_ABSX_();
    }

    /* --- SLO --- */
    /* shift left, OR result */
    public int KM_SLO(int src) {
        int w = (src << 1) & 0xff;
        this.a |= w;
        this.p &= (~(int) (N_FLAG | Z_FLAG | C_FLAG));
        this.p |= FLAG_NZ(this.a);
        this.p |= (src >> 7) & C_FLAG;
        return w;
    }

    /* macro */
    public void opCode03() {
        int adr = KA_INDX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_SLO(src));
    }

    public void opCode13() {
        int adr = KA_INDY();
        int src = K_READ(adr);
        K_WRITE(adr, KM_SLO(src));
    }

    public void opCode07() {
        int adr = KA_ZP();
        int src = K_READ(adr);
        K_WRITE(adr, KM_SLO(src));
    }

    public void opCode17() {
        int adr = KA_ZPX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_SLO(src));
    }

    public void opCode1B() {
        int adr = KA_ABSY();
        int src = K_READ(adr);
        K_WRITE(adr, KM_SLO(src));
    }

    public void opCode0F() {
        int adr = KA_ABS();
        int src = K_READ(adr);
        K_WRITE(adr, KM_SLO(src));
    }

    public void opCode1F() {
        int adr = KA_ABSX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_SLO(src));
    }

    /* --- RLA --- */
    /* rotate left, AND result */
    public int KM_RLA(int src) {
        int w = ((src << 1) | (this.p & C_FLAG)) & 0xff;
        this.a &= w;
        this.p &= ~(int) (N_FLAG | Z_FLAG | C_FLAG);
        this.p |= FLAG_NZ(this.a);
        this.p |= (src >> 7) & C_FLAG;
        return w;
    }

    /* macro */
    public void opCode23() {
        int adr = KA_INDX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_RLA(src));
    }

    public void opCode33() {
        int adr = KA_INDY();
        int src = K_READ(adr);
        K_WRITE(adr, KM_RLA(src));
    }

    public void opCode27() {
        int adr = KA_ZP();
        int src = K_READ(adr);
        K_WRITE(adr, KM_RLA(src));
    }

    public void opCode37() {
        int adr = KA_ZPX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_RLA(src));
    }

    public void opCode3B() {
        int adr = KA_ABSY();
        int src = K_READ(adr);
        K_WRITE(adr, KM_RLA(src));
    }

    public void opCode2F() {
        int adr = KA_ABS();
        int src = K_READ(adr);
        K_WRITE(adr, KM_RLA(src));
    }

    public void opCode3F() {
        int adr = KA_ABSX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_RLA(src));
    }

    /* --- SRE --- */
    /* shift right, EOR result */
    public int KM_SRE(int src) {
        int w = (src >> 1) & 0xff;
        this.a ^= w;
        this.p &= ~(int) (N_FLAG | Z_FLAG | C_FLAG);
        this.p |= FLAG_NZ(this.a);
        this.p |= src & C_FLAG;
        return w;
    }

    /* macro */
    public void opCode43() {
        int adr = KA_INDX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_SRE(src));
    }

    public void opCode53() {
        int adr = KA_INDY();
        int src = K_READ(adr);
        K_WRITE(adr, KM_SRE(src));
    }

    public void opCode47() {
        int adr = KA_ZP();
        int src = K_READ(adr);
        K_WRITE(adr, KM_SRE(src));
    }

    public void opCode57() {
        int adr = KA_ZPX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_SRE(src));
    }

    public void opCode5B() {
        int adr = KA_ABSY();
        int src = K_READ(adr);
        K_WRITE(adr, KM_SRE(src));
    }

    public void opCode4F() {
        int adr = KA_ABS();
        int src = K_READ(adr);
        K_WRITE(adr, KM_SRE(src));
    }

    public void opCode5F() {
        int adr = KA_ABSX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_SRE(src));
    }

    // RRA

    /* rotate right, ADC result */
    public int KM_RRA(int src) {
        int w = ((src >> 1) | ((this.p & C_FLAG) << 7)) & 0xff;
        this.p &= ~(int) (C_FLAG);
        this.p |= src & C_FLAG;
        KMI_ADC(w);
        return w;
    }

    /* macro */
    public void opCode63() {
        int adr = KA_INDX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_RRA(src));
    }

    public void opCode73() {
        int adr = KA_INDY();
        int src = K_READ(adr);
        K_WRITE(adr, KM_RRA(src));
    }

    public void opCode67() {
        int adr = KA_ZP();
        int src = K_READ(adr);
        K_WRITE(adr, KM_RRA(src));
    }

    public void opCode77() {
        int adr = KA_ZPX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_RRA(src));
    }

    public void opCode7B() {
        int adr = KA_ABSY();
        int src = K_READ(adr);
        K_WRITE(adr, KM_RRA(src));
    }

    public void opCode6F() {
        int adr = KA_ABS();
        int src = K_READ(adr);
        K_WRITE(adr, KM_RRA(src));
    }

    public void opCode7F() {
        int adr = KA_ABSX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_RRA(src));
    }

    // DCP

    /* decrement, CMP */
    public int KM_DCP(int src) {
        int w = (src - 1) & 0xff;
        KM_CMP(w);
        return w;
    }

    /* macro */
    public void opCodeC3() {
        int adr = KA_INDX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_DCP(src));
    }

    public void opCodeD3() {
        int adr = KA_INDY();
        int src = K_READ(adr);
        K_WRITE(adr, KM_DCP(src));
    }

    public void opCodeC7() {
        int adr = KA_ZP();
        int src = K_READ(adr);
        K_WRITE(adr, KM_DCP(src));
    }

    public void opCodeD7() {
        int adr = KA_ZPX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_DCP(src));
    }

    public void opCodeDB() {
        int adr = KA_ABSY();
        int src = K_READ(adr);
        K_WRITE(adr, KM_DCP(src));
    }

    public void opCodeCF() {
        int adr = KA_ABS();
        int src = K_READ(adr);
        K_WRITE(adr, KM_DCP(src));
    }

    public void opCodeDF() {
        int adr = KA_ABSX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_DCP(src));
    }

    /* --- ISC --- */
    /* increment, SBC */
    public int KM_ISC(int src) {
        int w = (src + 1) & 0xff;
        KMI_SBC(w);
        return w;
    }

    /* macro */
    public void opCodeE3() {
        int adr = KA_INDX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_ISC(src));
    }

    public void opCodeF3() {
        int adr = KA_INDY();
        int src = K_READ(adr);
        K_WRITE(adr, KM_ISC(src));
    }

    public void opCodeE7() {
        int adr = KA_ZP();
        int src = K_READ(adr);
        K_WRITE(adr, KM_ISC(src));
    }

    public void opCodeF7() {
        int adr = KA_ZPX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_ISC(src));
    }

    public void opCodeFB() {
        int adr = KA_ABSY();
        int src = K_READ(adr);
        K_WRITE(adr, KM_ISC(src));
    }

    public void opCodeEF() {
        int adr = KA_ABS();
        int src = K_READ(adr);
        K_WRITE(adr, KM_ISC(src));
    }

    public void opCodeFF() {
        int adr = KA_ABSX();
        int src = K_READ(adr);
        K_WRITE(adr, KM_ISC(src));
    }

    /* --- LAX --- */
    /* load A and X */
    public void KM_LAX(int src) {
        this.a = src;
        this.x = src;
        this.p &= ~(int) (N_FLAG | Z_FLAG);
        this.p |= FLAG_NZ(src);
    }

    /* macro */
    public void opCodeA3() {
        int adr = KA_INDX();
        int src = K_READ(adr);
        KM_LAX(src);
    }

    public void opCodeB3() {
        int adr = KA_INDY_();
        int src = K_READ(adr);
        KM_LAX(src);
    }

    public void opCodeA7() {
        int adr = KA_ZP();
        int src = K_READ(adr);
        KM_LAX(src);
    }

    public void opCodeB7() {
        int adr = KA_ZPY();
        int src = K_READ(adr);
        KM_LAX(src);
    }

    public void opCodeAB() {
        int adr = KA_IMM();
        int src = K_READ(adr);
        KM_LAX(src);
    }

    public void opCodeAF() {
        int adr = KA_ABS();
        int src = K_READ(adr);
        KM_LAX(src);
    }

    public void opCodeBF() {
        int adr = KA_ABSY_();
        int src = K_READ(adr);
        KM_LAX(src);
    }

    /* --- SAX --- */
    /* store A AND X */
    public void opCode83() {
        K_WRITE(KA_INDX(), (this.a & this.x));
    }

    public void opCode87() {
        K_WRITE(KA_ZP(), (this.a & this.x));
    }

    public void opCode97() {
        K_WRITE(KA_ZPY(), (this.a & this.x));
    }

    public void opCode8F() {
        K_WRITE(KA_ABS(), (this.a & this.x));
    }

    /* --- AHX --- */
    /* store A AND X AND high address (somewhat unstable) */
    public void opCode93() {
        int adr = KA_ZPY();
        K_WRITE(adr, (this.a & this.x & ((adr >> 8) + 1)) & 0xff);
    }

    public void opCode9F() {
        int adr = KA_ABSY();
        K_WRITE(adr, (this.a & this.x & ((adr >> 8) + 1)) & 0xff);
    }

    /* --- TAS --- */
    /* transfer A AND X to S, store A AND X AND high address */
    public void opCode9B() {
        int adr = KA_ABSY();
        this.s = this.a & this.x;
        K_WRITE(adr, (this.s & ((adr >> 8) + 1)) & 0xff);
    }

    /* --- SHY --- */
    /* store Y AND high address (somewhat unstable) */
    public void opCode9C() {
        int adr = KA_ABSX();
        K_WRITE(adr, (this.y & ((adr >> 8) + 1)) & 0xff);
    }

    /* --- SHX --- */
    /* store X AND high address (somewhat unstable) */
    public void opCode9E() {
        int adr = KA_ABSY();
        K_WRITE(adr, (this.x & ((adr >> 8) + 1)) & 0xff);
    }

    /* --- ANC --- */
    /* a = A AND immediate */
    public void opCode0B() {
        int adr = KA_IMM();
        this.a = (this.a & K_READ(adr)) & 0xff;
        this.p &= ~(int) (N_FLAG | Z_FLAG | C_FLAG);
        this.p |= FLAG_NZ(this.a);
        this.p |= (this.a >> 7); /* C_FLAG */
    }

    public void opCode2B() {
        int adr = KA_IMM();
        this.a = (this.a & K_READ(adr)) & 0xff;
        this.p &= ~(int) (N_FLAG | Z_FLAG | C_FLAG);
        this.p |= FLAG_NZ(this.a);
        this.p |= (this.a >> 7) & C_FLAG;
    }

    /* --- XAA --- */
    /* a = X AND immediate (unstable) */
    public void opCode8B() {
        int adr = KA_IMM();
        this.a = (this.x & K_READ(adr)) & 0xff;
        this.p &= ~(int) (N_FLAG | Z_FLAG);
        this.p |= FLAG_NZ(this.a);
    }

    /* --- ALR --- */
    /* A AND immediate (unstable), shift right */
    public void opCode4B() {
        int adr = KA_IMM();
        int res = (this.a & K_READ(adr)) & 0xff;
        this.a = res >> 1;
        this.p &= ~(int) (N_FLAG | Z_FLAG | C_FLAG);
        this.p |= FLAG_NZ(this.a);
        this.p |= (res & C_FLAG);
    }

    /* --- ARR --- */
    /* A AND immediate (unstable), rotate right, weird carry */
    public void opCode6B() {
        int adr = KA_IMM();
        int res = (this.a & K_READ(adr)) & 0xff;
        this.a = (res >> 1) + ((this.p & C_FLAG) << 7);
        this.p &= ~(int) (N_FLAG | V_FLAG | Z_FLAG | C_FLAG);
        this.p |= FLAG_NZ(this.a);
        this.p |= (res ^ (res >> 1)) & V_FLAG;
        this.p |= (res >> 7) & C_FLAG;
    }

    /* --- LAS --- */
    /* stack AND immediate, copy to A and X */
    public void opCodeBB() {
        int adr = KA_ABSY_();
        this.s &= (K_READ(adr)) & 0xff;
        this.a = this.s;
        this.x = this.s;
        this.p &= ~(int) (N_FLAG | Z_FLAG);
        this.p |= FLAG_NZ(this.a);
    }

    /* --- AXS --- */
    /* (A & X) - immediate, result in X */
    public void opCodeCB() {
        int adr = KA_IMM();
        int res = (this.a & this.x) - (K_READ(adr) & 0xff);
        this.x = (res) & 0xff;
        this.p &= ~(int) (N_FLAG | Z_FLAG | C_FLAG);
        this.p |= FLAG_NZ(this.x);
        this.p |= (res <= 0xFF) ? C_FLAG : 0;
    }

    /* --- SBC --- */
    /* EB is alternate opCode for SBC E9 */
    public void opCodeEB() {
        opCodeE9();
    }

    /*
     * m6502 clock cycle table (n) undefined OP-code +n +1 by page
     * boundary case BRK(#$00) +7 by interrupt BS - corrected NOP timings for
     * undefined opCodes
     */
    public byte[] cl_table = new byte[] {
        // L 0 1 2 3 4 5 6 7 8 9 A B C D E F H
        0, 6, (2), (8), (3), 3, 5, (5), 3, 2, 2, (2), (4), 4, 6, (6), // 0
        2, +5, (2), (8), (4), 4, 6, (6), 2, +4, (2), (7), (4), +4, 7, (7), // 1
        6, 6, (2), (8), 3, 3, 5, (5), 4, 2, 2, (2), 4, 4, 6, (6), // 2
        2, +5, (2), (8), (4), 4, 6, (6), 2, +4, (2), (7), (4), +4, 7, (7), // 3
        6, 6, (2), (8), (3), 3, 5, (5), 3, 2, 2, (2), 3, 4, 6, (6), // 4
        2, +5, (2), (8), (4), 4, 6, (6), 2, +4, (2), (7), (4), +4, 7, (7), // 5
        6, 6, (2), (8), (3), 3, 5, (5), 4, 2, 2, (2), 5, 4, 6, (6), // 6
        2, +5, (2), (8), (4), 4, 6, (6), 2, +4, (2), (7), (4), +4, 7, (7), // 7
        (2), 6, (2), (6), 3, 3, 3, (3), 2, (2), 2, (2), 4, 4, 4, (4), // 8
        2, 6, (2), (6), 4, 4, 4, (4), 2, 5, 2, (5), (5), 5, (5), (5), // 9
        2, 6, 2, (6), 3, 3, 3, (3), 2, 2, 2, (2), 4, 4, 4, (4), // A
        2, +5, (2), (5), 4, 4, 4, (4), 2, +4, 2, (4), +4, +4, +4, (4), // B
        2, 6, (2), (8), 3, 3, 5, (5), 2, 2, 2, (2), 4, 4, 6, (6), // C
        2, +5, (2), (8), (4), 4, 6, (6), 2, +4, (2), (7), (4), +4, 7, (7), // D
        2, 6, (2), (8), 3, 3, 5, (5), 2, 2, 2, (2), 4, 4, 6, (6), // E
        2, +5, (2), (8), (4), 4, 6, (6), 2, +4, (2), (7), (4), +4, 7, (7), // F
    };

    /* BS - allowing illegal opCode implementation */

    public void K_OPEXEC() {
        int opCode = this.lastcode = K_READ(KAI_IMM());
        KI_ADDCLOCK(cl_table[opCode]);
        switch (opCode) {
        case 0x00:
            opCode00();
            break;
        case 0x01:
            opCode01();
            break;
        case 0x02:
            opCode02();
            this.illegal = 1;
            break;
        case 0x03:
            opCode03();
            this.illegal = 1;
            break;
        case 0x04:
            opCode04();
            this.illegal = 1;
            break;
        case 0x05:
            opCode05();
            break;
        case 0x06:
            opCode06();
            break;
        case 0x07:
            opCode07();
            this.illegal = 1;
            break;
        case 0x08:
            opCode08();
            break;
        case 0x09:
            opCode09();
            break;
        case 0x0A:
            opCode0A();
            break;
        case 0x0B:
            opCode0B();
            this.illegal = 1;
            break;
        case 0x0C:
            opCode0C();
            this.illegal = 1;
            break;
        case 0x0D:
            opCode0D();
            break;
        case 0x0E:
            opCode0E();
            break;
        case 0x0F:
            opCode0F();
            this.illegal = 1;
            break;

        case 0x10:
            opCode10();
            break;
        case 0x11:
            opCode11();
            break;
        case 0x12:
            opCode12();
            this.illegal = 1;
            break;
        case 0x13:
            opCode13();
            this.illegal = 1;
            break;
        case 0x14:
            opCode14();
            this.illegal = 1;
            break;
        case 0x15:
            opCode15();
            break;
        case 0x16:
            opCode16();
            break;
        case 0x17:
            opCode17();
            this.illegal = 1;
            break;
        case 0x18:
            opCode18();
            break;
        case 0x19:
            opCode19();
            break;
        case 0x1A:
            opCode1A();
            this.illegal = 1;
            break;
        case 0x1B:
            opCode1B();
            this.illegal = 1;
            break;
        case 0x1C:
            opCode1C();
            this.illegal = 1;
            break;
        case 0x1D:
            opCode1D();
            break;
        case 0x1E:
            opCode1E();
            break;
        case 0x1F:
            opCode1F();
            this.illegal = 1;
            break;

        case 0x20:
            opCode20();
            break;
        case 0x21:
            opCode21();
            break;
        case 0x22:
            opCode22();
            this.illegal = 1;
            break;
        case 0x23:
            opCode23();
            this.illegal = 1;
            break;
        case 0x24:
            opCode24();
            break;
        case 0x25:
            opCode25();
            break;
        case 0x26:
            opCode26();
            break;
        case 0x27:
            opCode27();
            this.illegal = 1;
            break;
        case 0x28:
            opCode28();
            break;
        case 0x29:
            opCode29();
            break;
        case 0x2A:
            opCode2A();
            break;
        case 0x2B:
            opCode2B();
            this.illegal = 1;
            break;
        case 0x2C:
            opCode2C();
            break;
        case 0x2D:
            opCode2D();
            break;
        case 0x2E:
            opCode2E();
            break;
        case 0x2F:
            opCode2F();
            this.illegal = 1;
            break;

        case 0x30:
            opCode30();
            break;
        case 0x31:
            opCode31();
            break;
        case 0x32:
            opCode32();
            this.illegal = 1;
            break;
        case 0x33:
            opCode33();
            this.illegal = 1;
            break;
        case 0x34:
            opCode34();
            this.illegal = 1;
            break;
        case 0x35:
            opCode35();
            break;
        case 0x36:
            opCode36();
            break;
        case 0x37:
            opCode37();
            this.illegal = 1;
            break;
        case 0x38:
            opCode38();
            break;
        case 0x39:
            opCode39();
            break;
        case 0x3A:
            opCode3A();
            this.illegal = 1;
            break;
        case 0x3B:
            opCode3B();
            this.illegal = 1;
            break;
        case 0x3C:
            opCode3C();
            this.illegal = 1;
            break;
        case 0x3D:
            opCode3D();
            break;
        case 0x3E:
            opCode3E();
            break;
        case 0x3F:
            opCode3F();
            this.illegal = 1;
            break;

        case 0x40:
            opCode40();
            break;
        case 0x41:
            opCode41();
            break;
        case 0x42:
            opCode42();
            this.illegal = 1;
            break;
        case 0x43:
            opCode43();
            this.illegal = 1;
            break;
        case 0x44:
            opCode44();
            this.illegal = 1;
            break;
        case 0x45:
            opCode45();
            break;
        case 0x46:
            opCode46();
            break;
        case 0x47:
            opCode47();
            this.illegal = 1;
            break;
        case 0x48:
            opCode48();
            break;
        case 0x49:
            opCode49();
            break;
        case 0x4A:
            opCode4A();
            break;
        case 0x4B:
            opCode4B();
            this.illegal = 1;
            break;
        case 0x4C:
            opCode4C();
            break;
        case 0x4D:
            opCode4D();
            break;
        case 0x4E:
            opCode4E();
            break;
        case 0x4F:
            opCode4F();
            this.illegal = 1;
            break;

        case 0x50:
            opCode50();
            break;
        case 0x51:
            opCode51();
            break;
        case 0x52:
            opCode52();
            this.illegal = 1;
            break;
        case 0x53:
            opCode53();
            this.illegal = 1;
            break;
        case 0x54:
            opCode54();
            this.illegal = 1;
            break;
        case 0x55:
            opCode55();
            break;
        case 0x56:
            opCode56();
            break;
        case 0x57:
            opCode57();
            this.illegal = 1;
            break;
        case 0x58:
            opCode58();
            break;
        case 0x59:
            opCode59();
            break;
        case 0x5A:
            opCode5A();
            this.illegal = 1;
            break;
        case 0x5B:
            opCode5B();
            this.illegal = 1;
            break;
        case 0x5C:
            opCode5C();
            this.illegal = 1;
            break;
        case 0x5D:
            opCode5D();
            break;
        case 0x5E:
            opCode5E();
            break;
        case 0x5F:
            opCode5F();
            this.illegal = 1;
            break;

        case 0x60:
            opCode60();
            break;
        case 0x61:
            opCode61();
            break;
        case 0x62:
            opCode62();
            this.illegal = 1;
            break;
        case 0x63:
            opCode63();
            this.illegal = 1;
            break;
        case 0x64:
            opCode64();
            this.illegal = 1;
            break;
        case 0x65:
            opCode65();
            break;
        case 0x66:
            opCode66();
            break;
        case 0x67:
            opCode67();
            this.illegal = 1;
            break;
        case 0x68:
            opCode68();
            break;
        case 0x69:
            opCode69();
            break;
        case 0x6A:
            opCode6A();
            break;
        case 0x6B:
            opCode6B();
            this.illegal = 1;
            break;
        case 0x6C:
            opCode6C();
            break;
        case 0x6D:
            opCode6D();
            break;
        case 0x6E:
            opCode6E();
            break;
        case 0x6F:
            opCode6F();
            this.illegal = 1;
            break;

        case 0x70:
            opCode70();
            break;
        case 0x71:
            opCode71();
            break;
        case 0x72:
            opCode72();
            this.illegal = 1;
            break;
        case 0x73:
            opCode73();
            this.illegal = 1;
            break;
        case 0x74:
            opCode74();
            this.illegal = 1;
            break;
        case 0x75:
            opCode75();
            break;
        case 0x76:
            opCode76();
            break;
        case 0x77:
            opCode77();
            this.illegal = 1;
            break;
        case 0x78:
            opCode78();
            break;
        case 0x79:
            opCode79();
            break;
        case 0x7A:
            opCode7A();
            this.illegal = 1;
            break;
        case 0x7B:
            opCode7B();
            this.illegal = 1;
            break;
        case 0x7C:
            opCode7C();
            this.illegal = 1;
            break;
        case 0x7D:
            opCode7D();
            break;
        case 0x7E:
            opCode7E();
            break;
        case 0x7F:
            opCode7F();
            this.illegal = 1;
            break;

        case 0x80:
            opCode80();
            this.illegal = 1;
            break;
        case 0x81:
            opCode81();
            break;
        case 0x82:
            opCode82();
            this.illegal = 1;
            break;
        case 0x83:
            opCode83();
            this.illegal = 1;
            break;
        case 0x84:
            opCode84();
            break;
        case 0x85:
            opCode85();
            break;
        case 0x86:
            opCode86();
            break;
        case 0x87:
            opCode87();
            this.illegal = 1;
            break;
        case 0x88:
            opCode88();
            break;
        case 0x89:
            opCode89();
            this.illegal = 1;
            break;
        case 0x8A:
            opCode8A();
            break;
        case 0x8B:
            opCode8B();
            this.illegal = 1;
            break;
        case 0x8C:
            opCode8C();
            break;
        case 0x8D:
            opCode8D();
            break;
        case 0x8E:
            opCode8E();
            break;
        case 0x8F:
            opCode8F();
            this.illegal = 1;
            break;

        case 0x90:
            opCode90();
            break;
        case 0x91:
            opCode91();
            break;
        case 0x92:
            opCode92();
            this.illegal = 1;
            break;
        case 0x93:
            opCode93();
            this.illegal = 1;
            break;
        case 0x94:
            opCode94();
            break;
        case 0x95:
            opCode95();
            break;
        case 0x96:
            opCode96();
            break;
        case 0x97:
            opCode97();
            this.illegal = 1;
            break;
        case 0x98:
            opCode98();
            break;
        case 0x99:
            opCode99();
            break;
        case 0x9A:
            opCode9A();
            break;
        case 0x9B:
            opCode9B();
            this.illegal = 1;
            break;
        case 0x9C:
            opCode9C();
            this.illegal = 1;
            break;
        case 0x9D:
            opCode9D();
            break;
        case 0x9E:
            opCode9E();
            this.illegal = 1;
            break;
        case 0x9F:
            opCode9F();
            this.illegal = 1;
            break;

        case 0xA0:
            opCodeA0();
            break;
        case 0xA1:
            opCodeA1();
            break;
        case 0xA2:
            opCodeA2();
            break;
        case 0xA3:
            opCodeA3();
            this.illegal = 1;
            break;
        case 0xA4:
            opCodeA4();
            break;
        case 0xA5:
            opCodeA5();
            break;
        case 0xA6:
            opCodeA6();
            break;
        case 0xA7:
            opCodeA7();
            this.illegal = 1;
            break;
        case 0xA8:
            opCodeA8();
            break;
        case 0xA9:
            opCodeA9();
            break;
        case 0xAA:
            opCodeAA();
            break;
        case 0xAB:
            opCodeAB();
            this.illegal = 1;
            break;
        case 0xAC:
            opCodeAC();
            break;
        case 0xAD:
            opCodeAD();
            break;
        case 0xAE:
            opCodeAE();
            break;
        case 0xAF:
            opCodeAF();
            this.illegal = 1;
            break;

        case 0xB0:
            opCodeB0();
            break;
        case 0xB1:
            opCodeB1();
            break;
        case 0xB2:
            opCodeB2();
            this.illegal = 1;
            break;
        case 0xB3:
            opCodeB3();
            this.illegal = 1;
            break;
        case 0xB4:
            opCodeB4();
            break;
        case 0xB5:
            opCodeB5();
            break;
        case 0xB6:
            opCodeB6();
            break;
        case 0xB7:
            opCodeB7();
            this.illegal = 1;
            break;
        case 0xB8:
            opCodeB8();
            break;
        case 0xB9:
            opCodeB9();
            break;
        case 0xBA:
            opCodeBA();
            break;
        case 0xBB:
            opCodeBB();
            this.illegal = 1;
            break;
        case 0xBC:
            opCodeBC();
            break;
        case 0xBD:
            opCodeBD();
            break;
        case 0xBE:
            opCodeBE();
            break;
        case 0xBF:
            opCodeBF();
            this.illegal = 1;
            break;

        case 0xC0:
            opCodeC0();
            break;
        case 0xC1:
            opCodeC1();
            break;
        case 0xC2:
            opCodeC2();
            this.illegal = 1;
            break;
        case 0xC3:
            opCodeC3();
            this.illegal = 1;
            break;
        case 0xC4:
            opCodeC4();
            break;
        case 0xC5:
            opCodeC5();
            break;
        case 0xC6:
            opCodeC6();
            break;
        case 0xC7:
            opCodeC7();
            this.illegal = 1;
            break;
        case 0xC8:
            opCodeC8();
            break;
        case 0xC9:
            opCodeC9();
            break;
        case 0xCA:
            opCodeCA();
            break;
        case 0xCB:
            opCodeCB();
            this.illegal = 1;
            break;
        case 0xCC:
            opCodeCC();
            break;
        case 0xCD:
            opCodeCD();
            break;
        case 0xCE:
            opCodeCE();
            break;
        case 0xCF:
            opCodeCF();
            this.illegal = 1;
            break;

        case 0xD0:
            opCodeD0();
            break;
        case 0xD1:
            opCodeD1();
            break;
        case 0xD2:
            opCodeD2();
            this.illegal = 1;
            break;
        case 0xD3:
            opCodeD3();
            this.illegal = 1;
            break;
        case 0xD4:
            opCodeD4();
            this.illegal = 1;
            break;
        case 0xD5:
            opCodeD5();
            break;
        case 0xD6:
            opCodeD6();
            break;
        case 0xD7:
            opCodeD7();
            this.illegal = 1;
            break;
        case 0xD8:
            opCodeD8();
            break;
        case 0xD9:
            opCodeD9();
            break;
        case 0xDA:
            opCodeDA();
            this.illegal = 1;
            break;
        case 0xDB:
            opCodeDB();
            this.illegal = 1;
            break;
        case 0xDC:
            opCodeDC();
            this.illegal = 1;
            break;
        case 0xDD:
            opCodeDD();
            break;
        case 0xDE:
            opCodeDE();
            break;
        case 0xDF:
            opCodeDF();
            this.illegal = 1;
            break;

        case 0xE0:
            opCodeE0();
            break;
        case 0xE1:
            opCodeE1();
            break;
        case 0xE2:
            opCodeE2();
            this.illegal = 1;
            break;
        case 0xE3:
            opCodeE3();
            this.illegal = 1;
            break;
        case 0xE4:
            opCodeE4();
            break;
        case 0xE5:
            opCodeE5();
            break;
        case 0xE6:
            opCodeE6();
            break;
        case 0xE7:
            opCodeE7();
            this.illegal = 1;
            break;
        case 0xE8:
            opCodeE8();
            break;
        case 0xE9:
            opCodeE9();
            break;
        case 0xEA:
            opCodeEA();
            break;
        case 0xEB:
            opCodeEB();
            this.illegal = 1;
            break;
        case 0xEC:
            opCodeEC();
            break;
        case 0xED:
            opCodeED();
            break;
        case 0xEE:
            opCodeEE();
            break;
        case 0xEF:
            opCodeEF();
            this.illegal = 1;
            break;

        case 0xF0:
            opCodeF0();
            break;
        case 0xF1:
            opCodeF1();
            break;
        case 0xF2:
            opCodeF2();
            this.illegal = 1;
            break;
        case 0xF3:
            opCodeF3();
            this.illegal = 1;
            break;
        case 0xF4:
            opCodeF4();
            this.illegal = 1;
            break;
        case 0xF5:
            opCodeF5();
            break;
        case 0xF6:
            opCodeF6();
            break;
        case 0xF7:
            opCodeF7();
            this.illegal = 1;
            break;
        case 0xF8:
            opCodeF8();
            break;
        case 0xF9:
            opCodeF9();
            break;
        case 0xFA:
            opCodeFA();
            this.illegal = 1;
            break;
        case 0xFB:
            opCodeFB();
            this.illegal = 1;
            break;
        case 0xFC:
            opCodeFC();
            this.illegal = 1;
            break;
        case 0xFD:
            opCodeFD();
            break;
        case 0xFE:
            opCodeFE();
            break;
        case 0xFF:
            opCodeFF();
            this.illegal = 1;
            break;
        }
    }

    public void exec() {
        if (this.iRequest != 0) {
            if ((this.iRequest & IRQ_INIT) != 0) {
                this.a = 0;
                this.x = 0;
                this.y = 0;
                this.s = 0xFF;
                this.p = Z_FLAG | R_FLAG | I_FLAG;
                this.iRequest = 0;
                this.iMask = ~(int) 0;
                KI_ADDCLOCK(7);
                return;
            } else if ((this.iRequest & IRQ_RESET) != 0) {
                this.a = 0;
                this.x = 0;
                this.y = 0;
                this.s = 0xFF;
                this.p = Z_FLAG | R_FLAG | I_FLAG;
                this.pc = KI_READWORD(VEC_RESET);
                this.iRequest = 0;
                this.iMask = ~(int) 0;
            } else if ((this.iRequest & IRQ_NMI) != 0) {
                KM_PUSH((this.pc >> 8) & 0xff);
                KM_PUSH((this.pc) & 0xff);
                KM_PUSH(this.p | R_FLAG | B_FLAG);
                this.p = (this.p & ~T_FLAG) | I_FLAG; /* 6502 bug */
                this.iRequest &= ~(int) (IRQ_NMI | IRQ_BRK);
                this.pc = KI_READWORD(VEC_NMI);
                KI_ADDCLOCK(7);
            } else if ((this.iRequest & IRQ_BRK) != 0) {
                KM_PUSH((this.pc >> 8) & 0xff);
                KM_PUSH((this.pc) & 0xff);
                KM_PUSH(this.p | R_FLAG | B_FLAG);
                this.p = (this.p & ~T_FLAG) | I_FLAG; // 6502 bug
                this.iRequest &= ~(int) IRQ_BRK;
                this.pc = KI_READWORD(VEC_BRK);
                KI_ADDCLOCK(7);
            } else if ((this.p & I_FLAG) != 0) {
                /* interrupt disabled */
            } else if ((this.iMask & this.iRequest & IRQ_INT) != 0) {
                KM_PUSH((this.pc >> 8) & 0xff);
                KM_PUSH((this.pc) & 0xff);
                KM_PUSH((this.p | R_FLAG) & ~B_FLAG);
                this.p = (this.p & ~T_FLAG) | I_FLAG; // 6502 bug
                this.iRequest &= ~(int) IRQ_INT;
                this.pc = KI_READWORD(VEC_INT);
                KI_ADDCLOCK(7);
            }
        }
        K_OPEXEC();
    }

    protected int intAddress;
    protected boolean breaked;
    protected int clockPerFrame;
    protected int clockOfFrame;
    protected int frameQuarter;
    protected int breakPoint;
    protected Device bus;

    public double NES_BASECYCLES;

    private static final double DEFAULT_CLOCK = 1789773.0;
    private static final int DEFAULT_RATE = 44100;

    static final String[] OP_NAME = new String[] {
            // 0x-0 0x-1 0x-2 0x-3 0x-4 0x-5 0x-6 0x-7 0x-8 0x-9 0x-A 0x-B 0x-C 0x-D 0x-E 0x-F
        "BRK", "ORA", "kil", "slo", "nop", "ORA", "ASL", "slo", "PHP", "ORA", "ASL", "anc", "nop", "ORA", "ASL", "slo", // 0x0-
        "BPL", "ORA", "kil", "slo", "nop", "ORA", "ASL", "slo", "CLC", "ORA", "nop", "slo", "nop", "ORA", "ASL", "slo", // 0x1-
        "JSR", "AND", "kil", "rla", "BIT", "AND", "ROL", "rla", "PLP", "AND", "ROL", "anc", "BIT", "AND", "ROL", "rla", // 0x2-
        "BMI", "AND", "kil", "rla", "nop", "AND", "ROL", "rla", "SEC", "AND", "nop", "rla", "nop", "AND", "ROL", "rla", // 0x3-
        "RTI", "EOR", "kil", "sre", "nop", "EOR", "LSR", "sre", "PHA", "EOR", "LSR", "alr", "JMP", "EOR", "LSR", "sre", // 0x4-
        "BVC", "EOR", "kil", "sre", "nop", "EOR", "LSR", "sre", "CLI", "EOR", "nop", "sre", "nop", "EOR", "LSR", "sre", // 0x5-
        "RTS", "ADC", "kil", "rra", "nop", "ADC", "ROR", "rra", "PLA", "ADC", "ROR", "arr", "JMP", "ADC", "ROR", "rra", // 0x6-
        "BVS", "ADC", "kil", "rra", "nop", "ADC", "ROR", "rra", "SEI", "ADC", "nop", "rra", "nop", "ADC", "ROR", "rra", // 0x7-
        "nop", "STA", "nop", "sax", "STY", "STA", "STX", "sax", "DEY", "nop", "TXA", "xaa", "STY", "STA", "STX", "sax", // 0x8-
        "BCC", "STA", "kil", "ahx", "STY", "STA", "STX", "sax", "TYA", "STA", "TXS", "tas", "shy", "STA", "shx", "ahx", // 0x9-
        "LDY", "LDA", "LDX", "lax", "LDY", "LDA", "LDX", "lax", "TAY", "LDA", "TAX", "lax", "LDY", "LDA", "LDX", "lax", // 0xA-
        "BCS", "LDA", "kil", "lax", "LDY", "LDA", "LDX", "lax", "CLV", "LDA", "TSX", "las", "LDY", "LDA", "LDX", "lax", // 0xB-
        "CPY", "CMP", "nop", "dcp", "CPY", "CMP", "DEC", "dcp", "INY", "CMP", "DEX", "axs", "CPY", "CMP", "DEC", "dcp", // 0xC-
        "BNE", "CMP", "kil", "dcp", "nop", "CMP", "DEC", "dcp", "CLD", "CMP", "nop", "dcp", "nop", "CMP", "DEC", "dcp", // 0xD-
        "CPX", "SBC", "nop", "isc", "CPX", "SBC", "INC", "isc", "INX", "SBC", "NOP", "sbc", "CPX", "SBC", "INC", "isc", // 0xE-
        "BEQ", "SBC", "kil", "isc", "nop", "SBC", "INC", "isc", "SED", "SBC", "nop", "isc", "nop", "SBC", "INC", "isc" // 0xF-
    };

    public Km6502(double clock/* =DEFAULT_CLOCK */) {
        NES_BASECYCLES = clock;
        bus = null;
    }

    protected void finalize() {
    }

    private void writeByte(int adr, int val) {
        write(adr, val, 0);
    }

    private int readByte(int adr) {
        int[] val = new int[] { 0 };
        read(adr, val, 0);
        return val[0];
    }

    public void startup(int address) {
        breaked = false;
        this.pc = 0x4100;
        breakPoint = this.pc + 3;
        this.p = 0x26; // IRZ
                          // assert(bus);
        bus.write(this.pc + 0, 0x20, 0); // JSR
        bus.write(this.pc + 1, address & 0xff, 0);
        bus.write(this.pc + 2, address >> 8, 0);
        bus.write(this.pc + 3, 0x4c, 0); // JMP 04103H
        bus.write(this.pc + 4, breakPoint & 0xff, 0);
        bus.write(this.pc + 5, breakPoint >> 8, 0);
    }

    public int exec(int clock) {
        this.clock = 0;

        while (this.clock < clock) {
            if (!breaked) {
// System.err.pritnf("PC: 0x%04X\n", this.PC);

//                int tpc = this.pc;
//                int[][] tb = new int[3][1];
//                bus.read((tpc + 0) & 0xFFFF, tb[0]);
//                bus.read((tpc + 1) & 0xFFFF, tb[1]);
//                bus.read((tpc + 2) & 0xFFFF, tb[2]);
//                System.err.printf("%04x: A=%02x X=%02x Y=%02x P=%02x S=%02x %s > ",
//                                  this.pc,
//                                  this.a,
//                                  this.x,
//                                  this.y,
//                                  this.p,
//                                  this.s,
//                                  this.iRequest != 0 ? 'I' : 'i');

                exec();

//                System.err.printf("%s", OP_NAME[this.lastcode]);
//                int oplen = this.pc - tpc;
//                for (int i = 0; i < 3; ++i) {
//                    if (i == 0 || i < oplen) {
//                        System.err.printf(" %02x", tb[i][0]);
//                    }
//                }
//                System.err.println();

                if (this.pc == breakPoint)
                    breaked = true;
            } else {
                if ((clockOfFrame >> 16) < clock)
                    this.clock = (clockOfFrame >> 16) + 1;
                else
                    this.clock = clock;
            }

            // フレームクロックに到達
            if ((clockOfFrame >> 16) < this.clock) {
                if (breaked) {
                    // if (log_cpu!=null)
                    // log_cpu.Play();

                    startup(intAddress);
                }
                clockOfFrame += clockPerFrame;
                // System.err.pritnf("NMI\n");
            }
        }

        clockOfFrame -= this.clock << 16;

        return this.clock; // return actual number of clocks executed
    }

    public void setMemory(Device b) {
        bus = b;
    }

    @Override
    public boolean write(int adr, int val, int id) {
// System.err.pritnf("Write: 0x%04X = 0x%02X\n", adr, val);

        // for blargg's CPU tests
// #if 0
        // if (adr == 0x6000) {
        //  System.err.pritnf("Blargg result: %02X [");
        // int msg = 0x6004;
        // do {
        //  int ic;
        //  read(msg, ic);
        //  if (ic == 0) break;
        //  ++msg;
        // System.err.pritnf("%c", char(ic));
        // } while (1);
        //  System.err.pritnf("]\n");
        //  return false;
        // }
// #endif

        if (bus != null)
            return bus.write(adr, val, id);
        else
            return false;
    }

    @Override
    public boolean read(int adr, int[] val, int id) {
        if (bus != null) {
            boolean result = bus.read(adr, val, id);

// System.err.pritnf(" Read: 0x%04X = 0x%02X\n", adr, val);

            return result;
        } else
            return false;
    }

    // KM6502のリセット
    @Override
    public void reset() {
        this.readByte = this::readByte;
        this.writeByte = this::writeByte;
        this.iRequest = IRQ.INIT.v;
        this.clock = 0;
        this.a = 0;
        this.x = 0;
        this.y = 0;
        this.s = 0xff;
        this.pc = breakPoint = 0xffff;
        this.illegal = 0;
        breaked = false;
        exec();
    }

    public void start(int startAdr,
                      int intAdr,
                      double intFreq/* =60.0 */,
                      int a/* =0 */,
                      int x/* =0 */,
                      int y/* =0 */) {
        // 割り込みアドレス設定
        intAddress = intAdr;
        clockPerFrame = (int) (((1 << 16) * NES_BASECYCLES) / intFreq);
        clockOfFrame = 0;

        // count clock quarters
        frameQuarter = 3;

// log_cpu.Init(a, x);

        this.a = a;
        this.x = x;
        this.y = y;
        startup(startAdr);

        for (int i = 0; (i < (NES_BASECYCLES / intFreq)) && !breaked; i++, exec()) {
// #if TRACE
//            int tpc = this.pc;
//            int[][] tb = new int[3][1];
//            bus.read((tpc + 0) & 0xFFFF, tb[0]);
//            bus.read((tpc + 1) & 0xFFFF, tb[1]);
//            bus.read((tpc + 2) & 0xFFFF, tb[2]);
//            System.err.printf("%04x: A=%02x X=%02x Y=%02x P=%02x S=%02x %s > ",
//                              this.pc,
//                              this.a,
//                              this.x,
//                              this.y,
//                              this.p,
//                              this.s,
//                              this.iRequest != 0 ? 'I' : 'i');
// #endif
            if (this.pc == breakPoint) {
                breaked = true;
            }
        }

        clockOfFrame = 0;
    }

    @Override
    public void setOption(int id, int val) {
        throw new UnsupportedOperationException();
    }
}
