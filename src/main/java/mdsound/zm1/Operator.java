package mdsound.zm1;

import java.util.List;


public class Operator {

    public Fm fm;
    public Pcm pcm;
    public SlotConfiguration sc;
    public int number;
    public ZelMusic.commonParam cp;

    private List<Byte> pCMData;

    public Operator(int number, ZelMusic.commonParam cp) { //, List<byte> pCMData, int playClock, int chipClock)
        this.cp = cp;
        this.number = number;
        this.pCMData = cp.pcmData;
        fm = new Fm(this);
        pcm = new Pcm(this, pCMData);
        pcm.setRate(cp.chipClock, cp.playClock);
        sc = new SlotConfiguration(this);
    }

    private int noteByteMatrix = 0;

    public int getNoteByteMatrix() {
        return noteByteMatrix;
    }

    public void setNoteByteMatrix(int value) {
        noteByteMatrix = value;
    }

    private byte keyFrqmode = 0;

    public byte getKeyFrqmode() {
        return keyFrqmode;
    }

    public void setKeyFrqmode(byte value) {
        keyFrqmode = value;
    }

    private boolean keyOnFlg = false;

    public boolean isKeyOnFlg() {
        return keyOnFlg;
    }

    public void setKeyOnFlg(boolean value) {
        keyOnFlg = value;
    }

    public void update(int[][] outputs, int samples) {
        pcm.update(outputs, samples);
    }
}
