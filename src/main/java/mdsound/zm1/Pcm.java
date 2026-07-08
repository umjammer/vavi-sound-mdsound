package mdsound.zm1;

import java.util.List;


public class Pcm extends ChipElement {

    private boolean oldKeyOn;
    private int playPtr;
    private boolean play;
    private int adplbase;

    private int adplc; // Variables for frequency conversion
    private int adpld;
    private int deltan;

    private byte pcmMode = 0;

    public byte getPcmMode() {
        return pcmMode;
    }

    public void setPcmMode(byte value) {
        pcmMode = value;
    }

    private int playAddress = 0;

    public int getPlayAddress() {
        return playAddress;
    }

    public void setPlayAddress(int value) {
        playAddress = value;
    }

    private int stopAddress = 0;

    public int getStopAddress() {
        return stopAddress;
    }

    public void setStopAddress(int value) {
        stopAddress = value;
    }

    private int loopAddress = 0;

    public int getLoopAddress() {
        return loopAddress;
    }

    public void setLoopAddress(int value) {
        loopAddress = value;
    }

    private int keyOffAddress = 0;

    public int getKeyOffAddress() {
        return keyOffAddress;
    }

    public void setKeyOffAddress(int value) {
        keyOffAddress = value;
    }

    private byte pcmConfig = 0;

    public byte getPcmConfig() {
        return pcmConfig;
    }

    public void setPcmConfig(byte value) {
        pcmConfig = value;
    }

    private byte effectConfiguration = 0;

    public byte getEffectConfiguration() {
        return effectConfiguration;
    }

    public void setEffectConfiguration(byte value) {
        effectConfiguration = value;
    }

    private List<Byte> pCMData;

    private static int[] deltaKcTable = {
            76296, 80784, 85540, 90580, 90580, 95920, 101576, 107568,
            53784 * 2, 56958 * 2, 60322 * 2, 63724 * 2, 63724 * 2, 67660 * 2, 71658 * 2, 76296 * 2
    };

    public Pcm(Operator operator, List<Byte> pCMData) {
        super(operator);
        this.pCMData = pCMData;
        oldKeyOn = false;
        play = false;
        deltan = 256;
    }

    // fmgen
    public void setRate(int chipClock, int playClock) {
        adplbase = (int) ((int) (8192.0 * (chipClock / 72.0) / playClock));
        adpld = deltan * adplbase >> 16;
    }

    @Override
    public void write(int address, int data) {
        switch (address) {
            case 0x00:
                pcmMode = (byte) data;
                break;
            case 0x01:
            case 0x02:
            case 0x03:
            case 0x04:
                playAddress &= (int) ~(0x0000_00ff << ((address - 1) * 8));
                playAddress |= (int) ((byte) data << ((address - 1) * 8));
                break;
            case 0x05:
            case 0x06:
            case 0x07:
            case 0x08:
                stopAddress &= (int) ~(0x0000_00ff << ((address - 5) * 8));
                stopAddress |= (int) ((byte) data << ((address - 5) * 8));
                break;
            case 0x09:
            case 0x0a:
            case 0x0b:
            case 0x0c:
                loopAddress &= (int) ~(0x0000_00ff << ((address - 9) * 8));
                loopAddress |= (int) ((byte) data << ((address - 9) * 8));
                break;
            case 0x0d:
            case 0x0e:
            case 0x0f:
            case 0x10:
                keyOffAddress &= (int) ~(0x0000_00ff << ((address - 13) * 8));
                keyOffAddress |= (int) ((byte) data << ((address - 13) * 8));
                break;
            case 0x12:
                pcmConfig = (byte) data;
                break;
            case 0x13:
                effectConfiguration = (byte) data;
                break;

            default:
                throw new IllegalArgumentException("The address specification is incorrect");
        }
    }

    public void update(int[][] outputs, int samples) {
        boolean ko = (operator.getKeyFrqmode() & 0x80) != 0;
        if (ko) {
            if (!oldKeyOn || operator.isKeyOnFlg()) {
                // A key was pressed.
                oldKeyOn = ko;
                playPtr = playAddress;
                play = true;
            }
            //else {
            // While being pushed
            //}
        } else {
            if (oldKeyOn) {
                // The key was released.
                oldKeyOn = ko;
                if (keyOffAddress != 0) playPtr = keyOffAddress;
                else play = false;
            }
            //else {
            // While being not pushed
            //}
        }
        operator.setKeyOnFlg(false);

        if (!play) return;

        if ((operator.getKeyFrqmode() & 0x40) == 0) {
            int oct = (byte) ((operator.getNoteByteMatrix() >> 16) & 0x7);
            int note = (byte) ((operator.getNoteByteMatrix() >> 8) & 0xf);
            int kf = (byte) (operator.getNoteByteMatrix() & 0x3f);
            deltan = (deltaKcTable[note] >> (10 - (oct + 3))) & 0xffff;
            adpld = deltan * adplbase >> 16;
        } else {

        }

        if (adpld <= 8192) { // fplay < fsamp
            for (int i = 0; i < samples; i++) {
                byte d = (byte) (playPtr >= pCMData.size() ? 0 : pCMData.get(playPtr));
                if (adplc < 0) {
                    adplc += 8192;
                    playPtr++;
                    if (playPtr > stopAddress) {
                        if (loopAddress != 0)
                            playPtr = loopAddress;
                        else play = false;
                    }
                }

                outputs[0][i] += (d * ((operator.cp.sysPcmVol * operator.sc.getLeftVolume()) >> 12)) >> 5;
                outputs[1][i] += (d * ((operator.cp.sysPcmVol * operator.sc.getRightVolume()) >> 12)) >> 5;
                adplc -= adpld;
            }
            return;
        }

        // fplay > fsamp (adpld = fplay/famp*8192)
        int t = (-8192 * 8192) / adpld;
        for (int i = 0; i < samples; i++) {
            byte d = (byte) (playPtr >= pCMData.size() ? 0 : pCMData.get(playPtr));
            int s = d * (8192 + adplc);
            while (adplc < 0) {
                d = (byte) (playPtr >= pCMData.size() ? 0 : pCMData.get(playPtr));
                playPtr++;
                if (playPtr > stopAddress) {
                    if (loopAddress != 0)
                        playPtr = loopAddress;
                    else play = false;
                }

                if (!play) return;
                s -= d * Math.max(adplc, t);
                adplc -= t;
            }
            adplc -= 8192;
            s >>= 13;

            outputs[0][i] += (s * ((operator.cp.sysPcmVol * operator.sc.getLeftVolume()) >> 12)) >> 5;
            outputs[1][i] += (s * ((operator.cp.sysPcmVol * operator.sc.getRightVolume()) >> 12)) >> 5;
        }
    }
}
