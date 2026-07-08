package mdsound.zm1;


public class SlotConfiguration extends ChipElement {

    private byte leftVolume = 0;

    public byte getLeftVolume() {
        return leftVolume;
    }

    public void setLeftVolume(byte value) {
        leftVolume = value;
    }

    private byte rightVolume = 0;

    public byte getRightVolume() {
        return rightVolume;
    }

    public void setRightVolume(byte value) {
        rightVolume = value;
    }

    private byte lfofrq = 0;

    public byte getLfofrq() {
        return lfofrq;
    }

    public void setLfofrq(byte value) {
        lfofrq = value;
    }

    private byte lpfFilter = 0;

    public byte getLpfFilter() {
        return lpfFilter;
    }

    public void setLpfFilter(byte value) {
        lpfFilter = value;
    }

    private byte hpfFilter = 0;

    public byte getHpfFilter() {
        return hpfFilter;
    }

    public void setHpfFilter(byte value) {
        hpfFilter = value;
    }

    private byte lfosw = 0;

    public byte getLfosw() {
        return lfosw;
    }

    public void setLfosw(byte value) {
        lfosw = value;
    }

    private byte eff1val = 0;

    public byte getEff1val() {
        return eff1val;
    }

    public void setEff1val(byte value) {
        eff1val = value;
    }

    private byte eff2val = 0;

    public byte getEff2val() {
        return eff2val;
    }

    public void setEff2val(byte value) {
        eff2val = value;
    }

    public SlotConfiguration(Operator operator) {
        super(operator);
    }

    // TBD
    @Override
    public void write(int address, int data) {
        switch (address) {
            case 0x00:
                leftVolume = (byte) data;
                break;
            case 0x01:
                rightVolume = (byte) data;
                break;
            case 0x02:
                lfofrq = (byte) data;
                break;
            case 0x03:
                lpfFilter = (byte) data;
                break;
            case 0x04:
                hpfFilter = (byte) data;
                break;
            case 0x05:
                lfosw = (byte) data;
                break;
            case 0x06:
                eff1val = (byte) data;
                break;
            case 0x07:
                eff2val = (byte) data;
                break;

            default:
                throw new IllegalArgumentException("The address specification is incorrect");
        }
    }
}
