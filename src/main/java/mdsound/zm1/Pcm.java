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

    private final List<Byte> pCMData;

    private static final int[] deltaKcTable = {
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
        adplbase = playClock == 0 ? 0 : (int) (8192.0 * (chipClock / 72.0) / playClock);
        updateDelta();
    }

    private void updateDelta() {
        adpld = (int) ((long) deltan * adplbase >> 16);
    }

    /** replaces the byte of a little endian 32 bit value */
    private static int setByte(int value, int index, int data) {
        int shift = index * 8;
        return (value & ~(0xff << shift)) | ((data & 0xff) << shift);
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
                playAddress = setByte(playAddress, address - 0x01, data);
                break;
            case 0x05:
            case 0x06:
            case 0x07:
            case 0x08:
                stopAddress = setByte(stopAddress, address - 0x05, data);
                break;
            case 0x09:
            case 0x0a:
            case 0x0b:
            case 0x0c:
                loopAddress = setByte(loopAddress, address - 0x09, data);
                break;
            case 0x0d:
            case 0x0e:
            case 0x0f:
            case 0x10:
                keyOffAddress = setByte(keyOffAddress, address - 0x0d, data);
                break;
            case 0x12:
                pcmConfig = (byte) data;
                break;
            case 0x13:
                effectConfiguration = (byte) data;
                break;

            default:
                // 0x11 and others are reserved
                break;
        }
    }

    private int sample(int ptr) {
        return ptr >= 0 && ptr < pCMData.size() ? pCMData.get(ptr) : 0;
    }

    /** advances the play pointer, handles stop and loop */
    private void advance() {
        playPtr++;
        if (Integer.compareUnsigned(playPtr, stopAddress) > 0) {
            if (loopAddress != 0)
                playPtr = loopAddress;
            else play = false;
        }
    }

    private void output(int[][] outputs, int i, int s) {
        int vol = operator.cp.sysPcmVol;
        outputs[0][i] += (s * (int) ((long) vol * operator.sc.getLeftVolume() >> 12)) >> 5;
        outputs[1][i] += (s * (int) ((long) vol * operator.sc.getRightVolume() >> 12)) >> 5;
    }

    public void update(int[][] outputs, int samples) {
        boolean ko = (operator.getKeyFrqmode() & 0x80) != 0;
        if (ko) {
            if (!oldKeyOn || operator.isKeyOnFlg()) {
                // A key was pressed.
                oldKeyOn = ko;
                playPtr = playAddress;
                adplc = 0;
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

        int matrix = operator.getNoteByteMatrix();
        if ((operator.getKeyFrqmode() & 0x40) == 0) {
            // note mode: [23:16] octave, [15:8] note (key code), [5:0] key fraction
            int oct = (matrix >> 16) & 0x7;
            int note = (matrix >> 8) & 0xf;
            int kf = matrix & 0x3f;
            long d = (long) (deltaKcTable[note] >> (7 - oct)) * kftable[kf] >> 16;
            deltan = (int) d;
        } else {
            // frequency mode: [15:0] is delta-n directly (as ym2608 adpcm)
            deltan = matrix & 0xffff;
        }
        updateDelta();

        if (adpld <= 0) return;

        if (adpld <= 8192) { // fplay < fsamp
            for (int i = 0; i < samples && play; i++) {
                int d = sample(playPtr);
                if (adplc < 0) {
                    adplc += 8192;
                    advance();
                }

                output(outputs, i, d);
                adplc -= adpld;
            }
            return;
        }

        // fplay > fsamp (adpld = fplay/famp*8192)
        int t = (-8192 * 8192) / adpld;
        for (int i = 0; i < samples; i++) {
            int s = sample(playPtr) * (8192 + adplc);
            while (adplc < 0) {
                int d = sample(playPtr);
                advance();

                if (!play) return;
                s -= d * Math.max(adplc, t);
                adplc -= t;
            }
            adplc -= 8192;
            s >>= 13;

            output(outputs, i, s);
        }
    }
}
