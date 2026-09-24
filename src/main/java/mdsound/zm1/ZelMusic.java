package mdsound.zm1;

import java.util.ArrayList;
import java.util.List;


public class ZelMusic {

    private static final int MAX_OPERATOR = 48;
    public static final long MAX_PCMDATASIZE = 0x1_0000_0000L;

    private Operator[] ope = null;

    private final commonParam cp = new commonParam();

    public static class commonParam {

        public List<Byte> pcmData = null;
        public int playClock;
        public int chipClock;
        public int sysPcmVol;
    }

    public void reset() {
        setSystemVolumePCM(0);

        cp.pcmData = new ArrayList<>();
        ope = new Operator[MAX_OPERATOR];
        for (int i = 0; i < MAX_OPERATOR; i++)
            ope[i] = new Operator(i, cp); //, commonParam.PCMData[ChipID], commonParam.playClock, commonParam.chipClock);
    }

    public void start(int playClock, int chipClock) {
        cp.playClock = playClock;
        cp.chipClock = chipClock;
    }

    public void stop() {
        ope = null;
        if (cp.pcmData != null) cp.pcmData.clear();
    }

    public void update(int[][] outputs, int samples) {
        if (ope == null) return; // not reset yet or stopped
        for (int op = 0; op < MAX_OPERATOR; op++) {
            ope[op].update(outputs, samples);
        }
    }

    public int write(int bank, int adr, int data) {
        switch (bank) {
            case 0: // BANK A
                writeBankA(adr, data);
                break;
            case 1: // BANK B
                writeBankB(adr, data);
                break;
            case 2: // BANK C
                writeBankC(adr, data);
                break;
            case 3: // BANK D
                writeBankD(adr, data);
                break;
        }
        return 0;
    }

    public void setPCMData(byte[] data) {
        cp.pcmData = new ArrayList<>(data.length);
        for (byte b : data) {
            cp.pcmData.add(b);
        }
    }

    /**
     * @param db
     */
    public void setSystemVolumePCM(int db) {
        db = Math.min(db, 20);
        if (db > -192)
            cp.sysPcmVol = (int) (65536.0 * Math.pow(10.0, db / 40.0));
        else
            cp.sysPcmVol = 0;
    }

    private void writeBankA(int adr, int data) {
        // TODO system registers are not specified yet, ignored
    }

    private void writeBankB(int adr, int data) {
        if (ope == null) return;
        int opNum = adr / 0x100;
        if (opNum < 0 || opNum >= MAX_OPERATOR) return;
        int opAdr = adr % 0x100;
        int opTyp = opAdr < 0x80 ? 0 : (opAdr < 0xf0 ? 1 : 2);

        switch (opTyp) {
            case 0:
                ope[opNum].fm.write((byte) (opAdr - 0x00), (byte) data);
                break;
            case 1:
                ope[opNum].pcm.write((byte) (opAdr - 0x80), data);
                break;
            case 2:
                ope[opNum].sc.write((byte) (opAdr - 0xf0), (byte) data);
                break;
        }
    }

    private void writeBankC(int adr, int data) {
        if (adr < 0) return;
        if (cp.pcmData == null) cp.pcmData = new ArrayList<>();
        if (adr >= cp.pcmData.size()) {
            long size = adr - cp.pcmData.size() + 1;
            for (int i = 0; i < size; i++)
                cp.pcmData.add((byte) 0);
        }

        cp.pcmData.set(adr, (byte) data);
    }

    private void writeBankD(int adr, int data) {
        if (ope == null || adr < 0) return;

        if (adr < MAX_OPERATOR * 3) {
            // note byte matrix, 3 bytes per operator, little endian
            Operator o = ope[adr / 3];
            int shift = (adr % 3) * 8;
            int d = o.getNoteByteMatrix();
            d &= ~(0xff << shift);
            d |= (data & 0xff) << shift;
            o.setNoteByteMatrix(d);
        } else if (adr < MAX_OPERATOR * 4) {
            // key on / frequency mode
            Operator o = ope[adr - MAX_OPERATOR * 3];
            o.setKeyFrqmode((byte) data);
            if (!o.isKeyOnFlg()) {
                // off > on  --> true
                // off > off --> false
                o.setKeyOnFlg((data & 0x80) != 0);
            } else {
                // on > on  --> false
                // on > off --> false
                o.setKeyOnFlg(false);
            }
        }
    }
}
