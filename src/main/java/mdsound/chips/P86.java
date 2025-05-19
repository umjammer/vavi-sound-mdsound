package mdsound.chips;


/** from PMDWin */
public class P86 {

    public double samplingRate = 44100.0;
    private byte[] pcmData = null;
    private static final int MAXInst = 256;
    private final Inst[] inst = new Inst[MAXInst];

    /** use interpolation? */
    private boolean interpolation = false;
    /** Playback Frequency */
    private int rate;
    /** Frequency of the original data */
    private int srcRate;
    /** Pitch(fNum) */
    private int pitch;
    /** volume */
    private int vol;
    /** P86 Memory pointer for saving */
    private int _addr;
    /** PCM data address during sound generation */
    private int currentOffset;
    /** PCM data address during sounding (decimal part) */
    private int currestOffsetX;
    /** Remaining size */
    private int remainingSize;
    /** Sound start PCM data address */
    private int startOffset;
    /** PCM data size */
    private int size;
    /** PCM address addition value (integer part) */
    private int addsize1;
    /** PCM address addition value (decimal part) */
    private int addSize2;
    /** Repeat start position */
    private int repeatOffset;
    /** Size after repeat */
    private int repeatSize;
    /** Release start position */
    private int releaseOffset;
    /** Size after release */
    private int releaseSize;
    /** Repeat flag */
    private boolean repeatFlag;
    /** Flag to release or not */
    private boolean releaseFlag1;
    /** Flag of whether it has been released */
    private boolean releaseFlag2;

    /** Pan data 1 (bit0=left/bit1=right/bit2=reverse) */
    private int panFlag;
    /** Pan data 2 (volume value of the side that lowers the volume) */
    private int panDat;
    /** playing?flag */
    private boolean playing;

    private int volume;
    /** Volume table */
    private int[][] volumeTable;
    private static final int[] rateTable = {4135, 5513, 8270, 11025, 16540, 22050, 33080, 44100};

    private static class Inst {
        public int start;
        public int size;

        public Inst(byte[] pcmData, int i) {
            this.start =
                    (pcmData[i * 6 + 0 + 12 + 1 + 3] & 0xff) +
                    (pcmData[i * 6 + 1 + 12 + 1 + 3] & 0xff) * 0x100 +
                    (pcmData[i * 6 + 2 + 12 + 1 + 3] & 0xff) * 0x1_0000; // - 0x610;
            this.size =
                    (pcmData[i * 6 + 3 + 12 + 1 + 3] & 0xff) +
                    (pcmData[i * 6 + 4 + 12 + 1 + 3] & 0xff) * 0x100 +
                    (pcmData[i * 6 + 5 + 12 + 1 + 3] & 0xff) * 0x1_0000;
        }
    }

    /** from PMDWin p86drv.cpp */
    public int loadPcm(int port, int address, int data, byte[] pcmData) {
        this.pcmData = pcmData;

        for (int i = 0; i < MAXInst; i++) {
            inst[i] = new Inst(pcmData, i);
        }

        return 0;
    }

    /** */
    public void start(double samplingRate) {
        this.samplingRate = samplingRate;
        init();
    }

    /**
     * Initialization (internal processing)
     */
    public void init() {

        interpolation = false;
        rate = (int) samplingRate;
        srcRate = rateTable[4]; // 16.54kHz
        pitch = 0;
        vol = 0;

        currentOffset = 0;
        currestOffsetX = 0;
        remainingSize = 0;
        startOffset = 0;
        size = 0;
        addsize1 = 0;
        addSize2 = 0;
        repeatOffset = 0;
        repeatSize = 0;
        releaseOffset = 0;
        releaseSize = 0;
        repeatFlag = false;
        releaseFlag1 = false;
        releaseFlag2 = false;

        panFlag = 0;
        panDat = 0;
        playing = false;

        volume = 0;
        setVolume(0);
    }

    /**
     * For volume adjustment
     */
    private void setVolume(int volume) {
        makeVolumeTable(volume);
    }

    /**
     * Creating a volume table
     */
    private void makeVolumeTable(int volume) {
        volumeTable = new int[16][];
        int aVolumeTemp = (int) (0x1000 * Math.pow(10.0, volume / 40.0));
        if (this.volume != aVolumeTemp) {
            this.volume = aVolumeTemp;
            for (int i = 0; i < 16; i++) {
                volumeTable[i] = new int[256];
                // temp = pow(2.0, (i + 15) / 2.0) * aVolume / 0x18000;
                double temp = i * this.volume / 256d;
                for (int j = 0; j < 256; j++) {
                    volumeTable[i][j] = (int) ((byte) j * temp);
                }
            }
        }
    }

    /**
     * Middle (no linear interpolation)
     */
    private void doubleTrans(int[][] buffer, int samples) {
        for (int i = 0; i < samples; i++) {
            int data = volumeTable[vol][pcmData[currentOffset]];

            data = (short) Math.max(Math.min(data, Short.MAX_VALUE), Short.MIN_VALUE);
            buffer[0][i] += data;
            buffer[1][i] += data;

            if (addAddress()) {
                playing = false;
                return;
            }
        }
    }

    /**
     * Center (reverse phase, no linear interpolation)
     */
    private void doubleTransG(int[][] buffer, int samples) {
        for (int i = 0; i < samples; i++) {
            int data = volumeTable[vol][pcmData[currentOffset]];

            buffer[0][i] += data;
            buffer[1][i] -= data;

            if (addAddress()) {
                playing = false;
                return;
            }
        }
    }

    /**
     * Leftward (no linear interpolation)
     */
    private void leftTrans(int[][] buffer, int samples) {
        for (int i = 0; i < samples; i++) {
            int data = volumeTable[vol][pcmData[currentOffset]];

            buffer[0][i] += data;
            data = data * panDat / (256 / 2);
            buffer[1][i] += data;

            if (addAddress()) {
                playing = false;
                return;
            }
        }
    }

    /**
     * Leftward (reverse phase, no primary interpolation)
     */
    private void leftTransG(int[][] buffer, int samples) {
        for (int i = 0; i < samples; i++) {
            int data = volumeTable[vol][pcmData[currentOffset]];

            buffer[0][i] += data;
            data = data * panDat / (256 / 2);
            buffer[1][i] -= data;

            if (addAddress()) {
                playing = false;
                return;
            }
        }
    }

    /**
     * Rightward (no linear interpolation)
     */
    private void rightTrans(int[][] buffer, int samples) {
        for (int i = 0; i < samples; i++) {
            int data = volumeTable[vol][pcmData[currentOffset]];

            buffer[1][i] += data;
            data = data * panDat / (256 / 2);
            buffer[0][i] += data;

            if (addAddress()) {
                playing = false;
                return;
            }
        }
    }

    /**
     * Rightward (reverse phase, no linear interpolation)
     */
    private void rightTransG(int[][] buffer, int samples) {
        for (int i = 0; i < samples; i++) {
            int data = volumeTable[vol][pcmData[currentOffset]];

            buffer[1][i] -= data;
            data = data * panDat / (256 / 2);
            buffer[0][i] += data;

            if (addAddress()) {
                playing = false;
                return;
            }
        }
    }

    private boolean addAddress() {
        currestOffsetX += addSize2;
        if (currestOffsetX >= 0x1000) {
            currestOffsetX -= 0x1000;
            currentOffset++;
            remainingSize--;
        }
        currentOffset += addsize1;
        remainingSize -= addsize1;

        if (remainingSize > 1) { // First-order interpolation measures
            return false;
        } else if (!repeatFlag || releaseFlag2) {
            return true;
        }

        remainingSize = repeatSize;
        currentOffset = repeatOffset;
        return false;
    }

    public void update(int[][] outputs, int samples) {
        if (!playing) return;
        if (remainingSize <= 1) { // First-order interpolation measures
            playing = false;
            return;
        }
        switch (panFlag) {
        case 0:
            doubleTrans(outputs, samples);
            break;
        case 1:
            leftTrans(outputs, samples);
            break;
        case 2:
            rightTrans(outputs, samples);
            break;
        case 3:
            doubleTrans(outputs, samples);
            break;
        case 4:
            doubleTransG(outputs, samples);
            break;
        case 5:
            leftTransG(outputs, samples);
            break;
        case 6:
            rightTransG(outputs, samples);
            break;
        case 7:
            doubleTransG(outputs, samples);
            break;
        }
    }

    public int write(int port, int adr, int data) {
        switch ((byte) port) {
        case 0x00: // Init
            break;
        case 0x01: // LoadPcm
            break;
        case 0x02: // tone
            startOffset = inst[data].start;
            size = inst[data].size;
            repeatFlag = false;
            releaseFlag1 = false;
            break;
        case 0x03: // pan
            panFlag = adr;
            panDat = data;
            break;
        case 0x04: // volume
            vol = (byte) data;
            break;
        case 0x05: // pitch
            int srcRate = adr >> 5;
            int pitch = (adr & 0x1f) * 0x10000 + data;
            if (srcRate < 0 || srcRate > 7)
                break;
            if (pitch > 0x1f_ffff)
                break;

            this.pitch = pitch;
            this.srcRate = rateTable[srcRate];

            //logger.log(Level.TRACE, "pitch:%x srcrate:%x".formatted(pitch, srcrate));
            pitch = (int) (pitch * this.srcRate / (long) samplingRate);

            addSize2 = (pitch & 0xffff) >> 4;
            addsize1 = pitch >> 16;

            break;
        case 0x06: // loop
            break;
        case 0x07: // play
            currentOffset = startOffset;
            currestOffsetX = 0;
            remainingSize = size;
            playing = true;
            releaseFlag2 = false;
            break;
        case 0x08: // stop
            playing = false;
            break;
        case 0x09: // key off
            if (releaseFlag1) { // Is the release set?
                currentOffset = releaseOffset;
                remainingSize = releaseSize;
                releaseFlag2 = true; // Released
            } else {
                playing = false;
            }
            break;
        }

        return 0;
    }
}
