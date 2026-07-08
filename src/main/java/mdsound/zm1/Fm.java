package mdsound.zm1;

public class Fm extends ChipElement {

    private byte ar = 0;

    public byte getAr() {
        return ar;
    }

    public void setAr(byte value) {
        ar = (byte) (value & 0x1f);
    }

    private byte d1r = 0;

    public byte getD1r() {
        return d1r;
    }

    public void setD1r(byte value) {
        d1r = (byte) (value & 0x1f);
    }

    private byte d2r = 0;

    public byte getD2r() {
        return d2r;
    }

    public void setD2r(byte value) {
        d2r = (byte) (value & 0x1f);
    }

    private byte d1l = 0;

    public byte getD1l() {
        return d1l;
    }

    public void setD1l(byte value) {
        d1l = (byte) (value & 0x0f);
    }

    private byte rr = 0;

    public byte getRr() {
        return rr;
    }

    public void setRr(byte value) {
        rr = (byte) (value & 0x0f);
    }

    private byte tl = 0;

    public byte getTl() {
        return tl;
    }

    public void setTl(byte value) {
        tl = (byte) (value & 0x7f);
    }

    private byte mul = 0;

    public byte getMul() {
        return mul;
    }

    public void setMul(byte value) {
        mul = (byte) (value & 0x0f);
    }

    private byte dt12 = 0;

    public byte getDt12() {
        return dt12;
    }

    public void setDt12(byte value) {
        dt12 = (byte) (value & 0x1f);
    }

    private byte ksAmsen = 0;

    public byte getKsAmsen() {
        return ksAmsen;
    }

    public void setKsAmsen(byte value) {
        ksAmsen = (byte) (value & 0x07);
    }

    private byte pmsAms = 0;

    public byte getPmsAms() {
        return pmsAms;
    }

    public void setPmsAms(byte value) {
        pmsAms = (byte) (value & 0x1f);
    }

    private byte ws = 0;

    public byte getWs() {
        return ws;
    }

    public void setWs(byte value) {
        ws = (byte) (value & 0x0f);
    }

    private byte pmdAmd = 0;

    public Fm(Operator operator) {
        super(operator);
    }

    public byte getPmdAmd() {
        return pmdAmd;
    }

    public void setPmdAmd(byte value) {
        pmdAmd = (byte) (value & 0xff);
    }

    @Override
    public void write(int adress, int data) {
        switch (adress) {
            case 0x00:
                ar = (byte) (data & 0x1f);
                break;
            case 0x01:
                d1r = (byte) (data & 0x1f);
                break;
            case 0x02:
                d2r = (byte) (data & 0x1f);
                break;
            case 0x03:
                d1l = (byte) (data & 0x0f);
                break;
            case 0x04:
                rr = (byte) (data & 0x0f);
                break;
            case 0x05:
                tl = (byte) (data & 0x7f);
                break;
            case 0x06:
                mul = (byte) (data & 0x0f);
                break;
            case 0x07:
                dt12 = (byte) (data & 0x1f);
                break;
            case 0x08:
                ksAmsen = (byte) (data & 0x07);
                break;
            case 0x09:
                pmsAms = (byte) (data & 0x1f);
                break;
            case 0x0a:
                ws = (byte) (data & 0x0f);
                break;
            case 0x0b:
                pmdAmd = (byte) (data & 0xff);
                break;

            default:
                throw new IllegalArgumentException("The address specification is incorrect");
        }
    }
}
