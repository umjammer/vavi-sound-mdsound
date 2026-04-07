package mdsound.chips;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;

import vavi.util.ByteUtil;

import static java.lang.System.getLogger;


/**
 * PPZ8 (PMD).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2022-07-08 nsano initial version <br>
 */
public class PPZ8 {

    private static final Logger logger = getLogger(PPZ8.class.getName());

    private byte[][] pcmData = new byte[2][];
    private boolean[] isPVI = new boolean[2];
    private int bank = 0;
    private int ptr = 0;
    private boolean interrupt = false;
    private int adpcmEmu;
    private short[][] volumeTable = new short[][] {
            new short[256], new short[256], new short[256], new short[256],
            new short[256], new short[256], new short[256], new short[256],
            new short[256], new short[256], new short[256], new short[256],
            new short[256], new short[256], new short[256], new short[256]
    };
    private double samplingRate = 44100.0;
    private int PCM_VOLUME;
    private int volume;

    public static class Channel {
        public int startAddress;
        public int loopStartOffset;
        public int loopEndOffset;
        public boolean playing;
        public int pan;
        private double panL;
        private double panR;
        public int srcFrequency;
        public int volume;
        public int frequency;
        public boolean KeyOn;
        public boolean mask;

        private int _loopStartOffset;
        private int _loopEndOffset;
//        private int _frequency;
        private int _srcFrequency;

        public int bank;
        public int ptr;
        public int end;
        private double delta;
        public int num;

        private void init() {
            this.srcFrequency = 16000;
            this.pan = 5;
            this.panL = 1.0;
            this.panR = 1.0;
            this.volume = 8;
            this.mask = false;
//            this._frequency = 0;
            this._loopStartOffset = -1;
            this._loopEndOffset = -1;
        }
    }

    private Channel[] chWk = new Channel[] {
            new Channel(), new Channel(), new Channel(), new Channel(),
            new Channel(), new Channel(), new Channel(), new Channel()
    };
    private final Channel[] chWkBk = new Channel[] {
            new Channel(), new Channel(), new Channel(), new Channel(),
            new Channel(), new Channel(), new Channel(), new Channel()
    };

    /**
     * 0x00 Initialization
     */
    public void init() {
        bank = 0;
        ptr = 0;
        interrupt = false;
        for (int i = 0; i < 8; i++) {
            chWk[i].init();
        }
        PCM_VOLUME = 0;
        volume = 0;
        setAllVolume(12);
    }

    public void makeVolumeTable(int vol) {

        volume = vol;
        int aVolume = (int) (0x1000 * Math.pow(10.0, vol / 40.0));

        for (int i = 0; i < 16; i++) {
            double temp = Math.pow(2.0, (i + PCM_VOLUME) / 2.0) * aVolume / 0x18000;
            for (int j = 0; j < 256; j++) {
                volumeTable[i][j] = (short) (Math.clamp((j - 128) * temp, Short.MIN_VALUE, Short.MAX_VALUE));
            }
        }
    }

    /**
     * 0x01 Play PCM
     *
     * @param al PCM Channel (0-7)
     * @param dx PCM tone number
     */
    public void playPcm(int al, int dx) {
        logger.log(Level.TRACE, "ppz8em: PlayPCM: ch:%d @:%d".formatted(al, dx));

        int bank = (dx & 0x8000) != 0 ? 1 : 0;
        int num = dx & 0x7fff;
        chWk[al].bank = bank;
        chWk[al].num = num;

        if (pcmData[bank] != null) {
            chWk[al].ptr =
                    (pcmData[bank][num * 0x12 + 32] & 0xff) +
                    (pcmData[bank][num * 0x12 + 1 + 32] & 0xff) * 0x100 +
                    (pcmData[bank][num * 0x12 + 2 + 32] & 0xff) * 0x1_0000 +
                    (pcmData[bank][num * 0x12 + 3 + 32] & 0xff) * 0x10_00000 +
                    0x20 + 0x12 * 128;
            if (chWk[al].ptr >= pcmData[bank].length) {
                chWk[al].ptr = pcmData[bank].length - 1;
            }
            chWk[al].end = chWk[al].ptr +
                    (pcmData[bank][num * 0x12 + 4 + 32] & 0xff) +
                    (pcmData[bank][num * 0x12 + 5 + 32] & 0xff) * 0x100 +
                    (pcmData[bank][num * 0x12 + 6 + 32] & 0xff) * 0x1_0000 +
                    (pcmData[bank][num * 0x12 + 7 + 32] & 0xff) * 0x10_00000;
            if (chWk[al].end >= pcmData[bank].length) {
                chWk[al].end = pcmData[bank].length - 1;
            }
            chWk[al].startAddress = chWk[al].ptr;

            chWk[al].loopStartOffset = chWk[al]._loopStartOffset;
            if (chWk[al]._loopStartOffset == -1) {
                chWk[al].loopStartOffset =
                        (pcmData[bank][num * 0x12 + 8 + 32] & 0xff) +
                        (pcmData[bank][num * 0x12 + 9 + 32] & 0xff) * 0x100 +
                        (pcmData[bank][num * 0x12 + 10 + 32] & 0xff) * 0x10_000 +
                        (pcmData[bank][num * 0x12 + 11 + 32] & 0xff) * 0x100_0000;
            }
            chWk[al].loopEndOffset = chWk[al]._loopEndOffset;
            if (chWk[al]._loopEndOffset == -1) {
                chWk[al].loopEndOffset =
                        (pcmData[bank][num * 0x12 + 12 + 32] & 0xff) +
                        (pcmData[bank][num * 0x12 + 13 + 32] & 0xff) * 0x100 +
                        (pcmData[bank][num * 0x12 + 14 + 32] & 0xff) * 0x10_000 +
                        (pcmData[bank][num * 0x12 + 15 + 32] & 0xff) * 0x100_0000;
            }

            if (chWk[al].loopStartOffset == 0xffff) {
                chWk[al].loopStartOffset = -1;
                chWk[al].loopEndOffset = -1;
            }
            if (chWk[al].loopStartOffset == -1 || chWk[al].loopEndOffset == 0xffff) chWk[al].loopEndOffset = -1;

            // Seems unnecessary?
            //chWk[al].srcFrequency = (short) (chWk[chipID][al].ptr +
            //    (pcmData[bank][num * 0x12 + 16 + 32] | (pcmData[bank][num * 0x12 + 17 + 32] << 8))
            //);
            //chWk[al].frequency = chWk[al]._frequency;

            chWk[al].srcFrequency = chWk[al]._srcFrequency;
        }

        interrupt = false;
        chWk[al].playing = true;
        chWk[al].KeyOn = true;
    }

    /**
     * 0x02 Stop PCM
     *
     * @param al PCM Channel(0-7)
     */
    public void stopPCM(int al) {
        logger.log(Level.TRACE, "ppz8em: StopPCM: ch:%d".formatted(al));

        chWk[al].playing = false;
    }

    /**
     * 0x03 Load PVI file and convert to PCM
     *
     * @param bank    0: PCM buffer 0 1: PCM buffer 1
     * @param mode    0:.PVI (ADPCM)  1:.PZI(PCM)
     * @param pcmData File Contents
     */
    public int loadPcm(int bank, int mode, byte[][] pcmData) {
        logger.log(Level.TRACE, "ppz8em: LoadPCM: bank:%d mode:%d".formatted(bank, mode));

        bank &= 1;
        mode &= 1;
        int ret;
        this.pcmData = pcmData;

        if (mode == 0) // PVI Format
            ret = checkPVI(pcmData[bank]);
        else // PZI format
            ret = checkPZI(pcmData[bank]);

        if (ret == 0) {
            //this.pcmData[bank] = new byte[pcmData[bank].length];
            //Arrays.fill(pcmData, this.pcmData[bank], pcmData[bank].length);
            isPVI[bank] = mode == 0;
            if (isPVI[bank]) {
                ret = convertPviAdpcmToPziPcm(bank);
            }
        }

        return ret;
    }

    /**
     * 0x04 Loading status
     *
     * @param al 0xd: pcm0, oxe: pcm1
     */
    public void readStatus(int al) {
        switch (al) {
        case 0xd:
            logger.log(Level.TRACE, "ppz8em: ReadStatus: PCM0 table address");
            bank = 0;
            ptr = 0;
            break;
        case 0xe:
            logger.log(Level.TRACE, "ppz8em: ReadStatus: PCM1 table address");
            bank = 1;
            ptr = 0;
            break;
        }
    }

    /**
     * 0x07 Changing the volume
     *
     * @param al PCM Channel (0~7)
     * @param dx Volume (0-15 / 0-255)
     */
    public void setVolume(int al, int dx) {
        logger.log(Level.TRACE, "ppz8em: SetVolume: Ch:%d vol:%d".formatted(al, dx));

        chWk[al].volume = dx;
    }

    /**
     * 0x0B Specifying the PCM pitch frequency
     *
     * @param al PCM Channel (0~7)
     * @param dx PCM Pitch Frequency DX
     * @param cx PCM pitch frequency CX
     */
    public void setFrequency(int al, int dx, int cx) {
        logger.log(Level.TRACE, "ppz8em: SetFrequency: 0x%8x".formatted(dx * 0x10000 + cx));

        chWk[al].frequency = dx * 0x10000 + cx;
    }

    /**
     * 0x0e Setting the Loop Pointer
     *
     * @param al        PCM Channel (0~7)
     * @param lpStOfsDX Loop Start OffsetDX
     * @param lpStOfsCX Loop Start OffsetCX
     * @param lpEdOfsDI Loop End OffsetDI
     * @param lpEdOfsSI Loop End OffsetSI
     */
    public void setLoopPoint(int al, int lpStOfsDX, int lpStOfsCX, int lpEdOfsDI, int lpEdOfsSI) {
        logger.log(Level.TRACE, "ppz8em: SetLoopPoint: St:0x%8x Ed:0x%8x".formatted(
                lpStOfsDX * 0x10000 + lpStOfsCX, lpEdOfsDI * 0x10000 + lpEdOfsSI));

        al &= 7;
        chWk[al]._loopStartOffset = lpStOfsDX * 0x10000 + lpStOfsCX;
        chWk[al]._loopEndOffset = lpEdOfsDI * 0x10000 + lpEdOfsSI;

        if (chWk[al]._loopStartOffset == 0xffff || chWk[al]._loopStartOffset >= chWk[al]._loopEndOffset) {
            chWk[al]._loopStartOffset = -1;
            chWk[al]._loopEndOffset = -1;
        }
        if (chWk[al]._loopEndOffset == 0xffff) chWk[al]._loopEndOffset = -1;
    }

    /**
     * 0x12 Stop PCM interrupts
     */
    public void stopInterrupt() {
        logger.log(Level.TRACE, "ppz8em: stopInterrupt");

        interrupt = true;
    }

    /**
     * 0x13 PAN settings
     *
     * @param al PCM Channel (0~7)
     * @param dx PAN(0~9)
     */
    public void setPan(int al, int dx) {
        logger.log(Level.TRACE, "ppz8em:setPan: %d".formatted(dx));

        chWk[al].pan = dx;
        chWk[al].panL = (chWk[al].pan < 6 ? 1.0 : (0.25 * (9 - chWk[al].pan)));
        chWk[al].panR = (chWk[al].pan > 4 ? 1.0 : (0.25 * chWk[al].pan));
    }

    /**
     * 0x15 Original data frequency setting
     *
     * @param al PCM Channel (0~7)
     * @param dx Original data frequency
     */
    public void setSrcFrequency(int al, int dx) {
        logger.log(Level.TRACE, "ppz8em: setSrcFrequency: %d".formatted(dx));

        chWk[al]._srcFrequency = dx;
    }

    /**
     * 0x16 Overall Volume
     */
    public void setAllVolume(int vol) {
        logger.log(Level.TRACE, "ppz8em: SetAllVolume: %d".formatted(vol));

        if (vol < 16 && vol != PCM_VOLUME) {
            PCM_VOLUME = vol;
            makeVolumeTable(volume);
        }
    }

    /**
     * For volume adjustment
     */
    public void setVolume(int vol) {
        if (vol != volume) {
            makeVolumeTable(vol);
        }
    }

    /**
     * 0x18  Channel 7 ADPCM emulation settings
     *
     * @param al 0: Do not emulate ADPCM on channel 7. 1: Enable.
     */
    public void setAdpcmEmu(int al) {
        logger.log(Level.TRACE, "ppz8em: setAdpcmEmu: %d".formatted(al));

        adpcmEmu = al;
    }

    /**
     * 0x19 Resident disable permission/prohibition setting
     *
     * @param v 0: Permitted to cancel resident mode 1: Prohibited to cancel resident mode
     */
    public void setReleaseFlag(int v) {
        // Do nothing
    }

    private static int checkPZI(byte[] pcmData) {
        if (pcmData == null)
            return 5;
        if (!(pcmData[0] == 'P' && pcmData[1] == 'Z' && pcmData[2] == 'I'))
            return 2;

        return 0;
    }

    private static int checkPVI(byte[] pcmData) {
        if (pcmData == null)
            return 5;
        if (!(pcmData[0] == 'P' && pcmData[1] == 'V' && pcmData[2] == 'I'))
            return 2;

        return 0;
    }

    public void update(int[][] outputs, int samples) {
        if (interrupt) return;

        for (int j = 0; j < samples; j++) {
            int l = 0, r = 0;
            for (int i = 0; i < 8; i++) {
                if (pcmData[chWk[i].bank] == null) continue;
                if (!chWk[i].playing) continue;
                if (chWk[i].pan == 0) continue;

//                if (i == 6) {
//                    logger.log(Level.TRACE, volumeTable[chWk[i].volume][pcmData[chWk[i].bank][chWk[i].ptr]] * chWk[i].panL);
//                }

                int n = Integer.compareUnsigned(chWk[i].ptr, pcmData[chWk[i].bank].length) >= 0 ? 0x80 : pcmData[chWk[i].bank][chWk[i].ptr] & 0xff;
                if (!chWk[i].mask) {
                    l += (int) (volumeTable[chWk[i].volume][n] * chWk[i].panL);
                    r += (int) (volumeTable[chWk[i].volume][n] * chWk[i].panR);
                }
                chWk[i].delta += ((double) (chWk[i].srcFrequency & 0xffff_ffffL) * (chWk[i].frequency & 0xffff_ffffL) / 0x8000L) / samplingRate;
                chWk[i].ptr += (int) chWk[i].delta;
                chWk[i].delta -= (int) chWk[i].delta;

                // When the loop end position is reached, it returns to the loop start position.
                if (chWk[i].loopEndOffset != -1 && chWk[i].ptr >= chWk[i].startAddress + chWk[i].loopEndOffset) {
                    chWk[i].ptr -= chWk[i].loopEndOffset - chWk[i].loopStartOffset;
                }

                // When the end of the data is reached, it returns to the loop start position.
                // If no loop is specified, playback ends.
                if (chWk[i].ptr >= chWk[i].end) {
                    if (chWk[i].loopStartOffset != -1) {
                        chWk[i].ptr -= (chWk[i].end - chWk[i].startAddress - chWk[i].loopStartOffset);
                    } else {
                        chWk[i].playing = false;
                    }
                }
            }

            l = (short) Math.clamp(l, Short.MIN_VALUE, Short.MAX_VALUE);
            r = (short) Math.clamp(r, Short.MIN_VALUE, Short.MAX_VALUE);
            outputs[0][j] += l;
            outputs[1][j] += r;
        }
    }

    public int convertPviAdpcmToPziPcm(int bank) {
        int[] table1 = {
                1, 3, 5, 7, 9, 11, 13, 15,
                -1, -3, -5, -7, -9, -11, -13, -15,
        };
        int[] table2 = {
                57, 57, 57, 57, 77, 102, 128, 153,
                57, 57, 57, 57, 77, 102, 128, 153,
        };

        List<Byte> o = new ArrayList<>();

        // Generating the Header
        o.add((byte) 'P');
        o.add((byte) 'Z');
        o.add((byte) 'I');
        o.add((byte) '1');
        for (int i = 4; i < 0x0b; i++) o.add((byte) 0);
        byte instCount = pcmData[bank][0xb];
        o.add(instCount);
        for (int i = 0xc; i < 0x20; i++) o.add((byte) 0);

        // Tone table conversion
        long size2 = 0;
        for (int i = 0; i < instCount; i++) {
            int startAddress = ((pcmData[bank][i * 4 + 0x10] & 0xff) + (pcmData[bank][i * 4 + 0x11] & 0xff) * 0x100) << (5 + 1);
            int size = (((pcmData[bank][i * 4 + 0x12] & 0xff) + (pcmData[bank][i * 4 + 0x13] & 0xff) * 0x100) -
                    ((pcmData[bank][i * 4 + 0x10] & 0xff) + (pcmData[bank][i * 4 + 0x11] & 0xff) * 0x100) + 1) <<
                    (5 + 1);// endAdr - startAdr
            size2 += size;
            short rate = 16000; // 16kHz

            o.add((byte) startAddress);
            o.add((byte) (startAddress >>> 8));
            o.add((byte) (startAddress >>> 16));
            o.add((byte) (startAddress >>> 24));
            o.add((byte) size);
            o.add((byte) (size >>> 8));
            o.add((byte) (size >>> 16));
            o.add((byte) (size >>> 24));
            o.add((byte) 0xff);
            o.add((byte) 0xff);
            o.add((byte) 0);
            o.add((byte) 0); // loop_start
            o.add((byte) 0xff);
            o.add((byte) 0xff);
            o.add((byte) 0);
            o.add((byte) 0); // loop_end
            o.add((byte) rate);
            o.add((byte) (rate >> 8)); // rate
        }

        for (int i = instCount; i < 128; i++) {
            o.add((byte) 0);
            o.add((byte) 0);
            o.add((byte) 0);
            o.add((byte) 0);
            o.add((byte) 0);
            o.add((byte) 0);
            o.add((byte) 0);
            o.add((byte) 0);
            o.add((byte) 0xff);
            o.add((byte) 0xff);
            o.add((byte) 0);
            o.add((byte) 0); // loop_start
            o.add((byte) 0xff);
            o.add((byte) 0xff);
            o.add((byte) 0);
            o.add((byte) 0); // loop_end
            short rate = 16000; // 16kHz
            o.add((byte) rate);
            o.add((byte) (rate >> 8)); // rate
        }

        // Convert ADPCM to PCM
        int psrcPtr = 0x10 + 4 * 128;
        for (int i = 0; i < instCount; i++) {
            int xN = 0x80; // Xn (For ADPCM to PCM conversion)
            int deltaN = 127; // deltaN (For ADPCM to PCM conversion)

            int size = (((pcmData[bank][i * 4 + 0x12] & 0xff) + (pcmData[bank][i * 4 + 0x13] & 0xff) * 0x100) -
                    ((pcmData[bank][i * 4 + 0x10] & 0xff) + (pcmData[bank][i * 4 + 0x11] & 0xff) * 0x100) + 1) <<
                    (5 + 1); // endAdr - startAdr

            for (int j = 0; j < size / 2; j++) {
                int psrc = pcmData[bank][psrcPtr++] & 0xff;

                int n = xN + table1[(psrc >> 4) & 0x0f] * deltaN / 8;
                //logger.log(Level.TRACE, n);
                xN = Math.clamp(n, -32768, 32767);

                n = deltaN * table2[(psrc >> 4) & 0x0f] / 64;
                //logger.log(Level.TRACE, n);
                deltaN = Math.clamp(n, 127, 24576);

                o.add((byte) (xN / (32768 / 128) + 128));

                n = xN + table1[psrc & 0x0f] * deltaN / 8;
                //logger.log(Level.TRACE, n);
                xN = Math.clamp(n, -32768, 32767);

                n = deltaN * table2[psrc & 0x0f] / 64;
                //logger.log(Level.TRACE, n);
                deltaN = Math.clamp(n, 127, 24576);

                o.add((byte) (xN / (32768 / 128) + 128));
            }
        }

        pcmData[bank] = ByteUtil.toByteArray(o);
        //File.writeAllBytes("a.raw", pcmData[bank]);
        return 0;
    }

    public Channel[] getChannels() {
        for (int ch = 0; ch < 8; ch++) {
            chWkBk[ch].bank = chWk[ch].bank;
            chWkBk[ch].delta = chWk[ch].delta;
            chWkBk[ch].end = chWk[ch].end;
            chWkBk[ch].frequency = chWk[ch].frequency;
            chWkBk[ch].loopEndOffset = chWk[ch].loopEndOffset;
            chWkBk[ch].loopStartOffset = chWk[ch].loopStartOffset;
            chWkBk[ch].num = chWk[ch].num;
            chWkBk[ch].pan = chWk[ch].pan;
            chWkBk[ch].panL = chWk[ch].panL;
            chWkBk[ch].panR = chWk[ch].panR;
            chWkBk[ch].playing = chWk[ch].playing;
            chWkBk[ch].ptr = chWk[ch].ptr;
            chWkBk[ch].srcFrequency = chWk[ch].srcFrequency;
            chWkBk[ch].volume = chWk[ch].volume;
            chWkBk[ch].KeyOn = chWk[ch].KeyOn;

            chWk[ch].KeyOn = false;
        }
        return chWkBk;
    }

    public void setMask(byte chipID,byte channel,boolean isMask) {
        chWk[channel].mask = isMask;
    }

    public void reset() {
        pcmData = new byte[2][];
        isPVI = new boolean[2];
        chWk = new Channel[] {
                new Channel(), new Channel(), new Channel(), new Channel(),
                new Channel(), new Channel(), new Channel(), new Channel()
        };

        bank = 0;
        ptr = 0;
        interrupt = false;
        adpcmEmu = 0;
        volumeTable = new short[][] {
                new short[256], new short[256], new short[256], new short[256],
                new short[256], new short[256], new short[256], new short[256],
                new short[256], new short[256], new short[256], new short[256],
                new short[256], new short[256], new short[256], new short[256]
        };

        PCM_VOLUME = 0;
        volume = 0;
    }

    public int write(int port, int adr, int data) {
        switch (port & 0xff) {
        case 0x00:
            init();
            break;
        case 0x01:
            playPcm(adr & 0xff, data & 0xffff);
            break;
        case 0x02:
            stopPCM(adr & 0xff);
            break;
        case 0x03: // LoadPCM
            break;
        case 0x04: // ReadStatus
            readStatus(adr & 0xff);
            break;
        case 0x07:
            setVolume(adr & 0xff, data & 0xffff);
            break;
        case 0x0b:
            setFrequency(adr & 0xff, (data >>> 16) & 0xffff, data & 0xffff);
            break;
        case 0x0e:
            setLoopPoint((port >>> 8) & 0xff, (adr >>> 16) & 0xffff, adr & 0xffff, (data >>> 16) & 0xffff, data & 0xffff);
            break;
        case 0x12:
            stopInterrupt();
            break;
        case 0x13:
            setPan(adr & 0xff, data & 0xffff);
            break;
        case 0x15:
            setSrcFrequency(adr & 0xff, data & 0xffff);
            break;
        case 0x16:
            setAllVolume(data);
            break;
        case 0x18:
            setAdpcmEmu(adr & 0xff);
            break;
        case 0x19:
            setReleaseFlag(data);
            break;
        }
        return 0;
    }

    public void setSamplingRate(int clock) {
        samplingRate = clock;
    }
}
